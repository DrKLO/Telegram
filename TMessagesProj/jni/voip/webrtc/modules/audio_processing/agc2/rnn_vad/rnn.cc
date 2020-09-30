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

// Defines WEBRTC_ARCH_X86_FAMILY, used below.
#include "rtc_base/system/arch.h"

#if defined(WEBRTC_HAS_NEON)
#include <arm_neon.h>
#endif
#if defined(WEBRTC_ARCH_X86_FAMILY)
#include <emmintrin.h>
#endif
#include <algorithm>
#include <array>
#include <cmath>
#include <numeric>

#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "third_party/rnnoise/src/rnn_activations.h"
#include "third_party/rnnoise/src/rnn_vad_weights.h"

namespace webrtc {
namespace rnn_vad {
namespace {

using rnnoise::kWeightsScale;

using rnnoise::kInputLayerInputSize;
static_assert(kFeatureVectorSize == kInputLayerInputSize, "");
using rnnoise::kInputDenseBias;
using rnnoise::kInputDenseWeights;
using rnnoise::kInputLayerOutputSize;
static_assert(kInputLayerOutputSize <= kFullyConnectedLayersMaxUnits,
              "Increase kFullyConnectedLayersMaxUnits.");

using rnnoise::kHiddenGruBias;
using rnnoise::kHiddenGruRecurrentWeights;
using rnnoise::kHiddenGruWeights;
using rnnoise::kHiddenLayerOutputSize;
static_assert(kHiddenLayerOutputSize <= kRecurrentLayersMaxUnits,
              "Increase kRecurrentLayersMaxUnits.");

using rnnoise::kOutputDenseBias;
using rnnoise::kOutputDenseWeights;
using rnnoise::kOutputLayerOutputSize;
static_assert(kOutputLayerOutputSize <= kFullyConnectedLayersMaxUnits,
              "Increase kFullyConnectedLayersMaxUnits.");

using rnnoise::SigmoidApproximated;
using rnnoise::TansigApproximated;

inline float RectifiedLinearUnit(float x) {
  return x < 0.f ? 0.f : x;
}

std::vector<float> GetScaledParams(rtc::ArrayView<const int8_t> params) {
  std::vector<float> scaled_params(params.size());
  std::transform(params.begin(), params.end(), scaled_params.begin(),
                 [](int8_t x) -> float {
                   return rnnoise::kWeightsScale * static_cast<float>(x);
                 });
  return scaled_params;
}

// TODO(bugs.chromium.org/10480): Hard-code optimized layout and remove this
// function to improve setup time.
// Casts and scales |weights| and re-arranges the layout.
std::vector<float> GetPreprocessedFcWeights(
    rtc::ArrayView<const int8_t> weights,
    size_t output_size) {
  if (output_size == 1) {
    return GetScaledParams(weights);
  }
  // Transpose, scale and cast.
  const size_t input_size = rtc::CheckedDivExact(weights.size(), output_size);
  std::vector<float> w(weights.size());
  for (size_t o = 0; o < output_size; ++o) {
    for (size_t i = 0; i < input_size; ++i) {
      w[o * input_size + i] = rnnoise::kWeightsScale *
                              static_cast<float>(weights[i * output_size + o]);
    }
  }
  return w;
}

constexpr size_t kNumGruGates = 3;  // Update, reset, output.

// TODO(bugs.chromium.org/10480): Hard-coded optimized layout and remove this
// function to improve setup time.
// Casts and scales |tensor_src| for a GRU layer and re-arranges the layout.
// It works both for weights, recurrent weights and bias.
std::vector<float> GetPreprocessedGruTensor(
    rtc::ArrayView<const int8_t> tensor_src,
    size_t output_size) {
  // Transpose, cast and scale.
  // |n| is the size of the first dimension of the 3-dim tensor |weights|.
  const size_t n =
      rtc::CheckedDivExact(tensor_src.size(), output_size * kNumGruGates);
  const size_t stride_src = kNumGruGates * output_size;
  const size_t stride_dst = n * output_size;
  std::vector<float> tensor_dst(tensor_src.size());
  for (size_t g = 0; g < kNumGruGates; ++g) {
    for (size_t o = 0; o < output_size; ++o) {
      for (size_t i = 0; i < n; ++i) {
        tensor_dst[g * stride_dst + o * n + i] =
            rnnoise::kWeightsScale *
            static_cast<float>(
                tensor_src[i * stride_src + g * output_size + o]);
      }
    }
  }
  return tensor_dst;
}

void ComputeGruUpdateResetGates(size_t input_size,
                                size_t output_size,
                                rtc::ArrayView<const float> weights,
                                rtc::ArrayView<const float> recurrent_weights,
                                rtc::ArrayView<const float> bias,
                                rtc::ArrayView<const float> input,
                                rtc::ArrayView<const float> state,
                                rtc::ArrayView<float> gate) {
  for (size_t o = 0; o < output_size; ++o) {
    gate[o] = bias[o];
    for (size_t i = 0; i < input_size; ++i) {
      gate[o] += input[i] * weights[o * input_size + i];
    }
    for (size_t s = 0; s < output_size; ++s) {
      gate[o] += state[s] * recurrent_weights[o * output_size + s];
    }
    gate[o] = SigmoidApproximated(gate[o]);
  }
}

void ComputeGruOutputGate(size_t input_size,
                          size_t output_size,
                          rtc::ArrayView<const float> weights,
                          rtc::ArrayView<const float> recurrent_weights,
                          rtc::ArrayView<const float> bias,
                          rtc::ArrayView<const float> input,
                          rtc::ArrayView<const float> state,
                          rtc::ArrayView<const float> reset,
                          rtc::ArrayView<float> gate) {
  for (size_t o = 0; o < output_size; ++o) {
    gate[o] = bias[o];
    for (size_t i = 0; i < input_size; ++i) {
      gate[o] += input[i] * weights[o * input_size + i];
    }
    for (size_t s = 0; s < output_size; ++s) {
      gate[o] += state[s] * recurrent_weights[o * output_size + s] * reset[s];
    }
    gate[o] = RectifiedLinearUnit(gate[o]);
  }
}

// Gated recurrent unit (GRU) layer un-optimized implementation.
void ComputeGruLayerOutput(size_t input_size,
                           size_t output_size,
                           rtc::ArrayView<const float> input,
                           rtc::ArrayView<const float> weights,
                           rtc::ArrayView<const float> recurrent_weights,
                           rtc::ArrayView<const float> bias,
                           rtc::ArrayView<float> state) {
  RTC_DCHECK_EQ(input_size, input.size());
  // Stride and offset used to read parameter arrays.
  const size_t stride_in = input_size * output_size;
  const size_t stride_out = output_size * output_size;

  // Update gate.
  std::array<float, kRecurrentLayersMaxUnits> update;
  ComputeGruUpdateResetGates(
      input_size, output_size, weights.subview(0, stride_in),
      recurrent_weights.subview(0, stride_out), bias.subview(0, output_size),
      input, state, update);

  // Reset gate.
  std::array<float, kRecurrentLayersMaxUnits> reset;
  ComputeGruUpdateResetGates(
      input_size, output_size, weights.subview(stride_in, stride_in),
      recurrent_weights.subview(stride_out, stride_out),
      bias.subview(output_size, output_size), input, state, reset);

  // Output gate.
  std::array<float, kRecurrentLayersMaxUnits> output;
  ComputeGruOutputGate(
      input_size, output_size, weights.subview(2 * stride_in, stride_in),
      recurrent_weights.subview(2 * stride_out, stride_out),
      bias.subview(2 * output_size, output_size), input, state, reset, output);

  // Update output through the update gates and update the state.
  for (size_t o = 0; o < output_size; ++o) {
    output[o] = update[o] * state[o] + (1.f - update[o]) * output[o];
    state[o] = output[o];
  }
}

// Fully connected layer un-optimized implementation.
void ComputeFullyConnectedLayerOutput(
    size_t input_size,
    size_t output_size,
    rtc::ArrayView<const float> input,
    rtc::ArrayView<const float> bias,
    rtc::ArrayView<const float> weights,
    rtc::FunctionView<float(float)> activation_function,
    rtc::ArrayView<float> output) {
  RTC_DCHECK_EQ(input.size(), input_size);
  RTC_DCHECK_EQ(bias.size(), output_size);
  RTC_DCHECK_EQ(weights.size(), input_size * output_size);
  for (size_t o = 0; o < output_size; ++o) {
    output[o] = bias[o];
    // TODO(bugs.chromium.org/9076): Benchmark how different layouts for
    // |weights_| change the performance across different platforms.
    for (size_t i = 0; i < input_size; ++i) {
      output[o] += input[i] * weights[o * input_size + i];
    }
    output[o] = activation_function(output[o]);
  }
}

#if defined(WEBRTC_ARCH_X86_FAMILY)
// Fully connected layer SSE2 implementation.
void ComputeFullyConnectedLayerOutputSse2(
    size_t input_size,
    size_t output_size,
    rtc::ArrayView<const float> input,
    rtc::ArrayView<const float> bias,
    rtc::ArrayView<const float> weights,
    rtc::FunctionView<float(float)> activation_function,
    rtc::ArrayView<float> output) {
  RTC_DCHECK_EQ(input.size(), input_size);
  RTC_DCHECK_EQ(bias.size(), output_size);
  RTC_DCHECK_EQ(weights.size(), input_size * output_size);
  const size_t input_size_by_4 = input_size >> 2;
  const size_t offset = input_size & ~3;
  __m128 sum_wx_128;
  const float* v = reinterpret_cast<const float*>(&sum_wx_128);
  for (size_t o = 0; o < output_size; ++o) {
    // Perform 128 bit vector operations.
    sum_wx_128 = _mm_set1_ps(0);
    const float* x_p = input.data();
    const float* w_p = weights.data() + o * input_size;
    for (size_t i = 0; i < input_size_by_4; ++i, x_p += 4, w_p += 4) {
      sum_wx_128 = _mm_add_ps(sum_wx_128,
                              _mm_mul_ps(_mm_loadu_ps(x_p), _mm_loadu_ps(w_p)));
    }
    // Perform non-vector operations for any remaining items, sum up bias term
    // and results from the vectorized code, and apply the activation function.
    output[o] = activation_function(
        std::inner_product(input.begin() + offset, input.end(),
                           weights.begin() + o * input_size + offset,
                           bias[o] + v[0] + v[1] + v[2] + v[3]));
  }
}
#endif

}  // namespace

FullyConnectedLayer::FullyConnectedLayer(
    const size_t input_size,
    const size_t output_size,
    const rtc::ArrayView<const int8_t> bias,
    const rtc::ArrayView<const int8_t> weights,
    rtc::FunctionView<float(float)> activation_function,
    Optimization optimization)
    : input_size_(input_size),
      output_size_(output_size),
      bias_(GetScaledParams(bias)),
      weights_(GetPreprocessedFcWeights(weights, output_size)),
      activation_function_(activation_function),
      optimization_(optimization) {
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
  switch (optimization_) {
#if defined(WEBRTC_ARCH_X86_FAMILY)
    case Optimization::kSse2:
      ComputeFullyConnectedLayerOutputSse2(input_size_, output_size_, input,
                                           bias_, weights_,
                                           activation_function_, output_);
      break;
#endif
#if defined(WEBRTC_HAS_NEON)
    case Optimization::kNeon:
      // TODO(bugs.chromium.org/10480): Handle Optimization::kNeon.
      ComputeFullyConnectedLayerOutput(input_size_, output_size_, input, bias_,
                                       weights_, activation_function_, output_);
      break;
#endif
    default:
      ComputeFullyConnectedLayerOutput(input_size_, output_size_, input, bias_,
                                       weights_, activation_function_, output_);
  }
}

GatedRecurrentLayer::GatedRecurrentLayer(
    const size_t input_size,
    const size_t output_size,
    const rtc::ArrayView<const int8_t> bias,
    const rtc::ArrayView<const int8_t> weights,
    const rtc::ArrayView<const int8_t> recurrent_weights,
    Optimization optimization)
    : input_size_(input_size),
      output_size_(output_size),
      bias_(GetPreprocessedGruTensor(bias, output_size)),
      weights_(GetPreprocessedGruTensor(weights, output_size)),
      recurrent_weights_(
          GetPreprocessedGruTensor(recurrent_weights, output_size)),
      optimization_(optimization) {
  RTC_DCHECK_LE(output_size_, kRecurrentLayersMaxUnits)
      << "Static over-allocation of recurrent layers state vectors is not "
         "sufficient.";
  RTC_DCHECK_EQ(kNumGruGates * output_size_, bias_.size())
      << "Mismatching output size and bias terms array size.";
  RTC_DCHECK_EQ(kNumGruGates * input_size_ * output_size_, weights_.size())
      << "Mismatching input-output size and weight coefficients array size.";
  RTC_DCHECK_EQ(kNumGruGates * output_size_ * output_size_,
                recurrent_weights_.size())
      << "Mismatching input-output size and recurrent weight coefficients array"
         " size.";
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
  switch (optimization_) {
#if defined(WEBRTC_ARCH_X86_FAMILY)
    case Optimization::kSse2:
      // TODO(bugs.chromium.org/10480): Handle Optimization::kSse2.
      ComputeGruLayerOutput(input_size_, output_size_, input, weights_,
                            recurrent_weights_, bias_, state_);
      break;
#endif
#if defined(WEBRTC_HAS_NEON)
    case Optimization::kNeon:
      // TODO(bugs.chromium.org/10480): Handle Optimization::kNeon.
      ComputeGruLayerOutput(input_size_, output_size_, input, weights_,
                            recurrent_weights_, bias_, state_);
      break;
#endif
    default:
      ComputeGruLayerOutput(input_size_, output_size_, input, weights_,
                            recurrent_weights_, bias_, state_);
  }
}

RnnBasedVad::RnnBasedVad()
    : input_layer_(kInputLayerInputSize,
                   kInputLayerOutputSize,
                   kInputDenseBias,
                   kInputDenseWeights,
                   TansigApproximated,
                   DetectOptimization()),
      hidden_layer_(kInputLayerOutputSize,
                    kHiddenLayerOutputSize,
                    kHiddenGruBias,
                    kHiddenGruWeights,
                    kHiddenGruRecurrentWeights,
                    DetectOptimization()),
      output_layer_(kHiddenLayerOutputSize,
                    kOutputLayerOutputSize,
                    kOutputDenseBias,
                    kOutputDenseWeights,
                    SigmoidApproximated,
                    DetectOptimization()) {
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
