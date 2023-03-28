// Copyright 2017 The CRC32C Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file. See the AUTHORS file for names of contributors.

#ifndef CRC32C_CRC32C_CONFIG_H_
#define CRC32C_CRC32C_CONFIG_H_

// Define to 1 if building for a big-endian platform.
//#cmakedefine01 BYTE_ORDER_BIG_ENDIAN

// Define to 1 if the compiler has the __builtin_prefetch intrinsic.
#define HAVE_BUILTIN_PREFETCH 1

// Define to 1 if targeting X86 and the compiler has the _mm_prefetch intrinsic.

#if HAVE_SSE42 && (defined(_M_X64) || defined(__x86_64__))
#define HAVE_MM_PREFETCH 1
#endif

// Define to 1 if targeting X86 and the compiler has the _mm_crc32_u{8,32,64}
// intrinsics.
#if defined(__i386) || defined(__x86_64) || defined(_M_IX86)
//#define HAVE_SSE42 1
#endif

// Define to 1 if targeting ARM and the compiler has the __crc32c{b,h,w,d} and
// the vmull_p64 intrinsics.
#if defined(__aarch64__)
//#define HAVE_ARM64_CRC32C 1
#endif

// Define to 1 if the system libraries have the getauxval function in the
// <sys/auxv.h> header. Should be true on Linux and Android API level 20+.
#define HAVE_STRONG_GETAUXVAL 1

// Define to 1 if the compiler supports defining getauxval as a weak symbol.
// Should be true for any compiler that supports __attribute__((weak)).
#define HAVE_WEAK_GETAUXVAL

// Define to 1 if CRC32C tests have been built with Google Logging.
//#cmakedefine01 CRC32C_TESTS_BUILT_WITH_GLOG

#endif  // CRC32C_CRC32C_CONFIG_H_
