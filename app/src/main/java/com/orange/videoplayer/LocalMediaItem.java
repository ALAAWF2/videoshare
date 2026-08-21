package com.orange.videoplayer;

import android.net.Uri;

import java.io.Serializable;

public class LocalMediaItem implements Serializable {
    public final long id;
    public final String title;
    public final String path;
    public final Uri contentUri;
    public final long durationMs;
    public final long sizeBytes;
    public final long dateModified;
    public final String folderName;
    public final String artist;
    public final boolean isVideo;

    public LocalMediaItem(long id, String title, String path, Uri contentUri, long durationMs, long sizeBytes, long dateModified, String folderName, String artist, boolean isVideo) {
        this.id = id;
        this.title = title;
        this.path = path;
        this.contentUri = contentUri;
        this.durationMs = durationMs;
        this.sizeBytes = sizeBytes;
        this.dateModified = dateModified;
        this.folderName = folderName;
        this.artist = artist;
        this.isVideo = isVideo;
    }
}
