package com.ikechi.studio.onwa.player.view.core.objects;

import java.util.Random;

/**
 * Particle with life and decay fields, used in flame, particle, and flare styles.
 */
public class Particle {

    private static final Random RAND = new Random();

    public float x, y, z;
    public float vx, vy, vz;
    public float[] color = new float[4];
    public float life;          // current remaining life (0 = dead)
    public float decay;         // life lost per second
    public float size;

    public void reset(float[] emitterPos, float[] baseColor) {
        x = emitterPos[0] + (RAND.nextFloat() - 0.5f) * 0.1f;
        y = emitterPos[1] + (RAND.nextFloat() - 0.5f) * 0.1f;
        z = emitterPos[2];
        vx = (RAND.nextFloat() - 0.5f) * 0.02f;
        vy = 0.02f + RAND.nextFloat() * 0.05f;
        vz = (RAND.nextFloat() - 0.5f) * 0.01f;
        System.arraycopy(baseColor, 0, color, 0, 4);
        life = 1.0f + RAND.nextFloat() * 2.0f;   // random life between 1 and 3 seconds
        decay = 0.5f + RAND.nextFloat() * 0.5f;  // random decay rate
        size = 0.015f + RAND.nextFloat() * 0.025f;
    }

    public void update(float deltaTime, float beatIntensity) {
        float dt60 = deltaTime * 60f; // maintain behaviour from original (approx 60fps scaling)
        x += vx * dt60;
        y += vy * dt60;
        z += vz * dt60;

        // Simple gravity
        vy -= 0.0005f * dt60;

        // Beat boost
        if (beatIntensity > 0.1f) {
            vy += beatIntensity * 0.012f;
        }

        life -= decay * deltaTime;
        if (life < 0f) life = 0f;

        // Alpha fades with remaining life (adjust multiplier as needed in your styles)
        color[3] = life; // you can multiply by a factor later in the style's update
    }

    public boolean isAlive() {
        return life > 0f;
    }
}
