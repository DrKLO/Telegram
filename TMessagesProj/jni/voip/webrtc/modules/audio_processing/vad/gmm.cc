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

namespace webrtc {

static const int kMaxDimension = 10;

static void RemoveMean(const double* in,
                       const double* mean_vec,
                       int dimension,
                       double* out) {
  for (int n = 0; n < dimension; ++n)
    out[n] = in[n] - mean_vec[n];
}

static double ComputeExponent(const double* in,
                              const double* covar_inv,
                              int dimension) {
  double q = 0;
  for (int i = 0; i < dimension; ++i) {
    double v = 0;
    for (int j = 0; j < dimension; j++)
      v += (*covar_inv++) * in[j];
    q += v * in[i];
  }
  q *= -0.5;
  return q;
}

double EvaluateGmm(const double* x, const GmmParameters& gmm_parameters) {
  if (gmm_parameters.dimension > kMaxDimension) {
    return -1;  // This is invalid pdf so the caller can check this.
  }
  double f = 0;
  double v[kMaxDimension];
  const double* mean_vec = gmm_parameters.mean;
  const double* covar_inv = gmm_parameters.covar_inverse;

  for (int n = 0; n < gmm_parameters.num_mixtures; n++) {
    RemoveMean(x, mean_vec, gmm_parameters.dimension, v);
    double q = ComputeExponent(v, covar_inv, gmm_parameters.dimension) +
               gmm_parameters.weight[n];
    f += exp(q);
    mean_vec += gmm_parameters.dimension;
    covar_inv += gmm_parameters.dimension * gmm_parameters.dimension;
  }
  return f;
}

}  // namespace webrtc
