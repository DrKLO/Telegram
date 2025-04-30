/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "audio/audio_state.h"

#include <algorithm>
#include <memory>
#include <utility>
#include <vector>

#include "api/sequence_checker.h"
#include "api/task_queue/task_queue_base.h"
#include "api/units/time_delta.h"
#include "audio/audio_receive_stream.h"
#include "audio/audio_send_stream.h"
#include "modules/audio_device/include/audio_device.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace internal {

AudioState::AudioState(const AudioState::Config& config)
    : config_(config),
      audio_transport_(config_.audio_mixer.get(),
                       config_.audio_processing.get(),
                       config_.async_audio_processing_factory.get()) {
  RTC_DCHECK(config_.audio_mixer);
  RTC_DCHECK(config_.audio_device_module);
}

AudioState::~AudioState() {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  RTC_DCHECK(receiving_streams_.empty());
  RTC_DCHECK(sending_streams_.empty());
  RTC_DCHECK(!null_audio_poller_.Running());
}

AudioProcessing* AudioState::audio_processing() {
  return config_.audio_processing.get();
}

AudioTransport* AudioState::audio_transport() {
  return &audio_transport_;
}

void AudioState::AddReceivingStream(
    webrtc::AudioReceiveStreamInterface* stream) {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  RTC_DCHECK_EQ(0, receiving_streams_.count(stream));
  receiving_streams_.insert(stream);
  if (!config_.audio_mixer->AddSource(
          static_cast<AudioReceiveStreamImpl*>(stream))) {
    RTC_DLOG(LS_ERROR) << "Failed to add source to mixer.";
  }

  // Make sure playback is initialized; start playing if enabled.
  UpdateNullAudioPollerState();
  auto* adm = config_.audio_device_module.get();
  if (!adm->Playing()) {
    if (adm->InitPlayout() == 0) {
      if (playout_enabled_) {
        adm->StartPlayout();
      }
    } else {
      RTC_DLOG_F(LS_ERROR) << "Failed to initialize playout.";
    }
  }
}

void AudioState::RemoveReceivingStream(
    webrtc::AudioReceiveStreamInterface* stream) {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  auto count = receiving_streams_.erase(stream);
  RTC_DCHECK_EQ(1, count);
  config_.audio_mixer->RemoveSource(
      static_cast<AudioReceiveStreamImpl*>(stream));
  UpdateNullAudioPollerState();
  if (receiving_streams_.empty()) {
    config_.audio_device_module->StopPlayout();
  }
}

void AudioState::AddSendingStream(webrtc::AudioSendStream* stream,
                                  int sample_rate_hz,
                                  size_t num_channels) {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  auto& properties = sending_streams_[stream];
  properties.sample_rate_hz = sample_rate_hz;
  properties.num_channels = num_channels;
  UpdateAudioTransportWithSendingStreams();

  // Make sure recording is initialized; start recording if enabled.
  auto* adm = config_.audio_device_module.get();
  if (!adm->Recording()) {
    if (adm->InitRecording() == 0) {
      if (recording_enabled_) {
        adm->StartRecording();
      }
    } else {
      RTC_DLOG_F(LS_ERROR) << "Failed to initialize recording.";
    }
  }
}

void AudioState::RemoveSendingStream(webrtc::AudioSendStream* stream) {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  auto count = sending_streams_.erase(stream);
  RTC_DCHECK_EQ(1, count);
  UpdateAudioTransportWithSendingStreams();
  if (sending_streams_.empty()) {
    config_.audio_device_module->StopRecording();
  }
}

void AudioState::SetPlayout(bool enabled) {
  RTC_LOG(LS_INFO) << "SetPlayout(" << enabled << ")";
  RTC_DCHECK_RUN_ON(&thread_checker_);
  if (playout_enabled_ != enabled) {
    playout_enabled_ = enabled;
    if (enabled) {
      UpdateNullAudioPollerState();
      if (!receiving_streams_.empty()) {
        config_.audio_device_module->StartPlayout();
      }
    } else {
      config_.audio_device_module->StopPlayout();
      UpdateNullAudioPollerState();
    }
  }
}

void AudioState::SetRecording(bool enabled) {
  RTC_LOG(LS_INFO) << "SetRecording(" << enabled << ")";
  RTC_DCHECK_RUN_ON(&thread_checker_);
  if (recording_enabled_ != enabled) {
    recording_enabled_ = enabled;
    if (enabled) {
      if (!sending_streams_.empty()) {
        config_.audio_device_module->StartRecording();
      }
    } else {
      config_.audio_device_module->StopRecording();
    }
  }
}

void AudioState::SetStereoChannelSwapping(bool enable) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  audio_transport_.SetStereoChannelSwapping(enable);
}

void AudioState::UpdateAudioTransportWithSendingStreams() {
  RTC_DCHECK(thread_checker_.IsCurrent());
  std::vector<AudioSender*> audio_senders;
  int max_sample_rate_hz = 8000;
  size_t max_num_channels = 1;
  for (const auto& kv : sending_streams_) {
    audio_senders.push_back(kv.first);
    max_sample_rate_hz = std::max(max_sample_rate_hz, kv.second.sample_rate_hz);
    max_num_channels = std::max(max_num_channels, kv.second.num_channels);
  }
  audio_transport_.UpdateAudioSenders(std::move(audio_senders),
                                      max_sample_rate_hz, max_num_channels);
}

void AudioState::UpdateNullAudioPollerState() {
  // Run NullAudioPoller when there are receiving streams and playout is
  // disabled.
  if (!receiving_streams_.empty() && !playout_enabled_) {
    if (!null_audio_poller_.Running()) {
      AudioTransport* audio_transport = &audio_transport_;
      null_audio_poller_ = RepeatingTaskHandle::Start(
          TaskQueueBase::Current(), [audio_transport] {
            static constexpr size_t kNumChannels = 1;
            static constexpr uint32_t kSamplesPerSecond = 48'000;
            // 10ms of samples
            static constexpr size_t kNumSamples = kSamplesPerSecond / 100;

            // Buffer to hold the audio samples.
            int16_t buffer[kNumSamples * kNumChannels];

            // Output variables from `NeedMorePlayData`.
            size_t n_samples;
            int64_t elapsed_time_ms;
            int64_t ntp_time_ms;
            audio_transport->NeedMorePlayData(
                kNumSamples, sizeof(int16_t), kNumChannels, kSamplesPerSecond,
                buffer, n_samples, &elapsed_time_ms, &ntp_time_ms);

            // Reschedule the next poll iteration.
            return TimeDelta::Millis(10);
          });
    }
  } else {
    null_audio_poller_.Stop();
  }
}
}  // namespace internal

rtc::scoped_refptr<AudioState> AudioState::Create(
    const AudioState::Config& config) {
  return rtc::make_ref_counted<internal::AudioState>(config);
}
}  // namespace webrtc
