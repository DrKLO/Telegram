/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/video_codecs/builtin_video_encoder_factory.h"

#include <memory>
#include <string>
#include <vector>

#include "api/video_codecs/sdp_video_format.h"
#include "test/gtest.h"

namespace webrtc {

TEST(BuiltinVideoEncoderFactoryTest, AnnouncesVp9AccordingToBuildFlags) {
  std::unique_ptr<VideoEncoderFactory> factory =
      CreateBuiltinVideoEncoderFactory();
  bool claims_vp9_support = false;
  for (const SdpVideoFormat& format : factory->GetSupportedFormats()) {
    if (format.name == "VP9") {
      claims_vp9_support = true;
      break;
    }
  }
#if defined(RTC_ENABLE_VP9)
  EXPECT_TRUE(claims_vp9_support);
#else
  EXPECT_FALSE(claims_vp9_support);
#endif  // defined(RTC_ENABLE_VP9)
}

}  // namespace webrtc
