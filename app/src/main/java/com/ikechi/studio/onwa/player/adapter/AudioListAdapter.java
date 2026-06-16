package com.ikechi.studio.onwa.player.adapter;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.ikechi.studio.onwa.player.R;
import com.ikechi.studio.onwa.player.models.AudioItem;
import com.ikechi.studio.onwa.player.utils.MediaUtils;
import com.ikechi.studio.onwa.player.MainActivity;

import java.util.ArrayList;
import java.util.List;

public class AudioListAdapter extends BaseAdapter {

    private static final String TAG = "AudioListAdapter";
    private Context context;
    private List<AudioItem> audioItems;
    private LayoutInflater inflater;
    private OnPlayButtonClickListener listener;
    private MainActivity mActivity;
    private int currentlyPlayingPosition = -1;
    private boolean isServiceBound = false;
    private boolean useDynamicAlbumArt = true;

    private Bitmap defaultAlbumArt;

    private int primaryColor;
    private int textSecondaryColor;

    public interface OnPlayButtonClickListener {
        void onPlayButtonClick(int position);
    }

    public AudioListAdapter(Context context, List<AudioItem> audioItems, OnPlayButtonClickListener listener) {
        this.context = context;
        this.audioItems = audioItems != null ? audioItems : new ArrayList<AudioItem>();
        this.listener = listener;
        this.inflater = LayoutInflater.from(context);
        primaryColor = context.getResources().getColor(R.color.spring_pink);
        textSecondaryColor = context.getResources().getColor(R.color.text_secondary_dark);

        // Load default album art (will be used as fallback)
        defaultAlbumArt = BitmapFactory.decodeResource(context.getResources(), R.drawable.default_album_art);

        loadSettings();
    }

    public void setUseDynamicAlbumArt(boolean use) {
        useDynamicAlbumArt = use;
        notifyDataSetChanged(); // refresh to show/hide album art
    }

    private void loadSettings() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        // Settings are no longer needed for SpectraViz (kept for future use)
    }

    public void setActivity(MainActivity activity) {
        this.mActivity = activity;
        this.isServiceBound = mActivity.isServiceBound();
    }

    public void setCurrentlyPlaying(int position) {
        int oldPosition = currentlyPlayingPosition;
        currentlyPlayingPosition = position;

        // Notify both old and new positions to update their appearance (if needed)
        if (oldPosition != -1) {
            notifyDataSetChanged();
        }
        if (position != -1) {
            notifyDataSetChanged();
        }
    }

    @Override
    public int getCount() {
        return audioItems != null ? audioItems.size() : 0;
    }

    @Override
    public Object getItem(int position) {
        return audioItems != null && position >= 0 && position < audioItems.size() ? audioItems.get(position) : null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.audio_list_item, parent, false);
            holder = new ViewHolder();
            holder.albumArtView = (ImageView) convertView.findViewById(R.id.item_album_art);
            holder.title = (TextView) convertView.findViewById(R.id.item_title);
            holder.artist = (TextView) convertView.findViewById(R.id.item_artist);
            holder.moreButton = (ImageButton) convertView.findViewById(R.id.item_more);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final AudioItem item = audioItems.get(position);

        // Set title and artist directly from AudioItem
        holder.title.setText(item.getTitle());
        holder.artist.setText(item.getArtist());
        holder.title.setTextColor(primaryColor);
        holder.artist.setTextColor(textSecondaryColor);

        // Set album art using MediaUtils global cache
        if (useDynamicAlbumArt) {
            String filePath = item.getFilePath();
            // Get cached bitmap (scaled, from MediaUtils)
            Bitmap albumArt = MediaUtils.getAudioAlbumArtBitmap(filePath);
            if (albumArt != null) {
                holder.albumArtView.setImageBitmap(albumArt);
            } else {
                holder.albumArtView.setImageBitmap(defaultAlbumArt);
            }
        } else {
            // Album art disabled – show default
            holder.albumArtView.setImageBitmap(defaultAlbumArt);
        }

        // Click listener for play button
        convertView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (listener != null) {
						listener.onPlayButtonClick(position);
					}
				}
			});

        convertView.setOnLongClickListener(new View.OnLongClickListener() {
				@Override
				public boolean onLongClick(View v) {
					showContextMenu(position, v);
					return true;
				}
			});

        if (holder.moreButton != null) {
            holder.moreButton.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						showOptionsMenu(position, v);
					}
				});
        }

        return convertView;
    }

    public void updateAudioList(List<AudioItem> list) {
        audioItems = list != null ? list : new ArrayList<AudioItem>();
        // No need to clear local cache – MediaUtils cache is global and unaffected
        notifyDataSetChanged();
    }

    private void showContextMenu(final int position, final View view) {
        view.setBackgroundColor(context.getResources().getColor(R.color.highlight));
        final View v = view;
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
				@Override
				public void run() {
					v.setBackgroundColor(context.getResources().getColor(android.R.color.transparent));
				}
			}, 500);
    }

    private void showOptionsMenu(int position, View view) {
        AudioItem item = audioItems.get(position);
        Toast.makeText(context, "Options for: " + item.getTitle(), Toast.LENGTH_SHORT).show();
    }

    public void updateSettings() {
        loadSettings();
        notifyDataSetChanged();
    }

    public void release() {
        // Nothing to release – MediaUtils cache is managed globally.
        // We just nullify our reference to defaultAlbumArt to help GC.
        defaultAlbumArt = null;
    }

    static class ViewHolder {
        ImageView albumArtView;
        TextView title;
        TextView artist;
        ImageButton moreButton;
    }
}
