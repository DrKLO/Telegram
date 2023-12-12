/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/audio_network_adaptor/fec_controller_plr_based.h"

#include <string>
#include <utility>

#include "rtc_base/checks.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {

namespace {
class NullSmoothingFilter final : public SmoothingFilter {
 public:
  void AddSample(float sample) override { last_sample_ = sample; }

  absl::optional<float> GetAverage() override { return last_sample_; }

  bool SetTimeConstantMs(int time_constant_ms) override {
    RTC_DCHECK_NOTREACHED();
    return false;
  }

 private:
  absl::optional<float> last_sample_;
};
}  // namespace

FecControllerPlrBased::Config::Config(
    bool initial_fec_enabled,
    const ThresholdCurve& fec_enabling_threshold,
    const ThresholdCurve& fec_disabling_threshold,
    int time_constant_ms)
    : initial_fec_enabled(initial_fec_enabled),
      fec_enabling_threshold(fec_enabling_threshold),
      fec_disabling_threshold(fec_disabling_threshold),
      time_constant_ms(time_constant_ms) {}

FecControllerPlrBased::FecControllerPlrBased(
    const Config& config,
    std::unique_ptr<SmoothingFilter> smoothing_filter)
    : config_(config),
      fec_enabled_(config.initial_fec_enabled),
      packet_loss_smoother_(std::move(smoothing_filter)) {
  RTC_DCHECK(config_.fec_disabling_threshold <= config_.fec_enabling_threshold);
}

FecControllerPlrBased::FecControllerPlrBased(const Config& config)
    : FecControllerPlrBased(
          config,
          webrtc::field_trial::FindFullName("UseTwccPlrForAna") == "Enabled"
              ? std::unique_ptr<NullSmoothingFilter>(new NullSmoothingFilter())
              : std::unique_ptr<SmoothingFilter>(
                    new SmoothingFilterImpl(config.time_constant_ms))) {}

FecControllerPlrBased::~FecControllerPlrBased() = default;

void FecControllerPlrBased::UpdateNetworkMetrics(
    const NetworkMetrics& network_metrics) {
  if (network_metrics.uplink_bandwidth_bps)
    uplink_bandwidth_bps_ = network_metrics.uplink_bandwidth_bps;
  if (network_metrics.uplink_packet_loss_fraction) {
    packet_loss_smoother_->AddSample(
        *network_metrics.uplink_packet_loss_fraction);
  }
}

void FecControllerPlrBased::MakeDecision(AudioEncoderRuntimeConfig* config) {
  RTC_DCHECK(!config->enable_fec);
  RTC_DCHECK(!config->uplink_packet_loss_fraction);

  const auto& packet_loss = packet_loss_smoother_->GetAverage();

  fec_enabled_ = fec_enabled_ ? !FecDisablingDecision(packet_loss)
                              : FecEnablingDecision(packet_loss);

  config->enable_fec = fec_enabled_;

  config->uplink_packet_loss_fraction = packet_loss ? *packet_loss : 0.0;
}

bool FecControllerPlrBased::FecEnablingDecision(
    const absl::optional<float>& packet_loss) const {
  if (!uplink_bandwidth_bps_ || !packet_loss) {
    return false;
  } else {
    // Enable when above the curve or exactly on it.
    return !config_.fec_enabling_threshold.IsBelowCurve(
        {static_cast<float>(*uplink_bandwidth_bps_), *packet_loss});
  }
}

bool FecControllerPlrBased::FecDisablingDecision(
    const absl::optional<float>& packet_loss) const {
  if (!uplink_bandwidth_bps_ || !packet_loss) {
    return false;
  } else {
    // Disable when below the curve.
    return config_.fec_disabling_threshold.IsBelowCurve(
        {static_cast<float>(*uplink_bandwidth_bps_), *packet_loss});
  }
}

}  // namespace webrtc
