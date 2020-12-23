/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef MODULES_VIDEO_CODING_SVC_SCALABILITY_STRUCTURE_L3T3_H_
#define MODULES_VIDEO_CODING_SVC_SCALABILITY_STRUCTURE_L3T3_H_

#include "api/transport/rtp/dependency_descriptor.h"
#include "modules/video_coding/svc/scalability_structure_full_svc.h"

namespace webrtc {

// https://aomediacodec.github.io/av1-rtp-spec/#a63-l3t3-full-svc
class ScalabilityStructureL3T3 : public ScalabilityStructureFullSvc {
 public:
  ScalabilityStructureL3T3() : ScalabilityStructureFullSvc(3, 3) {}
  ~ScalabilityStructureL3T3() override;

  FrameDependencyStructure DependencyStructure() const override;
};

}  // namespace webrtc

#endif  // MODULES_VIDEO_CODING_SVC_SCALABILITY_STRUCTURE_L3T3_H_
