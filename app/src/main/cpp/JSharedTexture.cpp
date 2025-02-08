#include <jni.h>
#include "SharedTexture.h"

// Write C++ code here.
//
// Do not forget to dynamically load the C++ library into your application.
//
// For instance,
//
// In MainActivity.java:
//    static {
//       System.loadLibrary("learningvideo");
//    }
//
// Or, in MainActivity.kt:
//    companion object {
//      init {
//         System.loadLibrary("learningvideo")
//      }
//    }

class JSharedTexture {
public:
    JSharedTexture(int width, int height, int hwBufferFormat) :
        mTexture(new SharedTexture(width, height, hwBufferFormat)) {}

    JSharedTexture(JNIEnv *pEnv, jobject pJobject) :
            mTexture(new SharedTexture(pEnv, pJobject)) {}
    ~JSharedTexture(){
        delete mTexture;
        mTexture = nullptr;
    }

    SharedTexture *mTexture;
};

extern "C"
JNIEXPORT void JNICALL
Java_com_example_learningvideo_SharedTexture_bindTexToHwBuffer(JNIEnv *env, jobject thiz,
                                                               jlong ctx, jint frame_tex, jint tex_type) {
    if(!ctx) return;
    JSharedTexture *_ctx = reinterpret_cast<JSharedTexture*>(ctx);
    _ctx->mTexture->bindTexToHwBuffer(frame_tex, tex_type);
}
extern "C"
JNIEXPORT jobject JNICALL
Java_com_example_learningvideo_SharedTexture_getHardwareBuffer(JNIEnv *env, jobject thiz, jlong ctx) {
    if (!ctx) return nullptr;
    JSharedTexture *_ctx = reinterpret_cast<JSharedTexture*>(ctx);
    return _ctx->mTexture->getHardWareBuffer(env);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_example_learningvideo_SharedTexture_createNativeSharedTexture(JNIEnv *env, jobject thiz, jint width, jint height, jint hwBufferFormat) {
    JSharedTexture *ctx = new JSharedTexture(width, height, hwBufferFormat);
    jlong ret = reinterpret_cast<jlong>(ctx);
    return ret;
}
extern "C"
JNIEXPORT jlong JNICALL
Java_com_example_learningvideo_SharedTexture_createNativeSharedTexture2(JNIEnv *env, jobject thiz,
                                                                       jobject buffer) {
    JSharedTexture *ctx = new JSharedTexture(env, buffer);
    jlong ret = reinterpret_cast<jlong>(ctx);
    return ret;
}
extern "C"
JNIEXPORT jint JNICALL
Java_com_example_learningvideo_SharedTexture_createFence(JNIEnv *env, jobject thiz) {
    return SharedTexture::createFence();
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_learningvideo_SharedTexture_waitFence(JNIEnv *env, jobject thiz, jint fd) {
    return SharedTexture::waitFence(fd);
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_learningvideo_SharedTexture_isAvailable(JNIEnv *env, jclass clazz) {
    return SharedTexture::isAvailable();
}
extern "C"
JNIEXPORT void JNICALL
Java_com_example_learningvideo_SharedTexture_finalize(JNIEnv *env, jobject thiz,
                                                      jlong native_context) {
    if (native_context == 0) {
        return;
    }
    auto *_ctx = reinterpret_cast<JSharedTexture *>(native_context);
    delete _ctx;
}
extern "C"
JNIEXPORT void JNICALL
Java_com_example_learningvideo_SharedTexture_updateFrame(JNIEnv *env, jobject thiz,
                                                         jlong native_context, jbyteArray data, jint fence) {
    if (native_context == 0) {
        return;
    }
    auto *_ctx = reinterpret_cast<JSharedTexture *>(native_context);
    auto *pBuffer = reinterpret_cast<unsigned char *>(env->GetByteArrayElements(data,
                                                                                         nullptr));
    if (!pBuffer) {
        return;
    }
    _ctx->mTexture->updateFrame(pBuffer, fence);
    (*env).ReleaseByteArrayElements(data, reinterpret_cast<jbyte *>(pBuffer), 0);
}