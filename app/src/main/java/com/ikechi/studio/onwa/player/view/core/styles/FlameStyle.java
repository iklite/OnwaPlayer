
package com.ikechi.studio.onwa.player.view.core.styles;

import android.opengl.GLES20;
import android.util.Log;

import com.ikechi.studio.onwa.player.view.core.GLVisualizerRenderer;
import com.ikechi.studio.onwa.player.view.core.visuals.VisualStyle;
import com.ikechi.studio.onwa.player.utils.TextureHelper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Enhanced Living Inferno – A cinematic fire simulation with:
 * - Fluid dynamics (buoyancy, vorticity, turbulence)
 * - Physically accurate heat-based colors (black-body radiation)
 * - Audio-reactive energy and shockwaves
 * - Heat shimmer distortion
 * - Optimized GPU rendering
 */
public class FlameStyle extends VisualStyle {
    private static final String TAG = "FlameStyle";

    // --- Particle Counts ---
    private static final int PLASMA_PARTICLES = 150;    // Core white-hot plasma
    private static final int FIRE_PARTICLES = 500;      // Main flame body (increased for density)
    private static final int ASH_TRAILS = 80;           // Rising dark ash
    private static final int EMBER_COUNT = 120;         // Floating sparks
    private static final int SHOCKWAVE_COUNT = 4;       // Beat explosions
    private static final int HEAT_SHIMMER = 100;        // Distortion particles

    // --- Constants ---
    private static final float EMITTER_Y = -0.9f;       // Fire emitter position
    private static final float GRAVITY = -0.001f;      // Slight downward pull for ash
    private static final float BUOYANCY = 0.02f;        // Upward force for fire
    private static final float VORTICITY = 0.05f;       // Swirling motion
    private static final float TURBULENCE_SCALE = 0.1f; // Noise intensity
    private static final float DRAG = 0.97f;           // Air resistance
    private static final float SHOCKWAVE_SPEED = 1.5f; // Expansion speed
    private static final float HEAT_DISTORTION = 0.03f; // Shimmer strength

    private static final Random RAND = new Random();

    // --- Systems ---
    private final List<Plasma> plasmas = new ArrayList<>();
    private final List<FireParticle> fires = new ArrayList<>();
    private final List<Ash> ashes = new ArrayList<>();
    private final List<Ember> embers = new ArrayList<>();
    private final List<Shockwave> shockwaves = new ArrayList<>();
    private final List<Shimmer> shimmers = new ArrayList<>();

    // --- OpenGL ---
    private int particleTexture;
    private int glowTexture;
    private int vboId;
    private FloatBuffer vertexBuffer;
    private boolean initialized = false;

    // --- State ---
    private float smoothedEnergy = 0f;
    private float bassEnergy = 0f;
    private float globalTime = 0f;
    private float noiseOffset = 0f;
    private int shockIndex = 0;

    // --- Noise for Turbulence ---
    private static final int NOISE_SIZE = 128;
    private final float[][] noiseGrid = new float[NOISE_SIZE][NOISE_SIZE];

    // --- Inner Classes ---
    private static class Plasma {
        float x, y, z, vx, vy, vz, size, life, maxLife, temp;
        float[] color = new float[4];
    }

    private static class FireParticle {
        float x, y, z, vx, vy, vz, size, life, maxLife, temp;
        float[] color = new float[4];
        float turbulence;
    }

    private static class Ash {
        float x, y, z, vx, vy, vz, size, life, rotation;
        float[] color = new float[4];
    }

    private static class Ember {
        float x, y, z, vx, vy, vz, size, life, hue;
        float orbitRadius;
        float orbitSpeed;
        float orbitAngle;
    }

    private static class Shockwave {
        float x, y, z, radius, energy, life;
        int type; // 0=ring, 1=spiral, 2=star
        boolean active = false;
    }

    private static class Shimmer {
        float x, y, z, size, phase, strength;
    }

    // --- Initialization ---
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

        if (initialized) {
            cleanup();
        }

        // Initialize textures
        particleTexture = TextureHelper.createCircleTexture();
        glowTexture = createGlowTexture();

        // Initialize noise grid for turbulence
        initNoise();

        // Initialize particle systems
        initSystems();

        // Allocate VBO
        allocateBuffer();

