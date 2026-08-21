package com.orange.videoplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadService extends Service {

    public static final String ACTION_START_DOWNLOAD = "com.orange.videoplayer.action.START_DOWNLOAD";
    public static final String ACTION_PAUSE = "com.orange.videoplayer.action.PAUSE_DOWNLOAD";
    public static final String ACTION_RESUME = "com.orange.videoplayer.action.RESUME_DOWNLOAD";
    public static final String ACTION_CANCEL = "com.orange.videoplayer.action.CANCEL_DOWNLOAD";

    public static final String EXTRA_ID = "id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_ICON = "icon";

    private static final String CHANNEL_ID = "downloads";
    private static final int NOTIF_ID_FOREGROUND = 1;
    private static final String USER_AGENT = "IPTVSmartersPro/3.1.5 (Android; Mobile)";

    public static final Set<Long> activeIds = Collections.synchronizedSet(new HashSet<>());
    private final Map<Long, DownloadWorker> workers = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private ConnectivityManager.NetworkCallback networkCallback;
    private PowerManager.WakeLock wakeLock;
    private boolean isForegroundStarted = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        registerNetworkCallback();
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
        unregisterNetworkCallback();
        for (DownloadWorker worker : workers.values()) {
            worker.pause();
        }
        workers.clear();
        activeIds.clear();
        releaseWakeLock();
        executor.shutdownNow();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.download_notif_channel),
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription("إشعارات تنزيل الفيديوهات");
                channel.setShowBadge(false);
                nm.createNotificationChannel(channel);
            }
        }
    }

    private void ensureForeground(String title, String message) {
        Notification notification = buildForegroundNotification(title, message, -1, true);
        if (!isForegroundStarted) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIF_ID_FOREGROUND, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
                } else {
                    startForeground(NOTIF_ID_FOREGROUND, notification);
                }
                isForegroundStarted = true;
            } catch (Exception ignored) {
            }
        } else {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(NOTIF_ID_FOREGROUND, notification);
            }
        }
    }

    private Notification buildForegroundNotification(String title, String message, int progress, boolean indeterminate) {
        Intent intent = new Intent(this, DownloadsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        String displayTitle = (title != null && !title.isEmpty()) ? title : getString(R.string.downloads);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(displayTitle)
                .setContentText(message != null ? message : getString(R.string.iptv_loading))
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        if (progress >= 0) {
            builder.setProgress(100, progress, indeterminate);
        }

        return builder.build();
    }

    private synchronized void updateNotificationProgress(String title, long downloaded, long total) {
        if (workers.isEmpty()) return;
        int pct = (total > 0) ? (int) (downloaded * 100 / total) : 0;
        String msg;
        if (total > 0) {
            msg = DownloadHelper.formatFileSize(downloaded) + " / " + DownloadHelper.formatFileSize(total) + " (" + pct + "%)";
        } else if (downloaded > 0) {
            msg = DownloadHelper.formatFileSize(downloaded);
        } else {
            msg = getString(R.string.iptv_loading);
        }

        Notification notif = buildForegroundNotification(title, msg, pct, total <= 0);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIF_ID_FOREGROUND, notif);
        }
    }

    private synchronized void updateNotificationMessage(String title, String message) {
        if (workers.isEmpty()) return;
        Notification notif = buildForegroundNotification(title, message, -1, true);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIF_ID_FOREGROUND, notif);
        }
    }

    private void showCompletionNotification(long id, String title, File targetFile) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("url", Uri.fromFile(targetFile).toString());
        intent.putExtra("name", title);
        PendingIntent pi = PendingIntent.getActivity(
                this,
                (int) (id % Integer.MAX_VALUE),
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Notification notif = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_check)
                .setContentTitle(title)
                .setContentText(getString(R.string.download_status_completed))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify((int) (id % 100000 + 100), notif);
        }
    }

    private void handleStartDownload(Intent intent) {
        String url = intent.getStringExtra(EXTRA_URL);
        if (url == null || url.trim().isEmpty()) {
            checkStopService();
            return;
        }

        String rawTitle = intent.getStringExtra(EXTRA_TITLE);
        String iconUrl = intent.getStringExtra(EXTRA_ICON);
        long explicitId = intent.getLongExtra(EXTRA_ID, 0L);

        String displayTitle = (rawTitle != null && !rawTitle.trim().isEmpty()) ? rawTitle.trim() : LinkStore.autoName(url);
        String safeTitle = displayTitle.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        String ext = extractExtension(url);

        File dir = getDownloadsDir();
        if (!dir.exists()) {
            dir.mkdirs();
        }

        long id = (explicitId > 0) ? explicitId : System.currentTimeMillis();

        // Check if worker already exists
        if (workers.containsKey(id)) {
            return;
        }

        File targetFile = new File(dir, safeTitle + "." + ext);
        if (targetFile.exists() && explicitId <= 0) {
            targetFile = new File(dir, safeTitle + "_" + id + "." + ext);
        }
        File partFile = new File(dir, id + "_" + safeTitle + ".part");

        DownloadStore.getInstance(this).addOrUpdate(
                id,
                displayTitle,
                url,
                Uri.fromFile(targetFile).toString(),
                targetFile.getAbsolutePath(),
                DownloadStore.STATUS_RUNNING,
                0L,
                iconUrl
        );

        ensureForeground(displayTitle, getString(R.string.download_status_downloading, 0));
        startWorker(id, displayTitle, url, iconUrl, targetFile, partFile);
    }

    private void handlePauseDownload(Intent intent) {
        long id = intent.getLongExtra(EXTRA_ID, 0L);
        if (id <= 0) return;

        DownloadWorker worker = workers.remove(id);
        if (worker != null) {
            worker.pause();
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

        if (workers.containsKey(id)) {
            return;
        }

        JSONObject obj = DownloadStore.getInstance(this).get(id);
        if (obj == null) return;

        String url = obj.optString("url");
        if (url == null || url.trim().isEmpty()) return;

        String title = obj.optString("title", "فيديو محمل");
        String iconUrl = obj.optString("iconUrl");
        String filePath = obj.optString("filePath");

        String safeTitle = title.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        File dir = getDownloadsDir();
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File targetFile = (filePath != null && !filePath.isEmpty()) ? new File(filePath) : new File(dir, safeTitle + "." + extractExtension(url));
        File partFile = new File(dir, id + "_" + safeTitle + ".part");

        ensureForeground(title, getString(R.string.download_status_downloading, 0));
        startWorker(id, title, url, iconUrl, targetFile, partFile);
    }

    private void handleCancelDownload(Intent intent) {
        long id = intent.getLongExtra(EXTRA_ID, 0L);
        if (id <= 0) return;

        DownloadWorker worker = workers.remove(id);
        if (worker != null) {
            worker.cancel();
        }
        activeIds.remove(id);

        JSONObject obj = DownloadStore.getInstance(this).get(id);
        String title = (obj != null) ? obj.optString("title", "") : "";
        String safeTitle = title.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        File dir = getDownloadsDir();
        File partFile = new File(dir, id + "_" + safeTitle + ".part");
        if (partFile.exists()) {
            partFile.delete();
        }

        DownloadStore.getInstance(this).delete(id);
        checkStopService();
    }

    private void startWorker(long id, String title, String url, String iconUrl, File targetFile, File partFile) {
        DownloadWorker worker = new DownloadWorker(id, title, url, iconUrl, targetFile, partFile);
        workers.put(id, worker);
        activeIds.add(id);
        acquireWakeLock();
        executor.execute(worker);
    }

    private synchronized void checkStopService() {
        if (workers.isEmpty()) {
            if (isForegroundStarted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE);
                } else {
                    stopForeground(true);
                }
                isForegroundStarted = false;
            }
            releaseWakeLock();
            stopSelf();
        }
    }

    private synchronized void acquireWakeLock() {
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyPlyr:DownloadWakeLock");
                wakeLock.setReferenceCounted(false);
            }
        }
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(15 * 60 * 1000L); // 15 min safety timeout
        }
    }

    private synchronized void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception ignored) {
            }
        }
    }

    private void registerNetworkCallback() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null && networkCallback == null) {
                NetworkRequest request = new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build();
                networkCallback = new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(@NonNull Network network) {
                        for (DownloadWorker worker : workers.values()) {
                            synchronized (worker.retryLock) {
                                worker.retryLock.notifyAll();
                            }
                        }
                    }
                };
                cm.registerNetworkCallback(request, networkCallback);
            }
        } catch (Exception ignored) {
        }
    }

    private void unregisterNetworkCallback() {
        try {
            if (networkCallback != null) {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm != null) {
                    cm.unregisterNetworkCallback(networkCallback);
                }
                networkCallback = null;
            }
        } catch (Exception ignored) {
        }
    }

    private File getDownloadsDir() {
        File base = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (base == null) {
            base = getFilesDir();
        }
        return new File(base, "MyPlyr");
    }

    private static String extractExtension(String url) {
        String ext = "mp4";
        try {
            Uri parsed = Uri.parse(url);
            String path = parsed.getLastPathSegment();
            if (path != null && path.contains(".")) {
                int dot = path.lastIndexOf('.');
                String e = path.substring(dot + 1).toLowerCase();
                if (e.length() <= 4 && !e.contains("/")) {
                    ext = e;
                }
            }
        } catch (Exception ignored) {
        }
        return ext;
    }

    private static void copyFile(File src, File dst) throws IOException {
        try (InputStream in = new java.io.FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[32768];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }

    private class DownloadWorker implements Runnable {
        private final long id;
        private final String title;
        private final String url;
        private final String iconUrl;
        private final File targetFile;
        private final File partFile;
        private volatile boolean isPaused = false;
        private volatile boolean isCancelled = false;
        final Object retryLock = new Object();

        DownloadWorker(long id, String title, String url, String iconUrl, File targetFile, File partFile) {
            this.id = id;
            this.title = title;
            this.url = url;
            this.iconUrl = iconUrl;
            this.targetFile = targetFile;
            this.partFile = partFile;
        }

        void pause() {
            isPaused = true;
            synchronized (retryLock) {
                retryLock.notifyAll();
            }
        }

        void cancel() {
            isCancelled = true;
            synchronized (retryLock) {
                retryLock.notifyAll();
            }
        }

        @Override
        public void run() {
            int retryDelayIndex = 0;
            final int[] retryDelays = {3000, 5000, 10000, 30000};

            try {
                while (!isPaused && !isCancelled) {
                    long offset = (partFile.exists()) ? partFile.length() : 0L;
                    HttpURLConnection conn = null;
                    InputStream in = null;
                    FileOutputStream out = null;
                    boolean downloadCompleted = false;

                    try {
                        updateNotificationProgress(title, offset, -1);
                        URL requestUrl = new URL(url);
                        conn = openConnectionWithRedirects(requestUrl, offset);

                        int code = conn.getResponseCode();
                        long totalBytes = -1;
                        boolean append = false;

                        if (code == HttpURLConnection.HTTP_PARTIAL) { // 206
                            append = true;
                            String contentRange = conn.getHeaderField("Content-Range");
                            if (contentRange != null && contentRange.contains("/")) {
                                try {
                                    String totalStr = contentRange.substring(contentRange.lastIndexOf('/') + 1).trim();
                                    totalBytes = Long.parseLong(totalStr);
                                } catch (Exception ignored) {
                                }
                            }
                            if (totalBytes <= 0) {
                                long cl = conn.getContentLengthLong();
                                if (cl > 0) totalBytes = offset + cl;
                            }
                        } else if (code == HttpURLConnection.HTTP_OK) { // 200
                            offset = 0;
                            append = false;
                            totalBytes = conn.getContentLengthLong();
                        } else if (code == 416) { // Range Not Satisfiable
                            if (offset > 0) {
                                if (partFile.exists()) partFile.delete();
                                offset = 0;
                                continue;
                            } else {
                                throw new IOException("HTTP 416 Range Not Satisfiable");
                            }
                        } else {
                            throw new IOException("HTTP error response: " + code);
                        }

                        if (totalBytes <= 0) {
                            JSONObject st = DownloadStore.getInstance(DownloadService.this).get(id);
                            if (st != null) {
                                totalBytes = st.optLong("totalBytes", 0);
                            }
                        }

                        DownloadStore.getInstance(DownloadService.this).updateProgress(
                                id,
                                DownloadStore.STATUS_RUNNING,
                                offset,
                                totalBytes,
                                Uri.fromFile(targetFile).toString(),
                                targetFile.getAbsolutePath()
                        );

                        in = conn.getInputStream();
                        out = new FileOutputStream(partFile, append);
                        byte[] buffer = new byte[32768];
                        long downloadedBytes = offset;
                        long lastProgressTime = System.currentTimeMillis();

                        // Reset retry backoff upon successful read stream start
                        retryDelayIndex = 0;

                        while (!isPaused && !isCancelled) {
                            int read = in.read(buffer);
                            if (read == -1) {
                                break;
                            }
                            out.write(buffer, 0, read);
                            downloadedBytes += read;

                            long now = System.currentTimeMillis();
                            if (now - lastProgressTime >= 1000) {
                                lastProgressTime = now;
                                DownloadStore.getInstance(DownloadService.this).updateProgress(
                                        id,
                                        DownloadStore.STATUS_RUNNING,
                                        downloadedBytes,
                                        totalBytes,
                                        Uri.fromFile(targetFile).toString(),
                                        targetFile.getAbsolutePath()
                                );
                                updateNotificationProgress(title, downloadedBytes, totalBytes);
                            }
                        }
                        out.flush();

                        if (isPaused || isCancelled) {
                            break;
                        }

                        if (totalBytes > 0 && downloadedBytes < totalBytes) {
                            throw new IOException("Incomplete download stream: " + downloadedBytes + " / " + totalBytes);
                        }

                        downloadCompleted = true;

                    } catch (IOException e) {
                        if (isPaused || isCancelled) {
                            break;
                        }

                        long currentBytes = partFile.exists() ? partFile.length() : 0L;
                        DownloadStore.getInstance(DownloadService.this).updateProgress(
                                id,
                                DownloadStore.STATUS_PAUSED,
                                currentBytes,
                                -1,
                                Uri.fromFile(targetFile).toString(),
                                targetFile.getAbsolutePath()
                        );

                        updateNotificationMessage(title, getString(R.string.download_waiting_network));

                        int delay = retryDelays[Math.min(retryDelayIndex, retryDelays.length - 1)];
                        if (retryDelayIndex < retryDelays.length - 1) {
                            retryDelayIndex++;
                        }

                        synchronized (retryLock) {
                            try {
                                retryLock.wait(delay);
                            } catch (InterruptedException ie) {
                                // Interrupted on network available or user pause/cancel
                            }
                        }
                    } finally {
                        try {
                            if (in != null) in.close();
                        } catch (Exception ignored) {
                        }
                        try {
                            if (out != null) out.close();
                        } catch (Exception ignored) {
                        }
                        if (conn != null) conn.disconnect();
                    }

                    if (downloadCompleted) {
                        if (targetFile.exists()) {
                            targetFile.delete();
                        }
                        boolean renamed = partFile.renameTo(targetFile);
                        if (!renamed) {
                            try {
                                copyFile(partFile, targetFile);
                                partFile.delete();
                            } catch (Exception ignored) {
                            }
                        }

                        long finalSize = targetFile.exists() ? targetFile.length() : 0L;
                        DownloadStore.getInstance(DownloadService.this).updateProgress(
                                id,
                                DownloadStore.STATUS_SUCCESSFUL,
                                finalSize,
                                finalSize,
                                Uri.fromFile(targetFile).toString(),
                                targetFile.getAbsolutePath()
                        );
                        showCompletionNotification(id, title, targetFile);
                        break;
                    }
                }

                if (isCancelled) {
                    if (partFile.exists()) partFile.delete();
                    DownloadStore.getInstance(DownloadService.this).delete(id);
                } else if (isPaused) {
                    long currentBytes = partFile.exists() ? partFile.length() : 0L;
                    DownloadStore.getInstance(DownloadService.this).updateProgress(
                            id,
                            DownloadStore.STATUS_PAUSED,
                            currentBytes,
                            -1,
                            Uri.fromFile(targetFile).toString(),
                            targetFile.getAbsolutePath()
                    );
                }

            } finally {
                activeIds.remove(id);
                workers.remove(id);
                releaseWakeLock();
                checkStopService();
            }
        }

        private HttpURLConnection openConnectionWithRedirects(URL initialUrl, long offset) throws IOException {
            URL currentUrl = initialUrl;
            for (int redirects = 0; redirects < 5; redirects++) {
                HttpURLConnection conn = (HttpURLConnection) currentUrl.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("User-Agent", USER_AGENT);
                conn.setInstanceFollowRedirects(false);

                if (offset > 0) {
                    conn.setRequestProperty("Range", "bytes=" + offset + "-");
                }

                conn.connect();
                int code = conn.getResponseCode();
                if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP
                        || code == HttpURLConnection.HTTP_SEE_OTHER || code == 307 || code == 308) {
                    String location = conn.getHeaderField("Location");
                    conn.disconnect();
                    if (location != null) {
                        currentUrl = new URL(currentUrl, location);
                        continue;
                    }
                }
                return conn;
            }
            throw new IOException("Too many redirects");
        }
    }
}
