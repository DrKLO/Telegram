/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc/clipping_predictor_evaluator.h"

#include <algorithm>

#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace {

// Returns the index of the oldest item in the ring buffer for a non-empty
// ring buffer with give `size`, `tail` index and `capacity`.
int OldestExpectedDetectionIndex(int size, int tail, int capacity) {
  RTC_DCHECK_GT(size, 0);
  return tail - size + (tail < size ? capacity : 0);
}

}  // namespace

ClippingPredictorEvaluator::ClippingPredictorEvaluator(int history_size)
    : history_size_(history_size),
      ring_buffer_capacity_(history_size + 1),
      ring_buffer_(ring_buffer_capacity_) {
  RTC_DCHECK_GT(history_size_, 0);
  Reset();
  counters_.true_positives = 0;
  counters_.true_negatives = 0;
  counters_.false_positives = 0;
  counters_.false_negatives = 0;
}

ClippingPredictorEvaluator::~ClippingPredictorEvaluator() = default;

absl::optional<int> ClippingPredictorEvaluator::Observe(
    bool clipping_detected,
    bool clipping_predicted) {
  RTC_DCHECK_GE(ring_buffer_size_, 0);
  RTC_DCHECK_LE(ring_buffer_size_, ring_buffer_capacity_);
  RTC_DCHECK_GE(ring_buffer_tail_, 0);
  RTC_DCHECK_LT(ring_buffer_tail_, ring_buffer_capacity_);

  DecreaseTimesToLive();
  // Clipping is expected if there are expected detections regardless of
  // whether all the expected detections have been previously matched - i.e.,
  // `ExpectedDetection::detected` is true.
  const bool clipping_expected = ring_buffer_size_ > 0;

  absl::optional<int> prediction_interval;
  if (clipping_expected && clipping_detected) {
    prediction_interval = FindEarliestPredictionInterval();
    // Add a true positive for each unexpired expected detection.
    const int num_modified_items = MarkExpectedDetectionAsDetected();
    counters_.true_positives += num_modified_items;
    RTC_DCHECK(prediction_interval.has_value() || num_modified_items == 0);
    RTC_DCHECK(!prediction_interval.has_value() || num_modified_items > 0);
  } else if (clipping_expected && !clipping_detected) {
    // Add a false positive if there is one expected detection that has expired
    // and that has never been matched before. Note that there is at most one
    // unmatched expired detection.
    if (HasExpiredUnmatchedExpectedDetection()) {
      counters_.false_positives++;
    }
  } else if (!clipping_expected && clipping_detected) {
    counters_.false_negatives++;
  } else {
    RTC_DCHECK(!clipping_expected && !clipping_detected);
    counters_.true_negatives++;
  }

  if (clipping_predicted) {
    // TODO(bugs.webrtc.org/12874): Use designated initializers one fixed.
    Push(/*expected_detection=*/{/*ttl=*/history_size_, /*detected=*/false});
  }

  return prediction_interval;
}

void ClippingPredictorEvaluator::RemoveExpectations() {
  // Empty the ring buffer of expected detections.
  ring_buffer_tail_ = 0;
  ring_buffer_size_ = 0;
}

void ClippingPredictorEvaluator::Reset() {
  counters_.true_positives = 0;
  counters_.true_negatives = 0;
  counters_.false_positives = 0;
  counters_.false_negatives = 0;
  RemoveExpectations();
}

// Cost: O(1).
void ClippingPredictorEvaluator::Push(ExpectedDetection value) {
  ring_buffer_[ring_buffer_tail_] = value;
  ring_buffer_tail_++;
  if (ring_buffer_tail_ == ring_buffer_capacity_) {
    ring_buffer_tail_ = 0;
  }
  ring_buffer_size_ = std::min(ring_buffer_capacity_, ring_buffer_size_ + 1);
}

