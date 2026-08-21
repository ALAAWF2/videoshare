package com.orange.videoplayer;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LinkStore {

    private static final String PREFS = "myplyr_prefs";
    private static final String KEY = "links";

    private final SharedPreferences sp;

    public LinkStore(Context ctx) {
        sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public List<JSONObject> getAll() {
        List<JSONObject> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp.getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.getJSONObject(i));
            }
        } catch (JSONException ignored) {
        }
        Collections.sort(list, (a, b) -> Long.compare(b.optLong("ts"), a.optLong("ts")));
        return list;
    }

    private void saveAll(List<JSONObject> list) {
        JSONArray arr = new JSONArray();
        for (JSONObject o : list) arr.put(o);
        sp.edit().putString(KEY, arr.toString()).apply();
    }

    public JSONObject get(long id) {
        for (JSONObject o : getAll()) {
            if (o.optLong("id") == id) return o;
        }
        return null;
    }

    public long add(String name, String url) {
        long id = System.currentTimeMillis();
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("name", name);
            o.put("url", url);
            o.put("pos", 0L);
            o.put("dur", 0L);
            o.put("ts", id);
        } catch (JSONException ignored) {
        }
        List<JSONObject> list = getAll();
        list.add(o);
        saveAll(list);
        return id;
    }

    public void updatePosition(long id, long pos, long dur) {
        List<JSONObject> list = getAll();
        for (JSONObject o : list) {
            if (o.optLong("id") == id) {
                try {
                    o.put("pos", pos);
                    o.put("dur", dur);
                    o.put("ts", System.currentTimeMillis());
                } catch (JSONException ignored) {
                }
                break;
            }
        }
        saveAll(list);
    }

    public void updateUrl(long id, String url) {
        List<JSONObject> list = getAll();
        for (JSONObject o : list) {
            if (o.optLong("id") == id) {
                try {
                    o.put("url", url);
                } catch (JSONException ignored) {
                }
                break;
            }
        }
        saveAll(list);
    }

    public void rename(long id, String name) {
        List<JSONObject> list = getAll();
        for (JSONObject o : list) {
            if (o.optLong("id") == id) {
                try {
                    o.put("name", name);
                } catch (JSONException ignored) {
                }
                break;
            }
        }
        saveAll(list);
    }

    public void delete(long id) {
        List<JSONObject> list = getAll();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).optLong("id") == id) {
                list.remove(i);
                break;
            }
        }
        saveAll(list);
    }

    public static String autoName(String url) {
        try {
            Uri u = Uri.parse(url);
            String path = u.getLastPathSegment();
            if (path != null && !path.isEmpty()) {
                path = Uri.decode(path);
                int dot = path.lastIndexOf('.');
                if (dot > 0) path = path.substring(0, dot);
                path = path.replace('_', ' ').replace('-', ' ').replaceAll("\\s+", " ").trim();
                if (!path.isEmpty()) return path;
            }
            String host = u.getHost();
            if (host != null && !host.isEmpty()) return host;
        } catch (Exception ignored) {
        }
        return url;
    }

    public static class EpisodeInfo {
        public final String seriesBase;
        public final int episodeNum;

        public EpisodeInfo(String seriesBase, int episodeNum) {
            this.seriesBase = seriesBase;
            this.episodeNum = episodeNum;
        }
    }

    public static EpisodeInfo parseEpisodeInfo(String name) {
        if (name == null || name.trim().isEmpty()) {
            return new EpisodeInfo("", -1);
        }
        String trimmed = name.trim();

        // Pattern 1: Arabic / Ep markers at end ("حلقة 5", "الحلقة 5", "ح 5", "Episode 5", "Ep. 5")
        java.util.regex.Pattern p1 = java.util.regex.Pattern.compile(
                "(?i)(?:[\\s_\\-–—.:]+)?(?:الحلقة|حلقة|ح|episode|ep)\\.?\\s*(\\d+)\\s*$",
                java.util.regex.Pattern.UNICODE_CASE
        );
        java.util.regex.Matcher m1 = p1.matcher(trimmed);
        if (m1.find()) {
            try {
                int num = Integer.parseInt(m1.group(1));
                String base = trimmed.substring(0, m1.start()).replaceAll("[\\s_\\-–—.:]+$", "").trim();
                if (!base.isEmpty()) {
                    return new EpisodeInfo(base, num);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        // Pattern 2: SxxExx or Exx at end ("S01E05", "E05", "E5")
        java.util.regex.Pattern p2 = java.util.regex.Pattern.compile(
                "(?i)(?:[\\s_\\-–—.:]+)?(?:s\\d+\\s*)?e(\\d+)\\s*$"
        );
        java.util.regex.Matcher m2 = p2.matcher(trimmed);
        if (m2.find()) {
            try {
                int num = Integer.parseInt(m2.group(1));
                String base = trimmed.substring(0, m2.start()).replaceAll("[\\s_\\-–—.:]+$", "").trim();
                if (!base.isEmpty()) {
                    return new EpisodeInfo(base, num);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        // Pattern 3: Trailing digits preceded by separator or whitespace ("Show - 05", "Show_5")
        java.util.regex.Pattern p3 = java.util.regex.Pattern.compile(
                "[\\s_\\-–—.:]+(\\d+)\\s*$"
        );
        java.util.regex.Matcher m3 = p3.matcher(trimmed);
        if (m3.find()) {
            try {
                int num = Integer.parseInt(m3.group(1));
                String base = trimmed.substring(0, m3.start()).replaceAll("[\\s_\\-–—.:]+$", "").trim();
                if (!base.isEmpty()) {
                    return new EpisodeInfo(base, num);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return new EpisodeInfo(trimmed, -1);
    }

    public JSONObject getNextCandidate(long currentId, String currentName) {
        List<JSONObject> allEntries = getAll();
        if (currentName == null || allEntries == null || allEntries.isEmpty()) {
            return null;
        }
        EpisodeInfo currentInfo = parseEpisodeInfo(currentName);
        if (currentInfo.seriesBase.isEmpty()) {
            return null;
        }

        List<JSONObject> candidates = new ArrayList<>();
        List<EpisodeInfo> candidateInfos = new ArrayList<>();

        for (JSONObject entry : allEntries) {
            if (entry.optLong("id") == currentId) continue;
            String name = entry.optString("name");
            EpisodeInfo info = parseEpisodeInfo(name);
            if (info.seriesBase.equalsIgnoreCase(currentInfo.seriesBase)) {
                candidates.add(entry);
                candidateInfos.add(info);
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        if (candidates.size() == 1) {
            EpisodeInfo candInfo = candidateInfos.get(0);
            if (candInfo.episodeNum > currentInfo.episodeNum || candInfo.episodeNum == -1) {
                return candidates.get(0);
            }
            return null;
        }

        // Multiple candidates
        JSONObject bestGreater = null;
        int minGreaterEp = Integer.MAX_VALUE;

        JSONObject bestOverall = null;
        int minOverallEp = Integer.MAX_VALUE;

        for (int i = 0; i < candidates.size(); i++) {
            JSONObject c = candidates.get(i);
            int ep = candidateInfos.get(i).episodeNum;
            if (currentInfo.episodeNum >= 0 && ep > currentInfo.episodeNum) {
                if (ep < minGreaterEp) {
                    minGreaterEp = ep;
                    bestGreater = c;
                }
            }
            if (ep >= 0 && ep < minOverallEp) {
                minOverallEp = ep;
                bestOverall = c;
            }
        }

        if (bestGreater != null) {
            return bestGreater;
        }

        if (currentInfo.episodeNum == -1) {
            if (bestOverall != null) {
                return bestOverall;
            }
            return candidates.get(0);
        }

        return null;
    }
}
