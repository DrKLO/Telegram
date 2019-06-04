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
 * This file contains the function WebRtcSpl_FilterAR().
 * The description header can be found in signal_processing_library.h
 *
 */

#include "common_audio/signal_processing/include/signal_processing_library.h"

#include "rtc_base/checks.h"

size_t WebRtcSpl_FilterAR(const int16_t* a,
                          size_t a_length,
                          const int16_t* x,
                          size_t x_length,
                          int16_t* state,
                          size_t state_length,
                          int16_t* state_low,
                          size_t state_low_length,
                          int16_t* filtered,
                          int16_t* filtered_low,
                          size_t filtered_low_length)
{
    int64_t o;
    int32_t oLOW;
    size_t i, j, stop;
    const int16_t* x_ptr = &x[0];
    int16_t* filteredFINAL_ptr = filtered;
    int16_t* filteredFINAL_LOW_ptr = filtered_low;

    for (i = 0; i < x_length; i++)
    {
        // Calculate filtered[i] and filtered_low[i]
        const int16_t* a_ptr = &a[1];
        // The index can become negative, but the arrays will never be indexed
        // with it when negative. Nevertheless, the index cannot be a size_t
        // because of this.
        int filtered_ix = (int)i - 1;
        int16_t* state_ptr = &state[state_length - 1];
        int16_t* state_low_ptr = &state_low[state_length - 1];

        o = (int32_t)(*x_ptr++) * (1 << 12);
        oLOW = (int32_t)0;

        stop = (i < a_length) ? i + 1 : a_length;
        for (j = 1; j < stop; j++)
        {
          RTC_DCHECK_GE(filtered_ix, 0);
          o -= *a_ptr * filtered[filtered_ix];
          oLOW -= *a_ptr++ * filtered_low[filtered_ix];
          --filtered_ix;
        }
        for (j = i + 1; j < a_length; j++)
        {
          o -= *a_ptr * *state_ptr--;
          oLOW -= *a_ptr++ * *state_low_ptr--;
        }

        o += (oLOW >> 12);
        *filteredFINAL_ptr = (int16_t)((o + (int32_t)2048) >> 12);
        *filteredFINAL_LOW_ptr++ =
            (int16_t)(o - ((int32_t)(*filteredFINAL_ptr++) * (1 << 12)));
    }

    // Save the filter state
    if (x_length >= state_length)
    {
        WebRtcSpl_CopyFromEndW16(filtered, x_length, a_length - 1, state);
        WebRtcSpl_CopyFromEndW16(filtered_low, x_length, a_length - 1, state_low);
    } else
    {
        for (i = 0; i < state_length - x_length; i++)
        {
            state[i] = state[i + x_length];
            state_low[i] = state_low[i + x_length];
        }
        for (i = 0; i < x_length; i++)
        {
            state[state_length - x_length + i] = filtered[i];
            state[state_length - x_length + i] = filtered_low[i];
        }
    }

    return x_length;
}
