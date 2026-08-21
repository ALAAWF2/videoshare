package com.orange.videoplayer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class IptvModels {

    public static class Category implements Serializable {
        public final String id;
        public final String name;
        public int count = 0;

        public Category(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static class Item implements Serializable {
        public final String id;
        public final String name;
        public final String iconUrl;
        public final String containerExt;
        public final String type; // "live", "vod", "series"
        public final String streamUrl; // Pre-calculated or for M3U
        public final String categoryId;
        public final int num;
        public String rating;
        public String year;
        public String plot;
        public String genre;
        public String cast;
        public String director;
        public String duration;

        public Item(String id, String name, String iconUrl, String containerExt, String type, String streamUrl, String categoryId, int num) {
            this.id = id;
            this.name = name;
            this.iconUrl = iconUrl;
            this.containerExt = containerExt;
            this.type = type;
            this.streamUrl = streamUrl;
            this.categoryId = categoryId;
            this.num = num;
        }

        public Item(String id, String name, String iconUrl, String containerExt, String type, String streamUrl, String categoryId, int num, String rating, String year) {
            this(id, name, iconUrl, containerExt, type, streamUrl, categoryId, num);
            this.rating = rating;
            this.year = year;
        }
    }

    public static class VodDetails implements Serializable {
        public final String id;
        public final String name;
        public final String image;
        public final String rating;
        public final String releaseDate;
        public final String duration;
        public final String plot;
        public final String cast;
        public final String director;
        public final String genre;
        public final String containerExt;

        public VodDetails(String id, String name, String image, String rating, String releaseDate, String duration, String plot, String cast, String director, String genre, String containerExt) {
            this.id = id;
            this.name = name;
            this.image = image;
            this.rating = rating;
            this.releaseDate = releaseDate;
            this.duration = duration;
            this.plot = plot;
            this.cast = cast;
            this.director = director;
            this.genre = genre;
            this.containerExt = containerExt;
        }
    }

    public static class Season implements Serializable {
        public final String seasonNumber;
        public final String name;
        public final List<Episode> episodes = new ArrayList<>();

        public Season(String seasonNumber, String name) {
            this.seasonNumber = seasonNumber;
            this.name = name;
        }
    }

    public static class Episode implements Serializable {
        public final String id;
        public final String episodeNum;
        public final String title;
        public final String containerExt;
        public final String iconUrl;
        public final String streamUrl;

        public Episode(String id, String episodeNum, String title, String containerExt, String iconUrl, String streamUrl) {
            this.id = id;
            this.episodeNum = episodeNum;
            this.title = title;
            this.containerExt = containerExt;
            this.iconUrl = iconUrl;
            this.streamUrl = streamUrl;
        }
    }
}
