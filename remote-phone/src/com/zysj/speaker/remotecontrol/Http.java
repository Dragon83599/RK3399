package com.zysj.speaker.remotecontrol;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class Http {
    private Http() {
    }

    public static String get(String urlString) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        connection.setRequestMethod("GET");
        connection.setUseCaches(false);
        int code = connection.getResponseCode();
        InputStream in = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
        }
        in.close();
        connection.disconnect();
        return out.toString("UTF-8");
    }
}
