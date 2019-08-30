/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_NS_MAIN_SOURCE_DEFINES_H_
#define MODULES_AUDIO_PROCESSING_NS_MAIN_SOURCE_DEFINES_H_

#define BLOCKL_MAX 160        // max processing block length: 160
#define ANAL_BLOCKL_MAX 256   // max analysis block length: 256
#define HALF_ANAL_BLOCKL 129  // half max analysis block length + 1
#define NUM_HIGH_BANDS_MAX 2  // max number of high bands: 2

#define QUANTILE (float)0.25

#define SIMULT 3
#define END_STARTUP_LONG 200
#define END_STARTUP_SHORT 50
#define FACTOR (float)40.0
#define WIDTH (float)0.01

// Length of fft work arrays.
#define IP_LENGTH \
  (ANAL_BLOCKL_MAX >> 1)  // must be at least ceil(2 + sqrt(ANAL_BLOCKL_MAX/2))
#define W_LENGTH (ANAL_BLOCKL_MAX >> 1)

// PARAMETERS FOR NEW METHOD
#define DD_PR_SNR (float)0.98  // DD update of prior SNR
#define LRT_TAVG (float)0.50   // tavg parameter for LRT (previously 0.90)
#define SPECT_FL_TAVG \
  (float)0.30  // tavg parameter for spectral flatness measure
#define SPECT_DIFF_TAVG \
  (float)0.30  // tavg parameter for spectral difference measure
#define PRIOR_UPDATE (float)0.10   // update parameter of prior model
#define NOISE_UPDATE (float)0.90   // update parameter for noise
#define SPEECH_UPDATE (float)0.99  // update parameter when likely speech
#define WIDTH_PR_MAP \
  (float)4.0  // width parameter in sigmoid map for prior model
#define LRT_FEATURE_THR (float)0.5  // default threshold for LRT feature
#define SF_FEATURE_THR \
  (float)0.5  // default threshold for Spectral Flatness feature
#define SD_FEATURE_THR \
  (float)0.5  // default threshold for Spectral Difference feature
#define PROB_RANGE (float)0.20   // probability threshold for noise state in
                                 // speech/noise likelihood
#define HIST_PAR_EST 1000        // histogram size for estimation of parameters
#define GAMMA_PAUSE (float)0.05  // update for conservative noise estimate
//
#define B_LIM (float)0.5  // threshold in final energy gain factor calculation
#endif                    // MODULES_AUDIO_PROCESSING_NS_MAIN_SOURCE_DEFINES_H_
