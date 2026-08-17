package com.zysj.speaker.remotecontrol;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

public class TouchPadView extends View {
    public interface CommandSender {
        void send(String query, boolean quiet);
    }

    private static final float POINTER_SENSITIVITY = 2.4f;
    private static final int BOARD_WIDTH = 1920;
    private static final int BOARD_HEIGHT = 1080;

    private final CommandSender sender;
    private final Handler handler = new Handler();
    private boolean pointerScheduled;
    private float pendingDx;
    private float pendingDy;

    private float downX;
    private float downY;
    private float lastX;
    private float lastY;
    private float touchX;
    private float touchY;
    private boolean touchActive;
    private boolean dragging;
    private boolean moved;
    private boolean cursorMode = true;
    private float cursorX = BOARD_WIDTH / 2f;
    private float cursorY = BOARD_HEIGHT / 2f;
    private float lastTapBoardX = -1;
    private float lastTapBoardY = -1;
    private long lastTapTime;
    private boolean scrolling;
    private float scrollAccum;
    private float lastScrollY;
    private int scrollPointerId = -1;
    private final float scrollThreshold = dp(24);
    private final float dragSlop = dp(10);
    private final Paint borderPaint;
    private final Paint gridPaint;
    private final Paint cursorRingPaint;
    private final Paint cursorDotPaint;
    private final Paint tapRingPaint;
    private final Paint touchPaint;
    private final Paint textPaint;

    private final Runnable pointerFlushRunnable = new Runnable() {
        @Override
        public void run() {
            pointerScheduled = false;
            int dx = Math.round(pendingDx);
            int dy = Math.round(pendingDy);
            pendingDx = 0;
            pendingDy = 0;
            if (dx != 0 || dy != 0) {
                sender.send("cmd=pointer&dx=" + dx + "&dy=" + dy, true);
            }
        }
    };

