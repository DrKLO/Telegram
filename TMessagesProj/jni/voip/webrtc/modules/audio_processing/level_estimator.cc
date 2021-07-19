/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/level_estimator.h"

#include "api/array_view.h"

namespace webrtc {

LevelEstimator::LevelEstimator() {
  rms_.Reset();
}

LevelEstimator::~LevelEstimator() = default;

void LevelEstimator::ProcessStream(const AudioBuffer& audio) {
  for (size_t i = 0; i < audio.num_channels(); i++) {
    rms_.Analyze(rtc::ArrayView<const float>(audio.channels_const()[i],
                                             audio.num_frames()));
  }
}
}  // namespace webrtc
