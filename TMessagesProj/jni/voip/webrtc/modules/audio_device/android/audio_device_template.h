/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_DEVICE_ANDROID_AUDIO_DEVICE_TEMPLATE_H_
#define MODULES_AUDIO_DEVICE_ANDROID_AUDIO_DEVICE_TEMPLATE_H_

#include "api/sequence_checker.h"
#include "modules/audio_device/android/audio_manager.h"
#include "modules/audio_device/audio_device_generic.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {

// InputType/OutputType can be any class that implements the capturing/rendering
// part of the AudioDeviceGeneric API.
// Construction and destruction must be done on one and the same thread. Each
// internal implementation of InputType and OutputType will RTC_DCHECK if that
// is not the case. All implemented methods must also be called on the same
// thread. See comments in each InputType/OutputType class for more info.
// It is possible to call the two static methods (SetAndroidAudioDeviceObjects
// and ClearAndroidAudioDeviceObjects) from a different thread but both will
// RTC_CHECK that the calling thread is attached to a Java VM.

template <class InputType, class OutputType>
class AudioDeviceTemplate : public AudioDeviceGeneric {
 public:
  AudioDeviceTemplate(AudioDeviceModule::AudioLayer audio_layer,
                      AudioManager* audio_manager)
      : audio_layer_(audio_layer),
        audio_manager_(audio_manager),
        output_(audio_manager_),
        input_(audio_manager_),
        initialized_(false) {
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    RTC_CHECK(audio_manager);
    audio_manager_->SetActiveAudioLayer(audio_layer);
  }

  virtual ~AudioDeviceTemplate() { RTC_LOG(LS_INFO) << __FUNCTION__; }

  int32_t ActiveAudioLayer(
      AudioDeviceModule::AudioLayer& audioLayer) const override {
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    audioLayer = audio_layer_;
    return 0;
  }

  InitStatus Init() override {
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    RTC_DCHECK(thread_checker_.IsCurrent());
    RTC_DCHECK(!initialized_);
    if (!audio_manager_->Init()) {
      return InitStatus::OTHER_ERROR;
    }
    if (output_.Init() != 0) {
      audio_manager_->Close();
      return InitStatus::PLAYOUT_ERROR;
    }
    if (input_.Init() != 0) {
      output_.Terminate();
      audio_manager_->Close();
      return InitStatus::RECORDING_ERROR;
    }
    initialized_ = true;
    return InitStatus::OK;
  }

  int32_t Terminate() override {
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    RTC_DCHECK(thread_checker_.IsCurrent());
    int32_t err = input_.Terminate();
    err |= output_.Terminate();
    err |= !audio_manager_->Close();
    initialized_ = false;
    RTC_DCHECK_EQ(err, 0);
    return err;
  }

  bool Initialized() const override {
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    RTC_DCHECK(thread_checker_.IsCurrent());
    return initialized_;
  }

  int16_t PlayoutDevices() override {
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    return 1;
  }

  int16_t RecordingDevices() override {
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    return 1;
  }

  int32_t PlayoutDeviceName(uint16_t index,
                            char name[kAdmMaxDeviceNameSize],
                            char guid[kAdmMaxGuidSize]) override {
    RTC_CHECK_NOTREACHED();
  }

  int32_t RecordingDeviceName(uint16_t index,
                              char name[kAdmMaxDeviceNameSize],
                              char guid[kAdmMaxGuidSize]) override {
    RTC_CHECK_NOTREACHED();
  }

  int32_t SetPlayoutDevice(uint16_t index) override {
    // OK to use but it has no effect currently since device selection is
    // done using Andoid APIs instead.
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    return 0;
  }

  int32_t SetPlayoutDevice(
      AudioDeviceModule::WindowsDeviceType device) override {
    RTC_CHECK_NOTREACHED();
  }

  int32_t SetRecordingDevice(uint16_t index) override {
    // OK to use but it has no effect currently since device selection is
    // done using Andoid APIs instead.
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    return 0;
  }

  int32_t SetRecordingDevice(
      AudioDeviceModule::WindowsDeviceType device) override {
    RTC_CHECK_NOTREACHED();
  }

  int32_t PlayoutIsAvailable(bool& available) override {
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    available = true;
    return 0;
  }

  int32_t InitPlayout() override {
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    return output_.InitPlayout();
  }

  bool PlayoutIsInitialized() const override {
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    return output_.PlayoutIsInitialized();
  }

  int32_t RecordingIsAvailable(bool& available) override {
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    available = true;
    return 0;
  }

