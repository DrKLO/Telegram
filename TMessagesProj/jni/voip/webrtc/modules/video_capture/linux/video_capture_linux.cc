/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <errno.h>
#include <fcntl.h>
#include <linux/videodev2.h>
#include <stdio.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/select.h>
#include <time.h>
#include <unistd.h>

#include <new>
#include <string>

#include "api/scoped_refptr.h"
#include "media/base/video_common.h"
#if defined(WEBRTC_USE_PIPEWIRE)
#include "modules/video_capture/linux/video_capture_pipewire.h"
#endif
#include "modules/video_capture/linux/video_capture_v4l2.h"
#include "modules/video_capture/video_capture.h"
#include "modules/video_capture/video_capture_options.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace videocapturemodule {
rtc::scoped_refptr<VideoCaptureModule> VideoCaptureImpl::Create(
    const char* deviceUniqueId) {
  auto implementation = rtc::make_ref_counted<VideoCaptureModuleV4L2>();

  if (implementation->Init(deviceUniqueId) != 0)
    return nullptr;

  return implementation;
}

rtc::scoped_refptr<VideoCaptureModule> VideoCaptureImpl::Create(
    VideoCaptureOptions* options,
    const char* deviceUniqueId) {
#if defined(WEBRTC_USE_PIPEWIRE)
  if (options->allow_pipewire()) {
    auto implementation =
        rtc::make_ref_counted<VideoCaptureModulePipeWire>(options);

    if (implementation->Init(deviceUniqueId) == 0)
      return implementation;
  }
#endif
  if (options->allow_v4l2()) {
    auto implementation = rtc::make_ref_counted<VideoCaptureModuleV4L2>();

    if (implementation->Init(deviceUniqueId) == 0)
      return implementation;
  }
  return nullptr;
}
}  // namespace videocapturemodule
}  // namespace webrtc
