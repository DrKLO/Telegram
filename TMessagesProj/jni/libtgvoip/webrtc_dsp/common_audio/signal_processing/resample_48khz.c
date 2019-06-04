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
 * This file contains resampling functions between 48 kHz and nb/wb.
 * The description header can be found in signal_processing_library.h
 *
 */

#include <string.h>
#include "common_audio/signal_processing/include/signal_processing_library.h"
#include "common_audio/signal_processing/resample_by_2_internal.h"

////////////////////////////
///// 48 kHz -> 16 kHz /////
////////////////////////////

// 48 -> 16 resampler
void WebRtcSpl_Resample48khzTo16khz(const int16_t* in, int16_t* out,
                                    WebRtcSpl_State48khzTo16khz* state, int32_t* tmpmem)
{
    ///// 48 --> 48(LP) /////
    // int16_t  in[480]
    // int32_t out[480]
    /////
    WebRtcSpl_LPBy2ShortToInt(in, 480, tmpmem + 16, state->S_48_48);

    ///// 48 --> 32 /////
    // int32_t  in[480]
    // int32_t out[320]
    /////
    // copy state to and from input array
    memcpy(tmpmem + 8, state->S_48_32, 8 * sizeof(int32_t));
    memcpy(state->S_48_32, tmpmem + 488, 8 * sizeof(int32_t));
    WebRtcSpl_Resample48khzTo32khz(tmpmem + 8, tmpmem, 160);

    ///// 32 --> 16 /////
    // int32_t  in[320]
    // int16_t out[160]
    /////
    WebRtcSpl_DownBy2IntToShort(tmpmem, 320, out, state->S_32_16);
}

// initialize state of 48 -> 16 resampler
void WebRtcSpl_ResetResample48khzTo16khz(WebRtcSpl_State48khzTo16khz* state)
{
    memset(state->S_48_48, 0, 16 * sizeof(int32_t));
    memset(state->S_48_32, 0, 8 * sizeof(int32_t));
    memset(state->S_32_16, 0, 8 * sizeof(int32_t));
}

////////////////////////////
///// 16 kHz -> 48 kHz /////
////////////////////////////

// 16 -> 48 resampler
void WebRtcSpl_Resample16khzTo48khz(const int16_t* in, int16_t* out,
                                    WebRtcSpl_State16khzTo48khz* state, int32_t* tmpmem)
{
    ///// 16 --> 32 /////
    // int16_t  in[160]
    // int32_t out[320]
    /////
    WebRtcSpl_UpBy2ShortToInt(in, 160, tmpmem + 16, state->S_16_32);

    ///// 32 --> 24 /////
    // int32_t  in[320]
    // int32_t out[240]
    // copy state to and from input array
    /////
    memcpy(tmpmem + 8, state->S_32_24, 8 * sizeof(int32_t));
    memcpy(state->S_32_24, tmpmem + 328, 8 * sizeof(int32_t));
    WebRtcSpl_Resample32khzTo24khz(tmpmem + 8, tmpmem, 80);

    ///// 24 --> 48 /////
    // int32_t  in[240]
    // int16_t out[480]
    /////
    WebRtcSpl_UpBy2IntToShort(tmpmem, 240, out, state->S_24_48);
}

// initialize state of 16 -> 48 resampler
void WebRtcSpl_ResetResample16khzTo48khz(WebRtcSpl_State16khzTo48khz* state)
{
    memset(state->S_16_32, 0, 8 * sizeof(int32_t));
    memset(state->S_32_24, 0, 8 * sizeof(int32_t));
    memset(state->S_24_48, 0, 8 * sizeof(int32_t));
}

////////////////////////////
///// 48 kHz ->  8 kHz /////
////////////////////////////

// 48 -> 8 resampler
void WebRtcSpl_Resample48khzTo8khz(const int16_t* in, int16_t* out,
                                   WebRtcSpl_State48khzTo8khz* state, int32_t* tmpmem)
{
    ///// 48 --> 24 /////
    // int16_t  in[480]
    // int32_t out[240]
    /////
    WebRtcSpl_DownBy2ShortToInt(in, 480, tmpmem + 256, state->S_48_24);

    ///// 24 --> 24(LP) /////
    // int32_t  in[240]
    // int32_t out[240]
    /////
    WebRtcSpl_LPBy2IntToInt(tmpmem + 256, 240, tmpmem + 16, state->S_24_24);

    ///// 24 --> 16 /////
    // int32_t  in[240]
    // int32_t out[160]
    /////
    // copy state to and from input array
    memcpy(tmpmem + 8, state->S_24_16, 8 * sizeof(int32_t));
    memcpy(state->S_24_16, tmpmem + 248, 8 * sizeof(int32_t));
    WebRtcSpl_Resample48khzTo32khz(tmpmem + 8, tmpmem, 80);

    ///// 16 --> 8 /////
    // int32_t  in[160]
    // int16_t out[80]
    /////
    WebRtcSpl_DownBy2IntToShort(tmpmem, 160, out, state->S_16_8);
}

// initialize state of 48 -> 8 resampler
void WebRtcSpl_ResetResample48khzTo8khz(WebRtcSpl_State48khzTo8khz* state)
{
    memset(state->S_48_24, 0, 8 * sizeof(int32_t));
    memset(state->S_24_24, 0, 16 * sizeof(int32_t));
    memset(state->S_24_16, 0, 8 * sizeof(int32_t));
    memset(state->S_16_8, 0, 8 * sizeof(int32_t));
}

////////////////////////////
/////  8 kHz -> 48 kHz /////
////////////////////////////

// 8 -> 48 resampler
void WebRtcSpl_Resample8khzTo48khz(const int16_t* in, int16_t* out,
                                   WebRtcSpl_State8khzTo48khz* state, int32_t* tmpmem)
{
    ///// 8 --> 16 /////
    // int16_t  in[80]
    // int32_t out[160]
    /////
    WebRtcSpl_UpBy2ShortToInt(in, 80, tmpmem + 264, state->S_8_16);

    ///// 16 --> 12 /////
    // int32_t  in[160]
    // int32_t out[120]
    /////
    // copy state to and from input array
    memcpy(tmpmem + 256, state->S_16_12, 8 * sizeof(int32_t));
    memcpy(state->S_16_12, tmpmem + 416, 8 * sizeof(int32_t));
    WebRtcSpl_Resample32khzTo24khz(tmpmem + 256, tmpmem + 240, 40);

    ///// 12 --> 24 /////
    // int32_t  in[120]
    // int16_t out[240]
    /////
    WebRtcSpl_UpBy2IntToInt(tmpmem + 240, 120, tmpmem, state->S_12_24);

    ///// 24 --> 48 /////
    // int32_t  in[240]
    // int16_t out[480]
    /////
    WebRtcSpl_UpBy2IntToShort(tmpmem, 240, out, state->S_24_48);
}

// initialize state of 8 -> 48 resampler
void WebRtcSpl_ResetResample8khzTo48khz(WebRtcSpl_State8khzTo48khz* state)
{
    memset(state->S_8_16, 0, 8 * sizeof(int32_t));
    memset(state->S_16_12, 0, 8 * sizeof(int32_t));
    memset(state->S_12_24, 0, 8 * sizeof(int32_t));
    memset(state->S_24_48, 0, 8 * sizeof(int32_t));
}
