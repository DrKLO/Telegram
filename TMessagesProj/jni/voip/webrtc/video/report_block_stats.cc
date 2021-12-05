/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "video/report_block_stats.h"

#include <algorithm>

namespace webrtc {

namespace {
int FractionLost(uint32_t num_lost_sequence_numbers,
                 uint32_t num_sequence_numbers) {
  if (num_sequence_numbers == 0) {
    return 0;
  }
  return ((num_lost_sequence_numbers * 255) + (num_sequence_numbers / 2)) /
         num_sequence_numbers;
}
}  // namespace

// Helper class for rtcp statistics.
ReportBlockStats::ReportBlockStats()
    : num_sequence_numbers_(0), num_lost_sequence_numbers_(0) {}

ReportBlockStats::~ReportBlockStats() {}

void ReportBlockStats::Store(uint32_t ssrc,
                             int packets_lost,
                             uint32_t extended_highest_sequence_number) {
  Report report;
  report.packets_lost = packets_lost;
  report.extended_highest_sequence_number = extended_highest_sequence_number;

  // Get diff with previous report block.
  const auto prev_report = prev_reports_.find(ssrc);
  if (prev_report != prev_reports_.end()) {
    int seq_num_diff = report.extended_highest_sequence_number -
                       prev_report->second.extended_highest_sequence_number;
    int cum_loss_diff = report.packets_lost - prev_report->second.packets_lost;
    if (seq_num_diff >= 0 && cum_loss_diff >= 0) {
      // Update total number of packets/lost packets.
      num_sequence_numbers_ += seq_num_diff;
      num_lost_sequence_numbers_ += cum_loss_diff;
    }
  }
  // Store current report block.
  prev_reports_[ssrc] = report;
}

int ReportBlockStats::FractionLostInPercent() const {
  if (num_sequence_numbers_ == 0) {
    return -1;
  }
  return FractionLost(num_lost_sequence_numbers_, num_sequence_numbers_) * 100 /
         255;
}

}  // namespace webrtc
