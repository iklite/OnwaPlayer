package com.ikechi.studio.onwa.player.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ikechi.studio.onwa.player.models.AudioItem;

import java.lang.reflect.Type;
import java.util.List;

public class PrefUtils {

    private static final String PREF_NAME = "onwa_prefs";

    // ── User setup ───────────────────────────────────────────────────────────
    private static final String KEY_USER_SETUP   = "user_setup";
    private static final String KEY_USERNAME     = "username";
    private static final String KEY_REAL_NAME    = "real_name";

    // ── Display mode ─────────────────────────────────────────────────────────
    private static final String KEY_DISPLAY_MODE = "display_mode";

    // ── Playback state ───────────────────────────────────────────────────────
    private static final String KEY_LAST_URI      = "last_uri";
    private static final String KEY_LAST_POSITION = "last_position";
    private static final String KEY_LAST_SEEK     = "last_seek";
    private static final String KEY_SHUFFLE       = "shuffle";
    private static final String KEY_REPEAT_MODE   = "repeat_mode";

    // ── Floating queue button position ───────────────────────────────────────
    private static final String KEY_QUEUE_BTN_X = "queue_btn_x";
    private static final String KEY_QUEUE_BTN_Y = "queue_btn_y";

    // ── New preferences added for upgraded features ──────────────────────────
    private static final String KEY_AB_REPEAT_ENABLED = "ab_repeat_enabled";
    private static final String KEY_PLAYBACK_SPEED    = "playback_speed";
    private static final String KEY_SLEEP_TIMER_DURATION = "sleep_timer_duration"; // in minutes

    // ── Playlist persistence (for widget resume after service death) ─────────
    private static final String KEY_LAST_PLAYLIST = "last_playlist_json";

    // ── Equalizer preferences ────────────────────────────────────────────────
    private static final String KEY_EQUALIZER_PRESET = "equalizer_preset";
    private static final String KEY_BASS_BOOST       = "bass_boost";

    // =========================================================================
    // User setup
    // =========================================================================

    public static boolean isUserSetup(Context context) {
        return prefs(context).getBoolean(KEY_USER_SETUP, false);
    }

    public static void setUserSetup(Context context, boolean setup) {
        prefs(context).edit().putBoolean(KEY_USER_SETUP, setup).apply();
    }

    public static String getUsername(Context context) {
        return prefs(context).getString(KEY_USERNAME, "");
    }

    public static String getRealName(Context context) {
        return prefs(context).getString(KEY_REAL_NAME, "");
    }

    public static void saveUser(Context context, String username, String realName) {
        prefs(context).edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_REAL_NAME, realName)
            .putBoolean(KEY_USER_SETUP, true)
            .apply();
    }

    // =========================================================================
    // Display mode (0 = Album Art, 1 = Carousel, 2 = Visualizer)
    // =========================================================================

    public static int getDisplayMode(Context context) {
        return prefs(context).getInt(KEY_DISPLAY_MODE, 0);
    }

    public static void setDisplayMode(Context context, int mode) {
        prefs(context).edit().putInt(KEY_DISPLAY_MODE, mode).apply();
    }

    // =========================================================================
    // Playback state
    // =========================================================================

    public static String getLastUri(Context context) {
        return prefs(context).getString(KEY_LAST_URI, null);
    }

    public static void setLastUri(Context context, String uri) {
        prefs(context).edit().putString(KEY_LAST_URI, uri).apply();
    }

    public static int getLastPosition(Context context) {
        return prefs(context).getInt(KEY_LAST_POSITION, 0);
    }

    public static void setLastPosition(Context context, int position) {
        prefs(context).edit().putInt(KEY_LAST_POSITION, position).apply();
    }

    public static int getLastSeek(Context context) {
        return prefs(context).getInt(KEY_LAST_SEEK, 0);
    }

    public static void setLastSeek(Context context, int seekMs) {
        prefs(context).edit().putInt(KEY_LAST_SEEK, seekMs).apply();
    }

    public static boolean getShuffle(Context context) {
        return prefs(context).getBoolean(KEY_SHUFFLE, false);
    }

    public static void setShuffle(Context context, boolean shuffle) {
        prefs(context).edit().putBoolean(KEY_SHUFFLE, shuffle).apply();
    }

    public static int getRepeatMode(Context context) {
        return prefs(context).getInt(KEY_REPEAT_MODE, 0);
    }

    public static void setRepeatMode(Context context, int mode) {
        prefs(context).edit().putInt(KEY_REPEAT_MODE, mode).apply();
    }

    // ── Floating queue button ────────────────────────────────────────────────

    public static float getQueueBtnX(Context ctx) {
        return prefs(ctx).getFloat(KEY_QUEUE_BTN_X, -1f);
    }
    public static void setQueueBtnX(Context ctx, float x) {
        prefs(ctx).edit().putFloat(KEY_QUEUE_BTN_X, x).apply();
    }
    public static float getQueueBtnY(Context ctx) {
        return prefs(ctx).getFloat(KEY_QUEUE_BTN_Y, -1f);
    }
    public static void setQueueBtnY(Context ctx, float y) {
        prefs(ctx).edit().putFloat(KEY_QUEUE_BTN_Y, y).apply();
    }

    // ── A‑B Repeat ───────────────────────────────────────────────────────────

    public static boolean isAbRepeatEnabled(Context context) {
        return prefs(context).getBoolean(KEY_AB_REPEAT_ENABLED, false);
    }

    public static void setAbRepeatEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_AB_REPEAT_ENABLED, enabled).apply();
    }

    // ── Playback speed (stored as float, e.g. 1.0f = normal) ────────────────

    public static float getPlaybackSpeed(Context context) {
        return prefs(context).getFloat(KEY_PLAYBACK_SPEED, 1.0f);
    }

    public static void setPlaybackSpeed(Context context, float speed) {
        prefs(context).edit().putFloat(KEY_PLAYBACK_SPEED, speed).apply();
    }

    // ── Sleep timer duration (last selected option, in minutes) ──────────────

    public static int getSleepTimerDuration(Context context) {
        return prefs(context).getInt(KEY_SLEEP_TIMER_DURATION, 15); // default 15 minutes
    }

    public static void setSleepTimerDuration(Context context, int minutes) {
        prefs(context).edit().putInt(KEY_SLEEP_TIMER_DURATION, minutes).apply();
    }

    // ── Playlist persistence (Gson serialization) ────────────────────────────

    public static void saveLastPlaylist(Context context, List<AudioItem> playlist) {
        if (playlist == null || playlist.isEmpty()) return;
        Gson gson = new Gson();
        String json = gson.toJson(playlist);
        prefs(context).edit().putString(KEY_LAST_PLAYLIST, json).apply();
    }

    public static List<AudioItem> getLastPlaylist(Context context) {
        String json = prefs(context).getString(KEY_LAST_PLAYLIST, null);
        if (json == null) return null;
        Type type = new TypeToken<List<AudioItem>>(){}.getType();
        return new Gson().fromJson(json, type);
    }

    // ── Equalizer preferences ────────────────────────────────────────────────

    public static int getEqualizerPreset(Context context) {
        return prefs(context).getInt(KEY_EQUALIZER_PRESET, 0);
    }

    public static void setEqualizerPreset(Context context, int preset) {
        prefs(context).edit().putInt(KEY_EQUALIZER_PRESET, preset).apply();
    }

    public static int getBassBoost(Context context) {
        return prefs(context).getInt(KEY_BASS_BOOST, 0);
    }

    public static void setBassBoost(Context context, int strength) {
        prefs(context).edit().putInt(KEY_BASS_BOOST, strength).apply();
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
}