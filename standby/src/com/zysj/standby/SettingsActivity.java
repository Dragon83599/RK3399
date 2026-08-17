package com.zysj.standby;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;

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
        root.setPadding(padding, padding, padding, padding);
        scrollView.addView(root);

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

        return scrollView;
    }

    private void updateCheckBoxes() {
        for (int i = 0; i < checkBoxes.size() && i < allImages.size(); i++) {
            checkBoxes.get(i).setChecked(selectedPaths.contains(allImages.get(i).getAbsolutePath()));
        }
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}
