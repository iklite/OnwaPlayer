package com.ikechi.studio.onwa.player.view.core.styles;

import android.opengl.GLES20;
import com.ikechi.studio.onwa.player.view.core.GLVisualizerRenderer;
import com.ikechi.studio.onwa.player.view.core.visuals.VisualStyle;
import com.ikechi.studio.onwa.player.view.core.objects.Particle;
import com.ikechi.studio.onwa.player.utils.TextureHelper;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Black‑Hole visualisation – accretion disk, event‑horizon glow,
 * orbiting particles, and a central dark singularity.
 *
 * <p>The whole scene is rendered with the same textured shader
 * that the original used.  All geometry is packed into a dynamic VBO.</p>
 */
public class BlackHoleStyle extends VisualStyle {
    private static final Random RAND = new Random();

    // ── Layout constants ──────────────────────────────────────────────
    private static final int    MAX_PARTICLES       = 600;
    private static final float  HOLE_RADIUS         = 0.12f;   // black disc
    private static final float  HORIZON_RING_INNER  = 0.13f;
    private static final float  HORIZON_RING_OUTER  = 0.18f;
    private static final float  DISK_INNER          = 0.18f;
    private static final float  DISK_OUTER          = 0.65f;
    private static final float  GRAVITY             = 0.8f;
    private static final int    RING_SEGMENTS       = 64;
    private static final int    SAFETY_MARGIN       = 512;   // ample headroom

    // ── State ─────────────────────────────────────────────────────────
    private final List<Particle> particles = new ArrayList<>(MAX_PARTICLES);
    private int  sunTexture;
    private int  particleVboId;
    private FloatBuffer particleVertexBuffer;
    private int  particleVertexCount;
    private boolean buffersInitialized;
    private float globalTime;
    private float smoothedEnergy;

    // ── Shader compatibility ──────────────────────────────────────────
    @Override public boolean usesTexture() { return true; }

    // ── Initialisation ────────────────────────────────────────────────
    @Override
    public void init(int shader2D, int shader3D, int shader3DUniform,
                     int texturedShader2D, int texturedShader3D,
                     GLVisualizerRenderer renderer) {
        super.init(shader2D, shader3D, shader3DUniform,
                   texturedShader2D, texturedShader3D, renderer);

        if (buffersInitialized) {
            GLES20.glDeleteBuffers(1, new int[]{particleVboId}, 0);
            GLES20.glDeleteTextures(1, new int[]{sunTexture}, 0);
        }

        sunTexture = TextureHelper.createCircleTexture();
        particles.clear();
        for (int i = 0; i < MAX_PARTICLES; i++) {
            particles.add(new Particle());
        }

        allocateBuffers();
        buffersInitialized = true;
    }

