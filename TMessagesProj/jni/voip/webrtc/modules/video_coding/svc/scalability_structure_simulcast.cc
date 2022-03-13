/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/video_coding/svc/scalability_structure_simulcast.h"

#include <utility>
#include <vector>

#include "absl/base/macros.h"
#include "api/transport/rtp/dependency_descriptor.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace {

DecodeTargetIndication
Dti(int sid, int tid, const ScalableVideoController::LayerFrameConfig& config) {
  if (sid != config.SpatialId() || tid < config.TemporalId()) {
    return DecodeTargetIndication::kNotPresent;
  }
  if (tid == 0) {
    RTC_DCHECK_EQ(config.TemporalId(), 0);
    return DecodeTargetIndication::kSwitch;
  }
  if (tid == config.TemporalId()) {
    return DecodeTargetIndication::kDiscardable;
  }
  RTC_DCHECK_GT(tid, config.TemporalId());
  return DecodeTargetIndication::kSwitch;
}

}  // namespace

constexpr int ScalabilityStructureSimulcast::kMaxNumSpatialLayers;
constexpr int ScalabilityStructureSimulcast::kMaxNumTemporalLayers;

ScalabilityStructureSimulcast::ScalabilityStructureSimulcast(
    int num_spatial_layers,
    int num_temporal_layers)
    : num_spatial_layers_(num_spatial_layers),
      num_temporal_layers_(num_temporal_layers),
      active_decode_targets_(
          (uint32_t{1} << (num_spatial_layers * num_temporal_layers)) - 1) {
  RTC_DCHECK_LE(num_spatial_layers, kMaxNumSpatialLayers);
  RTC_DCHECK_LE(num_temporal_layers, kMaxNumTemporalLayers);
}

ScalabilityStructureSimulcast::~ScalabilityStructureSimulcast() = default;

ScalableVideoController::StreamLayersConfig
ScalabilityStructureSimulcast::StreamConfig() const {
  StreamLayersConfig result;
  result.num_spatial_layers = num_spatial_layers_;
  result.num_temporal_layers = num_temporal_layers_;
  result.scaling_factor_num[num_spatial_layers_ - 1] = 1;
  result.scaling_factor_den[num_spatial_layers_ - 1] = 1;
  for (int sid = num_spatial_layers_ - 1; sid > 0; --sid) {
    result.scaling_factor_num[sid - 1] = 1;
    result.scaling_factor_den[sid - 1] = 2 * result.scaling_factor_den[sid];
  }
  result.uses_reference_scaling = false;
  return result;
}

