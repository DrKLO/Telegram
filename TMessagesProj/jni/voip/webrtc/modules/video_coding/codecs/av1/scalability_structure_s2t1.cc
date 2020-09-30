/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/video_coding/codecs/av1/scalability_structure_s2t1.h"

#include <utility>
#include <vector>

#include "absl/base/macros.h"
#include "api/transport/rtp/dependency_descriptor.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace {

constexpr auto kNotPresent = DecodeTargetIndication::kNotPresent;
constexpr auto kSwitch = DecodeTargetIndication::kSwitch;

constexpr DecodeTargetIndication kDtis[2][2] = {
    {kSwitch, kNotPresent},  // S0
    {kNotPresent, kSwitch},  // S1
};

}  // namespace

ScalabilityStructureS2T1::~ScalabilityStructureS2T1() = default;

ScalableVideoController::StreamLayersConfig
ScalabilityStructureS2T1::StreamConfig() const {
  StreamLayersConfig result;
  result.num_spatial_layers = 2;
  result.num_temporal_layers = 1;
  result.scaling_factor_num[0] = 1;
  result.scaling_factor_den[0] = 2;
  return result;
}

FrameDependencyStructure ScalabilityStructureS2T1::DependencyStructure() const {
  FrameDependencyStructure structure;
  structure.num_decode_targets = 2;
  structure.num_chains = 2;
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
  std::vector<LayerFrameConfig> result(2);
  // Buffer0 keeps latest S0T0 frame, Buffer1 keeps latest S1T0 frame.
  if (restart || keyframe_) {
    result[0].S(0).Keyframe().Update(0);
    result[1].S(1).Keyframe().Update(1);
    keyframe_ = false;
  } else {
    result[0].S(0).ReferenceAndUpdate(0);
    result[1].S(1).ReferenceAndUpdate(1);
  }
  return result;
}

absl::optional<GenericFrameInfo> ScalabilityStructureS2T1::OnEncodeDone(
    LayerFrameConfig config) {
  absl::optional<GenericFrameInfo> frame_info;
  if (config.SpatialId() < 0 ||
      config.SpatialId() >= int{ABSL_ARRAYSIZE(kDtis)}) {
    RTC_LOG(LS_ERROR) << "Unexpected spatial id " << config.SpatialId();
    return frame_info;
  }
  frame_info.emplace();
  frame_info->spatial_id = config.SpatialId();
  frame_info->temporal_id = config.TemporalId();
  frame_info->encoder_buffers = std::move(config.Buffers());
  frame_info->decode_target_indications.assign(
      std::begin(kDtis[config.SpatialId()]),
      std::end(kDtis[config.SpatialId()]));
  frame_info->part_of_chain = {config.SpatialId() == 0,
                               config.SpatialId() == 1};
  return frame_info;
}

}  // namespace webrtc
