/*
 * SpanDSP - a series of DSP components for telephony
 *
 * g722_decode.c - The ITU G.722 codec, decode part.
 *
 * Written by Steve Underwood <steveu@coppice.org>
 *
 * Copyright (C) 2005 Steve Underwood
 *
 *  Despite my general liking of the GPL, I place my own contributions
 *  to this code in the public domain for the benefit of all mankind -
 *  even the slimy ones who might try to proprietize my work and use it
 *  to my detriment.
 *
 * Based in part on a single channel G.722 codec which is:
 *
 * Copyright (c) CMU 1993
 * Computer Science, Speech Group
 * Chengxiang Lu and Alex Hauptmann
 *
 * $Id: g722_decode.c,v 1.15 2006/07/07 16:37:49 steveu Exp $
 *
 * Modifications for WebRtc, 2011/04/28, by tlegrand:
 * -Removed usage of inttypes.h and tgmath.h
 * -Changed to use WebRtc types
 * -Changed __inline__ to __inline
 * -Added saturation check on output
 */

/*! \file */


#include <memory.h>
#include <stdio.h>
#include <stdlib.h>

#include "modules/third_party/g722/g722_enc_dec.h"

#if !defined(FALSE)
#define FALSE 0
#endif
#if !defined(TRUE)
#define TRUE (!FALSE)
#endif

static __inline int16_t saturate(int32_t amp)
{
    int16_t amp16;

    /* Hopefully this is optimised for the common case - not clipping */
    amp16 = (int16_t) amp;
    if (amp == amp16)
        return amp16;
    if (amp > WEBRTC_INT16_MAX)
        return  WEBRTC_INT16_MAX;
    return  WEBRTC_INT16_MIN;
}
/*- End of function --------------------------------------------------------*/

static void block4(G722DecoderState *s, int band, int d);

static void block4(G722DecoderState *s, int band, int d)
{
    int wd1;
    int wd2;
    int wd3;
    int i;

    /* Block 4, RECONS */
    s->band[band].d[0] = d;
    s->band[band].r[0] = saturate(s->band[band].s + d);

    /* Block 4, PARREC */
    s->band[band].p[0] = saturate(s->band[band].sz + d);

    /* Block 4, UPPOL2 */
    for (i = 0;  i < 3;  i++)
        s->band[band].sg[i] = s->band[band].p[i] >> 15;
    wd1 = saturate(s->band[band].a[1] * 4);

    wd2 = (s->band[band].sg[0] == s->band[band].sg[1])  ?  -wd1  :  wd1;
    if (wd2 > 32767)
        wd2 = 32767;
    wd3 = (s->band[band].sg[0] == s->band[band].sg[2])  ?  128  :  -128;
    wd3 += (wd2 >> 7);
    wd3 += (s->band[band].a[2]*32512) >> 15;
    if (wd3 > 12288)
        wd3 = 12288;
    else if (wd3 < -12288)
        wd3 = -12288;
    s->band[band].ap[2] = wd3;

    /* Block 4, UPPOL1 */
    s->band[band].sg[0] = s->band[band].p[0] >> 15;
    s->band[band].sg[1] = s->band[band].p[1] >> 15;
    wd1 = (s->band[band].sg[0] == s->band[band].sg[1])  ?  192  :  -192;
    wd2 = (s->band[band].a[1]*32640) >> 15;

    s->band[band].ap[1] = saturate(wd1 + wd2);
    wd3 = saturate(15360 - s->band[band].ap[2]);
    if (s->band[band].ap[1] > wd3)
        s->band[band].ap[1] = wd3;
    else if (s->band[band].ap[1] < -wd3)
        s->band[band].ap[1] = -wd3;

    /* Block 4, UPZERO */
    wd1 = (d == 0)  ?  0  :  128;
    s->band[band].sg[0] = d >> 15;
    for (i = 1;  i < 7;  i++)
    {
        s->band[band].sg[i] = s->band[band].d[i] >> 15;
        wd2 = (s->band[band].sg[i] == s->band[band].sg[0])  ?  wd1  :  -wd1;
        wd3 = (s->band[band].b[i]*32640) >> 15;
        s->band[band].bp[i] = saturate(wd2 + wd3);
    }

    /* Block 4, DELAYA */
    for (i = 6;  i > 0;  i--)
    {
        s->band[band].d[i] = s->band[band].d[i - 1];
        s->band[band].b[i] = s->band[band].bp[i];
    }

    for (i = 2;  i > 0;  i--)
    {
        s->band[band].r[i] = s->band[band].r[i - 1];
        s->band[band].p[i] = s->band[band].p[i - 1];
        s->band[band].a[i] = s->band[band].ap[i];
    }

    /* Block 4, FILTEP */
    wd1 = saturate(s->band[band].r[1] + s->band[band].r[1]);
    wd1 = (s->band[band].a[1]*wd1) >> 15;
    wd2 = saturate(s->band[band].r[2] + s->band[band].r[2]);
    wd2 = (s->band[band].a[2]*wd2) >> 15;
    s->band[band].sp = saturate(wd1 + wd2);

    /* Block 4, FILTEZ */
    s->band[band].sz = 0;
    for (i = 6;  i > 0;  i--)
    {
        wd1 = saturate(s->band[band].d[i] + s->band[band].d[i]);
        s->band[band].sz += (s->band[band].b[i]*wd1) >> 15;
    }
    s->band[band].sz = saturate(s->band[band].sz);

    /* Block 4, PREDIC */
    s->band[band].s = saturate(s->band[band].sp + s->band[band].sz);
}
/*- End of function --------------------------------------------------------*/

