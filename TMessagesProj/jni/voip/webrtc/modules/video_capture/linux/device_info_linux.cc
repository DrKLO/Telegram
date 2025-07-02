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
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <unistd.h>
// v4l includes
#include <linux/videodev2.h>

#include <vector>

#if defined(WEBRTC_USE_PIPEWIRE)
#include "modules/video_capture/linux/device_info_pipewire.h"
#endif
#include "modules/video_capture/linux/device_info_v4l2.h"
#include "modules/video_capture/video_capture.h"
#include "modules/video_capture/video_capture_defines.h"
#include "modules/video_capture/video_capture_impl.h"
#include "modules/video_capture/video_capture_options.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace videocapturemodule {
VideoCaptureModule::DeviceInfo* VideoCaptureImpl::CreateDeviceInfo() {
  return new videocapturemodule::DeviceInfoV4l2();
}

VideoCaptureModule::DeviceInfo* VideoCaptureImpl::CreateDeviceInfo(
    VideoCaptureOptions* options) {
#if defined(WEBRTC_USE_PIPEWIRE)
  if (options->allow_pipewire()) {
    return new videocapturemodule::DeviceInfoPipeWire(options);
  }
#endif
  if (options->allow_v4l2())
    return new videocapturemodule::DeviceInfoV4l2();

  return nullptr;
}
}  // namespace videocapturemodule
}  // namespace webrtc
