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
#include "modules/audio_processing/aec3/render_signal_analyzer.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/constructormagic.h"

namespace webrtc {

class SuppressionGain {
 public:
  SuppressionGain(const EchoCanceller3Config& config,
                  Aec3Optimization optimization,
                  int sample_rate_hz);
  ~SuppressionGain();
  void GetGain(
      const std::array<float, kFftLengthBy2Plus1>& suppressor_input_spectrum,
      const std::array<float, kFftLengthBy2Plus1>& nearend_spectrum,
      const std::array<float, kFftLengthBy2Plus1>& echo_spectrum,
      const std::array<float, kFftLengthBy2Plus1>& residual_echo_spectrum,
      const std::array<float, kFftLengthBy2Plus1>& comfort_noise_spectrum,
      const FftData& linear_aec_fft,
      const FftData& capture_fft,
      const RenderSignalAnalyzer& render_signal_analyzer,
      const AecState& aec_state,
      const std::vector<std::vector<float>>& render,
      float* high_bands_gain,
      std::array<float, kFftLengthBy2Plus1>* low_band_gain);

  // Toggles the usage of the initial state.
  void SetInitialState(bool state);

 private:
  // Computes the gain to apply for the bands beyond the first band.
  float UpperBandsGain(
      const std::array<float, kFftLengthBy2Plus1>& echo_spectrum,
      const std::array<float, kFftLengthBy2Plus1>& comfort_noise_spectrum,
      const absl::optional<int>& narrow_peak_band,
      bool saturated_echo,
      const std::vector<std::vector<float>>& render,
      const std::array<float, kFftLengthBy2Plus1>& low_band_gain) const;

  void GainToNoAudibleEcho(
      const std::array<float, kFftLengthBy2Plus1>& nearend,
      const std::array<float, kFftLengthBy2Plus1>& echo,
      const std::array<float, kFftLengthBy2Plus1>& masker,
      const std::array<float, kFftLengthBy2Plus1>& min_gain,
      const std::array<float, kFftLengthBy2Plus1>& max_gain,
      std::array<float, kFftLengthBy2Plus1>* gain) const;

  void LowerBandGain(
      bool stationary_with_low_power,
      const AecState& aec_state,
      const std::array<float, kFftLengthBy2Plus1>& suppressor_input,
      const std::array<float, kFftLengthBy2Plus1>& nearend,
      const std::array<float, kFftLengthBy2Plus1>& residual_echo,
      const std::array<float, kFftLengthBy2Plus1>& comfort_noise,
      std::array<float, kFftLengthBy2Plus1>* gain);

  void GetMinGain(rtc::ArrayView<const float> suppressor_input,
                  rtc::ArrayView<const float> weighted_residual_echo,
                  bool low_noise_render,
                  bool saturated_echo,
                  rtc::ArrayView<float> min_gain) const;

  void GetMaxGain(rtc::ArrayView<float> max_gain) const;

  class LowNoiseRenderDetector {
   public:
    bool Detect(const std::vector<std::vector<float>>& render);

   private:
    float average_power_ = 32768.f * 32768.f;
  };

  // Class for selecting whether the suppressor is in the nearend or echo state.
  class DominantNearendDetector {
   public:
    explicit DominantNearendDetector(
        const EchoCanceller3Config::Suppressor::DominantNearendDetection
            config);

    // Returns whether the current state is the nearend state.
    bool IsNearendState() const { return nearend_state_; }

    // Updates the state selection based on latest spectral estimates.
    void Update(rtc::ArrayView<const float> nearend_spectrum,
                rtc::ArrayView<const float> residual_echo_spectrum,
                rtc::ArrayView<const float> comfort_noise_spectrum,
                bool initial_state);

   private:
    const float enr_threshold_;
    const float enr_exit_threshold_;
    const float snr_threshold_;
    const int hold_duration_;
    const int trigger_threshold_;
    const bool use_during_initial_phase_;

    bool nearend_state_ = false;
    int trigger_counter_ = 0;
    int hold_counter_ = 0;
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
  const int state_change_duration_blocks_;
  float one_by_state_change_duration_blocks_;
  std::array<float, kFftLengthBy2Plus1> last_gain_;
  std::array<float, kFftLengthBy2Plus1> last_nearend_;
  std::array<float, kFftLengthBy2Plus1> last_echo_;
  LowNoiseRenderDetector low_render_detector_;
  bool initial_state_ = true;
  int initial_state_change_counter_ = 0;
  aec3::MovingAverage moving_average_;
  const GainParameters nearend_params_;
  const GainParameters normal_params_;
  DominantNearendDetector dominant_nearend_detector_;

  RTC_DISALLOW_COPY_AND_ASSIGN(SuppressionGain);
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AEC3_SUPPRESSION_GAIN_H_
