/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/test/create_frame_generator.h"

#include <cstdio>
#include <utility>

#include "rtc_base/checks.h"
#include "test/frame_generator.h"
#include "test/testsupport/ivf_video_frame_generator.h"

namespace webrtc {
namespace test {

std::unique_ptr<FrameGeneratorInterface> CreateSquareFrameGenerator(
    int width,
    int height,
    absl::optional<FrameGeneratorInterface::OutputType> type,
    absl::optional<int> num_squares) {
  return std::make_unique<SquareGenerator>(
      width, height, type.value_or(FrameGeneratorInterface::OutputType::kI420),
      num_squares.value_or(10));
}

std::unique_ptr<FrameGeneratorInterface> CreateFromYuvFileFrameGenerator(
    std::vector<std::string> filenames,
    size_t width,
    size_t height,
    int frame_repeat_count) {
  RTC_DCHECK(!filenames.empty());
  std::vector<FILE*> files;
  for (const std::string& filename : filenames) {
    FILE* file = fopen(filename.c_str(), "rb");
    RTC_DCHECK(file != nullptr) << "Failed to open: '" << filename << "'\n";
    files.push_back(file);
  }

  return std::make_unique<YuvFileGenerator>(files, width, height,
                                            frame_repeat_count);
}

std::unique_ptr<FrameGeneratorInterface> CreateFromIvfFileFrameGenerator(
    std::string filename) {
  return std::make_unique<IvfVideoFrameGenerator>(std::move(filename));
}

std::unique_ptr<FrameGeneratorInterface>
CreateScrollingInputFromYuvFilesFrameGenerator(
    Clock* clock,
    std::vector<std::string> filenames,
    size_t source_width,
    size_t source_height,
    size_t target_width,
    size_t target_height,
    int64_t scroll_time_ms,
    int64_t pause_time_ms) {
  RTC_DCHECK(!filenames.empty());
  std::vector<FILE*> files;
  for (const std::string& filename : filenames) {
    FILE* file = fopen(filename.c_str(), "rb");
    RTC_DCHECK(file != nullptr);
    files.push_back(file);
  }

  return std::make_unique<ScrollingImageFrameGenerator>(
      clock, files, source_width, source_height, target_width, target_height,
      scroll_time_ms, pause_time_ms);
}

std::unique_ptr<FrameGeneratorInterface>
CreateSlideFrameGenerator(int width, int height, int frame_repeat_count) {
  return std::make_unique<SlideGenerator>(width, height, frame_repeat_count);
}

}  // namespace test
}  // namespace webrtc
