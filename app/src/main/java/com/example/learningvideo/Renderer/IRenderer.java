package com.example.learningvideo.Renderer;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.view.View;

import com.example.learningvideo.EGLResources;

public interface IRenderer {
    void start(Context context, Handler handler, int width, int height);
    void render(Message msg);
    void release();
    View getView();
    void setup(int width, int height);
    EGLResources getEGLResource();
    boolean isFrameAvailable();
}
