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
#include "modules/audio_processing/agc2/agc2_common.h"
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
using InputVolumeControllerConfig = InputVolumeController::Config;

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

// Peak and RMS audio levels in dBFS.
struct AudioLevels {
  float peak_dbfs;
  float rms_dbfs;
};

// Speech level info.
struct SpeechLevel {
  bool is_confident;
  float rms_dbfs;
};

// Computes the audio levels for the first channel in `frame`.
AudioLevels ComputeAudioLevels(AudioFrameView<float> frame,
                               ApmDataDumper& data_dumper) {
  float peak = 0.0f;
  float rms = 0.0f;
  for (const auto& x : frame.channel(0)) {
    peak = std::max(std::fabs(x), peak);
    rms += x * x;
  }
  AudioLevels levels{
      FloatS16ToDbfs(peak),
      FloatS16ToDbfs(std::sqrt(rms / frame.samples_per_channel()))};
  data_dumper.DumpRaw("agc2_input_rms_dbfs", levels.rms_dbfs);
  data_dumper.DumpRaw("agc2_input_peak_dbfs", levels.peak_dbfs);
  return levels;
}

}  // namespace

std::atomic<int> GainController2::instance_count_(0);

GainController2::GainController2(
    const Agc2Config& config,
    const InputVolumeControllerConfig& input_volume_controller_config,
    int sample_rate_hz,
    int num_channels,
    bool use_internal_vad)
    : cpu_features_(GetAllowedCpuFeatures()),
      data_dumper_(instance_count_.fetch_add(1) + 1),
      fixed_gain_applier_(
          /*hard_clip_samples=*/false,
          /*initial_gain_factor=*/DbToRatio(config.fixed_digital.gain_db)),
      limiter_(sample_rate_hz, &data_dumper_, /*histogram_name_prefix=*/"Agc2"),
      calls_since_last_limiter_log_(0) {
  RTC_DCHECK(Validate(config));
  data_dumper_.InitiateNewSetOfRecordings();

  if (config.input_volume_controller.enabled ||
      config.adaptive_digital.enabled) {
    // Create dependencies.
    speech_level_estimator_ = std::make_unique<SpeechLevelEstimator>(
        &data_dumper_, config.adaptive_digital, kAdjacentSpeechFramesThreshold);
    if (use_internal_vad)
      vad_ = std::make_unique<VoiceActivityDetectorWrapper>(
          kVadResetPeriodMs, cpu_features_, sample_rate_hz);
  }

  if (config.input_volume_controller.enabled) {
    // Create controller.
    input_volume_controller_ = std::make_unique<InputVolumeController>(
        num_channels, input_volume_controller_config);
    // TODO(bugs.webrtc.org/7494): Call `Initialize` in ctor and remove method.
    input_volume_controller_->Initialize();
  }

  if (config.adaptive_digital.enabled) {
    // Create dependencies.
    noise_level_estimator_ = CreateNoiseFloorEstimator(&data_dumper_);
    saturation_protector_ = CreateSaturationProtector(
        kSaturationProtectorInitialHeadroomDb, kAdjacentSpeechFramesThreshold,
        &data_dumper_);
    // Create controller.
    adaptive_digital_controller_ =
        std::make_unique<AdaptiveDigitalGainController>(
            &data_dumper_, config.adaptive_digital,
            kAdjacentSpeechFramesThreshold);
  }
}

GainController2::~GainController2() = default;

// TODO(webrtc:7494): Pass the flag also to the other components.
void GainController2::SetCaptureOutputUsed(bool capture_output_used) {
  if (input_volume_controller_) {
    input_volume_controller_->HandleCaptureOutputUsedChange(
        capture_output_used);
  }
}

void GainController2::SetFixedGainDb(float gain_db) {
  const float gain_factor = DbToRatio(gain_db);
  if (fixed_gain_applier_.GetGainFactor() != gain_factor) {
    // Reset the limiter to quickly react on abrupt level changes caused by
    // large changes of the fixed gain.
    limiter_.Reset();
  }
  fixed_gain_applier_.SetGainFactor(gain_factor);
}

void GainController2::Analyze(int applied_input_volume,
                              const AudioBuffer& audio_buffer) {
  recommended_input_volume_ = absl::nullopt;

  RTC_DCHECK_GE(applied_input_volume, 0);
  RTC_DCHECK_LE(applied_input_volume, 255);

  if (input_volume_controller_) {
    input_volume_controller_->AnalyzeInputAudio(applied_input_volume,
                                                audio_buffer);
  }
}

