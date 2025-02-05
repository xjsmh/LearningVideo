package com.example.learningvideo.Decoder;

import static com.example.learningvideo.Core.MSG_ACTION_COMPLETED;
import static com.example.learningvideo.Core.MSG_DONE;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

public class Decoder1 extends DecoderBase {
    private static final String TAG = "Decoder";
    Handler mHandler;
    MediaCodec mDecoder;
    MediaExtractor mDeMuxer;
    ByteBuffer mSampleData;
    ByteBuffer mYUVData;
    private int mHeight;
    private int mWidth;
    private static int mDecodeFrame = 0;
    private MediaFormat mSrcFormat;
    private Surface mSurface = null;
    private boolean mIsEOS;

    @Override
    public boolean isEOS() {
        return mIsEOS;
    }

    @Override
    public void setObject(Object obj) {
        if (obj instanceof Surface)
            this.mSurface = (Surface)obj;
    }

    public Decoder1(AssetFileDescriptor afd, Handler workHandler, Context ctx) {
        mHandler = workHandler;
        mDeMuxer = new MediaExtractor();
        try {
            mDeMuxer.setDataSource(afd);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String mime;
        mSrcFormat = null;
        for (int i = 0; i < mDeMuxer.getTrackCount(); i++) {
            mSrcFormat = mDeMuxer.getTrackFormat(i);
            mime = mSrcFormat.getString(MediaFormat.KEY_MIME, "");
            if (mime.startsWith("video/")) {
                mHeight = mSrcFormat.getInteger(MediaFormat.KEY_HEIGHT);
                mWidth = mSrcFormat.getInteger(MediaFormat.KEY_WIDTH);
                mDeMuxer.selectTrack(i);
                try {
                    mDecoder = MediaCodec.createDecoderByType(mime);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                break;
            }
        }

    }

    @Override
    public int getHeight() {
        return mHeight;
    }

    @Override
    public int getWidth() {
        return mWidth;
    }

    @Override
    public void start() {
        mSrcFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
        mDecoder.configure(mSrcFormat, mSurface, null, 0);
        mDecoder.start();
    }

    @Override
    public void decode() {
        boolean gotOutput = false;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while(!gotOutput) {
            int idx = mDecoder.dequeueInputBuffer(1000);
            if (idx > 0) {
                mSampleData = mDecoder.getInputBuffer(idx);
                if (mSampleData != null) {
                    int readSize = mDeMuxer.readSampleData(mSampleData, 0);
                    if (readSize > 0) {
                        mDecoder.queueInputBuffer(idx, 0, readSize, mDeMuxer.getSampleTime(), 0);
                        mDeMuxer.advance();
                    } else {
                        mDecoder.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    }
                }
            }
            idx = mDecoder.dequeueOutputBuffer(info, 1000);
            if (idx > 0) {
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) > 0) {
                    if (mHandler != null)
                        Message.obtain(mHandler, MSG_DONE).sendToTarget();
                    mIsEOS = true;
                    break;
                }
                Log.e(TAG, "decode " + mDecodeFrame++);
                if (mSurface == null) {
                    mYUVData = mDecoder.getOutputBuffer(idx);
                    if (mYUVData != null) {
                        if (mHandler != null)
                            Message.obtain(mHandler,MSG_ACTION_COMPLETED, mWidth, mHeight, mYUVData).sendToTarget();
                        gotOutput = true;
                    }
                    mDecoder.releaseOutputBuffer(idx, false);
                } else {
                    gotOutput = true;
                    mDecoder.releaseOutputBuffer(idx, true);
                    if (mHandler != null)
                        Message.obtain(mHandler,MSG_ACTION_COMPLETED, mWidth, mHeight).sendToTarget();
                }
            } else if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat fmt = mDecoder.getOutputFormat();
                mHeight = fmt.getInteger(MediaFormat.KEY_HEIGHT);
                mWidth = fmt.getInteger(MediaFormat.KEY_WIDTH);
                int color_format = fmt.getInteger(MediaFormat.KEY_COLOR_FORMAT);
                assert color_format == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
            }
        }
    }

    @Override
    public void release() {
        mDeMuxer.release();
        mDecoder.stop();
        mDecoder.release();
    }
}
