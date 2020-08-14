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
 * This file contains the function WebRtcSpl_ComplexFFT().
 * The description header can be found in signal_processing_library.h
 *
 */

#include "common_audio/signal_processing/complex_fft_tables.h"
#include "common_audio/signal_processing/include/signal_processing_library.h"
#include "rtc_base/system/arch.h"

#define CFFTSFT 14
#define CFFTRND 1
#define CFFTRND2 16384

#define CIFFTSFT 14
#define CIFFTRND 1


int WebRtcSpl_ComplexFFT(int16_t frfi[], int stages, int mode)
{
    int i, j, l, k, istep, n, m;
    int16_t wr, wi;
    int32_t tr32, ti32, qr32, qi32;

    /* The 1024-value is a constant given from the size of kSinTable1024[],
     * and should not be changed depending on the input parameter 'stages'
     */
    n = 1 << stages;
    if (n > 1024)
        return -1;

    l = 1;
    k = 10 - 1; /* Constant for given kSinTable1024[]. Do not change
         depending on the input parameter 'stages' */

    if (mode == 0)
    {
        // mode==0: Low-complexity and Low-accuracy mode
        while (l < n)
        {
            istep = l << 1;

            for (m = 0; m < l; ++m)
            {
                j = m << k;

                /* The 256-value is a constant given as 1/4 of the size of
                 * kSinTable1024[], and should not be changed depending on the input
                 * parameter 'stages'. It will result in 0 <= j < N_SINE_WAVE/2
                 */
                wr = kSinTable1024[j + 256];
                wi = -kSinTable1024[j];

                for (i = m; i < n; i += istep)
                {
                    j = i + l;

                    tr32 = (wr * frfi[2 * j] - wi * frfi[2 * j + 1]) >> 15;

                    ti32 = (wr * frfi[2 * j + 1] + wi * frfi[2 * j]) >> 15;

                    qr32 = (int32_t)frfi[2 * i];
                    qi32 = (int32_t)frfi[2 * i + 1];
                    frfi[2 * j] = (int16_t)((qr32 - tr32) >> 1);
                    frfi[2 * j + 1] = (int16_t)((qi32 - ti32) >> 1);
                    frfi[2 * i] = (int16_t)((qr32 + tr32) >> 1);
                    frfi[2 * i + 1] = (int16_t)((qi32 + ti32) >> 1);
                }
            }

            --k;
            l = istep;

        }

    } else
    {
        // mode==1: High-complexity and High-accuracy mode
        while (l < n)
        {
            istep = l << 1;

            for (m = 0; m < l; ++m)
            {
                j = m << k;

                /* The 256-value is a constant given as 1/4 of the size of
                 * kSinTable1024[], and should not be changed depending on the input
                 * parameter 'stages'. It will result in 0 <= j < N_SINE_WAVE/2
                 */
                wr = kSinTable1024[j + 256];
                wi = -kSinTable1024[j];

#ifdef WEBRTC_ARCH_ARM_V7
                int32_t wri = 0;
                __asm __volatile("pkhbt %0, %1, %2, lsl #16" : "=r"(wri) :
                    "r"((int32_t)wr), "r"((int32_t)wi));
#endif

                for (i = m; i < n; i += istep)
                {
                    j = i + l;

#ifdef WEBRTC_ARCH_ARM_V7
                    register int32_t frfi_r;
                    __asm __volatile(
                        "pkhbt %[frfi_r], %[frfi_even], %[frfi_odd],"
                        " lsl #16\n\t"
                        "smlsd %[tr32], %[wri], %[frfi_r], %[cfftrnd]\n\t"
                        "smladx %[ti32], %[wri], %[frfi_r], %[cfftrnd]\n\t"
                        :[frfi_r]"=&r"(frfi_r),
                         [tr32]"=&r"(tr32),
                         [ti32]"=r"(ti32)
                        :[frfi_even]"r"((int32_t)frfi[2*j]),
                         [frfi_odd]"r"((int32_t)frfi[2*j +1]),
                         [wri]"r"(wri),
                         [cfftrnd]"r"(CFFTRND));
#else
                    tr32 = wr * frfi[2 * j] - wi * frfi[2 * j + 1] + CFFTRND;

                    ti32 = wr * frfi[2 * j + 1] + wi * frfi[2 * j] + CFFTRND;
#endif

                    tr32 >>= 15 - CFFTSFT;
                    ti32 >>= 15 - CFFTSFT;

                    qr32 = ((int32_t)frfi[2 * i]) * (1 << CFFTSFT);
                    qi32 = ((int32_t)frfi[2 * i + 1]) * (1 << CFFTSFT);

                    frfi[2 * j] = (int16_t)(
                        (qr32 - tr32 + CFFTRND2) >> (1 + CFFTSFT));
                    frfi[2 * j + 1] = (int16_t)(
                        (qi32 - ti32 + CFFTRND2) >> (1 + CFFTSFT));
                    frfi[2 * i] = (int16_t)(
                        (qr32 + tr32 + CFFTRND2) >> (1 + CFFTSFT));
                    frfi[2 * i + 1] = (int16_t)(
                        (qi32 + ti32 + CFFTRND2) >> (1 + CFFTSFT));
                }
            }

            --k;
            l = istep;
        }
    }
    return 0;
}

