/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AEC_DUMP_WRITE_TO_FILE_TASK_H_
#define MODULES_AUDIO_PROCESSING_AEC_DUMP_WRITE_TO_FILE_TASK_H_

#include <memory>
#include <string>
#include <utility>

#include "api/task_queue/queued_task.h"
#include "rtc_base/checks.h"
#include "rtc_base/event.h"
#include "rtc_base/ignore_wundef.h"
#include "rtc_base/system/file_wrapper.h"

// Files generated at build-time by the protobuf compiler.
RTC_PUSH_IGNORING_WUNDEF()
#ifdef WEBRTC_ANDROID_PLATFORM_BUILD
#include "external/webrtc/webrtc/modules/audio_processing/debug.pb.h"
#else
#include "modules/audio_processing/debug.pb.h"
#endif
RTC_POP_IGNORING_WUNDEF()

namespace webrtc {

class WriteToFileTask : public QueuedTask {
 public:
  WriteToFileTask(webrtc::FileWrapper* debug_file,
                  int64_t* num_bytes_left_for_log);
  ~WriteToFileTask() override;

  audioproc::Event* GetEvent();

 private:
  bool IsRoomForNextEvent(size_t event_byte_size) const;

  void UpdateBytesLeft(size_t event_byte_size);

  bool Run() override;

  webrtc::FileWrapper* const debug_file_;
  audioproc::Event event_;
  int64_t* const num_bytes_left_for_log_;
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AEC_DUMP_WRITE_TO_FILE_TASK_H_
