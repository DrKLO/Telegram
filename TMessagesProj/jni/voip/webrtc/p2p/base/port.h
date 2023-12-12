/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef P2P_BASE_PORT_H_
#define P2P_BASE_PORT_H_

#include <map>
#include <memory>
#include <set>
#include <string>
#include <utility>
#include <vector>

#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/candidate.h"
#include "api/field_trials_view.h"
#include "api/packet_socket_factory.h"
#include "api/rtc_error.h"
#include "api/task_queue/task_queue_base.h"
#include "api/transport/field_trial_based_config.h"
#include "api/transport/stun.h"
#include "logging/rtc_event_log/events/rtc_event_ice_candidate_pair.h"
#include "logging/rtc_event_log/events/rtc_event_ice_candidate_pair_config.h"
#include "logging/rtc_event_log/ice_logger.h"
#include "p2p/base/candidate_pair_interface.h"
#include "p2p/base/connection.h"
#include "p2p/base/connection_info.h"
#include "p2p/base/p2p_constants.h"
#include "p2p/base/port_interface.h"
#include "p2p/base/stun_request.h"
#include "rtc_base/async_packet_socket.h"
#include "rtc_base/callback_list.h"
#include "rtc_base/checks.h"
#include "rtc_base/memory/always_valid_pointer.h"
#include "rtc_base/net_helper.h"
#include "rtc_base/network.h"
#include "rtc_base/proxy_info.h"
#include "rtc_base/rate_tracker.h"
#include "rtc_base/socket_address.h"
#include "rtc_base/system/rtc_export.h"
#include "rtc_base/third_party/sigslot/sigslot.h"
#include "rtc_base/weak_ptr.h"

namespace cricket {

RTC_EXPORT extern const char LOCAL_PORT_TYPE[];
RTC_EXPORT extern const char STUN_PORT_TYPE[];
RTC_EXPORT extern const char PRFLX_PORT_TYPE[];
RTC_EXPORT extern const char RELAY_PORT_TYPE[];

// RFC 6544, TCP candidate encoding rules.
extern const int DISCARD_PORT;
extern const char TCPTYPE_ACTIVE_STR[];
extern const char TCPTYPE_PASSIVE_STR[];
extern const char TCPTYPE_SIMOPEN_STR[];

// The type preference MUST be an integer from 0 to 126 inclusive.
// https://datatracker.ietf.org/doc/html/rfc5245#section-4.1.2.1
enum IcePriorityValue : uint8_t {
  ICE_TYPE_PREFERENCE_RELAY_TLS = 0,
  ICE_TYPE_PREFERENCE_RELAY_TCP = 1,
  ICE_TYPE_PREFERENCE_RELAY_UDP = 2,
  ICE_TYPE_PREFERENCE_PRFLX_TCP = 80,
  ICE_TYPE_PREFERENCE_HOST_TCP = 90,
  ICE_TYPE_PREFERENCE_SRFLX = 100,
  ICE_TYPE_PREFERENCE_PRFLX = 110,
  ICE_TYPE_PREFERENCE_HOST = 126
};

enum class MdnsNameRegistrationStatus {
  // IP concealment with mDNS is not enabled or the name registration process is
  // not started yet.
  kNotStarted,
  // A request to create and register an mDNS name for a local IP address of a
  // host candidate is sent to the mDNS responder.
  kInProgress,
  // The name registration is complete and the created name is returned by the
  // mDNS responder.
  kCompleted,
};

// Stats that we can return about the port of a STUN candidate.
class StunStats {
 public:
  StunStats() = default;
  StunStats(const StunStats&) = default;
  ~StunStats() = default;

  StunStats& operator=(const StunStats& other) = default;

  int stun_binding_requests_sent = 0;
  int stun_binding_responses_received = 0;
  double stun_binding_rtt_ms_total = 0;
  double stun_binding_rtt_ms_squared_total = 0;
};

// Stats that we can return about a candidate.
class CandidateStats {
 public:
  CandidateStats() = default;
  CandidateStats(const CandidateStats&) = default;
  CandidateStats(CandidateStats&&) = default;
  CandidateStats(Candidate candidate,
                 absl::optional<StunStats> stats = absl::nullopt)
      : candidate_(std::move(candidate)), stun_stats_(std::move(stats)) {}
  ~CandidateStats() = default;

