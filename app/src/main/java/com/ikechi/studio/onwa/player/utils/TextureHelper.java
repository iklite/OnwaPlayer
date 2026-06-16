package com.ikechi.studio.onwa.player.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;

public final class TextureHelper {

    private static final String TAG = "TextureHelper";

    private TextureHelper() {}

    /**
     * Creates a 64×64 soft-circle GL texture suitable for particle rendering.
     * Uses a white-to-transparent radial gradient so particles can be tinted
     * by multiplying the texture alpha with the per-vertex colour.
     *
     * @return A valid GL texture object ID, or 0 if creation failed.
     */
    public static int createCircleTexture() {
        final int SIZE = 64;
        Bitmap bitmap = null;
        try {
            bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint  paint  = new Paint(Paint.ANTI_ALIAS_FLAG);

            RadialGradient gradient = new RadialGradient(
                SIZE * 0.5f, SIZE * 0.5f, SIZE * 0.5f,
                new int[]{ Color.WHITE, Color.TRANSPARENT },
                null,
                Shader.TileMode.CLAMP
            );
            paint.setShader(gradient);
            canvas.drawCircle(SIZE * 0.5f, SIZE * 0.5f, SIZE * 0.5f, paint);

            int[] ids = new int[1];
            GLES20.glGenTextures(1, ids, 0);
            if (ids[0] == 0) {
                Log.e(TAG, "glGenTextures returned 0");
                return 0;
            }

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0]);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

            return ids[0];

        } catch (Exception e) {
            Log.e(TAG, "Failed to create circle texture", e);
            return 0;
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
    }

    /**
     * Creates a texture from raw byte data (RGBA).
     * Used for procedural textures like paper grain.
     *
     * @param width  Texture width
     * @param height Texture height
     * @param data   Raw RGBA byte data (must be width * height * 4 bytes)
     * @return A valid GL texture object ID, or 0 if creation failed.
     */
    public static int loadTexture(int width, int height, byte[] data) {
        if (data == null || data.length != width * height * 4) {
            Log.e(TAG, "Invalid texture data size");
            return 0;
        }

        Bitmap bitmap = null;
        try {
            // Convert byte array to Bitmap
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

            // Copy RGBA data to bitmap
            int[] pixels = new int[width * height];
            for (int i = 0; i < width * height; i++) {
                int r = data[i * 4] & 0xFF;
                int g = data[i * 4 + 1] & 0xFF;
                int b = data[i * 4 + 2] & 0xFF;
                int a = data[i * 4 + 3] & 0xFF;
                pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
            }
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);

            int[] ids = new int[1];
            GLES20.glGenTextures(1, ids, 0);
            if (ids[0] == 0) {
                Log.e(TAG, "glGenTextures returned 0");
                return 0;
            }

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0]);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

            Log.d(TAG, "Created texture: " + ids[0] + " (" + width + "x" + height + ")");
            return ids[0];

        } catch (Exception e) {
            Log.e(TAG, "Failed to load texture from data", e);
            return 0;
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
    }

    /**
     * Creates a soft glow texture (larger, softer falloff than circle).
     * Good for bloom/glow effects.
     */
    public static int createGlowTexture() {
        final int SIZE = 128;
        Bitmap bitmap = null;
        try {
            bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            // Softer, wider gradient for glow
            RadialGradient gradient = new RadialGradient(
                SIZE * 0.5f, SIZE * 0.5f, SIZE * 0.6f,
                new int[]{ Color.WHITE, Color.TRANSPARENT },
                new float[]{ 0.0f, 1.0f },
                Shader.TileMode.CLAMP
            );
            paint.setShader(gradient);
            canvas.drawCircle(SIZE * 0.5f, SIZE * 0.5f, SIZE * 0.6f, paint);

            int[] ids = new int[1];
            GLES20.glGenTextures(1, ids, 0);
            if (ids[0] == 0) return 0;

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0]);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

            return ids[0];
        } catch (Exception e) {
            Log.e(TAG, "Failed to create glow texture", e);
            return 0;
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
    }
}

