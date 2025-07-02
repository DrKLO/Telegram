/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/absolute_capture_time_interpolator.h"

#include <limits>

#include "rtc_base/checks.h"

namespace webrtc {

AbsoluteCaptureTimeInterpolator::AbsoluteCaptureTimeInterpolator(Clock* clock)
    : clock_(clock) {}

uint32_t AbsoluteCaptureTimeInterpolator::GetSource(
    uint32_t ssrc,
    rtc::ArrayView<const uint32_t> csrcs) {
  if (csrcs.empty()) {
    return ssrc;
  }

  return csrcs[0];
}

absl::optional<AbsoluteCaptureTime>
AbsoluteCaptureTimeInterpolator::OnReceivePacket(
    uint32_t source,
    uint32_t rtp_timestamp,
    int rtp_clock_frequency_hz,
    const absl::optional<AbsoluteCaptureTime>& received_extension) {
  const Timestamp receive_time = clock_->CurrentTime();

  MutexLock lock(&mutex_);

  if (received_extension == absl::nullopt) {
    if (!ShouldInterpolateExtension(receive_time, source, rtp_timestamp,
                                    rtp_clock_frequency_hz)) {
      last_receive_time_ = Timestamp::MinusInfinity();
      return absl::nullopt;
    }

    return AbsoluteCaptureTime{
        .absolute_capture_timestamp = InterpolateAbsoluteCaptureTimestamp(
            rtp_timestamp, rtp_clock_frequency_hz, last_rtp_timestamp_,
            last_received_extension_.absolute_capture_timestamp),
        .estimated_capture_clock_offset =
            last_received_extension_.estimated_capture_clock_offset,
    };
  } else {
    last_source_ = source;
    last_rtp_timestamp_ = rtp_timestamp;
    last_rtp_clock_frequency_hz_ = rtp_clock_frequency_hz;
    last_received_extension_ = *received_extension;

    last_receive_time_ = receive_time;

    return received_extension;
  }
}

uint64_t AbsoluteCaptureTimeInterpolator::InterpolateAbsoluteCaptureTimestamp(
    uint32_t rtp_timestamp,
    int rtp_clock_frequency_hz,
    uint32_t last_rtp_timestamp,
    uint64_t last_absolute_capture_timestamp) {
  RTC_DCHECK_GT(rtp_clock_frequency_hz, 0);

  return last_absolute_capture_timestamp +
         static_cast<int64_t>(uint64_t{rtp_timestamp - last_rtp_timestamp}
                              << 32) /
             rtp_clock_frequency_hz;
}

bool AbsoluteCaptureTimeInterpolator::ShouldInterpolateExtension(
    Timestamp receive_time,
    uint32_t source,
    uint32_t rtp_timestamp,
    int rtp_clock_frequency_hz) const {
  // Shouldn't if the last received extension is not eligible for interpolation,
  // in particular if we don't have a previously received extension stored.
  if (receive_time - last_receive_time_ > kInterpolationMaxInterval) {
    return false;
  }

  // Shouldn't if the source has changed.
  if (last_source_ != source) {
    return false;
  }

  // Shouldn't if the RTP clock frequency has changed.
  if (last_rtp_clock_frequency_hz_ != rtp_clock_frequency_hz) {
    return false;
  }

  // Shouldn't if the RTP clock frequency is invalid.
  if (rtp_clock_frequency_hz <= 0) {
    return false;
  }

  return true;
}

}  // namespace webrtc
