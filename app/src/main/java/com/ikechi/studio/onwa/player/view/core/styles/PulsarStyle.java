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
 * Pulsar: A rotating neutron star with sweeping light beams.
 * - Central pulsar (white/blue core)
 * - Two sweeping beams (lighthouse effect)
 * - Particles emitted along beams
 * - Beat-reactive intensity
 */
public class PulsarStyle extends VisualStyle {
    private static final Random RAND = new Random();
    private static final int MAX_PARTICLES = 300;
    private static final int BEAM_PARTICLES = 100;
    private static final float PULSAR_RADIUS = 0.1f;
    private static final float BEAM_LENGTH = 1.2f;
    private static final float ROTATION_SPEED = 1.0f; // Radians per second

    private final List<Particle> particles = new ArrayList<>(MAX_PARTICLES);
    private final List<Particle> beamParticles = new ArrayList<>(BEAM_PARTICLES);
    private int sunTexture;
    private int particleVboId;
    private FloatBuffer particleVertexBuffer;
    private int particleVertexCount;
    private boolean buffersInitialized = false;
    private float globalTime = 0f;
    private float rotationAngle = 0f;
    private float smoothedEnergy = 0f;

    @Override
    public boolean usesTexture() {
        return true;
    }

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
        beamParticles.clear();
        for (int i = 0; i < MAX_PARTICLES; i++) {
            particles.add(new Particle());
        }
        for (int i = 0; i < BEAM_PARTICLES; i++) {
            beamParticles.add(new Particle());
        }

