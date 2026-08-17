package com.zysj.speaker.remotecontrol;

import android.content.Context;
import android.net.wifi.WifiManager;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Scan {
    public static final int PORT = 8080;

    public interface Listener {
        void onFound(String ip, String name);
    }

    private final Context context;
    private final Listener listener;
    private ExecutorService pool;
    private volatile boolean found;

    public Scan(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void start() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                scan();
            }
        }, "remote-scan");
        thread.start();
    }

    public void stop() {
        found = true;
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    private void scan() {
        try {
            WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            int ip = wifi.getConnectionInfo().getIpAddress();
            if (ip == 0) {
                return;
            }
            int network = ip & 0x00FFFFFF;
            List<String> candidates = new ArrayList<String>();
            for (int host = 1; host < 255; host++) {
                int candidate = network | (host << 24);
                if (candidate == ip) {
                    continue;
                }
                candidates.add(ipString(candidate));
            }
            List<String> targets = new ArrayList<String>();
            for (String candidate : candidates) {
                if (found) {
                    break;
                }
                targets.add(candidate);
            }
            pool = Executors.newFixedThreadPool(16);
            final CountDownLatch latch = new CountDownLatch(targets.size());
            for (final String target : targets) {
                pool.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            probe(target);
                        } finally {
                            latch.countDown();
                        }
                    }
                });
            }
            latch.await(15, TimeUnit.SECONDS);
            found = true;
            pool.shutdownNow();
        } catch (Exception ignored) {
            if (pool != null) {
                pool.shutdownNow();
            }
        }
    }

    private void probe(String ip) {
        if (found) {
            return;
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(
                    "http://" + ip + ":" + PORT + "/api/status").openConnection();
            connection.setConnectTimeout(700);
            connection.setReadTimeout(700);
            connection.setRequestMethod("GET");
            connection.setUseCaches(false);
            int code = connection.getResponseCode();
            if (code == 200) {
                InputStream in = connection.getInputStream();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    out.write(buffer, 0, n);
                }
                in.close();
                JSONObject json = new JSONObject(out.toString("UTF-8"));
                if (json.optBoolean("ok", false)) {
                    String name = json.optString("name", "");
                    if (name.length() > 0) {
                        found = true;
                        listener.onFound(ip, name);
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String ipString(int ip) {
        return (ip & 0xff) + "." + ((ip >> 8) & 0xff) + "."
                + ((ip >> 16) & 0xff) + "." + ((ip >> 24) & 0xff);
    }
}
