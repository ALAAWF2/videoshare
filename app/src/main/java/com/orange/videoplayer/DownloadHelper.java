package com.orange.videoplayer;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.util.Locale;

public class DownloadHelper {

    public interface DownloadCallback {
        void onDownloadStarted(long downloadId, String filename);
        void onDownloadError(String error);
    }

    public static void startDownload(Context context, IptvApiClient apiClient, String title, String streamUrl, String iconUrl, DownloadCallback callback) {
        if (context == null || streamUrl == null || streamUrl.trim().isEmpty()) {
            if (callback != null) callback.onDownloadError("الرابط غير صالح");
            return;
        }

        Toast.makeText(context, context.getString(R.string.resolving_direct), Toast.LENGTH_SHORT).show();

        IptvApiClient client = (apiClient != null) ? apiClient : new IptvApiClient();
        client.resolveDirectUrl(streamUrl, new IptvApiClient.Callback<String>() {
            @Override
            public void onSuccess(String resolvedUrl) {
                String finalUrl = (resolvedUrl != null && !resolvedUrl.isEmpty()) ? resolvedUrl : streamUrl;
                startServiceDownload(context, title, finalUrl, iconUrl, callback);
            }

            @Override
            public void onError(String error) {
                startServiceDownload(context, title, streamUrl, iconUrl, callback);
            }
        });
    }

    private static void startServiceDownload(Context context, String title, String directUrl, String iconUrl, DownloadCallback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        try {
            String displayTitle = (title != null && !title.trim().isEmpty()) ? title.trim() : LinkStore.autoName(directUrl);

            Intent intent = new Intent(context, DownloadService.class);
            intent.setAction(DownloadService.ACTION_START_DOWNLOAD);
            intent.putExtra(DownloadService.EXTRA_TITLE, displayTitle);
            intent.putExtra(DownloadService.EXTRA_URL, directUrl);
            if (iconUrl != null && !iconUrl.isEmpty()) {
                intent.putExtra(DownloadService.EXTRA_ICON, iconUrl);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }

            mainHandler.post(() -> {
                Toast.makeText(context, context.getString(R.string.download_started, displayTitle), Toast.LENGTH_SHORT).show();
                if (callback != null) callback.onDownloadStarted(System.currentTimeMillis(), displayTitle);
            });
        } catch (Exception e) {
            mainHandler.post(() -> {
                String err = e.getMessage() != null ? e.getMessage() : "خطأ أثناء بدء التحميل";
                Toast.makeText(context, context.getString(R.string.download_failed) + ": " + err, Toast.LENGTH_LONG).show();
                if (callback != null) callback.onDownloadError(err);
            });
        }
    }

    public static void pauseDownload(Context context, long downloadId) {
        if (context == null || downloadId <= 0) return;
        try {
            Intent intent = new Intent(context, DownloadService.class);
            intent.setAction(DownloadService.ACTION_PAUSE);
            intent.putExtra(DownloadService.EXTRA_ID, downloadId);
            context.startService(intent);
        } catch (Exception ignored) {
        }
    }

    public static void resumeDownload(Context context, long downloadId) {
        if (context == null || downloadId <= 0) return;
        try {
            Intent intent = new Intent(context, DownloadService.class);
            intent.setAction(DownloadService.ACTION_RESUME);
            intent.putExtra(DownloadService.EXTRA_ID, downloadId);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception ignored) {
        }
    }

    public static void cancelDownload(Context context, long downloadId) {
        if (context == null || downloadId <= 0) return;
        try {
            Intent intent = new Intent(context, DownloadService.class);
            intent.setAction(DownloadService.ACTION_CANCEL);
            intent.putExtra(DownloadService.EXTRA_ID, downloadId);
            context.startService(intent);
        } catch (Exception ignored) {
        }
    }

    public static String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 MB";
        double kb = bytes / 1024.0;
        double mb = kb / 1024.0;
        double gb = mb / 1024.0;
        if (gb >= 1.0) {
            return String.format(Locale.US, "%.2f GB", gb);
        } else if (mb >= 1.0) {
            return String.format(Locale.US, "%.1f MB", mb);
        } else {
            return String.format(Locale.US, "%.0f KB", kb);
        }
    }
}
