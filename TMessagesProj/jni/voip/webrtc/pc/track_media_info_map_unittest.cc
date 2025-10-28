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

#include <stddef.h>

#include <cstdint>
#include <initializer_list>
#include <string>
#include <type_traits>
#include <utility>
#include <vector>

#include "api/media_types.h"
#include "api/rtp_parameters.h"
#include "api/test/mock_video_track.h"
#include "media/base/media_channel.h"
#include "pc/audio_track.h"
#include "pc/test/fake_video_track_source.h"
#include "pc/test/mock_rtp_receiver_internal.h"
#include "pc/test/mock_rtp_sender_internal.h"
#include "pc/video_track.h"
#include "rtc_base/checks.h"
#include "rtc_base/thread.h"
#include "test/gmock.h"
#include "test/gtest.h"

using ::testing::ElementsAre;

namespace webrtc {

namespace {

RtpParameters CreateRtpParametersWithSsrcs(
    std::initializer_list<uint32_t> ssrcs) {
  RtpParameters params;
  for (uint32_t ssrc : ssrcs) {
    RtpEncodingParameters encoding_params;
    encoding_params.ssrc = ssrc;
    params.encodings.push_back(encoding_params);
  }
  return params;
}

rtc::scoped_refptr<MockRtpSenderInternal> CreateMockRtpSender(
    cricket::MediaType media_type,
    std::initializer_list<uint32_t> ssrcs,
    rtc::scoped_refptr<MediaStreamTrackInterface> track) {
  uint32_t first_ssrc;
  if (ssrcs.size()) {
    first_ssrc = *ssrcs.begin();
  } else {
    first_ssrc = 0;
  }
  auto sender = rtc::make_ref_counted<MockRtpSenderInternal>();
  EXPECT_CALL(*sender, track())
      .WillRepeatedly(::testing::Return(std::move(track)));
  EXPECT_CALL(*sender, ssrc()).WillRepeatedly(::testing::Return(first_ssrc));
  EXPECT_CALL(*sender, media_type())
      .WillRepeatedly(::testing::Return(media_type));
  EXPECT_CALL(*sender, GetParameters())
      .WillRepeatedly(::testing::Return(CreateRtpParametersWithSsrcs(ssrcs)));
  EXPECT_CALL(*sender, AttachmentId()).WillRepeatedly(::testing::Return(1));
  return sender;
}

rtc::scoped_refptr<MockRtpReceiverInternal> CreateMockRtpReceiver(
    cricket::MediaType media_type,
    std::initializer_list<uint32_t> ssrcs,
    rtc::scoped_refptr<MediaStreamTrackInterface> track) {
  auto receiver = rtc::make_ref_counted<MockRtpReceiverInternal>();
  EXPECT_CALL(*receiver, track())
      .WillRepeatedly(::testing::Return(std::move(track)));
  EXPECT_CALL(*receiver, media_type())
      .WillRepeatedly(::testing::Return(media_type));
  EXPECT_CALL(*receiver, GetParameters())
      .WillRepeatedly(::testing::Return(CreateRtpParametersWithSsrcs(ssrcs)));
  EXPECT_CALL(*receiver, AttachmentId()).WillRepeatedly(::testing::Return(1));
  return receiver;
}

rtc::scoped_refptr<VideoTrackInterface> CreateVideoTrack(
    const std::string& id) {
  return VideoTrack::Create(id, FakeVideoTrackSource::Create(false),
                            rtc::Thread::Current());
}

rtc::scoped_refptr<VideoTrackInterface> CreateMockVideoTrack(
    const std::string& id) {
  auto track = MockVideoTrack::Create();
  EXPECT_CALL(*track, kind())
      .WillRepeatedly(::testing::Return(VideoTrack::kVideoKind));
  return track;
}

class TrackMediaInfoMapTest : public ::testing::Test {
 public:
  TrackMediaInfoMapTest() : TrackMediaInfoMapTest(true) {}

