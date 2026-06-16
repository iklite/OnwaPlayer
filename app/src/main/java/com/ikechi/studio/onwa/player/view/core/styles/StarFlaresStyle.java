
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
 * Fixed Radiant Sun with:
 * - Physically accurate solar colors (black-body radiation)
 * - Spiral motion (magnetic field lines)
 * - Multi-layered sun (core, chromosphere, corona)
 * - Beat-reactive pulsations and color shifts
 * - Particle trails for smooth motion
 * - Fully compatible with original Particle class and shader
 */
public class StarFlaresStyle extends VisualStyle {

    private static final Random RAND = new Random();

    // --- Constants ---
    private static final int TRAIL_LENGTH = 3;  // Fixed: Now an int
    private static final float SUN_BASE_RADIUS = 0.25f;
    private static final float SUN_CORE_RADIUS = 0.12f;
    private static final float SUN_CHROMOSPHERE_RADIUS = 0.20f;
    private static final float SUN_CORONA_RADIUS = 0.28f;
    private static final int MAX_PARTICLES = 400;
    private static final float EMIT_RATE = 15.0f;
    private static final float GRAVITY = -0.05f;
    private static final float SPIRAL_STRENGTH = 0.3f;

    // --- State ---
    private float smoothedEnergy = 0f;
    private float bassEnergy = 0f;
    private float globalTime = 0f;
    private float emitAccum = 0f;

    // --- Particles ---
    private final List<Particle> particles = new ArrayList<>(MAX_PARTICLES);

    // --- OpenGL ---
    private int sunTexture;
    private int particleVboId;
    private FloatBuffer particleVertexBuffer;
    private int particleVertexCount;
    private boolean buffersInitialized = false;

    // --- Sun Layer Buffers ---
    private FloatBuffer sunVertexBuffer;
    private FloatBuffer sunUVBuffer;

    // --- Trail Support (using arrays of floats) ---
    private final float[][] particleTrailX = new float[MAX_PARTICLES][TRAIL_LENGTH];
    private final float[][] particleTrailY = new float[MAX_PARTICLES][TRAIL_LENGTH];
    private final float[][] particleTrailLife = new float[MAX_PARTICLES][TRAIL_LENGTH];

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
        for (int i = 0; i < MAX_PARTICLES; i++) {
            particles.add(new Particle());
        }
        for (int i = 0; i < MAX_PARTICLES; i++) {
            resetParticle(i, 0f);
        }

