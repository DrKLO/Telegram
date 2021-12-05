/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/src/jni/audio_device/audio_device_module.h"

#include <memory>
#include <utility>

#include "api/sequence_checker.h"
#include "api/task_queue/default_task_queue_factory.h"
#include "api/task_queue/task_queue_factory.h"
#include "modules/audio_device/audio_device_buffer.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/ref_counted_object.h"
#include "sdk/android/generated_audio_device_module_base_jni/WebRtcAudioManager_jni.h"
#include "system_wrappers/include/metrics.h"

namespace webrtc {
namespace jni {

namespace {

// This class combines a generic instance of an AudioInput and a generic
// instance of an AudioOutput to create an AudioDeviceModule. This is mostly
// done by delegating to the audio input/output with some glue code. This class
// also directly implements some of the AudioDeviceModule methods with dummy
// implementations.
//
// An instance can be created on any thread, but must then be used on one and
// the same thread. All public methods must also be called on the same thread.
// A thread checker will RTC_DCHECK if any method is called on an invalid
// thread.
// TODO(henrika): it might be useful to also support a scenario where the ADM
// is constructed on thread T1, used on thread T2 and destructed on T2 or T3.
// If so, care must be taken to ensure that only T2 is a COM thread.
class AndroidAudioDeviceModule : public AudioDeviceModule {
 public:
  // For use with UMA logging. Must be kept in sync with histograms.xml in
  // Chrome, located at
  // https://cs.chromium.org/chromium/src/tools/metrics/histograms/histograms.xml
  enum class InitStatus {
    OK = 0,
    PLAYOUT_ERROR = 1,
    RECORDING_ERROR = 2,
    OTHER_ERROR = 3,
    NUM_STATUSES = 4
  };

  AndroidAudioDeviceModule(AudioDeviceModule::AudioLayer audio_layer,
                           bool is_stereo_playout_supported,
                           bool is_stereo_record_supported,
                           uint16_t playout_delay_ms,
                           std::unique_ptr<AudioInput> audio_input,
                           std::unique_ptr<AudioOutput> audio_output)
      : audio_layer_(audio_layer),
        is_stereo_playout_supported_(is_stereo_playout_supported),
        is_stereo_record_supported_(is_stereo_record_supported),
        playout_delay_ms_(playout_delay_ms),
        task_queue_factory_(CreateDefaultTaskQueueFactory()),
        input_(std::move(audio_input)),
        output_(std::move(audio_output)),
        initialized_(false) {
    RTC_CHECK(input_);
    RTC_CHECK(output_);
    RTC_DLOG(INFO) << __FUNCTION__;
    thread_checker_.Detach();
  }

  ~AndroidAudioDeviceModule() override { RTC_DLOG(INFO) << __FUNCTION__; }

  int32_t ActiveAudioLayer(
      AudioDeviceModule::AudioLayer* audioLayer) const override {
    RTC_DLOG(INFO) << __FUNCTION__;
    *audioLayer = audio_layer_;
    return 0;
  }

  int32_t RegisterAudioCallback(AudioTransport* audioCallback) override {
    RTC_DLOG(INFO) << __FUNCTION__;
    return audio_device_buffer_->RegisterAudioCallback(audioCallback);
  }

  int32_t Init() override {
    RTC_DLOG(INFO) << __FUNCTION__;
    RTC_DCHECK(thread_checker_.IsCurrent());
    audio_device_buffer_ =
        std::make_unique<AudioDeviceBuffer>(task_queue_factory_.get());
    AttachAudioBuffer();
    if (initialized_) {
      return 0;
    }
    InitStatus status;
    if (output_->Init() != 0) {
      status = InitStatus::PLAYOUT_ERROR;
    } else if (input_->Init() != 0) {
      output_->Terminate();
      status = InitStatus::RECORDING_ERROR;
    } else {
      initialized_ = true;
      status = InitStatus::OK;
    }
    RTC_HISTOGRAM_ENUMERATION("WebRTC.Audio.InitializationResult",
                              static_cast<int>(status),
                              static_cast<int>(InitStatus::NUM_STATUSES));
    if (status != InitStatus::OK) {
      RTC_LOG(LS_ERROR) << "Audio device initialization failed.";
      return -1;
    }
    return 0;
  }

  int32_t Terminate() override {
    RTC_DLOG(INFO) << __FUNCTION__;
    if (!initialized_)
      return 0;
    RTC_DCHECK(thread_checker_.IsCurrent());
    int32_t err = input_->Terminate();
    err |= output_->Terminate();
    initialized_ = false;
    thread_checker_.Detach();
    audio_device_buffer_.reset(nullptr);
    RTC_DCHECK_EQ(err, 0);
    return err;
  }

