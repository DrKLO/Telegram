/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "logging/rtc_event_log/encoder/bit_writer.h"

namespace webrtc {

namespace {
size_t BitsToBytes(size_t bits) {
  return (bits / 8) + (bits % 8 > 0 ? 1 : 0);
}
}  // namespace

void BitWriter::WriteBits(uint64_t val, size_t bit_count) {
  RTC_DCHECK(valid_);
  const bool success = bit_writer_.WriteBits(val, bit_count);
  RTC_DCHECK(success);
  written_bits_ += bit_count;
}

void BitWriter::WriteBits(absl::string_view input) {
  RTC_DCHECK(valid_);
  for (char c : input) {
    WriteBits(static_cast<unsigned char>(c), CHAR_BIT);
  }
}

// Returns everything that was written so far.
// Nothing more may be written after this is called.
std::string BitWriter::GetString() {
  RTC_DCHECK(valid_);
  valid_ = false;

  buffer_.resize(BitsToBytes(written_bits_));
  written_bits_ = 0;

  std::string result;
  std::swap(buffer_, result);
  return result;
}

}  // namespace webrtc
