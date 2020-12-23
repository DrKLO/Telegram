
/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/async_audio_processing/async_audio_processing.h"

#include <utility>

#include "api/audio/audio_frame.h"
#include "api/task_queue/task_queue_factory.h"
#include "rtc_base/checks.h"

namespace webrtc {

AsyncAudioProcessing::Factory::~Factory() = default;
AsyncAudioProcessing::Factory::Factory(AudioFrameProcessor& frame_processor,
                                       TaskQueueFactory& task_queue_factory)
    : frame_processor_(frame_processor),
      task_queue_factory_(task_queue_factory) {}

std::unique_ptr<AsyncAudioProcessing>
AsyncAudioProcessing::Factory::CreateAsyncAudioProcessing(
    AudioFrameProcessor::OnAudioFrameCallback on_frame_processed_callback) {
  return std::make_unique<AsyncAudioProcessing>(
      frame_processor_, task_queue_factory_,
      std::move(on_frame_processed_callback));
}

AsyncAudioProcessing::~AsyncAudioProcessing() {
  frame_processor_.SetSink(nullptr);
}

AsyncAudioProcessing::AsyncAudioProcessing(
    AudioFrameProcessor& frame_processor,
    TaskQueueFactory& task_queue_factory,
    AudioFrameProcessor::OnAudioFrameCallback on_frame_processed_callback)
    : on_frame_processed_callback_(std::move(on_frame_processed_callback)),
      frame_processor_(frame_processor),
      task_queue_(task_queue_factory.CreateTaskQueue(
          "AsyncAudioProcessing",
          TaskQueueFactory::Priority::NORMAL)) {
  frame_processor_.SetSink([this](std::unique_ptr<AudioFrame> frame) {
    task_queue_.PostTask([this, frame = std::move(frame)]() mutable {
      on_frame_processed_callback_(std::move(frame));
    });
  });
}

void AsyncAudioProcessing::Process(std::unique_ptr<AudioFrame> frame) {
  task_queue_.PostTask([this, frame = std::move(frame)]() mutable {
    frame_processor_.Process(std::move(frame));
  });
}

}  // namespace webrtc
