/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/video_coding/utility/vp8_header_parser.h"

#include "rtc_base/logging.h"
#include "rtc_base/system/arch.h"

namespace webrtc {

namespace vp8 {
namespace {
const size_t kCommonPayloadHeaderLength = 3;
const size_t kKeyPayloadHeaderLength = 10;
const int kMbFeatureTreeProbs = 3;
const int kNumMbSegments = 4;
const int kNumRefLfDeltas = 4;
const int kNumModeLfDeltas = 4;

}  // namespace

// Bitstream parser according to
// https://tools.ietf.org/html/rfc6386#section-7.3
void VP8InitBitReader(VP8BitReader* const br,
                      const uint8_t* start,
                      const uint8_t* end) {
  br->range_ = 255;
  br->buf_ = start;
  br->buf_end_ = end;
  br->value_ = 0;
  br->bits_ = 0;

  // Read 2 bytes.
  int i = 0;
  while (++i <= 2) {
    if (br->buf_ != br->buf_end_) {
      br->value_ = br->value_ << 8 | *br->buf_++;
    } else {
      br->value_ = br->value_ << 8;
    }
  }
}

// Bit decoder according to https://tools.ietf.org/html/rfc6386#section-7.3
// Reads one bit from the bitstream, given that it has probability prob/256 to
// be 1.
int Vp8BitReaderGetBool(VP8BitReader* br, int prob) {
  uint32_t split = 1 + (((br->range_ - 1) * prob) >> 8);
  uint32_t split_hi = split << 8;
  int retval = 0;
  if (br->value_ >= split_hi) {
    retval = 1;
    br->range_ -= split;
    br->value_ -= split_hi;
  } else {
    retval = 0;
    br->range_ = split;
  }

  while (br->range_ < 128) {
    br->value_ <<= 1;
    br->range_ <<= 1;
    if (++br->bits_ == 8) {
      br->bits_ = 0;
      if (br->buf_ != br->buf_end_) {
        br->value_ |= *br->buf_++;
      }
    }
  }
  return retval;
}

uint32_t VP8GetValue(VP8BitReader* br, int num_bits) {
  uint32_t v = 0;
  while (num_bits--) {
    // According to https://tools.ietf.org/html/rfc6386
    // Probability 128/256 is used to encode header fields.
    v = (v << 1) | Vp8BitReaderGetBool(br, 128);
  }
  return v;
}

// Not a read_signed_literal() from RFC 6386!
// This one is used to read e.g. quantizer_update, which is written as:
// L(num_bits), sign-bit.
int32_t VP8GetSignedValue(VP8BitReader* br, int num_bits) {
  int v = VP8GetValue(br, num_bits);
  int sign = VP8GetValue(br, 1);
  return sign ? -v : v;
}

static void ParseSegmentHeader(VP8BitReader* br) {
  int use_segment = VP8GetValue(br, 1);
  if (use_segment) {
    int update_map = VP8GetValue(br, 1);
    if (VP8GetValue(br, 1)) {  // update_segment_feature_data.
      VP8GetValue(br, 1);      // segment_feature_mode.
      int s;
      for (s = 0; s < kNumMbSegments; ++s) {
        bool quantizer_update = VP8GetValue(br, 1);
        if (quantizer_update) {
          VP8GetSignedValue(br, 7);
        }
      }
      for (s = 0; s < kNumMbSegments; ++s) {
        bool loop_filter_update = VP8GetValue(br, 1);
        if (loop_filter_update) {
          VP8GetSignedValue(br, 6);
        }
      }
    }
    if (update_map) {
      int s;
      for (s = 0; s < kMbFeatureTreeProbs; ++s) {
        bool segment_prob_update = VP8GetValue(br, 1);
        if (segment_prob_update) {
          VP8GetValue(br, 8);
        }
      }
    }
  }
}

static void ParseFilterHeader(VP8BitReader* br) {
  VP8GetValue(br, 1);  // filter_type.
  VP8GetValue(br, 6);  // loop_filter_level.
  VP8GetValue(br, 3);  // sharpness_level.

  // mb_lf_adjustments.
  int loop_filter_adj_enable = VP8GetValue(br, 1);
  if (loop_filter_adj_enable) {
    int mode_ref_lf_delta_update = VP8GetValue(br, 1);
    if (mode_ref_lf_delta_update) {
      int i;
      for (i = 0; i < kNumRefLfDeltas; ++i) {
        int ref_frame_delta_update_flag = VP8GetValue(br, 1);
        if (ref_frame_delta_update_flag) {
          VP8GetSignedValue(br, 6);  // delta_magnitude.
        }
      }
      for (i = 0; i < kNumModeLfDeltas; ++i) {
        int mb_mode_delta_update_flag = VP8GetValue(br, 1);
        if (mb_mode_delta_update_flag) {
          VP8GetSignedValue(br, 6);  // delta_magnitude.
        }
      }
    }
  }
}

bool GetQp(const uint8_t* buf, size_t length, int* qp) {
  if (length < kCommonPayloadHeaderLength) {
    RTC_LOG(LS_WARNING) << "Failed to get QP, invalid length.";
    return false;
  }
  VP8BitReader br;
  const uint32_t bits = buf[0] | (buf[1] << 8) | (buf[2] << 16);
  int key_frame = !(bits & 1);
  // Size of first partition in bytes.
  uint32_t partition_length = (bits >> 5);
  size_t header_length = kCommonPayloadHeaderLength;
  if (key_frame) {
    header_length = kKeyPayloadHeaderLength;
  }
  if (header_length + partition_length > length) {
    RTC_LOG(LS_WARNING) << "Failed to get QP, invalid length: " << length;
    return false;
  }
  buf += header_length;

  VP8InitBitReader(&br, buf, buf + partition_length);
  if (key_frame) {
    // Color space and pixel type.
    VP8GetValue(&br, 1);
    VP8GetValue(&br, 1);
  }
  ParseSegmentHeader(&br);
  ParseFilterHeader(&br);
  // Parse log2_nbr_of_dct_partitions value.
  VP8GetValue(&br, 2);
  // Base QP.
  const int base_q0 = VP8GetValue(&br, 7);
  if (br.buf_ == br.buf_end_) {
    RTC_LOG(LS_WARNING) << "Failed to get QP, bitstream is truncated or"
                           " corrupted.";
    return false;
  }
  *qp = base_q0;
  return true;
}

}  // namespace vp8

}  // namespace webrtc