    private void allocateBuffers() {
        // Particles: 600 × 6 vertices
        // Event horizon ring: 64 segments × 6 vertices
        // Accretion disk: 64 segments × 6 vertices
        // Safety margin extra
        particleVertexCount = MAX_PARTICLES * 6
			+ RING_SEGMENTS * 6 * 2   // two rings
			+ SAFETY_MARGIN;
        int stride = 3 + 2 + 4; // pos(3) + uv(2) + color(4) = 9 floats

        ByteBuffer bb = ByteBuffer.allocateDirect(particleVertexCount * stride * 4);
        bb.order(ByteOrder.nativeOrder());
        particleVertexBuffer = bb.asFloatBuffer();

        int[] ids = new int[1];
        GLES20.glGenBuffers(1, ids, 0);
        particleVboId = ids[0];

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, particleVboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,
                            particleVertexCount * stride * 4,
                            null,
                            GLES20.GL_DYNAMIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    // ── Update loop ───────────────────────────────────────────────────
    @Override
    public void update(float deltaTime, float[] fftBands,
                       float beatIntensity, float totalTime) {
        globalTime += deltaTime;

        float audioEnergy = 0f;
        if (fftBands != null && fftBands.length > 0) {
            for (float v : fftBands) audioEnergy += v;
            audioEnergy /= fftBands.length * renderer.getSensitivity();
        }
        smoothedEnergy += (audioEnergy - smoothedEnergy) * 0.1f;

        for (Particle p : particles) {
            if (p.life <= 0) resetParticle(p, audioEnergy);

            // distance from centre
            float dx = p.x, dy = p.y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist < 0.001f) dist = 0.001f;

            // gravitational pull – stronger with audio energy
            float pull = GRAVITY / (dist * dist + 0.002f)
				* (1.0f + audioEnergy * 0.5f);
            float ax = -dx * pull;
            float ay = -dy * pull;

            // orbital motion (different per particle)
            ax += -dy * 0.15f;
            ay +=  dx * 0.15f;

            p.vx += ax * deltaTime;
            p.vy += ay * deltaTime;

            p.x += p.vx * deltaTime;
            p.y += p.vy * deltaTime;

            // lensing – stretch particles when close to hole
            float newDist = (float) Math.sqrt(p.x * p.x + p.y * p.y);
            if (newDist < HOLE_RADIUS * 3f) {
                float t = 1f - newDist / (HOLE_RADIUS * 3f);
                p.size *= (1f + t * t * 0.8f);
            }

            p.life -= p.decay * deltaTime;
        }

        buildParticleBuffer();
    }

    private void resetParticle(Particle p, float audioEnergy) {
        double angle = RAND.nextDouble() * 2.0 * Math.PI;
        // start somewhere in the disk
        float dist = DISK_INNER + RAND.nextFloat() * (DISK_OUTER - DISK_INNER);
        p.x = (float) Math.cos(angle) * dist;
        p.y = (float) Math.sin(angle) * dist;
        p.z = (RAND.nextFloat() - 0.5f) * 0.03f;

        // initial orbital speed – higher for inner particles
        float baseSpeed = 0.04f + (1.0f - dist / DISK_OUTER) * 0.08f;
        p.vx = (float) -Math.sin(angle) * baseSpeed;
        p.vy = (float)  Math.cos(angle) * baseSpeed;
        p.vz = (RAND.nextFloat() - 0.5f) * 0.01f;

        p.size = 0.012f + RAND.nextFloat() * 0.025f;
        p.life = 0.7f + RAND.nextFloat() * 1.0f;
        p.decay = 0.15f + RAND.nextFloat() * 0.2f;

        // ★ colour – temperature gradient: inner = blue/white, outer = orange/red
        float t = (dist - DISK_INNER) / (DISK_OUTER - DISK_INNER);   // 0..1
        if (t < 0.3f) {
            // inner hot: white‑blue
            p.color[0] = 0.6f + RAND.nextFloat() * 0.4f;
            p.color[1] = 0.8f + RAND.nextFloat() * 0.2f;
            p.color[2] = 1.0f;
        } else if (t < 0.7f) {
            // mid: cyan to greenish
            float s = (t - 0.3f) / 0.4f;
            p.color[0] = 0.3f - s * 0.2f;
            p.color[1] = 1.0f - s * 0.3f;
            p.color[2] = 1.0f - s * 0.6f;
        } else {
            // outer cool: orange/red
            float s = (t - 0.7f) / 0.3f;
            p.color[0] = 0.1f + s * 0.9f;
            p.color[1] = 0.5f * (1f - s);
            p.color[2] = 0.2f * (1f - s);
        }
        p.color[3] = 0.5f + audioEnergy * 0.4f;
    }

