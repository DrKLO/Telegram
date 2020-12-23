/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/video_coding/svc/scalability_structure_key_svc.h"

#include <bitset>
#include <utility>
#include <vector>

#include "absl/types/optional.h"
#include "api/transport/rtp/dependency_descriptor.h"
#include "api/video/video_bitrate_allocation.h"
#include "common_video/generic_frame_descriptor/generic_frame_info.h"
#include "modules/video_coding/svc/scalable_video_controller.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace {
// Values to use as LayerFrameConfig::Id
enum : int { kKey, kDelta };

DecodeTargetIndication
Dti(int sid, int tid, const ScalableVideoController::LayerFrameConfig& config) {
  if (config.IsKeyframe() || config.Id() == kKey) {
    RTC_DCHECK_EQ(config.TemporalId(), 0);
    return sid < config.SpatialId() ? DecodeTargetIndication::kNotPresent
                                    : DecodeTargetIndication::kSwitch;
  }

  if (sid != config.SpatialId() || tid < config.TemporalId()) {
    return DecodeTargetIndication::kNotPresent;
  }
  if (tid == config.TemporalId() && tid > 0) {
    return DecodeTargetIndication::kDiscardable;
  }
  return DecodeTargetIndication::kSwitch;
}

}  // namespace

constexpr int ScalabilityStructureKeySvc::kMaxNumSpatialLayers;
constexpr int ScalabilityStructureKeySvc::kMaxNumTemporalLayers;

ScalabilityStructureKeySvc::ScalabilityStructureKeySvc(int num_spatial_layers,
                                                       int num_temporal_layers)
    : num_spatial_layers_(num_spatial_layers),
      num_temporal_layers_(num_temporal_layers),
      active_decode_targets_(
          (uint32_t{1} << (num_spatial_layers * num_temporal_layers)) - 1) {
  // There is no point to use this structure without spatial scalability.
  RTC_DCHECK_GT(num_spatial_layers, 1);
  RTC_DCHECK_LE(num_spatial_layers, kMaxNumSpatialLayers);
  RTC_DCHECK_LE(num_temporal_layers, kMaxNumTemporalLayers);
}

ScalabilityStructureKeySvc::~ScalabilityStructureKeySvc() = default;

ScalableVideoController::StreamLayersConfig
ScalabilityStructureKeySvc::StreamConfig() const {
  StreamLayersConfig result;
  result.num_spatial_layers = num_spatial_layers_;
  result.num_temporal_layers = num_temporal_layers_;
  result.scaling_factor_num[num_spatial_layers_ - 1] = 1;
  result.scaling_factor_den[num_spatial_layers_ - 1] = 1;
  for (int sid = num_spatial_layers_ - 1; sid > 0; --sid) {
    result.scaling_factor_num[sid - 1] = 1;
    result.scaling_factor_den[sid - 1] = 2 * result.scaling_factor_den[sid];
  }
  return result;
}

bool ScalabilityStructureKeySvc::TemporalLayerIsActive(int tid) const {
  if (tid >= num_temporal_layers_) {
    return false;
  }
  for (int sid = 0; sid < num_spatial_layers_; ++sid) {
    if (DecodeTargetIsActive(sid, tid)) {
      return true;
    }
  }
  return false;
}

std::vector<ScalableVideoController::LayerFrameConfig>
ScalabilityStructureKeySvc::KeyframeConfig() {
  std::vector<LayerFrameConfig> configs;
  configs.reserve(num_spatial_layers_);
  absl::optional<int> spatial_dependency_buffer_id;
  spatial_id_is_enabled_.reset();
  // Disallow temporal references cross T0 on higher temporal layers.
  can_reference_t1_frame_for_spatial_id_.reset();
  for (int sid = 0; sid < num_spatial_layers_; ++sid) {
    if (!DecodeTargetIsActive(sid, /*tid=*/0)) {
      continue;
    }
    configs.emplace_back();
    ScalableVideoController::LayerFrameConfig& config = configs.back();
    config.Id(kKey).S(sid).T(0);

    if (spatial_dependency_buffer_id) {
      config.Reference(*spatial_dependency_buffer_id);
    } else {
      config.Keyframe();
    }
    config.Update(BufferIndex(sid, /*tid=*/0));

    spatial_id_is_enabled_.set(sid);
    spatial_dependency_buffer_id = BufferIndex(sid, /*tid=*/0);
  }
  return configs;
}

std::vector<ScalableVideoController::LayerFrameConfig>
ScalabilityStructureKeySvc::T0Config() {
  std::vector<LayerFrameConfig> configs;
  configs.reserve(num_spatial_layers_);
  // Disallow temporal references cross T0 on higher temporal layers.
  can_reference_t1_frame_for_spatial_id_.reset();
  for (int sid = 0; sid < num_spatial_layers_; ++sid) {
    if (!DecodeTargetIsActive(sid, /*tid=*/0)) {
      spatial_id_is_enabled_.reset(sid);
      continue;
    }
    configs.emplace_back();
    configs.back().Id(kDelta).S(sid).T(0).ReferenceAndUpdate(
        BufferIndex(sid, /*tid=*/0));
  }
  return configs;
}

