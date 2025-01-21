package com.example.learningvideo.Filter;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;

import com.example.learningvideo.EGLResources;

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
    private final EGLSurface mEGLSurface;

    @Override
    public void addInputTexture(int tex, int colorFormat, ITextureUploader uploader) {
        if (mInputTexture.size() > 1 || colorFormat != GLES20.GL_RGBA) {
            throw new RuntimeException();
        }
        mInputTexture.add(tex);
        if(uploader != null)
            mUploaders.add(uploader);
    }

    public GrayMask(FilterBase lastFilter, EGLContext context, EGLDisplay display, EGLConfig config, int inTexType, int width, int height) {
        super(lastFilter, context, display, config, inTexType, width, height);
        int[] attrib_list = {
                EGL14.EGL_WIDTH, width,
                EGL14.EGL_HEIGHT, height,
                EGL14.EGL_NONE
        };
        mEGLSurface = EGL14.eglCreatePbufferSurface(display, config, attrib_list, 0);
        if (mEGLSurface == EGL14.EGL_NO_SURFACE) {
            throw new RuntimeException();
        }
        //EGL14.eglMakeCurrent(display, mEGLSurface, EGL14.eglGetCurrentSurface(EGL14.EGL_READ), EGL14.eglGetCurrentContext());
        int[] tex = new int[1];
        GLES20.glGenTextures(1, tex, 0);
        mOutputTexture = tex[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mOutputTexture);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height,0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE,null);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, GLES20.GL_NONE);
        mOutTextureType = GLES20.GL_TEXTURE_2D;

        int[] FBO = new int[1];
        GLES20.glGenFramebuffers(1, FBO, 0);
        mFBO = FBO[0];
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFBO);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, mOutputTexture, 0);
        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException();
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_NONE);

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

        mInTexType = inTexType;
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
        int f = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(f, fragmentShader);
        GLES20.glCompileShader(f);
        mFBOProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(mFBOProgram, v);
        GLES20.glAttachShader(mFBOProgram, f);
        GLES20.glLinkProgram(mFBOProgram);
        GLES20.glDeleteShader(v);
        GLES20.glDeleteShader(f);
        GLES20.glUseProgram(mFBOProgram);

        mPosLoc = GLES20.glGetAttribLocation(mFBOProgram, "pos");
        mTexPosLoc = GLES20.glGetAttribLocation(mFBOProgram, "texPos");
        mTexSamplersLoc.add(GLES20.glGetUniformLocation(mFBOProgram, "texSampler1"));

        mLastFilter = lastFilter;
        mEGLResources = new EGLResources(false)
                .setEGLContext(context)
                .setEGLDisplay(display)
                .setEGLConfig(config)
                .setEGLDrawSurface(mEGLSurface)
                .setProgram(mFBOProgram)
                .setFBO(mFBO)
                .setPosAttrib(new EGLResources.VertexAttrib(2, mPosLoc, GLES20.GL_FLOAT, 16, sVertices, 0))
                .setTexPosAttrib(new EGLResources.VertexAttrib(2, mTexPosLoc, GLES20.GL_FLOAT, 16, sVertices, 2))
                .setDrawOrdersAttrib(new EGLResources.ElementAttrib(6, GLES20.GL_UNSIGNED_BYTE, sDrawOrders, 0))
                .setTextureType(getOutTextureType())
                .setTexture(getOutputTexture());
    }

    @Override
    public void process(EGLContext context, EGLDisplay display, EGLSurface surface){
        EGLSurface readSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_READ);
        EGL14.eglMakeCurrent(display, mEGLSurface, mEGLSurface, context);
        int[] size = new int[2];
        EGL14.eglQuerySurface(display, mEGLSurface, EGL14.EGL_WIDTH, size, 0);
        EGL14.eglQuerySurface(display, mEGLSurface, EGL14.EGL_HEIGHT, size, 1);
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
        EGL14.eglSwapBuffers(display, mEGLSurface);
        GLES20.glBindTexture(mInTexType, GLES20.GL_NONE);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_NONE);
        GLES20.glDisableVertexAttribArray(mPosLoc);
        GLES20.glDisableVertexAttribArray(mTexPosLoc);
        EGL14.eglMakeCurrent(display, surface, readSurface, context);
    }

}
