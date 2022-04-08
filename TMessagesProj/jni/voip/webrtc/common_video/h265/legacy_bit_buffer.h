/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef COMMON_VIDEO_H265_LEGACY_BIT_BUFFER_H_
#define COMMON_VIDEO_H265_LEGACY_BIT_BUFFER_H_

#include <stddef.h>  // For size_t.
#include <stdint.h>  // For integer types.

#include "rtc_base/constructor_magic.h"

namespace rtc {

// A class, similar to ByteBuffer, that can parse bit-sized data out of a set of
// bytes. Has a similar API to ByteBuffer, plus methods for reading bit-sized
// and exponential golomb encoded data. For a writable version, use
// BitBufferWriter. Unlike ByteBuffer, this class doesn't make a copy of the
// source bytes, so it can be used on read-only data.
// Sizes/counts specify bits/bytes, for clarity.
// Byte order is assumed big-endian/network.
class BitBuffer {
 public:
  BitBuffer(const uint8_t* bytes, size_t byte_count);

  // Gets the current offset, in bytes/bits, from the start of the buffer. The
  // bit offset is the offset into the current byte, in the range [0,7].
  void GetCurrentOffset(size_t* out_byte_offset, size_t* out_bit_offset);

  // The remaining bits in the byte buffer.
  uint64_t RemainingBitCount() const;

  // Reads byte-sized values from the buffer. Returns false if there isn't
  // enough data left for the specified type.
  bool ReadUInt8(uint8_t* val);
  bool ReadUInt16(uint16_t* val);
  bool ReadUInt32(uint32_t* val);

  // Reads bit-sized values from the buffer. Returns false if there isn't enough
  // data left for the specified bit count.
  bool ReadBits(uint32_t* val, size_t bit_count);

  // Peeks bit-sized values from the buffer. Returns false if there isn't enough
  // data left for the specified number of bits. Doesn't move the current
  // offset.
  bool PeekBits(uint32_t* val, size_t bit_count);

  // Reads value in range [0, num_values - 1].
  // This encoding is similar to ReadBits(val, Ceil(Log2(num_values)),
  // but reduces wastage incurred when encoding non-power of two value ranges
  // Non symmetric values are encoded as:
  // 1) n = countbits(num_values)
  // 2) k = (1 << n) - num_values
  // Value v in range [0, k - 1] is encoded in (n-1) bits.
  // Value v in range [k, num_values - 1] is encoded as (v+k) in n bits.
  // https://aomediacodec.github.io/av1-spec/#nsn
  // Returns false if there isn't enough data left.
  bool ReadNonSymmetric(uint32_t* val, uint32_t num_values);

  // Reads the exponential golomb encoded value at the current offset.
  // Exponential golomb values are encoded as:
  // 1) x = source val + 1
  // 2) In binary, write [countbits(x) - 1] 0s, then x
  // To decode, we count the number of leading 0 bits, read that many + 1 bits,
  // and increment the result by 1.
  // Returns false if there isn't enough data left for the specified type, or if
  // the value wouldn't fit in a uint32_t.
  bool ReadExponentialGolomb(uint32_t* val);
  // Reads signed exponential golomb values at the current offset. Signed
  // exponential golomb values are just the unsigned values mapped to the
  // sequence 0, 1, -1, 2, -2, etc. in order.
  bool ReadSignedExponentialGolomb(int32_t* val);

  // Moves current position |byte_count| bytes forward. Returns false if
  // there aren't enough bytes left in the buffer.
  bool ConsumeBytes(size_t byte_count);
  // Moves current position |bit_count| bits forward. Returns false if
  // there aren't enough bits left in the buffer.
  bool ConsumeBits(size_t bit_count);

  // Sets the current offset to the provied byte/bit offsets. The bit
  // offset is from the given byte, in the range [0,7].
  bool Seek(size_t byte_offset, size_t bit_offset);

 protected:
  const uint8_t* const bytes_;
  // The total size of |bytes_|.
  size_t byte_count_;
  // The current offset, in bytes, from the start of |bytes_|.
  size_t byte_offset_;
  // The current offset, in bits, into the current byte.
  size_t bit_offset_;

  RTC_DISALLOW_COPY_AND_ASSIGN(BitBuffer);
};

}  // namespace rtc

#endif  // COMMON_VIDEO_H265_LEGACY_BIT_BUFFER_H_
