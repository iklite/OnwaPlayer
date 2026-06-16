package com.ikechi.studio.onwa.player.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import java.util.Arrays;
import java.util.Random;

import com.ikechi.studio.onwa.player.R;
import android.graphics.*;

/**
 * SpectraViz - Advanced Audio Spectrum Visualizer
 * A visually stunning, high-performance audio visualizer with multiple effects
 */
public class SpectraViz extends View {

    // ===== VISUALIZATION STYLES =====
    public static final int STYLE_PULSING_BARS = 0;
    public static final int STYLE_FLUID_WAVE = 1;
    public static final int STYLE_COSMIC_CIRCLE = 2;
    public static final int STYLE_NEBULA_SPHERE = 3;
    public static final int STYLE_NEURAL_NETWORK = 4;
    public static final int STYLE_GEOMETRIC_FLOW = 5;
    public static final int STYLE_HOLOGRAM = 6;
    public static final int STYLE_CRYSTAL_GRID = 7;

    // ===== COLOR SCHEMES =====
    public static final int COLOR_COSMIC_AURORA = 0;
    public static final int COLOR_OCEAN_DEPTH = 1;
    public static final int COLOR_FIRE_STORM = 2;
    public static final int COLOR_NEBULA = 3;
    public static final int COLOR_BIO_LUMINESCENT = 4;
    public static final int COLOR_SYNTHWAVE = 5;
    public static final int COLOR_GALACTIC = 6;
    public static final int COLOR_ACID_RAIN = 7;
    public static final int COLOR_CUSTOM_GRADIENT = 8;

    // ===== BACKGROUND TYPES =====
    public static final int BG_NONE = 0;
    public static final int BG_IMAGE = 1;
    public static final int BG_GRADIENT = 2;
    public static final int BG_PARTICLES = 3;
    public static final int BG_STARS = 4;
    public static final int BG_VORTEX = 5;

    // ===== CONFIGURATION =====
    private int visualStyle = STYLE_PULSING_BARS;
    private int colorScheme = COLOR_COSMIC_AURORA;
    private int backgroundType = BG_GRADIENT;
    private int bandsCount = 128;
    private int frameRate = 60;
    private float sensitivity = 2.0f;
    private float smoothness = 0.7f;
    private float reactivity = 0.8f;
    private float glowIntensity = 0.6f;
    private float particleDensity = 0.5f;
    private boolean mirrorEffect = true;
    private boolean showPeaks = true;
    private boolean showGlow = true;
    private boolean autoRotateColors = false;

    // ===== BACKGROUND IMAGE SETTINGS =====
    private Bitmap backgroundImage = null;
    private Paint backgroundImagePaint;
    private boolean stretchBackground = true;
    private float backgroundAlpha = 0.5f;

    // ===== INTERNAL STATE =====
    private float[] amplitudes;
    private float[] smoothedAmplitudes;
    private float[] targetAmplitudes;
    private float[] peakValues;
    private float[] frequencyResponse;
    private float[] phaseOffsets;

    // ===== VISUAL ELEMENTS =====
    private Particle[] particles;
    private Star[] stars;
    private float rotationAngle = 0f;
    private float colorRotation = 0f;
    private float time = 0f;
    private int frameCount = 0;

    // ===== DRAWING OBJECTS =====
    private Paint primaryPaint;
    private Paint secondaryPaint;
    private Paint glowPaint;
    private Paint particlePaint;
    private Paint starPaint;
    private Paint bgPaint;
    private Paint maskPaint;
    private Path wavePath;
    private Path mirrorPath;

    // ===== DIMENSIONS =====
    private int width = 0;
    private int height = 0;
    private int centerX = 0;
    private int centerY = 0;
    private float maxRadius = 0;

    // ===== ANIMATION =====
    private Handler animHandler;
    private boolean isAnimating = false;
    private long lastFrameTime = 0;
    private Random random = new Random();

    // ===== COLOR PALETTES =====
    private int[] currentColors;
    private final int[][] colorPalettes = {
        // Cosmic Aurora
        {0xFF00FFFF, 0xFF0080FF, 0xFF8000FF, 0xFFFF00FF},
        // Ocean Depth
        {0xFF00F5FF, 0xFF0099CC, 0xFF0066CC, 0xFF003366},
        // Fire Storm
        {0xFFFFFF00, 0xFFFF8000, 0xFFFF0000, 0xFF800000},
        // Nebula
        {0xFFCC00FF, 0xFF6600CC, 0xFF330099, 0xFF000066},
        // Bio Luminescent
        {0xFF00FF80, 0xFF00CC66, 0xFF009933, 0xFF006600},
        // Synthwave
        {0xFFFF00FF, 0xFF00FFFF, 0xFFFFFF00, 0xFF0000FF},
        // Galactic
        {0xFF9999FF, 0xFF6666CC, 0xFF333399, 0xFF000066},
        // Acid Rain
        {0xFF00FF00, 0xFFFFFF00, 0xFFFF0000, 0xFF00FFFF}
    };

    // ===== PARTICLE SYSTEM =====
    private class Particle {
        float x, y;
        float vx, vy;
        float size;
        float life;
        float maxLife;
        int color;
        float opacity;
    }

    private class Star {
        float x, y;
        float speed;
        float size;
        float brightness;
        float twinklePhase;
    }

    // ===== CONSTRUCTORS =====
    public SpectraViz(Context context) {
        super(context);
        init(null);
    }

