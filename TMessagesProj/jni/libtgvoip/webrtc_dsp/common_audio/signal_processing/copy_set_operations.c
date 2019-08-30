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
 * This file contains the implementation of functions
 * WebRtcSpl_MemSetW16()
 * WebRtcSpl_MemSetW32()
 * WebRtcSpl_MemCpyReversedOrder()
 * WebRtcSpl_CopyFromEndW16()
 * WebRtcSpl_ZerosArrayW16()
 * WebRtcSpl_ZerosArrayW32()
 *
 * The description header can be found in signal_processing_library.h
 *
 */

#include <string.h>
#include "common_audio/signal_processing/include/signal_processing_library.h"


void WebRtcSpl_MemSetW16(int16_t *ptr, int16_t set_value, size_t length)
{
    size_t j;
    int16_t *arrptr = ptr;

    for (j = length; j > 0; j--)
    {
        *arrptr++ = set_value;
    }
}

void WebRtcSpl_MemSetW32(int32_t *ptr, int32_t set_value, size_t length)
{
    size_t j;
    int32_t *arrptr = ptr;

    for (j = length; j > 0; j--)
    {
        *arrptr++ = set_value;
    }
}

void WebRtcSpl_MemCpyReversedOrder(int16_t* dest,
                                   int16_t* source,
                                   size_t length)
{
    size_t j;
    int16_t* destPtr = dest;
    int16_t* sourcePtr = source;

    for (j = 0; j < length; j++)
    {
        *destPtr-- = *sourcePtr++;
    }
}

void WebRtcSpl_CopyFromEndW16(const int16_t *vector_in,
                              size_t length,
                              size_t samples,
                              int16_t *vector_out)
{
    // Copy the last <samples> of the input vector to vector_out
    WEBRTC_SPL_MEMCPY_W16(vector_out, &vector_in[length - samples], samples);
}

void WebRtcSpl_ZerosArrayW16(int16_t *vector, size_t length)
{
    WebRtcSpl_MemSetW16(vector, 0, length);
}

void WebRtcSpl_ZerosArrayW32(int32_t *vector, size_t length)
{
    WebRtcSpl_MemSetW32(vector, 0, length);
}