    // ── Buffer generation ─────────────────────────────────────────────
    private void buildParticleBuffer() {
        particleVertexBuffer.clear();

        // 1. orbiting particles
        for (Particle p : particles) {
            if (p.life <= 0) continue;
            putTriangle(p, p.x, p.y, p.z, p.size, p.color);
        }

        // 2. event‑horizon glow (thin bright ring)
        float[] glowColor = {
            0.95f, 0.8f, 0.2f,
            0.6f + smoothedEnergy * 0.4f
        };
        drawRing(0f, 0f, HORIZON_RING_INNER, HORIZON_RING_OUTER, glowColor);

        // 3. accretion disk (wide, with energy‑driven brightness)
        float diskAlpha = 0.25f + smoothedEnergy * 0.35f;
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double a1 = 2.0 * Math.PI * i / RING_SEGMENTS;
            double a2 = 2.0 * Math.PI * (i + 1) / RING_SEGMENTS;
            float t1 = (float)i / RING_SEGMENTS;
            float t2 = (float)(i + 1) / RING_SEGMENTS;
            float[] c1 = diskColorForT(t1, diskAlpha);
            float[] c2 = diskColorForT(t2, diskAlpha);

            float x1i = (float) Math.cos(a1) * DISK_INNER;
            float y1i = (float) Math.sin(a1) * DISK_INNER;
            float x1o = (float) Math.cos(a1) * DISK_OUTER;
            float y1o = (float) Math.sin(a1) * DISK_OUTER;
            float x2i = (float) Math.cos(a2) * DISK_INNER;
            float y2i = (float) Math.sin(a2) * DISK_INNER;
            float x2o = (float) Math.cos(a2) * DISK_OUTER;
            float y2o = (float) Math.sin(a2) * DISK_OUTER;

            // two triangles per segment
            putTriangleQuad(x1i, y1i, x1o, y1o, x2o, y2o, c1, c2);
            putTriangleQuad(x1i, y1i, x2o, y2o, x2i, y2i, c1, c2);
        }

        particleVertexBuffer.flip();

