/*
 *  Copyright 2012 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "p2p/base/turn_server.h"

#include <memory>
#include <tuple>  // for std::tie
#include <utility>

#include "absl/algorithm/container.h"
#include "api/packet_socket_factory.h"
#include "api/transport/stun.h"
#include "p2p/base/async_stun_tcp_socket.h"
#include "rtc_base/bind.h"
#include "rtc_base/byte_buffer.h"
#include "rtc_base/checks.h"
#include "rtc_base/helpers.h"
#include "rtc_base/logging.h"
#include "rtc_base/message_digest.h"
#include "rtc_base/socket_adapters.h"
#include "rtc_base/strings/string_builder.h"
#include "rtc_base/thread.h"

namespace cricket {

// TODO(juberti): Move this all to a future turnmessage.h
//  static const int IPPROTO_UDP = 17;
static const int kNonceTimeout = 60 * 60 * 1000;              // 60 minutes
static const int kDefaultAllocationTimeout = 10 * 60 * 1000;  // 10 minutes
static const int kPermissionTimeout = 5 * 60 * 1000;          //  5 minutes
static const int kChannelTimeout = 10 * 60 * 1000;            // 10 minutes

static const int kMinChannelNumber = 0x4000;
static const int kMaxChannelNumber = 0x7FFF;

static const size_t kNonceKeySize = 16;
static const size_t kNonceSize = 48;

static const size_t TURN_CHANNEL_HEADER_SIZE = 4U;

// TODO(mallinath) - Move these to a common place.
inline bool IsTurnChannelData(uint16_t msg_type) {
  // The first two bits of a channel data message are 0b01.
  return ((msg_type & 0xC000) == 0x4000);
}

// IDs used for posted messages for TurnServerAllocation.
enum {
  MSG_ALLOCATION_TIMEOUT,
};

// Encapsulates a TURN permission.
// The object is created when a create permission request is received by an
// allocation, and self-deletes when its lifetime timer expires.
class TurnServerAllocation::Permission : public rtc::MessageHandler {
 public:
  Permission(rtc::Thread* thread, const rtc::IPAddress& peer);
  ~Permission() override;

  const rtc::IPAddress& peer() const { return peer_; }
  void Refresh();

  sigslot::signal1<Permission*> SignalDestroyed;

 private:
  void OnMessage(rtc::Message* msg) override;

  rtc::Thread* thread_;
  rtc::IPAddress peer_;
};

// Encapsulates a TURN channel binding.
// The object is created when a channel bind request is received by an
// allocation, and self-deletes when its lifetime timer expires.
class TurnServerAllocation::Channel : public rtc::MessageHandler {
 public:
  Channel(rtc::Thread* thread, int id, const rtc::SocketAddress& peer);
  ~Channel() override;

  int id() const { return id_; }
  const rtc::SocketAddress& peer() const { return peer_; }
  void Refresh();

  sigslot::signal1<Channel*> SignalDestroyed;

 private:
  void OnMessage(rtc::Message* msg) override;

  rtc::Thread* thread_;
  int id_;
  rtc::SocketAddress peer_;
};

static bool InitResponse(const StunMessage* req, StunMessage* resp) {
  int resp_type = (req) ? GetStunSuccessResponseType(req->type()) : -1;
  if (resp_type == -1)
    return false;
  resp->SetType(resp_type);
  resp->SetTransactionID(req->transaction_id());
  return true;
}

static bool InitErrorResponse(const StunMessage* req,
                              int code,
                              const std::string& reason,
                              StunMessage* resp) {
  int resp_type = (req) ? GetStunErrorResponseType(req->type()) : -1;
  if (resp_type == -1)
    return false;
  resp->SetType(resp_type);
  resp->SetTransactionID(req->transaction_id());
  resp->AddAttribute(std::make_unique<cricket::StunErrorCodeAttribute>(
      STUN_ATTR_ERROR_CODE, code, reason));
  return true;
}

TurnServer::TurnServer(rtc::Thread* thread)
    : thread_(thread),
      nonce_key_(rtc::CreateRandomString(kNonceKeySize)),
      auth_hook_(NULL),
      redirect_hook_(NULL),
      enable_otu_nonce_(false) {}

TurnServer::~TurnServer() {
  RTC_DCHECK(thread_checker_.IsCurrent());
  for (InternalSocketMap::iterator it = server_sockets_.begin();
       it != server_sockets_.end(); ++it) {
    rtc::AsyncPacketSocket* socket = it->first;
    delete socket;
  }

  for (ServerSocketMap::iterator it = server_listen_sockets_.begin();
       it != server_listen_sockets_.end(); ++it) {
    rtc::AsyncSocket* socket = it->first;
    delete socket;
  }
}

void TurnServer::AddInternalSocket(rtc::AsyncPacketSocket* socket,
                                   ProtocolType proto) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  RTC_DCHECK(server_sockets_.end() == server_sockets_.find(socket));
  server_sockets_[socket] = proto;
  socket->SignalReadPacket.connect(this, &TurnServer::OnInternalPacket);
}

void TurnServer::AddInternalServerSocket(rtc::AsyncSocket* socket,
                                         ProtocolType proto) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  RTC_DCHECK(server_listen_sockets_.end() ==
             server_listen_sockets_.find(socket));
  server_listen_sockets_[socket] = proto;
  socket->SignalReadEvent.connect(this, &TurnServer::OnNewInternalConnection);
}

void TurnServer::SetExternalSocketFactory(
    rtc::PacketSocketFactory* factory,
    const rtc::SocketAddress& external_addr) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  external_socket_factory_.reset(factory);
  external_addr_ = external_addr;
}

void TurnServer::OnNewInternalConnection(rtc::AsyncSocket* socket) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  RTC_DCHECK(server_listen_sockets_.find(socket) !=
             server_listen_sockets_.end());
  AcceptConnection(socket);
}

void TurnServer::AcceptConnection(rtc::AsyncSocket* server_socket) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  // Check if someone is trying to connect to us.
  rtc::SocketAddress accept_addr;
  rtc::AsyncSocket* accepted_socket = server_socket->Accept(&accept_addr);
  if (accepted_socket != NULL) {
    ProtocolType proto = server_listen_sockets_[server_socket];
    cricket::AsyncStunTCPSocket* tcp_socket =
        new cricket::AsyncStunTCPSocket(accepted_socket, false);

    tcp_socket->SignalClose.connect(this, &TurnServer::OnInternalSocketClose);
    // Finally add the socket so it can start communicating with the client.
    AddInternalSocket(tcp_socket, proto);
  }
}

void TurnServer::OnInternalSocketClose(rtc::AsyncPacketSocket* socket,
                                       int err) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  DestroyInternalSocket(socket);
}

void TurnServer::OnInternalPacket(rtc::AsyncPacketSocket* socket,
                                  const char* data,
                                  size_t size,
                                  const rtc::SocketAddress& addr,
                                  const int64_t& /* packet_time_us */) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  // Fail if the packet is too small to even contain a channel header.
  if (size < TURN_CHANNEL_HEADER_SIZE) {
    return;
  }
  InternalSocketMap::iterator iter = server_sockets_.find(socket);
  RTC_DCHECK(iter != server_sockets_.end());
  TurnServerConnection conn(addr, iter->second, socket);
  uint16_t msg_type = rtc::GetBE16(data);
  if (!IsTurnChannelData(msg_type)) {
    // This is a STUN message.
    HandleStunMessage(&conn, data, size);
  } else {
    // This is a channel message; let the allocation handle it.
    TurnServerAllocation* allocation = FindAllocation(&conn);
    if (allocation) {
      allocation->HandleChannelData(data, size);
    }
    if (stun_message_observer_ != nullptr) {
      stun_message_observer_->ReceivedChannelData(data, size);
    }
  }
}