    public SpectraViz(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public SpectraViz(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        // Enable hardware acceleration
        setLayerType(LAYER_TYPE_HARDWARE, null);

        // Parse attributes
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.SpectraViz);
            try {
                visualStyle = a.getInt(R.styleable.SpectraViz_visualStyle, STYLE_PULSING_BARS);
                colorScheme = a.getInt(R.styleable.SpectraViz_colorScheme, COLOR_COSMIC_AURORA);
                backgroundType = a.getInt(R.styleable.SpectraViz_backgroundType, BG_GRADIENT);
                bandsCount = a.getInt(R.styleable.SpectraViz_bandsCount, 128);
                sensitivity = a.getFloat(R.styleable.SpectraViz_sensitivity, 2.0f);
                smoothness = a.getFloat(R.styleable.SpectraViz_smoothness, 0.7f);
                reactivity = a.getFloat(R.styleable.SpectraViz_reactivity, 0.8f);
                glowIntensity = a.getFloat(R.styleable.SpectraViz_glowIntensity, 0.6f);
                mirrorEffect = a.getBoolean(R.styleable.SpectraViz_mirrorEffect, true);
                showPeaks = a.getBoolean(R.styleable.SpectraViz_showPeaks, true);
                showGlow = a.getBoolean(R.styleable.SpectraViz_showGlow, true);
                autoRotateColors = a.getBoolean(R.styleable.SpectraViz_autoRotateColors, false);

                // Parse custom colors
                int colorsRes = a.getResourceId(R.styleable.SpectraViz_customColors, 0);
                if (colorsRes != 0) {
                    int[] customColors = getResources().getIntArray(colorsRes);
                    if (customColors != null && customColors.length >= 2) {
                        colorPalettes[COLOR_CUSTOM_GRADIENT] = customColors;
                    }
                }
            } finally {
                a.recycle();
            }
        }

        // Initialize drawing objects
        primaryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        primaryPaint.setStyle(Paint.Style.FILL);
        primaryPaint.setStrokeCap(Paint.Cap.ROUND);

        secondaryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        secondaryPaint.setStyle(Paint.Style.STROKE);
        secondaryPaint.setStrokeWidth(2);

        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setAlpha(100);

        particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        particlePaint.setStyle(Paint.Style.FILL);

        starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        starPaint.setStyle(Paint.Style.FILL);
        starPaint.setColor(Color.WHITE);

        bgPaint = new Paint();
        bgPaint.setStyle(Paint.Style.FILL);

        // Initialize background image paint
        backgroundImagePaint = new Paint();
        backgroundImagePaint.setAlpha((int)(backgroundAlpha * 255));

