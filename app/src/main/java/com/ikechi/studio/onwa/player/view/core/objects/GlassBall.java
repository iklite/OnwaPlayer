package com.ikechi.studio.onwa.player.view.core.objects;

import java.util.Random;

public class GlassBall {

    private static final Random RAND = new Random();

    public float x, y, z;
    public float vx, vy, vz;
    public float radius;
    public float[] color    = new float[4];
    public float   pulse;
    public float   rotation;

    // Base colour – used to prevent the colour drifting to white permanently
    private final float[] baseColor = new float[3];

    public GlassBall() {
        x  = RAND.nextFloat() * 1.6f - 0.8f;
        y  = RAND.nextFloat() * 1.6f - 0.8f;
        z  = RAND.nextFloat() * 0.5f;
        vx = (RAND.nextFloat() - 0.5f) * 0.018f;
        vy = (RAND.nextFloat() - 0.5f) * 0.018f;
        vz = (RAND.nextFloat() - 0.5f) * 0.008f;
        radius     = 0.08f + RAND.nextFloat() * 0.10f;
        baseColor[0] = color[0] = 0.5f + RAND.nextFloat() * 0.5f;
        baseColor[1] = color[1] = 0.5f + RAND.nextFloat() * 0.5f;
        baseColor[2] = color[2] = 0.5f + RAND.nextFloat() * 0.5f;
        color[3] = 0.75f;
        pulse    = 0f;
        rotation = 0f;
    }

    public void update(float deltaTime, float beatIntensity) {
        float dt60 = deltaTime * 60f;
        x += vx * dt60;
        y += vy * dt60;
        z += vz * dt60;

        // Bounce off walls
        if (Math.abs(x) > 0.88f) { vx = -vx * 0.92f; x = Math.signum(x) * 0.88f; }
        if (Math.abs(y) > 0.88f) { vy = -vy * 0.92f; y = Math.signum(y) * 0.88f; }
        if (z < -1.0f || z > 0.5f) {
            vz = -vz * 0.92f;
            z  = Math.max(-1.0f, Math.min(0.5f, z));
        }

        pulse    += deltaTime * 2.2f;
        rotation += deltaTime * 0.6f;

        if (beatIntensity > 0.1f) {
            // Boost towards white on beat, then drift back to baseColor
            color[0] = Math.min(1f, color[0] + beatIntensity * 0.25f);
            color[1] = Math.min(1f, color[1] + beatIntensity * 0.15f);
            color[2] = Math.min(1f, color[2] + beatIntensity * 0.35f);
            color[3] = 0.95f;
        } else {
            // Slowly relax back to base colour
            color[0] = lerp(color[0], baseColor[0], deltaTime * 0.5f);
            color[1] = lerp(color[1], baseColor[1], deltaTime * 0.5f);
            color[2] = lerp(color[2], baseColor[2], deltaTime * 0.5f);
            color[3] = Math.max(0.5f, color[3] * 0.997f);
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}

