package michael.example.com.smarthome;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebView;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class PracticeActivity extends AppCompatActivity {
    private WebView browser;
    Button practice, replay;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_practice);
        String item = getIntent().getExtras().getString("choice");

        Log.d(TAG, getIntent().getExtras().getString("choice") + "\nOur choice is: " +
                getIntent().getExtras().getString("choice"));


        browser = findViewById(R.id.videoView);
        practice = findViewById(R.id.practice);
        replay = findViewById(R.id.replay);

        browser.getSettings().setJavaScriptEnabled(true);
        browser.loadUrl("file:///android_asset/H-" + item + ".mp4");

        replay.setOnClickListener(view -> browser.loadUrl("file:///android_asset/H-" + item + ".mp4"));

        practice.setOnClickListener(view -> {
            Intent intent = new Intent(PracticeActivity.this,CameraPreview.class);
            intent.putExtra("choice", item);
            startActivity(intent);
        });


    }
}