int WebRtcSpl_ComplexIFFT(int16_t frfi[], int stages, int mode)
{
    size_t i, j, l, istep, n, m;
    int k, scale, shift;
    int16_t wr, wi;
    int32_t tr32, ti32, qr32, qi32;
    int32_t tmp32, round2;

    /* The 1024-value is a constant given from the size of kSinTable1024[],
     * and should not be changed depending on the input parameter 'stages'
     */
    n = ((size_t)1) << stages;
    if (n > 1024)
        return -1;

    scale = 0;

    l = 1;
    k = 10 - 1; /* Constant for given kSinTable1024[]. Do not change
         depending on the input parameter 'stages' */

    while (l < n)
    {
        // variable scaling, depending upon data
        shift = 0;
        round2 = 8192;

        tmp32 = WebRtcSpl_MaxAbsValueW16(frfi, 2 * n);
        if (tmp32 > 13573)
        {
            shift++;
            scale++;
            round2 <<= 1;
        }
        if (tmp32 > 27146)
        {
            shift++;
            scale++;
            round2 <<= 1;
        }

        istep = l << 1;

        if (mode == 0)
        {
            // mode==0: Low-complexity and Low-accuracy mode
            for (m = 0; m < l; ++m)
            {
                j = m << k;

                /* The 256-value is a constant given as 1/4 of the size of
                 * kSinTable1024[], and should not be changed depending on the input
                 * parameter 'stages'. It will result in 0 <= j < N_SINE_WAVE/2
                 */
                wr = kSinTable1024[j + 256];
                wi = kSinTable1024[j];

                for (i = m; i < n; i += istep)
                {
                    j = i + l;

                    tr32 = (wr * frfi[2 * j] - wi * frfi[2 * j + 1]) >> 15;

                    ti32 = (wr * frfi[2 * j + 1] + wi * frfi[2 * j]) >> 15;

                    qr32 = (int32_t)frfi[2 * i];
                    qi32 = (int32_t)frfi[2 * i + 1];
                    frfi[2 * j] = (int16_t)((qr32 - tr32) >> shift);
                    frfi[2 * j + 1] = (int16_t)((qi32 - ti32) >> shift);
                    frfi[2 * i] = (int16_t)((qr32 + tr32) >> shift);
                    frfi[2 * i + 1] = (int16_t)((qi32 + ti32) >> shift);
                }
            }
        } else
        {
            // mode==1: High-complexity and High-accuracy mode

            for (m = 0; m < l; ++m)
            {
                j = m << k;

                /* The 256-value is a constant given as 1/4 of the size of
                 * kSinTable1024[], and should not be changed depending on the input
                 * parameter 'stages'. It will result in 0 <= j < N_SINE_WAVE/2
                 */
                wr = kSinTable1024[j + 256];
                wi = kSinTable1024[j];

#ifdef WEBRTC_ARCH_ARM_V7
                int32_t wri = 0;
                __asm __volatile("pkhbt %0, %1, %2, lsl #16" : "=r"(wri) :
                    "r"((int32_t)wr), "r"((int32_t)wi));
#endif

                for (i = m; i < n; i += istep)
                {
                    j = i + l;

#ifdef WEBRTC_ARCH_ARM_V7
                    register int32_t frfi_r;
                    __asm __volatile(
                      "pkhbt %[frfi_r], %[frfi_even], %[frfi_odd], lsl #16\n\t"
                      "smlsd %[tr32], %[wri], %[frfi_r], %[cifftrnd]\n\t"
                      "smladx %[ti32], %[wri], %[frfi_r], %[cifftrnd]\n\t"
                      :[frfi_r]"=&r"(frfi_r),
                       [tr32]"=&r"(tr32),
                       [ti32]"=r"(ti32)
                      :[frfi_even]"r"((int32_t)frfi[2*j]),
                       [frfi_odd]"r"((int32_t)frfi[2*j +1]),
                       [wri]"r"(wri),
                       [cifftrnd]"r"(CIFFTRND)
                    );
#else

                    tr32 = wr * frfi[2 * j] - wi * frfi[2 * j + 1] + CIFFTRND;

                    ti32 = wr * frfi[2 * j + 1] + wi * frfi[2 * j] + CIFFTRND;
#endif
                    tr32 >>= 15 - CIFFTSFT;
                    ti32 >>= 15 - CIFFTSFT;

                    qr32 = ((int32_t)frfi[2 * i]) * (1 << CIFFTSFT);
                    qi32 = ((int32_t)frfi[2 * i + 1]) * (1 << CIFFTSFT);

                    frfi[2 * j] = (int16_t)(
                        (qr32 - tr32 + round2) >> (shift + CIFFTSFT));
                    frfi[2 * j + 1] = (int16_t)(
                        (qi32 - ti32 + round2) >> (shift + CIFFTSFT));
                    frfi[2 * i] = (int16_t)(
                        (qr32 + tr32 + round2) >> (shift + CIFFTSFT));
                    frfi[2 * i + 1] = (int16_t)(
                        (qi32 + ti32 + round2) >> (shift + CIFFTSFT));
                }
            }

        }
        --k;
        l = istep;
    }
    return scale;
}
