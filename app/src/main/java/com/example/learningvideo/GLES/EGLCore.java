package com.example.learningvideo.GLES;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;

import javax.microedition.khronos.egl.EGL;

public class EGLCore {
    private final EGLControlLevel mControlLevel;
    private EGLDisplay mEGLDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext mEGLContext = EGL14.EGL_NO_CONTEXT;
    private EGLConfig mEGLConfig = null;
    private EGLSurface mEGLSurface = EGL14.EGL_NO_SURFACE;
    private long mGLThreadId;

    public void updateSurface(EGLSurface eglSurface) {
        if (mControlLevel != EGLControlLevel.LEVEL_NOTHING) {
            throw new RuntimeException();
        }
        mEGLSurface = eglSurface;
    }

    public void destroySurface() {
        EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface);
        mEGLSurface = EGL14.EGL_NO_SURFACE;
    }

    enum EGLControlLevel{
        LEVEL_NOTHING,
        LEVEL_ONLY_SURFACE,
        LEVEL_ALL
    };

    public EGLCore(EGLContext eglContext, EGLDisplay eglDisplay, EGLSurface eglSurface, EGLConfig config) {
        mEGLContext = eglContext;
        mEGLDisplay = eglDisplay;
        mEGLSurface = eglSurface;
        mEGLConfig = config;
        mControlLevel = EGLControlLevel.LEVEL_NOTHING;
    }

    public EGLDisplay getEGLDisplay() {
        return mEGLDisplay;
    }

    public EGLContext getEGLContext() {
        return mEGLContext;
    }

    public EGLConfig getEGLConfig() {
        return mEGLConfig;
    }

    /**
     * 引用别的 EGLDisplay，EGLContext 创建EGLCore
     * 仅 EGLSurface 需要自己重新创建管理
     */
    public EGLCore(EGLCore core) {
        mControlLevel = EGLControlLevel.LEVEL_ONLY_SURFACE;
        mEGLDisplay = core.getEGLDisplay();
        mEGLContext = core.getEGLContext();
        mEGLConfig = core.getEGLConfig();
    }

    public long getGLThreadId() {
        return mGLThreadId;
    }

    /**
     * 创建全新的 EGLCore，自主管理 EGLDisplay，EGLContext，EGLSurface
     */
    public EGLCore(EGLContext sharedContext) {
        mControlLevel = EGLControlLevel.LEVEL_ALL;
        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException();
        }
        int[] version = new int[2];
        if(!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
            throw new RuntimeException();
        }
        int[] attrib_list = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] num_configs = new int[1];
        if(!EGL14.eglChooseConfig(mEGLDisplay, attrib_list, 0, configs, 0, 1, num_configs, 0))                            {
            throw new RuntimeException();
        }
        mEGLConfig = configs[0];
        attrib_list = new int[]{
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        mEGLContext = EGL14.eglCreateContext(mEGLDisplay, mEGLConfig, sharedContext, attrib_list, 0);
        if (mEGLContext == EGL14.EGL_NO_CONTEXT) {
            throw new RuntimeException();
        }
        mGLThreadId = Thread.currentThread().getId();
    }

    public EGLSurface getEGLSurface() {
        return mEGLSurface;
    }

    public void setWindowSurface(Object surface) {
        if (mEGLSurface != EGL14.EGL_NO_SURFACE) {
            throw new RuntimeException();
        }
        mEGLSurface = EGL14.eglCreateWindowSurface(mEGLDisplay, mEGLConfig, surface, new int[]{EGL14.EGL_NONE}, 0);
        if (mEGLSurface == EGL14.EGL_NO_SURFACE) {
            throw new RuntimeException();
        }
    }

    public void makeCurrent() {
        if(!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
            throw new RuntimeException();
        }
    }

    public int[] getSurfaceSize() {
        int[] size = new int[2];
        EGL14.eglQuerySurface(EGL14.eglGetCurrentDisplay(), EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW), EGL14.EGL_WIDTH, size, 0);
        EGL14.eglQuerySurface(EGL14.eglGetCurrentDisplay(), EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW), EGL14.EGL_HEIGHT, size, 1);
        return size;
    }

    public void release() {
        if (mControlLevel == EGLControlLevel.LEVEL_NOTHING) return;
        if (mControlLevel == EGLControlLevel.LEVEL_ALL) {
            if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                EGL14.eglDestroyContext(mEGLDisplay, mEGLContext);
                EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface);
                EGL14.eglReleaseThread();
                EGL14.eglTerminate(mEGLDisplay);
            }
            mEGLDisplay = EGL14.EGL_NO_DISPLAY;
            mEGLSurface = EGL14.EGL_NO_SURFACE;
            mEGLContext = EGL14.EGL_NO_CONTEXT;
        } else {
            if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface);
                mEGLSurface = EGL14.EGL_NO_SURFACE;
            }
        }
    }

    public void setPbufferSurface(int width, int height) {
        if (mEGLSurface != EGL14.EGL_NO_SURFACE) {
            throw new RuntimeException();
        }
        int[] attrib_list = {
                EGL14.EGL_WIDTH, width,
                EGL14.EGL_HEIGHT, height,
                EGL14.EGL_NONE
        };
        mEGLSurface = EGL14.eglCreatePbufferSurface(mEGLDisplay, mEGLConfig, attrib_list, 0);
        if (mEGLSurface == EGL14.EGL_NO_SURFACE) {
            throw new RuntimeException();
        }
    }

    public void swapBuffer() {
        EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface);
    }

    public void presentationTime(long time) {
        EGLExt.eglPresentationTimeANDROID(mEGLDisplay, mEGLSurface, time);
    }
}
