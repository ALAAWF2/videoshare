package com.orange.videoplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.YoutubeDLResponse;

import org.json.JSONObject;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kotlin.Unit;
import kotlin.jvm.functions.Function3;

/**
 * Universal background download service powered by yt-dlp & bundled FFmpeg.
 * Provides foreground progress notifications, pause/resume, and automatic MediaStore indexing.
 */
public class DownloadService extends Service {

    private static final String TAG = "DownloadService";

    public static final String ACTION_START_DOWNLOAD = "com.orange.videoplayer.action.START_DOWNLOAD";
    public static final String ACTION_PAUSE = "com.orange.videoplayer.action.PAUSE_DOWNLOAD";
    public static final String ACTION_RESUME = "com.orange.videoplayer.action.RESUME_DOWNLOAD";
    public static final String ACTION_CANCEL = "com.orange.videoplayer.action.CANCEL_DOWNLOAD";

    public static final String EXTRA_ID = "id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_FORMAT = "format";
    public static final String EXTRA_AUDIO_ONLY = "audio_only";
    public static final String EXTRA_ICON = "icon";

    private static final String CHANNEL_ID = "downloads";
    private static final int NOTIF_ID_FOREGROUND = 1001;

    public static final Set<Long> activeIds = Collections.synchronizedSet(new HashSet<>());
    private final Map<Long, DownloadTask> runningTasks = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private PowerManager.WakeLock wakeLock;
    private boolean isForegroundStarted = false;