  explicit TrackMediaInfoMapTest(bool use_real_video_track)
      : local_audio_track_(AudioTrack::Create("LocalAudioTrack", nullptr)),
        remote_audio_track_(AudioTrack::Create("RemoteAudioTrack", nullptr)),
        local_video_track_(use_real_video_track
                               ? CreateVideoTrack("LocalVideoTrack")
                               : CreateMockVideoTrack("LocalVideoTrack")),
        remote_video_track_(use_real_video_track
                                ? CreateVideoTrack("RemoteVideoTrack")
                                : CreateMockVideoTrack("LocalVideoTrack")) {}

  void AddRtpSenderWithSsrcs(std::initializer_list<uint32_t> ssrcs,
                             MediaStreamTrackInterface* local_track) {
    rtc::scoped_refptr<MockRtpSenderInternal> rtp_sender = CreateMockRtpSender(
        local_track->kind() == MediaStreamTrackInterface::kAudioKind
            ? cricket::MEDIA_TYPE_AUDIO
            : cricket::MEDIA_TYPE_VIDEO,
        ssrcs, rtc::scoped_refptr<MediaStreamTrackInterface>(local_track));
    rtp_senders_.push_back(rtp_sender);

    if (local_track->kind() == MediaStreamTrackInterface::kAudioKind) {
      cricket::VoiceSenderInfo voice_sender_info;
      size_t i = 0;
      for (uint32_t ssrc : ssrcs) {
        voice_sender_info.local_stats.push_back(cricket::SsrcSenderInfo());
        voice_sender_info.local_stats[i++].ssrc = ssrc;
      }
      voice_media_info_.senders.push_back(voice_sender_info);
    } else {
      cricket::VideoSenderInfo video_sender_info;
      size_t i = 0;
      for (uint32_t ssrc : ssrcs) {
        video_sender_info.local_stats.push_back(cricket::SsrcSenderInfo());
        video_sender_info.local_stats[i++].ssrc = ssrc;
      }
      video_media_info_.senders.push_back(video_sender_info);
      video_media_info_.aggregated_senders.push_back(video_sender_info);
    }
  }

  void AddRtpReceiverWithSsrcs(std::initializer_list<uint32_t> ssrcs,
                               MediaStreamTrackInterface* remote_track) {
    auto rtp_receiver = CreateMockRtpReceiver(
        remote_track->kind() == MediaStreamTrackInterface::kAudioKind
            ? cricket::MEDIA_TYPE_AUDIO
            : cricket::MEDIA_TYPE_VIDEO,
        ssrcs, rtc::scoped_refptr<MediaStreamTrackInterface>(remote_track));
    rtp_receivers_.push_back(rtp_receiver);

    if (remote_track->kind() == MediaStreamTrackInterface::kAudioKind) {
      cricket::VoiceReceiverInfo voice_receiver_info;
      size_t i = 0;
      for (uint32_t ssrc : ssrcs) {
        voice_receiver_info.local_stats.push_back(cricket::SsrcReceiverInfo());
        voice_receiver_info.local_stats[i++].ssrc = ssrc;
      }
      voice_media_info_.receivers.push_back(voice_receiver_info);
    } else {
      cricket::VideoReceiverInfo video_receiver_info;
      size_t i = 0;
      for (uint32_t ssrc : ssrcs) {
        video_receiver_info.local_stats.push_back(cricket::SsrcReceiverInfo());
        video_receiver_info.local_stats[i++].ssrc = ssrc;
      }
      video_media_info_.receivers.push_back(video_receiver_info);
    }
  }

  // Copies the current state of `voice_media_info_` and `video_media_info_`
  // into the map.
  void InitializeMap() {
    map_.Initialize(voice_media_info_, video_media_info_, rtp_senders_,
                    rtp_receivers_);
  }

 private:
  rtc::AutoThread main_thread_;
  cricket::VoiceMediaInfo voice_media_info_;
  cricket::VideoMediaInfo video_media_info_;

