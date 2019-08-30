/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/main_filter_update_gain.h"

#include <algorithm>
#include <functional>

#include "modules/audio_processing/aec3/adaptive_fir_filter.h"
#include "modules/audio_processing/aec3/aec3_common.h"
#include "modules/audio_processing/aec3/echo_path_variability.h"
#include "modules/audio_processing/aec3/fft_data.h"
#include "modules/audio_processing/aec3/render_signal_analyzer.h"
#include "modules/audio_processing/aec3/subtractor_output.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/atomicops.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace {

constexpr float kHErrorInitial = 10000.f;
constexpr int kPoorExcitationCounterInitial = 1000;

}  // namespace

int MainFilterUpdateGain::instance_count_ = 0;

MainFilterUpdateGain::MainFilterUpdateGain(
    const EchoCanceller3Config::Filter::MainConfiguration& config,
    size_t config_change_duration_blocks)
    : data_dumper_(
          new ApmDataDumper(rtc::AtomicOps::Increment(&instance_count_))),
      config_change_duration_blocks_(
          static_cast<int>(config_change_duration_blocks)),
      poor_excitation_counter_(kPoorExcitationCounterInitial) {
  SetConfig(config, true);
  H_error_.fill(kHErrorInitial);
  RTC_DCHECK_LT(0, config_change_duration_blocks_);
  one_by_config_change_duration_blocks_ = 1.f / config_change_duration_blocks_;
}

MainFilterUpdateGain::~MainFilterUpdateGain() {}

void MainFilterUpdateGain::HandleEchoPathChange(
    const EchoPathVariability& echo_path_variability) {
  if (echo_path_variability.gain_change) {
    // TODO(bugs.webrtc.org/9526) Handle gain changes.
  }

  if (echo_path_variability.delay_change !=
      EchoPathVariability::DelayAdjustment::kNone) {
    H_error_.fill(kHErrorInitial);
  }

  if (!echo_path_variability.gain_change) {
    poor_excitation_counter_ = kPoorExcitationCounterInitial;
    call_counter_ = 0;
  }
}

void MainFilterUpdateGain::Compute(
    const std::array<float, kFftLengthBy2Plus1>& render_power,
    const RenderSignalAnalyzer& render_signal_analyzer,
    const SubtractorOutput& subtractor_output,
    const AdaptiveFirFilter& filter,
    bool saturated_capture_signal,
    FftData* gain_fft) {
  RTC_DCHECK(gain_fft);
  // Introducing shorter notation to improve readability.
  const FftData& E_main = subtractor_output.E_main;
  const auto& E2_main = subtractor_output.E2_main;
  const auto& E2_shadow = subtractor_output.E2_shadow;
  FftData* G = gain_fft;
  const size_t size_partitions = filter.SizePartitions();
  auto X2 = render_power;
  const auto& erl = filter.Erl();
  ++call_counter_;

  UpdateCurrentConfig();

  if (render_signal_analyzer.PoorSignalExcitation()) {
    poor_excitation_counter_ = 0;
  }

  // Do not update the filter if the render is not sufficiently excited.
  if (++poor_excitation_counter_ < size_partitions ||
      saturated_capture_signal || call_counter_ <= size_partitions) {
    G->re.fill(0.f);
    G->im.fill(0.f);
  } else {
    // Corresponds to WGN of power -39 dBFS.
    std::array<float, kFftLengthBy2Plus1> mu;
    // mu = H_error / (0.5* H_error* X2 + n * E2).
    for (size_t k = 0; k < kFftLengthBy2Plus1; ++k) {
      mu[k] = X2[k] > current_config_.noise_gate
                  ? H_error_[k] / (0.5f * H_error_[k] * X2[k] +
                                   size_partitions * E2_main[k])
                  : 0.f;
    }

    // Avoid updating the filter close to narrow bands in the render signals.
    render_signal_analyzer.MaskRegionsAroundNarrowBands(&mu);

    // H_error = H_error - 0.5 * mu * X2 * H_error.
    for (size_t k = 0; k < H_error_.size(); ++k) {
      H_error_[k] -= 0.5f * mu[k] * X2[k] * H_error_[k];
    }

    // G = mu * E.
    std::transform(mu.begin(), mu.end(), E_main.re.begin(), G->re.begin(),
                   std::multiplies<float>());
    std::transform(mu.begin(), mu.end(), E_main.im.begin(), G->im.begin(),
                   std::multiplies<float>());
  }

  // H_error = H_error + factor * erl.
  std::array<float, kFftLengthBy2Plus1> H_error_increase;
  std::transform(E2_shadow.begin(), E2_shadow.end(), E2_main.begin(),
                 H_error_increase.begin(), [&](float a, float b) {
                   return a >= b ? current_config_.leakage_converged
                                 : current_config_.leakage_diverged;
                 });
  std::transform(erl.begin(), erl.end(), H_error_increase.begin(),
                 H_error_increase.begin(), std::multiplies<float>());
  std::transform(H_error_.begin(), H_error_.end(), H_error_increase.begin(),
                 H_error_.begin(), [&](float a, float b) {
                   float error = a + b;
                   error = std::max(error, current_config_.error_floor);
                   error = std::min(error, current_config_.error_ceil);
                   return error;
                 });

  data_dumper_->DumpRaw("aec3_main_gain_H_error", H_error_);
}

void MainFilterUpdateGain::UpdateCurrentConfig() {
  RTC_DCHECK_GE(config_change_duration_blocks_, config_change_counter_);
  if (config_change_counter_ > 0) {
    if (--config_change_counter_ > 0) {
      auto average = [](float from, float to, float from_weight) {
        return from * from_weight + to * (1.f - from_weight);
      };

      float change_factor =
          config_change_counter_ * one_by_config_change_duration_blocks_;

      current_config_.leakage_converged =
          average(old_target_config_.leakage_converged,
                  target_config_.leakage_converged, change_factor);
      current_config_.leakage_diverged =
          average(old_target_config_.leakage_diverged,
                  target_config_.leakage_diverged, change_factor);
      current_config_.error_floor =
          average(old_target_config_.error_floor, target_config_.error_floor,
                  change_factor);
      current_config_.error_ceil =
          average(old_target_config_.error_ceil, target_config_.error_ceil,
                  change_factor);
      current_config_.noise_gate =
          average(old_target_config_.noise_gate, target_config_.noise_gate,
                  change_factor);
    } else {
      current_config_ = old_target_config_ = target_config_;
    }
  }
  RTC_DCHECK_LE(0, config_change_counter_);
}

}  // namespace webrtc
