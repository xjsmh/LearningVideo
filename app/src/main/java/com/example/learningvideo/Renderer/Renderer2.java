package com.example.learningvideo.Renderer;

import static com.example.learningvideo.Core.MSG_NEXT_ACTION;
import static com.example.learningvideo.Core.MSG_RENDER;
import static com.example.learningvideo.Core.MSG_START;
import static com.example.learningvideo.Core.MSG_SURFACE_CREATED;

import android.app.Activity;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
//import android.opengl.EGLConfig;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

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

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.opengles.GL;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.egl.EGLConfig;

public class Renderer2 extends RendererBase {
    private static final String TAG = "Renderer2";
    private Handler mHandler;
    private GLSurfaceView mSurfaceView;
    private static final FloatBuffer sVertices;
    private static final ByteBuffer sDrawOrders;
    private int mRenderTex;
    private int mProgram;
    private int mPosLoc;
    private int mTexPosLoc;
    private int mTexSamplerLoc;
    private SurfaceTexture mFrameSurfaceTexture;
    int mFrameTextureType;
    private int mRenderFrame = 0;
    int mWidth = -1;
    int mHeight = -1;
    private volatile EGLCore mEGLCore;
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

    private volatile Surface mDecoderSurface;
    private int mFrameTexture;
    private volatile boolean mReadyToDraw;
    private volatile boolean mMessageHandled = false;
    private final Object mLock = new Object();

    public Renderer2(Handler handler) {
        super(handler);
        mHandler = handler;
        mFrameTextureType = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
    }

    public boolean isFrameAvailable() {
        return mIsFrameAvailable;
    }

    private volatile boolean mIsFrameAvailable = false;

    @Override
    public void render(Message msg) {
        mSurfaceView.requestRender();
    }

    @Override
    public void release() {
        mSurfaceView.queueEvent(
                () -> {
                    GLES20.glDeleteTextures(1, new int[]{mRenderTex}, 0);
                    GLES20.glDeleteProgram(mProgram);
                    mDecoderSurface.release();
                    mDecoderSurface = null;
                });
    }

    @Override
    public int getViewId() {
        return R.id.glSurfaceView;
    }

