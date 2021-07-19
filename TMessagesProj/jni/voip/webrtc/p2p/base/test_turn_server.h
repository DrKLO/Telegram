/*
 *  Copyright 2012 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef P2P_BASE_TEST_TURN_SERVER_H_
#define P2P_BASE_TEST_TURN_SERVER_H_

#include <string>
#include <vector>

#include "api/sequence_checker.h"
#include "api/transport/stun.h"
#include "p2p/base/basic_packet_socket_factory.h"
#include "p2p/base/turn_server.h"
#include "rtc_base/async_udp_socket.h"
#include "rtc_base/ssl_adapter.h"
#include "rtc_base/ssl_identity.h"
#include "rtc_base/thread.h"

namespace cricket {

static const char kTestRealm[] = "example.org";
static const char kTestSoftware[] = "TestTurnServer";

class TestTurnRedirector : public TurnRedirectInterface {
 public:
  explicit TestTurnRedirector(const std::vector<rtc::SocketAddress>& addresses)
      : alternate_server_addresses_(addresses),
        iter_(alternate_server_addresses_.begin()) {}

  virtual bool ShouldRedirect(const rtc::SocketAddress&,
                              rtc::SocketAddress* out) {
    if (!out || iter_ == alternate_server_addresses_.end()) {
      return false;
    }
    *out = *iter_++;
    return true;
  }

 private:
  const std::vector<rtc::SocketAddress>& alternate_server_addresses_;
  std::vector<rtc::SocketAddress>::const_iterator iter_;
};

class TestTurnServer : public TurnAuthInterface {
 public:
  TestTurnServer(rtc::Thread* thread,
                 const rtc::SocketAddress& int_addr,
                 const rtc::SocketAddress& udp_ext_addr,
                 ProtocolType int_protocol = PROTO_UDP,
                 bool ignore_bad_cert = true,
                 const std::string& common_name = "test turn server")
      : server_(thread), thread_(thread) {
    AddInternalSocket(int_addr, int_protocol, ignore_bad_cert, common_name);
    server_.SetExternalSocketFactory(new rtc::BasicPacketSocketFactory(thread),
                                     udp_ext_addr);
    server_.set_realm(kTestRealm);
    server_.set_software(kTestSoftware);
    server_.set_auth_hook(this);
  }

  ~TestTurnServer() { RTC_DCHECK(thread_checker_.IsCurrent()); }

  void set_enable_otu_nonce(bool enable) {
    RTC_DCHECK(thread_checker_.IsCurrent());
    server_.set_enable_otu_nonce(enable);
  }

  TurnServer* server() {
    RTC_DCHECK(thread_checker_.IsCurrent());
    return &server_;
  }

  void set_redirect_hook(TurnRedirectInterface* redirect_hook) {
    RTC_DCHECK(thread_checker_.IsCurrent());
    server_.set_redirect_hook(redirect_hook);
  }

  void set_enable_permission_checks(bool enable) {
    RTC_DCHECK(thread_checker_.IsCurrent());
    server_.set_enable_permission_checks(enable);
  }

  void AddInternalSocket(const rtc::SocketAddress& int_addr,
                         ProtocolType proto,
                         bool ignore_bad_cert = true,
                         const std::string& common_name = "test turn server") {
    RTC_DCHECK(thread_checker_.IsCurrent());
    if (proto == cricket::PROTO_UDP) {
      server_.AddInternalSocket(
          rtc::AsyncUDPSocket::Create(thread_->socketserver(), int_addr),
          proto);
    } else if (proto == cricket::PROTO_TCP || proto == cricket::PROTO_TLS) {
      // For TCP we need to create a server socket which can listen for incoming
      // new connections.
      rtc::AsyncSocket* socket =
          thread_->socketserver()->CreateAsyncSocket(AF_INET, SOCK_STREAM);
      if (proto == cricket::PROTO_TLS) {
        // For TLS, wrap the TCP socket with an SSL adapter. The adapter must
        // be configured with a self-signed certificate for testing.
        // Additionally, the client will not present a valid certificate, so we
        // must not fail when checking the peer's identity.
        rtc::SSLAdapter* adapter = rtc::SSLAdapter::Create(socket);
        adapter->SetRole(rtc::SSL_SERVER);
        adapter->SetIdentity(
            rtc::SSLIdentity::Create(common_name, rtc::KeyParams()));
        adapter->SetIgnoreBadCert(ignore_bad_cert);
        socket = adapter;
      }
      socket->Bind(int_addr);
      socket->Listen(5);
      server_.AddInternalServerSocket(socket, proto);
    } else {
      RTC_NOTREACHED() << "Unknown protocol type: " << proto;
    }
  }

  // Finds the first allocation in the server allocation map with a source
  // ip and port matching the socket address provided.
  TurnServerAllocation* FindAllocation(const rtc::SocketAddress& src) {
    RTC_DCHECK(thread_checker_.IsCurrent());
    const TurnServer::AllocationMap& map = server_.allocations();
    for (TurnServer::AllocationMap::const_iterator it = map.begin();
         it != map.end(); ++it) {
      if (src == it->first.src()) {
        return it->second.get();
      }
    }
    return NULL;
  }

 private:
  // For this test server, succeed if the password is the same as the username.
  // Obviously, do not use this in a production environment.
  virtual bool GetKey(const std::string& username,
                      const std::string& realm,
                      std::string* key) {
    RTC_DCHECK(thread_checker_.IsCurrent());
    return ComputeStunCredentialHash(username, realm, username, key);
  }

  TurnServer server_;
  rtc::Thread* thread_;
  webrtc::SequenceChecker thread_checker_;
};

}  // namespace cricket

#endif  // P2P_BASE_TEST_TURN_SERVER_H_
