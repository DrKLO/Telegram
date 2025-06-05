/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/rtc_event_log/rtc_event_log_factory.h"

#include <memory>

#include "absl/base/nullability.h"
#include "api/environment/environment.h"
#include "api/field_trials_view.h"
#include "api/rtc_event_log/rtc_event_log.h"

#ifdef WEBRTC_ENABLE_RTC_EVENT_LOG
#include "logging/rtc_event_log/rtc_event_log_impl.h"
#endif

namespace webrtc {

absl::Nonnull<std::unique_ptr<RtcEventLog>> RtcEventLogFactory::Create(
    const Environment& env) const {
#ifndef WEBRTC_ENABLE_RTC_EVENT_LOG
  return std::make_unique<RtcEventLogNull>();
#else
  if (env.field_trials().IsEnabled("WebRTC-RtcEventLogKillSwitch")) {
    return std::make_unique<RtcEventLogNull>();
  }
  return std::make_unique<RtcEventLogImpl>(env);
#endif
}

}  // namespace webrtc
