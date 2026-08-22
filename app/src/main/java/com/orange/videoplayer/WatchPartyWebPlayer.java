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

/**
 * Generates and shares cross-platform Web Player pages for iOS (Safari), macOS, Windows, and Android.
 * Implements Event-Driven sync + Local Monotonic Drift Correction (zero network heartbeat polling).
 */
public class WatchPartyWebPlayer {

    private static final String HOSTED_WEB_PLAYER_URL = "https://videoshare-one.vercel.app/";

    public static String getShareableWebUrl(String roomId, String videoUrl, String videoTitle) {
        if (roomId == null) roomId = "WP-1001";
        if (videoUrl != null && videoUrl.contains(".mkv")) {
            videoUrl = videoUrl.replaceFirst("(?i)\\.mkv(\\?|$)", ".mp4$1");
        }
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
        sb.append("🍿 انضم معي للمشاهدة الجماعية المتزامنة على MyPlyr!\n");
        if (videoTitle != null && !videoTitle.isEmpty()) {
            sb.append("🎬 ").append(videoTitle).append("\n");
        }
        sb.append("🔑 رمز الغرفة: ").append(roomId).append("\n\n");

        String webUrl = getShareableWebUrl(roomId, videoUrl, videoTitle);
        String lanUrl = LocalPartyServer.getLanUrl();

        sb.append("👇 اضغط على الرابط للمشاهدة فوراً بالمتصفح (آيفون / أندرويد / كمبيوتر):\n")
          .append(webUrl).append("\n\n");

        if (lanUrl != null) {
            sb.append("📶 لأجهزة نفس شبكة الواي فاي:\n")
              .append(lanUrl).append("\n\n");
        }

        sb.append("💡 افتح الرابط واضغط 'انضمام' للمشاهدة المتزامنة فوراً 🍿");
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

            String webUrl = getShareableWebUrl(roomId, videoUrl, videoTitle);
            String shareSubject = "مشغل المشاهدة الجماعية - " + (videoTitle != null ? videoTitle : "MyPlyr");
            String shareBody = "🍿 دعوة للمشاهدة الجماعية على MyPlyr\n" +
                    (videoTitle != null && !videoTitle.isEmpty() ? "🎬 " + videoTitle + "\n" : "") +
                    "🔑 رمز الغرفة: " + roomId + "\n\n" +
                    "🌐 الرابط المباشر للآيفون (انقر للفتح فوراً في Safari):\n" +
                    webUrl + "\n\n" +
                    "📁 إذا استلمت ملف HTML:\n" +
                    "على الآيفون، اضغط زر المشاركة (📤) ثم اختر 'فتح في Safari' 🌐";

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/html");
            intent.putExtra(Intent.EXTRA_STREAM, contentUri);
            intent.putExtra(Intent.EXTRA_SUBJECT, shareSubject);
            intent.putExtra(Intent.EXTRA_TEXT, shareBody);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(android.content.ClipData.newRawUri("HTML", contentUri));

            context.startActivity(Intent.createChooser(intent, "إرسال مشغل الويب للآيفون"));
        } catch (Exception e) {
            Toast.makeText(context, "تعذر إنشاء ملف المشغل: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public static String getSelfContainedHtml(String roomId, String videoUrl, String videoTitle) {
        String safeRoom = (roomId != null && !roomId.isEmpty()) ? roomId : "WP-1001";
        String safeUrl = (videoUrl != null) ? videoUrl.replace("\"", "\\\"").replace("\n", "").trim() : "";
        String safeTitle = (videoTitle != null && !videoTitle.isEmpty()) ? videoTitle.replace("\"", "\\\"").replace("\n", " ").trim() : "مشاهدة متزامنة";

        return "<!DOCTYPE html>\n" +
                "<html lang=\"ar\" dir=\"rtl\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover\">\n" +
                "    <title>" + safeTitle + " - MyPlyr Watch Party</title>\n" +
                "    <script src=\"https://cdn.jsdelivr.net/npm/hls.js@1.5.8/dist/hls.min.js\"></script>\n" +
                "    <style>\n" +
                "        :root {\n" +
                "            --bg-dark: #090D16;\n" +
                "            --card-bg: #141B2D;\n" +
                "            --accent: #E50914;\n" +
                "            --accent-glow: rgba(229, 9, 20, 0.4);\n" +
                "            --text: #FFFFFF;\n" +
                "            --text-dim: #8B949E;\n" +
                "            --border: #21262D;\n" +
                "            --green: #2EA043;\n" +
                "            --yellow: #D29922;\n" +
                "        }\n" +
                "        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; -webkit-tap-highlight-color: transparent; }\n" +
                "        body { background: var(--bg-dark); color: var(--text); display: flex; flex-direction: column; height: 100vh; height: 100dvh; overflow: hidden; user-select: none; }\n" +
                "        header { background: rgba(20, 27, 45, 0.95); backdrop-filter: blur(12px); padding: 12px 18px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid var(--border); z-index: 20; }\n" +
                "        .header-title { font-weight: bold; font-size: 15px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 60%; }\n" +
                "        .room-badge { background: rgba(46, 160, 67, 0.15); color: #3FB950; border: 1px solid rgba(46, 160, 67, 0.4); padding: 5px 14px; border-radius: 20px; font-size: 13px; font-weight: bold; display: flex; align-items: center; gap: 8px; }\n" +
                "        .status-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--yellow); box-shadow: 0 0 8px var(--yellow); transition: all 0.3s ease; }\n" +
                "        .player-wrap { position: relative; flex: 1; display: flex; align-items: center; justify-content: center; background: #000; overflow: hidden; }\n" +
                "        video { width: 100%; height: 100%; object-fit: contain; background: #000; }\n" +
                "        .sync-badge { position: absolute; top: 14px; left: 14px; background: rgba(13, 17, 23, 0.88); backdrop-filter: blur(8px); border: 1px solid var(--border); padding: 6px 14px; border-radius: 20px; font-size: 12px; color: var(--text); display: flex; align-items: center; gap: 6px; z-index: 10; pointer-events: none; transition: opacity 0.3s; }\n" +
                "        .drift-indicator { position: absolute; top: 14px; right: 14px; background: rgba(229, 9, 20, 0.2); border: 1px solid var(--accent); color: #FFA198; padding: 4px 10px; border-radius: 12px; font-size: 11px; font-weight: bold; display: none; z-index: 10; }\n" +
                "        .flying-emoji { position: absolute; bottom: 80px; font-size: 46px; animation: flyUp 2.4s cubic-bezier(0.25, 1, 0.5, 1) forwards; pointer-events: none; z-index: 15; }\n" +
                "        @keyframes flyUp { 0% { opacity: 0; transform: translateY(30px) scale(0.6); } 15% { opacity: 1; transform: translateY(0) scale(1.25); } 80% { opacity: 0.9; } 100% { opacity: 0; transform: translateY(-380px) scale(1.7); } }\n" +
                "        .reactions-container { background: rgba(20, 27, 45, 0.95); backdrop-filter: blur(12px); padding: 12px 16px; display: flex; align-items: center; justify-content: center; gap: 14px; border-top: 1px solid var(--border); z-index: 20; }\n" +
                "        .emoji-btn { background: var(--card-bg); border: 1px solid var(--border); border-radius: 50%; width: 48px; height: 48px; font-size: 24px; cursor: pointer; display: flex; align-items: center; justify-content: center; transition: transform 0.15s, background 0.15s; outline: none; }\n" +
                "        .emoji-btn:active { transform: scale(1.35); background: rgba(255,255,255,0.1); }\n" +
                "        .join-overlay { position: absolute; inset: 0; background: rgba(9, 13, 22, 0.94); backdrop-filter: blur(16px); display: flex; align-items: center; justify-content: center; z-index: 50; padding: 24px; text-align: center; }\n" +
                "        .join-card { background: var(--card-bg); border: 1px solid var(--border); border-radius: 24px; padding: 32px 24px; max-width: 380px; width: 100%; box-shadow: 0 20px 40px rgba(0,0,0,0.6); display: flex; flex-direction: column; align-items: center; gap: 16px; }\n" +
                "        .join-card h2 { font-size: 20px; color: var(--text); }\n" +
                "        .join-card p { font-size: 14px; color: var(--text-dim); line-height: 1.5; }\n" +
                "        .btn-unlock { background: linear-gradient(135deg, #E50914, #B81D24); color: #FFF; border: none; padding: 14px 28px; border-radius: 30px; font-size: 16px; font-weight: bold; cursor: pointer; box-shadow: 0 8px 20px var(--accent-glow); transition: transform 0.2s, box-shadow 0.2s; width: 100%; }\n" +
                "        .btn-unlock:active { transform: scale(0.97); }\n" +
                "        .resume-banner { position: absolute; bottom: 20px; left: 50%; transform: translateX(-50%); background: #E50914; color: #FFF; padding: 10px 20px; border-radius: 30px; font-size: 14px; font-weight: bold; cursor: pointer; z-index: 40; display: none; box-shadow: 0 6px 16px rgba(229,9,20,0.5); }\n" +
                "        .chat-toast { position: absolute; top: 70px; left: 50%; transform: translateX(-50%); background: rgba(20,27,45,0.92); border: 1px solid var(--border); padding: 8px 18px; border-radius: 20px; font-size: 13px; color: #FFF; z-index: 30; pointer-events: none; opacity: 0; transition: opacity 0.3s; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <header>\n" +
                "        <div class=\"header-title\" id=\"titleText\">🍿 " + safeTitle + "</div>\n" +
                "        <div class=\"room-badge\"><div class=\"status-dot\" id=\"statusDot\"></div><span id=\"roomCodeText\">" + safeRoom + "</span></div>\n" +
                "    </header>\n" +
                "    <div class=\"player-wrap\" id=\"playerWrap\">\n" +
                "        <div class=\"sync-badge\" id=\"syncStatus\"><span id=\"syncIcon\">🟡</span> <span id=\"syncText\">جاري الاتصال...</span></div>\n" +
                "        <div class=\"drift-indicator\" id=\"driftIndicator\">مزامنة السرعة ⚡</div>\n" +
                "        <div class=\"chat-toast\" id=\"chatToast\"></div>\n" +
                "        <div class=\"resume-banner\" id=\"resumeBanner\" onclick=\"unlockAndResume()\">▶ انقر للاستئناف</div>\n" +
                "        <div class=\"join-overlay\" id=\"joinOverlay\">\n" +
                "            <div class=\"join-card\">\n" +
                "                <div style=\"font-size: 48px;\">🍿</div>\n" +
                "                <h2>مشاهدة جماعية متزامنة</h2>\n" +
                "                <p>انقر للانضمام وتفعيل الصوت والمشاهدة المتزامنة لحظياً مع المضيف.</p>\n" +
                "                <button class=\"btn-unlock\" id=\"btnJoin\" onclick=\"unlockPlayback()\">🚀 انضمام وتفعيل التشغيل</button>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "        <video id=\"videoPlayer\" controls playsinline webkit-playsinline preload=\"metadata\"></video>\n" +
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
                "        const DEBUG = true;\n" +
                "        const room = '" + safeRoom + "';\n" +
                "        const initialSrc = '" + safeUrl + "';\n" +
                "        const topic = 'myplyr-party-' + room.toLowerCase();\n" +
                "        \n" +
                "        const SOFT_DRIFT_THRESHOLD = 0.25; // 250ms\n" +
                "        const HARD_DRIFT_THRESHOLD = 1.20; // 1200ms\n" +
                "        const CATCHUP_RATE = 1.04;         // speed up slightly\n" +
                "        const SLOWDOWN_RATE = 0.96;        // slow down slightly\n" +
                "        \n" +
                "        const video = document.getElementById('videoPlayer');\n" +
                "        const playerWrap = document.getElementById('playerWrap');\n" +
                "        const syncText = document.getElementById('syncText');\n" +
                "        const syncIcon = document.getElementById('syncIcon');\n" +
                "        const statusDot = document.getElementById('statusDot');\n" +
                "        const joinOverlay = document.getElementById('joinOverlay');\n" +
                "        const resumeBanner = document.getElementById('resumeBanner');\n" +
                "        const driftIndicator = document.getElementById('driftIndicator');\n" +
                "        const chatToast = document.getElementById('chatToast');\n" +
                "        \n" +
                "        let isUnlocked = false;\n" +
                "        let eventSource = null;\n" +
                "        let hlsInstance = null;\n" +
                "        let lastAppliedSeq = 0;\n" +
                "        let pendingSync = null;\n" +
                "        let reconnectTimer = null;\n" +
                "        let currentVideoSrc = '';\n" +
                "        let isApplyingRemoteSync = false;\n" +
                "        let isBuffering = false;\n" +
                "        let bufferStartTime = 0;\n" +
                "        let lastResyncTime = 0;\n" +
                "        \n" +
                "        // Monotonic local baseline for host position\n" +
                "        let hostBasePosSec = 0;\n" +
                "        let hostBaseTimeMs = 0;\n" +
                "        let hostStateIsPlaying = false;\n" +
                "        \n" +
                "        function log(...args) {\n" +
                "            if (DEBUG) console.log('[WatchParty]', ...args);\n" +
                "        }\n" +
                "        \n" +
                "        function wrapUrl(u) {\n" +
                "            if (!u || typeof u !== 'string') return u;\n" +
                "            if (u.indexOf('http://') === 0) {\n" +
                "                return 'https://videoshare-one.vercel.app/api/proxy?url=' + encodeURIComponent(u) + '&referer=' + encodeURIComponent(u);\n" +
                "            }\n" +
                "            return u;\n" +
                "        }\n" +
                "        \n" +
                "        function setVideoSource(url) {\n" +
                "            if (!url || url === currentVideoSrc) return;\n" +
                "            currentVideoSrc = url;\n" +
                "            log('Setting video source:', url);\n" +
                "            // Route insecure http streams through the HTTPS proxy (iOS Safari blocks plain http)\n" +
                "            const effectiveUrl = wrapUrl(url);\n" +
                "            const isHls = url.includes('.m3u8') || url.includes('.m3u') || url.includes('type=m3u8');\n" +
                "            const isSafariNativeHls = video.canPlayType('application/vnd.apple.mpegurl') || video.canPlayType('application/x-mpegURL');\n" +
                "            \n" +
                "            if (hlsInstance) {\n" +
                "                hlsInstance.destroy();\n" +
                "                hlsInstance = null;\n" +
                "            }\n" +
                "            \n" +
                "            if (isHls && !isSafariNativeHls && typeof Hls !== 'undefined' && Hls.isSupported()) {\n" +
                "                log('Initializing hls.js for stream');\n" +
                "                hlsInstance = new Hls({ enableWorker: true, lowLatencyMode: true, maxBufferLength: 30 });\n" +
                "                hlsInstance.loadSource(effectiveUrl);\n" +
                "                hlsInstance.attachMedia(video);\n" +
                "                hlsInstance.on(Hls.Events.ERROR, function(event, data) {\n" +
                "                    if (data.fatal) {\n" +
                "                        switch (data.type) {\n" +
                "                            case Hls.ErrorTypes.NETWORK_ERROR:\n" +
                "                                hlsInstance.startLoad();\n" +
                "                                break;\n" +
                "                            case Hls.ErrorTypes.MEDIA_ERROR:\n" +
                "                                hlsInstance.recoverMediaError();\n" +
                "                                break;\n" +
                "                            default:\n" +
                "                                hlsInstance.destroy();\n" +
                "                                break;\n" +
                "                        }\n" +
                "                    }\n" +
                "                });\n" +
                "            } else {\n" +
                "                log('Using native HTML5 / Safari HLS video source');\n" +
                "                video.src = effectiveUrl;\n" +
                "            }\n" +
                "            video.load();\n" +
                "        }\n" +
                "        \n" +
                "        video.addEventListener('loadedmetadata', () => {\n" +
                "            log('Video loadedmetadata: duration=' + video.duration);\n" +
                "            if (pendingSync) {\n" +
                "                const sync = pendingSync;\n" +
                "                pendingSync = null;\n" +
                "                handleSync(sync);\n" +
                "            }\n" +
                "        });\n" +
                "        \n" +
                "        video.addEventListener('waiting', () => {\n" +
                "            isBuffering = true;\n" +
                "            bufferStartTime = performance.now();\n" +
                "        });\n" +
                "        \n" +
                "        video.addEventListener('playing', () => {\n" +
                "            if (isBuffering && (performance.now() - bufferStartTime > 2500)) {\n" +
                "                log('Recovered from long buffer, requesting resync');\n" +
                "                requestResync();\n" +
                "            }\n" +
                "            isBuffering = false;\n" +
                "        });\n" +
                "        \n" +
                "        video.addEventListener('error', () => {\n" +
                "            log('Video error event:', video.error);\n" +
                "            updateConnectionStatus('error', 'تعذر تشغيل الفيديو، تأكد من صحة الرابط');\n" +
                "        });\n" +
                "        \n" +
                "        video.addEventListener('pause', () => {\n" +
                "            if (video.playbackRate !== 1.0) video.playbackRate = 1.0;\n" +
                "            driftIndicator.style.display = 'none';\n" +
                "        });\n" +
                "        \n" +
                "        function unlockPlayback() {\n" +
                "            log('User unlocked playback via gesture');\n" +
                "            isUnlocked = true;\n" +
                "            joinOverlay.style.display = 'none';\n" +
                "            \n" +
                "            const playPromise = video.play();\n" +
                "            if (playPromise !== undefined) {\n" +
                "                playPromise.then(() => {\n" +
                "                    log('Playback prime successful');\n" +
                "                    if (!hostStateIsPlaying) {\n" +
                "                        video.pause();\n" +
                "                    }\n" +
                "                }).catch(err => {\n" +
                "                    log('Playback prime catch:', err.name);\n" +
                "                });\n" +
                "            }\n" +
                "            \n" +
                "            publish({ action: 'join', sender: 'Safari/Web' });\n" +
                "            requestResync();\n" +
                "        }\n" +
                "        \n" +
                "        function unlockAndResume() {\n" +
                "            resumeBanner.style.display = 'none';\n" +
                "            video.play().catch(e => log('Resume failed:', e));\n" +
                "        }\n" +
                "        \n" +
                "        function connectRealtime() {\n" +
                "            if (eventSource) {\n" +
                "                eventSource.close();\n" +
                "                eventSource = null;\n" +
                "            }\n" +
                "            clearTimeout(reconnectTimer);\n" +
                "            \n" +
                "            const sseUrl = 'https://ntfy.sh/' + topic + '/sse';\n" +
                "            log('Connecting SSE to:', sseUrl);\n" +
                "            updateConnectionStatus('connecting', 'جاري الاتصال...');\n" +
                "            \n" +
                "            try {\n" +
                "                eventSource = new EventSource(sseUrl);\n" +
                "                \n" +
                "                eventSource.onopen = () => {\n" +
                "                    log('SSE connection open');\n" +
                "                    updateConnectionStatus('connected', 'متصل ومتزامن مع المضيف ✓');\n" +
                "                    publish({ action: 'join', sender: 'Safari/Web' });\n" +
                "                    requestResync();\n" +
                "                };\n" +
                "                \n" +
                "                eventSource.onmessage = (event) => {\n" +
                "                    const data = parseNtfyMessage(event.data);\n" +
                "                    if (data) {\n" +
                "                        handlePartyMessage(data);\n" +
                "                    }\n" +
                "                };\n" +
                "                \n" +
                "                eventSource.onerror = (err) => {\n" +
                "                    log('SSE error:', err);\n" +
                "                    updateConnectionStatus('reconnecting', 'جاري إعادة الاتصال...');\n" +
                "                    eventSource.close();\n" +
                "                    eventSource = null;\n" +
                "                    reconnectTimer = setTimeout(connectRealtime, 2500);\n" +
                "                };\n" +
                "            } catch (e) {\n" +
                "                log('EventSource constructor failed, retrying in 3s:', e);\n" +
                "                reconnectTimer = setTimeout(connectRealtime, 3000);\n" +
                "            }\n" +
                "        }\n" +
                "        \n" +
                "        function parseNtfyMessage(rawText) {\n" +
                "            if (!rawText) return null;\n" +
                "            try {\n" +
                "                const parsed = JSON.parse(rawText);\n" +
                "                if (parsed && parsed.event === 'message' && parsed.message) {\n" +
                "                    try {\n" +
                "                        return JSON.parse(parsed.message);\n" +
                "                    } catch (e) {\n" +
                "                        return null;\n" +
                "                    }\n" +
                "                }\n" +
                "                if (parsed && parsed.action) {\n" +
                "                    return parsed;\n" +
                "                }\n" +
                "            } catch (err) {\n" +
                "                log('parseNtfyMessage failed:', err);\n" +
                "            }\n" +
                "            return null;\n" +
                "        }\n" +
                "        \n" +
                "        function handlePartyMessage(data) {\n" +
                "            if (!data || !data.action) return;\n" +
                "            log('Received event:', data.action, data);\n" +
                "            \n" +
                "            if (data.action === 'sync') {\n" +
                "                handleSync(data);\n" +
                "            } else if (data.action === 'emoji') {\n" +
                "                spawnFloatingEmoji(data.emoji || '🔥');\n" +
                "            } else if (data.action === 'chat') {\n" +
                "                showChatToast((data.sender || 'صديق') + ': ' + (data.message || ''));\n" +
                "            } else if (data.action === 'join') {\n" +
                "                spawnFloatingEmoji('🎉');\n" +
                "            }\n" +
                "        }\n" +
                "        \n" +
                "        function handleSync(data) {\n" +
                "            if (data.seq && data.seq < lastAppliedSeq) {\n" +
                "                log('Dropping outdated seq:', data.seq, '<', lastAppliedSeq);\n" +
                "                return;\n" +
                "            }\n" +
                "            if (data.seq) lastAppliedSeq = data.seq;\n" +
                "            \n" +
                "            if (data.url && data.url !== currentVideoSrc) {\n" +
                "                setVideoSource(data.url);\n" +
                "            }\n" +
                "            if (data.title && document.getElementById('titleText')) {\n" +
                "                document.getElementById('titleText').innerText = '🍿 ' + data.title;\n" +
                "            }\n" +
                "            \n" +
                "            // Update local monotonic baseline\n" +
                "            hostBasePosSec = (data.pos || 0) / 1000.0;\n" +
                "            hostBaseTimeMs = performance.now();\n" +
                "            hostStateIsPlaying = (data.state === 'PLAYING');\n" +
                "            \n" +
                "            if (video.readyState < 1) {\n" +
                "                log('Video metadata not ready yet, saving pendingSync');\n" +
                "                pendingSync = data;\n" +
                "                return;\n" +
                "            }\n" +
                "            \n" +
                "            const isLive = !isFinite(video.duration) || video.duration === 0;\n" +
                "            \n" +
                "            isApplyingRemoteSync = true;\n" +
                "            try {\n" +
                "                if (!isLive) {\n" +
                "                    const diff = Math.abs(video.currentTime - hostBasePosSec);\n" +
                "                    if (diff > HARD_DRIFT_THRESHOLD || video.ended) {\n" +
                "                        video.currentTime = hostBasePosSec;\n" +
                "                        video.playbackRate = 1.0;\n" +
                "                    }\n" +
                "                }\n" +
                "                \n" +
                "                if (isUnlocked) {\n" +
                "                    if (hostStateIsPlaying && video.paused) {\n" +
                "                        video.play().then(() => {\n" +
                "                            resumeBanner.style.display = 'none';\n" +
                "                        }).catch(err => {\n" +
                "                            log('Play rejected by Safari:', err.name);\n" +
                "                            resumeBanner.style.display = 'block';\n" +
                "                        });\n" +
                "                    } else if (!hostStateIsPlaying && !video.paused) {\n" +
                "                        video.pause();\n" +
                "                        resumeBanner.style.display = 'none';\n" +
                "                    }\n" +
                "                }\n" +
                "            } finally {\n" +
                "                isApplyingRemoteSync = false;\n" +
                "            }\n" +
                "        }\n" +
                "        \n" +
                "        // Local Monotonic Drift Correction Loop (Runs every 350ms, ZERO network traffic)\n" +
                "        function getExpectedHostPosSec() {\n" +
                "            if (!hostStateIsPlaying) return hostBasePosSec;\n" +
                "            const elapsedSec = (performance.now() - hostBaseTimeMs) / 1000.0;\n" +
                "            return hostBasePosSec + elapsedSec;\n" +
                "        }\n" +
                "        \n" +
                "        setInterval(() => {\n" +
                "            if (video.readyState < 2 || isApplyingRemoteSync || isBuffering) return;\n" +
                "            const isLive = !isFinite(video.duration) || video.duration === 0;\n" +
                "            if (isLive) return;\n" +
                "            \n" +
                "            const expectedPos = getExpectedHostPosSec();\n" +
                "            const currentPos = video.currentTime;\n" +
                "            const drift = currentPos - expectedPos;\n" +
                "            const absDrift = Math.abs(drift);\n" +
                "            \n" +
                "            if (absDrift <= SOFT_DRIFT_THRESHOLD) {\n" +
                "                if (video.playbackRate !== 1.0) video.playbackRate = 1.0;\n" +
                "                driftIndicator.style.display = 'none';\n" +
                "            } else if (absDrift <= HARD_DRIFT_THRESHOLD) {\n" +
                "                if (drift > 0) {\n" +
                "                    video.playbackRate = SLOWDOWN_RATE;\n" +
                "                } else {\n" +
                "                    video.playbackRate = CATCHUP_RATE;\n" +
                "                }\n" +
                "                driftIndicator.style.display = 'block';\n" +
                "            } else if (absDrift <= 5.0) {\n" +
                "                video.currentTime = expectedPos;\n" +
                "                video.playbackRate = 1.0;\n" +
                "                driftIndicator.style.display = 'none';\n" +
                "            } else {\n" +
                "                video.currentTime = expectedPos;\n" +
                "                video.playbackRate = 1.0;\n" +
                "                driftIndicator.style.display = 'none';\n" +
                "                requestResync();\n" +
                "            }\n" +
                "        }, 350);\n" +
                "        \n" +
                "        function requestResync() {\n" +
                "            const now = performance.now();\n" +
                "            if (now - lastResyncTime < 5000) return;\n" +
                "            lastResyncTime = now;\n" +
                "            log('Sending request_sync to host');\n" +
                "            publish({ action: 'request_sync', sender: 'Safari/Web' });\n" +
                "        }\n" +
                "        \n" +
                "        document.addEventListener('visibilitychange', () => {\n" +
                "            if (document.visibilityState === 'visible') {\n" +
                "                log('Page visible -> requesting resync');\n" +
                "                requestResync();\n" +
                "            }\n" +
                "        });\n" +
                "        window.addEventListener('pageshow', () => {\n" +
                "            log('Page show -> requesting resync');\n" +
                "            requestResync();\n" +
                "        });\n" +
                "        \n" +
                "        function updateConnectionStatus(status, text) {\n" +
                "            syncText.innerText = text;\n" +
                "            if (status === 'connected') {\n" +
                "                syncIcon.innerText = '🟢';\n" +
                "                statusDot.style.background = '#2EA043';\n" +
                "                statusDot.style.boxShadow = '0 0 8px #2EA043';\n" +
                "            } else if (status === 'connecting' || status === 'reconnecting') {\n" +
                "                syncIcon.innerText = '🟡';\n" +
                "                statusDot.style.background = '#D29922';\n" +
                "                statusDot.style.boxShadow = '0 0 8px #D29922';\n" +
                "            } else {\n" +
                "                syncIcon.innerText = '🔴';\n" +
                "                statusDot.style.background = '#E50914';\n" +
                "                statusDot.style.boxShadow = '0 0 8px #E50914';\n" +
                "            }\n" +
                "        }\n" +
                "        \n" +
                "        function publish(data) {\n" +
                "            fetch('https://ntfy.sh/' + topic, {\n" +
                "                method: 'POST',\n" +
                "                headers: { 'Content-Type': 'text/plain; charset=utf-8' },\n" +
                "                body: JSON.stringify(data)\n" +
                "            }).catch(err => log('Publish failed:', err));\n" +
                "        }\n" +
                "        \n" +
                "        function sendReaction(emoji) {\n" +
                "            spawnFloatingEmoji(emoji);\n" +
                "            publish({ action: 'emoji', emoji: emoji, sender: 'Safari/Web' });\n" +
                "        }\n" +
                "        \n" +
                "        function spawnFloatingEmoji(emoji) {\n" +
                "            const el = document.createElement('div');\n" +
                "            el.className = 'flying-emoji';\n" +
                "            el.innerText = emoji;\n" +
                "            el.style.left = (15 + Math.random() * 70) + '%';\n" +
                "            playerWrap.appendChild(el);\n" +
                "            setTimeout(() => el.remove(), 2400);\n" +
                "        }\n" +
                "        \n" +
                "        function showChatToast(msg) {\n" +
                "            chatToast.innerText = msg;\n" +
                "            chatToast.style.opacity = '1';\n" +
                "            setTimeout(() => { chatToast.style.opacity = '0'; }, 3000);\n" +
                "        }\n" +
                "        \n" +
                "        if (initialSrc) setVideoSource(initialSrc);\n" +
                "        connectRealtime();\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";
    }
}
