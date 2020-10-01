/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/video_coding/codecs/av1/scalability_structure_l3t1.h"

#include <utility>
#include <vector>

#include "absl/base/macros.h"
#include "absl/types/optional.h"
#include "api/transport/rtp/dependency_descriptor.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace {

constexpr auto kNotPresent = DecodeTargetIndication::kNotPresent;
constexpr auto kSwitch = DecodeTargetIndication::kSwitch;
constexpr auto kRequired = DecodeTargetIndication::kRequired;

constexpr DecodeTargetIndication kDtis[5][3] = {
    {kSwitch, kSwitch, kSwitch},          // Key, S0
    {kNotPresent, kSwitch, kSwitch},      // Key, S1
    {kNotPresent, kNotPresent, kSwitch},  // Key and Delta, S2
    {kSwitch, kRequired, kRequired},      // Delta, S0
    {kNotPresent, kSwitch, kRequired},    // Delta, S1
};

}  // namespace

ScalabilityStructureL3T1::~ScalabilityStructureL3T1() = default;

ScalableVideoController::StreamLayersConfig
ScalabilityStructureL3T1::StreamConfig() const {
  StreamLayersConfig result;
  result.num_spatial_layers = 3;
  result.num_temporal_layers = 1;
  result.scaling_factor_num[0] = 1;
  result.scaling_factor_den[0] = 4;
  result.scaling_factor_num[1] = 1;
  result.scaling_factor_den[1] = 2;
  return result;
}

FrameDependencyStructure ScalabilityStructureL3T1::DependencyStructure() const {
  FrameDependencyStructure structure;
  structure.num_decode_targets = 3;
  structure.num_chains = 3;
  structure.decode_target_protected_by_chain = {0, 1, 2};
  auto& templates = structure.templates;
  templates.resize(6);
  templates[0].S(0).Dtis("SRR").ChainDiffs({3, 2, 1}).FrameDiffs({3});
  templates[1].S(0).Dtis("SSS").ChainDiffs({0, 0, 0});
  templates[2].S(1).Dtis("-SR").ChainDiffs({1, 1, 1}).FrameDiffs({3, 1});
  templates[3].S(1).Dtis("-SS").ChainDiffs({1, 1, 1}).FrameDiffs({1});
  templates[4].S(2).Dtis("--S").ChainDiffs({2, 1, 1}).FrameDiffs({3, 1});
  templates[5].S(2).Dtis("--S").ChainDiffs({2, 1, 1}).FrameDiffs({1});
  return structure;
}

std::vector<ScalableVideoController::LayerFrameConfig>
ScalabilityStructureL3T1::NextFrameConfig(bool restart) {
  std::vector<LayerFrameConfig> config(3);

  // Buffer i keeps latest frame for spatial layer i
  if (restart || keyframe_) {
    config[0].Id(0).S(0).Keyframe().Update(0);
    config[1].Id(1).S(1).Update(1).Reference(0);
    config[2].Id(2).S(2).Update(2).Reference(1);
    keyframe_ = false;
  } else {
    config[0].Id(3).S(0).ReferenceAndUpdate(0);
    config[1].Id(4).S(1).ReferenceAndUpdate(1).Reference(0);
    config[2].Id(2).S(2).ReferenceAndUpdate(2).Reference(1);
  }
  return config;
}

absl::optional<GenericFrameInfo> ScalabilityStructureL3T1::OnEncodeDone(
    LayerFrameConfig config) {
  absl::optional<GenericFrameInfo> frame_info;
  if (config.IsKeyframe() && config.Id() != 0) {
    // Encoder generated a key frame without asking to.
    if (config.SpatialId() > 0) {
      RTC_LOG(LS_WARNING) << "Unexpected spatial id " << config.SpatialId()
                          << " for key frame.";
    }
    config = LayerFrameConfig().Id(0).S(0).Keyframe().Update(0);
  }

  if (config.Id() < 0 || config.Id() >= int{ABSL_ARRAYSIZE(kDtis)}) {
    RTC_LOG(LS_ERROR) << "Unexpected config id " << config.Id();
    return frame_info;
  }
  frame_info.emplace();
  frame_info->spatial_id = config.SpatialId();
  frame_info->temporal_id = config.TemporalId();
  frame_info->encoder_buffers = config.Buffers();
  frame_info->decode_target_indications.assign(std::begin(kDtis[config.Id()]),
                                               std::end(kDtis[config.Id()]));
  frame_info->part_of_chain = {config.SpatialId() == 0, config.SpatialId() <= 1,
                               true};
  return frame_info;
}

}  // namespace webrtc
