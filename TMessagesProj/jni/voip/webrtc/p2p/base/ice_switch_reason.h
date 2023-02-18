/*
 *  Copyright 2022 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef P2P_BASE_ICE_SWITCH_REASON_H_
#define P2P_BASE_ICE_SWITCH_REASON_H_

#include <string>

namespace cricket {

enum class IceSwitchReason {
  REMOTE_CANDIDATE_GENERATION_CHANGE,
  NETWORK_PREFERENCE_CHANGE,
  NEW_CONNECTION_FROM_LOCAL_CANDIDATE,
  NEW_CONNECTION_FROM_REMOTE_CANDIDATE,
  NEW_CONNECTION_FROM_UNKNOWN_REMOTE_ADDRESS,
  NOMINATION_ON_CONTROLLED_SIDE,
  DATA_RECEIVED,
  CONNECT_STATE_CHANGE,
  SELECTED_CONNECTION_DESTROYED,
  // The ICE_CONTROLLER_RECHECK enum value lets an IceController request
  // P2PTransportChannel to recheck a switch periodically without an event
  // taking place.
  ICE_CONTROLLER_RECHECK,
};

std::string IceSwitchReasonToString(IceSwitchReason reason);

}  // namespace cricket

#endif  // P2P_BASE_ICE_SWITCH_REASON_H_
