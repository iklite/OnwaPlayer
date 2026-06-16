package com.ikechi.studio.onwa.player.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.PresetReverb;
import android.media.audiofx.Virtualizer;
import android.media.audiofx.Visualizer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.KeyEvent;

import com.ikechi.studio.IkLog;
import com.ikechi.studio.onwa.player.MainActivity;
import com.ikechi.studio.onwa.player.R;
import com.ikechi.studio.onwa.player.constants.RepeatMode;
import com.ikechi.studio.onwa.player.models.AudioItem;
import com.ikechi.studio.onwa.player.utils.FFTDataProcessor;
import com.ikechi.studio.onwa.player.utils.MediaDatabaseHelper;
import com.ikechi.studio.onwa.player.utils.MediaUtils;
import com.ikechi.studio.onwa.player.utils.PrefUtils;
import com.ikechi.studio.onwa.player.utils.SettingsManager;
import com.ikechi.studio.onwa.player.widget.WidgetDataHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class AudioPlayerService extends Service
        implements MediaPlayer.OnPreparedListener,
                   MediaPlayer.OnCompletionListener,
                   MediaPlayer.OnErrorListener,
                   AudioManager.OnAudioFocusChangeListener {

    private static final String TAG = "AudioPlayerService";

    // Broadcast actions
    public static final String ACTION_PLAYBACK_STARTED   = "com.ikechi.studio.onwa.player.PLAYBACK_STARTED";
    public static final String ACTION_PLAYBACK_PAUSED    = "com.ikechi.studio.onwa.player.PLAYBACK_PAUSED";
    public static final String ACTION_PLAYBACK_RESUMED   = "com.ikechi.studio.onwa.player.PLAYBACK_RESUMED";
    public static final String ACTION_PLAYBACK_STOPPED   = "com.ikechi.studio.onwa.player.PLAYBACK_STOPPED";
    public static final String ACTION_PLAYBACK_COMPLETED = "com.ikechi.studio.onwa.player.PLAYBACK_COMPLETED";
    public static final String ACTION_PLAYBACK_NEXT      = "com.ikechi.studio.onwa.player.PLAYBACK_NEXT";
    public static final String ACTION_PLAYBACK_PREVIOUS  = "com.ikechi.studio.onwa.player.PLAYBACK_PREVIOUS";
    public static final String ACTION_PLAYBACK_ERROR     = "com.ikechi.studio.onwa.player.PLAYBACK_ERROR";
    public static final String ACTION_SPECTRUM_DATA      = "com.ikechi.studio.onwa.player.SPECTRUM_DATA";
    public static final String ACTION_SHUFFLE_CHANGED    = "com.ikechi.studio.onwa.player.SHUFFLE_CHANGED";
    public static final String ACTION_REPEAT_MODE_CHANGED= "com.ikechi.studio.onwa.player.REPEAT_MODE_CHANGED";
    public static final String ACTION_METADATA_UPDATED   = "com.ikechi.studio.onwa.player.METADATA_UPDATED";
    public static final String ACTION_PLAYLIST_END_REACHED= "com.ikechi.studio.onwa.player.PLAYLIST_END_REACHED";
    public static final String ACTION_RESUME_LAST = "com.ikechi.studio.onwa.player.RESUME_LAST";

    // Widget sync action
    public static final String ACTION_WIDGET_DATA = "com.ikechi.studio.onwa.player.WIDGET_DATA";
    public static final String EXTRA_WIDGET_TITLE   = "widget_title";
    public static final String EXTRA_WIDGET_ARTIST  = "widget_artist";
    public static final String EXTRA_WIDGET_ART_URI = "widget_art_uri";
    public static final String EXTRA_WIDGET_IS_PLAYING = "widget_is_playing";

    // Extra keys
    public static final String EXTRA_URI = "uri";
    public static final String EXTRA_PLAYLIST = "playlist";
    public static final String EXTRA_PLAYLIST_POSITION = "playlist_position";
    public static final String EXTRA_ERROR = "error";
    public static final String EXTRA_SPECTRUM_DATA = "spectrum_data";
    public static final String EXTRA_SHUFFLE = "shuffle";
    public static final String EXTRA_REPEAT_MODE = "repeat_mode";
    public static final String EXTRA_SAMPLING_RATE = "sampling_rate";

    private static final String CHANNEL_ID = "onwa_player_channel";
    private static final int NOTIFICATION_ID = 101;
    private static final int MAX_CONSECUTIVE_ERRORS = 10;

    // Core components
    private MediaPlayer mediaPlayer;
    private Visualizer visualizer;
    private AudioManager audioManager;
    private FFTDataProcessor fftDataProcessor;
    private NotificationManager notificationManager;
    private Handler mainHandler;
    private MediaSession mediaSession;

    // Playback state
    private Uri currentUri;
    private String currentDisplayPath;
    private List<AudioItem> playlist;
    private List<AudioItem> originalPlaylist;
    private int currentPosition;
    private boolean isPlaying;
    private boolean isPaused;

    // Shuffle
    private boolean shuffle;

    // Other state
    private RepeatMode repeatMode = new RepeatMode(RepeatMode.REPEAT_MODE_NONE);
    private int consecutiveErrorCount = 0;

    // Metadata cache
    private String currentTitle = "Not Playing";
    private String currentArtist = "Unknown Artist";
    private String currentAlbum = "Unknown Album";
    private Bitmap currentAlbumArt;
    private byte[] currentAlbumArtBytes;

    private AudioFocusRequest audioFocusRequest;

    // Visualizer data
    private float[] spectrumData;

    // Wake lock
    private PowerManager.WakeLock wakeLock;

    // Sleep timer fade-out
    private Handler sleepHandler;
    private boolean fadingOut = false;

    // Binder
    private final IBinder binder = new AudioBinder();

    /**
     * Tracks whether the current pause was initiated by the user.
     * When true, audio focus regain will NOT resume playback.
     */
    private boolean userPaused = false;

    public class AudioBinder extends Binder {
        public AudioPlayerService getService() { return AudioPlayerService.this; }
    }

    // Notification receiver
    private final BroadcastReceiver notificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                String action = intent.getAction();
                if (action == null) return;
                IkLog.d(TAG, "Notification receiver action=" + action);
                switch (action) {
                    case "PLAY": playPause(); break;
                    case "PAUSE": pause(); break;
                    case "PLAY_PAUSE": playPause(); break;
                    case "NEXT": playNext(); break;
                    case "PREVIOUS": playPrevious(); break;
                    case "STOP":
                    case "CLOSE": stopPlayback(); break;
                }
            } catch (Exception e) {
                IkLog.e(TAG, "Error in notification receiver", e);
            }
        }
    };

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            IkLog.d(TAG, "Service onCreate");
            audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            mainHandler = new Handler(Looper.getMainLooper());
            fftDataProcessor = new FFTDataProcessor(64, 0.3f);
            createNotificationChannel();
            initializeMediaSession();
            playlist = new ArrayList<>();
            originalPlaylist = new ArrayList<>();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build();
                audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(playbackAttributes)
                        .setAcceptsDelayedFocusGain(true)
                        .setOnAudioFocusChangeListener(this)
                        .build();
            }

            IntentFilter filter = new IntentFilter();
            filter.addAction("PLAY");
            filter.addAction("PAUSE");
            filter.addAction("NEXT");
            filter.addAction("PREVIOUS");
            filter.addAction("STOP");
            filter.addAction("CLOSE");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(notificationReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(notificationReceiver, filter);
            }
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OnwaPlayer::AudioPlayback");
            wakeLock.setReferenceCounted(false);

            IkLog.d(TAG, "Service initialized successfully");
        } catch (Exception e) {
            IkLog.e(TAG, "Fatal error in onCreate", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (intent == null) {
                IkLog.w(TAG, "onStartCommand received null intent");
                return START_STICKY;
            }

            String action = intent.getAction();

            // ── Resume last playback (triggered by widget when service is dead) ──
            if (ACTION_RESUME_LAST.equals(action)) {
                String lastUri = PrefUtils.getLastUri(this);
                int lastPos = PrefUtils.getLastPosition(this);
                List<AudioItem> savedPlaylist = PrefUtils.getLastPlaylist(this);
                if (lastUri != null && savedPlaylist != null && !savedPlaylist.isEmpty()) {
                    if (lastPos < 0 || lastPos >= savedPlaylist.size()) lastPos = 0;
                    setPlaylist(savedPlaylist);
                    playAudioFromUri(Uri.parse(lastUri), lastPos);
                } else {
                    IkLog.w(TAG, "No last playback state to resume.");
                }
                return START_STICKY;
            }

            // ── Media button handling ───────────────────────────────────────
            if (Intent.ACTION_MEDIA_BUTTON.equals(action)) {
                KeyEvent key = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (key != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaSession != null) {
                        mediaSession.getController().dispatchMediaButtonEvent(key);
                    } else {
                        handleMediaButton(key);
                    }
                }
            }
            // ── Normal playback request ─────────────────────────────────────
            else if (intent.hasExtra(EXTRA_URI)) {
                String uriStr = intent.getStringExtra(EXTRA_URI);
                Uri uri = Uri.parse(uriStr);
                if (intent.hasExtra(EXTRA_PLAYLIST)) {
                    ArrayList<AudioItem> list = intent.getParcelableArrayListExtra(EXTRA_PLAYLIST);
                    if (list != null && !list.isEmpty()) {
                        setPlaylist(list);
                        int pos = indexOfUri(playlist, uri);
                        if (pos >= 0) {
                            currentPosition = pos;
                        } else {
                            int intentPos = intent.getIntExtra(EXTRA_PLAYLIST_POSITION, 0);
                            currentPosition = (intentPos >= 0 && intentPos < playlist.size()) ? intentPos : 0;
                        }
                    }
                }
                playAudioFromUri(uri, currentPosition);
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error in onStartCommand", e);
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            IkLog.d(TAG, "Service onDestroy");
            recordCurrentHistory();
            if (currentUri != null) {
                PrefUtils.setLastUri(this, currentUri.toString());
                PrefUtils.setLastPosition(this, currentPosition);
                PrefUtils.setLastSeek(this, getCurrentInTrackPosition());
            }
            cancelSleepFadeOut();
            releaseCurrentPlayer();
            releaseVisualizer();
            if (audioManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                    audioManager.abandonAudioFocusRequest(audioFocusRequest);
                } else {
                    audioManager.abandonAudioFocus(this);
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaSession != null) {
                mediaSession.setActive(false);
                mediaSession.release();
                mediaSession = null;
            }
            try { unregisterReceiver(notificationReceiver); } catch (Exception ignored) {}
            mainHandler.removeCallbacksAndMessages(null);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
            notificationManager.cancel(NOTIFICATION_ID);
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
            sendWidgetData(false);
            IkLog.d(TAG, "Service destroyed cleanly");
        } catch (Exception e) {
            IkLog.e(TAG, "Error in onDestroy", e);
        }
    }

    // =========================================================================
    // Playback control
    // =========================================================================

    private void releaseCurrentPlayer() {
        if (mediaPlayer == null) return;
        try {
            IkLog.d(TAG, "Releasing current player");
            MediaPlayer old = mediaPlayer;
            mediaPlayer = null;

            try { if (old.isPlaying()) old.stop(); } catch (Exception ignored) {}

            old.release();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            } else {
                audioManager.abandonAudioFocus(this);
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error releasing player", e);
        }
    }

    public void playAudioFromUri(Uri uri, int position) {
        try {
            if (uri == null || playlist.isEmpty()) {
                IkLog.e(TAG, "playAudioFromUri: invalid uri or empty playlist");
                return;
            }

            IkLog.d(TAG, "playAudioFromUri: " + uri + " pos=" + position);
            recordCurrentHistory();

            releaseCurrentPlayer();
            cancelSleepFadeOut();

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, uri);
            mediaPlayer.setOnPreparedListener(this);
            mediaPlayer.setOnCompletionListener(this);
            mediaPlayer.setOnErrorListener(this);

            mediaPlayer.prepareAsync();
            currentUri = uri;
            currentPosition = position;
            currentDisplayPath = playlist.get(position).getFilePath();

            int result;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                result = audioManager.requestAudioFocus(audioFocusRequest);
            } else {
                result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN);
            }
            isPlaying = false;
            isPaused = false;
            userPaused = false;

            AudioItem item = playlist.get(currentPosition);
            int playCount = item.getPlayCount();
            playCount++; item.setPlayCount(playCount);

            int index = indexOfUri(originalPlaylist, currentUri);
            if (index >= 0) originalPlaylist.get(index).setPlayCount(playCount);

            MediaDatabaseHelper dbHelper = MediaDatabaseHelper.getInstance(getApplicationContext());
            dbHelper.insertOrUpdateAudio(item, System.currentTimeMillis());

            startForeground(NOTIFICATION_ID, buildNotification());

            Intent startIntent = new Intent(ACTION_PLAYBACK_STARTED);
            startIntent.setPackage(getPackageName());
            startIntent.putExtra(EXTRA_URI, uri.toString());
            sendBroadcast(startIntent);

            currentTitle = item.getTitle();
            currentArtist = item.getArtist();
            currentAlbumArtBytes = item.getAlbumArtBytes();
            sendWidgetData(false);   // isPlaying = false until prepared
            extractMetadataAsync(uri);

        } catch (IOException e) {
            IkLog.e(TAG, "IOException playing " + uri, e);
            sendPlaybackError("Unable to play: " + uri);
            isPlaying = false;
            isPaused = false;
            stopForeground(true);
        } catch (Exception e) {
            IkLog.e(TAG, "Unexpected error in playUri", e);
            sendPlaybackError("Playback error");
            isPlaying = false;
            isPaused = false;
            stopForeground(true);
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        if (mp != mediaPlayer) return;
        try {
            consecutiveErrorCount = 0;
            int sessionId = mp.getAudioSessionId();

            // ★ Restore saved audio effects to the new session
            restoreAudioEffects(sessionId);

            mp.start();
            isPlaying = true;
            isPaused = false;
            userPaused = false;
            setupVisualizer(sessionId);
            startForeground(NOTIFICATION_ID, buildNotification());
            updateNotification();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                updateMediaSessionState(PlaybackState.STATE_PLAYING);

            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(10*60*1000L);
            }

            // Broadcast the audio session ID so the EqualizerFragment can use it
            Intent sessionIntent = new Intent("AUDIO_SESSION_ID_UPDATED");
            sessionIntent.setPackage(getPackageName());
            sessionIntent.putExtra("audio_session_id", sessionId);
            sendBroadcast(sessionIntent);

            sendWidgetData(true);
            IkLog.d(TAG, "onPrepared: started playback successfully");
        } catch (Exception e) {
            IkLog.e(TAG, "Error in onPrepared", e);
        }
    }

    // =========================================================================
    // ★ NEW: Restore saved audio effects to a given audio session
    // =========================================================================

    private void restoreAudioEffects(int audioSessionId) {
        Equalizer eq = null;
        BassBoost bb = null;
        Virtualizer virt = null;
        PresetReverb rev = null;
        try {
            // ── Equalizer bands ──────────────────────────────────────────
            eq = new Equalizer(0, audioSessionId);
            eq.setEnabled(true);

            SharedPreferences bandPrefs = getSharedPreferences("eq_bands", Context.MODE_PRIVATE);
            short numBands = eq.getNumberOfBands();
            for (short band = 0; band < numBands; band++) {
                short level = (short) bandPrefs.getInt("band_" + band, 0);
                eq.setBandLevel(band, level);
            }

            // Restore preset index and apply it
            int savedPreset = PrefUtils.getEqualizerPreset(this);
            if (savedPreset > 0 && savedPreset < eq.getNumberOfPresets()) {
                eq.usePreset((short) savedPreset);
            }

            IkLog.d(TAG, "restoreAudioEffects: equalizer bands & preset restored");

            // ── Bass Boost ───────────────────────────────────────────────
            bb = new BassBoost(0, audioSessionId);
            bb.setEnabled(true);
            int bbStrength = PrefUtils.getBassBoost(this);
            bb.setStrength((short) bbStrength);
            IkLog.d(TAG, "restoreAudioEffects: bass boost set to " + bbStrength);

            // ── Virtualizer ──────────────────────────────────────────────
            virt = new Virtualizer(0, audioSessionId);
            virt.setEnabled(true);
            // Virtualizer strength is not persisted per‑session, just enable it

            // ── Reverb ───────────────────────────────────────────────────
            rev = new PresetReverb(0, audioSessionId);
            rev.setEnabled(true);
            // Reverb preset is not persisted separately; just enable

        } catch (Exception e) {
            IkLog.e(TAG, "Error restoring audio effects", e);
        } finally {
            // Release the Java objects – the native parameters remain on the session
            if (eq != null) { eq.release(); }
            if (bb != null)  { bb.release(); }
            if (virt != null) { virt.release(); }
            if (rev != null) { rev.release(); }
        }
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (mp != mediaPlayer) return;
        IkLog.d(TAG, "onCompletion");
        recordHistoryForCurrentTrack(true);

        // Update widget to non-playing state immediately
        sendWidgetData(false);

        Intent completeIntent = new Intent(ACTION_PLAYBACK_COMPLETED);
        completeIntent.setPackage(getPackageName());
        sendBroadcast(completeIntent);

        if (repeatMode.getMode() == RepeatMode.REPEAT_MODE_ONE) {
            playAudioFromUri(currentUri, currentPosition);
        } else {
            playNext();
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        if (mp != mediaPlayer) return true;
        IkLog.e(TAG, "MediaPlayer error what=" + what + " extra=" + extra);
        sendPlaybackError("Playback error: " + what);

        releaseCurrentPlayer();
        releaseVisualizer();
        isPlaying = false;
        isPaused = false;
        userPaused = false;

        sendWidgetData(false);
        playNext();
        return true;
    }

    public void pause() {
        try {
            if (mediaPlayer == null || !isPlaying) return;
            mediaPlayer.pause();
            isPlaying = false;
            isPaused = true;
            userPaused = true;
            updateNotification();

            Intent pauseIntent = new Intent(ACTION_PLAYBACK_PAUSED);
            pauseIntent.setPackage(getPackageName());
            sendBroadcast(pauseIntent);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                updateMediaSessionState(PlaybackState.STATE_PAUSED);
            sendWidgetData(false);
            IkLog.d(TAG, "Paused by user");
        } catch (Exception e) {
            IkLog.e(TAG, "Error pausing", e);
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    public void resume() {
        try {
            if (mediaPlayer == null || !isPaused) return;
            mediaPlayer.start();
            isPlaying = true;
            isPaused = false;
            userPaused = false;
            startForeground(NOTIFICATION_ID, buildNotification());
            updateNotification();

            Intent resumeIntent = new Intent(ACTION_PLAYBACK_RESUMED);
            resumeIntent.setPackage(getPackageName());
            sendBroadcast(resumeIntent);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                updateMediaSessionState(PlaybackState.STATE_PLAYING);
            sendWidgetData(true);
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(10*60*1000L);
            }
            IkLog.d(TAG, "Resumed");
        } catch (Exception e) {
            IkLog.e(TAG, "Error resuming", e);
        }
    }

    public void playPause() {
        if (isPlaying) {
            pause();
        } else if (isPaused) {
            resume();
        } else if (!playlist.isEmpty()) {
            if (currentPosition < 0 || currentPosition >= playlist.size()) currentPosition = 0;
            playAudioFromUri(playlist.get(currentPosition).getUri(), currentPosition);
        }
    }

    public void playNext() {
        try {
            if (playlist.isEmpty()) return;

            Intent nextIntent = new Intent(ACTION_PLAYBACK_NEXT);
            nextIntent.setPackage(getPackageName());
            sendBroadcast(nextIntent);

            recordCurrentHistory();
            currentPosition++;
            if (currentPosition >= playlist.size()) {
                if (repeatMode.getMode() == RepeatMode.REPEAT_MODE_ALL) {
                    if (shuffle) {
                        Collections.shuffle(playlist);
                    }
                    currentPosition = 0;
                } else {
                    Intent endIntent = new Intent(ACTION_PLAYLIST_END_REACHED);
                    endIntent.setPackage(getPackageName());
                    sendBroadcast(endIntent);
                    stopPlayback();
                    return;
                }
            }
            Uri nextUri = playlist.get(currentPosition).getUri();
            playAudioFromUri(nextUri, currentPosition);
        } catch (Exception e) {
            IkLog.e(TAG, "Error in playNext", e);
        }
    }

    public void playPrevious() {
        try {
            if (playlist.isEmpty()) return;
            if (mediaPlayer != null && getCurrentInTrackPosition() > 3000) {
                mediaPlayer.seekTo(0);

                Intent prevIntent = new Intent(ACTION_PLAYBACK_PREVIOUS);
                prevIntent.setPackage(getPackageName());
                sendBroadcast(prevIntent);
                return;
            }

            Intent prevIntent = new Intent(ACTION_PLAYBACK_PREVIOUS);
            prevIntent.setPackage(getPackageName());
            sendBroadcast(prevIntent);

            recordCurrentHistory();
            currentPosition--;
            if (currentPosition < 0) {
                currentPosition = (repeatMode.getMode() == RepeatMode.REPEAT_MODE_ALL)
                        ? playlist.size() - 1 : 0;
            }
            playAudioFromUri(playlist.get(currentPosition).getUri(), currentPosition);
        } catch (Exception e) {
            IkLog.e(TAG, "Error in playPrevious", e);
        }
    }

    private void stopPlayback() {
        try {
            IkLog.d(TAG, "stopPlayback");
            recordCurrentHistory();
            cancelSleepFadeOut();
            releaseCurrentPlayer();
            releaseVisualizer();
            isPlaying = false;
            isPaused = false;
            userPaused = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(Service.STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }

            Intent stopIntent = new Intent(ACTION_PLAYBACK_STOPPED);
            stopIntent.setPackage(getPackageName());
            sendBroadcast(stopIntent);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                updateMediaSessionState(PlaybackState.STATE_STOPPED);
            sendWidgetData(false);
        } catch (Exception e) {
            IkLog.e(TAG, "Error stopping playback", e);
        }
    }

    // =========================================================================
    // Listening history recording (for stats)
    // =========================================================================

    private void recordHistoryForCurrentTrack(boolean completed) {
        if (currentUri == null) return;
        try {
            long listenedMs;
            if (completed) {
                listenedMs = getDuration();
                if (listenedMs <= 0) listenedMs = mediaPlayer != null ? getDurationFallback() : 0;
            } else {
                listenedMs = getCurrentInTrackPosition();
            }
            if (listenedMs > 500) {
                MediaDatabaseHelper.getInstance(this).recordPlayHistory(
                        currentUri.toString(), listenedMs);
                IkLog.d(TAG, "Recorded history: " + listenedMs + "ms for " + currentUri);
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error recording play history", e);
        }
    }

    private void recordCurrentHistory() {
        recordHistoryForCurrentTrack(false);
    }

    private long getDurationFallback() {
        if (mediaPlayer != null) {
            try { return mediaPlayer.getDuration(); } catch (Exception ignored) {}
        }
        return 0;
    }

    // =========================================================================
    // Widget helper
    // =========================================================================

    private void sendWidgetData(boolean playing) {
        try {
            WidgetDataHelper.save(this, currentTitle, currentArtist, currentAlbumArt, playing);

            Intent intent = new Intent(ACTION_WIDGET_DATA);
            intent.setPackage(getPackageName());
            intent.putExtra(EXTRA_WIDGET_TITLE, currentTitle);
            intent.putExtra(EXTRA_WIDGET_ARTIST, currentArtist);
            intent.putExtra(EXTRA_WIDGET_IS_PLAYING, playing);
            sendBroadcast(intent);
        } catch (Exception e) {
            IkLog.e(TAG, "Error sending widget data", e);
        }
    }

    // =========================================================================
    // Visualizer
    // =========================================================================

    private void setupVisualizer(int audioSessionId) {
        try {
            releaseVisualizer();
            visualizer = new Visualizer(audioSessionId);
            int[] range = Visualizer.getCaptureSizeRange();
            if (range == null || range.length == 0 || range[1] <= 0) {
                IkLog.w(TAG, "Visualizer not supported");
                releaseVisualizer();
                return;
            }
            final int captureSize = range[1];
            visualizer.setCaptureSize(captureSize);
            int rate = Visualizer.getMaxCaptureRate() / 2;
            if (rate <= 0) rate = 5000;

            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer vis, byte[] waveform, int samplingRate) {
                    try {
                        float[] processed = fftDataProcessor.process(waveform, captureSize);
                        spectrumData = processed;
                        Intent intent = new Intent(ACTION_SPECTRUM_DATA);
                        intent.setPackage(getPackageName());
                        intent.putExtra(EXTRA_SPECTRUM_DATA, processed);
                        intent.putExtra(EXTRA_SAMPLING_RATE, samplingRate);
                        sendBroadcast(intent);
                    } catch (Exception e) {
                        IkLog.e(TAG, "Error in visualizer data capture", e);
                    }
                }
                @Override public void onFftDataCapture(Visualizer vis, byte[] fft, int rate) {}
            }, rate, true, false);

            visualizer.setEnabled(true);
            IkLog.d(TAG, "Visualizer setup complete");
        } catch (Exception e) {
            IkLog.e(TAG, "Error setting up Visualizer", e);
            releaseVisualizer();
        }
    }

    private void releaseVisualizer() {
        if (visualizer == null) return;
        try {
            visualizer.setEnabled(false);
            visualizer.release();
            visualizer = null;
            spectrumData = null;
            IkLog.d(TAG, "Visualizer released");
        } catch (Exception e) {
            IkLog.e(TAG, "Error releasing visualizer", e);
        }
    }

    public void setPlaybackSpeed(float speed) {
        if (mediaPlayer == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                PlaybackParams params = new PlaybackParams();
                params.setSpeed(speed);
                mediaPlayer.setPlaybackParams(params);
                IkLog.d(TAG, "Playback speed set to " + speed);
            } catch (Exception e) {
                IkLog.e(TAG, "Error setting playback speed", e);
            }
        }
    }

    // =========================================================================
    // Sleep Timer Fade‑out
    // =========================================================================

    public void startSleepFadeOut(long delayMs) {
        try {
            cancelSleepFadeOut();
            sleepHandler = new Handler(Looper.getMainLooper());
            sleepHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mediaPlayer != null && isPlaying) {
                        fadingOut = true;
                        beginFadeOut();
                    }
                }
            }, delayMs);
            IkLog.d(TAG, "Sleep timer started: " + delayMs + "ms");
        } catch (Exception e) {
            IkLog.e(TAG, "Error starting sleep timer", e);
        }
    }

    public void cancelSleepFadeOut() {
        try {
            if (sleepHandler != null) {
                sleepHandler.removeCallbacksAndMessages(null);
            }
            fadingOut = false;
        } catch (Exception e) {
            IkLog.e(TAG, "Error canceling sleep fade out", e);
        }
    }

    private void beginFadeOut() {
        try {
            final int steps = 20;
            final long stepDuration = 250;
            final float startVolume = 1.0f;
            final float stepVolume = startVolume / steps;

            Runnable fadeStep = new Runnable() {
                int step = 0;
                float currentVol = startVolume;

                @Override
                public void run() {
                    if (mediaPlayer == null || !fadingOut || !isPlaying) {
                        fadingOut = false;
                        return;
                    }
                    step++;
                    currentVol -= stepVolume;
                    if (currentVol < 0) currentVol = 0;
                    mediaPlayer.setVolume(currentVol, currentVol);

                    if (step >= steps || currentVol <= 0) {
                        mediaPlayer.pause();
                        isPlaying = false;
                        isPaused = true;
                        userPaused = true;
                        updateNotification();
                        sendWidgetData(false);
                        fadingOut = false;
                        IkLog.d(TAG, "Sleep fade-out complete");
                    } else {
                        sleepHandler.postDelayed(this, stepDuration);
                    }
                }
            };
            sleepHandler.post(fadeStep);
        } catch (Exception e) {
            IkLog.e(TAG, "Error during fade-out", e);
        }
    }

    // =========================================================================
    // Metadata extraction
    // =========================================================================

    private void extractMetadataAsync(final Uri uri) {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final MediaUtils.AudioMetadata meta = MediaUtils.extractMetadata(getApplicationContext(), uri, true);
                    final Bitmap artBitmap;
                    if (meta.albumArtBytes != null && meta.albumArtBytes.length > 0) {
                        Bitmap decoded = BitmapFactory.decodeByteArray(
                                meta.albumArtBytes, 0, meta.albumArtBytes.length);
                        artBitmap = (decoded != null) ? decoded : getDefaultAlbumArt();
                    } else {
                        artBitmap = getDefaultAlbumArt();
                    }
                    mainHandler.post(new Runnable() {
                        @Override public void run() {
                            try {
                                if (!uri.equals(currentUri)) return;

                                if (meta.title != null && !meta.title.isEmpty()
                                        && !"Unknown".equalsIgnoreCase(meta.title)) {
                                    currentTitle = meta.title;
                                }
                                if (meta.artist != null && !meta.artist.isEmpty()
                                        && !"Unknown Artist".equalsIgnoreCase(meta.artist)) {
                                    currentArtist = meta.artist;
                                }
                                if (meta.album != null && !meta.album.isEmpty()
                                        && !"Unknown Album".equalsIgnoreCase(meta.album)) {
                                    currentAlbum = meta.album;
                                }

                                currentAlbumArt = artBitmap;
                                currentAlbumArtBytes = meta.albumArtBytes;

                                Intent metaIntent = new Intent(ACTION_METADATA_UPDATED);
                                metaIntent.setPackage(getPackageName());
                                metaIntent.putExtra(EXTRA_URI, uri.toString());
                                sendBroadcast(metaIntent);

                                updateNotification();
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                                    updateMediaSessionMetadata();
                                sendWidgetData(isPlaying);
                            } catch (Exception e) {
                                IkLog.e(TAG, "Error updating metadata on UI", e);
                            }
                        }
                    });
                } catch (Exception e) {
                    IkLog.e(TAG, "Error extracting metadata", e);
                }
            }
        }, "metadata-extractor").start();
    }

    // =========================================================================
    // Audio focus
    // =========================================================================

    @Override
    public void onAudioFocusChange(int focusChange) {
        try {
            if (mediaPlayer == null) return;
            IkLog.d(TAG, "Audio focus change: " + focusChange);
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    if (isPaused) {
                        if (!userPaused) {
                            resume();
                        }
                    } else if (!isPlaying && !isPaused) {
                        mediaPlayer.start();
                        isPlaying = true;
                        isPaused = false;
                        userPaused = false;
                        updateNotification();

                        Intent resumeIntent = new Intent(ACTION_PLAYBACK_RESUMED);
                        resumeIntent.setPackage(getPackageName());
                        sendBroadcast(resumeIntent);

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                            updateMediaSessionState(PlaybackState.STATE_PLAYING);
                        sendWidgetData(true);
                    }
                    mediaPlayer.setVolume(1.0f, 1.0f);
                    break;

                case AudioManager.AUDIOFOCUS_LOSS:
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    if (isPlaying) {
                        mediaPlayer.pause();
                        isPlaying = false;
                        isPaused = true;
                        updateNotification();

                        Intent pauseIntent = new Intent(ACTION_PLAYBACK_PAUSED);
                        pauseIntent.setPackage(getPackageName());
                        sendBroadcast(pauseIntent);

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                            updateMediaSessionState(PlaybackState.STATE_PAUSED);
                        sendWidgetData(false);
                    }
                    break;

                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    if (isPlaying) {
                        mediaPlayer.setVolume(0.2f, 0.2f);
                    }
                    break;
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error handling audio focus change", e);
        }
    }

    // =========================================================================
    // MediaSession + Notification
    // =========================================================================

    private void initializeMediaSession() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        try {
            mediaSession = new MediaSession(this, "Onwa Media Player");
            mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                    | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
            mediaSession.setCallback(new MediaSession.Callback() {
                @Override public void onPlay() { playPause(); }
                @Override public void onPause() { pause(); }
                @Override public void onSkipToNext() { playNext(); }
                @Override public void onSkipToPrevious() { playPrevious(); }
                @Override public void onStop() { stopPlayback(); }
                @Override public void onSeekTo(long pos) {
                    if (mediaPlayer != null) {
                        try { mediaPlayer.seekTo((int) pos); } catch (Exception ignored) {
                            IkLog.e(TAG, "Error seeking in media session");
                        }
                    }
                }
            });
            updateMediaSessionState(PlaybackState.STATE_NONE);
            mediaSession.setActive(true);
            IkLog.d(TAG, "MediaSession initialized");
        } catch (Exception e) {
            IkLog.e(TAG, "Error initialising MediaSession", e);
        }
    }

    private void updateMediaSessionState(int state) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || mediaSession == null) return;
        try {
            long actions = PlaybackState.ACTION_PLAY_PAUSE
                    | PlaybackState.ACTION_SKIP_TO_NEXT
                    | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                    | PlaybackState.ACTION_STOP
                    | PlaybackState.ACTION_SEEK_TO;
            actions |= (state == PlaybackState.STATE_PLAYING)
                    ? PlaybackState.ACTION_PAUSE : PlaybackState.ACTION_PLAY;

            mediaSession.setPlaybackState(new PlaybackState.Builder()
                    .setActions(actions)
                    .setState(state, getCurrentInTrackPosition(), 1.0f, SystemClock.elapsedRealtime())
                    .build());
        } catch (Exception e) {
            IkLog.e(TAG, "Error updating MediaSession state", e);
        }
    }

    private void updateMediaSessionMetadata() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || mediaSession == null) return;
        try {
            MediaMetadata.Builder mb = new MediaMetadata.Builder();
            mb.putString(MediaMetadata.METADATA_KEY_TITLE, currentTitle);
            mb.putString(MediaMetadata.METADATA_KEY_ARTIST, currentArtist);
            mb.putString(MediaMetadata.METADATA_KEY_ALBUM, currentAlbum);
            mb.putLong(MediaMetadata.METADATA_KEY_DURATION, getDuration());
            if (currentAlbumArt != null)
                mb.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, currentAlbumArt);
            mediaSession.setMetadata(mb.build());
        } catch (Exception e) {
            IkLog.e(TAG, "Error updating MediaSession metadata", e);
        }
    }

    private void updateNotification() {
        try {
            notificationManager.notify(NOTIFICATION_ID, buildNotification());
        } catch (Exception e) {
            IkLog.e(TAG, "Error updating notification", e);
        }
    }

    private Notification buildNotification() {
        try {
            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder = new Notification.Builder(this, CHANNEL_ID);
            } else {
                builder = new Notification.Builder(this);
            }

            builder.setContentTitle(currentTitle)
                    .setContentText(currentArtist)
                    .setSmallIcon(R.drawable.ic_music_note)
                    .setContentIntent(getMainActivityPendingIntent())
                    .setOngoing(true)
                    .setWhen(0)
                    .setOnlyAlertOnce(true)
                    .setShowWhen(false);

            builder.addAction(R.drawable.ic_previous, "Previous", getPendingIntent("PREVIOUS"));

            int ppIcon = isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
            String ppLabel = isPlaying ? "Pause" : "Play";
            builder.addAction(ppIcon, ppLabel, getPendingIntent(isPlaying ? "PAUSE" : "PLAY"));

            builder.addAction(R.drawable.ic_next, "Next", getPendingIntent("NEXT"));
            builder.addAction(R.drawable.ic_close, "Close", getPendingIntent("CLOSE"));

            if (currentAlbumArt != null) {
                builder.setLargeIcon(currentAlbumArt);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaSession != null) {
                builder.setStyle(new Notification.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));
            }

            return builder.build();
        } catch (Exception e) {
            IkLog.e(TAG, "Error building notification", e);
            return null;
        }
    }

    private PendingIntent getPendingIntent(String action) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        Intent intent = new Intent(action);
        intent.setPackage(getPackageName());  // Make it explicit
        return PendingIntent.getBroadcast(this, 0, intent, flags);
    }

    private PendingIntent getMainActivityPendingIntent() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(this, 0, intent, flags);
    }

    private void createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL_ID, "Onwa Media Pro", NotificationManager.IMPORTANCE_LOW);
                ch.setDescription("Audio playback controls");
                ch.setShowBadge(false);
                ch.setSound(null, null);
                notificationManager.createNotificationChannel(ch);
                IkLog.d(TAG, "Notification channel created");
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error creating notification channel", e);
        }
    }

    // =========================================================================
    // Shuffle helpers
    // =========================================================================

    private void applyShuffleToPlaylist() {
        if (playlist.isEmpty()) return;
        Collections.shuffle(playlist);
        int newPos = findPositionOfCurrentUri();
        currentPosition = (newPos >= 0 && newPos < playlist.size()) ? newPos : 0;
    }

    private int findPositionOfCurrentUri() {
        return indexOfUri(playlist, currentUri);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Bitmap getDefaultAlbumArt() {
        int[] arts = {
            R.drawable.default_album_art, R.drawable.default_album_art1,
            R.drawable.default_album_art2, R.drawable.default_album_art3,
            R.drawable.default_album_art4
        };
        return BitmapFactory.decodeResource(getResources(), arts[new Random().nextInt(arts.length)]);
    }

    private void handleMediaButton(KeyEvent key) {
        if (key.getAction() != KeyEvent.ACTION_DOWN) return;
        switch (key.getKeyCode()) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: playPause(); break;
            case KeyEvent.KEYCODE_MEDIA_NEXT: playNext(); break;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS: playPrevious(); break;
            case KeyEvent.KEYCODE_MEDIA_STOP: stopPlayback(); break;
        }
    }

    // =========================================================================
    // Public API
    // =========================================================================

    public void setPlaylist(List<AudioItem> list) {
        this.originalPlaylist = new ArrayList<AudioItem>(list);
        this.playlist = new ArrayList<AudioItem>(list);
        if (shuffle) applyShuffleToPlaylist();
        PrefUtils.saveLastPlaylist(this, list);
    }

    public List<AudioItem> getPlaylist() {
        return new ArrayList<AudioItem>(originalPlaylist);
    }

    public List<AudioItem> getActivePlaylist() {
        return new ArrayList<AudioItem>(playlist);
    }

    public void setShuffle(boolean enabled) {
        this.shuffle = enabled;
        if (enabled) {
            applyShuffleToPlaylist();
        } else {
            this.playlist = new ArrayList<AudioItem>(originalPlaylist);
        }
        int newPos = findPositionOfCurrentUri();
        currentPosition = (newPos >= 0 && newPos < this.playlist.size()) ? newPos : 0;

        Intent shuffleIntent = new Intent(ACTION_SHUFFLE_CHANGED);
        shuffleIntent.setPackage(getPackageName());
        shuffleIntent.putExtra(EXTRA_SHUFFLE, shuffle);
        sendBroadcast(shuffleIntent);
    }

    public boolean isShuffle() { return shuffle; }

    public void setRepeatMode(RepeatMode mode) {
        this.repeatMode.setMode(mode.getMode());

        Intent repeatIntent = new Intent(ACTION_REPEAT_MODE_CHANGED);
        repeatIntent.setPackage(getPackageName());
        repeatIntent.putExtra(EXTRA_REPEAT_MODE, this.repeatMode.getMode());
        sendBroadcast(repeatIntent);
    }

    public RepeatMode getRepeatMode() { return repeatMode; }

    public void seekTo(int position) {
        if (mediaPlayer != null) {
            try { mediaPlayer.seekTo(position); } catch (IllegalStateException ignored) {
                IkLog.w(TAG, "seekTo failed");
            }
        }
    }

    public int getCurrentInTrackPosition() {
        if (mediaPlayer != null) {
            try { return mediaPlayer.getCurrentPosition(); } catch (IllegalStateException ignored) {}
        }
        return 0;
    }

    public int getDuration() {
        if (mediaPlayer != null && isPlaying) {
            try {
                return mediaPlayer.getDuration();
            } catch (IllegalStateException e) {
                IkLog.e(TAG, "Error getting duration", e);
                return 0;
            }
        }
        return 0;
    }

    public boolean isPlaying() { return isPlaying; }
    public boolean isPaused() { return isPaused; }
    public Uri getCurrentUri() { return currentUri; }
    public String getCurrentFilePath() { return currentDisplayPath; }
    public String getCurrentTitle() { return currentTitle; }
    public String getCurrentArtist() { return currentArtist; }
    public String getCurrentAlbum() { return currentAlbum; }
    public Bitmap getCurrentAlbumArt() { return currentAlbumArt; }
    public int getCurrentPlaylistPosition() { return currentPosition; }

    public float[] getSpectrumData() {
        return (spectrumData != null) ? spectrumData.clone() : new float[64];
    }

    public int getAudioSessionId() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.getAudioSessionId();
            } catch (IllegalStateException ignored) {
            }
        }
        return 0;
    }

    private int indexOfUri(List<AudioItem> list, Uri uri) {
        if (list == null || uri == null) return -1;
        for (int i = 0; i < list.size(); i++) {
            if (uri.equals(list.get(i).getUri())) return i;
        }
        return -1;
    }

    private void sendPlaybackError(String error) {
        try {
            Intent errorIntent = new Intent(ACTION_PLAYBACK_ERROR);
            errorIntent.setPackage(getPackageName());
            errorIntent.putExtra(EXTRA_ERROR, error);
            sendBroadcast(errorIntent);
        } catch (Exception e) {
            IkLog.e(TAG, "Error sending playback error broadcast", e);
        }
    }
}