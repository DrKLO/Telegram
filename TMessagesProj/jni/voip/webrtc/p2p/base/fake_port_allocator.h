/*
 *  Copyright 2010 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef P2P_BASE_FAKE_PORT_ALLOCATOR_H_
#define P2P_BASE_FAKE_PORT_ALLOCATOR_H_

#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "absl/strings/string_view.h"
#include "p2p/base/basic_packet_socket_factory.h"
#include "p2p/base/port_allocator.h"
#include "p2p/base/udp_port.h"
#include "rtc_base/memory/always_valid_pointer.h"
#include "rtc_base/net_helpers.h"
#include "rtc_base/task_queue_for_test.h"
#include "rtc_base/thread.h"
#include "test/scoped_key_value_config.h"

namespace rtc {
class SocketFactory;
}

namespace cricket {

class TestUDPPort : public UDPPort {
 public:
  static TestUDPPort* Create(rtc::Thread* thread,
                             rtc::PacketSocketFactory* factory,
                             const rtc::Network* network,
                             uint16_t min_port,
                             uint16_t max_port,
                             absl::string_view username,
                             absl::string_view password,
                             bool emit_localhost_for_anyaddress,
                             const webrtc::FieldTrialsView* field_trials) {
    TestUDPPort* port =
        new TestUDPPort(thread, factory, network, min_port, max_port, username,
                        password, emit_localhost_for_anyaddress, field_trials);
    if (!port->Init()) {
      delete port;
      port = nullptr;
    }
    return port;
  }

 protected:
  TestUDPPort(rtc::Thread* thread,
              rtc::PacketSocketFactory* factory,
              const rtc::Network* network,
              uint16_t min_port,
              uint16_t max_port,
              absl::string_view username,
              absl::string_view password,
              bool emit_localhost_for_anyaddress,
              const webrtc::FieldTrialsView* field_trials)
      : UDPPort(thread,
                factory,
                network,
                min_port,
                max_port,
                username,
                password,
                emit_localhost_for_anyaddress,
                field_trials) {}
};

// A FakePortAllocatorSession can be used with either a real or fake socket
// factory. It gathers a single loopback port, using IPv6 if available and
// not disabled.
class FakePortAllocatorSession : public PortAllocatorSession {
 public:
  FakePortAllocatorSession(PortAllocator* allocator,
                           rtc::Thread* network_thread,
                           rtc::PacketSocketFactory* factory,
                           absl::string_view content_name,
                           int component,
                           absl::string_view ice_ufrag,
                           absl::string_view ice_pwd,
                           const webrtc::FieldTrialsView& field_trials)
      : PortAllocatorSession(content_name,
                             component,
                             ice_ufrag,
                             ice_pwd,
                             allocator->flags()),
        network_thread_(network_thread),
        factory_(factory),
        ipv4_network_("network",
                      "unittest",
                      rtc::IPAddress(INADDR_LOOPBACK),
                      32),
        ipv6_network_("network",
                      "unittest",
                      rtc::IPAddress(in6addr_loopback),
                      64),
        port_(),
        port_config_count_(0),
        stun_servers_(allocator->stun_servers()),
        turn_servers_(allocator->turn_servers()),
        field_trials_(field_trials) {
    ipv4_network_.AddIP(rtc::IPAddress(INADDR_LOOPBACK));
    ipv6_network_.AddIP(rtc::IPAddress(in6addr_loopback));
  }

  void SetCandidateFilter(uint32_t filter) override {
    candidate_filter_ = filter;
  }

  void StartGettingPorts() override {
    if (!port_) {
      rtc::Network& network =
          (rtc::HasIPv6Enabled() && (flags() & PORTALLOCATOR_ENABLE_IPV6))
              ? ipv6_network_
              : ipv4_network_;
      port_.reset(TestUDPPort::Create(network_thread_, factory_, &network, 0, 0,
                                      username(), password(), false,
                                      &field_trials_));
      RTC_DCHECK(port_);
      port_->SubscribePortDestroyed(
          [this](PortInterface* port) { OnPortDestroyed(port); });
      AddPort(port_.get());
    }
    ++port_config_count_;
    running_ = true;
  }

  void StopGettingPorts() override { running_ = false; }
  bool IsGettingPorts() override { return running_; }
  void ClearGettingPorts() override { is_cleared = true; }
  bool IsCleared() const override { return is_cleared; }

  void RegatherOnFailedNetworks() override {
    SignalIceRegathering(this, IceRegatheringReason::NETWORK_FAILURE);
  }

  std::vector<PortInterface*> ReadyPorts() const override {
    return ready_ports_;
  }
  std::vector<Candidate> ReadyCandidates() const override {
    return candidates_;
  }
  void PruneAllPorts() override { port_->Prune(); }
  bool CandidatesAllocationDone() const override { return allocation_done_; }

  int port_config_count() { return port_config_count_; }

  const ServerAddresses& stun_servers() const { return stun_servers_; }

  const std::vector<RelayServerConfig>& turn_servers() const {
    return turn_servers_;
  }

  uint32_t candidate_filter() const { return candidate_filter_; }

  int transport_info_update_count() const {
    return transport_info_update_count_;
  }

 protected:
  void UpdateIceParametersInternal() override {
    // Since this class is a fake and this method only is overridden for tests,
    // we don't need to actually update the transport info.
    ++transport_info_update_count_;
  }

 private:
  void AddPort(cricket::Port* port) {
    port->set_component(component());
    port->set_generation(generation());
    port->SignalPortComplete.connect(this,
                                     &FakePortAllocatorSession::OnPortComplete);
    port->PrepareAddress();
    ready_ports_.push_back(port);
    SignalPortReady(this, port);
    port->KeepAliveUntilPruned();
  }
  void OnPortComplete(cricket::Port* port) {
    const std::vector<Candidate>& candidates = port->Candidates();
    candidates_.insert(candidates_.end(), candidates.begin(), candidates.end());
    SignalCandidatesReady(this, candidates);

    allocation_done_ = true;
    SignalCandidatesAllocationDone(this);
  }
  void OnPortDestroyed(cricket::PortInterface* port) {
    // Don't want to double-delete port if it deletes itself.
    port_.release();
  }

  rtc::Thread* network_thread_;
  rtc::PacketSocketFactory* factory_;
  rtc::Network ipv4_network_;
  rtc::Network ipv6_network_;
  std::unique_ptr<cricket::Port> port_;
  int port_config_count_;
  std::vector<Candidate> candidates_;
  std::vector<PortInterface*> ready_ports_;
  bool allocation_done_ = false;
  bool is_cleared = false;
  ServerAddresses stun_servers_;
  std::vector<RelayServerConfig> turn_servers_;
  uint32_t candidate_filter_ = CF_ALL;
  int transport_info_update_count_ = 0;
  bool running_ = false;
  const webrtc::FieldTrialsView& field_trials_;
};

class FakePortAllocator : public cricket::PortAllocator {
 public:
  FakePortAllocator(rtc::Thread* network_thread,
                    rtc::PacketSocketFactory* factory)
      : FakePortAllocator(network_thread, factory, nullptr) {}

  FakePortAllocator(rtc::Thread* network_thread,
                    std::unique_ptr<rtc::PacketSocketFactory> factory)
      : FakePortAllocator(network_thread, nullptr, std::move(factory)) {}

  void SetNetworkIgnoreMask(int network_ignore_mask) override {}

  cricket::PortAllocatorSession* CreateSessionInternal(
      absl::string_view content_name,
      int component,
      absl::string_view ice_ufrag,
      absl::string_view ice_pwd) override {
    return new FakePortAllocatorSession(
        this, network_thread_, factory_.get(), std::string(content_name),
        component, std::string(ice_ufrag), std::string(ice_pwd), field_trials_);
  }

  bool initialized() const { return initialized_; }

  // For testing: Manipulate MdnsObfuscationEnabled()
  bool MdnsObfuscationEnabled() const override {
    return mdns_obfuscation_enabled_;
  }
  void SetMdnsObfuscationEnabledForTesting(bool enabled) {
    mdns_obfuscation_enabled_ = enabled;
  }

 private:
  FakePortAllocator(rtc::Thread* network_thread,
                    rtc::PacketSocketFactory* factory,
                    std::unique_ptr<rtc::PacketSocketFactory> owned_factory)
      : network_thread_(network_thread),
        factory_(std::move(owned_factory), factory) {
    if (network_thread_ == nullptr) {
      network_thread_ = rtc::Thread::Current();
      Initialize();
      return;
    }
    SendTask(network_thread_, [this] { Initialize(); });
  }

  webrtc::test::ScopedKeyValueConfig field_trials_;
  rtc::Thread* network_thread_;
  const webrtc::AlwaysValidPointerNoDefault<rtc::PacketSocketFactory> factory_;
  bool mdns_obfuscation_enabled_ = false;
};

}  // namespace cricket

#endif  // P2P_BASE_FAKE_PORT_ALLOCATOR_H_
