package com.ikechi.studio.onwa.player.utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.ikechi.studio.onwa.player.models.AudioItem;
import com.ikechi.studio.onwa.player.models.VideoItem;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import com.ikechi.studio.*;

/**
 * MediaDatabaseHelper — local SQLite cache for audio/video metadata, user
 * preferences (favorites, play counts), playlists, listening history, and
 * video bookmarks.
 *
 * <h3>Smart‑rescan philosophy</h3>
 * When the user refreshes the library we do NOT wipe the cache.  Instead we:
 * <ol>
 *   <li>Query MediaStore for the current set of URIs.</li>
 *   <li>Load existing favorites / play‑counts / playlist memberships from the DB.</li>
 *   <li>Batch‑insert new rows (preserving user data) and delete rows whose URIs
 *       are no longer present on disk.</li>
 * </ol>
 * This keeps favorites, play counts and playlist assignments intact across
 * rescans — even when files are moved or renamed (because the content:// URI
 * is stable).
 *
 * <h3>Playlist backend</h3>
 * Two tables: {@code playlists} and {@code playlist_audio} (junction).
 * Full CRUD plus convenience lookups.
 *
 * <h3>New tables</h3>
 * <ul>
 *   <li>{@code play_history} – each row records a single playback event
 *       (timestamp, track URI, duration listened).  Powers the listening
 *       stats dashboard.</li>
 *   <li>{@code video_bookmarks} – stores user‑created named timestamps
 *       for video URIs.</li>
 * </ul>
 *
 * <p>Zero Jetpack/AndroidX. Zero lambdas.  API 21+.
 */
