/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_FILTERBANK_INTERNAL_H_
#define MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_FILTERBANK_INTERNAL_H_

#include <stdint.h>

#if defined(__cplusplus) || defined(c_plusplus)
extern "C" {
#endif

/* Arguments:
 *   io:  Input/output, in Q0.
 *   len: Input, sample length.
 *   coefficient: Input.
 *   state: Input/output, filter state, in Q4.
 */
typedef void (*HighpassFilterFixDec32)(int16_t* io,
                                       int16_t len,
                                       const int16_t* coefficient,
                                       int32_t* state);
extern HighpassFilterFixDec32 WebRtcIsacfix_HighpassFilterFixDec32;

void WebRtcIsacfix_HighpassFilterFixDec32C(int16_t* io,
                                           int16_t len,
                                           const int16_t* coefficient,
                                           int32_t* state);

#if defined(MIPS_DSP_R1_LE)
void WebRtcIsacfix_HighpassFilterFixDec32MIPS(int16_t* io,
                                              int16_t len,
                                              const int16_t* coefficient,
                                              int32_t* state);
#endif

typedef void (*AllpassFilter2FixDec16)(
    int16_t* data_ch1,           // Input and output in channel 1, in Q0
    int16_t* data_ch2,           // Input and output in channel 2, in Q0
    const int16_t* factor_ch1,   // Scaling factor for channel 1, in Q15
    const int16_t* factor_ch2,   // Scaling factor for channel 2, in Q15
    const int length,            // Length of the data buffers
    int32_t* filter_state_ch1,   // Filter state for channel 1, in Q16
    int32_t* filter_state_ch2);  // Filter state for channel 2, in Q16
extern AllpassFilter2FixDec16 WebRtcIsacfix_AllpassFilter2FixDec16;

void WebRtcIsacfix_AllpassFilter2FixDec16C(int16_t* data_ch1,
                                           int16_t* data_ch2,
                                           const int16_t* factor_ch1,
                                           const int16_t* factor_ch2,
                                           const int length,
                                           int32_t* filter_state_ch1,
                                           int32_t* filter_state_ch2);

#if defined(WEBRTC_HAS_NEON)
void WebRtcIsacfix_AllpassFilter2FixDec16Neon(int16_t* data_ch1,
                                              int16_t* data_ch2,
                                              const int16_t* factor_ch1,
                                              const int16_t* factor_ch2,
                                              const int length,
                                              int32_t* filter_state_ch1,
                                              int32_t* filter_state_ch2);
#endif

#if defined(MIPS_DSP_R1_LE)
void WebRtcIsacfix_AllpassFilter2FixDec16MIPS(int16_t* data_ch1,
                                              int16_t* data_ch2,
                                              const int16_t* factor_ch1,
                                              const int16_t* factor_ch2,
                                              const int length,
                                              int32_t* filter_state_ch1,
                                              int32_t* filter_state_ch2);
#endif

#if defined(__cplusplus) || defined(c_plusplus)
}
#endif

#endif
/* WEBRTC_MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_FILTERBANK_INTERNAL_H_ */