  bool Initialized() const override {
    RTC_DLOG(INFO) << __FUNCTION__ << ":" << initialized_;
    return initialized_;
  }

  int16_t PlayoutDevices() override {
    RTC_DLOG(INFO) << __FUNCTION__;
    RTC_LOG(INFO) << "output: " << 1;
    return 1;
  }

  int16_t RecordingDevices() override {
    RTC_DLOG(INFO) << __FUNCTION__;
    RTC_DLOG(INFO) << "output: " << 1;
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
    RTC_DLOG(INFO) << __FUNCTION__ << "(" << index << ")";
    return 0;
  }

  int32_t SetPlayoutDevice(
      AudioDeviceModule::WindowsDeviceType device) override {
    RTC_CHECK_NOTREACHED();
  }

  int32_t SetRecordingDevice(uint16_t index) override {
    // OK to use but it has no effect currently since device selection is
    // done using Andoid APIs instead.
    RTC_DLOG(INFO) << __FUNCTION__ << "(" << index << ")";
    return 0;
  }

  int32_t SetRecordingDevice(
      AudioDeviceModule::WindowsDeviceType device) override {
    RTC_CHECK_NOTREACHED();
  }

  int32_t PlayoutIsAvailable(bool* available) override {
    RTC_DLOG(INFO) << __FUNCTION__;
    *available = true;
    RTC_DLOG(INFO) << "output: " << *available;
    return 0;
  }

  int32_t InitPlayout() override {
    RTC_DLOG(INFO) << __FUNCTION__;
    if (!initialized_)
      return -1;
    if (PlayoutIsInitialized()) {
      return 0;
    }
    int32_t result = output_->InitPlayout();
    RTC_DLOG(INFO) << "output: " << result;
    RTC_HISTOGRAM_BOOLEAN("WebRTC.Audio.InitPlayoutSuccess",
                          static_cast<int>(result == 0));
    return result;
  }

  bool PlayoutIsInitialized() const override {
    RTC_DLOG(INFO) << __FUNCTION__;
    return output_->PlayoutIsInitialized();
  }

  int32_t RecordingIsAvailable(bool* available) override {
    RTC_DLOG(INFO) << __FUNCTION__;
    *available = true;
    RTC_DLOG(INFO) << "output: " << *available;
    return 0;
  }

  int32_t InitRecording() override {
    RTC_DLOG(INFO) << __FUNCTION__;
    if (!initialized_)
      return -1;
    if (RecordingIsInitialized()) {
      return 0;
    }
    int32_t result = input_->InitRecording();
    RTC_DLOG(INFO) << "output: " << result;
    RTC_HISTOGRAM_BOOLEAN("WebRTC.Audio.InitRecordingSuccess",
                          static_cast<int>(result == 0));
    return result;
  }

  bool RecordingIsInitialized() const override {
    RTC_DLOG(INFO) << __FUNCTION__;
    return input_->RecordingIsInitialized();
  }

  int32_t StartPlayout() override {
    RTC_DLOG(INFO) << __FUNCTION__;
    if (!initialized_)
      return -1;
    if (Playing()) {
      return 0;
    }
    int32_t result = output_->StartPlayout();
    RTC_DLOG(INFO) << "output: " << result;
    RTC_HISTOGRAM_BOOLEAN("WebRTC.Audio.StartPlayoutSuccess",
                          static_cast<int>(result == 0));
    if (result == 0) {
      // Only start playing the audio device buffer if starting the audio
      // output succeeded.
      audio_device_buffer_->StartPlayout();
    }
    return result;
  }

  int32_t StopPlayout() override {
    RTC_DLOG(INFO) << __FUNCTION__;
    if (!initialized_)
      return -1;
    if (!Playing())
      return 0;
    RTC_LOG(INFO) << __FUNCTION__;
    audio_device_buffer_->StopPlayout();
    int32_t result = output_->StopPlayout();
    RTC_DLOG(INFO) << "output: " << result;
    RTC_HISTOGRAM_BOOLEAN("WebRTC.Audio.StopPlayoutSuccess",
                          static_cast<int>(result == 0));
    return result;
  }

  bool Playing() const override {
    RTC_DLOG(INFO) << __FUNCTION__;
    return output_->Playing();
  }

