package com.example.learningvideo.Filter;

import android.opengl.GLES20;

import com.example.learningvideo.GLES.EGLCore;
import com.example.learningvideo.GLES.Utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class GrayMask extends FilterBase {
    private static final FloatBuffer sVertices;
    private static final ByteBuffer sDrawOrders;
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

    private final int mInTexType;

    private final int mFBO;
    private final int mFBOProgram;
    private final int mPosLoc;
    private final int mTexPosLoc;
    private final List<Integer> mTexSamplersLoc = new ArrayList<>();

    @Override
    public void addInputTexture(int tex, int colorFormat, ITextureUploader uploader) {
        if (mInputTexture.size() > 1 || colorFormat != GLES20.GL_RGBA) {
            throw new RuntimeException();
        }
        mInputTexture.add(tex);
        if(uploader != null)
            mUploaders.add(uploader);
    }

    @Override
    public int getFBO() {
        return mFBO;
    }

    public GrayMask(FilterBase lastFilter, int width, int height) {
        this(lastFilter.getEGLCore(), lastFilter.getOutTextureType(), width, height);
        mLastFilter = lastFilter;
    }

    public GrayMask(EGLCore eglCore, int inTarget, int width, int height) {
        super(eglCore, inTarget, width, height);

        mEGLCore = new EGLCore(eglCore);
        mEGLCore.setPbufferSurface(width, height);
        mInTexType = inTarget;

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

        String fragmentShader =
                "#extension GL_OES_EGL_image_external : require \n" +
                        "precision highp float;" +
                        "varying vec2 outTexPos;" +
                        "uniform " + ((mInTexType == GLES20.GL_TEXTURE_2D) ? "sampler2D" : "samplerExternalOES") + " texSampler1;" +
                        "void main() {" +
                        "    vec3 W = vec3(0.2125, 0.7154, 0.0721);" +
                        "    vec4 mask = texture2D(texSampler1, outTexPos);" +
                        "    float luminance = dot(mask.rgb, W);" +
                        "    gl_FragColor = vec4(vec3(luminance), 1.0);" +
                        "}";
        mFBOProgram = Utils.createProgram(vertexShader, fragmentShader);
        GLES20.glUseProgram(mFBOProgram);

        mPosLoc = GLES20.glGetAttribLocation(mFBOProgram, "pos");
        mTexPosLoc = GLES20.glGetAttribLocation(mFBOProgram, "texPos");
        mTexSamplersLoc.add(GLES20.glGetUniformLocation(mFBOProgram, "texSampler1"));
    }

    @Override
    public void process(){
        mEGLCore.makeCurrent();
        int[] size = mEGLCore.getSurfaceSize();
        GLES20.glViewport(0, 0, size[0], size[1]);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(mFBOProgram);
        GLES20.glVertexAttribPointer(mPosLoc, 2, GLES20.GL_FLOAT, false, 16, sVertices.position(0));
        GLES20.glEnableVertexAttribArray(mPosLoc);
        GLES20.glVertexAttribPointer(mTexPosLoc, 2, GLES20.GL_FLOAT, false, 16, sVertices.position(2));
        GLES20.glEnableVertexAttribArray(mTexPosLoc);

        for (int i = 0; i < mUploaders.size(); i++) {
            mUploaders.get(i).uploadTexture(mInputTexture.get(i), i, mTexSamplersLoc.get(i));
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFBO);
        GLES20.glDrawElements(GLES20.GL_TRIANGLE_STRIP, 6, GLES20.GL_UNSIGNED_BYTE, sDrawOrders.position(0));
        mEGLCore.swapBuffer();
        GLES20.glBindTexture(mInTexType, GLES20.GL_NONE);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_NONE);
        GLES20.glDisableVertexAttribArray(mPosLoc);
        GLES20.glDisableVertexAttribArray(mTexPosLoc);
    }

    @Override
    public void release() {
        GLES20.glDeleteProgram(mFBOProgram);
        int[] values  = new int[1];
        values[0] = mOutputTexture;
        GLES20.glDeleteTextures(1, values, 0);
        values[0] = mFBO;
        GLES20.glDeleteFramebuffers(1, values, 0);
        mEGLCore.release();
    }

}
