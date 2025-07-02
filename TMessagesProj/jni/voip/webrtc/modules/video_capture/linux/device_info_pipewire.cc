/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_capture/linux/device_info_pipewire.h"

#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <unistd.h>

#include <vector>

#include "modules/video_capture/linux/pipewire_session.h"
#include "modules/video_capture/video_capture.h"
#include "modules/video_capture/video_capture_defines.h"
#include "modules/video_capture/video_capture_impl.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace videocapturemodule {
DeviceInfoPipeWire::DeviceInfoPipeWire(VideoCaptureOptions* options)
    : DeviceInfoImpl(), pipewire_session_(options->pipewire_session()) {}

int32_t DeviceInfoPipeWire::Init() {
  return 0;
}

DeviceInfoPipeWire::~DeviceInfoPipeWire() = default;

uint32_t DeviceInfoPipeWire::NumberOfDevices() {
  RTC_CHECK(pipewire_session_);

  return pipewire_session_->nodes().size();
}

int32_t DeviceInfoPipeWire::GetDeviceName(uint32_t deviceNumber,
                                          char* deviceNameUTF8,
                                          uint32_t deviceNameLength,
                                          char* deviceUniqueIdUTF8,
                                          uint32_t deviceUniqueIdUTF8Length,
                                          char* productUniqueIdUTF8,
                                          uint32_t productUniqueIdUTF8Length) {
  RTC_CHECK(pipewire_session_);

  if (deviceNumber >= NumberOfDevices())
    return -1;

  const PipeWireNode& node = pipewire_session_->nodes().at(deviceNumber);

  if (deviceNameLength <= node.display_name().length()) {
    RTC_LOG(LS_INFO) << "deviceNameUTF8 buffer passed is too small";
    return -1;
  }
  if (deviceUniqueIdUTF8Length <= node.unique_id().length()) {
    RTC_LOG(LS_INFO) << "deviceUniqueIdUTF8 buffer passed is too small";
    return -1;
  }
  if (productUniqueIdUTF8 &&
      productUniqueIdUTF8Length <= node.model_id().length()) {
    RTC_LOG(LS_INFO) << "productUniqueIdUTF8 buffer passed is too small";
    return -1;
  }

  memset(deviceNameUTF8, 0, deviceNameLength);
  node.display_name().copy(deviceNameUTF8, deviceNameLength);

  memset(deviceUniqueIdUTF8, 0, deviceUniqueIdUTF8Length);
  node.unique_id().copy(deviceUniqueIdUTF8, deviceUniqueIdUTF8Length);

  if (productUniqueIdUTF8) {
    memset(productUniqueIdUTF8, 0, productUniqueIdUTF8Length);
    node.model_id().copy(productUniqueIdUTF8, productUniqueIdUTF8Length);
  }

  return 0;
}

int32_t DeviceInfoPipeWire::CreateCapabilityMap(
    const char* deviceUniqueIdUTF8) {
  RTC_CHECK(pipewire_session_);

  for (auto& node : pipewire_session_->nodes()) {
    if (node.unique_id().compare(deviceUniqueIdUTF8) != 0)
      continue;

    _captureCapabilities = node.capabilities();
    _lastUsedDeviceNameLength = node.display_name().length();
    _lastUsedDeviceName = static_cast<char*>(
        realloc(_lastUsedDeviceName, _lastUsedDeviceNameLength + 1));
    memcpy(_lastUsedDeviceName, deviceUniqueIdUTF8,
           _lastUsedDeviceNameLength + 1);
    return _captureCapabilities.size();
  }
  return -1;
}

int32_t DeviceInfoPipeWire::DisplayCaptureSettingsDialogBox(
    const char* /*deviceUniqueIdUTF8*/,
    const char* /*dialogTitleUTF8*/,
    void* /*parentWindow*/,
    uint32_t /*positionX*/,
    uint32_t /*positionY*/) {
  return -1;
}

}  // namespace videocapturemodule
}  // namespace webrtc
