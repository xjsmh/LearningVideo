package com.example.learningvideo.Renderer;

import static com.example.learningvideo.Core.MSG_NEXT_ACTION;
import static com.example.learningvideo.Core.MSG_SURFACE_CREATED;

import android.app.Activity;
import android.content.Context;
import android.opengl.EGL14;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.example.learningvideo.Filter.FilterBase;
import com.example.learningvideo.Filter.FrameCapture;
import com.example.learningvideo.Filter.GrayMask;
import com.example.learningvideo.GLES.EGLCore;
import com.example.learningvideo.GLES.Utils;
import com.example.learningvideo.R;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class Renderer5 extends RendererBase {
    private static final String TAG = "Renderer5";
    private int mRenderFrame = 0;
    private Handler mHandler;
    private EGLCore mEGLCore;
    int mWidth;
    int mHeight;
    private static final FloatBuffer sVertices;
    private static final ByteBuffer sDrawOrders;
    private int mProgram;
    private int mPosLoc;
    private int mTexPosLoc;
    private int mTexSamplerLoc;
    private int mRenderTex;
    private int mFrameTexture;
    int mRenderTexType;
    //private int mFrameTextureType = GLES20.GL_TEXTURE_2D;
    private int mFrameTextureType = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
    private final List<FilterBase> mFilterList = new ArrayList<>();
    private boolean mReadyToDraw;
    private Surface mRenderSurface;

    public int getFrameTextureType() {
        return mFrameTextureType;
    }

    public void setFrameTextureType(int frameTextureType) {
        mFrameTextureType = frameTextureType;
    }

    static {
        float[] vertices = {
                -1, 1, 0, 0,
                -1,-1, 0, 1,
                1, -1, 1, 1,
                1, 1, 1, 0
        };
        sVertices = ByteBuffer.allocateDirect(vertices.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        sVertices.put(vertices).position(0);
        byte[] orders = {
                0, 1, 2,
                0, 2, 3
        };
        sDrawOrders = ByteBuffer.allocateDirect(orders.length).order(ByteOrder.nativeOrder());
        sDrawOrders.put(orders).position(0);
    }

    public Renderer5(Handler handler) {
        super(handler);
        mHandler = handler;
    }

    @Override
    public void render(Message msg) {

        for (FilterBase processor : mFilterList) {
            processor.process();
        }
        mEGLCore.makeCurrent();
        int[] size = mEGLCore.getSurfaceSize();
        GLES20.glViewport(0,0, size[0], size[1]);
        draw();
        mEGLCore.swapBuffer();
        Log.e(TAG, "render "+ mRenderFrame++);
        mHandler.sendEmptyMessage(MSG_NEXT_ACTION);
    }

    @Override
    public void release() {
        GLES20.glDeleteProgram(mProgram);
        int[] values  = new int[1];
        values[0] = mFrameTexture;
        GLES20.glDeleteTextures(1, values, 0);
        mEGLCore.release();
    }

    @Override
    public void setup(int width, int height) {
        if (mEGLCore != null) {
            mEGLCore.destroySurface();
            mEGLCore.setWindowSurface(mRenderSurface);
            mReadyToDraw = true;
            return;
        }
        mEGLCore = new EGLCore(EGL14.EGL_NO_CONTEXT);
        mEGLCore.setWindowSurface(mRenderSurface);
        mEGLCore.makeCurrent();

        mFrameTexture = Utils.genTexture(mFrameTextureType, null, width, height, GLES20.GL_RGBA);

        FilterBase grayMask = new GrayMask(mEGLCore, mFrameTextureType, width, height);
        grayMask.addInputTexture(mFrameTexture, GLES20.GL_RGBA,
                (int texture, int slot, int samplerLoc) -> {
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + slot);
                    //mSurfaceTexture.updateTexImage();
                    GLES20.glBindTexture(mFrameTextureType, texture);
                    GLES20.glUniform1i(samplerLoc, slot);
                });
        mFilterList.add(grayMask);

        FilterBase frameCapture = new FrameCapture(mFilterList.get(mFilterList.size()-1),  width, height);
        frameCapture.addInputTexture(mFilterList.get(mFilterList.size()-1).getOutputTexture(), GLES20.GL_RGBA);
        mFilterList.add(frameCapture);

        if (!mFilterList.isEmpty()) {
            mRenderTex = mFilterList.get(mFilterList.size() - 1).getOutputTexture();
            mRenderTexType = mFilterList.get(mFilterList.size() - 1).getOutTextureType();
        } else {
            mRenderTex = mFrameTexture;
            mRenderTexType = mFrameTextureType;
        }

        String vertexShader =
            "attribute vec2 pos;" +
            "attribute vec2 texPos;" +
            "varying vec2 outTexPos;" +
            "void main() {" +
            "    gl_Position = vec4(pos, 0, 1);" +
            "    outTexPos = texPos;" +
            "}";

        String fragmentShader =
            "#extension GL_OES_EGL_image_external : require \n" +
            "precision mediump float;" +
            "varying vec2 outTexPos;" +
            "uniform " + ((mRenderTexType == GLES20.GL_TEXTURE_2D) ? "sampler2D" : "samplerExternalOES") + " texSampler;" +
            "void main() {" +
            "    gl_FragColor = texture2D(texSampler, outTexPos);" +
            "}";
        mProgram = Utils.createProgram(vertexShader, fragmentShader);

        GLES20.glUseProgram(mProgram);
        mPosLoc = GLES20.glGetAttribLocation(mProgram, "pos");
        mTexPosLoc = GLES20.glGetAttribLocation(mProgram, "texPos");
        mTexSamplerLoc = GLES20.glGetUniformLocation(mProgram, "texSampler");
        mReadyToDraw = true;
    }

    @Override
    public EGLCore getEGLCore() {
        return mEGLCore;
    }

    @Override
    public boolean isFrameAvailable() {
        return true;
    }

    @Override
    public void draw() {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(mProgram);
        GLES20.glVertexAttribPointer(mPosLoc,2,GLES20.GL_FLOAT,false,4*4,sVertices.position(0));
        GLES20.glEnableVertexAttribArray(mPosLoc);
        GLES20.glVertexAttribPointer(mTexPosLoc,2,GLES20.GL_FLOAT,false,4*4,sVertices.position(2));
        GLES20.glEnableVertexAttribArray(mTexPosLoc);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glUniform1i(mTexSamplerLoc, 0);
        GLES20.glBindTexture(mRenderTexType , mRenderTex);
        GLES20.glDrawElements(GLES20.GL_TRIANGLE_STRIP,6,GLES20.GL_UNSIGNED_BYTE,sDrawOrders.position(0));
        GLES20.glDisableVertexAttribArray(mPosLoc);
        GLES20.glDisableVertexAttribArray(mTexPosLoc);
    }

    @Override
    public boolean readyToDraw() {
        return mReadyToDraw;
    }

    @Override
    public Object getInputSurface() {
        return mFrameTexture;
    }

    @Override
    public int getViewId() {
        return R.id.myTextureView;
    }

    @Override
    public void init(Context context) {
        if (context != null) {
            SurfaceView sv = ((Activity) context).findViewById(R.id.myTextureView);
            sv.getHolder().addCallback(new MyCallback());
            mRenderSurface = sv.getHolder().getSurface();
        }
    }

    private class MyCallback implements SurfaceHolder.Callback {

        @Override
        public void surfaceCreated(@NonNull SurfaceHolder holder) {
            Message.obtain(mHandler, MSG_SURFACE_CREATED).sendToTarget();
        }

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
            mRenderSurface = holder.getSurface();
        }

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
            mReadyToDraw = false;
        }
    }

}
