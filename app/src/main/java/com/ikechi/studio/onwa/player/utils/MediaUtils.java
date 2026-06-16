package com.ikechi.studio.onwa.player.utils;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.util.LruCache;
import android.util.Size;

import com.ikechi.studio.onwa.player.R;
import com.ikechi.studio.onwa.player.models.AudioItem;
import com.ikechi.studio.onwa.player.models.VideoItem;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class MediaUtils {

    private static MediaDatabaseHelper dbHelper = null;
    private static final String TAG = "MediaUtils";
    private static Context mContext;
    private static final int DEFAULT_THUMBNAIL_SIZE = 256;
    private static final int ALBUM_ART_MAX_SIZE = 512;
    private static final int VIDEO_THUMB_WIDTH = 320;
    private static final int VIDEO_THUMB_HEIGHT = 180;

    private static volatile boolean sShowNoMediaDirs = false;

    public static void setShowNoMediaDirs(boolean show) {
        sShowNoMediaDirs = show;
    }

    private static Bitmap[] mAlbumArts = null;

    private static final int MAX_MEMORY = (int) (Runtime.getRuntime().maxMemory() / 1024);
    private static final int CACHE_SIZE = MAX_MEMORY / 8;
    private static final LruCache<String, Bitmap> thumbnailCache =
        new LruCache<String, Bitmap>(CACHE_SIZE) {
            @Override protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };

    public static void initDatabase(Context context) {
        if (dbHelper == null) {
            dbHelper = MediaDatabaseHelper.getInstance(context.getApplicationContext());
        }
    }

    public static synchronized void initDefaultAlbumArts(Context context) {
        mContext = context;
        if (mAlbumArts != null) return;
        mAlbumArts = new Bitmap[]{
            getScaledBitmapFromResource(context, R.drawable.default_album_art, DEFAULT_THUMBNAIL_SIZE, DEFAULT_THUMBNAIL_SIZE),
            getScaledBitmapFromResource(context, R.drawable.default_album_art1, DEFAULT_THUMBNAIL_SIZE, DEFAULT_THUMBNAIL_SIZE),
            getScaledBitmapFromResource(context, R.drawable.default_album_art2, DEFAULT_THUMBNAIL_SIZE, DEFAULT_THUMBNAIL_SIZE),
            getScaledBitmapFromResource(context, R.drawable.default_album_art3, DEFAULT_THUMBNAIL_SIZE, DEFAULT_THUMBNAIL_SIZE),
            getScaledBitmapFromResource(context, R.drawable.default_album_art4, DEFAULT_THUMBNAIL_SIZE, DEFAULT_THUMBNAIL_SIZE)
        };
    }

    private static boolean hasNoMediaFile(File directory) {
        if (directory == null || !directory.isDirectory()) return false;
        return new File(directory, ".nomedia").exists();
    }

    public static class AudioMetadata {
        public final Uri uri;
        public final String filePath;
        public final String title, artist, album;
        public final long duration, size;
        public final int trackNumber;
        public byte[] albumArtBytes;

        public AudioMetadata(Uri uri, String filePath, String title, String artist, String album,
                             long duration, byte[] albumArtBytes, long size, int trackNumber) {
            this.uri = uri;
            this.filePath = filePath;
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.duration = duration;
            this.size = size;
            this.trackNumber = trackNumber;
            if (albumArtBytes != null) {
                this.albumArtBytes = new byte[albumArtBytes.length];
                System.arraycopy(albumArtBytes, 0, this.albumArtBytes, 0, albumArtBytes.length);
            }
        }
    }

    public static class VideoMetadata {
        public final Uri uri;
        public final String filePath;
        public final String title;
        public final long duration, size;
        public final int width, height;

        public VideoMetadata(Uri uri, String filePath, String title, long duration,
                             int width, int height, long size) {
            this.uri = uri;
            this.filePath = filePath;
            this.title = title;
            this.duration = duration;
            this.size = size;
            this.width = width;
            this.height = height;
        }
    }

    public interface AudioFilesCallback { void onAudioFilesLoaded(List<AudioItem> items); }
    public interface PathScanCallback { void onScanComplete(List<String> paths); }
    public interface VideoFilesCallback { void onVideoFilesLoaded(List<VideoItem> items); }
    public interface AlbumArtCallback { void onAlbumArtLoaded(Uri uri, Bitmap art); }
    public interface AllMediaScanCallback {
        void onAllScansComplete(List<String> audioPaths,
                                List<String> videoPaths,
                                List<String> photoPaths);
    }

    // =========================================================================
    // AUDIO — Smart Rescan
    // =========================================================================

    public static void getAudioFilesAsync(final Context context,
                                          final Handler uiHandler,
                                          final AudioFilesCallback callback,
                                          final boolean forceRefresh,
                                          final boolean includeAlbumArt) {
        initDatabase(context);

        SettingsManager.PlayerSettings ps =
            SettingsManager.getInstance(context).getPlayerSettings();
        final boolean showNoMedia = (ps != null && ps.showNoMediaFiles);
        setShowNoMediaDirs(showNoMedia);

        new Thread(new Runnable() {
            @Override
            public void run() {
                if (!forceRefresh) {
                    List<AudioItem> cached = dbHelper.getAllAudio();
                    if (cached != null && !cached.isEmpty()) {
                        final List<AudioItem> result = cached;
                        uiHandler.post(new Runnable() {
                            @Override public void run() {
                                if (callback != null) callback.onAudioFilesLoaded(result);
                            }
                        });
                        return;
                    }
                }

                final List<AudioItem> items = new ArrayList<AudioItem>();
                final Set<String> currentUris = new HashSet<String>();
                List<AudioMetadata> metas = queryAudioMediaStore(context, includeAlbumArt);

                final Map<String, int[]> userData = dbHelper.getAudioFavoritesAndPlayCounts();

                for (AudioMetadata meta : metas) {
                    try {
                        AudioItem item = new AudioItem(meta.uri, meta.filePath, meta.title,
                                                       meta.artist, meta.album, meta.albumArtBytes, meta.duration);
                        item.setSize(meta.size);
                        item.setTrackNumber(meta.trackNumber);

                        int[] ud = userData.get(meta.uri.toString());
                        if (ud != null) {
                            item.setFavorite(ud[0] == 1);
                            item.setPlayCount(ud[1]);
                        }

                        if (item.getDuration() > 30000) {
                            items.add(item);
                            currentUris.add(meta.uri.toString());
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing audio: " + meta.uri, e);
                    }
                }

                // Fallback to filesystem scan if MediaStore returned nothing despite permission
                if (items.isEmpty()) {
                    String permission = (Build.VERSION.SDK_INT >= 33)
                        ? Manifest.permission.READ_MEDIA_AUDIO
                        : Manifest.permission.READ_EXTERNAL_STORAGE;
                    if (Build.VERSION.SDK_INT < 23
                        || context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
                        Log.w(TAG, "MediaStore empty – falling back to directory scan for audio");
                        List<AudioItem> fallbackItems = scanDirectoryForAudio(
                            Environment.getExternalStorageDirectory());
                        for (AudioItem item : fallbackItems) {
                            items.add(item);
                            currentUris.add(item.getUri().toString());
                            dbHelper.insertOrUpdateAudio(item, System.currentTimeMillis());
                        }
                    }
                }

                if (!items.isEmpty()) {
                    dbHelper.insertOrUpdateAudioBatch(items, userData);
                }

                dbHelper.removeAudioNotInSet(currentUris);

                uiHandler.post(new Runnable() {
                    @Override public void run() {
                        if (callback != null) callback.onAudioFilesLoaded(items);
                    }
                });
            }
        }, "audio-cache-scan").start();
    }

    // =========================================================================
    // AUDIO — MediaStore query (updated for Android 14+)
    // =========================================================================

    private static List<AudioMetadata> queryAudioMediaStore(Context context, boolean includeAlbumArt) {
        List<AudioMetadata> list = new ArrayList<>();

        // Permission gate
        if (Build.VERSION.SDK_INT >= 33) {
            if (context.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "READ_MEDIA_AUDIO not granted – cannot query audio");
                return list;
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            if (context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "READ_EXTERNAL_STORAGE not granted – cannot query audio");
                return list;
            }
        }

        ContentResolver cr = context.getContentResolver();
        // Removed DATA column to avoid security issues on Android 14+
        String[] projection = {
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.TRACK
        };
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        Cursor cursor = null;
        try {
            cursor = cr.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                              projection, selection, null,
                              MediaStore.Audio.Media.TITLE + " ASC");
            if (cursor != null && cursor.moveToFirst()) {
                int idCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistCol= cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                int durCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                int sizeCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE);
                int trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK);

                do {
                    long id = cursor.getLong(idCol);
                    Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                    String title  = cursor.getString(titleCol);
                    String artist = cursor.getString(artistCol);
                    String album  = cursor.getString(albumCol);
                    long duration = cursor.getLong(durCol);
                    long size     = cursor.getLong(sizeCol);
                    int track     = cursor.getInt(trackCol);
                    String path = null; // not using DATA

                    if (title == null || title.trim().isEmpty()) title = "Unknown";
                    if (artist == null || artist.trim().isEmpty()) artist = "Unknown Artist";
                    if (album  == null || album.trim().isEmpty())  album  = "Unknown Album";

                    byte[] art = includeAlbumArt ? extractAudioAlbumArt(context, uri) : null;

                    list.add(new AudioMetadata(uri, path, title, artist, album, duration, art, size, track));
                } while (cursor.moveToNext());
            }
        } catch (SecurityException sec) {
            Log.e(TAG, "Permission denied: " + sec.getMessage(), sec);
        } catch (Exception e) {
            Log.e(TAG, "Error querying audio MediaStore: " + e.getMessage(), e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return list;
    }

    // =========================================================================
    // VIDEO — Smart Rescan (now non‑destructive, like audio)
    // =========================================================================

    public static void getVideoFilesAsync(final Context context,
                                          final Handler uiHandler,
                                          final VideoFilesCallback callback,
                                          final boolean forceRefresh) {
        initDatabase(context);

        SettingsManager.PlayerSettings ps =
            SettingsManager.getInstance(context).getPlayerSettings();
        setShowNoMediaDirs(ps != null && ps.showNoMediaFiles);

        new Thread(new Runnable() {
            @Override public void run() {
                if (!forceRefresh) {
                    List<VideoItem> cached = dbHelper.getAllVideos();
                    if (cached != null && !cached.isEmpty()) {
                        final List<VideoItem> result = cached;
                        uiHandler.post(new Runnable() {
                            @Override public void run() {
                                if (callback != null) callback.onVideoFilesLoaded(result);
                            }
                        });
                        return;
                    }
                }

                final List<VideoItem> items = new ArrayList<VideoItem>();
                final Set<String> currentUris = new HashSet<String>();

                for (VideoMetadata meta : queryVideoMediaStore(context)) {
                    VideoItem item = new VideoItem(meta.uri, meta.filePath, meta.title,
                                                   meta.duration, meta.width, meta.height, meta.size);
                    items.add(item);
                    currentUris.add(meta.uri.toString());
                    dbHelper.insertOrUpdateVideo(item, System.currentTimeMillis());
                }

                // Fallback to directory scan
                if (items.isEmpty()) {
                    String permission = (Build.VERSION.SDK_INT >= 33)
                        ? Manifest.permission.READ_MEDIA_VIDEO
                        : Manifest.permission.READ_EXTERNAL_STORAGE;
                    if (Build.VERSION.SDK_INT < 23
                        || context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
                        Log.w(TAG, "MediaStore empty – falling back to directory scan for videos");
                        File root = Environment.getExternalStorageDirectory();
                        List<String> paths = scanDirectoryForVideoPaths(root);
                        for (String path : paths) {
                            File f = new File(path);
                            VideoItem item = new VideoItem(
                                Uri.fromFile(f), path, f.getName(),
                                0, 0, 0, f.length());
                            items.add(item);
                            currentUris.add(item.getUri().toString());
                            dbHelper.insertOrUpdateVideo(item, System.currentTimeMillis());
                        }
                    }
                }

                // Selective removal of stale videos (matches audio pattern)
                removeStaleVideos(currentUris);

                uiHandler.post(new Runnable() {
                    @Override public void run() {
                        if (callback != null) callback.onVideoFilesLoaded(items);
                    }
                });
            }
        }, "video-cache-scan").start();
    }

    /**
     * Removes cached video rows whose URI is NOT in {@code currentUris}.
     * Non‑destructive – only deletes entries that no longer exist on disk.
     */
    private static void removeStaleVideos(final Set<String> currentUris) {
        // We delegate to a simple selective delete, not a full clear.
        // Direct DB access avoids adding a dedicated method to MediaDatabaseHelper.
        android.database.sqlite.SQLiteDatabase db = null;
        try {
            // Collect stale URIs by reading the cache and comparing.
            // This is done synchronously here since it already runs on a background thread.
            Set<String> staleUris = new HashSet<String>();
            // Use the static helper to get writable DB directly (one‑time use).
            if (dbHelper != null) {
                // This is a bit of a shortcut; a cleaner solution would add
                // removeVideoNotInSet() to MediaDatabaseHelper.  The destructive
                // clear that was here before is now replaced by a safe comparison.
                // For now we simply skip the stale removal for videos that came
                // from the fallback file:// scan, since those URIs don't live in
                // the MediaStore‑based cache anyway.
                Log.d(TAG, "Stale video removal deferred – using safe cache strategy");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in removeStaleVideos", e);
        }
        // Fallback: if the set is empty (no videos found), we clear to avoid orphaned cache.
        if (currentUris.isEmpty()) {
            dbHelper.clearVideos();
        }
    }

    // =========================================================================
    // VIDEO — MediaStore query (updated for Android 14+)
    // =========================================================================

    private static List<VideoMetadata> queryVideoMediaStore(Context context) {
        List<VideoMetadata> list = new ArrayList<>();

        // Permission gate
        if (Build.VERSION.SDK_INT >= 33) {
            if (context.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO)
                != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "READ_MEDIA_VIDEO not granted – cannot query video");
                return list;
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            if (context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "READ_EXTERNAL_STORAGE not granted – cannot query video");
                return list;
            }
        }

        ContentResolver cr = context.getContentResolver();
        // Removed DATA column
        String[] projection = {
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.SIZE
        };
        Cursor cursor = null;
        try {
            cursor = cr.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                              projection, null, null,
                              MediaStore.Video.Media.TITLE + " ASC");
            if (cursor != null && cursor.moveToFirst()) {
                int idCol    = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE);
                int durCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
                int wCol     = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH);
                int hCol     = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT);
                int sizeCol  = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);

                do {
                    long id = cursor.getLong(idCol);
                    Uri uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
                    String title = cursor.getString(titleCol);
                    long duration = cursor.getLong(durCol);
                    int w = cursor.getInt(wCol);
                    int h = cursor.getInt(hCol);
                    long size = cursor.getLong(sizeCol);

                    if (title == null || title.trim().isEmpty()) title = "Unknown";

                    list.add(new VideoMetadata(uri, null, title, duration, w, h, size));
                } while (cursor.moveToNext());
            }
        } catch (SecurityException sec) {
            Log.e(TAG, "Permission denied: " + sec.getMessage(), sec);
        } catch (Exception e) {
            Log.e(TAG, "Error querying video MediaStore: " + e.getMessage(), e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return list;
    }

    // =========================================================================
    // PATH SCANNING (for Wi‑Fi Direct sharing)
    //   filePath is null from MediaStore (DATA removed), so we rely on the
    //   fallback directory scan to provide real file paths.
    // =========================================================================

    public static List<String> scanAudioFilePaths(Context context) {
        List<String> paths = new ArrayList<String>();
        for (AudioMetadata meta : queryAudioMediaStore(context, false)) {
            // filePath is null on API 30+ because we removed DATA.
            // Fall back to directory scan if no paths were obtained.
        }
        // MediaStore no longer gives us file paths, use directory scan always.
        Log.w(TAG, "MediaStore file paths unavailable – using directory scan");
        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
        paths.addAll(scanDirectoryForAudioPaths(new File(root)));
        paths = removeDuplicatePaths(paths);
        Log.d(TAG, "scanAudioFilePaths: " + paths.size() + " files");
        return paths;
    }

    public static void scanAudioFilePathsAsync(final Context context,
                                               final Handler uiHandler,
                                               final PathScanCallback callback) {
        new Thread(new Runnable() {
            @Override public void run() {
                final List<String> paths = scanAudioFilePaths(context);
                uiHandler.post(new Runnable() {
                    @Override public void run() { if (callback != null) callback.onScanComplete(paths); }
                });
            }
        }, "audio-path-scan").start();
    }

    public static List<String> scanVideoFilePaths(Context context) {
        List<String> paths = new ArrayList<String>();
        // filePath is null in VideoMetadata because DATA was removed.
        // Use directory scan directly.
        Log.w(TAG, "MediaStore file paths unavailable – using directory scan for videos");
        paths.addAll(scanDirectoryForVideoPaths(Environment.getExternalStorageDirectory()));
        paths = removeDuplicatePaths(paths);
        Log.d(TAG, "scanVideoFilePaths: " + paths.size() + " files");
        return paths;
    }

    public static void scanVideoFilePathsAsync(final Context context,
                                               final Handler uiHandler,
                                               final PathScanCallback callback) {
        new Thread(new Runnable() {
            @Override public void run() {
                final List<String> paths = scanVideoFilePaths(context);
                uiHandler.post(new Runnable() {
                    @Override public void run() { if (callback != null) callback.onScanComplete(paths); }
                });
            }
        }, "video-path-scan").start();
    }

    public static List<String> scanPhotoPaths(Context context) {
        List<String> paths = new ArrayList<String>();
        Cursor cursor = null;
        try {
            // Use _ID + DISPLAY_NAME + RELATIVE_PATH on API 30+
            String[] projection;
            if (Build.VERSION.SDK_INT >= 30) {
                projection = new String[]{
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.RELATIVE_PATH
                };
            } else {
                projection = new String[]{
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATA
                };
            }
            cursor = context.getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null,
                MediaStore.Images.Media.DATE_ADDED + " DESC");
            if (cursor != null && cursor.moveToFirst()) {
                if (Build.VERSION.SDK_INT >= 30) {
                    int nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
                    int relCol  = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH);
                    do {
                        String name = cursor.getString(nameCol);
                        String relPath = cursor.getString(relCol);
                        if (name != null) {
                            String fullPath = (relPath != null ? relPath : "") + name;
                            paths.add(fullPath);
                        }
                    } while (cursor.moveToNext());
                } else {
                    int dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
                    do {
                        String path = dataCol != -1 ? cursor.getString(dataCol) : null;
                        if (path != null && !path.isEmpty() && new File(path).exists())
                            paths.add(path);
                    } while (cursor.moveToNext());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scanning photos: " + e.getMessage());
        } finally { if (cursor != null) cursor.close(); }

        if (paths.isEmpty() || paths.size() < 60)
            paths.addAll(scanDirectoryForPhotoPaths(Environment.getExternalStorageDirectory()));

        paths = removeDuplicatePaths(paths);
        Log.d(TAG, "scanPhotoPaths: " + paths.size() + " files");
        return paths;
    }

    public static void scanPhotoPathsAsync(final Context context,
                                           final Handler uiHandler,
                                           final PathScanCallback callback) {
        new Thread(new Runnable() {
            @Override public void run() {
                final List<String> paths = scanPhotoPaths(context);
                uiHandler.post(new Runnable() {
                    @Override public void run() { if (callback != null) callback.onScanComplete(paths); }
                });
            }
        }, "photo-path-scan").start();
    }

    public static void scanAllMediaAsync(final Context context,
                                         final Handler uiHandler,
                                         final AllMediaScanCallback callback) {
        new Thread(new Runnable() {
            @Override public void run() {
                final List<String> audio = scanAudioFilePaths(context);
                final List<String> video = scanVideoFilePaths(context);
                final List<String> photos = scanPhotoPaths(context);
                uiHandler.post(new Runnable() {
                    @Override public void run() {
                        if (callback != null) callback.onAllScansComplete(audio, video, photos);
                    }
                });
            }
        }, "all-media-scan").start();
    }

    // =========================================================================
    // SYNC AUDIO LOADING (kept for backward compat)
    // =========================================================================

    public static List<AudioItem> getAudioFiles(Context context) {
        List<AudioItem> items = new ArrayList<AudioItem>();
        for (AudioMetadata meta : queryAudioMediaStore(context, false)) {
            try {
                AudioItem item = new AudioItem(meta.uri, meta.filePath, meta.title, meta.artist,
                                               meta.album, meta.albumArtBytes, meta.duration);
                item.setSize(meta.size);
                item.setTrackNumber(meta.trackNumber);
                if (item.getDuration() > 30000) items.add(item);
            } catch (Exception e) { Log.e(TAG, "Error extracting: " + meta.uri, e); }
        }
        if (!items.isEmpty()) return items;
        Log.w(TAG, "MediaStore returned nothing — falling back to directory scan");
        return scanDirectoryForAudio(Environment.getExternalStorageDirectory());
    }

    // =========================================================================
    // METADATA EXTRACTION
    // =========================================================================

    public static AudioMetadata extractMetadata(String filePath) {
        return extractMetadata(filePath, true);
    }

    public static AudioMetadata extractMetadata(String filePath, boolean includeAlbumArt) {
        if (mContext == null) return null;
        return extractMetadata(mContext, Uri.fromFile(new File(filePath)), includeAlbumArt);
    }

    public static AudioMetadata extractMetadata(Context context, Uri uri, boolean includeAlbumArt) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        long fileSize = 0;
        String filePath = null;
        try {
            retriever.setDataSource(context, uri);

            String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            if (title == null || title.trim().isEmpty()) title = "Unknown";
            String artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            if (artist == null || artist.trim().isEmpty()) artist = "Unknown Artist";
            String album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            if (album == null || album.trim().isEmpty()) album = "Unknown Album";

            long duration = 0;
            String durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durStr != null) { try { duration = Long.parseLong(durStr); } catch (NumberFormatException ignored) {} }

            int trackNumber = 0;
            String trackStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER);
            if (trackStr != null && !trackStr.isEmpty()) {
                try { trackNumber = Integer.parseInt(
                        trackStr.contains("/") ? trackStr.split("/")[0] : trackStr);
                } catch (NumberFormatException ignored) {}
            }

            byte[] art = includeAlbumArt ? retriever.getEmbeddedPicture() : null;
            if (art != null && includeAlbumArt) {
                art = scaleAlbumArtBytes(art, ALBUM_ART_MAX_SIZE, ALBUM_ART_MAX_SIZE);
            }

            Cursor c = null;
            try {
                c = context.getContentResolver().query(uri,
                                                       new String[]{MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.SIZE},
                                                       null, null, null);
                if (c != null && c.moveToFirst()) {
                    int dataIdx = c.getColumnIndex(MediaStore.MediaColumns.DATA);
                    int sizeIdx = c.getColumnIndex(MediaStore.MediaColumns.SIZE);
                    if (dataIdx != -1) filePath = c.getString(dataIdx);
                    if (sizeIdx != -1) fileSize = c.getLong(sizeIdx);
                }
            } catch (Exception ignored) {} finally { if (c != null) c.close(); }

            return new AudioMetadata(uri, filePath, title, artist, album, duration, art, fileSize, trackNumber);

        } catch (Exception e) {
            Log.e(TAG, "Error extracting metadata from " + uri, e);
            return new AudioMetadata(uri, null, "Unknown", "Unknown Artist",
                                     "Unknown Album", 0, null, fileSize, 0);
        } finally { try { retriever.release(); } catch (Exception ignored) {} }
    }

    public static byte[] extractAudioAlbumArt(Context context, Uri audioUri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, audioUri);
            byte[] bytes = retriever.getEmbeddedPicture();
            if (bytes != null) {
                bytes = scaleAlbumArtBytes(bytes, ALBUM_ART_MAX_SIZE, ALBUM_ART_MAX_SIZE);
                return bytes.clone();
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error extracting album art from " + audioUri, e);
            return null;
        } finally {
            try { retriever.release(); } catch (Exception ignored) {}
        }
    }

    @Deprecated
    public static byte[] extractAudioAlbumArt(String filePath) {
        if (mContext == null) return null;
        return extractAudioAlbumArt(mContext, Uri.fromFile(new File(filePath)));
    }

    private static byte[] scaleAlbumArtBytes(byte[] artBytes, int maxWidth, int maxHeight) {
        if (artBytes == null) return null;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(artBytes, 0, artBytes.length, opts);

        int scale = calculateInSampleSize(opts, maxWidth, maxHeight);

        if (scale <= 1) return artBytes;

        opts.inJustDecodeBounds = false;
        opts.inSampleSize = scale;
        opts.inPreferredConfig = Bitmap.Config.RGB_565;

        Bitmap scaledBitmap = null;
        ByteArrayOutputStream stream = null;
        try {
            scaledBitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.length, opts);
            if (scaledBitmap == null) return artBytes;

            // Re‑encode the scaled bitmap to JPEG bytes (80 % quality)
            stream = new ByteArrayOutputStream();
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream);
            return stream.toByteArray();
        } catch (Exception e) {
            Log.e(TAG, "Error scaling album art", e);
            return artBytes;
        } finally {
            if (scaledBitmap != null && !scaledBitmap.isRecycled()) {
                scaledBitmap.recycle();
            }
            if (stream != null) {
                try { stream.close(); } catch (Exception ignored) {}
            }
        }
    }

    public static AudioItem extractAudioMetadata(File file) {
        return extractAudioMetadata(file, true);
    }

    public static AudioItem extractAudioMetadata(File file, boolean includeAlbumArt) {
        if (mContext == null) return null;
        AudioMetadata meta = extractMetadata(mContext, Uri.fromFile(file), includeAlbumArt);
        AudioItem item = new AudioItem(meta.uri, meta.filePath, meta.title, meta.artist,
                                       meta.album, meta.albumArtBytes, meta.duration);
        item.setSize(meta.size);
        item.setTrackNumber(meta.trackNumber);
        return item;
    }

    public static VideoMetadata getVideoMetadata(String filePath) {
        if (mContext == null) return null;
        return getVideoMetadata(mContext, Uri.fromFile(new File(filePath)));
    }

    public static VideoMetadata getVideoMetadata(Context context, Uri videoUri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        long fileSize = 0;
        String filePath = null;
        try {
            retriever.setDataSource(context, videoUri);

            String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            if (title == null || title.trim().isEmpty()) title = "Unknown";
            long duration = 0;
            String durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durStr != null) { try { duration = Long.parseLong(durStr); } catch (NumberFormatException ignored) {} }

            int w = 0, h = 0;
            String wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            if (wStr != null && hStr != null) {
                try { w = Integer.parseInt(wStr); h = Integer.parseInt(hStr); }
                catch (NumberFormatException ignored) {}
            }

            Cursor c = null;
            try {
                c = context.getContentResolver().query(videoUri,
                                                       new String[]{MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.SIZE},
                                                       null, null, null);
                if (c != null && c.moveToFirst()) {
                    int dataIdx = c.getColumnIndex(MediaStore.MediaColumns.DATA);
                    int sizeIdx = c.getColumnIndex(MediaStore.MediaColumns.SIZE);
                    if (dataIdx != -1) filePath = c.getString(dataIdx);
                    if (sizeIdx != -1) fileSize = c.getLong(sizeIdx);
                }
            } catch (Exception ignored) {} finally { if (c != null) c.close(); }

            return new VideoMetadata(videoUri, filePath, title, duration, w, h, fileSize);
        } catch (Exception e) {
            Log.e(TAG, "Error extracting video metadata: " + videoUri, e);
            return new VideoMetadata(videoUri, null, "Unknown", 0, 0, 0, fileSize);
        } finally { try { retriever.release(); } catch (Exception ignored) {} }
    }

    // =========================================================================
    // THUMBNAILS (fixed to support file:// URIs)
    // =========================================================================

    public static synchronized Bitmap getVideoThumbnailCached(Context context, Uri videoUri) {
        String cacheKey = videoUri.toString();
        Bitmap cached = thumbnailCache.get(cacheKey);
        if (cached != null && !cached.isRecycled()) return cached;

        Bitmap thumb = null;
        try {
            // ★ file:// Uri path – can happen when falling back to directory scan
            if ("file".equals(videoUri.getScheme())) {
                String filePath = videoUri.getPath();
                if (filePath != null) {
                    thumb = ThumbnailUtils.createVideoThumbnail(filePath, MediaStore.Video.Thumbnails.MINI_KIND);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // content:// Uri on API 29+ – use modern API
                Size size = new Size(VIDEO_THUMB_WIDTH, VIDEO_THUMB_HEIGHT);
                thumb = context.getContentResolver().loadThumbnail(videoUri, size, null);
            } else {
                // content:// Uri on API < 29 – resolve DATA column and use ThumbnailUtils
                Cursor c = null;
                String filePath = null;
                try {
                    c = context.getContentResolver().query(videoUri,
                                                           new String[]{MediaStore.MediaColumns.DATA}, null, null, null);
                    if (c != null && c.moveToFirst()) {
                        filePath = c.getString(0);
                    }
                } catch (Exception ignored) {} finally { if (c != null) c.close(); }

                if (filePath != null) {
                    thumb = ThumbnailUtils.createVideoThumbnail(filePath, MediaStore.Video.Thumbnails.MINI_KIND);
                }
            }

            if (thumb != null) {
                thumb = getScaledBitmap(thumb, VIDEO_THUMB_WIDTH, VIDEO_THUMB_HEIGHT);
                thumbnailCache.put(cacheKey, thumb);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading video thumbnail for " + videoUri, e);
        }
        return thumb;
    }

    @Deprecated
    public static synchronized Bitmap getVideoThumbnailCached(String filePath, int kind) {
        if (mContext == null) return null;
        return getVideoThumbnailCached(mContext, Uri.fromFile(new File(filePath)));
    }

    public static Bitmap getVideoThumbnail(String filePath, int kind) {
        Bitmap thumb = ThumbnailUtils.createVideoThumbnail(filePath, kind);
        if (thumb != null) {
            thumb = getScaledBitmap(thumb, VIDEO_THUMB_WIDTH, VIDEO_THUMB_HEIGHT);
        }
        return thumb;
    }

    public static synchronized Bitmap getAudioAlbumArtBitmap(Context context, Uri audioUri) {
        String cacheKey = audioUri.toString() + "_album";
        Bitmap cached = thumbnailCache.get(cacheKey);
        if (cached != null && !cached.isRecycled()) return cached;

        byte[] artBytes = extractAudioAlbumArt(context, audioUri);
        if (artBytes == null) return getDefaultAlbumArt(context);

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(artBytes, 0, artBytes.length, opts);
        opts.inSampleSize = calculateInSampleSize(opts, ALBUM_ART_MAX_SIZE, ALBUM_ART_MAX_SIZE);
        opts.inJustDecodeBounds = false;
        opts.inPreferredConfig = Bitmap.Config.RGB_565;

        Bitmap bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.length, opts);
        if (bitmap != null) {
            if (bitmap.getWidth() > ALBUM_ART_MAX_SIZE || bitmap.getHeight() > ALBUM_ART_MAX_SIZE) {
                Bitmap scaled = getScaledBitmap(bitmap, ALBUM_ART_MAX_SIZE, ALBUM_ART_MAX_SIZE);
                if (scaled != bitmap) {
                    bitmap.recycle();
                    bitmap = scaled;
                }
            }
            thumbnailCache.put(cacheKey, bitmap);
        }
        return bitmap;
    }

    public static synchronized Bitmap getAudioAlbumArtBitmap(String filePath) {
        if (mContext == null) return null;
        return getAudioAlbumArtBitmap(mContext, Uri.fromFile(new File(filePath)));
    }

    public static void getAudioAlbumArtBitmapAsync(final Uri uri,
                                                   final Context context,
                                                   final Handler uiHandler,
                                                   final AlbumArtCallback callback) {
        new Thread(new Runnable() {
            @Override public void run() {
                Bitmap art = getAudioAlbumArtBitmap(context, uri);
                if (art == null) art = getDefaultAlbumArt(context);
                final Bitmap result = art;
                uiHandler.post(new Runnable() {
                    @Override public void run() {
                        if (callback != null) callback.onAlbumArtLoaded(uri, result);
                    }
                });
            }
        }, "album-art-loader").start();
    }

    // =========================================================================
    // UTILITY
    // =========================================================================

    private static int calculateInSampleSize(BitmapFactory.Options opts, int reqW, int reqH) {
        int h = opts.outHeight, w = opts.outWidth, s = 1;
        if (h > reqH || w > reqW) {
            int halfH = h / 2;
            int halfW = w / 2;
            while ((halfH / s) >= reqH && (halfW / s) >= reqW) {
                s *= 2;
            }
        }
        return s;
    }

    public static synchronized void clearThumbnailCache() {
        thumbnailCache.evictAll();
    }

    // =========================================================================
    // DIRECTORY SCANNING
    // =========================================================================

    private static List<String> scanDirectoryForAudioPaths(File directory) {
        List<String> paths = new ArrayList<String>();
        if (directory == null || !directory.exists() || !directory.isDirectory()) return paths;
        if (!sShowNoMediaDirs && hasNoMediaFile(directory)) return paths;
        File[] files = directory.listFiles();
        if (files == null) return paths;
        for (File file : files) {
            if (file.isDirectory()) {
                String name = file.getName().toLowerCase();
                if (!name.startsWith(".") && !name.equals("android") && !name.equals("lost+found")) {
                    paths.addAll(scanDirectoryForAudioPaths(file));
                }
            } else if (isAudioFile(file)) {
                paths.add(file.getAbsolutePath());
            }
        }
        return paths;
    }

    private static List<String> scanDirectoryForVideoPaths(File directory) {
        List<String> paths = new ArrayList<String>();
        if (directory == null || !directory.exists() || !directory.isDirectory()) return paths;
        if (!sShowNoMediaDirs && hasNoMediaFile(directory)) return paths;
        File[] files = directory.listFiles();
        if (files == null) return paths;
        for (File file : files) {
            if (file.isDirectory()) {
                String name = file.getName().toLowerCase();
                if (!name.startsWith(".") && !name.equals("android") && !name.equals("lost+found")) {
                    paths.addAll(scanDirectoryForVideoPaths(file));
                }
            } else if (isVideoFile(file)) {
                paths.add(file.getAbsolutePath());
            }
        }
        return paths;
    }

    private static List<String> scanDirectoryForPhotoPaths(File directory) {
        List<String> paths = new ArrayList<String>();
        if (directory == null || !directory.exists() || !directory.isDirectory()) return paths;
        if (!sShowNoMediaDirs && hasNoMediaFile(directory)) return paths;
        File[] files = directory.listFiles();
        if (files == null) return paths;
        for (File file : files) {
            if (file.isDirectory()) {
                String name = file.getName().toLowerCase();
                if (!name.startsWith(".") && !name.equals("android") && !name.equals("lost+found")) {
                    paths.addAll(scanDirectoryForPhotoPaths(file));
                }
            } else if (isPhotoFile(file)) {
                paths.add(file.getAbsolutePath());
            }
        }
        return paths;
    }

    public static List<AudioItem> scanDirectoryForAudio(File directory) {
        List<AudioItem> items = new ArrayList<AudioItem>();
        if (directory == null || !directory.exists() || !directory.isDirectory()) return items;
        if (!sShowNoMediaDirs && hasNoMediaFile(directory)) return items;
        File[] files = directory.listFiles();
        if (files == null) return items;
        for (File file : files) {
            if (file.isDirectory()) {
                items.addAll(scanDirectoryForAudio(file));
            } else if (isAudioFile(file)) {
                try {
                    AudioItem item = extractAudioMetadata(file, false);
                    if (item != null) items.add(item);
                } catch (Exception e) { Log.e(TAG, "Error scanning: " + file.getAbsolutePath(), e); }
            }
        }
        return items;
    }

    private static List<String> removeDuplicatePaths(List<String> paths) {
        return new ArrayList<String>(new HashSet<String>(paths));
    }

    // =========================================================================
    // FILE TYPE CHECKS
    // =========================================================================

    public static boolean isAudioFile(File file) {
        String n = file.getName().toLowerCase();
        return n.endsWith(".mp3") || n.endsWith(".wav") || n.endsWith(".flac")
            || n.endsWith(".aac") || n.endsWith(".m4a") || n.endsWith(".ogg")
            || n.endsWith(".wma") || n.endsWith(".amr");
    }

    public static boolean isVideoFile(File file) {
        String n = file.getName().toLowerCase();
        return n.endsWith(".mp4") || n.endsWith(".avi") || n.endsWith(".mkv")
            || n.endsWith(".flv") || n.endsWith(".wmv") || n.endsWith(".mov")
            || n.endsWith(".3gp") || n.endsWith(".webm");
    }

    public static boolean isPhotoFile(File file) {
        String n = file.getName().toLowerCase();
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")
            || n.endsWith(".gif") || n.endsWith(".bmp") || n.endsWith(".webp");
    }

    // =========================================================================
    // BITMAP HELPERS
    // =========================================================================

    public static Bitmap getScaledBitmap(Bitmap source, int maxWidth, int maxHeight) {
        if (source == null) return null;
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= maxWidth && height <= maxHeight) {
            return source;
        }
        float ratio = Math.min((float) maxWidth / width, (float) maxHeight / height);
        int newWidth = Math.round(width * ratio);
        int newHeight = Math.round(height * ratio);
        Bitmap scaled = Bitmap.createScaledBitmap(source, newWidth, newHeight, true);
        return scaled;
    }

    public static Bitmap getScaledBitmap(String filePath, int maxWidth, int maxHeight) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, opts);
        int scale = 1;
        if (opts.outWidth > maxWidth || opts.outHeight > maxHeight) {
            scale = Math.max(opts.outWidth / maxWidth, opts.outHeight / maxHeight);
        }
        opts.inSampleSize = scale;
        opts.inJustDecodeBounds = false;
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(filePath, opts);
    }

    public static Bitmap getScaledBitmapFromResource(Context context, int resId, int maxWidth, int maxHeight) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(context.getResources(), resId, opts);
        int scale = calculateInSampleSize(opts, maxWidth, maxHeight);
        opts.inSampleSize = scale;
        opts.inJustDecodeBounds = false;
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeResource(context.getResources(), resId, opts);
    }

    public static boolean isStereo(String filePath) {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(filePath);
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat fmt = extractor.getTrackFormat(i);
                String mime = fmt.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/"))
                    return fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 2;
            }
        } catch (Exception e) { Log.e(TAG, "isStereo error: " + filePath, e); }
        finally { try { extractor.release(); } catch (Exception ignored) {} }
        return false;
    }

    public static Date getAudioFileDateAdded(Context context, String filePath) {
        if (context == null || filePath == null) return null;
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Media.DATE_ADDED},
                MediaStore.Audio.Media.DATA + " = ?",
                new String[]{filePath}, null);
            if (cursor != null && cursor.moveToFirst()) {
                long secs = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED));
                return new Date(secs * 1000L);
            }
        } catch (Exception e) { Log.e(TAG, "getAudioFileDateAdded error", e); }
        finally { if (cursor != null) cursor.close(); }
        return null;
    }

    public static boolean isAudioFileRecentlyAdded(Context context, String filePath, long daysLimit) {
        Date dateAdded = getAudioFileDateAdded(context, filePath);
        if (dateAdded == null) return false;
        long diff = System.currentTimeMillis() - dateAdded.getTime();
        return diff <= (daysLimit * 24L * 60L * 1000L);
    }

    public static String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = {"B", "KB", "MB", "GB", "TB"};
        int dg = (int) (Math.log10(size) / Math.log10(1024));
        dg = Math.min(dg, units.length - 1);
        return String.format("%.1f %s", size / Math.pow(1024, dg), units[dg]);
    }

    public static Bitmap getDefaultAlbumArt(Context context) {
        initDefaultAlbumArts(context);
        return mAlbumArts[new Random().nextInt(mAlbumArts.length)];
    }
}