package com.zysj.speaker.remote;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            try {
                Intent service = new Intent(context, RemoteService.class);
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(service);
                } else {
                    context.startService(service);
                }
                Log.i("RemoteMedia", "boot receiver started service");
            } catch (Exception e) {
                Log.e("RemoteMedia", "boot receiver start failed", e);
            }
        }
    }
}
