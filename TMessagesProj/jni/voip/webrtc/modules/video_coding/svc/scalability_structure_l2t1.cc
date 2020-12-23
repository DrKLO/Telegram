/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/video_coding/svc/scalability_structure_l2t1.h"

#include <vector>

#include "api/transport/rtp/dependency_descriptor.h"

namespace webrtc {

ScalabilityStructureL2T1::~ScalabilityStructureL2T1() = default;

FrameDependencyStructure ScalabilityStructureL2T1::DependencyStructure() const {
  FrameDependencyStructure structure;
  structure.num_decode_targets = 2;
  structure.num_chains = 2;
  structure.decode_target_protected_by_chain = {0, 1};
  structure.templates.resize(4);
  structure.templates[0].S(0).Dtis("SR").ChainDiffs({2, 1}).FrameDiffs({2});
  structure.templates[1].S(0).Dtis("SS").ChainDiffs({0, 0});
  structure.templates[2].S(1).Dtis("-S").ChainDiffs({1, 1}).FrameDiffs({2, 1});
  structure.templates[3].S(1).Dtis("-S").ChainDiffs({1, 1}).FrameDiffs({1});
  return structure;
}

}  // namespace webrtc
