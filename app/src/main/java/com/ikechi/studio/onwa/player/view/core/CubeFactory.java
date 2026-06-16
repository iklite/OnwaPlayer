package com.ikechi.studio.onwa.player.view.core;

/**
 * Factory for generating cuboid (3D bar) geometry.
 *
 * Vertex format: x, y, z  (36 vertices = 12 triangles = 6 faces × 2 triangles).
 * No normals or colours are included – callers supply those separately.
 */
public final class CubeFactory {

    private CubeFactory() {}

    /**
     * Creates a 3D bar centred on {@code centerX}, sitting on y = 0,
     * rising to y = {@code height}.
     *
     * @param centerX X position of the bar's centre.
     * @param height  Height of the bar (may be 0 for a flat degenerate bar).
     * @return Float array of xyz positions, 36 vertices × 3 components = 108 floats.
     */
    public static float[] createBar(float centerX, float height) {
        final float w = 0.25f;   // bar width (half = 0.125)
        final float d = 0.25f;   // bar depth (half = 0.125)

        float x0 = centerX - w * 0.5f;
        float x1 = centerX + w * 0.5f;
        float y0 = 0f;
        float y1 = height;
        float z0 = -d * 0.5f;
        float z1 =  d * 0.5f;

        return new float[]{
            // FRONT
            x0, y0, z1,  x1, y0, z1,  x1, y1, z1,
            x0, y0, z1,  x1, y1, z1,  x0, y1, z1,
            // BACK
            x1, y0, z0,  x0, y0, z0,  x0, y1, z0,
            x1, y0, z0,  x0, y1, z0,  x1, y1, z0,
            // LEFT
            x0, y0, z0,  x0, y0, z1,  x0, y1, z1,
            x0, y0, z0,  x0, y1, z1,  x0, y1, z0,
            // RIGHT
            x1, y0, z1,  x1, y0, z0,  x1, y1, z0,
            x1, y0, z1,  x1, y1, z0,  x1, y1, z1,
            // TOP
            x0, y1, z1,  x1, y1, z1,  x1, y1, z0,
            x0, y1, z1,  x1, y1, z0,  x0, y1, z0,
            // BOTTOM
            x0, y0, z0,  x1, y0, z0,  x1, y0, z1,
            x0, y0, z0,  x1, y0, z1,  x0, y0, z1
        };
    }
}

