package com.ikechi.studio.onwa.player.view.core.styles;

import android.opengl.GLES20;
import android.util.Log;

import com.ikechi.studio.onwa.player.view.core.GLVisualizerRenderer;
import com.ikechi.studio.onwa.player.view.core.visuals.VisualStyle;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Random;

/**
 * KaleidoFractalStyle - Fractal Kaleidoscope Mandala
 */
public class KaleidoFractalStyle extends VisualStyle {
    private static final String TAG = "KaleidoFractalStyle";

    private static final int MAX_MIRRORS = 12;
    private static final int WEDGE_SEGMENTS = 60;
    private static final int FRACTAL_DEPTH = 4;
    private static final int SPARKLE_COUNT = 200;

    private static final float ROTATION_SPEED = 0.25f;
    private static final float FRACTAL_SPEED = 0.6f;
    private static final float COLOR_FLOW_SPEED = 0.2f;

    private static final float RADIUS_MIN = 0.0f;
    private static final float RADIUS_MAX = 1.2f;

    private float smoothedBass = 0f;
    private float smoothedMid = 0f;
    private float beatPulse = 0f;
    private int beatFrames = 0;
    private int currentMirrorCount = 8;
    private float fractalScale = 1.0f;

    private float globalTime = 0f;
    private float kaleidoRotation = 0f;
    private float[] fractalPhases = new float[FRACTAL_DEPTH];
    private float currentDeltaTime = 0f;

    private float[] wedgeRadii = new float[WEDGE_SEGMENTS];

    private FloatBuffer kaleidoVertexBuffer;
    private ShortBuffer kaleidoIndexBuffer;
    private FloatBuffer fractalVertexBuffer;
    private FloatBuffer sparkleVertexBuffer;
    private FloatBuffer beamVertexBuffer;
    private FloatBuffer glowVertexBuffer;

    private int kaleidoVboId, fractalVboId, sparkleVboId, beamVboId, glowVboId;
    private int kaleidoIboId;

    private boolean buffersInitialized = false;

    private int kaleidoVertexCount = 0;
    private int fractalVertexCount = 0;
    private int sparkleVertexCount = 0;
    private int beamVertexCount = 0;
    private int glowVertexCount = 0;

    private float[] sparkleAngles = new float[SPARKLE_COUNT];
    private float[] sparkleRadii = new float[SPARKLE_COUNT];
    private float[] sparkleSpeeds = new float[SPARKLE_COUNT];
    private float[] sparkleSizes = new float[SPARKLE_COUNT];

    private Random random = new Random(42);

    @Override
    public void init(int shader2D, int shader3D, int shader3DUniform,
                     int texturedShader2D, int texturedShader3D,
                     GLVisualizerRenderer renderer) {
        super.init(shader2D, shader3D, shader3DUniform,
                   texturedShader2D, texturedShader3D, renderer);

        for (int i = 0; i < FRACTAL_DEPTH; i++) {
            fractalPhases[i] = (float)i / FRACTAL_DEPTH * (float)Math.PI * 2;
        }

        for (int i = 0; i < SPARKLE_COUNT; i++) {
            sparkleAngles[i] = random.nextFloat() * (float)(Math.PI * 2);
            sparkleRadii[i] = random.nextFloat() * RADIUS_MAX;
            sparkleSpeeds[i] = 0.3f + random.nextFloat() * 1.2f;
            sparkleSizes[i] = 0.002f + random.nextFloat() * 0.008f;
        }

        generateWedgePattern();
        allocateBuffers();
    }

    private void generateWedgePattern() {
        for (int i = 0; i < WEDGE_SEGMENTS; i++) {
            float t = (float)i / WEDGE_SEGMENTS;
            float radius = 0.05f + t * t * RADIUS_MAX;
            radius += (float)Math.sin(t * Math.PI * 8) * 0.03f;
            radius += (float)Math.cos(t * Math.PI * 13) * 0.02f;
            wedgeRadii[i] = radius;
        }
    }

