/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.scheduler;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.util.Util;

/**
 * Watches whether the {@link Requirements} are met and notifies the {@link Listener} on changes.
 */
public final class RequirementsWatcher {

  /**
   * Notified when RequirementsWatcher instance first created and on changes whether the {@link
   * Requirements} are met.
   */
  public interface Listener {
    /**
     * Called when there is a change on the met requirements.
     *
     * @param requirementsWatcher Calling instance.
     * @param notMetRequirements {@link Requirements.RequirementFlags RequirementFlags} that are not
     *     met, or 0.
     */
    void onRequirementsStateChanged(
        RequirementsWatcher requirementsWatcher,
        @Requirements.RequirementFlags int notMetRequirements);
  }

  private final Context context;
  private final Listener listener;
  private final Requirements requirements;
  private final Handler handler;

  @Nullable private DeviceStatusChangeReceiver receiver;

  private @Requirements.RequirementFlags int notMetRequirements;
  @Nullable private NetworkCallback networkCallback;

  /**
   * @param context Any context.
   * @param listener Notified whether the {@link Requirements} are met.
   * @param requirements The requirements to watch.
   */
  public RequirementsWatcher(Context context, Listener listener, Requirements requirements) {
    this.context = context.getApplicationContext();
    this.listener = listener;
    this.requirements = requirements;
    handler = Util.createHandlerForCurrentOrMainLooper();
  }

  /**
   * Starts watching for changes. Must be called from a thread that has an associated {@link
   * Looper}. Listener methods are called on the caller thread.
   *
   * @return Initial {@link Requirements.RequirementFlags RequirementFlags} that are not met, or 0.
   */
  public @Requirements.RequirementFlags int start() {
    notMetRequirements = requirements.getNotMetRequirements(context);

    IntentFilter filter = new IntentFilter();
    if (requirements.isNetworkRequired()) {
      if (Util.SDK_INT >= 24) {
        registerNetworkCallbackV24();
      } else {
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
      }
    }
    if (requirements.isChargingRequired()) {
      filter.addAction(Intent.ACTION_POWER_CONNECTED);
      filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
    }
    if (requirements.isIdleRequired()) {
      if (Util.SDK_INT >= 23) {
        filter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
      } else {
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
      }
    }
    if (requirements.isStorageNotLowRequired()) {
      filter.addAction(Intent.ACTION_DEVICE_STORAGE_LOW);
      filter.addAction(Intent.ACTION_DEVICE_STORAGE_OK);
    }
    receiver = new DeviceStatusChangeReceiver();
    Util.registerReceiverNotExported(context, receiver, filter, handler);
    return notMetRequirements;
  }

  /** Stops watching for changes. */
  public void stop() {
    context.unregisterReceiver(checkNotNull(receiver));
    receiver = null;
    if (Util.SDK_INT >= 24 && networkCallback != null) {
      unregisterNetworkCallbackV24();
    }
  }

  /** Returns watched {@link Requirements}. */
  public Requirements getRequirements() {
    return requirements;
  }

  @RequiresApi(24)
  private void registerNetworkCallbackV24() {
    ConnectivityManager connectivityManager =
        checkNotNull((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE));
    networkCallback = new NetworkCallback();
    connectivityManager.registerDefaultNetworkCallback(networkCallback);
  }

  @RequiresApi(24)
  private void unregisterNetworkCallbackV24() {
    ConnectivityManager connectivityManager =
        checkNotNull((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE));
    connectivityManager.unregisterNetworkCallback(checkNotNull(networkCallback));
    networkCallback = null;
  }

  private void checkRequirements() {
    @Requirements.RequirementFlags
    int notMetRequirements = requirements.getNotMetRequirements(context);
    if (this.notMetRequirements != notMetRequirements) {
      this.notMetRequirements = notMetRequirements;
      listener.onRequirementsStateChanged(this, notMetRequirements);
    }
  }

  /**
   * Re-checks the requirements if there are network requirements that are currently not met.
   *
   * <p>When we receive an event that implies newly established network connectivity, we re-check
   * the requirements by calling {@link #checkRequirements()}. This check sometimes sees that there
   * is still no active network, meaning that any network requirements will remain not met. By
   * calling this method when we receive other events that imply continued network connectivity, we
   * can detect that the requirements are met once an active network does exist.
   */
  private void recheckNotMetNetworkRequirements() {
    if ((notMetRequirements & (Requirements.NETWORK | Requirements.NETWORK_UNMETERED)) == 0) {
      // No unmet network requirements to recheck.
      return;
    }
    checkRequirements();
  }

  private class DeviceStatusChangeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (!isInitialStickyBroadcast()) {
        checkRequirements();
      }
    }
  }

  @RequiresApi(24)
  private final class NetworkCallback extends ConnectivityManager.NetworkCallback {

    private boolean receivedCapabilitiesChange;
    private boolean networkValidated;

    @Override
    public void onAvailable(Network network) {
      postCheckRequirements();
    }

    @Override
    public void onLost(Network network) {
      postCheckRequirements();
    }

    @Override
    public void onBlockedStatusChanged(Network network, boolean blocked) {
      if (!blocked) {
        postRecheckNotMetNetworkRequirements();
      }
    }

    @Override
    public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
      boolean networkValidated =
          networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
      if (!receivedCapabilitiesChange || this.networkValidated != networkValidated) {
        receivedCapabilitiesChange = true;
        this.networkValidated = networkValidated;
        postCheckRequirements();
      } else if (networkValidated) {
        postRecheckNotMetNetworkRequirements();
      }
    }

    private void postCheckRequirements() {
      handler.post(
          () -> {
            if (networkCallback != null) {
              checkRequirements();
            }
          });
    }

    private void postRecheckNotMetNetworkRequirements() {
      handler.post(
          () -> {
            if (networkCallback != null) {
              recheckNotMetNetworkRequirements();
            }
          });
    }
  }
}
