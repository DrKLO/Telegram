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

constexpr NamedStructureFactory kFactories[] = {
    {"NONE", Create<ScalableVideoControllerNoLayering>},
    {"L1T2", Create<ScalabilityStructureL1T2>},
    {"L1T3", Create<ScalabilityStructureL1T3>},
    {"L2T1", Create<ScalabilityStructureL2T1>},
    {"L2T1h", CreateH<ScalabilityStructureL2T1>},
    {"L2T1_KEY", Create<ScalabilityStructureL2T1Key>},
    {"L2T2", Create<ScalabilityStructureL2T2>},
    {"L2T2_KEY", Create<ScalabilityStructureL2T2Key>},
    {"L2T2_KEY_SHIFT", Create<ScalabilityStructureL2T2KeyShift>},
    {"L2T3_KEY", Create<ScalabilityStructureL2T3Key>},
    {"L3T1", Create<ScalabilityStructureL3T1>},
    {"L3T3", Create<ScalabilityStructureL3T3>},
    {"L3T3_KEY", Create<ScalabilityStructureL3T3Key>},
    {"S2T1", Create<ScalabilityStructureS2T1>},
    {"S3T3", Create<ScalabilityStructureS3T3>},
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

}  // namespace webrtc