  int32_t StartRecording() override {
    RTC_DLOG(INFO) << __FUNCTION__;
    if (!initialized_)
      return -1;
    if (Recording()) {
      return 0;
    }
    int32_t result = input_->StartRecording();
    RTC_DLOG(INFO) << "output: " << result;
    RTC_HISTOGRAM_BOOLEAN("WebRTC.Audio.StartRecordingSuccess",
                          static_cast<int>(result == 0));
    if (result == 0) {
      // Only start recording the audio device buffer if starting the audio
      // input succeeded.
      audio_device_buffer_->StartRecording();
    }
    return result;
  }

  int32_t StopRecording() override {
    RTC_DLOG(INFO) << __FUNCTION__;
    if (!initialized_)
      return -1;
    if (!Recording())
      return 0;
    audio_device_buffer_->StopRecording();
    int32_t result = input_->StopRecording();
    RTC_DLOG(INFO) << "output: " << result;
    RTC_HISTOGRAM_BOOLEAN("WebRTC.Audio.StopRecordingSuccess",
                          static_cast<int>(result == 0));
    return result;
  }

  bool Recording() const override {
    RTC_DLOG(INFO) << __FUNCTION__;
    return input_->Recording();
  }

  int32_t InitSpeaker() override {
    RTC_DLOG(INFO) << __FUNCTION__;
    return initialized_ ? 0 : -1;
  }

  bool SpeakerIsInitialized() const override {
    RTC_DLOG(INFO) << __FUNCTION__;
    return initialized_;
  }

  int32_t InitMicrophone() override {
    RTC_DLOG(INFO) << __FUNCTION__;
    return initialized_ ? 0 : -1;
  }

  bool MicrophoneIsInitialized() const override {
    RTC_DLOG(INFO) << __FUNCTION__;
    return initialized_;
  }

  int32_t SpeakerVolumeIsAvailable(bool* available) override {
    RTC_DLOG(INFO) << __FUNCTION__;
    if (!initialized_)
      return -1;
    *available = output_->SpeakerVolumeIsAvailable();
    RTC_DLOG(INFO) << "output: " << *available;
    return 0;
  }

  int32_t SetSpeakerVolume(uint32_t volume) override {
    RTC_DLOG(INFO) << __FUNCTION__;
    if (!initialized_)
      return -1;
    return output_->SetSpeakerVolume(volume);
  }

  int32_t SpeakerVolume(uint32_t* output_volume) const override {
    RTC_DLOG(INFO) << __FUNCTION__;
    if (!initialized_)
      return -1;
    absl::optional<uint32_t> volume = output_->SpeakerVolume();
    if (!volume)
      return -1;
    *output_volume = *volume;
    RTC_DLOG(INFO) << "output: " << *volume;
    return 0;
  }

  int32_t MaxSpeakerVolume(uint32_t* output_max_volume) const override {
    RTC_DLOG(INFO) << __FUNCTION__;
    if (!initialized_)
      return -1;
    absl::optional<uint32_t> max_volume = output_->MaxSpeakerVolume();
    if (!max_volume)
      return -1;
    *output_max_volume = *max_volume;
    return 0;
  }

  int32_t MinSpeakerVolume(uint32_t* output_min_volume) const override {
    RTC_DLOG(INFO) << __FUNCTION__;
    if (!initialized_)
      return -1;
    absl::optional<uint32_t> min_volume = output_->MinSpeakerVolume();
    if (!min_volume)
      return -1;
    *output_min_volume = *min_volume;
    return 0;
  }

  int32_t MicrophoneVolumeIsAvailable(bool* available) override {
    RTC_DLOG(INFO) << __FUNCTION__;
    *available = false;
    RTC_DLOG(INFO) << "output: " << *available;
    return -1;
  }

  int32_t SetMicrophoneVolume(uint32_t volume) override {
    RTC_DLOG(INFO) << __FUNCTION__ << "(" << volume << ")";
    RTC_CHECK_NOTREACHED();
  }

  int32_t MicrophoneVolume(uint32_t* volume) const override {
    RTC_DLOG(INFO) << __FUNCTION__;
    RTC_CHECK_NOTREACHED();
  }

  int32_t MaxMicrophoneVolume(uint32_t* maxVolume) const override {
    RTC_DLOG(INFO) << __FUNCTION__;
    RTC_CHECK_NOTREACHED();
  }

  int32_t MinMicrophoneVolume(uint32_t* minVolume) const override {
    RTC_DLOG(INFO) << __FUNCTION__;
    RTC_CHECK_NOTREACHED();
  }

  int32_t SpeakerMuteIsAvailable(bool* available) override {
    RTC_DLOG(INFO) << __FUNCTION__;
    RTC_CHECK_NOTREACHED();
  }

