/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef PC_TRACK_MEDIA_INFO_MAP_H_
#define PC_TRACK_MEDIA_INFO_MAP_H_

#include <stdint.h>

#include <map>
#include <memory>
#include <string>
#include <vector>

#include "absl/types/optional.h"
#include "api/media_stream_interface.h"
#include "api/scoped_refptr.h"
#include "media/base/media_channel.h"
#include "pc/rtp_receiver.h"
#include "pc/rtp_sender.h"
#include "rtc_base/ref_count.h"

namespace webrtc {

// Audio/video tracks and sender/receiver statistical information are associated
// with each other based on attachments to RTP senders/receivers. This class
// maps that relationship, in both directions, so that stats about a track can
// be retrieved on a per-attachment basis.
//
// An RTP sender/receiver sends or receives media for a set of SSRCs. The media
// comes from an audio/video track that is attached to it.
// |[Voice/Video][Sender/Receiver]Info| has statistical information for a set of
// SSRCs. Looking at the RTP senders and receivers uncovers the track <-> info
// relationships, which this class does.
class TrackMediaInfoMap {
 public:
  TrackMediaInfoMap(
      std::unique_ptr<cricket::VoiceMediaInfo> voice_media_info,
      std::unique_ptr<cricket::VideoMediaInfo> video_media_info,
      const std::vector<rtc::scoped_refptr<RtpSenderInternal>>& rtp_senders,
      const std::vector<rtc::scoped_refptr<RtpReceiverInternal>>&
          rtp_receivers);

  const cricket::VoiceMediaInfo* voice_media_info() const {
    return voice_media_info_.get();
  }
  const cricket::VideoMediaInfo* video_media_info() const {
    return video_media_info_.get();
  }

  const std::vector<cricket::VoiceSenderInfo*>* GetVoiceSenderInfos(
      const AudioTrackInterface& local_audio_track) const;
  const cricket::VoiceReceiverInfo* GetVoiceReceiverInfo(
      const AudioTrackInterface& remote_audio_track) const;
  const std::vector<cricket::VideoSenderInfo*>* GetVideoSenderInfos(
      const VideoTrackInterface& local_video_track) const;
  const cricket::VideoReceiverInfo* GetVideoReceiverInfo(
      const VideoTrackInterface& remote_video_track) const;

  const cricket::VoiceSenderInfo* GetVoiceSenderInfoBySsrc(uint32_t ssrc) const;
  const cricket::VoiceReceiverInfo* GetVoiceReceiverInfoBySsrc(
      uint32_t ssrc) const;
  const cricket::VideoSenderInfo* GetVideoSenderInfoBySsrc(uint32_t ssrc) const;
  const cricket::VideoReceiverInfo* GetVideoReceiverInfoBySsrc(
      uint32_t ssrc) const;

  rtc::scoped_refptr<AudioTrackInterface> GetAudioTrack(
      const cricket::VoiceSenderInfo& voice_sender_info) const;
  rtc::scoped_refptr<AudioTrackInterface> GetAudioTrack(
      const cricket::VoiceReceiverInfo& voice_receiver_info) const;
  rtc::scoped_refptr<VideoTrackInterface> GetVideoTrack(
      const cricket::VideoSenderInfo& video_sender_info) const;
  rtc::scoped_refptr<VideoTrackInterface> GetVideoTrack(
      const cricket::VideoReceiverInfo& video_receiver_info) const;

  // TODO(hta): Remove this function, and redesign the callers not to need it.
  // It is not going to work if a track is attached multiple times, and
  // it is not going to work if a received track is attached as a sending
  // track (loopback).
  absl::optional<int> GetAttachmentIdByTrack(
      const MediaStreamTrackInterface* track) const;

 private:
  absl::optional<std::string> voice_mid_;
  absl::optional<std::string> video_mid_;
  std::unique_ptr<cricket::VoiceMediaInfo> voice_media_info_;
  std::unique_ptr<cricket::VideoMediaInfo> video_media_info_;
  // These maps map tracks (identified by a pointer) to their corresponding info
  // object of the correct kind. One track can map to multiple info objects.
  std::map<const AudioTrackInterface*, std::vector<cricket::VoiceSenderInfo*>>
      voice_infos_by_local_track_;
  std::map<const AudioTrackInterface*, cricket::VoiceReceiverInfo*>
      voice_info_by_remote_track_;
  std::map<const VideoTrackInterface*, std::vector<cricket::VideoSenderInfo*>>
      video_infos_by_local_track_;
  std::map<const VideoTrackInterface*, cricket::VideoReceiverInfo*>
      video_info_by_remote_track_;
  // These maps map info objects to their corresponding tracks. They are always
  // the inverse of the maps above. One info object always maps to only one
  // track.
  std::map<const cricket::VoiceSenderInfo*,
           rtc::scoped_refptr<AudioTrackInterface>>
      audio_track_by_sender_info_;
  std::map<const cricket::VoiceReceiverInfo*,
           rtc::scoped_refptr<AudioTrackInterface>>
      audio_track_by_receiver_info_;
  std::map<const cricket::VideoSenderInfo*,
           rtc::scoped_refptr<VideoTrackInterface>>
      video_track_by_sender_info_;
  std::map<const cricket::VideoReceiverInfo*,
           rtc::scoped_refptr<VideoTrackInterface>>
      video_track_by_receiver_info_;
  // Map of tracks to attachment IDs.
  // Necessary because senders and receivers live on the signaling thread,
  // but the attachment IDs are needed while building stats on the networking
  // thread, so we can't look them up in the senders/receivers without
  // thread jumping.
  std::map<const MediaStreamTrackInterface*, int> attachment_id_by_track_;
  // These maps map SSRCs to the corresponding voice or video info objects.
  std::map<uint32_t, cricket::VoiceSenderInfo*> voice_info_by_sender_ssrc_;
  std::map<uint32_t, cricket::VoiceReceiverInfo*> voice_info_by_receiver_ssrc_;
  std::map<uint32_t, cricket::VideoSenderInfo*> video_info_by_sender_ssrc_;
  std::map<uint32_t, cricket::VideoReceiverInfo*> video_info_by_receiver_ssrc_;
};

}  // namespace webrtc

#endif  // PC_TRACK_MEDIA_INFO_MAP_H_
