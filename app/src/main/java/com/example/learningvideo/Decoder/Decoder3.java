package com.example.learningvideo.Decoder;

import android.content.res.AssetFileDescriptor;
import android.hardware.HardwareBuffer;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import com.example.learningvideo.IDecoderService;
import com.example.learningvideo.SharedTexture;

public class Decoder3 extends IDecoderService.Stub{

    Decoder1 mDecoder;
    SharedTexture mSharedTexture;

    @Override
    public void init(AssetFileDescriptor afd) throws RemoteException {
        mDecoder = new Decoder1(afd, null);
    }

    @Override
    public void start(HardwareBuffer hwBuf) throws RemoteException {
        mSharedTexture = new SharedTexture(hwBuf);
        mDecoder.setNeedSaveYuvByteArray(true);
        mDecoder.start(null);
    }

    @Override
    public boolean decode(ParcelFileDescriptor fence) throws RemoteException {
        mDecoder.decode();
        boolean eos = mDecoder.isEOS();
        if (!eos) {
            mSharedTexture.updateFrame(mDecoder.getYuvByteArray(), fence);
        }
        return eos;
    }

    @Override
    public int getHeight() throws RemoteException {
        return mDecoder.getHeight();
    }

    @Override
    public int getWidth() throws RemoteException {
        return mDecoder.getWidth();
    }

    @Override
    public void release() throws RemoteException {
        mDecoder.release();
        mSharedTexture = null;
    }

    @Override
    public ParcelFileDescriptor getFence() throws RemoteException {
        return null;
    }

}
