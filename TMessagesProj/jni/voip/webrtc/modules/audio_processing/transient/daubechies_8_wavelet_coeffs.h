/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// This header file defines the coefficients of the FIR based approximation of
// the Meyer Wavelet
#ifndef MODULES_AUDIO_PROCESSING_TRANSIENT_DAUBECHIES_8_WAVELET_COEFFS_H_
#define MODULES_AUDIO_PROCESSING_TRANSIENT_DAUBECHIES_8_WAVELET_COEFFS_H_

// Decomposition coefficients Daubechies 8.

namespace webrtc {

const int kDaubechies8CoefficientsLength = 16;

const float kDaubechies8HighPassCoefficients[kDaubechies8CoefficientsLength] = {
    -5.44158422430816093862e-02f, 3.12871590914465924627e-01f,
    -6.75630736298012846142e-01f, 5.85354683654869090148e-01f,
    1.58291052560238926228e-02f,  -2.84015542962428091389e-01f,
    -4.72484573997972536787e-04f, 1.28747426620186011803e-01f,
    1.73693010020221083600e-02f,  -4.40882539310647192377e-02f,
    -1.39810279170155156436e-02f, 8.74609404701565465445e-03f,
    4.87035299301066034600e-03f,  -3.91740372995977108837e-04f,
    -6.75449405998556772109e-04f, -1.17476784002281916305e-04f};

const float kDaubechies8LowPassCoefficients[kDaubechies8CoefficientsLength] = {
    -1.17476784002281916305e-04f, 6.75449405998556772109e-04f,
    -3.91740372995977108837e-04f, -4.87035299301066034600e-03f,
    8.74609404701565465445e-03f,  1.39810279170155156436e-02f,
    -4.40882539310647192377e-02f, -1.73693010020221083600e-02f,
    1.28747426620186011803e-01f,  4.72484573997972536787e-04f,
    -2.84015542962428091389e-01f, -1.58291052560238926228e-02f,
    5.85354683654869090148e-01f,  6.75630736298012846142e-01f,
    3.12871590914465924627e-01f,  5.44158422430816093862e-02f};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_TRANSIENT_DAUBECHIES_8_WAVELET_COEFFS_H_
