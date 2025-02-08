package com.example.learningvideo.Renderer;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.view.View;

import com.example.learningvideo.EGLResources;

public abstract class RendererBase {
    public RendererBase(Context context, Handler handler){}
    public abstract void start(int width, int height);
    public abstract void render(Message msg);
    public abstract void release();
    public abstract View getView();
    public abstract void setup(int width, int height);
    public abstract EGLResources getEGLResource();
    public abstract boolean isFrameAvailable();
    public void setFrameTextureType(int texture) { }
    public abstract int getFrameTextureType();
}
