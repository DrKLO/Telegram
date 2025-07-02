/*
 *  Copyright 2012 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "p2p/base/turn_port.h"

#include <cstdint>
#include <functional>
#include <memory>
#include <utility>
#include <vector>

#include "absl/algorithm/container.h"
#include "absl/strings/match.h"
#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/task_queue/pending_task_safety_flag.h"
#include "api/transport/stun.h"
#include "api/turn_customizer.h"
#include "p2p/base/connection.h"
#include "p2p/base/p2p_constants.h"
#include "rtc_base/async_packet_socket.h"
#include "rtc_base/byte_order.h"
#include "rtc_base/checks.h"
#include "rtc_base/experiments/field_trial_parser.h"
#include "rtc_base/logging.h"
#include "rtc_base/net_helpers.h"
#include "rtc_base/network/received_packet.h"
#include "rtc_base/socket_address.h"
#include "rtc_base/strings/string_builder.h"

namespace cricket {

using ::webrtc::SafeTask;
using ::webrtc::TaskQueueBase;
using ::webrtc::TimeDelta;

// TODO(juberti): Move to stun.h when relay messages have been renamed.
static const int TURN_ALLOCATE_REQUEST = STUN_ALLOCATE_REQUEST;

// Attributes in comprehension-optional range,
// ignored by TURN server that doesn't know about them.
// https://tools.ietf.org/html/rfc5389#section-18.2
const int STUN_ATTR_TURN_LOGGING_ID = 0xff05;

// TODO(juberti): Extract to turnmessage.h
static const int TURN_DEFAULT_PORT = 3478;
static const int TURN_CHANNEL_NUMBER_START = 0x4000;

static constexpr TimeDelta kTurnPermissionTimeout = TimeDelta::Minutes(5);

static const size_t TURN_CHANNEL_HEADER_SIZE = 4U;

// Retry at most twice (i.e. three different ALLOCATE requests) on
// STUN_ERROR_ALLOCATION_MISMATCH error per rfc5766.
static const size_t MAX_ALLOCATE_MISMATCH_RETRIES = 2;

static const int TURN_SUCCESS_RESULT_CODE = 0;

inline bool IsTurnChannelData(uint16_t msg_type) {
  return ((msg_type & 0xC000) == 0x4000);  // MSB are 0b01
}

static int GetRelayPreference(cricket::ProtocolType proto) {
  switch (proto) {
    case cricket::PROTO_TCP:
      return ICE_TYPE_PREFERENCE_RELAY_TCP;
    case cricket::PROTO_TLS:
      return ICE_TYPE_PREFERENCE_RELAY_TLS;
    default:
      RTC_DCHECK(proto == PROTO_UDP);
      return ICE_TYPE_PREFERENCE_RELAY_UDP;
  }
}

class TurnAllocateRequest : public StunRequest {
 public:
  explicit TurnAllocateRequest(TurnPort* port);
  void OnSent() override;
  void OnResponse(StunMessage* response) override;
  void OnErrorResponse(StunMessage* response) override;
  void OnTimeout() override;

 private:
  // Handles authentication challenge from the server.
  void OnAuthChallenge(StunMessage* response, int code);
  void OnTryAlternate(StunMessage* response, int code);
  void OnUnknownAttribute(StunMessage* response);

  TurnPort* port_;
};

class TurnRefreshRequest : public StunRequest {
 public:
  explicit TurnRefreshRequest(TurnPort* port, int lifetime = -1);
  void OnSent() override;
  void OnResponse(StunMessage* response) override;
  void OnErrorResponse(StunMessage* response) override;
  void OnTimeout() override;

 private:
  TurnPort* port_;
};

class TurnCreatePermissionRequest : public StunRequest {
 public:
  TurnCreatePermissionRequest(TurnPort* port,
                              TurnEntry* entry,
                              const rtc::SocketAddress& ext_addr);
  ~TurnCreatePermissionRequest() override;
  void OnSent() override;
  void OnResponse(StunMessage* response) override;
  void OnErrorResponse(StunMessage* response) override;
  void OnTimeout() override;

 private:
  TurnPort* port_;
  TurnEntry* entry_;
  rtc::SocketAddress ext_addr_;
};

class TurnChannelBindRequest : public StunRequest {
 public:
  TurnChannelBindRequest(TurnPort* port,
                         TurnEntry* entry,
                         int channel_id,
                         const rtc::SocketAddress& ext_addr);
  ~TurnChannelBindRequest() override;
  void OnSent() override;
  void OnResponse(StunMessage* response) override;
  void OnErrorResponse(StunMessage* response) override;
  void OnTimeout() override;

 private:
  TurnPort* port_;
  TurnEntry* entry_;
  int channel_id_;
  rtc::SocketAddress ext_addr_;
};

// Manages a "connection" to a remote destination. We will attempt to bring up
// a channel for this remote destination to reduce the overhead of sending data.
class TurnEntry : public sigslot::has_slots<> {
 public:
  enum BindState { STATE_UNBOUND, STATE_BINDING, STATE_BOUND };
  TurnEntry(TurnPort* port, Connection* conn, int channel_id);
  ~TurnEntry();

  TurnPort* port() { return port_; }

  int channel_id() const { return channel_id_; }
  // For testing only.
  void set_channel_id(int channel_id) { channel_id_ = channel_id; }

  const rtc::SocketAddress& address() const { return ext_addr_; }
  BindState state() const { return state_; }

  // Adds a new connection object to the list of connections that are associated
  // with this entry. If prior to this call there were no connections being
  // tracked (i.e. count goes from 0 -> 1), the internal safety flag is reset
  // which cancels any potential pending deletion tasks.
  void TrackConnection(Connection* conn);

  // Removes a connection from the list of tracked connections.
  // * If `conn` was the last connection removed, the function returns a
  //   safety flag that's used to schedule the deletion of the entry after a
  //   timeout expires. If during this timeout `TrackConnection` is called, the
  //   flag will be reset and pending tasks associated with it, cancelled.
  // * If `conn` was not the last connection, the return value will be nullptr.
  rtc::scoped_refptr<webrtc::PendingTaskSafetyFlag> UntrackConnection(
      Connection* conn);

  // Helper methods to send permission and channel bind requests.
  void SendCreatePermissionRequest(int delay);
  void SendChannelBindRequest(int delay);
  // Sends a packet to the given destination address.
  // This will wrap the packet in STUN if necessary.
  int Send(const void* data,
           size_t size,
           bool payload,
           const rtc::PacketOptions& options);

  void OnCreatePermissionSuccess();
  void OnCreatePermissionError(StunMessage* response, int code);
  void OnCreatePermissionTimeout();
  void OnChannelBindSuccess();
  void OnChannelBindError(StunMessage* response, int code);
  void OnChannelBindTimeout();
  // Signal sent when TurnEntry is destroyed.
  webrtc::CallbackList<TurnEntry*> destroyed_callback_list_;

 private:
  TurnPort* port_;
  int channel_id_;
  rtc::SocketAddress ext_addr_;
  BindState state_;
  // List of associated connection instances to keep track of how many and
  // which connections are associated with this entry. Once this is empty,
  // the entry can be deleted.
  std::vector<Connection*> connections_;
  webrtc::ScopedTaskSafety task_safety_;
};

TurnPort::TurnPort(TaskQueueBase* thread,
                   rtc::PacketSocketFactory* factory,
                   const rtc::Network* network,
                   rtc::AsyncPacketSocket* socket,
                   absl::string_view username,
                   absl::string_view password,
                   const ProtocolAddress& server_address,
                   const RelayCredentials& credentials,
                   int server_priority,
                   const std::vector<std::string>& tls_alpn_protocols,
                   const std::vector<std::string>& tls_elliptic_curves,
                   webrtc::TurnCustomizer* customizer,
                   rtc::SSLCertificateVerifier* tls_cert_verifier,
                   const webrtc::FieldTrialsView* field_trials)
    : Port(thread,
           RELAY_PORT_TYPE,
           factory,
           network,
           username,
           password,
           field_trials),
      server_address_(server_address),
      server_url_(ReconstructServerUrl()),
      tls_alpn_protocols_(tls_alpn_protocols),
      tls_elliptic_curves_(tls_elliptic_curves),
      tls_cert_verifier_(tls_cert_verifier),
      credentials_(credentials),
      socket_(socket),
      error_(0),
      stun_dscp_value_(rtc::DSCP_NO_CHANGE),
      request_manager_(
          thread,
          [this](const void* data, size_t size, StunRequest* request) {
            OnSendStunPacket(data, size, request);
          }),
      next_channel_number_(TURN_CHANNEL_NUMBER_START),
      state_(STATE_CONNECTING),
      server_priority_(server_priority),
      allocate_mismatch_retries_(0),
      turn_customizer_(customizer) {}

TurnPort::TurnPort(TaskQueueBase* thread,
                   rtc::PacketSocketFactory* factory,
                   const rtc::Network* network,
                   uint16_t min_port,
                   uint16_t max_port,
                   absl::string_view username,
                   absl::string_view password,
                   const ProtocolAddress& server_address,
                   const RelayCredentials& credentials,
                   int server_priority,
                   const std::vector<std::string>& tls_alpn_protocols,
                   const std::vector<std::string>& tls_elliptic_curves,
                   webrtc::TurnCustomizer* customizer,
                   rtc::SSLCertificateVerifier* tls_cert_verifier,
                   const webrtc::FieldTrialsView* field_trials)
    : Port(thread,
           RELAY_PORT_TYPE,
           factory,
           network,
           min_port,
           max_port,
           username,
           password,
           field_trials),
      server_address_(server_address),
      server_url_(ReconstructServerUrl()),
      tls_alpn_protocols_(tls_alpn_protocols),
      tls_elliptic_curves_(tls_elliptic_curves),
      tls_cert_verifier_(tls_cert_verifier),
      credentials_(credentials),
      socket_(nullptr),
      error_(0),
      stun_dscp_value_(rtc::DSCP_NO_CHANGE),
      request_manager_(
          thread,
          [this](const void* data, size_t size, StunRequest* request) {
            OnSendStunPacket(data, size, request);
          }),
      next_channel_number_(TURN_CHANNEL_NUMBER_START),
      state_(STATE_CONNECTING),
      server_priority_(server_priority),
      allocate_mismatch_retries_(0),
      turn_customizer_(customizer) {}

TurnPort::~TurnPort() {
  // TODO(juberti): Should this even be necessary?

  // release the allocation by sending a refresh with
  // lifetime 0.
  if (ready()) {
    Release();
  }

  entries_.clear();

  if (socket_)
    socket_->UnsubscribeCloseEvent(this);

  if (!SharedSocket()) {
    delete socket_;
  }
}

rtc::SocketAddress TurnPort::GetLocalAddress() const {
  return socket_ ? socket_->GetLocalAddress() : rtc::SocketAddress();
}

ProtocolType TurnPort::GetProtocol() const {
  return server_address_.proto;
}

TlsCertPolicy TurnPort::GetTlsCertPolicy() const {
  return tls_cert_policy_;
}

void TurnPort::SetTlsCertPolicy(TlsCertPolicy tls_cert_policy) {
  tls_cert_policy_ = tls_cert_policy;
}

void TurnPort::SetTurnLoggingId(absl::string_view turn_logging_id) {
  turn_logging_id_ = std::string(turn_logging_id);
}

std::vector<std::string> TurnPort::GetTlsAlpnProtocols() const {
  return tls_alpn_protocols_;
}

std::vector<std::string> TurnPort::GetTlsEllipticCurves() const {
  return tls_elliptic_curves_;
}

void TurnPort::PrepareAddress() {
  if (credentials_.username.empty() || credentials_.password.empty()) {
    RTC_LOG(LS_ERROR) << "Allocation can't be started without setting the"
                         " TURN server credentials for the user.";
    OnAllocateError(STUN_ERROR_UNAUTHORIZED,
                    "Missing TURN server credentials.");
    return;
  }

  if (!server_address_.address.port()) {
    // We will set default TURN port, if no port is set in the address.
    server_address_.address.SetPort(TURN_DEFAULT_PORT);
  }

  if (!AllowedTurnPort(server_address_.address.port(), &field_trials())) {
    // This can only happen after a 300 ALTERNATE SERVER, since the port can't
    // be created with a disallowed port number.
    RTC_LOG(LS_ERROR) << "Attempt to start allocation with disallowed port# "
                      << server_address_.address.port();
    OnAllocateError(STUN_ERROR_SERVER_ERROR,
                    "Attempt to start allocation to a disallowed port");
    return;
  }
  if (server_address_.address.IsUnresolvedIP()) {
    ResolveTurnAddress(server_address_.address);
  } else {
    // If protocol family of server address doesn't match with local, return.
    if (!IsCompatibleAddress(server_address_.address)) {
      RTC_LOG(LS_ERROR) << "IP address family does not match. server: "
                        << server_address_.address.family()
                        << " local: " << Network()->GetBestIP().family();
      OnAllocateError(STUN_ERROR_GLOBAL_FAILURE,
                      "IP address family does not match.");
      return;
    }

    // Insert the current address to prevent redirection pingpong.
    attempted_server_addresses_.insert(server_address_.address);

    RTC_LOG(LS_INFO)
        << ToString() << ": Trying to connect to TURN server via "
        << ProtoToString(server_address_.proto) << " @ "
        << server_address_.address.ToSensitiveNameAndAddressString();
    if (!CreateTurnClientSocket()) {
      RTC_LOG(LS_ERROR) << "Failed to create TURN client socket";
      OnAllocateError(SERVER_NOT_REACHABLE_ERROR,
                      "Failed to create TURN client socket.");
      return;
    }
    if (server_address_.proto == PROTO_UDP) {
      // If its UDP, send AllocateRequest now.
      // For TCP and TLS AllcateRequest will be sent by OnSocketConnect.
      SendRequest(new TurnAllocateRequest(this), 0);
    }
  }
}

bool TurnPort::CreateTurnClientSocket() {
  RTC_DCHECK(!socket_ || SharedSocket());

  if (server_address_.proto == PROTO_UDP && !SharedSocket()) {
    socket_ = socket_factory()->CreateUdpSocket(
        rtc::SocketAddress(Network()->GetBestIP(), 0), min_port(), max_port());
  } else if (server_address_.proto == PROTO_TCP ||
             server_address_.proto == PROTO_TLS) {
    RTC_DCHECK(!SharedSocket());
    int opts = rtc::PacketSocketFactory::OPT_STUN;

    // Apply server address TLS and insecure bits to options.
    if (server_address_.proto == PROTO_TLS) {
      if (tls_cert_policy_ ==
          TlsCertPolicy::TLS_CERT_POLICY_INSECURE_NO_CHECK) {
        opts |= rtc::PacketSocketFactory::OPT_TLS_INSECURE;
      } else {
        opts |= rtc::PacketSocketFactory::OPT_TLS;
      }
    }

    rtc::PacketSocketTcpOptions tcp_options;
    tcp_options.opts = opts;
    tcp_options.tls_alpn_protocols = tls_alpn_protocols_;
    tcp_options.tls_elliptic_curves = tls_elliptic_curves_;
    tcp_options.tls_cert_verifier = tls_cert_verifier_;
    socket_ = socket_factory()->CreateClientTcpSocket(
        rtc::SocketAddress(Network()->GetBestIP(), 0), server_address_.address,
        proxy(), user_agent(), tcp_options);
  }

  if (!socket_) {
    error_ = SOCKET_ERROR;
    return false;
  }

  // Apply options if any.
  for (SocketOptionsMap::iterator iter = socket_options_.begin();
       iter != socket_options_.end(); ++iter) {
    socket_->SetOption(iter->first, iter->second);
  }

  if (!SharedSocket()) {
    // If socket is shared, AllocationSequence will receive the packet.
    socket_->RegisterReceivedPacketCallback(
        [&](rtc::AsyncPacketSocket* socket, const rtc::ReceivedPacket& packet) {
          OnReadPacket(socket, packet);
        });
  }

  socket_->SignalReadyToSend.connect(this, &TurnPort::OnReadyToSend);

  socket_->SignalSentPacket.connect(this, &TurnPort::OnSentPacket);

  // TCP port is ready to send stun requests after the socket is connected,
  // while UDP port is ready to do so once the socket is created.
  if (server_address_.proto == PROTO_TCP ||
      server_address_.proto == PROTO_TLS) {
    socket_->SignalConnect.connect(this, &TurnPort::OnSocketConnect);
    socket_->SubscribeCloseEvent(
        this,
        [this](rtc::AsyncPacketSocket* s, int err) { OnSocketClose(s, err); });
  } else {
    state_ = STATE_CONNECTED;
  }
  return true;
}

void TurnPort::OnSocketConnect(rtc::AsyncPacketSocket* socket) {
  // This slot should only be invoked if we're using a connection-oriented
  // protocol.
  RTC_DCHECK(server_address_.proto == PROTO_TCP ||
             server_address_.proto == PROTO_TLS);

  // Do not use this port if the socket bound to an address not associated with
  // the desired network interface. This is seen in Chrome, where TCP sockets
  // cannot be given a binding address, and the platform is expected to pick
  // the correct local address.
  //
  // However, there are two situations in which we allow the bound address to
  // not be one of the addresses of the requested interface:
  // 1. The bound address is the loopback address. This happens when a proxy
  // forces TCP to bind to only the localhost address (see issue 3927).
  // 2. The bound address is the "any address". This happens when
  // multiple_routes is disabled (see issue 4780).
  //
  // Note that, aside from minor differences in log statements, this logic is
  // identical to that in TcpPort.
  const rtc::SocketAddress& socket_address = socket->GetLocalAddress();
  if (absl::c_none_of(Network()->GetIPs(),
                      [socket_address](const rtc::InterfaceAddress& addr) {
                        return socket_address.ipaddr() == addr;
                      })) {
    if (socket->GetLocalAddress().IsLoopbackIP()) {
      RTC_LOG(LS_WARNING) << "Socket is bound to the address:"
                          << socket_address.ToSensitiveNameAndAddressString()
                          << ", rather than an address associated with network:"
                          << Network()->ToString()
                          << ". Still allowing it since it's localhost.";
    } else if (IPIsAny(Network()->GetBestIP())) {
      RTC_LOG(LS_WARNING)
          << "Socket is bound to the address:"
          << socket_address.ToSensitiveNameAndAddressString()
          << ", rather than an address associated with network:"
          << Network()->ToString()
          << ". Still allowing it since it's the 'any' address"
             ", possibly caused by multiple_routes being disabled.";
    } else {
      RTC_LOG(LS_WARNING) << "Socket is bound to the address:"
                          << socket_address.ToSensitiveNameAndAddressString()
                          << ", rather than an address associated with network:"
                          << Network()->ToString() << ". Discarding TURN port.";
      OnAllocateError(
          STUN_ERROR_GLOBAL_FAILURE,
          "Address not associated with the desired network interface.");
      return;
    }
  }

  state_ = STATE_CONNECTED;  // It is ready to send stun requests.
  if (server_address_.address.IsUnresolvedIP()) {
    server_address_.address = socket_->GetRemoteAddress();
  }

  RTC_LOG(LS_INFO) << "TurnPort connected to "
                   << socket->GetRemoteAddress().ToSensitiveString()
                   << " using tcp.";
  SendRequest(new TurnAllocateRequest(this), 0);
}

void TurnPort::OnSocketClose(rtc::AsyncPacketSocket* socket, int error) {
  RTC_LOG(LS_WARNING) << ToString()
                      << ": Connection with server failed with error: "
                      << error;
  RTC_DCHECK(socket == socket_);
  Close();
}

void TurnPort::OnAllocateMismatch() {
  if (allocate_mismatch_retries_ >= MAX_ALLOCATE_MISMATCH_RETRIES) {
    RTC_LOG(LS_WARNING) << ToString() << ": Giving up on the port after "
                        << allocate_mismatch_retries_
                        << " retries for STUN_ERROR_ALLOCATION_MISMATCH";
    OnAllocateError(STUN_ERROR_ALLOCATION_MISMATCH,
                    "Maximum retries reached for allocation mismatch.");
    return;
  }

  RTC_LOG(LS_INFO) << ToString()
                   << ": Allocating a new socket after "
                      "STUN_ERROR_ALLOCATION_MISMATCH, retry: "
                   << allocate_mismatch_retries_ + 1;

  socket_->UnsubscribeCloseEvent(this);

  if (SharedSocket()) {
    ResetSharedSocket();
  } else {
    delete socket_;
  }
  socket_ = nullptr;

  ResetNonce();
  PrepareAddress();
  ++allocate_mismatch_retries_;
}

Connection* TurnPort::CreateConnection(const Candidate& remote_candidate,
                                       CandidateOrigin origin) {
  // TURN-UDP can only connect to UDP candidates.
  if (!SupportsProtocol(remote_candidate.protocol())) {
    return nullptr;
  }

  if (state_ == STATE_DISCONNECTED || state_ == STATE_RECEIVEONLY) {
    return nullptr;
  }

  // If the remote endpoint signaled us an mDNS candidate, we do not form a pair
  // with the relay candidate to avoid IP leakage in the CreatePermission
  // request.
  if (absl::EndsWith(remote_candidate.address().hostname(), LOCAL_TLD)) {
    return nullptr;
  }

  // A TURN port will have two candidates, STUN and TURN. STUN may not
  // present in all cases. If present stun candidate will be added first
  // and TURN candidate later.
  for (size_t index = 0; index < Candidates().size(); ++index) {
    const Candidate& local_candidate = Candidates()[index];
    if (local_candidate.is_relay() && local_candidate.address().family() ==
                                          remote_candidate.address().family()) {
      ProxyConnection* conn =
          new ProxyConnection(NewWeakPtr(), index, remote_candidate);
      // Create an entry, if needed, so we can get our permissions set up
      // correctly.
      if (CreateOrRefreshEntry(conn, next_channel_number_)) {
        next_channel_number_++;
      }
      AddOrReplaceConnection(conn);
      return conn;
    }
  }
  return nullptr;
}

bool TurnPort::FailAndPruneConnection(const rtc::SocketAddress& address) {
  Connection* conn = GetConnection(address);
  if (conn != nullptr) {
    conn->FailAndPrune();
    return true;
  }
  return false;
}

int TurnPort::SetOption(rtc::Socket::Option opt, int value) {
  // Remember the last requested DSCP value, for STUN traffic.
  if (opt == rtc::Socket::OPT_DSCP)
    stun_dscp_value_ = static_cast<rtc::DiffServCodePoint>(value);

  if (!socket_) {
    // If socket is not created yet, these options will be applied during socket
    // creation.
    socket_options_[opt] = value;
    return 0;
  }
  return socket_->SetOption(opt, value);
}

int TurnPort::GetOption(rtc::Socket::Option opt, int* value) {
  if (!socket_) {
    SocketOptionsMap::const_iterator it = socket_options_.find(opt);
    if (it == socket_options_.end()) {
      return -1;
    }
    *value = it->second;
    return 0;
  }

  return socket_->GetOption(opt, value);
}

int TurnPort::GetError() {
  return error_;
}

int TurnPort::SendTo(const void* data,
                     size_t size,
                     const rtc::SocketAddress& addr,
                     const rtc::PacketOptions& options,
                     bool payload) {
  // Try to find an entry for this specific address; we should have one.
  TurnEntry* entry = FindEntry(addr);
  RTC_DCHECK(entry);

  if (!ready()) {
    error_ = ENOTCONN;
    return SOCKET_ERROR;
  }

  // Send the actual contents to the server using the usual mechanism.
  rtc::PacketOptions modified_options(options);
  CopyPortInformationToPacketInfo(&modified_options.info_signaled_after_sent);
  int sent = entry->Send(data, size, payload, modified_options);
  if (sent <= 0) {
    error_ = socket_->GetError();
    return SOCKET_ERROR;
  }

  // The caller of the function is expecting the number of user data bytes,
  // rather than the size of the packet.
  return static_cast<int>(size);
}

bool TurnPort::CanHandleIncomingPacketsFrom(
    const rtc::SocketAddress& addr) const {
  return server_address_.address == addr;
}

void TurnPort::SendBindingErrorResponse(StunMessage* message,
                                        const rtc::SocketAddress& addr,
                                        int error_code,
                                        absl::string_view reason) {
  if (!GetConnection(addr))
    return;

  Port::SendBindingErrorResponse(message, addr, error_code, reason);
}

bool TurnPort::HandleIncomingPacket(rtc::AsyncPacketSocket* socket,
                                    const rtc::ReceivedPacket& packet) {
  if (socket != socket_) {
    // The packet was received on a shared socket after we've allocated a new
    // socket for this TURN port.
    return false;
  }

  // This is to guard against a STUN response from previous server after
  // alternative server redirection. TODO(guoweis): add a unit test for this
  // race condition.
  if (packet.source_address() != server_address_.address) {
    RTC_LOG(LS_WARNING)
        << ToString() << ": Discarding TURN message from unknown address: "
        << packet.source_address().ToSensitiveNameAndAddressString()
        << " server_address_: "
        << server_address_.address.ToSensitiveNameAndAddressString();
    return false;
  }

  // The message must be at least the size of a channel header.
  if (packet.payload().size() < TURN_CHANNEL_HEADER_SIZE) {
    RTC_LOG(LS_WARNING) << ToString()
                        << ": Received TURN message that was too short";
    return false;
  }

  if (state_ == STATE_DISCONNECTED) {
    RTC_LOG(LS_WARNING)
        << ToString()
        << ": Received TURN message while the TURN port is disconnected";
    return false;
  }

  const char* data = reinterpret_cast<const char*>(packet.payload().data());
  int size = packet.payload().size();
  int64_t packet_time_us =
      packet.arrival_time() ? packet.arrival_time()->us() : -1;

  // Check the message type, to see if is a Channel Data message.
  // The message will either be channel data, a TURN data indication, or
  // a response to a previous request.
  uint16_t msg_type = rtc::GetBE16(packet.payload().data());
  if (IsTurnChannelData(msg_type)) {
    HandleChannelData(msg_type, data, size, packet_time_us);
    return true;
  }

  if (msg_type == TURN_DATA_INDICATION) {
    HandleDataIndication(data, size, packet_time_us);
    return true;
  }

  if (SharedSocket() && (msg_type == STUN_BINDING_RESPONSE ||
                         msg_type == STUN_BINDING_ERROR_RESPONSE)) {
    RTC_LOG(LS_VERBOSE)
        << ToString()
        << ": Ignoring STUN binding response message on shared socket.";
    return false;
  }

  request_manager_.CheckResponse(data, size);

  return true;
}

void TurnPort::OnReadPacket(rtc::AsyncPacketSocket* socket,
                            const rtc::ReceivedPacket& packet) {
  HandleIncomingPacket(socket, packet);
}

void TurnPort::OnSentPacket(rtc::AsyncPacketSocket* socket,
                            const rtc::SentPacket& sent_packet) {
  PortInterface::SignalSentPacket(sent_packet);
}

void TurnPort::OnReadyToSend(rtc::AsyncPacketSocket* socket) {
  if (ready()) {
    Port::OnReadyToSend();
  }
}

bool TurnPort::SupportsProtocol(absl::string_view protocol) const {
  // Turn port only connects to UDP candidates.
  return protocol == UDP_PROTOCOL_NAME;
}

// Update current server address port with the alternate server address port.
bool TurnPort::SetAlternateServer(const rtc::SocketAddress& address) {
  // Check if we have seen this address before and reject if we did.
  AttemptedServerSet::iterator iter = attempted_server_addresses_.find(address);
  if (iter != attempted_server_addresses_.end()) {
    RTC_LOG(LS_WARNING) << ToString() << ": Redirection to ["
                        << address.ToSensitiveNameAndAddressString()
                        << "] ignored, allocation failed.";
    return false;
  }

  // If protocol family of server address doesn't match with local, return.
  if (!IsCompatibleAddress(address)) {
    RTC_LOG(LS_WARNING) << "Server IP address family does not match with "
                           "local host address family type";
    return false;
  }

  // Block redirects to a loopback address.
  // See: https://bugs.chromium.org/p/chromium/issues/detail?id=649118
  if (address.IsLoopbackIP()) {
    RTC_LOG(LS_WARNING) << ToString()
                        << ": Blocking attempted redirect to loopback address.";
    return false;
  }

  RTC_LOG(LS_INFO) << ToString() << ": Redirecting from TURN server ["
                   << server_address_.address.ToSensitiveNameAndAddressString()
                   << "] to TURN server ["
                   << address.ToSensitiveNameAndAddressString() << "]";
  server_address_ = ProtocolAddress(address, server_address_.proto);

  // Insert the current address to prevent redirection pingpong.
  attempted_server_addresses_.insert(server_address_.address);
  return true;
}

void TurnPort::ResolveTurnAddress(const rtc::SocketAddress& address) {
  if (resolver_)
    return;

  RTC_LOG(LS_INFO) << ToString() << ": Starting TURN host lookup for "
                   << address.ToSensitiveString();
  resolver_ = socket_factory()->CreateAsyncDnsResolver();
  auto callback = [this] {
    // If DNS resolve is failed when trying to connect to the server using TCP,
    // one of the reason could be due to DNS queries blocked by firewall.
    // In such cases we will try to connect to the server with hostname,
    // assuming socket layer will resolve the hostname through a HTTP proxy (if
    // any).
    auto& result = resolver_->result();
    if (result.GetError() != 0 && (server_address_.proto == PROTO_TCP ||
                                   server_address_.proto == PROTO_TLS)) {
      if (!CreateTurnClientSocket()) {
        OnAllocateError(SERVER_NOT_REACHABLE_ERROR,
                        "TURN host lookup received error.");
      }
      return;
    }

    // Copy the original server address in `resolved_address`. For TLS based
    // sockets we need hostname along with resolved address.
    rtc::SocketAddress resolved_address = server_address_.address;
    if (result.GetError() != 0 ||
        !result.GetResolvedAddress(Network()->GetBestIP().family(),
                                   &resolved_address)) {
      RTC_LOG(LS_WARNING) << ToString() << ": TURN host lookup received error "
                          << result.GetError();
      error_ = result.GetError();
      OnAllocateError(SERVER_NOT_REACHABLE_ERROR,
                      "TURN host lookup received error.");
      return;
    }
    server_address_.address = resolved_address;
    PrepareAddress();
  };
  resolver_->Start(address, Network()->family(), std::move(callback));
}

void TurnPort::OnSendStunPacket(const void* data,
                                size_t size,
                                StunRequest* request) {
  RTC_DCHECK(connected());
  rtc::PacketOptions options(StunDscpValue());
  options.info_signaled_after_sent.packet_type = rtc::PacketType::kTurnMessage;
  CopyPortInformationToPacketInfo(&options.info_signaled_after_sent);
  if (Send(data, size, options) < 0) {
    RTC_LOG(LS_ERROR) << ToString() << ": Failed to send TURN message, error: "
                      << socket_->GetError();
  }
}

void TurnPort::OnStunAddress(const rtc::SocketAddress& address) {
  // STUN Port will discover STUN candidate, as it's supplied with first TURN
  // server address.
  // Why not using this address? - P2PTransportChannel will start creating
  // connections after first candidate, which means it could start creating the
  // connections before TURN candidate added. For that to handle, we need to
  // supply STUN candidate from this port to UDPPort, and TurnPort should have
  // handle to UDPPort to pass back the address.
}

void TurnPort::OnAllocateSuccess(const rtc::SocketAddress& address,
                                 const rtc::SocketAddress& stun_address) {
  state_ = STATE_READY;

  rtc::SocketAddress related_address = stun_address;

  // For relayed candidate, Base is the candidate itself.
  AddAddress(address,          // Candidate address.
             address,          // Base address.
             related_address,  // Related address.
             UDP_PROTOCOL_NAME,
             ProtoToString(server_address_.proto),  // The first hop protocol.
             "",  // TCP candidate type, empty for turn candidates.
             RELAY_PORT_TYPE, GetRelayPreference(server_address_.proto),
             server_priority_, server_url_, true);
}

void TurnPort::OnAllocateError(int error_code, absl::string_view reason) {
  // We will send SignalPortError asynchronously as this can be sent during
  // port initialization. This way it will not be blocking other port
  // creation.
  thread()->PostTask(
      SafeTask(task_safety_.flag(), [this] { SignalPortError(this); }));
  std::string address = GetLocalAddress().HostAsSensitiveURIString();
  int port = GetLocalAddress().port();
  if (server_address_.proto == PROTO_TCP &&
      server_address_.address.IsPrivateIP()) {
    address.clear();
    port = 0;
  }
  SignalCandidateError(this, IceCandidateErrorEvent(address, port, server_url_,
                                                    error_code, reason));
}

void TurnPort::OnRefreshError() {
  // Need to clear the requests asynchronously because otherwise, the refresh
  // request may be deleted twice: once at the end of the message processing
  // and the other in HandleRefreshError().
  thread()->PostTask(
      SafeTask(task_safety_.flag(), [this] { HandleRefreshError(); }));
}

void TurnPort::HandleRefreshError() {
  request_manager_.Clear();
  state_ = STATE_RECEIVEONLY;
  // Fail and prune all connections; stop sending data.
  for (auto kv : connections()) {
    kv.second->FailAndPrune();
  }
}

void TurnPort::Release() {
  // Remove any pending refresh requests.
  request_manager_.Clear();

  // Send refresh with lifetime 0.
  TurnRefreshRequest* req = new TurnRefreshRequest(this, 0);
  SendRequest(req, 0);

  state_ = STATE_RECEIVEONLY;
}

void TurnPort::Close() {
  if (!ready()) {
    OnAllocateError(SERVER_NOT_REACHABLE_ERROR, "");
  }
  request_manager_.Clear();
  // Stop the port from creating new connections.
  state_ = STATE_DISCONNECTED;
  // Delete all existing connections; stop sending data.
  DestroyAllConnections();
  if (callbacks_for_test_) {
    callbacks_for_test_->OnTurnPortClosed();
  }
}

rtc::DiffServCodePoint TurnPort::StunDscpValue() const {
  return stun_dscp_value_;
}

// static
bool TurnPort::AllowedTurnPort(int port,
                               const webrtc::FieldTrialsView* field_trials) {
  // Port 53, 80 and 443 are used for existing deployments.
  // Ports above 1024 are assumed to be OK to use.
  if (port == 53 || port == 80 || port == 443 || port >= 1024) {
    return true;
  }
  return false;
}

void TurnPort::TryAlternateServer() {
  if (server_address().proto == PROTO_UDP) {
    // Send another allocate request to alternate server, with the received
    // realm and nonce values.
    SendRequest(new TurnAllocateRequest(this), 0);
  } else {
    // Since it's TCP, we have to delete the connected socket and reconnect
    // with the alternate server. PrepareAddress will send stun binding once
    // the new socket is connected.
    RTC_DCHECK(server_address().proto == PROTO_TCP ||
               server_address().proto == PROTO_TLS);
    RTC_DCHECK(!SharedSocket());
    delete socket_;
    socket_ = nullptr;
    PrepareAddress();
  }
}

void TurnPort::OnAllocateRequestTimeout() {
  OnAllocateError(SERVER_NOT_REACHABLE_ERROR,
                  "TURN allocate request timed out.");
}

void TurnPort::HandleDataIndication(const char* data,
                                    size_t size,
                                    int64_t packet_time_us) {
  // Read in the message, and process according to RFC5766, Section 10.4.
  rtc::ByteBufferReader buf(
      rtc::MakeArrayView(reinterpret_cast<const uint8_t*>(data), size));
  TurnMessage msg;
  if (!msg.Read(&buf)) {
    RTC_LOG(LS_WARNING) << ToString()
                        << ": Received invalid TURN data indication";
    return;
  }

  // Check mandatory attributes.
  const StunAddressAttribute* addr_attr =
      msg.GetAddress(STUN_ATTR_XOR_PEER_ADDRESS);
  if (!addr_attr) {
    RTC_LOG(LS_WARNING) << ToString()
                        << ": Missing STUN_ATTR_XOR_PEER_ADDRESS attribute "
                           "in data indication.";
    return;
  }

  const StunByteStringAttribute* data_attr = msg.GetByteString(STUN_ATTR_DATA);
  if (!data_attr) {
    RTC_LOG(LS_WARNING) << ToString()
                        << ": Missing STUN_ATTR_DATA attribute in "
                           "data indication.";
    return;
  }

  // Log a warning if the data didn't come from an address that we think we have
  // a permission for.
  rtc::SocketAddress ext_addr(addr_attr->GetAddress());
  if (!HasPermission(ext_addr.ipaddr())) {
    RTC_LOG(LS_WARNING) << ToString()
                        << ": Received TURN data indication with unknown "
                           "peer address, addr: "
                        << ext_addr.ToSensitiveString();
  }
  // TODO(bugs.webrtc.org/14870): rebuild DispatchPacket to take an
  // ArrayView<uint8_t>
  DispatchPacket(reinterpret_cast<const char*>(data_attr->array_view().data()),
                 data_attr->length(), ext_addr, PROTO_UDP, packet_time_us);
}

void TurnPort::HandleChannelData(int channel_id,
                                 const char* data,
                                 size_t size,
                                 int64_t packet_time_us) {
  // Read the message, and process according to RFC5766, Section 11.6.
  //    0                   1                   2                   3
  //    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
  //   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
  //   |         Channel Number        |            Length             |
  //   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
  //   |                                                               |
  //   /                       Application Data                        /
  //   /                                                               /
  //   |                                                               |
  //   |                               +-------------------------------+
  //   |                               |
  //   +-------------------------------+

  // Extract header fields from the message.
  uint16_t len = rtc::GetBE16(data + 2);
  if (len > size - TURN_CHANNEL_HEADER_SIZE) {
    RTC_LOG(LS_WARNING) << ToString()
                        << ": Received TURN channel data message with "
                           "incorrect length, len: "
                        << len;
    return;
  }
  // Allowing messages larger than `len`, as ChannelData can be padded.

  TurnEntry* entry = FindEntry(channel_id);
  if (!entry) {
    RTC_LOG(LS_WARNING) << ToString()
                        << ": Received TURN channel data message for invalid "
                           "channel, channel_id: "
                        << channel_id;
    return;
  }

  DispatchPacket(data + TURN_CHANNEL_HEADER_SIZE, len, entry->address(),
                 PROTO_UDP, packet_time_us);
}

void TurnPort::DispatchPacket(const char* data,
                              size_t size,
                              const rtc::SocketAddress& remote_addr,
                              ProtocolType proto,
                              int64_t packet_time_us) {
  rtc::ReceivedPacket packet = rtc::ReceivedPacket::CreateFromLegacy(
      data, size, packet_time_us, remote_addr);
  if (Connection* conn = GetConnection(remote_addr)) {
    conn->OnReadPacket(packet);
  } else {
    Port::OnReadPacket(packet, proto);
  }
}

bool TurnPort::ScheduleRefresh(uint32_t lifetime) {
  // Lifetime is in seconds, delay is in milliseconds.
  int delay = 1 * 60 * 1000;

  // Cutoff lifetime bigger than 1h.
  constexpr uint32_t max_lifetime = 60 * 60;

  if (lifetime < 2 * 60) {
    // The RFC does not mention a lower limit on lifetime.
    // So if server sends a value less than 2 minutes, we schedule a refresh
    // for half lifetime.
    RTC_LOG(LS_WARNING) << ToString()
                        << ": Received response with short lifetime: "
                        << lifetime << " seconds.";
    delay = (lifetime * 1000) / 2;
  } else if (lifetime > max_lifetime) {
    // Make 1 hour largest delay, and then we schedule a refresh for one minute
    // less than max lifetime.
    RTC_LOG(LS_WARNING) << ToString()
                        << ": Received response with long lifetime: "
                        << lifetime << " seconds.";
    delay = (max_lifetime - 60) * 1000;
  } else {
    // Normal case,
    // we schedule a refresh for one minute less than requested lifetime.
    delay = (lifetime - 60) * 1000;
  }

  SendRequest(new TurnRefreshRequest(this), delay);
  RTC_LOG(LS_INFO) << ToString() << ": Scheduled refresh in " << delay << "ms.";
  return true;
}

void TurnPort::SendRequest(StunRequest* req, int delay) {
  request_manager_.SendDelayed(req, delay);
}

void TurnPort::AddRequestAuthInfo(StunMessage* msg) {
  // If we've gotten the necessary data from the server, add it to our request.
  RTC_DCHECK(!hash_.empty());
  msg->AddAttribute(std::make_unique<StunByteStringAttribute>(
      STUN_ATTR_USERNAME, credentials_.username));
  msg->AddAttribute(
      std::make_unique<StunByteStringAttribute>(STUN_ATTR_REALM, realm_));
  msg->AddAttribute(
      std::make_unique<StunByteStringAttribute>(STUN_ATTR_NONCE, nonce_));
  const bool success = msg->AddMessageIntegrity(hash());
  RTC_DCHECK(success);
}

int TurnPort::Send(const void* data,
                   size_t len,
                   const rtc::PacketOptions& options) {
  return socket_->SendTo(data, len, server_address_.address, options);
}

void TurnPort::UpdateHash() {
  const bool success = ComputeStunCredentialHash(credentials_.username, realm_,
                                                 credentials_.password, &hash_);
  RTC_DCHECK(success);
}

bool TurnPort::UpdateNonce(StunMessage* response) {
  // When stale nonce error received, we should update
  // hash and store realm and nonce.
  // Check the mandatory attributes.
  const StunByteStringAttribute* realm_attr =
      response->GetByteString(STUN_ATTR_REALM);
  if (!realm_attr) {
    RTC_LOG(LS_ERROR) << "Missing STUN_ATTR_REALM attribute in "
                         "stale nonce error response.";
    return false;
  }
  set_realm(realm_attr->string_view());

  const StunByteStringAttribute* nonce_attr =
      response->GetByteString(STUN_ATTR_NONCE);
  if (!nonce_attr) {
    RTC_LOG(LS_ERROR) << "Missing STUN_ATTR_NONCE attribute in "
                         "stale nonce error response.";
    return false;
  }
  set_nonce(nonce_attr->string_view());
  return true;
}

void TurnPort::ResetNonce() {
  hash_.clear();
  nonce_.clear();
  realm_.clear();
}

bool TurnPort::HasPermission(const rtc::IPAddress& ipaddr) const {
  return absl::c_any_of(entries_, [&ipaddr](const auto& e) {
    return e->address().ipaddr() == ipaddr;
  });
}

TurnEntry* TurnPort::FindEntry(const rtc::SocketAddress& addr) const {
  auto it = absl::c_find_if(
      entries_, [&addr](const auto& e) { return e->address() == addr; });
  return (it != entries_.end()) ? it->get() : nullptr;
}

TurnEntry* TurnPort::FindEntry(int channel_id) const {
  auto it = absl::c_find_if(entries_, [&channel_id](const auto& e) {
    return e->channel_id() == channel_id;
  });
  return (it != entries_.end()) ? it->get() : nullptr;
}

bool TurnPort::CreateOrRefreshEntry(Connection* conn, int channel_number) {
  const Candidate& remote_candidate = conn->remote_candidate();
  TurnEntry* entry = FindEntry(remote_candidate.address());
  if (entry == nullptr) {
    entries_.push_back(std::make_unique<TurnEntry>(this, conn, channel_number));
    return true;
  }

  // Associate this connection object with an existing entry. If the entry
  // has been scheduled for deletion, this will cancel that task.
  entry->TrackConnection(conn);

  return false;
}

void TurnPort::HandleConnectionDestroyed(Connection* conn) {
  // Schedule an event to destroy TurnEntry for the connection, which is
  // being destroyed.
  const rtc::SocketAddress& remote_address = conn->remote_candidate().address();
  // We should always have an entry for this connection.
  TurnEntry* entry = FindEntry(remote_address);
  rtc::scoped_refptr<webrtc::PendingTaskSafetyFlag> flag =
      entry->UntrackConnection(conn);
  if (flag) {
    // An assumption here is that the lifetime flag for the entry, is within
    // the lifetime scope of `task_safety_` and therefore use of `this` is safe.
    // If an entry gets reused (associated with a new connection) while this
    // task is pending, the entry will reset the safety flag, thus cancel this
    // task.
    thread()->PostDelayedTask(SafeTask(flag,
                                       [this, entry] {
                                         entries_.erase(absl::c_find_if(
                                             entries_, [entry](const auto& e) {
                                               return e.get() == entry;
                                             }));
                                       }),
                              kTurnPermissionTimeout);
  }
}

void TurnPort::SetCallbacksForTest(CallbacksForTest* callbacks) {
  RTC_DCHECK(!callbacks_for_test_);
  callbacks_for_test_ = callbacks;
}

bool TurnPort::SetEntryChannelId(const rtc::SocketAddress& address,
                                 int channel_id) {
  TurnEntry* entry = FindEntry(address);
  if (!entry) {
    return false;
  }
  entry->set_channel_id(channel_id);
  return true;
}

std::string TurnPort::ReconstructServerUrl() {
  // https://www.rfc-editor.org/rfc/rfc7065#section-3.1
  // turnURI       = scheme ":" host [ ":" port ]
  //                 [ "?transport=" transport ]
  // scheme        = "turn" / "turns"
  // transport     = "udp" / "tcp" / transport-ext
  // transport-ext = 1*unreserved
  std::string scheme = "turn";
  std::string transport = "tcp";
  switch (server_address_.proto) {
    case PROTO_SSLTCP:
    case PROTO_TLS:
      scheme = "turns";
      break;
    case PROTO_UDP:
      transport = "udp";
      break;
    case PROTO_TCP:
      break;
  }
  rtc::StringBuilder url;
  url << scheme << ":" << server_address_.address.HostAsURIString() << ":"
      << server_address_.address.port() << "?transport=" << transport;
  return url.Release();
}

void TurnPort::TurnCustomizerMaybeModifyOutgoingStunMessage(
    StunMessage* message) {
  if (turn_customizer_ == nullptr) {
    return;
  }

  turn_customizer_->MaybeModifyOutgoingStunMessage(this, message);
}

bool TurnPort::TurnCustomizerAllowChannelData(const void* data,
                                              size_t size,
                                              bool payload) {
  if (turn_customizer_ == nullptr) {
    return true;
  }

  return turn_customizer_->AllowChannelData(this, data, size, payload);
}

void TurnPort::MaybeAddTurnLoggingId(StunMessage* msg) {
  if (!turn_logging_id_.empty()) {
    msg->AddAttribute(std::make_unique<StunByteStringAttribute>(
        STUN_ATTR_TURN_LOGGING_ID, turn_logging_id_));
  }
}

TurnAllocateRequest::TurnAllocateRequest(TurnPort* port)
    : StunRequest(port->request_manager(),
                  std::make_unique<TurnMessage>(TURN_ALLOCATE_REQUEST)),
      port_(port) {
  StunMessage* message = mutable_msg();
  // Create the request as indicated in RFC 5766, Section 6.1.
  RTC_DCHECK_EQ(message->type(), TURN_ALLOCATE_REQUEST);
  auto transport_attr =
      StunAttribute::CreateUInt32(STUN_ATTR_REQUESTED_TRANSPORT);
  transport_attr->SetValue(IPPROTO_UDP << 24);
  message->AddAttribute(std::move(transport_attr));
  if (!port_->hash().empty()) {
    port_->AddRequestAuthInfo(message);
  } else {
    SetAuthenticationRequired(false);
  }
  port_->MaybeAddTurnLoggingId(message);
  port_->TurnCustomizerMaybeModifyOutgoingStunMessage(message);
}

void TurnAllocateRequest::OnSent() {
  RTC_LOG(LS_INFO) << port_->ToString() << ": TURN allocate request sent, id="
                   << rtc::hex_encode(id());
  StunRequest::OnSent();
}

void TurnAllocateRequest::OnResponse(StunMessage* response) {
  RTC_LOG(LS_INFO) << port_->ToString()
                   << ": TURN allocate requested successfully, id="
                   << rtc::hex_encode(id())
                   << ", code=0"  // Makes logging easier to parse.
                      ", rtt="
                   << Elapsed();

  // Check mandatory attributes as indicated in RFC5766, Section 6.3.
  const StunAddressAttribute* mapped_attr =
      response->GetAddress(STUN_ATTR_XOR_MAPPED_ADDRESS);
  if (!mapped_attr) {
    RTC_LOG(LS_WARNING) << port_->ToString()
                        << ": Missing STUN_ATTR_XOR_MAPPED_ADDRESS "
                           "attribute in allocate success response";
    return;
  }
  // Using XOR-Mapped-Address for stun.
  port_->OnStunAddress(mapped_attr->GetAddress());

  const StunAddressAttribute* relayed_attr =
      response->GetAddress(STUN_ATTR_XOR_RELAYED_ADDRESS);
  if (!relayed_attr) {
    RTC_LOG(LS_WARNING) << port_->ToString()
                        << ": Missing STUN_ATTR_XOR_RELAYED_ADDRESS "
                           "attribute in allocate success response";
    return;
  }

  const StunUInt32Attribute* lifetime_attr =
      response->GetUInt32(STUN_ATTR_TURN_LIFETIME);
  if (!lifetime_attr) {
    RTC_LOG(LS_WARNING) << port_->ToString()
                        << ": Missing STUN_ATTR_TURN_LIFETIME attribute in "
                           "allocate success response";
    return;
  }
  // Notify the port the allocate succeeded, and schedule a refresh request.
  port_->OnAllocateSuccess(relayed_attr->GetAddress(),
                           mapped_attr->GetAddress());
  port_->ScheduleRefresh(lifetime_attr->value());
}

void TurnAllocateRequest::OnErrorResponse(StunMessage* response) {
  // Process error response according to RFC5766, Section 6.4.
  int error_code = response->GetErrorCodeValue();

  RTC_LOG(LS_INFO) << port_->ToString()
                   << ": Received TURN allocate error response, id="
                   << rtc::hex_encode(id()) << ", code=" << error_code
                   << ", rtt=" << Elapsed();

  switch (error_code) {
    case STUN_ERROR_UNAUTHORIZED:  // Unauthrorized.
      OnAuthChallenge(response, error_code);
      break;
    case STUN_ERROR_TRY_ALTERNATE:
      OnTryAlternate(response, error_code);
      break;
    case STUN_ERROR_ALLOCATION_MISMATCH: {
      // We must handle this error async because trying to delete the socket in
      // OnErrorResponse will cause a deadlock on the socket.
      TurnPort* port = port_;
      port->thread()->PostTask(SafeTask(
          port->task_safety_.flag(), [port] { port->OnAllocateMismatch(); }));
    } break;
    default:
      RTC_LOG(LS_WARNING) << port_->ToString()
                          << ": Received TURN allocate error response, id="
                          << rtc::hex_encode(id()) << ", code=" << error_code
                          << ", rtt=" << Elapsed();
      const StunErrorCodeAttribute* attr = response->GetErrorCode();
      port_->OnAllocateError(error_code, attr ? attr->reason() : "");
  }
}

void TurnAllocateRequest::OnTimeout() {
  RTC_LOG(LS_WARNING) << port_->ToString() << ": TURN allocate request "
                      << rtc::hex_encode(id()) << " timeout";
  port_->OnAllocateRequestTimeout();
}

void TurnAllocateRequest::OnAuthChallenge(StunMessage* response, int code) {
  // If we failed to authenticate even after we sent our credentials, fail hard.
  if (code == STUN_ERROR_UNAUTHORIZED && !port_->hash().empty()) {
    RTC_LOG(LS_WARNING) << port_->ToString()
                        << ": Failed to authenticate with the server "
                           "after challenge.";
    const StunErrorCodeAttribute* attr = response->GetErrorCode();
    port_->OnAllocateError(STUN_ERROR_UNAUTHORIZED, attr ? attr->reason() : "");
    return;
  }

  // Check the mandatory attributes.
  const StunByteStringAttribute* realm_attr =
      response->GetByteString(STUN_ATTR_REALM);
  if (!realm_attr) {
    RTC_LOG(LS_WARNING) << port_->ToString()
                        << ": Missing STUN_ATTR_REALM attribute in "
                           "allocate unauthorized response.";
    return;
  }
  port_->set_realm(realm_attr->string_view());

  const StunByteStringAttribute* nonce_attr =
      response->GetByteString(STUN_ATTR_NONCE);
  if (!nonce_attr) {
    RTC_LOG(LS_WARNING) << port_->ToString()
                        << ": Missing STUN_ATTR_NONCE attribute in "
                           "allocate unauthorized response.";
    return;
  }
  port_->set_nonce(nonce_attr->string_view());

  // Send another allocate request, with the received realm and nonce values.
  port_->SendRequest(new TurnAllocateRequest(port_), 0);
}

void TurnAllocateRequest::OnTryAlternate(StunMessage* response, int code) {
  // According to RFC 5389 section 11, there are use cases where
  // authentication of response is not possible, we're not validating
  // message integrity.
  const StunErrorCodeAttribute* error_code_attr = response->GetErrorCode();
  // Get the alternate server address attribute value.
  const StunAddressAttribute* alternate_server_attr =
      response->GetAddress(STUN_ATTR_ALTERNATE_SERVER);
  if (!alternate_server_attr) {
    RTC_LOG(LS_WARNING) << port_->ToString()
                        << ": Missing STUN_ATTR_ALTERNATE_SERVER "
                           "attribute in try alternate error response";
    port_->OnAllocateError(STUN_ERROR_TRY_ALTERNATE,
                           error_code_attr ? error_code_attr->reason() : "");
    return;
  }
  if (!port_->SetAlternateServer(alternate_server_attr->GetAddress())) {
    port_->OnAllocateError(STUN_ERROR_TRY_ALTERNATE,
                           error_code_attr ? error_code_attr->reason() : "");
    return;
  }

  // Check the attributes.
  const StunByteStringAttribute* realm_attr =
      response->GetByteString(STUN_ATTR_REALM);
  if (realm_attr) {
    RTC_LOG(LS_INFO) << port_->ToString()
                     << ": Applying STUN_ATTR_REALM attribute in "
                        "try alternate error response.";
    port_->set_realm(realm_attr->string_view());
  }

  const StunByteStringAttribute* nonce_attr =
      response->GetByteString(STUN_ATTR_NONCE);
  if (nonce_attr) {
    RTC_LOG(LS_INFO) << port_->ToString()
                     << ": Applying STUN_ATTR_NONCE attribute in "
                        "try alternate error response.";
    port_->set_nonce(nonce_attr->string_view());
  }

  // For TCP, we can't close the original Tcp socket during handling a 300 as
  // we're still inside that socket's event handler. Doing so will cause
  // deadlock.
  TurnPort* port = port_;
  port->thread()->PostTask(SafeTask(port->task_safety_.flag(),
                                    [port] { port->TryAlternateServer(); }));
}

TurnRefreshRequest::TurnRefreshRequest(TurnPort* port, int lifetime /*= -1*/)
    : StunRequest(port->request_manager(),
                  std::make_unique<TurnMessage>(TURN_REFRESH_REQUEST)),
      port_(port) {
  StunMessage* message = mutable_msg();
  // Create the request as indicated in RFC 5766, Section 7.1.
  // No attributes need to be included.
  RTC_DCHECK_EQ(message->type(), TURN_REFRESH_REQUEST);
  if (lifetime > -1) {
    message->AddAttribute(
        std::make_unique<StunUInt32Attribute>(STUN_ATTR_LIFETIME, lifetime));
  }

  port_->AddRequestAuthInfo(message);
  port_->TurnCustomizerMaybeModifyOutgoingStunMessage(message);
}

