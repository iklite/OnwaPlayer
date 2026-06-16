package com.ikechi.studio.onwa.player.view.core.visuals;

import android.opengl.GLES20;
import android.opengl.Matrix;

/**
 * Manages up to MAX_LIGHTS dynamic coloured point-lights.
 *
 * Lights orbit the scene and their colours cycle smoothly, so every style
 * automatically gets animated, music-reactive lighting without extra code.
 *
 * The beat-intensity scale passed in update() is used by GLVisualizerRenderer;
 * for now it's not plumbed here – lighting animates on time alone so the
 * fragment shader's attenuation reacts visually through the position changes.
 */
public class LightManager {

    public static final int MAX_LIGHTS = 3;

    private final float[] lightPositions = new float[MAX_LIGHTS * 3];
    private final float[] lightColors    = new float[MAX_LIGHTS * 3];
    private final float[] ambient        = {0.15f, 0.15f, 0.25f};

    private float totalTime = 0f;

    // Scratch vector for matrix multiply
    private final float[] tempVec4 = new float[4];
    private final float[] srcVec4  = new float[4];

    public void update(float deltaTime) {
        totalTime += deltaTime * 0.5f;

        for (int i = 0; i < MAX_LIGHTS; i++) {
            float phase = totalTime + i * 2.094f; // 120° phase offset per light (2π/3)

            lightPositions[i * 3]     = (float) Math.sin(phase)          * 2.2f;
            lightPositions[i * 3 + 1] = (float) Math.cos(phase * 0.7f)   * 1.6f;
            lightPositions[i * 3 + 2] = 2.0f + (float) Math.sin(phase * 0.5f);

            lightColors[i * 3]     = 0.5f + 0.5f * (float) Math.sin(phase);
            lightColors[i * 3 + 1] = 0.5f + 0.5f * (float) Math.sin(phase + 2.094f);
            lightColors[i * 3 + 2] = 0.5f + 0.5f * (float) Math.sin(phase + 4.189f);
        }
    }

    /**
     * Upload all lighting uniforms to the given program.
     *
     * @param program   Active GL program that contains the light uniforms.
     * @param viewMatrix The current view matrix (light positions are
     *                   transformed to view-space before upload).
     */
    public void setLightUniforms(int program, float[] viewMatrix) {
        if (program == 0) return;

        int lightCountLoc = GLES20.glGetUniformLocation(program, "uLightCount");
        if (lightCountLoc != -1) GLES20.glUniform1i(lightCountLoc, MAX_LIGHTS);

        int ambientLoc = GLES20.glGetUniformLocation(program, "uAmbient");
        if (ambientLoc != -1) GLES20.glUniform3fv(ambientLoc, 1, ambient, 0);

        for (int i = 0; i < MAX_LIGHTS; i++) {
            // Transform light world-position to view-space
            srcVec4[0] = lightPositions[i * 3];
            srcVec4[1] = lightPositions[i * 3 + 1];
            srcVec4[2] = lightPositions[i * 3 + 2];
            srcVec4[3] = 1.0f;
            Matrix.multiplyMV(tempVec4, 0, viewMatrix, 0, srcVec4, 0);

            String posName = "uLights[" + i + "].position";
            int posLoc = GLES20.glGetUniformLocation(program, posName);
            if (posLoc != -1) GLES20.glUniform3f(posLoc, tempVec4[0], tempVec4[1], tempVec4[2]);

            String colName = "uLights[" + i + "].color";
            int colLoc = GLES20.glGetUniformLocation(program, colName);
            if (colLoc != -1) GLES20.glUniform3f(colLoc,
												 lightColors[i * 3], lightColors[i * 3 + 1], lightColors[i * 3 + 2]);
        }
    }
}

