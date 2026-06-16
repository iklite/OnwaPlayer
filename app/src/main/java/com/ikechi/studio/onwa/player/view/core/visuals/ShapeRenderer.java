package com.ikechi.studio.onwa.player.view.core.visuals;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Utility class for rendering simple 2-D shapes with OpenGL ES 2.0 / 3.0.
 *
 * All methods are static.  Pre-allocated native buffers are reused every call
 * to avoid per-frame GC pressure.
 *
 * The shader program must already be in use (glUseProgram called) before
 * calling any method.  The uMVPMatrix uniform is set internally.
 *
 * Attribute names expected in the shader:
 *   vec3 vPosition
 *   vec4 aColor
 * Uniform names expected:
 *   mat4 uMVPMatrix
 */
public final class ShapeRenderer {

    private static final int MAX_VERTICES   = 1024;  // Increased for complex shapes
    private static final int FLOAT_SIZE     = 4;
    private static final int POSITION_COMPS = 3;
    private static final int COLOR_COMPS    = 4;

    private static final FloatBuffer vertexBuf;
    private static final FloatBuffer colorBuf;

    static {
        ByteBuffer vbb = ByteBuffer.allocateDirect(MAX_VERTICES * POSITION_COMPS * FLOAT_SIZE);
        vbb.order(ByteOrder.nativeOrder());
        vertexBuf = vbb.asFloatBuffer();

        ByteBuffer cbb = ByteBuffer.allocateDirect(MAX_VERTICES * COLOR_COMPS * FLOAT_SIZE);
        cbb.order(ByteOrder.nativeOrder());
        colorBuf = cbb.asFloatBuffer();
    }

    private ShapeRenderer() {}

    // -----------------------------------------------------------------------
    //  Internal helpers
    // -----------------------------------------------------------------------

    private static void setMVP(int program, float[] mvp) {
        if (mvp == null) return;
        int h = GLES20.glGetUniformLocation(program, "uMVPMatrix");
        if (h != -1) GLES20.glUniformMatrix4fv(h, 1, false, mvp, 0);
    }

    private static void bindAndDraw(int program, int primitiveType, int count) {
        int posLoc = GLES20.glGetAttribLocation(program, "vPosition");
        int colLoc = GLES20.glGetAttribLocation(program, "aColor");

        if (posLoc != -1) {
            GLES20.glVertexAttribPointer(posLoc, POSITION_COMPS, GLES20.GL_FLOAT, false, 0, vertexBuf);
            GLES20.glEnableVertexAttribArray(posLoc);
        }
        if (colLoc != -1) {
            GLES20.glVertexAttribPointer(colLoc, COLOR_COMPS, GLES20.GL_FLOAT, false, 0, colorBuf);
            GLES20.glEnableVertexAttribArray(colLoc);
        }

        GLES20.glDrawArrays(primitiveType, 0, count);

        if (posLoc != -1) GLES20.glDisableVertexAttribArray(posLoc);
        if (colLoc != -1) GLES20.glDisableVertexAttribArray(colLoc);
    }

    // -----------------------------------------------------------------------
    //  Public drawing API
    // -----------------------------------------------------------------------

    /**
     * Filled circle drawn as a GL_TRIANGLE_FAN.
     *
     * @param program  Active GL program.
     * @param mvp      MVP matrix (may be null to leave the uniform unchanged).
     * @param cx       Centre X in normalised device / world coordinates.
     * @param cy       Centre Y.
     * @param radius   Radius.
     * @param color    RGBA float[4].
     * @param segments Number of perimeter segments (clamped to ≥3, ≤MAX_VERTICES-2).
     */
    public static void drawCircle(int program, float[] mvp,
                                  float cx, float cy, float radius,
                                  float[] color, int segments) {
        segments = Math.max(3, Math.min(segments, MAX_VERTICES - 2));
        int vertexCount = segments + 2; // centre + perimeter + close

        setMVP(program, mvp);

        vertexBuf.clear();
        colorBuf.clear();

        // Centre
        vertexBuf.put(cx); vertexBuf.put(cy); vertexBuf.put(0f);
        colorBuf.put(color);

        for (int i = 0; i <= segments; i++) {
            double angle = 2.0 * Math.PI * i / segments;
            vertexBuf.put(cx + (float) Math.cos(angle) * radius);
            vertexBuf.put(cy + (float) Math.sin(angle) * radius);
            vertexBuf.put(0f);
            colorBuf.put(color);
        }

        vertexBuf.flip();
        colorBuf.flip();

        bindAndDraw(program, GLES20.GL_TRIANGLE_FAN, vertexCount);
    }

