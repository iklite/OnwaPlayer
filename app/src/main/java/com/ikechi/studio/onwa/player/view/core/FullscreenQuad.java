package com.ikechi.studio.onwa.player.view.core;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * A screen-space quad covering [-1,1] x [-1,1].
 * Useful for full-screen post-processing or background effects.
 */
public class FullscreenQuad {

    private static final float[] VERTICES = {
        -1f, -1f,
		1f, -1f,
        -1f,  1f,
		1f,  1f
    };

    private final FloatBuffer buffer;

    public FullscreenQuad() {
        ByteBuffer bb = ByteBuffer.allocateDirect(VERTICES.length * 4);
        bb.order(ByteOrder.nativeOrder());
        buffer = bb.asFloatBuffer();
        buffer.put(VERTICES);
        buffer.position(0);
    }

    /**
     * Draws the quad.  The shader program must already be in use.
     *
     * @param program Active GL program; must expose an "aPosition" vec2 attribute.
     */
    public void draw(int program) {
        if (program == 0) return;

        int pos = GLES20.glGetAttribLocation(program, "aPosition");
        if (pos == -1) return;

        buffer.position(0);
        GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 0, buffer);
        GLES20.glEnableVertexAttribArray(pos);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(pos);
    }
}

