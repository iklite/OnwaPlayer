package com.ikechi.studio.onwa.widgets.recyclerview.v1.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.EdgeEffect;
import android.widget.Scroller;
import android.content.res.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.ikechi.studio.onwa.widgets.recyclerview.v1.adapter.*;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.animator.*;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.decoration.*;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.listener.*;
import com.ikechi.studio.onwa.player.R;

/**
 * A fully custom, vertically <em>or</em> horizontally scrolling, view-recycling
 * container.
 *
 * <h3>Feature set — zero Jetpack, zero AndroidX, zero external libraries</h3>
 * <ul>
 *   <li>VERTICAL and HORIZONTAL orientation ({@link #setOrientation(int)})</li>
 *   <li>Per-viewType recycle pool (SparseArray keyed by type)</li>
 *   <li>Binary-search O(log n) visible-range detection</li>
 *   <li>Velocity-tracked fling with decay via {@link Scroller}</li>
 *   <li>{@link EdgeEffect} over-scroll glow on both leading and trailing edges</li>
 *   <li>Pluggable {@link ItemAnimator} — add / remove / move / change animations</li>
 *   <li>Pluggable {@link ItemDecoration} list — draw before/after children, add offsets</li>
 *   <li>Pluggable {@link OnItemTouchListener} list — intercept pipeline for helpers</li>
 *   <li>OnScrollListener with IDLE / DRAGGING / SETTLING states</li>
 *   <li>Custom scroll-indicator drawn on Canvas (orientation-aware)</li>
 *   <li>{@link #smoothScrollToPosition(int)} and {@link #scrollToPosition(int)}</li>
 *   <li>{@link #findViewHolderForAdapterPosition(int)}</li>
 *   <li>{@link #findViewHolderForItemId(long)} — stable-ID lookup survives recycling</li>
 *   <li>{@link #pulseItemId(long)} / {@link #stopPulseItemId(long)} — persistent pulse API</li>
 * </ul>
 *
 * <h3>Encapsulation</h3>
 * This class accesses {@link RecyclingAdapter.ViewHolder} internal state
 * <em>exclusively</em> through the package-private accessor methods defined in
 * {@link RecyclingAdapter.ViewHolder} — no direct field access anywhere.
 *
 * <p>Minimum SDK: 18 (uses EdgeEffect API 14, ValueAnimator API 11).
 */
public class RecyclingView extends ViewGroup {

	// ──────────────────────────────────────────────────────────────────────────
	// Orientation constants
	// ──────────────────────────────────────────────────────────────────────────

	/** Scroll and lay out items vertically (default). */
	public static final int VERTICAL   = 0;

	/** Scroll and lay out items horizontally. */
	public static final int HORIZONTAL = 1;

	// ──────────────────────────────────────────────────────────────────────────
	// Scroll-state constants
	// ──────────────────────────────────────────────────────────────────────────

	public static final int SCROLL_STATE_IDLE     = 0;
	public static final int SCROLL_STATE_DRAGGING = 1;
	public static final int SCROLL_STATE_SETTLING = 2;

	// ──────────────────────────────────────────────────────────────────────────
	// OnScrollListener
	// ──────────────────────────────────────────────────────────────────────────

	/**
	 * Callback for scroll-state and scroll-offset changes.
	 * Register via {@link #addOnScrollListener(OnScrollListener)}.
	 */
	public interface OnScrollListener {
		/** Called when the scroll state transitions. */
		void onScrollStateChanged(RecyclingView view, int newState);
		/**
		 * Called every time the scroll offset changes.
		 * @param dOffset Delta pixels scrolled (positive = scrolled forward).
		 */
		void onScrolled(RecyclingView view, int dOffset);
	}

	// ──────────────────────────────────────────────────────────────────────────
	// Constants
	// ──────────────────────────────────────────────────────────────────────────

	private static final long LONG_PRESS_TIMEOUT_MS  = 500L;
	private static final int  SCROLL_BAR_WIDTH_DP    = 4;
	private static final int  SCROLL_BAR_COLOR       = 0x99000000;
	private static final int  SCROLL_BAR_MARGIN_DP   = 2;

	// Snap animation duration (ms).
	private static final int  SNAP_DURATION_MS       = 220;

	// ──────────────────────────────────────────────────────────────────────────
	// Core state
	// ──────────────────────────────────────────────────────────────────────────

	private RecyclingAdapter mAdapter;
	private ItemAnimator     mItemAnimator;
	private int              mOrientation  = VERTICAL;

	// ── XML-settable features ─────────────────────────────────────────────────

	/**
	 * When true, the first adapter item appears at the trailing edge
	 * (bottom in VERTICAL, right in HORIZONTAL) and items are laid out
	 * toward the leading edge.  Equivalent to RecyclerView's reverseLayout.
	 * Set via {@code app:rv_reverseLayout="true"} or {@link #setReverseLayout}.
	 */
	private boolean mReverseLayout    = false;

	/**
	 * When true, the list snaps to the nearest item boundary after a fling
	 * or drag release, like a horizontal pager.
	 * Set via {@code app:rv_snapToPosition="true"} or {@link #setSnapToPosition}.
	 */
	private boolean mSnapToPosition   = false;

	/** Whether the scroll-position indicator bar is visible. */
	private boolean mScrollBarEnabled = true;

	/**
	 * Extra spacing (px) inserted between each item along the scroll axis.
	 * First and last items are NOT given extra leading/trailing padding.
	 * Set via {@code app:rv_itemSpacing="8dp"} or {@link #setItemSpacing}.
	 */
	private int     mItemSpacingPx    = 0;

	/**
	 * True while we are running a snap-correction scroll so that
	 * {@link #computeScroll} does not attempt to snap again recursively.
	 */
	private boolean mSnapping         = false;

	/** Pixels scrolled from the leading edge (top in VERTICAL, left in HORIZONTAL). */
	private int     mScrollOffset = 0;

	/**
	 * Total size of all items along the scroll axis
	 * (total height in VERTICAL, total width in HORIZONTAL).
	 */
	private int     mTotalSize    = 0;
	private boolean mLayoutDirty  = true;

	/**
	 * Content-coordinate leading edge of each item.
	 * (top in VERTICAL, left in HORIZONTAL)
	 */
	private int[]   mItemStart;

	/**
	 * Content-coordinate trailing edge of each item.
	 * (bottom in VERTICAL, right in HORIZONTAL)
	 */
	private int[]   mItemEnd;

	/** Decoration insets along the scroll axis (bottom in VERTICAL, right in HORIZONTAL). */
	private int[]   mDecoTrailing;

	// ──────────────────────────────────────────────────────────────────────────
	// Recycle pool   viewType → list<ViewHolder>
	// ──────────────────────────────────────────────────────────────────────────

	private final SparseArray<List<RecyclingAdapter.ViewHolder>> mPool =
	new SparseArray<List<RecyclingAdapter.ViewHolder>>();

	/** Active holders keyed by adapter position. */
	private final SparseArray<RecyclingAdapter.ViewHolder> mActive =
	new SparseArray<RecyclingAdapter.ViewHolder>();

	/**
	 * Holders mid-remove-animation: still attached to the view tree but not
	 * in {@code mActive}.  Must not be recycled until the animation ends.
	 */
	private final Set<RecyclingAdapter.ViewHolder> mDisappearing =
	new HashSet<RecyclingAdapter.ViewHolder>();

	// ──────────────────────────────────────────────────────────────────────────
	// Decorations, touch listeners, scroll listeners
	// ──────────────────────────────────────────────────────────────────────────

