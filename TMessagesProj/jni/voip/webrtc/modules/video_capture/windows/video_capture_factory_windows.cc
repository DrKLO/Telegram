/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/scoped_refptr.h"
#include "modules/video_capture/windows/video_capture_ds.h"

namespace webrtc {
namespace videocapturemodule {

// static
VideoCaptureModule::DeviceInfo* VideoCaptureImpl::CreateDeviceInfo() {
  // TODO(tommi): Use the Media Foundation version on Vista and up.
  return DeviceInfoDS::Create();
}

rtc::scoped_refptr<VideoCaptureModule> VideoCaptureImpl::Create(
    const char* device_id) {
  if (device_id == nullptr)
    return nullptr;

  // TODO(tommi): Use Media Foundation implementation for Vista and up.
  auto capture = rtc::make_ref_counted<VideoCaptureDS>();
  if (capture->Init(device_id) != 0) {
    return nullptr;
  }

  return capture;
}

}  // namespace videocapturemodule
}  // namespace webrtc
