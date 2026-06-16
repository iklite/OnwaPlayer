package com.ikechi.studio.onwa.player.fragment;

import androidx.fragment.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.ikechi.studio.onwa.player.R;
import com.ikechi.studio.onwa.player.adapter.AudioSectionedAdapter;
import com.ikechi.studio.onwa.player.models.AudioItem;
import com.ikechi.studio.onwa.player.models.AudioListRow;
import com.ikechi.studio.onwa.player.utils.IkBeautifulDialog;
import com.ikechi.studio.onwa.player.utils.MediaDatabaseHelper;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.animator.DefaultItemAnimator;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.decoration.SpacingDecoration;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.helper.DragDropHelper;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.helper.MediaSwipeActionHelper;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.widget.RecyclingView;
import com.ikechi.studio.onwa.player.utils.ColorExtractor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import android.net.Uri;


public class TrackListFragment extends Fragment
implements AudioSectionedAdapter.OnAudioItemClickListener,
AudioSectionedAdapter.OnFavoriteClickListener,
AudioSectionedAdapter.OnMoreOptionsClickListener {

    private static final String TAG = "TrackListFragment";
    private static final String ARG_CATEGORY_NAME = "category_name";
    private static final String ARG_ITEMS         = "items";
    private static final String ARG_PLAYLIST_ID   = "playlist_id";

	private ColorExtractor.Swatch mSwatch;
    private TextView              mHeader;
    private RecyclingView         mRecyclerView;
    private AudioSectionedAdapter mAdapter;
    private List<AudioItem>       mItems;
    private String                mCategoryName;
    private long                  mPlaylistId = -1;
    private OnTrackSelectedListener mListener;
    private OnPlaylistChangedListener mPlaylistListener;
    private MediaDatabaseHelper   mDbHelper;

    // Multi‑select state
    private boolean mMultiSelectMode = false;
    private final Set<Integer> mSelectedPositions = new HashSet<Integer>();
    private TextView mMultiSelectCounter;

    // ── Listener interfaces ──────────────────────────────────────────────

    public interface OnTrackSelectedListener {
        void onTrackSelected(AudioItem item, int position, List<AudioItem> categoryList);
        boolean onTrackLongClick(AudioItem item, int position);
    }

    /** Called when tracks are added/removed from a playlist so the parent can refresh. */
    public interface OnPlaylistChangedListener {
        void onPlaylistChanged(long playlistId);
    }

    // ── Factory methods ───────────────────────────────────────────────────

    public static TrackListFragment newInstance(String categoryName, List<AudioItem> items) {
        return newInstance(categoryName, items, -1);
    }

    public static TrackListFragment newInstance(String categoryName, List<AudioItem> items,
                                                long playlistId) {
        TrackListFragment f = new TrackListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CATEGORY_NAME, categoryName);
        args.putParcelableArrayList(ARG_ITEMS, new ArrayList<AudioItem>(items));
        args.putLong(ARG_PLAYLIST_ID, playlistId);
        f.setArguments(args);
        return f;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        Fragment parent = getParentFragment();
        if (parent instanceof OnTrackSelectedListener) {
            mListener = (OnTrackSelectedListener) parent;
        }
        if (parent instanceof OnPlaylistChangedListener) {
            mPlaylistListener = (OnPlaylistChangedListener) parent;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mCategoryName = getArguments().getString(ARG_CATEGORY_NAME);
            mItems = getArguments().getParcelableArrayList(ARG_ITEMS);
            mPlaylistId = getArguments().getLong(ARG_PLAYLIST_ID, -1);
        }
        if (mItems == null) mItems = new ArrayList<AudioItem>();
        mDbHelper = MediaDatabaseHelper.getInstance(getActivity());
    }

    /** Returns true if this fragment is showing a user-created playlist. */
    public boolean isPlaylistView() {
        return mPlaylistId >= 0;
    }

    public long getPlaylistId() {
        return mPlaylistId;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_track_list, container, false);
        mHeader = view.findViewById(R.id.track_list_header);
        mRecyclerView = view.findViewById(R.id.track_recycler);

        View counterBar = view.findViewById(R.id.multi_select_counter);
        if (counterBar != null) {
            mMultiSelectCounter = (TextView) counterBar.findViewById(R.id.multi_select_text);
            counterBar.setVisibility(View.GONE);
            counterBar.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						showBulkActionsDialog();
					}
				});
        }

        view.setFocusableInTouchMode(true);
        view.requestFocus();
        view.setOnKeyListener(new View.OnKeyListener() {
				@Override
				public boolean onKey(View v, int keyCode, KeyEvent event) {
					if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
						if (mMultiSelectMode) {
							exitMultiSelectMode();
							return true;
						} else if(getRootFragment().isSearchbarVisible()){
							getRootFragment().hideSearchBar();
							return true;
						}
					}
					return false;
				}
			});

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Header: show playlist name with indicator when viewing a playlist
        if (mHeader != null) {
            if (mPlaylistId >= 0) {
                mHeader.setText(mCategoryName + "  ▶");
            } else {
                mHeader.setText(mCategoryName);
            }
        }

        List<AudioListRow> rows = new ArrayList<AudioListRow>();
        for (AudioItem item : mItems) {
            rows.add(AudioListRow.item(item));
        }
        mAdapter = new AudioSectionedAdapter(rows);
        mAdapter.setOnAudioItemClickListener(this);
        mAdapter.setFavoriteClickListener(this);
        mAdapter.setMoreOptionsClickListener(this);
        mAdapter.setTrackListFragment(this);

        mRecyclerView.setOrientation(RecyclingView.VERTICAL);
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mRecyclerView.addItemDecoration(new SpacingDecoration(dp(4), true, 0x18000000));
        mRecyclerView.setScrollBarEnabled(true);

        // ── MediaSwipeActionHelper ────────────────────────────────────────
        MediaSwipeActionHelper.forAudioLibrary(mRecyclerView,
			new MediaSwipeActionHelper.SwipeCallback() {
				@Override
				public void onSwipeLeft(int position) {
					if (position < 0 || position >= mItems.size()) return;
					AudioItem item = mItems.get(position);
					try {
						int rows = getActivity().getContentResolver().delete(
							item.getUri(), null, null);
						if (rows > 0) {
							// Also remove from playlist if we're viewing one
							if (mPlaylistId >= 0) {
								mDbHelper.removeAudioFromPlaylist(
									item.getUri().toString(), mPlaylistId);
							}
							mItems.remove(position);
							mAdapter.removeItemAt(position);
							notifyPlaylistChanged();
							showToast("Deleted: " + item.getTitle());
						} else {
							showToast("Could not delete: " + item.getTitle());
						}
					} catch (Exception e) {
						showToast("Could not delete file");
					}
				}

				@Override
				public void onSwipeRight(int position) {
					if (position < 0 || position >= mItems.size()) return;
					AudioItem item = mItems.get(position);
					item.setFavorite(!item.isFavorite());
					mDbHelper.updateFavorite(item.getUri().toString(), item.isFavorite());
					mAdapter.notifyItemChanged(position);
					if (mListener != null) {
						mListener.onTrackSelected(item, position, new ArrayList<AudioItem>(mItems));
					}
					showToast(item.isFavorite() ? "Added to favorites" : "Removed from favorites");
				}

				@Override
				public void onSwipeRemoveFromList(int position) {
					if (position < 0 || position >= mItems.size()) return;
					AudioItem item = mAdapter.removeItemAt(position);
					if (item != null) {
						mItems.remove(item);
						if (mPlaylistId >= 0) {
							mDbHelper.removeAudioFromPlaylist(
								item.getUri().toString(), mPlaylistId);
						}
						notifyPlaylistChanged();
						showToast("Removed from list: " + item.getTitle());
					}
				}
			});

        // ── DragDropHelper ─────────────────────────────────────────────────
        new DragDropHelper(mRecyclerView, new DragDropHelper.DragCallback() {
				@Override
				public void onItemMoved(int fromPosition, int toPosition) {
					mAdapter.moveItem(fromPosition, toPosition);
					// Also update mItems order
					if (fromPosition >= 0 && fromPosition < mItems.size()
                        && toPosition >= 0 && toPosition < mItems.size()) {
						AudioItem item = mItems.remove(fromPosition);
						mItems.add(toPosition, item);
					}
				}
			});

        mRecyclerView.setAdapter(mAdapter);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
        mPlaylistListener = null;
    }

    // =====================================================================
    // MULTI‑SELECT MODE
    // =====================================================================

    private void enterMultiSelectMode(int firstSelectedPosition) {
        mMultiSelectMode = true;
        mSelectedPositions.clear();
        mSelectedPositions.add(firstSelectedPosition);

        View counterBar = getView().findViewById(R.id.multi_select_counter);
        if (counterBar != null) {
			mSwatch = getRootFragment().getSwatch();
            if(mSwatch != null) counterBar.setBackgroundColor(mSwatch.darkVibrant);
			counterBar.setVisibility(View.VISIBLE);
            updateMultiSelectCounter();
        }

        mAdapter.notifyDataSetChanged();
        showToast("Multi‑select mode — long‑press again for options");
    }

    private void exitMultiSelectMode() {
        mMultiSelectMode = false;
        mSelectedPositions.clear();

        View counterBar = getView().findViewById(R.id.multi_select_counter);
        if (counterBar != null) {
			mSwatch = getRootFragment().getSwatch();
			if(mSwatch != null) counterBar.setBackgroundColor(mSwatch.darkVibrant);
            counterBar.setVisibility(View.GONE);
        }
        mAdapter.notifyDataSetChanged();
    }

    private void toggleSelection(int position) {
        if (mSelectedPositions.contains(position)) {
            mSelectedPositions.remove(position);
        } else {
            mSelectedPositions.add(position);
        }

        if (mSelectedPositions.isEmpty()) {
            exitMultiSelectMode();
            return;
        }

        updateMultiSelectCounter();
        mAdapter.notifyItemChanged(position);
    }

    private void updateMultiSelectCounter() {
        if (mMultiSelectCounter != null) {
			mSwatch = getRootFragment().getSwatch();
			if(mSwatch != null){
				mMultiSelectCounter.setTextColor(mSwatch.titleTextColor);
			}
            mMultiSelectCounter.setText(mSelectedPositions.size() + " selected");
        }
    }

    private List<AudioItem> getSelectedItems() {
        List<AudioItem> selected = new ArrayList<AudioItem>();
        for (int pos : mSelectedPositions) {
            if (pos >= 0 && pos < mItems.size()) {
                selected.add(mItems.get(pos));
            }
        }
        return selected;
    }

    // =====================================================================
    // BULK ACTIONS
    // =====================================================================

    private void showBulkActionsDialog() {
        final List<AudioItem> selected = getSelectedItems();
        if (selected.isEmpty()) return;

        // Build options based on context
        List<String> optionList = new ArrayList<String>();
        optionList.add("Add to Playlist");
        optionList.add("Toggle Favorite");
        if (mPlaylistId >= 0) {
            optionList.add("Remove from Playlist");
        }
        optionList.add("Delete Selected");
        optionList.add("Select All");
        optionList.add("Deselect All");
        optionList.add("Cancel");

        final String[] options = optionList.toArray(new String[0]);

        IkBeautifulDialog dialog = new IkBeautifulDialog(getActivity());
        mSwatch = getRootFragment().getSwatch();
		dialog.setBackgroundColor(mSwatch != null ? mSwatch.darkVibrant : R.color.bronze_start);
		dialog.setMessage(selected.size() + " item(s) selected");
        dialog.setItems(options, new IkBeautifulDialog.OnItemClickListener() {
				@Override
				public void onItemClick(int position, String item) {
					if (item.equals("Add to Playlist")) {
						showBulkAddToPlaylistDialog(selected);
					} else if (item.equals("Toggle Favorite")) {
						bulkToggleFavorite(selected);
					} else if (item.equals("Remove from Playlist")) {
						bulkRemoveFromPlaylist(selected);
					} else if (item.equals("Delete Selected")) {
						showBulkDeleteConfirmation(selected);
					} else if (item.equals("Select All")) {
						selectAll();
					} else if (item.equals("Deselect All")) {
						exitMultiSelectMode();
					}
				}
			});
        dialog.showList();
    }

    private void selectAll() {
        mSelectedPositions.clear();
        for (int i = 0; i < mItems.size(); i++) {
            mSelectedPositions.add(i);
        }
        updateMultiSelectCounter();
        mAdapter.notifyDataSetChanged();
    }

    private void bulkToggleFavorite(List<AudioItem> items) {
        for (AudioItem item : items) {
            item.setFavorite(!item.isFavorite());
            mDbHelper.updateFavorite(item.getUri().toString(), item.isFavorite());
        }
        exitMultiSelectMode();
        showToast("Toggled favorite on " + items.size() + " item(s)");
    }

    private void bulkRemoveFromPlaylist(final List<AudioItem> items) {
        if (mPlaylistId < 0) return;

        new Thread(new Runnable() {
				@Override
				public void run() {
					for (AudioItem item : items) {
						mDbHelper.removeAudioFromPlaylist(
                            item.getUri().toString(), mPlaylistId);
					}
					if (getActivity() != null) {
						getActivity().runOnUiThread(new Runnable() {
								@Override
								public void run() {
									for (AudioItem item : items) {
										mItems.remove(item);
									}
									List<AudioListRow> rows = new ArrayList<AudioListRow>();
									for (AudioItem a : mItems) {
										rows.add(AudioListRow.item(a));
									}
									mAdapter.updateRows(rows);
									exitMultiSelectMode();
									notifyPlaylistChanged();
									showToast("Removed " + items.size() + " from playlist");
								}
							});
					}
				}
			}, "bulk-remove-playlist").start();
    }

    private void showBulkDeleteConfirmation(final List<AudioItem> items) {
        IkBeautifulDialog dialog = new IkBeautifulDialog(getActivity());
        mSwatch = getRootFragment().getSwatch();
		dialog.setBackgroundColor(mSwatch != null ? mSwatch.darkVibrant : R.color.bronze_start);
		dialog.setMessage("Delete " + items.size() + " item(s) permanently?\nThis cannot be undone.");
        dialog.setPositiveButton("Delete", new IkBeautifulDialog.OnPositiveClickListener() {
				@Override
				public void onClick() {
					performBulkDelete(items);
				}
			});
        dialog.setNegativeButton("Cancel", null);
        dialog.show();
    }

    private void performBulkDelete(List<AudioItem> items) {
        int deleted = 0;
        for (AudioItem item : items) {
            try {
                int rows = getActivity().getContentResolver().delete(item.getUri(), null, null);
                if (rows > 0) {
                    mItems.remove(item);
                    if (mPlaylistId >= 0) {
                        mDbHelper.removeAudioFromPlaylist(
							item.getUri().toString(), mPlaylistId);
                    }
                    deleted++;
                }
            } catch (Exception ignored) {}
        }

        List<AudioListRow> rows = new ArrayList<AudioListRow>();
        for (AudioItem item : mItems) {
            rows.add(AudioListRow.item(item));
        }
        mAdapter.updateRows(rows);
        exitMultiSelectMode();
        notifyPlaylistChanged();
        showToast("Deleted " + deleted + " file(s)");
    }

    // =====================================================================
    // SINGLE‑ITEM: Add to Playlist
    // =====================================================================

    private void showAddToPlaylistDialog(final AudioItem item) {
        new Thread(new Runnable() {
				@Override
				public void run() {
					final List<MediaDatabaseHelper.PlaylistInfo> playlists =
                        mDbHelper.getAllPlaylists();

					if (getActivity() != null) {
						getActivity().runOnUiThread(new Runnable() {
								@Override
								public void run() {
									if (playlists.isEmpty()) {
										showCreateAndAddSingleDialog(item);
										return;
									}

									String[] names = new String[playlists.size() + 1];
									for (int i = 0; i < playlists.size(); i++) {
										names[i] = playlists.get(i).name
											+ " (" + playlists.get(i).trackCount + ")";
									}
									names[playlists.size()] = "+ Create New Playlist";

									IkBeautifulDialog dialog = new IkBeautifulDialog(getActivity());
									mSwatch = getRootFragment().getSwatch();
									dialog.setBackgroundColor(mSwatch != null ? mSwatch.darkVibrant : R.color.bronze_start);
									dialog.setMessage("Add \"" + item.getTitle() + "\" to playlist");
									dialog.setItems(names, new IkBeautifulDialog.OnItemClickListener() {
											@Override
											public void onItemClick(int position, String name) {
												if (position < playlists.size()) {
													MediaDatabaseHelper.PlaylistInfo pl = playlists.get(position);
													mDbHelper.addAudioToPlaylist(
														item.getUri().toString(), pl.id);
													showToast("Added to " + pl.name);
												} else {
													showCreateAndAddSingleDialog(item);
												}
											}
										});
									dialog.showList();
								}
							});
					}
				}
			}, "playlist-add").start();
    }

    private void showCreateAndAddSingleDialog(final AudioItem item) {
        IkBeautifulDialog dialog = new IkBeautifulDialog(getActivity());
        mSwatch = getRootFragment().getSwatch();
		dialog.setBackgroundColor(mSwatch != null ? mSwatch.darkVibrant : R.color.bronze_start);
		dialog.setMessage("Create New Playlist");
        dialog.setInput("Playlist name", "", new IkBeautifulDialog.OnInputConfirmedListener() {
				@Override
				public void onInputConfirmed(String inputText) {
					if (inputText == null || inputText.trim().isEmpty()) {
						showToast("Name cannot be empty");
						return;
					}
					final String name = inputText.trim();
					mDbHelper.createPlaylist(name, new MediaDatabaseHelper.OnPlaylistCreatedCallback() {
							@Override
							public void onPlaylistCreated(long playlistId) {
								if (playlistId >= 0) {
									mDbHelper.addAudioToPlaylist(item.getUri().toString(), playlistId);
									if (getActivity() != null) {
										getActivity().runOnUiThread(new Runnable() {
												@Override
												public void run() {
													showToast("Added to '" + name + "'");
												}
											});
									}
								}
							}
						});
				}
			});
        dialog.setPositiveButton("Create & Add", null);
        dialog.showInput();
    }

    private void showBulkAddToPlaylistDialog(final List<AudioItem> items) {
        new Thread(new Runnable() {
				@Override
				public void run() {
					final List<MediaDatabaseHelper.PlaylistInfo> playlists =
                        mDbHelper.getAllPlaylists();

					if (getActivity() != null) {
						getActivity().runOnUiThread(new Runnable() {
								@Override
								public void run() {
									if (playlists.isEmpty()) {
										showCreateAndBulkAddDialog(items);
										return;
									}

									String[] names = new String[playlists.size() + 1];
									for (int i = 0; i < playlists.size(); i++) {
										names[i] = playlists.get(i).name
											+ " (" + playlists.get(i).trackCount + ")";
									}
									names[playlists.size()] = "+ Create New Playlist";

									IkBeautifulDialog dialog = new IkBeautifulDialog(getActivity());
									mSwatch = getRootFragment().getSwatch();
									dialog.setBackgroundColor(mSwatch != null ? mSwatch.darkVibrant : R.color.bronze_start);
									dialog.setMessage("Add " + items.size() + " item(s) to playlist");
									dialog.setItems(names, new IkBeautifulDialog.OnItemClickListener() {
											@Override
											public void onItemClick(int position, String name) {
												if (position < playlists.size()) {
													MediaDatabaseHelper.PlaylistInfo pl = playlists.get(position);
													for (AudioItem item : items) {
														mDbHelper.addAudioToPlaylist(
															item.getUri().toString(), pl.id);
													}
													exitMultiSelectMode();
													showToast("Added to " + pl.name);
												} else {
													showCreateAndBulkAddDialog(items);
												}
											}
										});
									dialog.showList();
								}
							});
					}
				}
			}, "playlist-bulk-add").start();
    }

    private void showCreateAndBulkAddDialog(final List<AudioItem> items) {
        IkBeautifulDialog dialog = new IkBeautifulDialog(getActivity());
        mSwatch = getRootFragment().getSwatch();
		dialog.setBackgroundColor(mSwatch != null ? mSwatch.darkVibrant : R.color.bronze_start);
		dialog.setMessage("Create New Playlist for " + items.size() + " item(s)");
        dialog.setInput("Playlist name", "", new IkBeautifulDialog.OnInputConfirmedListener() {
				@Override
				public void onInputConfirmed(String inputText) {
					if (inputText == null || inputText.trim().isEmpty()) {
						showToast("Name cannot be empty");
						return;
					}
					final String name = inputText.trim();
					mDbHelper.createPlaylist(name, new MediaDatabaseHelper.OnPlaylistCreatedCallback() {
							@Override
							public void onPlaylistCreated(long playlistId) {
								if (playlistId >= 0) {
									for (AudioItem item : items) {
										mDbHelper.addAudioToPlaylist(
											item.getUri().toString(), playlistId);
									}
									if (getActivity() != null) {
										getActivity().runOnUiThread(new Runnable() {
												@Override
												public void run() {
													exitMultiSelectMode();
													showToast("Added " + items.size()
															  + " item(s) to '" + name + "'");
												}
											});
									}
								}
							}
						});
				}
			});
        dialog.setPositiveButton("Create & Add", null);
        dialog.showInput();
    }

    // =====================================================================
    // SINGLE‑ITEM: Remove from Playlist
    // =====================================================================

    private void removeSingleFromPlaylist(final AudioItem item, final int position) {
        IkBeautifulDialog dialog = new IkBeautifulDialog(getActivity());
        mSwatch = getRootFragment().getSwatch();
		dialog.setBackgroundColor(mSwatch != null ? mSwatch.darkVibrant : R.color.bronze_start);
		dialog.setMessage("Remove \"" + item.getTitle() + "\" from this playlist?");
        dialog.setPositiveButton("Remove", new IkBeautifulDialog.OnPositiveClickListener() {
				@Override
				public void onClick() {
					mDbHelper.removeAudioFromPlaylist(item.getUri().toString(), mPlaylistId);
					mItems.remove(position);
					mAdapter.removeItemAt(position);
					notifyPlaylistChanged();
					showToast("Removed from playlist");
				}
			});
        dialog.setNegativeButton("Cancel", null);
        dialog.show();
    }

    // =====================================================================
    // SINGLE‑ITEM: Rename (file rename via MediaStore)
    // =====================================================================

    private void showRenameTrackDialog(final AudioItem item, final int position) {
        // Extract current name without extension
        String currentName = item.getTitle();
        String extension = "";
        int dotIndex = currentName.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = currentName.substring(dotIndex);
            currentName = currentName.substring(0, dotIndex);
        }
        final String ext = extension;

        IkBeautifulDialog dialog = new IkBeautifulDialog(getActivity());
        mSwatch = getRootFragment().getSwatch();
		dialog.setBackgroundColor(mSwatch != null ? mSwatch.darkVibrant : R.color.bronze_start);
		dialog.setMessage("Rename \"" + item.getTitle() + "\"");
        dialog.setInput("New name", currentName, new IkBeautifulDialog.OnInputConfirmedListener() {
				@Override
				public void onInputConfirmed(String inputText) {
					if (inputText == null || inputText.trim().isEmpty()) {
						showToast("Name cannot be empty");
						return;
					}
					final String newName = inputText.trim() + ext;
					try {
						android.content.ContentValues values = new android.content.ContentValues();
						values.put(android.provider.MediaStore.Audio.Media.DISPLAY_NAME, newName);
						values.put(android.provider.MediaStore.Audio.Media.TITLE, inputText.trim());
						int updated = getActivity().getContentResolver().update(
                            item.getUri(), values, null, null);
						if (updated > 0) {
							// Update local item
							item.setTitle(inputText.trim());
							mAdapter.notifyItemChanged(position);
							showToast("Renamed to " + inputText.trim());
						} else {
							showToast("Rename failed");
						}
					} catch (Exception e) {
						showToast("Rename failed: " + e.getMessage());
					}
				}
			});
        dialog.setPositiveButton("Rename", null);
        dialog.setNegativeButton("Cancel", null);
        dialog.showInput();
    }

	private AudioPlayerFragment getRootFragment(){
		Fragment parent = getParentFragment();
		if(parent instanceof AudioPlayerFragment){
			return (AudioPlayerFragment) parent;
		} else{
			return (AudioPlayerFragment) parent.getParentFragment();
		}

	}

    // =====================================================================
    // CLICK LISTENERS
    // =====================================================================

    @Override
    public void onAudioItemClick(AudioItem item, int position) {
        boolean isInSearchMode = getRootFragment().isSearchbarVisible();
		if (mMultiSelectMode) {
            toggleSelection(position);
			if(isInSearchMode) getRootFragment().hideSearchBar();
            return;
        }

        if (mListener != null) {
            mListener.onTrackSelected(item, position, new ArrayList<AudioItem>(mItems));
			if(isInSearchMode) getRootFragment().hideSearchBar();
        }
    }

    @Override
    public boolean onAudioItemLongClick(AudioItem item, int position) {
        final AudioItem fItem = item;
        final int fPosition = position;

        if (mMultiSelectMode) {
            showBulkActionsDialog();
            return true;
        }

        enterMultiSelectMode(position);
        mRecyclerView.scrollToPosition(position);
        // Build context-aware options
        List<String> optionList = new ArrayList<String>();
        optionList.add("Play");
        optionList.add("Add to Playlist");
        optionList.add("Toggle Favorite");
        optionList.add("Rename");
		optionList.add("Edit Metadata");
        if (mPlaylistId >= 0) {
            optionList.add("Remove from Playlist");
        }
        optionList.add("Select More...");
        optionList.add("Cancel");

        final String[] options = optionList.toArray(new String[0]);

        IkBeautifulDialog dialog = new IkBeautifulDialog(getActivity());
        mSwatch = getRootFragment().getSwatch();
		dialog.setBackgroundColor(mSwatch != null ? mSwatch.darkVibrant : getActivity().getResources().getColor(R.color.bronze_start));
		dialog.setMessage(item.getTitle());
        dialog.setItems(options, new IkBeautifulDialog.OnItemClickListener() {
				@Override
				public void onItemClick(int which, String option) {
					if (option.equals("Play")) {
						exitMultiSelectMode();
						if (mListener != null) {
							mListener.onTrackSelected(fItem, fPosition,
													  new ArrayList<AudioItem>(mItems));
						}
					} else if (option.equals("Add to Playlist")) {
						showAddToPlaylistDialog(fItem);
						exitMultiSelectMode();
					} else if (option.equals("Toggle Favorite")) {
						fItem.setFavorite(!fItem.isFavorite());
						mDbHelper.updateFavorite(fItem.getUri().toString(), fItem.isFavorite());
						mAdapter.notifyItemChanged(fPosition);
						exitMultiSelectMode();
					} else if (option.equals("Rename")) {
						showRenameTrackDialog(fItem, fPosition);
						exitMultiSelectMode();
					} else if (options.equals("Edit Metadata")){
						getRootFragment().openMetadataEditor(item);
					} else if (option.equals("Remove from Playlist")) {
						removeSingleFromPlaylist(fItem, fPosition);
						exitMultiSelectMode();
					} else if (option.equals("Select More...")) {
						// Stay in multi‑select mode
					} else {
						exitMultiSelectMode();
					}
				}
			});
        dialog.showList();
		if(getRootFragment().isSearchbarVisible()) getRootFragment().hideSearchBar();
        return true;
    }

    @Override
    public void onFavoriteClicked(AudioItem item, int position) {
        if (mMultiSelectMode) {
            toggleSelection(position);
            return;
        }
        item.setFavorite(!item.isFavorite()); // Negated because here we are toggling fav.
        mDbHelper.updateFavorite(item.getUri().toString(), item.isFavorite());
        mAdapter.notifyItemChanged(position);
    }

    @Override
    public void onOptionsClicked(AudioItem item, int position) {
        // The "more options" button was clicked — show the same context menu
        onAudioItemLongClick(item, position);
    }

    // =====================================================================
    // HELPERS
    // =====================================================================

    private void notifyPlaylistChanged() {
        if (mPlaylistListener != null && mPlaylistId >= 0) {
            mPlaylistListener.onPlaylistChanged(mPlaylistId);
        }
    }

    public AudioSectionedAdapter getAdapter() {
        return mAdapter;
    }

    public void notifyItemChanged(String filePath) {
        if (mAdapter != null) mAdapter.notifyItemChangedByPath(filePath);
    }

    public void notifyItemChanged(Uri uri) {
        if (mAdapter != null) mAdapter.notifyItemChangedByUri(uri);
    }

    public boolean isItemSelected(int position) {
        return mMultiSelectMode && mSelectedPositions.contains(position);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void showToast(String msg) {
        if (getActivity() != null) {
            Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }
}