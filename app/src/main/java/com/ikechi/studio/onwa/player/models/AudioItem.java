package com.ikechi.studio.onwa.player.models;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Base64;

import androidx.annotation.Keep;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.JsonAdapter;

import java.lang.reflect.Type;

@Keep
@JsonAdapter(AudioItem.GsonAdapter.class)   //
public class AudioItem implements Parcelable {

    // ── Fields ────────────────────────────────────────────────────────────────
    private final Uri uri;          // Primary: content:// uri from MediaStore
    private final String filePath;  // Keep for display only. Don't use for File() on 10+
    private String title;
    private String artist;
    private String album;
    private long duration;
    private long size;
    private int trackNumber;
    private int playcount;
    private byte[] albumArtBytes;
    private boolean favorite = false;

    // ── Constructors ──────────────────────────────────────────────────────────
    public AudioItem(Uri uri, String filePath, String title, String artist,
                     String album, byte[] albumArtBytes, long duration) {
        this.uri = uri;
        this.filePath = filePath;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.duration = duration;
        this.albumArtBytes = albumArtBytes != null ? albumArtBytes.clone() : null;
        this.playcount = 0;
    }

    // ── Parcelable ────────────────────────────────────────────────────────────
    protected AudioItem(Parcel in) {
        uri = in.readParcelable(Uri.class.getClassLoader());
        filePath = in.readString();
        title = in.readString();
        artist = in.readString();
        album = in.readString();
        duration = in.readLong();
        size = in.readLong();
        trackNumber = in.readInt();
        albumArtBytes = in.createByteArray();
        favorite = in.readInt() != 0;
        playcount = in.readInt();
    }

    public static final Creator<AudioItem> CREATOR = new Creator<AudioItem>() {
        @Override
        public AudioItem createFromParcel(Parcel in) {
            return new AudioItem(in);
        }

        @Override
        public AudioItem[] newArray(int size) {
            return new AudioItem[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(uri, flags);
        dest.writeString(filePath);
        dest.writeString(title);
        dest.writeString(artist);
        dest.writeString(album);
        dest.writeLong(duration);
        dest.writeLong(size);
        dest.writeInt(trackNumber);
        dest.writeByteArray(albumArtBytes);
        dest.writeInt(favorite ? 1 : 0);
        dest.writeInt(playcount);
    }

    // ── Getters / setters ─────────────────────────────────────────────────────
    public Uri getUri() {
        return uri;
    }

    @Deprecated
    public String getFilePath() {
        return filePath;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public int getTrackNumber() {
        return trackNumber;
    }

    public void setTrackNumber(int trackNumber) {
        this.trackNumber = trackNumber;
    }

    public void setPlayCount(int count) {
        playcount = count;
    }

    public int getPlayCount() {
        return playcount;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean fav) {
        this.favorite = fav;
    }

    public byte[] getAlbumArtBytes() {
        return albumArtBytes != null ? albumArtBytes.clone() : null;
    }

    public void setAlbumArtBytes(byte[] data) {
        albumArtBytes = data != null ? data.clone() : null;
    }

    // ── Utility ───────────────────────────────────────────────────────────────
    public String getFormattedDuration() {
        long totalSecs = duration / 1000;
        long mins = totalSecs / 60;
        long secs = totalSecs % 60;
        if (mins >= 60) {
            long hrs = mins / 60;
            mins = mins % 60;
            return hrs + ":" + (mins < 10 ? "0" : "") + mins
                    + ":" + (secs < 10 ? "0" : "") + secs;
        }
        return mins + ":" + (secs < 10 ? "0" : "") + secs;
    }

    // ── Gson adapter (inner static class) ─────────────────────────────────────
    /**
     * Custom Gson adapter for AudioItem.
     * Automatically used because of the @JsonAdapter annotation on the class.
     */
    @Keep
    public static class GsonAdapter implements JsonSerializer<AudioItem>, JsonDeserializer<AudioItem> {

        @Override
        public JsonElement serialize(AudioItem src, Type typeOfSrc,
                                     JsonSerializationContext context) {
            JsonObject obj = new JsonObject();

            if (src.uri != null) {
                obj.addProperty("uri", src.uri.toString());
            }
            if (src.filePath != null) {
                obj.addProperty("filePath", src.filePath);
            }
            obj.addProperty("title", src.title);
            obj.addProperty("artist", src.artist);
            obj.addProperty("album", src.album);
            obj.addProperty("duration", src.duration);
            obj.addProperty("size", src.size);
            obj.addProperty("trackNumber", src.trackNumber);
            obj.addProperty("playcount", src.playcount);
            obj.addProperty("favorite", src.favorite);

            if (src.albumArtBytes != null) {
                String base64 = Base64.encodeToString(src.albumArtBytes, Base64.DEFAULT);
                obj.addProperty("albumArtBytes", base64);
            }

            return obj;
        }

        @Override
        public AudioItem deserialize(JsonElement json, Type typeOfT,
                                     JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();

            Uri uri = null;
            if (obj.has("uri") && !obj.get("uri").isJsonNull()) {
                uri = Uri.parse(obj.get("uri").getAsString());
            }

            String filePath = null;
            if (obj.has("filePath") && !obj.get("filePath").isJsonNull()) {
                filePath = obj.get("filePath").getAsString();
            }

            String title = obj.get("title").getAsString();
            String artist = obj.get("artist").getAsString();
            String album = obj.get("album").getAsString();
            long duration = obj.get("duration").getAsLong();

            byte[] albumArtBytes = null;
            if (obj.has("albumArtBytes") && !obj.get("albumArtBytes").isJsonNull()) {
                String base64 = obj.get("albumArtBytes").getAsString();
                albumArtBytes = Base64.decode(base64, Base64.DEFAULT);
            }

            AudioItem item = new AudioItem(uri, filePath, title, artist, album,
                    albumArtBytes, duration);

            if (obj.has("size")) {
                item.setSize(obj.get("size").getAsLong());
            }
            if (obj.has("trackNumber")) {
                item.setTrackNumber(obj.get("trackNumber").getAsInt());
            }
            if (obj.has("playcount")) {
                item.setPlayCount(obj.get("playcount").getAsInt());
            }
            if (obj.has("favorite")) {
                item.setFavorite(obj.get("favorite").getAsBoolean());
            }

            return item;
        }
    }
}