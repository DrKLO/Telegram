/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.upstream;

import android.content.Context;
import android.os.Handler;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.BandwidthMeter.EventListener.EventDispatcher;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.NetworkTypeObserver;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.HashMap;
import java.util.Map;

/**
 * Estimates bandwidth by listening to data transfers.
 *
 * <p>The bandwidth estimate is calculated using a {@link SlidingPercentile} and is updated each
 * time a transfer ends. The initial estimate is based on the current operator's network country
 * code or the locale of the user, as well as the network connection type. This can be configured in
 * the {@link Builder}.
 */
public final class DefaultBandwidthMeter implements BandwidthMeter, TransferListener {

  /** Default initial Wifi bitrate estimate in bits per second. */
  public static final ImmutableList<Long> DEFAULT_INITIAL_BITRATE_ESTIMATES_WIFI =
      ImmutableList.of(4_400_000L, 3_200_000L, 2_300_000L, 1_600_000L, 810_000L);

  /** Default initial 2G bitrate estimates in bits per second. */
  public static final ImmutableList<Long> DEFAULT_INITIAL_BITRATE_ESTIMATES_2G =
      ImmutableList.of(1_400_000L, 990_000L, 730_000L, 510_000L, 230_000L);

  /** Default initial 3G bitrate estimates in bits per second. */
  public static final ImmutableList<Long> DEFAULT_INITIAL_BITRATE_ESTIMATES_3G =
      ImmutableList.of(2_100_000L, 1_400_000L, 1_000_000L, 890_000L, 640_000L);

  /** Default initial 4G bitrate estimates in bits per second. */
  public static final ImmutableList<Long> DEFAULT_INITIAL_BITRATE_ESTIMATES_4G =
      ImmutableList.of(2_600_000L, 1_700_000L, 1_300_000L, 1_000_000L, 700_000L);

  /** Default initial 5G-NSA bitrate estimates in bits per second. */
  public static final ImmutableList<Long> DEFAULT_INITIAL_BITRATE_ESTIMATES_5G_NSA =
      ImmutableList.of(5_700_000L, 3_700_000L, 2_300_000L, 1_700_000L, 990_000L);

  /** Default initial 5G-SA bitrate estimates in bits per second. */
  public static final ImmutableList<Long> DEFAULT_INITIAL_BITRATE_ESTIMATES_5G_SA =
      ImmutableList.of(2_800_000L, 1_800_000L, 1_400_000L, 1_100_000L, 870_000L);

  /**
   * Default initial bitrate estimate used when the device is offline or the network type cannot be
   * determined, in bits per second.
   */
  public static final long DEFAULT_INITIAL_BITRATE_ESTIMATE = 1_000_000;

  /** Default maximum weight for the sliding window. */
  public static final int DEFAULT_SLIDING_WINDOW_MAX_WEIGHT = 2000;

  /**
   * Index for the Wifi group index in the array returned by {@link
   * #getInitialBitrateCountryGroupAssignment}.
   */
  private static final int COUNTRY_GROUP_INDEX_WIFI = 0;
  /**
   * Index for the 2G group index in the array returned by {@link
   * #getInitialBitrateCountryGroupAssignment}.
   */
  private static final int COUNTRY_GROUP_INDEX_2G = 1;
  /**
   * Index for the 3G group index in the array returned by {@link
   * #getInitialBitrateCountryGroupAssignment}.
   */
  private static final int COUNTRY_GROUP_INDEX_3G = 2;
  /**
   * Index for the 4G group index in the array returned by {@link
   * #getInitialBitrateCountryGroupAssignment}.
   */
  private static final int COUNTRY_GROUP_INDEX_4G = 3;
  /**
   * Index for the 5G-NSA group index in the array returned by {@link
   * #getInitialBitrateCountryGroupAssignment}.
   */
  private static final int COUNTRY_GROUP_INDEX_5G_NSA = 4;
  /**
   * Index for the 5G-SA group index in the array returned by {@link
   * #getInitialBitrateCountryGroupAssignment}.
   */
  private static final int COUNTRY_GROUP_INDEX_5G_SA = 5;

  @Nullable private static DefaultBandwidthMeter singletonInstance;

  /** Builder for a bandwidth meter. */
  public static final class Builder {

