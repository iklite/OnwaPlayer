package com.ikechi.studio.onwa.widgets.recyclerview.v1.listener;

import com.ikechi.studio.onwa.widgets.recyclerview.v1.model.Item;


/**
 * Three-callback interface for item-level user interactions.
 */
public interface OnItemClickListener {

    /** Called when the user taps an item row. */
    void onItemClick(Item item, int position);

    /**
     * Called when the user long-presses an item row.
     * @return {@code true} if the event is consumed.
     */
    boolean onItemLongClick(Item item, int position);

    /**
     * Called when the favourite button is toggled.
     * @param isFavorite The <em>new</em> state after the toggle.
     */
    void onFavoriteClick(Item item, int position, boolean isFavorite);
}

