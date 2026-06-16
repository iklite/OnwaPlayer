package com.ikechi.studio.onwa.player.net;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.*;
import java.util.*;

/**
 * TCP client connection to the host's control server.
 */
public class ClientControlConnection {

    private static final String TAG = "ClientControlConnection";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int HEARTBEAT_INTERVAL_MS = 10000;

    public interface ClientCallbacks {
        void onConnected(String hostIp);
        void onConnectionFailed(String reason);
        void onDisconnected();
        void onMessageReceived(String message);
    }

    private final ClientCallbacks mCallbacks;
    private final Handler mMainHandler;
    private Socket mSocket;
    private PrintWriter mOut;
    private BufferedReader mIn;
    private volatile boolean mRunning;
    private Thread mHeartbeatThread;

    public ClientControlConnection(ClientCallbacks callbacks) {
        this(callbacks, new Handler(Looper.getMainLooper()));
    }

    public ClientControlConnection(ClientCallbacks callbacks, Handler mainHandler) {
        mCallbacks = callbacks;
        mMainHandler = mainHandler;
    }

    public void clientConnect(final String hostIp, final int port, final String username, final String realName) {
        new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						mSocket = new Socket();
						mSocket.connect(new InetSocketAddress(hostIp, port), CONNECT_TIMEOUT_MS);
						mSocket.setTcpNoDelay(true);
						mSocket.setKeepAlive(true);
						mOut = new PrintWriter(mSocket.getOutputStream(), true);
						mIn = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
						mOut.println("USER:" + username + "|" + realName);
						mRunning = true;
						mMainHandler.post(new Runnable() {
								@Override
								public void run() { if (mCallbacks != null) mCallbacks.onConnected(hostIp); }
							});
						startHeartbeat();
						String line;
						while (mRunning && (line = mIn.readLine()) != null) {
							final String msg = line;
							mMainHandler.post(new Runnable() {
									@Override
									public void run() { if (mCallbacks != null) mCallbacks.onMessageReceived(msg); }
								});
						}
					} catch (IOException e) {
					    final IOException ioe = e;
						Log.e(TAG, "Connection failed", e);
						mMainHandler.post(new Runnable() {
								@Override
								public void run() { if (mCallbacks != null) mCallbacks.onConnectionFailed(ioe.getMessage()); }
							});
						return;
					} finally {
						clientDisconnect();
						mMainHandler.post(new Runnable() {
								@Override
								public void run() { if (mCallbacks != null) mCallbacks.onDisconnected(); }
							});
					}
				}
			}, "ClientConnectThread").start();
    }

    private void startHeartbeat() {
        mHeartbeatThread = new Thread(new Runnable() {
				@Override
				public void run() {
					while (mRunning) {
						try { Thread.sleep(HEARTBEAT_INTERVAL_MS); if (mRunning) clientSendCommand("PING"); }
						catch (InterruptedException e) { break; }
					}
				}
			}, "ClientHeartbeat");
        mHeartbeatThread.start();
    }

    public void clientSendCommand(final String command) {
        if (mOut == null || !mRunning) return;
        new Thread(new Runnable() {
				@Override
				public void run() {
					try { mOut.println(command); mOut.flush(); }
					catch (Exception e) { Log.e(TAG, "Send failed: " + command, e); }
				}
			}).start();
    }

    public void clientDisconnect() {
        mRunning = false;
        if (mHeartbeatThread != null) { mHeartbeatThread.interrupt(); mHeartbeatThread = null; }
        try { if (mSocket != null) mSocket.close(); } catch (IOException e) {}
        mOut = null; mIn = null;
    }

    public String clientGetLocalIpAddress() {
		if (mSocket != null && mSocket.isConnected()) {
			InetAddress localAddr = mSocket.getLocalAddress();
			if (localAddr != null && !localAddr.isLoopbackAddress()) {
				String ip = localAddr.getHostAddress();
				// If already a P2P address, return it
				if (ip.startsWith("192.168.49.")) {
					return ip;
				}
			}
			// Fallback: scan interfaces for p2p
			try {
				Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
				while (interfaces.hasMoreElements()) {
					NetworkInterface iface = interfaces.nextElement();
					if (iface.getName().startsWith("p2p") && iface.isUp()) {
						Enumeration<InetAddress> addrs = iface.getInetAddresses();
						while (addrs.hasMoreElements()) {
							InetAddress addr = addrs.nextElement();
							if (!addr.isLoopbackAddress() && addr instanceof java.net.Inet4Address) {
								return addr.getHostAddress();
							}
						}
					}
				}
			} catch (SocketException e) {
				Log.e(TAG, "Failed to get network interfaces", e);
			}
			// Final fallback to socket's local address
			if (localAddr != null) return localAddr.getHostAddress();
		}
		return "";
	}

    public boolean clientIsConnected() { return mRunning && mSocket != null && mSocket.isConnected(); }
}
