package com.ikechi.studio.onwa.player.utils.colors;

import android.graphics.Color;
import android.util.Pair;
import java.util.*;

public class ColorPalette {

    // Private constructor to prevent instantiation
    private ColorPalette() {}

    // Primary Material Design Colors
    public static final List<Integer> MATERIAL_COLORS = Arrays.asList(
        Color.parseColor("#F44336"), // Material Red
        Color.parseColor("#E91E63"), // Material Pink
        Color.parseColor("#9C27B0"), // Material Purple
        Color.parseColor("#673AB7"), // Material Deep Purple
        Color.parseColor("#3F51B5"), // Material Indigo
        Color.parseColor("#2196F3"), // Material Blue
        Color.parseColor("#03A9F4"), // Material Light Blue
        Color.parseColor("#00BCD4"), // Material Cyan
        Color.parseColor("#009688"), // Material Teal
        Color.parseColor("#4CAF50"), // Material Green
        Color.parseColor("#8BC34A"), // Material Light Green
        Color.parseColor("#CDDC39"), // Material Lime
        Color.parseColor("#FFEB3B"), // Material Yellow
        Color.parseColor("#FFC107"), // Material Amber
        Color.parseColor("#FF9800"), // Material Orange
        Color.parseColor("#FF5722"), // Material Deep Orange
        Color.parseColor("#795548"), // Material Brown
        Color.parseColor("#9E9E9E"), // Material Grey
        Color.parseColor("#607D8B")  // Material Blue Grey
    );

    // Material Red Shades (50-900)
    public static final List<Integer> MATERIAL_RED_SHADES = Arrays.asList(
        Color.parseColor("#FFEBEE"), // 50
        Color.parseColor("#FFCDD2"), // 100
        Color.parseColor("#EF9A9A"), // 200
        Color.parseColor("#E57373"), // 300
        Color.parseColor("#EF5350"), // 400
        Color.parseColor("#F44336"), // 500
        Color.parseColor("#E53935"), // 600
        Color.parseColor("#D32F2F"), // 700
        Color.parseColor("#C62828"), // 800
        Color.parseColor("#B71C1C")  // 900
    );

    // Material Blue Shades
    public static final List<Integer> MATERIAL_BLUE_SHADES = Arrays.asList(
        Color.parseColor("#E3F2FD"), // 50
        Color.parseColor("#BBDEFB"), // 100
        Color.parseColor("#90CAF9"), // 200
        Color.parseColor("#64B5F6"), // 300
        Color.parseColor("#42A5F5"), // 400
        Color.parseColor("#2196F3"), // 500
        Color.parseColor("#1E88E5"), // 600
        Color.parseColor("#1976D2"), // 700
        Color.parseColor("#1565C0"), // 800
        Color.parseColor("#0D47A1")  // 900
    );

    // Material Green Shades
    public static final List<Integer> MATERIAL_GREEN_SHADES = Arrays.asList(
        Color.parseColor("#E8F5E9"), // 50
        Color.parseColor("#C8E6C9"), // 100
        Color.parseColor("#A5D6A7"), // 200
        Color.parseColor("#81C784"), // 300
        Color.parseColor("#66BB6A"), // 400
        Color.parseColor("#4CAF50"), // 500
        Color.parseColor("#43A047"), // 600
        Color.parseColor("#388E3C"), // 700
        Color.parseColor("#2E7D32"), // 800
        Color.parseColor("#1B5E20")  // 900
    );