void TurnServer::HandleStunMessage(TurnServerConnection* conn,
                                   const char* data,
                                   size_t size) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  TurnMessage msg;
  rtc::ByteBufferReader buf(data, size);
  if (!msg.Read(&buf) || (buf.Length() > 0)) {
    RTC_LOG(LS_WARNING) << "Received invalid STUN message";
    return;
  }

  if (stun_message_observer_ != nullptr) {
    stun_message_observer_->ReceivedMessage(&msg);
  }

  // If it's a STUN binding request, handle that specially.
  if (msg.type() == STUN_BINDING_REQUEST) {
    HandleBindingRequest(conn, &msg);
    return;
  }

  if (redirect_hook_ != NULL && msg.type() == STUN_ALLOCATE_REQUEST) {
    rtc::SocketAddress address;
    if (redirect_hook_->ShouldRedirect(conn->src(), &address)) {
      SendErrorResponseWithAlternateServer(conn, &msg, address);
      return;
    }
  }

  // Look up the key that we'll use to validate the M-I. If we have an
  // existing allocation, the key will already be cached.
  TurnServerAllocation* allocation = FindAllocation(conn);
  std::string key;
  if (!allocation) {
    GetKey(&msg, &key);
  } else {
    key = allocation->key();
  }

  // Ensure the message is authorized; only needed for requests.
  if (IsStunRequestType(msg.type())) {
    if (!CheckAuthorization(conn, &msg, data, size, key)) {
      return;
    }
  }

  if (!allocation && msg.type() == STUN_ALLOCATE_REQUEST) {
    HandleAllocateRequest(conn, &msg, key);
  } else if (allocation &&
             (msg.type() != STUN_ALLOCATE_REQUEST ||
              msg.transaction_id() == allocation->transaction_id())) {
    // This is a non-allocate request, or a retransmit of an allocate.
    // Check that the username matches the previous username used.
    if (IsStunRequestType(msg.type()) &&
        msg.GetByteString(STUN_ATTR_USERNAME)->GetString() !=
            allocation->username()) {
      SendErrorResponse(conn, &msg, STUN_ERROR_WRONG_CREDENTIALS,
                        STUN_ERROR_REASON_WRONG_CREDENTIALS);
      return;
    }
    allocation->HandleTurnMessage(&msg);
  } else {
    // Allocation mismatch.
    SendErrorResponse(conn, &msg, STUN_ERROR_ALLOCATION_MISMATCH,
                      STUN_ERROR_REASON_ALLOCATION_MISMATCH);
  }
}

