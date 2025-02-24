package com.example.learningvideo;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

public class MainActivity extends Activity {
    @Override
    protected void onStart() {
        super.onStart();
        Core.getInstance().start();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Core.getInstance().init(getResources().openRawResourceFd(R.raw.big_buck_bunny_720p_10mb), this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isFinishing()) {
            Core.getInstance().stop();
        } else {
            Core.getInstance().pause();
        }
    }
}