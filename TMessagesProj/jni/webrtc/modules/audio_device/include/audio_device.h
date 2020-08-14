/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_DEVICE_INCLUDE_AUDIO_DEVICE_H_
#define MODULES_AUDIO_DEVICE_INCLUDE_AUDIO_DEVICE_H_

#include "api/scoped_refptr.h"
#include "api/task_queue/task_queue_factory.h"
#include "modules/audio_device/include/audio_device_defines.h"
#include "rtc_base/ref_count.h"

namespace webrtc {

class AudioDeviceModuleForTest;

class AudioDeviceModule : public rtc::RefCountInterface {
 public:
  enum AudioLayer {
    kPlatformDefaultAudio = 0,
    kWindowsCoreAudio,
    kWindowsCoreAudio2,
    kLinuxAlsaAudio,
    kLinuxPulseAudio,
    kAndroidJavaAudio,
    kAndroidOpenSLESAudio,
    kAndroidJavaInputAndOpenSLESOutputAudio,
    kAndroidAAudioAudio,
    kAndroidJavaInputAndAAudioOutputAudio,
    kDummyAudio,
  };

  enum WindowsDeviceType {
    kDefaultCommunicationDevice = -1,
    kDefaultDevice = -2
  };

 public:
  // Creates a default ADM for usage in production code.
  static rtc::scoped_refptr<AudioDeviceModule> Create(
      AudioLayer audio_layer,
      TaskQueueFactory* task_queue_factory);
  // Creates an ADM with support for extra test methods. Don't use this factory
  // in production code.
  static rtc::scoped_refptr<AudioDeviceModuleForTest> CreateForTest(
      AudioLayer audio_layer,
      TaskQueueFactory* task_queue_factory);

  // Retrieve the currently utilized audio layer
  virtual int32_t ActiveAudioLayer(AudioLayer* audioLayer) const = 0;

  // Full-duplex transportation of PCM audio
  virtual int32_t RegisterAudioCallback(AudioTransport* audioCallback) = 0;

  // Main initialization and termination
  virtual int32_t Init() = 0;
  virtual int32_t Terminate() = 0;
  virtual bool Initialized() const = 0;

  // Device enumeration
  virtual int16_t PlayoutDevices() = 0;
  virtual int16_t RecordingDevices() = 0;
  virtual int32_t PlayoutDeviceName(uint16_t index,
                                    char name[kAdmMaxDeviceNameSize],
                                    char guid[kAdmMaxGuidSize]) = 0;
  virtual int32_t RecordingDeviceName(uint16_t index,
                                      char name[kAdmMaxDeviceNameSize],
                                      char guid[kAdmMaxGuidSize]) = 0;

  // Device selection
  virtual int32_t SetPlayoutDevice(uint16_t index) = 0;
  virtual int32_t SetPlayoutDevice(WindowsDeviceType device) = 0;
  virtual int32_t SetRecordingDevice(uint16_t index) = 0;
  virtual int32_t SetRecordingDevice(WindowsDeviceType device) = 0;

  // Audio transport initialization
  virtual int32_t PlayoutIsAvailable(bool* available) = 0;
  virtual int32_t InitPlayout() = 0;
  virtual bool PlayoutIsInitialized() const = 0;
  virtual int32_t RecordingIsAvailable(bool* available) = 0;
  virtual int32_t InitRecording() = 0;
  virtual bool RecordingIsInitialized() const = 0;

  // Audio transport control
  virtual int32_t StartPlayout() = 0;
  virtual int32_t StopPlayout() = 0;
  virtual bool Playing() const = 0;
  virtual int32_t StartRecording() = 0;
  virtual int32_t StopRecording() = 0;
  virtual bool Recording() const = 0;

  // Audio mixer initialization
  virtual int32_t InitSpeaker() = 0;
  virtual bool SpeakerIsInitialized() const = 0;
  virtual int32_t InitMicrophone() = 0;
  virtual bool MicrophoneIsInitialized() const = 0;

