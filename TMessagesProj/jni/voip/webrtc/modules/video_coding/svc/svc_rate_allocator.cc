/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/svc/svc_rate_allocator.h"

#include <algorithm>
#include <cmath>
#include <cstddef>
#include <numeric>
#include <vector>

#include "absl/container/inlined_vector.h"
#include "modules/video_coding/svc/create_scalability_structure.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace {

constexpr float kSpatialLayeringRateScalingFactor = 0.55f;
constexpr float kTemporalLayeringRateScalingFactor = 0.55f;

struct ActiveSpatialLayers {
  size_t first = 0;
  size_t num = 0;
};

ActiveSpatialLayers GetActiveSpatialLayers(const VideoCodec& codec,
                                           size_t num_spatial_layers) {
  ActiveSpatialLayers active;
  for (active.first = 0; active.first < num_spatial_layers; ++active.first) {
    if (codec.spatialLayers[active.first].active) {
      break;
    }
  }

  size_t last_active_layer = active.first;
  for (; last_active_layer < num_spatial_layers; ++last_active_layer) {
    if (!codec.spatialLayers[last_active_layer].active) {
      break;
    }
  }
  active.num = last_active_layer - active.first;

  return active;
}

std::vector<DataRate> AdjustAndVerify(
    const VideoCodec& codec,
    size_t first_active_layer,
    const std::vector<DataRate>& spatial_layer_rates) {
  std::vector<DataRate> adjusted_spatial_layer_rates;
  // Keep track of rate that couldn't be applied to the previous layer due to
  // max bitrate constraint, try to pass it forward to the next one.
  DataRate excess_rate = DataRate::Zero();
  for (size_t sl_idx = 0; sl_idx < spatial_layer_rates.size(); ++sl_idx) {
    DataRate min_rate = DataRate::KilobitsPerSec(
        codec.spatialLayers[first_active_layer + sl_idx].minBitrate);
    DataRate max_rate = DataRate::KilobitsPerSec(
        codec.spatialLayers[first_active_layer + sl_idx].maxBitrate);

    DataRate layer_rate = spatial_layer_rates[sl_idx] + excess_rate;
    if (layer_rate < min_rate) {
      // Not enough rate to reach min bitrate for desired number of layers,
      // abort allocation.
      if (spatial_layer_rates.size() == 1) {
        return spatial_layer_rates;
      }
      return adjusted_spatial_layer_rates;
    }

    if (layer_rate <= max_rate) {
      excess_rate = DataRate::Zero();
      adjusted_spatial_layer_rates.push_back(layer_rate);
    } else {
      excess_rate = layer_rate - max_rate;
      adjusted_spatial_layer_rates.push_back(max_rate);
    }
  }

  return adjusted_spatial_layer_rates;
}

static std::vector<DataRate> SplitBitrate(size_t num_layers,
                                          DataRate total_bitrate,
                                          float rate_scaling_factor) {
  std::vector<DataRate> bitrates;

  double denominator = 0.0;
  for (size_t layer_idx = 0; layer_idx < num_layers; ++layer_idx) {
    denominator += std::pow(rate_scaling_factor, layer_idx);
  }

  double numerator = std::pow(rate_scaling_factor, num_layers - 1);
  for (size_t layer_idx = 0; layer_idx < num_layers; ++layer_idx) {
    bitrates.push_back(numerator * total_bitrate / denominator);
    numerator /= rate_scaling_factor;
  }

  const DataRate sum =
      std::accumulate(bitrates.begin(), bitrates.end(), DataRate::Zero());

  // Keep the sum of split bitrates equal to the total bitrate by adding or
  // subtracting bits, which were lost due to rounding, to the latest layer.
  if (total_bitrate > sum) {
    bitrates.back() += total_bitrate - sum;
  } else if (total_bitrate < sum) {
    bitrates.back() -= sum - total_bitrate;
  }

  return bitrates;
}

