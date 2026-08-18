package com.zysj.speaker.remote;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RemoteService extends Service {
    public static final int PORT = 8080;

    private static MediaControl mediaControl;
    private HttpServer httpServer;
    private DiscoveryBeacon beacon;
    private CursorOverlay cursorOverlay;
    private final Handler mainHandler = new Handler();
    private ExecutorService helperExecutor;
    private final Runnable helperWatchdog = new Runnable() {
        @Override
        public void run() {
            helperExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    RootInputHelper.ensureRunning(RemoteService.this);
                }
            });
            mainHandler.postDelayed(this, 5000);
        }
    };

    public static MediaControl getMediaControl() {
        return mediaControl;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(1, buildNotification());

        mediaControl = new MediaControl(this);
        cursorOverlay = new CursorOverlay(this);
        cursorOverlay.start();
        httpServer = new HttpServer(this, mediaControl, cursorOverlay);
        httpServer.start();

        beacon = new DiscoveryBeacon(this);
        beacon.start();

        mediaControl.start();

        helperExecutor = Executors.newSingleThreadExecutor();
        mainHandler.post(helperWatchdog);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (httpServer != null) {
            httpServer.stop();
        }
        if (beacon != null) {
            beacon.stop();
        }
        if (mediaControl != null) {
            mediaControl.stop();
            mediaControl = null;
        }
        if (cursorOverlay != null) {
            cursorOverlay.stop();
            cursorOverlay = null;
        }
        mainHandler.removeCallbacks(helperWatchdog);
        if (helperExecutor != null) {
            helperExecutor.shutdownNow();
            helperExecutor = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "remote";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "遥控接收端", NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(channel);
        }
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(this, channelId);
        } else {
            builder = new Notification.Builder(this);
        }
        String ip = NetInfo.wifiIp(this);
        String url = ip == null ? "http://<ip>:" + PORT : "http://" + ip + ":" + PORT;
        builder.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("遥控接收端已启动")
                .setContentText(url)
                .setOngoing(true);
        return builder.build();
    }
}
