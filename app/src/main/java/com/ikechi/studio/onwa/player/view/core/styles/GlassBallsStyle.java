
package com.ikechi.studio.onwa.player.view.core.styles;

import android.opengl.GLES20;
import android.opengl.Matrix;

import com.ikechi.studio.onwa.player.view.core.GLVisualizerRenderer;
import com.ikechi.studio.onwa.player.view.core.visuals.VisualStyle;
import com.ikechi.studio.onwa.player.view.core.visuals.ShapeRenderer;
import com.ikechi.studio.onwa.player.view.core.objects.GlassBall;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Enhanced 3D glass balls with:
 * - Realistic physics (collisions, gravity, friction)
 * - Phong shading for glass-like reflections
 * - Beat-reactive pulsing and glow
 * - Dynamic lighting for specular highlights
 * - Optimized rendering for performance
 */
public class GlassBallsStyle extends VisualStyle {

    // --- Constants ---
    private static final int SPHERE_STACKS = 16;
    private static final int SPHERE_SLICES = 32;
    private static final float GRAVITY = -0.098f; // Earth-like gravity (scaled down)
    private static final float FRICTION = 0.98f;   // Air resistance
    private static final float RESTITUTION = 0.8f; // Bounciness (0 = no bounce, 1 = perfect bounce)
    private static final float WALL_RESTITUTION = 0.9f;
    private static final float BALL_RADIUS = 0.08f;
    private static final float MIN_DISTANCE = BALL_RADIUS * 2.01f; // Slightly more than sum of radii
    private static final int MAX_COLLISION_ITERATIONS = 3; // Prevent infinite collision loops

    // --- Sphere Geometry ---
    private int sphereVboId;   // Positions
    private int sphereNboId;   // Normals
    private int sphereIboId;   // Indices
    private int sphereIndexCount;
    private boolean initialized = false;

    // --- Scene Bounds (for collision with walls) ---
    private static final float WALL_SIZE = 1.0f; // World bounds: -1 to 1 in X, Y, Z
    private static final float[] WALL_MIN = {-WALL_SIZE, -WALL_SIZE, -WALL_SIZE};
    private static final float[] WALL_MAX = {WALL_SIZE, WALL_SIZE, WALL_SIZE};

    // --- Lighting (for Phong shading) ---
    private float[] lightPos = {0.5f, 0.5f, 1.0f}; // Light position (moves over time)
    private float[] lightColor = {1.0f, 1.0f, 1.0f, 1.0f}; // White light
    private float[] ambientColor = {0.1f, 0.1f, 0.15f, 1.0f}; // Dark blue ambient
    private float[] specularColor = {1.0f, 1.0f, 1.0f, 1.0f}; // White specular
    private float shininess = 100.0f; // High shininess for glass-like reflections

    // --- Glass Balls ---
    private final List<GlassBall> balls = new ArrayList<>();
    private final Random random = new Random();

    @Override
    public boolean usesUniformColor() {
        return true;
    }

    @Override
    public void init(int shader2D, int shader3D, int shader3DUniform,
                     int texturedShader2D, int texturedShader3D,
                     GLVisualizerRenderer renderer) {
        super.init(shader2D, shader3D, shader3DUniform,
                   texturedShader2D, texturedShader3D, renderer);
        releaseBuffers();
        buildSphereBuffers();
        resetBalls(renderer.getGlassBallCount());
    }

