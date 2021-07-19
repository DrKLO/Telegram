/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Log;

/**
 * Handles a {@link WifiLock}
 *
 * <p>The handling of wifi locks requires the {@link android.Manifest.permission#WAKE_LOCK}
 * permission.
 */
/* package */ final class WifiLockManager {

  private static final String TAG = "WifiLockManager";
  private static final String WIFI_LOCK_TAG = "ExoPlayer:WifiLockManager";

  @Nullable private final WifiManager wifiManager;
  @Nullable private WifiLock wifiLock;
  private boolean enabled;
  private boolean stayAwake;

  public WifiLockManager(Context context) {
    wifiManager =
        (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
  }

  /**
   * Sets whether to enable the usage of a {@link WifiLock}.
   *
   * <p>By default, wifi lock handling is not enabled. Enabling will acquire the wifi lock if
   * necessary. Disabling will release the wifi lock if held.
   *
   * <p>Enabling {@link WifiLock} requires the {@link android.Manifest.permission#WAKE_LOCK}.
   *
   * @param enabled True if the player should handle a {@link WifiLock}.
   */
  public void setEnabled(boolean enabled) {
    if (enabled && wifiLock == null) {
      if (wifiManager == null) {
        Log.w(TAG, "WifiManager is null, therefore not creating the WifiLock.");
        return;
      }
      wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, WIFI_LOCK_TAG);
      wifiLock.setReferenceCounted(false);
    }

    this.enabled = enabled;
    updateWifiLock();
  }

  /**
   * Sets whether to acquire or release the {@link WifiLock}.
   *
   * <p>The wifi lock will not be acquired unless handling has been enabled through {@link
   * #setEnabled(boolean)}.
   *
   * @param stayAwake True if the player should acquire the {@link WifiLock}. False if it should
   *     release.
   */
  public void setStayAwake(boolean stayAwake) {
    this.stayAwake = stayAwake;
    updateWifiLock();
  }

  private void updateWifiLock() {
    if (wifiLock == null) {
      return;
    }

    if (enabled && stayAwake) {
      wifiLock.acquire();
    } else {
      wifiLock.release();
    }
  }
}