  int32_t SetSpeakerMute(bool enable) override {
    RTC_DLOG(INFO) << __FUNCTION__ << "(" << enable << ")";
    RTC_CHECK_NOTREACHED();
  }

  int32_t SpeakerMute(bool* enabled) const override {
    RTC_DLOG(INFO) << __FUNCTION__;
    RTC_CHECK_NOTREACHED();
  }

  int32_t MicrophoneMuteIsAvailable(bool* available) override {
    RTC_DLOG(INFO) << __FUNCTION__;
    RTC_CHECK_NOTREACHED();
  }

  int32_t SetMicrophoneMute(bool enable) override {
    RTC_DLOG(INFO) << __FUNCTION__ << "(" << enable << ")";
    RTC_CHECK_NOTREACHED();
  }

  int32_t MicrophoneMute(bool* enabled) const override {
    RTC_DLOG(INFO) << __FUNCTION__;
    RTC_CHECK_NOTREACHED();
  }

  int32_t StereoPlayoutIsAvailable(bool* available) const override {
    RTC_DLOG(INFO) << __FUNCTION__;
    *available = is_stereo_playout_supported_;
    RTC_DLOG(INFO) << "output: " << *available;
    return 0;
  }

  int32_t SetStereoPlayout(bool enable) override {
    RTC_DLOG(INFO) << __FUNCTION__ << "(" << enable << ")";
    // Android does not support changes between mono and stero on the fly. The
    // use of stereo or mono is determined by the audio layer. It is allowed
    // to call this method if that same state is not modified.
    bool available = is_stereo_playout_supported_;
    if (enable != available) {
      RTC_LOG(WARNING) << "changing stereo playout not supported";
      return -1;
    }
    return 0;
  }

  int32_t StereoPlayout(bool* enabled) const override {
    RTC_DLOG(INFO) << __FUNCTION__;
    *enabled = is_stereo_playout_supported_;
    RTC_DLOG(INFO) << "output: " << *enabled;
    return 0;
  }

  int32_t StereoRecordingIsAvailable(bool* available) const override {
    RTC_DLOG(INFO) << __FUNCTION__;
    *available = is_stereo_record_supported_;
    RTC_DLOG(INFO) << "output: " << *available;
    return 0;
  }

  int32_t SetStereoRecording(bool enable) override {
    RTC_DLOG(INFO) << __FUNCTION__ << "(" << enable << ")";
    // Android does not support changes between mono and stero on the fly. The
    // use of stereo or mono is determined by the audio layer. It is allowed
    // to call this method if that same state is not modified.
    bool available = is_stereo_record_supported_;
    if (enable != available) {
      RTC_LOG(WARNING) << "changing stereo recording not supported";
      return -1;
    }
    return 0;
  }

  int32_t StereoRecording(bool* enabled) const override {
    RTC_DLOG(INFO) << __FUNCTION__;
    *enabled = is_stereo_record_supported_;
    RTC_DLOG(INFO) << "output: " << *enabled;
    return 0;
  }

  int32_t PlayoutDelay(uint16_t* delay_ms) const override {
    // Best guess we can do is to use half of the estimated total delay.
    *delay_ms = playout_delay_ms_ / 2;
    RTC_DCHECK_GT(*delay_ms, 0);
    return 0;
  }

  // Returns true if the device both supports built in AEC and the device
  // is not blocklisted.
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
    RTC_DLOG(INFO) << __FUNCTION__;
    if (!initialized_)
      return false;
    bool isAvailable = input_->IsAcousticEchoCancelerSupported();
    RTC_DLOG(INFO) << "output: " << isAvailable;
    return isAvailable;
  }

  // Not implemented for any input device on Android.
  bool BuiltInAGCIsAvailable() const override {
    RTC_DLOG(INFO) << __FUNCTION__;
    RTC_DLOG(INFO) << "output: " << false;
    return false;
  }

  // Returns true if the device both supports built in NS and the device
  // is not blocklisted.
  // TODO(henrika): add implementation for OpenSL ES based audio as well.
  // In addition, see comments for BuiltInAECIsAvailable().
  bool BuiltInNSIsAvailable() const override {
    RTC_DLOG(INFO) << __FUNCTION__;
    if (!initialized_)
      return false;
    bool isAvailable = input_->IsNoiseSuppressorSupported();
    RTC_DLOG(INFO) << "output: " << isAvailable;
    return isAvailable;
  }

