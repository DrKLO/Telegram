/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AEC3_SUPPRESSION_GAIN_H_
#define MODULES_AUDIO_PROCESSING_AEC3_SUPPRESSION_GAIN_H_

#include <array>
#include <memory>
#include <vector>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "api/audio/echo_canceller3_config.h"
#include "modules/audio_processing/aec3/aec3_common.h"
#include "modules/audio_processing/aec3/aec_state.h"
#include "modules/audio_processing/aec3/fft_data.h"
#include "modules/audio_processing/aec3/moving_average.h"
#include "modules/audio_processing/aec3/nearend_detector.h"
#include "modules/audio_processing/aec3/render_signal_analyzer.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/constructor_magic.h"

namespace webrtc {

class SuppressionGain {
 public:
  SuppressionGain(const EchoCanceller3Config& config,
                  Aec3Optimization optimization,
                  int sample_rate_hz,
                  size_t num_capture_channels);
  ~SuppressionGain();
  void GetGain(
      rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>>
          nearend_spectrum,
      rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>> echo_spectrum,
      rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>>
          residual_echo_spectrum,
      rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>>
          comfort_noise_spectrum,
      const RenderSignalAnalyzer& render_signal_analyzer,
      const AecState& aec_state,
      const std::vector<std::vector<std::vector<float>>>& render,
      bool clock_drift,
      float* high_bands_gain,
      std::array<float, kFftLengthBy2Plus1>* low_band_gain);

  bool IsDominantNearend() {
    return dominant_nearend_detector_->IsNearendState();
  }

  // Toggles the usage of the initial state.
  void SetInitialState(bool state);

 private:
  // Computes the gain to apply for the bands beyond the first band.
  float UpperBandsGain(
      rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>> echo_spectrum,
      rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>>
          comfort_noise_spectrum,
      const absl::optional<int>& narrow_peak_band,
      bool saturated_echo,
      const std::vector<std::vector<std::vector<float>>>& render,
      const std::array<float, kFftLengthBy2Plus1>& low_band_gain) const;

  void GainToNoAudibleEcho(const std::array<float, kFftLengthBy2Plus1>& nearend,
                           const std::array<float, kFftLengthBy2Plus1>& echo,
                           const std::array<float, kFftLengthBy2Plus1>& masker,
                           std::array<float, kFftLengthBy2Plus1>* gain) const;

  void LowerBandGain(
      bool stationary_with_low_power,
      const AecState& aec_state,
      rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>>
          suppressor_input,
      rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>> residual_echo,
      rtc::ArrayView<const std::array<float, kFftLengthBy2Plus1>> comfort_noise,
      bool clock_drift,
      std::array<float, kFftLengthBy2Plus1>* gain);

  void GetMinGain(rtc::ArrayView<const float> weighted_residual_echo,
                  rtc::ArrayView<const float> last_nearend,
                  rtc::ArrayView<const float> last_echo,
                  bool low_noise_render,
                  bool saturated_echo,
                  rtc::ArrayView<float> min_gain) const;

  void GetMaxGain(rtc::ArrayView<float> max_gain) const;

  class LowNoiseRenderDetector {
   public:
    bool Detect(const std::vector<std::vector<std::vector<float>>>& render);

   private:
    float average_power_ = 32768.f * 32768.f;
  };

  struct GainParameters {
    explicit GainParameters(
        const EchoCanceller3Config::Suppressor::Tuning& tuning);
    const float max_inc_factor;
    const float max_dec_factor_lf;
    std::array<float, kFftLengthBy2Plus1> enr_transparent_;
    std::array<float, kFftLengthBy2Plus1> enr_suppress_;
    std::array<float, kFftLengthBy2Plus1> emr_transparent_;
  };

  static int instance_count_;
  std::unique_ptr<ApmDataDumper> data_dumper_;
  const Aec3Optimization optimization_;
  const EchoCanceller3Config config_;
  const size_t num_capture_channels_;
  const int state_change_duration_blocks_;
  std::array<float, kFftLengthBy2Plus1> last_gain_;
  std::vector<std::array<float, kFftLengthBy2Plus1>> last_nearend_;
  std::vector<std::array<float, kFftLengthBy2Plus1>> last_echo_;
  LowNoiseRenderDetector low_render_detector_;
  bool initial_state_ = true;
  int initial_state_change_counter_ = 0;
  std::vector<aec3::MovingAverage> nearend_smoothers_;
  const GainParameters nearend_params_;
  const GainParameters normal_params_;
  std::unique_ptr<NearendDetector> dominant_nearend_detector_;

  RTC_DISALLOW_COPY_AND_ASSIGN(SuppressionGain);
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AEC3_SUPPRESSION_GAIN_H_
