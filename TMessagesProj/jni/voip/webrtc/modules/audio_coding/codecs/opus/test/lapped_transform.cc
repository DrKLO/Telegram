/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/codecs/opus/test/lapped_transform.h"

#include <algorithm>
#include <cstdlib>
#include <cstring>

#include "common_audio/real_fourier.h"
#include "rtc_base/checks.h"

namespace webrtc {

void LappedTransform::BlockThunk::ProcessBlock(const float* const* input,
                                               size_t num_frames,
                                               size_t num_input_channels,
                                               size_t num_output_channels,
                                               float* const* output) {
  RTC_CHECK_EQ(num_input_channels, parent_->num_in_channels_);
  RTC_CHECK_EQ(num_output_channels, parent_->num_out_channels_);
  RTC_CHECK_EQ(parent_->block_length_, num_frames);

  for (size_t i = 0; i < num_input_channels; ++i) {
    memcpy(parent_->real_buf_.Row(i), input[i], num_frames * sizeof(*input[0]));
    parent_->fft_->Forward(parent_->real_buf_.Row(i),
                           parent_->cplx_pre_.Row(i));
  }

  size_t block_length =
      RealFourier::ComplexLength(RealFourier::FftOrder(num_frames));
  RTC_CHECK_EQ(parent_->cplx_length_, block_length);
  parent_->block_processor_->ProcessAudioBlock(
      parent_->cplx_pre_.Array(), num_input_channels, parent_->cplx_length_,
      num_output_channels, parent_->cplx_post_.Array());

  for (size_t i = 0; i < num_output_channels; ++i) {
    parent_->fft_->Inverse(parent_->cplx_post_.Row(i),
                           parent_->real_buf_.Row(i));
    memcpy(output[i], parent_->real_buf_.Row(i),
           num_frames * sizeof(*input[0]));
  }
}

LappedTransform::LappedTransform(size_t num_in_channels,
                                 size_t num_out_channels,
                                 size_t chunk_length,
                                 const float* window,
                                 size_t block_length,
                                 size_t shift_amount,
                                 Callback* callback)
    : blocker_callback_(this),
      num_in_channels_(num_in_channels),
      num_out_channels_(num_out_channels),
      block_length_(block_length),
      chunk_length_(chunk_length),
      block_processor_(callback),
      blocker_(chunk_length_,
               block_length_,
               num_in_channels_,
               num_out_channels_,
               window,
               shift_amount,
               &blocker_callback_),
      fft_(RealFourier::Create(RealFourier::FftOrder(block_length_))),
      cplx_length_(RealFourier::ComplexLength(fft_->order())),
      real_buf_(num_in_channels,
                block_length_,
                RealFourier::kFftBufferAlignment),
      cplx_pre_(num_in_channels,
                cplx_length_,
                RealFourier::kFftBufferAlignment),
      cplx_post_(num_out_channels,
                 cplx_length_,
                 RealFourier::kFftBufferAlignment) {
  RTC_CHECK(num_in_channels_ > 0);
  RTC_CHECK_GT(block_length_, 0);
  RTC_CHECK_GT(chunk_length_, 0);
  RTC_CHECK(block_processor_);

  // block_length_ power of 2?
  RTC_CHECK_EQ(0, block_length_ & (block_length_ - 1));
}

LappedTransform::~LappedTransform() = default;

void LappedTransform::ProcessChunk(const float* const* in_chunk,
                                   float* const* out_chunk) {
  blocker_.ProcessChunk(in_chunk, chunk_length_, num_in_channels_,
                        num_out_channels_, out_chunk);
}

}  // namespace webrtc
