/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef MODULES_VIDEO_CODING_SVC_SCALABILITY_STRUCTURE_L1T2_H_
#define MODULES_VIDEO_CODING_SVC_SCALABILITY_STRUCTURE_L1T2_H_

#include "api/transport/rtp/dependency_descriptor.h"
#include "modules/video_coding/svc/scalability_structure_full_svc.h"

namespace webrtc {

class ScalabilityStructureL1T2 : public ScalabilityStructureFullSvc {
 public:
  ScalabilityStructureL1T2() : ScalabilityStructureFullSvc(1, 2) {}
  ~ScalabilityStructureL1T2() override;

  FrameDependencyStructure DependencyStructure() const override;
};

}  // namespace webrtc

#endif  // MODULES_VIDEO_CODING_SVC_SCALABILITY_STRUCTURE_L1T2_H_
