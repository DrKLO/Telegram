/* Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_VIDEO_CODING_CODECS_AV1_AV1_SVC_CONFIG_H_
#define MODULES_VIDEO_CODING_CODECS_AV1_AV1_SVC_CONFIG_H_

#include "api/video_codecs/video_codec.h"

namespace webrtc {

// Fills `video_codec.spatialLayers` using other members.
bool SetAv1SvcConfig(VideoCodec& video_codec);

}  // namespace webrtc

#endif  // MODULES_VIDEO_CODING_CODECS_AV1_AV1_SVC_CONFIG_H_
