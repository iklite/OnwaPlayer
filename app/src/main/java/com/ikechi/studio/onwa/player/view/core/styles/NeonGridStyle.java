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
 * QUANTUM LATTICE NEXUS
 * A mind-bending dimensional gateway featuring:
 * - Impossible Penrose-style geometry with recursive depth
 * - Volumetric energy beams (thick line quads)
 * - Pulsing concentric ring tunnels with perspective distortion
 * - Particle energy nodes at grid intersections
 * - Chromatic aberration and holographic flicker
 * - Dimensional rift background with parallax layers
 */
public class NeonGridStyle extends VisualStyle {

    // === ENHANCED CONSTANTS ===
    private static final int GRID_LINES = 32; // More lines for density
    private static final int RING_SEGMENTS = 72; // Smooth rings
    private static final int RING_COUNT = 5; // Tunnel depth
    private static final int MAX_NODES = 64; // Energy nodes at intersections
    private static final int BEAM_COUNT = 16; // Volumetric light beams

    // Vertex counts
    private static final int GRID_VERTEX_COUNT = GRID_LINES * 4; // 2 per line
    private static final int THICK_LINE_VERTS = 6; // 2 triangles per thick line
    private static final int RING_VERTEX_COUNT = RING_SEGMENTS * 2 * RING_COUNT;

    // === BUFFERS ===
    private FloatBuffer gridVertexBuffer; // Static grid positions
    private FloatBuffer gridColorBuffer;
    private FloatBuffer thickLineVerts, thickLineColors; // For wide beams
    private FloatBuffer ringVerts, ringColors;
    private FloatBuffer nodeVerts, nodeColors;
    private FloatBuffer riftVerts, riftColors;

    // === VBOs ===
    private int gridVbo, gridColorVbo;
    private int thickLineVbo, thickLineColorVbo;
    private int ringVbo, ringColorVbo;
    private int nodeVbo, nodeColorVbo;
    private int riftVbo, riftColorVbo;

    private boolean initialized = false;

    // === STATE ===
    private final float[] gridVertices = new float[GRID_VERTEX_COUNT * 3];
    private final float[] nodeParticles = new float[MAX_NODES * 6]; // x,y,z,life,energy,hue
    private final Random rng = new Random();

    private float tunnelPhase = 0f;
    private float chromaticPhase = 0f;
    private float lastBeatIntensity = 0f;
    private float beatFlash = 0f;
    private float dimensionalShift = 0f;
    private int activeNodeVertices = 0;

    @Override
    public void init(int shader2D, int shader3D, int shader3DUniform,
                     int texturedShader2D, int texturedShader3D,
                     GLVisualizerRenderer renderer) {
        super.init(shader2D, shader3D, shader3DUniform,
                   texturedShader2D, texturedShader3D, renderer);
        buildQuantumLattice();
    }

