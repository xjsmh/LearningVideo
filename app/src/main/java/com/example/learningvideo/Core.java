package com.example.learningvideo;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.example.learningvideo.Decoder.Decoder2Client;
import com.example.learningvideo.Decoder.Decoder3Client;
import com.example.learningvideo.Decoder.DecoderBase;
import com.example.learningvideo.Decoder.Decoder1;

import com.example.learningvideo.Renderer.Renderer1;
import com.example.learningvideo.Renderer.Renderer2;
import com.example.learningvideo.Renderer.Renderer3;
import com.example.learningvideo.Renderer.Renderer4;
import com.example.learningvideo.Renderer.RendererBase;
import com.example.learningvideo.Renderer.Renderer5;

import java.lang.ref.WeakReference;

public class Core {
    private final static String TAG = "Core";
    public static final int MSG_INIT = 2;
    public static final int MSG_SURFACE_CREATED = 4;
    public static final int MSG_DECODE = 5;
    public static final int MSG_RENDER = 6;
    public static final int MSG_ENCODE = 7;
    public static final int MSG_NEXT_ACTION = 8;
    public static final int MSG_DONE = 9;
    public static final int MSG_START = 10;
    WeakReference<Context> mContextRef;
    private AssetFileDescriptor mAfd;
    static DecoderBase mDecoder;
    static Encoder mEncoder;
    RendererBase mRenderer;
    HandlerThread mWorkThread;
    Handler mWorkHandler;
    int[] mActionFlow = {MSG_DECODE, MSG_RENDER, MSG_ENCODE};
    int mCurrentAction = -1;
    private volatile boolean mLooperRunning;
    Message mSavedMsg = new Message();
    private static final Core sInstance = new Core();
    private boolean isInited = false;

    private Core(){}

    public static Core getInstance() {
        return sInstance;
    }

    public void pause() {
        mLooperRunning = false;
    }

    public WeakReference<Context> getContextRef() {
        return mContextRef;
    }

    public void stop() {
        Message.obtain(mWorkHandler, MSG_DONE).sendToTarget();
    }

    public class WorkHandler extends Handler {
        public WorkHandler(@NonNull Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            //Log.d(TAG, "receive message: " + msg.what);
            switch(msg.what) {
                case MSG_INIT:
                    if (mRenderer == null)
                        mRenderer = new Renderer2(mWorkHandler);
                    mRenderer.init(mContextRef.get());
                    // so ugly
                    if (mDecoder == null) {
                        if (mRenderer instanceof Renderer5) {
                            mRenderer.setFrameTextureType(GLES20.GL_TEXTURE_2D);
                            int frameTextureType = mRenderer.getFrameTextureType();
                            if (frameTextureType == GLES20.GL_TEXTURE_2D) {
                                mDecoder = new Decoder2Client(mAfd, mWorkHandler);
                            } else if (frameTextureType == GLES11Ext.GL_TEXTURE_EXTERNAL_OES) {
                                mDecoder = new Decoder3Client(mAfd, mWorkHandler);
                            } else {
                                throw new RuntimeException();
                            }
                        } else {
                            mDecoder = new Decoder1(mAfd, mWorkHandler);
                        }
                    }
                    if (mEncoder == null) {
                        mEncoder = new Encoder(mWorkHandler);
                    }
                    break;
                case MSG_SURFACE_CREATED:
                    mLooperRunning = true;
                    onSetViewSize(mDecoder.getWidth(), mDecoder.getHeight());
                    mRenderer.setup(mDecoder.getWidth(), mDecoder.getHeight());
                    break;
                case MSG_START:
                    if (!mRenderer.readyToDraw()) {
                        Message.obtain(mWorkHandler, MSG_START).sendToTarget();
                        break;
                    }
                    mDecoder.start(mRenderer.getInputSurface());
                    mEncoder.start(mRenderer.getEGLCore(), mRenderer);
                    Message.obtain(this, MSG_NEXT_ACTION, mSavedMsg.arg1, mSavedMsg.arg2, mSavedMsg.obj).sendToTarget();
                    break;
                case MSG_DECODE:
                    mDecoder.decode();
                    break;
                case MSG_RENDER:
                    if (!mLooperRunning) return;
                    if (!mRenderer.isFrameAvailable()) {
                        Message.obtain(this, MSG_RENDER, msg.arg1, msg.arg2, msg.obj).sendToTarget();
                    } else {
                        mRenderer.render(msg);
                    }
                    break;
                case MSG_ENCODE:
                    mEncoder.encode();
                    break;
                case MSG_NEXT_ACTION:
                    if (mLooperRunning) {
                        Message.obtain(this, getNextAction(), msg.arg1, msg.arg2, msg.obj).sendToTarget();
                        break;
                    } else {
                        mSavedMsg.arg1 = msg.arg1;
                        mSavedMsg.arg2 = msg.arg2;
                        mSavedMsg.obj = msg.obj;
                        mEncoder.pause();
                    }
                    break;
                case MSG_DONE:
                    mRenderer.release();
                    mDecoder.release();
                    mEncoder.release();
                    mLooperRunning = false;
                    break;

            }
        }
    }

    private void onSetViewSize(int width, int height) {
        Context context = mContextRef.get();
        if (context == null) return;
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        lp.width = dm.widthPixels;
        lp.height = dm.widthPixels * height / width;
        lp.gravity = Gravity.CENTER;
        ((Activity)context).runOnUiThread(()->{ ((Activity)context).findViewById(mRenderer.getViewId()).setLayoutParams(lp); });
    }

    private int getNextAction() {
        mCurrentAction = (mCurrentAction + 1) % mActionFlow.length;
        return mActionFlow[mCurrentAction];
    }


    public void init(AssetFileDescriptor afd, Context context) {
        if (!isInited) {
            mWorkThread = new HandlerThread("work-thread");
            mWorkThread.start();
            mWorkHandler = new WorkHandler(mWorkThread.getLooper());
            mAfd = afd;
            isInited = true;
        }
        mContextRef = new WeakReference<>(context);
        Message.obtain(mWorkHandler, MSG_INIT).sendToTarget();
    }

    public void start() {
        mLooperRunning = true;
        Message.obtain(mWorkHandler, MSG_START).sendToTarget();
    }

}
