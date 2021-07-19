/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "common_audio/signal_processing/include/signal_processing_library.h"

#include "rtc_base/checks.h"
#include "rtc_base/sanitizer.h"

// TODO(Bjornv): Change the function parameter order to WebRTC code style.
// C version of WebRtcSpl_DownsampleFast() for generic platforms.
int WebRtcSpl_DownsampleFastC(const int16_t* data_in,
                              size_t data_in_length,
                              int16_t* data_out,
                              size_t data_out_length,
                              const int16_t* __restrict coefficients,
                              size_t coefficients_length,
                              int factor,
                              size_t delay) {
  int16_t* const original_data_out = data_out;
  size_t i = 0;
  size_t j = 0;
  int32_t out_s32 = 0;
  size_t endpos = delay + factor * (data_out_length - 1) + 1;

  // Return error if any of the running conditions doesn't meet.
  if (data_out_length == 0 || coefficients_length == 0
                           || data_in_length < endpos) {
    return -1;
  }

  rtc_MsanCheckInitialized(coefficients, sizeof(coefficients[0]),
                           coefficients_length);

  for (i = delay; i < endpos; i += factor) {
    out_s32 = 2048;  // Round value, 0.5 in Q12.

    for (j = 0; j < coefficients_length; j++) {
      // Negative overflow is permitted here, because this is
      // auto-regressive filters, and the state for each batch run is
      // stored in the "negative" positions of the output vector.
      rtc_MsanCheckInitialized(&data_in[(ptrdiff_t) i - (ptrdiff_t) j],
          sizeof(data_in[0]), 1);
      // out_s32 is in Q12 domain.
      out_s32 += coefficients[j] * data_in[(ptrdiff_t) i - (ptrdiff_t) j];
    }

    out_s32 >>= 12;  // Q0.

    // Saturate and store the output.
    *data_out++ = WebRtcSpl_SatW32ToW16(out_s32);
  }

  RTC_DCHECK_EQ(original_data_out + data_out_length, data_out);
  rtc_MsanCheckInitialized(original_data_out, sizeof(original_data_out[0]),
                           data_out_length);

  return 0;
}