    private void allocateBuffers() {
        if (buffersInitialized) {
            int[] vbos = new int[]{kaleidoVboId, fractalVboId, sparkleVboId, beamVboId, glowVboId};
            GLES20.glDeleteBuffers(5, vbos, 0);
            if (kaleidoIboId != 0) GLES20.glDeleteBuffers(1, new int[]{kaleidoIboId}, 0);
        }

        kaleidoVertexCount = MAX_MIRRORS * WEDGE_SEGMENTS * 2;
        int kaleidoFloatCount = kaleidoVertexCount * 7;

        fractalVertexCount = FRACTAL_DEPTH * 36 * 6;
        int fractalFloatCount = fractalVertexCount * 7;

        sparkleVertexCount = SPARKLE_COUNT * 6;
        int sparkleFloatCount = sparkleVertexCount * 7;

        beamVertexCount = MAX_MIRRORS * WEDGE_SEGMENTS * 2;
        int beamFloatCount = beamVertexCount * 7;

        glowVertexCount = 60;
        int glowFloatCount = glowVertexCount * 7;

        ByteBuffer kvb = ByteBuffer.allocateDirect(kaleidoFloatCount * 4);
        kvb.order(ByteOrder.nativeOrder());
        kaleidoVertexBuffer = kvb.asFloatBuffer();

        ByteBuffer kib = ByteBuffer.allocateDirect(kaleidoVertexCount * 2);
        kib.order(ByteOrder.nativeOrder());
        kaleidoIndexBuffer = kib.asShortBuffer();

        kaleidoIndexBuffer.clear();
        for (int i = 0; i < kaleidoVertexCount / 2; i++) {
            kaleidoIndexBuffer.put((short)(i * 2));
            kaleidoIndexBuffer.put((short)(i * 2 + 1));
        }
        kaleidoIndexBuffer.flip();

        ByteBuffer fvb = ByteBuffer.allocateDirect(fractalFloatCount * 4);
        fvb.order(ByteOrder.nativeOrder());
        fractalVertexBuffer = fvb.asFloatBuffer();

        ByteBuffer svb = ByteBuffer.allocateDirect(sparkleFloatCount * 4);
        svb.order(ByteOrder.nativeOrder());
        sparkleVertexBuffer = svb.asFloatBuffer();

        ByteBuffer bvb = ByteBuffer.allocateDirect(beamFloatCount * 4);
        bvb.order(ByteOrder.nativeOrder());
        beamVertexBuffer = bvb.asFloatBuffer();

        ByteBuffer gvb = ByteBuffer.allocateDirect(glowFloatCount * 4);
        gvb.order(ByteOrder.nativeOrder());
        glowVertexBuffer = gvb.asFloatBuffer();

        int[] vbos = new int[5];
        GLES20.glGenBuffers(5, vbos, 0);
        kaleidoVboId = vbos[0];
        fractalVboId = vbos[1];
        sparkleVboId = vbos[2];
        beamVboId = vbos[3];
        glowVboId = vbos[4];

        int[] ibos = new int[1];
        GLES20.glGenBuffers(1, ibos, 0);
        kaleidoIboId = ibos[0];

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, kaleidoVboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, kaleidoFloatCount * 4, null, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fractalVboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, fractalFloatCount * 4, null, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, sparkleVboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, sparkleFloatCount * 4, null, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, beamVboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, beamFloatCount * 4, null, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, glowVboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, glowFloatCount * 4, null, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, kaleidoIboId);
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, kaleidoVertexCount * 2, kaleidoIndexBuffer, GLES20.GL_STATIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

