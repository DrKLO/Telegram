/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "common_video/h265/h265_bitstream_parser.h"

#include <stdlib.h>

#include <cstdint>
#include <vector>

#include "common_video/h265/h265_common.h"
#include "rtc_base/bit_buffer.h"
#include "rtc_base/logging.h"

namespace {

const int kMaxAbsQpDeltaValue = 51;
const int kMinQpValue = 0;
const int kMaxQpValue = 51;

}  // namespace

namespace webrtc {

#define RETURN_ON_FAIL(x, res)            \
  if (!(x)) {                             \
    RTC_LOG_F(LS_ERROR) << "FAILED: " #x; \
    return res;                           \
  }

#define RETURN_INV_ON_FAIL(x) RETURN_ON_FAIL(x, kInvalidStream)

H265BitstreamParser::H265BitstreamParser() {}
H265BitstreamParser::~H265BitstreamParser() {}

H265BitstreamParser::Result H265BitstreamParser::ParseNonParameterSetNalu(
    const uint8_t* source,
    size_t source_length,
    uint8_t nalu_type) {
  if (!sps_ || !pps_)
    return kInvalidStream;

  last_slice_qp_delta_ = absl::nullopt;
  const std::vector<uint8_t> slice_rbsp =
      H265::ParseRbsp(source, source_length);
  if (slice_rbsp.size() < H265::kNaluTypeSize)
    return kInvalidStream;

  rtc::BitBuffer slice_reader(slice_rbsp.data() + H265::kNaluTypeSize,
                              slice_rbsp.size() - H265::kNaluTypeSize);
  // Check to see if this is an IDR slice, which has an extra field to parse
  // out.
  //bool is_idr = (source[0] & 0x0F) == H265::NaluType::kIdr;
  //uint8_t nal_ref_idc = (source[0] & 0x60) >> 5;
  uint32_t golomb_tmp;
  uint32_t bits_tmp;

  // first_slice_segment_in_pic_flag: u(1)
  uint32_t first_slice_segment_in_pic_flag = 0;
  RETURN_INV_ON_FAIL(slice_reader.ReadBits(&first_slice_segment_in_pic_flag, 1));
  if (H265::NaluType::kBlaWLp <= nalu_type &&
      nalu_type <= H265::NaluType::kRsvIrapVcl23) {
    // no_output_of_prior_pics_flag: u(1)
    RETURN_INV_ON_FAIL(slice_reader.ReadBits(&bits_tmp, 1));
  }
  // slice_pic_parameter_set_id: ue(v)
  RETURN_INV_ON_FAIL(slice_reader.ReadExponentialGolomb(&golomb_tmp));
  uint32_t dependent_slice_segment_flag = 0;
  if (first_slice_segment_in_pic_flag == 0) {
    if (pps_->dependent_slice_segments_enabled_flag) {
      // dependent_slice_segment_flag: u(1)
      RETURN_INV_ON_FAIL(slice_reader.ReadBits(&dependent_slice_segment_flag, 1));
    }

    // slice_segment_address: u(v)
    int32_t log2_ctb_size_y = sps_->log2_min_luma_coding_block_size_minus3 + 3 + sps_->log2_diff_max_min_luma_coding_block_size;
    uint32_t ctb_size_y = 1 << log2_ctb_size_y;
    uint32_t pic_width_in_ctbs_y = sps_->pic_width_in_luma_samples / ctb_size_y;
    if(sps_->pic_width_in_luma_samples % ctb_size_y)
      pic_width_in_ctbs_y++;

    uint32_t pic_height_in_ctbs_y = sps_->pic_height_in_luma_samples / ctb_size_y;
    if(sps_->pic_height_in_luma_samples % ctb_size_y)
      pic_height_in_ctbs_y++;

    uint32_t slice_segment_address_bits = H265::Log2(pic_height_in_ctbs_y * pic_width_in_ctbs_y);
    RETURN_INV_ON_FAIL(slice_reader.ReadBits(&bits_tmp, slice_segment_address_bits));
  }

  if (dependent_slice_segment_flag == 0) {
    for (uint32_t i = 0; i < pps_->num_extra_slice_header_bits; i++) {
      // slice_reserved_flag: u(1)
      RETURN_INV_ON_FAIL(slice_reader.ReadBits(&bits_tmp, 1));
    }
    // slice_type: ue(v)
    uint32_t slice_type = 0;
    RETURN_INV_ON_FAIL(slice_reader.ReadExponentialGolomb(&slice_type));
    if (pps_->output_flag_present_flag) {
      // pic_output_flag: u(1)
      RETURN_INV_ON_FAIL(slice_reader.ReadBits(&bits_tmp, 1));
    }
    if (sps_->separate_colour_plane_flag) {
      // colour_plane_id: u(2)
      RETURN_INV_ON_FAIL(slice_reader.ReadBits(&bits_tmp, 2));
    }
    uint32_t num_long_term_sps = 0;
    uint32_t num_long_term_pics = 0;
    std::vector<uint32_t> lt_idx_sps;
    std::vector<uint32_t> used_by_curr_pic_lt_flag;
    uint32_t short_term_ref_pic_set_sps_flag = 0;
    uint32_t short_term_ref_pic_set_idx = 0;
    H265SpsParser::ShortTermRefPicSet short_term_ref_pic_set;
    uint32_t slice_temporal_mvp_enabled_flag = 0;
    if (nalu_type != H265::NaluType::kIdrWRadl && nalu_type != H265::NaluType::kIdrNLp) {
      // slice_pic_order_cnt_lsb: u(v)
      uint32_t slice_pic_order_cnt_lsb_bits = sps_->log2_max_pic_order_cnt_lsb_minus4 + 4;
      RETURN_INV_ON_FAIL(slice_reader.ReadBits(&bits_tmp, slice_pic_order_cnt_lsb_bits));
      // short_term_ref_pic_set_sps_flag: u(1)
      RETURN_INV_ON_FAIL(slice_reader.ReadBits(&short_term_ref_pic_set_sps_flag, 1));
      if (!short_term_ref_pic_set_sps_flag) {
        absl::optional<H265SpsParser::ShortTermRefPicSet> ref_pic_set
          = H265SpsParser::ParseShortTermRefPicSet(sps_->num_short_term_ref_pic_sets,
            sps_->num_short_term_ref_pic_sets, sps_->short_term_ref_pic_set, *sps_, &slice_reader);
        if (ref_pic_set) {
          short_term_ref_pic_set = *ref_pic_set;
        } else {
          return kInvalidStream;
        }
      } else if (sps_->num_short_term_ref_pic_sets > 1) {
        // short_term_ref_pic_set_idx: u(v)
        uint32_t short_term_ref_pic_set_idx_bits = H265::Log2(sps_->num_short_term_ref_pic_sets);
        if ((1 << short_term_ref_pic_set_idx_bits) < sps_->num_short_term_ref_pic_sets) {
          short_term_ref_pic_set_idx_bits++;
        }
        if (short_term_ref_pic_set_idx_bits > 0) {
          RETURN_INV_ON_FAIL(slice_reader.ReadBits(&short_term_ref_pic_set_idx, short_term_ref_pic_set_idx_bits));
        }
      }
      if (sps_->long_term_ref_pics_present_flag) {
        if (sps_->num_long_term_ref_pics_sps > 0) {
          // num_long_term_sps: ue(v)
          RETURN_INV_ON_FAIL(slice_reader.ReadExponentialGolomb(&num_long_term_sps));
        }
        // num_long_term_sps: ue(v)
        RETURN_INV_ON_FAIL(slice_reader.ReadExponentialGolomb(&num_long_term_pics));
        lt_idx_sps.resize(num_long_term_sps + num_long_term_pics, 0);
        used_by_curr_pic_lt_flag.resize(num_long_term_sps + num_long_term_pics, 0);
        for (uint32_t i = 0; i < num_long_term_sps + num_long_term_pics; i++) {
          if (i < num_long_term_sps) {
            if (sps_->num_long_term_ref_pics_sps > 1) {
              // lt_idx_sps: u(v)
              uint32_t lt_idx_sps_bits = H265::Log2(sps_->num_long_term_ref_pics_sps);
              RETURN_INV_ON_FAIL(slice_reader.ReadBits(&lt_idx_sps[i], lt_idx_sps_bits));
            }
          } else {
            // poc_lsb_lt: u(v)
            uint32_t poc_lsb_lt_bits = sps_->log2_max_pic_order_cnt_lsb_minus4 + 4;
            RETURN_INV_ON_FAIL(slice_reader.ReadBits(&bits_tmp, poc_lsb_lt_bits));
            // used_by_curr_pic_lt_flag: u(1)
            RETURN_INV_ON_FAIL(slice_reader.ReadBits(&used_by_curr_pic_lt_flag[i], 1));
          }
          // delta_poc_msb_present_flag: u(1)
          uint32_t delta_poc_msb_present_flag = 0;
          RETURN_INV_ON_FAIL(slice_reader.ReadBits(&delta_poc_msb_present_flag, 1));
          if (delta_poc_msb_present_flag) {
            // delta_poc_msb_cycle_lt: ue(v)
            RETURN_INV_ON_FAIL(slice_reader.ReadExponentialGolomb(&golomb_tmp));
          }
        }
      }
      if (sps_->sps_temporal_mvp_enabled_flag) {
        // slice_temporal_mvp_enabled_flag: u(1)
        RETURN_INV_ON_FAIL(slice_reader.ReadBits(&slice_temporal_mvp_enabled_flag, 1));
      }
    }

    if (sps_->sample_adaptive_offset_enabled_flag) {
      // slice_sao_luma_flag: u(1)
      RETURN_INV_ON_FAIL(slice_reader.ReadBits(&bits_tmp, 1));
      uint32_t chroma_array_type = sps_->separate_colour_plane_flag == 0 ? sps_->chroma_format_idc : 0;
      if (chroma_array_type != 0) {
        // slice_sao_chroma_flag: u(1)
        RETURN_INV_ON_FAIL(slice_reader.ReadBits(&bits_tmp, 1));
      }
    }

    if (slice_type == H265::SliceType::kP || slice_type == H265::SliceType::kB) {
      // num_ref_idx_active_override_flag: u(1)
      uint32_t num_ref_idx_active_override_flag = 0;
      RETURN_INV_ON_FAIL(slice_reader.ReadBits(&num_ref_idx_active_override_flag, 1));
      uint32_t num_ref_idx_l0_active_minus1 = pps_->num_ref_idx_l0_default_active_minus1;
      uint32_t num_ref_idx_l1_active_minus1 = pps_->num_ref_idx_l1_default_active_minus1;
      if (num_ref_idx_active_override_flag) {
        // num_ref_idx_l0_active_minus1: ue(v)
        RETURN_INV_ON_FAIL(slice_reader.ReadExponentialGolomb(&num_ref_idx_l0_active_minus1));
        if (slice_type == H265::SliceType::kB) {
          // num_ref_idx_l1_active_minus1: ue(v)
          RETURN_INV_ON_FAIL(slice_reader.ReadExponentialGolomb(&num_ref_idx_l1_active_minus1));
        }
      }
      uint32_t num_pic_total_curr = CalcNumPocTotalCurr(
          num_long_term_sps, num_long_term_pics, lt_idx_sps,
          used_by_curr_pic_lt_flag, short_term_ref_pic_set_sps_flag,
          short_term_ref_pic_set_idx, short_term_ref_pic_set);
      if (pps_->lists_modification_present_flag && num_pic_total_curr > 1) {
        // ref_pic_lists_modification()
        uint32_t list_entry_bits = H265::Log2(num_pic_total_curr);
        if ((1 << list_entry_bits) < num_pic_total_curr) {
          list_entry_bits++;
        }
        // ref_pic_list_modification_flag_l0: u(1)
        uint32_t ref_pic_list_modification_flag_l0 = 0;
        RETURN_INV_ON_FAIL(slice_reader.ReadBits(&ref_pic_list_modification_flag_l0, 1));
        if (ref_pic_list_modification_flag_l0) {
          for (uint32_t i = 0; i < num_ref_idx_l0_active_minus1; i++) {
            // list_entry_l0: u(v)
            RETURN_INV_ON_FAIL(slice_reader.ReadBits(&bits_tmp, list_entry_bits));
          }
        }
        if (slice_type == H265::SliceType::kB) {
          // ref_pic_list_modification_flag_l1: u(1)
          uint32_t ref_pic_list_modification_flag_l1 = 0;
          RETURN_INV_ON_FAIL(slice_reader.ReadBits(&ref_pic_list_modification_flag_l1, 1));
          if (ref_pic_list_modification_flag_l1) {
            for (uint32_t i = 0; i < num_ref_idx_l1_active_minus1; i++) {
              // list_entry_l1: u(v)
              RETURN_INV_ON_FAIL(slice_reader.ReadBits(&bits_tmp, list_entry_bits));
            }
          }
        }
      }
      if (slice_type == H265::SliceType::kB) {
        // mvd_l1_zero_flag: u(1)
        RETURN_INV_ON_FAIL(slice_reader.ReadBits(&bits_tmp, 1));
      }
      if (pps_->cabac_init_present_flag) {
        // cabac_init_flag: u(1)
        RETURN_INV_ON_FAIL(slice_reader.ReadBits(&bits_tmp, 1));
      }
      if (slice_temporal_mvp_enabled_flag) {
        uint32_t collocated_from_l0_flag = 0;
        if (slice_type == H265::SliceType::kB) {
          // collocated_from_l0_flag: u(1)
          RETURN_INV_ON_FAIL(slice_reader.ReadBits(&collocated_from_l0_flag, 1));
        }
        if ((collocated_from_l0_flag && num_ref_idx_l0_active_minus1 > 0)
          || (!collocated_from_l0_flag && num_ref_idx_l1_active_minus1 > 0)) {
          // collocated_ref_idx: ue(v)
          RETURN_INV_ON_FAIL(slice_reader.ReadExponentialGolomb(&golomb_tmp));
        }
      }
      if ((pps_->weighted_pred_flag && slice_type == H265::SliceType::kP)
          || (pps_->weighted_bipred_flag && slice_type == H265::SliceType::kB)) {
        // pred_weight_table()
        // TODO(piasy): Do we need support for pred_weight_table()?
        RTC_LOG(LS_ERROR) << "Streams with pred_weight_table unsupported.";
        return kUnsupportedStream;
      }
      // five_minus_max_num_merge_cand: ue(v)
      RETURN_INV_ON_FAIL(slice_reader.ReadExponentialGolomb(&golomb_tmp));
      // TODO(piasy): motion_vector_resolution_control_idc?
    }
  }

  // slice_qp_delta: se(v)
  int32_t last_slice_qp_delta;
  RETURN_INV_ON_FAIL(
      slice_reader.ReadSignedExponentialGolomb(&last_slice_qp_delta));
  if (abs(last_slice_qp_delta) > kMaxAbsQpDeltaValue) {
    // Something has gone wrong, and the parsed value is invalid.
    RTC_LOG(LS_WARNING) << "Parsed QP value out of range.";
    return kInvalidStream;
  }

  last_slice_qp_delta_ = last_slice_qp_delta;

  return kOk;
}

uint32_t H265BitstreamParser::CalcNumPocTotalCurr(
    uint32_t num_long_term_sps, uint32_t num_long_term_pics,
    const std::vector<uint32_t> lt_idx_sps,
    const std::vector<uint32_t> used_by_curr_pic_lt_flag,
    uint32_t short_term_ref_pic_set_sps_flag,
    uint32_t short_term_ref_pic_set_idx,
    const H265SpsParser::ShortTermRefPicSet& short_term_ref_pic_set) {
  uint32_t num_poc_total_curr = 0;
  uint32_t curr_sps_idx;

  bool used_by_curr_pic_lt[16];
  uint32_t num_long_term = num_long_term_sps + num_long_term_pics;

  for (uint32_t i = 0; i < num_long_term; i++) {
    if (i < num_long_term_sps) {
      used_by_curr_pic_lt[i] = sps_->used_by_curr_pic_lt_sps_flag[lt_idx_sps[i]];
    } else {
      used_by_curr_pic_lt[i] = used_by_curr_pic_lt_flag[i];
    }
  }

  if (short_term_ref_pic_set_sps_flag) {
    curr_sps_idx = short_term_ref_pic_set_idx;
  } else {
    curr_sps_idx = sps_->num_short_term_ref_pic_sets;
  }

  if (sps_->short_term_ref_pic_set.size() <= curr_sps_idx) {
    if (curr_sps_idx != 0 || short_term_ref_pic_set_sps_flag) {
      return 0;
    }
  }

  const H265SpsParser::ShortTermRefPicSet* ref_pic_set;
  if (curr_sps_idx < sps_->short_term_ref_pic_set.size()) {
    ref_pic_set = &(sps_->short_term_ref_pic_set[curr_sps_idx]);
  } else {
    ref_pic_set = &short_term_ref_pic_set;
  }

  for (uint32_t i = 0; i < ref_pic_set->num_negative_pics; i++) {
    if (ref_pic_set->used_by_curr_pic_s0_flag[i]) {
      num_poc_total_curr++;
    }
  }

  for (uint32_t i = 0; i < ref_pic_set->num_positive_pics; i++) {
    if (ref_pic_set->used_by_curr_pic_s1_flag[i]) {
      num_poc_total_curr++;
    }
  }

  for (uint32_t i = 0; i < num_long_term_sps + num_long_term_pics; i++) {
    if (used_by_curr_pic_lt[i]) {
      num_poc_total_curr++;
    }
  }

  return num_poc_total_curr;
}

void H265BitstreamParser::ParseSlice(const uint8_t* slice, size_t length) {
  H265::NaluType nalu_type = H265::ParseNaluType(slice[0]);
  if (nalu_type == H265::NaluType::kSps) {
      sps_ = H265SpsParser::ParseSps(slice + H265::kNaluTypeSize,
                                     length - H265::kNaluTypeSize);
      if (!sps_) {
        RTC_LOG(LS_WARNING) << "Unable to parse SPS from H265 bitstream.";
      }
  } else if (nalu_type == H265::NaluType::kPps) {
      pps_ = H265PpsParser::ParsePps(slice + H265::kNaluTypeSize,
                                     length - H265::kNaluTypeSize);
      if (!pps_) {
        RTC_LOG(LS_WARNING) << "Unable to parse PPS from H265 bitstream.";
      }
  } else if (nalu_type <= H265::NaluType::kRsvIrapVcl23) {
      Result res = ParseNonParameterSetNalu(slice, length, nalu_type);
      if (res != kOk) {
        RTC_LOG(LS_INFO) << "Failed to parse bitstream. Error: " << res;
      }
  }
}

void H265BitstreamParser::ParseBitstream(const uint8_t* bitstream,
                                         size_t length) {
  std::vector<H265::NaluIndex> nalu_indices =
      H265::FindNaluIndices(bitstream, length);
  for (const H265::NaluIndex& index : nalu_indices)
    ParseSlice(&bitstream[index.payload_start_offset], index.payload_size);
}

bool H265BitstreamParser::GetLastSliceQp(int* qp) const {
  if (!last_slice_qp_delta_ || !pps_) {
    return false;
  }
  const int parsed_qp = 26 + pps_->pic_init_qp_minus26 + *last_slice_qp_delta_;
  if (parsed_qp < kMinQpValue || parsed_qp > kMaxQpValue) {
    RTC_LOG(LS_ERROR) << "Parsed invalid QP from bitstream.";
    return false;
  }
  *qp = parsed_qp;
  return true;
}

void H265BitstreamParser::ParseBitstream(
    rtc::ArrayView<const uint8_t> bitstream) {
  ParseBitstream(bitstream.data(), bitstream.size());
}

absl::optional<int> H265BitstreamParser::GetLastSliceQp() const {
  int qp;
  bool success = GetLastSliceQp(&qp);
  return success ? absl::optional<int>(qp) : absl::nullopt;
}

}  // namespace webrtc
