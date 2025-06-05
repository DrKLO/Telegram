/*
 *  Copyright 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/audio_track.h"

#include "rtc_base/checks.h"

namespace webrtc {

// static
rtc::scoped_refptr<AudioTrack> AudioTrack::Create(
    absl::string_view id,
    const rtc::scoped_refptr<AudioSourceInterface>& source) {
  return rtc::make_ref_counted<AudioTrack>(id, source);
}

AudioTrack::AudioTrack(absl::string_view label,
                       const rtc::scoped_refptr<AudioSourceInterface>& source)
    : MediaStreamTrack<AudioTrackInterface>(label), audio_source_(source) {
  if (audio_source_) {
    audio_source_->RegisterObserver(this);
    OnChanged();
  }
}

AudioTrack::~AudioTrack() {
  RTC_DCHECK_RUN_ON(&signaling_thread_checker_);
  set_state(MediaStreamTrackInterface::kEnded);
  if (audio_source_)
    audio_source_->UnregisterObserver(this);
}

std::string AudioTrack::kind() const {
  return kAudioKind;
}

AudioSourceInterface* AudioTrack::GetSource() const {
  // Callable from any thread.
  return audio_source_.get();
}

void AudioTrack::AddSink(AudioTrackSinkInterface* sink) {
  RTC_DCHECK_RUN_ON(&signaling_thread_checker_);
  if (audio_source_)
    audio_source_->AddSink(sink);
}

void AudioTrack::RemoveSink(AudioTrackSinkInterface* sink) {
  RTC_DCHECK_RUN_ON(&signaling_thread_checker_);
  if (audio_source_)
    audio_source_->RemoveSink(sink);
}

void AudioTrack::OnChanged() {
  RTC_DCHECK_RUN_ON(&signaling_thread_checker_);
  if (audio_source_->state() == MediaSourceInterface::kEnded) {
    set_state(kEnded);
  } else {
    set_state(kLive);
  }
}

}  // namespace webrtc