        buffersInitialized = true;
        Log.d(TAG, "KaleidoFractal buffers allocated");
    }

    @Override
    public void update(float deltaTime, float[] fftBands, float beatIntensity, float totalTime) {
        if (fftBands == null || fftBands.length == 0) return;

        this.totalTime = totalTime;
        globalTime = totalTime;
        currentDeltaTime = deltaTime;

        analyzeAudio(fftBands, beatIntensity);

        int targetMirrors = 6 + (int)(smoothedBass * 6);
        targetMirrors = Math.max(4, Math.min(MAX_MIRRORS, targetMirrors));
        if (targetMirrors != currentMirrorCount && beatFrames > 0) {
            currentMirrorCount = targetMirrors;
        }

        fractalScale = 0.7f + (float)Math.sin(globalTime * FRACTAL_SPEED) * 0.2f + beatPulse * 0.15f;
        kaleidoRotation += deltaTime * ROTATION_SPEED * (1f + smoothedMid * 0.5f);

        for (int i = 0; i < FRACTAL_DEPTH; i++) {
            fractalPhases[i] += deltaTime * FRACTAL_SPEED * (0.5f + i * 0.3f);
        }

        updateKaleidoscopeWedges();
        updateFractalRecursion();
        updateSparkles();
        updateKaleidoBeams();
        updateGlowCore();

        if (beatFrames > 0) {
            beatPulse = (float)beatFrames / 15f;
            beatFrames--;
        } else {
            beatPulse = 0f;
        }
    }

    private void analyzeAudio(float[] fftBands, float beatIntensity) {
        if (fftBands.length == 0) return;

        int bassEnd = fftBands.length / 6;
        int midEnd = fftBands.length / 2;

        float bass = 0f, mid = 0f;
        for (int i = 0; i < fftBands.length; i++) {
            float v = fftBands[i] * renderer.getSensitivity();
            if (i < bassEnd) bass += v * 1.5f;
            else if (i < midEnd) mid += v;
        }

        bass = Math.min(bass / bassEnd, 1.2f);
        mid = Math.min(mid / (midEnd - bassEnd), 1.0f);

        smoothedBass = lerp(smoothedBass, bass, 0.15f);
        smoothedMid = lerp(smoothedMid, mid, 0.12f);

        if (beatIntensity > 0.5f && smoothedBass > 0.4f) {
            beatFrames = 12;
        }
    }

    private void updateKaleidoscopeWedges() {
        kaleidoVertexBuffer.clear();

        float anglePerMirror = (float)(Math.PI * 2) / currentMirrorCount;
        float pulseRadius = 1f + (float)Math.sin(globalTime * 3) * 0.05f + beatPulse * 0.1f;

        for (int mirror = 0; mirror < currentMirrorCount; mirror++) {
            float mirrorAngle = mirror * anglePerMirror + kaleidoRotation;

            for (int i = 0; i < WEDGE_SEGMENTS; i++) {
                float t = (float)i / WEDGE_SEGMENTS;
                float radius = wedgeRadii[i] * pulseRadius;
                radius += (float)Math.sin(globalTime * 8 + mirror) * 0.02f * smoothedBass;

                float angleOuter = mirrorAngle + t * anglePerMirror;
                float xOuter = (float)Math.cos(angleOuter) * radius;
                float yOuter = (float)Math.sin(angleOuter) * radius;

                float angleInner = mirrorAngle + t * anglePerMirror * 0.8f;
                float xInner = (float)Math.cos(angleInner) * (radius * 0.3f);
                float yInner = (float)Math.sin(angleInner) * (radius * 0.3f);

                float hue = (t * 2 + (float)mirror / currentMirrorCount + globalTime * COLOR_FLOW_SPEED) % 1.0f;
                float sat = 0.8f + (float)Math.sin(globalTime * 4 + mirror) * 0.2f;
                float val = 0.7f + smoothedBass * 0.3f + beatPulse * 0.2f;
                float[] rgb = hsvToRgb(hue, sat, val);

                float alpha = 0.5f + t * 0.3f + smoothedBass * 0.2f;
                float z = (float)Math.sin(angleOuter * 5 + globalTime * 6) * 0.04f * smoothedBass;

                kaleidoVertexBuffer.put(xOuter);
                kaleidoVertexBuffer.put(yOuter);
                kaleidoVertexBuffer.put(z);
                kaleidoVertexBuffer.put(rgb[0]);
                kaleidoVertexBuffer.put(rgb[1]);
                kaleidoVertexBuffer.put(rgb[2]);
                kaleidoVertexBuffer.put(alpha);

                kaleidoVertexBuffer.put(xInner);
                kaleidoVertexBuffer.put(yInner);
                kaleidoVertexBuffer.put(z - 0.02f);
                kaleidoVertexBuffer.put(rgb[0] * 0.6f);
                kaleidoVertexBuffer.put(rgb[1] * 0.6f);
                kaleidoVertexBuffer.put(rgb[2] * 0.7f);
                kaleidoVertexBuffer.put(alpha * 0.5f);
            }
        }

        kaleidoVertexBuffer.flip();
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, kaleidoVboId);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, kaleidoVertexBuffer.remaining() * 4, kaleidoVertexBuffer);
    }

    private void updateFractalRecursion() {
        fractalVertexBuffer.clear();

        for (int depth = 0; depth < FRACTAL_DEPTH; depth++) {
            float depthScale = (float)Math.pow(0.5f, depth + 1) * fractalScale;
            float depthRotation = fractalPhases[depth];
            int subMirrors = Math.max(3, currentMirrorCount - depth);
            float subAngleStep = (float)(Math.PI * 2) / subMirrors;

            for (int mirror = 0; mirror < subMirrors; mirror++) {
                float angle = mirror * subAngleStep + depthRotation + kaleidoRotation * 0.5f;
                float radius = RADIUS_MAX * depthScale;

                for (int p = 0; p < 6; p++) {
                    float pointAngle = angle + (float)(p * Math.PI * 2 / 6);
                    float x = (float)Math.cos(pointAngle) * radius;
                    float y = (float)Math.sin(pointAngle) * radius;

                    float hue = (depth * 0.2f + globalTime * 0.15f) % 1.0f;
                    float[] rgb = hsvToRgb(hue, 0.9f, 0.95f);

                    float size = 0.004f * (FRACTAL_DEPTH - depth) * (1f + smoothedBass);

                    for (int tri = 0; tri < 3; tri++) {
                        float triAngle = pointAngle + (float)(tri * Math.PI * 2 / 3);
                        float tx = x + (float)Math.cos(triAngle) * size;
                        float ty = y + (float)Math.sin(triAngle) * size;

                        fractalVertexBuffer.put(tx);
                        fractalVertexBuffer.put(ty);
                        fractalVertexBuffer.put(0.06f);
                        fractalVertexBuffer.put(rgb[0]);
                        fractalVertexBuffer.put(rgb[1]);
                        fractalVertexBuffer.put(rgb[2]);
                        fractalVertexBuffer.put(0.6f);
                    }
                }
            }
        }

        fractalVertexBuffer.flip();
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fractalVboId);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, fractalVertexBuffer.remaining() * 4, fractalVertexBuffer);
    }

    private void updateKaleidoBeams() {
        beamVertexBuffer.clear();

        float anglePerMirror = (float)(Math.PI * 2) / currentMirrorCount;

        for (int mirror = 0; mirror < currentMirrorCount; mirror++) {
            float angle1 = mirror * anglePerMirror + kaleidoRotation;
            float angle2 = ((mirror + 1) % currentMirrorCount) * anglePerMirror + kaleidoRotation;

            float radius1 = RADIUS_MAX * (0.6f + (float)Math.sin(globalTime * 2 + mirror) * 0.1f);
            float radius2 = RADIUS_MAX * (0.6f + (float)Math.sin(globalTime * 2 + mirror + 1) * 0.1f);

            float x1 = (float)Math.cos(angle1) * radius1;
            float y1 = (float)Math.sin(angle1) * radius1;
            float x2 = (float)Math.cos(angle2) * radius2;
            float y2 = (float)Math.sin(angle2) * radius2;

            float hue = (float)mirror / currentMirrorCount + globalTime * 0.3f;
            float[] rgb = hsvToRgb(hue % 1.0f, 1.0f, 1.0f);
            float alpha = 0.4f + (float)Math.sin(globalTime * 8 + mirror) * 0.2f;

            beamVertexBuffer.put(x1);
            beamVertexBuffer.put(y1);
            beamVertexBuffer.put(0.01f);
            beamVertexBuffer.put(rgb[0]);
            beamVertexBuffer.put(rgb[1]);
            beamVertexBuffer.put(rgb[2]);
            beamVertexBuffer.put(alpha);

            beamVertexBuffer.put(x2);
            beamVertexBuffer.put(y2);
            beamVertexBuffer.put(0.01f);
            beamVertexBuffer.put(rgb[0]);
            beamVertexBuffer.put(rgb[1]);
            beamVertexBuffer.put(rgb[2]);
            beamVertexBuffer.put(alpha);
        }

        beamVertexBuffer.flip();
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, beamVboId);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, beamVertexBuffer.remaining() * 4, beamVertexBuffer);
    }

    private void updateSparkles() {
        sparkleVertexBuffer.clear();

        for (int i = 0; i < SPARKLE_COUNT; i++) {
            sparkleAngles[i] += currentDeltaTime * sparkleSpeeds[i] * (1f + smoothedBass);
            float radius = sparkleRadii[i] * (0.5f + (float)Math.sin(globalTime * 1.5f + i) * 0.2f);
            radius += beatPulse * 0.05f;

            float x = (float)Math.cos(sparkleAngles[i]) * radius;
            float y = (float)Math.sin(sparkleAngles[i]) * radius;
            float z = (float)Math.sin(globalTime * 4 + i) * 0.06f;

            float hue = (radius * 2 + globalTime * 0.2f) % 1.0f;
            float[] rgb = hsvToRgb(hue, 0.9f, 1.0f);
            float alpha = 0.5f + (float)Math.sin(globalTime * 8 + i) * 0.3f;

            float size = sparkleSizes[i] * (1f + smoothedBass);

            sparkleVertexBuffer.put(x - size);
            sparkleVertexBuffer.put(y - size);
            sparkleVertexBuffer.put(z);
            sparkleVertexBuffer.put(rgb[0]);
            sparkleVertexBuffer.put(rgb[1]);
            sparkleVertexBuffer.put(rgb[2]);
            sparkleVertexBuffer.put(alpha);

            sparkleVertexBuffer.put(x + size);
            sparkleVertexBuffer.put(y - size);
            sparkleVertexBuffer.put(z);
            sparkleVertexBuffer.put(rgb[0]);
            sparkleVertexBuffer.put(rgb[1]);
            sparkleVertexBuffer.put(rgb[2]);
            sparkleVertexBuffer.put(alpha);

            sparkleVertexBuffer.put(x - size);
            sparkleVertexBuffer.put(y + size);
            sparkleVertexBuffer.put(z);
            sparkleVertexBuffer.put(rgb[0]);
            sparkleVertexBuffer.put(rgb[1]);
            sparkleVertexBuffer.put(rgb[2]);
            sparkleVertexBuffer.put(alpha);

            sparkleVertexBuffer.put(x + size);
            sparkleVertexBuffer.put(y - size);
            sparkleVertexBuffer.put(z);
            sparkleVertexBuffer.put(rgb[0]);
            sparkleVertexBuffer.put(rgb[1]);
            sparkleVertexBuffer.put(rgb[2]);
            sparkleVertexBuffer.put(alpha);

            sparkleVertexBuffer.put(x + size);
            sparkleVertexBuffer.put(y + size);
            sparkleVertexBuffer.put(z);
            sparkleVertexBuffer.put(rgb[0]);
            sparkleVertexBuffer.put(rgb[1]);
            sparkleVertexBuffer.put(rgb[2]);
            sparkleVertexBuffer.put(alpha);

            sparkleVertexBuffer.put(x - size);
            sparkleVertexBuffer.put(y + size);
            sparkleVertexBuffer.put(z);
            sparkleVertexBuffer.put(rgb[0]);
            sparkleVertexBuffer.put(rgb[1]);
            sparkleVertexBuffer.put(rgb[2]);
            sparkleVertexBuffer.put(alpha);
        }

        sparkleVertexBuffer.flip();
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, sparkleVboId);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, sparkleVertexBuffer.remaining() * 4, sparkleVertexBuffer);
    }

    private void updateGlowCore() {
        glowVertexBuffer.clear();

        float coreSize = 0.07f + smoothedBass * 0.06f + beatPulse * 0.08f;

        int segments = 30;
        for (int i = 0; i < segments; i++) {
            float angle = (float)(i * 2 * Math.PI / segments);
            float x = (float)Math.cos(angle) * coreSize;
            float y = (float)Math.sin(angle) * coreSize;
            float xi = (float)Math.cos(angle) * (coreSize * 0.4f);
            float yi = (float)Math.sin(angle) * (coreSize * 0.4f);

            float hue = globalTime * 0.3f % 1.0f;
            float[] rgb = hsvToRgb(hue, 0.9f, 1.0f);

            glowVertexBuffer.put(x);
            glowVertexBuffer.put(y);
            glowVertexBuffer.put(0.1f);
            glowVertexBuffer.put(rgb[0]);
            glowVertexBuffer.put(rgb[1]);
            glowVertexBuffer.put(rgb[2]);
            glowVertexBuffer.put(0.9f);

            glowVertexBuffer.put(xi);
            glowVertexBuffer.put(yi);
            glowVertexBuffer.put(0.1f);
            glowVertexBuffer.put(1.0f);
            glowVertexBuffer.put(1.0f);
            glowVertexBuffer.put(1.0f);
            glowVertexBuffer.put(0.9f);
        }

        glowVertexBuffer.flip();

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, glowVboId);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, glowVertexBuffer.remaining() * 4, glowVertexBuffer);
    }

    private void drawCommon(float[] mvpMatrix, int program, float beatIntensity) {
        GLES20.glUseProgram(program);

        int posHandle = GLES20.glGetAttribLocation(program, "vPosition");
        int colHandle = GLES20.glGetAttribLocation(program, "aColor");
        int mvpHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");

        if (posHandle == -1) return;

        if (mvpHandle != -1) {
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0);
        }

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);

        int stride = 7 * 4;

        // Draw glow core
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, glowVboId);
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, stride, 0);
        GLES20.glEnableVertexAttribArray(posHandle);
        if (colHandle != -1) {
            GLES20.glVertexAttribPointer(colHandle, 4, GLES20.GL_FLOAT, false, stride, 3 * 4);
            GLES20.glEnableVertexAttribArray(colHandle);
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, glowVertexCount);

        // Draw kaleidoscope wedges
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, kaleidoVboId);
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, stride, 0);
        if (colHandle != -1) {
            GLES20.glVertexAttribPointer(colHandle, 4, GLES20.GL_FLOAT, false, stride, 3 * 4);
        }
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, kaleidoIboId);
        GLES20.glDrawElements(GLES20.GL_TRIANGLE_STRIP, kaleidoVertexCount, GLES20.GL_UNSIGNED_SHORT, 0);

        // Draw fractal recursion
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fractalVboId);
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, stride, 0);
        if (colHandle != -1) {
            GLES20.glVertexAttribPointer(colHandle, 4, GLES20.GL_FLOAT, false, stride, 3 * 4);
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, fractalVertexCount);

        // Draw kaleido beams
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);
        GLES20.glLineWidth(1.5f);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, beamVboId);
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, stride, 0);
        if (colHandle != -1) {
            GLES20.glVertexAttribPointer(colHandle, 4, GLES20.GL_FLOAT, false, stride, 3 * 4);
        }
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, beamVertexCount);

        // Draw sparkles
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, sparkleVboId);
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, stride, 0);
        if (colHandle != -1) {
            GLES20.glVertexAttribPointer(colHandle, 4, GLES20.GL_FLOAT, false, stride, 3 * 4);
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, sparkleVertexCount);

        GLES20.glDisableVertexAttribArray(posHandle);
        if (colHandle != -1) GLES20.glDisableVertexAttribArray(colHandle);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }

    private float[] hsvToRgb(float hue, float sat, float val) {
        float c = val * sat;
        float hp = hue * 6;
        float x = c * (1 - Math.abs(hp % 2 - 1));
        float m = val - c;

        float r1 = 0, g1 = 0, b1 = 0;
        if (hp <= 1) { r1 = c; g1 = x; b1 = 0; }
        else if (hp <= 2) { r1 = x; g1 = c; b1 = 0; }
        else if (hp <= 3) { r1 = 0; g1 = c; b1 = x; }
        else if (hp <= 4) { r1 = 0; g1 = x; b1 = c; }
        else if (hp <= 5) { r1 = x; g1 = 0; b1 = c; }
        else { r1 = c; g1 = 0; b1 = x; }

        return new float[]{r1 + m, g1 + m, b1 + m};
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    @Override
    public void draw2D(float[] mvpMatrix, GLVisualizerRenderer renderer) {
        drawCommon(mvpMatrix, shaderProgram2D, renderer.getBeatIntensity());
    }

    @Override
    public void draw3D(float[] mvpMatrix, GLVisualizerRenderer renderer) {
        drawCommon(mvpMatrix, shaderProgram3D, renderer.getBeatIntensity());
    }

    @Override
    public void release() {
        if (buffersInitialized) {
            int[] vbos = new int[]{kaleidoVboId, fractalVboId, sparkleVboId, beamVboId, glowVboId};
            GLES20.glDeleteBuffers(5, vbos, 0);
            if (kaleidoIboId != 0) GLES20.glDeleteBuffers(1, new int[]{kaleidoIboId}, 0);
            buffersInitialized = false;
        }
    }

    @Override
    public boolean usesTexture() {
        return false;
    }
}
