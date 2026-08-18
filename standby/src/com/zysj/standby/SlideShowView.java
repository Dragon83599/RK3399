package com.zysj.standby;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.graphics.Typeface;
import android.view.View;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.List;
import java.util.Set;

public class SlideShowView extends View {
    private static final long FADE_MS = 900L;
    private static final long RESCAN_MS = 5000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final List<File> images = new ArrayList<File>();

    private Bitmap current;
    private Bitmap previous;
    private float fade;
    private long fadeStart;
    private boolean running;
    private boolean loading;
    private boolean randomMode;
    private boolean showClock;
    private int clockSizeSp = PlaybackPrefs.DEFAULT_CLOCK_SIZE_SP;
    private String clockHorizontal = PlaybackPrefs.CLOCK_CENTER;
    private String clockVertical = PlaybackPrefs.CLOCK_BOTTOM;
    private int index;
    private long intervalMs = PlaybackPrefs.DEFAULT_IMAGE_INTERVAL_MS;
    private String clockText = "";
    private String dateText = "";
    private final SimpleDateFormat clockFormat =
            new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("M月d日 EEEE", Locale.CHINA);

    public SlideShowView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        index = 0;
        showClock = PlaybackPrefs.isShowClock(getContext());
        clockSizeSp = PlaybackPrefs.getClockSizeSp(getContext());
        clockHorizontal = PlaybackPrefs.getClockHorizontal(getContext());
        clockVertical = PlaybackPrefs.getClockVertical(getContext());
        updateClockText();
        handler.removeCallbacks(clockRunnable);
        if (showClock) {
            handler.postDelayed(clockRunnable, 30000L);
        }
        rebuildPlaylist();
        if (images.isEmpty()) {
            scheduleRescan();
            return;
        }
        loadNext();
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(advanceRunnable);
        handler.removeCallbacks(fadeRunnable);
        handler.removeCallbacks(rescanRunnable);
        handler.removeCallbacks(clockRunnable);
    }

    private final Runnable clockRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running || !showClock) {
                return;
            }
            updateClockText();
            handler.postDelayed(this, 30000L);
        }
    };

    private void updateClockText() {
        Date now = new Date();
        clockText = clockFormat.format(now);
        dateText = dateFormat.format(now);
        invalidate();
    }

    public void release() {
        stop();
        if (previous != null) {
            previous.recycle();
            previous = null;
        }
        if (current != null) {
            current.recycle();
            current = null;
        }
    }

    private void rebuildPlaylist() {
        images.clear();
        List<File> all = SongImages.find(getContext());
        if (PlaybackPrefs.hasSelection(getContext())) {
            Set<String> selected = PlaybackPrefs.getSelectedPaths(getContext());
            for (File file : all) {
                if (selected.contains(file.getAbsolutePath())) {
                    images.add(file);
                }
            }
        } else {
            images.addAll(all);
        }
        randomMode = PlaybackPrefs.MODE_RANDOM.equals(PlaybackPrefs.getMode(getContext()));
        intervalMs = PlaybackPrefs.getImageIntervalMs(getContext());
        if (randomMode) {
            Collections.shuffle(images);
        }
    }

    private void loadNext() {
        if (!running || loading || images.isEmpty()) {
            return;
        }
        if (randomMode && index >= images.size()) {
            Collections.shuffle(images);
            index = 0;
        }
        loading = true;
        File file = images.get(index % images.size());
        index++;
        new LoadTask(file).execute();
    }

    private void scheduleNext() {
        handler.removeCallbacks(advanceRunnable);
        if (running && !images.isEmpty()) {
            handler.postDelayed(advanceRunnable, intervalMs);
        }
    }

    private void scheduleRescan() {
        handler.removeCallbacks(rescanRunnable);
        if (running) {
            handler.postDelayed(rescanRunnable, RESCAN_MS);
        }
    }

    private final Runnable advanceRunnable = new Runnable() {
        @Override
        public void run() {
            loadNext();
        }
    };

    private final Runnable fadeRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            long now = SystemClock.uptimeMillis();
            fade = Math.min(1f, (now - fadeStart) / (float) FADE_MS);
            invalidate();
            if (fade < 1f) {
                handler.postDelayed(this, 16L);
            } else if (previous != null) {
                previous.recycle();
                previous = null;
            }
        }
    };

    private final Runnable rescanRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            rebuildPlaylist();
            if (!images.isEmpty()) {
                loadNext();
            } else {
                scheduleRescan();
            }
        }
    };

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) {
            return;
        }
        if (previous != null && fade < 1f) {
            drawFit(canvas, previous, width, height, Math.round(255f * (1f - fade)));
        }
        if (current != null) {
            drawFit(canvas, current, width, height, 255);
        }
        if (showClock && clockText.length() > 0) {
            drawClock(canvas);
        }
    }

    private void drawClock(Canvas canvas) {
        float density = getResources().getDisplayMetrics().density;
        int margin = Math.round(48f * density);
        float clockSize = clockSizeSp * density;
        float dateSize = Math.max(20f, clockSize * 0.4f);
        float gap = dateSize * 0.6f;

        paint.setAntiAlias(true);
        paint.setShadowLayer(12f * density, 0f, 4f * density,
                Color.argb(180, 0, 0, 0));
        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));

        paint.setTextSize(clockSize);
        float timeWidth = paint.measureText(clockText);
        paint.setTextSize(dateSize);
        float dateWidth = paint.measureText(dateText);
        float blockWidth = Math.max(timeWidth, dateWidth);
        float blockHeight = clockSize + gap + dateSize;

        float x;
        if (PlaybackPrefs.CLOCK_CENTER.equals(clockHorizontal)) {
            x = (getWidth() - blockWidth) / 2f;
        } else if (PlaybackPrefs.CLOCK_RIGHT.equals(clockHorizontal)) {
            x = getWidth() - margin - blockWidth;
        } else {
            x = margin;
        }

        float y;
        if (PlaybackPrefs.CLOCK_MIDDLE.equals(clockVertical)) {
            y = (getHeight() - blockHeight) / 2f;
        } else if (PlaybackPrefs.CLOCK_BOTTOM.equals(clockVertical)) {
            y = getHeight() - margin - blockHeight;
        } else {
            y = margin;
        }

        paint.setTextSize(clockSize);
        canvas.drawText(clockText, x, y + clockSize, paint);
        paint.setTextSize(dateSize);
        canvas.drawText(dateText, x, y + clockSize + gap + dateSize, paint);
        paint.clearShadowLayer();
        paint.setTypeface(null);
    }

    private void drawFit(Canvas canvas, Bitmap bitmap, int width, int height, int alpha) {
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }
        float scale = Math.min(
                (float) width / bitmap.getWidth(),
                (float) height / bitmap.getHeight());
        int drawWidth = Math.round(bitmap.getWidth() * scale);
        int drawHeight = Math.round(bitmap.getHeight() * scale);
        RectF dst = new RectF(
                (width - drawWidth) / 2f,
                (height - drawHeight) / 2f,
                (width + drawWidth) / 2f,
                (height + drawHeight) / 2f);
        paint.setAlpha(alpha);
        canvas.drawBitmap(bitmap, null, dst, paint);
    }

    private Bitmap decode(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        int sample = 1;
        while (bounds.outWidth / sample > 1920 || bounds.outHeight / sample > 1080) {
            sample <<= 1;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private class LoadTask extends AsyncTask<Void, Void, Bitmap> {
        private final File file;

        LoadTask(File file) {
            this.file = file;
        }

        @Override
        protected Bitmap doInBackground(Void... params) {
            return decode(file);
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            loading = false;
            if (!running || bitmap == null) {
                if (bitmap != null) {
                    bitmap.recycle();
                }
                scheduleNext();
                return;
            }
            previous = current;
            current = bitmap;
            fade = 0f;
            fadeStart = SystemClock.uptimeMillis();
            handler.removeCallbacks(fadeRunnable);
            handler.post(fadeRunnable);
            invalidate();
            scheduleNext();
        }
    }
}
