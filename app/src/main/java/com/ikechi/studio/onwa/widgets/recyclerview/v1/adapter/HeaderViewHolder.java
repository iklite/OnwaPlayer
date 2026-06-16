package com.ikechi.studio.onwa.widgets.recyclerview.v1.adapter;

import android.view.View;
import android.widget.TextView;

import com.ikechi.studio.onwa.player.R;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.adapter.RecyclingAdapter;


/**
 * Concrete {@link RecyclingAdapter.ViewHolder} for a section-header row.
 *
 * <p>The label {@link TextView} is {@code private}; external code calls
 * {@link #bind(String)} to populate it.
 *
 * <p>Zero lambda expressions. API 18+.
 */
public class HeaderViewHolder extends RecyclingAdapter.ViewHolder {

    private final TextView mTvHeader;

    public HeaderViewHolder(View itemView) {
        super(itemView);
        mTvHeader = itemView.findViewById(R.id.tv_header_label);
    }

    /**
     * Sets the header label text.
     *
     * @param title The section title to display.
     */
    public void bind(String title) {
        mTvHeader.setText(title != null ? title : "");
    }
}