  // TODO(henrika): add implementation for OpenSL ES based audio as well.
  int32_t EnableBuiltInAEC(bool enable) override {
    RTC_DLOG(INFO) << __FUNCTION__ << "(" << enable << ")";
    if (!initialized_)
      return -1;
    RTC_CHECK(BuiltInAECIsAvailable()) << "HW AEC is not available";
    int32_t result = input_->EnableBuiltInAEC(enable);
    RTC_DLOG(INFO) << "output: " << result;
    return result;
  }

  int32_t EnableBuiltInAGC(bool enable) override {
    RTC_DLOG(INFO) << __FUNCTION__ << "(" << enable << ")";
    RTC_CHECK_NOTREACHED();
  }

  // TODO(henrika): add implementation for OpenSL ES based audio as well.
  int32_t EnableBuiltInNS(bool enable) override {
    RTC_DLOG(INFO) << __FUNCTION__ << "(" << enable << ")";
    if (!initialized_)
      return -1;
    RTC_CHECK(BuiltInNSIsAvailable()) << "HW NS is not available";
    int32_t result = input_->EnableBuiltInNS(enable);
    RTC_DLOG(INFO) << "output: " << result;
    return result;
  }

  int32_t GetPlayoutUnderrunCount() const override {
    if (!initialized_)
      return -1;
    return output_->GetPlayoutUnderrunCount();
  }

  int32_t AttachAudioBuffer() {
    RTC_DLOG(INFO) << __FUNCTION__;
    output_->AttachAudioBuffer(audio_device_buffer_.get());
    input_->AttachAudioBuffer(audio_device_buffer_.get());
    return 0;
  }

 private:
  SequenceChecker thread_checker_;

  const AudioDeviceModule::AudioLayer audio_layer_;
  const bool is_stereo_playout_supported_;
  const bool is_stereo_record_supported_;
  const uint16_t playout_delay_ms_;
  const std::unique_ptr<TaskQueueFactory> task_queue_factory_;
  const std::unique_ptr<AudioInput> input_;
  const std::unique_ptr<AudioOutput> output_;
  std::unique_ptr<AudioDeviceBuffer> audio_device_buffer_;

  bool initialized_;
};

}  // namespace

ScopedJavaLocalRef<jobject> GetAudioManager(JNIEnv* env,
                                            const JavaRef<jobject>& j_context) {
  return Java_WebRtcAudioManager_getAudioManager(env, j_context);
}

int GetDefaultSampleRate(JNIEnv* env, const JavaRef<jobject>& j_audio_manager) {
  return Java_WebRtcAudioManager_getSampleRate(env, j_audio_manager);
}

void GetAudioParameters(JNIEnv* env,
                        const JavaRef<jobject>& j_context,
                        const JavaRef<jobject>& j_audio_manager,
                        int input_sample_rate,
                        int output_sample_rate,
                        bool use_stereo_input,
                        bool use_stereo_output,
                        AudioParameters* input_parameters,
                        AudioParameters* output_parameters) {
  const int output_channels = use_stereo_output ? 2 : 1;
  const int input_channels = use_stereo_input ? 2 : 1;
  const size_t output_buffer_size = Java_WebRtcAudioManager_getOutputBufferSize(
      env, j_context, j_audio_manager, output_sample_rate, output_channels);
  const size_t input_buffer_size = Java_WebRtcAudioManager_getInputBufferSize(
      env, j_context, j_audio_manager, input_sample_rate, input_channels);
  output_parameters->reset(output_sample_rate,
                           static_cast<size_t>(output_channels),
                           static_cast<size_t>(output_buffer_size));
  input_parameters->reset(input_sample_rate,
                          static_cast<size_t>(input_channels),
                          static_cast<size_t>(input_buffer_size));
  RTC_CHECK(input_parameters->is_valid());
  RTC_CHECK(output_parameters->is_valid());
}

rtc::scoped_refptr<AudioDeviceModule> CreateAudioDeviceModuleFromInputAndOutput(
    AudioDeviceModule::AudioLayer audio_layer,
    bool is_stereo_playout_supported,
    bool is_stereo_record_supported,
    uint16_t playout_delay_ms,
    std::unique_ptr<AudioInput> audio_input,
    std::unique_ptr<AudioOutput> audio_output) {
  RTC_DLOG(INFO) << __FUNCTION__;
  return rtc::make_ref_counted<AndroidAudioDeviceModule>(
      audio_layer, is_stereo_playout_supported, is_stereo_record_supported,
      playout_delay_ms, std::move(audio_input), std::move(audio_output));
}

}  // namespace jni
}  // namespace webrtc
