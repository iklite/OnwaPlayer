
package com.ikechi.studio.onwa.player.view.core.styles;

import android.opengl.GLES20;
import android.util.Log;

import com.ikechi.studio.onwa.player.view.core.GLVisualizerRenderer;
import com.ikechi.studio.onwa.player.view.core.visuals.VisualStyle;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Enhanced symmetrical waveform visualizer with:
 * - Dynamic morphing between sharp peaks and smooth waves
 * - Frequency-based color mapping (low = warm, high = cool)
 * - Beat-reactive glow and motion blur trails
 * - Adaptive bar width and depth
 * - Optimized for performance and visual appeal
 */
public class WaveformStyle extends VisualStyle {

    private static final String TAG = "WaveformStyle";
    private static final int MAX_BARS = 128;
    private static final int VERTS_PER_BAR = 6; // 2 triangles per bar
    private static final int TRAIL_LENGTH = 3;  // Number of motion blur trails per bar

    private int barCount = 64;
    private FloatBuffer vertexBuffer;
    private FloatBuffer colorBuffer;
    private int vboId, colorVboId;
    private boolean buffersInitialized = false;

    // Previous frame data for motion blur
    private float[] prevHeights = new float[MAX_BARS];
    private float[] prevColors = new float[MAX_BARS * 4]; // RGBA for each bar

    // Animation state
    private float globalTime = 0f;
    private float beatPulse = 0f;
    private float morphFactor = 0f; // 0 = sharp peaks, 1 = smooth waves

    @Override
    public void init(int shader2D, int shader3D, int shader3DUniform,
                     int texturedShader2D, int texturedShader3D,
                     GLVisualizerRenderer renderer) {
        super.init(shader2D, shader3D, shader3DUniform,
                   texturedShader2D, texturedShader3D, renderer);
        allocateBuffers();
    }

    private void allocateBuffers() {
        if (buffersInitialized) {
            GLES20.glDeleteBuffers(2, new int[]{vboId, colorVboId}, 0);
        }

        // Total vertices: bars + trails
        int totalVerts = MAX_BARS * VERTS_PER_BAR * (1 + TRAIL_LENGTH);
        ByteBuffer vbb = ByteBuffer.allocateDirect(totalVerts * 3 * 4);
        vbb.order(ByteOrder.nativeOrder());
        vertexBuffer = vbb.asFloatBuffer();

        ByteBuffer cbb = ByteBuffer.allocateDirect(totalVerts * 4 * 4);
        cbb.order(ByteOrder.nativeOrder());
        colorBuffer = cbb.asFloatBuffer();

        int[] vbos = new int[2];
        GLES20.glGenBuffers(2, vbos, 0);
        vboId = vbos[0];
        colorVboId = vbos[1];

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, totalVerts * 3 * 4, null, GLES20.GL_DYNAMIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, colorVboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, totalVerts * 4 * 4, null, GLES20.GL_DYNAMIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        buffersInitialized = true;
    }