    @Nullable private final Context context;

    private Map<Integer, Long> initialBitrateEstimates;
    private int slidingWindowMaxWeight;
    private Clock clock;
    private boolean resetOnNetworkTypeChange;

    /**
     * Creates a builder with default parameters and without listener.
     *
     * @param context A context.
     */
    public Builder(Context context) {
      // Handling of null is for backward compatibility only.
      this.context = context == null ? null : context.getApplicationContext();
      initialBitrateEstimates = getInitialBitrateEstimatesForCountry(Util.getCountryCode(context));
      slidingWindowMaxWeight = DEFAULT_SLIDING_WINDOW_MAX_WEIGHT;
      clock = Clock.DEFAULT;
      resetOnNetworkTypeChange = true;
    }

    /**
     * Sets the maximum weight for the sliding window.
     *
     * @param slidingWindowMaxWeight The maximum weight for the sliding window.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setSlidingWindowMaxWeight(int slidingWindowMaxWeight) {
      this.slidingWindowMaxWeight = slidingWindowMaxWeight;
      return this;
    }

    /**
     * Sets the initial bitrate estimate in bits per second that should be assumed when a bandwidth
     * estimate is unavailable.
     *
     * @param initialBitrateEstimate The initial bitrate estimate in bits per second.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setInitialBitrateEstimate(long initialBitrateEstimate) {
      for (Integer networkType : initialBitrateEstimates.keySet()) {
        setInitialBitrateEstimate(networkType, initialBitrateEstimate);
      }
      return this;
    }

    /**
     * Sets the initial bitrate estimate in bits per second that should be assumed when a bandwidth
     * estimate is unavailable and the current network connection is of the specified type.
     *
     * @param networkType The {@link C.NetworkType} this initial estimate is for.
     * @param initialBitrateEstimate The initial bitrate estimate in bits per second.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setInitialBitrateEstimate(
        @C.NetworkType int networkType, long initialBitrateEstimate) {
      initialBitrateEstimates.put(networkType, initialBitrateEstimate);
      return this;
    }

    /**
     * Sets the initial bitrate estimates to the default values of the specified country. The
     * initial estimates are used when a bandwidth estimate is unavailable.
     *
     * @param countryCode The ISO 3166-1 alpha-2 country code of the country whose default bitrate
     *     estimates should be used.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setInitialBitrateEstimate(String countryCode) {
      initialBitrateEstimates =
          getInitialBitrateEstimatesForCountry(Ascii.toUpperCase(countryCode));
      return this;
    }

    /**
     * Sets the clock used to estimate bandwidth from data transfers. Should only be set for testing
     * purposes.
     *
     * @param clock The clock used to estimate bandwidth from data transfers.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setClock(Clock clock) {
      this.clock = clock;
      return this;
    }

    /**
     * Sets whether to reset if the network type changes. The default value is {@code true}.
     *
     * @param resetOnNetworkTypeChange Whether to reset if the network type changes.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setResetOnNetworkTypeChange(boolean resetOnNetworkTypeChange) {
      this.resetOnNetworkTypeChange = resetOnNetworkTypeChange;
      return this;
    }

    /**
     * Builds the bandwidth meter.
     *
     * @return A bandwidth meter with the configured properties.
     */
    public DefaultBandwidthMeter build() {
      return new DefaultBandwidthMeter(
          context,
          initialBitrateEstimates,
          slidingWindowMaxWeight,
          clock,
          resetOnNetworkTypeChange);
    }