void TurnRefreshRequest::OnSent() {
  RTC_LOG(LS_INFO) << port_->ToString() << ": TURN refresh request sent, id="
                   << rtc::hex_encode(id());
  StunRequest::OnSent();
}

void TurnRefreshRequest::OnResponse(StunMessage* response) {
  RTC_LOG(LS_INFO) << port_->ToString()
                   << ": TURN refresh requested successfully, id="
                   << rtc::hex_encode(id())
                   << ", code=0"  // Makes logging easier to parse.
                      ", rtt="
                   << Elapsed();

  // Check mandatory attributes as indicated in RFC5766, Section 7.3.
  const StunUInt32Attribute* lifetime_attr =
      response->GetUInt32(STUN_ATTR_TURN_LIFETIME);
  if (!lifetime_attr) {
    RTC_LOG(LS_WARNING) << port_->ToString()
                        << ": Missing STUN_ATTR_TURN_LIFETIME attribute in "
                           "refresh success response.";
    return;
  }

  if (lifetime_attr->value() > 0) {
    // Schedule a refresh based on the returned lifetime value.
    port_->ScheduleRefresh(lifetime_attr->value());
  } else {
    // If we scheduled a refresh with lifetime 0, we're releasing this
    // allocation; see TurnPort::Release.
    TurnPort* port = port_;
    port->thread()->PostTask(
        SafeTask(port->task_safety_.flag(), [port] { port->Close(); }));
  }

  if (port_->callbacks_for_test_) {
    port_->callbacks_for_test_->OnTurnRefreshResult(TURN_SUCCESS_RESULT_CODE);
  }
}

