package com.ikechi.studio.onwa.player.view.core.styles;

import android.opengl.GLES20;

import com.ikechi.studio.onwa.player.view.core.GLVisualizerRenderer;
import com.ikechi.studio.onwa.player.view.core.visuals.VisualStyle;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;

/**
 * Kinetic BarsStyle - Wide frequency bars with physics-based floating dots
 *
 * Features:
 * - 48 wide bars filling full screen width
 * - Bars grow from bottom in response to audio, shrink to bottom when idle
 * - Dots rest on top of bars, get kicked by rising bars, fall with gravity/delay
 * - Rich color gradients with glow effects
 */
public class BarsStyle extends VisualStyle {

    private static final String TAG = "BarsStyle";
    private static final Random RAND = new Random();

    private static final int BAR_COUNT = 48; // Fixed count, wider bars

    // Physics constants
    private static final float GRAVITY = 3.0f;
    private static final float KICK_FORCE = 10.0f;
    private static final float AIR_RESISTANCE = 0.96f;
    private static final float GROUND_BOUNCE = 0.2f;
    private static final float IDLE_FALL_SPEED = 8.0f; // Fast collapse to bottom

    // Screen coverage
    private static final float SCREEN_WIDTH = 2.0f; // -1 to 1
    private static final float GAP = 0.003f; // Tiny gap between bars
    // Bar width calculated to fill screen: (total_width / count) - gap
    private float barWidth;

    // Bar and dot physics state
    private static class BarState {
        float currentHeight;
        float targetHeight;
        float prevHeight;
        boolean isGrowing;
        float idleTimer;
    }

    private static class DotState {
        float y;
        float vy;
        float width;
        float[] color = new float[4];
        boolean wasKicked;
        float kickGlow;
    }

    private BarState[] bars;
    private DotState[] dots;

    // Buffers
    private FloatBuffer barVertexBuffer;
    private FloatBuffer barColorBuffer;
    private FloatBuffer dotVertexBuffer;
    private FloatBuffer dotColorBuffer;

    private int barVboId, barColorVboId;
    private int dotVboId, dotColorVboId;

    private boolean buffersInitialized = false;

    // State
    private float globalTime = 0f;
    private float beatAccumulator = 0f;
    private boolean isIdle = true;

    @Override
    public void init(int shader2D, int shader3D, int shader3DUniform,
                     int texturedShader2D, int texturedShader3D,
                     GLVisualizerRenderer renderer) {
        super.init(shader2D, shader3D, shader3DUniform,
                   texturedShader2D, texturedShader3D, renderer);

        // Calculate bar width to perfectly fill screen width
        // Formula: (screen_width / bar_count) - gap
        barWidth = (SCREEN_WIDTH / BAR_COUNT) - GAP;

        initPhysicsState();
        allocateBuffers();
    }

    private void initPhysicsState() {
        bars = new BarState[BAR_COUNT];
        dots = new DotState[BAR_COUNT];

        for (int i = 0; i < BAR_COUNT; i++) {
            bars[i] = new BarState();
            bars[i].currentHeight = 0.0f; // Start at bottom (idle state)
            bars[i].targetHeight = 0.0f;
            bars[i].prevHeight = 0.0f;
            bars[i].idleTimer = 1f; // Start as idle

            dots[i] = new DotState();
            dots[i].y = -1.0f; // Start at bottom
            dots[i].vy = 0f;
            dots[i].width = barWidth;
            dots[i].kickGlow = 0f;
        }
    }

    private void allocateBuffers() {
        if (buffersInitialized) {
            GLES20.glDeleteBuffers(2, new int[]{barVboId, barColorVboId}, 0);
            GLES20.glDeleteBuffers(2, new int[]{dotVboId, dotColorVboId}, 0);
        }

        int barVertices = BAR_COUNT * 6;

        // Bar buffers
        ByteBuffer vbb = ByteBuffer.allocateDirect(barVertices * 3 * 4);
        vbb.order(ByteOrder.nativeOrder());
        barVertexBuffer = vbb.asFloatBuffer();

        ByteBuffer cbb = ByteBuffer.allocateDirect(barVertices * 4 * 4);
        cbb.order(ByteOrder.nativeOrder());
        barColorBuffer = cbb.asFloatBuffer();

        // Dot buffers
        ByteBuffer dvbb = ByteBuffer.allocateDirect(barVertices * 3 * 4);
        dvbb.order(ByteOrder.nativeOrder());
        dotVertexBuffer = dvbb.asFloatBuffer();

        ByteBuffer dcbb = ByteBuffer.allocateDirect(barVertices * 4 * 4);
        dcbb.order(ByteOrder.nativeOrder());
        dotColorBuffer = dcbb.asFloatBuffer();

        int[] vbos = new int[2];

        // Bar buffers
        GLES20.glGenBuffers(2, vbos, 0);
        barVboId = vbos[0];
        barColorVboId = vbos[1];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, barVboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, barVertices * 3 * 4, null, GLES20.GL_DYNAMIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, barColorVboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, barVertices * 4 * 4, null, GLES20.GL_DYNAMIC_DRAW);

