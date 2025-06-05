/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc/loudness_histogram.h"

#include <string.h>

#include <cmath>

#include "rtc_base/checks.h"

namespace webrtc {

static const double kHistBinCenters[] = {
    7.59621091765857e-02, 9.02036021061016e-02, 1.07115112009343e-01,
    1.27197217770508e-01, 1.51044347572047e-01, 1.79362373905283e-01,
    2.12989507320644e-01, 2.52921107370304e-01, 3.00339145144454e-01,
    3.56647189489147e-01, 4.23511952494003e-01, 5.02912623991786e-01,
    5.97199455365749e-01, 7.09163326739184e-01, 8.42118356728544e-01,
    1.00000000000000e+00, 1.18748153630660e+00, 1.41011239906908e+00,
    1.67448243801153e+00, 1.98841697800836e+00, 2.36120844786349e+00,
    2.80389143520905e+00, 3.32956930911896e+00, 3.95380207843188e+00,
    4.69506696634852e+00, 5.57530533426190e+00, 6.62057214370769e+00,
    7.86180718043869e+00, 9.33575086877358e+00, 1.10860317842269e+01,
    1.31644580546776e+01, 1.56325508754123e+01, 1.85633655299256e+01,
    2.20436538184971e+01, 2.61764319021997e+01, 3.10840295702492e+01,
    3.69117111886792e+01, 4.38319755100383e+01, 5.20496616180135e+01,
    6.18080121423973e+01, 7.33958732149108e+01, 8.71562442838066e+01,
    1.03496430860848e+02, 1.22900100720889e+02, 1.45941600416277e+02,
    1.73302955873365e+02, 2.05794060286978e+02, 2.44376646872353e+02,
    2.90192756065437e+02, 3.44598539797631e+02, 4.09204403447902e+02,
    4.85922673669740e+02, 5.77024203055553e+02, 6.85205587130498e+02,
    8.13668983291589e+02, 9.66216894324125e+02, 1.14736472207740e+03,
    1.36247442287647e+03, 1.61791322085579e+03, 1.92124207711260e+03,
    2.28143949334655e+03, 2.70916727454970e+03, 3.21708611729384e+03,
    3.82023036499473e+03, 4.53645302286906e+03, 5.38695420497926e+03,
    6.39690865534207e+03, 7.59621091765857e+03, 9.02036021061016e+03,
    1.07115112009343e+04, 1.27197217770508e+04, 1.51044347572047e+04,
    1.79362373905283e+04, 2.12989507320644e+04, 2.52921107370304e+04,
    3.00339145144454e+04, 3.56647189489147e+04};

static const double kProbQDomain = 1024.0;
// Loudness of -15 dB (smallest expected loudness) in log domain,
// loudness_db = 13.5 * log10(rms);
static const double kLogDomainMinBinCenter = -2.57752062648587;
// Loudness step of 1 dB in log domain
static const double kLogDomainStepSizeInverse = 5.81954605750359;

static const int kTransientWidthThreshold = 7;
static const double kLowProbabilityThreshold = 0.2;

static const int kLowProbThresholdQ10 =
    static_cast<int>(kLowProbabilityThreshold * kProbQDomain);

LoudnessHistogram::LoudnessHistogram()
    : num_updates_(0),
      audio_content_q10_(0),
      bin_count_q10_(),
      activity_probability_(),
      hist_bin_index_(),
      buffer_index_(0),
      buffer_is_full_(false),
      len_circular_buffer_(0),
      len_high_activity_(0) {
  static_assert(
      kHistSize == sizeof(kHistBinCenters) / sizeof(kHistBinCenters[0]),
      "histogram bin centers incorrect size");
}

LoudnessHistogram::LoudnessHistogram(int window_size)
    : num_updates_(0),
      audio_content_q10_(0),
      bin_count_q10_(),
      activity_probability_(new int[window_size]),
      hist_bin_index_(new int[window_size]),
      buffer_index_(0),
      buffer_is_full_(false),
      len_circular_buffer_(window_size),
      len_high_activity_(0) {}

LoudnessHistogram::~LoudnessHistogram() {}

void LoudnessHistogram::Update(double rms, double activity_probaility) {
  // If circular histogram is activated then remove the oldest entry.
  if (len_circular_buffer_ > 0)
    RemoveOldestEntryAndUpdate();

  // Find the corresponding bin.
  int hist_index = GetBinIndex(rms);
  // To Q10 domain.
  int prob_q10 =
      static_cast<int16_t>(floor(activity_probaility * kProbQDomain));
  InsertNewestEntryAndUpdate(prob_q10, hist_index);
}

// Doing nothing if buffer is not full, yet.
void LoudnessHistogram::RemoveOldestEntryAndUpdate() {
  RTC_DCHECK_GT(len_circular_buffer_, 0);
  // Do nothing if circular buffer is not full.
  if (!buffer_is_full_)
    return;

  int oldest_prob = activity_probability_[buffer_index_];
  int oldest_hist_index = hist_bin_index_[buffer_index_];
  UpdateHist(-oldest_prob, oldest_hist_index);
}

void LoudnessHistogram::RemoveTransient() {
  // Don't expect to be here if high-activity region is longer than
  // `kTransientWidthThreshold` or there has not been any transient.
  RTC_DCHECK_LE(len_high_activity_, kTransientWidthThreshold);
  int index =
      (buffer_index_ > 0) ? (buffer_index_ - 1) : len_circular_buffer_ - 1;
  while (len_high_activity_ > 0) {
    UpdateHist(-activity_probability_[index], hist_bin_index_[index]);
    activity_probability_[index] = 0;
    index = (index > 0) ? (index - 1) : (len_circular_buffer_ - 1);
    len_high_activity_--;
  }
}

void LoudnessHistogram::InsertNewestEntryAndUpdate(int activity_prob_q10,
                                                   int hist_index) {
  // Update the circular buffer if it is enabled.
  if (len_circular_buffer_ > 0) {
    // Removing transient.
    if (activity_prob_q10 <= kLowProbThresholdQ10) {
      // Lower than threshold probability, set it to zero.
      activity_prob_q10 = 0;
      // Check if this has been a transient.
      if (len_high_activity_ <= kTransientWidthThreshold)
        RemoveTransient();  // Remove this transient.
      len_high_activity_ = 0;
    } else if (len_high_activity_ <= kTransientWidthThreshold) {
      len_high_activity_++;
    }
    // Updating the circular buffer.
    activity_probability_[buffer_index_] = activity_prob_q10;
    hist_bin_index_[buffer_index_] = hist_index;
    // Increment the buffer index and check for wrap-around.
    buffer_index_++;
    if (buffer_index_ >= len_circular_buffer_) {
      buffer_index_ = 0;
      buffer_is_full_ = true;
    }
  }

  num_updates_++;
  if (num_updates_ < 0)
    num_updates_--;

  UpdateHist(activity_prob_q10, hist_index);
}

void LoudnessHistogram::UpdateHist(int activity_prob_q10, int hist_index) {
  bin_count_q10_[hist_index] += activity_prob_q10;
  audio_content_q10_ += activity_prob_q10;
}

double LoudnessHistogram::AudioContent() const {
  return audio_content_q10_ / kProbQDomain;
}

LoudnessHistogram* LoudnessHistogram::Create() {
  return new LoudnessHistogram;
}

LoudnessHistogram* LoudnessHistogram::Create(int window_size) {
  if (window_size < 0)
    return NULL;
  return new LoudnessHistogram(window_size);
}

void LoudnessHistogram::Reset() {
  // Reset the histogram, audio-content and number of updates.
  memset(bin_count_q10_, 0, sizeof(bin_count_q10_));
  audio_content_q10_ = 0;
  num_updates_ = 0;
  // Empty the circular buffer.
  buffer_index_ = 0;
  buffer_is_full_ = false;
  len_high_activity_ = 0;
}

int LoudnessHistogram::GetBinIndex(double rms) {
  // First exclude overload cases.
  if (rms <= kHistBinCenters[0]) {
    return 0;
  } else if (rms >= kHistBinCenters[kHistSize - 1]) {
    return kHistSize - 1;
  } else {
    // The quantizer is uniform in log domain. Alternatively we could do binary
    // search in linear domain.
    double rms_log = log(rms);

    int index = static_cast<int>(
        floor((rms_log - kLogDomainMinBinCenter) * kLogDomainStepSizeInverse));
    // The final decision is in linear domain.
    double b = 0.5 * (kHistBinCenters[index] + kHistBinCenters[index + 1]);
    if (rms > b) {
      return index + 1;
    }
    return index;
  }
}

double LoudnessHistogram::CurrentRms() const {
  double p;
  double mean_val = 0;
  if (audio_content_q10_ > 0) {
    double p_total_inverse = 1. / static_cast<double>(audio_content_q10_);
    for (int n = 0; n < kHistSize; n++) {
      p = static_cast<double>(bin_count_q10_[n]) * p_total_inverse;
      mean_val += p * kHistBinCenters[n];
    }
  } else {
    mean_val = kHistBinCenters[0];
  }
  return mean_val;
}

}  // namespace webrtc
