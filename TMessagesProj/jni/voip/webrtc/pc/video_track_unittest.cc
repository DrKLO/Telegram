/*
 *  Copyright 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/video_track.h"

#include <memory>

#include "media/base/fake_frame_source.h"
#include "pc/test/fake_video_track_renderer.h"
#include "pc/test/fake_video_track_source.h"
#include "pc/video_track_source.h"
#include "rtc_base/time_utils.h"
#include "test/gtest.h"

using webrtc::FakeVideoTrackRenderer;
using webrtc::FakeVideoTrackSource;
using webrtc::MediaSourceInterface;
using webrtc::MediaStreamTrackInterface;
using webrtc::VideoTrack;
using webrtc::VideoTrackInterface;
using webrtc::VideoTrackSource;

class VideoTrackTest : public ::testing::Test {
 public:
  VideoTrackTest() : frame_source_(640, 480, rtc::kNumMicrosecsPerSec / 30) {
    static const char kVideoTrackId[] = "track_id";
    video_track_source_ = rtc::make_ref_counted<FakeVideoTrackSource>(
        /*is_screencast=*/false);
    video_track_ = VideoTrack::Create(kVideoTrackId, video_track_source_,
                                      rtc::Thread::Current());
  }

 protected:
  rtc::AutoThread main_thread_;
  rtc::scoped_refptr<FakeVideoTrackSource> video_track_source_;
  rtc::scoped_refptr<VideoTrack> video_track_;
  cricket::FakeFrameSource frame_source_;
};

// VideoTrack::Create will create an API proxy around the source object.
// The `GetSource` method provides access to the proxy object intented for API
// use while the GetSourceInternal() provides direct access to the source object
// as provided to the `VideoTrack::Create` factory function.
TEST_F(VideoTrackTest, CheckApiProxyAndInternalSource) {
  EXPECT_NE(video_track_->GetSource(), video_track_source_.get());
  EXPECT_EQ(video_track_->GetSourceInternal(), video_track_source_.get());
}

// Test changing the source state also changes the track state.
TEST_F(VideoTrackTest, SourceStateChangeTrackState) {
  EXPECT_EQ(MediaStreamTrackInterface::kLive, video_track_->state());
  video_track_source_->SetState(MediaSourceInterface::kEnded);
  EXPECT_EQ(MediaStreamTrackInterface::kEnded, video_track_->state());
}

// Test adding renderers to a video track and render to them by providing
// frames to the source.
TEST_F(VideoTrackTest, RenderVideo) {
  // FakeVideoTrackRenderer register itself to `video_track_`
  std::unique_ptr<FakeVideoTrackRenderer> renderer_1(
      new FakeVideoTrackRenderer(video_track_.get()));

  video_track_source_->InjectFrame(frame_source_.GetFrame());
  EXPECT_EQ(1, renderer_1->num_rendered_frames());

  // FakeVideoTrackRenderer register itself to `video_track_`
  std::unique_ptr<FakeVideoTrackRenderer> renderer_2(
      new FakeVideoTrackRenderer(video_track_.get()));
  video_track_source_->InjectFrame(frame_source_.GetFrame());
  EXPECT_EQ(2, renderer_1->num_rendered_frames());
  EXPECT_EQ(1, renderer_2->num_rendered_frames());

  renderer_1.reset(nullptr);
  video_track_source_->InjectFrame(frame_source_.GetFrame());
  EXPECT_EQ(2, renderer_2->num_rendered_frames());
}

// Test that disabling the track results in blacked out frames.
TEST_F(VideoTrackTest, DisableTrackBlackout) {
  std::unique_ptr<FakeVideoTrackRenderer> renderer(
      new FakeVideoTrackRenderer(video_track_.get()));

  video_track_source_->InjectFrame(frame_source_.GetFrame());
  EXPECT_EQ(1, renderer->num_rendered_frames());
  EXPECT_FALSE(renderer->black_frame());

  video_track_->set_enabled(false);
  video_track_source_->InjectFrame(frame_source_.GetFrame());
  EXPECT_EQ(2, renderer->num_rendered_frames());
  EXPECT_TRUE(renderer->black_frame());

  video_track_->set_enabled(true);
  video_track_source_->InjectFrame(frame_source_.GetFrame());
  EXPECT_EQ(3, renderer->num_rendered_frames());
  EXPECT_FALSE(renderer->black_frame());
}