        maskPaint = new Paint();
        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));

        wavePath = new Path();
        mirrorPath = new Path();

        // Initialize arrays
        bandsCount = Math.max(32, Math.min(512, bandsCount));
        amplitudes = new float[bandsCount];
        smoothedAmplitudes = new float[bandsCount];
        targetAmplitudes = new float[bandsCount];
        peakValues = new float[bandsCount];
        frequencyResponse = new float[bandsCount];
        phaseOffsets = new float[bandsCount];

        // Initialize phase offsets for organic movement
        for (int i = 0; i < bandsCount; i++) {
            phaseOffsets[i] = (float) (Math.random() * Math.PI * 2);
            frequencyResponse[i] = (float) Math.pow((float) i / bandsCount, 0.5);
        }

        // Initialize animation handler
        animHandler = new Handler(Looper.getMainLooper());

        // Set initial colors
        updateColors();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        width = w;
        height = h;
        centerX = w / 2;
        centerY = h / 2;
        maxRadius = Math.min(w, h) * 0.45f;

        updateColors();
        initializeParticles();
        initializeStars();
    }

    private void initializeParticles() {
        int particleCount = (int) (500 * particleDensity);
        particles = new Particle[particleCount];

        for (int i = 0; i < particleCount; i++) {
            Particle p = new Particle();
            p.x = random.nextFloat() * width;
            p.y = random.nextFloat() * height;
            p.vx = (random.nextFloat() - 0.5f) * 2;
            p.vy = (random.nextFloat() - 0.5f) * 2;
            p.size = random.nextFloat() * 3 + 1;
            p.life = random.nextFloat();
            p.maxLife = random.nextFloat() * 100 + 50;
            p.color = currentColors[random.nextInt(currentColors.length)];
            p.opacity = 0.3f;
            particles[i] = p;
        }
    }

    private void initializeStars() {
        int starCount = 150;
        stars = new Star[starCount];

        for (int i = 0; i < starCount; i++) {
            Star s = new Star();
            s.x = random.nextFloat() * width;
            s.y = random.nextFloat() * height;
            s.speed = random.nextFloat() * 0.5f + 0.1f;
            s.size = random.nextFloat() * 2 + 0.5f;
            s.brightness = random.nextFloat() * 0.5f + 0.5f;
            s.twinklePhase = random.nextFloat() * (float) Math.PI * 2;
            stars[i] = s;
        }
    }

    private void updateColors() {
        if (colorScheme >= 0 && colorScheme < colorPalettes.length) {
            currentColors = colorPalettes[colorScheme];
        } else {
            currentColors = colorPalettes[COLOR_COSMIC_AURORA];
        }

        // Create gradient shader
        if (currentColors.length >= 2) {
            LinearGradient gradient = new LinearGradient(
                0, height, 0, 0,
                currentColors, null, Shader.TileMode.CLAMP
            );
            primaryPaint.setShader(gradient);
            glowPaint.setShader(gradient);
        }

        // Update background gradient
        int[] bgColors = getBackgroundColors();
        if (bgColors.length >= 2) {
            LinearGradient bgGradient = new LinearGradient(
                0, 0, width, height,
                bgColors, null, Shader.TileMode.CLAMP
            );
            bgPaint.setShader(bgGradient);
        }
    }

    private int[] getBackgroundColors() {
        switch (colorScheme) {
            case COLOR_COSMIC_AURORA:
                return new int[]{0xFF000022, 0xFF000044, 0xFF000066};
            case COLOR_OCEAN_DEPTH:
                return new int[]{0xFF001133, 0xFF002255, 0xFF003366};
            case COLOR_FIRE_STORM:
                return new int[]{0xFF220000, 0xFF440000, 0xFF660000};
            case COLOR_NEBULA:
                return new int[]{0xFF110022, 0xFF220044, 0xFF330066};
            case COLOR_BIO_LUMINESCENT:
                return new int[]{0xFF002200, 0xFF004400, 0xFF006600};
            case COLOR_SYNTHWAVE:
                return new int[]{0xFF000033, 0xFF330066, 0xFF660099};
            case COLOR_GALACTIC:
                return new int[]{0xFF000011, 0xFF000033, 0xFF000055};
            case COLOR_ACID_RAIN:
                return new int[]{0xFF003300, 0xFF006600, 0xFF009900};
            default:
                return new int[]{0xFF000011, 0xFF000033, 0xFF000055};
        }
    }

    /**
     * Update the visualizer with new audio data
     * @param bands Normalized frequency bands (0-1)
     */
    public void updateSpectrum(float[] bands) {
        if (bands == null || bands.length == 0) {
            handleSilence();
            return;
        }

        int bandCount = Math.min(bands.length, bandsCount);

        for (int i = 0; i < bandCount; i++) {
            float amplitude = bands[i] * sensitivity;

            // Apply frequency response curve
            amplitude *= frequencyResponse[i];

            // Add some organic movement with phase offsets
            float phaseMod = (float) Math.sin(time * 2 + phaseOffsets[i]) * 0.1f;
            amplitude += phaseMod;

            amplitude = Math.max(0, Math.min(1, amplitude));

            // Smooth transition to target
            targetAmplitudes[i] += (amplitude - targetAmplitudes[i]) * reactivity;

            // Update peaks
            if (targetAmplitudes[i] > peakValues[i]) {
                peakValues[i] = targetAmplitudes[i];
            }
        }

        // Fill remaining bands
        if (bandCount < bandsCount) {
            for (int i = bandCount; i < bandsCount; i++) {
                targetAmplitudes[i] *= 0.8f;
            }
        }

        startAnimation();
    }

    private void handleSilence() {
        // Create interesting idle animation
        for (int i = 0; i < bandsCount; i++) {
            float idleWave = (float) Math.sin(time * 0.5 + i * 0.1) * 0.1f + 0.05f;
            targetAmplitudes[i] = idleWave;
        }
        startAnimation();
    }

    private void startAnimation() {
        if (!isAnimating) {
            isAnimating = true;
            lastFrameTime = System.currentTimeMillis();
            animHandler.post(animationRunnable);
        }
    }

    private void stopAnimation() {
        isAnimating = false;
        animHandler.removeCallbacks(animationRunnable);
    }

    private final Runnable animationRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isAnimating) return;

            long currentTime = System.currentTimeMillis();
            float deltaTime = (currentTime - lastFrameTime) / 1000f;
            lastFrameTime = currentTime;

            // Update time
            time += deltaTime;
            frameCount++;

            // Update color rotation
            if (autoRotateColors) {
                colorRotation += deltaTime * 0.5f;
                if (colorRotation > 1f) colorRotation -= 1f;
                updateColors();
            }

            // Update particles
            updateParticles(deltaTime);

            // Update stars
            updateStars(deltaTime);

            // Update amplitudes with smooth interpolation
            updateAmplitudes(deltaTime);

            // Invalidate to trigger redraw
            invalidate();

            // Schedule next frame
            if (isAnimating) {
                animHandler.postDelayed(this, 1000 / frameRate);
            }
        }
    };

    private void updateParticles(float deltaTime) {
        if (particles == null) return;

        for (Particle p : particles) {
            p.x += p.vx * deltaTime * 30;
            p.y += p.vy * deltaTime * 30;
            p.life += deltaTime * 30;

            // Wrap around screen
            if (p.x < 0) p.x = width;
            if (p.x > width) p.x = 0;
            if (p.y < 0) p.y = height;
            if (p.y > height) p.y = 0;

            // Reset particle if life expired
            if (p.life > p.maxLife) {
                p.x = random.nextFloat() * width;
                p.y = random.nextFloat() * height;
                p.life = 0;
                p.maxLife = random.nextFloat() * 100 + 50;
            }
        }
    }

    private void updateStars(float deltaTime) {
        if (stars == null) return;

        for (Star s : stars) {
            s.y += s.speed * deltaTime * 30;
            s.twinklePhase += deltaTime * 2;

            // Wrap around screen
            if (s.y > height) {
                s.y = 0;
                s.x = random.nextFloat() * width;
            }

            // Twinkle effect
            s.brightness = 0.5f + 0.5f * (float) Math.sin(s.twinklePhase);
        }
    }

    private void updateAmplitudes(float deltaTime) {
        for (int i = 0; i < bandsCount; i++) {
            // Smooth interpolation
            smoothedAmplitudes[i] += (targetAmplitudes[i] - smoothedAmplitudes[i]) * smoothness;

            // Apply attack/decay for peaks
            peakValues[i] -= deltaTime * 2;
            if (peakValues[i] < smoothedAmplitudes[i]) {
                peakValues[i] = smoothedAmplitudes[i];
            }

            // Combine with peak for final amplitude
            amplitudes[i] = Math.max(smoothedAmplitudes[i], peakValues[i] * 0.7f);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (width == 0 || height == 0) return;

        // Draw background
        drawBackground(canvas);

        // Draw visual effect based on style
        switch (visualStyle) {
            case STYLE_FLUID_WAVE:
                drawFluidWave(canvas);
                break;
            case STYLE_COSMIC_CIRCLE:
                drawCosmicCircle(canvas);
                break;
            case STYLE_NEBULA_SPHERE:
                drawNebulaSphere(canvas);
                break;
            case STYLE_NEURAL_NETWORK:
                drawNeuralNetwork(canvas);
                break;
            case STYLE_GEOMETRIC_FLOW:
                drawGeometricFlow(canvas);
                break;
            case STYLE_HOLOGRAM:
                drawHologram(canvas);
                break;
            case STYLE_CRYSTAL_GRID:
                drawCrystalGrid(canvas);
                break;
            default: // STYLE_PULSING_BARS
                drawPulsingBars(canvas);
                break;
        }

        // Draw particles on top
        if (backgroundType == BG_PARTICLES || backgroundType == BG_VORTEX || backgroundType == BG_IMAGE) {
            drawParticles(canvas);
        }

        // Draw stars
        if (backgroundType == BG_STARS || backgroundType == BG_VORTEX || backgroundType == BG_IMAGE) {
            drawStars(canvas);
        }
    }

    private void drawBackground(Canvas canvas) {
        switch (backgroundType) {
            case BG_IMAGE:
                if (backgroundImage != null) {
                    // Draw background image
                    if (stretchBackground) {
                        // Stretch to fill entire view
                        RectF destRect = new RectF(0, 0, width, height);
                        canvas.drawBitmap(backgroundImage, null, destRect, backgroundImagePaint);
                    } else {
                        // Center the image
                        int imgWidth = backgroundImage.getWidth();
                        int imgHeight = backgroundImage.getHeight();

                        // Calculate scale to fit while maintaining aspect ratio
                        float scale = Math.min((float)width / imgWidth, (float)height / imgHeight);
                        int scaledWidth = (int)(imgWidth * scale);
                        int scaledHeight = (int)(imgHeight * scale);

                        // Calculate position to center
                        int left = (width - scaledWidth) / 2;
                        int top = (height - scaledHeight) / 2;

                        RectF destRect = new RectF(left, top, left + scaledWidth, top + scaledHeight);
                        canvas.drawBitmap(backgroundImage, null, destRect, backgroundImagePaint);
                    }
                } else {
                    // Fallback to gradient if no image
                    canvas.drawRect(0, 0, width, height, bgPaint);
                }
                break;

            case BG_GRADIENT:
                canvas.drawRect(0, 0, width, height, bgPaint);
                break;

            case BG_PARTICLES:
                // Draw black background for particles
                canvas.drawColor(Color.BLACK);
                break;

            case BG_STARS:
                canvas.drawColor(Color.BLACK);
                break;

            case BG_VORTEX:
                // Draw radial vortex effect
                RadialGradient vortexGradient = new RadialGradient(
                    centerX, centerY, maxRadius * 1.5f,
                    Color.TRANSPARENT, 0x80000000,
                    Shader.TileMode.CLAMP
                );
                bgPaint.setShader(vortexGradient);
                canvas.drawCircle(centerX, centerY, maxRadius * 1.5f, bgPaint);
                bgPaint.setShader(null);
                break;

            default: // BG_NONE
                canvas.drawColor(Color.TRANSPARENT);
                break;
        }
    }

    private void drawPulsingBars(Canvas canvas) {
        float barWidth = (float) width / bandsCount * 0.9f;
        float spacing = (float) width / bandsCount * 0.1f;

        for (int i = 0; i < bandsCount; i++) {
            float amplitude = amplitudes[i];
            if (amplitude < 0.01f) continue;

            // Calculate bar dimensions with pulsing effect
            float barHeight = amplitude * height * 0.8f;
            float pulse = (float) Math.sin(time * 3 + i * 0.1f) * 0.1f + 0.9f;
            barHeight *= pulse;

            float left = i * (barWidth + spacing);
            float top = height - barHeight;
            float right = left + barWidth;
            float bottom = height;

            // Draw bar with gradient based on height
            int[] barColors = getBarColors(amplitude);
            LinearGradient barGradient = new LinearGradient(
                left, top, left, bottom,
                barColors, null, Shader.TileMode.CLAMP
            );
            primaryPaint.setShader(barGradient);

            // Draw rounded bar
            float radius = barWidth * 0.3f;
            canvas.drawRoundRect(left, top, right, bottom, radius, radius, primaryPaint);

            // Draw peak indicator
            if (showPeaks && peakValues[i] > amplitude * 0.9f) {
                float peakY = height - (peakValues[i] * height * 0.8f);
                secondaryPaint.setColor(Color.WHITE);
                canvas.drawLine(left, peakY, right, peakY, secondaryPaint);
            }
        }
    }

    private void drawFluidWave(Canvas canvas) {
        wavePath.reset();

        float xStep = (float) width / (bandsCount - 1);
        wavePath.moveTo(0, height);

        // Create fluid wave
        for (int i = 0; i < bandsCount; i++) {
            float amplitude = amplitudes[i];
            float x = i * xStep;
            float y = height - (amplitude * height * 0.6f);

            // Add fluid motion
            float fluidY = y + (float) Math.sin(time * 2 + x * 0.01f) * 10;

            if (i == 0) {
                wavePath.moveTo(x, fluidY);
            } else {
                // Create smooth Bézier curve
                float prevX = (i - 1) * xStep;
                float prevY = height - (amplitudes[i - 1] * height * 0.6f);
                prevY += (float) Math.sin(time * 2 + prevX * 0.01f) * 10;

                float ctrlX1 = prevX + (x - prevX) * 0.3f;
                float ctrlY1 = prevY;
                float ctrlX2 = prevX + (x - prevX) * 0.7f;
                float ctrlY2 = fluidY;

                wavePath.cubicTo(ctrlX1, ctrlY1, ctrlX2, ctrlY2, x, fluidY);
            }
        }

        // Complete the wave for filling
        wavePath.lineTo(width, height);
        wavePath.lineTo(0, height);
        wavePath.close();

        // Draw wave with glow
        if (showGlow) {
            glowPaint.setAlpha((int) (glowIntensity * 150));
            canvas.drawPath(wavePath, glowPaint);
        }

        // Draw wave outline
        primaryPaint.setStyle(Paint.Style.FILL);
        canvas.drawPath(wavePath, primaryPaint);

        // Draw mirrored wave
        if (mirrorEffect) {
            canvas.save();
            canvas.scale(1, -1, centerX, centerY);
            primaryPaint.setAlpha(100);
            canvas.drawPath(wavePath, primaryPaint);
            primaryPaint.setAlpha(255);
            canvas.restore();
        }
    }

    private void drawCosmicCircle(Canvas canvas) {
        float radius = maxRadius;
        float angleStep = (float) (Math.PI * 2 / bandsCount);

        canvas.save();
        canvas.rotate(rotationAngle, centerX, centerY);
        rotationAngle += 0.5f;

        for (int i = 0; i < bandsCount; i++) {
            float amplitude = amplitudes[i];
            if (amplitude < 0.01f) continue;

            float angle = i * angleStep;
            float spikeLength = amplitude * radius * 0.8f;
            float innerRadius = radius * 0.3f;

            float x1 = centerX + (float) Math.cos(angle) * innerRadius;
            float y1 = centerY + (float) Math.sin(angle) * innerRadius;
            float x2 = centerX + (float) Math.cos(angle) * (innerRadius + spikeLength);
            float y2 = centerY + (float) Math.sin(angle) * (innerRadius + spikeLength);

            // Draw spike with gradient
            secondaryPaint.setStrokeWidth(amplitude * 8 + 2);
            canvas.drawLine(x1, y1, x2, y2, secondaryPaint);

            // Draw orb at end
            float orbRadius = amplitude * 15 + 3;
            canvas.drawCircle(x2, y2, orbRadius, primaryPaint);

            // Draw connecting lines
            if (i > 0) {
                float prevAngle = (i - 1) * angleStep;
                float prevX2 = centerX + (float) Math.cos(prevAngle) * (innerRadius + amplitudes[i - 1] * radius * 0.8f);
                float prevY2 = centerY + (float) Math.sin(prevAngle) * (innerRadius + amplitudes[i - 1] * radius * 0.8f);

                secondaryPaint.setStrokeWidth(1);
                secondaryPaint.setAlpha(100);
                canvas.drawLine(prevX2, prevY2, x2, y2, secondaryPaint);
                secondaryPaint.setAlpha(255);
            }
        }

        // Draw center circle
        canvas.drawCircle(centerX, centerY, radius * 0.2f, primaryPaint);

        canvas.restore();
    }

    private void drawNebulaSphere(Canvas canvas) {
        float radius = maxRadius * 0.8f;

        // Draw glow behind sphere
        if (showGlow) {
            int glowColor = Color.argb((int)(glowIntensity * 200), 255, 255, 255);
            RadialGradient glowGradient = new RadialGradient(
                centerX, centerY, radius * 1.5f,
                glowColor, Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            );
            glowPaint.setShader(glowGradient);
            canvas.drawCircle(centerX, centerY, radius * 1.5f, glowPaint);
        }

        // Draw sphere with radial gradient
        if (currentColors.length >= 2) {
            // Create positions for the colors (equally spaced)
            float[] stops = new float[currentColors.length];
            for (int i = 0; i < currentColors.length; i++) {
                stops[i] = i / (float) (currentColors.length - 1);
            }

            RadialGradient sphereGradient = new RadialGradient(
                centerX, centerY, radius,
                currentColors, stops,
                Shader.TileMode.CLAMP
            );
            primaryPaint.setShader(sphereGradient);
        }
        canvas.drawCircle(centerX, centerY, radius, primaryPaint);

        // Draw amplitude rings
        float ringStep = radius / 10;
        for (int ring = 1; ring <= 5; ring++) {
            float ringRadius = ringStep * ring * 2;
            float ringAmplitude = amplitudes[ring * 10 % bandsCount];

            secondaryPaint.setStrokeWidth(ringAmplitude * 3 + 1);
            secondaryPaint.setColor(Color.argb(150, 255, 255, 255));
            canvas.drawCircle(centerX, centerY, ringRadius, secondaryPaint);
        }

        // Draw orbiting particles
        int particleCount = 20;
        for (int i = 0; i < particleCount; i++) {
            float orbitRadius = radius * 1.5f;
            float orbitSpeed = 0.5f + amplitudes[i % bandsCount];
            float angle = time * orbitSpeed + i * (float)(Math.PI * 2 / particleCount);

            float x = centerX + (float) Math.cos(angle) * orbitRadius;
            float y = centerY + (float) Math.sin(angle) * orbitRadius;
            float particleSize = amplitudes[i % bandsCount] * 8 + 2;

            canvas.drawCircle(x, y, particleSize, primaryPaint);
        }
    }

    private void drawNeuralNetwork(Canvas canvas) {
        int nodes = bandsCount / 4;
        float[][] nodePositions = new float[nodes][2];
        float[] nodeEnergies = new float[nodes];

        // Calculate node positions in a sphere
        for (int i = 0; i < nodes; i++) {
            float phi = (float) (Math.acos(1 - 2 * (float) i / nodes));
            float theta = (float) (Math.PI * (1 + Math.sqrt(5)) * i);

            nodePositions[i][0] = centerX + (float) (Math.sin(phi) * Math.cos(theta)) * maxRadius;
            nodePositions[i][1] = centerY + (float) (Math.sin(phi) * Math.sin(theta)) * maxRadius;
            nodeEnergies[i] = amplitudes[i % (bandsCount / 4)] * 0.8f + amplitudes[(i * 3) % bandsCount] * 0.2f;
        }

        // Draw connections
        secondaryPaint.setStrokeWidth(1);
        for (int i = 0; i < nodes; i++) {
            for (int j = i + 1; j < Math.min(i + 5, nodes); j++) {
                float distance = (float) Math.sqrt(
                    Math.pow(nodePositions[i][0] - nodePositions[j][0], 2) +
                    Math.pow(nodePositions[i][1] - nodePositions[j][1], 2)
                );

                if (distance < maxRadius * 1.5f) {
                    float connectionStrength = (nodeEnergies[i] + nodeEnergies[j]) * 0.5f;
                    secondaryPaint.setAlpha((int) (connectionStrength * 150));
                    canvas.drawLine(
                        nodePositions[i][0], nodePositions[i][1],
                        nodePositions[j][0], nodePositions[j][1],
                        secondaryPaint
                    );
                }
            }
        }

        secondaryPaint.setAlpha(255);

        // Draw nodes
        for (int i = 0; i < nodes; i++) {
            float nodeSize = nodeEnergies[i] * 15 + 5;
            float pulse = (float) Math.sin(time * 3 + i * 0.5f) * 0.2f + 0.8f;
            nodeSize *= pulse;

            canvas.drawCircle(nodePositions[i][0], nodePositions[i][1], nodeSize, primaryPaint);

            // Draw inner glow
            if (nodeEnergies[i] > 0.3f) {
                glowPaint.setAlpha((int) (nodeEnergies[i] * 100));
                canvas.drawCircle(nodePositions[i][0], nodePositions[i][1], nodeSize * 1.5f, glowPaint);
            }
        }
    }

    private void drawGeometricFlow(Canvas canvas) {
        int shapes = bandsCount / 8;
        float shapeSize = maxRadius * 0.8f / shapes;

        canvas.save();
        canvas.rotate(rotationAngle * 0.5f, centerX, centerY);

        for (int i = 0; i < shapes; i++) {
            float amplitude = amplitudes[i * 8 % bandsCount];
            if (amplitude < 0.01f) continue;

            float radius = (i + 1) * shapeSize;
            float rotation = time * (i % 3 + 1) * 0.5f;
            int sides = 3 + i % 5; // 3 to 7 sides

            canvas.save();
            canvas.rotate(rotation, centerX, centerY);

            // Calculate polygon vertices
            Path polygon = new Path();
            for (int j = 0; j < sides; j++) {
                float angle = (float) (j * 2 * Math.PI / sides);
                float x = centerX + (float) Math.cos(angle) * radius * (1 + amplitude * 0.5f);
                float y = centerY + (float) Math.sin(angle) * radius * (1 + amplitude * 0.5f);

                if (j == 0) {
                    polygon.moveTo(x, y);
                } else {
                    polygon.lineTo(x, y);
                }
            }
            polygon.close();

            // Draw polygon with gradient - FIXED RadialGradient constructor
            int[] polyColors = getBarColors(amplitude);
            if (polyColors.length >= 2) {
                float[] stops = new float[polyColors.length];
                for (int j = 0; j < polyColors.length; j++) {
                    stops[j] = j / (float) (polyColors.length - 1);
                }

                RadialGradient polyGradient = new RadialGradient(
                    centerX, centerY, radius,
                    polyColors, stops,
                    Shader.TileMode.CLAMP
                );
                primaryPaint.setShader(polyGradient);
            }
            primaryPaint.setAlpha(150);
            canvas.drawPath(polygon, primaryPaint);
            primaryPaint.setAlpha(255);

            // Draw outline
            secondaryPaint.setStrokeWidth(amplitude * 3 + 1);
            secondaryPaint.setColor(Color.WHITE);
            canvas.drawPath(polygon, secondaryPaint);

            canvas.restore();
        }

        canvas.restore();
    }

    private void drawHologram(Canvas canvas) {
        // Draw scan lines
        int scanLines = 50;
        float scanHeight = height / (float) scanLines;

        for (int i = 0; i < scanLines; i++) {
            float y = i * scanHeight;
            float scanAmplitude = amplitudes[i * bandsCount / scanLines % bandsCount];

            // Draw scan line with varying intensity
            primaryPaint.setAlpha((int) (scanAmplitude * 100 + 50));
            canvas.drawLine(0, y, width, y, primaryPaint);

            // Draw data points on scan line
            int points = 20;
            for (int j = 0; j < points; j++) {
                float x = width * j / (float) points;
                float pointAmplitude = amplitudes[(i * points + j) % bandsCount];

                if (pointAmplitude > 0.1f) {
                    float pointSize = pointAmplitude * 10 + 2;
                    canvas.drawCircle(x, y, pointSize, secondaryPaint);
                }
            }
        }

        // Draw central data display
        float displayWidth = width * 0.8f;
        float displayHeight = height * 0.3f;
        float displayLeft = (width - displayWidth) / 2;
        float displayTop = (height - displayHeight) / 2;

        // Draw display background
        primaryPaint.setAlpha(50);
        canvas.drawRect(displayLeft, displayTop, displayLeft + displayWidth, displayTop + displayHeight, primaryPaint);
        primaryPaint.setAlpha(255);

        // Draw waveform in display
        Path displayWave = new Path();
        displayWave.moveTo(displayLeft, displayTop + displayHeight / 2);

        for (int i = 0; i < bandsCount; i++) {
            float x = displayLeft + (i * displayWidth / bandsCount);
            float amplitude = amplitudes[i];
            float y = displayTop + displayHeight / 2 - amplitude * displayHeight / 2;

            if (i == 0) {
                displayWave.moveTo(x, y);
            } else {
                displayWave.lineTo(x, y);
            }
        }

        secondaryPaint.setStrokeWidth(3);
        secondaryPaint.setColor(Color.CYAN);
        canvas.drawPath(displayWave, secondaryPaint);

        // Draw grid lines
        secondaryPaint.setStrokeWidth(1);
        secondaryPaint.setColor(Color.argb(100, 0, 255, 255));
        for (int i = 1; i < 5; i++) {
            float gridY = displayTop + (displayHeight * i / 5);
            canvas.drawLine(displayLeft, gridY, displayLeft + displayWidth, gridY, secondaryPaint);
        }
    }

    private void drawCrystalGrid(Canvas canvas) {
        int gridSize = 15;
        float cellSize = Math.min(width, height) / (float) gridSize;

        for (int x = 0; x < gridSize; x++) {
            for (int y = 0; y < gridSize; y++) {
                int index = (x * gridSize + y) % bandsCount;
                float amplitude = amplitudes[index];

                if (amplitude < 0.05f) continue;

                float crystalCenterX = (x + 0.5f) * cellSize;
                float crystalCenterY = (y + 0.5f) * cellSize;

                // Create crystal shape (diamond)
                Path crystal = new Path();
                crystal.moveTo(crystalCenterX, crystalCenterY - cellSize * 0.4f * amplitude);
                crystal.lineTo(crystalCenterX + cellSize * 0.4f * amplitude, crystalCenterY);
                crystal.lineTo(crystalCenterX, crystalCenterY + cellSize * 0.4f * amplitude);
                crystal.lineTo(crystalCenterX - cellSize * 0.4f * amplitude, crystalCenterY);
                crystal.close();

                // Draw crystal with gradient - FIXED RadialGradient constructor
                int[] crystalColors = getBarColors(amplitude);
                if (crystalColors.length >= 2) {
                    float[] stops = new float[crystalColors.length];
                    for (int j = 0; j < crystalColors.length; j++) {
                        stops[j] = j / (float) (crystalColors.length - 1);
                    }

                    RadialGradient crystalGradient = new RadialGradient(
                        crystalCenterX, crystalCenterY, cellSize * 0.4f * amplitude,
                        crystalColors, stops,
                        Shader.TileMode.CLAMP
                    );
                    primaryPaint.setShader(crystalGradient);
                }
                canvas.drawPath(crystal, primaryPaint);

                // Draw connections to neighbors
                if (x < gridSize - 1 && y < gridSize - 1) {
                    int rightIndex = ((x + 1) * gridSize + y) % bandsCount;
                    int downIndex = (x * gridSize + (y + 1)) % bandsCount;

                    float rightAmplitude = amplitudes[rightIndex];
                    float downAmplitude = amplitudes[downIndex];

                    if (rightAmplitude > 0.05f) {
                        secondaryPaint.setStrokeWidth(amplitude * 3);
                        secondaryPaint.setAlpha(100);
                        canvas.drawLine(
                            crystalCenterX, crystalCenterY,
                            crystalCenterX + cellSize, crystalCenterY,
                            secondaryPaint
                        );
                    }

                    if (downAmplitude > 0.05f) {
                        secondaryPaint.setStrokeWidth(amplitude * 3);
                        canvas.drawLine(
                            crystalCenterX, crystalCenterY,
                            crystalCenterX, crystalCenterY + cellSize,
                            secondaryPaint
                        );
                    }
                    secondaryPaint.setAlpha(255);
                }
            }
        }
    }

    private void drawParticles(Canvas canvas) {
        if (particles == null) return;

        for (Particle p : particles) {
            particlePaint.setColor(p.color);
            particlePaint.setAlpha((int) (p.opacity * 255 * (1 - p.life / p.maxLife)));
            canvas.drawCircle(p.x, p.y, p.size, particlePaint);
        }
    }

    private void drawStars(Canvas canvas) {
        if (stars == null) return;

        for (Star s : stars) {
            starPaint.setAlpha((int) (s.brightness * 255));
            canvas.drawCircle(s.x, s.y, s.size, starPaint);
        }
    }

    private int[] getBarColors(float amplitude) {
        // Simple gradient from first to second color based on amplitude
        if (currentColors.length >= 2) {
            int color1 = currentColors[0];
            int color2 = currentColors[1 % currentColors.length];

            // Interpolate between colors based on amplitude
            int red = (int) (Color.red(color1) * (1 - amplitude) + Color.red(color2) * amplitude);
            int green = (int) (Color.green(color1) * (1 - amplitude) + Color.green(color2) * amplitude);
            int blue = (int) (Color.blue(color1) * (1 - amplitude) + Color.blue(color2) * amplitude);

            int midColor = Color.argb(255, red, green, blue);

            return new int[]{
                Color.argb(255, Color.red(color1), Color.green(color1), Color.blue(color1)),
                midColor,
                Color.argb(150, Color.red(color2), Color.green(color2), Color.blue(color2))
            };
        }

        // Fallback
        return new int[]{Color.CYAN, Color.BLUE};
    }

    // ===== PUBLIC API =====

    public int getVisualStyle() {
        return visualStyle;
    }

    public void setVisualStyle(int style) {
        this.visualStyle = Math.max(STYLE_PULSING_BARS, Math.min(STYLE_CRYSTAL_GRID, style));
        invalidate();
    }

    public int getColorScheme() {
        return colorScheme;
    }

    public void setColorScheme(int scheme) {
        this.colorScheme = Math.max(COLOR_COSMIC_AURORA, Math.min(COLOR_CUSTOM_GRADIENT, scheme));
        updateColors();
        invalidate();
    }
	
	public void setBackgroundImage(Bitmap image){
		backgroundImage = image;
		invalidate();
	}
	
	public Bitmap getBackgroundImage(){
		return backgroundImage;
	}

    public int getBackgroundType() {
        return backgroundType;
    }

    public void setBackgroundType(int type) {
        this.backgroundType = Math.max(BG_NONE, Math.min(BG_VORTEX, type));
        invalidate();
    }

    public int getBandsCount() {
        return bandsCount;
    }

    public void setBandsCount(int count) {
        count = Math.max(32, Math.min(512, count));
        if (count != bandsCount) {
            bandsCount = count;
            amplitudes = new float[bandsCount];
            smoothedAmplitudes = new float[bandsCount];
            targetAmplitudes = new float[bandsCount];
            peakValues = new float[bandsCount];
            frequencyResponse = new float[bandsCount];
            phaseOffsets = new float[bandsCount];

            for (int i = 0; i < bandsCount; i++) {
                phaseOffsets[i] = (float) (Math.random() * Math.PI * 2);
                frequencyResponse[i] = (float) Math.pow((float) i / bandsCount, 0.5);
            }
            invalidate();
        }
    }

    public void setSensitivity(float sensitivity) {
        this.sensitivity = Math.max(0.1f, Math.min(5.0f, sensitivity));
    }

    public void setSmoothness(float smoothness) {
        this.smoothness = Math.max(0.1f, Math.min(0.95f, smoothness));
    }

    public void setReactivity(float reactivity) {
        this.reactivity = Math.max(0.1f, Math.min(0.95f, reactivity));
    }

    public void setGlowIntensity(float intensity) {
        this.glowIntensity = Math.max(0f, Math.min(1f, intensity));
    }

    public void setParticleDensity(float density) {
        this.particleDensity = Math.max(0f, Math.min(1f, density));
        initializeParticles();
    }

    public void setFrameRate(int fps) {
        this.frameRate = Math.max(24, Math.min(120, fps));
    }

    public void setMirrorEffect(boolean mirror) {
        this.mirrorEffect = mirror;
        invalidate();
    }

    public void setShowPeaks(boolean show) {
        this.showPeaks = show;
        invalidate();
    }

    public void setShowGlow(boolean show) {
        this.showGlow = show;
        invalidate();
    }

    public void setAutoRotateColors(boolean autoRotate) {
        this.autoRotateColors = autoRotate;
    }

    public void setCustomColors(int[] colors) {
        if (colors != null && colors.length >= 2) {
            colorPalettes[COLOR_CUSTOM_GRADIENT] = colors;
            if (colorScheme == COLOR_CUSTOM_GRADIENT) {
                updateColors();
                invalidate();
            }
        }
    }

    public void pause() {
        stopAnimation();
    }

    public void resume() {
        if (isAnimating) {
            startAnimation();
        }
    }

    public void clear() {
        Arrays.fill(targetAmplitudes, 0f);
        Arrays.fill(peakValues, 0f);
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimation();
    }
}
