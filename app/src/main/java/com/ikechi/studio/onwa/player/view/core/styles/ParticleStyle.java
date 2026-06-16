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
 * ParticleStyle - Breathing Rainbow Mandala
 * Features: Flowing mandala patterns that expand from and contract to center,
 *           intricate geometric petals, lotus layers, pulsing with audio
 */
public class ParticleStyle extends VisualStyle {
    private static final String TAG = "ParticleStyle";

    // === MANDALA CONFIGURATION - FULL INTRICATE SETTINGS ===
    private static final int MANDALA_LAYERS = 8;           // Concentric mandala layers
    private static final int PETALS_PER_LAYER = 12;        // Petals in each layer
    private static final int SUBDIVISIONS = 8;              // Smoothness of each petal
    private static final int LOTUS_PETALS = 16;             // Lotus flower petals
    private static final int SPIRAL_POINTS = 72;            // Flowing energy spiral

    // Animation parameters
    private static final float BREATHE_SPEED = 0.8f;
    private static final float ROTATION_SPEED = 0.3f;
    private static final float FLOW_SPEED = 0.5f;

    // Radius bounds
    private static final float RADIUS_CORE = 0.05f;
    private static final float RADIUS_MAX = 1.2f;

    // Audio reactivity
    private float smoothedBass = 0f;
    private float smoothedMid = 0f;
    private float smoothedTreble = 0f;
    private float beatIntensity = 0f;
    private int beatFrames = 0;

    // Mandala state
    private float globalTime = 0f;
    private float breathePhase = 0f;
    private float breatheAmount = 0.5f;
    private float rotationAngle = 0f;

    // Per-layer properties
    private float[] layerRadii = new float[MANDALA_LAYERS];
    private float[] layerSpeeds = new float[MANDALA_LAYERS];
    private float[] layerEnergies = new float[MANDALA_LAYERS];

    // Petal data
    private float[] petalAngles = new float[PETALS_PER_LAYER];

    // Buffers
    private FloatBuffer mandalaVertexBuffer;
    private ShortBuffer mandalaIndexBuffer;
    private FloatBuffer lotusVertexBuffer;
    private FloatBuffer spiralVertexBuffer;
    private FloatBuffer particleVertexBuffer;
    private FloatBuffer glowVertexBuffer;

    private int mandalaVboId, lotusVboId, spiralVboId, particleVboId, glowVboId;
    private int mandalaIboId;

    private boolean buffersInitialized = false;

    // Vertex counts
    private int mandalaVertexCount = 0;
    private int lotusVertexCount = 0;
    private int spiralVertexCount = 0;
    private int particleVertexCount = 0;
    private int glowVertexCount = 0;

    // Floating particles
    private static final int FLOATING_PARTICLES = 120;
    private float[] particleAngles = new float[FLOATING_PARTICLES];
    private float[] particleRadii = new float[FLOATING_PARTICLES];
    private float[] particleSpeeds = new float[FLOATING_PARTICLES];
    private float[] particlePhases = new float[FLOATING_PARTICLES];

    private Random random = new Random(42);

    @Override
    public void init(int shader2D, int shader3D, int shader3DUniform,
                     int texturedShader2D, int texturedShader3D,
                     GLVisualizerRenderer renderer) {
        super.init(shader2D, shader3D, shader3DUniform,
                   texturedShader2D, texturedShader3D, renderer);

        // Initialize mandala layers with golden ratio spacing
        float goldenRatio = 1.618033988749895f;
        for (int i = 0; i < MANDALA_LAYERS; i++) {
            float t = (float)(i + 1) / MANDALA_LAYERS;
            layerRadii[i] = RADIUS_CORE + (RADIUS_MAX - RADIUS_CORE) * (float)Math.pow(t, 0.7f);
            layerSpeeds[i] = 0.5f + (float)i / MANDALA_LAYERS * 1.5f;
            layerEnergies[i] = 0f;
        }

        // Initialize petal angles
        for (int i = 0; i < PETALS_PER_LAYER; i++) {
            petalAngles[i] = (float)(i * 2 * Math.PI / PETALS_PER_LAYER);
        }

        // Initialize floating particles
        for (int i = 0; i < FLOATING_PARTICLES; i++) {
            particleAngles[i] = random.nextFloat() * (float)(Math.PI * 2);
            particleRadii[i] = RADIUS_CORE + random.nextFloat() * RADIUS_MAX;
            particleSpeeds[i] = 0.2f + random.nextFloat() * 0.8f;
            particlePhases[i] = random.nextFloat() * (float)(Math.PI * 2);
        }

        allocateBuffers();
    }

