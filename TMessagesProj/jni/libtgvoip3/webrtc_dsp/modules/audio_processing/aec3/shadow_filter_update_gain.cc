/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/shadow_filter_update_gain.h"

#include <algorithm>
#include <functional>

#include "rtc_base/checks.h"

namespace webrtc {

ShadowFilterUpdateGain::ShadowFilterUpdateGain(
    const EchoCanceller3Config::Filter::ShadowConfiguration& config,
    size_t config_change_duration_blocks)
    : config_change_duration_blocks_(
          static_cast<int>(config_change_duration_blocks)) {
  SetConfig(config, true);
  RTC_DCHECK_LT(0, config_change_duration_blocks_);
  one_by_config_change_duration_blocks_ = 1.f / config_change_duration_blocks_;
}

void ShadowFilterUpdateGain::HandleEchoPathChange() {
  // TODO(peah): Check whether this counter should instead be initialized to a
  // large value.
  poor_signal_excitation_counter_ = 0;
  call_counter_ = 0;
}

void ShadowFilterUpdateGain::Compute(
    const std::array<float, kFftLengthBy2Plus1>& render_power,
    const RenderSignalAnalyzer& render_signal_analyzer,
    const FftData& E_shadow,
    size_t size_partitions,
    bool saturated_capture_signal,
    FftData* G) {
  RTC_DCHECK(G);
  ++call_counter_;

  UpdateCurrentConfig();

  if (render_signal_analyzer.PoorSignalExcitation()) {
    poor_signal_excitation_counter_ = 0;
  }

  // Do not update the filter if the render is not sufficiently excited.
  if (++poor_signal_excitation_counter_ < size_partitions ||
      saturated_capture_signal || call_counter_ <= size_partitions) {
    G->re.fill(0.f);
    G->im.fill(0.f);
    return;
  }

  // Compute mu.
  std::array<float, kFftLengthBy2Plus1> mu;
  auto X2 = render_power;
  std::transform(X2.begin(), X2.end(), mu.begin(), [&](float a) {
    return a > current_config_.noise_gate ? current_config_.rate / a : 0.f;
  });

  // Avoid updating the filter close to narrow bands in the render signals.
  render_signal_analyzer.MaskRegionsAroundNarrowBands(&mu);

  // G = mu * E * X2.
  std::transform(mu.begin(), mu.end(), E_shadow.re.begin(), G->re.begin(),
                 std::multiplies<float>());
  std::transform(mu.begin(), mu.end(), E_shadow.im.begin(), G->im.begin(),
                 std::multiplies<float>());
}

void ShadowFilterUpdateGain::UpdateCurrentConfig() {
  RTC_DCHECK_GE(config_change_duration_blocks_, config_change_counter_);
  if (config_change_counter_ > 0) {
    if (--config_change_counter_ > 0) {
      auto average = [](float from, float to, float from_weight) {
        return from * from_weight + to * (1.f - from_weight);
      };

      float change_factor =
          config_change_counter_ * one_by_config_change_duration_blocks_;

      current_config_.rate =
          average(old_target_config_.rate, target_config_.rate, change_factor);
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
