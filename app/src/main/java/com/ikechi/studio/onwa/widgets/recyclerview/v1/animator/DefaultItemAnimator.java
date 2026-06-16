package com.ikechi.studio.onwa.widgets.recyclerview.v1.animator;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.util.LongSparseArray;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import java.util.ArrayList;
import java.util.List;

import com.ikechi.studio.onwa.widgets.recyclerview.v1.adapter.RecyclingAdapter;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.widget.*;

/**
 * Default {@link ItemAnimator} implementation.
 *
 * <h3>Animation catalogue</h3>
 * <ul>
 *   <li><b>ADD</b>    — orientation-aware slide + fade-in + scale, overshoot settle, 380 ms.</li>
 *   <li><b>REMOVE</b> — orientation-aware slide + fade-out, accelerate, 280 ms.</li>
 *   <li><b>MOVE</b>   — translate along the primary axis, 260 ms.</li>
 *   <li><b>CHANGE</b> — scale-pulse + alpha flash, <em>ID-safe</em>, 220 ms.</li>
 *   <li><b>PULSE</b>  — continuous breathing loop, survives recycling, indefinite.</li>
 * </ul>
 *
 * <h3>Why CHANGE and PULSE are ID-safe</h3>
 * {@link ValueAnimator} callbacks fire asynchronously.  Between the call to
 * {@link #animateChange} and the first tick, RecyclingView may have recycled
 * the original holder — the same {@link View} reference now shows a completely
 * different item.  Animating that view produces the "wrong item pulses" bug.
 *
 * <p>The fix: capture the holder's <em>stable item ID</em> (stamped by
 * RecyclingView after every {@code onBindViewHolder} call) at the moment the
 * animation starts, then on every tick call
 * {@link RecyclingView#findViewHolderForItemId} to get whichever holder
 * <em>currently</em> shows that item.  If the item is off-screen the tick is
 * a silent no-op; when it scrolls back the correct view is found automatically.
 *
 * <p>Falls back to the captured {@link View} reference when the adapter does
 * not use stable IDs ({@code itemId == NO_ID}) for backwards compatibility.
 *
 * <h3>Pulse API (via RecyclingView)</h3>
 * <pre>{@code
 * adapter.setHasStableIds(true);           // once, in adapter setup
 *
 * recyclingView.pulseItemId(track.getId());        // start
 * recyclingView.stopPulseItemId(track.getId());    // stop one
 * recyclingView.stopAllPulses();                   // stop all
 * }</pre>
 *
 * <p>Zero external dependencies. Zero lambda expressions. API 18+.
 */
public class DefaultItemAnimator extends ItemAnimator {

    // ── Durations ─────────────────────────────────────────────────────────────

    private static final int   DURATION_ADD         = 380;
    private static final int   DURATION_REMOVE      = 280;
    private static final int   DURATION_MOVE        = 260;
    private static final int   DURATION_CHANGE      = 220;
    private static final float SLIDE_OFFSET_DP      = 60f;

    /** One full breathe cycle (expand → contract) in milliseconds. */
    private static final int   DURATION_PULSE_CYCLE = 1100;

    // ── Running-animation registries ──────────────────────────────────────────

    /** Transient one-shot animations (add / remove / move / change). */
    private final List<Animator> mRunning = new ArrayList<Animator>();

    /**
     * Persistent pulse animators keyed by stable item ID.
     * {@link LongSparseArray} is available from API 16 (min SDK is 18).
     */
    private final LongSparseArray<ValueAnimator> mPulseAnimators =
	new LongSparseArray<ValueAnimator>();

    // ── ADD ───────────────────────────────────────────────────────────────────

