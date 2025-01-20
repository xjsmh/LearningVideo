package com.example.learningvideo.Filter;

import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;

import java.util.ArrayList;
import java.util.List;

public abstract class FilterBase {
    protected List<Integer> mInputTexture = new ArrayList<>();
    protected List<ITextureUploader> mUploaders = new ArrayList<>();
    protected int mOutputTexture;
    protected int mOutTextureType;

    public FilterBase(EGLDisplay display, EGLConfig config, int inTexType, int width, int height) {
    }

    public int getOutTextureType() {
        return mOutTextureType;
    }

    public int getOutputTexture() {
        return mOutputTexture;
    }

    public abstract void addInputTexture(int tex, int colorFormat, ITextureUploader uploader);

    public abstract void process(EGLContext context, EGLDisplay display, EGLSurface surface);

    public interface ITextureUploader {
        void uploadTexture(int tex, int slot, int samplerLoc);
    }
}
