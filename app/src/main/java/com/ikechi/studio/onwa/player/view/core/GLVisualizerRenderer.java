package com.ikechi.studio.onwa.player.view.core;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;

import com.ikechi.studio.onwa.player.utils.ShaderUtils;
import com.ikechi.studio.onwa.player.utils.SettingsManager;
import com.ikechi.studio.onwa.player.view.core.visuals.LightManager;
import com.ikechi.studio.onwa.player.view.core.visuals.VisualStyle;
import com.ikechi.studio.onwa.player.view.core.styles.BarsStyle;
import com.ikechi.studio.onwa.player.view.core.styles.CircleStyle;
import com.ikechi.studio.onwa.player.view.core.styles.CosmicStyle;
import com.ikechi.studio.onwa.player.view.core.styles.DancingBaby2DStyle;
import com.ikechi.studio.onwa.player.view.core.styles.DancingBaby3DStyle;
import com.ikechi.studio.onwa.player.view.core.styles.FlameStyle;
import com.ikechi.studio.onwa.player.view.core.styles.GeometricStyle;
import com.ikechi.studio.onwa.player.view.core.styles.GlassBallsStyle;
import com.ikechi.studio.onwa.player.view.core.styles.HeartbeatStyle;
import com.ikechi.studio.onwa.player.view.core.styles.NeonGridStyle;
import com.ikechi.studio.onwa.player.view.core.styles.ParticleStyle;
import com.ikechi.studio.onwa.player.view.core.styles.SpectrumStyle;
import com.ikechi.studio.onwa.player.view.core.styles.StarFlaresStyle;
import com.ikechi.studio.onwa.player.view.core.styles.NebulaFlowStyle;
import com.ikechi.studio.onwa.player.view.core.styles.WaveformStyle;
import com.ikechi.studio.onwa.player.view.core.styles.CrystalMandalaStyle;
import com.ikechi.studio.onwa.player.view.core.styles.KaleidoFractalStyle;
import com.ikechi.studio.onwa.player.view.core.styles.BeautifulMandalaStyle;
import com.ikechi.studio.onwa.player.view.core.styles.PulsarStyle;
import com.ikechi.studio.onwa.player.view.core.styles.BlackHoleStyle;
import com.ikechi.studio.onwa.player.view.core.styles.NebulaStyle;
import com.ikechi.studio.onwa.player.view.core.styles.GalaxyStyle;
import com.ikechi.studio.onwa.player.view.core.styles.CometStyle;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GLVisualizerRenderer implements GLSurfaceView.Renderer {

    private static final String TAG = "GLVisualizerRenderer";

    // ---- Render modes ----
    public static final int MODE_2D = 0;
    public static final int MODE_3D = 1;

    // ---- Visualizer style indices ----
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
    //CELESTIAL STYLES
    public static final int STYLE_PULSAR = 18;
    public static final int STYLE_BLACK_HOLE = 19;
    public static final int STYLE_NEBULA = 20;
    public static final int STYLE_GALAXY = 21;
    public static final int STYLE_COMET = 22;

    // ---- Color scheme indices ----
    public static final int COLOR_SCHEME_RAINBOW = 0;
    public static final int COLOR_SCHEME_FIRE = 1;
    public static final int COLOR_SCHEME_OCEAN = 2;
    public static final int COLOR_SCHEME_FOREST = 3;
    public static final int COLOR_SCHEME_MONOCHROME = 4;
    public static final int COLOR_SCHEME_COSMIC = 5;
    public static final int COLOR_SCHEME_NEON = 6;
    public static final int COLOR_SCHEME_AURORA = 7;

    // ---- Performance levels ----
    public static final int PERFORMANCE_LOW = 0;
    public static final int PERFORMANCE_MEDIUM = 1;
    public static final int PERFORMANCE_HIGH = 2;

    private final Context context;

    // ---- Audio state (updated from audio thread) ----
    private volatile float[] fftBands;
    private volatile boolean beatDetected;
    private volatile float beatIntensity;

    // ---- Settings ----
    private int renderMode = MODE_3D;
    private int visualizerStyle = STYLE_NEBULA_FLOW;
    private int colorScheme = COLOR_SCHEME_RAINBOW;
    private int performanceLevel = PERFORMANCE_MEDIUM;
    private float sensitivity = 1.5f;
    private float rotationSpeed = 0.3f;
    private boolean wireframe = false;
    private boolean autoRotateStyles = true;
    private int styleRotationInterval = 10000;
    private int particleCount = 300;
    private int glassBallCount = 10;
    private boolean touchInteraction = true;
    private float beatDecayRate = 0.85f;
    private boolean showBeatIndicator = true;

    // ---- OpenGL shader programs ----
    private int shaderProgram2D;
    private int shaderProgram3D;
    private int shaderProgram3DUniform;
    private int texturedShaderProgram2D;
    private int texturedShaderProgram3D;

    // ---- Matrices ----
    private final float[] projectionMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[] modelMatrix = new float[16];
    private final float[] mvpMatrix = new float[16];

    // ---- Animation ----
    private float rotationAngle = 0.0f;
    private float totalTime = 0.0f;
    private long lastFrameTimeNs = 0;

    // ---- Touch ----
    private float touchRotationX = 0.0f;
    private float touchRotationY = 0.0f;
    private float lastTouchX, lastTouchY;

    // ---- Styles ----
    private final SparseArray<VisualStyle> styles = new SparseArray<VisualStyle>();
    private VisualStyle activeStyle;
    private long lastStyleChangeTime;

    // ---- Lighting ----
    private final LightManager lightManager = new LightManager();

    // ---- Surface state ----
    private boolean projectionDirty = true;
    private int surfaceWidth, surfaceHeight;

    // ---- Pause flag ----
    private boolean paused = false;

    public GLVisualizerRenderer(Context context, VisualizerConfig config) {
        this.context = context;
        applyConfig(config);
        lastStyleChangeTime = System.currentTimeMillis();
    }

    private void applyConfig(VisualizerConfig config) {
        if (config == null) return;
        renderMode = config.getRenderMode();
        visualizerStyle = config.getVisualizerStyle();
        colorScheme = config.getColorScheme();
        performanceLevel = config.getPerformanceLevel();
        sensitivity = config.getSensitivity();
        rotationSpeed = config.getRotationSpeed();
        wireframe = config.isWireframe();
        autoRotateStyles = config.isAutoRotateStyles();
        styleRotationInterval = config.getStyleRotationInterval();
        particleCount = config.getParticleCount();
        glassBallCount = config.getGlassBallCount();
        touchInteraction = config.isTouchInteraction();
        beatDecayRate = config.getBeatDecayRate();
        showBeatIndicator = config.isShowBeatIndicator();
        projectionDirty = true;
    }

    public void updateVisualizerSettings(SettingsManager.VisualizerSettings settings) {
        if (settings == null) return;
        renderMode = settings.renderMode;
        visualizerStyle = settings.visualizerStyle;
        colorScheme = settings.colorScheme;
        performanceLevel = settings.performanceLevel;
        sensitivity = settings.sensitivity;
        rotationSpeed = settings.rotationSpeed;
        wireframe = settings.wireframe;
        autoRotateStyles = settings.autoRotateStyles;
        styleRotationInterval = settings.styleRotationInterval;
        particleCount = settings.particleCount;
        glassBallCount = settings.glassBallCount;
        touchInteraction = settings.touchInteraction;
        beatDecayRate = settings.beatDecayRate;
        showBeatIndicator = settings.showBeatIndicator;
        projectionDirty = true;
        activeStyle = styles.get(visualizerStyle);
        Log.d(TAG, "Settings updated: style=" + visualizerStyle);
    }

    /** Thread-safe audio update. Called from audio thread. */
    public synchronized void updateAudioData(float[] bands, boolean beat, float intensity) {
        this.fftBands = (bands != null) ? bands.clone() : null;
        this.beatDetected = beat;
        this.beatIntensity = intensity;
    }

    // ---- Performance helpers ----

    public int getBarCount() {
        switch (performanceLevel) {
            case PERFORMANCE_LOW: return 32;
            case PERFORMANCE_HIGH: return 128;
            default: return 64;
        }
    }

    public int getParticleCount() { return particleCount; }
    public int getGlassBallCount() { return glassBallCount; }

    // ---- Renderer lifecycle ----

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.02f, 0.02f, 0.05f, 1.0f);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        try {
            String vert2D = ShaderUtils.loadShaderSource(context, "shaders/vertex_2d.glsl");
            String frag2D = ShaderUtils.loadShaderSource(context, "shaders/fragment_2d.glsl");
            String vert3D = ShaderUtils.loadShaderSource(context, "shaders/vertex_3d.glsl");
            String frag3D = ShaderUtils.loadShaderSource(context, "shaders/fragment_3d_lit.glsl");
            String vert3DUniform = ShaderUtils.loadShaderSource(context, "shaders/vertex_3d_uniform.glsl");
            String frag3DUniform = ShaderUtils.loadShaderSource(context, "shaders/fragment_3d_uniform.glsl");
            String vertTex2D = ShaderUtils.loadShaderSource(context, "shaders/vertex_texture_2d.glsl");
            String fragTex2D = ShaderUtils.loadShaderSource(context, "shaders/fragment_texture_2d.glsl");
            String vertTex3D = ShaderUtils.loadShaderSource(context, "shaders/vertex_texture_3d.glsl");
            String fragTex3D = ShaderUtils.loadShaderSource(context, "shaders/fragment_texture_3d.glsl");

            shaderProgram2D = ShaderUtils.createProgram(vert2D, frag2D);
            shaderProgram3D = ShaderUtils.createProgram(vert3D, frag3D);
            shaderProgram3DUniform = ShaderUtils.createProgram(vert3DUniform, frag3DUniform);
            texturedShaderProgram2D = ShaderUtils.createProgram(vertTex2D, fragTex2D);
            texturedShaderProgram3D = ShaderUtils.createProgram(vertTex3D, fragTex3D);

        } catch (RuntimeException e) {
            Log.e(TAG, "Shader load/compile failed", e);
        }

        initStyles();

        activeStyle = styles.get(visualizerStyle);
        if (activeStyle == null && styles.size() > 0) {
            activeStyle = styles.valueAt(0);
        }
        lastStyleChangeTime = System.currentTimeMillis();
    }

    private void initStyles() {
        // Release any previously created styles
        for (int i = 0; i < styles.size(); i++) {
            styles.valueAt(i).release();
        }
        styles.clear();

        // Existing styles
        styles.put(STYLE_BARS, new BarsStyle());
        styles.put(STYLE_WAVEFORM, new WaveformStyle());
        styles.put(STYLE_CIRCLE, new CircleStyle());
        styles.put(STYLE_PARTICLE, new ParticleStyle());
        styles.put(STYLE_SPECTRUM, new SpectrumStyle());
        styles.put(STYLE_FLAME, new FlameStyle());
        styles.put(STYLE_NEBULA_FLOW, new NebulaFlowStyle());
        styles.put(STYLE_STAR_FLARES, new StarFlaresStyle());
        styles.put(STYLE_DANCING_BABY_2D, new DancingBaby2DStyle());
        styles.put(STYLE_DANCING_BABY_3D, new DancingBaby3DStyle());
        styles.put(STYLE_GLASS_BALLS, new GlassBallsStyle());
        styles.put(STYLE_GEOMETRIC, new GeometricStyle());
        styles.put(STYLE_HEARTBEAT, new HeartbeatStyle());
        styles.put(STYLE_NEON_GRID, new NeonGridStyle());
        styles.put(STYLE_COSMIC, new CosmicStyle());
        styles.put(STYLE_KALEIDO_FRACTAL, new KaleidoFractalStyle());
        styles.put(STYLE_CRYSTAL_MANDALA, new CrystalMandalaStyle());
        styles.put(STYLE_BEAUTIFUL_MANDALA, new BeautifulMandalaStyle());

        // NEW CELESTIAL STYLES
        styles.put(STYLE_PULSAR, new PulsarStyle());
        styles.put(STYLE_BLACK_HOLE, new BlackHoleStyle());
        styles.put(STYLE_NEBULA, new NebulaStyle());
        styles.put(STYLE_GALAXY, new GalaxyStyle());
        styles.put(STYLE_COMET, new CometStyle());

        // Initialize all styles with shaders
        for (int i = 0; i < styles.size(); i++) {
            VisualStyle style = styles.valueAt(i);
            style.init(
                shaderProgram2D,
                shaderProgram3D,
                shaderProgram3DUniform,
                texturedShaderProgram2D,
                texturedShaderProgram3D,
                this
            );
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        surfaceWidth = width;
        surfaceHeight = Math.max(1, height); // avoid division by zero
        GLES20.glViewport(0, 0, width, surfaceHeight);
        projectionDirty = true;
    }

    private void updateProjection() {
        float ratio = (float) surfaceWidth / surfaceHeight;
        Matrix.setIdentityM(projectionMatrix, 0);
        Matrix.setIdentityM(viewMatrix, 0);
        Matrix.setIdentityM(modelMatrix, 0);

        if (renderMode == MODE_3D) {
            Matrix.perspectiveM(projectionMatrix, 0, 45.0f, ratio, 0.1f, 20.0f);
            Matrix.setLookAtM(viewMatrix, 0,
							  0.0f, 0.0f, 3.0f,
							  0.0f, 0.0f, 0.0f,
							  0.0f, 1.0f, 0.0f);
        } else {
            Matrix.orthoM(projectionMatrix, 0, -ratio, ratio, -1.0f, 1.0f, -1.0f, 1.0f);
        }
        projectionDirty = false;
    }

    private void updateMVPMatrix() {
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
        Matrix.multiplyMM(mvpMatrix, 0, mvpMatrix, 0, modelMatrix, 0);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        long now = System.nanoTime();
        float deltaTime = (lastFrameTimeNs > 0)
            ? Math.min((now - lastFrameTimeNs) * 1e-9f, 0.1f)
            : 0.016f;
        lastFrameTimeNs = now;

        if (!paused) totalTime += deltaTime;

        if (projectionDirty) updateProjection();

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // ---- Determine if DancingBaby style is active ----
        boolean isDancingBabyStyle = (visualizerStyle == STYLE_DANCING_BABY_2D ||
            visualizerStyle == STYLE_DANCING_BABY_3D);

        // ---- Rotation Logic ----
        if (!isDancingBabyStyle) {
            // Normal behavior: auto-rotate if not paused
            if (!paused) rotationAngle += rotationSpeed * deltaTime;
        }

        if (renderMode == MODE_3D) {
            Matrix.setIdentityM(modelMatrix, 0);

            if (!isDancingBabyStyle) {
                // Normal 3D styles: apply both auto and touch rotation
                Matrix.rotateM(modelMatrix, 0, rotationAngle + touchRotationX, 0.0f, 1.0f, 0.0f);
                Matrix.rotateM(modelMatrix, 0, touchRotationY, 1.0f, 0.0f, 0.0f);
            }
        } else {
            Matrix.setIdentityM(modelMatrix, 0);
        }
        updateMVPMatrix();

        // ---- Auto-rotate styles ----
        if (autoRotateStyles && !paused
            && System.currentTimeMillis() - lastStyleChangeTime > styleRotationInterval) {
            int nextIndex = (visualizerStyle + 1) % styles.size();
            visualizerStyle = styles.keyAt(nextIndex);
            activeStyle = styles.get(visualizerStyle);
            lastStyleChangeTime = System.currentTimeMillis();
            Log.d(TAG, "Auto-rotating to style: " + visualizerStyle);
        }

        // ---- Snapshot audio data safely ----
        float[] bands = fftBands;
        float intensity = beatIntensity;

        // ---- Update & draw active style ----
        if (activeStyle == null) return;

        if (!paused) activeStyle.update(deltaTime, bands, intensity, totalTime);

        if (renderMode == MODE_3D) {
            lightManager.update(deltaTime);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);

            int prog;
            if (activeStyle.usesUniformColor()) {
                prog = shaderProgram3DUniform;
            } else if (activeStyle.usesTexture()) {
                prog = texturedShaderProgram3D;
            } else {
                prog = shaderProgram3D;
            }
            GLES20.glUseProgram(prog);
            lightManager.setLightUniforms(prog, viewMatrix);

            if (isDancingBabyStyle) {
                float[] projOnly = new float[16];
                System.arraycopy(projectionMatrix, 0, projOnly, 0, 16);
                activeStyle.draw3D(projOnly, this);
            } else {
                activeStyle.draw3D(mvpMatrix, this);
            }

            GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        } else {
            int prog = activeStyle.usesTexture() ? texturedShaderProgram2D : shaderProgram2D;
            GLES20.glUseProgram(prog);

            if (isDancingBabyStyle) {
                float[] projOnly = new float[16];
                System.arraycopy(projectionMatrix, 0, projOnly, 0, 16);
                activeStyle.draw2D(projOnly, this);
            } else {
                activeStyle.draw2D(mvpMatrix, this);
            }
        }
    }

    // ---- Touch ----

    public boolean handleTouchEvent(MotionEvent event) {
        if (!touchInteraction) return false;
        float x = event.getX();
        float y = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = x; lastTouchY = y;
                return true;
            case MotionEvent.ACTION_MOVE:
                touchRotationX += (x - lastTouchX) * 0.5f;
                touchRotationY += (y - lastTouchY) * 0.5f;
                lastTouchX = x; lastTouchY = y;
                return true;
            case MotionEvent.ACTION_UP:
                return true;
        }
        return false;
    }

    public void resetRotation() {
        touchRotationX = 0;
        touchRotationY = 0;
    }

    // ---- Lifecycle ----

    public void pause() {
        paused = true;
    }

    public void resume(float restoredRotationSpeed) {
        paused = false;
        rotationSpeed = restoredRotationSpeed;
        lastFrameTimeNs = 0;
    }

    public void release() {
        for (int i = 0; i < styles.size(); i++) {
            styles.valueAt(i).release();
        }
        styles.clear();
        if (shaderProgram2D != 0) GLES20.glDeleteProgram(shaderProgram2D);
        if (shaderProgram3D != 0) GLES20.glDeleteProgram(shaderProgram3D);
        if (shaderProgram3DUniform != 0) GLES20.glDeleteProgram(shaderProgram3DUniform);
        if (texturedShaderProgram2D != 0) GLES20.glDeleteProgram(texturedShaderProgram2D);
        if (texturedShaderProgram3D != 0) GLES20.glDeleteProgram(texturedShaderProgram3D);
        shaderProgram2D = shaderProgram3D = shaderProgram3DUniform =
            texturedShaderProgram2D = texturedShaderProgram3D = 0;
    }

    // ---- Getters ----

    public int getColorScheme() { return colorScheme; }
    public float getBeatIntensity() { return beatIntensity; }
    public float getSensitivity() { return sensitivity; }
    public float getRotationSpeed() { return rotationSpeed; }
    public boolean isWireframe() { return wireframe; }
    public boolean isShowBeatIndicator() { return showBeatIndicator; }
    public LightManager getLightManager() { return lightManager; }
}

