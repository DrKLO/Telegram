/*
 *  Copyright 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/remote_audio_source.h"

#include <stddef.h>

#include <memory>

#include "absl/algorithm/container.h"
#include "api/scoped_refptr.h"
#include "api/sequence_checker.h"
#include "rtc_base/checks.h"
#include "rtc_base/location.h"
#include "rtc_base/logging.h"
#include "rtc_base/strings/string_format.h"
#include "rtc_base/thread.h"

namespace webrtc {

// This proxy is passed to the underlying media engine to receive audio data as
// they come in. The data will then be passed back up to the RemoteAudioSource
// which will fan it out to all the sinks that have been added to it.
class RemoteAudioSource::AudioDataProxy : public AudioSinkInterface {
 public:
  explicit AudioDataProxy(RemoteAudioSource* source) : source_(source) {
    RTC_DCHECK(source);
  }

  AudioDataProxy() = delete;
  AudioDataProxy(const AudioDataProxy&) = delete;
  AudioDataProxy& operator=(const AudioDataProxy&) = delete;

  ~AudioDataProxy() override { source_->OnAudioChannelGone(); }

  // AudioSinkInterface implementation.
  void OnData(const AudioSinkInterface::Data& audio) override {
    source_->OnData(audio);
  }

 private:
  const rtc::scoped_refptr<RemoteAudioSource> source_;
};

RemoteAudioSource::RemoteAudioSource(
    rtc::Thread* worker_thread,
    OnAudioChannelGoneAction on_audio_channel_gone_action)
    : main_thread_(rtc::Thread::Current()),
      worker_thread_(worker_thread),
      on_audio_channel_gone_action_(on_audio_channel_gone_action),
      state_(MediaSourceInterface::kLive) {
  RTC_DCHECK(main_thread_);
  RTC_DCHECK(worker_thread_);
}

RemoteAudioSource::~RemoteAudioSource() {
  RTC_DCHECK_RUN_ON(main_thread_);
  RTC_DCHECK(audio_observers_.empty());
  if (!sinks_.empty()) {
    RTC_LOG(LS_WARNING)
        << "RemoteAudioSource destroyed while sinks_ is non-empty.";
  }
}

void RemoteAudioSource::Start(cricket::VoiceMediaChannel* media_channel,
                              absl::optional<uint32_t> ssrc) {
  RTC_DCHECK_RUN_ON(worker_thread_);

  // Register for callbacks immediately before AddSink so that we always get
  // notified when a channel goes out of scope (signaled when "AudioDataProxy"
  // is destroyed).
  RTC_DCHECK(media_channel);
  ssrc ? media_channel->SetRawAudioSink(*ssrc,
                                        std::make_unique<AudioDataProxy>(this))
       : media_channel->SetDefaultRawAudioSink(
             std::make_unique<AudioDataProxy>(this));
}

void RemoteAudioSource::Stop(cricket::VoiceMediaChannel* media_channel,
                             absl::optional<uint32_t> ssrc) {
  RTC_DCHECK_RUN_ON(worker_thread_);
  RTC_DCHECK(media_channel);
  ssrc ? media_channel->SetRawAudioSink(*ssrc, nullptr)
       : media_channel->SetDefaultRawAudioSink(nullptr);
}

void RemoteAudioSource::SetState(SourceState new_state) {
  RTC_DCHECK_RUN_ON(main_thread_);
  if (state_ != new_state) {
    state_ = new_state;
    FireOnChanged();
  }
}

MediaSourceInterface::SourceState RemoteAudioSource::state() const {
  RTC_DCHECK_RUN_ON(main_thread_);
  return state_;
}

bool RemoteAudioSource::remote() const {
  RTC_DCHECK_RUN_ON(main_thread_);
  return true;
}

void RemoteAudioSource::SetVolume(double volume) {
  RTC_DCHECK_GE(volume, 0);
  RTC_DCHECK_LE(volume, 10);
  RTC_LOG(LS_INFO) << rtc::StringFormat("RAS::%s({volume=%.2f})", __func__,
                                        volume);
  for (auto* observer : audio_observers_) {
    observer->OnSetVolume(volume);
  }
}

void RemoteAudioSource::RegisterAudioObserver(AudioObserver* observer) {
  RTC_DCHECK(observer != NULL);
  RTC_DCHECK(!absl::c_linear_search(audio_observers_, observer));
  audio_observers_.push_back(observer);
}

void RemoteAudioSource::UnregisterAudioObserver(AudioObserver* observer) {
  RTC_DCHECK(observer != NULL);
  audio_observers_.remove(observer);
}

void RemoteAudioSource::AddSink(AudioTrackSinkInterface* sink) {
  RTC_DCHECK_RUN_ON(main_thread_);
  RTC_DCHECK(sink);

  if (state_ != MediaSourceInterface::kLive) {
    RTC_LOG(LS_ERROR) << "Can't register sink as the source isn't live.";
    return;
  }

  MutexLock lock(&sink_lock_);
  RTC_DCHECK(!absl::c_linear_search(sinks_, sink));
  sinks_.push_back(sink);
}

void RemoteAudioSource::RemoveSink(AudioTrackSinkInterface* sink) {
  RTC_DCHECK_RUN_ON(main_thread_);
  RTC_DCHECK(sink);

  MutexLock lock(&sink_lock_);
  sinks_.remove(sink);
}

void RemoteAudioSource::OnData(const AudioSinkInterface::Data& audio) {
  // Called on the externally-owned audio callback thread, via/from webrtc.
  MutexLock lock(&sink_lock_);
  for (auto* sink : sinks_) {
    // When peerconnection acts as an audio source, it should not provide
    // absolute capture timestamp.
    sink->OnData(audio.data, 16, audio.sample_rate, audio.channels,
                 audio.samples_per_channel,
                 /*absolute_capture_timestamp_ms=*/absl::nullopt);
  }
}

void RemoteAudioSource::OnAudioChannelGone() {
  if (on_audio_channel_gone_action_ != OnAudioChannelGoneAction::kEnd) {
    return;
  }
  // Called when the audio channel is deleted.  It may be the worker thread
  // in libjingle or may be a different worker thread.
  // This object needs to live long enough for the cleanup logic in OnMessage to
  // run, so take a reference to it as the data. Sometimes the message may not
  // be processed (because the thread was destroyed shortly after this call),
  // but that is fine because the thread destructor will take care of destroying
  // the message data which will release the reference on RemoteAudioSource.
  main_thread_->Post(RTC_FROM_HERE, this, 0,
                     new rtc::ScopedRefMessageData<RemoteAudioSource>(this));
}

void RemoteAudioSource::OnMessage(rtc::Message* msg) {
  RTC_DCHECK_RUN_ON(main_thread_);
  sinks_.clear();
  SetState(MediaSourceInterface::kEnded);
  // Will possibly delete this RemoteAudioSource since it is reference counted
  // in the message.
  delete msg->pdata;
}

}  // namespace webrtc
