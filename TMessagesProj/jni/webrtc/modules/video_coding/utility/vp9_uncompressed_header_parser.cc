/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/video_coding/utility/vp9_uncompressed_header_parser.h"

#include "rtc_base/bit_buffer.h"
#include "rtc_base/logging.h"

namespace webrtc {

#define RETURN_FALSE_IF_ERROR(x) \
  if (!(x)) {                    \
    return false;                \
  }

namespace vp9 {
namespace {
const size_t kVp9NumRefsPerFrame = 3;
const size_t kVp9MaxRefLFDeltas = 4;
const size_t kVp9MaxModeLFDeltas = 2;

bool Vp9ReadProfile(rtc::BitBuffer* br, uint8_t* profile) {
  uint32_t high_bit;
  uint32_t low_bit;
  RETURN_FALSE_IF_ERROR(br->ReadBits(&low_bit, 1));
  RETURN_FALSE_IF_ERROR(br->ReadBits(&high_bit, 1));
  *profile = (high_bit << 1) + low_bit;
  if (*profile > 2) {
    uint32_t reserved_bit;
    RETURN_FALSE_IF_ERROR(br->ReadBits(&reserved_bit, 1));
    if (reserved_bit) {
      RTC_LOG(LS_WARNING) << "Failed to get QP. Unsupported bitstream profile.";
      return false;
    }
  }
  return true;
}

bool Vp9ReadSyncCode(rtc::BitBuffer* br) {
  uint32_t sync_code;
  RETURN_FALSE_IF_ERROR(br->ReadBits(&sync_code, 24));
  if (sync_code != 0x498342) {
    RTC_LOG(LS_WARNING) << "Failed to get QP. Invalid sync code.";
    return false;
  }
  return true;
}

bool Vp9ReadColorConfig(rtc::BitBuffer* br,
                        uint8_t profile,
                        FrameInfo* frame_info) {
  if (profile == 0 || profile == 1) {
    frame_info->bit_detph = BitDept::k8Bit;
  } else if (profile == 2 || profile == 3) {
    uint32_t ten_or_twelve_bits;
    RETURN_FALSE_IF_ERROR(br->ReadBits(&ten_or_twelve_bits, 1));
    frame_info->bit_detph =
        ten_or_twelve_bits ? BitDept::k12Bit : BitDept::k10Bit;
  }
  uint32_t color_space;
  RETURN_FALSE_IF_ERROR(br->ReadBits(&color_space, 3));
  frame_info->color_space = static_cast<ColorSpace>(color_space);

  // SRGB is 7.
  if (color_space != 7) {
    uint32_t color_range;
    RETURN_FALSE_IF_ERROR(br->ReadBits(&color_range, 1));
    frame_info->color_range =
        color_range ? ColorRange::kFull : ColorRange::kStudio;

    if (profile == 1 || profile == 3) {
      uint32_t subsampling_x;
      uint32_t subsampling_y;
      RETURN_FALSE_IF_ERROR(br->ReadBits(&subsampling_x, 1));
      RETURN_FALSE_IF_ERROR(br->ReadBits(&subsampling_y, 1));
      if (subsampling_x) {
        frame_info->sub_sampling =
            subsampling_y ? YuvSubsampling::k420 : YuvSubsampling::k422;
      } else {
        frame_info->sub_sampling =
            subsampling_y ? YuvSubsampling::k440 : YuvSubsampling::k444;
      }

      uint32_t reserved_bit;
      RETURN_FALSE_IF_ERROR(br->ReadBits(&reserved_bit, 1));
      if (reserved_bit) {
        RTC_LOG(LS_WARNING) << "Failed to parse header. Reserved bit set.";
        return false;
      }
    } else {
      // Profile 0 or 2.
      frame_info->sub_sampling = YuvSubsampling::k420;
    }
  } else {
    // SRGB
    frame_info->color_range = ColorRange::kFull;
    if (profile == 1 || profile == 3) {
      frame_info->sub_sampling = YuvSubsampling::k444;
      uint32_t reserved_bit;
      RETURN_FALSE_IF_ERROR(br->ReadBits(&reserved_bit, 1));
      if (reserved_bit) {
        RTC_LOG(LS_WARNING) << "Failed to parse header. Reserved bit set.";
        return false;
      }
    } else {
      RTC_LOG(LS_WARNING) << "Failed to parse header. 4:4:4 color not supported"
                             " in profile 0 or 2.";
      return false;
    }
  }

  return true;
}

bool Vp9ReadFrameSize(rtc::BitBuffer* br, FrameInfo* frame_info) {
  // 16 bits: frame width - 1.
  uint16_t frame_width_minus_one;
  RETURN_FALSE_IF_ERROR(br->ReadUInt16(&frame_width_minus_one));
  // 16 bits: frame height - 1.
  uint16_t frame_height_minus_one;
  RETURN_FALSE_IF_ERROR(br->ReadUInt16(&frame_height_minus_one));
  frame_info->frame_width = frame_width_minus_one + 1;
  frame_info->frame_height = frame_height_minus_one + 1;
  return true;
}

bool Vp9ReadRenderSize(rtc::BitBuffer* br, FrameInfo* frame_info) {
  uint32_t render_and_frame_size_different;
  RETURN_FALSE_IF_ERROR(br->ReadBits(&render_and_frame_size_different, 1));
  if (render_and_frame_size_different) {
    // 16 bits: render width - 1.
    uint16_t render_width_minus_one;
    RETURN_FALSE_IF_ERROR(br->ReadUInt16(&render_width_minus_one));
    // 16 bits: render height - 1.
    uint16_t render_height_minus_one;
    RETURN_FALSE_IF_ERROR(br->ReadUInt16(&render_height_minus_one));
    frame_info->render_width = render_width_minus_one + 1;
    frame_info->render_height = render_height_minus_one + 1;
  } else {
    frame_info->render_width = frame_info->frame_width;
    frame_info->render_height = frame_info->frame_height;
  }
  return true;
}

bool Vp9ReadFrameSizeFromRefs(rtc::BitBuffer* br, FrameInfo* frame_info) {
  uint32_t found_ref = 0;
  for (size_t i = 0; i < kVp9NumRefsPerFrame; i++) {
    // Size in refs.
    RETURN_FALSE_IF_ERROR(br->ReadBits(&found_ref, 1));
    if (found_ref)
      break;
  }

  if (!found_ref) {
    if (!Vp9ReadFrameSize(br, frame_info)) {
      return false;
    }
  }
  return Vp9ReadRenderSize(br, frame_info);
}

bool Vp9ReadInterpolationFilter(rtc::BitBuffer* br) {
  uint32_t bit;
  RETURN_FALSE_IF_ERROR(br->ReadBits(&bit, 1));
  if (bit)
    return true;

  return br->ConsumeBits(2);
}

bool Vp9ReadLoopfilter(rtc::BitBuffer* br) {
  // 6 bits: filter level.
  // 3 bits: sharpness level.
  RETURN_FALSE_IF_ERROR(br->ConsumeBits(9));

  uint32_t mode_ref_delta_enabled;
  RETURN_FALSE_IF_ERROR(br->ReadBits(&mode_ref_delta_enabled, 1));
  if (mode_ref_delta_enabled) {
    uint32_t mode_ref_delta_update;
    RETURN_FALSE_IF_ERROR(br->ReadBits(&mode_ref_delta_update, 1));
    if (mode_ref_delta_update) {
      uint32_t bit;
      for (size_t i = 0; i < kVp9MaxRefLFDeltas; i++) {
        RETURN_FALSE_IF_ERROR(br->ReadBits(&bit, 1));
        if (bit) {
          RETURN_FALSE_IF_ERROR(br->ConsumeBits(7));
        }
      }
      for (size_t i = 0; i < kVp9MaxModeLFDeltas; i++) {
        RETURN_FALSE_IF_ERROR(br->ReadBits(&bit, 1));
        if (bit) {
          RETURN_FALSE_IF_ERROR(br->ConsumeBits(7));
        }
      }
    }
  }
  return true;
}
}  // namespace

bool Parse(const uint8_t* buf, size_t length, int* qp, FrameInfo* frame_info) {
  rtc::BitBuffer br(buf, length);

  // Frame marker.
  uint32_t frame_marker;
  RETURN_FALSE_IF_ERROR(br.ReadBits(&frame_marker, 2));
  if (frame_marker != 0x2) {
    RTC_LOG(LS_WARNING) << "Failed to parse header. Frame marker should be 2.";
    return false;
  }

  // Profile.
  uint8_t profile;
  if (!Vp9ReadProfile(&br, &profile))
    return false;
  frame_info->profile = profile;

  // Show existing frame.
  uint32_t show_existing_frame;
  RETURN_FALSE_IF_ERROR(br.ReadBits(&show_existing_frame, 1));
  if (show_existing_frame)
    return false;

  // Frame type: KEY_FRAME(0), INTER_FRAME(1).
  uint32_t frame_type;
  uint32_t show_frame;
  uint32_t error_resilient;
  RETURN_FALSE_IF_ERROR(br.ReadBits(&frame_type, 1));
  RETURN_FALSE_IF_ERROR(br.ReadBits(&show_frame, 1));
  RETURN_FALSE_IF_ERROR(br.ReadBits(&error_resilient, 1));
  frame_info->show_frame = show_frame;
  frame_info->error_resilient = error_resilient;

  if (frame_type == 0) {
    // Key-frame.
    if (!Vp9ReadSyncCode(&br))
      return false;
    if (!Vp9ReadColorConfig(&br, profile, frame_info))
      return false;
    if (!Vp9ReadFrameSize(&br, frame_info))
      return false;
    if (!Vp9ReadRenderSize(&br, frame_info))
      return false;
  } else {
    // Non-keyframe.
    uint32_t intra_only = 0;
    if (!show_frame)
      RETURN_FALSE_IF_ERROR(br.ReadBits(&intra_only, 1));
    if (!error_resilient)
      RETURN_FALSE_IF_ERROR(br.ConsumeBits(2));  // Reset frame context.

    if (intra_only) {
      if (!Vp9ReadSyncCode(&br))
        return false;

      if (profile > 0) {
        if (!Vp9ReadColorConfig(&br, profile, frame_info))
          return false;
      }
      // Refresh frame flags.
      RETURN_FALSE_IF_ERROR(br.ConsumeBits(8));
      if (!Vp9ReadFrameSize(&br, frame_info))
        return false;
      if (!Vp9ReadRenderSize(&br, frame_info))
        return false;
    } else {
      // Refresh frame flags.
      RETURN_FALSE_IF_ERROR(br.ConsumeBits(8));

      for (size_t i = 0; i < kVp9NumRefsPerFrame; i++) {
        // 3 bits: Ref frame index.
        // 1 bit: Ref frame sign biases.
        RETURN_FALSE_IF_ERROR(br.ConsumeBits(4));
      }

      if (!Vp9ReadFrameSizeFromRefs(&br, frame_info))
        return false;

      // Allow high precision mv.
      RETURN_FALSE_IF_ERROR(br.ConsumeBits(1));
      // Interpolation filter.
      if (!Vp9ReadInterpolationFilter(&br))
        return false;
    }
  }

  if (!error_resilient) {
    // 1 bit: Refresh frame context.
    // 1 bit: Frame parallel decoding mode.
    RETURN_FALSE_IF_ERROR(br.ConsumeBits(2));
  }

  // Frame context index.
  RETURN_FALSE_IF_ERROR(br.ConsumeBits(2));

  if (!Vp9ReadLoopfilter(&br))
    return false;

  // Base QP.
  uint8_t base_q0;
  RETURN_FALSE_IF_ERROR(br.ReadUInt8(&base_q0));
  *qp = base_q0;
  return true;
}

bool GetQp(const uint8_t* buf, size_t length, int* qp) {
  FrameInfo frame_info;
  return Parse(buf, length, qp, &frame_info);
}

absl::optional<FrameInfo> ParseIntraFrameInfo(const uint8_t* buf,
                                              size_t length) {
  int qp = 0;
  FrameInfo frame_info;
  if (Parse(buf, length, &qp, &frame_info) && frame_info.frame_width > 0) {
    return frame_info;
  }
  return absl::nullopt;
}

}  // namespace vp9
}  // namespace webrtc