    // --- Sphere Geometry ---
    private void buildSphereBuffers() {
        int numVerts = (SPHERE_STACKS + 1) * (SPHERE_SLICES + 1);
        float[] verts = new float[numVerts * 3];
        float[] norms = new float[numVerts * 3];
        short[] indices = new short[SPHERE_STACKS * SPHERE_SLICES * 6];

        int vi = 0;
        for (int i = 0; i <= SPHERE_STACKS; i++) {
            float theta = (float) (i * Math.PI / SPHERE_STACKS);
            float sinTheta = (float) Math.sin(theta);
            float cosTheta = (float) Math.cos(theta);
            for (int j = 0; j <= SPHERE_SLICES; j++) {
                float phi = (float) (j * 2 * Math.PI / SPHERE_SLICES);
                float x = (float) Math.cos(phi) * sinTheta;
                float y = cosTheta;
                float z = (float) Math.sin(phi) * sinTheta;
                verts[vi * 3] = x;
                verts[vi * 3 + 1] = y;
                verts[vi * 3 + 2] = z;
                norms[vi * 3] = x;
                norms[vi * 3 + 1] = y;
                norms[vi * 3 + 2] = z;
                vi++;
            }
        }

        int ii = 0;
        for (int i = 0; i < SPHERE_STACKS; i++) {
            for (int j = 0; j < SPHERE_SLICES; j++) {
                int f = i * (SPHERE_SLICES + 1) + j;
                int s = f + (SPHERE_SLICES + 1);
                indices[ii++] = (short) f;
                indices[ii++] = (short) s;
                indices[ii++] = (short) (f + 1);
                indices[ii++] = (short) s;
                indices[ii++] = (short) (s + 1);
                indices[ii++] = (short) (f + 1);
            }
        }
        sphereIndexCount = ii;

        FloatBuffer fv = nativeFB(verts);
        FloatBuffer fn = nativeFB(norms);
        ShortBuffer si = nativeSB(indices);

        int[] bufs = new int[3];
        GLES20.glGenBuffers(3, bufs, 0);
        sphereVboId = bufs[0];
        sphereNboId = bufs[1];
        sphereIboId = bufs[2];

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, sphereVboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, verts.length * 4, fv, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, sphereNboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, norms.length * 4, fn, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, sphereIboId);
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, indices.length * 2, si, GLES20.GL_STATIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

