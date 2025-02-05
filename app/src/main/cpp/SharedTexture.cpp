#include <mutex>
#include <unistd.h>
#include "SharedTexture.h"
#define LOG_TAG "SharedTexture-JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG,  LOG_TAG,  __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,   LOG_TAG,  __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,   LOG_TAG,  __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,  LOG_TAG,  __VA_ARGS__)
#define LOGF(...) __android_log_print(ANDROID_LOG_FATAL,  LOG_TAG,  __VA_ARGS__)

namespace glext {

    PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC eglGetNativeClientBufferANDROID = nullptr;
    PFNGLEGLIMAGETARGETTEXTURE2DOESPROC glEGLImageTargetTexture2DOES = nullptr;
    PFNEGLCREATEIMAGEKHRPROC eglCreateImageKHR = nullptr;
    PFNEGLDESTROYIMAGEKHRPROC eglDestroyImageKHR = nullptr;
    PFNEGLCREATESYNCKHRPROC eglCreateSyncKHR = nullptr;
    PFNEGLDESTROYSYNCKHRPROC eglDestroySyncKHR = nullptr;
    PFNEGLWAITSYNCKHRPROC eglWaitSyncKHR = nullptr;
    PFNEGLDUPNATIVEFENCEFDANDROIDPROC eglDupNativeFenceFDANDROID = nullptr;

}

const bool SharedTexture::AVAILABLE = []() {
    glext::eglGetNativeClientBufferANDROID = (PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC) eglGetProcAddress(
            "eglGetNativeClientBufferANDROID");
    glext::glEGLImageTargetTexture2DOES = (PFNGLEGLIMAGETARGETTEXTURE2DOESPROC) eglGetProcAddress(
            "glEGLImageTargetTexture2DOES");
    glext::eglCreateImageKHR = (PFNEGLCREATEIMAGEKHRPROC) eglGetProcAddress("eglCreateImageKHR");
    glext::eglDestroyImageKHR = (PFNEGLDESTROYIMAGEKHRPROC) eglGetProcAddress("eglDestroyImageKHR");
    glext::eglCreateSyncKHR = (PFNEGLCREATESYNCKHRPROC) eglGetProcAddress("eglCreateSyncKHR");
    glext::eglDestroySyncKHR = (PFNEGLDESTROYSYNCKHRPROC) eglGetProcAddress("eglDestroySyncKHR");
    glext::eglWaitSyncKHR = (PFNEGLWAITSYNCKHRPROC) eglGetProcAddress("eglWaitSyncKHR");
    glext::eglDupNativeFenceFDANDROID = (PFNEGLDUPNATIVEFENCEFDANDROIDPROC) eglGetProcAddress(
            "eglDupNativeFenceFDANDROID");

    return glext::eglGetNativeClientBufferANDROID
           && glext::glEGLImageTargetTexture2DOES
           && glext::eglCreateImageKHR
           && glext::eglDestroyImageKHR
           && glext::eglCreateSyncKHR
           && glext::eglDestroySyncKHR
           && glext::eglWaitSyncKHR
           && glext::eglDupNativeFenceFDANDROID;
}();

bool SharedTexture::isAvailable() {
    return AVAILABLE;
}

SharedTexture::SharedTexture(int width, int height, int type) :
  mHardwareBuffer(nullptr), mTex(0), mWidth(width), mHeight(height)
{
    AHardwareBuffer_Desc desc = {0};
    desc.width = width;
    desc.height = height;
    desc.layers = 1;
    desc.format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;
    desc.usage = AHARDWAREBUFFER_USAGE_CPU_READ_NEVER | AHARDWAREBUFFER_USAGE_CPU_WRITE_NEVER |
                 AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE | AHARDWAREBUFFER_USAGE_GPU_COLOR_OUTPUT;
    AHardwareBuffer_allocate(&desc, &mHardwareBuffer);
}

SharedTexture::~SharedTexture() {
    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (mEGLImage != EGL_NO_IMAGE_KHR) {
        glext::eglDestroyImageKHR(display, mEGLImage);
        mEGLImage = EGL_NO_IMAGE_KHR;
    }
    if (mHardwareBuffer) {
        AHardwareBuffer_release(mHardwareBuffer);
    }
}

void SharedTexture::bindTexToHwBuffer(int tex) {
    EGLClientBuffer clientBuffer = eglGetNativeClientBufferANDROID(mHardwareBuffer);
    EGLint attribs[] = {EGL_IMAGE_PRESERVED_KHR, EGL_TRUE, EGL_NONE};
    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    mEGLImage = eglCreateImageKHR(display, EGL_NO_CONTEXT, EGL_NATIVE_BUFFER_ANDROID, clientBuffer, attribs);
    glBindTexture(GL_TEXTURE_2D, tex);
    glEGLImageTargetTexture2DOES(GL_TEXTURE_2D, (GLeglImageOES)mEGLImage);
    mTex = tex;
}

jobject SharedTexture::getHardWareBuffer(JNIEnv *env) {
    return AHardwareBuffer_toHardwareBuffer(env, mHardwareBuffer);
}



SharedTexture::SharedTexture(JNIEnv *env, jobject buffer) :
        mHardwareBuffer(AHardwareBuffer_fromHardwareBuffer(env, buffer)), mTex(0)
{
    AHardwareBuffer_Desc desc;
    AHardwareBuffer_describe(mHardwareBuffer, &desc);
    AHardwareBuffer_acquire(mHardwareBuffer);
    mWidth = (int)desc.width;
    mHeight = (int)desc.height;
}

int SharedTexture::createFence() {
    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    EGLSyncKHR syncKHR = glext::eglCreateSyncKHR(display, EGL_SYNC_NATIVE_FENCE_ANDROID, nullptr);
    glFlush();
    int fence = glext::eglDupNativeFenceFDANDROID(display, syncKHR);
    glext::eglDestroySyncKHR(display, syncKHR);
    return fence;
}

bool SharedTexture::waitFence(int fenceFd) {
    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    int attrbs[] = {EGL_SYNC_NATIVE_FENCE_FD_ANDROID, fenceFd, EGL_NONE};
    EGLSyncKHR syncKHR = glext::eglCreateSyncKHR(display, EGL_SYNC_NATIVE_FENCE_ANDROID, attrbs);
    if (syncKHR == EGL_NO_SYNC_KHR) {
        LOGE("waitEGLFence failed: eglCreateSyncKHR null");
        close(fenceFd);
        return false;
    }
    EGLint success = glext::eglWaitSyncKHR(display, syncKHR, 0);
    glext::eglDestroySyncKHR(display, syncKHR);
    return (success == EGL_TRUE);
}
