package com.example.learningvideo.Decoder;

import static com.example.learningvideo.Core.MSG_NEXT_ACTION;
import static com.example.learningvideo.Core.MSG_DONE;

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

    private int mWantedOutputFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
    private boolean mIsNeedSaveYuvByteArray = false;
    private byte[] mYuvByteArray;
    private boolean mIsStarted;

    public void setNeedSaveYuvByteArray(boolean needSaveYuvByteArray) {
        mIsNeedSaveYuvByteArray = needSaveYuvByteArray;
    }

    @Override
    public boolean isEOS() {
        return mIsEOS;
    }

    public byte[] getYuvByteArray() {
        return mYuvByteArray;
    }

    public void setWantedOutputFormat(int wantedOutputFormat) {
        mWantedOutputFormat = wantedOutputFormat;
    }

    public Decoder1(AssetFileDescriptor afd, Handler workHandler) {
        super(afd, workHandler);
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
    public void start(Object obj) {
        if (obj instanceof Surface) mSurface = (Surface)obj;
        if (mIsStarted) {
            if (obj != null) {
                mDecoder.setOutputSurface(mSurface);
            }
        } else {
            if (mIsNeedSaveYuvByteArray) {
                mYuvByteArray = new byte[mWidth * mHeight * 3 / 2];
            }
            mSrcFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, mWantedOutputFormat);
            mDecoder.configure(mSrcFormat, mSurface, null, 0);
            mDecoder.start();
            mIsStarted = true;
        }
    }

    @Override
    public void decode() {
        if (mIsEOS) return;
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
                    if (mHandler != null) {
                        Message.obtain(mHandler, MSG_DONE).sendToTarget();
                        //mDeMuxer.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                        //mDecoder.flush();
                        //Message.obtain(mHandler, MSG_NEXT_ACTION, mWidth, mHeight, mYUVData).sendToTarget();
                    }
                    mIsEOS = true;
                    break;
                }
                Log.e(TAG, "decode " + mDecodeFrame++);
                if (mSurface == null) {
                    mYUVData = mDecoder.getOutputBuffer(idx);
                    if (mYUVData != null) {
                        if (mHandler != null)
                            Message.obtain(mHandler, MSG_NEXT_ACTION, mWidth, mHeight, mYUVData).sendToTarget();
                        if (mIsNeedSaveYuvByteArray) {
                            mYUVData.get(mYuvByteArray, 0, mYuvByteArray.length);
                        }
                        gotOutput = true;
                    }
                    mDecoder.releaseOutputBuffer(idx, false);
                } else {
                    gotOutput = true;
                    mDecoder.releaseOutputBuffer(idx, true);
                    if (mHandler != null)
                        Message.obtain(mHandler, MSG_NEXT_ACTION, mWidth, mHeight).sendToTarget();
                }
            } else if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat fmt = mDecoder.getOutputFormat();
                mHeight = fmt.getInteger(MediaFormat.KEY_HEIGHT);
                mWidth = fmt.getInteger(MediaFormat.KEY_WIDTH);
                int color_format = fmt.getInteger(MediaFormat.KEY_COLOR_FORMAT);
                assert color_format == mWantedOutputFormat;
            }
        }
    }

    @Override
    public void release() {
        mDeMuxer.release();
        mDecoder.stop();
        mDecoder.release();
        mIsStarted = false;
    }

}
