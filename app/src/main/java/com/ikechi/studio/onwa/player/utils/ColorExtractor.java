package com.ikechi.studio.onwa.player.utils;

import android.graphics.Bitmap;
import android.graphics.Color;

/**
 * Simple bitmap color extraction without AndroidX Palette.
 *
 * <p>Samples the bitmap at a grid of points and returns dominant / vibrant
 * colors suitable for dynamic UI theming.
 *
 * <p>Zero Jetpack.  Zero lambdas.  API 18+.
 */
public class ColorExtractor {

    /**
     * Result container for extracted colors.
     */
    public static class Swatch {
        /** The most vibrant color found. */
        public final int vibrant;
        /** A darker variant suitable for backgrounds. */
        public final int darkVibrant;
        /** A muted variant suitable for text surfaces. */
        public final int muted;
        /** Title text color — light or dark based on background. */
        public final int titleTextColor;
        /** Body text color. */
        public final int bodyTextColor;

        public Swatch(int vibrant, int darkVibrant, int muted,
                      int titleTextColor, int bodyTextColor) {
            this.vibrant = vibrant;
            this.darkVibrant = darkVibrant;
            this.muted = muted;
            this.titleTextColor = titleTextColor;
            this.bodyTextColor = bodyTextColor;
        }
    }

    /** Number of sample points along each axis. Total samples = GRID × GRID. */
    private static final int GRID = 12;
    /** Minimum population count for a color bucket to be considered. */
    private static final int MIN_POPULATION = 3;

    /**
     * Extracts colors from the given bitmap.
     *
     * @param bitmap Source bitmap (scaled down for performance).
     * @return A {@link Swatch} with extracted colors.
     */
    public static Swatch from(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return getDefaultSwatch();
        }

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (w == 0 || h == 0) return getDefaultSwatch();

        // Sample pixels on a grid
        int[] colors = new int[GRID * GRID];
        int idx = 0;
        for (int y = 0; y < GRID; y++) {
            for (int x = 0; x < GRID; x++) {
                int px = (x + 1) * w / (GRID + 1);
                int py = (y + 1) * h / (GRID + 1);
                colors[idx++] = bitmap.getPixel(px, py);
            }
        }

        // Find the most vibrant color (highest saturation)
        int vibrant = findMostVibrant(colors);
        int darkVibrant = darken(vibrant, 0.6f);
        int muted = desaturate(vibrant, 0.5f);

        // Determine text colors based on background luminance
        int titleColor = isDark(vibrant) ? Color.WHITE : Color.BLACK;
        int bodyColor = isDark(vibrant) ? 0xFFCCCCCC : 0xFF333333;

        return new Swatch(vibrant, darkVibrant, muted, titleColor, bodyColor);
    }

    private static int findMostVibrant(int[] colors) {
        int bestColor = colors[0];
        float bestSat = 0f;

        for (int color : colors) {
            float[] hsv = new float[3];
            Color.colorToHSV(color, hsv);
            float sat = hsv[1];
            float val = hsv[2];

            // Prefer saturated, not-too-dark, not-too-light colors
            if (sat > bestSat && val > 0.15f && val < 0.95f) {
                bestSat = sat;
                bestColor = color;
            }
        }

        // Fallback: use the color that appears most frequently with saturation > 0.2
        if (bestSat < 0.15f) {
            bestColor = findMostFrequentSaturated(colors);
        }

        return bestColor;
    }

    private static int findMostFrequentSaturated(int[] colors) {
        int bestColor = colors[0];
        int bestCount = 0;

        for (int i = 0; i < colors.length; i++) {
            float[] hsv = new float[3];
            Color.colorToHSV(colors[i], hsv);
            if (hsv[1] < 0.2f) continue;

            int count = 0;
            for (int j = 0; j < colors.length; j++) {
                if (colorDistance(colors[i], colors[j]) < 80f) {
                    count++;
                }
            }
            if (count > bestCount) {
                bestCount = count;
                bestColor = colors[i];
            }
        }

        return bestColor;
    }

    private static float colorDistance(int c1, int c2) {
        float dr = Color.red(c1)   - Color.red(c2);
        float dg = Color.green(c1) - Color.green(c2);
        float db = Color.blue(c1)  - Color.blue(c2);
        return (float) Math.sqrt(dr * dr + dg * dg + db * db);
    }

    private static int darken(int color, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= factor;
        return Color.HSVToColor(hsv);
    }

    private static int desaturate(int color, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] *= factor;
        return Color.HSVToColor(hsv);
    }

    private static boolean isDark(int color) {
        float luminance = 0.299f * Color.red(color)
			+ 0.587f * Color.green(color)
			+ 0.114f * Color.blue(color);
        return luminance < 128f;
    }

    public static Swatch getDefaultSwatch() {
        int v = 0xFF005C63;
        return new Swatch(v, darken(v, 0.6f), desaturate(v, 0.5f),
                          Color.WHITE, 0xFFCCCCCC);
    }
}
