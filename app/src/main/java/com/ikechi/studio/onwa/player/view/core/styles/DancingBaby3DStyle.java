package com.ikechi.studio.onwa.player.view.core.styles;

import android.opengl.GLES20;
import android.opengl.Matrix;

import com.ikechi.studio.onwa.player.view.core.GLVisualizerRenderer;
import com.ikechi.studio.onwa.player.view.core.visuals.VisualStyle;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * Ultra-bright 3D dancing figure with cheerful daytime environment.
 * Maximum brightness and saturation for vibrant game-like aesthetic.
 */
public class DancingBaby3DStyle extends VisualStyle {

    // ---- SCALE - Larger dancer ----
    private static final float SCALE = 2.4f;

    // ---- Human Anatomy ----
    private static final float HEAD_HEIGHT = 0.12f * SCALE;
    private static final float HEAD_WIDTH = 0.10f * SCALE;
    private static final float NECK_LEN = 0.05f * SCALE;
    private static final float TORSO_HEIGHT = 0.28f * SCALE;
    private static final float SHOULDER_WIDTH = 0.24f * SCALE;
    private static final float HIP_WIDTH = 0.16f * SCALE;
    private static final float ARM_LENGTH = 0.26f * SCALE;
    private static final float LEG_LENGTH = 0.32f * SCALE;
    private static final float UPPER_ARM_RATIO = 0.55f;
    private static final float THIGH_RATIO = 0.52f;

    // ---- Joint Limits ----
    private static final float SHOULDER_MIN = -2.8f;
    private static final float SHOULDER_MAX = 0.4f;
    private static final float ELBOW_MAX = 2.6f;
    private static final float HIP_MIN = -1.0f;
    private static final float HIP_MAX = 1.2f;
    private static final float KNEE_MAX = 2.4f;

    // ---- ULTRA-BRIGHT DAYTIME COLORS ----
    // Sky - bright cyan to white gradient
    private static final float[] SKY_TOP = {0.2f, 0.85f, 1.0f, 1.0f};      // Bright cyan
    private static final float[] SKY_MID = {0.6f, 0.95f, 1.0f, 1.0f};      // Light cyan
    private static final float[] SKY_BOTTOM = {0.9f, 0.98f, 1.0f, 1.0f};   // Near white

    // Sun - bright yellow with glow
    private static final float[] SUN_CORE = {1.0f, 1.0f, 0.4f, 1.0f};
    private static final float[] SUN_GLOW = {1.0f, 0.9f, 0.5f, 0.7f};
    private static final float[] SUN_HALO = {1.0f, 0.95f, 0.7f, 0.4f};

    // Ground - bright grass green
    private static final float[] GRASS_LIGHT = {0.5f, 0.95f, 0.4f, 1.0f};
    private static final float[] GRASS_MID = {0.3f, 0.85f, 0.35f, 1.0f};
    private static final float[] GRASS_DARK = {0.2f, 0.7f, 0.25f, 1.0f};

    // Floor platform - bright reflective
    private static final float[] FLOOR_TOP = {0.9f, 0.95f, 1.0f, 1.0f};
    private static final float[] FLOOR_SIDE = {0.7f, 0.8f, 0.95f, 1.0f};
    private static final float[] FLOOR_GLOW = {0.5f, 0.9f, 1.0f, 0.6f};

    // Character colors - saturated and bright
    private static final float[] SKIN = {1.0f, 0.85f, 0.75f, 1.0f};        // Peach
    private static final float[] SKIN_SHADOW = {0.95f, 0.75f, 0.65f, 1.0f};
    private static final float[] SHIRT = {1.0f, 0.15f, 0.5f, 1.0f};      // Hot pink
    private static final float[] SHIRT_LIGHT = {1.0f, 0.5f, 0.7f, 1.0f};   // Light pink
    private static final float[] PANTS = {0.1f, 0.8f, 1.0f, 1.0f};       // Bright cyan
    private static final float[] PANTS_LIGHT = {0.4f, 0.9f, 1.0f, 1.0f};   // Light cyan
    private static final float[] HAIR = {0.35f, 0.2f, 0.15f, 1.0f};      // Brown
    private static final float[] SHOE = {1.0f, 0.9f, 0.1f, 1.0f};        // Bright yellow
    private static final float[] WHITE = {1.0f, 1.0f, 1.0f, 1.0f};
    private static final float[] BLACK = {0.1f, 0.1f, 0.1f, 1.0f};

    // Rainbow colors - extra bright
    private static final float[][] RAINBOW = {
        {1.0f, 0.2f, 0.2f, 1.0f},   // Red
        {1.0f, 0.6f, 0.2f, 1.0f},   // Orange
        {1.0f, 0.95f, 0.2f, 1.0f},  // Yellow
        {0.3f, 1.0f, 0.3f, 1.0f},   // Green
        {0.2f, 0.8f, 1.0f, 1.0f},   // Blue
        {0.6f, 0.3f, 1.0f, 1.0f},   // Purple
        {1.0f, 0.4f, 0.8f, 1.0f}    // Pink
    };

    // Flower colors
    private static final float[][] FLOWER_COLORS = {
        {1.0f, 0.3f, 0.5f, 1.0f},   // Pink
        {1.0f, 0.8f, 0.2f, 1.0f},   // Yellow
        {0.5f, 0.3f, 1.0f, 1.0f},   // Purple
        {1.0f, 0.5f, 0.2f, 1.0f},   // Orange
        {0.3f, 0.9f, 1.0f, 1.0f}    // Cyan
    };

    // ---- Animation State ----
    private float bass, mid, treble;
    private float bassSmoothed, midSmoothed, trebleSmoothed;
    private static final float SMOOTH = 0.82f;
    private static final float MOTION_SMOOTH = 0.12f;

    private float lShoulderX, lShoulderZ, rShoulderX, rShoulderZ;
    private float lElbow, rElbow;
    private float lHipX, lHipZ, rHipX, rHipZ;
    private float lKnee, rKnee;
    private float spineBend, spineTwist, bounceY, headBob;

    private float tLShoulderX, tLShoulderZ, tRShoulderX, tRShoulderZ;
    private float tLElbow, tRElbow;
    private float tLHipX, tLHipZ, tRHipX, tRHipZ;
    private float tLKnee, tRKnee;
    private float tSpineBend, tSpineTwist, tBounceY, tHeadBob;

    private int danceStyle = 0;
    private float styleTimer = 0f;
    private float dancePhase = 0f;
    private float energy = 0f;

    // Environment animation
    private float cloudOffset = 0f;
    private float sunRotation = 0f;
    private float flowerSway = 0f;
    private float butterflyPhase = 0f;
    private float sparklePhase = 0f;

