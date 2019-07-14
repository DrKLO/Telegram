/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/ns/noise_suppression.h"

#include <stdlib.h>
#include <string.h>

#include "common_audio/signal_processing/include/signal_processing_library.h"
#include "modules/audio_processing/ns/defines.h"
#include "modules/audio_processing/ns/ns_core.h"

NsHandle* WebRtcNs_Create() {
  NoiseSuppressionC* self = malloc(sizeof(NoiseSuppressionC));
  self->initFlag = 0;
  return (NsHandle*)self;
}

void WebRtcNs_Free(NsHandle* NS_inst) {
  free(NS_inst);
}

int WebRtcNs_Init(NsHandle* NS_inst, uint32_t fs) {
  return WebRtcNs_InitCore((NoiseSuppressionC*)NS_inst, fs);
}

int WebRtcNs_set_policy(NsHandle* NS_inst, int mode) {
  return WebRtcNs_set_policy_core((NoiseSuppressionC*)NS_inst, mode);
}

void WebRtcNs_Analyze(NsHandle* NS_inst, const float* spframe) {
  WebRtcNs_AnalyzeCore((NoiseSuppressionC*)NS_inst, spframe);
}

void WebRtcNs_Process(NsHandle* NS_inst,
                      const float* const* spframe,
                      size_t num_bands,
                      float* const* outframe) {
  WebRtcNs_ProcessCore((NoiseSuppressionC*)NS_inst, spframe, num_bands,
                       outframe);
}

float WebRtcNs_prior_speech_probability(NsHandle* handle) {
  NoiseSuppressionC* self = (NoiseSuppressionC*)handle;
  if (handle == NULL) {
    return -1;
  }
  if (self->initFlag == 0) {
    return -1;
  }
  return self->priorSpeechProb;
}

const float* WebRtcNs_noise_estimate(const NsHandle* handle) {
  const NoiseSuppressionC* self = (const NoiseSuppressionC*)handle;
  if (handle == NULL || self->initFlag == 0) {
    return NULL;
  }
  return self->noise;
}

size_t WebRtcNs_num_freq() {
  return HALF_ANAL_BLOCKL;
}
