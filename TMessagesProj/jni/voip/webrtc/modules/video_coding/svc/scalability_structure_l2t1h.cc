/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/video_coding/svc/scalability_structure_l2t1h.h"

#include <utility>
#include <vector>

#include "absl/base/macros.h"
#include "api/transport/rtp/dependency_descriptor.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {

ScalabilityStructureL2T1h::~ScalabilityStructureL2T1h() = default;

ScalableVideoController::StreamLayersConfig
ScalabilityStructureL2T1h::StreamConfig() const {
  StreamLayersConfig result;
  result.num_spatial_layers = 2;
  result.num_temporal_layers = 1;
  // 1.5:1 scaling, see https://w3c.github.io/webrtc-svc/#scalabilitymodes*
  result.scaling_factor_num[0] = 2;
  result.scaling_factor_den[0] = 3;
  return result;
}

}  // namespace webrtc
