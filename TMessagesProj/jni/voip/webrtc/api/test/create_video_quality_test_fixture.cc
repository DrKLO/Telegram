/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/test/create_video_quality_test_fixture.h"

#include <memory>
#include <utility>

#include "video/video_quality_test.h"

namespace webrtc {

std::unique_ptr<VideoQualityTestFixtureInterface>
CreateVideoQualityTestFixture() {
  // By default, we don't override the FEC module, so pass an empty factory.
  return std::make_unique<VideoQualityTest>(nullptr);
}

std::unique_ptr<VideoQualityTestFixtureInterface> CreateVideoQualityTestFixture(
    std::unique_ptr<FecControllerFactoryInterface> fec_controller_factory) {
  auto components =
      std::make_unique<VideoQualityTestFixtureInterface::InjectionComponents>();
  components->fec_controller_factory = std::move(fec_controller_factory);
  return std::make_unique<VideoQualityTest>(std::move(components));
}

std::unique_ptr<VideoQualityTestFixtureInterface> CreateVideoQualityTestFixture(
    std::unique_ptr<VideoQualityTestFixtureInterface::InjectionComponents>
        components) {
  return std::make_unique<VideoQualityTest>(std::move(components));
}

}  // namespace webrtc
