package com.zysj.speaker.remote;

import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import android.util.Log;

public class HttpServer {
    private final Context context;
    private final MediaControl mediaControl;
    private final CursorOverlay cursorOverlay;
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private ExecutorService workers;
    private volatile boolean running;
    private byte[] pageCache;

    public HttpServer(Context context, MediaControl mediaControl, CursorOverlay cursorOverlay) {
        this.context = context.getApplicationContext();
        this.mediaControl = mediaControl;
        this.cursorOverlay = cursorOverlay;
    }

    public void start() {
        running = true;
        workers = Executors.newFixedThreadPool(4);
        acceptThread = new Thread(new Runnable() {
            @Override
            public void run() {
                acceptLoop();
            }
        }, "remote-http");
        acceptThread.start();
    }

    public void stop() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
        if (workers != null) {
            workers.shutdownNow();
        }
    }

    private void acceptLoop() {
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(RemoteService.PORT));
            Log.i("RemoteServer", "listening on " + RemoteService.PORT);
            while (running) {
                final Socket socket = serverSocket.accept();
                workers.execute(new Runnable() {
                    @Override
                    public void run() {
                        handle(socket);
                    }
                });
            }
        } catch (IOException e) {
            Log.e("RemoteServer", "http server stopped", e);
        }
    }

    private void handle(Socket socket) {
        try {
            socket.setSoTimeout(5000);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8"));
            String requestLine = reader.readLine();
            if (requestLine == null) {
                socket.close();
                return;
            }
            String[] parts = requestLine.split(" ");
            String path = parts.length > 1 ? parts[1] : "/";

            String line;
            int contentLength = 0;
            while ((line = reader.readLine()) != null && line.length() > 0) {
                String lower = line.toLowerCase();
                if (lower.startsWith("content-length:")) {
                    try {
                        contentLength = Integer.parseInt(line.substring(15).trim());
                    } catch (Exception ignored) {
                    }
                }
            }
            StringBuilder body = new StringBuilder();
            if (contentLength > 0 && contentLength < 65536) {
                char[] buffer = new char[contentLength];
                int read = 0;
                while (read < contentLength) {
                    int n = reader.read(buffer, read, contentLength - read);
                    if (n < 0) {
                        break;
                    }
                    read += n;
                }
                body.append(buffer, 0, read);
            }

            Map<String, String> params = new HashMap<String, String>();
            int queryIndex = path.indexOf('?');
            if (queryIndex >= 0) {
                parseParams(path.substring(queryIndex + 1), params);
                path = path.substring(0, queryIndex);
            }
            parseParams(body.toString(), params);

            if (path.equals("/") || path.equals("/index.html")) {
                respond(socket, "text/html; charset=utf-8", page());
            } else if (path.equals("/api/status")) {
                JSONObject status = mediaControl.status(context);
                status.put("inputHelper", rootHelperAlive());
                respond(socket, "application/json; charset=utf-8", status.toString());
            } else if (path.equals("/api/cmd")) {
                respond(socket, "application/json; charset=utf-8", runCommand(params));
            } else {
                respond(socket, "application/json; charset=utf-8",
                        "{\"ok\":false,\"error\":\"not found\"}");
            }
            socket.close();
        } catch (Exception ignored) {
            try {
                socket.close();
            } catch (Exception ignored2) {
            }
        }
    }

    private void parseParams(String query, Map<String, String> out) {
        if (query == null || query.length() == 0) {
            return;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String key;
            String value;
            if (eq >= 0) {
                key = pair.substring(0, eq);
                value = pair.substring(eq + 1);
            } else {
                key = pair;
                value = "";
            }
            try {
                out.put(URLDecoder.decode(key, "UTF-8"), URLDecoder.decode(value, "UTF-8"));
            } catch (Exception ignored) {
            }
        }
    }

    private int paramInt(Map<String, String> params, String key, int def) {
        try {
            String value = params.get(key);
            return value == null ? def : Integer.parseInt(value);
        } catch (Exception e) {
            return def;
        }
    }

    private double paramDouble(Map<String, String> params, String key, double def) {
        try {
            String value = params.get(key);
            return value == null ? def : Double.parseDouble(value);
        } catch (Exception e) {
            return def;
        }
    }

    private String runCommand(Map<String, String> params) {
        String cmd = params.get("cmd");
        String value = params.get("value");
        JSONObject result = new JSONObject();
        try {
            if (cmd == null) {
                result.put("ok", false);
                result.put("error", "missing cmd");
                return result.toString();
            }
            if (cmd.equals("toggle")) {
                mediaControl.toggle();
            } else if (cmd.equals("play")) {
                mediaControl.play();
            } else if (cmd.equals("pause")) {
                mediaControl.pause();
            } else if (cmd.equals("next")) {
                mediaControl.next();
            } else if (cmd.equals("prev")) {
                mediaControl.prev();
            } else if (cmd.equals("vol_up")) {
                mediaControl.volumeUp();
            } else if (cmd.equals("vol_down")) {
                mediaControl.volumeDown();
            } else if (cmd.equals("vol_set")) {
                try {
                    mediaControl.setVolumePercent(Integer.parseInt(value));
                } catch (Exception ignored) {
                }
            } else if (cmd.equals("launch")) {
                launchApp(value);
            } else if (cmd.equals("tap")) {
                int x = paramInt(params, "x", -1);
                int y = paramInt(params, "y", -1);
                if (x < 0 || y < 0) {
                    throw new Exception("missing x/y");
                }
                cursorOverlay.setPointer(x, y);
                if (!TouchAccessibilityService.tap(x, y)) {
                    throw new Exception("accessibility service not enabled");
                }
                cursorOverlay.flash();
            } else if (cmd.equals("swipe")) {
                int x1 = paramInt(params, "x1", -1);
                int y1 = paramInt(params, "y1", -1);
                int x2 = paramInt(params, "x2", -1);
                int y2 = paramInt(params, "y2", -1);
                long duration = paramInt(params, "duration", 300);
                if (x1 < 0 || y1 < 0 || x2 < 0 || y2 < 0) {
                    throw new Exception("missing swipe coordinates");
                }
                if (!TouchAccessibilityService.swipe(x1, y1, x2, y2, duration)) {
                    throw new Exception("accessibility service not enabled");
                }
                cursorOverlay.setPointer(x2, y2);
                cursorOverlay.flash();
            } else if (cmd.equals("pointer")) {
                double dx = paramDouble(params, "dx", 0);
                double dy = paramDouble(params, "dy", 0);
                if (!cursorOverlay.moveBy((float) dx, (float) dy)) {
                    throw new Exception("cursor overlay not enabled");
                }
            } else if (cmd.equals("click")) {
                int x = paramInt(params, "x", -1);
                int y = paramInt(params, "y", -1);
                if (x >= 0 && y >= 0) {
                    cursorOverlay.setPointer(x, y);
                }
                if (!cursorOverlay.clickAtCurrent()) {
                    if (!cursorOverlay.isAvailable()) {
                        throw new Exception("cursor overlay not enabled");
                    }
                    throw new Exception("accessibility service not enabled");
                }
            } else if (cmd.equals("scroll")) {
                String dir = params.get("dir");
                int count = paramInt(params, "count", 1);
                if (dir == null || !(dir.equals("up") || dir.equals("down")
                        || dir.equals("left") || dir.equals("right"))) {
                    throw new Exception("missing or invalid dir");
                }
                boolean any = false;
                int n = Math.max(1, Math.min(count, 10));
                for (int i = 0; i < n; i++) {
                    if (TouchAccessibilityService.scroll(dir)) {
                        any = true;
                        continue;
                    }
                    if (gestureScroll(dir)) {
                        any = true;
                        continue;
                    }
                    if (shellKey(dir)) {
                        any = true;
                    }
                }
                if (!any) {
                    throw new Exception("scroll failed");
                }
            } else if (cmd.equals("back")) {
                if (!TouchAccessibilityService.back()) {
                    throw new Exception("accessibility service not enabled");
                }
            } else if (cmd.equals("home")) {
                if (!TouchAccessibilityService.home()) {
                    throw new Exception("accessibility service not enabled");
                }
            } else if (cmd.equals("select")) {
                if (!TouchAccessibilityService.clickFocused()) {
                    throw new Exception("no focused clickable item");
                }
            } else if (cmd.equals("key")) {
                int code = parseKeyCode(params.get("code"));
                if (code <= 0) {
                    throw new Exception("invalid key code");
                }
                if (!rootHelperAlive()) {
                    throw new Exception("root input helper not running");
                }
                boolean longpress = "1".equals(params.get("longpress"))
                        || "true".equalsIgnoreCase(params.get("longpress"));
                sendRootInput(longpress
                        ? "keyevent --longpress " + code : "keyevent " + code);
            } else if (cmd.equals("nav")) {
                String key = params.get("key");
                if (key == null
                        || !(key.equals("power") || key.equals("recents")
                        || key.equals("home") || key.equals("back"))) {
                    throw new Exception("invalid nav key");
                }
                if (!rootHelperAlive()) {
                    throw new Exception("root input helper not running");
                }
                if (key.equals("power")) {
                    sendRootInput("keyevent --longpress 26");
                } else if (key.equals("home")) {
                    sendRootInput("tap 959 1044");
                } else if (key.equals("back")) {
                    sendRootInput("tap 764 1044");
                } else if (key.equals("recents")) {
                    openRecents();
                }
            } else if (cmd.equals("nav_dump")) {
                result.put("windows", TouchAccessibilityService.navDump());
            } else if (cmd.equals("clean")) {
                result.put("cleared", cleanBackground());
            } else if (cmd.equals("find_center")) {
                int[] center = TouchAccessibilityService.findTextCenter(params.get("text"));
                result.put("center", center == null ? "" : center[0] + "," + center[1]);
            } else if (cmd.equals("wake")) {
                wakeScreen();
            } else if (cmd.equals("sleep")) {
                if (!rootHelperAlive()) {
                    throw new Exception("root input helper not running");
                }
                sendRootInput("keyevent 26");
            } else if (cmd.equals("wall")) {
                launchWall();
            } else {
                result.put("ok", false);
                result.put("error", "unknown cmd: " + cmd);
                return result.toString();
            }
            result.put("ok", true);
            result.put("status", mediaControl.status(context));
        } catch (Exception e) {
            try {
                result.put("ok", false);
                result.put("error", e.toString());
            } catch (Exception ignored) {
            }
        }
        return result.toString();
    }

    private void launchApp(String value) {
        if (value == null) {
            return;
        }
        String pkg = value;
        if (value.equals("netease")) {
            pkg = "com.netease.cloudmusic";
        } else if (value.equals("zhiyue")) {
            pkg = "zhiyue.go.fmzonghe.hecheng";
        } else if (value.equals("bili")) {
            pkg = "tv.danmaku.bili";
        }
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(pkg);
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    private void launchWall() throws Exception {
        Intent intent = context.getPackageManager()
                .getLaunchIntentForPackage("com.zysj.standby");
        if (intent == null) {
            throw new Exception("standby app not installed");
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(intent);
    }

    private void wakeScreen() {
        Intent intent = new Intent(context, WakeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private boolean cleanBackground() throws Exception {
        if (!rootHelperAlive()) {
            throw new Exception("root input helper not running");
        }
        openRecents();
        boolean done = false;
        for (int i = 0; i < 8; i++) {
            int[] center = TouchAccessibilityService.findTextCenter("全部清除");
            if (center != null) {
                sendRootInput("tap " + center[0] + " " + center[1]);
                done = true;
                break;
            }
            sendRootInput("swipe 200 500 1700 500 400");
            Thread.sleep(500);
        }
        Thread.sleep(done ? 600 : 0);
        sendRootInput("keyevent 3");
        return done;
    }

    private void openRecents() throws Exception {
        if (!rootHelperAlive()) {
            throw new Exception("root input helper not running");
        }
        if (TouchAccessibilityService.hasText("全部清除")) {
            return;
        }
        if (!TouchAccessibilityService.navBarAccessible()) {
            sendRootInput("swipe 960 1070 960 900 250");
            Thread.sleep(700);
            if (!TouchAccessibilityService.navBarAccessible()) {
                sendRootInput("swipe 960 10 960 150 250");
                Thread.sleep(700);
            }
        }
        if (!TouchAccessibilityService.navBarAccessible()) {
            throw new Exception("navigation bar hidden");
        }
        sendRootInput("tap 1154 1044");
        Thread.sleep(1000);
        for (int i = 0; i < 4; i++) {
            if (TouchAccessibilityService.hasText("全部清除")) {
                return;
            }
            Thread.sleep(400);
        }
    }

    private boolean shellKey(String dir) {
        int keycode;
        if (dir.equals("up")) {
            keycode = 19;
        } else if (dir.equals("down")) {
            keycode = 20;
        } else if (dir.equals("left")) {
            keycode = 21;
        } else {
            keycode = 22;
        }
        try {
            sendRootInput("keyevent " + keycode);
            return rootHelperAlive();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean rootHelperAlive() {
        try {
            File alive = new File(context.getFilesDir(), "input_helper_alive");
            return alive.exists()
                    && System.currentTimeMillis() - alive.lastModified() < 3000;
        } catch (Exception e) {
            return false;
        }
    }

    private synchronized void sendRootInput(String args) throws Exception {
        File dir = context.getFilesDir();
        File tmp = new File(dir, "input_cmd.tmp");
        File cmdFile = new File(dir, "input_cmd");
        FileOutputStream out = new FileOutputStream(tmp);
        out.write(args.getBytes("UTF-8"));
        out.close();
        if (cmdFile.exists()) {
            cmdFile.delete();
        }
        if (!tmp.renameTo(cmdFile)) {
            throw new Exception("cannot queue root input");
        }
    }

    private int parseKeyCode(String name) {
        if (name == null) {
            return -1;
        }
        if (name.equals("up")) {
            return 19;
        }
        if (name.equals("down")) {
            return 20;
        }
        if (name.equals("left")) {
            return 21;
        }
        if (name.equals("right")) {
            return 22;
        }
        if (name.equals("ok") || name.equals("enter")) {
            return 66;
        }
        if (name.equals("back")) {
            return 4;
        }
        if (name.equals("home")) {
            return 3;
        }
        if (name.equals("recents")) {
            return 187;
        }
        if (name.equals("power")) {
            return 26;
        }
        try {
            return Integer.parseInt(name);
        } catch (Exception e) {
            return -1;
        }
    }

    private boolean gestureScroll(String dir) {
        if (dir.equals("down")) {
            return TouchAccessibilityService.swipe(960, 800, 960, 400, 200);
        }
        if (dir.equals("up")) {
            return TouchAccessibilityService.swipe(960, 400, 960, 800, 200);
        }
        if (dir.equals("left")) {
            return TouchAccessibilityService.swipe(400, 540, 800, 540, 200);
        }
        return TouchAccessibilityService.swipe(800, 540, 400, 540, 200);
    }

    private byte[] page() {
        if (pageCache == null) {
            try {
                InputStream in = context.getResources().openRawResource(R.raw.remote_html);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    out.write(buffer, 0, n);
                }
                in.close();
                pageCache = out.toByteArray();
            } catch (Exception e) {
                pageCache = "<html><body>page error</body></html>".getBytes();
            }
        }
        return pageCache;
    }

    private void respond(Socket socket, String contentType, byte[] body) throws IOException {
        OutputStream out = socket.getOutputStream();
        StringBuilder header = new StringBuilder();
        header.append("HTTP/1.1 200 OK\r\n");
        header.append("Content-Type: ").append(contentType).append("\r\n");
        header.append("Content-Length: ").append(body.length).append("\r\n");
        header.append("Connection: close\r\n\r\n");
        out.write(header.toString().getBytes("UTF-8"));
        out.write(body);
        out.flush();
    }

    private void respond(Socket socket, String contentType, String body) throws IOException {
        respond(socket, contentType, body.getBytes("UTF-8"));
    }
}
