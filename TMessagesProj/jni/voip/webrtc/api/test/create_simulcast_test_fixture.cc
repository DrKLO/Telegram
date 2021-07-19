/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/test/create_simulcast_test_fixture.h"

#include <memory>
#include <utility>

#include "api/test/simulcast_test_fixture.h"
#include "modules/video_coding/utility/simulcast_test_fixture_impl.h"

namespace webrtc {
namespace test {

std::unique_ptr<SimulcastTestFixture> CreateSimulcastTestFixture(
    std::unique_ptr<VideoEncoderFactory> encoder_factory,
    std::unique_ptr<VideoDecoderFactory> decoder_factory,
    SdpVideoFormat video_format) {
  return std::make_unique<SimulcastTestFixtureImpl>(
      std::move(encoder_factory), std::move(decoder_factory), video_format);
}

}  // namespace test
}  // namespace webrtc
