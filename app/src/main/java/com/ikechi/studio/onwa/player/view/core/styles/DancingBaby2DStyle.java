package com.ikechi.studio.onwa.player.view.core.styles;

import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.Matrix;
import android.util.Log;

import com.ikechi.studio.onwa.player.view.core.GLVisualizerRenderer;
import com.ikechi.studio.onwa.player.view.core.visuals.VisualStyle;
import com.ikechi.studio.onwa.player.utils.TextureHelper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * MANDALA FLOW - Intricate Radial Particle Patterns
 * 
 * Creates flowing mandala-like patterns with:
 * - 8-fold radial symmetry
 * - Multiple concentric layers
 * - Sine-wave modulated petals
 * - Color gradients through spectrum
 * - Audio-reactive morphing
 */
public class DancingBaby2DStyle extends VisualStyle {

    private static final String TAG = "DancingBaby2DStyle";
    private static final Random RAND = new Random();

    // 2000 particles for smooth patterns
    private static final int MAX_PARTICLES = 2000;
    private static final float PARTICLE_SIZE = 0.06f;

    // Mandala configuration
    private static final int SYMMETRY_FOLDS = 8;  // 8 petals
    private static final int CONCENTRIC_LAYERS = 5; // 5 rings
    private static final float MANDALA_RADIUS = 1.2f;

    // Flow speeds
    private static final float ORBIT_SPEED = 0.4f;
    private static final float RADIAL_PULSE_SPEED = 0.6f;
    private static final float PETAL_MORPH_SPEED = 0.3f;

    private static class MandalaParticle {
        int layer;        // Which concentric ring (0-4)
        int sector;       // Which of 8 sectors
        float orbitPhase; // Position in orbit (0-2π)
        float baseRadius; // Distance from center
        float x, y, z;
        float[] color = new float[4];
        float size;
        float life;

        void reset(int layer, int sector, int particlesPerLayer) {
            this.layer = layer;
            this.sector = sector;
            this.orbitPhase = ((float)sector / SYMMETRY_FOLDS) * (float)(Math.PI * 2) 
				+ RAND.nextFloat() * 0.1f; // Small random spread
            this.baseRadius = 0.2f + (layer * 0.25f) + RAND.nextFloat() * 0.1f;
            this.life = 1.0f;
            this.size = PARTICLE_SIZE * (1.0f + layer * 0.3f);
            this.z = 0;
            updatePosition(0, 0, 0);
        }

        void updatePosition(float time, float audioBass, float audioMid) {
            // Base orbit rotation
            float orbitAngle = orbitPhase + time * ORBIT_SPEED * (1.0f + layer * 0.2f);

            // Petal modulation - sine waves create flower shape
            float petalMod = (float)Math.sin(orbitAngle * SYMMETRY_FOLDS + time * PETAL_MORPH_SPEED);
            float petalDepth = 0.15f + audioMid * 0.1f; // Audio affects petal depth

            // Radial breathing/pulsing
            float pulse = (float)Math.sin(time * RADIAL_PULSE_SPEED + layer) * 0.08f * (1 + audioBass);
            float currentRadius = baseRadius + pulse + petalMod * petalDepth;

            // Calculate position with petal offset
            float petalAngle = orbitAngle + petalMod * 0.15f; // Slight angular wobble

            x = (float)Math.cos(petalAngle) * currentRadius;
            y = (float)Math.sin(petalAngle) * currentRadius;

            // Z varies with petal height (3D effect)
            z = petalMod * 0.15f * (1 + layer * 0.2f);
        }
    }

    private static class MandalaComparator implements Comparator<MandalaParticle> {
        private final float[] viewMatrix;
        MandalaComparator(float[] viewMatrix) { this.viewMatrix = viewMatrix; }

