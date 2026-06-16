package com.ikechi.studio.onwa.player.net;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

public class WiFiDirectBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "WiFiDirectReceiver";
    private final WifiP2pManager manager;
    private final WifiP2pManager.Channel channel;
    private final WiFiDirectManager wiFiDirectManager;

    public WiFiDirectBroadcastReceiver(WifiP2pManager manager,
                                       WifiP2pManager.Channel channel,
                                       WiFiDirectManager wiFiDirectManager) {
        this.manager = manager;
        this.channel = channel;
        this.wiFiDirectManager = wiFiDirectManager;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) {
            Log.w(TAG, "Received intent with null action");
            return;
        }
        if (manager == null || channel == null) {
            Log.e(TAG, "Manager or channel is null");
            return;
        }

        Log.d(TAG, "onReceive: " + action);

        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            handleStateChanged(intent);
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
            handlePeersChanged();
        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            handleConnectionChanged(intent);
        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
            handleThisDeviceChanged(intent);
        } else {
            Log.d(TAG, "Unhandled action: " + action);
        }
    }

    private void handleStateChanged(Intent intent) {
        int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
        Log.d(TAG, "P2P state changed: " + state);
        if (wiFiDirectManager != null) {
            try {
                wiFiDirectManager.onWifiP2pStateChanged(state);
            } catch (Exception e) {
                Log.e(TAG, "Error in onWifiP2pStateChanged", e);
            }
        }
    }

    private void handlePeersChanged() {
        if (wiFiDirectManager == null || manager == null || channel == null) {
            Log.w(TAG, "Manager or wiFiDirectManager is null in handlePeersChanged");
            return;
        }

        try {
            manager.requestPeers(channel, new WifiP2pManager.PeerListListener() {
					@Override
					public void onPeersAvailable(WifiP2pDeviceList peers) {
						try {
							if (wiFiDirectManager == null) {
								Log.w(TAG, "WiFiDirectManager is null in peer callback");
								return;
							}
							if (peers != null) {
								wiFiDirectManager.onPeersAvailable(peers);
							} else {
								Log.w(TAG, "Peers list is null");
							}
						} catch (Exception e) {
							Log.e(TAG, "Error in onPeersAvailable callback", e);
						}
					}
				});
        } catch (Exception e) {
            Log.e(TAG, "Error requesting peers", e);
        }
    }

    private void handleConnectionChanged(Intent intent) {
        NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
        if (networkInfo == null) {
            Log.w(TAG, "No network info in connection changed intent");
            return;
        }

        Log.d(TAG, "Connection changed: connected=" + networkInfo.isConnected() 
              + ", state=" + networkInfo.getState());

        if (networkInfo.isConnected()) {
            // Request detailed connection info
            if (manager != null && channel != null) {
                try {
                    manager.requestConnectionInfo(channel, new WifiP2pManager.ConnectionInfoListener() {
							@Override
							public void onConnectionInfoAvailable(WifiP2pInfo info) {
								try {
									if (info == null) {
										Log.w(TAG, "Connection info is null");
										return;
									}
									Log.d(TAG, "Connection info available: groupFormed=" + info.groupFormed 
										  + ", isGroupOwner=" + info.isGroupOwner
										  + ", groupOwnerAddress=" + (info.groupOwnerAddress != null ? info.groupOwnerAddress.getHostAddress() : "null"));
									if (wiFiDirectManager != null) {
										wiFiDirectManager.onConnectionInfoAvailable(info);
									} else {
										Log.e(TAG, "WiFiDirectManager is null in connection info callback");
									}
								} catch (Exception e) {
									Log.e(TAG, "Error in onConnectionInfoAvailable callback", e);
								}
							}
						});
                } catch (Exception e) {
                    Log.e(TAG, "Error requesting connection info", e);
                }
            }

            // Also request group info for additional details
            if (manager != null && channel != null) {
                try {
                    manager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
							@Override
							public void onGroupInfoAvailable(android.net.wifi.p2p.WifiP2pGroup group) {
								try {
									if (group != null) {
										Log.d(TAG, "Group info: " + group.getNetworkName() 
											  + ", owner: " + (group.getOwner() != null ? group.getOwner().deviceName : "null"));
									} else {
										Log.w(TAG, "Group info is null");
									}
								} catch (Exception e) {
									Log.e(TAG, "Error in onGroupInfoAvailable callback", e);
								}
							}
						});
                } catch (Exception e) {
                    Log.e(TAG, "Error requesting group info", e);
                }
            }
        } else {
            Log.d(TAG, "Network disconnected - notifying manager");
            if (wiFiDirectManager != null) {
                try {
                    wiFiDirectManager.onDisconnected();
                } catch (Exception e) {
                    Log.e(TAG, "Error in onDisconnected callback", e);
                }
            }
        }
    }

    private void handleThisDeviceChanged(Intent intent) {
        try {
            WifiP2pDevice device = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
            if (device != null && wiFiDirectManager != null) {
                wiFiDirectManager.onThisDeviceChanged(device);
            } else if (device == null) {
                Log.w(TAG, "Device is null in this device changed");
            } else if (wiFiDirectManager == null) {
                Log.e(TAG, "WiFiDirectManager is null in this device changed");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling this device changed", e);
        }
    }
}

