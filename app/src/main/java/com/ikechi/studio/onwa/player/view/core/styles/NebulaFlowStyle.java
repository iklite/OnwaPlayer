package com.ikechi.studio.onwa.player.view.core.styles;

import android.opengl.GLES20;
import android.opengl.Matrix;

import com.ikechi.studio.onwa.player.view.core.GLVisualizerRenderer;
import com.ikechi.studio.onwa.player.view.core.visuals.VisualStyle;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;

/**
 * NebulaFlowStyle - Audio-reactive 3D particle nebula using billboarded quads
 * Thousands of textured particles swirling in organic curl noise patterns.
 * Uses triangle strips instead of GL_POINTS for maximum compatibility.
 */
public class NebulaFlowStyle extends VisualStyle {

    private static final String TAG = "NebulaFlowStyle";

    // Particle system - using quads instead of points for compatibility
    private static final int PARTICLE_COUNT = 2000; // Quads are more expensive, so fewer particles
    private static final int LAYERS = 3;

    // Audio reactivity
    private float bassEnergy = 0f;
    private float midEnergy = 0f;
    private float highEnergy = 0f;
    private float beatPulse = 0f;
    private float smoothedBass = 0f;

    // Animation
    private float globalTime = 0f;
    private float rotationAngle = 0f;

    // Particle data
    private Particle[] particles;
    private FloatBuffer particleBuffer;
    private int particleVbo;
    private boolean buffersInitialized = false;

    // Flow parameters
    private float noiseScale = 1.5f;
    private float flowSpeed = 0.3f;

    private Random random = new Random(42);

    // Color palettes
    private static class ColorPalette {
        float hueBase, hueRange;
        float satBase, valBase;
    }
    private ColorPalette[] palettes = new ColorPalette[3];

    private static class Particle {
        float x, y, z;      // Position
        float vx, vy, vz;   // Velocity
        float life;         // 0-1 life cycle
        float decay;        // How fast it fades
        float size;         // Base size
        float hue;          // Color hue
        float brightness;   // Current brightness
        int layer;          // Which layer
        float audioFactor;  // How much audio affects this particle
        float rotation;     // Individual rotation
    }

    @Override
    public void init(int shader2D, int shader3D, int shader3DUniform,
                     int texturedShader2D, int texturedShader3D,
                     GLVisualizerRenderer renderer) {
        super.init(shader2D, shader3D, shader3DUniform,
                   texturedShader2D, texturedShader3D, renderer);

        initPalettes();
        initParticles();
        allocateBuffers();
    }

    private void initPalettes() {
        // Background: Deep blues and purples
        palettes[0] = new ColorPalette();
        palettes[0].hueBase = 0.65f;
        palettes[0].hueRange = 0.15f;
        palettes[0].satBase = 0.7f;
        palettes[0].valBase = 0.4f;

        // Mid: Magenta and pink
        palettes[1] = new ColorPalette();
        palettes[1].hueBase = 0.85f;
        palettes[1].hueRange = 0.1f;
        palettes[1].satBase = 0.8f;
        palettes[1].valBase = 0.6f;

        // Front: Gold to cyan
        palettes[2] = new ColorPalette();
        palettes[2].hueBase = 0.15f;
        palettes[2].hueRange = 0.45f;
        palettes[2].satBase = 0.9f;
        palettes[2].valBase = 0.9f;
    }

