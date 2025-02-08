package com.example.learningvideo.Decoder;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.example.learningvideo.SharedTexture;


public class DecoderService extends Service {
    String TAG = "Decoder-Service";

    public DecoderService() {
        if(!SharedTexture.isAvailable()) {
            throw new RuntimeException();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        int format = intent.getIntExtra("format", -1);
        if (format == SharedTexture.AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM)
            return new Decoder2();
        else if (format == SharedTexture.AHARDWAREBUFFER_FORMAT_Y8Cb8Cr8_420)
            return new Decoder3();
        else
            throw new RuntimeException();
    }

}