package com.example.learningvideo.Filter;

import android.graphics.Bitmap;
import android.opengl.GLES20;

import com.example.learningvideo.GLES.EGLCore;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class FrameCapture extends FilterBase{
    private static final int mQuality = 70;
    private static final String mFilePathPrefix = "sdcard/Download/capture_";
    private int mFrameNum = 0;


    public FrameCapture(FilterBase lastFilter, int width, int height) {
        super(lastFilter, width, height);
        mEGLCore = lastFilter.getEGLCore();
        mOutTextureType = lastFilter.getOutTextureType();
        mLastFilter = lastFilter;
    }

    public FrameCapture(EGLCore eglCore, int inTarget, int width, int height) {
        super(eglCore, inTarget, width, height);
        mEGLCore = eglCore;
        mOutTextureType = inTarget;
    }

    @Override
    public void addInputTexture(int tex, int colorFormat, ITextureUploader uploader) {
        if (mInputTexture.size() > 1 || colorFormat != GLES20.GL_RGBA) {
            throw new RuntimeException();
        }
        mInputTexture.add(tex);
        mOutputTexture = tex;
        if(uploader != null)
            mUploaders.add(uploader);
    }

    @Override
    public void process() {
        if((mFrameNum++) % 50 != 0) return;

        // 暂时只支持用FBO绘制的情况
        if (mLastFilter == null || mLastFilter.getFBO() < 0) return;
        mEGLCore.makeCurrent();
        int[] size = mEGLCore.getSurfaceSize();

        ByteBuffer pixels = ByteBuffer.allocateDirect(size[0] * size[1] * 4).order(ByteOrder.nativeOrder());
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mLastFilter.getFBO());
        GLES20.glReadPixels(0,0, size[0], size[1], GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_NONE);

        BufferedOutputStream bos = null;
        try {
            bos = new BufferedOutputStream(new FileOutputStream(mFilePathPrefix + mFrameNum + ".jpg"));
            Bitmap bmp = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
            bmp.copyPixelsFromBuffer(pixels);
            bmp.compress(Bitmap.CompressFormat.JPEG, mQuality, bos);
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
        }

    }

    @Override
    public void release() {
    }
}