    // Brand Colors
    public static final List<Integer> BRAND_COLORS = Arrays.asList(
        Color.parseColor("#1877F2"), // Facebook Blue
        Color.parseColor("#1DA1F2"), // Twitter Blue
        Color.parseColor("#E1306C"), // Instagram Purple
        Color.parseColor("#25D366"), // WhatsApp Green
        Color.parseColor("#0A66C2"), // LinkedIn Blue
        Color.parseColor("#FF0000"), // YouTube Red
        Color.parseColor("#BD081C"), // Pinterest Red
        Color.parseColor("#FFFC00"), // Snapchat Yellow
        Color.parseColor("#5865F2"), // Discord Blurple
        Color.parseColor("#9146FF"), // Twitch Purple
        Color.parseColor("#FF4500"), // Reddit Orange
        Color.parseColor("#1DB954"), // Spotify Green
        Color.parseColor("#4285F4")  // Google Blue
    );

    // Semantic Colors
    public static final Map<String, Integer> SEMANTIC_COLORS = new HashMap<>();
    static {
        SEMANTIC_COLORS.put("Success", Color.parseColor("#28A745"));
        SEMANTIC_COLORS.put("Success Light", Color.parseColor("#D4EDDA"));
        SEMANTIC_COLORS.put("Success Dark", Color.parseColor("#1E7E34"));
        SEMANTIC_COLORS.put("Warning", Color.parseColor("#FFC107"));
        SEMANTIC_COLORS.put("Warning Light", Color.parseColor("#FFF3CD"));
        SEMANTIC_COLORS.put("Warning Dark", Color.parseColor("#D39E00"));
        SEMANTIC_COLORS.put("Error", Color.parseColor("#DC3545"));
        SEMANTIC_COLORS.put("Error Light", Color.parseColor("#F8D7DA"));
        SEMANTIC_COLORS.put("Error Dark", Color.parseColor("#C82333"));
        SEMANTIC_COLORS.put("Info", Color.parseColor("#17A2B8"));
        SEMANTIC_COLORS.put("Info Light", Color.parseColor("#D1ECF1"));
        SEMANTIC_COLORS.put("Info Dark", Color.parseColor("#138496"));
    }

    // Status Colors
    public static final Map<String, Integer> STATUS_COLORS = new HashMap<>();
    static {
        STATUS_COLORS.put("Active", Color.parseColor("#4CAF50"));
        STATUS_COLORS.put("Inactive", Color.parseColor("#9E9E9E"));
        STATUS_COLORS.put("Pending", Color.parseColor("#FF9800"));
        STATUS_COLORS.put("Blocked", Color.parseColor("#F44336"));
        STATUS_COLORS.put("Online", Color.parseColor("#00E676"));
        STATUS_COLORS.put("Offline", Color.parseColor("#9E9E9E"));
        STATUS_COLORS.put("Away", Color.parseColor("#FFC107"));
        STATUS_COLORS.put("Busy", Color.parseColor("#F44336"));
    }

    // Priority Colors
    public static final Map<String, Integer> PRIORITY_COLORS = new HashMap<>();
    static {
        PRIORITY_COLORS.put("Critical", Color.parseColor("#D32F2F"));
        PRIORITY_COLORS.put("High", Color.parseColor("#FF5722"));
        PRIORITY_COLORS.put("Medium", Color.parseColor("#FF9800"));
        PRIORITY_COLORS.put("Low", Color.parseColor("#4CAF50"));
        PRIORITY_COLORS.put("Normal", Color.parseColor("#2196F3"));
    }

    // Text Colors
    public static final Map<String, Integer> TEXT_COLORS = new HashMap<>();
    static {
        TEXT_COLORS.put("Primary", Color.parseColor("#212121"));
        TEXT_COLORS.put("Primary Dark", Color.parseColor("#FFFFFF"));
        TEXT_COLORS.put("Secondary", Color.parseColor("#757575"));
        TEXT_COLORS.put("Secondary Dark", Color.parseColor("#BDBDBD"));
        TEXT_COLORS.put("Hint", Color.parseColor("#9E9E9E"));
        TEXT_COLORS.put("Hint Dark", Color.parseColor("#616161"));
        TEXT_COLORS.put("Disabled", Color.parseColor("#BDBDBD"));
        TEXT_COLORS.put("Link", Color.parseColor("#2196F3"));
        TEXT_COLORS.put("Link Visited", Color.parseColor("#673AB7"));
        TEXT_COLORS.put("Error", Color.parseColor("#F44336"));
        TEXT_COLORS.put("Success", Color.parseColor("#4CAF50"));
        TEXT_COLORS.put("Warning", Color.parseColor("#FF9800"));
    }

