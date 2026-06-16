package com.ikechi.studio.onwa.player.fragment;

import android.app.PictureInPictureParams;
import androidx.fragment.app.Fragment;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Rational;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.ikechi.studio.onwa.player.MainActivity;
import com.ikechi.studio.onwa.player.R;
import com.ikechi.studio.onwa.player.models.VideoItem;
import com.ikechi.studio.onwa.player.service.VideoBackgroundAudioService;
import com.ikechi.studio.onwa.player.utils.IkBeautifulDialog;
import com.ikechi.studio.onwa.player.utils.MediaDatabaseHelper;
import com.ikechi.studio.onwa.widgets.video.IkVideoView;
import com.ikechi.studio.IkLog;

import java.util.ArrayList;
import java.util.List;

public class VideoPlaybackFragment extends Fragment implements
        View.OnClickListener,
        SeekBar.OnSeekBarChangeListener,
        IkVideoView.OnPictureInPictureModeChangedListener {

    private static final String ARG_VIDEO_URI = "video_uri";
    private static final String ARG_VIDEO_TITLE = "video_title";
    private static final String ARG_VIDEO_POS = "video_position";
    private static final String ARG_VIDEO_URIS = "video_uris";
    private static final String ARG_VIDEO_TITLES = "video_titles";
    private static final String ARG_VIDEO_ITEMS = "video_items";

    private static final String TAG = "VideoPlaybackFragment";
    private static final int FINE_SEEK_MS = 1000;
    private static final long POSITION_SAVE_INTERVAL_MS = 2000;
    private static final long RESUME_THRESHOLD_MS = 5000; // Only show resume notification if position > 5 seconds

    private IkVideoView ikVideoView;
    private RelativeLayout overlay;
    private ImageButton btnCenterPlay, btnPlayPause, btnBack, btnPip;
    private ImageButton btnPrevVideo, btnNextVideo;
    private ImageButton btnSeekBack, btnSeekForward;
    private ImageButton btnBookmark;
    private ImageButton btnBgAudio;
    private TextView tvTitle, tvCurrentTime, tvTotalTime;
    private SeekBar seekBar;
    private ProgressBar loadingIndicator;

    // Resume notification views
    private LinearLayout resumeNotification;
    private TextView resumeMessage;
    private Button btnStartOver;

    private Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable hideUiRunnable = new Runnable() {
        @Override
        public void run() {
            hideControls();
        }
    };
    private Runnable hideResumeNotificationRunnable = null;

    private Runnable seekBarUpdater = new Runnable() {
        @Override
        public void run() {
            if (ikVideoView != null && isPlaying && !isSeeking && isAdded()) {
                int current = ikVideoView.getCurrentPosition();
                int duration = ikVideoView.getDuration();
                if (duration > 0) {
                    int progress = (int) ((long) current * 1000 / duration);
                    seekBar.setProgress(progress);
                    tvCurrentTime.setText(formatTime(current));
                }
                uiHandler.postDelayed(this, 1000);
            }
        }
    };

    private Runnable positionSaver = new Runnable() {
        @Override
        public void run() {
            if (ikVideoView != null && isPlaying && isAdded()) {
                saveCurrentPosition();
            }
            uiHandler.postDelayed(this, POSITION_SAVE_INTERVAL_MS);
        }
    };

    private boolean isPlaying = false;
    private boolean isControlsVisible = true;
    private boolean isSeeking = false;
    private boolean mIsEnteringPip = false;
    private int mSavedPosition = 0;
    private boolean mSavedWasPlaying = false;

    private String mVideoTitle;
    private ArrayList<String> mVideoUris;
    private ArrayList<String> mVideoTitles;
    private ArrayList<VideoItem> mVideoItems;
    private int mCurrentIndex;
    private Uri mCurrentVideoUri;
    private long mCurrentVideoDuration = 0;
    private boolean mResumeNotificationShown = false;

    // =========================================================================
    // Factory Method
    // =========================================================================
    public static VideoPlaybackFragment newInstance(String uri, String title, int position,
                                                    ArrayList<String> uris, ArrayList<String> titles,
                                                    List<VideoItem> items) {
        VideoPlaybackFragment frag = new VideoPlaybackFragment();
        Bundle args = new Bundle();
        args.putString(ARG_VIDEO_URI, uri);
        args.putString(ARG_VIDEO_TITLE, title);
        args.putInt(ARG_VIDEO_POS, position);
        args.putStringArrayList(ARG_VIDEO_URIS, uris);
        args.putStringArrayList(ARG_VIDEO_TITLES, titles);
        if (items != null) {
            args.putSerializable(ARG_VIDEO_ITEMS, new ArrayList<VideoItem>(items));
        }
        frag.setArguments(args);
        return frag;
    }

    // =========================================================================
    // Lifecycle Methods
    // =========================================================================
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mSavedPosition = savedInstanceState.getInt("state_position", 0);
            mSavedWasPlaying = savedInstanceState.getBoolean("state_playing", false);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_video_playback, container, false);

        // Initialize views
        ikVideoView = view.findViewById(R.id.ikVideoView);
        overlay = view.findViewById(R.id.overlay);
        btnCenterPlay = view.findViewById(R.id.btn_center_play);
        btnPlayPause = view.findViewById(R.id.btn_play_pause);
        btnBack = view.findViewById(R.id.btn_back);
        btnPip = view.findViewById(R.id.btn_pip);
        btnPrevVideo = view.findViewById(R.id.btn_prev_video);
        btnNextVideo = view.findViewById(R.id.btn_next_video);
        btnSeekBack = view.findViewById(R.id.btn_seek_back);
        btnSeekForward = view.findViewById(R.id.btn_seek_forward);
        btnBookmark = view.findViewById(R.id.btn_bookmark);
        btnBgAudio = view.findViewById(R.id.btn_bg_audio);
        tvTitle = view.findViewById(R.id.tv_title);
        tvCurrentTime = view.findViewById(R.id.tv_current_time);
        tvTotalTime = view.findViewById(R.id.tv_total_time);
        seekBar = view.findViewById(R.id.seek_bar);
        loadingIndicator = view.findViewById(R.id.loading_indicator);

        // Resume notification
        resumeNotification = (LinearLayout) inflater.inflate(R.layout.view_resume_notification, container, false);
        resumeMessage = resumeNotification.findViewById(R.id.resume_message);
        btnStartOver = resumeNotification.findViewById(R.id.btn_start_over);
        ViewGroup parent = (ViewGroup) view;
        parent.addView(resumeNotification);

        // Set click listeners
        overlay.setOnClickListener(this);
        btnCenterPlay.setOnClickListener(this);
        btnPlayPause.setOnClickListener(this);
        btnBack.setOnClickListener(this);
        btnPip.setOnClickListener(this);
        btnPrevVideo.setOnClickListener(this);
        btnNextVideo.setOnClickListener(this);
        btnSeekBack.setOnClickListener(this);
        btnSeekForward.setOnClickListener(this);
        btnBookmark.setOnClickListener(this);
        btnBgAudio.setOnClickListener(this);
        seekBar.setOnSeekBarChangeListener(this);
        btnStartOver.setOnClickListener(this);

        updateNavigationButtons();

        // PiP setup
        if (Build.VERSION.SDK_INT >= 24) {
            if (getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                btnPip.setVisibility(View.VISIBLE);
                ikVideoView.setOnPictureInPictureModeChangedListener(this);
                updatePipParams();
            } else {
                btnPip.setVisibility(View.GONE);
            }
        } else {
            btnPip.setVisibility(View.GONE);
        }

        // Parse arguments
        Bundle args = getArguments();
        if (args != null) {
            String uriString = args.getString(ARG_VIDEO_URI);
            if (uriString != null) {
                mCurrentVideoUri = Uri.parse(uriString);
                setupVideoUri(mCurrentVideoUri);
            }
            mVideoTitle = args.getString(ARG_VIDEO_TITLE, "Video");
            tvTitle.setText(mVideoTitle);
            mVideoUris = args.getStringArrayList(ARG_VIDEO_URIS);
            mVideoTitles = args.getStringArrayList(ARG_VIDEO_TITLES);
            mCurrentIndex = args.getInt(ARG_VIDEO_POS, 0);
            Object itemsObj = args.getSerializable(ARG_VIDEO_ITEMS);
            if (itemsObj instanceof ArrayList) {
                mVideoItems = (ArrayList<VideoItem>) itemsObj;
                IkLog.d(TAG, "Received video items list, size=" + (mVideoItems != null ? mVideoItems.size() : 0));
            } else {
                mVideoItems = new ArrayList<>();
                IkLog.d(TAG, "No video items list in arguments");
            }
        }

        // VideoView listeners
        ikVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                loadingIndicator.setVisibility(View.GONE);
                mCurrentVideoDuration = mp.getDuration();
                updateTotalTime();
                long savedPos = getSavedPositionForCurrentVideo();
                IkLog.d(TAG, "onPrepared: savedPos=" + savedPos + ", duration=" + mCurrentVideoDuration);
                if (savedPos > RESUME_THRESHOLD_MS && savedPos < mCurrentVideoDuration - 2000) {
                    showResumeNotification(savedPos);
                } else {
                    if (mSavedPosition > 0) {
                        ikVideoView.seekTo(mSavedPosition, false);
                        mSavedPosition = 0;
                    }
                    if (mSavedWasPlaying) {
                        startPlayback();
                        mSavedWasPlaying = false;
                    } else {
                        startPlayback();
                    }
                }
                updateNavigationButtons();
            }
        });

        ikVideoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                isPlaying = false;
                updatePlayButtons();
                seekBar.setProgress(seekBar.getMax());
                showControls();
                clearSavedPosition();
                playNextVideo();
            }
        });

        ikVideoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                loadingIndicator.setVisibility(View.GONE);
                Toast.makeText(getActivity(), "Error playing video: " + what, Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        ikVideoView.setOnInfoListener(new MediaPlayer.OnInfoListener() {
            @Override
            public boolean onInfo(MediaPlayer mp, int what, int extra) {
                switch (what) {
                    case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                        loadingIndicator.setVisibility(View.VISIBLE);
                        break;
                    case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                        loadingIndicator.setVisibility(View.GONE);
                        break;
                }
                return false;
            }
        });

        ikVideoView.setClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isControlsVisible) {
                    hideControls();
                } else {
                    showControls();
                }
            }
        });

        ikVideoView.setPlaybackStateListener(new IkVideoView.OnPlaybackStateChangeListener() {
            @Override
            public void onStateChanged(boolean paused) {
                isPlaying = !paused;
                updatePlayButtons();
                if (!paused) {
                    startUpdatingSeekBar();
                    startPositionSaver();
                    hideControlsDelayed();
                } else {
                    uiHandler.removeCallbacks(hideUiRunnable);
                    saveCurrentPosition();
                }
                updatePipParams();
            }
        });

        ikVideoView.setSeekbarListener(new IkVideoView.OnSeekbarStateChangedListener() {
            @Override
            public void onSeek(long position, boolean fromUser) {
                if (fromUser && isAdded()) {
                    int duration = ikVideoView.getDuration();
                    if (duration > 0) {
                        int progress = (int) (position * 1000 / duration);
                        seekBar.setProgress(progress);
                        tvCurrentTime.setText(formatTime((int) position));
                    }
                }
            }
        });

        return view;
    }

    // =========================================================================
    // Resume Position Logic
    // =========================================================================
    private long getSavedPositionForCurrentVideo() {
        try {
            // First, try to get the position from the passed video list
            if (mVideoItems != null && mCurrentIndex >= 0 && mCurrentIndex < mVideoItems.size()) {
                VideoItem item = mVideoItems.get(mCurrentIndex);
                if (item != null) {
                    long pos = item.getLastPosition();
                    IkLog.d(TAG, "getSavedPosition from videoList: " + pos + " ms");
                    return pos;
                }
            }

            // Fallback: Query the database directly
            if (mCurrentVideoUri != null) {
                MediaDatabaseHelper db = MediaDatabaseHelper.getInstance(getActivity());
                VideoItem dbItem = db.getVideoByUri(mCurrentVideoUri);
                if (dbItem != null) {
                    long pos = dbItem.getLastPosition();
                    IkLog.d(TAG, "getSavedPosition from database: " + pos + " ms");
                    // Update the cached video list if possible
                    if (mVideoItems != null && mCurrentIndex >= 0 && mCurrentIndex < mVideoItems.size()) {
                        mVideoItems.get(mCurrentIndex).setLastPosition(pos);
                    }
                    return pos;
                } else {
                    IkLog.d(TAG, "getSavedPosition: no video found in database for URI " + mCurrentVideoUri);
                }
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error in getSavedPositionForCurrentVideo", e);
        }
        return 0;
    }

    private void showResumeNotification(final long savedPosition) {
        try {
            IkLog.d(TAG, "showResumeNotification called with savedPos=" + savedPosition);
            if (mResumeNotificationShown) return;
            mResumeNotificationShown = true;

            String timeStr = formatTime((int) savedPosition);
            resumeMessage.setText("Resume from " + timeStr + "?");

            resumeNotification.setVisibility(View.VISIBLE);
            TranslateAnimation slideIn = new TranslateAnimation(0, 0, -resumeNotification.getHeight(), 0);
            slideIn.setDuration(300);
            resumeNotification.startAnimation(slideIn);

            if (hideResumeNotificationRunnable == null) {
                hideResumeNotificationRunnable = new Runnable() {
                    @Override
                    public void run() {
                        dismissResumeNotification(false);
                        startPlaybackFromPosition(0);
                    }
                };
            }
            uiHandler.postDelayed(hideResumeNotificationRunnable, 5000);
            IkLog.d(TAG, "showResumeNotification completed");
        } catch (Exception e) {
            IkLog.e(TAG, "Error in showResumeNotification", e);
        }
    }

    private void dismissResumeNotification(boolean userClickedStartOver) {
        try {
            if (resumeNotification.getVisibility() != View.VISIBLE) return;
            uiHandler.removeCallbacks(hideResumeNotificationRunnable);
            TranslateAnimation slideOut = new TranslateAnimation(0, 0, 0, -resumeNotification.getHeight());
            slideOut.setDuration(200);
            slideOut.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {}

                @Override
                public void onAnimationEnd(Animation animation) {
                    resumeNotification.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {}
            });
            resumeNotification.startAnimation(slideOut);
            mResumeNotificationShown = false;
            if (userClickedStartOver) {
                clearSavedPosition();
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error in dismissResumeNotification", e);
        }
    }

    private void startPlaybackFromPosition(long positionMs) {
        try {
            if (ikVideoView == null) return;
            if (positionMs > 0) {
                ikVideoView.seekTo((int) positionMs, false);
            }
            startPlayback();
        } catch (Exception e) {
            IkLog.e(TAG, "Error in startPlaybackFromPosition", e);
        }
    }

    private void clearSavedPosition() {
        try {
            if (mCurrentVideoUri != null) {
                MediaDatabaseHelper db = MediaDatabaseHelper.getInstance(getActivity());
                db.updateVideoLastPosition(mCurrentVideoUri, 0);
                if (mVideoItems != null && mCurrentIndex >= 0 && mCurrentIndex < mVideoItems.size()) {
                    mVideoItems.get(mCurrentIndex).setLastPosition(0);
                }
                IkLog.d(TAG, "Cleared saved position for " + mCurrentVideoUri);
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error in clearSavedPosition", e);
        }
    }

    private void saveCurrentPosition() {
        try {
            if (ikVideoView == null || mCurrentVideoUri == null) return;
            int position = ikVideoView.getCurrentPosition();
            if (position <= 0) return;
            int duration = ikVideoView.getDuration();
            if (duration > 0 && duration - position < 2000) {
                clearSavedPosition();
                return;
            }
            MediaDatabaseHelper db = MediaDatabaseHelper.getInstance(getActivity());
            db.updateVideoLastPosition(mCurrentVideoUri, position);
            if (mVideoItems != null && mCurrentIndex >= 0 && mCurrentIndex < mVideoItems.size()) {
                mVideoItems.get(mCurrentIndex).setLastPosition(position);
            }
            IkLog.d(TAG, "Saved position: " + position + "ms");
        } catch (Exception e) {
            IkLog.e(TAG, "Error in saveCurrentPosition", e);
        }
    }

    private void startPositionSaver() {
        try {
            uiHandler.removeCallbacks(positionSaver);
            uiHandler.post(positionSaver);
        } catch (Exception e) {
            IkLog.e(TAG, "Error in startPositionSaver", e);
        }
    }

    private void stopPositionSaver() {
        try {
            uiHandler.removeCallbacks(positionSaver);
        } catch (Exception e) {
            IkLog.e(TAG, "Error in stopPositionSaver", e);
        }
    }

    // =========================================================================
    // Video Setup and Control Methods
    // =========================================================================
    private void setupVideoUri(Uri uri) {
        if (Build.VERSION.SDK_INT >= 19 && "content".equals(uri.getScheme())) {
            try {
                getActivity().getContentResolver().takePersistableUriPermission(uri, 1);
            } catch (Exception e) {
                IkLog.w(TAG, "No persistable permission for uri: " + uri + " " + e.getMessage());
            }
        }
        ikVideoView.setVideoURI(uri);
        mCurrentVideoUri = uri;
    }

    private void updatePipParams() {
        if (Build.VERSION.SDK_INT >= 26 && ikVideoView != null && getActivity() != null) {
            try {
                int width = ikVideoView.getWidth();
                int height = ikVideoView.getHeight();
                if (width > 0 && height > 0) {
                    Rational aspectRatio = new Rational(width, height);
                    PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
                            .setAspectRatio(aspectRatio);
                    getActivity().setPictureInPictureParams(builder.build());
                }
            } catch (IllegalArgumentException e) {
                IkLog.w(TAG, "Invalid aspect ratio for PiP: " + e.getMessage());
            } catch (Exception e) {
                IkLog.w(TAG, "PiP params error: " + e.getMessage());
            }
        }
    }

    private void applyImmersiveMode() {
        if (getActivity() == null) return;
        Window window = getActivity().getWindow();
        if (Build.VERSION.SDK_INT >= 19) {
            View decorView = window.getDecorView();
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).hideMainBars();
        }
        if (ikVideoView != null) ikVideoView.invalidate();
    }

    private void clearImmersiveMode() {
        if (getActivity() == null) return;
        Window window = getActivity().getWindow();
        if (Build.VERSION.SDK_INT >= 19) {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showMainBars();
        }
        if (ikVideoView != null) ikVideoView.invalidate();
    }

    private void startPlayback() {
        if (!isAdded() || ikVideoView == null) return;
        ikVideoView.start();
        isPlaying = true;
        updatePlayButtons();
        startUpdatingSeekBar();
        startPositionSaver();
        hideControlsDelayed();
        updatePipParams();
    }

    private void pausePlayback() {
        if (!isAdded() || ikVideoView == null) return;
        ikVideoView.pause();
        isPlaying = false;
        updatePlayButtons();
        uiHandler.removeCallbacks(hideUiRunnable);
        stopPositionSaver();
        saveCurrentPosition();
        updatePipParams();
    }

    private void updatePlayButtons() {
        int res = isPlaying ? R.drawable.ic_pause_video : R.drawable.ic_play_video;
        btnCenterPlay.setImageResource(res);
        btnPlayPause.setImageResource(res);
    }

    private void togglePlayPause() {
        if (isPlaying) {
            pausePlayback();
        } else {
            startPlayback();
        }
    }

    private void startUpdatingSeekBar() {
        uiHandler.removeCallbacks(seekBarUpdater);
        uiHandler.post(seekBarUpdater);
    }

    private void updateTotalTime() {
        if (ikVideoView == null) return;
        int duration = ikVideoView.getDuration();
        tvTotalTime.setText(formatTime(duration));
    }

    private String formatTime(int ms) {
        int s = ms / 1000;
        int mins = s / 60;
        int secs = s % 60;
        if (mins >= 60) {
            int hrs = mins / 60;
            mins = mins % 60;
            return hrs + ":" + (mins < 10 ? "0" : "") + mins
                    + ":" + (secs < 10 ? "0" : "") + secs;
        }
        return mins + ":" + (secs < 10 ? "0" : "") + secs;
    }

    private void hideControls() {
        if (isControlsVisible && ikVideoView != null && ikVideoView.isPlaying()) {
            overlay.setVisibility(View.GONE);
            isControlsVisible = false;
        }
    }

    private void showControls() {
        overlay.setVisibility(View.VISIBLE);
        isControlsVisible = true;
        hideControlsDelayed();
    }

    private void hideControlsDelayed() {
        uiHandler.removeCallbacks(hideUiRunnable);
        uiHandler.postDelayed(hideUiRunnable, 2000);
    }

    private void playNextVideo() {
        if (mVideoUris != null && mCurrentIndex < mVideoUris.size() - 1) {
            mCurrentIndex++;
            loadVideoAtIndex(mCurrentIndex);
        } else {
            if (getActivity() != null) {
                clearImmersiveMode();
                getActivity().onBackPressed();
            }
        }
    }

    private void playPreviousVideo() {
        if (mVideoUris != null && mCurrentIndex > 0) {
            mCurrentIndex--;
            loadVideoAtIndex(mCurrentIndex);
        }
    }

    private void loadVideoAtIndex(int index) {
        String uriStr = mVideoUris.get(index);
        String title = (mVideoTitles != null && index < mVideoTitles.size())
                ? mVideoTitles.get(index) : "Video";
        mCurrentVideoUri = Uri.parse(uriStr);
        setupVideoUri(mCurrentVideoUri);
        tvTitle.setText(title);
        mVideoTitle = title;
        mSavedPosition = 0;
        mSavedWasPlaying = true;
        updateNavigationButtons();
    }

    private void updateNavigationButtons() {
        if (btnPrevVideo != null) {
            btnPrevVideo.setEnabled(mVideoUris != null && mCurrentIndex > 0);
        }
        if (btnNextVideo != null) {
            btnNextVideo.setEnabled(mVideoUris != null && mCurrentIndex < mVideoUris.size() - 1);
        }
    }

    private void fineSeekBackward() {
        if (ikVideoView == null) return;
        int pos = ikVideoView.getCurrentPosition() - FINE_SEEK_MS;
        if (pos < 0) pos = 0;
        ikVideoView.seekTo(pos, true);
    }

    private void fineSeekForward() {
        if (ikVideoView == null) return;
        int duration = ikVideoView.getDuration();
        if (duration <= 0) return;
        int pos = ikVideoView.getCurrentPosition() + FINE_SEEK_MS;
        if (pos > duration) pos = duration;
        ikVideoView.seekTo(pos, true);
    }

    private void startBackgroundAudio() {
        if (mCurrentVideoUri == null) {
            Toast.makeText(getActivity(), "No video loaded", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(getActivity(), VideoBackgroundAudioService.class);
        intent.putExtra(VideoBackgroundAudioService.EXTRA_VIDEO_URI, mCurrentVideoUri.toString());
        getActivity().startService(intent);
        Toast.makeText(getActivity(), "Audio will continue in background", Toast.LENGTH_SHORT).show();
    }

    private void stopBackgroundAudio() {
        Intent intent = new Intent(getActivity(), VideoBackgroundAudioService.class);
        intent.setAction(VideoBackgroundAudioService.ACTION_STOP);
        getActivity().startService(intent);
    }

    private void showBookmarkDialog() {
        if (ikVideoView == null || mCurrentVideoUri == null) {
            Toast.makeText(getActivity(), "No video loaded", Toast.LENGTH_SHORT).show();
            return;
        }
        final int positionMs = ikVideoView.getCurrentPosition();
        new IkBeautifulDialog(getActivity())
                .setMessage("Bookmark this moment?\n" + formatTime(positionMs))
                .setInput("Bookmark name", "", new IkBeautifulDialog.OnInputConfirmedListener() {
                    @Override
                    public void onInputConfirmed(String inputText) {
                        if (inputText == null || inputText.trim().isEmpty()) {
                            inputText = "Bookmark " + formatTime(positionMs);
                        }
                        MediaDatabaseHelper db = MediaDatabaseHelper.getInstance(getActivity());
                        db.addVideoBookmark(mCurrentVideoUri.toString(), inputText.trim(), positionMs);
                        Toast.makeText(getActivity(), "Bookmark saved: " + inputText.trim(),
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setPositiveButton("Save", null)
                .showInput();
    }

    // =========================================================================
    // Click and SeekBar Listeners
    // =========================================================================
    @Override
    public void onClick(View v) {
        if (v == overlay) {
            if (isControlsVisible) {
                hideControls();
            } else {
                showControls();
            }
        } else if (v == btnCenterPlay || v == btnPlayPause) {
            togglePlayPause();
        } else if (v == btnBack) {
            clearImmersiveMode();
            getActivity().onBackPressed();
        } else if (v == btnPip) {
            if (Build.VERSION.SDK_INT >= 24) {
                mIsEnteringPip = true;
                updatePipParams();
                getActivity().enterPictureInPictureMode();
            }
        } else if (v == btnPrevVideo) {
            playPreviousVideo();
        } else if (v == btnNextVideo) {
            playNextVideo();
        } else if (v == btnSeekBack) {
            fineSeekBackward();
            showControls();
        } else if (v == btnSeekForward) {
            fineSeekForward();
            showControls();
        } else if (v == btnBookmark) {
            showBookmarkDialog();
            showControls();
        } else if (v == btnBgAudio) {
            startBackgroundAudio();
            showControls();
        } else if (v == btnStartOver) {
            dismissResumeNotification(true);
            startPlaybackFromPosition(0);
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser && ikVideoView != null) {
            int duration = ikVideoView.getDuration();
            if (duration > 0) {
                int seekPos = (int) ((long) duration * progress / 1000);
                ikVideoView.seekTo(seekPos, true);
                tvCurrentTime.setText(formatTime(seekPos));
            }
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        isSeeking = true;
        uiHandler.removeCallbacks(hideUiRunnable);
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        isSeeking = false;
        startUpdatingSeekBar();
        hideControlsDelayed();
    }

    // =========================================================================
    // PiP and Configuration Changes
    // =========================================================================
    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        mIsEnteringPip = false;
        if (isInPictureInPictureMode) {
            overlay.setVisibility(View.GONE);
            uiHandler.removeCallbacksAndMessages(null);
        } else {
            showControls();
            if (isPlaying) {
                startUpdatingSeekBar();
                startPositionSaver();
            }
            applyImmersiveMode();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyImmersiveMode();
        updatePipParams();
    }

    // =========================================================================
    // Lifecycle Methods
    // =========================================================================
    @Override
    public void onResume() {
        super.onResume();
        stopBackgroundAudio();
        applyImmersiveMode();
        if (ikVideoView != null) {
            isPlaying = ikVideoView.isPlaying();
            updatePlayButtons();
            if (isPlaying) {
                startUpdatingSeekBar();
                startPositionSaver();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (ikVideoView != null) {
            mSavedPosition = ikVideoView.getCurrentPosition();
            mSavedWasPlaying = ikVideoView.isPlaying();
            saveCurrentPosition();
            if (!mIsEnteringPip && ikVideoView.isPlaying()) {
                ikVideoView.pause();
            }
        }
        stopPositionSaver();
        uiHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("state_position", mSavedPosition);
        outState.putBoolean("state_playing", mSavedWasPlaying);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mIsEnteringPip) return;
        if (ikVideoView != null && ikVideoView.isPlaying()) {
            ikVideoView.pause();
        }
        saveCurrentPosition();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        clearImmersiveMode();
        if (ikVideoView != null) {
            ikVideoView.stop();
        }
        stopPositionSaver();
        uiHandler.removeCallbacksAndMessages(null);
        if (resumeNotification != null) {
            resumeNotification.setVisibility(View.GONE);
        }
    }
}