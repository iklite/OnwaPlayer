package com.ikechi.studio.onwa.widgets.video;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.IOException;
import java.util.Locale;

import com.ikechi.studio.onwa.player.R;

public class IkVideoView extends FrameLayout implements
TextureView.SurfaceTextureListener,
SeekBar.OnSeekBarChangeListener {

    // UI components
    private TextureView textureView;
    private SeekBar seekBar;
    private TextView zoomPercentView;
    private TextView feedbackOverlay;
    private TextView seekFeedbackOverlay;
    private TextView speedFeedbackOverlay;

    // MediaPlayer and surface
    private MediaPlayer mediaPlayer;
    private Surface surface;
    private boolean isSurfaceReady = false;

    // Video source and state
    private Uri videoUri;
    private boolean autoStart = false;
    private boolean isPrepared = false;
    private boolean isPlaying = false;
    private boolean isPaused = false;
    private boolean isCompleted = false;
    private boolean isSeeking = false;

    // Scale type
    public enum ScaleType { FIT_CENTER, CENTER_CROP }
    private ScaleType scaleType = ScaleType.FIT_CENTER;

    // Gesture detectors
    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleGestureDetector;
    private boolean isScaling = false;

    // Zoom state
    private float currentZoomScale = 1.0f;
    private PointF currentZoomTranslate = new PointF(0f, 0f);
    private float minZoom = 0.5f;
    private float maxZoom = 3.0f;

    // Brightness & volume
    private AudioManager audioManager;
    private Window window;
    private boolean isAdjustingBrightness = false;
    private boolean isAdjustingVolume = false;
    private float startBrightness;
    private int startVolume;
    private float startY;
    private static final int EDGE_WIDTH_DP = 80;
    private int edgeWidthPx;

    // Volume boost removed – now simply tracks system max
    private int systemMaxVolume;
    private int currentSystemVolume;

    // Playback speed
    private float playbackSpeed = 1.0f;
    private static final float MIN_PLAYBACK_SPEED = 0.5f;
    private static final float MAX_PLAYBACK_SPEED = 2.0f;

    // Horizontal seek - only in bottom 30% of screen
    private boolean isSeekingHorizontal = false;
    private float startSeekX;
    private int startSeekPosition;
    private static final int SEEK_SENSITIVITY = 1;
    private static final int SEEK_FEEDBACK_DURATION_MS = 500;
    private Runnable hideSeekFeedbackRunnable;
    private static final float SEEK_ZONE_HEIGHT_RATIO = 0.30f;

    // Listeners
    private MediaPlayer.OnPreparedListener onPreparedListener;
    private MediaPlayer.OnErrorListener onErrorListener;
    private MediaPlayer.OnCompletionListener onCompletionListener;
    private MediaPlayer.OnInfoListener onInfoListener;
    private OnPictureInPictureModeChangedListener onPictureInPictureModeChangedListener;
    private View.OnClickListener mClickListener;
    private OnPlaybackStateChangeListener mPlaybackStateListener;
    private OnSeekbarStateChangedListener mSeekbarListener;

    // Handler
    private Handler handler = new Handler();
    private Runnable updateSeekBar = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && isPlaying && !isSeeking) {
                int current = mediaPlayer.getCurrentPosition();
                int duration = mediaPlayer.getDuration();
                if (duration > 0) {
                    seekBar.setProgress((int) ((long) current * 1000 / duration));
                    if (mSeekbarListener != null) {
                        mSeekbarListener.onSeek(current, false);
                    }
                }
                handler.postDelayed(this, 1000);
            }
        }
    };

    private Runnable hideSpeedFeedbackRunnable = new Runnable() {
        @Override
        public void run() {
            speedFeedbackOverlay.setVisibility(View.GONE);
        }
    };

    private Runnable hideFeedbackRunnable = new Runnable() {
        @Override
        public void run() {
            feedbackOverlay.setVisibility(View.GONE);
        }
    };

    // ── Edge double‑tap seeking with visual feedback ───────────────────────
    private int mEdgeSeekCount = 0;
    private long mEdgeSeekDirection = 0;   // 1 = forward, -1 = backward
    private final Handler mSeekDebounceHandler = new Handler(Looper.getMainLooper());
    private final Runnable mEdgeSeekRunner = new Runnable() {
        @Override
        public void run() {
            if (mEdgeSeekCount > 0 && mediaPlayer != null && isPrepared) {
                int delta = (int) mEdgeSeekDirection * 10000 * mEdgeSeekCount;
                int newPos = mediaPlayer.getCurrentPosition() + delta;
                newPos = Math.max(0, Math.min(newPos, mediaPlayer.getDuration()));
                mediaPlayer.seekTo(newPos);
                seekBar.setProgress((int) ((long) newPos * 1000 / mediaPlayer.getDuration()));
                String dir = mEdgeSeekDirection > 0 ? ">>" : "<<";
                showFeedback(dir + " " + (Math.abs(delta) / 1000) + "s");
                if (mSeekbarListener != null) mSeekbarListener.onSeek(newPos, true);
            }
            mEdgeSeekCount = 0;
            mEdgeSeekDirection = 0;
        }
    };

    // ★ NEW: Touch lock for preventing inadvertent input
    private boolean mTouchLocked = false;

    // Interfaces
    public interface OnPictureInPictureModeChangedListener {
        void onPictureInPictureModeChanged(boolean isInPictureInPictureMode);
    }

    public interface OnPlaybackStateChangeListener {
        void onStateChanged(boolean paused);
    }

    public interface OnSeekbarStateChangedListener {
        void onSeek(long position, boolean fromUser);
    }

    // Constructors
    public IkVideoView(Context context) {
        super(context);
        init(context, null);
    }

    public IkVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public IkVideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        // Initialize UI components
        textureView = new TextureView(context);
        seekBar = new SeekBar(context);
        seekBar.setMax(1000);
        seekBar.setVisibility(View.GONE);

        // Feedback overlays
        zoomPercentView = new TextView(context);
        feedbackOverlay = new TextView(context);
        seekFeedbackOverlay = new TextView(context);
        speedFeedbackOverlay = new TextView(context);

        // Configure feedback overlays
        configureFeedbackOverlay(zoomPercentView);
        configureFeedbackOverlay(feedbackOverlay);
        configureFeedbackOverlay(seekFeedbackOverlay);
        configureFeedbackOverlay(speedFeedbackOverlay);

        // Layout setup
        addViews();

        // Attributes
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.IkVideoView);
            String uriString = a.getString(R.styleable.IkVideoView_videoUri);
            if (uriString != null) {
                videoUri = Uri.parse(uriString);
            }
            autoStart = a.getBoolean(R.styleable.IkVideoView_autoStart, false);
            int scaleTypeValue = a.getInt(R.styleable.IkVideoView_scaleType, 0);
            scaleType = (scaleTypeValue == 0) ? ScaleType.FIT_CENTER : ScaleType.CENTER_CROP;
            boolean showSeekBar = a.getBoolean(R.styleable.IkVideoView_showSeekBar, true);
            seekBar.setVisibility(showSeekBar ? View.VISIBLE : View.GONE);
            a.recycle();
        }

        // Gesture and audio setup
        gestureDetector = new GestureDetector(context, new GestureListener());
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            systemMaxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            currentSystemVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        }
        if (context instanceof android.app.Activity) {
            window = ((android.app.Activity) context).getWindow();
        }
        edgeWidthPx = dpToPx(EDGE_WIDTH_DP);

        hideSeekFeedbackRunnable = new Runnable() {
            @Override
            public void run() {
                seekFeedbackOverlay.setVisibility(View.GONE);
            }
        };

        textureView.setSurfaceTextureListener(this);
        seekBar.setOnSeekBarChangeListener(this);
        setupTouchListener();
    }

    private void configureFeedbackOverlay(TextView overlay) {
        overlay.setVisibility(View.GONE);
        overlay.setBackgroundColor(0x80000000);
        overlay.setTextColor(0xFFFFFFFF);
        overlay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        overlay.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        overlay.setGravity(android.view.Gravity.CENTER);
    }

    private void addViews() {
        // TextureView
        LayoutParams textureParams = new LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT);
        textureParams.gravity = android.view.Gravity.FILL;
        addView(textureView, textureParams);

        // SeekBar
        LayoutParams seekParams = new LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        seekParams.gravity = android.view.Gravity.BOTTOM;
        addView(seekBar, seekParams);

        // Feedback overlays
        LayoutParams feedbackParams = new LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        feedbackParams.gravity = android.view.Gravity.CENTER;
        addView(zoomPercentView, feedbackParams);
        addView(feedbackOverlay, feedbackParams);
        addView(seekFeedbackOverlay, feedbackParams);
        addView(speedFeedbackOverlay, feedbackParams);
    }

    // Playback Speed Control
    public void setPlaybackSpeed(float speed) {
        if (mediaPlayer == null) return;
        playbackSpeed = Math.max(MIN_PLAYBACK_SPEED, Math.min(MAX_PLAYBACK_SPEED, speed));
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(playbackSpeed));
            }
        } catch (Exception e) {
            showFeedback("Speed adjustment not supported");
        }
        showSpeedFeedback();
    }

    private void showSpeedFeedback() {
        String speedText = String.format(Locale.getDefault(), "Speed: %.1fx", playbackSpeed);
        speedFeedbackOverlay.setText(speedText);
        speedFeedbackOverlay.setVisibility(View.VISIBLE);
        handler.postDelayed(hideSpeedFeedbackRunnable, 800);
    }

    // Seek Zone Logic
    private boolean isInSeekZone(float y) {
        return y > getHeight() * (1 - SEEK_ZONE_HEIGHT_RATIO);
    }

    // Touch Listener
    private void setupTouchListener() {
        textureView.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // ★ If touch is locked, ignore all input
                if (mTouchLocked) return false;

                int action = event.getActionMasked();
                float x = event.getX();
                float y = event.getY();
                int width = getWidth();

                // Forward to gesture detectors
                boolean gestureHandled = scaleGestureDetector.onTouchEvent(event);
                gestureHandled = gestureDetector.onTouchEvent(event) || gestureHandled;

                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        if (x < edgeWidthPx) {
                            isAdjustingBrightness = true;
                            startBrightness = getCurrentBrightness();
                            startY = y;
                            return true;
                        } else if (x > width - edgeWidthPx) {
                            isAdjustingVolume = true;
                            startVolume = getCurrentVolume();
                            startY = y;
                            return true;
                        } else if (isInSeekZone(y)) {
                            isSeekingHorizontal = true;
                            startSeekX = x;
                            startSeekPosition = getCurrentPosition();
                            isSeeking = true;
                            handler.removeCallbacks(updateSeekBar);
                            return true;
                        }
                        return gestureHandled;

                    case MotionEvent.ACTION_MOVE:
                        if (isAdjustingBrightness) {
                            adjustBrightness(y);
                        } else if (isAdjustingVolume) {
                            adjustVolume(y);
                        } else if (isSeekingHorizontal && !isScaling) {
                            performSeek(x);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        isAdjustingBrightness = false;
                        isAdjustingVolume = false;
                        isSeekingHorizontal = false;
                        feedbackOverlay.setVisibility(View.GONE);
                        if (isSeeking) {
                            isSeeking = false;
                            if (isPlaying) {
                                handler.post(updateSeekBar);
                            }
                        }
                        return true;

                    default:
                        return gestureHandled;
                }
            }
        });
    }

    // Brightness/Volume/Seek Logic
    private void adjustBrightness(float y) {
        float delta = (startY - y) / getHeight();
        float newBrightness = Math.max(0.0f, Math.min(1.0f, startBrightness + delta));
        setBrightness(newBrightness);
        String brightnessText = String.format(Locale.getDefault(), "Brightness: %d%%", Math.round(newBrightness * 100));
        showFeedback(brightnessText);
    }

    private void adjustVolume(float y) {
        float delta = (startY - y) / getHeight();
        // ★ Remove volume boost – cap at system max
        float volumePercent = (startVolume / (float) systemMaxVolume) + delta;
        volumePercent = Math.max(0.0f, Math.min(1.0f, volumePercent));
        int newVolume = Math.round(volumePercent * systemMaxVolume);
        setVolume(newVolume);
        String volumeText = String.format(Locale.getDefault(), "Volume: %d%%", Math.round(volumePercent * 100));
        showFeedback(volumeText);
    }

    private void performSeek(float x) {
        float deltaX = x - startSeekX;
        int duration = getDuration();
        if (duration > 0) {
            int seekDelta = (int) (deltaX * duration / getWidth() * SEEK_SENSITIVITY);
            int newPosition = startSeekPosition + seekDelta;
            newPosition = Math.max(0, Math.min(duration, newPosition));
            if (mediaPlayer != null) {
                mediaPlayer.seekTo(newPosition);
            }
            seekBar.setProgress((int) ((long) newPosition * 1000 / duration));
            showSeekFeedback(newPosition, duration);
        }
    }

    // Helper Methods
    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private float getCurrentBrightness() {
        if (window != null) {
            WindowManager.LayoutParams attrs = window.getAttributes();
            if (attrs.screenBrightness < 0) {
                return 0.5f;
            }
            return attrs.screenBrightness;
        }
        return 0.5f;
    }

    private void setBrightness(float brightness) {
        if (window != null) {
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.screenBrightness = brightness;
            window.setAttributes(attrs);
        }
    }

    private int getCurrentVolume() {
        if (audioManager != null) {
            return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        }
        return 0;
    }

    private void setVolume(int volume) {
        if (audioManager == null) return;
        // ★ Simplest call, no boost, no reflection
        int targetVolume = Math.max(0, Math.min(systemMaxVolume, volume));
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0);
    }

    private void showFeedback(String text) {
        feedbackOverlay.setText(text);
        feedbackOverlay.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideFeedbackRunnable);
        handler.postDelayed(hideFeedbackRunnable, 800);
    }

    private void showSeekFeedback(int position, int duration) {
        String seekText = String.format(Locale.getDefault(), "%s / %s",
                formatTime(position), formatTime(duration));
        seekFeedbackOverlay.setText(seekText);
        seekFeedbackOverlay.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideSeekFeedbackRunnable);
        handler.postDelayed(hideSeekFeedbackRunnable, SEEK_FEEDBACK_DURATION_MS);
    }

    private String formatTime(int millis) {
        int sec = (millis / 1000) % 60;
        int min = (millis / (1000 * 60)) % 60;
        int hr = millis / (1000 * 60 * 60);
        if (hr > 0) {
            return String.format("%d:%02d:%02d", hr, min, sec);
        } else {
            return String.format("%02d:%02d", min, sec);
        }
    }

    // SurfaceTextureListener
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        surface = new Surface(surfaceTexture);
        isSurfaceReady = true;
        openVideo();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
        applyScaleType(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        isSurfaceReady = false;
        if (surface != null) {
            surface.release();
            surface = null;
        }
        releaseMediaPlayer();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {}

    // Video Control
    public void setVideoURI(Uri uri) {
        videoUri = uri;
        if (isSurfaceReady) {
            openVideo();
        }
    }

    private void openVideo() {
        if (videoUri == null || !isSurfaceReady) {
            return;
        }
        releaseMediaPlayer();
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setSurface(surface);
            mediaPlayer.setDataSource(getContext(), videoUri);

            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    isPrepared = true;
                    isCompleted = false;
                    seekBar.setProgress(0);
                    currentZoomScale = 1.0f;
                    currentZoomTranslate.set(0f, 0f);
                    updateZoomPercentDisplay();
                    applyScaleType(textureView.getWidth(), textureView.getHeight());
                    if (autoStart) {
                        start();
                    }
                    if (onPreparedListener != null) {
                        onPreparedListener.onPrepared(mp);
                    }
                }
            });

            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    isPrepared = false;
                    if (onErrorListener != null) {
                        return onErrorListener.onError(mp, what, extra);
                    }
                    return true;
                }
            });

            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    isPlaying = false;
                    isPaused = false;
                    isCompleted = true;
                    setKeepScreenOn(false);
                    handler.removeCallbacks(updateSeekBar);
                    if (onCompletionListener != null) {
                        onCompletionListener.onCompletion(mp);
                    }
                }
            });

            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            handler.removeCallbacks(updateSeekBar);
            mediaPlayer.release();
            mediaPlayer = null;
            isPrepared = false;
            isPlaying = false;
            isPaused = false;
            isCompleted = false;
        }
    }

    // Playback Control
    public void start() {
        if (mediaPlayer == null || !isPrepared) return;
        if (isCompleted) {
            mediaPlayer.seekTo(0);
            isCompleted = false;
        }
        if (!isPlaying) {
            mediaPlayer.start();
            isPlaying = true;
            isPaused = false;
            handler.post(updateSeekBar);
        }
        setKeepScreenOn(true);
    }

    public void pause() {
        if (mediaPlayer != null && isPlaying) {
            mediaPlayer.pause();
            isPlaying = false;
            isPaused = true;
            setKeepScreenOn(false);
            handler.removeCallbacks(updateSeekBar);
        }
    }

    public void resume() {
        if (mediaPlayer == null || !isPrepared || isPlaying) return;
        if (isCompleted) {
            mediaPlayer.seekTo(0);
            isCompleted = false;
        }
        mediaPlayer.start();
        isPlaying = true;
        isPaused = false;
        setKeepScreenOn(true);
        handler.post(updateSeekBar);
    }

    public void stop() {
        releaseMediaPlayer();
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public int getDuration() {
        return (mediaPlayer != null && isPrepared) ? mediaPlayer.getDuration() : 0;
    }

    public int getCurrentPosition() {
        return (mediaPlayer != null && isPrepared) ? mediaPlayer.getCurrentPosition() : 0;
    }

    public void seekTo(int msec) {
        if (mediaPlayer != null && isPrepared) {
            mediaPlayer.seekTo(msec);
        }
    }

    public void seekTo(int msec, boolean fromUser) {
        if (mediaPlayer != null && isPrepared) {
            mediaPlayer.seekTo(msec);
            if (fromUser && mSeekbarListener != null) {
                mSeekbarListener.onSeek(msec, true);
            }
        }
    }

    // Scale Type
    public void setScaleType(ScaleType scaleType) {
        this.scaleType = scaleType;
        if (textureView.getWidth() > 0 && textureView.getHeight() > 0) {
            applyScaleType(textureView.getWidth(), textureView.getHeight());
        }
    }

    // Zoom Logic
    private Matrix computeBaseMatrix(int viewWidth, int viewHeight) {
        if (mediaPlayer == null) return new Matrix();
        int videoWidth = mediaPlayer.getVideoWidth();
        int videoHeight = mediaPlayer.getVideoHeight();
        if (videoWidth == 0 || videoHeight == 0 || viewWidth == 0 || viewHeight == 0) {
            return new Matrix();
        }

        Matrix matrix = new Matrix();
        float videoAr = (float) videoWidth / videoHeight;
        float viewAr = (float) viewWidth / viewHeight;
        float scaleX, scaleY, tx, ty;

        switch (scaleType) {
            case CENTER_CROP:
                if (videoAr > viewAr) {
                    scaleX = videoAr / viewAr;
                    scaleY = 1.0f;
                    tx = viewWidth * (1.0f - scaleX) / 2.0f;
                    ty = 0f;
                } else {
                    scaleX = 1.0f;
                    scaleY = viewAr / videoAr;
                    tx = 0f;
                    ty = viewHeight * (1.0f - scaleY) / 2.0f;
                }
                break;
            case FIT_CENTER:
            default:
                if (videoAr > viewAr) {
                    scaleX = 1.0f;
                    scaleY = viewAr / videoAr;
                    tx = 0f;
                    ty = viewHeight * (1.0f - scaleY) / 2.0f;
                } else {
                    scaleX = videoAr / viewAr;
                    scaleY = 1.0f;
                    tx = viewWidth * (1.0f - scaleX) / 2.0f;
                    ty = 0f;
                }
                break;
        }
        matrix.setScale(scaleX, scaleY);
        matrix.postTranslate(tx, ty);
        return matrix;
    }

    private void updateTransform() {
        if (mediaPlayer == null) return;
        int viewWidth = textureView.getWidth();
        int viewHeight = textureView.getHeight();
        if (viewWidth == 0 || viewHeight == 0) return;

        Matrix baseMatrix = computeBaseMatrix(viewWidth, viewHeight);
        Matrix finalMatrix = new Matrix(baseMatrix);
        finalMatrix.postScale(currentZoomScale, currentZoomScale, viewWidth / 2f, viewHeight / 2f);
        finalMatrix.postTranslate(currentZoomTranslate.x, currentZoomTranslate.y);
        textureView.setTransform(finalMatrix);
        updateZoomPercentDisplay();
    }

    private void updateZoomPercentDisplay() {
        int percent = Math.round(currentZoomScale * 100);
        zoomPercentView.setText(percent + "%");
        if (Math.abs(currentZoomScale - 1.0f) > 0.01f) {
            zoomPercentView.setVisibility(View.VISIBLE);
        } else {
            zoomPercentView.setVisibility(View.GONE);
        }
    }

    private void applyScaleType(int viewWidth, int viewHeight) {
        if (currentZoomScale > 1.0f) {
            clampTranslation(viewWidth, viewHeight);
        } else {
            currentZoomTranslate.set(0f, 0f);
        }
        updateTransform();
    }

    private void pan(float dx, float dy) {
        if (currentZoomScale <= 1.0f) return;
        int viewWidth = textureView.getWidth();
        int viewHeight = textureView.getHeight();
        if (viewWidth == 0 || viewHeight == 0) return;

        float maxX = (currentZoomScale - 1.0f) * viewWidth / 2.0f;
        float maxY = (currentZoomScale - 1.0f) * viewHeight / 2.0f;
        float newX = currentZoomTranslate.x + dx;
        float newY = currentZoomTranslate.y + dy;
        newX = Math.max(-maxX, Math.min(maxX, newX));
        newY = Math.max(-maxY, Math.min(maxY, newY));
        currentZoomTranslate.set(newX, newY);
        updateTransform();
    }

    private void clampTranslation(int viewWidth, int viewHeight) {
        float maxX = (currentZoomScale - 1.0f) * viewWidth / 2.0f;
        float maxY = (currentZoomScale - 1.0f) * viewHeight / 2.0f;
        float clampedX = Math.max(-maxX, Math.min(maxX, currentZoomTranslate.x));
        float clampedY = Math.max(-maxY, Math.min(maxY, currentZoomTranslate.y));
        if (clampedX != currentZoomTranslate.x || clampedY != currentZoomTranslate.y) {
            currentZoomTranslate.set(clampedX, clampedY);
        }
    }

    // Public Zoom API
    public int getZoomPercent() {
        return Math.round(currentZoomScale * 100);
    }

    public void resetZoom() {
        currentZoomScale = 1.0f;
        currentZoomTranslate.set(0f, 0f);
        updateTransform();
    }

    // SeekBar Visibility
    public void setSeekBarVisible(boolean visible) {
        seekBar.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    // SeekBar Listener
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser && mediaPlayer != null && isPrepared) {
            int duration = mediaPlayer.getDuration();
            if (duration > 0) {
                int seekPosition = (int) ((long) duration * progress / 1000);
                mediaPlayer.seekTo(seekPosition);
                showSeekFeedback(seekPosition, duration);
                if (mSeekbarListener != null) {
                    mSeekbarListener.onSeek(seekPosition, true);
                }
            }
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        isSeeking = true;
        handler.removeCallbacks(updateSeekBar);
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        isSeeking = false;
        if (isPlaying) {
            handler.post(updateSeekBar);
        }
    }

    // Picture-in-Picture
    @android.annotation.TargetApi(24)
    public void enterPictureInPictureMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && getContext() instanceof android.app.Activity) {
            ((android.app.Activity) getContext()).enterPictureInPictureMode();
        }
    }

    public void setOnPictureInPictureModeChangedListener(OnPictureInPictureModeChangedListener listener) {
        onPictureInPictureModeChangedListener = listener;
    }

    public void dispatchPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        if (onPictureInPictureModeChangedListener != null) {
            onPictureInPictureModeChangedListener.onPictureInPictureModeChanged(isInPictureInPictureMode);
        }
    }

    // ★ NEW: Touch lock public methods
    public void setTouchLocked(boolean locked) {
        mTouchLocked = locked;
    }

    public boolean isTouchLocked() {
        return mTouchLocked;
    }

    // Gesture Handling
    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            // ★ If locked, ignore all gestures
            if (mTouchLocked) return false;

            float x = e.getX();
            int width = getWidth();
            float edge = width * 0.18f;

            if (x < edge) {
                // left edge → rewind
                mEdgeSeekDirection = -1;
                mEdgeSeekCount++;
                mSeekDebounceHandler.removeCallbacks(mEdgeSeekRunner);
                mSeekDebounceHandler.postDelayed(mEdgeSeekRunner, 500);
                showFeedback("<<" + (mEdgeSeekCount * 10) + "s");
                return true;
            } else if (x > width - edge) {
                // right edge → forward
                mEdgeSeekDirection = 1;
                mEdgeSeekCount++;
                mSeekDebounceHandler.removeCallbacks(mEdgeSeekRunner);
                mSeekDebounceHandler.postDelayed(mEdgeSeekRunner, 500);
                showFeedback((mEdgeSeekCount * 10) + "s >>");
                return true;
            }

            // centre double‑tap: toggle play/pause + reset zoom
            resetZoom();
            if (isPlaying) {
                pause();
            } else {
                resume();
            }
            if (mPlaybackStateListener != null) {
                mPlaybackStateListener.onStateChanged(isPaused);
            }
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (mTouchLocked) return false;
            if (mClickListener != null) {
                mClickListener.onClick(IkVideoView.this);
            }
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (mTouchLocked) return false;
            if (!isScaling && currentZoomScale > 1.0f) {
                pan(-distanceX, -distanceY);
                return true;
            }
            return false;
        }
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            if (mTouchLocked) return false;
            isScaling = true;
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            if (mTouchLocked) return false;
            float scaleFactor = detector.getScaleFactor();
            float newScale = currentZoomScale * scaleFactor;
            newScale = Math.max(minZoom, Math.min(maxZoom, newScale));
            float focusX = detector.getFocusX();
            float focusY = detector.getFocusY();
            int viewWidth = textureView.getWidth();
            int viewHeight = textureView.getHeight();
            if (viewWidth == 0 || viewHeight == 0) return true;

            float viewCenterX = viewWidth / 2f;
            float viewCenterY = viewHeight / 2f;
            float dx = (focusX - viewCenterX - currentZoomTranslate.x) * (1 - scaleFactor);
            float dy = (focusY - viewCenterY - currentZoomTranslate.y) * (1 - scaleFactor);
            currentZoomTranslate.x += dx;
            currentZoomTranslate.y += dy;
            currentZoomScale = newScale;
            clampTranslation(viewWidth, viewHeight);
            updateTransform();
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            isScaling = false;
        }
    }

    // Public Listeners
    public void setOnPreparedListener(MediaPlayer.OnPreparedListener listener) {
        onPreparedListener = listener;
    }

    public void setOnErrorListener(MediaPlayer.OnErrorListener listener) {
        onErrorListener = listener;
    }

    public void setOnCompletionListener(MediaPlayer.OnCompletionListener listener) {
        onCompletionListener = listener;
    }

    public void setOnInfoListener(MediaPlayer.OnInfoListener listener) {
        onInfoListener = listener;
    }

    public void setClickListener(View.OnClickListener l) {
        mClickListener = l;
    }

    public void setPlaybackStateListener(OnPlaybackStateChangeListener l) {
        mPlaybackStateListener = l;
    }

    public void setSeekbarListener(OnSeekbarStateChangedListener l) {
        mSeekbarListener = l;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    // Playback Speed API
    public float getPlaybackSpeed() {
        return playbackSpeed;
    }
}