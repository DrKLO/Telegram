/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/gain_controller2.h"

#include "common_audio/include/audio_util.h"
#include "modules/audio_processing/audio_buffer.h"
#include "modules/audio_processing/include/audio_frame_view.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/atomic_ops.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/strings/string_builder.h"

namespace webrtc {

int GainController2::instance_count_ = 0;

GainController2::GainController2()
    : data_dumper_(rtc::AtomicOps::Increment(&instance_count_)),
      gain_applier_(/*hard_clip_samples=*/false,
                    /*initial_gain_factor=*/0.0f),
      limiter_(static_cast<size_t>(48000), &data_dumper_, "Agc2"),
      calls_since_last_limiter_log_(0) {
  if (config_.adaptive_digital.enabled) {
    adaptive_agc_ =
        std::make_unique<AdaptiveAgc>(&data_dumper_, config_.adaptive_digital);
  }
}

GainController2::~GainController2() = default;

void GainController2::Initialize(int sample_rate_hz, int num_channels) {
  RTC_DCHECK(sample_rate_hz == AudioProcessing::kSampleRate8kHz ||
             sample_rate_hz == AudioProcessing::kSampleRate16kHz ||
             sample_rate_hz == AudioProcessing::kSampleRate32kHz ||
             sample_rate_hz == AudioProcessing::kSampleRate48kHz);
  limiter_.SetSampleRate(sample_rate_hz);
  if (adaptive_agc_) {
    adaptive_agc_->Initialize(sample_rate_hz, num_channels);
  }
  data_dumper_.InitiateNewSetOfRecordings();
  data_dumper_.DumpRaw("sample_rate_hz", sample_rate_hz);
  calls_since_last_limiter_log_ = 0;
}

void GainController2::Process(AudioBuffer* audio) {
  data_dumper_.DumpRaw("agc2_notified_analog_level", analog_level_);
  AudioFrameView<float> float_frame(audio->channels(), audio->num_channels(),
                                    audio->num_frames());
  // Apply fixed gain first, then the adaptive one.
  gain_applier_.ApplyGain(float_frame);
  if (adaptive_agc_) {
    adaptive_agc_->Process(float_frame, limiter_.LastAudioLevel());
  }
  limiter_.Process(float_frame);

  // Log limiter stats every 30 seconds.
  ++calls_since_last_limiter_log_;
  if (calls_since_last_limiter_log_ == 3000) {
    calls_since_last_limiter_log_ = 0;
    InterpolatedGainCurve::Stats stats = limiter_.GetGainCurveStats();
    RTC_LOG(LS_INFO) << "AGC2 limiter stats"
                     << " | identity: " << stats.look_ups_identity_region
                     << " | knee: " << stats.look_ups_knee_region
                     << " | limiter: " << stats.look_ups_limiter_region
                     << " | saturation: " << stats.look_ups_saturation_region;
  }
}

void GainController2::NotifyAnalogLevel(int level) {
  if (analog_level_ != level && adaptive_agc_) {
    adaptive_agc_->HandleInputGainChange();
  }
  analog_level_ = level;
}

void GainController2::ApplyConfig(
    const AudioProcessing::Config::GainController2& config) {
  RTC_DCHECK(Validate(config));

  config_ = config;
  if (config.fixed_digital.gain_db != config_.fixed_digital.gain_db) {
    // Reset the limiter to quickly react on abrupt level changes caused by
    // large changes of the fixed gain.
    limiter_.Reset();
  }
  gain_applier_.SetGainFactor(DbToRatio(config_.fixed_digital.gain_db));
  if (config_.adaptive_digital.enabled) {
    adaptive_agc_ =
        std::make_unique<AdaptiveAgc>(&data_dumper_, config_.adaptive_digital);
  } else {
    adaptive_agc_.reset();
  }
}

bool GainController2::Validate(
    const AudioProcessing::Config::GainController2& config) {
  const auto& fixed = config.fixed_digital;
  const auto& adaptive = config.adaptive_digital;
  return fixed.gain_db >= 0.f && fixed.gain_db < 50.f &&
         adaptive.vad_probability_attack > 0.f &&
         adaptive.vad_probability_attack <= 1.f &&
         adaptive.level_estimator_adjacent_speech_frames_threshold >= 1 &&
         adaptive.initial_saturation_margin_db >= 0.f &&
         adaptive.initial_saturation_margin_db <= 100.f &&
         adaptive.extra_saturation_margin_db >= 0.f &&
         adaptive.extra_saturation_margin_db <= 100.f &&
         adaptive.gain_applier_adjacent_speech_frames_threshold >= 1 &&
         adaptive.max_gain_change_db_per_second > 0.f &&
         adaptive.max_output_noise_level_dbfs <= 0.f;
}

}  // namespace webrtc
