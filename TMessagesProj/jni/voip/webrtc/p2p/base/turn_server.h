/*
 *  Copyright 2012 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef P2P_BASE_TURN_SERVER_H_
#define P2P_BASE_TURN_SERVER_H_

#include <list>
#include <map>
#include <memory>
#include <set>
#include <string>
#include <utility>
#include <vector>

#include "api/sequence_checker.h"
#include "p2p/base/port_interface.h"
#include "rtc_base/async_packet_socket.h"
#include "rtc_base/socket_address.h"
#include "rtc_base/third_party/sigslot/sigslot.h"
#include "rtc_base/thread.h"

namespace rtc {
class ByteBufferWriter;
class PacketSocketFactory;
}  // namespace rtc

namespace cricket {

class StunMessage;
class TurnMessage;
class TurnServer;

// The default server port for TURN, as specified in RFC5766.
const int TURN_SERVER_PORT = 3478;

// Encapsulates the client's connection to the server.
class TurnServerConnection {
 public:
  TurnServerConnection() : proto_(PROTO_UDP), socket_(NULL) {}
  TurnServerConnection(const rtc::SocketAddress& src,
                       ProtocolType proto,
                       rtc::AsyncPacketSocket* socket);
  const rtc::SocketAddress& src() const { return src_; }
  rtc::AsyncPacketSocket* socket() { return socket_; }
  bool operator==(const TurnServerConnection& t) const;
  bool operator<(const TurnServerConnection& t) const;
  std::string ToString() const;

 private:
  rtc::SocketAddress src_;
  rtc::SocketAddress dst_;
  cricket::ProtocolType proto_;
  rtc::AsyncPacketSocket* socket_;
};

// Encapsulates a TURN allocation.
// The object is created when an allocation request is received, and then
// handles TURN messages (via HandleTurnMessage) and channel data messages
// (via HandleChannelData) for this allocation when received by the server.
// The object self-deletes and informs the server if its lifetime timer expires.
class TurnServerAllocation : public rtc::MessageHandlerAutoCleanup,
                             public sigslot::has_slots<> {
 public:
  TurnServerAllocation(TurnServer* server_,
                       rtc::Thread* thread,
                       const TurnServerConnection& conn,
                       rtc::AsyncPacketSocket* server_socket,
                       const std::string& key);
  ~TurnServerAllocation() override;

  TurnServerConnection* conn() { return &conn_; }
  const std::string& key() const { return key_; }
  const std::string& transaction_id() const { return transaction_id_; }
  const std::string& username() const { return username_; }
  const std::string& origin() const { return origin_; }
  const std::string& last_nonce() const { return last_nonce_; }
  void set_last_nonce(const std::string& nonce) { last_nonce_ = nonce; }

  std::string ToString() const;

  void HandleTurnMessage(const TurnMessage* msg);
  void HandleChannelData(const char* data, size_t size);

  sigslot::signal1<TurnServerAllocation*> SignalDestroyed;

 private:
  class Channel;
  class Permission;
  typedef std::list<Permission*> PermissionList;
  typedef std::list<Channel*> ChannelList;

  void HandleAllocateRequest(const TurnMessage* msg);
  void HandleRefreshRequest(const TurnMessage* msg);
  void HandleSendIndication(const TurnMessage* msg);
  void HandleCreatePermissionRequest(const TurnMessage* msg);
  void HandleChannelBindRequest(const TurnMessage* msg);

  void OnExternalPacket(rtc::AsyncPacketSocket* socket,
                        const char* data,
                        size_t size,
                        const rtc::SocketAddress& addr,
                        const int64_t& packet_time_us);

  static int ComputeLifetime(const TurnMessage* msg);
  bool HasPermission(const rtc::IPAddress& addr);
  void AddPermission(const rtc::IPAddress& addr);
  Permission* FindPermission(const rtc::IPAddress& addr) const;
  Channel* FindChannel(int channel_id) const;
  Channel* FindChannel(const rtc::SocketAddress& addr) const;

  void SendResponse(TurnMessage* msg);
  void SendBadRequestResponse(const TurnMessage* req);
  void SendErrorResponse(const TurnMessage* req,
                         int code,
                         const std::string& reason);
  void SendExternal(const void* data,
                    size_t size,
                    const rtc::SocketAddress& peer);

  void OnPermissionDestroyed(Permission* perm);
  void OnChannelDestroyed(Channel* channel);
  void OnMessage(rtc::Message* msg) override;

  TurnServer* const server_;
  rtc::Thread* const thread_;
  TurnServerConnection conn_;
  std::unique_ptr<rtc::AsyncPacketSocket> external_socket_;
  std::string key_;
  std::string transaction_id_;
  std::string username_;
  std::string origin_;
  std::string last_nonce_;
  PermissionList perms_;
  ChannelList channels_;
};

// An interface through which the MD5 credential hash can be retrieved.
class TurnAuthInterface {
 public:
  // Gets HA1 for the specified user and realm.
  // HA1 = MD5(A1) = MD5(username:realm:password).
  // Return true if the given username and realm are valid, or false if not.
  virtual bool GetKey(const std::string& username,
                      const std::string& realm,
                      std::string* key) = 0;
  virtual ~TurnAuthInterface() = default;
};

// An interface enables Turn Server to control redirection behavior.
class TurnRedirectInterface {
 public:
  virtual bool ShouldRedirect(const rtc::SocketAddress& address,
                              rtc::SocketAddress* out) = 0;
  virtual ~TurnRedirectInterface() {}
};

class StunMessageObserver {
 public:
  virtual void ReceivedMessage(const TurnMessage* msg) = 0;
  virtual void ReceivedChannelData(const char* data, size_t size) = 0;
  virtual ~StunMessageObserver() {}
};

// The core TURN server class. Give it a socket to listen on via
// AddInternalServerSocket, and a factory to create external sockets via
// SetExternalSocketFactory, and it's ready to go.
// Not yet wired up: TCP support.
class TurnServer : public sigslot::has_slots<> {
 public:
  typedef std::map<TurnServerConnection, std::unique_ptr<TurnServerAllocation>>
      AllocationMap;

  explicit TurnServer(rtc::Thread* thread);
  ~TurnServer() override;

  // Gets/sets the realm value to use for the server.
  const std::string& realm() const {
    RTC_DCHECK_RUN_ON(thread_);
    return realm_;
  }
  void set_realm(const std::string& realm) {
    RTC_DCHECK_RUN_ON(thread_);
    realm_ = realm;
  }

  // Gets/sets the value for the SOFTWARE attribute for TURN messages.
  const std::string& software() const {
    RTC_DCHECK_RUN_ON(thread_);
    return software_;
  }
  void set_software(const std::string& software) {
    RTC_DCHECK_RUN_ON(thread_);
    software_ = software;
  }

  const AllocationMap& allocations() const {
    RTC_DCHECK_RUN_ON(thread_);
    return allocations_;
  }

  // Sets the authentication callback; does not take ownership.
  void set_auth_hook(TurnAuthInterface* auth_hook) {
    RTC_DCHECK_RUN_ON(thread_);
    auth_hook_ = auth_hook;
  }

  void set_redirect_hook(TurnRedirectInterface* redirect_hook) {
    RTC_DCHECK_RUN_ON(thread_);
    redirect_hook_ = redirect_hook;
  }

  void set_enable_otu_nonce(bool enable) {
    RTC_DCHECK_RUN_ON(thread_);
    enable_otu_nonce_ = enable;
  }

  // If set to true, reject CreatePermission requests to RFC1918 addresses.
  void set_reject_private_addresses(bool filter) {
    RTC_DCHECK_RUN_ON(thread_);
    reject_private_addresses_ = filter;
  }

  void set_enable_permission_checks(bool enable) {
    RTC_DCHECK_RUN_ON(thread_);
    enable_permission_checks_ = enable;
  }

  // Starts listening for packets from internal clients.
  void AddInternalSocket(rtc::AsyncPacketSocket* socket, ProtocolType proto);
  // Starts listening for the connections on this socket. When someone tries
  // to connect, the connection will be accepted and a new internal socket
  // will be added.
  void AddInternalServerSocket(rtc::AsyncSocket* socket, ProtocolType proto);
  // Specifies the factory to use for creating external sockets.
  void SetExternalSocketFactory(rtc::PacketSocketFactory* factory,
                                const rtc::SocketAddress& address);
  // For testing only.
  std::string SetTimestampForNextNonce(int64_t timestamp) {
    RTC_DCHECK_RUN_ON(thread_);
    ts_for_next_nonce_ = timestamp;
    return GenerateNonce(timestamp);
  }

  void SetStunMessageObserver(std::unique_ptr<StunMessageObserver> observer) {
    RTC_DCHECK_RUN_ON(thread_);
    stun_message_observer_ = std::move(observer);
  }

 private:
  // All private member functions and variables should have access restricted to
  // thread_. But compile-time annotations are missing for members access from
  // TurnServerAllocation (via friend declaration), and the On* methods, which
  // are called via sigslot.
  std::string GenerateNonce(int64_t now) const RTC_RUN_ON(thread_);
  void OnInternalPacket(rtc::AsyncPacketSocket* socket,
                        const char* data,
                        size_t size,
                        const rtc::SocketAddress& address,
                        const int64_t& packet_time_us);

  void OnNewInternalConnection(rtc::AsyncSocket* socket);

  // Accept connections on this server socket.
  void AcceptConnection(rtc::AsyncSocket* server_socket) RTC_RUN_ON(thread_);
  void OnInternalSocketClose(rtc::AsyncPacketSocket* socket, int err);

  void HandleStunMessage(TurnServerConnection* conn,
                         const char* data,
                         size_t size) RTC_RUN_ON(thread_);
  void HandleBindingRequest(TurnServerConnection* conn, const StunMessage* msg)
      RTC_RUN_ON(thread_);
  void HandleAllocateRequest(TurnServerConnection* conn,
                             const TurnMessage* msg,
                             const std::string& key) RTC_RUN_ON(thread_);

  bool GetKey(const StunMessage* msg, std::string* key) RTC_RUN_ON(thread_);
  bool CheckAuthorization(TurnServerConnection* conn,
                          StunMessage* msg,
                          const char* data,
                          size_t size,
                          const std::string& key) RTC_RUN_ON(thread_);
  bool ValidateNonce(const std::string& nonce) const RTC_RUN_ON(thread_);

  TurnServerAllocation* FindAllocation(TurnServerConnection* conn)
      RTC_RUN_ON(thread_);
  TurnServerAllocation* CreateAllocation(TurnServerConnection* conn,
                                         int proto,
                                         const std::string& key)
      RTC_RUN_ON(thread_);

  void SendErrorResponse(TurnServerConnection* conn,
                         const StunMessage* req,
                         int code,
                         const std::string& reason);

  void SendErrorResponseWithRealmAndNonce(TurnServerConnection* conn,
                                          const StunMessage* req,
                                          int code,
                                          const std::string& reason)
      RTC_RUN_ON(thread_);

  void SendErrorResponseWithAlternateServer(TurnServerConnection* conn,
                                            const StunMessage* req,
                                            const rtc::SocketAddress& addr)
      RTC_RUN_ON(thread_);

  void SendStun(TurnServerConnection* conn, StunMessage* msg);
  void Send(TurnServerConnection* conn, const rtc::ByteBufferWriter& buf);

  void OnAllocationDestroyed(TurnServerAllocation* allocation)
      RTC_RUN_ON(thread_);
  void DestroyInternalSocket(rtc::AsyncPacketSocket* socket)
      RTC_RUN_ON(thread_);

  typedef std::map<rtc::AsyncPacketSocket*, ProtocolType> InternalSocketMap;
  typedef std::map<rtc::AsyncSocket*, ProtocolType> ServerSocketMap;

  rtc::Thread* const thread_;
  const std::string nonce_key_;
  std::string realm_ RTC_GUARDED_BY(thread_);
  std::string software_ RTC_GUARDED_BY(thread_);
  TurnAuthInterface* auth_hook_ RTC_GUARDED_BY(thread_);
  TurnRedirectInterface* redirect_hook_ RTC_GUARDED_BY(thread_);
  // otu - one-time-use. Server will respond with 438 if it's
  // sees the same nonce in next transaction.
  bool enable_otu_nonce_ RTC_GUARDED_BY(thread_);
  bool reject_private_addresses_ = false;
  // Check for permission when receiving an external packet.
  bool enable_permission_checks_ = true;

  InternalSocketMap server_sockets_ RTC_GUARDED_BY(thread_);
  ServerSocketMap server_listen_sockets_ RTC_GUARDED_BY(thread_);
  std::unique_ptr<rtc::PacketSocketFactory> external_socket_factory_
      RTC_GUARDED_BY(thread_);
  rtc::SocketAddress external_addr_ RTC_GUARDED_BY(thread_);

  AllocationMap allocations_ RTC_GUARDED_BY(thread_);

  // For testing only. If this is non-zero, the next NONCE will be generated
  // from this value, and it will be reset to 0 after generating the NONCE.
  int64_t ts_for_next_nonce_ RTC_GUARDED_BY(thread_) = 0;

  // For testing only. Used to observe STUN messages received.
  std::unique_ptr<StunMessageObserver> stun_message_observer_
      RTC_GUARDED_BY(thread_);

  friend class TurnServerAllocation;
};

}  // namespace cricket

#endif  // P2P_BASE_TURN_SERVER_H_