bool ScalabilityStructureSimulcast::TemporalLayerIsActive(int tid) const {
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

ScalabilityStructureSimulcast::FramePattern
ScalabilityStructureSimulcast::NextPattern() const {
  switch (last_pattern_) {
    case kNone:
    case kDeltaT2B:
      return kDeltaT0;
    case kDeltaT2A:
      if (TemporalLayerIsActive(1)) {
        return kDeltaT1;
      }
      return kDeltaT0;
    case kDeltaT1:
      if (TemporalLayerIsActive(2)) {
        return kDeltaT2B;
      }
      return kDeltaT0;
    case kDeltaT0:
      if (TemporalLayerIsActive(2)) {
        return kDeltaT2A;
      }
      if (TemporalLayerIsActive(1)) {
        return kDeltaT1;
      }
      return kDeltaT0;
  }
  RTC_DCHECK_NOTREACHED();
  return kDeltaT0;
}

std::vector<ScalableVideoController::LayerFrameConfig>
ScalabilityStructureSimulcast::NextFrameConfig(bool restart) {
  std::vector<LayerFrameConfig> configs;
  if (active_decode_targets_.none()) {
    last_pattern_ = kNone;
    return configs;
  }
  configs.reserve(num_spatial_layers_);

  if (last_pattern_ == kNone || restart) {
    can_reference_t0_frame_for_spatial_id_.reset();
    last_pattern_ = kNone;
  }
  FramePattern current_pattern = NextPattern();

  switch (current_pattern) {
    case kDeltaT0:
      // Disallow temporal references cross T0 on higher temporal layers.
      can_reference_t1_frame_for_spatial_id_.reset();
      for (int sid = 0; sid < num_spatial_layers_; ++sid) {
        if (!DecodeTargetIsActive(sid, /*tid=*/0)) {
          // Next frame from the spatial layer `sid` shouldn't depend on
          // potentially old previous frame from the spatial layer `sid`.
          can_reference_t0_frame_for_spatial_id_.reset(sid);
          continue;
        }
        configs.emplace_back();
        ScalableVideoController::LayerFrameConfig& config = configs.back();
        config.Id(current_pattern).S(sid).T(0);

        if (can_reference_t0_frame_for_spatial_id_[sid]) {
          config.ReferenceAndUpdate(BufferIndex(sid, /*tid=*/0));
        } else {
          config.Keyframe().Update(BufferIndex(sid, /*tid=*/0));
        }
        can_reference_t0_frame_for_spatial_id_.set(sid);
      }
      break;
    case kDeltaT1:
      for (int sid = 0; sid < num_spatial_layers_; ++sid) {
        if (!DecodeTargetIsActive(sid, /*tid=*/1) ||
            !can_reference_t0_frame_for_spatial_id_[sid]) {
          continue;
        }
        configs.emplace_back();
        ScalableVideoController::LayerFrameConfig& config = configs.back();
        config.Id(current_pattern)
            .S(sid)
            .T(1)
            .Reference(BufferIndex(sid, /*tid=*/0));
        // Save frame only if there is a higher temporal layer that may need it.
        if (num_temporal_layers_ > 2) {
          config.Update(BufferIndex(sid, /*tid=*/1));
        }
      }
      break;
    case kDeltaT2A:
    case kDeltaT2B:
      for (int sid = 0; sid < num_spatial_layers_; ++sid) {
        if (!DecodeTargetIsActive(sid, /*tid=*/2) ||
            !can_reference_t0_frame_for_spatial_id_[sid]) {
          continue;
        }
        configs.emplace_back();
        ScalableVideoController::LayerFrameConfig& config = configs.back();
        config.Id(current_pattern).S(sid).T(2);
        if (can_reference_t1_frame_for_spatial_id_[sid]) {
          config.Reference(BufferIndex(sid, /*tid=*/1));
        } else {
          config.Reference(BufferIndex(sid, /*tid=*/0));
        }
      }
      break;
    case kNone:
      RTC_DCHECK_NOTREACHED();
      break;
  }

  return configs;
}

GenericFrameInfo ScalabilityStructureSimulcast::OnEncodeDone(
    const LayerFrameConfig& config) {
  last_pattern_ = static_cast<FramePattern>(config.Id());
  if (config.TemporalId() == 1) {
    can_reference_t1_frame_for_spatial_id_.set(config.SpatialId());
  }
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
  if (config.TemporalId() == 0) {
    frame_info.part_of_chain[config.SpatialId()] = true;
  }
  frame_info.active_decode_targets = active_decode_targets_;
  return frame_info;
}

void ScalabilityStructureSimulcast::OnRatesUpdated(
    const VideoBitrateAllocation& bitrates) {
  for (int sid = 0; sid < num_spatial_layers_; ++sid) {
    // Enable/disable spatial layers independetely.
    bool active = true;
    for (int tid = 0; tid < num_temporal_layers_; ++tid) {
      // To enable temporal layer, require bitrates for lower temporal layers.
      active = active && bitrates.GetBitrate(sid, tid) > 0;
      SetDecodeTargetIsActive(sid, tid, active);
    }
  }
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

FrameDependencyStructure ScalabilityStructureS3T3::DependencyStructure() const {
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
  t[0x1].S(0).T(0).Dtis("SSS------").ChainDiffs({0, 0, 0});
  t[0x6].S(1).T(0).Dtis("---SSS---").ChainDiffs({1, 0, 0});
  t[0xB].S(2).T(0).Dtis("------SSS").ChainDiffs({2, 1, 0});
  t[0x3].S(0).T(2).Dtis("--D------").ChainDiffs({3, 2, 1}).FrameDiffs({3});
  t[0x8].S(1).T(2).Dtis("-----D---").ChainDiffs({4, 3, 2}).FrameDiffs({3});
  t[0xD].S(2).T(2).Dtis("--------D").ChainDiffs({5, 4, 3}).FrameDiffs({3});
  t[0x2].S(0).T(1).Dtis("-DS------").ChainDiffs({6, 5, 4}).FrameDiffs({6});
  t[0x7].S(1).T(1).Dtis("----DS---").ChainDiffs({7, 6, 5}).FrameDiffs({6});
  t[0xC].S(2).T(1).Dtis("-------DS").ChainDiffs({8, 7, 6}).FrameDiffs({6});
  t[0x4].S(0).T(2).Dtis("--D------").ChainDiffs({9, 8, 7}).FrameDiffs({3});
  t[0x9].S(1).T(2).Dtis("-----D---").ChainDiffs({10, 9, 8}).FrameDiffs({3});
  t[0xE].S(2).T(2).Dtis("--------D").ChainDiffs({11, 10, 9}).FrameDiffs({3});
  t[0x0].S(0).T(0).Dtis("SSS------").ChainDiffs({12, 11, 10}).FrameDiffs({12});
  t[0x5].S(1).T(0).Dtis("---SSS---").ChainDiffs({1, 12, 11}).FrameDiffs({12});
  t[0xA].S(2).T(0).Dtis("------SSS").ChainDiffs({2, 1, 12}).FrameDiffs({12});
  return structure;
}

}  // namespace webrtc
