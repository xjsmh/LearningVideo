package com.example.learningvideo;

import android.app.Activity;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Bundle;

import com.example.learningvideo.Renderer.Renderer1;
import com.example.learningvideo.Renderer.Renderer2;
import com.example.learningvideo.Renderer.Renderer3;
import com.example.learningvideo.Renderer.Renderer4;
import com.example.learningvideo.Renderer.Renderer5;

public class RenderActivity extends Activity {

    @Override
    protected void onStart() {
        super.onStart();
        Core.getInstance().start();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

    public static class GLSurfaceViewActivity extends RenderActivity {

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_glsurface_view);
            Core.getInstance().init(getResources().openRawResourceFd(R.raw.big_buck_bunny_720p_10mb), this, Renderer2.class);
        }
    }

    public static class SurfaceViewActivity extends RenderActivity {

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_surface_view);
            int id = getIntent().getIntExtra("rendererId", 0);
            Class render_clz = id == R.id.renderer3 ? Renderer3.class :
                                id == R.id.renderer4 ? Renderer4.class :
                                 Renderer5.class;
            if (id == R.id.renderer5_1) {
                Renderer5.setFrameTextureType(GLES20.GL_TEXTURE_2D);
            } else if (id == R.id.renderer5_2) {
                Renderer5.setFrameTextureType(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
            }
            Core.getInstance().init(getResources().openRawResourceFd(R.raw.big_buck_bunny_720p_10mb), this, render_clz);
        }
    }

    public static class TextureViewActivity extends RenderActivity {

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_texture_view);
            Core.getInstance().init(getResources().openRawResourceFd(R.raw.big_buck_bunny_720p_10mb), this, Renderer1.class);
        }
    }
}