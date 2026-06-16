package com.ikechi.studio.onwa.player.view.core.objects;

import java.util.Random;

public class CosmicParticle {

    private static final Random RAND = new Random();

    public float x, y, z;
    public float vx, vy, vz;
    public float size;
    public float[] color    = new float[4];
    public float   lifetime;

    public CosmicParticle() {
        reset();
    }

    public void reset() {
        x  = RAND.nextFloat() * 2f - 1f;
        y  = RAND.nextFloat() * 2f - 1f;
        z  = RAND.nextFloat() * 2f - 1f;
        vx = (RAND.nextFloat() - 0.5f) * 0.006f;
        vy = (RAND.nextFloat() - 0.5f) * 0.006f;
        vz = (RAND.nextFloat() - 0.5f) * 0.006f;
        size     = 0.015f + RAND.nextFloat() * 0.025f;
        color[0] = 0.3f  + RAND.nextFloat() * 0.7f;
        color[1] = 0.3f  + RAND.nextFloat() * 0.7f;
        color[2] = 0.75f + RAND.nextFloat() * 0.25f;
        color[3] = 0.6f;
        lifetime = 0.5f + RAND.nextFloat() * 0.5f; // normalised 0→1
    }

    public void update(float deltaTime, float beatIntensity) {
        float dt60 = deltaTime * 60f;
        x += vx * dt60;
        y += vy * dt60;
        z += vz * dt60;
        lifetime -= deltaTime * 0.12f;

        if (beatIntensity > 0.2f) {
            color[3] = Math.min(1f, color[3] + beatIntensity * 0.3f);
            // Size pulses but resets on next reset() so doesn't grow unbounded
            size     = Math.min(0.06f, size * (1f + beatIntensity * 0.15f));
        }

        // Wrap around the unit cube
        if (x < -1.2f) x = 1.2f; else if (x > 1.2f) x = -1.2f;
        if (y < -1.2f) y = 1.2f; else if (y > 1.2f) y = -1.2f;
        if (z < -1.2f) z = 1.2f; else if (z > 1.2f) z = -1.2f;
    }

    public boolean isAlive() { return lifetime > 0f; }
}