        if (particleVertexBuffer.remaining() > 0) {
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, particleVboId);
            GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0,
                                   particleVertexBuffer.remaining() * 4,
                                   particleVertexBuffer);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        }
    }

    // ── Colour helpers ────────────────────────────────────────────────
    /** Returns interpolated disk colour for a given normalised position 0‑1. */
    private float[] diskColorForT(float t, float baseAlpha) {
        // inner → white‑blue, outer → orange‑red
        float r = t < 0.5f ? (t * 2f) * 0.2f : 0.2f + (t - 0.5f) * 1.4f;
        float g = t < 0.5f ? 0.6f + t * 2f * 0.4f : 1.0f - (t - 0.5f) * 0.8f;
        float b = 1.0f - t * 0.8f;
        float a = baseAlpha * (1.0f - t * 0.3f); // outer is slightly dimmer
        return new float[]{r, g, b, a};
    }

    // ── Ring drawing (multiple segments) ──────────────────────────────
    private void drawRing(float cx, float cy, float inR, float outR, float[] color) {
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double a1 = 2.0 * Math.PI * i / RING_SEGMENTS;
            double a2 = 2.0 * Math.PI * (i + 1) / RING_SEGMENTS;
            float x1i = cx + (float) Math.cos(a1) * inR;
            float y1i = cy + (float) Math.sin(a1) * inR;
            float x1o = cx + (float) Math.cos(a1) * outR;
            float y1o = cy + (float) Math.sin(a1) * outR;
            float x2i = cx + (float) Math.cos(a2) * inR;
            float y2i = cy + (float) Math.sin(a2) * inR;
            float x2o = cx + (float) Math.cos(a2) * outR;
            float y2o = cy + (float) Math.sin(a2) * outR;

            // simple uniform colour for horizon ring
            putTriangleQuad(x1i, y1i, x1o, y1o, x2o, y2o, color, color);
            putTriangleQuad(x1i, y1i, x2o, y2o, x2i, y2i, color, color);
        }
    }

    // ── Low‑level buffer insertion ────────────────────────────────────
    /** Single textured triangle from pre‑computed particle. */
    private void putTriangle(Particle p, float cx, float cy, float cz,
                             float size, float[] color) {
        float s = size * 0.5f;
        float[] pos = {cx - s, cy - s, cz,  cx + s, cy - s, cz,
			cx - s, cy + s, cz,  cx + s, cy + s, cz};
        float[] uv  = {0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f};
        int[] tris = {0, 1, 2, 1, 3, 2};
        for (int idx : tris) {
            particleVertexBuffer.put(pos[idx * 3]);
            particleVertexBuffer.put(pos[idx * 3 + 1]);
            particleVertexBuffer.put(pos[idx * 3 + 2]);
            particleVertexBuffer.put(uv[idx * 2]);
            particleVertexBuffer.put(uv[idx * 2 + 1]);
            particleVertexBuffer.put(color[0]);
            particleVertexBuffer.put(color[1]);
            particleVertexBuffer.put(color[2]);
            particleVertexBuffer.put(color[3]);
        }
    }

    /** Two triangles forming a quad.  If colour arrays have length 4 they are used
     * for all vertices; if 8 they are split (first for vertex 3, second for vertex 2). */
    private void putTriangleQuad(float x1, float y1, float x2, float y2,
                                 float x3, float y3,
                                 float[] c1, float[] c2) {
        float[] pos = {x1, y1, 0f, x2, y2, 0f, x3, y3, 0f};
        float[] uv  = {0f, 0f, 1f, 0f, 0f, 1f};
        // first triangle (0,1,2) uses c1[0..3] for vertices 0 and 2? We'll assign per vertex.
        // In our case we want the disk to have a smooth colour gradient, so we use
        // the two colours for the two outer vertices of the segment.
        // We'll approximate by using c1 for the inner vertex and c2 for the outer ones.
        // This makes the disk look shaded radially.
        float[] cols = {
            c1[0], c1[1], c1[2], c1[3],   // vertex 0 (inner)
            c2[0], c2[1], c2[2], c2[3],   // vertex 1 (outer)
            c2[0], c2[1], c2[2], c2[3]    // vertex 2 (outer)
        };
        int[] tri = {0, 1, 2};
        for (int idx : tri) {
            particleVertexBuffer.put(pos[idx * 3]);
            particleVertexBuffer.put(pos[idx * 3 + 1]);
            particleVertexBuffer.put(pos[idx * 3 + 2]);
            particleVertexBuffer.put(uv[idx * 2]);
            particleVertexBuffer.put(uv[idx * 2 + 1]);
            particleVertexBuffer.put(cols[idx * 4]);
            particleVertexBuffer.put(cols[idx * 4 + 1]);
            particleVertexBuffer.put(cols[idx * 4 + 2]);
            particleVertexBuffer.put(cols[idx * 4 + 3]);
        }
    }

    // ── Central black hole (drawn with same textured shader) ─────────
    private void drawBlackHole(float[] mvpMatrix, int prog, float beat) {
        GLES20.glUseProgram(prog);
        int posH = GLES20.glGetAttribLocation(prog, "vPosition");
        int texH = GLES20.glGetAttribLocation(prog, "aTexCoord");
        int colH = GLES20.glGetAttribLocation(prog, "aColor");
        int mvpH = GLES20.glGetUniformLocation(prog, "uMVPMatrix");
        int texU = GLES20.glGetUniformLocation(prog, "uTexture");

        if (mvpH != -1) GLES20.glUniformMatrix4fv(mvpH, 1, false, mvpMatrix, 0);
        if (texU != -1) GLES20.glUniform1i(texU, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sunTexture);

        float r = HOLE_RADIUS * (1.0f + beat * 0.1f);
        float[] verts = {-r, -r, 0f, r, -r, 0f, -r, r, 0f, r, r, 0f};
        float[] uvs   = {0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f};

        ByteBuffer vb = ByteBuffer.allocateDirect(verts.length * 4);
        vb.order(ByteOrder.nativeOrder());
        FloatBuffer vf = vb.asFloatBuffer(); vf.put(verts); vf.position(0);
        ByteBuffer ub = ByteBuffer.allocateDirect(uvs.length * 4);
        ub.order(ByteOrder.nativeOrder());
        FloatBuffer uf = ub.asFloatBuffer(); uf.put(uvs); uf.position(0);

        if (posH != -1) {
            GLES20.glVertexAttribPointer(posH, 3, GLES20.GL_FLOAT, false, 0, vf);
            GLES20.glEnableVertexAttribArray(posH);
        }
        if (texH != -1) {
            GLES20.glVertexAttribPointer(texH, 2, GLES20.GL_FLOAT, false, 0, uf);
            GLES20.glEnableVertexAttribArray(texH);
        }
        if (colH != -1)
            GLES20.glVertexAttrib4f(colH, 0.0f, 0.0f, 0.02f, 0.95f);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        if (posH != -1) GLES20.glDisableVertexAttribArray(posH);
        if (texH != -1) GLES20.glDisableVertexAttribArray(texH);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    // ── Draw particles / rings (VBO) ─────────────────────────────────
    private void drawParticles(float[] mvpMatrix, int prog) {
        GLES20.glUseProgram(prog);
        int posH = GLES20.glGetAttribLocation(prog, "vPosition");
        int texH = GLES20.glGetAttribLocation(prog, "aTexCoord");
        int colH = GLES20.glGetAttribLocation(prog, "aColor");
        int mvpH = GLES20.glGetUniformLocation(prog, "uMVPMatrix");
        int texU = GLES20.glGetUniformLocation(prog, "uTexture");

        if (mvpH != -1) GLES20.glUniformMatrix4fv(mvpH, 1, false, mvpMatrix, 0);
        if (texU != -1) GLES20.glUniform1i(texU, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sunTexture);

        int stride = (3 + 2 + 4) * 4;
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, particleVboId);
        if (posH != -1) {
            GLES20.glVertexAttribPointer(posH, 3, GLES20.GL_FLOAT, false, stride, 0);
            GLES20.glEnableVertexAttribArray(posH);
        }
        if (texH != -1) {
            GLES20.glVertexAttribPointer(texH, 2, GLES20.GL_FLOAT, false, stride, 3*4);
            GLES20.glEnableVertexAttribArray(texH);
        }
        if (colH != -1) {
            GLES20.glVertexAttribPointer(colH, 4, GLES20.GL_FLOAT, false, stride, 5*4);
            GLES20.glEnableVertexAttribArray(colH);
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);

        int verts = particleVertexBuffer.limit() / 9;
        if (verts > 0) GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, verts);

        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        if (posH != -1) GLES20.glDisableVertexAttribArray(posH);
        if (texH != -1) GLES20.glDisableVertexAttribArray(texH);
        if (colH != -1) GLES20.glDisableVertexAttribArray(colH);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    // ── Public draw entry points ─────────────────────────────────────
    @Override
    public void draw2D(float[] mvpMatrix, GLVisualizerRenderer renderer) {
        float beat = renderer.getBeatIntensity();
        // draw back-to-front: disk particles → black hole on top
        drawBlackHole(mvpMatrix, texturedShaderProgram2D, beat);
        drawParticles(mvpMatrix, texturedShaderProgram2D);
    }

    @Override
    public void draw3D(float[] mvpMatrix, GLVisualizerRenderer renderer) {
        float beat = renderer.getBeatIntensity();
        drawBlackHole(mvpMatrix, texturedShaderProgram3D, beat);
        drawParticles(mvpMatrix, texturedShaderProgram3D);
    }

    @Override
    public void release() {
        if (buffersInitialized) {
            GLES20.glDeleteBuffers(1, new int[]{particleVboId}, 0);
            GLES20.glDeleteTextures(1, new int[]{sunTexture}, 0);
            buffersInitialized = false;
        }
    }
}
