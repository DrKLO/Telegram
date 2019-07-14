/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// This file contains platform-specific typedefs and defines.
// Much of it is derived from Chromium's build/build_config.h.

#ifndef RTC_BASE_SYSTEM_ARCH_H_
#define RTC_BASE_SYSTEM_ARCH_H_

// Processor architecture detection.  For more info on what's defined, see:
//   http://msdn.microsoft.com/en-us/library/b0084kay.aspx
//   http://www.agner.org/optimize/calling_conventions.pdf
//   or with gcc, run: "echo | gcc -E -dM -"
#if defined(_M_X64) || defined(__x86_64__)
#define WEBRTC_ARCH_X86_FAMILY
#define WEBRTC_ARCH_X86_64
#define WEBRTC_ARCH_64_BITS
#define WEBRTC_ARCH_LITTLE_ENDIAN
#elif defined(__aarch64__)
#define WEBRTC_ARCH_ARM_FAMILY
#define WEBRTC_ARCH_64_BITS
#define WEBRTC_ARCH_LITTLE_ENDIAN
#elif defined(_M_IX86) || defined(__i386__)
#define WEBRTC_ARCH_X86_FAMILY
#define WEBRTC_ARCH_X86
#define WEBRTC_ARCH_32_BITS
#define WEBRTC_ARCH_LITTLE_ENDIAN
#elif defined(__ARMEL__) || defined(__arm__) || defined(_M_ARM)
#define WEBRTC_ARCH_ARM_FAMILY
#define WEBRTC_ARCH_32_BITS
#define WEBRTC_ARCH_LITTLE_ENDIAN
#elif defined(__MIPSEL__)
#define WEBRTC_ARCH_MIPS_FAMILY
#if defined(__LP64__)
#define WEBRTC_ARCH_64_BITS
#else
#define WEBRTC_ARCH_32_BITS
#endif
#define WEBRTC_ARCH_LITTLE_ENDIAN
#elif defined(__pnacl__)
#define WEBRTC_ARCH_32_BITS
#define WEBRTC_ARCH_LITTLE_ENDIAN
#elif defined(__powerpc64__)
#if __BYTE_ORDER__ == __ORDER_LITTLE_ENDIAN__
#define WEBRTC_ARCH_LITTLE_ENDIAN
#else
#define WEBRTC_ARCH_BIG_ENDIAN
#endif
#define WEBRTC_ARCH_64_BITS
#elif defined(__powerpc__)
#define WEBRTC_ARCH_BIG_ENDIAN
#define WEBRTC_ARCH_32_BITS
#else
/* instead of failing, use typical unix defines... */
#if __BYTE_ORDER__ == __ORDER_LITTLE_ENDIAN__
#define WEBRTC_ARCH_LITTLE_ENDIAN
#elif __BYTE_ORDER__ == __ORDER_BIG_ENDIAN__
#define WEBRTC_ARCH_BIG_ENDIAN
#else
#error __BYTE_ORDER__ is not defined
#endif
#if defined(__LP64__)
#define WEBRTC_ARCH_64_BITS
#else
#define WEBRTC_ARCH_32_BITS
#endif
#endif

#if !(defined(WEBRTC_ARCH_LITTLE_ENDIAN) ^ defined(WEBRTC_ARCH_BIG_ENDIAN))
#error Define either WEBRTC_ARCH_LITTLE_ENDIAN or WEBRTC_ARCH_BIG_ENDIAN
#endif

#endif  // RTC_BASE_SYSTEM_ARCH_H_
