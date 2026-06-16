package com.ikechi.studio.onwa.player.adapter;

import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.ikechi.studio.onwa.player.R;
import com.ikechi.studio.onwa.player.utils.MediaDatabaseHelper;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.adapter.RecyclingAdapter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * PlaylistAdapter — displays user-created playlists in a RecyclingView.
 *
 * <p>Uses {@code item_playlist.xml} layout (or a simple built-in layout).
 * Supports click and long-click via {@link OnPlaylistClickListener}.
 */
public class PlaylistAdapter extends RecyclingAdapter {

    private final List<MediaDatabaseHelper.PlaylistInfo> mPlaylists;
    private OnPlaylistClickListener mListener;
    private static final int ICON_TINT = 0xFFF5A623; // gold accent
    private final SimpleDateFormat mDateFormat;

    public PlaylistAdapter(List<MediaDatabaseHelper.PlaylistInfo> playlists) {
        mPlaylists = new ArrayList<MediaDatabaseHelper.PlaylistInfo>(playlists);
        mDateFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
        setHasStableIds(true);
    }

    public void setOnPlaylistClickListener(OnPlaylistClickListener listener) {
        mListener = listener;
    }

    public void setPlaylists(List<MediaDatabaseHelper.PlaylistInfo> playlists) {
        mPlaylists.clear();
        if (playlists != null) {
            mPlaylists.addAll(playlists);
        }
        notifyDataSetChanged();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_playlist, parent, false);
        return new PlaylistViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        final MediaDatabaseHelper.PlaylistInfo playlist = mPlaylists.get(position);
        PlaylistViewHolder vh = (PlaylistViewHolder) holder;
        vh.bind(playlist, position, mListener, mDateFormat, ICON_TINT);
    }

    @Override
    public int getItemCount() {
        return mPlaylists.size();
    }

    @Override
    public long getItemId(int position) {
        return mPlaylists.get(position).id;
    }

    // ── ViewHolder ────────────────────────────────────────────────────────

    public static class PlaylistViewHolder extends ViewHolder {
        private final ImageView mIcon;
        private final TextView mName;
        private final TextView mSubtitle;
        private final TextView mTrackCount;
        private final ImageView mChevron;
        private final View mItemView;

        public PlaylistViewHolder(View itemView) {
            super(itemView);
            mItemView   = itemView;
            mIcon       = itemView.findViewById(R.id.imgPlaylistIcon);
            mName       = itemView.findViewById(R.id.tvPlaylistName);
            mSubtitle   = itemView.findViewById(R.id.tvPlaylistSubtitle);
            mTrackCount = itemView.findViewById(R.id.tvPlaylistTrackCount);
            mChevron    = itemView.findViewById(R.id.imgChevron);
        }

        public void bind(final MediaDatabaseHelper.PlaylistInfo playlist,
                         final int position,
                         final OnPlaylistClickListener listener,
                         final SimpleDateFormat dateFormat,
                         final int iconTint) {

            mName.setText(playlist.name);

            // Subtitle: creation date
            if (mSubtitle != null && playlist.createdDate > 0) {
                String dateStr = dateFormat.format(new Date(playlist.createdDate));
                mSubtitle.setText("Created " + dateStr);
            } else if (mSubtitle != null) {
                mSubtitle.setText(playlist.trackCount + " songs");
            }

            // Track count badge
            if (mTrackCount != null) {
                mTrackCount.setText(String.valueOf(playlist.trackCount));
            }

            // Icon tint
            if (mIcon != null) {
                mIcon.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN);
            }

            // Chevron tint
            if (mChevron != null) {
                mChevron.setColorFilter(0x80A0B0C8, PorterDuff.Mode.SRC_IN);
            }

            // Click
            mItemView.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						if (listener != null) {
							listener.onPlaylistClick(playlist);
						}
					}
				});

            // Long-click
            mItemView.setOnLongClickListener(new View.OnLongClickListener() {
					@Override
					public boolean onLongClick(View v) {
						if (listener != null) {
							listener.onPlaylistLongClick(playlist);
							return true;
						}
						return false;
					}
				});
        }
    }

    // ── Listener interface ────────────────────────────────────────────────

    public interface OnPlaylistClickListener {
        void onPlaylistClick(MediaDatabaseHelper.PlaylistInfo playlist);
        void onPlaylistLongClick(MediaDatabaseHelper.PlaylistInfo playlist);
    }
}
