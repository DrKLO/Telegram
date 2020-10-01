/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef COMMON_AUDIO_RESAMPLER_INCLUDE_PUSH_RESAMPLER_H_
#define COMMON_AUDIO_RESAMPLER_INCLUDE_PUSH_RESAMPLER_H_

#include <memory>
#include <vector>

namespace webrtc {

class PushSincResampler;

// Wraps PushSincResampler to provide stereo support.
// TODO(ajm): add support for an arbitrary number of channels.
template <typename T>
class PushResampler {
 public:
  PushResampler();
  virtual ~PushResampler();

  // Must be called whenever the parameters change. Free to be called at any
  // time as it is a no-op if parameters have not changed since the last call.
  int InitializeIfNeeded(int src_sample_rate_hz,
                         int dst_sample_rate_hz,
                         size_t num_channels);

  // Returns the total number of samples provided in destination (e.g. 32 kHz,
  // 2 channel audio gives 640 samples).
  int Resample(const T* src, size_t src_length, T* dst, size_t dst_capacity);

 private:
  int src_sample_rate_hz_;
  int dst_sample_rate_hz_;
  size_t num_channels_;
  // Vector that is needed to provide the proper inputs and outputs to the
  // interleave/de-interleave methods used in Resample. This needs to be
  // heap-allocated on the state to support an arbitrary number of channels
  // without doing run-time heap-allocations in the Resample method.
  std::vector<T*> channel_data_array_;

  struct ChannelResampler {
    std::unique_ptr<PushSincResampler> resampler;
    std::vector<T> source;
    std::vector<T> destination;
  };

  std::vector<ChannelResampler> channel_resamplers_;
};
}  // namespace webrtc

#endif  // COMMON_AUDIO_RESAMPLER_INCLUDE_PUSH_RESAMPLER_H_
