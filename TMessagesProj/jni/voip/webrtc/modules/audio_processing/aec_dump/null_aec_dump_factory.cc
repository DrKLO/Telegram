/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "absl/base/nullability.h"
#include "absl/strings/string_view.h"
#include "api/task_queue/task_queue_base.h"
#include "modules/audio_processing/aec_dump/aec_dump_factory.h"
#include "modules/audio_processing/include/aec_dump.h"

namespace webrtc {

absl::Nullable<std::unique_ptr<AecDump>> AecDumpFactory::Create(
    FileWrapper file,
    int64_t max_log_size_bytes,
    absl::Nonnull<TaskQueueBase*> worker_queue) {
  return nullptr;
}

absl::Nullable<std::unique_ptr<AecDump>> AecDumpFactory::Create(
    absl::string_view file_name,
    int64_t max_log_size_bytes,
    absl::Nonnull<TaskQueueBase*> worker_queue) {
  return nullptr;
}

absl::Nullable<std::unique_ptr<AecDump>> AecDumpFactory::Create(
    absl::Nonnull<FILE*> handle,
    int64_t max_log_size_bytes,
    absl::Nonnull<TaskQueueBase*> worker_queue) {
  return nullptr;
}
}  // namespace webrtc
