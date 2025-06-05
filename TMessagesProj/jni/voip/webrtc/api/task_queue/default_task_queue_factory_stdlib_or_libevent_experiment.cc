/*
 *  Copyright 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <memory>

#include "api/field_trials_view.h"
#include "api/task_queue/task_queue_factory.h"
#include "api/transport/field_trial_based_config.h"
#include "rtc_base/logging.h"
#include "rtc_base/memory/always_valid_pointer.h"
#include "rtc_base/task_queue_libevent.h"
#include "rtc_base/task_queue_stdlib.h"

namespace webrtc {

std::unique_ptr<TaskQueueFactory> CreateDefaultTaskQueueFactory(
    const FieldTrialsView* field_trials_view) {
  AlwaysValidPointer<const FieldTrialsView, FieldTrialBasedConfig> field_trials(
      field_trials_view);
  if (field_trials->IsEnabled("WebRTC-TaskQueue-ReplaceLibeventWithStdlib")) {
    RTC_LOG(LS_INFO) << "WebRTC-TaskQueue-ReplaceLibeventWithStdlib: "
                     << "using TaskQueueStdlibFactory.";
    return CreateTaskQueueStdlibFactory();
  }

  RTC_LOG(LS_INFO) << "WebRTC-TaskQueue-ReplaceLibeventWithStdlib: "
                   << "using TaskQueueLibeventFactory.";
  return CreateTaskQueueLibeventFactory();
}

}  // namespace webrtc