bool TurnServer::GetKey(const StunMessage* msg, std::string* key) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  const StunByteStringAttribute* username_attr =
      msg->GetByteString(STUN_ATTR_USERNAME);
  if (!username_attr) {
    return false;
  }

  std::string username = username_attr->GetString();
  return (auth_hook_ != NULL && auth_hook_->GetKey(username, realm_, key));
}

bool TurnServer::CheckAuthorization(TurnServerConnection* conn,
                                    const StunMessage* msg,
                                    const char* data,
                                    size_t size,
                                    const std::string& key) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  // RFC 5389, 10.2.2.
  RTC_DCHECK(IsStunRequestType(msg->type()));
  const StunByteStringAttribute* mi_attr =
      msg->GetByteString(STUN_ATTR_MESSAGE_INTEGRITY);
  const StunByteStringAttribute* username_attr =
      msg->GetByteString(STUN_ATTR_USERNAME);
  const StunByteStringAttribute* realm_attr =
      msg->GetByteString(STUN_ATTR_REALM);
  const StunByteStringAttribute* nonce_attr =
      msg->GetByteString(STUN_ATTR_NONCE);

  // Fail if no M-I.
  if (!mi_attr) {
    SendErrorResponseWithRealmAndNonce(conn, msg, STUN_ERROR_UNAUTHORIZED,
                                       STUN_ERROR_REASON_UNAUTHORIZED);
    return false;
  }

  // Fail if there is M-I but no username, nonce, or realm.
  if (!username_attr || !realm_attr || !nonce_attr) {
    SendErrorResponse(conn, msg, STUN_ERROR_BAD_REQUEST,
                      STUN_ERROR_REASON_BAD_REQUEST);
    return false;
  }

  // Fail if bad nonce.
  if (!ValidateNonce(nonce_attr->GetString())) {
    SendErrorResponseWithRealmAndNonce(conn, msg, STUN_ERROR_STALE_NONCE,
                                       STUN_ERROR_REASON_STALE_NONCE);
    return false;
  }

  // Fail if bad username or M-I.
  // We need |data| and |size| for the call to ValidateMessageIntegrity.
  if (key.empty() || !StunMessage::ValidateMessageIntegrity(data, size, key)) {
    SendErrorResponseWithRealmAndNonce(conn, msg, STUN_ERROR_UNAUTHORIZED,
                                       STUN_ERROR_REASON_UNAUTHORIZED);
    return false;
  }

  // Fail if one-time-use nonce feature is enabled.
  TurnServerAllocation* allocation = FindAllocation(conn);
  if (enable_otu_nonce_ && allocation &&
      allocation->last_nonce() == nonce_attr->GetString()) {
    SendErrorResponseWithRealmAndNonce(conn, msg, STUN_ERROR_STALE_NONCE,
                                       STUN_ERROR_REASON_STALE_NONCE);
    return false;
  }

  if (allocation) {
    allocation->set_last_nonce(nonce_attr->GetString());
  }
  // Success.
  return true;
}

void TurnServer::HandleBindingRequest(TurnServerConnection* conn,
                                      const StunMessage* req) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  StunMessage response;
  InitResponse(req, &response);

  // Tell the user the address that we received their request from.
  auto mapped_addr_attr = std::make_unique<StunXorAddressAttribute>(
      STUN_ATTR_XOR_MAPPED_ADDRESS, conn->src());
  response.AddAttribute(std::move(mapped_addr_attr));

  SendStun(conn, &response);
}

