package com.ikechi.studio.onwa.player.view.core.styles;

import android.opengl.GLES20;

import com.ikechi.studio.onwa.player.view.core.GLVisualizerRenderer;
import com.ikechi.studio.onwa.player.view.core.visuals.VisualStyle;
import com.ikechi.studio.onwa.player.view.core.objects.CosmicParticle;
import com.ikechi.studio.onwa.player.utils.TextureHelper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Deep-space particle system – particles drift slowly through a 3-D cube,
 * then warp-speed on beats.  Uses additive blending for a nebula glow.
 *
 * FIX: The original allocateBuffers() called glDeleteBuffers(3, ...) but only
 * created 2 VBOs, causing a crash.  There is now only one interleaved VBO.
 */
public class CosmicStyle extends VisualStyle {

    private static final int MAX_PARTICLES = 800;

    private final List<CosmicParticle> particles = new ArrayList<>(MAX_PARTICLES);

    private int         particleTexture;
    private int         vboId;           // one interleaved VBO: pos + uv + colour
    private FloatBuffer vertexBuffer;
    private int         vertexCount;     // 6 per particle
    private boolean     buffersInitialized = false;

    @Override
    public boolean usesTexture() { return true; }

    @Override
    public void init(int shader2D, int shader3D, int shader3DUniform,
                     int texturedShader2D, int texturedShader3D,
                     GLVisualizerRenderer renderer) {
        super.init(shader2D, shader3D, shader3DUniform,
                   texturedShader2D, texturedShader3D, renderer);

        // Re-initialise safely (idempotent after GL context loss / resume)
        if (buffersInitialized) {
            GLES20.glDeleteBuffers(1, new int[]{vboId}, 0);          // FIX: was 3
            GLES20.glDeleteTextures(1, new int[]{particleTexture}, 0);
        }

        particleTexture = TextureHelper.createCircleTexture();
        spawnParticles();
        allocateBuffers();
    }

    private void spawnParticles() {
        particles.clear();
        for (int i = 0; i < MAX_PARTICLES; i++) particles.add(new CosmicParticle());
    }

    private void allocateBuffers() {
        vertexCount = MAX_PARTICLES * 6;
        int stride  = 3 + 2 + 4;          // pos + uv + colour
        ByteBuffer bb = ByteBuffer.allocateDirect(vertexCount * stride * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();

        int[] ids = new int[1];
        GLES20.glGenBuffers(1, ids, 0);
        vboId = ids[0];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertexCount * stride * 4, null, GLES20.GL_DYNAMIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        buffersInitialized = true;
    }

    @Override
    public void update(float deltaTime, float[] fftBands, float beatIntensity, float totalTime) {
        this.totalTime = totalTime;

        for (CosmicParticle p : particles) {
            p.update(deltaTime, beatIntensity);
            if (!p.isAlive()) p.reset();
        }

        // Optional FFT-driven speed modulation
        float audioScale = 1.0f;
        if (fftBands != null && fftBands.length > 0) {
            float sum = 0f;
            int   n   = Math.min(fftBands.length / 4, 8);
            for (int k = 0; k < n; k++) sum += fftBands[k];
            audioScale = 1f + (sum / Math.max(n, 1)) * renderer.getSensitivity() * 1.5f;
        }
        final float warpSpeed = audioScale * (1f + beatIntensity * 6f);

        // Pack quads
        vertexBuffer.clear();
        for (CosmicParticle p : particles) {
            float s  = p.size * warpSpeed * 0.6f;
            float x  = p.x, y = p.y, z = p.z;
            float[] pos = { x-s, y-s, z,  x+s, y-s, z,  x-s, y+s, z,  x+s, y+s, z };
            float[] uv  = { 0f,0f,  1f,0f,  0f,1f,  1f,1f };
            int[] tris  = { 0,1,2,  1,3,2 };
            for (int idx : tris) {
                vertexBuffer.put(pos[idx*3]); vertexBuffer.put(pos[idx*3+1]); vertexBuffer.put(pos[idx*3+2]);
                vertexBuffer.put(uv[idx*2]);  vertexBuffer.put(uv[idx*2+1]);
                vertexBuffer.put(p.color);
            }
        }
        vertexBuffer.flip();

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, vertexBuffer.capacity() * 4, vertexBuffer);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    private void drawTextured(float[] mvpMatrix, int prog) {
        GLES20.glUseProgram(prog);
        int posH = GLES20.glGetAttribLocation (prog, "vPosition");
        int texH = GLES20.glGetAttribLocation (prog, "aTexCoord");
        int colH = GLES20.glGetAttribLocation (prog, "aColor");
        int mvpH = GLES20.glGetUniformLocation(prog, "uMVPMatrix");
        int texU = GLES20.glGetUniformLocation(prog, "uTexture");

        if (mvpH != -1) GLES20.glUniformMatrix4fv(mvpH, 1, false, mvpMatrix, 0);
        if (texU != -1) GLES20.glUniform1i(texU, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, particleTexture);

        int stride = (3 + 2 + 4) * 4;
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
        if (posH != -1) { GLES20.glVertexAttribPointer(posH, 3, GLES20.GL_FLOAT, false, stride, 0);   GLES20.glEnableVertexAttribArray(posH); }
        if (texH != -1) { GLES20.glVertexAttribPointer(texH, 2, GLES20.GL_FLOAT, false, stride, 3*4); GLES20.glEnableVertexAttribArray(texH); }
        if (colH != -1) { GLES20.glVertexAttribPointer(colH, 4, GLES20.GL_FLOAT, false, stride, 5*4); GLES20.glEnableVertexAttribArray(colH); }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE); // additive for nebula glow

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount);

        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        if (posH != -1) GLES20.glDisableVertexAttribArray(posH);
        if (texH != -1) GLES20.glDisableVertexAttribArray(texH);
        if (colH != -1) GLES20.glDisableVertexAttribArray(colH);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    @Override public void draw2D(float[] mvpMatrix, GLVisualizerRenderer renderer) { drawTextured(mvpMatrix, texturedShaderProgram2D); }
    @Override public void draw3D(float[] mvpMatrix, GLVisualizerRenderer renderer) { drawTextured(mvpMatrix, texturedShaderProgram3D); }

    @Override
    public void release() {
        if (buffersInitialized) {
            GLES20.glDeleteBuffers(1, new int[]{vboId}, 0);
            GLES20.glDeleteTextures(1, new int[]{particleTexture}, 0);
            buffersInitialized = false;
        }
    }
}

