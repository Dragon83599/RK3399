package com.zysj.speaker.remote;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

public class CursorOverlay {
    private static final int CURSOR_SIZE_DP = 56;
    private static final long FLASH_DURATION_MS = 350;

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private CursorView cursorView;
    private WindowManager.LayoutParams layoutParams;
    private volatile boolean started;
    private volatile float pointerX;
    private volatile float pointerY;
    private volatile boolean visible = true;
    private int screenWidth = 1920;
    private int screenHeight = 1080;
    private final int cursorSizePx;

    public CursorOverlay(Context context) {
        this.context = context.getApplicationContext();
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        if (metrics.widthPixels > 0 && metrics.heightPixels > 0) {
            screenWidth = metrics.widthPixels;
            screenHeight = metrics.heightPixels;
        }
        cursorSizePx = Math.round(metrics.density * CURSOR_SIZE_DP);
        pointerX = screenWidth / 2f;
        pointerY = screenHeight / 2f;
    }

    public boolean isAvailable() {
        return started && (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context));
    }

    public float getPointerX() {
        return pointerX;
    }

    public float getPointerY() {
        return pointerY;
    }

    public void start() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (started || Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(context)) {
                    return;
                }
                windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                cursorView = new CursorView(context);
                int type = Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE;
                layoutParams = new WindowManager.LayoutParams(
                        cursorSizePx,
                        cursorSizePx,
                        type,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT);
                layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
                updateWindowPosition();
                try {
                    windowManager.addView(cursorView, layoutParams);
                    started = true;
                } catch (Exception ignored) {
                }
            }
        });
    }

    public void stop() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                started = false;
                if (windowManager != null && cursorView != null) {
                    try {
                        windowManager.removeView(cursorView);
                    } catch (Exception ignored) {
                    }
                }
                windowManager = null;
                cursorView = null;
                layoutParams = null;
            }
        });
    }

    public boolean moveBy(float dx, float dy) {
        if (!isAvailable()) {
            return false;
        }
        pointerX = clamp(pointerX + dx, 0, screenWidth - 1);
        pointerY = clamp(pointerY + dy, 0, screenHeight - 1);
        postMove();
        return true;
    }

    public boolean setPointer(float x, float y) {
        if (!isAvailable()) {
            return false;
        }
        pointerX = clamp(x, 0, screenWidth - 1);
        pointerY = clamp(y, 0, screenHeight - 1);
        postMove();
        return true;
    }

    public boolean clickAtCurrent() {
        if (!isAvailable()) {
            return false;
        }
        boolean ok = TouchAccessibilityService.tap(Math.round(pointerX), Math.round(pointerY));
        if (ok) {
            flash();
        }
        return ok;
    }

    public void flash() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (cursorView != null) {
                    cursorView.flash();
                }
            }
        });
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (cursorView != null) {
                    cursorView.setVisibility(visible ? View.VISIBLE : View.GONE);
                }
            }
        });
    }

    public boolean isVisible() {
        return visible;
    }

    private void postMove() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (started) {
                    updateWindowPosition();
                }
            }
        });
    }

    private void updateWindowPosition() {
        if (layoutParams == null) {
            return;
        }
        layoutParams.x = Math.round(pointerX - cursorSizePx / 2f);
        layoutParams.y = Math.round(pointerY - cursorSizePx / 2f);
        if (windowManager != null && cursorView != null) {
            try {
                windowManager.updateViewLayout(cursorView, layoutParams);
            } catch (Exception ignored) {
            }
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private class CursorView extends View {
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint darkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint flashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float flashProgress = -1f;

        CursorView(Context context) {
            super(context);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(dp(2.5f));
            ringPaint.setColor(0xFFFFFFFF);
            darkPaint.setStyle(Paint.Style.STROKE);
            darkPaint.setStrokeWidth(dp(5f));
            darkPaint.setColor(0x99000000);
            centerPaint.setStyle(Paint.Style.FILL);
            centerPaint.setColor(0xFFE53935);
            tickPaint.setStyle(Paint.Style.STROKE);
            tickPaint.setStrokeWidth(dp(2f));
            tickPaint.setColor(0xFFFFFFFF);
            flashPaint.setStyle(Paint.Style.STROKE);
            flashPaint.setStrokeWidth(dp(3f));
            flashPaint.setColor(0xFFE53935);
        }

        void flash() {
            flashProgress = 0f;
            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(FLASH_DURATION_MS);
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    flashProgress = (Float) animation.getAnimatedValue();
                    invalidate();
                }
            });
            animator.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = Math.min(getWidth(), getHeight()) * 0.34f;

            if (flashProgress >= 0f) {
                float flashRadius = radius * (0.45f + 0.9f * flashProgress);
                flashPaint.setAlpha(Math.round(220 * (1f - flashProgress)));
                canvas.drawCircle(cx, cy, flashRadius, flashPaint);
                if (flashProgress >= 1f) {
                    flashProgress = -1f;
                }
            }

            float tick = radius + dp(4f);
            canvas.drawLine(cx - tick, cy, cx + tick, cy, darkPaint);
            canvas.drawLine(cx, cy - tick, cx, cy + tick, darkPaint);
            canvas.drawLine(cx - tick, cy, cx + tick, cy, tickPaint);
            canvas.drawLine(cx, cy - tick, cx, cy + tick, tickPaint);

            canvas.drawCircle(cx, cy, radius, darkPaint);
            canvas.drawCircle(cx, cy, radius, ringPaint);
            canvas.drawCircle(cx, cy, dp(4f), centerPaint);
        }

        private int dp(float value) {
            return Math.round(getResources().getDisplayMetrics().density * value);
        }
    }
}
