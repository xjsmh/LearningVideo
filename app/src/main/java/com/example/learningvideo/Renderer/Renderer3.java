package com.example.learningvideo.Renderer;

import static com.example.learningvideo.Core.MSG_NEXT_ACTION;
import static com.example.learningvideo.Core.MSG_SURFACE_CREATED;

import android.app.Activity;
import android.content.Context;
import android.graphics.SurfaceTexture;
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
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.example.learningvideo.Filter.DoNothingButUploadImage;
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

public class Renderer3 extends RendererBase {
    private static final String TAG = "Renderer3";
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
    private SurfaceTexture mFrameSurfaceTexture;
    int mFrameTextureType = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
    private volatile boolean mIsFrameAvailable = false;
    private final List<FilterBase> mFilterList = new ArrayList<>();
    Surface mRenderSurface;

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

    private int mFrameTexture;
    private Surface mDecoderSurface;
    private volatile boolean mReadyToDraw = false;

    @Override
    public int getViewId() {
        return R.id.myTextureView;
    }

    public Renderer3(Handler handler) {
        super(handler);
        mHandler = handler;
    }

    @Override
    public void init(Context context) {
        if (context != null) {
            SurfaceView sv = ((Activity) context).findViewById(R.id.myTextureView);
            sv.getHolder().addCallback(new MyCallback());
            mRenderSurface = sv.getHolder().getSurface();
        }
    }

    @Override
    public void render(Message msg) {
        for (FilterBase fp : mFilterList) {
            fp.process();
        }
        mEGLCore.makeCurrent();
        int[] size = mEGLCore.getSurfaceSize();
        GLES20.glViewport(0, 0, size[0], size[1]);
        draw();
        mEGLCore.swapBuffer();
        Log.e(TAG, "render "+ mRenderFrame++);
        Message.obtain(mHandler, MSG_NEXT_ACTION).sendToTarget();
    }

    @Override
    public void release() {
        GLES20.glDeleteProgram(mProgram);
        int[] values  = new int[1];
        values[0] = mFrameTexture;
        GLES20.glDeleteTextures(1, values, 0);
        mEGLCore.release();
        mDecoderSurface.release();
        mDecoderSurface = null;
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

        GLES20.glClearColor(0, 0, 0, 1);

        mFrameTexture = Utils.genTexture(mFrameTextureType, null, width, height, GLES20.GL_RGBA);
        mFrameSurfaceTexture = new SurfaceTexture(mFrameTexture);
        mFrameSurfaceTexture.setOnFrameAvailableListener((SurfaceTexture surfaceTexture) -> {mIsFrameAvailable = true;});

        FilterBase doNothing = new DoNothingButUploadImage(mEGLCore, mFrameTextureType, width, height);
        doNothing.addInputTexture(mFrameTexture, GLES20.GL_RGBA,
                (int tex, int slot, int samplerLoc) -> {
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + slot);
                    mFrameSurfaceTexture.updateTexImage();
                    GLES20.glUniform1i(samplerLoc, slot);
                });
        mFilterList.add(doNothing);

        FilterBase grayMask = new GrayMask(mFilterList.get(mFilterList.size()-1), width, height);
        grayMask.addInputTexture(mFilterList.get(mFilterList.size()-1).getOutputTexture(), GLES20.GL_RGBA,
                (int tex, int slot, int samplerLoc) -> {
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + slot);
                    //mSurfaceTexture.updateTexImage();
                    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex);
                    GLES20.glUniform1i(samplerLoc, slot);
                });
        mFilterList.add(grayMask);

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

        String fragmentShader =
                "#extension GL_OES_EGL_image_external : require \n" +
                "varying vec2 outTexPos;" +
                "uniform " +
                ((mFilterList.get(mFilterList.size()-1).getOutTextureType() == GLES20.GL_TEXTURE_2D) ? "sampler2D" : "samplerExternalOES") +
                " texSampler;" +
                "void main() {" +
                "    gl_FragColor = texture2D(texSampler, outTexPos);" +
                "}";
        mProgram = Utils.createProgram(vertexShader, fragmentShader);

        GLES20.glUseProgram(mProgram);
        mPosLoc = GLES20.glGetAttribLocation(mProgram, "pos");
        mTexPosLoc = GLES20.glGetAttribLocation(mProgram, "texPos");
        mTexSamplerLoc = GLES20.glGetUniformLocation(mProgram, "texSampler");
        mDecoderSurface = new Surface(mFrameSurfaceTexture);
        mReadyToDraw = true;
    }

    @Override
    public Surface getInputSurface() {
        return mDecoderSurface;
    }

    @Override
    public EGLCore getEGLCore() {
        return mEGLCore;
    }

    public boolean isFrameAvailable() {
        return mIsFrameAvailable;
    }

    @Override
    public int getFrameTextureType() {
        return mFrameTextureType;
    }

    @Override
    public void draw() {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(mProgram);
        GLES20.glVertexAttribPointer(mPosLoc, 2, GLES20.GL_FLOAT, false, 16, sVertices.position(0));
        GLES20.glEnableVertexAttribArray(mPosLoc);
        GLES20.glVertexAttribPointer(mTexPosLoc, 2, GLES20.GL_FLOAT, false, 16, sVertices.position(2));
        GLES20.glEnableVertexAttribArray(mTexPosLoc);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glUniform1i(mTexSamplerLoc, 0);
        GLES20.glBindTexture(mFilterList.get(mFilterList.size()-1).getOutTextureType(), mRenderTex);
        GLES20.glDrawElements(GLES20.GL_TRIANGLE_STRIP, 6, GLES20.GL_UNSIGNED_BYTE, sDrawOrders.position(0));
        GLES20.glBindTexture(mFilterList.get(mFilterList.size()-1).getOutTextureType(), GLES20.GL_NONE);
        GLES20.glDisableVertexAttribArray(mPosLoc);
        GLES20.glDisableVertexAttribArray(mTexPosLoc);
    }

    @Override
    public boolean readyToDraw() {
        return mReadyToDraw;
    }

    private class MyCallback implements SurfaceHolder.Callback {

        @Override
        public void surfaceCreated(@NonNull SurfaceHolder holder) {
            Message.obtain(mHandler, MSG_SURFACE_CREATED).sendToTarget();
        }

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

        }

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
            mReadyToDraw = false;
        }
    }

}
