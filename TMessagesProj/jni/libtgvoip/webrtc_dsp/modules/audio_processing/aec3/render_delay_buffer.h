/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AEC3_RENDER_DELAY_BUFFER_H_
#define MODULES_AUDIO_PROCESSING_AEC3_RENDER_DELAY_BUFFER_H_

#include <stddef.h>
#include <vector>

#include "api/audio/echo_canceller3_config.h"
#include "modules/audio_processing/aec3/downsampled_render_buffer.h"
#include "modules/audio_processing/aec3/render_buffer.h"

namespace webrtc {

// Class for buffering the incoming render blocks such that these may be
// extracted with a specified delay.
class RenderDelayBuffer {
 public:
  enum class BufferingEvent {
    kNone,
    kRenderUnderrun,
    kRenderOverrun,
    kApiCallSkew
  };

  static RenderDelayBuffer* Create(const EchoCanceller3Config& config,
                                   size_t num_bands);
  static RenderDelayBuffer* Create2(const EchoCanceller3Config& config,
                                    size_t num_bands);
  virtual ~RenderDelayBuffer() = default;

  // Resets the buffer alignment.
  virtual void Reset() = 0;

  // Inserts a block into the buffer.
  virtual BufferingEvent Insert(
      const std::vector<std::vector<float>>& block) = 0;

  // Updates the buffers one step based on the specified buffer delay. Returns
  // an enum indicating whether there was a special event that occurred.
  virtual BufferingEvent PrepareCaptureProcessing() = 0;

  // Sets the buffer delay and returns a bool indicating whether the delay
  // changed.
  virtual bool SetDelay(size_t delay) = 0;

  // Gets the buffer delay.
  virtual size_t Delay() const = 0;

  // Gets the buffer delay.
  virtual size_t MaxDelay() const = 0;

  // Returns the render buffer for the echo remover.
  virtual RenderBuffer* GetRenderBuffer() = 0;

  // Returns the downsampled render buffer.
  virtual const DownsampledRenderBuffer& GetDownsampledRenderBuffer() const = 0;

  // Returns whether the current delay is noncausal.
  virtual bool CausalDelay(size_t delay) const = 0;

  // Returns the maximum non calusal offset that can occur in the delay buffer.
  static int DelayEstimatorOffset(const EchoCanceller3Config& config);

  // Provides an optional external estimate of the audio buffer delay.
  virtual void SetAudioBufferDelay(size_t delay_ms) = 0;
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AEC3_RENDER_DELAY_BUFFER_H_
