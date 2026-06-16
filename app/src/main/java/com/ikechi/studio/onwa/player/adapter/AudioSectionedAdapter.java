package com.ikechi.studio.onwa.player.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.ikechi.studio.onwa.player.R;
import com.ikechi.studio.onwa.player.fragment.TrackListFragment;
import com.ikechi.studio.onwa.player.models.AudioItem;
import com.ikechi.studio.onwa.player.models.AudioListRow;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.adapter.RecyclingAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import android.net.Uri;

/**
 * AudioSectionedAdapter — powers the track list inside TrackListFragment.
 */
public class AudioSectionedAdapter extends RecyclingAdapter {

    private final List<AudioListRow> mRows;
    private OnAudioItemClickListener  mListener;
    private OnMoreOptionsClickListener mOptionsMoreClickListener;
    private OnFavoriteClickListener favoriteListener;
    private TrackListFragment mTrackListFragment;

    public AudioSectionedAdapter(List<AudioListRow> rows) {
        mRows = new ArrayList<AudioListRow>(rows);
        setHasStableIds(true);
    }

    /** Called by TrackListFragment so the adapter can query multi‑select state. */
    public void setTrackListFragment(TrackListFragment fragment) {
        mTrackListFragment = fragment;
    }

    public void setOnAudioItemClickListener(OnAudioItemClickListener listener) {
        mListener = listener;
    }

    public void setMoreOptionsClickListener(OnMoreOptionsClickListener listener) {
        mOptionsMoreClickListener = listener;
    }

    public void setFavoriteClickListener(final OnFavoriteClickListener l) {
        favoriteListener = l;
    }

    public void updateRows(List<AudioListRow> newRows) {
        mRows.clear();
        mRows.addAll(newRows);
        notifyDataSetChanged();
    }

    public void notifyItemChangedByPath(String filePath) {
        if (filePath == null) return;
        for (int i = 0; i < mRows.size(); i++) {
            AudioListRow row = mRows.get(i);
            if (row.getType() == AudioListRow.TYPE_ITEM
                && filePath.equals(row.getItem().getFilePath())) {
                notifyItemChanged(i);
                return;
            }
        }
    }

    public void notifyItemChangedByUri(Uri uri) {
        if (uri == null) return;
        for (int i = 0; i < mRows.size(); i++) {
            AudioListRow row = mRows.get(i);
            if (row.getType() == AudioListRow.TYPE_ITEM
                && uri.equals(row.getItem().getUri())) {
                notifyItemChanged(i);
                return;
            }
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == AudioListRow.TYPE_HEADER) {
            View v = inflater.inflate(R.layout.item_header, parent, false);
            return new HeaderViewHolder(v);
        } else {
            View v = inflater.inflate(R.layout.item_audio_row, parent, false);
            return new AudioItemViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        AudioListRow row = mRows.get(position);
        if (row.getType() == AudioListRow.TYPE_HEADER) {
            ((HeaderViewHolder) holder).bind(row.getHeader());
        } else {
            ((AudioItemViewHolder) holder).bind(
                row.getItem(), position, mListener, mOptionsMoreClickListener,
                favoriteListener, mTrackListFragment);
        }
    }

    @Override
    public int getItemCount() { return mRows.size(); }

    @Override
    public int getItemViewType(int position) { return mRows.get(position).getType(); }

    @Override
    public long getItemId(int position) {
        AudioListRow row = mRows.get(position);
        if (row.getType() == AudioListRow.TYPE_HEADER) {
            return -(long) row.getHeader().hashCode();
        } else {
            return row.getItem().getUri().hashCode();
        }
    }

    // -------------------------------------------------------------------------
    // ViewHolders
    // -------------------------------------------------------------------------

    public static class HeaderViewHolder extends ViewHolder {
        private final TextView mTitle;

        public HeaderViewHolder(View itemView) {
            super(itemView);
            mTitle = itemView.findViewById(R.id.tv_header_label);
        }

        public void bind(String header) {
            mTitle.setText(header);
        }
    }

    public static class AudioItemViewHolder extends ViewHolder {
        private final ImageButton mFavBtn;
        private final ImageButton mOptionsMore;
        private final TextView  mTitle;
        private final TextView  mArtist;
        private final TextView  mDuration;
        private final View      mItemView;

        public AudioItemViewHolder(View itemView) {
            super(itemView);
            mItemView    = itemView;
            mTitle       = itemView.findViewById(R.id.tv_title);
            mArtist      = itemView.findViewById(R.id.tv_artist);
            mDuration    = itemView.findViewById(R.id.tv_duration);
            mFavBtn      = itemView.findViewById(R.id.audio_item_favorite);
            mOptionsMore = itemView.findViewById(R.id.options_more);
        }

        public void bind(final AudioItem item,
                         final int position,
                         final OnAudioItemClickListener listener,
                         final OnMoreOptionsClickListener optionsListener,
                         final OnFavoriteClickListener favoriteListener,
                         final TrackListFragment trackListFragment) {

            mTitle.setText(item.getTitle());
            mArtist.setText(item.getArtist());
            mDuration.setText(formatDuration(item.getDuration()));

            // ── Multi‑select highlight ──────────────────────────────────
            if (trackListFragment != null && trackListFragment.isItemSelected(position)) {
                mItemView.setBackgroundColor(0x332196F3); // Light blue
                mFavBtn.setVisibility(View.GONE);
                mOptionsMore.setVisibility(View.GONE);
            } else {
                mItemView.setBackgroundColor(Color.TRANSPARENT);
                mFavBtn.setVisibility(View.VISIBLE);
                mOptionsMore.setVisibility(View.VISIBLE);
            }

            // ── Favorite button ─────────────────────────────────────────
            int resId = item.isFavorite()
                ? R.drawable.ic_favorite_filled
                : R.drawable.ic_favorite_border;
            mFavBtn.setImageResource(resId);

            mFavBtn.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						if (favoriteListener != null) {
							favoriteListener.onFavoriteClicked(item, position);
							int resId = item.isFavorite()
								? R.drawable.ic_favorite_border
								: R.drawable.ic_favorite_filled;
							mFavBtn.setImageResource(resId);
						} else {
							int resId = item.isFavorite()
								? R.drawable.ic_favorite_border
								: R.drawable.ic_favorite_filled;
							mFavBtn.setImageResource(resId);
						}
					}
				});

