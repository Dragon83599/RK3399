package com.zysj.standby;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SettingsActivity extends Activity {
    private static final int REQUEST_READ_STORAGE = 1;

    private List<File> allImages = new ArrayList<File>();
    private Set<String> selectedPaths = new HashSet<String>();
    private List<CheckBox> checkBoxes = new ArrayList<CheckBox>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.READ_EXTERNAL_STORAGE },
                    REQUEST_READ_STORAGE);
            return;
        }
        setContentView(createContent());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_READ_STORAGE) {
            setContentView(createContent());
        }
    }

    private View createContent() {
        allImages = SongImages.find(this);
        selectedPaths = new HashSet<String>(PlaybackPrefs.getSelectedPaths(this));
        if (!PlaybackPrefs.hasSelection(this)) {
            for (File file : allImages) {
                selectedPaths.add(file.getAbsolutePath());
            }
            PlaybackPrefs.setSelectedPaths(this, selectedPaths);
        }

        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(16);
        root.setPadding(padding, padding + statusBarHeight(), padding, padding);
        scrollView.addView(root);

        final long[] idleValues = new long[] {
                15000L, 30000L, 60000L, 120000L, 300000L, 600000L, 1800000L, 3600000L
        };
        Spinner idleSpinner = addSpinner(root, "进入屏保等待时间",
                new String[] { "15秒", "30秒", "1分钟", "2分钟", "5分钟", "10分钟", "30分钟", "60分钟" },
                idleValues, PlaybackPrefs.getIdleTimeoutMs(this));
        idleSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < idleValues.length) {
                    applyIdleTimeout(idleValues[position]);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        final long[] intervalValues = new long[] {
                5000L, 10000L, 15000L, 30000L, 60000L, 120000L, 300000L
        };
        Spinner intervalSpinner = addSpinner(root, "每幅画持续时间",
                new String[] { "5秒", "10秒", "15秒", "30秒", "1分钟", "2分钟", "5分钟" },
                intervalValues, PlaybackPrefs.getImageIntervalMs(this));
        intervalSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < intervalValues.length) {
                    PlaybackPrefs.setImageIntervalMs(SettingsActivity.this, intervalValues[position]);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        RadioGroup modeGroup = new RadioGroup(this);
        modeGroup.setOrientation(RadioGroup.HORIZONTAL);
        final RadioButton sequenceButton = new RadioButton(this);
        sequenceButton.setText("顺序播放");
        sequenceButton.setId(1);
        final RadioButton randomButton = new RadioButton(this);
        randomButton.setText("随机播放");
        randomButton.setId(2);
        if (PlaybackPrefs.MODE_RANDOM.equals(PlaybackPrefs.getMode(this))) {
            randomButton.setChecked(true);
        } else {
            sequenceButton.setChecked(true);
        }
        modeGroup.addView(sequenceButton);
        modeGroup.addView(randomButton);
        modeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                String mode = checkedId == randomButton.getId()
                        ? PlaybackPrefs.MODE_RANDOM
                        : PlaybackPrefs.MODE_SEQUENCE;
                PlaybackPrefs.setMode(SettingsActivity.this, mode);
            }
        });
        root.addView(modeGroup);

        final CheckBox clockBox = new CheckBox(this);
        clockBox.setText("屏保显示时钟");
        clockBox.setChecked(PlaybackPrefs.isShowClock(this));
        clockBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                PlaybackPrefs.setShowClock(SettingsActivity.this, isChecked);
            }
        });
        root.addView(clockBox);

        final int[] sizeValues = new int[]{56, 72, 88, 104, 128};
        final String[] sizeLabels = new String[]{"56", "72", "88", "104", "128"};
        Spinner sizeSpinner = addSpinner(root, "时钟大小",
                sizeLabels, new long[]{56, 72, 88, 104, 128},
                PlaybackPrefs.getClockSizeSp(this));
        sizeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < sizeValues.length) {
                    PlaybackPrefs.setClockSizeSp(SettingsActivity.this, sizeValues[position]);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        final String[] horizontalLabels = new String[]{"左侧", "居中", "右侧"};
        final String[] horizontalValues = new String[]{
                PlaybackPrefs.CLOCK_LEFT, PlaybackPrefs.CLOCK_CENTER, PlaybackPrefs.CLOCK_RIGHT
        };
        addClockPositionGroup(root, "时钟水平位置", horizontalLabels, horizontalValues,
                PlaybackPrefs.getClockHorizontal(this),
                new RadioGroup.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(RadioGroup group, int checkedId) {
                        if (checkedId >= 1 && checkedId <= horizontalValues.length) {
                            PlaybackPrefs.setClockHorizontal(SettingsActivity.this,
                                    horizontalValues[checkedId - 1]);
                        }
                    }
                });

        final String[] verticalLabels = new String[]{"顶部", "中间", "底部"};
        final String[] verticalValues = new String[]{
                PlaybackPrefs.CLOCK_TOP, PlaybackPrefs.CLOCK_MIDDLE, PlaybackPrefs.CLOCK_BOTTOM
        };
        addClockPositionGroup(root, "时钟垂直位置", verticalLabels, verticalValues,
                PlaybackPrefs.getClockVertical(this),
                new RadioGroup.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(RadioGroup group, int checkedId) {
                        if (checkedId >= 1 && checkedId <= verticalValues.length) {
                            PlaybackPrefs.setClockVertical(SettingsActivity.this,
                                    verticalValues[checkedId - 1]);
                        }
                    }
                });

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        Button selectAll = new Button(this);
        selectAll.setText("全选");
        Button selectNone = new Button(this);
        selectNone.setText("全不选");
        buttonRow.addView(selectAll);
        buttonRow.addView(selectNone);
        root.addView(buttonRow);

        checkBoxes = new ArrayList<CheckBox>();
        for (final File file : allImages) {
            final CheckBox checkBox = new CheckBox(this);
            checkBox.setText(file.getName());
            checkBox.setSingleLine(false);
            checkBox.setChecked(selectedPaths.contains(file.getAbsolutePath()));
            checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) {
                        selectedPaths.add(file.getAbsolutePath());
                    } else {
                        selectedPaths.remove(file.getAbsolutePath());
                    }
                    PlaybackPrefs.setSelectedPaths(SettingsActivity.this, selectedPaths);
                }
            });
            checkBoxes.add(checkBox);
            root.addView(checkBox);
        }

        selectAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedPaths.clear();
                for (File file : allImages) {
                    selectedPaths.add(file.getAbsolutePath());
                }
                PlaybackPrefs.setSelectedPaths(SettingsActivity.this, selectedPaths);
                updateCheckBoxes();
            }
        });
        selectNone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedPaths.clear();
                PlaybackPrefs.setSelectedPaths(SettingsActivity.this, selectedPaths);
                updateCheckBoxes();
            }
        });

        applyIdleTimeout(PlaybackPrefs.getIdleTimeoutMs(this));
        return scrollView;
    }

    private Spinner addSpinner(LinearLayout root, String label, String[] labels, long[] values, long current) {
        TextView textView = new TextView(this);
        textView.setText(label);
        textView.setTextSize(16f);
        textView.setPadding(0, dp(12), 0, 0);
        root.addView(textView);

        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setMinimumWidth(dp(240));
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                spinner.setSelection(i);
                break;
            }
        }
        root.addView(spinner);
        return spinner;
    }

    private void addClockPositionGroup(LinearLayout root, String label,
                                       String[] labels, String[] values, String current,
                                       RadioGroup.OnCheckedChangeListener listener) {
        TextView textView = new TextView(this);
        textView.setText(label);
        textView.setTextSize(16f);
        textView.setPadding(0, dp(12), 0, 0);
        root.addView(textView);

        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.HORIZONTAL);
        for (int i = 0; i < labels.length && i < values.length; i++) {
            RadioButton button = new RadioButton(this);
            button.setText(labels[i]);
            button.setId(i + 1);
            if (values[i].equals(current)) {
                button.setChecked(true);
            }
            group.addView(button);
        }
        group.setOnCheckedChangeListener(listener);
        root.addView(group);
    }

    private void applyIdleTimeout(long value) {
        PlaybackPrefs.setIdleTimeoutMs(this, value);
        if (Build.VERSION.SDK_INT >= 23 && !Settings.System.canWrite(this)) {
            Toast.makeText(this, "未授权修改系统设置", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, (int) value);
        } catch (SecurityException e) {
            Toast.makeText(this, "未授权修改系统设置", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateCheckBoxes() {
        for (int i = 0; i < checkBoxes.size() && i < allImages.size(); i++) {
            checkBoxes.get(i).setChecked(selectedPaths.contains(allImages.get(i).getAbsolutePath()));
        }
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private int statusBarHeight() {
        int resId = getResources().getIdentifier(
                "status_bar_height", "dimen", "android");
        if (resId > 0) {
            try {
                return getResources().getDimensionPixelSize(resId);
            } catch (Exception ignored) {
            }
        }
        return 36;
    }
}
