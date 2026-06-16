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
 * CELESTIAL NEURAL ARRAY - FIXED VERSION
 * Monumental audio visualization with corrected buffer allocations
 */
public class HeartbeatStyle extends VisualStyle {

    // === CONSTANTS ===
    private static final int HISTORY_POINTS = 256;
    private static final int SMOOTH_POINTS = 512;
    private static final int FREQ_BARS = 48;
    private static final int MAX_PARTICLES = 120;
    private static final int RING_SEGMENTS = 64;
    private static final int RING_COUNT = 3; // Multiple rings
    private static final int GLYPH_COUNT = 20;
    private static final int STARS_COUNT = 200;
    private static final int NEBULA_LAYERS = 3;

    private static final float PHOSPHOR_DECAY = 0.94f;
    private static final float GRAVITY_WELL_X = 0.9f;
    private static final float GRAVITY_WELL_Y = 0f;

    // === BUFFERS ===
    private FloatBuffer mainWaveVerts, mainWaveColors;
    private FloatBuffer phosphorVerts, phosphorColors;
    private FloatBuffer freqBarVerts, freqBarColors;
    private FloatBuffer particleVerts, particleColors;
    private FloatBuffer ringVerts, ringColors;
    private FloatBuffer glyphVerts, glyphColors;
    private FloatBuffer starVerts, starColors;
    private FloatBuffer nebulaVerts, nebulaColors;
    private FloatBuffer godRayVerts, godRayColors;

    // === VBOs ===
    private int mainWaveVbo, mainWaveColorVbo;
    private int phosphorVbo, phosphorColorVbo;
    private int freqBarVbo, freqBarColorVbo;
    private int particleVbo, particleColorVbo;
    private int ringVbo, ringColorVbo;
    private int glyphVbo, glyphColorVbo;
    private int starVbo, starColorVbo;
    private int nebulaVbo, nebulaColorVbo;
    private int godRayVbo, godRayColorVbo;

    private boolean initialized = false;

    // === STATE ===
    private final float[] history = new float[HISTORY_POINTS];
    private final float[] phosphorBuffer = new float[HISTORY_POINTS];
    private int historyHead = 0;

    private final float[] particles = new float[MAX_PARTICLES * 9];
    private final float[] stars = new float[STARS_COUNT * 4];
    private final Random rng = new Random();

    private float beatAccumulator = 0f;
    private float lastBeatIntensity = 0f;
    private float energyLevel = 0f;
    private float cosmicPhase = 0f;
    private float hologramFlicker = 0f;
    private float chromaticShift = 0f;
    private float ambientPulse = 0f;

    private boolean beatTriggered = false;
    private float beatFlash = 0f;
    private int beatCount = 0;

    private int activeParticleVertices = 0;
    private int activeGlyphVertices = 0;

    @Override
    public void init(int shader2D, int shader3D, int shader3DUniform,
                     int texturedShader2D, int texturedShader3D,
                     GLVisualizerRenderer renderer) {
        super.init(shader2D, shader3D, shader3DUniform, texturedShader2D, texturedShader3D, renderer);
        initBuffers();
        initVbos();
        initStarfield();
        initialized = true;
    }

    private void initBuffers() {
        // Main waveform
        mainWaveVerts = allocateBuffer(SMOOTH_POINTS * 3);
        mainWaveColors = allocateBuffer(SMOOTH_POINTS * 4);

        phosphorVerts = allocateBuffer(SMOOTH_POINTS * 3);
        phosphorColors = allocateBuffer(SMOOTH_POINTS * 4);

        // Frequency bars
        freqBarVerts = allocateBuffer(FREQ_BARS * 6 * 3);
        freqBarColors = allocateBuffer(FREQ_BARS * 6 * 4);

        // Particles
        particleVerts = allocateBuffer(MAX_PARTICLES * 6 * 3);
        particleColors = allocateBuffer(MAX_PARTICLES * 6 * 4);

        // === FIXED: Ring buffer for 3 rings ===
        ringVerts = allocateBuffer(RING_SEGMENTS * 2 * 3 * RING_COUNT); // 3 rings!
        ringColors = allocateBuffer(RING_SEGMENTS * 2 * 4 * RING_COUNT);

        // Glyphs - FIXED: 6 triangles per hexagon × 3 vertices
        glyphVerts = allocateBuffer(GLYPH_COUNT * 6 * 3 * 3); // 6 tris per hex
        glyphColors = allocateBuffer(GLYPH_COUNT * 6 * 4 * 3);

        // Stars
        starVerts = allocateBuffer(STARS_COUNT * 3);
        starColors = allocateBuffer(STARS_COUNT * 4);

        // Nebula
        nebulaVerts = allocateBuffer(NEBULA_LAYERS * 6 * 3);
        nebulaColors = allocateBuffer(NEBULA_LAYERS * 6 * 4);

        // God rays
        godRayVerts = allocateBuffer(24 * 3);
        godRayColors = allocateBuffer(24 * 4);
    }

