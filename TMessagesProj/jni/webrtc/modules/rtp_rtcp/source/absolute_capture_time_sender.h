/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_RTP_RTCP_SOURCE_ABSOLUTE_CAPTURE_TIME_SENDER_H_
#define MODULES_RTP_RTCP_SOURCE_ABSOLUTE_CAPTURE_TIME_SENDER_H_

#include "api/array_view.h"
#include "api/rtp_headers.h"
#include "api/units/time_delta.h"
#include "api/units/timestamp.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/thread_annotations.h"
#include "system_wrappers/include/clock.h"

namespace webrtc {

//
// Helper class for sending the |AbsoluteCaptureTime| header extension.
//
// Supports the "timestamp interpolation" optimization:
//   A sender SHOULD save bandwidth by not sending abs-capture-time with every
//   RTP packet. It SHOULD still send them at regular intervals (e.g. every
//   second) to help mitigate the impact of clock drift and packet loss. Mixers
//   SHOULD always send abs-capture-time with the first RTP packet after
//   changing capture system.
//
//   Timestamp interpolation works fine as long as there’s reasonably low
//   NTP/RTP clock drift. This is not always true. Senders that detect “jumps”
//   between its NTP and RTP clock mappings SHOULD send abs-capture-time with
//   the first RTP packet after such a thing happening.
//
// See: https://webrtc.org/experiments/rtp-hdrext/abs-capture-time/
//
class AbsoluteCaptureTimeSender {
 public:
  static constexpr TimeDelta kInterpolationMaxInterval =
      TimeDelta::Millis(1000);
  static constexpr TimeDelta kInterpolationMaxError = TimeDelta::Millis(1);

  explicit AbsoluteCaptureTimeSender(Clock* clock);

  // Returns the source (i.e. SSRC or CSRC) of the capture system.
  static uint32_t GetSource(uint32_t ssrc,
                            rtc::ArrayView<const uint32_t> csrcs);

  // Returns a header extension to be sent, or |absl::nullopt| if the header
  // extension shouldn't be sent.
  absl::optional<AbsoluteCaptureTime> OnSendPacket(
      uint32_t source,
      uint32_t rtp_timestamp,
      uint32_t rtp_clock_frequency,
      uint64_t absolute_capture_timestamp,
      absl::optional<int64_t> estimated_capture_clock_offset);

 private:
  bool ShouldSendExtension(
      Timestamp send_time,
      uint32_t source,
      uint32_t rtp_timestamp,
      uint32_t rtp_clock_frequency,
      uint64_t absolute_capture_timestamp,
      absl::optional<int64_t> estimated_capture_clock_offset) const
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  Clock* const clock_;

  Mutex mutex_;

  Timestamp last_send_time_ RTC_GUARDED_BY(mutex_);

  uint32_t last_source_ RTC_GUARDED_BY(mutex_);
  uint32_t last_rtp_timestamp_ RTC_GUARDED_BY(mutex_);
  uint32_t last_rtp_clock_frequency_ RTC_GUARDED_BY(mutex_);
  uint64_t last_absolute_capture_timestamp_ RTC_GUARDED_BY(mutex_);
  absl::optional<int64_t> last_estimated_capture_clock_offset_
      RTC_GUARDED_BY(mutex_);
};  // AbsoluteCaptureTimeSender

}  // namespace webrtc

#endif  // MODULES_RTP_RTCP_SOURCE_ABSOLUTE_CAPTURE_TIME_SENDER_H_
