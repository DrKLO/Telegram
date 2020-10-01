// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_HASH_MD5_CONSTEXPR_INTERNAL_H_
#define BASE_HASH_MD5_CONSTEXPR_INTERNAL_H_

#include <array>
#include <cstddef>
#include <cstdint>

#include "base/hash/md5.h"
#include "base/logging.h"

namespace base {
namespace internal {

// The implementation here is based on the pseudocode provided by Wikipedia:
// https://en.wikipedia.org/wiki/MD5#Pseudocode
struct MD5CE {
  //////////////////////////////////////////////////////////////////////////////
  // DATA STRUCTURES

  // The data representation at each round is a 4-tuple of uint32_t.
  struct IntermediateData {
    uint32_t a;
    uint32_t b;
    uint32_t c;
    uint32_t d;
  };

  // The input data for a single round consists of 16 uint32_t (64 bytes).
  using RoundData = std::array<uint32_t, 16>;

  //////////////////////////////////////////////////////////////////////////////
  // CONSTANTS

  static constexpr std::array<uint32_t, 64> kConstants = {
      {0xd76aa478, 0xe8c7b756, 0x242070db, 0xc1bdceee, 0xf57c0faf, 0x4787c62a,
       0xa8304613, 0xfd469501, 0x698098d8, 0x8b44f7af, 0xffff5bb1, 0x895cd7be,
       0x6b901122, 0xfd987193, 0xa679438e, 0x49b40821, 0xf61e2562, 0xc040b340,
       0x265e5a51, 0xe9b6c7aa, 0xd62f105d, 0x02441453, 0xd8a1e681, 0xe7d3fbc8,
       0x21e1cde6, 0xc33707d6, 0xf4d50d87, 0x455a14ed, 0xa9e3e905, 0xfcefa3f8,
       0x676f02d9, 0x8d2a4c8a, 0xfffa3942, 0x8771f681, 0x6d9d6122, 0xfde5380c,
       0xa4beea44, 0x4bdecfa9, 0xf6bb4b60, 0xbebfbc70, 0x289b7ec6, 0xeaa127fa,
       0xd4ef3085, 0x04881d05, 0xd9d4d039, 0xe6db99e5, 0x1fa27cf8, 0xc4ac5665,
       0xf4292244, 0x432aff97, 0xab9423a7, 0xfc93a039, 0x655b59c3, 0x8f0ccc92,
       0xffeff47d, 0x85845dd1, 0x6fa87e4f, 0xfe2ce6e0, 0xa3014314, 0x4e0811a1,
       0xf7537e82, 0xbd3af235, 0x2ad7d2bb, 0xeb86d391}};

  static constexpr std::array<uint32_t, 16> kShifts = {
      {7, 12, 17, 22, 5, 9, 14, 20, 4, 11, 16, 23, 6, 10, 15, 21}};

  // The initial intermediate data.
  static constexpr IntermediateData kInitialIntermediateData{
      0x67452301, 0xefcdab89, 0x98badcfe, 0x10325476};

  //////////////////////////////////////////////////////////////////////////////
  // PADDED MESSAGE GENERATION / EXTRACTION

  // Given the message length, calculates the padded message length. There has
  // to be room for the 1-byte end-of-message marker, plus 8 bytes for the
  // uint64_t encoded message length, all rounded up to a multiple of 64 bytes.
  static constexpr uint32_t GetPaddedMessageLength(const uint32_t n) {
    return (((n + 1 + 8) + 63) / 64) * 64;
  }

  // Extracts the |i|th byte of a uint64_t, where |i == 0| extracts the least
  // significant byte. It is expected that 0 <= i < 8.
  static constexpr uint8_t ExtractByte(const uint64_t value, const uint32_t i) {
    DCHECK(i < 8);
    return static_cast<uint8_t>((value >> (i * 8)) & 0xff);
  }

  // Extracts the |i|th byte of a message of length |n|.
  static constexpr uint8_t GetPaddedMessageByte(const char* data,
                                                const uint32_t n,
                                                const uint32_t m,
                                                const uint32_t i) {
    DCHECK(i < m);
    DCHECK(n < m);
    DCHECK(m % 64 == 0);
    if (i < n) {
      // Emit the message itself...
      return data[i];
    } else if (i == n) {
      // ...followed by the end of message marker.
      return 0x80;
    } else if (i >= m - 8) {
      // The last 8 bytes encode the original message length times 8.
      return ExtractByte(n * 8, i - (m - 8));
    } else {
      // And everything else is just empyt padding.
      return 0;
    }
  }

