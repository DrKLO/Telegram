/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/rms_level.h"

#include <algorithm>
#include <cmath>
#include <numeric>

#include "rtc_base/checks.h"

namespace webrtc {
namespace {
static constexpr float kMaxSquaredLevel = 32768 * 32768;
// kMinLevel is the level corresponding to kMinLevelDb, that is 10^(-127/10).
static constexpr float kMinLevel = 1.995262314968883e-13f;

// Calculates the normalized RMS value from a mean square value. The input
// should be the sum of squared samples divided by the number of samples. The
// value will be normalized to full range before computing the RMS, wich is
// returned as a negated dBfs. That is, 0 is full amplitude while 127 is very
// faint.
int ComputeRms(float mean_square) {
  if (mean_square <= kMinLevel * kMaxSquaredLevel) {
    // Very faint; simply return the minimum value.
    return RmsLevel::kMinLevelDb;
  }
  // Normalize by the max level.
  const float mean_square_norm = mean_square / kMaxSquaredLevel;
  RTC_DCHECK_GT(mean_square_norm, kMinLevel);
  // 20log_10(x^0.5) = 10log_10(x)
  const float rms = 10.f * std::log10(mean_square_norm);
  RTC_DCHECK_LE(rms, 0.f);
  RTC_DCHECK_GT(rms, -RmsLevel::kMinLevelDb);
  // Return the negated value.
  return static_cast<int>(-rms + 0.5f);
}
}  // namespace

RmsLevel::RmsLevel() {
  Reset();
}

RmsLevel::~RmsLevel() = default;

void RmsLevel::Reset() {
  sum_square_ = 0.f;
  sample_count_ = 0;
  max_sum_square_ = 0.f;
  block_size_ = absl::nullopt;
}

void RmsLevel::Analyze(rtc::ArrayView<const int16_t> data) {
  if (data.empty()) {
    return;
  }

  CheckBlockSize(data.size());

  const float sum_square =
      std::accumulate(data.begin(), data.end(), 0.f,
                      [](float a, int16_t b) { return a + b * b; });
  RTC_DCHECK_GE(sum_square, 0.f);
  sum_square_ += sum_square;
  sample_count_ += data.size();

  max_sum_square_ = std::max(max_sum_square_, sum_square);
}

void RmsLevel::Analyze(rtc::ArrayView<const float> data) {
  if (data.empty()) {
    return;
  }

  CheckBlockSize(data.size());

  float sum_square = 0.f;

  for (float data_k : data) {
    int16_t tmp =
        static_cast<int16_t>(std::min(std::max(data_k, -32768.f), 32767.f));
    sum_square += tmp * tmp;
  }
  RTC_DCHECK_GE(sum_square, 0.f);
  sum_square_ += sum_square;
  sample_count_ += data.size();

  max_sum_square_ = std::max(max_sum_square_, sum_square);
}

void RmsLevel::AnalyzeMuted(size_t length) {
  CheckBlockSize(length);
  sample_count_ += length;
}

int RmsLevel::Average() {
  int rms = (sample_count_ == 0) ? RmsLevel::kMinLevelDb
                                 : ComputeRms(sum_square_ / sample_count_);
  Reset();
  return rms;
}

RmsLevel::Levels RmsLevel::AverageAndPeak() {
  // Note that block_size_ should by design always be non-empty when
  // sample_count_ != 0. Also, the * operator of absl::optional enforces this
  // with a DCHECK.
  Levels levels = (sample_count_ == 0)
                      ? Levels{RmsLevel::kMinLevelDb, RmsLevel::kMinLevelDb}
                      : Levels{ComputeRms(sum_square_ / sample_count_),
                               ComputeRms(max_sum_square_ / *block_size_)};
  Reset();
  return levels;
}

void RmsLevel::CheckBlockSize(size_t block_size) {
  if (block_size_ != block_size) {
    Reset();
    block_size_ = block_size;
  }
}
}  // namespace webrtc
