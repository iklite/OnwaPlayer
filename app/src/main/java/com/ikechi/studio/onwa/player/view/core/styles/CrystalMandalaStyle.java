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
 * CrystalMandalaStyle - Geometric Holographic Mandala
 * Features: Sharp polygons, counter-rotating layers, laser beams,
 *           chromatic aberration, pulse waves traveling along geometry
 */
public class CrystalMandalaStyle extends VisualStyle {
    private static final String TAG = "CrystalMandalaStyle";

    // === GEOMETRIC CONFIGURATION ===
    private static final int HEX_LAYERS = 6;
    private static final int HEX_POINTS = 6;
    private static final int TRIANGLE_LAYERS = 8;
    private static final int DIAMOND_COUNT = 12;
    private static final int BEAM_COUNT = 36;
    private static final int WIREFRAME_SEGMENTS = 72;

    // Animation parameters
    private static final float ROTATION_SPEED_LAYER1 = 0.5f;
    private static final float ROTATION_SPEED_LAYER2 = -0.3f;
    private static final float ROTATION_SPEED_LAYER3 = 0.2f;
    private static final float PULSE_SPEED = 2.5f;

    // Radius bounds
    private static final float RADIUS_MIN = 0.1f;
    private static final float RADIUS_MAX = 1.15f;

    // Audio reactivity
    private float smoothedBass = 0f;
    private float smoothedMid = 0f;
    private float beatPulse = 0f;
    private int beatFrames = 0;
    private float pulseWave = 0f;

    // Animation state
    private float globalTime = 0f;
    private float rotation1 = 0f;
    private float rotation2 = 0f;
    private float rotation3 = 0f;

    // Layer radii
    private float[] hexRadii = new float[HEX_LAYERS];
    private float[] triangleRadii = new float[TRIANGLE_LAYERS];
    private float[] layerEnergies = new float[HEX_LAYERS];

    // Buffers
    private FloatBuffer hexVertexBuffer;
    private ShortBuffer hexIndexBuffer;
    private FloatBuffer triangleVertexBuffer;
    private ShortBuffer triangleIndexBuffer;
    private FloatBuffer diamondVertexBuffer;
    private FloatBuffer beamVertexBuffer;
    private FloatBuffer wireframeVertexBuffer;
    private FloatBuffer glowVertexBuffer;

    private int hexVboId, triangleVboId, diamondVboId, beamVboId, wireframeVboId, glowVboId;
    private int hexIboId, triangleIboId;

    private boolean buffersInitialized = false;

    // Vertex counts
    private int hexVertexCount = 0;
    private int triangleVertexCount = 0;
    private int diamondVertexCount = 0;
    private int beamVertexCount = 0;
    private int wireframeVertexCount = 0;
    private int glowVertexCount = 0;
    private int hexIndexCount = 0;
    private int triangleIndexCount = 0;

    private Random random = new Random(42);

    @Override
    public void init(int shader2D, int shader3D, int shader3DUniform,
                     int texturedShader2D, int texturedShader3D,
                     GLVisualizerRenderer renderer) {
        super.init(shader2D, shader3D, shader3DUniform,
                   texturedShader2D, texturedShader3D, renderer);

        // Initialize radii
        for (int i = 0; i < HEX_LAYERS; i++) {
            float t = (float)(i + 1) / HEX_LAYERS;
            hexRadii[i] = RADIUS_MIN + (RADIUS_MAX - RADIUS_MIN) * t;
            layerEnergies[i] = 0f;
        }

        for (int i = 0; i < TRIANGLE_LAYERS; i++) {
            float t = (float)(i + 1) / TRIANGLE_LAYERS;
            triangleRadii[i] = RADIUS_MIN + (RADIUS_MAX - RADIUS_MIN) * t * 0.8f;
        }

        allocateBuffers();
    }