// Returns the minimum bitrate needed for `num_active_layers` spatial layers to
// become active using the configuration specified by `codec`.
DataRate FindLayerTogglingThreshold(const VideoCodec& codec,
                                    size_t first_active_layer,
                                    size_t num_active_layers) {
  if (num_active_layers == 1) {
    return DataRate::KilobitsPerSec(codec.spatialLayers[0].minBitrate);
  }

  if (codec.mode == VideoCodecMode::kRealtimeVideo) {
    DataRate lower_bound = DataRate::Zero();
    DataRate upper_bound = DataRate::Zero();
    if (num_active_layers > 1) {
      for (size_t i = 0; i < num_active_layers - 1; ++i) {
        lower_bound += DataRate::KilobitsPerSec(
            codec.spatialLayers[first_active_layer + i].minBitrate);
        upper_bound += DataRate::KilobitsPerSec(
            codec.spatialLayers[first_active_layer + i].maxBitrate);
      }
    }
    upper_bound += DataRate::KilobitsPerSec(
        codec.spatialLayers[first_active_layer + num_active_layers - 1]
            .minBitrate);

    // Do a binary search until upper and lower bound is the highest bitrate for
    // `num_active_layers` - 1 layers and lowest bitrate for `num_active_layers`
    // layers respectively.
    while (upper_bound - lower_bound > DataRate::BitsPerSec(1)) {
      DataRate try_rate = (lower_bound + upper_bound) / 2;
      if (AdjustAndVerify(codec, first_active_layer,
                          SplitBitrate(num_active_layers, try_rate,
                                       kSpatialLayeringRateScalingFactor))
              .size() == num_active_layers) {
        upper_bound = try_rate;
      } else {
        lower_bound = try_rate;
      }
    }
    return upper_bound;
  } else {
    DataRate toggling_rate = DataRate::Zero();
    for (size_t i = 0; i < num_active_layers - 1; ++i) {
      toggling_rate += DataRate::KilobitsPerSec(
          codec.spatialLayers[first_active_layer + i].targetBitrate);
    }
    toggling_rate += DataRate::KilobitsPerSec(
        codec.spatialLayers[first_active_layer + num_active_layers - 1]
            .minBitrate);
    return toggling_rate;
  }
}

}  // namespace

SvcRateAllocator::NumLayers SvcRateAllocator::GetNumLayers(
    const VideoCodec& codec) {
  NumLayers layers;
  if (!codec.ScalabilityMode().empty()) {
    if (auto structure = CreateScalabilityStructure(codec.ScalabilityMode())) {
      ScalableVideoController::StreamLayersConfig config =
          structure->StreamConfig();
      layers.spatial = config.num_spatial_layers;
      layers.temporal = config.num_temporal_layers;
      return layers;
    }
  }
  if (codec.codecType == kVideoCodecVP9) {
    layers.spatial = codec.VP9().numberOfSpatialLayers;
    layers.temporal = codec.VP9().numberOfTemporalLayers;
    return layers;
  }
  layers.spatial = 1;
  layers.temporal = 1;
  return layers;
}

SvcRateAllocator::SvcRateAllocator(const VideoCodec& codec)
    : codec_(codec),
      num_layers_(GetNumLayers(codec)),
      experiment_settings_(StableTargetRateExperiment::ParseFromFieldTrials()),
      cumulative_layer_start_bitrates_(GetLayerStartBitrates(codec)),
      last_active_layer_count_(0) {
  RTC_DCHECK_GT(num_layers_.spatial, 0);
  RTC_DCHECK_LE(num_layers_.spatial, kMaxSpatialLayers);
  RTC_DCHECK_GT(num_layers_.temporal, 0);
  RTC_DCHECK_LE(num_layers_.temporal, 3);
  for (size_t layer_idx = 0; layer_idx < num_layers_.spatial; ++layer_idx) {
    // Verify min <= target <= max.
    if (codec.spatialLayers[layer_idx].active) {
      RTC_DCHECK_GT(codec.spatialLayers[layer_idx].maxBitrate, 0);
      RTC_DCHECK_GE(codec.spatialLayers[layer_idx].maxBitrate,
                    codec.spatialLayers[layer_idx].minBitrate);
      RTC_DCHECK_GE(codec.spatialLayers[layer_idx].targetBitrate,
                    codec.spatialLayers[layer_idx].minBitrate);
      RTC_DCHECK_GE(codec.spatialLayers[layer_idx].maxBitrate,
                    codec.spatialLayers[layer_idx].targetBitrate);
    }
  }
}

