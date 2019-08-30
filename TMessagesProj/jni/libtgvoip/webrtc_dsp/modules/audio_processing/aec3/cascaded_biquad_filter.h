/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AEC3_CASCADED_BIQUAD_FILTER_H_
#define MODULES_AUDIO_PROCESSING_AEC3_CASCADED_BIQUAD_FILTER_H_

#include <stddef.h>
#include <complex>
#include <vector>

#include "api/array_view.h"
#include "rtc_base/constructormagic.h"

namespace webrtc {

// Applies a number of biquads in a cascaded manner. The filter implementation
// is direct form 1.
class CascadedBiQuadFilter {
 public:
  struct BiQuadParam {
    BiQuadParam(std::complex<float> zero,
                std::complex<float> pole,
                float gain,
                bool mirror_zero_along_i_axis = false);
    BiQuadParam(const BiQuadParam&);
    std::complex<float> zero;
    std::complex<float> pole;
    float gain;
    bool mirror_zero_along_i_axis;
  };

  struct BiQuadCoefficients {
    float b[3];
    float a[2];
  };

  struct BiQuad {
    BiQuad(const BiQuadCoefficients& coefficients)
        : coefficients(coefficients), x(), y() {}
    BiQuad(const CascadedBiQuadFilter::BiQuadParam& param);
    BiQuadCoefficients coefficients;
    float x[2];
    float y[2];
  };

  CascadedBiQuadFilter(
      const CascadedBiQuadFilter::BiQuadCoefficients& coefficients,
      size_t num_biquads);
  CascadedBiQuadFilter(
      const std::vector<CascadedBiQuadFilter::BiQuadParam>& biquad_params);
  ~CascadedBiQuadFilter();
  // Applies the biquads on the values in x in order to form the output in y.
  void Process(rtc::ArrayView<const float> x, rtc::ArrayView<float> y);
  // Applies the biquads on the values in y in an in-place manner.
  void Process(rtc::ArrayView<float> y);

 private:
  void ApplyBiQuad(rtc::ArrayView<const float> x,
                   rtc::ArrayView<float> y,
                   CascadedBiQuadFilter::BiQuad* biquad);

  std::vector<BiQuad> biquads_;

  RTC_DISALLOW_IMPLICIT_CONSTRUCTORS(CascadedBiQuadFilter);
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AEC3_CASCADED_BIQUAD_FILTER_H_
