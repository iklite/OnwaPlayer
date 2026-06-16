package com.ikechi.studio.onwa.player.utils.colors;


import android.graphics.Color;
import java.util.List;

public class ColorTheme {
    private int primary;
    private int secondary;
    private int accent;
    private int background;
    private int surface;
    private int error;
    private int textPrimary;
    private int textSecondary;
    private boolean isDarkTheme;

    public ColorTheme() {
        // Default light theme
        this.primary = ColorPalette.MATERIAL_COLORS.get(5); // Material Blue
        this.secondary = ColorPalette.MATERIAL_COLORS.get(9); // Material Green
        this.accent = ColorPalette.MATERIAL_COLORS.get(14); // Material Orange
        this.background = Color.WHITE;
        this.surface = Color.WHITE;
        this.error = ColorPalette.SEMANTIC_COLORS.get("Error");
        this.textPrimary = ColorPalette.TEXT_COLORS.get("Primary");
        this.textSecondary = ColorPalette.TEXT_COLORS.get("Secondary");
        this.isDarkTheme = false;
    }

    public ColorTheme(int primary, int secondary, int accent, 
					  int background, int surface, int error,
					  int textPrimary, int textSecondary, boolean isDarkTheme) {
        this.primary = primary;
        this.secondary = secondary;
        this.accent = accent;
        this.background = background;
        this.surface = surface;
        this.error = error;
        this.textPrimary = textPrimary;
        this.textSecondary = textSecondary;
        this.isDarkTheme = isDarkTheme;
    }

    // Getters
    public int getPrimary() { return primary; }
    public int getSecondary() { return secondary; }
    public int getAccent() { return accent; }
    public int getBackground() { return background; }
    public int getSurface() { return surface; }
    public int getError() { return error; }
    public int getTextPrimary() { return textPrimary; }
    public int getTextSecondary() { return textSecondary; }
    public boolean isDarkTheme() { return isDarkTheme; }

    // Setters
    public void setPrimary(int primary) { this.primary = primary; }
    public void setSecondary(int secondary) { this.secondary = secondary; }
    public void setAccent(int accent) { this.accent = accent; }
    public void setBackground(int background) { this.background = background; }
    public void setSurface(int surface) { this.surface = surface; }
    public void setError(int error) { this.error = error; }
    public void setTextPrimary(int textPrimary) { this.textPrimary = textPrimary; }
    public void setTextSecondary(int textSecondary) { this.textSecondary = textSecondary; }
    public void setDarkTheme(boolean darkTheme) { isDarkTheme = darkTheme; }

    public ColorTheme toDarkTheme() {
        return new ColorTheme(
            ColorUtils.lighten(this.primary, 0.2f),
            ColorUtils.lighten(this.secondary, 0.2f),
            this.accent,
            ColorPalette.DARK_BACKGROUND_COLORS.get("Primary"),
            ColorPalette.DARK_BACKGROUND_COLORS.get("Card"),
            this.error,
            ColorPalette.TEXT_COLORS.get("Primary Dark"),
            ColorPalette.TEXT_COLORS.get("Secondary Dark"),
            true
        );
    }

    public ColorTheme toLightTheme() {
        return new ColorTheme(
            ColorUtils.darken(this.primary, 0.8f),
            ColorUtils.darken(this.secondary, 0.8f),
            this.accent,
            Color.WHITE,
            Color.WHITE,
            this.error,
            ColorPalette.TEXT_COLORS.get("Primary"),
            ColorPalette.TEXT_COLORS.get("Secondary"),
            false
        );
    }

    public GradientTheme generateGradientTheme() {
        return new GradientTheme(
            ColorPalette.getGradientSet("Ocean"),
            ColorPalette.getGradientSet("Sunset"),
            ColorPalette.getGradientSet("Fire")
        );
    }

    // Static factory methods for common themes
    public static ColorTheme createMaterialBlueTheme() {
        ColorTheme theme = new ColorTheme();
        theme.setPrimary(ColorPalette.MATERIAL_COLORS.get(5)); // Material Blue
        theme.setSecondary(ColorPalette.MATERIAL_COLORS.get(8)); // Material Teal
        theme.setAccent(ColorPalette.MATERIAL_COLORS.get(14)); // Material Orange
        return theme;
    }

    public static ColorTheme createMaterialPurpleTheme() {
        ColorTheme theme = new ColorTheme();
        theme.setPrimary(ColorPalette.MATERIAL_COLORS.get(3)); // Material Deep Purple
        theme.setSecondary(ColorPalette.MATERIAL_COLORS.get(2)); // Material Purple
        theme.setAccent(ColorPalette.MATERIAL_COLORS.get(13)); // Material Amber
        return theme;
    }

    public static ColorTheme createMaterialGreenTheme() {
        ColorTheme theme = new ColorTheme();
        theme.setPrimary(ColorPalette.MATERIAL_COLORS.get(9)); // Material Green
        theme.setSecondary(ColorPalette.MATERIAL_COLORS.get(10)); // Material Light Green
        theme.setAccent(ColorPalette.MATERIAL_COLORS.get(12)); // Material Yellow
        return theme;
    }
}

class GradientTheme {
    private final List<Integer> primaryGradient;
    private final List<Integer> secondaryGradient;
    private final List<Integer> accentGradient;

    public GradientTheme(List<Integer> primaryGradient, 
						 List<Integer> secondaryGradient, 
						 List<Integer> accentGradient) {
        this.primaryGradient = primaryGradient;
        this.secondaryGradient = secondaryGradient;
        this.accentGradient = accentGradient;
    }

    public List<Integer> getPrimaryGradient() { return primaryGradient; }
    public List<Integer> getSecondaryGradient() { return secondaryGradient; }
    public List<Integer> getAccentGradient() { return accentGradient; }
}
