/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_device/dummy/file_audio_device_factory.h"

#include <stdio.h>

#include <cstdlib>

#include "modules/audio_device/dummy/file_audio_device.h"
#include "rtc_base/logging.h"

namespace webrtc {

bool FileAudioDeviceFactory::_isConfigured = false;
char FileAudioDeviceFactory::_inputAudioFilename[MAX_FILENAME_LEN] = "";
char FileAudioDeviceFactory::_outputAudioFilename[MAX_FILENAME_LEN] = "";

FileAudioDevice* FileAudioDeviceFactory::CreateFileAudioDevice() {
  // Bail out here if the files haven't been set explicitly.
  // audio_device_impl.cc should then fall back to dummy audio.
  if (!_isConfigured) {
    RTC_LOG(LS_WARNING)
        << "WebRTC configured with WEBRTC_DUMMY_FILE_DEVICES but "
           "no device files supplied. Will fall back to dummy "
           "audio.";

    return nullptr;
  }
  return new FileAudioDevice(_inputAudioFilename, _outputAudioFilename);
}

void FileAudioDeviceFactory::SetFilenamesToUse(
    const char* inputAudioFilename,
    const char* outputAudioFilename) {
#ifdef WEBRTC_DUMMY_FILE_DEVICES
  RTC_DCHECK_LT(strlen(inputAudioFilename), MAX_FILENAME_LEN);
  RTC_DCHECK_LT(strlen(outputAudioFilename), MAX_FILENAME_LEN);

  // Copy the strings since we don't know the lifetime of the input pointers.
  strncpy(_inputAudioFilename, inputAudioFilename, MAX_FILENAME_LEN);
  strncpy(_outputAudioFilename, outputAudioFilename, MAX_FILENAME_LEN);
  _isConfigured = true;
#else
  // Sanity: must be compiled with the right define to run this.
  printf(
      "Trying to use dummy file devices, but is not compiled "
      "with WEBRTC_DUMMY_FILE_DEVICES. Bailing out.\n");
  std::exit(1);
#endif
}

}  // namespace webrtc
