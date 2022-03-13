/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_REMOTE_ESTIMATE_H_
#define LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_REMOTE_ESTIMATE_H_

#include <memory>

#include "absl/types/optional.h"
#include "api/rtc_event_log/rtc_event.h"
#include "api/units/data_rate.h"
#include "api/units/timestamp.h"

namespace webrtc {

class RtcEventRemoteEstimate final : public RtcEvent {
 public:
  static constexpr Type kType = Type::RemoteEstimateEvent;

  RtcEventRemoteEstimate(DataRate link_capacity_lower,
                         DataRate link_capacity_upper)
      : link_capacity_lower_(link_capacity_lower),
        link_capacity_upper_(link_capacity_upper) {}

  Type GetType() const override { return kType; }
  bool IsConfigEvent() const override { return false; }

  const DataRate link_capacity_lower_;
  const DataRate link_capacity_upper_;
};

struct LoggedRemoteEstimateEvent {
  LoggedRemoteEstimateEvent() = default;

  int64_t log_time_us() const { return timestamp.us(); }
  int64_t log_time_ms() const { return timestamp.ms(); }

  Timestamp timestamp = Timestamp::MinusInfinity();
  absl::optional<DataRate> link_capacity_lower;
  absl::optional<DataRate> link_capacity_upper;
};
}  // namespace webrtc
#endif  // LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_REMOTE_ESTIMATE_H_