VideoBitrateAllocation SvcRateAllocator::Allocate(
    VideoBitrateAllocationParameters parameters) {
  DataRate total_bitrate = parameters.total_bitrate;
  if (codec_.maxBitrate != 0) {
    total_bitrate =
        std::min(total_bitrate, DataRate::KilobitsPerSec(codec_.maxBitrate));
  }

  if (codec_.spatialLayers[0].targetBitrate == 0) {
    // Delegate rate distribution to encoder wrapper if bitrate thresholds
    // are not set.
    VideoBitrateAllocation bitrate_allocation;
    bitrate_allocation.SetBitrate(0, 0, total_bitrate.bps());
    return bitrate_allocation;
  }

  const ActiveSpatialLayers active_layers =
      GetActiveSpatialLayers(codec_, num_layers_.spatial);
  size_t num_spatial_layers = active_layers.num;

  if (num_spatial_layers == 0) {
    return VideoBitrateAllocation();  // All layers are deactivated.
  }

  // Figure out how many spatial layers should be active.
  if (experiment_settings_.IsEnabled() &&
      parameters.stable_bitrate > DataRate::Zero()) {
    double hysteresis_factor;
    if (codec_.mode == VideoCodecMode::kScreensharing) {
      hysteresis_factor = experiment_settings_.GetScreenshareHysteresisFactor();
    } else {
      hysteresis_factor = experiment_settings_.GetVideoHysteresisFactor();
    }

    DataRate stable_rate =
        std::min(parameters.total_bitrate, parameters.stable_bitrate);
    // First check if bitrate has grown large enough to enable new layers.
    size_t num_enabled_with_hysteresis =
        FindNumEnabledLayers(stable_rate / hysteresis_factor);
    if (num_enabled_with_hysteresis >= last_active_layer_count_) {
      num_spatial_layers = num_enabled_with_hysteresis;
    } else {
      // We could not enable new layers, check if any should be disabled.
      num_spatial_layers =
          std::min(last_active_layer_count_, FindNumEnabledLayers(stable_rate));
    }
  } else {
    num_spatial_layers = FindNumEnabledLayers(parameters.total_bitrate);
  }
  last_active_layer_count_ = num_spatial_layers;

  VideoBitrateAllocation allocation;
  if (codec_.mode == VideoCodecMode::kRealtimeVideo) {
    allocation = GetAllocationNormalVideo(total_bitrate, active_layers.first,
                                          num_spatial_layers);
  } else {
    allocation = GetAllocationScreenSharing(total_bitrate, active_layers.first,
                                            num_spatial_layers);
  }
  allocation.set_bw_limited(num_spatial_layers < active_layers.num);
  return allocation;
}

VideoBitrateAllocation SvcRateAllocator::GetAllocationNormalVideo(
    DataRate total_bitrate,
    size_t first_active_layer,
    size_t num_spatial_layers) const {
  std::vector<DataRate> spatial_layer_rates;
  if (num_spatial_layers == 0) {
    // Not enough rate for even the base layer. Force allocation at the total
    // bitrate anyway.
    num_spatial_layers = 1;
    spatial_layer_rates.push_back(total_bitrate);
  } else {
    spatial_layer_rates =
        AdjustAndVerify(codec_, first_active_layer,
                        SplitBitrate(num_spatial_layers, total_bitrate,
                                     kSpatialLayeringRateScalingFactor));
    RTC_DCHECK_EQ(spatial_layer_rates.size(), num_spatial_layers);
  }

  VideoBitrateAllocation bitrate_allocation;

  for (size_t sl_idx = 0; sl_idx < num_spatial_layers; ++sl_idx) {
    std::vector<DataRate> temporal_layer_rates =
        SplitBitrate(num_layers_.temporal, spatial_layer_rates[sl_idx],
                     kTemporalLayeringRateScalingFactor);

    // Distribute rate across temporal layers. Allocate more bits to lower
    // layers since they are used for prediction of higher layers and their
    // references are far apart.
    if (num_layers_.temporal == 1) {
      bitrate_allocation.SetBitrate(sl_idx + first_active_layer, 0,
                                    temporal_layer_rates[0].bps());
    } else if (num_layers_.temporal == 2) {
      bitrate_allocation.SetBitrate(sl_idx + first_active_layer, 0,
                                    temporal_layer_rates[1].bps());
      bitrate_allocation.SetBitrate(sl_idx + first_active_layer, 1,
                                    temporal_layer_rates[0].bps());
    } else {
      RTC_CHECK_EQ(num_layers_.temporal, 3);
      // In case of three temporal layers the high layer has two frames and the
      // middle layer has one frame within GOP (in between two consecutive low
      // layer frames). Thus high layer requires more bits (comparing pure
      // bitrate of layer, excluding bitrate of base layers) to keep quality on
      // par with lower layers.
      bitrate_allocation.SetBitrate(sl_idx + first_active_layer, 0,
                                    temporal_layer_rates[2].bps());
      bitrate_allocation.SetBitrate(sl_idx + first_active_layer, 1,
                                    temporal_layer_rates[0].bps());
      bitrate_allocation.SetBitrate(sl_idx + first_active_layer, 2,
                                    temporal_layer_rates[1].bps());
    }
  }

  return bitrate_allocation;
}

