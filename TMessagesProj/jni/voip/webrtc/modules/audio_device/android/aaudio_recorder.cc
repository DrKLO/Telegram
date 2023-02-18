/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_device/android/aaudio_recorder.h"

#include <memory>

#include "api/array_view.h"
#include "api/task_queue/task_queue_base.h"
#include "modules/audio_device/android/audio_manager.h"
#include "modules/audio_device/fine_audio_buffer.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/time_utils.h"

namespace webrtc {

AAudioRecorder::AAudioRecorder(AudioManager* audio_manager)
    : main_thread_(TaskQueueBase::Current()),
      aaudio_(audio_manager, AAUDIO_DIRECTION_INPUT, this) {
  RTC_LOG(LS_INFO) << "ctor";
  thread_checker_aaudio_.Detach();
}

AAudioRecorder::~AAudioRecorder() {
  RTC_LOG(LS_INFO) << "dtor";
  RTC_DCHECK(thread_checker_.IsCurrent());
  Terminate();
  RTC_LOG(LS_INFO) << "detected owerflows: " << overflow_count_;
}

int AAudioRecorder::Init() {
  RTC_LOG(LS_INFO) << "Init";
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (aaudio_.audio_parameters().channels() == 2) {
    RTC_DLOG(LS_WARNING) << "Stereo mode is enabled";
  }
  return 0;
}

int AAudioRecorder::Terminate() {
  RTC_LOG(LS_INFO) << "Terminate";
  RTC_DCHECK(thread_checker_.IsCurrent());
  StopRecording();
  return 0;
}

int AAudioRecorder::InitRecording() {
  RTC_LOG(LS_INFO) << "InitRecording";
  RTC_DCHECK(thread_checker_.IsCurrent());
  RTC_DCHECK(!initialized_);
  RTC_DCHECK(!recording_);
  if (!aaudio_.Init()) {
    return -1;
  }
  initialized_ = true;
  return 0;
}

int AAudioRecorder::StartRecording() {
  RTC_LOG(LS_INFO) << "StartRecording";
  RTC_DCHECK(thread_checker_.IsCurrent());
  RTC_DCHECK(initialized_);
  RTC_DCHECK(!recording_);
  if (fine_audio_buffer_) {
    fine_audio_buffer_->ResetPlayout();
  }
  if (!aaudio_.Start()) {
    return -1;
  }
  overflow_count_ = aaudio_.xrun_count();
  first_data_callback_ = true;
  recording_ = true;
  return 0;
}

int AAudioRecorder::StopRecording() {
  RTC_LOG(LS_INFO) << "StopRecording";
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (!initialized_ || !recording_) {
    return 0;
  }
  if (!aaudio_.Stop()) {
    return -1;
  }
  thread_checker_aaudio_.Detach();
  initialized_ = false;
  recording_ = false;
  return 0;
}

void AAudioRecorder::AttachAudioBuffer(AudioDeviceBuffer* audioBuffer) {
  RTC_LOG(LS_INFO) << "AttachAudioBuffer";
  RTC_DCHECK(thread_checker_.IsCurrent());
  audio_device_buffer_ = audioBuffer;
  const AudioParameters audio_parameters = aaudio_.audio_parameters();
  audio_device_buffer_->SetRecordingSampleRate(audio_parameters.sample_rate());
  audio_device_buffer_->SetRecordingChannels(audio_parameters.channels());
  RTC_CHECK(audio_device_buffer_);
  // Create a modified audio buffer class which allows us to deliver any number
  // of samples (and not only multiples of 10ms which WebRTC uses) to match the
  // native AAudio buffer size.
  fine_audio_buffer_ = std::make_unique<FineAudioBuffer>(audio_device_buffer_);
}

int AAudioRecorder::EnableBuiltInAEC(bool enable) {
  RTC_LOG(LS_INFO) << "EnableBuiltInAEC: " << enable;
  RTC_LOG(LS_ERROR) << "Not implemented";
  return -1;
}

int AAudioRecorder::EnableBuiltInAGC(bool enable) {
  RTC_LOG(LS_INFO) << "EnableBuiltInAGC: " << enable;
  RTC_LOG(LS_ERROR) << "Not implemented";
  return -1;
}

int AAudioRecorder::EnableBuiltInNS(bool enable) {
  RTC_LOG(LS_INFO) << "EnableBuiltInNS: " << enable;
  RTC_LOG(LS_ERROR) << "Not implemented";
  return -1;
}

void AAudioRecorder::OnErrorCallback(aaudio_result_t error) {
  RTC_LOG(LS_ERROR) << "OnErrorCallback: " << AAudio_convertResultToText(error);
  // RTC_DCHECK(thread_checker_aaudio_.IsCurrent());
  if (aaudio_.stream_state() == AAUDIO_STREAM_STATE_DISCONNECTED) {
    // The stream is disconnected and any attempt to use it will return
    // AAUDIO_ERROR_DISCONNECTED..
    RTC_LOG(LS_WARNING) << "Input stream disconnected => restart is required";
    // AAudio documentation states: "You should not close or reopen the stream
    // from the callback, use another thread instead". A message is therefore
    // sent to the main thread to do the restart operation.
    RTC_DCHECK(main_thread_);
    main_thread_->PostTask([this] { HandleStreamDisconnected(); });
  }
}

// Read and process `num_frames` of data from the `audio_data` buffer.
// TODO(henrika): possibly add trace here to be included in systrace.
// See https://developer.android.com/studio/profile/systrace-commandline.html.
aaudio_data_callback_result_t AAudioRecorder::OnDataCallback(
    void* audio_data,
    int32_t num_frames) {
  // TODO(henrika): figure out why we sometimes hit this one.
  // RTC_DCHECK(thread_checker_aaudio_.IsCurrent());
  // RTC_LOG(LS_INFO) << "OnDataCallback: " << num_frames;
  // Drain the input buffer at first callback to ensure that it does not
  // contain any old data. Will also ensure that the lowest possible latency
  // is obtained.
  if (first_data_callback_) {
    RTC_LOG(LS_INFO) << "--- First input data callback: "
                        "device id="
                     << aaudio_.device_id();
    aaudio_.ClearInputStream(audio_data, num_frames);
    first_data_callback_ = false;
  }
  // Check if the overflow counter has increased and if so log a warning.
  // TODO(henrika): possible add UMA stat or capacity extension.
  const int32_t overflow_count = aaudio_.xrun_count();
  if (overflow_count > overflow_count_) {
    RTC_LOG(LS_ERROR) << "Overflow detected: " << overflow_count;
    overflow_count_ = overflow_count;
  }
  // Estimated time between an audio frame was recorded by the input device and
  // it can read on the input stream.
  latency_millis_ = aaudio_.EstimateLatencyMillis();
  // TODO(henrika): use for development only.
  if (aaudio_.frames_read() % (1000 * aaudio_.frames_per_burst()) == 0) {
    RTC_DLOG(LS_INFO) << "input latency: " << latency_millis_
                      << ", num_frames: " << num_frames;
  }
  // Copy recorded audio in `audio_data` to the WebRTC sink using the
  // FineAudioBuffer object.
  fine_audio_buffer_->DeliverRecordedData(
      rtc::MakeArrayView(static_cast<const int16_t*>(audio_data),
                         aaudio_.samples_per_frame() * num_frames),
      static_cast<int>(latency_millis_ + 0.5));

  return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

void AAudioRecorder::HandleStreamDisconnected() {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  RTC_LOG(LS_INFO) << "HandleStreamDisconnected";
  if (!initialized_ || !recording_) {
    return;
  }
  // Perform a restart by first closing the disconnected stream and then start
  // a new stream; this time using the new (preferred) audio input device.
  // TODO(henrika): resolve issue where a one restart attempt leads to a long
  // sequence of new calls to OnErrorCallback().
  // See b/73148976 for details.
  StopRecording();
  InitRecording();
  StartRecording();
}
}  // namespace webrtc
