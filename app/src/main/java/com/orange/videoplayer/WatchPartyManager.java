package com.orange.videoplayer;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Serverless Event-Driven Synchronization Manager for Watch Party.
 * Uses ntfy.sh high-availability pub/sub topics with WebSocket on Android and SSE on Web/Safari.
 * Operates on discrete playback events (PLAY / PAUSE / SEEK / JOIN / REQUEST_SYNC) with zero network heartbeat overhead.
 */
public class WatchPartyManager {

    private static final String TAG = "WatchPartyManager";

    public interface Listener {
        void onSyncReceived(long targetPosMs, boolean isPlaying, long seq);
        void onEmojiReceived(String emoji, String sender);
        void onChatReceived(String message, String sender);
        void onPeerCountChanged(int peerCount);
        void onConnectionStatus(boolean isConnected);
        void onSyncRequested();
    }

    private static WatchPartyManager instance;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final OkHttpClient client;
    private final SecureRandom secureRandom = new SecureRandom();

    private WebSocket webSocket;
    private boolean isHost = false;
    private String roomId;
    private String videoUrl;
    private String videoTitle;
    private int peerCount = 1;
    private Listener listener;

    private final AtomicLong currentSeq = new AtomicLong(0L);
    private long lastAppliedSeq = 0L;

