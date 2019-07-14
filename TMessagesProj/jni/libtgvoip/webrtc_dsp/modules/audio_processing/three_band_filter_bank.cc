/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// An implementation of a 3-band FIR filter-bank with DCT modulation, similar to
// the proposed in "Multirate Signal Processing for Communication Systems" by
// Fredric J Harris.
//
// The idea is to take a heterodyne system and change the order of the
// components to get something which is efficient to implement digitally.
//
// It is possible to separate the filter using the noble identity as follows:
//
// H(z) = H0(z^3) + z^-1 * H1(z^3) + z^-2 * H2(z^3)
//
// This is used in the analysis stage to first downsample serial to parallel
// and then filter each branch with one of these polyphase decompositions of the
// lowpass prototype. Because each filter is only a modulation of the prototype,
// it is enough to multiply each coefficient by the respective cosine value to
// shift it to the desired band. But because the cosine period is 12 samples,
// it requires separating the prototype even further using the noble identity.
// After filtering and modulating for each band, the output of all filters is
// accumulated to get the downsampled bands.
//
// A similar logic can be applied to the synthesis stage.

// MSVC++ requires this to be set before any other includes to get M_PI.
#define _USE_MATH_DEFINES

#include "modules/audio_processing/three_band_filter_bank.h"

#include <cmath>

#include "rtc_base/checks.h"

