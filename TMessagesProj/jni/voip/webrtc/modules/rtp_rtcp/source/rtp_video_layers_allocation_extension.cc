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

#include <limits>

#include "api/video/video_layers_allocation.h"
#include "rtc_base/bit_buffer.h"

namespace webrtc {

constexpr RTPExtensionType RtpVideoLayersAllocationExtension::kId;
constexpr const char RtpVideoLayersAllocationExtension::kUri[];

namespace {

// Counts the number of bits used in the binary representation of val.
size_t CountBits(uint64_t val) {
  size_t bit_count = 0;
  while (val != 0) {
    bit_count++;
    val >>= 1;
  }
  return bit_count;
}

// Counts the number of bits used if `val`is encoded using unsigned exponential
// Golomb encoding.
// TODO(bugs.webrtc.org/12000): Move to bit_buffer.cc if Golomb encoding is used
// in the final version.
size_t SizeExponentialGolomb(uint32_t val) {
  if (val == std::numeric_limits<uint32_t>::max()) {
    return 0;
  }
  uint64_t val_to_encode = static_cast<uint64_t>(val) + 1;
  return CountBits(val_to_encode) * 2 - 1;
}

}  // namespace

// TODO(bugs.webrtc.org/12000): Review and revise the content and encoding of
// this extension. This is an experimental first version.

//  0                   1                   2
//  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// | NS|RSID|T|X|Res| Bit encoded data...
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// NS: Number of spatial layers/simulcast streams - 1. 2 bits, thus allowing
// passing number of layers/streams up-to 4.
// RSID: RTP stream id this allocation is sent on, numbered from 0. 2 bits.
// T: indicates if all spatial layers have the same amount of temporal layers.
// X: indicates if resolution and frame rate per spatial layer is present.
// Res: 2 bits reserved for future use.
// Bit encoded data: consists of following fields written in order:
//  1) T=1: Nt - 2-bit value of number of temporal layers - 1
//     T=0: NS 2-bit values of numbers of temporal layers - 1 for all spatial
//     layers from lower to higher.
//  2) Bitrates:
//     One value for each spatial x temporal layer.
//     Format: RSID (2-bit) SID(2-bit),folowed by bitrate for all temporal
//     layers for the RSID,SID tuple. All bitrates are in kbps. All bitrates are
//     total required bitrate to receive the corresponding layer, i.e. in
//     simulcast mode they include only corresponding spatial layer, in full-svc
//     all lower spatial layers are included. All lower temporal layers are also
//     included. All bitrates are written using unsigned Exponential Golomb
//     encoding.
//  3) [only if X bit is set]. Encoded width, 16-bit, height, 16-bit,
//    max frame rate 8-bit per spatial layer in order from lower to higher.

bool RtpVideoLayersAllocationExtension::Write(
    rtc::ArrayView<uint8_t> data,
    const VideoLayersAllocation& allocation) {
  RTC_DCHECK_LT(allocation.rtp_stream_index,
                VideoLayersAllocation::kMaxSpatialIds);
  RTC_DCHECK_GE(data.size(), ValueSize(allocation));
  rtc::BitBufferWriter writer(data.data(), data.size());

  // NS:
  if (allocation.active_spatial_layers.empty())
    return false;
  writer.WriteBits(allocation.active_spatial_layers.size() - 1, 2);

  // RSID:
  writer.WriteBits(allocation.rtp_stream_index, 2);

  // T:
  bool num_tls_is_the_same = true;
  size_t first_layers_number_of_temporal_layers =
      allocation.active_spatial_layers.front()
          .target_bitrate_per_temporal_layer.size();
  for (const auto& spatial_layer : allocation.active_spatial_layers) {
    if (first_layers_number_of_temporal_layers !=
        spatial_layer.target_bitrate_per_temporal_layer.size()) {
      num_tls_is_the_same = false;
      break;
    }
  }
  writer.WriteBits(num_tls_is_the_same ? 1 : 0, 1);

  // X:
  writer.WriteBits(allocation.resolution_and_frame_rate_is_valid ? 1 : 0, 1);

  // RESERVED:
  writer.WriteBits(/*val=*/0, /*bit_count=*/2);

  if (num_tls_is_the_same) {
    writer.WriteBits(first_layers_number_of_temporal_layers - 1, 2);
  } else {
    for (const auto& spatial_layer : allocation.active_spatial_layers) {
      writer.WriteBits(
          spatial_layer.target_bitrate_per_temporal_layer.size() - 1, 2);
    }
  }

  for (const auto& spatial_layer : allocation.active_spatial_layers) {
    writer.WriteBits(spatial_layer.rtp_stream_index, 2);
    writer.WriteBits(spatial_layer.spatial_id, 2);
    for (const DataRate& bitrate :
         spatial_layer.target_bitrate_per_temporal_layer) {
      writer.WriteExponentialGolomb(bitrate.kbps());
    }
  }

  if (allocation.resolution_and_frame_rate_is_valid) {
    for (const auto& spatial_layer : allocation.active_spatial_layers) {
      writer.WriteUInt16(spatial_layer.width);
      writer.WriteUInt16(spatial_layer.height);
      writer.WriteUInt8(spatial_layer.frame_rate_fps);
    }
  }
  return true;
}

bool RtpVideoLayersAllocationExtension::Parse(
    rtc::ArrayView<const uint8_t> data,
    VideoLayersAllocation* allocation) {
  if (data.size() == 0)
    return false;
  rtc::BitBuffer reader(data.data(), data.size());
  if (!allocation)
    return false;
  allocation->active_spatial_layers.clear();

  uint32_t val;
  // NS:
  if (!reader.ReadBits(&val, 2))
    return false;
  int active_spatial_layers = val + 1;

  // RSID:
  if (!reader.ReadBits(&val, 2))
    return false;
  allocation->rtp_stream_index = val;

  // T:
  if (!reader.ReadBits(&val, 1))
    return false;
  bool num_tls_is_constant = (val == 1);

  // X:
  if (!reader.ReadBits(&val, 1))
    return false;
  allocation->resolution_and_frame_rate_is_valid = (val == 1);

  // RESERVED:
  if (!reader.ReadBits(&val, 2))
    return false;

  int number_of_temporal_layers[VideoLayersAllocation::kMaxSpatialIds];
  if (num_tls_is_constant) {
    if (!reader.ReadBits(&val, 2))
      return false;
    for (int sl_idx = 0; sl_idx < active_spatial_layers; ++sl_idx) {
      number_of_temporal_layers[sl_idx] = val + 1;
    }
  } else {
    for (int sl_idx = 0; sl_idx < active_spatial_layers; ++sl_idx) {
      if (!reader.ReadBits(&val, 2))
        return false;
      number_of_temporal_layers[sl_idx] = val + 1;
      if (number_of_temporal_layers[sl_idx] >
          VideoLayersAllocation::kMaxTemporalIds)
        return false;
    }
  }

  for (int sl_idx = 0; sl_idx < active_spatial_layers; ++sl_idx) {
    allocation->active_spatial_layers.emplace_back();
    auto& spatial_layer = allocation->active_spatial_layers.back();
    auto& temporal_layers = spatial_layer.target_bitrate_per_temporal_layer;
    if (!reader.ReadBits(&val, 2))
      return false;
    spatial_layer.rtp_stream_index = val;
    if (!reader.ReadBits(&val, 2))
      return false;
    spatial_layer.spatial_id = val;
    for (int tl_idx = 0; tl_idx < number_of_temporal_layers[sl_idx]; ++tl_idx) {
      reader.ReadExponentialGolomb(&val);
      temporal_layers.push_back(DataRate::KilobitsPerSec(val));
    }
  }

  if (allocation->resolution_and_frame_rate_is_valid) {
    for (auto& spatial_layer : allocation->active_spatial_layers) {
      if (!reader.ReadUInt16(&spatial_layer.width))
        return false;
      if (!reader.ReadUInt16(&spatial_layer.height))
        return false;
      if (!reader.ReadUInt8(&spatial_layer.frame_rate_fps))
        return false;
    }
  }
  return true;
}

size_t RtpVideoLayersAllocationExtension::ValueSize(
    const VideoLayersAllocation& allocation) {
  if (allocation.active_spatial_layers.empty()) {
    return 0;
  }
  size_t size_in_bits = 8;  // Fixed first byte.¨
  bool num_tls_is_the_same = true;
  size_t first_layers_number_of_temporal_layers =
      allocation.active_spatial_layers.front()
          .target_bitrate_per_temporal_layer.size();
  for (const auto& spatial_layer : allocation.active_spatial_layers) {
    if (first_layers_number_of_temporal_layers !=
        spatial_layer.target_bitrate_per_temporal_layer.size()) {
      num_tls_is_the_same = false;
    }
    size_in_bits += 4;  // RSID, SID tuple.
    for (const auto& bitrate :
         spatial_layer.target_bitrate_per_temporal_layer) {
      size_in_bits += SizeExponentialGolomb(bitrate.kbps());
    }
  }
  if (num_tls_is_the_same) {
    size_in_bits += 2;
  } else {
    for (const auto& spatial_layer : allocation.active_spatial_layers) {
      size_in_bits +=
          2 * spatial_layer.target_bitrate_per_temporal_layer.size();
    }
  }
  if (allocation.resolution_and_frame_rate_is_valid) {
    size_in_bits += allocation.active_spatial_layers.size() * 5 * 8;
  }
  return (size_in_bits + 7) / 8;
}

}  // namespace webrtc
