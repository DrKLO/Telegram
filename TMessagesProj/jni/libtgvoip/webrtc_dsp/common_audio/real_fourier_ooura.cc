/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "common_audio/real_fourier_ooura.h"

#include <algorithm>
#include <cmath>

#include "common_audio/third_party/fft4g/fft4g.h"
#include "rtc_base/checks.h"

namespace webrtc {

using std::complex;

namespace {

void Conjugate(complex<float>* array, size_t complex_length) {
  std::for_each(array, array + complex_length,
                [=](complex<float>& v) { v = std::conj(v); });
}

size_t ComputeWorkIpSize(size_t fft_length) {
  return static_cast<size_t>(
      2 + std::ceil(std::sqrt(static_cast<float>(fft_length))));
}

}  // namespace

RealFourierOoura::RealFourierOoura(int fft_order)
    : order_(fft_order),
      length_(FftLength(order_)),
      complex_length_(ComplexLength(order_)),
      // Zero-initializing work_ip_ will cause rdft to initialize these work
      // arrays on the first call.
      work_ip_(new size_t[ComputeWorkIpSize(length_)]()),
      work_w_(new float[complex_length_]()) {
  RTC_CHECK_GE(fft_order, 1);
}

RealFourierOoura::~RealFourierOoura() = default;

void RealFourierOoura::Forward(const float* src, complex<float>* dest) const {
  {
    // This cast is well-defined since C++11. See "Non-static data members" at:
    // http://en.cppreference.com/w/cpp/numeric/complex
    auto* dest_float = reinterpret_cast<float*>(dest);
    std::copy(src, src + length_, dest_float);
    WebRtc_rdft(length_, 1, dest_float, work_ip_.get(), work_w_.get());
  }

  // Ooura places real[n/2] in imag[0].
  dest[complex_length_ - 1] = complex<float>(dest[0].imag(), 0.0f);
  dest[0] = complex<float>(dest[0].real(), 0.0f);
  // Ooura returns the conjugate of the usual Fourier definition.
  Conjugate(dest, complex_length_);
}

void RealFourierOoura::Inverse(const complex<float>* src, float* dest) const {
  {
    auto* dest_complex = reinterpret_cast<complex<float>*>(dest);
    // The real output array is shorter than the input complex array by one
    // complex element.
    const size_t dest_complex_length = complex_length_ - 1;
    std::copy(src, src + dest_complex_length, dest_complex);
    // Restore Ooura's conjugate definition.
    Conjugate(dest_complex, dest_complex_length);
    // Restore real[n/2] to imag[0].
    dest_complex[0] =
        complex<float>(dest_complex[0].real(), src[complex_length_ - 1].real());
  }

  WebRtc_rdft(length_, -1, dest, work_ip_.get(), work_w_.get());

  // Ooura returns a scaled version.
  const float scale = 2.0f / length_;
  std::for_each(dest, dest + length_, [scale](float& v) { v *= scale; });
}

int RealFourierOoura::order() const {
  return order_;
}

}  // namespace webrtc