    /** Convenience overload – keeps backward-compat callers that pass no mvp. */
    public static void drawCircle(int program,
                                  float cx, float cy, float radius,
                                  float[] color, int segments) {
        drawCircle(program, null, cx, cy, radius, color, segments);
    }

    /**
     * Filled ellipse (oval) drawn as GL_TRIANGLE_FAN.
     * Can be rotated for angled ellipses.
     *
     * @param rx       Horizontal radius.
     * @param ry       Vertical radius.
     * @param rotation Rotation in radians (0 = no rotation).
     */
    public static void drawEllipse(int program, float[] mvp,
                                   float cx, float cy, float rx, float ry,
                                   float rotation, float[] color) {
        int segments = Math.min(64, MAX_VERTICES - 2);
        int vertexCount = segments + 2;

        setMVP(program, mvp);

        vertexBuf.clear();
        colorBuf.clear();

        // Centre
        vertexBuf.put(cx); vertexBuf.put(cy); vertexBuf.put(0f);
        colorBuf.put(color);

        float cosR = (float) Math.cos(rotation);
        float sinR = (float) Math.sin(rotation);

        for (int i = 0; i <= segments; i++) {
            double angle = 2.0 * Math.PI * i / segments;
            float localX = (float) Math.cos(angle) * rx;
            float localY = (float) Math.sin(angle) * ry;

            // Apply rotation
            float rotX = localX * cosR - localY * sinR;
            float rotY = localX * sinR + localY * cosR;

            vertexBuf.put(cx + rotX);
            vertexBuf.put(cy + rotY);
            vertexBuf.put(0f);
            colorBuf.put(color);
        }

        vertexBuf.flip();
        colorBuf.flip();

        bindAndDraw(program, GLES20.GL_TRIANGLE_FAN, vertexCount);
    }

    /** Convenience overload with no rotation. */
    public static void drawEllipse(int program, float[] mvp,
                                   float cx, float cy, float rx, float ry,
                                   float[] color) {
        drawEllipse(program, mvp, cx, cy, rx, ry, 0f, color);
    }

    /** Convenience overload with no mvp or rotation. */
    public static void drawEllipse(int program,
                                   float cx, float cy, float rx, float ry,
                                   float[] color) {
        drawEllipse(program, null, cx, cy, rx, ry, 0f, color);
    }

    /**
     * Single line segment.
     *
     * @param lineWidth Passed to glLineWidth (clamped by driver; typically 1–10).
     */
    public static void drawLine(int program, float[] mvp,
                                float x1, float y1, float x2, float y2,
                                float[] color, float lineWidth) {
        setMVP(program, mvp);

        vertexBuf.clear();
        colorBuf.clear();

        vertexBuf.put(x1); vertexBuf.put(y1); vertexBuf.put(0f); colorBuf.put(color);
        vertexBuf.put(x2); vertexBuf.put(y2); vertexBuf.put(0f); colorBuf.put(color);

        vertexBuf.flip();
        colorBuf.flip();

        GLES20.glLineWidth(lineWidth);
        bindAndDraw(program, GLES20.GL_LINES, 2);
    }

