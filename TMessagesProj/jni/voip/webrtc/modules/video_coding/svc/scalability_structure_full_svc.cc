/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/video_coding/svc/scalability_structure_full_svc.h"

#include <utility>
#include <vector>

#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/transport/rtp/dependency_descriptor.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {

constexpr int ScalabilityStructureFullSvc::kMaxNumSpatialLayers;
constexpr int ScalabilityStructureFullSvc::kMaxNumTemporalLayers;
constexpr absl::string_view ScalabilityStructureFullSvc::kFramePatternNames[];

ScalabilityStructureFullSvc::ScalabilityStructureFullSvc(
    int num_spatial_layers,
    int num_temporal_layers,
    ScalingFactor resolution_factor)
    : num_spatial_layers_(num_spatial_layers),
      num_temporal_layers_(num_temporal_layers),
      resolution_factor_(resolution_factor),
      active_decode_targets_(
          (uint32_t{1} << (num_spatial_layers * num_temporal_layers)) - 1) {
  RTC_DCHECK_LE(num_spatial_layers, kMaxNumSpatialLayers);
  RTC_DCHECK_LE(num_temporal_layers, kMaxNumTemporalLayers);
}

ScalabilityStructureFullSvc::~ScalabilityStructureFullSvc() = default;

ScalabilityStructureFullSvc::StreamLayersConfig
ScalabilityStructureFullSvc::StreamConfig() const {
  StreamLayersConfig result;
  result.num_spatial_layers = num_spatial_layers_;
  result.num_temporal_layers = num_temporal_layers_;
  result.scaling_factor_num[num_spatial_layers_ - 1] = 1;
  result.scaling_factor_den[num_spatial_layers_ - 1] = 1;
  for (int sid = num_spatial_layers_ - 1; sid > 0; --sid) {
    result.scaling_factor_num[sid - 1] =
        resolution_factor_.num * result.scaling_factor_num[sid];
    result.scaling_factor_den[sid - 1] =
        resolution_factor_.den * result.scaling_factor_den[sid];
  }
  return result;
}

bool ScalabilityStructureFullSvc::TemporalLayerIsActive(int tid) const {
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

DecodeTargetIndication ScalabilityStructureFullSvc::Dti(
    int sid,
    int tid,
    const LayerFrameConfig& config) {
  if (sid < config.SpatialId() || tid < config.TemporalId()) {
    return DecodeTargetIndication::kNotPresent;
  }
  if (sid == config.SpatialId()) {
    if (tid == 0) {
      RTC_DCHECK_EQ(config.TemporalId(), 0);
      return DecodeTargetIndication::kSwitch;
    }
    if (tid == config.TemporalId()) {
      return DecodeTargetIndication::kDiscardable;
    }
    if (tid > config.TemporalId()) {
      RTC_DCHECK_GT(tid, config.TemporalId());
      return DecodeTargetIndication::kSwitch;
    }
  }
  RTC_DCHECK_GT(sid, config.SpatialId());
  RTC_DCHECK_GE(tid, config.TemporalId());
  if (config.IsKeyframe() || config.Id() == kKey) {
    return DecodeTargetIndication::kSwitch;
  }
  return DecodeTargetIndication::kRequired;
}

ScalabilityStructureFullSvc::FramePattern
ScalabilityStructureFullSvc::NextPattern() const {
  switch (last_pattern_) {
    case kNone:
      return kKey;
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
    case kKey:
    case kDeltaT0:
      if (TemporalLayerIsActive(2)) {
        return kDeltaT2A;
      }
      if (TemporalLayerIsActive(1)) {
        return kDeltaT1;
      }
      return kDeltaT0;
  }
  RTC_NOTREACHED();
  return kNone;
}

std::vector<ScalableVideoController::LayerFrameConfig>
ScalabilityStructureFullSvc::NextFrameConfig(bool restart) {
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

  absl::optional<int> spatial_dependency_buffer_id;
  switch (current_pattern) {
    case kDeltaT0:
    case kKey:
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

        if (spatial_dependency_buffer_id) {
          config.Reference(*spatial_dependency_buffer_id);
        } else if (current_pattern == kKey) {
          config.Keyframe();
        }

        if (can_reference_t0_frame_for_spatial_id_[sid]) {
          config.ReferenceAndUpdate(BufferIndex(sid, /*tid=*/0));
        } else {
          // TODO(bugs.webrtc.org/11999): Propagate chain restart on delta frame
          // to ChainDiffCalculator
          config.Update(BufferIndex(sid, /*tid=*/0));
        }

        can_reference_t0_frame_for_spatial_id_.set(sid);
        spatial_dependency_buffer_id = BufferIndex(sid, /*tid=*/0);
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
        config.Id(current_pattern).S(sid).T(1);
        // Temporal reference.
        config.Reference(BufferIndex(sid, /*tid=*/0));
        // Spatial reference unless this is the lowest active spatial layer.
        if (spatial_dependency_buffer_id) {
          config.Reference(*spatial_dependency_buffer_id);
        }
        // No frame reference top layer frame, so no need save it into a buffer.
        if (num_temporal_layers_ > 2 || sid < num_spatial_layers_ - 1) {
          config.Update(BufferIndex(sid, /*tid=*/1));
        }
        spatial_dependency_buffer_id = BufferIndex(sid, /*tid=*/1);
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
        // Temporal reference.
        if (current_pattern == kDeltaT2B &&
            can_reference_t1_frame_for_spatial_id_[sid]) {
          config.Reference(BufferIndex(sid, /*tid=*/1));
        } else {
          config.Reference(BufferIndex(sid, /*tid=*/0));
        }
        // Spatial reference unless this is the lowest active spatial layer.
        if (spatial_dependency_buffer_id) {
          config.Reference(*spatial_dependency_buffer_id);
        }
        // No frame reference top layer frame, so no need save it into a buffer.
        if (sid < num_spatial_layers_ - 1) {
          config.Update(BufferIndex(sid, /*tid=*/2));
        }
        spatial_dependency_buffer_id = BufferIndex(sid, /*tid=*/2);
      }
      break;
    case kNone:
      RTC_NOTREACHED();
      break;
  }

  if (configs.empty() && !restart) {
    RTC_LOG(LS_WARNING) << "Failed to generate configuration for L"
                        << num_spatial_layers_ << "T" << num_temporal_layers_
                        << " with active decode targets "
                        << active_decode_targets_.to_string('-').substr(
                               active_decode_targets_.size() -
                               num_spatial_layers_ * num_temporal_layers_)
                        << " and transition from "
                        << kFramePatternNames[last_pattern_] << " to "
                        << kFramePatternNames[current_pattern]
                        << ". Resetting.";
    return NextFrameConfig(/*restart=*/true);
  }

  return configs;
}