    // Background Colors
    public static final Map<String, Integer> BACKGROUND_COLORS = new HashMap<>();
    static {
        BACKGROUND_COLORS.put("Primary", Color.parseColor("#FFFFFF"));
        BACKGROUND_COLORS.put("Secondary", Color.parseColor("#F5F5F5"));
        BACKGROUND_COLORS.put("Tertiary", Color.parseColor("#EEEEEE"));
        BACKGROUND_COLORS.put("Card", Color.parseColor("#FFFFFF"));
        BACKGROUND_COLORS.put("Dialog", Color.parseColor("#FFFFFF"));
        BACKGROUND_COLORS.put("Snackbar", Color.parseColor("#323232"));
        BACKGROUND_COLORS.put("Toolbar", Color.parseColor("#6200EE"));
        BACKGROUND_COLORS.put("Status Bar", Color.parseColor("#3700B3"));
        BACKGROUND_COLORS.put("Navigation", Color.parseColor("#FFFFFF"));
    }

    // Dark Mode Backgrounds
    public static final Map<String, Integer> DARK_BACKGROUND_COLORS = new HashMap<>();
    static {
        DARK_BACKGROUND_COLORS.put("Primary", Color.parseColor("#121212"));
        DARK_BACKGROUND_COLORS.put("Secondary", Color.parseColor("#1E1E1E"));
        DARK_BACKGROUND_COLORS.put("Tertiary", Color.parseColor("#252525"));
        DARK_BACKGROUND_COLORS.put("Card", Color.parseColor("#1E1E1E"));
        DARK_BACKGROUND_COLORS.put("Dialog", Color.parseColor("#1E1E1E"));
        DARK_BACKGROUND_COLORS.put("Toolbar", Color.parseColor("#1F1B24"));
    }

    // Transparent Colors
    public static final Map<String, Integer> TRANSPARENT_COLORS = new HashMap<>();
    static {
        TRANSPARENT_COLORS.put("Transparent", Color.parseColor("#00000000"));
        TRANSPARENT_COLORS.put("White 10%", Color.parseColor("#1AFFFFFF"));
        TRANSPARENT_COLORS.put("White 20%", Color.parseColor("#33FFFFFF"));
        TRANSPARENT_COLORS.put("White 50%", Color.parseColor("#80FFFFFF"));
        TRANSPARENT_COLORS.put("Black 10%", Color.parseColor("#1A000000"));
        TRANSPARENT_COLORS.put("Black 20%", Color.parseColor("#33000000"));
        TRANSPARENT_COLORS.put("Black 50%", Color.parseColor("#80000000"));
        TRANSPARENT_COLORS.put("Black 70%", Color.parseColor("#B3000000"));
    }

    // Shadow Colors
    public static final Map<String, Integer> SHADOW_COLORS = new HashMap<>();
    static {
        SHADOW_COLORS.put("1dp", Color.parseColor("#0D000000"));
        SHADOW_COLORS.put("2dp", Color.parseColor("#12000000"));
        SHADOW_COLORS.put("3dp", Color.parseColor("#14000000"));
        SHADOW_COLORS.put("4dp", Color.parseColor("#17000000"));
        SHADOW_COLORS.put("6dp", Color.parseColor("#1C000000"));
        SHADOW_COLORS.put("8dp", Color.parseColor("#20000000"));
        SHADOW_COLORS.put("12dp", Color.parseColor("#26000000"));
        SHADOW_COLORS.put("16dp", Color.parseColor("#2B000000"));
        SHADOW_COLORS.put("24dp", Color.parseColor("#33000000"));
    }

