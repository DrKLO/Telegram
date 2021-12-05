/*
 *  Copyright 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import android.content.Context;
import android.os.Build;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import org.webrtc.NetworkChangeDetector;

/**
 * Borrowed from Chromium's
 * src/net/android/java/src/org/chromium/net/NetworkChangeNotifier.java
 *
 * <p>Triggers updates to the underlying network state from OS networking events.
 *
 * <p>This class is thread-safe.
 */
public class NetworkMonitor {
  /**
   * Alerted when the connection type of the network changes. The alert is fired on the UI thread.
   */
  public interface NetworkObserver {
    public void onConnectionTypeChanged(NetworkChangeDetector.ConnectionType connectionType);
  }

  private static final String TAG = "NetworkMonitor";

  // Lazy initialization holder class idiom for static fields.
  private static class InstanceHolder {
    // We are storing application context so it is okay.
    static final NetworkMonitor instance = new NetworkMonitor();
  }

  // Factory for creating NetworkChangeDetector.
  private NetworkChangeDetectorFactory networkChangeDetectorFactory =
      new NetworkChangeDetectorFactory() {
        @Override
        public NetworkChangeDetector create(
            NetworkChangeDetector.Observer observer, Context context) {
          return new NetworkMonitorAutoDetect(observer, context);
        }
      };

  // Native observers of the connection type changes.
  private final ArrayList<Long> nativeNetworkObservers;
  // Java observers of the connection type changes.
  private final ArrayList<NetworkObserver> networkObservers;

  private final Object networkChangeDetectorLock = new Object();
  // Object that detects the connection type changes and brings up mobile networks.
  @Nullable private NetworkChangeDetector networkChangeDetector;
  // Also guarded by autoDetectLock.
  private int numObservers;

  private volatile NetworkChangeDetector.ConnectionType currentConnectionType;

  private NetworkMonitor() {
    nativeNetworkObservers = new ArrayList<Long>();
    networkObservers = new ArrayList<NetworkObserver>();
    numObservers = 0;
    currentConnectionType = NetworkChangeDetector.ConnectionType.CONNECTION_UNKNOWN;
  }

  /**
   * Set the factory that will be used to create the network change detector.
   * Needs to be called before the monitoring is starts.
   */
  public void setNetworkChangeDetectorFactory(NetworkChangeDetectorFactory factory) {
    assertIsTrue(numObservers == 0);
    this.networkChangeDetectorFactory = factory;
  }

  // TODO(sakal): Remove once downstream dependencies have been updated.
  @Deprecated
  public static void init(Context context) {}

  /** Returns the singleton instance. This may be called from native or from Java code. */
  @CalledByNative
  public static NetworkMonitor getInstance() {
    return InstanceHolder.instance;
  }

  private static void assertIsTrue(boolean condition) {
    if (!condition) {
      throw new AssertionError("Expected to be true");
    }
  }

  /**
   * Enables auto detection of the network state change and brings up mobile networks for using
   * multi-networking. This requires the embedding app have the platform ACCESS_NETWORK_STATE and
   * CHANGE_NETWORK_STATE permission.
   */
  public void startMonitoring(Context applicationContext) {
    synchronized (networkChangeDetectorLock) {
      ++numObservers;
      if (networkChangeDetector == null) {
        networkChangeDetector = createNetworkChangeDetector(applicationContext);
      }
      currentConnectionType = networkChangeDetector.getCurrentConnectionType();
    }
  }

  /** Deprecated, pass in application context in startMonitoring instead. */
  @Deprecated
  public void startMonitoring() {
    startMonitoring(ContextUtils.getApplicationContext());
  }

