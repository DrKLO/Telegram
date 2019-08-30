/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AEC_AEC_CORE_OPTIMIZED_METHODS_H_
#define MODULES_AUDIO_PROCESSING_AEC_AEC_CORE_OPTIMIZED_METHODS_H_

#include <memory>

#include "modules/audio_processing/aec/aec_core.h"

namespace webrtc {

typedef void (*WebRtcAecFilterFar)(
    int num_partitions,
    int x_fft_buf_block_pos,
    float x_fft_buf[2][kExtendedNumPartitions * PART_LEN1],
    float h_fft_buf[2][kExtendedNumPartitions * PART_LEN1],
    float y_fft[2][PART_LEN1]);
extern WebRtcAecFilterFar WebRtcAec_FilterFar;
typedef void (*WebRtcAecScaleErrorSignal)(float mu,
                                          float error_threshold,
                                          float x_pow[PART_LEN1],
                                          float ef[2][PART_LEN1]);
extern WebRtcAecScaleErrorSignal WebRtcAec_ScaleErrorSignal;
typedef void (*WebRtcAecFilterAdaptation)(
    const OouraFft& ooura_fft,
    int num_partitions,
    int x_fft_buf_block_pos,
    float x_fft_buf[2][kExtendedNumPartitions * PART_LEN1],
    float e_fft[2][PART_LEN1],
    float h_fft_buf[2][kExtendedNumPartitions * PART_LEN1]);
extern WebRtcAecFilterAdaptation WebRtcAec_FilterAdaptation;

typedef void (*WebRtcAecOverdrive)(float overdrive_scaling,
                                   const float hNlFb,
                                   float hNl[PART_LEN1]);
extern WebRtcAecOverdrive WebRtcAec_Overdrive;

typedef void (*WebRtcAecSuppress)(const float hNl[PART_LEN1],
                                  float efw[2][PART_LEN1]);
extern WebRtcAecSuppress WebRtcAec_Suppress;

typedef void (*WebRtcAecComputeCoherence)(const CoherenceState* coherence_state,
                                          float* cohde,
                                          float* cohxd);
extern WebRtcAecComputeCoherence WebRtcAec_ComputeCoherence;

typedef void (*WebRtcAecUpdateCoherenceSpectra)(int mult,
                                                bool extended_filter_enabled,
                                                float efw[2][PART_LEN1],
                                                float dfw[2][PART_LEN1],
                                                float xfw[2][PART_LEN1],
                                                CoherenceState* coherence_state,
                                                short* filter_divergence_state,
                                                int* extreme_filter_divergence);
extern WebRtcAecUpdateCoherenceSpectra WebRtcAec_UpdateCoherenceSpectra;

typedef int (*WebRtcAecPartitionDelay)(
    int num_partitions,
    float h_fft_buf[2][kExtendedNumPartitions * PART_LEN1]);
extern WebRtcAecPartitionDelay WebRtcAec_PartitionDelay;

typedef void (*WebRtcAecStoreAsComplex)(const float* data,
                                        float data_complex[2][PART_LEN1]);
extern WebRtcAecStoreAsComplex WebRtcAec_StoreAsComplex;

typedef void (*WebRtcAecWindowData)(float* x_windowed, const float* x);
extern WebRtcAecWindowData WebRtcAec_WindowData;

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AEC_AEC_CORE_OPTIMIZED_METHODS_H_
