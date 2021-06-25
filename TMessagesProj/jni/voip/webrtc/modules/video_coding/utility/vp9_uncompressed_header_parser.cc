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

#include "absl/strings/string_view.h"
#include "rtc_base/bit_buffer.h"
#include "rtc_base/logging.h"

namespace webrtc {

// Evaluates x and returns false if false.
#define RETURN_IF_FALSE(x) \
  if (!(x)) {              \
    return false;          \
  }

// Evaluates x, which is intended to return an optional. If result is nullopt,
// returns false. Else, calls fun() with the dereferenced optional as parameter.
#define READ_OR_RETURN(x, fun)     \
  do {                             \
    if (auto optional_val = (x)) { \
      fun(*optional_val);          \
    } else {                       \
      return false;                \
    }                              \
  } while (false)

namespace vp9 {
namespace {
const size_t kVp9NumRefsPerFrame = 3;
const size_t kVp9MaxRefLFDeltas = 4;
const size_t kVp9MaxModeLFDeltas = 2;
const size_t kVp9MinTileWidthB64 = 4;
const size_t kVp9MaxTileWidthB64 = 64;

class BitstreamReader {
 public:
  explicit BitstreamReader(rtc::BitBuffer* buffer) : buffer_(buffer) {}

  // Reads on bit from the input stream and:
  // * returns false if bit cannot be read
  // * calls f_true() if bit is true, returns return value of that function
  // * calls f_else() if bit is false, returns return value of that function
  bool IfNextBoolean(
      std::function<bool()> f_true,
      std::function<bool()> f_false = [] { return true; }) {
    uint32_t val;
    if (!buffer_->ReadBits(1, val)) {
      return false;
    }
    if (val != 0) {
      return f_true();
    }
    return f_false();
  }

  absl::optional<bool> ReadBoolean() {
    uint32_t val;
    if (!buffer_->ReadBits(1, val)) {
      return {};
    }
    return {val != 0};
  }

  // Reads a bit from the input stream and returns:
  // * false if bit cannot be read
  // * true if bit matches expected_val
  // * false if bit does not match expected_val - in which case |error_msg| is
  //   logged as warning, if provided.
  bool VerifyNextBooleanIs(bool expected_val, absl::string_view error_msg) {
    uint32_t val;
    if (!buffer_->ReadBits(1, val)) {
      return false;
    }
    if ((val != 0) != expected_val) {
      if (!error_msg.empty()) {
        RTC_LOG(LS_WARNING) << error_msg;
      }
      return false;
    }
    return true;
  }

  // Reads |bits| bits from the bitstream and interprets them as an unsigned
  // integer that gets cast to the type T before returning.
  // Returns nullopt if all bits cannot be read.
  // If number of bits matches size of data type, the bits parameter may be
  // omitted. Ex:
  //  ReadUnsigned<uint8_t>(2);  // Returns uint8_t with 2 LSB populated.
  //  ReadUnsigned<uint8_t>();   // Returns uint8_t with all 8 bits populated.
  template <typename T>
  absl::optional<T> ReadUnsigned(int bits = sizeof(T) * 8) {
    RTC_DCHECK_LE(bits, 32);
    RTC_DCHECK_LE(bits, sizeof(T) * 8);
    uint32_t val;
    if (!buffer_->ReadBits(bits, val)) {
      return {};
    }
    return (static_cast<T>(val));
  }

  // Helper method that reads |num_bits| from the bitstream, returns:
  // * false if bits cannot be read.
  // * true if |expected_val| matches the read bits
  // * false if |expected_val| does not match the read bits, and logs
  //   |error_msg| as a warning (if provided).
  bool VerifyNextUnsignedIs(int num_bits,
                            uint32_t expected_val,
                            absl::string_view error_msg) {
    uint32_t val;
    if (!buffer_->ReadBits(num_bits, val)) {
      return false;
    }
    if (val != expected_val) {
      if (!error_msg.empty()) {
        RTC_LOG(LS_WARNING) << error_msg;
      }
      return false;
    }
    return true;
  }

  // Basically the same as ReadUnsigned() - but for signed integers.
  // Here |bits| indicates the size of the value - number of bits read from the
  // bit buffer is one higher (the sign bit). This is made to matche the spec in
  // which eg s(4) = f(1) sign-bit, plus an f(4).
  template <typename T>
  absl::optional<T> ReadSigned(int bits = sizeof(T) * 8) {
    uint32_t sign;
    if (!buffer_->ReadBits(1, sign)) {
      return {};
    }
    uint32_t val;
    if (!buffer_->ReadBits(bits, val)) {
      return {};
    }
    int64_t sign_val = val;
    if (sign != 0) {
      sign_val = -sign_val;
    }
    return {static_cast<T>(sign_val)};
  }

