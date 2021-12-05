/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_device/android/opensles_recorder.h"

#include <android/log.h>

#include <memory>

#include "api/array_view.h"
#include "modules/audio_device/android/audio_common.h"
#include "modules/audio_device/android/audio_manager.h"
#include "modules/audio_device/fine_audio_buffer.h"
#include "rtc_base/arraysize.h"
#include "rtc_base/checks.h"
#include "rtc_base/format_macros.h"
#include "rtc_base/platform_thread.h"
#include "rtc_base/time_utils.h"

#define TAG "OpenSLESRecorder"
#define ALOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, TAG, __VA_ARGS__)
#define ALOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define ALOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

#define LOG_ON_ERROR(op)                                    \
  [](SLresult err) {                                        \
    if (err != SL_RESULT_SUCCESS) {                         \
      ALOGE("%s:%d %s failed: %s", __FILE__, __LINE__, #op, \
            GetSLErrorString(err));                         \
      return true;                                          \
    }                                                       \
    return false;                                           \
  }(op)

namespace webrtc {

OpenSLESRecorder::OpenSLESRecorder(AudioManager* audio_manager)
    : audio_manager_(audio_manager),
      audio_parameters_(audio_manager->GetRecordAudioParameters()),
      audio_device_buffer_(nullptr),
      initialized_(false),
      recording_(false),
      engine_(nullptr),
      recorder_(nullptr),
      simple_buffer_queue_(nullptr),
      buffer_index_(0),
      last_rec_time_(0) {
  ALOGD("ctor[tid=%d]", rtc::CurrentThreadId());
  // Detach from this thread since we want to use the checker to verify calls
  // from the internal  audio thread.
  thread_checker_opensles_.Detach();
  // Use native audio output parameters provided by the audio manager and
  // define the PCM format structure.
  pcm_format_ = CreatePCMConfiguration(audio_parameters_.channels(),
                                       audio_parameters_.sample_rate(),
                                       audio_parameters_.bits_per_sample());
}

OpenSLESRecorder::~OpenSLESRecorder() {
  ALOGD("dtor[tid=%d]", rtc::CurrentThreadId());
  RTC_DCHECK(thread_checker_.IsCurrent());
  Terminate();
  DestroyAudioRecorder();
  engine_ = nullptr;
  RTC_DCHECK(!engine_);
  RTC_DCHECK(!recorder_);
  RTC_DCHECK(!simple_buffer_queue_);
}

int OpenSLESRecorder::Init() {
  ALOGD("Init[tid=%d]", rtc::CurrentThreadId());
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (audio_parameters_.channels() == 2) {
    ALOGD("Stereo mode is enabled");
  }
  return 0;
}

int OpenSLESRecorder::Terminate() {
  ALOGD("Terminate[tid=%d]", rtc::CurrentThreadId());
  RTC_DCHECK(thread_checker_.IsCurrent());
  StopRecording();
  return 0;
}

int OpenSLESRecorder::InitRecording() {
  ALOGD("InitRecording[tid=%d]", rtc::CurrentThreadId());
  RTC_DCHECK(thread_checker_.IsCurrent());
  RTC_DCHECK(!initialized_);
  RTC_DCHECK(!recording_);
  if (!ObtainEngineInterface()) {
    ALOGE("Failed to obtain SL Engine interface");
    return -1;
  }
  CreateAudioRecorder();
  initialized_ = true;
  buffer_index_ = 0;
  return 0;
}

int OpenSLESRecorder::StartRecording() {
  ALOGD("StartRecording[tid=%d]", rtc::CurrentThreadId());
  RTC_DCHECK(thread_checker_.IsCurrent());
  RTC_DCHECK(initialized_);
  RTC_DCHECK(!recording_);
  if (fine_audio_buffer_) {
    fine_audio_buffer_->ResetRecord();
  }
  // Add buffers to the queue before changing state to SL_RECORDSTATE_RECORDING
  // to ensure that recording starts as soon as the state is modified. On some
  // devices, SLAndroidSimpleBufferQueue::Clear() used in Stop() does not flush
  // the buffers as intended and we therefore check the number of buffers
  // already queued first. Enqueue() can return SL_RESULT_BUFFER_INSUFFICIENT
  // otherwise.
  int num_buffers_in_queue = GetBufferCount();
  for (int i = 0; i < kNumOfOpenSLESBuffers - num_buffers_in_queue; ++i) {
    if (!EnqueueAudioBuffer()) {
      recording_ = false;
      return -1;
    }
  }
  num_buffers_in_queue = GetBufferCount();
  RTC_DCHECK_EQ(num_buffers_in_queue, kNumOfOpenSLESBuffers);
  LogBufferState();
  // Start audio recording by changing the state to SL_RECORDSTATE_RECORDING.
  // Given that buffers are already enqueued, recording should start at once.
  // The macro returns -1 if recording fails to start.
  last_rec_time_ = rtc::Time();
  if (LOG_ON_ERROR(
          (*recorder_)->SetRecordState(recorder_, SL_RECORDSTATE_RECORDING))) {
    return -1;
  }
  recording_ = (GetRecordState() == SL_RECORDSTATE_RECORDING);
  RTC_DCHECK(recording_);
  return 0;
}

int OpenSLESRecorder::StopRecording() {
  ALOGD("StopRecording[tid=%d]", rtc::CurrentThreadId());
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (!initialized_ || !recording_) {
    return 0;
  }
  // Stop recording by setting the record state to SL_RECORDSTATE_STOPPED.
  if (LOG_ON_ERROR(
          (*recorder_)->SetRecordState(recorder_, SL_RECORDSTATE_STOPPED))) {
    return -1;
  }
  // Clear the buffer queue to get rid of old data when resuming recording.
  if (LOG_ON_ERROR((*simple_buffer_queue_)->Clear(simple_buffer_queue_))) {
    return -1;
  }
  thread_checker_opensles_.Detach();
  initialized_ = false;
  recording_ = false;
  return 0;
}

void OpenSLESRecorder::AttachAudioBuffer(AudioDeviceBuffer* audio_buffer) {
  ALOGD("AttachAudioBuffer");
  RTC_DCHECK(thread_checker_.IsCurrent());
  RTC_CHECK(audio_buffer);
  audio_device_buffer_ = audio_buffer;
  // Ensure that the audio device buffer is informed about the native sample
  // rate used on the recording side.
  const int sample_rate_hz = audio_parameters_.sample_rate();
  ALOGD("SetRecordingSampleRate(%d)", sample_rate_hz);
  audio_device_buffer_->SetRecordingSampleRate(sample_rate_hz);
  // Ensure that the audio device buffer is informed about the number of
  // channels preferred by the OS on the recording side.
  const size_t channels = audio_parameters_.channels();
  ALOGD("SetRecordingChannels(%" RTC_PRIuS ")", channels);
  audio_device_buffer_->SetRecordingChannels(channels);
  // Allocated memory for internal data buffers given existing audio parameters.
  AllocateDataBuffers();
}

int OpenSLESRecorder::EnableBuiltInAEC(bool enable) {
  ALOGD("EnableBuiltInAEC(%d)", enable);
  RTC_DCHECK(thread_checker_.IsCurrent());
  ALOGE("Not implemented");
  return 0;
}

int OpenSLESRecorder::EnableBuiltInAGC(bool enable) {
  ALOGD("EnableBuiltInAGC(%d)", enable);
  RTC_DCHECK(thread_checker_.IsCurrent());
  ALOGE("Not implemented");
  return 0;
}

int OpenSLESRecorder::EnableBuiltInNS(bool enable) {
  ALOGD("EnableBuiltInNS(%d)", enable);
  RTC_DCHECK(thread_checker_.IsCurrent());
  ALOGE("Not implemented");
  return 0;
}

bool OpenSLESRecorder::ObtainEngineInterface() {
  ALOGD("ObtainEngineInterface");
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (engine_)
    return true;
  // Get access to (or create if not already existing) the global OpenSL Engine
  // object.
  SLObjectItf engine_object = audio_manager_->GetOpenSLEngine();
  if (engine_object == nullptr) {
    ALOGE("Failed to access the global OpenSL engine");
    return false;
  }
  // Get the SL Engine Interface which is implicit.
  if (LOG_ON_ERROR(
          (*engine_object)
              ->GetInterface(engine_object, SL_IID_ENGINE, &engine_))) {
    return false;
  }
  return true;
}

bool OpenSLESRecorder::CreateAudioRecorder() {
  ALOGD("CreateAudioRecorder");
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (recorder_object_.Get())
    return true;
  RTC_DCHECK(!recorder_);
  RTC_DCHECK(!simple_buffer_queue_);

  // Audio source configuration.
  SLDataLocator_IODevice mic_locator = {SL_DATALOCATOR_IODEVICE,
                                        SL_IODEVICE_AUDIOINPUT,
                                        SL_DEFAULTDEVICEID_AUDIOINPUT, NULL};
  SLDataSource audio_source = {&mic_locator, NULL};

  // Audio sink configuration.
  SLDataLocator_AndroidSimpleBufferQueue buffer_queue = {
      SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE,
      static_cast<SLuint32>(kNumOfOpenSLESBuffers)};
  SLDataSink audio_sink = {&buffer_queue, &pcm_format_};

  // Create the audio recorder object (requires the RECORD_AUDIO permission).
  // Do not realize the recorder yet. Set the configuration first.
  const SLInterfaceID interface_id[] = {SL_IID_ANDROIDSIMPLEBUFFERQUEUE,
                                        SL_IID_ANDROIDCONFIGURATION};
  const SLboolean interface_required[] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE};
  if (LOG_ON_ERROR((*engine_)->CreateAudioRecorder(
          engine_, recorder_object_.Receive(), &audio_source, &audio_sink,
          arraysize(interface_id), interface_id, interface_required))) {
    return false;
  }

