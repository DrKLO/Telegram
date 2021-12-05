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
 * This file contains the function WebRtcSpl_ReflCoefToLpc().
 * The description header can be found in signal_processing_library.h
 *
 */

#include "common_audio/signal_processing/include/signal_processing_library.h"

void WebRtcSpl_ReflCoefToLpc(const int16_t *k, int use_order, int16_t *a)
{
    int16_t any[WEBRTC_SPL_MAX_LPC_ORDER + 1];
    int16_t *aptr, *aptr2, *anyptr;
    const int16_t *kptr;
    int m, i;

    kptr = k;
    *a = 4096; // i.e., (Word16_MAX >> 3)+1.
    *any = *a;
    a[1] = *k >> 3;

    for (m = 1; m < use_order; m++)
    {
        kptr++;
        aptr = a;
        aptr++;
        aptr2 = &a[m];
        anyptr = any;
        anyptr++;

        any[m + 1] = *kptr >> 3;
        for (i = 0; i < m; i++)
        {
            *anyptr = *aptr + (int16_t)((*aptr2 * *kptr) >> 15);
            anyptr++;
            aptr++;
            aptr2--;
        }

        aptr = a;
        anyptr = any;
        for (i = 0; i < (m + 2); i++)
        {
            *aptr = *anyptr;
            aptr++;
            anyptr++;
        }
    }
}
