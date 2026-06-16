package com.ikechi.studio.onwa.widgets.recyclerview.v1.decoration;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.widget.RecyclingView;

import android.view.View;

/**
 * Concrete {@link ItemDecoration} that adds a configurable pixel gap after every
 * row and optionally draws a thin divider line.
 *
 * <p>Orientation-aware: in {@link RecyclingView#VERTICAL} mode the gap is added
 * to the bottom of each row; in {@link RecyclingView#HORIZONTAL} mode it is added
 * to the right.  The divider is always drawn at the trailing edge of each row.
 *
 * <p>Zero external dependencies. Zero lambda expressions. API 18+.
 */
public class SpacingDecoration extends ItemDecoration {

    private final int     mSpacingPx;
    private final boolean mDrawDivider;
    private final Paint   mDividerPaint;

    /**
     * @param spacingPx    Gap (px) to add after each item.
     * @param drawDivider  Whether to draw a 1 px divider line at the trailing edge.
     * @param dividerColor ARGB colour of the divider (ignored when drawDivider is false).
     */
    public SpacingDecoration(int spacingPx, boolean drawDivider, int dividerColor) {
        mSpacingPx    = spacingPx;
        mDrawDivider  = drawDivider;
        mDividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mDividerPaint.setColor(dividerColor);
        mDividerPaint.setStrokeWidth(1f);
    }

    @Override
    public void getItemOffsets(Rect outRect, int position, RecyclingView parent) {
        if (parent.getOrientation() == RecyclingView.HORIZONTAL) {
            outRect.right = mSpacingPx;
        } else {
            outRect.bottom = mSpacingPx;
        }
    }

    @Override
    public void onDrawOver(Canvas canvas, RecyclingView parent) {
        if (!mDrawDivider) return;

        final int childCount = parent.getChildCount();
        final boolean isHorizontal = parent.getOrientation() == RecyclingView.HORIZONTAL;

        for (int i = 0; i < childCount - 1; i++) {
            View child = parent.getChildAt(i);
            if (isHorizontal) {
                float x = child.getRight() - 1f;
                canvas.drawLine(x, 0, x, parent.getHeight(), mDividerPaint);
            } else {
                float y = child.getBottom() - 1f;
                canvas.drawLine(0, y, parent.getWidth(), y, mDividerPaint);
            }
        }
    }
}