  // Configure the audio recorder (before it is realized).
  SLAndroidConfigurationItf recorder_config;
  if (LOG_ON_ERROR((recorder_object_->GetInterface(recorder_object_.Get(),
                                                   SL_IID_ANDROIDCONFIGURATION,
                                                   &recorder_config)))) {
    return false;
  }

  // Uses the default microphone tuned for audio communication.
  // Note that, SL_ANDROID_RECORDING_PRESET_VOICE_RECOGNITION leads to a fast
  // track but also excludes usage of required effects like AEC, AGC and NS.
  // SL_ANDROID_RECORDING_PRESET_VOICE_COMMUNICATION
  SLint32 stream_type = SL_ANDROID_RECORDING_PRESET_VOICE_COMMUNICATION;
  if (LOG_ON_ERROR(((*recorder_config)
                        ->SetConfiguration(recorder_config,
                                           SL_ANDROID_KEY_RECORDING_PRESET,
                                           &stream_type, sizeof(SLint32))))) {
    return false;
  }

  // The audio recorder can now be realized (in synchronous mode).
  if (LOG_ON_ERROR((recorder_object_->Realize(recorder_object_.Get(),
                                              SL_BOOLEAN_FALSE)))) {
    return false;
  }

  // Get the implicit recorder interface (SL_IID_RECORD).
  if (LOG_ON_ERROR((recorder_object_->GetInterface(
          recorder_object_.Get(), SL_IID_RECORD, &recorder_)))) {
    return false;
  }