G722DecoderState* WebRtc_g722_decode_init(G722DecoderState* s,
                                          int rate,
                                          int options) {
    s = s ? s : malloc(sizeof(*s));
    memset(s, 0, sizeof(*s));
    if (rate == 48000)
        s->bits_per_sample = 6;
    else if (rate == 56000)
        s->bits_per_sample = 7;
    else
        s->bits_per_sample = 8;
    if ((options & G722_SAMPLE_RATE_8000))
        s->eight_k = TRUE;
    if ((options & G722_PACKED)  &&  s->bits_per_sample != 8)
        s->packed = TRUE;
    else
        s->packed = FALSE;
    s->band[0].det = 32;
    s->band[1].det = 8;
    return s;
}
/*- End of function --------------------------------------------------------*/

int WebRtc_g722_decode_release(G722DecoderState *s)
{
    free(s);
    return 0;
}
/*- End of function --------------------------------------------------------*/

size_t WebRtc_g722_decode(G722DecoderState *s, int16_t amp[],
                          const uint8_t g722_data[], size_t len)
{
    static const int wl[8] = {-60, -30, 58, 172, 334, 538, 1198, 3042 };
    static const int rl42[16] = {0, 7, 6, 5, 4, 3, 2, 1,
                                 7, 6, 5, 4, 3,  2, 1, 0 };
    static const int ilb[32] =
    {
        2048, 2093, 2139, 2186, 2233, 2282, 2332,
        2383, 2435, 2489, 2543, 2599, 2656, 2714,
        2774, 2834, 2896, 2960, 3025, 3091, 3158,
        3228, 3298, 3371, 3444, 3520, 3597, 3676,
        3756, 3838, 3922, 4008
    };
    static const int wh[3] = {0, -214, 798};
    static const int rh2[4] = {2, 1, 2, 1};
    static const int qm2[4] = {-7408, -1616,  7408,   1616};
    static const int qm4[16] =
    {
              0, -20456, -12896,  -8968,
          -6288,  -4240,  -2584,  -1200,
          20456,  12896,   8968,   6288,
           4240,   2584,   1200,      0
    };
    static const int qm5[32] =
    {
           -280,   -280, -23352, -17560,
         -14120, -11664,  -9752,  -8184,
          -6864,  -5712,  -4696,  -3784,
          -2960,  -2208,  -1520,   -880,
          23352,  17560,  14120,  11664,
           9752,   8184,   6864,   5712,
           4696,   3784,   2960,   2208,
           1520,    880,    280,   -280
    };
    static const int qm6[64] =
    {
           -136,   -136,   -136,   -136,
         -24808, -21904, -19008, -16704,
         -14984, -13512, -12280, -11192,
         -10232,  -9360,  -8576,  -7856,
          -7192,  -6576,  -6000,  -5456,
          -4944,  -4464,  -4008,  -3576,
          -3168,  -2776,  -2400,  -2032,
          -1688,  -1360,  -1040,   -728,
          24808,  21904,  19008,  16704,
          14984,  13512,  12280,  11192,
          10232,   9360,   8576,   7856,
           7192,   6576,   6000,   5456,
           4944,   4464,   4008,   3576,
           3168,   2776,   2400,   2032,
           1688,   1360,   1040,    728,
            432,    136,   -432,   -136
    };
    static const int qmf_coeffs[12] =
    {
           3,  -11,   12,   32, -210,  951, 3876, -805,  362, -156,   53,  -11,
    };

    int dlowt;
    int rlow;
    int ihigh;
    int dhigh;
    int rhigh;
    int xout1;
    int xout2;
    int wd1;
    int wd2;
    int wd3;
    int code;
    size_t outlen;
    int i;
    size_t j;

    outlen = 0;
    rhigh = 0;
    for (j = 0;  j < len;  )
    {
        if (s->packed)
        {
            /* Unpack the code bits */
            if (s->in_bits < s->bits_per_sample)
            {
                s->in_buffer |= (g722_data[j++] << s->in_bits);
                s->in_bits += 8;
            }
            code = s->in_buffer & ((1 << s->bits_per_sample) - 1);
            s->in_buffer >>= s->bits_per_sample;
            s->in_bits -= s->bits_per_sample;
        }
        else
        {
            code = g722_data[j++];
        }

        switch (s->bits_per_sample)
        {
        default:
        case 8:
            wd1 = code & 0x3F;
            ihigh = (code >> 6) & 0x03;
            wd2 = qm6[wd1];
            wd1 >>= 2;
            break;
        case 7:
            wd1 = code & 0x1F;
            ihigh = (code >> 5) & 0x03;
            wd2 = qm5[wd1];
            wd1 >>= 1;
            break;
        case 6:
            wd1 = code & 0x0F;
            ihigh = (code >> 4) & 0x03;
            wd2 = qm4[wd1];
            break;
        }
        /* Block 5L, LOW BAND INVQBL */
        wd2 = (s->band[0].det*wd2) >> 15;
        /* Block 5L, RECONS */
        rlow = s->band[0].s + wd2;
        /* Block 6L, LIMIT */
        if (rlow > 16383)
            rlow = 16383;
        else if (rlow < -16384)
            rlow = -16384;

        /* Block 2L, INVQAL */
        wd2 = qm4[wd1];
        dlowt = (s->band[0].det*wd2) >> 15;

        /* Block 3L, LOGSCL */
        wd2 = rl42[wd1];
        wd1 = (s->band[0].nb*127) >> 7;
        wd1 += wl[wd2];
        if (wd1 < 0)
            wd1 = 0;
        else if (wd1 > 18432)
            wd1 = 18432;
        s->band[0].nb = wd1;

        /* Block 3L, SCALEL */
        wd1 = (s->band[0].nb >> 6) & 31;
        wd2 = 8 - (s->band[0].nb >> 11);
        wd3 = (wd2 < 0)  ?  (ilb[wd1] << -wd2)  :  (ilb[wd1] >> wd2);
        s->band[0].det = wd3 << 2;

        block4(s, 0, dlowt);

        if (!s->eight_k)
        {
            /* Block 2H, INVQAH */
            wd2 = qm2[ihigh];
            dhigh = (s->band[1].det*wd2) >> 15;
            /* Block 5H, RECONS */
            rhigh = dhigh + s->band[1].s;
            /* Block 6H, LIMIT */
            if (rhigh > 16383)
                rhigh = 16383;
            else if (rhigh < -16384)
                rhigh = -16384;

            /* Block 2H, INVQAH */
            wd2 = rh2[ihigh];
            wd1 = (s->band[1].nb*127) >> 7;
            wd1 += wh[wd2];
            if (wd1 < 0)
                wd1 = 0;
            else if (wd1 > 22528)
                wd1 = 22528;
            s->band[1].nb = wd1;

            /* Block 3H, SCALEH */
            wd1 = (s->band[1].nb >> 6) & 31;
            wd2 = 10 - (s->band[1].nb >> 11);
            wd3 = (wd2 < 0)  ?  (ilb[wd1] << -wd2)  :  (ilb[wd1] >> wd2);
            s->band[1].det = wd3 << 2;

            block4(s, 1, dhigh);
        }

        if (s->itu_test_mode)
        {
            amp[outlen++] = (int16_t) (rlow << 1);
            amp[outlen++] = (int16_t) (rhigh << 1);
        }
        else
        {
            if (s->eight_k)
            {
                amp[outlen++] = (int16_t) (rlow << 1);
            }
            else
            {
                /* Apply the receive QMF */
                for (i = 0;  i < 22;  i++)
                    s->x[i] = s->x[i + 2];
                s->x[22] = rlow + rhigh;
                s->x[23] = rlow - rhigh;

                xout1 = 0;
                xout2 = 0;
                for (i = 0;  i < 12;  i++)
                {
                    xout2 += s->x[2*i]*qmf_coeffs[i];
                    xout1 += s->x[2*i + 1]*qmf_coeffs[11 - i];
                }
                /* We shift by 12 to allow for the QMF filters (DC gain = 4096), less 1
                   to allow for the 15 bit input to the G.722 algorithm. */
                /* WebRtc, tlegrand: added saturation */
                amp[outlen++] = saturate(xout1 >> 11);
                amp[outlen++] = saturate(xout2 >> 11);
            }
        }
    }
    return outlen;
}
/*- End of function --------------------------------------------------------*/
/*- End of file ------------------------------------------------------------*/