        @Override
        public int compare(MandalaParticle a, MandalaParticle b) {
            float depthA = a.x * viewMatrix[2] + a.y * viewMatrix[6] + a.z * viewMatrix[10] + viewMatrix[14];
            float depthB = b.x * viewMatrix[2] + b.y * viewMatrix[6] + b.z * viewMatrix[10] + viewMatrix[14];
            return Float.compare(depthB, depthA);
        }
    }

    private List<MandalaParticle> particles;
    private int particleTexture;
    private int glowTexture;
    private int vboId;
    private int vaoId;
    private FloatBuffer vertexBuffer;
    private int vertexCount;
    private boolean initialized = false;

    private float smoothedBass = 0f;
    private float smoothedMid = 0f;
    private float smoothedTreble = 0f;
    private int beatPulse = 0;

    private float cameraAngle = 0f;
    private float mandalaRotation = 0f;
    private float[] viewMatrix = new float[16];
    private float[] projectionMatrix = new float[16];
    private float[] vpMatrix = new float[16];

    private int frameCounter = 0;

    @Override
    public boolean usesTexture() { return true; }

    @Override
    public void init(int shader2D, int shader3D, int shader3DUniform,
                     int texturedShader2D, int texturedShader3D,
                     GLVisualizerRenderer renderer) {
        super.init(shader2D, shader3D, shader3DUniform,
                   texturedShader2D, texturedShader3D, renderer);

        cleanup();

        particleTexture = TextureHelper.createCircleTexture();
        glowTexture = TextureHelper.createGlowTexture();

        particles = new ArrayList<MandalaParticle>(MAX_PARTICLES);

        // Create mandala structure: layers × sectors
        int particlesPerLayer = MAX_PARTICLES / CONCENTRIC_LAYERS;
        int idx = 0;
        for (int layer = 0; layer < CONCENTRIC_LAYERS && idx < MAX_PARTICLES; layer++) {
            for (int i = 0; i < particlesPerLayer && idx < MAX_PARTICLES; i++) {
                MandalaParticle p = new MandalaParticle();
                // Distribute across all 8 sectors evenly
                int sector = i % SYMMETRY_FOLDS;
                p.reset(layer, sector, particlesPerLayer);
                // Add slight random offset for organic feel
                p.orbitPhase += (i / (float)particlesPerLayer) * (float)(Math.PI * 2) / SYMMETRY_FOLDS;
                particles.add(p);
                idx++;
            }
        }

        allocateBuffers();
        setupCamera();

        initialized = true;
        Log.d(TAG, "Mandala Flow initialized: " + particles.size() + " particles, " 
              + SYMMETRY_FOLDS + "-fold symmetry, " + CONCENTRIC_LAYERS + " layers");
    }

    private void cleanup() {
        if (vboId != 0) GLES20.glDeleteBuffers(1, new int[]{vboId}, 0);
        if (vaoId != 0 && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
            GLES30.glDeleteVertexArrays(1, new int[]{vaoId}, 0);
        }
        if (particleTexture != 0) GLES20.glDeleteTextures(1, new int[]{particleTexture}, 0);
        if (glowTexture != 0) GLES20.glDeleteTextures(1, new int[]{glowTexture}, 0);
        vboId = vaoId = particleTexture = glowTexture = 0;
    }

    private void allocateBuffers() {
        vertexCount = MAX_PARTICLES * 6;
        int stride = (3 + 2 + 4) * 4;

        ByteBuffer bb = ByteBuffer.allocateDirect(vertexCount * stride);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
            int[] vaos = new int[1];
            GLES30.glGenVertexArrays(1, vaos, 0);
            vaoId = vaos[0];
        }

        int[] vbos = new int[1];
        GLES20.glGenBuffers(1, vbos, 0);
        vboId = vbos[0];

        if (vaoId != 0) {
            GLES30.glBindVertexArray(vaoId);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertexCount * stride, null, GLES20.GL_DYNAMIC_DRAW);

