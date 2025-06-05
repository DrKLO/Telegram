/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/deprecated/event_wrapper.h"

#include "rtc_base/event.h"

namespace webrtc {

class EventWrapperImpl : public EventWrapper {
 public:
  ~EventWrapperImpl() override {}

  bool Set() override {
    event_.Set();
    return true;
  }

  // TODO(bugs.webrtc.org/14366): Migrate to TimeDelta.
  EventTypeWrapper Wait(int max_time_ms) override {
    return event_.Wait(TimeDelta::Millis(max_time_ms)) ? kEventSignaled
                                                       : kEventTimeout;
  }

 private:
  rtc::Event event_;
};

// static
EventWrapper* EventWrapper::Create() {
  return new EventWrapperImpl();
}

}  // namespace webrtc
