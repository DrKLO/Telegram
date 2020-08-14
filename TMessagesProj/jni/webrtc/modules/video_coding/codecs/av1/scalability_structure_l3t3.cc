/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/video_coding/codecs/av1/scalability_structure_l3t3.h"

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
constexpr auto kRequired = DecodeTargetIndication::kRequired;

constexpr DecodeTargetIndication kDtis[12][9] = {
    // Key, S0
    {kSwitch, kSwitch, kSwitch,   // S0
     kSwitch, kSwitch, kSwitch,   // S1
     kSwitch, kSwitch, kSwitch},  // S2
    // Key, S1
    {kNotPresent, kNotPresent, kNotPresent,  // S0
     kSwitch, kSwitch, kSwitch,              // S1
     kSwitch, kSwitch, kSwitch},             // S2
    // Key, S2
    {kNotPresent, kNotPresent, kNotPresent,  // S0
     kNotPresent, kNotPresent, kNotPresent,  // S1
     kSwitch, kSwitch, kSwitch},             // S2
    // Delta, S0T2
    {kNotPresent, kNotPresent, kDiscardable,  // S0
     kNotPresent, kNotPresent, kRequired,     // S1
     kNotPresent, kNotPresent, kRequired},    // S2
    // Delta, S1T2
    {kNotPresent, kNotPresent, kNotPresent,   // S0
     kNotPresent, kNotPresent, kDiscardable,  // S1
     kNotPresent, kNotPresent, kRequired},    // S2
    // Delta, S2T2
    {kNotPresent, kNotPresent, kNotPresent,    // S0
     kNotPresent, kNotPresent, kNotPresent,    // S1
     kNotPresent, kNotPresent, kDiscardable},  // S2
    // Delta, S0T1
    {kNotPresent, kDiscardable, kSwitch,  // S0
     kNotPresent, kRequired, kRequired,   // S1
     kNotPresent, kRequired, kRequired},  // S2
    // Delta, S1T1
    {kNotPresent, kNotPresent, kNotPresent,  // S0
     kNotPresent, kDiscardable, kSwitch,     // S1
     kNotPresent, kRequired, kRequired},     // S2
    // Delta, S2T1
    {kNotPresent, kNotPresent, kNotPresent,  // S0
     kNotPresent, kNotPresent, kNotPresent,  // S1
     kNotPresent, kDiscardable, kSwitch},    // S2
    // Delta, S0T0
    {kSwitch, kSwitch, kSwitch,         // S0
     kRequired, kRequired, kRequired,   // S1
     kRequired, kRequired, kRequired},  // S2
    // Delta, S1T0
    {kNotPresent, kNotPresent, kNotPresent,  // S0
     kSwitch, kSwitch, kSwitch,              // S1
     kRequired, kRequired, kRequired},       // S2
    // Delta, S2T0
    {kNotPresent, kNotPresent, kNotPresent,  // S0
     kNotPresent, kNotPresent, kNotPresent,  // S1
     kSwitch, kSwitch, kSwitch},             // S2
};

}  // namespace

ScalabilityStructureL3T3::~ScalabilityStructureL3T3() = default;

ScalableVideoController::StreamLayersConfig
ScalabilityStructureL3T3::StreamConfig() const {
  StreamLayersConfig result;
  result.num_spatial_layers = 3;
  result.num_temporal_layers = 3;
  result.scaling_factor_num[0] = 1;
  result.scaling_factor_den[0] = 4;
  result.scaling_factor_num[1] = 1;
  result.scaling_factor_den[1] = 2;
  return result;
}

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

ScalableVideoController::LayerFrameConfig
ScalabilityStructureL3T3::KeyFrameConfig() const {
  return LayerFrameConfig().Id(0).S(0).T(0).Keyframe().Update(0);
}

std::vector<ScalableVideoController::LayerFrameConfig>
ScalabilityStructureL3T3::NextFrameConfig(bool restart) {
  if (restart) {
    next_pattern_ = kKeyFrame;
  }
  std::vector<LayerFrameConfig> config(3);

  // For this structure name each of 8 buffers after the layer of the frame that
  // buffer keeps.
  static constexpr int kS0T0 = 0;
  static constexpr int kS1T0 = 1;
  static constexpr int kS2T0 = 2;
  static constexpr int kS0T1 = 3;
  static constexpr int kS1T1 = 4;
  static constexpr int kS2T1 = 5;
  static constexpr int kS0T2 = 6;
  static constexpr int kS1T2 = 7;
  switch (next_pattern_) {
    case kKeyFrame:
      config[0].Id(0).S(0).T(0).Keyframe().Update(kS0T0);
      config[1].Id(1).S(1).T(0).Update(kS1T0).Reference(kS0T0);
      config[2].Id(2).S(2).T(0).Update(kS2T0).Reference(kS1T0);
      next_pattern_ = kDeltaFrameT2A;
      break;
    case kDeltaFrameT2A:
      config[0].Id(3).S(0).T(2).Reference(kS0T0).Update(kS0T2);
      config[1].Id(4).S(1).T(2).Reference(kS1T0).Reference(kS0T2).Update(kS1T2);
      config[2].Id(5).S(2).T(2).Reference(kS2T0).Reference(kS1T2);
      next_pattern_ = kDeltaFrameT1;
      break;
    case kDeltaFrameT1:
      config[0].Id(6).S(0).T(1).Reference(kS0T0).Update(kS0T1);
      config[1].Id(7).S(1).T(1).Reference(kS1T0).Reference(kS0T1).Update(kS1T1);
      config[2].Id(8).S(2).T(1).Reference(kS2T0).Reference(kS1T1).Update(kS2T1);
      next_pattern_ = kDeltaFrameT2B;
      break;
    case kDeltaFrameT2B:
      config[0].Id(3).S(0).T(2).Reference(kS0T1).Update(kS0T2);
      config[1].Id(4).S(1).T(2).Reference(kS1T1).Reference(kS0T2).Update(kS1T2);
      config[2].Id(5).S(2).T(2).Reference(kS2T1).Reference(kS1T2);
      next_pattern_ = kDeltaFrameT0;
      break;
    case kDeltaFrameT0:
      config[0].Id(9).S(0).T(0).ReferenceAndUpdate(kS0T0);
      config[1].Id(10).S(1).T(0).ReferenceAndUpdate(kS1T0).Reference(kS0T0);
      config[2].Id(11).S(2).T(0).ReferenceAndUpdate(kS2T0).Reference(kS1T0);
      next_pattern_ = kDeltaFrameT2A;
      break;
  }
  return config;
}

absl::optional<GenericFrameInfo> ScalabilityStructureL3T3::OnEncodeDone(
    LayerFrameConfig config) {
  if (config.IsKeyframe() && config.Id() != 0) {
    // Encoder generated a key frame without asking to.
    if (config.SpatialId() > 0) {
      RTC_LOG(LS_WARNING) << "Unexpected spatial id " << config.SpatialId()
                          << " for key frame.";
    }
    config = LayerFrameConfig()
                 .Keyframe()
                 .Id(0)
                 .S(0)
                 .T(0)
                 .Update(0)
                 .Update(1)
                 .Update(2)
                 .Update(3)
                 .Update(4)
                 .Update(5)
                 .Update(6)
                 .Update(7);
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
  if (config.TemporalId() == 0) {
    frame_info->part_of_chain = {config.SpatialId() == 0,
                                 config.SpatialId() <= 1, true};
  } else {
    frame_info->part_of_chain = {false, false, false};
  }
  return frame_info;
}

}  // namespace webrtc