    private static Map<Integer, Long> getInitialBitrateEstimatesForCountry(String countryCode) {
      int[] groupIndices = getInitialBitrateCountryGroupAssignment(countryCode);
      Map<Integer, Long> result = new HashMap<>(/* initialCapacity= */ 8);
      result.put(C.NETWORK_TYPE_UNKNOWN, DEFAULT_INITIAL_BITRATE_ESTIMATE);
      result.put(
          C.NETWORK_TYPE_WIFI,
          DEFAULT_INITIAL_BITRATE_ESTIMATES_WIFI.get(groupIndices[COUNTRY_GROUP_INDEX_WIFI]));
      result.put(
          C.NETWORK_TYPE_2G,
          DEFAULT_INITIAL_BITRATE_ESTIMATES_2G.get(groupIndices[COUNTRY_GROUP_INDEX_2G]));
      result.put(
          C.NETWORK_TYPE_3G,
          DEFAULT_INITIAL_BITRATE_ESTIMATES_3G.get(groupIndices[COUNTRY_GROUP_INDEX_3G]));
      result.put(
          C.NETWORK_TYPE_4G,
          DEFAULT_INITIAL_BITRATE_ESTIMATES_4G.get(groupIndices[COUNTRY_GROUP_INDEX_4G]));
      result.put(
          C.NETWORK_TYPE_5G_NSA,
          DEFAULT_INITIAL_BITRATE_ESTIMATES_5G_NSA.get(groupIndices[COUNTRY_GROUP_INDEX_5G_NSA]));
      result.put(
          C.NETWORK_TYPE_5G_SA,
          DEFAULT_INITIAL_BITRATE_ESTIMATES_5G_SA.get(groupIndices[COUNTRY_GROUP_INDEX_5G_SA]));
      // Assume default Wifi speed for Ethernet to prevent using the slower fallback.
      result.put(
          C.NETWORK_TYPE_ETHERNET,
          DEFAULT_INITIAL_BITRATE_ESTIMATES_WIFI.get(groupIndices[COUNTRY_GROUP_INDEX_WIFI]));
      return result;
    }
  }

  /**
   * Returns a singleton instance of a {@link DefaultBandwidthMeter} with default configuration.
   *
   * @param context A {@link Context}.
   * @return The singleton instance.
   */
  public static synchronized DefaultBandwidthMeter getSingletonInstance(Context context) {
    if (singletonInstance == null) {
      singletonInstance = new DefaultBandwidthMeter.Builder(context).build();
    }
    return singletonInstance;
  }

  private static final int ELAPSED_MILLIS_FOR_ESTIMATE = 2000;
  private static final int BYTES_TRANSFERRED_FOR_ESTIMATE = 512 * 1024;

  private final ImmutableMap<Integer, Long> initialBitrateEstimates;
  private final EventDispatcher eventDispatcher;
  private final SlidingPercentile slidingPercentile;
  private final Clock clock;
  private final boolean resetOnNetworkTypeChange;

  private int streamCount;
  private long sampleStartTimeMs;
  private long sampleBytesTransferred;

  private @C.NetworkType int networkType;
  private long totalElapsedTimeMs;
  private long totalBytesTransferred;
  private long bitrateEstimate;
  private long lastReportedBitrateEstimate;

  private boolean networkTypeOverrideSet;
  private @C.NetworkType int networkTypeOverride;

  /**
   * @deprecated Use {@link Builder} instead.
   */
  @Deprecated
  public DefaultBandwidthMeter() {
    this(
        /* context= */ null,
        /* initialBitrateEstimates= */ ImmutableMap.of(),
        DEFAULT_SLIDING_WINDOW_MAX_WEIGHT,
        Clock.DEFAULT,
        /* resetOnNetworkTypeChange= */ false);
  }

  private DefaultBandwidthMeter(
      @Nullable Context context,
      Map<Integer, Long> initialBitrateEstimates,
      int maxWeight,
      Clock clock,
      boolean resetOnNetworkTypeChange) {
    this.initialBitrateEstimates = ImmutableMap.copyOf(initialBitrateEstimates);
    this.eventDispatcher = new EventDispatcher();
    this.slidingPercentile = new SlidingPercentile(maxWeight);
    this.clock = clock;
    this.resetOnNetworkTypeChange = resetOnNetworkTypeChange;
    if (context != null) {
      NetworkTypeObserver networkTypeObserver = NetworkTypeObserver.getInstance(context);
      networkType = networkTypeObserver.getNetworkType();
      bitrateEstimate = getInitialBitrateEstimateForNetworkType(networkType);
      networkTypeObserver.register(/* listener= */ this::onNetworkTypeChanged);
    } else {
      networkType = C.NETWORK_TYPE_UNKNOWN;
      bitrateEstimate = getInitialBitrateEstimateForNetworkType(C.NETWORK_TYPE_UNKNOWN);
    }
  }

