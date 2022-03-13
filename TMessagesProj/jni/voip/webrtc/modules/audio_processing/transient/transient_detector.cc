/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/transient/transient_detector.h"

#include <float.h>
#include <string.h>

#include <algorithm>
#include <cmath>

#include "modules/audio_processing/transient/common.h"
#include "modules/audio_processing/transient/daubechies_8_wavelet_coeffs.h"
#include "modules/audio_processing/transient/moving_moments.h"
#include "modules/audio_processing/transient/wpd_node.h"
#include "modules/audio_processing/transient/wpd_tree.h"
#include "rtc_base/checks.h"

namespace webrtc {

static const int kTransientLengthMs = 30;
static const int kChunksAtStartupLeftToDelete =
    kTransientLengthMs / ts::kChunkSizeMs;
static const float kDetectThreshold = 16.f;

TransientDetector::TransientDetector(int sample_rate_hz)
    : samples_per_chunk_(sample_rate_hz * ts::kChunkSizeMs / 1000),
      last_first_moment_(),
      last_second_moment_(),
      chunks_at_startup_left_to_delete_(kChunksAtStartupLeftToDelete),
      reference_energy_(1.f),
      using_reference_(false) {
  RTC_DCHECK(sample_rate_hz == ts::kSampleRate8kHz ||
             sample_rate_hz == ts::kSampleRate16kHz ||
             sample_rate_hz == ts::kSampleRate32kHz ||
             sample_rate_hz == ts::kSampleRate48kHz);
  int samples_per_transient = sample_rate_hz * kTransientLengthMs / 1000;
  // Adjustment to avoid data loss while downsampling, making
  // `samples_per_chunk_` and `samples_per_transient` always divisible by
  // `kLeaves`.
  samples_per_chunk_ -= samples_per_chunk_ % kLeaves;
  samples_per_transient -= samples_per_transient % kLeaves;

  tree_leaves_data_length_ = samples_per_chunk_ / kLeaves;
  wpd_tree_.reset(new WPDTree(samples_per_chunk_,
                              kDaubechies8HighPassCoefficients,
                              kDaubechies8LowPassCoefficients,
                              kDaubechies8CoefficientsLength, kLevels));
  for (size_t i = 0; i < kLeaves; ++i) {
    moving_moments_[i].reset(
        new MovingMoments(samples_per_transient / kLeaves));
  }

  first_moments_.reset(new float[tree_leaves_data_length_]);
  second_moments_.reset(new float[tree_leaves_data_length_]);

  for (int i = 0; i < kChunksAtStartupLeftToDelete; ++i) {
    previous_results_.push_back(0.f);
  }
}

TransientDetector::~TransientDetector() {}

float TransientDetector::Detect(const float* data,
                                size_t data_length,
                                const float* reference_data,
                                size_t reference_length) {
  RTC_DCHECK(data);
  RTC_DCHECK_EQ(samples_per_chunk_, data_length);

  // TODO(aluebs): Check if these errors can logically happen and if not assert
  // on them.
  if (wpd_tree_->Update(data, samples_per_chunk_) != 0) {
    return -1.f;
  }

  float result = 0.f;

  for (size_t i = 0; i < kLeaves; ++i) {
    WPDNode* leaf = wpd_tree_->NodeAt(kLevels, i);

    moving_moments_[i]->CalculateMoments(leaf->data(), tree_leaves_data_length_,
                                         first_moments_.get(),
                                         second_moments_.get());

    // Add value delayed (Use the last moments from the last call to Detect).
    float unbiased_data = leaf->data()[0] - last_first_moment_[i];
    result +=
        unbiased_data * unbiased_data / (last_second_moment_[i] + FLT_MIN);

    // Add new values.
    for (size_t j = 1; j < tree_leaves_data_length_; ++j) {
      unbiased_data = leaf->data()[j] - first_moments_[j - 1];
      result +=
          unbiased_data * unbiased_data / (second_moments_[j - 1] + FLT_MIN);
    }

    last_first_moment_[i] = first_moments_[tree_leaves_data_length_ - 1];
    last_second_moment_[i] = second_moments_[tree_leaves_data_length_ - 1];
  }

  result /= tree_leaves_data_length_;

  result *= ReferenceDetectionValue(reference_data, reference_length);

  if (chunks_at_startup_left_to_delete_ > 0) {
    chunks_at_startup_left_to_delete_--;
    result = 0.f;
  }

  if (result >= kDetectThreshold) {
    result = 1.f;
  } else {
    // Get proportional value.
    // Proportion achieved with a squared raised cosine function with domain
    // [0, kDetectThreshold) and image [0, 1), it's always increasing.
    const float horizontal_scaling = ts::kPi / kDetectThreshold;
    const float kHorizontalShift = ts::kPi;
    const float kVerticalScaling = 0.5f;
    const float kVerticalShift = 1.f;

    result = (std::cos(result * horizontal_scaling + kHorizontalShift) +
              kVerticalShift) *
             kVerticalScaling;
    result *= result;
  }

  previous_results_.pop_front();
  previous_results_.push_back(result);

  // In the current implementation we return the max of the current result and
  // the previous results, so the high results have a width equals to
  // `transient_length`.
  return *std::max_element(previous_results_.begin(), previous_results_.end());
}

// Looks for the highest slope and compares it with the previous ones.
// An exponential transformation takes this to the [0, 1] range. This value is
// multiplied by the detection result to avoid false positives.
float TransientDetector::ReferenceDetectionValue(const float* data,
                                                 size_t length) {
  if (data == NULL) {
    using_reference_ = false;
    return 1.f;
  }
  static const float kEnergyRatioThreshold = 0.2f;
  static const float kReferenceNonLinearity = 20.f;
  static const float kMemory = 0.99f;
  float reference_energy = 0.f;
  for (size_t i = 1; i < length; ++i) {
    reference_energy += data[i] * data[i];
  }
  if (reference_energy == 0.f) {
    using_reference_ = false;
    return 1.f;
  }
  RTC_DCHECK_NE(0, reference_energy_);
  float result = 1.f / (1.f + std::exp(kReferenceNonLinearity *
                                       (kEnergyRatioThreshold -
                                        reference_energy / reference_energy_)));
  reference_energy_ =
      kMemory * reference_energy_ + (1.f - kMemory) * reference_energy;

  using_reference_ = true;

  return result;
}

}  // namespace webrtc