  /**
   * Enables auto detection of the network state change and brings up mobile networks for using
   * multi-networking. This requires the embedding app have the platform ACCESS_NETWORK_STATE and
   * CHANGE_NETWORK_STATE permission.
   */
  @CalledByNative
  private void startMonitoring(@Nullable Context applicationContext, long nativeObserver) {
    Logging.d(TAG, "Start monitoring with native observer " + nativeObserver);

    startMonitoring(
        applicationContext != null ? applicationContext : ContextUtils.getApplicationContext());
    // The native observers expect a network list update after they call startMonitoring.
    synchronized (nativeNetworkObservers) {
      nativeNetworkObservers.add(nativeObserver);
    }
    updateObserverActiveNetworkList(nativeObserver);
    // currentConnectionType was updated in startMonitoring().
    // Need to notify the native observers here.
    notifyObserversOfConnectionTypeChange(currentConnectionType);
  }

  /**
   * Stop network monitoring. If no one is monitoring networks, destroy and reset
   * networkChangeDetector.
   */
  public void stopMonitoring() {
    synchronized (networkChangeDetectorLock) {
      if (--numObservers == 0) {
        networkChangeDetector.destroy();
        networkChangeDetector = null;
      }
    }
  }

  @CalledByNative
  private void stopMonitoring(long nativeObserver) {
    Logging.d(TAG, "Stop monitoring with native observer " + nativeObserver);
    stopMonitoring();
    synchronized (nativeNetworkObservers) {
      nativeNetworkObservers.remove(nativeObserver);
    }
  }

  // Returns true if network binding is supported on this platform.
  @CalledByNative
  private boolean networkBindingSupported() {
    synchronized (networkChangeDetectorLock) {
      return networkChangeDetector != null && networkChangeDetector.supportNetworkCallback();
    }
  }

  @CalledByNative
  private static int androidSdkInt() {
    return Build.VERSION.SDK_INT;
  }

  private NetworkChangeDetector.ConnectionType getCurrentConnectionType() {
    return currentConnectionType;
  }

  private NetworkChangeDetector createNetworkChangeDetector(Context appContext) {
    return networkChangeDetectorFactory.create(new NetworkChangeDetector.Observer() {
      @Override
      public void onConnectionTypeChanged(NetworkChangeDetector.ConnectionType newConnectionType) {
        updateCurrentConnectionType(newConnectionType);
      }

      @Override
      public void onNetworkConnect(NetworkChangeDetector.NetworkInformation networkInfo) {
        notifyObserversOfNetworkConnect(networkInfo);
      }

      @Override
      public void onNetworkDisconnect(long networkHandle) {
        notifyObserversOfNetworkDisconnect(networkHandle);
      }

      @Override
      public void onNetworkPreference(
          List<NetworkChangeDetector.ConnectionType> types, int preference) {
        notifyObserversOfNetworkPreference(types, preference);
      }
    }, appContext);
  }

  private void updateCurrentConnectionType(NetworkChangeDetector.ConnectionType newConnectionType) {
    currentConnectionType = newConnectionType;
    notifyObserversOfConnectionTypeChange(newConnectionType);
  }

  /** Alerts all observers of a connection change. */
  private void notifyObserversOfConnectionTypeChange(
      NetworkChangeDetector.ConnectionType newConnectionType) {
    List<Long> nativeObservers = getNativeNetworkObserversSync();
    for (Long nativeObserver : nativeObservers) {
      nativeNotifyConnectionTypeChanged(nativeObserver);
    }
    // This avoids calling external methods while locking on an object.
    List<NetworkObserver> javaObservers;
    synchronized (networkObservers) {
      javaObservers = new ArrayList<>(networkObservers);
    }
    for (NetworkObserver observer : javaObservers) {
      observer.onConnectionTypeChanged(newConnectionType);
    }
  }

  private void notifyObserversOfNetworkConnect(
      NetworkChangeDetector.NetworkInformation networkInfo) {
    List<Long> nativeObservers = getNativeNetworkObserversSync();
    for (Long nativeObserver : nativeObservers) {
      nativeNotifyOfNetworkConnect(nativeObserver, networkInfo);
    }
  }

  private void notifyObserversOfNetworkDisconnect(long networkHandle) {
    List<Long> nativeObservers = getNativeNetworkObserversSync();
    for (Long nativeObserver : nativeObservers) {
      nativeNotifyOfNetworkDisconnect(nativeObserver, networkHandle);
    }
  }

