package com.ikechi.studio.onwa.player.widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import com.ikechi.studio.IkLog;

import java.io.ByteArrayOutputStream;

/** Simple persistent store for last known widget data. */
public class WidgetDataHelper {
    private static final String TAG = "WidgetDataHelper";
    private static final String PREF = "widget_data";
    private static final int MAX_ART_BASE64_LENGTH = 100_000;

    public static class WidgetData {
        public String title = "Not Playing";
        public String artist = "";
        public Bitmap art = null;
        public boolean isPlaying = false;
    }

    /**
     * Save widget data. Art is resized to 200x200 to avoid huge Base64 strings.
     */
    public static void save(Context ctx, String title, String artist, Bitmap art, boolean isPlaying) {
        try {
            SharedPreferences prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("title", title != null ? title : "Not Playing");
            editor.putString("artist", artist != null ? artist : "");
            editor.putBoolean("playing", isPlaying);

            if (art != null && !art.isRecycled()) {
                // Resize to max 200x200 to save memory and avoid transactionTooLarge
                int maxSize = 200;
                Bitmap resized = art;
                if (art.getWidth() > maxSize || art.getHeight() > maxSize) {
                    float scale = Math.min((float) maxSize / art.getWidth(), (float) maxSize / art.getHeight());
                    int newWidth = Math.round(art.getWidth() * scale);
                    int newHeight = Math.round(art.getHeight() * scale);
                    try {
                        resized = Bitmap.createScaledBitmap(art, newWidth, newHeight, true);
                    } catch (Throwable t) {
                        IkLog.e(TAG, "Error resizing widget art, using original", t);
                        resized = art;
                    }
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                resized.compress(Bitmap.CompressFormat.JPEG, 70, baos);
                String encoded = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
                editor.putString("art", encoded);
                if (resized != art) {
                    try { resized.recycle(); } catch (Throwable ignored) {}
                }
            } else {
                editor.remove("art");
            }
            editor.apply();
            IkLog.d(TAG, "Saved widget data: " + title + " - " + artist + " (playing=" + isPlaying + ")");
        } catch (Throwable e) {
            IkLog.e(TAG, "Error saving widget data", e);
        }
    }

    public static WidgetData getLastData(Context ctx) {
        WidgetData data = new WidgetData();
        try {
            SharedPreferences prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            data.title = prefs.getString("title", "Not Playing");
            data.artist = prefs.getString("artist", "");
            data.isPlaying = prefs.getBoolean("playing", false);
            String artStr = prefs.getString("art", null);

            if (artStr != null && artStr.length() > 0 && artStr.length() < MAX_ART_BASE64_LENGTH) {
                try {
                    byte[] bytes = Base64.decode(artStr, Base64.DEFAULT);
                    data.art = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (data.art == null) {
                        IkLog.w(TAG, "Failed to decode widget art bitmap from saved data");
                    } else {
                        IkLog.d(TAG, "Loaded widget data with art (" + data.art.getWidth() + "x" + data.art.getHeight() + ")");
                    }
                } catch (Throwable t) {
                    IkLog.e(TAG, "Error decoding art for widget", t);
                    data.art = null;
                }
            } else if (artStr != null && artStr.length() >= MAX_ART_BASE64_LENGTH) {
                IkLog.w(TAG, "Art Base64 string too large, skipping: " + artStr.length() + " chars");
            } else {
                IkLog.d(TAG, "Loaded widget data: " + data.title + " - " + data.artist + " (playing=" + data.isPlaying + ") no art");
            }
        } catch (Throwable t) {
            IkLog.e(TAG, "Error getting last widget data", t);
        }
        return data;
    }
}