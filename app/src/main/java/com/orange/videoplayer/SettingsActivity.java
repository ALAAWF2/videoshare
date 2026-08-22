package com.orange.videoplayer;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.yausername.youtubedl_android.YoutubeDL;

import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity {

    private SettingsStore store;

    private TextView tvThemeValue;
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

    private View itemCobaltServer;
    private TextView tvCobaltValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        store = new SettingsStore(this);
        setTheme(store.getThemeResId());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvThemeValue = findViewById(R.id.tv_theme_value);
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

        itemCobaltServer = findViewById(R.id.item_cobalt_server);
        tvCobaltValue = findViewById(R.id.tv_cobalt_value);

        findViewById(R.id.item_theme).setOnClickListener(v -> showThemeDialog());
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

        itemCobaltServer.setOnClickListener(v -> showYtdlpUpdateDialog());

        updateUI();
    }

    private void showYtdlpUpdateDialog() {
        String currentVer = getYtdlpVersion();
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_ytdlp_title)
                .setMessage("الإصدار المثبت: " + currentVer + "\n\nهل تريد فحص وتحديث محرك التحميل إلى أحدث إصدار من GitHub؟")
                .setPositiveButton("تحديث الآن", (dialog, which) -> performYtdlpUpdate())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void performYtdlpUpdate() {
        Toast.makeText(this, R.string.settings_ytdlp_updating, Toast.LENGTH_SHORT).show();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                YoutubeDL.UpdateStatus st = YoutubeDL.getInstance()
                        .updateYoutubeDL(getApplicationContext(), YoutubeDL.UpdateChannel._STABLE);
                String newVer = getYtdlpVersion();
                runOnUiThread(() -> {
                    if (st == YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE) {
                        Toast.makeText(this, getString(R.string.settings_ytdlp_up_to_date) + " (" + newVer + ")", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, getString(R.string.settings_ytdlp_updated) + " (" + newVer + ")", Toast.LENGTH_LONG).show();
                    }
                    updateUI();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    String err = e.getMessage() != null ? e.getMessage() : "خطأ غير معروف";
                    Toast.makeText(this, getString(R.string.settings_ytdlp_error, err), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private String getYtdlpVersion() {
        try {
            return YoutubeDL.getInstance().version(getApplicationContext());
        } catch (Exception e) {
            return "?";
        }
    }

    private void updateUI() {
        String theme = store.getAppTheme();
        if (SettingsStore.THEME_AMOLED.equals(theme)) {
            tvThemeValue.setText(R.string.theme_amoled);
        } else if (SettingsStore.THEME_OCEAN.equals(theme)) {
            tvThemeValue.setText(R.string.theme_ocean);
        } else if (SettingsStore.THEME_CYBERPUNK.equals(theme)) {
            tvThemeValue.setText(R.string.theme_cyberpunk);
        } else {
            tvThemeValue.setText(R.string.theme_default);
        }

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

        String ver = getYtdlpVersion();
        tvCobaltValue.setText(ver);
    }

    private void showThemeDialog() {
        String[] labels = new String[]{
                getString(R.string.theme_default),
                getString(R.string.theme_amoled),
                getString(R.string.theme_ocean),
                getString(R.string.theme_cyberpunk)
        };
        String[] values = new String[]{
                SettingsStore.THEME_DEFAULT,
                SettingsStore.THEME_AMOLED,
                SettingsStore.THEME_OCEAN,
                SettingsStore.THEME_CYBERPUNK
        };

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_theme_title)
                .setItems(labels, (dialog, which) -> {
                    store.setAppTheme(values[which]);
                    updateUI();
                    recreate();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
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
        int selectedIndex = 1; // default to 2x
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
        int selectedIndex = 1; // default to 60s
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
