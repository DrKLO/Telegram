/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "video/encoder_bitrate_adjuster.h"

#include <algorithm>
#include <memory>
#include <vector>

#include "rtc_base/experiments/rate_control_settings.h"
#include "rtc_base/logging.h"
#include "rtc_base/time_utils.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {
namespace {
// Helper struct with metadata for a single spatial layer.
struct LayerRateInfo {
  double link_utilization_factor = 0.0;
  double media_utilization_factor = 0.0;
  DataRate target_rate = DataRate::Zero();

  DataRate WantedOvershoot() const {
    // If there is headroom, allow bitrate to go up to media rate limit.
    // Still limit media utilization to 1.0, so we don't overshoot over long
    // runs even if we have headroom.
    const double max_media_utilization =
        std::max(1.0, media_utilization_factor);
    if (link_utilization_factor > max_media_utilization) {
      return (link_utilization_factor - max_media_utilization) * target_rate;
    }
    return DataRate::Zero();
  }
};
}  // namespace
constexpr int64_t EncoderBitrateAdjuster::kWindowSizeMs;
constexpr size_t EncoderBitrateAdjuster::kMinFramesSinceLayoutChange;
constexpr double EncoderBitrateAdjuster::kDefaultUtilizationFactor;

EncoderBitrateAdjuster::EncoderBitrateAdjuster(const VideoCodec& codec_settings)
    : utilize_bandwidth_headroom_(RateControlSettings::ParseFromFieldTrials()
                                      .BitrateAdjusterCanUseNetworkHeadroom()),
      frames_since_layout_change_(0),
      min_bitrates_bps_{} {
  if (codec_settings.codecType == VideoCodecType::kVideoCodecVP9) {
    for (size_t si = 0; si < codec_settings.VP9().numberOfSpatialLayers; ++si) {
      if (codec_settings.spatialLayers[si].active) {
        min_bitrates_bps_[si] =
            std::max(codec_settings.minBitrate * 1000,
                     codec_settings.spatialLayers[si].minBitrate * 1000);
      }
    }
  } else {
    for (size_t si = 0; si < codec_settings.numberOfSimulcastStreams; ++si) {
      if (codec_settings.simulcastStream[si].active) {
        min_bitrates_bps_[si] =
            std::max(codec_settings.minBitrate * 1000,
                     codec_settings.simulcastStream[si].minBitrate * 1000);
      }
    }
  }
}

EncoderBitrateAdjuster::~EncoderBitrateAdjuster() = default;

