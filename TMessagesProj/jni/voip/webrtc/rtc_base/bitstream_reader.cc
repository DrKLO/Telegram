/*
 *  Copyright 2021 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/bitstream_reader.h"

#include <stdint.h>

#include <limits>

#include "absl/numeric/bits.h"
#include "rtc_base/checks.h"
#include "rtc_base/numerics/safe_conversions.h"

namespace webrtc {

uint64_t BitstreamReader::ReadBits(int bits) {
  RTC_DCHECK_GE(bits, 0);
  RTC_DCHECK_LE(bits, 64);
  set_last_read_is_verified(false);

  if (remaining_bits_ < bits) {
    remaining_bits_ -= bits;
    return 0;
  }

  int remaining_bits_in_first_byte = remaining_bits_ % 8;
  remaining_bits_ -= bits;
  if (bits < remaining_bits_in_first_byte) {
    // Reading fewer bits than what's left in the current byte, just
    // return the portion of this byte that is needed.
    int offset = (remaining_bits_in_first_byte - bits);
    return ((*bytes_) >> offset) & ((1 << bits) - 1);
  }

  uint64_t result = 0;
  if (remaining_bits_in_first_byte > 0) {
    // Read all bits that were left in the current byte and consume that byte.
    bits -= remaining_bits_in_first_byte;
    uint8_t mask = (1 << remaining_bits_in_first_byte) - 1;
    result = static_cast<uint64_t>(*bytes_ & mask) << bits;
    ++bytes_;
  }

  // Read as many full bytes as we can.
  while (bits >= 8) {
    bits -= 8;
    result |= uint64_t{*bytes_} << bits;
    ++bytes_;
  }
  // Whatever is left to read is smaller than a byte, so grab just the needed
  // bits and shift them into the lowest bits.
  if (bits > 0) {
    result |= (*bytes_ >> (8 - bits));
  }
  return result;
}

int BitstreamReader::ReadBit() {
  set_last_read_is_verified(false);
  --remaining_bits_;
  if (remaining_bits_ < 0) {
    return 0;
  }

  int bit_position = remaining_bits_ % 8;
  if (bit_position == 0) {
    // Read the last bit from current byte and move to the next byte.
    return (*bytes_++) & 0x01;
  }

  return (*bytes_ >> bit_position) & 0x01;
}

void BitstreamReader::ConsumeBits(int bits) {
  RTC_DCHECK_GE(bits, 0);
  set_last_read_is_verified(false);
  if (remaining_bits_ < bits) {
    Invalidate();
    return;
  }

  int remaining_bytes = (remaining_bits_ + 7) / 8;
  remaining_bits_ -= bits;
  int new_remaining_bytes = (remaining_bits_ + 7) / 8;
  bytes_ += (remaining_bytes - new_remaining_bytes);
}

uint32_t BitstreamReader::ReadNonSymmetric(uint32_t num_values) {
  RTC_DCHECK_GT(num_values, 0);
  RTC_DCHECK_LE(num_values, uint32_t{1} << 31);

  int width = absl::bit_width(num_values);
  uint32_t num_min_bits_values = (uint32_t{1} << width) - num_values;

  uint64_t val = ReadBits(width - 1);
  if (val < num_min_bits_values) {
    return val;
  }
  return (val << 1) + ReadBit() - num_min_bits_values;
}

uint32_t BitstreamReader::ReadExponentialGolomb() {
  // Count the number of leading 0.
  int zero_bit_count = 0;
  while (ReadBit() == 0) {
    if (++zero_bit_count >= 32) {
      // Golob value won't fit into 32 bits of the return value. Fail the parse.
      Invalidate();
      return 0;
    }
  }

  // The bit count of the value is the number of zeros + 1.
  // However the first '1' was already read above.
  return (uint32_t{1} << zero_bit_count) +
         rtc::dchecked_cast<uint32_t>(ReadBits(zero_bit_count)) - 1;
}

int BitstreamReader::ReadSignedExponentialGolomb() {
  uint32_t unsigned_val = ReadExponentialGolomb();
  if ((unsigned_val & 1) == 0) {
    return -static_cast<int>(unsigned_val / 2);
  } else {
    return (unsigned_val + 1) / 2;
  }
}

uint64_t BitstreamReader::ReadLeb128() {
  uint64_t decoded = 0;
  size_t i = 0;
  uint8_t byte;
  // A LEB128 value can in theory be arbitrarily large, but for convenience sake
  // consider it invalid if it can't fit in an uint64_t.
  do {
    byte = Read<uint8_t>();
    decoded +=
        (static_cast<uint64_t>(byte & 0x7f) << static_cast<uint64_t>(7 * i));
    ++i;
  } while (i < 10 && (byte & 0x80));

  // The first 9 bytes represent the first 63 bits. The tenth byte can therefore
  // not be larger than 1 as it would overflow an uint64_t.
  if (i == 10 && byte > 1) {
    Invalidate();
  }

  return Ok() ? decoded : 0;
}

std::string BitstreamReader::ReadString(int num_bytes) {
  std::string res;
  res.reserve(num_bytes);
  for (int i = 0; i < num_bytes; ++i) {
    res += Read<uint8_t>();
  }

  return Ok() ? res : std::string();
}

}  // namespace webrtc
