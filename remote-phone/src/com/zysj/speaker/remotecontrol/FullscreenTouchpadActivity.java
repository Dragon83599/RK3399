package com.zysj.speaker.remotecontrol;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FullscreenTouchpadActivity extends Activity {
    private static final String PREFS = "remote_prefs";
    private static final String KEY_HOST = "host";
    private static final int PORT = 8080;

    private String host;
    private boolean cursorMode = true;
    private TouchPadView touchPad;
    private Button modeButton;
    private TextView statusView;
    private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        host = getIntent().getStringExtra("host");
        if (host == null || host.length() == 0) {
            host = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_HOST, "");
        }
        setContentView(buildUi());
        hideSystemUi();
    }

    private void hideSystemUi() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF11161B);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(12), dp(12), dp(12), dp(8));
        bar.setGravity(Gravity.CENTER_VERTICAL);

        Button backButton = new Button(this);
        backButton.setText("返回");
        backButton.setTextSize(15);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        bar.addView(backButton,
                new LinearLayout.LayoutParams(dp(80), dp(44)));

        modeButton = new Button(this);
        modeButton.setText("光标");
        modeButton.setTextSize(15);
        LinearLayout.LayoutParams modeParams =
                new LinearLayout.LayoutParams(dp(88), dp(44));
        modeParams.leftMargin = dp(10);
        modeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMode();
            }
        });
        bar.addView(modeButton, modeParams);

        Button scrollUpButton = new Button(this);
        scrollUpButton.setText("上滚");
        scrollUpButton.setTextSize(14);
        LinearLayout.LayoutParams upParams =
                new LinearLayout.LayoutParams(dp(64), dp(44));
        upParams.leftMargin = dp(8);
        scrollUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendQuery("cmd=scroll&dir=up&count=1", false);
            }
        });
        bar.addView(scrollUpButton, upParams);

        Button scrollDownButton = new Button(this);
        scrollDownButton.setText("下滚");
        scrollDownButton.setTextSize(14);
        LinearLayout.LayoutParams downParams =
                new LinearLayout.LayoutParams(dp(64), dp(44));
        downParams.leftMargin = dp(8);
        scrollDownButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendQuery("cmd=scroll&dir=down&count=1", false);
            }
        });
        bar.addView(scrollDownButton, downParams);

        statusView = new TextView(this);
        statusView.setText(host == null || host.length() == 0 ? "未设置 IP" : host);
        statusView.setTextColor(0xFF9AA0A6);
        statusView.setTextSize(13);
        statusView.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        statusView.setPadding(dp(10), 0, 0, 0);
        bar.addView(statusView,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        root.addView(bar,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        touchPad = new TouchPadView(this, new TouchPadView.CommandSender() {
            @Override
            public void send(String query, boolean quiet) {
                sendQuery(query, quiet);
            }
        });
        root.addView(touchPad,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(keyRow(new String[]{"上", "下", "左", "右", "OK"},
                new String[]{"up", "down", "left", "right", "ok"}));
        root.addView(navRow(new String[]{"返回", "主页", "后台", "电源", "清理"},
                new String[]{"back", "home", "recents", "power", "clean"}));
        root.addView(powerRow());
        return root;
    }

    private View powerRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(12), dp(6), dp(12), dp(8));
        Button wake = new Button(this);
        wake.setText("亮屏");
        wake.setTextSize(14);
        wake.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendQuery("cmd=wake", false);
            }
        });
        row.addView(wake,
                new LinearLayout.LayoutParams(0, dp(42), 1f));
        Button wall = new Button(this);
        wall.setText("进入壁画");
        wall.setTextSize(14);
        LinearLayout.LayoutParams wallParams =
                new LinearLayout.LayoutParams(0, dp(42), 1f);
        wallParams.leftMargin = dp(6);
        wall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendQuery("cmd=wall", false);
            }
        });
        row.addView(wall, wallParams);
        return row;
    }

    private View keyRow(final String[] labels, final String[] codes) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(12), dp(6), dp(12), 0);
        for (int i = 0; i < labels.length; i++) {
            Button button = new Button(this);
            button.setText(labels[i]);
            button.setTextSize(14);
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(0, dp(42), 1f);
            if (i > 0) {
                params.leftMargin = dp(6);
            }
            row.addView(button, params);
            final String code = codes[i];
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sendQuery("cmd=key&code=" + code, false);
                }
            });
        }
        return row;
    }

    private View navRow(final String[] labels, final String[] keys) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(12), dp(6), dp(12), 0);
        for (int i = 0; i < labels.length; i++) {
            Button button = new Button(this);
            button.setText(labels[i]);
            button.setTextSize(14);
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(0, dp(42), 1f);
            if (i > 0) {
                params.leftMargin = dp(6);
            }
            row.addView(button, params);
            final String key = keys[i];
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (key.equals("clean")) {
                        sendQuery("cmd=clean", false);
                    } else {
                        sendQuery("cmd=nav&key=" + key, false);
                    }
                }
            });
        }
        return row;
    }

    private void toggleMode() {
        cursorMode = !cursorMode;
        modeButton.setText(cursorMode ? "光标" : "绝对");
        touchPad.setCursorMode(cursorMode);
    }

    private void sendQuery(final String query, final boolean quiet) {
        if (host == null || host.length() == 0) {
            setStatus("未设置接收端 IP", quiet);
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
                            setStatus(json.optString("error", "命令执行失败"), false);
                        }
                    }
                } catch (final Exception e) {
                    if (!quiet) {
                        setStatus("命令发送失败", false);
                    }
                }
            }
        });
    }

    private void setStatus(final String text, final boolean quiet) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (statusView != null) {
                    statusView.setText(text);
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (touchPad != null) {
            touchPad.release();
        }
        commandExecutor.shutdownNow();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}
