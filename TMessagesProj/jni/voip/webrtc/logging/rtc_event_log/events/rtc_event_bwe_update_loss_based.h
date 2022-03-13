/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_BWE_UPDATE_LOSS_BASED_H_
#define LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_BWE_UPDATE_LOSS_BASED_H_

#include <stdint.h>

#include <memory>

#include "api/rtc_event_log/rtc_event.h"
#include "api/units/timestamp.h"

namespace webrtc {

class RtcEventBweUpdateLossBased final : public RtcEvent {
 public:
  static constexpr Type kType = Type::BweUpdateLossBased;

  RtcEventBweUpdateLossBased(int32_t bitrate_bps_,
                             uint8_t fraction_loss_,
                             int32_t total_packets_);
  ~RtcEventBweUpdateLossBased() override;

  Type GetType() const override { return kType; }
  bool IsConfigEvent() const override { return false; }

  std::unique_ptr<RtcEventBweUpdateLossBased> Copy() const;

  int32_t bitrate_bps() const { return bitrate_bps_; }
  uint8_t fraction_loss() const { return fraction_loss_; }
  int32_t total_packets() const { return total_packets_; }

 private:
  RtcEventBweUpdateLossBased(const RtcEventBweUpdateLossBased& other);

  const int32_t bitrate_bps_;
  const uint8_t fraction_loss_;
  const int32_t total_packets_;
};

struct LoggedBweLossBasedUpdate {
  LoggedBweLossBasedUpdate() = default;
  LoggedBweLossBasedUpdate(Timestamp timestamp,
                           int32_t bitrate_bps,
                           uint8_t fraction_lost,
                           int32_t expected_packets)
      : timestamp(timestamp),
        bitrate_bps(bitrate_bps),
        fraction_lost(fraction_lost),
        expected_packets(expected_packets) {}

  int64_t log_time_us() const { return timestamp.us(); }
  int64_t log_time_ms() const { return timestamp.ms(); }

  Timestamp timestamp = Timestamp::MinusInfinity();
  int32_t bitrate_bps;
  uint8_t fraction_lost;
  int32_t expected_packets;
};

}  // namespace webrtc

#endif  // LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_BWE_UPDATE_LOSS_BASED_H_
