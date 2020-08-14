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

#include <algorithm>
#include <iterator>

#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/numerics/safe_minmax.h"

namespace webrtc {

namespace {
void ShiftBuffer(std::array<float, kPeakEnveloperBufferSize>* buffer_) {
  // Move everything one element back.
  std::copy(buffer_->begin() + 1, buffer_->end(), buffer_->begin());
}
}  // namespace

SaturationProtector::PeakEnveloper::PeakEnveloper() = default;

void SaturationProtector::PeakEnveloper::Process(float frame_peak_dbfs) {
  // Update the delayed buffer and the current superframe peak.
  current_superframe_peak_dbfs_ =
      std::max(current_superframe_peak_dbfs_, frame_peak_dbfs);
  speech_time_in_estimate_ms_ += kFrameDurationMs;
  if (speech_time_in_estimate_ms_ > kPeakEnveloperSuperFrameLengthMs) {
    speech_time_in_estimate_ms_ = 0;
    const bool buffer_full = elements_in_buffer_ == kPeakEnveloperBufferSize;
    if (buffer_full) {
      ShiftBuffer(&peak_delay_buffer_);
      *peak_delay_buffer_.rbegin() = current_superframe_peak_dbfs_;
    } else {
      peak_delay_buffer_[elements_in_buffer_] = current_superframe_peak_dbfs_;
      elements_in_buffer_++;
    }
    current_superframe_peak_dbfs_ = -90.f;
  }
}

float SaturationProtector::PeakEnveloper::Query() const {
  float result;
  if (elements_in_buffer_ > 0) {
    result = peak_delay_buffer_[0];
  } else {
    result = current_superframe_peak_dbfs_;
  }
  return result;
}

SaturationProtector::SaturationProtector(ApmDataDumper* apm_data_dumper)
    : SaturationProtector(apm_data_dumper, GetExtraSaturationMarginOffsetDb()) {
}

SaturationProtector::SaturationProtector(ApmDataDumper* apm_data_dumper,
                                         float extra_saturation_margin_db)
    : apm_data_dumper_(apm_data_dumper),
      last_margin_(GetInitialSaturationMarginDb()),
      extra_saturation_margin_db_(extra_saturation_margin_db) {}

void SaturationProtector::UpdateMargin(
    const VadWithLevel::LevelAndProbability& vad_data,
    float last_speech_level_estimate) {
  peak_enveloper_.Process(vad_data.speech_peak_dbfs);
  const float delayed_peak_dbfs = peak_enveloper_.Query();
  const float difference_db = delayed_peak_dbfs - last_speech_level_estimate;

  if (last_margin_ < difference_db) {
    last_margin_ = last_margin_ * kSaturationProtectorAttackConstant +
                   difference_db * (1.f - kSaturationProtectorAttackConstant);
  } else {
    last_margin_ = last_margin_ * kSaturationProtectorDecayConstant +
                   difference_db * (1.f - kSaturationProtectorDecayConstant);
  }

  last_margin_ = rtc::SafeClamp<float>(last_margin_, 12.f, 25.f);
}

float SaturationProtector::LastMargin() const {
  return last_margin_ + extra_saturation_margin_db_;
}

void SaturationProtector::Reset() {
  peak_enveloper_ = PeakEnveloper();
}

void SaturationProtector::DebugDumpEstimate() const {
  if (apm_data_dumper_) {
    apm_data_dumper_->DumpRaw(
        "agc2_adaptive_saturation_protector_delayed_peak_dbfs",
        peak_enveloper_.Query());
    apm_data_dumper_->DumpRaw("agc2_adaptive_saturation_margin_db",
                              last_margin_);
  }
}

}  // namespace webrtc
