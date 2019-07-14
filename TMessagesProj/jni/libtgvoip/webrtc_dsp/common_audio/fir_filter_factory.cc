/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "common_audio/fir_filter_factory.h"

#include "common_audio/fir_filter_c.h"
#include "rtc_base/checks.h"
#include "rtc_base/system/arch.h"

#if defined(WEBRTC_ARCH_ARM_FAMILY) && defined(WEBRTC_HAS_NEON)
#include "common_audio/fir_filter_neon.h"
#elif defined(WEBRTC_ARCH_X86_FAMILY)
#include "common_audio/fir_filter_sse.h"
#include "system_wrappers/include/cpu_features_wrapper.h"  // kSSE2, WebRtc_G...
#endif

namespace webrtc {

FIRFilter* CreateFirFilter(const float* coefficients,
                           size_t coefficients_length,
                           size_t max_input_length) {
  if (!coefficients || coefficients_length <= 0 || max_input_length <= 0) {
    RTC_NOTREACHED();
    return nullptr;
  }

  FIRFilter* filter = nullptr;
// If we know the minimum architecture at compile time, avoid CPU detection.
#if defined(WEBRTC_ARCH_X86_FAMILY)
#if defined(__SSE2__)
  filter =
      new FIRFilterSSE2(coefficients, coefficients_length, max_input_length);
#else
  // x86 CPU detection required.
  if (WebRtc_GetCPUInfo(kSSE2)) {
    filter =
        new FIRFilterSSE2(coefficients, coefficients_length, max_input_length);
  } else {
    filter = new FIRFilterC(coefficients, coefficients_length);
  }
#endif
#elif defined(WEBRTC_HAS_NEON)
  filter =
      new FIRFilterNEON(coefficients, coefficients_length, max_input_length);
#else
  filter = new FIRFilterC(coefficients, coefficients_length);
#endif

  return filter;
}

}  // namespace webrtc
