/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Log;

/**
 * Handles a {@link WakeLock}.
 *
 * <p>The handling of wake locks requires the {@link android.Manifest.permission#WAKE_LOCK}
 * permission.
 */
/* package */ final class WakeLockManager {

  private static final String TAG = "WakeLockManager";
  private static final String WAKE_LOCK_TAG = "ExoPlayer:WakeLockManager";

  @Nullable private final PowerManager powerManager;
  @Nullable private WakeLock wakeLock;
  private boolean enabled;
  private boolean stayAwake;

  public WakeLockManager(Context context) {
    powerManager =
        (PowerManager) context.getApplicationContext().getSystemService(Context.POWER_SERVICE);
  }

  /**
   * Sets whether to enable the acquiring and releasing of the {@link WakeLock}.
   *
   * <p>By default, wake lock handling is not enabled. Enabling this will acquire the wake lock if
   * necessary. Disabling this will release the wake lock if it is held.
   *
   * <p>Enabling {@link WakeLock} requires the {@link android.Manifest.permission#WAKE_LOCK}.
   *
   * @param enabled True if the player should handle a {@link WakeLock}, false otherwise.
   */
  public void setEnabled(boolean enabled) {
    if (enabled) {
      if (wakeLock == null) {
        if (powerManager == null) {
          Log.w(TAG, "PowerManager is null, therefore not creating the WakeLock.");
          return;
        }
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);
        wakeLock.setReferenceCounted(false);
      }
    }

    this.enabled = enabled;
    updateWakeLock();
  }

  /**
   * Sets whether to acquire or release the {@link WakeLock}.
   *
   * <p>Please note this method requires wake lock handling to be enabled through setEnabled(boolean
   * enable) to actually have an impact on the {@link WakeLock}.
   *
   * @param stayAwake True if the player should acquire the {@link WakeLock}. False if the player
   *     should release.
   */
  public void setStayAwake(boolean stayAwake) {
    this.stayAwake = stayAwake;
    updateWakeLock();
  }

  // WakelockTimeout suppressed because the time the wake lock is needed for is unknown (could be
  // listening to radio with screen off for multiple hours), therefore we can not determine a
  // reasonable timeout that would not affect the user.
  @SuppressLint("WakelockTimeout")
  private void updateWakeLock() {
    if (wakeLock == null) {
      return;
    }

    if (enabled && stayAwake) {
      wakeLock.acquire();
    } else {
      wakeLock.release();
    }
  }
}
