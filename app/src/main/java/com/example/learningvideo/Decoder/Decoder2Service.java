package com.example.learningvideo.Decoder;

import android.app.Service;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.SurfaceTexture;
import android.hardware.HardwareBuffer;
import android.opengl.EGL14;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.example.learningvideo.IDecoder2Service;
import com.example.learningvideo.SharedTexture;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;


public class Decoder2Service extends Service {
    private final HandlerThread mWorkThread;
    EGLSurface mEGLSurface;
    String TAG = "Decode-Service";
    public static final int MSG_SETUP_EGL = 1;
    public static final int MSG_UPLOAD_FRAME = 2;
    public static final int MSG_GET_FENCE = 3;
    DecoderBase mDecoder;
    SharedTexture mSharedTexture;
    EGLDisplay mEGLDisplay;
    EGLContext mEGLContext;
    EGLConfig mEGLConfig;
    int mFrameTexture;
    int mFBO;
    int mFBOTexture;
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
                -1,-1, 0, 0,
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

    public Decoder2Service() {
        if(!SharedTexture.isAvailable()) {
            throw new RuntimeException();
        }
        mWorkThread = new HandlerThread("work-thread");
        mWorkThread.start();
        mHandler = new WorkHandler(mWorkThread.getLooper());
    }

    private void onSetupEGL(HardwareBuffer hwBuf) {
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
        int[] attrib_list = {
                EGL14.EGL_WIDTH, mDecoder.getWidth(),
                EGL14.EGL_HEIGHT, mDecoder.getHeight(),
                EGL14.EGL_NONE
        };
        mEGLSurface = EGL14.eglCreatePbufferSurface(mEGLDisplay, mEGLConfig, attrib_list, 0);
        if (mEGLSurface == EGL14.EGL_NO_SURFACE) {
            throw new RuntimeException();
        }

        if(!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
            throw new RuntimeException();
        }
        GLES20.glClearColor(1, 0, 0, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        int[] temp = new int[1];
        GLES20.glGenTextures(1, temp, 0);
        mFrameTexture = temp[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mFrameTexture);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,  GLES20.GL_NONE);
        mSurfaceTexture = new SurfaceTexture(mFrameTexture);
        mSurfaceTexture.setOnFrameAvailableListener(
                (SurfaceTexture st) -> { mFrameAvailable = true; Log.e(TAG, "frame available"); }
        );
        mDecoder.setObject(new Surface(mSurfaceTexture));

        GLES20.glGenTextures(1, temp, 0);
        mFBOTexture = temp[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFBOTexture);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, mDecoder.getWidth(), mDecoder.getHeight(), 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,  GLES20.GL_NONE);
        mSharedTexture = new SharedTexture(hwBuf);
        mSharedTexture.bindTexture(mFBOTexture);

        GLES20.glGenFramebuffers(1, temp, 0);
        mFBO = temp[0];
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFBO);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, mFBOTexture, 0);
        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException();
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_NONE);

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
                        "precision mediump float;" +
                        "varying vec2 outTexPos;" +
                        "uniform samplerExternalOES texSampler;" +
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

    public class WorkHandler extends Handler {
        public WorkHandler(@NonNull Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            synchronized (mLock) {
                switch (msg.what) {
                    case MSG_SETUP_EGL:
                        onSetupEGL((HardwareBuffer)msg.obj);
                        break;
                    case MSG_UPLOAD_FRAME:
                        if (!mFrameAvailable) {
                            Message.obtain(this, MSG_UPLOAD_FRAME).sendToTarget();
                        } else {
                            onUploadFrame();
                        }
                        break;
                    case MSG_GET_FENCE:
                        mFence = mSharedTexture.createFenceFd();
                        break;
                }
                mMessageHandled = true;
                mLock.notify();
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new DecoderBinder();
    }

    class DecoderBinder extends IDecoder2Service.Stub {

        private int mFrameNum = 0;
        @Override
        public void init(AssetFileDescriptor afd) throws RemoteException {
            mDecoder = new Decoder1(afd, null, null);
        }



        @Override
        public void start(HardwareBuffer hwBuf) throws RemoteException {
            synchronized (mLock) {
                Message.obtain(mHandler, MSG_SETUP_EGL, hwBuf).sendToTarget();
                while (!mMessageHandled) {
                    try {
                        mLock.wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                mMessageHandled = false;
            }
            mDecoder.start();
        }

        @Override
        public boolean decode() throws RemoteException {
            mDecoder.decode();
            boolean eos = mDecoder.isEOS();
            try {
                if (!eos) {
                    synchronized (mLock) {
                        Message.obtain(mHandler, MSG_UPLOAD_FRAME).sendToTarget();
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
            } catch (RuntimeException e) {
                throw new RuntimeException(e);
            } catch (Exception e) {
                Log.e("BBB", e.toString());
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
            mDecoder.release();
        }

        @Override
        public ParcelFileDescriptor getFence(){
            synchronized (mLock) {
                Message.obtain(mHandler, MSG_GET_FENCE).sendToTarget();
                while (!mMessageHandled) {
                    try {
                        mLock.wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                mMessageHandled = false;
            }
            return mFence;
        }
    }

    private void onUploadFrame() {
        if (!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
            throw new RuntimeException();
        }
        int[] size = new int[2];
        EGL14.eglQuerySurface(mEGLDisplay, mEGLSurface, EGL14.EGL_WIDTH, size, 0);
        EGL14.eglQuerySurface(mEGLDisplay, mEGLSurface, EGL14.EGL_HEIGHT, size, 1);
        int renderWidth = size[0];
        int renderHeight = size[1];
        GLES20.glUseProgram(mProgram);
        GLES20.glViewport(0, 0, renderWidth, renderHeight);
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
        EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface);

        ByteBuffer pixels = ByteBuffer.allocateDirect(size[0] * size[1] * 4).order(ByteOrder.nativeOrder());
                    /*GLES20.glReadPixels(0,0, size[0], size[1], GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels);
                    BufferedOutputStream bos = null;
                    try {
                        bos = new BufferedOutputStream(new FileOutputStream("sdcard/Download/decode_" + (mFrameNum++) + ".jpg"));
                        Bitmap bmp = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
                        bmp.copyPixelsFromBuffer(pixels);
                        bmp.compress(Bitmap.CompressFormat.JPEG, 70, bos);
                        bmp.recycle();
                    } catch (FileNotFoundException e) {
                        throw new RuntimeException(e);
                    } finally {
                        if (bos != null) {
                            try {
                                bos.close();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }*/

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_NONE);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_NONE);
        GLES20.glDisableVertexAttribArray(mPosLoc);
        GLES20.glDisableVertexAttribArray(mTexPosLoc);
        //GLES20.glFinish();
        mFrameAvailable = false;
    }
}