/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AEC3_SUPPRESSION_GAIN_LIMITER_H_
#define MODULES_AUDIO_PROCESSING_AEC3_SUPPRESSION_GAIN_LIMITER_H_

#include "api/array_view.h"
#include "api/audio/echo_canceller3_config.h"
#include "rtc_base/constructormagic.h"

namespace webrtc {

// Class for applying a smoothly increasing limit for the suppression gain
// during call startup and after in-call resets.
class SuppressionGainUpperLimiter {
 public:
  explicit SuppressionGainUpperLimiter(const EchoCanceller3Config& config);

  // Reset the limiting behavior.
  void Reset();

  // Updates the limiting behavior for the current capture bloc.
  void Update(bool render_activity, bool transparent_mode);

  // Returns the current suppressor gain limit.
  float Limit() const { return suppressor_gain_limit_; }

  // Return whether the suppressor gain limit is active.
  bool IsActive() const { return (realignment_counter_ > 0); }

  // Inactivate limiter.
  void Deactivate() {
    realignment_counter_ = 0;
    suppressor_gain_limit_ = 1.f;
  }

 private:
  const EchoCanceller3Config::EchoRemovalControl::GainRampup rampup_config_;
  const float gain_rampup_increase_;
  bool call_startup_phase_ = true;
  int realignment_counter_ = 0;
  bool active_render_seen_ = false;
  float suppressor_gain_limit_ = 1.f;
  bool recent_reset_ = false;

  RTC_DISALLOW_IMPLICIT_CONSTRUCTORS(SuppressionGainUpperLimiter);
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AEC3_SUPPRESSION_GAIN_LIMITER_H_
