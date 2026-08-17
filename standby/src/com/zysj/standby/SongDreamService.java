package com.zysj.standby;

import android.service.dreams.DreamService;

public class SongDreamService extends DreamService {
    private SlideShowView slideShow;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        slideShow = new SlideShowView(this);
        setContentView(slideShow);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        if (slideShow != null) {
            slideShow.start();
        }
    }

    @Override
    public void onDreamingStopped() {
        if (slideShow != null) {
            slideShow.stop();
        }
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        if (slideShow != null) {
            slideShow.release();
            slideShow = null;
        }
        super.onDetachedFromWindow();
    }
}
