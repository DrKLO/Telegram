/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/utility/simulcast_rate_allocator.h"

#include <stdio.h>

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <numeric>
#include <string>
#include <tuple>
#include <vector>

#include "rtc_base/checks.h"
#include "rtc_base/experiments/rate_control_settings.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {
namespace {
// Ratio allocation between temporal streams:
// Values as required for the VP8 codec (accumulating).
static const float
    kLayerRateAllocation[kMaxTemporalStreams][kMaxTemporalStreams] = {
        {1.0f, 1.0f, 1.0f, 1.0f},  // 1 layer
        {0.6f, 1.0f, 1.0f, 1.0f},  // 2 layers {60%, 40%}
        {0.4f, 0.6f, 1.0f, 1.0f},  // 3 layers {40%, 20%, 40%}
        {0.25f, 0.4f, 0.6f, 1.0f}  // 4 layers {25%, 15%, 20%, 40%}
};

static const float kBaseHeavy3TlRateAllocation[kMaxTemporalStreams] = {
    0.6f, 0.8f, 1.0f, 1.0f  // 3 layers {60%, 20%, 20%}
};

const uint32_t kLegacyScreenshareTl0BitrateKbps = 200;
const uint32_t kLegacyScreenshareTl1BitrateKbps = 1000;
}  // namespace

float SimulcastRateAllocator::GetTemporalRateAllocation(
    int num_layers,
    int temporal_id,
    bool base_heavy_tl3_alloc) {
  RTC_CHECK_GT(num_layers, 0);
  RTC_CHECK_LE(num_layers, kMaxTemporalStreams);
  RTC_CHECK_GE(temporal_id, 0);
  RTC_CHECK_LT(temporal_id, num_layers);
  if (num_layers == 3 && base_heavy_tl3_alloc) {
    return kBaseHeavy3TlRateAllocation[temporal_id];
  }
  return kLayerRateAllocation[num_layers - 1][temporal_id];
}

SimulcastRateAllocator::SimulcastRateAllocator(const VideoCodec& codec)
    : codec_(codec),
      stable_rate_settings_(StableTargetRateExperiment::ParseFromFieldTrials()),
      rate_control_settings_(RateControlSettings::ParseFromFieldTrials()),
      legacy_conference_mode_(false) {}

SimulcastRateAllocator::~SimulcastRateAllocator() = default;

VideoBitrateAllocation SimulcastRateAllocator::Allocate(
    VideoBitrateAllocationParameters parameters) {
  VideoBitrateAllocation allocated_bitrates;
  DataRate stable_rate = parameters.total_bitrate;
  if (stable_rate_settings_.IsEnabled() &&
      parameters.stable_bitrate > DataRate::Zero()) {
    stable_rate = std::min(parameters.stable_bitrate, parameters.total_bitrate);
  }
  DistributeAllocationToSimulcastLayers(parameters.total_bitrate, stable_rate,
                                        &allocated_bitrates);
  DistributeAllocationToTemporalLayers(&allocated_bitrates);
  return allocated_bitrates;
}

