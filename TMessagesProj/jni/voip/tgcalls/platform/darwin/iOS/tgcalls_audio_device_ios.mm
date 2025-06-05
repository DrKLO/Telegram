/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#import <AVFoundation/AVFoundation.h>
#import <Foundation/Foundation.h>

#include "tgcalls_audio_device_ios.h"

#include <cmath>

#include "api/array_view.h"
#include "sdk/objc/native/src/audio/helpers.h"
#include "api/task_queue/pending_task_safety_flag.h"
#include "modules/audio_device/fine_audio_buffer.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/thread.h"
#include "rtc_base/thread_annotations.h"
#include "rtc_base/time_utils.h"
#include "system_wrappers/include/field_trial.h"
#include "system_wrappers/include/metrics.h"

#import "base/RTCLogging.h"
#import "RTCAudioSession+Private.h"
#import "RTCAudioSession.h"
#import "RTCAudioSessionConfiguration.h"
#import "RTCNativeAudioSessionDelegateAdapter.h"

namespace webrtc {
namespace tgcalls_ios_adm {

#define LOGI() RTC_LOG(LS_INFO) << "AudioDeviceIOS::"

#define LOG_AND_RETURN_IF_ERROR(error, message)    \
  do {                                             \
    OSStatus err = error;                          \
    if (err) {                                     \
      RTC_LOG(LS_ERROR) << message << ": " << err; \
      return false;                                \
    }                                              \
  } while (0)

#define LOG_IF_ERROR(error, message)               \
  do {                                             \
    OSStatus err = error;                          \
    if (err) {                                     \
      RTC_LOG(LS_ERROR) << message << ": " << err; \
    }                                              \
  } while (0)

// Hardcoded delay estimates based on real measurements.
// TODO(henrika): these value is not used in combination with built-in AEC.
// Can most likely be removed.
const UInt16 kFixedPlayoutDelayEstimate = 30;
const UInt16 kFixedRecordDelayEstimate = 30;

using ios::CheckAndLogError;

#if !defined(NDEBUG)
// Returns true when the code runs on a device simulator.
static bool DeviceIsSimulator() {
  return ios::GetDeviceName() == "x86_64";
}

// Helper method that logs essential device information strings.
static void LogDeviceInfo() {
  RTC_LOG(LS_INFO) << "LogDeviceInfo";
  @autoreleasepool {
    RTC_LOG(LS_INFO) << " system name: " << ios::GetSystemName();
    RTC_LOG(LS_INFO) << " system version: " << ios::GetSystemVersionAsString();
    RTC_LOG(LS_INFO) << " device type: " << ios::GetDeviceType();
    RTC_LOG(LS_INFO) << " device name: " << ios::GetDeviceName();
    RTC_LOG(LS_INFO) << " process name: " << ios::GetProcessName();
    RTC_LOG(LS_INFO) << " process ID: " << ios::GetProcessID();
    RTC_LOG(LS_INFO) << " OS version: " << ios::GetOSVersionString();
    RTC_LOG(LS_INFO) << " processing cores: " << ios::GetProcessorCount();
    RTC_LOG(LS_INFO) << " low power mode: " << ios::GetLowPowerModeEnabled();
#if TARGET_IPHONE_SIMULATOR
    RTC_LOG(LS_INFO) << " TARGET_IPHONE_SIMULATOR is defined";
#endif
    RTC_LOG(LS_INFO) << " DeviceIsSimulator: " << DeviceIsSimulator();
  }
}
#endif  // !defined(NDEBUG)

AudioDeviceIOS::AudioDeviceIOS(bool bypass_voice_processing, bool disable_recording, bool enableSystemMute, int numChannels)
    : bypass_voice_processing_(bypass_voice_processing),
      disable_recording_(disable_recording),
      enableSystemMute_(enableSystemMute),
      numChannels_(numChannels),
      audio_device_buffer_(nullptr),
      audio_unit_(nullptr),
      recording_(0),
      playing_(0),
      initialized_(false),
      audio_is_initialized_(false),
      is_interrupted_(false),
      has_configured_session_(false),
      num_detected_playout_glitches_(0),
      last_playout_time_(0),
      num_playout_callbacks_(0),
      last_output_volume_change_time_(0) {
  LOGI() << "ctor" << ios::GetCurrentThreadDescription()
         << ",bypass_voice_processing=" << bypass_voice_processing_;
  io_thread_checker_.Detach();
  thread_ = rtc::Thread::Current();

  audio_session_observer_ = [[RTCNativeAudioSessionDelegateAdapter alloc] initWithObserver:this];
}

AudioDeviceIOS::~AudioDeviceIOS() {
  RTC_DCHECK_RUN_ON(thread_);
  LOGI() << "~dtor" << ios::GetCurrentThreadDescription();
  safety_->SetNotAlive();
  Terminate();
  audio_session_observer_ = nil;
}

void AudioDeviceIOS::AttachAudioBuffer(AudioDeviceBuffer* audioBuffer) {
  LOGI() << "AttachAudioBuffer";
  RTC_DCHECK(audioBuffer);
  RTC_DCHECK_RUN_ON(thread_);
  audio_device_buffer_ = audioBuffer;
}

AudioDeviceGeneric::InitStatus AudioDeviceIOS::Init() {
  LOGI() << "Init";
  io_thread_checker_.Detach();

  RTC_DCHECK_RUN_ON(thread_);
  if (initialized_) {
    return InitStatus::OK;
  }
#if !defined(NDEBUG)
  LogDeviceInfo();
#endif
  // Store the preferred sample rate and preferred number of channels already
  // here. They have not been set and confirmed yet since configureForWebRTC
  // is not called until audio is about to start. However, it makes sense to
  // store the parameters now and then verify at a later stage.
  RTC_OBJC_TYPE(RTCAudioSessionConfiguration)* config =
      [RTC_OBJC_TYPE(RTCAudioSessionConfiguration) webRTCConfiguration];
  playout_parameters_.reset(config.sampleRate, config.outputNumberOfChannels);
  record_parameters_.reset(config.sampleRate, config.inputNumberOfChannels);
  // Ensure that the audio device buffer (ADB) knows about the internal audio
  // parameters. Note that, even if we are unable to get a mono audio session,
  // we will always tell the I/O audio unit to do a channel format conversion
  // to guarantee mono on the "input side" of the audio unit.
  UpdateAudioDeviceBuffer();
  initialized_ = true;
  return InitStatus::OK;
}

int32_t AudioDeviceIOS::Terminate() {
  LOGI() << "Terminate";
  RTC_DCHECK_RUN_ON(thread_);
  if (!initialized_) {
    return 0;
  }
  StopPlayout();
  StopRecording();
  initialized_ = false;
  return 0;
}

bool AudioDeviceIOS::Initialized() const {
  RTC_DCHECK_RUN_ON(thread_);
  return initialized_;
}

int32_t AudioDeviceIOS::InitPlayout() {
  LOGI() << "InitPlayout";
  RTC_DCHECK_RUN_ON(thread_);
  RTC_DCHECK(initialized_);
  RTC_DCHECK(!audio_is_initialized_);
  RTC_DCHECK(!playing_.load());
  if (!audio_is_initialized_) {
    if (!InitPlayOrRecord()) {
      RTC_LOG_F(LS_ERROR) << "InitPlayOrRecord failed for InitPlayout!";
      return -1;
    }
  }
  audio_is_initialized_ = true;
  return 0;
}

bool AudioDeviceIOS::PlayoutIsInitialized() const {
  RTC_DCHECK_RUN_ON(thread_);
  return audio_is_initialized_;
}

bool AudioDeviceIOS::RecordingIsInitialized() const {
  RTC_DCHECK_RUN_ON(thread_);
  return audio_is_initialized_;
}

int32_t AudioDeviceIOS::InitRecording() {
  LOGI() << "InitRecording";
  RTC_DCHECK_RUN_ON(thread_);
  RTC_DCHECK(initialized_);
  RTC_DCHECK(!audio_is_initialized_);
  RTC_DCHECK(!recording_.load());
  if (!audio_is_initialized_) {
    if (!InitPlayOrRecord()) {
      RTC_LOG_F(LS_ERROR) << "InitPlayOrRecord failed for InitRecording!";
      return -1;
    }
  }
  audio_is_initialized_ = true;
  return 0;
}

int32_t AudioDeviceIOS::StartPlayout() {
  LOGI() << "StartPlayout";
  RTC_DCHECK_RUN_ON(thread_);
  RTC_DCHECK(audio_is_initialized_);
  RTC_DCHECK(!playing_.load());
  RTC_DCHECK(audio_unit_);
  if (fine_audio_buffer_) {
    fine_audio_buffer_->ResetPlayout();
  }
  if (!recording_.load() && audio_unit_->GetState() == VoiceProcessingAudioUnit::kInitialized) {
    OSStatus result = audio_unit_->Start();
    if (result != noErr) {
      RTC_OBJC_TYPE(RTCAudioSession)* session = [RTC_OBJC_TYPE(RTCAudioSession) sharedInstance];
      [session notifyAudioUnitStartFailedWithError:result];
      RTCLogError(@"StartPlayout failed to start audio unit, reason %d", result);
      return -1;
    }
    RTC_LOG(LS_INFO) << "Voice-Processing I/O audio unit is now started";
  }
  playing_.store(1, std::memory_order_release);
  num_playout_callbacks_ = 0;
  num_detected_playout_glitches_ = 0;
  return 0;
}

int32_t AudioDeviceIOS::StopPlayout() {
  LOGI() << "StopPlayout";
  RTC_DCHECK_RUN_ON(thread_);
  if (!audio_is_initialized_ || !playing_.load()) {
    return 0;
  }
  if (!recording_.load()) {
    ShutdownPlayOrRecord();
    audio_is_initialized_ = false;
  }
  playing_.store(0, std::memory_order_release);

  // Derive average number of calls to OnGetPlayoutData() between detected
  // audio glitches and add the result to a histogram.
  int average_number_of_playout_callbacks_between_glitches = 100000;
  RTC_DCHECK_GE(num_playout_callbacks_, num_detected_playout_glitches_);
  if (num_detected_playout_glitches_ > 0) {
    average_number_of_playout_callbacks_between_glitches =
        num_playout_callbacks_ / num_detected_playout_glitches_;
  }
  RTC_HISTOGRAM_COUNTS_100000("WebRTC.Audio.AveragePlayoutCallbacksBetweenGlitches",
                              average_number_of_playout_callbacks_between_glitches);
  RTCLog(@"Average number of playout callbacks between glitches: %d",
         average_number_of_playout_callbacks_between_glitches);
  return 0;
}

bool AudioDeviceIOS::Playing() const {
  return playing_.load();
}

int32_t AudioDeviceIOS::StartRecording() {
  LOGI() << "StartRecording";
  RTC_DCHECK_RUN_ON(thread_);
  RTC_DCHECK(audio_is_initialized_);
  RTC_DCHECK(!recording_.load());
  RTC_DCHECK(audio_unit_);
  if (fine_audio_buffer_) {
    fine_audio_buffer_->ResetRecord();
  }
  if (!playing_.load() && audio_unit_->GetState() == VoiceProcessingAudioUnit::kInitialized) {
    OSStatus result = audio_unit_->Start();
    if (result != noErr) {
      RTC_OBJC_TYPE(RTCAudioSession)* session = [RTC_OBJC_TYPE(RTCAudioSession) sharedInstance];
      [session notifyAudioUnitStartFailedWithError:result];
      RTCLogError(@"StartRecording failed to start audio unit, reason %d", result);
      return -1;
    }
    RTC_LOG(LS_INFO) << "Voice-Processing I/O audio unit is now started";
  }
  recording_.store(1, std::memory_order_release);
  return 0;
}

int32_t AudioDeviceIOS::StopRecording() {
  LOGI() << "StopRecording";
  RTC_DCHECK_RUN_ON(thread_);
  if (!audio_is_initialized_ || !recording_.load()) {
    return 0;
  }
  if (!playing_.load()) {
    ShutdownPlayOrRecord();
    audio_is_initialized_ = false;
  }
  recording_.store(0, std::memory_order_release);
  return 0;
}

bool AudioDeviceIOS::Recording() const {
  return recording_.load();
}

void AudioDeviceIOS::setIsBufferPlaying(bool isBufferPlaying) {
  isBufferPlaying_ = isBufferPlaying;
}

void AudioDeviceIOS::setIsBufferRecording(bool isBufferRecording) {
  isBufferRecording_ = isBufferRecording;
}

int32_t AudioDeviceIOS::PlayoutDelay(uint16_t& delayMS) const {
  delayMS = kFixedPlayoutDelayEstimate;
  return 0;
}

int AudioDeviceIOS::GetPlayoutAudioParameters(AudioParameters* params) const {
  LOGI() << "GetPlayoutAudioParameters";
  RTC_DCHECK(playout_parameters_.is_valid());
  RTC_DCHECK_RUN_ON(thread_);
  *params = playout_parameters_;
  return 0;
}

int AudioDeviceIOS::GetRecordAudioParameters(AudioParameters* params) const {
  LOGI() << "GetRecordAudioParameters";
  RTC_DCHECK(record_parameters_.is_valid());
  RTC_DCHECK_RUN_ON(thread_);
  *params = record_parameters_;
  return 0;
}

void AudioDeviceIOS::OnInterruptionBegin() {
  RTC_DCHECK(thread_);
  LOGI() << "OnInterruptionBegin";
  thread_->PostTask(SafeTask(safety_, [this] { HandleInterruptionBegin(); }));
}

void AudioDeviceIOS::OnInterruptionEnd() {
  RTC_DCHECK(thread_);
  LOGI() << "OnInterruptionEnd";
  thread_->PostTask(SafeTask(safety_, [this] { HandleInterruptionEnd(); }));
}

void AudioDeviceIOS::OnValidRouteChange() {
  RTC_DCHECK(thread_);
  thread_->PostTask(SafeTask(safety_, [this] { HandleValidRouteChange(); }));
}

void AudioDeviceIOS::OnCanPlayOrRecordChange(bool can_play_or_record) {
  RTC_DCHECK(thread_);
  thread_->PostTask(SafeTask(
      safety_, [this, can_play_or_record] { HandleCanPlayOrRecordChange(can_play_or_record); }));
}

void AudioDeviceIOS::OnChangedOutputVolume() {
  RTC_DCHECK(thread_);
  thread_->PostTask(SafeTask(safety_, [this] { HandleOutputVolumeChange(); }));
}

void AudioDeviceIOS::setTone(std::shared_ptr<tgcalls::CallAudioTone> tone) {
    RTC_DCHECK(thread_);
    thread_->PostTask(SafeTask(safety_, [this, tone] {
        _tone = tone;
        _hasTone.store(true);
    }));
}

OSStatus AudioDeviceIOS::OnDeliverRecordedData(AudioUnitRenderActionFlags* flags,
                                               const AudioTimeStamp* time_stamp,
                                               UInt32 bus_number,
                                               UInt32 num_frames,
                                               AudioBufferList* /* io_data */) {
  RTC_DCHECK_RUN_ON(&io_thread_checker_);
  OSStatus result = noErr;
  // Simply return if recording is not enabled.
  if (!recording_.load(std::memory_order_acquire)) return result;

  // Set the size of our own audio buffer and clear it first to avoid copying
  // in combination with potential reallocations.
  // On real iOS devices, the size will only be set once (at first callback).
  record_audio_buffer_.Clear();
  record_audio_buffer_.SetSize(num_frames);

  // Allocate AudioBuffers to be used as storage for the received audio.
  // The AudioBufferList structure works as a placeholder for the
  // AudioBuffer structure, which holds a pointer to the actual data buffer
  // in `record_audio_buffer_`. Recorded audio will be rendered into this memory
  // at each input callback when calling AudioUnitRender().
  AudioBufferList audio_buffer_list;
  audio_buffer_list.mNumberBuffers = 1;
  AudioBuffer* audio_buffer = &audio_buffer_list.mBuffers[0];
  audio_buffer->mNumberChannels = record_parameters_.channels();
  audio_buffer->mDataByteSize =
      record_audio_buffer_.size() * VoiceProcessingAudioUnit::kBytesPerSample;
  audio_buffer->mData = reinterpret_cast<int8_t*>(record_audio_buffer_.data());

  // Obtain the recorded audio samples by initiating a rendering cycle.
  // Since it happens on the input bus, the `io_data` parameter is a reference
  // to the preallocated audio buffer list that the audio unit renders into.
  // We can make the audio unit provide a buffer instead in io_data, but we
  // currently just use our own.
  // TODO(henrika): should error handling be improved?
  result = audio_unit_->Render(flags, time_stamp, bus_number, num_frames, &audio_buffer_list);
  if (result != noErr) {
    RTCLogError(@"Failed to render audio.");
    return result;
  }

  // Get a pointer to the recorded audio and send it to the WebRTC ADB.
  // Use the FineAudioBuffer instance to convert between native buffer size
  // and the 10ms buffer size used by WebRTC.
 
  if (isBufferRecording_) {
    fine_audio_buffer_->DeliverRecordedData(record_audio_buffer_, kFixedRecordDelayEstimate);
  }
  return noErr;
}

OSStatus AudioDeviceIOS::OnGetPlayoutData(AudioUnitRenderActionFlags* flags,
                                          const AudioTimeStamp* time_stamp,
                                          UInt32 bus_number,
                                          UInt32 num_frames,
                                          AudioBufferList* io_data) {
  RTC_DCHECK_RUN_ON(&io_thread_checker_);
  // Verify 16-bit, noninterleaved mono PCM signal format.
  RTC_DCHECK_EQ(1, io_data->mNumberBuffers);
  AudioBuffer* audio_buffer = &io_data->mBuffers[0];
  //RTC_DCHECK_EQ(1, audio_buffer->mNumberChannels);

  // Produce silence and give audio unit a hint about it if playout is not
  // activated.
  if (!playing_.load(std::memory_order_acquire)) {
    const size_t size_in_bytes = audio_buffer->mDataByteSize;
    RTC_CHECK_EQ((size_in_bytes / audio_buffer->mNumberChannels) / VoiceProcessingAudioUnit::kBytesPerSample, num_frames);
    *flags |= kAudioUnitRenderAction_OutputIsSilence;
    memset(static_cast<int8_t*>(audio_buffer->mData), 0, size_in_bytes);
    return noErr;
  }

  // Measure time since last call to OnGetPlayoutData() and see if it is larger
  // than a well defined threshold which depends on the current IO buffer size.
  // If so, we have an indication of a glitch in the output audio since the
  // core audio layer will most likely run dry in this state.
  ++num_playout_callbacks_;
  const int64_t now_time = rtc::TimeMillis();
  if (time_stamp->mSampleTime != num_frames) {
    const int64_t delta_time = now_time - last_playout_time_;
    const int glitch_threshold = 1.6 * playout_parameters_.GetBufferSizeInMilliseconds();
    if (delta_time > glitch_threshold) {
      RTCLogWarning(@"Possible playout audio glitch detected.\n"
                     "  Time since last OnGetPlayoutData was %lld ms.\n",
                    delta_time);
      // Exclude extreme delta values since they do most likely not correspond
      // to a real glitch. Instead, the most probable cause is that a headset
      // has been plugged in or out. There are more direct ways to detect
      // audio device changes (see HandleValidRouteChange()) but experiments
      // show that using it leads to more complex implementations.
      // TODO(henrika): more tests might be needed to come up with an even
      // better upper limit.
      if (glitch_threshold < 120 && delta_time > 120) {
        RTCLog(@"Glitch warning is ignored. Probably caused by device switch.");
      } else {
        thread_->PostTask(SafeTask(safety_, [this] { HandlePlayoutGlitchDetected(); }));
      }
    }
  }
  last_playout_time_ = now_time;

  // Read decoded 16-bit PCM samples from WebRTC (using a size that matches
  // the native I/O audio unit) and copy the result to the audio buffer in the
  // `io_data` destination.
  if (isBufferPlaying_) {
    fine_audio_buffer_->GetPlayoutData(
      rtc::ArrayView<int16_t>(static_cast<int16_t*>(audio_buffer->mData), num_frames * audio_buffer->mNumberChannels), kFixedPlayoutDelayEstimate);
  } else {
    memset(audio_buffer->mData, 0, num_frames * audio_buffer->mNumberChannels * sizeof(int16_t));
  }

  if (_hasTone.load()) {
      int16_t *mixDestination = static_cast<int16_t*>(audio_buffer->mData);
      
      auto tone = _tone;
      if (tone) {
          size_t destinationOffset = 0;
          size_t destinationMax = num_frames * audio_buffer->mNumberChannels;
          std::vector<int16_t> const &samples = tone->samples();
          size_t toneOffset = tone->offset();
          
          while (destinationOffset < destinationMax) {
              while (toneOffset < samples.size() && destinationOffset < destinationMax) {
                  for (int i = 0; i < audio_buffer->mNumberChannels; i++) {
                      int32_t current = mixDestination[destinationOffset];
                      current = current + samples[toneOffset];
                      if (current > INT16_MAX) {
                          current = INT16_MAX;
                      }
                      if (current < INT16_MIN) {
                          current = INT16_MIN;
                      }
                      mixDestination[destinationOffset] = current;
                      destinationOffset++;
                  }
                  toneOffset++;
              }
              if (toneOffset >= samples.size()) {
                  if (tone->loopCount() <= 1) {
                      _tone.reset();
                      tone.reset();
                      _hasTone.store(false);
                      break;
                  } else {
                      tone->setLoopCount(tone->loopCount() - 1);
                      toneOffset = 0;
                  }
              }
          }
          if (tone) {
              tone->setOffset(toneOffset);
          }
      } else {
          _hasTone.store(false);
      }
  }

  return noErr;
}

void AudioDeviceIOS::OnMutedSpeechStatusChanged(bool isDetectingSpeech) {
    if (mutedSpeechDetectionChanged) {
        mutedSpeechDetectionChanged(isDetectingSpeech);
    }
}

void AudioDeviceIOS::HandleInterruptionBegin() {
  RTC_DCHECK_RUN_ON(thread_);
  RTCLog(@"Interruption begin. IsInterrupted changed from %d to 1.", is_interrupted_);
  if (audio_unit_ && audio_unit_->GetState() == VoiceProcessingAudioUnit::kStarted) {
    RTCLog(@"Stopping the audio unit due to interruption begin.");
    if (!audio_unit_->Stop()) {
      RTCLogError(@"Failed to stop the audio unit for interruption begin.");
    }
    PrepareForNewStart();
  }
  is_interrupted_ = true;
}

void AudioDeviceIOS::HandleInterruptionEnd() {
  RTC_DCHECK_RUN_ON(thread_);
  RTCLog(@"Interruption ended. IsInterrupted changed from %d to 0. "
          "Updating audio unit state.",
         is_interrupted_);
  is_interrupted_ = false;
  if (!audio_unit_) return;
  if (webrtc::field_trial::IsEnabled("WebRTC-Audio-iOS-Holding")) {
    // Work around an issue where audio does not restart properly after an interruption
    // by restarting the audio unit when the interruption ends.
    if (audio_unit_->GetState() == VoiceProcessingAudioUnit::kStarted) {
      audio_unit_->Stop();
      PrepareForNewStart();
    }
    if (audio_unit_->GetState() == VoiceProcessingAudioUnit::kInitialized) {
      audio_unit_->Uninitialize();
    }
    // Allocate new buffers given the potentially new stream format.
    SetupAudioBuffersForActiveAudioSession();
  }
  UpdateAudioUnit([RTC_OBJC_TYPE(RTCAudioSession) sharedInstance].canPlayOrRecord);
}

void AudioDeviceIOS::HandleValidRouteChange() {
  RTC_DCHECK_RUN_ON(thread_);
  RTC_OBJC_TYPE(RTCAudioSession)* session = [RTC_OBJC_TYPE(RTCAudioSession) sharedInstance];
  RTCLog(@"%@", session);
  HandleSampleRateChange();
}

void AudioDeviceIOS::HandleCanPlayOrRecordChange(bool can_play_or_record) {
  RTCLog(@"Handling CanPlayOrRecord change to: %d", can_play_or_record);
  UpdateAudioUnit(can_play_or_record);
}

void AudioDeviceIOS::HandleSampleRateChange() {
  RTC_DCHECK_RUN_ON(thread_);
  RTCLog(@"Handling sample rate change.");

  // Don't do anything if we're interrupted.
  if (is_interrupted_) {
    RTCLog(@"Ignoring sample rate change due to interruption.");
    return;
  }

  // If we don't have an audio unit yet, or the audio unit is uninitialized,
  // there is no work to do.
  if (!audio_unit_ || audio_unit_->GetState() < VoiceProcessingAudioUnit::kInitialized) {
    return;
  }

  // The audio unit is already initialized or started.
  // Check to see if the sample rate or buffer size has changed.
  RTC_OBJC_TYPE(RTCAudioSession)* session = [RTC_OBJC_TYPE(RTCAudioSession) sharedInstance];
  const double new_sample_rate = session.sampleRate;
  const NSTimeInterval session_buffer_duration = session.IOBufferDuration;
  const size_t new_frames_per_buffer =
      static_cast<size_t>(new_sample_rate * session_buffer_duration + .5);
  const double current_sample_rate = playout_parameters_.sample_rate();
  const size_t current_frames_per_buffer = playout_parameters_.frames_per_buffer();
  RTCLog(@"Handling playout sample rate change:\n"
          "  Session sample rate: %f frames_per_buffer: %lu\n"
          "  ADM sample rate: %f frames_per_buffer: %lu",
         new_sample_rate,
         (unsigned long)new_frames_per_buffer,
         current_sample_rate,
         (unsigned long)current_frames_per_buffer);

  // Sample rate and buffer size are the same, no work to do.
  if (std::abs(current_sample_rate - new_sample_rate) <= DBL_EPSILON &&
      current_frames_per_buffer == new_frames_per_buffer) {
    RTCLog(@"Ignoring sample rate change since audio parameters are intact.");
    return;
  }

  // Extra sanity check to ensure that the new sample rate is valid.
  if (new_sample_rate <= 0.0) {
    RTCLogError(@"Sample rate is invalid: %f", new_sample_rate);
    return;
  }

  // We need to adjust our format and buffer sizes.
  // The stream format is about to be changed and it requires that we first
  // stop and uninitialize the audio unit to deallocate its resources.
  RTCLog(@"Stopping and uninitializing audio unit to adjust buffers.");
  bool restart_audio_unit = false;
  if (audio_unit_->GetState() == VoiceProcessingAudioUnit::kStarted) {
    audio_unit_->Stop();
    restart_audio_unit = true;
    PrepareForNewStart();
  }
  if (audio_unit_->GetState() == VoiceProcessingAudioUnit::kInitialized) {
    audio_unit_->Uninitialize();
  }

  // Allocate new buffers given the new stream format.
  SetupAudioBuffersForActiveAudioSession();

  // Initialize the audio unit again with the new sample rate.
  if (!audio_unit_->Initialize(playout_parameters_.sample_rate())) {
    RTCLogError(@"Failed to initialize the audio unit with sample rate: %d",
                playout_parameters_.sample_rate());
    return;
  }

  // Restart the audio unit if it was already running.
  if (restart_audio_unit) {
    OSStatus result = audio_unit_->Start();
    if (result != noErr) {
      RTC_OBJC_TYPE(RTCAudioSession)* session = [RTC_OBJC_TYPE(RTCAudioSession) sharedInstance];
      [session notifyAudioUnitStartFailedWithError:result];
      RTCLogError(@"Failed to start audio unit with sample rate: %d, reason %d",
                  playout_parameters_.sample_rate(),
                  result);
      return;
    }
  }
  RTCLog(@"Successfully handled sample rate change.");
}

void AudioDeviceIOS::HandlePlayoutGlitchDetected() {
  RTC_DCHECK_RUN_ON(thread_);
  // Don't update metrics if we're interrupted since a "glitch" is expected
  // in this state.
  if (is_interrupted_) {
    RTCLog(@"Ignoring audio glitch due to interruption.");
    return;
  }
  // Avoid doing glitch detection for two seconds after a volume change
  // has been detected to reduce the risk of false alarm.
  if (last_output_volume_change_time_ > 0 &&
      rtc::TimeSince(last_output_volume_change_time_) < 2000) {
    RTCLog(@"Ignoring audio glitch due to recent output volume change.");
    return;
  }
  num_detected_playout_glitches_++;
  RTCLog(@"Number of detected playout glitches: %lld", num_detected_playout_glitches_);

  int64_t glitch_count = num_detected_playout_glitches_;
  dispatch_async(dispatch_get_main_queue(), ^{
    RTC_OBJC_TYPE(RTCAudioSession)* session = [RTC_OBJC_TYPE(RTCAudioSession) sharedInstance];
    [session notifyDidDetectPlayoutGlitch:glitch_count];
  });
}

void AudioDeviceIOS::HandleOutputVolumeChange() {
  RTC_DCHECK_RUN_ON(thread_);
  RTCLog(@"Output volume change detected.");
  // Store time of this detection so it can be used to defer detection of
  // glitches too close in time to this event.
  last_output_volume_change_time_ = rtc::TimeMillis();
}

void AudioDeviceIOS::UpdateAudioDeviceBuffer() {
  LOGI() << "UpdateAudioDevicebuffer";
  // AttachAudioBuffer() is called at construction by the main class but check
  // just in case.
  RTC_DCHECK(audio_device_buffer_) << "AttachAudioBuffer must be called first";
  RTC_DCHECK_GT(playout_parameters_.sample_rate(), 0);
  RTC_DCHECK_GT(record_parameters_.sample_rate(), 0);
  //RTC_DCHECK_EQ(playout_parameters_.channels(), 1);
  RTC_DCHECK_EQ(record_parameters_.channels(), 1);
  // Inform the audio device buffer (ADB) about the new audio format.
  audio_device_buffer_->SetPlayoutSampleRate(playout_parameters_.sample_rate());
  audio_device_buffer_->SetPlayoutChannels(playout_parameters_.channels());
  audio_device_buffer_->SetRecordingSampleRate(record_parameters_.sample_rate());
  audio_device_buffer_->SetRecordingChannels(record_parameters_.channels());
}

void AudioDeviceIOS::SetupAudioBuffersForActiveAudioSession() {
  LOGI() << "SetupAudioBuffersForActiveAudioSession";
  // Verify the current values once the audio session has been activated.
  RTC_OBJC_TYPE(RTCAudioSession)* session = [RTC_OBJC_TYPE(RTCAudioSession) sharedInstance];
  double sample_rate = session.sampleRate;
  NSTimeInterval io_buffer_duration = session.IOBufferDuration;
  RTCLog(@"%@", session);

  // Log a warning message for the case when we are unable to set the preferred
  // hardware sample rate but continue and use the non-ideal sample rate after
  // reinitializing the audio parameters. Most BT headsets only support 8kHz or
  // 16kHz.
  RTC_OBJC_TYPE(RTCAudioSessionConfiguration)* webRTCConfig =
      [RTC_OBJC_TYPE(RTCAudioSessionConfiguration) webRTCConfiguration];
  if (sample_rate != webRTCConfig.sampleRate) {
    RTC_LOG(LS_WARNING) << "Unable to set the preferred sample rate";
  }

  // Crash reports indicates that it can happen in rare cases that the reported
  // sample rate is less than or equal to zero. If that happens and if a valid
  // sample rate has already been set during initialization, the best guess we
  // can do is to reuse the current sample rate.
  if (sample_rate <= DBL_EPSILON && playout_parameters_.sample_rate() > 0) {
    RTCLogError(@"Reported rate is invalid: %f. "
                 "Using %d as sample rate instead.",
                sample_rate, playout_parameters_.sample_rate());
    sample_rate = playout_parameters_.sample_rate();
  }

  // At this stage, we also know the exact IO buffer duration and can add
  // that info to the existing audio parameters where it is converted into
  // number of audio frames.
  // Example: IO buffer size = 0.008 seconds <=> 128 audio frames at 16kHz.
  // Hence, 128 is the size we expect to see in upcoming render callbacks.
  playout_parameters_.reset(sample_rate, playout_parameters_.channels(), io_buffer_duration);
  RTC_DCHECK(playout_parameters_.is_complete());
  record_parameters_.reset(sample_rate, record_parameters_.channels(), io_buffer_duration);
  RTC_DCHECK(record_parameters_.is_complete());
  RTC_LOG(LS_INFO) << " frames per I/O buffer: " << playout_parameters_.frames_per_buffer();
  RTC_LOG(LS_INFO) << " bytes per I/O buffer: " << playout_parameters_.GetBytesPerBuffer();
  //RTC_DCHECK_EQ(playout_parameters_.GetBytesPerBuffer(), record_parameters_.GetBytesPerBuffer());

  // Update the ADB parameters since the sample rate might have changed.
  UpdateAudioDeviceBuffer();

  // Create a modified audio buffer class which allows us to ask for,
  // or deliver, any number of samples (and not only multiple of 10ms) to match
  // the native audio unit buffer size.
  RTC_DCHECK(audio_device_buffer_);
  fine_audio_buffer_.reset(new FineAudioBuffer(audio_device_buffer_));
}

bool AudioDeviceIOS::CreateAudioUnit() {
  RTC_DCHECK(!audio_unit_);

  audio_unit_.reset(new VoiceProcessingAudioUnit(bypass_voice_processing_, disable_recording_, enableSystemMute_, numChannels_, this));
  if (!audio_unit_->Init()) {
    audio_unit_.reset();
    return false;
  }

  return true;
}

void AudioDeviceIOS::UpdateAudioUnit(bool can_play_or_record) {
  RTC_DCHECK_RUN_ON(thread_);
  RTCLog(@"Updating audio unit state. CanPlayOrRecord=%d IsInterrupted=%d",
         can_play_or_record,
         is_interrupted_);

  if (is_interrupted_) {
    RTCLog(@"Ignoring audio unit update due to interruption.");
    return;
  }

  // If we're not initialized we don't need to do anything. Audio unit will
  // be initialized on initialization.
  if (!audio_is_initialized_) return;

  // If we're initialized, we must have an audio unit.
  RTC_DCHECK(audio_unit_);

  bool should_initialize_audio_unit = false;
  bool should_uninitialize_audio_unit = false;
  bool should_start_audio_unit = false;
  bool should_stop_audio_unit = false;

  switch (audio_unit_->GetState()) {
    case VoiceProcessingAudioUnit::kInitRequired:
      RTCLog(@"VPAU state: InitRequired");
      RTC_DCHECK_NOTREACHED();
      break;
    case VoiceProcessingAudioUnit::kUninitialized:
      RTCLog(@"VPAU state: Uninitialized");
      should_initialize_audio_unit = can_play_or_record;
      should_start_audio_unit =
          should_initialize_audio_unit && (playing_.load() || recording_.load());
      break;
    case VoiceProcessingAudioUnit::kInitialized:
      RTCLog(@"VPAU state: Initialized");
      should_start_audio_unit = can_play_or_record && (playing_.load() || recording_.load());
      should_uninitialize_audio_unit = !can_play_or_record;
      break;
    case VoiceProcessingAudioUnit::kStarted:
      RTCLog(@"VPAU state: Started");
      RTC_DCHECK(playing_.load() || recording_.load());
      should_stop_audio_unit = !can_play_or_record;
      should_uninitialize_audio_unit = should_stop_audio_unit;
      break;
  }

  if (should_initialize_audio_unit) {
    RTCLog(@"Initializing audio unit for UpdateAudioUnit");
    ConfigureAudioSession();
    SetupAudioBuffersForActiveAudioSession();
    if (!audio_unit_->Initialize(playout_parameters_.sample_rate())) {
      RTCLogError(@"Failed to initialize audio unit.");
      return;
    }
  }

  if (should_start_audio_unit) {
    RTCLog(@"Starting audio unit for UpdateAudioUnit");
    // Log session settings before trying to start audio streaming.
    RTC_OBJC_TYPE(RTCAudioSession)* session = [RTC_OBJC_TYPE(RTCAudioSession) sharedInstance];
    RTCLog(@"%@", session);
    OSStatus result = audio_unit_->Start();
    if (result != noErr) {
      [session notifyAudioUnitStartFailedWithError:result];
      RTCLogError(@"Failed to start audio unit, reason %d", result);
      return;
    }
  }

  if (should_stop_audio_unit) {
    RTCLog(@"Stopping audio unit for UpdateAudioUnit");
    if (!audio_unit_->Stop()) {
      RTCLogError(@"Failed to stop audio unit.");
      PrepareForNewStart();
      return;
    }
    PrepareForNewStart();
  }

  if (should_uninitialize_audio_unit) {
    RTCLog(@"Uninitializing audio unit for UpdateAudioUnit");
    audio_unit_->Uninitialize();
    UnconfigureAudioSession();
  }
}

bool AudioDeviceIOS::ConfigureAudioSession() {
  RTC_DCHECK_RUN_ON(thread_);
  RTCLog(@"Configuring audio session.");
  if (has_configured_session_) {
    RTCLogWarning(@"Audio session already configured.");
    return false;
  }
  RTC_OBJC_TYPE(RTCAudioSession)* session = [RTC_OBJC_TYPE(RTCAudioSession) sharedInstance];
  [session lockForConfiguration];
  bool success = [session configureWebRTCSession:nil disableRecording:disable_recording_];
  [session unlockForConfiguration];
  if (success) {
    has_configured_session_ = true;
    RTCLog(@"Configured audio session.");
  } else {
    RTCLog(@"Failed to configure audio session.");
  }
  return success;
}

bool AudioDeviceIOS::ConfigureAudioSessionLocked() {
  RTC_DCHECK_RUN_ON(thread_);
  RTCLog(@"Configuring audio session.");
  if (has_configured_session_) {
    RTCLogWarning(@"Audio session already configured.");
    return false;
  }
  RTC_OBJC_TYPE(RTCAudioSession)* session = [RTC_OBJC_TYPE(RTCAudioSession) sharedInstance];
  bool success = [session configureWebRTCSession:nil disableRecording:disable_recording_];
  if (success) {
    has_configured_session_ = true;
    RTCLog(@"Configured audio session.");
  } else {
    RTCLog(@"Failed to configure audio session.");
  }
  return success;
}

void AudioDeviceIOS::UnconfigureAudioSession() {
  RTC_DCHECK_RUN_ON(thread_);
  RTCLog(@"Unconfiguring audio session.");
  if (!has_configured_session_) {
    RTCLogWarning(@"Audio session already unconfigured.");
    return;
  }
  RTC_OBJC_TYPE(RTCAudioSession)* session = [RTC_OBJC_TYPE(RTCAudioSession) sharedInstance];
  [session lockForConfiguration];
  [session unconfigureWebRTCSession:nil];
  if (!disable_recording_) {
    [session endWebRTCSession:nil];
  }
  [session unlockForConfiguration];
  has_configured_session_ = false;
  RTCLog(@"Unconfigured audio session.");
}

bool AudioDeviceIOS::InitPlayOrRecord() {
  LOGI() << "InitPlayOrRecord";
  RTC_DCHECK_RUN_ON(thread_);

  // There should be no audio unit at this point.
  if (!CreateAudioUnit()) {
    return false;
  }

  RTC_OBJC_TYPE(RTCAudioSession)* session = [RTC_OBJC_TYPE(RTCAudioSession) sharedInstance];
  // Subscribe to audio session events.
  [session pushDelegate:audio_session_observer_];
  is_interrupted_ = session.isInterrupted ? true : false;

  // Lock the session to make configuration changes.
  [session lockForConfiguration];
  NSError* error = nil;
  if (![session beginWebRTCSession:&error]) {
    [session unlockForConfiguration];
    RTCLogError(@"Failed to begin WebRTC session: %@", error.localizedDescription);
    audio_unit_.reset();
    return false;
  }

  // If we are ready to play or record, and if the audio session can be
  // configured, then initialize the audio unit.
  if (session.canPlayOrRecord) {
    if (!ConfigureAudioSessionLocked()) {
      // One possible reason for failure is if an attempt was made to use the
      // audio session during or after a Media Services failure.
      // See AVAudioSessionErrorCodeMediaServicesFailed for details.
      [session unlockForConfiguration];
      audio_unit_.reset();
      return false;
    }
    SetupAudioBuffersForActiveAudioSession();
    audio_unit_->Initialize(playout_parameters_.sample_rate());
  }

  // Release the lock.
  [session unlockForConfiguration];
  return true;
}

void AudioDeviceIOS::ShutdownPlayOrRecord() {
  LOGI() << "ShutdownPlayOrRecord";
  RTC_DCHECK_RUN_ON(thread_);

  // Stop the audio unit to prevent any additional audio callbacks.
  audio_unit_->Stop();

  // Close and delete the voice-processing I/O unit.
  audio_unit_.reset();

  // Detach thread checker for the AURemoteIO::IOThread to ensure that the
  // next session uses a fresh thread id.
  io_thread_checker_.Detach();

  // Remove audio session notification observers.
  RTC_OBJC_TYPE(RTCAudioSession)* session = [RTC_OBJC_TYPE(RTCAudioSession) sharedInstance];
  [session removeDelegate:audio_session_observer_];

  // All I/O should be stopped or paused prior to deactivating the audio
  // session, hence we deactivate as last action.
  UnconfigureAudioSession();
}

void AudioDeviceIOS::PrepareForNewStart() {
  LOGI() << "PrepareForNewStart";
  // The audio unit has been stopped and preparations are needed for an upcoming
  // restart. It will result in audio callbacks from a new native I/O thread
  // which means that we must detach thread checkers here to be prepared for an
  // upcoming new audio stream.
  io_thread_checker_.Detach();
}

bool AudioDeviceIOS::IsInterrupted() {
  return is_interrupted_;
}

#pragma mark - Not Implemented

int32_t AudioDeviceIOS::ActiveAudioLayer(AudioDeviceModule::AudioLayer& audioLayer) const {
  audioLayer = AudioDeviceModule::kPlatformDefaultAudio;
  return 0;
}

int16_t AudioDeviceIOS::PlayoutDevices() {
  // TODO(henrika): improve.
  RTC_LOG_F(LS_WARNING) << "Not implemented";
  return (int16_t)1;
}

int16_t AudioDeviceIOS::RecordingDevices() {
  // TODO(henrika): improve.
  RTC_LOG_F(LS_WARNING) << "Not implemented";
  return (int16_t)1;
}

int32_t AudioDeviceIOS::InitSpeaker() {
  return 0;
}

bool AudioDeviceIOS::SpeakerIsInitialized() const {
  return true;
}

int32_t AudioDeviceIOS::SpeakerVolumeIsAvailable(bool& available) {
  available = false;
  return 0;
}

int32_t AudioDeviceIOS::SetSpeakerVolume(uint32_t volume) {
  RTC_DCHECK_NOTREACHED() << "Not implemented";
  return -1;
}

int32_t AudioDeviceIOS::SpeakerVolume(uint32_t& volume) const {
  RTC_DCHECK_NOTREACHED() << "Not implemented";
  return -1;
}

int32_t AudioDeviceIOS::MaxSpeakerVolume(uint32_t& maxVolume) const {
  RTC_DCHECK_NOTREACHED() << "Not implemented";
  return -1;
}

int32_t AudioDeviceIOS::MinSpeakerVolume(uint32_t& minVolume) const {
  RTC_DCHECK_NOTREACHED() << "Not implemented";
  return -1;
}

int32_t AudioDeviceIOS::SpeakerMuteIsAvailable(bool& available) {
  available = false;
  return 0;
}

int32_t AudioDeviceIOS::SetSpeakerMute(bool enable) {
  RTC_DCHECK_NOTREACHED() << "Not implemented";
  return -1;
}

int32_t AudioDeviceIOS::SpeakerMute(bool& enabled) const {
  RTC_DCHECK_NOTREACHED() << "Not implemented";
  return -1;
}

int32_t AudioDeviceIOS::SetPlayoutDevice(uint16_t index) {
  RTC_LOG_F(LS_WARNING) << "Not implemented";
  return 0;
}

int32_t AudioDeviceIOS::SetPlayoutDevice(AudioDeviceModule::WindowsDeviceType) {
  RTC_LOG_F(LS_WARNING) << "Not implemented";
  return 0;
}

int32_t AudioDeviceIOS::InitMicrophone() {
  return 0;
}

bool AudioDeviceIOS::MicrophoneIsInitialized() const {
  return true;
}

int32_t AudioDeviceIOS::MicrophoneMuteIsAvailable(bool& available) {
  available = true;
  return 0;
}

int32_t AudioDeviceIOS::SetMicrophoneMute(bool enable) {
  isMicrophoneMuted_ = enable;
  if (audio_unit_) {
    audio_unit_->setIsMicrophoneMuted(enable);
  }
  return 0;
}

int32_t AudioDeviceIOS::MicrophoneMute(bool& enabled) const {
  enabled = isMicrophoneMuted_;
  return 0;
}

int32_t AudioDeviceIOS::StereoRecordingIsAvailable(bool& available) {
  available = false;
  return 0;
}

int32_t AudioDeviceIOS::SetStereoRecording(bool enable) {
  RTC_LOG_F(LS_WARNING) << "Not implemented";
  return -1;
}

int32_t AudioDeviceIOS::StereoRecording(bool& enabled) const {
  enabled = false;
  return 0;
}

int32_t AudioDeviceIOS::StereoPlayoutIsAvailable(bool& available) {
  available = false;
  return 0;
}

int32_t AudioDeviceIOS::SetStereoPlayout(bool enable) {
  RTC_LOG_F(LS_WARNING) << "Not implemented";
  return -1;
}

int32_t AudioDeviceIOS::StereoPlayout(bool& enabled) const {
  enabled = false;
  return 0;
}

int32_t AudioDeviceIOS::MicrophoneVolumeIsAvailable(bool& available) {
  available = false;
  return 0;
}

int32_t AudioDeviceIOS::SetMicrophoneVolume(uint32_t volume) {
  RTC_LOG_F(LS_WARNING) << "Not implemented";
  return -1;
}

int32_t AudioDeviceIOS::MicrophoneVolume(uint32_t& volume) const {
  RTC_LOG_F(LS_WARNING) << "Not implemented";
  return -1;
}

int32_t AudioDeviceIOS::MaxMicrophoneVolume(uint32_t& maxVolume) const {
  RTC_LOG_F(LS_WARNING) << "Not implemented";
  return -1;
}

int32_t AudioDeviceIOS::MinMicrophoneVolume(uint32_t& minVolume) const {
  RTC_LOG_F(LS_WARNING) << "Not implemented";
  return -1;
}

int32_t AudioDeviceIOS::PlayoutDeviceName(uint16_t index,
                                          char name[kAdmMaxDeviceNameSize],
                                          char guid[kAdmMaxGuidSize]) {
  RTC_LOG_F(LS_WARNING) << "Not implemented";
  return -1;
}

int32_t AudioDeviceIOS::RecordingDeviceName(uint16_t index,
                                            char name[kAdmMaxDeviceNameSize],
                                            char guid[kAdmMaxGuidSize]) {
  RTC_LOG_F(LS_WARNING) << "Not implemented";
  return -1;
}

int32_t AudioDeviceIOS::SetRecordingDevice(uint16_t index) {
  RTC_LOG_F(LS_WARNING) << "Not implemented";
  return 0;
}

int32_t AudioDeviceIOS::SetRecordingDevice(AudioDeviceModule::WindowsDeviceType) {
  RTC_LOG_F(LS_WARNING) << "Not implemented";
  return 0;
}

int32_t AudioDeviceIOS::PlayoutIsAvailable(bool& available) {
  available = true;
  return 0;
}

int32_t AudioDeviceIOS::RecordingIsAvailable(bool& available) {
  available = true;
  return 0;
}

}  // namespace tgcalls_ios_adm
}  // namespace webrtc
