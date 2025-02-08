package com.example.learningvideo;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.view.View;

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

public class Core {
    public static final int MSG_SETUP_EGL = 1;
    public static final int MSG_CREATED = 2;
    public static final int MSG_START = 3;
    public static final int MSG_SURFACE_CREATED = 4;
    public static final int MSG_DECODE = 5;
    public static final int MSG_RENDER = 6;
    public static final int MSG_ENCODE = 7;
    public static final int MSG_ACTION_COMPLETED = 8;
    public static final int MSG_DONE = 9;
    private final Context mContext;
    private final AssetFileDescriptor mAfd;
    DecoderBase mDecoder;
    Encoder mEncoder;
    RendererBase mRenderer;
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
                case MSG_CREATED:

                    // so ugly
                    if (mRenderer instanceof Renderer5) {
                        mRenderer.setFrameTextureType(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
                        int frameTextureType = mRenderer.getFrameTextureType();
                        if (frameTextureType == GLES20.GL_TEXTURE_2D) {
                            mDecoder = new Decoder2Client(mAfd, mWorkHandler, mContext);
                        } else if (frameTextureType == GLES11Ext.GL_TEXTURE_EXTERNAL_OES){
                            mDecoder = new Decoder3Client(mAfd, mWorkHandler, mContext);
                        } else {
                            throw new RuntimeException();
                        }
                    } else {
                        mDecoder = new Decoder1(mAfd, mWorkHandler, mContext);
                    }

                    mEncoder = new Encoder(mWorkHandler);
                    break;
                case MSG_START:
                    mRenderer.start(mDecoder.getWidth(), mDecoder.getHeight());
                    break;
                case MSG_SETUP_EGL:
                    mRenderer.setup(mDecoder.getWidth(), mDecoder.getHeight());
                    break;
                case MSG_SURFACE_CREATED:
                    mDecoder.setObject(msg.obj);
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
        mContext = context;
        mAfd = afd;
        mRenderer = new Renderer5(mContext, mWorkHandler);
        Message.obtain(mWorkHandler, MSG_CREATED).sendToTarget();
    }
    public void start() {
        Message.obtain(mWorkHandler, MSG_START).sendToTarget();
    }

    public View getSurfaceView(){
        return mRenderer.getView();
    }
}
