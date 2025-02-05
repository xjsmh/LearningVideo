package com.example.learningvideo;

import android.hardware.HardwareBuffer;
import android.os.ParcelFileDescriptor;

public class SharedTexture {
    private HardwareBuffer mHwBuffer = null;
    private long mNativeContext = 0;
    static {
        System.loadLibrary("learningvideo");
    }
    public SharedTexture(int width, int height) {
        mNativeContext = createNativeSharedTexture(width, height, 0);
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

    public void bindTexture(int frameTex) {
        bindTexToHwBuffer(mNativeContext, frameTex);
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
    private native long createNativeSharedTexture(int width, int height, int type);

    private native void bindTexToHwBuffer(long ctx, int frameTex);

    private native HardwareBuffer getHardwareBuffer(long ctx);
}
