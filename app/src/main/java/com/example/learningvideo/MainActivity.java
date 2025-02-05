package com.example.learningvideo;

import android.os.Bundle;
import android.view.SurfaceView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        Core core = new Core(getResources().openRawResourceFd(R.raw.big_buck_bunny_720p_10mb), this);
        core.start();
        setContentView(core.getSurfaceView());
    }
}