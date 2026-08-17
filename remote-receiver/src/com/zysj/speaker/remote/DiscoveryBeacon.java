package com.zysj.speaker.remote;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class DiscoveryBeacon {
    public static final int DISCOVERY_PORT = 8177;

    private final Context context;
    private Thread thread;
    private volatile boolean running;
    private DatagramSocket socket;

    public DiscoveryBeacon(Context context) {
        this.context = context.getApplicationContext();
    }

    public void start() {
        running = true;
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                loop();
            }
        }, "remote-beacon");
        thread.start();
    }

    public void stop() {
        running = false;
        if (socket != null) {
            socket.close();
        }
    }

    private void loop() {
        try {
            socket = new DatagramSocket();
            socket.setBroadcast(true);
            String ip = NetInfo.wifiIp(context);
            String payload = "SPEAKER_REMOTE|" + (ip == null ? "" : ip)
                    + "|" + RemoteService.PORT + "|壁画音响";
            byte[] bytes = payload.getBytes("UTF-8");
            String subnet = subnetBroadcast();
            while (running) {
                if (subnet != null && !subnet.equals("255.255.255.255")) {
                    send(subnet, bytes);
                }
                send("255.255.255.255", bytes);
                Thread.sleep(3000);
            }
        } catch (Exception ignored) {
        }
    }

    private void send(String destination, byte[] bytes) {
        try {
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length,
                    InetAddress.getByName(destination), DISCOVERY_PORT);
            socket.send(packet);
        } catch (Exception ignored) {
        }
    }

    private String subnetBroadcast() {
        try {
            WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            DhcpInfo dhcp = wifi.getDhcpInfo();
            int ip = dhcp.ipAddress;
            int mask = dhcp.netmask;
            if (ip == 0 || mask == 0) {
                return null;
            }
            int broadcast = (ip & mask) | (~mask);
            return (broadcast & 0xff) + "." + ((broadcast >> 8) & 0xff) + "."
                    + ((broadcast >> 16) & 0xff) + "." + ((broadcast >> 24) & 0xff);
        } catch (Exception e) {
            return null;
        }
    }
}
