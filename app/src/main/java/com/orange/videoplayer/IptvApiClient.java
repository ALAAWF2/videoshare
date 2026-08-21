package com.orange.videoplayer;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IptvApiClient {

    private static final int TIMEOUT_MS = 15000;
    private static final int FAILOVER_TIMEOUT_MS = 8000;
    private static final String USER_AGENT = "IPTVSmartersPro/3.1.5 (Android; Mobile)";

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String error);
    }

    public interface OnServerChangeListener {
        void onServerChanged(String newWorkingServer);
    }

    public static class AuthResult {
        public final String server;
        public final JSONObject response;

        public AuthResult(String server, JSONObject response) {
            this.server = server;
            this.response = response;
        }
    }

    private static final Map<String, String> DOH_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    public static String getHostKey(String urlOrHost) {
        if (urlOrHost == null) return "";
        String s = urlOrHost.trim().toLowerCase();
        if (s.startsWith("http://")) s = s.substring(7);
        if (s.startsWith("https://")) s = s.substring(8);
        int slash = s.indexOf('/');
        if (slash >= 0) s = s.substring(0, slash);
        if (s.endsWith(":80")) s = s.substring(0, s.length() - 3);
        return s;
    }

    public static String resolveHostViaDoh(String host) {
        if (host == null || host.trim().isEmpty()) return null;
        String cleanHost = host.trim();
        if (cleanHost.startsWith("http://")) cleanHost = cleanHost.substring(7);
        if (cleanHost.startsWith("https://")) cleanHost = cleanHost.substring(8);
        int slash = cleanHost.indexOf('/');
        if (slash >= 0) cleanHost = cleanHost.substring(0, slash);
        int colon = cleanHost.indexOf(':');
        if (colon >= 0) cleanHost = cleanHost.substring(0, colon);
        cleanHost = cleanHost.trim().toLowerCase();

        if (cleanHost.isEmpty()) return null;

        // If host is already an IPv4 address, return it
        if (cleanHost.matches("^\\d{1,3}(\\.\\d{1,3}){3}$")) {
            return cleanHost;
        }

        if (DOH_CACHE.containsKey(cleanHost)) {
            return DOH_CACHE.get(cleanHost);
        }

        HttpURLConnection conn = null;
        BufferedReader reader = null;
        try {
            URL url = new URL("https://cloudflare-dns.com/dns-query?name=" + URLEncoder.encode(cleanHost, "UTF-8") + "&type=A");
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestProperty("Accept", "application/dns-json");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setInstanceFollowRedirects(true);
            conn.connect();

            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_OK) {
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                JSONObject obj = new JSONObject(sb.toString());
                JSONArray answers = obj.optJSONArray("Answer");
                if (answers != null) {
                    for (int i = 0; i < answers.length(); i++) {
                        JSONObject ans = answers.optJSONObject(i);
                        if (ans != null && ans.optInt("type") == 1) {
                            String ip = ans.optString("data", "").trim();
                            if (!ip.isEmpty() && ip.matches("^\\d{1,3}(\\.\\d{1,3}){3}$")) {
                                DOH_CACHE.put(cleanHost, ip);
                                return ip;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            try {
                if (reader != null) reader.close();
            } catch (Exception ignored) {
            }
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    public static List<String> buildCandidates(String server, List<String> mirrors) {
        List<String> result = new ArrayList<>();
        java.util.Set<String> seenHosts = new java.util.LinkedHashSet<>();
        if (mirrors == null || mirrors.isEmpty()) {
            mirrors = java.util.Arrays.asList(IptvStore.DEFAULT_ACTION_TV_MIRRORS);
        }

        List<String> rawList = new ArrayList<>();
        if (server != null && !server.trim().isEmpty()) {
            rawList.add(server.trim());
        }
        if (mirrors != null) {
            for (String m : mirrors) {
                if (m != null && !m.trim().isEmpty()) {
                    rawList.add(m.trim());
                }
            }
        }

        // 1. Add domain forms
        for (String raw : rawList) {
            String key = getHostKey(raw);
            if (!key.isEmpty() && !seenHosts.contains(key)) {
                seenHosts.add(key);
                result.add(IptvStore.normalizeServerUrl(raw));
            }
        }

        // 2. Add IP forms via DoH (extra no-VPN attempt, harmless)
        for (String raw : rawList) {
            try {
                String clean = raw.trim();
                if (clean.startsWith("http://")) clean = clean.substring(7);
                if (clean.startsWith("https://")) clean = clean.substring(8);
                int slash = clean.indexOf('/');
                if (slash >= 0) clean = clean.substring(0, slash);
                String port = "";
                int colon = clean.indexOf(':');
                String domain = clean;
                if (colon >= 0) {
                    domain = clean.substring(0, colon);
                    port = clean.substring(colon + 1);
                }
                String ip = resolveHostViaDoh(domain);
                if (ip != null && !ip.isEmpty()) {
                    String ipCandidate = "http://" + ip + (port.isEmpty() || "80".equals(port) ? "" : ":" + port);
                    String ipKey = getHostKey(ipCandidate);
                    if (!ipKey.isEmpty() && !seenHosts.contains(ipKey) && !seenHosts.contains(ip)) {
                        seenHosts.add(ipKey);
                        seenHosts.add(ip);
                        result.add(IptvStore.normalizeServerUrl(ipCandidate));
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return result;
    }

    private interface QueryBuilder {
        String buildUrl(String serverBase) throws Exception;
    }

    private interface ResponseParser<T> {
        T parse(String workingServer, String responseBody) throws Exception;
    }

    private <T> void executeWithFailover(
            String server,
            List<String> mirrors,
            QueryBuilder queryBuilder,
            ResponseParser<T> parser,
            OnServerChangeListener serverListener,
            Callback<T> callback,
            String defaultErrorMessage
    ) {
        executor.execute(() -> {
            List<String> candidates = buildCandidates(server, mirrors);
            if (candidates.isEmpty() && server != null && !server.trim().isEmpty()) {
                candidates.add(IptvStore.normalizeServerUrl(server));
            }

            Exception lastException = null;
            for (String cand : candidates) {
                try {
                    String urlStr = queryBuilder.buildUrl(cand);
                    int timeout = candidates.size() > 1 ? FAILOVER_TIMEOUT_MS : TIMEOUT_MS;
                    String jsonStr = httpGet(urlStr, timeout);
                    T result = parser.parse(cand, jsonStr);
                    if (result != null) {
                        if (serverListener != null && !cand.equalsIgnoreCase(IptvStore.normalizeServerUrl(server))) {
                            mainHandler.post(() -> serverListener.onServerChanged(cand));
                        }
                        mainHandler.post(() -> callback.onSuccess(result));
                        return;
                    }
                } catch (Exception e) {
                    lastException = e;
                }
            }

            final String finalErrorMsg;
            if (candidates.size() > 1) {
                finalErrorMsg = "كل السيرفرات محجوبة على هذه الشبكة. الحل: بدّل لشبكة الجوال أو فعّل VPN مجاني (WARP - تطبيق 1.1.1.1)";
            } else {
                finalErrorMsg = defaultErrorMessage + (lastException != null && lastException.getMessage() != null ? ": " + lastException.getMessage() : "");
            }
            mainHandler.post(() -> callback.onError(finalErrorMsg));
        });
    }

    public void authenticateXtream(String server, List<String> mirrors, String username, String password, Callback<AuthResult> callback) {
        executor.execute(() -> {
            List<String> candidates = buildCandidates(server, mirrors);
            if (candidates.isEmpty() && server != null && !server.trim().isEmpty()) {
                candidates.add(IptvStore.normalizeServerUrl(server));
            }

            String lastAuthError = null;
            Exception lastException = null;

            for (String cand : candidates) {
                try {
                    String u = URLEncoder.encode(username, "UTF-8");
                    String p = URLEncoder.encode(password, "UTF-8");
                    String urlStr = cand + "/player_api.php?username=" + u + "&password=" + p;

                    int timeout = candidates.size() > 1 ? FAILOVER_TIMEOUT_MS : TIMEOUT_MS;
                    String jsonStr = httpGet(urlStr, timeout);
                    JSONObject root = new JSONObject(jsonStr);

                    JSONObject userInfo = root.optJSONObject("user_info");
                    if (userInfo != null) {
                        int auth = userInfo.optInt("auth", -1);
                        String status = userInfo.optString("status", "");
                        if (auth == 1 || "Active".equalsIgnoreCase(status) || auth == -1) {
                            mainHandler.post(() -> callback.onSuccess(new AuthResult(cand, root)));
                            return;
                        } else if (auth == 0 || "Disabled".equalsIgnoreCase(status) || "Expired".equalsIgnoreCase(status)) {
                            lastAuthError = "بيانات الاشتراك غير صحيحة أو منتهية الصلاحية";
                        }
                    }
                } catch (Exception e) {
                    lastException = e;
                }
            }

            final String finalAuthError = lastAuthError;
            final Exception finalException = lastException;
            if (finalAuthError != null) {
                mainHandler.post(() -> callback.onError(finalAuthError));
            } else if (candidates.size() > 1) {
                mainHandler.post(() -> callback.onError("كل السيرفرات محجوبة على هذه الشبكة. الحل: بدّل لشبكة الجوال أو فعّل VPN مجاني (WARP - تطبيق 1.1.1.1)"));
            } else {
                String msg = "تعذر الاتصال بالسيرفر: " + (finalException != null && finalException.getMessage() != null ? finalException.getMessage() : "خطأ غير معروف");
                mainHandler.post(() -> callback.onError(msg));
            }
        });
    }

    public void authenticateXtream(String server, String username, String password, Callback<JSONObject> callback) {
        authenticateXtream(server, null, username, password, new Callback<AuthResult>() {
            @Override
            public void onSuccess(AuthResult result) {
                callback.onSuccess(result.response);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void getCategories(String server, String username, String password, String section, Callback<List<IptvModels.Category>> callback) {
        getCategories(server, null, username, password, section, null, callback);
    }

    public void getCategories(String server, List<String> mirrors, String username, String password, String section, OnServerChangeListener serverListener, Callback<List<IptvModels.Category>> callback) {
        String action = "get_live_categories";
        if ("vod".equalsIgnoreCase(section)) {
            action = "get_vod_categories";
        } else if ("series".equalsIgnoreCase(section)) {
            action = "get_series_categories";
        }
        final String finalAction = action;

        executeWithFailover(
                server,
                mirrors,
                cand -> {
                    String u = URLEncoder.encode(username, "UTF-8");
                    String p = URLEncoder.encode(password, "UTF-8");
                    return cand + "/player_api.php?username=" + u + "&password=" + p + "&action=" + finalAction;
                },
                (workingServer, jsonStr) -> {
                    List<IptvModels.Category> list = new ArrayList<>();
                    list.add(new IptvModels.Category("all", "الكل"));

                    JSONArray arr = new JSONArray(jsonStr);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        String id = obj.optString("category_id");
                        String name = obj.optString("category_name");
                        if (name.isEmpty()) name = "فئة " + id;
                        list.add(new IptvModels.Category(id, name));
                    }
                    return list;
                },
                serverListener,
                callback,
                "تعذر تحميل الأقسام"
        );
    }

    public void getItems(String server, String username, String password, String section, String categoryId, Callback<List<IptvModels.Item>> callback) {
        getItems(server, null, username, password, section, categoryId, null, callback);
    }

    public void getItems(String server, List<String> mirrors, String username, String password, String section, String categoryId, OnServerChangeListener serverListener, Callback<List<IptvModels.Item>> callback) {
        String action = "get_live_streams";
        if ("vod".equalsIgnoreCase(section)) {
            action = "get_vod_streams";
        } else if ("series".equalsIgnoreCase(section)) {
            action = "get_series";
        }
        final String finalAction = action;

        executeWithFailover(
                server,
                mirrors,
                cand -> {
                    String u = URLEncoder.encode(username, "UTF-8");
                    String p = URLEncoder.encode(password, "UTF-8");
                    String urlStr = cand + "/player_api.php?username=" + u + "&password=" + p + "&action=" + finalAction;
                    if (categoryId != null && !categoryId.isEmpty() && !"all".equalsIgnoreCase(categoryId)) {
                        urlStr += "&category_id=" + URLEncoder.encode(categoryId, "UTF-8");
                    }
                    return urlStr;
                },
                (workingServer, jsonStr) -> {
                    List<IptvModels.Item> list = new ArrayList<>();
                    JSONArray arr = new JSONArray(jsonStr);

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        String id;
                        String name;
                        String iconUrl = null;
                        String containerExt = obj.optString("container_extension", "");
                        int num = obj.optInt("num", i + 1);
                        String catId = obj.optString("category_id", "");
                        String streamUrl = null;

                        String rating = obj.optString("rating", obj.optString("rating_5based", ""));
                        String year = obj.optString("year", obj.optString("releaseDate", obj.optString("releasedate", "")));
                        if (year.length() > 4) {
                            try {
                                year = year.substring(0, 4);
                            } catch (Exception ignored) {}
                        }

                        if ("series".equalsIgnoreCase(section)) {
                            id = obj.optString("series_id");
                            name = obj.optString("name", obj.optString("title", "مسلسل"));
                            iconUrl = obj.optString("cover", obj.optString("stream_icon", ""));
                        } else if ("vod".equalsIgnoreCase(section)) {
                            id = obj.optString("stream_id");
                            name = obj.optString("name", obj.optString("title", "فيلم"));
                            iconUrl = obj.optString("stream_icon", "");
                            String ext = containerExt.isEmpty() ? "mp4" : containerExt;
                            streamUrl = workingServer + "/movie/" + username + "/" + password + "/" + id + "." + ext;
                        } else {
                            // Live
                            id = obj.optString("stream_id");
                            name = obj.optString("name", "قناة");
                            iconUrl = obj.optString("stream_icon", "");
                            String ext = containerExt.isEmpty() ? "ts" : containerExt;
                            streamUrl = workingServer + "/live/" + username + "/" + password + "/" + id + "." + ext;
                        }

                        IptvModels.Item item = new IptvModels.Item(id, name, iconUrl, containerExt, section, streamUrl, catId, num);
                        item.rating = rating;
                        item.year = year;
                        item.plot = obj.optString("plot", obj.optString("description", ""));
                        item.genre = obj.optString("genre", "");
                        list.add(item);
                    }
                    return list;
                },
                serverListener,
                callback,
                "تعذر تحميل المحتوى"
        );
    }

    public void getVodInfo(String server, String username, String password, String vodId, Callback<IptvModels.VodDetails> callback) {
        getVodInfo(server, null, username, password, vodId, null, callback);
    }

    public void getVodInfo(String server, List<String> mirrors, String username, String password, String vodId, OnServerChangeListener serverListener, Callback<IptvModels.VodDetails> callback) {
        executeWithFailover(
                server,
                mirrors,
                cand -> {
                    String u = URLEncoder.encode(username, "UTF-8");
                    String p = URLEncoder.encode(password, "UTF-8");
                    return cand + "/player_api.php?username=" + u + "&password=" + p + "&action=get_vod_info&vod_id=" + URLEncoder.encode(vodId, "UTF-8");
                },
                (workingServer, jsonStr) -> {
                    JSONObject root = new JSONObject(jsonStr);
                    JSONObject info = root.optJSONObject("info");
                    JSONObject movieData = root.optJSONObject("movie_data");

                    String name = info != null ? info.optString("name", "") : "";
                    String image = info != null ? info.optString("movie_image", "") : "";
                    String rating = info != null ? info.optString("rating", "") : "";
                    String releaseDate = info != null ? info.optString("releasedate", info.optString("release_date", "")) : "";
                    String duration = info != null ? info.optString("duration", info.optString("episode_run_time", "")) : "";
                    String plot = info != null ? info.optString("plot", info.optString("description", "")) : "";
                    String cast = info != null ? info.optString("cast", info.optString("actors", "")) : "";
                    String director = info != null ? info.optString("director", "") : "";
                    String genre = info != null ? info.optString("genre", "") : "";
                    String containerExt = movieData != null ? movieData.optString("container_extension", "mp4") : "mp4";

                    return new IptvModels.VodDetails(vodId, name, image, rating, releaseDate, duration, plot, cast, director, genre, containerExt);
                },
                serverListener,
                callback,
                "تعذر تحميل تفاصيل الفيلم"
        );
    }


    public void getSeriesInfo(String server, String username, String password, String seriesId, Callback<List<IptvModels.Season>> callback) {
        getSeriesInfo(server, null, username, password, seriesId, null, callback);
    }

    public void getSeriesInfo(String server, List<String> mirrors, String username, String password, String seriesId, OnServerChangeListener serverListener, Callback<List<IptvModels.Season>> callback) {
        executeWithFailover(
                server,
                mirrors,
                cand -> {
                    String u = URLEncoder.encode(username, "UTF-8");
                    String p = URLEncoder.encode(password, "UTF-8");
                    return cand + "/player_api.php?username=" + u + "&password=" + p + "&action=get_series_info&series_id=" + URLEncoder.encode(seriesId, "UTF-8");
                },
                (workingServer, jsonStr) -> {
                    JSONObject root = new JSONObject(jsonStr);
                    Map<String, IptvModels.Season> seasonMap = new LinkedHashMap<>();

                    // Parse episodes object: {"1": [...], "2": [...]}
                    JSONObject episodesObj = root.optJSONObject("episodes");
                    if (episodesObj != null) {
                        Iterator<String> keys = episodesObj.keys();
                        while (keys.hasNext()) {
                            String sKey = keys.next();
                            JSONArray epArr = episodesObj.optJSONArray(sKey);
                            if (epArr != null) {
                                IptvModels.Season season = seasonMap.get(sKey);
                                if (season == null) {
                                    season = new IptvModels.Season(sKey, "الموسم " + sKey);
                                    seasonMap.put(sKey, season);
                                }
                                for (int i = 0; i < epArr.length(); i++) {
                                    JSONObject epObj = epArr.getJSONObject(i);
                                    String id = epObj.optString("id");
                                    String epNum = epObj.optString("episode_num", String.valueOf(i + 1));
                                    String title = epObj.optString("title", "الحلقة " + epNum);
                                    String containerExt = epObj.optString("container_extension", "mp4");
                                    if (containerExt.isEmpty()) containerExt = "mp4";
                                    String icon = epObj.optJSONObject("info") != null ? epObj.optJSONObject("info").optString("movie_image", "") : "";
                                    String streamUrl = workingServer + "/series/" + username + "/" + password + "/" + id + "." + containerExt;

                                    season.episodes.add(new IptvModels.Episode(id, epNum, title, containerExt, icon, streamUrl));
                                }
                            }
                        }
                    } else {
                        // Sometimes episodes is an array
                        JSONArray episodesArr = root.optJSONArray("episodes");
                        if (episodesArr != null) {
                            for (int i = 0; i < episodesArr.length(); i++) {
                                JSONObject epObj = episodesArr.getJSONObject(i);
                                String id = epObj.optString("id");
                                String sNum = epObj.optString("season", "1");
                                String epNum = epObj.optString("episode_num", String.valueOf(i + 1));
                                String title = epObj.optString("title", "الحلقة " + epNum);
                                String containerExt = epObj.optString("container_extension", "mp4");
                                if (containerExt.isEmpty()) containerExt = "mp4";
                                String streamUrl = workingServer + "/series/" + username + "/" + password + "/" + id + "." + containerExt;

                                IptvModels.Season season = seasonMap.get(sNum);
                                if (season == null) {
                                    season = new IptvModels.Season(sNum, "الموسم " + sNum);
                                    seasonMap.put(sNum, season);
                                }
                                season.episodes.add(new IptvModels.Episode(id, epNum, title, containerExt, "", streamUrl));
                            }
                        }
                    }

                    return new ArrayList<>(seasonMap.values());
                },
                serverListener,
                callback,
                "تعذر تحميل حلقات المسلسل"
        );
    }

    public void parseM3uPlaylist(String playlistUrl, Callback<Map<IptvModels.Category, List<IptvModels.Item>>> callback) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            BufferedReader reader = null;
            try {
                URL url = new URL(playlistUrl.trim());
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setRequestProperty("User-Agent", USER_AGENT);
                conn.setInstanceFollowRedirects(true);
                conn.connect();

                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    mainHandler.post(() -> callback.onError("تعذر جلب القائمة: كود الاستجابة " + responseCode));
                    return;
                }

                reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                String line;
                String currentTitle = null;
                String currentGroup = "القنوات العامة";
                String currentLogo = null;
                int channelCount = 0;

                Map<String, IptvModels.Category> categoryMap = new LinkedHashMap<>();
                Map<String, List<IptvModels.Item>> itemsByCategory = new LinkedHashMap<>();

                IptvModels.Category allCat = new IptvModels.Category("all", "الكل");
                categoryMap.put("all", allCat);
                itemsByCategory.put("all", new ArrayList<>());

                Pattern extinfPattern = Pattern.compile("^#EXTINF:.*?(?:group-title=\"([^\"]*)\")?.*?(?:tvg-logo=\"([^\"]*)\")?.*?,(.*)$", Pattern.CASE_INSENSITIVE);

                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    if (line.startsWith("#EXTINF:")) {
                        currentGroup = "القنوات العامة";
                        currentLogo = null;
                        currentTitle = "قناة " + (channelCount + 1);

                        // Try regex match
                        Matcher m = extinfPattern.matcher(line);
                        if (m.find()) {
                            String group = m.group(1);
                            if (group != null && !group.trim().isEmpty()) {
                                currentGroup = group.trim();
                            }
                            String logo = m.group(2);
                            if (logo != null && !logo.trim().isEmpty()) {
                                currentLogo = logo.trim();
                            }
                            String title = m.group(3);
                            if (title != null && !title.trim().isEmpty()) {
                                currentTitle = title.trim();
                            }
                        } else {
                            // Simple fallback: text after last comma is title
                            int commaIdx = line.lastIndexOf(',');
                            if (commaIdx >= 0 && commaIdx < line.length() - 1) {
                                currentTitle = line.substring(commaIdx + 1).trim();
                            }
                            // Extract group-title if present
                            int gIdx = line.indexOf("group-title=\"");
                            if (gIdx >= 0) {
                                int gEnd = line.indexOf("\"", gIdx + 13);
                                if (gEnd > gIdx) {
                                    currentGroup = line.substring(gIdx + 13, gEnd).trim();
                                }
                            }
                        }
                    } else if (!line.startsWith("#")) {
                        // URL line
                        if (line.startsWith("http://") || line.startsWith("https://")) {
                            channelCount++;
                            String streamUrl = line;
                            String catId = currentGroup.toLowerCase();

                            IptvModels.Category cat = categoryMap.get(catId);
                            if (cat == null) {
                                cat = new IptvModels.Category(catId, currentGroup);
                                categoryMap.put(catId, cat);
                                itemsByCategory.put(catId, new ArrayList<>());
                            }

                            IptvModels.Item item = new IptvModels.Item(
                                    String.valueOf(channelCount),
                                    currentTitle,
                                    currentLogo,
                                    "m3u8",
                                    "live",
                                    streamUrl,
                                    catId,
                                    channelCount
                            );

                            itemsByCategory.get(catId).add(item);
                            itemsByCategory.get("all").add(item);
                        }
                    }
                }

                if (channelCount == 0) {
                    mainHandler.post(() -> callback.onError("لم يتم العثور على أي قنوات في رابط القائمة"));
                    return;
                }

                // Update category counts
                for (Map.Entry<String, IptvModels.Category> entry : categoryMap.entrySet()) {
                    List<IptvModels.Item> list = itemsByCategory.get(entry.getKey());
                    entry.getValue().count = list != null ? list.size() : 0;
                }

                Map<IptvModels.Category, List<IptvModels.Item>> result = new LinkedHashMap<>();
                for (Map.Entry<String, IptvModels.Category> entry : categoryMap.entrySet()) {
                    result.put(entry.getValue(), itemsByCategory.get(entry.getKey()));
                }

                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("تعذر قراءة قائمة M3U: " + (e.getMessage() != null ? e.getMessage() : "")));
            } finally {
                try {
                    if (reader != null) reader.close();
                } catch (Exception ignored) {
                }
                if (conn != null) conn.disconnect();
            }
        });
    }

    private static String httpGet(String urlStr) throws Exception {
        return httpGet(urlStr, TIMEOUT_MS);
    }

    private static String httpGet(String urlStr, int timeoutMs) throws Exception {
        HttpURLConnection conn = null;
        BufferedReader reader = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setInstanceFollowRedirects(true);
            conn.connect();

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new Exception("كود الاستجابة " + code);
            }

            reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } finally {
            try {
                if (reader != null) reader.close();
            } catch (Exception ignored) {
            }
            if (conn != null) conn.disconnect();
        }
    }

    public void resolveDirectUrl(String streamUrl, Callback<String> callback) {
        executor.execute(() -> {
            try {
                String resolved = resolveDirectUrlSync(streamUrl);
                mainHandler.post(() -> callback.onSuccess(resolved));
            } catch (Exception e) {
                String msg = "تعذر استخراج الرابط المباشر" + (e.getMessage() != null && !e.getMessage().isEmpty() ? " (" + e.getMessage() + ")" : "");
                mainHandler.post(() -> callback.onError(msg));
            }
        });
    }

    public static String resolveDirectUrlSync(String streamUrl) throws Exception {
        if (streamUrl == null || streamUrl.trim().isEmpty()) {
            throw new Exception("الرابط غير صالح");
        }
        String currentUrl = streamUrl.trim();
        int redirects = 0;
        final int maxRedirects = 5;

        while (redirects < maxRedirects) {
            HttpURLConnection conn = null;
            int responseCode = -1;

            try {
                conn = openConnectionForDirectUrl(currentUrl, "HEAD");
                responseCode = conn.getResponseCode();
            } catch (Exception e) {
                responseCode = -1;
            }

            if (responseCode == -1 || responseCode == HttpURLConnection.HTTP_BAD_METHOD || responseCode == 405 || responseCode == 400 || responseCode == 403 || responseCode == 501) {
                if (conn != null) {
                    try { conn.disconnect(); } catch (Exception ignored) {}
                    conn = null;
                }
                conn = openConnectionForDirectUrl(currentUrl, "GET");
                responseCode = conn.getResponseCode();
            }

            try {
                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM
                        || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                        || responseCode == HttpURLConnection.HTTP_SEE_OTHER
                        || responseCode == 307
                        || responseCode == 308) {
                    String location = conn.getHeaderField("Location");
                    if (location != null && !location.trim().isEmpty()) {
                        URL base = new URL(currentUrl);
                        URL resolved = new URL(base, location.trim());
                        String nextUrl = resolved.toString();
                        if (nextUrl.equals(currentUrl)) {
                            return nextUrl;
                        }
                        currentUrl = nextUrl;
                        redirects++;
                        continue;
                    }
                    return currentUrl;
                } else if (responseCode >= 200 && responseCode < 400) {
                    return currentUrl;
                } else {
                    throw new Exception("كود الاستجابة " + responseCode);
                }
            } finally {
                if (conn != null) {
                    try { conn.disconnect(); } catch (Exception ignored) {}
                }
            }
        }
        return currentUrl;
    }

    private static HttpURLConnection openConnectionForDirectUrl(String urlStr, String method) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestMethod(method);
        return conn;
    }

    public enum MirrorStatus {
        OK,
        BLOCKED_OR_DEAD,
        AUTH_ERROR
    }

    public static class MirrorHostResult {
        public final String host;
        public final MirrorStatus status;
        public final long latencyMs;

        public MirrorHostResult(String host, MirrorStatus status, long latencyMs) {
            this.host = host;
            this.status = status;
            this.latencyMs = latencyMs;
        }
    }

    public static class MirrorProbeSummary {
        public final List<MirrorHostResult> results;
        public final String firstOkHost;

        public MirrorProbeSummary(List<MirrorHostResult> results, String firstOkHost) {
            this.results = results;
            this.firstOkHost = firstOkHost;
        }
    }

    public interface MirrorProbeCallback {
        void onHostProbed(MirrorHostResult hostResult, int currentIndex, int totalCount);
        void onComplete(MirrorProbeSummary summary);
    }

    public void probeMirrors(String server, List<String> mirrors, String username, String password, MirrorProbeCallback callback) {
        executor.execute(() -> {
            List<String> candidates = buildCandidates(server, mirrors);
            if (candidates.isEmpty() && server != null && !server.trim().isEmpty()) {
                candidates.add(IptvStore.normalizeServerUrl(server));
            }

            List<MirrorHostResult> allResults = new ArrayList<>();
            String firstOkHost = null;
            final int total = candidates.size();

            for (int i = 0; i < total; i++) {
                String cand = candidates.get(i);
                long t0 = System.currentTimeMillis();
                MirrorStatus status = MirrorStatus.BLOCKED_OR_DEAD;

                HttpURLConnection conn = null;
                BufferedReader reader = null;
                try {
                    String u = URLEncoder.encode(username != null ? username : "", "UTF-8");
                    String p = URLEncoder.encode(password != null ? password : "", "UTF-8");
                    String urlStr = cand + "/player_api.php?username=" + u + "&password=" + p;

                    URL url = new URL(urlStr);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(6000);
                    conn.setReadTimeout(6000);
                    conn.setRequestProperty("User-Agent", USER_AGENT);
                    conn.setInstanceFollowRedirects(true);
                    conn.connect();

                    int code = conn.getResponseCode();
                    if (code == HttpURLConnection.HTTP_OK) {
                        reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        String body = sb.toString().trim();
                        if (!body.isEmpty() && body.startsWith("{")) {
                            JSONObject root = new JSONObject(body);
                            JSONObject userInfo = root.optJSONObject("user_info");
                            if (userInfo != null) {
                                int auth = userInfo.optInt("auth", -1);
                                String st = userInfo.optString("status", "");
                                if (auth == 1 || "Active".equalsIgnoreCase(st) || auth == -1) {
                                    status = MirrorStatus.OK;
                                } else if (auth == 0 || "Disabled".equalsIgnoreCase(st) || "Expired".equalsIgnoreCase(st)) {
                                    status = MirrorStatus.AUTH_ERROR;
                                } else {
                                    status = MirrorStatus.OK;
                                }
                            } else {
                                status = MirrorStatus.BLOCKED_OR_DEAD;
                            }
                        } else {
                            status = MirrorStatus.BLOCKED_OR_DEAD;
                        }
                    } else {
                        status = MirrorStatus.BLOCKED_OR_DEAD;
                    }
                } catch (Exception e) {
                    status = MirrorStatus.BLOCKED_OR_DEAD;
                } finally {
                    try {
                        if (reader != null) reader.close();
                    } catch (Exception ignored) {
                    }
                    if (conn != null) conn.disconnect();
                }

                long latency = Math.max(1, System.currentTimeMillis() - t0);
                MirrorHostResult hostResult = new MirrorHostResult(cand, status, latency);
                allResults.add(hostResult);

                if (status == MirrorStatus.OK && firstOkHost == null) {
                    firstOkHost = cand;
                }

                final int currentIndex = i + 1;
                if (callback != null) {
                    mainHandler.post(() -> callback.onHostProbed(hostResult, currentIndex, total));
                }
            }

            final String finalFirstOk = firstOkHost;
            if (callback != null) {
                mainHandler.post(() -> callback.onComplete(new MirrorProbeSummary(allResults, finalFirstOk)));
            }
        });
    }

    public void probeMirrors(String server, List<String> mirrors, String username, String password, Callback<MirrorProbeSummary> callback) {
        probeMirrors(server, mirrors, username, password, new MirrorProbeCallback() {
            @Override
            public void onHostProbed(MirrorHostResult hostResult, int currentIndex, int totalCount) {
            }

            @Override
            public void onComplete(MirrorProbeSummary summary) {
                if (callback != null) {
                    callback.onSuccess(summary);
                }
            }
        });
    }
}
