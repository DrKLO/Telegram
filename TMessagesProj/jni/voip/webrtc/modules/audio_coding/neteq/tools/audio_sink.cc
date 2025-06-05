/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/tools/audio_sink.h"

namespace webrtc {
namespace test {

bool AudioSinkFork::WriteArray(const int16_t* audio, size_t num_samples) {
  return left_sink_->WriteArray(audio, num_samples) &&
         right_sink_->WriteArray(audio, num_samples);
}

bool VoidAudioSink::WriteArray(const int16_t* audio, size_t num_samples) {
  return true;
}

}  // namespace test
}  // namespace webrtc
