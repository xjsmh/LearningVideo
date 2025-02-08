#ifndef LEARNINGVIDEO_SHAREDTEXTURE_H
#define LEARNINGVIDEO_SHAREDTEXTURE_H

#include "android/hardware_buffer.h"
#include <android/hardware_buffer_jni.h>
#include <jni.h>
#define EGL_EGLEXT_PROTOTYPES
#define GL_GLEXT_PROTOTYPES
#include <EGL/egl.h>

#include <EGL/eglext.h>
#include <GLES/gl.h>
#include <GLES/glext.h>
#include <android/log.h>

class SharedTexture {
public:
    SharedTexture(int width, int height, int hwBufferFormat);
    SharedTexture(JNIEnv *env, jobject buffer);
    ~SharedTexture();

    void bindTexToHwBuffer(int tex, int texType);
    jobject getHardWareBuffer(JNIEnv *env);
    static bool waitFence(int fenceFd);
    static int createFence();
    static bool isAvailable();

    void updateFrame(unsigned char *data, int fence);

private:
    AHardwareBuffer *mHardwareBuffer;
    EGLImageKHR mEGLImage = EGL_NO_IMAGE_KHR;
    int mTex;
    int mWidth;
    int mHeight;
    const static bool AVAILABLE;
};


#endif //LEARNINGVIDEO_SHAREDTEXTURE_H
