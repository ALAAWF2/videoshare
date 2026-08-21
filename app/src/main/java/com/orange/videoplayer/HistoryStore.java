package com.orange.videoplayer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HistoryStore {

    private static final String PREFS_NAME = "myplyr_history";
    private static final String KEY_HISTORY = "history_list";
    private static final int MAX_HISTORY_ITEMS = 200;

    private static HistoryStore instance;
    private final SharedPreferences sp;

    private HistoryStore(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized HistoryStore getInstance(Context context) {
        if (instance == null) {
            instance = new HistoryStore(context);
        }
        return instance;
    }

    public synchronized List<JSONObject> getAll() {
        List<JSONObject> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp.getString(KEY_HISTORY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.getJSONObject(i));
            }
        } catch (JSONException ignored) {
        }
        Collections.sort(list, (a, b) -> Long.compare(b.optLong("ts"), a.optLong("ts")));
        return list;
    }

    private synchronized void saveAll(List<JSONObject> list) {
        if (list.size() > MAX_HISTORY_ITEMS) {
            list = list.subList(0, MAX_HISTORY_ITEMS);
        }
        JSONArray arr = new JSONArray();
        for (JSONObject o : list) {
            arr.put(o);
        }
        sp.edit().putString(KEY_HISTORY, arr.toString()).apply();
    }

    public synchronized void addOrUpdate(String title, String url, String iconUrl, String type, long pos, long dur) {
        if (url == null || url.trim().isEmpty()) return;
        String cleanUrl = url.trim();
        List<JSONObject> list = getAll();
        JSONObject existing = null;

        for (JSONObject o : list) {
            if (cleanUrl.equals(o.optString("url"))) {
                existing = o;
                break;
            }
        }

        long now = System.currentTimeMillis();
        if (existing != null) {
            try {
                if (title != null && !title.trim().isEmpty()) existing.put("title", title.trim());
                if (iconUrl != null && !iconUrl.trim().isEmpty()) existing.put("iconUrl", iconUrl.trim());
                if (type != null && !type.trim().isEmpty()) existing.put("type", type.trim());
                if (pos >= 0) existing.put("pos", pos);
                if (dur > 0) existing.put("dur", dur);
                existing.put("ts", now);
            } catch (JSONException ignored) {
            }
        } else {
            JSONObject o = new JSONObject();
            try {
                o.put("id", now);
                o.put("title", (title != null && !title.trim().isEmpty()) ? title.trim() : LinkStore.autoName(cleanUrl));
                o.put("url", cleanUrl);
                o.put("iconUrl", iconUrl != null ? iconUrl.trim() : "");
                o.put("type", type != null ? type.trim() : "vod");
                o.put("pos", Math.max(0, pos));
                o.put("dur", Math.max(0, dur));
                o.put("ts", now);
                list.add(0, o);
            } catch (JSONException ignored) {
            }
        }
        saveAll(list);
    }

    public synchronized void updatePosition(String url, long pos, long dur) {
        if (url == null || url.trim().isEmpty()) return;
        String cleanUrl = url.trim();
        List<JSONObject> list = getAll();
        for (JSONObject o : list) {
            if (cleanUrl.equals(o.optString("url"))) {
                try {
                    o.put("pos", pos);
                    if (dur > 0) o.put("dur", dur);
                    o.put("ts", System.currentTimeMillis());
                } catch (JSONException ignored) {
                }
                saveAll(list);
                break;
            }
        }
    }

    public synchronized void delete(long id) {
        List<JSONObject> list = getAll();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).optLong("id") == id) {
                list.remove(i);
                break;
            }
        }
        saveAll(list);
    }

    public synchronized void clearAll() {
        sp.edit().remove(KEY_HISTORY).apply();
    }
}