    // Chart Colors (10 distinct colors for data visualization)
    public static final List<Integer> CHART_COLORS = Arrays.asList(
        Color.parseColor("#FF6B6B"),
        Color.parseColor("#4ECDC4"),
        Color.parseColor("#FFD166"),
        Color.parseColor("#06D6A0"),
        Color.parseColor("#118AB2"),
        Color.parseColor("#EF476F"),
        Color.parseColor("#7209B7"),
        Color.parseColor("#F15BB5"),
        Color.parseColor("#00BBF9"),
        Color.parseColor("#00F5D4")
    );

    // Rarity Colors (Gaming)
    public static final Map<String, Integer> RARITY_COLORS = new HashMap<>();
    static {
        RARITY_COLORS.put("Common", Color.parseColor("#9E9E9E"));
        RARITY_COLORS.put("Uncommon", Color.parseColor("#4CAF50"));
        RARITY_COLORS.put("Rare", Color.parseColor("#2196F3"));
        RARITY_COLORS.put("Epic", Color.parseColor("#9C27B0"));
        RARITY_COLORS.put("Legendary", Color.parseColor("#FF9800"));
        RARITY_COLORS.put("Mythic", Color.parseColor("#F44336"));
        RARITY_COLORS.put("Ancient", Color.parseColor("#795548"));
    }

    // Gradient Color Sets
    public static final Map<String, List<Integer>> GRADIENT_SETS = new HashMap<>();
    static {
        GRADIENT_SETS.put("Sunset", Arrays.asList(
							  Color.parseColor("#FF6B6B"),
							  Color.parseColor("#FF8E53"),
							  Color.parseColor("#FFD166")
						  ));
        GRADIENT_SETS.put("Ocean", Arrays.asList(
							  Color.parseColor("#2196F3"),
							  Color.parseColor("#21CBF3"),
							  Color.parseColor("#03DAC6")
						  ));
        GRADIENT_SETS.put("Forest", Arrays.asList(
							  Color.parseColor("#4CAF50"),
							  Color.parseColor("#8BC34A"),
							  Color.parseColor("#CDDC39")
						  ));
        GRADIENT_SETS.put("Purple Dream", Arrays.asList(
							  Color.parseColor("#9C27B0"),
							  Color.parseColor("#673AB7"),
							  Color.parseColor("#3F51B5")
						  ));
        GRADIENT_SETS.put("Fire", Arrays.asList(
							  Color.parseColor("#FF5722"),
							  Color.parseColor("#FF9800"),
							  Color.parseColor("#FFC107")
						  ));
        GRADIENT_SETS.put("Ice", Arrays.asList(
							  Color.parseColor("#00BCD4"),
							  Color.parseColor("#4FC3F7"),
							  Color.parseColor("#E3F2FD")
						  ));
    }

    // Category Colors (up to 20 categories)
    public static final List<Integer> CATEGORY_COLORS = Arrays.asList(
        Color.parseColor("#FF5252"),  // Category A
        Color.parseColor("#FF4081"),  // Category B
        Color.parseColor("#E040FB"),  // Category C
        Color.parseColor("#7C4DFF"),  // Category D
        Color.parseColor("#536DFE"),  // Category E
        Color.parseColor("#448AFF"),  // Category F
        Color.parseColor("#40C4FF"),  // Category G
        Color.parseColor("#18FFFF"),  // Category H
        Color.parseColor("#64FFDA"),  // Category I
        Color.parseColor("#69F0AE"),  // Category J
        Color.parseColor("#B2FF59"),  // Category K
        Color.parseColor("#EEFF41"),  // Category L
        Color.parseColor("#FFFF00"),  // Category M
        Color.parseColor("#FFD740"),  // Category N
        Color.parseColor("#FFAB40"),  // Category O
        Color.parseColor("#FF6E40"),  // Category P
        Color.parseColor("#FF3D00"),  // Category Q
        Color.parseColor("#795548"),  // Category R
        Color.parseColor("#9E9E9E"),  // Category S
        Color.parseColor("#607D8B")   // Category T
    );

