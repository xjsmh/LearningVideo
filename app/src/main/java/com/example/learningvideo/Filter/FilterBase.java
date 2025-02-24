package com.example.learningvideo.Filter;

import com.example.learningvideo.GLES.EGLCore;

import java.util.ArrayList;
import java.util.List;

public abstract class FilterBase {
    protected List<Integer> mInputTexture = new ArrayList<>();
    protected List<ITextureUploader> mUploaders = new ArrayList<>();
    protected int mOutputTexture;
    protected int mOutTextureType;
    protected EGLCore mEGLCore;
    protected FilterBase mLastFilter;

    public EGLCore getEGLCore() {
        return mEGLCore;
    }

    public FilterBase(FilterBase lastFilter, int width, int height) {
    }

    public FilterBase(EGLCore eglCore, int inTarget, int width, int height) {
    }

    public int getOutTextureType() {
        return mOutTextureType;
    }

    public int getOutputTexture() {
        return mOutputTexture;
    }
    public int getFBO() {return -1;}
    public void addInputTexture(int tex, int colorFormat) {
        addInputTexture(tex, colorFormat, null);
    }
    public abstract void addInputTexture(int tex, int colorFormat, ITextureUploader uploader);

    public abstract void process();
    public abstract void release();

    public interface ITextureUploader {
        void uploadTexture(int tex, int slot, int samplerLoc);
    }
}
