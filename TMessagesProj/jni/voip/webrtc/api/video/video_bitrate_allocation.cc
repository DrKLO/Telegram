/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/video/video_bitrate_allocation.h"

#include <cstdint>

#include "rtc_base/checks.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "rtc_base/strings/string_builder.h"

namespace webrtc {

VideoBitrateAllocation::VideoBitrateAllocation()
    : sum_(0), is_bw_limited_(false) {}

bool VideoBitrateAllocation::SetBitrate(size_t spatial_index,
                                        size_t temporal_index,
                                        uint32_t bitrate_bps) {
  RTC_CHECK_LT(spatial_index, kMaxSpatialLayers);
  RTC_CHECK_LT(temporal_index, kMaxTemporalStreams);
  int64_t new_bitrate_sum_bps = sum_;
  absl::optional<uint32_t>& layer_bitrate =
      bitrates_[spatial_index][temporal_index];
  if (layer_bitrate) {
    RTC_DCHECK_LE(*layer_bitrate, sum_);
    new_bitrate_sum_bps -= *layer_bitrate;
  }
  new_bitrate_sum_bps += bitrate_bps;
  if (new_bitrate_sum_bps > kMaxBitrateBps)
    return false;

  layer_bitrate = bitrate_bps;
  sum_ = rtc::dchecked_cast<uint32_t>(new_bitrate_sum_bps);
  return true;
}

bool VideoBitrateAllocation::HasBitrate(size_t spatial_index,
                                        size_t temporal_index) const {
  RTC_CHECK_LT(spatial_index, kMaxSpatialLayers);
  RTC_CHECK_LT(temporal_index, kMaxTemporalStreams);
  return bitrates_[spatial_index][temporal_index].has_value();
}

uint32_t VideoBitrateAllocation::GetBitrate(size_t spatial_index,
                                            size_t temporal_index) const {
  RTC_CHECK_LT(spatial_index, kMaxSpatialLayers);
  RTC_CHECK_LT(temporal_index, kMaxTemporalStreams);
  return bitrates_[spatial_index][temporal_index].value_or(0);
}

// Whether the specific spatial layers has the bitrate set in any of its
// temporal layers.
bool VideoBitrateAllocation::IsSpatialLayerUsed(size_t spatial_index) const {
  RTC_CHECK_LT(spatial_index, kMaxSpatialLayers);
  for (size_t i = 0; i < kMaxTemporalStreams; ++i) {
    if (bitrates_[spatial_index][i].has_value())
      return true;
  }
  return false;
}

// Get the sum of all the temporal layer for a specific spatial layer.
uint32_t VideoBitrateAllocation::GetSpatialLayerSum(
    size_t spatial_index) const {
  RTC_CHECK_LT(spatial_index, kMaxSpatialLayers);
  return GetTemporalLayerSum(spatial_index, kMaxTemporalStreams - 1);
}

uint32_t VideoBitrateAllocation::GetTemporalLayerSum(
    size_t spatial_index,
    size_t temporal_index) const {
  RTC_CHECK_LT(spatial_index, kMaxSpatialLayers);
  RTC_CHECK_LT(temporal_index, kMaxTemporalStreams);
  uint32_t sum = 0;
  for (size_t i = 0; i <= temporal_index; ++i) {
    sum += bitrates_[spatial_index][i].value_or(0);
  }
  return sum;
}

std::vector<uint32_t> VideoBitrateAllocation::GetTemporalLayerAllocation(
    size_t spatial_index) const {
  RTC_CHECK_LT(spatial_index, kMaxSpatialLayers);
  std::vector<uint32_t> temporal_rates;

  // Find the highest temporal layer with a defined bitrate in order to
  // determine the size of the temporal layer allocation.
  for (size_t i = kMaxTemporalStreams; i > 0; --i) {
    if (bitrates_[spatial_index][i - 1].has_value()) {
      temporal_rates.resize(i);
      break;
    }
  }

  for (size_t i = 0; i < temporal_rates.size(); ++i) {
    temporal_rates[i] = bitrates_[spatial_index][i].value_or(0);
  }

  return temporal_rates;
}

std::vector<absl::optional<VideoBitrateAllocation>>
VideoBitrateAllocation::GetSimulcastAllocations() const {
  std::vector<absl::optional<VideoBitrateAllocation>> bitrates;
  for (size_t si = 0; si < kMaxSpatialLayers; ++si) {
    absl::optional<VideoBitrateAllocation> layer_bitrate;
    if (IsSpatialLayerUsed(si)) {
      layer_bitrate = VideoBitrateAllocation();
      for (int tl = 0; tl < kMaxTemporalStreams; ++tl) {
        if (HasBitrate(si, tl))
          layer_bitrate->SetBitrate(0, tl, GetBitrate(si, tl));
      }
    }
    bitrates.push_back(layer_bitrate);
  }
  return bitrates;
}

bool VideoBitrateAllocation::operator==(
    const VideoBitrateAllocation& other) const {
  for (size_t si = 0; si < kMaxSpatialLayers; ++si) {
    for (size_t ti = 0; ti < kMaxTemporalStreams; ++ti) {
      if (bitrates_[si][ti] != other.bitrates_[si][ti])
        return false;
    }
  }
  return true;
}

std::string VideoBitrateAllocation::ToString() const {
  if (sum_ == 0)
    return "VideoBitrateAllocation [ [] ]";

  // Max string length in practice is 260, but let's have some overhead and
  // round up to nearest power of two.
  char string_buf[512];
  rtc::SimpleStringBuilder ssb(string_buf);

  ssb << "VideoBitrateAllocation [";
  uint32_t spatial_cumulator = 0;
  for (size_t si = 0; si < kMaxSpatialLayers; ++si) {
    RTC_DCHECK_LE(spatial_cumulator, sum_);
    if (spatial_cumulator == sum_)
      break;

    const uint32_t layer_sum = GetSpatialLayerSum(si);
    if (layer_sum == sum_ && si == 0) {
      ssb << " [";
    } else {
      if (si > 0)
        ssb << ",";
      ssb << '\n' << "  [";
    }
    spatial_cumulator += layer_sum;

    uint32_t temporal_cumulator = 0;
    for (size_t ti = 0; ti < kMaxTemporalStreams; ++ti) {
      RTC_DCHECK_LE(temporal_cumulator, layer_sum);
      if (temporal_cumulator == layer_sum)
        break;

      if (ti > 0)
        ssb << ", ";

      uint32_t bitrate = bitrates_[si][ti].value_or(0);
      ssb << bitrate;
      temporal_cumulator += bitrate;
    }
    ssb << "]";
  }

  RTC_DCHECK_EQ(spatial_cumulator, sum_);
  ssb << " ]";
  return ssb.str();
}

}  // namespace webrtc