void TurnServer::HandleAllocateRequest(TurnServerConnection* conn,
                                       const TurnMessage* msg,
                                       const std::string& key) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  // Check the parameters in the request.
  const StunUInt32Attribute* transport_attr =
      msg->GetUInt32(STUN_ATTR_REQUESTED_TRANSPORT);
  if (!transport_attr) {
    SendErrorResponse(conn, msg, STUN_ERROR_BAD_REQUEST,
                      STUN_ERROR_REASON_BAD_REQUEST);
    return;
  }

  // Only UDP is supported right now.
  int proto = transport_attr->value() >> 24;
  if (proto != IPPROTO_UDP) {
    SendErrorResponse(conn, msg, STUN_ERROR_UNSUPPORTED_PROTOCOL,
                      STUN_ERROR_REASON_UNSUPPORTED_PROTOCOL);
    return;
  }

  // Create the allocation and let it send the success response.
  // If the actual socket allocation fails, send an internal error.
  TurnServerAllocation* alloc = CreateAllocation(conn, proto, key);
  if (alloc) {
    alloc->HandleTurnMessage(msg);
  } else {
    SendErrorResponse(conn, msg, STUN_ERROR_SERVER_ERROR,
                      "Failed to allocate socket");
  }
}

std::string TurnServer::GenerateNonce(int64_t now) const {
  RTC_DCHECK(thread_checker_.IsCurrent());
  // Generate a nonce of the form hex(now + HMAC-MD5(nonce_key_, now))
  std::string input(reinterpret_cast<const char*>(&now), sizeof(now));
  std::string nonce = rtc::hex_encode(input.c_str(), input.size());
  nonce += rtc::ComputeHmac(rtc::DIGEST_MD5, nonce_key_, input);
  RTC_DCHECK(nonce.size() == kNonceSize);

  return nonce;
}

bool TurnServer::ValidateNonce(const std::string& nonce) const {
  RTC_DCHECK(thread_checker_.IsCurrent());
  // Check the size.
  if (nonce.size() != kNonceSize) {
    return false;
  }

  // Decode the timestamp.
  int64_t then;
  char* p = reinterpret_cast<char*>(&then);
  size_t len =
      rtc::hex_decode(p, sizeof(then), nonce.substr(0, sizeof(then) * 2));
  if (len != sizeof(then)) {
    return false;
  }

  // Verify the HMAC.
  if (nonce.substr(sizeof(then) * 2) !=
      rtc::ComputeHmac(rtc::DIGEST_MD5, nonce_key_,
                       std::string(p, sizeof(then)))) {
    return false;
  }

  // Validate the timestamp.
  return rtc::TimeMillis() - then < kNonceTimeout;
}

TurnServerAllocation* TurnServer::FindAllocation(TurnServerConnection* conn) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  AllocationMap::const_iterator it = allocations_.find(*conn);
  return (it != allocations_.end()) ? it->second.get() : nullptr;
}

TurnServerAllocation* TurnServer::CreateAllocation(TurnServerConnection* conn,
                                                   int proto,
                                                   const std::string& key) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  rtc::AsyncPacketSocket* external_socket =
      (external_socket_factory_)
          ? external_socket_factory_->CreateUdpSocket(external_addr_, 0, 0)
          : NULL;
  if (!external_socket) {
    return NULL;
  }

  // The Allocation takes ownership of the socket.
  TurnServerAllocation* allocation =
      new TurnServerAllocation(this, thread_, *conn, external_socket, key);
  allocation->SignalDestroyed.connect(this, &TurnServer::OnAllocationDestroyed);
  allocations_[*conn].reset(allocation);
  return allocation;
}

void TurnServer::SendErrorResponse(TurnServerConnection* conn,
                                   const StunMessage* req,
                                   int code,
                                   const std::string& reason) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  TurnMessage resp;
  InitErrorResponse(req, code, reason, &resp);
  RTC_LOG(LS_INFO) << "Sending error response, type=" << resp.type()
                   << ", code=" << code << ", reason=" << reason;
  SendStun(conn, &resp);
}

void TurnServer::SendErrorResponseWithRealmAndNonce(TurnServerConnection* conn,
                                                    const StunMessage* msg,
                                                    int code,
                                                    const std::string& reason) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  TurnMessage resp;
  InitErrorResponse(msg, code, reason, &resp);

  int64_t timestamp = rtc::TimeMillis();
  if (ts_for_next_nonce_) {
    timestamp = ts_for_next_nonce_;
    ts_for_next_nonce_ = 0;
  }
  resp.AddAttribute(std::make_unique<StunByteStringAttribute>(
      STUN_ATTR_NONCE, GenerateNonce(timestamp)));
  resp.AddAttribute(
      std::make_unique<StunByteStringAttribute>(STUN_ATTR_REALM, realm_));
  SendStun(conn, &resp);
}

void TurnServer::SendErrorResponseWithAlternateServer(
    TurnServerConnection* conn,
    const StunMessage* msg,
    const rtc::SocketAddress& addr) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  TurnMessage resp;
  InitErrorResponse(msg, STUN_ERROR_TRY_ALTERNATE,
                    STUN_ERROR_REASON_TRY_ALTERNATE_SERVER, &resp);
  resp.AddAttribute(
      std::make_unique<StunAddressAttribute>(STUN_ATTR_ALTERNATE_SERVER, addr));
  SendStun(conn, &resp);
}

