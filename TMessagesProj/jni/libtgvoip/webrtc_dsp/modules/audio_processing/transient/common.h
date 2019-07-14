/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_TRANSIENT_COMMON_H_
#define MODULES_AUDIO_PROCESSING_TRANSIENT_COMMON_H_
namespace webrtc {
namespace ts {

static const float kPi = 3.14159265358979323846f;
static const int kChunkSizeMs = 10;
enum {
  kSampleRate8kHz = 8000,
  kSampleRate16kHz = 16000,
  kSampleRate32kHz = 32000,
  kSampleRate48kHz = 48000
};

}  // namespace ts
}  // namespace webrtc
#endif  // MODULES_AUDIO_PROCESSING_TRANSIENT_COMMON_H_
