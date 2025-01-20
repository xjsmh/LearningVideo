package com.example.learningvideo.Renderer;

import static com.example.learningvideo.Core.MSG_ACTION_COMPLETED;
import static com.example.learningvideo.Core.MSG_SETUP_EGL;
import static com.example.learningvideo.Core.MSG_SURFACE_CREATED;

import android.app.Activity;
import android.content.Context;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.example.learningvideo.EGLResources;
import com.example.learningvideo.Filter.FilterBase;
import com.example.learningvideo.Filter.GrayMask;
import com.example.learningvideo.Filter.NV12ToRGBA;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class Renderer4 implements IRenderer {
    private static final String TAG = "Renderer4";
    private int mRenderFrame = 0;
    private SurfaceView mSurfaceView;
    private Handler mHandler;
    private EGLDisplay mEGLDisplay;
    private EGLConfig mEGLConfig;
    private EGLContext mEGLContext;
    private EGLSurface mEGLSurface;
    int mWidth;
    int mHeight;
    private Context mContext;
    private static final FloatBuffer sVertices;
    private static final ByteBuffer sDrawOrders;
    private int mProgram;
    private int mPosLoc;
    private int mTexPosLoc;
    private int mTexSamplerLoc;
    private int mRenderTex;
    private final List<FilterBase> mFrameProcessors = new ArrayList<>();
    private final int[] mFrameTex = new int[2];
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

    @Override
    public void start(Context context, Handler handler, int width, int height) {
        mContext = context;
        mHandler = handler;
        mSurfaceView = new SurfaceView(context);
        mSurfaceView.getHolder().addCallback(new MyCallback());
    }

    @Override
    public void render(Message msg) {
        if (mWidth != msg.arg1 || mHeight != msg.arg2) {
            mWidth = msg.arg1;
            mHeight = msg.arg2;
            ((Activity)mContext).runOnUiThread(()->{
                DisplayMetrics dm = mSurfaceView.getResources().getDisplayMetrics();
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);

                lp.width = dm.widthPixels;
                lp.height = dm.widthPixels * mHeight / mWidth;
                lp.gravity = Gravity.CENTER;
                mSurfaceView.setLayoutParams(lp);
            });
        }
        mYUV = (ByteBuffer)msg.obj;

        EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext);
        for (FilterBase processor : mFrameProcessors) {
            processor.process(mEGLContext, EGL14.eglGetCurrentDisplay(), EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW));
        }

        GLES20.glUseProgram(mProgram);
        int[] size = new int[2];
        EGL14.eglQuerySurface(EGL14.eglGetCurrentDisplay(), EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW), EGL14.EGL_WIDTH, size, 0);
        EGL14.eglQuerySurface(EGL14.eglGetCurrentDisplay(), EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW), EGL14.EGL_HEIGHT, size, 1);
        GLES20.glViewport(0,0, size[0], size[1]);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glVertexAttribPointer(mPosLoc,2,GLES20.GL_FLOAT,false,4*4,sVertices.position(0));
        GLES20.glEnableVertexAttribArray(mPosLoc);
        GLES20.glVertexAttribPointer(mTexPosLoc,2,GLES20.GL_FLOAT,false,4*4,sVertices.position(2));
        GLES20.glEnableVertexAttribArray(mTexPosLoc);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glUniform1i(mTexSamplerLoc, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mRenderTex);
        GLES20.glDrawElements(GLES20.GL_TRIANGLE_STRIP,6,GLES20.GL_UNSIGNED_BYTE,sDrawOrders.position(0));
        EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface);
        GLES20.glDisableVertexAttribArray(mPosLoc);
        GLES20.glDisableVertexAttribArray(mTexPosLoc);
        GLES20.glBindTexture(mFrameProcessors.get(mFrameProcessors.size()-1).getOutTextureType(), GLES20.GL_NONE);

        Log.e(TAG, "render "+ mRenderFrame++);
        mHandler.sendEmptyMessage(MSG_ACTION_COMPLETED);
    }

    @Override
    public void release() {
        EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface);
        EGL14.eglDestroyContext(mEGLDisplay, mEGLContext);
    }

    @Override
    public View getView() {
        return mSurfaceView;
    }

    @Override
    public void setup(int width, int height) {

        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException();
        }
        int[] major = new int[1];
        int[] minor = new int[1];
        if(!EGL14.eglInitialize(mEGLDisplay, major, 0, minor, 0)) {
            throw new RuntimeException();
        }
        int[] attribs = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] num_config = new int[1];
        if(!EGL14.eglChooseConfig(mEGLDisplay, attribs, 0, configs, 0, 1, num_config, 0)) {
            throw new RuntimeException();
        }
        mEGLConfig = configs[0];
        int[] attribs2 = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        mEGLContext = EGL14.eglCreateContext(mEGLDisplay, mEGLConfig, EGL14.EGL_NO_CONTEXT, attribs2, 0);
        if (mEGLContext == EGL14.EGL_NO_CONTEXT) {
            throw new RuntimeException();
        }
        mEGLSurface = EGL14.eglCreateWindowSurface(mEGLDisplay, mEGLConfig, mSurfaceView.getHolder().getSurface(),
                new int[]{EGL14.EGL_NONE}, 0);
        if (mEGLSurface == EGL14.EGL_NO_SURFACE) {
            throw new RuntimeException();
        }
        if(!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
            throw new RuntimeException();
        }

        GLES20.glClearColor(0, 0, 0, 1);

        GLES20.glGenTextures(2, mFrameTex, 0);
        for (int i = 0; i < 2; i++) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFrameTex[i]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,  GLES20.GL_NONE);
        }

        mFrameProcessors.add(createNV12ToRGBA(width, height));
        mFrameProcessors.add(createGrayMask(width, height));

        mRenderTex = mFrameProcessors.get(mFrameProcessors.size()-1).getOutputTexture();
        mEGLContext = EGL14.eglGetCurrentContext();
        String vertexShader =
                "attribute vec2 pos;" +
                "attribute vec2 texPos;" +
                "varying vec2 outTexPos;" +
                "void main() {" +
                "    gl_Position = vec4(pos, 0, 1);" +
                "    outTexPos = texPos;" +
                "}";
        int v = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(v, vertexShader);
        GLES20.glCompileShader(v);

        String samplerType = (mFrameProcessors.get(mFrameProcessors.size()-1).getOutTextureType() == GLES20.GL_TEXTURE_2D) ? "sampler2D" : "samplerExternalOES";
        String fragmentShader =
                "##extension GL_OES_EGL_image_external : require \n" +
                "precision mediump float;" +
                "varying vec2 outTexPos;" +
                "uniform " + samplerType + " texSampler;" +
                "void main() {" +
                "    gl_FragColor = texture2D(texSampler, outTexPos);" +
                "}";
        int f = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(f, fragmentShader);
        GLES20.glCompileShader(f);
        mProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(mProgram, v);
        GLES20.glAttachShader(mProgram, f);
        GLES20.glLinkProgram(mProgram);
        GLES20.glDeleteShader(v);
        GLES20.glDeleteShader(f);
        GLES20.glUseProgram(mProgram);
        mPosLoc = GLES20.glGetAttribLocation(mProgram, "pos");
        mTexPosLoc = GLES20.glGetAttribLocation(mProgram, "texPos");
        mTexSamplerLoc = GLES20.glGetUniformLocation(mProgram, "texSampler");

        Message.obtain(mHandler, MSG_SURFACE_CREATED, null).sendToTarget();
    }

    private FilterBase createGrayMask(int width, int height) {
        FilterBase grayMask = new GrayMask(mEGLDisplay,mEGLConfig, mFrameProcessors.get(mFrameProcessors.size()-1).getOutTextureType(), width, height);
        grayMask.addInputTexture(mFrameProcessors.get(mFrameProcessors.size()-1).getOutputTexture(), GLES20.GL_RGBA, (int tex, int slot, int samplerLoc) -> {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + slot);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
            GLES20.glUniform1i(samplerLoc, slot);
        });
        return grayMask;
    }

    @NonNull
    private FilterBase createNV12ToRGBA(int width, int height) {
        FilterBase nV12ToRGBA = new NV12ToRGBA(mEGLDisplay,mEGLConfig,GLES20.GL_TEXTURE_2D, width, height);
        nV12ToRGBA.addInputTexture(mFrameTex[0], GLES20.GL_LUMINANCE,
                (int tex, int slot, int samplerLoc) -> {
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + slot);
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
                    GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_LUMINANCE,mWidth, mHeight,0,GLES20.GL_LUMINANCE,GLES20.GL_UNSIGNED_BYTE,mYUV.position(0));
                    GLES20.glUniform1i(samplerLoc, slot);
                }
        );
        nV12ToRGBA.addInputTexture(mFrameTex[1], GLES20.GL_LUMINANCE_ALPHA,
                (int tex, int slot, int samplerLoc) -> {
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + slot);
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
                    GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_LUMINANCE_ALPHA,mWidth / 2, mHeight / 2,0,GLES20.GL_LUMINANCE_ALPHA,GLES20.GL_UNSIGNED_BYTE,mYUV.position(mWidth * mHeight));
                    GLES20.glUniform1i(samplerLoc, slot);
                }
        );
        return nV12ToRGBA;
    }

    @Override
    public EGLResources getEGLResource() {
        return new EGLResources(false)
                .setEGLContext(mEGLContext)
                .setEGLConfig(mEGLConfig)
                .setEGLDisplay(mEGLDisplay)
                .setProgram(mProgram)
                .setTexture(mRenderTex)
                .setTextureType(GLES20.GL_TEXTURE_2D)
                .setPosAttrib(new EGLResources.VertexAttrib(2, mPosLoc, GLES20.GL_FLOAT, 16, sVertices, 0))
                .setTexPosAttrib(new EGLResources.VertexAttrib(2, mTexPosLoc, GLES20.GL_FLOAT, 16, sVertices, 2))
                .setDrawOrdersAttrib(new EGLResources.ElementAttrib(6, GLES20.GL_UNSIGNED_BYTE, sDrawOrders, 0));
    }

    @Override
    public boolean isFrameAvailable() {
        return true;
    }

    private class MyCallback implements SurfaceHolder.Callback {

        @Override
        public void surfaceCreated(@NonNull SurfaceHolder holder) {
            Message.obtain(mHandler, MSG_SETUP_EGL).sendToTarget();
        }

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

        }

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder holder) {

        }
    }

}
