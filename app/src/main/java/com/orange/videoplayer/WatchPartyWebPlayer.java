package com.orange.videoplayer;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class WatchPartyWebPlayer {

    // Universal public web video player that is live and works on iOS Safari & PC
    private static final String PUBLIC_WEB_PLAYER = "https://clappr.io/demo/";

    public static String getShareableWebUrl(String roomId, String videoUrl, String videoTitle) {
        if (roomId == null) roomId = "WP-1001";
        if (videoUrl == null || videoUrl.isEmpty()) return "";

        // If it's an online HTTP/HTTPS stream, return direct stream URL or web player
        if (videoUrl.startsWith("http://") || videoUrl.startsWith("https://")) {
            return videoUrl;
        }

        return videoUrl;
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
                sb.append("▶️ رابط المشاهدة المباشر (للآيفون والكمبيوتر):\n").append(videoUrl).append("\n\n");
                try {
                    String encoded = URLEncoder.encode(videoUrl, "UTF-8");
                    sb.append("🌐 مشغل الويب المتزامن:\n")
                      .append(PUBLIC_WEB_PLAYER).append("?src=").append(encoded);
                } catch (UnsupportedEncodingException ignored) {
                }
            } else {
                sb.append("📁 ملاحظة: هذا الفيديو مخزن محلياً على الهاتف.");
            }
        }

        return sb.toString();
    }
}
