package com.example.learningvideo.Renderer;

import static com.example.learningvideo.Core.MSG_ACTION_COMPLETED;
import static com.example.learningvideo.Core.MSG_SURFACE_CREATED;

import android.app.Activity;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
//import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.example.learningvideo.EGLResources;
import com.example.learningvideo.Filter.DoNothingButUploadImage;
import com.example.learningvideo.Filter.FilterBase;
import com.example.learningvideo.Filter.FrameCapture;
import com.example.learningvideo.Filter.GrayMask;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.egl.EGLConfig;

public class Renderer2 extends RendererBase {
    private static final String TAG = "Renderer2";
    private Handler mHandler;
    private MySurfaceView mSurfaceView;
    private static final FloatBuffer sVertices;
    private static final ByteBuffer sDrawOrders;
    private int mRenderTex;
    private int mProgram;
    private int mPosLoc;
    private int mTexPosLoc;
    private int mTexSamplerLoc;
    private EGLContext mEGLContext;
    private SurfaceTexture mSurfaceTexture;
    private int mRenderFrame = 0;
    int mWidth = -1;
    int mHeight = -1;
    private boolean mSizeChanged = false;
    private Context mContext;
    private final List<FilterBase> mFilterList = new ArrayList<>();

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

    private EGLDisplay mEGLDisplay;
    private EGLSurface mEGLSurface;
    private android.opengl.EGLConfig mEGLConfig;

    public Renderer2(Context context, Handler handler) {
        super(context, handler);
        mContext = context;
        mHandler = handler;
        mSurfaceView = new MySurfaceView(context);
        mSurfaceView.setEGLContextClientVersion(2);
        mSurfaceView.setRenderer(mSurfaceView);
        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    public boolean isFrameAvailable() {
        return mIsFrameAvailable;
    }

    private volatile boolean mIsFrameAvailable = false;

    @Override
    public void start(int width, int height) {
        mWidth = width;
        mHeight = height;
        ((Activity)mContext).runOnUiThread(()->{
            DisplayMetrics dm = mSurfaceView.getResources().getDisplayMetrics();
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);

            lp.width = dm.widthPixels;
            lp.height = dm.widthPixels * mHeight / mWidth;
            lp.gravity = Gravity.CENTER;
            mSurfaceView.setLayoutParams(lp);
        });
    }

    @Override
    public void render(Message msg) {
        mSizeChanged = (mWidth != msg.arg1 || mHeight != msg.arg2);
        if (mSizeChanged) {
            mWidth = msg.arg1;
            mHeight = msg.arg2;
        }
        mSurfaceView.requestRender();
    }

    @Override
    public void release() {
        mSurfaceView.queueEvent(
                () -> {
                    GLES20.glDeleteTextures(1, new int[]{mRenderTex}, 0);
                    GLES20.glDeleteProgram(mProgram);
                });
    }

    @Override
    public EGLResources getEGLResource() {
        return new EGLResources(true)
                .setEGLContext(mEGLContext)
                .setProgram(mProgram)
                .setTexture(mRenderTex)
                .setTextureType(mFilterList.get(mFilterList.size()-1).getOutTextureType())
                .setPosAttrib(new EGLResources.VertexAttrib(2, mPosLoc, GLES20.GL_FLOAT, 16, sVertices, 0))
                .setTexPosAttrib(new EGLResources.VertexAttrib(2, mTexPosLoc, GLES20.GL_FLOAT, 16, sVertices, 2))
                .setDrawOrdersAttrib(new EGLResources.ElementAttrib(6, GLES20.GL_UNSIGNED_BYTE, sDrawOrders, 0));
    }

    @Override
    public View getView() {
        return mSurfaceView;
    }

    @Override
    public void setup(int width, int height) {
        GLES20.glClearColor(0, 0, 0, 1);

        int[] frameTex = new int[1];
        GLES20.glGenTextures(1, frameTex, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, frameTex[0]);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_NONE);

