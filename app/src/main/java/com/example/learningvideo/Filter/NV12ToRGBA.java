package com.example.learningvideo.Filter;

import android.opengl.GLES20;

import com.example.learningvideo.GLES.EGLCore;
import com.example.learningvideo.GLES.Utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;

public class NV12ToRGBA extends FilterBase {
    private static final FloatBuffer sVertices;
    private static final ByteBuffer sDrawOrders;
    private final int mFBO;
    private final int mProgram;
    private final ArrayList<Integer> mTexSamplerLocList = new ArrayList<>();
    private final int mPosLoc;
    private final int mTexPosLoc;

    @Override
    public int getFBO() {
        return mFBO;
    }

    static {
        float[] vertices = {
                -1, 1, 0, 1,
                -1,-1, 0, 0,
                1, -1, 1, 0,
                1, 1, 1, 1
        };
        sVertices = ByteBuffer.allocateDirect(vertices.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        sVertices.put(vertices).position(0);
        byte[] orders = {
                0, 1, 2,
                0, 2, 3
        };
        sDrawOrders = ByteBuffer.allocateDirect(orders.length).order(ByteOrder.nativeOrder());
        sDrawOrders.put(orders).position(0);
    }

    public NV12ToRGBA(FilterBase lastFilter, int width, int height) {
        this(lastFilter.getEGLCore(), lastFilter.getOutTextureType(), width, height);
        mLastFilter = lastFilter;
    }

    public NV12ToRGBA(EGLCore eglCore, int inTexType, int width, int height) {
        super(eglCore, inTexType, width, height);
        mEGLCore = new EGLCore(eglCore);
        mEGLCore.setPbufferSurface(width, height);
        mOutTextureType = GLES20.GL_TEXTURE_2D;
        mOutputTexture = Utils.genTexture(mOutTextureType, null, width, height, GLES20.GL_RGBA);
        mFBO = Utils.genFrameBuffer(GLES20.GL_COLOR_ATTACHMENT0, mOutTextureType, mOutputTexture);

        String vertexShader =
                "attribute vec2 pos;" +
                "attribute vec2 texPos;" +
                "varying vec2 outTexPos;" +
                "void main() {" +
                "    gl_Position = vec4(pos, 0, 1);" +
                "    outTexPos = texPos;" +
                "}";

        String samplerType = (inTexType == GLES20.GL_TEXTURE_2D) ? "sampler2D" : "samplerExternalOES";
        String fragmentShader =
                "#extension GL_OES_EGL_image_external : require \n" +
                "precision mediump float;" +
                "varying vec2 outTexPos;" +
                "uniform " + samplerType + " Y_Sampler;" +
                "uniform " + samplerType + " UV_Sampler;" +
                "void main() {" +
                "    float r = texture2D(Y_Sampler, outTexPos).r + 1.402 * (texture2D(UV_Sampler, outTexPos).a - 0.5);" +
                "    float g = texture2D(Y_Sampler, outTexPos).r - 0.3441 * (texture2D(UV_Sampler, outTexPos).r - 0.5) - 0.7141 * (texture2D(UV_Sampler, outTexPos).a - 0.5);" +
                "    float b = texture2D(Y_Sampler, outTexPos).r + 1.772 * (texture2D(UV_Sampler, outTexPos).r - 0.5);" +
                "    gl_FragColor = vec4(r, g, b, 1);" +
                "}";

        mProgram = Utils.createProgram(vertexShader, fragmentShader);

        GLES20.glUseProgram(mProgram);
        mPosLoc = GLES20.glGetAttribLocation(mProgram, "pos");
        mTexPosLoc = GLES20.glGetAttribLocation(mProgram, "texPos");
        mTexSamplerLocList.add(GLES20.glGetUniformLocation(mProgram, "Y_Sampler"));
        mTexSamplerLocList.add(GLES20.glGetUniformLocation(mProgram, "UV_Sampler"));

    }

    @Override
    public void addInputTexture(int tex, int colorFormat, ITextureUploader uploader) {
        if (mInputTexture.size() > 2
            || (mInputTexture.isEmpty() && colorFormat != GLES20.GL_LUMINANCE)
            || (!mInputTexture.isEmpty() && colorFormat != GLES20.GL_LUMINANCE_ALPHA)) {
            throw new RuntimeException();
        }
        mInputTexture.add(tex);
        if(uploader != null)
            mUploaders.add(uploader);
    }

    @Override
    public void process() {
        mEGLCore.makeCurrent();
        //EGL14.eglMakeCurrent(display, mEGLSurface, mEGLSurface, context);
        GLES20.glUseProgram(mProgram);
        int[] size = mEGLCore.getSurfaceSize();
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glViewport(0,0, size[0], size[1]);
        GLES20.glVertexAttribPointer(mPosLoc, 2, GLES20.GL_FLOAT, false, 16, sVertices.position(0));
        GLES20.glEnableVertexAttribArray(mPosLoc);
        GLES20.glVertexAttribPointer(mTexPosLoc, 2, GLES20.GL_FLOAT, false, 16, sVertices.position(2));
        GLES20.glEnableVertexAttribArray(mTexPosLoc);

        for (int i = 0; i < mUploaders.size(); i++) {
            mUploaders.get(i).uploadTexture(mInputTexture.get(i), i, mTexSamplerLocList.get(i));
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFBO);
        GLES20.glDrawElements(GLES20.GL_TRIANGLE_STRIP, 6, GLES20.GL_UNSIGNED_BYTE, sDrawOrders.position(0));
        mEGLCore.swapBuffer();
        //GLES20.glBindTexture(mInTexType, GLES20.GL_NONE);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_NONE);
        GLES20.glDisableVertexAttribArray(mPosLoc);
        GLES20.glDisableVertexAttribArray(mTexPosLoc);
    }

    @Override
    public void release() {
        GLES20.glDeleteProgram(mProgram);
        int[] values  = new int[1];
        values[0] = mOutputTexture;
        GLES20.glDeleteTextures(1, values, 0);
        values[0] = mFBO;
        GLES20.glDeleteFramebuffers(1, values, 0);
        mEGLCore.release();
    }
}
