/*
 *  Copyright 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/media_stream_observer.h"

#include <functional>
#include <string>
#include <utility>
#include <vector>

#include "absl/algorithm/container.h"

namespace webrtc {

MediaStreamObserver::MediaStreamObserver(
    MediaStreamInterface* stream,
    std::function<void(AudioTrackInterface*, MediaStreamInterface*)>
        audio_track_added_callback,
    std::function<void(AudioTrackInterface*, MediaStreamInterface*)>
        audio_track_removed_callback,
    std::function<void(VideoTrackInterface*, MediaStreamInterface*)>
        video_track_added_callback,
    std::function<void(VideoTrackInterface*, MediaStreamInterface*)>
        video_track_removed_callback)
    : stream_(stream),
      cached_audio_tracks_(stream->GetAudioTracks()),
      cached_video_tracks_(stream->GetVideoTracks()),
      audio_track_added_callback_(std::move(audio_track_added_callback)),
      audio_track_removed_callback_(std::move(audio_track_removed_callback)),
      video_track_added_callback_(std::move(video_track_added_callback)),
      video_track_removed_callback_(std::move(video_track_removed_callback)) {
  stream_->RegisterObserver(this);
}

MediaStreamObserver::~MediaStreamObserver() {
  stream_->UnregisterObserver(this);
}

void MediaStreamObserver::OnChanged() {
  AudioTrackVector new_audio_tracks = stream_->GetAudioTracks();
  VideoTrackVector new_video_tracks = stream_->GetVideoTracks();

  // Find removed audio tracks.
  for (const auto& cached_track : cached_audio_tracks_) {
    if (absl::c_none_of(
            new_audio_tracks,
            [cached_track](const AudioTrackVector::value_type& new_track) {
              return new_track->id() == cached_track->id();
            })) {
      audio_track_removed_callback_(cached_track.get(), stream_.get());
    }
  }

  // Find added audio tracks.
  for (const auto& new_track : new_audio_tracks) {
    if (absl::c_none_of(
            cached_audio_tracks_,
            [new_track](const AudioTrackVector::value_type& cached_track) {
              return new_track->id() == cached_track->id();
            })) {
      audio_track_added_callback_(new_track.get(), stream_.get());
    }
  }

  // Find removed video tracks.
  for (const auto& cached_track : cached_video_tracks_) {
    if (absl::c_none_of(
            new_video_tracks,
            [cached_track](const VideoTrackVector::value_type& new_track) {
              return new_track->id() == cached_track->id();
            })) {
      video_track_removed_callback_(cached_track.get(), stream_.get());
    }
  }

  // Find added video tracks.
  for (const auto& new_track : new_video_tracks) {
    if (absl::c_none_of(
            cached_video_tracks_,
            [new_track](const VideoTrackVector::value_type& cached_track) {
              return new_track->id() == cached_track->id();
            })) {
      video_track_added_callback_(new_track.get(), stream_.get());
    }
  }

  cached_audio_tracks_ = new_audio_tracks;
  cached_video_tracks_ = new_video_tracks;
}

}  // namespace webrtc