  int32_t InitRecording() override {
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    return input_.InitRecording();
  }

  bool RecordingIsInitialized() const override {
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    return input_.RecordingIsInitialized();
  }

  int32_t StartPlayout() override {
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    if (!audio_manager_->IsCommunicationModeEnabled()) {
      RTC_LOG(LS_WARNING)
          << "The application should use MODE_IN_COMMUNICATION audio mode!";
    }
    return output_.StartPlayout();
  }

  int32_t StopPlayout() override {
    // Avoid using audio manger (JNI/Java cost) if playout was inactive.
    if (!Playing())
      return 0;
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    int32_t err = output_.StopPlayout();
    return err;
  }

  bool Playing() const override {
    RTC_LOG(LS_INFO) << __FUNCTION__;
    return output_.Playing();
  }

  int32_t StartRecording() override {
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    if (!audio_manager_->IsCommunicationModeEnabled()) {
      RTC_LOG(LS_WARNING)
          << "The application should use MODE_IN_COMMUNICATION audio mode!";
    }
    return input_.StartRecording();
  }

  int32_t StopRecording() override {
    // Avoid using audio manger (JNI/Java cost) if recording was inactive.
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    if (!Recording())
      return 0;
    int32_t err = input_.StopRecording();
    return err;
  }

  bool Recording() const override { return input_.Recording(); }

  int32_t InitSpeaker() override {
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    return 0;
  }

  bool SpeakerIsInitialized() const override {
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    return true;
  }

  int32_t InitMicrophone() override {
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    return 0;
  }

  bool MicrophoneIsInitialized() const override {
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    return true;
  }

  int32_t SpeakerVolumeIsAvailable(bool& available) override {
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    return output_.SpeakerVolumeIsAvailable(available);
  }

  int32_t SetSpeakerVolume(uint32_t volume) override {
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    return output_.SetSpeakerVolume(volume);
  }

  int32_t SpeakerVolume(uint32_t& volume) const override {
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    return output_.SpeakerVolume(volume);
  }

  int32_t MaxSpeakerVolume(uint32_t& maxVolume) const override {
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    return output_.MaxSpeakerVolume(maxVolume);
  }

  int32_t MinSpeakerVolume(uint32_t& minVolume) const override {
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    return output_.MinSpeakerVolume(minVolume);
  }

  int32_t MicrophoneVolumeIsAvailable(bool& available) override {
    available = false;
    return -1;
  }

  int32_t SetMicrophoneVolume(uint32_t volume) override {
    RTC_CHECK_NOTREACHED();
  }

  int32_t MicrophoneVolume(uint32_t& volume) const override {
    RTC_CHECK_NOTREACHED();
    return -1;
  }

  int32_t MaxMicrophoneVolume(uint32_t& maxVolume) const override {
    RTC_CHECK_NOTREACHED();
  }

  int32_t MinMicrophoneVolume(uint32_t& minVolume) const override {
    RTC_CHECK_NOTREACHED();
  }

  int32_t SpeakerMuteIsAvailable(bool& available) override {
    RTC_CHECK_NOTREACHED();
  }

  int32_t SetSpeakerMute(bool enable) override { RTC_CHECK_NOTREACHED(); }

  int32_t SpeakerMute(bool& enabled) const override { RTC_CHECK_NOTREACHED(); }

  int32_t MicrophoneMuteIsAvailable(bool& available) override {
    RTC_CHECK_NOTREACHED();
  }

  int32_t SetMicrophoneMute(bool enable) override { RTC_CHECK_NOTREACHED(); }

  int32_t MicrophoneMute(bool& enabled) const override {
    RTC_CHECK_NOTREACHED();
  }

  // Returns true if the audio manager has been configured to support stereo
  // and false otherwised. Default is mono.
  int32_t StereoPlayoutIsAvailable(bool& available) override {
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    available = audio_manager_->IsStereoPlayoutSupported();
    return 0;
  }

  int32_t SetStereoPlayout(bool enable) override {
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    bool available = audio_manager_->IsStereoPlayoutSupported();
    // Android does not support changes between mono and stero on the fly.
    // Instead, the native audio layer is configured via the audio manager
    // to either support mono or stereo. It is allowed to call this method
    // if that same state is not modified.
    return (enable == available) ? 0 : -1;
  }

  int32_t StereoPlayout(bool& enabled) const override {
    enabled = audio_manager_->IsStereoPlayoutSupported();
    return 0;
  }

  int32_t StereoRecordingIsAvailable(bool& available) override {
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    available = audio_manager_->IsStereoRecordSupported();
    return 0;
  }

