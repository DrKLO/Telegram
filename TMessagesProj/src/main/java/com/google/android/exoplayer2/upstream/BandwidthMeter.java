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

import android.os.Handler;
import android.support.annotation.Nullable;

/**
 * Provides estimates of the currently available bandwidth.
 */
public interface BandwidthMeter {

  /**
   * A listener of {@link BandwidthMeter} events.
   */
  interface EventListener {

    /**
     * Called periodically to indicate that bytes have been transferred.
     *
     * <p>Note: The estimated bitrate is typically derived from more information than just {@code
     * bytes} and {@code elapsedMs}.
     *
     * @param elapsedMs The time taken to transfer the bytes, in milliseconds.
     * @param bytes The number of bytes transferred.
     * @param bitrate The estimated bitrate in bits/sec.
     */
    void onBandwidthSample(int elapsedMs, long bytes, long bitrate);
  }

  /** Returns the estimated bandwidth in bits/sec. */
  long getBitrateEstimate();

  /**
   * Returns the {@link TransferListener} that this instance uses to gather bandwidth information
   * from data transfers. May be null, if no transfer listener is used.
   */
  @Nullable
  TransferListener getTransferListener();

  /**
   * Adds an {@link EventListener} to be informed of bandwidth samples.
   *
   * @param eventHandler A handler for events.
   * @param eventListener A listener of events.
   */
  void addEventListener(Handler eventHandler, EventListener eventListener);

  /**
   * Removes an {@link EventListener}.
   *
   * @param eventListener The listener to be removed.
   */
  void removeEventListener(EventListener eventListener);
}
