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

namespace webrtc {

namespace {

constexpr int kMinimumNumberOfSamples = 2;
constexpr int kTimingLogIntervalMs = 10000;
constexpr int kClocksOffsetSmoothingWindow = 100;

}  // namespace

// TODO(wu): Refactor this class so that it can be shared with
// vie_sync_module.cc.
RemoteNtpTimeEstimator::RemoteNtpTimeEstimator(Clock* clock)
    : clock_(clock),
      ntp_clocks_offset_estimator_(kClocksOffsetSmoothingWindow),
      last_timing_log_ms_(-1) {}

RemoteNtpTimeEstimator::~RemoteNtpTimeEstimator() {}

bool RemoteNtpTimeEstimator::UpdateRtcpTimestamp(int64_t rtt,
                                                 uint32_t ntp_secs,
                                                 uint32_t ntp_frac,
                                                 uint32_t rtp_timestamp) {
  bool new_rtcp_sr = false;
  if (!rtp_to_ntp_.UpdateMeasurements(ntp_secs, ntp_frac, rtp_timestamp,
                                      &new_rtcp_sr)) {
    return false;
  }
  if (!new_rtcp_sr) {
    // No new RTCP SR since last time this function was called.
    return true;
  }

  // Update extrapolator with the new arrival time.
  // The extrapolator assumes the ntp time.
  int64_t receiver_arrival_time_ms =
      clock_->TimeInMilliseconds() + NtpOffsetMs();
  int64_t sender_send_time_ms = Clock::NtpToMs(ntp_secs, ntp_frac);
  int64_t sender_arrival_time_ms = sender_send_time_ms + rtt / 2;
  int64_t remote_to_local_clocks_offset =
      receiver_arrival_time_ms - sender_arrival_time_ms;
  ntp_clocks_offset_estimator_.Insert(remote_to_local_clocks_offset);
  return true;
}

int64_t RemoteNtpTimeEstimator::Estimate(uint32_t rtp_timestamp) {
  int64_t sender_capture_ntp_ms = 0;
  if (!rtp_to_ntp_.Estimate(rtp_timestamp, &sender_capture_ntp_ms)) {
    return -1;
  }

  int64_t remote_to_local_clocks_offset =
      ntp_clocks_offset_estimator_.GetFilteredValue();
  int64_t receiver_capture_ntp_ms =
      sender_capture_ntp_ms + remote_to_local_clocks_offset;

  // TODO(bugs.webrtc.org/11327): Clock::CurrentNtpInMilliseconds() was
  // previously used to calculate the offset between the local and the remote
  // clock. However, rtc::TimeMillis() + NtpOffsetMs() is now used as the local
  // ntp clock value. To preserve the old behavior of this method, the return
  // value is adjusted with the difference between the two local ntp clocks.
  int64_t now_ms = clock_->TimeInMilliseconds();
  int64_t offset_between_local_ntp_clocks =
      clock_->CurrentNtpInMilliseconds() - now_ms - NtpOffsetMs();
  receiver_capture_ntp_ms += offset_between_local_ntp_clocks;

  if (now_ms - last_timing_log_ms_ > kTimingLogIntervalMs) {
    RTC_LOG(LS_INFO) << "RTP timestamp: " << rtp_timestamp
                     << " in NTP clock: " << sender_capture_ntp_ms
                     << " estimated time in receiver NTP clock: "
                     << receiver_capture_ntp_ms;
    last_timing_log_ms_ = now_ms;
  }
  return receiver_capture_ntp_ms;
}

absl::optional<int64_t>
RemoteNtpTimeEstimator::EstimateRemoteToLocalClockOffsetMs() {
  if (ntp_clocks_offset_estimator_.GetNumberOfSamplesStored() <
      kMinimumNumberOfSamples) {
    return absl::nullopt;
  }
  return ntp_clocks_offset_estimator_.GetFilteredValue();
}

}  // namespace webrtc
