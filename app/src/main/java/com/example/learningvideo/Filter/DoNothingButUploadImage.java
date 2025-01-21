package com.example.learningvideo.Filter;

import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;

public class DoNothingButUploadImage extends FilterBase {

    public DoNothingButUploadImage(FilterBase lastFilter, EGLContext context, EGLDisplay display, EGLConfig config, int inTexType, int width, int height) {
        super(lastFilter, context, display, config, inTexType, width, height);
        mOutTextureType = inTexType;
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
    public void process(EGLContext context, EGLDisplay display, EGLSurface surface) {
        mUploaders.get(0).uploadTexture(mInputTexture.get(0), 0, 0);
        //GLES20.glBindTexture(mOutTextureType, GLES20.GL_NONE);
    }

}
