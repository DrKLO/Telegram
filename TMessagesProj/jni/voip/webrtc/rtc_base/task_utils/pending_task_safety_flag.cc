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

namespace webrtc {

// static
rtc::scoped_refptr<PendingTaskSafetyFlag> PendingTaskSafetyFlag::CreateInternal(
    bool alive) {
  // Explicit new, to access private constructor.
  return rtc::scoped_refptr<PendingTaskSafetyFlag>(
      new PendingTaskSafetyFlag(alive));
}

// static
rtc::scoped_refptr<PendingTaskSafetyFlag> PendingTaskSafetyFlag::Create() {
  return CreateInternal(true);
}

rtc::scoped_refptr<PendingTaskSafetyFlag>
PendingTaskSafetyFlag::CreateDetached() {
  rtc::scoped_refptr<PendingTaskSafetyFlag> safety_flag = CreateInternal(true);
  safety_flag->main_sequence_.Detach();
  return safety_flag;
}

rtc::scoped_refptr<PendingTaskSafetyFlag>
PendingTaskSafetyFlag::CreateDetachedInactive() {
  rtc::scoped_refptr<PendingTaskSafetyFlag> safety_flag = CreateInternal(false);
  safety_flag->main_sequence_.Detach();
  return safety_flag;
}

void PendingTaskSafetyFlag::SetNotAlive() {
  RTC_DCHECK_RUN_ON(&main_sequence_);
  alive_ = false;
}

void PendingTaskSafetyFlag::SetAlive() {
  RTC_DCHECK_RUN_ON(&main_sequence_);
  alive_ = true;
}

bool PendingTaskSafetyFlag::alive() const {
  RTC_DCHECK_RUN_ON(&main_sequence_);
  return alive_;
}

}  // namespace webrtc
