/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_capture/video_capture_impl.h"

namespace webrtc {
namespace videocapturemodule {

// static
VideoCaptureModule::DeviceInfo* VideoCaptureImpl::CreateDeviceInfo() {
  return nullptr;
}

rtc::scoped_refptr<VideoCaptureModule> VideoCaptureImpl::Create(
    const char* device_id) {
  return nullptr;
}

}  // namespace videocapturemodule
}  // namespace webrtc