GenericFrameInfo ScalabilityStructureFullSvc::OnEncodeDone(
    const LayerFrameConfig& config) {
  // When encoder drops all frames for a temporal unit, it is better to reuse
  // old temporal pattern rather than switch to next one, thus switch to next
  // pattern defered here from the `NextFrameConfig`.
  // In particular creating VP9 references rely on this behavior.
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
  if (config.TemporalId() == 0) {
    frame_info.part_of_chain.resize(num_spatial_layers_);
    for (int sid = 0; sid < num_spatial_layers_; ++sid) {
      frame_info.part_of_chain[sid] = config.SpatialId() <= sid;
    }
  } else {
    frame_info.part_of_chain.assign(num_spatial_layers_, false);
  }
  frame_info.active_decode_targets = active_decode_targets_;
  return frame_info;
}

void ScalabilityStructureFullSvc::OnRatesUpdated(
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

FrameDependencyStructure ScalabilityStructureL1T2::DependencyStructure() const {
  FrameDependencyStructure structure;
  structure.num_decode_targets = 2;
  structure.num_chains = 1;
  structure.decode_target_protected_by_chain = {0, 0};
  structure.templates.resize(3);
  structure.templates[0].T(0).Dtis("SS").ChainDiffs({0});
  structure.templates[1].T(0).Dtis("SS").ChainDiffs({2}).FrameDiffs({2});
  structure.templates[2].T(1).Dtis("-D").ChainDiffs({1}).FrameDiffs({1});
  return structure;
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

FrameDependencyStructure ScalabilityStructureL2T1::DependencyStructure() const {
  FrameDependencyStructure structure;
  structure.num_decode_targets = 2;
  structure.num_chains = 2;
  structure.decode_target_protected_by_chain = {0, 1};
  structure.templates.resize(4);
  structure.templates[0].S(0).Dtis("SR").ChainDiffs({2, 1}).FrameDiffs({2});
  structure.templates[1].S(0).Dtis("SS").ChainDiffs({0, 0});
  structure.templates[2].S(1).Dtis("-S").ChainDiffs({1, 1}).FrameDiffs({2, 1});
  structure.templates[3].S(1).Dtis("-S").ChainDiffs({1, 1}).FrameDiffs({1});
  return structure;
}

FrameDependencyStructure ScalabilityStructureL2T2::DependencyStructure() const {
  FrameDependencyStructure structure;
  structure.num_decode_targets = 4;
  structure.num_chains = 2;
  structure.decode_target_protected_by_chain = {0, 0, 1, 1};
  structure.templates.resize(6);
  auto& templates = structure.templates;
  templates[0].S(0).T(0).Dtis("SSSS").ChainDiffs({0, 0});
  templates[1].S(0).T(0).Dtis("SSRR").ChainDiffs({4, 3}).FrameDiffs({4});
  templates[2].S(0).T(1).Dtis("-D-R").ChainDiffs({2, 1}).FrameDiffs({2});
  templates[3].S(1).T(0).Dtis("--SS").ChainDiffs({1, 1}).FrameDiffs({1});
  templates[4].S(1).T(0).Dtis("--SS").ChainDiffs({1, 1}).FrameDiffs({4, 1});
  templates[5].S(1).T(1).Dtis("---D").ChainDiffs({3, 2}).FrameDiffs({2, 1});
  return structure;
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
