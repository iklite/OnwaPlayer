package com.ikechi.studio.onwa.player.fragment;

import android.Manifest;
import androidx.fragment.app.Fragment;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pDevice;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.ikechi.studio.IkLog;
import com.ikechi.studio.onwa.player.MainActivity;
import com.ikechi.studio.onwa.player.R;
import com.ikechi.studio.onwa.player.adapter.MediaListAdapter;
import com.ikechi.studio.onwa.player.models.AudioItem;
import com.ikechi.studio.onwa.player.models.ChatMessage;
import com.ikechi.studio.onwa.player.models.User;
import com.ikechi.studio.onwa.player.models.VideoItem;
import com.ikechi.studio.onwa.player.net.ClientControlConnection;
import com.ikechi.studio.onwa.player.net.ClientHttpServer;
import com.ikechi.studio.onwa.player.net.HostControlServer;
import com.ikechi.studio.onwa.player.net.HostHttpServer;
import com.ikechi.studio.onwa.player.net.WiFiDirectManager;
import com.ikechi.studio.onwa.player.utils.ChatDatabaseHelper;
import com.ikechi.studio.onwa.player.utils.IkBeautifulDialog;
import com.ikechi.studio.onwa.player.utils.MediaDatabaseHelper;
import com.ikechi.studio.onwa.player.utils.MediaUtils;
import com.ikechi.studio.onwa.player.utils.PrefUtils;
import com.ikechi.studio.onwa.player.widget.WidgetDataHelper;
import com.ikechi.studio.onwa.widgets.MultiPanelLayout;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.adapter.RecyclingAdapter;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.animator.DefaultItemAnimator;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.decoration.SpacingDecoration;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.helper.DragDropHelper;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.helper.MediaSwipeActionHelper;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.widget.RecyclingView;
import com.ikechi.studio.onwa.widgets.video.IkVideoView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class SharePlaybackFragment extends Fragment implements
        HostControlServer.HostCallbacks,
        ClientControlConnection.ClientCallbacks,
        IkVideoView.OnPlaybackStateChangeListener {

    private static final String TAG = "SharePlaybackFrag";

    // Permission codes
    private static final int PERM_READ = 100;
    private static final int PERM_WRITE = 101;
    private static final int PERM_LOC = 102;
    private static final int PERM_NEARBY = 103;

    // Android 13+ permission strings
    private static final String NEARBY_WIFI_DEVICES = "android.permission.NEARBY_WIFI_DEVICES";
    private static final String READ_MEDIA_AUDIO = "android.permission.READ_MEDIA_AUDIO";
    private static final String READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO";
    private static final String READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES";
    private static final String READ_MEDIA_VISUAL_USER_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED";
    private static final String POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS";

    // Android 12+ Bluetooth permissions
    private static final String BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT";
    private static final String BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN";
    private static final String BLUETOOTH_ADVERTISE = "android.permission.BLUETOOTH_ADVERTISE";

    // Ports
    private static final int PORT_HTTP = 8080;
    private static final int PORT_CONTROL = 8081;

    // Media types
    private static final int MEDIA_NONE = 0;
    private static final int MEDIA_VIDEO = 1;
    private static final int MEDIA_AUDIO = 2;

    // Roles
    private static final int ROLE_NONE = 0;
    private static final int ROLE_HOST = 1;
    private static final int ROLE_CLIENT = 2;

    // Pending action
    private static final int ACTION_NONE = 0;
    private static final int ACTION_START_HOST = 1;
    private static final int ACTION_DISCOVER = 2;
    private int mPendingAction = ACTION_NONE;

    // UI components
    private IkVideoView mVideoView;
    private ImageView mAlbumArtView;
    private SeekBar mSeekBar;
    private ImageButton mBtnPlayPause, mBtnPlayNext;
    private TextView mTvCurrentMedia;
    private TextView mTvPosition;

    // Network panel
    private TextView mTvNetStatus;
    private TextView mTvGroupName;
    private TextView mTvRole;
    private TextView mTvNetQuality;
    private Button mBtnStartHost;
    private Button mBtnJoinSession;
    private Button mBtnDisconnect;
    private Button mBtnDownload;
    private Button mBtnRetry;
    private ProgressBar mDownloadProgress;
    private TextView mTvDownloadStatus;
    private RecyclingView mMembersList;

    // Media panels
    private RecyclingView mVideoList;
    private RecyclingView mAudioList;
    private TextView mTvVideoCount;
    private TextView mTvAudioCount;

    // Network media panel
    private RecyclingView mNetworkMediaList;
    private TextView mTvNetworkMediaCount;
    private NetworkMediaAdapter mNetworkMediaAdapter;

    // Chat panel
    private TextView mTvUnreadCount;
    private ChatFragment mChatFragment;

    // State
    private int mRole = ROLE_NONE;
    private int mMediaType = MEDIA_NONE;
    private boolean mIsPlaying = false;
    private int mUnreadChat = 0;

    // Session ID
    private static String sPersistentSessionId = "";
    private String mSessionId = "";

    private String mHostIp = "";
    private String mHostDownloadUrl = "";

    // Media – purely Uri‑based
    private Uri mCurrentUri;
    private String mCurrentTitle;

    // Thread‑safe user/media collections
    private final Set<User> mConnectedUsers = Collections.synchronizedSet(new HashSet<User>());
    private final Map<String, RemoteMediaInfo> mRemoteMediaMap = new HashMap<>();
    private final List<RemoteMediaInfo> mRemoteMediaList = new CopyOnWriteArrayList<>();

    // Role‑specific state objects
    private static class HostState {
        HostControlServer controlServer;
        HostHttpServer httpServer;
    }
    private static class ClientState {
        ClientControlConnection controlConnection;
        ClientHttpServer httpServer;
        String localP2pIp;
    }
    private HostState mHostState = new HostState();
    private ClientState mClientState = new ClientState();

    // Utilities
    private ChatDatabaseHelper mChatDb;
    private final Handler mUiHandler = new Handler(Looper.getMainLooper());

    // Adapters
    private MediaListAdapter mVideoAdapter;
    private MediaListAdapter mAudioAdapter;
    private MembersAdapter mMembersAdapter;

    // Discovered peers
    private List<WifiP2pDevice> mPeerDevices = new ArrayList<>();

    // IP addresses
    private String mGroupOwnerIp;
    private String mLocalP2pIp;
    private boolean mIsRegisteringReceiver = false;
    private int mRetryCount = 0;

    // Back press
    private int backPressCount = 0;

    // Pending share request (host)
    private String mPendingShareType;
    private String mPendingShareUrl;
    private String mPendingShareTitle;
    private User mPendingShareUser;

    // Guard against multiple peer picker dialogs
    private boolean mPeerDialogShowing = false;

    // Current playing media owner
    private String mCurrentMediaOwner = "";

    // Own identity
    private String mMyUsername = "";
    private String mMyRealName = "";

    // Host migration fields
    private List<User> mLastKnownUsers = new ArrayList<>();
    private String mLastMediaUrl;
    private long mLastMediaPosition;
    private boolean mHostLost = false;
    private Handler mHostMigrationHandler = new Handler(Looper.getMainLooper());

    // Stats / widget
    private Object mCurrentLocalItem;         // VideoItem or AudioItem
    private long mPlayStartPositionMs;        // for calculating listened time
    private boolean mPlayRecorded = false;    // whether play count already incremented for this track

    // =========================================================================
    // Interfaces
    // =========================================================================

    public interface OnMediaClickListener {
        void onMediaClick(RemoteMediaInfo media);
        void onDownloadClick(RemoteMediaInfo media);
    }

    // =========================================================================
    // RemoteMediaInfo data class
    // =========================================================================

    public static class RemoteMediaInfo {
        public String username;
        public String realName;
        public String mediaType;
        public String url;
        public String title;
        public boolean isAvailable;
        private final static String TAG = "RemoteMediaInfo";

        public RemoteMediaInfo(String username, String realName, String mediaType,
                               String url, String title) {
            this.username = username;
            this.realName = realName;
            this.mediaType = mediaType;
            this.url = url;
            this.title = title;
            this.isAvailable = true;
            IkLog.i(TAG, "Remote media initialized: User :- " + realName + ",  " + username);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RemoteMediaInfo that = (RemoteMediaInfo) o;
            return url != null && url.equals(that.url);
        }

        @Override
        public int hashCode() {
            return url != null ? url.hashCode() : 0;
        }
    }

    // =========================================================================
    // Fragment lifecycle
    // =========================================================================

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        IkLog.setInstantFlush(true);
        IkLog.d(TAG, "onCreateView");
        View root = inflater.inflate(R.layout.fragment_share_playback, container, false);
        bindViews(root);
        setupTransportControls();
        setupNetworkPanel();
        setupMediaPanels();
        setupNetworkMediaPanel(root);
        mChatDb = ChatDatabaseHelper.getInstance(getActivity());
        mMyUsername = PrefUtils.getUsername(getActivity());
        mMyRealName = PrefUtils.getRealName(getActivity());
        setupChatPanel(root);
        registerWiFiDirectReceiver();

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setActiveFragment(this);
            ((MainActivity) getActivity()).hideMainBars();
        }
        requestMediaScan();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mRole == ROLE_HOST && !sPersistentSessionId.isEmpty() && mSessionId.isEmpty()) {
            mSessionId = sPersistentSessionId;
            if (mHostState.controlServer != null) mHostState.controlServer.setSessionId(mSessionId);
        }
        if (mRole == ROLE_CLIENT && mClientState.httpServer == null) {
            mClientState.httpServer = new ClientHttpServer(PORT_HTTP, getActivity());
            try {
                mClientState.httpServer.clientStart();
            } catch (IOException e) {
                IkLog.e(TAG, "Failed to start client HTTP server", e);
            }
        }
        if (mPendingAction != ACTION_NONE) runSettingsChain();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        recordCurrentHistory();   // save listening stats before leaving
        sendWidgetUpdate(false);

        WiFiDirectManager wdm = getWiFiDirectManager();
        if (wdm != null) wdm.cleanup();
        resetToOffline();
        if (mIsRegisteringReceiver && wdm != null) {
            wdm.unregisterReceiver();
            mIsRegisteringReceiver = false;
        }
        mUiHandler.removeCallbacksAndMessages(null);
    }

    private void registerWiFiDirectReceiver() {
        try {
            if (!mIsRegisteringReceiver && getActivity() != null) {
                WiFiDirectManager wdm = getWiFiDirectManager();
                if (wdm != null) {
                    wdm.registerReceiver();
                    mIsRegisteringReceiver = true;
                }
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error registering receiver", e);
        }
    }

    // =========================================================================
    // Back press
    // =========================================================================

    public boolean handleBackPressed() {
        if (getActivity() == null) return false;
        backPressCount++;
        if (backPressCount == 1) {
            ((MainActivity) getActivity()).showMainBars();
            return true;
        }
        return false;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private WiFiDirectManager getWiFiDirectManager() {
        if (getActivity() == null) return null;
        return ((MainActivity) getActivity()).getWiFiDirectManager();
    }

    private long parseLong(String s, long fallback) {
        try { return Long.parseLong(s.trim()); }
        catch (NumberFormatException e) { IkLog.e(TAG, "Error in parseLong", e); return fallback; }
    }

    private void showToast(final String msg) {
        if (getActivity() == null || mUiHandler == null) return;
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getActivity(), msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private String buildStreamUrl() {
        String ip = (mRole == ROLE_HOST) ? mGroupOwnerIp : mClientState.localP2pIp;
        if (TextUtils.isEmpty(ip)) {
            ip = getLocalP2pIpAddress();
            if (mRole == ROLE_CLIENT) mClientState.localP2pIp = ip;
        }
        if (TextUtils.isEmpty(ip)) return "";
        return "http://" + ip + ":" + PORT_HTTP + "/media";
    }

    private String getLocalP2pIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                if (iface.getName().startsWith("p2p")) {
                    Enumeration<InetAddress> addrs = iface.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        InetAddress addr = addrs.nextElement();
                        if (!addr.isLoopbackAddress() && addr.getHostAddress().contains(".")) {
                            return addr.getHostAddress();
                        }
                    }
                }
            }
            // fallback: any non‑loopback
            interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (!addr.isLoopbackAddress() && addr.getHostAddress().contains(".")) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            IkLog.e(TAG, "getLocalP2pIpAddress failed", e);
        }
        return "";
    }

    // =========================================================================
    // UI Binding and Setup
    // =========================================================================

    private void bindViews(View root) {
        mVideoView = (IkVideoView) root.findViewById(R.id.videoView);
        mAlbumArtView = (ImageView) root.findViewById(R.id.albumArtView);
        mSeekBar = (SeekBar) root.findViewById(R.id.seekBar);
        mBtnPlayPause = (ImageButton) root.findViewById(R.id.btnPlayPause);
        mBtnPlayNext = (ImageButton) root.findViewById(R.id.btnPlayNext);
        mTvCurrentMedia = (TextView) root.findViewById(R.id.tvCurrentMedia);
        mTvPosition = (TextView) root.findViewById(R.id.tvPosition);

        mTvNetStatus = (TextView) root.findViewById(R.id.tvNetStatus);
        mTvGroupName = (TextView) root.findViewById(R.id.tvGroupName);
        mTvRole = (TextView) root.findViewById(R.id.tvRole);
        mTvNetQuality = (TextView) root.findViewById(R.id.tvNetQuality);
        mBtnStartHost = (Button) root.findViewById(R.id.btnStartHost);
        mBtnJoinSession = (Button) root.findViewById(R.id.btnJoinSession);
        mBtnDisconnect = (Button) root.findViewById(R.id.btnDisconnect);
        mBtnDownload = (Button) root.findViewById(R.id.btnDownload);
        mBtnRetry = (Button) root.findViewById(R.id.btnRetry);
        mDownloadProgress = (ProgressBar) root.findViewById(R.id.downloadProgress);
        mTvDownloadStatus = (TextView) root.findViewById(R.id.tvDownloadStatus);
        mMembersList = (RecyclingView) root.findViewById(R.id.membersList);

        mVideoList = (RecyclingView) root.findViewById(R.id.videoList);
        mAudioList = (RecyclingView) root.findViewById(R.id.audioList);
        mTvVideoCount = (TextView) root.findViewById(R.id.tvVideoCount);
        mTvAudioCount = (TextView) root.findViewById(R.id.tvAudioCount);

        mTvUnreadCount = (TextView) root.findViewById(R.id.tvUnreadCount);
    }

    private void setupTransportControls() {
        mVideoView.setPlaybackStateListener(this);
        mVideoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                // Record full listen for local audio
                recordHistoryForCurrentTrack(true);
                sendWidgetUpdate(false);
                playNext(mMediaType);
            }
        });
        mVideoView.setSeekbarListener(new IkVideoView.OnSeekbarStateChangedListener() {
            @Override
            public void onSeek(long position, boolean fromUser) {
                if (fromUser) {
                    updateSeekBar(position);
                    sendCommand("SEEK:" + position);
                } else {
                    updateSeekBar(position);
                }
            }
        });
        mBtnPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mVideoView.isPlaying()) {
                    mVideoView.pause();
                    mIsPlaying = false;
                    mBtnPlayPause.setImageResource(android.R.drawable.ic_media_play);
                    recordCurrentHistory();   // save listened time
                    sendCommand("PLAY:false");
                    sendWidgetUpdate(false);
                } else {
                    mVideoView.start();
                    mIsPlaying = true;
                    mBtnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
                    updateTimeDisplay();
                    mPlayStartPositionMs = mVideoView.getCurrentPosition();
                    sendCommand("PLAY:true");
                    sendWidgetUpdate(true);
                    if (!mPlayRecorded) incrementPlayCount();
                }
            }
        });
        mBtnPlayNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recordCurrentHistory();
                sendWidgetUpdate(false);
                playNext(mMediaType);
            }
        });

        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && mVideoView.getDuration() > 0) {
                    int pos = (int) ((progress / 1000.0) * mVideoView.getDuration());
                    mVideoView.seekTo(pos, fromUser);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {}

            @Override
            public void onStopTrackingTouch(SeekBar sb) {}
        });
    }

    private void setupNetworkPanel() {
        updateNetworkPanelUI();
        mBtnStartHost.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkSettingsAndStartHost();
            }
        });
        mBtnJoinSession.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkSettingsAndDiscover();
            }
        });
        mBtnDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                disconnect();
            }
        });
        mBtnDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestDownload();
            }
        });
        mBtnRetry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBtnRetry.setVisibility(View.GONE);
                mBtnJoinSession.setVisibility(View.VISIBLE);
                mTvNetStatus.setText("Tap 'Join a Session' to try again");
                mRetryCount = 0;
            }
        });

        mMembersAdapter = new MembersAdapter();
        mMembersList.setOrientation(RecyclingView.VERTICAL);
        mMembersList.setAdapter(mMembersAdapter);
    }

    private void setupMediaPanels() {
        // Video list
        mVideoAdapter = new MediaListAdapter(MediaListAdapter.TYPE_VIDEO);
        mVideoAdapter.setOnMediaItemClickListener(new MediaListAdapter.OnMediaItemClickListener() {
            @Override
            public void onVideoItemClick(VideoItem item) {
                playLocalMedia(item);
                if (mRole == ROLE_CLIENT) {
                    requestShareMedia("VIDEO", buildStreamUrl(), item.getTitle());
                }
            }
            @Override
            public void onAudioItemClick(AudioItem item) {}
        });
        mVideoList.setItemAnimator(new DefaultItemAnimator());
        mVideoList.addItemDecoration(new SpacingDecoration(dpToPx(4), true, 0x18000000));

        MediaSwipeActionHelper.forVideoLibrary(mVideoList,
                new MediaSwipeActionHelper.SwipeCallback() {
                    @Override
                    public void onSwipeLeft(int position) {
                        List<VideoItem> videos = mVideoAdapter.getVideoList();
                        if (position < 0 || position >= videos.size()) return;
                        VideoItem item = videos.get(position);
                        try {
                            getActivity().getContentResolver().delete(item.getUri(), null, null);
                            mVideoAdapter.removeItemAt(position);
                            mTvVideoCount.setText(mVideoAdapter.getVideoList().size() + " Videos");
                            showToast("Deleted: " + item.getTitle());
                        } catch (Exception e) {
                            IkLog.e(TAG, "Could not delete file", e);
                            showToast("Could not delete file");
                        }
                    }

                    @Override
                    public void onSwipeRight(int position) {
                        List<VideoItem> videos = mVideoAdapter.getVideoList();
                        if (position < 0 || position >= videos.size()) return;
                        VideoItem item = videos.get(position);
                        playLocalMedia(item);
                    }

                    @Override
                    public void onSwipeRemoveFromList(int position) {
                        VideoItem item = (VideoItem) mVideoAdapter.removeItemAt(position);
                        if (item != null) {
                            mTvVideoCount.setText(mVideoAdapter.getVideoList().size() + " Videos");
                            showToast("Removed: " + item.getTitle());
                        }
                    }
                });

        new DragDropHelper(mVideoList, new DragDropHelper.DragCallback() {
            @Override public void onItemMoved(int from, int to) {
                mVideoAdapter.moveItem(from, to);
                mVideoAdapter.notifyItemMoved(from, to);
            }
        });
        mVideoList.setAdapter(mVideoAdapter);

        // Audio list
        mAudioAdapter = new MediaListAdapter(MediaListAdapter.TYPE_AUDIO);
        mAudioAdapter.setOnMediaItemClickListener(new MediaListAdapter.OnMediaItemClickListener() {
            @Override public void onVideoItemClick(VideoItem item) {}
            @Override
            public void onAudioItemClick(AudioItem item) {
                playLocalMedia(item);
                if (mRole == ROLE_CLIENT) {
                    requestShareMedia("AUDIO", buildStreamUrl(), item.getTitle());
                }
            }
        });
        mAudioList.setItemAnimator(new DefaultItemAnimator());
        mAudioList.addItemDecoration(new SpacingDecoration(dpToPx(4), true, 0x18000000));
        MediaSwipeActionHelper.forVideoLibrary(mAudioList,
                new MediaSwipeActionHelper.SwipeCallback() {
                    @Override
                    public void onSwipeLeft(int position) {
                        List<AudioItem> audios = mAudioAdapter.getAudioList();
                        if (position < 0 || position >= audios.size()) return;
                        AudioItem item = audios.get(position);
                        try {
                            getActivity().getContentResolver().delete(item.getUri(), null, null);
                            mAudioAdapter.removeItemAt(position);
                            mTvAudioCount.setText(mAudioAdapter.getAudioList().size() + " Tracks");
                            showToast("Deleted: " + item.getTitle());
                        } catch (Exception e) {
                            IkLog.e(TAG, "Could not delete file", e);
                            showToast("Could not delete file");
                        }
                    }

                    @Override
                    public void onSwipeRight(int position) {
                        List<VideoItem> videos = mVideoAdapter.getVideoList();
                        if (position < 0 || position >= videos.size()) return;
                        VideoItem item = videos.get(position);
                        playLocalMedia(item);
                    }

                    @Override
                    public void onSwipeRemoveFromList(int position) {
                        VideoItem item = (VideoItem) mVideoAdapter.removeItemAt(position);
                        if (item != null) {
                            mTvAudioCount.setText(mAudioAdapter.getAudioList().size() + " Tracks");
                            showToast("Removed: " + item.getTitle());
                        }
                    }
                });
        new DragDropHelper(mAudioList, new DragDropHelper.DragCallback() {
            @Override public void onItemMoved(int from, int to) {
                mAudioAdapter.moveItem(from, to);
                mAudioAdapter.notifyItemMoved(from, to);
            }
        });
        mAudioList.setAdapter(mAudioAdapter);
    }

    private void setupChatPanel(View root) {
        mChatFragment = new ChatFragment();
        getChildFragmentManager().beginTransaction()
                .replace(R.id.chat_container, mChatFragment)
                .commit();
        mChatFragment.setChatListener(new ChatFragment.ChatListener() {
            @Override
            public void onSendMessage(String text) {
                sendChatMessage(text);
            }
        });
        MultiPanelLayout mp = (MultiPanelLayout) root.findViewById(R.id.multi_panel);
        if (mp != null) {
            mp.setOnPanelStateChangeListener(new MultiPanelLayout.OnPanelStateChangeListener() {
                @Override
                public void onPanelExpanded(int idx) {
                    if (idx == MultiPanelLayout.PANEL_CHAT) {
                        mUnreadChat = 0;
                        mTvUnreadCount.setText("");
                    } else if (idx == MultiPanelLayout.PANEL_VIDEO) {
                        mVideoList.invalidate();
                        mVideoAdapter.notifyDataSetChanged();
                    }
                }
                @Override
                public void onPanelCollapsed(int idx) {}
            });
        }
        loadChatHistory();
    }

    private void setupNetworkMediaPanel(View root) {
        mNetworkMediaList = (RecyclingView) root.findViewById(R.id.networkMediaList);
        mTvNetworkMediaCount = (TextView) root.findViewById(R.id.tvNetworkMediaCount);
        mNetworkMediaAdapter = new NetworkMediaAdapter();
        mNetworkMediaAdapter.setOnMediaClickListener(new OnMediaClickListener() {
            @Override
            public void onMediaClick(RemoteMediaInfo media) {
                playRemoteMedia(media);
            }
            @Override
            public void onDownloadClick(RemoteMediaInfo media) {
                startDownload(media.url, media.title, media.mediaType);
            }
        });
        mNetworkMediaList.setAdapter(mNetworkMediaAdapter);
    }

    private void updateNetworkPanelUI() {
        if (getActivity() == null || getView() == null) return;
        switch (mRole) {
            case ROLE_NONE:
                mTvNetStatus.setText("Offline");
                mTvGroupName.setText("No active session");
                mTvRole.setText("");
                mTvNetQuality.setText("");
                mBtnStartHost.setVisibility(View.VISIBLE);
                mBtnStartHost.setEnabled(true);
                mBtnJoinSession.setVisibility(View.VISIBLE);
                mBtnJoinSession.setEnabled(true);
                mBtnDisconnect.setVisibility(View.GONE);
                mBtnDownload.setVisibility(View.GONE);
                mBtnRetry.setVisibility(View.GONE);
                break;
            case ROLE_HOST:
                mTvNetStatus.setText("Hosting");
                mTvGroupName.setText("Session: " + (mSessionId.length() > 8 ? mSessionId.substring(0, 8) : mSessionId));
                mTvRole.setText("Host");
                mBtnStartHost.setVisibility(View.GONE);
                mBtnJoinSession.setVisibility(View.GONE);
                mBtnDisconnect.setVisibility(View.VISIBLE);
                mBtnDownload.setVisibility(View.GONE);
                mBtnRetry.setVisibility(View.GONE);
                break;
            case ROLE_CLIENT:
                mTvNetStatus.setText("Connected");
                mTvGroupName.setText("Session: " + (mSessionId.length() > 8 ? mSessionId.substring(0, 8) : mSessionId));
                mTvRole.setText("Client");
                mBtnStartHost.setVisibility(View.GONE);
                mBtnJoinSession.setVisibility(View.GONE);
                mBtnDisconnect.setVisibility(View.VISIBLE);
                mBtnDownload.setVisibility(mMediaType != MEDIA_NONE ? View.VISIBLE : View.GONE);
                mBtnRetry.setVisibility(View.GONE);
                break;
        }
    }

    // =========================================================================
    // Media scan
    // =========================================================================

    private void requestMediaScan() {
        List<String> perms = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) {
            if (getActivity().checkSelfPermission(READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                    getActivity().checkSelfPermission(READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                perms.add(READ_MEDIA_AUDIO);
                perms.add(READ_MEDIA_VIDEO);
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            if (getActivity().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
        if (!perms.isEmpty()) {
            requestPermissions(perms.toArray(new String[0]), PERM_READ);
        } else {
            doMediaScan();
        }
    }

    private void doMediaScan() {
        mTvVideoCount.setText("Scanning...");
        MediaUtils.getVideoFilesAsync(getActivity(), mUiHandler, new MediaUtils.VideoFilesCallback() {
            @Override
            public void onVideoFilesLoaded(List<VideoItem> items) {
                mVideoAdapter.setVideoItems(items);
                mVideoAdapter.notifyDataSetChanged();
                mTvVideoCount.setText(items.size() + " Videos");
            }
        }, false);

        mTvAudioCount.setText("Scanning...");
        MediaUtils.getAudioFilesAsync(getActivity(), mUiHandler, new MediaUtils.AudioFilesCallback() {
            @Override
            public void onAudioFilesLoaded(List<AudioItem> items) {
                mAudioAdapter.setAudioItems(items);
                mAudioAdapter.notifyDataSetChanged();
                mTvAudioCount.setText(items.size() + " Tracks");
            }
        }, false, false);
    }

    public void refreshMedia() {
        requestMediaScan();
    }

    // =========================================================================
    // Playback helpers
    // =========================================================================

    private void playLocalMedia(Object item) {
        backPressCount = 0;
        ((MainActivity) getActivity()).hideMainBars();

        String title = "";
        int type = MEDIA_NONE;
        Uri uri = null;
        if (item instanceof VideoItem) {
            VideoItem v = (VideoItem) item;
            title = v.getTitle();
            type = MEDIA_VIDEO;
            uri = v.getUri();
        } else if (item instanceof AudioItem) {
            AudioItem a = (AudioItem) item;
            title = a.getTitle();
            type = MEDIA_AUDIO;
            uri = a.getUri();
        }
        if (uri == null) return;

        mCurrentLocalItem = item;          // remember for stats
        mPlayRecorded = false;
        mCurrentUri = uri;
        mCurrentTitle = title;
        mCurrentMediaOwner = mMyUsername;
        mMediaType = type;
        mTvCurrentMedia.setText(title);
        mAlbumArtView.setVisibility(type == MEDIA_AUDIO ? View.VISIBLE : View.GONE);
        mVideoView.setVisibility(type == MEDIA_AUDIO ? View.GONE : View.VISIBLE);
        mVideoView.setVideoURI(uri);
        final int finalType = type;
        final String finalTitle = title;
        mVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mVideoView.start();
                mBtnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
                mIsPlaying = true;
                updateTimeDisplay();
                if (finalType == MEDIA_AUDIO) {
                    byte[] art = MediaUtils.extractAudioAlbumArt(getActivity(), mCurrentUri);
                    if (art != null) {
                        mAlbumArtView.setImageBitmap(BitmapFactory.decodeByteArray(art, 0, art.length));
                        sendCommand("ALBUM_ART:" + Base64.encodeToString(art, Base64.DEFAULT));
                    } else {
                        mAlbumArtView.setImageBitmap(MediaUtils.getDefaultAlbumArt(getActivity()));
                        sendCommand("ALBUM_ART:DEFAULT");
                    }
                }
                // Stats: increment play count for AudioItem
                if (!mPlayRecorded) {
                    incrementPlayCount();
                }
                mPlayStartPositionMs = 0;
                sendWidgetUpdate(true);
            }
        });

        // Serve media via HTTP
        if (mRole == ROLE_HOST) {
            if (mHostState.httpServer == null) {
                mHostState.httpServer = new HostHttpServer(PORT_HTTP, getActivity());
                try { mHostState.httpServer.hostStart(); } catch (IOException e) {
                    IkLog.e(TAG, "Failed to start host HTTP server", e);
                }
            }
            mHostState.httpServer.hostSetMediaUri(mCurrentUri);
            String streamUrl = buildStreamUrl();
            String typeStr = (type == MEDIA_VIDEO) ? "VIDEO" : "AUDIO";
            mHostState.controlServer.hostBroadcastMediaChange(typeStr, streamUrl, finalTitle, mMyUsername, mMyRealName);
        } else if (mRole == ROLE_CLIENT) {
            if (mClientState.httpServer == null) {
                mClientState.httpServer = new ClientHttpServer(PORT_HTTP, getActivity());
            }
            try {
                mClientState.httpServer.clientSetMediaUri(mCurrentUri);
                mClientState.httpServer.setOnPreparedListener(new ClientHttpServer.OnServerPreparedListener() {
                    @Override public void onPrepared() {
                        String streamUrl = buildStreamUrl();
                        String typeStr = (finalType == MEDIA_VIDEO) ? "VIDEO" : "AUDIO";
                        sendCommand("MEDIA:" + typeStr + "|" + streamUrl + "|" + finalTitle + "|" + mMyUsername + "|" + mMyRealName);
                    }
                });
                mClientState.httpServer.clientStart();
            } catch (Exception e) {
                IkLog.e(TAG, "Failed to start client HTTP server", e);
            }
        }
    }

    private void playRemoteMedia(RemoteMediaInfo media) {
        if (media == null || TextUtils.isEmpty(media.url)) {
            showToast("Media URL not available");
            return;
        }
        // Remote media: no local stats
        mCurrentLocalItem = null;
        mPlayRecorded = false;

        mCurrentTitle = media.title;
        mCurrentMediaOwner = media.username;
        mCurrentUri = null;
        mMediaType = "VIDEO".equals(media.mediaType) ? MEDIA_VIDEO : MEDIA_AUDIO;
        mTvCurrentMedia.setText(mCurrentTitle);
        mAlbumArtView.setVisibility(mMediaType == MEDIA_AUDIO ? View.VISIBLE : View.GONE);
        mVideoView.setVisibility(mMediaType == MEDIA_AUDIO ? View.GONE : View.VISIBLE);

        IkLog.d(TAG, "playRemoteMedia: loading URL " + media.url);
        mVideoView.setVideoURI(Uri.parse(media.url));
        mVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mVideoView.start();
                mBtnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
                mIsPlaying = true;
                updateTimeDisplay();
                if (mMediaType == MEDIA_AUDIO) {
                    mAlbumArtView.setImageBitmap(MediaUtils.getDefaultAlbumArt(getActivity()));
                }
                mPlayStartPositionMs = 0;
                sendWidgetUpdate(true);
            }
        });
        mVideoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                IkLog.e(TAG, "MediaPlayer error: what= " + what + " extra= " + extra);
                showToast("Failed to play media");
                return true;
            }
        });
    }

    private void playNext(int mediaType) {
        if (mCurrentUri == null) return;
        if (mediaType == MEDIA_VIDEO) {
            VideoItem next = (VideoItem) getNextPlaybackItem(mCurrentUri);
            if (next == null) { showToast("End of list"); return; }
            playLocalMedia(next);
        } else if (mediaType == MEDIA_AUDIO) {
            AudioItem next = (AudioItem) getNextPlaybackItem(mCurrentUri);
            if (next == null) { showToast("End of list"); return; }
            playLocalMedia(next);
        }
    }

    private Object getNextPlaybackItem(Uri currentMedia) {
        if (mMediaType == MEDIA_NONE) return null;
        if (mMediaType == MEDIA_VIDEO) {
            List<VideoItem> items = mVideoAdapter.getVideoList();
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).getUri().equals(currentMedia)) {
                    return (i < items.size() - 1) ? items.get(i + 1) : null;
                }
            }
        } else {
            List<AudioItem> items = mAudioAdapter.getAudioList();
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).getUri().equals(currentMedia)) {
                    return (i < items.size() - 1) ? items.get(i + 1) : null;
                }
            }
        }
        return null;
    }

    private void updateSeekBar(long position) {
        int duration = mVideoView.getDuration();
        if (duration > 0) {
            mSeekBar.setProgress((int) (position * 1000 / duration));
            mTvPosition.setText(formatTime((int) position) + " / " + formatTime(duration));
        }
    }

    private void updateTimeDisplay() {
        int cur = mVideoView.getCurrentPosition();
        int dur = mVideoView.getDuration();
        if (dur > 0) {
            mTvPosition.setText(formatTime(cur) + " / " + formatTime(dur));
        }
    }

    private String formatTime(int millis) {
        int sec = (millis / 1000) % 60;
        int min = (millis / (1000 * 60)) % 60;
        int hr = millis / (1000 * 60 * 60);
        if (hr > 0) return String.format("%d:%02d:%02d", hr, min, sec);
        else return String.format("%02d:%02d", min, sec);
    }

    // =========================================================================
    // Stats & Widget helpers (new)
    // =========================================================================

    private void incrementPlayCount() {
        if (mCurrentLocalItem instanceof AudioItem) {
            AudioItem ai = (AudioItem) mCurrentLocalItem;
            int count = ai.getPlayCount() + 1;
            ai.setPlayCount(count);
            MediaDatabaseHelper.getInstance(getActivity()).updatePlayCount(
                    ai.getUri().toString(), count);
        }
        mPlayRecorded = true;
    }

    private void recordCurrentHistory() {
        if (mCurrentLocalItem instanceof AudioItem && mCurrentUri != null && mIsPlaying) {
            long pos = mVideoView.getCurrentPosition();
            long listened = pos - mPlayStartPositionMs;
            if (listened > 500) {
                MediaDatabaseHelper.getInstance(getActivity()).recordPlayHistory(
                        mCurrentUri.toString(), listened);
                IkLog.d(TAG, "Recorded history partial: " + listened + "ms");
            }
            mPlayStartPositionMs = pos;  // shift forward
        }
    }

    private void recordHistoryForCurrentTrack(boolean completed) {
        if (mCurrentLocalItem instanceof AudioItem && mCurrentUri != null) {
            long listenedMs;
            if (completed) {
                listenedMs = mVideoView.getDuration();
                if (listenedMs <= 0) listenedMs = mVideoView.getCurrentPosition();
            } else {
                listenedMs = mVideoView.getCurrentPosition() - mPlayStartPositionMs;
                if (listenedMs < 0) listenedMs = 0;
            }
            if (listenedMs > 500) {
                MediaDatabaseHelper.getInstance(getActivity()).recordPlayHistory(
                        mCurrentUri.toString(), listenedMs);
                IkLog.d(TAG, "Recorded history " + (completed ? "complete" : "partial") + ": " + listenedMs + "ms");
            }
        }
        mPlayRecorded = false;
    }

    private void sendWidgetUpdate(boolean isPlaying) {
        if (mCurrentLocalItem instanceof AudioItem) {
            AudioItem ai = (AudioItem) mCurrentLocalItem;
            String title = ai.getTitle();
            String artist = ai.getArtist();
            Bitmap art = null;
            byte[] artBytes = ai.getAlbumArtBytes();
            if (artBytes != null && artBytes.length > 0) {
                art = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.length);
            } else {
                art = MediaUtils.getDefaultAlbumArt(getActivity());
            }
            WidgetDataHelper.save(getActivity(), title, artist, art, isPlaying);
            Intent intent = new Intent("com.ikechi.studio.onwa.player.WIDGET_DATA");
            intent.putExtra("widget_title", title);
            intent.putExtra("widget_artist", artist);
            intent.putExtra("widget_is_playing", isPlaying);
            getActivity().sendBroadcast(intent);
        } else if (mCurrentLocalItem instanceof VideoItem) {
            VideoItem vi = (VideoItem) mCurrentLocalItem;
            WidgetDataHelper.save(getActivity(), vi.getTitle(), "", null, isPlaying);
            Intent intent = new Intent("com.ikechi.studio.onwa.player.WIDGET_DATA");
            intent.putExtra("widget_title", vi.getTitle());
            intent.putExtra("widget_artist", "");
            intent.putExtra("widget_is_playing", isPlaying);
            getActivity().sendBroadcast(intent);
        } else {
            // remote media: clear widget
            WidgetDataHelper.save(getActivity(), "Not Playing", "", null, false);
            Intent intent = new Intent("com.ikechi.studio.onwa.player.WIDGET_DATA");
            intent.putExtra("widget_title", "Not Playing");
            intent.putExtra("widget_artist", "");
            intent.putExtra("widget_is_playing", false);
            getActivity().sendBroadcast(intent);
        }
    }

    // =========================================================================
    // Command sending (client)
    // =========================================================================

    private void sendCommand(final String cmd) {
        if (mRole != ROLE_CLIENT || mClientState.controlConnection == null) return;
        mClientState.controlConnection.clientSendCommand(cmd);
    }

    // =========================================================================
    // Download
    // =========================================================================

    private void requestDownload() {
        if (mRole == ROLE_NONE) return;
        if (Build.VERSION.SDK_INT < 29 && Build.VERSION.SDK_INT >= 23) {
            if (getActivity().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERM_WRITE);
                return;
            }
        }
        sendCommand("DOWNLOAD_REQUEST");
    }

    private void startDownload(String urlStr, String title, String mediaType) {
        if (urlStr == null || urlStr.isEmpty()) { showToast("Download URL not available"); return; }
        String safeTitle = (title != null) ? title.replaceAll("[^a-zA-Z0-9_\\-]", "_") : "onwa_download";
        new DownloadTask(urlStr, safeTitle, mediaType).execute();
    }

    private class DownloadTask extends AsyncTask<Void, Integer, File> {
        private final String mUrl;
        private final String mBaseName;
        private final String mMediaTypeHint;
        private String mErrorMsg;

        DownloadTask(String url, String baseName, String mediaTypeHint) {
            mUrl = url;
            mBaseName = baseName;
            mMediaTypeHint = mediaTypeHint;
        }

        @Override
        protected void onPreExecute() {
            mBtnDownload.setEnabled(false);
            mDownloadProgress.setVisibility(View.VISIBLE);
            mTvDownloadStatus.setVisibility(View.VISIBLE);
            mTvDownloadStatus.setText("Connecting...");
            mDownloadProgress.setProgress(0);
        }

        @Override
        protected File doInBackground(Void... v) {
            HttpURLConnection headConn = null;
            HttpURLConnection getConn = null;
            InputStream in = null;
            FileOutputStream out = null;
            try {
                headConn = (HttpURLConnection) new URL(mUrl).openConnection();
                headConn.setRequestMethod("HEAD");
                headConn.setConnectTimeout(10000);
                headConn.setReadTimeout(30000);
                headConn.connect();

                String contentType = headConn.getContentType();
                String contentDisposition = headConn.getHeaderField("Content-Disposition");

                String guessedName = URLUtil.guessFileName(mUrl, contentDisposition, contentType);

                if (guessedName == null || guessedName.isEmpty()) {
                    String ext = ".mp4";
                    if (mMediaTypeHint != null && mMediaTypeHint.equalsIgnoreCase("AUDIO")) {
                        ext = ".mp3";
                    }
                    guessedName = mBaseName + ext;
                }

                if (!guessedName.startsWith(mBaseName)) {
                    int dot = guessedName.lastIndexOf('.');
                    String ext = (dot >= 0) ? guessedName.substring(dot) : "";
                    guessedName = mBaseName + ext;
                }

                String safeName = guessedName.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
                headConn.disconnect();

                getConn = (HttpURLConnection) new URL(mUrl).openConnection();
                getConn.setConnectTimeout(10000);
                getConn.setReadTimeout(30000);
                getConn.connect();

                int total = getConn.getContentLength();
                in = getConn.getInputStream();

                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();
                File outFile = new File(dir, safeName);
                out = new FileOutputStream(outFile);

                byte[] buf = new byte[8192];
                int read; long downloaded = 0;
                while ((read = in.read(buf)) != -1) {
                    out.write(buf, 0, read);
                    downloaded += read;
                    if (total > 0) publishProgress((int) (downloaded * 100 / total));
                }
                return outFile;
            } catch (Exception e) {
                IkLog.e(TAG, "Error in doInBackground ", e);
                mErrorMsg = e.getMessage();
                return null;
            } finally {
                try { if (in != null) in.close(); } catch (Exception ignored) {}
                try { if (out != null) out.close(); } catch (Exception ignored) {}
                if (headConn != null) headConn.disconnect();
                if (getConn != null) getConn.disconnect();
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            mDownloadProgress.setProgress(values[0]);
            mTvDownloadStatus.setText("Downloading... " + values[0] + "%");
        }

        @Override
        protected void onPostExecute(File file) {
            mBtnDownload.setEnabled(true);
            if (file != null) {
                mDownloadProgress.setVisibility(View.GONE);
                mTvDownloadStatus.setText("Saved: " + file.getName());
                Toast.makeText(getActivity(), "Download complete: " + file.getName(), Toast.LENGTH_LONG).show();
            } else {
                mTvDownloadStatus.setText("Download failed: " + mErrorMsg);
            }
        }
    }

    // =========================================================================
    // Chat
    // =========================================================================

    private void loadChatHistory() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<ChatMessage> history = mChatDb.getMessagesForUser(mMyUsername);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mChatFragment != null) mChatFragment.setMessages(history);
                        }
                    });
                }
            }
        }).start();
    }

    public void clearChatHistory() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                mChatDb.deleteMessagesForUser(mMyUsername);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mChatFragment != null) mChatFragment.setMessages(new ArrayList<ChatMessage>());
                            showToast("Chat history cleared");
                        }
                    });
                }
            }
        }).start();
    }

    private void sendChatMessage(String text) {
        if (TextUtils.isEmpty(text)) return;
        String username = mMyUsername;
        String realName = mMyRealName;
        long timestamp = System.currentTimeMillis();
        boolean isPrivate = false;
        String targetUser = "";
        String visibleText = text;
        if (text.startsWith("@")) {
            int space = text.indexOf(' ');
            if (space > 1) {
                targetUser = text.substring(1, space).trim();
                visibleText = text.substring(space + 1).trim();
                if (!visibleText.isEmpty()) isPrivate = true;
            }
        }
        final ChatMessage msg = new ChatMessage(username, realName, text, timestamp, true, isPrivate, targetUser);
        new Thread(new Runnable() {
            @Override
            public void run() {
                mChatDb.insertMessage(msg);
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mChatFragment != null) mChatFragment.addMessage(msg);
                    }
                });
            }
        }).start();

        if (mRole == ROLE_HOST) {
            if (mHostState.controlServer != null) {
                if (isPrivate) {
                    mHostState.controlServer.hostSendToUser(targetUser,
                            "PCHAT:" + targetUser + "|" + username + "|" + realName + "|" + visibleText + "|" + timestamp);
                } else {
                    mHostState.controlServer.hostBroadcast(
                            "CHAT:" + username + "|" + realName + "|" + text + "|" + timestamp, null);
                }
            }
        } else {
            if (isPrivate) {
                sendCommand("PCHAT:" + targetUser + "|" + username + "|" + realName + "|" + visibleText + "|" + timestamp);
            } else {
                sendCommand("CHAT:" + username + "|" + realName + "|" + text + "|" + timestamp);
            }
        }
    }

    private void deliverChatMessage(final ChatMessage msg) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                mChatDb.insertMessage(msg);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mChatFragment != null) mChatFragment.addMessage(msg);
                            MultiPanelLayout mp = (MultiPanelLayout) getView().findViewById(R.id.multi_panel);
                            if (mp != null && !mp.isPanelExpanded(MultiPanelLayout.PANEL_CHAT)) {
                                mUnreadChat++;
                                mTvUnreadCount.setText(String.valueOf(mUnreadChat));
                            }
                        }
                    });
                }
            }
        }).start();
    }

    // =========================================================================
    // HostCallbacks implementation
    // =========================================================================

    @Override
    public void onClientConnected(final User user, Socket socket) {
        synchronized (mConnectedUsers) { mConnectedUsers.add(user); }
        if (mHostState.controlServer != null) {
            mHostState.controlServer.hostSendUserList();
            mHostState.controlServer.hostSendToUser(user.getUsername(), "SESSION:" + mSessionId);
        }
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                refreshMembersUI();
                if (mCurrentUri != null && mMediaType != MEDIA_NONE) {
                    String type = (mMediaType == MEDIA_VIDEO) ? "VIDEO" : "AUDIO";
                    mHostState.controlServer.hostSendToUser(user.getUsername(),
                            "MEDIA:" + type + "|" + buildStreamUrl() + "|" + mCurrentTitle + "|" + mMyUsername + "|" + mMyRealName);
                    long pos = mVideoView.getCurrentPosition();
                    boolean play = mVideoView.isPlaying();
                    mHostState.controlServer.hostSendSyncState(user.getUsername(), play, pos);
                }
            }
        });
    }

    @Override
    public void onClientDisconnected(final User user) {
        synchronized (mConnectedUsers) { mConnectedUsers.remove(user); }
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                removeRemoteMediaByUser(user.getUsername());
                refreshMembersUI();
                showToast(user.getRealName() + " left the session");
            }
        });
    }

    @Override
    public void onPlayPause(final boolean play, final User from) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (play) {
                    mVideoView.start();
                    mBtnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
                } else {
                    mVideoView.pause();
                    mBtnPlayPause.setImageResource(android.R.drawable.ic_media_play);
                }
                mIsPlaying = play;
                updateTimeDisplay();
                mHostState.controlServer.hostBroadcast("PLAY:" + play, from);
            }
        });
    }

    @Override
    public void onSeek(final long position, final User from) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mVideoView.seekTo((int) position, false);
                if (mRole == ROLE_HOST) {
                    mHostState.controlServer.hostBroadcast("SEEK:" + position, from);
                }
            }
        });
    }

    @Override
    public void onChatMessage(String sessionId, String username, String realName, String message,
                              long timestamp, boolean isPrivate, String targetUsername, User from) {
        final ChatMessage msg = new ChatMessage(username, realName, message, timestamp, false, isPrivate, targetUsername);
        deliverChatMessage(msg);
    }

    @Override
    public void onSyncRequest(final User from) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                long pos = mVideoView.getCurrentPosition();
                boolean play = mVideoView.isPlaying();
                mHostState.controlServer.hostSendSyncState(from.getUsername(), play, pos);
            }
        });
    }

    @Override
    public void onDownloadRequest(final User from) {
        String url = buildStreamUrl();
        if (!TextUtils.isEmpty(url)) {
            mHostState.controlServer.hostSendDownloadUrl(from.getUsername(), url);
        }
    }

    @Override
    public void onMediaChange(final String type, final String url, final String title, final User from) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                RemoteMediaInfo media = new RemoteMediaInfo(from.getUsername(), from.getRealName(), type, url, title);
                addRemoteMedia(media);
            }
        });
    }

    @Override
    public void onShareRequest(final String type, final String url, final String title, final User fromUser) {
        mPendingShareType = type;
        mPendingShareUrl = url;
        mPendingShareTitle = title;
        mPendingShareUser = fromUser;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new IkBeautifulDialog(getActivity())
                        .setMessage(fromUser.getRealName() + " wants to share:\n" + title + "\n\nApprove?")
                        .setPositiveButton("Approve", new IkBeautifulDialog.OnPositiveClickListener() {
                            @Override
                            public void onClick() {
                                if (mHostState.controlServer != null) {
                                    mHostState.controlServer.hostBroadcastMediaChange(type, url, title,
                                            fromUser.getUsername(), fromUser.getRealName());
                                }
                                RemoteMediaInfo media = new RemoteMediaInfo(fromUser.getUsername(), fromUser.getRealName(),
                                        type, url, title);
                                addRemoteMedia(media);
                                mMediaType = type.equals("VIDEO") ? MEDIA_VIDEO : MEDIA_AUDIO;
                                playRemoteMedia(media);
                                mHostState.controlServer.hostSendToUser(fromUser.getUsername(),
                                        "SHARE_APPROVED:" + type + "|" + url + "|" + title);
                                mPendingShareType = null;
                            }
                        })
                        .setNegativeButton("Reject", new IkBeautifulDialog.OnNegativeClickListener() {
                            @Override
                            public void onClick() {
                                mPendingShareType = null;
                            }
                        })
                        .show();
            }
        });
    }

    @Override
    public void onStateChanged(boolean paused) {
        mIsPlaying = !paused;
        mBtnPlayPause.setImageResource(paused ? android.R.drawable.ic_media_play : android.R.drawable.ic_media_pause);
        updateTimeDisplay();
    }

    // =========================================================================
    // ClientCallbacks implementation
    // =========================================================================

    @Override
    public void onConnected(final String hostIp) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mRole = ROLE_CLIENT;
                mHostIp = hostIp;
                mClientState.localP2pIp = mClientState.controlConnection.clientGetLocalIpAddress();
                updateNetworkPanelUI();
                mTvNetStatus.setText("Connected");
                mHostLost = false;
                sendCommand("SYNC_REQUEST");
                loadChatHistory();
            }
        });
    }

    @Override
    public void onConnectionFailed(final String reason) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                resetToOffline();
                showConnectionFailureDialog();
            }
        });
    }

    @Override
    public void onDisconnected() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mRole == ROLE_CLIENT && !mHostLost) {
                    mHostLost = true;
                    mLastKnownUsers.clear();
                    synchronized (mConnectedUsers) { mLastKnownUsers.addAll(mConnectedUsers); }
                    mLastMediaUrl = buildStreamUrl();
                    mLastMediaPosition = mVideoView.getCurrentPosition();
                    mTvNetStatus.setText("Host lost, finding new host...");
                    mHostMigrationHandler.postDelayed(mHostMigrationRunnable, 800);
                } else {
                    resetToOffline();
                }
            }
        });
    }

    @Override
    public void onMessageReceived(final String line) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                processClientMessage(line);
            }
        });
    }

    // =========================================================================
    // Host migration
    // =========================================================================

    private Runnable mHostMigrationRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mHostLost || mRole != ROLE_CLIENT) return;
            clientAttemptHostMigration();
        }
    };

    private void clientAttemptHostMigration() {
        List<User> candidates = new ArrayList<>();
        for (User u : mLastKnownUsers) {
            if (!u.getUsername().equals(mMyUsername)) candidates.add(u);
        }
        Collections.sort(candidates, new Comparator<User>() {
            @Override
            public int compare(User a, User b) { return a.getUsername().compareTo(b.getUsername()); }
        });
        if (candidates.isEmpty()) {
            clientPromoteToHost();
            return;
        }
        clientTryNextCandidate(candidates, 0);
    }

    private void clientTryNextCandidate(final List<User> candidates, final int index) {
        if (index >= candidates.size()) { clientPromoteToHost(); return; }
        final User candidate = candidates.get(index);
        new Thread(new Runnable() {
            @Override
            public void run() {
                Socket test = new Socket();
                try {
                    test.connect(new InetSocketAddress(candidate.getDeviceAddress(), PORT_CONTROL), 2000);
                    test.close();
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            clientConnectToHost(candidate.getDeviceAddress());
                        }
                    });
                } catch (IOException e) {
                    IkLog.e(TAG, "Error in clientTryNextCandidate", e);
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            clientTryNextCandidate(candidates, index + 1);
                        }
                    });
                }
            }
        }).start();
    }

    private void clientPromoteToHost() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new IkBeautifulDialog(getActivity())
                        .setMessage("You are now the host. Reconnecting members...")
                        .setPositiveButton("OK", null)
                        .show();
                try { resetToOffline(); } catch (Throwable e) { IkLog.e(TAG, "Error trying to reset to offline in \nclientPromoteToHost", e); }
                try {
                    if (mClientState.controlConnection != null) {
                        mClientState.controlConnection.clientDisconnect();
                        mClientState.controlConnection = null;
                    }
                    mRole = ROLE_NONE;
                } catch (Exception e) {}
                mHostLost = false;
                getWiFiDirectManager().disconnect();
                mUiHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startHosting();
                    }
                }, 1000);
            }
        });
    }

    // =========================================================================
    // Client message processing
    // =========================================================================

    private void processClientMessage(String line) {
        if (line == null) return;
        if (line.startsWith("SESSION:")) {
            mSessionId = line.substring(8).trim();
            updateNetworkPanelUI();
            loadChatHistory();
        } else if (line.startsWith("USERLIST:")) {
            parseAndUpdateUserList(line.substring(9));
        } else if (line.startsWith("MEDIA:")) {
            String[] p = line.substring(6).split("\\|", 5);
            if (p.length >= 5) {
                if (p[3].equals(mMyUsername)) {
                    IkLog.d(TAG, "Ignoring own MEDIA broadcast");
                    return;
                }
                RemoteMediaInfo media = new RemoteMediaInfo(p[3], p[4], p[0], p[1], p[2]);
                playRemoteMedia(media);
                addRemoteMedia(media);
                sendCommand("SYNC_REQUEST");
                updateTimeDisplay();
            }
        } else if (line.startsWith("SYNC_STATE:")) {
            String[] parts = line.substring(11).split("\\|", 2);
            if (parts.length >= 2) {
                boolean play = Boolean.parseBoolean(parts[0]);
                long pos = parseLong(parts[1], 0);
                mVideoView.seekTo((int) pos, true);
                if (play) { mVideoView.start(); mBtnPlayPause.setImageResource(android.R.drawable.ic_media_pause); mIsPlaying = true; }
                updateTimeDisplay();
            }
        } else if (line.startsWith("PLAY:")) {
            boolean play = Boolean.parseBoolean(line.substring(5));
            if (play) { mVideoView.start(); mBtnPlayPause.setImageResource(android.R.drawable.ic_media_pause); }
            else { mVideoView.pause(); mBtnPlayPause.setImageResource(android.R.drawable.ic_media_play); }
            mIsPlaying = play;
            updateTimeDisplay();
        } else if (line.startsWith("SEEK:")) {
            long pos = parseLong(line.substring(5), 0);
            mVideoView.seekTo((int) pos, true);
        } else if (line.startsWith("DOWNLOAD_URL:")) {
            mHostDownloadUrl = line.substring(13).trim();
            startDownload(mHostDownloadUrl, mCurrentTitle != null ? mCurrentTitle : "download",
                    mMediaType == MEDIA_VIDEO ? "VIDEO" : "AUDIO");
        } else if (line.startsWith("CHAT:")) {
            parseBroadcastChat(line.substring(5));
        } else if (line.startsWith("PCHAT:")) {
            parsePrivateChat(line.substring(6));
        } else if (line.startsWith("ALBUM_ART:")) {
            String artData = line.substring(10);
            if ("DEFAULT".equals(artData)) {
                mAlbumArtView.setImageBitmap(MediaUtils.getDefaultAlbumArt(getActivity()));
            } else {
                try {
                    byte[] bytes = Base64.decode(artData, Base64.DEFAULT);
                    mAlbumArtView.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
                } catch (Exception e) {
                    IkLog.e(TAG, "Error setting AlbumArt: " + e.getMessage(), e);
                }
            }
        } else if (line.startsWith("SHARE_APPROVED:")) {
            showToast("Your media was approved and shared");
        } else if (line.startsWith("SHARE_REJECTED:")) {
            showToast("Host rejected your media: " + line.substring(15));
        }
    }

    private void parseBroadcastChat(String payload) {
        String[] p = payload.split("\\|", 4);
        if (p.length < 4) return;
        ChatMessage msg = new ChatMessage(p[0], p[1], p[2], parseLong(p[3], System.currentTimeMillis()), false);
        deliverChatMessage(msg);
    }

    private void parsePrivateChat(String payload) {
        String[] p = payload.split("\\|", 5);
        if (p.length < 5) return;
        ChatMessage msg = new ChatMessage(p[1], p[2], p[3], parseLong(p[4], System.currentTimeMillis()), false, true, p[0]);
        deliverChatMessage(msg);
    }

    private void parseAndUpdateUserList(String data) {
        synchronized (mConnectedUsers) {
            mConnectedUsers.clear();
            if (data != null && !data.isEmpty()) {
                String[] users = data.split(",");
                for (String u : users) {
                    String[] p = u.split("\\|", 2);
                    if (p.length >= 2) mConnectedUsers.add(new User(p[0].trim(), p[1].trim()));
                }
            }
            if (mRole == ROLE_HOST) {
                User hostUser = new User(mMyUsername, mMyRealName);
                if (!mConnectedUsers.contains(hostUser)) mConnectedUsers.add(hostUser);
            }
        }
        refreshMembersUI();
        refreshNetworkMediaList();
    }

    // =========================================================================
    // Remote media management
    // =========================================================================

    private void addRemoteMedia(RemoteMediaInfo media) {
        IkLog.d(TAG, "addRemoteMedia: " + media.title + " from " + media.username);
        if (media == null || media.url == null) return;
        if (media.username.equals(mMyUsername)) return;
        if (!mRemoteMediaMap.containsKey(media.title)) {
            mRemoteMediaMap.put(media.title, media);
            mRemoteMediaList.add(media);
        } else {
            mRemoteMediaMap.put(media.title, media);
            for (int i = 0; i < mRemoteMediaList.size(); i++) {
                if (mRemoteMediaList.get(i).title.equals(media.title)) {
                    mRemoteMediaList.set(i, media);
                    break;
                }
            }
        }
        refreshNetworkMediaList();
    }

    private void removeRemoteMediaByUser(String username) {
        List<RemoteMediaInfo> toRemove = new ArrayList<>();
        for (RemoteMediaInfo info : mRemoteMediaList) {
            if (info.username.equals(username)) toRemove.add(info);
        }
        for (RemoteMediaInfo info : toRemove) {
            mRemoteMediaList.remove(info);
            mRemoteMediaMap.remove(info.title);
        }
        refreshNetworkMediaList();
    }

    private void refreshNetworkMediaList() {
        if (mNetworkMediaAdapter != null) {
            mNetworkMediaAdapter.setMediaList(new ArrayList<>(mRemoteMediaList));
            mNetworkMediaAdapter.notifyDataSetChanged();
        }
        if (mTvNetworkMediaCount != null) {
            mTvNetworkMediaCount.setText(mRemoteMediaList.size() + " items");
        }
    }

    private void refreshMembersUI() {
        List<User> userList = new ArrayList<>();
        synchronized (mConnectedUsers) {
            for (User u : mConnectedUsers) {
                if (u.getUsername().equals(mMyUsername)) userList.add(new User(mMyUsername, "Me"));
                else userList.add(u);
            }
        }
        mMembersAdapter.setUsers(userList);
        mMembersAdapter.notifyDataSetChanged();
        if (mChatFragment != null) mChatFragment.setMembers(userList);
    }

    // =========================================================================
    // Host / Client startup and shutdown
    // =========================================================================

    private void startHosting() {
        mTvNetStatus.setText("Starting servers...");
        mBtnStartHost.setEnabled(false);
        synchronized (mConnectedUsers) { mConnectedUsers.clear(); }
        mRemoteMediaMap.clear();
        mRemoteMediaList.clear();
        refreshNetworkMediaList();

        mHostState.httpServer = new HostHttpServer(PORT_HTTP, getActivity());
        mHostState.controlServer = new HostControlServer(getActivity(), PORT_CONTROL, this);
        try { mHostState.httpServer.hostStart(); }
        catch (IOException e) { IkLog.e(TAG, "Host Http server failed", e); showToast("HTTP server failed"); resetToOffline(); return; }
        try { mHostState.controlServer.start(); }
        catch (IOException e) {
            IkLog.e(TAG, "Host control server failed", e);
            mHostState.httpServer.hostStop();
            showToast("Control server failed"); resetToOffline(); return;
        }

        mTvNetStatus.setText("Creating Group...");
        getWiFiDirectManager().createGroupAsHost(new WiFiDirectManager.ConnectionListener() {
            @Override
            public void onConnectionInfoAvailable(InetAddress addr, boolean isGroupOwner) {
                mGroupOwnerIp = addr.getHostAddress();
                mLocalP2pIp = mGroupOwnerIp;
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mPendingAction = ACTION_NONE;
                        mRole = ROLE_HOST;
                        if (sPersistentSessionId.isEmpty()) sPersistentSessionId = UUID.randomUUID().toString();
                        mSessionId = sPersistentSessionId;
                        mHostState.controlServer.setSessionId(mSessionId);
                        updateNetworkPanelUI();
                        mTvNetStatus.setText("Hosting");
                        User hostUser = new User(mMyUsername, mMyRealName);
                        mConnectedUsers.add(hostUser);
                        refreshMembersUI();
                        if (mCurrentUri != null && mMediaType != MEDIA_NONE) {
                            String type = (mMediaType == MEDIA_VIDEO) ? "VIDEO" : "AUDIO";
                            mHostState.controlServer.hostBroadcastMediaChange(type, buildStreamUrl(), mCurrentTitle,
                                    mMyUsername, mMyRealName);
                        }
                        new IkBeautifulDialog(getActivity()).setMessage("Group created.\nYou are Host.")
                                .setPositiveButton("OK", null).show();
                    }
                });
            }
            @Override public void onDisconnected() { resetToOffline(); }
            @Override public void onConnectionFailed(String reason) { resetToOffline(); showToast("Host failed: " + reason); }
        });
    }

    private void clientConnectToHost(String hostIp) {
        if (mClientState.controlConnection != null) mClientState.controlConnection.clientDisconnect();
        mClientState.controlConnection = new ClientControlConnection(this);
        mClientState.controlConnection.clientConnect(hostIp, PORT_CONTROL, mMyUsername, mMyRealName);
    }

    private void resetToOffline() {
        if (mRole == ROLE_HOST) {
            if (mHostState.controlServer != null) mHostState.controlServer.stop();
            if (mHostState.httpServer != null) mHostState.httpServer.hostStop();
        } else if (mRole == ROLE_CLIENT) {
            if (mClientState.controlConnection != null) mClientState.controlConnection.clientDisconnect();
            if (mClientState.httpServer != null) mClientState.httpServer.clientStop();
        }
        mRole = ROLE_NONE;
        mMediaType = MEDIA_NONE;
        mIsPlaying = false;
        mSessionId = "";
        mGroupOwnerIp = null;
        mLocalP2pIp = null;
        mHostDownloadUrl = "";
        mCurrentUri = null;
        mCurrentTitle = null;
        mCurrentLocalItem = null;
        mPendingAction = ACTION_NONE;
        mPeerDialogShowing = false;
        mHostLost = false;
        synchronized (mConnectedUsers) { mConnectedUsers.clear(); }
        mRemoteMediaMap.clear();
        mRemoteMediaList.clear();
        refreshMembersUI();
        refreshNetworkMediaList();
        updateNetworkPanelUI();
        getWiFiDirectManager().disconnect();
    }

    private void disconnect() {
        resetToOffline();
        mTvNetStatus.setText("Offline");
        mTvGroupName.setText("No active session");
    }

    // =========================================================================
    // Settings chain (WiFi / Location / Nearby)
    // =========================================================================

    private void checkSettingsAndStartHost() { mPendingAction = ACTION_START_HOST; runSettingsChain(); }
    private void checkSettingsAndDiscover() { mPendingAction = ACTION_DISCOVER; runSettingsChain(); }

    private void runSettingsChain() {
        try {
            if (getActivity() == null || mPendingAction == ACTION_NONE) return;
            final WiFiDirectManager wdm = getWiFiDirectManager();
            if (!wdm.isWifiEnabled()) {
                wdm.autoEnableWifiWithFallback(getActivity());
                mUiHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (wdm.isWifiEnabled()) continueSettingsChain();
                        else showRequiredSettingsDialog("Wi-Fi Required", "Enable Wi-Fi manually.",
                                WiFiDirectManager.ERROR_WIFI_DISABLED);
                    }
                }, 1000);
                return;
            }
            continueSettingsChain();
        } catch (Exception e) {
            IkLog.e(TAG, " Error in runSettingsChain ", e);
        }
    }

    private void continueSettingsChain() {
        try {
            WiFiDirectManager wdm = getWiFiDirectManager();
            List<String> perms = new ArrayList<>();

            if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT <= 32) {
                if (getActivity().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
                }
            }

            if (Build.VERSION.SDK_INT >= 33) {
                if (getActivity().checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                    perms.add(Manifest.permission.NEARBY_WIFI_DEVICES);
                }
            }

            if (!perms.isEmpty()) {
                requestPermissions(perms.toArray(new String[0]), PERM_NEARBY);
                return;
            }

            if (Build.VERSION.SDK_INT <= 32 && !wdm.isLocationEnabled()) {
                showRequiredSettingsDialog("Location Required", "Enable location.", WiFiDirectManager.ERROR_LOCATION_DISABLED);
                return;
            }

            boolean canCheckBluetooth = Build.VERSION.SDK_INT < 31 ||
                    getActivity().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
            if (canCheckBluetooth && wdm.isBluetoothEnabled()) {
                showBluetoothSuggestionDialog();
                return;
            }

            if (mPendingAction == ACTION_START_HOST) startHosting();
            else discoverPeers();
        } catch (Exception e) {
            IkLog.e(TAG, " Error in continueSettingsChain", e);
        }
    }

    private void showRequiredSettingsDialog(String title, String msg, final int code) {
        new IkBeautifulDialog(getActivity())
                .setMessage(msg)
                .setPositiveButton("Open Settings", new IkBeautifulDialog.OnPositiveClickListener() {
                    @Override
                    public void onClick() {
                        String action = WiFiDirectManager.getSettingsIntentAction(code);
                        if (action != null) startActivity(new Intent(action));
                    }
                })
                .setNegativeButton("Cancel", new IkBeautifulDialog.OnNegativeClickListener() {
                    @Override public void onClick() { mPendingAction = ACTION_NONE; }
                }).show();
    }

    private void showBluetoothSuggestionDialog() {
        new IkBeautifulDialog(getActivity())
                .setMessage("Bluetooth is on. Turn it off?")
                .setPositiveButton("Turn Off", new IkBeautifulDialog.OnPositiveClickListener() {
                    @Override public void onClick() { startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)); }
                })
                .setNegativeButton("Continue", new IkBeautifulDialog.OnNegativeClickListener() {
                    @Override public void onClick() { if (mPendingAction == ACTION_START_HOST) startHosting(); else discoverPeers(); }
                }).show();
    }

    private void discoverPeers() {
        try {
            mTvNetStatus.setText("Discovering...");
            mBtnJoinSession.setEnabled(false);
            getWiFiDirectManager().discoverPeers(new WiFiDirectManager.PeerListListener() {
                @Override
                public void onPeersAvailable(final List<WifiP2pDevice> peers) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mBtnJoinSession.setEnabled(true);
                            if (peers.isEmpty()) {
                                if (mRetryCount < 2) { mRetryCount++; discoverPeers(); }
                                else { mPendingAction = ACTION_NONE; showNoPeersFoundDialog(); }
                            } else {
                                mRetryCount = 0; mPendingAction = ACTION_NONE;
                                if (mRole == ROLE_NONE && !mPeerDialogShowing) showPeerPickerDialog(peers);
                            }
                        }
                    });
                }
                @Override public void onDiscoveryStopped() {}
            });
        } catch (Exception e) {
            IkLog.e(TAG, "Error in discoverPeers, ", e);
        }
    }

    private void showPeerPickerDialog(final List<WifiP2pDevice> peers) {
        if (mRole != ROLE_NONE || mPeerDialogShowing) return;
        mPeerDialogShowing = true;
        final String[] names = new String[peers.size()];
        for (int i = 0; i < peers.size(); i++) {
            names[i] = (peers.get(i).deviceName != null ? peers.get(i).deviceName : "Unknown") + "_Onwa_Group";
        }
        new IkBeautifulDialog(getActivity())
                .setMessage("Choose a host")
                .setItems(names, new IkBeautifulDialog.OnItemClickListener() {
                    @Override
                    public void onItemClick(int pos, String item) {
                        mPeerDialogShowing = false;
                        connectToPeer(peers.get(pos));
                    }
                })
                .setNegativeButton("Cancel", new IkBeautifulDialog.OnNegativeClickListener() {
                    @Override public void onClick() { mPeerDialogShowing = false; }
                }).showList();
    }

    private void connectToPeer(WifiP2pDevice device) {
        try {
            mTvNetStatus.setText("Connecting...");
            mBtnJoinSession.setEnabled(false);
            getWiFiDirectManager().connectToGroup(device, new WiFiDirectManager.ConnectionListener() {
                @Override
                public void onConnectionInfoAvailable(InetAddress addr, boolean isGroupOwner) {
                    mGroupOwnerIp = addr.getHostAddress();
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mBtnJoinSession.setEnabled(true);
                            clientConnectToHost(mGroupOwnerIp);
                        }
                    });
                }
                @Override public void onDisconnected() { resetToOffline(); }
                @Override public void onConnectionFailed(String reason) { showConnectionFailureDialog(); }
            });
        } catch (Exception e) {
            IkLog.e(TAG, "Error in connectToPeer ", e);
        }
    }

    private void showNoPeersFoundDialog() {
        new IkBeautifulDialog(getActivity())
                .setMessage("No hosts found. Retry?")
                .setPositiveButton("Retry", new IkBeautifulDialog.OnPositiveClickListener() {
                    @Override public void onClick() { discoverPeers(); }
                })
                .setNegativeButton("Cancel", new IkBeautifulDialog.OnNegativeClickListener() {
                    @Override public void onClick() { mBtnRetry.setVisibility(View.VISIBLE); mBtnJoinSession.setVisibility(View.GONE); }
                }).show();
    }

    private void showConnectionFailureDialog() {
        new IkBeautifulDialog(getActivity())
                .setMessage("Connection failed. Retry?")
                .setPositiveButton("Retry", new IkBeautifulDialog.OnPositiveClickListener() {
                    @Override public void onClick() { checkSettingsAndDiscover(); }
                })
                .setNegativeButton("Cancel", new IkBeautifulDialog.OnNegativeClickListener() {
                    @Override public void onClick() { mBtnRetry.setVisibility(View.VISIBLE); mBtnJoinSession.setVisibility(View.GONE); }
                }).show();
    }

    // =========================================================================
    // Permissions result
    // =========================================================================

    @Override
    public void onRequestPermissionsResult(int reqCode, String[] perms, int[] grants) {
        boolean allGranted = true;
        if (grants.length > 0) {
            for (int res : grants) {
                if (res != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
        } else {
            allGranted = false;
        }

        if (allGranted) {
            if (reqCode == PERM_READ) {
                doMediaScan();
            } else if (reqCode == PERM_WRITE) {
                sendCommand("DOWNLOAD_REQUEST");
            } else if (reqCode == PERM_LOC || reqCode == PERM_NEARBY) {
                runSettingsChain();
            }
        } else {
            if (reqCode == PERM_LOC || reqCode == PERM_NEARBY) {
                mPendingAction = ACTION_NONE;
                showToast("WiFi Direct requires location/nearby permissions");
                mBtnStartHost.setEnabled(true);
                mBtnJoinSession.setEnabled(true);
            } else if (reqCode == PERM_READ) {
                showToast("Storage permission required to load media");
            }
        }
    }

    // =========================================================================
    // Inner adapters
    // =========================================================================

    private class MembersAdapter extends RecyclingAdapter {
        private List<User> mUsers = new ArrayList<>();
        void setUsers(List<User> users) { mUsers.clear(); if (users != null) mUsers.addAll(users); notifyDataSetChanged(); }
        @Override public int getItemCount() { return mUsers.size(); }
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setPadding(32, 16, 32, 16);
            tv.setTextColor(0xCC8B4513);
            tv.setTextSize(13f);
            tv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new SimpleHolder(tv);
        }
        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            User u = mUsers.get(position);
            ((SimpleHolder) holder).tvText.setText(u.getRealName() + " (" + u.getUsername() + ")");
        }
        private class SimpleHolder extends RecyclingAdapter.ViewHolder { TextView tvText; SimpleHolder(TextView v) { super(v); tvText = v; } }
    }

    private class NetworkMediaAdapter extends RecyclingAdapter {
        private List<RemoteMediaInfo> mMediaList = new ArrayList<>();
        private OnMediaClickListener mListener;
        void setOnMediaClickListener(OnMediaClickListener l) { mListener = l; }
        void setMediaList(List<RemoteMediaInfo> list) { mMediaList.clear(); if (list != null) mMediaList.addAll(list); notifyDataSetChanged(); }
        @Override public int getItemCount() { return mMediaList.size(); }
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new NetworkMediaHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_network_media, parent, false));
        }
        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            final RemoteMediaInfo media = mMediaList.get(position);
            NetworkMediaHolder h = (NetworkMediaHolder) holder;
            h.tvTitle.setText(media.title);
            h.tvOwner.setText("by " + media.realName);
            h.tvType.setText(media.mediaType);
            h.btnPlay.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { if (mListener != null) mListener.onMediaClick(media); }
            });
            h.btnDownload.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { if (mListener != null) mListener.onDownloadClick(media); }
            });
        }
        private class NetworkMediaHolder extends RecyclingAdapter.ViewHolder {
            TextView tvTitle, tvOwner, tvType; View btnPlay, btnDownload;
            NetworkMediaHolder(View v) {
                super(v);
                tvTitle = v.findViewById(R.id.tv_media_title);
                tvOwner = v.findViewById(R.id.tv_media_owner);
                tvType = v.findViewById(R.id.tv_media_type);
                btnPlay = v.findViewById(R.id.btn_play);
                btnDownload = v.findViewById(R.id.btn_download);
            }
        }
    }

    // =========================================================================
    // Share request helper
    // =========================================================================

    private void requestShareMedia(String type, String url, String title) {
        if (mRole != ROLE_CLIENT) return;
        sendCommand("SHARE_REQUEST:" + type + "|" + url + "|" + title);
        showToast("Share request sent to host");
    }
}