/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/gain_control_for_experimental_agc.h"

#include "modules/audio_processing/include/audio_processing.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/atomicops.h"
#include "rtc_base/criticalsection.h"

namespace webrtc {

int GainControlForExperimentalAgc::instance_counter_ = 0;

GainControlForExperimentalAgc::GainControlForExperimentalAgc(
    GainControl* gain_control,
    rtc::CriticalSection* crit_capture)
    : data_dumper_(
          new ApmDataDumper(rtc::AtomicOps::Increment(&instance_counter_))),
      real_gain_control_(gain_control),
      volume_(0),
      crit_capture_(crit_capture) {}

GainControlForExperimentalAgc::~GainControlForExperimentalAgc() = default;

int GainControlForExperimentalAgc::Enable(bool enable) {
  return real_gain_control_->Enable(enable);
}

bool GainControlForExperimentalAgc::is_enabled() const {
  return real_gain_control_->is_enabled();
}

int GainControlForExperimentalAgc::set_stream_analog_level(int level) {
  rtc::CritScope cs_capture(crit_capture_);
  data_dumper_->DumpRaw("experimental_gain_control_set_stream_analog_level", 1,
                        &level);
  do_log_level_ = true;
  volume_ = level;
  return AudioProcessing::kNoError;
}

int GainControlForExperimentalAgc::stream_analog_level() {
  rtc::CritScope cs_capture(crit_capture_);
  if (do_log_level_) {
    data_dumper_->DumpRaw("experimental_gain_control_stream_analog_level", 1,
                          &volume_);
    do_log_level_ = false;
  }
  return volume_;
}

int GainControlForExperimentalAgc::set_mode(Mode mode) {
  return AudioProcessing::kNoError;
}

GainControl::Mode GainControlForExperimentalAgc::mode() const {
  return GainControl::kAdaptiveAnalog;
}

int GainControlForExperimentalAgc::set_target_level_dbfs(int level) {
  return AudioProcessing::kNoError;
}

int GainControlForExperimentalAgc::target_level_dbfs() const {
  return real_gain_control_->target_level_dbfs();
}

int GainControlForExperimentalAgc::set_compression_gain_db(int gain) {
  return AudioProcessing::kNoError;
}

int GainControlForExperimentalAgc::compression_gain_db() const {
  return real_gain_control_->compression_gain_db();
}

int GainControlForExperimentalAgc::enable_limiter(bool enable) {
  return AudioProcessing::kNoError;
}

bool GainControlForExperimentalAgc::is_limiter_enabled() const {
  return real_gain_control_->is_limiter_enabled();
}

int GainControlForExperimentalAgc::set_analog_level_limits(int minimum,
                                                           int maximum) {
  return AudioProcessing::kNoError;
}

int GainControlForExperimentalAgc::analog_level_minimum() const {
  return real_gain_control_->analog_level_minimum();
}

int GainControlForExperimentalAgc::analog_level_maximum() const {
  return real_gain_control_->analog_level_maximum();
}

bool GainControlForExperimentalAgc::stream_is_saturated() const {
  return real_gain_control_->stream_is_saturated();
}

void GainControlForExperimentalAgc::SetMicVolume(int volume) {
  rtc::CritScope cs_capture(crit_capture_);
  volume_ = volume;
}

int GainControlForExperimentalAgc::GetMicVolume() {
  rtc::CritScope cs_capture(crit_capture_);
  return volume_;
}

void GainControlForExperimentalAgc::Initialize() {
  data_dumper_->InitiateNewSetOfRecordings();
}

}  // namespace webrtc
