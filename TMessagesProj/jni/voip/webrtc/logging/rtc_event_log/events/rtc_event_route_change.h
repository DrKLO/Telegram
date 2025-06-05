/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_ROUTE_CHANGE_H_
#define LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_ROUTE_CHANGE_H_

#include <memory>
#include <string>
#include <vector>

#include "absl/strings/string_view.h"
#include "api/rtc_event_log/rtc_event.h"
#include "api/units/timestamp.h"
#include "logging/rtc_event_log/events/rtc_event_field_encoding_parser.h"

namespace webrtc {

struct LoggedRouteChangeEvent {
  LoggedRouteChangeEvent() = default;
  LoggedRouteChangeEvent(Timestamp timestamp, bool connected, uint32_t overhead)
      : timestamp(timestamp), connected(connected), overhead(overhead) {}

  int64_t log_time_us() const { return timestamp.us(); }
  int64_t log_time_ms() const { return timestamp.ms(); }
  Timestamp log_time() const { return timestamp; }

  Timestamp timestamp = Timestamp::MinusInfinity();
  bool connected;
  uint32_t overhead;
};

class RtcEventRouteChange final : public RtcEvent {
 public:
  static constexpr Type kType = Type::RouteChangeEvent;

  RtcEventRouteChange(bool connected, uint32_t overhead);
  ~RtcEventRouteChange() override;

  Type GetType() const override { return kType; }
  bool IsConfigEvent() const override { return false; }

  std::unique_ptr<RtcEventRouteChange> Copy() const;

  bool connected() const { return connected_; }
  uint32_t overhead() const { return overhead_; }

  static std::string Encode(rtc::ArrayView<const RtcEvent*> batch) {
    // TODO(terelius): Implement
    return "";
  }

  static RtcEventLogParseStatus Parse(
      absl::string_view encoded_bytes,
      bool batched,
      std::vector<LoggedRouteChangeEvent>& output) {
    // TODO(terelius): Implement
    return RtcEventLogParseStatus::Error("Not Implemented", __FILE__, __LINE__);
  }

 private:
  RtcEventRouteChange(const RtcEventRouteChange& other);

  const bool connected_;
  const uint32_t overhead_;
};

}  // namespace webrtc
#endif  // LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_ROUTE_CHANGE_H_
