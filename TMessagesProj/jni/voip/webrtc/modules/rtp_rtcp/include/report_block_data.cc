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

#include "rtc_base/checks.h"

namespace webrtc {

TimeDelta ReportBlockData::jitter(int rtp_clock_rate_hz) const {
  RTC_DCHECK_GT(rtp_clock_rate_hz, 0);
  // Conversion to TimeDelta and division are swapped to avoid conversion
  // to/from floating point types.
  return TimeDelta::Seconds(jitter()) / rtp_clock_rate_hz;
}

void ReportBlockData::SetReportBlock(uint32_t sender_ssrc,
                                     const rtcp::ReportBlock& report_block,
                                     Timestamp report_block_timestamp_utc) {
  sender_ssrc_ = sender_ssrc;
  source_ssrc_ = report_block.source_ssrc();
  fraction_lost_raw_ = report_block.fraction_lost();
  cumulative_lost_ = report_block.cumulative_lost();
  extended_highest_sequence_number_ = report_block.extended_high_seq_num();
  jitter_ = report_block.jitter();
  report_block_timestamp_utc_ = report_block_timestamp_utc;
}

void ReportBlockData::AddRoundTripTimeSample(TimeDelta rtt) {
  last_rtt_ = rtt;
  sum_rtt_ += rtt;
  ++num_rtts_;
}

}  // namespace webrtc
