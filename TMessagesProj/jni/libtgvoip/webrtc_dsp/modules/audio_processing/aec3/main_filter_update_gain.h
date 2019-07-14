/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AEC3_MAIN_FILTER_UPDATE_GAIN_H_
#define MODULES_AUDIO_PROCESSING_AEC3_MAIN_FILTER_UPDATE_GAIN_H_

#include <stddef.h>
#include <array>
#include <memory>

#include "api/audio/echo_canceller3_config.h"
#include "modules/audio_processing/aec3/aec3_common.h"
#include "rtc_base/constructormagic.h"

namespace webrtc {

class AdaptiveFirFilter;
class ApmDataDumper;
struct EchoPathVariability;
struct FftData;
class RenderSignalAnalyzer;
struct SubtractorOutput;

// Provides functionality for  computing the adaptive gain for the main filter.
class MainFilterUpdateGain {
 public:
  explicit MainFilterUpdateGain(
      const EchoCanceller3Config::Filter::MainConfiguration& config,
      size_t config_change_duration_blocks);
  ~MainFilterUpdateGain();

  // Takes action in the case of a known echo path change.
  void HandleEchoPathChange(const EchoPathVariability& echo_path_variability);

  // Computes the gain.
  void Compute(const std::array<float, kFftLengthBy2Plus1>& render_power,
               const RenderSignalAnalyzer& render_signal_analyzer,
               const SubtractorOutput& subtractor_output,
               const AdaptiveFirFilter& filter,
               bool saturated_capture_signal,
               FftData* gain_fft);

  // Sets a new config.
  void SetConfig(const EchoCanceller3Config::Filter::MainConfiguration& config,
                 bool immediate_effect) {
    if (immediate_effect) {
      old_target_config_ = current_config_ = target_config_ = config;
      config_change_counter_ = 0;
    } else {
      old_target_config_ = current_config_;
      target_config_ = config;
      config_change_counter_ = config_change_duration_blocks_;
    }
  }

 private:
  static int instance_count_;
  std::unique_ptr<ApmDataDumper> data_dumper_;
  const int config_change_duration_blocks_;
  float one_by_config_change_duration_blocks_;
  EchoCanceller3Config::Filter::MainConfiguration current_config_;
  EchoCanceller3Config::Filter::MainConfiguration target_config_;
  EchoCanceller3Config::Filter::MainConfiguration old_target_config_;
  std::array<float, kFftLengthBy2Plus1> H_error_;
  size_t poor_excitation_counter_;
  size_t call_counter_ = 0;
  int config_change_counter_ = 0;

  // Updates the current config towards the target config.
  void UpdateCurrentConfig();

  RTC_DISALLOW_COPY_AND_ASSIGN(MainFilterUpdateGain);
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AEC3_MAIN_FILTER_UPDATE_GAIN_H_
