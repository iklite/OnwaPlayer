package com.ikechi.studio.onwa.player;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.ikechi.studio.IkLog;
import com.ikechi.studio.onwa.player.constants.RepeatMode;
import com.ikechi.studio.onwa.player.fragment.AudioPlayerFragment;
import com.ikechi.studio.onwa.player.fragment.ImageViewerFragment;
import com.ikechi.studio.onwa.player.fragment.SettingsFragment;
import com.ikechi.studio.onwa.player.fragment.SharePlaybackFragment;
import com.ikechi.studio.onwa.player.fragment.UserSetupFragment;
import com.ikechi.studio.onwa.player.fragment.VideoPlayerFragment;
import com.ikechi.studio.onwa.player.models.AudioItem;
import com.ikechi.studio.onwa.player.net.WiFiDirectManager;
import com.ikechi.studio.onwa.player.service.AudioPlayerService;
import com.ikechi.studio.onwa.player.utils.PrefUtils;
import com.ikechi.studio.onwa.player.utils.SettingsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity
implements UserSetupFragment.OnSetupCompleteListener {

    private static final String TAG = "MainActivity";

    private static final int TAB_AUDIO   = 0;
    private static final int TAB_VIDEO   = 1;
    private static final int TAB_IMAGE   = 2;
    private static final int TAB_SHARE   = 3;
    private static final int TAB_SETTINGS = 4;


    // UI
    private LinearLayout tabContainer;
    private LinearLayout[] tabButtons;
    private TextView[] tabLabels;
    private ImageButton actionBarSettingsBtn, btnRefresh;
    private LinearLayout searchView;
    private int currentTab = -1;

    // Audio list
    private List<AudioItem> cachedAudioList;

    // Fragment management
    private Fragment currentFragment;
    private Fragment activeFragment;
    private final SparseArray<Fragment> fragmentCache = new SparseArray<>();

    // Audio service
    private AudioPlayerService audioService;
    private boolean isServiceBound = false;

    private boolean isSearchViewActive = false;

    private int appClosingCounter = 0;
    private boolean closeTimerScheduled = false;
    private Timer appClosingTimer;
    private TimerTask closingTimerTask;

    // WiFi Direct
    private WiFiDirectManager mWiFiDirectManager;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                audioService = ((AudioPlayerService.AudioBinder) service).getService();
                isServiceBound = true;
                IkLog.d(TAG, "Service connected");
                notifyActiveFragmentServiceConnected(true);
                applyPlayerSettings();
            } catch (Exception e) {
                IkLog.e(TAG, "Error in onServiceConnected", e);
            }
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            try {
                isServiceBound = false;
                audioService = null;
                notifyActiveFragmentServiceConnected(false);
                IkLog.d(TAG, "Service disconnected");
            } catch (Exception e) {
                IkLog.e(TAG, "Error in onServiceDisconnected", e);
            }
        }
    };

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_main);
            IkLog.d(TAG, "onCreate");

            SettingsManager.getInstance(this);
            initViews();
            applyTheme();

            cachedAudioList = new ArrayList<>();

            mWiFiDirectManager = new WiFiDirectManager(this);
            startAndBindAudioService();
            loadDefaultTab();
            resetAppClosingTimer();
        } catch (Exception e) {
            IkLog.e(TAG, "Fatal error in onCreate", e);
            Toast.makeText(this, "App failed to start", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (isServiceBound) {
                unbindService(serviceConnection);
                isServiceBound = false;
            }
            if (appClosingTimer != null) { appClosingTimer.cancel(); appClosingTimer = null; }
            fragmentCache.clear();
            IkLog.d(TAG, "onDestroy completed");
        } catch (Exception e) {
            IkLog.e(TAG, "Error in onDestroy", e);
        }
    }

    // -------------------------------------------------------------------------
    // Immersive mode
    // -------------------------------------------------------------------------

    public void applyImmersiveMode() {
        try {
            Window window = getWindow();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
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
        } catch (Exception e) {
            IkLog.e(TAG, "Error applying immersive mode", e);
        }
    }

    public void clearImmersiveMode() {
        try {
            Window window = getWindow();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error clearing immersive mode", e);
        }
    }

    // -------------------------------------------------------------------------
    // Views
    // -------------------------------------------------------------------------

    private void initViews() {
        try {
            actionBarSettingsBtn = findViewById(R.id.btn_actionBar_settings);
            actionBarSettingsBtn.setOnClickListener(v -> switchTab(TAB_SETTINGS));

            btnRefresh = findViewById(R.id.btn_media_refresh);
            if (btnRefresh != null) {
                btnRefresh.setOnClickListener(v -> {
                    if (currentFragment instanceof AudioPlayerFragment)
                        ((AudioPlayerFragment) currentFragment).refreshAudioLibrary();
                    else if (currentFragment instanceof VideoPlayerFragment)
                        ((VideoPlayerFragment) currentFragment).refreshVideos();
                    else if (currentFragment instanceof SharePlaybackFragment)
                        ((SharePlaybackFragment) currentFragment).refreshMedia();
                });
            }

            findViewById(R.id.btn_actionBar_searchView).setOnClickListener(v -> {
                try {
                    if (activeFragment instanceof AudioPlayerFragment) {
                        if (isSearchViewActive) {
                            ((AudioPlayerFragment) activeFragment).hideSearchBar();
                            isSearchViewActive = false;
                        } else {
                            ((AudioPlayerFragment) activeFragment).showSearchBar();
                            isSearchViewActive = true;
                        }
                    }
                } catch (Exception e) {
                    IkLog.e(TAG, "Error toggling search bar", e);
                }
            });

            tabContainer = findViewById(R.id.tab_container);
            tabButtons = new LinearLayout[5];
            tabLabels = new TextView[5];

            int[] tabButtonIds = {
                R.id.tab_audio, R.id.tab_video, R.id.tab_photos, R.id.tab_share, R.id.tab_settings
            };
            int[] tabLabelIds = {
                R.id.tab_audio_label, R.id.tab_video_label, R.id.tab_photos_label,
                R.id.tab_share_label, R.id.tab_settings_label
            };

            for (int i = 0; i < 5; i++) {
                final int idx = i;
                tabButtons[i] = findViewById(tabButtonIds[i]);
                tabButtons[i].setOnClickListener(v -> switchTab(idx));
                tabLabels[i] = findViewById(tabLabelIds[i]);
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error initializing views", e);
        }
    }

    // -------------------------------------------------------------------------
    // Tabs
    // -------------------------------------------------------------------------

    private void loadDefaultTab() {
        try {
            int defaultTab = SettingsManager.getInstance(this).getPlayerSettings().defaultTab;
            switchTab(defaultTab);
        } catch (Exception e) {
            IkLog.e(TAG, "Error loading default tab", e);
        }
    }

    private void switchTab(int tabIndex) {
        if (tabIndex == currentTab) return;
        try {
            Fragment fragment = fragmentCache.get(tabIndex);
            if (fragment == null) {
                switch (tabIndex) {
                    case TAB_AUDIO:   fragment = new AudioPlayerFragment(); break;
                    case TAB_VIDEO:   fragment = new VideoPlayerFragment(); break;
                    case TAB_IMAGE:   fragment = new ImageViewerFragment(); break;
                    case TAB_SHARE:   fragment = PrefUtils.isUserSetup(this)
							? new SharePlaybackFragment()
							: new UserSetupFragment();
						break;
                    case TAB_SETTINGS:fragment = new SettingsFragment(); break;
                    default: return;
                }
                fragmentCache.put(tabIndex, fragment);
            }
            updateTabSelection(tabIndex);
            currentTab = tabIndex;
            replaceFragment(fragment);
        } catch (Exception e) {
            IkLog.e(TAG, "Error switching tab", e);
        }
    }

    private void replaceFragment(Fragment fragment) {
        try {
            FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
            tx.replace(R.id.main_container, fragment);
            tx.commitAllowingStateLoss();       // safer against state loss
            currentFragment = fragment;
        } catch (Exception e) {
            IkLog.e(TAG, "Error replacing fragment", e);
        }
    }

    private void updateTabSelection(int position) {
        try {
            for (int i = 0; i < 5; i++) {
                boolean selected = (i == position);
                tabButtons[i].setSelected(selected);
                tabLabels[i].setTextColor(getResources().getColor(
											  selected ? R.color.accent : R.color.text_secondary));
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error updating tab selection", e);
        }
    }

    // -------------------------------------------------------------------------
    // Audio list
    // -------------------------------------------------------------------------

    public void storeAudioList(List<AudioItem> list) {
        cachedAudioList.clear();
        if (list != null) cachedAudioList.addAll(list);
        IkLog.d(TAG, "Audio list stored: " + cachedAudioList.size() + " items");
    }

    public List<AudioItem> retrieveMasterAudioList() {
        return new ArrayList<>(cachedAudioList);
    }

    // -------------------------------------------------------------------------
    // Service helpers
    // -------------------------------------------------------------------------

    public void setPlaylist(List<AudioItem> list) {
        try {
            if (audioService != null && list != null && !list.isEmpty())
                audioService.setPlaylist(new ArrayList<>(list));
        } catch (Exception e) {
            IkLog.e(TAG, "Error setting playlist", e);
        }
    }

    public List<AudioItem> getPlaylist() {
        try {
            if (audioService != null) {
                List<AudioItem> pl = audioService.getPlaylist();
                if (pl != null && !pl.isEmpty()) return pl;
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error getting playlist from service", e);
        }
        return retrieveMasterAudioList();
    }

    public List<AudioItem> getActivePlaylist() {
        try {
            if (audioService != null) {
                List<AudioItem> active = audioService.getActivePlaylist();
                if (active != null && !active.isEmpty()) return active;
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error getting active playlist", e);
        }
        return new ArrayList<>();
    }

    public void setActiveFragment(Fragment frag) {
        try {
            this.activeFragment = this.currentFragment = frag;
            if (isServiceBound && frag instanceof AudioPlayerFragment) {
                ((AudioPlayerFragment) frag).onServiceAvailable(isServiceBound);
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error setting active fragment", e);
        }
    }

    private void notifyActiveFragmentServiceConnected(boolean connected) {
        try {
            if (activeFragment instanceof AudioPlayerFragment) {
                ((AudioPlayerFragment) activeFragment).onServiceAvailable(connected);
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error notifying fragment about service", e);
        }
    }

    // -------------------------------------------------------------------------
    // Main bars
    // -------------------------------------------------------------------------

    public void hideMainBars() {
        try {
            ViewGroup vg = findViewById(R.id.customActionBar);
            if (vg != null) { vg.setVisibility(View.GONE); vg.invalidate(); }
            vg = findViewById(R.id.tabs_scroller);
            if (vg != null) { vg.setVisibility(View.GONE); vg.invalidate(); }
            FrameLayout frame = findViewById(R.id.main_container);
            if (frame != null) frame.invalidate();
        } catch (Exception e) {
            IkLog.e(TAG, "Error hiding main bars", e);
        }
    }

    public void showMainBars() {
        try {
            ViewGroup vg = findViewById(R.id.customActionBar);
            if (vg != null) { vg.setVisibility(View.VISIBLE); vg.requestLayout(); }
            vg = findViewById(R.id.tabs_scroller);
            if (vg != null) { vg.setVisibility(View.VISIBLE); vg.requestLayout(); }
            FrameLayout frame = findViewById(R.id.main_container);
            if (frame != null) frame.invalidate();
        } catch (Exception e) {
            IkLog.e(TAG, "Error showing main bars", e);
        }
    }

    // -------------------------------------------------------------------------
    // Playback
    // -------------------------------------------------------------------------

    public void playUri(Uri uri, int pos) {
        try {
            if (audioService == null) { IkLog.e(TAG, "playUri: service is null"); return; }
            audioService.playAudioFromUri(uri, pos);
        } catch (Exception e) {
            IkLog.e(TAG, "Error playing URI", e);
        }
    }

    public void pausePlayback() { try { if (audioService != null) audioService.pause(); } catch (Exception e) { IkLog.e(TAG, "pause error", e); } }
    public void resumePlayback() { try { if (audioService != null) audioService.resume(); } catch (Exception e) { IkLog.e(TAG, "resume error", e); } }
    public void playNext() { try { if (audioService != null) audioService.playNext(); } catch (Exception e) { IkLog.e(TAG, "playNext error", e); } }
    public void playPrevious() { try { if (audioService != null) audioService.playPrevious(); } catch (Exception e) { IkLog.e(TAG, "playPrevious error", e); } }
    public void seekTo(int pos) { try { if (audioService != null) audioService.seekTo(pos); } catch (Exception e) { IkLog.e(TAG, "seekTo error", e); } }

    public void launchService(Uri uri, int position, List<AudioItem> audioList) {
        if (audioList == null || audioList.isEmpty()) return;
        try {
            Intent intent = new Intent(getApplicationContext(), AudioPlayerService.class);
            intent.putExtra(AudioPlayerService.EXTRA_URI, uri.toString());
            intent.putExtra(AudioPlayerService.EXTRA_PLAYLIST_POSITION, position);
            intent.putParcelableArrayListExtra(AudioPlayerService.EXTRA_PLAYLIST,
                                               new ArrayList<>(audioList));
            startService(intent);
            bindService(new Intent(getApplicationContext(), AudioPlayerService.class),
                        serviceConnection, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            IkLog.e(TAG, "Error launching service", e);
        }
    }

    public boolean isServiceBound()      { return isServiceBound; }
    public boolean isPlaying() {
        try { return audioService != null && audioService.isPlaying(); } catch (Exception e) { return false; }
    }
    public boolean isPaused() {
        try { return audioService != null && audioService.isPaused(); } catch (Exception e) { return false; }
    }
    public Uri getCurrentUri() {
        try { return audioService != null ? audioService.getCurrentUri() : null; } catch (Exception e) { return null; }
    }
    public String getCurrentTitle() {
        try { return audioService != null ? audioService.getCurrentTitle() : "Unknown Title"; } catch (Exception e) { return "Unknown Title"; }
    }
    public String getCurrentArtist() {
        try { return audioService != null ? audioService.getCurrentArtist() : "Unknown Artist"; } catch (Exception e) { return "Unknown Artist"; }
    }
    public String getCurrentAlbum() {
        try { return audioService != null ? audioService.getCurrentAlbum() : "Unknown Album"; } catch (Exception e) { return "Unknown Album"; }
    }
    public Bitmap getCurrentAlbumArt() {
        try { return audioService != null ? audioService.getCurrentAlbumArt() : null; } catch (Exception e) { return null; }
    }
    public int getCurrentInTrackPosition() {
        try { return audioService != null ? audioService.getCurrentInTrackPosition() : 0; } catch (Exception e) { return 0; }
    }
    public int getDuration() {
        try { return audioService != null ? audioService.getDuration() : 0; } catch (Exception e) { return 0; }
    }

    public void setShuffle(boolean s) {
        try { if (audioService != null) audioService.setShuffle(s); } catch (Exception e) { IkLog.e(TAG, "setShuffle error", e); }
    }
    public boolean isShuffle() {
        try { return audioService != null && audioService.isShuffle(); } catch (Exception e) { return false; }
    }
    public void setRepeatMode(RepeatMode m) {
        try { if (audioService != null) audioService.setRepeatMode(m); } catch (Exception e) { IkLog.e(TAG, "setRepeatMode error", e); }
    }
    public int getRepeatMode() {
        try { return audioService != null ? audioService.getRepeatMode().getMode() : RepeatMode.REPEAT_MODE_NONE; } catch (Exception e) { return RepeatMode.REPEAT_MODE_NONE; }
    }

	public int getAudioSessionId() {
		if (audioService != null) {
			return audioService.getAudioSessionId();
		}
		return 0;
	}

    public void setPlaybackSpeed(float speed) {
        try { if (audioService != null) audioService.setPlaybackSpeed(speed); } catch (Exception e) { IkLog.e(TAG, "setPlaybackSpeed error", e); }
    }
    public void setSleepTimer(long delayMs) {
        try { if (audioService != null) audioService.startSleepFadeOut(delayMs); } catch (Exception e) { IkLog.e(TAG, "setSleepTimer error", e); }
    }

    public void applyPlayerSettings() {
        try {
            SettingsManager.PlayerSettings ps = SettingsManager.getInstance(this).getPlayerSettings();
           
            if (ps.keepScreenOn) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error applying player settings", e);
        }
    }


    // -------------------------------------------------------------------------
    // Search & tabs convenience
    // -------------------------------------------------------------------------

    public void setSearchViewHandle(ViewGroup vg) { searchView = (LinearLayout) vg; }
    public void enableSearchView(boolean show) {
        try {
            if (searchView != null) {
                searchView.setVisibility(show ? View.VISIBLE : View.GONE);
                searchView.invalidate();
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error enabling search view", e);
        }
    }

    public WiFiDirectManager getWiFiDirectManager() { return mWiFiDirectManager; }

    // -------------------------------------------------------------------------
    // Service binding
    // -------------------------------------------------------------------------

    private void startAndBindAudioService() {
        try {
            Intent intent = new Intent(this, AudioPlayerService.class);
            startService(intent);
            bindService(intent, serviceConnection, BIND_AUTO_CREATE);
            IkLog.d(TAG, "Audio service started and bound");
        } catch (Exception e) {
            IkLog.e(TAG, "Error starting/binding audio service", e);
        }
    }

    // -------------------------------------------------------------------------
    // Theme & back press
    // -------------------------------------------------------------------------

    private void applyTheme() {
        try {
            tabContainer.setBackgroundColor(getResources().getColor(R.color.card_background));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                getWindow().setStatusBarColor(getResources().getColor(R.color.primary_dark));
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error applying theme", e);
        }
    }

    @Override
    public void onBackPressed() {
        try {
            if (currentFragment instanceof AudioPlayerFragment
                && ((AudioPlayerFragment) currentFragment).handleBackPress()) return;
            if (currentFragment instanceof UserSetupFragment) return;
            if (currentFragment instanceof SharePlaybackFragment
                && ((SharePlaybackFragment) currentFragment).handleBackPressed()) return;

            FragmentManager fm = getSupportFragmentManager();
            if (fm.getBackStackEntryCount() > 0) {
                fm.popBackStack();
                return;
            }

            appClosingCounter++;
            if (appClosingCounter == 1)
                Toast.makeText(this, "Press back again to quit", Toast.LENGTH_SHORT).show();
            if (!closeTimerScheduled) scheduleCloseTimer();
            if (appClosingCounter >= 2) { cancelAndResetCloseTimer(); super.onBackPressed(); }
        } catch (Exception e) {
            IkLog.e(TAG, "Error handling back press", e);
        }
    }

    private void scheduleCloseTimer() {
        try {
            appClosingTimer = new Timer();
            closingTimerTask = new TimerTask() {
                @Override public void run() { appClosingCounter = 0; closeTimerScheduled = false; }
            };
            appClosingTimer.schedule(closingTimerTask, 2000);
            closeTimerScheduled = true;
        } catch (Exception e) {
            IkLog.e(TAG, "Error scheduling close timer", e);
        }
    }

    private void cancelAndResetCloseTimer() {
        try {
            if (appClosingTimer != null) { appClosingTimer.cancel(); appClosingTimer = null; }
            closingTimerTask = null;
            closeTimerScheduled = false;
            appClosingCounter = 0;
        } catch (Exception e) {
            IkLog.e(TAG, "Error resetting close timer", e);
        }
    }

    private void resetAppClosingTimer() { cancelAndResetCloseTimer(); }

    @Override
    public void onSetupComplete() {
        try {
            SharePlaybackFragment shareFragment = new SharePlaybackFragment();
            fragmentCache.put(TAB_SHARE, shareFragment);
            replaceFragment(shareFragment);
            currentFragment = shareFragment;
        } catch (Exception e) {
            IkLog.e(TAG, "Error on setup complete", e);
        }
    }
}