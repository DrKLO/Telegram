/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/src/jni/audio_device/aaudio_player.h"

#include <memory>

#include "api/array_view.h"
#include "api/task_queue/task_queue_base.h"
#include "modules/audio_device/fine_audio_buffer.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {

namespace jni {

AAudioPlayer::AAudioPlayer(const AudioParameters& audio_parameters)
    : main_thread_(TaskQueueBase::Current()),
      aaudio_(audio_parameters, AAUDIO_DIRECTION_OUTPUT, this) {
  RTC_LOG(LS_INFO) << "ctor";
  thread_checker_aaudio_.Detach();
}

AAudioPlayer::~AAudioPlayer() {
  RTC_LOG(LS_INFO) << "dtor";
  RTC_DCHECK_RUN_ON(&main_thread_checker_);
  Terminate();
  RTC_LOG(LS_INFO) << "#detected underruns: " << underrun_count_;
}

int AAudioPlayer::Init() {
  RTC_LOG(LS_INFO) << "Init";
  RTC_DCHECK_RUN_ON(&main_thread_checker_);
  if (aaudio_.audio_parameters().channels() == 2) {
    RTC_DLOG(LS_WARNING) << "Stereo mode is enabled";
  }
  return 0;
}

int AAudioPlayer::Terminate() {
  RTC_LOG(LS_INFO) << "Terminate";
  RTC_DCHECK_RUN_ON(&main_thread_checker_);
  StopPlayout();
  return 0;
}

int AAudioPlayer::InitPlayout() {
  RTC_LOG(LS_INFO) << "InitPlayout";
  RTC_DCHECK_RUN_ON(&main_thread_checker_);
  RTC_DCHECK(!initialized_);
  RTC_DCHECK(!playing_);
  if (!aaudio_.Init()) {
    return -1;
  }
  initialized_ = true;
  return 0;
}

bool AAudioPlayer::PlayoutIsInitialized() const {
  RTC_DCHECK_RUN_ON(&main_thread_checker_);
  return initialized_;
}

int AAudioPlayer::StartPlayout() {
  RTC_LOG(LS_INFO) << "StartPlayout";
  RTC_DCHECK_RUN_ON(&main_thread_checker_);
  RTC_DCHECK(!playing_);
  if (!initialized_) {
    RTC_DLOG(LS_WARNING)
        << "Playout can not start since InitPlayout must succeed first";
    return 0;
  }
  if (fine_audio_buffer_) {
    fine_audio_buffer_->ResetPlayout();
  }
  if (!aaudio_.Start()) {
    return -1;
  }
  underrun_count_ = aaudio_.xrun_count();
  first_data_callback_ = true;
  playing_ = true;
  return 0;
}

int AAudioPlayer::StopPlayout() {
  RTC_LOG(LS_INFO) << "StopPlayout";
  RTC_DCHECK_RUN_ON(&main_thread_checker_);
  if (!initialized_ || !playing_) {
    return 0;
  }
  if (!aaudio_.Stop()) {
    RTC_LOG(LS_ERROR) << "StopPlayout failed";
    return -1;
  }
  thread_checker_aaudio_.Detach();
  initialized_ = false;
  playing_ = false;
  return 0;
}

bool AAudioPlayer::Playing() const {
  RTC_DCHECK_RUN_ON(&main_thread_checker_);
  return playing_;
}

void AAudioPlayer::AttachAudioBuffer(AudioDeviceBuffer* audioBuffer) {
  RTC_DLOG(LS_INFO) << "AttachAudioBuffer";
  RTC_DCHECK_RUN_ON(&main_thread_checker_);
  audio_device_buffer_ = audioBuffer;
  const AudioParameters audio_parameters = aaudio_.audio_parameters();
  audio_device_buffer_->SetPlayoutSampleRate(audio_parameters.sample_rate());
  audio_device_buffer_->SetPlayoutChannels(audio_parameters.channels());
  RTC_CHECK(audio_device_buffer_);
  // Create a modified audio buffer class which allows us to ask for any number
  // of samples (and not only multiple of 10ms) to match the optimal buffer
  // size per callback used by AAudio.
  fine_audio_buffer_ = std::make_unique<FineAudioBuffer>(audio_device_buffer_);
}

bool AAudioPlayer::SpeakerVolumeIsAvailable() {
  return false;
}

int AAudioPlayer::SetSpeakerVolume(uint32_t volume) {
  return -1;
}

absl::optional<uint32_t> AAudioPlayer::SpeakerVolume() const {
  return absl::nullopt;
}

absl::optional<uint32_t> AAudioPlayer::MaxSpeakerVolume() const {
  return absl::nullopt;
}

absl::optional<uint32_t> AAudioPlayer::MinSpeakerVolume() const {
  return absl::nullopt;
}

void AAudioPlayer::OnErrorCallback(aaudio_result_t error) {
  RTC_LOG(LS_ERROR) << "OnErrorCallback: " << AAudio_convertResultToText(error);
  // TODO(henrika): investigate if we can use a thread checker here. Initial
  // tests shows that this callback can sometimes be called on a unique thread
  // but according to the documentation it should be on the same thread as the
  // data callback.
  // RTC_DCHECK_RUN_ON(&thread_checker_aaudio_);
  if (aaudio_.stream_state() == AAUDIO_STREAM_STATE_DISCONNECTED) {
    // The stream is disconnected and any attempt to use it will return
    // AAUDIO_ERROR_DISCONNECTED.
    RTC_LOG(LS_WARNING) << "Output stream disconnected";
    // AAudio documentation states: "You should not close or reopen the stream
    // from the callback, use another thread instead". A message is therefore
    // sent to the main thread to do the restart operation.
    RTC_DCHECK(main_thread_);
    main_thread_->PostTask([this] { HandleStreamDisconnected(); });
  }
}

aaudio_data_callback_result_t AAudioPlayer::OnDataCallback(void* audio_data,
                                                           int32_t num_frames) {
  RTC_DCHECK_RUN_ON(&thread_checker_aaudio_);
  // Log device id in first data callback to ensure that a valid device is
  // utilized.
  if (first_data_callback_) {
    RTC_LOG(LS_INFO) << "--- First output data callback: "
                        "device id="
                     << aaudio_.device_id();
    first_data_callback_ = false;
  }

  // Check if the underrun count has increased. If it has, increase the buffer
  // size by adding the size of a burst. It will reduce the risk of underruns
  // at the expense of an increased latency.
  // TODO(henrika): enable possibility to disable and/or tune the algorithm.
  const int32_t underrun_count = aaudio_.xrun_count();
  if (underrun_count > underrun_count_) {
    RTC_LOG(LS_ERROR) << "Underrun detected: " << underrun_count;
    underrun_count_ = underrun_count;
    aaudio_.IncreaseOutputBufferSize();
  }

  // Estimate latency between writing an audio frame to the output stream and
  // the time that same frame is played out on the output audio device.
  latency_millis_ = aaudio_.EstimateLatencyMillis();
  // TODO(henrika): use for development only.
  if (aaudio_.frames_written() % (1000 * aaudio_.frames_per_burst()) == 0) {
    RTC_DLOG(LS_INFO) << "output latency: " << latency_millis_
                      << ", num_frames: " << num_frames;
  }

  // Read audio data from the WebRTC source using the FineAudioBuffer object
  // and write that data into `audio_data` to be played out by AAudio.
  // Prime output with zeros during a short initial phase to avoid distortion.
  // TODO(henrika): do more work to figure out of if the initial forced silence
  // period is really needed.
  if (aaudio_.frames_written() < 50 * aaudio_.frames_per_burst()) {
    const size_t num_bytes =
        sizeof(int16_t) * aaudio_.samples_per_frame() * num_frames;
    memset(audio_data, 0, num_bytes);
  } else {
    fine_audio_buffer_->GetPlayoutData(
        rtc::MakeArrayView(static_cast<int16_t*>(audio_data),
                           aaudio_.samples_per_frame() * num_frames),
        static_cast<int>(latency_millis_ + 0.5));
  }

  // TODO(henrika): possibly add trace here to be included in systrace.
  // See https://developer.android.com/studio/profile/systrace-commandline.html.
  return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

void AAudioPlayer::HandleStreamDisconnected() {
  RTC_DCHECK_RUN_ON(&main_thread_checker_);
  RTC_DLOG(LS_INFO) << "HandleStreamDisconnected";
  if (!initialized_ || !playing_) {
    return;
  }
  // Perform a restart by first closing the disconnected stream and then start
  // a new stream; this time using the new (preferred) audio output device.
  StopPlayout();
  InitPlayout();
  StartPlayout();
}

}  // namespace jni

}  // namespace webrtc
