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
 * Galaxy: A spiral galaxy with a bright core and swirling arms.
 * - Spiral arms (logarithmic spiral)
 * - Bright galactic core
 * - Scattered stars
 * - Slow rotation
 */
public class GalaxyStyle extends VisualStyle {
    private static final Random RAND = new Random();
    private static final int MAX_CORE_PARTICLES = 100;
    private static final int MAX_ARM_PARTICLES = 300;
    private static final int MAX_STAR_PARTICLES = 200;
    private static final float GALAXY_SIZE = 1.5f;
    private static final float CORE_RADIUS = 0.2f;
    private static final float ARM_RADIUS = 0.8f;
    private static final float ROTATION_SPEED = 0.1f; // Radians per second

    private final List<Particle> coreParticles = new ArrayList<>(MAX_CORE_PARTICLES);
    private final List<Particle> armParticles = new ArrayList<>(MAX_ARM_PARTICLES);
    private final List<Particle> starParticles = new ArrayList<>(MAX_STAR_PARTICLES);
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
        coreParticles.clear();
        armParticles.clear();
        starParticles.clear();
        for (int i = 0; i < MAX_CORE_PARTICLES; i++) {
            coreParticles.add(new Particle());
        }
        for (int i = 0; i < MAX_ARM_PARTICLES; i++) {
            armParticles.add(new Particle());
        }
        for (int i = 0; i < MAX_STAR_PARTICLES; i++) {
            starParticles.add(new Particle());
        }
        for (Particle p : coreParticles) {
            resetCoreParticle(p);
        }
        for (Particle p : armParticles) {
            resetArmParticle(p);
        }
        for (Particle p : starParticles) {
            resetStarParticle(p);
        }

        allocateBuffers();
        buffersInitialized = true;
    }

    private void allocateBuffers() {
        particleVertexCount = (MAX_CORE_PARTICLES + MAX_ARM_PARTICLES + MAX_STAR_PARTICLES) * 6;
        int stride = 3 + 2 + 4;

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

        float audioEnergy = 0f;
        if (fftBands != null && fftBands.length > 0) {
            for (float v : fftBands) audioEnergy += v;
            audioEnergy /= fftBands.length * renderer.getSensitivity();
        }
        smoothedEnergy += (audioEnergy - smoothedEnergy) * 0.1f;

        // Update core particles (pulsing)
        for (Particle p : coreParticles) {
            p.color[3] = 0.7f + smoothedEnergy * 0.3f;
        }

        // Update arm particles (rotating)
        for (Particle p : armParticles) {
            // Rotate around galaxy center
            float dist = (float) Math.sqrt(p.x * p.x + p.y * p.y);
            float angle = (float) Math.atan2(p.y, p.x) + rotationAngle;
            p.x = (float) Math.cos(angle) * dist;
            p.y = (float) Math.sin(angle) * dist;
        }

        // Update star particles (twinkling)
        for (Particle p : starParticles) {
            if (RAND.nextFloat() < 0.005f) {
                p.color[3] = 0.3f + RAND.nextFloat() * 0.7f;
            }
            p.color[3] += (0.6f - p.color[3]) * 0.01f;
        }

        buildParticleBuffer();
    }

    private void resetCoreParticle(Particle p) {
        double angle = RAND.nextDouble() * 2.0 * Math.PI;
        float dist = RAND.nextFloat() * CORE_RADIUS;
        p.x = (float) Math.cos(angle) * dist;
        p.y = (float) Math.sin(angle) * dist;
        p.z = (RAND.nextFloat() - 0.5f) * 0.1f;

        p.vx = 0f;
        p.vy = 0f;
        p.vz = 0f;

        p.size = 0.03f + RAND.nextFloat() * 0.04f;
        p.life = 1.0f;
        p.decay = 0f;

        // Color: white/yellow
        p.color = new float[]{
            0.9f + RAND.nextFloat() * 0.1f,
            0.95f + RAND.nextFloat() * 0.05f,
            0.8f + RAND.nextFloat() * 0.2f,
            0.7f + RAND.nextFloat() * 0.2f
        };
    }

    private void resetArmParticle(Particle p) {
        // Logarithmic spiral: r = a * e^(b * theta)
        double theta = RAND.nextDouble() * 10.0 * Math.PI;
        float a = CORE_RADIUS;
        float b = 0.1f;
        float r = (float) (a * Math.exp(b * theta));
        p.x = (float) Math.cos(theta) * r;
        p.y = (float) Math.sin(theta) * r;
        p.z = (RAND.nextFloat() - 0.5f) * 0.1f;

        p.vx = 0f;
        p.vy = 0f;
        p.vz = 0f;

        p.size = 0.02f + RAND.nextFloat() * 0.03f;
        p.life = 1.0f;
        p.decay = 0f;

        // Color: blue/white
        p.color = new float[]{
            0.5f + RAND.nextFloat() * 0.3f,
            0.7f + RAND.nextFloat() * 0.2f,
            0.9f + RAND.nextFloat() * 0.1f,
            0.5f + RAND.nextFloat() * 0.3f
        };
    }

    private void resetStarParticle(Particle p) {
        p.x = (RAND.nextFloat() - 0.5f) * GALAXY_SIZE * 2f;
        p.y = (RAND.nextFloat() - 0.5f) * GALAXY_SIZE * 2f;
        p.z = (RAND.nextFloat() - 0.5f) * 0.1f;

        p.vx = 0f;
        p.vy = 0f;
        p.vz = 0f;

        p.size = 0.01f + RAND.nextFloat() * 0.02f;
        p.life = 1.0f;
        p.decay = 0f;

        // Color: white with slight tint
        p.color = new float[]{
            0.9f + RAND.nextFloat() * 0.1f,
            0.95f + RAND.nextFloat() * 0.05f,
            1.0f,
            0.3f + RAND.nextFloat() * 0.5f
        };
    }

    private void buildParticleBuffer() {
        particleVertexBuffer.clear();

        // Draw core particles
        for (Particle p : coreParticles) {
            drawParticle(p.x, p.y, p.z, p.size, p.color);
        }

        // Draw arm particles
        for (Particle p : armParticles) {
            drawParticle(p.x, p.y, p.z, p.size, p.color);
        }

        // Draw star particles
        for (Particle p : starParticles) {
            drawParticle(p.x, p.y, p.z, p.size, p.color);
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

    private void drawGalaxy(float[] mvpMatrix, int prog) {
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
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        int verts = particleVertexBuffer.limit() / 9;
        if (verts > 0) {
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, verts);
        }

        if (posH != -1) GLES20.glDisableVertexAttribArray(posH);
        if (texH != -1) GLES20.glDisableVertexAttribArray(texH);
        if (colH != -1) GLES20.glDisableVertexAttribArray(colH);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    @Override
    public void draw2D(float[] mvpMatrix, GLVisualizerRenderer renderer) {
        drawGalaxy(mvpMatrix, texturedShaderProgram2D);
    }

    @Override
    public void draw3D(float[] mvpMatrix, GLVisualizerRenderer renderer) {
        drawGalaxy(mvpMatrix, texturedShaderProgram3D);
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