void TurnRefreshRequest::OnErrorResponse(StunMessage* response) {
  int error_code = response->GetErrorCodeValue();

  if (error_code == STUN_ERROR_STALE_NONCE) {
    if (port_->UpdateNonce(response)) {
      // Send RefreshRequest immediately.
      port_->SendRequest(new TurnRefreshRequest(port_), 0);
    }
  } else {
    RTC_LOG(LS_WARNING) << port_->ToString()
                        << ": Received TURN refresh error response, id="
                        << rtc::hex_encode(id()) << ", code=" << error_code
                        << ", rtt=" << Elapsed();
    port_->OnRefreshError();
    if (port_->callbacks_for_test_) {
      port_->callbacks_for_test_->OnTurnRefreshResult(error_code);
    }
  }
}

void TurnRefreshRequest::OnTimeout() {
  RTC_LOG(LS_WARNING) << port_->ToString() << ": TURN refresh timeout "
                      << rtc::hex_encode(id());
  port_->OnRefreshError();
}

TurnCreatePermissionRequest::TurnCreatePermissionRequest(
    TurnPort* port,
    TurnEntry* entry,
    const rtc::SocketAddress& ext_addr)
    : StunRequest(
          port->request_manager(),
          std::make_unique<TurnMessage>(TURN_CREATE_PERMISSION_REQUEST)),
      port_(port),
      entry_(entry),
      ext_addr_(ext_addr) {
  RTC_DCHECK(entry_);
  entry_->destroyed_callback_list_.AddReceiver(this, [this](TurnEntry* entry) {
    RTC_DCHECK(entry_ == entry);
    entry_ = nullptr;
  });
  StunMessage* message = mutable_msg();
  // Create the request as indicated in RFC5766, Section 9.1.
  RTC_DCHECK_EQ(message->type(), TURN_CREATE_PERMISSION_REQUEST);
  message->AddAttribute(std::make_unique<StunXorAddressAttribute>(
      STUN_ATTR_XOR_PEER_ADDRESS, ext_addr_));
  port_->AddRequestAuthInfo(message);
  port_->TurnCustomizerMaybeModifyOutgoingStunMessage(message);
}