  CandidateStats& operator=(const CandidateStats& other) = default;

  const Candidate& candidate() const { return candidate_; }

  const absl::optional<StunStats>& stun_stats() const { return stun_stats_; }

 private:
  Candidate candidate_;
  // STUN port stats if this candidate is a STUN candidate.
  absl::optional<StunStats> stun_stats_;
};

typedef std::vector<CandidateStats> CandidateStatsList;

const char* ProtoToString(ProtocolType proto);
absl::optional<ProtocolType> StringToProto(absl::string_view proto_name);

struct ProtocolAddress {
  rtc::SocketAddress address;
  ProtocolType proto;

  ProtocolAddress(const rtc::SocketAddress& a, ProtocolType p)
      : address(a), proto(p) {}

  bool operator==(const ProtocolAddress& o) const {
    return address == o.address && proto == o.proto;
  }
  bool operator!=(const ProtocolAddress& o) const { return !(*this == o); }
};

struct IceCandidateErrorEvent {
  IceCandidateErrorEvent() = default;
  IceCandidateErrorEvent(absl::string_view address,
                         int port,
                         absl::string_view url,
                         int error_code,
                         absl::string_view error_text)
      : address(std::move(address)),
        port(port),
        url(std::move(url)),
        error_code(error_code),
        error_text(std::move(error_text)) {}

  std::string address;
  int port = 0;
  std::string url;
  int error_code = 0;
  std::string error_text;
};

struct CandidatePairChangeEvent {
  CandidatePair selected_candidate_pair;
  int64_t last_data_received_ms;
  std::string reason;
  // How long do we estimate that we've been disconnected.
  int64_t estimated_disconnected_time_ms;
};

typedef std::set<rtc::SocketAddress> ServerAddresses;

// Represents a local communication mechanism that can be used to create
// connections to similar mechanisms of the other client.  Subclasses of this
// one add support for specific mechanisms like local UDP ports.
class Port : public PortInterface, public sigslot::has_slots<> {
 public:
  // INIT: The state when a port is just created.
  // KEEP_ALIVE_UNTIL_PRUNED: A port should not be destroyed even if no
  // connection is using it.
  // PRUNED: It will be destroyed if no connection is using it for a period of
  // 30 seconds.
  enum class State { INIT, KEEP_ALIVE_UNTIL_PRUNED, PRUNED };
  Port(webrtc::TaskQueueBase* thread,
       absl::string_view type,
       rtc::PacketSocketFactory* factory,
       const rtc::Network* network,
       absl::string_view username_fragment,
       absl::string_view password,
       const webrtc::FieldTrialsView* field_trials = nullptr);
  Port(webrtc::TaskQueueBase* thread,
       absl::string_view type,
       rtc::PacketSocketFactory* factory,
       const rtc::Network* network,
       uint16_t min_port,
       uint16_t max_port,
       absl::string_view username_fragment,
       absl::string_view password,
       const webrtc::FieldTrialsView* field_trials = nullptr);
  ~Port() override;

  // Note that the port type does NOT uniquely identify different subclasses of
  // Port. Use the 2-tuple of the port type AND the protocol (GetProtocol()) to
  // uniquely identify subclasses. Whenever a new subclass of Port introduces a
  // conflit in the value of the 2-tuple, make sure that the implementation that
  // relies on this 2-tuple for RTTI is properly changed.
  const std::string& Type() const override;
  const rtc::Network* Network() const override;

  // Methods to set/get ICE role and tiebreaker values.
  IceRole GetIceRole() const override;
  void SetIceRole(IceRole role) override;

  void SetIceTiebreaker(uint64_t tiebreaker) override;
  uint64_t IceTiebreaker() const override;