// Bit-rate is allocated in such a way, that the highest enabled layer will have
// between min and max bitrate, and all others will have exactly target
// bit-rate allocated.
VideoBitrateAllocation SvcRateAllocator::GetAllocationScreenSharing(
    DataRate total_bitrate,
    size_t first_active_layer,
    size_t num_spatial_layers) const {
  VideoBitrateAllocation bitrate_allocation;

  if (num_spatial_layers == 0 ||
      total_bitrate <
          DataRate::KilobitsPerSec(
              codec_.spatialLayers[first_active_layer].minBitrate)) {
    // Always enable at least one layer.
    bitrate_allocation.SetBitrate(first_active_layer, 0, total_bitrate.bps());
    return bitrate_allocation;
  }

  DataRate allocated_rate = DataRate::Zero();
  DataRate top_layer_rate = DataRate::Zero();
  size_t sl_idx;
  for (sl_idx = first_active_layer;
       sl_idx < first_active_layer + num_spatial_layers; ++sl_idx) {
    const DataRate min_rate =
        DataRate::KilobitsPerSec(codec_.spatialLayers[sl_idx].minBitrate);
    const DataRate target_rate =
        DataRate::KilobitsPerSec(codec_.spatialLayers[sl_idx].targetBitrate);

    if (allocated_rate + min_rate > total_bitrate) {
      // Use stable rate to determine if layer should be enabled.
      break;
    }

    top_layer_rate = std::min(target_rate, total_bitrate - allocated_rate);
    bitrate_allocation.SetBitrate(sl_idx, 0, top_layer_rate.bps());
    allocated_rate += top_layer_rate;
  }

  if (sl_idx > 0 && total_bitrate - allocated_rate > DataRate::Zero()) {
    // Add leftover to the last allocated layer.
    top_layer_rate = std::min(
        top_layer_rate + (total_bitrate - allocated_rate),
        DataRate::KilobitsPerSec(codec_.spatialLayers[sl_idx - 1].maxBitrate));
    bitrate_allocation.SetBitrate(sl_idx - 1, 0, top_layer_rate.bps());
  }

  return bitrate_allocation;
}

size_t SvcRateAllocator::FindNumEnabledLayers(DataRate target_rate) const {
  if (cumulative_layer_start_bitrates_.empty()) {
    return 0;
  }

  size_t num_enabled_layers = 0;
  for (DataRate start_rate : cumulative_layer_start_bitrates_) {
    // First layer is always enabled.
    if (num_enabled_layers == 0 || start_rate <= target_rate) {
      ++num_enabled_layers;
    } else {
      break;
    }
  }

  return num_enabled_layers;
}

DataRate SvcRateAllocator::GetMaxBitrate(const VideoCodec& codec) {
  const NumLayers num_layers = GetNumLayers(codec);
  const ActiveSpatialLayers active_layers =
      GetActiveSpatialLayers(codec, num_layers.spatial);

  DataRate max_bitrate = DataRate::Zero();
  for (size_t sl_idx = 0; sl_idx < active_layers.num; ++sl_idx) {
    max_bitrate += DataRate::KilobitsPerSec(
        codec.spatialLayers[active_layers.first + sl_idx].maxBitrate);
  }

  if (codec.maxBitrate != 0) {
    max_bitrate =
        std::min(max_bitrate, DataRate::KilobitsPerSec(codec.maxBitrate));
  }

  return max_bitrate;
}

DataRate SvcRateAllocator::GetPaddingBitrate(const VideoCodec& codec) {
  auto start_bitrate = GetLayerStartBitrates(codec);
  if (start_bitrate.empty()) {
    return DataRate::Zero();  // All layers are deactivated.
  }

  return start_bitrate.back();
}

absl::InlinedVector<DataRate, kMaxSpatialLayers>
SvcRateAllocator::GetLayerStartBitrates(const VideoCodec& codec) {
  absl::InlinedVector<DataRate, kMaxSpatialLayers> start_bitrates;
  const NumLayers num_layers = GetNumLayers(codec);
  const ActiveSpatialLayers active_layers =
      GetActiveSpatialLayers(codec, num_layers.spatial);
  DataRate last_rate = DataRate::Zero();
  for (size_t i = 1; i <= active_layers.num; ++i) {
    DataRate layer_toggling_rate =
        FindLayerTogglingThreshold(codec, active_layers.first, i);
    start_bitrates.push_back(layer_toggling_rate);
    RTC_DCHECK_LE(last_rate, layer_toggling_rate);
    last_rate = layer_toggling_rate;
  }
  return start_bitrates;
}

}  // namespace webrtc