    // All colors in a single list (lazy loaded)
    private static List<Integer> allColors = null;

    public static List<Integer> getAllColors() {
        if (allColors == null) {
            Set<Integer> colorSet = new HashSet<>();
            colorSet.addAll(MATERIAL_COLORS);
            colorSet.addAll(MATERIAL_RED_SHADES);
            colorSet.addAll(MATERIAL_BLUE_SHADES);
            colorSet.addAll(MATERIAL_GREEN_SHADES);
            colorSet.addAll(BRAND_COLORS);
            colorSet.addAll(SEMANTIC_COLORS.values());
            colorSet.addAll(STATUS_COLORS.values());
            colorSet.addAll(PRIORITY_COLORS.values());
            colorSet.addAll(TEXT_COLORS.values());
            colorSet.addAll(BACKGROUND_COLORS.values());
            colorSet.addAll(DARK_BACKGROUND_COLORS.values());
            colorSet.addAll(TRANSPARENT_COLORS.values());
            colorSet.addAll(SHADOW_COLORS.values());
            colorSet.addAll(CHART_COLORS);
            colorSet.addAll(RARITY_COLORS.values());

            // Add gradient sets
            for (List<Integer> gradient : GRADIENT_SETS.values()) {
                colorSet.addAll(gradient);
            }

            colorSet.addAll(CATEGORY_COLORS);
            allColors = new ArrayList<>(colorSet);
        }
        return allColors;
    }

    // Getter Methods
    public static int getMaterialColor(int index) {
        if (index >= 0 && index < MATERIAL_COLORS.size()) {
            return MATERIAL_COLORS.get(index);
        }
        return Color.BLACK;
    }

    public static List<Integer> getGradientSet(String name) {
        return GRADIENT_SETS.getOrDefault(name, Arrays.asList(Color.BLACK, Color.WHITE));
    }

    public static int getSemanticColor(String name) {
        return SEMANTIC_COLORS.getOrDefault(name, Color.BLACK);
    }

    public static int getStatusColor(String name) {
        return STATUS_COLORS.getOrDefault(name, Color.GRAY);
    }

    public static int getPriorityColor(String name) {
        return PRIORITY_COLORS.getOrDefault(name, Color.BLUE);
    }

    public static int getTextColor(String name) {
        return TEXT_COLORS.getOrDefault(name, Color.BLACK);
    }

    public static int getBackgroundColor(String name) {
        return BACKGROUND_COLORS.getOrDefault(name, Color.WHITE);
    }

    public static int getDarkBackgroundColor(String name) {
        return DARK_BACKGROUND_COLORS.getOrDefault(name, Color.BLACK);
    }

    public static int getTransparentColor(String name) {
        return TRANSPARENT_COLORS.getOrDefault(name, Color.TRANSPARENT);
    }

    public static int getShadowColor(String elevation) {
        return SHADOW_COLORS.getOrDefault(elevation, Color.TRANSPARENT);
    }

    public static int getChartColor(int index) {
        if (index >= 0 && index < CHART_COLORS.size()) {
            return CHART_COLORS.get(index);
        }
        return Color.BLACK;
    }

    public static int getCategoryColor(int index) {
        if (index >= 0 && index < CATEGORY_COLORS.size()) {
            return CATEGORY_COLORS.get(index);
        }
        return Color.BLACK;
    }

    // Color manipulation methods
    public static int darken(int color, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= factor; // Value component
        return Color.HSVToColor(hsv);
    }

    public static int darken(int color) {
        return darken(color, 0.8f);
    }