void GainController2::Process(absl::optional<float> speech_probability,
                              bool input_volume_changed,
                              AudioBuffer* audio) {
  recommended_input_volume_ = absl::nullopt;

  data_dumper_.DumpRaw("agc2_applied_input_volume_changed",
                       input_volume_changed);
  if (input_volume_changed) {
    // Handle input volume changes.
    if (speech_level_estimator_)
      speech_level_estimator_->Reset();
    if (saturation_protector_)
      saturation_protector_->Reset();
  }

  AudioFrameView<float> float_frame(audio->channels(), audio->num_channels(),
                                    audio->num_frames());
  // Compute speech probability.
  if (vad_) {
    // When the VAD component runs, `speech_probability` should not be specified
    // because APM should not run the same VAD twice (as an APM sub-module and
    // internally in AGC2).
    RTC_DCHECK(!speech_probability.has_value());
    speech_probability = vad_->Analyze(float_frame);
  }
  if (speech_probability.has_value()) {
    RTC_DCHECK_GE(*speech_probability, 0.0f);
    RTC_DCHECK_LE(*speech_probability, 1.0f);
  }
  // The speech probability may not be defined at this step (e.g., when the
  // fixed digital controller alone is enabled).
  if (speech_probability.has_value())
    data_dumper_.DumpRaw("agc2_speech_probability", *speech_probability);

  // Compute audio, noise and speech levels.
  AudioLevels audio_levels = ComputeAudioLevels(float_frame, data_dumper_);
  absl::optional<float> noise_rms_dbfs;
  if (noise_level_estimator_) {
    // TODO(bugs.webrtc.org/7494): Pass `audio_levels` to remove duplicated
    // computation in `noise_level_estimator_`.
    noise_rms_dbfs = noise_level_estimator_->Analyze(float_frame);
  }
  absl::optional<SpeechLevel> speech_level;
  if (speech_level_estimator_) {
    RTC_DCHECK(speech_probability.has_value());
    speech_level_estimator_->Update(
        audio_levels.rms_dbfs, audio_levels.peak_dbfs, *speech_probability);
    speech_level =
        SpeechLevel{.is_confident = speech_level_estimator_->is_confident(),
                    .rms_dbfs = speech_level_estimator_->level_dbfs()};
  }

  // Update the recommended input volume.
  if (input_volume_controller_) {
    RTC_DCHECK(speech_level.has_value());
    RTC_DCHECK(speech_probability.has_value());
    if (speech_probability.has_value()) {
      recommended_input_volume_ =
          input_volume_controller_->RecommendInputVolume(
              *speech_probability,
              speech_level->is_confident
                  ? absl::optional<float>(speech_level->rms_dbfs)
                  : absl::nullopt);
    }
  }

  if (adaptive_digital_controller_) {
    RTC_DCHECK(saturation_protector_);
    RTC_DCHECK(speech_probability.has_value());
    RTC_DCHECK(speech_level.has_value());
    saturation_protector_->Analyze(*speech_probability, audio_levels.peak_dbfs,
                                   speech_level->rms_dbfs);
    float headroom_db = saturation_protector_->HeadroomDb();
    data_dumper_.DumpRaw("agc2_headroom_db", headroom_db);
    float limiter_envelope_dbfs = FloatS16ToDbfs(limiter_.LastAudioLevel());
    data_dumper_.DumpRaw("agc2_limiter_envelope_dbfs", limiter_envelope_dbfs);
    RTC_DCHECK(noise_rms_dbfs.has_value());
    adaptive_digital_controller_->Process(
        /*info=*/{.speech_probability = *speech_probability,
                  .speech_level_dbfs = speech_level->rms_dbfs,
                  .speech_level_reliable = speech_level->is_confident,
                  .noise_rms_dbfs = *noise_rms_dbfs,
                  .headroom_db = headroom_db,
                  .limiter_envelope_dbfs = limiter_envelope_dbfs},
        float_frame);
  }

  // TODO(bugs.webrtc.org/7494): Pass `audio_levels` to remove duplicated
  // computation in `limiter_`.
  fixed_gain_applier_.ApplyGain(float_frame);

  limiter_.Process(float_frame);

  // Periodically log limiter stats.
  if (++calls_since_last_limiter_log_ == kLogLimiterStatsPeriodNumFrames) {
    calls_since_last_limiter_log_ = 0;
    InterpolatedGainCurve::Stats stats = limiter_.GetGainCurveStats();
    RTC_LOG(LS_INFO) << "[AGC2] limiter stats"
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
  return fixed.gain_db >= 0.0f && fixed.gain_db < 50.0f &&
         adaptive.headroom_db >= 0.0f && adaptive.max_gain_db > 0.0f &&
         adaptive.initial_gain_db >= 0.0f &&
         adaptive.max_gain_change_db_per_second > 0.0f &&
         adaptive.max_output_noise_level_dbfs <= 0.0f;
}

}  // namespace webrtc
