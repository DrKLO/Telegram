/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtp_video_layers_allocation_extension.h"

#include <stddef.h>
#include <stdint.h>

#include "absl/algorithm/container.h"
#include "api/video/video_layers_allocation.h"
#include "modules/rtp_rtcp/source/byte_io.h"
#include "modules/rtp_rtcp/source/leb128.h"
#include "rtc_base/checks.h"

namespace webrtc {

constexpr RTPExtensionType RtpVideoLayersAllocationExtension::kId;

namespace {

constexpr int kMaxNumRtpStreams = 4;

bool AllocationIsValid(const VideoLayersAllocation& allocation) {
  // Since all multivalue fields are stored in (rtp_stream_id, spatial_id) order
  // assume `allocation.active_spatial_layers` is already sorted. It is simpler
  // to assemble it in the sorted way than to resort during serialization.
  if (!absl::c_is_sorted(
          allocation.active_spatial_layers,
          [](const VideoLayersAllocation::SpatialLayer& lhs,
             const VideoLayersAllocation::SpatialLayer& rhs) {
            return std::make_tuple(lhs.rtp_stream_index, lhs.spatial_id) <
                   std::make_tuple(rhs.rtp_stream_index, rhs.spatial_id);
          })) {
    return false;
  }

  int max_rtp_stream_idx = 0;
  for (const auto& spatial_layer : allocation.active_spatial_layers) {
    if (spatial_layer.rtp_stream_index < 0 ||
        spatial_layer.rtp_stream_index >= 4) {
      return false;
    }
    if (spatial_layer.spatial_id < 0 || spatial_layer.spatial_id >= 4) {
      return false;
    }
    if (spatial_layer.target_bitrate_per_temporal_layer.empty() ||
        spatial_layer.target_bitrate_per_temporal_layer.size() > 4) {
      return false;
    }
    if (max_rtp_stream_idx < spatial_layer.rtp_stream_index) {
      max_rtp_stream_idx = spatial_layer.rtp_stream_index;
    }
    if (allocation.resolution_and_frame_rate_is_valid) {
      if (spatial_layer.width <= 0) {
        return false;
      }
      if (spatial_layer.height <= 0) {
        return false;
      }
      if (spatial_layer.frame_rate_fps > 255) {
        return false;
      }
    }
  }
  if (allocation.rtp_stream_index < 0 ||
      (!allocation.active_spatial_layers.empty() &&
       allocation.rtp_stream_index > max_rtp_stream_idx)) {
    return false;
  }
  return true;
}

struct SpatialLayersBitmasks {
  int max_rtp_stream_id = 0;
  uint8_t spatial_layer_bitmask[kMaxNumRtpStreams] = {};
  bool bitmasks_are_the_same = true;
};

SpatialLayersBitmasks SpatialLayersBitmasksPerRtpStream(
    const VideoLayersAllocation& allocation) {
  RTC_DCHECK(AllocationIsValid(allocation));
  SpatialLayersBitmasks result;
  for (const auto& layer : allocation.active_spatial_layers) {
    result.spatial_layer_bitmask[layer.rtp_stream_index] |=
        (1u << layer.spatial_id);
    if (result.max_rtp_stream_id < layer.rtp_stream_index) {
      result.max_rtp_stream_id = layer.rtp_stream_index;
    }
  }
  for (int i = 1; i <= result.max_rtp_stream_id; ++i) {
    if (result.spatial_layer_bitmask[i] != result.spatial_layer_bitmask[0]) {
      result.bitmasks_are_the_same = false;
      break;
    }
  }
  return result;
}

}  // namespace

// See /docs/native-code/rtp-rtpext/video-layers-allocation00/README.md
// for the description of the format.

bool RtpVideoLayersAllocationExtension::Write(
    rtc::ArrayView<uint8_t> data,
    const VideoLayersAllocation& allocation) {
  RTC_DCHECK(AllocationIsValid(allocation));
  RTC_DCHECK_GE(data.size(), ValueSize(allocation));

  if (allocation.active_spatial_layers.empty()) {
    data[0] = 0;
    return true;
  }

  SpatialLayersBitmasks slb = SpatialLayersBitmasksPerRtpStream(allocation);
  uint8_t* write_at = data.data();
  // First half of the header byte.
  *write_at = (allocation.rtp_stream_index << 6);
  // number of rtp stream - 1 is the same as the maximum rtp_stream_id.
  *write_at |= slb.max_rtp_stream_id << 4;
  if (slb.bitmasks_are_the_same) {
    // Second half of the header byte.
    *write_at |= slb.spatial_layer_bitmask[0];
  } else {
    // spatial layer bitmasks when they are different for different RTP streams.
    *++write_at =
        (slb.spatial_layer_bitmask[0] << 4) | slb.spatial_layer_bitmask[1];
    if (slb.max_rtp_stream_id >= 2) {
      *++write_at =
          (slb.spatial_layer_bitmask[2] << 4) | slb.spatial_layer_bitmask[3];
    }
  }
  ++write_at;

  {  // Number of temporal layers.
    int bit_offset = 8;
    *write_at = 0;
    for (const auto& layer : allocation.active_spatial_layers) {
      if (bit_offset == 0) {
        bit_offset = 6;
        *++write_at = 0;
      } else {
        bit_offset -= 2;
      }
      *write_at |=
          ((layer.target_bitrate_per_temporal_layer.size() - 1) << bit_offset);
    }
    ++write_at;
  }

  // Target bitrates.
  for (const auto& spatial_layer : allocation.active_spatial_layers) {
    for (const DataRate& bitrate :
         spatial_layer.target_bitrate_per_temporal_layer) {
      write_at += WriteLeb128(bitrate.kbps(), write_at);
    }
  }

  if (allocation.resolution_and_frame_rate_is_valid) {
    for (const auto& spatial_layer : allocation.active_spatial_layers) {
      ByteWriter<uint16_t>::WriteBigEndian(write_at, spatial_layer.width - 1);
      write_at += 2;
      ByteWriter<uint16_t>::WriteBigEndian(write_at, spatial_layer.height - 1);
      write_at += 2;
      *write_at = spatial_layer.frame_rate_fps;
      ++write_at;
    }
  }
  RTC_DCHECK_EQ(write_at - data.data(), ValueSize(allocation));
  return true;
}

bool RtpVideoLayersAllocationExtension::Parse(
    rtc::ArrayView<const uint8_t> data,
    VideoLayersAllocation* allocation) {
  if (data.empty() || allocation == nullptr) {
    return false;
  }

  allocation->active_spatial_layers.clear();

  const uint8_t* read_at = data.data();
  const uint8_t* const end = data.data() + data.size();

  if (data.size() == 1 && *read_at == 0) {
    allocation->rtp_stream_index = 0;
    allocation->resolution_and_frame_rate_is_valid = true;
    return AllocationIsValid(*allocation);
  }

  // Header byte.
  allocation->rtp_stream_index = *read_at >> 6;
  int num_rtp_streams = 1 + ((*read_at >> 4) & 0b11);
  uint8_t spatial_layers_bitmasks[kMaxNumRtpStreams];
  spatial_layers_bitmasks[0] = *read_at & 0b1111;

  if (spatial_layers_bitmasks[0] != 0) {
    for (int i = 1; i < num_rtp_streams; ++i) {
      spatial_layers_bitmasks[i] = spatial_layers_bitmasks[0];
    }
  } else {
    // Spatial layer bitmasks when they are different for different RTP streams.
    if (++read_at == end) {
      return false;
    }
    spatial_layers_bitmasks[0] = *read_at >> 4;
    spatial_layers_bitmasks[1] = *read_at & 0b1111;
    if (num_rtp_streams > 2) {
      if (++read_at == end) {
        return false;
      }
      spatial_layers_bitmasks[2] = *read_at >> 4;
      spatial_layers_bitmasks[3] = *read_at & 0b1111;
    }
  }
  if (++read_at == end) {
    return false;
  }

  // Read number of temporal layers,
  // Create `allocation->active_spatial_layers` while iterating though it.
  int bit_offset = 8;
  for (int stream_idx = 0; stream_idx < num_rtp_streams; ++stream_idx) {
    for (int sid = 0; sid < VideoLayersAllocation::kMaxSpatialIds; ++sid) {
      if ((spatial_layers_bitmasks[stream_idx] & (1 << sid)) == 0) {
        continue;
      }

      if (bit_offset == 0) {
        bit_offset = 6;
        if (++read_at == end) {
          return false;
        }
      } else {
        bit_offset -= 2;
      }
      int num_temporal_layers = 1 + ((*read_at >> bit_offset) & 0b11);
      allocation->active_spatial_layers.emplace_back();
      auto& layer = allocation->active_spatial_layers.back();
      layer.rtp_stream_index = stream_idx;
      layer.spatial_id = sid;
      layer.target_bitrate_per_temporal_layer.resize(num_temporal_layers,
                                                     DataRate::Zero());
    }
  }
  if (++read_at == end) {
    return false;
  }

  // Target bitrates.
  for (auto& layer : allocation->active_spatial_layers) {
    for (DataRate& rate : layer.target_bitrate_per_temporal_layer) {
      uint64_t bitrate_kbps = ReadLeb128(read_at, end);
      // bitrate_kbps might represent larger values than DataRate type,
      // discard unreasonably large values.
      if (read_at == nullptr || bitrate_kbps > 1'000'000) {
        return false;
      }
      rate = DataRate::KilobitsPerSec(bitrate_kbps);
    }
  }

  if (read_at == end) {
    allocation->resolution_and_frame_rate_is_valid = false;
    return AllocationIsValid(*allocation);
  }

  if (read_at + 5 * allocation->active_spatial_layers.size() != end) {
    // data is left, but it size is not what can be used for resolutions and
    // framerates.
    return false;
  }
  allocation->resolution_and_frame_rate_is_valid = true;
  for (auto& layer : allocation->active_spatial_layers) {
    layer.width = 1 + ByteReader<uint16_t, 2>::ReadBigEndian(read_at);
    read_at += 2;
    layer.height = 1 + ByteReader<uint16_t, 2>::ReadBigEndian(read_at);
    read_at += 2;
    layer.frame_rate_fps = *read_at;
    ++read_at;
  }

  return AllocationIsValid(*allocation);
}

size_t RtpVideoLayersAllocationExtension::ValueSize(
    const VideoLayersAllocation& allocation) {
  if (allocation.active_spatial_layers.empty()) {
    return 1;
  }
  size_t result = 1;  // header
  SpatialLayersBitmasks slb = SpatialLayersBitmasksPerRtpStream(allocation);
  if (!slb.bitmasks_are_the_same) {
    ++result;
    if (slb.max_rtp_stream_id >= 2) {
      ++result;
    }
  }
  // 2 bits per active spatial layer, rounded up to full byte, i.e.
  // 0.25 byte per active spatial layer.
  result += (allocation.active_spatial_layers.size() + 3) / 4;
  for (const auto& spatial_layer : allocation.active_spatial_layers) {
    for (DataRate value : spatial_layer.target_bitrate_per_temporal_layer) {
      result += Leb128Size(value.kbps());
    }
  }
  if (allocation.resolution_and_frame_rate_is_valid) {
    result += 5 * allocation.active_spatial_layers.size();
  }
  return result;
}

}  // namespace webrtc
