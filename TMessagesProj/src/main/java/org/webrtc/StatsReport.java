/*
 *  Copyright 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

/** Java version of webrtc::StatsReport. */
public class StatsReport {
  /** Java version of webrtc::StatsReport::Value. */
  public static class Value {
    public final String name;
    public final String value;

    @CalledByNative("Value")
    public Value(String name, String value) {
      this.name = name;
      this.value = value;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append("[").append(name).append(": ").append(value).append("]");
      return builder.toString();
    }
  }

  public final String id;
  public final String type;
  // Time since 1970-01-01T00:00:00Z in milliseconds.
  public final double timestamp;
  public final Value[] values;

  @CalledByNative
  public StatsReport(String id, String type, double timestamp, Value[] values) {
    this.id = id;
    this.type = type;
    this.timestamp = timestamp;
    this.values = values;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("id: ")
        .append(id)
        .append(", type: ")
        .append(type)
        .append(", timestamp: ")
        .append(timestamp)
        .append(", values: ");
    for (int i = 0; i < values.length; ++i) {
      builder.append(values[i].toString()).append(", ");
    }
    return builder.toString();
  }
}
