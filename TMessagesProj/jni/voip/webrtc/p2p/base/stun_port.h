/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef P2P_BASE_STUN_PORT_H_
#define P2P_BASE_STUN_PORT_H_

#include <functional>
#include <map>
#include <memory>
#include <string>

#include "absl/memory/memory.h"
#include "p2p/base/port.h"
#include "p2p/base/stun_request.h"
#include "rtc_base/async_packet_socket.h"
#include "rtc_base/task_utils/pending_task_safety_flag.h"

namespace cricket {

// Lifetime chosen for STUN ports on low-cost networks.
static const int INFINITE_LIFETIME = -1;
// Lifetime for STUN ports on high-cost networks: 2 minutes
static const int HIGH_COST_PORT_KEEPALIVE_LIFETIME = 2 * 60 * 1000;

// Communicates using the address on the outside of a NAT.
class UDPPort : public Port {
 public:
  static std::unique_ptr<UDPPort> Create(
      rtc::Thread* thread,
      rtc::PacketSocketFactory* factory,
      rtc::Network* network,
      rtc::AsyncPacketSocket* socket,
      const std::string& username,
      const std::string& password,
      bool emit_local_for_anyaddress,
      absl::optional<int> stun_keepalive_interval) {
    // Using `new` to access a non-public constructor.
    auto port =
        absl::WrapUnique(new UDPPort(thread, factory, network, socket, username,
                                     password, emit_local_for_anyaddress));
    port->set_stun_keepalive_delay(stun_keepalive_interval);
    if (!port->Init()) {
      return nullptr;
    }
    return port;
  }

  static std::unique_ptr<UDPPort> Create(
      rtc::Thread* thread,
      rtc::PacketSocketFactory* factory,
      rtc::Network* network,
      uint16_t min_port,
      uint16_t max_port,
      const std::string& username,
      const std::string& password,
      bool emit_local_for_anyaddress,
      absl::optional<int> stun_keepalive_interval) {
    // Using `new` to access a non-public constructor.
    auto port = absl::WrapUnique(new UDPPort(thread, factory, network, min_port,
                                             max_port, username, password,
                                             emit_local_for_anyaddress));
    port->set_stun_keepalive_delay(stun_keepalive_interval);
    if (!port->Init()) {
      return nullptr;
    }
    return port;
  }

  ~UDPPort() override;

  rtc::SocketAddress GetLocalAddress() const {
    return socket_->GetLocalAddress();
  }

  const ServerAddresses& server_addresses() const { return server_addresses_; }
  void set_server_addresses(const ServerAddresses& addresses) {
    server_addresses_ = addresses;
  }

  void PrepareAddress() override;

  Connection* CreateConnection(const Candidate& address,
                               CandidateOrigin origin) override;
  int SetOption(rtc::Socket::Option opt, int value) override;
  int GetOption(rtc::Socket::Option opt, int* value) override;
  int GetError() override;

  bool HandleIncomingPacket(rtc::AsyncPacketSocket* socket,
                            const char* data,
                            size_t size,
                            const rtc::SocketAddress& remote_addr,
                            int64_t packet_time_us) override;

  bool SupportsProtocol(const std::string& protocol) const override;
  ProtocolType GetProtocol() const override;

  void GetStunStats(absl::optional<StunStats>* stats) override;

  void set_stun_keepalive_delay(const absl::optional<int>& delay);
  int stun_keepalive_delay() const { return stun_keepalive_delay_; }

  // Visible for testing.
  int stun_keepalive_lifetime() const { return stun_keepalive_lifetime_; }
  void set_stun_keepalive_lifetime(int lifetime) {
    stun_keepalive_lifetime_ = lifetime;
  }
  // Returns true if there is a pending request with type `msg_type`.
  bool HasPendingRequest(int msg_type) {
    return requests_.HasRequest(msg_type);
  }

 protected:
  UDPPort(rtc::Thread* thread,
          rtc::PacketSocketFactory* factory,
          rtc::Network* network,
          uint16_t min_port,
          uint16_t max_port,
          const std::string& username,
          const std::string& password,
          bool emit_local_for_anyaddress);

  UDPPort(rtc::Thread* thread,
          rtc::PacketSocketFactory* factory,
          rtc::Network* network,
          rtc::AsyncPacketSocket* socket,
          const std::string& username,
          const std::string& password,
          bool emit_local_for_anyaddress);

  bool Init();

  int SendTo(const void* data,
             size_t size,
             const rtc::SocketAddress& addr,
             const rtc::PacketOptions& options,
             bool payload) override;

  void UpdateNetworkCost() override;

  rtc::DiffServCodePoint StunDscpValue() const override;

  void OnLocalAddressReady(rtc::AsyncPacketSocket* socket,
                           const rtc::SocketAddress& address);

  void PostAddAddress(bool is_final) override;

  void OnReadPacket(rtc::AsyncPacketSocket* socket,
                    const char* data,
                    size_t size,
                    const rtc::SocketAddress& remote_addr,
                    const int64_t& packet_time_us);

