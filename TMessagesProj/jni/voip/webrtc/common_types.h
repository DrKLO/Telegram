/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef COMMON_TYPES_H_
#define COMMON_TYPES_H_

#include <stddef.h>  // For size_t

#include <cstdint>

namespace webrtc {

struct FrameCounts {
  FrameCounts() : key_frames(0), delta_frames(0) {}
  int key_frames;
  int delta_frames;
};

// Callback, used to notify an observer whenever frame counts have been updated.
class FrameCountObserver {
 public:
  virtual ~FrameCountObserver() {}
  virtual void FrameCountUpdated(const FrameCounts& frame_counts,
                                 uint32_t ssrc) = 0;
};

// ==================================================================
// Video specific types
// ==================================================================

struct SpatialLayer {
  bool operator==(const SpatialLayer& other) const;
  bool operator!=(const SpatialLayer& other) const { return !(*this == other); }

  unsigned short width;
  unsigned short height;
  float maxFramerate;  // fps.
  unsigned char numberOfTemporalLayers;
  unsigned int maxBitrate;     // kilobits/sec.
  unsigned int targetBitrate;  // kilobits/sec.
  unsigned int minBitrate;     // kilobits/sec.
  unsigned int qpMax;          // minimum quality
  bool active;                 // encoded and sent.
};

// Simulcast is when the same stream is encoded multiple times with different
// settings such as resolution.
typedef SpatialLayer SimulcastStream;

// Minimum and maximum playout delay values from capture to render.
// These are best effort values.
//
// A value < 0 indicates no change from previous valid value.
//
// min = max = 0 indicates that the receiver should try and render
// frame as soon as possible.
//
// min = x, max = y indicates that the receiver is free to adapt
// in the range (x, y) based on network jitter.
//
// Note: Given that this gets embedded in a union, it is up-to the owner to
// initialize these values.
struct PlayoutDelay {
  PlayoutDelay(int min_ms, int max_ms) : min_ms(min_ms), max_ms(max_ms) {}
  int min_ms;
  int max_ms;

  static PlayoutDelay Noop() { return PlayoutDelay(-1, -1); }

  bool IsNoop() const { return min_ms == -1 && max_ms == -1; }
  bool operator==(const PlayoutDelay& rhs) const {
    return min_ms == rhs.min_ms && max_ms == rhs.max_ms;
  }
};

}  // namespace webrtc

#endif  // COMMON_TYPES_H_
