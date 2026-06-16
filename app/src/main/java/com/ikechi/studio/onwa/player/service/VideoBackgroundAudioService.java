package com.ikechi.studio.onwa.player.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.ikechi.studio.IkLog;
import com.ikechi.studio.onwa.player.MainActivity;
import com.ikechi.studio.onwa.player.R;
import com.ikechi.studio.onwa.player.utils.MediaDatabaseHelper;

public class VideoBackgroundAudioService extends Service
        implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnErrorListener,
        MediaPlayer.OnCompletionListener,
        AudioManager.OnAudioFocusChangeListener {

    private static final String TAG = "VideoBgAudio";
    private static final String CHANNEL_ID = "video_bg_audio_channel";
    private static final int NOTIFICATION_ID = 201;

    public static final String EXTRA_VIDEO_URI = "video_uri";
    public static final String ACTION_STOP = "stop";
    public static final String ACTION_PLAY_PAUSE = "play_pause";
    public static final String ACTION_PLAY = "play";
    public static final String ACTION_PAUSE = "pause";

    private MediaPlayer mediaPlayer;
    private Uri videoUri;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private PowerManager.WakeLock wakeLock;
    private MediaSession mediaSession;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isPlaying = false;
    private boolean isPaused = false;
    private int currentPosition = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            createNotificationChannel();
            audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            // Wake lock to keep CPU alive during background playback
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VideoBgAudio::WakeLock");
            wakeLock.setReferenceCounted(false);
            // MediaSession
            initMediaSession();
            IkLog.d(TAG, "Service created");
        } catch (Exception e) {
            IkLog.e(TAG, "Error in onCreate", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (intent == null) {
                IkLog.w(TAG, "onStartCommand received null intent");
                return START_NOT_STICKY;
            }

            String actionStr = intent.getAction();
            IkLog.d(TAG, "onStartCommand action=" + actionStr);

            if (ACTION_STOP.equals(actionStr)) {
                stopPlaybackAndSelf();
                return START_NOT_STICKY;
            }

            if (ACTION_PLAY_PAUSE.equals(actionStr)) {
                if (mediaPlayer != null && isPlaying) {
                    pausePlayback();
                } else if (mediaPlayer != null && !isPlaying && !isPaused && currentPosition > 0) {
                    resumePlayback();
                } else {
                    // If mediaPlayer is null or not prepared, we might still have a URI
                    if (videoUri != null && mediaPlayer == null) {
                        startPlayback();
                    }
                }
                return START_NOT_STICKY;
            }

            if (ACTION_PLAY.equals(actionStr)) {
                if (mediaPlayer != null && !isPlaying) {
                    resumePlayback();
                } else if (mediaPlayer == null && videoUri != null) {
                    startPlayback();
                }
                return START_NOT_STICKY;
            }

            if (ACTION_PAUSE.equals(actionStr)) {
                if (mediaPlayer != null && isPlaying) {
                    pausePlayback();
                }
                return START_NOT_STICKY;
            }

            String uriStr = intent.getStringExtra(EXTRA_VIDEO_URI);
            if (uriStr != null) {
                videoUri = Uri.parse(uriStr);
                startPlayback();
            } else {
                IkLog.w(TAG, "No video URI provided");
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error in onStartCommand", e);
            stopSelf();
        }
        return START_STICKY;
    }

    private void initMediaSession() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        try {
            mediaSession = new MediaSession(this, "VideoBackgroundAudio");
            mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS |
                    MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
            mediaSession.setCallback(new MediaSession.Callback() {
                @Override
                public void onPlay() {
                    if (mediaPlayer != null && !isPlaying) {
                        resumePlayback();
                    } else if (mediaPlayer == null && videoUri != null) {
                        startPlayback();
                    }
                }
                @Override
                public void onPause() {
                    if (mediaPlayer != null && isPlaying) {
                        pausePlayback();
                    }
                }
                @Override
                public void onStop() {
                    stopPlaybackAndSelf();
                }
            });
            updateMediaSessionState(PlaybackState.STATE_NONE);
            mediaSession.setActive(true);
            IkLog.d(TAG, "MediaSession initialized");
        } catch (Exception e) {
            IkLog.e(TAG, "Error initializing MediaSession", e);
        }
    }

    private void updateMediaSessionState(int state) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || mediaSession == null) return;
        try {
            long actions = PlaybackState.ACTION_PLAY_PAUSE |
                    PlaybackState.ACTION_PLAY |
                    PlaybackState.ACTION_PAUSE |
                    PlaybackState.ACTION_STOP;
            if (state == PlaybackState.STATE_PLAYING) {
                actions |= PlaybackState.ACTION_PAUSE;
            } else {
                actions |= PlaybackState.ACTION_PLAY;
            }
            PlaybackState.Builder builder = new PlaybackState.Builder()
                    .setActions(actions)
                    .setState(state, mediaPlayer != null ? mediaPlayer.getCurrentPosition() : 0,
                            1.0f, SystemClock.elapsedRealtime());
            mediaSession.setPlaybackState(builder.build());
        } catch (Exception e) {
            IkLog.e(TAG, "Error updating MediaSession state", e);
        }
    }

    private void startPlayback() {
        try {
            if (videoUri == null) {
                IkLog.e(TAG, "No video URI to play");
                stopSelf();
                return;
            }
            if (!requestAudioFocus()) {
                IkLog.w(TAG, "Could not obtain audio focus, continuing anyway");
            }
            if (mediaPlayer != null) releasePlayer();
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, videoUri);
            mediaPlayer.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                            .build());
            mediaPlayer.setOnPreparedListener(this);
            mediaPlayer.setOnErrorListener(this);
            mediaPlayer.setOnCompletionListener(this);
            mediaPlayer.prepareAsync();
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(10 * 60 * 1000L); // 10 minutes
            }
            IkLog.d(TAG, "Starting async prepare for " + videoUri);
        } catch (Exception e) {
            IkLog.e(TAG, "Failed to start background audio", e);
            stopSelf();
        }
    }

    private void resumePlayback() {
        if (mediaPlayer == null || isPlaying) return;
        try {
            if (!requestAudioFocus()) {
                IkLog.w(TAG, "Could not obtain audio focus on resume");
            }
            mediaPlayer.start();
            isPlaying = true;
            isPaused = false;
            updateMediaSessionState(PlaybackState.STATE_PLAYING);
            updateNotification();
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(10 * 60 * 1000L);
            }
            IkLog.d(TAG, "Playback resumed");
        } catch (Exception e) {
            IkLog.e(TAG, "Error resuming playback", e);
        }
    }

    private void pausePlayback() {
        if (mediaPlayer == null || !isPlaying) return;
        try {
            mediaPlayer.pause();
            currentPosition = mediaPlayer.getCurrentPosition();
            isPlaying = false;
            isPaused = true;
            updateMediaSessionState(PlaybackState.STATE_PAUSED);
            updateNotification();
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
            IkLog.d(TAG, "Playback paused");
        } catch (Exception e) {
            IkLog.e(TAG, "Error pausing playback", e);
        }
    }

    private void stopPlaybackAndSelf() {
        try {
            releasePlayer();
            abandonAudioFocus();
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            stopForeground(true);
            stopSelf();
            IkLog.d(TAG, "Playback stopped and service destroyed");
        } catch (Exception e) {
            IkLog.e(TAG, "Error during stop", e);
            stopSelf();
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        try {
            mp.start();
            isPlaying = true;
            isPaused = false;
            startForeground(NOTIFICATION_ID, buildNotification());
            updateMediaSessionState(PlaybackState.STATE_PLAYING);
            IkLog.d(TAG, "Playback started, foreground notification shown");
        } catch (Exception e) {
            IkLog.e(TAG, "Error in onPrepared", e);
            stopSelf();
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        IkLog.e(TAG, "MediaPlayer error " + what + " extra=" + extra);
        stopPlaybackAndSelf();
        return true;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        IkLog.d(TAG, "Playback completed, stopping service");
        // Optionally clear saved position in database
        if (videoUri != null) {
            MediaDatabaseHelper db = MediaDatabaseHelper.getInstance(this);
            db.updateVideoLastPosition(videoUri, 0);
        }
        stopPlaybackAndSelf();
    }

    @Override
    public void onDestroy() {
        try {
            releasePlayer();
            abandonAudioFocus();
            if (mediaSession != null) {
                mediaSession.setActive(false);
                mediaSession.release();
                mediaSession = null;
            }
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            stopForeground(true);
            IkLog.d(TAG, "Service destroyed");
        } catch (Exception e) {
            IkLog.e(TAG, "Error in onDestroy", e);
        }
        super.onDestroy();
    }

    private void releasePlayer() {
        try {
            if (mediaPlayer != null) {
                try { mediaPlayer.stop(); } catch (Exception ignored) {}
                mediaPlayer.release();
                mediaPlayer = null;
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error releasing player", e);
        }
    }

    // Audio focus handling
    private boolean requestAudioFocus() {
        if (audioManager == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest == null) {
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build();
                audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(attrs)
                        .setOnAudioFocusChangeListener(this)
                        .build();
            }
            int result = audioManager.requestAudioFocus(audioFocusRequest);
            return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        } else {
            int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);
            return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }
    }

    private void abandonAudioFocus() {
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        } else {
            audioManager.abandonAudioFocus(this);
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (mediaPlayer != null && isPlaying) {
                    pausePlayback();
                }
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                // Do not auto‑resume – let the user decide via controls
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                if (mediaPlayer != null && isPlaying) {
                    mediaPlayer.setVolume(0.2f, 0.2f);
                }
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                // restore volume later if needed, but we don't track
                break;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildNotification() {
        try {
            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder = new Notification.Builder(this, CHANNEL_ID);
            } else {
                builder = new Notification.Builder(this);
            }
            // Pending intent to return to MainActivity
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                    new Intent(this, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            // Play/Pause action
            int playPauseIcon = isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
            PendingIntent playPauseIntent = PendingIntent.getService(this, 0,
                    new Intent(this, VideoBackgroundAudioService.class).setAction(ACTION_PLAY_PAUSE),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            // Stop action
            PendingIntent stopIntent = PendingIntent.getService(this, 0,
                    new Intent(this, VideoBackgroundAudioService.class).setAction(ACTION_STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.setContentTitle("Video audio playing")
                    .setContentText("Tap to return")
                    .setSmallIcon(R.drawable.ic_video_placeholder)
                    .setOngoing(true)
                    .setContentIntent(contentIntent)
                    .addAction(playPauseIcon, "Play/Pause", playPauseIntent)
                    .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setStyle(new android.app.Notification.MediaStyle()
                        .setShowActionsInCompactView(0, 1));
            }
            return builder.build();
        } catch (Exception e) {
            IkLog.e(TAG, "Error building notification", e);
            return null;
        }
    }

    private void updateNotification() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(NOTIFICATION_ID, buildNotification());
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error updating notification", e);
        }
    }

    private void createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "Video background audio",
                        NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("Shows when video audio plays in background");
                channel.setShowBadge(false);
                NotificationManager mgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (mgr != null) mgr.createNotificationChannel(channel);
                IkLog.d(TAG, "Notification channel created");
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error creating notification channel", e);
        }
    }
}