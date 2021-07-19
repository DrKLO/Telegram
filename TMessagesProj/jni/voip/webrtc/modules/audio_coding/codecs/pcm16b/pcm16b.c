/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/codecs/pcm16b/pcm16b.h"

size_t WebRtcPcm16b_Encode(const int16_t* speech,
                           size_t len,
                           uint8_t* encoded) {
  size_t i;
  for (i = 0; i < len; ++i) {
    uint16_t s = speech[i];
    encoded[2 * i] = s >> 8;
    encoded[2 * i + 1] = s;
  }
  return 2 * len;
}

size_t WebRtcPcm16b_Decode(const uint8_t* encoded,
                           size_t len,
                           int16_t* speech) {
  size_t i;
  for (i = 0; i < len / 2; ++i)
    speech[i] = encoded[2 * i] << 8 | encoded[2 * i + 1];
  return len / 2;
}
