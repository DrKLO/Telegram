/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_capture/video_capture_factory.h"

#include "modules/video_capture/video_capture_impl.h"

namespace webrtc {

rtc::scoped_refptr<VideoCaptureModule> VideoCaptureFactory::Create(
    const char* deviceUniqueIdUTF8) {
#if defined(WEBRTC_ANDROID) || defined(WEBRTC_MAC)
  return nullptr;
#else
  return videocapturemodule::VideoCaptureImpl::Create(deviceUniqueIdUTF8);
#endif
}

rtc::scoped_refptr<VideoCaptureModule> VideoCaptureFactory::Create(
    VideoCaptureOptions* options,
    const char* deviceUniqueIdUTF8) {
// This is only implemented on pure Linux and WEBRTC_LINUX is defined for
// Android as well
#if !defined(WEBRTC_LINUX) || defined(WEBRTC_ANDROID)
  return nullptr;
#else
  return videocapturemodule::VideoCaptureImpl::Create(options,
                                                      deviceUniqueIdUTF8);
#endif
}

VideoCaptureModule::DeviceInfo* VideoCaptureFactory::CreateDeviceInfo() {
#if defined(WEBRTC_ANDROID) || defined(WEBRTC_MAC)
  return nullptr;
#else
  return videocapturemodule::VideoCaptureImpl::CreateDeviceInfo();
#endif
}

VideoCaptureModule::DeviceInfo* VideoCaptureFactory::CreateDeviceInfo(
    VideoCaptureOptions* options) {
// This is only implemented on pure Linux and WEBRTC_LINUX is defined for
// Android as well
#if !defined(WEBRTC_LINUX) || defined(WEBRTC_ANDROID)
  return nullptr;
#else
  return videocapturemodule::VideoCaptureImpl::CreateDeviceInfo(options);
#endif
}

}  // namespace webrtc
