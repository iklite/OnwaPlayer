package com.ikechi.studio.onwa.player.view.core.visuals;

import android.opengl.GLES20;
import com.ikechi.studio.onwa.player.view.core.*;

public abstract class VisualStyle {

    protected int shaderProgram2D;
    protected int shaderProgram3D;
    protected int shaderProgram3DUniform;
    protected int texturedShaderProgram2D;
    protected int texturedShaderProgram3D;
    protected GLVisualizerRenderer renderer;
    protected float totalTime;

    /**
     * Called once after the GL context is ready.
     * Subclasses must call super.init() first, then do their own GL setup.
     */
    public void init(int shader2D, int shader3D, int shader3DUniform,
                     int texturedShader2D, int texturedShader3D,
                     GLVisualizerRenderer renderer) {
        this.shaderProgram2D         = shader2D;
        this.shaderProgram3D         = shader3D;
        this.shaderProgram3DUniform  = shader3DUniform;
        this.texturedShaderProgram2D = texturedShader2D;
        this.texturedShaderProgram3D = texturedShader3D;
        this.renderer                = renderer;
    }

    /**
     * Return true if this style uses uniform (single) colour in its 3D draw.
     * The renderer will bind shaderProgram3DUniform for you before calling draw3D().
     */
    public boolean usesUniformColor() { return false; }

    /**
     * Return true if this style renders textured quads.
     * The renderer will bind the appropriate textured shader for you.
     */
    public boolean usesTexture() { return false; }

    /** Called every frame on the GL thread. Update animation state here. */
    public abstract void update(float deltaTime, float[] fftBands,
                                float beatIntensity, float totalTime);

    /** Draw using the 2D orthographic program. */
    public abstract void draw2D(float[] mvpMatrix,
                                GLVisualizerRenderer renderer);

    /** Draw using the 3D perspective program. */
    public abstract void draw3D(float[] mvpMatrix,
                                GLVisualizerRenderer renderer);

    /** Release all GL resources owned by this style. */
    public abstract void release();

    // ---- Convenience helpers ----

    /** Helper: safely set the uMVPMatrix uniform in any program. */
    protected static void setMVP(int program, float[] mvp) {
        int handle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
        if (handle != -1) GLES20.glUniformMatrix4fv(handle, 1, false, mvp, 0);
    }

    /** Helper: safely enable a vertex attrib array identified by name. */
    protected static int enableAttrib(int program, String name) {
        int loc = GLES20.glGetAttribLocation(program, name);
        if (loc != -1) GLES20.glEnableVertexAttribArray(loc);
        return loc;
    }

    /** Helper: safely disable a vertex attrib array by location. */
    protected static void disableAttrib(int loc) {
        if (loc != -1) GLES20.glDisableVertexAttribArray(loc);
    }
}

