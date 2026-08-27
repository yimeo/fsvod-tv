package com.feihong.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class SourceRepository {
    private static final String PREFS = "feihong_tv";
    private static final String SOURCES = "sources";
    private static final String ACTIVE = "active_source";
    private final SharedPreferences preferences;

    SourceRepository(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    List<Models.Source> getSources() {
        List<Models.Source> result = new ArrayList<>();
        String stored = preferences.getString(SOURCES, "[]");
        try {
            JSONArray array = new JSONArray(stored == null ? "[]" : stored);
            for (int index = 0; index < array.length(); index++) {
                JSONObject value = array.optJSONObject(index);
                if (value == null) continue;
                String url = value.optString("apiUrl").trim();
                if (url.length() == 0) continue;
                String id = value.optString("id", url);
                String name = value.optString("name", endpointHost(url));
                result.add(new Models.Source(id, name, url, value.optString("health", "unknown")));
            }
        } catch (JSONException ignored) { }
        return result;
    }

    Models.Source getActiveSource() {
        String activeId = preferences.getString(ACTIVE, "");
        List<Models.Source> sources = getSources();
        for (Models.Source source : sources) if (source.id.equals(activeId)) return source;
        return sources.isEmpty() ? null : sources.get(0);
    }

    void saveSources(List<Models.Source> sources) {
        JSONArray array = new JSONArray();
        for (Models.Source source : sources) {
            JSONObject value = new JSONObject();
            try {
                value.put("id", source.id);
                value.put("name", source.name);
                value.put("apiUrl", source.apiUrl);
                value.put("health", source.health);
            } catch (JSONException ignored) { }
            array.put(value);
        }
        preferences.edit().putString(SOURCES, array.toString()).apply();
    }

    void setActiveSource(String sourceId) {
        preferences.edit().putString(ACTIVE, sourceId).apply();
    }

    void upsert(Models.Source candidate) {
        List<Models.Source> values = getSources();
        boolean replaced = false;
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index).id.equals(candidate.id)) {
                values.set(index, candidate);
                replaced = true;
                break;
            }
        }
        if (!replaced) values.add(0, candidate);
        saveSources(values);
    }

    void updateHealth(String sourceId, String health) {
        List<Models.Source> values = getSources();
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index).id.equals(sourceId)) values.set(index, values.get(index).withHealth(health));
        }
        saveSources(values);
    }

    static String endpointHost(String value) {
        String normalized = value.replaceFirst("^https?://", "");
        int slash = normalized.indexOf('/');
        return slash < 0 ? normalized : normalized.substring(0, slash);
    }
}
