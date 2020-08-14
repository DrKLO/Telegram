/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/video_coding/codecs/av1/scalability_structure_l2t2_key_shift.h"

#include <utility>
#include <vector>

#include "absl/base/macros.h"
#include "api/transport/rtp/dependency_descriptor.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace {

constexpr auto kNotPresent = DecodeTargetIndication::kNotPresent;
constexpr auto kDiscardable = DecodeTargetIndication::kDiscardable;
constexpr auto kSwitch = DecodeTargetIndication::kSwitch;

constexpr DecodeTargetIndication kDtis[6][4] = {
    {kSwitch, kSwitch, kSwitch, kSwitch},                   //    kKey, S0T0
    {kNotPresent, kNotPresent, kSwitch, kSwitch},           //    kKey, S1T0
    {kSwitch, kSwitch, kNotPresent, kNotPresent},           //    kDelta0, S0T0
    {kNotPresent, kNotPresent, kNotPresent, kDiscardable},  //    kDelta0, S1T1
    {kNotPresent, kDiscardable, kNotPresent, kNotPresent},  //    kDelta1, S0T1
    {kNotPresent, kNotPresent, kSwitch, kSwitch},           //    kDelta1, S1T0
};

}  // namespace

ScalabilityStructureL2T2KeyShift::~ScalabilityStructureL2T2KeyShift() = default;

ScalableVideoController::StreamLayersConfig
ScalabilityStructureL2T2KeyShift::StreamConfig() const {
  StreamLayersConfig result;
  result.num_spatial_layers = 2;
  result.num_temporal_layers = 2;
  result.scaling_factor_num[0] = 1;
  result.scaling_factor_den[0] = 2;
  return result;
}

FrameDependencyStructure ScalabilityStructureL2T2KeyShift::DependencyStructure()
    const {
  FrameDependencyStructure structure;
  structure.num_decode_targets = 4;
  structure.num_chains = 2;
  structure.decode_target_protected_by_chain = {0, 0, 1, 1};
  structure.templates.resize(7);
  auto& templates = structure.templates;
  templates[0].S(0).T(0).Dtis("SSSS").ChainDiffs({0, 0});
  templates[1].S(0).T(0).Dtis("SS--").ChainDiffs({2, 1}).FrameDiffs({2});
  templates[2].S(0).T(0).Dtis("SS--").ChainDiffs({4, 1}).FrameDiffs({4});
  templates[3].S(0).T(1).Dtis("-D--").ChainDiffs({2, 3}).FrameDiffs({2});
  templates[4].S(1).T(0).Dtis("--SS").ChainDiffs({1, 1}).FrameDiffs({1});
  templates[5].S(1).T(0).Dtis("--SS").ChainDiffs({3, 4}).FrameDiffs({4});
  templates[6].S(1).T(1).Dtis("---D").ChainDiffs({1, 2}).FrameDiffs({2});
  return structure;
}

ScalableVideoController::LayerFrameConfig
ScalabilityStructureL2T2KeyShift::KeyFrameConfig() const {
  return LayerFrameConfig().Id(0).Keyframe().S(0).T(0).Update(0);
}

std::vector<ScalableVideoController::LayerFrameConfig>
ScalabilityStructureL2T2KeyShift::NextFrameConfig(bool restart) {
  if (restart) {
    next_pattern_ = kKey;
  }
  std::vector<LayerFrameConfig> result(2);

  // Buffer0 keeps latest S0T0 frame,
  // Buffer1 keeps latest S1T0 frame.
  switch (next_pattern_) {
    case kKey:
      result[0] = KeyFrameConfig();
      result[1].Id(1).S(1).T(0).Reference(0).Update(1);
      next_pattern_ = kDelta0;
      break;
    case kDelta0:
      result[0].Id(2).S(0).T(0).ReferenceAndUpdate(0);
      result[1].Id(3).S(1).T(1).Reference(1);
      next_pattern_ = kDelta1;
      break;
    case kDelta1:
      result[0].Id(4).S(0).T(1).Reference(0);
      result[1].Id(5).S(1).T(0).ReferenceAndUpdate(1);
      next_pattern_ = kDelta0;
      break;
  }
  return result;
}

absl::optional<GenericFrameInfo> ScalabilityStructureL2T2KeyShift::OnEncodeDone(
    LayerFrameConfig config) {
  if (config.IsKeyframe()) {
    config = KeyFrameConfig();
  }

  absl::optional<GenericFrameInfo> frame_info;
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
  if (config.IsKeyframe()) {
    frame_info->part_of_chain = {true, true};
  } else if (config.TemporalId() == 0) {
    frame_info->part_of_chain = {config.SpatialId() == 0,
                                 config.SpatialId() == 1};
  } else {
    frame_info->part_of_chain = {false, false};
  }
  return frame_info;
}

}  // namespace webrtc