void TurnServer::SendStun(TurnServerConnection* conn, StunMessage* msg) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  rtc::ByteBufferWriter buf;
  // Add a SOFTWARE attribute if one is set.
  if (!software_.empty()) {
    msg->AddAttribute(std::make_unique<StunByteStringAttribute>(
        STUN_ATTR_SOFTWARE, software_));
  }
  msg->Write(&buf);
  Send(conn, buf);
}

void TurnServer::Send(TurnServerConnection* conn,
                      const rtc::ByteBufferWriter& buf) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  rtc::PacketOptions options;
  conn->socket()->SendTo(buf.Data(), buf.Length(), conn->src(), options);
}

void TurnServer::OnAllocationDestroyed(TurnServerAllocation* allocation) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  // Removing the internal socket if the connection is not udp.
  rtc::AsyncPacketSocket* socket = allocation->conn()->socket();
  InternalSocketMap::iterator iter = server_sockets_.find(socket);
  // Skip if the socket serving this allocation is UDP, as this will be shared
  // by all allocations.
  // Note: We may not find a socket if it's a TCP socket that was closed, and
  // the allocation is only now timing out.
  if (iter != server_sockets_.end() && iter->second != cricket::PROTO_UDP) {
    DestroyInternalSocket(socket);
  }

  AllocationMap::iterator it = allocations_.find(*(allocation->conn()));
  if (it != allocations_.end()) {
    it->second.release();
    allocations_.erase(it);
  }
}

void TurnServer::DestroyInternalSocket(rtc::AsyncPacketSocket* socket) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  InternalSocketMap::iterator iter = server_sockets_.find(socket);
  if (iter != server_sockets_.end()) {
    rtc::AsyncPacketSocket* socket = iter->first;
    socket->SignalReadPacket.disconnect(this);
    server_sockets_.erase(iter);
    // We must destroy the socket async to avoid invalidating the sigslot
    // callback list iterator inside a sigslot callback. (In other words,
    // deleting an object from within a callback from that object).
    sockets_to_delete_.push_back(
        std::unique_ptr<rtc::AsyncPacketSocket>(socket));
    invoker_.AsyncInvoke<void>(RTC_FROM_HERE, rtc::Thread::Current(),
                               rtc::Bind(&TurnServer::FreeSockets, this));
  }
}

void TurnServer::FreeSockets() {
  RTC_DCHECK(thread_checker_.IsCurrent());
  sockets_to_delete_.clear();
}

TurnServerConnection::TurnServerConnection(const rtc::SocketAddress& src,
                                           ProtocolType proto,
                                           rtc::AsyncPacketSocket* socket)
    : src_(src),
      dst_(socket->GetRemoteAddress()),
      proto_(proto),
      socket_(socket) {}

bool TurnServerConnection::operator==(const TurnServerConnection& c) const {
  return src_ == c.src_ && dst_ == c.dst_ && proto_ == c.proto_;
}

bool TurnServerConnection::operator<(const TurnServerConnection& c) const {
  return std::tie(src_, dst_, proto_) < std::tie(c.src_, c.dst_, c.proto_);
}

std::string TurnServerConnection::ToString() const {
  const char* const kProtos[] = {"unknown", "udp", "tcp", "ssltcp"};
  rtc::StringBuilder ost;
  ost << src_.ToSensitiveString() << "-" << dst_.ToSensitiveString() << ":"
      << kProtos[proto_];
  return ost.Release();
}

TurnServerAllocation::TurnServerAllocation(TurnServer* server,
                                           rtc::Thread* thread,
                                           const TurnServerConnection& conn,
                                           rtc::AsyncPacketSocket* socket,
                                           const std::string& key)
    : server_(server),
      thread_(thread),
      conn_(conn),
      external_socket_(socket),
      key_(key) {
  external_socket_->SignalReadPacket.connect(
      this, &TurnServerAllocation::OnExternalPacket);
}

TurnServerAllocation::~TurnServerAllocation() {
  for (ChannelList::iterator it = channels_.begin(); it != channels_.end();
       ++it) {
    delete *it;
  }
  for (PermissionList::iterator it = perms_.begin(); it != perms_.end(); ++it) {
    delete *it;
  }
  thread_->Clear(this, MSG_ALLOCATION_TIMEOUT);
  RTC_LOG(LS_INFO) << ToString() << ": Allocation destroyed";
}

std::string TurnServerAllocation::ToString() const {
  rtc::StringBuilder ost;
  ost << "Alloc[" << conn_.ToString() << "]";
  return ost.Release();
}

