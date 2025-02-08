package com.example.learningvideo;

import static com.example.learningvideo.Core.MSG_ACTION_COMPLETED;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

public class Encoder {
    private static final String TAG = "Encoder";
    private int mEncodeFrame = 0;
    Handler mHandler;
    private MediaCodec mEncoder;
    private MediaMuxer mMuxer;
    private Surface mInputSurface;
    private EGLSurface mEGLSurface;
    private int mWidth = 640;
    private int mHeight = 360;
    private int mFrameRate = 30;
    private int mBitrate = 36000000;
    private int mIFrameInterval = 1;
    private String mPath = "sdcard/Download/out.mp4";
    private EGLResources mEGLResources;
    private int mTrackID;
    private int mFrameCount = 0;


    public Encoder(Handler handler) {
        mHandler = handler;
    }

    public void start(EGLResources resources) {
        setupEncoder();
        setupEGL(resources);
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

    private void setupEGL(EGLResources resource) {
        EGLConfig config = resource.getEGLConfig();
        EGLDisplay display = resource.getEGLDisplay();
        EGLContext context = resource.getEGLContext();
        if(resource.isNeedShared()) {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (display == EGL14.EGL_NO_DISPLAY) {
                throw new RuntimeException();
            }
            int[] version = new int[2];
            if(!EGL14.eglInitialize(display, version, 0, version, 1)) {
                throw new RuntimeException();
            }
            int[] attribs = {
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE,8,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[1];
            int[] num_configs = new int[1];
            if(!EGL14.eglChooseConfig(display, attribs, 0, configs,0,1,num_configs,0)) {
                throw new RuntimeException();
            }
            config = configs[0];
            int[] attribs_ctx = {
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                    EGL14.EGL_NONE
            };
            context = EGL14.eglCreateContext(display, config, resource.getEGLContext(), attribs_ctx, 0);
            if (context == EGL14.EGL_NO_CONTEXT) {
                throw new RuntimeException();
            }
        }
        mEGLSurface = EGL14.eglCreateWindowSurface(display, config, mInputSurface, new int[]{EGL14.EGL_NONE}, 0);
        if (mEGLSurface == EGL14.EGL_NO_SURFACE) {
            throw new RuntimeException();
        }
        if(!EGL14.eglMakeCurrent(display, mEGLSurface, mEGLSurface, context)) {
            throw new RuntimeException();
        }
        GLES20.glClearColor(0, 0, 0, 1);
        mEGLResources = resource;
        mEGLResources.setEGLConfig(config);
        mEGLResources.setEGLDisplay(display);
        mEGLResources.setEGLContext(context);
    }

    public void encode() {
        EGL14.eglMakeCurrent(mEGLResources.getEGLDisplay(), mEGLSurface, mEGLSurface, mEGLResources.getEGLContext());
        GLES20.glViewport(0, 0, mWidth, mHeight);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(mEGLResources.getProgram());
        EGLResources.VertexAttrib posAttrib = mEGLResources.getPosAttrib();
        GLES20.glVertexAttribPointer(posAttrib.getLoc(), posAttrib.getSize(), posAttrib.getType(), false, posAttrib.getStride(), posAttrib.getBuffer());
        GLES20.glEnableVertexAttribArray(posAttrib.getLoc());
        EGLResources.VertexAttrib texPosAttrib = mEGLResources.getTexPosAttrib();
        GLES20.glVertexAttribPointer(texPosAttrib.getLoc(), texPosAttrib.getSize(), texPosAttrib.getType(), false, texPosAttrib.getStride(), texPosAttrib.getBuffer());
        GLES20.glEnableVertexAttribArray(texPosAttrib.getLoc());
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(mEGLResources.getTextureType(), mEGLResources.getTexture());
        EGLResources.ElementAttrib drawOrdersAttrib = mEGLResources.getDrawOrdersAttrib();
        GLES20.glDrawElements(GLES20.GL_TRIANGLE_STRIP, drawOrdersAttrib.getCount(), drawOrdersAttrib.getType(), drawOrdersAttrib.getBuffer());
        EGLExt.eglPresentationTimeANDROID(mEGLResources.getEGLDisplay(), mEGLSurface, (mFrameCount++) * 1000000000L / mFrameRate);
        EGL14.eglSwapBuffers(mEGLResources.getEGLDisplay(), mEGLSurface);
        GLES20.glBindTexture(mEGLResources.getTextureType(), GLES20.GL_NONE);
        GLES20.glDisableVertexAttribArray(posAttrib.getLoc());
        GLES20.glDisableVertexAttribArray(texPosAttrib.getLoc());
        //GLES20.glFinish();
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
        mHandler.sendEmptyMessage(MSG_ACTION_COMPLETED);
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
    }
}