TurnCreatePermissionRequest::~TurnCreatePermissionRequest() {
  if (entry_) {
    entry_->destroyed_callback_list_.RemoveReceivers(this);
  }
}

void TurnCreatePermissionRequest::OnSent() {
  RTC_LOG(LS_INFO) << port_->ToString()
                   << ": TURN create permission request sent, id="
                   << rtc::hex_encode(id());
  StunRequest::OnSent();
}

void TurnCreatePermissionRequest::OnResponse(StunMessage* response) {
  RTC_LOG(LS_INFO) << port_->ToString()
                   << ": TURN permission requested successfully, id="
                   << rtc::hex_encode(id())
                   << ", code=0"  // Makes logging easier to parse.
                      ", rtt="
                   << Elapsed();

  if (entry_) {
    entry_->OnCreatePermissionSuccess();
  }
}

void TurnCreatePermissionRequest::OnErrorResponse(StunMessage* response) {
  int error_code = response->GetErrorCodeValue();
  RTC_LOG(LS_WARNING) << port_->ToString()
                      << ": Received TURN create permission error response, id="
                      << rtc::hex_encode(id()) << ", code=" << error_code
                      << ", rtt=" << Elapsed();
  if (entry_) {
    entry_->OnCreatePermissionError(response, error_code);
  }
}