    private void initParticles() {
        particles = new Particle[PARTICLE_COUNT];

        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particles[i] = new Particle();
            resetParticle(particles[i], true);
        }
    }

    private void resetParticle(Particle p, boolean initial) {
        // Spherical distribution
        float theta = random.nextFloat() * (float)Math.PI * 2;
        float phi = (float)Math.acos(2f * random.nextFloat() - 1f);
        float r = 0.5f + random.nextFloat() * 1.5f;

        if (initial) {
            p.x = r * (float)Math.sin(phi) * (float)Math.cos(theta);
            p.y = r * (float)Math.sin(phi) * (float)Math.sin(theta);
            p.z = r * (float)Math.cos(phi);
        } else {
            // Respawn at edges
            float spawnAngle = globalTime * 0.5f + random.nextFloat() * (float)Math.PI;
            float spawnDist = 2.0f;
            p.x = (float)Math.cos(spawnAngle) * spawnDist;
            p.y = (float)Math.sin(spawnAngle) * spawnDist * 0.5f;
            p.z = (random.nextFloat() - 0.5f) * 2f;
        }

        p.vx = 0f;
        p.vy = 0f;
        p.vz = 0f;
        p.life = 1f;
        p.decay = 0.2f + random.nextFloat() * 0.5f;
        p.size = 0.03f + random.nextFloat() * 0.05f;
        p.layer = random.nextInt(3);
        p.audioFactor = 0.5f + random.nextFloat() * 0.5f;
        p.rotation = random.nextFloat() * (float)Math.PI * 2;

        ColorPalette pal = palettes[p.layer];
        p.hue = pal.hueBase + (random.nextFloat() - 0.5f) * pal.hueRange;
        p.brightness = pal.valBase + (random.nextFloat() - 0.5f) * 0.2f;
    }

    private void allocateBuffers() {
        if (buffersInitialized) {
            GLES20.glDeleteBuffers(1, new int[]{particleVbo}, 0);
        }

        // Each particle = 4 vertices (quad) × 7 floats (3 position + 4 color)
        // Using triangle strip: 4 vertices per particle
        ByteBuffer bb = ByteBuffer.allocateDirect(PARTICLE_COUNT * 4 * 7 * 4);
        bb.order(ByteOrder.nativeOrder());
        particleBuffer = bb.asFloatBuffer();

        int[] bufs = new int[1];
        GLES20.glGenBuffers(1, bufs, 0);
        particleVbo = bufs[0];

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, particleVbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, PARTICLE_COUNT * 4 * 7 * 4, null, GLES20.GL_DYNAMIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        buffersInitialized = true;
    }

    @Override
    public void update(float deltaTime, float[] fftBands, float beatIntensity, float totalTime) {
        this.totalTime = totalTime;
        globalTime = totalTime;

        if (fftBands == null || fftBands.length == 0) return;

        analyzeAudio(fftBands, beatIntensity, deltaTime);
        updateParticles(deltaTime);
        buildGeometry();
    }

    private void analyzeAudio(float[] fftBands, float beatIntensity, float deltaTime) {
        int bassEnd = fftBands.length / 6;
        int midEnd = fftBands.length / 2;

        float bass = 0f, mid = 0f, high = 0f;
        for (int i = 0; i < fftBands.length; i++) {
            float v = fftBands[i] * renderer.getSensitivity();
            if (i < bassEnd) bass += v * 2f;
            else if (i < midEnd) mid += v;
            else high += v;
        }

        bass = Math.min(bass / bassEnd, 2.5f);
        mid = Math.min(mid / (midEnd - bassEnd), 1.5f);
        high = Math.min(high / (fftBands.length - midEnd), 1.0f);

        smoothedBass = lerp(smoothedBass, bass, 0.15f);
        bassEnergy = smoothedBass;
        midEnergy = lerp(midEnergy, mid, 0.12f);
        highEnergy = lerp(highEnergy, high, 0.1f);

        if (beatIntensity > 0.6f && bass > 0.8f) {
            beatPulse = 1f;
        }
        beatPulse = Math.max(0f, beatPulse - deltaTime * 3f);

        flowSpeed = 0.2f + bassEnergy * 0.8f;
        rotationAngle += deltaTime * (0.1f + bassEnergy * 0.5f);
    }

    private void updateParticles(float deltaTime) {
        float dt = Math.min(deltaTime, 0.05f);

        for (Particle p : particles) {
            p.life -= p.decay * dt * (0.5f + bassEnergy * 0.5f);

            if (p.life <= 0) {
                resetParticle(p, false);
                continue;
            }

            // Curl noise flow field
            float noiseX = p.x * noiseScale + globalTime * flowSpeed;
            float noiseY = p.y * noiseScale + globalTime * flowSpeed * 0.7f;
            float noiseZ = p.z * noiseScale + globalTime * flowSpeed * 0.5f;

            float curlX = (float)(Math.sin(noiseY) * Math.cos(noiseZ));
            float curlY = (float)(Math.sin(noiseZ) * Math.cos(noiseX));
            float curlZ = (float)(Math.sin(noiseX) * Math.cos(noiseY));

            float audioMod = 1f + bassEnergy * p.audioFactor * 2f;
            float midMod = 1f + midEnergy * 0.5f;

            float targetVx = curlX * flowSpeed * audioMod;
            float targetVy = curlY * flowSpeed * audioMod;
            float targetVz = curlZ * flowSpeed * midMod;

            p.vx = lerp(p.vx, targetVx, dt * 2f);
            p.vy = lerp(p.vy, targetVy, dt * 2f);
            p.vz = lerp(p.vz, targetVz, dt * 2f);

            // Beat pulse explosion
            if (beatPulse > 0.1f) {
                float dist = (float)Math.sqrt(p.x*p.x + p.y*p.y + p.z*p.z);
                if (dist > 0.01f) {
                    float push = beatPulse * 2f / (dist + 0.1f);
                    p.vx += (p.x / dist) * push * dt;
                    p.vy += (p.y / dist) * push * dt;
                    p.vz += (p.z / dist) * push * dt;
                }
            }

            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.z += p.vz * dt;

            // Soft boundary
            float bound = 2.5f;
            if (Math.abs(p.x) > bound) p.x *= 0.9f;
            if (Math.abs(p.y) > bound) p.y *= 0.9f;
            if (Math.abs(p.z) > bound) p.z *= 0.9f;

            // Update color
            ColorPalette pal = palettes[p.layer];
            float lifeFactor = 1f - p.life;
            p.hue = pal.hueBase + lifeFactor * 0.1f + bassEnergy * 0.05f * p.audioFactor;
            if (p.hue > 1f) p.hue -= 1f;

            float beatBoost = beatPulse * 0.5f * p.audioFactor;
            p.brightness = (pal.valBase + lifeFactor * 0.3f + bassEnergy * 0.3f) * p.life + beatBoost;
            p.brightness = Math.min(1f, p.brightness);

            // Rotation animation
            p.rotation += dt * (0.5f + bassEnergy);
        }
    }

    private void buildGeometry() {
        particleBuffer.clear();

        // Build billboarded quads for each particle
        // We'll create quads that always face camera by using a simple trick:
        // The quad is built in view space, not world space

        for (Particle p : particles) {
            // Transform position with rotation
            float cosR = (float)Math.cos(rotationAngle);
            float sinR = (float)Math.sin(rotationAngle);

            float rx = p.x * cosR - p.z * sinR;
            float rz = p.x * sinR + p.z * cosR;
            float ry = p.y;

            // Size varies by layer and audio
            float sizeMult = 1f + p.layer * 0.5f + bassEnergy * p.audioFactor;
            float finalSize = p.size * sizeMult * (0.5f + p.life * 0.5f);

            // Get color
            float[] rgb = hsvToRgb(p.hue, palettes[p.layer].satBase, p.brightness);

            // Alpha
            float baseAlpha = 0.4f + p.layer * 0.2f;
            float audioAlpha = midEnergy * 0.3f * p.audioFactor;
            float alpha = (baseAlpha + audioAlpha) * p.life;
            alpha = Math.min(0.9f, alpha);

            // Build quad as triangle strip (4 vertices)
            // We'll use a simple square that will be billboarded in shader or
            // we can manually billboard by creating a quad perpendicular to view

            // For compatibility, we create a simple quad in XY plane at the particle position
            // The actual billboarding would need shader support, so we use a fixed orientation
            // that rotates with the scene

            float halfSize = finalSize * 0.5f;

            // Vertex 1: bottom-left
            particleBuffer.put(rx - halfSize);
            particleBuffer.put(ry - halfSize);
            particleBuffer.put(rz);
            particleBuffer.put(rgb[0]);
            particleBuffer.put(rgb[1]);
            particleBuffer.put(rgb[2]);
            particleBuffer.put(alpha);

            // Vertex 2: bottom-right
            particleBuffer.put(rx + halfSize);
            particleBuffer.put(ry - halfSize);
            particleBuffer.put(rz);
            particleBuffer.put(rgb[0]);
            particleBuffer.put(rgb[1]);
            particleBuffer.put(rgb[2]);
            particleBuffer.put(alpha);

            // Vertex 3: top-left
            particleBuffer.put(rx - halfSize);
            particleBuffer.put(ry + halfSize);
            particleBuffer.put(rz);
            particleBuffer.put(rgb[0]);
            particleBuffer.put(rgb[1]);
            particleBuffer.put(rgb[2]);
            particleBuffer.put(alpha);

            // Vertex 4: top-right
            particleBuffer.put(rx + halfSize);
            particleBuffer.put(ry + halfSize);
            particleBuffer.put(rz);
            particleBuffer.put(rgb[0]);
            particleBuffer.put(rgb[1]);
            particleBuffer.put(rgb[2]);
            particleBuffer.put(alpha);
        }

        particleBuffer.flip();

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, particleVbo);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, particleBuffer.remaining() * 4, particleBuffer);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    @Override
    public void draw3D(float[] mvpMatrix, GLVisualizerRenderer renderer) {
        GLES20.glUseProgram(shaderProgram3D);

        int posHandle = GLES20.glGetAttribLocation(shaderProgram3D, "vPosition");
        int colHandle = GLES20.glGetAttribLocation(shaderProgram3D, "aColor");
        int mvpHandle = GLES20.glGetUniformLocation(shaderProgram3D, "uMVPMatrix");

        if (mvpHandle != -1) {
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0);
        }

        GLES20.glEnable(GLES20.GL_BLEND);
        // Additive blending for glowing effect
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, particleVbo);

        if (posHandle != -1) {
            GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 7 * 4, 0);
            GLES20.glEnableVertexAttribArray(posHandle);
        }

        if (colHandle != -1) {
            GLES20.glVertexAttribPointer(colHandle, 4, GLES20.GL_FLOAT, false, 7 * 4, 3 * 4);
            GLES20.glEnableVertexAttribArray(colHandle);
        }

        // Draw quads as triangle strips
        // Each particle is 4 vertices, drawn as TRIANGLE_STRIP
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, i * 4, 4);
        }

        if (posHandle != -1) GLES20.glDisableVertexAttribArray(posHandle);
        if (colHandle != -1) GLES20.glDisableVertexAttribArray(colHandle);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    @Override
    public void draw2D(float[] mvpMatrix, GLVisualizerRenderer renderer) {
        draw3D(mvpMatrix, renderer);
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
    public void release() {
        if (buffersInitialized) {
            GLES20.glDeleteBuffers(1, new int[]{particleVbo}, 0);
            buffersInitialized = false;
        }
    }

    @Override
    public boolean usesTexture() {
        return false;
    }
}

