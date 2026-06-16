package com.ikechi.studio.onwa.widgets.recyclerview.v1.helper;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.widget.*;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.adapter.*;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.listener.*;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.decoration.*;

/**
 * Drag-and-drop reorder helper for {@link RecyclingView}.
 *
 * <h3>Initiation — long-press only</h3>
 * A drag begins <em>only</em> after the finger rests on an item for
 * {@link #LONG_PRESS_MS} without moving beyond {@code touchSlop}.
 * Any earlier movement cancels the long-press arm and returns scroll control
 * to the RecyclingView, eliminating the false-drag / scroll conflict.
 *
 * <h3>Finger-up behaviour</h3>
 * Lifting the finger <em>without</em> having dragged to a new position
 * (e.g. an accidental long-press) silently cancels the drag — no callback,
 * no reorder.  Lifting after genuinely moving the shadow to a different slot
 * commits the reorder via a short snap animation and then fires the callback.
 * This means an accidental long-press is harmless: just lift your finger.
 *
 * <h3>Live room-making animation</h3>
 * As the shadow crosses each item boundary, holders between the origin and
 * the hover target slide aside ({@code translateY} / {@code translateX}) to
 * open a visual gap.  Reversing the drag slides them back.  All translations
 * are zeroed before the callback fires.
 *
 * <h3>Auto-scroll</h3>
 * When the finger approaches the leading or trailing edge, the list scrolls
 * automatically to help reach off-screen drop targets.  Speed increases
 * proportionally to how close the finger is to the edge.
 *
 * <h3>Orientation</h3>
 * All axis-specific logic (primary / secondary, shadow placement, shift
 * direction, auto-scroll direction) is orientation-aware for both
 * {@link RecyclingView#VERTICAL} and {@link RecyclingView#HORIZONTAL}.
 *
 * <h3>Encapsulation</h3>
 * All {@link RecyclingAdapter.ViewHolder} access goes through
 * {@link RecyclingAdapter.ViewHolder#getItemView()} — no direct field access.
 *
 * <p>Zero external dependencies. Zero lambda expressions. API 18+.
 */
public class DragDropHelper {

    // ── Timing ────────────────────────────────────────────────────────────────

    /** Hold time (ms) before a drag is initiated. */
    private static final long  LONG_PRESS_MS              = 600L;

    // ── Shadow appearance ─────────────────────────────────────────────────────

    private static final float LIFT_SCALE                 = 1.04f;
    private static final float SHADOW_ALPHA               = 0.88f;
    private static final int   SHADOW_ELEV_DP             = 6;

    // ── Shift animation ───────────────────────────────────────────────────────

    private static final int   SHIFT_DURATION_MS          = 180;

    // ── Auto-scroll ───────────────────────────────────────────────────────────

    /**
     * Distance from the leading / trailing edge (px) inside which auto-scroll
     * activates.  Derived from {@link #AUTO_SCROLL_THRESHOLD_DP} at runtime.
     */
    private static final int   AUTO_SCROLL_THRESHOLD_DP   = 80;

    /** Runnable interval (ms) — roughly 60 fps. */
    private static final int   AUTO_SCROLL_INTERVAL_MS    = 16;

    /**
     * Scroll-speed multiplier.  Actual pixels per tick =
     * {@code factor × AUTO_SCROLL_SPEED × thresholdPx}, where factor ∈ [0, 1]
     * grows as the finger approaches the edge.
     */
    private static final float AUTO_SCROLL_SPEED          = 0.18f;

    // ──────────────────────────────────────────────────────────────────────────
    // Callback
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Fired when the user successfully drags an item to a new position.
     *
     * <p>The host <strong>must</strong> update the adapter data set and call
     * {@code notifyItemMoved(fromPosition, toPosition)} inside this method.
     * Only fired when {@code fromPosition != toPosition}.
     */
    public interface DragCallback {
        void onItemMoved(int fromPosition, int toPosition);
    }

    // ── Core state ────────────────────────────────────────────────────────────

    private final RecyclingView mParent;
    private final DragCallback  mCallback;
    private final int           mTouchSlop;

