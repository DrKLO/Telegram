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
 * This file contains implementations of the divisions
 * WebRtcSpl_DivU32U16()
 * WebRtcSpl_DivW32W16()
 * WebRtcSpl_DivW32W16ResW16()
 * WebRtcSpl_DivResultInQ31()
 * WebRtcSpl_DivW32HiLow()
 *
 * The description header can be found in signal_processing_library.h
 *
 */

#include "common_audio/signal_processing/include/signal_processing_library.h"
#include "rtc_base/sanitizer.h"

uint32_t WebRtcSpl_DivU32U16(uint32_t num, uint16_t den)
{
    // Guard against division with 0
    if (den != 0)
    {
        return (uint32_t)(num / den);
    } else
    {
        return (uint32_t)0xFFFFFFFF;
    }
}

int32_t WebRtcSpl_DivW32W16(int32_t num, int16_t den)
{
    // Guard against division with 0
    if (den != 0)
    {
        return (int32_t)(num / den);
    } else
    {
        return (int32_t)0x7FFFFFFF;
    }
}

int16_t WebRtcSpl_DivW32W16ResW16(int32_t num, int16_t den)
{
    // Guard against division with 0
    if (den != 0)
    {
        return (int16_t)(num / den);
    } else
    {
        return (int16_t)0x7FFF;
    }
}

int32_t WebRtcSpl_DivResultInQ31(int32_t num, int32_t den)
{
    int32_t L_num = num;
    int32_t L_den = den;
    int32_t div = 0;
    int k = 31;
    int change_sign = 0;

    if (num == 0)
        return 0;

    if (num < 0)
    {
        change_sign++;
        L_num = -num;
    }
    if (den < 0)
    {
        change_sign++;
        L_den = -den;
    }
    while (k--)
    {
        div <<= 1;
        L_num <<= 1;
        if (L_num >= L_den)
        {
            L_num -= L_den;
            div++;
        }
    }
    if (change_sign == 1)
    {
        div = -div;
    }
    return div;
}

int32_t WebRtcSpl_DivW32HiLow(int32_t num, int16_t den_hi, int16_t den_low)
{
    int16_t approx, tmp_hi, tmp_low, num_hi, num_low;
    int32_t tmpW32;

    approx = (int16_t)WebRtcSpl_DivW32W16((int32_t)0x1FFFFFFF, den_hi);
    // result in Q14 (Note: 3FFFFFFF = 0.5 in Q30)

    // tmpW32 = 1/den = approx * (2.0 - den * approx) (in Q30)
    tmpW32 = (den_hi * approx << 1) + ((den_low * approx >> 15) << 1);
    // tmpW32 = den * approx

    // result in Q30 (tmpW32 = 2.0-(den*approx))
    tmpW32 = (int32_t)((int64_t)0x7fffffffL - tmpW32);

    // Store tmpW32 in hi and low format
    tmp_hi = (int16_t)(tmpW32 >> 16);
    tmp_low = (int16_t)((tmpW32 - ((int32_t)tmp_hi << 16)) >> 1);

    // tmpW32 = 1/den in Q29
    tmpW32 = (tmp_hi * approx + (tmp_low * approx >> 15)) << 1;

    // 1/den in hi and low format
    tmp_hi = (int16_t)(tmpW32 >> 16);
    tmp_low = (int16_t)((tmpW32 - ((int32_t)tmp_hi << 16)) >> 1);

    // Store num in hi and low format
    num_hi = (int16_t)(num >> 16);
    num_low = (int16_t)((num - ((int32_t)num_hi << 16)) >> 1);

    // num * (1/den) by 32 bit multiplication (result in Q28)

    tmpW32 = num_hi * tmp_hi + (num_hi * tmp_low >> 15) +
        (num_low * tmp_hi >> 15);

    // Put result in Q31 (convert from Q28)
    tmpW32 = WEBRTC_SPL_LSHIFT_W32(tmpW32, 3);

    return tmpW32;
}
