/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <algorithm>
#include <numeric>

#include "modules/audio_processing/agc2/rnn_vad/rnn_fc.h"
#include "rtc_base/checks.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "third_party/rnnoise/src/rnn_activations.h"
#include "third_party/rnnoise/src/rnn_vad_weights.h"

namespace webrtc {
namespace rnn_vad {
namespace {

std::vector<float> GetScaledParams(rtc::ArrayView<const int8_t> params) {
  std::vector<float> scaled_params(params.size());
  std::transform(params.begin(), params.end(), scaled_params.begin(),
                 [](int8_t x) -> float {
                   return ::rnnoise::kWeightsScale * static_cast<float>(x);
                 });
  return scaled_params;
}

// TODO(bugs.chromium.org/10480): Hard-code optimized layout and remove this
// function to improve setup time.
// Casts and scales `weights` and re-arranges the layout.
std::vector<float> PreprocessWeights(rtc::ArrayView<const int8_t> weights,
                                     int output_size) {
  if (output_size == 1) {
    return GetScaledParams(weights);
  }
  // Transpose, scale and cast.
  const int input_size = rtc::CheckedDivExact(
      rtc::dchecked_cast<int>(weights.size()), output_size);
  std::vector<float> w(weights.size());
  for (int o = 0; o < output_size; ++o) {
    for (int i = 0; i < input_size; ++i) {
      w[o * input_size + i] = rnnoise::kWeightsScale *
                              static_cast<float>(weights[i * output_size + o]);
    }
  }
  return w;
}

rtc::FunctionView<float(float)> GetActivationFunction(
    ActivationFunction activation_function) {
  switch (activation_function) {
    case ActivationFunction::kTansigApproximated:
      return ::rnnoise::TansigApproximated;
    case ActivationFunction::kSigmoidApproximated:
      return ::rnnoise::SigmoidApproximated;
  }
}

}  // namespace

FullyConnectedLayer::FullyConnectedLayer(
    const int input_size,
    const int output_size,
    const rtc::ArrayView<const int8_t> bias,
    const rtc::ArrayView<const int8_t> weights,
    ActivationFunction activation_function,
    const AvailableCpuFeatures& cpu_features,
    absl::string_view layer_name)
    : input_size_(input_size),
      output_size_(output_size),
      bias_(GetScaledParams(bias)),
      weights_(PreprocessWeights(weights, output_size)),
      vector_math_(cpu_features),
      activation_function_(GetActivationFunction(activation_function)) {
  RTC_DCHECK_LE(output_size_, kFullyConnectedLayerMaxUnits)
      << "Insufficient FC layer over-allocation (" << layer_name << ").";
  RTC_DCHECK_EQ(output_size_, bias_.size())
      << "Mismatching output size and bias terms array size (" << layer_name
      << ").";
  RTC_DCHECK_EQ(input_size_ * output_size_, weights_.size())
      << "Mismatching input-output size and weight coefficients array size ("
      << layer_name << ").";
}

FullyConnectedLayer::~FullyConnectedLayer() = default;

void FullyConnectedLayer::ComputeOutput(rtc::ArrayView<const float> input) {
  RTC_DCHECK_EQ(input.size(), input_size_);
  rtc::ArrayView<const float> weights(weights_);
  for (int o = 0; o < output_size_; ++o) {
    output_[o] = activation_function_(
        bias_[o] + vector_math_.DotProduct(
                       input, weights.subview(o * input_size_, input_size_)));
  }
}

}  // namespace rnn_vad
}  // namespace webrtc
