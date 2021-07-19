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
 * This file contains the function WebRtcSpl_Sqrt().
 * The description header can be found in signal_processing_library.h
 *
 */

#include "rtc_base/checks.h"
#include "common_audio/signal_processing/include/signal_processing_library.h"

int32_t WebRtcSpl_SqrtLocal(int32_t in);

int32_t WebRtcSpl_SqrtLocal(int32_t in)
{

    int16_t x_half, t16;
    int32_t A, B, x2;

    /* The following block performs:
     y=in/2
     x=y-2^30
     x_half=x/2^31
     t = 1 + (x_half) - 0.5*((x_half)^2) + 0.5*((x_half)^3) - 0.625*((x_half)^4)
         + 0.875*((x_half)^5)
     */

    B = in / 2;

    B = B - ((int32_t)0x40000000); // B = in/2 - 1/2
    x_half = (int16_t)(B >> 16);  // x_half = x/2 = (in-1)/2
    B = B + ((int32_t)0x40000000); // B = 1 + x/2
    B = B + ((int32_t)0x40000000); // Add 0.5 twice (since 1.0 does not exist in Q31)

    x2 = ((int32_t)x_half) * ((int32_t)x_half) * 2; // A = (x/2)^2
    A = -x2; // A = -(x/2)^2
    B = B + (A >> 1); // B = 1 + x/2 - 0.5*(x/2)^2

    A >>= 16;
    A = A * A * 2; // A = (x/2)^4
    t16 = (int16_t)(A >> 16);
    B += -20480 * t16 * 2;  // B = B - 0.625*A
    // After this, B = 1 + x/2 - 0.5*(x/2)^2 - 0.625*(x/2)^4

    A = x_half * t16 * 2;  // A = (x/2)^5
    t16 = (int16_t)(A >> 16);
    B += 28672 * t16 * 2;  // B = B + 0.875*A
    // After this, B = 1 + x/2 - 0.5*(x/2)^2 - 0.625*(x/2)^4 + 0.875*(x/2)^5

    t16 = (int16_t)(x2 >> 16);
    A = x_half * t16 * 2;  // A = x/2^3

    B = B + (A >> 1); // B = B + 0.5*A
    // After this, B = 1 + x/2 - 0.5*(x/2)^2 + 0.5*(x/2)^3 - 0.625*(x/2)^4 + 0.875*(x/2)^5

    B = B + ((int32_t)32768); // Round off bit

    return B;
}

int32_t WebRtcSpl_Sqrt(int32_t value)
{
    /*
     Algorithm:

     Six term Taylor Series is used here to compute the square root of a number
     y^0.5 = (1+x)^0.5 where x = y-1
     = 1+(x/2)-0.5*((x/2)^2+0.5*((x/2)^3-0.625*((x/2)^4+0.875*((x/2)^5)
     0.5 <= x < 1

     Example of how the algorithm works, with ut=sqrt(in), and
     with in=73632 and ut=271 (even shift value case):

     in=73632
     y= in/131072
     x=y-1
     t = 1 + (x/2) - 0.5*((x/2)^2) + 0.5*((x/2)^3) - 0.625*((x/2)^4) + 0.875*((x/2)^5)
     ut=t*(1/sqrt(2))*512

     or:

     in=73632
     in2=73632*2^14
     y= in2/2^31
     x=y-1
     t = 1 + (x/2) - 0.5*((x/2)^2) + 0.5*((x/2)^3) - 0.625*((x/2)^4) + 0.875*((x/2)^5)
     ut=t*(1/sqrt(2))
     ut2=ut*2^9

     which gives:

     in  = 73632
     in2 = 1206386688
     y   = 0.56176757812500
     x   = -0.43823242187500
     t   = 0.74973506527313
     ut  = 0.53014274874797
     ut2 = 2.714330873589594e+002

     or:

     in=73632
     in2=73632*2^14
     y=in2/2
     x=y-2^30
     x_half=x/2^31
     t = 1 + (x_half) - 0.5*((x_half)^2) + 0.5*((x_half)^3) - 0.625*((x_half)^4)
         + 0.875*((x_half)^5)
     ut=t*(1/sqrt(2))
     ut2=ut*2^9

     which gives:

     in  = 73632
     in2 = 1206386688
     y   = 603193344
     x   = -470548480
     x_half =  -0.21911621093750
     t   = 0.74973506527313
     ut  = 0.53014274874797
     ut2 = 2.714330873589594e+002

     */

    int16_t x_norm, nshift, t16, sh;
    int32_t A;

    int16_t k_sqrt_2 = 23170; // 1/sqrt2 (==5a82)

    A = value;

    // The convention in this function is to calculate sqrt(abs(A)). Negate the
    // input if it is negative.
    if (A < 0) {
        if (A == WEBRTC_SPL_WORD32_MIN) {
            // This number cannot be held in an int32_t after negating.
            // Map it to the maximum positive value.
            A = WEBRTC_SPL_WORD32_MAX;
        } else {
            A = -A;
        }
    } else if (A == 0) {
        return 0;  // sqrt(0) = 0
    }

    sh = WebRtcSpl_NormW32(A); // # shifts to normalize A
    A = WEBRTC_SPL_LSHIFT_W32(A, sh); // Normalize A
    if (A < (WEBRTC_SPL_WORD32_MAX - 32767))
    {
        A = A + ((int32_t)32768); // Round off bit
    } else
    {
        A = WEBRTC_SPL_WORD32_MAX;
    }

    x_norm = (int16_t)(A >> 16);  // x_norm = AH

    nshift = (sh / 2);
    RTC_DCHECK_GE(nshift, 0);

    A = (int32_t)WEBRTC_SPL_LSHIFT_W32((int32_t)x_norm, 16);
    A = WEBRTC_SPL_ABS_W32(A); // A = abs(x_norm<<16)
    A = WebRtcSpl_SqrtLocal(A); // A = sqrt(A)

    if (2 * nshift == sh) {
        // Even shift value case

        t16 = (int16_t)(A >> 16);  // t16 = AH

        A = k_sqrt_2 * t16 * 2;  // A = 1/sqrt(2)*t16
        A = A + ((int32_t)32768); // Round off
        A = A & ((int32_t)0x7fff0000); // Round off

        A >>= 15;  // A = A>>16

    } else
    {
        A >>= 16;  // A = A>>16
    }

    A = A & ((int32_t)0x0000ffff);
    A >>= nshift;  // De-normalize the result.

    return A;
}
