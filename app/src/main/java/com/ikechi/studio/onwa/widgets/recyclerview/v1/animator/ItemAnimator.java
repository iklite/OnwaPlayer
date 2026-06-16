package com.ikechi.studio.onwa.widgets.recyclerview.v1.animator;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.adapter.*;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.widget.*;

/**
 * Abstract item animator.
 *
 * <p>Implement this to supply custom add / remove / move / change animations.
 * Each method receives a {@link RecyclingAdapter.ViewHolder} — use
 * {@link RecyclingAdapter.ViewHolder#getItemView()} to access the view to animate.
 * Call the {@code endAction} {@link Runnable} when the animation completes or is
 * cancelled so the RecyclingView can clean up.
 *
 * <p>The default implementation is {@link DefaultItemAnimator}.
 *
 * <p>Zero external dependencies. Zero lambda expressions. API 18+.
 */
public abstract class ItemAnimator {

    /**
     * Animate a newly inserted view appearing.
     *
     * @param holder    The ViewHolder whose view just entered the layout.
     * @param endAction Must be called when the animation ends or is cancelled.
     */
    public abstract void animateAdd(RecyclingAdapter.ViewHolder holder, Runnable endAction);

    /**
     * Animate a view that is leaving the layout.
     *
     * @param holder    The ViewHolder whose view is being removed.
     * @param endAction Must be called when the animation ends or is cancelled.
     */
    public abstract void animateRemove(RecyclingAdapter.ViewHolder holder, Runnable endAction);

    /**
     * Animate a view that has moved from one position to another in the list.
     *
     * @param holder    The ViewHolder that moved.
     * @param fromLead  Content-coordinate leading edge before the data change
     *                  (top in VERTICAL, left in HORIZONTAL).
     * @param toLead    Content-coordinate leading edge after the data change.
     * @param endAction Must be called when the animation ends or is cancelled.
     */
    public abstract void animateMove(RecyclingAdapter.ViewHolder holder,
                                     int fromLead, int toLead,
                                     Runnable endAction);

    /**
     * Animate an in-place content change (e.g. favourite-toggle).
     *
     * @param holder    The ViewHolder whose data changed.
     * @param endAction Must be called when the animation ends or is cancelled.
     */
    public abstract void animateChange(RecyclingAdapter.ViewHolder holder, Runnable endAction);

    /** Cancel all running animations immediately and reset all animated views. */
    public abstract void cancelAll();

    /** Returns {@code true} if at least one animation is currently running. */
    public abstract boolean isRunning();

    // ── Persistent pulse API ──────────────────────────────────────────────────

    /**
     * Starts a continuous breathing animation on the item identified by
     * {@code itemId}, and keeps it running until {@link #stopPulse(long)} or
     * {@link #stopAllPulses()} is called.
     *
     * <p>The animation tracks the item by ID, not by view reference, so it
     * survives recycling: if the item scrolls off-screen the pulse pauses
     * invisibly and resumes automatically when the item scrolls back.
     *
     * <p>Typical use-case: highlight a currently-playing song track, a live
     * notification badge, or any item that requires continuous visual attention.
     *
     * <p>The default implementation is a no-op.  {@link DefaultItemAnimator}
     * provides a full implementation.
     *
     * @param itemId The stable ID returned by
     *               {@link RecyclingAdapter#getItemId(int)} for the target item.
     *               The adapter <strong>must</strong> have stable IDs enabled
     *               ({@link RecyclingAdapter#setHasStableIds(boolean) setHasStableIds(true)}).
     * @param parent The {@link RecyclingView} that owns the item.
     */
    public void startPulse(long itemId, RecyclingView parent) { /* no-op default */ }

    /**
     * Stops the continuous pulse for the item with {@code itemId} and resets
     * its view to the normal (scale=1, alpha=1) state.
     *
     * <p>Safe to call when no pulse is running for {@code itemId}.
     * The default implementation is a no-op.
     *
     * @param itemId The stable ID of the item whose pulse should stop.
     */
    public void stopPulse(long itemId) { /* no-op default */ }

    /**
     * Stops all active persistent pulses and resets all affected views.
     * The default implementation is a no-op.
     */
    public void stopAllPulses() { /* no-op default */ }
}

