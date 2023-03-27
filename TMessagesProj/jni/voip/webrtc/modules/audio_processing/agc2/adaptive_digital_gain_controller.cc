/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc2/adaptive_digital_gain_controller.h"

#include <algorithm>

#include "common_audio/include/audio_util.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace {

// Peak and RMS audio levels in dBFS.
struct AudioLevels {
  float peak_dbfs;
  float rms_dbfs;
};

// Computes the audio levels for the first channel in `frame`.
AudioLevels ComputeAudioLevels(AudioFrameView<float> frame) {
  float peak = 0.0f;
  float rms = 0.0f;
  for (const auto& x : frame.channel(0)) {
    peak = std::max(std::fabs(x), peak);
    rms += x * x;
  }
  return {FloatS16ToDbfs(peak),
          FloatS16ToDbfs(std::sqrt(rms / frame.samples_per_channel()))};
}

}  // namespace

AdaptiveDigitalGainController::AdaptiveDigitalGainController(
    ApmDataDumper* apm_data_dumper,
    const AudioProcessing::Config::GainController2::AdaptiveDigital& config,
    int sample_rate_hz,
    int num_channels)
    : speech_level_estimator_(apm_data_dumper, config),
      gain_controller_(apm_data_dumper, config, sample_rate_hz, num_channels),
      apm_data_dumper_(apm_data_dumper),
      noise_level_estimator_(CreateNoiseFloorEstimator(apm_data_dumper)),
      saturation_protector_(
          CreateSaturationProtector(kSaturationProtectorInitialHeadroomDb,
                                    config.adjacent_speech_frames_threshold,
                                    apm_data_dumper)) {
  RTC_DCHECK(apm_data_dumper);
  RTC_DCHECK(noise_level_estimator_);
  RTC_DCHECK(saturation_protector_);
}

AdaptiveDigitalGainController::~AdaptiveDigitalGainController() = default;

void AdaptiveDigitalGainController::Initialize(int sample_rate_hz,
                                               int num_channels) {
  gain_controller_.Initialize(sample_rate_hz, num_channels);
}

void AdaptiveDigitalGainController::Process(AudioFrameView<float> frame,
                                            float speech_probability,
                                            float limiter_envelope) {
  AudioLevels levels = ComputeAudioLevels(frame);
  apm_data_dumper_->DumpRaw("agc2_input_rms_dbfs", levels.rms_dbfs);
  apm_data_dumper_->DumpRaw("agc2_input_peak_dbfs", levels.peak_dbfs);

  AdaptiveDigitalGainApplier::FrameInfo info;

  info.speech_probability = speech_probability;

  speech_level_estimator_.Update(levels.rms_dbfs, levels.peak_dbfs,
                                 info.speech_probability);
  info.speech_level_dbfs = speech_level_estimator_.level_dbfs();
  info.speech_level_reliable = speech_level_estimator_.IsConfident();
  apm_data_dumper_->DumpRaw("agc2_speech_level_dbfs", info.speech_level_dbfs);
  apm_data_dumper_->DumpRaw("agc2_speech_level_reliable",
                            info.speech_level_reliable);

  info.noise_rms_dbfs = noise_level_estimator_->Analyze(frame);
  apm_data_dumper_->DumpRaw("agc2_noise_rms_dbfs", info.noise_rms_dbfs);

  saturation_protector_->Analyze(info.speech_probability, levels.peak_dbfs,
                                 info.speech_level_dbfs);
  info.headroom_db = saturation_protector_->HeadroomDb();
  apm_data_dumper_->DumpRaw("agc2_headroom_db", info.headroom_db);

  info.limiter_envelope_dbfs = FloatS16ToDbfs(limiter_envelope);
  apm_data_dumper_->DumpRaw("agc2_limiter_envelope_dbfs",
                            info.limiter_envelope_dbfs);

  gain_controller_.Process(info, frame);
}

void AdaptiveDigitalGainController::HandleInputGainChange() {
  speech_level_estimator_.Reset();
  saturation_protector_->Reset();
}

absl::optional<float>
AdaptiveDigitalGainController::GetSpeechLevelDbfsIfConfident() const {
  return speech_level_estimator_.IsConfident()
             ? absl::optional<float>(speech_level_estimator_.level_dbfs())
             : absl::nullopt;
}

}  // namespace webrtc
