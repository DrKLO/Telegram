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
 * This file contains the resampling functions for 22 kHz.
 * The description header can be found in signal_processing_library.h
 *
 */

#include "common_audio/signal_processing/include/signal_processing_library.h"
#include "common_audio/signal_processing/resample_by_2_internal.h"

// Declaration of internally used functions
static void WebRtcSpl_32khzTo22khzIntToShort(const int32_t *In, int16_t *Out,
                                             int32_t K);

void WebRtcSpl_32khzTo22khzIntToInt(const int32_t *In, int32_t *Out,
                                    int32_t K);

// interpolation coefficients
static const int16_t kCoefficients32To22[5][9] = {
        {127, -712,  2359, -6333, 23456, 16775, -3695,  945, -154},
        {-39,  230,  -830,  2785, 32366, -2324,   760, -218,   38},
        {117, -663,  2222, -6133, 26634, 13070, -3174,  831, -137},
        {-77,  457, -1677,  5958, 31175, -4136,  1405, -408,   71},
        { 98, -560,  1900, -5406, 29240,  9423, -2480,  663, -110}
};

//////////////////////
// 22 kHz -> 16 kHz //
//////////////////////

// number of subblocks; options: 1, 2, 4, 5, 10
#define SUB_BLOCKS_22_16    5

// 22 -> 16 resampler
void WebRtcSpl_Resample22khzTo16khz(const int16_t* in, int16_t* out,
                                    WebRtcSpl_State22khzTo16khz* state, int32_t* tmpmem)
{
    int k;

    // process two blocks of 10/SUB_BLOCKS_22_16 ms (to reduce temp buffer size)
    for (k = 0; k < SUB_BLOCKS_22_16; k++)
    {
        ///// 22 --> 44 /////
        // int16_t  in[220/SUB_BLOCKS_22_16]
        // int32_t out[440/SUB_BLOCKS_22_16]
        /////
        WebRtcSpl_UpBy2ShortToInt(in, 220 / SUB_BLOCKS_22_16, tmpmem + 16, state->S_22_44);

        ///// 44 --> 32 /////
        // int32_t  in[440/SUB_BLOCKS_22_16]
        // int32_t out[320/SUB_BLOCKS_22_16]
        /////
        // copy state to and from input array
        tmpmem[8] = state->S_44_32[0];
        tmpmem[9] = state->S_44_32[1];
        tmpmem[10] = state->S_44_32[2];
        tmpmem[11] = state->S_44_32[3];
        tmpmem[12] = state->S_44_32[4];
        tmpmem[13] = state->S_44_32[5];
        tmpmem[14] = state->S_44_32[6];
        tmpmem[15] = state->S_44_32[7];
        state->S_44_32[0] = tmpmem[440 / SUB_BLOCKS_22_16 + 8];
        state->S_44_32[1] = tmpmem[440 / SUB_BLOCKS_22_16 + 9];
        state->S_44_32[2] = tmpmem[440 / SUB_BLOCKS_22_16 + 10];
        state->S_44_32[3] = tmpmem[440 / SUB_BLOCKS_22_16 + 11];
        state->S_44_32[4] = tmpmem[440 / SUB_BLOCKS_22_16 + 12];
        state->S_44_32[5] = tmpmem[440 / SUB_BLOCKS_22_16 + 13];
        state->S_44_32[6] = tmpmem[440 / SUB_BLOCKS_22_16 + 14];
        state->S_44_32[7] = tmpmem[440 / SUB_BLOCKS_22_16 + 15];

        WebRtcSpl_Resample44khzTo32khz(tmpmem + 8, tmpmem, 40 / SUB_BLOCKS_22_16);

        ///// 32 --> 16 /////
        // int32_t  in[320/SUB_BLOCKS_22_16]
        // int32_t out[160/SUB_BLOCKS_22_16]
        /////
        WebRtcSpl_DownBy2IntToShort(tmpmem, 320 / SUB_BLOCKS_22_16, out, state->S_32_16);

        // move input/output pointers 10/SUB_BLOCKS_22_16 ms seconds ahead
        in += 220 / SUB_BLOCKS_22_16;
        out += 160 / SUB_BLOCKS_22_16;
    }
}

