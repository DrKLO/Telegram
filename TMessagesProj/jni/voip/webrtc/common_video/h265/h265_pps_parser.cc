/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "common_video/h265/h265_pps_parser.h"

#include <memory>
#include <vector>

#include "common_video/h265/h265_common.h"
#include "common_video/h265/h265_sps_parser.h"
#include "rtc_base/bit_buffer.h"
#include "rtc_base/logging.h"

#define RETURN_EMPTY_ON_FAIL(x) \
  if (!(x)) {                   \
    return absl::nullopt;        \
  }

namespace {
const int kMaxPicInitQpDeltaValue = 25;
const int kMinPicInitQpDeltaValue = -26;
}  // namespace

namespace webrtc {

// General note: this is based off the 06/2019 version of the H.265 standard.
// You can find it on this page:
// http://www.itu.int/rec/T-REC-H.265

absl::optional<H265PpsParser::PpsState> H265PpsParser::ParsePps(
    const uint8_t* data,
    size_t length) {
  // First, parse out rbsp, which is basically the source buffer minus emulation
  // bytes (the last byte of a 0x00 0x00 0x03 sequence). RBSP is defined in
  // section 7.3.1.1 of the H.265 standard.
  std::vector<uint8_t> unpacked_buffer = H265::ParseRbsp(data, length);
  rtc::BitBuffer bit_buffer(unpacked_buffer.data(), unpacked_buffer.size());
  return ParseInternal(&bit_buffer);
}

bool H265PpsParser::ParsePpsIds(const uint8_t* data,
                                size_t length,
                                uint32_t* pps_id,
                                uint32_t* sps_id) {
  RTC_DCHECK(pps_id);
  RTC_DCHECK(sps_id);
  // First, parse out rbsp, which is basically the source buffer minus emulation
  // bytes (the last byte of a 0x00 0x00 0x03 sequence). RBSP is defined in
  // section 7.3.1.1 of the H.265 standard.
  std::vector<uint8_t> unpacked_buffer = H265::ParseRbsp(data, length);
  rtc::BitBuffer bit_buffer(unpacked_buffer.data(), unpacked_buffer.size());
  return ParsePpsIdsInternal(&bit_buffer, pps_id, sps_id);
}

absl::optional<uint32_t> H265PpsParser::ParsePpsIdFromSliceSegmentLayerRbsp(
    const uint8_t* data,
    size_t length,
    uint8_t nalu_type) {
  rtc::BitBuffer slice_reader(data, length);

  // first_slice_segment_in_pic_flag: u(1)
  uint32_t first_slice_segment_in_pic_flag = 0;
  RETURN_EMPTY_ON_FAIL(
      slice_reader.ReadBits(&first_slice_segment_in_pic_flag, 1));

  if (nalu_type >= H265::NaluType::kBlaWLp &&
      nalu_type <= H265::NaluType::kRsvIrapVcl23) {
    // no_output_of_prior_pics_flag: u(1)
    RETURN_EMPTY_ON_FAIL(slice_reader.ConsumeBits(1));
  }

  // slice_pic_parameter_set_id: ue(v)
  uint32_t slice_pic_parameter_set_id = 0;
  if (!slice_reader.ReadExponentialGolomb(&slice_pic_parameter_set_id))
    return absl::nullopt;

  return slice_pic_parameter_set_id;
}

absl::optional<H265PpsParser::PpsState> H265PpsParser::ParseInternal(
    rtc::BitBuffer* bit_buffer) {
  PpsState pps;

  RETURN_EMPTY_ON_FAIL(ParsePpsIdsInternal(bit_buffer, &pps.id, &pps.sps_id));

  uint32_t bits_tmp;
  uint32_t golomb_ignored;
  int32_t signed_golomb_ignored;
  // dependent_slice_segments_enabled_flag: u(1)
  RETURN_EMPTY_ON_FAIL(bit_buffer->ReadBits(&pps.dependent_slice_segments_enabled_flag, 1));
  // output_flag_present_flag: u(1)
  RETURN_EMPTY_ON_FAIL(bit_buffer->ReadBits(&pps.output_flag_present_flag, 1));
  // num_extra_slice_header_bits: u(3)
  RETURN_EMPTY_ON_FAIL(bit_buffer->ReadBits(&pps.num_extra_slice_header_bits, 3));
  // sign_data_hiding_enabled_flag: u(1)
  RETURN_EMPTY_ON_FAIL(bit_buffer->ReadBits(&bits_tmp, 1));
  // cabac_init_present_flag: u(1)
  RETURN_EMPTY_ON_FAIL(bit_buffer->ReadBits(&pps.cabac_init_present_flag, 1));
  // num_ref_idx_l0_default_active_minus1: ue(v)
  RETURN_EMPTY_ON_FAIL(bit_buffer->ReadExponentialGolomb(&pps.num_ref_idx_l0_default_active_minus1));
  // num_ref_idx_l1_default_active_minus1: ue(v)
  RETURN_EMPTY_ON_FAIL(bit_buffer->ReadExponentialGolomb(&pps.num_ref_idx_l1_default_active_minus1));
  // init_qp_minus26: se(v)
  RETURN_EMPTY_ON_FAIL(bit_buffer->ReadSignedExponentialGolomb(&pps.pic_init_qp_minus26));
  // Sanity-check parsed value
  if (pps.pic_init_qp_minus26 > kMaxPicInitQpDeltaValue ||
      pps.pic_init_qp_minus26 < kMinPicInitQpDeltaValue) {
    RETURN_EMPTY_ON_FAIL(false);
  }
  // constrained_intra_pred_flag: u(1)
  RETURN_EMPTY_ON_FAIL(bit_buffer->ReadBits(&bits_tmp, 1));
  // transform_skip_enabled_flag: u(1)
  RETURN_EMPTY_ON_FAIL(bit_buffer->ReadBits(&bits_tmp, 1));
  // cu_qp_delta_enabled_flag: u(1)
  uint32_t cu_qp_delta_enabled_flag = 0;
  RETURN_EMPTY_ON_FAIL(bit_buffer->ReadBits(&cu_qp_delta_enabled_flag, 1));
  if (cu_qp_delta_enabled_flag) {
    // diff_cu_qp_delta_depth: ue(v)
    RETURN_EMPTY_ON_FAIL(bit_buffer->ReadExponentialGolomb(&golomb_ignored));
  }
  // pps_cb_qp_offset: se(v)
  RETURN_EMPTY_ON_FAIL(bit_buffer->ReadSignedExponentialGolomb(&signed_golomb_ignored));
  // pps_cr_qp_offset: se(v)
  RETURN_EMPTY_ON_FAIL(bit_buffer->ReadSignedExponentialGolomb(&signed_golomb_ignored));
  // pps_slice_chroma_qp_offsets_present_flag: u(1)
  RETURN_EMPTY_ON_FAIL(bit_buffer->ReadBits(&bits_tmp, 1));
  // weighted_pred_flag: u(1)
  RETURN_EMPTY_ON_FAIL(bit_buffer->ReadBits(&pps.weighted_pred_flag, 1));
  // weighted_bipred_flag: u(1)
  RETURN_EMPTY_ON_FAIL(bit_buffer->ReadBits(&pps.weighted_bipred_flag, 1));
  // transquant_bypass_enabled_flag: u(1)
  RETURN_EMPTY_ON_FAIL(bit_buffer->ReadBits(&bits_tmp, 1));
  // tiles_enabled_flag: u(1)
  uint32_t tiles_enabled_flag = 0;
  RETURN_EMPTY_ON_FAIL(bit_buffer->ReadBits(&tiles_enabled_flag, 1));
  // entropy_coding_sync_enabled_flag: u(1)
  RETURN_EMPTY_ON_FAIL(bit_buffer->ReadBits(&bits_tmp, 1));
  if (tiles_enabled_flag) {
    // num_tile_columns_minus1: ue(v)
    uint32_t num_tile_columns_minus1 = 0;
    RETURN_EMPTY_ON_FAIL(bit_buffer->ReadExponentialGolomb(&num_tile_columns_minus1));
    // num_tile_rows_minus1: ue(v)
    uint32_t num_tile_rows_minus1 = 0;
    RETURN_EMPTY_ON_FAIL(bit_buffer->ReadExponentialGolomb(&num_tile_rows_minus1));
    // uniform_spacing_flag: u(1)
    uint32_t uniform_spacing_flag = 0;
    RETURN_EMPTY_ON_FAIL(bit_buffer->ReadBits(&uniform_spacing_flag, 1));
    if (!uniform_spacing_flag) {
      for (uint32_t i = 0; i < num_tile_columns_minus1; i++) {
        // column_width_minus1: ue(v)
        RETURN_EMPTY_ON_FAIL(bit_buffer->ReadExponentialGolomb(&golomb_ignored));
      }
      for (uint32_t i = 0; i < num_tile_rows_minus1; i++) {
        // row_height_minus1: ue(v)
        RETURN_EMPTY_ON_FAIL(bit_buffer->ReadExponentialGolomb(&golomb_ignored));
      }
      // loop_filter_across_tiles_enabled_flag: u(1)
      RETURN_EMPTY_ON_FAIL(bit_buffer->ReadBits(&bits_tmp, 1));
    }
  }
  // pps_loop_filter_across_slices_enabled_flag: u(1)
  RETURN_EMPTY_ON_FAIL(bit_buffer->ReadBits(&bits_tmp, 1));
  // deblocking_filter_control_present_flag: u(1)
  uint32_t deblocking_filter_control_present_flag = 0;
  RETURN_EMPTY_ON_FAIL(bit_buffer->ReadBits(&deblocking_filter_control_present_flag, 1));
  if (deblocking_filter_control_present_flag) {
    // deblocking_filter_override_enabled_flag: u(1)
    RETURN_EMPTY_ON_FAIL(bit_buffer->ReadBits(&bits_tmp, 1));
    // pps_deblocking_filter_disabled_flag: u(1)
    uint32_t pps_deblocking_filter_disabled_flag = 0;
    RETURN_EMPTY_ON_FAIL(bit_buffer->ReadBits(&pps_deblocking_filter_disabled_flag, 1));
    if (!pps_deblocking_filter_disabled_flag) {
      // pps_beta_offset_div2: se(v)
      RETURN_EMPTY_ON_FAIL(bit_buffer->ReadSignedExponentialGolomb(&signed_golomb_ignored));
      // pps_tc_offset_div2: se(v)
      RETURN_EMPTY_ON_FAIL(bit_buffer->ReadSignedExponentialGolomb(&signed_golomb_ignored));
    }
  }
  // pps_scaling_list_data_present_flag: u(1)
  uint32_t pps_scaling_list_data_present_flag = 0;
  RETURN_EMPTY_ON_FAIL(bit_buffer->ReadBits(&pps_scaling_list_data_present_flag, 1));
  if (pps_scaling_list_data_present_flag) {
    // scaling_list_data()
    if (!H265SpsParser::ParseScalingListData(bit_buffer)) {
      return absl::nullopt;
    }
  }
  // lists_modification_present_flag: u(1)
  RETURN_EMPTY_ON_FAIL(bit_buffer->ReadBits(&pps.lists_modification_present_flag, 1));
  // log2_parallel_merge_level_minus2: ue(v)
  RETURN_EMPTY_ON_FAIL(bit_buffer->ReadExponentialGolomb(&golomb_ignored));
  // slice_segment_header_extension_present_flag: u(1)
  RETURN_EMPTY_ON_FAIL(bit_buffer->ReadBits(&bits_tmp, 1));

  return pps;
}

bool H265PpsParser::ParsePpsIdsInternal(rtc::BitBuffer* bit_buffer,
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