    /** Convenience overload – no mvp. */
    public static void drawLine(int program,
                                float x1, float y1, float x2, float y2,
                                float[] color, float lineWidth) {
        drawLine(program, null, x1, y1, x2, y2, color, lineWidth);
    }

    /**
     * Connected line strip (polyline).
     * Points array: [x0,y0, x1,y1, x2,y2, ...]
     *
     * @param points    Flat array of x,y pairs. Must have at least 2 points.
     * @param lineWidth Width of the line.
     */
    public static void drawLineStrip(int program, float[] mvp,
                                     float[] points, float[] color, float lineWidth) {
        if (points == null || points.length < 4 || points.length % 2 != 0) return;

        int numPoints = points.length / 2;
        if (numPoints > MAX_VERTICES) numPoints = MAX_VERTICES;

        setMVP(program, mvp);

        vertexBuf.clear();
        colorBuf.clear();

        for (int i = 0; i < numPoints; i++) {
            vertexBuf.put(points[i * 2]);
            vertexBuf.put(points[i * 2 + 1]);
            vertexBuf.put(0f);
            colorBuf.put(color);
        }

        vertexBuf.flip();
        colorBuf.flip();

        GLES20.glLineWidth(lineWidth);
        bindAndDraw(program, GLES20.GL_LINE_STRIP, numPoints);
    }

    /** Convenience overload – no mvp. */
    public static void drawLineStrip(int program,
                                     float[] points, float[] color, float lineWidth) {
        drawLineStrip(program, null, points, color, lineWidth);
    }

    /**
     * Filled convex polygon given as flat [x0,y0, x1,y1, …] array.
     * Drawn as GL_TRIANGLE_FAN from the centroid.
     */
    public static void drawPolygon(int program, float[] mvp,
                                   float[] points, float[] color) {
        int numPoints    = points.length / 2;
        int vertexCount  = numPoints + 1; // centroid + perimeter
        if (numPoints < 2 || vertexCount > MAX_VERTICES) return;

        setMVP(program, mvp);

        float cx = 0, cy = 0;
        for (int i = 0; i < points.length; i += 2) { cx += points[i]; cy += points[i + 1]; }
        cx /= numPoints; cy /= numPoints;

        vertexBuf.clear();
        colorBuf.clear();

        vertexBuf.put(cx); vertexBuf.put(cy); vertexBuf.put(0f); colorBuf.put(color);
        for (int i = 0; i < points.length; i += 2) {
            vertexBuf.put(points[i]); vertexBuf.put(points[i + 1]); vertexBuf.put(0f);
            colorBuf.put(color);
        }

        vertexBuf.flip();
        colorBuf.flip();

        bindAndDraw(program, GLES20.GL_TRIANGLE_FAN, vertexCount);
    }

    /** Convenience overload – no mvp. */
    public static void drawPolygon(int program, float[] points, float[] color) {
        drawPolygon(program, null, points, color);
    }

    /**
     * Filled quadrilateral (any four points).
     * Drawn as two triangles.
     *
     * @param x1,y1 First corner
     * @param x2,y2 Second corner  
     * @param x3,y3 Third corner
     * @param x4,y4 Fourth corner
     */
    public static void drawQuad(int program, float[] mvp,
                                float x1, float y1, float x2, float y2,
                                float x3, float y3, float x4, float y4,
                                float[] color) {
        setMVP(program, mvp);

        vertexBuf.clear();
        colorBuf.clear();

        // Triangle 1: 0,1,2
        vertexBuf.put(x1); vertexBuf.put(y1); vertexBuf.put(0f); colorBuf.put(color);
        vertexBuf.put(x2); vertexBuf.put(y2); vertexBuf.put(0f); colorBuf.put(color);
        vertexBuf.put(x3); vertexBuf.put(y3); vertexBuf.put(0f); colorBuf.put(color);

        // Triangle 2: 0,2,3
        vertexBuf.put(x1); vertexBuf.put(y1); vertexBuf.put(0f); colorBuf.put(color);
        vertexBuf.put(x3); vertexBuf.put(y3); vertexBuf.put(0f); colorBuf.put(color);
        vertexBuf.put(x4); vertexBuf.put(y4); vertexBuf.put(0f); colorBuf.put(color);

        vertexBuf.flip();
        colorBuf.flip();

        bindAndDraw(program, GLES20.GL_TRIANGLES, 6);
    }

