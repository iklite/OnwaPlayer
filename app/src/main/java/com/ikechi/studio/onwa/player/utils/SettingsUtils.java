package com.ikechi.studio.onwa.player.utils;

/**
 * Utility class for comparing settings objects without duplication.
 */
public class SettingsUtils {

    /**
     * Compares two VisualizerSettings objects for equality.
     */
    public static boolean areVisualizerSettingsEqual(SettingsManager.VisualizerSettings s1,
                                                     SettingsManager.VisualizerSettings s2) {
        if (s1 == s2) return true;
        if (s1 == null || s2 == null) return false;

        return s1.renderMode             == s2.renderMode
            && s1.visualizerStyle    == s2.visualizerStyle
            && s1.colorScheme        == s2.colorScheme
            && s1.performanceLevel   == s2.performanceLevel
            && Float.compare(s1.sensitivity,   s2.sensitivity)   == 0
            && s1.barCount           == s2.barCount
            && Float.compare(s1.rotationSpeed, s2.rotationSpeed) == 0
            && s1.wireframe          == s2.wireframe
            && s1.useVBO             == s2.useVBO
            && s1.useLighting        == s2.useLighting
            && s1.autoRotateStyles   == s2.autoRotateStyles
            && s1.styleRotationInterval == s2.styleRotationInterval
            && s1.particleCount      == s2.particleCount
            && s1.glassBallCount     == s2.glassBallCount
            && s1.touchInteraction   == s2.touchInteraction
            && Float.compare(s1.beatDecayRate, s2.beatDecayRate) == 0
            && s1.showBeatIndicator  == s2.showBeatIndicator;
    }

    /**
     * Compares two PlayerSettings objects for equality.
     */
    public static boolean arePlayerSettingsEqual(SettingsManager.PlayerSettings s1,
                                                 SettingsManager.PlayerSettings s2) {
        if (s1 == s2) return true;
        if (s1 == null || s2 == null) return false;

        return s1.autoPlay             == s2.autoPlay
            && s1.gaplessPlayback  == s2.gaplessPlayback
            && s1.crossfade        == s2.crossfade
            && s1.keepScreenOn     == s2.keepScreenOn
            && s1.defaultTab       == s2.defaultTab
            && s1.crossfadeDuration     == s2.crossfadeDuration
            && s1.visualizerUpdateRate  == s2.visualizerUpdateRate
            && s1.highQualityAudio      == s2.highQualityAudio
            && s1.realTimeProcessing    == s2.realTimeProcessing
            && s1.showNoMediaFiles      == s2.showNoMediaFiles;
            
    }
}