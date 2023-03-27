/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "api/test/network_emulation_manager.h"

#include <utility>

#include "call/simulated_network.h"
#include "rtc_base/checks.h"

namespace webrtc {

bool AbslParseFlag(absl::string_view text, TimeMode* mode, std::string* error) {
  if (text == "realtime") {
    *mode = TimeMode::kRealTime;
    return true;
  }
  if (text == "simulated") {
    *mode = TimeMode::kSimulated;
    return true;
  }
  *error =
      "Unknown value for TimeMode enum. Options are 'realtime' or 'simulated'";
  return false;
}

std::string AbslUnparseFlag(TimeMode mode) {
  switch (mode) {
    case TimeMode::kRealTime:
      return "realtime";
    case TimeMode::kSimulated:
      return "simulated";
  }
  RTC_CHECK_NOTREACHED();
  return "unknown";
}

NetworkEmulationManager::SimulatedNetworkNode::Builder&
NetworkEmulationManager::SimulatedNetworkNode::Builder::config(
    BuiltInNetworkBehaviorConfig config) {
  config_ = config;
  return *this;
}

NetworkEmulationManager::SimulatedNetworkNode::Builder&
NetworkEmulationManager::SimulatedNetworkNode::Builder::delay_ms(
    int queue_delay_ms) {
  config_.queue_delay_ms = queue_delay_ms;
  return *this;
}

NetworkEmulationManager::SimulatedNetworkNode::Builder&
NetworkEmulationManager::SimulatedNetworkNode::Builder::capacity_kbps(
    int link_capacity_kbps) {
  config_.link_capacity_kbps = link_capacity_kbps;
  return *this;
}

NetworkEmulationManager::SimulatedNetworkNode::Builder&
NetworkEmulationManager::SimulatedNetworkNode::Builder::capacity_Mbps(
    int link_capacity_Mbps) {
  config_.link_capacity_kbps = link_capacity_Mbps * 1000;
  return *this;
}

NetworkEmulationManager::SimulatedNetworkNode::Builder&
NetworkEmulationManager::SimulatedNetworkNode::Builder::loss(double loss_rate) {
  config_.loss_percent = std::round(loss_rate * 100);
  return *this;
}

NetworkEmulationManager::SimulatedNetworkNode::Builder&
NetworkEmulationManager::SimulatedNetworkNode::Builder::packet_queue_length(
    int max_queue_length_in_packets) {
  config_.queue_length_packets = max_queue_length_in_packets;
  return *this;
}

NetworkEmulationManager::SimulatedNetworkNode
NetworkEmulationManager::SimulatedNetworkNode::Builder::Build(
    uint64_t random_seed) const {
  RTC_CHECK(net_);
  return Build(net_, random_seed);
}

NetworkEmulationManager::SimulatedNetworkNode
NetworkEmulationManager::SimulatedNetworkNode::Builder::Build(
    NetworkEmulationManager* net,
    uint64_t random_seed) const {
  RTC_CHECK(net);
  RTC_CHECK(net_ == nullptr || net_ == net);
  SimulatedNetworkNode res;
  auto behavior = std::make_unique<SimulatedNetwork>(config_, random_seed);
  res.simulation = behavior.get();
  res.node = net->CreateEmulatedNode(std::move(behavior));
  return res;
}

std::pair<EmulatedNetworkManagerInterface*, EmulatedNetworkManagerInterface*>
NetworkEmulationManager::CreateEndpointPairWithTwoWayRoutes(
    const BuiltInNetworkBehaviorConfig& config) {
  auto* alice_node = CreateEmulatedNode(config);
  auto* bob_node = CreateEmulatedNode(config);

  auto* alice_endpoint = CreateEndpoint(EmulatedEndpointConfig());
  auto* bob_endpoint = CreateEndpoint(EmulatedEndpointConfig());

  CreateRoute(alice_endpoint, {alice_node}, bob_endpoint);
  CreateRoute(bob_endpoint, {bob_node}, alice_endpoint);

  return {
      CreateEmulatedNetworkManagerInterface({alice_endpoint}),
      CreateEmulatedNetworkManagerInterface({bob_endpoint}),
  };
}
}  // namespace webrtc
