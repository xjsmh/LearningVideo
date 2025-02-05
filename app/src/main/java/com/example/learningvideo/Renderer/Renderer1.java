package com.example.learningvideo.Renderer;

import static com.example.learningvideo.Core.MSG_ACTION_COMPLETED;
import static com.example.learningvideo.Core.MSG_SETUP_EGL;
import static com.example.learningvideo.Core.MSG_SURFACE_CREATED;

import android.app.Activity;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
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

public class Renderer1 extends RendererBase implements TextureView.SurfaceTextureListener{
    private static final String TAG = "Renderer1";
    private int mRenderFrame = 0;

    private Handler mHandler;
    TextureView mTextureView;
    SurfaceTexture mRenderSurfaceTexture;
    SurfaceTexture mFrameSurfaceTexture;
    private EGLDisplay mEGLDisplay;
    private EGLConfig mEGLConfig;
    private EGLContext mEGLContext;
    private EGLSurface mEGLSurface;
    private volatile boolean mIsFrameAvailable;
    private final List<FilterBase> mFilterList = new ArrayList<>();
    private int mProgram;
    private int mPosLoc;
    private int mTexPosLoc;
    private int mTexSamplerLoc;
    private int mWidth;
    private int mHeight;
    private Context mContext;
    private static final FloatBuffer sVertices;
    private static final ByteBuffer sDrawOrders;

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

    public Renderer1(Context context, Handler handler) {
        super(context, handler);
        mContext = context;
        mHandler = handler;
        mTextureView = new TextureView(mContext);
        mTextureView.setSurfaceTextureListener(this);
    }

    @Override
    public void start(int width, int height) {
        mWidth = width;
        mHeight = height;
        DisplayMetrics dm = mTextureView.getResources().getDisplayMetrics();
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        lp.width = dm.widthPixels;
        lp.height = dm.widthPixels * mHeight / mWidth;
        lp.gravity = Gravity.CENTER;
        ((Activity)mContext).runOnUiThread(()->{mTextureView.setLayoutParams(lp);});
    }