        initialized = true;
        Log.d(TAG, "Enhanced Living Inferno initialized");
    }

    private int createGlowTexture() {
        // Create a soft glow texture (Gaussian blur effect)
        return TextureHelper.createGlowTexture();
    }

    private void initNoise() {
        for (int i = 0; i < NOISE_SIZE; i++) {
            for (int j = 0; j < NOISE_SIZE; j++) {
                noiseGrid[i][j] = RAND.nextFloat() * 2f - 1f;
            }
        }
    }

    private void cleanup() {
        if (vboId != 0) {
            GLES20.glDeleteBuffers(1, new int[]{vboId}, 0);
            vboId = 0;
        }
        if (particleTexture != 0) {
            GLES20.glDeleteTextures(1, new int[]{particleTexture}, 0);
            particleTexture = 0;
        }
        if (glowTexture != 0 && glowTexture != particleTexture) {
            GLES20.glDeleteTextures(1, new int[]{glowTexture}, 0);
            glowTexture = 0;
        }
    }

    private void initSystems() {
        // Plasma core
        for (int i = 0; i < PLASMA_PARTICLES; i++) {
            Plasma p = new Plasma();
            resetPlasma(p, true);
            plasmas.add(p);
        }

        // Fire body
        for (int i = 0; i < FIRE_PARTICLES; i++) {
            FireParticle p = new FireParticle();
            resetFire(p, 0f, true);
            fires.add(p);
        }

        // Ash trails
        for (int i = 0; i < ASH_TRAILS; i++) {
            Ash a = new Ash();
            resetAsh(a, true);
            ashes.add(a);
        }

        // Ember constellation
        for (int i = 0; i < EMBER_COUNT; i++) {
            Ember e = new Ember();
            resetEmber(e, true);
            embers.add(e);
        }

        // Shockwave pool
        for (int i = 0; i < SHOCKWAVE_COUNT; i++) {
            shockwaves.add(new Shockwave());
        }

        // Heat shimmer
        for (int i = 0; i < HEAT_SHIMMER; i++) {
            Shimmer s = new Shimmer();
            resetShimmer(s);
            shimmers.add(s);
        }
    }

    private void allocateBuffer() {
        // Calculate max vertices: all particles × 6 (2 triangles per quad)
        int maxVerts = (PLASMA_PARTICLES + FIRE_PARTICLES + ASH_TRAILS +
			EMBER_COUNT + SHOCKWAVE_COUNT * 32 + HEAT_SHIMMER) * 6;
        int stride = 9; // 3 pos + 2 uv + 4 color

        ByteBuffer bb = ByteBuffer.allocateDirect(maxVerts * stride * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();

        int[] ids = new int[1];
        GLES20.glGenBuffers(1, ids, 0);
        vboId = ids[0];

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, maxVerts * stride * 4,
							null, GLES20.GL_DYNAMIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    // --- Particle Reset Methods ---
    private void resetPlasma(Plasma p, boolean initial) {
        float angle = RAND.nextFloat() * (float) (Math.PI * 2);
        float radius = RAND.nextFloat() * 0.1f;
        p.x = (float) Math.cos(angle) * radius;
        p.y = EMITTER_Y + RAND.nextFloat() * 0.1f;
        p.z = RAND.nextFloat() * 0.05f - 0.025f;

        p.vx = (RAND.nextFloat() - 0.5f) * 0.05f;
        p.vy = 0.2f + RAND.nextFloat() * 0.1f;
        p.vz = (RAND.nextFloat() - 0.5f) * 0.02f;

        p.size = 0.05f + RAND.nextFloat() * 0.1f;
        p.temp = 1.5f; // Extremely hot (white-blue)
        p.maxLife = 0.5f + RAND.nextFloat() * 0.3f;
        p.life = initial ? RAND.nextFloat() * p.maxLife : p.maxLife;

        // White-blue plasma color
        updatePlasmaColor(p);
    }

    private void updatePlasmaColor(Plasma p) {
        // Temperature-based color (black-body radiation approximation)
        float temp = p.temp;
        if (temp > 1.2f) {
            // White-blue (hottest)
            p.color[0] = 0.9f + (temp - 1.2f) * 0.5f;
            p.color[1] = 0.95f + (temp - 1.2f) * 0.3f;
            p.color[2] = 1.0f;
        } else if (temp > 0.8f) {
            // Blue-white
            float t = (temp - 0.8f) / 0.4f;
            p.color[0] = 0.7f + t * 0.2f;
            p.color[1] = 0.8f + t * 0.15f;
            p.color[2] = 0.9f + t * 0.1f;
        } else {
            // White
            p.color[0] = 0.9f;
            p.color[1] = 0.95f;
            p.color[2] = 1.0f;
        }
        p.color[3] = 0.8f + (p.life / p.maxLife) * 0.2f;
    }

    private void resetFire(FireParticle p, float audioEnergy, boolean initial) {
        float angle = (float) Math.PI / 2 + (RAND.nextFloat() - 0.5f) * 1.0f;
        float dist = RAND.nextFloat() * (0.3f + audioEnergy * 0.2f);

        p.x = (float) Math.cos(angle) * dist;
        p.y = EMITTER_Y + RAND.nextFloat() * 0.05f;
        p.z = (RAND.nextFloat() - 0.5f) * 0.1f;

        float speed = 0.3f + RAND.nextFloat() * 0.4f + audioEnergy * 0.5f;
        p.vx = (float) Math.cos(angle) * speed * 0.5f;
        p.vy = (float) Math.sin(angle) * speed + BUOYANCY;
        p.vz = (RAND.nextFloat() - 0.5f) * 0.05f;

        p.size = 0.03f + RAND.nextFloat() * 0.05f + audioEnergy * 0.02f;
        p.temp = 0.8f + RAND.nextFloat() * 0.4f + audioEnergy * 0.2f;
        p.maxLife = 0.8f + RAND.nextFloat() * 0.5f - audioEnergy * 0.1f;
        p.life = initial ? RAND.nextFloat() * p.maxLife : p.maxLife;
        p.turbulence = RAND.nextFloat();

        updateFireColor(p);
    }

    private void updateFireColor(FireParticle p) {
        // Black-body radiation approximation for fire
        float temp = p.temp;
        if (temp > 1.0f) {
            // White (hottest)
            p.color[0] = 1.0f;
            p.color[1] = 0.95f;
            p.color[2] = 0.8f;
        } else if (temp > 0.7f) {
            // Yellow-white
            float t = (temp - 0.7f) / 0.3f;
            p.color[0] = 0.9f + t * 0.1f;
            p.color[1] = 0.8f + t * 0.15f;
            p.color[2] = 0.5f + t * 0.3f;
        } else if (temp > 0.4f) {
            // Orange
            float t = (temp - 0.4f) / 0.3f;
            p.color[0] = 0.9f;
            p.color[1] = 0.4f + t * 0.4f;
            p.color[2] = 0.1f + t * 0.2f;
        } else {
            // Red (coolest)
            p.color[0] = 0.8f + temp * 0.2f;
            p.color[1] = 0.1f + temp * 0.2f;
            p.color[2] = 0.05f;
        }
        p.color[3] = 0.6f + (p.life / p.maxLife) * 0.4f;
    }

    private void resetAsh(Ash a, boolean initial) {
        a.x = (RAND.nextFloat() - 0.5f) * 1.0f;
        a.y = EMITTER_Y + RAND.nextFloat() * 0.2f;
        a.z = (RAND.nextFloat() - 0.5f) * 0.1f;

        a.vx = (RAND.nextFloat() - 0.5f) * 0.05f;
        a.vy = 0.1f + RAND.nextFloat() * 0.1f + GRAVITY;
        a.vz = (RAND.nextFloat() - 0.5f) * 0.02f;

        a.size = 0.02f + RAND.nextFloat() * 0.03f;
        a.rotation = RAND.nextFloat() * (float) (Math.PI * 2);
        a.life = 1.0f + RAND.nextFloat() * 1.5f;

        // Dark gray with slight red tint
        a.color[0] = 0.1f + RAND.nextFloat() * 0.05f;
        a.color[1] = 0.05f + RAND.nextFloat() * 0.03f;
        a.color[2] = 0.03f + RAND.nextFloat() * 0.02f;
        a.color[3] = 0.3f + RAND.nextFloat() * 0.2f;
    }

    private void resetEmber(Ember e, boolean initial) {
        // Orbital paths
        e.orbitRadius = 0.4f + RAND.nextFloat() * 0.3f;
        e.orbitSpeed = 0.1f + RAND.nextFloat() * 0.2f;
        e.orbitAngle = RAND.nextFloat() * (float) (Math.PI * 2);

        e.x = (float) Math.cos(e.orbitAngle) * e.orbitRadius;
        e.y = EMITTER_Y + 0.3f + RAND.nextFloat() * 0.2f;
        e.z = (RAND.nextFloat() - 0.5f) * 0.2f;

        e.vx = 0;
        e.vy = 0;
        e.vz = 0;

        e.size = 0.01f + RAND.nextFloat() * 0.02f;
        e.hue = 0.05f + RAND.nextFloat() * 0.07f; // Red-orange
        e.life = 2.0f + RAND.nextFloat() * 3.0f;
    }

    private void resetShimmer(Shimmer s) {
        s.x = (RAND.nextFloat() - 0.5f) * 1.8f;
        s.y = EMITTER_Y + RAND.nextFloat() * 1.8f;
        s.z = (RAND.nextFloat() - 0.5f) * 0.1f;
        s.size = 0.3f + RAND.nextFloat() * 0.7f;
        s.phase = RAND.nextFloat() * (float) (Math.PI * 2);
        s.strength = 0.05f + RAND.nextFloat() * 0.15f;
    }

    private void spawnShockwave(float energy) {
        Shockwave sw = shockwaves.get(shockIndex);
        shockIndex = (shockIndex + 1) % SHOCKWAVE_COUNT;

        sw.x = (RAND.nextFloat() - 0.5f) * 0.4f;
        sw.y = EMITTER_Y + 0.2f + RAND.nextFloat() * 0.2f;
        sw.z = (RAND.nextFloat() - 0.5f) * 0.1f;
        sw.radius = 0.01f;
        sw.energy = energy;
        sw.life = 1.0f;
        sw.type = energy > 0.8f ? 2 : (energy > 0.6f ? 1 : 0);
        sw.active = true;
    }

    // --- Update Loop ---
    @Override
    public void update(float deltaTime, float[] fftBands, float beatIntensity, float totalTime) {
        globalTime += deltaTime;
        noiseOffset += deltaTime * 0.1f;

        // Calculate energies
        float instantEnergy = 0f;
        float bassEnergy = 0f;
        if (fftBands != null && fftBands.length > 0) {
            for (int i = 0; i < fftBands.length; i++) {
                float v = fftBands[i] * renderer.getSensitivity();
                instantEnergy += v;
                if (i < fftBands.length / 4) {
                    bassEnergy += v;
                }
            }
            instantEnergy /= fftBands.length;
            bassEnergy = Math.min(bassEnergy / (fftBands.length / 4) * 2f, 2f);
        }

        // Smooth energies
        smoothedEnergy += (instantEnergy - smoothedEnergy) * 0.08f;
        bassEnergy = bassEnergy * 0.9f + beatIntensity * 0.1f;

        // Spawn shockwave on heavy beats
        if (beatIntensity > 0.6f && bassEnergy > 0.5f) {
            spawnShockwave(beatIntensity);
            bassEnergy = 0f;
        }

        // Update all systems
        updatePlasmas(deltaTime, bassEnergy);
        updateFires(deltaTime, smoothedEnergy, bassEnergy);
        updateAshes(deltaTime, bassEnergy);
        updateEmbers(deltaTime, smoothedEnergy);
        updateShockwaves(deltaTime, smoothedEnergy);
        updateShimmers(deltaTime, smoothedEnergy);

        // Build render buffer
        buildBuffer();
    }

    // --- System Updates ---
    private void updatePlasmas(float dt, float bassEnergy) {
		for (Plasma p : plasmas) {
			// Clamp coordinates to [-1, 1] to avoid noise grid out-of-bounds
			float clampedX = Math.max(-1f, Math.min(1f, p.x));
			float clampedY = Math.max(-1f, Math.min(1f, p.y));

			// Turbulent core motion (use clamped coordinates for noise)
			p.vx += (sampleNoise(clampedX * 2f, clampedY * 2f, globalTime) - 0.5f) * 0.08f * bassEnergy;
			p.vy += (sampleNoise(clampedX * 2f, clampedY * 2f + 10f, globalTime) - 0.5f) * 0.05f;
            p.vz += (sampleNoise(p.x * 2f, p.y * 2f + 20f, globalTime) - 0.5f) * 0.02f;

            // Apply drag
            p.vx *= DRAG;
            p.vy *= DRAG;
            p.vz *= DRAG;

            // Update position
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.z += p.vz * dt;

            // Converge toward center
            p.x *= 0.98f;

            // Update temperature (cools as it rises)
            p.temp = Math.max(0.5f, p.temp - dt * 0.5f + bassEnergy * 0.1f);

            // Update life
            p.life -= dt;
            if (p.life <= 0 || p.y > -0.3f) {
                resetPlasma(p, false);
            }

            updatePlasmaColor(p);
        }
    }

    private void updateFires(float dt, float energy, float bassEnergy) {
        for (FireParticle p : fires) {
            // Turbulence from noise
            float noise = sampleNoise(p.x * 3f, p.y * 3f, globalTime) * 2f - 1f;
            p.vx += noise * 0.03f * energy;
            p.vy += (sampleNoise(p.x * 2f, p.y * 2f + 10f, globalTime) - 0.5f) * 0.02f * energy;

            // Buoyancy
            p.vy += BUOYANCY * (1f + energy * 0.5f);

            // Vorticity (swirling motion)
            float vortex = (float) Math.sin(globalTime * 2f + p.x * 3f) * VORTICITY * energy;
            p.vx += p.y * vortex * dt;
            p.vy += -p.x * vortex * dt;

            // Apply drag
            p.vx *= DRAG;
            p.vy *= DRAG;
            p.vz *= DRAG;

            // Update position
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.z += p.vz * dt;

            // Temperature decreases as particle rises
            p.temp = Math.max(0.3f, p.temp - dt * 0.3f + energy * 0.05f);

            // Update life
            p.life -= dt * (0.5f + energy * 0.2f);
            if (p.life <= 0 || p.y > 1.2f || Math.abs(p.x) > 1.4f) {
                resetFire(p, energy, false);
            }

            updateFireColor(p);
        }
    }

    private void updateAshes(float dt, float bassEnergy) {
        for (Ash a : ashes) {
            // Turbulence
            a.vx += (sampleNoise(a.x * 2f, a.y * 2f, globalTime) - 0.5f) * 0.02f * bassEnergy;
            a.vy += (sampleNoise(a.x * 2f, a.y * 2f + 10f, globalTime) - 0.5f) * 0.01f;

            // Apply gravity and drag
            a.vy += GRAVITY;
            a.vx *= DRAG;
            a.vy *= DRAG;
            a.vz *= DRAG;

            // Update position
            a.x += a.vx * dt;
            a.y += a.vy * dt;
            a.z += a.vz * dt;

            // Rotation
            a.rotation += dt * 0.5f;

            // Update life
            a.life -= dt * 0.3f;
            if (a.life <= 0 || a.y > 0.9f) {
                resetAsh(a, false);
            }

            // Fade out
            a.color[3] = a.life * 0.5f;
        }
    }

    private void updateEmbers(float dt, float energy) {
        for (Ember e : embers) {
            // Orbital motion
            e.orbitAngle += e.orbitSpeed * dt * (1f + energy * 0.5f);
            float targetX = (float) Math.cos(e.orbitAngle) * e.orbitRadius;
            float targetY = EMITTER_Y + 0.3f + (float) Math.sin(e.orbitAngle * 0.5f) * 0.1f;

            // Move toward orbit with some randomness
            e.vx = (targetX - e.x) * 0.1f + (RAND.nextFloat() - 0.5f) * 0.01f;
            e.vy = (targetY - e.y) * 0.1f + (RAND.nextFloat() - 0.5f) * 0.01f;

            // Apply velocity
            e.x += e.vx * dt;
            e.y += e.vy * dt;
            e.z += e.vz * dt;

            // Spiral outward as they age
            float age = 1f - e.life / 5f;
            e.orbitRadius *= 1f + age * 0.001f;

            // Update life
            e.life -= dt * 0.2f;
            if (e.life <= 0 || e.y > 1.2f) {
                resetEmber(e, false);
            }
        }
    }

    private void updateShockwaves(float dt, float energy) {
        for (Shockwave sw : shockwaves) {
            if (!sw.active) continue;

            sw.radius += dt * SHOCKWAVE_SPEED * (0.5f + sw.energy * 0.5f);
            sw.life -= dt * 1.2f;

            // Distort nearby particles (simulated heat wave)
            if (sw.radius < 0.5f) {
                for (FireParticle p : fires) {
                    float dx = p.x - sw.x;
                    float dy = p.y - sw.y;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    if (dist < sw.radius) {
                        // Push particles outward
                        float force = (sw.radius - dist) / sw.radius * sw.energy * 0.2f;
                        p.vx += dx / dist * force;
                        p.vy += dy / dist * force;
                    }
                }
            }

            if (sw.life <= 0) {
                sw.active = false;
            }
        }
    }

    private void updateShimmers(float dt, float energy) {
        for (Shimmer s : shimmers) {
            s.phase += dt * (1f + energy * 2f);
            s.y += dt * (0.2f + energy * 0.1f);
            s.x += (sampleNoise(s.x * 0.5f, s.y * 0.5f, globalTime) - 0.5f) * 0.01f * energy;

            if (s.y > 1.2f) {
                resetShimmer(s);
                s.y = EMITTER_Y - 0.1f;
            }

            // Strength pulses with energy
            s.strength = 0.05f + energy * 0.1f;
        }
    }

    // --- Noise Sampling (for turbulence) ---
    private float sampleNoise(float x, float y, float time) {
		// Scale coordinates to noise grid
		int ix = (int) ((x + 1f) * 0.5f * NOISE_SIZE) % NOISE_SIZE;
		int iy = (int) ((y + 1f) * 0.5f * NOISE_SIZE) % NOISE_SIZE;

		// Ensure indices are within bounds (0 to NOISE_SIZE-1)
		ix = Math.max(0, Math.min(ix, NOISE_SIZE - 1));
		iy = Math.max(0, Math.min(iy, NOISE_SIZE - 1));

		// Fractional part for interpolation
		float fx = (x + 1f) * 0.5f * NOISE_SIZE - ix;
		float fy = (y + 1f) * 0.5f * NOISE_SIZE - iy;

		// Get noise values at corners
		float n00 = noiseGrid[ix][iy];
		float n01 = noiseGrid[ix][(iy + 1) % NOISE_SIZE];
		float n10 = noiseGrid[(ix + 1) % NOISE_SIZE][iy];
		float n11 = noiseGrid[(ix + 1) % NOISE_SIZE][(iy + 1) % NOISE_SIZE];

		// Bilinear interpolation
		float ix0 = n00 + fx * (n10 - n00);
		float ix1 = n01 + fx * (n11 - n01);
		return ix0 + fy * (ix1 - ix0);
	}

    // --- Buffer Building ---
    private void buildBuffer() {
        vertexBuffer.clear();

        // Layer 1: Plasma core (brightest, additive blending)
        for (Plasma p : plasmas) {
            if (p.life <= 0) continue;
            drawParticle(p.x, p.y, p.z, p.size, p.color, true);
        }

        // Layer 2: Shockwaves (behind fire)
        for (Shockwave sw : shockwaves) {
            if (!sw.active) continue;
            drawShockwave(sw);
        }

        // Layer 3: Main fire (additive blending)
        for (FireParticle p : fires) {
            if (p.life <= 0) continue;
            float size = p.size * (0.7f + p.life / p.maxLife * 0.3f);
            drawParticle(p.x, p.y, p.z, size, p.color, true);
        }

        // Layer 4: Heat shimmer (subtractive blending)
        for (Shimmer s : shimmers) {
            float alpha = (float) Math.sin(s.phase) * s.strength * smoothedEnergy;
            if (alpha > 0) {
                float[] color = {1f, 1f, 1f, alpha * 0.5f};
                drawParticle(s.x, s.y, s.z, s.size, color, false);
            }
        }

        // Layer 5: Ash (dark, behind embers)
        for (Ash a : ashes) {
            if (a.life <= 0) continue;
            drawRotatedParticle(a.x, a.y, a.z, a.size, a.rotation, a.color);
        }

        // Layer 6: Ember constellation (brightest, on top)
        for (Ember e : embers) {
            if (e.life <= 0) continue;
            float[] rgb = hsbToRgb(e.hue, 0.9f, 1.0f);
            float alpha = Math.min(1f, e.life / 2f) * 0.9f;
            float pulse = (float) Math.sin(globalTime * 10f + e.life * 2f) * 0.2f + 0.8f;
            float[] color = {rgb[0], rgb[1], rgb[2], alpha * pulse};
            float size = e.size * (0.8f + pulse * 0.4f);
            drawParticle(e.x, e.y, e.z, size, color, true);
        }

        vertexBuffer.flip();

        int bytes = vertexBuffer.limit() * 4;
        if (bytes > 0) {
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
            GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, bytes, vertexBuffer);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        }
    }

    // --- Drawing Helpers ---
    private void drawParticle(float x, float y, float z, float size, float[] color, boolean glow) {
        float s = size * 0.5f;
        float x0 = x - s, y0 = y - s;
        float x1 = x + s, y1 = y - s;
        float x2 = x - s, y2 = y + s;
        float x3 = x + s, y3 = y + s;

        float r = color[0], g = color[1], b = color[2], a = color[3];

        // Triangle 1
        putVert(x0, y0, z, 0, 0, r, g, b, a);
        putVert(x1, y1, z, 1, 0, r, g, b, a);
        putVert(x2, y2, z, 0, 1, r, g, b, a);
        // Triangle 2
        putVert(x1, y1, z, 1, 0, r, g, b, a);
        putVert(x3, y3, z, 1, 1, r, g, b, a);
        putVert(x2, y2, z, 0, 1, r, g, b, a);
    }

    private void drawRotatedParticle(float x, float y, float z, float size, float rot, float[] color) {
        float c = (float) Math.cos(rot) * size * 0.5f;
        float s = (float) Math.sin(rot) * size * 0.5f;

        float[] dx = {-c + s, c + s, -c - s, c - s};
        float[] dy = {-s - c, s - c, -s + c, s + c};

        float r = color[0], g = color[1], b = color[2], a = color[3];

        putVert(x + dx[0], y + dy[0], z, 0, 0, r, g, b, a);
        putVert(x + dx[1], y + dy[1], z, 1, 0, r, g, b, a);
        putVert(x + dx[2], y + dy[2], z, 0, 1, r, g, b, a);

        putVert(x + dx[1], y + dy[1], z, 1, 0, r, g, b, a);
        putVert(x + dx[3], y + dy[3], z, 1, 1, r, g, b, a);
        putVert(x + dx[2], y + dy[2], z, 0, 1, r, g, b, a);
    }

    private void drawShockwave(Shockwave sw) {
        int segments = sw.type == 2 ? 8 : (sw.type == 1 ? 16 : 32);
        float inner = sw.radius;
        float outer = sw.radius + 0.04f + sw.energy * 0.08f;

        float r = 1.0f, g = 0.9f, b = 0.6f;
        float a = sw.life * 0.7f;

        for (int i = 0; i < segments; i++) {
            double a1 = 2.0 * Math.PI * i / segments;
            double a2 = 2.0 * Math.PI * (i + 1) / segments;

            // Add spiral twist for type 1
            if (sw.type == 1) {
                a1 += sw.radius * 3;
                a2 += sw.radius * 3;
            }

            // Star points for type 2
            float r1 = (sw.type == 2 && i % 2 == 0) ? outer * 1.3f : outer;
            float r2 = (sw.type == 2 && (i + 1) % 2 == 0) ? outer * 1.3f : outer;

            float x1i = sw.x + (float) Math.cos(a1) * inner;
            float y1i = sw.y + (float) Math.sin(a1) * inner;
            float x1o = sw.x + (float) Math.cos(a1) * r1;
            float y1o = sw.y + (float) Math.sin(a1) * r1;
            float x2i = sw.x + (float) Math.cos(a2) * inner;
            float y2i = sw.y + (float) Math.sin(a2) * inner;
            float x2o = sw.x + (float) Math.cos(a2) * r2;
            float y2o = sw.y + (float) Math.sin(a2) * r2;

            // Quad as two triangles
            putVert(x1i, y1i, sw.z + 0.05f, 0.5f, 0.5f, r, g, b, a);
            putVert(x1o, y1o, sw.z + 0.05f, 0.5f, 0.5f, r, g, b, a * 0.5f);
            putVert(x2o, y2o, sw.z + 0.05f, 0.5f, 0.5f, r, g, b, a * 0.5f);

            putVert(x1i, y1i, sw.z + 0.05f, 0.5f, 0.5f, r, g, b, a);
            putVert(x2o, y2o, sw.z + 0.05f, 0.5f, 0.5f, r, g, b, a * 0.5f);
            putVert(x2i, y2i, sw.z + 0.05f, 0.5f, 0.5f, r, g, b, a);
        }
    }

    private void putVert(float x, float y, float z, float u, float v,
                         float r, float g, float b, float a) {
        vertexBuffer.put(x);
        vertexBuffer.put(y);
        vertexBuffer.put(z);
        vertexBuffer.put(u);
        vertexBuffer.put(v);
        vertexBuffer.put(r);
        vertexBuffer.put(g);
        vertexBuffer.put(b);
        vertexBuffer.put(a);
    }

    // --- Color Conversion ---
    private float[] hsbToRgb(float h, float s, float b) {
        float[] rgb = new float[3];
        h = h % 1.0f;
        int i = (int) (h * 6);
        float f = h * 6 - i;
        float p = b * (1 - s);
        float q = b * (1 - f * s);
        float t = b * (1 - (1 - f) * s);

        switch (i % 6) {
            case 0: rgb[0] = b; rgb[1] = t; rgb[2] = p; break;
            case 1: rgb[0] = q; rgb[1] = b; rgb[2] = p; break;
            case 2: rgb[0] = p; rgb[1] = b; rgb[2] = t; break;
            case 3: rgb[0] = p; rgb[1] = q; rgb[2] = b; break;
            case 4: rgb[0] = t; rgb[1] = p; rgb[2] = b; break;
            case 5: rgb[0] = b; rgb[1] = p; rgb[2] = q; break;
        }
        return rgb;
    }

    // --- Drawing ---
    @Override
    public void draw2D(float[] mvpMatrix, GLVisualizerRenderer renderer) {
        draw(mvpMatrix, texturedShaderProgram2D);
    }

    @Override
    public void draw3D(float[] mvpMatrix, GLVisualizerRenderer renderer) {
        draw(mvpMatrix, texturedShaderProgram3D);
    }

    private void draw(float[] mvpMatrix, int program) {
        if (program == 0 || vboId == 0) return;

        GLES20.glUseProgram(program);

        int posH = GLES20.glGetAttribLocation(program, "vPosition");
        int texH = GLES20.glGetAttribLocation(program, "aTexCoord");
        int colH = GLES20.glGetAttribLocation(program, "aColor");
        int mvpH = GLES20.glGetUniformLocation(program, "uMVPMatrix");
        int texU = GLES20.glGetUniformLocation(program, "uTexture");

        if (mvpH != -1) GLES20.glUniformMatrix4fv(mvpH, 1, false, mvpMatrix, 0);
        if (texU != -1) GLES20.glUniform1i(texU, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, particleTexture);

        int stride = 9 * 4; // 36 bytes (3 pos + 2 uv + 4 color)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);

        if (posH != -1) {
            GLES20.glVertexAttribPointer(posH, 3, GLES20.GL_FLOAT, false, stride, 0);
            GLES20.glEnableVertexAttribArray(posH);
        }
        if (texH != -1) {
            GLES20.glVertexAttribPointer(texH, 2, GLES20.GL_FLOAT, false, stride, 12);
            GLES20.glEnableVertexAttribArray(texH);
        }
        if (colH != -1) {
            GLES20.glVertexAttribPointer(colH, 4, GLES20.GL_FLOAT, false, stride, 20);
            GLES20.glEnableVertexAttribArray(colH);
        }

        // Enable additive blending for glow effects
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);

        int verts = vertexBuffer.limit() / 9;
        if (verts > 0) {
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, verts);
        }

        // Reset blending for other layers
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        if (posH != -1) GLES20.glDisableVertexAttribArray(posH);
        if (texH != -1) GLES20.glDisableVertexAttribArray(texH);
        if (colH != -1) GLES20.glDisableVertexAttribArray(colH);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    @Override
    public void release() {
        if (initialized) {
            cleanup();
            initialized = false;
        }
    }
}

