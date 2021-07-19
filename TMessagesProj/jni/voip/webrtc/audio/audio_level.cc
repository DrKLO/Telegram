/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "audio/audio_level.h"

#include "api/audio/audio_frame.h"
#include "common_audio/signal_processing/include/signal_processing_library.h"

namespace webrtc {
namespace voe {

AudioLevel::AudioLevel()
    : abs_max_(0), count_(0), current_level_full_range_(0) {}

AudioLevel::~AudioLevel() {}

void AudioLevel::Reset() {
  MutexLock lock(&mutex_);
  abs_max_ = 0;
  count_ = 0;
  current_level_full_range_ = 0;
  total_energy_ = 0.0;
  total_duration_ = 0.0;
}

int16_t AudioLevel::LevelFullRange() const {
  MutexLock lock(&mutex_);
  return current_level_full_range_;
}

void AudioLevel::ResetLevelFullRange() {
  MutexLock lock(&mutex_);
  abs_max_ = 0;
  count_ = 0;
  current_level_full_range_ = 0;
}

double AudioLevel::TotalEnergy() const {
  MutexLock lock(&mutex_);
  return total_energy_;
}

double AudioLevel::TotalDuration() const {
  MutexLock lock(&mutex_);
  return total_duration_;
}

void AudioLevel::ComputeLevel(const AudioFrame& audioFrame, double duration) {
  // Check speech level (works for 2 channels as well)
  int16_t abs_value =
      audioFrame.muted()
          ? 0
          : WebRtcSpl_MaxAbsValueW16(
                audioFrame.data(),
                audioFrame.samples_per_channel_ * audioFrame.num_channels_);

  // Protect member access using a lock since this method is called on a
  // dedicated audio thread in the RecordedDataIsAvailable() callback.
  MutexLock lock(&mutex_);

  if (abs_value > abs_max_)
    abs_max_ = abs_value;

  // Update level approximately 9 times per second, assuming audio frame
  // duration is approximately 10 ms. (The update frequency is every
  // 11th (= |kUpdateFrequency+1|) call: 1000/(11*10)=9.09..., we should
  // probably change this behavior, see https://crbug.com/webrtc/10784).
  if (count_++ == kUpdateFrequency) {
    current_level_full_range_ = abs_max_;

    count_ = 0;

    // Decay the absolute maximum (divide by 4)
    abs_max_ >>= 2;
  }

  // See the description for "totalAudioEnergy" in the WebRTC stats spec
  // (https://w3c.github.io/webrtc-stats/#dom-rtcmediastreamtrackstats-totalaudioenergy)
  // for an explanation of these formulas. In short, we need a value that can
  // be used to compute RMS audio levels over different time intervals, by
  // taking the difference between the results from two getStats calls. To do
  // this, the value needs to be of units "squared sample value * time".
  double additional_energy =
      static_cast<double>(current_level_full_range_) / INT16_MAX;
  additional_energy *= additional_energy;
  total_energy_ += additional_energy * duration;
  total_duration_ += duration;
}

}  // namespace voe
}  // namespace webrtc