    private void allocateBuffers() {
        if (buffersInitialized) {
            int[] vbos = new int[]{hexVboId, triangleVboId, diamondVboId, beamVboId, wireframeVboId, glowVboId};
            GLES20.glDeleteBuffers(6, vbos, 0);
            if (hexIboId != 0) GLES20.glDeleteBuffers(1, new int[]{hexIboId}, 0);
            if (triangleIboId != 0) GLES20.glDeleteBuffers(1, new int[]{triangleIboId}, 0);
        }

        // Calculate vertex counts
        hexVertexCount = HEX_LAYERS * HEX_POINTS * 2;
        int hexFloatCount = hexVertexCount * 7;

        triangleVertexCount = TRIANGLE_LAYERS * 3 * 2;
        int triangleFloatCount = triangleVertexCount * 7;

        diamondVertexCount = DIAMOND_COUNT * 6;
        int diamondFloatCount = diamondVertexCount * 7;

        beamVertexCount = BEAM_COUNT * 2;
        int beamFloatCount = beamVertexCount * 7;

        wireframeVertexCount = WIREFRAME_SEGMENTS * 2;
        int wireframeFloatCount = wireframeVertexCount * 7;

        glowVertexCount = 48;
        int glowFloatCount = glowVertexCount * 7;

        // Calculate index counts correctly
        // For hexagons: each layer has HEX_POINTS * 2 lines (outer and inner separately)
        hexIndexCount = HEX_LAYERS * HEX_POINTS * 4;  // 4 indices per edge (2 lines * 2 vertices each)

        // For triangles: each layer has 3 edges * 2 vertices * 2 (for inner/outer)
        triangleIndexCount = TRIANGLE_LAYERS * 3 * 4;  // 4 indices per triangle edge

        // Allocate vertex buffers
        ByteBuffer hvb = ByteBuffer.allocateDirect(hexFloatCount * 4);
        hvb.order(ByteOrder.nativeOrder());
        hexVertexBuffer = hvb.asFloatBuffer();

        ByteBuffer hib = ByteBuffer.allocateDirect(hexIndexCount * 2);
        hib.order(ByteOrder.nativeOrder());
        hexIndexBuffer = hib.asShortBuffer();

        // Build hexagon indices correctly
        hexIndexBuffer.clear();
        for (int layer = 0; layer < HEX_LAYERS; layer++) {
            int base = layer * HEX_POINTS * 2;
            for (int p = 0; p < HEX_POINTS; p++) {
                int next = (p + 1) % HEX_POINTS;
                // Outer edge
                hexIndexBuffer.put((short)(base + p * 2));
                hexIndexBuffer.put((short)(base + next * 2));
                // Inner edge
                hexIndexBuffer.put((short)(base + p * 2 + 1));
                hexIndexBuffer.put((short)(base + next * 2 + 1));
            }
        }
        hexIndexBuffer.flip();

        ByteBuffer tvb = ByteBuffer.allocateDirect(triangleFloatCount * 4);
        tvb.order(ByteOrder.nativeOrder());
        triangleVertexBuffer = tvb.asFloatBuffer();

        ByteBuffer tib = ByteBuffer.allocateDirect(triangleIndexCount * 2);
        tib.order(ByteOrder.nativeOrder());
        triangleIndexBuffer = tib.asShortBuffer();

        // Build triangle indices
        triangleIndexBuffer.clear();
        for (int layer = 0; layer < TRIANGLE_LAYERS; layer++) {
            int base = layer * 3 * 2;
            for (int p = 0; p < 3; p++) {
                int next = (p + 1) % 3;
                // Outer edge
                triangleIndexBuffer.put((short)(base + p * 2));
                triangleIndexBuffer.put((short)(base + next * 2));
                // Inner edge
                triangleIndexBuffer.put((short)(base + p * 2 + 1));
                triangleIndexBuffer.put((short)(base + next * 2 + 1));
            }
        }
        triangleIndexBuffer.flip();

        ByteBuffer dvb = ByteBuffer.allocateDirect(diamondFloatCount * 4);
        dvb.order(ByteOrder.nativeOrder());
        diamondVertexBuffer = dvb.asFloatBuffer();

        ByteBuffer bvb = ByteBuffer.allocateDirect(beamFloatCount * 4);
        bvb.order(ByteOrder.nativeOrder());
        beamVertexBuffer = bvb.asFloatBuffer();

        ByteBuffer wvb = ByteBuffer.allocateDirect(wireframeFloatCount * 4);
        wvb.order(ByteOrder.nativeOrder());
        wireframeVertexBuffer = wvb.asFloatBuffer();

        ByteBuffer gvb = ByteBuffer.allocateDirect(glowFloatCount * 4);
        gvb.order(ByteOrder.nativeOrder());
        glowVertexBuffer = gvb.asFloatBuffer();

        // Generate VBOs
        int[] vbos = new int[6];
        GLES20.glGenBuffers(6, vbos, 0);
        hexVboId = vbos[0];
        triangleVboId = vbos[1];
        diamondVboId = vbos[2];
        beamVboId = vbos[3];
        wireframeVboId = vbos[4];
        glowVboId = vbos[5];

        int[] ibos = new int[2];
        GLES20.glGenBuffers(2, ibos, 0);
        hexIboId = ibos[0];
        triangleIboId = ibos[1];

        // Initialize buffer data
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, hexVboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, hexFloatCount * 4, null, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, triangleVboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, triangleFloatCount * 4, null, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, diamondVboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, diamondFloatCount * 4, null, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, beamVboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, beamFloatCount * 4, null, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, wireframeVboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, wireframeFloatCount * 4, null, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, glowVboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, glowFloatCount * 4, null, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, hexIboId);
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, hexIndexCount * 2, hexIndexBuffer, GLES20.GL_STATIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, triangleIboId);
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, triangleIndexCount * 2, triangleIndexBuffer, GLES20.GL_STATIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

        buffersInitialized = true;
        Log.d(TAG, "Crystal Mandala buffers allocated: hexIdx=" + hexIndexCount + " triIdx=" + triangleIndexCount);
    }

    @Override
    public void update(float deltaTime, float[] fftBands, float beatIntensity, float totalTime) {
        if (fftBands == null || fftBands.length == 0) return;

        this.totalTime = totalTime;
        globalTime = totalTime;

        analyzeAudio(fftBands, beatIntensity);

        rotation1 += deltaTime * ROTATION_SPEED_LAYER1 * (1f + smoothedBass * 0.5f);
        rotation2 += deltaTime * ROTATION_SPEED_LAYER2 * (1f + smoothedMid * 0.3f);
        rotation3 += deltaTime * ROTATION_SPEED_LAYER3;

        pulseWave = (float)Math.sin(globalTime * PULSE_SPEED);
        if (beatFrames > 0) {
            beatPulse = (float)beatFrames / 15f;
            beatFrames--;
        } else {
            beatPulse = 0f;
        }

        updateLayerEnergies(fftBands);

        updateHexLayers();
        updateTriangleLayers();
        updateDiamonds();
        updateLaserBeams();
        updateWireframe();
        updateGlowCore();
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

    private void updateLayerEnergies(float[] fftBands) {
        for (int i = 0; i < HEX_LAYERS; i++) {
            int fftIdx = (int)((float)i / HEX_LAYERS * fftBands.length);
            fftIdx = Math.min(fftIdx, fftBands.length - 1);
            float target = fftBands[fftIdx] * renderer.getSensitivity();
            layerEnergies[i] = lerp(layerEnergies[i], target, 0.12f);
        }
    }

    private void updateHexLayers() {
        hexVertexBuffer.clear();

        for (int layer = 0; layer < HEX_LAYERS; layer++) {
            float baseRadius = hexRadii[layer];
            float energy = layerEnergies[layer];
            float pulse = (float)Math.sin(globalTime * 3 + layer) * 0.03f * energy;
            float radius = baseRadius + pulse + beatPulse * 0.05f;
            float thickness = 0.008f + energy * 0.01f;

            for (int p = 0; p < HEX_POINTS; p++) {
                float angle = (float)(p * 2 * Math.PI / HEX_POINTS) + rotation1 * (layer % 2 == 0 ? 1 : -1);

                float x = (float)Math.cos(angle) * radius;
                float y = (float)Math.sin(angle) * radius;
                float xi = (float)Math.cos(angle) * (radius - thickness);
                float yi = (float)Math.sin(angle) * (radius - thickness);

                float hue = ((float)layer / HEX_LAYERS + globalTime * 0.15f) % 1.0f;
                float sat = 0.9f;
                float val = 0.7f + energy * 0.3f + pulseWave * 0.1f;
                float[] rgb = hsvToRgb(hue, sat, val);

                float alpha = 0.6f + energy * 0.3f;
                float z = (float)Math.sin(angle * 3 + globalTime * 5) * 0.04f * energy;

                hexVertexBuffer.put(x);
                hexVertexBuffer.put(y);
                hexVertexBuffer.put(z);
                hexVertexBuffer.put(rgb[0]);
                hexVertexBuffer.put(rgb[1]);
                hexVertexBuffer.put(rgb[2]);
                hexVertexBuffer.put(alpha);

                hexVertexBuffer.put(xi);
                hexVertexBuffer.put(yi);
                hexVertexBuffer.put(z - 0.01f);
                hexVertexBuffer.put(rgb[0] * 0.6f);
                hexVertexBuffer.put(rgb[1] * 0.6f);
                hexVertexBuffer.put(rgb[2] * 0.7f);
                hexVertexBuffer.put(alpha * 0.5f);
            }
        }

        hexVertexBuffer.flip();
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, hexVboId);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, hexVertexBuffer.remaining() * 4, hexVertexBuffer);
    }

    private void updateTriangleLayers() {
        triangleVertexBuffer.clear();

        for (int layer = 0; layer < TRIANGLE_LAYERS; layer++) {
            float radius = triangleRadii[layer] * (0.8f + pulseWave * 0.1f + beatPulse * 0.08f);
            float energy = (float)Math.sin(globalTime * 2 + layer) * 0.5f + 0.5f;

            for (int p = 0; p < 3; p++) {
                float angle = (float)(p * 2 * Math.PI / 3) + rotation2;

                float x = (float)Math.cos(angle) * radius;
                float y = (float)Math.sin(angle) * radius;

                float angleInner = angle + rotation2 * 0.5f;
                float xInner = (float)Math.cos(angleInner) * radius * 0.6f;
                float yInner = (float)Math.sin(angleInner) * radius * 0.6f;

                float hue = (0.5f + (float)layer / TRIANGLE_LAYERS + globalTime * 0.2f) % 1.0f;
                float[] rgb = hsvToRgb(hue, 0.85f, 0.95f);

                triangleVertexBuffer.put(x);
                triangleVertexBuffer.put(y);
                triangleVertexBuffer.put(0.03f);
                triangleVertexBuffer.put(rgb[0]);
                triangleVertexBuffer.put(rgb[1]);
                triangleVertexBuffer.put(rgb[2]);
                triangleVertexBuffer.put(0.7f);

                triangleVertexBuffer.put(xInner);
                triangleVertexBuffer.put(yInner);
                triangleVertexBuffer.put(0.03f);
                triangleVertexBuffer.put(rgb[0] * 0.5f);
                triangleVertexBuffer.put(rgb[1] * 0.5f);
                triangleVertexBuffer.put(rgb[2] * 0.6f);
                triangleVertexBuffer.put(0.5f);
            }
        }

        triangleVertexBuffer.flip();
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, triangleVboId);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, triangleVertexBuffer.remaining() * 4, triangleVertexBuffer);
    }

    private void updateDiamonds() {
        diamondVertexBuffer.clear();

        for (int i = 0; i < DIAMOND_COUNT; i++) {
            float angle = (float)(i * 2 * Math.PI / DIAMOND_COUNT) + rotation3;
            float radius = 0.7f + smoothedBass * 0.15f;
            float energy = (float)Math.sin(globalTime * 4 + angle) * 0.5f + 0.5f;

            float x1 = (float)Math.cos(angle) * (radius - 0.08f);
            float y1 = (float)Math.sin(angle) * (radius - 0.08f);
            float x2 = (float)Math.cos(angle + 0.2f) * radius;
            float y2 = (float)Math.sin(angle + 0.2f) * radius;
            float x3 = (float)Math.cos(angle) * (radius + 0.08f);
            float y3 = (float)Math.sin(angle) * (radius + 0.08f);
            float x4 = (float)Math.cos(angle - 0.2f) * radius;
            float y4 = (float)Math.sin(angle - 0.2f) * radius;

            float hue = (angle / (float)(Math.PI * 2) + globalTime * 0.3f) % 1.0f;
            float[] rgb = hsvToRgb(hue, 0.9f, 0.85f + energy * 0.15f);
            float alpha = 0.5f + energy * 0.3f;

            // Triangle 1
            diamondVertexBuffer.put(x1); diamondVertexBuffer.put(y1); diamondVertexBuffer.put(0.06f);
            diamondVertexBuffer.put(rgb[0]); diamondVertexBuffer.put(rgb[1]); diamondVertexBuffer.put(rgb[2]); diamondVertexBuffer.put(alpha);

            diamondVertexBuffer.put(x2); diamondVertexBuffer.put(y2); diamondVertexBuffer.put(0.06f);
            diamondVertexBuffer.put(rgb[0]); diamondVertexBuffer.put(rgb[1]); diamondVertexBuffer.put(rgb[2]); diamondVertexBuffer.put(alpha);

            diamondVertexBuffer.put(x3); diamondVertexBuffer.put(y3); diamondVertexBuffer.put(0.06f);
            diamondVertexBuffer.put(rgb[0]); diamondVertexBuffer.put(rgb[1]); diamondVertexBuffer.put(rgb[2]); diamondVertexBuffer.put(alpha);

            // Triangle 2
            diamondVertexBuffer.put(x3); diamondVertexBuffer.put(y3); diamondVertexBuffer.put(0.06f);
            diamondVertexBuffer.put(rgb[0]); diamondVertexBuffer.put(rgb[1]); diamondVertexBuffer.put(rgb[2]); diamondVertexBuffer.put(alpha);

            diamondVertexBuffer.put(x4); diamondVertexBuffer.put(y4); diamondVertexBuffer.put(0.06f);
            diamondVertexBuffer.put(rgb[0]); diamondVertexBuffer.put(rgb[1]); diamondVertexBuffer.put(rgb[2]); diamondVertexBuffer.put(alpha);

            diamondVertexBuffer.put(x1); diamondVertexBuffer.put(y1); diamondVertexBuffer.put(0.06f);
            diamondVertexBuffer.put(rgb[0]); diamondVertexBuffer.put(rgb[1]); diamondVertexBuffer.put(rgb[2]); diamondVertexBuffer.put(alpha);
        }

        diamondVertexBuffer.flip();
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, diamondVboId);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, diamondVertexBuffer.remaining() * 4, diamondVertexBuffer);
    }

    private void updateLaserBeams() {
        beamVertexBuffer.clear();

        for (int i = 0; i < BEAM_COUNT; i++) {
            float angle = (float)(i * 2 * Math.PI / BEAM_COUNT) + globalTime * 0.8f;
            float radiusStart = 0.15f;
            float radiusEnd = RADIUS_MAX * (0.7f + smoothedBass * 0.2f);

            float x1 = (float)Math.cos(angle) * radiusStart;
            float y1 = (float)Math.sin(angle) * radiusStart;
            float x2 = (float)Math.cos(angle) * radiusEnd;
            float y2 = (float)Math.sin(angle) * radiusEnd;

            float hue = (angle / (float)(Math.PI * 2) + globalTime * 0.5f) % 1.0f;
            float[] rgb = hsvToRgb(hue, 1.0f, 1.0f);
            float alpha = 0.4f + (float)Math.sin(globalTime * 10 + i) * 0.2f;

            beamVertexBuffer.put(x1); beamVertexBuffer.put(y1); beamVertexBuffer.put(0.02f);
            beamVertexBuffer.put(rgb[0]); beamVertexBuffer.put(rgb[1]); beamVertexBuffer.put(rgb[2]); beamVertexBuffer.put(alpha);

            beamVertexBuffer.put(x2); beamVertexBuffer.put(y2); beamVertexBuffer.put(0.02f);
            beamVertexBuffer.put(rgb[0]); beamVertexBuffer.put(rgb[1]); beamVertexBuffer.put(rgb[2]); beamVertexBuffer.put(alpha);
        }

        beamVertexBuffer.flip();
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, beamVboId);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, beamVertexBuffer.remaining() * 4, beamVertexBuffer);
    }

    private void updateWireframe() {
		wireframeVertexBuffer.clear();

		for (int i = 0; i < WIREFRAME_SEGMENTS; i++) {  // <-- CHANGED: i < WIREFRAME_SEGMENTS instead of i <= WIREFRAME_SEGMENTS
			float angle = (float)(i * 2 * Math.PI / WIREFRAME_SEGMENTS);
			float radius = RADIUS_MAX * (0.85f + (float)Math.sin(globalTime * 2) * 0.05f + beatPulse * 0.08f);

			float x = (float)Math.cos(angle) * radius;
			float y = (float)Math.sin(angle) * radius;

			float nextAngle = (float)((i + 1) * 2 * Math.PI / WIREFRAME_SEGMENTS);
			float xNext = (float)Math.cos(nextAngle) * radius;
			float yNext = (float)Math.sin(nextAngle) * radius;

			float[] rgb = {0.2f, 0.8f, 1.0f};
			float alpha = 0.3f + (float)Math.sin(globalTime * 4 + i) * 0.15f;

			wireframeVertexBuffer.put(x); wireframeVertexBuffer.put(y); wireframeVertexBuffer.put(0.01f);
			wireframeVertexBuffer.put(rgb[0]); wireframeVertexBuffer.put(rgb[1]); wireframeVertexBuffer.put(rgb[2]); wireframeVertexBuffer.put(alpha);

			wireframeVertexBuffer.put(xNext); wireframeVertexBuffer.put(yNext); wireframeVertexBuffer.put(0.01f);
			wireframeVertexBuffer.put(rgb[0]); wireframeVertexBuffer.put(rgb[1]); wireframeVertexBuffer.put(rgb[2]); wireframeVertexBuffer.put(alpha);
		}

		wireframeVertexBuffer.flip();
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, wireframeVboId);
		GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, wireframeVertexBuffer.remaining() * 4, wireframeVertexBuffer);
	}
	

    private void updateGlowCore() {
        glowVertexBuffer.clear();

        float coreSize = 0.08f + smoothedBass * 0.06f + beatPulse * 0.05f;

        for (int i = 0; i < 24; i++) {
            float angle = (float)(i * 2 * Math.PI / 24);
            float x = (float)Math.cos(angle) * coreSize;
            float y = (float)Math.sin(angle) * coreSize;

            float xi = (float)Math.cos(angle) * (coreSize * 0.4f);
            float yi = (float)Math.sin(angle) * (coreSize * 0.4f);

            float[] rgb = hsvToRgb(globalTime * 0.3f % 1.0f, 0.8f, 1.0f);

            glowVertexBuffer.put(x);
            glowVertexBuffer.put(y);
            glowVertexBuffer.put(0.08f);
            glowVertexBuffer.put(rgb[0]);
            glowVertexBuffer.put(rgb[1]);
            glowVertexBuffer.put(rgb[2]);
            glowVertexBuffer.put(0.9f);

            glowVertexBuffer.put(xi);
            glowVertexBuffer.put(yi);
            glowVertexBuffer.put(0.08f);
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

        if (posHandle == -1) return;

        if (mvpHandle != -1) {
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0);
        }

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);

        int stride = 7 * 4;

        // Draw glow core (additive)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, glowVboId);
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, stride, 0);
        GLES20.glEnableVertexAttribArray(posHandle);
        if (colHandle != -1) {
            GLES20.glVertexAttribPointer(colHandle, 4, GLES20.GL_FLOAT, false, stride, 3 * 4);
            GLES20.glEnableVertexAttribArray(colHandle);
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, glowVertexCount);

        // Draw hexagon layers
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, hexVboId);
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, stride, 0);
        if (colHandle != -1) {
            GLES20.glVertexAttribPointer(colHandle, 4, GLES20.GL_FLOAT, false, stride, 3 * 4);
        }
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, hexIboId);
        GLES20.glDrawElements(GLES20.GL_LINES, hexIndexCount, GLES20.GL_UNSIGNED_SHORT, 0);

        // Draw triangles
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, triangleVboId);
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, stride, 0);
        if (colHandle != -1) {
            GLES20.glVertexAttribPointer(colHandle, 4, GLES20.GL_FLOAT, false, stride, 3 * 4);
        }
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, triangleIboId);
        GLES20.glDrawElements(GLES20.GL_LINES, triangleIndexCount, GLES20.GL_UNSIGNED_SHORT, 0);

        // Draw diamonds (triangles)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, diamondVboId);
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, stride, 0);
        if (colHandle != -1) {
            GLES20.glVertexAttribPointer(colHandle, 4, GLES20.GL_FLOAT, false, stride, 3 * 4);
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, diamondVertexCount);

        // Draw laser beams (lines with additive)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);
        GLES20.glLineWidth(2.0f);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, beamVboId);
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, stride, 0);
        if (colHandle != -1) {
            GLES20.glVertexAttribPointer(colHandle, 4, GLES20.GL_FLOAT, false, stride, 3 * 4);
        }
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, beamVertexCount);

        // Draw wireframe
        GLES20.glLineWidth(1.0f);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, wireframeVboId);
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, stride, 0);
        if (colHandle != -1) {
            GLES20.glVertexAttribPointer(colHandle, 4, GLES20.GL_FLOAT, false, stride, 3 * 4);
        }
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, wireframeVertexCount);

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
            int[] vbos = new int[]{hexVboId, triangleVboId, diamondVboId, beamVboId, wireframeVboId, glowVboId};
            GLES20.glDeleteBuffers(6, vbos, 0);
            if (hexIboId != 0) GLES20.glDeleteBuffers(1, new int[]{hexIboId}, 0);
            if (triangleIboId != 0) GLES20.glDeleteBuffers(1, new int[]{triangleIboId}, 0);
            buffersInitialized = false;
        }
    }

    @Override
    public boolean usesTexture() {
        return false;
    }
}
