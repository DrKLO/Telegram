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

#include <limits>

#include "rtc_base/checks.h"

namespace webrtc {
namespace {

constexpr Timestamp kInvalidLastReceiveTime = Timestamp::MinusInfinity();
}  // namespace

constexpr TimeDelta AbsoluteCaptureTimeReceiver::kInterpolationMaxInterval;

AbsoluteCaptureTimeReceiver::AbsoluteCaptureTimeReceiver(Clock* clock)
    : clock_(clock),
      remote_to_local_clock_offset_(absl::nullopt),
      last_receive_time_(kInvalidLastReceiveTime) {}

uint32_t AbsoluteCaptureTimeReceiver::GetSource(
    uint32_t ssrc,
    rtc::ArrayView<const uint32_t> csrcs) {
  if (csrcs.empty()) {
    return ssrc;
  }

  return csrcs[0];
}

void AbsoluteCaptureTimeReceiver::SetRemoteToLocalClockOffset(
    absl::optional<int64_t> value_q32x32) {
  MutexLock lock(&mutex_);

  remote_to_local_clock_offset_ = value_q32x32;
}

absl::optional<AbsoluteCaptureTime>
AbsoluteCaptureTimeReceiver::OnReceivePacket(
    uint32_t source,
    uint32_t rtp_timestamp,
    uint32_t rtp_clock_frequency,
    const absl::optional<AbsoluteCaptureTime>& received_extension) {
  const Timestamp receive_time = clock_->CurrentTime();

  MutexLock lock(&mutex_);

  AbsoluteCaptureTime extension;
  if (received_extension == absl::nullopt) {
    if (!ShouldInterpolateExtension(receive_time, source, rtp_timestamp,
                                    rtp_clock_frequency)) {
      last_receive_time_ = kInvalidLastReceiveTime;
      return absl::nullopt;
    }

    extension.absolute_capture_timestamp = InterpolateAbsoluteCaptureTimestamp(
        rtp_timestamp, rtp_clock_frequency, last_rtp_timestamp_,
        last_absolute_capture_timestamp_);
    extension.estimated_capture_clock_offset =
        last_estimated_capture_clock_offset_;
  } else {
    last_source_ = source;
    last_rtp_timestamp_ = rtp_timestamp;
    last_rtp_clock_frequency_ = rtp_clock_frequency;
    last_absolute_capture_timestamp_ =
        received_extension->absolute_capture_timestamp;
    last_estimated_capture_clock_offset_ =
        received_extension->estimated_capture_clock_offset;

    last_receive_time_ = receive_time;

    extension = *received_extension;
  }

  extension.estimated_capture_clock_offset = AdjustEstimatedCaptureClockOffset(
      extension.estimated_capture_clock_offset);

  return extension;
}

uint64_t AbsoluteCaptureTimeReceiver::InterpolateAbsoluteCaptureTimestamp(
    uint32_t rtp_timestamp,
    uint32_t rtp_clock_frequency,
    uint32_t last_rtp_timestamp,
    uint64_t last_absolute_capture_timestamp) {
  RTC_DCHECK_GT(rtp_clock_frequency, 0);

  return last_absolute_capture_timestamp +
         static_cast<int64_t>(
             rtc::dchecked_cast<uint64_t>(rtp_timestamp - last_rtp_timestamp)
             << 32) /
             rtp_clock_frequency;
}

bool AbsoluteCaptureTimeReceiver::ShouldInterpolateExtension(
    Timestamp receive_time,
    uint32_t source,
    uint32_t rtp_timestamp,
    uint32_t rtp_clock_frequency) const {
  // Shouldn't if we don't have a previously received extension stored.
  if (last_receive_time_ == kInvalidLastReceiveTime) {
    return false;
  }

  // Shouldn't if the last received extension is too old.
  if ((receive_time - last_receive_time_) > kInterpolationMaxInterval) {
    return false;
  }

  // Shouldn't if the source has changed.
  if (last_source_ != source) {
    return false;
  }

  // Shouldn't if the RTP clock frequency has changed.
  if (last_rtp_clock_frequency_ != rtp_clock_frequency) {
    return false;
  }

  // Shouldn't if the RTP clock frequency is invalid.
  if (rtp_clock_frequency <= 0) {
    return false;
  }

  return true;
}

absl::optional<int64_t>
AbsoluteCaptureTimeReceiver::AdjustEstimatedCaptureClockOffset(
    absl::optional<int64_t> received_value) const {
  if (received_value == absl::nullopt ||
      remote_to_local_clock_offset_ == absl::nullopt) {
    return absl::nullopt;
  }

  // Do calculations as "unsigned" to make overflows deterministic.
  return static_cast<uint64_t>(*received_value) +
         static_cast<uint64_t>(*remote_to_local_clock_offset_);
}

}  // namespace webrtc
