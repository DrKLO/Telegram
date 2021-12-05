/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_AUDIO_CODECS_ILBC_AUDIO_ENCODER_ILBC_CONFIG_H_
#define API_AUDIO_CODECS_ILBC_AUDIO_ENCODER_ILBC_CONFIG_H_

namespace webrtc {

struct AudioEncoderIlbcConfig {
  bool IsOk() const {
    return (frame_size_ms == 20 || frame_size_ms == 30 || frame_size_ms == 40 ||
            frame_size_ms == 60);
  }
  int frame_size_ms = 30;  // Valid values are 20, 30, 40, and 60 ms.
  // Note that frame size 40 ms produces encodings with two 20 ms frames in
  // them, and frame size 60 ms consists of two 30 ms frames.
};

}  // namespace webrtc

#endif  // API_AUDIO_CODECS_ILBC_AUDIO_ENCODER_ILBC_CONFIG_H_
