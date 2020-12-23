/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc2/saturation_protector.h"

#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/numerics/safe_minmax.h"

namespace webrtc {
namespace {

constexpr float kMinLevelDbfs = -90.f;

// Min/max margins are based on speech crest-factor.
constexpr float kMinMarginDb = 12.f;
constexpr float kMaxMarginDb = 25.f;

using saturation_protector_impl::RingBuffer;

}  // namespace

bool RingBuffer::operator==(const RingBuffer& b) const {
  RTC_DCHECK_LE(size_, buffer_.size());
  RTC_DCHECK_LE(b.size_, b.buffer_.size());
  if (size_ != b.size_) {
    return false;
  }
  for (int i = 0, i0 = FrontIndex(), i1 = b.FrontIndex(); i < size_;
       ++i, ++i0, ++i1) {
    if (buffer_[i0 % buffer_.size()] != b.buffer_[i1 % b.buffer_.size()]) {
      return false;
    }
  }
  return true;
}

void RingBuffer::Reset() {
  next_ = 0;
  size_ = 0;
}

void RingBuffer::PushBack(float v) {
  RTC_DCHECK_GE(next_, 0);
  RTC_DCHECK_GE(size_, 0);
  RTC_DCHECK_LT(next_, buffer_.size());
  RTC_DCHECK_LE(size_, buffer_.size());
  buffer_[next_++] = v;
  if (rtc::SafeEq(next_, buffer_.size())) {
    next_ = 0;
  }
  if (rtc::SafeLt(size_, buffer_.size())) {
    size_++;
  }
}

absl::optional<float> RingBuffer::Front() const {
  if (size_ == 0) {
    return absl::nullopt;
  }
  RTC_DCHECK_LT(FrontIndex(), buffer_.size());
  return buffer_[FrontIndex()];
}

bool SaturationProtectorState::operator==(
    const SaturationProtectorState& b) const {
  return margin_db == b.margin_db && peak_delay_buffer == b.peak_delay_buffer &&
         max_peaks_dbfs == b.max_peaks_dbfs &&
         time_since_push_ms == b.time_since_push_ms;
}

void ResetSaturationProtectorState(float initial_margin_db,
                                   SaturationProtectorState& state) {
  state.margin_db = initial_margin_db;
  state.peak_delay_buffer.Reset();
  state.max_peaks_dbfs = kMinLevelDbfs;
  state.time_since_push_ms = 0;
}

void UpdateSaturationProtectorState(float speech_peak_dbfs,
                                    float speech_level_dbfs,
                                    SaturationProtectorState& state) {
  // Get the max peak over `kPeakEnveloperSuperFrameLengthMs` ms.
  state.max_peaks_dbfs = std::max(state.max_peaks_dbfs, speech_peak_dbfs);
  state.time_since_push_ms += kFrameDurationMs;
  if (rtc::SafeGt(state.time_since_push_ms, kPeakEnveloperSuperFrameLengthMs)) {
    // Push `max_peaks_dbfs` back into the ring buffer.
    state.peak_delay_buffer.PushBack(state.max_peaks_dbfs);
    // Reset.
    state.max_peaks_dbfs = kMinLevelDbfs;
    state.time_since_push_ms = 0;
  }

  // Update margin by comparing the estimated speech level and the delayed max
  // speech peak power.
  // TODO(alessiob): Check with aleloi@ why we use a delay and how to tune it.
  const float delayed_peak_dbfs =
      state.peak_delay_buffer.Front().value_or(state.max_peaks_dbfs);
  const float difference_db = delayed_peak_dbfs - speech_level_dbfs;
  if (difference_db > state.margin_db) {
    // Attack.
    state.margin_db =
        state.margin_db * kSaturationProtectorAttackConstant +
        difference_db * (1.f - kSaturationProtectorAttackConstant);
  } else {
    // Decay.
    state.margin_db = state.margin_db * kSaturationProtectorDecayConstant +
                      difference_db * (1.f - kSaturationProtectorDecayConstant);
  }

  state.margin_db =
      rtc::SafeClamp<float>(state.margin_db, kMinMarginDb, kMaxMarginDb);
}

}  // namespace webrtc
