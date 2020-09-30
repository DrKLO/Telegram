/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/video_coding/codecs/av1/scalability_structure_l2t1.h"

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
constexpr auto kRequired = DecodeTargetIndication::kRequired;

constexpr DecodeTargetIndication kDtis[4][2] = {
    {kSwitch, kSwitch},        // Key, S0
    {kNotPresent, kSwitch},    // Key, S1
    {kSwitch, kRequired},      // Delta, S0
    {kNotPresent, kRequired},  // Delta, S1
};

}  // namespace

ScalabilityStructureL2T1::~ScalabilityStructureL2T1() = default;

ScalableVideoController::StreamLayersConfig
ScalabilityStructureL2T1::StreamConfig() const {
  StreamLayersConfig result;
  result.num_spatial_layers = 2;
  result.num_temporal_layers = 1;
  result.scaling_factor_num[0] = 1;
  result.scaling_factor_den[0] = 2;
  return result;
}

FrameDependencyStructure ScalabilityStructureL2T1::DependencyStructure() const {
  FrameDependencyStructure structure;
  structure.num_decode_targets = 2;
  structure.num_chains = 2;
  structure.decode_target_protected_by_chain = {0, 1};
  structure.templates.resize(4);
  structure.templates[0].S(0).Dtis("SR").ChainDiffs({2, 1}).FrameDiffs({2});
  structure.templates[1].S(0).Dtis("SS").ChainDiffs({0, 0});
  structure.templates[2].S(1).Dtis("-R").ChainDiffs({1, 1}).FrameDiffs({2, 1});
  structure.templates[3].S(1).Dtis("-S").ChainDiffs({1, 1}).FrameDiffs({1});
  return structure;
}

ScalableVideoController::LayerFrameConfig
ScalabilityStructureL2T1::KeyFrameConfig() const {
  return LayerFrameConfig().Id(0).S(0).Keyframe().Update(0);
}

std::vector<ScalableVideoController::LayerFrameConfig>
ScalabilityStructureL2T1::NextFrameConfig(bool restart) {
  std::vector<LayerFrameConfig> result(2);
  // Buffer0 keeps latest S0 frame, Buffer1 keeps latest S1 frame.
  if (restart || keyframe_) {
    result[0] = KeyFrameConfig();
    result[1].Id(1).S(1).Reference(0).Update(1);
    keyframe_ = false;
  } else {
    result[0].Id(2).S(0).ReferenceAndUpdate(0);
    result[1].Id(3).S(1).Reference(0).ReferenceAndUpdate(1);
  }
  return result;
}

absl::optional<GenericFrameInfo> ScalabilityStructureL2T1::OnEncodeDone(
    LayerFrameConfig config) {
  absl::optional<GenericFrameInfo> frame_info;
  if (config.IsKeyframe()) {
    config = KeyFrameConfig();
  }

  if (config.Id() < 0 || config.Id() >= int{ABSL_ARRAYSIZE(kDtis)}) {
    RTC_LOG(LS_ERROR) << "Unexpected config id " << config.Id();
    return frame_info;
  }
  frame_info.emplace();
  frame_info->spatial_id = config.SpatialId();
  frame_info->temporal_id = config.TemporalId();
  frame_info->encoder_buffers = std::move(config.Buffers());
  frame_info->decode_target_indications.assign(std::begin(kDtis[config.Id()]),
                                               std::end(kDtis[config.Id()]));
  frame_info->part_of_chain = {config.SpatialId() == 0, true};
  return frame_info;
}

}  // namespace webrtc