namespace webrtc {
namespace {

const size_t kNumBands = 3;
const size_t kSparsity = 4;

// Factors to take into account when choosing |kNumCoeffs|:
//   1. Higher |kNumCoeffs|, means faster transition, which ensures less
//      aliasing. This is especially important when there is non-linear
//      processing between the splitting and merging.
//   2. The delay that this filter bank introduces is
//      |kNumBands| * |kSparsity| * |kNumCoeffs| / 2, so it increases linearly
//      with |kNumCoeffs|.
//   3. The computation complexity also increases linearly with |kNumCoeffs|.
const size_t kNumCoeffs = 4;

// The Matlab code to generate these |kLowpassCoeffs| is:
//
// N = kNumBands * kSparsity * kNumCoeffs - 1;
// h = fir1(N, 1 / (2 * kNumBands), kaiser(N + 1, 3.5));
// reshape(h, kNumBands * kSparsity, kNumCoeffs);
//
// Because the total bandwidth of the lower and higher band is double the middle
// one (because of the spectrum parity), the low-pass prototype is half the
// bandwidth of 1 / (2 * |kNumBands|) and is then shifted with cosine modulation
// to the right places.
// A Kaiser window is used because of its flexibility and the alpha is set to
// 3.5, since that sets a stop band attenuation of 40dB ensuring a fast
// transition.
const float kLowpassCoeffs[kNumBands * kSparsity][kNumCoeffs] = {
    {-0.00047749f, -0.00496888f, +0.16547118f, +0.00425496f},
    {-0.00173287f, -0.01585778f, +0.14989004f, +0.00994113f},
    {-0.00304815f, -0.02536082f, +0.12154542f, +0.01157993f},
    {-0.00383509f, -0.02982767f, +0.08543175f, +0.00983212f},
    {-0.00346946f, -0.02587886f, +0.04760441f, +0.00607594f},
    {-0.00154717f, -0.01136076f, +0.01387458f, +0.00186353f},
    {+0.00186353f, +0.01387458f, -0.01136076f, -0.00154717f},
    {+0.00607594f, +0.04760441f, -0.02587886f, -0.00346946f},
    {+0.00983212f, +0.08543175f, -0.02982767f, -0.00383509f},
    {+0.01157993f, +0.12154542f, -0.02536082f, -0.00304815f},
    {+0.00994113f, +0.14989004f, -0.01585778f, -0.00173287f},
    {+0.00425496f, +0.16547118f, -0.00496888f, -0.00047749f}};

// Downsamples |in| into |out|, taking one every |kNumbands| starting from
// |offset|. |split_length| is the |out| length. |in| has to be at least
// |kNumBands| * |split_length| long.
void Downsample(const float* in,
                size_t split_length,
                size_t offset,
                float* out) {
  for (size_t i = 0; i < split_length; ++i) {
    out[i] = in[kNumBands * i + offset];
  }
}

// Upsamples |in| into |out|, scaling by |kNumBands| and accumulating it every
// |kNumBands| starting from |offset|. |split_length| is the |in| length. |out|
// has to be at least |kNumBands| * |split_length| long.
void Upsample(const float* in, size_t split_length, size_t offset, float* out) {
  for (size_t i = 0; i < split_length; ++i) {
    out[kNumBands * i + offset] += kNumBands * in[i];
  }
}

}  // namespace

// Because the low-pass filter prototype has half bandwidth it is possible to
// use a DCT to shift it in both directions at the same time, to the center
// frequencies [1 / 12, 3 / 12, 5 / 12].
ThreeBandFilterBank::ThreeBandFilterBank(size_t length)
    : in_buffer_(rtc::CheckedDivExact(length, kNumBands)),
      out_buffer_(in_buffer_.size()) {
  for (size_t i = 0; i < kSparsity; ++i) {
    for (size_t j = 0; j < kNumBands; ++j) {
      analysis_filters_.push_back(
          std::unique_ptr<SparseFIRFilter>(new SparseFIRFilter(
              kLowpassCoeffs[i * kNumBands + j], kNumCoeffs, kSparsity, i)));
      synthesis_filters_.push_back(
          std::unique_ptr<SparseFIRFilter>(new SparseFIRFilter(
              kLowpassCoeffs[i * kNumBands + j], kNumCoeffs, kSparsity, i)));
    }
  }
  dct_modulation_.resize(kNumBands * kSparsity);
  for (size_t i = 0; i < dct_modulation_.size(); ++i) {
    dct_modulation_[i].resize(kNumBands);
    for (size_t j = 0; j < kNumBands; ++j) {
      dct_modulation_[i][j] =
          2.f * cos(2.f * M_PI * i * (2.f * j + 1.f) / dct_modulation_.size());
    }
  }
}

ThreeBandFilterBank::~ThreeBandFilterBank() = default;

// The analysis can be separated in these steps:
//   1. Serial to parallel downsampling by a factor of |kNumBands|.
//   2. Filtering of |kSparsity| different delayed signals with polyphase
//      decomposition of the low-pass prototype filter and upsampled by a factor
//      of |kSparsity|.
//   3. Modulating with cosines and accumulating to get the desired band.
void ThreeBandFilterBank::Analysis(const float* in,
                                   size_t length,
                                   float* const* out) {
  RTC_CHECK_EQ(in_buffer_.size(), rtc::CheckedDivExact(length, kNumBands));
  for (size_t i = 0; i < kNumBands; ++i) {
    memset(out[i], 0, in_buffer_.size() * sizeof(*out[i]));
  }
  for (size_t i = 0; i < kNumBands; ++i) {
    Downsample(in, in_buffer_.size(), kNumBands - i - 1, &in_buffer_[0]);
    for (size_t j = 0; j < kSparsity; ++j) {
      const size_t offset = i + j * kNumBands;
      analysis_filters_[offset]->Filter(&in_buffer_[0], in_buffer_.size(),
                                        &out_buffer_[0]);
      DownModulate(&out_buffer_[0], out_buffer_.size(), offset, out);
    }
  }
}

// The synthesis can be separated in these steps:
//   1. Modulating with cosines.
//   2. Filtering each one with a polyphase decomposition of the low-pass
//      prototype filter upsampled by a factor of |kSparsity| and accumulating
//      |kSparsity| signals with different delays.
//   3. Parallel to serial upsampling by a factor of |kNumBands|.
void ThreeBandFilterBank::Synthesis(const float* const* in,
                                    size_t split_length,
                                    float* out) {
  RTC_CHECK_EQ(in_buffer_.size(), split_length);
  memset(out, 0, kNumBands * in_buffer_.size() * sizeof(*out));
  for (size_t i = 0; i < kNumBands; ++i) {
    for (size_t j = 0; j < kSparsity; ++j) {
      const size_t offset = i + j * kNumBands;
      UpModulate(in, in_buffer_.size(), offset, &in_buffer_[0]);
      synthesis_filters_[offset]->Filter(&in_buffer_[0], in_buffer_.size(),
                                         &out_buffer_[0]);
      Upsample(&out_buffer_[0], out_buffer_.size(), i, out);
    }
  }
}

// Modulates |in| by |dct_modulation_| and accumulates it in each of the
// |kNumBands| bands of |out|. |offset| is the index in the period of the
// cosines used for modulation. |split_length| is the length of |in| and each
// band of |out|.
void ThreeBandFilterBank::DownModulate(const float* in,
                                       size_t split_length,
                                       size_t offset,
                                       float* const* out) {
  for (size_t i = 0; i < kNumBands; ++i) {
    for (size_t j = 0; j < split_length; ++j) {
      out[i][j] += dct_modulation_[offset][i] * in[j];
    }
  }
}

// Modulates each of the |kNumBands| bands of |in| by |dct_modulation_| and
// accumulates them in |out|. |out| is cleared before starting to accumulate.
// |offset| is the index in the period of the cosines used for modulation.
// |split_length| is the length of each band of |in| and |out|.
void ThreeBandFilterBank::UpModulate(const float* const* in,
                                     size_t split_length,
                                     size_t offset,
                                     float* out) {
  memset(out, 0, split_length * sizeof(*out));
  for (size_t i = 0; i < kNumBands; ++i) {
    for (size_t j = 0; j < split_length; ++j) {
      out[j] += dct_modulation_[offset][i] * in[i][j];
    }
  }
}

}  // namespace webrtc
