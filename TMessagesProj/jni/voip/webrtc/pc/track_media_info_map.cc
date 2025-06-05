/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/track_media_info_map.h"

#include <cstdint>
#include <set>
#include <type_traits>
#include <utility>

#include "api/media_types.h"
#include "api/rtp_parameters.h"
#include "media/base/stream_params.h"
#include "rtc_base/checks.h"
#include "rtc_base/thread.h"

namespace webrtc {

namespace {

template <typename K, typename V>
V FindValueOrNull(const std::map<K, V>& map, const K& key) {
  auto it = map.find(key);
  return (it != map.end()) ? it->second : nullptr;
}

template <typename K, typename V>
const V* FindAddressOrNull(const std::map<K, V>& map, const K& key) {
  auto it = map.find(key);
  return (it != map.end()) ? &it->second : nullptr;
}

void GetAudioAndVideoTrackBySsrc(
    rtc::ArrayView<rtc::scoped_refptr<RtpSenderInternal>> rtp_senders,
    rtc::ArrayView<rtc::scoped_refptr<RtpReceiverInternal>> rtp_receivers,
    std::map<uint32_t, AudioTrackInterface*>* local_audio_track_by_ssrc,
    std::map<uint32_t, VideoTrackInterface*>* local_video_track_by_ssrc,
    std::map<uint32_t, AudioTrackInterface*>* remote_audio_track_by_ssrc,
    std::map<uint32_t, VideoTrackInterface*>* remote_video_track_by_ssrc,
    AudioTrackInterface** unsignaled_audio_track,
    VideoTrackInterface** unsignaled_video_track) {
  RTC_DCHECK(local_audio_track_by_ssrc->empty());
  RTC_DCHECK(local_video_track_by_ssrc->empty());
  RTC_DCHECK(remote_audio_track_by_ssrc->empty());
  RTC_DCHECK(remote_video_track_by_ssrc->empty());
  for (const auto& rtp_sender : rtp_senders) {
    cricket::MediaType media_type = rtp_sender->media_type();
    MediaStreamTrackInterface* track = rtp_sender->track().get();
    if (!track) {
      continue;
    }
    // TODO(deadbeef): `ssrc` should be removed in favor of `GetParameters`.
    uint32_t ssrc = rtp_sender->ssrc();
    if (ssrc != 0) {
      if (media_type == cricket::MEDIA_TYPE_AUDIO) {
        RTC_DCHECK(local_audio_track_by_ssrc->find(ssrc) ==
                   local_audio_track_by_ssrc->end());
        (*local_audio_track_by_ssrc)[ssrc] =
            static_cast<AudioTrackInterface*>(track);
      } else {
        RTC_DCHECK(local_video_track_by_ssrc->find(ssrc) ==
                   local_video_track_by_ssrc->end());
        (*local_video_track_by_ssrc)[ssrc] =
            static_cast<VideoTrackInterface*>(track);
      }
    }
  }
  for (const auto& rtp_receiver : rtp_receivers) {
    cricket::MediaType media_type = rtp_receiver->media_type();
    MediaStreamTrackInterface* track = rtp_receiver->track().get();
    RTC_DCHECK(track);
    RtpParameters params = rtp_receiver->GetParameters();
    for (const RtpEncodingParameters& encoding : params.encodings) {
      if (!encoding.ssrc) {
        if (media_type == cricket::MEDIA_TYPE_AUDIO) {
          *unsignaled_audio_track = static_cast<AudioTrackInterface*>(track);
        } else {
          RTC_DCHECK(media_type == cricket::MEDIA_TYPE_VIDEO);
          *unsignaled_video_track = static_cast<VideoTrackInterface*>(track);
        }
        continue;
      }
      if (media_type == cricket::MEDIA_TYPE_AUDIO) {
        RTC_DCHECK(remote_audio_track_by_ssrc->find(*encoding.ssrc) ==
                   remote_audio_track_by_ssrc->end());
        (*remote_audio_track_by_ssrc)[*encoding.ssrc] =
            static_cast<AudioTrackInterface*>(track);
      } else {
        RTC_DCHECK(remote_video_track_by_ssrc->find(*encoding.ssrc) ==
                   remote_video_track_by_ssrc->end());
        (*remote_video_track_by_ssrc)[*encoding.ssrc] =
            static_cast<VideoTrackInterface*>(track);
      }
    }
  }
}

}  // namespace

TrackMediaInfoMap::TrackMediaInfoMap() = default;

void TrackMediaInfoMap::Initialize(
    absl::optional<cricket::VoiceMediaInfo> voice_media_info,
    absl::optional<cricket::VideoMediaInfo> video_media_info,
    rtc::ArrayView<rtc::scoped_refptr<RtpSenderInternal>> rtp_senders,
    rtc::ArrayView<rtc::scoped_refptr<RtpReceiverInternal>> rtp_receivers) {
  rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;
  RTC_DCHECK(!is_initialized_);
  is_initialized_ = true;
  voice_media_info_ = std::move(voice_media_info);
  video_media_info_ = std::move(video_media_info);

  std::map<uint32_t, AudioTrackInterface*> local_audio_track_by_ssrc;
  std::map<uint32_t, VideoTrackInterface*> local_video_track_by_ssrc;
  std::map<uint32_t, AudioTrackInterface*> remote_audio_track_by_ssrc;
  std::map<uint32_t, VideoTrackInterface*> remote_video_track_by_ssrc;
  AudioTrackInterface* unsignaled_audio_track = nullptr;
  VideoTrackInterface* unsignaled_video_track = nullptr;
  GetAudioAndVideoTrackBySsrc(
      rtp_senders, rtp_receivers, &local_audio_track_by_ssrc,
      &local_video_track_by_ssrc, &remote_audio_track_by_ssrc,
      &remote_video_track_by_ssrc, &unsignaled_audio_track,
      &unsignaled_video_track);

  for (const auto& sender : rtp_senders) {
    attachment_id_by_track_[sender->track().get()] = sender->AttachmentId();
  }
  for (const auto& receiver : rtp_receivers) {
    attachment_id_by_track_[receiver->track().get()] = receiver->AttachmentId();
  }

  if (voice_media_info_.has_value()) {
    for (auto& sender_info : voice_media_info_->senders) {
      AudioTrackInterface* associated_track =
          FindValueOrNull(local_audio_track_by_ssrc, sender_info.ssrc());
      if (associated_track) {
        // One sender is associated with at most one track.
        // One track may be associated with multiple senders.
        audio_track_by_sender_info_[&sender_info] = associated_track;
      }
      if (sender_info.ssrc() == 0)
        continue;  // Unconnected SSRC. bugs.webrtc.org/8673
      RTC_CHECK(voice_info_by_sender_ssrc_.count(sender_info.ssrc()) == 0)
          << "Duplicate voice sender SSRC: " << sender_info.ssrc();
      voice_info_by_sender_ssrc_[sender_info.ssrc()] = &sender_info;
    }
    for (auto& receiver_info : voice_media_info_->receivers) {
      AudioTrackInterface* associated_track =
          FindValueOrNull(remote_audio_track_by_ssrc, receiver_info.ssrc());
      if (associated_track) {
        // One receiver is associated with at most one track, which is uniquely
        // associated with that receiver.
        audio_track_by_receiver_info_[&receiver_info] = associated_track;
      } else if (unsignaled_audio_track) {
        audio_track_by_receiver_info_[&receiver_info] = unsignaled_audio_track;
      }
      RTC_CHECK(voice_info_by_receiver_ssrc_.count(receiver_info.ssrc()) == 0)
          << "Duplicate voice receiver SSRC: " << receiver_info.ssrc();
      voice_info_by_receiver_ssrc_[receiver_info.ssrc()] = &receiver_info;
    }
  }
  if (video_media_info_.has_value()) {
    for (auto& sender_info : video_media_info_->senders) {
      std::set<uint32_t> ssrcs;
      ssrcs.insert(sender_info.ssrc());
      for (auto& ssrc_group : sender_info.ssrc_groups) {
        for (auto ssrc : ssrc_group.ssrcs) {
          ssrcs.insert(ssrc);
        }
      }
      for (auto ssrc : ssrcs) {
        VideoTrackInterface* associated_track =
            FindValueOrNull(local_video_track_by_ssrc, ssrc);
        if (associated_track) {
          // One sender is associated with at most one track.
          // One track may be associated with multiple senders.
          video_track_by_sender_info_[&sender_info] = associated_track;
          break;
        }
      }
    }
    for (auto& sender_info : video_media_info_->aggregated_senders) {
      if (sender_info.ssrc() == 0)
        continue;  // Unconnected SSRC. bugs.webrtc.org/8673
      RTC_DCHECK(video_info_by_sender_ssrc_.count(sender_info.ssrc()) == 0)
          << "Duplicate video sender SSRC: " << sender_info.ssrc();
      video_info_by_sender_ssrc_[sender_info.ssrc()] = &sender_info;
      VideoTrackInterface* associated_track =
          FindValueOrNull(local_video_track_by_ssrc, sender_info.ssrc());
      if (associated_track) {
        video_track_by_sender_info_[&sender_info] = associated_track;
      }
    }
    for (auto& receiver_info : video_media_info_->receivers) {
      VideoTrackInterface* associated_track =
          FindValueOrNull(remote_video_track_by_ssrc, receiver_info.ssrc());
      if (associated_track) {
        // One receiver is associated with at most one track, which is uniquely
        // associated with that receiver.
        video_track_by_receiver_info_[&receiver_info] = associated_track;
      } else if (unsignaled_video_track) {
        video_track_by_receiver_info_[&receiver_info] = unsignaled_video_track;
      }
      RTC_DCHECK(video_info_by_receiver_ssrc_.count(receiver_info.ssrc()) == 0)
          << "Duplicate video receiver SSRC: " << receiver_info.ssrc();
      video_info_by_receiver_ssrc_[receiver_info.ssrc()] = &receiver_info;
    }
  }
}

const cricket::VoiceSenderInfo* TrackMediaInfoMap::GetVoiceSenderInfoBySsrc(
    uint32_t ssrc) const {
  RTC_DCHECK(is_initialized_);
  return FindValueOrNull(voice_info_by_sender_ssrc_, ssrc);
}
const cricket::VoiceReceiverInfo* TrackMediaInfoMap::GetVoiceReceiverInfoBySsrc(
    uint32_t ssrc) const {
  RTC_DCHECK(is_initialized_);
  return FindValueOrNull(voice_info_by_receiver_ssrc_, ssrc);
}

const cricket::VideoSenderInfo* TrackMediaInfoMap::GetVideoSenderInfoBySsrc(
    uint32_t ssrc) const {
  RTC_DCHECK(is_initialized_);
  return FindValueOrNull(video_info_by_sender_ssrc_, ssrc);
}

const cricket::VideoReceiverInfo* TrackMediaInfoMap::GetVideoReceiverInfoBySsrc(
    uint32_t ssrc) const {
  RTC_DCHECK(is_initialized_);
  return FindValueOrNull(video_info_by_receiver_ssrc_, ssrc);
}

rtc::scoped_refptr<AudioTrackInterface> TrackMediaInfoMap::GetAudioTrack(
    const cricket::VoiceSenderInfo& voice_sender_info) const {
  RTC_DCHECK(is_initialized_);
  return FindValueOrNull(audio_track_by_sender_info_, &voice_sender_info);
}

rtc::scoped_refptr<AudioTrackInterface> TrackMediaInfoMap::GetAudioTrack(
    const cricket::VoiceReceiverInfo& voice_receiver_info) const {
  RTC_DCHECK(is_initialized_);
  return FindValueOrNull(audio_track_by_receiver_info_, &voice_receiver_info);
}

rtc::scoped_refptr<VideoTrackInterface> TrackMediaInfoMap::GetVideoTrack(
    const cricket::VideoSenderInfo& video_sender_info) const {
  RTC_DCHECK(is_initialized_);
  return FindValueOrNull(video_track_by_sender_info_, &video_sender_info);
}

rtc::scoped_refptr<VideoTrackInterface> TrackMediaInfoMap::GetVideoTrack(
    const cricket::VideoReceiverInfo& video_receiver_info) const {
  RTC_DCHECK(is_initialized_);
  return FindValueOrNull(video_track_by_receiver_info_, &video_receiver_info);
}

absl::optional<int> TrackMediaInfoMap::GetAttachmentIdByTrack(
    const MediaStreamTrackInterface* track) const {
  RTC_DCHECK(is_initialized_);
  auto it = attachment_id_by_track_.find(track);
  return it != attachment_id_by_track_.end() ? absl::optional<int>(it->second)
                                             : absl::nullopt;
}

}  // namespace webrtc
