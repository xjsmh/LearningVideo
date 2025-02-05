// IDecoder2Service.aidl
package com.example.learningvideo;

// Declare any non-default types here with import statements

interface IDecoder2Service {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
    void init(in AssetFileDescriptor afd);
    void start(in HardwareBuffer hwBuf);
    boolean decode();
    int getHeight();
    int getWidth();
    void release();
    ParcelFileDescriptor getFence();
}