package com.example.learningvideo;

import android.hardware.HardwareBuffer;
import android.os.ParcelFileDescriptor;

public class SharedTexture {
    public final static int AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM = 1;
    public final static int AHARDWAREBUFFER_FORMAT_Y8Cb8Cr8_420 = 35;
    private HardwareBuffer mHwBuffer = null;
    private long mNativeContext = 0;
    static {
        System.loadLibrary("learningvideo");
    }
    public SharedTexture(int width, int height, int hwBufferFormat) {
        mNativeContext = createNativeSharedTexture(width, height, hwBufferFormat);
        mHwBuffer = getHardwareBuffer(mNativeContext);
    }

    public SharedTexture(HardwareBuffer buffer) {
        mNativeContext = createNativeSharedTexture2(buffer);
        mHwBuffer = buffer;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        if (mNativeContext != 0) {
            if (mHwBuffer != null) {
                mHwBuffer.close();
                mHwBuffer = null;
            }
            finalize(mNativeContext);
            mNativeContext = 0;
        }
    }

    private native void finalize(long nativeContext);

    public native static boolean isAvailable();

    private native long createNativeSharedTexture2(HardwareBuffer buffer);

    public void bindTexture(int frameTex, int texType) {
        bindTexToHwBuffer(mNativeContext, frameTex, texType);
    }

    public HardwareBuffer getHardwareBuffer() {
        return mHwBuffer;
    }
    public ParcelFileDescriptor createFenceFd() {
        int fd = createFence();
        if (fd != -1) {
            return ParcelFileDescriptor.adoptFd(fd);
        }
        return null;
    }

    public boolean waitFenceFd(ParcelFileDescriptor pfd) {
        if (pfd != null) {
            int fd = pfd.detachFd();
            if (fd != -1) {
                return waitFence(fd);
            }
        }

        return false;
    }
    private native int createFence();

    private native boolean waitFence(int fd);
    private native long createNativeSharedTexture(int width, int height, int hwBufferFormat);

    private native void bindTexToHwBuffer(long ctx, int frameTex, int texType);

    private native HardwareBuffer getHardwareBuffer(long ctx);

    public void updateFrame(byte[] data, ParcelFileDescriptor fence) {
        if (fence != null) {
            int fd = fence.detachFd();
            if (fd != -1) {
                updateFrame(mNativeContext, data, fd);
            }
        }
    }

    private native void updateFrame(long nativeContext, byte[] data, int fence);
}
