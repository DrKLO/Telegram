/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AGC2_RNN_VAD_RNN_H_
#define MODULES_AUDIO_PROCESSING_AGC2_RNN_VAD_RNN_H_

#include <stddef.h>
#include <sys/types.h>
#include <array>

#include "api/array_view.h"
#include "modules/audio_processing/agc2/rnn_vad/common.h"

namespace webrtc {
namespace rnn_vad {

// Maximum number of units for a fully-connected layer. This value is used to
// over-allocate space for fully-connected layers output vectors (implemented as
// std::array). The value should equal the number of units of the largest
// fully-connected layer.
constexpr size_t kFullyConnectedLayersMaxUnits = 24;

// Maximum number of units for a recurrent layer. This value is used to
// over-allocate space for recurrent layers state vectors (implemented as
// std::array). The value should equal the number of units of the largest
// recurrent layer.
constexpr size_t kRecurrentLayersMaxUnits = 24;

// Fully-connected layer.
class FullyConnectedLayer {
 public:
  FullyConnectedLayer(const size_t input_size,
                      const size_t output_size,
                      const rtc::ArrayView<const int8_t> bias,
                      const rtc::ArrayView<const int8_t> weights,
                      float (*const activation_function)(float));
  FullyConnectedLayer(const FullyConnectedLayer&) = delete;
  FullyConnectedLayer& operator=(const FullyConnectedLayer&) = delete;
  ~FullyConnectedLayer();
  size_t input_size() const { return input_size_; }
  size_t output_size() const { return output_size_; }
  rtc::ArrayView<const float> GetOutput() const;
  // Computes the fully-connected layer output.
  void ComputeOutput(rtc::ArrayView<const float> input);

 private:
  const size_t input_size_;
  const size_t output_size_;
  const rtc::ArrayView<const int8_t> bias_;
  const rtc::ArrayView<const int8_t> weights_;
  float (*const activation_function_)(float);
  // The output vector of a recurrent layer has length equal to |output_size_|.
  // However, for efficiency, over-allocation is used.
  std::array<float, kFullyConnectedLayersMaxUnits> output_;
};

// Recurrent layer with gated recurrent units (GRUs).
class GatedRecurrentLayer {
 public:
  GatedRecurrentLayer(const size_t input_size,
                      const size_t output_size,
                      const rtc::ArrayView<const int8_t> bias,
                      const rtc::ArrayView<const int8_t> weights,
                      const rtc::ArrayView<const int8_t> recurrent_weights,
                      float (*const activation_function)(float));
  GatedRecurrentLayer(const GatedRecurrentLayer&) = delete;
  GatedRecurrentLayer& operator=(const GatedRecurrentLayer&) = delete;
  ~GatedRecurrentLayer();
  size_t input_size() const { return input_size_; }
  size_t output_size() const { return output_size_; }
  rtc::ArrayView<const float> GetOutput() const;
  void Reset();
  // Computes the recurrent layer output and updates the status.
  void ComputeOutput(rtc::ArrayView<const float> input);

 private:
  const size_t input_size_;
  const size_t output_size_;
  const rtc::ArrayView<const int8_t> bias_;
  const rtc::ArrayView<const int8_t> weights_;
  const rtc::ArrayView<const int8_t> recurrent_weights_;
  float (*const activation_function_)(float);
  // The state vector of a recurrent layer has length equal to |output_size_|.
  // However, to avoid dynamic allocation, over-allocation is used.
  std::array<float, kRecurrentLayersMaxUnits> state_;
};

// Recurrent network based VAD.
class RnnBasedVad {
 public:
  RnnBasedVad();
  RnnBasedVad(const RnnBasedVad&) = delete;
  RnnBasedVad& operator=(const RnnBasedVad&) = delete;
  ~RnnBasedVad();
  void Reset();
  // Compute and returns the probability of voice (range: [0.0, 1.0]).
  float ComputeVadProbability(
      rtc::ArrayView<const float, kFeatureVectorSize> feature_vector,
      bool is_silence);

 private:
  FullyConnectedLayer input_layer_;
  GatedRecurrentLayer hidden_layer_;
  FullyConnectedLayer output_layer_;
};

}  // namespace rnn_vad
}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AGC2_RNN_VAD_RNN_H_