    /** Convenience overload – no mvp. */
    public static void drawQuad(int program,
                                float x1, float y1, float x2, float y2,
                                float x3, float y3, float x4, float y4,
                                float[] color) {
        drawQuad(program, null, x1, y1, x2, y2, x3, y3, x4, y4, color);
    }

    /**
     * Arc drawn as GL_LINE_STRIP.
     *
     * @param radiusX   Horizontal radius.
     * @param radiusY   Vertical radius (use same value for circular arc).
     * @param startDeg  Start angle in degrees (0 = +X axis).
     * @param endDeg    End angle in degrees.
     */
    public static void drawArc(int program, float[] mvp,
                               float cx, float cy, float radiusX, float radiusY,
                               float startDeg, float endDeg,
                               float[] color, float lineWidth) {
        int segments    = Math.min(60, MAX_VERTICES - 1);
        int vertexCount = segments + 1;

        setMVP(program, mvp);

        double startRad = Math.toRadians(startDeg);
        double endRad   = Math.toRadians(endDeg);
        double step     = (endRad - startRad) / segments;

        vertexBuf.clear();
        colorBuf.clear();

        for (int i = 0; i <= segments; i++) {
            double a = startRad + i * step;
            vertexBuf.put(cx + (float) Math.cos(a) * radiusX);
            vertexBuf.put(cy + (float) Math.sin(a) * radiusY);
            vertexBuf.put(0f);
            colorBuf.put(color);
        }

        vertexBuf.flip();
        colorBuf.flip();

        GLES20.glLineWidth(lineWidth);
        bindAndDraw(program, GLES20.GL_LINE_STRIP, vertexCount);
    }

    /** Convenience overload – no mvp. */
    public static void drawArc(int program,
                               float cx, float cy, float radiusX, float radiusY,
                               float startDeg, float endDeg,
                               float[] color, float lineWidth) {
        drawArc(program, null, cx, cy, radiusX, radiusY, startDeg, endDeg, color, lineWidth);
    }

    /**
     * Filled star drawn as GL_TRIANGLE_FAN.
     *
     * @param points      Number of star points (clamped to ≥3).
     * @param rotationDeg Initial rotation offset in degrees.
     */
    public static void drawStar(int program, float[] mvp,
                                float cx, float cy,
                                float outerRadius, float innerRadius,
                                int points, float rotationDeg, float[] color) {
        points = Math.max(3, Math.min(points, (MAX_VERTICES - 1) / 2));
        int vertexCount = points * 2 + 1; // centre + alternating outer/inner

        setMVP(program, mvp);

        double startAngle = Math.toRadians(rotationDeg);
        double step       = Math.PI / points;

        vertexBuf.clear();
        colorBuf.clear();

        vertexBuf.put(cx); vertexBuf.put(cy); vertexBuf.put(0f); colorBuf.put(color);

        for (int i = 0; i < points; i++) {
            double outerA = startAngle + 2 * i * step;
            vertexBuf.put(cx + (float) Math.cos(outerA) * outerRadius);
            vertexBuf.put(cy + (float) Math.sin(outerA) * outerRadius);
            vertexBuf.put(0f); colorBuf.put(color);

            double innerA = outerA + step;
            vertexBuf.put(cx + (float) Math.cos(innerA) * innerRadius);
            vertexBuf.put(cy + (float) Math.sin(innerA) * innerRadius);
            vertexBuf.put(0f); colorBuf.put(color);
        }

        vertexBuf.flip();
        colorBuf.flip();

        bindAndDraw(program, GLES20.GL_TRIANGLE_FAN, vertexCount);
    }