  /**
   * Overrides the network type. Handled in the same way as if the meter had detected a change from
   * the current network type to the specified network type internally.
   *
   * <p>Applications should not normally call this method. It is intended for testing purposes.
   *
   * @param networkType The overriding network type.
   */
  public synchronized void setNetworkTypeOverride(@C.NetworkType int networkType) {
    networkTypeOverride = networkType;
    networkTypeOverrideSet = true;
    onNetworkTypeChanged(networkType);
  }

  @Override
  public synchronized long getBitrateEstimate() {
    return bitrateEstimate;
  }

  @Override
  public TransferListener getTransferListener() {
    return this;
  }

  @Override
  public void addEventListener(Handler eventHandler, EventListener eventListener) {
    Assertions.checkNotNull(eventHandler);
    Assertions.checkNotNull(eventListener);
    eventDispatcher.addListener(eventHandler, eventListener);
  }

  @Override
  public void removeEventListener(EventListener eventListener) {
    eventDispatcher.removeListener(eventListener);
  }

  @Override
  public void onTransferInitializing(DataSource source, DataSpec dataSpec, boolean isNetwork) {
    // Do nothing.
  }

  @Override
  public synchronized void onTransferStart(
      DataSource source, DataSpec dataSpec, boolean isNetwork) {
    if (!isTransferAtFullNetworkSpeed(dataSpec, isNetwork)) {
      return;
    }
    if (streamCount == 0) {
      sampleStartTimeMs = clock.elapsedRealtime();
    }
    streamCount++;
  }

  @Override
  public synchronized void onBytesTransferred(
      DataSource source, DataSpec dataSpec, boolean isNetwork, int bytesTransferred) {
    if (!isTransferAtFullNetworkSpeed(dataSpec, isNetwork)) {
      return;
    }
    sampleBytesTransferred += bytesTransferred;
  }

  @Override
  public synchronized void onTransferEnd(DataSource source, DataSpec dataSpec, boolean isNetwork) {
    if (!isTransferAtFullNetworkSpeed(dataSpec, isNetwork)) {
      return;
    }
    Assertions.checkState(streamCount > 0);
    long nowMs = clock.elapsedRealtime();
    int sampleElapsedTimeMs = (int) (nowMs - sampleStartTimeMs);
    totalElapsedTimeMs += sampleElapsedTimeMs;
    totalBytesTransferred += sampleBytesTransferred;
    if (sampleElapsedTimeMs > 0) {
      float bitsPerSecond = (sampleBytesTransferred * 8000f) / sampleElapsedTimeMs;
      slidingPercentile.addSample((int) Math.sqrt(sampleBytesTransferred), bitsPerSecond);
      if (totalElapsedTimeMs >= ELAPSED_MILLIS_FOR_ESTIMATE
          || totalBytesTransferred >= BYTES_TRANSFERRED_FOR_ESTIMATE) {
        bitrateEstimate = (long) slidingPercentile.getPercentile(0.5f);
      }
      maybeNotifyBandwidthSample(sampleElapsedTimeMs, sampleBytesTransferred, bitrateEstimate);
      sampleStartTimeMs = nowMs;
      sampleBytesTransferred = 0;
    } // Else any sample bytes transferred will be carried forward into the next sample.
    streamCount--;
  }

  private synchronized void onNetworkTypeChanged(@C.NetworkType int networkType) {
    if (this.networkType != C.NETWORK_TYPE_UNKNOWN && !resetOnNetworkTypeChange) {
      // Reset on network change disabled. Ignore all updates except the initial one.
      return;
    }

    if (networkTypeOverrideSet) {
      networkType = networkTypeOverride;
    }
    if (this.networkType == networkType) {
      return;
    }

    this.networkType = networkType;
    if (networkType == C.NETWORK_TYPE_OFFLINE
        || networkType == C.NETWORK_TYPE_UNKNOWN
        || networkType == C.NETWORK_TYPE_OTHER) {
      // It's better not to reset the bandwidth meter for these network types.
      return;
    }

    // Reset the bitrate estimate and report it, along with any bytes transferred.
    this.bitrateEstimate = getInitialBitrateEstimateForNetworkType(networkType);
    long nowMs = clock.elapsedRealtime();
    int sampleElapsedTimeMs = streamCount > 0 ? (int) (nowMs - sampleStartTimeMs) : 0;
    maybeNotifyBandwidthSample(sampleElapsedTimeMs, sampleBytesTransferred, bitrateEstimate);

    // Reset the remainder of the state.
    sampleStartTimeMs = nowMs;
    sampleBytesTransferred = 0;
    totalBytesTransferred = 0;
    totalElapsedTimeMs = 0;
    slidingPercentile.reset();
  }

