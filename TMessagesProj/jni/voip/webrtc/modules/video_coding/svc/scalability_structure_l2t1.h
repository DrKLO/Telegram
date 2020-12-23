/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef MODULES_VIDEO_CODING_SVC_SCALABILITY_STRUCTURE_L2T1_H_
#define MODULES_VIDEO_CODING_SVC_SCALABILITY_STRUCTURE_L2T1_H_

#include "api/transport/rtp/dependency_descriptor.h"
#include "modules/video_coding/svc/scalability_structure_full_svc.h"

namespace webrtc {

// S1  0--0--0-
//     |  |  | ...
// S0  0--0--0-
class ScalabilityStructureL2T1 : public ScalabilityStructureFullSvc {
 public:
  ScalabilityStructureL2T1() : ScalabilityStructureFullSvc(2, 1) {}
  ~ScalabilityStructureL2T1() override;

  FrameDependencyStructure DependencyStructure() const override;
};

}  // namespace webrtc

#endif  // MODULES_VIDEO_CODING_SVC_SCALABILITY_STRUCTURE_L2T1_H_
