/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef MODULES_VIDEO_CODING_CODECS_AV1_SCALABILITY_STRUCTURE_L2T1H_H_
#define MODULES_VIDEO_CODING_CODECS_AV1_SCALABILITY_STRUCTURE_L2T1H_H_

#include "modules/video_coding/codecs/av1/scalability_structure_l2t1.h"
#include "modules/video_coding/codecs/av1/scalable_video_controller.h"

namespace webrtc {

class ScalabilityStructureL2T1h : public ScalabilityStructureL2T1 {
 public:
  ~ScalabilityStructureL2T1h() override;

  StreamLayersConfig StreamConfig() const override;
};

}  // namespace webrtc

#endif  // MODULES_VIDEO_CODING_CODECS_AV1_SCALABILITY_STRUCTURE_L2T1H_H_
