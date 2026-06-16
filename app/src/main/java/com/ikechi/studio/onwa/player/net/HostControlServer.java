package com.ikechi.studio.onwa.player.net;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.ikechi.studio.onwa.player.models.User;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * TCP control server that runs exclusively on the host.
 *
 * <p>All network I/O is performed on background threads.
 */
public class HostControlServer {

    private static final String TAG = "HostControlServer";
    private static final int ACCEPT_TIMEOUT_MS = 2000;
    private static final int THREAD_POOL_SIZE = 8;
    private static final int SHUTDOWN_TIMEOUT_SEC = 3;
    private static final int HANDSHAKE_TIMEOUT_MS = 10000;
    private static final int HEARTBEAT_INTERVAL_MS = 10000;

    private final int mPort;
    private final Context mContext;
    private final HostCallbacks mCallbacks;

    private ServerSocket mServerSocket;
    private volatile boolean mRunning;
    private String mSessionId = "";

    private final Set<ClientHandler> mClients = new HashSet<ClientHandler>();
    private final Set<User> mUsers = new HashSet<User>();

    private HandlerThread mNetworkThread;
    private Handler mNetworkHandler;
    private ExecutorService mSendExecutor;
    private Thread mAcceptThread;

    public interface HostCallbacks {
        void onClientConnected(User user, Socket socket);
        void onClientDisconnected(User user);
        void onPlayPause(boolean play, User from);
        void onSeek(long position, User from);
        void onChatMessage(String sessionId, String username, String realName,
                           String message, long timestamp, boolean isPrivate,
                           String targetUsername, User from);
        void onSyncRequest(User from);
        void onDownloadRequest(User from);
        void onMediaChange(String type, String url, String title, User from);
        void onShareRequest(String type, String url, String title, User from);
    }

    public HostControlServer(Context context, int port, HostCallbacks callbacks) {
        mContext = context;
        mPort = port;
        mCallbacks = callbacks;

        mNetworkThread = new HandlerThread("HostControlNetwork");
        mNetworkThread.start();
        mNetworkHandler = new Handler(mNetworkThread.getLooper());
        mSendExecutor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    }

    public void setSessionId(String sessionId) {
        mSessionId = sessionId != null ? sessionId : "";
        hostBroadcast("SESSION:" + mSessionId, null);
    }

    public void start() throws IOException {
        if (mRunning) return;
        mServerSocket = new ServerSocket(mPort);
        mServerSocket.setReuseAddress(true);
        mServerSocket.setSoTimeout(ACCEPT_TIMEOUT_MS);
        mRunning = true;

        mAcceptThread = new Thread(new Runnable() {
				@Override
				public void run() {
					Log.d(TAG, "HostControlServer started on port " + mPort);
					while (mRunning) {
						try {
							Socket socket = mServerSocket.accept();
							if (socket != null && mRunning) {
								socket.setTcpNoDelay(true);
								socket.setKeepAlive(true);
								ClientHandler handler = new ClientHandler(socket);
								synchronized (mClients) { mClients.add(handler); }
								new Thread(handler, "HostClient-" + socket.getInetAddress().getHostAddress()).start();
							}
						} catch (SocketTimeoutException ignored) {
						} catch (IOException e) {
							if (mRunning) Log.e(TAG, "accept() failed", e);
						}
					}
					Log.d(TAG, "Accept loop ended");
				}
			}, "HostAcceptThread");
        mAcceptThread.start();
    }

