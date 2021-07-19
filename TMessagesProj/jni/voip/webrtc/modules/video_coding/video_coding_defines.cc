/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/include/video_coding_defines.h"

namespace webrtc {

void VCMReceiveCallback::OnDroppedFrames(uint32_t frames_dropped) {}
void VCMReceiveCallback::OnIncomingPayloadType(int payload_type) {}
void VCMReceiveCallback::OnDecoderImplementationName(
    const char* implementation_name) {}

}  // namespace webrtc
