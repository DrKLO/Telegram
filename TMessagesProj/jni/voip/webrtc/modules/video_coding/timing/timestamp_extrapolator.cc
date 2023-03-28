/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/timing/timestamp_extrapolator.h"

#include <algorithm>

#include "absl/types/optional.h"
#include "modules/include/module_common_types_public.h"

namespace webrtc {

namespace {

constexpr double kLambda = 1;
constexpr uint32_t kStartUpFilterDelayInPackets = 2;
constexpr double kAlarmThreshold = 60e3;
// in timestamp ticks, i.e. 15 ms
constexpr double kAccDrift = 6600;
constexpr double kAccMaxError = 7000;
constexpr double kP11 = 1e10;

}  // namespace

TimestampExtrapolator::TimestampExtrapolator(Timestamp start)
    : start_(Timestamp::Zero()),
      prev_(Timestamp::Zero()),
      packet_count_(0),
      detector_accumulator_pos_(0),
      detector_accumulator_neg_(0) {
  Reset(start);
}

void TimestampExtrapolator::Reset(Timestamp start) {
  start_ = start;
  prev_ = start_;
  first_unwrapped_timestamp_ = absl::nullopt;
  w_[0] = 90.0;
  w_[1] = 0;
  p_[0][0] = 1;
  p_[1][1] = kP11;
  p_[0][1] = p_[1][0] = 0;
  unwrapper_ = TimestampUnwrapper();
  packet_count_ = 0;
  detector_accumulator_pos_ = 0;
  detector_accumulator_neg_ = 0;
}

void TimestampExtrapolator::Update(Timestamp now, uint32_t ts90khz) {
  if (now - prev_ > TimeDelta::Seconds(10)) {
    // Ten seconds without a complete frame.
    // Reset the extrapolator
    Reset(now);
  } else {
    prev_ = now;
  }

  // Remove offset to prevent badly scaled matrices
  const TimeDelta offset = now - start_;
  double t_ms = offset.ms();

  int64_t unwrapped_ts90khz = unwrapper_.Unwrap(ts90khz);

  if (!first_unwrapped_timestamp_) {
    // Make an initial guess of the offset,
    // should be almost correct since t_ms - start
    // should about zero at this time.
    w_[1] = -w_[0] * t_ms;
    first_unwrapped_timestamp_ = unwrapped_ts90khz;
  }

  double residual =
      (static_cast<double>(unwrapped_ts90khz) - *first_unwrapped_timestamp_) -
      t_ms * w_[0] - w_[1];
  if (DelayChangeDetection(residual) &&
      packet_count_ >= kStartUpFilterDelayInPackets) {
    // A sudden change of average network delay has been detected.
    // Force the filter to adjust its offset parameter by changing
    // the offset uncertainty. Don't do this during startup.
    p_[1][1] = kP11;
  }

  if (prev_unwrapped_timestamp_ &&
      unwrapped_ts90khz < prev_unwrapped_timestamp_) {
    // Drop reordered frames.
    return;
  }

  // T = [t(k) 1]';
  // that = T'*w;
  // K = P*T/(lambda + T'*P*T);
  double K[2];
  K[0] = p_[0][0] * t_ms + p_[0][1];
  K[1] = p_[1][0] * t_ms + p_[1][1];
  double TPT = kLambda + t_ms * K[0] + K[1];
  K[0] /= TPT;
  K[1] /= TPT;
  // w = w + K*(ts(k) - that);
  w_[0] = w_[0] + K[0] * residual;
  w_[1] = w_[1] + K[1] * residual;
  // P = 1/lambda*(P - K*T'*P);
  double p00 =
      1 / kLambda * (p_[0][0] - (K[0] * t_ms * p_[0][0] + K[0] * p_[1][0]));
  double p01 =
      1 / kLambda * (p_[0][1] - (K[0] * t_ms * p_[0][1] + K[0] * p_[1][1]));
  p_[1][0] =
      1 / kLambda * (p_[1][0] - (K[1] * t_ms * p_[0][0] + K[1] * p_[1][0]));
  p_[1][1] =
      1 / kLambda * (p_[1][1] - (K[1] * t_ms * p_[0][1] + K[1] * p_[1][1]));
  p_[0][0] = p00;
  p_[0][1] = p01;
  prev_unwrapped_timestamp_ = unwrapped_ts90khz;
  if (packet_count_ < kStartUpFilterDelayInPackets) {
    packet_count_++;
  }
}

absl::optional<Timestamp> TimestampExtrapolator::ExtrapolateLocalTime(
    uint32_t timestamp90khz) const {
  int64_t unwrapped_ts90khz = unwrapper_.UnwrapWithoutUpdate(timestamp90khz);

  if (!first_unwrapped_timestamp_) {
    return absl::nullopt;
  } else if (packet_count_ < kStartUpFilterDelayInPackets) {
    constexpr double kRtpTicksPerMs = 90;
    TimeDelta diff = TimeDelta::Millis(
        (unwrapped_ts90khz - *prev_unwrapped_timestamp_) / kRtpTicksPerMs);
    return prev_ + diff;
  } else if (w_[0] < 1e-3) {
    return start_;
  } else {
    double timestampDiff = unwrapped_ts90khz - *first_unwrapped_timestamp_;
    auto diff_ms = static_cast<int64_t>((timestampDiff - w_[1]) / w_[0] + 0.5);
    return start_ + TimeDelta::Millis(diff_ms);
  }
}

bool TimestampExtrapolator::DelayChangeDetection(double error) {
  // CUSUM detection of sudden delay changes
  error = (error > 0) ? std::min(error, kAccMaxError)
                      : std::max(error, -kAccMaxError);
  detector_accumulator_pos_ =
      std::max(detector_accumulator_pos_ + error - kAccDrift, double{0});
  detector_accumulator_neg_ =
      std::min(detector_accumulator_neg_ + error + kAccDrift, double{0});
  if (detector_accumulator_pos_ > kAlarmThreshold ||
      detector_accumulator_neg_ < -kAlarmThreshold) {
    // Alarm
    detector_accumulator_pos_ = detector_accumulator_neg_ = 0;
    return true;
  }
  return false;
}

}  // namespace webrtc
