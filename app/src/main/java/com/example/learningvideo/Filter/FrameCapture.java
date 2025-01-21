package com.example.learningvideo.Filter;

import android.graphics.Bitmap;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;

import com.example.learningvideo.EGLResources;

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


    public FrameCapture(FilterBase lastFilter, EGLContext context, EGLDisplay display, EGLConfig config, int inTexType, int width, int height) {
        super(lastFilter, context, display, config, inTexType, width, height);
        mOutTextureType = inTexType;
        mLastFilter = lastFilter;
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
    public void process(EGLContext context, EGLDisplay display, EGLSurface surface) {
        if((mFrameNum++) % 50 != 0) return;

        EGLResources lastFilterResources = mLastFilter.getEGLResources();
        // 暂时只支持用FBO绘制的情况
        if (lastFilterResources.getFBO() < 0) return;

        EGL14.eglMakeCurrent(lastFilterResources.getEGLDisplay(), lastFilterResources.getEGLDrawSurface(),
                lastFilterResources.getEGLDrawSurface(), lastFilterResources.getEGLContext());
        int[] size = new int[2];
        EGL14.eglQuerySurface(display, lastFilterResources.getEGLDrawSurface(), EGL14.EGL_WIDTH, size, 0);
        EGL14.eglQuerySurface(display, lastFilterResources.getEGLDrawSurface(), EGL14.EGL_HEIGHT, size, 1);

        ByteBuffer pixels = ByteBuffer.allocateDirect(size[0] * size[1] * 4).order(ByteOrder.nativeOrder());
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, lastFilterResources.getFBO());
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

        EGL14.eglMakeCurrent(display, surface, surface, context);
    }
}