public class MediaDatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "MediaDatabaseHelper";
    private static final String DATABASE_NAME = "media_cache.db";
    private static final int DATABASE_VERSION = 6;   // 6: added advanced stats queries

    // ── Table: audio ──────────────────────────────────────────────────────────
    private static final String TABLE_AUDIO = "audio";
    private static final String COL_URI = "uri";
    private static final String COL_PATH = "file_path";
    private static final String COL_TITLE = "title";
    private static final String COL_ARTIST = "artist";
    private static final String COL_ALBUM = "album";
    private static final String COL_DURATION = "duration";
    private static final String COL_SIZE = "file_size";
    private static final String COL_TRACK_NUMBER = "track_number";
    private static final String COL_LAST_MODIFIED = "last_modified";
    private static final String COL_FAVORITE = "favorite";
    private static final String COL_PLAY_COUNT = "play_count";

    // ── Table: video ──────────────────────────────────────────────────────────
    private static final String TABLE_VIDEO = "video";
    private static final String COL_WIDTH = "width";
    private static final String COL_HEIGHT = "height";
    private static final String COL_LAST_PLAY_POSITION = "last_position"; // fixed constant

    // ── Table: playlists ──────────────────────────────────────────────────────
    private static final String TABLE_PLAYLISTS = "playlists";
    private static final String COL_PLAYLIST_ID = "_id";
    private static final String COL_PLAYLIST_NAME = "name";
    private static final String COL_PLAYLIST_CREATED = "created_date";

    // ── Table: playlist_audio (junction) ──────────────────────────────────────
    private static final String TABLE_PLAYLIST_AUDIO = "playlist_audio";
    private static final String COL_PA_PLAYLIST_ID = "playlist_id";
    private static final String COL_PA_AUDIO_URI = "audio_uri";
    private static final String COL_PA_POSITION = "position";
    private static final String COL_PA_ADDED_DATE = "added_date";

    // ★ NEW TABLE: play history for listening stats
    private static final String TABLE_HISTORY = "play_history";
    private static final String COL_HISTORY_ID = "_id";
    private static final String COL_HISTORY_URI = "audio_uri";
    private static final String COL_HISTORY_TIMESTAMP = "timestamp";
    private static final String COL_HISTORY_LISTENED_MS = "listened_ms";

    // ★ NEW TABLE: video bookmarks
    private static final String TABLE_BOOKMARKS = "video_bookmarks";
    private static final String COL_BOOK_ID = "_id";
    private static final String COL_BOOK_URI = "video_uri";
    private static final String COL_BOOK_TITLE = "title";
    private static final String COL_BOOK_POSITION = "position_ms";

    // ── CREATE statements ─────────────────────────────────────────────────────
    private static final String CREATE_AUDIO_TABLE =
        "CREATE TABLE IF NOT EXISTS " + TABLE_AUDIO + "("
        + COL_URI + " TEXT PRIMARY KEY,"
        + COL_PATH + " TEXT,"
        + COL_TITLE + " TEXT,"
        + COL_ARTIST + " TEXT,"
        + COL_ALBUM + " TEXT,"
        + COL_DURATION + " INTEGER,"
        + COL_SIZE + " INTEGER,"
        + COL_TRACK_NUMBER + " INTEGER,"
        + COL_LAST_MODIFIED + " INTEGER,"
        + COL_FAVORITE + " INTEGER DEFAULT 0,"
        + COL_PLAY_COUNT + " INTEGER DEFAULT 0)";

    private static final String CREATE_VIDEO_TABLE =
        "CREATE TABLE IF NOT EXISTS " + TABLE_VIDEO + "("
        + COL_URI + " TEXT PRIMARY KEY,"
        + COL_PATH + " TEXT,"
        + COL_TITLE + " TEXT,"
        + COL_DURATION + " INTEGER,"
        + COL_WIDTH + " INTEGER,"
        + COL_HEIGHT + " INTEGER,"
        + COL_SIZE + " INTEGER,"
        + COL_LAST_PLAY_POSITION + " INTEGER DEFAULT 0,"
        + COL_LAST_MODIFIED + " INTEGER)";

    private static final String CREATE_PLAYLISTS_TABLE =
        "CREATE TABLE IF NOT EXISTS " + TABLE_PLAYLISTS + "("
        + COL_PLAYLIST_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
        + COL_PLAYLIST_NAME + " TEXT UNIQUE NOT NULL,"
        + COL_PLAYLIST_CREATED + " INTEGER)";

    private static final String CREATE_PLAYLIST_AUDIO_TABLE =
        "CREATE TABLE IF NOT EXISTS " + TABLE_PLAYLIST_AUDIO + "("
        + COL_PA_PLAYLIST_ID + " INTEGER NOT NULL,"
        + COL_PA_AUDIO_URI + " TEXT NOT NULL,"
        + COL_PA_POSITION + " INTEGER DEFAULT 0,"
        + COL_PA_ADDED_DATE + " INTEGER,"
        + "PRIMARY KEY (" + COL_PA_PLAYLIST_ID + ", " + COL_PA_AUDIO_URI + "),"
        + "FOREIGN KEY (" + COL_PA_PLAYLIST_ID + ") REFERENCES "
        + TABLE_PLAYLISTS + "(" + COL_PLAYLIST_ID + ") ON DELETE CASCADE,"
        + "FOREIGN KEY (" + COL_PA_AUDIO_URI + ") REFERENCES "
        + TABLE_AUDIO + "(" + COL_URI + ") ON DELETE CASCADE)";

    private static final String CREATE_HISTORY_TABLE =
        "CREATE TABLE IF NOT EXISTS " + TABLE_HISTORY + "("
        + COL_HISTORY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
        + COL_HISTORY_URI + " TEXT NOT NULL,"
        + COL_HISTORY_TIMESTAMP + " INTEGER NOT NULL,"
        + COL_HISTORY_LISTENED_MS + " INTEGER DEFAULT 0)";

    private static final String CREATE_BOOKMARKS_TABLE =
        "CREATE TABLE IF NOT EXISTS " + TABLE_BOOKMARKS + "("
        + COL_BOOK_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
        + COL_BOOK_URI + " TEXT NOT NULL,"
        + COL_BOOK_TITLE + " TEXT,"
        + COL_BOOK_POSITION + " INTEGER NOT NULL)";

    // ── Singleton ─────────────────────────────────────────────────────────────
    private static MediaDatabaseHelper sInstance;
    private static final Object sLock = new Object();

    private final Handler mDbHandler;

    public static MediaDatabaseHelper getInstance(Context context) {
        if (sInstance == null) {
            synchronized (sLock) {
                if (sInstance == null) {
                    sInstance = new MediaDatabaseHelper(context.getApplicationContext());
                }
            }
        }
        return sInstance;
    }

    private MediaDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        setWriteAheadLoggingEnabled(true);
        HandlerThread thread = new HandlerThread("MediaDBThread");
        thread.start();
        mDbHandler = new Handler(thread.getLooper());
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_AUDIO_TABLE);
        db.execSQL(CREATE_VIDEO_TABLE);
        db.execSQL(CREATE_PLAYLISTS_TABLE);
        db.execSQL(CREATE_PLAYLIST_AUDIO_TABLE);
        db.execSQL(CREATE_HISTORY_TABLE);
        db.execSQL(CREATE_BOOKMARKS_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // For versions before 5, add the video last_position column if not present
        if (oldVersion < 5 && newVersion >= 5) {
            try {
                // Check if column already exists (safety)
                Cursor cursor = db.rawQuery("PRAGMA table_info(" + TABLE_VIDEO + ")", null);
                boolean columnExists = false;
                if (cursor != null) {
                    int nameIndex = cursor.getColumnIndex("name");
                    while (cursor.moveToNext()) {
                        if (COL_LAST_PLAY_POSITION.equals(cursor.getString(nameIndex))) {
                            columnExists = true;
                            break;
                        }
                    }
                    cursor.close();
                }
                if (!columnExists) {
                    db.execSQL("ALTER TABLE " + TABLE_VIDEO + " ADD COLUMN " 
                               + COL_LAST_PLAY_POSITION + " INTEGER DEFAULT 0");
                    IkLog.d(TAG, "Added column " + COL_LAST_PLAY_POSITION + " to " + TABLE_VIDEO);
                }
            } catch (Exception e) {
                IkLog.e(TAG, "Error adding last_position column", e);
            }
        }

        // For all upgrades, ensure all tables exist (but don't drop data)
        db.execSQL(CREATE_AUDIO_TABLE);
        db.execSQL(CREATE_VIDEO_TABLE);
        db.execSQL(CREATE_PLAYLISTS_TABLE);
        db.execSQL(CREATE_PLAYLIST_AUDIO_TABLE);
        db.execSQL(CREATE_HISTORY_TABLE);
        db.execSQL(CREATE_BOOKMARKS_TABLE);
    }

    // =========================================================================
    // SMART RESCAN — Audio
    // =========================================================================

    /**
     * Returns the set of all audio URIs currently stored in the cache.
     * Called on the background thread.
     */
    public Set<String> getAllAudioUris() {
        Set<String> uris = new HashSet<String>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT " + COL_URI + " FROM " + TABLE_AUDIO, null);
            if (cursor.moveToFirst()) {
                do {
                    uris.add(cursor.getString(0));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error reading audio URIs", e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return uris;
    }

    /**
     * Returns a map: URI → [favorite (0/1), playCount].
     * Called on the background thread before batch‑insert so we can
     * preserve user data.
     */
    public Map<String, int[]> getAudioFavoritesAndPlayCounts() {
        Map<String, int[]> map = new HashMap<String, int[]>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                "SELECT " + COL_URI + ", " + COL_FAVORITE + ", " + COL_PLAY_COUNT
                + " FROM " + TABLE_AUDIO, null);
            if (cursor.moveToFirst()) {
                do {
                    String uri = cursor.getString(0);
                    int fav = cursor.getInt(1);
                    int pc = cursor.getInt(2);
                    map.put(uri, new int[]{fav, pc});
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error reading favorites/play counts", e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return map;
    }

    /**
     * Batch‑insert or update audio rows inside a single transaction.
     * <p>
     * {@code userData} is the map returned by
     * {@link #getAudioFavoritesAndPlayCounts()}.  Values from the map
     * override the defaults from the AudioItem so that favorites and
     * play counts survive a rescan.
     *
     * @param items    Fresh items from MediaStore.
     * @param userData Existing favorites & play counts keyed by URI.
     */
    public void insertOrUpdateAudioBatch(final List<AudioItem> items,
                                         final Map<String, int[]> userData) {
        mDbHandler.post(new Runnable() {
                @Override
                public void run() {
                    SQLiteDatabase db = getWritableDatabase();
                    db.beginTransaction();
                    try {
                        for (AudioItem item : items) {
                            ContentValues values = new ContentValues();
                            String uriStr = item.getUri().toString();
                            values.put(COL_URI, uriStr);
                            values.put(COL_PATH, item.getFilePath());
                            values.put(COL_TITLE, item.getTitle());
                            values.put(COL_ARTIST, item.getArtist());
                            values.put(COL_ALBUM, item.getAlbum());
                            values.put(COL_DURATION, item.getDuration());
                            values.put(COL_SIZE, item.getSize());
                            values.put(COL_TRACK_NUMBER, item.getTrackNumber());
                            values.put(COL_LAST_MODIFIED, System.currentTimeMillis());

                            // Preserve user data when available
                            int[] ud = userData.get(uriStr);
                            if (ud != null) {
                                values.put(COL_FAVORITE, ud[0]);
                                values.put(COL_PLAY_COUNT, ud[1]);
                            } else {
                                values.put(COL_FAVORITE, item.isFavorite() ? 1 : 0);
                                values.put(COL_PLAY_COUNT, item.getPlayCount());
                            }

                            db.insertWithOnConflict(TABLE_AUDIO, null, values,
                                                    SQLiteDatabase.CONFLICT_REPLACE);
                        }
                        db.setTransactionSuccessful();
                    } catch (Exception e) {
                        IkLog.e(TAG, "Error in batch audio insert", e);
                    } finally {
                        db.endTransaction();
                    }
                }
            });
    }

    /**
     * Removes cached audio rows whose URI is NOT in {@code currentUris}.
     * Call <em>after</em> batch‑inserting the fresh MediaStore data.
     */
    public void removeAudioNotInSet(final Set<String> currentUris) {
        mDbHandler.post(new Runnable() {
                @Override
                public void run() {
                    SQLiteDatabase db = getWritableDatabase();
                    db.beginTransaction();
                    try {
                        // First collect all cached URIs
                        Cursor c = db.rawQuery("SELECT " + COL_URI + " FROM " + TABLE_AUDIO, null);
                        List<String> toDelete = new ArrayList<String>();
                        if (c.moveToFirst()) {
                            do {
                                String uri = c.getString(0);
                                if (!currentUris.contains(uri)) {
                                    toDelete.add(uri);
                                }
                            } while (c.moveToNext());
                        }
                        c.close();

                        // Delete them (cascades to playlist_audio via FK)
                        for (String uri : toDelete) {
                            db.delete(TABLE_AUDIO, COL_URI + " = ?", new String[]{uri});
                        }
                        db.setTransactionSuccessful();
                        IkLog.d(TAG, "Removed " + toDelete.size() + " stale audio entries");
                    } catch (Exception e) {
                        IkLog.e(TAG, "Error removing stale audio", e);
                    } finally {
                        db.endTransaction();
                    }
                }
            });
    }

    // =========================================================================
    // SINGLE‑ROW AUDIO OPERATIONS
    // =========================================================================

    /** Inserts or updates a single audio row.  Kept for non‑batch callers. */
    public void insertOrUpdateAudio(final AudioItem item, final long lastModified) {
        mDbHandler.post(new Runnable() {
                @Override
                public void run() {
                    SQLiteDatabase db = getWritableDatabase();
                    ContentValues values = new ContentValues();
                    values.put(COL_URI, item.getUri().toString());
                    values.put(COL_PATH, item.getFilePath());
                    values.put(COL_TITLE, item.getTitle());
                    values.put(COL_ARTIST, item.getArtist());
                    values.put(COL_ALBUM, item.getAlbum());
                    values.put(COL_DURATION, item.getDuration());
                    values.put(COL_SIZE, item.getSize());
                    values.put(COL_TRACK_NUMBER, item.getTrackNumber());
                    values.put(COL_LAST_MODIFIED, lastModified);
                    values.put(COL_FAVORITE, item.isFavorite() ? 1 : 0);
                    values.put(COL_PLAY_COUNT, item.getPlayCount());
                    try {
                        db.insertWithOnConflict(TABLE_AUDIO, null, values,
                                                SQLiteDatabase.CONFLICT_REPLACE);
                    } catch (Exception e) {
                        IkLog.e(TAG, "Error inserting audio item", e);
                    }
                }
            });
    }

    /**
     * Updates only the favorite flag for the given URI.
     * Much lighter than re‑inserting the whole row.
     */
    public void updateFavorite(final String uri, final boolean favorite) {
        mDbHandler.post(new Runnable() {
                @Override
                public void run() {
                    SQLiteDatabase db = getWritableDatabase();
                    ContentValues values = new ContentValues();
                    values.put(COL_FAVORITE, favorite ? 1 : 0);
                    try {
                        db.update(TABLE_AUDIO, values, COL_URI + " = ?",
                                  new String[]{uri});
                    } catch (Exception e) {
                        IkLog.e(TAG, "Error updating favorite", e);
                    }
                }
            });
    }

    /**
     * Updates only the play count for the given URI.
     */
    public void updatePlayCount(final String uri, final int playCount) {
        mDbHandler.post(new Runnable() {
                @Override
                public void run() {
                    SQLiteDatabase db = getWritableDatabase();
                    ContentValues values = new ContentValues();
                    values.put(COL_PLAY_COUNT, playCount);
                    try {
                        db.update(TABLE_AUDIO, values, COL_URI + " = ?",
                                  new String[]{uri});
                    } catch (Exception e) {
                        IkLog.e(TAG, "Error updating play count", e);
                    }
                }
            });
    }

    /**
     * Returns a single AudioItem by URI, or null if not cached.
     */
    public AudioItem getAudioByUri(String uriStr) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                "SELECT * FROM " + TABLE_AUDIO + " WHERE " + COL_URI + " = ?",
                new String[]{uriStr});
            if (cursor.moveToFirst()) {
                return cursorToAudioItem(cursor);
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error getting audio by URI", e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }

    /** Returns all cached audio items, sorted by title. */
    public List<AudioItem> getAllAudio() {
        List<AudioItem> list = new ArrayList<AudioItem>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                "SELECT * FROM " + TABLE_AUDIO + " ORDER BY " + COL_TITLE + " ASC",
                null);
            if (cursor.moveToFirst()) {
                do {
                    list.add(cursorToAudioItem(cursor));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error reading audio cache", e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return list;
    }

    public void clearAudio() {
        mDbHandler.post(new Runnable() {
                @Override
                public void run() {
                    SQLiteDatabase db = getWritableDatabase();
                    try {
                        db.delete(TABLE_AUDIO, null, null);
                    } catch (Exception e) {
                        IkLog.e(TAG, "Error clearing audio cache", e);
                    }
                }
            });
    }

    // =========================================================================
    // VIDEO OPERATIONS (with last position support)
    // =========================================================================

    /**
     * Inserts or updates a video item, including its last playback position.
     * @param item VideoItem containing all metadata and saved position.
     * @param lastModified Timestamp of last modification (e.g., system time).
     */
    public void insertOrUpdateVideo(final VideoItem item, final long lastModified) {
        mDbHandler.post(new Runnable() {
                @Override
                public void run() {
                    SQLiteDatabase db = getWritableDatabase();
                    ContentValues values = new ContentValues();
                    values.put(COL_URI, item.getUri().toString());
                    values.put(COL_PATH, item.getFilePath());
                    values.put(COL_TITLE, item.getTitle());
                    values.put(COL_DURATION, item.getDuration());
                    values.put(COL_WIDTH, item.getWidth());
                    values.put(COL_HEIGHT, item.getHeight());
                    values.put(COL_SIZE, item.getSize());
                    values.put(COL_LAST_PLAY_POSITION, item.getLastPosition());
                    values.put(COL_LAST_MODIFIED, lastModified);
                    try {
                        db.insertWithOnConflict(TABLE_VIDEO, null, values,
                                                SQLiteDatabase.CONFLICT_REPLACE);
                    } catch (Exception e) {
                        IkLog.e(TAG, "Error inserting video item", e);
                    }
                }
            });
    }

    /**
     * Efficiently updates only the last playback position for a video.
     * Called frequently (e.g., every 2 seconds or on pause).
     *
     * @param uri      Content URI of the video.
     * @param position Current playback position in milliseconds.
     */
    public void updateVideoLastPosition(final Uri uri, final long position) {
        mDbHandler.post(new Runnable() {
                @Override
                public void run() {
                    SQLiteDatabase db = getWritableDatabase();
                    ContentValues values = new ContentValues();
                    values.put(COL_LAST_PLAY_POSITION, position);
                    try {
                        db.update(TABLE_VIDEO, values, COL_URI + " = ?",
                                  new String[]{uri.toString()});
                    } catch (Exception e) {
                        IkLog.e(TAG, "Error updating video last position", e);
                    }
                }
            });
    }

    /**
     * Returns a single VideoItem by URI, including its last saved position.
     * @return VideoItem or null if not found.
     */
    public VideoItem getVideoByUri(final Uri uri) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                "SELECT * FROM " + TABLE_VIDEO + " WHERE " + COL_URI + " = ?",
                new String[]{uri.toString()});
            if (cursor.moveToFirst()) {
                return cursorToVideoItem(cursor);
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error getting video by URI", e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }

    /**
     * Returns all cached video items, sorted by title, each with its last saved position.
     */
    public List<VideoItem> getAllVideos() {
        List<VideoItem> list = new ArrayList<VideoItem>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                "SELECT * FROM " + TABLE_VIDEO + " ORDER BY " + COL_TITLE + " ASC",
                null);
            if (cursor.moveToFirst()) {
                do {
                    list.add(cursorToVideoItem(cursor));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error reading video cache", e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return list;
    }

    /**
     * Converts a database cursor (pointing to the video table) to a VideoItem.
     * Assumes the cursor contains all columns defined in CREATE_VIDEO_TABLE.
     */
    private VideoItem cursorToVideoItem(Cursor cursor) {
        String uriStr = cursor.getString(cursor.getColumnIndexOrThrow(COL_URI));
        String path = cursor.getString(cursor.getColumnIndexOrThrow(COL_PATH));
        String title = cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE));
        long duration = cursor.getLong(cursor.getColumnIndexOrThrow(COL_DURATION));
        int width = cursor.getInt(cursor.getColumnIndexOrThrow(COL_WIDTH));
        int height = cursor.getInt(cursor.getColumnIndexOrThrow(COL_HEIGHT));
        long size = cursor.getLong(cursor.getColumnIndexOrThrow(COL_SIZE));
        long lastPos = 0;
        try {
            lastPos = cursor.getLong(cursor.getColumnIndexOrThrow(COL_LAST_PLAY_POSITION));
        } catch (IllegalArgumentException e) {
            // Column might not exist (fallback for old DB)
            IkLog.w(TAG, "Column " + COL_LAST_PLAY_POSITION + " not found, using 0");
        }
        Uri uri = Uri.parse(uriStr);
        VideoItem item = new VideoItem(uri, path, title, duration, width, height, size);
        item.setLastPosition(lastPos);
        return item;
    }

    public void clearVideos() {
        mDbHandler.post(new Runnable() {
                @Override
                public void run() {
                    SQLiteDatabase db = getWritableDatabase();
                    try {
                        db.delete(TABLE_VIDEO, null, null);
                    } catch (Exception e) {
                        IkLog.e(TAG, "Error clearing video cache", e);
                    }
                }
            });
    }

    // =========================================================================
    // PLAYLISTS — CRUD
    // =========================================================================

    /**
     * Simple data class returned by {@link #getAllPlaylists()}.
     */
    public static class PlaylistInfo {
        public final long id;
        public final String name;
        public final long createdDate;
        public int trackCount;

        public PlaylistInfo(long id, String name, long createdDate, int trackCount) {
            this.id = id;
            this.name = name;
            this.createdDate = createdDate;
            this.trackCount = trackCount;
        }
    }

    /**
     * Creates a new playlist and returns its ID (asynchronously via callback).
     *
     * @param name     Playlist name (must be unique).
     * @param callback Receives the new playlist ID, or -1 on failure.
     */
    public void createPlaylist(final String name, final OnPlaylistCreatedCallback callback) {
        mDbHandler.post(new Runnable() {
                @Override
                public void run() {
                    SQLiteDatabase db = getWritableDatabase();
                    long id = -1;
                    try {
                        ContentValues values = new ContentValues();
                        values.put(COL_PLAYLIST_NAME, name);
                        values.put(COL_PLAYLIST_CREATED, System.currentTimeMillis());
                        id = db.insertOrThrow(TABLE_PLAYLISTS, null, values);
                    } catch (Exception e) {
                        IkLog.e(TAG, "Error creating playlist", e);
                    }
                    final long result = id;
                    if (callback != null) {
                        new Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onPlaylistCreated(result);
                                }
                            });
                    }
                }
            });
    }

    public interface OnPlaylistCreatedCallback {
        void onPlaylistCreated(long playlistId);
    }

    /** Deletes a playlist and all its track associations. */
    public void deletePlaylist(final long playlistId) {
        mDbHandler.post(new Runnable() {
                @Override
                public void run() {
                    SQLiteDatabase db = getWritableDatabase();
                    db.beginTransaction();
                    try {
                        db.delete(TABLE_PLAYLIST_AUDIO,
                                  COL_PA_PLAYLIST_ID + " = ?",
                                  new String[]{String.valueOf(playlistId)});
                        db.delete(TABLE_PLAYLISTS,
                                  COL_PLAYLIST_ID + " = ?",
                                  new String[]{String.valueOf(playlistId)});
                        db.setTransactionSuccessful();
                    } catch (Exception e) {
                        IkLog.e(TAG, "Error deleting playlist", e);
                    } finally {
                        db.endTransaction();
                    }
                }
            });
    }

    /** Renames an existing playlist. */
    public void renamePlaylist(final long playlistId, final String newName) {
        mDbHandler.post(new Runnable() {
                @Override
                public void run() {
                    SQLiteDatabase db = getWritableDatabase();
                    ContentValues values = new ContentValues();
                    values.put(COL_PLAYLIST_NAME, newName);
                    try {
                        db.update(TABLE_PLAYLISTS, values,
                                  COL_PLAYLIST_ID + " = ?",
                                  new String[]{String.valueOf(playlistId)});
                    } catch (Exception e) {
                        IkLog.e(TAG, "Error renaming playlist", e);
                    }
                }
            });
    }

    /**
     * Returns all playlists with their current track counts.
     * Call from a background thread.
     */
    public List<PlaylistInfo> getAllPlaylists() {
        List<PlaylistInfo> list = new ArrayList<PlaylistInfo>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                "SELECT p." + COL_PLAYLIST_ID + ", p." + COL_PLAYLIST_NAME
                + ", p." + COL_PLAYLIST_CREATED
                + ", COUNT(pa." + COL_PA_AUDIO_URI + ") AS track_count "
                + "FROM " + TABLE_PLAYLISTS + " p "
                + "LEFT JOIN " + TABLE_PLAYLIST_AUDIO + " pa "
                + "ON p." + COL_PLAYLIST_ID + " = pa." + COL_PA_PLAYLIST_ID + " "
                + "GROUP BY p." + COL_PLAYLIST_ID + " "
                + "ORDER BY p." + COL_PLAYLIST_NAME + " ASC",
                null);
            if (cursor.moveToFirst()) {
                do {
                    long id = cursor.getLong(0);
                    String name = cursor.getString(1);
                    long created = cursor.getLong(2);
                    int count = cursor.getInt(3);
                    list.add(new PlaylistInfo(id, name, created, count));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error reading playlists", e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return list;
    }

    // =========================================================================
    // PLAYLISTS — Track management
    // =========================================================================

    /**
     * Adds an audio track to a playlist.
     * Safe to call when the track is already in the playlist (ignored).
     */
    public void addAudioToPlaylist(final String audioUri, final long playlistId) {
        mDbHandler.post(new Runnable() {
                @Override
                public void run() {
                    SQLiteDatabase db = getWritableDatabase();
                    // Get next position
                    int nextPos = 0;
                    Cursor c = null;
                    try {
                        c = db.rawQuery(
                            "SELECT MAX(" + COL_PA_POSITION + ") FROM " + TABLE_PLAYLIST_AUDIO
                            + " WHERE " + COL_PA_PLAYLIST_ID + " = ?",
                            new String[]{String.valueOf(playlistId)});
                        if (c.moveToFirst() && !c.isNull(0)) {
                            nextPos = c.getInt(0) + 1;
                        }
                    } catch (Exception e) {
                        IkLog.e(TAG, "Error getting max position", e);
                    } finally {
                        if (c != null) c.close();
                    }

                    ContentValues values = new ContentValues();
                    values.put(COL_PA_PLAYLIST_ID, playlistId);
                    values.put(COL_PA_AUDIO_URI, audioUri);
                    values.put(COL_PA_POSITION, nextPos);
                    values.put(COL_PA_ADDED_DATE, System.currentTimeMillis());
                    try {
                        db.insertWithOnConflict(TABLE_PLAYLIST_AUDIO, null, values,
                                                SQLiteDatabase.CONFLICT_IGNORE);
                    } catch (Exception e) {
                        IkLog.e(TAG, "Error adding to playlist", e);
                    }
                }
            });
    }

    /** Removes a track from a playlist. */
    public void removeAudioFromPlaylist(final String audioUri, final long playlistId) {
        mDbHandler.post(new Runnable() {
                @Override
                public void run() {
                    SQLiteDatabase db = getWritableDatabase();
                    try {
                        db.delete(TABLE_PLAYLIST_AUDIO,
                                  COL_PA_PLAYLIST_ID + " = ? AND " + COL_PA_AUDIO_URI + " = ?",
                                  new String[]{String.valueOf(playlistId), audioUri});
                    } catch (Exception e) {
                        IkLog.e(TAG, "Error removing from playlist", e);
                    }
                }
            });
    }

    /**
     * Returns all AudioItems in a playlist, ordered by position.
     * Joins with the audio table to get full metadata.
     */
    public List<AudioItem> getPlaylistAudioItems(long playlistId) {
        List<AudioItem> list = new ArrayList<AudioItem>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                "SELECT a.* FROM " + TABLE_AUDIO + " a "
                + "INNER JOIN " + TABLE_PLAYLIST_AUDIO + " pa "
                + "ON a." + COL_URI + " = pa." + COL_PA_AUDIO_URI + " "
                + "WHERE pa." + COL_PA_PLAYLIST_ID + " = ? "
                + "ORDER BY pa." + COL_PA_POSITION + " ASC",
                new String[]{String.valueOf(playlistId)});
            if (cursor.moveToFirst()) {
                do {
                    list.add(cursorToAudioItem(cursor));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error reading playlist items", e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return list;
    }

    /**
     * Returns the IDs of all playlists that contain the given audio URI.
     */
    public List<Long> getPlaylistsForAudioUri(String audioUri) {
        List<Long> ids = new ArrayList<Long>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                "SELECT " + COL_PA_PLAYLIST_ID + " FROM " + TABLE_PLAYLIST_AUDIO
                + " WHERE " + COL_PA_AUDIO_URI + " = ?",
                new String[]{audioUri});
            if (cursor.moveToFirst()) {
                do {
                    ids.add(cursor.getLong(0));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error reading playlists for URI", e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return ids;
    }

    /** Returns true if the track is in the given playlist. */
    public boolean isAudioInPlaylist(String audioUri, long playlistId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                "SELECT 1 FROM " + TABLE_PLAYLIST_AUDIO
                + " WHERE " + COL_PA_PLAYLIST_ID + " = ? AND " + COL_PA_AUDIO_URI + " = ?",
                new String[]{String.valueOf(playlistId), audioUri});
            return cursor.moveToFirst();
        } catch (Exception e) {
            return false;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    // =========================================================================
    // ★ NEW: Listening history (for stats dashboard)
    // =========================================================================

    /**
     * Records a playback event.  Called by the audio service when a track
     * is played for at least a few seconds.
     *
     * @param audioUri    URI of the track that was played.
     * @param listenedMs  How long the track was listened to (>= 0).
     */
    public void recordPlayHistory(final String audioUri, final long listenedMs) {
        mDbHandler.post(new Runnable() {
                @Override
                public void run() {
                    SQLiteDatabase db = getWritableDatabase();
                    ContentValues values = new ContentValues();
                    values.put(COL_HISTORY_URI, audioUri);
                    values.put(COL_HISTORY_TIMESTAMP, System.currentTimeMillis());
                    values.put(COL_HISTORY_LISTENED_MS, listenedMs);
                    try {
                        db.insert(TABLE_HISTORY, null, values);
                    } catch (Exception e) {
                        IkLog.e(TAG, "Error recording play history", e);
                    }
                }
            });
    }

    /**
     * Returns the total number of play events (for stats).
     */
    public int getTotalPlayCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_HISTORY, null);
            if (cursor.moveToFirst()) return cursor.getInt(0);
        } finally {
            if (cursor != null) cursor.close();
        }
        return 0;
    }

    /**
     * Returns the total listening time in milliseconds.
     */
    public long getTotalListeningTimeMs() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT SUM(" + COL_HISTORY_LISTENED_MS + ") FROM " + TABLE_HISTORY, null);
            if (cursor.moveToFirst()) return cursor.getLong(0);
        } finally {
            if (cursor != null) cursor.close();
        }
        return 0;
    }

    /**
     * Returns the most played artist (by number of play events).
     * Joins with the audio table to get the artist name.
     */
    public String getTopArtist() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                "SELECT a." + COL_ARTIST + ", COUNT(*) AS cnt "
                + "FROM " + TABLE_HISTORY + " h "
                + "LEFT JOIN " + TABLE_AUDIO + " a ON h." + COL_HISTORY_URI + " = a." + COL_URI + " "
                + "GROUP BY a." + COL_ARTIST + " "
                + "ORDER BY cnt DESC LIMIT 1", null);
            if (cursor.moveToFirst()) return cursor.getString(0);
        } finally {
            if (cursor != null) cursor.close();
        }
        return "Unknown";
    }

    /**
     * Returns the most played track (by number of play events).
     * Joins with the audio table to get the title.
     */
    public String getTopTrack() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                "SELECT a." + COL_TITLE + ", COUNT(*) AS cnt "
                + "FROM " + TABLE_HISTORY + " h "
                + "LEFT JOIN " + TABLE_AUDIO + " a ON h." + COL_HISTORY_URI + " = a." + COL_URI + " "
                + "GROUP BY a." + COL_TITLE + " "
                + "ORDER BY cnt DESC LIMIT 1", null);
            if (cursor.moveToFirst()) return cursor.getString(0);
        } finally {
            if (cursor != null) cursor.close();
        }
        return "Unknown";
    }

    /**
     * ★ NEW: Returns the most played album (by number of play events).
     */
    public String getTopAlbum() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                "SELECT a." + COL_ALBUM + ", COUNT(*) AS cnt "
                + "FROM " + TABLE_HISTORY + " h "
                + "LEFT JOIN " + TABLE_AUDIO + " a ON h." + COL_HISTORY_URI + " = a." + COL_URI + " "
                + "GROUP BY a." + COL_ALBUM + " "
                + "ORDER BY cnt DESC LIMIT 1", null);
            if (cursor.moveToFirst()) return cursor.getString(0);
        } finally {
            if (cursor != null) cursor.close();
        }
        return "Unknown";
    }

    /**
     * ★ NEW: Returns the number of distinct tracks ever played.
     */
    public int getTotalDistinctTracks() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT COUNT(DISTINCT " + COL_HISTORY_URI + ") FROM " + TABLE_HISTORY, null);
            if (cursor.moveToFirst()) return cursor.getInt(0);
        } finally {
            if (cursor != null) cursor.close();
        }
        return 0;
    }

    /**
     * Returns listening activity grouped by hour of day (0‑23).
     * Returns an int array where index = hour (0‑23) and value = play count.
     */
    public int[] getHourlyActivity() {
        int[] hours = new int[24];
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                "SELECT CAST(strftime('%H', " + COL_HISTORY_TIMESTAMP + " / 1000, 'unixepoch') AS INTEGER) AS hour, COUNT(*) "
                + "FROM " + TABLE_HISTORY + " GROUP BY hour", null);
            if (cursor.moveToFirst()) {
                do {
                    int hour = cursor.getInt(0);
                    int count = cursor.getInt(1);
                    if (hour >= 0 && hour < 24) hours[hour] = count;
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return hours;
    }

    // =========================================================================
    // ★ NEW: Advanced stats queries (streaks, peak times, top artists)
    // =========================================================================

    public static class StreakInfo {
        public int currentStreak;
        public int longestStreak;
    }

    public static class ArtistCount {
        public String artist;
        public int count;
        public ArtistCount(String artist, int count) { this.artist = artist; this.count = count; }
    }

    /**
     * Returns the current and longest listening streak (consecutive days with at least one play).
     * All timestamps are stored in UTC; we compare using local midnight boundaries.
     */
    public StreakInfo getListeningStreak() {
        StreakInfo info = new StreakInfo();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            // Get all distinct play dates (local midnight, in milliseconds)
            cursor = db.rawQuery(
                "SELECT DISTINCT " + COL_HISTORY_TIMESTAMP + " FROM " + TABLE_HISTORY
                + " ORDER BY " + COL_HISTORY_TIMESTAMP + " DESC", null);
            List<Long> days = new ArrayList<>();
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            if (cursor.moveToFirst()) {
                do {
                    long ts = cursor.getLong(0);
                    // Normalize to midnight of the day in the device's timezone
                    Calendar localDay = Calendar.getInstance();
                    localDay.setTimeInMillis(ts);
                    localDay.set(Calendar.HOUR_OF_DAY, 0);
                    localDay.set(Calendar.MINUTE, 0);
                    localDay.set(Calendar.SECOND, 0);
                    localDay.set(Calendar.MILLISECOND, 0);
                    days.add(localDay.getTimeInMillis());
                } while (cursor.moveToNext());
            }

            if (days.isEmpty()) return info;

            // Calculate current streak (most recent consecutive days ending at today or yesterday)
            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);
            long todayMs = today.getTimeInMillis();
            long yesterdayMs = todayMs - 86400000L;

            int current = 0;
            long expected = (days.get(0) == todayMs || days.get(0) == yesterdayMs) ? days.get(0) : -1;
            if (expected > 0) {
                for (int i = 0; i < days.size(); i++) {
                    if (days.get(i) == expected) {
                        current++;
                        expected -= 86400000L;
                    } else break;
                }
            }
            info.currentStreak = current;

            // Calculate longest streak
            int longest = 1;
            int temp = 1;
            for (int i = 1; i < days.size(); i++) {
                if (days.get(i - 1) - days.get(i) == 86400000L) {
                    temp++;
                    if (temp > longest) longest = temp;
                } else {
                    temp = 1;
                }
            }
            info.longestStreak = Math.max(longest, current);
        } catch (Exception e) {
            IkLog.e(TAG, "Error calculating listening streak", e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return info;
    }

    /** Returns the hour (0‑23) with the most play events. */
    public int getPeakListeningHour() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                "SELECT CAST(strftime('%H', " + COL_HISTORY_TIMESTAMP + " / 1000, 'unixepoch') AS INTEGER) AS hour, COUNT(*) AS cnt "
                + "FROM " + TABLE_HISTORY + " GROUP BY hour ORDER BY cnt DESC LIMIT 1", null);
            if (cursor.moveToFirst()) return cursor.getInt(0);
        } finally {
            if (cursor != null) cursor.close();
        }
        return -1;
    }

    /** Returns the day of week (1=Sunday, 7=Saturday) with the most play events. */
    public int getPeakListeningDay() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                "SELECT CAST(strftime('%w', " + COL_HISTORY_TIMESTAMP + " / 1000, 'unixepoch') AS INTEGER) + 1 AS dow, COUNT(*) AS cnt "
                + "FROM " + TABLE_HISTORY + " GROUP BY dow ORDER BY cnt DESC LIMIT 1", null);
            if (cursor.moveToFirst()) return cursor.getInt(0);
        } finally {
            if (cursor != null) cursor.close();
        }
        return -1;
    }

    /** Returns the top N artists by play count (from history). */
    public List<ArtistCount> getTopArtists(int limit) {
        List<ArtistCount> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                "SELECT a." + COL_ARTIST + ", COUNT(*) AS cnt "
                + "FROM " + TABLE_HISTORY + " h "
                + "LEFT JOIN " + TABLE_AUDIO + " a ON h." + COL_HISTORY_URI + " = a." + COL_URI + " "
                + "WHERE a." + COL_ARTIST + " IS NOT NULL AND a." + COL_ARTIST + " != '' "
                + "GROUP BY a." + COL_ARTIST + " "
                + "ORDER BY cnt DESC LIMIT " + limit, null);
            if (cursor.moveToFirst()) {
                do {
                    list.add(new ArtistCount(cursor.getString(0), cursor.getInt(1)));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return list;
    }

    // =========================================================================
    // ★ NEW: Video bookmarks
    // =========================================================================

    /**
     * Inserts a new bookmark for a video.
     *
     * @param videoUri    URI of the video.
     * @param title       User‑visible name for the bookmark (e.g. "Guitar solo").
     * @param positionMs  Timestamp in milliseconds.
     */
    public void addVideoBookmark(final String videoUri, final String title,
                                 final long positionMs) {
        mDbHandler.post(new Runnable() {
                @Override
                public void run() {
                    SQLiteDatabase db = getWritableDatabase();
                    ContentValues values = new ContentValues();
                    values.put(COL_BOOK_URI, videoUri);
                    values.put(COL_BOOK_TITLE, title);
                    values.put(COL_BOOK_POSITION, positionMs);
                    try {
                        db.insert(TABLE_BOOKMARKS, null, values);
                    } catch (Exception e) {
                        IkLog.e(TAG, "Error adding video bookmark", e);
                    }
                }
            });
    }

    /** Returns all bookmarks for a given video URI. */
    public List<Bookmark> getVideoBookmarks(String videoUri) {
        List<Bookmark> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_BOOKMARKS, null,
                              COL_BOOK_URI + " = ?", new String[]{videoUri},
                              null, null, COL_BOOK_POSITION + " ASC");
            while (cursor.moveToFirst()) {
                do {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_BOOK_ID));
                    String title = cursor.getString(cursor.getColumnIndexOrThrow(COL_BOOK_TITLE));
                    long pos = cursor.getLong(cursor.getColumnIndexOrThrow(COL_BOOK_POSITION));
                    list.add(new Bookmark(id, title, pos));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return list;
    }

    /** Deletes a bookmark by ID. */
    public void deleteVideoBookmark(final long bookmarkId) {
        mDbHandler.post(new Runnable() {
                @Override
                public void run() {
                    SQLiteDatabase db = getWritableDatabase();
                    db.delete(TABLE_BOOKMARKS, COL_BOOK_ID + " = ?",
                              new String[]{String.valueOf(bookmarkId)});
                }
            });
    }

    /** Simple data class for a bookmark. */
    public static class Bookmark {
        public final long id;
        public final String title;
        public final long positionMs;

        public Bookmark(long id, String title, long positionMs) {
            this.id = id;
            this.title = title;
            this.positionMs = positionMs;
        }
    }

    // =========================================================================
    // Cursor → AudioItem helper
    // =========================================================================

    private AudioItem cursorToAudioItem(Cursor cursor) {
        String uriStr = cursor.getString(cursor.getColumnIndexOrThrow(COL_URI));
        String path = cursor.getString(cursor.getColumnIndexOrThrow(COL_PATH));
        String title = cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE));
        String artist = cursor.getString(cursor.getColumnIndexOrThrow(COL_ARTIST));
        String album = cursor.getString(cursor.getColumnIndexOrThrow(COL_ALBUM));
        long duration = cursor.getLong(cursor.getColumnIndexOrThrow(COL_DURATION));
        long size = cursor.getLong(cursor.getColumnIndexOrThrow(COL_SIZE));
        int track = cursor.getInt(cursor.getColumnIndexOrThrow(COL_TRACK_NUMBER));
        boolean favorite = cursor.getInt(cursor.getColumnIndexOrThrow(COL_FAVORITE)) == 1;
        int playCount = cursor.getInt(cursor.getColumnIndexOrThrow(COL_PLAY_COUNT));

        Uri uri = Uri.parse(uriStr);
        AudioItem item = new AudioItem(uri, path, title, artist, album, null, duration);
        item.setSize(size);
        item.setTrackNumber(track);
        item.setFavorite(favorite);
        item.setPlayCount(playCount);
        return item;
    }
}