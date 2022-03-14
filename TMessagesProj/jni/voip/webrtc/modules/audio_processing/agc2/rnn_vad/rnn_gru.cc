/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc2/rnn_vad/rnn_gru.h"

#include "rtc_base/checks.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "third_party/rnnoise/src/rnn_activations.h"
#include "third_party/rnnoise/src/rnn_vad_weights.h"

namespace webrtc {
namespace rnn_vad {
namespace {

constexpr int kNumGruGates = 3;  // Update, reset, output.

std::vector<float> PreprocessGruTensor(rtc::ArrayView<const int8_t> tensor_src,
                                       int output_size) {
  // Transpose, cast and scale.
  // `n` is the size of the first dimension of the 3-dim tensor `weights`.
  const int n = rtc::CheckedDivExact(rtc::dchecked_cast<int>(tensor_src.size()),
                                     output_size * kNumGruGates);
  const int stride_src = kNumGruGates * output_size;
  const int stride_dst = n * output_size;
  std::vector<float> tensor_dst(tensor_src.size());
  for (int g = 0; g < kNumGruGates; ++g) {
    for (int o = 0; o < output_size; ++o) {
      for (int i = 0; i < n; ++i) {
        tensor_dst[g * stride_dst + o * n + i] =
            ::rnnoise::kWeightsScale *
            static_cast<float>(
                tensor_src[i * stride_src + g * output_size + o]);
      }
    }
  }
  return tensor_dst;
}

// Computes the output for the update or the reset gate.
// Operation: `g = sigmoid(W^T∙i + R^T∙s + b)` where
// - `g`: output gate vector
// - `W`: weights matrix
// - `i`: input vector
// - `R`: recurrent weights matrix
// - `s`: state gate vector
// - `b`: bias vector
void ComputeUpdateResetGate(int input_size,
                            int output_size,
                            const VectorMath& vector_math,
                            rtc::ArrayView<const float> input,
                            rtc::ArrayView<const float> state,
                            rtc::ArrayView<const float> bias,
                            rtc::ArrayView<const float> weights,
                            rtc::ArrayView<const float> recurrent_weights,
                            rtc::ArrayView<float> gate) {
  RTC_DCHECK_EQ(input.size(), input_size);
  RTC_DCHECK_EQ(state.size(), output_size);
  RTC_DCHECK_EQ(bias.size(), output_size);
  RTC_DCHECK_EQ(weights.size(), input_size * output_size);
  RTC_DCHECK_EQ(recurrent_weights.size(), output_size * output_size);
  RTC_DCHECK_GE(gate.size(), output_size);  // `gate` is over-allocated.
  for (int o = 0; o < output_size; ++o) {
    float x = bias[o];
    x += vector_math.DotProduct(input,
                                weights.subview(o * input_size, input_size));
    x += vector_math.DotProduct(
        state, recurrent_weights.subview(o * output_size, output_size));
    gate[o] = ::rnnoise::SigmoidApproximated(x);
  }
}

// Computes the output for the state gate.
// Operation: `s' = u .* s + (1 - u) .* ReLU(W^T∙i + R^T∙(s .* r) + b)` where
// - `s'`: output state gate vector
// - `s`: previous state gate vector
// - `u`: update gate vector
// - `W`: weights matrix
// - `i`: input vector
// - `R`: recurrent weights matrix
// - `r`: reset gate vector
// - `b`: bias vector
// - `.*` element-wise product
void ComputeStateGate(int input_size,
                      int output_size,
                      const VectorMath& vector_math,
                      rtc::ArrayView<const float> input,
                      rtc::ArrayView<const float> update,
                      rtc::ArrayView<const float> reset,
                      rtc::ArrayView<const float> bias,
                      rtc::ArrayView<const float> weights,
                      rtc::ArrayView<const float> recurrent_weights,
                      rtc::ArrayView<float> state) {
  RTC_DCHECK_EQ(input.size(), input_size);
  RTC_DCHECK_GE(update.size(), output_size);  // `update` is over-allocated.
  RTC_DCHECK_GE(reset.size(), output_size);   // `reset` is over-allocated.
  RTC_DCHECK_EQ(bias.size(), output_size);
  RTC_DCHECK_EQ(weights.size(), input_size * output_size);
  RTC_DCHECK_EQ(recurrent_weights.size(), output_size * output_size);
  RTC_DCHECK_EQ(state.size(), output_size);
  std::array<float, kGruLayerMaxUnits> reset_x_state;
  for (int o = 0; o < output_size; ++o) {
    reset_x_state[o] = state[o] * reset[o];
  }
  for (int o = 0; o < output_size; ++o) {
    float x = bias[o];
    x += vector_math.DotProduct(input,
                                weights.subview(o * input_size, input_size));
    x += vector_math.DotProduct(
        {reset_x_state.data(), static_cast<size_t>(output_size)},
        recurrent_weights.subview(o * output_size, output_size));
    state[o] = update[o] * state[o] + (1.f - update[o]) * std::max(0.f, x);
  }
}

}  // namespace

GatedRecurrentLayer::GatedRecurrentLayer(
    const int input_size,
    const int output_size,
    const rtc::ArrayView<const int8_t> bias,
    const rtc::ArrayView<const int8_t> weights,
    const rtc::ArrayView<const int8_t> recurrent_weights,
    const AvailableCpuFeatures& cpu_features,
    absl::string_view layer_name)
    : input_size_(input_size),
      output_size_(output_size),
      bias_(PreprocessGruTensor(bias, output_size)),
      weights_(PreprocessGruTensor(weights, output_size)),
      recurrent_weights_(PreprocessGruTensor(recurrent_weights, output_size)),
      vector_math_(cpu_features) {
  RTC_DCHECK_LE(output_size_, kGruLayerMaxUnits)
      << "Insufficient GRU layer over-allocation (" << layer_name << ").";
  RTC_DCHECK_EQ(kNumGruGates * output_size_, bias_.size())
      << "Mismatching output size and bias terms array size (" << layer_name
      << ").";
  RTC_DCHECK_EQ(kNumGruGates * input_size_ * output_size_, weights_.size())
      << "Mismatching input-output size and weight coefficients array size ("
      << layer_name << ").";
  RTC_DCHECK_EQ(kNumGruGates * output_size_ * output_size_,
                recurrent_weights_.size())
      << "Mismatching input-output size and recurrent weight coefficients array"
         " size ("
      << layer_name << ").";
  Reset();
}

GatedRecurrentLayer::~GatedRecurrentLayer() = default;

void GatedRecurrentLayer::Reset() {
  state_.fill(0.f);
}

void GatedRecurrentLayer::ComputeOutput(rtc::ArrayView<const float> input) {
  RTC_DCHECK_EQ(input.size(), input_size_);

  // The tensors below are organized as a sequence of flattened tensors for the
  // `update`, `reset` and `state` gates.
  rtc::ArrayView<const float> bias(bias_);
  rtc::ArrayView<const float> weights(weights_);
  rtc::ArrayView<const float> recurrent_weights(recurrent_weights_);
  // Strides to access to the flattened tensors for a specific gate.
  const int stride_weights = input_size_ * output_size_;
  const int stride_recurrent_weights = output_size_ * output_size_;

  rtc::ArrayView<float> state(state_.data(), output_size_);

  // Update gate.
  std::array<float, kGruLayerMaxUnits> update;
  ComputeUpdateResetGate(
      input_size_, output_size_, vector_math_, input, state,
      bias.subview(0, output_size_), weights.subview(0, stride_weights),
      recurrent_weights.subview(0, stride_recurrent_weights), update);
  // Reset gate.
  std::array<float, kGruLayerMaxUnits> reset;
  ComputeUpdateResetGate(input_size_, output_size_, vector_math_, input, state,
                         bias.subview(output_size_, output_size_),
                         weights.subview(stride_weights, stride_weights),
                         recurrent_weights.subview(stride_recurrent_weights,
                                                   stride_recurrent_weights),
                         reset);
  // State gate.
  ComputeStateGate(input_size_, output_size_, vector_math_, input, update,
                   reset, bias.subview(2 * output_size_, output_size_),
                   weights.subview(2 * stride_weights, stride_weights),
                   recurrent_weights.subview(2 * stride_recurrent_weights,
                                             stride_recurrent_weights),
                   state);
}

}  // namespace rnn_vad
}  // namespace webrtc
