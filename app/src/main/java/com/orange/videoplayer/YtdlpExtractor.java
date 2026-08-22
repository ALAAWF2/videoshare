package com.orange.videoplayer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.YoutubeDLResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Universal metadata and formats extractor using the embedded yt-dlp engine.
 * Supports YouTube, Instagram, TikTok, Facebook, Twitter/X, and 1000+ streaming sites.
 */
public final class YtdlpExtractor {

    private static final String TAG = "YtdlpExtractor";
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public static class FormatItem {
        public final String formatId;
        public final String ext;
        public final int height;
        public final int width;
        public final int fps;
        public final long filesize; // in bytes (0 if unknown)
        public final double tbr;    // total bitrate in kbps
        public final double abr;    // audio bitrate in kbps
        public final String vcodec;
        public final String acodec;
        public final String formatNote;
        public final boolean isVideo;
        public final boolean isAudioOnly;
        public final boolean hasAudio; // true if progressive stream

        public FormatItem(String formatId, String ext, int height, int width, int fps,
                          long filesize, double tbr, double abr, String vcodec, String acodec,
                          String formatNote, boolean isVideo, boolean isAudioOnly, boolean hasAudio) {
            this.formatId = formatId;
            this.ext = ext != null ? ext : "mp4";
            this.height = height;
            this.width = width;
            this.fps = fps;
            this.filesize = filesize;
            this.tbr = tbr;
            this.abr = abr;
            this.vcodec = vcodec;
            this.acodec = acodec;
            this.formatNote = formatNote != null ? formatNote : "";
            this.isVideo = isVideo;
            this.isAudioOnly = isAudioOnly;
            this.hasAudio = hasAudio;
        }

        public String getDisplayQuality() {
            if (isAudioOnly) {
                if (abr > 0) {
                    return String.format(Locale.US, "صوت (%d kbps)", Math.round(abr));
                }
                return "صوت عالي الجودة (" + ext.toUpperCase(Locale.US) + ")";
            }
            if (height > 0) {
                String label = height + "p";
                if (fps > 30) label += fps;
                if (height >= 2160) label += " (4K UHD)";
                else if (height >= 1440) label += " (2K QHD)";
                else if (height >= 1080) label += " (Full HD)";
                else if (height >= 720) label += " (HD)";
                return label;
            }
            return !formatNote.isEmpty() ? formatNote : "فيديو";
        }

        public String getFormattedSize() {
            if (filesize <= 0) return "";
            if (filesize < 1024 * 1024) {
                return String.format(Locale.US, "%.1f KB", filesize / 1024.0);
            }
            if (filesize < 1024 * 1024 * 1024) {
                return String.format(Locale.US, "%.1f MB", filesize / (1024.0 * 1024.0));
            }
            return String.format(Locale.US, "%.2f GB", filesize / (1024.0 * 1024.0 * 1024.0));
        }
    }

    public static class MediaInfo {
        public final String title;
        public final String webpageUrl;
        public final String extractor;
        public final String thumbnailUrl;
        public final long durationSeconds;
        public final String uploader;
        public final List<FormatItem> videoFormats;
        public final List<FormatItem> audioFormats;

        public MediaInfo(String title, String webpageUrl, String extractor,
                         String thumbnailUrl, long durationSeconds, String uploader,
                         List<FormatItem> videoFormats, List<FormatItem> audioFormats) {
            this.title = title != null && !title.trim().isEmpty() ? title.trim() : "فيديو";
            this.webpageUrl = webpageUrl;
            this.extractor = extractor != null ? extractor : "General";
            this.thumbnailUrl = thumbnailUrl;
            this.durationSeconds = durationSeconds;
            this.uploader = uploader;
            this.videoFormats = videoFormats != null ? videoFormats : Collections.emptyList();
            this.audioFormats = audioFormats != null ? audioFormats : Collections.emptyList();
        }

        public String getPlatformName() {
            if (extractor == null) return "فيديو";
            String lower = extractor.toLowerCase(Locale.ROOT);
            if (lower.contains("youtube")) return "يوتيوب";
            if (lower.contains("instagram")) return "انستغرام";
            if (lower.contains("tiktok")) return "تيك توك";
            if (lower.contains("twitter") || lower.contains("x")) return "تويتر / X";
            if (lower.contains("facebook")) return "فيسبوك";
            if (lower.contains("reddit")) return "ريديت";
            if (lower.contains("vimeo")) return "فيميو";
            if (lower.contains("twitch")) return "تويتش";
            if (lower.contains("soundcloud")) return "ساوند كلاود";
            if (lower.contains("dailymotion")) return "ديلي موشن";
            if (lower.contains("pinterest")) return "بينترست";
            return extractor;
        }

        public String getFormattedDuration() {
            if (durationSeconds <= 0) return "";
            long minutes = durationSeconds / 60;
            long seconds = durationSeconds % 60;
            long hours = minutes / 60;
            minutes = minutes % 60;
            if (hours > 0) {
                return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
            }
            return String.format(Locale.US, "%d:%02d", minutes, seconds);
        }
    }

    public interface Callback {
        void onSuccess(MediaInfo mediaInfo);
        void onError(String arabicMessage);
    }

    private YtdlpExtractor() {}