    private WatchPartyManager() {
        client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    public static synchronized WatchPartyManager getInstance() {
        if (instance == null) {
            instance = new WatchPartyManager();
        }
        return instance;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public boolean isPartyActive() {
        return roomId != null && !roomId.isEmpty();
    }

    public boolean isHost() {
        return isHost;
    }

    public String getRoomId() {
        return roomId;
    }

    public int getPeerCount() {
        return peerCount;
    }

    public String createRoom(String streamUrl, String title) {
        this.isHost = true;
        this.videoUrl = streamUrl;
        this.videoTitle = title;
        // Generate cryptographically secure 6-digit room code
        int code = 100000 + secureRandom.nextInt(900000);
        this.roomId = "WP-" + code;
        this.peerCount = 1;
        this.currentSeq.set(0L);
        this.lastAppliedSeq = 0L;

        connectWebSocket();
        return this.roomId;
    }

    public void joinRoom(String roomCode, String streamUrl, String title) {
        this.isHost = false;
        this.videoUrl = streamUrl;
        this.videoTitle = title;
        this.roomId = roomCode.trim().toUpperCase(Locale.US);
        this.peerCount = 2;
        this.currentSeq.set(0L);
        this.lastAppliedSeq = 0L;

        connectWebSocket();
    }

    public String getTopic() {
        if (roomId == null || roomId.isEmpty()) return "myplyr-party-default";
        return "myplyr-party-" + roomId.toLowerCase(Locale.US);
    }

    private void connectWebSocket() {
        disconnect();

        String wsUrl = "wss://ntfy.sh/" + getTopic() + "/ws";
        Log.d(TAG, "Connecting WebSocket to: " + wsUrl);

        Request request = new Request.Builder()
                .url(wsUrl)
                .build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                Log.d(TAG, "WebSocket connected successfully");
                mainHandler.post(() -> {
                    if (listener != null) listener.onConnectionStatus(true);
                });

                // Announce Join and request initial sync
                try {
                    JSONObject joinMsg = new JSONObject();
                    joinMsg.put("action", "join");
                    joinMsg.put("sender", isHost ? "المضيف (Host)" : "ضيف (Guest)");
                    joinMsg.put("ts", System.currentTimeMillis());
                    publishMessage(joinMsg.toString());

                    if (!isHost) {
                        requestSync();
                    }
                } catch (JSONException ignored) {
                }
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                try {
                    JSONObject envelope = new JSONObject(text);
                    if ("message".equals(envelope.optString("event"))) {
                        String payload = envelope.optString("message");
                        handleIncomingMessage(payload);
                    } else if (envelope.has("action")) {
                        handleIncomingMessage(text);
                    }
                } catch (JSONException e) {
                    handleIncomingMessage(text);
                }
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                Log.d(TAG, "WebSocket closed: " + reason);
                mainHandler.post(() -> {
                    if (listener != null) listener.onConnectionStatus(false);
                });
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                Log.w(TAG, "WebSocket failure: " + t.getMessage());
                mainHandler.post(() -> {
                    if (listener != null) listener.onConnectionStatus(false);
                });
            }
        });
    }

    private void publishMessage(String payload) {
        if (roomId == null || roomId.isEmpty()) return;
        RequestBody body = RequestBody.create(payload, MediaType.parse("text/plain; charset=utf-8"));
        Request req = new Request.Builder()
                .url("https://ntfy.sh/" + getTopic())
                .post(body)
                .build();

        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.w(TAG, "Failed to publish message: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) {
                response.close();
            }
        });
    }

    private void handleIncomingMessage(String text) {
        if (text == null || text.trim().isEmpty()) return;
        try {
            JSONObject obj = new JSONObject(text.trim());
            String action = obj.optString("action");
            String sender = obj.optString("sender", "صديق");
            long seq = obj.optLong("seq", 0L);

            if ("sync".equals(action)) {
                if (seq > 0 && seq < lastAppliedSeq) {
                    // Drop outdated out-of-order message
                    return;
                }
                if (seq > 0) {
                    lastAppliedSeq = seq;
                }

                long pos = obj.optLong("pos", 0L);
                boolean isPlaying = "PLAYING".equalsIgnoreCase(obj.optString("state"));

                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onSyncReceived(pos, isPlaying, seq);
                    }
                });

            } else if ("request_sync".equals(action)) {
                if (isHost) {
                    mainHandler.post(() -> {
                        if (listener != null) listener.onSyncRequested();
                    });
                }

            } else if ("join".equals(action)) {
                peerCount++;
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onPeerCountChanged(peerCount);
                        listener.onEmojiReceived("🎉", sender);
                        if (isHost) {
                            listener.onSyncRequested();
                        }
                    }
                });

            } else if ("emoji".equals(action)) {
                String emoji = obj.optString("emoji", "🔥");
                mainHandler.post(() -> {
                    if (listener != null) listener.onEmojiReceived(emoji, sender);
                });

            } else if ("chat".equals(action)) {
                String message = obj.optString("message", "");
                mainHandler.post(() -> {
                    if (listener != null) listener.onChatReceived(message, sender);
                });
            }
        } catch (JSONException ignored) {
        }
    }

    /**
     * Broadcasts state change on discrete events (Play / Pause / Seek / Media change / Sync response).
     * No periodic heartbeat.
     */
    public void broadcastSync(long posMs, boolean isPlaying) {
        if (!isPartyActive() || !isHost) return;

        long seq = currentSeq.incrementAndGet();
        try {
            JSONObject obj = new JSONObject();
            obj.put("action", "sync");
            obj.put("seq", seq);
            obj.put("state", isPlaying ? "PLAYING" : "PAUSED");
            obj.put("pos", posMs);
            obj.put("sender", "المضيف");
            obj.put("url", videoUrl);
            obj.put("title", videoTitle);
            obj.put("ts", System.currentTimeMillis());
            publishMessage(obj.toString());
        } catch (JSONException ignored) {
        }
    }

    /**
     * Requests immediate sync from the Host (on join, reconnect, app resume, or buffering recovery).
     */
    public void requestSync() {
        if (!isPartyActive() || isHost) return;

        try {
            JSONObject reqSync = new JSONObject();
            reqSync.put("action", "request_sync");
            reqSync.put("sender", "Guest");
            reqSync.put("ts", System.currentTimeMillis());
            publishMessage(reqSync.toString());
        } catch (JSONException ignored) {
        }
    }

    public void sendEmoji(String emoji) {
        if (!isPartyActive()) return;

        try {
            JSONObject obj = new JSONObject();
            obj.put("action", "emoji");
            obj.put("emoji", emoji);
            obj.put("sender", isHost ? "المضيف" : "ضيف");
            obj.put("ts", System.currentTimeMillis());
            publishMessage(obj.toString());
        } catch (JSONException ignored) {
        }
    }

    public void sendChat(String message) {
        if (!isPartyActive() || message == null || message.trim().isEmpty()) return;

        try {
            JSONObject obj = new JSONObject();
            obj.put("action", "chat");
            obj.put("message", message.trim());
            obj.put("sender", isHost ? "المضيف" : "أنا");
            obj.put("ts", System.currentTimeMillis());
            publishMessage(obj.toString());
        } catch (JSONException ignored) {
        }
    }

    public void leaveRoom() {
        disconnect();
        roomId = null;
        isHost = false;
        peerCount = 1;
        if (listener != null) listener.onConnectionStatus(false);
    }

    private void disconnect() {
        if (webSocket != null) {
            try {
                webSocket.close(1000, "Left");
            } catch (Exception ignored) {
            }
            webSocket = null;
        }
    }
}
