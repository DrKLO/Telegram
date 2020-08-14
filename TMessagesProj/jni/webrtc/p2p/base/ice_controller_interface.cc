/*
 *  Copyright 2019 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "p2p/base/ice_controller_interface.h"

#include <string>

namespace cricket {

std::string IceControllerEvent::ToString() const {
  std::string reason;
  switch (type) {
    case REMOTE_CANDIDATE_GENERATION_CHANGE:
      reason = "remote candidate generation maybe changed";
      break;
    case NETWORK_PREFERENCE_CHANGE:
      reason = "network preference changed";
      break;
    case NEW_CONNECTION_FROM_LOCAL_CANDIDATE:
      reason = "new candidate pairs created from a new local candidate";
      break;
    case NEW_CONNECTION_FROM_REMOTE_CANDIDATE:
      reason = "new candidate pairs created from a new remote candidate";
      break;
    case NEW_CONNECTION_FROM_UNKNOWN_REMOTE_ADDRESS:
      reason = "a new candidate pair created from an unknown remote address";
      break;
    case NOMINATION_ON_CONTROLLED_SIDE:
      reason = "nomination on the controlled side";
      break;
    case DATA_RECEIVED:
      reason = "data received";
      break;
    case CONNECT_STATE_CHANGE:
      reason = "candidate pair state changed";
      break;
    case SELECTED_CONNECTION_DESTROYED:
      reason = "selected candidate pair destroyed";
      break;
    case ICE_CONTROLLER_RECHECK:
      reason = "ice-controller-request-recheck";
      break;
  }
  if (recheck_delay_ms) {
    reason += " (after delay: " + std::to_string(recheck_delay_ms) + ")";
  }
  return reason;
}

}  // namespace cricket