  private void notifyObserversOfNetworkPreference(
      List<NetworkChangeDetector.ConnectionType> types, int preference) {
    List<Long> nativeObservers = getNativeNetworkObserversSync();
    for (NetworkChangeDetector.ConnectionType type : types) {
      for (Long nativeObserver : nativeObservers) {
        nativeNotifyOfNetworkPreference(nativeObserver, type, preference);
      }
    }
  }

  private void updateObserverActiveNetworkList(long nativeObserver) {
    List<NetworkChangeDetector.NetworkInformation> networkInfoList;
    synchronized (networkChangeDetectorLock) {
      networkInfoList =
          (networkChangeDetector == null) ? null : networkChangeDetector.getActiveNetworkList();
    }
    if (networkInfoList == null || networkInfoList.size() == 0) {
      return;
    }

    NetworkChangeDetector.NetworkInformation[] networkInfos =
        new NetworkChangeDetector.NetworkInformation[networkInfoList.size()];
    networkInfos = networkInfoList.toArray(networkInfos);
    nativeNotifyOfActiveNetworkList(nativeObserver, networkInfos);
  }

  private List<Long> getNativeNetworkObserversSync() {
    synchronized (nativeNetworkObservers) {
      return new ArrayList<>(nativeNetworkObservers);
    }
  }

  /**
   * Adds an observer for any connection type changes.
   *
   * @deprecated Use getInstance(appContext).addObserver instead.
   */
  @Deprecated
  public static void addNetworkObserver(NetworkObserver observer) {
    getInstance().addObserver(observer);
  }

  public void addObserver(NetworkObserver observer) {
    synchronized (networkObservers) {
      networkObservers.add(observer);
    }
  }

  /**
   * Removes an observer for any connection type changes.
   *
   * @deprecated Use getInstance(appContext).removeObserver instead.
   */
  @Deprecated
  public static void removeNetworkObserver(NetworkObserver observer) {
    getInstance().removeObserver(observer);
  }

  public void removeObserver(NetworkObserver observer) {
    synchronized (networkObservers) {
      networkObservers.remove(observer);
    }
  }

  /** Checks if there currently is connectivity. */
  public static boolean isOnline() {
    NetworkChangeDetector.ConnectionType connectionType = getInstance().getCurrentConnectionType();
    return connectionType != NetworkChangeDetector.ConnectionType.CONNECTION_NONE;
  }

  private native void nativeNotifyConnectionTypeChanged(long nativeAndroidNetworkMonitor);

  private native void nativeNotifyOfNetworkConnect(
      long nativeAndroidNetworkMonitor, NetworkChangeDetector.NetworkInformation networkInfo);

  private native void nativeNotifyOfNetworkDisconnect(
      long nativeAndroidNetworkMonitor, long networkHandle);

  private native void nativeNotifyOfActiveNetworkList(
      long nativeAndroidNetworkMonitor, NetworkChangeDetector.NetworkInformation[] networkInfos);

  private native void nativeNotifyOfNetworkPreference(
      long nativeAndroidNetworkMonitor, NetworkChangeDetector.ConnectionType type, int preference);

  // For testing only.
  @Nullable
  NetworkChangeDetector getNetworkChangeDetector() {
    synchronized (networkChangeDetectorLock) {
      return networkChangeDetector;
    }
  }

  // For testing only.
  int getNumObservers() {
    synchronized (networkChangeDetectorLock) {
      return numObservers;
    }
  }

  // For testing only.
  static NetworkMonitorAutoDetect createAndSetAutoDetectForTest(Context context) {
    NetworkMonitor networkMonitor = getInstance();
    NetworkChangeDetector networkChangeDetector =
        networkMonitor.createNetworkChangeDetector(context);
    networkMonitor.networkChangeDetector = networkChangeDetector;
    return (NetworkMonitorAutoDetect) networkChangeDetector;
  }
}
