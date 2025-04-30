/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/codecs/opus/test/audio_ring_buffer.h"

#include "common_audio/ring_buffer.h"
#include "rtc_base/checks.h"

// This is a simple multi-channel wrapper over the ring_buffer.h C interface.

namespace webrtc {

AudioRingBuffer::AudioRingBuffer(size_t channels, size_t max_frames) {
  buffers_.reserve(channels);
  for (size_t i = 0; i < channels; ++i)
    buffers_.push_back(WebRtc_CreateBuffer(max_frames, sizeof(float)));
}

AudioRingBuffer::~AudioRingBuffer() {
  for (auto* buf : buffers_)
    WebRtc_FreeBuffer(buf);
}

void AudioRingBuffer::Write(const float* const* data,
                            size_t channels,
                            size_t frames) {
  RTC_DCHECK_EQ(buffers_.size(), channels);
  for (size_t i = 0; i < channels; ++i) {
    const size_t written = WebRtc_WriteBuffer(buffers_[i], data[i], frames);
    RTC_CHECK_EQ(written, frames);
  }
}

void AudioRingBuffer::Read(float* const* data, size_t channels, size_t frames) {
  RTC_DCHECK_EQ(buffers_.size(), channels);
  for (size_t i = 0; i < channels; ++i) {
    const size_t read =
        WebRtc_ReadBuffer(buffers_[i], nullptr, data[i], frames);
    RTC_CHECK_EQ(read, frames);
  }
}

size_t AudioRingBuffer::ReadFramesAvailable() const {
  // All buffers have the same amount available.
  return WebRtc_available_read(buffers_[0]);
}

size_t AudioRingBuffer::WriteFramesAvailable() const {
  // All buffers have the same amount available.
  return WebRtc_available_write(buffers_[0]);
}

void AudioRingBuffer::MoveReadPositionForward(size_t frames) {
  for (auto* buf : buffers_) {
    const size_t moved =
        static_cast<size_t>(WebRtc_MoveReadPtr(buf, static_cast<int>(frames)));
    RTC_CHECK_EQ(moved, frames);
  }
}

void AudioRingBuffer::MoveReadPositionBackward(size_t frames) {
  for (auto* buf : buffers_) {
    const size_t moved = static_cast<size_t>(
        -WebRtc_MoveReadPtr(buf, -static_cast<int>(frames)));
    RTC_CHECK_EQ(moved, frames);
  }
}

}  // namespace webrtc
