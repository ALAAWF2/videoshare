package com.orange.videoplayer;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class WatchPartyWebPlayer {

    // Live hosted GitHub Pages Web Player for iOS Safari & Web
    private static final String HOSTED_WEB_PLAYER_URL = "https://alaawf2.github.io/videoshare/";

    public static String getShareableWebUrl(String roomId, String videoUrl, String videoTitle) {
        if (roomId == null) roomId = "WP-1001";
        String encodedUrl = "";
        String encodedTitle = "";
        try {
            if (videoUrl != null) encodedUrl = URLEncoder.encode(videoUrl, "UTF-8");
            if (videoTitle != null) encodedTitle = URLEncoder.encode(videoTitle, "UTF-8");
        } catch (UnsupportedEncodingException ignored) {
            if (videoUrl != null) encodedUrl = videoUrl;
            if (videoTitle != null) encodedTitle = videoTitle;
        }

        return HOSTED_WEB_PLAYER_URL + "?room=" + roomId + "&src=" + encodedUrl + "&title=" + encodedTitle;
    }

    public static String buildFullShareMessage(String roomId, String videoUrl, String videoTitle) {
        StringBuilder sb = new StringBuilder();
        sb.append("🍿 دعوة للمشاهدة الجماعية على MyPlyr\n");
        if (videoTitle != null && !videoTitle.isEmpty()) {
            sb.append("🎬 ").append(videoTitle).append("\n");
        }
        sb.append("🔑 رمز الغرفة: ").append(roomId).append("\n\n");

        if (videoUrl != null && !videoUrl.isEmpty()) {
            if (videoUrl.startsWith("http://") || videoUrl.startsWith("https://")) {
                String webPlayerUrl = getShareableWebUrl(roomId, videoUrl, videoTitle);
                sb.append("🌐 رابط المشاهدة المباشر للآيفون والكمبيوتر (متزامن):\n")
                  .append(webPlayerUrl).append("\n\n");
                sb.append("▶️ رابط البث المباشر الخام:\n").append(videoUrl);
            } else {
                sb.append("📁 ملاحظة: هذا الفيديو مخزن محلياً على الهاتف.");
            }
        }

        return sb.toString();
    }
}
