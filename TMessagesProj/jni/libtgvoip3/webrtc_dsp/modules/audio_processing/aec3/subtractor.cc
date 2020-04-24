/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/subtractor.h"

#include <algorithm>
#include <utility>

#include "api/array_view.h"
#include "modules/audio_processing/aec3/fft_data.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/checks.h"
#include "rtc_base/numerics/safe_minmax.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {

namespace {

bool EnableAgcGainChangeResponse() {
  return !field_trial::IsEnabled("WebRTC-Aec3AgcGainChangeResponseKillSwitch");
}

bool EnableAdaptationDuringSaturation() {
  return !field_trial::IsEnabled("WebRTC-Aec3RapidAgcGainRecoveryKillSwitch");
}

bool EnableMisadjustmentEstimator() {
  return !field_trial::IsEnabled("WebRTC-Aec3MisadjustmentEstimatorKillSwitch");
}

bool EnableShadowFilterJumpstart() {
  return !field_trial::IsEnabled("WebRTC-Aec3ShadowFilterJumpstartKillSwitch");
}

bool EnableShadowFilterBoostedJumpstart() {
  return !field_trial::IsEnabled(
      "WebRTC-Aec3ShadowFilterBoostedJumpstartKillSwitch");
}

bool EnableEarlyShadowFilterJumpstart() {
  return !field_trial::IsEnabled(
      "WebRTC-Aec3EarlyShadowFilterJumpstartKillSwitch");
}

void PredictionError(const Aec3Fft& fft,
                     const FftData& S,
                     rtc::ArrayView<const float> y,
                     std::array<float, kBlockSize>* e,
                     std::array<float, kBlockSize>* s,
                     bool adaptation_during_saturation,
                     bool* saturation) {
  std::array<float, kFftLength> tmp;
  fft.Ifft(S, &tmp);
  constexpr float kScale = 1.0f / kFftLengthBy2;
  std::transform(y.begin(), y.end(), tmp.begin() + kFftLengthBy2, e->begin(),
                 [&](float a, float b) { return a - b * kScale; });

  *saturation = false;

  if (s) {
    for (size_t k = 0; k < s->size(); ++k) {
      (*s)[k] = kScale * tmp[k + kFftLengthBy2];
    }
    auto result = std::minmax_element(s->begin(), s->end());
    *saturation = *result.first <= -32768 || *result.first >= 32767;
  }
  if (!(*saturation)) {
    auto result = std::minmax_element(e->begin(), e->end());
    *saturation = *result.first <= -32768 || *result.first >= 32767;
  }

  if (!adaptation_during_saturation) {
    std::for_each(e->begin(), e->end(),
                  [](float& a) { a = rtc::SafeClamp(a, -32768.f, 32767.f); });
  } else {
    *saturation = false;
  }
}

void ScaleFilterOutput(rtc::ArrayView<const float> y,
                       float factor,
                       rtc::ArrayView<float> e,
                       rtc::ArrayView<float> s) {
  RTC_DCHECK_EQ(y.size(), e.size());
  RTC_DCHECK_EQ(y.size(), s.size());
  for (size_t k = 0; k < y.size(); ++k) {
    s[k] *= factor;
    e[k] = y[k] - s[k];
  }
}

}  // namespace

Subtractor::Subtractor(const EchoCanceller3Config& config,
                       ApmDataDumper* data_dumper,
                       Aec3Optimization optimization)
    : fft_(),
      data_dumper_(data_dumper),
      optimization_(optimization),
      config_(config),
      adaptation_during_saturation_(EnableAdaptationDuringSaturation()),
      enable_misadjustment_estimator_(EnableMisadjustmentEstimator()),
      enable_agc_gain_change_response_(EnableAgcGainChangeResponse()),
      enable_shadow_filter_jumpstart_(EnableShadowFilterJumpstart()),
      enable_shadow_filter_boosted_jumpstart_(
          EnableShadowFilterBoostedJumpstart()),
      enable_early_shadow_filter_jumpstart_(EnableEarlyShadowFilterJumpstart()),
      main_filter_(config_.filter.main.length_blocks,
                   config_.filter.main_initial.length_blocks,
                   config.filter.config_change_duration_blocks,
                   optimization,
                   data_dumper_),
      shadow_filter_(config_.filter.shadow.length_blocks,
                     config_.filter.shadow_initial.length_blocks,
                     config.filter.config_change_duration_blocks,
                     optimization,
                     data_dumper_),
      G_main_(config_.filter.main_initial,
              config_.filter.config_change_duration_blocks),
      G_shadow_(config_.filter.shadow_initial,
                config.filter.config_change_duration_blocks) {
  RTC_DCHECK(data_dumper_);
}

