package com.example.learningvideo.Decoder;

import android.content.res.AssetFileDescriptor;
import android.os.Handler;

public abstract class DecoderBase {
    public DecoderBase(AssetFileDescriptor afd, Handler workHandler) {
    }

    public abstract boolean isEOS();

    public abstract int getHeight();

    public abstract int getWidth();

    public abstract void start(Object obj);

    public abstract void decode();

    public abstract void release();

}
