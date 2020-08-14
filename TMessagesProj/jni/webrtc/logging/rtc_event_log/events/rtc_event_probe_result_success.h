/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_PROBE_RESULT_SUCCESS_H_
#define LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_PROBE_RESULT_SUCCESS_H_

#include <stdint.h>

#include <memory>

#include "api/rtc_event_log/rtc_event.h"

namespace webrtc {

class RtcEventProbeResultSuccess final : public RtcEvent {
 public:
  RtcEventProbeResultSuccess(int32_t id, int32_t bitrate_bps);
  ~RtcEventProbeResultSuccess() override = default;

  Type GetType() const override;

  bool IsConfigEvent() const override;

  std::unique_ptr<RtcEventProbeResultSuccess> Copy() const;

  int32_t id() const { return id_; }
  int32_t bitrate_bps() const { return bitrate_bps_; }

 private:
  RtcEventProbeResultSuccess(const RtcEventProbeResultSuccess& other);

  const int32_t id_;
  const int32_t bitrate_bps_;
};

}  // namespace webrtc

#endif  // LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_PROBE_RESULT_SUCCESS_H_