void TurnServerAllocation::HandleTurnMessage(const TurnMessage* msg) {
  RTC_DCHECK(msg != NULL);
  switch (msg->type()) {
    case STUN_ALLOCATE_REQUEST:
      HandleAllocateRequest(msg);
      break;
    case TURN_REFRESH_REQUEST:
      HandleRefreshRequest(msg);
      break;
    case TURN_SEND_INDICATION:
      HandleSendIndication(msg);
      break;
    case TURN_CREATE_PERMISSION_REQUEST:
      HandleCreatePermissionRequest(msg);
      break;
    case TURN_CHANNEL_BIND_REQUEST:
      HandleChannelBindRequest(msg);
      break;
    default:
      // Not sure what to do with this, just eat it.
      RTC_LOG(LS_WARNING) << ToString()
                          << ": Invalid TURN message type received: "
                          << msg->type();
  }
}

void TurnServerAllocation::HandleAllocateRequest(const TurnMessage* msg) {
  // Copy the important info from the allocate request.
  transaction_id_ = msg->transaction_id();
  const StunByteStringAttribute* username_attr =
      msg->GetByteString(STUN_ATTR_USERNAME);
  RTC_DCHECK(username_attr != NULL);
  username_ = username_attr->GetString();
  const StunByteStringAttribute* origin_attr =
      msg->GetByteString(STUN_ATTR_ORIGIN);
  if (origin_attr) {
    origin_ = origin_attr->GetString();
  }

  // Figure out the lifetime and start the allocation timer.
  int lifetime_secs = ComputeLifetime(msg);
  thread_->PostDelayed(RTC_FROM_HERE, lifetime_secs * 1000, this,
                       MSG_ALLOCATION_TIMEOUT);

  RTC_LOG(LS_INFO) << ToString()
                   << ": Created allocation with lifetime=" << lifetime_secs;

  // We've already validated all the important bits; just send a response here.
  TurnMessage response;
  InitResponse(msg, &response);

  auto mapped_addr_attr = std::make_unique<StunXorAddressAttribute>(
      STUN_ATTR_XOR_MAPPED_ADDRESS, conn_.src());
  auto relayed_addr_attr = std::make_unique<StunXorAddressAttribute>(
      STUN_ATTR_XOR_RELAYED_ADDRESS, external_socket_->GetLocalAddress());
  auto lifetime_attr =
      std::make_unique<StunUInt32Attribute>(STUN_ATTR_LIFETIME, lifetime_secs);
  response.AddAttribute(std::move(mapped_addr_attr));
  response.AddAttribute(std::move(relayed_addr_attr));
  response.AddAttribute(std::move(lifetime_attr));

  SendResponse(&response);
}

void TurnServerAllocation::HandleRefreshRequest(const TurnMessage* msg) {
  // Figure out the new lifetime.
  int lifetime_secs = ComputeLifetime(msg);

  // Reset the expiration timer.
  thread_->Clear(this, MSG_ALLOCATION_TIMEOUT);
  thread_->PostDelayed(RTC_FROM_HERE, lifetime_secs * 1000, this,
                       MSG_ALLOCATION_TIMEOUT);

  RTC_LOG(LS_INFO) << ToString()
                   << ": Refreshed allocation, lifetime=" << lifetime_secs;

  // Send a success response with a LIFETIME attribute.
  TurnMessage response;
  InitResponse(msg, &response);

  auto lifetime_attr =
      std::make_unique<StunUInt32Attribute>(STUN_ATTR_LIFETIME, lifetime_secs);
  response.AddAttribute(std::move(lifetime_attr));

  SendResponse(&response);
}

void TurnServerAllocation::HandleSendIndication(const TurnMessage* msg) {
  // Check mandatory attributes.
  const StunByteStringAttribute* data_attr = msg->GetByteString(STUN_ATTR_DATA);
  const StunAddressAttribute* peer_attr =
      msg->GetAddress(STUN_ATTR_XOR_PEER_ADDRESS);
  if (!data_attr || !peer_attr) {
    RTC_LOG(LS_WARNING) << ToString() << ": Received invalid send indication";
    return;
  }

  // If a permission exists, send the data on to the peer.
  if (HasPermission(peer_attr->GetAddress().ipaddr())) {
    SendExternal(data_attr->bytes(), data_attr->length(),
                 peer_attr->GetAddress());
  } else {
    RTC_LOG(LS_WARNING) << ToString()
                        << ": Received send indication without permission"
                           " peer="
                        << peer_attr->GetAddress().ToSensitiveString();
  }
}