        allocateBuffers();
        buffersInitialized = true;
    }

    private void allocateBuffers() {
        // 300+100 particles × 6 verts = 2400, plus 2 beam quads × 6 verts each = 12 extra.
        // Add a safety margin of 24 to cover any edge-case overdraw.
        particleVertexCount = (MAX_PARTICLES + BEAM_PARTICLES) * 6 + 12 + 24;
        int stride = 3 + 2 + 4; // pos (3) + uv (2) + color (4) = 9 floats

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

    @Override
    public void update(float deltaTime, float[] fftBands, float beatIntensity, float totalTime) {
        globalTime += deltaTime;
        rotationAngle += ROTATION_SPEED * deltaTime;

        // Calculate audio energy
        float audioEnergy = 0f;
        if (fftBands != null && fftBands.length > 0) {
            for (float v : fftBands) audioEnergy += v;
            audioEnergy /= fftBands.length * renderer.getSensitivity();
        }
        smoothedEnergy += (audioEnergy - smoothedEnergy) * 0.1f;

        // Update pulsar core (particles)
        for (Particle p : particles) {
            if (p.life <= 0) {
                resetParticle(p, audioEnergy);
            }
            p.life -= p.decay * deltaTime;
        }

        // Update beam particles
        for (Particle p : beamParticles) {
            if (p.life <= 0) {
                resetBeamParticle(p, audioEnergy);
            }
            p.life -= p.decay * deltaTime;
        }

        buildParticleBuffer();
    }

    private void resetParticle(Particle p, float audioEnergy) {
        // Emit from pulsar surface
        double angle = RAND.nextDouble() * 2.0 * Math.PI;
        float radius = PULSAR_RADIUS * (0.8f + RAND.nextFloat() * 0.4f);
        p.x = (float) Math.cos(angle) * radius;
        p.y = (float) Math.sin(angle) * radius;
        p.z = (RAND.nextFloat() - 0.5f) * 0.05f;

        // Velocity: slight outward motion
        p.vx = (RAND.nextFloat() - 0.5f) * 0.1f;
        p.vy = (RAND.nextFloat() - 0.5f) * 0.1f;
        p.vz = (RAND.nextFloat() - 0.5f) * 0.05f;

        p.size = 0.02f + RAND.nextFloat() * 0.03f;
        p.life = 0.5f + RAND.nextFloat() * 0.5f;
        p.decay = 0.5f + RAND.nextFloat() * 0.5f;

        // Color: white/blue
        p.color[0] = 0.8f + RAND.nextFloat() * 0.2f;
        p.color[1] = 0.9f + RAND.nextFloat() * 0.1f;
        p.color[2] = 1.0f;
        p.color[3] = 0.8f + audioEnergy * 0.2f;
    }

    private void resetBeamParticle(Particle p, float audioEnergy) {
        // Emit along one of the two beams
        int beamIndex = RAND.nextInt(2);
        float beamAngle = rotationAngle + beamIndex * (float) Math.PI;
        float dist = RAND.nextFloat() * BEAM_LENGTH;
        p.x = (float) Math.cos(beamAngle) * dist;
        p.y = (float) Math.sin(beamAngle) * dist;
        p.z = (RAND.nextFloat() - 0.5f) * 0.05f;

        // Velocity: along the beam
        p.vx = (float) Math.cos(beamAngle) * (0.5f + audioEnergy * 0.5f);
        p.vy = (float) Math.sin(beamAngle) * (0.5f + audioEnergy * 0.5f);
        p.vz = (RAND.nextFloat() - 0.5f) * 0.02f;

        p.size = 0.03f + RAND.nextFloat() * 0.04f + audioEnergy * 0.02f;
        p.life = 0.3f + RAND.nextFloat() * 0.4f;
        p.decay = 0.8f + RAND.nextFloat() * 0.4f;

        // Color: white with beam tint
        p.color[0] = 0.9f + audioEnergy * 0.1f;
        p.color[1] = 0.95f + audioEnergy * 0.05f;
        p.color[2] = 1.0f;
        p.color[3] = 0.7f + audioEnergy * 0.3f;
    }

    private void buildParticleBuffer() {
        particleVertexBuffer.clear();

        // Draw pulsar core particles
        for (Particle p : particles) {
            if (p.life <= 0) continue;
            drawParticle(p.x, p.y, p.z, p.size, p.color);
        }

        // Draw beam particles
        for (Particle p : beamParticles) {
            if (p.life <= 0) continue;
            drawParticle(p.x, p.y, p.z, p.size, p.color);
        }

        // Draw beams (as quads)
        for (int i = 0; i < 2; i++) {
            float beamAngle = rotationAngle + i * (float) Math.PI;
            float beamWidth = 0.05f + smoothedEnergy * 0.03f;
            float beamLength = BEAM_LENGTH * (0.8f + smoothedEnergy * 0.2f);

            // Beam color (white with energy tint)
            float[] beamColor = {
                0.9f + smoothedEnergy * 0.1f,
                0.95f + smoothedEnergy * 0.05f,
                1.0f,
                0.3f + smoothedEnergy * 0.4f
            };

            // Outer edge of the beam
            float ox1 = (float) Math.cos(beamAngle + beamWidth) * beamLength;
            float oy1 = (float) Math.sin(beamAngle + beamWidth) * beamLength;
            float ox2 = (float) Math.cos(beamAngle - beamWidth) * beamLength;
            float oy2 = (float) Math.sin(beamAngle - beamWidth) * beamLength;

            // Draw a quad from origin to beam edge (2 triangles = 6 vertices)
            drawQuad(0f, 0f, ox1, oy1, ox2, oy2, beamColor);
        }

        particleVertexBuffer.flip();

        if (particleVertexBuffer.remaining() > 0) {
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, particleVboId);
            GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0,
                                   particleVertexBuffer.remaining() * 4, particleVertexBuffer);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        }
    }

    private void drawParticle(float x, float y, float z, float size, float[] color) {
        float s = size * 0.5f;
        float[] pos = {
            x - s, y - s, z,
            x + s, y - s, z,
            x - s, y + s, z,
            x + s, y + s, z
        };
        float[] uv = {0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f};
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

    /**
     * Draws a quad as two triangles (6 vertices).
     * Vertices: origin (x0,y0), then two outer points.
     */
    private void drawQuad(float x0, float y0, float x1, float y1, float x2, float y2, float[] color) {
        // Triangle 1: origin → p1 → p2
        // Triangle 2: origin → p2 → p1 (double‑sided blend gives glow effect)
        float[] pos = {x0, y0, 0f, x1, y1, 0f, x2, y2, 0f};
        float[] uv  = {0.5f, 0.5f, 1f, 0f, 0f, 1f};
        int[] tris = {0, 1, 2, 0, 2, 1};

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

    private void drawPulsar(float[] mvpMatrix, int prog, float beat) {
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

        // Draw pulsar core (white/blue)
        float pulsarSize = PULSAR_RADIUS * (1.0f + beat * 0.2f);
        float[] vertices = {
            -pulsarSize, -pulsarSize, 0f,
            pulsarSize, -pulsarSize, 0f,
            -pulsarSize, pulsarSize, 0f,
            pulsarSize, pulsarSize, 0f
        };
        float[] uvs = {0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f};

        ByteBuffer vbb = ByteBuffer.allocateDirect(vertices.length * 4);
        vbb.order(ByteOrder.nativeOrder());
        FloatBuffer vertexBuf = vbb.asFloatBuffer();
        vertexBuf.put(vertices);
        vertexBuf.position(0);

        ByteBuffer ubb = ByteBuffer.allocateDirect(uvs.length * 4);
        ubb.order(ByteOrder.nativeOrder());
        FloatBuffer uvBuf = ubb.asFloatBuffer();
        uvBuf.put(uvs);
        uvBuf.position(0);

        if (posH != -1) {
            GLES20.glVertexAttribPointer(posH, 3, GLES20.GL_FLOAT, false, 0, vertexBuf);
            GLES20.glEnableVertexAttribArray(posH);
        }
        if (texH != -1) {
            GLES20.glVertexAttribPointer(texH, 2, GLES20.GL_FLOAT, false, 0, uvBuf);
            GLES20.glEnableVertexAttribArray(texH);
        }
        if (colH != -1) {
            GLES20.glVertexAttrib4f(colH, 0.9f, 0.95f, 1.0f, 1.0f);
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        if (posH != -1) GLES20.glDisableVertexAttribArray(posH);
        if (texH != -1) GLES20.glDisableVertexAttribArray(texH);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    private void drawFlares(float[] mvpMatrix, int prog) {
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
            GLES20.glVertexAttribPointer(texH, 2, GLES20.GL_FLOAT, false, stride, 3 * 4);
            GLES20.glEnableVertexAttribArray(texH);
        }
        if (colH != -1) {
            GLES20.glVertexAttribPointer(colH, 4, GLES20.GL_FLOAT, false, stride, 5 * 4);
            GLES20.glEnableVertexAttribArray(colH);
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);

        int verts = particleVertexBuffer.limit() / 9;
        if (verts > 0) {
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, verts);
        }

        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        if (posH != -1) GLES20.glDisableVertexAttribArray(posH);
        if (texH != -1) GLES20.glDisableVertexAttribArray(texH);
        if (colH != -1) GLES20.glDisableVertexAttribArray(colH);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    @Override
    public void draw2D(float[] mvpMatrix, GLVisualizerRenderer renderer) {
        float beat = renderer.getBeatIntensity();
        drawPulsar(mvpMatrix, texturedShaderProgram2D, beat);
        drawFlares(mvpMatrix, texturedShaderProgram2D);
    }

    @Override
    public void draw3D(float[] mvpMatrix, GLVisualizerRenderer renderer) {
        float beat = renderer.getBeatIntensity();
        drawPulsar(mvpMatrix, texturedShaderProgram3D, beat);
        drawFlares(mvpMatrix, texturedShaderProgram3D);
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
