/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "common_video/h264/sps_parser.h"

#include <cstdint>
#include <vector>

#include "common_video/h264/h264_common.h"
#include "rtc_base/bitstream_reader.h"

namespace {
constexpr int kScalingDeltaMin = -128;
constexpr int kScaldingDeltaMax = 127;
}  // namespace

namespace webrtc {

SpsParser::SpsState::SpsState() = default;
SpsParser::SpsState::SpsState(const SpsState&) = default;
SpsParser::SpsState::~SpsState() = default;

// General note: this is based off the 02/2014 version of the H.264 standard.
// You can find it on this page:
// http://www.itu.int/rec/T-REC-H.264

// Unpack RBSP and parse SPS state from the supplied buffer.
absl::optional<SpsParser::SpsState> SpsParser::ParseSps(const uint8_t* data,
                                                        size_t length) {
  std::vector<uint8_t> unpacked_buffer = H264::ParseRbsp(data, length);
  BitstreamReader reader(unpacked_buffer);
  return ParseSpsUpToVui(reader);
}

absl::optional<SpsParser::SpsState> SpsParser::ParseSpsUpToVui(
    BitstreamReader& reader) {
  // Now, we need to use a bitstream reader to parse through the actual AVC SPS
  // format. See Section 7.3.2.1.1 ("Sequence parameter set data syntax") of the
  // H.264 standard for a complete description.
  // Since we only care about resolution, we ignore the majority of fields, but
  // we still have to actively parse through a lot of the data, since many of
  // the fields have variable size.
  // We're particularly interested in:
  // chroma_format_idc -> affects crop units
  // pic_{width,height}_* -> resolution of the frame in macroblocks (16x16).
  // frame_crop_*_offset -> crop information

  SpsState sps;

  // chroma_format_idc will be ChromaArrayType if separate_colour_plane_flag is
  // 0. It defaults to 1, when not specified.
  uint32_t chroma_format_idc = 1;

  // profile_idc: u(8). We need it to determine if we need to read/skip chroma
  // formats.
  uint8_t profile_idc = reader.Read<uint8_t>();
  // constraint_set0_flag through constraint_set5_flag + reserved_zero_2bits
  // 1 bit each for the flags + 2 bits + 8 bits for level_idc = 16 bits.
  reader.ConsumeBits(16);
  // seq_parameter_set_id: ue(v)
  sps.id = reader.ReadExponentialGolomb();
  sps.separate_colour_plane_flag = 0;
  // See if profile_idc has chroma format information.
  if (profile_idc == 100 || profile_idc == 110 || profile_idc == 122 ||
      profile_idc == 244 || profile_idc == 44 || profile_idc == 83 ||
      profile_idc == 86 || profile_idc == 118 || profile_idc == 128 ||
      profile_idc == 138 || profile_idc == 139 || profile_idc == 134) {
    // chroma_format_idc: ue(v)
    chroma_format_idc = reader.ReadExponentialGolomb();
    if (chroma_format_idc == 3) {
      // separate_colour_plane_flag: u(1)
      sps.separate_colour_plane_flag = reader.ReadBit();
    }
    // bit_depth_luma_minus8: ue(v)
    reader.ReadExponentialGolomb();
    // bit_depth_chroma_minus8: ue(v)
    reader.ReadExponentialGolomb();
    // qpprime_y_zero_transform_bypass_flag: u(1)
    reader.ConsumeBits(1);
    // seq_scaling_matrix_present_flag: u(1)
    if (reader.Read<bool>()) {
      // Process the scaling lists just enough to be able to properly
      // skip over them, so we can still read the resolution on streams
      // where this is included.
      int scaling_list_count = (chroma_format_idc == 3 ? 12 : 8);
      for (int i = 0; i < scaling_list_count; ++i) {
        // seq_scaling_list_present_flag[i]  : u(1)
        if (reader.Read<bool>()) {
          int last_scale = 8;
          int next_scale = 8;
          int size_of_scaling_list = i < 6 ? 16 : 64;
          for (int j = 0; j < size_of_scaling_list; j++) {
            if (next_scale != 0) {
              // delta_scale: se(v)
              int delta_scale = reader.ReadSignedExponentialGolomb();
              if (!reader.Ok() || delta_scale < kScalingDeltaMin ||
                  delta_scale > kScaldingDeltaMax) {
                return absl::nullopt;
              }
              next_scale = (last_scale + delta_scale + 256) % 256;
            }
            if (next_scale != 0)
              last_scale = next_scale;
          }
        }
      }
    }
  }
  // log2_max_frame_num and log2_max_pic_order_cnt_lsb are used with
  // BitstreamReader::ReadBits, which can read at most 64 bits at a time. We
  // also have to avoid overflow when adding 4 to the on-wire golomb value,
  // e.g., for evil input data, ReadExponentialGolomb might return 0xfffc.
  const uint32_t kMaxLog2Minus4 = 12;

  // log2_max_frame_num_minus4: ue(v)
  uint32_t log2_max_frame_num_minus4 = reader.ReadExponentialGolomb();
  if (!reader.Ok() || log2_max_frame_num_minus4 > kMaxLog2Minus4) {
    return absl::nullopt;
  }
  sps.log2_max_frame_num = log2_max_frame_num_minus4 + 4;

  // pic_order_cnt_type: ue(v)
  sps.pic_order_cnt_type = reader.ReadExponentialGolomb();
  if (sps.pic_order_cnt_type == 0) {
    // log2_max_pic_order_cnt_lsb_minus4: ue(v)
    uint32_t log2_max_pic_order_cnt_lsb_minus4 = reader.ReadExponentialGolomb();
    if (!reader.Ok() || log2_max_pic_order_cnt_lsb_minus4 > kMaxLog2Minus4) {
      return absl::nullopt;
    }
    sps.log2_max_pic_order_cnt_lsb = log2_max_pic_order_cnt_lsb_minus4 + 4;
  } else if (sps.pic_order_cnt_type == 1) {
    // delta_pic_order_always_zero_flag: u(1)
    sps.delta_pic_order_always_zero_flag = reader.ReadBit();
    // offset_for_non_ref_pic: se(v)
    reader.ReadExponentialGolomb();
    // offset_for_top_to_bottom_field: se(v)
    reader.ReadExponentialGolomb();
    // num_ref_frames_in_pic_order_cnt_cycle: ue(v)
    uint32_t num_ref_frames_in_pic_order_cnt_cycle =
        reader.ReadExponentialGolomb();
    for (size_t i = 0; i < num_ref_frames_in_pic_order_cnt_cycle; ++i) {
      // offset_for_ref_frame[i]: se(v)
      reader.ReadExponentialGolomb();
      if (!reader.Ok()) {
        return absl::nullopt;
      }
    }
  }
  // max_num_ref_frames: ue(v)
  sps.max_num_ref_frames = reader.ReadExponentialGolomb();
  // gaps_in_frame_num_value_allowed_flag: u(1)
  reader.ConsumeBits(1);
  //
  // IMPORTANT ONES! Now we're getting to resolution. First we read the pic
  // width/height in macroblocks (16x16), which gives us the base resolution,
  // and then we continue on until we hit the frame crop offsets, which are used
  // to signify resolutions that aren't multiples of 16.
  //
  // pic_width_in_mbs_minus1: ue(v)
  sps.width = 16 * (reader.ReadExponentialGolomb() + 1);
  // pic_height_in_map_units_minus1: ue(v)
  uint32_t pic_height_in_map_units_minus1 = reader.ReadExponentialGolomb();
  // frame_mbs_only_flag: u(1)
  sps.frame_mbs_only_flag = reader.ReadBit();
  if (!sps.frame_mbs_only_flag) {
    // mb_adaptive_frame_field_flag: u(1)
    reader.ConsumeBits(1);
  }
  sps.height =
      16 * (2 - sps.frame_mbs_only_flag) * (pic_height_in_map_units_minus1 + 1);
  // direct_8x8_inference_flag: u(1)
  reader.ConsumeBits(1);
  //
  // MORE IMPORTANT ONES! Now we're at the frame crop information.
  //
  uint32_t frame_crop_left_offset = 0;
  uint32_t frame_crop_right_offset = 0;
  uint32_t frame_crop_top_offset = 0;
  uint32_t frame_crop_bottom_offset = 0;
  // frame_cropping_flag: u(1)
  if (reader.Read<bool>()) {
    // frame_crop_{left, right, top, bottom}_offset: ue(v)
    frame_crop_left_offset = reader.ReadExponentialGolomb();
    frame_crop_right_offset = reader.ReadExponentialGolomb();
    frame_crop_top_offset = reader.ReadExponentialGolomb();
    frame_crop_bottom_offset = reader.ReadExponentialGolomb();
  }
  // vui_parameters_present_flag: u(1)
  sps.vui_params_present = reader.ReadBit();

  // Far enough! We don't use the rest of the SPS.
  if (!reader.Ok()) {
    return absl::nullopt;
  }

  // Figure out the crop units in pixels. That's based on the chroma format's
  // sampling, which is indicated by chroma_format_idc.
  if (sps.separate_colour_plane_flag || chroma_format_idc == 0) {
    frame_crop_bottom_offset *= (2 - sps.frame_mbs_only_flag);
    frame_crop_top_offset *= (2 - sps.frame_mbs_only_flag);
  } else if (!sps.separate_colour_plane_flag && chroma_format_idc > 0) {
    // Width multipliers for formats 1 (4:2:0) and 2 (4:2:2).
    if (chroma_format_idc == 1 || chroma_format_idc == 2) {
      frame_crop_left_offset *= 2;
      frame_crop_right_offset *= 2;
    }
    // Height multipliers for format 1 (4:2:0).
    if (chroma_format_idc == 1) {
      frame_crop_top_offset *= 2;
      frame_crop_bottom_offset *= 2;
    }
  }
  // Subtract the crop for each dimension.
  sps.width -= (frame_crop_left_offset + frame_crop_right_offset);
  sps.height -= (frame_crop_top_offset + frame_crop_bottom_offset);

  return sps;
}

}  // namespace webrtc
