/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "media/engine/adm_helpers.h"

#include "modules/audio_device/include/audio_device.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace adm_helpers {

// On Windows Vista and newer, Microsoft introduced the concept of "Default
// Communications Device". This means that there are two types of default
// devices (old Wave Audio style default and Default Communications Device).
//
// On Windows systems which only support Wave Audio style default, uses either
// -1 or 0 to select the default device.
//
// Using a #define for AUDIO_DEVICE since we will call *different* versions of
// the ADM functions, depending on the ID type.
#if defined(WEBRTC_WIN)
#define AUDIO_DEVICE_ID \
  (AudioDeviceModule::WindowsDeviceType::kDefaultCommunicationDevice)
#else
#define AUDIO_DEVICE_ID (0u)
#endif  // defined(WEBRTC_WIN)

void Init(AudioDeviceModule* adm) {
  RTC_DCHECK(adm);

  RTC_CHECK_EQ(0, adm->Init()) << "Failed to initialize the ADM.";

  // Playout device.
  {
    if (adm->SetPlayoutDevice(AUDIO_DEVICE_ID) != 0) {
      RTC_LOG(LS_ERROR) << "Unable to set playout device.";
      return;
    }
    if (adm->InitSpeaker() != 0) {
      RTC_LOG(LS_ERROR) << "Unable to access speaker.";
    }

    // Set number of channels
    bool available = false;
    if (adm->StereoPlayoutIsAvailable(&available) != 0) {
      RTC_LOG(LS_ERROR) << "Failed to query stereo playout.";
    }
    if (adm->SetStereoPlayout(available) != 0) {
      RTC_LOG(LS_ERROR) << "Failed to set stereo playout mode.";
    }
  }

  // Recording device.
  {
    if (adm->SetRecordingDevice(AUDIO_DEVICE_ID) != 0) {
      RTC_LOG(LS_ERROR) << "Unable to set recording device.";
      return;
    }
    if (adm->InitMicrophone() != 0) {
      RTC_LOG(LS_ERROR) << "Unable to access microphone.";
    }

    // Set number of channels
    bool available = false;
    if (adm->StereoRecordingIsAvailable(&available) != 0) {
      RTC_LOG(LS_ERROR) << "Failed to query stereo recording.";
    }
    if (adm->SetStereoRecording(available) != 0) {
      RTC_LOG(LS_ERROR) << "Failed to set stereo recording mode.";
    }
  }
}
}  // namespace adm_helpers
}  // namespace webrtc
