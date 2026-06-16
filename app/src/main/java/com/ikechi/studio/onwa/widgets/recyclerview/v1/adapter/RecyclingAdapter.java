package com.ikechi.studio.onwa.widgets.recyclerview.v1.adapter;

import android.view.View;
import android.view.ViewGroup;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.widget.*;


/**
 * Abstract base adapter for {@link RecyclingView}.
 *
 * <h3>Library contract</h3>
 * <ul>
 *   <li>Subclass {@link BaseListAdapter} for typed single-model lists.</li>
 *   <li>Extend this class directly for full, multi-view-type control.</li>
 * </ul>
 *
 * <h3>Encapsulation guarantee</h3>
 * Every field in {@link ViewHolder} is {@code private}.  The only public
 * surface is {@link ViewHolder#getItemView()} and
 * {@link ViewHolder#getAdapterPosition()}.  All other bookkeeping is
 * accessed through package-private methods used exclusively by
 * {@link RecyclingView} (which lives in the same package).
 *
 * <p>Zero external dependencies. Zero lambda expressions. API 18+.
 */
public abstract class RecyclingAdapter {

    // ── Stable-ID sentinel ────────────────────────────────────────────────────

    /**
     * Sentinel value returned by {@link ViewHolder#getBoundItemId()} when no
     * stable ID has been assigned (either because the adapter does not have
     * stable IDs, or because the holder has not yet been bound).
     */
    public static final long NO_ID = -1L;

    // ── Registration ─────────────────────────────────────────────────────────

    private RecyclingView mAttachedView;

    // ── Stable-ID flag ────────────────────────────────────────────────────────

    private boolean mHasStableIds = false;

    /**
     * Declares whether each item in the data set has a stable, unique ID.
     *
     * <p>When {@code true}, the RecyclingView stores the value returned by
     * {@link #getItemId(int)} alongside each bound {@link ViewHolder} so that
     * animations (change-pulse, persistent pulse) can look up the <em>correct</em>
     * holder even after recycling has moved views around.
     *
     * <p>You must also override {@link #getItemId(int)} to return a genuinely
     * unique, stable {@code long} for each item in your data set — not the
     * position.  Using the position as an ID is the default and is NOT stable.
     *
     * @param hasStableIds {@code true} to enable stable-ID tracking.
     */
    public final void setHasStableIds(boolean hasStableIds) {
        mHasStableIds = hasStableIds;
    }

    /** Returns {@code true} if this adapter uses stable, unique item IDs. */
    public final boolean hasStableIds() { return mHasStableIds; }

    /** Called by RecyclingView only. */
    public final void attachTo(RecyclingView view) { mAttachedView = view; }

    /** Called by RecyclingView only. */
    public final void detach() { mAttachedView = null; }

    // ── Abstract API (implement in your adapter) ──────────────────────────────

    /**
     * Called when the RecyclingView needs a new, uninflated row for the given
     * {@code viewType}.  Inflate your item layout here and return a concrete
     * {@link ViewHolder} wrapping it.
     *
     * @param parent   The RecyclingView — pass as the root for inflation.
     * @param viewType The view type returned by {@link #getItemViewType(int)}.
     * @return A newly created {@link ViewHolder}. Must not be null.
     */
    public abstract ViewHolder onCreateViewHolder(ViewGroup parent, int viewType);

    /**
     * Bind data to the {@link ViewHolder} at the given {@code position}.
     * This method may be called on a recycled holder, so always overwrite every
     * field you set here.
     *
     * @param holder   The holder to populate.
     * @param position The adapter position of the item to bind.
     */
    public abstract void onBindViewHolder(ViewHolder holder, int position);

    /**
     * Returns the total number of items managed by this adapter.
     */
    public abstract int getItemCount();

    /**
     * Returns the view-type integer for the item at {@code position}.
     * The default implementation returns {@code 0} (single view type).
     * Override when you have multiple row layouts.
     */
    public int getItemViewType(int position) { return 0; }

    /**
     * Returns a stable, unique ID for the item at {@code position}.
     * Override this (and call {@code setHasStableIds(true)} on the RecyclingView)
     * to enable stable-ID optimisation.  Default: the position itself.
     */
    public long getItemId(int position) { return position; }

    // ── Data-change notifications ──────────────────────────────────────────────

    /** Notify that the entire data set was replaced. Triggers a full rebind. */
    public final void notifyDataSetChanged() {
        if (mAttachedView != null) mAttachedView.onAdapterDataChanged();
    }

    /** Notify that one item was inserted at {@code position}. */
    public final void notifyItemInserted(int position) {
        if (mAttachedView != null) mAttachedView.onAdapterItemInserted(position);
    }