Subtractor::~Subtractor() = default;

void Subtractor::HandleEchoPathChange(
    const EchoPathVariability& echo_path_variability) {
  const auto full_reset = [&]() {
    main_filter_.HandleEchoPathChange();
    shadow_filter_.HandleEchoPathChange();
    G_main_.HandleEchoPathChange(echo_path_variability);
    G_shadow_.HandleEchoPathChange();
    G_main_.SetConfig(config_.filter.main_initial, true);
    G_shadow_.SetConfig(config_.filter.shadow_initial, true);
    main_filter_.SetSizePartitions(config_.filter.main_initial.length_blocks,
                                   true);
    shadow_filter_.SetSizePartitions(
        config_.filter.shadow_initial.length_blocks, true);
  };

  if (echo_path_variability.delay_change !=
      EchoPathVariability::DelayAdjustment::kNone) {
    full_reset();
  }

  if (echo_path_variability.gain_change && enable_agc_gain_change_response_) {
    G_main_.HandleEchoPathChange(echo_path_variability);
  }
}

void Subtractor::ExitInitialState() {
  G_main_.SetConfig(config_.filter.main, false);
  G_shadow_.SetConfig(config_.filter.shadow, false);
  main_filter_.SetSizePartitions(config_.filter.main.length_blocks, false);
  shadow_filter_.SetSizePartitions(config_.filter.shadow.length_blocks, false);
}