    /** Convenience overload – no mvp. */
    public static void drawStar(int program,
                                float cx, float cy,
                                float outerRadius, float innerRadius,
                                int points, float rotationDeg, float[] color) {
        drawStar(program, null, cx, cy, outerRadius, innerRadius, points, rotationDeg, color);
    }

    /**
     * Filled rectangle aligned with axes.
     *
     * @param left   Left edge X.
     * @param top    Top edge Y.
     * @param right  Right edge X.
     * @param bottom Bottom edge Y.
     */
    public static void drawRect(int program, float[] mvp,
                                float left, float top, float right, float bottom,
                                float[] color) {
        drawQuad(program, mvp, left, top, right, top, right, bottom, left, bottom, color);
    }

    /** Convenience overload – no mvp. */
    public static void drawRect(int program,
                                float left, float top, float right, float bottom,
                                float[] color) {
        drawRect(program, null, left, top, right, bottom, color);
    }

    /**
     * Hollow rectangle (border only).
     *
     * @param lineWidth Width of the border lines.
     */
    public static void drawRectOutline(int program, float[] mvp,
                                       float left, float top, float right, float bottom,
                                       float[] color, float lineWidth) {
        setMVP(program, mvp);

        vertexBuf.clear();
        colorBuf.clear();

        // Four corners connected
        vertexBuf.put(left);  vertexBuf.put(top);    vertexBuf.put(0f); colorBuf.put(color);
        vertexBuf.put(right); vertexBuf.put(top);    vertexBuf.put(0f); colorBuf.put(color);
        vertexBuf.put(right); vertexBuf.put(bottom); vertexBuf.put(0f); colorBuf.put(color);
        vertexBuf.put(left);  vertexBuf.put(bottom); vertexBuf.put(0f); colorBuf.put(color);
        vertexBuf.put(left);  vertexBuf.put(top);    vertexBuf.put(0f); colorBuf.put(color);

        vertexBuf.flip();
        colorBuf.flip();

        GLES20.glLineWidth(lineWidth);
        bindAndDraw(program, GLES20.GL_LINE_STRIP, 5);
    }

    /** Convenience overload – no mvp. */
    public static void drawRectOutline(int program,
                                       float left, float top, float right, float bottom,
                                       float[] color, float lineWidth) {
        drawRectOutline(program, null, left, top, right, bottom, color, lineWidth);
    }

    /**
     * Bezier curve drawn as line strip.
     * Quadratic Bezier: start, control, end.
     *
     * @param segments  Number of line segments to approximate curve.
     */
    public static void drawBezierQuad(int program, float[] mvp,
                                      float x0, float y0, float cx, float cy, float x1, float y1,
                                      float[] color, float lineWidth, int segments) {
        segments = Math.max(2, Math.min(segments, MAX_VERTICES - 1));

        setMVP(program, mvp);

        vertexBuf.clear();
        colorBuf.clear();

        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            float mt = 1 - t;
            float x = mt * mt * x0 + 2 * mt * t * cx + t * t * x1;
            float y = mt * mt * y0 + 2 * mt * t * cy + t * t * y1;

            vertexBuf.put(x);
            vertexBuf.put(y);
            vertexBuf.put(0f);
            colorBuf.put(color);
        }

        vertexBuf.flip();
        colorBuf.flip();

