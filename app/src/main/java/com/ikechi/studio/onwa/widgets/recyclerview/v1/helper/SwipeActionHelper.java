package com.ikechi.studio.onwa.widgets.recyclerview.v1.helper;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.widget.*;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.listener.*;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.decoration.*;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.adapter.*;

/**
 * Swipe-action helper for a <em>vertical</em> {@link RecyclingView}.
 *
 * <h3>Behaviour</h3>
 * <ul>
 *   <li>Swipe <b>left</b>  → red  DELETE  action revealed; full swipe dismisses.</li>
 *   <li>Swipe <b>right</b> → green FAVOURITE action revealed; full swipe commits.</li>
 *   <li>Release below threshold → spring back.</li>
 * </ul>
 *
 * <h3>Encapsulation</h3>
 * All access to the {@link RecyclingAdapter.ViewHolder} goes through
 * {@link RecyclingAdapter.ViewHolder#getItemView()} — no direct field access.
 *
 * <p>Zero external dependencies. Zero lambda expressions. API 18+.
 */
public class SwipeActionHelper {

    // ── Thresholds ────────────────────────────────────────────────────────────
    private static final float THRESHOLD_COMMIT = 0.55f;

    // ── Colours ───────────────────────────────────────────────────────────────
    private static final int COLOR_DELETE    = 0xFFE53935;
    private static final int COLOR_FAVOURITE = 0xFF43A047;
    private static final int COLOR_LABEL     = 0xFFFFFFFF;

    // ──────────────────────────────────────────────────────────────────────────
    // Callback
    // ──────────────────────────────────────────────────────────────────────────

    /** Fired when a swipe action is committed by the user. */
    public interface SwipeCallback {
        /** The item at {@code adapterPosition} was fully swiped left (delete intent). */
        void onSwipeLeft(int adapterPosition);
        /** The item at {@code adapterPosition} was fully swiped right (favourite intent). */
        void onSwipeRight(int adapterPosition);
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private final RecyclingView mParent;
    private final SwipeCallback mCallback;

    private int     mSwipingPos  = -1;
    private View    mSwipingView;        // obtained via getItemView() — stored as View
    private float   mSwipeStartX;
    private float   mCurrentDx;
    private boolean mSwiping;

    private VelocityTracker mVelocity;
    private final int       mTouchSlop;

    // ── Paints ────────────────────────────────────────────────────────────────

    private final Paint mBgPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * Attaches the helper to {@code parent}.
     *
     * @param parent   The RecyclingView to attach to.
     * @param callback Receives swipe-left and swipe-right commit events.
     */
    public SwipeActionHelper(RecyclingView parent, SwipeCallback callback) {
        mParent   = parent;
        mCallback = callback;

        mTouchSlop = ViewConfiguration.get(parent.getContext()).getScaledTouchSlop();

        mLabelPaint.setColor(COLOR_LABEL);
        mLabelPaint.setTextSize(dpToPx(13));
        mLabelPaint.setTextAlign(Paint.Align.CENTER);
        mLabelPaint.setFakeBoldText(true);

        parent.addOnItemTouchListener(new InternalTouchListener());
        parent.addItemDecoration(new InternalDecoration());
    }

    // ── Internal touch listener ───────────────────────────────────────────────

    private class InternalTouchListener implements OnItemTouchListener {

        private float mDownX, mDownY;

        @Override
        public boolean onInterceptTouchEvent(RecyclingView view, MotionEvent ev) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mDownX = ev.getX();
                    mDownY = ev.getY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    float dx = Math.abs(ev.getX() - mDownX);
                    float dy = Math.abs(ev.getY() - mDownY);
                    if (dx > mTouchSlop && dx > dy * 2f) {
                        beginSwipe(ev);
                        return mSwiping;
                    }
                    break;
            }
            return false;
        }