  // Reads |bits| from the bitstream, disregarding their value.
  // Returns true if full number of bits were read, false otherwise.
  bool ConsumeBits(int bits) { return buffer_->ConsumeBits(bits); }

 private:
  rtc::BitBuffer* buffer_;
};

bool Vp9ReadColorConfig(BitstreamReader* br, FrameInfo* frame_info) {
  if (frame_info->profile == 2 || frame_info->profile == 3) {
    READ_OR_RETURN(br->ReadBoolean(), [frame_info](bool ten_or_twelve_bits) {
      frame_info->bit_detph =
          ten_or_twelve_bits ? BitDept::k12Bit : BitDept::k10Bit;
    });
  } else {
    frame_info->bit_detph = BitDept::k8Bit;
  }

  READ_OR_RETURN(
      br->ReadUnsigned<uint8_t>(3), [frame_info](uint8_t color_space) {
        frame_info->color_space = static_cast<ColorSpace>(color_space);
      });

  if (frame_info->color_space != ColorSpace::CS_RGB) {
    READ_OR_RETURN(br->ReadBoolean(), [frame_info](bool color_range) {
      frame_info->color_range =
          color_range ? ColorRange::kFull : ColorRange::kStudio;
    });

    if (frame_info->profile == 1 || frame_info->profile == 3) {
      READ_OR_RETURN(br->ReadUnsigned<uint8_t>(2),
                     [frame_info](uint8_t subsampling) {
                       switch (subsampling) {
                         case 0b00:
                           frame_info->sub_sampling = YuvSubsampling::k444;
                           break;
                         case 0b01:
                           frame_info->sub_sampling = YuvSubsampling::k440;
                           break;
                         case 0b10:
                           frame_info->sub_sampling = YuvSubsampling::k422;
                           break;
                         case 0b11:
                           frame_info->sub_sampling = YuvSubsampling::k420;
                           break;
                       }
                     });

      RETURN_IF_FALSE(br->VerifyNextBooleanIs(
          0, "Failed to parse header. Reserved bit set."));
    } else {
      // Profile 0 or 2.
      frame_info->sub_sampling = YuvSubsampling::k420;
    }
  } else {
    // SRGB
    frame_info->color_range = ColorRange::kFull;
    if (frame_info->profile == 1 || frame_info->profile == 3) {
      frame_info->sub_sampling = YuvSubsampling::k444;
      RETURN_IF_FALSE(br->VerifyNextBooleanIs(
          0, "Failed to parse header. Reserved bit set."));
    } else {
      RTC_LOG(LS_WARNING) << "Failed to parse header. 4:4:4 color not supported"
                             " in profile 0 or 2.";
      return false;
    }
  }

  return true;
}

bool Vp9ReadFrameSize(BitstreamReader* br, FrameInfo* frame_info) {
  // 16 bits: frame (width|height) - 1.
  READ_OR_RETURN(br->ReadUnsigned<uint16_t>(), [frame_info](uint16_t width) {
    frame_info->frame_width = width + 1;
  });
  READ_OR_RETURN(br->ReadUnsigned<uint16_t>(), [frame_info](uint16_t height) {
    frame_info->frame_height = height + 1;
  });
  return true;
}

bool Vp9ReadRenderSize(BitstreamReader* br, FrameInfo* frame_info) {
  // render_and_frame_size_different
  return br->IfNextBoolean(
      [&] {
        // 16 bits: render (width|height) - 1.
        READ_OR_RETURN(br->ReadUnsigned<uint16_t>(),
                       [frame_info](uint16_t width) {
                         frame_info->render_width = width + 1;
                       });
        READ_OR_RETURN(br->ReadUnsigned<uint16_t>(),
                       [frame_info](uint16_t height) {
                         frame_info->render_height = height + 1;
                       });
        return true;
      },
      /*else*/
      [&] {
        frame_info->render_height = frame_info->frame_height;
        frame_info->render_width = frame_info->frame_width;
        return true;
      });
}

bool Vp9ReadFrameSizeFromRefs(BitstreamReader* br, FrameInfo* frame_info) {
  bool found_ref = false;
  for (size_t i = 0; !found_ref && i < kVp9NumRefsPerFrame; i++) {
    // Size in refs.
    READ_OR_RETURN(br->ReadBoolean(), [&](bool ref) { found_ref = ref; });
  }

  if (!found_ref) {
    if (!Vp9ReadFrameSize(br, frame_info)) {
      return false;
    }
  }
  return Vp9ReadRenderSize(br, frame_info);
}

bool Vp9ReadLoopfilter(BitstreamReader* br) {
  // 6 bits: filter level.
  // 3 bits: sharpness level.
  RETURN_IF_FALSE(br->ConsumeBits(9));

  return br->IfNextBoolean([&] {    // if mode_ref_delta_enabled
    return br->IfNextBoolean([&] {  // if mode_ref_delta_update
      for (size_t i = 0; i < kVp9MaxRefLFDeltas; i++) {
        RETURN_IF_FALSE(br->IfNextBoolean([&] { return br->ConsumeBits(7); }));
      }
      for (size_t i = 0; i < kVp9MaxModeLFDeltas; i++) {
        RETURN_IF_FALSE(br->IfNextBoolean([&] { return br->ConsumeBits(7); }));
      }
      return true;
    });
  });
}

bool Vp9ReadQp(BitstreamReader* br, FrameInfo* frame_info) {
  READ_OR_RETURN(br->ReadUnsigned<uint8_t>(),
                 [frame_info](uint8_t qp) { frame_info->base_qp = qp; });

  // yuv offsets
  for (int i = 0; i < 3; ++i) {
    RETURN_IF_FALSE(br->IfNextBoolean([br] {  // if delta_coded
      return br->ConsumeBits(5);
    }));
  }
  return true;
}

bool Vp9ReadSegmentationParams(BitstreamReader* br) {
  constexpr int kVp9MaxSegments = 8;
  constexpr int kVp9SegLvlMax = 4;
  constexpr int kSegmentationFeatureBits[kVp9SegLvlMax] = {8, 6, 2, 0};
  constexpr bool kSegmentationFeatureSigned[kVp9SegLvlMax] = {1, 1, 0, 0};

  return br->IfNextBoolean([&] {    // segmentation_enabled
    return br->IfNextBoolean([&] {  // update_map
      // Consume probs.
      for (int i = 0; i < 7; ++i) {
        RETURN_IF_FALSE(br->IfNextBoolean([br] { return br->ConsumeBits(7); }));
      }

      return br->IfNextBoolean([&] {  // temporal_update
        // Consume probs.
        for (int i = 0; i < 3; ++i) {
          RETURN_IF_FALSE(
              br->IfNextBoolean([br] { return br->ConsumeBits(7); }));
        }
        return true;
      });
    });
  });

  return br->IfNextBoolean([&] {
    RETURN_IF_FALSE(br->ConsumeBits(1));  // abs_or_delta
    for (int i = 0; i < kVp9MaxSegments; ++i) {
      for (int j = 0; j < kVp9SegLvlMax; ++j) {
        RETURN_IF_FALSE(br->IfNextBoolean([&] {  // feature_enabled
          return br->ConsumeBits(kSegmentationFeatureBits[j] +
                                 kSegmentationFeatureSigned[j]);
        }));
      }
    }
    return true;
  });
}

bool Vp9ReadTileInfo(BitstreamReader* br, FrameInfo* frame_info) {
  size_t mi_cols = (frame_info->frame_width + 7) >> 3;
  size_t sb64_cols = (mi_cols + 7) >> 3;

  size_t min_log2 = 0;
  while ((kVp9MaxTileWidthB64 << min_log2) < sb64_cols) {
    ++min_log2;
  }

  size_t max_log2 = 1;
  while ((sb64_cols >> max_log2) >= kVp9MinTileWidthB64) {
    ++max_log2;
  }
  --max_log2;

  size_t cols_log2 = min_log2;
  bool done = false;
  while (!done && cols_log2 < max_log2) {
    RETURN_IF_FALSE(br->IfNextBoolean(
        [&] {
          ++cols_log2;
          return true;
        },
        [&] {
          done = true;
          return true;
        }));
  }

  // rows_log2;
  return br->IfNextBoolean([&] { return br->ConsumeBits(1); });
}
}  // namespace

bool Parse(const uint8_t* buf, size_t length, FrameInfo* frame_info) {
  rtc::BitBuffer bit_buffer(buf, length);
  BitstreamReader br(&bit_buffer);

  // Frame marker.
  RETURN_IF_FALSE(br.VerifyNextUnsignedIs(
      2, 0x2, "Failed to parse header. Frame marker should be 2."));

  // Profile has low bit first.
  READ_OR_RETURN(br.ReadBoolean(),
                 [frame_info](bool low) { frame_info->profile = int{low}; });
  READ_OR_RETURN(br.ReadBoolean(), [frame_info](bool high) {
    frame_info->profile |= int{high} << 1;
  });
  if (frame_info->profile > 2) {
    RETURN_IF_FALSE(br.VerifyNextBooleanIs(
        false, "Failed to get QP. Unsupported bitstream profile."));
  }

  // Show existing frame.
  RETURN_IF_FALSE(br.IfNextBoolean([&] {
    READ_OR_RETURN(br.ReadUnsigned<uint8_t>(3),
                   [frame_info](uint8_t frame_idx) {
                     frame_info->show_existing_frame = frame_idx;
                   });
    return true;
  }));
  if (frame_info->show_existing_frame.has_value()) {
    return true;
  }

  READ_OR_RETURN(br.ReadBoolean(), [frame_info](bool frame_type) {
    // Frame type: KEY_FRAME(0), INTER_FRAME(1).
    frame_info->is_keyframe = frame_type == 0;
  });
  READ_OR_RETURN(br.ReadBoolean(), [frame_info](bool show_frame) {
    frame_info->show_frame = show_frame;
  });
  READ_OR_RETURN(br.ReadBoolean(), [frame_info](bool error_resilient) {
    frame_info->error_resilient = error_resilient;
  });

  if (frame_info->is_keyframe) {
    RETURN_IF_FALSE(br.VerifyNextUnsignedIs(
        24, 0x498342, "Failed to get QP. Invalid sync code."));

    if (!Vp9ReadColorConfig(&br, frame_info))
      return false;
    if (!Vp9ReadFrameSize(&br, frame_info))
      return false;
    if (!Vp9ReadRenderSize(&br, frame_info))
      return false;
  } else {
    // Non-keyframe.
    bool is_intra_only = false;
    if (!frame_info->show_frame) {
      READ_OR_RETURN(br.ReadBoolean(),
                     [&](bool intra_only) { is_intra_only = intra_only; });
    }
    if (!frame_info->error_resilient) {
      RETURN_IF_FALSE(br.ConsumeBits(2));  // Reset frame context.
    }

    if (is_intra_only) {
      RETURN_IF_FALSE(br.VerifyNextUnsignedIs(
          24, 0x498342, "Failed to get QP. Invalid sync code."));

      if (frame_info->profile > 0) {
        if (!Vp9ReadColorConfig(&br, frame_info))
          return false;
      }
      // Refresh frame flags.
      RETURN_IF_FALSE(br.ConsumeBits(8));
      if (!Vp9ReadFrameSize(&br, frame_info))
        return false;
      if (!Vp9ReadRenderSize(&br, frame_info))
        return false;
    } else {
      // Refresh frame flags.
      RETURN_IF_FALSE(br.ConsumeBits(8));

      for (size_t i = 0; i < kVp9NumRefsPerFrame; i++) {
        // 3 bits: Ref frame index.
        // 1 bit: Ref frame sign biases.
        RETURN_IF_FALSE(br.ConsumeBits(4));
      }

      if (!Vp9ReadFrameSizeFromRefs(&br, frame_info))
        return false;

      // Allow high precision mv.
      RETURN_IF_FALSE(br.ConsumeBits(1));
      // Interpolation filter.
      RETURN_IF_FALSE(br.IfNextBoolean([] { return true; },
                                       [&br] { return br.ConsumeBits(2); }));
    }
  }

  if (!frame_info->error_resilient) {
    // 1 bit: Refresh frame context.
    // 1 bit: Frame parallel decoding mode.
    RETURN_IF_FALSE(br.ConsumeBits(2));
  }

  // Frame context index.
  RETURN_IF_FALSE(br.ConsumeBits(2));

  if (!Vp9ReadLoopfilter(&br))
    return false;

  // Read base QP.
  RETURN_IF_FALSE(Vp9ReadQp(&br, frame_info));

  const bool kParseFullHeader = false;
  if (kParseFullHeader) {
    // Currently not used, but will be needed when parsing beyond the
    // uncompressed header.
    RETURN_IF_FALSE(Vp9ReadSegmentationParams(&br));

    RETURN_IF_FALSE(Vp9ReadTileInfo(&br, frame_info));

    RETURN_IF_FALSE(br.ConsumeBits(16));  // header_size_in_bytes
  }

  return true;
}

bool GetQp(const uint8_t* buf, size_t length, int* qp) {
  FrameInfo frame_info;
  if (!Parse(buf, length, &frame_info)) {
    return false;
  }
  *qp = frame_info.base_qp;
  return true;
}

absl::optional<FrameInfo> ParseIntraFrameInfo(const uint8_t* buf,
                                              size_t length) {
  FrameInfo frame_info;
  if (Parse(buf, length, &frame_info) && frame_info.frame_width > 0) {
    return frame_info;
  }
  return absl::nullopt;
}

}  // namespace vp9
}  // namespace webrtc