    @Override
    public void render(Message msg) {
        if (mWidth != msg.arg1 || mHeight != msg.arg2) {
            mWidth = msg.arg1;
            mHeight = msg.arg2;
            DisplayMetrics dm = mTextureView.getResources().getDisplayMetrics();
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
            lp.width = dm.widthPixels;
            lp.height = dm.widthPixels * mHeight / mWidth;
            lp.gravity = Gravity.CENTER;
            ((Activity)mContext).runOnUiThread(()-> {mTextureView.setLayoutParams(lp);});
        }

        EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext);
        int[] size = new int[2];
        EGL14.eglQuerySurface(EGL14.eglGetCurrentDisplay(), EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW), EGL14.EGL_WIDTH, size, 0);
        EGL14.eglQuerySurface(EGL14.eglGetCurrentDisplay(), EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW), EGL14.EGL_HEIGHT, size, 1);
        GLES20.glViewport(0,0, size[0], size[1]);

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        for (FilterBase fp : mFilterList) {
            fp.process(mEGLContext, mEGLDisplay, mEGLSurface);
        }

        GLES20.glUseProgram(mProgram);
        GLES20.glVertexAttribPointer(mPosLoc, 2, GLES20.GL_FLOAT, false, 16, sVertices.position(0));
        GLES20.glEnableVertexAttribArray(mPosLoc);
        GLES20.glVertexAttribPointer(mTexPosLoc, 2, GLES20.GL_FLOAT, false, 16, sVertices.position(2));
        GLES20.glEnableVertexAttribArray(mTexPosLoc);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(mFilterList.get(mFilterList.size()-1).getOutTextureType(), mFilterList.get(mFilterList.size()-1).getOutputTexture());
        GLES20.glUniform1i(mTexSamplerLoc, 0);
        GLES20.glDrawElements(GLES20.GL_TRIANGLE_STRIP, 6, GLES20.GL_UNSIGNED_BYTE, sDrawOrders.position(0));
        EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface);
        Log.e(TAG, "render "+ mRenderFrame++);
        GLES20.glBindTexture(mFilterList.get(mFilterList.size()-1).getOutTextureType(), GLES20.GL_NONE);
        GLES20.glDisableVertexAttribArray(mPosLoc);
        GLES20.glDisableVertexAttribArray(mTexPosLoc);

        mIsFrameAvailable = false;

        Message.obtain(mHandler, MSG_ACTION_COMPLETED).sendToTarget();

    }

    @Override
    public void release() {

    }

    @Override
    public View getView() {
        return mTextureView;
    }

    @Override
    public void setup(int width, int height) {
        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException();
        }
        int[] version = new int[2];
        if(!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
            throw new RuntimeException();
        }
        int[] attrib_list = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] num_configs = new int[1];
        if(!EGL14.eglChooseConfig(mEGLDisplay, attrib_list, 0, configs, 0, 1, num_configs, 0))                            {
            throw new RuntimeException();
        }
        mEGLConfig = configs[0];
        attrib_list = new int[]{
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        mEGLContext = EGL14.eglCreateContext(mEGLDisplay, mEGLConfig, EGL14.EGL_NO_CONTEXT, attrib_list, 0);
        if (mEGLContext == EGL14.EGL_NO_CONTEXT) {
            throw new RuntimeException();
        }
        mEGLSurface = EGL14.eglCreateWindowSurface(mEGLDisplay, mEGLConfig, mRenderSurfaceTexture, new int[]{EGL14.EGL_NONE}, 0);
        if (mEGLSurface == EGL14.EGL_NO_SURFACE) {
            throw new RuntimeException();
        }
        if(!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
            throw new RuntimeException();
        }

        GLES20.glClearColor(0, 0, 0, 1);

        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        int frameTexture = textures[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, frameTexture);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_NONE);
        mFrameSurfaceTexture = new SurfaceTexture(frameTexture);
        mFrameSurfaceTexture.setOnFrameAvailableListener( (SurfaceTexture st) -> { mIsFrameAvailable = true; } );

        FilterBase doNothing = new DoNothingButUploadImage(null, mEGLContext, mEGLDisplay, configs[0], GLES11Ext.GL_TEXTURE_EXTERNAL_OES, width, height);
        doNothing.addInputTexture(frameTexture, GLES20.GL_RGBA,
                (int tex, int slot, int samplerLoc) -> {
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + slot);
                    mFrameSurfaceTexture.updateTexImage();
                    GLES20.glUniform1i(samplerLoc, slot);
                });
        mFilterList.add(doNothing);

        FilterBase grayMask = new GrayMask(mFilterList.get(mFilterList.size()-1), mEGLContext, mEGLDisplay, configs[0], mFilterList.get(mFilterList.size()-1).getOutTextureType(), width, height);
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

        String verTexShader =
                "attribute vec2 pos;" +
                "attribute vec2 texPos;" +
                "varying vec2 outTexPos;" +
                "void main() {" +
                "    gl_Position = vec4(pos, 0, 1);" +
                "    outTexPos = texPos;" +
                "}";
        int v = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(v, verTexShader);
        GLES20.glCompileShader(v);

        String outTexType = mFilterList.get(mFilterList.size()-1).getOutTextureType() == GLES20.GL_TEXTURE_2D ? "sampler2D" : "samplerExternalOES";
        String fragmentShader =
                "#extension GL_OES_EGL_image_external : require \n" +
                "precision mediump float;" +
                "varying vec2 outTexPos;" +
                "uniform " + outTexType + " texSampler;" +
                "void main() {" +
                "    gl_FragColor = texture2D(texSampler, outTexPos);" +
                "}";
        int f = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(f, fragmentShader);
        GLES20.glCompileShader(f);
        
        mProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(mProgram, v);
        GLES20.glAttachShader(mProgram,f);
        GLES20.glLinkProgram(mProgram);
        GLES20.glDeleteShader(v);
        GLES20.glDeleteShader(f);
        
        GLES20.glUseProgram(mProgram);
        mPosLoc = GLES20.glGetAttribLocation(mProgram, "pos");
        mTexPosLoc = GLES20.glGetAttribLocation(mProgram, "texPos");
        mTexSamplerLoc = GLES20.glGetUniformLocation(mProgram, "texSampler");

        Message.obtain(mHandler, MSG_SURFACE_CREATED, new Surface(mFrameSurfaceTexture)).sendToTarget();
    }

    @Override
    public boolean isFrameAvailable() {
        return mIsFrameAvailable;
    }

    @Override
    public EGLResources getEGLResource() {
        return new EGLResources(false)
                .setEGLContext(mEGLContext)
                .setEGLDisplay(mEGLDisplay)
                .setEGLConfig(mEGLConfig)
                .setProgram(mProgram)
                .setPosAttrib(new EGLResources.VertexAttrib(2, mPosLoc, GLES20.GL_FLOAT, 16, sVertices, 0))
                .setTexPosAttrib(new EGLResources.VertexAttrib(2, mTexPosLoc, GLES20.GL_FLOAT, 16, sVertices, 2))
                .setDrawOrdersAttrib(new EGLResources.ElementAttrib(6, GLES20.GL_UNSIGNED_BYTE, sDrawOrders, 0))
                .setTextureType(mFilterList.get(mFilterList.size()-1).getOutTextureType())
                .setTexture(mFilterList.get(mFilterList.size()-1).getOutputTexture());
    }

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        mRenderSurfaceTexture = surface;
        Message.obtain(mHandler, MSG_SETUP_EGL).sendToTarget();
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

    }
}