        GLES20.glLineWidth(lineWidth);
        bindAndDraw(program, GLES20.GL_LINE_STRIP, segments + 1);
    }

    /** Convenience overload – no mvp. */
    public static void drawBezierQuad(int program,
                                      float x0, float y0, float cx, float cy, float x1, float y1,
                                      float[] color, float lineWidth, int segments) {
        drawBezierQuad(program, null, x0, y0, cx, cy, x1, y1, color, lineWidth, segments);
    }

    /**
     * Cubic Bezier curve: start, control1, control2, end.
     */
    public static void drawBezierCubic(int program, float[] mvp,
                                       float x0, float y0, float cx1, float cy1,
                                       float cx2, float cy2, float x1, float y1,
                                       float[] color, float lineWidth, int segments) {
        segments = Math.max(2, Math.min(segments, MAX_VERTICES - 1));

        setMVP(program, mvp);

        vertexBuf.clear();
        colorBuf.clear();

        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            float mt = 1 - t;
            float mt2 = mt * mt;
            float mt3 = mt2 * mt;
            float t2 = t * t;
            float t3 = t2 * t;

            float x = mt3 * x0 + 3 * mt2 * t * cx1 + 3 * mt * t2 * cx2 + t3 * x1;
            float y = mt3 * y0 + 3 * mt2 * t * cy1 + 3 * mt * t2 * cy2 + t3 * y1;

            vertexBuf.put(x);
            vertexBuf.put(y);
            vertexBuf.put(0f);
            colorBuf.put(color);
        }

        vertexBuf.flip();
        colorBuf.flip();

        GLES20.glLineWidth(lineWidth);
        bindAndDraw(program, GLES20.GL_LINE_STRIP, segments + 1);
    }

    /** Convenience overload – no mvp. */
    public static void drawBezierCubic(int program,
                                       float x0, float y0, float cx1, float cy1,
                                       float cx2, float cy2, float x1, float y1,
                                       float[] color, float lineWidth, int segments) {
        drawBezierCubic(program, null, x0, y0, cx1, cy1, cx2, cy2, x1, y1, color, lineWidth, segments);
    }

    /**
     * Gradient quad with per-vertex colors.
     * Colors: c1 (top-left), c2 (top-right), c3 (bottom-right), c4 (bottom-left).
     */
    public static void drawGradientQuad(int program, float[] mvp,
                                        float x1, float y1, float x2, float y2,
                                        float x3, float y3, float x4, float y4,
                                        float[] c1, float[] c2, float[] c3, float[] c4) {
        setMVP(program, mvp);

        vertexBuf.clear();

        // Triangle 1: 0,1,2
        vertexBuf.put(x1); vertexBuf.put(y1); vertexBuf.put(0f);
        vertexBuf.put(x2); vertexBuf.put(y2); vertexBuf.put(0f);
        vertexBuf.put(x3); vertexBuf.put(y3); vertexBuf.put(0f);

        // Triangle 2: 0,2,3
        vertexBuf.put(x1); vertexBuf.put(y1); vertexBuf.put(0f);
        vertexBuf.put(x3); vertexBuf.put(y3); vertexBuf.put(0f);
        vertexBuf.put(x4); vertexBuf.put(y4); vertexBuf.put(0f);

        vertexBuf.flip();

        // For gradient, we need to handle colors differently - use color array directly
        int posLoc = GLES20.glGetAttribLocation(program, "vPosition");
        int colLoc = GLES20.glGetAttribLocation(program, "aColor");

        if (posLoc != -1) {
            GLES20.glVertexAttribPointer(posLoc, POSITION_COMPS, GLES20.GL_FLOAT, false, 0, vertexBuf);
            GLES20.glEnableVertexAttribArray(posLoc);
        }

        if (colLoc != -1) {
            colorBuf.clear();
            colorBuf.put(c1); colorBuf.put(c2); colorBuf.put(c3);
            colorBuf.put(c1); colorBuf.put(c3); colorBuf.put(c4);
            colorBuf.flip();
            GLES20.glVertexAttribPointer(colLoc, COLOR_COMPS, GLES20.GL_FLOAT, false, 0, colorBuf);
            GLES20.glEnableVertexAttribArray(colLoc);
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);

        if (posLoc != -1) GLES20.glDisableVertexAttribArray(posLoc);
        if (colLoc != -1) GLES20.glDisableVertexAttribArray(colLoc);
    }
}