  // Get the simple buffer queue interface (SL_IID_ANDROIDSIMPLEBUFFERQUEUE).
  // It was explicitly requested.
  if (LOG_ON_ERROR((recorder_object_->GetInterface(
          recorder_object_.Get(), SL_IID_ANDROIDSIMPLEBUFFERQUEUE,
          &simple_buffer_queue_)))) {
    return false;
  }

  // Register the input callback for the simple buffer queue.
  // This callback will be called when receiving new data from the device.
  if (LOG_ON_ERROR(((*simple_buffer_queue_)
                        ->RegisterCallback(simple_buffer_queue_,
                                           SimpleBufferQueueCallback, this)))) {
    return false;
  }
  return true;
}

void OpenSLESRecorder::DestroyAudioRecorder() {
  ALOGD("DestroyAudioRecorder");
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (!recorder_object_.Get())
    return;
  (*simple_buffer_queue_)
      ->RegisterCallback(simple_buffer_queue_, nullptr, nullptr);
  recorder_object_.Reset();
  recorder_ = nullptr;
  simple_buffer_queue_ = nullptr;
}

void OpenSLESRecorder::SimpleBufferQueueCallback(
    SLAndroidSimpleBufferQueueItf buffer_queue,
    void* context) {
  OpenSLESRecorder* stream = static_cast<OpenSLESRecorder*>(context);
  stream->ReadBufferQueue();
}

