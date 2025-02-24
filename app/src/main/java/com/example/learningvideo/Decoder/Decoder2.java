package com.example.learningvideo.Decoder;

import android.content.res.AssetFileDescriptor;
import android.graphics.SurfaceTexture;
import android.hardware.HardwareBuffer;
import android.opengl.EGL14;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.example.learningvideo.GLES.EGLCore;
import com.example.learningvideo.GLES.Utils;
import com.example.learningvideo.IDecoderService;
import com.example.learningvideo.SharedTexture;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

class Decoder2 extends IDecoderService.Stub {

    private HandlerThread mWorkThread;
    public static final int MSG_SETUP_EGL = 1;
    public static final int MSG_UPLOAD_FRAME = 2;
    public static final int MSG_GET_FENCE = 3;
    private static final int MSG_RELEASE = 4;
    Decoder1 mDecoder;
    SharedTexture mSharedTexture;
    private EGLCore mEGLCore;
    int mFrameTexture;
    int mFrameTexTarget;
    int mFBO;
    int mFBOTexture;
    int mFBOTexTarget;
    private int mProgram;
    private int mPosLoc;
    private int mTexPosLoc;
    private int mTexSamplerLoc;
    private volatile boolean mFrameAvailable = false;
    private final Object mLock = new Object();
    private Handler mHandler;

    private ParcelFileDescriptor mFence;
    private volatile boolean mMessageHandled = false;

    private static final FloatBuffer sVertices;
    private static final ByteBuffer sDrawOrders;