  int32_t SetStereoRecording(bool enable) override {
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    bool available = audio_manager_->IsStereoRecordSupported();
    // Android does not support changes between mono and stero on the fly.
    // Instead, the native audio layer is configured via the audio manager
    // to either support mono or stereo. It is allowed to call this method
    // if that same state is not modified.
    return (enable == available) ? 0 : -1;
  }

  int32_t StereoRecording(bool& enabled) const override {
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    enabled = audio_manager_->IsStereoRecordSupported();
    return 0;
  }

  int32_t PlayoutDelay(uint16_t& delay_ms) const override {
    // Best guess we can do is to use half of the estimated total delay.
    delay_ms = audio_manager_->GetDelayEstimateInMilliseconds() / 2;
    RTC_DCHECK_GT(delay_ms, 0);
    return 0;
  }

  void AttachAudioBuffer(AudioDeviceBuffer* audioBuffer) override {
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    output_.AttachAudioBuffer(audioBuffer);
    input_.AttachAudioBuffer(audioBuffer);
  }

  // Returns true if the device both supports built in AEC and the device
  // is not blacklisted.
  // Currently, if OpenSL ES is used in both directions, this method will still
  // report the correct value and it has the correct effect. As an example:
  // a device supports built in AEC and this method returns true. Libjingle
  // will then disable the WebRTC based AEC and that will work for all devices
  // (mainly Nexus) even when OpenSL ES is used for input since our current
  // implementation will enable built-in AEC by default also for OpenSL ES.
  // The only "bad" thing that happens today is that when Libjingle calls
  // OpenSLESRecorder::EnableBuiltInAEC() it will not have any real effect and
  // a "Not Implemented" log will be filed. This non-perfect state will remain
  // until I have added full support for audio effects based on OpenSL ES APIs.
  bool BuiltInAECIsAvailable() const override {
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    return audio_manager_->IsAcousticEchoCancelerSupported();
  }

  // TODO(henrika): add implementation for OpenSL ES based audio as well.
  int32_t EnableBuiltInAEC(bool enable) override {
    RTC_DLOG(LS_INFO) << __FUNCTION__ << "(" << enable << ")";
    RTC_CHECK(BuiltInAECIsAvailable()) << "HW AEC is not available";
    return input_.EnableBuiltInAEC(enable);
  }

  // Returns true if the device both supports built in AGC and the device
  // is not blacklisted.
  // TODO(henrika): add implementation for OpenSL ES based audio as well.
  // In addition, see comments for BuiltInAECIsAvailable().
  bool BuiltInAGCIsAvailable() const override {
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    return audio_manager_->IsAutomaticGainControlSupported();
  }

  // TODO(henrika): add implementation for OpenSL ES based audio as well.
  int32_t EnableBuiltInAGC(bool enable) override {
    RTC_DLOG(LS_INFO) << __FUNCTION__ << "(" << enable << ")";
    RTC_CHECK(BuiltInAGCIsAvailable()) << "HW AGC is not available";
    return input_.EnableBuiltInAGC(enable);
  }

  // Returns true if the device both supports built in NS and the device
  // is not blacklisted.
  // TODO(henrika): add implementation for OpenSL ES based audio as well.
  // In addition, see comments for BuiltInAECIsAvailable().
  bool BuiltInNSIsAvailable() const override {
    RTC_DLOG(LS_INFO) << __FUNCTION__;
    return audio_manager_->IsNoiseSuppressorSupported();
  }

  // TODO(henrika): add implementation for OpenSL ES based audio as well.
  int32_t EnableBuiltInNS(bool enable) override {
    RTC_DLOG(LS_INFO) << __FUNCTION__ << "(" << enable << ")";
    RTC_CHECK(BuiltInNSIsAvailable()) << "HW NS is not available";
    return input_.EnableBuiltInNS(enable);
  }

 private:
  SequenceChecker thread_checker_;

  // Local copy of the audio layer set during construction of the
  // AudioDeviceModuleImpl instance. Read only value.
  const AudioDeviceModule::AudioLayer audio_layer_;

  // Non-owning raw pointer to AudioManager instance given to use at
  // construction. The real object is owned by AudioDeviceModuleImpl and the
  // life time is the same as that of the AudioDeviceModuleImpl, hence there
  // is no risk of reading a NULL pointer at any time in this class.
  AudioManager* const audio_manager_;

  OutputType output_;

  InputType input_;

  bool initialized_;
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_DEVICE_ANDROID_AUDIO_DEVICE_TEMPLATE_H_
