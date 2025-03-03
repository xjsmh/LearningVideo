package com.example.learningvideo.Renderer;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.view.View;

import com.example.learningvideo.GLES.EGLCore;
import com.example.learningvideo.IDrawable;

public abstract class RendererBase implements IDrawable {
    public RendererBase(Handler handler){}
    public abstract void render(Message msg);
    public abstract void release();
    public int getViewId() {return -1;}
    public abstract void setup(int width, int height);
    public abstract EGLCore getEGLCore();
    public abstract boolean isFrameAvailable();

    public abstract boolean readyToDraw();

    public Object getInputSurface() {
        return null;
    }

    public void init(Context context) {
    }
}
