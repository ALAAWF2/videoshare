package com.orange.videoplayer;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.LruCache;
import android.util.Size;
import android.widget.ImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalMediaScanner {

    public static class ScanResult {
        public final List<LocalMediaItem> videos;
        public final List<LocalMediaItem> audios;
        public final List<LocalMediaItem> all;

        public ScanResult(List<LocalMediaItem> videos, List<LocalMediaItem> audios, List<LocalMediaItem> all) {
            this.videos = videos;
            this.audios = audios;
            this.all = all;
        }
    }

    public interface ScanCallback {
        void onScanComplete(ScanResult result);
    }

    private static final ExecutorService executor = Executors.newFixedThreadPool(3);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> thumbCache = new LruCache<>(80);

    public static void scanMediaAsync(Context context, ScanCallback callback) {
        if (context == null || callback == null) return;
        Context appCtx = context.getApplicationContext();

        executor.execute(() -> {
            List<LocalMediaItem> videos = scanVideos(appCtx);
            List<LocalMediaItem> audios = scanAudios(appCtx);

            List<LocalMediaItem> all = new ArrayList<>();
            all.addAll(videos);
            all.addAll(audios);
            Collections.sort(all, (a, b) -> Long.compare(b.dateModified, a.dateModified));

            ScanResult result = new ScanResult(videos, audios, all);
            mainHandler.post(() -> callback.onScanComplete(result));
        });
    }

    public static List<LocalMediaItem> scanVideos(Context context) {
        List<LocalMediaItem> list = new ArrayList<>();
        if (context == null) return list;

        Uri collection = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                : MediaStore.Video.Media.EXTERNAL_CONTENT_URI;

        String[] projection = new String[]{
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        };

        String sortOrder = MediaStore.Video.Media.DATE_MODIFIED + " DESC";

        try (Cursor cursor = context.getContentResolver().query(collection, projection, null, null, sortOrder)) {
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                int nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME);
                int dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA);
                int durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION);
                int sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE);
                int dateCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED);
                int bucketCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idCol);
                    String name = (nameCol >= 0) ? cursor.getString(nameCol) : null;
                    String path = (dataCol >= 0) ? cursor.getString(dataCol) : null;
                    long duration = (durCol >= 0) ? cursor.getLong(durCol) : 0L;
                    long size = (sizeCol >= 0) ? cursor.getLong(sizeCol) : 0L;
                    long dateModified = (dateCol >= 0) ? cursor.getLong(dateCol) : 0L;
                    String bucket = (bucketCol >= 0) ? cursor.getString(bucketCol) : "";

                    if (name == null || name.isEmpty()) {
                        if (path != null) {
                            name = new File(path).getName();
                        } else {
                            name = "فيديو " + id;
                        }
                    }

                    Uri contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
                    list.add(new LocalMediaItem(id, name, path, contentUri, duration, size, dateModified, bucket, "", true));
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public static List<LocalMediaItem> scanAudios(Context context) {
        List<LocalMediaItem> list = new ArrayList<>();
        if (context == null) return list;

        Uri collection = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        String[] projection = new String[]{
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_MODIFIED
        };

        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        String sortOrder = MediaStore.Audio.Media.DATE_MODIFIED + " DESC";

        try (Cursor cursor = context.getContentResolver().query(collection, projection, selection, null, sortOrder)) {
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int nameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME);
                int titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
                int dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
                int artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
                int durCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
                int sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE);
                int dateCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idCol);
                    String name = (nameCol >= 0) ? cursor.getString(nameCol) : null;
                    String title = (titleCol >= 0) ? cursor.getString(titleCol) : null;
                    String path = (dataCol >= 0) ? cursor.getString(dataCol) : null;
                    String artist = (artistCol >= 0) ? cursor.getString(artistCol) : "";
                    long duration = (durCol >= 0) ? cursor.getLong(durCol) : 0L;
                    long size = (sizeCol >= 0) ? cursor.getLong(sizeCol) : 0L;
                    long dateModified = (dateCol >= 0) ? cursor.getLong(dateCol) : 0L;

                    String displayName = (title != null && !title.isEmpty()) ? title : name;
                    if (displayName == null || displayName.isEmpty()) {
                        if (path != null) {
                            displayName = new File(path).getName();
                        } else {
                            displayName = "مقطع صوتي " + id;
                        }
                    }

                    Uri contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                    list.add(new LocalMediaItem(id, displayName, path, contentUri, duration, size, dateModified, "", artist, false));
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public static void loadThumbnail(Context context, LocalMediaItem item, ImageView imageView) {
        if (imageView == null || item == null) return;

        if (!item.isVideo) {
            imageView.setImageResource(R.drawable.ic_audio);
            imageView.setTag(null);
            return;
        }

        String key = "vid_" + item.id;
        imageView.setTag(key);

        Bitmap cached = thumbCache.get(key);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }

        imageView.setImageResource(R.drawable.ic_video);

        executor.execute(() -> {
            Bitmap thumb = null;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && item.contentUri != null) {
                    thumb = context.getContentResolver().loadThumbnail(item.contentUri, new Size(160, 160), null);
                } else if (item.path != null && !item.path.isEmpty()) {
                    thumb = ThumbnailUtils.createVideoThumbnail(item.path, MediaStore.Images.Thumbnails.MINI_KIND);
                }
            } catch (Exception ignored) {
            }

            if (thumb != null) {
                thumbCache.put(key, thumb);
                final Bitmap finalThumb = thumb;
                mainHandler.post(() -> {
                    if (key.equals(imageView.getTag())) {
                        imageView.setImageBitmap(finalThumb);
                    }
                });
            }
        });
    }
}
