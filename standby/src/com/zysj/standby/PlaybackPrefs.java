package com.zysj.standby;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public final class PlaybackPrefs {
    public static final String MODE_SEQUENCE = "sequence";
    public static final String MODE_RANDOM = "random";

    private static final String PREFS = "playback";
    private static final String KEY_MODE = "mode";
    private static final String KEY_SELECTED = "selected";

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

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