void Subtractor::Process(const RenderBuffer& render_buffer,
                         const rtc::ArrayView<const float> capture,
                         const RenderSignalAnalyzer& render_signal_analyzer,
                         const AecState& aec_state,
                         SubtractorOutput* output) {
  RTC_DCHECK_EQ(kBlockSize, capture.size());
  rtc::ArrayView<const float> y = capture;
  FftData& E_main = output->E_main;
  FftData E_shadow;
  std::array<float, kBlockSize>& e_main = output->e_main;
  std::array<float, kBlockSize>& e_shadow = output->e_shadow;

  FftData S;
  FftData& G = S;

  // Form the outputs of the main and shadow filters.
  main_filter_.Filter(render_buffer, &S);
  bool main_saturation = false;
  PredictionError(fft_, S, y, &e_main, &output->s_main,
                  adaptation_during_saturation_, &main_saturation);

  shadow_filter_.Filter(render_buffer, &S);
  bool shadow_saturation = false;
  PredictionError(fft_, S, y, &e_shadow, &output->s_shadow,
                  adaptation_during_saturation_, &shadow_saturation);

  // Compute the signal powers in the subtractor output.
  output->ComputeMetrics(y);

  // Adjust the filter if needed.
  bool main_filter_adjusted = false;
  if (enable_misadjustment_estimator_) {
    filter_misadjustment_estimator_.Update(*output);
    if (filter_misadjustment_estimator_.IsAdjustmentNeeded()) {
      float scale = filter_misadjustment_estimator_.GetMisadjustment();
      main_filter_.ScaleFilter(scale);
      ScaleFilterOutput(y, scale, e_main, output->s_main);
      filter_misadjustment_estimator_.Reset();
      main_filter_adjusted = true;
    }
  }

  // Compute the FFts of the main and shadow filter outputs.
  fft_.ZeroPaddedFft(e_main, Aec3Fft::Window::kHanning, &E_main);
  fft_.ZeroPaddedFft(e_shadow, Aec3Fft::Window::kHanning, &E_shadow);

  // Compute spectra for future use.
  E_shadow.Spectrum(optimization_, output->E2_shadow);
  E_main.Spectrum(optimization_, output->E2_main);

  // Compute the render powers.
  std::array<float, kFftLengthBy2Plus1> X2_main;
  std::array<float, kFftLengthBy2Plus1> X2_shadow_data;
  std::array<float, kFftLengthBy2Plus1>& X2_shadow =
      main_filter_.SizePartitions() == shadow_filter_.SizePartitions()
          ? X2_main
          : X2_shadow_data;
  if (main_filter_.SizePartitions() == shadow_filter_.SizePartitions()) {
    render_buffer.SpectralSum(main_filter_.SizePartitions(), &X2_main);
  } else if (main_filter_.SizePartitions() > shadow_filter_.SizePartitions()) {
    render_buffer.SpectralSums(shadow_filter_.SizePartitions(),
                               main_filter_.SizePartitions(), &X2_shadow,
                               &X2_main);
  } else {
    render_buffer.SpectralSums(main_filter_.SizePartitions(),
                               shadow_filter_.SizePartitions(), &X2_main,
                               &X2_shadow);
  }

  // Update the main filter.
  if (!main_filter_adjusted) {
    G_main_.Compute(X2_main, render_signal_analyzer, *output, main_filter_,
                    aec_state.SaturatedCapture() || main_saturation, &G);
  } else {
    G.re.fill(0.f);
    G.im.fill(0.f);
  }
  main_filter_.Adapt(render_buffer, G);
  data_dumper_->DumpRaw("aec3_subtractor_G_main", G.re);
  data_dumper_->DumpRaw("aec3_subtractor_G_main", G.im);

  // Update the shadow filter.
  poor_shadow_filter_counter_ =
      output->e2_main < output->e2_shadow ? poor_shadow_filter_counter_ + 1 : 0;
  if (((poor_shadow_filter_counter_ < 5 &&
        enable_early_shadow_filter_jumpstart_) ||
       (poor_shadow_filter_counter_ < 10 &&
        !enable_early_shadow_filter_jumpstart_)) ||
      !enable_shadow_filter_jumpstart_) {
    G_shadow_.Compute(X2_shadow, render_signal_analyzer, E_shadow,
                      shadow_filter_.SizePartitions(),
                      aec_state.SaturatedCapture() || shadow_saturation, &G);
    shadow_filter_.Adapt(render_buffer, G);
  } else {
    poor_shadow_filter_counter_ = 0;
    if (enable_shadow_filter_boosted_jumpstart_) {
      shadow_filter_.SetFilter(main_filter_.GetFilter());
      G_shadow_.Compute(X2_shadow, render_signal_analyzer, E_main,
                        shadow_filter_.SizePartitions(),
                        aec_state.SaturatedCapture() || main_saturation, &G);
      shadow_filter_.Adapt(render_buffer, G);
    } else {
      G.re.fill(0.f);
      G.im.fill(0.f);
      shadow_filter_.Adapt(render_buffer, G);
      shadow_filter_.SetFilter(main_filter_.GetFilter());
    }
  }

  data_dumper_->DumpRaw("aec3_subtractor_G_shadow", G.re);
  data_dumper_->DumpRaw("aec3_subtractor_G_shadow", G.im);
  filter_misadjustment_estimator_.Dump(data_dumper_);
  DumpFilters();

  if (adaptation_during_saturation_) {
    std::for_each(e_main.begin(), e_main.end(),
                  [](float& a) { a = rtc::SafeClamp(a, -32768.f, 32767.f); });
  }

  data_dumper_->DumpWav("aec3_main_filter_output", kBlockSize, &e_main[0],
                        16000, 1);
  data_dumper_->DumpWav("aec3_shadow_filter_output", kBlockSize, &e_shadow[0],
                        16000, 1);
}

void Subtractor::FilterMisadjustmentEstimator::Update(
    const SubtractorOutput& output) {
  e2_acum_ += output.e2_main;
  y2_acum_ += output.y2;
  if (++n_blocks_acum_ == n_blocks_) {
    if (y2_acum_ > n_blocks_ * 200.f * 200.f * kBlockSize) {
      float update = (e2_acum_ / y2_acum_);
      if (e2_acum_ > n_blocks_ * 7500.f * 7500.f * kBlockSize) {
        // Duration equal to blockSizeMs * n_blocks_ * 4.
        overhang_ = 4;
      } else {
        overhang_ = std::max(overhang_ - 1, 0);
      }

      if ((update < inv_misadjustment_) || (overhang_ > 0)) {
        inv_misadjustment_ += 0.1f * (update - inv_misadjustment_);
      }
    }
    e2_acum_ = 0.f;
    y2_acum_ = 0.f;
    n_blocks_acum_ = 0;
  }
}

void Subtractor::FilterMisadjustmentEstimator::Reset() {
  e2_acum_ = 0.f;
  y2_acum_ = 0.f;
  n_blocks_acum_ = 0;
  inv_misadjustment_ = 0.f;
  overhang_ = 0.f;
}

void Subtractor::FilterMisadjustmentEstimator::Dump(
    ApmDataDumper* data_dumper) const {
  data_dumper->DumpRaw("aec3_inv_misadjustment_factor", inv_misadjustment_);
}

}  // namespace webrtc
