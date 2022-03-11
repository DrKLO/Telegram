/*
 *  Copyright 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/media_stream.h"

#include <stddef.h>

#include <utility>

#include "rtc_base/checks.h"
#include "rtc_base/ref_counted_object.h"

namespace webrtc {

template <class V>
static typename V::iterator FindTrack(V* vector, const std::string& track_id) {
  typename V::iterator it = vector->begin();
  for (; it != vector->end(); ++it) {
    if ((*it)->id() == track_id) {
      break;
    }
  }
  return it;
}

rtc::scoped_refptr<MediaStream> MediaStream::Create(const std::string& id) {
  return rtc::make_ref_counted<MediaStream>(id);
}

MediaStream::MediaStream(const std::string& id) : id_(id) {}

bool MediaStream::AddTrack(AudioTrackInterface* track) {
  return AddTrack<AudioTrackVector, AudioTrackInterface>(
      &audio_tracks_, rtc::scoped_refptr<AudioTrackInterface>(track));
}

bool MediaStream::AddTrack(VideoTrackInterface* track) {
  return AddTrack<VideoTrackVector, VideoTrackInterface>(
      &video_tracks_, rtc::scoped_refptr<VideoTrackInterface>(track));
}

bool MediaStream::RemoveTrack(AudioTrackInterface* track) {
  return RemoveTrack<AudioTrackVector>(&audio_tracks_, track);
}

bool MediaStream::RemoveTrack(VideoTrackInterface* track) {
  return RemoveTrack<VideoTrackVector>(&video_tracks_, track);
}

rtc::scoped_refptr<AudioTrackInterface> MediaStream::FindAudioTrack(
    const std::string& track_id) {
  AudioTrackVector::iterator it = FindTrack(&audio_tracks_, track_id);
  if (it == audio_tracks_.end())
    return nullptr;
  return *it;
}

rtc::scoped_refptr<VideoTrackInterface> MediaStream::FindVideoTrack(
    const std::string& track_id) {
  VideoTrackVector::iterator it = FindTrack(&video_tracks_, track_id);
  if (it == video_tracks_.end())
    return nullptr;
  return *it;
}

template <typename TrackVector, typename Track>
bool MediaStream::AddTrack(TrackVector* tracks,
                           rtc::scoped_refptr<Track> track) {
  typename TrackVector::iterator it = FindTrack(tracks, track->id());
  if (it != tracks->end())
    return false;
  tracks->emplace_back(std::move((track)));
  FireOnChanged();
  return true;
}

template <typename TrackVector>
bool MediaStream::RemoveTrack(TrackVector* tracks,
                              MediaStreamTrackInterface* track) {
  RTC_DCHECK(tracks != NULL);
  if (!track)
    return false;
  typename TrackVector::iterator it = FindTrack(tracks, track->id());
  if (it == tracks->end())
    return false;
  tracks->erase(it);
  FireOnChanged();
  return true;
}

}  // namespace webrtc