  void OnSentPacket(rtc::AsyncPacketSocket* socket,
                    const rtc::SentPacket& sent_packet) override;

  void OnReadyToSend(rtc::AsyncPacketSocket* socket);

  // This method will send STUN binding request if STUN server address is set.
  void MaybePrepareStunCandidate();

  void SendStunBindingRequests();

  // Helper function which will set `addr`'s IP to the default local address if
  // `addr` is the "any" address and `emit_local_for_anyaddress_` is true. When
  // returning false, it indicates that the operation has failed and the
  // address shouldn't be used by any candidate.
  bool MaybeSetDefaultLocalAddress(rtc::SocketAddress* addr) const;

 private:
  // A helper class which can be called repeatedly to resolve multiple
  // addresses, as opposed to rtc::AsyncDnsResolverInterface, which can only
  // resolve one address per instance.
  class AddressResolver {
   public:
    explicit AddressResolver(
        rtc::PacketSocketFactory* factory,
        std::function<void(const rtc::SocketAddress&, int)> done_callback);

    void Resolve(const rtc::SocketAddress& address);
    bool GetResolvedAddress(const rtc::SocketAddress& input,
                            int family,
                            rtc::SocketAddress* output) const;

   private:
    typedef std::map<rtc::SocketAddress,
                     std::unique_ptr<webrtc::AsyncDnsResolverInterface>>
        ResolverMap;

    rtc::PacketSocketFactory* socket_factory_;
    // The function is called when resolving the specified address is finished.
    // The first argument is the input address, the second argument is the error
    // or 0 if it succeeded.
    std::function<void(const rtc::SocketAddress&, int)> done_;
    // Resolver may fire callbacks that refer to done_, so ensure
    // that all resolvers are destroyed first.
    ResolverMap resolvers_;
  };

  // DNS resolution of the STUN server.
  void ResolveStunAddress(const rtc::SocketAddress& stun_addr);
  void OnResolveResult(const rtc::SocketAddress& input, int error);

  void SendStunBindingRequest(const rtc::SocketAddress& stun_addr);

  // Below methods handles binding request responses.
  void OnStunBindingRequestSucceeded(
      int rtt_ms,
      const rtc::SocketAddress& stun_server_addr,
      const rtc::SocketAddress& stun_reflected_addr);
  void OnStunBindingOrResolveRequestFailed(
      const rtc::SocketAddress& stun_server_addr,
      int error_code,
      const std::string& reason);

  // Sends STUN requests to the server.
  void OnSendPacket(const void* data, size_t size, StunRequest* req);

  // TODO(mallinaht) - Move this up to cricket::Port when SignalAddressReady is
  // changed to SignalPortReady.
  void MaybeSetPortCompleteOrError();

  bool HasCandidateWithAddress(const rtc::SocketAddress& addr) const;

  // If this is a low-cost network, it will keep on sending STUN binding
  // requests indefinitely to keep the NAT binding alive. Otherwise, stop
  // sending STUN binding requests after HIGH_COST_PORT_KEEPALIVE_LIFETIME.
  int GetStunKeepaliveLifetime() {
    return (network_cost() >= rtc::kNetworkCostHigh)
               ? HIGH_COST_PORT_KEEPALIVE_LIFETIME
               : INFINITE_LIFETIME;
  }

  ServerAddresses server_addresses_;
  ServerAddresses bind_request_succeeded_servers_;
  ServerAddresses bind_request_failed_servers_;
  StunRequestManager requests_;
  rtc::AsyncPacketSocket* socket_;
  int error_;
  int send_error_count_ = 0;
  std::unique_ptr<AddressResolver> resolver_;
  bool ready_;
  int stun_keepalive_delay_;
  int stun_keepalive_lifetime_ = INFINITE_LIFETIME;
  rtc::DiffServCodePoint dscp_;

  StunStats stats_;

  // This is true by default and false when
  // PORTALLOCATOR_DISABLE_DEFAULT_LOCAL_CANDIDATE is specified.
  bool emit_local_for_anyaddress_;

  friend class StunBindingRequest;
};

class StunPort : public UDPPort {
 public:
  static std::unique_ptr<StunPort> Create(
      rtc::Thread* thread,
      rtc::PacketSocketFactory* factory,
      rtc::Network* network,
      uint16_t min_port,
      uint16_t max_port,
      const std::string& username,
      const std::string& password,
      const ServerAddresses& servers,
      absl::optional<int> stun_keepalive_interval);

  void PrepareAddress() override;

 protected:
  StunPort(rtc::Thread* thread,
           rtc::PacketSocketFactory* factory,
           rtc::Network* network,
           uint16_t min_port,
           uint16_t max_port,
           const std::string& username,
           const std::string& password,
           const ServerAddresses& servers);
};

}  // namespace cricket

#endif  // P2P_BASE_STUN_PORT_H_