    /** Notify that one item at {@code position} was removed. */
    public final void notifyItemRemoved(int position) {
        if (mAttachedView != null) mAttachedView.onAdapterItemRemoved(position);
    }

    /** Notify that the item at {@code position} changed in-place. */
    public final void notifyItemChanged(int position) {
        if (mAttachedView != null) mAttachedView.onAdapterItemChanged(position);
    }

    /** Notify that an item moved from {@code fromPosition} to {@code toPosition}. */
    public final void notifyItemMoved(int fromPosition, int toPosition) {
        if (mAttachedView != null) mAttachedView.onAdapterItemMoved(fromPosition, toPosition);
    }

    /** Notify that {@code itemCount} items were inserted starting at {@code positionStart}. */
    public final void notifyItemRangeInserted(int positionStart, int itemCount) {
        if (mAttachedView != null) mAttachedView.onAdapterRangeInserted(positionStart, itemCount);
    }

    /** Notify that {@code itemCount} items were removed starting at {@code positionStart}. */
    public final void notifyItemRangeRemoved(int positionStart, int itemCount) {
        if (mAttachedView != null) mAttachedView.onAdapterRangeRemoved(positionStart, itemCount);
    }

    // =========================================================================
    // ViewHolder
    // =========================================================================

    /**
     * Base ViewHolder.
     *
     * <h3>Encapsulation</h3>
     * Every internal field is {@code private}.  External code may only call:
     * <ul>
     *   <li>{@link #getItemView()}          — the inflated row view</li>
     *   <li>{@link #getAdapterPosition()}   — current adapter position</li>
     * </ul>
     * All other bookkeeping is accessed through {@code final} (package-private)
     * methods that are only visible to {@link RecyclingView} in the same package.
     */
    public abstract static class ViewHolder {

        // ── Public surface ────────────────────────────────────────────────────

        private final View mItemView;

        /**
         * Construct a ViewHolder wrapping {@code itemView}.
         *
         * @param itemView The root view of this row. Must not be null.
         */
        public ViewHolder(View itemView) {
            if (itemView == null) {
                throw new IllegalArgumentException("itemView passed to ViewHolder must not be null");
            }
            mItemView = itemView;
        }

        /** Returns the root view of this row. */
        public final View getItemView() { return mItemView; }

        /** Returns the adapter position this holder is currently bound to, or -1 if unbound. */
        public final int getAdapterPosition() { return mPosition; }

        // ── Private bookkeeping — only RecyclingView (same package) may touch ─

        /** Current adapter position. */
        private int  mPosition   = -1;

        /** View-type stored at bind time for pool keying. */
        private int  mViewType   = 0;

        /**
         * The stable item ID that was current when this holder was last bound,
         * or {@link RecyclingAdapter#NO_ID} if the adapter does not use stable IDs
         * or the holder has not yet been bound.
         *
         * <p>Written by RecyclingView immediately after every {@code onBindViewHolder}
         * call.  Read by {@link DefaultItemAnimator} to verify that a View still
         * represents the intended item before applying an animation tick — preventing
         * the pulse-wrong-item bug that occurs when a holder is recycled mid-animation.
         */
        private long mBoundItemId = NO_ID;

        /** True while a remove-animation is playing; must not be recycled. */
        private boolean mPendingRemove = false;

        /** True while an add-animation is playing. */
        private boolean mPendingAdd    = false;

        /**
         * Content-coordinate leading edge (top in VERTICAL, left in HORIZONTAL)
         * captured just before a layout change, so move-animations can compute delta.
         */
        private int     mPreLayoutLead = Integer.MIN_VALUE;

        // ── Package-private accessors — RecyclingView use only ───────────────

        public final void    setPosition(int pos)           { mPosition = pos; }
        public final void    setViewType(int vt)            { mViewType = vt; }
        public final int     getViewType()                  { return mViewType; }
        public final void    setBoundItemId(long id)        { mBoundItemId = id; }
        public final long    getBoundItemId()               { return mBoundItemId; }
        public final void    setPendingRemove(boolean flag) { mPendingRemove = flag; }
        public final boolean isPendingRemove()              { return mPendingRemove; }
        public final void    setPendingAdd(boolean flag)    { mPendingAdd = flag; }
        public final boolean isPendingAdd()                 { return mPendingAdd; }
        public final void    setPreLayoutLead(int lead)     { mPreLayoutLead = lead; }
        public final int     getPreLayoutLead()             { return mPreLayoutLead; }
        public final boolean hasPreLayoutLead() {
            return mPreLayoutLead != Integer.MIN_VALUE;
        }
        public final void    clearPreLayoutLead()           { mPreLayoutLead = Integer.MIN_VALUE; }
    }
}

