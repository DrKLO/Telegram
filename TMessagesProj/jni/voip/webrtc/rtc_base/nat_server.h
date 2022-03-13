/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_NAT_SERVER_H_
#define RTC_BASE_NAT_SERVER_H_

#include <map>
#include <set>

#include "rtc_base/async_udp_socket.h"
#include "rtc_base/constructor_magic.h"
#include "rtc_base/nat_types.h"
#include "rtc_base/proxy_server.h"
#include "rtc_base/socket_address_pair.h"
#include "rtc_base/socket_factory.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/thread.h"

namespace rtc {

// Change how routes (socketaddress pairs) are compared based on the type of
// NAT.  The NAT server maintains a hashtable of the routes that it knows
// about.  So these affect which routes are treated the same.
struct RouteCmp {
  explicit RouteCmp(NAT* nat);
  size_t operator()(const SocketAddressPair& r) const;
  bool operator()(const SocketAddressPair& r1,
                  const SocketAddressPair& r2) const;

  bool symmetric;
};

// Changes how addresses are compared based on the filtering rules of the NAT.
struct AddrCmp {
  explicit AddrCmp(NAT* nat);
  size_t operator()(const SocketAddress& r) const;
  bool operator()(const SocketAddress& r1, const SocketAddress& r2) const;

  bool use_ip;
  bool use_port;
};

// Implements the NAT device.  It listens for packets on the internal network,
// translates them, and sends them out over the external network.
//
// TCP connections initiated from the internal side of the NAT server are
// also supported, by making a connection to the NAT server's TCP address and
// then sending the remote address in quasi-STUN format. The connection status
// will be indicated back to the client as a 1 byte status code, where '0'
// indicates success.

const int NAT_SERVER_UDP_PORT = 4237;
const int NAT_SERVER_TCP_PORT = 4238;

class NATServer : public sigslot::has_slots<> {
 public:
  NATServer(NATType type,
            SocketFactory* internal,
            const SocketAddress& internal_udp_addr,
            const SocketAddress& internal_tcp_addr,
            SocketFactory* external,
            const SocketAddress& external_ip);
  ~NATServer() override;

  SocketAddress internal_udp_address() const {
    return udp_server_socket_->GetLocalAddress();
  }

  SocketAddress internal_tcp_address() const {
    return tcp_proxy_server_->GetServerAddress();
  }

  // Packets received on one of the networks.
  void OnInternalUDPPacket(AsyncPacketSocket* socket,
                           const char* buf,
                           size_t size,
                           const SocketAddress& addr,
                           const int64_t& packet_time_us);
  void OnExternalUDPPacket(AsyncPacketSocket* socket,
                           const char* buf,
                           size_t size,
                           const SocketAddress& remote_addr,
                           const int64_t& packet_time_us);

 private:
  typedef std::set<SocketAddress, AddrCmp> AddressSet;

  /* Records a translation and the associated external socket. */
  struct TransEntry {
    TransEntry(const SocketAddressPair& r, AsyncUDPSocket* s, NAT* nat);
    ~TransEntry();

    void AllowlistInsert(const SocketAddress& addr);
    bool AllowlistContains(const SocketAddress& ext_addr);

    SocketAddressPair route;
    AsyncUDPSocket* socket;
    AddressSet* allowlist;
    webrtc::Mutex mutex_;
  };

  typedef std::map<SocketAddressPair, TransEntry*, RouteCmp> InternalMap;
  typedef std::map<SocketAddress, TransEntry*> ExternalMap;

  /* Creates a new entry that translates the given route. */
  void Translate(const SocketAddressPair& route);

  /* Determines whether the NAT would filter out a packet from this address. */
  bool ShouldFilterOut(TransEntry* entry, const SocketAddress& ext_addr);

  NAT* nat_;
  SocketFactory* external_;
  SocketAddress external_ip_;
  AsyncUDPSocket* udp_server_socket_;
  ProxyServer* tcp_proxy_server_;
  InternalMap* int_map_;
  ExternalMap* ext_map_;
  RTC_DISALLOW_COPY_AND_ASSIGN(NATServer);
};

}  // namespace rtc

#endif  // RTC_BASE_NAT_SERVER_H_