  bool SharedSocket() const override;
  void ResetSharedSocket() { shared_socket_ = false; }

  // Should not destroy the port even if no connection is using it. Called when
  // a port is ready to use.
  void KeepAliveUntilPruned();
  // Allows a port to be destroyed if no connection is using it.
  void Prune();

  // Call to stop any currently pending operations from running.
  void CancelPendingTasks();

  // The thread on which this port performs its I/O.
  webrtc::TaskQueueBase* thread() { return thread_; }

  // The factory used to create the sockets of this port.
  rtc::PacketSocketFactory* socket_factory() const { return factory_; }

  // For debugging purposes.
  const std::string& content_name() const { return content_name_; }
  void set_content_name(absl::string_view content_name) {
    content_name_ = std::string(content_name);
  }

  int component() const { return component_; }
  void set_component(int component) { component_ = component; }

  bool send_retransmit_count_attribute() const {
    return send_retransmit_count_attribute_;
  }
  void set_send_retransmit_count_attribute(bool enable) {
    send_retransmit_count_attribute_ = enable;
  }

  // Identifies the generation that this port was created in.
  uint32_t generation() const { return generation_; }
  void set_generation(uint32_t generation) { generation_ = generation; }

  const std::string username_fragment() const;
  const std::string& password() const { return password_; }

  // May be called when this port was initially created by a pooled
  // PortAllocatorSession, and is now being assigned to an ICE transport.
  // Updates the information for candidates as well.
  void SetIceParameters(int component,
                        absl::string_view username_fragment,
                        absl::string_view password);

  // Fired when candidates are discovered by the port. When all candidates
  // are discovered that belong to port SignalAddressReady is fired.
  sigslot::signal2<Port*, const Candidate&> SignalCandidateReady;
  // Provides all of the above information in one handy object.
  const std::vector<Candidate>& Candidates() const override;
  // Fired when candidate discovery failed using certain server.
  sigslot::signal2<Port*, const IceCandidateErrorEvent&> SignalCandidateError;

  // SignalPortComplete is sent when port completes the task of candidates
  // allocation.
  sigslot::signal1<Port*> SignalPortComplete;
  // This signal sent when port fails to allocate candidates and this port
  // can't be used in establishing the connections. When port is in shared mode
  // and port fails to allocate one of the candidates, port shouldn't send
  // this signal as other candidates might be usefull in establishing the
  // connection.
  sigslot::signal1<Port*> SignalPortError;

  void SubscribePortDestroyed(
      std::function<void(PortInterface*)> callback) override;
  void SendPortDestroyed(Port* port);
  // Returns a map containing all of the connections of this port, keyed by the
  // remote address.
  typedef std::map<rtc::SocketAddress, Connection*> AddressMap;
  const AddressMap& connections() { return connections_; }

  // Returns the connection to the given address or NULL if none exists.
  Connection* GetConnection(const rtc::SocketAddress& remote_addr) override;

  // Removes and deletes a connection object. `DestroyConnection` will
  // delete the connection object directly whereas `DestroyConnectionAsync`
  // defers the `delete` operation to when the call stack has been unwound.
  // Async may be needed when deleting a connection object from within a
  // callback.
  void DestroyConnection(Connection* conn) {
    DestroyConnectionInternal(conn, false);
  }

  void DestroyConnectionAsync(Connection* conn) {
    DestroyConnectionInternal(conn, true);
  }

  // In a shared socket mode each port which shares the socket will decide
  // to accept the packet based on the `remote_addr`. Currently only UDP
  // port implemented this method.
  // TODO(mallinath) - Make it pure virtual.
  virtual bool HandleIncomingPacket(rtc::AsyncPacketSocket* socket,
                                    const char* data,
                                    size_t size,
                                    const rtc::SocketAddress& remote_addr,
                                    int64_t packet_time_us);

  // Shall the port handle packet from this `remote_addr`.
  // This method is overridden by TurnPort.
  virtual bool CanHandleIncomingPacketsFrom(
      const rtc::SocketAddress& remote_addr) const;