VideoBitrateAllocation EncoderBitrateAdjuster::AdjustRateAllocation(
    const VideoEncoder::RateControlParameters& rates) {
  current_rate_control_parameters_ = rates;

  // First check that overshoot detectors exist, and store per spatial layer
  // how many active temporal layers we have.
  size_t active_tls_[kMaxSpatialLayers] = {};
  for (size_t si = 0; si < kMaxSpatialLayers; ++si) {
    active_tls_[si] = 0;
    for (size_t ti = 0; ti < kMaxTemporalStreams; ++ti) {
      // Layer is enabled iff it has both positive bitrate and framerate target.
      if (rates.bitrate.GetBitrate(si, ti) > 0 &&
          current_fps_allocation_[si].size() > ti &&
          current_fps_allocation_[si][ti] > 0) {
        ++active_tls_[si];
        if (!overshoot_detectors_[si][ti]) {
          overshoot_detectors_[si][ti] =
              std::make_unique<EncoderOvershootDetector>(kWindowSizeMs);
          frames_since_layout_change_ = 0;
        }
      } else if (overshoot_detectors_[si][ti]) {
        // Layer removed, destroy overshoot detector.
        overshoot_detectors_[si][ti].reset();
        frames_since_layout_change_ = 0;
      }
    }
  }

  // Next poll the overshoot detectors and populate the adjusted allocation.
  const int64_t now_ms = rtc::TimeMillis();
  VideoBitrateAllocation adjusted_allocation;
  std::vector<LayerRateInfo> layer_infos;
  DataRate wanted_overshoot_sum = DataRate::Zero();

  for (size_t si = 0; si < kMaxSpatialLayers; ++si) {
    layer_infos.emplace_back();
    LayerRateInfo& layer_info = layer_infos.back();

    layer_info.target_rate =
        DataRate::BitsPerSec(rates.bitrate.GetSpatialLayerSum(si));

    // Adjustment is done per spatial layer only (not per temporal layer).
    if (frames_since_layout_change_ < kMinFramesSinceLayoutChange) {
      layer_info.link_utilization_factor = kDefaultUtilizationFactor;
      layer_info.media_utilization_factor = kDefaultUtilizationFactor;
    } else if (active_tls_[si] == 0 ||
               layer_info.target_rate == DataRate::Zero()) {
      // No signaled temporal layers, or no bitrate set. Could either be unused
      // spatial layer or bitrate dynamic mode; pass bitrate through without any
      // change.
      layer_info.link_utilization_factor = 1.0;
      layer_info.media_utilization_factor = 1.0;
    } else if (active_tls_[si] == 1) {
      // A single active temporal layer, this might mean single layer or that
      // encoder does not support temporal layers. Merge target bitrates for
      // this spatial layer.
      RTC_DCHECK(overshoot_detectors_[si][0]);
      layer_info.link_utilization_factor =
          overshoot_detectors_[si][0]
              ->GetNetworkRateUtilizationFactor(now_ms)
              .value_or(kDefaultUtilizationFactor);
      layer_info.media_utilization_factor =
          overshoot_detectors_[si][0]
              ->GetMediaRateUtilizationFactor(now_ms)
              .value_or(kDefaultUtilizationFactor);
    } else if (layer_info.target_rate > DataRate::Zero()) {
      // Multiple temporal layers enabled for this spatial layer. Update rate
      // for each of them and make a weighted average of utilization factors,
      // with bitrate fraction used as weight.
      // If any layer is missing a utilization factor, fall back to default.
      layer_info.link_utilization_factor = 0.0;
      layer_info.media_utilization_factor = 0.0;
      for (size_t ti = 0; ti < active_tls_[si]; ++ti) {
        RTC_DCHECK(overshoot_detectors_[si][ti]);
        const absl::optional<double> ti_link_utilization_factor =
            overshoot_detectors_[si][ti]->GetNetworkRateUtilizationFactor(
                now_ms);
        const absl::optional<double> ti_media_utilization_factor =
            overshoot_detectors_[si][ti]->GetMediaRateUtilizationFactor(now_ms);
        if (!ti_link_utilization_factor || !ti_media_utilization_factor) {
          layer_info.link_utilization_factor = kDefaultUtilizationFactor;
          layer_info.media_utilization_factor = kDefaultUtilizationFactor;
          break;
        }
        const double weight =
            static_cast<double>(rates.bitrate.GetBitrate(si, ti)) /
            layer_info.target_rate.bps();
        layer_info.link_utilization_factor +=
            weight * ti_link_utilization_factor.value();
        layer_info.media_utilization_factor +=
            weight * ti_media_utilization_factor.value();
      }
    } else {
      RTC_NOTREACHED();
    }

    if (layer_info.link_utilization_factor < 1.0) {
      // TODO(sprang): Consider checking underuse and allowing it to cancel some
      // potential overuse by other streams.

      // Don't boost target bitrate if encoder is under-using.
      layer_info.link_utilization_factor = 1.0;
    } else {
      // Don't reduce encoder target below 50%, in which case the frame dropper
      // should kick in instead.
      layer_info.link_utilization_factor =
          std::min(layer_info.link_utilization_factor, 2.0);

      // Keep track of sum of desired overshoot bitrate.
      wanted_overshoot_sum += layer_info.WantedOvershoot();
    }
  }

  // Available link headroom that can be used to fill wanted overshoot.
  DataRate available_headroom = DataRate::Zero();
  if (utilize_bandwidth_headroom_) {
    available_headroom = rates.bandwidth_allocation -
                         DataRate::BitsPerSec(rates.bitrate.get_sum_bps());
  }

  // All wanted overshoots are satisfied in the same proportion based on
  // available headroom.
  const double granted_overshoot_ratio =
      wanted_overshoot_sum == DataRate::Zero()
          ? 0.0
          : std::min(1.0, available_headroom.bps<double>() /
                              wanted_overshoot_sum.bps());

  for (size_t si = 0; si < kMaxSpatialLayers; ++si) {
    LayerRateInfo& layer_info = layer_infos[si];
    double utilization_factor = layer_info.link_utilization_factor;
    DataRate allowed_overshoot =
        granted_overshoot_ratio * layer_info.WantedOvershoot();
    if (allowed_overshoot > DataRate::Zero()) {
      // Pretend the target bitrate is higher by the allowed overshoot.
      // Since utilization_factor = actual_bitrate / target_bitrate, it can be
      // done by multiplying by old_target_bitrate / new_target_bitrate.
      utilization_factor *= layer_info.target_rate.bps<double>() /
                            (allowed_overshoot.bps<double>() +
                             layer_info.target_rate.bps<double>());
    }

    if (min_bitrates_bps_[si] > 0 &&
        layer_info.target_rate > DataRate::Zero() &&
        DataRate::BitsPerSec(min_bitrates_bps_[si]) < layer_info.target_rate) {
      // Make sure rate adjuster doesn't push target bitrate below minimum.
      utilization_factor =
          std::min(utilization_factor, layer_info.target_rate.bps<double>() /
                                           min_bitrates_bps_[si]);
    }

    if (layer_info.target_rate > DataRate::Zero()) {
      RTC_LOG(LS_VERBOSE) << "Utilization factors for spatial index " << si
                          << ": link = " << layer_info.link_utilization_factor
                          << ", media = " << layer_info.media_utilization_factor
                          << ", wanted overshoot = "
                          << layer_info.WantedOvershoot().bps()
                          << " bps, available headroom = "
                          << available_headroom.bps()
                          << " bps, total utilization factor = "
                          << utilization_factor;
    }

    // Populate the adjusted allocation with determined utilization factor.
    if (active_tls_[si] == 1 &&
        layer_info.target_rate >
            DataRate::BitsPerSec(rates.bitrate.GetBitrate(si, 0))) {
      // Bitrate allocation indicates temporal layer usage, but encoder
      // does not seem to support it. Pipe all bitrate into a single
      // overshoot detector.
      uint32_t adjusted_layer_bitrate_bps =
          std::min(static_cast<uint32_t>(
                       layer_info.target_rate.bps() / utilization_factor + 0.5),
                   layer_info.target_rate.bps<uint32_t>());
      adjusted_allocation.SetBitrate(si, 0, adjusted_layer_bitrate_bps);
    } else {
      for (size_t ti = 0; ti < kMaxTemporalStreams; ++ti) {
        if (rates.bitrate.HasBitrate(si, ti)) {
          uint32_t adjusted_layer_bitrate_bps = std::min(
              static_cast<uint32_t>(
                  rates.bitrate.GetBitrate(si, ti) / utilization_factor + 0.5),
              rates.bitrate.GetBitrate(si, ti));
          adjusted_allocation.SetBitrate(si, ti, adjusted_layer_bitrate_bps);
        }
      }
    }

    // In case of rounding errors, add bitrate to TL0 until min bitrate
    // constraint has been met.
    const uint32_t adjusted_spatial_layer_sum =
        adjusted_allocation.GetSpatialLayerSum(si);
    if (layer_info.target_rate > DataRate::Zero() &&
        adjusted_spatial_layer_sum < min_bitrates_bps_[si]) {
      adjusted_allocation.SetBitrate(si, 0,
                                     adjusted_allocation.GetBitrate(si, 0) +
                                         min_bitrates_bps_[si] -
                                         adjusted_spatial_layer_sum);
    }

    // Update all detectors with the new adjusted bitrate targets.
    for (size_t ti = 0; ti < kMaxTemporalStreams; ++ti) {
      const uint32_t layer_bitrate_bps = adjusted_allocation.GetBitrate(si, ti);
      // Overshoot detector may not exist, eg for ScreenshareLayers case.
      if (layer_bitrate_bps > 0 && overshoot_detectors_[si][ti]) {
        // Number of frames in this layer alone is not cumulative, so
        // subtract fps from any low temporal layer.
        const double fps_fraction =
            static_cast<double>(
                current_fps_allocation_[si][ti] -
                (ti == 0 ? 0 : current_fps_allocation_[si][ti - 1])) /
            VideoEncoder::EncoderInfo::kMaxFramerateFraction;

        if (fps_fraction <= 0.0) {
          RTC_LOG(LS_WARNING)
              << "Encoder config has temporal layer with non-zero bitrate "
                 "allocation but zero framerate allocation.";
          continue;
        }

        overshoot_detectors_[si][ti]->SetTargetRate(
            DataRate::BitsPerSec(layer_bitrate_bps),
            fps_fraction * rates.framerate_fps, now_ms);
      }
    }
  }

  // Since no spatial layers or streams are toggled by the adjustment
  // bw-limited flag stays the same.
  adjusted_allocation.set_bw_limited(rates.bitrate.is_bw_limited());

  return adjusted_allocation;
}

