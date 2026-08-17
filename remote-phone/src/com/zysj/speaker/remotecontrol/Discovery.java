package com.zysj.speaker.remotecontrol;

import android.content.Context;
import android.net.wifi.WifiManager;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class Discovery {
    public static final int PORT = 8177;

    public interface Listener {
        void onFound(String ip, String name);
    }

    private final Context context;
    private final Listener listener;
    private Thread thread;
    private volatile boolean running;
    private WifiManager.MulticastLock lock;

    public Discovery(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void start() {
        running = true;
        try {
            WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            lock = wifi.createMulticastLock("remote-discovery");
            lock.acquire();
        } catch (Exception ignored) {
        }
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                loop();
            }
        }, "remote-discovery");
        thread.start();
    }

    public void stop() {
        running = false;
        if (lock != null && lock.isHeld()) {
            try {
                lock.release();
            } catch (Exception ignored) {
            }
        }
    }

    private void loop() {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket(PORT);
            socket.setSoTimeout(2000);
            byte[] buffer = new byte[1024];
            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String text = new String(packet.getData(), 0, packet.getLength(), "UTF-8");
                    if (text.startsWith("SPEAKER_REMOTE|")) {
                        String[] parts = text.split("\\|");
                        if (parts.length >= 4 && parts[1].length() > 0) {
                            listener.onFound(parts[1], parts[3]);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