// Cost: O(N).
void ClippingPredictorEvaluator::DecreaseTimesToLive() {
  bool expired_found = false;
  for (int i = ring_buffer_tail_ - ring_buffer_size_; i < ring_buffer_tail_;
       ++i) {
    int index = i >= 0 ? i : ring_buffer_capacity_ + i;
    RTC_DCHECK_GE(index, 0);
    RTC_DCHECK_LT(index, ring_buffer_.size());
    RTC_DCHECK_GE(ring_buffer_[index].ttl, 0);
    if (ring_buffer_[index].ttl == 0) {
      RTC_DCHECK(!expired_found)
          << "There must be at most one expired item in the ring buffer.";
      expired_found = true;
      RTC_DCHECK_EQ(index, OldestExpectedDetectionIndex(ring_buffer_size_,
                                                        ring_buffer_tail_,
                                                        ring_buffer_capacity_))
          << "The expired item must be the oldest in the ring buffer.";
    }
    ring_buffer_[index].ttl--;
  }
  if (expired_found) {
    ring_buffer_size_--;
  }
}

// Cost: O(N).
absl::optional<int> ClippingPredictorEvaluator::FindEarliestPredictionInterval()
    const {
  absl::optional<int> prediction_interval;
  for (int i = ring_buffer_tail_ - ring_buffer_size_; i < ring_buffer_tail_;
       ++i) {
    int index = i >= 0 ? i : ring_buffer_capacity_ + i;
    RTC_DCHECK_GE(index, 0);
    RTC_DCHECK_LT(index, ring_buffer_.size());
    if (!ring_buffer_[index].detected) {
      prediction_interval = std::max(prediction_interval.value_or(0),
                                     history_size_ - ring_buffer_[index].ttl);
    }
  }
  return prediction_interval;
}

// Cost: O(N).
int ClippingPredictorEvaluator::MarkExpectedDetectionAsDetected() {
  int num_modified_items = 0;
  for (int i = ring_buffer_tail_ - ring_buffer_size_; i < ring_buffer_tail_;
       ++i) {
    int index = i >= 0 ? i : ring_buffer_capacity_ + i;
    RTC_DCHECK_GE(index, 0);
    RTC_DCHECK_LT(index, ring_buffer_.size());
    if (!ring_buffer_[index].detected) {
      num_modified_items++;
    }
    ring_buffer_[index].detected = true;
  }
  return num_modified_items;
}

// Cost: O(1).
bool ClippingPredictorEvaluator::HasExpiredUnmatchedExpectedDetection() const {
  if (ring_buffer_size_ == 0) {
    return false;
  }
  // If an expired item, that is `ttl` equal to 0, exists, it must be the
  // oldest.
  const int oldest_index = OldestExpectedDetectionIndex(
      ring_buffer_size_, ring_buffer_tail_, ring_buffer_capacity_);
  RTC_DCHECK_GE(oldest_index, 0);
  RTC_DCHECK_LT(oldest_index, ring_buffer_.size());
  return ring_buffer_[oldest_index].ttl == 0 &&
         !ring_buffer_[oldest_index].detected;
}

absl::optional<ClippingPredictionMetrics> ComputeClippingPredictionMetrics(
    const ClippingPredictionCounters& counters) {
  RTC_DCHECK_GE(counters.true_positives, 0);
  RTC_DCHECK_GE(counters.true_negatives, 0);
  RTC_DCHECK_GE(counters.false_positives, 0);
  RTC_DCHECK_GE(counters.false_negatives, 0);
  if (counters.true_positives == 0) {
    // Both precision and recall are zero in this case and hence the F1 score
    // is undefined.
    return absl::nullopt;
  }
  int precision_denominator =
      counters.true_positives + counters.false_positives;
  int recall_denominator = counters.true_positives + counters.false_negatives;
  if (precision_denominator == 0 || recall_denominator == 0) {
    // Both precision and recall must be defined.
    return absl::nullopt;
  }
  ClippingPredictionMetrics metrics;
  float true_positives = counters.true_positives;
  metrics.precision = true_positives / precision_denominator;
  metrics.recall = true_positives / recall_denominator;
  float f1_score_denominator = metrics.precision + metrics.recall;
  RTC_DCHECK_GT(f1_score_denominator, 0.0f);
  metrics.f1_score =
      2 * metrics.precision * metrics.recall / f1_score_denominator;
  return metrics;
}

}  // namespace webrtc