// initialize state of 22 -> 16 resampler
void WebRtcSpl_ResetResample22khzTo16khz(WebRtcSpl_State22khzTo16khz* state)
{
    int k;
    for (k = 0; k < 8; k++)
    {
        state->S_22_44[k] = 0;
        state->S_44_32[k] = 0;
        state->S_32_16[k] = 0;
    }
}

//////////////////////
// 16 kHz -> 22 kHz //
//////////////////////

// number of subblocks; options: 1, 2, 4, 5, 10
#define SUB_BLOCKS_16_22    4

// 16 -> 22 resampler
void WebRtcSpl_Resample16khzTo22khz(const int16_t* in, int16_t* out,
                                    WebRtcSpl_State16khzTo22khz* state, int32_t* tmpmem)
{
    int k;

    // process two blocks of 10/SUB_BLOCKS_16_22 ms (to reduce temp buffer size)
    for (k = 0; k < SUB_BLOCKS_16_22; k++)
    {
        ///// 16 --> 32 /////
        // int16_t  in[160/SUB_BLOCKS_16_22]
        // int32_t out[320/SUB_BLOCKS_16_22]
        /////
        WebRtcSpl_UpBy2ShortToInt(in, 160 / SUB_BLOCKS_16_22, tmpmem + 8, state->S_16_32);

        ///// 32 --> 22 /////
        // int32_t  in[320/SUB_BLOCKS_16_22]
        // int32_t out[220/SUB_BLOCKS_16_22]
        /////
        // copy state to and from input array
        tmpmem[0] = state->S_32_22[0];
        tmpmem[1] = state->S_32_22[1];
        tmpmem[2] = state->S_32_22[2];
        tmpmem[3] = state->S_32_22[3];
        tmpmem[4] = state->S_32_22[4];
        tmpmem[5] = state->S_32_22[5];
        tmpmem[6] = state->S_32_22[6];
        tmpmem[7] = state->S_32_22[7];
        state->S_32_22[0] = tmpmem[320 / SUB_BLOCKS_16_22];
        state->S_32_22[1] = tmpmem[320 / SUB_BLOCKS_16_22 + 1];
        state->S_32_22[2] = tmpmem[320 / SUB_BLOCKS_16_22 + 2];
        state->S_32_22[3] = tmpmem[320 / SUB_BLOCKS_16_22 + 3];
        state->S_32_22[4] = tmpmem[320 / SUB_BLOCKS_16_22 + 4];
        state->S_32_22[5] = tmpmem[320 / SUB_BLOCKS_16_22 + 5];
        state->S_32_22[6] = tmpmem[320 / SUB_BLOCKS_16_22 + 6];
        state->S_32_22[7] = tmpmem[320 / SUB_BLOCKS_16_22 + 7];

        WebRtcSpl_32khzTo22khzIntToShort(tmpmem, out, 20 / SUB_BLOCKS_16_22);

        // move input/output pointers 10/SUB_BLOCKS_16_22 ms seconds ahead
        in += 160 / SUB_BLOCKS_16_22;
        out += 220 / SUB_BLOCKS_16_22;
    }
}

// initialize state of 16 -> 22 resampler
void WebRtcSpl_ResetResample16khzTo22khz(WebRtcSpl_State16khzTo22khz* state)
{
    int k;
    for (k = 0; k < 8; k++)
    {
        state->S_16_32[k] = 0;
        state->S_32_22[k] = 0;
    }
}

//////////////////////
// 22 kHz ->  8 kHz //
//////////////////////

// number of subblocks; options: 1, 2, 5, 10
#define SUB_BLOCKS_22_8     2

