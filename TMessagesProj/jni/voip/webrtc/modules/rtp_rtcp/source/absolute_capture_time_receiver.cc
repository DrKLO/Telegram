/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/absolute_capture_time_receiver.h"

namespace webrtc {

AbsoluteCaptureTimeReceiver::AbsoluteCaptureTimeReceiver(Clock* clock)
    : AbsoluteCaptureTimeInterpolator(clock) {}

void AbsoluteCaptureTimeReceiver::SetRemoteToLocalClockOffset(
    absl::optional<int64_t> value_q32x32) {
  capture_clock_offset_updater_.SetRemoteToLocalClockOffset(value_q32x32);
}

absl::optional<AbsoluteCaptureTime>
AbsoluteCaptureTimeReceiver::OnReceivePacket(
    uint32_t source,
    uint32_t rtp_timestamp,
    uint32_t rtp_clock_frequency,
    const absl::optional<AbsoluteCaptureTime>& received_extension) {
  auto extension = AbsoluteCaptureTimeInterpolator::OnReceivePacket(
      source, rtp_timestamp, rtp_clock_frequency, received_extension);

  if (extension.has_value()) {
    extension->estimated_capture_clock_offset =
        capture_clock_offset_updater_.AdjustEstimatedCaptureClockOffset(
            extension->estimated_capture_clock_offset);
  }

  return extension;
}

}  // namespace webrtc
