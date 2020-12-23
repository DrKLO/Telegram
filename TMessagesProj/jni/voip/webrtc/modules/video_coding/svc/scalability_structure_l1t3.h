/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef MODULES_VIDEO_CODING_SVC_SCALABILITY_STRUCTURE_L1T3_H_
#define MODULES_VIDEO_CODING_SVC_SCALABILITY_STRUCTURE_L1T3_H_

#include "api/transport/rtp/dependency_descriptor.h"
#include "modules/video_coding/svc/scalability_structure_full_svc.h"

namespace webrtc {

// T2       0   0   0   0
//          |  /    |  /
// T1       / 0     / 0  ...
//         |_/     |_/
// T0     0-------0------
// Time-> 0 1 2 3 4 5 6 7
class ScalabilityStructureL1T3 : public ScalabilityStructureFullSvc {
 public:
  ScalabilityStructureL1T3() : ScalabilityStructureFullSvc(1, 3) {}
  ~ScalabilityStructureL1T3() override;

  FrameDependencyStructure DependencyStructure() const override;
};

}  // namespace webrtc

#endif  // MODULES_VIDEO_CODING_SVC_SCALABILITY_STRUCTURE_L1T3_H_
