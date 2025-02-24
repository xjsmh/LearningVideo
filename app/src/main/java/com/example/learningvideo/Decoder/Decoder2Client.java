package com.example.learningvideo.Decoder;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import com.example.learningvideo.SharedTexture;

public class Decoder2Client extends DecoderClientBase {

    public Decoder2Client(AssetFileDescriptor afd, Handler handler) {
        super(afd, handler);
    }

    @Override
    public int getTextureType() {
        return GLES20.GL_TEXTURE_2D;
    }

    @Override
    public int getHwBufferFormat() {
        return SharedTexture.AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;
    }

    @Override
    public void waitFence() {
        ParcelFileDescriptor fence = null;
        try {
            fence = getService().getFence();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        if (fence != null) {
            getSharedTexture().waitFenceFd(fence);
        }
    }


}
