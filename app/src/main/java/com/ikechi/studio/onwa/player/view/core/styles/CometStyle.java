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
 * Comet: A bright nucleus with a dynamic, flowing tail.
 * - Bright nucleus (white/blue)
 * - Dynamic tail (particles streaming behind)
 * - Beat-reactive tail length
 * - Orbital motion
 * - Fixed: No dependency on Particle.maxLife
 */
public class CometStyle extends VisualStyle {
    private static final Random RAND = new Random();
    private static final int MAX_NUCLEUS_PARTICLES = 50;
    private static final int MAX_TAIL_PARTICLES = 200;
    private static final float NUCLEUS_RADIUS = 0.1f;
    private static final float ORBIT_RADIUS = 0.8f;
    private static final float ORBIT_SPEED = 0.5f; // Radians per second
    private static final int TAIL_LENGTH = 3; // Trail segments per particle

    private final List<Particle> nucleusParticles = new ArrayList<>(MAX_NUCLEUS_PARTICLES);
    private final List<Particle> tailParticles = new ArrayList<>(MAX_TAIL_PARTICLES);
    private int sunTexture;
    private int particleVboId;
    private FloatBuffer particleVertexBuffer;
    private int particleVertexCount;
    private boolean buffersInitialized = false;
    private float globalTime = 0f;
    private float orbitAngle = 0f;
    private float smoothedEnergy = 0f;
    private float tailLength = 0.5f;

    // Trail storage (using arrays of floats)
    private final float[][] tailTrailX = new float[MAX_TAIL_PARTICLES][TAIL_LENGTH];
    private final float[][] tailTrailY = new float[MAX_TAIL_PARTICLES][TAIL_LENGTH];
    private final float[][] tailTrailLife = new float[MAX_TAIL_PARTICLES][TAIL_LENGTH];

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
        nucleusParticles.clear();
        tailParticles.clear();
        for (int i = 0; i < MAX_NUCLEUS_PARTICLES; i++) {
            nucleusParticles.add(new Particle());
        }
        for (int i = 0; i < MAX_TAIL_PARTICLES; i++) {
            tailParticles.add(new Particle());
        }
        for (int i = 0; i < MAX_NUCLEUS_PARTICLES; i++) {
            resetNucleusParticle(i, 0f);
        }
        for (int i = 0; i < MAX_TAIL_PARTICLES; i++) {
            resetTailParticle(i, 0f);
        }

