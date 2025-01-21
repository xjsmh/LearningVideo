package com.example.learningvideo;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.view.Surface;
import android.view.View;

import androidx.annotation.NonNull;

import com.example.learningvideo.Renderer.IRenderer;
import com.example.learningvideo.Renderer.Renderer1;
import com.example.learningvideo.Renderer.Renderer2;
import com.example.learningvideo.Renderer.Renderer3;
import com.example.learningvideo.Renderer.Renderer4;

public class Core {
    public static final int MSG_SETUP_EGL = 1;
    public static final int MSG_SURFACE_CREATED = 2;
    public static final int MSG_DECODE = 3;
    public static final int MSG_RENDER = 4;
    public static final int MSG_ENCODE = 5;
    public static final int MSG_ACTION_COMPLETED = 6;
    public static final int MSG_DONE = 7;
    private final Context mContext;
    Decoder mDecoder;
    Encoder mEncoder;
    IRenderer mRenderer;
    HandlerThread mWorkThread;
    Handler mWorkHandler;
    int[] mActionFlow = {MSG_DECODE, MSG_RENDER, MSG_ENCODE};
    int mCurrentAction = 0;

    public class WorkHandler extends Handler {
        public WorkHandler(@NonNull Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch(msg.what) {
                case MSG_SETUP_EGL:
                    mRenderer.setup(mDecoder.getWidth(), mDecoder.getHeight());
                    break;
                case MSG_SURFACE_CREATED:
                    mDecoder.setSurface((Surface) msg.obj);
                    mDecoder.start();
                    mEncoder.start(mRenderer.getEGLResource());
                case MSG_DECODE:
                    mDecoder.decode();
                    break;
                case MSG_RENDER:
                    if(!mRenderer.isFrameAvailable()) {
                        Message.obtain(this, MSG_RENDER, msg.arg1, msg.arg2, msg.obj).sendToTarget();
                    } else {
                        mRenderer.render(msg);
                    }
                    break;
                case MSG_ENCODE:
                    mEncoder.encode();
                    break;
                case MSG_ACTION_COMPLETED:
                    Message.obtain(this, getNextAction(), msg.arg1, msg.arg2, msg.obj).sendToTarget();
                    break;
                case MSG_DONE:
                    mRenderer.release();
                    mDecoder.release();
                    mEncoder.release();
                    break;

            }
        }
    }

    private int getNextAction() {
        mCurrentAction = (mCurrentAction + 1) % mActionFlow.length;
        return mActionFlow[mCurrentAction];
    }

    public Core(AssetFileDescriptor afd, Context context) {
        mWorkThread = new HandlerThread("work-thread");
        mWorkThread.start();
        mWorkHandler = new WorkHandler(mWorkThread.getLooper());
        mDecoder = new Decoder(afd, mWorkHandler);
        mEncoder = new Encoder(mWorkHandler);
        mRenderer = new Renderer2();
        mContext = context;
    }
    public void start() {
        mRenderer.start(mContext, mWorkHandler, mDecoder.getWidth(), mDecoder.getHeight());
    }

    public View getSurfaceView(){
        return mRenderer.getView();
    }
}
