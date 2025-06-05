/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/vad/gmm.h"

#include <math.h>

#include "modules/audio_processing/vad/noise_gmm_tables.h"
#include "modules/audio_processing/vad/voice_gmm_tables.h"
#include "test/gtest.h"

namespace webrtc {

TEST(GmmTest, EvaluateGmm) {
  GmmParameters noise_gmm;
  GmmParameters voice_gmm;

  // Setup noise GMM.
  noise_gmm.dimension = kNoiseGmmDim;
  noise_gmm.num_mixtures = kNoiseGmmNumMixtures;
  noise_gmm.weight = kNoiseGmmWeights;
  noise_gmm.mean = &kNoiseGmmMean[0][0];
  noise_gmm.covar_inverse = &kNoiseGmmCovarInverse[0][0][0];

  // Setup voice GMM.
  voice_gmm.dimension = kVoiceGmmDim;
  voice_gmm.num_mixtures = kVoiceGmmNumMixtures;
  voice_gmm.weight = kVoiceGmmWeights;
  voice_gmm.mean = &kVoiceGmmMean[0][0];
  voice_gmm.covar_inverse = &kVoiceGmmCovarInverse[0][0][0];

  // Test vectors. These are the mean of the GMM means.
  const double kXVoice[kVoiceGmmDim] = {-1.35893162459863, 602.862491970368,
                                        178.022069191324};
  const double kXNoise[kNoiseGmmDim] = {-2.33443722724409, 2827.97828765184,
                                        141.114178166812};

  // Expected pdf values. These values are computed in MATLAB using EvalGmm.m
  const double kPdfNoise = 1.88904409403101e-07;
  const double kPdfVoice = 1.30453996982266e-06;

  // Relative error should be smaller that the following value.
  const double kAcceptedRelativeErr = 1e-10;

  // Test Voice.
  double pdf = EvaluateGmm(kXVoice, voice_gmm);
  EXPECT_GT(pdf, 0);
  double relative_error = fabs(pdf - kPdfVoice) / kPdfVoice;
  EXPECT_LE(relative_error, kAcceptedRelativeErr);

  // Test Noise.
  pdf = EvaluateGmm(kXNoise, noise_gmm);
  EXPECT_GT(pdf, 0);
  relative_error = fabs(pdf - kPdfNoise) / kPdfNoise;
  EXPECT_LE(relative_error, kAcceptedRelativeErr);
}

}  // namespace webrtc
