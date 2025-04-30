/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef COMMON_VIDEO_H264_SPS_PARSER_H_
#define COMMON_VIDEO_H264_SPS_PARSER_H_

#include "absl/types/optional.h"
#include "rtc_base/bitstream_reader.h"
#include "rtc_base/system/rtc_export.h"

namespace webrtc {

// A class for parsing out sequence parameter set (SPS) data from an H264 NALU.
class RTC_EXPORT SpsParser {
 public:
  // The parsed state of the SPS. Only some select values are stored.
  // Add more as they are actually needed.
  struct RTC_EXPORT SpsState {
    SpsState();
    SpsState(const SpsState&);
    ~SpsState();

    uint32_t width = 0;
    uint32_t height = 0;
    uint32_t delta_pic_order_always_zero_flag = 0;
    uint32_t separate_colour_plane_flag = 0;
    uint32_t frame_mbs_only_flag = 0;
    uint32_t log2_max_frame_num = 4;          // Smallest valid value.
    uint32_t log2_max_pic_order_cnt_lsb = 4;  // Smallest valid value.
    uint32_t pic_order_cnt_type = 0;
    uint32_t max_num_ref_frames = 0;
    uint32_t vui_params_present = 0;
    uint32_t id = 0;
  };

  // Unpack RBSP and parse SPS state from the supplied buffer.
  static absl::optional<SpsState> ParseSps(const uint8_t* data, size_t length);

 protected:
  // Parse the SPS state, up till the VUI part, for a buffer where RBSP
  // decoding has already been performed.
  static absl::optional<SpsState> ParseSpsUpToVui(BitstreamReader& reader);
};

}  // namespace webrtc
#endif  // COMMON_VIDEO_H264_SPS_PARSER_H_
