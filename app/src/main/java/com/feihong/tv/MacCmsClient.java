package com.feihong.tv;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MacCmsClient {
    private static final int TIMEOUT_MS = 9000;
    private static final String[] API_SUFFIXES = {"/api.php/provide/vod/", "/index.php/api.php/provide/vod/", "/provide/vod/"};

    private MacCmsClient() { }

    static Models.Source discover(String address, String displayName) throws Exception {
        String normalized = normalizeAddress(address);
        List<String> candidates = new ArrayList<>();
        if (normalized.contains("provide/vod")) candidates.add(ensureTrailingSlash(normalized));
        else for (String suffix : API_SUFFIXES) candidates.add(origin(normalized) + suffix);
        Exception lastError = null;
        for (String candidate : candidates) {
            try {
                fetchPage(new Models.Source(candidate, displayName, candidate, "unknown"), "", "", 1);
                String name = trim(displayName).length() == 0 ? SourceRepository.endpointHost(candidate) : trim(displayName);
                return new Models.Source(candidate, name, candidate, "healthy");
            } catch (Exception error) {
                lastError = error;
            }
        }
        throw lastError == null ? new Exception("未识别到可用 MACCMS 数据源") : lastError;
    }

    static Models.Page fetchPage(Models.Source source, String typeId, String keyword, int page) throws Exception {
        Uri.Builder builder = Uri.parse(source.apiUrl).buildUpon()
                .appendQueryParameter("ac", "list")
                .appendQueryParameter("pg", String.valueOf(Math.max(1, page)))
                .appendQueryParameter("pagesize", "24")
                .appendQueryParameter("by", "time");
        if (trim(typeId).length() > 0) builder.appendQueryParameter("t", typeId);
        if (trim(keyword).length() > 0) builder.appendQueryParameter("wd", keyword);
        JSONObject response = getJson(builder.build().toString());
        JSONArray list = response.optJSONArray("list");
        List<Models.Vod> items = new ArrayList<>();
        List<Models.Category> categories = new ArrayList<>();
        Map<String, Models.Category> categoryMap = new LinkedHashMap<>();
        if (list != null) {
            for (int index = 0; index < list.length(); index++) {
                JSONObject raw = list.optJSONObject(index);
                if (raw == null) continue;
                Models.Vod item = mapVod(raw, source.apiUrl);
                if (item != null) items.add(item);
                String categoryId = first(raw, "type_id", "tid");
                String categoryName = first(raw, "type_name", "type");
                String parentId = first(raw, "type_id_1", "type_pid", "parent_id");
                if (categoryId.length() > 0 && categoryName.length() > 0) {
                    categoryMap.put(categoryId, new Models.Category(categoryId, categoryName, "0".equals(parentId) ? "" : parentId));
                }
            }
        }
        JSONArray classList = response.optJSONArray("class");
        if (classList != null) {
            for (int index = 0; index < classList.length(); index++) {
                JSONObject raw = classList.optJSONObject(index);
                if (raw == null) continue;
                String categoryId = first(raw, "type_id", "tid", "id");
                String categoryName = first(raw, "type_name", "name", "type");
                String parentId = first(raw, "type_pid", "type_id_1", "parent_id", "pid");
                if (categoryId.length() > 0 && categoryName.length() > 0) {
                    categoryMap.put(categoryId, new Models.Category(categoryId, categoryName, "0".equals(parentId) ? "" : parentId));
                }
            }
        }
        categories.addAll(categoryMap.values());
        if (response.optInt("code", 1) != 1 && items.isEmpty()) throw new Exception(response.optString("msg", "数据源未返回可用内容"));
        return new Models.Page(items, categories, Math.max(1, response.optInt("page", page)), Math.max(1, response.optInt("pagecount", 1)));
    }

    static Models.VodDetail fetchDetail(Models.Source source, String id) throws Exception {
        Uri url = Uri.parse(source.apiUrl).buildUpon().appendQueryParameter("ac", "detail").appendQueryParameter("ids", id).build();
        JSONObject response = getJson(url.toString());
        JSONArray list = response.optJSONArray("list");
        if (list == null || list.length() == 0 || list.optJSONObject(0) == null) throw new Exception("未取得影片详情");
        JSONObject raw = list.getJSONObject(0);
        Models.Vod base = mapVod(raw, source.apiUrl);
        if (base == null) throw new Exception("影片详情格式无效");
        return new Models.VodDetail(base, clean(first(raw, "vod_content", "des")), clean(first(raw, "vod_actor", "actor")), clean(first(raw, "vod_director", "director")), parsePlaySources(first(raw, "vod_play_from"), first(raw, "vod_play_url")));
    }

    static List<String> parseOfficialConfigEndpoints(JSONObject config) {
        List<String> endpoints = new ArrayList<>();
        String primary = trim(config.optString("primaryApi"));
        String backup = trim(config.optString("backupApi"));
        if (isConfigUrl(primary)) endpoints.add(primary);
        if (isConfigUrl(backup) && !endpoints.contains(backup)) endpoints.add(backup);
        return endpoints;
    }

    static List<Models.Source> parseOfficialSources(JSONObject config) {
        List<Models.Source> values = new ArrayList<>();
        JSONArray sources = config.optJSONArray("sources");
        if (sources == null) return values;
        for (int index = 0; index < sources.length(); index++) {
            JSONObject item = sources.optJSONObject(index);
            if (item == null) continue;
            String address = first(item, "api", "apiUrl", "url", "address", "endpoint");
            if (address.length() == 0) continue;
            String name = first(item, "name", "displayName", "title", "siteName");
            String endpoint = ensureTrailingSlash(address);
            values.add(new Models.Source(endpoint, name.length() == 0 ? SourceRepository.endpointHost(endpoint) : name, endpoint, "unknown"));
        }
        return values;
    }

    static JSONObject getJson(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestProperty("Accept", "application/json, text/plain, */*");
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
        String body = read(stream);
        connection.disconnect();
        if (status < 200 || status >= 300) throw new Exception("网络请求失败（HTTP " + status + "）");
        return new JSONObject(body.replace("\uFEFF", ""));
    }

    private static Models.Vod mapVod(JSONObject raw, String apiUrl) {
        String id = first(raw, "vod_id", "id");
        String name = first(raw, "vod_name", "name");
        if (id.length() == 0 || name.length() == 0) return null;
        String poster = resolveUrl(first(raw, "vod_pic", "vod_pic_slide", "vod_pic_url", "vod_pic_thumb", "pic", "cover"), apiUrl);
        return new Models.Vod(id, name, first(raw, "type_id", "tid"), first(raw, "type_name", "type"), first(raw, "vod_remarks", "vod_serial", "note"), first(raw, "vod_year", "year"), first(raw, "vod_area", "area"), poster);
    }

    private static List<Models.PlaySource> parsePlaySources(String rawNames, String rawUrls) {
        List<Models.PlaySource> sources = new ArrayList<>();
        String[] names = rawNames.split("\\$\\$\\$");
        String[] groups = rawUrls.split("\\$\\$\\$");
        for (int sourceIndex = 0; sourceIndex < groups.length; sourceIndex++) {
            List<Models.Episode> episodes = new ArrayList<>();
            String[] entries = groups[sourceIndex].split("#");
            for (int episodeIndex = 0; episodeIndex < entries.length; episodeIndex++) {
                String[] pair = entries[episodeIndex].split("\\$", 2);
                String url = pair.length > 1 ? trim(pair[1]) : "";
                if (url.length() > 0) episodes.add(new Models.Episode(trim(pair[0]).length() == 0 ? "第 " + (episodeIndex + 1) + " 集" : trim(pair[0]), url));
            }
            if (!episodes.isEmpty()) sources.add(new Models.PlaySource(sourceIndex < names.length && trim(names[sourceIndex]).length() > 0 ? trim(names[sourceIndex]) : "线路 " + (sourceIndex + 1), episodes));
        }
        return sources;
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        StringBuilder value = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) value.append(line);
        reader.close();
        return value.toString();
    }

    private static String first(JSONObject raw, String... keys) {
        for (String key : keys) {
            String value = trim(raw.optString(key));
            if (value.length() > 0) return value;
        }
        return "";
    }

    private static boolean isConfigUrl(String value) {
        if (value == null) return false;
        String lower = value.trim().toLowerCase();
        int query = lower.indexOf('?');
        int fragment = lower.indexOf('#');
        int cut = query < 0 ? fragment : fragment < 0 ? query : Math.min(query, fragment);
        if (cut >= 0) lower = lower.substring(0, cut);
        return lower.endsWith("/api.json");
    }

    private static String resolveUrl(String value, String apiUrl) {
        String raw = trim(value);
        if (raw.length() == 0) return "";
        if (raw.startsWith("//")) return Uri.parse(apiUrl).getScheme() + ":" + raw;
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw;
        return origin(apiUrl) + (raw.startsWith("/") ? raw : "/" + raw);
    }

    private static String normalizeAddress(String value) throws Exception {
        String raw = trim(value);
        if (raw.length() == 0) throw new Exception("请输入数据源地址");
        if (!raw.startsWith("http://") && !raw.startsWith("https://")) raw = "https://" + raw;
        new URL(raw);
        return raw.replaceAll("/+$", "");
    }

    private static String origin(String value) {
        Uri uri = Uri.parse(value);
        return uri.getScheme() + "://" + uri.getAuthority();
    }

    private static String ensureTrailingSlash(String value) { return value.endsWith("/") ? value : value + "/"; }
    private static String trim(String value) { return value == null ? "" : value.trim(); }
    private static String clean(String value) { return trim(value).replaceAll("<[^>]*>", " ").replaceAll("&nbsp;", " ").replaceAll("\\s+", " "); }
}
