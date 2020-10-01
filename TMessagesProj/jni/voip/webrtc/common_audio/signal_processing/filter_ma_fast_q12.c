/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */


/*
 * This file contains the function WebRtcSpl_FilterMAFastQ12().
 * The description header can be found in signal_processing_library.h
 *
 */

#include "common_audio/signal_processing/include/signal_processing_library.h"

#include "rtc_base/sanitizer.h"

void WebRtcSpl_FilterMAFastQ12(const int16_t* in_ptr,
                               int16_t* out_ptr,
                               const int16_t* B,
                               size_t B_length,
                               size_t length)
{
    size_t i, j;

    rtc_MsanCheckInitialized(B, sizeof(B[0]), B_length);
    rtc_MsanCheckInitialized(in_ptr - B_length + 1, sizeof(in_ptr[0]),
                             B_length + length - 1);

    for (i = 0; i < length; i++)
    {
        int32_t o = 0;

        for (j = 0; j < B_length; j++)
        {
          // Negative overflow is permitted here, because this is
          // auto-regressive filters, and the state for each batch run is
          // stored in the "negative" positions of the output vector.
          o += B[j] * in_ptr[(ptrdiff_t) i - (ptrdiff_t) j];
        }

        // If output is higher than 32768, saturate it. Same with negative side
        // 2^27 = 134217728, which corresponds to 32768 in Q12

        // Saturate the output
        o = WEBRTC_SPL_SAT((int32_t)134215679, o, (int32_t)-134217728);

        *out_ptr++ = (int16_t)((o + (int32_t)2048) >> 12);
    }
    return;
}
