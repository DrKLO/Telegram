/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/*
 * codec.h
 *
 * This header file contains the calls to the internal encoder
 * and decoder functions.
 *
 */

#ifndef MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_CODEC_H_
#define MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_CODEC_H_

#include "modules/audio_coding/codecs/isac/fix/source/structs.h"

#ifdef __cplusplus
extern "C" {
#endif

int WebRtcIsacfix_EstimateBandwidth(BwEstimatorstr* bwest_str,
                                    Bitstr_dec* streamdata,
                                    size_t packet_size,
                                    uint16_t rtp_seq_number,
                                    uint32_t send_ts,
                                    uint32_t arr_ts);

int WebRtcIsacfix_DecodeImpl(int16_t* signal_out16,
                             IsacFixDecoderInstance* ISACdec_obj,
                             size_t* current_framesamples);

void WebRtcIsacfix_DecodePlcImpl(int16_t* decoded,
                                 IsacFixDecoderInstance* ISACdec_obj,
                                 size_t* current_framesample);

int WebRtcIsacfix_EncodeImpl(int16_t* in,
                             IsacFixEncoderInstance* ISACenc_obj,
                             BwEstimatorstr* bw_estimatordata,
                             int16_t CodingMode);

int WebRtcIsacfix_EncodeStoredData(IsacFixEncoderInstance* ISACenc_obj,
                                   int BWnumber,
                                   float scale);

/* initialization functions */

void WebRtcIsacfix_InitMaskingEnc(MaskFiltstr_enc* maskdata);
void WebRtcIsacfix_InitMaskingDec(MaskFiltstr_dec* maskdata);

void WebRtcIsacfix_InitPreFilterbank(PreFiltBankstr* prefiltdata);

void WebRtcIsacfix_InitPostFilterbank(PostFiltBankstr* postfiltdata);

void WebRtcIsacfix_InitPitchFilter(PitchFiltstr* pitchfiltdata);

void WebRtcIsacfix_InitPitchAnalysis(PitchAnalysisStruct* State);

void WebRtcIsacfix_InitPlc(PLCstr* State);

/* transform functions */

void WebRtcIsacfix_InitTransform(void);

typedef void (*Time2Spec)(int16_t* inre1Q9,
                          int16_t* inre2Q9,
                          int16_t* outre,
                          int16_t* outim);
typedef void (*Spec2Time)(int16_t* inreQ7,
                          int16_t* inimQ7,
                          int32_t* outre1Q16,
                          int32_t* outre2Q16);

extern Time2Spec WebRtcIsacfix_Time2Spec;
extern Spec2Time WebRtcIsacfix_Spec2Time;

void WebRtcIsacfix_Time2SpecC(int16_t* inre1Q9,
                              int16_t* inre2Q9,
                              int16_t* outre,
                              int16_t* outim);
void WebRtcIsacfix_Spec2TimeC(int16_t* inreQ7,
                              int16_t* inimQ7,
                              int32_t* outre1Q16,
                              int32_t* outre2Q16);

#if defined(WEBRTC_HAS_NEON)
void WebRtcIsacfix_Time2SpecNeon(int16_t* inre1Q9,
                                 int16_t* inre2Q9,
                                 int16_t* outre,
                                 int16_t* outim);
void WebRtcIsacfix_Spec2TimeNeon(int16_t* inreQ7,
                                 int16_t* inimQ7,
                                 int32_t* outre1Q16,
                                 int32_t* outre2Q16);
#endif

#if defined(MIPS32_LE)
void WebRtcIsacfix_Time2SpecMIPS(int16_t* inre1Q9,
                                 int16_t* inre2Q9,
                                 int16_t* outre,
                                 int16_t* outim);
void WebRtcIsacfix_Spec2TimeMIPS(int16_t* inreQ7,
                                 int16_t* inimQ7,
                                 int32_t* outre1Q16,
                                 int32_t* outre2Q16);
#endif

/* filterbank functions */

void WebRtcIsacfix_SplitAndFilter1(int16_t* in,
                                   int16_t* LP16,
                                   int16_t* HP16,
                                   PreFiltBankstr* prefiltdata);

void WebRtcIsacfix_FilterAndCombine1(int16_t* tempin_ch1,
                                     int16_t* tempin_ch2,
                                     int16_t* out16,
                                     PostFiltBankstr* postfiltdata);

/* normalized lattice filters */

void WebRtcIsacfix_NormLatticeFilterMa(size_t orderCoef,
                                       int32_t* stateGQ15,
                                       int16_t* lat_inQ0,
                                       int16_t* filt_coefQ15,
                                       int32_t* gain_lo_hiQ17,
                                       int16_t lo_hi,
                                       int16_t* lat_outQ9);

void WebRtcIsacfix_NormLatticeFilterAr(size_t orderCoef,
                                       int16_t* stateGQ0,
                                       int32_t* lat_inQ25,
                                       int16_t* filt_coefQ15,
                                       int32_t* gain_lo_hiQ17,
                                       int16_t lo_hi,
                                       int16_t* lat_outQ0);

/* TODO(kma): Remove the following functions into individual header files. */

/* Internal functions in both C and ARM Neon versions */

int WebRtcIsacfix_AutocorrC(int32_t* __restrict r,
                            const int16_t* __restrict x,
                            int16_t N,
                            int16_t order,
                            int16_t* __restrict scale);

void WebRtcIsacfix_FilterMaLoopC(int16_t input0,
                                 int16_t input1,
                                 int32_t input2,
                                 int32_t* ptr0,
                                 int32_t* ptr1,
                                 int32_t* ptr2);

#if defined(WEBRTC_HAS_NEON)
int WebRtcIsacfix_AutocorrNeon(int32_t* __restrict r,
                               const int16_t* __restrict x,
                               int16_t N,
                               int16_t order,
                               int16_t* __restrict scale);

void WebRtcIsacfix_FilterMaLoopNeon(int16_t input0,
                                    int16_t input1,
                                    int32_t input2,
                                    int32_t* ptr0,
                                    int32_t* ptr1,
                                    int32_t* ptr2);
#endif

#if defined(MIPS32_LE)
int WebRtcIsacfix_AutocorrMIPS(int32_t* __restrict r,
                               const int16_t* __restrict x,
                               int16_t N,
                               int16_t order,
                               int16_t* __restrict scale);

void WebRtcIsacfix_FilterMaLoopMIPS(int16_t input0,
                                    int16_t input1,
                                    int32_t input2,
                                    int32_t* ptr0,
                                    int32_t* ptr1,
                                    int32_t* ptr2);
#endif

/* Function pointers associated with the above functions. */

typedef int (*AutocorrFix)(int32_t* __restrict r,
                           const int16_t* __restrict x,
                           int16_t N,
                           int16_t order,
                           int16_t* __restrict scale);
extern AutocorrFix WebRtcIsacfix_AutocorrFix;

typedef void (*FilterMaLoopFix)(int16_t input0,
                                int16_t input1,
                                int32_t input2,
                                int32_t* ptr0,
                                int32_t* ptr1,
                                int32_t* ptr2);
extern FilterMaLoopFix WebRtcIsacfix_FilterMaLoopFix;

#ifdef __cplusplus
}  // extern "C"
#endif

#endif /* MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_CODEC_H_ */