void SimulcastRateAllocator::DistributeAllocationToSimulcastLayers(
    DataRate total_bitrate,
    DataRate stable_bitrate,
    VideoBitrateAllocation* allocated_bitrates) {
  DataRate left_in_total_allocation = total_bitrate;
  DataRate left_in_stable_allocation = stable_bitrate;

  if (codec_.maxBitrate) {
    DataRate max_rate = DataRate::KilobitsPerSec(codec_.maxBitrate);
    left_in_total_allocation = std::min(left_in_total_allocation, max_rate);
    left_in_stable_allocation = std::min(left_in_stable_allocation, max_rate);
  }

  if (codec_.numberOfSimulcastStreams == 0) {
    // No simulcast, just set the target as this has been capped already.
    if (codec_.active) {
      allocated_bitrates->SetBitrate(
          0, 0,
          std::max(DataRate::KilobitsPerSec(codec_.minBitrate),
                   left_in_total_allocation)
              .bps());
    }
    return;
  }

  // Sort the layers by maxFramerate, they might not always be from smallest
  // to biggest
  std::vector<size_t> layer_index(codec_.numberOfSimulcastStreams);
  std::iota(layer_index.begin(), layer_index.end(), 0);
  std::stable_sort(layer_index.begin(), layer_index.end(),
                   [this](size_t a, size_t b) {
                     return std::tie(codec_.simulcastStream[a].maxBitrate) <
                            std::tie(codec_.simulcastStream[b].maxBitrate);
                   });

  // Find the first active layer. We don't allocate to inactive layers.
  size_t active_layer = 0;
  for (; active_layer < codec_.numberOfSimulcastStreams; ++active_layer) {
    if (codec_.simulcastStream[layer_index[active_layer]].active) {
      // Found the first active layer.
      break;
    }
  }
  // All streams could be inactive, and nothing more to do.
  if (active_layer == codec_.numberOfSimulcastStreams) {
    return;
  }

  // Always allocate enough bitrate for the minimum bitrate of the first
  // active layer. Suspending below min bitrate is controlled outside the
  // codec implementation and is not overridden by this.
  DataRate min_rate = DataRate::KilobitsPerSec(
      codec_.simulcastStream[layer_index[active_layer]].minBitrate);
  left_in_total_allocation = std::max(left_in_total_allocation, min_rate);
  left_in_stable_allocation = std::max(left_in_stable_allocation, min_rate);

  // Begin by allocating bitrate to simulcast streams, putting all bitrate in
  // temporal layer 0. We'll then distribute this bitrate, across potential
  // temporal layers, when stream allocation is done.

  bool first_allocation = false;
  if (stream_enabled_.empty()) {
    // First time allocating, this means we should not include hysteresis in
    // case this is a reconfiguration of an existing enabled stream.
    first_allocation = true;
    stream_enabled_.resize(codec_.numberOfSimulcastStreams, false);
  }

  size_t top_active_layer = active_layer;
  // Allocate up to the target bitrate for each active simulcast layer.
  for (; active_layer < codec_.numberOfSimulcastStreams; ++active_layer) {
    const SpatialLayer& stream =
        codec_.simulcastStream[layer_index[active_layer]];
    if (!stream.active) {
      stream_enabled_[layer_index[active_layer]] = false;
      continue;
    }
    // If we can't allocate to the current layer we can't allocate to higher
    // layers because they require a higher minimum bitrate.
    DataRate min_bitrate = DataRate::KilobitsPerSec(stream.minBitrate);
    DataRate target_bitrate = DataRate::KilobitsPerSec(stream.targetBitrate);
    double hysteresis_factor =
        codec_.mode == VideoCodecMode::kRealtimeVideo
            ? stable_rate_settings_.GetVideoHysteresisFactor()
            : stable_rate_settings_.GetScreenshareHysteresisFactor();
    if (!first_allocation && !stream_enabled_[layer_index[active_layer]]) {
      min_bitrate = std::min(hysteresis_factor * min_bitrate, target_bitrate);
    }
    if (left_in_stable_allocation < min_bitrate) {
      allocated_bitrates->set_bw_limited(true);
      break;
    }

    // We are allocating to this layer so it is the current active allocation.
    top_active_layer = layer_index[active_layer];
    stream_enabled_[layer_index[active_layer]] = true;
    DataRate layer_rate = std::min(left_in_total_allocation, target_bitrate);
    allocated_bitrates->SetBitrate(layer_index[active_layer], 0,
                                   layer_rate.bps());
    left_in_total_allocation -= layer_rate;
    left_in_stable_allocation -=
        std::min(left_in_stable_allocation, target_bitrate);
  }

  // All layers above this one are not active.
  for (; active_layer < codec_.numberOfSimulcastStreams; ++active_layer) {
    stream_enabled_[layer_index[active_layer]] = false;
  }

  // Next, try allocate remaining bitrate, up to max bitrate, in top active
  // stream.
  // TODO(sprang): Allocate up to max bitrate for all layers once we have a
  //               better idea of possible performance implications.
  if (left_in_total_allocation > DataRate::Zero()) {
    const SpatialLayer& stream = codec_.simulcastStream[top_active_layer];
    DataRate initial_layer_rate = DataRate::BitsPerSec(
        allocated_bitrates->GetSpatialLayerSum(top_active_layer));
    DataRate additional_allocation = std::min(
        left_in_total_allocation,
        DataRate::KilobitsPerSec(stream.maxBitrate) - initial_layer_rate);
    allocated_bitrates->SetBitrate(
        top_active_layer, 0,
        (initial_layer_rate + additional_allocation).bps());
  }
}