    @Override
    public void init(Context context) {
        if (context != null) {
            mSurfaceView = ((Activity) context).findViewById(R.id.glSurfaceView);
            MyCallback cb = new MyCallback();
            mSurfaceView.getHolder().addCallback(cb);
            mSurfaceView.setEGLConfigChooser(new GLSurfaceView.EGLConfigChooser() {
                @Override
                public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
                    int[] attrib_list = {
                            EGL14.EGL_RED_SIZE, 8,
                            EGL14.EGL_GREEN_SIZE, 8,
                            EGL14.EGL_BLUE_SIZE, 8,
                            EGL14.EGL_ALPHA_SIZE, 8,
                            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                            EGL14.EGL_NONE
                    };
                    EGLConfig[] configs = new EGLConfig[1];
                    int[] num_configs = new int[1];
                    if(!egl.eglChooseConfig(display, attrib_list, configs, 1, num_configs))                            {
                        throw new RuntimeException();
                    }
                    return configs[0];
                }
            });
            mSurfaceView.setEGLContextClientVersion(2);
            mSurfaceView.setRenderer(cb);
            mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        }
    }

    @Override
    public void setup(int width, int height) {
        mSurfaceView.queueEvent(()-> {
            mFilterList.clear();
            synchronized (mLock) {
                GLES20.glClearColor(0, 0, 0, 1);

                mFrameTexture = Utils.genTexture(mFrameTextureType, null, width, height, GLES20.GL_RGBA);

                android.opengl.EGLConfig[] configs = new android.opengl.EGLConfig[1];
                int[] num_configs = new int[1];
                if (!EGL14.eglGetConfigs(EGL14.eglGetCurrentDisplay(), configs, 0, 1, num_configs, 0)) {
                    throw new RuntimeException();
                }

                mEGLCore = new EGLCore(EGL14.eglGetCurrentContext(), EGL14.eglGetCurrentDisplay(), EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW), configs[0]);
                FilterBase doNothing = new DoNothingButUploadImage(mEGLCore, mFrameTextureType, width, height);
                doNothing.addInputTexture(mFrameTexture, GLES20.GL_RGBA,
                        (int tex, int slot, int samplerLoc) -> {
                            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + slot);
                            mFrameSurfaceTexture.updateTexImage();
                            GLES20.glUniform1i(samplerLoc, slot);
                        });
                mFilterList.add(doNothing);

                FilterBase grayMask = new GrayMask(mFilterList.get(mFilterList.size() - 1), width, height);
                grayMask.addInputTexture(mFilterList.get(mFilterList.size() - 1).getOutputTexture(), GLES20.GL_RGBA,
                        (int tex, int slot, int samplerLoc) -> {
                            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + slot);
                            //mSurfaceTexture.updateTexImage();
                            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex);
                            GLES20.glUniform1i(samplerLoc, slot);
                        });
                mFilterList.add(grayMask);

                FilterBase frameCapture = new FrameCapture(mFilterList.get(mFilterList.size() - 1), width, height);
                frameCapture.addInputTexture(mFilterList.get(mFilterList.size() - 1).getOutputTexture(), GLES20.GL_RGBA);
                mFilterList.add(frameCapture);

                //EGL14.eglMakeCurrent(EGL14.eglGetCurrentDisplay(), s, EGL14.eglGetCurrentSurface(EGL14.EGL_READ), mEGLContext);
                mRenderTex = mFilterList.get(mFilterList.size() - 1).getOutputTexture();
                mFrameSurfaceTexture = new SurfaceTexture(mFrameTexture);
                mFrameSurfaceTexture.setOnFrameAvailableListener((SurfaceTexture surfaceTexture) -> {
                    mIsFrameAvailable = true;
                    mHandler.sendEmptyMessage(MSG_RENDER);
                }, mHandler);

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
                                "uniform " + (mFilterList.get(mFilterList.size() - 1).getOutTextureType() == GLES20.GL_TEXTURE_2D ? "sampler2D" : "samplerExternalOES") + " texSampler;" +
                                "varying vec2 outTexPos;" +
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
                Log.d(TAG, "setup mReadyToDraw = true;");
                mMessageHandled = true;
                mLock.notify();
            }
        });
        synchronized (mLock) {
            while (!mMessageHandled) {
                try {
                    mLock.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            mMessageHandled = false;
        }
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

    @Override
    public void draw() {
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
    }

    private class MyCallback implements GLSurfaceView.Renderer, SurfaceHolder.Callback{


        @Override
        public void surfaceCreated(@NonNull SurfaceHolder holder) {

        }

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            mReadyToDraw = false;
        }


        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            mEGLCore = null;
            Message.obtain(mHandler, MSG_SURFACE_CREATED).sendToTarget();
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
            if (mEGLCore != null) {
                mEGLCore.updateSurface(EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW));
                mReadyToDraw = true;
                mHandler.sendEmptyMessage(MSG_START);
            }
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            if (!mReadyToDraw) {
                return;
            }
            if (!mIsFrameAvailable) {
                Log.e(TAG, "why here ?");
                mEGLCore.makeCurrent();
                draw();
                return;
            }

            for (FilterBase fp : mFilterList) {
                fp.process();
            }
            mEGLCore.makeCurrent();
            int[] size = mEGLCore.getSurfaceSize();
            GLES20.glViewport(0, 0, size[0], size[1]);
            Renderer2.this.draw();
            Log.e(TAG, "render "+ mRenderFrame++);
            mIsFrameAvailable = false;
            mSurfaceView.queueEvent(() -> {
                mHandler.sendEmptyMessage(MSG_NEXT_ACTION);
            });
        }

    }
}
