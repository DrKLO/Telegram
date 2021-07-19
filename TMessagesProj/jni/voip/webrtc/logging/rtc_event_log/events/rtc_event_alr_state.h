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
  static constexpr Type kType = Type::AlrStateEvent;

  explicit RtcEventAlrState(bool in_alr);
  ~RtcEventAlrState() override;

  Type GetType() const override { return kType; }
  bool IsConfigEvent() const override { return false; }

  std::unique_ptr<RtcEventAlrState> Copy() const;

  bool in_alr() const { return in_alr_; }

 private:
  RtcEventAlrState(const RtcEventAlrState& other);

  const bool in_alr_;
};

struct LoggedAlrStateEvent {
  LoggedAlrStateEvent() = default;
  LoggedAlrStateEvent(int64_t timestamp_us, bool in_alr)
      : timestamp_us(timestamp_us), in_alr(in_alr) {}

  int64_t log_time_us() const { return timestamp_us; }
  int64_t log_time_ms() const { return timestamp_us / 1000; }

  int64_t timestamp_us;
  bool in_alr;
};

}  // namespace webrtc
#endif  // LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_ALR_STATE_H_
