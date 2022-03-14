/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Parts of this file derived from Chromium's base/cpu.cc.

#include "rtc_base/system/arch.h"
#include "system_wrappers/include/cpu_features_wrapper.h"
#include "system_wrappers/include/field_trial.h"

#if defined(WEBRTC_ARCH_X86_FAMILY) && defined(_MSC_VER)
#include <intrin.h>
#endif

namespace webrtc {

// No CPU feature is available => straight C path.
int GetCPUInfoNoASM(CPUFeature feature) {
  (void)feature;
  return 0;
}

#if defined(WEBRTC_ARCH_X86_FAMILY)

#if defined(WEBRTC_ENABLE_AVX2)
// xgetbv returns the value of an Intel Extended Control Register (XCR).
// Currently only XCR0 is defined by Intel so `xcr` should always be zero.
static uint64_t xgetbv(uint32_t xcr) {
#if defined(_MSC_VER)
  return _xgetbv(xcr);
#else
  uint32_t eax, edx;

  __asm__ volatile("xgetbv" : "=a"(eax), "=d"(edx) : "c"(xcr));
  return (static_cast<uint64_t>(edx) << 32) | eax;
#endif  // _MSC_VER
}
#endif  // WEBRTC_ENABLE_AVX2

#ifndef _MSC_VER
// Intrinsic for "cpuid".
#if defined(__pic__) && defined(__i386__)
static inline void __cpuid(int cpu_info[4], int info_type) {
  __asm__ volatile(
      "mov %%ebx, %%edi\n"
      "cpuid\n"
      "xchg %%edi, %%ebx\n"
      : "=a"(cpu_info[0]), "=D"(cpu_info[1]), "=c"(cpu_info[2]),
        "=d"(cpu_info[3])
      : "a"(info_type));
}
#else
static inline void __cpuid(int cpu_info[4], int info_type) {
  __asm__ volatile("cpuid\n"
                   : "=a"(cpu_info[0]), "=b"(cpu_info[1]), "=c"(cpu_info[2]),
                     "=d"(cpu_info[3])
                   : "a"(info_type), "c"(0));
}
#endif
#endif  // _MSC_VER
#endif  // WEBRTC_ARCH_X86_FAMILY

#if defined(WEBRTC_ARCH_X86_FAMILY)
// Actual feature detection for x86.
int GetCPUInfo(CPUFeature feature) {
  int cpu_info[4];
  __cpuid(cpu_info, 1);
  if (feature == kSSE2) {
    return 0 != (cpu_info[3] & 0x04000000);
  }
  if (feature == kSSE3) {
    return 0 != (cpu_info[2] & 0x00000001);
  }
#if defined(WEBRTC_ENABLE_AVX2)
  if (feature == kAVX2 &&
      !webrtc::field_trial::IsEnabled("WebRTC-Avx2SupportKillSwitch")) {
    int cpu_info7[4];
    __cpuid(cpu_info7, 0);
    int num_ids = cpu_info7[0];
    if (num_ids < 7) {
      return 0;
    }
    // Interpret CPU feature information.
    __cpuid(cpu_info7, 7);

    // AVX instructions can be used when
    //     a) AVX are supported by the CPU,
    //     b) XSAVE is supported by the CPU,
    //     c) XSAVE is enabled by the kernel.
    // See http://software.intel.com/en-us/blogs/2011/04/14/is-avx-enabled
    // AVX2 support needs (avx_support && (cpu_info7[1] & 0x00000020) != 0;).
    return (cpu_info[2] & 0x10000000) != 0 &&
           (cpu_info[2] & 0x04000000) != 0 /* XSAVE */ &&
           (cpu_info[2] & 0x08000000) != 0 /* OSXSAVE */ &&
           (xgetbv(0) & 0x00000006) == 6 /* XSAVE enabled by kernel */ &&
           (cpu_info7[1] & 0x00000020) != 0;
  }
#endif  // WEBRTC_ENABLE_AVX2
  return 0;
}
#else
// Default to straight C for other platforms.
int GetCPUInfo(CPUFeature feature) {
  (void)feature;
  return 0;
}
#endif

}  // namespace webrtc
