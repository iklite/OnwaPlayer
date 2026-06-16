package com.ikechi.studio.onwa.player.utils.colors;

import android.graphics.drawable.GradientDrawable;
import java.util.List;

public class GradientHelper {

    public static GradientDrawable createLinearGradientDrawable(int startColor, int endColor, int angle) {
        GradientDrawable gradient = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{startColor, endColor}
        );
        gradient.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        gradient.setGradientCenter(0.5f, 0.5f);
        return gradient;
    }

    public static GradientDrawable createSunsetGradientDrawable(int cornerRadius) {
        List<Integer> colors = ColorPalette.getGradientSet("Sunset");
        int[] colorArray = new int[colors.size()];
        for (int i = 0; i < colors.size(); i++) {
            colorArray[i] = colors.get(i);
        }

        GradientDrawable gradient = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            colorArray
        );
        gradient.setCornerRadius(cornerRadius);
        return gradient;
    }

    public static GradientDrawable createButtonGradient(int color, boolean isPressed) {
        int baseColor;
        int edgeColor;

        if (isPressed) {
            baseColor = ColorUtils.darken(color, 0.8f);
            edgeColor = ColorUtils.darken(color, 0.6f);
        } else {
            baseColor = color;
            edgeColor = ColorUtils.lighten(color, 1.2f);
        }

        GradientDrawable gradient = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{baseColor, edgeColor}
        );
        gradient.setCornerRadius(12);
        gradient.setStroke(2, ColorUtils.darken(color, 0.3f));

        return gradient;
    }

    // Additional helper methods without lambdas

    public static GradientDrawable createMultiColorGradientDrawable(List<Integer> colors, int cornerRadius) {
        int[] colorArray = new int[colors.size()];
        for (int i = 0; i < colors.size(); i++) {
            colorArray[i] = colors.get(i);
        }

        GradientDrawable gradient = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            colorArray
        );
        gradient.setCornerRadius(cornerRadius);
        return gradient;
    }

    public static GradientDrawable createRadialGradientDrawable(int centerColor, int edgeColor, int cornerRadius) {
        GradientDrawable gradient = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{centerColor, edgeColor}
        );
        gradient.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        gradient.setGradientCenter(0.5f, 0.5f);
        gradient.setGradientRadius(500);
        gradient.setCornerRadius(cornerRadius);
        return gradient;
    }

    public static GradientDrawable createStatefulButtonGradient(int normalColor, int pressedColor) {
        // Create gradient for normal state
        int normalStart = ColorUtils.lighten(normalColor, 1.1f);
        int normalEnd = ColorUtils.darken(normalColor, 0.9f);

        int pressedStart = ColorUtils.darken(pressedColor, 0.8f);
        int pressedEnd = ColorUtils.darken(pressedColor, 0.6f);

        // Note: For actual StateListDrawable, you'd need to create multiple GradientDrawables
        // This is just the normal state gradient
        GradientDrawable gradient = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{normalStart, normalEnd}
        );
        gradient.setCornerRadius(8);
        gradient.setStroke(2, ColorUtils.darken(normalColor, 0.3f));

        return gradient;
    }

    public static GradientDrawable createGradientFromSpec(GradientBuilder.GradientSpec spec, int cornerRadius) {
        List<Integer> colors = spec.getColors();
        int[] colorArray = new int[colors.size()];
        for (int i = 0; i < colors.size(); i++) {
            colorArray[i] = colors.get(i);
        }

        GradientDrawable gradient = new GradientDrawable(
            getOrientationFromAngle(spec.getAngle()),
            colorArray
        );

        if (spec.getType() == GradientBuilder.GradientType.RADIAL) {
            gradient.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            gradient.setGradientCenter(spec.getCenterX(), spec.getCenterY());
            gradient.setGradientRadius(spec.getRadius() * 1000);
        } else if (spec.getType() == GradientBuilder.GradientType.SWEEP) {
            gradient.setGradientType(GradientDrawable.SWEEP_GRADIENT);
            gradient.setGradientCenter(spec.getCenterX(), spec.getCenterY());
        }

        gradient.setCornerRadius(cornerRadius);
        return gradient;
    }

    private static GradientDrawable.Orientation getOrientationFromAngle(int angle) {
        // Convert angle to nearest Android orientation
        // Android doesn't support arbitrary angles in XML, but we can approximate
        if (angle >= 315 || angle < 45) {
            return GradientDrawable.Orientation.LEFT_RIGHT;
        } else if (angle >= 45 && angle < 135) {
            return GradientDrawable.Orientation.BL_TR;
        } else if (angle >= 135 && angle < 225) {
            return GradientDrawable.Orientation.BOTTOM_TOP;
        } else {
            return GradientDrawable.Orientation.BR_TL;
        }
    }

    public static GradientDrawable createMaterialGradient(int color500, int color700, int cornerRadius) {
        // Material Design style gradient using two shades
        GradientDrawable gradient = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{color500, color700}
        );
        gradient.setCornerRadius(cornerRadius);
        return gradient;
    }

    public static GradientDrawable createNeonGlowGradient(int baseColor, int cornerRadius) {
        int brightColor = ColorUtils.lighten(baseColor, 1.5f);
        int darkColor = ColorUtils.darken(baseColor, 0.7f);

        GradientDrawable gradient = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{brightColor, baseColor, darkColor}
        );
        gradient.setCornerRadius(cornerRadius);
        gradient.setStroke(1, brightColor);

        return gradient;
    }
}