        // Dot buffers
        GLES20.glGenBuffers(2, vbos, 0);
        dotVboId = vbos[0];
        dotColorVboId = vbos[1];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, dotVboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, barVertices * 3 * 4, null, GLES20.GL_DYNAMIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, dotColorVboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, barVertices * 4 * 4, null, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        buffersInitialized = true;
    }

    @Override
    public void update(float deltaTime, float[] fftBands, float beatIntensity, float totalTime) {
        this.totalTime = totalTime;
        globalTime = totalTime;

        // Detect if we have active audio
        boolean hasAudio = (fftBands != null && fftBands.length > 0);
        float totalEnergy = 0f;

        if (hasAudio) {
            for (float v : fftBands) {
                totalEnergy += v;
            }
            totalEnergy = (totalEnergy / fftBands.length) * renderer.getSensitivity();
        }

        // IDLE DETECTION: No audio or very low energy = idle state
        // In idle: bars collapse to bottom, dots rest at bottom
        if (!hasAudio || totalEnergy < 0.03f) {
            isIdle = true;
            for (BarState bar : bars) {
                bar.idleTimer += deltaTime;
            }
        } else {
            isIdle = false;
            for (BarState bar : bars) {
                bar.idleTimer = 0f;
            }
        }

        float dt = Math.min(deltaTime, 0.05f);

        // Update beat effects
        beatAccumulator = Math.max(0f, beatAccumulator - dt * 2f);
        if (beatIntensity > 0.6f) {
            beatAccumulator = 1f;
        }

        // Update physics for each bar/dot pair
        for (int i = 0; i < BAR_COUNT; i++) {
            updateBarPhysics(i, fftBands, dt, totalEnergy);
            updateDotPhysics(i, dt, beatIntensity);
        }

        // Build geometry
        buildBarGeometry();
        buildDotGeometry();
    }

    private void updateBarPhysics(int i, float[] fftBands, float dt, float totalEnergy) {
        BarState bar = bars[i];
        float targetHeight;

        if (isIdle || totalEnergy < 0.03f) {
            // IDLE: Bars should shrink to the bottom (height = 0)
            targetHeight = 0.0f;
        } else {
            // ACTIVE: Bars grow upward from the bottom based on audio energy
            int fftIdx = (i * fftBands.length) / BAR_COUNT;
            float rawHeight = fftBands[Math.min(fftIdx, fftBands.length - 1)];

            float sensitivity = renderer.getSensitivity();
            float beatBoost = 1f + beatAccumulator * 0.5f;
            targetHeight = rawHeight * sensitivity * beatBoost * 2.0f;
            targetHeight = Math.min(targetHeight, 1.95f); // Cap near top
            targetHeight = Math.max(targetHeight, 0.0f); // Minimum height (bottom)
        }

        bar.targetHeight = targetHeight;

        // Movement speed based on state
        float speed;
        if (isIdle) {
            // Fast collapse to bottom when idle
            speed = IDLE_FALL_SPEED;
        } else {
            // Normal: snappy attack, comfortable decay
            float diff = bar.targetHeight - bar.currentHeight;
            float attackSpeed = 20f;
            float decaySpeed = 10f;
            speed = diff > 0 ? attackSpeed : decaySpeed;
        }

        bar.currentHeight = lerp(bar.currentHeight, bar.targetHeight, dt * speed);

        bar.isGrowing = bar.currentHeight > bar.prevHeight;
        bar.prevHeight = bar.currentHeight;

        // Keep dot width synced with bar
        dots[i].width = barWidth;
    }

    private void updateDotPhysics(int i, float dt, float beatIntensity) {
        BarState bar = bars[i];
        DotState dot = dots[i];

        float barTop = -1.0f + bar.currentHeight;

        // KICK DETECTION: Bar growing up and hitting the dot from below
        if (bar.isGrowing && barTop >= dot.y && bar.prevHeight <= dot.y + 0.02f) {
            // Bar kicked the dot upward!
            dot.vy = KICK_FORCE * (1f + beatIntensity * 0.5f);
            dot.wasKicked = true;
            dot.kickGlow = 1f;
        }

        // Apply physics
        dot.vy -= GRAVITY * dt; // Gravity pulls down
        dot.vy *= AIR_RESISTANCE; // Air resistance

        // Update position
        dot.y += dot.vy * dt;

        // Ground collision: dot lands on bar top (with delay/lag effect)
        if (dot.y < barTop && dot.vy < 0) {
            if (dot.vy < -2.0f) {
                // Falling fast: bounce
                dot.vy *= -GROUND_BOUNCE;
                dot.y = barTop;
            } else {
                // Falling slow: rest on bar with slight lag
                dot.y = lerp(dot.y, barTop, dt * 5f);
                dot.vy *= 0.3f;
            }
        }

        // Hard floor limit
        if (dot.y < -0.99f) {
            dot.y = -0.99f;
            dot.vy = 0f;
        }

        // Ceiling
        if (dot.y > 0.99f) {
            dot.y = 0.99f;
            dot.vy *= -0.5f;
        }

        // Decay kick glow
        dot.kickGlow = Math.max(0f, dot.kickGlow - dt * 2.5f);
        dot.wasKicked = dot.kickGlow > 0.1f;
    }

    private void buildBarGeometry() {
        barVertexBuffer.clear();
        barColorBuffer.clear();

        for (int i = 0; i < BAR_COUNT; i++) {
            BarState bar = bars[i];
            DotState dot = dots[i];

            // Calculate position: bars fill screen from -1 to 1
            float left = -1.0f + i * (barWidth + GAP);
            float right = left + barWidth;
            float bottom = -1.0f;
            float top = bottom + bar.currentHeight; // Bars grow upward from bottom

            // Dim when idle
            float dimFactor = isIdle ? 0.25f : 1.0f;

            float[] color = getBarColor(i, bar.currentHeight, bar.isGrowing, dot.kickGlow, dimFactor);

            // Triangle 1
            put3(barVertexBuffer, left, bottom, 0f);
            barColorBuffer.put(color);
            put3(barVertexBuffer, right, bottom, 0f);
            barColorBuffer.put(color);
            put3(barVertexBuffer, right, top, 0f);
            barColorBuffer.put(color);

            // Triangle 2
            put3(barVertexBuffer, left, bottom, 0f);
            barColorBuffer.put(color);
            put3(barVertexBuffer, right, top, 0f);
            barColorBuffer.put(color);
            put3(barVertexBuffer, left, top, 0f);
            barColorBuffer.put(color);
        }

        barVertexBuffer.flip();
        barColorBuffer.flip();

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, barVboId);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, barVertexBuffer.remaining() * 4, barVertexBuffer);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, barColorVboId);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, barColorBuffer.remaining() * 4, barColorBuffer);
    }

    private void buildDotGeometry() {
        dotVertexBuffer.clear();
        dotColorBuffer.clear();

        for (int i = 0; i < BAR_COUNT; i++) {
            DotState dot = dots[i];

            // Dot matches bar width exactly
            float left = -1.0f + i * (barWidth + GAP);
            float right = left + dot.width;

            // Dot height (thicker than before)
            float dotHeight = 0.05f + dot.kickGlow * 0.03f;
            float bottom = dot.y;
            float top = bottom + dotHeight;

            // Dim when idle
            float dimFactor = isIdle ? 0.3f : 1.0f;

            float[] color = getDotColor(i, dot, globalTime, dimFactor);

            // Triangle 1
            put3(dotVertexBuffer, left, bottom, 0.05f);
            dotColorBuffer.put(color);
            put3(dotVertexBuffer, right, bottom, 0.05f);
            dotColorBuffer.put(color);
            put3(dotVertexBuffer, right, top, 0.05f);
            dotColorBuffer.put(color);

            // Triangle 2
            put3(dotVertexBuffer, left, bottom, 0.05f);
            dotColorBuffer.put(color);
            put3(dotVertexBuffer, right, top, 0.05f);
            dotColorBuffer.put(color);
            put3(dotVertexBuffer, left, top, 0.05f);
            dotColorBuffer.put(color);
        }

        dotVertexBuffer.flip();
        dotColorBuffer.flip();

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, dotVboId);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, dotVertexBuffer.remaining() * 4, dotVertexBuffer);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, dotColorVboId);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, dotColorBuffer.remaining() * 4, dotColorBuffer);
    }

    private float[] getBarColor(int index, float height, boolean isGrowing, float kickGlow, float dimFactor) {
        float t = (float) index / BAR_COUNT;
        float intensity = height / 2.0f;

        // Rainbow gradient across screen
        float hue = (t * 0.9f + globalTime * 0.03f) % 1.0f;
        float sat = 0.75f + intensity * 0.25f;
        float val = (0.35f + intensity * 0.65f) * dimFactor;

        // Growing bars get brighter
        if (isGrowing && !isIdle) {
            val += 0.15f;
            sat += 0.1f;
        }

        // Kick glow adds flash
        val += kickGlow * 0.4f;

        float[] rgb = hsvToRgb(hue, Math.min(1f, sat), Math.min(1f, val));
        float alpha = (0.85f + intensity * 0.15f) * dimFactor;

        return new float[]{rgb[0], rgb[1], rgb[2], alpha};
    }

    private float[] getDotColor(int index, DotState dot, float time, float dimFactor) {
        float t = (float) index / BAR_COUNT;

        // Faster hue rotation for dots, plus height influence
        float hue = (t * 0.9f + time * 0.12f + dot.y * 0.08f) % 1.0f;
        float speed = Math.abs(dot.vy);
        float val = (0.55f + Math.min(speed * 0.25f, 0.35f) + dot.kickGlow * 0.35f) * dimFactor;
        float sat = 0.85f - dot.kickGlow * 0.25f;

        // Gold flash on kick
        if (dot.wasKicked && !isIdle) {
            hue = 0.12f; // Gold
            val = Math.min(1f, val + 0.25f);
            sat = 0.9f;
        }

        float[] rgb = hsvToRgb(hue, Math.min(1f, sat), Math.min(1f, val));
        float alpha = (0.75f + dot.kickGlow * 0.25f) * dimFactor;

        return new float[]{rgb[0], rgb[1], rgb[2], Math.min(1f, alpha)};
    }

    private void drawCommon(float[] mvpMatrix, int program) {
        GLES20.glUseProgram(program);

        int posHandle = GLES20.glGetAttribLocation(program, "vPosition");
        int colHandle = GLES20.glGetAttribLocation(program, "aColor");
        int mvpHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");

        if (mvpHandle != -1) {
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0);
        }

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        // Draw bars
        drawBuffer(barVboId, barColorVboId, posHandle, colHandle, BAR_COUNT * 6);

        // Draw dots with additive glow
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);
        drawBuffer(dotVboId, dotColorVboId, posHandle, colHandle, BAR_COUNT * 6);

        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisableVertexAttribArray(posHandle);
        GLES20.glDisableVertexAttribArray(colHandle);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    private void drawBuffer(int vbo, int cbo, int posHandle, int colHandle, int vertexCount) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, 0);
        GLES20.glEnableVertexAttribArray(posHandle);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, cbo);
        GLES20.glVertexAttribPointer(colHandle, 4, GLES20.GL_FLOAT, false, 0, 0);
        GLES20.glEnableVertexAttribArray(colHandle);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount);
    }

    private static void put3(FloatBuffer b, float x, float y, float z) {
        b.put(x); b.put(y); b.put(z);
    }

    private float[] hsvToRgb(float hue, float sat, float val) {
        float c = val * sat;
        float hp = hue * 6f;
        float x = c * (1f - Math.abs(hp % 2f - 1f));
        float m = val - c;

        float r, g, b;
        if (hp <= 1f) { r = c; g = x; b = 0f; }
        else if (hp <= 2f) { r = x; g = c; b = 0f; }
        else if (hp <= 3f) { r = 0f; g = c; b = x; }
        else if (hp <= 4f) { r = 0f; g = x; b = c; }
        else if (hp <= 5f) { r = x; g = 0f; b = c; }
        else { r = c; g = 0f; b = x; }

        return new float[]{r + m, g + m, b + m};
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    @Override
    public void draw2D(float[] mvpMatrix, GLVisualizerRenderer renderer) {
        drawCommon(mvpMatrix, shaderProgram2D);
    }

    @Override
    public void draw3D(float[] mvpMatrix, GLVisualizerRenderer renderer) {
        drawCommon(mvpMatrix, shaderProgram3D);
    }

    @Override
    public void release() {
        if (buffersInitialized) {
            GLES20.glDeleteBuffers(2, new int[]{barVboId, barColorVboId}, 0);
            GLES20.glDeleteBuffers(2, new int[]{dotVboId, dotColorVboId}, 0);
            buffersInitialized = false;
        }
    }
}
