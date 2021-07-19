/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/video_coding/include/video_codec_interface.h"

namespace webrtc {
CodecSpecificInfo::CodecSpecificInfo() : codecType(kVideoCodecGeneric) {
  memset(&codecSpecific, 0, sizeof(codecSpecific));
}

CodecSpecificInfo::CodecSpecificInfo(const CodecSpecificInfo&) = default;
CodecSpecificInfo::~CodecSpecificInfo() = default;

}  // namespace webrtc
