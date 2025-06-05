/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/ns/prior_signal_model_estimator.h"

#include <math.h>

#include <algorithm>

#include "modules/audio_processing/ns/fast_math.h"
#include "rtc_base/checks.h"

namespace webrtc {

namespace {

// Identifies the first of the two largest peaks in the histogram.
void FindFirstOfTwoLargestPeaks(
    float bin_size,
    rtc::ArrayView<const int, kHistogramSize> spectral_flatness,
    float* peak_position,
    int* peak_weight) {
  RTC_DCHECK(peak_position);
  RTC_DCHECK(peak_weight);

  int peak_value = 0;
  int secondary_peak_value = 0;
  *peak_position = 0.f;
  float secondary_peak_position = 0.f;
  *peak_weight = 0;
  int secondary_peak_weight = 0;

  // Identify the two largest peaks.
  for (int i = 0; i < kHistogramSize; ++i) {
    const float bin_mid = (i + 0.5f) * bin_size;
    if (spectral_flatness[i] > peak_value) {
      // Found new "first" peak candidate.
      secondary_peak_value = peak_value;
      secondary_peak_weight = *peak_weight;
      secondary_peak_position = *peak_position;

      peak_value = spectral_flatness[i];
      *peak_weight = spectral_flatness[i];
      *peak_position = bin_mid;
    } else if (spectral_flatness[i] > secondary_peak_value) {
      // Found new "second" peak candidate.
      secondary_peak_value = spectral_flatness[i];
      secondary_peak_weight = spectral_flatness[i];
      secondary_peak_position = bin_mid;
    }
  }

  // Merge the peaks if they are close.
  if ((fabs(secondary_peak_position - *peak_position) < 2 * bin_size) &&
      (secondary_peak_weight > 0.5f * (*peak_weight))) {
    *peak_weight += secondary_peak_weight;
    *peak_position = 0.5f * (*peak_position + secondary_peak_position);
  }
}

void UpdateLrt(rtc::ArrayView<const int, kHistogramSize> lrt_histogram,
               float* prior_model_lrt,
               bool* low_lrt_fluctuations) {
  RTC_DCHECK(prior_model_lrt);
  RTC_DCHECK(low_lrt_fluctuations);

  float average = 0.f;
  float average_compl = 0.f;
  float average_squared = 0.f;
  int count = 0;

  for (int i = 0; i < 10; ++i) {
    float bin_mid = (i + 0.5f) * kBinSizeLrt;
    average += lrt_histogram[i] * bin_mid;
    count += lrt_histogram[i];
  }
  if (count > 0) {
    average = average / count;
  }

  for (int i = 0; i < kHistogramSize; ++i) {
    float bin_mid = (i + 0.5f) * kBinSizeLrt;
    average_squared += lrt_histogram[i] * bin_mid * bin_mid;
    average_compl += lrt_histogram[i] * bin_mid;
  }
  constexpr float kOneFeatureUpdateWindowSize = 1.f / kFeatureUpdateWindowSize;
  average_squared = average_squared * kOneFeatureUpdateWindowSize;
  average_compl = average_compl * kOneFeatureUpdateWindowSize;

  // Fluctuation limit of LRT feature.
  *low_lrt_fluctuations = average_squared - average * average_compl < 0.05f;

  // Get threshold for LRT feature.
  constexpr float kMaxLrt = 1.f;
  constexpr float kMinLrt = .2f;
  if (*low_lrt_fluctuations) {
    // Very low fluctuation, so likely noise.
    *prior_model_lrt = kMaxLrt;
  } else {
    *prior_model_lrt = std::min(kMaxLrt, std::max(kMinLrt, 1.2f * average));
  }
}

}  // namespace

PriorSignalModelEstimator::PriorSignalModelEstimator(float lrt_initial_value)
    : prior_model_(lrt_initial_value) {}

// Extract thresholds for feature parameters and computes the threshold/weights.
void PriorSignalModelEstimator::Update(const Histograms& histograms) {
  bool low_lrt_fluctuations;
  UpdateLrt(histograms.get_lrt(), &prior_model_.lrt, &low_lrt_fluctuations);

  // For spectral flatness and spectral difference: compute the main peaks of
  // the histograms.
  float spectral_flatness_peak_position;
  int spectral_flatness_peak_weight;
  FindFirstOfTwoLargestPeaks(
      kBinSizeSpecFlat, histograms.get_spectral_flatness(),
      &spectral_flatness_peak_position, &spectral_flatness_peak_weight);

  float spectral_diff_peak_position = 0.f;
  int spectral_diff_peak_weight = 0;
  FindFirstOfTwoLargestPeaks(kBinSizeSpecDiff, histograms.get_spectral_diff(),
                             &spectral_diff_peak_position,
                             &spectral_diff_peak_weight);

  // Reject if weight of peaks is not large enough, or peak value too small.
  // Peak limit for spectral flatness (varies between 0 and 1).
  const int use_spec_flat = spectral_flatness_peak_weight < 0.3f * 500 ||
                                    spectral_flatness_peak_position < 0.6f
                                ? 0
                                : 1;

  // Reject if weight of peaks is not large enough or if fluctuation of the LRT
  // feature are very low, indicating a noise state.
  const int use_spec_diff =
      spectral_diff_peak_weight < 0.3f * 500 || low_lrt_fluctuations ? 0 : 1;

  // Update the model.
  prior_model_.template_diff_threshold = 1.2f * spectral_diff_peak_position;
  prior_model_.template_diff_threshold =
      std::min(1.f, std::max(0.16f, prior_model_.template_diff_threshold));

  float one_by_feature_sum = 1.f / (1.f + use_spec_flat + use_spec_diff);
  prior_model_.lrt_weighting = one_by_feature_sum;

  if (use_spec_flat == 1) {
    prior_model_.flatness_threshold = 0.9f * spectral_flatness_peak_position;
    prior_model_.flatness_threshold =
        std::min(.95f, std::max(0.1f, prior_model_.flatness_threshold));
    prior_model_.flatness_weighting = one_by_feature_sum;
  } else {
    prior_model_.flatness_weighting = 0.f;
  }

  if (use_spec_diff == 1) {
    prior_model_.difference_weighting = one_by_feature_sum;
  } else {
    prior_model_.difference_weighting = 0.f;
  }
}

}  // namespace webrtc
