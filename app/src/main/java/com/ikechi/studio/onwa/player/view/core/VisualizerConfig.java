package com.ikechi.studio.onwa.player.view.core;

/**
 * Holds all configurable settings for the GL visualizer.
 * Call resetToDefaults() to populate sensible starting values.
 */
public class VisualizerConfig {

    private int renderMode;
    private int visualizerStyle;
    private int colorScheme;
    private int performanceLevel;
    private float sensitivity;
    private float rotationSpeed;
    private boolean wireframe;
    private boolean autoRotateStyles;
    private int styleRotationInterval;
    private int particleCount;
    private int glassBallCount;
    private boolean touchInteraction;
    private float beatDecayRate;
    private boolean showBeatIndicator;

    public VisualizerConfig() {
        resetToDefaults();
    }

    /** Populate with safe, visually rich default values. */
    public void resetToDefaults() {
        renderMode           = GLVisualizerRenderer.MODE_3D;
        visualizerStyle      = GLVisualizerRenderer.STYLE_NEBULA_FLOW;
        colorScheme          = GLVisualizerRenderer.COLOR_SCHEME_RAINBOW;
        performanceLevel     = GLVisualizerRenderer.PERFORMANCE_MEDIUM;
        sensitivity          = 1.5f;
        rotationSpeed        = 0.3f;
        wireframe            = false;
        autoRotateStyles     = true;
        styleRotationInterval= 10000;
        particleCount        = 300;
        glassBallCount       = 10;
        touchInteraction     = true;
        beatDecayRate        = 0.85f;
        showBeatIndicator    = true;
    }

    // ---- Getters ----

    public int getRenderMode()            { return renderMode; }
    public int getVisualizerStyle()       { return visualizerStyle; }
    public int getColorScheme()           { return colorScheme; }
    public int getPerformanceLevel()      { return performanceLevel; }
    public float getSensitivity()         { return sensitivity; }
    public float getRotationSpeed()       { return rotationSpeed; }
    public boolean isWireframe()          { return wireframe; }
    public boolean isAutoRotateStyles()   { return autoRotateStyles; }
    public int getStyleRotationInterval() { return styleRotationInterval; }
    public int getParticleCount()         { return particleCount; }
    public int getGlassBallCount()        { return glassBallCount; }
    public boolean isTouchInteraction()   { return touchInteraction; }
    public float getBeatDecayRate()       { return beatDecayRate; }
    public boolean isShowBeatIndicator()  { return showBeatIndicator; }

    // ---- Setters ----

    public VisualizerConfig setRenderMode(int v)            { renderMode = v; return this; }
    public VisualizerConfig setVisualizerStyle(int v)       { visualizerStyle = v; return this; }
    public VisualizerConfig setColorScheme(int v)           { colorScheme = v; return this; }
    public VisualizerConfig setPerformanceLevel(int v)      { performanceLevel = v; return this; }
    public VisualizerConfig setSensitivity(float v)         { sensitivity = v; return this; }
    public VisualizerConfig setRotationSpeed(float v)       { rotationSpeed = v; return this; }
    public VisualizerConfig setWireframe(boolean v)         { wireframe = v; return this; }
    public VisualizerConfig setAutoRotateStyles(boolean v)  { autoRotateStyles = v; return this; }
    public VisualizerConfig setStyleRotationInterval(int v) { styleRotationInterval = v; return this; }
    public VisualizerConfig setParticleCount(int v)         { particleCount = v; return this; }
    public VisualizerConfig setGlassBallCount(int v)        { glassBallCount = v; return this; }
    public VisualizerConfig setTouchInteraction(boolean v)  { touchInteraction = v; return this; }
    public VisualizerConfig setBeatDecayRate(float v)       { beatDecayRate = v; return this; }
    public VisualizerConfig setShowBeatIndicator(boolean v) { showBeatIndicator = v; return this; }
}

