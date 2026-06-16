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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.ikechi.studio.onwa.player.utils.colors.*;

/**
 * Neural Constellation Network – Upgraded Crystalline Aurora.
 * Features: Dynamic node connections, particle trails, cyberpunk themes.
 */
public class CircleStyle extends VisualStyle {
    private static final String TAG = "CircleStyle";

    // === CONFIG ===
    private static final int AURORA_SEGMENTS = 180;
    private static final int HISTORY_DEPTH = 5;
    private static final int MAX_NODES = 40;
    private static final int MAX_SHARDS = 16;
    private static final int TRAIL_LENGTH = 8;
    private static final float CONNECTION_DISTANCE = 0.25f;

    // Themes: 0=Aurora (rainbow), 1=Cyberpunk (amber/cyan), 2=Ocean (blue/green), 3=Fire (red/orange)
    private int colorTheme = 1; // Default cyberpunk
    private int theme = colorTheme;
    // Buffers
    private FloatBuffer auroraVertexBuffer;
    private ShortBuffer auroraIndexBuffer;
    private FloatBuffer nodeVertexBuffer;
    private FloatBuffer connectionBuffer;
    private FloatBuffer shardVertexBuffer;
    private FloatBuffer trailVertexBuffer;

    private int auroraVboId, nodeVboId, connectionVboId, shardVboId, trailVboId;
    private int auroraIboId;

    private boolean buffersInitialized = false;
    private float[][] historyRing = new float[HISTORY_DEPTH][AURORA_SEGMENTS];
    private int historyPointer = 0;
    private float smoothedBeat = 0f;
    private float globalPhase = 0f;
    private float[] nodePhase = new float[MAX_NODES];
    private float[] nodeEnergy = new float[MAX_NODES];
    private Random random = new Random(42);

    private ScheduledExecutorService colorChangeExecutor;

    private static class Shard {
        float angle, radius, rotation;
        float size, energy;
        int type;
        float[] trailX = new float[TRAIL_LENGTH];
        float[] trailY = new float[TRAIL_LENGTH];
        int trailIndex = 0;
    }
    private Shard[] shards = new Shard[MAX_SHARDS];

    @Override
    public void init(int shader2D, int shader3D, int shader3DUniform,
                     int texturedShader2D, int texturedShader3D,
                     GLVisualizerRenderer renderer) {
        super.init(shader2D, shader3D, shader3DUniform,
                   texturedShader2D, texturedShader3D, renderer);

        for (int i = 0; i < HISTORY_DEPTH; i++) {
            historyRing[i] = new float[AURORA_SEGMENTS];
        }

        float golden = 1.618033988749895f;
        for (int i = 0; i < MAX_NODES; i++) {
            nodePhase[i] = (i * golden) % 1.0f;
            nodeEnergy[i] = 0f;
        }

        for (int i = 0; i < MAX_SHARDS; i++) {
            shards[i] = new Shard();
            shards[i].type = i % 3;
        }

        allocateBuffers();

        // Initialize ScheduledExecutorService for color theme cycling
        colorChangeExecutor = Executors.newSingleThreadScheduledExecutor();
        colorChangeExecutor.scheduleAtFixedRate(
            new Runnable() {
                @Override
                public void run() {
                    int thm = (theme + 1) % 4; // Cycles 0→1→2→3→0
                    setColorTheme(thm);
                }
            },
            8, // Initial delay (seconds)
            8, // Period (seconds)
            TimeUnit.SECONDS
        );
    }

