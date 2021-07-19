// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// This header defines cross-platform ByteSwap() implementations for 16, 32 and
// 64-bit values, and NetToHostXX() / HostToNextXX() functions equivalent to
// the traditional ntohX() and htonX() functions.
// Use the functions defined here rather than using the platform-specific
// functions directly.

#ifndef BASE_SYS_BYTEORDER_H_
#define BASE_SYS_BYTEORDER_H_

#include <stdint.h>

#include "build/build_config.h"

#if defined(COMPILER_MSVC)
#include <stdlib.h>
#endif

namespace base {

// Returns a value with all bytes in |x| swapped, i.e. reverses the endianness.
inline uint16_t ByteSwap(uint16_t x) {
#if defined(COMPILER_MSVC)
  return _byteswap_ushort(x);
#else
  return __builtin_bswap16(x);
#endif
}

inline uint32_t ByteSwap(uint32_t x) {
#if defined(COMPILER_MSVC)
  return _byteswap_ulong(x);
#else
  return __builtin_bswap32(x);
#endif
}

inline uint64_t ByteSwap(uint64_t x) {
#if defined(COMPILER_MSVC)
  return _byteswap_uint64(x);
#else
  return __builtin_bswap64(x);
#endif
}

inline uintptr_t ByteSwapUintPtrT(uintptr_t x) {
  // We do it this way because some build configurations are ILP32 even when
  // defined(ARCH_CPU_64_BITS). Unfortunately, we can't use sizeof in #ifs. But,
  // because these conditionals are constexprs, the irrelevant branches will
  // likely be optimized away, so this construction should not result in code
  // bloat.
  static_assert(sizeof(uintptr_t) == 4 || sizeof(uintptr_t) == 8,
                "Unsupported uintptr_t size");
  if (sizeof(uintptr_t) == 4)
    return ByteSwap(static_cast<uint32_t>(x));
  return ByteSwap(static_cast<uint64_t>(x));
}

// Converts the bytes in |x| from host order (endianness) to little endian, and
// returns the result.
inline uint16_t ByteSwapToLE16(uint16_t x) {
#if defined(ARCH_CPU_LITTLE_ENDIAN)
  return x;
#else
  return ByteSwap(x);
#endif
}
inline uint32_t ByteSwapToLE32(uint32_t x) {
#if defined(ARCH_CPU_LITTLE_ENDIAN)
  return x;
#else
  return ByteSwap(x);
#endif
}
inline uint64_t ByteSwapToLE64(uint64_t x) {
#if defined(ARCH_CPU_LITTLE_ENDIAN)
  return x;
#else
  return ByteSwap(x);
#endif
}

// Converts the bytes in |x| from network to host order (endianness), and
// returns the result.
inline uint16_t NetToHost16(uint16_t x) {
#if defined(ARCH_CPU_LITTLE_ENDIAN)
  return ByteSwap(x);
#else
  return x;
#endif
}
inline uint32_t NetToHost32(uint32_t x) {
#if defined(ARCH_CPU_LITTLE_ENDIAN)
  return ByteSwap(x);
#else
  return x;
#endif
}
inline uint64_t NetToHost64(uint64_t x) {
#if defined(ARCH_CPU_LITTLE_ENDIAN)
  return ByteSwap(x);
#else
  return x;
#endif
}

// Converts the bytes in |x| from host to network order (endianness), and
// returns the result.
inline uint16_t HostToNet16(uint16_t x) {
#if defined(ARCH_CPU_LITTLE_ENDIAN)
  return ByteSwap(x);
#else
  return x;
#endif
}
inline uint32_t HostToNet32(uint32_t x) {
#if defined(ARCH_CPU_LITTLE_ENDIAN)
  return ByteSwap(x);
#else
  return x;
#endif
}
inline uint64_t HostToNet64(uint64_t x) {
#if defined(ARCH_CPU_LITTLE_ENDIAN)
  return ByteSwap(x);
#else
  return x;
#endif
}

}  // namespace base

#endif  // BASE_SYS_BYTEORDER_H_
