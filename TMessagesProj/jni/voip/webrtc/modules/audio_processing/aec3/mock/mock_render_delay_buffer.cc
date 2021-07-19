/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/mock/mock_render_delay_buffer.h"

namespace webrtc {
namespace test {

MockRenderDelayBuffer::MockRenderDelayBuffer(int sample_rate_hz,
                                             size_t num_channels)
    : block_buffer_(GetRenderDelayBufferSize(4, 4, 12),
                    NumBandsForRate(sample_rate_hz),
                    num_channels,
                    kBlockSize),
      spectrum_buffer_(block_buffer_.buffer.size(), num_channels),
      fft_buffer_(block_buffer_.buffer.size(), num_channels),
      render_buffer_(&block_buffer_, &spectrum_buffer_, &fft_buffer_),
      downsampled_render_buffer_(GetDownSampledBufferSize(4, 4)) {
  ON_CALL(*this, GetRenderBuffer())
      .WillByDefault(
          ::testing::Invoke(this, &MockRenderDelayBuffer::FakeGetRenderBuffer));
  ON_CALL(*this, GetDownsampledRenderBuffer())
      .WillByDefault(::testing::Invoke(
          this, &MockRenderDelayBuffer::FakeGetDownsampledRenderBuffer));
}

MockRenderDelayBuffer::~MockRenderDelayBuffer() = default;

}  // namespace test
}  // namespace webrtc