        android.opengl.EGLConfig[] configs = new android.opengl.EGLConfig[1];
        int[] num_configs = new int[1];
        if(!EGL14.eglGetConfigs(EGL14.eglGetCurrentDisplay(), configs, 0, 1, num_configs, 0)) {
            throw new RuntimeException();
        }
        mEGLContext = EGL14.eglGetCurrentContext();
        mEGLDisplay = EGL14.eglGetCurrentDisplay();
        mEGLSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
        mEGLConfig = configs[0];
        FilterBase doNothing = new DoNothingButUploadImage(null, mEGLContext, mEGLDisplay, mEGLConfig, GLES11Ext.GL_TEXTURE_EXTERNAL_OES, width, height);
        doNothing.addInputTexture(frameTex[0], GLES20.GL_RGBA,
                (int tex, int slot, int samplerLoc) -> {
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + slot);
                    mSurfaceTexture.updateTexImage();
                    GLES20.glUniform1i(samplerLoc, slot);
                });
        mFilterList.add(doNothing);

        FilterBase grayMask = new GrayMask(mFilterList.get(mFilterList.size()-1), mEGLContext, mEGLDisplay, mEGLConfig, mFilterList.get(mFilterList.size()-1).getOutTextureType(), width, height);
        grayMask.addInputTexture(mFilterList.get(mFilterList.size()-1).getOutputTexture(), GLES20.GL_RGBA,
                (int tex, int slot, int samplerLoc) -> {
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + slot);
                    //mSurfaceTexture.updateTexImage();
                    GLES20.glBindTexture(mFilterList.get(mFilterList.size()-1).getOutTextureType(), tex);
                    GLES20.glUniform1i(samplerLoc, slot);
                });
        mFilterList.add(grayMask);

        FilterBase frameCapture = new FrameCapture(mFilterList.get(mFilterList.size()-1), mEGLContext, mEGLDisplay,mEGLConfig, mFilterList.get(mFilterList.size()-1).getOutTextureType(), width, height);
        frameCapture.addInputTexture(mFilterList.get(mFilterList.size()-1).getOutputTexture(), GLES20.GL_RGBA);
        mFilterList.add(frameCapture);

        //EGL14.eglMakeCurrent(EGL14.eglGetCurrentDisplay(), s, EGL14.eglGetCurrentSurface(EGL14.EGL_READ), mEGLContext);
        mRenderTex = mFilterList.get(mFilterList.size()-1).getOutputTexture();
        mSurfaceTexture = new SurfaceTexture(frameTex[0]);
        mSurfaceTexture.setOnFrameAvailableListener((SurfaceTexture surfaceTexture)-> {mIsFrameAvailable = true;}, mHandler);

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

        String fragmentShader =
                "#extension GL_OES_EGL_image_external : require \n" +
                "uniform " + (mFilterList.get(mFilterList.size()-1).getOutTextureType() == GLES20.GL_TEXTURE_2D ? "sampler2D" : "samplerExternalOES") + " texSampler;" +
                "varying vec2 outTexPos;" +
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
    }

    private class MySurfaceView extends GLSurfaceView implements GLSurfaceView.Renderer{

        public MySurfaceView(Context context) {
            super(context);
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            while(mWidth < 0 || mHeight < 0);
            setup(mWidth, mHeight);
            Message.obtain(mHandler, MSG_SURFACE_CREATED, new Surface(mSurfaceTexture)).sendToTarget();
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            if (!mIsFrameAvailable) return;
            if (mSizeChanged) {
                ((Activity)mContext).runOnUiThread(()->{
                    DisplayMetrics dm = getResources().getDisplayMetrics();
                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);

                    lp.width = dm.widthPixels;
                    lp.height = dm.widthPixels * mHeight / mWidth;
                    lp.gravity = Gravity.CENTER;
                    this.setLayoutParams(lp);
                });
            }
            mEGLSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
            for (FilterBase fp : mFilterList) {
                fp.process(mEGLContext, mEGLDisplay, mEGLSurface);
            }
            int[] size = new int[2];
            EGL14.eglQuerySurface(mEGLDisplay, mEGLSurface, EGL14.EGL_WIDTH, size, 0);
            EGL14.eglQuerySurface(mEGLDisplay, mEGLSurface, EGL14.EGL_HEIGHT, size, 1);
            GLES20.glViewport(0, 0, size[0], size[1]);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glUseProgram(mProgram);
            GLES20.glVertexAttribPointer(mPosLoc, 2, GLES20.GL_FLOAT, false, 16, sVertices.position(0));
            GLES20.glEnableVertexAttribArray(mPosLoc);
            GLES20.glVertexAttribPointer(mTexPosLoc, 2, GLES20.GL_FLOAT, false, 16, sVertices.position(2));
            GLES20.glEnableVertexAttribArray(mTexPosLoc);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(mFilterList.get(mFilterList.size()-1).getOutTextureType(), mRenderTex);
            GLES20.glUniform1i(mTexSamplerLoc, 0);
            GLES20.glDrawElements(GLES20.GL_TRIANGLE_STRIP, 6, GLES20.GL_UNSIGNED_BYTE, sDrawOrders.position(0));
            GLES20.glBindTexture(mFilterList.get(mFilterList.size()-1).getOutTextureType(), GLES20.GL_NONE);
            GLES20.glDisableVertexAttribArray(mPosLoc);
            GLES20.glDisableVertexAttribArray(mTexPosLoc);
            Log.e(TAG, "render "+ mRenderFrame++);
            mIsFrameAvailable = false;
            queueEvent(() -> {
                GLES20.glFinish();
                mHandler.sendEmptyMessage(MSG_ACTION_COMPLETED);
            });
        }

    }
}