void TurnCreatePermissionRequest::OnTimeout() {
  RTC_LOG(LS_WARNING) << port_->ToString()
                      << ": TURN create permission timeout "
                      << rtc::hex_encode(id());
  if (entry_) {
    entry_->OnCreatePermissionTimeout();
  }
}

TurnChannelBindRequest::TurnChannelBindRequest(
    TurnPort* port,
    TurnEntry* entry,
    int channel_id,
    const rtc::SocketAddress& ext_addr)
    : StunRequest(port->request_manager(),
                  std::make_unique<TurnMessage>(TURN_CHANNEL_BIND_REQUEST)),
      port_(port),
      entry_(entry),
      channel_id_(channel_id),
      ext_addr_(ext_addr) {
  RTC_DCHECK(entry_);
  entry_->destroyed_callback_list_.AddReceiver(this, [this](TurnEntry* entry) {
    RTC_DCHECK(entry_ == entry);
    entry_ = nullptr;
  });
  StunMessage* message = mutable_msg();
  // Create the request as indicated in RFC5766, Section 11.1.
  RTC_DCHECK_EQ(message->type(), TURN_CHANNEL_BIND_REQUEST);
  message->AddAttribute(std::make_unique<StunUInt32Attribute>(
      STUN_ATTR_CHANNEL_NUMBER, channel_id_ << 16));
  message->AddAttribute(std::make_unique<StunXorAddressAttribute>(
      STUN_ATTR_XOR_PEER_ADDRESS, ext_addr_));
  port_->AddRequestAuthInfo(message);
  port_->TurnCustomizerMaybeModifyOutgoingStunMessage(message);
}

