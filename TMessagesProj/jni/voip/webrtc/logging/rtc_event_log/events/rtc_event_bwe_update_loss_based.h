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

namespace webrtc {

class RtcEventBweUpdateLossBased final : public RtcEvent {
 public:
  RtcEventBweUpdateLossBased(int32_t bitrate_bps_,
                             uint8_t fraction_loss_,
                             int32_t total_packets_);
  ~RtcEventBweUpdateLossBased() override;

  Type GetType() const override;

  bool IsConfigEvent() const override;

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

}  // namespace webrtc

#endif  // LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_BWE_UPDATE_LOSS_BASED_H_