void SimulcastRateAllocator::DistributeAllocationToTemporalLayers(
    VideoBitrateAllocation* allocated_bitrates_bps) const {
  const int num_spatial_streams =
      std::max(1, static_cast<int>(codec_.numberOfSimulcastStreams));

  // Finally, distribute the bitrate for the simulcast streams across the
  // available temporal layers.
  for (int simulcast_id = 0; simulcast_id < num_spatial_streams;
       ++simulcast_id) {
    uint32_t target_bitrate_kbps =
        allocated_bitrates_bps->GetBitrate(simulcast_id, 0) / 1000;
    if (target_bitrate_kbps == 0) {
      continue;
    }

    const uint32_t expected_allocated_bitrate_kbps = target_bitrate_kbps;
    RTC_DCHECK_EQ(
        target_bitrate_kbps,
        allocated_bitrates_bps->GetSpatialLayerSum(simulcast_id) / 1000);
    const int num_temporal_streams = NumTemporalStreams(simulcast_id);
    uint32_t max_bitrate_kbps;
    // Legacy temporal-layered only screenshare, or simulcast screenshare
    // with legacy mode for simulcast stream 0.
    if (codec_.mode == VideoCodecMode::kScreensharing &&
        legacy_conference_mode_ && simulcast_id == 0) {
      // TODO(holmer): This is a "temporary" hack for screensharing, where we
      // interpret the startBitrate as the encoder target bitrate. This is
      // to allow for a different max bitrate, so if the codec can't meet
      // the target we still allow it to overshoot up to the max before dropping
      // frames. This hack should be improved.
      max_bitrate_kbps =
          std::min(kLegacyScreenshareTl1BitrateKbps, target_bitrate_kbps);
      target_bitrate_kbps =
          std::min(kLegacyScreenshareTl0BitrateKbps, target_bitrate_kbps);
    } else if (num_spatial_streams == 1) {
      max_bitrate_kbps = codec_.maxBitrate;
    } else {
      max_bitrate_kbps = codec_.simulcastStream[simulcast_id].maxBitrate;
    }

    std::vector<uint32_t> tl_allocation;
    if (num_temporal_streams == 1) {
      tl_allocation.push_back(target_bitrate_kbps);
    } else {
      if (codec_.mode == VideoCodecMode::kScreensharing &&
          legacy_conference_mode_ && simulcast_id == 0) {
        tl_allocation = ScreenshareTemporalLayerAllocation(
            target_bitrate_kbps, max_bitrate_kbps, simulcast_id);
      } else {
        tl_allocation = DefaultTemporalLayerAllocation(
            target_bitrate_kbps, max_bitrate_kbps, simulcast_id);
      }
    }
    RTC_DCHECK_GT(tl_allocation.size(), 0);
    RTC_DCHECK_LE(tl_allocation.size(), num_temporal_streams);

    uint64_t tl_allocation_sum_kbps = 0;
    for (size_t tl_index = 0; tl_index < tl_allocation.size(); ++tl_index) {
      uint32_t layer_rate_kbps = tl_allocation[tl_index];
      if (layer_rate_kbps > 0) {
        allocated_bitrates_bps->SetBitrate(simulcast_id, tl_index,
                                           layer_rate_kbps * 1000);
      }
      tl_allocation_sum_kbps += layer_rate_kbps;
    }
    RTC_DCHECK_LE(tl_allocation_sum_kbps, expected_allocated_bitrate_kbps);
  }
}

std::vector<uint32_t> SimulcastRateAllocator::DefaultTemporalLayerAllocation(
    int bitrate_kbps,
    int max_bitrate_kbps,
    int simulcast_id) const {
  const size_t num_temporal_layers = NumTemporalStreams(simulcast_id);
  std::vector<uint32_t> bitrates;
  for (size_t i = 0; i < num_temporal_layers; ++i) {
    float layer_bitrate =
        bitrate_kbps *
        GetTemporalRateAllocation(
            num_temporal_layers, i,
            rate_control_settings_.Vp8BaseHeavyTl3RateAllocation());
    bitrates.push_back(static_cast<uint32_t>(layer_bitrate + 0.5));
  }

  // Allocation table is of aggregates, transform to individual rates.
  uint32_t sum = 0;
  for (size_t i = 0; i < num_temporal_layers; ++i) {
    uint32_t layer_bitrate = bitrates[i];
    RTC_DCHECK_LE(sum, bitrates[i]);
    bitrates[i] -= sum;
    sum = layer_bitrate;

    if (sum >= static_cast<uint32_t>(bitrate_kbps)) {
      // Sum adds up; any subsequent layers will be 0.
      bitrates.resize(i + 1);
      break;
    }
  }

  return bitrates;
}

std::vector<uint32_t>
SimulcastRateAllocator::ScreenshareTemporalLayerAllocation(
    int bitrate_kbps,
    int max_bitrate_kbps,
    int simulcast_id) const {
  if (simulcast_id > 0) {
    return DefaultTemporalLayerAllocation(bitrate_kbps, max_bitrate_kbps,
                                          simulcast_id);
  }
  std::vector<uint32_t> allocation;
  allocation.push_back(bitrate_kbps);
  if (max_bitrate_kbps > bitrate_kbps)
    allocation.push_back(max_bitrate_kbps - bitrate_kbps);
  return allocation;
}

const VideoCodec& webrtc::SimulcastRateAllocator::GetCodec() const {
  return codec_;
}

int SimulcastRateAllocator::NumTemporalStreams(size_t simulcast_id) const {
  return std::max<uint8_t>(
      1,
      codec_.codecType == kVideoCodecVP8 && codec_.numberOfSimulcastStreams == 0
          ? codec_.VP8().numberOfTemporalLayers
          : codec_.simulcastStream[simulcast_id].numberOfTemporalLayers);
}

void SimulcastRateAllocator::SetLegacyConferenceMode(bool enabled) {
  legacy_conference_mode_ = enabled;
}

}  // namespace webrtc
