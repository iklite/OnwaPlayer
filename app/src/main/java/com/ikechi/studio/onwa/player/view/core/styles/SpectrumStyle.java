package com.ikechi.studio.onwa.player.view.core.styles;

import android.opengl.GLES20;

import com.ikechi.studio.onwa.player.view.core.GLVisualizerRenderer;
import com.ikechi.studio.onwa.player.view.core.visuals.VisualStyle;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Radial spectrum – filled wedge bars radiating from a small centre,
 * with physics-based dots that sit on the bar tips and are kicked upward by bar growth.
 *
 * COORDINATE SYSTEM:
 *   INVERT_Y = true  → y = +1 is SCREEN BOTTOM, y = –1 is SCREEN TOP.
 *   INVERT_Y = false → y = –1 is bottom, y = +1 is top (standard NDC).
 */
public class SpectrumStyle extends VisualStyle {

    // --- Constants (Tweak these for feel) ---
    private static final boolean INVERT_Y = true;
    private static final float INNER_RADIUS = 0.25f;
    private static final float MAX_HEIGHT = 0.75f;
    private static final float BAR_ANGLE_FACTOR = 0.6f;

    // Dot physics (optimized for responsiveness)
    private static final float DOT_GRAVITY = 4.0f;
    private static final float DOT_KICK_FORCE = 12.0f;
    private static final float DOT_AIR_RESISTANCE = 0.94f;
    private static final float DOT_GROUND_BOUNCE = 0.3f;
    private static final float IDLE_FALL_SPEED = 10.0f;
    private static final int TRAIL_LENGTH = 5; // For comet-like trail effect

    // --- State ---
    private int barCount;
    private FloatBuffer barVertexBuffer, barColorBuffer;
    private FloatBuffer dotVertexBuffer, dotColorBuffer;
    private int barVboId, barColorVboId;
    private int dotVboId, dotColorVboId;
    private boolean buffersInitialized = false;

    private BarState[] bars;
    private DotState[] dots;

    private float globalTime = 0f;
    private float beatAccumulator = 0f;
    private boolean isIdle = true;

    // --- Per-bar and per-dot state ---
    private static class BarState {
        float currentHeight;
        float targetHeight;
        float prevHeight;
        boolean isGrowing;
    }

    private static class DotState {
        float radius;
        float velocity;
        float kickGlow;
        boolean wasKicked;
        // Trail: stores last N positions for comet effect
        float[] trailRadius = new float[TRAIL_LENGTH];
        float[] trailTime = new float[TRAIL_LENGTH]; // Age of each trail point (0 = newest)
    }

    // --- Initialization ---
    @Override
    public void init(int shader2D, int shader3D, int shader3DUniform,
                     int texturedShader2D, int texturedShader3D,
                     GLVisualizerRenderer renderer) {
        super.init(shader2D, shader3D, shader3DUniform,
                   texturedShader2D, texturedShader3D, renderer);
        barCount = renderer.getBarCount();
        initPhysics();
        allocateBuffers();
    }

    private void initPhysics() {
        bars = new BarState[barCount];
        dots = new DotState[barCount];
        for (int i = 0; i < barCount; i++) {
            bars[i] = new BarState();
            bars[i].currentHeight = 0f;
            bars[i].targetHeight = 0f;
            bars[i].prevHeight = 0f;

            dots[i] = new DotState();
            dots[i].radius = INNER_RADIUS;
            dots[i].velocity = 0f;
            dots[i].kickGlow = 0f;
            // Initialize trail
            for (int j = 0; j < TRAIL_LENGTH; j++) {
                dots[i].trailRadius[j] = INNER_RADIUS;
                dots[i].trailTime[j] = 1f; // Start as "old" (invisible)
            }
        }
    }

