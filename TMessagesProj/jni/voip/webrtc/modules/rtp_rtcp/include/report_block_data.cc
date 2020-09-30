/*
 *  Copyright 2019 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/include/report_block_data.h"

namespace webrtc {

ReportBlockData::ReportBlockData()
    : report_block_(),
      report_block_timestamp_utc_us_(0),
      last_rtt_ms_(0),
      min_rtt_ms_(0),
      max_rtt_ms_(0),
      sum_rtt_ms_(0),
      num_rtts_(0) {}

double ReportBlockData::AvgRttMs() const {
  return num_rtts_ ? static_cast<double>(sum_rtt_ms_) / num_rtts_ : 0.0;
}

void ReportBlockData::SetReportBlock(RTCPReportBlock report_block,
                                     int64_t report_block_timestamp_utc_us) {
  report_block_ = report_block;
  report_block_timestamp_utc_us_ = report_block_timestamp_utc_us;
}

void ReportBlockData::AddRoundTripTimeSample(int64_t rtt_ms) {
  if (rtt_ms > max_rtt_ms_)
    max_rtt_ms_ = rtt_ms;
  if (num_rtts_ == 0 || rtt_ms < min_rtt_ms_)
    min_rtt_ms_ = rtt_ms;
  last_rtt_ms_ = rtt_ms;
  sum_rtt_ms_ += rtt_ms;
  ++num_rtts_;
}

}  // namespace webrtc
