package com.orange.videoplayer;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class WatchPartyWebPlayer {

    // Live hosted Vercel Web Player for iOS Safari & Web
    private static final String HOSTED_WEB_PLAYER_URL = "https://videoshare-one.vercel.app/";

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
                sb.append("🌐 الطريقة 1: التشغيل المباشر في Safari (للآيفون):\n")
                  .append(videoUrl).append("\n\n");

                String vlcUrl = "vlc://" + videoUrl;
                sb.append("📱 الطريقة 2: التشغيل التلقائي عبر تطبيق VLC (للآيفون بضغطة واحدة):\n")
                  .append(vlcUrl);
            } else {
                sb.append("📁 ملاحظة: هذا الفيديو مخزن محلياً على الهاتف.");
            }
        }

        return sb.toString();
    }

    public static void shareHtmlPlayerFile(Context context, String roomId, String videoUrl, String videoTitle) {
        try {
            String htmlContent = getSelfContainedHtml(roomId, videoUrl, videoTitle);
            File cacheDir = new File(context.getCacheDir(), "shared_players");
            if (!cacheDir.exists()) cacheDir.mkdirs();

            File htmlFile = new File(cacheDir, "MyPlyr-WatchParty.html");
            FileOutputStream fos = new FileOutputStream(htmlFile);
            fos.write(htmlContent.getBytes("UTF-8"));
            fos.flush();
            fos.close();

            Uri contentUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    htmlFile
            );

            String shareSubject = "مشغل المشاهدة الجماعية - " + (videoTitle != null ? videoTitle : "MyPlyr");
            String shareBody = "🍿 دعوة للمشاهدة الجماعية على MyPlyr\n" +
                    (videoTitle != null && !videoTitle.isEmpty() ? "🎬 " + videoTitle + "\n" : "") +
                    "🔑 رمز الغرفة: " + roomId + "\n\n" +
                    "📱 طريقة التشغيل على الآيفون في ثانيتين:\n" +
                    "1️⃣ افتح الملف المرفق واضغط زر المشاركة (📤) بالأعلى.\n" +
                    "2️⃣ اختر متصفح Safari.\n" +
                    "3️⃣ اضغط زر التشغيل للبدء بالمشاهدة المتزامنة فوراً! 🍿🎬";

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/html");
            intent.putExtra(Intent.EXTRA_STREAM, contentUri);
            intent.putExtra(Intent.EXTRA_SUBJECT, shareSubject);
            intent.putExtra(Intent.EXTRA_TEXT, shareBody);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            context.startActivity(Intent.createChooser(intent, "إرسال ملف المشغل للآيفون (واتساب / تيليجرام)"));
        } catch (Exception e) {
            Toast.makeText(context, "تعذر إنشاء ملف المشغل: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public static String getSelfContainedHtml(String roomId, String videoUrl, String videoTitle) {
        String safeRoom = (roomId != null) ? roomId : "WP-1001";
        String safeUrl = (videoUrl != null) ? videoUrl.replace("\"", "\\\"") : "";
        String safeTitle = (videoTitle != null) ? videoTitle.replace("\"", "\\\"") : "مشاهدة متزامنة";

        return "<!DOCTYPE html>\n" +
                "<html lang=\"ar\" dir=\"rtl\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">\n" +
                "    <title>" + safeTitle + " - MyPlyr Watch Party</title>\n" +
                "    <script src=\"https://cdn.jsdelivr.net/npm/hls.js@latest\"></script>\n" +
                "    <style>\n" +
                "        :root { --bg-dark: #090D16; --card-bg: #141B2D; --accent: #E50914; --text: #FFFFFF; --text-dim: #8B949E; --border: #21262D; }\n" +
                "        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; -webkit-tap-highlight-color: transparent; }\n" +
                "        body { background: var(--bg-dark); color: var(--text); display: flex; flex-direction: column; height: 100vh; height: 100dvh; overflow: hidden; }\n" +
                "        header { background: rgba(20, 27, 45, 0.95); padding: 12px 18px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid var(--border); }\n" +
                "        .room-badge { background: rgba(46, 160, 67, 0.15); color: #3FB950; border: 1px solid rgba(46, 160, 67, 0.4); padding: 4px 12px; border-radius: 20px; font-size: 13px; font-weight: bold; display: flex; align-items: center; gap: 6px; }\n" +
                "        .status-dot { width: 8px; height: 8px; border-radius: 50%; background: #3FB950; box-shadow: 0 0 8px #3FB950; }\n" +
                "        .player-wrap { position: relative; flex: 1; display: flex; align-items: center; justify-content: center; background: #000; overflow: hidden; }\n" +
                "        video { width: 100%; height: 100%; object-fit: contain; }\n" +
                "        .sync-badge { position: absolute; top: 14px; left: 14px; background: rgba(13, 17, 23, 0.85); border: 1px solid var(--border); padding: 6px 14px; border-radius: 20px; font-size: 12px; color: var(--text-dim); display: flex; align-items: center; gap: 6px; pointer-events: none; }\n" +
                "        .flying-emoji { position: absolute; bottom: 70px; font-size: 42px; animation: flyUp 2.4s cubic-bezier(0.25, 1, 0.5, 1) forwards; pointer-events: none; }\n" +
                "        @keyframes flyUp { 0% { opacity: 0; transform: translateY(20px) scale(0.6); } 15% { opacity: 1; transform: translateY(0) scale(1.2); } 80% { opacity: 0.9; } 100% { opacity: 0; transform: translateY(-340px) scale(1.6); } }\n" +
                "        .reactions-container { background: rgba(20, 27, 45, 0.95); padding: 10px 16px; display: flex; align-items: center; justify-content: center; gap: 12px; border-top: 1px solid var(--border); }\n" +
                "        .emoji-btn { background: var(--card-bg); border: 1px solid var(--border); border-radius: 50%; width: 46px; height: 46px; font-size: 22px; cursor: pointer; display: flex; align-items: center; justify-content: center; transition: transform 0.15s; outline: none; }\n" +
                "        .emoji-btn:active { transform: scale(1.35); }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <header>\n" +
                "        <div style=\"font-weight: bold; font-size: 16px;\">🍿 " + safeTitle + "</div>\n" +
                "        <div class=\"room-badge\"><div class=\"status-dot\" id=\"statusDot\"></div><span id=\"roomCodeText\">" + safeRoom + "</span></div>\n" +
                "    </header>\n" +
                "    <div class=\"player-wrap\" id=\"playerWrap\">\n" +
                "        <div class=\"sync-badge\" id=\"syncStatus\"><span>🟢</span> <span id=\"syncText\">متصل ومتزامن ✓</span></div>\n" +
                "        <video id=\"videoPlayer\" controls playsinline webkit-playsinline></video>\n" +
                "    </div>\n" +
                "    <div class=\"reactions-container\">\n" +
                "        <button class=\"emoji-btn\" onclick=\"sendReaction('🔥')\">🔥</button>\n" +
                "        <button class=\"emoji-btn\" onclick=\"sendReaction('❤️')\">❤️</button>\n" +
                "        <button class=\"emoji-btn\" onclick=\"sendReaction('😂')\">😂</button>\n" +
                "        <button class=\"emoji-btn\" onclick=\"sendReaction('👏')\">👏</button>\n" +
                "        <button class=\"emoji-btn\" onclick=\"sendReaction('🍿')\">🍿</button>\n" +
                "        <button class=\"emoji-btn\" onclick=\"sendReaction('😱')\">😱</button>\n" +
                "    </div>\n" +
                "    <script>\n" +
                "        const room = '" + safeRoom + "';\n" +
                "        const videoSrc = '" + safeUrl + "';\n" +
                "        const video = document.getElementById('videoPlayer');\n" +
                "        const playerWrap = document.getElementById('playerWrap');\n" +
                "        const syncText = document.getElementById('syncText');\n" +
                "        const statusDot = document.getElementById('statusDot');\n" +
                "        \n" +
                "        if (Hls.isSupported() && (videoSrc.includes('.m3u8') || videoSrc.includes('ts'))) {\n" +
                "            const hls = new Hls({ enableWorker: true, lowLatencyMode: true });\n" +
                "            hls.loadSource(videoSrc);\n" +
                "            hls.attachMedia(video);\n" +
                "        } else {\n" +
                "            video.src = videoSrc;\n" +
                "        }\n" +
                "        video.play().catch(() => {});\n" +
                "        \n" +
                "        const topic = 'myplyr-party-' + room.toLowerCase();\n" +
                "        let ws = null;\n" +
                "        function connectWebSocket() {\n" +
                "            const wsUrl = 'wss://ntfy.sh/' + topic + '/ws';\n" +
                "            try {\n" +
                "                ws = new WebSocket(wsUrl);\n" +
                "                ws.onopen = () => {\n" +
                "                    syncText.innerText = 'متصل ومتزامن مع المضيف ✓';\n" +
                "                    statusDot.style.background = '#3FB950';\n" +
                "                    publish({ action: 'join', sender: 'Safari (iOS)' });\n" +
                "                };\n" +
                "                ws.onmessage = (event) => {\n" +
                "                    try {\n" +
                "                        const envelope = JSON.parse(event.data);\n" +
                "                        if (envelope.event === 'message' && envelope.message) {\n" +
                "                            const data = JSON.parse(envelope.message);\n" +
                "                            handleSocketMessage(data);\n" +
                "                        }\n" +
                "                    } catch (e) {}\n" +
                "                };\n" +
                "                ws.onclose = () => {\n" +
                "                    syncText.innerText = 'جاري إعادة الاتصال...';\n" +
                "                    statusDot.style.background = '#E50914';\n" +
                "                    setTimeout(connectWebSocket, 2000);\n" +
                "                };\n" +
                "            } catch (e) {\n" +
                "                setTimeout(connectWebSocket, 3000);\n" +
                "            }\n" +
                "        }\n" +
                "        function publish(data) {\n" +
                "            fetch('https://ntfy.sh/' + topic, {\n" +
                "                method: 'POST',\n" +
                "                headers: { 'Content-Type': 'text/plain; charset=utf-8' },\n" +
                "                body: JSON.stringify(data)\n" +
                "            }).catch(() => {});\n" +
                "        }\n" +
                "        function handleSocketMessage(data) {\n" +
                "            if (data.action === 'sync') {\n" +
                "                const targetPosSec = (data.pos || 0) / 1000.0;\n" +
                "                const isPlaying = (data.state === 'PLAYING');\n" +
                "                if (Math.abs(video.currentTime - targetPosSec) > 1.5) {\n" +
                "                    video.currentTime = targetPosSec;\n" +
                "                }\n" +
                "                if (isPlaying && video.paused) video.play().catch(() => {});\n" +
                "                else if (!isPlaying && !video.paused) video.pause();\n" +
                "            } else if (data.action === 'emoji') {\n" +
                "                spawnFloatingEmoji(data.emoji || '🔥');\n" +
                "            }\n" +
                "        }\n" +
                "        function sendReaction(emoji) {\n" +
                "            spawnFloatingEmoji(emoji);\n" +
                "            publish({ action: 'emoji', emoji: emoji, sender: 'Safari' });\n" +
                "        }\n" +
                "        function spawnFloatingEmoji(emoji) {\n" +
                "            const el = document.createElement('div');\n" +
                "            el.className = 'flying-emoji';\n" +
                "            el.innerText = emoji;\n" +
                "            el.style.left = (15 + Math.random() * 70) + '%';\n" +
                "            playerWrap.appendChild(el);\n" +
                "            setTimeout(() => el.remove(), 2400);\n" +
                "        }\n" +
                "        connectWebSocket();\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";
    }
}