    @Override
    public void animateAdd(final RecyclingAdapter.ViewHolder holder, final Runnable endAction) {
        final View v          = holder.getItemView();
        final boolean isVert  = isVertical(v);
        final float slideFrom = dpToPx(v, SLIDE_OFFSET_DP);

        v.setAlpha(0f);
        if (isVert) v.setTranslationY(slideFrom);
        else        v.setTranslationX(slideFrom);
        v.setScaleX(0.92f);
        v.setScaleY(0.92f);

        ObjectAnimator alpha  = ObjectAnimator.ofFloat(v, "alpha", 0f, 1f);
        ObjectAnimator trans  = isVert
			? ObjectAnimator.ofFloat(v, "translationY", slideFrom, 0f)
			: ObjectAnimator.ofFloat(v, "translationX", slideFrom, 0f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(v, "scaleX", 0.92f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(v, "scaleY", 0.92f, 1f);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(alpha, trans, scaleX, scaleY);
        set.setDuration(DURATION_ADD);
        set.setInterpolator(new OvershootInterpolator(1.1f));
        set.addListener(new AnimatorListenerAdapter() {
				@Override public void onAnimationEnd(Animator a) {
					resetView(v); mRunning.remove(a);
					if (endAction != null) endAction.run();
				}
				@Override public void onAnimationCancel(Animator a) {
					resetView(v); mRunning.remove(a);
				}
			});
        mRunning.add(set);
        set.start();
    }

    // ── REMOVE ────────────────────────────────────────────────────────────────

    @Override
    public void animateRemove(final RecyclingAdapter.ViewHolder holder, final Runnable endAction) {
        final View v         = holder.getItemView();
        final boolean isVert = isVertical(v);
        final float target   = isVert ? v.getHeight() : v.getWidth();

        ObjectAnimator alpha = ObjectAnimator.ofFloat(v, "alpha", 1f, 0f);
        ObjectAnimator trans = isVert
			? ObjectAnimator.ofFloat(v, "translationY", 0f, target)
			: ObjectAnimator.ofFloat(v, "translationX", 0f, target);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(alpha, trans);
        set.setDuration(DURATION_REMOVE);
        set.setInterpolator(new AccelerateInterpolator(1.5f));
        set.addListener(new AnimatorListenerAdapter() {
				@Override public void onAnimationEnd(Animator a) {
					resetView(v); mRunning.remove(a);
					if (endAction != null) endAction.run();
				}
				@Override public void onAnimationCancel(Animator a) {
					resetView(v); mRunning.remove(a);
				}
			});
        mRunning.add(set);
        set.start();
    }

    // ── MOVE ──────────────────────────────────────────────────────────────────

    @Override
    public void animateMove(final RecyclingAdapter.ViewHolder holder,
                            int fromLead, int toLead, final Runnable endAction) {
        final View v         = holder.getItemView();
        final boolean isVert = isVertical(v);
        final int delta      = fromLead - toLead;

        if (isVert) {
            v.setTranslationY(delta);
            ObjectAnimator anim = ObjectAnimator.ofFloat(v, "translationY", delta, 0f);
            anim.setDuration(DURATION_MOVE);
            anim.setInterpolator(new DecelerateInterpolator(1.5f));
            anim.addListener(new AnimatorListenerAdapter() {
					@Override public void onAnimationEnd(Animator a) {
						v.setTranslationY(0f); mRunning.remove(a);
						if (endAction != null) endAction.run();
					}
					@Override public void onAnimationCancel(Animator a) {
						v.setTranslationY(0f); mRunning.remove(a);
					}
				});
            mRunning.add(anim);
            anim.start();
        } else {
            v.setTranslationX(delta);
            ObjectAnimator anim = ObjectAnimator.ofFloat(v, "translationX", delta, 0f);
            anim.setDuration(DURATION_MOVE);
            anim.setInterpolator(new DecelerateInterpolator(1.5f));
            anim.addListener(new AnimatorListenerAdapter() {
					@Override public void onAnimationEnd(Animator a) {
						v.setTranslationX(0f); mRunning.remove(a);
						if (endAction != null) endAction.run();
					}
					@Override public void onAnimationCancel(Animator a) {
						v.setTranslationX(0f); mRunning.remove(a);
					}
				});
            mRunning.add(anim);
            anim.start();
        }
    }

    // ── CHANGE (ID-safe) ──────────────────────────────────────────────────────

    /**
     * Plays a single scale+alpha pulse for the item currently shown by {@code holder}.
     *
     * <p>The stable item ID is captured at call time.  Every animation tick
     * resolves the <em>current</em> view for that ID via
     * {@link RecyclingView#findViewHolderForItemId}, so the pulse always lands
     * on the correct item even if the holder has been recycled mid-animation.
     */
    @Override
    public void animateChange(final RecyclingAdapter.ViewHolder holder, final Runnable endAction) {
        final View          capturedView = holder.getItemView();
        final long          itemId       = holder.getBoundItemId();
        final RecyclingView parent       = parentOf(capturedView);

        ValueAnimator pulse = ValueAnimator.ofFloat(0f, 1f);
        pulse.setDuration(DURATION_CHANGE);
        pulse.setInterpolator(new DecelerateInterpolator());

        pulse.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
				@Override
				public void onAnimationUpdate(ValueAnimator anim) {
					View target = resolveView(parent, itemId, capturedView);
					if (target == null) return; // item off-screen — skip tick

					float t     = (Float) anim.getAnimatedValue();
					float scale = t < 0.5f
                        ? 1f - 0.08f * (t / 0.5f)
                        : 0.92f + 0.08f * ((t - 0.5f) / 0.5f);
					float alpha = t < 0.5f
                        ? 1f - 0.30f * (t / 0.5f)
                        : 0.70f + 0.30f * ((t - 0.5f) / 0.5f);
					target.setScaleX(scale);
					target.setScaleY(scale);
					target.setAlpha(alpha);
				}
			});

        pulse.addListener(new AnimatorListenerAdapter() {
				@Override public void onAnimationEnd(Animator a) {
					View target = resolveView(parent, itemId, capturedView);
					if (target != null) resetView(target);
					mRunning.remove(a);
					if (endAction != null) endAction.run();
				}
				@Override public void onAnimationCancel(Animator a) {
					View target = resolveView(parent, itemId, capturedView);
					if (target != null) resetView(target);
					mRunning.remove(a);
				}
			});

        mRunning.add(pulse);
        pulse.start();
    }