    private boolean mDragging    = false;
    private int     mDragFromPos = -1;   // adapter position where drag started
    private int     mDragToPos   = -1;   // adapter position currently hovered

    /** The original item view — hidden during drag; restored on end. */
    private View    mDraggedView;

    /** Captured bitmap of the dragged item used to draw the floating shadow. */
    private Bitmap  mDragBitmap;

    /** Current finger position on the primary axis (Y = VERTICAL, X = HORIZONTAL). */
    private float   mDragPrimary;

    /** Offset from the item's leading edge to the initial finger contact point. */
    private float   mDragOffsetPrimary;

    // ── Shift animators ───────────────────────────────────────────────────────

    /**
     * Running {@link ValueAnimator}s keyed by adapter position.
     * Stored so they can be cancelled and reversed when the target changes.
     */
    private final SparseArray<ValueAnimator> mShiftAnimators =
            new SparseArray<ValueAnimator>();

    // ── Auto-scroll ───────────────────────────────────────────────────────────

    /** True while the auto-scroll tick loop is posted. */
    private boolean mAutoScrolling;

    /** Direction of auto-scroll: -1 = toward leading edge, +1 = toward trailing. */
    private int     mAutoScrollDirection;

    /**
     * Last known finger position on the primary axis, updated every MOVE.
     * Read by the auto-scroll runnable which runs on a separate cadence.
     */
    private float   mLastAutoScrollPrimary;

    /** Threshold in pixels computed once in the constructor. */
    private final float mAutoScrollThresholdPx;