    /**
     * Extracts full metadata and format list for any URL asynchronously.
     */
    public static void extract(Context context, String url, Callback callback) {
        if (url == null || url.trim().isEmpty()) {
            postError(callback, "يرجى إدخال رابط صالح");
            return;
        }

        EXECUTOR.execute(() -> {
            MediaInfo result = runExtraction(context, url.trim());
            if (result != null) {
                postSuccess(callback, result);
                return;
            }

            // Retry after updating yt-dlp binary if first attempt failed
            Log.w(TAG, "First extraction failed. Attempting binary update & retry...");
            boolean updated = YtdlpUpdater.updateSync(context);
            if (updated) {
                result = runExtraction(context, url.trim());
                if (result != null) {
                    postSuccess(callback, result);
                    return;
                }
            }

            postError(callback, "تعذر استخراج بيانات الفيديو من الرابط، تأكد من صحة الرابط أو جرب موقع آخر");
        });
    }

    private static MediaInfo runExtraction(Context context, String url) {
        try {
            try {
                YoutubeDL.getInstance().init(context.getApplicationContext());
            } catch (Exception ignored) {
            }

            YoutubeDLRequest req = new YoutubeDLRequest(url);
            req.addOption("--dump-single-json");
            req.addOption("--no-playlist");
            req.addOption("--no-warnings");
            req.addOption("--no-update");
            req.addOption("--ignore-errors");
            req.addOption("--extractor-args", "youtube:player_client=ios,web,mweb,android");

            YoutubeDLResponse resp = YoutubeDL.getInstance().execute(req, null, null);
            String output = resp.getOut();
            if (output == null || output.trim().isEmpty()) {
                return null;
            }

            JSONObject root = new JSONObject(output.trim());
            if (root.has("entries")) {
                JSONArray entries = root.optJSONArray("entries");
                if (entries != null && entries.length() > 0) {
                    JSONObject first = entries.optJSONObject(0);
                    if (first != null) {
                        root = first;
                    }
                }
            }

            String title = root.optString("title", "فيديو");
            String webpageUrl = root.optString("webpage_url", url);
            String extractor = root.optString("extractor_key", root.optString("extractor", "General"));
            String thumbnail = root.optString("thumbnail", null);
            long duration = root.optLong("duration", 0L);
            String uploader = root.optString("uploader", root.optString("channel", null));

            List<FormatItem> allFormats = parseFormats(root.optJSONArray("formats"));

            Map<Integer, FormatItem> bestVideoByHeight = new HashMap<>();
            List<FormatItem> audioFormats = new ArrayList<>();

            for (FormatItem f : allFormats) {
                if (f.isAudioOnly) {
                    audioFormats.add(f);
                } else if (f.isVideo && f.height > 0) {
                    FormatItem existing = bestVideoByHeight.get(f.height);
                    if (existing == null || f.tbr > existing.tbr || (f.fps > existing.fps && f.tbr >= existing.tbr * 0.8)) {
                        if (existing == null || "mp4".equalsIgnoreCase(f.ext) || f.tbr > existing.tbr * 1.3) {
                            bestVideoByHeight.put(f.height, f);
                        }
                    }
                }
            }

            List<FormatItem> videoFormats = new ArrayList<>(bestVideoByHeight.values());
            Collections.sort(videoFormats, (a, b) -> {
                if (b.height != a.height) return Integer.compare(b.height, a.height);
                if (b.fps != a.fps) return Integer.compare(b.fps, a.fps);
                return Double.compare(b.tbr, a.tbr);
            });

            Collections.sort(audioFormats, (a, b) -> Double.compare(b.abr, a.abr));

            return new MediaInfo(title, webpageUrl, extractor, thumbnail, duration, uploader, videoFormats, audioFormats);
        } catch (Exception e) {
            Log.e(TAG, "runExtraction error: " + e.getMessage());
            return null;
        }
    }

    private static List<FormatItem> parseFormats(JSONArray formatsArr) {
        List<FormatItem> list = new ArrayList<>();
        if (formatsArr == null) return list;

        for (int i = 0; i < formatsArr.length(); i++) {
            JSONObject fo = formatsArr.optJSONObject(i);
            if (fo == null) continue;

            String formatId = fo.optString("format_id", String.valueOf(i));
            String ext = fo.optString("ext", "mp4");
            int height = fo.optInt("height", 0);
            int width = fo.optInt("width", 0);
            int fps = fo.optInt("fps", 0);
            long filesize = fo.optLong("filesize", fo.optLong("filesize_approx", 0L));
            double tbr = fo.optDouble("tbr", 0.0);
            double abr = fo.optDouble("abr", 0.0);
            String vcodec = fo.optString("vcodec", "none");
            String acodec = fo.optString("acodec", "none");
            String formatNote = fo.optString("format_note", "");

            boolean hasVideoCodec = vcodec != null && !vcodec.isEmpty() && !"none".equalsIgnoreCase(vcodec);
            boolean hasAudioCodec = acodec != null && !acodec.isEmpty() && !"none".equalsIgnoreCase(acodec);

            boolean isAudioOnly = !hasVideoCodec && hasAudioCodec;
            boolean isVideo = hasVideoCodec;
            boolean hasAudio = hasVideoCodec && hasAudioCodec;

            if (ext.equalsIgnoreCase("mhtml") || formatId.startsWith("sb") || (!hasVideoCodec && !hasAudioCodec)) {
                continue;
            }

            list.add(new FormatItem(formatId, ext, height, width, fps, filesize, tbr, abr,
                    vcodec, acodec, formatNote, isVideo, isAudioOnly, hasAudio));
        }

        return list;
    }

    private static void postSuccess(Callback callback, MediaInfo info) {
        if (callback != null) {
            MAIN_HANDLER.post(() -> callback.onSuccess(info));
        }
    }

    private static void postError(Callback callback, String message) {
        if (callback != null) {
            MAIN_HANDLER.post(() -> callback.onError(message));
        }
    }
}
