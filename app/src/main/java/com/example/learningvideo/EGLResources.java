package com.example.learningvideo;

import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;

import java.nio.Buffer;

public class EGLResources {
    private final boolean mIsNeedShared;
    private EGLContext mEGLContext;
    private EGLConfig mEGLConfig;
    private EGLDisplay mEGLDisplay;
    private EGLSurface mEGLDrawSurface;
    private int mProgram;
    // 注意FBO并不是EGL共享资源，这里只是为了方便使用加上
    private int mFBO = -1;

    public int getFBO() {
        return mFBO;
    }

    public EGLResources setFBO(int FBO) {
        mFBO = FBO;
        return this;
    }

    public EGLSurface getEGLDrawSurface() {
        return mEGLDrawSurface;
    }

    public EGLResources setEGLDrawSurface(EGLSurface EGLDrawSurface) {
        mEGLDrawSurface = EGLDrawSurface;
        return this;
    }

    private int mTexture;
    private int mTextureType;
    private VertexAttrib mPosAttrib;

    public VertexAttrib getPosAttrib() {
        return mPosAttrib;
    }

    public EGLResources setPosAttrib(VertexAttrib posAttrib) {
        mPosAttrib = posAttrib;
        return this;
    }

    public VertexAttrib getTexPosAttrib() {
        return mTexPosAttrib;
    }

    public EGLResources setTexPosAttrib(VertexAttrib texPosAttrib) {
        mTexPosAttrib = texPosAttrib;
        return this;
    }

    public ElementAttrib getDrawOrdersAttrib() {
        return mDrawOrdersAttrib;
    }

    public EGLResources setDrawOrdersAttrib(ElementAttrib drawOrdersAttrib) {
        mDrawOrdersAttrib = drawOrdersAttrib;
        return this;
    }

    private VertexAttrib mTexPosAttrib;
    private ElementAttrib mDrawOrdersAttrib;


    public boolean isNeedShared() {
        return mIsNeedShared;
    }

    public EGLContext getEGLContext() {
        return mEGLContext;
    }

    public EGLResources setEGLContext(EGLContext EGLContext) {
        mEGLContext = EGLContext;
        return this;
    }

    public EGLConfig getEGLConfig() {
        return mEGLConfig;
    }

    public EGLResources setEGLConfig(EGLConfig EGLConfig) {
        mEGLConfig = EGLConfig;
        return this;
    }

    public EGLDisplay getEGLDisplay() {
        return mEGLDisplay;
    }

    public EGLResources setEGLDisplay(EGLDisplay EGLDisplay) {
        mEGLDisplay = EGLDisplay;
        return this;
    }

    public int getProgram() {
        return mProgram;
    }

    public EGLResources setProgram(int program) {
        mProgram = program;
        return this;
    }

    public int getTexture() {
        return mTexture;
    }

    public EGLResources setTexture(int texture) {
        mTexture = texture;
        return this;
    }

    public int getTextureType() {
        return mTextureType;
    }

    public EGLResources setTextureType(int textureType) {
        mTextureType = textureType;
        return this;
    }

    public EGLResources(boolean isNeedShared) {
        this.mIsNeedShared = isNeedShared;
    }

    public static class BufferAttrib {
        int mType;
        Buffer mBuffer;
        int mOffset;

        public BufferAttrib(int type, Buffer buffer, int offset) {
            mType = type;
            mBuffer = buffer;
            mOffset = offset;
        }

        public int getType() {
            return mType;
        }

        public void setType(int type) {
            mType = type;
        }

        public Buffer getBuffer() {
            return mBuffer.position(mOffset);
        }

        public void setBuffer(Buffer buffer) {
            mBuffer = buffer;
        }
    }

    public static class VertexAttrib extends BufferAttrib {
        int mSize;
        int mLoc;
        int mStride;

        public int getSize() {
            return mSize;
        }

        public void setSize(int size) {
            mSize = size;
        }

        public int getLoc() {
            return mLoc;
        }

        public void setLoc(int loc) {
            mLoc = loc;
        }

        public int getStride() {
            return mStride;
        }

        public void setStride(int stride) {
            mStride = stride;
        }

        public VertexAttrib(int size, int loc, int type, int stride, Buffer buffer, int offset) {
            super(type, buffer, offset);
            mSize = size;
            mLoc = loc;
            mStride = stride;
        }

    }

    public static class ElementAttrib extends BufferAttrib {
        int mCount;

        public ElementAttrib(int count, int type, Buffer buffer, int offset) {
            super(type, buffer, offset);
            mCount = count;
        }

        public int getCount() {
            return mCount;
        }

        public void setCount(int count) {
            mCount = count;
        }
    }

}
