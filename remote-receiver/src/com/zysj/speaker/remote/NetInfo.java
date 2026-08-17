package com.zysj.speaker.remote;

import android.content.Context;
import android.net.wifi.WifiManager;

public final class NetInfo {
    private NetInfo() {
    }

    public static String wifiIp(Context context) {
        try {
            WifiManager wifi = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            int ip = wifi.getConnectionInfo().getIpAddress();
            if (ip == 0) {
                return null;
            }
            return (ip & 0xff) + "." + ((ip >> 8) & 0xff) + "."
                    + ((ip >> 16) & 0xff) + "." + ((ip >> 24) & 0xff);
        } catch (Exception e) {
            return null;
        }
    }
}