    // ── Persistent pulse ──────────────────────────────────────────────────────

    /**
     * Starts an indefinite breathing animation on the item with {@code itemId}.
     * Runs until {@link #stopPulse(long)} or {@link #stopAllPulses()} is called.
     *
     * <p>Every tick calls {@link RecyclingView#findViewHolderForItemId} so the
     * animation automatically follows the item through recycling and scrolling.
     */
    @Override
    public void startPulse(final long itemId, final RecyclingView parent) {
        if (itemId == RecyclingAdapter.NO_ID || parent == null) return;

        stopPulse(itemId); // cancel any existing animator for this ID first

        ValueAnimator pulse = ValueAnimator.ofFloat(0f, 1f);
        pulse.setDuration(DURATION_PULSE_CYCLE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setRepeatMode(ValueAnimator.RESTART);
        pulse.setInterpolator(new AccelerateDecelerateInterpolator());

        pulse.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
				@Override
				public void onAnimationUpdate(ValueAnimator anim) {
					RecyclingAdapter.ViewHolder h = parent.findViewHolderForItemId(itemId);
					if (h == null) return; // off-screen — will resume when scrolled back

					// Triangle wave: 0→1 (expand) then 1→0 (contract) = symmetrical breathe.
					float t     = (Float) anim.getAnimatedValue();
					float phase = (t < 0.5f) ? (t / 0.5f) : (1f - (t - 0.5f) / 0.5f);

					View v = h.getItemView();
					v.setScaleX(1f + 0.06f * phase);
					v.setScaleY(1f + 0.06f * phase);
					v.setAlpha(1f - 0.25f * phase);
				}
			});

        pulse.addListener(new AnimatorListenerAdapter() {
				@Override
				public void onAnimationCancel(Animator a) {
					// Restore view when stopped externally.
					RecyclingAdapter.ViewHolder h = parent.findViewHolderForItemId(itemId);
					if (h != null) resetView(h.getItemView());
					mPulseAnimators.remove(itemId);
				}
			});

        mPulseAnimators.put(itemId, pulse);
        pulse.start();
    }

    /** Stops the persistent pulse for {@code itemId} and resets its view. */
    @Override
    public void stopPulse(long itemId) {
        ValueAnimator existing = mPulseAnimators.get(itemId);
        if (existing != null) existing.cancel(); // listener handles cleanup
    }

    /** Stops all active persistent pulses and resets all affected views. */
    @Override
    public void stopAllPulses() {
        for (int i = mPulseAnimators.size() - 1; i >= 0; i--) {
            ValueAnimator anim = mPulseAnimators.valueAt(i);
            if (anim != null) anim.cancel();
        }
        mPulseAnimators.clear(); // defensive clear in case cancel() didn't fire synchronously
    }

    // ── Cancel & state ────────────────────────────────────────────────────────

    @Override
    public void cancelAll() {
        stopAllPulses();
        for (int i = mRunning.size() - 1; i >= 0; i--) {
            mRunning.get(i).cancel();
        }
        mRunning.clear();
    }

    @Override
    public boolean isRunning() {
        return !mRunning.isEmpty() || mPulseAnimators.size() > 0;
    }

    // ── Resolution helpers ────────────────────────────────────────────────────

    /**
     * Returns the live view that currently represents {@code itemId}.
     *
     * <p>Returns {@code null} (not {@code fallback}) when the item is off-screen
     * so callers skip the tick rather than accidentally animating the wrong view.
     * Falls back to {@code fallback} only when stable IDs are not in use.
     */
    private static View resolveView(RecyclingView parent, long itemId, View fallback) {
        if (parent == null || itemId == RecyclingAdapter.NO_ID) {
            return fallback; // no ID tracking — use captured reference (legacy path)
        }
        RecyclingAdapter.ViewHolder h = parent.findViewHolderForItemId(itemId);
        return (h != null) ? h.getItemView() : null; // null = off-screen, skip tick
    }

    /** Walks the view hierarchy to find the owning {@link RecyclingView}. */
    private static RecyclingView parentOf(View v) {
        if (v == null) return null;
        android.view.ViewParent p = v.getParent();
        return (p instanceof RecyclingView) ? (RecyclingView) p : null;
    }

    // ── Existing helpers (unchanged) ──────────────────────────────────────────

    private static boolean isVertical(View v) {
        RecyclingView parent = (RecyclingView) v.getParent();
        return parent != null && parent.getOrientation() == RecyclingView.VERTICAL;
    }

    private static void resetView(View v) {
        v.setAlpha(1f);
        v.setTranslationX(0f);
        v.setTranslationY(0f);
        v.setScaleX(1f);
        v.setScaleY(1f);
    }

    private static float dpToPx(View v, float dp) {
        return dp * v.getContext().getResources().getDisplayMetrics().density;
    }
}

