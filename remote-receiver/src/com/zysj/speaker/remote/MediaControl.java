package com.zysj.speaker.remote;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.view.KeyEvent;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MediaControl {
    private static final String PREFS = "remote_prefs";
    private static final String KEY_VOLUME_LIMIT_PERCENT = "volume_limit_percent";
    private static final int DEFAULT_VOLUME_LIMIT_PERCENT = 80;
    private static final String[] PREFERRED = {
            "com.netease.cloudmusic",
            "zhiyue.go.fmzonghe.hecheng",
            "tv.danmaku.bili",
            "com.android.music"
    };

    private final Context context;
    private MediaSessionManager sessionManager;
    private AudioManager audioManager;
    private MediaController active;
    private MediaController notificationController;
    private String fallbackPkg = "";
    private String fallbackTitle = "";
    private String fallbackArtist = "";
    private boolean fallbackPlaying;

    private final MediaController.Callback controllerCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            notifyChanged();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            notifyChanged();
        }

        @Override
        public void onSessionDestroyed() {
            refresh();
        }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener =
            new MediaSessionManager.OnActiveSessionsChangedListener() {
                @Override
                public void onActiveSessionsChanged(List<MediaController> controllers) {
                    refresh();
                }
            };

    public interface Listener {
        void onStatusChanged();
    }

    private Listener listener;

    public MediaControl(Context context) {
        this.context = context.getApplicationContext();
    }

    public void start() {
        sessionManager = (MediaSessionManager) context
                .getSystemService(Context.MEDIA_SESSION_SERVICE);
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        final ComponentName component = new ComponentName(context, MediaButtonReceiver.class);
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.i("RemoteMedia", "media init start");
                try {
                    sessionManager.addOnActiveSessionsChangedListener(sessionsListener, component);
                } catch (Exception ignored) {
                }
                try {
                    audioManager.registerMediaButtonEventReceiver(component);
                    Log.i("RemoteMedia", "media button receiver registered");
                } catch (Exception ignored) {
                    Log.e("RemoteMedia", "register media button failed");
                }
                refresh();
            }
        }, "remote-media").start();
    }

    public void stop() {
        if (sessionManager != null) {
            sessionManager.removeOnActiveSessionsChangedListener(sessionsListener);
        }
        if (active != null) {
            active.unregisterCallback(controllerCallback);
        }
        active = null;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public synchronized void refresh() {
        List<MediaController> all = new ArrayList<MediaController>();
        try {
            all = sessionManager.getActiveSessions(
                    new ComponentName(context, MediaButtonReceiver.class));
            Log.i("RemoteMedia", "sessions via component: " + all.size());
        } catch (Exception ignored) {
        }
        if (all.isEmpty()) {
            try {
                List<MediaController> allSessions =
                        sessionManager.getActiveSessions(null);
                Log.i("RemoteMedia", "sessions via null: " + allSessions.size());
                all = allSessions;
            } catch (Exception ignored) {
                Log.e("RemoteMedia", "getActiveSessions(null) denied");
            }
        }

        if (!all.isEmpty()) {
            MediaController best = null;
            for (MediaController controller : all) {
                if (isPreferredPackage(controller.getPackageName())) {
                    best = controller;
                    break;
                }
            }
            if (best == null) {
                for (MediaController controller : all) {
                    if (!controller.getPackageName().equals(context.getPackageName())) {
                        best = controller;
                        break;
                    }
                }
            }
            if (best != active) {
                if (active != null) {
                    active.unregisterCallback(controllerCallback);
                }
                active = best;
                if (active != null) {
                    active.registerCallback(controllerCallback);
                }
            }
            notifyChanged();
            return;
        }

        if (notificationController != null && notificationController != active) {
            if (active != null) {
                active.unregisterCallback(controllerCallback);
            }
            active = notificationController;
            if (active != null) {
                active.registerCallback(controllerCallback);
            }
        }
        notifyChanged();
    }

    public static boolean isPreferredPackage(String packageName) {
        for (String pkg : PREFERRED) {
            if (pkg.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    public synchronized void useNotificationController(MediaController controller) {
        notificationController = controller;
        if (active == null || active == notificationController) {
            if (active != null) {
                active.unregisterCallback(controllerCallback);
            }
            active = controller;
            if (active != null) {
                active.registerCallback(controllerCallback);
            }
            notifyChanged();
        }
    }

    public synchronized void setNotificationStatus(String pkg, String title,
                                                   String artist, boolean playing) {
        fallbackPkg = pkg;
        fallbackTitle = title;
        fallbackArtist = artist;
        fallbackPlaying = playing;
        if (active == null) {
            notifyChanged();
        }
    }

    public boolean isPlaying() {
        if (active != null) {
            PlaybackState state = active.getPlaybackState();
            return state != null && state.getState() == PlaybackState.STATE_PLAYING;
        }
        return fallbackPlaying;
    }

    public void toggle() {
        if (active != null) {
            if (isPlaying()) {
                active.getTransportControls().pause();
            } else {
                active.getTransportControls().play();
            }
        } else {
            dispatchKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        }
    }

    public void play() {
        if (active != null) {
            active.getTransportControls().play();
        } else {
            dispatchKey(KeyEvent.KEYCODE_MEDIA_PLAY);
        }
    }

    public void pause() {
        if (active != null) {
            active.getTransportControls().pause();
        } else {
            dispatchKey(KeyEvent.KEYCODE_MEDIA_PAUSE);
        }
    }

    public void next() {
        if (active != null) {
            active.getTransportControls().skipToNext();
        } else {
            dispatchKey(KeyEvent.KEYCODE_MEDIA_NEXT);
        }
    }

    public void prev() {
        if (active != null) {
            active.getTransportControls().skipToPrevious();
        } else {
            dispatchKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        }
    }

    private void dispatchKey(int keyCode) {
        try {
            long now = android.os.SystemClock.uptimeMillis();
            audioManager.dispatchMediaKeyEvent(
                    new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
            audioManager.dispatchMediaKeyEvent(
                    new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
            Log.i("RemoteMedia", "dispatched key " + keyCode);
        } catch (Exception e) {
            Log.e("RemoteMedia", "dispatch key failed", e);
        }
    }

    public void forwardKey(KeyEvent event) {
        if (active != null && event != null) {
            try {
                active.dispatchMediaButtonEvent(event);
            } catch (Exception ignored) {
            }
        }
    }

    public int getVolume() {
        return audioManager == null ? 0 : audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    public int getMaxVolume() {
        return audioManager == null ? 1 : audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    }

    public void volumeUp() {
        if (audioManager != null) {
            if (getVolume() >= limitVolume()) {
                return;
            }
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0);
            if (getVolume() > limitVolume()) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, limitVolume(), 0);
            }
        }
    }

    public void volumeDown() {
        if (audioManager != null) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0);
        }
    }

    public void setVolumePercent(int percent) {
        if (audioManager == null) {
            return;
        }
        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int target = Math.max(0, Math.min(100, percent)) * max / 100;
        if (target > limitVolume()) {
            target = limitVolume();
        }
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
    }

    public int getVolumeLimitPercent() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_VOLUME_LIMIT_PERCENT, DEFAULT_VOLUME_LIMIT_PERCENT);
    }

    public void setVolumeLimitPercent(int percent) {
        int value = Math.max(1, Math.min(100, percent));
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_VOLUME_LIMIT_PERCENT, value)
                .apply();
        if (audioManager != null && getVolume() > limitVolume()) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, limitVolume(), 0);
        }
    }

    private int limitVolume() {
        if (audioManager == null) {
            return getMaxVolume();
        }
        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return Math.round(max * getVolumeLimitPercent() / 100f);
    }

    public JSONObject status(Context ctx) {
        JSONObject json = new JSONObject();
        try {
            json.put("ok", true);
            json.put("name", "壁画音响");
            String ip = NetInfo.wifiIp(ctx);
            json.put("ip", ip == null ? "" : ip);
            json.put("port", RemoteService.PORT);
            json.put("playing", isPlaying());
            json.put("volume", getVolume());
            json.put("maxVolume", getMaxVolume());
            json.put("volumeLimit", getVolumeLimitPercent());

            String title = fallbackTitle;
            String artist = fallbackArtist;
            String album = "";
            String app = fallbackPkg;
            long duration = -1;
            long position = 0;
            if (active != null) {
                app = active.getPackageName();
                MediaMetadata meta = active.getMetadata();
                if (meta != null) {
                    title = valueOrEmpty(meta.getString(MediaMetadata.METADATA_KEY_TITLE));
                    artist = valueOrEmpty(meta.getString(MediaMetadata.METADATA_KEY_ARTIST));
                    album = valueOrEmpty(meta.getString(MediaMetadata.METADATA_KEY_ALBUM));
                    duration = meta.getLong(MediaMetadata.METADATA_KEY_DURATION);
                }
                PlaybackState state = active.getPlaybackState();
                if (state != null) {
                    position = state.getPosition();
                }
            }
            json.put("app", app);
            json.put("title", title);
            json.put("artist", artist);
            json.put("album", album);
            json.put("position", position);
            json.put("duration", duration);

            PackageManager pm = ctx.getPackageManager();
            JSONArray apps = new JSONArray();
            for (String pkg : PREFERRED) {
                try {
                    ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                    JSONObject appInfo = new JSONObject();
                    appInfo.put("pkg", pkg);
                    appInfo.put("label", pm.getApplicationLabel(info).toString());
                    apps.put(appInfo);
                } catch (PackageManager.NameNotFoundException ignored) {
                }
            }
            json.put("apps", apps);
        } catch (Exception e) {
            try {
                json.put("ok", false);
                json.put("error", e.toString());
            } catch (Exception ignored) {
            }
        }
        return json;
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private void notifyChanged() {
        if (listener != null) {
            listener.onStatusChanged();
        }
    }
}