TurnChannelBindRequest::~TurnChannelBindRequest() {
  if (entry_) {
    entry_->destroyed_callback_list_.RemoveReceivers(this);
  }
}

void TurnChannelBindRequest::OnSent() {
  RTC_LOG(LS_INFO) << port_->ToString()
                   << ": TURN channel bind request sent, id="
                   << rtc::hex_encode(id());
  StunRequest::OnSent();
}

void TurnChannelBindRequest::OnResponse(StunMessage* response) {
  RTC_LOG(LS_INFO) << port_->ToString()
                   << ": TURN channel bind requested successfully, id="
                   << rtc::hex_encode(id())
                   << ", code=0"  // Makes logging easier to parse.
                      ", rtt="
                   << Elapsed();

  if (entry_) {
    entry_->OnChannelBindSuccess();
    // Refresh the channel binding just under the permission timeout
    // threshold. The channel binding has a longer lifetime, but
    // this is the easiest way to keep both the channel and the
    // permission from expiring.
    TimeDelta delay = kTurnPermissionTimeout - TimeDelta::Minutes(1);
    entry_->SendChannelBindRequest(delay.ms());
    RTC_LOG(LS_INFO) << port_->ToString() << ": Scheduled channel bind in "
                     << delay.ms() << "ms.";
  }
}

