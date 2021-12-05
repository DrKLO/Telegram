/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/utility/pffft_wrapper.h"

#include "rtc_base/checks.h"
#include "third_party/pffft/src/pffft.h"

namespace webrtc {
namespace {

size_t GetBufferSize(size_t fft_size, Pffft::FftType fft_type) {
  return fft_size * (fft_type == Pffft::FftType::kReal ? 1 : 2);
}

float* AllocatePffftBuffer(size_t size) {
  return static_cast<float*>(pffft_aligned_malloc(size * sizeof(float)));
}

}  // namespace

Pffft::FloatBuffer::FloatBuffer(size_t fft_size, FftType fft_type)
    : size_(GetBufferSize(fft_size, fft_type)),
      data_(AllocatePffftBuffer(size_)) {}

Pffft::FloatBuffer::~FloatBuffer() {
  pffft_aligned_free(data_);
}

rtc::ArrayView<const float> Pffft::FloatBuffer::GetConstView() const {
  return {data_, size_};
}

rtc::ArrayView<float> Pffft::FloatBuffer::GetView() {
  return {data_, size_};
}

Pffft::Pffft(size_t fft_size, FftType fft_type)
    : fft_size_(fft_size),
      fft_type_(fft_type),
      pffft_status_(pffft_new_setup(
          fft_size_,
          fft_type == Pffft::FftType::kReal ? PFFFT_REAL : PFFFT_COMPLEX)),
      scratch_buffer_(
          AllocatePffftBuffer(GetBufferSize(fft_size_, fft_type_))) {
  RTC_DCHECK(pffft_status_);
  RTC_DCHECK(scratch_buffer_);
}

Pffft::~Pffft() {
  pffft_destroy_setup(pffft_status_);
  pffft_aligned_free(scratch_buffer_);
}

bool Pffft::IsValidFftSize(size_t fft_size, FftType fft_type) {
  if (fft_size == 0) {
    return false;
  }
  // PFFFT only supports transforms for inputs of length N of the form
  // N = (2^a)*(3^b)*(5^c) where b >=0 and c >= 0 and a >= 5 for the real FFT
  // and a >= 4 for the complex FFT.
  constexpr int kFactors[] = {2, 3, 5};
  int factorization[] = {0, 0, 0};
  int n = static_cast<int>(fft_size);
  for (int i = 0; i < 3; ++i) {
    while (n % kFactors[i] == 0) {
      n = n / kFactors[i];
      factorization[i]++;
    }
  }
  int a_min = (fft_type == Pffft::FftType::kReal) ? 5 : 4;
  return factorization[0] >= a_min && n == 1;
}

bool Pffft::IsSimdEnabled() {
  return pffft_simd_size() > 1;
}

std::unique_ptr<Pffft::FloatBuffer> Pffft::CreateBuffer() const {
  // Cannot use make_unique from absl because Pffft is the only friend of
  // Pffft::FloatBuffer.
  std::unique_ptr<Pffft::FloatBuffer> buffer(
      new Pffft::FloatBuffer(fft_size_, fft_type_));
  return buffer;
}

void Pffft::ForwardTransform(const FloatBuffer& in,
                             FloatBuffer* out,
                             bool ordered) {
  RTC_DCHECK_EQ(in.size(), GetBufferSize(fft_size_, fft_type_));
  RTC_DCHECK_EQ(in.size(), out->size());
  RTC_DCHECK(scratch_buffer_);
  if (ordered) {
    pffft_transform_ordered(pffft_status_, in.const_data(), out->data(),
                            scratch_buffer_, PFFFT_FORWARD);
  } else {
    pffft_transform(pffft_status_, in.const_data(), out->data(),
                    scratch_buffer_, PFFFT_FORWARD);
  }
}

void Pffft::BackwardTransform(const FloatBuffer& in,
                              FloatBuffer* out,
                              bool ordered) {
  RTC_DCHECK_EQ(in.size(), GetBufferSize(fft_size_, fft_type_));
  RTC_DCHECK_EQ(in.size(), out->size());
  RTC_DCHECK(scratch_buffer_);
  if (ordered) {
    pffft_transform_ordered(pffft_status_, in.const_data(), out->data(),
                            scratch_buffer_, PFFFT_BACKWARD);
  } else {
    pffft_transform(pffft_status_, in.const_data(), out->data(),
                    scratch_buffer_, PFFFT_BACKWARD);
  }
}

void Pffft::FrequencyDomainConvolve(const FloatBuffer& fft_x,
                                    const FloatBuffer& fft_y,
                                    FloatBuffer* out,
                                    float scaling) {
  RTC_DCHECK_EQ(fft_x.size(), GetBufferSize(fft_size_, fft_type_));
  RTC_DCHECK_EQ(fft_x.size(), fft_y.size());
  RTC_DCHECK_EQ(fft_x.size(), out->size());
  pffft_zconvolve_accumulate(pffft_status_, fft_x.const_data(),
                             fft_y.const_data(), out->data(), scaling);
}

}  // namespace webrtc