    public void stop() {
        mRunning = false;
        try { if (mServerSocket != null) mServerSocket.close(); } catch (IOException e) {}
        if (mAcceptThread != null) {
            try { mAcceptThread.join(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        if (mSendExecutor != null) {
            mSendExecutor.shutdownNow();
            try { mSendExecutor.awaitTermination(SHUTDOWN_TIMEOUT_SEC, TimeUnit.SECONDS); } catch (InterruptedException e) {}
        }
        synchronized (mClients) {
            for (ClientHandler h : new ArrayList<ClientHandler>(mClients)) h.cleanup();
            mClients.clear();
        }
        synchronized (mUsers) { mUsers.clear(); }
        if (mNetworkThread != null) { mNetworkThread.quit(); mNetworkThread = null; }
        Log.d(TAG, "HostControlServer stopped");
    }

    public void hostBroadcast(final String message, final User exclude) {
        if (mSendExecutor == null || mSendExecutor.isShutdown()) return;
        final List<ClientHandler> snapshot;
        synchronized (mClients) { snapshot = new ArrayList<ClientHandler>(mClients); }
        mSendExecutor.execute(new Runnable() {
				@Override
				public void run() {
					for (ClientHandler h : snapshot) {
						if (exclude == null || h.mUser == null || !h.mUser.equals(exclude)) {
							h.send(message);
						}
					}
				}
			});
    }

    public void hostSendToUser(final String username, final String message) {
        if (mSendExecutor == null || mSendExecutor.isShutdown()) return;
        final List<ClientHandler> snapshot;
        synchronized (mClients) { snapshot = new ArrayList<ClientHandler>(mClients); }
        mSendExecutor.execute(new Runnable() {
				@Override
				public void run() {
					for (ClientHandler h : snapshot) {
						if (h.mUser != null && h.mUser.getUsername().equals(username)) {
							h.send(message);
							return;
						}
					}
				}
			});
    }

    public void hostSendUserList() {
        StringBuilder sb = new StringBuilder("USERLIST:");
        synchronized (mUsers) {
            boolean first = true;
            for (User u : mUsers) {
                if (!first) sb.append(',');
                sb.append(u.getUsername()).append('|').append(u.getRealName());
                first = false;
            }
        }
        hostBroadcast(sb.toString(), null);
    }

    public void hostSendSyncState(String target, boolean playing, long pos) {
        hostSendToUser(target, "SYNC_STATE:" + playing + "|" + pos);
    }

    public void hostSendDownloadUrl(String target, String url) {
        hostSendToUser(target, "DOWNLOAD_URL:" + url);
    }

    public void hostBroadcastMediaChange(String type, String url, String title,
                                         String username, String realName) {
        hostBroadcast("MEDIA:" + type + "|" + url + "|" + title + "|" + username + "|" + realName, null);
    }

    public Set<User> getConnectedUsers() {
        synchronized (mUsers) { return new HashSet<User>(mUsers); }
    }

    // -------------------------------------------------------------------------
    private class ClientHandler implements Runnable {
        private final Socket mSocket;
        private PrintWriter mOut;
        private BufferedReader mIn;
        private volatile boolean mRunning = true;
        private User mUser;
        private final long mHandshakeStartTime = System.currentTimeMillis();

        ClientHandler(Socket s) { mSocket = s; }

        @Override
        public void run() {
            try {
                mIn = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
                mOut = new PrintWriter(mSocket.getOutputStream(), true);
                String line = null;
                while (line == null && mRunning) {
                    try {
                        mSocket.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
                        line = mIn.readLine();
                        mSocket.setSoTimeout(0);
                    } catch (SocketTimeoutException e) {
                        if (System.currentTimeMillis() - mHandshakeStartTime > HANDSHAKE_TIMEOUT_MS) {
                            cleanup(); return;
                        }
                    }
                }
                if (line == null || !line.startsWith("USER:")) { cleanup(); return; }
                String[] parts = line.substring(5).split("\\|", 2);
                if (parts.length < 2) { cleanup(); return; }
                mUser = new User(parts[0].trim(), parts[1].trim(), mSocket.getInetAddress().getHostAddress());
                synchronized (mUsers) { mUsers.add(mUser); }
                send("SESSION:" + mSessionId);
                hostSendUserList();
                if (mCallbacks != null) mCallbacks.onClientConnected(mUser, mSocket);
                startHeartbeat();
                while (mRunning && (line = mIn.readLine()) != null) {
                    if ("PING".equals(line)) { send("PONG"); continue; }
                    dispatch(line);
                }
            } catch (IOException e) {
                if (mRunning) Log.e(TAG, "I/O error", e);
            } finally { cleanup(); }
        }

        private void startHeartbeat() {
            new Thread(new Runnable() {
					@Override
					public void run() {
						while (mRunning) {
							try { Thread.sleep(HEARTBEAT_INTERVAL_MS); if (mRunning) send("PING"); }
							catch (InterruptedException e) { break; }
						}
					}
				}).start();
        }

        private void dispatch(String msg) {
            if (msg == null) return;
            if (msg.startsWith("CHAT:")) {
                String[] p = msg.substring(5).split("\\|", 4);
                if (p.length >= 4) {
                    long ts = parseLong(p[3], System.currentTimeMillis());
                    if (mCallbacks != null) mCallbacks.onChatMessage(mSessionId, p[0], p[1], p[2], ts, false, "", mUser);
                    hostBroadcast(msg, mUser);
                }
            } else if (msg.startsWith("PCHAT:")) {
                String[] p = msg.substring(6).split("\\|", 5);
                if (p.length >= 5) {
                    String target = p[0];
                    hostSendToUser(target, msg);
                    long ts = parseLong(p[4], System.currentTimeMillis());
                    if (mCallbacks != null) mCallbacks.onChatMessage(mSessionId, p[1], p[2], p[3], ts, true, target, mUser);
                }
            } else if (msg.startsWith("PLAY:")) {
                boolean play = Boolean.parseBoolean(msg.substring(5));
                if (mCallbacks != null) mCallbacks.onPlayPause(play, mUser);
                hostBroadcast(msg, mUser);
            } else if (msg.startsWith("SEEK:")) {
                long pos = parseLong(msg.substring(5), 0);
                if (mCallbacks != null) mCallbacks.onSeek(pos, mUser);
                hostBroadcast(msg, mUser);
            } else if (msg.startsWith("MEDIA:")) {
                String[] p = msg.substring(6).split("\\|", 5);
                if (p.length >= 5 && mCallbacks != null) mCallbacks.onMediaChange(p[0], p[1], p[2], mUser);
                hostBroadcast(msg, mUser);
            } else if (msg.startsWith("SHARE_REQUEST:")) {
                String[] p = msg.substring(14).split("\\|", 3);
                if (p.length >= 3 && mCallbacks != null) mCallbacks.onShareRequest(p[0], p[1], p[2], mUser);
            } else if ("SYNC_REQUEST".equals(msg)) {
                if (mCallbacks != null) mCallbacks.onSyncRequest(mUser);
            } else if ("DOWNLOAD_REQUEST".equals(msg)) {
                if (mCallbacks != null) mCallbacks.onDownloadRequest(mUser);
            } else if (msg.startsWith("NOW_PLAYING:")) {
                String[] p = msg.substring(12).split("\\|", 3);
                if (p.length >= 3 && mCallbacks != null) mCallbacks.onMediaChange(p[0], p[1], p[2], mUser);
                hostBroadcast(msg, mUser);
            } else if (msg.startsWith("ALBUM_ART:")) {
                hostBroadcast(msg, mUser);
            }
        }

        void send(String msg) {
            if (mOut == null || !mRunning) return;
            try { mOut.println(msg); if (mOut.checkError()) mRunning = false; }
            catch (Exception e) { mRunning = false; }
        }

        void cleanup() {
            if (!mRunning) return;
            mRunning = false;
            try { mIn.close(); } catch (IOException ignored) {}
            try { mOut.close(); } catch (Exception ignored) {}
            try { mSocket.close(); } catch (IOException ignored) {}
            synchronized (mClients) { mClients.remove(this); }
            synchronized (mUsers) { if (mUser != null) mUsers.remove(mUser); }
            if (mCallbacks != null && mUser != null) mCallbacks.onClientDisconnected(mUser);
            hostSendUserList();
        }

        private long parseLong(String s, long fallback) {
            try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return fallback; }
        }
    }
}