void OpenSLESRecorder::AllocateDataBuffers() {
  ALOGD("AllocateDataBuffers");
  RTC_DCHECK(thread_checker_.IsCurrent());
  RTC_DCHECK(!simple_buffer_queue_);
  RTC_CHECK(audio_device_buffer_);
  // Create a modified audio buffer class which allows us to deliver any number
  // of samples (and not only multiple of 10ms) to match the native audio unit
  // buffer size.
  ALOGD("frames per native buffer: %" RTC_PRIuS,
        audio_parameters_.frames_per_buffer());
  ALOGD("frames per 10ms buffer: %" RTC_PRIuS,
        audio_parameters_.frames_per_10ms_buffer());
  ALOGD("bytes per native buffer: %" RTC_PRIuS,
        audio_parameters_.GetBytesPerBuffer());
  ALOGD("native sample rate: %d", audio_parameters_.sample_rate());
  RTC_DCHECK(audio_device_buffer_);
  fine_audio_buffer_ = std::make_unique<FineAudioBuffer>(audio_device_buffer_);
  // Allocate queue of audio buffers that stores recorded audio samples.
  const int buffer_size_samples =
      audio_parameters_.frames_per_buffer() * audio_parameters_.channels();
  audio_buffers_.reset(new std::unique_ptr<SLint16[]>[kNumOfOpenSLESBuffers]);
  for (int i = 0; i < kNumOfOpenSLESBuffers; ++i) {
    audio_buffers_[i].reset(new SLint16[buffer_size_samples]);
  }
}

void OpenSLESRecorder::ReadBufferQueue() {
  RTC_DCHECK(thread_checker_opensles_.IsCurrent());
  SLuint32 state = GetRecordState();
  if (state != SL_RECORDSTATE_RECORDING) {
    ALOGW("Buffer callback in non-recording state!");
    return;
  }
  // Check delta time between two successive callbacks and provide a warning
  // if it becomes very large.
  // TODO(henrika): using 150ms as upper limit but this value is rather random.
  const uint32_t current_time = rtc::Time();
  const uint32_t diff = current_time - last_rec_time_;
  if (diff > 150) {
    ALOGW("Bad OpenSL ES record timing, dT=%u [ms]", diff);
  }
  last_rec_time_ = current_time;
  // Send recorded audio data to the WebRTC sink.
  // TODO(henrika): fix delay estimates. It is OK to use fixed values for now
  // since there is no support to turn off built-in EC in combination with
  // OpenSL ES anyhow. Hence, as is, the WebRTC based AEC (which would use
  // these estimates) will never be active.
  fine_audio_buffer_->DeliverRecordedData(
      rtc::ArrayView<const int16_t>(
          audio_buffers_[buffer_index_].get(),
          audio_parameters_.frames_per_buffer() * audio_parameters_.channels()),
      25);
  // Enqueue the utilized audio buffer and use if for recording again.
  EnqueueAudioBuffer();
}

bool OpenSLESRecorder::EnqueueAudioBuffer() {
  SLresult err =
      (*simple_buffer_queue_)
          ->Enqueue(
              simple_buffer_queue_,
              reinterpret_cast<SLint8*>(audio_buffers_[buffer_index_].get()),
              audio_parameters_.GetBytesPerBuffer());
  if (SL_RESULT_SUCCESS != err) {
    ALOGE("Enqueue failed: %s", GetSLErrorString(err));
    return false;
  }
  buffer_index_ = (buffer_index_ + 1) % kNumOfOpenSLESBuffers;
  return true;
}

SLuint32 OpenSLESRecorder::GetRecordState() const {
  RTC_DCHECK(recorder_);
  SLuint32 state;
  SLresult err = (*recorder_)->GetRecordState(recorder_, &state);
  if (SL_RESULT_SUCCESS != err) {
    ALOGE("GetRecordState failed: %s", GetSLErrorString(err));
  }
  return state;
}

SLAndroidSimpleBufferQueueState OpenSLESRecorder::GetBufferQueueState() const {
  RTC_DCHECK(simple_buffer_queue_);
  // state.count: Number of buffers currently in the queue.
  // state.index: Index of the currently filling buffer. This is a linear index
  // that keeps a cumulative count of the number of buffers recorded.
  SLAndroidSimpleBufferQueueState state;
  SLresult err =
      (*simple_buffer_queue_)->GetState(simple_buffer_queue_, &state);
  if (SL_RESULT_SUCCESS != err) {
    ALOGE("GetState failed: %s", GetSLErrorString(err));
  }
  return state;
}

void OpenSLESRecorder::LogBufferState() const {
  SLAndroidSimpleBufferQueueState state = GetBufferQueueState();
  ALOGD("state.count:%d state.index:%d", state.count, state.index);
}

SLuint32 OpenSLESRecorder::GetBufferCount() {
  SLAndroidSimpleBufferQueueState state = GetBufferQueueState();
  return state.count;
}

}  // namespace webrtc