    private FloatBuffer allocateBuffer(int capacity) {
        ByteBuffer bb = ByteBuffer.allocateDirect(capacity * 4);
        bb.order(ByteOrder.nativeOrder());
        return bb.asFloatBuffer();
    }

    private void initVbos() {
        int[] vbos = new int[18];
        GLES20.glGenBuffers(18, vbos, 0);

        mainWaveVbo = vbos[0]; mainWaveColorVbo = vbos[1];
        phosphorVbo = vbos[2]; phosphorColorVbo = vbos[3];
        freqBarVbo = vbos[4]; freqBarColorVbo = vbos[5];
        particleVbo = vbos[6]; particleColorVbo = vbos[7];
        ringVbo = vbos[8]; ringColorVbo = vbos[9];
        glyphVbo = vbos[10]; glyphColorVbo = vbos[11];
        starVbo = vbos[12]; starColorVbo = vbos[13];
        nebulaVbo = vbos[14]; nebulaColorVbo = vbos[15];
        godRayVbo = vbos[16]; godRayColorVbo = vbos[17];

        // Allocate GPU storage with CORRECT sizes
        bufferData(mainWaveVbo, SMOOTH_POINTS * 3 * 4);
        bufferData(mainWaveColorVbo, SMOOTH_POINTS * 4 * 4);
        bufferData(phosphorVbo, SMOOTH_POINTS * 3 * 4);
        bufferData(phosphorColorVbo, SMOOTH_POINTS * 4 * 4);
        bufferData(freqBarVbo, FREQ_BARS * 6 * 3 * 4);
        bufferData(freqBarColorVbo, FREQ_BARS * 6 * 4 * 4);
        bufferData(particleVbo, MAX_PARTICLES * 6 * 3 * 4);
        bufferData(particleColorVbo, MAX_PARTICLES * 6 * 4 * 4);

        // === FIXED: Ring VBO size for 3 rings ===
        bufferData(ringVbo, RING_SEGMENTS * 2 * 3 * RING_COUNT * 4);
        bufferData(ringColorVbo, RING_SEGMENTS * 2 * 4 * RING_COUNT * 4);

        // === FIXED: Glyph VBO size ===
        bufferData(glyphVbo, GLYPH_COUNT * 6 * 3 * 3 * 4);
        bufferData(glyphColorVbo, GLYPH_COUNT * 6 * 4 * 3 * 4);

        bufferData(starVbo, STARS_COUNT * 3 * 4);
        bufferData(starColorVbo, STARS_COUNT * 4 * 4);
        bufferData(nebulaVbo, NEBULA_LAYERS * 6 * 3 * 4);
        bufferData(nebulaColorVbo, NEBULA_LAYERS * 6 * 4 * 4);
        bufferData(godRayVbo, 24 * 3 * 4);
        bufferData(godRayColorVbo, 24 * 4 * 4);
    }

