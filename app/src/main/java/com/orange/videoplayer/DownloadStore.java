package com.orange.videoplayer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DownloadStore {

    private static final String PREFS_NAME = "myplyr_downloads";
    private static final String KEY_DOWNLOADS = "downloads_list";

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_RUNNING = 1;
    public static final int STATUS_PAUSED = 2;
    public static final int STATUS_SUCCESSFUL = 3;
    public static final int STATUS_FAILED = 4;

    private static DownloadStore instance;
    private final SharedPreferences sp;

    private DownloadStore(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized DownloadStore getInstance(Context context) {
        if (instance == null) {
            instance = new DownloadStore(context);
        }
        return instance;
    }

    public synchronized List<JSONObject> getAll() {
        List<JSONObject> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp.getString(KEY_DOWNLOADS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.getJSONObject(i));
            }
        } catch (JSONException ignored) {
        }
        Collections.sort(list, (a, b) -> Long.compare(b.optLong("addedTs"), a.optLong("addedTs")));
        return list;
    }

    private synchronized void saveAll(List<JSONObject> list) {
        JSONArray arr = new JSONArray();
        for (JSONObject o : list) {
            arr.put(o);
        }
        sp.edit().putString(KEY_DOWNLOADS, arr.toString()).apply();
    }

    public synchronized JSONObject get(long downloadId) {
        for (JSONObject o : getAll()) {
            if (o.optLong("downloadId") == downloadId || o.optLong("id") == downloadId) {
                return o;
            }
        }
        return null;
    }

    public synchronized JSONObject getByUrl(String url) {
        if (url == null || url.trim().isEmpty()) return null;
        String cleanUrl = url.trim();
        for (JSONObject o : getAll()) {
            if (cleanUrl.equals(o.optString("url"))) {
                return o;
            }
        }
        return null;
    }

    public synchronized void addOrUpdate(long downloadId, String title, String url, String localUri, String filePath, int status, long totalBytes, String iconUrl) {
        List<JSONObject> list = getAll();
        JSONObject existing = null;
        for (JSONObject o : list) {
            if (o.optLong("downloadId") == downloadId || (url != null && url.equals(o.optString("url")))) {
                existing = o;
                break;
            }
        }

        long now = System.currentTimeMillis();
        if (existing != null) {
            try {
                existing.put("downloadId", downloadId);
                if (title != null && !title.isEmpty()) existing.put("title", title);
                if (url != null && !url.isEmpty()) existing.put("url", url);
                if (localUri != null && !localUri.isEmpty()) existing.put("localUri", localUri);
                if (filePath != null && !filePath.isEmpty()) existing.put("filePath", filePath);
                existing.put("status", status);
                if (totalBytes > 0) existing.put("totalBytes", totalBytes);
                if (iconUrl != null && !iconUrl.isEmpty()) existing.put("iconUrl", iconUrl);
                existing.put("updatedTs", now);
            } catch (JSONException ignored) {
            }
        } else {
            JSONObject o = new JSONObject();
            try {
                o.put("id", now);
                o.put("downloadId", downloadId);
                o.put("title", title != null ? title : "فيديو محمل");
                o.put("url", url != null ? url : "");
                o.put("localUri", localUri != null ? localUri : "");
                o.put("filePath", filePath != null ? filePath : "");
                o.put("status", status);
                o.put("totalBytes", totalBytes);
                o.put("downloadedBytes", 0L);
                o.put("iconUrl", iconUrl != null ? iconUrl : "");
                o.put("addedTs", now);
                o.put("updatedTs", now);
                list.add(o);
            } catch (JSONException ignored) {
            }
        }
        saveAll(list);
    }

    public synchronized void updateProgress(long downloadId, int status, long downloadedBytes, long totalBytes, String localUri, String filePath) {
        List<JSONObject> list = getAll();
        for (JSONObject o : list) {
            if (o.optLong("downloadId") == downloadId) {
                try {
                    o.put("status", status);
                    if (downloadedBytes >= 0) o.put("downloadedBytes", downloadedBytes);
                    if (totalBytes > 0) o.put("totalBytes", totalBytes);
                    if (localUri != null && !localUri.isEmpty()) o.put("localUri", localUri);
                    if (filePath != null && !filePath.isEmpty()) o.put("filePath", filePath);
                    o.put("updatedTs", System.currentTimeMillis());
                } catch (JSONException ignored) {
                }
                break;
            }
        }
        saveAll(list);
    }

    public synchronized void setMeta(long downloadId, String key, String value) {
        List<JSONObject> list = getAll();
        for (JSONObject o : list) {
            if (o.optLong("downloadId") == downloadId) {
                try {
                    if (value == null) {
                        o.remove(key);
                    } else {
                        o.put(key, value);
                    }
                    o.put("updatedTs", System.currentTimeMillis());
                } catch (JSONException ignored) {
                }
                break;
            }
        }
        saveAll(list);
    }

    public synchronized void delete(long downloadId) {
        List<JSONObject> list = getAll();
        for (int i = 0; i < list.size(); i++) {
            JSONObject o = list.get(i);
            if (o.optLong("downloadId") == downloadId || o.optLong("id") == downloadId) {
                String filePath = o.optString("filePath");
                if (filePath != null && !filePath.isEmpty()) {
                    try {
                        File f = new File(filePath);
                        if (f.exists()) f.delete();
                        File parent = f.getParentFile();
                        if (parent != null) {
                            String title = o.optString("title", "");
                            String safeTitle = title.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
                            File part = new File(parent, downloadId + "_" + safeTitle + ".part");
                            if (part.exists()) part.delete();
                        }
                    } catch (Exception ignored) {
                    }
                }
                list.remove(i);
                break;
            }
        }
        saveAll(list);
    }
}
