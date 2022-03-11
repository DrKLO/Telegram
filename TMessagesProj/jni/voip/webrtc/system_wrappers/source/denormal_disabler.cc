/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "system_wrappers/include/denormal_disabler.h"

#include "rtc_base/checks.h"

namespace webrtc {
namespace {

#if defined(WEBRTC_ARCH_X86_FAMILY) && defined(__clang__)
#define WEBRTC_DENORMAL_DISABLER_X86_SUPPORTED
#endif

#if defined(WEBRTC_DENORMAL_DISABLER_X86_SUPPORTED) || \
    defined(WEBRTC_ARCH_ARM_FAMILY)
#define WEBRTC_DENORMAL_DISABLER_SUPPORTED
#endif

constexpr int kUnspecifiedStatusWord = -1;

#if defined(WEBRTC_DENORMAL_DISABLER_SUPPORTED)

// Control register bit mask to disable denormals on the hardware.
#if defined(WEBRTC_DENORMAL_DISABLER_X86_SUPPORTED)
// On x86 two bits are used: flush-to-zero (FTZ) and denormals-are-zero (DAZ).
constexpr int kDenormalBitMask = 0x8040;
#elif defined(WEBRTC_ARCH_ARM_FAMILY)
// On ARM one bit is used: flush-to-zero (FTZ).
constexpr int kDenormalBitMask = 1 << 24;
#endif

// Reads the relevant CPU control register and returns its value for supported
// architectures and compilers. Otherwise returns `kUnspecifiedStatusWord`.
int ReadStatusWord() {
  int result = kUnspecifiedStatusWord;
#if defined(WEBRTC_DENORMAL_DISABLER_X86_SUPPORTED)
  asm volatile("stmxcsr %0" : "=m"(result));
#elif defined(WEBRTC_ARCH_ARM_FAMILY) && defined(WEBRTC_ARCH_32_BITS)
  asm volatile("vmrs %[result], FPSCR" : [result] "=r"(result));
#elif defined(WEBRTC_ARCH_ARM_FAMILY) && defined(WEBRTC_ARCH_64_BITS)
  asm volatile("mrs %x[result], FPCR" : [result] "=r"(result));
#endif
  return result;
}

// Writes `status_word` in the relevant CPU control register if the architecture
// and the compiler are supported.
void SetStatusWord(int status_word) {
#if defined(WEBRTC_DENORMAL_DISABLER_X86_SUPPORTED)
  asm volatile("ldmxcsr %0" : : "m"(status_word));
#elif defined(WEBRTC_ARCH_ARM_FAMILY) && defined(WEBRTC_ARCH_32_BITS)
  asm volatile("vmsr FPSCR, %[src]" : : [src] "r"(status_word));
#elif defined(WEBRTC_ARCH_ARM_FAMILY) && defined(WEBRTC_ARCH_64_BITS)
  asm volatile("msr FPCR, %x[src]" : : [src] "r"(status_word));
#endif
}

// Returns true if the status word indicates that denormals are enabled.
constexpr bool DenormalsEnabled(int status_word) {
  return (status_word & kDenormalBitMask) != kDenormalBitMask;
}

#endif  // defined(WEBRTC_DENORMAL_DISABLER_SUPPORTED)

}  // namespace

#if defined(WEBRTC_DENORMAL_DISABLER_SUPPORTED)
DenormalDisabler::DenormalDisabler(bool enabled)
    : status_word_(enabled ? ReadStatusWord() : kUnspecifiedStatusWord),
      disabling_activated_(enabled && DenormalsEnabled(status_word_)) {
  if (disabling_activated_) {
    RTC_DCHECK_NE(status_word_, kUnspecifiedStatusWord);
    SetStatusWord(status_word_ | kDenormalBitMask);
    RTC_DCHECK(!DenormalsEnabled(ReadStatusWord()));
  }
}

bool DenormalDisabler::IsSupported() {
  return true;
}

DenormalDisabler::~DenormalDisabler() {
  if (disabling_activated_) {
    RTC_DCHECK_NE(status_word_, kUnspecifiedStatusWord);
    SetStatusWord(status_word_);
  }
}
#else
DenormalDisabler::DenormalDisabler(bool enabled)
    : status_word_(kUnspecifiedStatusWord), disabling_activated_(false) {}

bool DenormalDisabler::IsSupported() {
  return false;
}

DenormalDisabler::~DenormalDisabler() = default;
#endif

}  // namespace webrtc
