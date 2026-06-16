package com.ikechi.studio.onwa.widgets.recyclerview.v1.listener;

import android.view.MotionEvent;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.widget.RecyclingView;


/**
 * Allows external helpers (e.g. {@link SwipeActionHelper}, {@link DragDropHelper})
 * to intercept touch events at the {@link RecyclingView} level before they reach
 * individual item views.
 *
 * <p>Register via {@link RecyclingView#addOnItemTouchListener(OnItemTouchListener)}.
 *
 * <p>Zero external dependencies. Zero lambda expressions. API 18+.
 */
public interface OnItemTouchListener {

    /**
     * Called while the RecyclingView is deciding whether to intercept a touch event.
     *
     * @param view The RecyclingView receiving the event.
     * @param ev   The motion event.
     * @return {@code true} to claim the event stream; {@code false} to pass it on.
     */
    boolean onInterceptTouchEvent(RecyclingView view, MotionEvent ev);

    /**
     * Called when this listener owns the event stream (after returning {@code true}
     * from {@link #onInterceptTouchEvent}).
     *
     * @param view The RecyclingView.
     * @param ev   The motion event.
     */
    void onTouchEvent(RecyclingView view, MotionEvent ev);
}