    public TouchPadView(Context context, CommandSender sender) {
        super(context);
        this.sender = sender;
        borderPaint = new Paint();
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(2));
        borderPaint.setColor(0xFF9E9E9E);
        gridPaint = new Paint();
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(1));
        gridPaint.setColor(0xFFBDBDBD);
        cursorRingPaint = new Paint();
        cursorRingPaint.setStyle(Paint.Style.STROKE);
        cursorRingPaint.setStrokeWidth(dp(2));
        cursorRingPaint.setColor(0xFFE53935);
        cursorDotPaint = new Paint();
        cursorDotPaint.setStyle(Paint.Style.FILL);
        cursorDotPaint.setColor(0xFFE53935);
        tapRingPaint = new Paint();
        tapRingPaint.setStyle(Paint.Style.STROKE);
        tapRingPaint.setStrokeWidth(dp(2));
        tapRingPaint.setColor(0xFFFB8C00);
        touchPaint = new Paint();
        touchPaint.setStyle(Paint.Style.FILL);
        touchPaint.setColor(0x5533B5E5);
        textPaint = new Paint();
        textPaint.setColor(0xFF757575);
        textPaint.setTextSize(sp(12));
        textPaint.setTextAlign(Paint.Align.CENTER);
        setBackgroundColor(0xFFF5F5F5);
    }

    public void setCursorMode(boolean mode) {
        cursorMode = mode;
        pendingDx = 0;
        pendingDy = 0;
        invalidate();
    }

    public boolean isCursorMode() {
        return cursorMode;
    }

    public void release() {
        handler.removeCallbacks(pointerFlushRunnable);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(1, 1, getWidth() - 1, getHeight() - 1, borderPaint);

        if (!cursorMode) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            canvas.drawLine(cx, 0, cx, getHeight(), gridPaint);
            canvas.drawLine(0, cy, getWidth(), cy, gridPaint);
        }

        if (lastTapBoardX >= 0 && lastTapBoardY >= 0) {
            long elapsed = SystemClock.uptimeMillis() - lastTapTime;
            if (elapsed < 1500) {
                float alpha = 1f - elapsed / 1500f;
                tapRingPaint.setAlpha(Math.round(255 * alpha));
                textPaint.setAlpha(Math.round(255 * alpha));
                canvas.drawCircle(toViewX(lastTapBoardX), toViewY(lastTapBoardY),
                        dp(14), tapRingPaint);
                canvas.drawText("(" + Math.round(lastTapBoardX) + ","
                                + Math.round(lastTapBoardY) + ")",
                        toViewX(lastTapBoardX),
                        toViewY(lastTapBoardY) - dp(18), textPaint);
            } else {
                lastTapBoardX = -1;
                lastTapBoardY = -1;
            }
        }

        if (cursorMode) {
            float vx = toViewX(cursorX);
            float vy = toViewY(cursorY);
            canvas.drawLine(vx - dp(10), vy, vx + dp(10), vy, cursorRingPaint);
            canvas.drawLine(vx, vy - dp(10), vx, vy + dp(10), cursorRingPaint);
            canvas.drawCircle(vx, vy, dp(5), cursorDotPaint);
        }

        if (touchActive) {
            canvas.drawCircle(touchX, touchY, dp(9), touchPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                downX = event.getX();
                downY = event.getY();
                lastX = downX;
                lastY = downY;
                touchX = downX;
                touchY = downY;
                touchActive = true;
                dragging = false;
                moved = false;
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                getParent().requestDisallowInterceptTouchEvent(true);
                if (event.getPointerCount() >= 2) {
                    if (!scrolling) {
                        scrolling = true;
                        scrollPointerId = event.getPointerId(event.getPointerCount() - 1);
                        int index = event.findPointerIndex(scrollPointerId);
                        lastScrollY = index >= 0 ? event.getY(index) : event.getY();
                        scrollAccum = 0;
                    } else {
                        int index = event.findPointerIndex(scrollPointerId);
                        if (index >= 0) {
                            float y = event.getY(index);
                            float dy = y - lastScrollY;
                            lastScrollY = y;
                            scrollAccum += dy;
                            if (scrollAccum <= -scrollThreshold) {
                                scrollAccum = 0;
                                sender.send("cmd=scroll&dir=down&count=1", true);
                            } else if (scrollAccum >= scrollThreshold) {
                                scrollAccum = 0;
                                sender.send("cmd=scroll&dir=up&count=1", true);
                            }
                        }
                    }
                    invalidate();
                    return true;
                }
                float dx = event.getX() - lastX;
                float dy = event.getY() - lastY;
                lastX = event.getX();
                lastY = event.getY();
                touchX = lastX;
                touchY = lastY;
                if (cursorMode) {
                    if (Math.abs(dx) > 0.3f || Math.abs(dy) > 0.3f) {
                        moved = true;
                        pendingDx += dx * POINTER_SENSITIVITY;
                        pendingDy += dy * POINTER_SENSITIVITY;
                        cursorX = clamp(cursorX + dx * POINTER_SENSITIVITY, 0, BOARD_WIDTH - 1);
                        cursorY = clamp(cursorY + dy * POINTER_SENSITIVITY, 0, BOARD_HEIGHT - 1);
                        schedulePointerFlush();
                    }
                } else if (!dragging && Math.hypot(
                        event.getX() - downX, event.getY() - downY) > dragSlop) {
                    dragging = true;
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                getParent().requestDisallowInterceptTouchEvent(false);
                if (scrolling) {
                    scrolling = false;
                    scrollAccum = 0;
                    scrollPointerId = -1;
                    invalidate();
                    return true;
                }
                touchActive = false;
                if (cursorMode) {
                    flushPointerNow();
                    if (!moved) {
                        sender.send("cmd=click", false);
                    }
                } else {
                    int sx = toBoardX(downX);
                    int sy = toBoardY(downY);
                    int ex = toBoardX(event.getX());
                    int ey = toBoardY(event.getY());
                    if (dragging) {
                        sender.send("cmd=swipe&x1=" + sx + "&y1=" + sy
                                + "&x2=" + ex + "&y2=" + ey + "&duration=300", false);
                    } else {
                        sender.send("cmd=tap&x=" + ex + "&y=" + ey, false);
                        lastTapBoardX = ex;
                        lastTapBoardY = ey;
                        lastTapTime = SystemClock.uptimeMillis();
                    }
                }
                invalidate();
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                dragging = false;
                scrolling = false;
                scrollAccum = 0;
                scrollPointerId = -1;
                touchActive = false;
                pendingDx = 0;
                pendingDy = 0;
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void schedulePointerFlush() {
        if (pointerScheduled) {
            return;
        }
        pointerScheduled = true;
        handler.postDelayed(pointerFlushRunnable, 40);
    }

    private void flushPointerNow() {
        handler.removeCallbacks(pointerFlushRunnable);
        pointerFlushRunnable.run();
    }

    private float toViewX(float boardX) {
        return boardX / (BOARD_WIDTH - 1) * getWidth();
    }

    private float toViewY(float boardY) {
        return boardY / (BOARD_HEIGHT - 1) * getHeight();
    }

    private int toBoardX(float viewX) {
        return Math.max(0, Math.min(BOARD_WIDTH - 1,
                Math.round(viewX / getWidth() * BOARD_WIDTH)));
    }

    private int toBoardY(float viewY) {
        return Math.max(0, Math.min(BOARD_HEIGHT - 1,
                Math.round(viewY / getHeight() * BOARD_HEIGHT)));
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(float value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private int sp(float value) {
        return Math.round(getResources().getDisplayMetrics().scaledDensity * value);
    }
}