    private void bufferData(int vbo, int size) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, size, null, GLES20.GL_DYNAMIC_DRAW);
    }

    private void initStarfield() {
        for (int i = 0; i < STARS_COUNT; i++) {
            stars[i * 4] = (rng.nextFloat() - 0.5f) * 4f;
            stars[i * 4 + 1] = (rng.nextFloat() - 0.5f) * 3f;
            stars[i * 4 + 2] = -2f - rng.nextFloat() * 3f;
            stars[i * 4 + 3] = 0.3f + rng.nextFloat() * 0.7f;
        }
    }

    @Override
    public void update(float deltaTime, float[] fftBands, float beatIntensity, float totalTime) {
        this.totalTime = totalTime;

        cosmicPhase += deltaTime * 0.5f;
        hologramFlicker = (float) Math.sin(totalTime * 8f) * 0.5f + 0.5f;
        ambientPulse = (float) Math.sin(totalTime * 2f) * 0.3f + 0.7f;

        energyLevel = energyLevel * 0.92f + beatIntensity * 0.08f;
        beatAccumulator += deltaTime;

        beatTriggered = beatIntensity > 0.6f && lastBeatIntensity < 0.3f;
        if (beatTriggered) {
            beatFlash = 1f;
            beatCount++;
            chromaticShift = 0.03f + beatIntensity * 0.04f;
        }
        beatFlash *= 0.88f;
        chromaticShift *= 0.92f;
        lastBeatIntensity = beatIntensity;

        float currentSample = extractAudioSample(fftBands, beatIntensity);
        updateHistory(currentSample);
        updatePhosphorTrail();

        generateMainWaveform(beatIntensity, deltaTime);
        generatePhosphorTrail(beatIntensity);
        generateFrequencyArchitecture(fftBands, beatIntensity);
        generateSolarFlareParticles(deltaTime, beatTriggered, beatIntensity);
        generateHarmonicRings(beatIntensity, totalTime);
        generateDataGlyphs(deltaTime, beatIntensity);
        generateStarfield(beatIntensity);
        generateNebulaLayers(beatIntensity);
        generateGodRays(beatIntensity);
    }

    private float extractAudioSample(float[] fftBands, float beatIntensity) {
        if (fftBands == null || fftBands.length == 0) return 0f;

        int lowEnd = Math.max(3, fftBands.length / 6);
        int midEnd = Math.max(6, fftBands.length / 2);

        float bass = 0f, mids = 0f, treble = 0f;
        float weight = 1f, totalWeight = 0f;

        for (int i = 0; i < lowEnd; i++) {
            bass += fftBands[i] * weight;
            totalWeight += weight;
            weight *= 0.75f;
        }
        bass = (bass / totalWeight) * 1.6f;

        totalWeight = 0f; weight = 1f;
        for (int i = lowEnd; i < midEnd && i < fftBands.length; i++) {
            mids += fftBands[i] * weight;
            totalWeight += weight;
            weight *= 0.85f;
        }
        mids = (mids / totalWeight) * 0.8f;

        for (int i = midEnd; i < fftBands.length; i++) {
            treble += fftBands[i] * 0.3f;
        }

        float combined = (bass + mids + treble) * renderer.getSensitivity();

        if (beatIntensity > 0.7f) {
            combined += beatFlash * 0.4f * (float) Math.sin(beatAccumulator * 12f);
        }

        combined = combined / (1f + combined * 0.25f);
        return Math.min(combined, 1.0f);
    }

    private void updateHistory(float sample) {
        history[historyHead] = sample;
        historyHead = (historyHead + 1) % HISTORY_POINTS;
    }

    private void updatePhosphorTrail() {
        for (int i = 0; i < HISTORY_POINTS; i++) {
            int idx = (historyHead - 1 - i + HISTORY_POINTS) % HISTORY_POINTS;
            if (i == 0) {
                phosphorBuffer[idx] = Math.max(phosphorBuffer[idx] * PHOSPHOR_DECAY, history[idx]);
            } else {
                phosphorBuffer[idx] *= PHOSPHOR_DECAY;
            }
        }
    }

    private void generateMainWaveform(float beatIntensity, float deltaTime) {
        mainWaveVerts.clear();
        mainWaveColors.clear();

        for (int i = 0; i < SMOOTH_POINTS; i++) {
            float t = i / (float)(SMOOTH_POINTS - 1);
            float historyPos = t * (HISTORY_POINTS - 1);
            int idx = (int) historyPos;
            float frac = historyPos - idx;

            float p0 = getHistoryAt(idx - 1);
            float p1 = getHistoryAt(idx);
            float p2 = getHistoryAt(idx + 1);
            float p3 = getHistoryAt(idx + 2);

            float value = catmullRom(p0, p1, p2, p3, frac);

            float x = -1.0f + 2.0f * t;
            float y = value * 1.2f;
            float z = (float) Math.sin(t * Math.PI) * 0.2f * beatIntensity;

            if (hologramFlicker > 0.85f) {
                z += 0.03f;
            }

            mainWaveVerts.put(x);
            mainWaveVerts.put(y);
            mainWaveVerts.put(z);

            float age = t;
            float intensity = value;

            float r = 0.2f + age * 0.8f + intensity * 0.5f;
            float g = 0.5f + age * 0.4f + intensity * 0.8f;
            float b = 1f;
            float a = 0.6f + age * 0.4f + beatFlash * 0.3f;

            if (intensity > 0.8f || beatFlash > 0.4f) {
                r = 1f; g = 0.95f; b = 0.9f; a = 1f;
            }

            float shift = chromaticShift * (1f - age);
            r = Math.min(r + shift, 1f);

            mainWaveColors.put(Math.min(r, 1f));
            mainWaveColors.put(Math.min(g, 1f));
            mainWaveColors.put(Math.min(b, 1f));
            mainWaveColors.put(Math.min(a, 1f));
        }

        mainWaveVerts.flip();
        mainWaveColors.flip();
        updateVboFull(mainWaveVbo, mainWaveVerts);
        updateVboFull(mainWaveColorVbo, mainWaveColors);
    }

    private void generatePhosphorTrail(float beatIntensity) {
        phosphorVerts.clear();
        phosphorColors.clear();

        float delay = 30f;

        for (int i = 0; i < SMOOTH_POINTS; i++) {
            float t = i / (float)(SMOOTH_POINTS - 1);
            float historyPos = t * (HISTORY_POINTS - 1) - delay;
            int idx = (int) historyPos;
            float frac = historyPos - idx;

            if (idx < 0) idx += HISTORY_POINTS;

            float p0 = getPhosphorAt(idx - 1);
            float p1 = getPhosphorAt(idx);
            float p2 = getPhosphorAt(idx + 1);
            float p3 = getPhosphorAt(idx + 2);

            float value = catmullRom(p0, p1, p2, p3, frac);

            float x = -1.0f + 2.0f * t;
            float y = value * 1.2f;
            float z = -0.15f;

            phosphorVerts.put(x);
            phosphorVerts.put(y);
            phosphorVerts.put(z);

            float age = t;
            float strength = value * 0.9f + 0.1f;

            float r = 0.4f * strength;
            float g = 0.1f + age * 0.3f;
            float b = 0.8f + age * 0.2f;
            float a = strength * (0.25f + age * 0.35f) * ambientPulse;

            phosphorColors.put(r);
            phosphorColors.put(Math.min(g, 1f));
            phosphorColors.put(Math.min(b, 1f));
            phosphorColors.put(Math.min(a, 0.8f));
        }

        phosphorVerts.flip();
        phosphorColors.flip();
        updateVboFull(phosphorVbo, phosphorVerts);
        updateVboFull(phosphorColorVbo, phosphorColors);
    }

    private void generateFrequencyArchitecture(float[] fftBands, float beatIntensity) {
        if (fftBands == null || fftBands.length < FREQ_BARS) return;

        freqBarVerts.clear();
        freqBarColors.clear();

        float barWidth = 2.0f / FREQ_BARS;
        float baseX = -1.0f;

        for (int i = 0; i < FREQ_BARS; i++) {
            int fftIdx = (int) ((i / (float) FREQ_BARS) * (fftBands.length * 0.8f));
            fftIdx = Math.min(fftIdx, fftBands.length - 1);

            float magnitude = fftBands[fftIdx] * renderer.getSensitivity() * (1f + beatIntensity * 0.5f);
            magnitude = (float) Math.pow(magnitude, 0.55f);
            magnitude = Math.min(magnitude * 2.0f, 1.4f);

            float x = baseX + i * barWidth;
            float yBottom = -1.2f;
            float yTop = yBottom + magnitude * 1.8f;
            float z = -0.6f - (i % 3) * 0.1f;

            float hue = i / (float) FREQ_BARS;
            float r, g, b;
            if (hue < 0.5f) {
                r = 0.6f + hue * 0.8f;
                g = 0.2f + hue * 0.6f;
                b = 0.9f - hue * 0.4f;
            } else {
                r = 1.0f - (hue - 0.5f) * 0.6f;
                g = 0.8f - (hue - 0.5f) * 0.3f;
                b = 0.7f + (hue - 0.5f) * 0.3f;
            }

            float a = 0.2f + magnitude * 0.3f + beatFlash * 0.15f;

            float topWidth = barWidth * 0.7f;
            float baseWidth = barWidth * 1.0f;

            // Tri 1
            addQuadVertex(freqBarVerts, freqBarColors, x, yBottom, z, r, g, b, a * 0.7f);
            addQuadVertex(freqBarVerts, freqBarColors, x + baseWidth, yBottom, z, r, g, b, a * 0.7f);
            addQuadVertex(freqBarVerts, freqBarColors, x + (baseWidth - topWidth) * 0.5f, yTop, z, r * 1.2f, g * 1.2f, b * 1.2f, a);

            // Tri 2
            addQuadVertex(freqBarVerts, freqBarColors, x + baseWidth, yBottom, z, r, g, b, a * 0.7f);
            addQuadVertex(freqBarVerts, freqBarColors, x + (baseWidth + topWidth) * 0.5f, yTop, z, r * 1.2f, g * 1.2f, b * 1.2f, a);
            addQuadVertex(freqBarVerts, freqBarColors, x + (baseWidth - topWidth) * 0.5f, yTop, z, r * 1.2f, g * 1.2f, b * 1.2f, a);
        }

        freqBarVerts.flip();
        freqBarColors.flip();
        updateVboFull(freqBarVbo, freqBarVerts);
        updateVboFull(freqBarColorVbo, freqBarColors);
    }

    private void generateSolarFlareParticles(float deltaTime, boolean spawnBurst, float beatIntensity) {
        if (spawnBurst) {
            int toSpawn = (int) (20 + beatIntensity * 40);
            for (int i = 0; i < MAX_PARTICLES && toSpawn > 0; i++) {
                if (particles[i * 9 + 5] <= 0f) {
                    spawnSolarParticle(i, beatIntensity, true);
                    toSpawn--;
                }
            }
        } else if (rng.nextFloat() < 0.15f + energyLevel * 0.4f) {
            for (int i = 0; i < MAX_PARTICLES; i++) {
                if (particles[i * 9 + 5] <= 0f) {
                    spawnSolarParticle(i, beatIntensity, false);
                    break;
                }
            }
        }

        particleVerts.clear();
        particleColors.clear();
        int verticesWritten = 0;

        for (int i = 0; i < MAX_PARTICLES; i++) {
            int base = i * 9;
            float life = particles[base + 5];

            if (life > 0f) {
                float px = particles[base];
                float py = particles[base + 1];
                float pz = particles[base + 2];
                float vx = particles[base + 3];
                float vy = particles[base + 4];

                float dx = GRAVITY_WELL_X - px;
                float dy = GRAVITY_WELL_Y - py;
                float distSq = dx * dx + dy * dy;
                float gravity = 2.0f / (distSq + 0.05f);

                float perpX = -dy;
                float perpY = dx;
                float perpLen = (float) Math.sqrt(perpX * perpX + perpY * perpY);
                if (perpLen > 0) {
                    perpX /= perpLen;
                    perpY /= perpLen;
                }

                vx += (dx * gravity + perpX * 0.5f) * deltaTime;
                vy += (dy * gravity + perpY * 0.5f) * deltaTime;

                vx *= 0.96f;
                vy *= 0.96f;

                px += vx * deltaTime;
                py += vy * deltaTime;
                pz = 0.1f + life * 0.2f;

                particles[base] = px;
                particles[base + 1] = py;
                particles[base + 2] = pz;
                particles[base + 3] = vx;
                particles[base + 4] = vy;

                life -= deltaTime * (0.35f + particles[base + 8] * 0.3f);
                particles[base + 5] = life;

                float size = particles[base + 6] * life * (1f + beatFlash);
                float hue = particles[base + 7];
                float energy = particles[base + 8];

                float r = 1f;
                float g = 0.4f + hue * 0.4f * life;
                float b = hue * 0.2f * life;
                float a = life * (0.8f + energy * 0.4f + beatFlash * 0.3f);

                float rot = totalTime * 2f + i;
                float c = (float) Math.cos(rot);
                float s = (float) Math.sin(rot);

                float[] corners = {-size, -size, size, -size, -size, size,
					size, -size, size, size, -size, size};

                for (int j = 0; j < 6; j++) {
                    float lx = corners[j * 2];
                    float ly = corners[j * 2 + 1];
                    float rx = lx * c - ly * s;
                    float ry = lx * s + ly * c;

                    particleVerts.put(px + rx);
                    particleVerts.put(py + ry);
                    particleVerts.put(pz);

                    particleColors.put(r);
                    particleColors.put(g);
                    particleColors.put(b);
                    particleColors.put(a);
                }

                verticesWritten += 6;
            }
        }

        particleVerts.flip();
        particleColors.flip();

        activeParticleVertices = verticesWritten;
        if (verticesWritten > 0) {
            updateVbo(particleVbo, particleVerts, verticesWritten);
            updateColorVbo(particleColorVbo, particleColors, verticesWritten);
        }
    }

    private void spawnSolarParticle(int idx, float beatIntensity, boolean isBurst) {
        int base = idx * 9;

        float angle = rng.nextFloat() * 6.283f;
        float dist = 1.2f + rng.nextFloat() * 0.5f;

        particles[base] = (float) Math.cos(angle) * dist;
        particles[base + 1] = (float) Math.sin(angle) * dist;
        particles[base + 2] = 0f;

        float speed = isBurst ? 0.8f + beatIntensity * 1.2f : 0.2f + rng.nextFloat() * 0.3f;
        particles[base + 3] = -(float) Math.cos(angle) * speed;
        particles[base + 4] = -(float) Math.sin(angle) * speed;

        particles[base + 5] = 1f;
        particles[base + 6] = 0.04f + rng.nextFloat() * 0.06f;
        particles[base + 7] = rng.nextFloat();
        particles[base + 8] = isBurst ? 1.0f : 0.3f + rng.nextFloat() * 0.4f;
    }

    // === FIXED: Ring generation with proper buffer management ===
    private void generateHarmonicRings(float beatIntensity, float time) {
        ringVerts.clear();
        ringColors.clear();

        float baseRadius = 0.5f + beatIntensity * 0.3f;
        float cx = 0.9f;
        float cy = 0f;

        for (int ring = 0; ring < RING_COUNT; ring++) {
            float radius = baseRadius * (0.6f + ring * 0.3f);
            float ringPhase = time * (1f + ring * 0.5f);
            float tiltX = (float) Math.sin(ringPhase * 0.7f) * 0.3f;

            for (int i = 0; i < RING_SEGMENTS; i++) {
                float angle1 = (i / (float) RING_SEGMENTS) * 6.283f;
                float angle2 = ((i + 1) / (float) RING_SEGMENTS) * 6.283f;

                float x1 = cx + radius * (float) Math.cos(angle1);
                float y1 = cy + radius * (float) Math.sin(angle1) * 0.5f;
                float z1 = -0.3f + radius * (float) Math.sin(angle1) * tiltX;

                float x2 = cx + radius * (float) Math.cos(angle2);
                float y2 = cy + radius * (float) Math.sin(angle2) * 0.5f;
                float z2 = -0.3f + radius * (float) Math.sin(angle2) * tiltX;

                float pulse = 1f + beatFlash * (float) Math.sin(i * 0.3f + ringPhase * 3f);

                ringVerts.put(x1); ringVerts.put(y1); ringVerts.put(z1);
                ringVerts.put(x2); ringVerts.put(y2); ringVerts.put(z2);

                float r = 0.3f + ring * 0.2f + beatIntensity * 0.3f;
                float g = 0.7f + ring * 0.1f;
                float b = 1f - ring * 0.2f;
                float a = (0.25f + beatIntensity * 0.35f) * pulse / (ring + 1);

                ringColors.put(r); ringColors.put(g); ringColors.put(b); ringColors.put(a);
                ringColors.put(r); ringColors.put(g); ringColors.put(b); ringColors.put(a);
            }
        }

        ringVerts.flip();
        ringColors.flip();
        updateVboFull(ringVbo, ringVerts);
        updateVboFull(ringColorVbo, ringColors);
    }

    // === FIXED: Glyph generation with proper vertex counting ===
    private void generateDataGlyphs(float deltaTime, float beatIntensity) {
        glyphVerts.clear();
        glyphColors.clear();

        int verticesWritten = 0;

        for (int i = 0; i < GLYPH_COUNT; i++) {
            float orbitPhase = totalTime * 0.4f + i * 0.5f;
            float orbitRadius = 0.3f + (i % 4) * 0.15f;
            float x = GRAVITY_WELL_X + (float) Math.cos(orbitPhase) * orbitRadius;
            float y = GRAVITY_WELL_Y + (float) Math.sin(orbitPhase) * orbitRadius * 0.6f;
            float z = -0.1f + (float) Math.sin(orbitPhase * 2f) * 0.1f;

            float size = 0.03f + beatIntensity * 0.025f;
            float alpha = 0.3f + beatFlash * 0.4f;

            if (hologramFlicker > 0.92f && (i + beatCount) % 4 == 0) {
                alpha = 0f;
            }

            float r = 0.8f, g = 0.9f, b = 1f;

            // Build hexagon (6 triangles, 3 vertices each = 18 vertices per glyph)
            for (int seg = 0; seg < 6; seg++) {
                float a1 = (seg / 6f) * 6.283f;
                float a2 = ((seg + 1) / 6f) * 6.283f;

                float cx = x, cy = y, cz = z;
                float x1 = x + (float) Math.cos(a1) * size;
                float y1 = y + (float) Math.sin(a1) * size;
                float x2 = x + (float) Math.cos(a2) * size;
                float y2 = y + (float) Math.sin(a2) * size;

                // Triangle: center -> edge1 -> edge2
                glyphVerts.put(cx); glyphVerts.put(cy); glyphVerts.put(cz);
                glyphVerts.put(x1); glyphVerts.put(y1); glyphVerts.put(cz);
                glyphVerts.put(x2); glyphVerts.put(y2); glyphVerts.put(cz);

                // Colors with gradient
                glyphColors.put(r); glyphColors.put(g); glyphColors.put(b); glyphColors.put(alpha);
                glyphColors.put(r); glyphColors.put(g); glyphColors.put(b); glyphColors.put(alpha * 0.6f);
                glyphColors.put(r); glyphColors.put(g); glyphColors.put(b); glyphColors.put(alpha * 0.6f);

                verticesWritten += 3;
            }
        }

        glyphVerts.flip();
        glyphColors.flip();

        activeGlyphVertices = verticesWritten;
        if (verticesWritten > 0) {
            updateVbo(glyphVbo, glyphVerts, verticesWritten);
            updateColorVbo(glyphColorVbo, glyphColors, verticesWritten);
        }
    }

    private void generateStarfield(float beatIntensity) {
        starVerts.clear();
        starColors.clear();

        float twinklePhase = totalTime * 3f;

        for (int i = 0; i < STARS_COUNT; i++) {
            float x = stars[i * 4];
            float y = stars[i * 4 + 1];
            float z = stars[i * 4 + 2];
            float brightness = stars[i * 4 + 3];

            x += (float) Math.sin(totalTime * 0.1f + i) * 0.002f;

            starVerts.put(x);
            starVerts.put(y);
            starVerts.put(z);

            float twinkle = (float) Math.sin(twinklePhase + i * 0.5f) * 0.3f + 0.7f;
            float a = brightness * twinkle * (0.5f + beatIntensity * 0.5f);

            float temp = (i % 5) / 5f;
            float r = 0.8f + temp * 0.2f;
            float g = 0.9f - temp * 0.1f;
            float b = 1f;

            starColors.put(r);
            starColors.put(g);
            starColors.put(b);
            starColors.put(a * 0.8f);
        }

        starVerts.flip();
        starColors.flip();
        updateVboFull(starVbo, starVerts);
        updateVboFull(starColorVbo, starColors);
    }

    private void generateNebulaLayers(float beatIntensity) {
        nebulaVerts.clear();
        nebulaColors.clear();

        for (int layer = 0; layer < NEBULA_LAYERS; layer++) {
            float z = -3f - layer * 1.5f;
            float phase = totalTime * 0.2f + layer;
            float driftX = (float) Math.sin(phase) * 0.3f;
            float driftY = (float) Math.cos(phase * 0.7f) * 0.2f;

            float size = 2.5f + layer * 0.5f;
            float x1 = -size + driftX, y1 = -size + driftY;
            float x2 = size + driftX, y2 = -size + driftY;
            float x3 = -size + driftX, y3 = size + driftY;
            float x4 = size + driftX, y4 = size + driftY;

            float r, g, b, a;
            if (layer == 0) {
                r = 0.4f; g = 0.1f; b = 0.6f; a = 0.15f;
            } else if (layer == 1) {
                r = 0.1f; g = 0.3f; b = 0.7f; a = 0.12f;
            } else {
                r = 0.05f; g = 0.5f; b = 0.6f; a = 0.1f;
            }

            a *= (0.8f + beatIntensity * 0.4f);

            addQuadVertex(nebulaVerts, nebulaColors, x1, y1, z, r, g, b, a);
            addQuadVertex(nebulaVerts, nebulaColors, x2, y2, z, r, g, b, a);
            addQuadVertex(nebulaVerts, nebulaColors, x3, y3, z, r, g, b, a * 1.3f);

            addQuadVertex(nebulaVerts, nebulaColors, x2, y2, z, r, g, b, a);
            addQuadVertex(nebulaVerts, nebulaColors, x4, y4, z, r, g, b, a * 1.3f);
            addQuadVertex(nebulaVerts, nebulaColors, x3, y3, z, r, g, b, a * 1.3f);
        }

        nebulaVerts.flip();
        nebulaColors.flip();
        updateVboFull(nebulaVbo, nebulaVerts);
        updateVboFull(nebulaColorVbo, nebulaColors);
    }

    private void generateGodRays(float beatIntensity) {
        godRayVerts.clear();
        godRayColors.clear();

        int rayCount = 8;
        float originX = 0.9f;
        float originY = 0f;

        for (int i = 0; i < rayCount; i++) {
            float angle = (i / (float) rayCount) * 6.283f + totalTime * 0.3f;
            float length = 1.5f + beatIntensity * 0.8f;
            float width = 0.02f + beatFlash * 0.03f;

            float dx = (float) Math.cos(angle);
            float dy = (float) Math.sin(angle);

            float x1 = originX + dx * 0.1f;
            float y1 = originY + dy * 0.1f;
            float x2 = originX + dx * length;
            float y2 = originY + dy * length;
            float x3 = originX + (dx - dy) * width;
            float y3 = originY + (dy + dx) * width;

            godRayVerts.put(x1); godRayVerts.put(y1); godRayVerts.put(0.2f);
            godRayVerts.put(x2); godRayVerts.put(y2); godRayVerts.put(0.5f);
            godRayVerts.put(x3); godRayVerts.put(y3); godRayVerts.put(0.2f);

            float r = 1f, g = 0.9f, b = 0.7f;
            float a = (0.1f + beatFlash * 0.2f) * (1f - i / (float) rayCount * 0.3f);

            godRayColors.put(r); godRayColors.put(g); godRayColors.put(b); godRayColors.put(a);
            godRayColors.put(r); godRayColors.put(g); godRayColors.put(b); godRayColors.put(0f);
            godRayColors.put(r); godRayColors.put(g); godRayColors.put(b); godRayColors.put(a * 0.5f);
        }

        godRayVerts.flip();
        godRayColors.flip();
        updateVboFull(godRayVbo, godRayVerts);
        updateVboFull(godRayColorVbo, godRayColors);
    }

    private float getHistoryAt(int idx) {
        int wrapped = ((idx % HISTORY_POINTS) + HISTORY_POINTS) % HISTORY_POINTS;
        return history[wrapped];
    }

    private float getPhosphorAt(int idx) {
        int wrapped = ((idx % HISTORY_POINTS) + HISTORY_POINTS) % HISTORY_POINTS;
        return phosphorBuffer[wrapped];
    }

    private float catmullRom(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return 0.5f * (
            (2f * p1) +
            (-p0 + p2) * t +
            (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 +
            (-p0 + 3f * p1 - 3f * p2 + p3) * t3
			);
    }

    private void updateVbo(int vbo, FloatBuffer data, int vertexCount) {
        if (vertexCount <= 0) return;
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, vertexCount * 3 * 4, data);
    }

    private void updateColorVbo(int vbo, FloatBuffer data, int vertexCount) {
        if (vertexCount <= 0) return;
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, vertexCount * 4 * 4, data);
    }

    private void updateVboFull(int vbo, FloatBuffer data) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, data.capacity() * 4, data);
    }

    private void addQuadVertex(FloatBuffer vb, FloatBuffer cb, 
                               float x, float y, float z, 
                               float r, float g, float b, float a) {
        vb.put(x); vb.put(y); vb.put(z);
        cb.put(r); cb.put(g); cb.put(b); cb.put(a);
    }

    private void drawLayer(int vbo, int colorVbo, int posHandle, int colHandle, 
						   int mode, int count, float lineWidth) {
        if (count <= 0) return;
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, 0);
        GLES20.glEnableVertexAttribArray(posHandle);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, colorVbo);
        GLES20.glVertexAttribPointer(colHandle, 4, GLES20.GL_FLOAT, false, 0, 0);
        GLES20.glEnableVertexAttribArray(colHandle);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        if (lineWidth > 0) GLES20.glLineWidth(lineWidth);
        GLES20.glDrawArrays(mode, 0, count);
    }

    private void drawCommon(float[] mvpMatrix, int program, float beatIntensity) {
        GLES20.glUseProgram(program);
        int posHandle = GLES20.glGetAttribLocation(program, "vPosition");
        int colHandle = GLES20.glGetAttribLocation(program, "aColor");
        int mvpHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");

        if (mvpHandle != -1) {
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0);
        }

        // Deep space background
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);

        drawLayer(nebulaVbo, nebulaColorVbo, posHandle, colHandle,
                  GLES20.GL_TRIANGLES, NEBULA_LAYERS * 6, 0);

        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        drawLayer(starVbo, starColorVbo, posHandle, colHandle,
                  GLES20.GL_POINTS, STARS_COUNT, 0);

        // Cathedral architecture
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);

        drawLayer(freqBarVbo, freqBarColorVbo, posHandle, colHandle,
                  GLES20.GL_TRIANGLES, FREQ_BARS * 6, 0);

        drawLayer(godRayVbo, godRayColorVbo, posHandle, colHandle,
                  GLES20.GL_TRIANGLES, 24, 0);

        // Main visualization
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        drawLayer(phosphorVbo, phosphorColorVbo, posHandle, colHandle,
                  GLES20.GL_LINE_STRIP, SMOOTH_POINTS, 3f);

        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);

        // === FIXED: Draw all 3 rings ===
        drawLayer(ringVbo, ringColorVbo, posHandle, colHandle,
                  GLES20.GL_LINES, RING_SEGMENTS * 2 * RING_COUNT, 3f);

        GLES20.glLineWidth(6f + beatFlash * 6f);
        drawLayer(mainWaveVbo, mainWaveColorVbo, posHandle, colHandle,
                  GLES20.GL_LINE_STRIP, SMOOTH_POINTS, 0);

        if (activeGlyphVertices > 0) {
            drawLayer(glyphVbo, glyphColorVbo, posHandle, colHandle,
                      GLES20.GL_TRIANGLES, activeGlyphVertices, 0);
        }

        if (activeParticleVertices > 0) {
            drawLayer(particleVbo, particleColorVbo, posHandle, colHandle,
                      GLES20.GL_TRIANGLES, activeParticleVertices, 0);
        }

        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisableVertexAttribArray(posHandle);
        GLES20.glDisableVertexAttribArray(colHandle);
    }

    @Override 
    public void draw2D(float[] mvpMatrix, GLVisualizerRenderer renderer) {
        drawCommon(mvpMatrix, shaderProgram2D, renderer.getBeatIntensity());
    }

    @Override 
    public void draw3D(float[] mvpMatrix, GLVisualizerRenderer renderer) {
        float[] rotated = new float[16];
        float tilt = renderer.getBeatIntensity() * 12f;
        Matrix.rotateM(rotated, 0, mvpMatrix, 0, tilt, 0f, 1f, 0f);
        Matrix.rotateM(rotated, 0, rotated, 0, tilt * 0.4f, 1f, 0f, 0f);
        drawCommon(rotated, shaderProgram3D, renderer.getBeatIntensity());
    }

    @Override
    public void release() {
        if (initialized) {
            int[] vbos = {
                mainWaveVbo, mainWaveColorVbo, phosphorVbo, phosphorColorVbo,
                freqBarVbo, freqBarColorVbo, particleVbo, particleColorVbo,
                ringVbo, ringColorVbo, glyphVbo, glyphColorVbo,
                starVbo, starColorVbo, nebulaVbo, nebulaColorVbo,
                godRayVbo, godRayColorVbo
            };
            GLES20.glDeleteBuffers(18, vbos, 0);
            initialized = false;
        }
    }
}