void TurnChannelBindRequest::OnErrorResponse(StunMessage* response) {
  int error_code = response->GetErrorCodeValue();
  RTC_LOG(LS_WARNING) << port_->ToString()
                      << ": Received TURN channel bind error response, id="
                      << rtc::hex_encode(id()) << ", code=" << error_code
                      << ", rtt=" << Elapsed();
  if (entry_) {
    entry_->OnChannelBindError(response, error_code);
  }
}

void TurnChannelBindRequest::OnTimeout() {
  RTC_LOG(LS_WARNING) << port_->ToString() << ": TURN channel bind timeout "
                      << rtc::hex_encode(id());
  if (entry_) {
    entry_->OnChannelBindTimeout();
  }
}

TurnEntry::TurnEntry(TurnPort* port, Connection* conn, int channel_id)
    : port_(port),
      channel_id_(channel_id),
      ext_addr_(conn->remote_candidate().address()),
      state_(STATE_UNBOUND),
      connections_({conn}) {
  // Creating permission for `ext_addr_`.
  SendCreatePermissionRequest(0);
}

TurnEntry::~TurnEntry() {
  destroyed_callback_list_.Send(this);
}

void TurnEntry::TrackConnection(Connection* conn) {
  RTC_DCHECK(absl::c_find(connections_, conn) == connections_.end());
  if (connections_.empty()) {
    task_safety_.reset();
  }
  connections_.push_back(conn);
}

