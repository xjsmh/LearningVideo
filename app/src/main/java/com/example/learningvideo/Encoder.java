package com.example.learningvideo;

import static com.example.learningvideo.Core.MSG_NEXT_ACTION;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import com.example.learningvideo.GLES.EGLCore;

import java.io.IOException;
import java.nio.ByteBuffer;

public class Encoder {
    private static final String TAG = "Encoder";
    private int mEncodeFrame = 0;
    Handler mHandler;
    private MediaCodec mEncoder;
    private MediaMuxer mMuxer;
    private Surface mInputSurface;

    private int mWidth = 640;
    private int mHeight = 360;
    private int mFrameRate = 240;
    private int mBitrate = 36000000;
    private int mIFrameInterval = 1;
    private String mPath = "sdcard/Download/out1.mp4";
    private int mTrackID;
    private int mFrameCount = 0;
    EGLCore mEGLCore;
    EGLContext mSharedEGLContext = EGL14.EGL_NO_CONTEXT;
    IDrawable mBufferProvider;
    private long mGLThreadId;
    private boolean mIsStarted = false;


    public Encoder(Handler handler) {
        mHandler = handler;
    }

    public void start(EGLCore eglCore, IDrawable bufferProvider) {
        if (mSharedEGLContext != eglCore.getEGLContext()) {
            if (mEncoder == null)
                setupEncoder();
            setupEGL(eglCore);
            mBufferProvider = bufferProvider;
        } else {
            mEGLCore.setWindowSurface(mInputSurface);
        }

    }

    private void setupEncoder() {
        MediaFormat fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, mWidth, mHeight);
        fmt.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
        fmt.setInteger(MediaFormat.KEY_BIT_RATE, mBitrate);
        fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, mIFrameInterval);
        fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        String codecName = mcl.findEncoderForFormat(fmt);
        if (codecName == null) throw new RuntimeException();
        try {
            mEncoder = MediaCodec.createByCodecName(codecName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        mEncoder.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mInputSurface = mEncoder.createInputSurface();
        mEncoder.start();
        try {
            mMuxer = new MediaMuxer(mPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void setupEGL(EGLCore eglCore) {
        mGLThreadId = Thread.currentThread().getId();
        mEGLCore = mGLThreadId != eglCore.getGLThreadId() ? new EGLCore(eglCore.getEGLContext()) : new EGLCore(eglCore);
        mEGLCore.setWindowSurface(mInputSurface);
        mSharedEGLContext = eglCore.getEGLContext();
    }

    public void encode() {
        mEGLCore.makeCurrent();
        GLES20.glViewport(0, 0, mWidth, mHeight);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        mBufferProvider.draw();
        mEGLCore.presentationTime((mFrameCount++) * 1000000000L / mFrameRate);
        mEGLCore.swapBuffer();

        GLES20.glFinish();
        Log.e(TAG, "encode " + mEncodeFrame++);

        boolean gotOutput = false;
        do {
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int index = mEncoder.dequeueOutputBuffer(info, 1000);
            if (index > 0) {
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) > 0) {
                    break;
                }
                ByteBuffer sample = mEncoder.getOutputBuffer(index);
                if (sample != null) {
                    mMuxer.writeSampleData(mTrackID, sample, info);
                }
                gotOutput = true;
                mEncoder.releaseOutputBuffer(index, false);
            } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat fmt = mEncoder.getOutputFormat();
                mWidth = fmt.getInteger(MediaFormat.KEY_WIDTH);
                mHeight = fmt.getInteger(MediaFormat.KEY_HEIGHT);
                mTrackID = mMuxer.addTrack(fmt);
                mMuxer.start();
                gotOutput = true;
            } else {
                gotOutput = false;
            }
        }while(gotOutput);
        mHandler.sendEmptyMessage(MSG_NEXT_ACTION);
    }

    public void release() {
        boolean gotEOS = false;
        mEncoder.signalEndOfInputStream();
        while (!gotEOS) {
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int index = mEncoder.dequeueOutputBuffer(info, 1000);
            if (index > 0) {
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) > 0) {
                    gotEOS = true;
                }
                ByteBuffer sample = mEncoder.getOutputBuffer(index);
                if (sample != null) {
                    mMuxer.writeSampleData(mTrackID, sample, info);
                }
                mEncoder.releaseOutputBuffer(index, false);
            }
        }
        mEncoder.stop();
        mEncoder.release();
        mMuxer.stop();
        mMuxer.release();
        mEGLCore.release();
        mInputSurface.release();
        mInputSurface = null;
    }

    public void pause() {
        mEGLCore.makeCurrent();
        mEGLCore.destroySurface();
    }
}
