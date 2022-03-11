/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef VIDEO_FRAME_BUFFER_PROXY_H_
#define VIDEO_FRAME_BUFFER_PROXY_H_

#include <memory>

#include "api/metronome/metronome.h"
#include "api/task_queue/task_queue_base.h"
#include "api/video/encoded_frame.h"
#include "modules/video_coding/include/video_coding_defines.h"
#include "modules/video_coding/timing.h"
#include "rtc_base/task_queue.h"
#include "system_wrappers/include/clock.h"
#include "video/decode_synchronizer.h"

namespace webrtc {

class FrameSchedulingReceiver {
 public:
  virtual ~FrameSchedulingReceiver() = default;

  virtual void OnEncodedFrame(std::unique_ptr<EncodedFrame> frame) = 0;
  virtual void OnDecodableFrameTimeout(TimeDelta wait_time) = 0;
};

// Temporary class to enable replacement of frame_buffer2 with frame_buffer3.
// Once frame_buffer3 has shown to work with a field trial, frame_buffer2 will
// be removed and this class should be directly integrated into
// video_receive_stream2. bugs.webrtc.org/13343 tracks this integration.
class FrameBufferProxy {
 public:
  static std::unique_ptr<FrameBufferProxy> CreateFromFieldTrial(
      Clock* clock,
      TaskQueueBase* worker_queue,
      VCMTiming* timing,
      VCMReceiveStatisticsCallback* stats_proxy,
      rtc::TaskQueue* decode_queue,
      FrameSchedulingReceiver* receiver,
      TimeDelta max_wait_for_keyframe,
      TimeDelta max_wait_for_frame,
      DecodeSynchronizer* decode_sync);
  virtual ~FrameBufferProxy() = default;

  // Run on the worker thread.
  virtual void StopOnWorker() = 0;
  virtual void SetProtectionMode(VCMVideoProtection protection_mode) = 0;
  virtual void Clear() = 0;
  virtual absl::optional<int64_t> InsertFrame(
      std::unique_ptr<EncodedFrame> frame) = 0;
  virtual void UpdateRtt(int64_t max_rtt_ms) = 0;
  virtual int Size() = 0;

  // Run on either the worker thread or the decode thread.
  virtual void StartNextDecode(bool keyframe_required) = 0;
};

}  // namespace webrtc

#endif  // VIDEO_FRAME_BUFFER_PROXY_H_
