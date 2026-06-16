package com.ikechi.studio.onwa.player.net;

import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.net.wifi.*;
import android.Manifest;
import android.content.pm.PackageManager;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import com.ikechi.studio.IkLog;

/**
 * Manages Wi-Fi Direct operations.
 *
 */
public class WiFiDirectManager
{

    private static final String TAG                    = "WiFiDirectManager";
    private static final int    MAX_RETRIES            = 3;
    private static final long   RETRY_DELAY_MS         = 700;
    private static final long   SETTLE_DELAY_MS        = 300;
    private static final long   GROUP_CREATION_TIMEOUT_MS = 15000;

    public static final int ERROR_WIFI_DISABLED    = -100;
    public static final int ERROR_LOCATION_DISABLED = -101;

    private WifiP2pManager                manager;
    private WifiP2pManager.Channel        channel;
    private WiFiDirectBroadcastReceiver   receiver;
    private Context                       context;
    private ConnectionListener            connectionListener;
    private PeerListListener              peerListListener;
    private DeviceInfoListener            deviceInfoListener;
    private boolean                       isDiscovering        = false;
    private final Handler                 handler = new Handler(Looper.getMainLooper());
    private boolean                       isWifiP2pEnabled     = false;
    private WifiP2pDevice                 thisDevice;

    // Group-creation timeout
    private Runnable mGroupCreationTimeoutRunnable;
    private boolean  mGroupCreationInProgress = false;

    // Duplicate-connection guard
    private boolean  mIsConnected    = false;
    private final Object mConnectionLock = new Object();

    // WiFi auto-enable tracking
    private boolean wifiWasAutoEnabled = false;

    // -------------------------------------------------------------------------
    // Interfaces
    // -------------------------------------------------------------------------

    public interface ConnectionListener
	{
        void onConnectionInfoAvailable(InetAddress groupOwnerAddress, boolean isGroupOwner);
        void onConnectionFailed(String reason);
        void onDisconnected();
    }

    public interface PeerListListener
	{
        void onPeersAvailable(List<WifiP2pDevice> peers);
        void onDiscoveryStopped();
    }

