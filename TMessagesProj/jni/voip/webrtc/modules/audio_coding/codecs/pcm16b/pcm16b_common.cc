/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/codecs/pcm16b/pcm16b_common.h"

#include <stdint.h>

#include <initializer_list>

namespace webrtc {

void Pcm16BAppendSupportedCodecSpecs(std::vector<AudioCodecSpec>* specs) {
  for (uint8_t num_channels : {1, 2}) {
    for (int sample_rate_hz : {8000, 16000, 32000}) {
      specs->push_back(
          {{"L16", sample_rate_hz, num_channels},
           {sample_rate_hz, num_channels, sample_rate_hz * num_channels * 16}});
    }
  }
}

}  // namespace webrtc