void EncoderBitrateAdjuster::OnEncoderInfo(
    const VideoEncoder::EncoderInfo& encoder_info) {
  // Copy allocation into current state and re-allocate.
  for (size_t si = 0; si < kMaxSpatialLayers; ++si) {
    current_fps_allocation_[si] = encoder_info.fps_allocation[si];
  }

  // Trigger re-allocation so that overshoot detectors have correct targets.
  AdjustRateAllocation(current_rate_control_parameters_);
}

void EncoderBitrateAdjuster::OnEncodedFrame(DataSize size,
                                            int spatial_index,
                                            int temporal_index) {
  ++frames_since_layout_change_;
  // Detectors may not exist, for instance if ScreenshareLayers is used.
  auto& detector = overshoot_detectors_[spatial_index][temporal_index];
  if (detector) {
    detector->OnEncodedFrame(size.bytes(), rtc::TimeMillis());
  }
}

void EncoderBitrateAdjuster::Reset() {
  for (size_t si = 0; si < kMaxSpatialLayers; ++si) {
    for (size_t ti = 0; ti < kMaxTemporalStreams; ++ti) {
      overshoot_detectors_[si][ti].reset();
    }
  }
  // Call AdjustRateAllocation() with the last know bitrate allocation, so that
  // the appropriate overuse detectors are immediately re-created.
  AdjustRateAllocation(current_rate_control_parameters_);
}

}  // namespace webrtc
