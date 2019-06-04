/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/vad/pitch_internal.h"

#include <cmath>

// A 4-to-3 linear interpolation.
// The interpolation constants are derived as following:
// Input pitch parameters are updated every 7.5 ms. Within a 30-ms interval
// we are interested in pitch parameters of 0-5 ms, 10-15ms and 20-25ms. This is
// like interpolating 4-to-6 and keep the odd samples.
// The reason behind this is that LPC coefficients are computed for the first
// half of each 10ms interval.
static void PitchInterpolation(double old_val, const double* in, double* out) {
  out[0] = 1. / 6. * old_val + 5. / 6. * in[0];
  out[1] = 5. / 6. * in[1] + 1. / 6. * in[2];
  out[2] = 0.5 * in[2] + 0.5 * in[3];
}

void GetSubframesPitchParameters(int sampling_rate_hz,
                                 double* gains,
                                 double* lags,
                                 int num_in_frames,
                                 int num_out_frames,
                                 double* log_old_gain,
                                 double* old_lag,
                                 double* log_pitch_gain,
                                 double* pitch_lag_hz) {
  // Gain interpolation is in log-domain, also returned in log-domain.
  for (int n = 0; n < num_in_frames; n++)
    gains[n] = log(gains[n] + 1e-12);

  // Interpolate lags and gains.
  PitchInterpolation(*log_old_gain, gains, log_pitch_gain);
  *log_old_gain = gains[num_in_frames - 1];
  PitchInterpolation(*old_lag, lags, pitch_lag_hz);
  *old_lag = lags[num_in_frames - 1];

  // Convert pitch-lags to Hertz.
  for (int n = 0; n < num_out_frames; n++) {
    pitch_lag_hz[n] = (sampling_rate_hz) / (pitch_lag_hz[n]);
  }
}
