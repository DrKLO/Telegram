/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/utility/delay_estimator.h"

#include <stdlib.h>
#include <string.h>

#include <algorithm>

#include "rtc_base/checks.h"

namespace webrtc {

namespace {

// Number of right shifts for scaling is linearly depending on number of bits in
// the far-end binary spectrum.
static const int kShiftsAtZero = 13;  // Right shifts at zero binary spectrum.
static const int kShiftsLinearSlope = 3;

static const int32_t kProbabilityOffset = 1024;      // 2 in Q9.
static const int32_t kProbabilityLowerLimit = 8704;  // 17 in Q9.
static const int32_t kProbabilityMinSpread = 2816;   // 5.5 in Q9.

// Robust validation settings
static const float kHistogramMax = 3000.f;
static const float kLastHistogramMax = 250.f;
static const float kMinHistogramThreshold = 1.5f;
static const int kMinRequiredHits = 10;
static const int kMaxHitsWhenPossiblyNonCausal = 10;
static const int kMaxHitsWhenPossiblyCausal = 1000;
static const float kQ14Scaling = 1.f / (1 << 14);  // Scaling by 2^14 to get Q0.
static const float kFractionSlope = 0.05f;
static const float kMinFractionWhenPossiblyCausal = 0.5f;
static const float kMinFractionWhenPossiblyNonCausal = 0.25f;

}  // namespace

// Counts and returns number of bits of a 32-bit word.
static int BitCount(uint32_t u32) {
  uint32_t tmp =
      u32 - ((u32 >> 1) & 033333333333) - ((u32 >> 2) & 011111111111);
  tmp = ((tmp + (tmp >> 3)) & 030707070707);
  tmp = (tmp + (tmp >> 6));
  tmp = (tmp + (tmp >> 12) + (tmp >> 24)) & 077;

  return ((int)tmp);
}

// Compares the |binary_vector| with all rows of the |binary_matrix| and counts
// per row the number of times they have the same value.
//
// Inputs:
//      - binary_vector     : binary "vector" stored in a long
//      - binary_matrix     : binary "matrix" stored as a vector of long
//      - matrix_size       : size of binary "matrix"
//
// Output:
//      - bit_counts        : "Vector" stored as a long, containing for each
//                            row the number of times the matrix row and the
//                            input vector have the same value
//
static void BitCountComparison(uint32_t binary_vector,
                               const uint32_t* binary_matrix,
                               int matrix_size,
                               int32_t* bit_counts) {
  int n = 0;

  // Compare |binary_vector| with all rows of the |binary_matrix|
  for (; n < matrix_size; n++) {
    bit_counts[n] = (int32_t)BitCount(binary_vector ^ binary_matrix[n]);
  }
}

// Collects necessary statistics for the HistogramBasedValidation().  This
// function has to be called prior to calling HistogramBasedValidation().  The
// statistics updated and used by the HistogramBasedValidation() are:
//  1. the number of |candidate_hits|, which states for how long we have had the
//     same |candidate_delay|
//  2. the |histogram| of candidate delays over time.  This histogram is
//     weighted with respect to a reliability measure and time-varying to cope
//     with possible delay shifts.
// For further description see commented code.
//
// Inputs:
//  - candidate_delay   : The delay to validate.
//  - valley_depth_q14  : The cost function has a valley/minimum at the
//                        |candidate_delay| location.  |valley_depth_q14| is the
//                        cost function difference between the minimum and
//                        maximum locations.  The value is in the Q14 domain.
//  - valley_level_q14  : Is the cost function value at the minimum, in Q14.
static void UpdateRobustValidationStatistics(BinaryDelayEstimator* self,
                                             int candidate_delay,
                                             int32_t valley_depth_q14,
                                             int32_t valley_level_q14) {
  const float valley_depth = valley_depth_q14 * kQ14Scaling;
  float decrease_in_last_set = valley_depth;
  const int max_hits_for_slow_change = (candidate_delay < self->last_delay)
                                           ? kMaxHitsWhenPossiblyNonCausal
                                           : kMaxHitsWhenPossiblyCausal;
  int i = 0;

  RTC_DCHECK_EQ(self->history_size, self->farend->history_size);
  // Reset |candidate_hits| if we have a new candidate.
  if (candidate_delay != self->last_candidate_delay) {
    self->candidate_hits = 0;
    self->last_candidate_delay = candidate_delay;
  }
  self->candidate_hits++;

  // The |histogram| is updated differently across the bins.
  // 1. The |candidate_delay| histogram bin is increased with the
  //    |valley_depth|, which is a simple measure of how reliable the
  //    |candidate_delay| is.  The histogram is not increased above
  //    |kHistogramMax|.
  self->histogram[candidate_delay] += valley_depth;
  if (self->histogram[candidate_delay] > kHistogramMax) {
    self->histogram[candidate_delay] = kHistogramMax;
  }
  // 2. The histogram bins in the neighborhood of |candidate_delay| are
  //    unaffected.  The neighborhood is defined as x + {-2, -1, 0, 1}.
  // 3. The histogram bins in the neighborhood of |last_delay| are decreased
  //    with |decrease_in_last_set|.  This value equals the difference between
  //    the cost function values at the locations |candidate_delay| and
  //    |last_delay| until we reach |max_hits_for_slow_change| consecutive hits
  //    at the |candidate_delay|.  If we exceed this amount of hits the
  //    |candidate_delay| is a "potential" candidate and we start decreasing
  //    these histogram bins more rapidly with |valley_depth|.
  if (self->candidate_hits < max_hits_for_slow_change) {
    decrease_in_last_set =
        (self->mean_bit_counts[self->compare_delay] - valley_level_q14) *
        kQ14Scaling;
  }
  // 4. All other bins are decreased with |valley_depth|.
  // TODO(bjornv): Investigate how to make this loop more efficient.  Split up
  // the loop?  Remove parts that doesn't add too much.
  for (i = 0; i < self->history_size; ++i) {
    int is_in_last_set = (i >= self->last_delay - 2) &&
                         (i <= self->last_delay + 1) && (i != candidate_delay);
    int is_in_candidate_set =
        (i >= candidate_delay - 2) && (i <= candidate_delay + 1);
    self->histogram[i] -=
        decrease_in_last_set * is_in_last_set +
        valley_depth * (!is_in_last_set && !is_in_candidate_set);
    // 5. No histogram bin can go below 0.
    if (self->histogram[i] < 0) {
      self->histogram[i] = 0;
    }
  }
}

// Validates the |candidate_delay|, estimated in WebRtc_ProcessBinarySpectrum(),
// based on a mix of counting concurring hits with a modified histogram
// of recent delay estimates.  In brief a candidate is valid (returns 1) if it
// is the most likely according to the histogram.  There are a couple of
// exceptions that are worth mentioning:
//  1. If the |candidate_delay| < |last_delay| it can be that we are in a
//     non-causal state, breaking a possible echo control algorithm.  Hence, we
//     open up for a quicker change by allowing the change even if the
//     |candidate_delay| is not the most likely one according to the histogram.
//  2. There's a minimum number of hits (kMinRequiredHits) and the histogram
//     value has to reached a minimum (kMinHistogramThreshold) to be valid.
//  3. The action is also depending on the filter length used for echo control.
//     If the delay difference is larger than what the filter can capture, we
//     also move quicker towards a change.
// For further description see commented code.
//
// Input:
//  - candidate_delay     : The delay to validate.
//
// Return value:
//  - is_histogram_valid  : 1 - The |candidate_delay| is valid.
//                          0 - Otherwise.
static int HistogramBasedValidation(const BinaryDelayEstimator* self,
                                    int candidate_delay) {
  float fraction = 1.f;
  float histogram_threshold = self->histogram[self->compare_delay];
  const int delay_difference = candidate_delay - self->last_delay;
  int is_histogram_valid = 0;

  // The histogram based validation of |candidate_delay| is done by comparing
  // the |histogram| at bin |candidate_delay| with a |histogram_threshold|.
  // This |histogram_threshold| equals a |fraction| of the |histogram| at bin
  // |last_delay|.  The |fraction| is a piecewise linear function of the
  // |delay_difference| between the |candidate_delay| and the |last_delay|
  // allowing for a quicker move if
  //  i) a potential echo control filter can not handle these large differences.
  // ii) keeping |last_delay| instead of updating to |candidate_delay| could
  //     force an echo control into a non-causal state.
  // We further require the histogram to have reached a minimum value of
  // |kMinHistogramThreshold|.  In addition, we also require the number of
  // |candidate_hits| to be more than |kMinRequiredHits| to remove spurious
  // values.

  // Calculate a comparison histogram value (|histogram_threshold|) that is
  // depending on the distance between the |candidate_delay| and |last_delay|.
  // TODO(bjornv): How much can we gain by turning the fraction calculation
  // into tables?
  if (delay_difference > self->allowed_offset) {
    fraction = 1.f - kFractionSlope * (delay_difference - self->allowed_offset);
    fraction = (fraction > kMinFractionWhenPossiblyCausal
                    ? fraction
                    : kMinFractionWhenPossiblyCausal);
  } else if (delay_difference < 0) {
    fraction =
        kMinFractionWhenPossiblyNonCausal - kFractionSlope * delay_difference;
    fraction = (fraction > 1.f ? 1.f : fraction);
  }
  histogram_threshold *= fraction;
  histogram_threshold =
      (histogram_threshold > kMinHistogramThreshold ? histogram_threshold
                                                    : kMinHistogramThreshold);

  is_histogram_valid =
      (self->histogram[candidate_delay] >= histogram_threshold) &&
      (self->candidate_hits > kMinRequiredHits);

  return is_histogram_valid;
}

// Performs a robust validation of the |candidate_delay| estimated in
// WebRtc_ProcessBinarySpectrum().  The algorithm takes the
// |is_instantaneous_valid| and the |is_histogram_valid| and combines them
// into a robust validation.  The HistogramBasedValidation() has to be called
// prior to this call.
// For further description on how the combination is done, see commented code.
//
// Inputs:
//  - candidate_delay         : The delay to validate.
//  - is_instantaneous_valid  : The instantaneous validation performed in
//                              WebRtc_ProcessBinarySpectrum().
//  - is_histogram_valid      : The histogram based validation.
//
// Return value:
//  - is_robust               : 1 - The candidate_delay is valid according to a
//                                  combination of the two inputs.
//                            : 0 - Otherwise.
static int RobustValidation(const BinaryDelayEstimator* self,
                            int candidate_delay,
                            int is_instantaneous_valid,
                            int is_histogram_valid) {
  int is_robust = 0;

  // The final robust validation is based on the two algorithms; 1) the
  // |is_instantaneous_valid| and 2) the histogram based with result stored in
  // |is_histogram_valid|.
  //   i) Before we actually have a valid estimate (|last_delay| == -2), we say
  //      a candidate is valid if either algorithm states so
  //      (|is_instantaneous_valid| OR |is_histogram_valid|).
  is_robust =
      (self->last_delay < 0) && (is_instantaneous_valid || is_histogram_valid);
  //  ii) Otherwise, we need both algorithms to be certain
  //      (|is_instantaneous_valid| AND |is_histogram_valid|)
  is_robust |= is_instantaneous_valid && is_histogram_valid;
  // iii) With one exception, i.e., the histogram based algorithm can overrule
  //      the instantaneous one if |is_histogram_valid| = 1 and the histogram
  //      is significantly strong.
  is_robust |= is_histogram_valid &&
               (self->histogram[candidate_delay] > self->last_delay_histogram);

  return is_robust;
}

void WebRtc_FreeBinaryDelayEstimatorFarend(BinaryDelayEstimatorFarend* self) {
  if (self == NULL) {
    return;
  }

  free(self->binary_far_history);
  self->binary_far_history = NULL;

  free(self->far_bit_counts);
  self->far_bit_counts = NULL;

  free(self);
}

BinaryDelayEstimatorFarend* WebRtc_CreateBinaryDelayEstimatorFarend(
    int history_size) {
  BinaryDelayEstimatorFarend* self = NULL;

  if (history_size > 1) {
    // Sanity conditions fulfilled.
    self = static_cast<BinaryDelayEstimatorFarend*>(
        malloc(sizeof(BinaryDelayEstimatorFarend)));
  }
  if (self == NULL) {
    return NULL;
  }

  self->history_size = 0;
  self->binary_far_history = NULL;
  self->far_bit_counts = NULL;
  if (WebRtc_AllocateFarendBufferMemory(self, history_size) == 0) {
    WebRtc_FreeBinaryDelayEstimatorFarend(self);
    self = NULL;
  }
  return self;
}

int WebRtc_AllocateFarendBufferMemory(BinaryDelayEstimatorFarend* self,
                                      int history_size) {
  RTC_DCHECK(self);
  // (Re-)Allocate memory for history buffers.
  self->binary_far_history = static_cast<uint32_t*>(
      realloc(self->binary_far_history,
              history_size * sizeof(*self->binary_far_history)));
  self->far_bit_counts = static_cast<int*>(realloc(
      self->far_bit_counts, history_size * sizeof(*self->far_bit_counts)));
  if ((self->binary_far_history == NULL) || (self->far_bit_counts == NULL)) {
    history_size = 0;
  }
  // Fill with zeros if we have expanded the buffers.
  if (history_size > self->history_size) {
    int size_diff = history_size - self->history_size;
    memset(&self->binary_far_history[self->history_size], 0,
           sizeof(*self->binary_far_history) * size_diff);
    memset(&self->far_bit_counts[self->history_size], 0,
           sizeof(*self->far_bit_counts) * size_diff);
  }
  self->history_size = history_size;

  return self->history_size;
}

void WebRtc_InitBinaryDelayEstimatorFarend(BinaryDelayEstimatorFarend* self) {
  RTC_DCHECK(self);
  memset(self->binary_far_history, 0, sizeof(uint32_t) * self->history_size);
  memset(self->far_bit_counts, 0, sizeof(int) * self->history_size);
}

void WebRtc_SoftResetBinaryDelayEstimatorFarend(
    BinaryDelayEstimatorFarend* self,
    int delay_shift) {
  int abs_shift = abs(delay_shift);
  int shift_size = 0;
  int dest_index = 0;
  int src_index = 0;
  int padding_index = 0;

  RTC_DCHECK(self);
  shift_size = self->history_size - abs_shift;
  RTC_DCHECK_GT(shift_size, 0);
  if (delay_shift == 0) {
    return;
  } else if (delay_shift > 0) {
    dest_index = abs_shift;
  } else if (delay_shift < 0) {
    src_index = abs_shift;
    padding_index = shift_size;
  }

  // Shift and zero pad buffers.
  memmove(&self->binary_far_history[dest_index],
          &self->binary_far_history[src_index],
          sizeof(*self->binary_far_history) * shift_size);
  memset(&self->binary_far_history[padding_index], 0,
         sizeof(*self->binary_far_history) * abs_shift);
  memmove(&self->far_bit_counts[dest_index], &self->far_bit_counts[src_index],
          sizeof(*self->far_bit_counts) * shift_size);
  memset(&self->far_bit_counts[padding_index], 0,
         sizeof(*self->far_bit_counts) * abs_shift);
}

void WebRtc_AddBinaryFarSpectrum(BinaryDelayEstimatorFarend* handle,
                                 uint32_t binary_far_spectrum) {
  RTC_DCHECK(handle);
  // Shift binary spectrum history and insert current |binary_far_spectrum|.
  memmove(&(handle->binary_far_history[1]), &(handle->binary_far_history[0]),
          (handle->history_size - 1) * sizeof(uint32_t));
  handle->binary_far_history[0] = binary_far_spectrum;

  // Shift history of far-end binary spectrum bit counts and insert bit count
  // of current |binary_far_spectrum|.
  memmove(&(handle->far_bit_counts[1]), &(handle->far_bit_counts[0]),
          (handle->history_size - 1) * sizeof(int));
  handle->far_bit_counts[0] = BitCount(binary_far_spectrum);
}

void WebRtc_FreeBinaryDelayEstimator(BinaryDelayEstimator* self) {
  if (self == NULL) {
    return;
  }

  free(self->mean_bit_counts);
  self->mean_bit_counts = NULL;

  free(self->bit_counts);
  self->bit_counts = NULL;

  free(self->binary_near_history);
  self->binary_near_history = NULL;

  free(self->histogram);
  self->histogram = NULL;

  // BinaryDelayEstimator does not have ownership of |farend|, hence we do not
  // free the memory here. That should be handled separately by the user.
  self->farend = NULL;

  free(self);
}

BinaryDelayEstimator* WebRtc_CreateBinaryDelayEstimator(
    BinaryDelayEstimatorFarend* farend,
    int max_lookahead) {
  BinaryDelayEstimator* self = NULL;

  if ((farend != NULL) && (max_lookahead >= 0)) {
    // Sanity conditions fulfilled.
    self = static_cast<BinaryDelayEstimator*>(
        malloc(sizeof(BinaryDelayEstimator)));
  }
  if (self == NULL) {
    return NULL;
  }

  self->farend = farend;
  self->near_history_size = max_lookahead + 1;
  self->history_size = 0;
  self->robust_validation_enabled = 0;  // Disabled by default.
  self->allowed_offset = 0;

  self->lookahead = max_lookahead;

  // Allocate memory for spectrum and history buffers.
  self->mean_bit_counts = NULL;
  self->bit_counts = NULL;
  self->histogram = NULL;
  self->binary_near_history = static_cast<uint32_t*>(
      malloc((max_lookahead + 1) * sizeof(*self->binary_near_history)));
  if (self->binary_near_history == NULL ||
      WebRtc_AllocateHistoryBufferMemory(self, farend->history_size) == 0) {
    WebRtc_FreeBinaryDelayEstimator(self);
    self = NULL;
  }

  return self;
}

int WebRtc_AllocateHistoryBufferMemory(BinaryDelayEstimator* self,
                                       int history_size) {
  BinaryDelayEstimatorFarend* far = self->farend;
  // (Re-)Allocate memory for spectrum and history buffers.
  if (history_size != far->history_size) {
    // Only update far-end buffers if we need.
    history_size = WebRtc_AllocateFarendBufferMemory(far, history_size);
  }
  // The extra array element in |mean_bit_counts| and |histogram| is a dummy
  // element only used while |last_delay| == -2, i.e., before we have a valid
  // estimate.
  self->mean_bit_counts = static_cast<int32_t*>(
      realloc(self->mean_bit_counts,
              (history_size + 1) * sizeof(*self->mean_bit_counts)));
  self->bit_counts = static_cast<int32_t*>(
      realloc(self->bit_counts, history_size * sizeof(*self->bit_counts)));
  self->histogram = static_cast<float*>(
      realloc(self->histogram, (history_size + 1) * sizeof(*self->histogram)));

  if ((self->mean_bit_counts == NULL) || (self->bit_counts == NULL) ||
      (self->histogram == NULL)) {
    history_size = 0;
  }
  // Fill with zeros if we have expanded the buffers.
  if (history_size > self->history_size) {
    int size_diff = history_size - self->history_size;
    memset(&self->mean_bit_counts[self->history_size], 0,
           sizeof(*self->mean_bit_counts) * size_diff);
    memset(&self->bit_counts[self->history_size], 0,
           sizeof(*self->bit_counts) * size_diff);
    memset(&self->histogram[self->history_size], 0,
           sizeof(*self->histogram) * size_diff);
  }
  self->history_size = history_size;

  return self->history_size;
}

void WebRtc_InitBinaryDelayEstimator(BinaryDelayEstimator* self) {
  int i = 0;
  RTC_DCHECK(self);

  memset(self->bit_counts, 0, sizeof(int32_t) * self->history_size);
  memset(self->binary_near_history, 0,
         sizeof(uint32_t) * self->near_history_size);
  for (i = 0; i <= self->history_size; ++i) {
    self->mean_bit_counts[i] = (20 << 9);  // 20 in Q9.
    self->histogram[i] = 0.f;
  }
  self->minimum_probability = kMaxBitCountsQ9;          // 32 in Q9.
  self->last_delay_probability = (int)kMaxBitCountsQ9;  // 32 in Q9.

  // Default return value if we're unable to estimate. -1 is used for errors.
  self->last_delay = -2;

  self->last_candidate_delay = -2;
  self->compare_delay = self->history_size;
  self->candidate_hits = 0;
  self->last_delay_histogram = 0.f;
}

int WebRtc_SoftResetBinaryDelayEstimator(BinaryDelayEstimator* self,
                                         int delay_shift) {
  int lookahead = 0;
  RTC_DCHECK(self);
  lookahead = self->lookahead;
  self->lookahead -= delay_shift;
  if (self->lookahead < 0) {
    self->lookahead = 0;
  }
  if (self->lookahead > self->near_history_size - 1) {
    self->lookahead = self->near_history_size - 1;
  }
  return lookahead - self->lookahead;
}

int WebRtc_ProcessBinarySpectrum(BinaryDelayEstimator* self,
                                 uint32_t binary_near_spectrum) {
  int i = 0;
  int candidate_delay = -1;
  int valid_candidate = 0;

  int32_t value_best_candidate = kMaxBitCountsQ9;
  int32_t value_worst_candidate = 0;
  int32_t valley_depth = 0;

  RTC_DCHECK(self);
  if (self->farend->history_size != self->history_size) {
    // Non matching history sizes.
    return -1;
  }
  if (self->near_history_size > 1) {
    // If we apply lookahead, shift near-end binary spectrum history. Insert
    // current |binary_near_spectrum| and pull out the delayed one.
    memmove(&(self->binary_near_history[1]), &(self->binary_near_history[0]),
            (self->near_history_size - 1) * sizeof(uint32_t));
    self->binary_near_history[0] = binary_near_spectrum;
    binary_near_spectrum = self->binary_near_history[self->lookahead];
  }

  // Compare with delayed spectra and store the |bit_counts| for each delay.
  BitCountComparison(binary_near_spectrum, self->farend->binary_far_history,
                     self->history_size, self->bit_counts);

  // Update |mean_bit_counts|, which is the smoothed version of |bit_counts|.
  for (i = 0; i < self->history_size; i++) {
    // |bit_counts| is constrained to [0, 32], meaning we can smooth with a
    // factor up to 2^26. We use Q9.
    int32_t bit_count = (self->bit_counts[i] << 9);  // Q9.

    // Update |mean_bit_counts| only when far-end signal has something to
    // contribute. If |far_bit_counts| is zero the far-end signal is weak and
    // we likely have a poor echo condition, hence don't update.
    if (self->farend->far_bit_counts[i] > 0) {
      // Make number of right shifts piecewise linear w.r.t. |far_bit_counts|.
      int shifts = kShiftsAtZero;
      shifts -= (kShiftsLinearSlope * self->farend->far_bit_counts[i]) >> 4;
      WebRtc_MeanEstimatorFix(bit_count, shifts, &(self->mean_bit_counts[i]));
    }
  }

  // Find |candidate_delay|, |value_best_candidate| and |value_worst_candidate|
  // of |mean_bit_counts|.
  for (i = 0; i < self->history_size; i++) {
    if (self->mean_bit_counts[i] < value_best_candidate) {
      value_best_candidate = self->mean_bit_counts[i];
      candidate_delay = i;
    }
    if (self->mean_bit_counts[i] > value_worst_candidate) {
      value_worst_candidate = self->mean_bit_counts[i];
    }
  }
  valley_depth = value_worst_candidate - value_best_candidate;

  // The |value_best_candidate| is a good indicator on the probability of
  // |candidate_delay| being an accurate delay (a small |value_best_candidate|
  // means a good binary match). In the following sections we make a decision
  // whether to update |last_delay| or not.
  // 1) If the difference bit counts between the best and the worst delay
  //    candidates is too small we consider the situation to be unreliable and
  //    don't update |last_delay|.
  // 2) If the situation is reliable we update |last_delay| if the value of the
  //    best candidate delay has a value less than
  //     i) an adaptive threshold |minimum_probability|, or
  //    ii) this corresponding value |last_delay_probability|, but updated at
  //        this time instant.

  // Update |minimum_probability|.
  if ((self->minimum_probability > kProbabilityLowerLimit) &&
      (valley_depth > kProbabilityMinSpread)) {
    // The "hard" threshold can't be lower than 17 (in Q9).
    // The valley in the curve also has to be distinct, i.e., the
    // difference between |value_worst_candidate| and |value_best_candidate| has
    // to be large enough.
    int32_t threshold = value_best_candidate + kProbabilityOffset;
    if (threshold < kProbabilityLowerLimit) {
      threshold = kProbabilityLowerLimit;
    }
    if (self->minimum_probability > threshold) {
      self->minimum_probability = threshold;
    }
  }
  // Update |last_delay_probability|.
  // We use a Markov type model, i.e., a slowly increasing level over time.
  self->last_delay_probability++;
  // Validate |candidate_delay|.  We have a reliable instantaneous delay
  // estimate if
  //  1) The valley is distinct enough (|valley_depth| > |kProbabilityOffset|)
  // and
  //  2) The depth of the valley is deep enough
  //      (|value_best_candidate| < |minimum_probability|)
  //     and deeper than the best estimate so far
  //      (|value_best_candidate| < |last_delay_probability|)
  valid_candidate = ((valley_depth > kProbabilityOffset) &&
                     ((value_best_candidate < self->minimum_probability) ||
                      (value_best_candidate < self->last_delay_probability)));

  // Check for nonstationary farend signal.
  const bool non_stationary_farend =
      std::any_of(self->farend->far_bit_counts,
                  self->farend->far_bit_counts + self->history_size,
                  [](int a) { return a > 0; });

  if (non_stationary_farend) {
    // Only update the validation statistics when the farend is nonstationary
    // as the underlying estimates are otherwise frozen.
    UpdateRobustValidationStatistics(self, candidate_delay, valley_depth,
                                     value_best_candidate);
  }

  if (self->robust_validation_enabled) {
    int is_histogram_valid = HistogramBasedValidation(self, candidate_delay);
    valid_candidate = RobustValidation(self, candidate_delay, valid_candidate,
                                       is_histogram_valid);
  }

  // Only update the delay estimate when the farend is nonstationary and when
  // a valid delay candidate is available.
  if (non_stationary_farend && valid_candidate) {
    if (candidate_delay != self->last_delay) {
      self->last_delay_histogram =
          (self->histogram[candidate_delay] > kLastHistogramMax
               ? kLastHistogramMax
               : self->histogram[candidate_delay]);
      // Adjust the histogram if we made a change to |last_delay|, though it was
      // not the most likely one according to the histogram.
      if (self->histogram[candidate_delay] <
          self->histogram[self->compare_delay]) {
        self->histogram[self->compare_delay] = self->histogram[candidate_delay];
      }
    }
    self->last_delay = candidate_delay;
    if (value_best_candidate < self->last_delay_probability) {
      self->last_delay_probability = value_best_candidate;
    }
    self->compare_delay = self->last_delay;
  }

  return self->last_delay;
}

int WebRtc_binary_last_delay(BinaryDelayEstimator* self) {
  RTC_DCHECK(self);
  return self->last_delay;
}

float WebRtc_binary_last_delay_quality(BinaryDelayEstimator* self) {
  float quality = 0;
  RTC_DCHECK(self);

  if (self->robust_validation_enabled) {
    // Simply a linear function of the histogram height at delay estimate.
    quality = self->histogram[self->compare_delay] / kHistogramMax;
  } else {
    // Note that |last_delay_probability| states how deep the minimum of the
    // cost function is, so it is rather an error probability.
    quality = (float)(kMaxBitCountsQ9 - self->last_delay_probability) /
              kMaxBitCountsQ9;
    if (quality < 0) {
      quality = 0;
    }
  }
  return quality;
}

void WebRtc_MeanEstimatorFix(int32_t new_value,
                             int factor,
                             int32_t* mean_value) {
  int32_t diff = new_value - *mean_value;

  // mean_new = mean_value + ((new_value - mean_value) >> factor);
  if (diff < 0) {
    diff = -((-diff) >> factor);
  } else {
    diff = (diff >> factor);
  }
  *mean_value += diff;
}

}  // namespace webrtc
