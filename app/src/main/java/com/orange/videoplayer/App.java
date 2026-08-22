package com.orange.videoplayer;

import android.app.Application;
import android.util.Log;

import com.yausername.youtubedl_android.YoutubeDL;

import java.util.concurrent.Executors;

/**
 * Application entry point: initializes yt-dlp runtime engine and checks for updates in the background.
 */
public class App extends Application {

    private static final String TAG = "MyPlyrApp";

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize YoutubeDL and auto-update binary in background thread (same as YTDLnis)
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                YoutubeDL.getInstance().init(getApplicationContext());
                Log.d(TAG, "YoutubeDL initialized. Current version: " + YoutubeDL.getInstance().version(getApplicationContext()));

                // Auto-update to latest yt-dlp version from GitHub
                YtdlpUpdater.autoUpdateIfNeeded(getApplicationContext());
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize/update YoutubeDL", e);
            }
        });
    }
}