        allocateBuffers();
        buffersInitialized = true;
    }

    private void allocateBuffers() {
        // Account for nucleus particles + tail particles + their trails
        particleVertexCount = (MAX_NUCLEUS_PARTICLES + MAX_TAIL_PARTICLES * (1 + TAIL_LENGTH)) * 6;
        int stride = 3 + 2 + 4; // pos (3) + uv (2) + color (4)

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
        orbitAngle += ORBIT_SPEED * deltaTime;

        float audioEnergy = 0f;
        if (fftBands != null && fftBands.length > 0) {
            for (float v : fftBands) audioEnergy += v;
            audioEnergy /= fftBands.length * renderer.getSensitivity();
        }
        smoothedEnergy += (audioEnergy - smoothedEnergy) * 0.1f;

        // Update tail length based on beat
        tailLength = 0.5f + smoothedEnergy * 0.5f;

        // Update nucleus position (orbiting)
        float nucleusX = (float) Math.cos(orbitAngle) * ORBIT_RADIUS;
        float nucleusY = (float) Math.sin(orbitAngle) * ORBIT_RADIUS;

        // Update nucleus particles
        for (int i = 0; i < nucleusParticles.size(); i++) {
            Particle p = nucleusParticles.get(i);
            if (p.life <= 0) {
                resetNucleusParticle(i, audioEnergy);
            }

            // Move with nucleus
            p.x = nucleusX + (RAND.nextFloat() - 0.5f) * NUCLEUS_RADIUS * 0.5f;
            p.y = nucleusY + (RAND.nextFloat() - 0.5f) * NUCLEUS_RADIUS * 0.5f;

            p.life -= p.decay * deltaTime;
        }

        // Update tail particles
        for (int i = 0; i < tailParticles.size(); i++) {
            Particle p = tailParticles.get(i);
            if (p.life <= 0) {
                resetTailParticle(i, audioEnergy);
            }

            // Store initial life for trail alpha calculations
            float initialLife = 0.3f + RAND.nextFloat() * 0.4f;

            // Move along tail (behind nucleus)
            float tailProgress = p.life / initialLife; // Use initialLife instead of maxLife
            float tailDist = tailLength * (1.0f - tailProgress);
            float tailAngle = orbitAngle - (float) Math.PI; // Point tail opposite to motion
            p.x = nucleusX + (float) Math.cos(tailAngle) * tailDist * (0.5f + RAND.nextFloat() * 0.5f);
            p.y = nucleusY + (float) Math.sin(tailAngle) * tailDist * (0.5f + RAND.nextFloat() * 0.5f);

            // Update trail
            System.arraycopy(tailTrailX[i], 0, tailTrailX[i], 1, TAIL_LENGTH - 1);
            System.arraycopy(tailTrailY[i], 0, tailTrailY[i], 1, TAIL_LENGTH - 1);
            tailTrailX[i][0] = p.x;
            tailTrailY[i][0] = p.y;
            tailTrailLife[i][0] = p.life;

            p.life -= p.decay * deltaTime;
        }

        buildParticleBuffer();
    }

    private void resetNucleusParticle(int index, float audioEnergy) {
        Particle p = nucleusParticles.get(index);
        p.x = 0f;
        p.y = 0f;
        p.z = (RAND.nextFloat() - 0.5f) * 0.05f;

        p.vx = 0f;
        p.vy = 0f;
        p.vz = 0f;

        p.size = 0.04f + RAND.nextFloat() * 0.06f;
        p.life = 0.5f + RAND.nextFloat() * 0.5f;
        p.decay = 0.5f + RAND.nextFloat() * 0.5f;

        // Color: white/blue
        p.color = new float[]{
            0.9f + RAND.nextFloat() * 0.1f,
            0.95f + RAND.nextFloat() * 0.05f,
            1.0f,
            0.8f + audioEnergy * 0.2f
        };
    }

    private void resetTailParticle(int index, float audioEnergy) {
        Particle p = tailParticles.get(index);
        p.x = 0f;
        p.y = 0f;
        p.z = (RAND.nextFloat() - 0.5f) * 0.05f;

        p.vx = 0f;
        p.vy = 0f;
        p.vz = 0f;

        p.size = 0.02f + RAND.nextFloat() * 0.03f;
        p.life = 0.3f + RAND.nextFloat() * 0.4f;
        p.decay = 0.5f + RAND.nextFloat() * 0.5f;

        // Color: white with slight blue tint
        p.color = new float[]{
            0.8f + RAND.nextFloat() * 0.2f,
            0.85f + RAND.nextFloat() * 0.15f,
            0.95f + RAND.nextFloat() * 0.05f,
            0.6f + audioEnergy * 0.2f
        };

        // Reset trail
        for (int j = 0; j < TAIL_LENGTH; j++) {
            tailTrailX[index][j] = p.x;
            tailTrailY[index][j] = p.y;
            tailTrailLife[index][j] = 0f;
        }
    }

    private void buildParticleBuffer() {
        particleVertexBuffer.clear();

        // Draw nucleus particles
        for (Particle p : nucleusParticles) {
            if (p.life <= 0) continue;
            drawParticle(p.x, p.y, p.z, p.size, p.color);
        }

        // Draw tail particles and their trails
        for (int i = 0; i < tailParticles.size(); i++) {
            Particle p = tailParticles.get(i);
            if (p.life <= 0) continue;

            // Main particle
            drawParticle(p.x, p.y, p.z, p.size, p.color);

            // Trail
            for (int j = 0; j < TAIL_LENGTH; j++) {
                if (tailTrailLife[i][j] <= 0) continue;
                // Use initial life (0.3f + random * 0.4f) as the reference for trail alpha
                float initialLife = 0.3f + RAND.nextFloat() * 0.4f;
                float trailAlpha = tailTrailLife[i][j] / initialLife * 0.5f;
                if (trailAlpha <= 0) continue;

                float[] trailColor = {
                    p.color[0] * 0.7f,
                    p.color[1] * 0.7f,
                    p.color[2] * 0.7f,
                    trailAlpha * p.color[3]
                };
                float trailSize = p.size * (0.5f + j * 0.1f);
                drawParticle(tailTrailX[i][j], tailTrailY[i][j], p.z, trailSize, trailColor);
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

    private void drawComet(float[] mvpMatrix, int prog) {
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
        drawComet(mvpMatrix, texturedShaderProgram2D);
    }

    @Override
    public void draw3D(float[] mvpMatrix, GLVisualizerRenderer renderer) {
        drawComet(mvpMatrix, texturedShaderProgram3D);
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

