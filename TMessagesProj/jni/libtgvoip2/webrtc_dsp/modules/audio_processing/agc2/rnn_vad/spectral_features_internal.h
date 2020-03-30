/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AGC2_RNN_VAD_SPECTRAL_FEATURES_INTERNAL_H_
#define MODULES_AUDIO_PROCESSING_AGC2_RNN_VAD_SPECTRAL_FEATURES_INTERNAL_H_

#include <stddef.h>
#include <array>
#include <complex>

#include "api/array_view.h"
#include "modules/audio_processing/agc2/rnn_vad/common.h"
#include "rtc_base/function_view.h"

namespace webrtc {
namespace rnn_vad {

// Computes FFT boundary indexes corresponding to sub-bands.
std::array<size_t, kNumBands> ComputeBandBoundaryIndexes(
    size_t sample_rate_hz,
    size_t frame_size_samples);

// Iterates through frequency bands and computes coefficients via |functor| for
// triangular bands with peak response at each band boundary. |functor| returns
// a floating point value for the FFT coefficient having index equal to the
// argument passed to |functor|; that argument is in the range {0, ...
// |max_freq_bin_index| - 1}.
void ComputeBandCoefficients(
    rtc::FunctionView<float(size_t)> functor,
    rtc::ArrayView<const size_t, kNumBands> band_boundaries,
    const size_t max_freq_bin_index,
    rtc::ArrayView<float, kNumBands> coefficients);

// Given an array of FFT coefficients and a vector of band boundary indexes,
// computes band energy coefficients.
void ComputeBandEnergies(
    rtc::ArrayView<const std::complex<float>> fft_coeffs,
    rtc::ArrayView<const size_t, kNumBands> band_boundaries,
    rtc::ArrayView<float, kNumBands> band_energies);

// Computes log band energy coefficients.
void ComputeLogBandEnergiesCoefficients(
    rtc::ArrayView<const float, kNumBands> band_energy_coeffs,
    rtc::ArrayView<float, kNumBands> log_band_energy_coeffs);

// Creates a DCT table for arrays having size equal to |kNumBands|.
std::array<float, kNumBands * kNumBands> ComputeDctTable();

// Computes DCT for |in| given a pre-computed DCT table. In-place computation is
// not allowed and |out| can be smaller than |in| in order to only compute the
// first DCT coefficients.
void ComputeDct(rtc::ArrayView<const float, kNumBands> in,
                rtc::ArrayView<const float, kNumBands * kNumBands> dct_table,
                rtc::ArrayView<float> out);

}  // namespace rnn_vad
}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AGC2_RNN_VAD_SPECTRAL_FEATURES_INTERNAL_H_
