/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Unit tests for BackgroundNoise class.

#include "modules/audio_coding/neteq/background_noise.h"

#include "test/gtest.h"

namespace webrtc {

TEST(BackgroundNoise, CreateAndDestroy) {
  size_t channels = 1;
  BackgroundNoise bgn(channels);
}

// TODO(hlundin): Write more tests.

}  // namespace webrtc
