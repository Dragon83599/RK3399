package com.zysj.standby;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

public class MainActivity extends Activity {
    private SlideShowView slideShow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        slideShow = new SlideShowView(this);
        setContentView(slideShow);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (slideShow != null) {
            slideShow.start();
        }
    }

    @Override
    protected void onStop() {
        if (slideShow != null) {
            slideShow.stop();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (slideShow != null) {
            slideShow.release();
            slideShow = null;
        }
        super.onDestroy();
    }
}
