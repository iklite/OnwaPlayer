package com.ikechi.studio.onwa.player.utils.colors;


import java.util.Arrays;
import java.util.List;

public class GradientBuilder {

    public enum GradientType {
        LINEAR, RADIAL, SWEEP
		}

    public static class GradientSpec {
        private final List<Integer> colors;
        private final GradientType type;
        private final int angle;
        private final float centerX;
        private final float centerY;
        private final float radius;

        public GradientSpec(List<Integer> colors, GradientType type, int angle, 
							float centerX, float centerY, float radius) {
            this.colors = colors;
            this.type = type;
            this.angle = angle;
            this.centerX = centerX;
            this.centerY = centerY;
            this.radius = radius;
        }

        public List<Integer> getColors() { return colors; }
        public GradientType getType() { return type; }
        public int getAngle() { return angle; }
        public float getCenterX() { return centerX; }
        public float getCenterY() { return centerY; }
        public float getRadius() { return radius; }

        public int[] getColorsArray() {
            int[] array = new int[colors.size()];
            for (int i = 0; i < colors.size(); i++) {
                array[i] = colors.get(i);
            }
            return array;
        }

        public float[] getPositions() {
            float[] positions = new float[colors.size()];
            for (int i = 0; i < colors.size(); i++) {
                positions[i] = i / (float) (colors.size() - 1);
            }
            return positions;
        }
    }

    public static GradientSpec createLinearGradient(int startColor, int endColor) {
        return createLinearGradient(startColor, endColor, 0);
    }

    public static GradientSpec createLinearGradient(int startColor, int endColor, int angle) {
        return new GradientSpec(
            Arrays.asList(startColor, endColor),
            GradientType.LINEAR,
            angle,
            0.5f,
            0.5f,
            0.5f
        );
    }

    public static GradientSpec createMultiColorGradient(List<Integer> colors, int angle) {
        return new GradientSpec(
            colors,
            GradientType.LINEAR,
            angle,
            0.5f,
            0.5f,
            0.5f
        );
    }

    public static GradientSpec createRadialGradient(int centerColor, int edgeColor) {
        return new GradientSpec(
            Arrays.asList(centerColor, edgeColor),
            GradientType.RADIAL,
            0,
            0.5f,
            0.5f,
            1.0f
        );
    }

    public static GradientSpec createSunsetGradient() {
        return createMultiColorGradient(
            ColorPalette.getGradientSet("Sunset"),
            45
        );
    }

    public static GradientSpec createOceanGradient() {
        return createMultiColorGradient(
            ColorPalette.getGradientSet("Ocean"),
            135
        );
    }

    public static GradientSpec createFireGradient() {
        return createMultiColorGradient(
            ColorPalette.getGradientSet("Fire"),
            90
        );
    }
}
