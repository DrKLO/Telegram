/*
 *  Copyright 2012 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef P2P_BASE_TURN_PORT_H_
#define P2P_BASE_TURN_PORT_H_

#include <stdio.h>

#include <list>
#include <map>
#include <memory>
#include <set>
#include <string>
#include <vector>

#include "absl/memory/memory.h"
#include "api/async_dns_resolver.h"
#include "p2p/base/port.h"
#include "p2p/client/basic_port_allocator.h"
#include "rtc_base/async_packet_socket.h"
#include "rtc_base/ssl_certificate.h"
#include "rtc_base/task_utils/pending_task_safety_flag.h"

namespace webrtc {
class TurnCustomizer;
}

namespace cricket {

const int kMaxTurnUsernameLength = 509;  // RFC 8489 section 14.3

extern const int STUN_ATTR_TURN_LOGGING_ID;
extern const char TURN_PORT_TYPE[];
class TurnAllocateRequest;
class TurnEntry;

class TurnPort : public Port {
 public:
  enum PortState {
    STATE_CONNECTING,    // Initial state, cannot send any packets.
    STATE_CONNECTED,     // Socket connected, ready to send stun requests.
    STATE_READY,         // Received allocate success, can send any packets.
    STATE_RECEIVEONLY,   // Had REFRESH_REQUEST error, cannot send any packets.
    STATE_DISCONNECTED,  // TCP connection died, cannot send/receive any
                         // packets.
  };

  // Create a TURN port using the shared UDP socket, `socket`.
  static std::unique_ptr<TurnPort> Create(
      rtc::Thread* thread,
      rtc::PacketSocketFactory* factory,
      rtc::Network* network,
      rtc::AsyncPacketSocket* socket,
      const std::string& username,  // ice username.
      const std::string& password,  // ice password.
      const ProtocolAddress& server_address,
      const RelayCredentials& credentials,
      int server_priority,
      webrtc::TurnCustomizer* customizer) {
    // Do basic parameter validation.
    if (credentials.username.size() > kMaxTurnUsernameLength) {
      RTC_LOG(LS_ERROR) << "Attempt to use TURN with a too long username "
                        << "of length " << credentials.username.size();
      return nullptr;
    }
    // Do not connect to low-numbered ports. The default STUN port is 3478.
    if (!AllowedTurnPort(server_address.address.port())) {
      RTC_LOG(LS_ERROR) << "Attempt to use TURN to connect to port "
                        << server_address.address.port();
      return nullptr;
    }
    // Using `new` to access a non-public constructor.
    return absl::WrapUnique(
        new TurnPort(thread, factory, network, socket, username, password,
                     server_address, credentials, server_priority, customizer));
  }

  // TODO(steveanton): Remove once downstream clients have moved to `Create`.
  static std::unique_ptr<TurnPort> CreateUnique(
      rtc::Thread* thread,
      rtc::PacketSocketFactory* factory,
      rtc::Network* network,
      rtc::AsyncPacketSocket* socket,
      const std::string& username,  // ice username.
      const std::string& password,  // ice password.
      const ProtocolAddress& server_address,
      const RelayCredentials& credentials,
      int server_priority,
      webrtc::TurnCustomizer* customizer) {
    return Create(thread, factory, network, socket, username, password,
                  server_address, credentials, server_priority, customizer);
  }

  // Create a TURN port that will use a new socket, bound to `network` and
  // using a port in the range between `min_port` and `max_port`.
  static std::unique_ptr<TurnPort> Create(
      rtc::Thread* thread,
      rtc::PacketSocketFactory* factory,
      rtc::Network* network,
      uint16_t min_port,
      uint16_t max_port,
      const std::string& username,  // ice username.
      const std::string& password,  // ice password.
      const ProtocolAddress& server_address,
      const RelayCredentials& credentials,
      int server_priority,
      const std::vector<std::string>& tls_alpn_protocols,
      const std::vector<std::string>& tls_elliptic_curves,
      webrtc::TurnCustomizer* customizer,
      rtc::SSLCertificateVerifier* tls_cert_verifier = nullptr) {
    // Do basic parameter validation.
    if (credentials.username.size() > kMaxTurnUsernameLength) {
      RTC_LOG(LS_ERROR) << "Attempt to use TURN with a too long username "
                        << "of length " << credentials.username.size();
      return nullptr;
    }
    // Do not connect to low-numbered ports. The default STUN port is 3478.
    if (!AllowedTurnPort(server_address.address.port())) {
      RTC_LOG(LS_ERROR) << "Attempt to use TURN to connect to port "
                        << server_address.address.port();
      return nullptr;
    }
    // Using `new` to access a non-public constructor.
    return absl::WrapUnique(new TurnPort(
        thread, factory, network, min_port, max_port, username, password,
        server_address, credentials, server_priority, tls_alpn_protocols,
        tls_elliptic_curves, customizer, tls_cert_verifier));
  }

  // TODO(steveanton): Remove once downstream clients have moved to `Create`.
  static std::unique_ptr<TurnPort> CreateUnique(
      rtc::Thread* thread,
      rtc::PacketSocketFactory* factory,
      rtc::Network* network,
      uint16_t min_port,
      uint16_t max_port,
      const std::string& username,  // ice username.
      const std::string& password,  // ice password.
      const ProtocolAddress& server_address,
      const RelayCredentials& credentials,
      int server_priority,
      const std::vector<std::string>& tls_alpn_protocols,
      const std::vector<std::string>& tls_elliptic_curves,
      webrtc::TurnCustomizer* customizer,
      rtc::SSLCertificateVerifier* tls_cert_verifier = nullptr) {
    return Create(thread, factory, network, min_port, max_port, username,
                  password, server_address, credentials, server_priority,
                  tls_alpn_protocols, tls_elliptic_curves, customizer,
                  tls_cert_verifier);
  }

  ~TurnPort() override;

  const ProtocolAddress& server_address() const { return server_address_; }
  // Returns an empty address if the local address has not been assigned.
  rtc::SocketAddress GetLocalAddress() const;

  bool ready() const { return state_ == STATE_READY; }
  bool connected() const {
    return state_ == STATE_READY || state_ == STATE_CONNECTED;
  }
  const RelayCredentials& credentials() const { return credentials_; }

  ProtocolType GetProtocol() const override;

  virtual TlsCertPolicy GetTlsCertPolicy() const;
  virtual void SetTlsCertPolicy(TlsCertPolicy tls_cert_policy);

  void SetTurnLoggingId(const std::string& turn_logging_id);

  virtual std::vector<std::string> GetTlsAlpnProtocols() const;
  virtual std::vector<std::string> GetTlsEllipticCurves() const;

  // Release a TURN allocation by sending a refresh with lifetime 0.
  // Sets state to STATE_RECEIVEONLY.
  void Release();

  void PrepareAddress() override;
  Connection* CreateConnection(const Candidate& c,
                               PortInterface::CandidateOrigin origin) override;
  int SendTo(const void* data,
             size_t size,
             const rtc::SocketAddress& addr,
             const rtc::PacketOptions& options,
             bool payload) override;
  int SetOption(rtc::Socket::Option opt, int value) override;
  int GetOption(rtc::Socket::Option opt, int* value) override;
  int GetError() override;

  bool HandleIncomingPacket(rtc::AsyncPacketSocket* socket,
                            const char* data,
                            size_t size,
                            const rtc::SocketAddress& remote_addr,
                            int64_t packet_time_us) override;
  bool CanHandleIncomingPacketsFrom(
      const rtc::SocketAddress& addr) const override;
  virtual void OnReadPacket(rtc::AsyncPacketSocket* socket,
                            const char* data,
                            size_t size,
                            const rtc::SocketAddress& remote_addr,
                            const int64_t& packet_time_us);

  void OnSentPacket(rtc::AsyncPacketSocket* socket,
                    const rtc::SentPacket& sent_packet) override;
  virtual void OnReadyToSend(rtc::AsyncPacketSocket* socket);
  bool SupportsProtocol(const std::string& protocol) const override;

  void OnSocketConnect(rtc::AsyncPacketSocket* socket);
  void OnSocketClose(rtc::AsyncPacketSocket* socket, int error);

  const std::string& hash() const { return hash_; }
  const std::string& nonce() const { return nonce_; }

  int error() const { return error_; }

  void OnAllocateMismatch();

  rtc::AsyncPacketSocket* socket() const { return socket_; }

  // Signal with resolved server address.
  // Parameters are port, server address and resolved server address.
  // This signal will be sent only if server address is resolved successfully.
  sigslot::
      signal3<TurnPort*, const rtc::SocketAddress&, const rtc::SocketAddress&>
          SignalResolvedServerAddress;

  // Signal when TurnPort is closed,
  // e.g remote socket closed (TCP)
  //  or receiveing a REFRESH response with lifetime 0.
  sigslot::signal1<TurnPort*> SignalTurnPortClosed;

  // All public methods/signals below are for testing only.
  sigslot::signal2<TurnPort*, int> SignalTurnRefreshResult;
  sigslot::signal3<TurnPort*, const rtc::SocketAddress&, int>
      SignalCreatePermissionResult;
  void FlushRequests(int msg_type) { request_manager_.Flush(msg_type); }
  bool HasRequests() { return !request_manager_.empty(); }
  void set_credentials(const RelayCredentials& credentials) {
    credentials_ = credentials;
  }
  // Finds the turn entry with `address` and sets its channel id.
  // Returns true if the entry is found.
  bool SetEntryChannelId(const rtc::SocketAddress& address, int channel_id);
  // Visible for testing.
  // Shuts down the turn port, usually because of some fatal errors.
  void Close();

  void HandleConnectionDestroyed(Connection* conn) override;

 protected:
  TurnPort(rtc::Thread* thread,
           rtc::PacketSocketFactory* factory,
           rtc::Network* network,
           rtc::AsyncPacketSocket* socket,
           const std::string& username,
           const std::string& password,
           const ProtocolAddress& server_address,
           const RelayCredentials& credentials,
           int server_priority,
           webrtc::TurnCustomizer* customizer);

  TurnPort(rtc::Thread* thread,
           rtc::PacketSocketFactory* factory,
           rtc::Network* network,
           uint16_t min_port,
           uint16_t max_port,
           const std::string& username,
           const std::string& password,
           const ProtocolAddress& server_address,
           const RelayCredentials& credentials,
           int server_priority,
           const std::vector<std::string>& tls_alpn_protocols,
           const std::vector<std::string>& tls_elliptic_curves,
           webrtc::TurnCustomizer* customizer,
           rtc::SSLCertificateVerifier* tls_cert_verifier = nullptr);

  // NOTE: This method needs to be accessible for StunPort
  // return true if entry was created (i.e channel_number consumed).
  bool CreateOrRefreshEntry(const rtc::SocketAddress& addr, int channel_number);

  bool CreateOrRefreshEntry(const rtc::SocketAddress& addr,
                            int channel_number,
                            const std::string& remote_ufrag);

  rtc::DiffServCodePoint StunDscpValue() const override;

 private:
  enum {
    MSG_ALLOCATE_ERROR = MSG_FIRST_AVAILABLE,
    MSG_ALLOCATE_MISMATCH,
    MSG_TRY_ALTERNATE_SERVER,
    MSG_REFRESH_ERROR,
    MSG_ALLOCATION_RELEASED
  };

  typedef std::list<TurnEntry*> EntryList;
  typedef std::map<rtc::Socket::Option, int> SocketOptionsMap;
  typedef std::set<rtc::SocketAddress> AttemptedServerSet;

  static bool AllowedTurnPort(int port);
  void OnMessage(rtc::Message* pmsg) override;

  bool CreateTurnClientSocket();

  void set_nonce(const std::string& nonce) { nonce_ = nonce; }
  void set_realm(const std::string& realm) {
    if (realm != realm_) {
      realm_ = realm;
      UpdateHash();
    }
  }

  void OnRefreshError();
  void HandleRefreshError();
  bool SetAlternateServer(const rtc::SocketAddress& address);
  void ResolveTurnAddress(const rtc::SocketAddress& address);
  void OnResolveResult(rtc::AsyncResolverInterface* resolver);

  void AddRequestAuthInfo(StunMessage* msg);
  void OnSendStunPacket(const void* data, size_t size, StunRequest* request);
  // Stun address from allocate success response.
  // Currently used only for testing.
  void OnStunAddress(const rtc::SocketAddress& address);
  void OnAllocateSuccess(const rtc::SocketAddress& address,
                         const rtc::SocketAddress& stun_address);
  void OnAllocateError(int error_code, const std::string& reason);
  void OnAllocateRequestTimeout();

  void HandleDataIndication(const char* data,
                            size_t size,
                            int64_t packet_time_us);
  void HandleChannelData(int channel_id,
                         const char* data,
                         size_t size,
                         int64_t packet_time_us);
  void DispatchPacket(const char* data,
                      size_t size,
                      const rtc::SocketAddress& remote_addr,
                      ProtocolType proto,
                      int64_t packet_time_us);

  bool ScheduleRefresh(uint32_t lifetime);
  void SendRequest(StunRequest* request, int delay);
  int Send(const void* data, size_t size, const rtc::PacketOptions& options);
  void UpdateHash();
  bool UpdateNonce(StunMessage* response);
  void ResetNonce();

  bool HasPermission(const rtc::IPAddress& ipaddr) const;
  TurnEntry* FindEntry(const rtc::SocketAddress& address) const;
  TurnEntry* FindEntry(int channel_id) const;
  bool EntryExists(TurnEntry* e);
  void DestroyEntry(TurnEntry* entry);
  // Destroys the entry only if `timestamp` matches the destruction timestamp
  // in `entry`.
  void DestroyEntryIfNotCancelled(TurnEntry* entry, int64_t timestamp);

  // Marks the connection with remote address `address` failed and
  // pruned (a.k.a. write-timed-out). Returns true if a connection is found.
  bool FailAndPruneConnection(const rtc::SocketAddress& address);

  // Reconstruct the URL of the server which the candidate is gathered from.
  std::string ReconstructedServerUrl();

  void MaybeAddTurnLoggingId(StunMessage* message);

  void TurnCustomizerMaybeModifyOutgoingStunMessage(StunMessage* message);
  bool TurnCustomizerAllowChannelData(const void* data,
                                      size_t size,
                                      bool payload);

  ProtocolAddress server_address_;
  TlsCertPolicy tls_cert_policy_ = TlsCertPolicy::TLS_CERT_POLICY_SECURE;
  std::vector<std::string> tls_alpn_protocols_;
  std::vector<std::string> tls_elliptic_curves_;
  rtc::SSLCertificateVerifier* tls_cert_verifier_;
  RelayCredentials credentials_;
  AttemptedServerSet attempted_server_addresses_;

  rtc::AsyncPacketSocket* socket_;
  SocketOptionsMap socket_options_;
  std::unique_ptr<webrtc::AsyncDnsResolverInterface> resolver_;
  int error_;
  rtc::DiffServCodePoint stun_dscp_value_;

  StunRequestManager request_manager_;
  std::string realm_;  // From 401/438 response message.
  std::string nonce_;  // From 401/438 response message.
  std::string hash_;   // Digest of username:realm:password

  int next_channel_number_;
  EntryList entries_;

  PortState state_;
  // By default the value will be set to 0. This value will be used in
  // calculating the candidate priority.
  int server_priority_;

  // The number of retries made due to allocate mismatch error.
  size_t allocate_mismatch_retries_;

  // Optional TurnCustomizer that can modify outgoing messages. Once set, this
  // must outlive the TurnPort's lifetime.
  webrtc::TurnCustomizer* turn_customizer_ = nullptr;

  // Optional TurnLoggingId.
  // An identifier set by application that is added to TURN_ALLOCATE_REQUEST
  // and can be used to match client/backend logs.
  // TODO(jonaso): This should really be initialized in constructor,
  // but that is currently so terrible. Fix once constructor is changed
  // to be more easy to work with.
  std::string turn_logging_id_;

  webrtc::ScopedTaskSafety task_safety_;

  friend class TurnEntry;
  friend class TurnAllocateRequest;
  friend class TurnRefreshRequest;
  friend class TurnCreatePermissionRequest;
  friend class TurnChannelBindRequest;
};

}  // namespace cricket

#endif  // P2P_BASE_TURN_PORT_H_
