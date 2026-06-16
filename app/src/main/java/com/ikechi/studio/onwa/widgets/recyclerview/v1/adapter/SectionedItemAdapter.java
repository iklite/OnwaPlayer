package com.ikechi.studio.onwa.widgets .recyclerview.v1.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.ikechi.studio.onwa.player.R;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.listener.OnItemClickListener;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.model.Item;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.adapter.RecyclingAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.model.ListRow;


/**
 * Concrete adapter that supports two view types in a single flat list:
 * <ul>
 *   <li>{@link ListRow#TYPE_HEADER} → {@link HeaderViewHolder} (section label)</li>
 *   <li>{@link ListRow#TYPE_ITEM}   → {@link ItemViewHolder}   (interactive data row)</li>
 * </ul>
 *
 * <p>This class extends {@link RecyclingAdapter} directly (rather than
 * {@link com.ikechi_studio.widgets.recyclerview.v1.widget.BaseListAdapter}) because
 * it manages two model types in one list via {@link ListRow} — a design that
 * naturally requires full adapter control.  For single-model lists, extend
 * {@code BaseListAdapter<T>} instead.
 *
 * <h3>CRUD helpers</h3>
 * {@link #addItem(Item)}, {@link #addItemAt(int, Item)}, {@link #removeItemAt(int)},
 * {@link #updateItemAt(int, Item)}, {@link #moveItem(int, int)},
 * {@link #clearAll()}, {@link #replaceAll(List)}.
 *
 * <p>Zero lambda expressions. API 18+.
 */
public class SectionedItemAdapter extends RecyclingAdapter {

    private final Context             mContext;
    private final List<ListRow>       mRows;
    private       OnItemClickListener mListener;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * @param context Application / Activity context. Must not be null.
     * @param rows    Initial row list (may contain headers and items). Must not be null.
     */
    public SectionedItemAdapter(Context context, List<ListRow> rows) {
        if (context == null) throw new IllegalArgumentException("context must not be null");
        if (rows   == null) throw new IllegalArgumentException("rows must not be null");
        mContext = context;
        mRows    = new ArrayList<ListRow>(rows);
    }

    // ── Listener ──────────────────────────────────────────────────────────────

    /**
     * Attaches a click listener.  Pass {@code null} to remove the listener.
     *
     * @param listener The listener to receive click / long-click / favourite events.
     */
    public void setOnItemClickListener(OnItemClickListener listener) {
        mListener = listener;
    }

    // ── RecyclingAdapter overrides ────────────────────────────────────────────

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(mContext);
        if (viewType == ListRow.TYPE_HEADER) {
            View v = inf.inflate(R.layout.item_header, parent, false);
            return new HeaderViewHolder(v);
        } else {
            View v = inf.inflate(R.layout.item_row, parent, false);
            return new ItemViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        ListRow row = mRows.get(position);
        if (row.isHeader()) {
            ((HeaderViewHolder) holder).bind(row.getTitle());
        } else {
            ((ItemViewHolder) holder).bind(row.getItem(), position, mListener);
        }
    }

    @Override
    public int getItemCount() { return mRows.size(); }

    @Override
    public int getItemViewType(int position) { return mRows.get(position).getViewType(); }

    @Override
    public long getItemId(int position) {
        ListRow row = mRows.get(position);
        return row.isHeader()
			? -(long) row.getTitle().hashCode()
			: row.getItem().getId();
    }

    // ── Data accessors ────────────────────────────────────────────────────────

    /**
     * Returns an immutable snapshot of the current row list.
     */
    public List<ListRow> getRows() {
        return Collections.unmodifiableList(new ArrayList<ListRow>(mRows));
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    /**
     * Appends a data item to the end of the list.
     *
     * @param item The item to add.
     */
    public void addItem(Item item) {
        mRows.add(ListRow.item(item));
        notifyItemInserted(mRows.size() - 1);
    }

    /**
     * Inserts a data item at the given flat adapter position.
     *
     * @param position Target position.
     * @param item     The item to insert.
     */
    public void addItemAt(int position, Item item) {
        mRows.add(position, ListRow.item(item));
        notifyItemInserted(position);
    }

    /**
     * Removes the row at {@code position}.
     *
     * @param position The position to remove.
     * @return The removed row, or {@code null} if position was out of range.
     */
    public ListRow removeItemAt(int position) {
        if (position < 0 || position >= mRows.size()) return null;
        ListRow removed = mRows.remove(position);
        notifyItemRemoved(position);
        return removed;
    }

    /**
     * Replaces the row at {@code position} with a new data item.
     *
     * @param position Target position.
     * @param item     The replacement item.
     */
    public void updateItemAt(int position, Item item) {
        if (position < 0 || position >= mRows.size()) return;
        mRows.set(position, ListRow.item(item));
        notifyItemChanged(position);
    }

    /**
     * Moves the row at {@code fromPosition} to {@code toPosition}.
     *
     * @param fromPosition Source index.
     * @param toPosition   Destination index.
     */
    public void moveItem(int fromPosition, int toPosition) {
        if (fromPosition == toPosition) return;
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                Collections.swap(mRows, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(mRows, i, i - 1);
            }
        }
        notifyItemMoved(fromPosition, toPosition);
    }

    /**
     * Removes all rows and notifies the view.
     */
    public void clearAll() {
        int oldSize = mRows.size();
        mRows.clear();
        notifyItemRangeRemoved(0, oldSize);
    }

    /**
     * Replaces the entire data set atomically and triggers a full rebind.
     *
     * @param rows Replacement list. Must not be null.
     */
    public void replaceAll(List<ListRow> rows) {
        if (rows == null) throw new IllegalArgumentException("rows must not be null");
        mRows.clear();
        mRows.addAll(rows);
        notifyDataSetChanged();
    }
}

