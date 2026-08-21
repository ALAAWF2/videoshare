package com.orange.videoplayer;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class WatchPartyManager {

    public interface Listener {
        void onSyncReceived(long targetPosMs, boolean isPlaying);
        void onEmojiReceived(String emoji, String sender);
        void onChatReceived(String message, String sender);
        void onPeerCountChanged(int peerCount);
        void onConnectionStatus(boolean isConnected);
    }

    private static WatchPartyManager instance;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final OkHttpClient client;

    private WebSocket webSocket;
    private boolean isHost = false;
    private String roomId;
    private String videoUrl;
    private String videoTitle;
    private int peerCount = 1;
    private Listener listener;

    private WatchPartyManager() {
        client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(15, TimeUnit.SECONDS)
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
        int code = 1000 + new Random().nextInt(9000);
        this.roomId = "WP-" + code;
        this.peerCount = 1;

        connectWebSocket();
        return this.roomId;
    }

    public void joinRoom(String roomCode, String streamUrl, String title) {
        this.isHost = false;
        this.videoUrl = streamUrl;
        this.videoTitle = title;
        this.roomId = roomCode.trim().toUpperCase(Locale.US);
        this.peerCount = 2;

        connectWebSocket();
    }

    private void connectWebSocket() {
        disconnect();

        // High performance public WebSocket relay
        String wsUrl = "wss://socketsbay.com/wss/v2/1/" + roomId;
        Request request = new Request.Builder()
                .url(wsUrl)
                .build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                mainHandler.post(() -> {
                    if (listener != null) listener.onConnectionStatus(true);
                });
                // Broadcast join announcement
                try {
                    JSONObject joinMsg = new JSONObject();
                    joinMsg.put("action", "join");
                    joinMsg.put("sender", isHost ? "المضيف (Host)" : "ضيف (Guest)");
                    joinMsg.put("ts", System.currentTimeMillis());
                    ws.send(joinMsg.toString());
                } catch (JSONException ignored) {
                }
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                handleIncomingMessage(text);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                mainHandler.post(() -> {
                    if (listener != null) listener.onConnectionStatus(false);
                });
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                mainHandler.post(() -> {
                    if (listener != null) listener.onConnectionStatus(false);
                });
            }
        });
    }

    private void handleIncomingMessage(String text) {
        try {
            JSONObject obj = new JSONObject(text);
            String action = obj.optString("action");
            String sender = obj.optString("sender", "صديق");

            if ("sync".equals(action)) {
                long pos = obj.optLong("pos", 0);
                boolean isPlaying = "PLAYING".equalsIgnoreCase(obj.optString("state"));
                long sentTs = obj.optLong("ts", System.currentTimeMillis());
                long latency = Math.max(0, (System.currentTimeMillis() - sentTs) / 2);
                long adjustedPos = isPlaying ? (pos + latency) : pos;

                mainHandler.post(() -> {
                    if (listener != null) listener.onSyncReceived(adjustedPos, isPlaying);
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

            } else if ("join".equals(action)) {
                peerCount++;
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onPeerCountChanged(peerCount);
                        listener.onEmojiReceived("🎉", sender);
                    }
                });
            }
        } catch (JSONException ignored) {
        }
    }

    public void broadcastSync(long posMs, boolean isPlaying) {
        if (webSocket == null || !isPartyActive()) return;

        try {
            JSONObject obj = new JSONObject();
            obj.put("action", "sync");
            obj.put("state", isPlaying ? "PLAYING" : "PAUSED");
            obj.put("pos", posMs);
            obj.put("sender", isHost ? "المضيف" : "ضيف");
            obj.put("ts", System.currentTimeMillis());
            webSocket.send(obj.toString());
        } catch (JSONException ignored) {
        }
    }

    public void sendEmoji(String emoji) {
        if (webSocket == null || !isPartyActive()) return;

        try {
            JSONObject obj = new JSONObject();
            obj.put("action", "emoji");
            obj.put("emoji", emoji);
            obj.put("sender", isHost ? "المضيف" : "ضيف");
            obj.put("ts", System.currentTimeMillis());
            webSocket.send(obj.toString());
        } catch (JSONException ignored) {
        }
    }

    public void sendChat(String message) {
        if (webSocket == null || !isPartyActive() || message == null || message.trim().isEmpty()) return;

        try {
            JSONObject obj = new JSONObject();
            obj.put("action", "chat");
            obj.put("message", message.trim());
            obj.put("sender", isHost ? "المضيف" : "أنا");
            obj.put("ts", System.currentTimeMillis());
            webSocket.send(obj.toString());
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
