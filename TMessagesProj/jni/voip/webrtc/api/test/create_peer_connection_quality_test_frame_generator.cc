/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/test/create_peer_connection_quality_test_frame_generator.h"

#include <utility>
#include <vector>

#include "api/test/create_frame_generator.h"
#include "api/test/peerconnection_quality_test_fixture.h"
#include "rtc_base/checks.h"
#include "test/testsupport/file_utils.h"

namespace webrtc {
namespace webrtc_pc_e2e {

using VideoConfig =
    ::webrtc::webrtc_pc_e2e::PeerConnectionE2EQualityTestFixture::VideoConfig;
using ScreenShareConfig = ::webrtc::webrtc_pc_e2e::
    PeerConnectionE2EQualityTestFixture::ScreenShareConfig;

void ValidateScreenShareConfig(const VideoConfig& video_config,
                               const ScreenShareConfig& screen_share_config) {
  if (screen_share_config.slides_yuv_file_names.empty()) {
    if (screen_share_config.scrolling_params) {
      // If we have scrolling params, then its |source_width| and |source_heigh|
      // will be used as width and height of video input, so we have to validate
      // it against width and height of default input.
      RTC_CHECK_EQ(screen_share_config.scrolling_params->source_width,
                   kDefaultSlidesWidth);
      RTC_CHECK_EQ(screen_share_config.scrolling_params->source_height,
                   kDefaultSlidesHeight);
    } else {
      RTC_CHECK_EQ(video_config.width, kDefaultSlidesWidth);
      RTC_CHECK_EQ(video_config.height, kDefaultSlidesHeight);
    }
  }
  if (screen_share_config.scrolling_params) {
    RTC_CHECK_LE(screen_share_config.scrolling_params->duration,
                 screen_share_config.slide_change_interval);
    RTC_CHECK_GE(screen_share_config.scrolling_params->source_width,
                 video_config.width);
    RTC_CHECK_GE(screen_share_config.scrolling_params->source_height,
                 video_config.height);
  }
}

std::unique_ptr<test::FrameGeneratorInterface> CreateSquareFrameGenerator(
    const VideoConfig& video_config,
    absl::optional<test::FrameGeneratorInterface::OutputType> type) {
  return test::CreateSquareFrameGenerator(
      video_config.width, video_config.height, std::move(type), absl::nullopt);
}

std::unique_ptr<test::FrameGeneratorInterface> CreateFromYuvFileFrameGenerator(
    const VideoConfig& video_config,
    std::string filename) {
  return test::CreateFromYuvFileFrameGenerator(
      {std::move(filename)}, video_config.width, video_config.height,
      /*frame_repeat_count=*/1);
}

std::unique_ptr<test::FrameGeneratorInterface> CreateScreenShareFrameGenerator(
    const VideoConfig& video_config,
    const ScreenShareConfig& screen_share_config) {
  ValidateScreenShareConfig(video_config, screen_share_config);
  if (screen_share_config.generate_slides) {
    return test::CreateSlideFrameGenerator(
        video_config.width, video_config.height,
        screen_share_config.slide_change_interval.seconds() * video_config.fps);
  }
  std::vector<std::string> slides = screen_share_config.slides_yuv_file_names;
  if (slides.empty()) {
    // If slides is empty we need to add default slides as source. In such case
    // video width and height is validated to be equal to kDefaultSlidesWidth
    // and kDefaultSlidesHeight.
    slides.push_back(test::ResourcePath("web_screenshot_1850_1110", "yuv"));
    slides.push_back(test::ResourcePath("presentation_1850_1110", "yuv"));
    slides.push_back(test::ResourcePath("photo_1850_1110", "yuv"));
    slides.push_back(test::ResourcePath("difficult_photo_1850_1110", "yuv"));
  }
  if (!screen_share_config.scrolling_params) {
    // Cycle image every slide_change_interval seconds.
    return test::CreateFromYuvFileFrameGenerator(
        slides, video_config.width, video_config.height,
        screen_share_config.slide_change_interval.seconds() * video_config.fps);
  }

  TimeDelta pause_duration = screen_share_config.slide_change_interval -
                             screen_share_config.scrolling_params->duration;
  RTC_DCHECK(pause_duration >= TimeDelta::Zero());
  return test::CreateScrollingInputFromYuvFilesFrameGenerator(
      Clock::GetRealTimeClock(), slides,
      screen_share_config.scrolling_params->source_width,
      screen_share_config.scrolling_params->source_height, video_config.width,
      video_config.height, screen_share_config.scrolling_params->duration.ms(),
      pause_duration.ms());
}

}  // namespace webrtc_pc_e2e
}  // namespace webrtc