	private final List<ItemDecoration>      mDecorations    = new ArrayList<ItemDecoration>();
	private final List<OnItemTouchListener> mTouchListeners = new ArrayList<OnItemTouchListener>();
	private       OnItemTouchListener       mActiveTouch;

	private final List<OnScrollListener> mScrollListeners = new ArrayList<OnScrollListener>();
	private       int                    mScrollState     = SCROLL_STATE_IDLE;

	// ──────────────────────────────────────────────────────────────────────────
	// Fling / scroll
	// ──────────────────────────────────────────────────────────────────────────

	private final Scroller        mScroller;
	private       VelocityTracker mVelocityTracker;
	private       float           mLastTouchPrimary; // Y in VERTICAL, X in HORIZONTAL
	private       float           mDownPrimary;      // same
	private       float           mDownSecondary;    // X in VERTICAL, Y in HORIZONTAL
	private       boolean         mIsDragging;
	private final int             mTouchSlop;
	private final int             mMaxFlingVelocity;
	private final int             mMinFlingVelocity;

	// ──────────────────────────────────────────────────────────────────────────
	// Long-press
	// ──────────────────────────────────────────────────────────────────────────

	private final Handler mHandler = new Handler();
	private       int     mPendingLongPos = -1;

	private final Runnable mLongPressRunnable = new Runnable() {
		@Override
		public void run() {
			if (mPendingLongPos >= 0) {
				RecyclingAdapter.ViewHolder h = mActive.get(mPendingLongPos);
				if (h != null) {
					h.getItemView().performLongClick();
				}
			}
			mPendingLongPos = -1;
		}
	};

	// ──────────────────────────────────────────────────────────────────────────
	// Edge effects
	// ──────────────────────────────────────────────────────────────────────────

	private final EdgeEffect mEdgeGlowLeading;   // top (VERTICAL) or left (HORIZONTAL)
	private final EdgeEffect mEdgeGlowTrailing;  // bottom (VERTICAL) or right (HORIZONTAL)

	// ──────────────────────────────────────────────────────────────────────────
	// Scroll bar
	// ──────────────────────────────────────────────────────────────────────────

	private final Paint mScrollBarPaint;
	private final int   mScrollBarWidthPx;
	private final int   mScrollBarMarginPx;

	// ──────────────────────────────────────────────────────────────────────────
	// Scratch rect for decoration offsets
	// ──────────────────────────────────────────────────────────────────────────

	private final android.graphics.Rect mDecoRect = new android.graphics.Rect();

	// ──────────────────────────────────────────────────────────────────────────
	// Constructors
	// ──────────────────────────────────────────────────────────────────────────

	public RecyclingView(Context context) {
		this(context, null);
	}

