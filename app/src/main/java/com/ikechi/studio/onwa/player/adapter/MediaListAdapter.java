package com.ikechi.studio.onwa.player.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.ikechi.studio.onwa.player.R;
import com.ikechi.studio.onwa.player.models.AudioItem;
import com.ikechi.studio.onwa.player.models.VideoItem;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.adapter.RecyclingAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Unified adapter for the video and audio library panels.
 *
 * Works with two item types that share similar display needs (title,
 * subtitle, duration).  Supply either a list of {@link VideoItem} or
 * {@link AudioItem}; the adapter detects the type via {@code TYPE_VIDEO} /
 * {@code TYPE_AUDIO} and inflates the appropriate item layout.
 *
 * Caller receives selection events through {@link OnMediaItemClickListener}.
 */
public class MediaListAdapter extends RecyclingAdapter {

    public static final int TYPE_VIDEO = 0;
    public static final int TYPE_AUDIO = 1;

    // ── Callback ──────────────────────────────────────────────────────────────
    public interface OnMediaItemClickListener {
        void onVideoItemClick(VideoItem item);
        void onAudioItemClick(AudioItem item);
    }

    // ── Data ──────────────────────────────────────────────────────────────────
    private final int     mType;
    private List<Object>  mItems = new ArrayList<Object>();
    private OnMediaItemClickListener mListener;

    public MediaListAdapter(int type) {
        mType = type;
    }

    public void setOnMediaItemClickListener(OnMediaItemClickListener l) {
        mListener = l;
    }

    // ── Data population ───────────────────────────────────────────────────────

    public void setVideoItems(List<VideoItem> items) {
        mItems.clear();
        if (items != null) mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void setAudioItems(List<AudioItem> items) {
        mItems.clear();
        if (items != null) mItems.addAll(items);
        notifyDataSetChanged();
    }

    // ★ FIXED – return properly typed lists without casting the whole collection
    public List<VideoItem> getVideoItems() {
        List<VideoItem> list = new ArrayList<>();
        for (Object obj : mItems) {
            if (obj instanceof VideoItem) list.add((VideoItem) obj);
        }
        return list;
    }

    public List<VideoItem> getVideoList() {
        return getVideoItems();
    }

    public List<AudioItem> getAudioItems() {
        List<AudioItem> list = new ArrayList<>();
        for (Object obj : mItems) {
            if (obj instanceof AudioItem) list.add((AudioItem) obj);
        }
        return list;
    }

    public List<AudioItem> getAudioList() {
        return getAudioItems();
    }

    // ── RecyclingAdapter overrides ────────────────────────────────────────────

    @Override
    public int getItemCount() { return mItems.size(); }

    @Override
    public int getItemViewType(int position) { return mType; }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        int layoutId = (viewType == TYPE_VIDEO)
            ? R.layout.item_media_video
            : R.layout.item_media_audio;
        View v = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
        return new MediaViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        MediaViewHolder vh = (MediaViewHolder) holder;
        if (mType == TYPE_VIDEO) {
            vh.bindVideo((VideoItem) mItems.get(position));
        } else {
            vh.bindAudio((AudioItem) mItems.get(position));
        }
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    private class MediaViewHolder extends RecyclingAdapter.ViewHolder {

        private final TextView tvTitle;
        private final TextView tvSubtitle;
        private final TextView tvDuration;
        private final View     btnPlay;

        MediaViewHolder(View v) {
            super(v);
            tvTitle    = (TextView) v.findViewById(R.id.tv_media_title);
            tvSubtitle = (TextView) v.findViewById(R.id.tv_media_subtitle);
            tvDuration = (TextView) v.findViewById(R.id.tv_media_duration);
            btnPlay    = v.findViewById(R.id.btn_media_play);
        }

        void bindVideo(final VideoItem item) {
            tvTitle.setText(item.getTitle());
            tvSubtitle.setText(formatResolution(item.getWidth(), item.getHeight()));
            tvDuration.setText(formatDuration(item.getDuration()));
            btnPlay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mListener != null) mListener.onVideoItemClick(item);
                }
            });
            getItemView().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mListener != null) mListener.onVideoItemClick(item);
                }
            });
        }

        void bindAudio(final AudioItem item) {
            tvTitle.setText(item.getTitle());
            tvSubtitle.setText(item.getArtist() + "  \u2022  " + item.getAlbum());
            tvDuration.setText(item.getFormattedDuration());
            btnPlay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mListener != null) mListener.onAudioItemClick(item);
                }
            });
            getItemView().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mListener != null) mListener.onAudioItemClick(item);
                }
            });
        }

        // ── Formatting helpers ────────────────────────────────────────────

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

        private String formatResolution(int w, int h) {
            if (w <= 0 || h <= 0) return "Video";
            return w + " \u00d7 " + h;
        }
    }

    // ── Data manipulation methods ─────────────────────────────────────────────

    public void addItem(VideoItem item) {
        if (mType == TYPE_AUDIO) return;
        mItems.add(item);
        notifyItemInserted(mItems.size() - 1);
    }

    public void addItem(AudioItem item) {
        if (mType == TYPE_VIDEO) return;
        mItems.add(item);
        notifyItemInserted(mItems.size() - 1);
    }

    public void addItemAt(int position, VideoItem item) {
        if (mType == TYPE_AUDIO) return;
        mItems.add(position, item);
        notifyItemInserted(position);
    }

    public void addItemAt(int position, AudioItem item) {
        if (mType == TYPE_VIDEO) return;
        mItems.add(position, item);
        notifyItemInserted(position);
    }

    public Object removeItemAt(int position) {
        if (position < 0 || position >= mItems.size()) return null;
        Object removed = mItems.remove(position);
        notifyItemRemoved(position);
        return removed;
    }

    public void updateItemAt(int position, Object item) {
        if (position < 0 || position >= mItems.size()) return;
        mItems.add(position, item);
        notifyItemChanged(position);
    }

    public void moveItem(int from, int to) {
        if (from == to) return;
        if (from < to) {
            for (int i = from; i < to; i++) Collections.swap(mItems, i, i + 1);
        } else {
            for (int i = from; i > to; i--) Collections.swap(mItems, i, i - 1);
        }
        notifyItemMoved(from, to);
    }

    public void clearAll() {
        int oldSize = mItems.size();
        mItems.clear();
        notifyItemRangeRemoved(0, oldSize);
    }

    public void replaceAll(List<Object> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public List<Object> getItems() { return new ArrayList<>(mItems); }
}