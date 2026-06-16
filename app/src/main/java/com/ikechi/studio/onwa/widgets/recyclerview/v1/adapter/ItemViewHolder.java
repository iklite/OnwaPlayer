package com.ikechi.studio.onwa.widgets.recyclerview.v1.adapter;

import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import com.ikechi.studio.onwa.player.R;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.listener.OnItemClickListener;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.model.Item;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.adapter.RecyclingAdapter;


/**
 * Concrete {@link RecyclingAdapter.ViewHolder} for a data-item row.
 *
 * <h3>Encapsulation</h3>
 * Every bound view reference is {@code private}.  The only public API is:
 * <ul>
 *   <li>{@link #bind(Item, int, OnItemClickListener)} — populates and wires events</li>
 *   <li>{@link #getItemView()} (inherited) — root view</li>
 *   <li>{@link #getAdapterPosition()} (inherited) — current position</li>
 * </ul>
 *
 * <p>Zero lambda expressions. API 18+.
 */
public class ItemViewHolder extends RecyclingAdapter.ViewHolder {

    // ── Private view references ───────────────────────────────────────────────
    private final View        mContainer;
    private final TextView    mTvTitle;
    private final TextView    mTvDescription;
    private final TextView    mTvCategory;
    private final ImageButton mBtnFavorite;

    // ── Constructor ───────────────────────────────────────────────────────────

    public ItemViewHolder(View itemView) {
        super(itemView);
        mContainer     = itemView.findViewById(R.id.item_container);
        mTvTitle       = itemView.findViewById(R.id.tv_title);
        mTvDescription = itemView.findViewById(R.id.tv_description);
        mTvCategory    = itemView.findViewById(R.id.tv_category);
        mBtnFavorite   = itemView.findViewById(R.id.btn_favorite);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Populates all views with data from {@code item} and wires click listeners.
     * Always called from
     * {@link SectionedItemAdapter#onBindViewHolder(RecyclingAdapter.ViewHolder, int)}.
     *
     * @param item     Data model for this row.
     * @param position Adapter position (captured at bind time).
     * @param listener Host listener.
     */
    public void bind(final Item item, final int position,
                     final OnItemClickListener listener) {
        mTvTitle.setText(item.getTitle());
        mTvDescription.setText(item.getDescription());
        mTvCategory.setText(item.getCategory());
        updateFavIcon(item.isFavorite());

        mContainer.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (listener != null) listener.onItemClick(item, position);
				}
			});

        mContainer.setOnLongClickListener(new View.OnLongClickListener() {
				@Override
				public boolean onLongClick(View v) {
					if (listener != null) return listener.onItemLongClick(item, position);
					return false;
				}
			});

        mBtnFavorite.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					boolean next = !item.isFavorite();
					item.setFavorite(next);
					updateFavIcon(next);
					if (listener != null) listener.onFavoriteClick(item, position, next);
				}
			});
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void updateFavIcon(boolean isFav) {
        mBtnFavorite.setImageResource(isFav
									  ? android.R.drawable.btn_star_big_on
									  : android.R.drawable.btn_star_big_off);
    }
}

