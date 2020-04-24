/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc2/rnn_vad/rnn.h"

#include <algorithm>
#include <array>
#include <cmath>

#include "rtc_base/checks.h"
#include "third_party/rnnoise/src/rnn_activations.h"
#include "third_party/rnnoise/src/rnn_vad_weights.h"

namespace webrtc {
namespace rnn_vad {

using rnnoise::kWeightsScale;

using rnnoise::kInputLayerInputSize;
static_assert(kFeatureVectorSize == kInputLayerInputSize, "");
using rnnoise::kInputDenseWeights;
using rnnoise::kInputDenseBias;
using rnnoise::kInputLayerOutputSize;
static_assert(kInputLayerOutputSize <= kFullyConnectedLayersMaxUnits,
              "Increase kFullyConnectedLayersMaxUnits.");

using rnnoise::kHiddenGruRecurrentWeights;
using rnnoise::kHiddenGruWeights;
using rnnoise::kHiddenGruBias;
using rnnoise::kHiddenLayerOutputSize;
static_assert(kHiddenLayerOutputSize <= kRecurrentLayersMaxUnits,
              "Increase kRecurrentLayersMaxUnits.");

using rnnoise::kOutputDenseWeights;
using rnnoise::kOutputDenseBias;
using rnnoise::kOutputLayerOutputSize;
static_assert(kOutputLayerOutputSize <= kFullyConnectedLayersMaxUnits,
              "Increase kFullyConnectedLayersMaxUnits.");

using rnnoise::RectifiedLinearUnit;
using rnnoise::SigmoidApproximated;
using rnnoise::TansigApproximated;

FullyConnectedLayer::FullyConnectedLayer(
    const size_t input_size,
    const size_t output_size,
    const rtc::ArrayView<const int8_t> bias,
    const rtc::ArrayView<const int8_t> weights,
    float (*const activation_function)(float))
    : input_size_(input_size),
      output_size_(output_size),
      bias_(bias),
      weights_(weights),
      activation_function_(activation_function) {
  RTC_DCHECK_LE(output_size_, kFullyConnectedLayersMaxUnits)
      << "Static over-allocation of fully-connected layers output vectors is "
         "not sufficient.";
  RTC_DCHECK_EQ(output_size_, bias_.size())
      << "Mismatching output size and bias terms array size.";
  RTC_DCHECK_EQ(input_size_ * output_size_, weights_.size())
      << "Mismatching input-output size and weight coefficients array size.";
}

FullyConnectedLayer::~FullyConnectedLayer() = default;

rtc::ArrayView<const float> FullyConnectedLayer::GetOutput() const {
  return rtc::ArrayView<const float>(output_.data(), output_size_);
}

void FullyConnectedLayer::ComputeOutput(rtc::ArrayView<const float> input) {
  // TODO(bugs.chromium.org/9076): Optimize using SSE/AVX fused multiply-add
  // operations.
  for (size_t o = 0; o < output_size_; ++o) {
    output_[o] = bias_[o];
    // TODO(bugs.chromium.org/9076): Benchmark how different layouts for
    // |weights_| change the performance across different platforms.
    for (size_t i = 0; i < input_size_; ++i) {
      output_[o] += input[i] * weights_[i * output_size_ + o];
    }
    output_[o] = (*activation_function_)(kWeightsScale * output_[o]);
  }
}

GatedRecurrentLayer::GatedRecurrentLayer(
    const size_t input_size,
    const size_t output_size,
    const rtc::ArrayView<const int8_t> bias,
    const rtc::ArrayView<const int8_t> weights,
    const rtc::ArrayView<const int8_t> recurrent_weights,
    float (*const activation_function)(float))
    : input_size_(input_size),
      output_size_(output_size),
      bias_(bias),
      weights_(weights),
      recurrent_weights_(recurrent_weights),
      activation_function_(activation_function) {
  RTC_DCHECK_LE(output_size_, kRecurrentLayersMaxUnits)
      << "Static over-allocation of recurrent layers state vectors is not "
      << "sufficient.";
  RTC_DCHECK_EQ(3 * output_size_, bias_.size())
      << "Mismatching output size and bias terms array size.";
  RTC_DCHECK_EQ(3 * input_size_ * output_size_, weights_.size())
      << "Mismatching input-output size and weight coefficients array size.";
  RTC_DCHECK_EQ(3 * input_size_ * output_size_, recurrent_weights_.size())
      << "Mismatching input-output size and recurrent weight coefficients array"
      << " size.";
  Reset();
}

GatedRecurrentLayer::~GatedRecurrentLayer() = default;

rtc::ArrayView<const float> GatedRecurrentLayer::GetOutput() const {
  return rtc::ArrayView<const float>(state_.data(), output_size_);
}

void GatedRecurrentLayer::Reset() {
  state_.fill(0.f);
}

void GatedRecurrentLayer::ComputeOutput(rtc::ArrayView<const float> input) {
  // TODO(bugs.chromium.org/9076): Optimize using SSE/AVX fused multiply-add
  // operations.
  // Stride and offset used to read parameter arrays.
  const size_t stride = 3 * output_size_;
  size_t offset = 0;

  // Compute update gates.
  std::array<float, kRecurrentLayersMaxUnits> update;
  for (size_t o = 0; o < output_size_; ++o) {
    update[o] = bias_[o];
    // TODO(bugs.chromium.org/9076): Benchmark how different layouts for
    // |weights_| and |recurrent_weights_| change the performance across
    // different platforms.
    for (size_t i = 0; i < input_size_; ++i) {  // Add input.
      update[o] += input[i] * weights_[i * stride + o];
    }
    for (size_t s = 0; s < output_size_; ++s) {
      update[o] += state_[s] * recurrent_weights_[s * stride + o];
    }  // Add state.
    update[o] = SigmoidApproximated(kWeightsScale * update[o]);
  }

  // Compute reset gates.
  offset += output_size_;
  std::array<float, kRecurrentLayersMaxUnits> reset;
  for (size_t o = 0; o < output_size_; ++o) {
    reset[o] = bias_[offset + o];
    for (size_t i = 0; i < input_size_; ++i) {  // Add input.
      reset[o] += input[i] * weights_[offset + i * stride + o];
    }
    for (size_t s = 0; s < output_size_; ++s) {  // Add state.
      reset[o] += state_[s] * recurrent_weights_[offset + s * stride + o];
    }
    reset[o] = SigmoidApproximated(kWeightsScale * reset[o]);
  }

  // Compute output.
  offset += output_size_;
  std::array<float, kRecurrentLayersMaxUnits> output;
  for (size_t o = 0; o < output_size_; ++o) {
    output[o] = bias_[offset + o];
    for (size_t i = 0; i < input_size_; ++i) {  // Add input.
      output[o] += input[i] * weights_[offset + i * stride + o];
    }
    for (size_t s = 0; s < output_size_;
         ++s) {  // Add state through reset gates.
      output[o] +=
          state_[s] * recurrent_weights_[offset + s * stride + o] * reset[s];
    }
    output[o] = (*activation_function_)(kWeightsScale * output[o]);
    // Update output through the update gates.
    output[o] = update[o] * state_[o] + (1.f - update[o]) * output[o];
  }

  // Update the state. Not done in the previous loop since that would pollute
  // the current state and lead to incorrect output values.
  std::copy(output.begin(), output.end(), state_.begin());
}

RnnBasedVad::RnnBasedVad()
    : input_layer_(kInputLayerInputSize,
                   kInputLayerOutputSize,
                   kInputDenseBias,
                   kInputDenseWeights,
                   TansigApproximated),
      hidden_layer_(kInputLayerOutputSize,
                    kHiddenLayerOutputSize,
                    kHiddenGruBias,
                    kHiddenGruWeights,
                    kHiddenGruRecurrentWeights,
                    RectifiedLinearUnit),
      output_layer_(kHiddenLayerOutputSize,
                    kOutputLayerOutputSize,
                    kOutputDenseBias,
                    kOutputDenseWeights,
                    SigmoidApproximated) {
  // Input-output chaining size checks.
  RTC_DCHECK_EQ(input_layer_.output_size(), hidden_layer_.input_size())
      << "The input and the hidden layers sizes do not match.";
  RTC_DCHECK_EQ(hidden_layer_.output_size(), output_layer_.input_size())
      << "The hidden and the output layers sizes do not match.";
}

RnnBasedVad::~RnnBasedVad() = default;

void RnnBasedVad::Reset() {
  hidden_layer_.Reset();
}

float RnnBasedVad::ComputeVadProbability(
    rtc::ArrayView<const float, kFeatureVectorSize> feature_vector,
    bool is_silence) {
  if (is_silence) {
    Reset();
    return 0.f;
  }
  input_layer_.ComputeOutput(feature_vector);
  hidden_layer_.ComputeOutput(input_layer_.GetOutput());
  output_layer_.ComputeOutput(hidden_layer_.GetOutput());
  const auto vad_output = output_layer_.GetOutput();
  return vad_output[0];
}

}  // namespace rnn_vad
}  // namespace webrtc
