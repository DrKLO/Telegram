/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.util;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyCallback.DisplayInfoListener;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import java.lang.ref.WeakReference;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Observer for network type changes.
 *
 * <p>{@link #register Registered} listeners are informed at registration and whenever the network
 * type changes.
 *
 * <p>The current network type can also be {@link #getNetworkType queried} without registration.
 */
public final class NetworkTypeObserver {

  /** A listener for network type changes. */
  public interface Listener {

    /**
     * Called when the network type changed or when the listener is first registered.
     *
     * <p>This method is always called on the main thread.
     */
    void onNetworkTypeChanged(@C.NetworkType int networkType);
  }

  @Nullable private static NetworkTypeObserver staticInstance;

  private final Handler mainHandler;
  // This class needs to hold weak references as it doesn't require listeners to unregister.
  private final CopyOnWriteArrayList<WeakReference<Listener>> listeners;
  private final Object networkTypeLock;

  @GuardedBy("networkTypeLock")
  private @C.NetworkType int networkType;

  /**
   * Returns a network type observer instance.
   *
   * @param context A {@link Context}.
   */
  public static synchronized NetworkTypeObserver getInstance(Context context) {
    if (staticInstance == null) {
      staticInstance = new NetworkTypeObserver(context);
    }
    return staticInstance;
  }

  /** Resets the network type observer for tests. */
  @VisibleForTesting
  public static synchronized void resetForTests() {
    staticInstance = null;
  }

  private NetworkTypeObserver(Context context) {
    mainHandler = new Handler(Looper.getMainLooper());
    listeners = new CopyOnWriteArrayList<>();
    networkTypeLock = new Object();
    networkType = C.NETWORK_TYPE_UNKNOWN;
    IntentFilter filter = new IntentFilter();
    filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
    Util.registerReceiverNotExported(context, new Receiver(), filter);
  }

  /**
   * Registers a listener.
   *
   * <p>The current network type will be reported to the listener after registration.
   *
   * @param listener The {@link Listener}.
   */
  public void register(Listener listener) {
    removeClearedReferences();
    listeners.add(new WeakReference<>(listener));
    // Simulate an initial update on the main thread (like the sticky broadcast we'd receive if
    // we were to register a separate broadcast receiver for each listener).
    mainHandler.post(() -> listener.onNetworkTypeChanged(getNetworkType()));
  }

  /** Returns the current network type. */
  public @C.NetworkType int getNetworkType() {
    synchronized (networkTypeLock) {
      return networkType;
    }
  }

  private void removeClearedReferences() {
    for (WeakReference<Listener> listenerReference : listeners) {
      if (listenerReference.get() == null) {
        listeners.remove(listenerReference);
      }
    }
  }

  private void updateNetworkType(@C.NetworkType int networkType) {
    synchronized (networkTypeLock) {
      if (this.networkType == networkType) {
        return;
      }
      this.networkType = networkType;
    }
    for (WeakReference<Listener> listenerReference : listeners) {
      @Nullable Listener listener = listenerReference.get();
      if (listener != null) {
        listener.onNetworkTypeChanged(networkType);
      } else {
        listeners.remove(listenerReference);
      }
    }
  }

  private static @C.NetworkType int getNetworkTypeFromConnectivityManager(Context context) {
    NetworkInfo networkInfo;
    @Nullable
    ConnectivityManager connectivityManager =
        (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    if (connectivityManager == null) {
      return C.NETWORK_TYPE_UNKNOWN;
    }
    try {
      networkInfo = connectivityManager.getActiveNetworkInfo();
    } catch (SecurityException e) {
      // Expected if permission was revoked.
      return C.NETWORK_TYPE_UNKNOWN;
    }
    if (networkInfo == null || !networkInfo.isConnected()) {
      return C.NETWORK_TYPE_OFFLINE;
    }
    switch (networkInfo.getType()) {
      case ConnectivityManager.TYPE_WIFI:
        return C.NETWORK_TYPE_WIFI;
      case ConnectivityManager.TYPE_WIMAX:
        return C.NETWORK_TYPE_4G;
      case ConnectivityManager.TYPE_MOBILE:
      case ConnectivityManager.TYPE_MOBILE_DUN:
      case ConnectivityManager.TYPE_MOBILE_HIPRI:
        return getMobileNetworkType(networkInfo);
      case ConnectivityManager.TYPE_ETHERNET:
        return C.NETWORK_TYPE_ETHERNET;
      default:
        return C.NETWORK_TYPE_OTHER;
    }
  }

  private static @C.NetworkType int getMobileNetworkType(NetworkInfo networkInfo) {
    switch (networkInfo.getSubtype()) {
      case TelephonyManager.NETWORK_TYPE_EDGE:
      case TelephonyManager.NETWORK_TYPE_GPRS:
        return C.NETWORK_TYPE_2G;
      case TelephonyManager.NETWORK_TYPE_1xRTT:
      case TelephonyManager.NETWORK_TYPE_CDMA:
      case TelephonyManager.NETWORK_TYPE_EVDO_0:
      case TelephonyManager.NETWORK_TYPE_EVDO_A:
      case TelephonyManager.NETWORK_TYPE_EVDO_B:
      case TelephonyManager.NETWORK_TYPE_HSDPA:
      case TelephonyManager.NETWORK_TYPE_HSPA:
      case TelephonyManager.NETWORK_TYPE_HSUPA:
      case TelephonyManager.NETWORK_TYPE_IDEN:
      case TelephonyManager.NETWORK_TYPE_UMTS:
      case TelephonyManager.NETWORK_TYPE_EHRPD:
      case TelephonyManager.NETWORK_TYPE_HSPAP:
      case TelephonyManager.NETWORK_TYPE_TD_SCDMA:
        return C.NETWORK_TYPE_3G;
      case TelephonyManager.NETWORK_TYPE_LTE:
        return C.NETWORK_TYPE_4G;
      case TelephonyManager.NETWORK_TYPE_NR:
        return Util.SDK_INT >= 29 ? C.NETWORK_TYPE_5G_SA : C.NETWORK_TYPE_UNKNOWN;
      case TelephonyManager.NETWORK_TYPE_IWLAN:
        return C.NETWORK_TYPE_WIFI;
      case TelephonyManager.NETWORK_TYPE_GSM:
      case TelephonyManager.NETWORK_TYPE_UNKNOWN:
      default: // Future mobile network types.
        return C.NETWORK_TYPE_CELLULAR_UNKNOWN;
    }
  }

  private final class Receiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
      @C.NetworkType int networkType = getNetworkTypeFromConnectivityManager(context);
      if (Util.SDK_INT >= 31 && networkType == C.NETWORK_TYPE_4G) {
        // Delay update of the network type to check whether this is actually 5G-NSA.
        Api31.disambiguate4gAnd5gNsa(context, /* instance= */ NetworkTypeObserver.this);
      } else {
        updateNetworkType(networkType);
      }
    }
  }

  @RequiresApi(31)
  private static final class Api31 {

    public static void disambiguate4gAnd5gNsa(Context context, NetworkTypeObserver instance) {
      try {
        TelephonyManager telephonyManager =
            checkNotNull((TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE));
        DisplayInfoCallback callback = new DisplayInfoCallback(instance);
        telephonyManager.registerTelephonyCallback(context.getMainExecutor(), callback);
        // We are only interested in the initial response with the current state, so unregister
        // the listener immediately.
        telephonyManager.unregisterTelephonyCallback(callback);
      } catch (RuntimeException e) {
        // Ignore problems with listener registration and keep reporting as 4G.
        instance.updateNetworkType(C.NETWORK_TYPE_4G);
      }
    }

    private static final class DisplayInfoCallback extends TelephonyCallback
        implements DisplayInfoListener {

      private final NetworkTypeObserver instance;

      public DisplayInfoCallback(NetworkTypeObserver instance) {
        this.instance = instance;
      }

      @Override
      public void onDisplayInfoChanged(TelephonyDisplayInfo telephonyDisplayInfo) {
        int overrideNetworkType = telephonyDisplayInfo.getOverrideNetworkType();
        boolean is5gNsa =
            overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA
                || overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE
                || overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED;
        instance.updateNetworkType(is5gNsa ? C.NETWORK_TYPE_5G_NSA : C.NETWORK_TYPE_4G);
      }
    }
  }
}
