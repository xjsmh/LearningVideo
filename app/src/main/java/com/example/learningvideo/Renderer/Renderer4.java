package com.example.learningvideo.Renderer;

import static com.example.learningvideo.Core.MSG_NEXT_ACTION;
import static com.example.learningvideo.Core.MSG_START;
import static com.example.learningvideo.Core.MSG_SURFACE_CREATED;

import android.app.Activity;
import android.content.Context;
import android.opengl.EGL14;
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
import com.example.learningvideo.Filter.NV12ToRGBA;
import com.example.learningvideo.GLES.EGLCore;
import com.example.learningvideo.GLES.Utils;
import com.example.learningvideo.R;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class Renderer4 extends RendererBase {
    private static final String TAG = "Renderer4";
    private int mRenderFrame = 0;
    private Handler mHandler;
    int mWidth;
    int mHeight;
    private static final FloatBuffer sVertices;
    private static final ByteBuffer sDrawOrders;
    private EGLCore mEGLCore;
    private int mProgram;
    private int mPosLoc;
    private int mTexPosLoc;
    private int mTexSamplerLoc;
    private int mRenderTex;
    private final List<FilterBase> mFilterList = new ArrayList<>();
    private final int[] mFrameTex = new int[2];
    int mFrameTextureType = GLES20.GL_TEXTURE_2D;
    ByteBuffer mYUV;

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

    private boolean mReadyToDraw = false;
    private Surface mRenderSurface;

    public Renderer4(Handler handler) {
        super(handler);
        mHandler = handler;
    }

    @Override
    public int getViewId() {
        return R.id.surfaceView;
    }

    @Override
    public void init(Context context) {
        if (context != null) {
            SurfaceView sv = ((Activity) context).findViewById(R.id.surfaceView);
            sv.getHolder().addCallback(new MyCallback());
            mRenderSurface = sv.getHolder().getSurface();
        }
    }

    @Override
    public void render(Message msg) {
        mYUV = (ByteBuffer)msg.obj;

        for (FilterBase processor : mFilterList) {
            processor.process();
        }

        mEGLCore.makeCurrent();
        int[] size = mEGLCore.getSurfaceSize();
        GLES20.glViewport(0,0, size[0], size[1]);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        draw();
        mEGLCore.swapBuffer();

        Log.e(TAG, "render "+ mRenderFrame++);
        mHandler.sendEmptyMessage(MSG_NEXT_ACTION);
    }

    @Override
    public void release() {
        GLES20.glDeleteProgram(mProgram);
        GLES20.glDeleteTextures(2, mFrameTex, 0);
        mEGLCore.release();
    }

    @Override
    public void setup(int width, int height) {
        if (mEGLCore != null) {
            mEGLCore.destroySurface();
            mEGLCore.setWindowSurface(mRenderSurface);
            mReadyToDraw = true;
            mHandler.sendEmptyMessage(MSG_START);
            return;
        }
        mWidth = width;
        mHeight= height;
        mEGLCore = new EGLCore(EGL14.EGL_NO_CONTEXT);
        mEGLCore.setWindowSurface(mRenderSurface);
        mEGLCore.makeCurrent();

        GLES20.glClearColor(0, 0, 0, 1);

        mFrameTex[0] = Utils.genTexture(mFrameTextureType, null, width, -height, GLES20.GL_LUMINANCE);
        mFrameTex[1] = Utils.genTexture(mFrameTextureType, null, width / 2, -height / 2, GLES20.GL_LUMINANCE_ALPHA);

        mFilterList.add(createNV12ToRGBA(width, height));
        mFilterList.add(createGrayMask(width, height));
        FilterBase frameCapture = new FrameCapture(mFilterList.get(mFilterList.size()-1), width, height);
        frameCapture.addInputTexture(mFilterList.get(mFilterList.size()-1).getOutputTexture(), GLES20.GL_RGBA);
        mFilterList.add(frameCapture);
        mRenderTex = mFilterList.get(mFilterList.size()-1).getOutputTexture();
        String vertexShader =
                "attribute vec2 pos;" +
                "attribute vec2 texPos;" +
                "varying vec2 outTexPos;" +
                "void main() {" +
                "    gl_Position = vec4(pos, 0, 1);" +
                "    outTexPos = texPos;" +
                "}";

        String samplerType = (mFilterList.get(mFilterList.size()-1).getOutTextureType() == GLES20.GL_TEXTURE_2D) ? "sampler2D" : "samplerExternalOES";
        String fragmentShader =
                "#extension GL_OES_EGL_image_external : require \n" +
                "precision mediump float;" +
                "varying vec2 outTexPos;" +
                "uniform " + samplerType + " texSampler;" +
                "void main() {" +
                "    gl_FragColor = texture2D(texSampler, outTexPos);" +
                "}";
        mProgram = Utils.createProgram(vertexShader, fragmentShader);

        GLES20.glUseProgram(mProgram);
        mPosLoc = GLES20.glGetAttribLocation(mProgram, "pos");
        mTexPosLoc = GLES20.glGetAttribLocation(mProgram, "texPos");
        mTexSamplerLoc = GLES20.glGetUniformLocation(mProgram, "texSampler");
        mReadyToDraw = true;
        mHandler.sendEmptyMessage(MSG_START);
    }

    @Override
    public EGLCore getEGLCore() {
        return mEGLCore;
    }

    @Override
    public boolean readyToDraw() {
        return mReadyToDraw;
    }

    private FilterBase createGrayMask(int width, int height) {
        FilterBase grayMask = new GrayMask(mFilterList.get(mFilterList.size()-1), width, height);
        grayMask.addInputTexture(mFilterList.get(mFilterList.size()-1).getOutputTexture(), GLES20.GL_RGBA, (int tex, int slot, int samplerLoc) -> {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + slot);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
            GLES20.glUniform1i(samplerLoc, slot);
        });
        return grayMask;
    }

    @NonNull
    private FilterBase createNV12ToRGBA(int width, int height) {
        FilterBase nV12ToRGBA = new NV12ToRGBA(mEGLCore, mFrameTextureType, width, height);
        nV12ToRGBA.addInputTexture(mFrameTex[0], GLES20.GL_LUMINANCE,
                (int tex, int slot, int samplerLoc) -> {
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + slot);
                    GLES20.glBindTexture(mFrameTextureType, tex);
                    GLES20.glTexImage2D(mFrameTextureType,0,GLES20.GL_LUMINANCE,mWidth, mHeight,0,GLES20.GL_LUMINANCE,GLES20.GL_UNSIGNED_BYTE,mYUV.position(0));
                    GLES20.glUniform1i(samplerLoc, slot);
                }
        );
        nV12ToRGBA.addInputTexture(mFrameTex[1], GLES20.GL_LUMINANCE_ALPHA,
                (int tex, int slot, int samplerLoc) -> {
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + slot);
                    GLES20.glBindTexture(mFrameTextureType, tex);
                    GLES20.glTexImage2D(mFrameTextureType,0,GLES20.GL_LUMINANCE_ALPHA,mWidth / 2, mHeight / 2,0,GLES20.GL_LUMINANCE_ALPHA,GLES20.GL_UNSIGNED_BYTE,mYUV.position(mWidth * mHeight));
                    GLES20.glUniform1i(samplerLoc, slot);

                }
        );
        return nV12ToRGBA;
    }


    @Override
    public boolean isFrameAvailable() {
        return true;
    }

    @Override
    public void draw() {
        GLES20.glUseProgram(mProgram);
        GLES20.glVertexAttribPointer(mPosLoc,2,GLES20.GL_FLOAT,false,4*4,sVertices.position(0));
        GLES20.glEnableVertexAttribArray(mPosLoc);
        GLES20.glVertexAttribPointer(mTexPosLoc,2,GLES20.GL_FLOAT,false,4*4,sVertices.position(2));
        GLES20.glEnableVertexAttribArray(mTexPosLoc);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mRenderTex);
        GLES20.glUniform1i(mTexSamplerLoc, 0);

        GLES20.glDrawElements(GLES20.GL_TRIANGLE_STRIP,6,GLES20.GL_UNSIGNED_BYTE,sDrawOrders.position(0));
        GLES20.glDisableVertexAttribArray(mPosLoc);
        GLES20.glDisableVertexAttribArray(mTexPosLoc);
        GLES20.glBindTexture(mFilterList.get(mFilterList.size()-1).getOutTextureType(), GLES20.GL_NONE);
    }

    private class MyCallback implements SurfaceHolder.Callback {

        @Override
        public void surfaceCreated(@NonNull SurfaceHolder holder) {
            Message.obtain(mHandler, MSG_SURFACE_CREATED).sendToTarget();
            Log.e(TAG, "surfaceCreated");
        }

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
            mRenderSurface = holder.getSurface();
            Log.e(TAG, "surfaceChanged");
        }

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
            mReadyToDraw = false;
            Log.e(TAG, "surfaceDestroyed");
        }
    }

}
