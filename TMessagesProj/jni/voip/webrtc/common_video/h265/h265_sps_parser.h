/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef COMMON_VIDEO_H265_H265_SPS_PARSER_H_
#define COMMON_VIDEO_H265_H265_SPS_PARSER_H_

#include <vector>

#include "absl/types/optional.h"

namespace rtc {
class BitBuffer;
}

namespace webrtc {

// A class for parsing out sequence parameter set (SPS) data from an H265 NALU.
class H265SpsParser {
 public:

  struct ShortTermRefPicSet {
    ShortTermRefPicSet();

    uint32_t inter_ref_pic_set_prediction_flag = 0;
    std::vector<uint32_t> used_by_curr_pic_flag;
    std::vector<uint32_t> use_delta_flag;
    uint32_t num_negative_pics = 0;
    uint32_t num_positive_pics = 0;
    std::vector<uint32_t> delta_poc_s0_minus1;
    std::vector<uint32_t> used_by_curr_pic_s0_flag;
    std::vector<uint32_t> delta_poc_s1_minus1;
    std::vector<uint32_t> used_by_curr_pic_s1_flag;
  };

  // The parsed state of the SPS. Only some select values are stored.
  // Add more as they are actually needed.
  struct SpsState {
    SpsState();

    uint32_t sps_max_sub_layers_minus1;
    uint32_t chroma_format_idc = 0;
    uint32_t separate_colour_plane_flag = 0;
    uint32_t pic_width_in_luma_samples = 0;
    uint32_t pic_height_in_luma_samples = 0;
    uint32_t log2_max_pic_order_cnt_lsb_minus4 = 0;
    std::vector<uint32_t> sps_max_dec_pic_buffering_minus1;
    uint32_t log2_min_luma_coding_block_size_minus3 = 0;
    uint32_t log2_diff_max_min_luma_coding_block_size = 0;
    uint32_t sample_adaptive_offset_enabled_flag = 0;
    uint32_t num_short_term_ref_pic_sets = 0;
    std::vector<H265SpsParser::ShortTermRefPicSet> short_term_ref_pic_set;
    uint32_t long_term_ref_pics_present_flag = 0;
    uint32_t num_long_term_ref_pics_sps = 0;
    std::vector<uint32_t> used_by_curr_pic_lt_sps_flag;
    uint32_t sps_temporal_mvp_enabled_flag = 0;
    uint32_t width = 0;
    uint32_t height = 0;
    uint32_t id = 0;
    uint32_t vps_id = 0;
  };

  // Unpack RBSP and parse SPS state from the supplied buffer.
  static absl::optional<SpsState> ParseSps(const uint8_t* data, size_t length);

  static bool ParseScalingListData(rtc::BitBuffer* buffer);

  static absl::optional<ShortTermRefPicSet> ParseShortTermRefPicSet(
        uint32_t st_rps_idx, uint32_t num_short_term_ref_pic_sets,
        const std::vector<ShortTermRefPicSet>& ref_pic_sets,
        SpsState& sps, rtc::BitBuffer* buffer);

 protected:
 // Parse the SPS state, for a bit buffer where RBSP decoding has already been
 // performed.
  static absl::optional<SpsState> ParseSpsInternal(rtc::BitBuffer* buffer);
};

}  // namespace webrtc
#endif  // COMMON_VIDEO_H265_H265_SPS_PARSER_H_
