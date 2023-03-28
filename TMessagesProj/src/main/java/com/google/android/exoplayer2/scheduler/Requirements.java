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

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.LOCAL_VARIABLE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PowerManager;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Defines a set of device state requirements. */
public final class Requirements implements Parcelable {

  /**
   * Requirement flags. Possible flag values are {@link #NETWORK}, {@link #NETWORK_UNMETERED},
   * {@link #DEVICE_IDLE}, {@link #DEVICE_CHARGING} and {@link #DEVICE_STORAGE_NOT_LOW}.
   */
  // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
  // with Kotlin usages from before TYPE_USE was added.
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef(
      flag = true,
      value = {NETWORK, NETWORK_UNMETERED, DEVICE_IDLE, DEVICE_CHARGING, DEVICE_STORAGE_NOT_LOW})
  public @interface RequirementFlags {}

  /** Requirement that the device has network connectivity. */
  public static final int NETWORK = 1;
  /** Requirement that the device has a network connection that is unmetered. */
  public static final int NETWORK_UNMETERED = 1 << 1;
  /** Requirement that the device is idle. */
  public static final int DEVICE_IDLE = 1 << 2;
  /** Requirement that the device is charging. */
  public static final int DEVICE_CHARGING = 1 << 3;
  /**
   * Requirement that the device's <em>internal</em> storage is not low. Note that this requirement
   * is not affected by the status of external storage.
   */
  public static final int DEVICE_STORAGE_NOT_LOW = 1 << 4;

  private final @RequirementFlags int requirements;

  /**
   * @param requirements A combination of requirement flags.
   */
  public Requirements(@RequirementFlags int requirements) {
    if ((requirements & NETWORK_UNMETERED) != 0) {
      // Make sure network requirement flags are consistent.
      requirements |= NETWORK;
    }
    this.requirements = requirements;
  }

  /** Returns the requirements. */
  public @RequirementFlags int getRequirements() {
    return requirements;
  }

  /**
   * Filters the requirements, returning the subset that are enabled by the provided filter.
   *
   * @param requirementsFilter The enabled {@link RequirementFlags}.
   * @return The filtered requirements. If the filter does not cause a change in the requirements
   *     then this instance will be returned.
   */
  public Requirements filterRequirements(int requirementsFilter) {
    int filteredRequirements = requirements & requirementsFilter;
    return filteredRequirements == requirements ? this : new Requirements(filteredRequirements);
  }

  /** Returns whether network connectivity is required. */
  public boolean isNetworkRequired() {
    return (requirements & NETWORK) != 0;
  }

  /** Returns whether un-metered network connectivity is required. */
  public boolean isUnmeteredNetworkRequired() {
    return (requirements & NETWORK_UNMETERED) != 0;
  }

  /** Returns whether the device is required to be charging. */
  public boolean isChargingRequired() {
    return (requirements & DEVICE_CHARGING) != 0;
  }

  /** Returns whether the device is required to be idle. */
  public boolean isIdleRequired() {
    return (requirements & DEVICE_IDLE) != 0;
  }

  /** Returns whether the device is required to not be low on <em>internal</em> storage. */
  public boolean isStorageNotLowRequired() {
    return (requirements & DEVICE_STORAGE_NOT_LOW) != 0;
  }

  /**
   * Returns whether the requirements are met.
   *
   * @param context Any context.
   * @return Whether the requirements are met.
   */
  public boolean checkRequirements(Context context) {
    return getNotMetRequirements(context) == 0;
  }

  /**
   * Returns requirements that are not met, or 0.
   *
   * @param context Any context.
   * @return The requirements that are not met, or 0.
   */
  public @RequirementFlags int getNotMetRequirements(Context context) {
    @RequirementFlags int notMetRequirements = getNotMetNetworkRequirements(context);
    if (isChargingRequired() && !isDeviceCharging(context)) {
      notMetRequirements |= DEVICE_CHARGING;
    }
    if (isIdleRequired() && !isDeviceIdle(context)) {
      notMetRequirements |= DEVICE_IDLE;
    }
    if (isStorageNotLowRequired() && !isStorageNotLow(context)) {
      notMetRequirements |= DEVICE_STORAGE_NOT_LOW;
    }
    return notMetRequirements;
  }

  private @RequirementFlags int getNotMetNetworkRequirements(Context context) {
    if (!isNetworkRequired()) {
      return 0;
    }

    ConnectivityManager connectivityManager =
        (ConnectivityManager)
            Assertions.checkNotNull(context.getSystemService(Context.CONNECTIVITY_SERVICE));
    @Nullable NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
    if (networkInfo == null
        || !networkInfo.isConnected()
        || !isInternetConnectivityValidated(connectivityManager)) {
      return requirements & (NETWORK | NETWORK_UNMETERED);
    }

    if (isUnmeteredNetworkRequired() && connectivityManager.isActiveNetworkMetered()) {
      return NETWORK_UNMETERED;
    }

    return 0;
  }

  private boolean isDeviceCharging(Context context) {
    @Nullable
    Intent batteryStatus =
        Util.registerReceiverNotExported(
            context, /* receiver= */ null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    if (batteryStatus == null) {
      return false;
    }
    int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
    return status == BatteryManager.BATTERY_STATUS_CHARGING
        || status == BatteryManager.BATTERY_STATUS_FULL;
  }

  private boolean isDeviceIdle(Context context) {
    PowerManager powerManager =
        (PowerManager) Assertions.checkNotNull(context.getSystemService(Context.POWER_SERVICE));
    return Util.SDK_INT >= 23
        ? powerManager.isDeviceIdleMode()
        : Util.SDK_INT >= 20 ? !powerManager.isInteractive() : !powerManager.isScreenOn();
  }

  private boolean isStorageNotLow(Context context) {
    return Util.registerReceiverNotExported(
            context, /* receiver= */ null, new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW))
        == null;
  }

  private static boolean isInternetConnectivityValidated(ConnectivityManager connectivityManager) {
    // It's possible to check NetworkCapabilities.NET_CAPABILITY_VALIDATED from API level 23, but
    // RequirementsWatcher only fires an event to re-check the requirements when NetworkCapabilities
    // change from API level 24. We assume that network capability is validated for API level 23 to
    // keep in sync.
    if (Util.SDK_INT < 24) {
      return true;
    }

    @Nullable Network activeNetwork = connectivityManager.getActiveNetwork();
    if (activeNetwork == null) {
      return false;
    }

    try {
      @Nullable
      NetworkCapabilities networkCapabilities =
          connectivityManager.getNetworkCapabilities(activeNetwork);
      return networkCapabilities != null
          && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    } catch (SecurityException e) {
      // Workaround for https://issuetracker.google.com/issues/175055271.
      return true;
    }
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    return requirements == ((Requirements) o).requirements;
  }

  @Override
  public int hashCode() {
    return requirements;
  }

  // Parcelable implementation.

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(requirements);
  }

  public static final Parcelable.Creator<Requirements> CREATOR =
      new Creator<Requirements>() {

        @Override
        public Requirements createFromParcel(Parcel in) {
          return new Requirements(in.readInt());
        }

        @Override
        public Requirements[] newArray(int size) {
          return new Requirements[size];
        }
      };
}
