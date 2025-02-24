package com.example.learningvideo.Filter;

import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;

import com.example.learningvideo.GLES.EGLCore;

public class DoNothingButUploadImage extends FilterBase {

    public DoNothingButUploadImage(EGLCore eglCore, int inTexType, int width, int height) {
        super(eglCore, inTexType, width, height);
        mEGLCore = eglCore;
        mOutTextureType = inTexType;
    }

    public DoNothingButUploadImage(FilterBase lastFilter, int width, int height) {
        super(lastFilter, width, height);
        throw new RuntimeException();
    }

    @Override
    public void addInputTexture(int tex, int colorFormat, ITextureUploader uploader) {
        if (mInputTexture.size() > 1 || colorFormat != GLES20.GL_RGBA) {
            throw new RuntimeException();
        }
        mInputTexture.add(tex);
        mOutputTexture = tex;
        if(uploader != null)
            mUploaders.add(uploader);
    }

    @Override
    public void process() {
        mEGLCore.makeCurrent();
        int[] size = mEGLCore.getSurfaceSize();
        GLES20.glViewport(0, 0, size[0], size[1]);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        mUploaders.get(0).uploadTexture(mInputTexture.get(0), 0, 0);
    }

    @Override
    public void release() {
        // nothing to do
    }

}
