/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/include/audio_processing_statistics.h"

namespace webrtc {

AudioProcessingStats::AudioProcessingStats() = default;

AudioProcessingStats::AudioProcessingStats(const AudioProcessingStats& other) =
    default;

AudioProcessingStats::~AudioProcessingStats() = default;

}  // namespace webrtc
