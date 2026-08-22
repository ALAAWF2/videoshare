package com.orange.videoplayer;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.yausername.youtubedl_android.YoutubeDL;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles automatic background updates for the yt-dlp binary from GitHub releases.
 * Ensures the app always has the latest extractors to bypass YouTube SABR / 403 walls.
 */
public final class YtdlpUpdater {

    private static final String TAG = "YtdlpUpdater";
    private static final String PREFS_NAME = "ytdlp_update_prefs";
    private static final String KEY_LAST_UPDATE_CHECK = "last_update_check";
    private static final AtomicBoolean isUpdating = new AtomicBoolean(false);

    private YtdlpUpdater() {}

    /**
     * Checks and updates yt-dlp binary in the background if 12h have passed.
     */
    public static void autoUpdateIfNeeded(Context context) {
        if (context == null) return;
        Context appCtx = context.getApplicationContext();

        SharedPreferences sp = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastCheck = sp.getLong(KEY_LAST_UPDATE_CHECK, 0L);
        long now = System.currentTimeMillis();

        if (now - lastCheck < 12 * 60 * 60 * 1000L) {
            return;
        }

        updateSync(appCtx);
    }

    /**
     * Synchronously downloads and installs the latest yt-dlp binary from GitHub.
     */
    public static boolean updateSync(Context context) {
        if (context == null) return false;
        if (!isUpdating.compareAndSet(false, true)) {
            return false;
        }

        try {
            Context appCtx = context.getApplicationContext();
            try {
                YoutubeDL.getInstance().init(appCtx);
            } catch (Exception ignored) {
            }

            Log.d(TAG, "Updating yt-dlp binary from GitHub releases...");
            YoutubeDL.UpdateStatus st = YoutubeDL.getInstance().updateYoutubeDL(appCtx, YoutubeDL.UpdateChannel._STABLE);
            String ver = YoutubeDL.getInstance().version(appCtx);
            Log.i(TAG, "yt-dlp update status: " + st + " | Current version: " + ver);

            appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putLong(KEY_LAST_UPDATE_CHECK, System.currentTimeMillis()).apply();
            return true;
        } catch (Exception e) {
            Log.w(TAG, "yt-dlp update failed: " + e.getMessage());
            return false;
        } finally {
            isUpdating.set(false);
        }
    }
}