  // Speaker volume controls
  virtual int32_t SpeakerVolumeIsAvailable(bool* available) = 0;
  virtual int32_t SetSpeakerVolume(uint32_t volume) = 0;
  virtual int32_t SpeakerVolume(uint32_t* volume) const = 0;
  virtual int32_t MaxSpeakerVolume(uint32_t* maxVolume) const = 0;
  virtual int32_t MinSpeakerVolume(uint32_t* minVolume) const = 0;

  // Microphone volume controls
  virtual int32_t MicrophoneVolumeIsAvailable(bool* available) = 0;
  virtual int32_t SetMicrophoneVolume(uint32_t volume) = 0;
  virtual int32_t MicrophoneVolume(uint32_t* volume) const = 0;
  virtual int32_t MaxMicrophoneVolume(uint32_t* maxVolume) const = 0;
  virtual int32_t MinMicrophoneVolume(uint32_t* minVolume) const = 0;

  // Speaker mute control
  virtual int32_t SpeakerMuteIsAvailable(bool* available) = 0;
  virtual int32_t SetSpeakerMute(bool enable) = 0;
  virtual int32_t SpeakerMute(bool* enabled) const = 0;

  // Microphone mute control
  virtual int32_t MicrophoneMuteIsAvailable(bool* available) = 0;
  virtual int32_t SetMicrophoneMute(bool enable) = 0;
  virtual int32_t MicrophoneMute(bool* enabled) const = 0;

  // Stereo support
  virtual int32_t StereoPlayoutIsAvailable(bool* available) const = 0;
  virtual int32_t SetStereoPlayout(bool enable) = 0;
  virtual int32_t StereoPlayout(bool* enabled) const = 0;
  virtual int32_t StereoRecordingIsAvailable(bool* available) const = 0;
  virtual int32_t SetStereoRecording(bool enable) = 0;
  virtual int32_t StereoRecording(bool* enabled) const = 0;

  // Playout delay
  virtual int32_t PlayoutDelay(uint16_t* delayMS) const = 0;

  // Only supported on Android.
  virtual bool BuiltInAECIsAvailable() const = 0;
  virtual bool BuiltInAGCIsAvailable() const = 0;
  virtual bool BuiltInNSIsAvailable() const = 0;

  // Enables the built-in audio effects. Only supported on Android.
  virtual int32_t EnableBuiltInAEC(bool enable) = 0;
  virtual int32_t EnableBuiltInAGC(bool enable) = 0;
  virtual int32_t EnableBuiltInNS(bool enable) = 0;

  // Play underrun count. Only supported on Android.
  // TODO(alexnarest): Make it abstract after upstream projects support it.
  virtual int32_t GetPlayoutUnderrunCount() const { return -1; }

// Only supported on iOS.
#if defined(WEBRTC_IOS)
  virtual int GetPlayoutAudioParameters(AudioParameters* params) const = 0;
  virtual int GetRecordAudioParameters(AudioParameters* params) const = 0;
#endif  // WEBRTC_IOS

 protected:
  ~AudioDeviceModule() override {}
};

// Extends the default ADM interface with some extra test methods.
// Intended for usage in tests only and requires a unique factory method.
class AudioDeviceModuleForTest : public AudioDeviceModule {
 public:
  // Triggers internal restart sequences of audio streaming. Can be used by
  // tests to emulate events corresponding to e.g. removal of an active audio
  // device or other actions which causes the stream to be disconnected.
  virtual int RestartPlayoutInternally() = 0;
  virtual int RestartRecordingInternally() = 0;

  virtual int SetPlayoutSampleRate(uint32_t sample_rate) = 0;
  virtual int SetRecordingSampleRate(uint32_t sample_rate) = 0;
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_DEVICE_INCLUDE_AUDIO_DEVICE_H_