        initialized = true;
    }

    // --- Ball Initialization ---
    private void resetBalls(int count) {
        balls.clear();
        for (int i = 0; i < count; i++) {
            GlassBall ball = new GlassBall();
            // Random initial position within bounds
            ball.x = random.nextFloat() * 2f - 1f;
            ball.y = random.nextFloat() * 2f - 1f;
            ball.z = random.nextFloat() * 2f - 1f;
            // Random initial velocity
            ball.vx = (random.nextFloat() - 0.5f) * 0.05f;
            ball.vy = (random.nextFloat() - 0.5f) * 0.05f;
            ball.vz = (random.nextFloat() - 0.5f) * 0.05f;
            // Random color (semi-transparent)
            ball.color = new float[]{
                random.nextFloat() * 0.5f + 0.3f, // R
                random.nextFloat() * 0.5f + 0.3f, // G
                random.nextFloat() * 0.5f + 0.5f, // B (bluish tint)
                0.7f + random.nextFloat() * 0.3f   // Alpha (semi-transparent)
            };
            ball.radius = BALL_RADIUS;
            balls.add(ball);
        }
    }

    // --- Physics Update ---
    @Override
    public void update(float deltaTime, float[] fftBands, float beatIntensity, float totalTime) {
        this.totalTime = totalTime;

        // Update light position (moves in a circle over time)
        lightPos[0] = (float) Math.cos(totalTime * 0.3f) * 0.8f;
        lightPos[1] = (float) Math.sin(totalTime * 0.2f) * 0.6f;
        lightPos[2] = 0.5f + (float) Math.sin(totalTime * 0.4f) * 0.3f;

        // Update each ball
        for (GlassBall ball : balls) {
            // Apply gravity
            ball.vy += GRAVITY * deltaTime;

            // Apply friction (air resistance)
            ball.vx *= FRICTION;
            ball.vy *= FRICTION;
            ball.vz *= FRICTION;

            // Store previous position for collision response
            float prevX = ball.x;
            float prevY = ball.y;
            float prevZ = ball.z;

            // Update position
            ball.x += ball.vx * deltaTime;
            ball.y += ball.vy * deltaTime;
            ball.z += ball.vz * deltaTime;

            // Collision with walls
            handleWallCollision(ball);

            // Beat-reactive pulsing
            if (beatIntensity > 0.5f) {
                ball.radius = BALL_RADIUS * (1.0f + beatIntensity * 0.2f);
            } else {
                ball.radius = BALL_RADIUS;
            }
        }

        // Handle collisions between balls
        for (int i = 0; i < balls.size(); i++) {
            for (int j = i + 1; j < balls.size(); j++) {
                handleBallCollision(balls.get(i), balls.get(j));
            }
        }
    }

    // --- Wall Collision ---
    private void handleWallCollision(GlassBall ball) {
        for (int axis = 0; axis < 3; axis++) {
            if (ball.x + ball.radius > WALL_MAX[axis] && ball.vx > 0) {
                ball.x = WALL_MAX[axis] - ball.radius;
                ball.vx = -ball.vx * WALL_RESTITUTION;
            } else if (ball.x - ball.radius < WALL_MIN[axis] && ball.vx < 0) {
                ball.x = WALL_MIN[axis] + ball.radius;
                ball.vx = -ball.vx * WALL_RESTITUTION;
            }

            if (ball.y + ball.radius > WALL_MAX[axis] && ball.vy > 0) {
                ball.y = WALL_MAX[axis] - ball.radius;
                ball.vy = -ball.vy * WALL_RESTITUTION;
            } else if (ball.y - ball.radius < WALL_MIN[axis] && ball.vy < 0) {
                ball.y = WALL_MIN[axis] + ball.radius;
                ball.vy = -ball.vy * WALL_RESTITUTION;
            }

            if (ball.z + ball.radius > WALL_MAX[axis] && ball.vz > 0) {
                ball.z = WALL_MAX[axis] - ball.radius;
                ball.vz = -ball.vz * WALL_RESTITUTION;
            } else if (ball.z - ball.radius < WALL_MIN[axis] && ball.vz < 0) {
                ball.z = WALL_MIN[axis] + ball.radius;
                ball.vz = -ball.vz * WALL_RESTITUTION;
            }
        }
    }

    // --- Ball-to-Ball Collision ---
    private void handleBallCollision(GlassBall ball1, GlassBall ball2) {
        float dx = ball2.x - ball1.x;
        float dy = ball2.y - ball1.y;
        float dz = ball2.z - ball1.z;
        float distanceSquared = dx * dx + dy * dy + dz * dz;
        float minDistance = ball1.radius + ball2.radius;

        if (distanceSquared < minDistance * minDistance) {
            // Collision detected
            float distance = (float) Math.sqrt(distanceSquared);
            float overlap = minDistance - distance;

            // Normalize the collision normal
            float nx = dx / distance;
            float ny = dy / distance;
            float nz = dz / distance;

            // Move balls apart to prevent sticking
            float moveX = nx * overlap * 0.5f;
            float moveY = ny * overlap * 0.5f;
            float moveZ = nz * overlap * 0.5f;
            ball1.x -= moveX;
            ball1.y -= moveY;
            ball1.z -= moveZ;
            ball2.x += moveX;
            ball2.y += moveY;
            ball2.z += moveZ;

            // Calculate relative velocity
            float rvx = ball2.vx - ball1.vx;
            float rvy = ball2.vy - ball1.vy;
            float rvz = ball2.vz - ball1.vz;

            // Calculate relative velocity along the collision normal
            float velocityAlongNormal = rvx * nx + rvy * ny + rvz * nz;

            // Do not resolve if balls are moving away
            if (velocityAlongNormal > 0) return;

            // Calculate restitution (bounciness)
            float e = RESTITUTION;

            // Calculate impulse scalar
            float j = -(1 + e) * velocityAlongNormal;
            float jx = j * nx;
            float jy = j * ny;
            float jz = j * nz;

            // Apply impulse
            ball1.vx -= jx / ball1.radius;
            ball1.vy -= jy / ball1.radius;
            ball1.vz -= jz / ball1.radius;
            ball2.vx += jx / ball2.radius;
            ball2.vy += jy / ball2.radius;
            ball2.vz += jz / ball2.radius;
        }
    }

    // --- 2D Drawing (Fallback) ---
    @Override
    public void draw2D(float[] mvpMatrix, GLVisualizerRenderer renderer) {
        float beat = renderer.getBeatIntensity();
        for (GlassBall ball : balls) {
            // Draw main circle
            ShapeRenderer.drawCircle(
                shaderProgram2D, mvpMatrix,
                ball.x, ball.y,
                ball.radius + beat * 0.015f,
                ball.color, 32
            );

            // Draw specular highlight (simulated)
            float[] highlight = {
                1.0f, 1.0f, 1.0f,
                0.28f + beat * 0.2f // Alpha pulses with beat
            };
            ShapeRenderer.drawCircle(
                shaderProgram2D, mvpMatrix,
                ball.x - ball.radius * 0.28f,
                ball.y + ball.radius * 0.28f,
                ball.radius * (0.18f + beat * 0.05f),
                highlight, 16
            );
        }
    }

    // --- 3D Drawing (Main) ---
    @Override
    public void draw3D(float[] mvpMatrix, GLVisualizerRenderer renderer) {
        if (!initialized) return;

        int prog = shaderProgram3DUniform;
        int posH = GLES20.glGetAttribLocation(prog, "vPosition");
        int normH = GLES20.glGetAttribLocation(prog, "aNormal");
        int mvpH = GLES20.glGetUniformLocation(prog, "uMVPMatrix");
        int modelH = GLES20.glGetUniformLocation(prog, "uModel");
        int colorH = GLES20.glGetUniformLocation(prog, "uColor");
        int lightPosH = GLES20.glGetUniformLocation(prog, "uLightPos");
        int lightColorH = GLES20.glGetUniformLocation(prog, "uLightColor");
        int ambientH = GLES20.glGetUniformLocation(prog, "uAmbientColor");
        int specularH = GLES20.glGetUniformLocation(prog, "uSpecularColor");
        int shininessH = GLES20.glGetUniformLocation(prog, "uShininess");

        // Bind sphere geometry (shared for all balls)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, sphereVboId);
        if (posH != -1) {
            GLES20.glVertexAttribPointer(posH, 3, GLES20.GL_FLOAT, false, 0, 0);
            GLES20.glEnableVertexAttribArray(posH);
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, sphereNboId);
        if (normH != -1) {
            GLES20.glVertexAttribPointer(normH, 3, GLES20.GL_FLOAT, false, 0, 0);
            GLES20.glEnableVertexAttribArray(normH);
        }
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, sphereIboId);

        // Set lighting uniforms (shared for all balls)
        if (lightPosH != -1) GLES20.glUniform3fv(lightPosH, 1, lightPos, 0);
        if (lightColorH != -1) GLES20.glUniform4fv(lightColorH, 1, lightColor, 0);
        if (ambientH != -1) GLES20.glUniform4fv(ambientH, 1, ambientColor, 0);
        if (specularH != -1) GLES20.glUniform4fv(specularH, 1, specularColor, 0);
        if (shininessH != -1) GLES20.glUniform1f(shininessH, shininess);

        float beat = renderer.getBeatIntensity();
        float[] model = new float[16];
        float[] tempMvp = new float[16];

        // Enable blending for transparency
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        // Draw each ball
        for (GlassBall ball : balls) {
            float r = ball.radius;
            Matrix.setIdentityM(model, 0);
            Matrix.translateM(model, 0, ball.x, ball.y, ball.z);
            Matrix.scaleM(model, 0, r, r, r);
            Matrix.multiplyMM(tempMvp, 0, mvpMatrix, 0, model, 0);

            if (mvpH != -1) GLES20.glUniformMatrix4fv(mvpH, 1, false, tempMvp, 0);
            if (modelH != -1) GLES20.glUniformMatrix4fv(modelH, 1, false, model, 0);
            if (colorH != -1) GLES20.glUniform4fv(colorH, 1, ball.color, 0);

            GLES20.glDrawElements(
                GLES20.GL_TRIANGLES,
                sphereIndexCount,
                GLES20.GL_UNSIGNED_SHORT,
                0
            );
        }

        // Disable blending
        GLES20.glDisable(GLES20.GL_BLEND);

        // Cleanup
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        if (posH != -1) GLES20.glDisableVertexAttribArray(posH);
        if (normH != -1) GLES20.glDisableVertexAttribArray(normH);
    }

    // --- Buffer Management ---
    private void releaseBuffers() {
        if (initialized) {
            GLES20.glDeleteBuffers(3, new int[]{sphereVboId, sphereNboId, sphereIboId}, 0);
            initialized = false;
        }
    }

    @Override
    public void release() {
        releaseBuffers();
    }

    // --- Utilities ---
    private static FloatBuffer nativeFB(float[] d) {
        ByteBuffer b = ByteBuffer.allocateDirect(d.length * 4);
        b.order(ByteOrder.nativeOrder());
        FloatBuffer fb = b.asFloatBuffer();
        fb.put(d);
        fb.position(0);
        return fb;
    }

    private static ShortBuffer nativeSB(short[] d) {
        ByteBuffer b = ByteBuffer.allocateDirect(d.length * 2);
        b.order(ByteOrder.nativeOrder());
        ShortBuffer sb = b.asShortBuffer();
        sb.put(d);
        sb.position(0);
        return sb;
    }
}

