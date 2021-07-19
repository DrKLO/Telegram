/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_AUDIO_CODECS_ISAC_AUDIO_ENCODER_ISAC_H_
#define API_AUDIO_CODECS_ISAC_AUDIO_ENCODER_ISAC_H_

#if WEBRTC_USE_BUILTIN_ISAC_FIX && !WEBRTC_USE_BUILTIN_ISAC_FLOAT
#include "api/audio_codecs/isac/audio_encoder_isac_fix.h"  // nogncheck
#elif WEBRTC_USE_BUILTIN_ISAC_FLOAT && !WEBRTC_USE_BUILTIN_ISAC_FIX
#include "api/audio_codecs/isac/audio_encoder_isac_float.h"  // nogncheck
#else
#error "Must choose either fix or float"
#endif

namespace webrtc {

#if WEBRTC_USE_BUILTIN_ISAC_FIX
using AudioEncoderIsac = AudioEncoderIsacFix;
#elif WEBRTC_USE_BUILTIN_ISAC_FLOAT
using AudioEncoderIsac = AudioEncoderIsacFloat;
#endif

}  // namespace webrtc

#endif  // API_AUDIO_CODECS_ISAC_AUDIO_ENCODER_ISAC_H_
