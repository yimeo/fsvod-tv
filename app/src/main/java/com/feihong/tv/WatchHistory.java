package com.feihong.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class WatchHistory {
    private static final String PREFS = "feihong_tv_watch_history";
    private static final String ITEMS = "items";
    private static final int MAX_ITEMS = 30;

    static final class Record {
        final String title;
        final String sourceName;
        final String episodeName;
        final String url;
        final String posterUrl;
        final int episodeIndex;
        final long positionMs;
        final long durationMs;
        final long updatedAt;

        Record(String title, String sourceName, String episodeName, String url, String posterUrl, int episodeIndex, long positionMs, long durationMs, long updatedAt) {
            this.title = title;
            this.sourceName = sourceName;
            this.episodeName = episodeName;
            this.url = url;
            this.posterUrl = posterUrl;
            this.episodeIndex = episodeIndex;
            this.positionMs = positionMs;
            this.durationMs = durationMs;
            this.updatedAt = updatedAt;
        }
    }

    private WatchHistory() { }

    static void save(Context context, String title, String sourceName, String episodeName, String url, String posterUrl, int episodeIndex, long positionMs, long durationMs) {
        if (title == null || title.trim().length() == 0 || url == null || url.trim().length() == 0) return;
        List<Record> records = get(context);
        List<Record> next = new ArrayList<>();
        for (Record record : records) if (!record.title.equals(title) || record.episodeIndex != episodeIndex) next.add(record);
        next.add(0, new Record(title, sourceName, episodeName, url, posterUrl, episodeIndex, Math.max(0, positionMs), Math.max(0, durationMs), System.currentTimeMillis()));
        while (next.size() > MAX_ITEMS) next.remove(next.size() - 1);
        JSONArray array = new JSONArray();
        for (Record record : next) {
            JSONObject item = new JSONObject();
            try {
                item.put("title", record.title);
                item.put("sourceName", record.sourceName);
                item.put("episodeName", record.episodeName);
                item.put("url", record.url);
                item.put("posterUrl", record.posterUrl);
                item.put("episodeIndex", record.episodeIndex);
                item.put("positionMs", record.positionMs);
                item.put("durationMs", record.durationMs);
                item.put("updatedAt", record.updatedAt);
            } catch (JSONException ignored) { }
            array.put(item);
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(ITEMS, array.toString()).apply();
    }

    static List<Record> get(Context context) {
        List<Record> result = new ArrayList<>();
        String value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ITEMS, "[]");
        try {
            JSONArray array = new JSONArray(value == null ? "[]" : value);
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.optJSONObject(index);
                if (item == null) continue;
                String url = item.optString("url", "");
                if (url.length() == 0) continue;
                result.add(new Record(item.optString("title", "未命名影片"), item.optString("sourceName", ""), item.optString("episodeName", ""), url, item.optString("posterUrl", ""), item.optInt("episodeIndex", 0), item.optLong("positionMs", 0), item.optLong("durationMs", 0), item.optLong("updatedAt", 0)));
            }
        } catch (JSONException ignored) { }
        return result;
    }

    static Record latest(Context context) {
        List<Record> records = get(context);
        return records.isEmpty() ? null : records.get(0);
    }

    static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(ITEMS).apply();
    }
}
