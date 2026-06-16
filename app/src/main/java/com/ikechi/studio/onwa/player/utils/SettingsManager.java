package com.ikechi.studio.onwa.player.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import androidx.preference.PreferenceManager;
import android.util.Log;

import com.ikechi.studio.onwa.player.view.core.GLVisualizerRenderer;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SettingsManager — single source of truth for all user preferences.
 */
public class SettingsManager {

    private static final String TAG = "SettingsManager";
    private static volatile SettingsManager instance;

    private final Context context;
    private final SharedPreferences preferences;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;
    private final Handler mainHandler;

    private final CopyOnWriteArrayList<OnVisualizerSettingsChangedListener> visualizerListeners;
    private final CopyOnWriteArrayList<OnPlayerSettingsChangedListener> playerListeners;

    private SettingsCache currentSettings;

    // --- Visualizer Style Indices ---
    public static final int STYLE_BARS = 0;
    public static final int STYLE_WAVEFORM = 1;
    public static final int STYLE_CIRCLE = 2;
    public static final int STYLE_PARTICLE = 3;
    public static final int STYLE_SPECTRUM = 4;
    public static final int STYLE_FLAME = 5;
    public static final int STYLE_NEBULA_FLOW = 6;
    public static final int STYLE_STAR_FLARES = 7;
    public static final int STYLE_DANCING_BABY_2D = 8;
    public static final int STYLE_DANCING_BABY_3D = 9;
    public static final int STYLE_GLASS_BALLS = 10;
    public static final int STYLE_GEOMETRIC = 11;
    public static final int STYLE_HEARTBEAT = 12;
    public static final int STYLE_NEON_GRID = 13;
    public static final int STYLE_COSMIC = 14;
    public static final int STYLE_KALEIDO_FRACTAL = 15;
    public static final int STYLE_CRYSTAL_MANDALA = 16;
    public static final int STYLE_BEAUTIFUL_MANDALA = 17;
    // NEW CELESTIAL STYLES
    public static final int STYLE_PULSAR = 18;
    public static final int STYLE_BLACK_HOLE = 19;
    public static final int STYLE_NEBULA = 20;
    public static final int STYLE_GALAXY = 21;
    public static final int STYLE_COMET = 22;

    // --- Color Scheme Indices ---
    public static final int COLOR_SCHEME_RAINBOW = 0;
    public static final int COLOR_SCHEME_FIRE = 1;
    public static final int COLOR_SCHEME_OCEAN = 2;
    public static final int COLOR_SCHEME_FOREST = 3;
    public static final int COLOR_SCHEME_MONOCHROME = 4;
    public static final int COLOR_SCHEME_COSMIC = 5;
    public static final int COLOR_SCHEME_NEON = 6;
    public static final int COLOR_SCHEME_AURORA = 7;

    public static final boolean AUTO_ROTATE_STYLES_DEFAULT = true;
    public static final int STYLE_ROTATION_INTERVAL_DEFAULT = 10000;

    // --- Listener Interfaces ---
    public interface OnVisualizerSettingsChangedListener {
        void onVisualizerSettingsChanged(VisualizerSettings settings);
    }

    public interface OnPlayerSettingsChangedListener {
        void onPlayerSettingsChanged(PlayerSettings settings);
    }

    // --- Settings POJOs ---
    public static class VisualizerSettings {
        public int renderMode;
        public int visualizerStyle;
        public int colorScheme;
        public int performanceLevel;
        public float sensitivity;
        public int barCount;
        public float rotationSpeed;
        public boolean wireframe;
        public boolean useVBO;
        public boolean useLighting;
        public boolean useTrueFFT;
        public boolean autoRotateStyles;
        public int styleRotationInterval;
        public int particleCount;
        public int glassBallCount;
        public boolean touchInteraction;
        public float beatDecayRate;
        public boolean showBeatIndicator;
    }

    public static class PlayerSettings {
        public boolean autoPlay;
        public boolean gaplessPlayback;
        public boolean crossfade;
        public boolean keepScreenOn;
        public int defaultTab;
        public int crossfadeDuration;
        public int visualizerUpdateRate;
        public boolean highQualityAudio;
        public boolean realTimeProcessing;
        public boolean showNoMediaFiles;
        
    }

