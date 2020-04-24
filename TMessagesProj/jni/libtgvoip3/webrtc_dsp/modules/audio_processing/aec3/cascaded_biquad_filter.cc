/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/audio_processing/aec3/cascaded_biquad_filter.h"

#include <algorithm>

#include "rtc_base/checks.h"

namespace webrtc {

CascadedBiQuadFilter::BiQuadParam::BiQuadParam(std::complex<float> zero,
                                               std::complex<float> pole,
                                               float gain,
                                               bool mirror_zero_along_i_axis)
    : zero(zero),
      pole(pole),
      gain(gain),
      mirror_zero_along_i_axis(mirror_zero_along_i_axis) {}

CascadedBiQuadFilter::BiQuadParam::BiQuadParam(const BiQuadParam&) = default;

CascadedBiQuadFilter::BiQuad::BiQuad(
    const CascadedBiQuadFilter::BiQuadParam& param)
    : x(), y() {
  float z_r = std::real(param.zero);
  float z_i = std::imag(param.zero);
  float p_r = std::real(param.pole);
  float p_i = std::imag(param.pole);
  float gain = param.gain;

  if (param.mirror_zero_along_i_axis) {
    // Assuming zeroes at z_r and -z_r.
    RTC_DCHECK(z_i == 0.f);
    coefficients.b[0] = gain * 1.f;
    coefficients.b[1] = 0.f;
    coefficients.b[2] = gain * -(z_r * z_r);
  } else {
    // Assuming zeros at (z_r + z_i*i) and (z_r - z_i*i).
    coefficients.b[0] = gain * 1.f;
    coefficients.b[1] = gain * -2.f * z_r;
    coefficients.b[2] = gain * (z_r * z_r + z_i * z_i);
  }

  // Assuming poles at (p_r + p_i*i) and (p_r - p_i*i).
  coefficients.a[0] = -2.f * p_r;
  coefficients.a[1] = p_r * p_r + p_i * p_i;
}

CascadedBiQuadFilter::CascadedBiQuadFilter(
    const CascadedBiQuadFilter::BiQuadCoefficients& coefficients,
    size_t num_biquads)
    : biquads_(num_biquads, coefficients) {}

CascadedBiQuadFilter::CascadedBiQuadFilter(
    const std::vector<CascadedBiQuadFilter::BiQuadParam>& biquad_params) {
  for (const auto& param : biquad_params) {
    biquads_.push_back(BiQuad(param));
  }
}

CascadedBiQuadFilter::~CascadedBiQuadFilter() = default;

void CascadedBiQuadFilter::Process(rtc::ArrayView<const float> x,
                                   rtc::ArrayView<float> y) {
  if (biquads_.size() > 0) {
    ApplyBiQuad(x, y, &biquads_[0]);
    for (size_t k = 1; k < biquads_.size(); ++k) {
      ApplyBiQuad(y, y, &biquads_[k]);
    }
  } else {
    std::copy(x.begin(), x.end(), y.begin());
  }
}

void CascadedBiQuadFilter::Process(rtc::ArrayView<float> y) {
  for (auto& biquad : biquads_) {
    ApplyBiQuad(y, y, &biquad);
  }
}

void CascadedBiQuadFilter::ApplyBiQuad(rtc::ArrayView<const float> x,
                                       rtc::ArrayView<float> y,
                                       CascadedBiQuadFilter::BiQuad* biquad) {
  RTC_DCHECK_EQ(x.size(), y.size());
  const auto* c_b = biquad->coefficients.b;
  const auto* c_a = biquad->coefficients.a;
  auto* m_x = biquad->x;
  auto* m_y = biquad->y;
  for (size_t k = 0; k < x.size(); ++k) {
    const float tmp = x[k];
    y[k] = c_b[0] * tmp + c_b[1] * m_x[0] + c_b[2] * m_x[1] - c_a[0] * m_y[0] -
           c_a[1] * m_y[1];
    m_x[1] = m_x[0];
    m_x[0] = tmp;
    m_y[1] = m_y[0];
    m_y[0] = y[k];
  }
}

}  // namespace webrtc