  // Sends a response error to the given request.
  void SendBindingErrorResponse(StunMessage* message,
                                const rtc::SocketAddress& addr,
                                int error_code,
                                absl::string_view reason) override;
  void SendUnknownAttributesErrorResponse(
      StunMessage* message,
      const rtc::SocketAddress& addr,
      const std::vector<uint16_t>& unknown_types);

  void set_proxy(absl::string_view user_agent, const rtc::ProxyInfo& proxy) {
    user_agent_ = std::string(user_agent);
    proxy_ = proxy;
  }
  const std::string& user_agent() { return user_agent_; }
  const rtc::ProxyInfo& proxy() { return proxy_; }

  void EnablePortPackets() override;

  // Called if the port has no connections and is no longer useful.
  void Destroy();

  // Debugging description of this port
  std::string ToString() const override;
  uint16_t min_port() { return min_port_; }
  uint16_t max_port() { return max_port_; }

  // Timeout shortening function to speed up unit tests.
  void set_timeout_delay(int delay);

  // This method will return local and remote username fragements from the
  // stun username attribute if present.
  bool ParseStunUsername(const StunMessage* stun_msg,
                         std::string* local_username,
                         std::string* remote_username) const;
  std::string CreateStunUsername(absl::string_view remote_username) const;

  bool MaybeIceRoleConflict(const rtc::SocketAddress& addr,
                            IceMessage* stun_msg,
                            absl::string_view remote_ufrag);

  // Called when a packet has been sent to the socket.
  // This is made pure virtual to notify subclasses of Port that they MUST
  // listen to AsyncPacketSocket::SignalSentPacket and then call
  // PortInterface::OnSentPacket.
  virtual void OnSentPacket(rtc::AsyncPacketSocket* socket,
                            const rtc::SentPacket& sent_packet) = 0;

  // Called when the socket is currently able to send.
  void OnReadyToSend();

  // Called when the Connection discovers a local peer reflexive candidate.
  void AddPrflxCandidate(const Candidate& local);

  int16_t network_cost() const { return network_cost_; }

  void GetStunStats(absl::optional<StunStats>* stats) override {}

  // Foundation:  An arbitrary string that is the same for two candidates
  //   that have the same type, base IP address, protocol (UDP, TCP,
  //   etc.), and STUN or TURN server.  If any of these are different,
  //   then the foundation will be different.  Two candidate pairs with
  //   the same foundation pairs are likely to have similar network
  //   characteristics. Foundations are used in the frozen algorithm.
  std::string ComputeFoundation(absl::string_view type,
                                absl::string_view protocol,
                                absl::string_view relay_protocol,
                                const rtc::SocketAddress& base_address);

 protected:
  virtual void UpdateNetworkCost();

  void set_type(absl::string_view type) { type_ = std::string(type); }

  rtc::WeakPtr<Port> NewWeakPtr() { return weak_factory_.GetWeakPtr(); }

  void AddAddress(const rtc::SocketAddress& address,
                  const rtc::SocketAddress& base_address,
                  const rtc::SocketAddress& related_address,
                  absl::string_view protocol,
                  absl::string_view relay_protocol,
                  absl::string_view tcptype,
                  absl::string_view type,
                  uint32_t type_preference,
                  uint32_t relay_preference,
                  absl::string_view url,
                  bool is_final);

  void FinishAddingAddress(const Candidate& c, bool is_final)
      RTC_RUN_ON(thread_);

  virtual void PostAddAddress(bool is_final);

  // Adds the given connection to the map keyed by the remote candidate address.
  // If an existing connection has the same address, the existing one will be
  // replaced and destroyed.
  void AddOrReplaceConnection(Connection* conn);

  // Called when a packet is received from an unknown address that is not
  // currently a connection.  If this is an authenticated STUN binding request,
  // then we will signal the client.
  void OnReadPacket(const char* data,
                    size_t size,
                    const rtc::SocketAddress& addr,
                    ProtocolType proto);

