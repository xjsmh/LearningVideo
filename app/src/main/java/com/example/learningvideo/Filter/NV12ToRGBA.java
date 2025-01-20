package com.example.learningvideo.Filter;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;

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
    private final EGLSurface mEGLSurface;
    private final int mPosLoc;
    private final int mTexPosLoc;

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

    public NV12ToRGBA(EGLDisplay display, EGLConfig config, int inTexType, int width, int height) {
        super(display, config, inTexType, width, height);
        int[] attrib_list = {
                EGL14.EGL_WIDTH, width,
                EGL14.EGL_HEIGHT, height,
                EGL14.EGL_NONE
        };
        mEGLSurface = EGL14.eglCreatePbufferSurface(display, config, attrib_list, 0);
        if (mEGLSurface == EGL14.EGL_NO_SURFACE) {
            throw new RuntimeException();
        }

        int[] tex = new int[1];
        GLES20.glGenTextures(1, tex, 0);
        mOutputTexture = tex[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mOutputTexture);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,  GLES20.GL_NONE);
        mOutTextureType = GLES20.GL_TEXTURE_2D;

        int[] FBO = new int[1];
        GLES20.glGenFramebuffers(1, FBO, 0);
        mFBO = FBO[0];
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,  mFBO);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, mOutputTexture, 0);
        if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException();
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,  GLES20.GL_NONE);

        String vertexShader =
                "attribute vec2 pos;" +
                "attribute vec2 texPos;" +
                "varying vec2 outTexPos;" +
                "void main() {" +
                "    gl_Position = vec4(pos, 0, 1);" +
                "    outTexPos = texPos;" +
                "}";
        int v = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(v, vertexShader);
        GLES20.glCompileShader(v);

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
        int f = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(f, fragmentShader);
        GLES20.glCompileShader(f);

        mProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(mProgram, v);
        GLES20.glAttachShader(mProgram, f);
        GLES20.glLinkProgram(mProgram);
        GLES20.glDeleteShader(v);
        GLES20.glDeleteShader(f);

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
        mUploaders.add(uploader);
    }

    @Override
    public void process(EGLContext context, EGLDisplay display, EGLSurface surface) {
        EGLSurface readS = EGL14.eglGetCurrentSurface(EGL14.EGL_READ);
        if(display ==EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException();
        }
        if(context == EGL14.EGL_NO_CONTEXT) {
            throw new RuntimeException();
        }
        EGL14.eglMakeCurrent(display, mEGLSurface, mEGLSurface, EGL14.eglGetCurrentContext());
        //EGL14.eglMakeCurrent(display, mEGLSurface, mEGLSurface, context);
        GLES20.glUseProgram(mProgram);
        int[] size = new int[2];
        EGL14.eglQuerySurface(display, mEGLSurface, EGL14.EGL_WIDTH, size, 0);
        EGL14.eglQuerySurface(display, mEGLSurface, EGL14.EGL_HEIGHT, size, 1);
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
        EGL14.eglSwapBuffers(display, mEGLSurface);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, GLES20.GL_NONE);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_NONE);
        GLES20.glDisableVertexAttribArray(mPosLoc);
        GLES20.glDisableVertexAttribArray(mTexPosLoc);
        EGL14.eglMakeCurrent(display, surface, readS, context);
    }
}