    private void allocateBuffers() {
        if (buffersInitialized) {
            int[] vbos = new int[]{auroraVboId, nodeVboId, connectionVboId, shardVboId, trailVboId};
            GLES20.glDeleteBuffers(5, vbos, 0);
            if (auroraIboId != 0) {
                GLES20.glDeleteBuffers(1, new int[]{auroraIboId}, 0);
            }
        }

        // Aurora ribbon
        int auroraFloats = AURORA_SEGMENTS * 2 * 7;
        ByteBuffer avb = ByteBuffer.allocateDirect(auroraFloats * 4);
        avb.order(ByteOrder.nativeOrder());
        auroraVertexBuffer = avb.asFloatBuffer();

        ByteBuffer aib = ByteBuffer.allocateDirect((AURORA_SEGMENTS + 1) * 2 * 2);
        aib.order(ByteOrder.nativeOrder());
        auroraIndexBuffer = aib.asShortBuffer();
        auroraIndexBuffer.clear();
        for (int i = 0; i <= AURORA_SEGMENTS; i++) {
            int idx = i % AURORA_SEGMENTS;
            auroraIndexBuffer.put((short)(idx * 2));
            auroraIndexBuffer.put((short)(idx * 2 + 1));
        }
        auroraIndexBuffer.flip();

        // Nodes
        ByteBuffer nvb = ByteBuffer.allocateDirect(MAX_NODES * 6 * 7 * 4);
        nvb.order(ByteOrder.nativeOrder());
        nodeVertexBuffer = nvb.asFloatBuffer();

        // Connections: max lines between nodes (n*(n-1)/2), each line = 2 verts * 7 floats
        int maxConnections = MAX_NODES * (MAX_NODES - 1) / 2;
        ByteBuffer cb = ByteBuffer.allocateDirect(maxConnections * 2 * 7 * 4);
        cb.order(ByteOrder.nativeOrder());
        connectionBuffer = cb.asFloatBuffer();

        // Shards
        ByteBuffer svb = ByteBuffer.allocateDirect(MAX_SHARDS * 18 * 3 * 7 * 4);
        svb.order(ByteOrder.nativeOrder());
        shardVertexBuffer = svb.asFloatBuffer();

        // Trails: TRAIL_LENGTH particles per shard, each = 6 verts (quad) * 7 floats
        ByteBuffer tvb = ByteBuffer.allocateDirect(MAX_SHARDS * TRAIL_LENGTH * 6 * 7 * 4);
        tvb.order(ByteOrder.nativeOrder());
        trailVertexBuffer = tvb.asFloatBuffer();

        int[] vbos = new int[5];
        GLES20.glGenBuffers(5, vbos, 0);
        auroraVboId = vbos[0];
        nodeVboId = vbos[1];
        connectionVboId = vbos[2];
        shardVboId = vbos[3];
        trailVboId = vbos[4];

        int[] ibos = new int[1];
        GLES20.glGenBuffers(1, ibos, 0);
        auroraIboId = ibos[0];

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, auroraVboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, auroraFloats * 4, null, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, nodeVboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, MAX_NODES * 6 * 7 * 4, null, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, connectionVboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, maxConnections * 2 * 7 * 4, null, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, shardVboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, MAX_SHARDS * 18 * 3 * 7 * 4, null, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, trailVboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, MAX_SHARDS * TRAIL_LENGTH * 6 * 7 * 4, null, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, auroraIboId);
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER,
                            auroraIndexBuffer.remaining() * 2, auroraIndexBuffer, GLES20.GL_STATIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