    /**
     * Tick loop that scrolls the list while the finger is near an edge.
     * Runs every {@link #AUTO_SCROLL_INTERVAL_MS} ms via {@code postDelayed}.
     * Stops itself when {@link #stopAutoScroll()} is called.
     */
    private final Runnable mAutoScrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mAutoScrolling || !mDragging || mDraggedView == null) return;

            boolean isV      = isVertical();
            float   viewSize = isV ? mParent.getHeight() : mParent.getWidth();
            float   primary  = mLastAutoScrollPrimary;
            float   distance;

            if (mAutoScrollDirection < 0) {
                // Near leading edge — distance from threshold to finger.
                distance = Math.max(0f, mAutoScrollThresholdPx - primary);
            } else {
                // Near trailing edge — distance from finger to threshold.
                distance = Math.max(0f, primary - (viewSize - mAutoScrollThresholdPx));
            }

            if (distance > 0f) {
                float factor      = distance / mAutoScrollThresholdPx; // 0..1
                int   scrollDelta = (int) (factor * AUTO_SCROLL_SPEED * mAutoScrollThresholdPx);
                if (scrollDelta > 0) {
                    mParent.scrollBy(mAutoScrollDirection * scrollDelta);

                    // Re-evaluate hover position after the list has moved.
                    float x = isV ? 0f       : mDragPrimary;
                    float y = isV ? mDragPrimary : 0f;
                    int hovered = mParent.getChildAdapterPosition(x, y);
                    if (hovered >= 0 && hovered != mDragToPos) {
                        updateShifts(hovered);
                    }
                    mParent.invalidate();
                }
            }

            // Reschedule until stopAutoScroll() is called.
            if (mAutoScrolling) {
                mParent.postDelayed(this, AUTO_SCROLL_INTERVAL_MS);
            }
        }
    };

    // ── Paints ────────────────────────────────────────────────────────────────

    private final Paint mShadowPaint    = new Paint(Paint.ANTI_ALIAS_FLAG
            | Paint.FILTER_BITMAP_FLAG);
    private final Paint mElevationPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ──────────────────────────────────────────────────────────────────────────
    // Constructor
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Attaches the helper to {@code parent}.
     *
     * @param parent   The RecyclingView to attach to.
     * @param callback Receives the from- and to-positions when the user drops.
     */
    public DragDropHelper(RecyclingView parent, DragCallback callback) {
        mParent               = parent;
        mCallback             = callback;
        mTouchSlop            = ViewConfiguration.get(parent.getContext()).getScaledTouchSlop();
        mAutoScrollThresholdPx = dpToPx(AUTO_SCROLL_THRESHOLD_DP);
        mElevationPaint.setColor(0x28000000);

        parent.addOnItemTouchListener(new InternalTouchListener());
        parent.addItemDecoration(new InternalDecoration());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Drag lifecycle
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Starts a drag for the item at {@code adapterPosition}.
     * Called ONLY from the long-press runnable — never from touch event code.
     */
    private void startDrag(int adapterPosition, float fingerPrimary) {
        stopAutoScroll(); // guard against any stale runnable

        RecyclingAdapter.ViewHolder h =
                mParent.findViewHolderForAdapterPosition(adapterPosition);
        if (h == null) return;

        mDraggedView       = h.getItemView();
        mDragFromPos       = adapterPosition;
        mDragToPos         = adapterPosition;
        mDragOffsetPrimary = fingerPrimary - (isVertical()
                ? mDraggedView.getTop()
                : mDraggedView.getLeft());
        mDragPrimary       = fingerPrimary;
        mDragging          = true;

        mDragBitmap = captureBitmap(mDraggedView);
        mDraggedView.setAlpha(0f); // hide original; shadow represents it
        mParent.invalidate();
    }

    /**
     * Commits the drag: snaps the shadow to the destination slot, resets all
     * state, then fires the callback.
     *
     * <p>Called from {@code ACTION_UP} only when {@code mDragToPos != mDragFromPos},
     * i.e., the user deliberately moved the item to a new position.
     */
    private void drop() {
        stopAutoScroll();
        if (!mDragging) return;

        final int  from    = mDragFromPos;
        final int  to      = mDragToPos;
        final View dragged = mDraggedView;

        RecyclingAdapter.ViewHolder dest =
                mParent.findViewHolderForAdapterPosition(to);

        float startPrimary  = mDragPrimary - mDragOffsetPrimary;
        float targetPrimary = (dest != null)
                ? (isVertical() ? dest.getItemView().getTop()
                                : dest.getItemView().getLeft())
                : startPrimary;

        final Bitmap bmp = mDragBitmap;

        ValueAnimator snap = ValueAnimator.ofFloat(startPrimary, targetPrimary);
        snap.setDuration(180);
        snap.setInterpolator(new DecelerateInterpolator(1.5f));
        snap.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                mDragPrimary = (Float) a.getAnimatedValue() + mDragOffsetPrimary;
                mParent.invalidate();
            }
        });
        snap.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (dragged != null) dragged.setAlpha(1f);
                resetAllShifts();
                if (bmp != null && !bmp.isRecycled()) bmp.recycle();
                mDragging    = false;
                mDragBitmap  = null;
                mDraggedView = null;
                mParent.invalidate();
                // Only notify if the position actually changed.
                if (from != to && mCallback != null) mCallback.onItemMoved(from, to);
            }
        });
        snap.start();
    }

    /**
     * Cancels an active drag <em>without</em> committing any reorder.
     *
     * <p>Used in two situations:
     * <ol>
     *   <li>The user lifts their finger without having moved the shadow to a
     *       new position (accidental long-press or changed mind). Lifting the
     *       finger always cancels in this case.</li>
     *   <li>Another gesture (e.g. a horizontal swipe) steals the touch stream
     *       mid-drag via a synthesised {@code ACTION_CANCEL}.</li>
     * </ol>
     *
     * <p>No snap animation is played and no {@link DragCallback} is fired.
     * The original view's alpha is restored and all shift translations are
     * immediately zeroed so the list returns to its natural layout.
     */
    private void forceCancelDrag() {
        stopAutoScroll();
        if (!mDragging) return;

        resetAllShifts();

        if (mDraggedView != null) {
            mDraggedView.setAlpha(1f);
            mDraggedView = null;
        }
        if (mDragBitmap != null && !mDragBitmap.isRecycled()) {
            mDragBitmap.recycle();
            mDragBitmap = null;
        }

        mDragging    = false;
        mDragFromPos = -1;
        mDragToPos   = -1;

        mParent.invalidate();
        // Intentionally NO callback — nothing was committed.
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Bitmap snapshot
    // ──────────────────────────────────────────────────────────────────────────

    private Bitmap captureBitmap(View view) {
        Bitmap bmp = Bitmap.createBitmap(
                Math.max(1, view.getWidth()),
                Math.max(1, view.getHeight()),
                Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bmp));
        return bmp;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Live shift animation
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Called whenever the hover target changes position.
     *
     * <p>Computes the range of items that must slide aside to make room for the
     * dragged item, starts/reverses their shift animators, and cancels animators
     * for positions that are no longer in range.
     */
    private void updateShifts(int newTarget) {
        if (newTarget == mDragToPos) return;

        boolean isV      = isVertical();
        float   itemSize = isV ? mDraggedView.getHeight()
                               : mDraggedView.getWidth();

        mDragToPos = newTarget;

        int   rangeStart;
        int   rangeEnd;
        float shiftAmount;

        if (newTarget > mDragFromPos) {
            // Shadow moved forward — items in (dragFrom, newTarget] slide back.
            rangeStart  = mDragFromPos + 1;
            rangeEnd    = newTarget;
            shiftAmount = -itemSize;
        } else {
            // Shadow moved backward — items in [newTarget, dragFrom) slide forward.
            rangeStart  = newTarget;
            rangeEnd    = mDragFromPos - 1;
            shiftAmount = +itemSize;
        }

        // Reverse any positions now outside the active range.
        for (int i = 0; i < mShiftAnimators.size(); i++) {
            int pos = mShiftAnimators.keyAt(i);
            if (pos < rangeStart || pos > rangeEnd) {
                animateShiftTo(pos, isV, 0f);
            }
        }

        // Shift every position in the active range.
        for (int pos = rangeStart; pos <= rangeEnd; pos++) {
            animateShiftTo(pos, isV, shiftAmount);
        }

        mParent.invalidate();
    }

    /**
     * Animates the holder at {@code pos} to {@code targetTranslation}.
     * Off-screen holders are removed from the animator map and skipped.
     * Cancels any running animator for this position before starting a new one.
     */
    private void animateShiftTo(final int pos, final boolean isV,
                                final float targetTranslation) {
        RecyclingAdapter.ViewHolder h =
                mParent.findViewHolderForAdapterPosition(pos);
        if (h == null) {
            // Holder is off-screen — clear the stale record.
            ValueAnimator stale = mShiftAnimators.get(pos);
            if (stale != null) stale.cancel();
            mShiftAnimators.remove(pos);
            return;
        }

        final View view = h.getItemView();

        ValueAnimator existing = mShiftAnimators.get(pos);
        if (existing != null && existing.isRunning()) existing.cancel();

        float currentTranslation = isV
                ? view.getTranslationY()
                : view.getTranslationX();

        if (Math.abs(currentTranslation - targetTranslation) < 0.5f) {
            if (targetTranslation == 0f) mShiftAnimators.remove(pos);
            return;
        }

        ValueAnimator anim = ValueAnimator.ofFloat(currentTranslation, targetTranslation);
        anim.setDuration(SHIFT_DURATION_MS);
        anim.setInterpolator(new DecelerateInterpolator(1.2f));
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float val = (Float) a.getAnimatedValue();
                if (isV) view.setTranslationY(val);
                else     view.setTranslationX(val);
            }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                if (targetTranslation == 0f) mShiftAnimators.remove(pos);
            }
        });

        mShiftAnimators.put(pos, anim);
        anim.start();
    }

    /**
     * Cancels every shift animator and immediately zeroes all translations
     * so no holder is left displaced after a drag ends.
     */
    private void resetAllShifts() {
        boolean isV = isVertical();
        for (int i = 0; i < mShiftAnimators.size(); i++) {
            ValueAnimator anim = mShiftAnimators.valueAt(i);
            if (anim != null && anim.isRunning()) anim.cancel();

            int pos = mShiftAnimators.keyAt(i);
            RecyclingAdapter.ViewHolder h =
                    mParent.findViewHolderForAdapterPosition(pos);
            if (h != null) {
                if (isV) h.getItemView().setTranslationY(0f);
                else     h.getItemView().setTranslationX(0f);
            }
        }
        mShiftAnimators.clear();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Auto-scroll
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Starts the auto-scroll tick loop in the given direction.
     *
     * @param direction -1 to scroll toward the leading edge (up / left),
     *                  +1 to scroll toward the trailing edge (down / right).
     */
    private void startAutoScroll(int direction) {
        // If already scrolling in the same direction, nothing to do.
        if (mAutoScrolling && mAutoScrollDirection == direction) return;
        mAutoScrollDirection = direction;
        if (!mAutoScrolling) {
            mAutoScrolling = true;
            // Post immediately so the first tick fires without delay.
            mParent.removeCallbacks(mAutoScrollRunnable);
            mParent.post(mAutoScrollRunnable);
        }
    }

    /**
     * Stops the auto-scroll tick loop.
     * Safe to call when not scrolling — the {@code removeCallbacks} is a no-op.
     */
    private void stopAutoScroll() {
        mAutoScrolling = false;
        mParent.removeCallbacks(mAutoScrollRunnable);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Internal touch listener
    // ──────────────────────────────────────────────────────────────────────────

    private class InternalTouchListener implements OnItemTouchListener {

        private float   mDownPrimary;
        private float   mDownSecondary;
        private boolean mWaitingForLongPress = false;
        private int     mDownPos            = -1;

        private final Handler mH = new Handler();

        /**
         * The ONLY code path that calls {@link DragDropHelper#startDrag}.
         * Fires only if the finger has not moved beyond {@code touchSlop}
         * for {@link DragDropHelper#LONG_PRESS_MS} milliseconds.
         */
        private final Runnable mLongPressRunnable = new Runnable() {
            @Override
            public void run() {
                if (mWaitingForLongPress && mDownPos >= 0) {
                    mWaitingForLongPress = false;
                    startDrag(mDownPos, mDownPrimary);
                }
            }
        };

        @Override
        public boolean onInterceptTouchEvent(RecyclingView view, MotionEvent ev) {
            boolean isV = isVertical();

            switch (ev.getActionMasked()) {

                case MotionEvent.ACTION_DOWN: {
                    mDownPrimary   = isV ? ev.getY() : ev.getX();
                    mDownSecondary = isV ? ev.getX() : ev.getY();
                    mDownPos = view.getChildAdapterPosition(ev.getX(), ev.getY());

                    if (mDownPos >= 0) {
                        mWaitingForLongPress = true;
                        mH.removeCallbacks(mLongPressRunnable);
                        mH.postDelayed(mLongPressRunnable, LONG_PRESS_MS);
                    }
                    // Never intercept on DOWN — RecyclingView must scroll freely.
                    return false;
                }

                case MotionEvent.ACTION_MOVE: {
                    // Claim the stream immediately if a drag is already live.
                    if (mDragging) return true;

                    // Cancel the long-press arm if the finger moved — user is scrolling.
                    if (mWaitingForLongPress) {
                        float movePrimary   = isV ? ev.getY() : ev.getX();
                        float moveSecondary = isV ? ev.getX() : ev.getY();
                        if (Math.abs(movePrimary   - mDownPrimary)   > mTouchSlop
                                || Math.abs(moveSecondary - mDownSecondary) > mTouchSlop) {
                            cancelLongPress();
                        }
                    }
                    return false;
                }

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    cancelLongPress();
                    // If a drag was started (long-press fired) while another gesture
                    // simultaneously claimed the stream (synthesised CANCEL from
                    // RecyclingView.onInterceptTouchEvent), cancel the drag cleanly.
                    if (mDragging) forceCancelDrag();
                    return false;
                }
            }
            return false;
        }

        @Override
        public void onTouchEvent(RecyclingView view, MotionEvent ev) {
            // Reached only after returning true from onInterceptTouchEvent,
            // which happens only while mDragging == true.
            if (!mDragging) return;

            boolean isV = isVertical();

            switch (ev.getActionMasked()) {

                case MotionEvent.ACTION_MOVE: {
                    mDragPrimary           = isV ? ev.getY() : ev.getX();
                    mLastAutoScrollPrimary = mDragPrimary;

                    // Update hover target.
                    float x       = isV ? 0f          : mDragPrimary;
                    float y       = isV ? mDragPrimary : 0f;
                    int   hovered = view.getChildAdapterPosition(x, y);
                    if (hovered >= 0 && hovered != mDragToPos) {
                        updateShifts(hovered);
                    }

                    // Manage auto-scroll based on finger proximity to edges.
                    float viewSize  = isV ? mParent.getHeight() : mParent.getWidth();
                    int   direction = 0;
                    if (mDragPrimary < mAutoScrollThresholdPx) {
                        direction = -1;
                    } else if (mDragPrimary > viewSize - mAutoScrollThresholdPx) {
                        direction = 1;
                    }

                    if (direction != 0) {
                        startAutoScroll(direction);
                    } else if (mAutoScrolling) {
                        stopAutoScroll();
                    }

                    view.invalidate();
                    break;
                }

                case MotionEvent.ACTION_UP: {
                    stopAutoScroll();
                    // ── Finger-up policy ──────────────────────────────────────
                    // If the shadow never left the original position (from == to),
                    // the user either accidentally triggered a long-press or changed
                    // their mind. Cancel silently — no reorder, no callback.
                    //
                    // If the shadow moved to a different position, the user made a
                    // deliberate reorder. Commit it with a short snap animation.
                    if (mDragFromPos == mDragToPos) {
                        forceCancelDrag();
                    } else {
                        drop();
                    }
                    break;
                }

                case MotionEvent.ACTION_CANCEL: {
                    // System-initiated cancel (multi-touch, window lost focus, etc.).
                    // Always abort — never commit on a cancel.
                    forceCancelDrag();
                    break;
                }
            }
        }

        private void cancelLongPress() {
            mWaitingForLongPress = false;
            mDownPos             = -1;
            mH.removeCallbacks(mLongPressRunnable);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ItemDecoration — shadow drawn above all children
    // ──────────────────────────────────────────────────────────────────────────

    private class InternalDecoration extends ItemDecoration {

        @Override
        public void onDrawOver(Canvas canvas, RecyclingView parent) {
            if (!mDragging || mDragBitmap == null || mDragBitmap.isRecycled()) return;

            boolean isV  = isVertical();
            float   lead = mDragPrimary - mDragOffsetPrimary;
            float   bW   = mDragBitmap.getWidth();
            float   bH   = mDragBitmap.getHeight();
            float   elev = dpToPx(SHADOW_ELEV_DP);

            // Elevation shadow rect, slightly offset below and right.
            float sLeft   = isV ? elev            : lead + elev;
            float sTop    = isV ? lead + elev      : elev;
            float sRight  = isV ? bW - elev        : lead + bW - elev;
            float sBottom = isV ? lead + bH + elev : bH - elev;
            canvas.drawRoundRect(
                    new RectF(sLeft, sTop, sRight, sBottom),
                    dpToPx(8), dpToPx(8),
                    mElevationPaint);

            // Scaled bitmap following the finger.
            float drawX = isV ? 0f   : lead;
            float drawY = isV ? lead : 0f;
            float cx    = drawX + bW / 2f;
            float cy    = drawY + bH / 2f;

            mShadowPaint.setAlpha((int) (255 * SHADOW_ALPHA));
            canvas.save();
            canvas.scale(LIFT_SCALE, LIFT_SCALE, cx, cy);
            canvas.drawBitmap(mDragBitmap, drawX, drawY, mShadowPaint);
            canvas.restore();
        }
    }

    // ── Util ──────────────────────────────────────────────────────────────────

    private boolean isVertical() {
        return mParent.getOrientation() == RecyclingView.VERTICAL;
    }

    private float dpToPx(int dp) {
        return dp * mParent.getContext().getResources().getDisplayMetrics().density;
    }
}
