package com.zysj.speaker.remotecontrol;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.SeekBar;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int BG = 0xFF0D1117;
    private static final int PANEL = 0xFF161B22;
    private static final int PANEL2 = 0xFF1C232C;
    private static final int BORDER = 0xFF2A323D;
    private static final int TEXT = 0xFFE6EDF3;
    private static final int MUTED = 0xFF8B949E;
    private static final int ACCENT = 0xFF2DD4BF;

    private static final String PREFS = "remote_prefs";
    private static final String KEY_HOST = "host";
    private static final int PORT = 8080;

    private EditText hostInput;
    private TextView statusView;
    private Button playButton;
    private Button padModeButton;
    private TouchPadView touchPad;
    private SeekBar volumeLimitBar;
    private TextView volumeLimitText;
    private SharedPreferences prefs;
    private final Handler handler = new Handler();
    private final ExecutorService commandExecutor = Executors.newCachedThreadPool();
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
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        root.setPadding(pad, dp(12), pad, dp(28));
        root.setBackgroundColor(BG);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("音响遥控");
        title.setTextSize(24);
        title.setTextColor(TEXT);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("壁画音响控制面板");
        subtitle.setTextSize(13);
        subtitle.setTextColor(MUTED);
        root.addView(subtitle);

        LinearLayout hostRow = new LinearLayout(this);
        hostRow.setOrientation(LinearLayout.HORIZONTAL);
        hostRow.setPadding(0, dp(14), 0, 0);

        hostInput = new EditText(this);
        hostInput.setHint("接收端 IP，如 192.168.1.66");
        hostInput.setHintTextColor(0xFF6B7280);
        hostInput.setTextColor(TEXT);
        hostInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        hostInput.setSingleLine(true);
        hostInput.setSelectAllOnFocus(true);
        hostInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        hostInput.setPadding(dp(10), 0, dp(10), 0);
        hostInput.setBackground(rounded(PANEL, BORDER));
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
                new LinearLayout.LayoutParams(0, dp(42), 1f));

        Button connectButton = styledButton("连接", 42);
        LinearLayout.LayoutParams connParams = new LinearLayout.LayoutParams(dp(88), dp(42));
        connParams.leftMargin = dp(8);
        hostRow.addView(connectButton, connParams);
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
        statusView.setTextSize(14);
        statusView.setTextColor(MUTED);
        statusView.setLineSpacing(0f, 1.2f);
        statusView.setPadding(0, dp(12), 0, dp(6));
        root.addView(statusView);

        root.addView(sectionLabel("播放控制"));
        root.addView(buttonRow(new String[]{"上一首", "播放/暂停", "下一首"},
                new String[]{"prev", "toggle", "next"}));

        root.addView(sectionLabel("音量与显示"));
        root.addView(buttonRow(new String[]{"音量-", "亮屏", "壁画", "音量+"},
                new String[]{"vol_down", "wake", "wall", "vol_up"}));

        LinearLayout limitRow = new LinearLayout(this);
        limitRow.setOrientation(LinearLayout.HORIZONTAL);
        limitRow.setPadding(0, dp(10), 0, 0);
        TextView limitLabel = new TextView(this);
        limitLabel.setText("音量上限");
        limitLabel.setTextSize(13);
        limitLabel.setTextColor(MUTED);
        limitLabel.setGravity(Gravity.CENTER_VERTICAL);
        volumeLimitText = new TextView(this);
        volumeLimitText.setText("80%");
        volumeLimitText.setTextSize(13);
        volumeLimitText.setTextColor(TEXT);
        volumeLimitText.setGravity(Gravity.CENTER_VERTICAL);
        volumeLimitText.setPadding(dp(8), 0, 0, 0);
        volumeLimitBar = new SeekBar(this);
        volumeLimitBar.setMax(90);
        volumeLimitBar.setProgress(70);
        volumeLimitBar.setProgressTintList(ColorStateList.valueOf(ACCENT));
        volumeLimitBar.setThumbTintList(ColorStateList.valueOf(ACCENT));
        volumeLimitBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (volumeLimitText != null) {
                    volumeLimitText.setText((progress + 10) + "%");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                sendCommandQuery("cmd=vol_limit&value=" + (seekBar.getProgress() + 10));
            }
        });
        limitRow.addView(limitLabel,
                new LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT));
        limitRow.addView(volumeLimitBar,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        limitRow.addView(volumeLimitText,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(limitRow);

        root.addView(sectionLabel("应用"));
        root.addView(buttonRow(new String[]{"网易云", "知悦", "哔哩"},
                new String[]{"netease", "zhiyue", "bili"}));

        root.addView(sectionLabel("系统键"));
        root.addView(navRow(new String[]{"返回", "主页", "后台", "清理", "关机", "重启"},
                new String[]{"back", "home", "recents", "clean", "poweroff", "reboot"}));

        root.addView(sectionLabel("方向键"));
        root.addView(keyRow(new String[]{"上", "下", "左", "右", "OK"},
                new String[]{"up", "down", "left", "right", "ok"}));

        LinearLayout padHeader = new LinearLayout(this);
        padHeader.setOrientation(LinearLayout.HORIZONTAL);
        padHeader.setPadding(0, dp(14), 0, dp(6));

        TextView padLabel = new TextView(this);
        padLabel.setText("触控板");
        padLabel.setTextSize(14);
        padLabel.setTextColor(MUTED);
        padLabel.setGravity(Gravity.CENTER_VERTICAL);
        padHeader.addView(padLabel,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        padModeButton = styledButton("光标", 40);
        padModeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePadMode();
            }
        });
        padHeader.addView(padModeButton,
                new LinearLayout.LayoutParams(dp(88), dp(40)));

        Button fullscreenButton = styledButton("全屏", 40);
        LinearLayout.LayoutParams fullscreenParams =
                new LinearLayout.LayoutParams(dp(72), dp(40));
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
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180));
        padParams.topMargin = dp(2);
        touchPad.setBackground(rounded(0xFF0B0F14, BORDER));
        root.addView(touchPad, padParams);

        LinearLayout scrollRow = new LinearLayout(this);
        scrollRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams scrollRowParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        scrollRowParams.topMargin = dp(8);

        Button scrollUpButton = styledButton("上滚", 44);
        scrollUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendCommandQuery("cmd=scroll&dir=up&count=1");
            }
        });
        scrollRow.addView(scrollUpButton,
                new LinearLayout.LayoutParams(0, dp(44), 1f));

        Button scrollDownButton = styledButton("下滚", 44);
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
            Button button = styledButton(labels[i], 48);
            if (labels.length >= 6) {
                button.setTextSize(13);
            }
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(0, dp(48), 1f);
            if (i > 0) {
                params.leftMargin = dp(8);
            }
            row.addView(button, params);
            if (labels[i].equals("播放/暂停")) {
                playButton = button;
                button.setBackground(rounded(0xFF0F766E, 0xFF14B8A6));
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
            Button button = styledButton(labels[i], 48);
            button.setTextSize(16);
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(0, dp(48), 1f);
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
            Button button = styledButton(labels[i], 46);
            button.setTextSize(13);
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(0, dp(46), 1f);
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
                    } else if (key.equals("poweroff") || key.equals("reboot")) {
                        sendCommandQuery("cmd=" + key);
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
                    final int volumeLimit = Math.max(10,
                            Math.min(100, json.optInt("volumeLimit", 80)));
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
                            if (volumeLimitBar != null) {
                                volumeLimitBar.setProgress(volumeLimit - 10);
                            }
                            if (volumeLimitText != null) {
                                volumeLimitText.setText(volumeLimit + "%");
                            }
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

    private GradientDrawable rounded(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setStroke(dp(1), stroke);
        drawable.setCornerRadius(dp(8));
        return drawable;
    }

    private Button styledButton(String label, int heightDp) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setTextColor(TEXT);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(0, 0, 0, 0);
        button.setGravity(Gravity.CENTER);
        button.setBackground(rounded(PANEL, BORDER));
        button.setHeight(dp(heightDp));
        return button;
    }

    private TextView sectionLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(12);
        label.setTextColor(MUTED);
        label.setPadding(0, dp(14), 0, dp(6));
        return label;
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
