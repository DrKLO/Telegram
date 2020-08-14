/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include <utility>

#include "api/test/network_emulation_manager.h"
#include "call/simulated_network.h"

namespace webrtc {

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
NetworkEmulationManager::SimulatedNetworkNode::Builder::Build() const {
  RTC_CHECK(net_);
  return Build(net_);
}

NetworkEmulationManager::SimulatedNetworkNode
NetworkEmulationManager::SimulatedNetworkNode::Builder::Build(
    NetworkEmulationManager* net) const {
  RTC_CHECK(net);
  RTC_CHECK(net_ == nullptr || net_ == net);
  SimulatedNetworkNode res;
  auto behavior = std::make_unique<SimulatedNetwork>(config_);
  res.simulation = behavior.get();
  res.node = net->CreateEmulatedNode(std::move(behavior));
  return res;
}
}  // namespace webrtc