    private void allocateBuffers() {
        if (buffersInitialized) {
            GLES20.glDeleteBuffers(2, new int[]{barVboId, barColorVboId}, 0);
            GLES20.glDeleteBuffers(2, new int[]{dotVboId, dotColorVboId}, 0);
        }

        int barVertCount = barCount * 6; // 2 triangles per bar
        int dotVertCount = barCount * (6 + 6 * TRAIL_LENGTH); // 2 triangles per dot + trail

        ByteBuffer vbb = ByteBuffer.allocateDirect(barVertCount * 3 * 4);
        vbb.order(ByteOrder.nativeOrder());
        barVertexBuffer = vbb.asFloatBuffer();

        ByteBuffer cbb = ByteBuffer.allocateDirect(barVertCount * 4 * 4);
        cbb.order(ByteOrder.nativeOrder());
        barColorBuffer = cbb.asFloatBuffer();

        ByteBuffer dvbb = ByteBuffer.allocateDirect(dotVertCount * 3 * 4);
        dvbb.order(ByteOrder.nativeOrder());
        dotVertexBuffer = dvbb.asFloatBuffer();

        ByteBuffer dcbb = ByteBuffer.allocateDirect(dotVertCount * 4 * 4);
        dcbb.order(ByteOrder.nativeOrder());
        dotColorBuffer = dcbb.asFloatBuffer();

        int[] vbos = new int[2];
        GLES20.glGenBuffers(2, vbos, 0);
        barVboId = vbos[0];
        barColorVboId = vbos[1];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, barVboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, barVertCount * 3 * 4, null, GLES20.GL_DYNAMIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, barColorVboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, barVertCount * 4 * 4, null, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glGenBuffers(2, vbos, 0);
        dotVboId = vbos[0];
        dotColorVboId = vbos[1];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, dotVboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, dotVertCount * 3 * 4, null, GLES20.GL_DYNAMIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, dotColorVboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, dotVertCount * 4 * 4, null, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        buffersInitialized = true;
    }

    // --- Update Logic ---
    @Override
    public void update(float deltaTime, float[] fftBands, float beatIntensity, float totalTime) {
        this.totalTime = totalTime;
        globalTime = totalTime;
        if (fftBands == null) return;

        // Idle detection
        float totalEnergy = 0f;
        for (float v : fftBands) totalEnergy += v;
        totalEnergy = (totalEnergy / fftBands.length) * renderer.getSensitivity();
        isIdle = totalEnergy < 0.03f;

        float dt = Math.min(deltaTime, 0.05f);
        beatAccumulator = Math.max(0f, beatAccumulator - dt * 2f);
        if (beatIntensity > 0.6f) beatAccumulator = 1f;

        // Update bars and dots
        for (int i = 0; i < barCount; i++) {
            updateBar(i, fftBands, dt);
            updateDot(i, dt, beatIntensity);
        }

        buildGeometry();
    }

    private void updateBar(int i, float[] fftBands, float dt) {
        BarState bar = bars[i];
        float target;

        if (isIdle) {
            target = 0f;
        } else {
            int fftIdx = (i * fftBands.length) / barCount;
            float raw = fftBands[Math.min(fftIdx, fftBands.length - 1)];
            float gain = renderer.getSensitivity() * (1f + beatAccumulator * 0.5f);
            target = raw * gain * 1.8f;
            target = Math.min(target, MAX_HEIGHT);
            target = Math.max(target, 0.02f);
        }

        bar.targetHeight = target;

        float speed = isIdle ? IDLE_FALL_SPEED : (bar.targetHeight > bar.currentHeight ? 20f : 10f);
        bar.currentHeight = lerp(bar.currentHeight, bar.targetHeight, dt * speed);
        bar.isGrowing = bar.currentHeight > bar.prevHeight;
        bar.prevHeight = bar.currentHeight;
    }

    private void updateDot(int i, float dt, float beatIntensity) {
        BarState bar = bars[i];
        DotState dot = dots[i];
        float barTipRadius = INNER_RADIUS + bar.currentHeight;

        // Kick detection: bar tip just passed the dot's radius
        if (bar.isGrowing && barTipRadius > dot.radius && bar.prevHeight <= (dot.radius - INNER_RADIUS)) {
            dot.velocity = DOT_KICK_FORCE * (1f + beatIntensity * 0.5f);
            dot.kickGlow = 1f;
            dot.wasKicked = true;
        }

        // Physics
        dot.velocity -= DOT_GRAVITY * dt;
        dot.velocity *= DOT_AIR_RESISTANCE;
        dot.radius += dot.velocity * dt;

        // Soft landing on bar tip
        if (dot.radius < barTipRadius && dot.velocity < 0) {
            if (dot.velocity < -1.5f) {
                dot.velocity *= -DOT_GROUND_BOUNCE;
                dot.radius = barTipRadius;
            } else {
                dot.radius = barTipRadius;
                dot.velocity = 0f;
            }
        }

        // Hard limits
        dot.radius = Math.max(INNER_RADIUS, Math.min(dot.radius, 0.98f));
        if (dot.radius >= 0.98f) {
            dot.velocity = -Math.abs(dot.velocity) * 0.4f;
        }

        // Update trail
        System.arraycopy(dot.trailRadius, 0, dot.trailRadius, 1, TRAIL_LENGTH - 1);
        System.arraycopy(dot.trailTime, 0, dot.trailTime, 1, TRAIL_LENGTH - 1);
        dot.trailRadius[0] = dot.radius;
        dot.trailTime[0] = 0f;
        for (int j = 1; j < TRAIL_LENGTH; j++) {
            dot.trailTime[j] += dt;
        }

        // Glow decay
        dot.kickGlow = Math.max(0f, dot.kickGlow - dt * 2.5f);
        dot.wasKicked = dot.kickGlow > 0.1f;
    }

