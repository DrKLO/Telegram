/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/suppression_gain_limiter.h"

#include <math.h>
#include <algorithm>

#include "modules/audio_processing/aec3/aec3_common.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace {

// Computes the gain rampup factor to use.
float ComputeGainRampupIncrease(
    const EchoCanceller3Config::EchoRemovalControl::GainRampup& rampup_config) {
  return powf(1.f / rampup_config.first_non_zero_gain,
              1.f / rampup_config.non_zero_gain_blocks);
}

}  // namespace

SuppressionGainUpperLimiter::SuppressionGainUpperLimiter(
    const EchoCanceller3Config& config)
    : rampup_config_(config.echo_removal_control.gain_rampup),
      gain_rampup_increase_(ComputeGainRampupIncrease(rampup_config_)) {
  Reset();
}

void SuppressionGainUpperLimiter::Reset() {
  recent_reset_ = true;
}

void SuppressionGainUpperLimiter::Update(bool render_activity,
                                         bool transparent_mode) {
  if (transparent_mode) {
    active_render_seen_ = true;
    call_startup_phase_ = false;
    recent_reset_ = false;
    suppressor_gain_limit_ = 1.f;
    return;
  }

  if (recent_reset_ && !call_startup_phase_) {
    // Only enforce 250 ms full suppression after in-call resets,
    constexpr int kMuteFramesAfterReset = kNumBlocksPerSecond / 4;
    realignment_counter_ = kMuteFramesAfterReset;
  } else if (!active_render_seen_ && render_activity) {
    // Enforce a tailormade suppression limiting during call startup.
    active_render_seen_ = true;
    realignment_counter_ = rampup_config_.full_gain_blocks;
  } else if (realignment_counter_ > 0) {
    if (--realignment_counter_ == 0) {
      call_startup_phase_ = false;
    }
  }
  recent_reset_ = false;

  // Do not enforce any gain limit on the suppressor.
  if (!IsActive()) {
    suppressor_gain_limit_ = 1.f;
    return;
  }

  // Enforce full suppression.
  if (realignment_counter_ > rampup_config_.non_zero_gain_blocks ||
      (!call_startup_phase_ && realignment_counter_ > 0)) {
    suppressor_gain_limit_ = rampup_config_.initial_gain;
    return;
  }

  // Start increasing the gain limit.
  if (realignment_counter_ == rampup_config_.non_zero_gain_blocks) {
    suppressor_gain_limit_ = rampup_config_.first_non_zero_gain;
    return;
  }

  // Increase the gain limit until it reaches 1.f.
  RTC_DCHECK_LT(0.f, suppressor_gain_limit_);
  suppressor_gain_limit_ =
      std::min(1.f, suppressor_gain_limit_ * gain_rampup_increase_);
  RTC_DCHECK_GE(1.f, suppressor_gain_limit_);
}

}  // namespace webrtc
