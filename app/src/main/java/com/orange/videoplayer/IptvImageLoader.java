package com.orange.videoplayer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class IptvImageLoader {

    private static IptvImageLoader instance;

    private final LruCache<String, Bitmap> memoryCache;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private IptvImageLoader() {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = Math.max(1024 * 4, maxMemory / 8); // At least 4MB or 1/8th of memory
        memoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    public static synchronized IptvImageLoader getInstance() {
        if (instance == null) {
            instance = new IptvImageLoader();
        }
        return instance;
    }

    public void load(ImageView imageView, String url, int placeholderResId) {
        if (imageView == null) return;

        if (url == null || url.trim().isEmpty()) {
            imageView.setTag(null);
            if (placeholderResId != 0) {
                imageView.setImageResource(placeholderResId);
            }
            return;
        }

        final String trimmedUrl = url.trim();
        imageView.setTag(trimmedUrl);

        Bitmap cached = memoryCache.get(trimmedUrl);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }

        if (placeholderResId != 0) {
            imageView.setImageResource(placeholderResId);
        }

        executor.execute(() -> {
            Bitmap bitmap = downloadBitmap(trimmedUrl);
            if (bitmap != null) {
                memoryCache.put(trimmedUrl, bitmap);
                mainHandler.post(() -> {
                    if (trimmedUrl.equals(imageView.getTag())) {
                        imageView.setImageBitmap(bitmap);
                    }
                });
            }
        });
    }

    private Bitmap downloadBitmap(String urlStr) {
        HttpURLConnection conn = null;
        InputStream is = null;
        ByteArrayOutputStream baos = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:40.0) Gecko/40.0 Firefox/40.0");
            conn.setInstanceFollowRedirects(true);
            conn.connect();

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                is = conn.getInputStream();
                baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, read);
                }
                byte[] data = baos.toByteArray();

                // Decode bitmap with bounds check first to avoid OOM
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, options);

                // Target max dimension ~256px for channel/poster thumbnail
                int maxDim = Math.max(options.outWidth, options.outHeight);
                int inSampleSize = 1;
                while (maxDim / (inSampleSize * 2) >= 128) {
                    inSampleSize *= 2;
                }

                options.inJustDecodeBounds = false;
                options.inSampleSize = inSampleSize;
                return BitmapFactory.decodeByteArray(data, 0, data.length, options);
            }
        } catch (Exception ignored) {
        } finally {
            try {
                if (baos != null) baos.close();
            } catch (Exception ignored) {
            }
            try {
                if (is != null) is.close();
            } catch (Exception ignored) {
            }
            if (conn != null) conn.disconnect();
        }
        return null;
    }
}
