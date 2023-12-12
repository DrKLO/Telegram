/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "common_video/h264/pps_parser.h"

#include <cstdint>
#include <limits>
#include <vector>

#include "absl/numeric/bits.h"
#include "common_video/h264/h264_common.h"
#include "rtc_base/bitstream_reader.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace {
constexpr int kMaxPicInitQpDeltaValue = 25;
constexpr int kMinPicInitQpDeltaValue = -26;
}  // namespace

// General note: this is based off the 02/2014 version of the H.264 standard.
// You can find it on this page:
// http://www.itu.int/rec/T-REC-H.264

absl::optional<PpsParser::PpsState> PpsParser::ParsePps(const uint8_t* data,
                                                        size_t length) {
  // First, parse out rbsp, which is basically the source buffer minus emulation
  // bytes (the last byte of a 0x00 0x00 0x03 sequence). RBSP is defined in
  // section 7.3.1 of the H.264 standard.
  return ParseInternal(H264::ParseRbsp(data, length));
}

bool PpsParser::ParsePpsIds(const uint8_t* data,
                            size_t length,
                            uint32_t* pps_id,
                            uint32_t* sps_id) {
  RTC_DCHECK(pps_id);
  RTC_DCHECK(sps_id);
  // First, parse out rbsp, which is basically the source buffer minus emulation
  // bytes (the last byte of a 0x00 0x00 0x03 sequence). RBSP is defined in
  // section 7.3.1 of the H.264 standard.
  std::vector<uint8_t> unpacked_buffer = H264::ParseRbsp(data, length);
  BitstreamReader reader(unpacked_buffer);
  *pps_id = reader.ReadExponentialGolomb();
  *sps_id = reader.ReadExponentialGolomb();
  return reader.Ok();
}

absl::optional<uint32_t> PpsParser::ParsePpsIdFromSlice(const uint8_t* data,
                                                        size_t length) {
  std::vector<uint8_t> unpacked_buffer = H264::ParseRbsp(data, length);
  BitstreamReader slice_reader(unpacked_buffer);

  // first_mb_in_slice: ue(v)
  slice_reader.ReadExponentialGolomb();
  // slice_type: ue(v)
  slice_reader.ReadExponentialGolomb();
  // pic_parameter_set_id: ue(v)
  uint32_t slice_pps_id = slice_reader.ReadExponentialGolomb();
  if (!slice_reader.Ok()) {
    return absl::nullopt;
  }
  return slice_pps_id;
}

absl::optional<PpsParser::PpsState> PpsParser::ParseInternal(
    rtc::ArrayView<const uint8_t> buffer) {
  BitstreamReader reader(buffer);
  PpsState pps;
  pps.id = reader.ReadExponentialGolomb();
  pps.sps_id = reader.ReadExponentialGolomb();

  // entropy_coding_mode_flag: u(1)
  pps.entropy_coding_mode_flag = reader.Read<bool>();
  // bottom_field_pic_order_in_frame_present_flag: u(1)
  pps.bottom_field_pic_order_in_frame_present_flag = reader.Read<bool>();

  // num_slice_groups_minus1: ue(v)
  uint32_t num_slice_groups_minus1 = reader.ReadExponentialGolomb();
  if (num_slice_groups_minus1 > 0) {
    // slice_group_map_type: ue(v)
    uint32_t slice_group_map_type = reader.ReadExponentialGolomb();
    if (slice_group_map_type == 0) {
      for (uint32_t i_group = 0;
           i_group <= num_slice_groups_minus1 && reader.Ok(); ++i_group) {
        // run_length_minus1[iGroup]: ue(v)
        reader.ReadExponentialGolomb();
      }
    } else if (slice_group_map_type == 1) {
      // TODO(sprang): Implement support for dispersed slice group map type.
      // See 8.2.2.2 Specification for dispersed slice group map type.
    } else if (slice_group_map_type == 2) {
      for (uint32_t i_group = 0;
           i_group <= num_slice_groups_minus1 && reader.Ok(); ++i_group) {
        // top_left[iGroup]: ue(v)
        reader.ReadExponentialGolomb();
        // bottom_right[iGroup]: ue(v)
        reader.ReadExponentialGolomb();
      }
    } else if (slice_group_map_type == 3 || slice_group_map_type == 4 ||
               slice_group_map_type == 5) {
      // slice_group_change_direction_flag: u(1)
      reader.ConsumeBits(1);
      // slice_group_change_rate_minus1: ue(v)
      reader.ReadExponentialGolomb();
    } else if (slice_group_map_type == 6) {
      // pic_size_in_map_units_minus1: ue(v)
      uint32_t pic_size_in_map_units = reader.ReadExponentialGolomb() + 1;
      int slice_group_id_bits = 1 + absl::bit_width(num_slice_groups_minus1);

      // slice_group_id: array of size pic_size_in_map_units, each element
      // is represented by ceil(log2(num_slice_groups_minus1 + 1)) bits.
      int64_t bits_to_consume =
          int64_t{slice_group_id_bits} * pic_size_in_map_units;
      if (!reader.Ok() || bits_to_consume > std::numeric_limits<int>::max()) {
        return absl::nullopt;
      }
      reader.ConsumeBits(bits_to_consume);
    }
  }
  // num_ref_idx_l0_default_active_minus1: ue(v)
  reader.ReadExponentialGolomb();
  // num_ref_idx_l1_default_active_minus1: ue(v)
  reader.ReadExponentialGolomb();
  // weighted_pred_flag: u(1)
  pps.weighted_pred_flag = reader.Read<bool>();
  // weighted_bipred_idc: u(2)
  pps.weighted_bipred_idc = reader.ReadBits(2);

  // pic_init_qp_minus26: se(v)
  pps.pic_init_qp_minus26 = reader.ReadSignedExponentialGolomb();
  // Sanity-check parsed value
  if (!reader.Ok() || pps.pic_init_qp_minus26 > kMaxPicInitQpDeltaValue ||
      pps.pic_init_qp_minus26 < kMinPicInitQpDeltaValue) {
    return absl::nullopt;
  }
  // pic_init_qs_minus26: se(v)
  reader.ReadExponentialGolomb();
  // chroma_qp_index_offset: se(v)
  reader.ReadExponentialGolomb();
  // deblocking_filter_control_present_flag: u(1)
  // constrained_intra_pred_flag: u(1)
  reader.ConsumeBits(2);
  // redundant_pic_cnt_present_flag: u(1)
  pps.redundant_pic_cnt_present_flag = reader.ReadBit();
  if (!reader.Ok()) {
    return absl::nullopt;
  }

  return pps;
}

}  // namespace webrtc