    // ---- Geometry ----
    private int sphereVbo, sphereIbo, sphereCount;
    private int capsuleVbo, capsuleIbo, capsuleCount;
    private int boxVbo, boxIbo, boxCount;
    private int torusVbo, torusIbo, torusCount;
    private boolean initialized = false;

    private final float[] modelMatrix = new float[16];
    private final float[] mvpMatrix = new float[16];

    @Override
    public boolean usesUniformColor() { return true; }

    @Override
    public void init(int shader2D, int shader3D, int shader3DUniform,
                     int texturedShader2D, int texturedShader3D,
                     GLVisualizerRenderer renderer) {
        super.init(shader2D, shader3D, shader3DUniform,
				   texturedShader2D, texturedShader3D, renderer);
        releaseGeometry();
        buildGeometries();
        initialized = true;
    }

    private void buildGeometries() {
        buildSphere(24, 48);  // Higher res for smoother look
        buildCapsule(20, 40);
        buildBox();
        buildTorus(32, 64);
    }

    private void buildSphere(int stacks, int slices) {
        int vertCount = (stacks + 1) * (slices + 1);
        float[] verts = new float[vertCount * 3];
        short[] indices = new short[stacks * slices * 6];

        int vi = 0;
        for (int i = 0; i <= stacks; i++) {
            float theta = (float) (i * Math.PI / stacks);
            float sinT = (float) Math.sin(theta);
            float cosT = (float) Math.cos(theta);
            for (int j = 0; j <= slices; j++) {
                float phi = (float) (j * 2 * Math.PI / slices);
                verts[vi++] = (float) Math.cos(phi) * sinT;
                verts[vi++] = cosT;
                verts[vi++] = (float) Math.sin(phi) * sinT;
            }
        }

        int ii = 0;
        for (int i = 0; i < stacks; i++) {
            for (int j = 0; j < slices; j++) {
                short a = (short) (i * (slices + 1) + j);
                short b = (short) (a + 1);
                short c = (short) ((i + 1) * (slices + 1) + j);
                short d = (short) (c + 1);
                indices[ii++] = a; indices[ii++] = c; indices[ii++] = b;
                indices[ii++] = b; indices[ii++] = c; indices[ii++] = d;
            }
        }

        sphereCount = ii;
        int[] bufs = new int[2];
        GLES20.glGenBuffers(2, bufs, 0);
        sphereVbo = bufs[0];
        sphereIbo = bufs[1];
        uploadBuffer(sphereVbo, verts);
        uploadIndexBuffer(sphereIbo, indices);
    }

    private void buildCapsule(int stacks, int slices) {
        int rings = stacks + 2;
        int vertCount = rings * (slices + 1);
        float[] verts = new float[vertCount * 3];
        short[] indices = new short[(rings - 1) * slices * 6];

        int vi = 0;
        for (int i = 0; i < rings; i++) {
            float t = i / (float) (rings - 1);
            float y = (t - 0.5f) * 2f;
            float radius = (float) Math.cos((t - 0.5f) * Math.PI * 0.95f);
            if (radius < 0) radius = 0;

            for (int j = 0; j <= slices; j++) {
                float phi = (float) (j * 2 * Math.PI / slices);
                verts[vi++] = radius * (float) Math.cos(phi);
                verts[vi++] = y * 0.5f;
                verts[vi++] = radius * (float) Math.sin(phi);
            }
        }

        int ii = 0;
        for (int i = 0; i < rings - 1; i++) {
            for (int j = 0; j < slices; j++) {
                short a = (short) (i * (slices + 1) + j);
                short b = (short) (a + 1);
                short c = (short) ((i + 1) * (slices + 1) + j);
                short d = (short) (c + 1);
                indices[ii++] = a; indices[ii++] = c; indices[ii++] = b;
                indices[ii++] = b; indices[ii++] = c; indices[ii++] = d;
            }
        }

        capsuleCount = ii;
        int[] bufs = new int[2];
        GLES20.glGenBuffers(2, bufs, 0);
        capsuleVbo = bufs[0];
        capsuleIbo = bufs[1];
        uploadBuffer(capsuleVbo, verts);
        uploadIndexBuffer(capsuleIbo, indices);
    }

    private void buildBox() {
        float[] verts = {
            -0.5f, -0.5f, 0.5f,  0.5f, -0.5f, 0.5f,  0.5f, 0.5f, 0.5f,  -0.5f, 0.5f, 0.5f,
            -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f,  0.5f, -0.5f, -0.5f,
        };
        short[] indices = {
            0,1,2, 0,2,3, 4,5,6, 4,6,7, 0,3,5, 0,5,4, 1,7,6, 1,6,2, 3,2,6, 3,6,5, 0,4,7, 0,7,1
        };
        boxCount = indices.length;
        int[] bufs = new int[2];
        GLES20.glGenBuffers(2, bufs, 0);
        boxVbo = bufs[0];
        boxIbo = bufs[1];
        uploadBuffer(boxVbo, verts);
        uploadIndexBuffer(boxIbo, indices);
    }

    private void buildTorus(int rings, int sectors) {
        float majorR = 1.0f;
        float minorR = 0.3f;
        int vertCount = (rings + 1) * (sectors + 1);
        float[] verts = new float[vertCount * 3];
        short[] indices = new short[rings * sectors * 6];

        int vi = 0;
        for (int i = 0; i <= rings; i++) {
            float theta = (float) (i * 2 * Math.PI / rings);
            float cosT = (float) Math.cos(theta);
            float sinT = (float) Math.sin(theta);
            for (int j = 0; j <= sectors; j++) {
                float phi = (float) (j * 2 * Math.PI / sectors);
                float cosP = (float) Math.cos(phi);
                float sinP = (float) Math.sin(phi);
                verts[vi++] = (majorR + minorR * cosP) * cosT;
                verts[vi++] = minorR * sinP;
                verts[vi++] = (majorR + minorR * cosP) * sinT;
            }
        }

        int ii = 0;
        for (int i = 0; i < rings; i++) {
            for (int j = 0; j < sectors; j++) {
                short a = (short) (i * (sectors + 1) + j);
                short b = (short) (a + 1);
                short c = (short) ((i + 1) * (sectors + 1) + j);
                short d = (short) (c + 1);
                indices[ii++] = a; indices[ii++] = c; indices[ii++] = b;
                indices[ii++] = b; indices[ii++] = c; indices[ii++] = d;
            }
        }

        torusCount = ii;
        int[] bufs = new int[2];
        GLES20.glGenBuffers(2, bufs, 0);
        torusVbo = bufs[0];
        torusIbo = bufs[1];
        uploadBuffer(torusVbo, verts);
        uploadIndexBuffer(torusIbo, indices);
    }