    // --- Geometry Building ---
    private void buildGeometry() {
        barVertexBuffer.clear(); barColorBuffer.clear();
        dotVertexBuffer.clear(); dotColorBuffer.clear();

        float angleStep = (float)(2 * Math.PI / barCount);
        float halfWidth = angleStep * BAR_ANGLE_FACTOR * 0.5f;

        for (int i = 0; i < barCount; i++) {
            float angle = i * angleStep;
            float cosA = (float)Math.cos(angle);
            float sinA = (float)Math.sin(angle);

            // Bar vertices
            float rInner = INNER_RADIUS;
            float rOuter = INNER_RADIUS + bars[i].currentHeight;
            float a1 = angle - halfWidth;
            float a2 = angle + halfWidth;
            float x1 = (float)Math.cos(a1), y1 = (float)Math.sin(a1);
            float x2 = (float)Math.cos(a2), y2 = (float)Math.sin(a2);

            float[] innerA1 = {x1 * rInner, y1 * rInner, 0f};
            float[] innerA2 = {x2 * rInner, y2 * rInner, 0f};
            float[] outerA1 = {x1 * rOuter, y1 * rOuter, 0f};
            float[] outerA2 = {x2 * rOuter, y2 * rOuter, 0f};

            if (INVERT_Y) {
                innerA1[1] = -innerA1[1]; innerA2[1] = -innerA2[1];
                outerA1[1] = -outerA1[1]; outerA2[1] = -outerA2[1];
            }

            float dim = isIdle ? 0.25f : 1.0f;
            float[] barColor = getBarColor(i, bars[i].currentHeight, bars[i].isGrowing, dots[i].kickGlow, dim);
            putTriangle(barVertexBuffer, barColorBuffer, innerA1, innerA2, outerA2, barColor);
            putTriangle(barVertexBuffer, barColorBuffer, innerA1, outerA2, outerA1, barColor);

            // Dot and trail
            DotState dot = dots[i];
            float dotR = dot.radius;
            float baseDotSize = 0.04f + (bars[i].currentHeight / MAX_HEIGHT) * 0.03f;
            float dotSize = baseDotSize + dot.kickGlow * 0.04f;
            float dotCx = cosA * dotR;
            float dotCy = sinA * dotR;
            if (INVERT_Y) dotCy = -dotCy;

            float[] dotCol = getDotColor(i, dot, dim);
            float zDot = 0.05f;

            // Main dot
            float[] v0 = {dotCx - dotSize, dotCy - dotSize, zDot};
            float[] v1 = {dotCx + dotSize, dotCy - dotSize, zDot};
            float[] v2 = {dotCx + dotSize, dotCy + dotSize, zDot};
            float[] v3 = {dotCx - dotSize, dotCy + dotSize, zDot};
            putTriangle(dotVertexBuffer, dotColorBuffer, v0, v1, v2, dotCol);
            putTriangle(dotVertexBuffer, dotColorBuffer, v0, v2, v3, dotCol);

            // Trail
            for (int j = 0; j < TRAIL_LENGTH; j++) {
                if (dot.trailTime[j] < 0.3f) {
                    float trailR = dot.trailRadius[j];
                    float trailAlpha = 1f - (dot.trailTime[j] / 0.3f);
                    float trailSize = dotSize * 0.7f * trailAlpha;
                    float trailCx = cosA * trailR;
                    float trailCy = sinA * trailR;
                    if (INVERT_Y) trailCy = -trailCy;

                    float[] trailCol = new float[]{
                        dotCol[0], dotCol[1], dotCol[2], dotCol[3] * trailAlpha * 0.5f
                    };

                    float[] tv0 = {trailCx - trailSize, trailCy - trailSize, zDot};
                    float[] tv1 = {trailCx + trailSize, trailCy - trailSize, zDot};
                    float[] tv2 = {trailCx + trailSize, trailCy + trailSize, zDot};
                    float[] tv3 = {trailCx - trailSize, trailCy + trailSize, zDot};
                    putTriangle(dotVertexBuffer, dotColorBuffer, tv0, tv1, tv2, trailCol);
                    putTriangle(dotVertexBuffer, dotColorBuffer, tv0, tv2, tv3, trailCol);
                }
            }
        }

        // Upload to GPU
        barVertexBuffer.flip(); barColorBuffer.flip();
        dotVertexBuffer.flip(); dotColorBuffer.flip();

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, barVboId);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, barVertexBuffer.remaining() * 4, barVertexBuffer);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, barColorVboId);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, barColorBuffer.remaining() * 4, barColorBuffer);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, dotVboId);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, dotVertexBuffer.remaining() * 4, dotVertexBuffer);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, dotColorVboId);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, dotColorBuffer.remaining() * 4, dotColorBuffer);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    private void putTriangle(FloatBuffer vb, FloatBuffer cb, float[] v0, float[] v1, float[] v2, float[] color) {
        vb.put(v0); cb.put(color);
        vb.put(v1); cb.put(color);
        vb.put(v2); cb.put(color);
    }

    // --- Color Logic ---
    private float[] getBarColor(int i, float height, boolean isGrowing, float kickGlow, float dimFactor) {
        float t = (float)i / barCount;
        float hue = (t * 0.9f + globalTime * 0.05f) % 1.0f;
        float sat = 0.75f + (height / MAX_HEIGHT) * 0.25f;
        float val = (0.35f + (height / MAX_HEIGHT) * 0.65f) * dimFactor;

        if (isIdle) {
            float pulse = (float)(0.5f + 0.5f * Math.sin(globalTime * 2f));
            val = Math.min(0.4f, val * (0.7f + pulse * 0.3f));
        } else if (isGrowing) {
            val += 0.15f;
            sat += 0.1f;
        }

        val += kickGlow * 0.4f;
        float[] rgb = hsvToRgb(hue, Math.min(1f, sat), Math.min(1f, val));
        return new float[]{rgb[0], rgb[1], rgb[2], (0.85f + (height / MAX_HEIGHT) * 0.15f) * dimFactor};
    }

    private float[] getDotColor(int i, DotState dot, float dimFactor) {
        float t = (float)i / barCount;
        float baseHue = (t * 0.9f + globalTime * 0.12f + (dot.radius - INNER_RADIUS) * 0.3f) % 1.0f;
        float hue = baseHue;
        float speed = Math.abs(dot.velocity);
        float val = (0.55f + Math.min(speed * 0.25f, 0.35f) + dot.kickGlow * 0.35f) * dimFactor;
        float sat = 0.85f - dot.kickGlow * 0.25f;

        if (dot.wasKicked && !isIdle) {
            float goldBlend = dot.kickGlow;
            hue = lerp(baseHue, 0.12f, goldBlend);
            val = Math.min(1f, val + 0.25f * goldBlend);
            sat = lerp(sat, 0.9f, goldBlend);
        }

        float[] rgb = hsvToRgb(hue, Math.min(1f, sat), Math.min(1f, val));
        return new float[]{rgb[0], rgb[1], rgb[2], (0.75f + dot.kickGlow * 0.25f) * dimFactor};
    }

    // --- Drawing ---
    private void drawCommon(float[] mvpMatrix, int program) {
        GLES20.glUseProgram(program);
        int posHandle = GLES20.glGetAttribLocation(program, "vPosition");
        int colHandle = GLES20.glGetAttribLocation(program, "aColor");
        int mvpHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
        if (mvpHandle != -1) GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        // Draw bars
        drawBuffer(barVboId, barColorVboId, posHandle, colHandle, barCount * 6);

        // Draw dots with additive glow
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);
        drawBuffer(dotVboId, dotColorVboId, posHandle, colHandle, barCount * (6 + 6 * TRAIL_LENGTH));

        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisableVertexAttribArray(posHandle);
        GLES20.glDisableVertexAttribArray(colHandle);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    private void drawBuffer(int vbo, int cbo, int posHandle, int colHandle, int count) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, 0);
        GLES20.glEnableVertexAttribArray(posHandle);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, cbo);
        GLES20.glVertexAttribPointer(colHandle, 4, GLES20.GL_FLOAT, false, 0, 0);
        GLES20.glEnableVertexAttribArray(colHandle);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count);
    }

    // --- Utilities ---
    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private float[] hsvToRgb(float h, float s, float v) {
        float c = v * s;
        float hp = h * 6f;
        float x = c * (1f - Math.abs(hp % 2f - 1f));
        float m = v - c;
        float r, g, b;
        if (hp <= 1f) { r = c; g = x; b = 0f; }
        else if (hp <= 2f) { r = x; g = c; b = 0f; }
        else if (hp <= 3f) { r = 0f; g = c; b = x; }
        else if (hp <= 4f) { r = 0f; g = x; b = c; }
        else if (hp <= 5f) { r = x; g = 0f; b = c; }
        else { r = c; g = 0f; b = x; }
        return new float[]{r + m, g + m, b + m};
    }

    @Override public void draw2D(float[] mvpMatrix, GLVisualizerRenderer r) {
        drawCommon(mvpMatrix, shaderProgram2D);
    }

    @Override public void draw3D(float[] mvpMatrix, GLVisualizerRenderer r) {
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