            int prog = texturedShaderProgram3D != 0 ? texturedShaderProgram3D : texturedShaderProgram2D;
            if (prog != 0) {
                int pos = GLES20.glGetAttribLocation(prog, "vPosition");
                int tex = GLES20.glGetAttribLocation(prog, "aTexCoord");
                int col = GLES20.glGetAttribLocation(prog, "aColor");

                if (pos != -1) {
                    GLES20.glVertexAttribPointer(pos, 3, GLES20.GL_FLOAT, false, stride, 0);
                    GLES20.glEnableVertexAttribArray(pos);
                }
                if (tex != -1) {
                    GLES20.glVertexAttribPointer(tex, 2, GLES20.GL_FLOAT, false, stride, 3*4);
                    GLES20.glEnableVertexAttribArray(tex);
                }
                if (col != -1) {
                    GLES20.glVertexAttribPointer(col, 4, GLES20.GL_FLOAT, false, stride, 5*4);
                    GLES20.glEnableVertexAttribArray(col);
                }
            }

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
            GLES30.glBindVertexArray(0);
        }
    }

    private void setupCamera() {
        Matrix.setLookAtM(viewMatrix, 0, 0, 0, 2.8f, 0, 0, 0, 0, 1, 0);
        Matrix.perspectiveM(projectionMatrix, 0, 50f, 1f, 0.1f, 100f);
        updateVPMatrix();
    }

    private void updateVPMatrix() {
        Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
    }

    @Override
    public void update(float deltaTime, float[] fftBands, float beatIntensity, float totalTime) {
        if (!initialized) return;

        this.totalTime = totalTime;
        frameCounter++;

        analyzeAudio(fftBands, beatIntensity);

        // Camera slowly orbits
        cameraAngle += deltaTime * 0.08f;
        float camX = (float)Math.sin(cameraAngle) * 2.8f;
        float camZ = (float)Math.cos(cameraAngle) * 2.8f;
        float camY = smoothedMid * 0.2f;

        Matrix.setLookAtM(viewMatrix, 0, camX, camY, camZ, 0, 0, 0, 0, 1, 0);
        updateVPMatrix();

        // Mandala rotation speed based on audio
        float rotationSpeed = 0.15f + smoothedBass * 0.3f;
        mandalaRotation += deltaTime * rotationSpeed;

        updateParticles(deltaTime, totalTime);

        if (frameCounter % 2 == 0) {
            Collections.sort(particles, new MandalaComparator(viewMatrix));
        }

        buildVertexBuffer();
    }

    private void analyzeAudio(float[] fftBands, float beatIntensity) {
        if (fftBands == null || fftBands.length == 0) {
            smoothedBass = lerp(smoothedBass, 0, 0.1f);
            smoothedMid = lerp(smoothedMid, 0, 0.1f);
            smoothedTreble = lerp(smoothedTreble, 0, 0.1f);
            return;
        }

        int bassEnd = fftBands.length / 8;
        int midEnd = fftBands.length / 2;

        float bass = 0, mid = 0, treble = 0;
        for (int i = 0; i < fftBands.length; i++) {
            float v = fftBands[i] * renderer.getSensitivity();
            if (i < bassEnd) bass += v * 2f;
            else if (i < midEnd) mid += v;
            else treble += v;
        }

        bass = Math.min(bass / bassEnd, 3f);
        mid = Math.min(mid / (midEnd - bassEnd), 2f);
        treble = Math.min(treble / (fftBands.length - midEnd), 1.5f);

        smoothedBass = lerp(smoothedBass, bass, 0.1f);
        smoothedMid = lerp(smoothedMid, mid, 0.12f);
        smoothedTreble = lerp(smoothedTreble, treble, 0.15f);

        if (beatIntensity > 0.5f && smoothedBass > 0.6f) {
            beatPulse = 10;
        }
        if (beatPulse > 0) beatPulse--;
    }

    private void updateParticles(float deltaTime, float totalTime) {
        float time = totalTime;

        for (int i = 0; i < particles.size(); i++) {
            MandalaParticle p = particles.get(i);

            // Update position with mandala flow
            p.updatePosition(time + mandalaRotation, smoothedBass, smoothedMid);

            // Color based on layer, sector, and audio
            float hue = (p.layer / (float)CONCENTRIC_LAYERS + p.sector / (float)SYMMETRY_FOLDS 
				+ time * 0.1f + smoothedBass * 0.2f) % 1.0f;

            // HSV to RGB for rainbow mandala
            float[] rgb = hsvToRgb(hue, 0.8f, 0.9f + smoothedTreble * 0.1f);

            // Beat flash - brighten everything
            if (beatPulse > 0) {
                float flash = beatPulse / 10f;
                rgb[0] = lerp(rgb[0], 1.0f, flash * 0.3f);
                rgb[1] = lerp(rgb[1], 0.9f, flash * 0.2f);
                rgb[2] = lerp(rgb[2], 0.8f, flash * 0.2f);
            }

            p.color[0] = rgb[0];
            p.color[1] = rgb[1];
            p.color[2] = rgb[2];
            p.color[3] = 0.7f + (1.0f - p.layer / (float)CONCENTRIC_LAYERS) * 0.3f; // Inner brighter

            // Size pulse on beat
            if (beatPulse > 0) {
                p.size = PARTICLE_SIZE * (1.0f + p.layer * 0.3f) * (1.0f + beatPulse * 0.05f);
            } else {
                p.size = PARTICLE_SIZE * (1.0f + p.layer * 0.3f);
            }
        }
    }

    private float[] hsvToRgb(float h, float s, float v) {
        float[] rgb = new float[3];
        int i = (int)(h * 6);
        float f = h * 6 - i;
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);

        switch (i % 6) {
            case 0: rgb[0] = v; rgb[1] = t; rgb[2] = p; break;
            case 1: rgb[0] = q; rgb[1] = v; rgb[2] = p; break;
            case 2: rgb[0] = p; rgb[1] = v; rgb[2] = t; break;
            case 3: rgb[0] = p; rgb[1] = q; rgb[2] = v; break;
            case 4: rgb[0] = t; rgb[1] = p; rgb[2] = v; break;
            case 5: rgb[0] = v; rgb[1] = p; rgb[2] = q; break;
        }
        return rgb;
    }

    private void buildVertexBuffer() {
        vertexBuffer.clear();

        for (int i = 0; i < particles.size(); i++) {
            MandalaParticle p = particles.get(i);

            float s = p.size;
            float x = p.x, y = p.y, z = p.z;

            float[] pos = {x-s, y-s, z, x+s, y-s, z, x-s, y+s, z, x+s, y+s, z};
            float[] uv = {0,0, 1,0, 0,1, 1,1};
            int[] tris = {0,1,2, 1,3,2};

            for (int ti = 0; ti < tris.length; ti++) {
                int vi = tris[ti];
                vertexBuffer.put(pos[vi*3]);
                vertexBuffer.put(pos[vi*3+1]);
                vertexBuffer.put(pos[vi*3+2]);
                vertexBuffer.put(uv[vi*2]);
                vertexBuffer.put(uv[vi*2+1]);
                vertexBuffer.put(p.color[0]);
                vertexBuffer.put(p.color[1]);
                vertexBuffer.put(p.color[2]);
                vertexBuffer.put(p.color[3]);
            }
        }

        vertexBuffer.flip();

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, vertexBuffer.remaining() * 4, vertexBuffer);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    @Override
    public void draw2D(float[] mvpMatrix, GLVisualizerRenderer renderer) {
        draw3D(mvpMatrix, renderer);
    }

    @Override
    public void draw3D(float[] mvpMatrix, GLVisualizerRenderer renderer) {
        if (!initialized) return;

        int prog = texturedShaderProgram3D;
        if (prog == 0) prog = texturedShaderProgram2D;
        if (prog == 0) return;

        GLES20.glUseProgram(prog);

        int mvpLoc = GLES20.glGetUniformLocation(prog, "uMVPMatrix");
        if (mvpLoc != -1) GLES20.glUniformMatrix4fv(mvpLoc, 1, false, vpMatrix, 0);

        int texLoc = GLES20.glGetUniformLocation(prog, "uTexture");
        if (texLoc != -1) GLES20.glUniform1i(texLoc, 0);

        setLightingUniforms(prog);

        // Glow pass
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, glowTexture);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);
        drawGeometry();

        // Core pass
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, particleTexture);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        drawGeometry();

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        if (vaoId != 0) GLES30.glBindVertexArray(0);
    }

    private void drawGeometry() {
        if (vaoId != 0) {
            GLES30.glBindVertexArray(vaoId);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount);
        } else {
            int stride = (3 + 2 + 4) * 4;
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);

            int prog = texturedShaderProgram3D != 0 ? texturedShaderProgram3D : texturedShaderProgram2D;
            int pos = GLES20.glGetAttribLocation(prog, "vPosition");
            int tex = GLES20.glGetAttribLocation(prog, "aTexCoord");
            int col = GLES20.glGetAttribLocation(prog, "aColor");

            if (pos != -1) {
                GLES20.glVertexAttribPointer(pos, 3, GLES20.GL_FLOAT, false, stride, 0);
                GLES20.glEnableVertexAttribArray(pos);
            }
            if (tex != -1) {
                GLES20.glVertexAttribPointer(tex, 2, GLES20.GL_FLOAT, false, stride, 3*4);
                GLES20.glEnableVertexAttribArray(tex);
            }
            if (col != -1) {
                GLES20.glVertexAttribPointer(col, 4, GLES20.GL_FLOAT, false, stride, 5*4);
                GLES20.glEnableVertexAttribArray(col);
            }

            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount);

            if (pos != -1) GLES20.glDisableVertexAttribArray(pos);
            if (tex != -1) GLES20.glDisableVertexAttribArray(tex);
            if (col != -1) GLES20.glDisableVertexAttribArray(col);

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        }
    }

    private void setLightingUniforms(int prog) {
        int amb = GLES20.glGetUniformLocation(prog, "uAmbient");
        int cnt = GLES20.glGetUniformLocation(prog, "uLightCount");

        if (amb != -1) GLES20.glUniform3f(amb, 0.2f, 0.18f, 0.22f);
        if (cnt != -1) GLES20.glUniform1i(cnt, 3);

        int l0p = GLES20.glGetUniformLocation(prog, "uLights[0].position");
        int l0c = GLES20.glGetUniformLocation(prog, "uLights[0].color");
        if (l0p != -1) GLES20.glUniform3f(l0p, 3f, 2f, 2f);
        if (l0c != -1) GLES20.glUniform3f(l0c, 0.9f, 0.85f, 0.95f);

        int l1p = GLES20.glGetUniformLocation(prog, "uLights[1].position");
        int l1c = GLES20.glGetUniformLocation(prog, "uLights[1].color");
        if (l1p != -1) GLES20.glUniform3f(l1p, -2f, 3f, -1f);
        if (l1c != -1) GLES20.glUniform3f(l1c, 0.4f, 0.5f, 0.7f);

        int l2p = GLES20.glGetUniformLocation(prog, "uLights[2].position");
        int l2c = GLES20.glGetUniformLocation(prog, "uLights[2].color");
        if (l2p != -1) GLES20.glUniform3f(l2p, 0f, -2f, 2f);
        if (l2c != -1) GLES20.glUniform3f(l2c, 0.8f, 0.6f, 0.4f);
    }

    @Override
    public void release() {
        cleanup();
        initialized = false;
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}

