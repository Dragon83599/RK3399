package com.zysj.standby;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public final class PlaybackPrefs {
    public static final String MODE_SEQUENCE = "sequence";
    public static final String MODE_RANDOM = "random";
    public static final long DEFAULT_IDLE_TIMEOUT_MS = 60000L;
    public static final long DEFAULT_IMAGE_INTERVAL_MS = 15000L;

    private static final String PREFS = "playback";
    private static final String KEY_MODE = "mode";
    private static final String KEY_SELECTED = "selected";
    private static final String KEY_IDLE_TIMEOUT_MS = "idle_timeout_ms";
    private static final String KEY_IMAGE_INTERVAL_MS = "image_interval_ms";
    private static final String KEY_SHOW_CLOCK = "show_clock";
    private static final String KEY_CLOCK_SIZE_SP = "clock_size_sp";
    private static final String KEY_CLOCK_HORIZONTAL = "clock_horizontal";
    private static final String KEY_CLOCK_VERTICAL = "clock_vertical";

    public static final int DEFAULT_CLOCK_SIZE_SP = 88;
    public static final String CLOCK_LEFT = "left";
    public static final String CLOCK_CENTER = "center";
    public static final String CLOCK_RIGHT = "right";
    public static final String CLOCK_TOP = "top";
    public static final String CLOCK_MIDDLE = "middle";
    public static final String CLOCK_BOTTOM = "bottom";

    private PlaybackPrefs() {
    }

    public static String getMode(Context context) {
        return prefs(context).getString(KEY_MODE, MODE_SEQUENCE);
    }

    public static void setMode(Context context, String mode) {
        prefs(context).edit().putString(KEY_MODE, mode).apply();
    }

    public static boolean hasSelection(Context context) {
        return prefs(context).contains(KEY_SELECTED);
    }

    public static Set<String> getSelectedPaths(Context context) {
        Set<String> stored = prefs(context).getStringSet(KEY_SELECTED, null);
        return stored == null ? new HashSet<String>() : new HashSet<String>(stored);
    }

    public static void setSelectedPaths(Context context, Set<String> paths) {
        prefs(context).edit().putStringSet(KEY_SELECTED, new HashSet<String>(paths)).apply();
    }

    public static long getIdleTimeoutMs(Context context) {
        return prefs(context).getLong(KEY_IDLE_TIMEOUT_MS, DEFAULT_IDLE_TIMEOUT_MS);
    }

    public static void setIdleTimeoutMs(Context context, long value) {
        prefs(context).edit().putLong(KEY_IDLE_TIMEOUT_MS, value).apply();
    }

    public static long getImageIntervalMs(Context context) {
        return prefs(context).getLong(KEY_IMAGE_INTERVAL_MS, DEFAULT_IMAGE_INTERVAL_MS);
    }

    public static void setImageIntervalMs(Context context, long value) {
        prefs(context).edit().putLong(KEY_IMAGE_INTERVAL_MS, value).apply();
    }

    public static boolean isShowClock(Context context) {
        return prefs(context).getBoolean(KEY_SHOW_CLOCK, true);
    }

    public static void setShowClock(Context context, boolean show) {
        prefs(context).edit().putBoolean(KEY_SHOW_CLOCK, show).apply();
    }

    public static int getClockSizeSp(Context context) {
        return prefs(context).getInt(KEY_CLOCK_SIZE_SP, DEFAULT_CLOCK_SIZE_SP);
    }

    public static void setClockSizeSp(Context context, int value) {
        prefs(context).edit().putInt(KEY_CLOCK_SIZE_SP, value).apply();
    }

    public static String getClockHorizontal(Context context) {
        return prefs(context).getString(KEY_CLOCK_HORIZONTAL, CLOCK_CENTER);
    }

    public static void setClockHorizontal(Context context, String value) {
        prefs(context).edit().putString(KEY_CLOCK_HORIZONTAL, value).apply();
    }

    public static String getClockVertical(Context context) {
        return prefs(context).getString(KEY_CLOCK_VERTICAL, CLOCK_BOTTOM);
    }

    public static void setClockVertical(Context context, String value) {
        prefs(context).edit().putString(KEY_CLOCK_VERTICAL, value).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
