package com.ikechi.studio.onwa.player.fragment;

import android.app.AlertDialog;
import androidx.fragment.app.Fragment;
import android.app.RecoverableSecurityException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.ikechi.studio.onwa.player.MainActivity;
import com.ikechi.studio.onwa.player.R;
import com.ikechi.studio.onwa.player.models.VideoItem;
import com.ikechi.studio.onwa.player.utils.IkBeautifulDialog;
import com.ikechi.studio.onwa.player.utils.MediaDatabaseHelper;
import com.ikechi.studio.onwa.player.utils.MediaUtils;
import com.ikechi.studio.IkLog;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoPlayerFragment extends Fragment {

    private static final String TAG = "VideoPlayerFragment";
    private static final String READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO";
    private static final int REQ_STORAGE_PERMS = 1001;
    private static final int REQ_DELETE_PERMISSION = 1002;
    private static final int REQ_RENAME_PERMISSION = 1003;

    private GridView gridView;
    private ProgressBar progressBar;
    private TextView emptyText;
    private VideoGridAdapter adapter;
    private List<VideoItem> videoList = new ArrayList<>();
    private Handler uiHandler = new Handler(Looper.getMainLooper());
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    // Temp storage for pending permission actions
    private VideoItem mPendingDeleteItem = null;
    private int mPendingDeletePos = -1;
    private VideoItem mPendingRenameItem = null;
    private int mPendingRenamePos = -1;
    private String mPendingNewBaseName = null;
    private String mPendingExtension = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        IkLog.d(TAG, "onCreateView started");
        View view = inflater.inflate(R.layout.fragment_video_player, container, false);

        // Set instant flush for debugging – remove in production
        IkLog.setInstantFlush(true);

        try {
            gridView = view.findViewById(R.id.video_grid);
            progressBar = view.findViewById(R.id.progress_bar);
            emptyText = view.findViewById(R.id.empty_text);

            adapter = new VideoGridAdapter();
            gridView.setAdapter(adapter);
            IkLog.d(TAG, "Adapter set on gridView");

            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).setActiveFragment(this);
                IkLog.d(TAG, "Registered as active fragment in MainActivity");
            }

            gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
					@Override
					public void onItemClick(AdapterView<?> parent, View view,
											int position, long id) {
						if (position >= 0 && position < videoList.size()) {
							VideoItem item = videoList.get(position);
							IkLog.i(TAG, "Video item clicked: " + item.getTitle());
							openVideoPlayer(item.getUri(), item.getTitle(), position, videoList);
						}
					}
				});

            loadVideos();
        } catch (Exception e) {
            IkLog.e(TAG, "Error in onCreateView", e);
        }
        IkLog.d(TAG, "onCreateView finished");
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        IkLog.d(TAG, "onResume");
        try {
            update();
        } catch (Exception e) {
            IkLog.e(TAG, "Error in onResume", e);
        }
    }

    private void update() {
        IkLog.i(TAG, "update() – refreshing adapter and video list");
        try {
            adapter = new VideoGridAdapter();
            if (gridView != null) {
                gridView.setAdapter(adapter);
            } else {
                gridView = getView().findViewById(R.id.video_grid);
                gridView.setAdapter(adapter);
                IkLog.w(TAG, "gridView was null, re-obtained from getView()");
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error adding adapter to gridView", e);
        }

        try {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).setActiveFragment(this);
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error setting active fragment", e);
        }

        try {
            gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
					@Override
					public void onItemClick(AdapterView<?> parent, View view,
											int position, long id) {
						if (position >= 0 && position < videoList.size()) {
							VideoItem item = videoList.get(position);
							openVideoPlayer(item.getUri(), item.getTitle(), position, videoList);
						}
					}
				});
            IkLog.d(TAG, "OnItemClickListener set on gridView");
        } catch (Exception e) {
            IkLog.e(TAG, "Error setting OnItemClickListener", e);
        }

        try {
            refreshVideos();
            IkLog.i(TAG, "refreshVideos() called from update()");
        } catch (Exception e) {
            IkLog.e(TAG, "Error in update", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        IkLog.d(TAG, "onDestroy");
        try {
            if (executorService != null) {
                executorService.shutdownNow();
                executorService = null;
                IkLog.d(TAG, "ExecutorService shut down");
            }
            if (uiHandler != null) {
                uiHandler.removeCallbacksAndMessages(null);
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error in onDestroy", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean granted = grantResults.length > 0 &&
			grantResults[0] == PackageManager.PERMISSION_GRANTED;
        IkLog.d(TAG, "onRequestPermissionsResult – request=" + requestCode +
                ", granted=" + granted);

        if (granted) {
            if (requestCode == REQ_STORAGE_PERMS) {
                if (mPendingDeleteItem != null) {
                    IkLog.d(TAG, "Permission granted, executing pending delete");
                    deleteVideoFile(mPendingDeleteItem, mPendingDeletePos);
                } else if (mPendingRenameItem != null) {
                    IkLog.d(TAG, "Permission granted, executing pending rename");
                    renameVideoFile(mPendingRenameItem, mPendingRenamePos,
                                    mPendingNewBaseName, mPendingExtension);
                } else {
                    loadVideos();
                    IkLog.d(TAG, "Permission granted, reloading videos");
                }
            }
        } else {
            Toast.makeText(getActivity(),
						   "Storage permission denied. Cannot access media.",
						   Toast.LENGTH_LONG).show();
        }
        clearPendingActions();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        IkLog.d(TAG, "onActivityResult – request=" + requestCode +
                ", result=" + resultCode);
        if (resultCode == android.app.Activity.RESULT_OK) {
            if (requestCode == REQ_DELETE_PERMISSION && mPendingDeleteItem != null) {
                deleteVideoFile(mPendingDeleteItem, mPendingDeletePos);
            } else if (requestCode == REQ_RENAME_PERMISSION && mPendingRenameItem != null) {
                renameVideoFile(mPendingRenameItem, mPendingRenamePos,
                                mPendingNewBaseName, mPendingExtension);
            }
        }
        clearPendingActions();
    }

    private void clearPendingActions() {
        mPendingDeleteItem = null;
        mPendingDeletePos = -1;
        mPendingRenameItem = null;
        mPendingRenamePos = -1;
        mPendingNewBaseName = null;
        mPendingExtension = null;
        IkLog.d(TAG, "Pending actions cleared");
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return getActivity().checkSelfPermission(READ_MEDIA_VIDEO) ==
				PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return getActivity().checkSelfPermission(
				android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
				PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private boolean hasWritePermission() {
        if (Build.VERSION.SDK_INT >= 30) return true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return getActivity().checkSelfPermission(
				android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
				PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestStoragePermission() {
        IkLog.d(TAG, "requestStoragePermission – SDK=" + Build.VERSION.SDK_INT);
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{READ_MEDIA_VIDEO}, REQ_STORAGE_PERMS);
        } else if (Build.VERSION.SDK_INT >= 31) {
            requestPermissions(new String[]{
								   android.Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_STORAGE_PERMS);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{
								   android.Manifest.permission.READ_EXTERNAL_STORAGE,
								   android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE_PERMS);
        }
    }

    private void loadVideos() {
        if (getActivity() == null) {
            IkLog.w(TAG, "loadVideos – Activity is null, aborting");
            return;
        }

        if (!hasStoragePermission()) {
            IkLog.d(TAG, "loadVideos – no storage permission, requesting");
            requestStoragePermission();
            return;
        }

        IkLog.d(TAG, "loadVideos – showing progress, loading from MediaUtils");
        progressBar.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);

        MediaUtils.getVideoFilesAsync(getActivity(), uiHandler,
            new MediaUtils.VideoFilesCallback() {
                @Override
                public void onVideoFilesLoaded(final List<VideoItem> videoItems) {
                    if (getActivity() == null) {
                        IkLog.w(TAG, "loadVideos callback – Activity null");
                        return;
                    }

                    getActivity().runOnUiThread(new Runnable() {
							@Override
							public void run() {
								try {
									IkLog.d(TAG, "loadVideos callback – received " +
											(videoItems != null ? videoItems.size() : 0) +
											" video items");
									progressBar.setVisibility(View.GONE);
									videoList.clear();

									if (videoItems != null) {
										MediaDatabaseHelper db =
											MediaDatabaseHelper.getInstance(getActivity());
										for (VideoItem item : videoItems) {
											VideoItem dbItem = db.getVideoByUri(item.getUri());
											if (dbItem != null) {
												item.setLastPosition(dbItem.getLastPosition());
											}
											videoList.add(item);
										}
										IkLog.d(TAG, "Video list populated, size=" +
												videoList.size());
									}

									adapter.notifyDataSetChanged();
									emptyText.setVisibility(
										videoList.isEmpty() ? View.VISIBLE : View.GONE);
								} catch (Exception e) {
									IkLog.e(TAG, "Error in loadVideos callback", e);
								}
							}
						});
                }
            }, true);
    }

    public void refreshVideos() {
        IkLog.i(TAG, "refreshVideos called");

        if (getActivity() == null) {
            IkLog.w(TAG, "refreshVideos – Activity null, aborting");
            return;
        }

        if (!hasStoragePermission()) {
            IkLog.d(TAG, "refreshVideos – no storage permission, requesting");
            requestStoragePermission();
            return;
        }

        try {
            progressBar.setVisibility(View.VISIBLE);
            emptyText.setVisibility(View.GONE);

            if (uiHandler == null) {
                uiHandler = new Handler(Looper.getMainLooper());
                IkLog.w(TAG, "uiHandler was null, created new one");
            }

            MediaUtils.getVideoFilesAsync(getActivity(), uiHandler,
                new MediaUtils.VideoFilesCallback() {
                    @Override
                    public void onVideoFilesLoaded(final List<VideoItem> videoItems) {
                        try {
                            getActivity().runOnUiThread(new Runnable() {
									@Override
									public void run() {
										try {
											IkLog.d(TAG, "refreshVideos callback – items=" +
													(videoItems != null ? ("" + videoItems.size()) : "null"));
											if (progressBar != null) {
												progressBar.setVisibility(View.GONE);
											} else {
												IkLog.w(TAG, "progressBar is null");
											}

											if (videoItems != null) {
												if (videoList != null) {
													videoList.clear();
												} else {
													videoList = new ArrayList<VideoItem>();
													IkLog.w(TAG, "videoList was null, created new");
												}

												MediaDatabaseHelper db =
													MediaDatabaseHelper.getInstance(getActivity());
												for (VideoItem item : videoItems) {
													VideoItem dbItem = db.getVideoByUri(item.getUri());
													if (dbItem != null) {
														item.setLastPosition(dbItem.getLastPosition());
													}
													videoList.add(item);
												}
												IkLog.d(TAG, "videoList populated, size=" +
														videoList.size());
											}

											adapter.notifyDataSetChanged();
											emptyText.setVisibility(
												videoList.isEmpty() ? View.VISIBLE : View.GONE);
											IkLog.i(TAG, "refreshVideos finished successfully");
										} catch (Exception e) {
											IkLog.e(TAG, "Error in refreshVideos callback", e);
										}
									}
								});
                        } catch (Exception e) {
                            IkLog.e(TAG, "Error posting to UI thread in refreshVideos", e);
                        }
                    }
                }, true);
        } catch (Throwable e) {
            IkLog.e(TAG, "Error in refreshVideos", e);
        }
    }

    private void openVideoPlayer(Uri uri, String title, int position,
                                 List<VideoItem> list) {
        try {
            if (getActivity() == null || getFragmentManager() == null) {
                IkLog.w(TAG, "openVideoPlayer – Activity/FragmentManager null");
                return;
            }

            ArrayList<String> uris = new ArrayList<>();
            ArrayList<String> titles = new ArrayList<>();
            for (VideoItem v : list) {
                uris.add(v.getUri().toString());
                titles.add(v.getTitle());
            }

            IkLog.i(TAG, "Opening VideoPlaybackFragment for: " + title);
            VideoPlaybackFragment fragment = VideoPlaybackFragment.newInstance(
                uri.toString(), title, position, uris, titles, list);

            getFragmentManager()
                .beginTransaction()
                .replace(R.id.main_container, fragment, "video_playback")
                .addToBackStack("video_playback")
                .commit();
        } catch (Exception e) {
            IkLog.e(TAG, "Error in openVideoPlayer", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Adapter
    // ─────────────────────────────────────────────────────────────────────
    private class VideoGridAdapter extends BaseAdapter {
        @Override
        public int getCount() { return videoList.size(); }

        @Override
        public Object getItem(int position) { return videoList.get(position); }

        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getActivity().getLayoutInflater()
                    .inflate(R.layout.item_video_grid, parent, false);
            }

            final VideoItem item = videoList.get(position);
            final int pos = position;

            TextView titleTv = convertView.findViewById(R.id.title);
            TextView durationTv = convertView.findViewById(R.id.duration);
            ImageView thumbnail = convertView.findViewById(R.id.thumbnail);

            titleTv.setText(item.getTitle());
            durationTv.setText(formatDuration(item.getDuration()));

            loadThumbnailAsync(item.getUri(), thumbnail);

            convertView.setOnLongClickListener(new View.OnLongClickListener() {
					@Override
					public boolean onLongClick(View v) {
						IkLog.d(TAG, "Long click on video: " + item.getTitle());
						showVideoOptionsDialog(item, pos);
						return true;
					}
				});

            convertView.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						IkLog.d(TAG, "Click on video: " + item.getTitle());
						if (pos >= 0 && pos < videoList.size()) {
							openVideoPlayer(item.getUri(), item.getTitle(), pos, videoList);
						}
					}
				});

            return convertView;
        }

        private void loadThumbnailAsync(final Uri videoUri, final ImageView imageView) {
            if (executorService == null || executorService.isShutdown()) {
                executorService = Executors.newSingleThreadExecutor();
                IkLog.d(TAG, "ExecutorService recreated for thumbnail loading");
            }
            executorService.execute(new Runnable() {
					@Override
					public void run() {
						final Bitmap bitmap = MediaUtils.getVideoThumbnailCached(
							getActivity(), videoUri);
						if (uiHandler == null) {
							uiHandler = new Handler(Looper.getMainLooper());
						}
						uiHandler.post(new Runnable() {
								@Override
								public void run() {
									if (bitmap != null) {
										imageView.setImageBitmap(bitmap);
									} else {
										imageView.setImageResource(R.drawable.ic_video_placeholder);
									}
								}
							});
					}
				});
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Options (delete / rename)
    // ─────────────────────────────────────────────────────────────────────
    private void showVideoOptionsDialog(final VideoItem item, final int position) {
        if (getActivity() == null) return;
        final String[] options = {"Delete", "Rename"};
        new AlertDialog.Builder(getActivity())
            .setTitle("Choose action for: " + item.getTitle())
            .setItems(options, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (which == 0) {
                        showDeleteConfirmationDialog(item, position);
                    } else if (which == 1) {
                        showRenameDialog(item, position);
                    }
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showDeleteConfirmationDialog(final VideoItem item, final int position) {
        if (getActivity() == null) return;
        new IkBeautifulDialog(getActivity())
            .setMessage("Delete \"" + item.getTitle() + "\"?")
            .setPositiveButton("Delete", new IkBeautifulDialog.OnPositiveClickListener() {
                @Override
                public void onClick() {
                    if (hasWritePermission()) {
                        deleteVideoFile(item, position);
                    } else {
                        mPendingDeleteItem = item;
                        mPendingDeletePos = position;
                        requestStoragePermission();
                    }
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void deleteVideoFile(final VideoItem item, final int position) {
        if (executorService == null || executorService.isShutdown()) {
            executorService = Executors.newSingleThreadExecutor();
        }
        IkLog.d(TAG, "deleteVideoFile – starting delete for: " + item.getTitle());

        executorService.execute(new Runnable() {
				@Override
				public void run() {
					boolean success = false;
					String errorMessage = null;
					try {
						ContentResolver resolver = getActivity().getContentResolver();
						Uri uri = item.getUri();
						try {
							int rows = resolver.delete(uri, null, null);
							success = rows > 0;
						} catch (RecoverableSecurityException e) {
							if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
								IntentSender intentSender =
									e.getUserAction().getActionIntent().getIntentSender();
								try {
									mPendingDeleteItem = item;
									mPendingDeletePos = position;
									startIntentSenderForResult(intentSender,
															   REQ_DELETE_PERMISSION, null, 0, 0, 0, null);
									return;
								} catch (Exception ex) {
									errorMessage = "Failed to request delete permission";
								}
							} else {
								errorMessage = "Permission denied";
							}
						} catch (SecurityException e) {
							errorMessage = "Permission denied by system";
						}
						if (!success && errorMessage == null) {
							errorMessage = "File could not be deleted";
						}
					} catch (Exception e) {
						errorMessage = e.getMessage();
						IkLog.e(TAG, "deleteVideoFile exception", e);
					}

					final boolean finalSuccess = success;
					final String finalError = errorMessage;

					if (uiHandler == null) uiHandler = new Handler(Looper.getMainLooper());
					uiHandler.post(new Runnable() {
							@Override
							public void run() {
								if (getActivity() == null) return;
								if (finalSuccess) {
									videoList.remove(position);
									adapter.notifyDataSetChanged();
									if (videoList.isEmpty()) emptyText.setVisibility(View.VISIBLE);
									Toast.makeText(getActivity(), "Video deleted",
												   Toast.LENGTH_SHORT).show();
									IkLog.i(TAG, "Video deleted: " + item.getTitle());
								} else {
									String msg = "Failed to delete video";
									if (finalError != null) msg += ": " + finalError;
									Toast.makeText(getActivity(), msg, Toast.LENGTH_LONG).show();
									IkLog.e(TAG, msg);
								}
							}
						});
				}
			});
    }

    private void showRenameDialog(final VideoItem item, final int position) {
        if (getActivity() == null) return;

        String originalName = item.getTitle();
        String extension = "";
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalName.substring(dotIndex);
            originalName = originalName.substring(0, dotIndex);
        }
        final String ext = extension;

        new IkBeautifulDialog(getActivity())
            .setMessage("Enter new name for \"" + item.getTitle() + "\"")
            .setInput("Video name (without extension)", originalName,
			new IkBeautifulDialog.OnInputConfirmedListener() {
				@Override
				public void onInputConfirmed(String newBaseName) {
					if (newBaseName == null || newBaseName.trim().isEmpty()) {
						Toast.makeText(getActivity(), "Name cannot be empty",
									   Toast.LENGTH_SHORT).show();
						return;
					}
					if (hasWritePermission()) {
						renameVideoFile(item, position, newBaseName.trim(), ext);
					} else {
						mPendingRenameItem = item;
						mPendingRenamePos = position;
						mPendingNewBaseName = newBaseName.trim();
						mPendingExtension = ext;
						requestStoragePermission();
					}
				}
			})
            .setPositiveButton("Rename", null)
            .showInput();
    }

    private void renameVideoFile(final VideoItem item, final int position,
                                 final String newBaseName, final String extension) {
        if (executorService == null || executorService.isShutdown()) {
            executorService = Executors.newSingleThreadExecutor();
        }
        IkLog.d(TAG, "renameVideoFile – renaming to: " + newBaseName + extension);

        executorService.execute(new Runnable() {
				@Override
				public void run() {
					String errorMessage = null;
					VideoItem updatedItem = null;
					boolean success = false;
					try {
						ContentResolver resolver = getActivity().getContentResolver();
						Uri updateUri = item.getUri();
						String newFileName = newBaseName + extension;

						ContentValues values = new ContentValues();
						values.put(MediaStore.Video.Media.DISPLAY_NAME, newFileName);
						values.put(MediaStore.Video.Media.TITLE, newBaseName);

						try {
							int updated = resolver.update(updateUri, values, null, null);
							if (updated > 0) {
								success = true;
								Cursor c = null;
								String newPath = null;
								try {
									c = resolver.query(updateUri,
													   new String[]{MediaStore.Video.Media.DATA},
													   null, null, null);
									if (c != null && c.moveToFirst()) {
										newPath = c.getString(0);
									}
								} catch (Exception ignored) {
								} finally {
									if (c != null) c.close();
								}
								updatedItem = createUpdatedVideoItem(newPath, newBaseName,
																	 item, updateUri);
							} else {
								errorMessage = "MediaStore update failed";
							}
						} catch (RecoverableSecurityException e) {
							if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
								IntentSender intentSender =
									e.getUserAction().getActionIntent().getIntentSender();
								try {
									mPendingRenameItem = item;
									mPendingRenamePos = position;
									mPendingNewBaseName = newBaseName;
									mPendingExtension = extension;
									startIntentSenderForResult(intentSender,
															   REQ_RENAME_PERMISSION, null, 0, 0, 0, null);
									return;
								} catch (Exception ex) {
									errorMessage = "Failed to request rename permission";
								}
							} else {
								errorMessage = "Permission denied";
							}
						} catch (SecurityException e) {
							errorMessage = "Permission denied by system";
						}
					} catch (Exception e) {
						errorMessage = e.getMessage();
						IkLog.e(TAG, "renameVideoFile exception", e);
					}

					final boolean finalSuccess = success;
					final String finalError = errorMessage;
					final VideoItem finalUpdatedItem = updatedItem;

					if (uiHandler == null) uiHandler = new Handler(Looper.getMainLooper());
					uiHandler.post(new Runnable() {
							@Override
							public void run() {
								if (finalSuccess && finalUpdatedItem != null) {
									videoList.set(position, finalUpdatedItem);
									adapter.notifyDataSetChanged();
									Toast.makeText(getActivity(),
												   "Renamed to: " + finalUpdatedItem.getTitle(),
												   Toast.LENGTH_SHORT).show();
									IkLog.i(TAG, "Video renamed to: " + finalUpdatedItem.getTitle());
								} else {
									String msg = "Rename failed";
									if (finalError != null) msg += ": " + finalError;
									Toast.makeText(getActivity(), msg, Toast.LENGTH_LONG).show();
									IkLog.e(TAG, msg);
								}
							}
						});
				}
			});
    }

    private VideoItem createUpdatedVideoItem(String newPath, String newBaseName,
                                             VideoItem oldItem, Uri newUri) {
        return new VideoItem(
            newUri,
            newPath,
            newBaseName,
            oldItem.getDuration(),
            oldItem.getWidth(),
            oldItem.getHeight(),
            oldItem.getSize()
        );
    }

    private String formatDuration(long ms) {
        long s = ms / 1000;
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