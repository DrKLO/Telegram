/*
 *  Copyright 2022 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef P2P_BASE_ACTIVE_ICE_CONTROLLER_FACTORY_INTERFACE_H_
#define P2P_BASE_ACTIVE_ICE_CONTROLLER_FACTORY_INTERFACE_H_

#include <memory>

#include "p2p/base/active_ice_controller_interface.h"
#include "p2p/base/ice_agent_interface.h"
#include "p2p/base/ice_controller_factory_interface.h"

namespace cricket {

// An active ICE controller may be constructed with the same arguments as a
// legacy ICE controller. Additionally, an ICE agent must be provided for the
// active ICE controller to interact with.
struct ActiveIceControllerFactoryArgs {
  IceControllerFactoryArgs legacy_args;
  IceAgentInterface* ice_agent;
};

class ActiveIceControllerFactoryInterface {
 public:
  virtual ~ActiveIceControllerFactoryInterface() = default;
  virtual std::unique_ptr<ActiveIceControllerInterface> Create(
      const ActiveIceControllerFactoryArgs&) = 0;
};

}  // namespace cricket

#endif  // P2P_BASE_ACTIVE_ICE_CONTROLLER_FACTORY_INTERFACE_H_
