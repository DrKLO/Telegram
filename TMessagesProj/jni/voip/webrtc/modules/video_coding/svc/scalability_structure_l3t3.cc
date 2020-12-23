/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/video_coding/svc/scalability_structure_l3t3.h"

#include <vector>

#include "api/transport/rtp/dependency_descriptor.h"

namespace webrtc {

ScalabilityStructureL3T3::~ScalabilityStructureL3T3() = default;

FrameDependencyStructure ScalabilityStructureL3T3::DependencyStructure() const {
  FrameDependencyStructure structure;
  structure.num_decode_targets = 9;
  structure.num_chains = 3;
  structure.decode_target_protected_by_chain = {0, 0, 0, 1, 1, 1, 2, 2, 2};
  auto& t = structure.templates;
  t.resize(15);
  // Templates are shown in the order frames following them appear in the
  // stream, but in `structure.templates` array templates are sorted by
  // (`spatial_id`, `temporal_id`) since that is a dependency descriptor
  // requirement. Indexes are written in hex for nicer alignment.
  t[0x1].S(0).T(0).Dtis("SSSSSSSSS").ChainDiffs({0, 0, 0});
  t[0x6].S(1).T(0).Dtis("---SSSSSS").ChainDiffs({1, 1, 1}).FrameDiffs({1});
  t[0xB].S(2).T(0).Dtis("------SSS").ChainDiffs({2, 1, 1}).FrameDiffs({1});
  t[0x3].S(0).T(2).Dtis("--D--R--R").ChainDiffs({3, 2, 1}).FrameDiffs({3});
  t[0x8].S(1).T(2).Dtis("-----D--R").ChainDiffs({4, 3, 2}).FrameDiffs({3, 1});
  t[0xD].S(2).T(2).Dtis("--------D").ChainDiffs({5, 4, 3}).FrameDiffs({3, 1});
  t[0x2].S(0).T(1).Dtis("-DS-RR-RR").ChainDiffs({6, 5, 4}).FrameDiffs({6});
  t[0x7].S(1).T(1).Dtis("----DS-RR").ChainDiffs({7, 6, 5}).FrameDiffs({6, 1});
  t[0xC].S(2).T(1).Dtis("-------DS").ChainDiffs({8, 7, 6}).FrameDiffs({6, 1});
  t[0x4].S(0).T(2).Dtis("--D--R--R").ChainDiffs({9, 8, 7}).FrameDiffs({3});
  t[0x9].S(1).T(2).Dtis("-----D--R").ChainDiffs({10, 9, 8}).FrameDiffs({3, 1});
  t[0xE].S(2).T(2).Dtis("--------D").ChainDiffs({11, 10, 9}).FrameDiffs({3, 1});
  t[0x0].S(0).T(0).Dtis("SSSRRRRRR").ChainDiffs({12, 11, 10}).FrameDiffs({12});
  t[0x5].S(1).T(0).Dtis("---SSSRRR").ChainDiffs({1, 1, 1}).FrameDiffs({12, 1});
  t[0xA].S(2).T(0).Dtis("------SSS").ChainDiffs({2, 1, 1}).FrameDiffs({12, 1});
  return structure;
}

}  // namespace webrtc