    private void allocateBuffers() {
        // Delete old buffers if they exist
        if (buffersInitialized) {
            int[] vbos = new int[]{mandalaVboId, lotusVboId, spiralVboId, particleVboId, glowVboId};
            GLES20.glDeleteBuffers(5, vbos, 0);
            if (mandalaIboId != 0) {
                GLES20.glDeleteBuffers(1, new int[]{mandalaIboId}, 0);
            }
        }

        // Calculate vertex counts FIRST - using FULL configuration values
        // Mandala: each layer has PETALS_PER_LAYER petals, each petal has (SUBDIVISIONS + 1) * 2 vertices
        mandalaVertexCount = MANDALA_LAYERS * PETALS_PER_LAYER * (SUBDIVISIONS + 1) * 2;
        int mandalaFloatCount = mandalaVertexCount * 7;

        // Lotus: each petal has 9 segments * 2 vertices
        lotusVertexCount = LOTUS_PETALS * 9 * 2;
        int lotusFloatCount = lotusVertexCount * 7;

        // Spiral: each point is a quad (6 vertices)
        spiralVertexCount = SPIRAL_POINTS * 6;
        int spiralFloatCount = spiralVertexCount * 7;

        // Particles: each particle is a quad (6 vertices)
        particleVertexCount = FLOATING_PARTICLES * 6;
        int particleFloatCount = particleVertexCount * 7;

        // Glow core: 36 segments * 2 vertices (triangle strip)
        glowVertexCount = 37 * 2;
        int glowFloatCount = glowVertexCount * 7;

        Log.d(TAG, "Buffer sizes - Mandala: " + mandalaVertexCount + " vertices, " + mandalaFloatCount + " floats");
        Log.d(TAG, "Lotus: " + lotusVertexCount + " vertices, Spiral: " + spiralVertexCount + " vertices");
        Log.d(TAG, "Particles: " + particleVertexCount + " vertices, Glow: " + glowVertexCount + " vertices");

        // Allocate buffers
        ByteBuffer mvb = ByteBuffer.allocateDirect(mandalaFloatCount * 4);
        mvb.order(ByteOrder.nativeOrder());
        mandalaVertexBuffer = mvb.asFloatBuffer();

        ByteBuffer mib = ByteBuffer.allocateDirect(mandalaVertexCount * 2);
        mib.order(ByteOrder.nativeOrder());
        mandalaIndexBuffer = mib.asShortBuffer();

        // Build indices
        mandalaIndexBuffer.clear();
        for (int i = 0; i < mandalaVertexCount / 2; i++) {
            mandalaIndexBuffer.put((short)(i * 2));
            mandalaIndexBuffer.put((short)(i * 2 + 1));
        }
        mandalaIndexBuffer.flip();

        ByteBuffer lvb = ByteBuffer.allocateDirect(lotusFloatCount * 4);
        lvb.order(ByteOrder.nativeOrder());
        lotusVertexBuffer = lvb.asFloatBuffer();

        ByteBuffer svb = ByteBuffer.allocateDirect(spiralFloatCount * 4);
        svb.order(ByteOrder.nativeOrder());
        spiralVertexBuffer = svb.asFloatBuffer();

        ByteBuffer pvb = ByteBuffer.allocateDirect(particleFloatCount * 4);
        pvb.order(ByteOrder.nativeOrder());
        particleVertexBuffer = pvb.asFloatBuffer();

        ByteBuffer gvb = ByteBuffer.allocateDirect(glowFloatCount * 4);
        gvb.order(ByteOrder.nativeOrder());
        glowVertexBuffer = gvb.asFloatBuffer();

        // Generate VBOs
        int[] vbos = new int[5];
        GLES20.glGenBuffers(5, vbos, 0);
        mandalaVboId = vbos[0];
        lotusVboId = vbos[1];
        spiralVboId = vbos[2];
        particleVboId = vbos[3];
        glowVboId = vbos[4];

        int[] ibos = new int[1];
        GLES20.glGenBuffers(1, ibos, 0);
        mandalaIboId = ibos[0];

        // Initialize buffer data with CORRECT sizes
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mandalaVboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mandalaFloatCount * 4, null, GLES20.GL_DYNAMIC_DRAW);
        if (GLES20.glGetError() != 0) Log.e(TAG, "Error allocating mandala buffer");

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, lotusVboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, lotusFloatCount * 4, null, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, spiralVboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, spiralFloatCount * 4, null, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, particleVboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, particleFloatCount * 4, null, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, glowVboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, glowFloatCount * 4, null, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mandalaIboId);
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, mandalaVertexCount * 2, mandalaIndexBuffer, GLES20.GL_STATIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

        buffersInitialized = true;
        Log.d(TAG, "All buffers allocated successfully");
    }

    @Override
    public void update(float deltaTime, float[] fftBands, float beatIntensity, float totalTime) {
        if (fftBands == null || fftBands.length == 0) return;

        this.totalTime = totalTime;
        globalTime = totalTime;

        // Analyze audio
        analyzeAudio(fftBands, beatIntensity);

        // Update breathing phase - creates flow in/out effect
        breathePhase += deltaTime * BREATHE_SPEED * (1f + smoothedBass * 0.5f);
        breatheAmount = (float)(Math.sin(breathePhase) * 0.5f + 0.5f);

        // Add beat pulse
        if (beatFrames > 0) {
            breatheAmount += 0.2f * (beatFrames / 15f);
            breatheAmount = Math.min(1f, breatheAmount);
            beatFrames--;
        }

        // Update rotation
        rotationAngle += deltaTime * ROTATION_SPEED * (1f + smoothedBass);

        // Update layer energies from audio
        updateLayerEnergies(fftBands);

        // Generate all mandala components
        updateMandalaPetals();
        updateLotusCenter();
        updateSpiralFlow();
        updateFloatingParticles();
        updateGlowCore();
    }

    private void analyzeAudio(float[] fftBands, float beatIntensity) {
        if (fftBands.length == 0) return;

        int bassEnd = fftBands.length / 6;
        int midEnd = fftBands.length / 2;

        float bass = 0f, mid = 0f, treble = 0f;
        for (int i = 0; i < fftBands.length; i++) {
            float v = fftBands[i] * renderer.getSensitivity();
            if (i < bassEnd) bass += v * 1.5f;
            else if (i < midEnd) mid += v;
            else treble += v * 0.8f;
        }

        bass = Math.min(bass / bassEnd, 1.2f);
        mid = Math.min(mid / (midEnd - bassEnd), 1.0f);
        treble = Math.min(treble / (fftBands.length - midEnd), 0.8f);

        smoothedBass = lerp(smoothedBass, bass, 0.15f);
        smoothedMid = lerp(smoothedMid, mid, 0.12f);
        smoothedTreble = lerp(smoothedTreble, treble, 0.1f);
        this.beatIntensity = beatIntensity;

        if (beatIntensity > 0.5f && smoothedBass > 0.4f) {
            beatFrames = 10;
        }
    }

    private void updateLayerEnergies(float[] fftBands) {
        for (int i = 0; i < MANDALA_LAYERS; i++) {
            int fftIdx = (int)((float)i / MANDALA_LAYERS * fftBands.length);
            fftIdx = Math.min(fftIdx, fftBands.length - 1);
            float target = fftBands[fftIdx] * renderer.getSensitivity();
            layerEnergies[i] = lerp(layerEnergies[i], target, 0.12f);
        }
    }

    private void updateMandalaPetals() {
        mandalaVertexBuffer.clear();

        for (int layer = 0; layer < MANDALA_LAYERS; layer++) {
            float baseRadius = layerRadii[layer];
            float energy = layerEnergies[layer];

            // Breathing affects radius - flows OUT from center
            float breatheFactor = 0.3f + breatheAmount * 0.7f;
            float layerPulse = (float)Math.sin(globalTime * 2 + layer) * 0.05f * energy;
            float currentRadius = baseRadius * breatheFactor + layerPulse;

            float petalWidth = currentRadius * 0.15f * (0.5f + energy * 0.5f);

            for (int petal = 0; petal < PETALS_PER_LAYER; petal++) {
                float baseAngle = petalAngles[petal] + rotationAngle * layerSpeeds[layer];
                float hueBase = (float)layer / MANDALA_LAYERS + globalTime * 0.1f;

                for (int sub = 0; sub <= SUBDIVISIONS; sub++) {
                    float t = (float)sub / SUBDIVISIONS;

                    // Beautiful petal curve
                    float angleOffset = (float)Math.sin(t * Math.PI) * 0.8f;
                    float leftAngle = baseAngle - petalWidth * angleOffset;
                    float rightAngle = baseAngle + petalWidth * angleOffset;

                    float radiusFactor = (float)Math.sin(t * Math.PI);
                    float r = currentRadius * (0.2f + t * 0.8f);

                    float xLeft = (float)Math.cos(leftAngle) * r;
                    float yLeft = (float)Math.sin(leftAngle) * r;
                    float xRight = (float)Math.cos(rightAngle) * r;
                    float yRight = (float)Math.sin(rightAngle) * r;

                    // Rainbow color based on angle, layer, and time
                    float hue = (hueBase + (float)petal / PETALS_PER_LAYER + t * 0.3f) % 1.0f;
                    float sat = 0.7f + energy * 0.3f + (1f - t) * 0.2f;
                    float val = 0.6f + energy * 0.4f + (1f - t) * 0.2f;
                    float[] rgb = hsvToRgb(hue, sat, val);

                    float alpha = 0.5f + energy * 0.3f + (1f - t) * 0.2f;
                    float z = (float)Math.sin(globalTime * 3 + petal) * 0.03f * energy * (1f - t);

                    // Left edge vertex
                    mandalaVertexBuffer.put(xLeft);
                    mandalaVertexBuffer.put(yLeft);
                    mandalaVertexBuffer.put(z);
                    mandalaVertexBuffer.put(rgb[0]);
                    mandalaVertexBuffer.put(rgb[1]);
                    mandalaVertexBuffer.put(rgb[2]);
                    mandalaVertexBuffer.put(alpha);

                    // Right edge vertex
                    mandalaVertexBuffer.put(xRight);
                    mandalaVertexBuffer.put(yRight);
                    mandalaVertexBuffer.put(z);
                    mandalaVertexBuffer.put(rgb[0] * 0.8f);
                    mandalaVertexBuffer.put(rgb[1] * 0.8f);
                    mandalaVertexBuffer.put(rgb[2] * 0.9f);
                    mandalaVertexBuffer.put(alpha * 0.7f);
                }
            }
        }

        mandalaVertexBuffer.flip();

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mandalaVboId);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, mandalaVertexBuffer.remaining() * 4, mandalaVertexBuffer);
    }

    private void updateLotusCenter() {
        lotusVertexBuffer.clear();

        float coreRadius = 0.12f + smoothedBass * 0.05f + breatheAmount * 0.08f;

        for (int petal = 0; petal < LOTUS_PETALS; petal++) {
            float angle = (float)(petal * 2 * Math.PI / LOTUS_PETALS) + rotationAngle * 1.5f;
            float petalEnergy = (float)Math.sin(globalTime * 2 + petal) * 0.5f + 0.5f;

            for (int t = 0; t <= 8; t++) {
                float param = (float)t / 8f;
                float radiusFactor = (float)Math.sin(param * Math.PI);
                float r = coreRadius + radiusFactor * 0.12f * (0.5f + smoothedBass * 0.5f);

                float angleOffset = (float)Math.sin(param * Math.PI) * 0.3f;
                float leftAngle = angle - 0.12f - angleOffset * 0.5f;
                float rightAngle = angle + 0.12f + angleOffset * 0.5f;

                float xLeft = (float)Math.cos(leftAngle) * r;
                float yLeft = (float)Math.sin(leftAngle) * r;
                float xRight = (float)Math.cos(rightAngle) * r;
                float yRight = (float)Math.sin(rightAngle) * r;

                // Golden/pinkish to rainbow transition
                float hue = 0.05f + (float)petal / LOTUS_PETALS * 0.3f + globalTime * 0.05f;
                float sat = 0.8f + petalEnergy * 0.2f;
                float val = 0.9f + smoothedBass * 0.1f;
                float[] rgb = hsvToRgb(hue, sat, val);

                float alpha = 0.7f + petalEnergy * 0.3f;

                lotusVertexBuffer.put(xLeft);
                lotusVertexBuffer.put(yLeft);
                lotusVertexBuffer.put(0.02f);
                lotusVertexBuffer.put(rgb[0]);
                lotusVertexBuffer.put(rgb[1]);
                lotusVertexBuffer.put(rgb[2]);
                lotusVertexBuffer.put(alpha);

                lotusVertexBuffer.put(xRight);
                lotusVertexBuffer.put(yRight);
                lotusVertexBuffer.put(0.02f);
                lotusVertexBuffer.put(rgb[0] * 0.7f);
                lotusVertexBuffer.put(rgb[1] * 0.6f);
                lotusVertexBuffer.put(rgb[2] * 0.8f);
                lotusVertexBuffer.put(alpha * 0.6f);
            }
        }

        lotusVertexBuffer.flip();

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, lotusVboId);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, lotusVertexBuffer.remaining() * 4, lotusVertexBuffer);
    }

    private void updateSpiralFlow() {
        spiralVertexBuffer.clear();

        // Energy spiral that flows outward/inward with breath
        float flowDirection = (float)Math.sin(breathePhase) * 0.5f + 0.5f;

        for (int i = 0; i < SPIRAL_POINTS; i++) {
            float t = (float)i / SPIRAL_POINTS;
            float angle = t * (float)(Math.PI * 8) + globalTime * FLOW_SPEED;

            float radius;
            if (flowDirection > 0.5f) {
                // Expanding outward
                radius = RADIUS_CORE + t * (RADIUS_MAX - RADIUS_CORE) * breatheAmount;
            } else {
                // Contracting inward
                radius = RADIUS_MAX - t * (RADIUS_MAX - RADIUS_CORE) * (1f - breatheAmount);
            }

            float x = (float)Math.cos(angle) * radius;
            float y = (float)Math.sin(angle) * radius;
            float z = (float)Math.sin(globalTime * 5 + angle * 2) * 0.05f;

            // Rainbow flowing colors
            float hue = (t * 2 + globalTime * 0.2f) % 1.0f;
            float sat = 0.9f;
            float val = 0.8f + (float)Math.sin(globalTime * 4 + i) * 0.2f;
            float[] rgb = hsvToRgb(hue, sat, val);

            float size = 0.006f * (1f + smoothedBass);
            float alpha = 0.6f;

            // Quad as two triangles
            // Triangle 1
            spiralVertexBuffer.put(x - size);
            spiralVertexBuffer.put(y - size);
            spiralVertexBuffer.put(z);
            spiralVertexBuffer.put(rgb[0]);
            spiralVertexBuffer.put(rgb[1]);
            spiralVertexBuffer.put(rgb[2]);
            spiralVertexBuffer.put(alpha);

            spiralVertexBuffer.put(x + size);
            spiralVertexBuffer.put(y - size);
            spiralVertexBuffer.put(z);
            spiralVertexBuffer.put(rgb[0]);
            spiralVertexBuffer.put(rgb[1]);
            spiralVertexBuffer.put(rgb[2]);
            spiralVertexBuffer.put(alpha);

            spiralVertexBuffer.put(x - size);
            spiralVertexBuffer.put(y + size);
            spiralVertexBuffer.put(z);
            spiralVertexBuffer.put(rgb[0]);
            spiralVertexBuffer.put(rgb[1]);
            spiralVertexBuffer.put(rgb[2]);
            spiralVertexBuffer.put(alpha);

            // Triangle 2
            spiralVertexBuffer.put(x + size);
            spiralVertexBuffer.put(y - size);
            spiralVertexBuffer.put(z);
            spiralVertexBuffer.put(rgb[0]);
            spiralVertexBuffer.put(rgb[1]);
            spiralVertexBuffer.put(rgb[2]);
            spiralVertexBuffer.put(alpha);

            spiralVertexBuffer.put(x + size);
            spiralVertexBuffer.put(y + size);
            spiralVertexBuffer.put(z);
            spiralVertexBuffer.put(rgb[0]);
            spiralVertexBuffer.put(rgb[1]);
            spiralVertexBuffer.put(rgb[2]);
            spiralVertexBuffer.put(alpha);

            spiralVertexBuffer.put(x - size);
            spiralVertexBuffer.put(y + size);
            spiralVertexBuffer.put(z);
            spiralVertexBuffer.put(rgb[0]);
            spiralVertexBuffer.put(rgb[1]);
            spiralVertexBuffer.put(rgb[2]);
            spiralVertexBuffer.put(alpha);
        }

        spiralVertexBuffer.flip();

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, spiralVboId);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, spiralVertexBuffer.remaining() * 4, spiralVertexBuffer);
    }

    private void updateFloatingParticles() {
        particleVertexBuffer.clear();

        for (int i = 0; i < FLOATING_PARTICLES; i++) {
            // Update particle positions
            particleAngles[i] += 0.02f * particleSpeeds[i] * (1f + smoothedBass);
            float radius = particleRadii[i] * (0.3f + breatheAmount * 0.7f);
            radius += (float)Math.sin(globalTime * 2 + particlePhases[i]) * 0.02f;

            float x = (float)Math.cos(particleAngles[i]) * radius;
            float y = (float)Math.sin(particleAngles[i]) * radius;
            float z = (float)Math.sin(globalTime * 3 + particlePhases[i]) * 0.08f;

            // Rainbow colors
            float hue = (radius * 2 + globalTime * 0.15f) % 1.0f;
            float[] rgb = hsvToRgb(hue, 0.85f, 0.9f);

            float size = 0.004f + (float)Math.sin(globalTime * 5 + i) * 0.002f + smoothedBass * 0.003f;
            float alpha = 0.5f + (float)Math.sin(globalTime * 3 + i) * 0.2f;

            // Quad as two triangles
            // Triangle 1
            particleVertexBuffer.put(x - size);
            particleVertexBuffer.put(y - size);
            particleVertexBuffer.put(z);
            particleVertexBuffer.put(rgb[0]);
            particleVertexBuffer.put(rgb[1]);
            particleVertexBuffer.put(rgb[2]);
            particleVertexBuffer.put(alpha);

            particleVertexBuffer.put(x + size);
            particleVertexBuffer.put(y - size);
            particleVertexBuffer.put(z);
            particleVertexBuffer.put(rgb[0]);
            particleVertexBuffer.put(rgb[1]);
            particleVertexBuffer.put(rgb[2]);
            particleVertexBuffer.put(alpha);

            particleVertexBuffer.put(x - size);
            particleVertexBuffer.put(y + size);
            particleVertexBuffer.put(z);
            particleVertexBuffer.put(rgb[0]);
            particleVertexBuffer.put(rgb[1]);
            particleVertexBuffer.put(rgb[2]);
            particleVertexBuffer.put(alpha);

            // Triangle 2
            particleVertexBuffer.put(x + size);
            particleVertexBuffer.put(y - size);
            particleVertexBuffer.put(z);
            particleVertexBuffer.put(rgb[0]);
            particleVertexBuffer.put(rgb[1]);
            particleVertexBuffer.put(rgb[2]);
            particleVertexBuffer.put(alpha);

            particleVertexBuffer.put(x + size);
            particleVertexBuffer.put(y + size);
            particleVertexBuffer.put(z);
            particleVertexBuffer.put(rgb[0]);
            particleVertexBuffer.put(rgb[1]);
            particleVertexBuffer.put(rgb[2]);
            particleVertexBuffer.put(alpha);

            particleVertexBuffer.put(x - size);
            particleVertexBuffer.put(y + size);
            particleVertexBuffer.put(z);
            particleVertexBuffer.put(rgb[0]);
            particleVertexBuffer.put(rgb[1]);
            particleVertexBuffer.put(rgb[2]);
            particleVertexBuffer.put(alpha);
        }

        particleVertexBuffer.flip();

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, particleVboId);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, particleVertexBuffer.remaining() * 4, particleVertexBuffer);
    }

    private void updateGlowCore() {
        glowVertexBuffer.clear();

        float coreSize = 0.06f + smoothedBass * 0.04f + beatFrames * 0.003f;
        float pulse = (float)Math.sin(globalTime * 8) * 0.01f;
        coreSize += pulse;

        int segments = 36;
        for (int i = 0; i <= segments; i++) {
            float angle = (float)(i * 2 * Math.PI / segments);
            float x = (float)Math.cos(angle) * coreSize;
            float y = (float)Math.sin(angle) * coreSize;

            // Rainbow or white color
            float[] rgb;
            if (i % 3 == 0) {
                rgb = hsvToRgb(globalTime * 0.2f % 1.0f, 0.9f, 1.0f);
            } else {
                rgb = new float[]{1.0f, 1.0f, 1.0f};
            }

            float alpha = 0.9f + (float)Math.sin(globalTime * 10) * 0.1f;

            // Outer vertex
            glowVertexBuffer.put(x);
            glowVertexBuffer.put(y);
            glowVertexBuffer.put(0.05f);
            glowVertexBuffer.put(rgb[0]);
            glowVertexBuffer.put(rgb[1]);
            glowVertexBuffer.put(rgb[2]);
            glowVertexBuffer.put(alpha);

            // Inner vertex
            float xi = (float)Math.cos(angle) * (coreSize * 0.5f);
            float yi = (float)Math.sin(angle) * (coreSize * 0.5f);

            glowVertexBuffer.put(xi);
            glowVertexBuffer.put(yi);
            glowVertexBuffer.put(0.05f);
            glowVertexBuffer.put(1.0f);
            glowVertexBuffer.put(0.9f);
            glowVertexBuffer.put(0.5f);
            glowVertexBuffer.put(0.8f);
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

        if (posHandle == -1) {
            Log.e(TAG, "Could not get vPosition handle");
            return;
        }

        if (mvpHandle != -1) {
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0);
        }

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);

        int stride = 7 * 4;

        // Draw glow core first (additive blending for brightness)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, glowVboId);
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, stride, 0);
        GLES20.glEnableVertexAttribArray(posHandle);
        if (colHandle != -1) {
            GLES20.glVertexAttribPointer(colHandle, 4, GLES20.GL_FLOAT, false, stride, 3 * 4);
            GLES20.glEnableVertexAttribArray(colHandle);
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, glowVertexCount);

        // Draw lotus center
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, lotusVboId);
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, stride, 0);
        if (colHandle != -1) {
            GLES20.glVertexAttribPointer(colHandle, 4, GLES20.GL_FLOAT, false, stride, 3 * 4);
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, lotusVertexCount);

        // Draw mandala petals
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mandalaVboId);
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, stride, 0);
        if (colHandle != -1) {
            GLES20.glVertexAttribPointer(colHandle, 4, GLES20.GL_FLOAT, false, stride, 3 * 4);
        }
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mandalaIboId);
        GLES20.glDrawElements(GLES20.GL_TRIANGLE_STRIP, mandalaVertexCount, GLES20.GL_UNSIGNED_SHORT, 0);

        // Draw spiral flow (additive for energy feel)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, spiralVboId);
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, stride, 0);
        if (colHandle != -1) {
            GLES20.glVertexAttribPointer(colHandle, 4, GLES20.GL_FLOAT, false, stride, 3 * 4);
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, spiralVertexCount);

        // Draw floating particles
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, particleVboId);
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, stride, 0);
        if (colHandle != -1) {
            GLES20.glVertexAttribPointer(colHandle, 4, GLES20.GL_FLOAT, false, stride, 3 * 4);
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, particleVertexCount);

        // Cleanup
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
            int[] vbos = new int[]{mandalaVboId, lotusVboId, spiralVboId, particleVboId, glowVboId};
            GLES20.glDeleteBuffers(5, vbos, 0);
            if (mandalaIboId != 0) {
                GLES20.glDeleteBuffers(1, new int[]{mandalaIboId}, 0);
            }
            buffersInitialized = false;
        }
    }

    @Override
    public boolean usesTexture() {
        return false;
    }
}
