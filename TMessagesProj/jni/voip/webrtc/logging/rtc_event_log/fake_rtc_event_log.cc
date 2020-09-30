/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "logging/rtc_event_log/fake_rtc_event_log.h"

#include "logging/rtc_event_log/events/rtc_event_ice_candidate_pair.h"
#include "rtc_base/bind.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {

FakeRtcEventLog::FakeRtcEventLog(rtc::Thread* thread) : thread_(thread) {
  RTC_DCHECK(thread_);
}
FakeRtcEventLog::~FakeRtcEventLog() = default;

bool FakeRtcEventLog::StartLogging(std::unique_ptr<RtcEventLogOutput> output,
                                   int64_t output_period_ms) {
  return true;
}

void FakeRtcEventLog::StopLogging() {
  invoker_.Flush(thread_);
}

void FakeRtcEventLog::Log(std::unique_ptr<RtcEvent> event) {
  RtcEvent::Type rtc_event_type = event->GetType();
  invoker_.AsyncInvoke<void>(
      RTC_FROM_HERE, thread_,
      rtc::Bind(&FakeRtcEventLog::IncrementEventCount, this, rtc_event_type));
}

}  // namespace webrtc
