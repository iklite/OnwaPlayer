package com.ikechi.studio.onwa.player.view.core.styles;

import android.opengl.GLES20;
import com.ikechi.studio.onwa.player.view.core.GLVisualizerRenderer;
import com.ikechi.studio.onwa.player.view.core.visuals.VisualStyle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Rotating nested geometric polygons.
 * Size, color, and motion respond to FFT energy and beat intensity.
 * Uses the UNTEXTURED shader – hence overrides {@link #usesTexture()} to return {@code false}.
 */
public class GeometricStyle extends VisualStyle {
    private static final int MAX_VERTS = 128;
    private final FloatBuffer tempVB;
    private final FloatBuffer tempCB;
    private float[] currentFft;
    private float currentBeat;
    private float totalTime;

    public GeometricStyle() {
        ByteBuffer vbb = ByteBuffer.allocateDirect(MAX_VERTS * 3 * 4);
        vbb.order(ByteOrder.nativeOrder());
        tempVB = vbb.asFloatBuffer();

        ByteBuffer cbb = ByteBuffer.allocateDirect(MAX_VERTS * 4 * 4);
        cbb.order(ByteOrder.nativeOrder());
        tempCB = cbb.asFloatBuffer();
    }

    // ★ Tell the renderer we do NOT need a texture
    @Override
    public boolean usesTexture() {
        return false;
    }

    @Override
    public void init(int shader2D, int shader3D, int shader3DUniform,
                     int texturedShader2D, int texturedShader3D,
                     GLVisualizerRenderer renderer) {
        super.init(shader2D, shader3D, shader3DUniform,
                   texturedShader2D, texturedShader3D, renderer);
    }

    @Override
    public void update(float deltaTime, float[] fftBands,
                       float beatIntensity, float totalTime) {
        this.totalTime = totalTime;
        this.currentFft = fftBands;
        this.currentBeat = beatIntensity;
    }

    @Override
    public void draw2D(float[] mvpMatrix, GLVisualizerRenderer renderer) {
        GLES20.glUseProgram(shaderProgram2D);
        drawGeometry(mvpMatrix, shaderProgram2D, renderer);
    }

    @Override
    public void draw3D(float[] mvpMatrix, GLVisualizerRenderer renderer) {
        GLES20.glUseProgram(shaderProgram3D);
        drawGeometry(mvpMatrix, shaderProgram3D, renderer);
    }

    private void drawGeometry(float[] mvpMatrix, int program,
                              GLVisualizerRenderer renderer) {
        float time = totalTime * 0.3f;
        int shapes = 12;

        for (int i = 0; i < shapes; i++) {
            float rotDeg = time * 57.3f + i * 30f;
            // ★ Increased base radius
            float baseRad = 0.35f + (float) Math.sin(totalTime * 0.7f + i) * 0.15f;

            // FFT modulation
            if (currentFft != null && currentFft.length > 0) {
                int bi = (i * currentFft.length) / shapes;
                baseRad += currentFft[Math.min(bi, currentFft.length - 1)] * 0.25f;
            }
            baseRad = Math.min(baseRad + currentBeat * 0.12f, 0.95f);

            // Dynamic colour
            float t = (float) i / shapes;
            float[] color = new float[] {
                0.5f + 0.5f * (float) Math.sin(time + i * 0.3f),
                0.5f + 0.5f * (float) Math.cos(time + i * 0.5f),
                0.5f + 0.5f * (float) Math.sin(time + i * 0.7f),
                0.9f
            };

            int sides = 3 + (i % 5); // 3,4,5,6,7
            drawRegularPolygon(program, mvpMatrix, 0f, 0f,
                               baseRad, sides, rotDeg, color);

            // Inner nested shape
            float[] innerColor = new float[] {
                color[2], color[0], color[1], 0.6f
            };
            drawRegularPolygon(program, mvpMatrix, 0f, 0f,
                               baseRad * 0.45f, sides + 2,
                               -rotDeg * 1.2f, innerColor);
        }
    }

    private void drawRegularPolygon(int program, float[] mvpMatrix,
                                    float cx, float cy, float radius,
                                    int sides, float rotDeg, float[] color) {
        if (sides < 3) sides = 3;
        int vertexCount = sides + 2; // centre + perimeter + closing
        if (vertexCount > MAX_VERTS) return;

        double rot = Math.toRadians(rotDeg);

        tempVB.clear();
        tempCB.clear();

        // centre point
        tempVB.put(cx).put(cy).put(0f);
        tempCB.put(color[0]).put(color[1]).put(color[2]).put(color[3]);

        // perimeter points
        for (int i = 0; i <= sides; i++) {
            double angle = rot + 2.0 * Math.PI * i / sides;
            tempVB.put(cx + (float) Math.cos(angle) * radius);
            tempVB.put(cy + (float) Math.sin(angle) * radius);
            tempVB.put(0f);
            tempCB.put(color[0]).put(color[1]).put(color[2]).put(color[3]);
        }

        tempVB.flip();
        tempCB.flip();

        // --- Shader attributes ---
        int posHandle = GLES20.glGetAttribLocation(program, "vPosition");
        int colHandle = GLES20.glGetAttribLocation(program, "aColor");
        int mvpHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
        int colUniform = GLES20.glGetUniformLocation(program, "uColor");

        // Use the renderer-supplied matrix; if not available, use identity
        if (mvpHandle != -1) {
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0);
        }

        // Vertex position
        if (posHandle != -1) {
            GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT,
                                         false, 0, tempVB);
            GLES20.glEnableVertexAttribArray(posHandle);
        }

        // ★ Robust colour handling
        if (colHandle != -1) {
            // per-vertex colour
            GLES20.glVertexAttribPointer(colHandle, 4, GLES20.GL_FLOAT,
                                         false, 0, tempCB);
            GLES20.glEnableVertexAttribArray(colHandle);
        } else if (colUniform != -1) {
            // fallback: uniform colour
            GLES20.glUniform4fv(colUniform, 1, color, 0);
        }
        // If neither exists, the shader will use a default colour – still visible

        // Enable blending for translucency
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, vertexCount);

        // Cleanup
        if (posHandle != -1) GLES20.glDisableVertexAttribArray(posHandle);
        if (colHandle != -1) GLES20.glDisableVertexAttribArray(colHandle);
    }

    @Override
    public void release() {}
}
