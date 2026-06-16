package com.ikechi.studio.onwa.player.fragment;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.ikechi.studio.IkLog;
import com.ikechi.studio.onwa.player.MainActivity;
import com.ikechi.studio.onwa.player.R;
import com.ikechi.studio.onwa.player.models.AudioItem;
import com.ikechi.studio.onwa.player.models.Category;
import com.ikechi.studio.onwa.player.utils.IkBeautifulDialog;
import com.ikechi.studio.onwa.player.utils.MediaDatabaseHelper;
import com.ikechi.studio.onwa.player.utils.MediaUtils;
import com.ikechi.studio.onwa.widgets.MultiPanelLayout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class LibraryCategoryFragment extends Fragment {

    private static final String TAG = "LibraryCategoryFrag";

    private MultiPanelLayout mMultiPanel;
    private RecyclerView    mCategoryRecycler;
    private RecyclerView    mPlaylistRecycler;
    private CategoryCardAdapter  mCategoryAdapter;
    private PlaylistCardAdapter  mPlaylistAdapter;
    private OnCategorySelectedListener mListener;
    private ProgressBar mLoadingProgress;
    private View mLayoutPlaylistEmpty;
    private View mBtnAddPlaylist;
    private ImageView mBtnAddPlaylistCentered;
    private TextView mPlaylistCountText;

    private final List<AudioItem> mFavouritesList = new ArrayList<>();
    private final List<AudioItem> mMostPlayedList = new ArrayList<>();
    private final List<AudioItem> mRecentsList = new ArrayList<>();
    private List<AudioItem> mHistoryList = new ArrayList<>();

    private static final int MOST_PLAYED_LIST_MAX = 50;
    private static final int MIN_PLAY_COUNT = 8;

    private long mCurrentPlaylistId = -1;

    public interface OnCategorySelectedListener {
        void onCategorySelected(Category category);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        Fragment parent = getParentFragment();
        if (parent instanceof OnCategorySelectedListener) {
            mListener = (OnCategorySelectedListener) parent;
        } else {
            IkLog.e(TAG, "Parent does not implement OnCategorySelectedListener");
            throw new RuntimeException("Parent must implement OnCategorySelectedListener");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_library_category, container, false);

        mMultiPanel = (MultiPanelLayout) view.findViewById(R.id.library_multi_panel);
        mCategoryRecycler = (RecyclerView) view.findViewById(R.id.category_recycler);
        mPlaylistRecycler = (RecyclerView) view.findViewById(R.id.playlist_recycler);
        mLoadingProgress = (ProgressBar) view.findViewById(R.id.audio_refresh_progress);
        mLayoutPlaylistEmpty = view.findViewById(R.id.layout_playlist_empty);
        mBtnAddPlaylist = view.findViewById(R.id.btn_add_playlist);
        mBtnAddPlaylistCentered = view.findViewById(R.id.btn_add_playlist_centered);
        mPlaylistCountText = (TextView) view.findViewById(R.id.tv_playlist_count);

        // Category grid – 2 columns
        mCategoryRecycler.setLayoutManager(new GridLayoutManager(getContext(), 2));
        mCategoryAdapter = new CategoryCardAdapter(new ArrayList<Category>());
        mCategoryAdapter.setOnCategoryClickListener(new CategoryCardAdapter.OnCategoryClickListener() {
				@Override
				public void onCategoryClick(Category category, int position) {
					handleCategoryClick(category, position);
				}
			});
        mCategoryRecycler.setAdapter(mCategoryAdapter);

        // Playlist list – single column
        mPlaylistRecycler.setLayoutManager(new GridLayoutManager(getContext(), 1));
        mPlaylistAdapter = new PlaylistCardAdapter(new ArrayList<MediaDatabaseHelper.PlaylistInfo>());
        mPlaylistAdapter.setOnPlaylistClickListener(new PlaylistCardAdapter.OnPlaylistClickListener() {
				@Override
				public void onPlaylistClick(MediaDatabaseHelper.PlaylistInfo playlist) {
					mCurrentPlaylistId = playlist.id;
					loadPlaylistTracks(playlist);
				}
				@Override
				public void onPlaylistLongClick(MediaDatabaseHelper.PlaylistInfo playlist) {
					showPlaylistOptionsDialog(playlist);
				}
			});
        mPlaylistRecycler.setAdapter(mPlaylistAdapter);

        if (mMultiPanel != null) {
            mMultiPanel.setOnPanelStateChangeListener(new MultiPanelLayout.OnPanelStateChangeListener() {
					@Override
					public void onPanelExpanded(int panelIndex) {
						if (mBtnAddPlaylist != null) {
							mBtnAddPlaylist.setVisibility(View.VISIBLE);
							mBtnAddPlaylist.bringToFront();
							if (mBtnAddPlaylistCentered != null) {
								mBtnAddPlaylistCentered.setVisibility(View.VISIBLE);
								mBtnAddPlaylistCentered.bringToFront();
							}
						}
					}
					@Override
					public void onPanelCollapsed(int panelIndex) { }
				});
        }

        if (mBtnAddPlaylist != null) {
            mBtnAddPlaylist.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						IkLog.d(TAG, "btn_add_playlist CLICKED");
						if (mMultiPanel != null) {
							mMultiPanel.expandPanel(0);
						}
						showCreatePlaylistDialog();
					}
				});
        }

        if (mBtnAddPlaylistCentered != null) {
            mBtnAddPlaylistCentered.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) { showCreatePlaylistDialog(); }
				});
        }

        return view;
    }

    public void showProgress() {
        if (!isAdded()) return;
        if (mLoadingProgress != null) mLoadingProgress.setVisibility(View.VISIBLE);
        View v = getView();
        if (v != null) {
            TextView tv = v.findViewById(R.id.libTopTv);
            if (tv != null) tv.setText("Loading...");
        }
    }

    public void hideProgress() {
        if (!isAdded()) return;
        if (mLoadingProgress != null) mLoadingProgress.setVisibility(View.GONE);
        View v = getView();
        if (v != null) {
            TextView tv = v.findViewById(R.id.libTopTv);
            if (tv != null) tv.setText("BROWSE");
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        buildCategoriesAsync();
        loadPlaylists();
    }

    @Override
    public void onResume() {
        super.onResume();
        buildCategoriesAsync();
        loadPlaylists();
        if (mBtnAddPlaylist != null) {
            mBtnAddPlaylist.bringToFront();
            mBtnAddPlaylist.invalidate();
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    // ── Category building ─────────────────────────────────────────────────

    private void buildCategoriesAsync() {
        final List<AudioItem> allSongs   = getAudioListFromParent();
        final List<AudioItem> nowPlaying = getNowPlayingListFromParent();
        final Activity activity = getActivity();

        if (allSongs == null || allSongs.isEmpty()) {
            if (isAdded()) updateCategoryAdapter(buildStaticCategories(nowPlaying));
            return;
        }

        final Handler uiHandler = new Handler(Looper.getMainLooper());
        final Context appCtx = activity != null ? activity.getApplicationContext() : null;
        if (appCtx == null) {
            if (isAdded()) updateCategoryAdapter(buildStaticCategories(nowPlaying));
            return;
        }

        IkLog.d(TAG, "buildCategoriesAsync started");
        new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						final List<Category> categories = buildCategoriesInBackground(allSongs, nowPlaying, appCtx);
						uiHandler.post(new Runnable() {
								@Override
								public void run() {
									if (!isAdded()) return;
									updateCategoryAdapter(categories);
									hideProgress();
									IkLog.d(TAG, "Categories built: " + categories.size());
								}
							});
					} catch (Exception e) {
						IkLog.e(TAG, "Error building categories in background", e);
					}
				}
			}, "category-builder").start();
    }

    private void updateCategoryAdapter(List<Category> categories) {
        if (mCategoryAdapter != null) {
            mCategoryAdapter.updateItems(categories);
        }
    }

    private List<Category> buildCategoriesInBackground(List<AudioItem> allSongs,
                                                       List<AudioItem> nowPlaying,
                                                       Context context) {
        List<Category> list = new ArrayList<>();
        sortCategories(allSongs, context);

        list.add(new Category(0, "All Songs", R.drawable.ic_music_note, new ArrayList<>(allSongs)));
        if (nowPlaying != null && !nowPlaying.isEmpty()) {
            list.add(new Category(1, "Currently Playing", R.drawable.ic_queue_music, new ArrayList<>(nowPlaying)));
        }
        list.add(new Category(2, "Favorites", R.drawable.ic_favorite, new ArrayList<>(mFavouritesList)));
        list.add(new Category(3, "Most Played", R.drawable.ic_most_played, new ArrayList<>(mMostPlayedList)));
        list.add(new Category(4, "Recently Added", R.drawable.ic_clock, new ArrayList<>(mRecentsList)));
        list.add(new Category(5, "History", R.drawable.ic_history, new ArrayList<>(mHistoryList)));
        list.add(new Category(6, "Listening Stats", R.drawable.ic_stats, new ArrayList<>()));

        return list;
    }

    private synchronized void sortCategories(List<AudioItem> songs, Context context) {
        mRecentsList.clear();
        mFavouritesList.clear();
        mMostPlayedList.clear();
        mHistoryList.clear();

        for (AudioItem item : songs) {
            if (MediaUtils.isAudioFileRecentlyAdded(context, item.getFilePath(), 10L)) {
                mRecentsList.add(item);
            }
            if (item.isFavorite()) mFavouritesList.add(item);
            if (item.getPlayCount() >= MIN_PLAY_COUNT) mMostPlayedList.add(item);
            if (item.getPlayCount() > 0) mHistoryList.add(item);
        }

        if (mMostPlayedList.size() > MOST_PLAYED_LIST_MAX) {
            shrinkMostPlayedList(mMostPlayedList);
        }
        mHistoryList.sort(new Comparator<AudioItem>() {
				@Override
				public int compare(AudioItem a, AudioItem b) {
					return Integer.compare(b.getPlayCount(), a.getPlayCount());
				}
			});
        if (mHistoryList.size() > MOST_PLAYED_LIST_MAX) {
            mHistoryList = new ArrayList<>(mHistoryList.subList(0, MOST_PLAYED_LIST_MAX));
        }
    }

    private synchronized void shrinkMostPlayedList(List<AudioItem> songs) {
        List<AudioItem> list = new ArrayList<>(songs);
        int minCount = MIN_PLAY_COUNT;
        do {
            for (int i = list.size() - 1; i >= 0; i--) {
                if (list.get(i).getPlayCount() <= minCount) {
                    list.remove(i);
                }
            }
            minCount++;
        } while (list.size() > MOST_PLAYED_LIST_MAX);

        mMostPlayedList.clear();
        mMostPlayedList.addAll(list);
    }

    private List<Category> buildStaticCategories(List<AudioItem> nowPlaying) {
        List<Category> list = new ArrayList<>();
        if (nowPlaying != null && !nowPlaying.isEmpty()) {
            list.add(new Category(0, "Currently Playing", R.drawable.ic_queue_music, new ArrayList<>(nowPlaying)));
        }
        list.add(new Category(1, "All Songs",     R.drawable.ic_music_note,   new ArrayList<>()));
        list.add(new Category(2, "Favorites",      R.drawable.ic_favorite,     new ArrayList<>()));
        list.add(new Category(3, "Most Played",    R.drawable.ic_most_played,  new ArrayList<>()));
        list.add(new Category(4, "Recently Added", R.drawable.ic_clock,        new ArrayList<>()));
        list.add(new Category(5, "History",        R.drawable.ic_history,      new ArrayList<>()));
        list.add(new Category(6, "Listening Stats", R.drawable.ic_stats,       new ArrayList<>()));
        return list;
    }

    // ── Playlist loading ───────────────────────────────────────────────────

    private void loadPlaylists() {
        final Activity activity = getActivity();
        if (activity == null) {
            IkLog.w(TAG, "loadPlaylists: Activity null");
            return;
        }

        new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						final MediaDatabaseHelper dbHelper = MediaDatabaseHelper.getInstance(activity);
						final List<MediaDatabaseHelper.PlaylistInfo> playlists = dbHelper.getAllPlaylists();

						activity.runOnUiThread(new Runnable() {
								@Override
								public void run() {
									if (!isAdded()) return;
									if (mPlaylistAdapter != null) {
										mPlaylistAdapter.setPlaylists(playlists);
									}

									boolean hasPlaylists = playlists != null && !playlists.isEmpty();

									if (mPlaylistCountText != null) {
										int count = hasPlaylists ? playlists.size() : 0;
										mPlaylistCountText.setText(count + " playlist" + (count != 1 ? "s" : ""));
									}

									if (mLayoutPlaylistEmpty != null) {
										mLayoutPlaylistEmpty.setVisibility(hasPlaylists ? View.GONE : View.VISIBLE);
									}

									if (mPlaylistRecycler != null) {
										mPlaylistRecycler.setVisibility(hasPlaylists ? View.VISIBLE : View.GONE);
									}

									if (mBtnAddPlaylist != null) {
										mBtnAddPlaylist.setVisibility(View.VISIBLE);
										mBtnAddPlaylist.bringToFront();
									}
									IkLog.d(TAG, "Playlists loaded: " + (playlists != null ? playlists.size() : 0));
								}
							});
					} catch (Exception e) {
						IkLog.e(TAG, "Error loading playlists", e);
					}
				}
			}, "playlist-loader").start();
    }

    // ── Playlist CRUD dialogs ─────────────────────────────────────────────

    private void showCreatePlaylistDialog() {
        final Activity activity = getActivity();
        if (activity == null) return;

        IkBeautifulDialog dialog = new IkBeautifulDialog(activity);
        dialog.setMessage("Create New Playlist");
        dialog.setInput("Playlist name", "", new IkBeautifulDialog.OnInputConfirmedListener() {
				@Override
				public void onInputConfirmed(String inputText) {
					if (inputText == null || inputText.trim().isEmpty()) {
						showToast("Name cannot be empty");
						return;
					}
					final String name = inputText.trim();
					MediaDatabaseHelper db = MediaDatabaseHelper.getInstance(activity);
					db.createPlaylist(name, new MediaDatabaseHelper.OnPlaylistCreatedCallback() {
							@Override
							public void onPlaylistCreated(long playlistId) {
								if (playlistId >= 0) {
									loadPlaylists();
									showToast("Playlist '" + name + "' created");
								}
							}
						});
				}
			});
        dialog.setPositiveButton("Create", null);
        dialog.showInput();
    }

    private void showPlaylistOptionsDialog(final MediaDatabaseHelper.PlaylistInfo playlist) {
        final Activity activity = getActivity();
        if (activity == null) return;

        IkBeautifulDialog dialog = new IkBeautifulDialog(activity);
        dialog.setMessage(playlist.name + " (" + playlist.trackCount + " tracks)");
        dialog.setItems(new String[]{"Open", "Rename", "Delete", "Cancel"},
            new IkBeautifulDialog.OnItemClickListener() {
                @Override
                public void onItemClick(int position, String item) {
                    if (position == 0) {
                        mCurrentPlaylistId = playlist.id;
                        loadPlaylistTracks(playlist);
                    } else if (position == 1) {
                        showRenamePlaylistDialog(playlist);
                    } else if (position == 2) {
                        showDeletePlaylistDialog(playlist);
                    }
                }
            });
        dialog.showList();
    }

    private void showRenamePlaylistDialog(final MediaDatabaseHelper.PlaylistInfo playlist) {
        final Activity activity = getActivity();
        if (activity == null) return;

        IkBeautifulDialog dialog = new IkBeautifulDialog(activity);
        dialog.setMessage("Rename playlist '" + playlist.name + "'");
        dialog.setInput("New name", playlist.name, new IkBeautifulDialog.OnInputConfirmedListener() {
				@Override
				public void onInputConfirmed(String inputText) {
					if (inputText == null || inputText.trim().isEmpty()) {
						showToast("Name cannot be empty");
						return;
					}
					final String newName = inputText.trim();
					MediaDatabaseHelper db = MediaDatabaseHelper.getInstance(activity);
					db.renamePlaylist(playlist.id, newName);
					loadPlaylists();
					showToast("Renamed to '" + newName + "'");
				}
			});
        dialog.setPositiveButton("Rename", null);
        dialog.showInput();
    }

    private void showDeletePlaylistDialog(final MediaDatabaseHelper.PlaylistInfo playlist) {
        final Activity activity = getActivity();
        if (activity == null) return;

        IkBeautifulDialog dialog = new IkBeautifulDialog(activity);
        dialog.setMessage("Delete playlist '" + playlist.name + "'?\nThis cannot be undone.");
        dialog.setPositiveButton("Delete", new IkBeautifulDialog.OnPositiveClickListener() {
				@Override
				public void onClick() {
					MediaDatabaseHelper db = MediaDatabaseHelper.getInstance(activity);
					db.deletePlaylist(playlist.id);
					loadPlaylists();
					showToast("Playlist deleted");
				}
			});
        dialog.setNegativeButton("Cancel", null);
        dialog.show();
    }

    private void loadPlaylistTracks(final MediaDatabaseHelper.PlaylistInfo playlist) {
        final Activity activity = getActivity();
        if (activity == null) {
            IkLog.w(TAG, "loadPlaylistTracks: Activity null");
            return;
        }

        showProgress();
        new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						MediaDatabaseHelper dbHelper = MediaDatabaseHelper.getInstance(activity);
						final List<AudioItem> tracks = dbHelper.getPlaylistAudioItems(playlist.id);

						activity.runOnUiThread(new Runnable() {
								@Override
								public void run() {
									if (!isAdded()) return;
									hideProgress();
									if (mListener != null) {
										Category cat = new Category((int) playlist.id, playlist.name,
																	R.drawable.ic_playlist, tracks);
										mListener.onCategorySelected(cat);
									}
									IkLog.d(TAG, "Playlist tracks loaded: " + (tracks != null ? tracks.size() : 0));
								}
							});
					} catch (Exception e) {
						IkLog.e(TAG, "Error loading playlist tracks", e);
					}
				}
			}, "playlist-tracks-loader").start();
    }

    // ── Category click handler ─────────────────────────────────────────────

    private void handleCategoryClick(Category category, int position) {
        if (category.getId() == 6) {
            showStatsFragment();
        } else {
            if (mListener != null) mListener.onCategorySelected(category);
        }
    }

    private void showStatsFragment() {
        IkLog.d(TAG, "About to show stats fragment");
        if (!isAdded()) return;
        StatsFragment frag = new StatsFragment();
        getParentFragment().getChildFragmentManager()
			.beginTransaction()
			.setReorderingAllowed(true)
			.setCustomAnimations(
			R.anim.fragment_slide_in_right, R.anim.fragment_slide_out_left,
			R.anim.fragment_slide_in_left, R.anim.fragment_slide_out_right)
			.replace(R.id.content_frame, frag)
			.addToBackStack("stats")
			.commitAllowingStateLoss();
        IkLog.d(TAG, "Showing stats fragment");
    }

    private List<AudioItem> getAudioListFromParent() {
        Fragment parent = getParentFragment();
        if (parent instanceof AudioPlayerFragment) {
            return ((AudioPlayerFragment) parent).getAudioList();
        }
        return new ArrayList<>();
    }

    private List<AudioItem> getNowPlayingListFromParent() {
        Activity activity = getActivity();
        if (activity instanceof MainActivity) {
            return ((MainActivity) activity).getActivePlaylist();
        }
        return new ArrayList<>();
    }

    private void showToast(String msg) {
        Activity activity = getActivity();
        if (activity != null) {
            Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Card Adapters (static inner classes)
    // ═══════════════════════════════════════════════════════════════════

    private static class CategoryCardAdapter extends RecyclerView.Adapter<CategoryCardAdapter.VH> {

        private List<Category> items;
        private OnCategoryClickListener listener;

        interface OnCategoryClickListener {
            void onCategoryClick(Category category, int position);
        }

        CategoryCardAdapter(List<Category> items) {
            this.items = items;
        }

        void setOnCategoryClickListener(OnCategoryClickListener listener) {
            this.listener = listener;
        }

        void updateItems(List<Category> newItems) {
            this.items = newItems;
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() { return items.size(); }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            View card = inflater.inflate(R.layout.item_category_card, parent, false);
            return new VH(card);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Category cat = items.get(position);
            holder.icon.setImageResource(cat.getIconResId());
            holder.title.setText(cat.getName());

            // Listening Stats is a special card – don't show a track count
            if (cat.getId() == 6) {
                holder.count.setText("Play Stats");
            } else {
                holder.count.setText(cat.getItems().size() + " tracks");
            }

            holder.card.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						if (listener != null) listener.onCategoryClick(cat, position);
					}
				});
        }

        static class VH extends RecyclerView.ViewHolder {
            MaterialCardView card;
            ImageView icon;
            TextView title, count;
            VH(View itemView) {
                super(itemView);
                card = (MaterialCardView) itemView.findViewById(R.id.category_card);
                icon = itemView.findViewById(R.id.category_icon);
                title = itemView.findViewById(R.id.category_title);
                count = itemView.findViewById(R.id.category_track_count);
            }
        }
    }

    private static class PlaylistCardAdapter extends RecyclerView.Adapter<PlaylistCardAdapter.VH> {

        private List<MediaDatabaseHelper.PlaylistInfo> items;
        private OnPlaylistClickListener listener;

        interface OnPlaylistClickListener {
            void onPlaylistClick(MediaDatabaseHelper.PlaylistInfo playlist);
            void onPlaylistLongClick(MediaDatabaseHelper.PlaylistInfo playlist);
        }

        PlaylistCardAdapter(List<MediaDatabaseHelper.PlaylistInfo> items) {
            this.items = items;
        }

        void setOnPlaylistClickListener(OnPlaylistClickListener listener) {
            this.listener = listener;
        }

        void setPlaylists(List<MediaDatabaseHelper.PlaylistInfo> list) {
            this.items = list;
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() { return items.size(); }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            View card = inflater.inflate(R.layout.item_playlist_card, parent, false);
            return new VH(card);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            MediaDatabaseHelper.PlaylistInfo pl = items.get(position);
            holder.name.setText(pl.name);
            holder.count.setText(pl.trackCount + " tracks");
            holder.card.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						if (listener != null) listener.onPlaylistClick(pl);
					}
				});
            holder.card.setOnLongClickListener(new View.OnLongClickListener() {
					@Override
					public boolean onLongClick(View v) {
						if (listener != null) listener.onPlaylistLongClick(pl);
						return true;
					}
				});
        }

        static class VH extends RecyclerView.ViewHolder {
            MaterialCardView card;
            TextView name, count;
            VH(View itemView) {
                super(itemView);
                card = (MaterialCardView) itemView.findViewById(R.id.playlist_card);
                name = itemView.findViewById(R.id.playlist_name);
                count = itemView.findViewById(R.id.playlist_track_count);
            }
        }
    }
}