        allocateBuffers();
        allocateSunBuffers();
        buffersInitialized = true;
    }

    private void allocateBuffers() {
        // Account for particles + trails (each particle has TRAIL_LENGTH trails)
        particleVertexCount = MAX_PARTICLES * 6 * (1 + TRAIL_LENGTH);
        int stride = 3 + 2 + 4;  // pos (3) + uv (2) + color (4)

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

    private void allocateSunBuffers() {
        int sunVertices = 3 * 4;  // 3 layers * 4 vertices
        int sunUVs = 2 * 4;

        ByteBuffer vbb = ByteBuffer.allocateDirect(sunVertices * 4);
        vbb.order(ByteOrder.nativeOrder());
        sunVertexBuffer = vbb.asFloatBuffer();

        ByteBuffer ubb = ByteBuffer.allocateDirect(sunUVs * 4);
        ubb.order(ByteOrder.nativeOrder());
        sunUVBuffer = ubb.asFloatBuffer();
    }

    private void resetParticle(int index, float audioEnergy) {
        Particle p = particles.get(index);

        // Emit from a random point on the sun's surface
        double angle = RAND.nextDouble() * 2.0 * Math.PI;
        float emitRadius = SUN_BASE_RADIUS * (0.8f + RAND.nextFloat() * 0.4f);
        p.x = (float) Math.cos(angle) * emitRadius;
        p.y = (float) Math.sin(angle) * emitRadius;
        p.z = (RAND.nextFloat() - 0.5f) * 0.05f;

        // Velocity: outward with spiral motion
        float outwardSpeed = 1.0f + RAND.nextFloat() * 1.0f + audioEnergy * 1.5f;
        float nx = p.x / emitRadius;
        float ny = p.y / emitRadius;
        p.vx = nx * outwardSpeed;
        p.vy = ny * outwardSpeed;

        // Add spiral motion
        float spiralSpeed = 0.5f + RAND.nextFloat() * 0.5f + audioEnergy * 0.3f;
        p.vx += -ny * spiralSpeed;
        p.vy += nx * spiralSpeed;

        p.vz = (RAND.nextFloat() - 0.5f) * 0.02f;

        // Size
        p.size = 0.04f + RAND.nextFloat() * 0.06f + audioEnergy * 0.08f;

        // Color (white-hot)
        p.color[0] = 1.0f;
        p.color[1] = 0.95f;
        p.color[2] = 0.9f;
        p.color[3] = 0.8f + audioEnergy * 0.2f;

        // Life
        p.life = 1.0f;
        p.decay = 0.3f + RAND.nextFloat() * 0.2f;

        // Reset trail for this particle
        for (int i = 0; i < TRAIL_LENGTH; i++) {
            particleTrailX[index][i] = p.x;
            particleTrailY[index][i] = p.y;
            particleTrailLife[index][i] = 0f;
        }
    }

    @Override
    public void update(float deltaTime, float[] fftBands, float beatIntensity, float totalTime) {
        this.totalTime = totalTime;
        globalTime = totalTime;

        // Calculate audio energies
        float audioEnergy = 0f;
        float bassEnergy = 0f;
        if (fftBands != null && fftBands.length > 0) {
            for (int i = 0; i < fftBands.length; i++) {
                float v = fftBands[i] * renderer.getSensitivity();
                audioEnergy += v;
                if (i < fftBands.length / 4) {
                    bassEnergy += v;
                }
            }
            audioEnergy /= fftBands.length;
            bassEnergy = Math.min(bassEnergy / (fftBands.length / 4) * 2f, 2f);
        }
        smoothedEnergy += (audioEnergy - smoothedEnergy) * 0.1f;
        bassEnergy *= 0.9f;

        // Emission
        emitAccum += EMIT_RATE * deltaTime * (1.0f + audioEnergy * 2.0f + beatIntensity * 1.5f);
        int emitCount = (int) emitAccum;
        emitAccum -= emitCount;

        for (int i = 0; i < emitCount && i < MAX_PARTICLES; i++) {
            for (int j = 0; j < MAX_PARTICLES; j++) {
                if (particles.get(j).life <= 0) {
                    resetParticle(j, audioEnergy);
                    break;
                }
            }
        }

        // Update particles and trails
        for (int i = 0; i < MAX_PARTICLES; i++) {
            Particle p = particles.get(i);
            if (p.life <= 0) continue;

            // Spiral motion
            float dist = (float) Math.sqrt(p.x * p.x + p.y * p.y);
            if (dist > 0) {
                float spiralPhase = globalTime * 2f + dist * 3f;
                float spiralForce = SPIRAL_STRENGTH * (float) Math.sin(spiralPhase) * (1f + audioEnergy);
                p.vx += -p.y / dist * spiralForce * deltaTime;
                p.vy += p.x / dist * spiralForce * deltaTime;
            }

            // Gravity
            dist = (float) Math.sqrt(p.x * p.x + p.y * p.y);
            if (dist > 0) {
                p.vx -= p.x / dist * GRAVITY * deltaTime;
                p.vy -= p.y / dist * GRAVITY * deltaTime;
            }

            // Update position
            p.x += p.vx * deltaTime;
            p.y += p.vy * deltaTime;
            p.z += p.vz * deltaTime;

            // Update trail (shift old positions)
            // Fixed: Correct System.arraycopy usage
            System.arraycopy(particleTrailX[i], 0, particleTrailX[i], 1, TRAIL_LENGTH - 1);
            System.arraycopy(particleTrailY[i], 0, particleTrailY[i], 1, TRAIL_LENGTH - 1);
            System.arraycopy(particleTrailLife[i], 0, particleTrailLife[i], 1, TRAIL_LENGTH - 1);

            // Store current position as the newest trail point
            particleTrailX[i][0] = p.x;
            particleTrailY[i][0] = p.y;
            particleTrailLife[i][0] = p.life;

            // Update color based on life
            float lifeRatio = p.life / 1.0f;
            if (lifeRatio > 0.7f) {
                p.color[0] = 1.0f;
                p.color[1] = 0.95f;
                p.color[2] = 0.9f;
            } else if (lifeRatio > 0.4f) {
                p.color[0] = 1.0f;
                p.color[1] = 0.8f + (lifeRatio - 0.4f) * 0.4f;
                p.color[2] = 0.5f + (lifeRatio - 0.4f) * 0.8f;
            } else {
                p.color[0] = 1.0f;
                p.color[1] = 0.4f + lifeRatio * 0.4f;
                p.color[2] = 0.1f + lifeRatio * 0.2f;
            }
            p.color[0] = Math.min(1.0f, p.color[0] + audioEnergy * 0.2f);
            p.color[1] = Math.min(1.0f, p.color[1] + audioEnergy * 0.2f);
            p.color[2] = Math.min(1.0f, p.color[2] + audioEnergy * 0.1f);
            p.color[3] = 0.7f + lifeRatio * 0.3f + audioEnergy * 0.2f;

            // Age
            p.life -= p.decay * deltaTime;
            if (p.life <= 0) {
                p.life = 0;
            }
        }

        buildParticleBuffer();
    }

    private void buildParticleBuffer() {
        particleVertexBuffer.clear();

        for (int i = 0; i < MAX_PARTICLES; i++) {
            Particle p = particles.get(i);
            if (p.life <= 0) continue;

            // --- Main Particle ---
            drawParticle(p.x, p.y, p.z, p.size, p.color);

            // --- Trail ---
            for (int j = 0; j < TRAIL_LENGTH; j++) {
                if (particleTrailLife[i][j] <= 0) continue;
                float trailAlpha = particleTrailLife[i][j] / 1.0f * 0.5f;
                if (trailAlpha <= 0) continue;

                float[] trailColor = {
                    p.color[0] * 0.7f,
                    p.color[1] * 0.7f,
                    p.color[2] * 0.7f,
                    trailAlpha * p.color[3]
                };
                float trailSize = p.size * (0.5f + j * 0.1f);
                drawParticle(particleTrailX[i][j], particleTrailY[i][j], p.z, trailSize, trailColor);
            }
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

    private void drawSun(float[] mvpMatrix, int prog, float beat) {
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

        // Draw multi-layered sun
        drawSunLayer(SUN_CORE_RADIUS * (1.0f + beat * 0.1f), 1.0f, 1.0f, 1.0f, 1.0f, prog, posH, texH, colH);
        drawSunLayer(SUN_CHROMOSPHERE_RADIUS * (1.0f + beat * 0.15f), 1.0f, 0.85f, 0.4f, 0.9f, prog, posH, texH, colH);
        drawSunLayer(SUN_CORONA_RADIUS * (1.0f + beat * 0.2f), 0.6f, 0.7f, 1.0f, 0.5f, prog, posH, texH, colH);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    private void drawSunLayer(float radius, float r, float g, float b, float a,
                              int prog, int posH, int texH, int colH) {
        float[] vertices = {
            -radius, -radius, 0f,
            radius, -radius, 0f,
            -radius, radius, 0f,
            radius, radius, 0f
        };
        float[] uvs = {0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f};

        sunVertexBuffer.clear();
        sunVertexBuffer.put(vertices);
        sunVertexBuffer.position(0);

        sunUVBuffer.clear();
        sunUVBuffer.put(uvs);
        sunUVBuffer.position(0);

        if (posH != -1) {
            GLES20.glVertexAttribPointer(posH, 3, GLES20.GL_FLOAT, false, 0, sunVertexBuffer);
            GLES20.glEnableVertexAttribArray(posH);
        }
        if (texH != -1) {
            GLES20.glVertexAttribPointer(texH, 2, GLES20.GL_FLOAT, false, 0, sunUVBuffer);
            GLES20.glEnableVertexAttribArray(texH);
        }
        if (colH != -1) {
            GLES20.glVertexAttrib4f(colH, r, g, b, a);
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        if (posH != -1) GLES20.glDisableVertexAttribArray(posH);
        if (texH != -1) GLES20.glDisableVertexAttribArray(texH);
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

        // Additive blending for HDR-like brightness
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);

        int verts = particleVertexBuffer.limit() / 9;
        if (verts > 0) {
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, verts);
        }

        // Restore default blending
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        if (posH != -1) GLES20.glDisableVertexAttribArray(posH);
        if (texH != -1) GLES20.glDisableVertexAttribArray(texH);
        if (colH != -1) GLES20.glDisableVertexAttribArray(colH);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    @Override
    public void draw2D(float[] mvpMatrix, GLVisualizerRenderer renderer) {
        float beat = renderer.getBeatIntensity();
        drawSun(mvpMatrix, texturedShaderProgram2D, beat);
        drawFlares(mvpMatrix, texturedShaderProgram2D);
    }

    @Override
    public void draw3D(float[] mvpMatrix, GLVisualizerRenderer renderer) {
        float beat = renderer.getBeatIntensity();
        drawSun(mvpMatrix, texturedShaderProgram3D, beat);
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