void TurnServerAllocation::HandleCreatePermissionRequest(
    const TurnMessage* msg) {
  // Check mandatory attributes.
  const StunAddressAttribute* peer_attr =
      msg->GetAddress(STUN_ATTR_XOR_PEER_ADDRESS);
  if (!peer_attr) {
    SendBadRequestResponse(msg);
    return;
  }

  if (server_->reject_private_addresses_ &&
      rtc::IPIsPrivate(peer_attr->GetAddress().ipaddr())) {
    SendErrorResponse(msg, STUN_ERROR_FORBIDDEN, STUN_ERROR_REASON_FORBIDDEN);
    return;
  }

  // Add this permission.
  AddPermission(peer_attr->GetAddress().ipaddr());

  RTC_LOG(LS_INFO) << ToString() << ": Created permission, peer="
                   << peer_attr->GetAddress().ToSensitiveString();

  // Send a success response.
  TurnMessage response;
  InitResponse(msg, &response);
  SendResponse(&response);
}

void TurnServerAllocation::HandleChannelBindRequest(const TurnMessage* msg) {
  // Check mandatory attributes.
  const StunUInt32Attribute* channel_attr =
      msg->GetUInt32(STUN_ATTR_CHANNEL_NUMBER);
  const StunAddressAttribute* peer_attr =
      msg->GetAddress(STUN_ATTR_XOR_PEER_ADDRESS);
  if (!channel_attr || !peer_attr) {
    SendBadRequestResponse(msg);
    return;
  }

  // Check that channel id is valid.
  int channel_id = channel_attr->value() >> 16;
  if (channel_id < kMinChannelNumber || channel_id > kMaxChannelNumber) {
    SendBadRequestResponse(msg);
    return;
  }

  // Check that this channel id isn't bound to another transport address, and
  // that this transport address isn't bound to another channel id.
  Channel* channel1 = FindChannel(channel_id);
  Channel* channel2 = FindChannel(peer_attr->GetAddress());
  if (channel1 != channel2) {
    SendBadRequestResponse(msg);
    return;
  }

  // Add or refresh this channel.
  if (!channel1) {
    channel1 = new Channel(thread_, channel_id, peer_attr->GetAddress());
    channel1->SignalDestroyed.connect(
        this, &TurnServerAllocation::OnChannelDestroyed);
    channels_.push_back(channel1);
  } else {
    channel1->Refresh();
  }

  // Channel binds also refresh permissions.
  AddPermission(peer_attr->GetAddress().ipaddr());

  RTC_LOG(LS_INFO) << ToString() << ": Bound channel, id=" << channel_id
                   << ", peer=" << peer_attr->GetAddress().ToSensitiveString();

  // Send a success response.
  TurnMessage response;
  InitResponse(msg, &response);
  SendResponse(&response);
}

void TurnServerAllocation::HandleChannelData(const char* data, size_t size) {
  // Extract the channel number from the data.
  uint16_t channel_id = rtc::GetBE16(data);
  Channel* channel = FindChannel(channel_id);
  if (channel) {
    // Send the data to the peer address.
    SendExternal(data + TURN_CHANNEL_HEADER_SIZE,
                 size - TURN_CHANNEL_HEADER_SIZE, channel->peer());
  } else {
    RTC_LOG(LS_WARNING) << ToString()
                        << ": Received channel data for invalid channel, id="
                        << channel_id;
  }
}

void TurnServerAllocation::OnExternalPacket(
    rtc::AsyncPacketSocket* socket,
    const char* data,
    size_t size,
    const rtc::SocketAddress& addr,
    const int64_t& /* packet_time_us */) {
  RTC_DCHECK(external_socket_.get() == socket);
  Channel* channel = FindChannel(addr);
  if (channel) {
    // There is a channel bound to this address. Send as a channel message.
    rtc::ByteBufferWriter buf;
    buf.WriteUInt16(channel->id());
    buf.WriteUInt16(static_cast<uint16_t>(size));
    buf.WriteBytes(data, size);
    server_->Send(&conn_, buf);
  } else if (!server_->enable_permission_checks_ ||
             HasPermission(addr.ipaddr())) {
    // No channel, but a permission exists. Send as a data indication.
    TurnMessage msg;
    msg.SetType(TURN_DATA_INDICATION);
    msg.SetTransactionID(rtc::CreateRandomString(kStunTransactionIdLength));
    msg.AddAttribute(std::make_unique<StunXorAddressAttribute>(
        STUN_ATTR_XOR_PEER_ADDRESS, addr));
    msg.AddAttribute(
        std::make_unique<StunByteStringAttribute>(STUN_ATTR_DATA, data, size));
    server_->SendStun(&conn_, &msg);
  } else {
    RTC_LOG(LS_WARNING)
        << ToString() << ": Received external packet without permission, peer="
        << addr.ToSensitiveString();
  }
}

int TurnServerAllocation::ComputeLifetime(const TurnMessage* msg) {
  // Return the smaller of our default lifetime and the requested lifetime.
  int lifetime = kDefaultAllocationTimeout / 1000;  // convert to seconds
  const StunUInt32Attribute* lifetime_attr = msg->GetUInt32(STUN_ATTR_LIFETIME);
  if (lifetime_attr && static_cast<int>(lifetime_attr->value()) < lifetime) {
    lifetime = static_cast<int>(lifetime_attr->value());
  }
  return lifetime;
}