  // Extracts the uint32_t starting at position |i| from the padded message
  // generate by the provided input |data| of length |n|. The bytes are treated
  // in little endian order.
  static constexpr uint32_t GetPaddedMessageWord(const char* data,
                                                 const uint32_t n,
                                                 const uint32_t m,
                                                 const uint32_t i) {
    DCHECK(i % 4 == 0);
    DCHECK(i < m);
    DCHECK(n < m);
    DCHECK(m % 64 == 0);
    return static_cast<uint32_t>(GetPaddedMessageByte(data, n, m, i)) |
           static_cast<uint32_t>((GetPaddedMessageByte(data, n, m, i + 1))
                                 << 8) |
           static_cast<uint32_t>((GetPaddedMessageByte(data, n, m, i + 2))
                                 << 16) |
           static_cast<uint32_t>((GetPaddedMessageByte(data, n, m, i + 3))
                                 << 24);
  }

  // Given an input buffer of length |n| bytes, extracts one round worth of data
  // starting at offset |i|.
  static constexpr RoundData GetRoundData(const char* data,
                                          const uint32_t n,
                                          const uint32_t m,
                                          const uint32_t i) {
    DCHECK(i % 64 == 0);
    DCHECK(i < m);
    DCHECK(n < m);
    DCHECK(m % 64 == 0);
    return RoundData{{GetPaddedMessageWord(data, n, m, i),
                      GetPaddedMessageWord(data, n, m, i + 4),
                      GetPaddedMessageWord(data, n, m, i + 8),
                      GetPaddedMessageWord(data, n, m, i + 12),
                      GetPaddedMessageWord(data, n, m, i + 16),
                      GetPaddedMessageWord(data, n, m, i + 20),
                      GetPaddedMessageWord(data, n, m, i + 24),
                      GetPaddedMessageWord(data, n, m, i + 28),
                      GetPaddedMessageWord(data, n, m, i + 32),
                      GetPaddedMessageWord(data, n, m, i + 36),
                      GetPaddedMessageWord(data, n, m, i + 40),
                      GetPaddedMessageWord(data, n, m, i + 44),
                      GetPaddedMessageWord(data, n, m, i + 48),
                      GetPaddedMessageWord(data, n, m, i + 52),
                      GetPaddedMessageWord(data, n, m, i + 56),
                      GetPaddedMessageWord(data, n, m, i + 60)}};
  }

  //////////////////////////////////////////////////////////////////////////////
  // HASH IMPLEMENTATION

  // Mixes elements |b|, |c| and |d| at round |i| of the calculation.
  static constexpr uint32_t CalcF(const uint32_t i,
                                  const uint32_t b,
                                  const uint32_t c,
                                  const uint32_t d) {
    DCHECK(i < 64);
    if (i < 16) {
      return d ^ (b & (c ^ d));
    } else if (i < 32) {
      return c ^ (d & (b ^ c));
    } else if (i < 48) {
      return b ^ c ^ d;
    } else {
      return c ^ (b | (~d));
    }
  }
  static constexpr uint32_t CalcF(const uint32_t i,
                                  const IntermediateData& intermediate) {
    return CalcF(i, intermediate.b, intermediate.c, intermediate.d);
  }

  // Calculates the indexing function at round |i|.
  static constexpr uint32_t CalcG(const uint32_t i) {
    DCHECK(i < 64);
    if (i < 16) {
      return i;
    } else if (i < 32) {
      return (5 * i + 1) % 16;
    } else if (i < 48) {
      return (3 * i + 5) % 16;
    } else {
      return (7 * i) % 16;
    }
  }

  // Calculates the rotation to be applied at round |i|.
  static constexpr uint32_t GetShift(const uint32_t i) {
    DCHECK(i < 64);
    return kShifts[(i / 16) * 4 + (i % 4)];
  }

  // Rotates to the left the given |value| by the given |bits|.
  static constexpr uint32_t LeftRotate(const uint32_t value,
                                       const uint32_t bits) {
    DCHECK(bits < 32);
    return (value << bits) | (value >> (32 - bits));
  }

  // Applies the ith step of mixing.
  static constexpr IntermediateData ApplyStep(
      const uint32_t i,
      const RoundData& data,
      const IntermediateData& intermediate) {
    DCHECK(i < 64);
    const uint32_t g = CalcG(i);
    DCHECK(g < 16);
    const uint32_t f =
        CalcF(i, intermediate) + intermediate.a + kConstants[i] + data[g];
    const uint32_t s = GetShift(i);
    return IntermediateData{/* a */ intermediate.d,
                            /* b */ intermediate.b + LeftRotate(f, s),
                            /* c */ intermediate.b,
                            /* d */ intermediate.c};
  }

  // Adds two IntermediateData together.
  static constexpr IntermediateData Add(const IntermediateData& intermediate1,
                                        const IntermediateData& intermediate2) {
    return IntermediateData{
        intermediate1.a + intermediate2.a, intermediate1.b + intermediate2.b,
        intermediate1.c + intermediate2.c, intermediate1.d + intermediate2.d};
  }