rtc::scoped_refptr<webrtc::PendingTaskSafetyFlag> TurnEntry::UntrackConnection(
    Connection* conn) {
  connections_.erase(absl::c_find(connections_, conn));
  return connections_.empty() ? task_safety_.flag() : nullptr;
}

void TurnEntry::SendCreatePermissionRequest(int delay) {
  port_->SendRequest(new TurnCreatePermissionRequest(port_, this, ext_addr_),
                     delay);
}

void TurnEntry::SendChannelBindRequest(int delay) {
  port_->SendRequest(
      new TurnChannelBindRequest(port_, this, channel_id_, ext_addr_), delay);
}

int TurnEntry::Send(const void* data,
                    size_t size,
                    bool payload,
                    const rtc::PacketOptions& options) {
  rtc::ByteBufferWriter buf;
  if (state_ != STATE_BOUND ||
      !port_->TurnCustomizerAllowChannelData(data, size, payload)) {
    // If we haven't bound the channel yet, we have to use a Send Indication.
    // The turn_customizer_ can also make us use Send Indication.
    TurnMessage msg(TURN_SEND_INDICATION);
    msg.AddAttribute(std::make_unique<StunXorAddressAttribute>(
        STUN_ATTR_XOR_PEER_ADDRESS, ext_addr_));
    msg.AddAttribute(
        std::make_unique<StunByteStringAttribute>(STUN_ATTR_DATA, data, size));

    port_->TurnCustomizerMaybeModifyOutgoingStunMessage(&msg);

    const bool success = msg.Write(&buf);
    RTC_DCHECK(success);

    // If we're sending real data, request a channel bind that we can use later.
    if (state_ == STATE_UNBOUND && payload) {
      SendChannelBindRequest(0);
      state_ = STATE_BINDING;
    }
  } else {
    // If the channel is bound, we can send the data as a Channel Message.
    buf.WriteUInt16(channel_id_);
    buf.WriteUInt16(static_cast<uint16_t>(size));
    buf.WriteBytes(reinterpret_cast<const uint8_t*>(data), size);
  }
  rtc::PacketOptions modified_options(options);
  modified_options.info_signaled_after_sent.turn_overhead_bytes =
      buf.Length() - size;
  return port_->Send(buf.Data(), buf.Length(), modified_options);
}

void TurnEntry::OnCreatePermissionSuccess() {
  RTC_LOG(LS_INFO) << port_->ToString() << ": Create permission for "
                   << ext_addr_.ToSensitiveString() << " succeeded";
  if (port_->callbacks_for_test_) {
    port_->callbacks_for_test_->OnTurnCreatePermissionResult(
        TURN_SUCCESS_RESULT_CODE);
  }

  // If `state_` is STATE_BOUND, the permission will be refreshed
  // by ChannelBindRequest.
  if (state_ != STATE_BOUND) {
    // Refresh the permission request about 1 minute before the permission
    // times out.
    TimeDelta delay = kTurnPermissionTimeout - TimeDelta::Minutes(1);
    SendCreatePermissionRequest(delay.ms());
    RTC_LOG(LS_INFO) << port_->ToString()
                     << ": Scheduled create-permission-request in "
                     << delay.ms() << "ms.";
  }
}

void TurnEntry::OnCreatePermissionError(StunMessage* response, int code) {
  if (code == STUN_ERROR_STALE_NONCE) {
    if (port_->UpdateNonce(response)) {
      SendCreatePermissionRequest(0);
    }
  } else {
    bool found = port_->FailAndPruneConnection(ext_addr_);
    if (found) {
      RTC_LOG(LS_ERROR) << "Received TURN CreatePermission error response, "
                           "code="
                        << code << "; pruned connection.";
    }
  }
  if (port_->callbacks_for_test_) {
    port_->callbacks_for_test_->OnTurnCreatePermissionResult(code);
  }
}

void TurnEntry::OnCreatePermissionTimeout() {
  port_->FailAndPruneConnection(ext_addr_);
}

void TurnEntry::OnChannelBindSuccess() {
  RTC_LOG(LS_INFO) << port_->ToString() << ": Successful channel bind for "
                   << ext_addr_.ToSensitiveString();
  RTC_DCHECK(state_ == STATE_BINDING || state_ == STATE_BOUND);
  state_ = STATE_BOUND;
}

void TurnEntry::OnChannelBindError(StunMessage* response, int code) {
  // If the channel bind fails due to errors other than STATE_NONCE,
  // we will fail and prune the connection and rely on ICE restart to
  // re-establish a new connection if needed.
  if (code == STUN_ERROR_STALE_NONCE) {
    if (port_->UpdateNonce(response)) {
      // Send channel bind request with fresh nonce.
      SendChannelBindRequest(0);
    }
  } else {
    state_ = STATE_UNBOUND;
    port_->FailAndPruneConnection(ext_addr_);
  }
}
void TurnEntry::OnChannelBindTimeout() {
  state_ = STATE_UNBOUND;
  port_->FailAndPruneConnection(ext_addr_);
}
}  // namespace cricket
