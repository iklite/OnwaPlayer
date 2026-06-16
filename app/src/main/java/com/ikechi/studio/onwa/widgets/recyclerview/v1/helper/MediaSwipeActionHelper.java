package com.ikechi.studio.onwa.widgets.recyclerview.v1.helper;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import com.ikechi.studio.onwa.player.utils.IkBeautifulDialog;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.adapter.RecyclingAdapter;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.decoration.ItemDecoration;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.listener.OnItemTouchListener;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.widget.RecyclingView;

/**
 * Configurable swipe-action helper for {@link RecyclingView}.
 *
 * <h3>What's new vs. the original:</h3>
 * <ul>
 *   <li>Actions are fully configurable — labels, colors, commit behaviour.</li>
 *   <li>Delete swipe shows a confirmation dialog with three choices:
 *       <b>Delete</b> (permanent), <b>Remove</b> (from list only), <b>Cancel</b>.</li>
 *   <li>Two built‑in presets:
 *       {@link #forAudioLibrary(RecyclingView, SwipeCallback)} — FAV / DELETE,
 *       {@link #forVideoLibrary(RecyclingView, SwipeCallback)} — PLAY / DELETE.</li>
 *   <li>Orientation‑aware: works in both VERTICAL and HORIZONTAL lists.</li>
 * </ul>
 *
 * <p>Zero external dependencies. Zero lambda expressions. API 18+.
 */
public class MediaSwipeActionHelper {

    // ── Thresholds ────────────────────────────────────────────────────────────
    private static final float THRESHOLD_COMMIT = 0.55f;

    // ── Default colours ───────────────────────────────────────────────────────
    private static final int COLOR_DELETE    = 0xFFE53935;
    private static final int COLOR_FAVOURITE = 0xFF43A047;
    private static final int COLOR_PLAY      = 0xFF2196F3;
    private static final int COLOR_LABEL     = 0xFFFFFFFF;

    // ──────────────────────────────────────────────────────────────────────────
    // SwipeAction descriptor
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Describes one swipe action (left or right).
     */
    public static class SwipeAction {
        /** Display label drawn on the revealed strip. */
        public final String label;
        /** Background colour of the revealed strip. */
        public final int color;
        /** If true, the action commits on full swipe; otherwise it springs back. */
        public final boolean commits;
        /** If true, show a confirmation dialog before committing. */
        public final boolean confirmBeforeCommit;

        public SwipeAction(String label, int color, boolean commits,
                           boolean confirmBeforeCommit) {
            this.label = label;
            this.color = color;
            this.commits = commits;
            this.confirmBeforeCommit = confirmBeforeCommit;
        }

        /** A non‑committing action that just springs back. */
        public static SwipeAction springBack(String label, int color) {
            return new SwipeAction(label, color, false, false);
        }

        /** A committing action with no confirmation. */
        public static SwipeAction commit(String label, int color) {
            return new SwipeAction(label, color, true, false);
        }

