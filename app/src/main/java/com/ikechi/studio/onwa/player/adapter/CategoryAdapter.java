package com.ikechi.studio.onwa.player.adapter;

import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.ikechi.studio.onwa.player.R;
import com.ikechi.studio.onwa.player.models.Category;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.adapter.RecyclingAdapter;

import java.util.List;

/**
 * CategoryAdapter — drives the category grid in LibraryCategoryFragment.
 *
 * Fixes applied vs. original:
 *  - category_track_count TextView is now populated (it was inflated in XML but
 *    never set, so it always showed the placeholder "track count" string).
 *  - Icon tint is applied programmatically via setColorFilter() so it works on
 *    API 18 (android:tint and android:colorFilter attributes require API 21+).
 */
public class CategoryAdapter extends RecyclingAdapter {

    private final List<Category>       mCategories;
    private       OnCategoryClickListener mListener;

    /** Accent colour applied to category icons. Change to match your theme. */
    private static final int ICON_TINT = 0xFFF5A623;   // gold accent

    public CategoryAdapter(List<Category> categories) {
        mCategories = categories;
        setHasStableIds(true);
    }

    public void setOnCategoryClickListener(OnCategoryClickListener listener) {
        mListener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
			.inflate(R.layout.item_category, parent, false);
        return new CategoryViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Category cat = mCategories.get(position);
        ((CategoryViewHolder) holder).bind(cat, position, mListener);
    }

    @Override
    public int getItemCount() { return mCategories.size(); }

    @Override
    public long getItemId(int position) {
        return mCategories.get(position).getId();
    }

    // -------------------------------------------------------------------------
    // ViewHolder
    // -------------------------------------------------------------------------

    public static class CategoryViewHolder extends ViewHolder {
        private final ImageView mIcon;
        private final TextView  mName;
        private final TextView  mTrackCount;
        private final View      mItemView;

        public CategoryViewHolder(View itemView) {
            super(itemView);
            mItemView   = itemView;
            mIcon       = itemView.findViewById(R.id.category_icon);
            mName       = itemView.findViewById(R.id.category_name);
            mTrackCount = itemView.findViewById(R.id.category_track_count);
        }

        public void bind(final Category category, final int position,
                         final OnCategoryClickListener listener) {
            mName.setText(category.getName());

            // Icon resource + programmatic tint (API 18-compatible)
            mIcon.setImageResource(category.getIconResId());
            mIcon.setColorFilter(ICON_TINT, PorterDuff.Mode.SRC_IN);

            // Track count (the XML placeholder "track count" was never replaced)
            int count = (category.getItems() != null) ? category.getItems().size() : 0;
            if (mTrackCount != null) {
                if (count > 0) {
                    mTrackCount.setVisibility(View.VISIBLE);
                    mTrackCount.setText(count + (count == 1 ? " song" : " songs"));
                } else {
                    mTrackCount.setVisibility(View.GONE);
                }
            }

            mItemView.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						if (listener != null) listener.onCategoryClick(category, position);
					}
				});
        }
    }

    // -------------------------------------------------------------------------
    // Listener interface
    // -------------------------------------------------------------------------

    public interface OnCategoryClickListener {
        void onCategoryClick(Category category, int position);
    }
}

