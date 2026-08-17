package com.zysj.speaker.remotecontrol;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String PREFS = "remote_prefs";
    private static final String KEY_HOST = "host";
    private static final int PORT = 8080;

    private EditText hostInput;
    private TextView statusView;
    private Button playButton;
    private Button padModeButton;
    private TouchPadView touchPad;
    private SharedPreferences prefs;
    private final Handler handler = new Handler();
    private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor();
    private volatile String host;
    private volatile boolean connected;
    private Discovery discovery;
    private volatile boolean playing;
    private Scan scan;
    private boolean cursorMode = true;
    private final Runnable startScanTask = new Runnable() {
        @Override
        public void run() {
            if (!connected && scan != null) {
                scan.start();
            }
        }
    };

    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 3000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        setContentView(buildUi());
        host = prefs.getString(KEY_HOST, "");
        if (host.length() > 0) {
            hostInput.setText(host);
        }

        discovery = new Discovery(this, new Discovery.Listener() {
            @Override
            public void onFound(final String ip, final String name) {
                onDeviceFound(ip, name);
            }
        });
        discovery.start();

        scan = new Scan(this, new Scan.Listener() {
            @Override
            public void onFound(final String ip, final String name) {
                onDeviceFound(ip, name);
            }
        });
        handler.postDelayed(startScanTask, 2000);

        handler.post(pollTask);
        if (host.length() > 0) {
            connect();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(pollTask);
        if (touchPad != null) {
            touchPad.release();
        }
        if (discovery != null) {
            discovery.stop();
        }
        if (scan != null) {
            scan.stop();
        }
        handler.removeCallbacks(startScanTask);
        commandExecutor.shutdownNow();
        super.onDestroy();
    }

    private void onDeviceFound(final String ip, final String name) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!connected) {
                    hostInput.setText(ip);
                    host = ip;
                    saveHost();
                    connect();
                }
            }
        });
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("音响遥控");
        title.setTextSize(26);
        root.addView(title);

        LinearLayout hostRow = new LinearLayout(this);
        hostRow.setOrientation(LinearLayout.HORIZONTAL);
        hostRow.setPadding(0, dp(16), 0, 0);

        hostInput = new EditText(this);
        hostInput.setHint("接收端 IP，如 192.168.1.66");
        hostInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        hostInput.setSingleLine(true);
        hostInput.setSelectAllOnFocus(true);
        hostInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        hostInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    host = hostInput.getText().toString().trim();
                    saveHost();
                    connect();
                    hideKeyboard();
                    return true;
                }
                return false;
            }
        });
        hostRow.addView(hostInput,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button connectButton = new Button(this);
        connectButton.setText("连接");
        hostRow.addView(connectButton,
                new LinearLayout.LayoutParams(dp(96), ViewGroup.LayoutParams.WRAP_CONTENT));
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host = hostInput.getText().toString().trim();
                saveHost();
                hideKeyboard();
                connect();
            }
        });
        root.addView(hostRow);

        statusView = new TextView(this);
        statusView.setText("未连接\n自动发现设备中…");
        statusView.setTextSize(15);
        statusView.setPadding(0, dp(12), 0, dp(12));
        root.addView(statusView);

        root.addView(buttonRow(new String[]{"上一首", "播放/暂停", "下一首"},
                new String[]{"prev", "toggle", "next"}));
        root.addView(buttonRow(new String[]{"音量-", "亮屏", "进入壁画", "音量+"},
                new String[]{"vol_down", "wake", "wall", "vol_up"}));
        root.addView(buttonRow(new String[]{"网易云", "知悦", "哔哩"},
                new String[]{"netease", "zhiyue", "bili"}));
        root.addView(navRow(new String[]{"返回", "主页", "后台", "电源", "清理"},
                new String[]{"back", "home", "recents", "power", "clean"}));
        root.addView(keyRow(new String[]{"上", "下", "左", "右", "OK"},
                new String[]{"up", "down", "left", "right", "ok"}));

        LinearLayout padHeader = new LinearLayout(this);
        padHeader.setOrientation(LinearLayout.HORIZONTAL);
        padHeader.setPadding(0, dp(10), 0, 0);

        TextView padLabel = new TextView(this);
        padLabel.setText("触控板");
        padLabel.setTextSize(14);
        padLabel.setGravity(Gravity.CENTER_VERTICAL);
        padHeader.addView(padLabel,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        padModeButton = new Button(this);
        padModeButton.setText("光标");
        padModeButton.setTextSize(14);
        padModeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePadMode();
            }
        });
        padHeader.addView(padModeButton,
                new LinearLayout.LayoutParams(dp(88), dp(44)));

        Button fullscreenButton = new Button(this);
        fullscreenButton.setText("全屏");
        fullscreenButton.setTextSize(14);
        LinearLayout.LayoutParams fullscreenParams =
                new LinearLayout.LayoutParams(dp(72), dp(44));
        fullscreenParams.leftMargin = dp(8);
        fullscreenButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this,
                        FullscreenTouchpadActivity.class);
                if (host != null) {
                    intent.putExtra("host", host);
                }
                startActivity(intent);
            }
        });
        padHeader.addView(fullscreenButton, fullscreenParams);
        root.addView(padHeader);

        touchPad = new TouchPadView(this, new TouchPadView.CommandSender() {
            @Override
            public void send(String query, boolean quiet) {
                sendCommandQuery(query, quiet);
            }
        });
        LinearLayout.LayoutParams padParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(170));
        padParams.topMargin = dp(6);
        root.addView(touchPad, padParams);

        LinearLayout scrollRow = new LinearLayout(this);
        scrollRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams scrollRowParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        scrollRowParams.topMargin = dp(6);

        Button scrollUpButton = new Button(this);
        scrollUpButton.setText("上滚");
        scrollUpButton.setTextSize(15);
        scrollUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendCommandQuery("cmd=scroll&dir=up&count=1");
            }
        });
        scrollRow.addView(scrollUpButton,
                new LinearLayout.LayoutParams(0, dp(44), 1f));

        Button scrollDownButton = new Button(this);
        scrollDownButton.setText("下滚");
        scrollDownButton.setTextSize(15);
        LinearLayout.LayoutParams downParams =
                new LinearLayout.LayoutParams(0, dp(44), 1f);
        downParams.leftMargin = dp(8);
        scrollDownButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendCommandQuery("cmd=scroll&dir=down&count=1");
            }
        });
        scrollRow.addView(scrollDownButton, downParams);

        root.addView(scrollRow, scrollRowParams);
        return scroll;
    }

    private void togglePadMode() {
        cursorMode = !cursorMode;
        padModeButton.setText(cursorMode ? "光标" : "绝对");
        touchPad.setCursorMode(cursorMode);
    }

    private View buttonRow(final String[] labels, final String[] commands) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            Button button = new Button(this);
            button.setText(labels[i]);
            button.setTextSize(16);
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(0, dp(48), 1f);
            if (i > 0) {
                params.leftMargin = dp(8);
            }
            row.addView(button, params);
            if (labels[i].equals("播放/暂停")) {
                playButton = button;
            }
            final String command = commands[i];
            if (command.equals("toggle")) {
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        sendCommand(playing ? "pause" : "play", null);
                        playing = !playing;
                        playButton.setText(playing ? "暂停" : "播放");
                    }
                });
            } else {
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (command.equals("netease") || command.equals("zhiyue")
                                || command.equals("bili")) {
                            sendCommand("launch", command);
                        } else {
                            sendCommand(command, null);
                        }
                    }
                });
            }
        }
        return row;
    }

    private View keyRow(final String[] labels, final String[] codes) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, 0);
        for (int i = 0; i < labels.length; i++) {
            Button button = new Button(this);
            button.setText(labels[i]);
            button.setTextSize(15);
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(0, dp(44), 1f);
            if (i > 0) {
                params.leftMargin = dp(6);
            }
            row.addView(button, params);
            final String code = codes[i];
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sendCommandQuery("cmd=key&code=" + code);
                }
            });
        }
        return row;
    }

    private View navRow(final String[] labels, final String[] keys) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, 0);
        for (int i = 0; i < labels.length; i++) {
            Button button = new Button(this);
            button.setText(labels[i]);
            button.setTextSize(15);
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(0, dp(44), 1f);
            if (i > 0) {
                params.leftMargin = dp(6);
            }
            row.addView(button, params);
            final String key = keys[i];
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (key.equals("clean")) {
                        sendCommandQuery("cmd=clean");
                    } else {
                        sendCommandQuery("cmd=nav&key=" + key);
                    }
                }
            });
        }
        return row;
    }

    private void connect() {
        if (host == null || host.length() == 0) {
            setStatus("请输入接收端 IP");
            return;
        }
        refresh();
    }

    private void refresh() {
        if (host == null || host.length() == 0) {
            return;
        }
        final String target = host;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final String body = Http.get("http://" + target + ":" + PORT + "/api/status");
                    final JSONObject json = new JSONObject(body);
                    final boolean ok = json.optBoolean("ok", false);
                    if (!ok) {
                        throw new Exception(json.optString("error", "error"));
                    }
                    final String title = json.optString("title", "");
                    final String artist = json.optString("artist", "");
                    final String app = json.optString("app", "");
                    final boolean isPlaying = json.optBoolean("playing", false);
                    final int volume = json.optInt("volume", -1);
                    final int maxVolume = json.optInt("maxVolume", -1);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            connected = true;
                            playing = isPlaying;
                            StringBuilder text = new StringBuilder();
                            if (app.length() > 0) {
                                text.append(appLabel(app));
                                text.append(" · ").append(isPlaying ? "播放中" : "已暂停");
                            } else {
                                text.append("无播放会话");
                            }
                            if (volume >= 0) {
                                text.append(" · 音量 ").append(volume).append("/").append(maxVolume);
                            }
                            if (title.length() > 0) {
                                text.append("\n").append(title);
                                if (artist.length() > 0) {
                                    text.append(" - ").append(artist);
                                }
                            }
                            statusView.setText(text.toString());
                            playButton.setText(isPlaying ? "暂停" : "播放");
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            connected = false;
                            statusView.setText("无法连接 " + target
                                    + "\n请确认手机与板子在同一 WiFi");
                        }
                    });
                }
            }
        }).start();
    }

    private void sendCommand(final String cmd, final String value) {
        StringBuilder query = new StringBuilder("cmd=").append(cmd);
        if (value != null) {
            query.append("&value=").append(value);
        }
        sendCommandQuery(query.toString());
    }

    private void sendCommandQuery(final String query) {
        sendCommandQuery(query, false);
    }

    private void sendCommandQuery(final String query, final boolean quiet) {
        if (host == null || host.length() == 0) {
            setStatus("请先输入接收端 IP");
            return;
        }
        final String target = host;
        commandExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String body = Http.get("http://" + target + ":" + PORT
                            + "/api/cmd?" + query);
                    JSONObject json = new JSONObject(body);
                    if (!json.optBoolean("ok", false)) {
                        if (!quiet) {
                            setStatus(json.optString("error", "命令执行失败"));
                        }
                    } else {
                        refresh();
                    }
                } catch (final Exception e) {
                    if (!quiet) {
                        setStatus("命令发送失败");
                    }
                }
            }
        });
    }

    private void setStatus(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusView.setText(text);
            }
        });
    }

    private String appLabel(String pkg) {
        if (pkg.equals("com.netease.cloudmusic")) {
            return "网易云音乐";
        }
        if (pkg.equals("zhiyue.go.fmzonghe.hecheng")) {
            return "知悦TV";
        }
        if (pkg.equals("tv.danmaku.bili")) {
            return "哔哩哔哩";
        }
        if (pkg.equals("com.android.music")) {
            return "本地音乐";
        }
        return pkg;
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(hostInput.getWindowToken(), 0);
        }
        hostInput.clearFocus();
    }

    private void saveHost() {
        prefs.edit().putString(KEY_HOST, host).apply();
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}
