/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AEC3_AEC3_FFT_H_
#define MODULES_AUDIO_PROCESSING_AEC3_AEC3_FFT_H_

#include <array>

#include "api/array_view.h"
#include "modules/audio_processing/aec3/aec3_common.h"
#include "modules/audio_processing/aec3/fft_data.h"
#include "modules/audio_processing/utility/ooura_fft.h"
#include "rtc_base/checks.h"
#include "rtc_base/constructormagic.h"

namespace webrtc {

// Wrapper class that provides 128 point real valued FFT functionality with the
// FftData type.
class Aec3Fft {
 public:
  enum class Window { kRectangular, kHanning, kSqrtHanning };

  Aec3Fft() = default;
  // Computes the FFT. Note that both the input and output are modified.
  void Fft(std::array<float, kFftLength>* x, FftData* X) const {
    RTC_DCHECK(x);
    RTC_DCHECK(X);
    ooura_fft_.Fft(x->data());
    X->CopyFromPackedArray(*x);
  }
  // Computes the inverse Fft.
  void Ifft(const FftData& X, std::array<float, kFftLength>* x) const {
    RTC_DCHECK(x);
    X.CopyToPackedArray(x);
    ooura_fft_.InverseFft(x->data());
  }

  // Windows the input using a Hanning window, and then adds padding of
  // kFftLengthBy2 initial zeros before computing the Fft.
  void ZeroPaddedFft(rtc::ArrayView<const float> x,
                     Window window,
                     FftData* X) const;

  // Concatenates the kFftLengthBy2 values long x and x_old before computing the
  // Fft. After that, x is copied to x_old.
  void PaddedFft(rtc::ArrayView<const float> x,
                 rtc::ArrayView<const float> x_old,
                 FftData* X) const {
    PaddedFft(x, x_old, Window::kRectangular, X);
  }

  // Padded Fft using a time-domain window.
  void PaddedFft(rtc::ArrayView<const float> x,
                 rtc::ArrayView<const float> x_old,
                 Window window,
                 FftData* X) const;

 private:
  const OouraFft ooura_fft_;

  RTC_DISALLOW_COPY_AND_ASSIGN(Aec3Fft);
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AEC3_AEC3_FFT_H_
