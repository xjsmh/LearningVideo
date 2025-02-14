package com.example.learningvideo.Decoder;

import static com.example.learningvideo.Core.MSG_ACTION_COMPLETED;
import static com.example.learningvideo.Core.MSG_DONE;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.AssetFileDescriptor;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;

import com.example.learningvideo.IDecoderService;
import com.example.learningvideo.SharedTexture;

public abstract class DecoderClientBase extends DecoderBase{
    private final Handler mHandler;
    private final AssetFileDescriptor mAfd;
    private final Context mContext;
    ServiceConnection mServiceConnection;
    volatile IDecoderService mService;
    private EGLContext mEGLContext;
    private EGLDisplay mEGLDisplay;
    private EGLSurface mEGLSurface;
    private int mTexture;
    private SharedTexture mSharedTexture;

    public abstract int getHwBufferFormat();

    public DecoderClientBase(AssetFileDescriptor afd, Handler handler, Context ctx) {
        if(!SharedTexture.isAvailable()) {
            throw new RuntimeException();
        }
        mAfd = afd;
        mHandler = handler;
        mServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mService = IDecoderService.Stub.asInterface(service);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {

            }
        };
        Intent intent = new Intent(ctx, DecoderService.class);
        intent.putExtra("format", getHwBufferFormat());
        ctx.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE|Context.BIND_IMPORTANT);
        mContext = ctx;
        while(mService == null);
        try {
            mService.init(mAfd);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }
    private void setupEGL() {
        int width = getWidth();
        int height = getHeight();
        mEGLContext = EGL14.eglGetCurrentContext();
        if (mEGLContext == EGL14.EGL_NO_CONTEXT) {
            throw new RuntimeException();
        }
        mEGLDisplay = EGL14.eglGetCurrentDisplay();
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException();
        }
        mEGLSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
        if (mEGLSurface == EGL14.EGL_NO_SURFACE) {
            throw new RuntimeException();
        }
        if(!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
            throw new RuntimeException();
        }
        mSharedTexture = new SharedTexture(width, height, getHwBufferFormat());
        mSharedTexture.bindTexture(mTexture, getTextureType());
    }

    public abstract int getTextureType();

    @Override
    public void start() {
        try {
            setupEGL();
            mService.start(mSharedTexture.getHardwareBuffer());
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void decode() {
        try {
            boolean eos = mService.decode(mSharedTexture.createFenceFd());
            if (!eos) {
                waitFence();
                Message.obtain(mHandler,MSG_ACTION_COMPLETED, mService.getWidth(), mService.getHeight()).sendToTarget();
            } else {
                Message.obtain(mHandler, MSG_DONE).sendToTarget();
            }
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    IDecoderService getService() {
        return mService;
    }

    SharedTexture getSharedTexture() {
        return mSharedTexture;
    }

    public abstract void waitFence();

    @Override
    public int getHeight(){
        try {
            return mService.getHeight();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getWidth() {
        try {
            return mService.getWidth();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void release() {
        try {
            mService.release();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        mContext.unbindService(mServiceConnection);
    }

    @Override
    public boolean isEOS() {
        boolean eos = false;
        try {
            eos = mService.decode(mSharedTexture.createFenceFd());
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        return eos;
    }

    public void setObject(Object obj) {
        if (obj instanceof Integer) {
            mTexture = (int)obj;
        }
    }
}
