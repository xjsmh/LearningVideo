package com.example.learningvideo.Decoder;

import static com.example.learningvideo.Core.MSG_NEXT_ACTION;
import static com.example.learningvideo.Core.MSG_DONE;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.AssetFileDescriptor;
import android.opengl.EGL14;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import com.example.learningvideo.Core;
import com.example.learningvideo.GLES.EGLCore;
import com.example.learningvideo.IDecoderService;
import com.example.learningvideo.SharedTexture;

public abstract class DecoderClientBase extends DecoderBase{
    private Handler mHandler;
    private final AssetFileDescriptor mAfd;
    ServiceConnection mServiceConnection;
    volatile IDecoderService mService;
    private int mTexture;
    private SharedTexture mSharedTexture;
    private boolean mIsStarted;

    public abstract int getHwBufferFormat();

    public DecoderClientBase(AssetFileDescriptor afd, Handler handler) {
        super(afd, handler);
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
        Context ctx = Core.getInstance().getContextRef().get();
        if(ctx == null) throw new RuntimeException();
        Intent intent = new Intent(ctx.getApplicationContext(), DecoderService.class);
        intent.putExtra("format", getHwBufferFormat());
        ctx.getApplicationContext().bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE|Context.BIND_IMPORTANT);
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
        android.opengl.EGLConfig[] configs = new android.opengl.EGLConfig[1];
        int[] num_configs = new int[1];
        if(!EGL14.eglGetConfigs(EGL14.eglGetCurrentDisplay(), configs, 0, 1, num_configs, 0)) {
            throw new RuntimeException();
        }
        mSharedTexture = new SharedTexture(width, height, getHwBufferFormat());
        mSharedTexture.bindTexture(mTexture, getTextureType());
    }

    public abstract int getTextureType();

    @Override
    public void start(Object obj) {
        try {
            if (obj instanceof Integer) {
                mTexture = (int)obj;
            }
            if (!mIsStarted) {
                setupEGL();
                mService.start(mSharedTexture.getHardwareBuffer());
                mIsStarted = true;
            } else {
                mSharedTexture.bindTexture(mTexture, getTextureType());
            }
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
                Message.obtain(mHandler, MSG_NEXT_ACTION, mService.getWidth(), mService.getHeight()).sendToTarget();
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
        mSharedTexture = null;
        try {
            mService.release();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        Context ctx = Core.getInstance().getContextRef().get();
        if(ctx == null) throw new RuntimeException();
        ctx.getApplicationContext().unbindService(mServiceConnection);
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

}
