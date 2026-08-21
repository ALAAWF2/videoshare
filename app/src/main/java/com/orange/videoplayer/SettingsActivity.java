package com.orange.videoplayer;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

public class SettingsActivity extends AppCompatActivity {

    private SettingsStore store;

    private TextView tvDefaultSpeedValue;
    private MaterialSwitch switchLongPress;
    private TextView tvLongPressSpeedValue;
    private View itemLongPressSpeed;
    private MaterialSwitch switchPip;
    private MaterialSwitch switchDoubleTap;
    private TextView tvDoubleTapSecondsValue;
    private View itemDoubleTapSeconds;

    private MaterialSwitch switchBrightnessGesture;
    private MaterialSwitch switchVolumeGesture;
    private MaterialSwitch switchScrub;
    private TextView tvScrubWindowValue;
    private View itemScrubWindow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        store = new SettingsStore(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvDefaultSpeedValue = findViewById(R.id.tv_default_speed_value);
        switchLongPress = findViewById(R.id.switch_long_press);
        tvLongPressSpeedValue = findViewById(R.id.tv_long_press_speed_value);
        itemLongPressSpeed = findViewById(R.id.item_long_press_speed);
        switchPip = findViewById(R.id.switch_pip);

        switchDoubleTap = findViewById(R.id.switch_double_tap);
        tvDoubleTapSecondsValue = findViewById(R.id.tv_double_tap_seconds_value);
        itemDoubleTapSeconds = findViewById(R.id.item_double_tap_seconds);

        switchBrightnessGesture = findViewById(R.id.switch_brightness_gesture);
        switchVolumeGesture = findViewById(R.id.switch_volume_gesture);
        switchScrub = findViewById(R.id.switch_scrub);
        tvScrubWindowValue = findViewById(R.id.tv_scrub_window_value);
        itemScrubWindow = findViewById(R.id.item_scrub_window);

        findViewById(R.id.item_default_speed).setOnClickListener(v -> showDefaultSpeedDialog());

        findViewById(R.id.item_long_press_enable).setOnClickListener(v -> {
            boolean newVal = !switchLongPress.isChecked();
            switchLongPress.setChecked(newVal);
            store.setLongPressEnabled(newVal);
            updateUI();
        });

        itemLongPressSpeed.setOnClickListener(v -> {
            if (store.isLongPressEnabled()) {
                showLongPressSpeedDialog();
            }
        });

        findViewById(R.id.item_pip_enable).setOnClickListener(v -> {
            boolean newVal = !switchPip.isChecked();
            switchPip.setChecked(newVal);
            store.setPipEnabled(newVal);
            updateUI();
        });

        findViewById(R.id.item_double_tap_enable).setOnClickListener(v -> {
            boolean newVal = !switchDoubleTap.isChecked();
            switchDoubleTap.setChecked(newVal);
            store.setDoubleTapEnabled(newVal);
            updateUI();
        });

        itemDoubleTapSeconds.setOnClickListener(v -> {
            if (store.isDoubleTapEnabled()) {
                showDoubleTapSecondsDialog();
            }
        });

        findViewById(R.id.item_brightness_gesture).setOnClickListener(v -> {
            boolean newVal = !switchBrightnessGesture.isChecked();
            switchBrightnessGesture.setChecked(newVal);
            store.setBrightnessGestureEnabled(newVal);
            updateUI();
        });

        findViewById(R.id.item_volume_gesture).setOnClickListener(v -> {
            boolean newVal = !switchVolumeGesture.isChecked();
            switchVolumeGesture.setChecked(newVal);
            store.setVolumeGestureEnabled(newVal);
            updateUI();
        });

        findViewById(R.id.item_scrub_enable).setOnClickListener(v -> {
            boolean newVal = !switchScrub.isChecked();
            switchScrub.setChecked(newVal);
            store.setScrubEnabled(newVal);
            updateUI();
        });

        itemScrubWindow.setOnClickListener(v -> {
            if (store.isScrubEnabled()) {
                showScrubWindowDialog();
            }
        });

        updateUI();
    }

