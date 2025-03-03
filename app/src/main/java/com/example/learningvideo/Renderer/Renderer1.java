package com.example.learningvideo.Renderer;

import static com.example.learningvideo.Core.MSG_NEXT_ACTION;
import static com.example.learningvideo.Core.MSG_DONE;
import static com.example.learningvideo.Core.MSG_RENDER;
import static com.example.learningvideo.Core.MSG_START;
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
import com.example.learningvideo.IDrawable;
import com.example.learningvideo.R;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class Renderer1 extends RendererBase implements TextureView.SurfaceTextureListener, IDrawable{
    private static final String TAG = "Renderer1";
    private int mRenderFrame = 0;
    private Handler mHandler;
    SurfaceTexture mRenderSurfaceTexture;
    SurfaceTexture mFrameSurfaceTexture;
    int mFrameTextureType = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
    EGLCore mEGLCore;
    private volatile boolean mIsFrameAvailable;
    private final List<FilterBase> mFilterList = new ArrayList<>();
    private int mProgram;
    private int mPosLoc;
    private int mTexPosLoc;
    private int mTexSamplerLoc;
    private int mWidth;
    private int mHeight;
    private static final FloatBuffer sVertices;
    private static final ByteBuffer sDrawOrders;
    private Surface mDecoderSurface;

    static {
        float[] vertices = {
                -1, 1, 0, 0,
                -1,-1, 0, 1,
                1, -1, 1, 1,
                1, 1, 1, 0
        };
        byte[] drawOrders = {
                0, 1, 2,
                0, 2, 3
        };
        sVertices = ByteBuffer.allocateDirect(vertices.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        sVertices.put(vertices).position(0);
        sDrawOrders = ByteBuffer.allocateDirect(vertices.length).order(ByteOrder.nativeOrder());
        sDrawOrders.put(drawOrders).position(0);
    }

    private int mFrameTexture;
    private boolean mReadyToDraw;

    public Renderer1(Handler handler) {
        super(handler);
        mHandler = handler;
    }

    @Override
    public void init(Context context) {
        if (context != null)
            ((TextureView)((Activity)context).findViewById(R.id.textureView)).setSurfaceTextureListener(this);
    }

    @Override
    public void render(Message msg) {

        for (FilterBase fp : mFilterList) {
            fp.process();
        }

        mEGLCore.makeCurrent();
        int[] size = mEGLCore.getSurfaceSize();
        GLES20.glViewport(0,0, size[0], size[1]);

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        draw();
        mEGLCore.swapBuffer();
        Log.e(TAG, "render "+ mRenderFrame++);

        mIsFrameAvailable = false;
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
    public int getViewId() {
        return R.id.textureView;
    }

    @Override
    public void setup(int width, int height) {
        mWidth = width;
        mHeight = height;

        if (mEGLCore != null) {
            mEGLCore.destroySurface();
            mEGLCore.setWindowSurface(mRenderSurfaceTexture);
            mReadyToDraw = true;
            mHandler.sendEmptyMessage(MSG_START);
            return;
        }

        mEGLCore = new EGLCore(EGL14.EGL_NO_CONTEXT);
        mEGLCore.setWindowSurface(mRenderSurfaceTexture);
        mEGLCore.makeCurrent();

        GLES20.glClearColor(0, 0, 0, 1);

        mFrameTexture = Utils.genTexture(mFrameTextureType, null, width, height, GLES20.GL_RGBA);
        mFrameSurfaceTexture = new SurfaceTexture(mFrameTexture);
        mFrameSurfaceTexture.setOnFrameAvailableListener( (SurfaceTexture st) -> {
            mIsFrameAvailable = true;
            mHandler.sendEmptyMessage(MSG_RENDER);
        });

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

        String verTexShader =
                "attribute vec2 pos;" +
                "attribute vec2 texPos;" +
                "varying vec2 outTexPos;" +
                "void main() {" +
                "    gl_Position = vec4(pos, 0, 1);" +
                "    outTexPos = texPos;" +
                "}";

        String outTexType = mFilterList.get(mFilterList.size()-1).getOutTextureType() == GLES20.GL_TEXTURE_2D ? "sampler2D" : "samplerExternalOES";
        String fragmentShader =
                "#extension GL_OES_EGL_image_external : require \n" +
                "precision mediump float;" +
                "varying vec2 outTexPos;" +
                "uniform " + outTexType + " texSampler;" +
                "void main() {" +
                "    gl_FragColor = texture2D(texSampler, outTexPos);" +
                "}";

        mProgram = Utils.createProgram(verTexShader, fragmentShader);
        GLES20.glUseProgram(mProgram);
        mPosLoc = GLES20.glGetAttribLocation(mProgram, "pos");
        mTexPosLoc = GLES20.glGetAttribLocation(mProgram, "texPos");
        mTexSamplerLoc = GLES20.glGetUniformLocation(mProgram, "texSampler");

        mDecoderSurface = new Surface(mFrameSurfaceTexture);
        mReadyToDraw = true;
        mHandler.sendEmptyMessage(MSG_START);
    }

    @Override
    public boolean isFrameAvailable() {
        return mIsFrameAvailable;
    }

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        mRenderSurfaceTexture = surface;
        Message.obtain(mHandler, MSG_SURFACE_CREATED).sendToTarget();
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        mReadyToDraw = false;
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
    }

    @Override
    public void draw() {
        GLES20.glUseProgram(mProgram);
        GLES20.glVertexAttribPointer(mPosLoc, 2, GLES20.GL_FLOAT, false, 16, sVertices.position(0));
        GLES20.glEnableVertexAttribArray(mPosLoc);
        GLES20.glVertexAttribPointer(mTexPosLoc, 2, GLES20.GL_FLOAT, false, 16, sVertices.position(2));
        GLES20.glEnableVertexAttribArray(mTexPosLoc);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(mFilterList.get(mFilterList.size()-1).getOutTextureType(), mFilterList.get(mFilterList.size()-1).getOutputTexture());
        GLES20.glUniform1i(mTexSamplerLoc, 0);
        GLES20.glDrawElements(GLES20.GL_TRIANGLE_STRIP, 6, GLES20.GL_UNSIGNED_BYTE, sDrawOrders.position(0));
        GLES20.glBindTexture(mFilterList.get(mFilterList.size()-1).getOutTextureType(), GLES20.GL_NONE);
        GLES20.glDisableVertexAttribArray(mPosLoc);
        GLES20.glDisableVertexAttribArray(mTexPosLoc);
    }

    @Override
    public EGLCore getEGLCore() {
        return mEGLCore;
    }

    @Override
    public Surface getInputSurface() {
        return mDecoderSurface;
    }

    @Override
    public boolean readyToDraw() {
        return mReadyToDraw;
    }

}
