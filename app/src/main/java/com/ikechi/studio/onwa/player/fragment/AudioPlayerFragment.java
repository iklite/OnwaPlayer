package com.ikechi.studio.onwa.player.fragment;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.ikechi.studio.IkLog;
import com.ikechi.studio.onwa.player.MainActivity;
import com.ikechi.studio.onwa.player.R;
import com.ikechi.studio.onwa.player.adapter.AudioSectionedAdapter;
import com.ikechi.studio.onwa.player.constants.RepeatMode;
import com.ikechi.studio.onwa.player.models.AudioItem;
import com.ikechi.studio.onwa.player.models.AudioListRow;
import com.ikechi.studio.onwa.player.models.Category;
import com.ikechi.studio.onwa.player.service.AudioPlayerService;
import com.ikechi.studio.onwa.player.utils.ColorExtractor;
import com.ikechi.studio.onwa.player.utils.IkBeautifulDialog;
import com.ikechi.studio.onwa.player.utils.LyricsHelper;
import com.ikechi.studio.onwa.player.utils.MediaDatabaseHelper;
import com.ikechi.studio.onwa.player.utils.MediaUtils;
import com.ikechi.studio.onwa.player.utils.PrefUtils;
import com.ikechi.studio.onwa.player.utils.SettingsManager;
import com.ikechi.studio.onwa.player.utils.SettingsUtils;
import com.ikechi.studio.onwa.player.view.CoverFlowView;
import com.ikechi.studio.onwa.player.view.GLVisualizerView;
import com.ikechi.studio.onwa.player.view.WaveformSeekBar;
import com.ikechi.studio.onwa.widgets.MultiPanelLayout;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class AudioPlayerFragment extends Fragment
implements SettingsManager.OnVisualizerSettingsChangedListener,
LibraryCategoryFragment.OnCategorySelectedListener,
TrackListFragment.OnTrackSelectedListener {

    private static final String TAG = "AudioPlayerFragment";

    private static final int DISPLAY_ALBUM_ART  = 0;
    private static final int DISPLAY_CAROUSEL   = 1;
    private static final int DISPLAY_VISUALIZER = 2;
    private int mDisplayMode = DISPLAY_ALBUM_ART;

    private static final int REQUEST_READ_STORAGE = 100;

    // ── Backstack tag for the equalizer ─────────────────────────────
    private static final String BACKSTACK_EQUALIZER = "equalizer";

    // ── UI ──────────────────────────────────────────────────────────
    private MultiPanelLayout mMultiPanel;
    private FrameLayout mContentFrame;
    private ViewGroup mNowPlayingPanel;
    private ImageView mAlbumArt;
    private CoverFlowView mCoverFlowView;
    private GLVisualizerView mGlVisualizerView;
    private View mSwipeLayer;
    private TextView mSongTitle, mArtistName, mAlbumName;
    private TextView mHandleTitle, mHandleArtist;
    private EditText searchEditText;
    private WaveformSeekBar mWaveformSeekBar;
    private TextView mCurrentTime, mTotalTime;
    private ImageButton mBtnPlayPause, mBtnNext, mBtnPrevious,
	mBtnRepeat, mBtnShuffle, mBtnFavorite,
	mBtnSwitchVisualizer, mBtnVisualizerStyle, mBtnColorScheme,
	btnNowPlayingClose;
    private View mBtnQueue;                      // now a FAB
    private ImageButton mBtnPlaybackSpeed;
    private ImageButton mBtnSleepTimer;
    private ImageButton mBtnRepeatAB;
    private ImageButton mBtnEqualizer;
    private ImageButton mBtnMoreOptions;
    private ImageButton mBtnLyrics;
    private View mLyricsSection;                 // changed from LinearLayout to View
    private TextView mLyricsContent;
    private boolean searchBarShown = false;
    private List<AudioItem> mAudioList;
    private Uri mCurrentPlayingUri = null;
    private String mCurrentDisplayPath = null;
    private int mCurrentPlayingPosition = -1;
    private boolean mPanelVisible = false;
    private boolean mIsSeeking = false;
    private boolean mPanelSuppressed = false;

    /**
     * True while the equalizer fragment is on the back stack AND was opened
     * from the NowPlayingPanel. Used to restore the panel when the equalizer
     * is popped.
     */
    private boolean mEqualizerOpenedFromPanel = false;

    private MainActivity mActivity;
    private boolean mIsServiceBound = false;

    private Handler mProgressHandler;
    private Runnable mProgressRunnable;
    private Handler mVisualizerHandler;
    private Runnable mVisualizerRunnable;
    private long mLastVisualizerUpdate = 0;
    private int mVisualizerUpdateInterval = 16;

    private SettingsManager mSettingsManager;
    private SettingsManager.VisualizerSettings mCurrentVisualizerSettings;
    private SettingsManager.PlayerSettings mCurrentPlayerSettings;

    private float[] mIdleFFTData;
    private boolean mIsVisualizerActive = true;

    private RepeatMode mRepeatMode = new RepeatMode(RepeatMode.REPEAT_MODE_NONE);
    private boolean mIsShuffle = false;
    private boolean mIsFavorite = false;

    // ── A-B Repeat ─────────────────────────────────────────────────
    private boolean mIsRepeatAB = false;
    private int mRepeatAPos = -1;
    private int mRepeatBPos = -1;
    private Handler mRepeatABHandler;
    private Runnable mRepeatABRunnable;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private ShakeDetector mShakeDetector;

    private GestureDetector mSwipeGestureDetector;
    private PlaybackReceiver mPlaybackReceiver;
    private ColorExtractor.Swatch mCurrentSwatch;
    private boolean mLyricsVisible = false;

    private boolean mIsScanningAudio = false;

    private static class PlaybackReceiver extends BroadcastReceiver {
        private final WeakReference<AudioPlayerFragment> mRef;
        PlaybackReceiver(AudioPlayerFragment f) { mRef = new WeakReference<>(f); }
        @Override public void onReceive(Context ctx, Intent intent) {
            AudioPlayerFragment frag = mRef.get();
            if (frag != null) frag.handleBroadcast(intent);
        }
    }

    // =========================================================================
    // Fragment Lifecycle
    // =========================================================================

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setRetainInstance(true);
            IkLog.d(TAG, "onCreate");
        } catch (Exception e) {
            IkLog.e(TAG, "Error in onCreate", e);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.fragment_audio_player, container, false);
            mContentFrame = (FrameLayout) view.findViewById(R.id.content_frame);
            initNowPlayingPanel(view);
            searchEditText = (EditText) view.findViewById(R.id.searchEditText);
            initSearchBar(view);
            initRefreshButton(view);

            mActivity = (MainActivity) getActivity();
            if (mActivity == null) {
                IkLog.e(TAG, "onCreateView: host Activity is null");
                return view;
            }

            // ── Equalizer button ──────────────────────────────────────────────
            mBtnEqualizer = view.findViewById(R.id.btn_now_equalizer);
            if (mBtnEqualizer != null) {
                mBtnEqualizer.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							openEqualizer();
						}
					});
            } else {
                IkLog.e(TAG, "btn_now_equalizer not found in layout!");
            }

            mProgressHandler = new Handler(Looper.getMainLooper());
            mVisualizerHandler = new Handler(Looper.getMainLooper());
            mSettingsManager = SettingsManager.getInstance(mActivity.getApplicationContext());
            mSettingsManager.addVisualizerSettingsListener(this);
            loadSettings();

            mIdleFFTData = new float[64];
            for (int i = 0; i < mIdleFFTData.length; i++) mIdleFFTData[i] = 0.1f;

            mDisplayMode = PrefUtils.getDisplayMode(mActivity);
            mIsShuffle = PrefUtils.getShuffle(mActivity);
            mRepeatMode.setMode(PrefUtils.getRepeatMode(mActivity));

            if (mCoverFlowView != null) {
                mCoverFlowView.setOnTrackChangeListener(new CoverFlowView.OnTrackChangeListener() {
						@Override
						public void onTrackChanged(AudioItem item, int position) {
							try {
								if (mIsServiceBound && mActivity != null) {
									List<AudioItem> queue = mActivity.getActivePlaylist();
									if (queue.isEmpty()) queue = mActivity.retrieveMasterAudioList();
									playAudioItem(item, position, queue);
								}
							} catch (Exception e) {
								IkLog.e(TAG, "Error in cover flow track change", e);
							}
						}
					});
            }

            mSwipeGestureDetector = new GestureDetector(mActivity,
				new GestureDetector.SimpleOnGestureListener() {
					@Override
					public boolean onFling(MotionEvent e1, MotionEvent e2,
										   float vx, float vy) {
						try {
							if (Math.abs(vx) <= Math.abs(vy) || Math.abs(vx) <= 800)
								return false;
							if (vx < 0) {
								if (mCoverFlowView != null
									&& mDisplayMode == DISPLAY_CAROUSEL) {
									mCoverFlowView.moveToRight();
								} else if (mActivity != null && mIsServiceBound) {
									mActivity.playNext();
								}
							} else {
								if (mCoverFlowView != null
									&& mDisplayMode == DISPLAY_CAROUSEL) {
									mCoverFlowView.moveToLeft();
								} else if (mActivity != null && mIsServiceBound) {
									mActivity.playPrevious();
								}
							}
							return true;
						} catch (Exception e) {
							IkLog.e(TAG, "Error in gesture fling", e);
							return false;
						}
					}
				});

            showCategoryGrid();
            mActivity.setActiveFragment(this);

            // ── Back-stack listener (panel restore only, never hides) ────────
            getChildFragmentManager().addOnBackStackChangedListener(
				new FragmentManager.OnBackStackChangedListener() {
					@Override
					public void onBackStackChanged() {
						try {
							if (!mEqualizerOpenedFromPanel) return;

							FragmentManager cfm = getChildFragmentManager();
							boolean equalizerStillPresent = false;
							for (int i = 0; i < cfm.getBackStackEntryCount(); i++) {
								if (BACKSTACK_EQUALIZER.equals(
										cfm.getBackStackEntryAt(i).getName())) {
									equalizerStillPresent = true;
									break;
								}
							}

							if (!equalizerStillPresent) {
								mEqualizerOpenedFromPanel = false;
								IkLog.d(TAG, "Equalizer popped — restoring NowPlayingPanel");
								if (mPanelVisible && mMultiPanel != null) {
									mMultiPanel.showTabs();
									mMultiPanel.expandPanel(0);
									if (mActivity != null) mActivity.hideMainBars();
								}
							}
						} catch (Exception e) {
							IkLog.e(TAG, "Error in backstack listener", e);
						}
					}
				});

            mSensorManager = (SensorManager) mActivity.getSystemService(Context.SENSOR_SERVICE);
            if (mSensorManager != null) {
                mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            }
            mShakeDetector = new ShakeDetector();

            IkLog.d(TAG, "onCreateView completed");
            return view;
        } catch (Exception e) {
            IkLog.e(TAG, "Fatal error in onCreateView", e);
            return new View(getActivity());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            if (mActivity == null) mActivity = (MainActivity) getActivity();
            if (mActivity == null) {
                IkLog.w(TAG, "onResume: Activity still null");
                return;
            }

            mActivity.setActiveFragment(this);

            IntentFilter filter = new IntentFilter();
            filter.addAction(AudioPlayerService.ACTION_PLAYBACK_STARTED);
            filter.addAction(AudioPlayerService.ACTION_PLAYBACK_PAUSED);
            filter.addAction(AudioPlayerService.ACTION_PLAYBACK_RESUMED);
            filter.addAction(AudioPlayerService.ACTION_PLAYBACK_STOPPED);
            filter.addAction(AudioPlayerService.ACTION_PLAYBACK_COMPLETED);
            filter.addAction(AudioPlayerService.ACTION_SPECTRUM_DATA);
            filter.addAction(AudioPlayerService.ACTION_SHUFFLE_CHANGED);
            filter.addAction(AudioPlayerService.ACTION_REPEAT_MODE_CHANGED);
            filter.addAction(AudioPlayerService.ACTION_METADATA_UPDATED);
            filter.addAction(AudioPlayerService.ACTION_PLAYLIST_END_REACHED);

            mPlaybackReceiver = new PlaybackReceiver(this);
            if (Build.VERSION.SDK_INT >= 33) {
                mActivity.registerReceiver(mPlaybackReceiver, filter,
										   Context.RECEIVER_NOT_EXPORTED);
            } else {
                mActivity.registerReceiver(mPlaybackReceiver, filter);
            }

            if (mAudioList == null || mAudioList.isEmpty()) {
                checkAndRequestStoragePermission();
            }
            reloadSettings();

            if (mSensorManager != null && mAccelerometer != null) {
                mSensorManager.registerListener(mShakeDetector, mAccelerometer,
												SensorManager.SENSOR_DELAY_UI);
            }
            IkLog.d(TAG, "onResume completed");
        } catch (Exception e) {
            IkLog.e(TAG, "Error in onResume", e);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            if (mActivity != null && mIsServiceBound) {
                PrefUtils.setLastSeek(mActivity, mActivity.getCurrentInTrackPosition());
            }
            stopProgressUpdates();
            stopVisualizerUpdates();
            if (mPlaybackReceiver != null && getActivity() != null) {
                getActivity().unregisterReceiver(mPlaybackReceiver);
                mPlaybackReceiver = null;
            }
            if (mSensorManager != null) {
                mSensorManager.unregisterListener(mShakeDetector);
            }
            IkLog.d(TAG, "onPause completed");
        } catch (Exception e) {
            IkLog.e(TAG, "Error in onPause", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (mProgressHandler != null) mProgressHandler.removeCallbacksAndMessages(null);
            if (mVisualizerHandler != null) mVisualizerHandler.removeCallbacksAndMessages(null);
            if (mRepeatABHandler != null) mRepeatABHandler.removeCallbacksAndMessages(null);
            if (mSettingsManager != null) mSettingsManager.removeVisualizerSettingsListener(this);
            mProgressHandler = null;
            mVisualizerHandler = null;
            mRepeatABHandler = null;
            mActivity = null;
            IkLog.d(TAG, "onDestroy completed");
        } catch (Exception e) {
            IkLog.e(TAG, "Error in onDestroy", e);
        }
    }

    // =========================================================================
    // Back-Stack
    // =========================================================================

    public boolean handleBackPress() {
        try {
            FragmentManager cfm = getChildFragmentManager();

            if (cfm.getBackStackEntryCount() > 0) {
                try {
                    cfm.popBackStack();
                } catch (IllegalStateException ignored) {
                }
                return true;
            }

            if (mPanelVisible) {
                if (mMultiPanel != null && mMultiPanel.isPanelExpanded(0)) {
                    mMultiPanel.collapseAll();
                    return true;
                }
                hideNowPlayingPanel();
                return true;
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error in handleBackPress", e);
        }
        return false;
    }

    // =========================================================================
    // Init
    // =========================================================================

    private void initNowPlayingPanel(View root) {
        try {
            mMultiPanel = (MultiPanelLayout) root.findViewById(R.id.audio_multi_panel);
            mNowPlayingPanel = (ViewGroup) root.findViewById(R.id.now_playing_panel);
            mAlbumArt = (ImageView) root.findViewById(R.id.now_playing_album_art);
            mCoverFlowView = (CoverFlowView) root.findViewById(R.id.now_playing_coverflow);
            mGlVisualizerView = (GLVisualizerView) root.findViewById(
				R.id.now_playing_glVisualizerView);
            mSwipeLayer = root.findViewById(R.id.now_playing_swipe_layer);
            mSongTitle = (TextView) root.findViewById(R.id.now_playing_title);
            mArtistName = (TextView) root.findViewById(R.id.now_playing_artist);
            mAlbumName = (TextView) root.findViewById(R.id.now_playing_album);
            mHandleTitle = (TextView) root.findViewById(R.id.now_playing_handle_title);
            mHandleArtist = (TextView) root.findViewById(R.id.now_playing_handle_artist);
            mWaveformSeekBar = (WaveformSeekBar) root.findViewById(
				R.id.now_playing_waveform_seekbar);
            mCurrentTime = (TextView) root.findViewById(R.id.now_playing_current_time);
            mTotalTime = (TextView) root.findViewById(R.id.now_playing_total_time);
            mBtnPlayPause = (ImageButton) root.findViewById(R.id.btn_now_play_pause);
            mBtnNext = (ImageButton) root.findViewById(R.id.btn_now_next);
            mBtnPrevious = (ImageButton) root.findViewById(R.id.btn_now_previous);
            mBtnRepeat = (ImageButton) root.findViewById(R.id.btn_now_repeat);
            mBtnShuffle = (ImageButton) root.findViewById(R.id.btn_now_shuffle);
            mBtnFavorite = (ImageButton) root.findViewById(R.id.btn_now_favorite);
            mBtnSwitchVisualizer = (ImageButton) root.findViewById(
				R.id.btn_now_playing_switch_visualizer);
            mBtnVisualizerStyle = (ImageButton) root.findViewById(
				R.id.btn_now_visualizer_style);
            mBtnColorScheme = (ImageButton) root.findViewById(R.id.btn_now_color_scheme);
            btnNowPlayingClose = (ImageButton) root.findViewById(
				R.id.btn_now_playing_back_top);
            mBtnQueue = root.findViewById(R.id.btn_queue);
            mBtnPlaybackSpeed = root.findViewById(R.id.btn_now_playback_speed);
            mBtnSleepTimer    = root.findViewById(R.id.btn_now_sleep_timer);
            mBtnRepeatAB      = root.findViewById(R.id.btn_now_repeat_ab);
            mBtnMoreOptions   = root.findViewById(R.id.btn_now_more);
            mBtnLyrics        = root.findViewById(R.id.btn_now_lyrics);
            mLyricsSection    = root.findViewById(R.id.lyrics_section);
            mLyricsContent    = root.findViewById(R.id.lyrics_content);

            // Wire the MaterialToolbar's navigation icon to hide the panel
            com.google.android.material.appbar.MaterialToolbar toolbar =
				root.findViewById(R.id.now_playing_handle_container);
            if (toolbar != null) {
                toolbar.setNavigationOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							hideNowPlayingPanel();
						}
					});
            }

            if (mSwipeLayer != null) {
                mSwipeLayer.setOnTouchListener(new View.OnTouchListener() {
						@Override
						public boolean onTouch(View v, MotionEvent event) {
							try {
								if (mDisplayMode == DISPLAY_VISUALIZER) return false;
								mSwipeGestureDetector.onTouchEvent(event);
								if (event.getActionMasked() == MotionEvent.ACTION_UP
                                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
									v.performClick();
								}
							} catch (Exception e) {
								IkLog.e(TAG, "Error in swipe touch", e);
							}
							return true;
						}
					});
                mSwipeLayer.setClickable(true);
            }

            if (mWaveformSeekBar != null) {
                mWaveformSeekBar.setOnSeekListener(new WaveformSeekBar.OnSeekListener() {
						@Override
						public void onSeek(long position) {
							try {
								if (mActivity != null && mIsServiceBound) {
									mActivity.seekTo((int) position);
								}
							} catch (Exception e) {
								IkLog.e(TAG, "Error on seek", e);
							}
						}
					});
            }

            if (mBtnQueue != null) {
                mBtnQueue.setOnClickListener(new View.OnClickListener() {
						@Override public void onClick(View v) { showQueueDialog(); }
					});
            }
            if (mBtnLyrics != null) {
                mBtnLyrics.setOnClickListener(new View.OnClickListener() {
						@Override public void onClick(View v) { toggleLyricsVisibility(); }
					});
            }

            setupNowPlayingListeners();

            if (mMultiPanel != null) {
                mMultiPanel.hideTabs();
                mMultiPanel.setFillMode(true);
            }
            applyDisplayMode(mDisplayMode);
        } catch (Exception e) {
            IkLog.e(TAG, "Error initializing now playing panel", e);
        }
    }

    private void setupNowPlayingListeners() {
        try {
            btnNowPlayingClose.setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) { hideNowPlayingPanel(); }
				});

            mBtnPlayPause.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						try {
							if (mActivity == null) return;
							if (!mIsServiceBound) {
								List<AudioItem> list = mActivity.retrieveMasterAudioList();
								if (!list.isEmpty()) playAudioItem(list.get(0), 0, list);
								return;
							}
							if (mActivity.isPlaying()) mActivity.pausePlayback();
							else mActivity.resumePlayback();
						} catch (Exception e) {
							IkLog.e(TAG, "Error in play/pause click", e);
						}
					}
				});

            mBtnNext.setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) {
						try {
							if (mActivity != null && mIsServiceBound) mActivity.playNext();
							else showToast("No track playing");
						} catch (Exception e) {
							IkLog.e(TAG, "Error in next click", e);
						}
					}
				});

            mBtnPrevious.setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) {
						try {
							if (mActivity != null && mIsServiceBound) mActivity.playPrevious();
							else showToast("No track playing");
						} catch (Exception e) {
							IkLog.e(TAG, "Error in previous click", e);
						}
					}
				});

            mBtnRepeat.setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) { toggleRepeat(); }
				});
            mBtnShuffle.setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) { toggleShuffle(); }
				});
            mBtnFavorite.setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) { toggleFavorite(); }
				});
            mBtnSwitchVisualizer.setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) { cycleDisplayMode(); }
				});
            mBtnVisualizerStyle.setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) { cycleVisualizerStyle(); }
				});
            mBtnColorScheme.setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) { cycleColorScheme(); }
				});

            if (mBtnPlaybackSpeed != null) {
                mBtnPlaybackSpeed.setOnClickListener(new View.OnClickListener() {
						@Override public void onClick(View v) { showPlaybackSpeedDialog(); }
					});
            }
            if (mBtnSleepTimer != null) {
                mBtnSleepTimer.setOnClickListener(new View.OnClickListener() {
						@Override public void onClick(View v) { showSleepTimerDialog(); }
					});
            }
            if (mBtnRepeatAB != null) {
                mBtnRepeatAB.setOnClickListener(new View.OnClickListener() {
						@Override public void onClick(View v) { toggleRepeatAB(); }
					});
            }
            if (mBtnMoreOptions != null) {
                mBtnMoreOptions.setOnClickListener(new View.OnClickListener() {
						@Override public void onClick(View v) { showMoreOptionsDialog(); }
					});
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error setting up now playing listeners", e);
        }
    }

    // ── Feature dialogs ─────────────────────────────────────────────────────

    private void showMoreOptionsDialog() {
        try {
            if (mActivity == null) return;
            int bgColor = mCurrentSwatch != null
				? mCurrentSwatch.darkVibrant
				: ContextCompat.getColor(mActivity, R.color.bronze_start);
            String[] options = {
                "Equalizer", "Playback Speed", "Sleep Timer",
                "A‑B Repeat", "Lyrics", "Edit Metadata", "Cancel"
            };
            new IkBeautifulDialog(mActivity)
				.setBackgroundColor(bgColor)
				.setMessage("More Options")
				.setItems(options, new IkBeautifulDialog.OnItemClickListener() {
					@Override
					public void onItemClick(int position, String item) {
						if      (position == 0) openEqualizer();
						else if (position == 1) showPlaybackSpeedDialog();
						else if (position == 2) showSleepTimerDialog();
						else if (position == 3) toggleRepeatAB();
						else if (position == 4) toggleLyricsVisibility();
						else if (position == 5) openMetadataEditor(null);
					}
				})
				.showList();
        } catch (Exception e) {
            IkLog.e(TAG, "Error showing more options dialog", e);
        }
    }

    private void openEqualizer() {
        try {
            if (mActivity == null) {
                IkLog.e(TAG, "Equalizer: mActivity is null");
                return;
            }

            Fragment existing = getChildFragmentManager()
				.findFragmentByTag(BACKSTACK_EQUALIZER);
            if (existing != null && existing.isAdded()) {
                IkLog.d(TAG, "Equalizer already open, ignoring");
                return;
            }

            if (mPanelVisible) {
                mEqualizerOpenedFromPanel = true;
                if (mMultiPanel != null) {
                    mMultiPanel.collapseAll();
                    mMultiPanel.hideTabs();
                }
                mActivity.showMainBars();
                IkLog.d(TAG, "NowPlayingPanel collapsed temporarily for equalizer");
            }

            EqualizerFragment eqFragment = new EqualizerFragment();
            getChildFragmentManager()
				.beginTransaction()
				.replace(R.id.content_frame, eqFragment, BACKSTACK_EQUALIZER)
				.addToBackStack(BACKSTACK_EQUALIZER)
				.commit();
            IkLog.d(TAG, "EqualizerFragment opened from dialog");
        } catch (Exception e) {
            IkLog.e(TAG, "Error opening EqualizerFragment from dialog", e);
        }
    }

    public void openMetadataEditor(AudioItem item) {
        try {
            if (mActivity == null || mCurrentPlayingUri == null) {
                showToast("No track playing");
                return;
            }
            MetadataEditorFragment metaFrag = new MetadataEditorFragment();
            getChildFragmentManager()
				.beginTransaction()
				.replace(R.id.content_frame, metaFrag)
				.addToBackStack(null)
				.commit();
				if(item != null) metaFrag.setAudioData(item);
            IkLog.d(TAG, "MetadataEditorFragment opened");
        } catch (Exception e) {
            IkLog.e(TAG, "Error opening MetadataEditorFragment", e);
        }
    }

    private void showPlaybackSpeedDialog() {
        try {
            if (mActivity == null) return;
            int bgColor = mCurrentSwatch != null
				? mCurrentSwatch.darkVibrant
				: ContextCompat.getColor(mActivity, R.color.bronze_start);
            final float[] speeds = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
            String[] labels = new String[speeds.length];
            for (int i = 0; i < speeds.length; i++) {
                labels[i] = String.format("%.2fx", speeds[i]);
            }
            new IkBeautifulDialog(mActivity)
				.setBackgroundColor(bgColor)
				.setMessage("Playback Speed")
				.setItems(labels, new IkBeautifulDialog.OnItemClickListener() {
					@Override
					public void onItemClick(int position, String label) {
						if (mActivity != null) {
							mActivity.setPlaybackSpeed(speeds[position]);
							showToast("Speed: " + label);
						}
					}
				})
				.showList();
        } catch (Exception e) {
            IkLog.e(TAG, "Error showing playback speed dialog", e);
        }
    }

    private void showSleepTimerDialog() {
        try {
            if (mActivity == null) return;
            int bgColor = mCurrentSwatch != null
				? mCurrentSwatch.darkVibrant
				: ContextCompat.getColor(mActivity, R.color.bronze_start);
            String[] options = {
                "15 minutes", "30 minutes", "45 minutes", "60 minutes", "Cancel"
            };
            new IkBeautifulDialog(mActivity)
				.setBackgroundColor(bgColor)
				.setMessage("Sleep Timer")
				.setItems(options, new IkBeautifulDialog.OnItemClickListener() {
					@Override
					public void onItemClick(int position, String item) {
						if (position < 4) {
							long ms = (position + 1) * 15 * 60 * 1000L;
							if (mActivity != null) {
								mActivity.setSleepTimer(ms);
								showToast("Sleep timer set for " + item);
							}
						}
					}
				})
				.showList();
        } catch (Exception e) {
            IkLog.e(TAG, "Error showing sleep timer dialog", e);
        }
    }

    // ── A-B Repeat ──────────────────────────────────────────────────────────

    private void toggleRepeatAB() {
        try {
            if (mCurrentPlayingUri == null || mActivity == null) {
                showToast("No track playing");
                return;
            }
            if (!mIsRepeatAB) {
                mRepeatAPos = mActivity.getCurrentInTrackPosition();
                mRepeatBPos = -1;
                mIsRepeatAB = true;
                showToast("A point set. Tap again for B.");
                IkLog.d(TAG, "A‑B repeat: point A set at " + mRepeatAPos);
            } else if (mRepeatBPos == -1) {
                mRepeatBPos = mActivity.getCurrentInTrackPosition();
                if (mRepeatBPos <= mRepeatAPos) {
                    showToast("B must be after A. A‑B repeat cancelled.");
                    cancelRepeatABLoop();
                    return;
                }
                startRepeatABLoop();
                showToast("A‑B repeat active. Tap again to cancel.");
                IkLog.d(TAG, "A‑B repeat: loop started A=" + mRepeatAPos
                        + " B=" + mRepeatBPos);
            } else {
                cancelRepeatABLoop();
                showToast("A‑B repeat cancelled.");
                IkLog.d(TAG, "A‑B repeat: cancelled by user");
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error toggling A‑B repeat", e);
        }
    }

    private void startRepeatABLoop() {
        if (mRepeatABHandler != null && mRepeatABRunnable != null) {
            mRepeatABHandler.removeCallbacks(mRepeatABRunnable);
        }
        mRepeatABHandler = new Handler(Looper.getMainLooper());
        mRepeatABRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    if (!mIsRepeatAB || mActivity == null || !mIsServiceBound) return;
                    if (!mActivity.isPlaying()) {
                        if (mRepeatABHandler != null)
                            mRepeatABHandler.postDelayed(this, 200);
                        return;
                    }
                    int pos = mActivity.getCurrentInTrackPosition();
                    if (pos >= mRepeatBPos || pos < mRepeatAPos) {
                        mActivity.seekTo(mRepeatAPos);
                        IkLog.d(TAG, "A‑B loop: seeked to A=" + mRepeatAPos
                                + " from pos=" + pos);
                    }
                    if (mRepeatABHandler != null)
                        mRepeatABHandler.postDelayed(this, 200);
                } catch (Exception e) {
                    IkLog.e(TAG, "Error in A‑B repeat loop", e);
                }
            }
        };
        mRepeatABHandler.post(mRepeatABRunnable);
        IkLog.d(TAG, "A‑B repeat loop handler started");
    }

    private void cancelRepeatABLoop() {
        try {
            mIsRepeatAB = false;
            mRepeatAPos = -1;
            mRepeatBPos = -1;
            if (mRepeatABHandler != null && mRepeatABRunnable != null) {
                mRepeatABHandler.removeCallbacks(mRepeatABRunnable);
            }
            mRepeatABHandler = null;
            mRepeatABRunnable = null;
            IkLog.d(TAG, "A‑B repeat loop cancelled");
        } catch (Exception e) {
            IkLog.e(TAG, "Error cancelling A‑B repeat loop", e);
        }
    }

    // ── Lyrics ──────────────────────────────────────────────────────────────

    private void toggleLyricsVisibility() {
        try {
            if (mCurrentPlayingUri == null) {
                showToast("No track playing");
                return;
            }
            mLyricsVisible = !mLyricsVisible;
            if (mLyricsSection != null) {
                mLyricsSection.setVisibility(mLyricsVisible ? View.VISIBLE : View.GONE);
            }
            if (mLyricsVisible) {
                fetchAndShowLyrics();
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error toggling lyrics visibility", e);
        }
    }

    private void fetchAndShowLyrics() {
        if (mLyricsContent == null || mCurrentPlayingUri == null || mActivity == null) return;
        mLyricsContent.setText("Loading lyrics…");
        IkLog.d(TAG, "Fetching lyrics for " + mCurrentPlayingUri);

        final Uri currentUri = mCurrentPlayingUri;
        final String currentPath = mCurrentDisplayPath;
        final Context ctx = mActivity.getApplicationContext();

        new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						String lyrics = LyricsHelper.getEmbeddedLyrics(ctx, currentUri);
						if (lyrics == null) {
							if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
								lyrics = LyricsHelper.getExternalLyricsForUri(ctx, currentUri);
							} else {
								if (currentPath != null) {
									lyrics = LyricsHelper.getExternalLyrics(currentPath);
								}
							}
						}
						final String finalLyrics = lyrics;
						new Handler(Looper.getMainLooper()).post(new Runnable() {
								@Override
								public void run() {
									try {
										if (!isAdded()) return;
										if (mLyricsContent != null) {
											if (finalLyrics != null
												&& !finalLyrics.trim().isEmpty()) {
												mLyricsContent.setText(finalLyrics);
											} else {
												mLyricsContent.setText("No lyrics found.\n\n");
											}
										}
									} catch (Exception e) {
										IkLog.e(TAG, "Error displaying lyrics", e);
									}
								}
							});
					} catch (Exception e) {
						IkLog.e(TAG, "Error fetching lyrics", e);
					}
				}
			}, "lyrics-loader").start();
    }

    // ── Shake detector ──────────────────────────────────────────────────────

    private class ShakeDetector implements SensorEventListener {
        private static final float SHAKE_THRESHOLD = 2.5f;
        private static final int SHAKE_SLOP_TIME_MS = 500;
        private long mShakeTimestamp;
        private float lastX, lastY, lastZ;

        @Override
        public void onSensorChanged(SensorEvent event) {
            try {
                float x = event.values[0], y = event.values[1], z = event.values[2];
                float deltaX = Math.abs(x - lastX);
                float deltaY = Math.abs(y - lastY);
                float deltaZ = Math.abs(z - lastZ);
                float acceleration = (float) Math.sqrt(
					deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
                long now = System.currentTimeMillis();
                if (acceleration > SHAKE_THRESHOLD) {
                    if (now - mShakeTimestamp > SHAKE_SLOP_TIME_MS) {
                        mShakeTimestamp = now;
                        if (mIsServiceBound) {
                            // Sensor too sensitive, disabled for now.
                        }
                    }
                }
                lastX = x; lastY = y; lastZ = z;
            } catch (Exception e) {
                IkLog.e(TAG, "Error in shake detector", e);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    }

    // =========================================================================
    // Display mode
    // =========================================================================

    private void cycleDisplayMode() {
        try {
            int next = (mDisplayMode + 1) % 3;
            applyDisplayMode(next);
            if (mActivity != null) PrefUtils.setDisplayMode(mActivity, next);
            IkLog.d(TAG, "Display mode cycled to: " + next);
        } catch (Exception e) {
            IkLog.e(TAG, "Error cycling display mode", e);
        }
    }

    private void applyDisplayMode(int mode) {
        try {
            mDisplayMode = mode;
            if (mAlbumArt != null) mAlbumArt.setVisibility(View.GONE);
            if (mCoverFlowView != null) mCoverFlowView.setVisibility(View.GONE);
            if (mGlVisualizerView != null) mGlVisualizerView.setVisibility(View.GONE);

            switch (mode) {
                case DISPLAY_ALBUM_ART:
                    if (mAlbumArt != null) mAlbumArt.setVisibility(View.VISIBLE);
                    if (mSwipeLayer != null) mSwipeLayer.setVisibility(View.VISIBLE);
                    if (mBtnSwitchVisualizer != null)
                        mBtnSwitchVisualizer.setImageResource(R.drawable.ic_album_art);
                    stopVisualizerUpdates();
                    break;

                case DISPLAY_CAROUSEL:
                    if (mCoverFlowView != null) {
                        mCoverFlowView.setVisibility(View.VISIBLE);
                        List<AudioItem> queue = (mActivity != null)
							? mActivity.getActivePlaylist()
							: new ArrayList<AudioItem>();
                        if (!queue.isEmpty())
                            mCoverFlowView.setItems(queue, mCurrentPlayingPosition);
                    }
                    if (mSwipeLayer != null) mSwipeLayer.setVisibility(View.GONE);
                    if (mBtnSwitchVisualizer != null)
                        mBtnSwitchVisualizer.setImageResource(R.drawable.ic_carousel);
                    stopVisualizerUpdates();
                    break;

                case DISPLAY_VISUALIZER:
                    if (mGlVisualizerView != null) {
                        mGlVisualizerView.setVisibility(View.VISIBLE);
                        mGlVisualizerView.onResume();
                    }
                    if (mSwipeLayer != null) mSwipeLayer.setVisibility(View.GONE);
                    if (mBtnSwitchVisualizer != null)
                        mBtnSwitchVisualizer.setImageResource(R.drawable.ic_waveform);
                    if (mIsVisualizerActive) startVisualizerUpdates();
                    else startIdleAnimation();
                    break;
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error applying display mode", e);
        }
    }

    // =========================================================================
    // Panel show / hide
    // =========================================================================

    protected void showNowPlayingPanel(boolean animate) {
        try {
            if (mPanelVisible) return;
            mPanelVisible = true;
            if (mMultiPanel != null) {
                mMultiPanel.showTabs();
                mMultiPanel.expandPanel(0);
            }
            if (mActivity != null) mActivity.hideMainBars();
            IkLog.d(TAG, "Now playing panel shown");
        } catch (Exception e) {
            IkLog.e(TAG, "Error showing now playing panel", e);
        }
    }

    protected void hideNowPlayingPanel() {
        try {
            if (!mPanelVisible) return;
            mPanelVisible = false;
            mPanelSuppressed = true;
            if (mMultiPanel != null) {
                mMultiPanel.collapseAll();
                mMultiPanel.hideTabs();
            }
            stopIdleAnimation();
            stopProgressUpdates();
            if (mActivity != null) mActivity.showMainBars();
            IkLog.d(TAG, "Now playing panel hidden");
        } catch (Exception e) {
            IkLog.e(TAG, "Error hiding now playing panel", e);
        }
    }

    public boolean isNowPlayingPanelVisible() { return mPanelVisible; }

    // =========================================================================
    // Service availability
    // =========================================================================

    public void onServiceAvailable(boolean connected) {
        try {
            if (mActivity == null) return;
            mIsServiceBound = connected;
            IkLog.d(TAG, "Service available: " + connected);
            Uri currentUri = mActivity.getCurrentUri();
            if (currentUri != null) {
                updateNowPlayingFromUri(currentUri);
                updatePlayButton();
                if (mActivity.isPlaying() && !mPanelSuppressed) {
                    startVisualizerUpdates();
                    showNowPlayingPanel(false);
                }
            }
            mIsShuffle = mActivity.isShuffle();
            mRepeatMode.setMode(mActivity.getRepeatMode());
            updateShuffleButton();
            updateRepeatButton();
        } catch (Exception e) {
            IkLog.e(TAG, "Error in onServiceAvailable", e);
        }
    }

    // =========================================================================
    // Navigation
    // =========================================================================

    private void showCategoryGrid() {
        try {
            if (!isAdded()) return;
            LibraryCategoryFragment frag = new LibraryCategoryFragment();
            getChildFragmentManager()
				.beginTransaction()
				.replace(R.id.content_frame, frag)
				.commitAllowingStateLoss();
            IkLog.d(TAG, "Category grid shown");
        } catch (Exception e) {
            IkLog.e(TAG, "Error showing category grid", e);
        }
    }

    @Override
    public void onCategorySelected(Category category) {
        try {
            if (!isAdded()) return;
            TrackListFragment frag = TrackListFragment.newInstance(
				category.getName(), category.getItems());
            getChildFragmentManager()
				.beginTransaction()
				.setCustomAnimations(
				R.anim.fragment_slide_in_right,
				R.anim.fragment_slide_out_left,
				R.anim.fragment_slide_in_left,
				R.anim.fragment_slide_out_right)
				.replace(R.id.content_frame, frag)
				.addToBackStack("tracklist")
				.commitAllowingStateLoss();
            IkLog.d(TAG, "Category selected: " + category.getName());
        } catch (Exception e) {
            IkLog.e(TAG, "Error on category selected", e);
        }
    }

    @Override
    public void onTrackSelected(AudioItem item, int position, List<AudioItem> categoryList) {
        try {
            playAudioItem(item, position, categoryList);
        } catch (Exception e) {
            IkLog.e(TAG, "Error on track selected", e);
        }
    }

    @Override
    public boolean onTrackLongClick(AudioItem item, int position) { return false; }

    // =========================================================================
    // Playback
    // =========================================================================

    private void playAudioItem(final AudioItem item, final int position,
                               final List<AudioItem> categoryList) {
        try {
            if (mActivity == null || item == null || !mIsServiceBound) {
                IkLog.w(TAG, "playAudioItem: null activity, item, or service not bound");
                return;
            }
            if (categoryList == null || categoryList.isEmpty()) return;

            int indexInCategory = findPositionInList(categoryList, item.getUri());
            if (indexInCategory == -1) indexInCategory = position;

            mCurrentPlayingUri = item.getUri();
            mCurrentDisplayPath = item.getFilePath();
            mCurrentPlayingPosition = indexInCategory;
            updateNowPlayingFromItem(item);

            mPanelSuppressed = false;
            showNowPlayingPanel(true);

            final List<AudioItem> queue = new ArrayList<>(categoryList);
            if (mIsServiceBound) {
                mActivity.setPlaylist(queue);
                mActivity.playUri(item.getUri(), indexInCategory);
            } else {
                mActivity.launchService(item.getUri(), indexInCategory, queue);
            }

            PrefUtils.setLastUri(mActivity, item.getUri().toString());
            PrefUtils.setLastPosition(mActivity, indexInCategory);

            startProgressUpdates();
            if (mDisplayMode == DISPLAY_CAROUSEL && mCoverFlowView != null) {
                mCoverFlowView.setItems(queue, indexInCategory);
            }

            cancelRepeatABLoop();

            if (mLyricsVisible) {
                fetchAndShowLyrics();
            }
            IkLog.d(TAG, "Playing: " + item.getTitle());
        } catch (Exception e) {
            IkLog.e(TAG, "Error playing audio item", e);
        }
    }

    // =========================================================================
    // Now-Playing UI
    // =========================================================================

    private void updateNowPlayingFromItem(final AudioItem item) {
        try {
            if (item == null) { resetNowPlayingInfo(); return; }
            if (mSongTitle != null)  mSongTitle.setText(item.getTitle());
            if (mArtistName != null) mArtistName.setText(item.getArtist());
            if (mAlbumName != null)  mAlbumName.setText(
                    item.getAlbum() != null ? item.getAlbum() : "");
            if (mHandleTitle != null)  mHandleTitle.setText(item.getTitle());
            if (mHandleArtist != null) mHandleArtist.setText(item.getArtist());

            int duration = (mActivity != null && mIsServiceBound)
				? mActivity.getDuration() : (int) item.getDuration();
            if (duration <= 0) duration = (int) item.getDuration();
            if (mWaveformSeekBar != null) mWaveformSeekBar.setDuration(duration);
            if (mTotalTime != null) mTotalTime.setText(formatTime(duration));
            if (mWaveformSeekBar != null) mWaveformSeekBar.setPosition(0);
            if (mCurrentTime != null) mCurrentTime.setText("0:00");

            mIsFavorite = item.isFavorite();
            updateFavoriteButton();

            byte[] artBytes = item.getAlbumArtBytes();
            if (artBytes != null && artBytes.length > 0) {
                Bitmap bmp = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.length);
                if (bmp != null) {
                    if (mAlbumArt != null) mAlbumArt.setImageBitmap(bmp);
                    applyDynamicColors(bmp);
                    return;
                }
            }
            if (mAlbumArt != null && mActivity != null) {
                Bitmap defaultArt = MediaUtils.getDefaultAlbumArt(mActivity);
                mAlbumArt.setImageBitmap(defaultArt);
                applyDynamicColors(defaultArt);
            }
            loadAlbumArtAsync(item.getUri());
        } catch (Exception e) {
            IkLog.e(TAG, "Error updating now playing from item", e);
        }
    }

    private void updateNowPlayingFromUri(Uri uri) {
        try {
            mCurrentPlayingUri = uri;
            if (uri == null) {
                mCurrentPlayingPosition = -1;
                resetNowPlayingInfo();
                return;
            }

            List<AudioItem> serviceList = (mActivity != null)
				? mActivity.getPlaylist() : null;
            int pos = findPositionInList(serviceList, uri);
            AudioItem item = (pos != -1 && serviceList != null)
				? serviceList.get(pos) : null;

            if (item == null) {
                List<AudioItem> masterList = (mActivity != null)
					? mActivity.retrieveMasterAudioList() : null;
                int masterPos = findPositionInList(masterList, uri);
                if (masterPos != -1 && masterList != null) item = masterList.get(masterPos);
            }

            mCurrentPlayingPosition = (pos != -1) ? pos
				: findPositionInList(
				mActivity != null
				? mActivity.retrieveMasterAudioList() : null, uri);

            if (item != null) {
                updateNowPlayingFromItem(item);
            } else {
                if (mSongTitle != null)  mSongTitle.setText("Unknown Track");
                if (mArtistName != null) mArtistName.setText("");
                if (mAlbumName != null)  mAlbumName.setText("");
                if (mAlbumArt != null && mActivity != null) {
                    mAlbumArt.setImageBitmap(MediaUtils.getDefaultAlbumArt(mActivity));
                }
                loadAlbumArtAsync(uri);
            }
            updatePlayButton();

            if (mDisplayMode == DISPLAY_CAROUSEL && mCoverFlowView != null) {
                mCoverFlowView.setCurrentIndex(mCurrentPlayingPosition);
            }
            if (mLyricsVisible) {
                fetchAndShowLyrics();
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error updating now playing from URI", e);
        }
    }

    private void loadAlbumArtAsync(final Uri uri) {
        try {
            if (mActivity == null) return;
            MediaUtils.getAudioAlbumArtBitmapAsync(uri, mActivity,
				new Handler(Looper.getMainLooper()),
				new MediaUtils.AlbumArtCallback() {
					@Override public void onAlbumArtLoaded(Uri u, Bitmap art) {
						try {
							if (!isAdded() || mAlbumArt == null) return;
							if (u.equals(mCurrentPlayingUri)) {
								mAlbumArt.setImageBitmap(art);
								applyDynamicColors(art);
							}
						} catch (Exception e) {
							IkLog.e(TAG, "Error in album art callback", e);
						}
					}
				});
        } catch (Exception e) {
            IkLog.e(TAG, "Error loading album art async", e);
        }
    }

    // =========================================================================
    // Dynamic colors
    // =========================================================================

    private void applyDynamicColors(Bitmap bitmap) {
        try {
            if (bitmap == null) return;

            Bitmap scaled = Bitmap.createScaledBitmap(bitmap,
													  Math.min(bitmap.getWidth(), 64),
													  Math.min(bitmap.getHeight(), 64), true);

            mCurrentSwatch = ColorExtractor.from(scaled);

            if (mNowPlayingPanel != null)
                mNowPlayingPanel.setBackgroundColor(mCurrentSwatch.darkVibrant);
            if (mWaveformSeekBar != null)
                mWaveformSeekBar.setPlayedColor(mCurrentSwatch.vibrant);
            if (mSongTitle != null)  mSongTitle.setTextColor(mCurrentSwatch.titleTextColor);
            if (mArtistName != null) mArtistName.setTextColor(mCurrentSwatch.bodyTextColor);
            if (mAlbumName != null)  mAlbumName.setTextColor(mCurrentSwatch.bodyTextColor);
            if (mCurrentTime != null) mCurrentTime.setTextColor(mCurrentSwatch.bodyTextColor);
            if (mTotalTime != null)   mTotalTime.setTextColor(mCurrentSwatch.bodyTextColor);

            if (scaled != bitmap && !scaled.isRecycled()) scaled.recycle();
        } catch (Exception e) {
            IkLog.e(TAG, "Error applying dynamic colors", e);
        }
    }

    private void resetNowPlayingInfo() {
        try {
            if (mSongTitle != null)  mSongTitle.setText("No Track Playing");
            if (mArtistName != null) mArtistName.setText("");
            if (mAlbumName != null)  mAlbumName.setText("");
            if (mAlbumArt != null && mActivity != null) {
                mAlbumArt.setImageBitmap(MediaUtils.getDefaultAlbumArt(mActivity));
            }
            if (mWaveformSeekBar != null) mWaveformSeekBar.setPosition(0);
            if (mCurrentTime != null) mCurrentTime.setText("0:00");
            if (mTotalTime != null)   mTotalTime.setText("0:00");
            mIsFavorite = false;
            updateFavoriteButton();
            if (mNowPlayingPanel != null)
                mNowPlayingPanel.setBackgroundColor(0xFF005C63);
        } catch (Exception e) {
            IkLog.e(TAG, "Error resetting now playing info", e);
        }
    }

    // =========================================================================
    // Progress updates
    // =========================================================================

    private void updatePlayButton() {
        try {
            if (mBtnPlayPause == null) return;
            boolean playing = (mActivity != null && mIsServiceBound
				&& mActivity.isPlaying());
            mBtnPlayPause.setImageResource(
				playing ? R.drawable.ic_pause_circle : R.drawable.ic_play_circle);
            if (playing) {
                startProgressUpdates();
                if (mDisplayMode == DISPLAY_VISUALIZER && mIsVisualizerActive) {
                    startVisualizerUpdates();
                }
            } else {
                stopProgressUpdates();
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error updating play button", e);
        }
    }

    private void handleBroadcast(Intent intent) {
        try {
            if (intent == null || intent.getAction() == null) return;
            String action = intent.getAction();

            if (action.equals(AudioPlayerService.ACTION_PLAYBACK_STARTED)) {
                String uriStr = intent.getStringExtra(AudioPlayerService.EXTRA_URI);
                if (uriStr != null) updateNowPlayingFromUri(Uri.parse(uriStr));
                updatePlayButton();
                if (!mPanelSuppressed) showNowPlayingPanel(true);
                startProgressUpdates();
                if (mDisplayMode == DISPLAY_VISUALIZER) startVisualizerUpdates();

            } else if (action.equals(AudioPlayerService.ACTION_PLAYBACK_PAUSED)) {
                updatePlayButton();
                pauseVisualizerUpdates();

            } else if (action.equals(AudioPlayerService.ACTION_PLAYBACK_RESUMED)) {
                updatePlayButton();
                startProgressUpdates();
                if (mDisplayMode == DISPLAY_VISUALIZER) startVisualizerUpdates();

            } else if (action.equals(AudioPlayerService.ACTION_PLAYBACK_STOPPED)
					   || action.equals(AudioPlayerService.ACTION_PLAYBACK_COMPLETED)) {
                updatePlayButton();
                stopProgressUpdates();
                stopVisualizerUpdates();
                if (action.equals(AudioPlayerService.ACTION_PLAYBACK_STOPPED)) {
                    mCurrentPlayingUri = null;
                    mCurrentDisplayPath = null;
                    mCurrentPlayingPosition = -1;
                    if (mPanelVisible && mActivity != null && !mActivity.isPlaying()) {
                        resetNowPlayingInfo();
                    }
                }

            } else if (action.equals(AudioPlayerService.ACTION_METADATA_UPDATED)) {
                String uriStr = intent.getStringExtra(AudioPlayerService.EXTRA_URI);
                if (uriStr != null
					&& Uri.parse(uriStr).equals(mCurrentPlayingUri)
					&& mActivity != null) {
                    Bitmap serviceArt = mActivity.getCurrentAlbumArt();
                    if (serviceArt != null && mAlbumArt != null) {
                        mAlbumArt.setImageBitmap(serviceArt);
                        applyDynamicColors(serviceArt);
                    }
                    updateNowPlayingFromUri(Uri.parse(uriStr));
                }

            } else if (action.equals(AudioPlayerService.ACTION_SPECTRUM_DATA)) {
                float[] data = intent.getFloatArrayExtra(
					AudioPlayerService.EXTRA_SPECTRUM_DATA);
                if (data != null && mIsVisualizerActive) {
                    if (mGlVisualizerView != null)
                        mGlVisualizerView.updateAudioData(data);
                    if (mWaveformSeekBar != null)
                        mWaveformSeekBar.setWaveformData(data);
                }

            } else if (action.equals(AudioPlayerService.ACTION_SHUFFLE_CHANGED)) {
                mIsShuffle = intent.getBooleanExtra(
					AudioPlayerService.EXTRA_SHUFFLE, mIsShuffle);
                updateShuffleButton();

            } else if (action.equals(AudioPlayerService.ACTION_REPEAT_MODE_CHANGED)) {
                int mode = intent.getIntExtra(AudioPlayerService.EXTRA_REPEAT_MODE,
											  mRepeatMode.getMode());
                mRepeatMode.setMode(mode);
                updateRepeatButton();

            } else if (action.equals(AudioPlayerService.ACTION_PLAYLIST_END_REACHED)) {
                showToast("End of playlist reached!");
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error handling broadcast: " + intent.getAction(), e);
        }
    }

    // =========================================================================
    // Visualizer updates
    // =========================================================================

    private void startVisualizerUpdates() {
        try {
            stopVisualizerUpdates();
            mVisualizerRunnable = new Runnable() {
                @Override public void run() {
                    long now = System.currentTimeMillis();
                    if (now - mLastVisualizerUpdate >= mVisualizerUpdateInterval) {
                        mLastVisualizerUpdate = now;
                        if (mGlVisualizerView != null && mIsVisualizerActive)
                            mGlVisualizerView.requestRender();
                    }
                    if (mVisualizerHandler != null)
                        mVisualizerHandler.postDelayed(this, mVisualizerUpdateInterval);
                }
            };
            if (mVisualizerHandler != null)
                mVisualizerHandler.post(mVisualizerRunnable);
        } catch (Exception e) {
            IkLog.e(TAG, "Error starting visualizer", e);
        }
    }

    private void pauseVisualizerUpdates() { stopVisualizerUpdates(); }

    private void stopVisualizerUpdates() {
        try {
            if (mVisualizerRunnable != null && mVisualizerHandler != null) {
                mVisualizerHandler.removeCallbacks(mVisualizerRunnable);
                mVisualizerRunnable = null;
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error stopping visualizer", e);
        }
    }

    private void startIdleAnimation() {
        try {
            stopIdleAnimation();
            mVisualizerRunnable = new Runnable() {
                private float phase = 0f;
                @Override public void run() {
                    phase += 0.1f;
                    if (phase > (float) (Math.PI * 2)) phase = 0f;
                    for (int i = 0; i < mIdleFFTData.length; i++) {
                        float pos = (float) i / mIdleFFTData.length;
                        mIdleFFTData[i] = 0.1f
							+ 0.1f * (float) Math.sin(pos * 8f + phase);
                    }
                    if (mGlVisualizerView != null && mIsVisualizerActive) {
                        mGlVisualizerView.updateAudioData(mIdleFFTData);
                        mGlVisualizerView.requestRender();
                    }
                    if (mVisualizerHandler != null)
                        mVisualizerHandler.postDelayed(this, 200);
                }
            };
            mVisualizerHandler.post(mVisualizerRunnable);
        } catch (Exception e) {
            IkLog.e(TAG, "Error starting idle animation", e);
        }
    }

    private void stopIdleAnimation() { stopVisualizerUpdates(); }

    // =========================================================================
    // Progress updates
    // =========================================================================

    private void startProgressUpdates() {
        try {
            stopProgressUpdates();
            mProgressRunnable = new Runnable() {
                @Override public void run() {
                    if (mProgressHandler == null) return;
                    if (mActivity != null && mIsServiceBound
						&& mPanelVisible && !mIsSeeking) {
                        int pos = mActivity.getCurrentInTrackPosition();
                        int dur = mActivity.getDuration();
                        if (dur > 0) {
                            if (mWaveformSeekBar != null) {
                                mWaveformSeekBar.setDuration(dur);
                                mWaveformSeekBar.setPosition(pos);
                            }
                            if (mCurrentTime != null) mCurrentTime.setText(formatTime(pos));
                            if (mTotalTime != null)   mTotalTime.setText(formatTime(dur));
                        }
                    }
                    if (mProgressHandler != null)
                        mProgressHandler.postDelayed(this, 500);
                }
            };
            mProgressHandler.post(mProgressRunnable);
        } catch (Exception e) {
            IkLog.e(TAG, "Error starting progress updates", e);
        }
    }

    private void stopProgressUpdates() {
        try {
            if (mProgressRunnable != null && mProgressHandler != null) {
                mProgressHandler.removeCallbacks(mProgressRunnable);
                mProgressRunnable = null;
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error stopping progress updates", e);
        }
    }

    // =========================================================================
    // Toggles
    // =========================================================================

    private void toggleRepeat() {
        try {
            int current = mRepeatMode.getMode(), next;
            String label;
            if (current == RepeatMode.REPEAT_MODE_NONE) {
                next = RepeatMode.REPEAT_MODE_ALL; label = "Repeat All";
            } else if (current == RepeatMode.REPEAT_MODE_ALL) {
                next = RepeatMode.REPEAT_MODE_ONE; label = "Repeat One";
            } else {
                next = RepeatMode.REPEAT_MODE_NONE; label = "Repeat Off";
            }
            mRepeatMode.setMode(next);
            updateRepeatButton();
            if (mActivity != null) mActivity.setRepeatMode(mRepeatMode);
            PrefUtils.setRepeatMode(mActivity, next);
            showToast(label);
        } catch (Exception e) {
            IkLog.e(TAG, "Error toggling repeat", e);
        }
    }

    private void updateRepeatButton() {
        try {
            if (mBtnRepeat == null) return;
            int mode = mRepeatMode.getMode();
            if (mode == RepeatMode.REPEAT_MODE_ALL)
                mBtnRepeat.setImageResource(R.drawable.ic_repeat_all);
            else if (mode == RepeatMode.REPEAT_MODE_ONE)
                mBtnRepeat.setImageResource(R.drawable.ic_repeat_one);
            else
                mBtnRepeat.setImageResource(R.drawable.ic_repeat_none);
        } catch (Exception e) {
            IkLog.e(TAG, "Error updating repeat button", e);
        }
    }

    private void toggleShuffle() {
        try {
            mIsShuffle = !mIsShuffle;
            updateShuffleButton();
            if (mActivity != null) mActivity.setShuffle(mIsShuffle);
            PrefUtils.setShuffle(mActivity, mIsShuffle);
            showToast(mIsShuffle ? "Shuffle on" : "Shuffle off");
        } catch (Exception e) {
            IkLog.e(TAG, "Error toggling shuffle", e);
        }
    }

    private void updateShuffleButton() {
        try {
            if (mBtnShuffle == null) return;
            mBtnShuffle.setImageResource(
				mIsShuffle ? R.drawable.ic_shuffle_on : R.drawable.ic_shuffle_off);
        } catch (Exception e) {
            IkLog.e(TAG, "Error updating shuffle button", e);
        }
    }

    private void toggleFavorite() {
        try {
            if (mCurrentPlayingUri == null || mActivity == null) return;

            List<AudioItem> masterList = mActivity.retrieveMasterAudioList();
            int pos = findPositionInList(masterList, mCurrentPlayingUri);
            if (pos == -1 || masterList == null) return;

            AudioItem item = masterList.get(pos);
            item.setFavorite(!item.isFavorite());
            mIsFavorite = item.isFavorite();
            updateFavoriteButton();

            if (mAudioList != null) {
                int localPos = findPositionInList(mAudioList, mCurrentPlayingUri);
                if (localPos != -1) mAudioList.get(localPos).setFavorite(mIsFavorite);
            }

            Fragment frag = getChildFragmentManager()
				.findFragmentById(R.id.content_frame);
            if (frag instanceof TrackListFragment) {
                ((TrackListFragment) frag).notifyItemChanged(mCurrentPlayingUri);
            }

            MediaDatabaseHelper dbHelper =
				MediaDatabaseHelper.getInstance(mActivity);
            dbHelper.updateFavorite(mCurrentPlayingUri.toString(), mIsFavorite);

            showToast(mIsFavorite ? "Added to favorites" : "Removed from favorites");
        } catch (Exception e) {
            IkLog.e(TAG, "Error toggling favorite", e);
        }
    }

    private void updateFavoriteButton() {
        try {
            if (mBtnFavorite == null) return;
            mBtnFavorite.setImageResource(mIsFavorite
										  ? R.drawable.ic_favorite_filled
										  : R.drawable.ic_favorite_border);
        } catch (Exception e) {
            IkLog.e(TAG, "Error updating favorite button", e);
        }
    }

    private void cycleVisualizerStyle() {
        try {
            if (mCurrentVisualizerSettings == null) return;
            int newStyle = (mCurrentVisualizerSettings.visualizerStyle + 1) % 23;
            mCurrentVisualizerSettings.visualizerStyle = newStyle;
            mSettingsManager.saveVisualizerSettings(mCurrentVisualizerSettings);
            applyVisualizerSettings();
            showToast("Style: " + mSettingsManager.getVisualizerStyleName(newStyle));
        } catch (Exception e) {
            IkLog.e(TAG, "Error cycling visualizer style", e);
        }
    }

    private void cycleColorScheme() {
        try {
            if (mCurrentVisualizerSettings == null) return;
            int newScheme = (mCurrentVisualizerSettings.colorScheme + 1) % 8;
            mCurrentVisualizerSettings.colorScheme = newScheme;
            mSettingsManager.saveVisualizerSettings(mCurrentVisualizerSettings);
            applyVisualizerSettings();
            showToast("Color: " + mSettingsManager.getColorSchemeName(newScheme));
        } catch (Exception e) {
            IkLog.e(TAG, "Error cycling color scheme", e);
        }
    }

    // =========================================================================
    // Settings
    // =========================================================================

    @Override
    public void onVisualizerSettingsChanged(SettingsManager.VisualizerSettings settings) {
        try {
            mCurrentVisualizerSettings = settings;
            applyVisualizerSettings();
        } catch (Exception e) {
            IkLog.e(TAG, "Error on visualizer settings changed", e);
        }
    }

    private void loadSettings() {
        try {
            mCurrentVisualizerSettings = mSettingsManager.getVisualizerSettings();
            mCurrentPlayerSettings = mSettingsManager.getPlayerSettings();
            applyVisualizerSettings();
            applyPlayerSettings();
        } catch (Exception e) {
            IkLog.e(TAG, "Error loading settings", e);
        }
    }

    private void reloadSettings() {
        if (mSettingsManager == null) {
            IkLog.w(TAG, "reloadSettings skipped – mSettingsManager is null");
            return;
        }
        try {
            SettingsManager.VisualizerSettings newViz =
				mSettingsManager.getVisualizerSettings();
            SettingsManager.PlayerSettings newPlayer =
				mSettingsManager.getPlayerSettings();
            if (newViz != null && !SettingsUtils.areVisualizerSettingsEqual(
                    newViz, mCurrentVisualizerSettings)) {
                mCurrentVisualizerSettings = newViz;
                applyVisualizerSettings();
            }
            if (newPlayer != null && !SettingsUtils.arePlayerSettingsEqual(
                    newPlayer, mCurrentPlayerSettings)) {
                mCurrentPlayerSettings = newPlayer;
                applyPlayerSettings();
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error reloading settings", e);
        }
    }

    private void applyVisualizerSettings() {
        try {
            if (mGlVisualizerView == null || mCurrentVisualizerSettings == null) return;
            mGlVisualizerView.queueEvent(new Runnable() {
					@Override public void run() {
						if (mGlVisualizerView.getRenderer() != null)
							mGlVisualizerView.getRenderer()
                                .updateVisualizerSettings(mCurrentVisualizerSettings);
					}
				});
        } catch (Exception e) {
            IkLog.e(TAG, "Error applying visualizer settings", e);
        }
    }

    private void applyPlayerSettings() {
        try {
            if (mCurrentPlayerSettings != null) {
                int rate = mCurrentPlayerSettings.visualizerUpdateRate;
                mVisualizerUpdateInterval = (rate > 0) ? Math.max(16, 1000 / rate) : 16;
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error applying player settings", e);
        }
    }

    public ColorExtractor.Swatch getSwatch() { return mCurrentSwatch; }

    // =========================================================================
    // Audio loading
    // =========================================================================

    public void refreshAudioLibrary() { loadAudioFilesAsync(true); }

    private void loadAudioFilesAsync(final boolean forceRefresh) {
        if (mIsScanningAudio && !forceRefresh) {
            IkLog.d(TAG, "Scan already in progress, ignored");
            return;
        }
        if (mActivity == null) {
            IkLog.w(TAG, "loadAudioFilesAsync: Activity null, aborted");
            return;
        }
        mIsScanningAudio = true;
        showProgressBar();
        IkLog.d(TAG, "Starting audio scan, forceRefresh= " + forceRefresh);

        try {
            MediaUtils.getAudioFilesAsync(mActivity, new Handler(Looper.getMainLooper()),
				new MediaUtils.AudioFilesCallback() {
					@Override public void onAudioFilesLoaded(List<AudioItem> items) {
						try {
							mIsScanningAudio = false;
							if (!isAdded() || mActivity == null) {
								IkLog.w(TAG, "Fragment not attached when audio files loaded");
								return;
							}
							hideProgressBar();
							if (items == null || items.isEmpty()) {
								IkLog.w(TAG, "No audio files found");
								return;
							}
							mAudioList = new ArrayList<>(items);
							mActivity.storeAudioList(mAudioList);
							IkLog.d(TAG, "Audio files loaded: " + items.size());
							showCategoryGrid();
						} catch (Exception e) {
							IkLog.e(TAG, "Error in onAudioFilesLoaded callback", e);
						}
					}
				}, forceRefresh, false);
        } catch (Exception e) {
            IkLog.e(TAG, "Error starting audio scan", e);
            mIsScanningAudio = false;
        }
    }

    private void showProgressBar() {
        try {
            if (!isAdded()) return;
            Fragment childFrag = getChildFragmentManager()
				.findFragmentById(R.id.content_frame);
            if (childFrag instanceof LibraryCategoryFragment) {
                ((LibraryCategoryFragment) childFrag).showProgress();
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error showing progress bar", e);
        }
    }

    private void hideProgressBar() {
        try {
            if (!isAdded()) return;
            Fragment childFrag = getChildFragmentManager()
				.findFragmentById(R.id.content_frame);
            if (childFrag instanceof LibraryCategoryFragment) {
                ((LibraryCategoryFragment) childFrag).hideProgress();
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error hiding progress bar", e);
        }
    }

    // =========================================================================
    // Queue dialog
    // =========================================================================

    private void showQueueDialog() {
        try {
            if (mActivity == null) return;
            final List<AudioItem> queue = mActivity.getActivePlaylist();
            if (queue.isEmpty()) { showToast("Queue is empty"); return; }

            int bgColor = mCurrentSwatch != null
				? mCurrentSwatch.darkVibrant
				: ContextCompat.getColor(mActivity, R.color.bronze_start);

            final int MAX_TITLE_LEN = 35;
            String[] names = new String[queue.size()];
            for (int i = 0; i < queue.size(); i++) {
                AudioItem item = queue.get(i);
                String prefix = (i == mCurrentPlayingPosition) ? "▶ " : "  ";
                String title = item.getTitle();
                if (title.length() > MAX_TITLE_LEN)
                    title = title.substring(0, MAX_TITLE_LEN) + "…";
                String artist = item.getArtist();
                if (artist != null && artist.length() > MAX_TITLE_LEN)
                    artist = artist.substring(0, MAX_TITLE_LEN) + "…";
                names[i] = prefix + title + " — " + (artist != null ? artist : "");
            }

            IkBeautifulDialog dialog = new IkBeautifulDialog(mActivity);
            dialog.setBackgroundColor(bgColor);
            dialog.setMessage("Play Queue (" + queue.size() + " tracks)");
            dialog.setItems(names, new IkBeautifulDialog.OnItemClickListener() {
					@Override public void onItemClick(int position, String item) {
						if (mIsServiceBound && mActivity != null
                            && position < queue.size()) {
							playAudioItem(queue.get(position), position, queue);
						}
					}
				});
            dialog.showList();
        } catch (Exception e) {
            IkLog.e(TAG, "Error showing queue dialog", e);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private int findPositionInList(List<AudioItem> list, Uri uri) {
        if (list == null || uri == null) return -1;
        for (int i = 0; i < list.size(); i++) {
            if (uri.equals(list.get(i).getUri())) return i;
        }
        return -1;
    }

    @Deprecated
    private int findPositionInList(List<AudioItem> list, String filePath) {
        if (list == null || filePath == null) return -1;
        for (int i = 0; i < list.size(); i++) {
            if (filePath.equals(list.get(i).getFilePath())) return i;
        }
        return -1;
    }

    private String formatTime(int ms) {
        int s    = ms / 1000;
        int mins = s / 60;
        int secs = s % 60;
        if (mins >= 60) {
            int hrs = mins / 60;
            mins = mins % 60;
            return hrs + ":"
				+ (mins < 10 ? "0" : "") + mins + ":"
				+ (secs < 10 ? "0" : "") + secs;
        }
        return mins + ":" + (secs < 10 ? "0" : "") + secs;
    }

    private void showToast(String msg) {
        try {
            if (mActivity != null) {
                Toast.makeText(mActivity, msg, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error showing toast", e);
        }
    }

    public List<AudioItem> getAudioList() {
        return mAudioList != null
			? new ArrayList<>(mAudioList)
			: new ArrayList<AudioItem>();
    }

    // ── Search bar ───────────────────────────────────────────────────────────

    public boolean isSearchbarVisible() { return searchBarShown; }

    public void showSearchBar() {
        try {
            View v = getView(); if (v == null) return;
            ViewGroup sc = (ViewGroup) v.findViewById(R.id.search_bar_container);
            if (sc != null) { sc.bringToFront(); sc.setVisibility(View.VISIBLE); }
            searchBarShown = true;
        } catch (Exception e) {
            IkLog.e(TAG, "Error showing search bar", e);
        }
    }

    public void hideSearchBar() {
        try {
            View v = getView(); if (v == null) return;
            ViewGroup sc = (ViewGroup) v.findViewById(R.id.search_bar_container);
            if (sc != null) {
                ViewParent p = sc.getParent();
                if (p != null) {
                    ViewGroup vg = (ViewGroup) p;
                    vg.removeView(sc);
                    vg.addView(sc, 0);
                    sc.setVisibility(View.GONE);
                }
            }
            searchBarShown = false;
        } catch (Exception e) {
            IkLog.e(TAG, "Error hiding search bar", e);
        }
    }

    private void initSearchBar(View root) {
        try {
            ViewGroup sc = (ViewGroup) root.findViewById(R.id.search_bar_container);
            if (sc != null && mActivity != null) mActivity.setSearchViewHandle(sc);
            searchEditText.addTextChangedListener(new TextWatcher() {
					@Override public void beforeTextChanged(CharSequence s,
															int st, int c, int a) {}
					@Override public void onTextChanged(CharSequence s,
														int st, int b, int c) { filterAudioList(s.toString()); }
					@Override public void afterTextChanged(Editable s) {}
				});
        } catch (Exception e) {
            IkLog.e(TAG, "Error initializing search bar", e);
        }
    }

    private void filterAudioList(String query) {
        try {
            List<AudioItem> source = (mAudioList != null)
				? mAudioList : new ArrayList<AudioItem>();
            List<AudioItem> filtered = new ArrayList<>();
            if (query == null || query.isEmpty()) {
                filtered.addAll(source);
            } else {
                String lower = query.toLowerCase();
                for (AudioItem item : source) {
                    String path = item.getFilePath();
                    String fileName = (path != null)
						? path.substring(path.lastIndexOf('/') + 1) : "";
                    if (fileName.toLowerCase().contains(lower)
						|| item.getTitle().toLowerCase().contains(lower)
						|| item.getArtist().toLowerCase().contains(lower)) {
                        filtered.add(item);
                    }
                }
            }

            Fragment frag = getChildFragmentManager()
				.findFragmentById(R.id.content_frame);
            if (frag instanceof TrackListFragment) {
                AudioSectionedAdapter adapter =
					((TrackListFragment) frag).getAdapter();
                if (adapter != null) {
                    List<AudioListRow> rows = new ArrayList<>();
                    for (AudioItem item : filtered) rows.add(AudioListRow.item(item));
                    adapter.updateRows(rows);
                }
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error filtering audio list", e);
        }
    }

    private void initRefreshButton(View root) {
        try {
            ImageButton refresh = (ImageButton) root.findViewById(R.id.btn_media_refresh);
            if (refresh != null) {
                refresh.setOnClickListener(new View.OnClickListener() {
						@Override public void onClick(View v) { refreshAudioLibrary(); }
					});
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error initializing refresh button", e);
        }
    }

    // =========================================================================
    // Permission gate
    // =========================================================================

    private void checkAndRequestStoragePermission() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                loadAudioFilesAsync(false);
                return;
            }
            String permission;
            if (Build.VERSION.SDK_INT >= 33) {
                permission = android.Manifest.permission.READ_MEDIA_AUDIO;
            } else {
                permission = android.Manifest.permission.READ_EXTERNAL_STORAGE;
            }
            if (mActivity != null
				&& mActivity.checkSelfPermission(permission)
				== PackageManager.PERMISSION_GRANTED) {
                loadAudioFilesAsync(false);
            } else if (mActivity != null) {
                requestPermissions(new String[]{permission}, REQUEST_READ_STORAGE);
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error in permission check", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        try {
            if (requestCode != REQUEST_READ_STORAGE) {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                return;
            }
            if (grantResults.length > 0
				&& grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadAudioFilesAsync(false);
            } else {
                showToast("Storage permission is required to load music");
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error in onRequestPermissionsResult", e);
        }
    }
}