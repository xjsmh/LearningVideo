package com.example.learningvideo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

public class MainActivity extends Activity {

    RadioGroup radioGroup;
    Button buttonSubmit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        radioGroup = findViewById(R.id.renderer);
        buttonSubmit = findViewById(R.id.button);

        buttonSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                int selectedId = radioGroup.getCheckedRadioButtonId();

                if (selectedId == -1) {
                    Toast.makeText(MainActivity.this, "Please select an option", Toast.LENGTH_SHORT).show();
                } else {
                    RadioButton selectedRadioButton = findViewById(selectedId);
                    int id = selectedRadioButton.getId();
                    Intent intent = new Intent();
                    switch (id) {
                        case R.id.renderer1:
                            intent.setClass(MainActivity.this, RenderActivity.TextureViewActivity.class);
                            break;
                        case R.id.renderer2:
                            intent.setClass(MainActivity.this, RenderActivity.GLSurfaceViewActivity.class);
                            break;
                        case R.id.renderer3:
                        case R.id.renderer4:
                        case R.id.renderer5_1:
                        case R.id.renderer5_2:
                            intent.setClass(MainActivity.this, RenderActivity.SurfaceViewActivity.class);
                            intent.putExtra("rendererId", id);
                            break;
                    }
                    startActivity(intent);
                }
            }
        });
    }

}