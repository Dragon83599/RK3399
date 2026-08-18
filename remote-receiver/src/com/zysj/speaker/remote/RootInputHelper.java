package com.zysj.speaker.remote;

import android.content.Context;
import android.util.Log;

import java.io.File;

public class RootInputHelper {
    private static final String TAG = "RootInputHelper";
    private static final String SCRIPT = "/data/local/tmp/input_helper.sh";
    private static final String ALIVE = "input_helper_alive";
    private static final long ALIVE_TIMEOUT_MS = 3000;

    private RootInputHelper() {
    }

    public static boolean isAlive(Context context) {
        try {
            File alive = new File(context.getFilesDir(), ALIVE);
            return alive.exists()
                    && System.currentTimeMillis() - alive.lastModified() < ALIVE_TIMEOUT_MS;
        } catch (Exception e) {
            return false;
        }
    }

    public static void ensureRunning(Context context) {
        if (isAlive(context)) {
            return;
        }
        for (int attempt = 0; attempt < 3 && !isAlive(context); attempt++) {
            try {
                Log.i(TAG, "starting root input helper, attempt=" + (attempt + 1));
                Process process = new ProcessBuilder("su", "-c",
                        "setsid sh " + SCRIPT + " >/dev/null 2>&1 &")
                        .redirectErrorStream(true)
                        .start();
                int exit = process.waitFor();
                Log.i(TAG, "root input helper start exit=" + exit);
            } catch (Exception e) {
                Log.w(TAG, "cannot start root input helper", e);
            }
            try {
                Thread.sleep(700);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
