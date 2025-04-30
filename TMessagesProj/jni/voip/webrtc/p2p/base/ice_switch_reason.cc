/*
 *  Copyright 2022 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "p2p/base/ice_switch_reason.h"

#include <string>

namespace cricket {

std::string IceSwitchReasonToString(IceSwitchReason reason) {
  switch (reason) {
    case IceSwitchReason::REMOTE_CANDIDATE_GENERATION_CHANGE:
      return "remote candidate generation maybe changed";
    case IceSwitchReason::NETWORK_PREFERENCE_CHANGE:
      return "network preference changed";
    case IceSwitchReason::NEW_CONNECTION_FROM_LOCAL_CANDIDATE:
      return "new candidate pairs created from a new local candidate";
    case IceSwitchReason::NEW_CONNECTION_FROM_REMOTE_CANDIDATE:
      return "new candidate pairs created from a new remote candidate";
    case IceSwitchReason::NEW_CONNECTION_FROM_UNKNOWN_REMOTE_ADDRESS:
      return "a new candidate pair created from an unknown remote address";
    case IceSwitchReason::NOMINATION_ON_CONTROLLED_SIDE:
      return "nomination on the controlled side";
    case IceSwitchReason::DATA_RECEIVED:
      return "data received";
    case IceSwitchReason::CONNECT_STATE_CHANGE:
      return "candidate pair state changed";
    case IceSwitchReason::SELECTED_CONNECTION_DESTROYED:
      return "selected candidate pair destroyed";
    case IceSwitchReason::ICE_CONTROLLER_RECHECK:
      return "ice-controller-request-recheck";
    case IceSwitchReason::APPLICATION_REQUESTED:
      return "application requested";
    case IceSwitchReason::UNKNOWN:
    default:
      return "unknown";
  }
}

}  // namespace cricket
