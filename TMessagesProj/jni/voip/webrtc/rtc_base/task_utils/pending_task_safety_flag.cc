/*
 *  Copyright 2020 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/task_utils/pending_task_safety_flag.h"

#include "rtc_base/ref_counted_object.h"

namespace webrtc {

// static
rtc::scoped_refptr<PendingTaskSafetyFlag> PendingTaskSafetyFlag::Create() {
  return new rtc::RefCountedObject<PendingTaskSafetyFlag>();
}

void PendingTaskSafetyFlag::SetNotAlive() {
  RTC_DCHECK_RUN_ON(&main_sequence_);
  alive_ = false;
}

bool PendingTaskSafetyFlag::alive() const {
  RTC_DCHECK_RUN_ON(&main_sequence_);
  return alive_;
}

}  // namespace webrtc