    private void buildQuantumLattice() {
        if (initialized) {
            int[] vbos = new int[10];
            GLES20.glGenBuffers(10, vbos, 0);
        }

        // === Build static impossible grid ===
        // Create Penrose-style impossible geometry with recursive depth
        int idx = 0;
        for (int i = 0; i < GRID_LINES; i++) {
            float pos = -1.2f + 2.4f * i / (GRID_LINES - 1);

            // Horizontal lines with Z-depth variation (impossible geometry)
            float zH = (i % 2 == 0) ? 0f : -0.3f; // Alternating depth
            gridVertices[idx++] = -1.2f; 
            gridVertices[idx++] = pos; 
            gridVertices[idx++] = zH;

            gridVertices[idx++] = 1.2f; 
            gridVertices[idx++] = pos; 
            gridVertices[idx++] = zH;

            // Vertical lines with perspective distortion
            float zV = (i % 2 == 0) ? -0.15f : 0.15f;
            gridVertices[idx++] = pos; 
            gridVertices[idx++] = -1.2f; 
            gridVertices[idx++] = zV;

            gridVertices[idx++] = pos; 
            gridVertices[idx++] = 1.2f; 
            gridVertices[idx++] = zV;
        }

        // Allocate buffers
        ByteBuffer vbb = ByteBuffer.allocateDirect(gridVertices.length * 4);
        vbb.order(ByteOrder.nativeOrder());
        gridVertexBuffer = vbb.asFloatBuffer();
        gridVertexBuffer.put(gridVertices).position(0);

        ByteBuffer cbb = ByteBuffer.allocateDirect(GRID_VERTEX_COUNT * 4 * 4);
        cbb.order(ByteOrder.nativeOrder());
        gridColorBuffer = cbb.asFloatBuffer();

        // Thick line buffers (for wide beams)
        ByteBuffer tlb = ByteBuffer.allocateDirect(BEAM_COUNT * THICK_LINE_VERTS * 3 * 4);
        tlb.order(ByteOrder.nativeOrder());
        thickLineVerts = tlb.asFloatBuffer();

        ByteBuffer tlc = ByteBuffer.allocateDirect(BEAM_COUNT * THICK_LINE_VERTS * 4 * 4);
        tlc.order(ByteOrder.nativeOrder());
        thickLineColors = tlc.asFloatBuffer();

        // Ring tunnel buffers
        ByteBuffer rb = ByteBuffer.allocateDirect(RING_VERTEX_COUNT * 3 * 4);
        rb.order(ByteOrder.nativeOrder());
        ringVerts = rb.asFloatBuffer();

        ByteBuffer rcb = ByteBuffer.allocateDirect(RING_VERTEX_COUNT * 4 * 4);
        rcb.order(ByteOrder.nativeOrder());
        ringColors = rcb.asFloatBuffer();

        // Node buffers
        ByteBuffer nb = ByteBuffer.allocateDirect(MAX_NODES * 6 * 3 * 4); // Quads
        nb.order(ByteOrder.nativeOrder());
        nodeVerts = nb.asFloatBuffer();

        ByteBuffer ncb = ByteBuffer.allocateDirect(MAX_NODES * 6 * 4 * 4);
        ncb.order(ByteOrder.nativeOrder());
        nodeColors = ncb.asFloatBuffer();

        // Dimensional rift background
        ByteBuffer drb = ByteBuffer.allocateDirect(12 * 3 * 4); // 2 large quads
        drb.order(ByteOrder.nativeOrder());
        riftVerts = drb.asFloatBuffer();

        ByteBuffer drcb = ByteBuffer.allocateDirect(12 * 4 * 4);
        drcb.order(ByteOrder.nativeOrder());
        riftColors = drcb.asFloatBuffer();

        // Generate VBOs
        int[] vbos = new int[10];
        GLES20.glGenBuffers(10, vbos, 0);
        gridVbo = vbos[0];
        gridColorVbo = vbos[1];
        thickLineVbo = vbos[2];
        thickLineColorVbo = vbos[3];
        ringVbo = vbos[4];
        ringColorVbo = vbos[5];
        nodeVbo = vbos[6];
        nodeColorVbo = vbos[7];
        riftVbo = vbos[8];
        riftColorVbo = vbos[9];

        // Upload static grid
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, gridVbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, gridVertices.length * 4, gridVertexBuffer, GLES20.GL_STATIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, gridColorVbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, GRID_VERTEX_COUNT * 4 * 4, null, GLES20.GL_DYNAMIC_DRAW);