            // ── More options button ─────────────────────────────────────
            mOptionsMore.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						if (optionsListener != null) {
							optionsListener.onOptionsClicked(item, position);
						}
					}
				});

            // ── Row click ───────────────────────────────────────────────
            mItemView.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						if (listener != null) listener.onAudioItemClick(item, position);
					}
				});

            mItemView.setOnLongClickListener(new View.OnLongClickListener() {
					@Override
					public boolean onLongClick(View v) {
						return listener != null
							&& listener.onAudioItemLongClick(item, position);
					}
				});
        }

        private String formatDuration(long ms) {
            long s    = ms / 1000;
            long mins = s / 60;
            long secs = s % 60;
            if (mins >= 60) {
                long hrs = mins / 60;
                mins = mins % 60;
                return hrs + ":" + (mins < 10 ? "0" : "") + mins
                    + ":" + (secs < 10 ? "0" : "") + secs;
            }
            return mins + ":" + (secs < 10 ? "0" : "") + secs;
        }
    }

    // ── Data manipulation methods ────────────────────────────────────────

    public void addItem(AudioItem item) {
        mRows.add(AudioListRow.item(item));
        notifyItemInserted(mRows.size() - 1);
    }

    public void addItemAt(int position, AudioItem item) {
        mRows.add(position, AudioListRow.item(item));
        notifyItemInserted(position);
    }

    public AudioItem removeItemAt(int position) {
        if (position < 0 || position >= mRows.size()) return null;
        AudioListRow row = mRows.remove(position);
        AudioItem item = row.getItem();
        notifyItemRemoved(position);
        return item;
    }

    public void updateItemAt(int position, AudioItem item) {
        if (position < 0 || position >= mRows.size()) return;
        mRows.add(position, AudioListRow.item(item));
        notifyItemChanged(position);
    }

    public void moveItem(int from, int to) {
        if (from == to) return;
        if (from < to) {
            for (int i = from; i < to; i++) Collections.swap(mRows, i, i + 1);
        } else {
            for (int i = from; i > to; i--) Collections.swap(mRows, i, i - 1);
        }
        notifyItemMoved(from, to);
    }

    public void clearAll() {
        int oldSize = mRows.size();
        mRows.clear();
        notifyItemRangeRemoved(0, oldSize);
    }

    public void replaceAll(List<AudioItem> items) {
        mRows.clear();
        for (AudioItem item : items) {
            mRows.add(AudioListRow.item(item));
        }
        notifyDataSetChanged();
    }

    public List<AudioItem> getItems() {
        List<AudioItem> items = new ArrayList<>();
        for (AudioListRow row : mRows) {
            items.add(row.getItem());
        }
        return items;
    }

    // ── Listener interfaces ──────────────────────────────────────────────

    public interface OnAudioItemClickListener {
        void onAudioItemClick(AudioItem item, int position);
        boolean onAudioItemLongClick(AudioItem item, int position);
    }

    public interface OnMoreOptionsClickListener {
        void onOptionsClicked(AudioItem item, int position);
    }

    public interface OnFavoriteClickListener {
        void onFavoriteClicked(AudioItem item, int position);
    }
}