        /** A committing action that asks for confirmation first. */
        public static SwipeAction commitWithConfirm(String label, int color) {
            return new SwipeAction(label, color, true, true);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Callback
    // ──────────────────────────────────────────────────────────────────────────

    public interface SwipeCallback {
        /** Fired when the LEFT action is committed. */
        void onSwipeLeft(int adapterPosition);
        /** Fired when the RIGHT action is committed. */
        void onSwipeRight(int adapterPosition);
        /**
         * Fired when the user chooses "Remove from list" in the delete
         * confirmation dialog (instead of permanent delete).
         */
        void onSwipeRemoveFromList(int adapterPosition);
    }

    // ── Factory presets ───────────────────────────────────────────────────────

    /**
     * Audio‑library preset:
     * <ul>
     *   <li>Left  → DELETE (with confirmation dialog)</li>
     *   <li>Right → ★ FAV (commits immediately)</li>
     * </ul>
     */
    public static MediaSwipeActionHelper forAudioLibrary(RecyclingView parent,
													SwipeCallback callback) {
        return new MediaSwipeActionHelper(parent, callback,
									 SwipeAction.commitWithConfirm("DELETE", COLOR_DELETE),
									 SwipeAction.commit("▶ PLAY", COLOR_FAVOURITE));
    }

    /**
     * Video‑library preset:
     * <ul>
     *   <li>Left  → DELETE (with confirmation dialog)</li>
     *   <li>Right → PLAY (commits immediately)</li>
     * </ul>
     */
    public static MediaSwipeActionHelper forVideoLibrary(RecyclingView parent,
													SwipeCallback callback) {
        return new MediaSwipeActionHelper(parent, callback,
									 SwipeAction.commitWithConfirm("DELETE", COLOR_DELETE),
									 SwipeAction.commit("▶ PLAY", COLOR_PLAY));
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private final RecyclingView mParent;
    private final SwipeCallback mCallback;
    private final SwipeAction   mLeftAction;
    private final SwipeAction   mRightAction;

    private int     mSwipingPos  = -1;
    private View    mSwipingView;
    private float   mSwipeStartPrimary;
    private float   mCurrentDx;
    private boolean mSwiping;

    private VelocityTracker mVelocity;
    private final int       mTouchSlop;

    // ── Paints ────────────────────────────────────────────────────────────────
    private final Paint mBgPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mIconPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * Full constructor with custom left/right actions.
     *
     * @param parent      The RecyclingView to attach to.
     * @param callback    Receives commit events.
     * @param leftAction  Action revealed on left swipe (typically delete).
     * @param rightAction Action revealed on right swipe (typically play/fav).
     */
    public MediaSwipeActionHelper(RecyclingView parent, SwipeCallback callback,
                             SwipeAction leftAction, SwipeAction rightAction) {
        mParent     = parent;
        mCallback   = callback;
        mLeftAction  = leftAction;
        mRightAction = rightAction;

        mTouchSlop = ViewConfiguration.get(parent.getContext()).getScaledTouchSlop();

        mLabelPaint.setColor(COLOR_LABEL);
        mLabelPaint.setTextSize(dpToPx(13));
        mLabelPaint.setTextAlign(Paint.Align.CENTER);
        mLabelPaint.setFakeBoldText(true);

        mIconPaint.setColor(COLOR_LABEL);
        mIconPaint.setTextSize(dpToPx(16));
        mIconPaint.setTextAlign(Paint.Align.CENTER);

        parent.addOnItemTouchListener(new InternalTouchListener());
        parent.addItemDecoration(new InternalDecoration());
    }

    // ── Internal touch listener ───────────────────────────────────────────────

    private class InternalTouchListener implements OnItemTouchListener {

		private float mDownX;
		private float mDownY;

		@Override
		public boolean onInterceptTouchEvent(RecyclingView view, MotionEvent ev) {
			boolean isVert = (view.getOrientation() == RecyclingView.VERTICAL);

			switch (ev.getActionMasked()) {
				case MotionEvent.ACTION_DOWN:
					mDownX = ev.getX();
					mDownY = ev.getY();
					break;

				case MotionEvent.ACTION_MOVE: {
						float dx = Math.abs(ev.getX() - mDownX);
						float dy = Math.abs(ev.getY() - mDownY);

						if (isVert) {
							// Vertical list: swipe is horizontal (dx), scroll is vertical (dy)
							// Only intercept when horizontal swipe is clearly dominant
							if (dx > mTouchSlop && dx > dy * 1.5f) {
								beginSwipe(ev);
								return mSwiping;
							}
						} else {
							// Horizontal list: swipe is vertical (dy), scroll is horizontal (dx)
							if (dy > mTouchSlop && dy > dx * 1.5f) {
								beginSwipe(ev);
								return mSwiping;
							}
						}
						break;
					}
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

        mSwipingPos      = pos;
        mSwipingView     = h.getItemView();
        boolean isVert   = (mParent.getOrientation() == RecyclingView.VERTICAL);
        mSwipeStartPrimary = isVert ? 0f : ev.getX(); // primary axis
        if (isVert) {
            mSwipeStartPrimary = ev.getX();
        } else {
            mSwipeStartPrimary = ev.getY();
        }
        mCurrentDx   = 0f;
        mSwiping     = true;

        if (mVelocity == null) mVelocity = VelocityTracker.obtain();
        else mVelocity.clear();
        mVelocity.addMovement(ev);
    }

    private void handleSwipeEvent(MotionEvent ev) {
        if (!mSwiping || mSwipingView == null) return;
        if (mVelocity != null) mVelocity.addMovement(ev);

        boolean isVert = (mParent.getOrientation() == RecyclingView.VERTICAL);
        float primary  = isVert ? ev.getX() : ev.getY();
        mCurrentDx = primary - mSwipeStartPrimary;

        if (isVert) {
            mSwipingView.setTranslationX(mCurrentDx);
        } else {
            mSwipingView.setTranslationY(mCurrentDx);
        }
        mParent.invalidate();

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
					float width = isVert
						? mSwipingView.getWidth()
						: mSwipingView.getHeight();
					float fraction = Math.abs(mCurrentDx) / width;
					boolean isLeft = mCurrentDx < 0;

					if (ev.getActionMasked() == MotionEvent.ACTION_UP
						&& fraction > THRESHOLD_COMMIT) {
						SwipeAction action = isLeft ? mLeftAction : mRightAction;
						if (action.commits) {
							if (action.confirmBeforeCommit && isLeft) {
								showDeleteConfirmation(mSwipingPos, mSwipingView);
							} else {
								commitAction(isLeft, mSwipingPos, mSwipingView);
							}
						} else {
							springBack();
						}
					} else {
						springBack();
					}
					break;
				}
        }
    }

    // ── Confirmation dialog ───────────────────────────────────────────────────

    private void showDeleteConfirmation(final int pos, final View view) {
        // Use IkBeautifulDialog for a polished look, fall back to AlertDialog
        try {
            IkBeautifulDialog dialog = new IkBeautifulDialog(mParent.getContext());
            dialog.setMessage("What would you like to do?");
            dialog.setItems(new String[]{
					"Delete permanently",
					"Remove from list",
					"Cancel"
				}, new IkBeautifulDialog.OnItemClickListener() {
					@Override
					public void onItemClick(int which, String item) {
						if (which == 0) {
							// Permanent delete
							if (mCallback != null) mCallback.onSwipeLeft(pos);
							dismissLeft(view);
						} else if (which == 1) {
							// Remove from list only
							if (mCallback != null) mCallback.onSwipeRemoveFromList(pos);
							dismissLeft(view);
						} else {
							// Cancel — spring back
							springBack();
						}
					}
				});
            dialog.setCancelable(true);
            dialog.showList();
        } catch (Exception e) {
            // Fallback to standard AlertDialog
            new AlertDialog.Builder(mParent.getContext())
                .setTitle("Delete")
                .setItems(new String[]{"Delete permanently", "Remove from list", "Cancel"},
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface d, int which) {
						if (which == 0) {
							if (mCallback != null) mCallback.onSwipeLeft(pos);
							dismissLeft(view);
						} else if (which == 1) {
							if (mCallback != null) mCallback.onSwipeRemoveFromList(pos);
							dismissLeft(view);
						} else {
							springBack();
						}
					}
				})
                .show();
        }
    }

