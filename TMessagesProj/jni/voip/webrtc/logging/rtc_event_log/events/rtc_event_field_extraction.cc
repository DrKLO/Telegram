/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "logging/rtc_event_log/events/rtc_event_field_extraction.h"

#include <algorithm>
#include <limits>

#include "rtc_base/checks.h"

namespace webrtc_event_logging {

// The bitwidth required to encode values in the range
// [0, `max_pos_magnitude`] using an unsigned representation.
uint8_t UnsignedBitWidth(uint64_t max_magnitude) {
  uint8_t required_bits = 1;
  while (max_magnitude >>= 1) {
    ++required_bits;
  }
  return required_bits;
}

// The bitwidth required to encode signed values in the range
// [-`max_neg_magnitude`, `max_pos_magnitude`] using a signed
// 2-complement representation.
uint8_t SignedBitWidth(uint64_t max_pos_magnitude, uint64_t max_neg_magnitude) {
  const uint8_t bitwidth_positive =
      max_pos_magnitude > 0 ? UnsignedBitWidth(max_pos_magnitude) : 0;
  const uint8_t bitwidth_negative =
      (max_neg_magnitude > 1) ? UnsignedBitWidth(max_neg_magnitude - 1) : 0;
  return 1 + std::max(bitwidth_positive, bitwidth_negative);
}

// Return the maximum integer of a given bit width.
uint64_t MaxUnsignedValueOfBitWidth(uint64_t bit_width) {
  RTC_DCHECK_GE(bit_width, 1);
  RTC_DCHECK_LE(bit_width, 64);
  return (bit_width == 64) ? std::numeric_limits<uint64_t>::max()
                           : ((static_cast<uint64_t>(1) << bit_width) - 1);
}

// Computes the delta between `previous` and `current`, under the assumption
// that `bit_mask` is the largest value before wrap-around occurs. The bitmask
// must be of the form 2^x-1. (We use the wrap-around to more efficiently
// compress counters that wrap around at different bit widths than the
// backing C++ data type.)
uint64_t UnsignedDelta(uint64_t previous, uint64_t current, uint64_t bit_mask) {
  RTC_DCHECK_LE(previous, bit_mask);
  RTC_DCHECK_LE(current, bit_mask);
  return (current - previous) & bit_mask;
}

}  // namespace webrtc_event_logging