  // Processes an entire message.
  static constexpr IntermediateData ProcessMessage(const char* message,
                                                   const uint32_t n) {
    const uint32_t m = GetPaddedMessageLength(n);
    IntermediateData intermediate0 = kInitialIntermediateData;
    for (uint32_t offset = 0; offset < m; offset += 64) {
      RoundData data = GetRoundData(message, n, m, offset);
      IntermediateData intermediate1 = intermediate0;
      for (uint32_t i = 0; i < 64; ++i)
        intermediate1 = ApplyStep(i, data, intermediate1);
      intermediate0 = Add(intermediate0, intermediate1);
    }
    return intermediate0;
  }

  //////////////////////////////////////////////////////////////////////////////
  // HELPER FUNCTIONS

  // Converts an IntermediateData to a final digest.
  static constexpr MD5Digest IntermediateDataToMD5Digest(
      const IntermediateData& intermediate) {
    return MD5Digest{{static_cast<uint8_t>((intermediate.a >> 0) & 0xff),
                      static_cast<uint8_t>((intermediate.a >> 8) & 0xff),
                      static_cast<uint8_t>((intermediate.a >> 16) & 0xff),
                      static_cast<uint8_t>((intermediate.a >> 24) & 0xff),
                      static_cast<uint8_t>((intermediate.b >> 0) & 0xff),
                      static_cast<uint8_t>((intermediate.b >> 8) & 0xff),
                      static_cast<uint8_t>((intermediate.b >> 16) & 0xff),
                      static_cast<uint8_t>((intermediate.b >> 24) & 0xff),
                      static_cast<uint8_t>((intermediate.c >> 0) & 0xff),
                      static_cast<uint8_t>((intermediate.c >> 8) & 0xff),
                      static_cast<uint8_t>((intermediate.c >> 16) & 0xff),
                      static_cast<uint8_t>((intermediate.c >> 24) & 0xff),
                      static_cast<uint8_t>((intermediate.d >> 0) & 0xff),
                      static_cast<uint8_t>((intermediate.d >> 8) & 0xff),
                      static_cast<uint8_t>((intermediate.d >> 16) & 0xff),
                      static_cast<uint8_t>((intermediate.d >> 24) & 0xff)}};
  }

  static constexpr uint32_t StringLength(const char* string) {
    const char* end = string;
    while (*end != 0)
      ++end;
    // Double check that the precision losing conversion is safe.
    DCHECK(end >= string);
    DCHECK(static_cast<std::ptrdiff_t>(static_cast<uint32_t>(end - string)) ==
           (end - string));
    return static_cast<uint32_t>(end - string);
  }

  static constexpr uint32_t SwapEndian(uint32_t a) {
    return ((a & 0xff) << 24) | (((a >> 8) & 0xff) << 16) |
           (((a >> 16) & 0xff) << 8) | ((a >> 24) & 0xff);
  }

  //////////////////////////////////////////////////////////////////////////////
  // WRAPPER FUNCTIONS

  static constexpr MD5Digest Sum(const char* data, uint32_t n) {
    return IntermediateDataToMD5Digest(ProcessMessage(data, n));
  }

  static constexpr uint64_t Hash64(const char* data, uint32_t n) {
    IntermediateData intermediate = ProcessMessage(data, n);
    return (static_cast<uint64_t>(SwapEndian(intermediate.a)) << 32) |
           static_cast<uint64_t>(SwapEndian(intermediate.b));
  }

  static constexpr uint32_t Hash32(const char* data, uint32_t n) {
    IntermediateData intermediate = ProcessMessage(data, n);
    return SwapEndian(intermediate.a);
  }
};

}  // namespace internal

// Implementations of the functions exposed in the public header.

constexpr MD5Digest MD5SumConstexpr(const char* string) {
  return internal::MD5CE::Sum(string, internal::MD5CE::StringLength(string));
}

constexpr MD5Digest MD5SumConstexpr(const char* string, uint32_t length) {
  return internal::MD5CE::Sum(string, length);
}

constexpr uint64_t MD5Hash64Constexpr(const char* string) {
  return internal::MD5CE::Hash64(string, internal::MD5CE::StringLength(string));
}

constexpr uint64_t MD5Hash64Constexpr(const char* string, uint32_t length) {
  return internal::MD5CE::Hash64(string, length);
}

constexpr uint32_t MD5Hash32Constexpr(const char* string) {
  return internal::MD5CE::Hash32(string, internal::MD5CE::StringLength(string));
}

constexpr uint32_t MD5Hash32Constexpr(const char* string, uint32_t length) {
  return internal::MD5CE::Hash32(string, length);
}

}  // namespace base

#endif  // BASE_HASH_MD5_CONSTEXPR_INTERNAL_H_
