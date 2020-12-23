/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/video_coding/svc/scalability_structure_l1t3.h"

#include <vector>

#include "api/transport/rtp/dependency_descriptor.h"

namespace webrtc {

ScalabilityStructureL1T3::~ScalabilityStructureL1T3() = default;

FrameDependencyStructure ScalabilityStructureL1T3::DependencyStructure() const {
  FrameDependencyStructure structure;
  structure.num_decode_targets = 3;
  structure.num_chains = 1;
  structure.decode_target_protected_by_chain = {0, 0, 0};
  structure.templates.resize(5);
  structure.templates[0].T(0).Dtis("SSS").ChainDiffs({0});
  structure.templates[1].T(0).Dtis("SSS").ChainDiffs({4}).FrameDiffs({4});
  structure.templates[2].T(1).Dtis("-DS").ChainDiffs({2}).FrameDiffs({2});
  structure.templates[3].T(2).Dtis("--D").ChainDiffs({1}).FrameDiffs({1});
  structure.templates[4].T(2).Dtis("--D").ChainDiffs({3}).FrameDiffs({1});
  return structure;
}

}  // namespace webrtc
