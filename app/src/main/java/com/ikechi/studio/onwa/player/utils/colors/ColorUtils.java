package com.ikechi.studio.onwa.player.utils.colors;

import android.graphics.Color;

public class ColorUtils {

    private ColorUtils() {}

    public static int darken(int color, float factor) {
        return ColorPalette.darken(color, factor);
    }

    public static int darken(int color) {
        return ColorPalette.darken(color);
    }

    public static int lighten(int color, float factor) {
        return ColorPalette.lighten(color, factor);
    }

    public static int lighten(int color) {
        return ColorPalette.lighten(color);
    }

    public static int withAlpha(int color, int alpha) {
        return ColorPalette.withAlpha(color, alpha);
    }

    public static int getComplementary(int color) {
        return ColorPalette.getComplementaryColor(color);
    }

    public static String toHex(int color) {
        return ColorPalette.toHexString(color);
    }

    public static String toArgb(int color) {
        return ColorPalette.toArgbString(color);
    }

    public static boolean isDark(int color) {
        return ColorPalette.isDarkColor(color);
    }

    public static int getTextColorForBackground(int backgroundColor) {
        return ColorPalette.getTextColorForBackground(backgroundColor);
    }

    // Get contrast ratio between two colors (WCAG)
    public static double getContrastRatio(int color1, int color2) {
        double luminance1 = getRelativeLuminance(color1);
        double luminance2 = getRelativeLuminance(color2);

        double lighter = Math.max(luminance1, luminance2);
        double darker = Math.min(luminance1, luminance2);

        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double getRelativeLuminance(int color) {
        double r = Color.red(color) / 255.0;
        double g = Color.green(color) / 255.0;
        double b = Color.blue(color) / 255.0;

        r = r <= 0.03928 ? r / 12.92 : Math.pow((r + 0.055) / 1.055, 2.4);
        g = g <= 0.03928 ? g / 12.92 : Math.pow((g + 0.055) / 1.055, 2.4);
        b = b <= 0.03928 ? b / 12.92 : Math.pow((b + 0.055) / 1.055, 2.4);

        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    // Check if contrast meets WCAG AA standard (4.5:1)
    public static boolean meetsContrastAA(int color1, int color2) {
        return getContrastRatio(color1, color2) >= 4.5;
    }

    // Check if contrast meets WCAG AAA standard (7:1)
    public static boolean meetsContrastAAA(int color1, int color2) {
        return getContrastRatio(color1, color2) >= 7.0;
    }

    // Blend two colors
    public static int blendColors(int color1, int color2, float ratio) {
        float inverseRatio = 1 - ratio;
        float a = Color.alpha(color1) * inverseRatio + Color.alpha(color2) * ratio;
        float r = Color.red(color1) * inverseRatio + Color.red(color2) * ratio;
        float g = Color.green(color1) * inverseRatio + Color.green(color2) * ratio;
        float b = Color.blue(color1) * inverseRatio + Color.blue(color2) * ratio;

        return Color.argb((int) a, (int) r, (int) g, (int) b);
    }

    // Convert color to grayscale
    public static int toGrayscale(int color) {
        int gray = (int) (Color.red(color) * 0.299 + 
			Color.green(color) * 0.587 + 
			Color.blue(color) * 0.114);
        return Color.rgb(gray, gray, gray);
    }

    // Invert color
    public static int invertColor(int color) {
        return Color.rgb(
            255 - Color.red(color),
            255 - Color.green(color),
            255 - Color.blue(color)
        );
    }
}
