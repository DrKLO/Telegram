/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.telegram.messenger.exoplayer.upstream;

/**
 * Provides estimates of the currently available bandwidth.
 */
public interface BandwidthMeter extends TransferListener {

  /**
   * Interface definition for a callback to be notified of {@link BandwidthMeter} events.
   */
  public interface EventListener {

    /**
     * Invoked periodically to indicate that bytes have been transferred.
     *
     * @param elapsedMs The time taken to transfer the bytes, in milliseconds.
     * @param bytes The number of bytes transferred.
     * @param bitrate The estimated bitrate in bits/sec, or {@link #NO_ESTIMATE} if no estimate
     *     is available. Note that this estimate is typically derived from more information than
     *     {@code bytes} and {@code elapsedMs}.
     */
    void onBandwidthSample(int elapsedMs, long bytes, long bitrate);
  }

  /**
   * Indicates no bandwidth estimate is available.
   */
  final long NO_ESTIMATE = -1;

  /**
   * Gets the estimated bandwidth, in bits/sec.
   *
   * @return Estimated bandwidth in bits/sec, or {@link #NO_ESTIMATE} if no estimate is available.
   */
  long getBitrateEstimate();

}
