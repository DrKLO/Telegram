/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_TEST_PEER_NETWORK_DEPENDENCIES_H_
#define API_TEST_PEER_NETWORK_DEPENDENCIES_H_

#include "api/packet_socket_factory.h"
#include "rtc_base/network.h"
#include "rtc_base/thread.h"

namespace webrtc {
namespace webrtc_pc_e2e {

// The network dependencies needed when adding a peer to tests using
// PeerConnectionE2EQualityTestFixture.
struct PeerNetworkDependencies {
  rtc::Thread* network_thread;
  rtc::NetworkManager* network_manager;
  rtc::PacketSocketFactory* packet_socket_factory;
};

}  // namespace webrtc_pc_e2e
}  // namespace webrtc

#endif  // API_TEST_PEER_NETWORK_DEPENDENCIES_H_