    @Override
    public void update(float deltaTime, float[] fftBands, float beatIntensity, float totalTime) {
        try {
            this.totalTime = totalTime;
            this.globalTime = totalTime;
            if (fftBands == null || fftBands.length == 0) return;

            barCount = Math.min(fftBands.length, MAX_BARS);

            // Update beat pulse (0 to 1, decays over time)
            beatPulse = Math.max(0f, beatPulse - deltaTime * 3f);
            if (beatIntensity > 0.7f) beatPulse = 1f;

            // Update morph factor based on average energy (low energy = more wave-like)
            float avgEnergy = 0f;
            for (float v : fftBands) avgEnergy += v;
            avgEnergy /= barCount;
            morphFactor = 1f - Math.min(1f, avgEnergy * 2f); // High energy = sharp (0), low = wave (1)

            vertexBuffer.clear();
            colorBuffer.clear();

            float barWidth = 2.0f / barCount;
            float baseHalfWidth = barWidth * 0.4f; // Default bar thickness

            for (int i = 0; i < barCount; i++) {
                // --- Calculate height and width ---
                float magnitude = fftBands[i] * renderer.getSensitivity();
                float height = magnitude * (1.0f + beatIntensity * 0.5f);
                height = Math.min(height, 0.95f);

                // Adaptive width: thicker when energy is high
                float energyFactor = Math.min(1f, magnitude * 3f);
                float halfWidth = baseHalfWidth * (0.7f + energyFactor * 0.3f);

                // Bar centre X coordinate
                float centreX = -1.0f + (i + 0.5f) * barWidth;
                float left = centreX - halfWidth;
                float right = centreX + halfWidth;

                // --- Morph between sharp peaks and smooth waves ---
                float topY, bottomY;
                if (morphFactor < 0.5f) {
                    // Sharp peaks (linear)
                    topY = height;
                    bottomY = -height;
                } else {
                    // Smooth waves (sine-based)
                    float waveFactor = (morphFactor - 0.5f) * 2f; // 0 to 1
                    topY = (float) (height * Math.sin(waveFactor * Math.PI / 2f));
                    bottomY = -topY;
                }

                // --- Vertices for the main bar ---
                float[] xCoords = { left, right, right, left, right, left };
                float[] yCoords = { bottomY, bottomY, topY, bottomY, topY, topY };
                float[] zCoords = { 0f, 0f, 0f, 0f, 0f, 0f };

                // --- Color calculation (frequency-based) ---
                float hue = (float) i / barCount; // 0 to 1 across bars
                // Low frequencies (left) = warm (red/orange), high frequencies (right) = cool (blue/purple)
                float baseHue = hue * 0.7f; // 0 to 0.7 (red to purple)
                float saturation = 0.8f + beatIntensity * 0.2f;
                float value = 0.7f + beatIntensity * 0.3f;

                // Add time-based rotation for dynamic colors
                baseHue = (baseHue + globalTime * 0.02f) % 1.0f;

                // --- Main bar color ---
                float[] barColor = hsvToRgb(baseHue, saturation, value);

                // Apply vertical gradient (brighter at center, darker at edges)
                for (int j = 0; j < 6; j++) {
                    float yPos = yCoords[j];
                    float t = Math.abs(yPos) / (Math.abs(topY - bottomY) + 0.001f);
                    float brightness = 1.0f - t * 0.4f + beatPulse * 0.3f;
                    brightness = Math.min(brightness, 1.0f);

                    // Add z-depth for 3D effect (center bars are slightly forward)
                    float z = (float) Math.sin(globalTime * 0.5f + i * 0.1f) * 0.02f;

                    vertexBuffer.put(xCoords[j]);
                    vertexBuffer.put(yCoords[j]);
                    vertexBuffer.put(z);

                    colorBuffer.put(barColor[0] * brightness);
                    colorBuffer.put(barColor[1] * brightness);
                    colorBuffer.put(barColor[2] * brightness);
                    colorBuffer.put(1.0f); // Alpha
                }

                // --- Motion blur trail ---
                if (TRAIL_LENGTH > 0) {
                    for (int t = 0; t < TRAIL_LENGTH; t++) {
                        float trailFactor = 1f - (float) t / TRAIL_LENGTH;
                        float trailHeight = prevHeights[i] * trailFactor;
                        float trailAlpha = 0.3f * trailFactor * (1f + beatPulse * 0.5f);

                        // Trail vertices (slightly smaller)
                        float trailLeft = centreX - halfWidth * 0.8f;
                        float trailRight = centreX + halfWidth * 0.8f;
                        float[] trailX = { trailLeft, trailRight, trailRight, trailLeft, trailRight, trailLeft };
                        float[] trailY = { -trailHeight, -trailHeight, trailHeight, -trailHeight, trailHeight, trailHeight };

                        // Trail color (darker version of main color)
                        float[] trailColor = new float[] {
                            prevColors[i * 4] * 0.7f,
                            prevColors[i * 4 + 1] * 0.7f,
                            prevColors[i * 4 + 2] * 0.7f,
                            trailAlpha
                        };

                        for (int j = 0; j < 6; j++) {
                            vertexBuffer.put(trailX[j]);
                            vertexBuffer.put(trailY[j]);
                            vertexBuffer.put(0f); // No depth for trails

                            colorBuffer.put(trailColor[0]);
                            colorBuffer.put(trailColor[1]);
                            colorBuffer.put(trailColor[2]);
                            colorBuffer.put(trailColor[3]);
                        }
                    }
                }

                // Store current height and color for next frame's trail
                prevHeights[i] = height;
                System.arraycopy(barColor, 0, prevColors, i * 4, 3);
                prevColors[i * 4 + 3] = 1.0f; // Alpha
            }

            vertexBuffer.flip();
            colorBuffer.flip();

            // Upload to GPU
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
            GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, vertexBuffer.remaining() * 4, vertexBuffer);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, colorVboId);
            GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, colorBuffer.remaining() * 4, colorBuffer);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        } catch (Exception e) {
            Log.e(TAG, "Error in update: " + e.getMessage(), e);
        }
    }

    private void drawCommon(float[] mvpMatrix, int program) {
        GLES20.glUseProgram(program);
        int posHandle = GLES20.glGetAttribLocation(program, "vPosition");
        int colHandle = GLES20.glGetAttribLocation(program, "aColor");
        int mvpHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");

        if (mvpHandle != -1) {
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0);
        }

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, 0);
        GLES20.glEnableVertexAttribArray(posHandle);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, colorVboId);
        GLES20.glVertexAttribPointer(colHandle, 4, GLES20.GL_FLOAT, false, 0, 0);
        GLES20.glEnableVertexAttribArray(colHandle);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        // Enable blending for alpha and glow effects
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        // Draw all vertices (bars + trails)
        int totalVerts = barCount * VERTS_PER_BAR * (1 + TRAIL_LENGTH);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, totalVerts);

        GLES20.glDisableVertexAttribArray(posHandle);
        GLES20.glDisableVertexAttribArray(colHandle);
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
            GLES20.glDeleteBuffers(2, new int[]{vboId, colorVboId}, 0);
            buffersInitialized = false;
        }
    }

    @Override
    public boolean usesTexture() {
        return false;
    }

    // --- Helper Methods ---
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
}