    public static int lighten(int color, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] = Math.min(1.0f, hsv[2] * factor);
        return Color.HSVToColor(hsv);
    }

    public static int lighten(int color) {
        return lighten(color, 1.2f);
    }

    public static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    public static int getComplementaryColor(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[0] = (hsv[0] + 180) % 360;
        return Color.HSVToColor(hsv);
    }

    public static List<Integer> getAnalogousColors(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);

        List<Integer> colors = new ArrayList<>();
        colors.add(Color.HSVToColor(new float[]{(hsv[0] + 30) % 360, hsv[1], hsv[2]}));
        colors.add(color);
        colors.add(Color.HSVToColor(new float[]{(hsv[0] + 330) % 360, hsv[1], hsv[2]}));

        return colors;
    }

    public static List<Integer> generateGradient(int startColor, int endColor, int steps) {
        List<Integer> gradient = new ArrayList<>();
        for (int i = 0; i < steps; i++) {
            float ratio = i / (float) (steps - 1);
            int red = (int) (Color.red(startColor) * (1 - ratio) + Color.red(endColor) * ratio);
            int green = (int) (Color.green(startColor) * (1 - ratio) + Color.green(endColor) * ratio);
            int blue = (int) (Color.blue(startColor) * (1 - ratio) + Color.blue(endColor) * ratio);
            gradient.add(Color.rgb(red, green, blue));
        }
        return gradient;
    }

    // Random color generator
    public static int getRandomColor() {
        Random random = new Random();
        return Color.rgb(
            random.nextInt(256),
            random.nextInt(256),
            random.nextInt(256)
        );
    }

    public static int getRandomMaterialColor() {
        Random random = new Random();
        return MATERIAL_COLORS.get(random.nextInt(MATERIAL_COLORS.size()));
    }

    public static List<Integer> getRandomGradientSet() {
        Random random = new Random();
        List<String> keys = new ArrayList<>(GRADIENT_SETS.keySet());
        return GRADIENT_SETS.get(keys.get(random.nextInt(keys.size())));
    }

    // Color comparison methods
    public static boolean isDarkColor(int color) {
        double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return darkness > 0.5;
    }

    public static int getTextColorForBackground(int backgroundColor) {
        return isDarkColor(backgroundColor) ? Color.WHITE : Color.BLACK;
    }

    // Color string conversion
    public static String toHexString(int color) {
        return String.format("#%06X", 0xFFFFFF & color);
    }

    public static String toArgbString(int color) {
        return String.format("#%08X", color);
    }

    public static int fromHex(String hex) {
        return Color.parseColor(hex);
    }

    // Get color by name (searches all color collections)
    public static Integer findColorByName(String name) {
        // Check each map in order
        if (SEMANTIC_COLORS.containsKey(name)) return SEMANTIC_COLORS.get(name);
        if (STATUS_COLORS.containsKey(name)) return STATUS_COLORS.get(name);
        if (PRIORITY_COLORS.containsKey(name)) return PRIORITY_COLORS.get(name);
        if (TEXT_COLORS.containsKey(name)) return TEXT_COLORS.get(name);
        if (BACKGROUND_COLORS.containsKey(name)) return BACKGROUND_COLORS.get(name);
        if (DARK_BACKGROUND_COLORS.containsKey(name)) return DARK_BACKGROUND_COLORS.get(name);
        if (TRANSPARENT_COLORS.containsKey(name)) return TRANSPARENT_COLORS.get(name);
        if (SHADOW_COLORS.containsKey(name)) return SHADOW_COLORS.get(name);
        if (RARITY_COLORS.containsKey(name)) return RARITY_COLORS.get(name);

        return null;
    }

    // Get all color names
    public static List<String> getAllColorNames() {
        Set<String> names = new HashSet<>();
        names.addAll(SEMANTIC_COLORS.keySet());
        names.addAll(STATUS_COLORS.keySet());
        names.addAll(PRIORITY_COLORS.keySet());
        names.addAll(TEXT_COLORS.keySet());
        names.addAll(BACKGROUND_COLORS.keySet());
        names.addAll(DARK_BACKGROUND_COLORS.keySet());
        names.addAll(TRANSPARENT_COLORS.keySet());
        names.addAll(SHADOW_COLORS.keySet());
        names.addAll(RARITY_COLORS.keySet());

        return new ArrayList<>(names);
    }

    // Color palette generation
    public static List<Integer> generateMonochromaticPalette(int baseColor, int steps) {
        float[] hsv = new float[3];
        Color.colorToHSV(baseColor, hsv);

        List<Integer> palette = new ArrayList<>();
        for (int i = 0; i < steps; i++) {
            float value = i / (float) (steps - 1);
            palette.add(Color.HSVToColor(new float[]{hsv[0], hsv[1], value}));
        }
        return palette;
    }

    public static List<Integer> generateAnalogousPalette(int baseColor) {
        float[] hsv = new float[3];
        Color.colorToHSV(baseColor, hsv);

        List<Integer> palette = new ArrayList<>();
        palette.add(Color.HSVToColor(new float[]{(hsv[0] + 330) % 360, hsv[1], hsv[2]}));
        palette.add(Color.HSVToColor(new float[]{(hsv[0] + 345) % 360, hsv[1], hsv[2]}));
        palette.add(baseColor);
        palette.add(Color.HSVToColor(new float[]{(hsv[0] + 15) % 360, hsv[1], hsv[2]}));
        palette.add(Color.HSVToColor(new float[]{(hsv[0] + 30) % 360, hsv[1], hsv[2]}));

        return palette;
    }

    // Gradient creation for views
    public static int[] createLinearGradientColors(int startColor, int endColor) {
        return new int[]{startColor, endColor};
    }

    public static int[] createLinearGradientColors(int startColor, int endColor, int angle) {
        return new int[]{startColor, endColor};
    }

    public static int[] createRadialGradientColors(int centerColor, int edgeColor) {
        return new int[]{centerColor, edgeColor};
    }

    // Material Design elevation shadows
    public static Pair<Integer, Integer> getElevationShadow(int elevation) {
        Map<Integer, Pair<Integer, Integer>> shadows = new HashMap<>();
        shadows.put(1, new Pair<>(Color.parseColor("#0D000000"), Color.parseColor("#0D000000")));
        shadows.put(2, new Pair<>(Color.parseColor("#12000000"), Color.parseColor("#12000000")));
        shadows.put(3, new Pair<>(Color.parseColor("#14000000"), Color.parseColor("#14000000")));
        shadows.put(4, new Pair<>(Color.parseColor("#17000000"), Color.parseColor("#17000000")));
        shadows.put(6, new Pair<>(Color.parseColor("#1C000000"), Color.parseColor("#1C000000")));
        shadows.put(8, new Pair<>(Color.parseColor("#20000000"), Color.parseColor("#20000000")));
        shadows.put(12, new Pair<>(Color.parseColor("#26000000"), Color.parseColor("#26000000")));
        shadows.put(16, new Pair<>(Color.parseColor("#2B000000"), Color.parseColor("#2B000000")));
        shadows.put(24, new Pair<>(Color.parseColor("#33000000"), Color.parseColor("#33000000")));

        return shadows.getOrDefault(elevation, new Pair<>(Color.TRANSPARENT, Color.TRANSPARENT));
    }

    // Helper Pair class since Android's Pair requires API 19+
    public static class Pair<F, S> {
        public final F first;
        public final S second;

        public Pair(F first, S second) {
            this.first = first;
            this.second = second;
        }
    }

    // Get gradient angle in degrees
    public static float getGradientAngle(int angleDegrees) {
        // Convert angle to radians and adjust for Android coordinate system
        return (float) Math.toRadians(angleDegrees - 90);
    }
}
