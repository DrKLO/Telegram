/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/nat_server.h"

#include <cstddef>
#include <cstdint>
#include <memory>

#include "api/array_view.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/nat_socket_factory.h"
#include "rtc_base/network/received_packet.h"
#include "rtc_base/socket_adapters.h"
#include "rtc_base/socket_address.h"

namespace rtc {

RouteCmp::RouteCmp(NAT* nat) : symmetric(nat->IsSymmetric()) {}

size_t RouteCmp::operator()(const SocketAddressPair& r) const {
  size_t h = r.source().Hash();
  if (symmetric)
    h ^= r.destination().Hash();
  return h;
}

bool RouteCmp::operator()(const SocketAddressPair& r1,
                          const SocketAddressPair& r2) const {
  if (r1.source() < r2.source())
    return true;
  if (r2.source() < r1.source())
    return false;
  if (symmetric && (r1.destination() < r2.destination()))
    return true;
  if (symmetric && (r2.destination() < r1.destination()))
    return false;
  return false;
}

AddrCmp::AddrCmp(NAT* nat)
    : use_ip(nat->FiltersIP()), use_port(nat->FiltersPort()) {}

size_t AddrCmp::operator()(const SocketAddress& a) const {
  size_t h = 0;
  if (use_ip)
    h ^= HashIP(a.ipaddr());
  if (use_port)
    h ^= a.port() | (a.port() << 16);
  return h;
}

bool AddrCmp::operator()(const SocketAddress& a1,
                         const SocketAddress& a2) const {
  if (use_ip && (a1.ipaddr() < a2.ipaddr()))
    return true;
  if (use_ip && (a2.ipaddr() < a1.ipaddr()))
    return false;
  if (use_port && (a1.port() < a2.port()))
    return true;
  if (use_port && (a2.port() < a1.port()))
    return false;
  return false;
}

// Proxy socket that will capture the external destination address intended for
// a TCP connection to the NAT server.
class NATProxyServerSocket : public AsyncProxyServerSocket {
 public:
  NATProxyServerSocket(Socket* socket)
      : AsyncProxyServerSocket(socket, kNATEncodedIPv6AddressSize) {
    BufferInput(true);
  }

  void SendConnectResult(int err, const SocketAddress& addr) override {
    char code = err ? 1 : 0;
    BufferedReadAdapter::DirectSend(&code, sizeof(char));
  }

 protected:
  void ProcessInput(char* data, size_t* len) override {
    if (*len < 2) {
      return;
    }

    int family = data[1];
    RTC_DCHECK(family == AF_INET || family == AF_INET6);
    if ((family == AF_INET && *len < kNATEncodedIPv4AddressSize) ||
        (family == AF_INET6 && *len < kNATEncodedIPv6AddressSize)) {
      return;
    }

    SocketAddress dest_addr;
    size_t address_length = UnpackAddressFromNAT(
        MakeArrayView(reinterpret_cast<const uint8_t*>(data), *len),
        &dest_addr);
    *len -= address_length;
    if (*len > 0) {
      memmove(data, data + address_length, *len);
    }

    bool remainder = (*len > 0);
    BufferInput(false);
    SignalConnectRequest(this, dest_addr);
    if (remainder) {
      SignalReadEvent(this);
    }
  }
};

class NATProxyServer : public ProxyServer {
 public:
  NATProxyServer(SocketFactory* int_factory,
                 const SocketAddress& int_addr,
                 SocketFactory* ext_factory,
                 const SocketAddress& ext_ip)
      : ProxyServer(int_factory, int_addr, ext_factory, ext_ip) {}