std::vector<ScalableVideoController::LayerFrameConfig>
ScalabilityStructureKeySvc::T1Config() {
  std::vector<LayerFrameConfig> configs;
  configs.reserve(num_spatial_layers_);
  for (int sid = 0; sid < num_spatial_layers_; ++sid) {
    if (!DecodeTargetIsActive(sid, /*tid=*/1)) {
      continue;
    }
    configs.emplace_back();
    ScalableVideoController::LayerFrameConfig& config = configs.back();
    config.Id(kDelta).S(sid).T(1).Reference(BufferIndex(sid, /*tid=*/0));
    if (num_temporal_layers_ > 2) {
      config.Update(BufferIndex(sid, /*tid=*/1));
      can_reference_t1_frame_for_spatial_id_.set(sid);
    }
  }
  return configs;
}

std::vector<ScalableVideoController::LayerFrameConfig>
ScalabilityStructureKeySvc::T2Config() {
  std::vector<LayerFrameConfig> configs;
  configs.reserve(num_spatial_layers_);
  for (int sid = 0; sid < num_spatial_layers_; ++sid) {
    if (!DecodeTargetIsActive(sid, /*tid=*/2)) {
      continue;
    }
    configs.emplace_back();
    ScalableVideoController::LayerFrameConfig& config = configs.back();
    config.Id(kDelta).S(sid).T(2);
    if (can_reference_t1_frame_for_spatial_id_[sid]) {
      config.Reference(BufferIndex(sid, /*tid=*/1));
    } else {
      config.Reference(BufferIndex(sid, /*tid=*/0));
    }
  }
  return configs;
}

std::vector<ScalableVideoController::LayerFrameConfig>
ScalabilityStructureKeySvc::NextFrameConfig(bool restart) {
  if (active_decode_targets_.none()) {
    last_pattern_ = kNone;
    return {};
  }

  if (restart) {
    last_pattern_ = kNone;
  }

  switch (last_pattern_) {
    case kNone:
      last_pattern_ = kDeltaT0;
      return KeyframeConfig();
    case kDeltaT2B:
      last_pattern_ = kDeltaT0;
      return T0Config();
    case kDeltaT2A:
      if (TemporalLayerIsActive(1)) {
        last_pattern_ = kDeltaT1;
        return T1Config();
      }
      last_pattern_ = kDeltaT0;
      return T0Config();
    case kDeltaT1:
      if (TemporalLayerIsActive(2)) {
        last_pattern_ = kDeltaT2B;
        return T2Config();
      }
      last_pattern_ = kDeltaT0;
      return T0Config();
    case kDeltaT0:
      if (TemporalLayerIsActive(2)) {
        last_pattern_ = kDeltaT2A;
        return T2Config();
      } else if (TemporalLayerIsActive(1)) {
        last_pattern_ = kDeltaT1;
        return T1Config();
      }
      last_pattern_ = kDeltaT0;
      return T0Config();
  }
  RTC_NOTREACHED();
  return {};
}

GenericFrameInfo ScalabilityStructureKeySvc::OnEncodeDone(
    const LayerFrameConfig& config) {
  GenericFrameInfo frame_info;
  frame_info.spatial_id = config.SpatialId();
  frame_info.temporal_id = config.TemporalId();
  frame_info.encoder_buffers = config.Buffers();
  frame_info.decode_target_indications.reserve(num_spatial_layers_ *
                                               num_temporal_layers_);
  for (int sid = 0; sid < num_spatial_layers_; ++sid) {
    for (int tid = 0; tid < num_temporal_layers_; ++tid) {
      frame_info.decode_target_indications.push_back(Dti(sid, tid, config));
    }
  }
  frame_info.part_of_chain.assign(num_spatial_layers_, false);
  if (config.IsKeyframe() || config.Id() == kKey) {
    RTC_DCHECK_EQ(config.TemporalId(), 0);
    for (int sid = config.SpatialId(); sid < num_spatial_layers_; ++sid) {
      frame_info.part_of_chain[sid] = true;
    }
  } else if (config.TemporalId() == 0) {
    frame_info.part_of_chain[config.SpatialId()] = true;
  }
  frame_info.active_decode_targets = active_decode_targets_;
  return frame_info;
}

