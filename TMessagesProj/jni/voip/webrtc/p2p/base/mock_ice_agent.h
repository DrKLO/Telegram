/*
 *  Copyright 2018 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef P2P_BASE_MOCK_ICE_AGENT_H_
#define P2P_BASE_MOCK_ICE_AGENT_H_

#include <vector>

#include "p2p/base/connection.h"
#include "p2p/base/ice_agent_interface.h"
#include "p2p/base/ice_switch_reason.h"
#include "p2p/base/transport_description.h"
#include "test/gmock.h"

namespace cricket {

class MockIceAgent : public IceAgentInterface {
 public:
  ~MockIceAgent() override = default;

  MOCK_METHOD(int64_t, GetLastPingSentMs, (), (override, const));
  MOCK_METHOD(IceRole, GetIceRole, (), (override, const));
  MOCK_METHOD(void, OnStartedPinging, (), (override));
  MOCK_METHOD(void, UpdateConnectionStates, (), (override));
  MOCK_METHOD(void, UpdateState, (), (override));
  MOCK_METHOD(void,
              ForgetLearnedStateForConnections,
              (rtc::ArrayView<const Connection* const>),
              (override));
  MOCK_METHOD(void, SendPingRequest, (const Connection*), (override));
  MOCK_METHOD(void,
              SwitchSelectedConnection,
              (const Connection*, IceSwitchReason),
              (override));
  MOCK_METHOD(bool,
              PruneConnections,
              (rtc::ArrayView<const Connection* const>),
              (override));
};

}  // namespace cricket

#endif  // P2P_BASE_MOCK_ICE_AGENT_H_