  private void maybeNotifyBandwidthSample(
      int elapsedMs, long bytesTransferred, long bitrateEstimate) {
    if (elapsedMs == 0 && bytesTransferred == 0 && bitrateEstimate == lastReportedBitrateEstimate) {
      return;
    }
    lastReportedBitrateEstimate = bitrateEstimate;
    eventDispatcher.bandwidthSample(elapsedMs, bytesTransferred, bitrateEstimate);
  }

  private long getInitialBitrateEstimateForNetworkType(@C.NetworkType int networkType) {
    Long initialBitrateEstimate = initialBitrateEstimates.get(networkType);
    if (initialBitrateEstimate == null) {
      initialBitrateEstimate = initialBitrateEstimates.get(C.NETWORK_TYPE_UNKNOWN);
    }
    if (initialBitrateEstimate == null) {
      initialBitrateEstimate = DEFAULT_INITIAL_BITRATE_ESTIMATE;
    }
    return initialBitrateEstimate;
  }

  private static boolean isTransferAtFullNetworkSpeed(DataSpec dataSpec, boolean isNetwork) {
    return isNetwork && !dataSpec.isFlagSet(DataSpec.FLAG_MIGHT_NOT_USE_FULL_NETWORK_SPEED);
  }

  /**
   * Returns initial bitrate group assignments for a {@code country}. The initial bitrate is a list
   * of indices for [Wifi, 2G, 3G, 4G, 5G_NSA, 5G_SA].
   */
  private static int[] getInitialBitrateCountryGroupAssignment(String country) {
    switch (country) {
      case "AD":
      case "CW":
        return new int[] {2, 2, 0, 0, 2, 2};
      case "AE":
        return new int[] {1, 4, 3, 4, 4, 2};
      case "AG":
        return new int[] {2, 4, 3, 4, 2, 2};
      case "AL":
        return new int[] {1, 1, 1, 3, 2, 2};
      case "AM":
        return new int[] {2, 3, 2, 3, 2, 2};
      case "AO":
        return new int[] {4, 4, 4, 3, 2, 2};
      case "AS":
        return new int[] {2, 2, 3, 3, 2, 2};
      case "AT":
        return new int[] {1, 2, 1, 4, 1, 4};
      case "AU":
        return new int[] {0, 2, 1, 1, 3, 0};
      case "BE":
        return new int[] {0, 1, 4, 4, 3, 2};
      case "BH":
        return new int[] {1, 3, 1, 4, 4, 2};
      case "BJ":
        return new int[] {4, 4, 2, 3, 2, 2};
      case "BN":
        return new int[] {3, 2, 0, 1, 2, 2};
      case "BO":
        return new int[] {1, 2, 3, 2, 2, 2};
      case "BR":
        return new int[] {1, 1, 2, 1, 1, 0};
      case "BW":
        return new int[] {3, 2, 1, 0, 2, 2};
      case "BY":
        return new int[] {1, 1, 2, 3, 2, 2};
      case "CA":
        return new int[] {0, 2, 3, 3, 3, 3};
      case "CH":
        return new int[] {0, 0, 0, 0, 0, 3};
      case "BZ":
      case "CK":
        return new int[] {2, 2, 2, 1, 2, 2};
      case "CL":
        return new int[] {1, 1, 2, 1, 3, 2};
      case "CM":
        return new int[] {4, 3, 3, 4, 2, 2};
      case "CN":
        return new int[] {2, 0, 4, 3, 3, 1};
      case "CO":
        return new int[] {2, 3, 4, 2, 2, 2};
      case "CR":
        return new int[] {2, 4, 4, 4, 2, 2};
      case "CV":
        return new int[] {2, 3, 0, 1, 2, 2};
      case "CZ":
        return new int[] {0, 0, 2, 0, 1, 2};
      case "DE":
        return new int[] {0, 1, 3, 2, 2, 2};
      case "DO":
        return new int[] {3, 4, 4, 4, 4, 2};
      case "AZ":
      case "BF":
      case "DZ":
        return new int[] {3, 3, 4, 4, 2, 2};
      case "EC":
        return new int[] {1, 3, 2, 1, 2, 2};
      case "CI":
      case "EG":
        return new int[] {3, 4, 3, 3, 2, 2};
      case "FI":
        return new int[] {0, 0, 0, 2, 0, 2};
      case "FJ":
        return new int[] {3, 1, 2, 3, 2, 2};
      case "FM":
        return new int[] {4, 2, 3, 0, 2, 2};
      case "AI":
      case "BB":
      case "BM":
      case "BQ":
      case "DM":
      case "FO":
        return new int[] {0, 2, 0, 0, 2, 2};
      case "FR":
        return new int[] {1, 1, 2, 1, 1, 2};
      case "GB":
        return new int[] {0, 1, 1, 2, 1, 2};
      case "GE":
        return new int[] {1, 0, 0, 2, 2, 2};
      case "GG":
        return new int[] {0, 2, 1, 0, 2, 2};
      case "CG":
      case "GH":
        return new int[] {3, 3, 3, 3, 2, 2};
      case "GM":
        return new int[] {4, 3, 2, 4, 2, 2};
      case "GN":
        return new int[] {4, 4, 4, 2, 2, 2};
      case "GP":
        return new int[] {3, 1, 1, 3, 2, 2};
      case "GQ":
        return new int[] {4, 4, 3, 3, 2, 2};
      case "GT":
        return new int[] {2, 2, 2, 1, 1, 2};
      case "AW":
      case "GU":
        return new int[] {1, 2, 4, 4, 2, 2};
      case "GW":
        return new int[] {4, 4, 2, 2, 2, 2};
      case "GY":
        return new int[] {3, 0, 1, 1, 2, 2};
      case "HK":
        return new int[] {0, 1, 1, 3, 2, 0};
      case "HN":
        return new int[] {3, 3, 2, 2, 2, 2};
      case "ID":
        return new int[] {3, 1, 1, 2, 3, 2};
      case "BA":
      case "IE":
        return new int[] {1, 1, 1, 1, 2, 2};
      case "IL":
        return new int[] {1, 2, 2, 3, 4, 2};
      case "IM":
        return new int[] {0, 2, 0, 1, 2, 2};
      case "IN":
        return new int[] {1, 1, 2, 1, 2, 1};
      case "IR":
        return new int[] {4, 2, 3, 3, 4, 2};
      case "IS":
        return new int[] {0, 0, 1, 0, 0, 2};
      case "IT":
        return new int[] {0, 0, 1, 1, 1, 2};
      case "GI":
      case "JE":
        return new int[] {1, 2, 0, 1, 2, 2};
      case "JM":
        return new int[] {2, 4, 2, 1, 2, 2};
      case "JO":
        return new int[] {2, 0, 1, 1, 2, 2};
      case "JP":
        return new int[] {0, 3, 3, 3, 4, 4};
      case "KE":
        return new int[] {3, 2, 2, 1, 2, 2};
      case "KH":
        return new int[] {1, 0, 4, 2, 2, 2};
      case "CU":
      case "KI":
        return new int[] {4, 2, 4, 3, 2, 2};
      case "CD":
      case "KM":
        return new int[] {4, 3, 3, 2, 2, 2};
      case "KR":
        return new int[] {0, 2, 2, 4, 4, 4};
      case "KW":
        return new int[] {1, 0, 1, 0, 0, 2};
      case "BD":
      case "KZ":
        return new int[] {2, 1, 2, 2, 2, 2};
      case "LA":
        return new int[] {1, 2, 1, 3, 2, 2};
      case "BS":
      case "LB":
        return new int[] {3, 2, 1, 2, 2, 2};
      case "LK":
        return new int[] {3, 2, 3, 4, 4, 2};
      case "LR":
        return new int[] {3, 4, 3, 4, 2, 2};
      case "LU":
        return new int[] {1, 1, 4, 2, 0, 2};
      case "CY":
      case "HR":
      case "LV":
        return new int[] {1, 0, 0, 0, 0, 2};
      case "MA":
        return new int[] {3, 3, 2, 1, 2, 2};
      case "MC":
        return new int[] {0, 2, 2, 0, 2, 2};
      case "MD":
        return new int[] {1, 0, 0, 0, 2, 2};
      case "ME":
        return new int[] {2, 0, 0, 1, 1, 2};
      case "MH":
        return new int[] {4, 2, 1, 3, 2, 2};
      case "MK":
        return new int[] {2, 0, 0, 1, 3, 2};
      case "MM":
        return new int[] {2, 2, 2, 3, 4, 2};
      case "MN":
        return new int[] {2, 0, 1, 2, 2, 2};
      case "MO":
        return new int[] {0, 2, 4, 4, 4, 2};
      case "KG":
      case "MQ":
        return new int[] {2, 1, 1, 2, 2, 2};
      case "MR":
        return new int[] {4, 2, 3, 4, 2, 2};
      case "DK":
      case "EE":
      case "HU":
      case "LT":
      case "MT":
        return new int[] {0, 0, 0, 0, 0, 2};
      case "MV":
        return new int[] {3, 4, 1, 3, 3, 2};
      case "MW":
        return new int[] {4, 2, 3, 3, 2, 2};
      case "MX":
        return new int[] {3, 4, 4, 4, 2, 2};
      case "MY":
        return new int[] {1, 0, 4, 1, 2, 2};
      case "NA":
        return new int[] {3, 4, 3, 2, 2, 2};
      case "NC":
        return new int[] {3, 2, 3, 4, 2, 2};
      case "NG":
        return new int[] {3, 4, 2, 1, 2, 2};
      case "NI":
        return new int[] {2, 3, 4, 3, 2, 2};
      case "NL":
        return new int[] {0, 2, 3, 3, 0, 4};
      case "NO":
        return new int[] {0, 1, 2, 1, 1, 2};
      case "NP":
        return new int[] {2, 1, 4, 3, 2, 2};
      case "NR":
        return new int[] {4, 0, 3, 2, 2, 2};
      case "NU":
        return new int[] {4, 2, 2, 1, 2, 2};
      case "NZ":
        return new int[] {1, 0, 2, 2, 4, 2};
      case "OM":
        return new int[] {2, 3, 1, 3, 4, 2};
      case "PA":
        return new int[] {2, 3, 3, 3, 2, 2};
      case "PE":
        return new int[] {1, 2, 4, 4, 3, 2};
      case "AF":
      case "PG":
        return new int[] {4, 3, 3, 3, 2, 2};
      case "PH":
        return new int[] {2, 1, 3, 2, 2, 0};
      case "PL":
        return new int[] {2, 1, 2, 2, 4, 2};
      case "PR":
        return new int[] {2, 0, 2, 0, 2, 1};
      case "PS":
        return new int[] {3, 4, 1, 4, 2, 2};
      case "PT":
        return new int[] {1, 0, 0, 0, 1, 2};
      case "PW":
        return new int[] {2, 2, 4, 2, 2, 2};
      case "BL":
      case "MF":
      case "PY":
        return new int[] {1, 2, 2, 2, 2, 2};
      case "QA":
        return new int[] {1, 4, 4, 4, 4, 2};
      case "RE":
        return new int[] {1, 2, 2, 3, 1, 2};
      case "RO":
        return new int[] {0, 0, 1, 2, 1, 2};
      case "RS":
        return new int[] {2, 0, 0, 0, 2, 2};
      case "RU":
        return new int[] {1, 0, 0, 0, 3, 3};
      case "RW":
        return new int[] {3, 3, 1, 0, 2, 2};
      case "MU":
      case "SA":
        return new int[] {3, 1, 1, 2, 2, 2};
      case "CF":
      case "SB":
        return new int[] {4, 2, 4, 2, 2, 2};
      case "SC":
        return new int[] {4, 3, 1, 1, 2, 2};
      case "SD":
        return new int[] {4, 3, 4, 2, 2, 2};
      case "SE":
        return new int[] {0, 1, 1, 1, 0, 2};
      case "SG":
        return new int[] {2, 3, 3, 3, 3, 3};
      case "AQ":
      case "ER":
      case "SH":
        return new int[] {4, 2, 2, 2, 2, 2};
      case "BG":
      case "ES":
      case "GR":
      case "SI":
        return new int[] {0, 0, 0, 0, 1, 2};
      case "IQ":
      case "SJ":
        return new int[] {3, 2, 2, 2, 2, 2};
      case "SK":
        return new int[] {1, 1, 1, 1, 3, 2};
      case "GF":
      case "PK":
      case "SL":
        return new int[] {3, 2, 3, 3, 2, 2};
      case "ET":
      case "SN":
        return new int[] {4, 4, 3, 2, 2, 2};
      case "SO":
        return new int[] {3, 2, 2, 4, 4, 2};
      case "SR":
        return new int[] {2, 4, 3, 0, 2, 2};
      case "ST":
        return new int[] {2, 2, 1, 2, 2, 2};
      case "PF":
      case "SV":
        return new int[] {2, 3, 3, 1, 2, 2};
      case "SZ":
        return new int[] {4, 4, 3, 4, 2, 2};
      case "TC":
        return new int[] {2, 2, 1, 3, 2, 2};
      case "GA":
      case "TG":
        return new int[] {3, 4, 1, 0, 2, 2};
      case "TH":
        return new int[] {0, 1, 2, 1, 2, 2};
      case "DJ":
      case "SY":
      case "TJ":
        return new int[] {4, 3, 4, 4, 2, 2};
      case "GL":
      case "TK":
        return new int[] {2, 2, 2, 4, 2, 2};
      case "TL":
        return new int[] {4, 2, 4, 4, 2, 2};
      case "SS":
      case "TM":
        return new int[] {4, 2, 2, 3, 2, 2};
      case "TR":
        return new int[] {1, 0, 0, 1, 3, 2};
      case "TT":
        return new int[] {1, 4, 0, 0, 2, 2};
      case "TW":
        return new int[] {0, 2, 0, 0, 0, 0};
      case "ML":
      case "TZ":
        return new int[] {3, 4, 2, 2, 2, 2};
      case "UA":
        return new int[] {0, 1, 1, 2, 4, 2};
      case "LS":
      case "UG":
        return new int[] {3, 3, 3, 2, 2, 2};
      case "US":
        return new int[] {1, 1, 4, 1, 3, 1};
      case "TN":
      case "UY":
        return new int[] {2, 1, 1, 1, 2, 2};
      case "UZ":
        return new int[] {2, 2, 3, 4, 3, 2};
      case "AX":
      case "CX":
      case "LI":
      case "MP":
      case "MS":
      case "PM":
      case "SM":
      case "VA":
        return new int[] {0, 2, 2, 2, 2, 2};
      case "GD":
      case "KN":
      case "KY":
      case "LC":
      case "SX":
      case "VC":
        return new int[] {1, 2, 0, 0, 2, 2};
      case "VG":
        return new int[] {2, 2, 0, 1, 2, 2};
      case "VI":
        return new int[] {0, 2, 1, 2, 2, 2};
      case "VN":
        return new int[] {0, 0, 1, 2, 2, 1};
      case "VU":
        return new int[] {4, 3, 3, 1, 2, 2};
      case "IO":
      case "TV":
      case "WF":
        return new int[] {4, 2, 2, 4, 2, 2};
      case "BT":
      case "MZ":
      case "WS":
        return new int[] {3, 1, 2, 1, 2, 2};
      case "XK":
        return new int[] {1, 2, 1, 1, 2, 2};
      case "BI":
      case "HT":
      case "MG":
      case "NE":
      case "TD":
      case "VE":
      case "YE":
        return new int[] {4, 4, 4, 4, 2, 2};
      case "YT":
        return new int[] {2, 3, 3, 4, 2, 2};
      case "ZA":
        return new int[] {2, 3, 2, 1, 2, 2};
      case "ZM":
        return new int[] {4, 4, 4, 3, 3, 2};
      case "LY":
      case "TO":
      case "ZW":
        return new int[] {3, 2, 4, 3, 2, 2};
      default:
        return new int[] {2, 2, 2, 2, 2, 2};
    }
  }
}
