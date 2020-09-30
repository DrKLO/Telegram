/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/video_coding/codecs/av1/scalability_structure_l1t3.h"

#include <utility>
#include <vector>

#include "absl/base/macros.h"
#include "absl/types/optional.h"
#include "api/transport/rtp/dependency_descriptor.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace {

constexpr auto kNotPresent = DecodeTargetIndication::kNotPresent;
constexpr auto kDiscardable = DecodeTargetIndication::kDiscardable;
constexpr auto kSwitch = DecodeTargetIndication::kSwitch;

constexpr DecodeTargetIndication kDtis[3][3] = {
    {kSwitch, kSwitch, kSwitch},               // T0
    {kNotPresent, kDiscardable, kSwitch},      // T1
    {kNotPresent, kNotPresent, kDiscardable},  // T2
};

}  // namespace

ScalabilityStructureL1T3::~ScalabilityStructureL1T3() = default;

ScalableVideoController::StreamLayersConfig
ScalabilityStructureL1T3::StreamConfig() const {
  StreamLayersConfig result;
  result.num_spatial_layers = 1;
  result.num_temporal_layers = 3;
  return result;
}

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

std::vector<ScalableVideoController::LayerFrameConfig>
ScalabilityStructureL1T3::NextFrameConfig(bool restart) {
  if (restart) {
    next_pattern_ = kKeyFrame;
  }
  std::vector<LayerFrameConfig> config(1);

  switch (next_pattern_) {
    case kKeyFrame:
      config[0].T(0).Keyframe().Update(0);
      next_pattern_ = kDeltaFrameT2A;
      break;
    case kDeltaFrameT2A:
      config[0].T(2).Reference(0);
      next_pattern_ = kDeltaFrameT1;
      break;
    case kDeltaFrameT1:
      config[0].T(1).Reference(0).Update(1);
      next_pattern_ = kDeltaFrameT2B;
      break;
    case kDeltaFrameT2B:
      config[0].T(2).Reference(1);
      next_pattern_ = kDeltaFrameT0;
      break;
    case kDeltaFrameT0:
      config[0].T(0).ReferenceAndUpdate(0);
      next_pattern_ = kDeltaFrameT2A;
      break;
  }
  return config;
}

absl::optional<GenericFrameInfo> ScalabilityStructureL1T3::OnEncodeDone(
    LayerFrameConfig config) {
  absl::optional<GenericFrameInfo> frame_info;
  if (config.TemporalId() < 0 ||
      config.TemporalId() >= int{ABSL_ARRAYSIZE(kDtis)}) {
    RTC_LOG(LS_ERROR) << "Unexpected temporal id " << config.TemporalId();
    return frame_info;
  }
  frame_info.emplace();
  frame_info->temporal_id = config.TemporalId();
  frame_info->encoder_buffers = config.Buffers();
  frame_info->decode_target_indications.assign(
      std::begin(kDtis[config.TemporalId()]),
      std::end(kDtis[config.TemporalId()]));
  frame_info->part_of_chain = {config.TemporalId() == 0};
  return frame_info;
}

}  // namespace webrtc
