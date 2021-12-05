/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_device/audio_device_name.h"

#include <utility>

namespace webrtc {

const char AudioDeviceName::kDefaultDeviceId[] = "default";

AudioDeviceName::AudioDeviceName(std::string device_name, std::string unique_id)
    : device_name(std::move(device_name)), unique_id(std::move(unique_id)) {}

bool AudioDeviceName::IsValid() {
  return !device_name.empty() && !unique_id.empty();
}

}  // namespace webrtc
