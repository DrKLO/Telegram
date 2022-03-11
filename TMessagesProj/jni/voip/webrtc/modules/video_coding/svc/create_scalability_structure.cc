/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/video_coding/svc/create_scalability_structure.h"

#include <memory>

#include "absl/strings/string_view.h"
#include "modules/video_coding/svc/scalability_structure_full_svc.h"
#include "modules/video_coding/svc/scalability_structure_key_svc.h"
#include "modules/video_coding/svc/scalability_structure_l2t2_key_shift.h"
#include "modules/video_coding/svc/scalability_structure_simulcast.h"
#include "modules/video_coding/svc/scalable_video_controller.h"
#include "modules/video_coding/svc/scalable_video_controller_no_layering.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace {

struct NamedStructureFactory {
  absl::string_view name;
  // Use function pointer to make NamedStructureFactory trivally destructable.
  std::unique_ptr<ScalableVideoController> (*factory)();
  ScalableVideoController::StreamLayersConfig config;
};

// Wrap std::make_unique function to have correct return type.
template <typename T>
std::unique_ptr<ScalableVideoController> Create() {
  return std::make_unique<T>();
}

template <typename T>
std::unique_ptr<ScalableVideoController> CreateH() {
  // 1.5:1 scaling, see https://w3c.github.io/webrtc-svc/#scalabilitymodes*
  typename T::ScalingFactor factor;
  factor.num = 2;
  factor.den = 3;
  return std::make_unique<T>(factor);
}

constexpr ScalableVideoController::StreamLayersConfig kConfigNone = {
    /*num_spatial_layers=*/1, /*num_temporal_layers=*/1,
    /*uses_reference_scaling=*/false};

constexpr ScalableVideoController::StreamLayersConfig kConfigL1T2 = {
    /*num_spatial_layers=*/1, /*num_temporal_layers=*/2,
    /*uses_reference_scaling=*/false};

constexpr ScalableVideoController::StreamLayersConfig kConfigL1T3 = {
    /*num_spatial_layers=*/1, /*num_temporal_layers=*/3,
    /*uses_reference_scaling=*/false};

constexpr ScalableVideoController::StreamLayersConfig kConfigL2T1 = {
    /*num_spatial_layers=*/2,
    /*num_temporal_layers=*/1,
    /*uses_reference_scaling=*/true,
    {1, 1},
    {2, 1}};

constexpr ScalableVideoController::StreamLayersConfig kConfigL2T1h = {
    /*num_spatial_layers=*/2,
    /*num_temporal_layers=*/1,
    /*uses_reference_scaling=*/true,
    {2, 1},
    {3, 1}};

constexpr ScalableVideoController::StreamLayersConfig kConfigL2T2 = {
    /*num_spatial_layers=*/2,
    /*num_temporal_layers=*/2,
    /*uses_reference_scaling=*/true,
    {1, 1},
    {2, 1}};

constexpr ScalableVideoController::StreamLayersConfig kConfigL2T3 = {
    /*num_spatial_layers=*/2,
    /*num_temporal_layers=*/3,
    /*uses_reference_scaling=*/true,
    {1, 1},
    {2, 1}};

constexpr ScalableVideoController::StreamLayersConfig kConfigL3T1 = {
    /*num_spatial_layers=*/3,
    /*num_temporal_layers=*/1,
    /*uses_reference_scaling=*/true,
    {1, 1, 1},
    {4, 2, 1}};

constexpr ScalableVideoController::StreamLayersConfig kConfigL3T3 = {
    /*num_spatial_layers=*/3,
    /*num_temporal_layers=*/3,
    /*uses_reference_scaling=*/true,
    {1, 1, 1},
    {4, 2, 1}};

constexpr ScalableVideoController::StreamLayersConfig kConfigS2T1 = {
    /*num_spatial_layers=*/2,
    /*num_temporal_layers=*/1,
    /*uses_reference_scaling=*/false,
    {1, 1},
    {2, 1}};

constexpr ScalableVideoController::StreamLayersConfig kConfigS3T3 = {
    /*num_spatial_layers=*/3,
    /*num_temporal_layers=*/3,
    /*uses_reference_scaling=*/false,
    {1, 1, 1},
    {4, 2, 1}};

constexpr NamedStructureFactory kFactories[] = {
    {"NONE", Create<ScalableVideoControllerNoLayering>, kConfigNone},
    {"L1T2", Create<ScalabilityStructureL1T2>, kConfigL1T2},
    {"L1T3", Create<ScalabilityStructureL1T3>, kConfigL1T3},
    {"L2T1", Create<ScalabilityStructureL2T1>, kConfigL2T1},
    {"L2T1h", CreateH<ScalabilityStructureL2T1>, kConfigL2T1h},
    {"L2T1_KEY", Create<ScalabilityStructureL2T1Key>, kConfigL2T1},
    {"L2T2", Create<ScalabilityStructureL2T2>, kConfigL2T2},
    {"L2T2_KEY", Create<ScalabilityStructureL2T2Key>, kConfigL2T2},
    {"L2T2_KEY_SHIFT", Create<ScalabilityStructureL2T2KeyShift>, kConfigL2T2},
    {"L2T3_KEY", Create<ScalabilityStructureL2T3Key>, kConfigL2T3},
    {"L3T1", Create<ScalabilityStructureL3T1>, kConfigL3T1},
    {"L3T3", Create<ScalabilityStructureL3T3>, kConfigL3T3},
    {"L3T3_KEY", Create<ScalabilityStructureL3T3Key>, kConfigL3T3},
    {"S2T1", Create<ScalabilityStructureS2T1>, kConfigS2T1},
    {"S3T3", Create<ScalabilityStructureS3T3>, kConfigS3T3},
};

}  // namespace

std::unique_ptr<ScalableVideoController> CreateScalabilityStructure(
    absl::string_view name) {
  RTC_DCHECK(!name.empty());
  for (const auto& entry : kFactories) {
    if (entry.name == name) {
      return entry.factory();
    }
  }
  return nullptr;
}

absl::optional<ScalableVideoController::StreamLayersConfig>
ScalabilityStructureConfig(absl::string_view name) {
  RTC_DCHECK(!name.empty());
  for (const auto& entry : kFactories) {
    if (entry.name == name) {
      return entry.config;
    }
  }
  return absl::nullopt;
}

}  // namespace webrtc