    private void uploadBuffer(int vbo, float[] data) {
        FloatBuffer fb = ByteBuffer.allocateDirect(data.length * 4)
			.order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(data).flip();
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, fb.capacity() * 4, fb, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    private void uploadIndexBuffer(int ibo, short[] data) {
        ShortBuffer sb = ByteBuffer.allocateDirect(data.length * 2)
			.order(ByteOrder.nativeOrder()).asShortBuffer();
        sb.put(data).flip();
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo);
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, sb.capacity() * 2, sb, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    @Override
    public void update(float deltaTime, float[] fftBands, float beatIntensity, float totalTime) {
        this.totalTime = totalTime;
        if (fftBands == null || fftBands.length == 0) return;

        int n = fftBands.length;
        int bassEnd = n / 5;
        int midStart = bassEnd;
        int midEnd = n * 3 / 5;
        int trebleStart = midEnd;

        float bSum = 0, mSum = 0, tSum = 0;
        for (int i = 0; i < bassEnd; i++) bSum += fftBands[i];
        for (int i = midStart; i < midEnd; i++) mSum += fftBands[i];
        for (int i = trebleStart; i < n; i++) tSum += fftBands[i];

        bass = bSum / bassEnd;
        mid = mSum / (midEnd - midStart);
        treble = tSum / (n - trebleStart);

        bassSmoothed = bassSmoothed * SMOOTH + bass * (1 - SMOOTH);
        midSmoothed = midSmoothed * SMOOTH + mid * (1 - SMOOTH);
        trebleSmoothed = trebleSmoothed * SMOOTH + treble * (1 - SMOOTH);

        float sens = renderer.getSensitivity();
        energy = bassSmoothed * sens * (1 + beatIntensity * 0.6f);

        styleTimer += deltaTime;
        if (beatIntensity > 0.55f && styleTimer > 3.5f) {
            danceStyle = (danceStyle + 1) % 3;
            styleTimer = 0f;
        }

        float speed = 2.2f + energy * 0.6f + beatIntensity * 1.5f;
        dancePhase += deltaTime * speed;

        cloudOffset += deltaTime * 0.15f;
        sunRotation += deltaTime * 0.3f;
        flowerSway += deltaTime * 1.5f;
        butterflyPhase += deltaTime * 2.5f;
        sparklePhase += deltaTime * 4f;

        calculateDance(dancePhase, energy, midSmoothed * sens, trebleSmoothed * sens);
        smoothMotion();
    }

    private void calculateDance(float phase, float energy, float mid, float treble) {
        switch (danceStyle) {
            case 0: calculateFunky(phase, energy, mid); break;
            case 1: calculateSmooth(phase, energy, mid, treble); break;
            case 2: calculateEnergetic(phase, energy, mid); break;
        }
    }

    private void calculateFunky(float phase, float energy, float mid) {
        tBounceY = -0.38f * SCALE + (float) Math.sin(phase * 2f) * 0.08f * energy;
        tSpineBend = (float) Math.sin(phase * 0.6f) * 0.2f * energy;
        tSpineTwist = (float) Math.sin(phase * 0.8f) * 0.35f * energy;

        float armAmp = 0.8f + mid * 0.6f;
        tLShoulderX = (float) Math.sin(phase) * armAmp;
        tRShoulderX = (float) Math.sin(phase + Math.PI) * armAmp;
        tLShoulderZ = 0.4f + (float) Math.sin(phase * 1.5f) * 0.25f;
        tRShoulderZ = -0.4f - (float) Math.sin(phase * 1.5f) * 0.25f;

        tLElbow = 1.1f + (float) Math.abs(Math.sin(phase)) * 0.7f * energy;
        tRElbow = 1.1f + (float) Math.abs(Math.sin(phase + Math.PI)) * 0.7f * energy;

        float legAmp = 0.6f + energy * 0.5f;
        tLHipX = (float) Math.sin(phase + Math.PI * 0.5f) * legAmp;
        tRHipX = (float) Math.sin(phase + Math.PI * 1.5f) * legAmp;
        tLHipZ = 0.15f;
        tRHipZ = -0.15f;

        tLKnee = 0.35f + (float) Math.abs(Math.sin(phase + Math.PI * 0.5f)) * 0.85f * energy;
        tRKnee = 0.35f + (float) Math.abs(Math.sin(phase + Math.PI * 1.5f)) * 0.85f * energy;

        tHeadBob = (float) Math.sin(phase * 2.5f) * 0.03f * energy;
    }

    private void calculateSmooth(float phase, float energy, float mid, float treble) {
        tBounceY = -0.28f * SCALE + (float) Math.sin(phase) * 0.05f * energy;
        tSpineBend = (float) Math.sin(phase * 0.5f) * 0.1f;
        tSpineTwist = (float) Math.sin(phase * 0.7f) * 0.5f * energy;

        float flow = 0.6f + mid * 0.4f;
        tLShoulderX = (float) Math.sin(phase * 0.8f) * flow - 0.3f;
        tRShoulderX = (float) Math.sin(phase * 0.8f + Math.PI * 0.4f) * flow + 0.3f;
        tLShoulderZ = 0.5f;
        tRShoulderZ = -0.5f;

        tLElbow = 0.25f + (float) Math.sin(phase * 0.8f) * 0.2f;
        tRElbow = 0.25f + (float) Math.sin(phase * 0.8f + Math.PI * 0.4f) * 0.2f;

        tLHipX = (float) Math.sin(phase * 0.6f) * 0.4f * energy;
        tRHipX = (float) Math.sin(phase * 0.6f + Math.PI) * 0.4f * energy;
        tLHipZ = 0.25f;
        tRHipZ = -0.25f;

        tLKnee = 0.2f + (float) Math.abs(Math.sin(phase * 0.6f)) * 0.35f;
        tRKnee = 0.2f + (float) Math.abs(Math.sin(phase * 0.6f + Math.PI)) * 0.35f;

        tHeadBob = (float) Math.sin(phase * 0.8f) * 0.02f;
    }

    private void calculateEnergetic(float phase, float energy, float mid) {
        float snap = (float) Math.floor(phase * 2f) / 2f;
        float t = phase - snap;

        tBounceY = -0.35f * SCALE + (t < 0.08f ? 0.1f : 0f) * energy;
        tSpineBend = (float) Math.sin(snap * 1.5f) * 0.15f * energy;
        tSpineTwist = (float) Math.sin(snap * 2f) * 0.4f * energy;

        float armPos = (float) Math.sin(snap * 2f) > 0 ? 1.0f : -0.6f;
        tLShoulderX = armPos;
        tRShoulderX = -armPos;
        tLShoulderZ = 0.6f;
        tRShoulderZ = -0.6f;

        boolean bent = (float) Math.sin(snap * 3f) > 0;
        tLElbow = bent ? 2.2f : 0.2f;
        tRElbow = bent ? 0.2f : 2.2f;

        tLHipX = 0.4f * (float) Math.sin(snap * 2f) * energy;
        tRHipX = -0.4f * (float) Math.sin(snap * 2f) * energy;
        tLHipZ = 0.2f;
        tRHipZ = -0.2f;
        tLKnee = 0.6f;
        tRKnee = 0.6f;

        tHeadBob = 0f;
    }

    private void smoothMotion() {
        lShoulderX += (tLShoulderX - lShoulderX) * MOTION_SMOOTH;
        rShoulderX += (tRShoulderX - rShoulderX) * MOTION_SMOOTH;
        lShoulderZ += (tLShoulderZ - lShoulderZ) * MOTION_SMOOTH;
        rShoulderZ += (tRShoulderZ - rShoulderZ) * MOTION_SMOOTH;
        lElbow += (tLElbow - lElbow) * MOTION_SMOOTH;
        rElbow += (tRElbow - rElbow) * MOTION_SMOOTH;
        lHipX += (tLHipX - lHipX) * MOTION_SMOOTH;
        rHipX += (tRHipX - rHipX) * MOTION_SMOOTH;
        lHipZ += (tLHipZ - lHipZ) * MOTION_SMOOTH;
        rHipZ += (tRHipZ - rHipZ) * MOTION_SMOOTH;
        lKnee += (tLKnee - lKnee) * MOTION_SMOOTH;
        rKnee += (tRKnee - rKnee) * MOTION_SMOOTH;
        spineBend += (tSpineBend - spineBend) * MOTION_SMOOTH;
        spineTwist += (tSpineTwist - spineTwist) * MOTION_SMOOTH;
        bounceY += (tBounceY - bounceY) * MOTION_SMOOTH;
        headBob += (tHeadBob - headBob) * MOTION_SMOOTH;
    }

	@Override
	public void draw3D(float[] mvpMatrix, GLVisualizerRenderer renderer) {
		if (!initialized) return;

		// Extract projection matrix from the provided MVP (first 4x4 block)
		float[] projMatrix = new float[16];
		System.arraycopy(mvpMatrix, 0, projMatrix, 0, 16);

		// Create a fixed view matrix that always looks at center
		float[] fixedViewMatrix = new float[16];
		Matrix.setLookAtM(fixedViewMatrix, 0,
						  0f, 1f, 6f,     // Eye position - slightly elevated for better view
						  0f, 0.5f, 0f,   // Look at center (slightly below center to show full body)
						  0f, 1f, 0f);    // Up vector

		// Identity model matrix (no rotation)
		float[] identityModel = new float[16];
		Matrix.setIdentityM(identityModel, 0);

		// Combine: projection * fixedView * identityModel
		float[] temp = new float[16];
		float[] centeredMVP = new float[16];
		Matrix.multiplyMM(temp, 0, fixedViewMatrix, 0, identityModel, 0);
		Matrix.multiplyMM(centeredMVP, 0, projMatrix, 0, temp, 0);

		// Draw environment and dancer with centered MVP
		drawBrightEnvironment(centeredMVP);

		float hipY = bounceY;
		float spineX = spineBend * 0.12f * SCALE;
		float shoulderY = hipY + TORSO_HEIGHT;
		float shoulderX = spineX + spineBend * 0.18f * SCALE;
		float headY = shoulderY + NECK_LEN + HEAD_HEIGHT * 0.5f + headBob;

		drawLegs(centeredMVP, 0f, hipY, 0f);
		drawTorso(centeredMVP, spineX, hipY, shoulderX, shoulderY);
		drawHead(centeredMVP, shoulderX, headY);
		drawArms(centeredMVP, shoulderX, shoulderY, 0f);
	}

	@Override
	public void draw2D(float[] mvpMatrix, GLVisualizerRenderer renderer) {
		// For 2D mode, just use orthographic projection with identity rotation
		// This should rarely be called for 3D style, but implement for completeness

		// Extract projection from mvpMatrix (which should be orthographic in 2D mode)
		float[] projMatrix = new float[16];
		System.arraycopy(mvpMatrix, 0, projMatrix, 0, 16);

		// Create identity view and model matrices
		float[] identityView = new float[16];
		float[] identityModel = new float[16];
		Matrix.setIdentityM(identityView, 0);
		Matrix.setIdentityM(identityModel, 0);

		// Combine: projection * identityView * identityModel
		float[] temp = new float[16];
		float[] centeredMVP = new float[16];
		Matrix.multiplyMM(temp, 0, identityView, 0, identityModel, 0);
		Matrix.multiplyMM(centeredMVP, 0, projMatrix, 0, temp, 0);

		// For 2D mode, we need a different drawing approach
		// Since the 3D style is meant for 3D, we'll just return or draw a simplified version
		// You could either call the 3D drawing with orthographic projection or just return
		if (initialized) {
			drawBrightEnvironment(centeredMVP);
			// Draw simplified dancer for 2D mode if needed
		}
	}

    private void drawBrightEnvironment(float[] mvp) {
        // Large bright sky dome
        setModelMatrix(0f, 0f, 0f, 10f, 10f, 10f, 0f, 0f, 0f, 0f);
        multiplyAndDraw(mvp, modelMatrix, SKY_BOTTOM, sphereVbo, sphereIbo, sphereCount);

        // Sun with multiple glow layers
        float sunY = 3.5f + (float) Math.sin(totalTime * 0.15f) * 0.2f;
        float sunX = (float) Math.sin(totalTime * 0.1f) * 0.5f;

        // Outer halo
        setModelMatrix(sunX, sunY, -6f, 1.2f, 1.2f, 1.2f, sunRotation, 0f, 0f, 1f);
        multiplyAndDraw(mvp, modelMatrix, SUN_HALO, sphereVbo, sphereIbo, sphereCount);

        // Middle glow
        setModelMatrix(sunX, sunY, -6f, 0.8f, 0.8f, 0.8f, sunRotation * 1.5f, 0f, 0f, 1f);
        multiplyAndDraw(mvp, modelMatrix, SUN_GLOW, sphereVbo, sphereIbo, sphereCount);

        // Core
        setModelMatrix(sunX, sunY, -6f, 0.5f, 0.5f, 0.5f, 0f, 0f, 0f, 0f);
        multiplyAndDraw(mvp, modelMatrix, SUN_CORE, sphereVbo, sphereIbo, sphereCount);

        // Sun rays
        for (int i = 0; i < 12; i++) {
            float angle = i * 0.524f + sunRotation;
            float rx = sunX + (float) Math.cos(angle) * 0.9f;
            float ry = sunY + (float) Math.sin(angle) * 0.9f;
            setModelMatrix(rx, ry, -6f, 0.12f, 0.12f, 0.12f, 0f, 0f, 0f, 0f);
            multiplyAndDraw(mvp, modelMatrix, new float[]{1f, 1f, 0.6f, 0.8f}, sphereVbo, sphereIbo, sphereCount);
        }

        // Bright rainbow arc
        for (int i = 0; i < 7; i++) {
            float angle = -0.5f + i * 0.15f;
            float rx = (float) Math.sin(angle) * 4f;
            float ry = 2f + (float) Math.cos(angle) * 2.5f;
            float rz = -5f;
            setModelMatrix(rx, ry, rz, 0.25f, 0.25f, 0.1f, 0f, 0f, 0f, 0f);
            multiplyAndDraw(mvp, modelMatrix, RAINBOW[i], torusVbo, torusIbo, torusCount);
        }

        // Happy clouds with faces (puffs)
        for (int c = 0; c < 4; c++) {
            float cx = ((c * 2.5f + cloudOffset) % 12f) - 6f;
            float cy = 2.8f + (float) Math.sin(c + totalTime * 0.2f) * 0.2f;
            float cz = -3f - c * 0.8f;

            // Multiple white puffs per cloud
            for (int p = 0; p < 5; p++) {
                float px = cx + (p - 2) * 0.35f + (float) Math.sin(p + totalTime * 0.5f) * 0.1f;
                float py = cy + (float) Math.sin(p * 1.3f + totalTime) * 0.08f;
                float puffSize = 0.35f + (float) Math.sin(p * 2f) * 0.08f;
                setModelMatrix(px, py, cz, puffSize, puffSize * 0.7f, puffSize, 0f, 0f, 0f, 0f);
                multiplyAndDraw(mvp, modelMatrix, WHITE, sphereVbo, sphereIbo, sphereCount);
            }
        }

        // Green hills in background
        for (int h = -2; h <= 2; h++) {
            float hx = h * 2.5f;
            float hy = -0.8f * SCALE;
            float hz = -4f;
            float hillSize = 1.2f + (float) Math.sin(h) * 0.2f;
            setModelMatrix(hx, hy - hillSize * 0.3f, hz, hillSize, hillSize * 0.6f, hillSize * 0.8f, 0f, 0f, 0f, 0f);
            multiplyAndDraw(mvp, modelMatrix, GRASS_MID, sphereVbo, sphereIbo, sphereCount);
        }

        // Bright grass field
        for (int i = -4; i <= 4; i++) {
            for (int j = -3; j <= 3; j++) {
                float gx = i * 0.9f + (float) Math.sin(j + totalTime * 0.3f) * 0.1f;
                float gz = j * 0.7f - 2f;
                float gy = -0.65f * SCALE;

                // Grass tuft
                float sway = (float) Math.sin(flowerSway + i + j) * 0.05f;
                float[] grassColor = ((i + j) % 2 == 0) ? GRASS_LIGHT : GRASS_MID;
                setModelMatrix(gx + sway, gy, gz, 0.25f, 0.15f, 0.25f, sway * 50f, 0f, 0f, 1f);
                multiplyAndDraw(mvp, modelMatrix, grassColor, capsuleVbo, capsuleIbo, capsuleCount);
            }
        }

        // Dancing platform - bright and reflective
        setModelMatrix(0f, -0.72f * SCALE, 0f, 3f, 0.12f * SCALE, 2.5f, 0f, 0f, 0f, 0f);
        multiplyAndDraw(mvp, modelMatrix, FLOOR_TOP, boxVbo, boxIbo, boxCount);

        // Platform edge glow
        setModelMatrix(0f, -0.78f * SCALE, 0f, 3.1f, 0.06f * SCALE, 2.6f, 0f, 0f, 0f, 0f);
        multiplyAndDraw(mvp, modelMatrix, FLOOR_GLOW, boxVbo, boxIbo, boxCount);

        // Glowing tiles on platform
        for (int i = -2; i <= 2; i++) {
            for (int j = -1; j <= 1; j++) {
                float tx = i * 0.7f;
                float tz = j * 0.8f;
                float pulse = 0.6f + (float) Math.sin(sparklePhase + i * 2 + j) * 0.4f;
                float[] tileColor = {0.4f * pulse, 0.85f * pulse, 1.0f * pulse, 0.7f};
                setModelMatrix(tx, -0.68f * SCALE, tz, 0.3f, 0.02f * SCALE, 0.35f, 0f, 0f, 0f, 0f);
                multiplyAndDraw(mvp, modelMatrix, tileColor, boxVbo, boxIbo, boxCount);
            }
        }

        // Colorful flowers swaying
        for (int f = 0; f < 8; f++) {
            float angle = f * 0.785f;
            float fx = (float) Math.cos(angle) * 2.8f;
            float fz = (float) Math.sin(angle) * 2f - 1f;
            float fy = -0.55f * SCALE;
            float sway = (float) Math.sin(flowerSway + f) * 0.15f;

            // Stem
            setModelMatrix(fx + sway * 0.3f, fy + 0.15f * SCALE, fz, 0.04f, 0.15f * SCALE, 0.04f, sway * 30f, 0f, 0f, 1f);
            multiplyAndDraw(mvp, modelMatrix, GRASS_DARK, capsuleVbo, capsuleIbo, capsuleCount);

            // Petals
            float[] flowerColor = FLOWER_COLORS[f % 5];
            for (int p = 0; p < 5; p++) {
                float pa = p * 1.257f + sway;
                float px = fx + (float) Math.cos(pa) * 0.12f + sway * 0.5f;
                float py = fy + 0.28f * SCALE + (float) Math.sin(flowerSway * 2 + p) * 0.03f;
                float pz = fz + (float) Math.sin(pa) * 0.12f;
                setModelMatrix(px, py, pz, 0.08f, 0.08f, 0.08f, 0f, 0f, 0f, 0f);
                multiplyAndDraw(mvp, modelMatrix, flowerColor, sphereVbo, sphereIbo, sphereCount);
            }

            // Flower center
            setModelMatrix(fx + sway * 0.5f, fy + 0.28f * SCALE, fz, 0.06f, 0.06f, 0.06f, 0f, 0f, 0f, 0f);
            multiplyAndDraw(mvp, modelMatrix, new float[]{1f, 0.9f, 0.2f, 1f}, sphereVbo, sphereIbo, sphereCount);
        }

        // Butterflies flying around
        for (int b = 0; b < 5; b++) {
            float bt = butterflyPhase + b * 1.257f;
            float bx = (float) Math.sin(bt * 0.7f) * 2.5f;
            float by = 0.5f + (float) Math.sin(bt * 1.3f) * 0.6f;
            float bz = (float) Math.cos(bt * 0.5f) * 2f;
            float wingFlap = (float) Math.sin(bt * 15f) * 0.3f;

            float[] wingColor = RAINBOW[b % 7];

            // Left wing
            setModelMatrix(bx - 0.08f + wingFlap * 0.05f, by, bz, 0.08f, 0.06f, 0.02f, wingFlap * 30f, 0f, 0f, 1f);
            multiplyAndDraw(mvp, modelMatrix, wingColor, sphereVbo, sphereIbo, sphereCount);

            // Right wing
            setModelMatrix(bx + 0.08f - wingFlap * 0.05f, by, bz, 0.08f, 0.06f, 0.02f, -wingFlap * 30f, 0f, 0f, 1f);
            multiplyAndDraw(mvp, modelMatrix, wingColor, sphereVbo, sphereIbo, sphereCount);

            // Body
            setModelMatrix(bx, by, bz, 0.03f, 0.08f, 0.03f, 0f, 0f, 0f, 0f);
            multiplyAndDraw(mvp, modelMatrix, BLACK, capsuleVbo, capsuleIbo, capsuleCount);
        }

        // Floating sparkles/stars
        for (int s = 0; s < 15; s++) {
            float st = sparklePhase + s * 0.42f;
            float sx = (float) Math.sin(st * 0.8f + s) * 3f;
            float sy = 1f + (float) Math.cos(st * 0.6f + s * 0.5f) * 1.5f;
            float sz = (float) Math.sin(st + s * 0.3f) * 2f - 2f;
            float twinkle = 0.5f + (float) Math.sin(st * 3f) * 0.5f;
            float size = 0.04f + twinkle * 0.03f;

            float[] sparkleColor = {1f, 1f, 0.9f, twinkle};
            setModelMatrix(sx, sy, sz, size, size, size, st * 20f, 0f, 0f, 1f);
            multiplyAndDraw(mvp, modelMatrix, sparkleColor, sphereVbo, sphereIbo, sphereCount);
        }

        // Decorative trees with bright leaves
        for (int side = -1; side <= 1; side += 2) {
            float tx = side * 4f;
            float tz = -1.5f;

            // Trunk
            setModelMatrix(tx, -0.3f * SCALE, tz, 0.18f, 0.5f * SCALE, 0.18f, 0f, 0f, 0f, 0f);
            multiplyAndDraw(mvp, modelMatrix, new float[]{0.55f, 0.35f, 0.25f, 1f}, capsuleVbo, capsuleIbo, capsuleCount);

            // Leaves - bright green spheres
            for (int layer = 0; layer < 3; layer++) {
                float ly = 0.1f * SCALE + layer * 0.28f * SCALE;
                float ls = (0.5f - layer * 0.12f) * SCALE;
                float[] leafColor = (layer == 0) ? GRASS_LIGHT : (layer == 1) ? GRASS_MID : GRASS_DARK;

                // Main foliage
                setModelMatrix(tx, ly, tz, ls, ls * 0.9f, ls, 0f, 0f, 0f, 0f);
                multiplyAndDraw(mvp, modelMatrix, leafColor, sphereVbo, sphereIbo, sphereCount);

                // Extra puffs for fullness
                for (int p = 0; p < 4; p++) {
                    float pa = p * 1.57f + totalTime * 0.2f;
                    float px = tx + (float) Math.cos(pa) * ls * 0.4f;
                    float pz = tz + (float) Math.sin(pa) * ls * 0.4f;
                    float py = ly + (float) Math.sin(p + totalTime) * 0.05f * SCALE;
                    setModelMatrix(px, py, pz, ls * 0.5f, ls * 0.5f, ls * 0.5f, 0f, 0f, 0f, 0f);
                    multiplyAndDraw(mvp, modelMatrix, GRASS_LIGHT, sphereVbo, sphereIbo, sphereCount);
                }
            }
        }
    }

    private void drawHead(float[] mvp, float x, float y) {
        float z = 0f;

        // Neck
        setModelMatrix(x, y - HEAD_HEIGHT * 0.4f, z, 0.05f * SCALE, 0.08f * SCALE, 0.05f * SCALE, 0f, 0f, 0f, 0f);
        multiplyAndDraw(mvp, modelMatrix, SKIN, capsuleVbo, capsuleIbo, capsuleCount);

        // Face
        setModelMatrix(x, y, z, HEAD_WIDTH * 0.5f, HEAD_HEIGHT * 0.55f, HEAD_WIDTH * 0.5f, 0f, 0f, 0f, 0f);
        multiplyAndDraw(mvp, modelMatrix, SKIN, sphereVbo, sphereIbo, sphereCount);

        // Hair - styled with volume
        setModelMatrix(x, y + HEAD_HEIGHT * 0.35f, z, HEAD_WIDTH * 0.6f, HEAD_HEIGHT * 0.4f, HEAD_WIDTH * 0.65f, 0f, 0f, 0f, 0f);
        multiplyAndDraw(mvp, modelMatrix, HAIR, sphereVbo, sphereIbo, sphereCount);

        // Side bangs
        setModelMatrix(x - HEAD_WIDTH * 0.35f, y + HEAD_HEIGHT * 0.15f, z + HEAD_WIDTH * 0.2f, 
					   0.06f * SCALE, 0.18f * SCALE, 0.06f * SCALE, 0.4f, 0f, 0f, 1f);
        multiplyAndDraw(mvp, modelMatrix, HAIR, capsuleVbo, capsuleIbo, capsuleCount);
        setModelMatrix(x + HEAD_WIDTH * 0.35f, y + HEAD_HEIGHT * 0.15f, z + HEAD_WIDTH * 0.2f, 
					   0.06f * SCALE, 0.18f * SCALE, 0.06f * SCALE, -0.4f, 0f, 0f, 1f);
        multiplyAndDraw(mvp, modelMatrix, HAIR, capsuleVbo, capsuleIbo, capsuleCount);

        // Eyes - large and expressive
        float eyeY = y + HEAD_HEIGHT * 0.08f;
        float eyeZ = z + HEAD_WIDTH * 0.42f;
        float eyeX = HEAD_WIDTH * 0.22f;

        // Whites
        setModelMatrix(x - eyeX, eyeY, eyeZ, 0.035f * SCALE, 0.04f * SCALE, 0.015f * SCALE, 0f, 0f, 0f, 0f);
        multiplyAndDraw(mvp, modelMatrix, WHITE, sphereVbo, sphereIbo, sphereCount);
        setModelMatrix(x + eyeX, eyeY, eyeZ, 0.035f * SCALE, 0.04f * SCALE, 0.015f * SCALE, 0f, 0f, 0f, 0f);
        multiplyAndDraw(mvp, modelMatrix, WHITE, sphereVbo, sphereIbo, sphereCount);

        // Irises - bright blue
        float lookX = spineTwist * 0.015f * SCALE;
        float[] irisColor = {0.2f, 0.7f, 1.0f, 1f};
        setModelMatrix(x - eyeX + lookX, eyeY, eyeZ + 0.008f * SCALE, 0.018f * SCALE, 0.022f * SCALE, 0.008f * SCALE, 0f, 0f, 0f, 0f);
        multiplyAndDraw(mvp, modelMatrix, irisColor, sphereVbo, sphereIbo, sphereCount);
        setModelMatrix(x + eyeX + lookX, eyeY, eyeZ + 0.008f * SCALE, 0.018f * SCALE, 0.022f * SCALE, 0.008f * SCALE, 0f, 0f, 0f, 0f);
        multiplyAndDraw(mvp, modelMatrix, irisColor, sphereVbo, sphereIbo, sphereCount);

        // Pupils
        setModelMatrix(x - eyeX + lookX, eyeY, eyeZ + 0.012f * SCALE, 0.01f * SCALE, 0.01f * SCALE, 0.005f * SCALE, 0f, 0f, 0f, 0f);
        multiplyAndDraw(mvp, modelMatrix, BLACK, sphereVbo, sphereIbo, sphereCount);
        setModelMatrix(x + eyeX + lookX, eyeY, eyeZ + 0.012f * SCALE, 0.01f * SCALE, 0.01f * SCALE, 0.005f * SCALE, 0f, 0f, 0f, 0f);
        multiplyAndDraw(mvp, modelMatrix, BLACK, sphereVbo, sphereIbo, sphereCount);

        // Highlights - shiny
        setModelMatrix(x - eyeX + lookX - 0.008f * SCALE, eyeY + 0.01f * SCALE, eyeZ + 0.015f * SCALE, 
					   0.005f * SCALE, 0.005f * SCALE, 0.003f * SCALE, 0f, 0f, 0f, 0f);
        multiplyAndDraw(mvp, modelMatrix, WHITE, sphereVbo, sphereIbo, sphereCount);
        setModelMatrix(x + eyeX + lookX - 0.008f * SCALE, eyeY + 0.01f * SCALE, eyeZ + 0.015f * SCALE, 
					   0.005f * SCALE, 0.005f * SCALE, 0.003f * SCALE, 0f, 0f, 0f, 0f);
        multiplyAndDraw(mvp, modelMatrix, WHITE, sphereVbo, sphereIbo, sphereCount);

        // Happy smile
        float mouthY = y - HEAD_HEIGHT * 0.25f;
        float smile = 0.025f * SCALE + (bassSmoothed + midSmoothed) * 0.015f * SCALE;
        for (int i = -2; i <= 2; i++) {
            float t = i * 0.2f;
            float my = mouthY - (float) Math.cos(t * Math.PI * 0.5f) * smile + smile;
            float mx = x + t * HEAD_WIDTH * 0.5f;
            setModelMatrix(mx, my, eyeZ, 0.012f * SCALE, 0.008f * SCALE, 0.006f * SCALE, 0f, 0f, 0f, 0f);
            float[] lipColor = {1f, 0.4f, 0.5f, 1f};
            multiplyAndDraw(mvp, modelMatrix, lipColor, sphereVbo, sphereIbo, sphereCount);
        }

        // Blush on cheeks
        setModelMatrix(x - HEAD_WIDTH * 0.4f, y - HEAD_HEIGHT * 0.05f, eyeZ, 0.025f * SCALE, 0.015f * SCALE, 0.008f * SCALE, 0f, 0f, 0f, 0f);
        multiplyAndDraw(mvp, modelMatrix, new float[]{1f, 0.6f, 0.6f, 0.5f}, sphereVbo, sphereIbo, sphereCount);
        setModelMatrix(x + HEAD_WIDTH * 0.4f, y - HEAD_HEIGHT * 0.05f, eyeZ, 0.025f * SCALE, 0.015f * SCALE, 0.008f * SCALE, 0f, 0f, 0f, 0f);
        multiplyAndDraw(mvp, modelMatrix, new float[]{1f, 0.6f, 0.6f, 0.5f}, sphereVbo, sphereIbo, sphereCount);
    }

    private void drawTorso(float[] mvp, float hipX, float hipY, float shoulderX, float shoulderY) {
        // Hips
        setModelMatrix(hipX, hipY + 0.05f * SCALE, 0f, HIP_WIDTH * 0.55f, 0.06f * SCALE, HIP_WIDTH * 0.4f, 0f, 0f, 0f, 0f);
        multiplyAndDraw(mvp, modelMatrix, PANTS, sphereVbo, sphereIbo, sphereCount);

        // Lower torso
        float midY = (hipY + shoulderY) * 0.5f;
        setModelMatrix((hipX + shoulderX) * 0.5f, midY - 0.02f * SCALE, 0f,
					   HIP_WIDTH * 0.5f, TORSO_HEIGHT * 0.28f, HIP_WIDTH * 0.35f, spineTwist * 10f, 0f, 1f, 0f);
        multiplyAndDraw(mvp, modelMatrix, PANTS, capsuleVbo, capsuleIbo, capsuleCount);

        // Upper torso
        setModelMatrix(shoulderX, midY + 0.1f * SCALE, 0f,
					   SHOULDER_WIDTH * 0.55f, TORSO_HEIGHT * 0.32f, SHOULDER_WIDTH * 0.4f, spineTwist * 10f, 0f, 1f, 0f);
        multiplyAndDraw(mvp, modelMatrix, SHIRT, capsuleVbo, capsuleIbo, capsuleCount);

        // White stripe detail
        setModelMatrix(shoulderX, midY + 0.1f * SCALE, 0.06f * SCALE,
					   SHOULDER_WIDTH * 0.56f, TORSO_HEIGHT * 0.05f, SHOULDER_WIDTH * 0.41f, spineTwist * 10f, 0f, 1f, 0f);
        multiplyAndDraw(mvp, modelMatrix, WHITE, capsuleVbo, capsuleIbo, capsuleCount);
    }

    private void drawArms(float[] mvp, float shoulderX, float shoulderY, float shoulderZ) {
        drawLimb(mvp, true, shoulderX - SHOULDER_WIDTH * 0.45f, shoulderY - 0.03f * SCALE, shoulderZ,
				 lShoulderX, lShoulderZ, lElbow, ARM_LENGTH * UPPER_ARM_RATIO, ARM_LENGTH * (1 - UPPER_ARM_RATIO),
				 SHIRT, SKIN, false);
        drawLimb(mvp, false, shoulderX + SHOULDER_WIDTH * 0.45f, shoulderY - 0.03f * SCALE, shoulderZ,
				 rShoulderX, rShoulderZ, rElbow, ARM_LENGTH * UPPER_ARM_RATIO, ARM_LENGTH * (1 - UPPER_ARM_RATIO),
				 SHIRT, SKIN, false);
    }

    private void drawLegs(float[] mvp, float hipX, float hipY, float hipZ) {
        drawLimb(mvp, true, hipX - HIP_WIDTH * 0.35f, hipY - 0.02f * SCALE, hipZ,
				 lHipX, lHipZ, lKnee, LEG_LENGTH * THIGH_RATIO, LEG_LENGTH * (1 - THIGH_RATIO),
				 PANTS, SKIN_SHADOW, true);
        drawLimb(mvp, false, hipX + HIP_WIDTH * 0.35f, hipY - 0.02f * SCALE, hipZ,
				 rHipX, rHipZ, rKnee, LEG_LENGTH * THIGH_RATIO, LEG_LENGTH * (1 - THIGH_RATIO),
				 PANTS, SKIN_SHADOW, true);
    }

    private void drawLimb(float[] mvp, boolean isLeft, float startX, float startY, float startZ,
                          float jointXAng, float jointZAng, float bendAng,
                          float upperLen, float lowerLen,
                          float[] upperColor, float[] endColor, boolean isLeg) {
        float side = isLeft ? -1 : 1;

        jointXAng = clamp(jointXAng, isLeg ? HIP_MIN : SHOULDER_MIN, isLeg ? HIP_MAX : SHOULDER_MAX);
        bendAng = clamp(bendAng, 0, isLeg ? KNEE_MAX : ELBOW_MAX);

        float jointX = startX + (float) Math.sin(jointXAng) * upperLen * 0.5f;
        float jointY = startY - (float) Math.cos(jointXAng) * upperLen * 0.5f;
        float jointZ = startZ + jointZAng * 0.15f * SCALE;

        float upperRot = (float) Math.toDegrees(jointXAng);
        float thick = isLeg ? 0.065f * SCALE : 0.055f * SCALE;
        setModelMatrix((startX + jointX) * 0.5f, (startY + jointY) * 0.5f, (startZ + jointZ) * 0.5f,
					   thick, upperLen * 0.5f, thick * 0.85f, upperRot, 0f, 0f, 1f);
        multiplyAndDraw(mvp, modelMatrix, upperColor, capsuleVbo, capsuleIbo, capsuleCount);

        float jointEndX = startX + (float) Math.sin(jointXAng) * upperLen;
        float jointEndY = startY - (float) Math.cos(jointXAng) * upperLen;
        setModelMatrix(jointEndX, jointEndY, jointZ, thick * 0.9f, thick * 0.9f, thick * 0.9f, 0f, 0f, 0f, 0f);
        multiplyAndDraw(mvp, modelMatrix, isLeg ? PANTS_LIGHT : SHIRT_LIGHT, sphereVbo, sphereIbo, sphereCount);

        float lowerAng = jointXAng + bendAng * side;
        float lowerEndX = jointEndX + (float) Math.sin(lowerAng) * lowerLen;
        float lowerEndY = jointEndY - (float) Math.cos(lowerAng) * lowerLen;
        float lowerRot = (float) Math.toDegrees(lowerAng);
        float lowerThick = thick * 0.75f;

        setModelMatrix((jointEndX + lowerEndX) * 0.5f, (jointEndY + lowerEndY) * 0.5f, jointZ,
					   lowerThick, lowerLen * 0.5f, lowerThick * 0.85f, lowerRot, 0f, 0f, 1f);
        multiplyAndDraw(mvp, modelMatrix, upperColor, capsuleVbo, capsuleIbo, capsuleCount);

        if (isLeg) {
            // Bright yellow shoe
            setModelMatrix(lowerEndX, lowerEndY - 0.03f * SCALE, jointZ,
						   0.07f * SCALE, 0.05f * SCALE, 0.12f * SCALE, lowerRot * 0.3f, 0f, 1f, 0f);
            multiplyAndDraw(mvp, modelMatrix, SHOE, sphereVbo, sphereIbo, sphereCount);
        } else {
            // Hand
            setModelMatrix(lowerEndX, lowerEndY, jointZ, 0.045f * SCALE, 0.04f * SCALE, 0.05f * SCALE, 0f, 0f, 0f, 0f);
            multiplyAndDraw(mvp, modelMatrix, endColor, sphereVbo, sphereIbo, sphereCount);
        }
    }

    private void setModelMatrix(float tx, float ty, float tz,
                                float sx, float sy, float sz,
                                float rotDeg, float rx, float ry, float rz) {
        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0, tx, ty, tz);
        if (rotDeg != 0f) Matrix.rotateM(modelMatrix, 0, rotDeg, rx, ry, rz);
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz);
    }

    private void multiplyAndDraw(float[] mvp, float[] model, float[] color,
								 int vbo, int ibo, int count) {
        Matrix.multiplyMM(mvpMatrix, 0, mvp, 0, model, 0);
        drawMesh(vbo, ibo, count, color);
    }

    private void drawMesh(int vbo, int ibo, int indexCount, float[] color) {
        int prog = shaderProgram3DUniform;
        GLES20.glUseProgram(prog);

        int mvpLoc = GLES20.glGetUniformLocation(prog, "uMVPMatrix");
        int colorLoc = GLES20.glGetUniformLocation(prog, "uColor");
        int modelLoc = GLES20.glGetUniformLocation(prog, "uModel");

        if (mvpLoc != -1) GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mvpMatrix, 0);
        if (colorLoc != -1) GLES20.glUniform4fv(colorLoc, 1, color, 0);
        if (modelLoc != -1) GLES20.glUniformMatrix4fv(modelLoc, 1, false, modelMatrix, 0);

        int posLoc = GLES20.glGetAttribLocation(prog, "vPosition");
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        if (posLoc != -1) {
            GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, 0, 0);
            GLES20.glEnableVertexAttribArray(posLoc);
        }

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        if (posLoc != -1) GLES20.glDisableVertexAttribArray(posLoc);
    }

    private float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    private void releaseGeometry() {
        if (initialized) {
            GLES20.glDeleteBuffers(2, new int[]{sphereVbo, sphereIbo}, 0);
            GLES20.glDeleteBuffers(2, new int[]{capsuleVbo, capsuleIbo}, 0);
            GLES20.glDeleteBuffers(2, new int[]{boxVbo, boxIbo}, 0);
            GLES20.glDeleteBuffers(2, new int[]{torusVbo, torusIbo}, 0);
            initialized = false;
        }
    }

    @Override
    public void release() {
        releaseGeometry();
    }
}