        // Allocate dynamic buffers
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, thickLineVbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, BEAM_COUNT * THICK_LINE_VERTS * 3 * 4, null, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, thickLineColorVbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, BEAM_COUNT * THICK_LINE_VERTS * 4 * 4, null, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ringVbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, RING_VERTEX_COUNT * 3 * 4, null, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ringColorVbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, RING_VERTEX_COUNT * 4 * 4, null, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, nodeVbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, MAX_NODES * 6 * 3 * 4, null, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, nodeColorVbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, MAX_NODES * 6 * 4 * 4, null, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, riftVbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 12 * 3 * 4, null, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, riftColorVbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 12 * 4 * 4, null, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        initialized = true;
    }

    @Override
    public void update(float deltaTime, float[] fftBands, float beatIntensity, float totalTime) {
        this.totalTime = totalTime;

        tunnelPhase += deltaTime * 2f;
        chromaticPhase += deltaTime * 3f;

        // Beat detection
        boolean beatTriggered = beatIntensity > 0.65f && lastBeatIntensity < 0.35f;
        if (beatTriggered) {
            beatFlash = 1f;
            dimensionalShift = 0.5f;
        }
        beatFlash *= 0.9f;
        dimensionalShift *= 0.95f;
        lastBeatIntensity = beatIntensity;

        // Update all dynamic geometry
        updateGridColors(fftBands, beatIntensity);
        updateThickEnergyBeams(fftBands, beatIntensity);
        updateTunnelRings(beatIntensity);
        updateEnergyNodes(deltaTime, beatTriggered, beatIntensity);
        updateDimensionalRift(beatIntensity);
    }

    private void updateGridColors(float[] fftBands, float beatIntensity) {
        gridColorBuffer.clear();

        for (int v = 0; v < GRID_VERTEX_COUNT; v++) {
            int lineIndex = v / 2;

            // Quantum color cycling with chromatic aberration
            float hueBase = (totalTime * 0.5f + lineIndex * 0.1f) % 6.283f;
            float hueShift = dimensionalShift * (float) Math.sin(chromaticPhase + lineIndex);

            float r = 0.5f + 0.5f * (float) Math.sin(hueBase + hueShift);
            float g = 0.5f + 0.5f * (float) Math.sin(hueBase + 2.1f + hueShift * 0.5f);
            float b = 0.5f + 0.5f * (float) Math.sin(hueBase + 4.2f + hueShift * 0.3f);

            // FFT-reactive brightness per line
            float brightness = 0.4f + beatIntensity * 0.6f;
            if (fftBands != null && fftBands.length > 0) {
                int bandIdx = (lineIndex * fftBands.length) / (GRID_LINES * 2);
                bandIdx = Math.min(bandIdx, fftBands.length - 1);
                brightness += fftBands[bandIdx] * 0.8f;
            }

            // Dimensional rift effect on beats
            if (beatFlash > 0.3f && lineIndex % 4 == 0) {
                r = 1f; g = 1f; b = 1f; brightness = 1f;
            }

            float a = Math.min(0.3f + brightness * 0.5f, 0.9f);

            gridColorBuffer.put(r * brightness);
            gridColorBuffer.put(g * brightness);
            gridColorBuffer.put(b * brightness);
            gridColorBuffer.put(a);
        }

        gridColorBuffer.flip();
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, gridColorVbo);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, gridColorBuffer.capacity() * 4, gridColorBuffer);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    // === THICK ENERGY BEAMS ===
    private void updateThickEnergyBeams(float[] fftBands, float beatIntensity) {
        thickLineVerts.clear();
        thickLineColors.clear();

        int beamsToDraw = BEAM_COUNT;

        for (int i = 0; i < beamsToDraw; i++) {
            // Create beams from center radiating outward
            float angle = (i / (float) beamsToDraw) * 6.283f + totalTime * 0.2f;
            float length = 0.5f + (fftBands != null && i < fftBands.length ? fftBands[i % fftBands.length] * 1.5f : 0f);
            length *= (1f + beatFlash * 0.5f);

            float x1 = (float) Math.cos(angle) * 0.1f;
            float y1 = (float) Math.sin(angle) * 0.1f;
            float x2 = (float) Math.cos(angle) * (0.1f + length);
            float y2 = (float) Math.sin(angle) * (0.1f + length);

            // Thick line width varies with intensity
            float width = 0.02f + beatIntensity * 0.03f + (fftBands != null && i < fftBands.length ? fftBands[i % fftBands.length] * 0.05f : 0f);

            // Perpendicular vector for width
            float dx = x2 - x1;
            float dy = y2 - y1;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            float nx = -dy / len * width;
            float ny = dx / len * width;

            // Quad vertices (2 triangles)
            float z = 0.05f + i * 0.01f;

            // Tri 1
            thickLineVerts.put(x1 + nx); thickLineVerts.put(y1 + ny); thickLineVerts.put(z);
            thickLineVerts.put(x2 + nx); thickLineVerts.put(y2 + ny); thickLineVerts.put(z);
            thickLineVerts.put(x1 - nx); thickLineVerts.put(y1 - ny); thickLineVerts.put(z);

            // Tri 2
            thickLineVerts.put(x2 + nx); thickLineVerts.put(y2 + ny); thickLineVerts.put(z);
            thickLineVerts.put(x2 - nx); thickLineVerts.put(y2 - ny); thickLineVerts.put(z);
            thickLineVerts.put(x1 - nx); thickLineVerts.put(y1 - ny); thickLineVerts.put(z);

            // Energy beam colors - hot core, cool edges
            float r = 1f, g = 0.8f + beatFlash * 0.2f, b = 0.4f + beatFlash * 0.4f;
            float a = 0.6f + beatIntensity * 0.4f;

            for (int v = 0; v < 6; v++) {
                thickLineColors.put(r);
                thickLineColors.put(g);
                thickLineColors.put(b);
                thickLineColors.put(a * (v < 3 ? 1f : 0.7f)); // Gradient
            }
        }

        thickLineVerts.flip();
        thickLineColors.flip();

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, thickLineVbo);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, beamsToDraw * THICK_LINE_VERTS * 3 * 4, thickLineVerts);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, thickLineColorVbo);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, beamsToDraw * THICK_LINE_VERTS * 4 * 4, thickLineColors);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    // === TUNNEL RINGS ===
    private void updateTunnelRings(float beatIntensity) {
        ringVerts.clear();
        ringColors.clear();

        float cx = 0f, cy = 0f;

        for (int ring = 0; ring < RING_COUNT; ring++) {
            // Tunnel perspective - rings get smaller as they go deeper
            float depth = ring * 0.15f;
            float z = -0.5f - depth;
            float perspective = 1f / (1f + depth * 2f);
            float baseRadius = (0.8f - ring * 0.1f) * perspective * (1f + beatFlash * 0.3f);

            // Pulsing animation
            float pulse = (float) Math.sin(tunnelPhase + ring * 0.8f) * 0.1f + 1f;
            float radius = baseRadius * pulse;

            // Rotation
            float rot = totalTime * (0.3f + ring * 0.1f) * (1f + beatIntensity);

            for (int i = 0; i < RING_SEGMENTS; i++) {
                float angle1 = (i / (float) RING_SEGMENTS) * 6.283f + rot;
                float angle2 = ((i + 1) / (float) RING_SEGMENTS) * 6.283f + rot;

                float x1 = cx + radius * (float) Math.cos(angle1);
                float y1 = cy + radius * (float) Math.sin(angle1);
                float x2 = cx + radius * (float) Math.cos(angle2);
                float y2 = cy + radius * (float) Math.sin(angle2);

                ringVerts.put(x1); ringVerts.put(y1); ringVerts.put(z);
                ringVerts.put(x2); ringVerts.put(y2); ringVerts.put(z);

                // Neon cyan to purple gradient
                float hue = (ring + i / (float) RING_SEGMENTS) / RING_COUNT;
                float r = 0.2f + hue * 0.6f;
                float g = 0.8f - hue * 0.3f;
                float b = 1f;
                float a = (0.4f - ring * 0.05f) * (1f + beatFlash * 0.5f);

                ringColors.put(r); ringColors.put(g); ringColors.put(b); ringColors.put(a);
                ringColors.put(r); ringColors.put(g); ringColors.put(b); ringColors.put(a);
            }
        }

        ringVerts.flip();
        ringColors.flip();

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ringVbo);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, ringVerts.capacity() * 4, ringVerts);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ringColorVbo);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, ringColors.capacity() * 4, ringColors);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    // === ENERGY NODES ===
    private void updateEnergyNodes(float deltaTime, boolean spawnBurst, float beatIntensity) {
        // Spawn nodes on beats
        if (spawnBurst) {
            for (int i = 0; i < MAX_NODES; i++) {
                if (nodeParticles[i * 6 + 3] <= 0f) {
                    // Spawn at grid intersection
                    int gx = rng.nextInt(GRID_LINES);
                    int gy = rng.nextInt(GRID_LINES);
                    float x = -1.2f + 2.4f * gx / (GRID_LINES - 1);
                    float y = -1.2f + 2.4f * gy / (GRID_LINES - 1);

                    nodeParticles[i * 6] = x;
                    nodeParticles[i * 6 + 1] = y;
                    nodeParticles[i * 6 + 2] = 0.1f + rng.nextFloat() * 0.2f;
                    nodeParticles[i * 6 + 3] = 1f; // life
                    nodeParticles[i * 6 + 4] = beatIntensity; // energy
                    nodeParticles[i * 6 + 5] = rng.nextFloat(); // hue
                    break;
                }
            }
        }

        nodeVerts.clear();
        nodeColors.clear();
        int verticesWritten = 0;

        for (int i = 0; i < MAX_NODES; i++) {
            float life = nodeParticles[i * 6 + 3];
            if (life > 0f) {
                life -= deltaTime * 0.8f;
                nodeParticles[i * 6 + 3] = life;

                float px = nodeParticles[i * 6];
                float py = nodeParticles[i * 6 + 1];
                float pz = nodeParticles[i * 6 + 2];
                float energy = nodeParticles[i * 6 + 4];
                float hue = nodeParticles[i * 6 + 5];

                float size = 0.03f * life * (1f + energy);

                // Glowing node quad
                addNodeQuad(px, py, pz, size, hue, life, energy);
                verticesWritten += 6;
            }
        }

        nodeVerts.flip();
        nodeColors.flip();

        activeNodeVertices = verticesWritten;
        if (verticesWritten > 0) {
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, nodeVbo);
            GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, verticesWritten * 3 * 4, nodeVerts);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, nodeColorVbo);
            GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, verticesWritten * 4 * 4, nodeColors);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        }
    }

    private void addNodeQuad(float x, float y, float z, float size, float hue, float life, float energy) {
        float r = 0.5f + hue * 0.5f;
        float g = 0.8f;
        float b = 1f - hue * 0.3f;
        float a = life * (0.8f + energy * 0.4f);

        // Center glow
        float[][] corners = {
            {x - size, y - size}, {x + size, y - size}, {x - size, y + size},
            {x + size, y - size}, {x + size, y + size}, {x - size, y + size}
        };

        for (int i = 0; i < 6; i++) {
            nodeVerts.put(corners[i][0]);
            nodeVerts.put(corners[i][1]);
            nodeVerts.put(z);

            float alpha = a * (i < 3 ? 1f : 0.6f);
            nodeColors.put(r);
            nodeColors.put(g);
            nodeColors.put(b);
            nodeColors.put(alpha);
        }
    }

    // === DIMENSIONAL RIFT BACKGROUND ===
    private void updateDimensionalRift(float beatIntensity) {
        riftVerts.clear();
        riftColors.clear();

        // Two massive quads forming a cross/rift
        float driftX = (float) Math.sin(totalTime * 0.1f) * 0.2f;
        float driftY = (float) Math.cos(totalTime * 0.13f) * 0.15f;

        float size = 3f + beatFlash * 0.5f;

        // Horizontal rift quad
        float r = 0.1f, g = 0.05f, b = 0.2f;
        float a = 0.3f + beatIntensity * 0.2f;

        addRiftQuad(-size + driftX, -0.5f + driftY, -2f, 
                    size + driftX, -0.5f + driftY, -2f,
                    -size + driftX, 0.5f + driftY, -2f,
                    size + driftX, 0.5f + driftY, -2f,
                    r, g, b, a);

        // Vertical rift quad
        addRiftQuad(-0.5f + driftX, -size + driftY, -2.1f,
                    0.5f + driftX, -size + driftY, -2.1f,
                    -0.5f + driftX, size + driftY, -2.1f,
                    0.5f + driftX, size + driftY, -2.1f,
                    r * 0.8f, g * 0.8f, b * 1.2f, a * 0.8f);

        riftVerts.flip();
        riftColors.flip();

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, riftVbo);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, riftVerts.capacity() * 4, riftVerts);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, riftColorVbo);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, riftColors.capacity() * 4, riftColors);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    private void addRiftQuad(float x1, float y1, float z1, float x2, float y2, float z2,
							 float x3, float y3, float z3, float x4, float y4, float z4,
							 float r, float g, float b, float a) {
        // Tri 1
        riftVerts.put(x1); riftVerts.put(y1); riftVerts.put(z1);
        riftVerts.put(x2); riftVerts.put(y2); riftVerts.put(z2);
        riftVerts.put(x3); riftVerts.put(y3); riftVerts.put(z3);

        riftColors.put(r); riftColors.put(g); riftColors.put(b); riftColors.put(a);
        riftColors.put(r); riftColors.put(g); riftColors.put(b); riftColors.put(a);
        riftColors.put(r * 1.3f); riftColors.put(g * 1.3f); riftColors.put(b * 1.3f); riftColors.put(a * 1.2f);

        // Tri 2
        riftVerts.put(x2); riftVerts.put(y2); riftVerts.put(z2);
        riftVerts.put(x4); riftVerts.put(y4); riftVerts.put(z4);
        riftVerts.put(x3); riftVerts.put(y3); riftVerts.put(z3);

        riftColors.put(r); riftColors.put(g); riftColors.put(b); riftColors.put(a);
        riftColors.put(r * 1.3f); riftColors.put(g * 1.3f); riftColors.put(b * 1.3f); riftColors.put(a * 1.2f);
        riftColors.put(r * 1.3f); riftColors.put(g * 1.3f); riftColors.put(b * 1.3f); riftColors.put(a * 1.2f);
    }

    // === RENDERING ===
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

        GLES20.glEnable(GLES20.GL_BLEND);

        // 1. Dimensional rift background (dark purple cross)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        drawLayer(riftVbo, riftColorVbo, posHandle, colHandle,
                  GLES20.GL_TRIANGLES, 12, 0);

        // 2. Tunnel rings (neon cyan/purple concentric circles)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);
        drawLayer(ringVbo, ringColorVbo, posHandle, colHandle,
                  GLES20.GL_LINES, RING_VERTEX_COUNT, 2f);

        // 3. Impossible geometry grid with chromatic aberration
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        drawLayer(gridVbo, gridColorVbo, posHandle, colHandle,
                  GLES20.GL_LINES, GRID_VERTEX_COUNT, 1f);

        // 4. Thick energy beams (volumetric light)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);
        drawLayer(thickLineVbo, thickLineColorVbo, posHandle, colHandle,
                  GLES20.GL_TRIANGLES, BEAM_COUNT * THICK_LINE_VERTS, 0);

        // 5. Glowing energy nodes at intersections
        if (activeNodeVertices > 0) {
            drawLayer(nodeVbo, nodeColorVbo, posHandle, colHandle,
                      GLES20.GL_TRIANGLES, activeNodeVertices, 0);
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
        float tilt = renderer.getBeatIntensity() * 15f;
        Matrix.rotateM(rotated, 0, mvpMatrix, 0, tilt, 0f, 1f, 0f);
        Matrix.rotateM(rotated, 0, rotated, 0, tilt * 0.5f, 1f, 0f, 0f);
        drawCommon(rotated, shaderProgram3D, renderer.getBeatIntensity());
    }

    @Override
    public void release() {
        if (initialized) {
            int[] vbos = new int[]{gridVbo, gridColorVbo, thickLineVbo, thickLineColorVbo,
				ringVbo, ringColorVbo, nodeVbo, nodeColorVbo,
				riftVbo, riftColorVbo};
            GLES20.glDeleteBuffers(10, vbos, 0);
            initialized = false;
        }
    }
}

