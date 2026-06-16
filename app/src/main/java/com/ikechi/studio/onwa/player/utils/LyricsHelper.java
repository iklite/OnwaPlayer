package com.ikechi.studio.onwa.player.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ikechi.studio.IkLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class LyricsHelper {

    private static final String TAG = "LyricsHelper";

    /**
     * Attempts to extract embedded lyrics from an audio file.
     * NOTE: Android has no public SDK constant for a lyrics metadata key in
     * MediaMetadataRetriever. Embedded lyrics are therefore not reliably
     * accessible via the public API on any Android version. This method
     * always returns null and is kept as a defined entry point for
     * future support if a public API becomes available.
     */
    @Nullable
    public static String getEmbeddedLyrics(@NonNull Context context, @NonNull Uri audioUri) {
        IkLog.d(TAG, "getEmbeddedLyrics: no public MediaMetadataRetriever lyrics key exists "
                + "in the Android SDK — embedded lyrics unavailable for " + audioUri);
        return null;
    }

    /**
     * Find and read an external .lrc file for the given audio URI.
     * API 29+  : searches MediaStore Files collection by display name + relative path.
     * API 23-28: resolves the file path via MediaStore.Audio.Media.DATA, then reads
     *            the sibling .lrc directly — file path access is still valid on these levels.
     */
    @Nullable
    public static String getExternalLyricsForUri(@NonNull Context context, @NonNull Uri audioUri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            IkLog.d(TAG, "getExternalLyricsForUri: using MediaStore strategy (API 29+) for " + audioUri);
            return getExternalLyricsViaMediaStore(context, audioUri);
        } else {
            IkLog.d(TAG, "getExternalLyricsForUri: using legacy file-path strategy (API 23-28) for " + audioUri);
            return getExternalLyricsLegacy(context, audioUri);
        }
    }

    /**
     * Legacy convenience method for callers that only have a raw file path (API 23-28).
     * On API 29+ file path access to shared storage is blocked by Scoped Storage;
     * use getExternalLyricsForUri() with a content URI instead.
     *
     * @param audioPath Absolute file path of the audio file e.g. /storage/emulated/0/Music/song.mp3
     */
    @Deprecated
    @Nullable
    public static String getExternalLyrics(@NonNull String audioPath) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            IkLog.w(TAG, "getExternalLyrics(String) is unreliable on API 29+ due to Scoped Storage. "
                    + "Use getExternalLyricsForUri(Context, Uri) instead. path=" + audioPath);
        }

        int dot = audioPath.lastIndexOf('.');
        String lrcPath = (dot > 0 ? audioPath.substring(0, dot) : audioPath) + ".lrc";
        File lrcFile = new File(lrcPath);

        if (!lrcFile.exists() || !lrcFile.isFile()) {
            IkLog.d(TAG, "getExternalLyrics: no .lrc file at " + lrcPath);
            return null;
        }

        IkLog.d(TAG, "getExternalLyrics: reading .lrc at " + lrcPath);
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(lrcFile));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
            String lyrics = sb.toString().trim();
            if (!lyrics.isEmpty()) {
                IkLog.d(TAG, "getExternalLyrics: read " + lyrics.length() + " chars for " + audioPath);
                return lyrics;
            }
            IkLog.d(TAG, "getExternalLyrics: .lrc file was empty at " + lrcPath);
        } catch (Exception e) {
            IkLog.e(TAG, "getExternalLyrics: error reading .lrc at " + lrcPath + ": " + e.getMessage());
        } finally {
            if (br != null) try { br.close(); } catch (Exception ignored) {}
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // Internal — API 29+
    // -------------------------------------------------------------------------

    @Nullable
    private static String getExternalLyricsViaMediaStore(@NonNull Context context,
                                                          @NonNull Uri audioUri) {
        ContentResolver resolver = context.getContentResolver();
        String displayName = null;
        String relativePath = null;

        Cursor cursor = null;
        try {
            cursor = resolver.query(audioUri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIdx >= 0) displayName = cursor.getString(nameIdx);

                int relIdx = cursor.getColumnIndex(MediaStore.Audio.AudioColumns.RELATIVE_PATH);
                if (relIdx < 0) relIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH);
                if (relIdx >= 0) relativePath = cursor.getString(relIdx);
            }
        } catch (Exception e) {
            IkLog.e(TAG, "getExternalLyricsViaMediaStore: error querying audio info: " + e.getMessage());
            return null;
        } finally {
            if (cursor != null) try { cursor.close(); } catch (Exception ignored) {}
        }

        if (displayName == null) {
            IkLog.w(TAG, "getExternalLyricsViaMediaStore: could not resolve display name for " + audioUri);
            return null;
        }

        int dot = displayName.lastIndexOf('.');
        String lrcName = (dot > 0 ? displayName.substring(0, dot) : displayName) + ".lrc";
        IkLog.d(TAG, "getExternalLyricsViaMediaStore: looking for '" + lrcName
                + "' in relativePath='" + relativePath + "'");

        Uri collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL);
        String selection = MediaStore.Files.FileColumns.DISPLAY_NAME + " = ?";
        String[] selectionArgs;
        if (relativePath != null && !relativePath.isEmpty()) {
            selection += " AND " + MediaStore.Files.FileColumns.RELATIVE_PATH + " = ?";
            selectionArgs = new String[]{lrcName, relativePath};
        } else {
            selectionArgs = new String[]{lrcName};
        }

        Cursor lrcCursor = null;
        try {
            lrcCursor = resolver.query(collection,
                    new String[]{MediaStore.Files.FileColumns._ID},
                    selection, selectionArgs, null);

            if (lrcCursor == null || !lrcCursor.moveToFirst()) {
                IkLog.d(TAG, "getExternalLyricsViaMediaStore: no .lrc found in MediaStore for " + audioUri);
                return null;
            }

            long id = lrcCursor.getLong(0);
            Uri lrcUri = Uri.withAppendedPath(collection, String.valueOf(id));
            IkLog.d(TAG, "getExternalLyricsViaMediaStore: .lrc found, reading uri=" + lrcUri);
            return readTextFromUri(resolver, lrcUri, audioUri.toString());

        } catch (Exception e) {
            IkLog.e(TAG, "getExternalLyricsViaMediaStore: error searching .lrc: " + e.getMessage());
        } finally {
            if (lrcCursor != null) try { lrcCursor.close(); } catch (Exception ignored) {}
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // Internal — API 23-28
    // -------------------------------------------------------------------------

    @Nullable
    private static String getExternalLyricsLegacy(@NonNull Context context,
                                                    @NonNull Uri audioUri) {
        String audioPath = null;
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    audioUri,
                    new String[]{MediaStore.Audio.Media.DATA},
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int col = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
                if (col >= 0) audioPath = cursor.getString(col);
            }
        } catch (Exception e) {
            IkLog.e(TAG, "getExternalLyricsLegacy: error resolving file path: " + e.getMessage());
            return null;
        } finally {
            if (cursor != null) try { cursor.close(); } catch (Exception ignored) {}
        }

        if (audioPath == null || audioPath.isEmpty()) {
            IkLog.w(TAG, "getExternalLyricsLegacy: could not resolve file path for " + audioUri);
            return null;
        }

        IkLog.d(TAG, "getExternalLyricsLegacy: resolved path=" + audioPath);
        return getExternalLyrics(audioPath);
    }

    // -------------------------------------------------------------------------
    // Shared utility
    // -------------------------------------------------------------------------

    @Nullable
    private static String readTextFromUri(@NonNull ContentResolver resolver,
                                           @NonNull Uri uri,
                                           @NonNull String logLabel) {
        InputStream inputStream = null;
        BufferedReader reader = null;
        try {
            inputStream = resolver.openInputStream(uri);
            if (inputStream == null) {
                IkLog.w(TAG, "readTextFromUri: openInputStream returned null for " + logLabel);
                return null;
            }
            reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append("\n");
            String content = sb.toString().trim();
            if (!content.isEmpty()) {
                IkLog.d(TAG, "readTextFromUri: read " + content.length() + " chars for " + logLabel);
                return content;
            }
            IkLog.d(TAG, "readTextFromUri: content was empty for " + logLabel);
        } catch (Exception e) {
            IkLog.e(TAG, "readTextFromUri: error reading " + logLabel + ": " + e.getMessage());
        } finally {
            if (reader != null) try { reader.close(); } catch (Exception ignored) {}
            else if (inputStream != null) try { inputStream.close(); } catch (Exception ignored) {}
        }
        return null;
    }
}