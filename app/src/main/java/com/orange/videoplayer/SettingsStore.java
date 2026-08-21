package com.orange.videoplayer;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

public class SettingsStore {

    private static final String PREFS_NAME = "myplyr_settings";

    public static final String KEY_DEFAULT_SPEED = "default_speed";
    public static final String KEY_LONG_PRESS_SPEED = "long_press_speed";
    public static final String KEY_LONG_PRESS_ENABLED = "long_press_enabled";
    public static final String KEY_DOUBLE_TAP_SECONDS = "double_tap_seconds";
    public static final String KEY_DOUBLE_TAP_ENABLED = "double_tap_enabled";
    public static final String KEY_BRIGHTNESS_GESTURE_ENABLED = "brightness_gesture_enabled";
    public static final String KEY_VOLUME_GESTURE_ENABLED = "volume_gesture_enabled";
    public static final String KEY_SCRUB_ENABLED = "scrub_enabled";
    public static final String KEY_SCRUB_WINDOW_SECONDS = "scrub_window_seconds";
    public static final String KEY_PIP_ENABLED = "pip_enabled";

    public static final float[] SPEED_OPTIONS = new float[]{0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f};
    public static final String[] SPEED_LABELS = new String[]{"0.5×", "0.75×", "1×", "1.25×", "1.5×", "2×", "2.5×", "3×"};

    public static final int[] SEEK_SECONDS_OPTIONS = new int[]{3, 5, 10, 15, 20, 30};
    public static final String[] SEEK_SECONDS_LABELS = new String[]{"3 ثوانٍ", "5 ثوانٍ", "10 ثوانٍ", "15 ثانية", "20 ثانية", "30 ثانية"};

    public static final int[] SCRUB_WINDOW_OPTIONS = new int[]{30, 60, 120, 300, 600};
    public static final String[] SCRUB_WINDOW_LABELS = new String[]{"30 ثانية", "60 ثانية (دقيقة)", "120 ثانية (دقيقتان)", "300 ثانية (5 دقائق)", "600 ثانية (10 دقائق)"};

    private final SharedPreferences sp;

    public SettingsStore(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public float getDefaultSpeed() {
        return sp.getFloat(KEY_DEFAULT_SPEED, 1.0f);
    }

    public void setDefaultSpeed(float speed) {
        sp.edit().putFloat(KEY_DEFAULT_SPEED, speed).apply();
    }

    public float getLongPressSpeed() {
        return sp.getFloat(KEY_LONG_PRESS_SPEED, 2.0f);
    }

    public void setLongPressSpeed(float speed) {
        sp.edit().putFloat(KEY_LONG_PRESS_SPEED, speed).apply();
    }

    public boolean isLongPressEnabled() {
        return sp.getBoolean(KEY_LONG_PRESS_ENABLED, true);
    }

    public void setLongPressEnabled(boolean enabled) {
        sp.edit().putBoolean(KEY_LONG_PRESS_ENABLED, enabled).apply();
    }

    public int getDoubleTapSeconds() {
        return sp.getInt(KEY_DOUBLE_TAP_SECONDS, 5);
    }

    public void setDoubleTapSeconds(int seconds) {
        sp.edit().putInt(KEY_DOUBLE_TAP_SECONDS, seconds).apply();
    }

    public boolean isDoubleTapEnabled() {
        return sp.getBoolean(KEY_DOUBLE_TAP_ENABLED, true);
    }

    public void setDoubleTapEnabled(boolean enabled) {
        sp.edit().putBoolean(KEY_DOUBLE_TAP_ENABLED, enabled).apply();
    }

    public boolean isBrightnessGestureEnabled() {
        return sp.getBoolean(KEY_BRIGHTNESS_GESTURE_ENABLED, true);
    }

    public void setBrightnessGestureEnabled(boolean enabled) {
        sp.edit().putBoolean(KEY_BRIGHTNESS_GESTURE_ENABLED, enabled).apply();
    }

    public boolean isVolumeGestureEnabled() {
        return sp.getBoolean(KEY_VOLUME_GESTURE_ENABLED, true);
    }

    public void setVolumeGestureEnabled(boolean enabled) {
        sp.edit().putBoolean(KEY_VOLUME_GESTURE_ENABLED, enabled).apply();
    }

    public boolean isScrubEnabled() {
        return sp.getBoolean(KEY_SCRUB_ENABLED, true);
    }

    public void setScrubEnabled(boolean enabled) {
        sp.edit().putBoolean(KEY_SCRUB_ENABLED, enabled).apply();
    }

    public int getScrubWindowSeconds() {
        return sp.getInt(KEY_SCRUB_WINDOW_SECONDS, 120);
    }

    public void setScrubWindowSeconds(int seconds) {
        sp.edit().putInt(KEY_SCRUB_WINDOW_SECONDS, seconds).apply();
    }

    public boolean isPipEnabled() {
        return sp.getBoolean(KEY_PIP_ENABLED, true);
    }

    public void setPipEnabled(boolean enabled) {
        sp.edit().putBoolean(KEY_PIP_ENABLED, enabled).apply();
    }

    public static String formatSpeed(float speed) {
        if (speed == (int) speed) {
            return String.format(Locale.US, "%d×", (int) speed);
        } else {
            String str = String.valueOf(speed);
            if (str.endsWith(".0")) {
                str = str.substring(0, str.length() - 2);
            }
            return String.format(Locale.US, "%s×", str);
        }
    }

    public static String formatSeconds(int seconds) {
        for (int i = 0; i < SEEK_SECONDS_OPTIONS.length; i++) {
            if (SEEK_SECONDS_OPTIONS[i] == seconds) {
                return SEEK_SECONDS_LABELS[i];
            }
        }
        return seconds + " ثوانٍ";
    }

    public static String formatScrubWindow(int seconds) {
        for (int i = 0; i < SCRUB_WINDOW_OPTIONS.length; i++) {
            if (SCRUB_WINDOW_OPTIONS[i] == seconds) {
                return SCRUB_WINDOW_LABELS[i];
            }
        }
        return seconds + " ثانية";
    }

    public static float getNextSpeed(float currentSpeed) {
        int currentIndex = -1;
        float minDiff = Float.MAX_VALUE;
        for (int i = 0; i < SPEED_OPTIONS.length; i++) {
            float diff = Math.abs(SPEED_OPTIONS[i] - currentSpeed);
            if (diff < minDiff) {
                minDiff = diff;
                currentIndex = i;
            }
        }
        int nextIndex = (currentIndex + 1) % SPEED_OPTIONS.length;
        return SPEED_OPTIONS[nextIndex];
    }
}
