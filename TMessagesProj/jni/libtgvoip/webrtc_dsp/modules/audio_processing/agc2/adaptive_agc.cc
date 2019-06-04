/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc2/adaptive_agc.h"

#include "common_audio/include/audio_util.h"
#include "modules/audio_processing/agc2/vad_with_level.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/checks.h"

namespace webrtc {

AdaptiveAgc::AdaptiveAgc(ApmDataDumper* apm_data_dumper)
    : speech_level_estimator_(apm_data_dumper),
      gain_applier_(apm_data_dumper),
      apm_data_dumper_(apm_data_dumper),
      noise_level_estimator_(apm_data_dumper) {
  RTC_DCHECK(apm_data_dumper);
}

AdaptiveAgc::AdaptiveAgc(ApmDataDumper* apm_data_dumper,
                         const AudioProcessing::Config::GainController2& config)
    : speech_level_estimator_(
          apm_data_dumper,
          config.adaptive_digital.level_estimator,
          config.adaptive_digital.use_saturation_protector,
          config.adaptive_digital.extra_saturation_margin_db),
      gain_applier_(apm_data_dumper),
      apm_data_dumper_(apm_data_dumper),
      noise_level_estimator_(apm_data_dumper) {
  RTC_DCHECK(apm_data_dumper);
}

AdaptiveAgc::~AdaptiveAgc() = default;

void AdaptiveAgc::Process(AudioFrameView<float> float_frame,
                          float last_audio_level) {
  auto signal_with_levels = SignalWithLevels(float_frame);
  signal_with_levels.vad_result = vad_.AnalyzeFrame(float_frame);
  apm_data_dumper_->DumpRaw("agc2_vad_probability",
                            signal_with_levels.vad_result.speech_probability);
  apm_data_dumper_->DumpRaw("agc2_vad_rms_dbfs",
                            signal_with_levels.vad_result.speech_rms_dbfs);
  apm_data_dumper_->DumpRaw("agc2_vad_peak_dbfs",
                            signal_with_levels.vad_result.speech_peak_dbfs);

  speech_level_estimator_.UpdateEstimation(signal_with_levels.vad_result);

  signal_with_levels.input_level_dbfs =
      speech_level_estimator_.LatestLevelEstimate();

  signal_with_levels.input_noise_level_dbfs =
      noise_level_estimator_.Analyze(float_frame);

  apm_data_dumper_->DumpRaw("agc2_noise_estimate_dbfs",
                            signal_with_levels.input_noise_level_dbfs);

  signal_with_levels.limiter_audio_level_dbfs =
      last_audio_level > 0 ? FloatS16ToDbfs(last_audio_level) : -90.f;
  apm_data_dumper_->DumpRaw("agc2_last_limiter_audio_level",
                            signal_with_levels.limiter_audio_level_dbfs);

  signal_with_levels.estimate_is_confident =
      speech_level_estimator_.LevelEstimationIsConfident();

  // The gain applier applies the gain.
  gain_applier_.Process(signal_with_levels);
}

void AdaptiveAgc::Reset() {
  speech_level_estimator_.Reset();
}

}  // namespace webrtc
