package com.example.learningvideo.Decoder;

public abstract class DecoderBase {
    public abstract boolean isEOS();

    public abstract void setObject(Object obj);

    public abstract int getHeight();

    public abstract int getWidth();

    public abstract void start();

    public abstract void decode();

    public abstract void release();
}