bool TurnServerAllocation::HasPermission(const rtc::IPAddress& addr) {
  return (FindPermission(addr) != NULL);
}

void TurnServerAllocation::AddPermission(const rtc::IPAddress& addr) {
  Permission* perm = FindPermission(addr);
  if (!perm) {
    perm = new Permission(thread_, addr);
    perm->SignalDestroyed.connect(this,
                                  &TurnServerAllocation::OnPermissionDestroyed);
    perms_.push_back(perm);
  } else {
    perm->Refresh();
  }
}

TurnServerAllocation::Permission* TurnServerAllocation::FindPermission(
    const rtc::IPAddress& addr) const {
  for (PermissionList::const_iterator it = perms_.begin(); it != perms_.end();
       ++it) {
    if ((*it)->peer() == addr)
      return *it;
  }
  return NULL;
}

TurnServerAllocation::Channel* TurnServerAllocation::FindChannel(
    int channel_id) const {
  for (ChannelList::const_iterator it = channels_.begin();
       it != channels_.end(); ++it) {
    if ((*it)->id() == channel_id)
      return *it;
  }
  return NULL;
}

TurnServerAllocation::Channel* TurnServerAllocation::FindChannel(
    const rtc::SocketAddress& addr) const {
  for (ChannelList::const_iterator it = channels_.begin();
       it != channels_.end(); ++it) {
    if ((*it)->peer() == addr)
      return *it;
  }
  return NULL;
}

void TurnServerAllocation::SendResponse(TurnMessage* msg) {
  // Success responses always have M-I.
  msg->AddMessageIntegrity(key_);
  server_->SendStun(&conn_, msg);
}

void TurnServerAllocation::SendBadRequestResponse(const TurnMessage* req) {
  SendErrorResponse(req, STUN_ERROR_BAD_REQUEST, STUN_ERROR_REASON_BAD_REQUEST);
}

void TurnServerAllocation::SendErrorResponse(const TurnMessage* req,
                                             int code,
                                             const std::string& reason) {
  server_->SendErrorResponse(&conn_, req, code, reason);
}

void TurnServerAllocation::SendExternal(const void* data,
                                        size_t size,
                                        const rtc::SocketAddress& peer) {
  rtc::PacketOptions options;
  external_socket_->SendTo(data, size, peer, options);
}

void TurnServerAllocation::OnMessage(rtc::Message* msg) {
  RTC_DCHECK(msg->message_id == MSG_ALLOCATION_TIMEOUT);
  SignalDestroyed(this);
  delete this;
}

void TurnServerAllocation::OnPermissionDestroyed(Permission* perm) {
  auto it = absl::c_find(perms_, perm);
  RTC_DCHECK(it != perms_.end());
  perms_.erase(it);
}

void TurnServerAllocation::OnChannelDestroyed(Channel* channel) {
  auto it = absl::c_find(channels_, channel);
  RTC_DCHECK(it != channels_.end());
  channels_.erase(it);
}

TurnServerAllocation::Permission::Permission(rtc::Thread* thread,
                                             const rtc::IPAddress& peer)
    : thread_(thread), peer_(peer) {
  Refresh();
}

TurnServerAllocation::Permission::~Permission() {
  thread_->Clear(this, MSG_ALLOCATION_TIMEOUT);
}

void TurnServerAllocation::Permission::Refresh() {
  thread_->Clear(this, MSG_ALLOCATION_TIMEOUT);
  thread_->PostDelayed(RTC_FROM_HERE, kPermissionTimeout, this,
                       MSG_ALLOCATION_TIMEOUT);
}

void TurnServerAllocation::Permission::OnMessage(rtc::Message* msg) {
  RTC_DCHECK(msg->message_id == MSG_ALLOCATION_TIMEOUT);
  SignalDestroyed(this);
  delete this;
}

TurnServerAllocation::Channel::Channel(rtc::Thread* thread,
                                       int id,
                                       const rtc::SocketAddress& peer)
    : thread_(thread), id_(id), peer_(peer) {
  Refresh();
}

TurnServerAllocation::Channel::~Channel() {
  thread_->Clear(this, MSG_ALLOCATION_TIMEOUT);
}

void TurnServerAllocation::Channel::Refresh() {
  thread_->Clear(this, MSG_ALLOCATION_TIMEOUT);
  thread_->PostDelayed(RTC_FROM_HERE, kChannelTimeout, this,
                       MSG_ALLOCATION_TIMEOUT);
}

void TurnServerAllocation::Channel::OnMessage(rtc::Message* msg) {
  RTC_DCHECK(msg->message_id == MSG_ALLOCATION_TIMEOUT);
  SignalDestroyed(this);
  delete this;
}

}  // namespace cricket
