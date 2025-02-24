package com.example.learningvideo.GLES;

import android.opengl.GLES20;
import android.util.Log;

import java.nio.ByteBuffer;

public class Utils {
    public static int genTexture(int target, ByteBuffer data, int width, int height, int format) {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        GLES20.glBindTexture(target, textures[0]);
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        if(width > 0 && height > 0) {
            GLES20.glTexImage2D(target, 0, format, width, height, 0, format, GLES20.GL_UNSIGNED_BYTE, data);
        }
        GLES20.glBindTexture(target, GLES20.GL_NONE);
        return textures[0];
    }

    public static int createProgram(String vertexSource, String fragSource) {
        int v = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(v, vertexSource);
        GLES20.glCompileShader(v);

        int f = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(f, fragSource);
        GLES20.glCompileShader(f);

        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, v);
        GLES20.glAttachShader(program,f);
        GLES20.glLinkProgram(program);
        GLES20.glDeleteShader(v);
        GLES20.glDeleteShader(f);

        return program;
    }

    public static int genFrameBuffer(int attachment, int target, int texture) {
        int[] FBO = new int[1];
        GLES20.glGenFramebuffers(1, FBO, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, FBO[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, attachment, target, texture, 0);
        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException();
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_NONE);
        return FBO[0];
    }

    public static void checkGlError(String op) {
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            String msg = op + ": glError 0x" + Integer.toHexString(error);
            Log.e("ZJQCore", msg);
            throw new RuntimeException(msg);
        }
    }
}
