/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/video_coding/svc/scalability_structure_l1t2.h"

#include <vector>

#include "api/transport/rtp/dependency_descriptor.h"

namespace webrtc {

ScalabilityStructureL1T2::~ScalabilityStructureL1T2() = default;

FrameDependencyStructure ScalabilityStructureL1T2::DependencyStructure() const {
  FrameDependencyStructure structure;
  structure.num_decode_targets = 2;
  structure.num_chains = 1;
  structure.decode_target_protected_by_chain = {0, 0};
  structure.templates.resize(3);
  structure.templates[0].T(0).Dtis("SS").ChainDiffs({0});
  structure.templates[1].T(0).Dtis("SS").ChainDiffs({2}).FrameDiffs({2});
  structure.templates[2].T(1).Dtis("-D").ChainDiffs({1}).FrameDiffs({1});
  return structure;
}

}  // namespace webrtc
