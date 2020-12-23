/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/video_coding/svc/scalability_structure_s2t1.h"

#include <utility>
#include <vector>

#include "absl/base/macros.h"
#include "api/transport/rtp/dependency_descriptor.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {

constexpr int ScalabilityStructureS2T1::kNumSpatialLayers;

ScalabilityStructureS2T1::~ScalabilityStructureS2T1() = default;

ScalableVideoController::StreamLayersConfig
ScalabilityStructureS2T1::StreamConfig() const {
  StreamLayersConfig result;
  result.num_spatial_layers = kNumSpatialLayers;
  result.num_temporal_layers = 1;
  result.scaling_factor_num[0] = 1;
  result.scaling_factor_den[0] = 2;
  return result;
}

FrameDependencyStructure ScalabilityStructureS2T1::DependencyStructure() const {
  FrameDependencyStructure structure;
  structure.num_decode_targets = kNumSpatialLayers;
  structure.num_chains = kNumSpatialLayers;
  structure.decode_target_protected_by_chain = {0, 1};
  structure.templates.resize(4);
  structure.templates[0].S(0).Dtis("S-").ChainDiffs({2, 1}).FrameDiffs({2});
  structure.templates[1].S(0).Dtis("S-").ChainDiffs({0, 0});
  structure.templates[2].S(1).Dtis("-S").ChainDiffs({1, 2}).FrameDiffs({2});
  structure.templates[3].S(1).Dtis("-S").ChainDiffs({1, 0});
  return structure;
}

std::vector<ScalableVideoController::LayerFrameConfig>
ScalabilityStructureS2T1::NextFrameConfig(bool restart) {
  if (restart) {
    can_reference_frame_for_spatial_id_.reset();
  }
  std::vector<LayerFrameConfig> configs;
  configs.reserve(kNumSpatialLayers);
  for (int sid = 0; sid < kNumSpatialLayers; ++sid) {
    if (!active_decode_targets_[sid]) {
      can_reference_frame_for_spatial_id_.reset(sid);
      continue;
    }
    configs.emplace_back();
    LayerFrameConfig& config = configs.back().S(sid);
    if (can_reference_frame_for_spatial_id_[sid]) {
      config.ReferenceAndUpdate(sid);
    } else {
      config.Keyframe().Update(sid);
      can_reference_frame_for_spatial_id_.set(sid);
    }
  }

  return configs;
}

GenericFrameInfo ScalabilityStructureS2T1::OnEncodeDone(
    const LayerFrameConfig& config) {
  GenericFrameInfo frame_info;
  frame_info.spatial_id = config.SpatialId();
  frame_info.temporal_id = config.TemporalId();
  frame_info.encoder_buffers = config.Buffers();
  frame_info.decode_target_indications = {
      config.SpatialId() == 0 ? DecodeTargetIndication::kSwitch
                              : DecodeTargetIndication::kNotPresent,
      config.SpatialId() == 1 ? DecodeTargetIndication::kSwitch
                              : DecodeTargetIndication::kNotPresent,
  };
  frame_info.part_of_chain = {config.SpatialId() == 0, config.SpatialId() == 1};
  frame_info.active_decode_targets = active_decode_targets_;
  return frame_info;
}

void ScalabilityStructureS2T1::OnRatesUpdated(
    const VideoBitrateAllocation& bitrates) {
  active_decode_targets_.set(0, bitrates.GetBitrate(/*sid=*/0, /*tid=*/0) > 0);
  active_decode_targets_.set(1, bitrates.GetBitrate(/*sid=*/1, /*tid=*/0) > 0);
}

}  // namespace webrtc
