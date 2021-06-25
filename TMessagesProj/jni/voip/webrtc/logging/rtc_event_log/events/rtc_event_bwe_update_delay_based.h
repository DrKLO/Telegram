/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_BWE_UPDATE_DELAY_BASED_H_
#define LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_BWE_UPDATE_DELAY_BASED_H_

#include <stdint.h>

#include <memory>

#include "api/network_state_predictor.h"
#include "api/rtc_event_log/rtc_event.h"

namespace webrtc {

class RtcEventBweUpdateDelayBased final : public RtcEvent {
 public:
  static constexpr Type kType = Type::BweUpdateDelayBased;

  RtcEventBweUpdateDelayBased(int32_t bitrate_bps,
                              BandwidthUsage detector_state);
  ~RtcEventBweUpdateDelayBased() override;

  Type GetType() const override { return kType; }
  bool IsConfigEvent() const override { return false; }

  std::unique_ptr<RtcEventBweUpdateDelayBased> Copy() const;

  int32_t bitrate_bps() const { return bitrate_bps_; }
  BandwidthUsage detector_state() const { return detector_state_; }

 private:
  RtcEventBweUpdateDelayBased(const RtcEventBweUpdateDelayBased& other);

  const int32_t bitrate_bps_;
  const BandwidthUsage detector_state_;
};

struct LoggedBweDelayBasedUpdate {
  LoggedBweDelayBasedUpdate() = default;
  LoggedBweDelayBasedUpdate(int64_t timestamp_us,
                            int32_t bitrate_bps,
                            BandwidthUsage detector_state)
      : timestamp_us(timestamp_us),
        bitrate_bps(bitrate_bps),
        detector_state(detector_state) {}

  int64_t log_time_us() const { return timestamp_us; }
  int64_t log_time_ms() const { return timestamp_us / 1000; }

  int64_t timestamp_us;
  int32_t bitrate_bps;
  BandwidthUsage detector_state;
};

}  // namespace webrtc

#endif  // LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_BWE_UPDATE_DELAY_BASED_H_
