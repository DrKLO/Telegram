/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_device/linux/audio_mixer_manager_alsa_linux.h"

#include "modules/audio_device/linux/audio_device_alsa_linux.h"
#include "rtc_base/logging.h"

// Accesses ALSA functions through our late-binding symbol table instead of
// directly. This way we don't have to link to libasound, which means our binary
// will work on systems that don't have it.
#define LATE(sym)                                                            \
  LATESYM_GET(webrtc::adm_linux_alsa::AlsaSymbolTable, GetAlsaSymbolTable(), \
              sym)

namespace webrtc {

AudioMixerManagerLinuxALSA::AudioMixerManagerLinuxALSA()
    : _outputMixerHandle(NULL),
      _inputMixerHandle(NULL),
      _outputMixerElement(NULL),
      _inputMixerElement(NULL) {
  RTC_LOG(LS_INFO) << __FUNCTION__ << " created";

  memset(_outputMixerStr, 0, kAdmMaxDeviceNameSize);
  memset(_inputMixerStr, 0, kAdmMaxDeviceNameSize);
}

AudioMixerManagerLinuxALSA::~AudioMixerManagerLinuxALSA() {
  RTC_LOG(LS_INFO) << __FUNCTION__ << " destroyed";
  Close();
}

// ============================================================================
//                                    PUBLIC METHODS
// ============================================================================

int32_t AudioMixerManagerLinuxALSA::Close() {
  RTC_LOG(LS_VERBOSE) << __FUNCTION__;

  MutexLock lock(&mutex_);

  CloseSpeakerLocked();
  CloseMicrophoneLocked();

  return 0;
}

int32_t AudioMixerManagerLinuxALSA::CloseSpeaker() {
  MutexLock lock(&mutex_);
  return CloseSpeakerLocked();
}

int32_t AudioMixerManagerLinuxALSA::CloseSpeakerLocked() {
  RTC_LOG(LS_VERBOSE) << __FUNCTION__;

  int errVal = 0;

  if (_outputMixerHandle != NULL) {
    RTC_LOG(LS_VERBOSE) << "Closing playout mixer";
    LATE(snd_mixer_free)(_outputMixerHandle);
    if (errVal < 0) {
      RTC_LOG(LS_ERROR) << "Error freeing playout mixer: "
                        << LATE(snd_strerror)(errVal);
    }
    errVal = LATE(snd_mixer_detach)(_outputMixerHandle, _outputMixerStr);
    if (errVal < 0) {
      RTC_LOG(LS_ERROR) << "Error detaching playout mixer: "
                        << LATE(snd_strerror)(errVal);
    }
    errVal = LATE(snd_mixer_close)(_outputMixerHandle);
    if (errVal < 0) {
      RTC_LOG(LS_ERROR) << "Error snd_mixer_close(handleMixer) errVal="
                        << errVal;
    }
    _outputMixerHandle = NULL;
    _outputMixerElement = NULL;
  }
  memset(_outputMixerStr, 0, kAdmMaxDeviceNameSize);

  return 0;
}

int32_t AudioMixerManagerLinuxALSA::CloseMicrophone() {
  MutexLock lock(&mutex_);
  return CloseMicrophoneLocked();
}

int32_t AudioMixerManagerLinuxALSA::CloseMicrophoneLocked() {
  RTC_LOG(LS_VERBOSE) << __FUNCTION__;

  int errVal = 0;

  if (_inputMixerHandle != NULL) {
    RTC_LOG(LS_VERBOSE) << "Closing record mixer";

    LATE(snd_mixer_free)(_inputMixerHandle);
    if (errVal < 0) {
      RTC_LOG(LS_ERROR) << "Error freeing record mixer: "
                        << LATE(snd_strerror)(errVal);
    }
    RTC_LOG(LS_VERBOSE) << "Closing record mixer 2";

    errVal = LATE(snd_mixer_detach)(_inputMixerHandle, _inputMixerStr);
    if (errVal < 0) {
      RTC_LOG(LS_ERROR) << "Error detaching record mixer: "
                        << LATE(snd_strerror)(errVal);
    }
    RTC_LOG(LS_VERBOSE) << "Closing record mixer 3";

    errVal = LATE(snd_mixer_close)(_inputMixerHandle);
    if (errVal < 0) {
      RTC_LOG(LS_ERROR) << "Error snd_mixer_close(handleMixer) errVal="
                        << errVal;
    }

    RTC_LOG(LS_VERBOSE) << "Closing record mixer 4";
    _inputMixerHandle = NULL;
    _inputMixerElement = NULL;
  }
  memset(_inputMixerStr, 0, kAdmMaxDeviceNameSize);

  return 0;
}

int32_t AudioMixerManagerLinuxALSA::OpenSpeaker(char* deviceName) {
  RTC_LOG(LS_VERBOSE) << "AudioMixerManagerLinuxALSA::OpenSpeaker(name="
                      << deviceName << ")";

  MutexLock lock(&mutex_);

  int errVal = 0;

  // Close any existing output mixer handle
  //
  if (_outputMixerHandle != NULL) {
    RTC_LOG(LS_VERBOSE) << "Closing playout mixer";

    LATE(snd_mixer_free)(_outputMixerHandle);
    if (errVal < 0) {
      RTC_LOG(LS_ERROR) << "Error freeing playout mixer: "
                        << LATE(snd_strerror)(errVal);
    }
    errVal = LATE(snd_mixer_detach)(_outputMixerHandle, _outputMixerStr);
    if (errVal < 0) {
      RTC_LOG(LS_ERROR) << "Error detaching playout mixer: "
                        << LATE(snd_strerror)(errVal);
    }
    errVal = LATE(snd_mixer_close)(_outputMixerHandle);
    if (errVal < 0) {
      RTC_LOG(LS_ERROR) << "Error snd_mixer_close(handleMixer) errVal="
                        << errVal;
    }
  }
  _outputMixerHandle = NULL;
  _outputMixerElement = NULL;

  errVal = LATE(snd_mixer_open)(&_outputMixerHandle, 0);
  if (errVal < 0) {
    RTC_LOG(LS_ERROR) << "snd_mixer_open(&_outputMixerHandle, 0) - error";
    return -1;
  }

  char controlName[kAdmMaxDeviceNameSize] = {0};
  GetControlName(controlName, deviceName);

  RTC_LOG(LS_VERBOSE) << "snd_mixer_attach(_outputMixerHandle, " << controlName
                      << ")";

  errVal = LATE(snd_mixer_attach)(_outputMixerHandle, controlName);
  if (errVal < 0) {
    RTC_LOG(LS_ERROR) << "snd_mixer_attach(_outputMixerHandle, " << controlName
                      << ") error: " << LATE(snd_strerror)(errVal);
    _outputMixerHandle = NULL;
    return -1;
  }
  strcpy(_outputMixerStr, controlName);

  errVal = LATE(snd_mixer_selem_register)(_outputMixerHandle, NULL, NULL);
  if (errVal < 0) {
    RTC_LOG(LS_ERROR)
        << "snd_mixer_selem_register(_outputMixerHandle, NULL, NULL), "
           "error: "
        << LATE(snd_strerror)(errVal);
    _outputMixerHandle = NULL;
    return -1;
  }

  // Load and find the proper mixer element
  if (LoadSpeakerMixerElement() < 0) {
    return -1;
  }

  if (_outputMixerHandle != NULL) {
    RTC_LOG(LS_VERBOSE) << "the output mixer device is now open ("
                        << _outputMixerHandle << ")";
  }

  return 0;
}

int32_t AudioMixerManagerLinuxALSA::OpenMicrophone(char* deviceName) {
  RTC_LOG(LS_VERBOSE) << "AudioMixerManagerLinuxALSA::OpenMicrophone(name="
                      << deviceName << ")";

  MutexLock lock(&mutex_);

  int errVal = 0;

  // Close any existing input mixer handle
  //
  if (_inputMixerHandle != NULL) {
    RTC_LOG(LS_VERBOSE) << "Closing record mixer";

    LATE(snd_mixer_free)(_inputMixerHandle);
    if (errVal < 0) {
      RTC_LOG(LS_ERROR) << "Error freeing record mixer: "
                        << LATE(snd_strerror)(errVal);
    }
    RTC_LOG(LS_VERBOSE) << "Closing record mixer";

    errVal = LATE(snd_mixer_detach)(_inputMixerHandle, _inputMixerStr);
    if (errVal < 0) {
      RTC_LOG(LS_ERROR) << "Error detaching record mixer: "
                        << LATE(snd_strerror)(errVal);
    }
    RTC_LOG(LS_VERBOSE) << "Closing record mixer";

    errVal = LATE(snd_mixer_close)(_inputMixerHandle);
    if (errVal < 0) {
      RTC_LOG(LS_ERROR) << "Error snd_mixer_close(handleMixer) errVal="
                        << errVal;
    }
    RTC_LOG(LS_VERBOSE) << "Closing record mixer";
  }
  _inputMixerHandle = NULL;
  _inputMixerElement = NULL;

  errVal = LATE(snd_mixer_open)(&_inputMixerHandle, 0);
  if (errVal < 0) {
    RTC_LOG(LS_ERROR) << "snd_mixer_open(&_inputMixerHandle, 0) - error";
    return -1;
  }

  char controlName[kAdmMaxDeviceNameSize] = {0};
  GetControlName(controlName, deviceName);

  RTC_LOG(LS_VERBOSE) << "snd_mixer_attach(_inputMixerHandle, " << controlName
                      << ")";

  errVal = LATE(snd_mixer_attach)(_inputMixerHandle, controlName);
  if (errVal < 0) {
    RTC_LOG(LS_ERROR) << "snd_mixer_attach(_inputMixerHandle, " << controlName
                      << ") error: " << LATE(snd_strerror)(errVal);

    _inputMixerHandle = NULL;
    return -1;
  }
  strcpy(_inputMixerStr, controlName);

  errVal = LATE(snd_mixer_selem_register)(_inputMixerHandle, NULL, NULL);
  if (errVal < 0) {
    RTC_LOG(LS_ERROR)
        << "snd_mixer_selem_register(_inputMixerHandle, NULL, NULL), "
           "error: "
        << LATE(snd_strerror)(errVal);

    _inputMixerHandle = NULL;
    return -1;
  }
  // Load and find the proper mixer element
  if (LoadMicMixerElement() < 0) {
    return -1;
  }

  if (_inputMixerHandle != NULL) {
    RTC_LOG(LS_VERBOSE) << "the input mixer device is now open ("
                        << _inputMixerHandle << ")";
  }

  return 0;
}

bool AudioMixerManagerLinuxALSA::SpeakerIsInitialized() const {
  RTC_LOG(LS_INFO) << __FUNCTION__;

  return (_outputMixerHandle != NULL);
}

bool AudioMixerManagerLinuxALSA::MicrophoneIsInitialized() const {
  RTC_LOG(LS_INFO) << __FUNCTION__;

  return (_inputMixerHandle != NULL);
}

int32_t AudioMixerManagerLinuxALSA::SetSpeakerVolume(uint32_t volume) {
  RTC_LOG(LS_VERBOSE) << "AudioMixerManagerLinuxALSA::SetSpeakerVolume(volume="
                      << volume << ")";

  MutexLock lock(&mutex_);

  if (_outputMixerElement == NULL) {
    RTC_LOG(LS_WARNING) << "no avaliable output mixer element exists";
    return -1;
  }

  int errVal = LATE(snd_mixer_selem_set_playback_volume_all)(
      _outputMixerElement, volume);
  if (errVal < 0) {
    RTC_LOG(LS_ERROR) << "Error changing master volume: "
                      << LATE(snd_strerror)(errVal);
    return -1;
  }

  return (0);
}

int32_t AudioMixerManagerLinuxALSA::SpeakerVolume(uint32_t& volume) const {
  if (_outputMixerElement == NULL) {
    RTC_LOG(LS_WARNING) << "no avaliable output mixer element exists";
    return -1;
  }

  long int vol(0);

  int errVal = LATE(snd_mixer_selem_get_playback_volume)(
      _outputMixerElement, (snd_mixer_selem_channel_id_t)0, &vol);
  if (errVal < 0) {
    RTC_LOG(LS_ERROR) << "Error getting outputvolume: "
                      << LATE(snd_strerror)(errVal);
    return -1;
  }
  RTC_LOG(LS_VERBOSE) << "AudioMixerManagerLinuxALSA::SpeakerVolume() => vol="
                      << vol;

  volume = static_cast<uint32_t>(vol);

  return 0;
}

int32_t AudioMixerManagerLinuxALSA::MaxSpeakerVolume(
    uint32_t& maxVolume) const {
  if (_outputMixerElement == NULL) {
    RTC_LOG(LS_WARNING) << "no avilable output mixer element exists";
    return -1;
  }

  long int minVol(0);
  long int maxVol(0);

  int errVal = LATE(snd_mixer_selem_get_playback_volume_range)(
      _outputMixerElement, &minVol, &maxVol);

  RTC_LOG(LS_VERBOSE) << "Playout hardware volume range, min: " << minVol
                      << ", max: " << maxVol;

  if (maxVol <= minVol) {
    RTC_LOG(LS_ERROR) << "Error getting get_playback_volume_range: "
                      << LATE(snd_strerror)(errVal);
  }

  maxVolume = static_cast<uint32_t>(maxVol);

  return 0;
}

int32_t AudioMixerManagerLinuxALSA::MinSpeakerVolume(
    uint32_t& minVolume) const {
  if (_outputMixerElement == NULL) {
    RTC_LOG(LS_WARNING) << "no avaliable output mixer element exists";
    return -1;
  }

  long int minVol(0);
  long int maxVol(0);

  int errVal = LATE(snd_mixer_selem_get_playback_volume_range)(
      _outputMixerElement, &minVol, &maxVol);

  RTC_LOG(LS_VERBOSE) << "Playout hardware volume range, min: " << minVol
                      << ", max: " << maxVol;

  if (maxVol <= minVol) {
    RTC_LOG(LS_ERROR) << "Error getting get_playback_volume_range: "
                      << LATE(snd_strerror)(errVal);
  }

  minVolume = static_cast<uint32_t>(minVol);

  return 0;
}

// TL: Have done testnig with these but they don't seem reliable and
// they were therefore not added
/*
 // ----------------------------------------------------------------------------
 //    SetMaxSpeakerVolume
 // ----------------------------------------------------------------------------

 int32_t AudioMixerManagerLinuxALSA::SetMaxSpeakerVolume(
     uint32_t maxVolume)
 {

 if (_outputMixerElement == NULL)
 {
 RTC_LOG(LS_WARNING) << "no avaliable output mixer element exists";
 return -1;
 }

 long int minVol(0);
 long int maxVol(0);

 int errVal = snd_mixer_selem_get_playback_volume_range(
 _outputMixerElement, &minVol, &maxVol);
 if ((maxVol <= minVol) || (errVal != 0))
 {
 RTC_LOG(LS_WARNING) << "Error getting playback volume range: "
                 << snd_strerror(errVal);
 }

 maxVol = maxVolume;
 errVal = snd_mixer_selem_set_playback_volume_range(
 _outputMixerElement, minVol, maxVol);
 RTC_LOG(LS_VERBOSE) << "Playout hardware volume range, min: " << minVol
                 << ", max: " << maxVol;
 if (errVal != 0)
 {
 RTC_LOG(LS_ERROR) << "Error setting playback volume range: "
               << snd_strerror(errVal);
 return -1;
 }

 return 0;
 }

 // ----------------------------------------------------------------------------
 //    SetMinSpeakerVolume
 // ----------------------------------------------------------------------------

 int32_t AudioMixerManagerLinuxALSA::SetMinSpeakerVolume(
     uint32_t minVolume)
 {

 if (_outputMixerElement == NULL)
 {
 RTC_LOG(LS_WARNING) << "no avaliable output mixer element exists";
 return -1;
 }

 long int minVol(0);
 long int maxVol(0);

 int errVal = snd_mixer_selem_get_playback_volume_range(
 _outputMixerElement, &minVol, &maxVol);
 if ((maxVol <= minVol) || (errVal != 0))
 {
 RTC_LOG(LS_WARNING) << "Error getting playback volume range: "
                 << snd_strerror(errVal);
 }

 minVol = minVolume;
 errVal = snd_mixer_selem_set_playback_volume_range(
 _outputMixerElement, minVol, maxVol);
 RTC_LOG(LS_VERBOSE) << "Playout hardware volume range, min: " << minVol
                 << ", max: " << maxVol;
 if (errVal != 0)
 {
 RTC_LOG(LS_ERROR) << "Error setting playback volume range: "
               << snd_strerror(errVal);
 return -1;
 }

 return 0;
 }
 */

int32_t AudioMixerManagerLinuxALSA::SpeakerVolumeIsAvailable(bool& available) {
  if (_outputMixerElement == NULL) {
    RTC_LOG(LS_WARNING) << "no avaliable output mixer element exists";
    return -1;
  }

  available = LATE(snd_mixer_selem_has_playback_volume)(_outputMixerElement);

  return 0;
}

int32_t AudioMixerManagerLinuxALSA::SpeakerMuteIsAvailable(bool& available) {
  if (_outputMixerElement == NULL) {
    RTC_LOG(LS_WARNING) << "no avaliable output mixer element exists";
    return -1;
  }

  available = LATE(snd_mixer_selem_has_playback_switch)(_outputMixerElement);

  return 0;
}

int32_t AudioMixerManagerLinuxALSA::SetSpeakerMute(bool enable) {
  RTC_LOG(LS_VERBOSE) << "AudioMixerManagerLinuxALSA::SetSpeakerMute(enable="
                      << enable << ")";

  MutexLock lock(&mutex_);

  if (_outputMixerElement == NULL) {
    RTC_LOG(LS_WARNING) << "no avaliable output mixer element exists";
    return -1;
  }

  // Ensure that the selected speaker destination has a valid mute control.
  bool available(false);
  SpeakerMuteIsAvailable(available);
  if (!available) {
    RTC_LOG(LS_WARNING) << "it is not possible to mute the speaker";
    return -1;
  }

  // Note value = 0 (off) means muted
  int errVal = LATE(snd_mixer_selem_set_playback_switch_all)(
      _outputMixerElement, !enable);
  if (errVal < 0) {
    RTC_LOG(LS_ERROR) << "Error setting playback switch: "
                      << LATE(snd_strerror)(errVal);
    return -1;
  }

  return (0);
}

int32_t AudioMixerManagerLinuxALSA::SpeakerMute(bool& enabled) const {
  if (_outputMixerElement == NULL) {
    RTC_LOG(LS_WARNING) << "no avaliable output mixer exists";
    return -1;
  }

  // Ensure that the selected speaker destination has a valid mute control.
  bool available =
      LATE(snd_mixer_selem_has_playback_switch)(_outputMixerElement);
  if (!available) {
    RTC_LOG(LS_WARNING) << "it is not possible to mute the speaker";
    return -1;
  }

  int value(false);

  // Retrieve one boolean control value for a specified mute-control
  //
  int errVal = LATE(snd_mixer_selem_get_playback_switch)(
      _outputMixerElement, (snd_mixer_selem_channel_id_t)0, &value);
  if (errVal < 0) {
    RTC_LOG(LS_ERROR) << "Error getting playback switch: "
                      << LATE(snd_strerror)(errVal);
    return -1;
  }

  // Note value = 0 (off) means muted
  enabled = (bool)!value;

  return 0;
}

int32_t AudioMixerManagerLinuxALSA::MicrophoneMuteIsAvailable(bool& available) {
  if (_inputMixerElement == NULL) {
    RTC_LOG(LS_WARNING) << "no avaliable input mixer element exists";
    return -1;
  }

  available = LATE(snd_mixer_selem_has_capture_switch)(_inputMixerElement);
  return 0;
}

int32_t AudioMixerManagerLinuxALSA::SetMicrophoneMute(bool enable) {
  RTC_LOG(LS_VERBOSE) << "AudioMixerManagerLinuxALSA::SetMicrophoneMute(enable="
                      << enable << ")";

  MutexLock lock(&mutex_);

  if (_inputMixerElement == NULL) {
    RTC_LOG(LS_WARNING) << "no avaliable input mixer element exists";
    return -1;
  }

  // Ensure that the selected microphone destination has a valid mute control.
  bool available(false);
  MicrophoneMuteIsAvailable(available);
  if (!available) {
    RTC_LOG(LS_WARNING) << "it is not possible to mute the microphone";
    return -1;
  }

  // Note value = 0 (off) means muted
  int errVal =
      LATE(snd_mixer_selem_set_capture_switch_all)(_inputMixerElement, !enable);
  if (errVal < 0) {
    RTC_LOG(LS_ERROR) << "Error setting capture switch: "
                      << LATE(snd_strerror)(errVal);
    return -1;
  }

  return (0);
}

int32_t AudioMixerManagerLinuxALSA::MicrophoneMute(bool& enabled) const {
  if (_inputMixerElement == NULL) {
    RTC_LOG(LS_WARNING) << "no avaliable input mixer exists";
    return -1;
  }

  // Ensure that the selected microphone destination has a valid mute control.
  bool available = LATE(snd_mixer_selem_has_capture_switch)(_inputMixerElement);
  if (!available) {
    RTC_LOG(LS_WARNING) << "it is not possible to mute the microphone";
    return -1;
  }

  int value(false);

  // Retrieve one boolean control value for a specified mute-control
  //
  int errVal = LATE(snd_mixer_selem_get_capture_switch)(
      _inputMixerElement, (snd_mixer_selem_channel_id_t)0, &value);
  if (errVal < 0) {
    RTC_LOG(LS_ERROR) << "Error getting capture switch: "
                      << LATE(snd_strerror)(errVal);
    return -1;
  }

  // Note value = 0 (off) means muted
  enabled = (bool)!value;

  return 0;
}

int32_t AudioMixerManagerLinuxALSA::MicrophoneVolumeIsAvailable(
    bool& available) {
  if (_inputMixerElement == NULL) {
    RTC_LOG(LS_WARNING) << "no avaliable input mixer element exists";
    return -1;
  }

  available = LATE(snd_mixer_selem_has_capture_volume)(_inputMixerElement);

  return 0;
}

int32_t AudioMixerManagerLinuxALSA::SetMicrophoneVolume(uint32_t volume) {
  RTC_LOG(LS_VERBOSE)
      << "AudioMixerManagerLinuxALSA::SetMicrophoneVolume(volume=" << volume
      << ")";

  MutexLock lock(&mutex_);

  if (_inputMixerElement == NULL) {
    RTC_LOG(LS_WARNING) << "no avaliable input mixer element exists";
    return -1;
  }

  int errVal =
      LATE(snd_mixer_selem_set_capture_volume_all)(_inputMixerElement, volume);
  if (errVal < 0) {
    RTC_LOG(LS_ERROR) << "Error changing microphone volume: "
                      << LATE(snd_strerror)(errVal);
    return -1;
  }

  return (0);
}

// TL: Have done testnig with these but they don't seem reliable and
// they were therefore not added
/*
 // ----------------------------------------------------------------------------
 //    SetMaxMicrophoneVolume
 // ----------------------------------------------------------------------------

 int32_t AudioMixerManagerLinuxALSA::SetMaxMicrophoneVolume(
     uint32_t maxVolume)
 {

 if (_inputMixerElement == NULL)
 {
 RTC_LOG(LS_WARNING) << "no avaliable output mixer element exists";
 return -1;
 }

 long int minVol(0);
 long int maxVol(0);

 int errVal = snd_mixer_selem_get_capture_volume_range(_inputMixerElement,
  &minVol, &maxVol);
 if ((maxVol <= minVol) || (errVal != 0))
 {
 RTC_LOG(LS_WARNING) << "Error getting capture volume range: "
                 << snd_strerror(errVal);
 }

 maxVol = (long int)maxVolume;
 printf("min %d max %d", minVol, maxVol);
 errVal = snd_mixer_selem_set_capture_volume_range(_inputMixerElement, minVol,
 maxVol); RTC_LOG(LS_VERBOSE) << "Capture hardware volume range, min: " <<
 minVol
                 << ", max: " << maxVol;
 if (errVal != 0)
 {
 RTC_LOG(LS_ERROR) << "Error setting capture volume range: "
               << snd_strerror(errVal);
 return -1;
 }

 return 0;
 }

 // ----------------------------------------------------------------------------
 //    SetMinMicrophoneVolume
 // ----------------------------------------------------------------------------

 int32_t AudioMixerManagerLinuxALSA::SetMinMicrophoneVolume(
 uint32_t minVolume)
 {

 if (_inputMixerElement == NULL)
 {
 RTC_LOG(LS_WARNING) << "no avaliable output mixer element exists";
 return -1;
 }

 long int minVol(0);
 long int maxVol(0);

 int errVal = snd_mixer_selem_get_capture_volume_range(
 _inputMixerElement, &minVol, &maxVol);
 if (maxVol <= minVol)
 {
 //maxVol = 255;
 RTC_LOG(LS_WARNING) << "Error getting capture volume range: "
                 << snd_strerror(errVal);
 }

 printf("min %d max %d", minVol, maxVol);
 minVol = (long int)minVolume;
 errVal = snd_mixer_selem_set_capture_volume_range(
 _inputMixerElement, minVol, maxVol);
 RTC_LOG(LS_VERBOSE) << "Capture hardware volume range, min: " << minVol
                 << ", max: " << maxVol;
 if (errVal != 0)
 {
 RTC_LOG(LS_ERROR) << "Error setting capture volume range: "
               << snd_strerror(errVal);
 return -1;
 }

 return 0;
 }
 */

int32_t AudioMixerManagerLinuxALSA::MicrophoneVolume(uint32_t& volume) const {
  if (_inputMixerElement == NULL) {
    RTC_LOG(LS_WARNING) << "no avaliable input mixer element exists";
    return -1;
  }

  long int vol(0);

  int errVal = LATE(snd_mixer_selem_get_capture_volume)(
      _inputMixerElement, (snd_mixer_selem_channel_id_t)0, &vol);
  if (errVal < 0) {
    RTC_LOG(LS_ERROR) << "Error getting inputvolume: "
                      << LATE(snd_strerror)(errVal);
    return -1;
  }
  RTC_LOG(LS_VERBOSE)
      << "AudioMixerManagerLinuxALSA::MicrophoneVolume() => vol=" << vol;

  volume = static_cast<uint32_t>(vol);

  return 0;
}

int32_t AudioMixerManagerLinuxALSA::MaxMicrophoneVolume(
    uint32_t& maxVolume) const {
  if (_inputMixerElement == NULL) {
    RTC_LOG(LS_WARNING) << "no avaliable input mixer element exists";
    return -1;
  }

  long int minVol(0);
  long int maxVol(0);

  // check if we have mic volume at all
  if (!LATE(snd_mixer_selem_has_capture_volume)(_inputMixerElement)) {
    RTC_LOG(LS_ERROR) << "No microphone volume available";
    return -1;
  }

  int errVal = LATE(snd_mixer_selem_get_capture_volume_range)(
      _inputMixerElement, &minVol, &maxVol);

  RTC_LOG(LS_VERBOSE) << "Microphone hardware volume range, min: " << minVol
                      << ", max: " << maxVol;
  if (maxVol <= minVol) {
    RTC_LOG(LS_ERROR) << "Error getting microphone volume range: "
                      << LATE(snd_strerror)(errVal);
  }

  maxVolume = static_cast<uint32_t>(maxVol);

  return 0;
}

int32_t AudioMixerManagerLinuxALSA::MinMicrophoneVolume(
    uint32_t& minVolume) const {
  if (_inputMixerElement == NULL) {
    RTC_LOG(LS_WARNING) << "no avaliable input mixer element exists";
    return -1;
  }

  long int minVol(0);
  long int maxVol(0);

  int errVal = LATE(snd_mixer_selem_get_capture_volume_range)(
      _inputMixerElement, &minVol, &maxVol);

  RTC_LOG(LS_VERBOSE) << "Microphone hardware volume range, min: " << minVol
                      << ", max: " << maxVol;
  if (maxVol <= minVol) {
    RTC_LOG(LS_ERROR) << "Error getting microphone volume range: "
                      << LATE(snd_strerror)(errVal);
  }

  minVolume = static_cast<uint32_t>(minVol);

  return 0;
}

// ============================================================================
//                                 Private Methods
// ============================================================================

int32_t AudioMixerManagerLinuxALSA::LoadMicMixerElement() const {
  int errVal = LATE(snd_mixer_load)(_inputMixerHandle);
  if (errVal < 0) {
    RTC_LOG(LS_ERROR) << "snd_mixer_load(_inputMixerHandle), error: "
                      << LATE(snd_strerror)(errVal);
    _inputMixerHandle = NULL;
    return -1;
  }

  snd_mixer_elem_t* elem = NULL;
  snd_mixer_elem_t* micElem = NULL;
  unsigned mixerIdx = 0;
  const char* selemName = NULL;

  // Find and store handles to the right mixer elements
  for (elem = LATE(snd_mixer_first_elem)(_inputMixerHandle); elem;
       elem = LATE(snd_mixer_elem_next)(elem), mixerIdx++) {
    if (LATE(snd_mixer_selem_is_active)(elem)) {
      selemName = LATE(snd_mixer_selem_get_name)(elem);
      if (strcmp(selemName, "Capture") == 0)  // "Capture", "Mic"
      {
        _inputMixerElement = elem;
        RTC_LOG(LS_VERBOSE) << "Capture element set";
      } else if (strcmp(selemName, "Mic") == 0) {
        micElem = elem;
        RTC_LOG(LS_VERBOSE) << "Mic element found";
      }
    }

    if (_inputMixerElement) {
      // Use the first Capture element that is found
      // The second one may not work
      break;
    }
  }

  if (_inputMixerElement == NULL) {
    // We didn't find a Capture handle, use Mic.
    if (micElem != NULL) {
      _inputMixerElement = micElem;
      RTC_LOG(LS_VERBOSE) << "Using Mic as capture volume.";
    } else {
      _inputMixerElement = NULL;
      RTC_LOG(LS_ERROR) << "Could not find capture volume on the mixer.";

      return -1;
    }
  }

  return 0;
}

int32_t AudioMixerManagerLinuxALSA::LoadSpeakerMixerElement() const {
  int errVal = LATE(snd_mixer_load)(_outputMixerHandle);
  if (errVal < 0) {
    RTC_LOG(LS_ERROR) << "snd_mixer_load(_outputMixerHandle), error: "
                      << LATE(snd_strerror)(errVal);
    _outputMixerHandle = NULL;
    return -1;
  }

  snd_mixer_elem_t* elem = NULL;
  snd_mixer_elem_t* masterElem = NULL;
  snd_mixer_elem_t* speakerElem = NULL;
  unsigned mixerIdx = 0;
  const char* selemName = NULL;

  // Find and store handles to the right mixer elements
  for (elem = LATE(snd_mixer_first_elem)(_outputMixerHandle); elem;
       elem = LATE(snd_mixer_elem_next)(elem), mixerIdx++) {
    if (LATE(snd_mixer_selem_is_active)(elem)) {
      selemName = LATE(snd_mixer_selem_get_name)(elem);
      RTC_LOG(LS_VERBOSE) << "snd_mixer_selem_get_name " << mixerIdx << ": "
                          << selemName << " =" << elem;

      // "Master", "PCM", "Wave", "Master Mono", "PC Speaker", "PCM", "Wave"
      if (strcmp(selemName, "PCM") == 0) {
        _outputMixerElement = elem;
        RTC_LOG(LS_VERBOSE) << "PCM element set";
      } else if (strcmp(selemName, "Master") == 0) {
        masterElem = elem;
        RTC_LOG(LS_VERBOSE) << "Master element found";
      } else if (strcmp(selemName, "Speaker") == 0) {
        speakerElem = elem;
        RTC_LOG(LS_VERBOSE) << "Speaker element found";
      }
    }

    if (_outputMixerElement) {
      // We have found the element we want
      break;
    }
  }

  // If we didn't find a PCM Handle, use Master or Speaker
  if (_outputMixerElement == NULL) {
    if (masterElem != NULL) {
      _outputMixerElement = masterElem;
      RTC_LOG(LS_VERBOSE) << "Using Master as output volume.";
    } else if (speakerElem != NULL) {
      _outputMixerElement = speakerElem;
      RTC_LOG(LS_VERBOSE) << "Using Speaker as output volume.";
    } else {
      _outputMixerElement = NULL;
      RTC_LOG(LS_ERROR) << "Could not find output volume in the mixer.";
      return -1;
    }
  }

  return 0;
}

void AudioMixerManagerLinuxALSA::GetControlName(char* controlName,
                                                char* deviceName) const {
  // Example
  // deviceName: "front:CARD=Intel,DEV=0"
  // controlName: "hw:CARD=Intel"
  char* pos1 = strchr(deviceName, ':');
  char* pos2 = strchr(deviceName, ',');
  if (!pos2) {
    // Can also be default:CARD=Intel
    pos2 = &deviceName[strlen(deviceName)];
  }
  if (pos1 && pos2) {
    strcpy(controlName, "hw");
    int nChar = (int)(pos2 - pos1);
    strncpy(&controlName[2], pos1, nChar);
    controlName[2 + nChar] = '\0';
  } else {
    strcpy(controlName, deviceName);
  }
}

}  // namespace webrtc