// 22 -> 8 resampler
void WebRtcSpl_Resample22khzTo8khz(const int16_t* in, int16_t* out,
                                   WebRtcSpl_State22khzTo8khz* state, int32_t* tmpmem)
{
    int k;

    // process two blocks of 10/SUB_BLOCKS_22_8 ms (to reduce temp buffer size)
    for (k = 0; k < SUB_BLOCKS_22_8; k++)
    {
        ///// 22 --> 22 lowpass /////
        // int16_t  in[220/SUB_BLOCKS_22_8]
        // int32_t out[220/SUB_BLOCKS_22_8]
        /////
        WebRtcSpl_LPBy2ShortToInt(in, 220 / SUB_BLOCKS_22_8, tmpmem + 16, state->S_22_22);

        ///// 22 --> 16 /////
        // int32_t  in[220/SUB_BLOCKS_22_8]
        // int32_t out[160/SUB_BLOCKS_22_8]
        /////
        // copy state to and from input array
        tmpmem[8] = state->S_22_16[0];
        tmpmem[9] = state->S_22_16[1];
        tmpmem[10] = state->S_22_16[2];
        tmpmem[11] = state->S_22_16[3];
        tmpmem[12] = state->S_22_16[4];
        tmpmem[13] = state->S_22_16[5];
        tmpmem[14] = state->S_22_16[6];
        tmpmem[15] = state->S_22_16[7];
        state->S_22_16[0] = tmpmem[220 / SUB_BLOCKS_22_8 + 8];
        state->S_22_16[1] = tmpmem[220 / SUB_BLOCKS_22_8 + 9];
        state->S_22_16[2] = tmpmem[220 / SUB_BLOCKS_22_8 + 10];
        state->S_22_16[3] = tmpmem[220 / SUB_BLOCKS_22_8 + 11];
        state->S_22_16[4] = tmpmem[220 / SUB_BLOCKS_22_8 + 12];
        state->S_22_16[5] = tmpmem[220 / SUB_BLOCKS_22_8 + 13];
        state->S_22_16[6] = tmpmem[220 / SUB_BLOCKS_22_8 + 14];
        state->S_22_16[7] = tmpmem[220 / SUB_BLOCKS_22_8 + 15];

        WebRtcSpl_Resample44khzTo32khz(tmpmem + 8, tmpmem, 20 / SUB_BLOCKS_22_8);

        ///// 16 --> 8 /////
        // int32_t in[160/SUB_BLOCKS_22_8]
        // int32_t out[80/SUB_BLOCKS_22_8]
        /////
        WebRtcSpl_DownBy2IntToShort(tmpmem, 160 / SUB_BLOCKS_22_8, out, state->S_16_8);

        // move input/output pointers 10/SUB_BLOCKS_22_8 ms seconds ahead
        in += 220 / SUB_BLOCKS_22_8;
        out += 80 / SUB_BLOCKS_22_8;
    }
}

// initialize state of 22 -> 8 resampler
void WebRtcSpl_ResetResample22khzTo8khz(WebRtcSpl_State22khzTo8khz* state)
{
    int k;
    for (k = 0; k < 8; k++)
    {
        state->S_22_22[k] = 0;
        state->S_22_22[k + 8] = 0;
        state->S_22_16[k] = 0;
        state->S_16_8[k] = 0;
    }
}

//////////////////////
//  8 kHz -> 22 kHz //
//////////////////////

// number of subblocks; options: 1, 2, 5, 10
#define SUB_BLOCKS_8_22     2

// 8 -> 22 resampler
void WebRtcSpl_Resample8khzTo22khz(const int16_t* in, int16_t* out,
                                   WebRtcSpl_State8khzTo22khz* state, int32_t* tmpmem)
{
    int k;

    // process two blocks of 10/SUB_BLOCKS_8_22 ms (to reduce temp buffer size)
    for (k = 0; k < SUB_BLOCKS_8_22; k++)
    {
        ///// 8 --> 16 /////
        // int16_t  in[80/SUB_BLOCKS_8_22]
        // int32_t out[160/SUB_BLOCKS_8_22]
        /////
        WebRtcSpl_UpBy2ShortToInt(in, 80 / SUB_BLOCKS_8_22, tmpmem + 18, state->S_8_16);

        ///// 16 --> 11 /////
        // int32_t  in[160/SUB_BLOCKS_8_22]
        // int32_t out[110/SUB_BLOCKS_8_22]
        /////
        // copy state to and from input array
        tmpmem[10] = state->S_16_11[0];
        tmpmem[11] = state->S_16_11[1];
        tmpmem[12] = state->S_16_11[2];
        tmpmem[13] = state->S_16_11[3];
        tmpmem[14] = state->S_16_11[4];
        tmpmem[15] = state->S_16_11[5];
        tmpmem[16] = state->S_16_11[6];
        tmpmem[17] = state->S_16_11[7];
        state->S_16_11[0] = tmpmem[160 / SUB_BLOCKS_8_22 + 10];
        state->S_16_11[1] = tmpmem[160 / SUB_BLOCKS_8_22 + 11];
        state->S_16_11[2] = tmpmem[160 / SUB_BLOCKS_8_22 + 12];
        state->S_16_11[3] = tmpmem[160 / SUB_BLOCKS_8_22 + 13];
        state->S_16_11[4] = tmpmem[160 / SUB_BLOCKS_8_22 + 14];
        state->S_16_11[5] = tmpmem[160 / SUB_BLOCKS_8_22 + 15];
        state->S_16_11[6] = tmpmem[160 / SUB_BLOCKS_8_22 + 16];
        state->S_16_11[7] = tmpmem[160 / SUB_BLOCKS_8_22 + 17];

        WebRtcSpl_32khzTo22khzIntToInt(tmpmem + 10, tmpmem, 10 / SUB_BLOCKS_8_22);

        ///// 11 --> 22 /////
        // int32_t  in[110/SUB_BLOCKS_8_22]
        // int16_t out[220/SUB_BLOCKS_8_22]
        /////
        WebRtcSpl_UpBy2IntToShort(tmpmem, 110 / SUB_BLOCKS_8_22, out, state->S_11_22);

        // move input/output pointers 10/SUB_BLOCKS_8_22 ms seconds ahead
        in += 80 / SUB_BLOCKS_8_22;
        out += 220 / SUB_BLOCKS_8_22;
    }
}

