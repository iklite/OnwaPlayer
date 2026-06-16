package com.ikechi.studio.onwa.player.view.core.objects;

import java.util.Random;

public class StarFlare {

    private static final Random RAND = new Random();

    public float x, y;
    public float angle;       // current rotation angle in degrees
    public float length;      // current arm length
    public float[] color      = new float[4];
    public float   pulsePhase;

    private final float baseLength;

    public StarFlare(float x, float y) {
        this.x   = x;
        this.y   = y;
        angle      = RAND.nextFloat() * 360f;
        baseLength = 0.18f + RAND.nextFloat() * 0.22f;
        length     = baseLength;
        pulsePhase = RAND.nextFloat() * 6.28318f;

        float hue  = RAND.nextFloat();
        color[0]   = 0.5f + 0.5f * (float) Math.sin(hue * 6.28318f);
        color[1]   = 0.5f + 0.5f * (float) Math.sin((hue + 0.333f) * 6.28318f);
        color[2]   = 0.5f + 0.5f * (float) Math.sin((hue + 0.667f) * 6.28318f);
        color[3]   = 0.8f;
    }

    public void update(float deltaTime, float beatIntensity) {
        pulsePhase += deltaTime * 2.5f;
        angle      += deltaTime * 22f;

        if (beatIntensity > 0.2f) {
            color[3] = Math.min(1f, 0.8f + beatIntensity * 0.5f);
            length   = baseLength + beatIntensity * 0.35f;
        } else {
            float pulse = 0.5f + 0.5f * (float) Math.sin(pulsePhase);
            color[3]    = Math.max(0.35f, 0.75f * pulse);
            // Ease length back to base
            length = length + (baseLength - length) * Math.min(1f, deltaTime * 4f);
        }
    }
}

