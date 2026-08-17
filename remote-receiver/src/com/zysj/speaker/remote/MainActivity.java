package com.zysj.speaker.remote;

import android.app.Activity;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public class MainActivity extends Activity {
    private TextView statusView;
    private Button permissionButton;
    private final Handler handler = new Handler();
    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
            updateStatus();
            handler.postDelayed(this, 3000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startService(new Intent(this, RemoteService.class));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(24);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("遥控接收端");
        title.setTextSize(24);

        statusView = new TextView(this);
        statusView.setTextSize(16);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(0, dp(16), 0, dp(24));

        permissionButton = new Button(this);
        permissionButton.setText("授权悬浮窗以显示光标");
        permissionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        });

        Button openButton = new Button(this);
        openButton.setText("打开遥控页面");
        openButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String ip = NetInfo.wifiIp(MainActivity.this);
                if (ip == null) {
                    statusView.setText("未获取到 WiFi IP，请确认已联网");
                    return;
                }
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("http://" + ip + ":" + RemoteService.PORT + "/")));
            }
        });

        root.addView(title);
        root.addView(statusView);
        root.addView(permissionButton);
        root.addView(openButton);
        setContentView(root);
        updateStatus();
        handler.postDelayed(refresher, 3000);
    }

    private void updateStatus() {
        String ip = NetInfo.wifiIp(this);
        String text;
        if (ip == null) {
            text = "未获取到 WiFi IP，请确认已联网";
        } else {
            text = "服务地址\nhttp://" + ip + ":" + RemoteService.PORT + "/\n\n"
                    + "光标：" + (canDrawOverlay() ? "已显示" : "未授权") + "\n"
                    + "触控注入：" + (isTouchReady() ? "可用" : "未开启无障碍服务") + "\n\n"
                    + "手机装“音响遥控”App，或直接用浏览器打开上面的地址。";
        }
        statusView.setText(text);
        permissionButton.setVisibility(canDrawOverlay() ? View.GONE : View.VISIBLE);
    }

    private boolean canDrawOverlay() {
        return android.os.Build.VERSION.SDK_INT < 23
                || Settings.canDrawOverlays(this);
    }

    private boolean isTouchReady() {
        AccessibilityManager manager =
                (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager == null) {
            return false;
        }
        List<AccessibilityServiceInfo> enabled =
                manager.getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo info : enabled) {
            if (info.getResolveInfo() != null
                    && info.getResolveInfo().serviceInfo != null
                    && getPackageName().equals(info.getResolveInfo().serviceInfo.packageName)
                    && TouchAccessibilityService.class.getName()
                    .equals(info.getResolveInfo().serviceInfo.name)) {
                return true;
            }
        }
        return false;
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(refresher);
        super.onDestroy();
    }
}
