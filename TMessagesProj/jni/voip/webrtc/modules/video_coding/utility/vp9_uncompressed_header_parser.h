/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_VIDEO_CODING_UTILITY_VP9_UNCOMPRESSED_HEADER_PARSER_H_
#define MODULES_VIDEO_CODING_UTILITY_VP9_UNCOMPRESSED_HEADER_PARSER_H_

#include <stddef.h>
#include <stdint.h>
#include "absl/types/optional.h"

namespace webrtc {

namespace vp9 {

// Gets the QP, QP range: [0, 255].
// Returns true on success, false otherwise.
bool GetQp(const uint8_t* buf, size_t length, int* qp);

// Bit depth per channel. Support varies by profile.
enum class BitDept : uint8_t {
  k8Bit = 8,
  k10Bit = 10,
  k12Bit = 12,
};

enum class ColorSpace : uint8_t {
  CS_UNKNOWN = 0,    // Unknown (in this case the color space must be signaled
                     // outside the VP9 bitstream).
  CS_BT_601 = 1,     // CS_BT_601 Rec. ITU-R BT.601-7
  CS_BT_709 = 2,     // Rec. ITU-R BT.709-6
  CS_SMPTE_170 = 3,  // SMPTE-170
  CS_SMPTE_240 = 4,  // SMPTE-240
  CS_BT_2020 = 5,    // Rec. ITU-R BT.2020-2
  CS_RESERVED = 6,   // Reserved
  CS_RGB = 7,        // sRGB (IEC 61966-2-1)
};

enum class ColorRange {
  kStudio,  // Studio swing:
            // For BitDepth equals 8:
            //     Y is between 16 and 235 inclusive.
            //     U and V are between 16 and 240 inclusive.
            // For BitDepth equals 10:
            //     Y is between 64 and 940 inclusive.
            //     U and V are between 64 and 960 inclusive.
            // For BitDepth equals 12:
            //     Y is between 256 and 3760.
            //     U and V are between 256 and 3840 inclusive.
  kFull     // Full swing; no restriction on Y, U, V values.
};

enum class YuvSubsampling {
  k444,
  k440,
  k422,
  k420,
};

struct FrameInfo {
  int profile = 0;  // Profile 0-3 are valid.
  absl::optional<uint8_t> show_existing_frame;
  bool is_keyframe = false;
  bool show_frame = false;
  bool error_resilient = false;
  BitDept bit_detph = BitDept::k8Bit;
  ColorSpace color_space = ColorSpace::CS_UNKNOWN;
  ColorRange color_range;
  YuvSubsampling sub_sampling;
  int frame_width = 0;
  int frame_height = 0;
  int render_width = 0;
  int render_height = 0;
  int base_qp = 0;
};

// Parses frame information for a VP9 key-frame or all-intra frame from a
// bitstream. Returns nullopt on failure or if not a key-frame.
absl::optional<FrameInfo> ParseIntraFrameInfo(const uint8_t* buf,
                                              size_t length);

}  // namespace vp9

}  // namespace webrtc

#endif  // MODULES_VIDEO_CODING_UTILITY_VP9_UNCOMPRESSED_HEADER_PARSER_H_