    public interface DeviceInfoListener
	{
        void onDeviceInfoChanged(WifiP2pDevice device);
    }

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public WiFiDirectManager(Context context)
	{
		IkLog.init(context);
		IkLog.setInstantFlush(true);
		try
		{
			this.context = context.getApplicationContext();
			manager = (WifiP2pManager) this.context.getSystemService(Context.WIFI_P2P_SERVICE);
			if (manager != null)
			{
				channel = manager.initialize(this.context, Looper.getMainLooper(), null);
			}
			else
			{
				IkLog.e(TAG, "WifiP2pManager is null");
			}
			receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);
		}
		catch (Exception e)
		{
			IkLog.e(TAG, "Error in " + TAG + " constructor", e);
		}

    }

    // -------------------------------------------------------------------------
    // BroadcastReceiver registration
    // -------------------------------------------------------------------------

    public void registerReceiver()
	{
        if (context == null || receiver == null)
		{
		    String msg = context == null ? "context == null" : "receiver == null";
			IkLog.d(TAG, msg);
			return;
		}

		try
		{
			android.content.IntentFilter filter = new android.content.IntentFilter();
			filter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
			filter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
			filter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
			filter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION); 

			// For Android 13+ (API 33+): must specify RECEIVER_NOT_EXPORTED
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
			} else {
				context.registerReceiver(receiver, filter);
			}
			IkLog.d(TAG, "Receiver registered");
		}
		catch (Exception e)
		{
			IkLog.e(TAG, "error in registerReceiver", e);
		}
    }

    public void unregisterReceiver()
	{
        if (context == null || receiver == null) return;
        try
		{
            context.unregisterReceiver(receiver);
            IkLog.d(TAG, "Receiver unregistered");
        }
		catch (IllegalArgumentException e)
		{
            IkLog.e(TAG, "error in unregisterReceiver: Receiver not registered", e);
        }
    }

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    public void cleanup()
	{
        IkLog.d(TAG, "cleanup: clearing all listeners");
        if (handler != null) handler.removeCallbacksAndMessages(null);
        cancelGroupCreationTimeout();
        connectionListener     = null;
        peerListListener       = null;
        deviceInfoListener     = null;
        mGroupCreationInProgress = false;
        synchronized (mConnectionLock)
		{ mIsConnected = false; }
    }

    // -------------------------------------------------------------------------
    // WiFi auto-enable / disable
    // -------------------------------------------------------------------------

    public boolean autoEnableWifiWithFallback(Context activityContext)
	{
        try
		{
			WifiManager wifiManager = (WifiManager) context.getApplicationContext()
				.getSystemService(Context.WIFI_SERVICE);
			if (wifiManager == null) return false;

			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
			{
				if (wifiManager.isWifiEnabled()) return true;
				try
				{
					boolean result = wifiManager.setWifiEnabled(true);
					if (result)
					{
						wifiWasAutoEnabled = true;
						return true;
					}
				}
				catch (SecurityException e)
				{
					IkLog.e(TAG, "SecurityException enabling WiFi", e);
				}
			}

			wifiWasAutoEnabled = false;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
			{
				activityContext.startActivity(
					new Intent(android.provider.Settings.Panel.ACTION_WIFI));
			}
			else
			{
				activityContext.startActivity(
					new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS));
			}
		}
		catch (Exception e)
		{
			IkLog.e(TAG, "error in autoEnableWifiWithFallback", e);
		}
        return false;
    }

    public void disableAutoEnabledWifi()
	{
        try
		{
			if (!wifiWasAutoEnabled) return;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
			{
				wifiWasAutoEnabled = false;
				return;
			}
			WifiManager wm = (WifiManager) context.getApplicationContext()
				.getSystemService(Context.WIFI_SERVICE);
			if (wm == null)
			{ wifiWasAutoEnabled = false; return; }
			try
			{
				if (wm.isWifiEnabled()) wm.setWifiEnabled(false);
			}
			catch (SecurityException e)
			{
				IkLog.e(TAG, "SecurityException disabling WiFi", e);
			}
			wifiWasAutoEnabled = false;
		}
		catch (Exception e)
		{
			IkLog.e(TAG, "Error in disableAutoEnabledWifi", e);
		}
    }

    public void markWifiEnabledByUser()
	{
        wifiWasAutoEnabled = false;
    }

    // -------------------------------------------------------------------------
    // System state checks
    // -------------------------------------------------------------------------

    public boolean isWifiEnabled()
	{
        try
		{
			WifiManager wm = (WifiManager) context.getApplicationContext()
				.getSystemService(Context.WIFI_SERVICE);
			return wm != null && wm.isWifiEnabled();
		}
		catch (Exception e)
		{
			IkLog.e(TAG, "error in isWifiEnabled", e);
		}
		return false;
    }

    /**
     * Checks whether location services are required and enabled.
     */
    public boolean isLocationEnabled()
	{
        try
        {
            // Android 13+ (API 33+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            {
                // Check if the app has the new NEARBY_WIFI_DEVICES permission
                if (context.checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)
					== PackageManager.PERMISSION_GRANTED)
                {
                    // Permission granted → location does NOT need to be enabled
                    return true;
                }
                else
                {
                    // Permission missing → cannot proceed even if location is on
                    return false;
                }
            }
            else
            {
                // Older Android: location must be enabled for Wi-Fi Direct
                LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
                if (lm == null) return false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                {
                    return lm.isLocationEnabled();
                }
                return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
					|| lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            }
        }
        catch (Exception e)
        {
            IkLog.e(TAG, "error in isLocationEnabled", e);
        }
        return false;
    }

    public boolean isBluetoothEnabled()
	{
        try
		{
            android.bluetooth.BluetoothAdapter adapter =
				android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            return adapter != null && adapter.isEnabled();
        }
		catch (SecurityException e)
		{
            IkLog.e(TAG, "isBluetoothEnabled SecurityException", e);
            return false;
        }
    }

    public boolean isWifiP2pEnabled()
	{ return isWifiP2pEnabled; }

    public void setWifiP2pEnabled(boolean enabled)
	{
        isWifiP2pEnabled = enabled;
    }

    // -------------------------------------------------------------------------
    // Host: create group
    // -------------------------------------------------------------------------

    public void createGroupAsHost(final ConnectionListener listener)
	{
        IkLog.d(TAG, "createGroupAsHost: starting");
        if (manager == null || channel == null)
		{
            if (listener != null) listener.onConnectionFailed("Manager not available");
			IkLog.d(TAG, "problem in createGroupAsHost: Manager not available");
            return;
        }

		try
		{
			connectionListener = listener;
			mGroupCreationInProgress = true;
			synchronized (mConnectionLock)
			{ mIsConnected = false; }
			startGroupCreationTimeout(listener);

			if (isDiscovering)
			{
				manager.stopPeerDiscovery(channel, new WifiP2pManager.ActionListener() {
						@Override public void onSuccess()
						{
							isDiscovering = false;
							handler.postDelayed(new Runnable() {
									@Override public void run()
									{ removeGroupAndThenCreate(MAX_RETRIES); }
								}, SETTLE_DELAY_MS);
						}
						@Override public void onFailure(int reason)
						{
							isDiscovering = false;
							removeGroupAndThenCreate(MAX_RETRIES);
						}
					});
			}
			else
			{
				removeGroupAndThenCreate(MAX_RETRIES);
			}
		}
		catch (Exception e)
		{
			IkLog.e(TAG, "error in createGroupAsHost", e);
		}

    }

    // -------------------------------------------------------------------------
    // Client: connect to group
    // -------------------------------------------------------------------------

    public void connectToGroup(WifiP2pDevice device, final ConnectionListener listener)
	{
        IkLog.d(TAG, "connectToGroup: connecting to " + device.deviceName);
        if (manager == null || channel == null)
		{
            if (listener != null) listener.onConnectionFailed("Manager not available");
            IkLog.d(TAG, "problem in connectToGroup: manager not available");
			return;
        }

        connectionListener = listener;
        mGroupCreationInProgress = true;
        synchronized (mConnectionLock)
		{ mIsConnected = false; }
        startGroupCreationTimeout(listener);

        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        try
		{ config.wps.setup = WpsInfo.PBC; }
		catch (Exception e)
		{
            IkLog.e(TAG, "Could not set WPS setup method", e);
        }
        config.groupOwnerIntent = 0;

        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
				@Override public void onSuccess()
				{
					IkLog.d(TAG, "connectToGroup: initiated – waiting for connection info");
				}
				@Override public void onFailure(int reason)
				{
					IkLog.e(TAG, "connectToGroup: failed: " + reason);
					mGroupCreationInProgress = false;
					cancelGroupCreationTimeout();
					String errMsg = getErrorString(reason);
					ConnectionListener temp = connectionListener;
					connectionListener = null;
					if (temp != null) temp.onConnectionFailed(errMsg);
				}
			});
    }

    // -------------------------------------------------------------------------
    // Disconnect
    // -------------------------------------------------------------------------

    public void disconnect()
	{
        try
		{
			IkLog.d(TAG, "disconnect: removing group");
			if (manager == null || channel == null) return;
			cancelGroupCreationTimeout();
			mGroupCreationInProgress = false;
			manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
					@Override public void onSuccess()
					{
						IkLog.d(TAG, "removeGroup succeeded");
						synchronized (mConnectionLock)
						{ mIsConnected = false; }
					}
					@Override public void onFailure(int reason)
					{
						IkLog.e(TAG, "removeGroup failed (" + reason + ")");
						synchronized (mConnectionLock)
						{ mIsConnected = false; }
					}
				});
		}
		catch (Exception e)
		{
			IkLog.e(TAG, "error in disconnect", e);
		}
    }

    // -------------------------------------------------------------------------
    // Peer discovery
    // -------------------------------------------------------------------------

    public void discoverPeers(final PeerListListener listener)
	{
        try
		{
			if (manager == null || channel == null)
			{
				IkLog.e(TAG, "discoverPeers: manager or channel is null");
				if (listener != null) listener.onDiscoveryStopped();
				return;
			}
			if (isDiscovering)
			{
				IkLog.d(TAG, "discoverPeers: already discovering");
				// Update listener so the caller gets results from the ongoing discovery
				peerListListener = listener;
				return;
			}

			peerListListener = listener;
			manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
					@Override public void onSuccess()
					{
						isDiscovering = true;
						IkLog.d(TAG, "Discovery started");
					}
					@Override public void onFailure(int reason)
					{
						isDiscovering = false;
						IkLog.e(TAG, "Discovery failed: " + getErrorString(reason));
						if (peerListListener != null) peerListListener.onDiscoveryStopped();
					}
				});
		}
		catch (Exception e)
		{
			IkLog.e(TAG, "error in discoverPeers", e);
		}
    }

    public void stopPeerDiscovery()
	{
		try
		{
			if (manager == null || channel == null || !isDiscovering) return;
			manager.stopPeerDiscovery(channel, new WifiP2pManager.ActionListener() {
					@Override public void onSuccess()
					{
						isDiscovering = false;
						IkLog.d(TAG, "Peer discovery stopped");
						if (peerListListener != null) peerListListener.onDiscoveryStopped();
					}
					@Override public void onFailure(int reason)
					{
						IkLog.d(TAG, "failure stopping peer discovery");
						isDiscovering = false;
						if (peerListListener != null) peerListListener.onDiscoveryStopped();
					}
				});
		}
		catch (Exception e)
		{
			IkLog.e(TAG, "error in stopPeerDiscovery", e);
		}
    }

    // -------------------------------------------------------------------------
    // Callbacks from BroadcastReceiver
    // -------------------------------------------------------------------------

    /**
     * Called every time WIFI_P2P_PEERS_CHANGED_ACTION fires — potentially
     * multiple times during a single discovery session.
     *
     */
    public void onPeersAvailable(WifiP2pDeviceList peerDeviceList)
	{
        try{
			if (peerListListener == null || peerDeviceList == null) return;

			List<WifiP2pDevice> peers =
				new ArrayList<WifiP2pDevice>(peerDeviceList.getDeviceList());
			IkLog.d(TAG, "onPeersAvailable: " + peers.size() + " peers");

			if (!peers.isEmpty())
			{
				// Deliver once, then clear the listener to prevent repeated dialogs
				PeerListListener temp = peerListListener;
				peerListListener = null;
				isDiscovering    = false;
				temp.onPeersAvailable(peers);
			}
			else
			{
				// Keep listener active so retry logic in the fragment still fires
				peerListListener.onPeersAvailable(peers);
			}
		}catch(Exception e){
			IkLog.e(TAG, "error in onPeersAvailable", e);
		}
    }

    public void onConnectionInfoAvailable(WifiP2pInfo info)
	{
        try{
			IkLog.d(TAG, "onConnectionInfoAvailable: groupFormed=" + info.groupFormed
					+ ", isGroupOwner=" + info.isGroupOwner);

			if (!mGroupCreationInProgress)
			{
				IkLog.w(TAG, "Connection info received but group creation not in progress – ignoring");
				return;
			}

			mGroupCreationInProgress = false;
			cancelGroupCreationTimeout();

			synchronized (mConnectionLock)
			{
				if (mIsConnected)
				{
					IkLog.w(TAG, "Already connected – duplicate callback ignored");
					return;
				}
				mIsConnected = true;
			}

			if (info.groupFormed && info.groupOwnerAddress != null)
			{
				ConnectionListener temp = connectionListener;
				connectionListener = null;
				if (temp != null)
				{
					temp.onConnectionInfoAvailable(info.groupOwnerAddress, info.isGroupOwner);
				}
			}
			else if (!info.groupFormed)
			{
				IkLog.w(TAG, "Group not formed");
				ConnectionListener temp = connectionListener;
				connectionListener = null;
				if (temp != null) temp.onConnectionFailed("Group formation failed");
			}
		}catch(Exception e){
			IkLog.e(TAG, "error in onConnectionInfoAvailable", e);
		}
    }

    public void onDisconnected()
	{
        IkLog.d(TAG, "onDisconnected: group dissolved");
        cancelGroupCreationTimeout();
        mGroupCreationInProgress = false;
        synchronized (mConnectionLock)
		{ mIsConnected = false; }

        ConnectionListener temp = connectionListener;
        connectionListener = null;
        if (temp != null) temp.onDisconnected();
    }

    public void onWifiP2pStateChanged(int state)
	{
        isWifiP2pEnabled = (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED);
        IkLog.d(TAG, "WiFi P2P state: " + (isWifiP2pEnabled ? "ENABLED" : "DISABLED"));
    }

    public void onThisDeviceChanged(WifiP2pDevice device)
	{
        thisDevice = device;
        if (deviceInfoListener != null) deviceInfoListener.onDeviceInfoChanged(device);
    }

    // -------------------------------------------------------------------------
    // Settings intent helper
    // -------------------------------------------------------------------------

    public static String getSettingsIntentAction(int errorCode)
	{
        switch (errorCode)
		{
            case ERROR_WIFI_DISABLED:    return android.provider.Settings.ACTION_WIFI_SETTINGS;
            case ERROR_LOCATION_DISABLED: return android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS;
            default:                     return null;
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void startGroupCreationTimeout(final ConnectionListener listener)
	{
        cancelGroupCreationTimeout();
        mGroupCreationTimeoutRunnable = new Runnable() {
            @Override
            public void run()
			{
                if (mGroupCreationInProgress)
				{
                    IkLog.e(TAG, "Group creation timed out after " + GROUP_CREATION_TIMEOUT_MS + "ms");
                    mGroupCreationInProgress = false;
                    ConnectionListener temp = connectionListener;
                    connectionListener = null;
                    if (temp != null) temp.onConnectionFailed("Group creation timed out");
                }
            }
        };
        handler.postDelayed(mGroupCreationTimeoutRunnable, GROUP_CREATION_TIMEOUT_MS);
    }

    private void cancelGroupCreationTimeout()
	{
        if (mGroupCreationTimeoutRunnable != null)
		{
            handler.removeCallbacks(mGroupCreationTimeoutRunnable);
            mGroupCreationTimeoutRunnable = null;
        }
    }

    private void removeGroupAndThenCreate(final int retriesLeft)
	{
        try{
			if (manager == null || channel == null)
			{
				mGroupCreationInProgress = false;
				cancelGroupCreationTimeout();
				ConnectionListener temp = connectionListener;
				connectionListener = null;
				if (temp != null) temp.onConnectionFailed("Manager not available");
				return;
			}
			manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
					@Override public void onSuccess()
					{
						IkLog.d(TAG, "Existing group removed");
						handler.postDelayed(new Runnable() {
								@Override public void run()
								{ attemptCreateGroup(retriesLeft); }
							}, SETTLE_DELAY_MS);
					}
					@Override public void onFailure(int reason)
					{
						IkLog.d(TAG, "removeGroup failed (likely no group): " + reason);
						attemptCreateGroup(retriesLeft);
					}
				});
		}catch(Exception e){
			IkLog.e(TAG, "error in removeGroupAndThenCreate", e);
		}
    }

    private void attemptCreateGroup(final int retriesLeft)
	{
        try{
			if (manager == null || channel == null)
			{
				mGroupCreationInProgress = false;
				cancelGroupCreationTimeout();
				ConnectionListener temp = connectionListener;
				connectionListener = null;
				if (temp != null) temp.onConnectionFailed("Manager not available");
				return;
			}
			manager.createGroup(channel, new WifiP2pManager.ActionListener() {
					@Override public void onSuccess()
					{
						IkLog.d(TAG, "Group creation initiated – waiting for connection info broadcast");
					}
					@Override public void onFailure(int reason)
					{
						IkLog.e(TAG, "createGroup failed: " + reason);
						if (reason == WifiP2pManager.BUSY && retriesLeft > 0)
						{
							handler.postDelayed(new Runnable() {
									@Override public void run()
									{
										removeGroupAndThenCreate(retriesLeft - 1);
									}
								}, RETRY_DELAY_MS);
						}
						else
						{
							mGroupCreationInProgress = false;
							cancelGroupCreationTimeout();
							String errMsg = getErrorString(reason);
							ConnectionListener temp = connectionListener;
							connectionListener = null;
							if (temp != null) temp.onConnectionFailed(errMsg);
						}
					}
				});
		}catch(Exception e){
			IkLog.e(TAG, "erroe in attemptCreateGroup", e);
		}
    }

    private String getErrorString(int reason)
	{
        switch (reason)
		{
            case 0: return "Internal error";
            case 1: return "Wi-Fi Direct not supported";
            case 2: return "Wi-Fi Direct is busy";
            case 3: return "No service requests registered";
            default: return "Unknown error (code " + reason + ")";
        }
    }
}