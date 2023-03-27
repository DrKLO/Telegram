/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/include/remote_ntp_time_estimator.h"

#include <cstdint>

#include "modules/rtp_rtcp/source/time_util.h"
#include "rtc_base/logging.h"
#include "system_wrappers/include/clock.h"
#include "system_wrappers/include/ntp_time.h"

namespace webrtc {

namespace {

constexpr int kMinimumNumberOfSamples = 2;
constexpr TimeDelta kTimingLogInterval = TimeDelta::Seconds(10);
constexpr int kClocksOffsetSmoothingWindow = 100;

// Subtracts two NtpTime values keeping maximum precision.
int64_t Subtract(NtpTime minuend, NtpTime subtrahend) {
  uint64_t a = static_cast<uint64_t>(minuend);
  uint64_t b = static_cast<uint64_t>(subtrahend);
  return a >= b ? static_cast<int64_t>(a - b) : -static_cast<int64_t>(b - a);
}

NtpTime Add(NtpTime lhs, int64_t rhs) {
  uint64_t result = static_cast<uint64_t>(lhs);
  if (rhs >= 0) {
    result += static_cast<uint64_t>(rhs);
  } else {
    result -= static_cast<uint64_t>(-rhs);
  }
  return NtpTime(result);
}

}  // namespace

// TODO(wu): Refactor this class so that it can be shared with
// vie_sync_module.cc.
RemoteNtpTimeEstimator::RemoteNtpTimeEstimator(Clock* clock)
    : clock_(clock),
      ntp_clocks_offset_estimator_(kClocksOffsetSmoothingWindow) {}

bool RemoteNtpTimeEstimator::UpdateRtcpTimestamp(TimeDelta rtt,
                                                 NtpTime sender_send_time,
                                                 uint32_t rtp_timestamp) {
  switch (rtp_to_ntp_.UpdateMeasurements(sender_send_time, rtp_timestamp)) {
    case RtpToNtpEstimator::kInvalidMeasurement:
      return false;
    case RtpToNtpEstimator::kSameMeasurement:
      // No new RTCP SR since last time this function was called.
      return true;
    case RtpToNtpEstimator::kNewMeasurement:
      break;
  }

  // Assume connection is symmetric and thus time to deliver the packet is half
  // the round trip time.
  int64_t deliver_time_ntp = ToNtpUnits(rtt) / 2;

  // Update extrapolator with the new arrival time.
  NtpTime receiver_arrival_time = clock_->CurrentNtpTime();
  int64_t remote_to_local_clocks_offset =
      Subtract(receiver_arrival_time, sender_send_time) - deliver_time_ntp;
  ntp_clocks_offset_estimator_.Insert(remote_to_local_clocks_offset);
  return true;
}

NtpTime RemoteNtpTimeEstimator::EstimateNtp(uint32_t rtp_timestamp) {
  NtpTime sender_capture = rtp_to_ntp_.Estimate(rtp_timestamp);
  if (!sender_capture.Valid()) {
    return sender_capture;
  }

  int64_t remote_to_local_clocks_offset =
      ntp_clocks_offset_estimator_.GetFilteredValue();
  NtpTime receiver_capture = Add(sender_capture, remote_to_local_clocks_offset);

  Timestamp now = clock_->CurrentTime();
  if (now - last_timing_log_ > kTimingLogInterval) {
    RTC_LOG(LS_INFO) << "RTP timestamp: " << rtp_timestamp
                     << " in NTP clock: " << sender_capture.ToMs()
                     << " estimated time in receiver NTP clock: "
                     << receiver_capture.ToMs();
    last_timing_log_ = now;
  }

  return receiver_capture;
}

absl::optional<int64_t>
RemoteNtpTimeEstimator::EstimateRemoteToLocalClockOffset() {
  if (ntp_clocks_offset_estimator_.GetNumberOfSamplesStored() <
      kMinimumNumberOfSamples) {
    return absl::nullopt;
  }
  return ntp_clocks_offset_estimator_.GetFilteredValue();
}

}  // namespace webrtc
