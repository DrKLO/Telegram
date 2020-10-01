/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef COMMON_VIDEO_H264_PPS_PARSER_H_
#define COMMON_VIDEO_H264_PPS_PARSER_H_

#include "absl/types/optional.h"

namespace rtc {
class BitBuffer;
}

namespace webrtc {

// A class for parsing out picture parameter set (PPS) data from a H264 NALU.
class PpsParser {
 public:
  // The parsed state of the PPS. Only some select values are stored.
  // Add more as they are actually needed.
  struct PpsState {
    PpsState() = default;

    bool bottom_field_pic_order_in_frame_present_flag = false;
    bool weighted_pred_flag = false;
    bool entropy_coding_mode_flag = false;
    uint32_t weighted_bipred_idc = false;
    uint32_t redundant_pic_cnt_present_flag = 0;
    int pic_init_qp_minus26 = 0;
    uint32_t id = 0;
    uint32_t sps_id = 0;
  };

  // Unpack RBSP and parse PPS state from the supplied buffer.
  static absl::optional<PpsState> ParsePps(const uint8_t* data, size_t length);

  static bool ParsePpsIds(const uint8_t* data,
                          size_t length,
                          uint32_t* pps_id,
                          uint32_t* sps_id);

  static absl::optional<uint32_t> ParsePpsIdFromSlice(const uint8_t* data,
                                                      size_t length);

 protected:
  // Parse the PPS state, for a bit buffer where RBSP decoding has already been
  // performed.
  static absl::optional<PpsState> ParseInternal(rtc::BitBuffer* bit_buffer);
  static bool ParsePpsIdsInternal(rtc::BitBuffer* bit_buffer,
                                  uint32_t* pps_id,
                                  uint32_t* sps_id);
};

}  // namespace webrtc

#endif  // COMMON_VIDEO_H264_PPS_PARSER_H_