  // If the given data comprises a complete and correct STUN message then the
  // return value is true, otherwise false. If the message username corresponds
  // with this port's username fragment, msg will contain the parsed STUN
  // message.  Otherwise, the function may send a STUN response internally.
  // remote_username contains the remote fragment of the STUN username.
  bool GetStunMessage(const char* data,
                      size_t size,
                      const rtc::SocketAddress& addr,
                      std::unique_ptr<IceMessage>* out_msg,
                      std::string* out_username);

  // Checks if the address in addr is compatible with the port's ip.
  bool IsCompatibleAddress(const rtc::SocketAddress& addr);

  // Returns DSCP value packets generated by the port itself should use.
  virtual rtc::DiffServCodePoint StunDscpValue() const;

  // Extra work to be done in subclasses when a connection is destroyed.
  virtual void HandleConnectionDestroyed(Connection* conn) {}

  void DestroyAllConnections();

  void CopyPortInformationToPacketInfo(rtc::PacketInfo* info) const;

  MdnsNameRegistrationStatus mdns_name_registration_status() const {
    return mdns_name_registration_status_;
  }
  void set_mdns_name_registration_status(MdnsNameRegistrationStatus status) {
    mdns_name_registration_status_ = status;
  }

  const webrtc::FieldTrialsView& field_trials() const { return *field_trials_; }

 private:
  void Construct();

  void PostDestroyIfDead(bool delayed);
  void DestroyIfDead();

  // Called internally when deleting a connection object.
  // Returns true if the connection object was removed from the `connections_`
  // list and the state updated accordingly. If the connection was not found
  // in the list, the return value is false. Note that this may indicate
  // incorrect behavior of external code that might be attempting to delete
  // connection objects from within a 'on destroyed' callback notification
  // for the connection object itself.
  bool OnConnectionDestroyed(Connection* conn);

  // Private implementation of DestroyConnection to keep the async usage
  // distinct.
  void DestroyConnectionInternal(Connection* conn, bool async);

  void OnNetworkTypeChanged(const rtc::Network* network);

  webrtc::TaskQueueBase* const thread_;
  rtc::PacketSocketFactory* const factory_;
  std::string type_;
  bool send_retransmit_count_attribute_;
  const rtc::Network* network_;
  uint16_t min_port_;
  uint16_t max_port_;
  std::string content_name_;
  int component_;
  uint32_t generation_;
  // In order to establish a connection to this Port (so that real data can be
  // sent through), the other side must send us a STUN binding request that is
  // authenticated with this username_fragment and password.
  // PortAllocatorSession will provide these username_fragment and password.
  //
  // Note: we should always use username_fragment() instead of using
  // `ice_username_fragment_` directly. For the details see the comment on
  // username_fragment().
  std::string ice_username_fragment_;
  std::string password_;
  std::vector<Candidate> candidates_ RTC_GUARDED_BY(thread_);
  AddressMap connections_;
  int timeout_delay_;
  bool enable_port_packets_;
  IceRole ice_role_;
  uint64_t tiebreaker_;
  bool shared_socket_;
  // Information to use when going through a proxy.
  std::string user_agent_;
  rtc::ProxyInfo proxy_;

  // A virtual cost perceived by the user, usually based on the network type
  // (WiFi. vs. Cellular). It takes precedence over the priority when
  // comparing two connections.
  int16_t network_cost_;
  State state_ = State::INIT;
  int64_t last_time_all_connections_removed_ = 0;
  MdnsNameRegistrationStatus mdns_name_registration_status_ =
      MdnsNameRegistrationStatus::kNotStarted;

  rtc::WeakPtrFactory<Port> weak_factory_;
  webrtc::AlwaysValidPointer<const webrtc::FieldTrialsView,
                             webrtc::FieldTrialBasedConfig>
      field_trials_;

  bool MaybeObfuscateAddress(Candidate* c,
                             absl::string_view type,
                             bool is_final) RTC_RUN_ON(thread_);

  friend class Connection;
  webrtc::CallbackList<PortInterface*> port_destroyed_callback_list_;
};

}  // namespace cricket

#endif  // P2P_BASE_PORT_H_
