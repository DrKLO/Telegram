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

#include <memory>
#include <utility>

#include "common_audio/include/audio_util.h"
#include "modules/audio_processing/agc2/cpu_features.h"
#include "modules/audio_processing/audio_buffer.h"
#include "modules/audio_processing/include/audio_frame_view.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/strings/string_builder.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {
namespace {

using Agc2Config = AudioProcessing::Config::GainController2;

constexpr int kLogLimiterStatsPeriodMs = 30'000;
constexpr int kFrameLengthMs = 10;
constexpr int kLogLimiterStatsPeriodNumFrames =
    kLogLimiterStatsPeriodMs / kFrameLengthMs;

// Detects the available CPU features and applies any kill-switches.
AvailableCpuFeatures GetAllowedCpuFeatures() {
  AvailableCpuFeatures features = GetAvailableCpuFeatures();
  if (field_trial::IsEnabled("WebRTC-Agc2SimdSse2KillSwitch")) {
    features.sse2 = false;
  }
  if (field_trial::IsEnabled("WebRTC-Agc2SimdAvx2KillSwitch")) {
    features.avx2 = false;
  }
  if (field_trial::IsEnabled("WebRTC-Agc2SimdNeonKillSwitch")) {
    features.neon = false;
  }
  return features;
}

// Creates an adaptive digital gain controller if enabled.
std::unique_ptr<AdaptiveDigitalGainController> CreateAdaptiveDigitalController(
    const Agc2Config::AdaptiveDigital& config,
    int sample_rate_hz,
    int num_channels,
    ApmDataDumper* data_dumper) {
  if (config.enabled) {
    return std::make_unique<AdaptiveDigitalGainController>(
        data_dumper, config, sample_rate_hz, num_channels);
  }
  return nullptr;
}

}  // namespace

std::atomic<int> GainController2::instance_count_(0);

GainController2::GainController2(const Agc2Config& config,
                                 int sample_rate_hz,
                                 int num_channels,
                                 bool use_internal_vad)
    : cpu_features_(GetAllowedCpuFeatures()),
      data_dumper_(instance_count_.fetch_add(1) + 1),
      fixed_gain_applier_(
          /*hard_clip_samples=*/false,
          /*initial_gain_factor=*/DbToRatio(config.fixed_digital.gain_db)),
      adaptive_digital_controller_(
          CreateAdaptiveDigitalController(config.adaptive_digital,
                                          sample_rate_hz,
                                          num_channels,
                                          &data_dumper_)),
      limiter_(sample_rate_hz, &data_dumper_, /*histogram_name_prefix=*/"Agc2"),
      calls_since_last_limiter_log_(0) {
  RTC_DCHECK(Validate(config));
  data_dumper_.InitiateNewSetOfRecordings();
  const bool use_vad = config.adaptive_digital.enabled;
  if (use_vad && use_internal_vad) {
    // TODO(bugs.webrtc.org/7494): Move `vad_reset_period_ms` from adaptive
    // digital to gain controller 2 config.
    vad_ = std::make_unique<VoiceActivityDetectorWrapper>(
        config.adaptive_digital.vad_reset_period_ms, cpu_features_,
        sample_rate_hz);
  }
}

GainController2::~GainController2() = default;

void GainController2::SetFixedGainDb(float gain_db) {
  const float gain_factor = DbToRatio(gain_db);
  if (fixed_gain_applier_.GetGainFactor() != gain_factor) {
    // Reset the limiter to quickly react on abrupt level changes caused by
    // large changes of the fixed gain.
    limiter_.Reset();
  }
  fixed_gain_applier_.SetGainFactor(gain_factor);
}

void GainController2::Process(absl::optional<float> speech_probability,
                              bool input_volume_changed,
                              AudioBuffer* audio) {
  data_dumper_.DumpRaw("agc2_applied_input_volume_changed",
                       input_volume_changed);
  if (input_volume_changed && !!adaptive_digital_controller_) {
    adaptive_digital_controller_->HandleInputGainChange();
  }

  AudioFrameView<float> float_frame(audio->channels(), audio->num_channels(),
                                    audio->num_frames());
  if (vad_) {
    speech_probability = vad_->Analyze(float_frame);
  } else if (speech_probability.has_value()) {
    RTC_DCHECK_GE(speech_probability.value(), 0.0f);
    RTC_DCHECK_LE(speech_probability.value(), 1.0f);
  }
  if (speech_probability.has_value()) {
    data_dumper_.DumpRaw("agc2_speech_probability", speech_probability.value());
  }
  fixed_gain_applier_.ApplyGain(float_frame);
  if (adaptive_digital_controller_) {
    RTC_DCHECK(speech_probability.has_value());
    adaptive_digital_controller_->Process(
        float_frame, speech_probability.value(), limiter_.LastAudioLevel());
  }
  limiter_.Process(float_frame);

  // Periodically log limiter stats.
  if (++calls_since_last_limiter_log_ == kLogLimiterStatsPeriodNumFrames) {
    calls_since_last_limiter_log_ = 0;
    InterpolatedGainCurve::Stats stats = limiter_.GetGainCurveStats();
    RTC_LOG(LS_INFO) << "AGC2 limiter stats"
                     << " | identity: " << stats.look_ups_identity_region
                     << " | knee: " << stats.look_ups_knee_region
                     << " | limiter: " << stats.look_ups_limiter_region
                     << " | saturation: " << stats.look_ups_saturation_region;
  }
}

bool GainController2::Validate(
    const AudioProcessing::Config::GainController2& config) {
  const auto& fixed = config.fixed_digital;
  const auto& adaptive = config.adaptive_digital;
  return fixed.gain_db >= 0.0f && fixed.gain_db < 50.f &&
         adaptive.headroom_db >= 0.0f && adaptive.max_gain_db > 0.0f &&
         adaptive.initial_gain_db >= 0.0f &&
         adaptive.max_gain_change_db_per_second > 0.0f &&
         adaptive.max_output_noise_level_dbfs <= 0.0f;
}

}  // namespace webrtc
