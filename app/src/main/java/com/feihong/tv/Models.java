package com.feihong.tv;

import java.util.ArrayList;
import java.util.List;

final class Models {
    private Models() { }

    static final class Source {
        final String id;
        final String name;
        final String apiUrl;
        final String health;

        Source(String id, String name, String apiUrl, String health) {
            this.id = id;
            this.name = name;
            this.apiUrl = apiUrl;
            this.health = health;
        }

        Source withHealth(String value) { return new Source(id, name, apiUrl, value); }
    }

    static final class Category {
        final String id;
        final String name;
        final String parentId;

        Category(String id, String name, String parentId) {
            this.id = id;
            this.name = name;
            this.parentId = parentId;
        }
    }

    static class Vod {
        final String id;
        final String name;
        final String typeId;
        final String typeName;
        final String remarks;
        final String year;
        final String area;
        final String posterUrl;

        Vod(String id, String name, String typeId, String typeName, String remarks, String year, String area, String posterUrl) {
            this.id = id;
            this.name = name;
            this.typeId = typeId;
            this.typeName = typeName;
            this.remarks = remarks;
            this.year = year;
            this.area = area;
            this.posterUrl = posterUrl;
        }
    }

    static final class Episode {
        final String name;
        final String url;

        Episode(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }

    static final class PlaySource {
        final String name;
        final List<Episode> episodes;

        PlaySource(String name, List<Episode> episodes) {
            this.name = name;
            this.episodes = episodes;
        }
    }

    static final class VodDetail extends Vod {
        final String content;
        final String actor;
        final String director;
        final List<PlaySource> playSources;

        VodDetail(Vod base, String content, String actor, String director, List<PlaySource> playSources) {
            super(base.id, base.name, base.typeId, base.typeName, base.remarks, base.year, base.area, base.posterUrl);
            this.content = content;
            this.actor = actor;
            this.director = director;
            this.playSources = playSources;
        }
    }

    static final class Page {
        final List<Vod> items;
        final List<Category> categories;
        final int page;
        final int pageCount;

        Page(List<Vod> items, List<Category> categories, int page, int pageCount) {
            this.items = items;
            this.categories = categories;
            this.page = page;
            this.pageCount = pageCount;
        }
    }

    static List<String> episodeNames(List<Episode> episodes) {
        List<String> result = new ArrayList<>();
        for (Episode episode : episodes) result.add(episode.name);
        return result;
    }
}
