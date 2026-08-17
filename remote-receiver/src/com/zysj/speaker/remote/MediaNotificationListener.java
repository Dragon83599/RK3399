package com.zysj.speaker.remote;

import android.app.Notification;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

public class MediaNotificationListener extends NotificationListenerService {
    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        try {
            startForegroundService(new android.content.Intent(this, RemoteService.class));
        } catch (Exception e) {
            Log.e("RemoteMedia", "start remote service from listener failed", e);
        }
        MediaControl control = RemoteService.getMediaControl();
        if (control != null) {
            control.refresh();
        }
        StatusBarNotification[] active = getActiveNotifications();
        Log.i("RemoteMedia", "notification listener connected, active=" + active.length);
        for (StatusBarNotification sbn : active) {
            handle(sbn);
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        handle(sbn);
    }

    private void handle(StatusBarNotification sbn) {
        MediaControl control = RemoteService.getMediaControl();
        if (control == null || sbn == null) {
            return;
        }
        String pkg = sbn.getPackageName();
        if (!MediaControl.isPreferredPackage(pkg)) {
            return;
        }
        Notification notification = sbn.getNotification();
        if (notification == null) {
            return;
        }
        Bundle extras = notification.extras;
        MediaSession.Token token = extras.getParcelable(Notification.EXTRA_MEDIA_SESSION);
        Log.i("RemoteMedia", "notification from " + pkg + " token=" + (token != null));
        if (token != null) {
            MediaController controller = new MediaController(getApplicationContext(), token);
            control.useNotificationController(controller);
        } else {
            CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
            control.setNotificationStatus(pkg,
                    title == null ? "" : title.toString(),
                    text == null ? "" : text.toString(),
                    false);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        MediaControl control = RemoteService.getMediaControl();
        if (control != null) {
            control.refresh();
        }
    }
}