    private static class SettingsCache {
        VisualizerSettings visualizerSettings = new VisualizerSettings();
        PlayerSettings playerSettings = new PlayerSettings();
    }

    // --- Singleton ---
    private SettingsManager(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = PreferenceManager.getDefaultSharedPreferences(this.context);
        this.mainHandler = new Handler(Looper.getMainLooper());

        visualizerListeners = new CopyOnWriteArrayList<OnVisualizerSettingsChangedListener>();
        playerListeners = new CopyOnWriteArrayList<OnPlayerSettingsChangedListener>();

        currentSettings = new SettingsCache();
        loadAllSettings();
        setupPreferenceListener();
        Log.d(TAG, "SettingsManager initialized");
    }

    public static synchronized SettingsManager getInstance(Context context) {
        if (instance == null) {
            instance = new SettingsManager(context);
        }
        return instance;
    }

    // --- Preference Listener ---
    private void setupPreferenceListener() {
        preferenceListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                loadAllSettings();

                boolean visualizerChanged = false;
                boolean playerChanged = false;

                if (key.startsWith("render_") || key.startsWith("visualizer_")
                    || key.equals("color_scheme") || key.equals("performance_level")
                    || key.equals("sensitivity") || key.equals("bar_count")
                    || key.equals("rotation_speed") || key.equals("wireframe")
                    || key.equals("use_vbo") || key.equals("use_lighting")
                    || key.equals("auto_rotate_styles") || key.equals("style_rotation_interval")
                    || key.equals("particle_count") || key.equals("glass_ball_count")
                    || key.equals("touch_interaction") || key.equals("beat_decay_rate")
                    || key.equals("show_beat_indicator")) {
                    visualizerChanged = true;
                }

                if (key.startsWith("auto_") || key.startsWith("gapless_")
                    || key.equals("crossfade") || key.equals("keep_screen_on")
                    || key.equals("default_tab") || key.equals("crossfade_duration")
                    || key.equals("visualizer_update_rate")
                    || key.equals("high_quality_audio") || key.equals("real_time_processing")
                    || key.equals("show_no_media_files")) {
                    playerChanged = true;
                }

                if (visualizerChanged) {
                    notifyVisualizerSettingsChanged();
                }
                if (playerChanged) {
                    notifyPlayerSettingsChanged();
                }
            }
        };
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
    }

    // --- Load/Save ---
    private synchronized void loadAllSettings() {
        // Visualizer
        currentSettings.visualizerSettings.renderMode = preferences.getInt("render_mode", GLVisualizerRenderer.MODE_3D);
        currentSettings.visualizerSettings.visualizerStyle = preferences.getInt("visualizer_style", STYLE_NEBULA_FLOW);
        currentSettings.visualizerSettings.colorScheme = preferences.getInt("color_scheme", COLOR_SCHEME_FIRE);
        currentSettings.visualizerSettings.performanceLevel = preferences.getInt("performance_level", GLVisualizerRenderer.PERFORMANCE_MEDIUM);
        currentSettings.visualizerSettings.sensitivity = preferences.getFloat("sensitivity", 1.5f);
        currentSettings.visualizerSettings.barCount = preferences.getInt("bar_count", 64);
        currentSettings.visualizerSettings.rotationSpeed = preferences.getFloat("rotation_speed", 0.5f);
        currentSettings.visualizerSettings.wireframe = preferences.getBoolean("wireframe", false);
        currentSettings.visualizerSettings.useVBO = preferences.getBoolean("use_vbo", true);
        currentSettings.visualizerSettings.useLighting = preferences.getBoolean("use_lighting", true);
        currentSettings.visualizerSettings.useTrueFFT = preferences.getBoolean("use_true_fft", true);
        currentSettings.visualizerSettings.autoRotateStyles = preferences.getBoolean("auto_rotate_styles", AUTO_ROTATE_STYLES_DEFAULT);
        currentSettings.visualizerSettings.styleRotationInterval = preferences.getInt("style_rotation_interval", STYLE_ROTATION_INTERVAL_DEFAULT);
        currentSettings.visualizerSettings.particleCount = preferences.getInt("particle_count", 300);
        currentSettings.visualizerSettings.glassBallCount = preferences.getInt("glass_ball_count", 10);
        currentSettings.visualizerSettings.touchInteraction = preferences.getBoolean("touch_interaction", true);
        currentSettings.visualizerSettings.beatDecayRate = preferences.getFloat("beat_decay_rate", 0.85f);
        currentSettings.visualizerSettings.showBeatIndicator = preferences.getBoolean("show_beat_indicator", true);

        // Player
        currentSettings.playerSettings.autoPlay = preferences.getBoolean("auto_play", true);
        currentSettings.playerSettings.gaplessPlayback = preferences.getBoolean("gapless_playback", false);
        currentSettings.playerSettings.crossfade = preferences.getBoolean("crossfade", false);
        currentSettings.playerSettings.keepScreenOn = preferences.getBoolean("keep_screen_on", false);
        currentSettings.playerSettings.defaultTab = preferences.getInt("default_tab", 0);
        currentSettings.playerSettings.crossfadeDuration = preferences.getInt("crossfade_duration", 3);
        currentSettings.playerSettings.visualizerUpdateRate = preferences.getInt("visualizer_update_rate", 60);
        currentSettings.playerSettings.highQualityAudio = preferences.getBoolean("high_quality_audio", true);
        currentSettings.playerSettings.realTimeProcessing = preferences.getBoolean("real_time_processing", true);
        currentSettings.playerSettings.showNoMediaFiles = preferences.getBoolean("show_no_media_files", false);
      
    }

    public void saveVisualizerSettings(VisualizerSettings settings) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("render_mode", settings.renderMode);
        editor.putInt("visualizer_style", settings.visualizerStyle);
        editor.putInt("color_scheme", settings.colorScheme);
        editor.putInt("performance_level", settings.performanceLevel);
        editor.putFloat("sensitivity", settings.sensitivity);
        editor.putInt("bar_count", settings.barCount);
        editor.putFloat("rotation_speed", settings.rotationSpeed);
        editor.putBoolean("wireframe", settings.wireframe);
        editor.putBoolean("use_vbo", settings.useVBO);
        editor.putBoolean("use_lighting", settings.useLighting);
        editor.putBoolean("use_true_fft", settings.useTrueFFT);
        editor.putBoolean("auto_rotate_styles", settings.autoRotateStyles);
        editor.putInt("style_rotation_interval", settings.styleRotationInterval);
        editor.putInt("particle_count", settings.particleCount);
        editor.putInt("glass_ball_count", settings.glassBallCount);
        editor.putBoolean("touch_interaction", settings.touchInteraction);
        editor.putFloat("beat_decay_rate", settings.beatDecayRate);
        editor.putBoolean("show_beat_indicator", settings.showBeatIndicator);
        editor.apply();

        currentSettings.visualizerSettings = settings;
        notifyVisualizerSettingsChanged();
    }

    public void savePlayerSettings(PlayerSettings settings) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("auto_play", settings.autoPlay);
        editor.putBoolean("gapless_playback", settings.gaplessPlayback);
        editor.putBoolean("crossfade", settings.crossfade);
        editor.putBoolean("keep_screen_on", settings.keepScreenOn);
        editor.putInt("default_tab", settings.defaultTab);
        editor.putInt("crossfade_duration", settings.crossfadeDuration);
        editor.putInt("visualizer_update_rate", settings.visualizerUpdateRate);
        editor.putBoolean("high_quality_audio", settings.highQualityAudio);
        editor.putBoolean("real_time_processing", settings.realTimeProcessing);
        editor.putBoolean("show_no_media_files", settings.showNoMediaFiles);
        editor.apply();

        currentSettings.playerSettings = settings;
        notifyPlayerSettingsChanged();
    }

    // --- Getters ---
    public synchronized VisualizerSettings getVisualizerSettings() {
        return currentSettings.visualizerSettings;
    }

    public synchronized PlayerSettings getPlayerSettings() {
        return currentSettings.playerSettings;
    }

    // --- Notify Helpers ---
    private synchronized void notifyVisualizerSettingsChanged() {
        final VisualizerSettings snap = currentSettings.visualizerSettings;
        mainHandler.post(new Runnable() {
				@Override
				public void run() {
					for (OnVisualizerSettingsChangedListener l : visualizerListeners) {
						try {
							l.onVisualizerSettingsChanged(snap);
						} catch (Exception e) {
							Log.e(TAG, "Error notifying visualizer listener", e);
						}
					}
				}
			});
    }

    private synchronized void notifyPlayerSettingsChanged() {
        final PlayerSettings snap = currentSettings.playerSettings;
        mainHandler.post(new Runnable() {
				@Override
				public void run() {
					for (OnPlayerSettingsChangedListener l : playerListeners) {
						try {
							l.onPlayerSettingsChanged(snap);
						} catch (Exception e) {
							Log.e(TAG, "Error notifying player listener", e);
						}
					}
				}
			});
    }

    // --- Listener Management ---
    public void addVisualizerSettingsListener(OnVisualizerSettingsChangedListener l) {
        if (!visualizerListeners.contains(l)) {
            visualizerListeners.add(l);
        }
    }

    public void removeVisualizerSettingsListener(OnVisualizerSettingsChangedListener l) {
        visualizerListeners.remove(l);
    }

    public void addPlayerSettingsListener(OnPlayerSettingsChangedListener l) {
        if (!playerListeners.contains(l)) {
            playerListeners.add(l);
        }
    }

    public void removePlayerSettingsListener(OnPlayerSettingsChangedListener l) {
        playerListeners.remove(l);
    }

    // --- Name Helpers ---
    public String getVisualizerStyleName(int style) {
        switch (style) {
            case STYLE_BARS: return "Bars";
            case STYLE_WAVEFORM: return "Waveform";
            case STYLE_CIRCLE: return "Circle";
            case STYLE_PARTICLE: return "Particles";
            case STYLE_SPECTRUM: return "Spectrum";
            case STYLE_FLAME: return "Flame";
            case STYLE_NEBULA_FLOW: return "Nebula Flow";
            case STYLE_STAR_FLARES: return "Star Flares";
            case STYLE_DANCING_BABY_2D: return "Dancing Baby (2D)";
            case STYLE_DANCING_BABY_3D: return "Dancing Baby (3D)";
            case STYLE_GLASS_BALLS: return "Glass Balls";
            case STYLE_GEOMETRIC: return "Geometric Patterns";
            case STYLE_HEARTBEAT: return "Heartbeat";
            case STYLE_NEON_GRID: return "Neon Grid";
            case STYLE_COSMIC: return "Cosmic";
            case STYLE_KALEIDO_FRACTAL: return "Kaleidoscope";
            case STYLE_CRYSTAL_MANDALA: return "Crystal Mandala";
            case STYLE_BEAUTIFUL_MANDALA: return "Beautiful Mandala";
				// NEW CELESTIAL STYLES
            case STYLE_PULSAR: return "Pulsar";
            case STYLE_BLACK_HOLE: return "Black Hole";
            case STYLE_NEBULA: return "Nebula";
            case STYLE_GALAXY: return "Galaxy";
            case STYLE_COMET: return "Comet";
            default: return "Unknown";
        }
    }

    public String getColorSchemeName(int scheme) {
        switch (scheme) {
            case COLOR_SCHEME_RAINBOW: return "Rainbow";
            case COLOR_SCHEME_FIRE: return "Fire";
            case COLOR_SCHEME_OCEAN: return "Ocean";
            case COLOR_SCHEME_FOREST: return "Forest";
            case COLOR_SCHEME_MONOCHROME: return "Monochrome";
            case COLOR_SCHEME_COSMIC: return "Cosmic";
            case COLOR_SCHEME_NEON: return "Neon";
            case COLOR_SCHEME_AURORA: return "Aurora";
            default: return "Unknown";
        }
    }

    // --- Cleanup ---
    public void cleanup() {
        if (preferences != null && preferenceListener != null) {
            preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener);
            preferenceListener = null;
        }
        visualizerListeners.clear();
        playerListeners.clear();
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
    }
}