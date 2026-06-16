package com.ikechi.studio.onwa.player.utils;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.Nullable;   // AndroidX annotation, consistent with project

public class RealPathUtil {
    private static final String TAG = "RealPathUtil";

    /**
     * @deprecated DO NOT USE on Android 10+. Scoped Storage blocks direct file access.
     * Use the content:// Uri directly with ContentResolver instead.
     * This method returns null for all content:// uris on Android 10+.
     */
    @Deprecated
    @Nullable
    public static String getPath(Context context, Uri uri) {
        // ── Hard block on Android 10+ ─────────────────────────────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.w(TAG, "getPath() called on API " + Build.VERSION.SDK_INT
                  + " – returning null. Use Uri directly.");
            return null;
        }

        // file:// uri – only works if you created the file
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        // DocumentProvider
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
            && DocumentsContract.isDocumentUri(context, uri)) {
            if (isExternalStorageDocument(uri)) {
                String docId = DocumentsContract.getDocumentId(uri);
                String[] split = docId.split(":");
                String type = split[0];
                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }
                // TODO: Handle non-primary volumes like SD cards
            } else if (isDownloadsDocument(uri)) {
                String id = DocumentsContract.getDocumentId(uri);

                // Handle raw downloads id like "raw:/storage/emulated/0/Download/file.mp4"
                if (id != null && id.startsWith("raw:")) {
                    return id.replaceFirst("raw:", "");
                }

                // Handle msf: id – cannot resolve
                if (id != null && id.startsWith("msf:")) {
                    return null;
                }

                try {
                    Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.parseLong(id));
                    return getDataColumn(context, contentUri, null, null);
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Downloads id not a number: " + id);
                    return null;
                }
            } else if (isMediaDocument(uri)) {
                String docId = DocumentsContract.getDocumentId(uri);
                String[] split = docId.split(":");
                String type = split[0];
                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }
                if (contentUri != null) {
                    String selection = MediaStore.MediaColumns._ID + "=?";
                    String[] selectionArgs = new String[]{split[1]};
                    return getDataColumn(context, contentUri, selection, selectionArgs);
                }
            }
        }
        // MediaStore content:// uri
        else if ("content".equalsIgnoreCase(uri.getScheme()) && Build.VERSION.SDK_INT < 29) {
            return getDataColumn(context, uri, null, null);
        }

        return null;
    }

    /**
     * @deprecated MediaStore.MediaColumns.DATA is deprecated on Android 10+.
     * Will return null for files you don't own. Use ContentResolver.openInputStream(uri) instead.
     */
    @Deprecated
    @Nullable
    private static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        final String column = MediaStore.MediaColumns.DATA;
        final String[] projection = {column};
        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(index);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get DATA column for " + uri + ": " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }

    private static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    private static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    private static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    /**
     * Preferred method: Check if you can read the uri directly instead of converting to path.
     * Use this to decide whether to call getPath() or just use the Uri.
     */
    public static boolean canResolveToFilePath(Context context, Uri uri) {
        // On Android 10+ we can never safely resolve a content:// URI to a file path,
        // unless it's a file:// URI that we own.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return "file".equalsIgnoreCase(uri.getScheme());
        }
        return true; // API < 29: getPath() usually works
    }
}