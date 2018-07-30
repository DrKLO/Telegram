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
package org.telegram.messenger.exoplayer2.upstream;

import android.os.Handler;
import android.support.annotation.Nullable;
import org.telegram.messenger.exoplayer2.util.Assertions;
import org.telegram.messenger.exoplayer2.util.Clock;
import org.telegram.messenger.exoplayer2.util.SlidingPercentile;

/**
 * Estimates bandwidth by listening to data transfers. The bandwidth estimate is calculated using
 * a {@link SlidingPercentile} and is updated each time a transfer ends.
 */
public final class DefaultBandwidthMeter implements BandwidthMeter, TransferListener<Object> {

  /** Default initial bitrate estimate in bits per second. */
  public static final long DEFAULT_INITIAL_BITRATE_ESTIMATE = 1_000_000;

  /** Default maximum weight for the sliding window. */
  public static final int DEFAULT_SLIDING_WINDOW_MAX_WEIGHT = 2000;

  /** Builder for a bandwidth meter. */
  public static final class Builder {

    private @Nullable Handler eventHandler;
    private @Nullable EventListener eventListener;
    private long initialBitrateEstimate;
    private int slidingWindowMaxWeight;
    private Clock clock;

    /** Creates a builder with default parameters and without listener. */
    public Builder() {
      initialBitrateEstimate = DEFAULT_INITIAL_BITRATE_ESTIMATE;
      slidingWindowMaxWeight = DEFAULT_SLIDING_WINDOW_MAX_WEIGHT;
      clock = Clock.DEFAULT;
    }

    /**
     * Sets an event listener for new bandwidth estimates.
     *
     * @param eventHandler A handler for events.
     * @param eventListener A listener of events.
     * @return This builder.
     * @throws IllegalArgumentException If the event handler or listener are null.
     */
    public Builder setEventListener(Handler eventHandler, EventListener eventListener) {
      Assertions.checkArgument(eventHandler != null && eventListener != null);
      this.eventHandler = eventHandler;
      this.eventListener = eventListener;
      return this;
    }

    /**
     * Sets the maximum weight for the sliding window.
     *
     * @param slidingWindowMaxWeight The maximum weight for the sliding window.
     * @return This builder.
     */
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
    public Builder setInitialBitrateEstimate(long initialBitrateEstimate) {
      this.initialBitrateEstimate = initialBitrateEstimate;
      return this;
    }

    /**
     * Sets the clock used to estimate bandwidth from data transfers. Should only be set for testing
     * purposes.
     *
     * @param clock The clock used to estimate bandwidth from data transfers.
     * @return This builder.
     */
    public Builder setClock(Clock clock) {
      this.clock = clock;
      return this;
    }

    /**
     * Builds the bandwidth meter.
     *
     * @return A bandwidth meter with the configured properties.
     */
    public DefaultBandwidthMeter build() {
      return new DefaultBandwidthMeter(
          eventHandler, eventListener, initialBitrateEstimate, slidingWindowMaxWeight, clock);
    }
  }

  private static final int ELAPSED_MILLIS_FOR_ESTIMATE = 2000;
  private static final int BYTES_TRANSFERRED_FOR_ESTIMATE = 512 * 1024;

  private final @Nullable Handler eventHandler;
  private final @Nullable EventListener eventListener;
  private final SlidingPercentile slidingPercentile;
  private final Clock clock;

  private int streamCount;
  private long sampleStartTimeMs;
  private long sampleBytesTransferred;

  private long totalElapsedTimeMs;
  private long totalBytesTransferred;
  private long bitrateEstimate;

  /** Creates a bandwidth meter with default parameters. */
  public DefaultBandwidthMeter() {
    this(
        /* eventHandler= */ null,
        /* eventListener= */ null,
        DEFAULT_INITIAL_BITRATE_ESTIMATE,
        DEFAULT_SLIDING_WINDOW_MAX_WEIGHT,
        Clock.DEFAULT);
  }

  /** @deprecated Use {@link Builder} instead. */
  @Deprecated
  public DefaultBandwidthMeter(Handler eventHandler, EventListener eventListener) {
    this(
        eventHandler,
        eventListener,
        DEFAULT_INITIAL_BITRATE_ESTIMATE,
        DEFAULT_SLIDING_WINDOW_MAX_WEIGHT,
        Clock.DEFAULT);
  }

  /** @deprecated Use {@link Builder} instead. */
  @Deprecated
  public DefaultBandwidthMeter(Handler eventHandler, EventListener eventListener, int maxWeight) {
    this(eventHandler, eventListener, DEFAULT_INITIAL_BITRATE_ESTIMATE, maxWeight, Clock.DEFAULT);
  }

  private DefaultBandwidthMeter(
      @Nullable Handler eventHandler,
      @Nullable EventListener eventListener,
      long initialBitrateEstimate,
      int maxWeight,
      Clock clock) {
    this.eventHandler = eventHandler;
    this.eventListener = eventListener;
    this.slidingPercentile = new SlidingPercentile(maxWeight);
    this.clock = clock;
    bitrateEstimate = initialBitrateEstimate;
  }

  @Override
  public synchronized long getBitrateEstimate() {
    return bitrateEstimate;
  }

  @Override
  public synchronized void onTransferStart(Object source, DataSpec dataSpec) {
    if (streamCount == 0) {
      sampleStartTimeMs = clock.elapsedRealtime();
    }
    streamCount++;
  }

  @Override
  public synchronized void onBytesTransferred(Object source, int bytes) {
    sampleBytesTransferred += bytes;
  }

  @Override
  public synchronized void onTransferEnd(Object source) {
    Assertions.checkState(streamCount > 0);
    long nowMs = clock.elapsedRealtime();
    int sampleElapsedTimeMs = (int) (nowMs - sampleStartTimeMs);
    totalElapsedTimeMs += sampleElapsedTimeMs;
    totalBytesTransferred += sampleBytesTransferred;
    if (sampleElapsedTimeMs > 0) {
      float bitsPerSecond = (sampleBytesTransferred * 8000) / sampleElapsedTimeMs;
      slidingPercentile.addSample((int) Math.sqrt(sampleBytesTransferred), bitsPerSecond);
      if (totalElapsedTimeMs >= ELAPSED_MILLIS_FOR_ESTIMATE
          || totalBytesTransferred >= BYTES_TRANSFERRED_FOR_ESTIMATE) {
        bitrateEstimate = (long) slidingPercentile.getPercentile(0.5f);
      }
    }
    notifyBandwidthSample(sampleElapsedTimeMs, sampleBytesTransferred, bitrateEstimate);
    if (--streamCount > 0) {
      sampleStartTimeMs = nowMs;
    }
    sampleBytesTransferred = 0;
  }

  private void notifyBandwidthSample(final int elapsedMs, final long bytes, final long bitrate) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onBandwidthSample(elapsedMs, bytes, bitrate);
        }
      });
    }
  }
}
