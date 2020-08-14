/*
 *  Copyright 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import androidx.annotation.Nullable;
import java.util.List;

/** Interface for detecting network changes */
public interface NetworkChangeDetector {
  // java equivalent of c++ android_network_monitor.h / NetworkType.
  public static enum ConnectionType {
    CONNECTION_UNKNOWN,
    CONNECTION_ETHERNET,
    CONNECTION_WIFI,
    CONNECTION_5G,
    CONNECTION_4G,
    CONNECTION_3G,
    CONNECTION_2G,
    CONNECTION_UNKNOWN_CELLULAR,
    CONNECTION_BLUETOOTH,
    CONNECTION_VPN,
    CONNECTION_NONE
  }

  public static class IPAddress {
    public final byte[] address;

    public IPAddress(byte[] address) {
      this.address = address;
    }

    @CalledByNative("IPAddress")
    private byte[] getAddress() {
      return address;
    }
  }

  /** Java version of NetworkMonitor.NetworkInformation */
  public static class NetworkInformation {
    public final String name;
    public final ConnectionType type;
    // Used to specify the underlying network type if the type is CONNECTION_VPN.
    public final ConnectionType underlyingTypeForVpn;
    public final long handle;
    public final IPAddress[] ipAddresses;

    public NetworkInformation(String name, ConnectionType type, ConnectionType underlyingTypeForVpn,
        long handle, IPAddress[] addresses) {
      this.name = name;
      this.type = type;
      this.underlyingTypeForVpn = underlyingTypeForVpn;
      this.handle = handle;
      this.ipAddresses = addresses;
    }

    @CalledByNative("NetworkInformation")
    private IPAddress[] getIpAddresses() {
      return ipAddresses;
    }

    @CalledByNative("NetworkInformation")
    private ConnectionType getConnectionType() {
      return type;
    }

    @CalledByNative("NetworkInformation")
    private ConnectionType getUnderlyingConnectionTypeForVpn() {
      return underlyingTypeForVpn;
    }

    @CalledByNative("NetworkInformation")
    private long getHandle() {
      return handle;
    }

    @CalledByNative("NetworkInformation")
    private String getName() {
      return name;
    }
  };

  /** Observer interface by which observer is notified of network changes. */
  public static interface Observer {
    /** Called when default network changes. */
    public void onConnectionTypeChanged(ConnectionType newConnectionType);

    public void onNetworkConnect(NetworkInformation networkInfo);

    public void onNetworkDisconnect(long networkHandle);

    /**
     * Called when network preference change for a (list of) connection type(s). (e.g WIFI) is
     * |NOT_PREFERRED| or |NEUTRAL|.
     *
     * <p>note: |types| is a list of ConnectionTypes, so that all cellular types can be modified in
     * one call.
     */
    public void onNetworkPreference(List<ConnectionType> types, int preference);
  }

  public ConnectionType getCurrentConnectionType();

  public boolean supportNetworkCallback();

  @Nullable public List<NetworkInformation> getActiveNetworkList();

  public void destroy();
}
