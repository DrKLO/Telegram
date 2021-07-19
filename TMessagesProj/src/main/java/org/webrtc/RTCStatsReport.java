/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import java.util.Map;

/**
 * Java version of webrtc::RTCStatsReport. Each RTCStatsReport produced by
 * getStats contains multiple RTCStats objects; one for each underlying object
 * (codec, stream, transport, etc.) that was inspected to produce the stats.
 */
public class RTCStatsReport {
  private final long timestampUs;
  private final Map<String, RTCStats> stats;

  public RTCStatsReport(long timestampUs, Map<String, RTCStats> stats) {
    this.timestampUs = timestampUs;
    this.stats = stats;
  }

  // Timestamp in microseconds.
  public double getTimestampUs() {
    return timestampUs;
  }

  // Map of stats object IDs to stats objects. Can be used to easily look up
  // other stats objects, when they refer to each other by ID.
  public Map<String, RTCStats> getStatsMap() {
    return stats;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("{ timestampUs: ").append(timestampUs).append(", stats: [\n");
    boolean first = true;
    for (RTCStats stat : stats.values()) {
      if (!first) {
        builder.append(",\n");
      }
      builder.append(stat);
      first = false;
    }
    builder.append(" ] }");
    return builder.toString();
  }

  // TODO(bugs.webrtc.org/8557) Use ctor directly with full Map type.
  @SuppressWarnings("unchecked")
  @CalledByNative
  private static RTCStatsReport create(long timestampUs, Map stats) {
    return new RTCStatsReport(timestampUs, stats);
  }
}