void ScalabilityStructureKeySvc::OnRatesUpdated(
    const VideoBitrateAllocation& bitrates) {
  for (int sid = 0; sid < num_spatial_layers_; ++sid) {
    // Enable/disable spatial layers independetely.
    bool active = bitrates.GetBitrate(sid, /*tid=*/0) > 0;
    SetDecodeTargetIsActive(sid, /*tid=*/0, active);
    if (!spatial_id_is_enabled_[sid] && active) {
      // Key frame is required to reenable any spatial layer.
      last_pattern_ = kNone;
    }

    for (int tid = 1; tid < num_temporal_layers_; ++tid) {
      // To enable temporal layer, require bitrates for lower temporal layers.
      active = active && bitrates.GetBitrate(sid, tid) > 0;
      SetDecodeTargetIsActive(sid, tid, active);
    }
  }
}

ScalabilityStructureL2T1Key::~ScalabilityStructureL2T1Key() = default;

FrameDependencyStructure ScalabilityStructureL2T1Key::DependencyStructure()
    const {
  FrameDependencyStructure structure;
  structure.num_decode_targets = 2;
  structure.num_chains = 2;
  structure.decode_target_protected_by_chain = {0, 1};
  structure.templates.resize(4);
  structure.templates[0].S(0).Dtis("S-").ChainDiffs({2, 1}).FrameDiffs({2});
  structure.templates[1].S(0).Dtis("SS").ChainDiffs({0, 0});
  structure.templates[2].S(1).Dtis("-S").ChainDiffs({1, 2}).FrameDiffs({2});
  structure.templates[3].S(1).Dtis("-S").ChainDiffs({1, 1}).FrameDiffs({1});
  return structure;
}

ScalabilityStructureL2T2Key::~ScalabilityStructureL2T2Key() = default;

FrameDependencyStructure ScalabilityStructureL2T2Key::DependencyStructure()
    const {
  FrameDependencyStructure structure;
  structure.num_decode_targets = 4;
  structure.num_chains = 2;
  structure.decode_target_protected_by_chain = {0, 0, 1, 1};
  structure.templates.resize(6);
  auto& templates = structure.templates;
  templates[0].S(0).T(0).Dtis("SSSS").ChainDiffs({0, 0});
  templates[1].S(0).T(0).Dtis("SS--").ChainDiffs({4, 3}).FrameDiffs({4});
  templates[2].S(0).T(1).Dtis("-D--").ChainDiffs({2, 1}).FrameDiffs({2});
  templates[3].S(1).T(0).Dtis("--SS").ChainDiffs({1, 1}).FrameDiffs({1});
  templates[4].S(1).T(0).Dtis("--SS").ChainDiffs({1, 4}).FrameDiffs({4});
  templates[5].S(1).T(1).Dtis("---D").ChainDiffs({3, 2}).FrameDiffs({2});
  return structure;
}

ScalabilityStructureL3T3Key::~ScalabilityStructureL3T3Key() = default;

FrameDependencyStructure ScalabilityStructureL3T3Key::DependencyStructure()
    const {
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
  t[0x0].S(0).T(0).Dtis("SSSSSSSSS").ChainDiffs({0, 0, 0});
  t[0x5].S(1).T(0).Dtis("---SSSSSS").ChainDiffs({1, 1, 1}).FrameDiffs({1});
  t[0xA].S(2).T(0).Dtis("------SSS").ChainDiffs({2, 1, 1}).FrameDiffs({1});
  t[0x3].S(0).T(2).Dtis("--D------").ChainDiffs({3, 2, 1}).FrameDiffs({3});
  t[0x8].S(1).T(2).Dtis("-----D---").ChainDiffs({4, 3, 2}).FrameDiffs({3});
  t[0xD].S(2).T(2).Dtis("--------D").ChainDiffs({5, 4, 3}).FrameDiffs({3});
  t[0x2].S(0).T(1).Dtis("-DS------").ChainDiffs({6, 5, 4}).FrameDiffs({6});
  t[0x7].S(1).T(1).Dtis("----DS---").ChainDiffs({7, 6, 5}).FrameDiffs({6});
  t[0xC].S(2).T(1).Dtis("-------DS").ChainDiffs({8, 7, 6}).FrameDiffs({6});
  t[0x4].S(0).T(2).Dtis("--D------").ChainDiffs({9, 8, 7}).FrameDiffs({3});
  t[0x9].S(1).T(2).Dtis("-----D---").ChainDiffs({10, 9, 8}).FrameDiffs({3});
  t[0xE].S(2).T(2).Dtis("--------D").ChainDiffs({11, 10, 9}).FrameDiffs({3});
  t[0x1].S(0).T(0).Dtis("SSS------").ChainDiffs({12, 11, 10}).FrameDiffs({12});
  t[0x6].S(1).T(0).Dtis("---SSS---").ChainDiffs({1, 12, 11}).FrameDiffs({12});
  t[0xB].S(2).T(0).Dtis("------SSS").ChainDiffs({2, 1, 12}).FrameDiffs({12});
  return structure;
}

}  // namespace webrtc
