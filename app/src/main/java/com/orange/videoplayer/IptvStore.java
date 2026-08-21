package com.orange.videoplayer;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class IptvStore {

    private static final String PREFS_NAME = "myplyr_iptv";
    private static final String KEY_SUBSCRIPTIONS = "subscriptions";
    private static final String KEY_FAVORITES = "favorites";

    public static final String TYPE_XTREAM = "xtream";
    public static final String TYPE_M3U = "m3u";

    public static final String[] DEFAULT_ACTION_TV_MIRRORS = new String[]{
            "tg7080.top:80",
            "top1tv.is:2095",
            "vux8.top:80",
            "hq-iptv.net",
            "ebtiger.site:80",
            "bellhtop.com:8080",
            "at566.net",
            "s1.u-on.to:2095",
            "ali5g30.top:80",
            "toytcl.xyz:8080",
            "riding12660.cdn-24.me",
            "sawa2026.vip:80",
            "istar1.pro:80",
            "fal2.xyz:80",
            "yshoots.com:80",
            "alico20.top:80",
            "belltv.org:80",
            "b1718o.top:80",
            "saromatv.org:80",
            "teratv.online:80"
    };

    private final SharedPreferences sp;

    public IptvStore(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public synchronized List<JSONObject> getAll() {
        List<JSONObject> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp.getString(KEY_SUBSCRIPTIONS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.getJSONObject(i));
            }
        } catch (JSONException ignored) {
        }
        Collections.sort(list, (a, b) -> Long.compare(b.optLong("ts"), a.optLong("ts")));
        return list;
    }

    private synchronized void saveAll(List<JSONObject> list) {
        JSONArray arr = new JSONArray();
        for (JSONObject o : list) {
            arr.put(o);
        }
        sp.edit().putString(KEY_SUBSCRIPTIONS, arr.toString()).apply();
    }

    public synchronized JSONObject get(long id) {
        for (JSONObject o : getAll()) {
            if (o.optLong("id") == id) {
                return o;
            }
        }
        return null;
    }

    public synchronized long addXtream(String name, String server, String username, String password) {
        return addXtream(name, server, username, password, Collections.emptyList());
    }

    public synchronized long addXtream(String name, String server, String username, String password, List<String> mirrors) {
        long id = System.currentTimeMillis();
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("type", TYPE_XTREAM);
            o.put("name", (name != null && !name.trim().isEmpty()) ? name.trim() : normalizeServerName(server));
            o.put("server", normalizeServerUrl(server));
            o.put("username", username != null ? username.trim() : "");
            o.put("password", password != null ? password.trim() : "");
            o.put("url", "");
            JSONArray mArr = new JSONArray();
            if (mirrors != null) {
                for (String m : mirrors) {
                    if (m != null && !m.trim().isEmpty()) {
                        mArr.put(m.trim());
                    }
                }
            }
            o.put("mirrors", mArr);
            o.put("ts", id);
        } catch (JSONException ignored) {
        }
        List<JSONObject> list = getAll();
        list.add(o);
        saveAll(list);
        return id;
    }

    public synchronized void setMirrors(long id, List<String> hosts) {
        List<JSONObject> list = getAll();
        for (JSONObject o : list) {
            if (o.optLong("id") == id) {
                try {
                    JSONArray arr = new JSONArray();
                    if (hosts != null) {
                        for (String h : hosts) {
                            if (h != null && !h.trim().isEmpty()) {
                                arr.put(h.trim());
                            }
                        }
                    }
                    o.put("mirrors", arr);
                } catch (JSONException ignored) {
                }
                break;
            }
        }
        saveAll(list);
    }

    public static List<String> getMirrors(JSONObject sub) {
        List<String> list = new ArrayList<>();
        if (sub == null) return list;
        JSONArray arr = sub.optJSONArray("mirrors");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                String m = arr.optString(i, "").trim();
                if (!m.isEmpty()) {
                    list.add(m);
                }
            }
        }
        return list;
    }

    public synchronized List<String> getMirrors(long id) {
        return getMirrors(get(id));
    }

    public synchronized void updateServer(long id, String server) {
        if (server == null || server.trim().isEmpty()) return;
        String normalized = normalizeServerUrl(server);
        List<JSONObject> list = getAll();
        for (JSONObject o : list) {
            if (o.optLong("id") == id) {
                try {
                    o.put("server", normalized);
                } catch (JSONException ignored) {
                }
                break;
            }
        }
        saveAll(list);
    }

    public synchronized long addM3u(String name, String url) {
        long id = System.currentTimeMillis();
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("type", TYPE_M3U);
            o.put("name", (name != null && !name.trim().isEmpty()) ? name.trim() : LinkStore.autoName(url));
            o.put("server", "");
            o.put("username", "");
            o.put("password", "");
            o.put("url", url != null ? url.trim() : "");
            o.put("mirrors", new JSONArray());
            o.put("ts", id);
        } catch (JSONException ignored) {
        }
        List<JSONObject> list = getAll();
        list.add(o);
        saveAll(list);
        return id;
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

    public synchronized List<JSONObject> getFavorites() {
        List<JSONObject> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp.getString(KEY_FAVORITES, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.getJSONObject(i));
            }
        } catch (JSONException ignored) {
        }
        Collections.sort(list, (a, b) -> Long.compare(b.optLong("ts"), a.optLong("ts")));
        return list;
    }

    private synchronized void saveFavorites(List<JSONObject> list) {
        JSONArray arr = new JSONArray();
        for (JSONObject o : list) {
            arr.put(o);
        }
        sp.edit().putString(KEY_FAVORITES, arr.toString()).apply();
    }

    public synchronized void addFavorite(String name, String url) {
        addFavorite(name, url, null);
    }

    public synchronized void addFavorite(String name, String url, String directUrl) {
        if (url == null || url.trim().isEmpty()) return;
        String cleanUrl = url.trim();
        List<JSONObject> list = getFavorites();
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
                if (name != null && !name.trim().isEmpty()) {
                    existing.put("name", name.trim());
                }
                if (directUrl != null && !directUrl.trim().isEmpty()) {
                    existing.put("direct_url", directUrl.trim());
                }
                existing.put("ts", now);
            } catch (JSONException ignored) {
            }
        } else {
            JSONObject o = new JSONObject();
            try {
                o.put("name", (name != null && !name.trim().isEmpty()) ? name.trim() : LinkStore.autoName(cleanUrl));
                o.put("url", cleanUrl);
                if (directUrl != null && !directUrl.trim().isEmpty()) {
                    o.put("direct_url", directUrl.trim());
                }
                o.put("ts", now);
                list.add(o);
            } catch (JSONException ignored) {
            }
        }
        saveFavorites(list);
    }

    public static void addFavoriteResolved(final Context ctx, final String name, final String streamUrl) {
        new IptvApiClient().resolveDirectUrl(streamUrl, new IptvApiClient.Callback<String>() {
            @Override
            public void onSuccess(String resolvedUrl) {
                String direct = (resolvedUrl != null && !resolvedUrl.isEmpty()) ? resolvedUrl : "";
                new IptvStore(ctx).addFavorite(name, streamUrl, direct);
                try {
                    Toast.makeText(ctx, ctx.getString(R.string.fav_added_direct), Toast.LENGTH_SHORT).show();
                } catch (Exception ignored) {
                }
            }

            @Override
            public void onError(String error) {
                new IptvStore(ctx).addFavorite(name, streamUrl, "");
                try {
                    Toast.makeText(ctx, ctx.getString(R.string.fav_added), Toast.LENGTH_SHORT).show();
                } catch (Exception ignored) {
                }
            }
        });
    }

    public synchronized void removeFavorite(String url) {
        if (url == null) return;
        String cleanUrl = url.trim();
        List<JSONObject> list = getFavorites();
        for (int i = 0; i < list.size(); i++) {
            if (cleanUrl.equals(list.get(i).optString("url"))) {
                list.remove(i);
                break;
            }
        }
        saveFavorites(list);
    }

    public synchronized boolean isFavorite(String url) {
        if (url == null || url.trim().isEmpty()) return false;
        String cleanUrl = url.trim();
        for (JSONObject o : getFavorites()) {
            if (cleanUrl.equals(o.optString("url"))) {
                return true;
            }
        }
        return false;
    }

    public static String normalizeServerUrl(String server) {
        if (server == null) return "";
        String s = server.trim();
        if (!s.startsWith("http://") && !s.startsWith("https://")) {
            s = "http://" + s;
        }
        if (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    public static String normalizeServerName(String server) {
        String normalized = normalizeServerUrl(server);
        return LinkStore.autoName(normalized);
    }
}