	public RecyclingView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public RecyclingView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);

		mScroller         = new Scroller(context);
		mEdgeGlowLeading  = new EdgeEffect(context);
		mEdgeGlowTrailing = new EdgeEffect(context);
		mItemAnimator     = new DefaultItemAnimator();

		ViewConfiguration vc = ViewConfiguration.get(context);
		mTouchSlop        = vc.getScaledTouchSlop();
		mMaxFlingVelocity = vc.getScaledMaximumFlingVelocity();
		mMinFlingVelocity = vc.getScaledMinimumFlingVelocity();

		mScrollBarPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
		mScrollBarPaint.setColor(SCROLL_BAR_COLOR);
		mScrollBarWidthPx  = dpToPx(SCROLL_BAR_WIDTH_DP);
		mScrollBarMarginPx = dpToPx(SCROLL_BAR_MARGIN_DP);

		// ── Read XML attributes ───────────────────────────────────────────────
		if (attrs != null) {
			TypedArray a = context.obtainStyledAttributes(
				attrs, R.styleable.RecyclingView, defStyleAttr, 0);
			try {
				mOrientation      = a.getInt(
					R.styleable.RecyclingView_rv_orientation, VERTICAL);
				mReverseLayout    = a.getBoolean(
					R.styleable.RecyclingView_rv_reverseLayout, false);
				mSnapToPosition   = a.getBoolean(
					R.styleable.RecyclingView_rv_snapToPosition, false);
				mScrollBarEnabled = a.getBoolean(
					R.styleable.RecyclingView_rv_scrollBarEnabled, true);
				mItemSpacingPx    = a.getDimensionPixelSize(
					R.styleable.RecyclingView_rv_itemSpacing, 0);
				int barColor = a.getColor(
					R.styleable.RecyclingView_rv_scrollBarColor, SCROLL_BAR_COLOR);
				mScrollBarPaint.setColor(barColor);
			} finally {
				a.recycle();
			}
		}

		setWillNotDraw(false);
		setOverScrollMode(OVER_SCROLL_NEVER);
		setClipToPadding(true);
	}

	// ──────────────────────────────────────────────────────────────────────────
	// Public API
	// ──────────────────────────────────────────────────────────────────────────

	/**
	 * Attaches an adapter and triggers a full layout.
	 *
	 * @param adapter The adapter to use. Pass {@code null} to detach.
	 */
	public void setAdapter(RecyclingAdapter adapter) {
		if (mAdapter != null) mAdapter.detach();
		mAdapter = adapter;
		if (mAdapter != null) mAdapter.attachTo(this);
		fullReset();
		requestLayout();
	}

	/** Returns the currently attached adapter, or {@code null}. */
	public RecyclingAdapter getAdapter() { return mAdapter; }

	/**
	 * Sets the scroll orientation.
	 *
	 * @param orientation {@link #VERTICAL} or {@link #HORIZONTAL}.
	 */
	public void setOrientation(int orientation) {
		if (orientation != VERTICAL && orientation != HORIZONTAL) {
			throw new IllegalArgumentException(
				"orientation must be RecyclingView.VERTICAL or RecyclingView.HORIZONTAL");
		}
		if (mOrientation != orientation) {
			mOrientation = orientation;
			fullReset();
			requestLayout();
		}
	}

	/** Returns the current scroll orientation ({@link #VERTICAL} or {@link #HORIZONTAL}). */
	public int getOrientation() { return mOrientation; }

	/**
	 * Reverses the layout direction.
	 *
	 * <p>When {@code true}, adapter position 0 appears at the trailing edge
	 * (bottom in VERTICAL, right in HORIZONTAL). Equivalent to setting
	 * {@code app:rv_reverseLayout="true"} in XML.
	 *
	 * @param reverse {@code true} to reverse; {@code false} for normal order.
	 */
	public void setReverseLayout(boolean reverse) {
		if (mReverseLayout != reverse) {
			mReverseLayout = reverse;
			fullReset();
			requestLayout();
		}
	}

	/** Returns whether layout direction is reversed. */
	public boolean isReverseLayout() { return mReverseLayout; }

	/**
	 * Enables or disables snap-to-item behaviour.
	 *
	 * <p>When enabled, the list snaps to the nearest item boundary after each
	 * fling or drag release, similar to a pager. Equivalent to
	 * {@code app:rv_snapToPosition="true"} in XML.
	 *
	 * @param snap {@code true} to enable snap; {@code false} for free scroll.
	 */
	public void setSnapToPosition(boolean snap) { mSnapToPosition = snap; }

	/** Returns whether snap-to-item behaviour is enabled. */
	public boolean isSnapToPosition() { return mSnapToPosition; }

	/**
	 * Shows or hides the scroll-position indicator bar.
	 * Equivalent to {@code app:rv_scrollBarEnabled="false"} in XML.
	 *
	 * @param enabled {@code true} to show the bar (default); {@code false} to hide.
	 */
	public void setScrollBarEnabled(boolean enabled) {
		if (mScrollBarEnabled != enabled) {
			mScrollBarEnabled = enabled;
			invalidate();
		}
	}

	/** Returns whether the scroll-position indicator bar is visible. */
	public boolean isScrollBarEnabled() { return mScrollBarEnabled; }

	/**
	 * Sets the color of the scroll-position indicator bar.
	 * Equivalent to {@code app:rv_scrollBarColor="@color/…"} in XML.
	 *
	 * @param color ARGB color integer, e.g. {@code 0xFF607D8B}.
	 */
	public void setScrollBarColor(int color) {
		mScrollBarPaint.setColor(color);
		if (mScrollBarEnabled) invalidate();
	}

	/**
	 * Sets uniform spacing (in pixels) between items along the scroll axis.
	 *
	 * <p>Spacing is inserted <em>between</em> items only — the first and last
	 * item are not given extra leading or trailing padding.  Pass {@code 0} to
	 * remove spacing.  Equivalent to {@code app:rv_itemSpacing="8dp"} in XML.
	 *
	 * @param spacingPx Spacing in pixels. Use {@code dpToPx(dp)} if needed.
	 */
	public void setItemSpacing(int spacingPx) {
		if (mItemSpacingPx != spacingPx) {
			mItemSpacingPx = Math.max(0, spacingPx);
			mLayoutDirty   = true;
			requestLayout();
		}
	}

	/** Returns the current inter-item spacing in pixels. */
	public int getItemSpacing() { return mItemSpacingPx; }

	/**
	 * Replaces the item animator.  Any currently running animations are cancelled.
	 *
	 * @param animator New animator. Pass {@code null} to disable animations.
	 */
	public void setItemAnimator(ItemAnimator animator) {
		if (mItemAnimator != null) mItemAnimator.cancelAll();
		mItemAnimator = animator;
	}

	/** Returns the current item animator. */
	public ItemAnimator getItemAnimator() { return mItemAnimator; }

	/** Adds an {@link ItemDecoration} and triggers a layout refresh. */
	public void addItemDecoration(ItemDecoration decoration) {
		mDecorations.add(decoration);
		mLayoutDirty = true;
		requestLayout();
	}

	/** Removes a previously added {@link ItemDecoration} and triggers a layout refresh. */
	public void removeItemDecoration(ItemDecoration decoration) {
		if (mDecorations.remove(decoration)) {
			mLayoutDirty = true;
			requestLayout();
		}
	}

	/** Registers an {@link OnItemTouchListener}. */
	public void addOnItemTouchListener(OnItemTouchListener listener) {
		if (listener != null && !mTouchListeners.contains(listener)) {
			mTouchListeners.add(listener);
		}
	}

	/** Removes a previously registered {@link OnItemTouchListener}. */
	public void removeOnItemTouchListener(OnItemTouchListener listener) {
		mTouchListeners.remove(listener);
		if (mActiveTouch == listener) mActiveTouch = null;
	}

	/** Registers an {@link OnScrollListener}. */
	public void addOnScrollListener(OnScrollListener listener) {
		if (listener != null && !mScrollListeners.contains(listener)) {
			mScrollListeners.add(listener);
		}
	}

	/** Removes a previously registered {@link OnScrollListener}. */
	public void removeOnScrollListener(OnScrollListener listener) {
		mScrollListeners.remove(listener);
	}

	/** Returns the current scroll state ({@code SCROLL_STATE_*}). */
	public int getScrollState() { return mScrollState; }

	/**
	 * Returns the adapter position of the item whose bounds contain the point
	 * ({@code x}, {@code y}) in the RecyclingView's own coordinate system,
	 * or {@code -1} if no item is at that point.
	 */
	public int getChildAdapterPosition(float x, float y) {
		int lead = (mOrientation == VERTICAL) ? (int) y : (int) x;
		return positionForContentCoord(lead + mScrollOffset);
	}

	/**
	 * Returns the active {@link RecyclingAdapter.ViewHolder} bound to
	 * {@code position}, or {@code null} if that position is not currently visible.
	 */
	public RecyclingAdapter.ViewHolder findViewHolderForAdapterPosition(int position) {
		return mActive.get(position);
	}

	/**
	 * Returns the currently visible (active) {@link RecyclingAdapter.ViewHolder}
	 * whose stable item ID equals {@code itemId}, or {@code null} if no active
	 * holder is currently bound to that ID (item is off-screen or ID not found).
	 *
	 * <p>This lookup is the foundation of the ID-safe pulse and change-animation
	 * system.  Because RecyclingView stamps {@code mBoundItemId} on every holder
	 * at bind time, this method always returns the holder that <em>currently</em>
	 * shows the requested item — even after the list has been scrolled and
	 * holders have been reused for different positions.
	 *
	 * <p>The adapter must have stable IDs enabled via
	 * {@link RecyclingAdapter#setHasStableIds setHasStableIds(true)} for this
	 * to work correctly; without it every position reports
	 * {@link RecyclingAdapter#NO_ID} and this method always returns {@code null}.
	 *
	 * @param itemId The stable ID to search for.
	 * @return The active holder for that ID, or {@code null} if off-screen.
	 */
	public RecyclingAdapter.ViewHolder findViewHolderForItemId(long itemId) {
		if (itemId == RecyclingAdapter.NO_ID) return null;
		for (int i = 0; i < mActive.size(); i++) {
			RecyclingAdapter.ViewHolder h = mActive.valueAt(i);
			if (h.getBoundItemId() == itemId) return h;
		}
		return null;
	}

	/**
	 * Returns the stable item ID of the item at the given touch coordinates,
	 * or {@link RecyclingAdapter#NO_ID} if no item is at that point or the
	 * adapter does not use stable IDs.
	 *
	 * <p>Intended for use inside click / long-press handlers where you have a
	 * raw {@link MotionEvent} and need to record <em>which</em> item was
	 * touched in a recycling-safe way — without holding a positional reference
	 * that becomes stale after an insert or remove.
	 *
	 * <pre>{@code
	 * recyclingView.addOnItemTouchListener(new OnItemTouchListener() {
	 *     public boolean onInterceptTouchEvent(RecyclingView rv, MotionEvent e) {
	 *         if (e.getAction() == MotionEvent.ACTION_UP) {
	 *             long id = rv.getItemIdAt(e.getX(), e.getY());
	 *             if (id != RecyclingAdapter.NO_ID) startPlaying(id);
	 *         }
	 *         return false;
	 *     }
	 *     public void onTouchEvent(RecyclingView rv, MotionEvent e) {}
	 * });
	 * }</pre>
	 *
	 * @param x X coordinate in the RecyclingView's own coordinate system.
	 * @param y Y coordinate in the RecyclingView's own coordinate system.
	 * @return Stable item ID, or {@link RecyclingAdapter#NO_ID}.
	 */
	public long getItemIdAt(float x, float y) {
		int pos = getChildAdapterPosition(x, y);
		if (pos < 0 || mAdapter == null) return RecyclingAdapter.NO_ID;
		// Use the stamped ID from the active holder when available — it is
		// always consistent with what is currently displayed on screen.
		RecyclingAdapter.ViewHolder h = mActive.get(pos);
		if (h != null) {
			long id = h.getBoundItemId();
			// Fall back to adapter query if the holder was not yet stamped
			// (edge case: called during the very first layout pass).
			return (id != RecyclingAdapter.NO_ID) ? id : mAdapter.getItemId(pos);
		}
		return mAdapter.getItemId(pos);
	}

	// ── Persistent pulse public API ───────────────────────────────────────────

	/**
	 * Starts a continuous breathing / pulsing animation on the item with the
	 * given stable ID.  The animation survives recycling: if the item scrolls
	 * off-screen the pulse pauses invisibly and resumes automatically when it
	 * scrolls back into view.
	 *
	 * <p>Typical use-cases: currently-playing song track, active download,
	 * live notification badge — any item requiring sustained visual attention.
	 *
	 * <p>Requires the adapter to have stable IDs enabled via
	 * {@link RecyclingAdapter#setHasStableIds setHasStableIds(true)}.
	 * If the current {@link ItemAnimator} does not override {@code startPulse}
	 * the call is a harmless no-op (the base class provides a no-op default).
	 *
	 * @param itemId Stable ID of the item to pulse.
	 * @see #stopPulseItemId(long)
	 * @see #stopAllPulses()
	 */
	public void pulseItemId(long itemId) {
		if (mItemAnimator != null) mItemAnimator.startPulse(itemId, this);
	}

	/**
	 * Stops the continuous pulse for the item with {@code itemId} and resets
	 * its view to the normal (scale=1, alpha=1) state.
	 * Safe to call when no pulse is running for that ID.
	 *
	 * @param itemId Stable ID of the item whose pulse should stop.
	 * @see #pulseItemId(long)
	 * @see #stopAllPulses()
	 */
	public void stopPulseItemId(long itemId) {
		if (mItemAnimator != null) mItemAnimator.stopPulse(itemId);
	}

	/**
	 * Stops every active persistent pulse and resets all pulsing views to their
	 * normal state.  Called automatically when the adapter is replaced or the
	 * list is fully reset.
	 *
	 * @see #pulseItemId(long)
	 * @see #stopPulseItemId(long)
	 */
	public void stopAllPulses() {
		if (mItemAnimator != null) mItemAnimator.stopAllPulses();
	}

	/**
	 * Immediately scrolls so that {@code position} is visible (no animation).
	 *
	 * @param position The adapter position to scroll to.
	 */
	public void scrollToPosition(int position) {
		if (!isPositionValid(position) || mLayoutDirty) return;
		int target = clampOffset(mItemStart[position]);
		if (target != mScrollOffset) {
			mScrollOffset = target;
			requestLayout();
		}
	}

	/**
	 * Smoothly animates the list so that {@code position} is visible.
	 *
	 * @param position The adapter position to scroll to.
	 */
	public void smoothScrollToPosition(int position) {
		if (!isPositionValid(position)) return;
		if (mLayoutDirty) {
			final int p = position;
			post(new Runnable() {
					@Override public void run() { smoothScrollToPosition(p); }
				});
			return;
		}
		int target = clampOffset(mItemStart[position]);
		if (target != mScrollOffset) {
			if (mOrientation == VERTICAL) {
				mScroller.startScroll(0, mScrollOffset, 0, target - mScrollOffset, 350);
			} else {
				mScroller.startScroll(mScrollOffset, 0, target - mScrollOffset, 0, 350);
			}
			setScrollState(SCROLL_STATE_SETTLING);
			invalidate();
		}
	}

	public void scrollBy(int delta) {
		if (delta == 0) return;
		int oldOffset = mScrollOffset;
		int newOffset = oldOffset + delta;
		int clamped   = clampOffset(newOffset);
		mScrollOffset = clamped;
		int actualDelta = clamped - oldOffset;
		if (actualDelta != 0) dispatchScrolled(actualDelta);
		int over = newOffset - clamped;
		if (over != 0) {
			float viewPortSizeF = (mOrientation == VERTICAL) ? getHeight() : getWidth();
			if (over < 0) {
				mEdgeGlowLeading.onPull(-over / viewPortSizeF, 0.5f);
				if (!mEdgeGlowTrailing.isFinished()) mEdgeGlowTrailing.onRelease();
			} else {
				mEdgeGlowTrailing.onPull(over / viewPortSizeF, 0.5f);
				if (!mEdgeGlowLeading.isFinished()) mEdgeGlowLeading.onRelease();
			}
		}
		requestLayout();
		invalidate();
	}

	// ──────────────────────────────────────────────────────────────────────────
	// Adapter-change callbacks (called by RecyclingAdapter)
	// ──────────────────────────────────────────────────────────────────────────

	public final void onAdapterDataChanged() { fullReset(); requestLayout(); }

	public final void onAdapterRangeInserted(int start, int count) {
		snapshotLeadingEdges();
		for (int i = mActive.size() - 1; i >= 0; i--) {
			int pos = mActive.keyAt(i);
			if (pos >= start) {
				RecyclingAdapter.ViewHolder h = mActive.valueAt(i);
				mActive.remove(pos);
				mActive.put(pos + count, h);
				h.setPosition(pos + count);
			}
		}
		mLayoutDirty = true;
		requestLayout();
		post(new Runnable() {
				@Override public void run() { dispatchMoveAnimations(); }
			});
	}

	public final void onAdapterRangeRemoved(int start, int count) {
		snapshotLeadingEdges();
		for (int i = mActive.size() - 1; i >= 0; i--) {
			int pos = mActive.keyAt(i);
			if (pos >= start && pos < start + count) {
				RecyclingAdapter.ViewHolder h = mActive.valueAt(i);
				h.setPendingRemove(true);
				mDisappearing.add(h);
				mActive.removeAt(i);
			}
		}
		SparseArray<RecyclingAdapter.ViewHolder> newActive = new SparseArray<RecyclingAdapter.ViewHolder>();
		for (int i = 0; i < mActive.size(); i++) {
			int pos = mActive.keyAt(i);
			RecyclingAdapter.ViewHolder h = mActive.valueAt(i);
			int newPos = (pos < start) ? pos : pos - count;
			newActive.put(newPos, h);
			h.setPosition(newPos);
		}
		mActive.clear();
		for (int i = 0; i < newActive.size(); i++) {
			mActive.put(newActive.keyAt(i), newActive.valueAt(i));
		}
		mLayoutDirty = true;
		requestLayout();
		for (final RecyclingAdapter.ViewHolder h : mDisappearing) {
			if (mItemAnimator != null) {
				mItemAnimator.animateRemove(h, new Runnable() {
						@Override public void run() {
							h.setPendingRemove(false);
							mDisappearing.remove(h);
							removeView(h.getItemView());
							poolHolder(h);
							requestLayout();
						}
					});
			} else {
				removeView(h.getItemView());
				poolHolder(h);
				mDisappearing.remove(h);
			}
		}
		post(new Runnable() {
				@Override public void run() { dispatchMoveAnimations(); }
			});
	}

	public final void onAdapterItemMoved(int fromPosition, int toPosition) {
		snapshotLeadingEdges();
		SparseArray<RecyclingAdapter.ViewHolder> newActive = new SparseArray<RecyclingAdapter.ViewHolder>();
		for (int i = 0; i < mActive.size(); i++) {
			int pos = mActive.keyAt(i);
			RecyclingAdapter.ViewHolder h = mActive.valueAt(i);
			int newPos;
			if (pos == fromPosition) {
				newPos = toPosition;
			} else if (fromPosition < toPosition) {
				newPos = (pos > fromPosition && pos <= toPosition) ? pos - 1 : pos;
			} else {
				newPos = (pos >= toPosition && pos < fromPosition) ? pos + 1 : pos;
			}
			newActive.put(newPos, h);
			h.setPosition(newPos);
		}
		mActive.clear();
		for (int i = 0; i < newActive.size(); i++) {
			mActive.put(newActive.keyAt(i), newActive.valueAt(i));
		}
		mLayoutDirty = true;
		requestLayout();
		post(new Runnable() {
				@Override public void run() { dispatchMoveAnimations(); }
			});
	}

	public final void onAdapterItemInserted(final int position) {
		snapshotLeadingEdges();
		mLayoutDirty = true;
		requestLayout();
		post(new Runnable() {
				@Override public void run() {
					final RecyclingAdapter.ViewHolder h = mActive.get(position);
					if (h != null && mItemAnimator != null) {
						h.setPendingAdd(true);
						mItemAnimator.animateAdd(h, new Runnable() {
								@Override public void run() { h.setPendingAdd(false); }
							});
					}
					dispatchMoveAnimations();
				}
			});
	}

	public final void onAdapterItemRemoved(final int position) {
		final RecyclingAdapter.ViewHolder outgoing = mActive.get(position);
		if (outgoing != null) {
			outgoing.setPendingRemove(true);
			mActive.remove(position);
			mDisappearing.add(outgoing);
		}
		snapshotLeadingEdges();
		mLayoutDirty = true;
		requestLayout();
		if (outgoing != null && mItemAnimator != null) {
			mItemAnimator.animateRemove(outgoing, new Runnable() {
					@Override public void run() {
						outgoing.setPendingRemove(false);
						mDisappearing.remove(outgoing);
						removeView(outgoing.getItemView());
						poolHolder(outgoing);
						requestLayout();
					}
				});
		}
		post(new Runnable() {
				@Override public void run() { dispatchMoveAnimations(); }
			});
	}

	/**
	 * Called when a single item changes in-place (e.g. favourite-toggle).
	 *
	 * <p>Rebinds the holder and stamps the new stable item ID so that
	 * {@link DefaultItemAnimator#animateChange} can look up the correct
	 * view on every animation tick — even if the holder is recycled during
	 * the animation.
	 */
	public final void onAdapterItemChanged(final int position) {
		RecyclingAdapter.ViewHolder h = mActive.get(position);
		if (h != null && mAdapter != null) {
			mAdapter.onBindViewHolder(h, position);
			// Stamp the stable ID AFTER rebind so the animator's per-tick
			// lookup via findViewHolderForItemId always finds this holder
			// and not a recycled holder that now shows a different item.
			h.setBoundItemId(mAdapter.getItemId(position));
			if (mItemAnimator != null) mItemAnimator.animateChange(h, null);
		}
		mLayoutDirty = true;
		requestLayout();
	}

	// ──────────────────────────────────────────────────────────────────────────
	// Snapshot & move-animation helpers
	// ──────────────────────────────────────────────────────────────────────────

	private void snapshotLeadingEdges() {
		for (int i = 0; i < mActive.size(); i++) {
			RecyclingAdapter.ViewHolder h = mActive.valueAt(i);
			int lead = (mOrientation == VERTICAL)
				? h.getItemView().getTop()
				: h.getItemView().getLeft();
			h.setPreLayoutLead(lead + mScrollOffset);
		}
	}

	private void dispatchMoveAnimations() {
		if (mItemAnimator == null || mItemStart == null) return;
		final int count = (mAdapter != null) ? mAdapter.getItemCount() : mItemStart.length;
		for (int i = 0; i < mActive.size(); i++) {
			RecyclingAdapter.ViewHolder h = mActive.valueAt(i);
			if (!h.hasPreLayoutLead()) continue;
			int adapterPos = mActive.keyAt(i);
			int slot = slotForPos(adapterPos, count);
			if (slot < 0 || slot >= mItemStart.length) { h.clearPreLayoutLead(); continue; }
			int newLead = mItemStart[slot];
			if (h.getPreLayoutLead() != newLead) {
				mItemAnimator.animateMove(h, h.getPreLayoutLead(), newLead, null);
			}
			h.clearPreLayoutLead();
		}
	}

	// ──────────────────────────────────────────────────────────────────────────
	// Measure
	// ──────────────────────────────────────────────────────────────────────────

	@Override
	protected void onMeasure(int widthSpec, int heightSpec) {
		int wSize = MeasureSpec.getSize(widthSpec);
		int hSize = MeasureSpec.getSize(heightSpec);
		int hMode = MeasureSpec.getMode(heightSpec);
		int wMode = MeasureSpec.getMode(widthSpec);

		if (mOrientation == HORIZONTAL
			&& mAdapter != null
			&& mAdapter.getItemCount() > 0
			&& hMode != MeasureSpec.EXACTLY) {
			int contentH = measureCrossAxisSize(hSize);
			int measuredH = (hMode == MeasureSpec.AT_MOST)
				? Math.min(contentH, hSize) : contentH;
			setMeasuredDimension(resolveSize(wSize, widthSpec), measuredH);
		} else if (mOrientation == VERTICAL
				   && mAdapter != null
				   && mAdapter.getItemCount() > 0
				   && wMode != MeasureSpec.EXACTLY) {
			int contentW = measureCrossAxisSize(wSize);
			int measuredW = (wMode == MeasureSpec.AT_MOST)
				? Math.min(contentW, wSize) : contentW;
			setMeasuredDimension(measuredW, resolveSize(hSize, heightSpec));
		} else {
			setMeasuredDimension(
				resolveSize(wSize, widthSpec),
				resolveSize(hSize, heightSpec));
		}
	}

	private int measureCrossAxisSize(int crossHint) {
		if (mAdapter == null) return 0;
		int count = mAdapter.getItemCount();
		if (count == 0) return 0;
		int maxSize = 0;
		android.util.SparseArray<Boolean> sampled = new android.util.SparseArray<Boolean>();
		for (int pos = 0; pos < count && sampled.size() < 3; pos++) {
			int vt = mAdapter.getItemViewType(pos);
			if (sampled.get(vt) != null) continue;
			sampled.put(vt, Boolean.TRUE);
			RecyclingAdapter.ViewHolder h = obtainFromPool(vt);
			if (h == null) h = mAdapter.onCreateViewHolder(this, vt);
			mAdapter.onBindViewHolder(h, pos);
			View child = h.getItemView();
			if (mOrientation == HORIZONTAL) {
				child.measure(
					MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
					crossHint > 0
					? MeasureSpec.makeMeasureSpec(crossHint, MeasureSpec.AT_MOST)
					: MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
				maxSize = Math.max(maxSize, child.getMeasuredHeight());
			} else {
				child.measure(
					crossHint > 0
					? MeasureSpec.makeMeasureSpec(crossHint, MeasureSpec.AT_MOST)
					: MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
					MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
				maxSize = Math.max(maxSize, child.getMeasuredWidth());
			}
			poolHolder(h);
		}
		return maxSize;
	}

	// ──────────────────────────────────────────────────────────────────────────
	// Layout
	// ──────────────────────────────────────────────────────────────────────────

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		if (mAdapter == null || mAdapter.getItemCount() == 0) {
			recycleNonDisappearing();
			return;
		}

		final int viewW = r - l;
		final int viewH = b - t;

		if (mLayoutDirty) computeGeometry(viewW, viewH);

		mScrollOffset = clampOffset(mScrollOffset);

		final int viewportSize = (mOrientation == VERTICAL) ? viewH : viewW;
		final int firstSlot    = findFirstVisible();
		final int lastSlot     = findLastVisible(viewportSize);
		final int count        = mAdapter.getItemCount();

		// ── Recycle off-screen holders ────────────────────────────────────────
		int i = 0;
		while (i < mActive.size()) {
			int pos  = mActive.keyAt(i);
			int slot = slotForPos(pos, count);
			if (slot < firstSlot || slot > lastSlot) {
				RecyclingAdapter.ViewHolder h = mActive.valueAt(i);
				if (!h.isPendingRemove()) {
					removeView(h.getItemView());
					poolHolder(h);
					mActive.removeAt(i);
					continue;
				}
			}
			i++;
		}

		// ── Bind newly visible holders ─────────────────────────────────────────
		for (int slot = firstSlot; slot <= lastSlot && slot < count; slot++) {
			int pos = posForSlot(slot, count);
			if (mActive.get(pos) != null) continue;
			int vt = mAdapter.getItemViewType(pos);
			RecyclingAdapter.ViewHolder holder = obtainFromPool(vt);
			if (holder == null) holder = mAdapter.onCreateViewHolder(this, vt);
			holder.setPosition(pos);
			holder.setViewType(vt);
			holder.clearPreLayoutLead();
			mAdapter.onBindViewHolder(holder, pos);
			// Stamp the stable item ID immediately after bind so that any
			// animation tick that calls findViewHolderForItemId() finds the
			// correct holder — not whatever item this view previously showed.
			holder.setBoundItemId(mAdapter.getItemId(pos));
			addView(holder.getItemView());
			mActive.put(pos, holder);
		}

		// ── Position every active child ───────────────────────────────────────
		for (int idx = 0; idx < mActive.size(); idx++) {
			int pos  = mActive.keyAt(idx);
			int slot = slotForPos(pos, count);
			if (slot < 0 || slot >= mItemStart.length) continue;
			RecyclingAdapter.ViewHolder h = mActive.valueAt(idx);
			View child = h.getItemView();
			int startPx = mItemStart[slot] - mScrollOffset;
			int endPx   = mItemEnd[slot]   - mScrollOffset;
			int size    = endPx - startPx;
			if (mOrientation == VERTICAL) {
				child.measure(
					MeasureSpec.makeMeasureSpec(viewW, MeasureSpec.EXACTLY),
					MeasureSpec.makeMeasureSpec(size,  MeasureSpec.EXACTLY));
				child.layout(0, startPx, viewW, endPx);
			} else {
				child.measure(
					MeasureSpec.makeMeasureSpec(size,  MeasureSpec.EXACTLY),
					MeasureSpec.makeMeasureSpec(viewH, MeasureSpec.EXACTLY));
				child.layout(startPx, 0, endPx, viewH);
			}
		}

		invalidate();
	}

	// ──────────────────────────────────────────────────────────────────────────
	// Draw (decorations + scroll bar + edge glow)
	// ──────────────────────────────────────────────────────────────────────────

	@Override
	protected void dispatchDraw(Canvas canvas) {
		for (int i = 0; i < mDecorations.size(); i++) {
			mDecorations.get(i).onDraw(canvas, this);
		}
		super.dispatchDraw(canvas);
		for (int i = 0; i < mDecorations.size(); i++) {
			mDecorations.get(i).onDrawOver(canvas, this);
		}
		drawScrollBar(canvas);
		drawEdgeGlow(canvas);
	}

	private void drawScrollBar(Canvas canvas) {
		if (!mScrollBarEnabled) return;
		final int viewportSize = (mOrientation == VERTICAL) ? getHeight() : getWidth();
		if (mTotalSize <= viewportSize) return;
		float ratio    = (float) viewportSize / mTotalSize;
		float barLen   = ratio * viewportSize;
		float barStart = ((float) mScrollOffset / mTotalSize) * viewportSize;
		float rx       = mScrollBarWidthPx / 2f;
		RectF rect;
		if (mOrientation == VERTICAL) {
			float barLeft  = getWidth()  - mScrollBarWidthPx - mScrollBarMarginPx;
			float barRight = getWidth()  - mScrollBarMarginPx;
			rect = new RectF(barLeft, barStart, barRight, barStart + barLen);
		} else {
			float barTop    = getHeight() - mScrollBarWidthPx - mScrollBarMarginPx;
			float barBottom = getHeight() - mScrollBarMarginPx;
			rect = new RectF(barStart, barTop, barStart + barLen, barBottom);
		}
		canvas.drawRoundRect(rect, rx, rx, mScrollBarPaint);
	}

	private void drawEdgeGlow(Canvas canvas) {
		if (mOrientation == VERTICAL) {
			if (!mEdgeGlowLeading.isFinished()) {
				int saved = canvas.save();
				canvas.rotate(-90f);
				canvas.translate(-getHeight(), 0f);
				mEdgeGlowLeading.setSize(getHeight(), getWidth());
				if (mEdgeGlowLeading.draw(canvas)) postInvalidate();
				canvas.restoreToCount(saved);
			}
			if (!mEdgeGlowTrailing.isFinished()) {
				int saved = canvas.save();
				canvas.rotate(90f);
				canvas.translate(0f, -getWidth());
				mEdgeGlowTrailing.setSize(getHeight(), getWidth());
				if (mEdgeGlowTrailing.draw(canvas)) postInvalidate();
				canvas.restoreToCount(saved);
			}
		} else {
			if (!mEdgeGlowLeading.isFinished()) {
				int saved = canvas.save();
				canvas.rotate(180f);
				canvas.translate(-getWidth(), -getHeight());
				mEdgeGlowLeading.setSize(getHeight(), getWidth());
				if (mEdgeGlowLeading.draw(canvas)) postInvalidate();
				canvas.restoreToCount(saved);
			}
			if (!mEdgeGlowTrailing.isFinished()) {
				int saved = canvas.save();
				canvas.rotate(90f);
				canvas.translate(0f, -getWidth());
				mEdgeGlowTrailing.setSize(getHeight(), getWidth());
				if (mEdgeGlowTrailing.draw(canvas)) postInvalidate();
				canvas.restoreToCount(saved);
			}
		}
	}

	// ──────────────────────────────────────────────────────────────────────────
	// Fling
	// ──────────────────────────────────────────────────────────────────────────

	@Override
	public void computeScroll() {
		if (mScroller.computeScrollOffset()) {
			int newOffset = (mOrientation == VERTICAL)
				? mScroller.getCurrY()
				: mScroller.getCurrX();
			if (newOffset != mScrollOffset) {
				int dOffset = newOffset - mScrollOffset;
				mScrollOffset = newOffset;
				dispatchScrolled(dOffset);
				requestLayout();
			}
			postInvalidate();
		} else if (mScrollState == SCROLL_STATE_SETTLING) {
			if (mSnapToPosition && !mSnapping && mItemStart != null) {
				int snapTarget = nearestSnapTarget(mScrollOffset);
				if (snapTarget != mScrollOffset) {
					mSnapping = true;
					int dx = (mOrientation == VERTICAL) ? 0 : snapTarget - mScrollOffset;
					int dy = (mOrientation == VERTICAL) ? snapTarget - mScrollOffset : 0;
					mScroller.startScroll(mScrollOffset, mScrollOffset, dx, dy, SNAP_DURATION_MS);
					postInvalidate();
					return;
				}
			}
			mSnapping = false;
			setScrollState(SCROLL_STATE_IDLE);
		}
	}

	// ──────────────────────────────────────────────────────────────────────────
	// Touch
	// ──────────────────────────────────────────────────────────────────────────

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		for (int i = 0; i < mTouchListeners.size(); i++) {
			OnItemTouchListener l = mTouchListeners.get(i);
			if (l.onInterceptTouchEvent(this, ev)) {
				mActiveTouch = l;
				MotionEvent cancel = MotionEvent.obtain(ev);
				cancel.setAction(MotionEvent.ACTION_CANCEL);
				for (int j = 0; j < mTouchListeners.size(); j++) {
					OnItemTouchListener other = mTouchListeners.get(j);
					if (other != l) other.onInterceptTouchEvent(this, cancel);
				}
				cancel.recycle();
				return true;
			}
		}

		final float primary   = primary(ev);
		final float secondary = secondary(ev);

		switch (ev.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				mIsDragging       = false;
				mDownPrimary      = primary;
				mDownSecondary    = secondary;
				mLastTouchPrimary = primary;
				if (!mScroller.isFinished()) mScroller.abortAnimation();
				return false;
			case MotionEvent.ACTION_MOVE:
				float dPrimary   = Math.abs(primary   - mDownPrimary);
				float dSecondary = Math.abs(secondary - mDownSecondary);
				if (dPrimary > mTouchSlop && dPrimary > dSecondary) {
					mIsDragging = true;
					return true;
				}
				return false;
		}
		return false;
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		if (mActiveTouch != null) {
			mActiveTouch.onTouchEvent(this, ev);
			int action = ev.getActionMasked();
			if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
				mActiveTouch = null;
			}
			return true;
		}

		if (mVelocityTracker == null) mVelocityTracker = VelocityTracker.obtain();
		mVelocityTracker.addMovement(ev);

		switch (ev.getActionMasked()) {

			case MotionEvent.ACTION_DOWN: {
					mIsDragging       = false;
					mDownPrimary      = primary(ev);
					mDownSecondary    = secondary(ev);
					mLastTouchPrimary = primary(ev);
					if (!mScroller.isFinished()) mScroller.abortAnimation();
					int pressedPos = positionForContentCoord((int) primary(ev) + mScrollOffset);
					if (pressedPos >= 0) {
						mPendingLongPos = pressedPos;
						mHandler.postDelayed(mLongPressRunnable, LONG_PRESS_TIMEOUT_MS);
					}
					return true;
				}

			case MotionEvent.ACTION_MOVE: {
					float p    = primary(ev);
					float absP = Math.abs(p - mDownPrimary);
					float absS = Math.abs(secondary(ev) - mDownSecondary);
					if (!mIsDragging && absP > mTouchSlop && absP > absS) {
						mIsDragging = true;
						mHandler.removeCallbacks(mLongPressRunnable);
						mPendingLongPos = -1;
						setScrollState(SCROLL_STATE_DRAGGING);
					}
					if (mIsDragging) {
						int delta   = (int) (mLastTouchPrimary - p);
						int before  = mScrollOffset;
						int clamped = clampOffset(mScrollOffset + delta);
						int over    = (mScrollOffset + delta) - clamped;
						mScrollOffset = clamped;
						if (mScrollOffset != before) dispatchScrolled(mScrollOffset - before);
						float viewPortSizeF = (mOrientation == VERTICAL) ? getHeight() : getWidth();
						if (over < 0) {
							mEdgeGlowLeading.onPull(-over / viewPortSizeF,
													(mOrientation == VERTICAL) ? ev.getX() / getWidth() : ev.getY() / getHeight());
							if (!mEdgeGlowTrailing.isFinished()) mEdgeGlowTrailing.onRelease();
						} else if (over > 0) {
							mEdgeGlowTrailing.onPull(over / viewPortSizeF,
													 (mOrientation == VERTICAL) ? 1f - ev.getX() / getWidth() : 1f - ev.getY() / getHeight());
							if (!mEdgeGlowLeading.isFinished()) mEdgeGlowLeading.onRelease();
						}
						requestLayout();
						mLastTouchPrimary = p;
					}
					return true;
				}

			case MotionEvent.ACTION_UP: {
					mHandler.removeCallbacks(mLongPressRunnable);
					if (!mIsDragging) {
						int pos = positionForContentCoord((int) primary(ev) + mScrollOffset);
						if (pos >= 0) {
							RecyclingAdapter.ViewHolder h = mActive.get(pos);
							if (h != null) h.getItemView().performClick();
						}
					} else {
						mVelocityTracker.computeCurrentVelocity(1000, mMaxFlingVelocity);
						int velocity = (mOrientation == VERTICAL)
							? -(int) mVelocityTracker.getYVelocity()
							:  -(int) mVelocityTracker.getXVelocity();
						if (Math.abs(velocity) > mMinFlingVelocity) {
							mSnapping = false;
							if (mOrientation == VERTICAL) {
								mScroller.fling(0, mScrollOffset, 0, velocity, 0, 0, 0, maxScroll());
							} else {
								mScroller.fling(mScrollOffset, 0, velocity, 0, 0, maxScroll(), 0, 0);
							}
							setScrollState(SCROLL_STATE_SETTLING);
							postInvalidate();
						} else if (mSnapToPosition && mItemStart != null) {
							int snapTarget = nearestSnapTarget(mScrollOffset);
							if (snapTarget != mScrollOffset) {
								mSnapping = true;
								if (mOrientation == VERTICAL) {
									mScroller.startScroll(0, mScrollOffset, 0, snapTarget - mScrollOffset, SNAP_DURATION_MS);
								} else {
									mScroller.startScroll(mScrollOffset, 0, snapTarget - mScrollOffset, 0, SNAP_DURATION_MS);
								}
								setScrollState(SCROLL_STATE_SETTLING);
								postInvalidate();
							} else {
								setScrollState(SCROLL_STATE_IDLE);
							}
						} else {
							setScrollState(SCROLL_STATE_IDLE);
						}
						releaseEdgeGlow();
					}
					cleanTouch();
					return true;
				}

			case MotionEvent.ACTION_CANCEL: {
					mHandler.removeCallbacks(mLongPressRunnable);
					releaseEdgeGlow();
					setScrollState(SCROLL_STATE_IDLE);
					cleanTouch();
					return true;
				}
		}
		return super.onTouchEvent(ev);
	}

	// ──────────────────────────────────────────────────────────────────────────
	// Geometry computation
	// ──────────────────────────────────────────────────────────────────────────

	private void computeGeometry(int viewW, int viewH) {
		recycleNonDisappearing();

		final int count = mAdapter.getItemCount();
		mItemStart    = new int[count];
		mItemEnd      = new int[count];
		mDecoTrailing = new int[count];

		int[] decoTrailingByAdapterPos = new int[count];
		for (int pos = 0; pos < count; pos++) {
			mDecoRect.setEmpty();
			for (int d = 0; d < mDecorations.size(); d++) {
				mDecorations.get(d).getItemOffsets(mDecoRect, pos, this);
			}
			decoTrailingByAdapterPos[pos] = (mOrientation == VERTICAL)
				? mDecoRect.bottom : mDecoRect.right;
		}

		int lead = 0;
		for (int slot = 0; slot < count; slot++) {
			int adapterPos = posForSlot(slot, count);
			mItemStart[slot]    = lead;
			mDecoTrailing[slot] = decoTrailingByAdapterPos[adapterPos];

			int vt = mAdapter.getItemViewType(adapterPos);
			RecyclingAdapter.ViewHolder h = obtainFromPool(vt);
			if (h == null) h = mAdapter.onCreateViewHolder(this, vt);
			h.setPosition(adapterPos);
			h.setViewType(vt);
			mAdapter.onBindViewHolder(h, adapterPos);

			View child = h.getItemView();
			int itemSize;
			if (mOrientation == VERTICAL) {
				child.measure(
					MeasureSpec.makeMeasureSpec(viewW, MeasureSpec.EXACTLY),
					MeasureSpec.makeMeasureSpec(0,     MeasureSpec.UNSPECIFIED));
				itemSize = child.getMeasuredHeight() + mDecoTrailing[slot];
			} else {
				child.measure(
					MeasureSpec.makeMeasureSpec(0,     MeasureSpec.UNSPECIFIED),
					MeasureSpec.makeMeasureSpec(viewH, MeasureSpec.EXACTLY));
				itemSize = child.getMeasuredWidth() + mDecoTrailing[slot];
			}
			mItemEnd[slot] = lead + itemSize;
			lead          += itemSize;
			if (mItemSpacingPx > 0 && slot < count - 1) lead += mItemSpacingPx;
			poolHolder(h);
		}

		mTotalSize   = lead;
		mLayoutDirty = false;
	}

	// ──────────────────────────────────────────────────────────────────────────
	// Slot mapping (reverse-layout support)
	// ──────────────────────────────────────────────────────────────────────────

	private int slotForPos(int adapterPos, int count) {
		return mReverseLayout ? (count - 1 - adapterPos) : adapterPos;
	}

	private int posForSlot(int slot, int count) {
		return mReverseLayout ? (count - 1 - slot) : slot;
	}

	// ──────────────────────────────────────────────────────────────────────────
	// Binary search helpers
	// ──────────────────────────────────────────────────────────────────────────

	private int findFirstVisible() {
		if (mItemEnd == null || mItemEnd.length == 0) return 0;
		int lo = 0, hi = mItemEnd.length - 1;
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (mItemEnd[mid] <= mScrollOffset) lo = mid + 1; else hi = mid;
		}
		return lo;
	}

	private int findLastVisible(int viewportSize) {
		if (mItemStart == null || mItemStart.length == 0) return -1;
		int bottom = mScrollOffset + viewportSize;
		int n = mItemStart.length;
		int lo = 0, hi = n - 1;
		while (lo < hi) {
			int mid = (lo + hi + 1) >>> 1;
			if (mItemStart[mid] >= bottom) hi = mid - 1; else lo = mid;
		}
		return lo;
	}

	private int positionForContentCoord(int contentCoord) {
		if (mItemStart == null || contentCoord < 0 || contentCoord >= mTotalSize) return -1;
		int count = mItemStart.length;
		int lo = 0, hi = count - 1;
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			if (contentCoord < mItemStart[mid]) hi = mid - 1;
			else if (contentCoord >= mItemEnd[mid]) lo = mid + 1;
			else return posForSlot(mid, count);
		}
		return -1;
	}

	// ──────────────────────────────────────────────────────────────────────────
	// Snap helper
	// ──────────────────────────────────────────────────────────────────────────

	private int nearestSnapTarget(int scrollOffset) {
		if (mItemStart == null || mItemStart.length == 0) return 0;
		int lo = 0, hi = mItemStart.length - 1;
		while (lo < hi) {
			int mid = (lo + hi + 1) >>> 1;
			if (mItemStart[mid] <= scrollOffset) lo = mid; else hi = mid - 1;
		}
		int best = mItemStart[lo];
		if (lo + 1 < mItemStart.length) {
			int next = mItemStart[lo + 1];
			if (Math.abs(next - scrollOffset) < Math.abs(best - scrollOffset)) best = next;
		}
		return clampOffset(best);
	}

	// ──────────────────────────────────────────────────────────────────────────
	// Pool helpers
	// ──────────────────────────────────────────────────────────────────────────

	private void poolHolder(RecyclingAdapter.ViewHolder h) {
		// Reset every visual property any helper may have altered so recycled
		// holders never carry stale translations or scale from a previous use.
		View v = h.getItemView();
		v.setAlpha(1f);
		v.setTranslationX(0f);
		v.setTranslationY(0f);
		v.setScaleX(1f);
		v.setScaleY(1f);

		List<RecyclingAdapter.ViewHolder> bucket = mPool.get(h.getViewType());
		if (bucket == null) {
			bucket = new ArrayList<RecyclingAdapter.ViewHolder>();
			mPool.put(h.getViewType(), bucket);
		}
		bucket.add(h);
	}

	private RecyclingAdapter.ViewHolder obtainFromPool(int viewType) {
		List<RecyclingAdapter.ViewHolder> bucket = mPool.get(viewType);
		if (bucket == null || bucket.isEmpty()) return null;
		return bucket.remove(bucket.size() - 1);
	}

	// ──────────────────────────────────────────────────────────────────────────
	// Helpers
	// ──────────────────────────────────────────────────────────────────────────

	private float primary(MotionEvent ev) {
		return (mOrientation == VERTICAL) ? ev.getY() : ev.getX();
	}

	private float secondary(MotionEvent ev) {
		return (mOrientation == VERTICAL) ? ev.getX() : ev.getY();
	}

	private void recycleNonDisappearing() {
		for (int i = 0; i < mActive.size(); i++) {
			RecyclingAdapter.ViewHolder h = mActive.valueAt(i);
			removeView(h.getItemView());
			poolHolder(h);
		}
		mActive.clear();
	}

	private void fullReset() {
		if (mItemAnimator != null) {
			// Stop persistent pulses before clearing views; otherwise a pulse
			// animator that fires after the view is detached will crash or
			// animate the wrong item.
			mItemAnimator.stopAllPulses();
			mItemAnimator.cancelAll();
		}
		recycleNonDisappearing();
		for (RecyclingAdapter.ViewHolder h : mDisappearing) {
			removeView(h.getItemView());
		}
		mDisappearing.clear();
		mLayoutDirty  = true;
		mScrollOffset = 0;
		mTotalSize    = 0;
	}

	private int clampOffset(int offset) {
		return Math.max(0, Math.min(offset, maxScroll()));
	}

	private int maxScroll() {
		int viewportSize = (mOrientation == VERTICAL) ? getHeight() : getWidth();
		return Math.max(0, mTotalSize - viewportSize);
	}

	private boolean isPositionValid(int position) {
		return mAdapter != null && mItemStart != null
			&& position >= 0 && position < mAdapter.getItemCount();
	}

	private void cleanTouch() {
		mIsDragging     = false;
		mPendingLongPos = -1;
		if (mVelocityTracker != null) { mVelocityTracker.recycle(); mVelocityTracker = null; }
	}

	private void releaseEdgeGlow() {
		mEdgeGlowLeading.onRelease();
		mEdgeGlowTrailing.onRelease();
	}

	private void setScrollState(int state) {
		if (state == mScrollState) return;
		mScrollState = state;
		for (int i = 0; i < mScrollListeners.size(); i++) {
			mScrollListeners.get(i).onScrollStateChanged(this, state);
		}
	}

	private void dispatchScrolled(int dOffset) {
		for (int i = 0; i < mScrollListeners.size(); i++) {
			mScrollListeners.get(i).onScrolled(this, dOffset);
		}
	}

	private int dpToPx(int dp) {
		return Math.round(dp * getContext().getResources().getDisplayMetrics().density);
	}
}

