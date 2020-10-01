/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/congestion_controller/pcc/pcc_factory.h"

#include <memory>

#include "modules/congestion_controller/pcc/pcc_network_controller.h"

namespace webrtc {

PccNetworkControllerFactory::PccNetworkControllerFactory() {}

std::unique_ptr<NetworkControllerInterface> PccNetworkControllerFactory::Create(
    NetworkControllerConfig config) {
  return std::make_unique<pcc::PccNetworkController>(config);
}

TimeDelta PccNetworkControllerFactory::GetProcessInterval() const {
  return TimeDelta::PlusInfinity();
}

}  // namespace webrtc
