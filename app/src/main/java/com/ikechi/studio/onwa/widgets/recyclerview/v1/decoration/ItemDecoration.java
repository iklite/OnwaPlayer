package com.ikechi.studio.onwa.widgets.recyclerview.v1.decoration;

import android.graphics.Canvas;
import android.graphics.Rect;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.widget.*;


/**
 * Abstract base for item decorations.
 *
 * <p>Decorations let you draw visuals (dividers, backgrounds, badges) around
 * items and inject extra spacing — without touching the item views themselves.
 *
 * <p>Zero external dependencies. Zero lambda expressions. API 18+.
 */
public abstract class ItemDecoration {

    /**
     * Draw decorations <em>behind</em> the child views (called before
     * {@link android.view.ViewGroup#dispatchDraw}).
     *
     * @param canvas The RecyclingView's canvas.
     * @param parent The RecyclingView.
     */
    public void onDraw(Canvas canvas, RecyclingView parent) {}

    /**
     * Draw decorations <em>on top of</em> the child views (called after
     * {@link android.view.ViewGroup#dispatchDraw}).
     *
     * @param canvas The RecyclingView's canvas.
     * @param parent The RecyclingView.
     */
    public void onDrawOver(Canvas canvas, RecyclingView parent) {}

    /**
     * Supply additional spacing around the item at {@code position}.
     * The RecyclingView adds these insets to the measured size of each row.
     *
     * @param outRect  Populate with (left, top, right, bottom) insets in pixels.
     * @param position The adapter position of the item.
     * @param parent   The RecyclingView.
     */
    public void getItemOffsets(Rect outRect, int position, RecyclingView parent) {}
}