 protected:
  AsyncProxyServerSocket* WrapSocket(Socket* socket) override {
    return new NATProxyServerSocket(socket);
  }
};

NATServer::NATServer(NATType type,
                     rtc::Thread& internal_socket_thread,
                     SocketFactory* internal,
                     const SocketAddress& internal_udp_addr,
                     const SocketAddress& internal_tcp_addr,
                     rtc::Thread& external_socket_thread,
                     SocketFactory* external,
                     const SocketAddress& external_ip)
    : internal_socket_thread_(internal_socket_thread),
      external_socket_thread_(external_socket_thread),
      external_(external),
      external_ip_(external_ip.ipaddr(), 0) {
  nat_ = NAT::Create(type);

  internal_socket_thread_.BlockingCall([&] {
    udp_server_socket_ = AsyncUDPSocket::Create(internal, internal_udp_addr);
    udp_server_socket_->RegisterReceivedPacketCallback(
        [&](rtc::AsyncPacketSocket* socket, const rtc::ReceivedPacket& packet) {
          OnInternalUDPPacket(socket, packet);
        });
  });

  tcp_proxy_server_ =
      new NATProxyServer(internal, internal_tcp_addr, external, external_ip);

  int_map_ = new InternalMap(RouteCmp(nat_));
  ext_map_ = new ExternalMap();
}

NATServer::~NATServer() {
  for (InternalMap::iterator iter = int_map_->begin(); iter != int_map_->end();
       iter++)
    delete iter->second;

  delete nat_;
  delete udp_server_socket_;
  delete tcp_proxy_server_;
  delete int_map_;
  delete ext_map_;
}

void NATServer::OnInternalUDPPacket(AsyncPacketSocket* socket,
                                    const rtc::ReceivedPacket& packet) {
  RTC_DCHECK(internal_socket_thread_.IsCurrent());
  // Read the intended destination from the wire.
  SocketAddress dest_addr;
  size_t length = UnpackAddressFromNAT(packet.payload(), &dest_addr);

  // Find the translation for these addresses (allocating one if necessary).
  SocketAddressPair route(packet.source_address(), dest_addr);
  InternalMap::iterator iter = int_map_->find(route);
  if (iter == int_map_->end()) {
    Translate(route);
    iter = int_map_->find(route);
  }
  RTC_DCHECK(iter != int_map_->end());

  // Allow the destination to send packets back to the source.
  iter->second->AllowlistInsert(dest_addr);

  // Send the packet to its intended destination.
  rtc::PacketOptions options;
  const char* buf = reinterpret_cast<const char*>(packet.payload().data());
  size_t size = packet.payload().size();
  iter->second->socket->SendTo(buf + length, size - length, dest_addr, options);
}

void NATServer::OnExternalUDPPacket(AsyncPacketSocket* socket,
                                    const rtc::ReceivedPacket& packet) {
  RTC_DCHECK(external_socket_thread_.IsCurrent());
  SocketAddress local_addr = socket->GetLocalAddress();

  // Find the translation for this addresses.
  ExternalMap::iterator iter = ext_map_->find(local_addr);
  RTC_DCHECK(iter != ext_map_->end());

  // Allow the NAT to reject this packet.
  if (ShouldFilterOut(iter->second, packet.source_address())) {
    RTC_LOG(LS_INFO) << "Packet from "
                     << packet.source_address().ToSensitiveString()
                     << " was filtered out by the NAT.";
    return;
  }

  // Forward this packet to the internal address.
  // First prepend the address in a quasi-STUN format.
  std::unique_ptr<char[]> real_buf(
      new char[packet.payload().size() + kNATEncodedIPv6AddressSize]);
  size_t addrlength = PackAddressForNAT(
      real_buf.get(), packet.payload().size() + kNATEncodedIPv6AddressSize,
      packet.source_address());
  // Copy the data part after the address.
  rtc::PacketOptions options;
  memcpy(real_buf.get() + addrlength, packet.payload().data(),
         packet.payload().size());
  udp_server_socket_->SendTo(real_buf.get(),
                             packet.payload().size() + addrlength,
                             iter->second->route.source(), options);
}

void NATServer::Translate(const SocketAddressPair& route) {
  external_socket_thread_.BlockingCall([&] {
    AsyncUDPSocket* socket = AsyncUDPSocket::Create(external_, external_ip_);

    if (!socket) {
      RTC_LOG(LS_ERROR) << "Couldn't find a free port!";
      return;
    }

    TransEntry* entry = new TransEntry(route, socket, nat_);
    (*int_map_)[route] = entry;
    (*ext_map_)[socket->GetLocalAddress()] = entry;
    socket->RegisterReceivedPacketCallback(
        [&](rtc::AsyncPacketSocket* socket, const rtc::ReceivedPacket& packet) {
          OnExternalUDPPacket(socket, packet);
        });
  });
}

bool NATServer::ShouldFilterOut(TransEntry* entry,
                                const SocketAddress& ext_addr) {
  return entry->AllowlistContains(ext_addr);
}

NATServer::TransEntry::TransEntry(const SocketAddressPair& r,
                                  AsyncUDPSocket* s,
                                  NAT* nat)
    : route(r), socket(s) {
  allowlist = new AddressSet(AddrCmp(nat));
}

NATServer::TransEntry::~TransEntry() {
  delete allowlist;
  delete socket;
}

void NATServer::TransEntry::AllowlistInsert(const SocketAddress& addr) {
  webrtc::MutexLock lock(&mutex_);
  allowlist->insert(addr);
}

bool NATServer::TransEntry::AllowlistContains(const SocketAddress& ext_addr) {
  webrtc::MutexLock lock(&mutex_);
  return allowlist->find(ext_addr) == allowlist->end();
}

}  // namespace rtc
