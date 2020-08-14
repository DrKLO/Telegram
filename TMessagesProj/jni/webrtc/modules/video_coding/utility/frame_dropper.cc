/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/utility/frame_dropper.h"

#include <algorithm>

namespace webrtc {

namespace {

const float kDefaultFrameSizeAlpha = 0.9f;
const float kDefaultKeyFrameRatioAlpha = 0.99f;
// 1 key frame every 10th second in 30 fps.
const float kDefaultKeyFrameRatioValue = 1 / 300.0f;

const float kDefaultDropRatioAlpha = 0.9f;
const float kDefaultDropRatioValue = 0.96f;
// Maximum duration over which frames are continuously dropped.
const float kDefaultMaxDropDurationSecs = 4.0f;

// Default target bitrate.
// TODO(isheriff): Should this be higher to avoid dropping too many packets when
// the bandwidth is unknown at the start ?
const float kDefaultTargetBitrateKbps = 300.0f;
const float kDefaultIncomingFrameRate = 30;
const float kLeakyBucketSizeSeconds = 0.5f;

// A delta frame that is bigger than |kLargeDeltaFactor| times the average
// delta frame is a large frame that is spread out for accumulation.
const int kLargeDeltaFactor = 3;

// Cap on the frame size accumulator to prevent excessive drops.
const float kAccumulatorCapBufferSizeSecs = 3.0f;
}  // namespace

FrameDropper::FrameDropper()
    : key_frame_ratio_(kDefaultKeyFrameRatioAlpha),
      delta_frame_size_avg_kbits_(kDefaultFrameSizeAlpha),
      drop_ratio_(kDefaultDropRatioAlpha, kDefaultDropRatioValue),
      enabled_(true),
      max_drop_duration_secs_(kDefaultMaxDropDurationSecs) {
  Reset();
}

FrameDropper::~FrameDropper() = default;

void FrameDropper::Reset() {
  key_frame_ratio_.Reset(kDefaultKeyFrameRatioAlpha);
  key_frame_ratio_.Apply(1.0f, kDefaultKeyFrameRatioValue);
  delta_frame_size_avg_kbits_.Reset(kDefaultFrameSizeAlpha);

  accumulator_ = 0.0f;
  accumulator_max_ = kDefaultTargetBitrateKbps / 2;
  target_bitrate_ = kDefaultTargetBitrateKbps;
  incoming_frame_rate_ = kDefaultIncomingFrameRate;

  large_frame_accumulation_count_ = 0;
  large_frame_accumulation_chunk_size_ = 0;
  large_frame_accumulation_spread_ = 0.5 * kDefaultIncomingFrameRate;

  drop_next_ = false;
  drop_ratio_.Reset(0.9f);
  drop_ratio_.Apply(0.0f, 0.0f);
  drop_count_ = 0;
  was_below_max_ = true;
}

void FrameDropper::Enable(bool enable) {
  enabled_ = enable;
}

void FrameDropper::Fill(size_t framesize_bytes, bool delta_frame) {
  if (!enabled_) {
    return;
  }
  float framesize_kbits = 8.0f * static_cast<float>(framesize_bytes) / 1000.0f;
  if (!delta_frame) {
    key_frame_ratio_.Apply(1.0, 1.0);
    // Do not spread if we are already doing it (or we risk dropping bits that
    // need accumulation). Given we compute the key frame ratio and spread
    // based on that, this should not normally happen.
    if (large_frame_accumulation_count_ == 0) {
      if (key_frame_ratio_.filtered() > 1e-5 &&
          1 / key_frame_ratio_.filtered() < large_frame_accumulation_spread_) {
        large_frame_accumulation_count_ =
            static_cast<int32_t>(1 / key_frame_ratio_.filtered() + 0.5);
      } else {
        large_frame_accumulation_count_ =
            static_cast<int32_t>(large_frame_accumulation_spread_ + 0.5);
      }
      large_frame_accumulation_chunk_size_ =
          framesize_kbits / large_frame_accumulation_count_;
      framesize_kbits = 0;
    }
  } else {
    // Identify if it is an unusually large delta frame and spread accumulation
    // if that is the case.
    if (delta_frame_size_avg_kbits_.filtered() != -1 &&
        (framesize_kbits >
         kLargeDeltaFactor * delta_frame_size_avg_kbits_.filtered()) &&
        large_frame_accumulation_count_ == 0) {
      large_frame_accumulation_count_ =
          static_cast<int32_t>(large_frame_accumulation_spread_ + 0.5);
      large_frame_accumulation_chunk_size_ =
          framesize_kbits / large_frame_accumulation_count_;
      framesize_kbits = 0;
    } else {
      delta_frame_size_avg_kbits_.Apply(1, framesize_kbits);
    }
    key_frame_ratio_.Apply(1.0, 0.0);
  }
  // Change the level of the accumulator (bucket)
  accumulator_ += framesize_kbits;
  CapAccumulator();
}

void FrameDropper::Leak(uint32_t input_framerate) {
  if (!enabled_) {
    return;
  }
  if (input_framerate < 1) {
    return;
  }
  if (target_bitrate_ < 0.0f) {
    return;
  }
  // Add lower bound for large frame accumulation spread.
  large_frame_accumulation_spread_ = std::max(0.5 * input_framerate, 5.0);
  // Expected bits per frame based on current input frame rate.
  float expected_bits_per_frame = target_bitrate_ / input_framerate;
  if (large_frame_accumulation_count_ > 0) {
    expected_bits_per_frame -= large_frame_accumulation_chunk_size_;
    --large_frame_accumulation_count_;
  }
  accumulator_ -= expected_bits_per_frame;
  if (accumulator_ < 0.0f) {
    accumulator_ = 0.0f;
  }
  UpdateRatio();
}

void FrameDropper::UpdateRatio() {
  if (accumulator_ > 1.3f * accumulator_max_) {
    // Too far above accumulator max, react faster.
    drop_ratio_.UpdateBase(0.8f);
  } else {
    // Go back to normal reaction.
    drop_ratio_.UpdateBase(0.9f);
  }
  if (accumulator_ > accumulator_max_) {
    // We are above accumulator max, and should ideally drop a frame. Increase
    // the drop_ratio_ and drop the frame later.
    if (was_below_max_) {
      drop_next_ = true;
    }
    drop_ratio_.Apply(1.0f, 1.0f);
    drop_ratio_.UpdateBase(0.9f);
  } else {
    drop_ratio_.Apply(1.0f, 0.0f);
  }
  was_below_max_ = accumulator_ < accumulator_max_;
}

// This function signals when to drop frames to the caller. It makes use of the
// drop_ratio_ to smooth out the drops over time.
bool FrameDropper::DropFrame() {
  if (!enabled_) {
    return false;
  }
  if (drop_next_) {
    drop_next_ = false;
    drop_count_ = 0;
  }

  if (drop_ratio_.filtered() >= 0.5f) {  // Drops per keep
    // Limit is the number of frames we should drop between each kept frame
    // to keep our drop ratio. limit is positive in this case.
    float denom = 1.0f - drop_ratio_.filtered();
    if (denom < 1e-5) {
      denom = 1e-5f;
    }
    int32_t limit = static_cast<int32_t>(1.0f / denom - 1.0f + 0.5f);
    // Put a bound on the max amount of dropped frames between each kept
    // frame, in terms of frame rate and window size (secs).
    int max_limit =
        static_cast<int>(incoming_frame_rate_ * max_drop_duration_secs_);
    if (limit > max_limit) {
      limit = max_limit;
    }
    if (drop_count_ < 0) {
      // Reset the drop_count_ since it was negative and should be positive.
      drop_count_ = -drop_count_;
    }
    if (drop_count_ < limit) {
      // As long we are below the limit we should drop frames.
      drop_count_++;
      return true;
    } else {
      // Only when we reset drop_count_ a frame should be kept.
      drop_count_ = 0;
      return false;
    }
  } else if (drop_ratio_.filtered() > 0.0f &&
             drop_ratio_.filtered() < 0.5f) {  // Keeps per drop
    // Limit is the number of frames we should keep between each drop
    // in order to keep the drop ratio. limit is negative in this case,
    // and the drop_count_ is also negative.
    float denom = drop_ratio_.filtered();
    if (denom < 1e-5) {
      denom = 1e-5f;
    }
    int32_t limit = -static_cast<int32_t>(1.0f / denom - 1.0f + 0.5f);
    if (drop_count_ > 0) {
      // Reset the drop_count_ since we have a positive
      // drop_count_, and it should be negative.
      drop_count_ = -drop_count_;
    }
    if (drop_count_ > limit) {
      if (drop_count_ == 0) {
        // Drop frames when we reset drop_count_.
        drop_count_--;
        return true;
      } else {
        // Keep frames as long as we haven't reached limit.
        drop_count_--;
        return false;
      }
    } else {
      drop_count_ = 0;
      return false;
    }
  }
  drop_count_ = 0;
  return false;
}

void FrameDropper::SetRates(float bitrate, float incoming_frame_rate) {
  // Bit rate of -1 means infinite bandwidth.
  accumulator_max_ = bitrate * kLeakyBucketSizeSeconds;
  if (target_bitrate_ > 0.0f && bitrate < target_bitrate_ &&
      accumulator_ > accumulator_max_) {
    // Rescale the accumulator level if the accumulator max decreases
    accumulator_ = bitrate / target_bitrate_ * accumulator_;
  }
  target_bitrate_ = bitrate;
  CapAccumulator();
  incoming_frame_rate_ = incoming_frame_rate;
}

// Put a cap on the accumulator, i.e., don't let it grow beyond some level.
// This is a temporary fix for screencasting where very large frames from
// encoder will cause very slow response (too many frame drops).
// TODO(isheriff): Remove this now that large delta frames are also spread out ?
void FrameDropper::CapAccumulator() {
  float max_accumulator = target_bitrate_ * kAccumulatorCapBufferSizeSecs;
  if (accumulator_ > max_accumulator) {
    accumulator_ = max_accumulator;
  }
}
}  // namespace webrtc
