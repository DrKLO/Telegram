/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_VIDEO_CAPTURE_LINUX_VIDEO_CAPTURE_V4L2_H_
#define MODULES_VIDEO_CAPTURE_LINUX_VIDEO_CAPTURE_V4L2_H_

#include <stddef.h>
#include <stdint.h>

#include <memory>

#include "modules/video_capture/video_capture_defines.h"
#include "modules/video_capture/video_capture_impl.h"
#include "rtc_base/platform_thread.h"
#include "rtc_base/synchronization/mutex.h"

namespace webrtc {
namespace videocapturemodule {
class VideoCaptureModuleV4L2 : public VideoCaptureImpl {
 public:
  VideoCaptureModuleV4L2();
  ~VideoCaptureModuleV4L2() override;
  int32_t Init(const char* deviceUniqueId);
  int32_t StartCapture(const VideoCaptureCapability& capability) override;
  int32_t StopCapture() override;
  bool CaptureStarted() override;
  int32_t CaptureSettings(VideoCaptureCapability& settings) override;

 private:
  enum { kNoOfV4L2Bufffers = 4 };

  static void CaptureThread(void*);
  bool CaptureProcess();
  bool AllocateVideoBuffers();
  bool DeAllocateVideoBuffers();

  rtc::PlatformThread _captureThread;
  Mutex capture_lock_;
  bool quit_ RTC_GUARDED_BY(capture_lock_);
  int32_t _deviceId;
  int32_t _deviceFd;

  int32_t _buffersAllocatedByDevice;
  int32_t _currentWidth;
  int32_t _currentHeight;
  int32_t _currentFrameRate;
  bool _captureStarted;
  VideoType _captureVideoType;
  struct Buffer {
    void* start;
    size_t length;
  };
  Buffer* _pool;
};
}  // namespace videocapturemodule
}  // namespace webrtc

#endif  // MODULES_VIDEO_CAPTURE_LINUX_VIDEO_CAPTURE_V4L2_H_
