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
#include <vector>

#include "common_video/h264/h264_common.h"
#include "rtc_base/bit_buffer.h"
#include "rtc_base/checks.h"

#define RETURN_EMPTY_ON_FAIL(x) \
  if (!(x)) {                   \
    return absl::nullopt;       \
  }

namespace {
const int kMaxPicInitQpDeltaValue = 25;
const int kMinPicInitQpDeltaValue = -26;
}  // namespace

namespace webrtc {

// General note: this is based off the 02/2014 version of the H.264 standard.
// You can find it on this page:
// http://www.itu.int/rec/T-REC-H.264

absl::optional<PpsParser::PpsState> PpsParser::ParsePps(const uint8_t* data,
                                                        size_t length) {
  // First, parse out rbsp, which is basically the source buffer minus emulation
  // bytes (the last byte of a 0x00 0x00 0x03 sequence). RBSP is defined in
  // section 7.3.1 of the H.264 standard.
  std::vector<uint8_t> unpacked_buffer = H264::ParseRbsp(data, length);
  rtc::BitBuffer bit_buffer(unpacked_buffer.data(), unpacked_buffer.size());
  return ParseInternal(&bit_buffer);
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
  rtc::BitBuffer bit_buffer(unpacked_buffer.data(), unpacked_buffer.size());
  return ParsePpsIdsInternal(&bit_buffer, pps_id, sps_id);
}

absl::optional<uint32_t> PpsParser::ParsePpsIdFromSlice(const uint8_t* data,
                                                        size_t length) {
  std::vector<uint8_t> unpacked_buffer = H264::ParseRbsp(data, length);
  rtc::BitBuffer slice_reader(unpacked_buffer.data(), unpacked_buffer.size());

  uint32_t golomb_tmp;
  // first_mb_in_slice: ue(v)
  if (!slice_reader.ReadExponentialGolomb(&golomb_tmp))
    return absl::nullopt;
  // slice_type: ue(v)
  if (!slice_reader.ReadExponentialGolomb(&golomb_tmp))
    return absl::nullopt;
  // pic_parameter_set_id: ue(v)
  uint32_t slice_pps_id;
  if (!slice_reader.ReadExponentialGolomb(&slice_pps_id))
    return absl::nullopt;
  return slice_pps_id;
}

absl::optional<PpsParser::PpsState> PpsParser::ParseInternal(
    rtc::BitBuffer* bit_buffer) {
  PpsState pps;

  RETURN_EMPTY_ON_FAIL(ParsePpsIdsInternal(bit_buffer, &pps.id, &pps.sps_id));

  uint32_t bits_tmp;
  uint32_t golomb_ignored;
  // entropy_coding_mode_flag: u(1)
  uint32_t entropy_coding_mode_flag;
  RETURN_EMPTY_ON_FAIL(bit_buffer->ReadBits(&entropy_coding_mode_flag, 1));
  pps.entropy_coding_mode_flag = entropy_coding_mode_flag != 0;
  // bottom_field_pic_order_in_frame_present_flag: u(1)
  uint32_t bottom_field_pic_order_in_frame_present_flag;
  RETURN_EMPTY_ON_FAIL(
      bit_buffer->ReadBits(&bottom_field_pic_order_in_frame_present_flag, 1));
  pps.bottom_field_pic_order_in_frame_present_flag =
      bottom_field_pic_order_in_frame_present_flag != 0;

  // num_slice_groups_minus1: ue(v)
  uint32_t num_slice_groups_minus1;
  RETURN_EMPTY_ON_FAIL(
      bit_buffer->ReadExponentialGolomb(&num_slice_groups_minus1));
  if (num_slice_groups_minus1 > 0) {
    uint32_t slice_group_map_type;
    // slice_group_map_type: ue(v)
    RETURN_EMPTY_ON_FAIL(
        bit_buffer->ReadExponentialGolomb(&slice_group_map_type));
    if (slice_group_map_type == 0) {
      for (uint32_t i_group = 0; i_group <= num_slice_groups_minus1;
           ++i_group) {
        // run_length_minus1[iGroup]: ue(v)
        RETURN_EMPTY_ON_FAIL(
            bit_buffer->ReadExponentialGolomb(&golomb_ignored));
      }
    } else if (slice_group_map_type == 1) {
      // TODO(sprang): Implement support for dispersed slice group map type.
      // See 8.2.2.2 Specification for dispersed slice group map type.
    } else if (slice_group_map_type == 2) {
      for (uint32_t i_group = 0; i_group <= num_slice_groups_minus1;
           ++i_group) {
        // top_left[iGroup]: ue(v)
        RETURN_EMPTY_ON_FAIL(
            bit_buffer->ReadExponentialGolomb(&golomb_ignored));
        // bottom_right[iGroup]: ue(v)
        RETURN_EMPTY_ON_FAIL(
            bit_buffer->ReadExponentialGolomb(&golomb_ignored));
      }
    } else if (slice_group_map_type == 3 || slice_group_map_type == 4 ||
               slice_group_map_type == 5) {
      // slice_group_change_direction_flag: u(1)
      RETURN_EMPTY_ON_FAIL(bit_buffer->ReadBits(&bits_tmp, 1));
      // slice_group_change_rate_minus1: ue(v)
      RETURN_EMPTY_ON_FAIL(bit_buffer->ReadExponentialGolomb(&golomb_ignored));
    } else if (slice_group_map_type == 6) {
      // pic_size_in_map_units_minus1: ue(v)
      uint32_t pic_size_in_map_units_minus1;
      RETURN_EMPTY_ON_FAIL(
          bit_buffer->ReadExponentialGolomb(&pic_size_in_map_units_minus1));
      uint32_t slice_group_id_bits = 0;
      uint32_t num_slice_groups = num_slice_groups_minus1 + 1;
      // If num_slice_groups is not a power of two an additional bit is required
      // to account for the ceil() of log2() below.
      if ((num_slice_groups & (num_slice_groups - 1)) != 0)
        ++slice_group_id_bits;
      while (num_slice_groups > 0) {
        num_slice_groups >>= 1;
        ++slice_group_id_bits;
      }
      for (uint32_t i = 0; i <= pic_size_in_map_units_minus1; i++) {
        // slice_group_id[i]: u(v)
        // Represented by ceil(log2(num_slice_groups_minus1 + 1)) bits.
        RETURN_EMPTY_ON_FAIL(
            bit_buffer->ReadBits(&bits_tmp, slice_group_id_bits));
      }
    }
  }
  // num_ref_idx_l0_default_active_minus1: ue(v)
  RETURN_EMPTY_ON_FAIL(bit_buffer->ReadExponentialGolomb(&golomb_ignored));
  // num_ref_idx_l1_default_active_minus1: ue(v)
  RETURN_EMPTY_ON_FAIL(bit_buffer->ReadExponentialGolomb(&golomb_ignored));
  // weighted_pred_flag: u(1)
  uint32_t weighted_pred_flag;
  RETURN_EMPTY_ON_FAIL(bit_buffer->ReadBits(&weighted_pred_flag, 1));
  pps.weighted_pred_flag = weighted_pred_flag != 0;
  // weighted_bipred_idc: u(2)
  RETURN_EMPTY_ON_FAIL(bit_buffer->ReadBits(&pps.weighted_bipred_idc, 2));

  // pic_init_qp_minus26: se(v)
  RETURN_EMPTY_ON_FAIL(
      bit_buffer->ReadSignedExponentialGolomb(&pps.pic_init_qp_minus26));
  // Sanity-check parsed value
  if (pps.pic_init_qp_minus26 > kMaxPicInitQpDeltaValue ||
      pps.pic_init_qp_minus26 < kMinPicInitQpDeltaValue) {
    RETURN_EMPTY_ON_FAIL(false);
  }
  // pic_init_qs_minus26: se(v)
  RETURN_EMPTY_ON_FAIL(bit_buffer->ReadExponentialGolomb(&golomb_ignored));
  // chroma_qp_index_offset: se(v)
  RETURN_EMPTY_ON_FAIL(bit_buffer->ReadExponentialGolomb(&golomb_ignored));
  // deblocking_filter_control_present_flag: u(1)
  // constrained_intra_pred_flag: u(1)
  RETURN_EMPTY_ON_FAIL(bit_buffer->ReadBits(&bits_tmp, 2));
  // redundant_pic_cnt_present_flag: u(1)
  RETURN_EMPTY_ON_FAIL(
      bit_buffer->ReadBits(&pps.redundant_pic_cnt_present_flag, 1));

  return pps;
}

bool PpsParser::ParsePpsIdsInternal(rtc::BitBuffer* bit_buffer,
                                    uint32_t* pps_id,
                                    uint32_t* sps_id) {
  // pic_parameter_set_id: ue(v)
  if (!bit_buffer->ReadExponentialGolomb(pps_id))
    return false;
  // seq_parameter_set_id: ue(v)
  if (!bit_buffer->ReadExponentialGolomb(sps_id))
    return false;
  return true;
}

}  // namespace webrtc
