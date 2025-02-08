package com.example.learningvideo.Decoder;

import static com.example.learningvideo.SharedTexture.AHARDWAREBUFFER_FORMAT_Y8Cb8Cr8_420;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.opengl.GLES11Ext;
import android.os.Handler;

public class Decoder3Client extends DecoderClientBase {
    public Decoder3Client(AssetFileDescriptor afd, Handler handler, Context ctx) {
        super(afd, handler, ctx);
    }

    @Override
    public int getTextureType() {
        return GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
    }

    @Override
    public int getHwBufferFormat() {
        return AHARDWAREBUFFER_FORMAT_Y8Cb8Cr8_420;
    }

    @Override
    public void waitFence() {
    }
}