        @Override
        public void onTouchEvent(RecyclingView view, MotionEvent ev) {
            handleSwipeEvent(ev);
        }
    }

    // ── Swipe state machine ───────────────────────────────────────────────────

    private void beginSwipe(MotionEvent ev) {
        int pos = mParent.getChildAdapterPosition(ev.getX(), ev.getY());
        if (pos < 0) return;
        RecyclingAdapter.ViewHolder h = mParent.findViewHolderForAdapterPosition(pos);
        if (h == null) return;

        mSwipingPos  = pos;
        // Access the view exclusively through the public getItemView() method.
        mSwipingView = h.getItemView();
        mSwipeStartX = ev.getX();
        mCurrentDx   = 0f;
        mSwiping     = true;

        if (mVelocity == null) mVelocity = VelocityTracker.obtain();
        else mVelocity.clear();
        mVelocity.addMovement(ev);
    }

    private void handleSwipeEvent(MotionEvent ev) {
        if (!mSwiping || mSwipingView == null) return;
        if (mVelocity != null) mVelocity.addMovement(ev);

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                mCurrentDx = ev.getX() - mSwipeStartX;
                mSwipingView.setTranslationX(mCurrentDx);
                mParent.invalidate();
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                float fraction = Math.abs(mCurrentDx) / mSwipingView.getWidth();
                boolean isLeft = mCurrentDx < 0;
                if (ev.getActionMasked() == MotionEvent.ACTION_UP
                        && fraction > THRESHOLD_COMMIT) {
                    if (isLeft) dismissLeft(); else commitRight();
                } else {
                    springBack();
                }
                break;
            }
        }
    }

    // ── Animations ────────────────────────────────────────────────────────────

    private void dismissLeft() {
        final int   pos  = mSwipingPos;
        final View  view = mSwipingView;
        final float to   = -view.getWidth();

        ValueAnimator anim = ValueAnimator.ofFloat(mCurrentDx, to);
        anim.setDuration(220);
        anim.setInterpolator(new AccelerateInterpolator(1.5f));
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float v = (Float) a.getAnimatedValue();
                view.setTranslationX(v);
                view.setAlpha(1f - Math.abs(v) / view.getWidth());
                mParent.invalidate();
            }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                // The view is now fully off-screen (translationX ≈ -width, alpha ≈ 0).
                // DO NOT snap it back to translationX=0 / alpha=1 here.
                // Doing so causes the item to visibly flash back into its slot before
                // the adapter's notifyItemRemoved / animateRemove sequence can hide it,
                // making it look like the item "did not disappear".
                //
                // Instead we leave the view in its dismissed state.  DefaultItemAnimator
                // detects that the view is already invisible and skips the redundant
                // remove animation, jumping straight to removeView() and pool cleanup.
                resetSwipe();
                if (mCallback != null) mCallback.onSwipeLeft(pos);
            }
        });
        anim.start();
    }

    private void commitRight() {
        final int   pos  = mSwipingPos;
        final View  view = mSwipingView;
        final float from = mCurrentDx;

        ValueAnimator anim = ValueAnimator.ofFloat(from, 0f);
        anim.setDuration(280);
        anim.setInterpolator(new DecelerateInterpolator(1.5f));
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                view.setTranslationX((Float) a.getAnimatedValue());
                mParent.invalidate();
            }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                resetSwipe();
                if (mCallback != null) mCallback.onSwipeRight(pos);
            }
        });
        anim.start();
    }

    private void springBack() {
        if (mSwipingView == null) return;
        final View  view = mSwipingView;
        final float from = mCurrentDx;

        ValueAnimator anim = ValueAnimator.ofFloat(from, 0f);
        anim.setDuration(300);
        anim.setInterpolator(new DecelerateInterpolator(2f));
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                view.setTranslationX((Float) a.getAnimatedValue());
                mParent.invalidate();
            }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                view.setTranslationX(0f);
                resetSwipe();
            }
        });
        anim.start();
    }

    private void resetSwipe() {
        mSwiping     = false;
        mSwipingPos  = -1;
        mSwipingView = null;
        mCurrentDx   = 0f;
        if (mVelocity != null) { mVelocity.recycle(); mVelocity = null; }
    }

    // ── Decoration — draws the coloured action background ─────────────────────

    private class InternalDecoration extends ItemDecoration {
        @Override
        public void onDraw(Canvas canvas, RecyclingView parent) {
            if (!mSwiping || mSwipingView == null || Math.abs(mCurrentDx) < 1f) return;

            boolean isLeft = mCurrentDx < 0;
            float   top    = mSwipingView.getTop();
            float   bottom = mSwipingView.getBottom();
            float   w      = parent.getWidth();
            float   r      = dpToPx(8);

            if (isLeft) {
                // ── Delete strip (revealed on the right as item slides left) ──
                // The red strip spans from (w + mCurrentDx) to w.
                // mCurrentDx is negative during a left swipe, so the strip grows
                // from the right edge leftward as the finger moves.
                mBgPaint.setColor(COLOR_DELETE);
                canvas.drawRoundRect(new RectF(w + mCurrentDx, top, w, bottom), r, r, mBgPaint);
                // Centre the label inside the revealed strip, exactly as FAV does
                // on the other side.  Strip centre = w + mCurrentDx / 2  (mCurrentDx < 0).
                canvas.drawText("DELETE", w + mCurrentDx / 2f, (top + bottom) / 2f + dpToPx(5), mLabelPaint);
            } else {
                // ── Favourite strip (revealed on the left as item slides right) ─
                // The green strip spans from 0 to mCurrentDx.
                mBgPaint.setColor(COLOR_FAVOURITE);
                canvas.drawRoundRect(new RectF(0, top, mCurrentDx, bottom), r, r, mBgPaint);
                // Centre the label inside the revealed strip.  Strip centre = mCurrentDx / 2.
                canvas.drawText("★ FAV", mCurrentDx / 2f, (top + bottom) / 2f + dpToPx(5), mLabelPaint);
            }
        }
    }

    // ── Util ──────────────────────────────────────────────────────────────────

    private float dpToPx(int dp) {
        return dp * mParent.getContext().getResources().getDisplayMetrics().density;
    }
}
