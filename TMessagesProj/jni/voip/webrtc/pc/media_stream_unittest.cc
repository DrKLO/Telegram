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

#include "pc/audio_track.h"
#include "pc/test/fake_video_track_source.h"
#include "pc/video_track.h"
#include "rtc_base/thread.h"
#include "test/gmock.h"
#include "test/gtest.h"

static const char kStreamId1[] = "local_stream_1";
static const char kVideoTrackId[] = "dummy_video_cam_1";
static const char kAudioTrackId[] = "dummy_microphone_1";

using rtc::scoped_refptr;
using ::testing::Exactly;

namespace webrtc {

// Helper class to test Observer.
class MockObserver : public ObserverInterface {
 public:
  explicit MockObserver(NotifierInterface* notifier) : notifier_(notifier) {
    notifier_->RegisterObserver(this);
  }

  ~MockObserver() { Unregister(); }

  void Unregister() {
    if (notifier_) {
      notifier_->UnregisterObserver(this);
      notifier_ = nullptr;
    }
  }

  MOCK_METHOD(void, OnChanged, (), (override));

 private:
  NotifierInterface* notifier_;
};

class MediaStreamTest : public ::testing::Test {
 protected:
  virtual void SetUp() {
    stream_ = MediaStream::Create(kStreamId1);
    ASSERT_TRUE(stream_.get() != NULL);

    video_track_ = VideoTrack::Create(
        kVideoTrackId, FakeVideoTrackSource::Create(), rtc::Thread::Current());
    ASSERT_TRUE(video_track_.get() != NULL);
    EXPECT_EQ(MediaStreamTrackInterface::kLive, video_track_->state());

    audio_track_ = AudioTrack::Create(kAudioTrackId, nullptr);

    ASSERT_TRUE(audio_track_.get() != NULL);
    EXPECT_EQ(MediaStreamTrackInterface::kLive, audio_track_->state());

    EXPECT_TRUE(stream_->AddTrack(video_track_));
    EXPECT_FALSE(stream_->AddTrack(video_track_));
    EXPECT_TRUE(stream_->AddTrack(audio_track_));
    EXPECT_FALSE(stream_->AddTrack(audio_track_));
  }

  void ChangeTrack(MediaStreamTrackInterface* track) {
    MockObserver observer(track);

    EXPECT_CALL(observer, OnChanged()).Times(Exactly(1));
    track->set_enabled(false);
    EXPECT_FALSE(track->enabled());
  }

  rtc::AutoThread main_thread_;
  scoped_refptr<MediaStreamInterface> stream_;
  scoped_refptr<AudioTrackInterface> audio_track_;
  scoped_refptr<VideoTrackInterface> video_track_;
};

TEST_F(MediaStreamTest, GetTrackInfo) {
  ASSERT_EQ(1u, stream_->GetVideoTracks().size());
  ASSERT_EQ(1u, stream_->GetAudioTracks().size());

  // Verify the video track.
  scoped_refptr<MediaStreamTrackInterface> video_track(
      stream_->GetVideoTracks()[0]);
  EXPECT_EQ(0, video_track->id().compare(kVideoTrackId));
  EXPECT_TRUE(video_track->enabled());

  ASSERT_EQ(1u, stream_->GetVideoTracks().size());
  EXPECT_TRUE(stream_->GetVideoTracks()[0].get() == video_track.get());
  EXPECT_TRUE(stream_->FindVideoTrack(video_track->id()).get() ==
              video_track.get());
  video_track = stream_->GetVideoTracks()[0];
  EXPECT_EQ(0, video_track->id().compare(kVideoTrackId));
  EXPECT_TRUE(video_track->enabled());

  // Verify the audio track.
  scoped_refptr<MediaStreamTrackInterface> audio_track(
      stream_->GetAudioTracks()[0]);
  EXPECT_EQ(0, audio_track->id().compare(kAudioTrackId));
  EXPECT_TRUE(audio_track->enabled());
  ASSERT_EQ(1u, stream_->GetAudioTracks().size());
  EXPECT_TRUE(stream_->GetAudioTracks()[0].get() == audio_track.get());
  EXPECT_TRUE(stream_->FindAudioTrack(audio_track->id()).get() ==
              audio_track.get());
  audio_track = stream_->GetAudioTracks()[0];
  EXPECT_EQ(0, audio_track->id().compare(kAudioTrackId));
  EXPECT_TRUE(audio_track->enabled());
}

TEST_F(MediaStreamTest, RemoveTrack) {
  MockObserver observer(stream_.get());

  EXPECT_CALL(observer, OnChanged()).Times(Exactly(2));

  EXPECT_TRUE(stream_->RemoveTrack(audio_track_));
  EXPECT_FALSE(stream_->RemoveTrack(audio_track_));
  EXPECT_EQ(0u, stream_->GetAudioTracks().size());
  EXPECT_EQ(0u, stream_->GetAudioTracks().size());

  EXPECT_TRUE(stream_->RemoveTrack(video_track_));
  EXPECT_FALSE(stream_->RemoveTrack(video_track_));

  EXPECT_EQ(0u, stream_->GetVideoTracks().size());
  EXPECT_EQ(0u, stream_->GetVideoTracks().size());

  EXPECT_FALSE(stream_->RemoveTrack(rtc::scoped_refptr<AudioTrackInterface>()));
  EXPECT_FALSE(stream_->RemoveTrack(rtc::scoped_refptr<VideoTrackInterface>()));
}

TEST_F(MediaStreamTest, ChangeVideoTrack) {
  scoped_refptr<VideoTrackInterface> video_track(stream_->GetVideoTracks()[0]);
  ChangeTrack(video_track.get());
}

TEST_F(MediaStreamTest, ChangeAudioTrack) {
  scoped_refptr<AudioTrackInterface> audio_track(stream_->GetAudioTracks()[0]);
  ChangeTrack(audio_track.get());
}

}  // namespace webrtc