// initialize state of 8 -> 22 resampler
void WebRtcSpl_ResetResample8khzTo22khz(WebRtcSpl_State8khzTo22khz* state)
{
    int k;
    for (k = 0; k < 8; k++)
    {
        state->S_8_16[k] = 0;
        state->S_16_11[k] = 0;
        state->S_11_22[k] = 0;
    }
}

// compute two inner-products and store them to output array
static void WebRtcSpl_DotProdIntToInt(const int32_t* in1, const int32_t* in2,
                                      const int16_t* coef_ptr, int32_t* out1,
                                      int32_t* out2)
{
    int32_t tmp1 = 16384;
    int32_t tmp2 = 16384;
    int16_t coef;

    coef = coef_ptr[0];
    tmp1 += coef * in1[0];
    tmp2 += coef * in2[-0];

    coef = coef_ptr[1];
    tmp1 += coef * in1[1];
    tmp2 += coef * in2[-1];

    coef = coef_ptr[2];
    tmp1 += coef * in1[2];
    tmp2 += coef * in2[-2];

    coef = coef_ptr[3];
    tmp1 += coef * in1[3];
    tmp2 += coef * in2[-3];

    coef = coef_ptr[4];
    tmp1 += coef * in1[4];
    tmp2 += coef * in2[-4];

    coef = coef_ptr[5];
    tmp1 += coef * in1[5];
    tmp2 += coef * in2[-5];

    coef = coef_ptr[6];
    tmp1 += coef * in1[6];
    tmp2 += coef * in2[-6];

    coef = coef_ptr[7];
    tmp1 += coef * in1[7];
    tmp2 += coef * in2[-7];

    coef = coef_ptr[8];
    *out1 = tmp1 + coef * in1[8];
    *out2 = tmp2 + coef * in2[-8];
}

// compute two inner-products and store them to output array
static void WebRtcSpl_DotProdIntToShort(const int32_t* in1, const int32_t* in2,
                                        const int16_t* coef_ptr, int16_t* out1,
                                        int16_t* out2)
{
    int32_t tmp1 = 16384;
    int32_t tmp2 = 16384;
    int16_t coef;

    coef = coef_ptr[0];
    tmp1 += coef * in1[0];
    tmp2 += coef * in2[-0];

    coef = coef_ptr[1];
    tmp1 += coef * in1[1];
    tmp2 += coef * in2[-1];

    coef = coef_ptr[2];
    tmp1 += coef * in1[2];
    tmp2 += coef * in2[-2];

    coef = coef_ptr[3];
    tmp1 += coef * in1[3];
    tmp2 += coef * in2[-3];

    coef = coef_ptr[4];
    tmp1 += coef * in1[4];
    tmp2 += coef * in2[-4];

    coef = coef_ptr[5];
    tmp1 += coef * in1[5];
    tmp2 += coef * in2[-5];

    coef = coef_ptr[6];
    tmp1 += coef * in1[6];
    tmp2 += coef * in2[-6];

    coef = coef_ptr[7];
    tmp1 += coef * in1[7];
    tmp2 += coef * in2[-7];

    coef = coef_ptr[8];
    tmp1 += coef * in1[8];
    tmp2 += coef * in2[-8];

    // scale down, round and saturate
    tmp1 >>= 15;
    if (tmp1 > (int32_t)0x00007FFF)
        tmp1 = 0x00007FFF;
    if (tmp1 < (int32_t)0xFFFF8000)
        tmp1 = 0xFFFF8000;
    tmp2 >>= 15;
    if (tmp2 > (int32_t)0x00007FFF)
        tmp2 = 0x00007FFF;
    if (tmp2 < (int32_t)0xFFFF8000)
        tmp2 = 0xFFFF8000;
    *out1 = (int16_t)tmp1;
    *out2 = (int16_t)tmp2;
}

