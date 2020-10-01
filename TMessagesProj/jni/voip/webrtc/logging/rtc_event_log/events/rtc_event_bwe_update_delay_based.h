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

#include "api/rtc_event_log/rtc_event.h"

namespace webrtc {

enum class BandwidthUsage;

class RtcEventBweUpdateDelayBased final : public RtcEvent {
 public:
  RtcEventBweUpdateDelayBased(int32_t bitrate_bps,
                              BandwidthUsage detector_state);
  ~RtcEventBweUpdateDelayBased() override;

  Type GetType() const override;

  bool IsConfigEvent() const override;

  std::unique_ptr<RtcEventBweUpdateDelayBased> Copy() const;

  int32_t bitrate_bps() const { return bitrate_bps_; }
  BandwidthUsage detector_state() const { return detector_state_; }

 private:
  RtcEventBweUpdateDelayBased(const RtcEventBweUpdateDelayBased& other);

  const int32_t bitrate_bps_;
  const BandwidthUsage detector_state_;
};

}  // namespace webrtc

#endif  // LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_BWE_UPDATE_DELAY_BASED_H_
