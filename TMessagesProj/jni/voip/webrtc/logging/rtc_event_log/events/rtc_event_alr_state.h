/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_ALR_STATE_H_
#define LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_ALR_STATE_H_

#include <memory>

#include "api/rtc_event_log/rtc_event.h"

namespace webrtc {

class RtcEventAlrState final : public RtcEvent {
 public:
  explicit RtcEventAlrState(bool in_alr);
  ~RtcEventAlrState() override;

  Type GetType() const override;

  bool IsConfigEvent() const override;

  std::unique_ptr<RtcEventAlrState> Copy() const;

  bool in_alr() const { return in_alr_; }

 private:
  RtcEventAlrState(const RtcEventAlrState& other);

  const bool in_alr_;
};

}  // namespace webrtc
#endif  // LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_ALR_STATE_H_
