/*
 *  Copyright 2022 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/test/peerconnection_quality_test_fixture.h"

#include <vector>

#include "absl/types/optional.h"
#include "api/test/pclf/media_configuration.h"
#include "api/test/video/video_frame_writer.h"
#include "rtc_base/gunit.h"
#include "test/gmock.h"
#include "test/testsupport/file_utils.h"

namespace webrtc {
namespace webrtc_pc_e2e {
namespace {

using ::testing::Eq;

TEST(PclfVideoSubscriptionTest,
     MaxFromSenderSpecEqualIndependentOfOtherFields) {
  VideoResolution r1(VideoResolution::Spec::kMaxFromSender);
  r1.set_width(1);
  r1.set_height(2);
  r1.set_fps(3);
  VideoResolution r2(VideoResolution::Spec::kMaxFromSender);
  r1.set_width(4);
  r1.set_height(5);
  r1.set_fps(6);
  EXPECT_EQ(r1, r2);
}

TEST(PclfVideoSubscriptionTest, WhenSpecIsNotSetFieldsAreCompared) {
  VideoResolution test_resolution(/*width=*/1, /*height=*/2,
                                  /*fps=*/3);
  VideoResolution equal_resolution(/*width=*/1, /*height=*/2,
                                   /*fps=*/3);
  VideoResolution different_width(/*width=*/10, /*height=*/2,
                                  /*fps=*/3);
  VideoResolution different_height(/*width=*/1, /*height=*/20,
                                   /*fps=*/3);
  VideoResolution different_fps(/*width=*/1, /*height=*/20,
                                /*fps=*/30);

  EXPECT_EQ(test_resolution, equal_resolution);
  EXPECT_NE(test_resolution, different_width);
  EXPECT_NE(test_resolution, different_height);
  EXPECT_NE(test_resolution, different_fps);
}

TEST(PclfVideoSubscriptionTest, GetMaxResolutionForEmptyReturnsNullopt) {
  absl::optional<VideoResolution> resolution =
      VideoSubscription::GetMaxResolution(std::vector<VideoConfig>{});
  ASSERT_FALSE(resolution.has_value());
}

TEST(PclfVideoSubscriptionTest, GetMaxResolutionSelectMaxForEachDimention) {
  VideoConfig max_width(/*width=*/1000, /*height=*/1, /*fps=*/1);
  VideoConfig max_height(/*width=*/1, /*height=*/100, /*fps=*/1);
  VideoConfig max_fps(/*width=*/1, /*height=*/1, /*fps=*/10);

  absl::optional<VideoResolution> resolution =
      VideoSubscription::GetMaxResolution(
          std::vector<VideoConfig>{max_width, max_height, max_fps});
  ASSERT_TRUE(resolution.has_value());
  EXPECT_EQ(resolution->width(), static_cast<size_t>(1000));
  EXPECT_EQ(resolution->height(), static_cast<size_t>(100));
  EXPECT_EQ(resolution->fps(), 10);
}

struct TestVideoFrameWriter : public test::VideoFrameWriter {
 public:
  TestVideoFrameWriter(absl::string_view file_name_prefix,
                       const VideoResolution& resolution)
      : file_name_prefix(file_name_prefix), resolution(resolution) {}

  bool WriteFrame(const VideoFrame& frame) override { return true; }

  void Close() override {}

  std::string file_name_prefix;
  VideoResolution resolution;
};

TEST(VideoDumpOptionsTest, InputVideoWriterHasCorrectFileName) {
  VideoResolution resolution(/*width=*/1280, /*height=*/720, /*fps=*/30);

  TestVideoFrameWriter* writer = nullptr;
  VideoDumpOptions options("foo", /*sampling_modulo=*/1,
                           /*export_frame_ids=*/false,
                           /*video_frame_writer_factory=*/
                           [&](absl::string_view file_name_prefix,
                               const VideoResolution& resolution) {
                             auto out = std::make_unique<TestVideoFrameWriter>(
                                 file_name_prefix, resolution);
                             writer = out.get();
                             return out;
                           });
  std::unique_ptr<test::VideoFrameWriter> created_writer =
      options.CreateInputDumpVideoFrameWriter("alice-video", resolution);

  ASSERT_TRUE(writer != nullptr);
  ASSERT_THAT(writer->file_name_prefix,
              Eq(test::JoinFilename("foo", "alice-video_1280x720_30")));
  ASSERT_THAT(writer->resolution, Eq(resolution));
}

TEST(VideoDumpOptionsTest, OutputVideoWriterHasCorrectFileName) {
  VideoResolution resolution(/*width=*/1280, /*height=*/720, /*fps=*/30);

  TestVideoFrameWriter* writer = nullptr;
  VideoDumpOptions options("foo", /*sampling_modulo=*/1,
                           /*export_frame_ids=*/false,
                           /*video_frame_writer_factory=*/
                           [&](absl::string_view file_name_prefix,
                               const VideoResolution& resolution) {
                             auto out = std::make_unique<TestVideoFrameWriter>(
                                 file_name_prefix, resolution);
                             writer = out.get();
                             return out;
                           });
  std::unique_ptr<test::VideoFrameWriter> created_writer =
      options.CreateOutputDumpVideoFrameWriter("alice-video", "bob",
                                               resolution);

  ASSERT_TRUE(writer != nullptr);
  ASSERT_THAT(writer->file_name_prefix,
              Eq(test::JoinFilename("foo", "alice-video_bob_1280x720_30")));
  ASSERT_THAT(writer->resolution, Eq(resolution));
}

}  // namespace
}  // namespace webrtc_pc_e2e
}  // namespace webrtc
