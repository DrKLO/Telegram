/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_CODING_CODECS_ISAC_FIX_INCLUDE_AUDIO_DECODER_ISACFIX_H_
#define MODULES_AUDIO_CODING_CODECS_ISAC_FIX_INCLUDE_AUDIO_DECODER_ISACFIX_H_

#include "modules/audio_coding/codecs/isac/audio_decoder_isac_t.h"
#include "modules/audio_coding/codecs/isac/fix/source/isac_fix_type.h"

namespace webrtc {

using AudioDecoderIsacFixImpl = AudioDecoderIsacT<IsacFix>;

}  // namespace webrtc
#endif  // MODULES_AUDIO_CODING_CODECS_ISAC_FIX_INCLUDE_AUDIO_DECODER_ISACFIX_H_