    private void commitAction(final boolean isLeft, final int pos, final View view) {
        if (isLeft) {
            dismissLeft(view);
            if (mCallback != null) mCallback.onSwipeLeft(pos);
        } else {
            commitRight();
        }
    }

    // ── Animations ────────────────────────────────────────────────────────────

    private void dismissLeft(final View view) {
        final boolean isVert = (mParent.getOrientation() == RecyclingView.VERTICAL);
        float to = isVert ? -view.getWidth() : -view.getHeight();
        ValueAnimator anim = ValueAnimator.ofFloat(mCurrentDx, to);
        anim.setDuration(220);
        anim.setInterpolator(new AccelerateInterpolator(1.5f));
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
				@Override
				public void onAnimationUpdate(ValueAnimator a) {
					float v = (Float) a.getAnimatedValue();
					if (isVert) {
						view.setTranslationX(v);
						view.setAlpha(1f - Math.abs(v) / view.getWidth());
					} else {
						view.setTranslationY(v);
						view.setAlpha(1f - Math.abs(v) / view.getHeight());
					}
					mParent.invalidate();
				}
			});
        anim.addListener(new AnimatorListenerAdapter() {
				@Override
				public void onAnimationEnd(Animator a) {
					resetSwipe();
				}
			});
        anim.start();
    }

    private void dismissLeft() {
        if (mSwipingView != null) dismissLeft(mSwipingView);
    }

    private void commitRight() {
        final View view = mSwipingView;
        final float from = mCurrentDx;
        ValueAnimator anim = ValueAnimator.ofFloat(from, 0f);
        anim.setDuration(280);
        anim.setInterpolator(new DecelerateInterpolator(1.5f));
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
				@Override
				public void onAnimationUpdate(ValueAnimator a) {
					float val = (Float) a.getAnimatedValue();
					boolean isVert = (mParent.getOrientation() == RecyclingView.VERTICAL);
					if (isVert) view.setTranslationX(val);
					else view.setTranslationY(val);
					mParent.invalidate();
				}
			});
        anim.addListener(new AnimatorListenerAdapter() {
				@Override
				public void onAnimationEnd(Animator a) {
					resetSwipe();
					if (mCallback != null) mCallback.onSwipeRight(mSwipingPos);
				}
			});
        anim.start();
    }

    private void springBack() {
        if (mSwipingView == null) return;
        final View view = mSwipingView;
        final float from = mCurrentDx;
        ValueAnimator anim = ValueAnimator.ofFloat(from, 0f);
        anim.setDuration(300);
        anim.setInterpolator(new DecelerateInterpolator(2f));
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
				@Override
				public void onAnimationUpdate(ValueAnimator a) {
					float val = (Float) a.getAnimatedValue();
					boolean isVert = (mParent.getOrientation() == RecyclingView.VERTICAL);
					if (isVert) view.setTranslationX(val);
					else view.setTranslationY(val);
					mParent.invalidate();
				}
			});
        anim.addListener(new AnimatorListenerAdapter() {
				@Override
				public void onAnimationEnd(Animator a) {
					boolean isVert = (mParent.getOrientation() == RecyclingView.VERTICAL);
					if (isVert) view.setTranslationX(0f);
					else view.setTranslationY(0f);
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

            boolean isVert  = (parent.getOrientation() == RecyclingView.VERTICAL);
            boolean isLeft  = mCurrentDx < 0;
            SwipeAction action = isLeft ? mLeftAction : mRightAction;

            float top    = mSwipingView.getTop();
            float bottom = mSwipingView.getBottom();
            float left   = mSwipingView.getLeft();
            float right  = mSwipingView.getRight();
            float r      = dpToPx(8);

            mBgPaint.setColor(action.color);

            if (isVert) {
                if (isLeft) {
                    // Reveal on the right — strip from (w + dx) to w
                    float stripLeft = right + mCurrentDx;
                    canvas.drawRoundRect(
                        new RectF(stripLeft, top, right, bottom), r, r, mBgPaint);
                    float cx = (stripLeft + right) / 2f;
                    float cy = (top + bottom) / 2f + dpToPx(5);
                    canvas.drawText(action.label, cx, cy, mLabelPaint);
                } else {
                    // Reveal on the left — strip from 0 to dx
                    float stripRight = left + mCurrentDx;
                    canvas.drawRoundRect(
                        new RectF(left, top, stripRight, bottom), r, r, mBgPaint);
                    float cx = (left + stripRight) / 2f;
                    float cy = (top + bottom) / 2f + dpToPx(5);
                    canvas.drawText(action.label, cx, cy, mLabelPaint);
                }
            } else {
                // Horizontal orientation
                if (isLeft) {
                    float stripBottom = bottom + mCurrentDx;
                    canvas.drawRoundRect(
                        new RectF(left, stripBottom, right, bottom), r, r, mBgPaint);
                    float cx = (left + right) / 2f;
                    float cy = (stripBottom + bottom) / 2f + dpToPx(5);
                    canvas.drawText(action.label, cx, cy, mLabelPaint);
                } else {
                    float stripTop = top + mCurrentDx;
                    canvas.drawRoundRect(
                        new RectF(left, top, right, stripTop), r, r, mBgPaint);
                    float cx = (left + right) / 2f;
                    float cy = (top + stripTop) / 2f + dpToPx(5);
                    canvas.drawText(action.label, cx, cy, mLabelPaint);
                }
            }
        }
    }

    // ── Util ──────────────────────────────────────────────────────────────────

    private float dpToPx(int dp) {
        return dp * mParent.getContext().getResources().getDisplayMetrics().density;
    }
}