        buffersInitialized = true;
    }

    @Override
    public void update(float deltaTime, float[] fftBands, float beatIntensity, float totalTime) {
        try {
            this.totalTime = totalTime;
            if (fftBands == null || fftBands.length == 0) return;

            smoothedBeat += (beatIntensity - smoothedBeat) * 0.15f;
            globalPhase += deltaTime * (0.3f + smoothedBeat * 0.5f);

            historyPointer = (historyPointer + 1) % HISTORY_DEPTH;
            mapFftToRing(fftBands, historyRing[historyPointer]);

            updateAuroraRibbon(totalTime, beatIntensity);
            updateConstellationNodes(fftBands, totalTime, beatIntensity);
            updateConnections();
            updateCrystallineShards(beatIntensity, totalTime);
            updateTrails();

        } catch (Exception e) {
            Log.e(TAG, "Error in update: " + e.getMessage(), e);
        }
    }

    private void mapFftToRing(float[] fft, float[] ring) {
        int fftLen = fft.length;
        for (int i = 0; i < AURORA_SEGMENTS; i++) {
            float pos = (float)i / AURORA_SEGMENTS * fftLen;
            int idx = (int)pos;
            float t = pos - idx;
            idx = Math.min(idx, fftLen - 1);
            int idx2 = Math.min(idx + 1, fftLen - 1);
            ring[i] = (fft[idx] * (1-t) + fft[idx2] * t) * renderer.getSensitivity();
        }
    }

    private void updateAuroraRibbon(float time, float beat) {
        auroraVertexBuffer.clear();

        float baseR = 0.35f + smoothedBeat * 0.08f;

        for (int i = 0; i < AURORA_SEGMENTS; i++) {
            double angle = 2.0 * Math.PI * i / AURORA_SEGMENTS + globalPhase;

            float amplitude = 0f;
            for (int h = 0; h < HISTORY_DEPTH; h++) {
                int idx = (historyPointer - h + HISTORY_DEPTH) % HISTORY_DEPTH;
                float decay = 1.0f - (float)h / HISTORY_DEPTH;
                amplitude += historyRing[idx][i] * decay;
            }
            amplitude /= HISTORY_DEPTH;

            float wave = (float)(Math.sin(angle * 3 + time * 2) * 0.1f +
                Math.cos(angle * 5 - time * 1.5f) * 0.05f) * amplitude;

            float rOuter = baseR + amplitude * 0.5f + wave;
            float rInner = baseR - 0.06f + amplitude * 0.25f;

            if (beat > 0.6f) {
                float punch = (beat - 0.6f) * 0.2f * (float)Math.sin(angle * 8);
                rOuter += punch;
            }

            float xo = (float)Math.cos(angle) * rOuter;
            float yo = (float)Math.sin(angle) * rOuter;
            float zo = amplitude * 0.1f;

            float xi = (float)Math.cos(angle) * rInner;
            float yi = (float)Math.sin(angle) * rInner;
            float zi = -0.02f;

            float[] rgb = getThemeColor((float)i / AURORA_SEGMENTS + time * 0.08f,
                                        0.7f, 0.6f + amplitude * 0.5f + beat * 0.2f);
            float alpha = 0.5f + amplitude * 0.4f;

            auroraVertexBuffer.put(xo);
            auroraVertexBuffer.put(yo);
            auroraVertexBuffer.put(zo);
            auroraVertexBuffer.put(rgb[0]);
            auroraVertexBuffer.put(rgb[1]);
            auroraVertexBuffer.put(rgb[2]);
            auroraVertexBuffer.put(alpha);

            auroraVertexBuffer.put(xi);
            auroraVertexBuffer.put(yi);
            auroraVertexBuffer.put(zi);
            auroraVertexBuffer.put(rgb[0] * 0.6f);
            auroraVertexBuffer.put(rgb[1] * 0.6f);
            auroraVertexBuffer.put(rgb[2] * 0.7f);
            auroraVertexBuffer.put(alpha * 0.5f);
        }

        auroraVertexBuffer.flip();

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, auroraVboId);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0,
                               auroraVertexBuffer.remaining() * 4, auroraVertexBuffer);
    }

    private void updateConstellationNodes(float[] fft, float time, float beat) {
        nodeVertexBuffer.clear();

        float baseR = 0.6f + smoothedBeat * 0.1f;

        for (int i = 0; i < MAX_NODES; i++) {
            float phase = nodePhase[i] + time * 0.15f;
            double angle = 2.0 * Math.PI * phase;

            int fftIdx = (int)(nodePhase[i] * fft.length);
            fftIdx = Math.min(fftIdx, fft.length - 1);
            float energy = fft[fftIdx] * renderer.getSensitivity();
            nodeEnergy[i] = energy;

            float r = baseR + energy * 0.3f + (float)Math.sin(time * 3 + i) * 0.03f;
            r += beat * 0.08f;

            float x = (float)Math.cos(angle) * r;
            float y = (float)Math.sin(angle) * r;
            float z = 0.05f + energy * 0.15f;

            float size = 0.012f + energy * 0.03f + beat * 0.015f;

            float[] rgb = getThemeColor(nodePhase[i] + time * 0.05f, 0.8f, 0.9f);

            float[][] local = {{0, -size}, {size, 0}, {0, size}, {-size, 0}};
            int[] tri = {0, 1, 2, 0, 2, 3};

            for (int v = 0; v < 6; v++) {
                int vi = tri[v];
                float vx = x + local[vi][0];
                float vy = y + local[vi][1];

                nodeVertexBuffer.put(vx);
                nodeVertexBuffer.put(vy);
                nodeVertexBuffer.put(z);

                float dist = Math.abs(local[vi][0]) + Math.abs(local[vi][1]);
                float intensity = 1.0f - dist / size * 0.3f;

                nodeVertexBuffer.put(rgb[0] * intensity);
                nodeVertexBuffer.put(rgb[1] * intensity);
                nodeVertexBuffer.put(rgb[2] * intensity);
                nodeVertexBuffer.put(0.7f + energy * 0.3f);
            }
        }

        nodeVertexBuffer.flip();

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, nodeVboId);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0,
                               nodeVertexBuffer.remaining() * 4, nodeVertexBuffer);
    }

    private void updateConnections() {
        connectionBuffer.clear();

        float[] nodeX = new float[MAX_NODES];
        float[] nodeY = new float[MAX_NODES];
        float baseR = 0.6f + smoothedBeat * 0.1f;

        for (int i = 0; i < MAX_NODES; i++) {
            float phase = nodePhase[i] + totalTime * 0.15f;
            double angle = 2.0 * Math.PI * phase;
            float r = baseR + nodeEnergy[i] * 0.3f;
            nodeX[i] = (float)Math.cos(angle) * r;
            nodeY[i] = (float)Math.sin(angle) * r;
        }

        for (int i = 0; i < MAX_NODES; i++) {
            for (int j = i + 1; j < MAX_NODES; j++) {
                float dx = nodeX[i] - nodeX[j];
                float dy = nodeY[i] - nodeY[j];
                float dist = (float)Math.sqrt(dx * dx + dy * dy);

                if (dist < CONNECTION_DISTANCE) {
                    float alpha = (1.0f - dist / CONNECTION_DISTANCE) * 0.4f *
                        (nodeEnergy[i] + nodeEnergy[j]) * 0.5f;

                    float[] rgb = getThemeColor((nodePhase[i] + nodePhase[j]) * 0.5f, 0.6f, 0.8f);

                    connectionBuffer.put(nodeX[i]);
                    connectionBuffer.put(nodeY[i]);
                    connectionBuffer.put(0.03f);
                    connectionBuffer.put(rgb[0]);
                    connectionBuffer.put(rgb[1]);
                    connectionBuffer.put(rgb[2]);
                    connectionBuffer.put(alpha);

                    connectionBuffer.put(nodeX[j]);
                    connectionBuffer.put(nodeY[j]);
                    connectionBuffer.put(0.03f);
                    connectionBuffer.put(rgb[0]);
                    connectionBuffer.put(rgb[1]);
                    connectionBuffer.put(rgb[2]);
                    connectionBuffer.put(alpha);
                }
            }
        }

        connectionBuffer.flip();

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, connectionVboId);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0,
                               connectionBuffer.remaining() * 4, connectionBuffer);
    }

    private void updateCrystallineShards(float beat, float time) {
        shardVertexBuffer.clear();

        int activeShards = Math.max(2, (int)(beat * MAX_SHARDS));

        for (int i = 0; i < activeShards; i++) {
            if (beat > 0.5f && shards[i].energy < 0.1f && random.nextFloat() < 0.2f) {
                shards[i].angle = random.nextFloat() * (float)(2 * Math.PI);
                shards[i].radius = 0.15f + random.nextFloat() * 0.25f;
                shards[i].size = 0.04f + beat * 0.06f;
                shards[i].energy = 1.0f;
                shards[i].rotation = random.nextFloat() * (float)(Math.PI * 2);
                for (int t = 0; t < TRAIL_LENGTH; t++) {
                    shards[i].trailX[t] = (float)Math.cos(shards[i].angle) * shards[i].radius;
                    shards[i].trailY[t] = (float)Math.sin(shards[i].angle) * shards[i].radius;
                }
            }

            shards[i].trailIndex = (shards[i].trailIndex + 1) % TRAIL_LENGTH;
            shards[i].trailX[shards[i].trailIndex] = (float)Math.cos(shards[i].angle) * shards[i].radius;
            shards[i].trailY[shards[i].trailIndex] = (float)Math.sin(shards[i].angle) * shards[i].radius;

            shards[i].energy *= 0.92f;
            shards[i].rotation += 0.03f;

            float s = shards[i].size * shards[i].energy;
            if (s < 0.005f) continue;

            float r = shards[i].radius;
            float cx = (float)Math.cos(shards[i].angle) * r;
            float cy = (float)Math.sin(shards[i].angle) * r;
            float cz = 0.1f;

            float cosR = (float)Math.cos(shards[i].rotation);
            float sinR = (float)Math.sin(shards[i].rotation);

            float[] rgb = getThemeColor(0.55f + shards[i].energy * 0.15f,
                                        0.5f + shards[i].energy * 0.3f, 0.95f);
            float alpha = shards[i].energy * 0.8f;

            if (shards[i].type == 0) {
                for (int v = 0; v < 3; v++) {
                    double va = shards[i].rotation + v * 2.0944;
                    float lx = (float)Math.cos(va) * s;
                    float ly = (float)Math.sin(va) * s;

                    shardVertexBuffer.put(cx + lx);
                    shardVertexBuffer.put(cy + ly);
                    shardVertexBuffer.put(cz);
                    shardVertexBuffer.put(rgb[0]);
                    shardVertexBuffer.put(rgb[1]);
                    shardVertexBuffer.put(rgb[2]);
                    shardVertexBuffer.put(alpha);
                }
            } else if (shards[i].type == 1) {
                float[][] v = {{0,s}, {s,0}, {0,-s}, {-s,0}};
                int[] idx = {0,1,2, 0,2,3};
                for (int k = 0; k < 6; k++) {
                    int vi = idx[k];
                    float lx = v[vi][0] * cosR - v[vi][1] * sinR;
                    float ly = v[vi][0] * sinR + v[vi][1] * cosR;
                    shardVertexBuffer.put(cx + lx);
                    shardVertexBuffer.put(cy + ly);
                    shardVertexBuffer.put(cz);
                    shardVertexBuffer.put(rgb[0]);
                    shardVertexBuffer.put(rgb[1]);
                    shardVertexBuffer.put(rgb[2]);
                    shardVertexBuffer.put(alpha);
                }
            } else {
                for (int t = 0; t < 6; t++) {
                    shardVertexBuffer.put(cx);
                    shardVertexBuffer.put(cy);
                    shardVertexBuffer.put(cz);
                    shardVertexBuffer.put(rgb[0]);
                    shardVertexBuffer.put(rgb[1]);
                    shardVertexBuffer.put(rgb[2]);
                    shardVertexBuffer.put(alpha);

                    for (int e = 0; e < 2; e++) {
                        double va = shards[i].rotation + (t + e) * 1.0472;
                        float lx = (float)Math.cos(va) * s;
                        float ly = (float)Math.sin(va) * s;
                        shardVertexBuffer.put(cx + lx);
                        shardVertexBuffer.put(cy + ly);
                        shardVertexBuffer.put(cz);
                        shardVertexBuffer.put(rgb[0] * 0.8f);
                        shardVertexBuffer.put(rgb[1] * 0.8f);
                        shardVertexBuffer.put(rgb[2] * 0.9f);
                        shardVertexBuffer.put(alpha * 0.8f);
                    }
                }
            }
        }

        shardVertexBuffer.flip();

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, shardVboId);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0,
                               shardVertexBuffer.remaining() * 4, shardVertexBuffer);
    }

    private void updateTrails() {
        trailVertexBuffer.clear();

        for (int i = 0; i < MAX_SHARDS; i++) {
            if (shards[i].energy < 0.1f) continue;

            float[] rgb = getThemeColor(0.6f, 0.7f, 0.9f);

            for (int t = 0; t < TRAIL_LENGTH - 1; t++) {
                int idx = (shards[i].trailIndex - t + TRAIL_LENGTH) % TRAIL_LENGTH;
                int idx2 = (idx - 1 + TRAIL_LENGTH) % TRAIL_LENGTH;

                float x1 = shards[i].trailX[idx];
                float y1 = shards[i].trailY[idx];
                float x2 = shards[i].trailX[idx2];
                float y2 = shards[i].trailY[idx2];

                float size = 0.008f * (1.0f - (float)t / TRAIL_LENGTH) * shards[i].energy;
                float alpha = 0.3f * (1.0f - (float)t / TRAIL_LENGTH) * shards[i].energy;

                if (size < 0.001f) continue;

                float[][] local = {{-size,-size}, {size,-size}, {size,size}, {-size,size}};
                int[] tri = {0,1,2, 0,2,3};

                for (int v = 0; v < 6; v++) {
                    int vi = tri[v];
                    trailVertexBuffer.put(x1 + local[vi][0]);
                    trailVertexBuffer.put(y1 + local[vi][1]);
                    trailVertexBuffer.put(0.08f);
                    trailVertexBuffer.put(rgb[0]);
                    trailVertexBuffer.put(rgb[1]);
                    trailVertexBuffer.put(rgb[2]);
                    trailVertexBuffer.put(alpha);
                }
            }
        }

        trailVertexBuffer.flip();

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, trailVboId);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0,
                               trailVertexBuffer.remaining() * 4, trailVertexBuffer);
    }

    private float[] getThemeColor(float hue, float saturation, float brightness) {
        float[] rgb = new float[3];

        switch (colorTheme) {
            case 1: // Cyberpunk: Amber/Cyan split
                hue = hue % 1.0f;
                if (hue < 0.5f) {
                    hue = 0.08f + hue * 0.15f;
                } else {
                    hue = 0.45f + (hue - 0.5f) * 0.2f;
                }
                break;
            case 2: // Ocean: Deep blues and teals
                hue = 0.5f + hue * 0.25f;
                saturation *= 0.8f;
                break;
            case 3: // Fire: Reds, oranges, yellows
                hue = hue * 0.18f;
                saturation = Math.min(1.0f, saturation * 1.2f);
                break;
        }

        hue = hue % 1.0f;
        if (hue < 0) hue += 1.0f;

        int i = (int)(hue * 6);
        float f = hue * 6 - i;
        float p = brightness * (1 - saturation);
        float q = brightness * (1 - f * saturation);
        float t = brightness * (1 - (1 - f) * saturation);

        switch (i % 6) {
            case 0: rgb[0] = brightness; rgb[1] = t; rgb[2] = p; break;
            case 1: rgb[0] = q; rgb[1] = brightness; rgb[2] = p; break;
            case 2: rgb[0] = p; rgb[1] = brightness; rgb[2] = t; break;
            case 3: rgb[0] = p; rgb[1] = q; rgb[2] = brightness; break;
            case 4: rgb[0] = t; rgb[1] = p; rgb[2] = brightness; break;
            case 5: rgb[0] = brightness; rgb[1] = p; rgb[2] = q; break;
        }
        return rgb;
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
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);

        // === AURORA RIBBON ===
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, auroraVboId);
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 7 * 4, 0);
        GLES20.glEnableVertexAttribArray(posHandle);

        if (colHandle != -1) {
            GLES20.glVertexAttribPointer(colHandle, 4, GLES20.GL_FLOAT, false, 7 * 4, 3 * 4);
            GLES20.glEnableVertexAttribArray(colHandle);
        }

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, auroraIboId);
        GLES20.glDrawElements(GLES20.GL_TRIANGLE_STRIP,
                              (AURORA_SEGMENTS + 1) * 2,
                              GLES20.GL_UNSIGNED_SHORT, 0);

        // === NODE CONNECTIONS ===
        int connectionVerts = connectionBuffer.position() / 7;
        if (connectionVerts > 0) {
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, connectionVboId);
            GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 7 * 4, 0);
            if (colHandle != -1) {
                GLES20.glVertexAttribPointer(colHandle, 4, GLES20.GL_FLOAT, false, 7 * 4, 3 * 4);
            }
            GLES20.glLineWidth(1.0f);
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, connectionVerts);
        }

        // === CONSTELLATION NODES ===
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, nodeVboId);
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 7 * 4, 0);

        if (colHandle != -1) {
            GLES20.glVertexAttribPointer(colHandle, 4, GLES20.GL_FLOAT, false, 7 * 4, 3 * 4);
        }

        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, MAX_NODES * 6);

        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        // === SHARD TRAILS ===
        int trailVerts = trailVertexBuffer.position() / 7;
        if (trailVerts > 0) {
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, trailVboId);
            GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 7 * 4, 0);
            if (colHandle != -1) {
                GLES20.glVertexAttribPointer(colHandle, 4, GLES20.GL_FLOAT, false, 7 * 4, 3 * 4);
            }
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, trailVerts);
        }

        // === CRYSTALLINE SHARDS ===
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, shardVboId);
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 7 * 4, 0);

        if (colHandle != -1) {
            GLES20.glVertexAttribPointer(colHandle, 4, GLES20.GL_FLOAT, false, 7 * 4, 3 * 4);
        }

        int shardVerts = shardVertexBuffer.position() / 7;
        if (shardVerts > 0) {
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, shardVerts);
        }

        GLES20.glDisableVertexAttribArray(posHandle);
        if (colHandle != -1) GLES20.glDisableVertexAttribArray(colHandle);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
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
        try {
            if (colorChangeExecutor != null) {
                colorChangeExecutor.shutdownNow();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error shutting down executor: " + e.getMessage(), e);
        }

        if (buffersInitialized) {
            GLES20.glDeleteBuffers(5, new int[]{auroraVboId, nodeVboId, connectionVboId, shardVboId, trailVboId}, 0);
            if (auroraIboId != 0) {
                GLES20.glDeleteBuffers(1, new int[]{auroraIboId}, 0);
            }
            buffersInitialized = false;
        }
    }

    @Override
    public boolean usesTexture() {
        return false;
    }

    public void setColorTheme(int theme) {
        if (theme >= 0 && theme <= 3) {
            this.colorTheme = theme;
        }
    }
}
