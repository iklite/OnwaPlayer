package com.ikechi.studio.onwa.widgets.recyclerview.v1.adapter;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Abstract, typed base adapter for single-model lists.
 *
 * <h3>Purpose</h3>
 * Extend this class when all rows in your list share one model type {@code T}.
 * It manages the {@code List<T>} and exposes a full CRUD API so you only need
 * to implement {@link #onCreateViewHolder} and {@link #onBindViewHolder}.
 *
 * <pre>
 * public class MyAdapter extends BaseListAdapter&lt;MyModel&gt; {
 *
 *     public MyAdapter(List&lt;MyModel&gt; items) { super(items); }
 *
 *     &#64;Override
 *     public RecyclingAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
 *         View v = LayoutInflater.from(parent.getContext())
 *                                .inflate(R.layout.my_row, parent, false);
 *         return new MyViewHolder(v);
 *     }
 *
 *     &#64;Override
 *     public void onBindViewHolder(RecyclingAdapter.ViewHolder holder, int position) {
 *         ((MyViewHolder) holder).bind(getItem(position));
 *     }
 * }
 * </pre>
 *
 * <h3>Library design note</h3>
 * This class does <em>not</em> override {@link #getItemViewType(int)} or
 * {@link #getItemId(int)}.  Override those in your subclass if needed.
 *
 * <p>Zero external dependencies. Zero lambda expressions. API 18+.
 *
 * @param <T> The type of model object held in the list.
 */
public abstract class BaseListAdapter<T> extends RecyclingAdapter {

    private final List<T> mItems;

    /**
     * Constructs the adapter with a defensive copy of the supplied list.
     *
     * @param items Initial data (must not be {@code null}).
     */
    public BaseListAdapter(List<T> items) {
        if (items == null) throw new IllegalArgumentException("items must not be null");
        mItems = new ArrayList<T>(items);
    }

    // ── Data access ───────────────────────────────────────────────────────────

    /**
     * Returns the model object at {@code position}.
     *
     * @param position Adapter position (0-based).
     * @return The item at that position.
     * @throws IndexOutOfBoundsException if position is out of range.
     */
    public final T getItem(int position) {
        return mItems.get(position);
    }

    /** {@inheritDoc} */
    @Override
    public final int getItemCount() {
        return mItems.size();
    }

    /**
     * Returns an immutable snapshot of the current list.
     * Modifying the returned list has no effect on the adapter.
     */
    public final List<T> getItems() {
        return Collections.unmodifiableList(new ArrayList<T>(mItems));
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    /**
     * Appends {@code item} to the end of the list and notifies the view.
     *
     * @param item The item to add.
     */
    public final void addItem(T item) {
        mItems.add(item);
        notifyItemInserted(mItems.size() - 1);
    }

    /**
     * Inserts {@code item} at the given {@code position} and notifies the view.
     *
     * @param position Target index (0-based).
     * @param item     The item to insert.
     */
    public final void addItemAt(int position, T item) {
        mItems.add(position, item);
        notifyItemInserted(position);
    }

    /**
     * Appends all items in {@code newItems} and notifies the view of a range insertion.
     *
     * @param newItems Items to append (must not be {@code null}).
     */
    public final void addAll(List<T> newItems) {
        if (newItems == null) throw new IllegalArgumentException("newItems must not be null");
        int start = mItems.size();
        mItems.addAll(newItems);
        notifyItemRangeInserted(start, newItems.size());
    }

    /**
     * Removes the item at {@code position} and notifies the view.
     *
     * @param position Index of the item to remove.
     * @return The removed item.
     */
    public final T removeItemAt(int position) {
        T removed = mItems.remove(position);
        notifyItemRemoved(position);
        return removed;
    }

    /**
     * Replaces the item at {@code position} with {@code item} and notifies the view.
     *
     * @param position Target index.
     * @param item     The replacement item.
     */
    public final void updateItemAt(int position, T item) {
        mItems.set(position, item);
        notifyItemChanged(position);
    }

    /**
     * Moves the item at {@code fromPosition} to {@code toPosition} using
     * successive swaps (stable, preserves relative order of other items).
     *
     * @param fromPosition Source index.
     * @param toPosition   Destination index.
     */
    public final void moveItem(int fromPosition, int toPosition) {
        if (fromPosition == toPosition) return;
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                Collections.swap(mItems, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(mItems, i, i - 1);
            }
        }
        notifyItemMoved(fromPosition, toPosition);
    }

    /**
     * Removes all items from the list and notifies the view of the full range removal.
     */
    public final void clearAll() {
        int oldSize = mItems.size();
        mItems.clear();
        notifyItemRangeRemoved(0, oldSize);
    }

    /**
     * Replaces the entire data set atomically and triggers a full rebind.
     *
     * @param newItems Replacement list (must not be {@code null}).
     */
    public final void replaceAll(List<T> newItems) {
        if (newItems == null) throw new IllegalArgumentException("newItems must not be null");
        mItems.clear();
        mItems.addAll(newItems);
        notifyDataSetChanged();
    }
}

