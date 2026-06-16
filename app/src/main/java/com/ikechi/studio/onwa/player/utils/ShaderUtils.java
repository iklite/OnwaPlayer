package com.ikechi.studio.onwa.player.utils;

import android.content.Context;
import android.opengl.GLES20;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public final class ShaderUtils {

    private static final String TAG = "ShaderUtils";

    private ShaderUtils() {}

    /**
     * Reads a shader source file from assets.
     *
     * @param context  Application context.
     * @param filename Path relative to the assets root, e.g. "shaders/vertex_2d.glsl".
     * @return The full source as a String.
     * @throws RuntimeException if the file cannot be read.
     */
    public static String loadShaderSource(Context context, String filename) {
        StringBuilder sb = new StringBuilder();
        InputStream is = null;
        BufferedReader reader = null;
        try {
            is     = context.getAssets().open(filename);
            reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not read shader file: " + filename, e);
            throw new RuntimeException("Could not read shader file: " + filename, e);
        } finally {
            if (reader != null) try { reader.close(); } catch (IOException ignored) {}
            if (is     != null) try { is.close();     } catch (IOException ignored) {}
        }
        return sb.toString();
    }

    /**
     * Compiles and links a GLSL program.
     *
     * @param vertexSource   GLSL ES 3.00 vertex shader source.
     * @param fragmentSource GLSL ES 3.00 fragment shader source.
     * @return Non-zero GL program ID.
     * @throws RuntimeException if compilation or linking fails.
     */
    public static int createProgram(String vertexSource, String fragmentSource) {
        int vs = compileShader(GLES20.GL_VERTEX_SHADER,   vertexSource);
        int fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);

        int program = GLES20.glCreateProgram();
        if (program == 0) {
            GLES20.glDeleteShader(vs);
            GLES20.glDeleteShader(fs);
            throw new RuntimeException("glCreateProgram returned 0");
        }

        GLES20.glAttachShader(program, vs);
        GLES20.glAttachShader(program, fs);
        GLES20.glLinkProgram(program);

        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            String info = GLES20.glGetProgramInfoLog(program);
            GLES20.glDeleteProgram(program);
            GLES20.glDeleteShader(vs);
            GLES20.glDeleteShader(fs);
            throw new RuntimeException("Program link failed:\n" + info);
        }

        // Shaders are no longer needed once linked
        GLES20.glDetachShader(program, vs);
        GLES20.glDetachShader(program, fs);
        GLES20.glDeleteShader(vs);
        GLES20.glDeleteShader(fs);

        return program;
    }

    private static int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        if (shader == 0) throw new RuntimeException("glCreateShader returned 0 for type " + type);

        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);

        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            String info = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            String typeName = (type == GLES20.GL_VERTEX_SHADER) ? "vertex" : "fragment";
            throw new RuntimeException(typeName + " shader compilation failed:\n" + info);
        }

        return shader;
    }
}