 protected:
  std::vector<rtc::scoped_refptr<RtpSenderInternal>> rtp_senders_;
  std::vector<rtc::scoped_refptr<RtpReceiverInternal>> rtp_receivers_;
  TrackMediaInfoMap map_;
  rtc::scoped_refptr<AudioTrack> local_audio_track_;
  rtc::scoped_refptr<AudioTrack> remote_audio_track_;
  rtc::scoped_refptr<VideoTrackInterface> local_video_track_;
  rtc::scoped_refptr<VideoTrackInterface> remote_video_track_;
};

}  // namespace

TEST_F(TrackMediaInfoMapTest, SingleSenderReceiverPerTrackWithOneSsrc) {
  AddRtpSenderWithSsrcs({1}, local_audio_track_.get());
  AddRtpReceiverWithSsrcs({2}, remote_audio_track_.get());
  AddRtpSenderWithSsrcs({3}, local_video_track_.get());
  AddRtpReceiverWithSsrcs({4}, remote_video_track_.get());
  InitializeMap();
  // RTP audio sender -> local audio track
  EXPECT_EQ(map_.GetAudioTrack(map_.voice_media_info()->senders[0]),
            local_audio_track_.get());
  // RTP audio receiver -> remote audio track
  EXPECT_EQ(map_.GetAudioTrack(map_.voice_media_info()->receivers[0]),
            remote_audio_track_.get());
  // RTP video sender -> local video track
  EXPECT_EQ(map_.GetVideoTrack(map_.video_media_info()->senders[0]),
            local_video_track_.get());
  // RTP video receiver -> remote video track
  EXPECT_EQ(map_.GetVideoTrack(map_.video_media_info()->receivers[0]),
            remote_video_track_.get());
}

TEST_F(TrackMediaInfoMapTest,
       SingleSenderReceiverPerTrackWithAudioAndVideoUseSameSsrc) {
  AddRtpSenderWithSsrcs({1}, local_audio_track_.get());
  AddRtpReceiverWithSsrcs({2}, remote_audio_track_.get());
  AddRtpSenderWithSsrcs({1}, local_video_track_.get());
  AddRtpReceiverWithSsrcs({2}, remote_video_track_.get());
  InitializeMap();
  // RTP audio sender -> local audio track
  EXPECT_EQ(map_.GetAudioTrack(map_.voice_media_info()->senders[0]),
            local_audio_track_.get());
  // RTP audio receiver -> remote audio track
  EXPECT_EQ(map_.GetAudioTrack(map_.voice_media_info()->receivers[0]),
            remote_audio_track_.get());
  // RTP video sender -> local video track
  EXPECT_EQ(map_.GetVideoTrack(map_.video_media_info()->senders[0]),
            local_video_track_.get());
  // RTP video receiver -> remote video track
  EXPECT_EQ(map_.GetVideoTrack(map_.video_media_info()->receivers[0]),
            remote_video_track_.get());
}

TEST_F(TrackMediaInfoMapTest, SingleMultiSsrcSenderPerTrack) {
  AddRtpSenderWithSsrcs({1, 2}, local_audio_track_.get());
  AddRtpSenderWithSsrcs({3, 4}, local_video_track_.get());
  InitializeMap();
  // RTP audio senders -> local audio track
  EXPECT_EQ(map_.GetAudioTrack(map_.voice_media_info()->senders[0]),
            local_audio_track_.get());
  // RTP video senders -> local video track
  EXPECT_EQ(map_.GetVideoTrack(map_.video_media_info()->senders[0]),
            local_video_track_.get());
}

TEST_F(TrackMediaInfoMapTest, MultipleOneSsrcSendersPerTrack) {
  AddRtpSenderWithSsrcs({1}, local_audio_track_.get());
  AddRtpSenderWithSsrcs({2}, local_audio_track_.get());
  AddRtpSenderWithSsrcs({3}, local_video_track_.get());
  AddRtpSenderWithSsrcs({4}, local_video_track_.get());
  InitializeMap();
  // RTP audio senders -> local audio track
  EXPECT_EQ(map_.GetAudioTrack(map_.voice_media_info()->senders[0]),
            local_audio_track_.get());
  EXPECT_EQ(map_.GetAudioTrack(map_.voice_media_info()->senders[1]),
            local_audio_track_.get());
  // RTP video senders -> local video track
  EXPECT_EQ(map_.GetVideoTrack(map_.video_media_info()->senders[0]),
            local_video_track_.get());
  EXPECT_EQ(map_.GetVideoTrack(map_.video_media_info()->senders[1]),
            local_video_track_.get());
}

TEST_F(TrackMediaInfoMapTest, MultipleMultiSsrcSendersPerTrack) {
  AddRtpSenderWithSsrcs({1, 2}, local_audio_track_.get());
  AddRtpSenderWithSsrcs({3, 4}, local_audio_track_.get());
  AddRtpSenderWithSsrcs({5, 6}, local_video_track_.get());
  AddRtpSenderWithSsrcs({7, 8}, local_video_track_.get());
  InitializeMap();
  // RTP audio senders -> local audio track
  EXPECT_EQ(map_.GetAudioTrack(map_.voice_media_info()->senders[0]),
            local_audio_track_.get());
  EXPECT_EQ(map_.GetAudioTrack(map_.voice_media_info()->senders[1]),
            local_audio_track_.get());
  // RTP video senders -> local video track
  EXPECT_EQ(map_.GetVideoTrack(map_.video_media_info()->senders[0]),
            local_video_track_.get());
  EXPECT_EQ(map_.GetVideoTrack(map_.video_media_info()->senders[1]),
            local_video_track_.get());
}

// SSRCs can be reused for send and receive in loopback.
TEST_F(TrackMediaInfoMapTest, SingleSenderReceiverPerTrackWithSsrcNotUnique) {
  AddRtpSenderWithSsrcs({1}, local_audio_track_.get());
  AddRtpReceiverWithSsrcs({1}, remote_audio_track_.get());
  AddRtpSenderWithSsrcs({2}, local_video_track_.get());
  AddRtpReceiverWithSsrcs({2}, remote_video_track_.get());
  InitializeMap();
  // RTP audio senders -> local audio track
  EXPECT_EQ(map_.GetAudioTrack(map_.voice_media_info()->senders[0]),
            local_audio_track_.get());
  // RTP audio receiver -> remote audio track
  EXPECT_EQ(map_.GetAudioTrack(map_.voice_media_info()->receivers[0]),
            remote_audio_track_.get());
  // RTP video senders -> local video track
  EXPECT_EQ(map_.GetVideoTrack(map_.video_media_info()->senders[0]),
            local_video_track_.get());
  // RTP video receiver -> remote video track
  EXPECT_EQ(map_.GetVideoTrack(map_.video_media_info()->receivers[0]),
            remote_video_track_.get());
}

TEST_F(TrackMediaInfoMapTest, SsrcLookupFunction) {
  AddRtpSenderWithSsrcs({1}, local_audio_track_.get());
  AddRtpReceiverWithSsrcs({2}, remote_audio_track_.get());
  AddRtpSenderWithSsrcs({3}, local_video_track_.get());
  AddRtpReceiverWithSsrcs({4}, remote_video_track_.get());
  InitializeMap();
  EXPECT_TRUE(map_.GetVoiceSenderInfoBySsrc(1));
  EXPECT_TRUE(map_.GetVoiceReceiverInfoBySsrc(2));
  EXPECT_TRUE(map_.GetVideoSenderInfoBySsrc(3));
  EXPECT_TRUE(map_.GetVideoReceiverInfoBySsrc(4));
  EXPECT_FALSE(map_.GetVoiceSenderInfoBySsrc(2));
  EXPECT_FALSE(map_.GetVoiceSenderInfoBySsrc(1024));
}

TEST_F(TrackMediaInfoMapTest, GetAttachmentIdByTrack) {
  AddRtpSenderWithSsrcs({1}, local_audio_track_.get());
  InitializeMap();
  EXPECT_EQ(rtp_senders_[0]->AttachmentId(),
            map_.GetAttachmentIdByTrack(local_audio_track_.get()));
  EXPECT_EQ(absl::nullopt,
            map_.GetAttachmentIdByTrack(local_video_track_.get()));
}

}  // namespace webrtc