//   Resampling ratio: 11/16
// input:  int32_t (normalized, not saturated) :: size 16 * K
// output: int32_t (shifted 15 positions to the left, + offset 16384) :: size 11 * K
//      K: Number of blocks

void WebRtcSpl_32khzTo22khzIntToInt(const int32_t* In,
                                    int32_t* Out,
                                    int32_t K)
{
    /////////////////////////////////////////////////////////////
    // Filter operation:
    //
    // Perform resampling (16 input samples -> 11 output samples);
    // process in sub blocks of size 16 samples.
    int32_t m;

    for (m = 0; m < K; m++)
    {
        // first output sample
        Out[0] = ((int32_t)In[3] << 15) + (1 << 14);

        // sum and accumulate filter coefficients and input samples
        WebRtcSpl_DotProdIntToInt(&In[0], &In[22], kCoefficients32To22[0], &Out[1], &Out[10]);

        // sum and accumulate filter coefficients and input samples
        WebRtcSpl_DotProdIntToInt(&In[2], &In[20], kCoefficients32To22[1], &Out[2], &Out[9]);

        // sum and accumulate filter coefficients and input samples
        WebRtcSpl_DotProdIntToInt(&In[3], &In[19], kCoefficients32To22[2], &Out[3], &Out[8]);

        // sum and accumulate filter coefficients and input samples
        WebRtcSpl_DotProdIntToInt(&In[5], &In[17], kCoefficients32To22[3], &Out[4], &Out[7]);

        // sum and accumulate filter coefficients and input samples
        WebRtcSpl_DotProdIntToInt(&In[6], &In[16], kCoefficients32To22[4], &Out[5], &Out[6]);

        // update pointers
        In += 16;
        Out += 11;
    }
}

//   Resampling ratio: 11/16
// input:  int32_t (normalized, not saturated) :: size 16 * K
// output: int16_t (saturated) :: size 11 * K
//      K: Number of blocks

void WebRtcSpl_32khzTo22khzIntToShort(const int32_t *In,
                                      int16_t *Out,
                                      int32_t K)
{
    /////////////////////////////////////////////////////////////
    // Filter operation:
    //
    // Perform resampling (16 input samples -> 11 output samples);
    // process in sub blocks of size 16 samples.
    int32_t tmp;
    int32_t m;

    for (m = 0; m < K; m++)
    {
        // first output sample
        tmp = In[3];
        if (tmp > (int32_t)0x00007FFF)
            tmp = 0x00007FFF;
        if (tmp < (int32_t)0xFFFF8000)
            tmp = 0xFFFF8000;
        Out[0] = (int16_t)tmp;

        // sum and accumulate filter coefficients and input samples
        WebRtcSpl_DotProdIntToShort(&In[0], &In[22], kCoefficients32To22[0], &Out[1], &Out[10]);

        // sum and accumulate filter coefficients and input samples
        WebRtcSpl_DotProdIntToShort(&In[2], &In[20], kCoefficients32To22[1], &Out[2], &Out[9]);

        // sum and accumulate filter coefficients and input samples
        WebRtcSpl_DotProdIntToShort(&In[3], &In[19], kCoefficients32To22[2], &Out[3], &Out[8]);

        // sum and accumulate filter coefficients and input samples
        WebRtcSpl_DotProdIntToShort(&In[5], &In[17], kCoefficients32To22[3], &Out[4], &Out[7]);

        // sum and accumulate filter coefficients and input samples
        WebRtcSpl_DotProdIntToShort(&In[6], &In[16], kCoefficients32To22[4], &Out[5], &Out[6]);

        // update pointers
        In += 16;
        Out += 11;
    }
}
