/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/level_estimator_impl.h"

#include <stddef.h>
#include <stdint.h>

#include "api/array_view.h"
#include "modules/audio_processing/audio_buffer.h"
#include "modules/audio_processing/rms_level.h"
#include "rtc_base/checks.h"

namespace webrtc {

LevelEstimatorImpl::LevelEstimatorImpl(rtc::CriticalSection* crit)
    : crit_(crit), rms_(new RmsLevel()) {
  RTC_DCHECK(crit);
}

LevelEstimatorImpl::~LevelEstimatorImpl() {}

void LevelEstimatorImpl::Initialize() {
  rtc::CritScope cs(crit_);
  rms_->Reset();
}

void LevelEstimatorImpl::ProcessStream(AudioBuffer* audio) {
  RTC_DCHECK(audio);
  rtc::CritScope cs(crit_);
  if (!enabled_) {
    return;
  }

  for (size_t i = 0; i < audio->num_channels(); i++) {
    rms_->Analyze(rtc::ArrayView<const int16_t>(audio->channels_const()[i],
                                                audio->num_frames()));
  }
}

int LevelEstimatorImpl::Enable(bool enable) {
  rtc::CritScope cs(crit_);
  if (enable && !enabled_) {
    rms_->Reset();
  }
  enabled_ = enable;
  return AudioProcessing::kNoError;
}

bool LevelEstimatorImpl::is_enabled() const {
  rtc::CritScope cs(crit_);
  return enabled_;
}

int LevelEstimatorImpl::RMS() {
  rtc::CritScope cs(crit_);
  if (!enabled_) {
    return AudioProcessing::kNotEnabledError;
  }

  return rms_->Average();
}
}  // namespace webrtc