    private static final Pattern PROGRESS_SIZE_PATTERN = Pattern.compile("of\\s+~?\\s*([0-9.]+\\s*[KMGTP]?i?B)", Pattern.CASE_INSENSITIVE);

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            checkStopService();
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        if (ACTION_START_DOWNLOAD.equals(action)) {
            handleStartDownload(intent);
        } else if (ACTION_PAUSE.equals(action)) {
            handlePauseDownload(intent);
        } else if (ACTION_RESUME.equals(action)) {
            handleResumeDownload(intent);
        } else if (ACTION_CANCEL.equals(action)) {
            handleCancelDownload(intent);
        }

        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releaseWakeLock();
    }

    private void handleStartDownload(Intent intent) {
        String url = intent.getStringExtra(EXTRA_URL);
        if (url == null || url.trim().isEmpty()) {
            checkStopService();
            return;
        }

        String title = intent.getStringExtra(EXTRA_TITLE);
        String format = intent.getStringExtra(EXTRA_FORMAT);
        boolean audioOnly = intent.getBooleanExtra(EXTRA_AUDIO_ONLY, false);
        String iconUrl = intent.getStringExtra(EXTRA_ICON);
        long explicitId = intent.getLongExtra(EXTRA_ID, 0L);

        long id = (explicitId > 0) ? explicitId : System.currentTimeMillis();
        if (runningTasks.containsKey(id)) {
            return;
        }

        String displayTitle = (title != null && !title.trim().isEmpty()) ? title.trim() : "فيديو";
        File dir = getDownloadsDir();

        DownloadStore.getInstance(this).addOrUpdate(
                id,
                displayTitle,
                url,
                "",
                new File(dir, sanitizeFilename(displayTitle) + (audioOnly ? ".m4a" : ".mp4")).getAbsolutePath(),
                DownloadStore.STATUS_RUNNING,
                0L,
                iconUrl
        );

        if (format != null && !format.isEmpty()) {
            DownloadStore.getInstance(this).setMeta(id, "format", format);
        }
        DownloadStore.getInstance(this).setMeta(id, "audioOnly", String.valueOf(audioOnly));

        ensureForeground(displayTitle, getString(R.string.download_status_downloading, 0));

        DownloadTask task = new DownloadTask(id, displayTitle, url, format, audioOnly, iconUrl, dir);
        runningTasks.put(id, task);
        activeIds.add(id);
        acquireWakeLock();
        executor.execute(task);
    }

    private void handlePauseDownload(Intent intent) {
        long id = intent.getLongExtra(EXTRA_ID, 0L);
        if (id <= 0) return;

        DownloadTask task = runningTasks.remove(id);
        if (task != null) {
            task.cancelOrPause();
        }
        activeIds.remove(id);

        JSONObject obj = DownloadStore.getInstance(this).get(id);
        if (obj != null) {
            long currentDownloaded = obj.optLong("downloadedBytes", 0);
            long total = obj.optLong("totalBytes", 0);
            DownloadStore.getInstance(this).updateProgress(
                    id,
                    DownloadStore.STATUS_PAUSED,
                    currentDownloaded,
                    total,
                    obj.optString("localUri"),
                    obj.optString("filePath")
            );
        }
        checkStopService();
    }

    private void handleResumeDownload(Intent intent) {
        long id = intent.getLongExtra(EXTRA_ID, 0L);
        if (id <= 0) return;

        if (runningTasks.containsKey(id)) {
            return;
        }

        JSONObject obj = DownloadStore.getInstance(this).get(id);
        if (obj == null) return;

        String url = obj.optString("url");
        if (url == null || url.trim().isEmpty()) return;

        String title = obj.optString("title", "فيديو محمل");
        String format = obj.optString("format", null);
        boolean audioOnly = "true".equalsIgnoreCase(obj.optString("audioOnly", "false"));
        String iconUrl = obj.optString("iconUrl");

        ensureForeground(title, getString(R.string.download_status_downloading, 0));

        DownloadTask task = new DownloadTask(id, title, url, format, audioOnly, iconUrl, getDownloadsDir());
        runningTasks.put(id, task);
        activeIds.add(id);
        acquireWakeLock();
        executor.execute(task);
    }

    private void handleCancelDownload(Intent intent) {
        long id = intent.getLongExtra(EXTRA_ID, 0L);
        if (id <= 0) return;

        DownloadTask task = runningTasks.remove(id);
        if (task != null) {
            task.cancelOrPause();
        }
        activeIds.remove(id);

        DownloadStore.getInstance(this).delete(id);
        checkStopService();
    }

    private class DownloadTask implements Runnable {
        private final long id;
        private final String title;
        private final String url;
        private final String format;
        private final boolean audioOnly;
        private final String iconUrl;
        private final File dir;
        private final String procId;
        private volatile boolean isCancelled = false;
        private long lastProgressUpdate = 0L;

        DownloadTask(long id, String title, String url, String format, boolean audioOnly, String iconUrl, File dir) {
            this.id = id;
            this.title = title;
            this.url = url;
            this.format = format;
            this.audioOnly = audioOnly;
            this.iconUrl = iconUrl;
            this.dir = dir;
            this.procId = "dl_" + id;
        }

        public void cancelOrPause() {
            isCancelled = true;
            try {
                YoutubeDL.getInstance().destroyProcessById(procId);
            } catch (Exception ignored) {
            }
        }

        @Override
        public void run() {
            try {
                if (isCancelled) return;

                boolean success = executeDownload(false);
                if (!success && !isCancelled) {
                    Log.w(TAG, "Download attempt 1 failed. Updating yt-dlp binary from GitHub and retrying...");
                    YtdlpUpdater.updateSync(DownloadService.this);
                    if (!isCancelled) {
                        executeDownload(true);
                    }
                }
            } finally {
                activeIds.remove(id);
                runningTasks.remove(id);
                releaseWakeLock();
                checkStopService();
            }
        }

        private boolean executeDownload(boolean isRetry) {
            try {
                if (isCancelled) return false;

                try {
                    YoutubeDL.getInstance().init(getApplicationContext());
                } catch (Exception ignored) {
                }

                String safeTitle = sanitizeFilename(title);
                String template = dir.getAbsolutePath() + "/" + safeTitle + " [" + id + "].%(ext)s";

                YoutubeDLRequest req = new YoutubeDLRequest(url);
                req.addOption("-o", template);
                req.addOption("--no-mtime");
                req.addOption("--no-playlist");
                req.addOption("--newline");
                req.addOption("--continue");
                req.addOption("--no-update");
                req.addOption("--ignore-errors");
                req.addOption("--extractor-args", "youtube:player_client=ios,web,mweb,android");
                req.addOption("--concurrent-fragments", "4");

                if (audioOnly) {
                    req.addOption("-f", "bestaudio[ext=m4a]/bestaudio/best");
                    req.addOption("-x");
                    req.addOption("--audio-format", "m4a");
                    req.addOption("--embed-metadata");
                } else if (format != null && !format.isEmpty()) {
                    req.addOption("-f", format + "+bestaudio/best/" + format);
                    req.addOption("--merge-output-format", "mp4");
                } else {
                    // Universal best video (up to 4K) + best audio merged to MP4 via bundled ffmpeg
                    req.addOption("-f", "(bestvideo[ext=mp4][height<=2160]+bestaudio[ext=m4a])/(bestvideo+bestaudio)/best");
                    req.addOption("--merge-output-format", "mp4");
                }

                Log.d(TAG, "Starting yt-dlp download (retry=" + isRetry + "): " + url + " [procId=" + procId + "]");

                YoutubeDLResponse resp = YoutubeDL.getInstance().execute(req, procId, new Function3<Float, Long, String, Unit>() {
                    @Override
                    public Unit invoke(Float progress, Long eta, String line) {
                        if (isCancelled) return Unit.INSTANCE;

                        long now = System.currentTimeMillis();
                        if (now - lastProgressUpdate >= 500) {
                            lastProgressUpdate = now;
                            int p = (progress != null) ? Math.round(progress) : 0;
                            p = Math.max(0, Math.min(100, p));

                            updateNotificationProgress(title, p);

                            DownloadStore.getInstance(DownloadService.this).updateProgress(
                                    id,
                                    DownloadStore.STATUS_RUNNING,
                                    p,
                                    100L,
                                    "",
                                    ""
                            );
                        }
                        return Unit.INSTANCE;
                    }
                });

                if (isCancelled) return false;

                // Find the downloaded file
                File outputFile = findOutputFile(dir, id, safeTitle);
                if (outputFile != null && outputFile.exists() && outputFile.length() > 0) {
                    long size = outputFile.length();
                    String finalPath = outputFile.getAbsolutePath();
                    Uri uri = Uri.fromFile(outputFile);

                    DownloadStore.getInstance(DownloadService.this).updateProgress(
                            id,
                            DownloadStore.STATUS_SUCCESSFUL,
                            size,
                            size,
                            uri.toString(),
                            finalPath
                    );
                    DownloadStore.getInstance(DownloadService.this).setMeta(id, "error", null);

                    // Scan file so it appears immediately in the gallery and player
                    MediaScannerConnection.scanFile(DownloadService.this,
                            new String[]{finalPath}, null, null);

                    showCompletionNotification(id, title, outputFile);
                    Log.i(TAG, "Download completed successfully: " + finalPath + " (" + size + " bytes)");
                    return true;
                } else if (isRetry) {
                    fail("تعذر العثور على الملف المحمل");
                }
                return false;

            } catch (YoutubeDL.CanceledException e) {
                Log.d(TAG, "Download canceled or paused: " + id);
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Download failed (retry=" + isRetry + "): " + e.getMessage(), e);
                if (isRetry) {
                    String msg = e.getMessage() != null ? e.getMessage() : "خطأ غير معروف";
                    if (msg.contains("Sign in")) {
                        msg = "الفيديو يتطلب تسجيل الدخول أو تأكيد العمر";
                    }
                    fail(msg);
                }
                return false;
            }
        }

        private void fail(String errorMsg) {
            if (isCancelled) {
                DownloadStore.getInstance(DownloadService.this).delete(id);
                return;
            }
            DownloadStore.getInstance(DownloadService.this).updateProgress(
                    id,
                    DownloadStore.STATUS_FAILED,
                    0L,
                    0L,
                    "",
                    ""
            );
            DownloadStore.getInstance(DownloadService.this).setMeta(id, "error", errorMsg);
            updateNotificationMessage(title, errorMsg);
        }
    }

    private File findOutputFile(File dir, long id, String safeTitle) {
        String idTag = "[" + id + "]";
        File[] files = dir.listFiles();
        if (files == null) return null;

        // 1. Look for exact ID tag match
        for (File f : files) {
            if (f.isFile() && f.getName().contains(idTag) && !f.getName().endsWith(".part") && !f.getName().endsWith(".ytdl")) {
                return f;
            }
        }

        // 2. Look for safeTitle match
        for (File f : files) {
            if (f.isFile() && f.getName().startsWith(safeTitle) && !f.getName().endsWith(".part") && !f.getName().endsWith(".ytdl")) {
                return f;
            }
        }

        return null;
    }

    private File getDownloadsDir() {
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MyPlyr");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private static String sanitizeFilename(String name) {
        if (name == null) return "video";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private synchronized void acquireWakeLock() {
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyPlyr:DownloadWakeLock");
                wakeLock.acquire(45 * 60 * 1000L); // 45 min timeout
            }
        }
    }

    private synchronized void releaseWakeLock() {
        if (runningTasks.isEmpty() && wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception ignored) {
            }
            wakeLock = null;
        }
    }

    private void checkStopService() {
        if (runningTasks.isEmpty()) {
            stopForeground(true);
            isForegroundStarted = false;
            stopSelf();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.download_channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.download_channel_desc));
            channel.enableVibration(false);
            channel.setSound(null, null);

            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private void ensureForeground(String title, String content) {
        Intent intent = new Intent(this, DownloadsActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notif = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_download)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();

        if (!isForegroundStarted) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIF_ID_FOREGROUND, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
                } else {
                    startForeground(NOTIF_ID_FOREGROUND, notif);
                }
                isForegroundStarted = true;
            } catch (Exception e) {
                Log.e(TAG, "Failed to start foreground notification", e);
            }
        } else {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(NOTIF_ID_FOREGROUND, notif);
            }
        }
    }

    private void updateNotificationProgress(String title, int progress) {
        Intent intent = new Intent(this, DownloadsActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(progress > 0 ? getString(R.string.download_status_downloading, progress) : "جاري التحميل...")
                .setSmallIcon(R.drawable.ic_download)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, progress, progress <= 0);

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIF_ID_FOREGROUND, builder.build());
        }
    }

    private void updateNotificationMessage(String title, String message) {
        Intent intent = new Intent(this, DownloadsActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_download)
                .setContentIntent(pi)
                .setOngoing(false)
                .setAutoCancel(true);

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    private void showCompletionNotification(long id, String title, File file) {
        Intent playIntent = new Intent(this, PlayerActivity.class);
        playIntent.putExtra("url", Uri.fromFile(file).toString());
        playIntent.putExtra("name", title);

        PendingIntent pi = PendingIntent.getActivity(
                this, (int) id, playIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notif = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(getString(R.string.download_completed))
                .setSmallIcon(R.drawable.ic_download)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify((int) (id & 0x7FFFFFFF), notif);
        }
    }
}
