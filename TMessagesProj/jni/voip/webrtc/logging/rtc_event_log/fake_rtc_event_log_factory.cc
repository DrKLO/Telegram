/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "logging/rtc_event_log/fake_rtc_event_log_factory.h"

#include <memory>

#include "api/rtc_event_log/rtc_event_log.h"
#include "logging/rtc_event_log/fake_rtc_event_log.h"

namespace webrtc {

absl::Nonnull<std::unique_ptr<RtcEventLog>> FakeRtcEventLogFactory::Create(
    const Environment& /*env*/) const {
  auto fake_event_log = std::make_unique<FakeRtcEventLog>();
  const_cast<FakeRtcEventLog*&>(last_log_created_) = fake_event_log.get();
  return fake_event_log;
}

}  // namespace webrtc