    private void updateUI() {
        float defaultSpeed = store.getDefaultSpeed();
        tvDefaultSpeedValue.setText(SettingsStore.formatSpeed(defaultSpeed));

        boolean lpEnabled = store.isLongPressEnabled();
        switchLongPress.setChecked(lpEnabled);
        float lpSpeed = store.getLongPressSpeed();
        tvLongPressSpeedValue.setText(SettingsStore.formatSpeed(lpSpeed));
        itemLongPressSpeed.setAlpha(lpEnabled ? 1.0f : 0.4f);
        itemLongPressSpeed.setEnabled(lpEnabled);

        switchPip.setChecked(store.isPipEnabled());

        boolean dtEnabled = store.isDoubleTapEnabled();
        switchDoubleTap.setChecked(dtEnabled);
        int dtSeconds = store.getDoubleTapSeconds();
        tvDoubleTapSecondsValue.setText(SettingsStore.formatSeconds(dtSeconds));
        itemDoubleTapSeconds.setAlpha(dtEnabled ? 1.0f : 0.4f);
        itemDoubleTapSeconds.setEnabled(dtEnabled);

        switchBrightnessGesture.setChecked(store.isBrightnessGestureEnabled());
        switchVolumeGesture.setChecked(store.isVolumeGestureEnabled());

        boolean scrubEnabled = store.isScrubEnabled();
        switchScrub.setChecked(scrubEnabled);
        int scrubWindow = store.getScrubWindowSeconds();
        tvScrubWindowValue.setText(SettingsStore.formatScrubWindow(scrubWindow));
        itemScrubWindow.setAlpha(scrubEnabled ? 1.0f : 0.4f);
        itemScrubWindow.setEnabled(scrubEnabled);
    }

    private void showDefaultSpeedDialog() {
        float current = store.getDefaultSpeed();
        int selectedIndex = 2; // default to 1x
        for (int i = 0; i < SettingsStore.SPEED_OPTIONS.length; i++) {
            if (Math.abs(SettingsStore.SPEED_OPTIONS[i] - current) < 0.01f) {
                selectedIndex = i;
                break;
            }
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_default_speed)
                .setSingleChoiceItems(SettingsStore.SPEED_LABELS, selectedIndex, (dialog, which) -> {
                    store.setDefaultSpeed(SettingsStore.SPEED_OPTIONS[which]);
                    updateUI();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showLongPressSpeedDialog() {
        float current = store.getLongPressSpeed();
        int selectedIndex = 5; // default to 2x
        for (int i = 0; i < SettingsStore.SPEED_OPTIONS.length; i++) {
            if (Math.abs(SettingsStore.SPEED_OPTIONS[i] - current) < 0.01f) {
                selectedIndex = i;
                break;
            }
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_long_press_speed)
                .setSingleChoiceItems(SettingsStore.SPEED_LABELS, selectedIndex, (dialog, which) -> {
                    store.setLongPressSpeed(SettingsStore.SPEED_OPTIONS[which]);
                    updateUI();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showDoubleTapSecondsDialog() {
        int current = store.getDoubleTapSeconds();
        int selectedIndex = 1; // default to 5s
        for (int i = 0; i < SettingsStore.SEEK_SECONDS_OPTIONS.length; i++) {
            if (SettingsStore.SEEK_SECONDS_OPTIONS[i] == current) {
                selectedIndex = i;
                break;
            }
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_double_tap_seconds)
                .setSingleChoiceItems(SettingsStore.SEEK_SECONDS_LABELS, selectedIndex, (dialog, which) -> {
                    store.setDoubleTapSeconds(SettingsStore.SEEK_SECONDS_OPTIONS[which]);
                    updateUI();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showScrubWindowDialog() {
        int current = store.getScrubWindowSeconds();
        int selectedIndex = 2; // default to 120s
        for (int i = 0; i < SettingsStore.SCRUB_WINDOW_OPTIONS.length; i++) {
            if (SettingsStore.SCRUB_WINDOW_OPTIONS[i] == current) {
                selectedIndex = i;
                break;
            }
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_scrub_window)
                .setSingleChoiceItems(SettingsStore.SCRUB_WINDOW_LABELS, selectedIndex, (dialog, which) -> {
                    store.setScrubWindowSeconds(SettingsStore.SCRUB_WINDOW_OPTIONS[which]);
                    updateUI();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