    static {
        float[] vertices = {
                -1, 1, 0, 1,
                -1, -1, 0, 0,
                1, -1, 1, 0,
                1, 1, 1, 1
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

    private SurfaceTexture mSurfaceTexture;

    private int mFrameNum = 0;
    private Surface mDecoderSurface;

    @Override
    public void init(AssetFileDescriptor afd) throws RemoteException {
        mWorkThread = new HandlerThread("work-thread");
        mWorkThread.start();
        mHandler = new WorkHandler(mWorkThread.getLooper());
        mDecoder = new Decoder1(afd, null);
        mFrameTexTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
        mFBOTexTarget = GLES20.GL_TEXTURE_2D;
    }

    @Override
    public void start(HardwareBuffer hwBuf) throws RemoteException {
        postMsgAndWaitResponse(MSG_SETUP_EGL, hwBuf);
        mDecoder.start(mDecoderSurface);
    }

    private void postMsgAndWaitResponse(int msg, Object obj) {
        synchronized (mLock) {
            Message.obtain(mHandler, msg, obj).sendToTarget();
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
    public boolean decode(ParcelFileDescriptor fence) throws RemoteException {
        mDecoder.decode();
        boolean eos = mDecoder.isEOS();
        if (!eos) {
            postMsgAndWaitResponse(MSG_UPLOAD_FRAME, fence);
        }

        return eos;
    }

    @Override
    public int getHeight() throws RemoteException {
        return mDecoder.getHeight();
    }

    @Override
    public int getWidth() throws RemoteException {
        return mDecoder.getWidth();
    }

    @Override
    public void release() throws RemoteException {
        postMsgAndWaitResponse(MSG_RELEASE, null);
    }

    @Override
    public ParcelFileDescriptor getFence() {
        postMsgAndWaitResponse(MSG_GET_FENCE, null);
        return mFence;
    }

    public class WorkHandler extends Handler {
        public WorkHandler(@NonNull Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            synchronized (mLock) {
                switch (msg.what) {
                    case MSG_SETUP_EGL:
                        onSetupEGL((HardwareBuffer) msg.obj);
                        break;
                    case MSG_UPLOAD_FRAME:
                        if (!mFrameAvailable) {
                            Message.obtain(this, MSG_UPLOAD_FRAME, msg.obj).sendToTarget();
                        } else {
                            onUploadFrame((ParcelFileDescriptor)msg.obj);
                        }
                        break;
                    case MSG_GET_FENCE:
                        mFence = mSharedTexture.createFenceFd();
                        break;
                    case MSG_RELEASE:
                        mDecoder.release();
                        GLES20.glDeleteProgram(mProgram);
                        int[] values  = new int[1];
                        values[0] = mFrameTexture;
                        GLES20.glDeleteTextures(1, values, 0);
                        values[0] = mFBOTexture;
                        GLES20.glDeleteTextures(1, values, 0);
                        values[0] = mFBO;
                        GLES20.glDeleteFramebuffers(1, values, 0);
                        mEGLCore.release();
                        mDecoderSurface.release();
                        mDecoderSurface = null;
                        mSharedTexture = null;
                }
                mMessageHandled = true;
                mLock.notify();
            }
        }
    }

    private void onSetupEGL(HardwareBuffer hwBuf) {
        mEGLCore = new EGLCore(EGL14.EGL_NO_CONTEXT);
        mEGLCore.setPbufferSurface(mDecoder.getWidth(), mDecoder.getHeight());
        mEGLCore.makeCurrent();
        GLES20.glClearColor(1, 0, 0, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        mFrameTexture = Utils.genTexture(mFrameTexTarget, null, mDecoder.getWidth(), mDecoder.getHeight(), GLES20.GL_RGBA);
        mSurfaceTexture = new SurfaceTexture(mFrameTexture);
        mSurfaceTexture.setOnFrameAvailableListener(
                (SurfaceTexture st) -> {
                    mFrameAvailable = true;
                }
        );
        mDecoderSurface = new Surface(mSurfaceTexture);

        mFBOTexture = Utils.genTexture(mFBOTexTarget, null, mDecoder.getWidth(), mDecoder.getHeight(), GLES20.GL_RGBA);
        mSharedTexture = new SharedTexture(hwBuf);
        mSharedTexture.bindTexture(mFBOTexture, GLES20.GL_TEXTURE_2D);
        mFBO = Utils.genFrameBuffer(GLES20.GL_COLOR_ATTACHMENT0, mFBOTexTarget, mFBOTexture);

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
                "uniform samplerExternalOES texSampler;" +
                "void main() {" +
                "    gl_FragColor = texture2D(texSampler, outTexPos);" +
                "}";
        mProgram = Utils.createProgram(vertexShader, fragmentShader);

        mPosLoc = GLES20.glGetAttribLocation(mProgram, "pos");
        mTexPosLoc = GLES20.glGetAttribLocation(mProgram, "texPos");
        mTexSamplerLoc = GLES20.glGetUniformLocation(mProgram, "texSampler");
    }

    private void onUploadFrame(ParcelFileDescriptor fence) {
        mEGLCore.makeCurrent();
        if (fence != null) {
            mSharedTexture.waitFenceFd(fence);
        }
        int[] size = mEGLCore.getSurfaceSize();
        GLES20.glUseProgram(mProgram);
        GLES20.glViewport(0, 0, size[0], size[1]);
        GLES20.glVertexAttribPointer(mPosLoc, 2, GLES20.GL_FLOAT, false, 16, sVertices.position(0));
        GLES20.glEnableVertexAttribArray(mPosLoc);
        GLES20.glVertexAttribPointer(mTexPosLoc, 2, GLES20.GL_FLOAT, false, 16, sVertices.position(2));
        GLES20.glEnableVertexAttribArray(mTexPosLoc);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFBO);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        GLES20.glUniform1i(mTexSamplerLoc, 0);
        mSurfaceTexture.updateTexImage();
        GLES20.glDrawElements(GLES20.GL_TRIANGLE_STRIP, 6, GLES20.GL_UNSIGNED_BYTE, sDrawOrders.position(0));
        mEGLCore.swapBuffer();

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_NONE);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_NONE);
        GLES20.glDisableVertexAttribArray(mPosLoc);
        GLES20.glDisableVertexAttribArray(mTexPosLoc);
        //GLES20.glFinish();
        mFrameAvailable = false;
    }
}
