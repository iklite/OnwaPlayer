package com.ikechi.studio.onwa.player.models;

import android.net.Uri;
import java.io.Serializable;
import java.util.Objects;

public class VideoItem implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Uri uri;
    private final String path;         // now nullable
    private final String title;
    private final long duration;
    private final int width;
    private final int height;
    private final long size;
    private long lastPosition;

    /**
     * Constructor.
     * @param uri    Must not be null.
     * @param path   May be null (no known file path).
     * @param title  May be null – will be replaced with "Unknown".
     */
    public VideoItem(Uri uri, String path, String title, long duration,
                     int width, int height, long size) {
        this.uri = Objects.requireNonNull(uri, "URI cannot be null");
        this.path = path;                               // allowed to be null
        this.title = (title != null) ? title : "Unknown";  // safe default
        this.duration = duration;
        this.width = width;
        this.height = height;
        this.size = size;
        this.lastPosition = 0;
    }

    public void setLastPosition(long pos) {
        this.lastPosition = pos;
    }

    // Getters
    public Uri getUri() { return uri; }

    @Deprecated
    public String getFilePath() { return path; }   // may return null now

    public String getTitle() { return title; }
    public long getDuration() { return duration; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public long getSize() { return size; }
    public long getLastPosition() { return lastPosition; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VideoItem videoItem = (VideoItem) o;
        return duration == videoItem.duration &&
			width == videoItem.width &&
			height == videoItem.height &&
			size == videoItem.size &&
			lastPosition == videoItem.lastPosition &&
			Objects.equals(uri, videoItem.uri) &&
			Objects.equals(path, videoItem.path) &&
			Objects.equals(title, videoItem.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, path, title, duration, width, height, size, lastPosition);
    }

    @Override
    public String toString() {
        return "VideoItem{" +
			"uri=" + uri +
			", path='" + path + '\'' +
			", title='" + title + '\'' +
			", duration=" + duration +
			", width=" + width +
			", height=" + height +
			", size=" + size +
			", lastPosition=" + lastPosition +
			'}';
    }
}