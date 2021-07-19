// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_BIG_ENDIAN_H_
#define BASE_BIG_ENDIAN_H_

#include <stddef.h>
#include <stdint.h>

#include "base/base_export.h"
#include "base/strings/string_piece.h"

namespace base {

// Read an integer (signed or unsigned) from |buf| in Big Endian order.
// Note: this loop is unrolled with -O1 and above.
// NOTE(szym): glibc dns-canon.c use ntohs(*(uint16_t*)ptr) which is
// potentially unaligned.
// This would cause SIGBUS on ARMv5 or earlier and ARMv6-M.
template<typename T>
inline void ReadBigEndian(const char buf[], T* out) {
  *out = buf[0];
  for (size_t i = 1; i < sizeof(T); ++i) {
    *out <<= 8;
    // Must cast to uint8_t to avoid clobbering by sign extension.
    *out |= static_cast<uint8_t>(buf[i]);
  }
}

// Write an integer (signed or unsigned) |val| to |buf| in Big Endian order.
// Note: this loop is unrolled with -O1 and above.
template<typename T>
inline void WriteBigEndian(char buf[], T val) {
  for (size_t i = 0; i < sizeof(T); ++i) {
    buf[sizeof(T)-i-1] = static_cast<char>(val & 0xFF);
    val >>= 8;
  }
}

// Specializations to make clang happy about the (dead code) shifts above.
template <>
inline void ReadBigEndian<uint8_t>(const char buf[], uint8_t* out) {
  *out = buf[0];
}

template <>
inline void WriteBigEndian<uint8_t>(char buf[], uint8_t val) {
  buf[0] = static_cast<char>(val);
}

// Allows reading integers in network order (big endian) while iterating over
// an underlying buffer. All the reading functions advance the internal pointer.
class BASE_EXPORT BigEndianReader {
 public:
  BigEndianReader(const char* buf, size_t len);

  const char* ptr() const { return ptr_; }
  size_t remaining() const { return end_ - ptr_; }

  bool Skip(size_t len);
  bool ReadBytes(void* out, size_t len);
  // Creates a StringPiece in |out| that points to the underlying buffer.
  bool ReadPiece(base::StringPiece* out, size_t len);
  bool ReadU8(uint8_t* value);
  bool ReadU16(uint16_t* value);
  bool ReadU32(uint32_t* value);
  bool ReadU64(uint64_t* value);

  // Reads a length-prefixed region:
  // 1. reads a big-endian length L from the buffer;
  // 2. sets |*out| to a StringPiece over the next L many bytes
  // of the buffer (beyond the end of the bytes encoding the length); and
  // 3. skips the main reader past this L-byte substring.
  //
  // Fails if reading a U8 or U16 fails, or if the parsed length is greater
  // than the number of bytes remaining in the stream.
  //
  // On failure, leaves the stream at the same position
  // as before the call.
  bool ReadU8LengthPrefixed(base::StringPiece* out);
  bool ReadU16LengthPrefixed(base::StringPiece* out);

 private:
  // Hidden to promote type safety.
  template<typename T>
  bool Read(T* v);
  template <typename T>
  bool ReadLengthPrefixed(base::StringPiece* out);

  const char* ptr_;
  const char* end_;
};

// Allows writing integers in network order (big endian) while iterating over
// an underlying buffer. All the writing functions advance the internal pointer.
class BASE_EXPORT BigEndianWriter {
 public:
  BigEndianWriter(char* buf, size_t len);

  char* ptr() const { return ptr_; }
  size_t remaining() const { return end_ - ptr_; }

  bool Skip(size_t len);
  bool WriteBytes(const void* buf, size_t len);
  bool WriteU8(uint8_t value);
  bool WriteU16(uint16_t value);
  bool WriteU32(uint32_t value);
  bool WriteU64(uint64_t value);

 private:
  // Hidden to promote type safety.
  template<typename T>
  bool Write(T v);

  char* ptr_;
  char* end_;
};

}  // namespace base

#endif  // BASE_BIG_ENDIAN_H_
