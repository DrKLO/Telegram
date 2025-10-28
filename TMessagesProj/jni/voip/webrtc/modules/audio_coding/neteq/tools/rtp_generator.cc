/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/tools/rtp_generator.h"

namespace webrtc {
namespace test {

uint32_t RtpGenerator::GetRtpHeader(uint8_t payload_type,
                                    size_t payload_length_samples,
                                    RTPHeader* rtp_header) {
  RTC_DCHECK(rtp_header);
  if (!rtp_header) {
    return 0;
  }
  rtp_header->sequenceNumber = seq_number_++;
  rtp_header->timestamp = timestamp_;
  timestamp_ += static_cast<uint32_t>(payload_length_samples);
  rtp_header->payloadType = payload_type;
  rtp_header->markerBit = false;
  rtp_header->ssrc = ssrc_;
  rtp_header->numCSRCs = 0;

  uint32_t this_send_time = next_send_time_ms_;
  RTC_DCHECK_GT(samples_per_ms_, 0);
  next_send_time_ms_ +=
      ((1.0 + drift_factor_) * payload_length_samples) / samples_per_ms_;
  return this_send_time;
}

void RtpGenerator::set_drift_factor(double factor) {
  if (factor > -1.0) {
    drift_factor_ = factor;
  }
}

uint32_t TimestampJumpRtpGenerator::GetRtpHeader(uint8_t payload_type,
                                                 size_t payload_length_samples,
                                                 RTPHeader* rtp_header) {
  uint32_t ret = RtpGenerator::GetRtpHeader(payload_type,
                                            payload_length_samples, rtp_header);
  if (timestamp_ - static_cast<uint32_t>(payload_length_samples) <=
          jump_from_timestamp_ &&
      timestamp_ > jump_from_timestamp_) {
    // We just moved across the `jump_from_timestamp_` timestamp. Do the jump.
    timestamp_ = jump_to_timestamp_;
  }
  return ret;
}

}  // namespace test
}  // namespace webrtc
