/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/video_coding/codecs/av1/libaom_av1_decoder.h"

#include <memory>

#include "api/video_codecs/video_decoder.h"

namespace webrtc {

const bool kIsLibaomAv1DecoderSupported = false;

std::unique_ptr<VideoDecoder> CreateLibaomAv1Decoder() {
  return nullptr;
}

}  // namespace webrtc
