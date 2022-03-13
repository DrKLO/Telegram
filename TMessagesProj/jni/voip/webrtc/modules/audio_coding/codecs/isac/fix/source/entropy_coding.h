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
 * entropy_coding.h
 *
 * This header file contains all of the functions used to arithmetically
 * encode the iSAC bistream
 *
 */

#ifndef MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_ENTROPY_CODING_H_
#define MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_ENTROPY_CODING_H_

#include "modules/audio_coding/codecs/isac/fix/source/structs.h"

/* decode complex spectrum (return number of bytes in stream) */
int WebRtcIsacfix_DecodeSpec(Bitstr_dec* streamdata,
                             int16_t* frQ7,
                             int16_t* fiQ7,
                             int16_t AvgPitchGain_Q12);

/* encode complex spectrum */
int WebRtcIsacfix_EncodeSpec(const int16_t* fr,
                             const int16_t* fi,
                             Bitstr_enc* streamdata,
                             int16_t AvgPitchGain_Q12);

/* decode & dequantize LPC Coef */
int WebRtcIsacfix_DecodeLpcCoef(Bitstr_dec* streamdata,
                                int32_t* LPCCoefQ17,
                                int32_t* gain_lo_hiQ17,
                                int16_t* outmodel);

int WebRtcIsacfix_DecodeLpc(int32_t* gain_lo_hiQ17,
                            int16_t* LPCCoef_loQ15,
                            int16_t* LPCCoef_hiQ15,
                            Bitstr_dec* streamdata,
                            int16_t* outmodel);

/* quantize & code LPC Coef */
int WebRtcIsacfix_EncodeLpc(int32_t* gain_lo_hiQ17,
                            int16_t* LPCCoef_loQ15,
                            int16_t* LPCCoef_hiQ15,
                            int16_t* model,
                            int32_t* sizeQ11,
                            Bitstr_enc* streamdata,
                            IsacSaveEncoderData* encData,
                            transcode_obj* transcodeParam);

int WebRtcIsacfix_EstCodeLpcGain(int32_t* gain_lo_hiQ17,
                                 Bitstr_enc* streamdata,
                                 IsacSaveEncoderData* encData);
/* decode & dequantize RC */
int WebRtcIsacfix_DecodeRcCoef(Bitstr_dec* streamdata, int16_t* RCQ15);

/* quantize & code RC */
int WebRtcIsacfix_EncodeRcCoef(int16_t* RCQ15, Bitstr_enc* streamdata);

/* decode & dequantize squared Gain */
int WebRtcIsacfix_DecodeGain2(Bitstr_dec* streamdata, int32_t* Gain2);

/* quantize & code squared Gain (input is squared gain) */
int WebRtcIsacfix_EncodeGain2(int32_t* gain2, Bitstr_enc* streamdata);

int WebRtcIsacfix_EncodePitchGain(int16_t* PitchGains_Q12,
                                  Bitstr_enc* streamdata,
                                  IsacSaveEncoderData* encData);

int WebRtcIsacfix_EncodePitchLag(int16_t* PitchLagQ7,
                                 int16_t* PitchGain_Q12,
                                 Bitstr_enc* streamdata,
                                 IsacSaveEncoderData* encData);

int WebRtcIsacfix_DecodePitchGain(Bitstr_dec* streamdata,
                                  int16_t* PitchGain_Q12);

int WebRtcIsacfix_DecodePitchLag(Bitstr_dec* streamdata,
                                 int16_t* PitchGain_Q12,
                                 int16_t* PitchLagQ7);

int WebRtcIsacfix_DecodeFrameLen(Bitstr_dec* streamdata, size_t* framelength);

int WebRtcIsacfix_EncodeFrameLen(int16_t framelength, Bitstr_enc* streamdata);

int WebRtcIsacfix_DecodeSendBandwidth(Bitstr_dec* streamdata, int16_t* BWno);

int WebRtcIsacfix_EncodeReceiveBandwidth(int16_t* BWno, Bitstr_enc* streamdata);

void WebRtcIsacfix_TranscodeLpcCoef(int32_t* tmpcoeffs_gQ6, int16_t* index_gQQ);

// Pointer functions for LPC transforms.

typedef void (*MatrixProduct1)(const int16_t matrix0[],
                               const int32_t matrix1[],
                               int32_t matrix_product[],
                               const int matrix1_index_factor1,
                               const int matrix0_index_factor1,
                               const int matrix1_index_init_case,
                               const int matrix1_index_step,
                               const int matrix0_index_step,
                               const int inner_loop_count,
                               const int mid_loop_count,
                               const int shift);
typedef void (*MatrixProduct2)(const int16_t matrix0[],
                               const int32_t matrix1[],
                               int32_t matrix_product[],
                               const int matrix0_index_factor,
                               const int matrix0_index_step);

extern MatrixProduct1 WebRtcIsacfix_MatrixProduct1;
extern MatrixProduct2 WebRtcIsacfix_MatrixProduct2;

void WebRtcIsacfix_MatrixProduct1C(const int16_t matrix0[],
                                   const int32_t matrix1[],
                                   int32_t matrix_product[],
                                   const int matrix1_index_factor1,
                                   const int matrix0_index_factor1,
                                   const int matrix1_index_init_case,
                                   const int matrix1_index_step,
                                   const int matrix0_index_step,
                                   const int inner_loop_count,
                                   const int mid_loop_count,
                                   const int shift);
void WebRtcIsacfix_MatrixProduct2C(const int16_t matrix0[],
                                   const int32_t matrix1[],
                                   int32_t matrix_product[],
                                   const int matrix0_index_factor,
                                   const int matrix0_index_step);

#if defined(WEBRTC_HAS_NEON)
void WebRtcIsacfix_MatrixProduct1Neon(const int16_t matrix0[],
                                      const int32_t matrix1[],
                                      int32_t matrix_product[],
                                      const int matrix1_index_factor1,
                                      const int matrix0_index_factor1,
                                      const int matrix1_index_init_case,
                                      const int matrix1_index_step,
                                      const int matrix0_index_step,
                                      const int inner_loop_count,
                                      const int mid_loop_count,
                                      const int shift);
void WebRtcIsacfix_MatrixProduct2Neon(const int16_t matrix0[],
                                      const int32_t matrix1[],
                                      int32_t matrix_product[],
                                      const int matrix0_index_factor,
                                      const int matrix0_index_step);
#endif

#if defined(MIPS32_LE)
void WebRtcIsacfix_MatrixProduct1MIPS(const int16_t matrix0[],
                                      const int32_t matrix1[],
                                      int32_t matrix_product[],
                                      const int matrix1_index_factor1,
                                      const int matrix0_index_factor1,
                                      const int matrix1_index_init_case,
                                      const int matrix1_index_step,
                                      const int matrix0_index_step,
                                      const int inner_loop_count,
                                      const int mid_loop_count,
                                      const int shift);

void WebRtcIsacfix_MatrixProduct2MIPS(const int16_t matrix0[],
                                      const int32_t matrix1[],
                                      int32_t matrix_product[],
                                      const int matrix0_index_factor,
                                      const int matrix0_index_step);
#endif

#endif  // MODULES_AUDIO_CODING_CODECS_ISAC_FIX_SOURCE_ENTROPY_CODING_H_
