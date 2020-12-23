/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// P2PTransportChannel wraps up the state management of the connection between
// two P2P clients.  Clients have candidate ports for connecting, and
// connections which are combinations of candidates from each end (Alice and
// Bob each have candidates, one candidate from Alice and one candidate from
// Bob are used to make a connection, repeat to make many connections).
//
// When all of the available connections become invalid (non-writable), we
// kick off a process of determining more candidates and more connections.
//
#ifndef P2P_BASE_P2P_TRANSPORT_CHANNEL_H_
#define P2P_BASE_P2P_TRANSPORT_CHANNEL_H_

#include <algorithm>
#include <map>
#include <memory>
#include <set>
#include <string>
#include <vector>

#include "api/async_resolver_factory.h"
#include "api/candidate.h"
#include "api/rtc_error.h"
#include "logging/rtc_event_log/events/rtc_event_ice_candidate_pair_config.h"
#include "logging/rtc_event_log/ice_logger.h"
#include "p2p/base/candidate_pair_interface.h"
#include "p2p/base/ice_controller_factory_interface.h"
#include "p2p/base/ice_controller_interface.h"
#include "p2p/base/ice_transport_internal.h"
#include "p2p/base/p2p_constants.h"
#include "p2p/base/p2p_transport_channel_ice_field_trials.h"
#include "p2p/base/port_allocator.h"
#include "p2p/base/port_interface.h"
#include "p2p/base/regathering_controller.h"
#include "rtc_base/async_invoker.h"
#include "rtc_base/async_packet_socket.h"
#include "rtc_base/constructor_magic.h"
#include "rtc_base/strings/string_builder.h"
#include "rtc_base/system/rtc_export.h"
#include "rtc_base/third_party/sigslot/sigslot.h"
#include "rtc_base/thread_annotations.h"

namespace webrtc {
class RtcEventLog;
}  // namespace webrtc

namespace cricket {

// Enum for UMA metrics, used to record whether the channel is
// connected/connecting/disconnected when ICE restart happens.
enum class IceRestartState { CONNECTING, CONNECTED, DISCONNECTED, MAX_VALUE };

static const int MIN_PINGS_AT_WEAK_PING_INTERVAL = 3;

bool IceCredentialsChanged(const std::string& old_ufrag,
                           const std::string& old_pwd,
                           const std::string& new_ufrag,
                           const std::string& new_pwd);

// Adds the port on which the candidate originated.
class RemoteCandidate : public Candidate {
 public:
  RemoteCandidate(const Candidate& c, PortInterface* origin_port)
      : Candidate(c), origin_port_(origin_port) {}

  PortInterface* origin_port() { return origin_port_; }

 private:
  PortInterface* origin_port_;
};

// P2PTransportChannel manages the candidates and connection process to keep
// two P2P clients connected to each other.
class RTC_EXPORT P2PTransportChannel : public IceTransportInternal {
 public:
  // For testing only.
  // TODO(zstein): Remove once AsyncResolverFactory is required.
  P2PTransportChannel(const std::string& transport_name,
                      int component,
                      PortAllocator* allocator);
  P2PTransportChannel(
      const std::string& transport_name,
      int component,
      PortAllocator* allocator,
      webrtc::AsyncResolverFactory* async_resolver_factory,
      webrtc::RtcEventLog* event_log = nullptr,
      IceControllerFactoryInterface* ice_controller_factory = nullptr);
  ~P2PTransportChannel() override;

  // From TransportChannelImpl:
  IceTransportState GetState() const override;
  webrtc::IceTransportState GetIceTransportState() const override;

  const std::string& transport_name() const override;
  int component() const override;
  bool writable() const override;
  bool receiving() const override;
  void SetIceRole(IceRole role) override;
  IceRole GetIceRole() const override;
  void SetIceTiebreaker(uint64_t tiebreaker) override;
  void SetIceParameters(const IceParameters& ice_params) override;
  void SetRemoteIceParameters(const IceParameters& ice_params) override;
  void SetRemoteIceMode(IceMode mode) override;
  // TODO(deadbeef): Deprecated. Remove when Chromium's
  // IceTransportChannel does not depend on this.
  void Connect() {}
  void MaybeStartGathering() override;
  IceGatheringState gathering_state() const override;
  void ResolveHostnameCandidate(const Candidate& candidate);
  void AddRemoteCandidate(const Candidate& candidate) override;
  void RemoveRemoteCandidate(const Candidate& candidate) override;
  void RemoveAllRemoteCandidates() override;
  // Sets the parameters in IceConfig. We do not set them blindly. Instead, we
  // only update the parameter if it is considered set in |config|. For example,
  // a negative value of receiving_timeout will be considered "not set" and we
  // will not use it to update the respective parameter in |config_|.
  // TODO(deadbeef): Use absl::optional instead of negative values.
  void SetIceConfig(const IceConfig& config) override;
  const IceConfig& config() const;
  static webrtc::RTCError ValidateIceConfig(const IceConfig& config);

  // From TransportChannel:
  int SendPacket(const char* data,
                 size_t len,
                 const rtc::PacketOptions& options,
                 int flags) override;
  int SetOption(rtc::Socket::Option opt, int value) override;
  bool GetOption(rtc::Socket::Option opt, int* value) override;
  int GetError() override;
  bool GetStats(IceTransportStats* ice_transport_stats) override;
  absl::optional<int> GetRttEstimate() override;
  const Connection* selected_connection() const override;
  absl::optional<const CandidatePair> GetSelectedCandidatePair() const override;

  // TODO(honghaiz): Remove this method once the reference of it in
  // Chromoting is removed.
  const Connection* best_connection() const {
    RTC_DCHECK_RUN_ON(network_thread_);
    return selected_connection_;
  }

  void set_incoming_only(bool value) {
    RTC_DCHECK_RUN_ON(network_thread_);
    incoming_only_ = value;
  }

  // Note: These are only for testing purpose.
  // |ports_| and |pruned_ports| should not be changed from outside.
  const std::vector<PortInterface*>& ports() {
    RTC_DCHECK_RUN_ON(network_thread_);
    return ports_;
  }
  const std::vector<PortInterface*>& pruned_ports() {
    RTC_DCHECK_RUN_ON(network_thread_);
    return pruned_ports_;
  }

  IceMode remote_ice_mode() const {
    RTC_DCHECK_RUN_ON(network_thread_);
    return remote_ice_mode_;
  }

  void PruneAllPorts();
  int check_receiving_interval() const;
  absl::optional<rtc::NetworkRoute> network_route() const override;

  // Helper method used only in unittest.
  rtc::DiffServCodePoint DefaultDscpValue() const;

  // Public for unit tests.
  Connection* FindNextPingableConnection();
  void MarkConnectionPinged(Connection* conn);

  // Public for unit tests.
  rtc::ArrayView<Connection*> connections() const;

  // Public for unit tests.
  PortAllocatorSession* allocator_session() const {
    RTC_DCHECK_RUN_ON(network_thread_);
    if (allocator_sessions_.empty()) {
      return nullptr;
    }
    return allocator_sessions_.back().get();
  }

  // Public for unit tests.
  const std::vector<RemoteCandidate>& remote_candidates() const {
    RTC_DCHECK_RUN_ON(network_thread_);
    return remote_candidates_;
  }

  std::string ToString() const {
    RTC_DCHECK_RUN_ON(network_thread_);
    const std::string RECEIVING_ABBREV[2] = {"_", "R"};
    const std::string WRITABLE_ABBREV[2] = {"_", "W"};
    rtc::StringBuilder ss;
    ss << "Channel[" << transport_name_ << "|" << component_ << "|"
       << RECEIVING_ABBREV[receiving_] << WRITABLE_ABBREV[writable_] << "]";
    return ss.Release();
  }

 private:
  rtc::Thread* thread() const { return network_thread_; }

  bool IsGettingPorts() {
    RTC_DCHECK_RUN_ON(network_thread_);
    return allocator_session()->IsGettingPorts();
  }

  // Returns true if it's possible to send packets on |connection|.
  bool ReadyToSend(Connection* connection) const;
  bool PresumedWritable(const Connection* conn) const;
  void UpdateConnectionStates();
  void RequestSortAndStateUpdate(IceControllerEvent reason_to_sort);
  // Start pinging if we haven't already started, and we now have a connection
  // that's pingable.
  void MaybeStartPinging();

  void SortConnectionsAndUpdateState(IceControllerEvent reason_to_sort);
  void SortConnections();
  void SortConnectionsIfNeeded();
  void SwitchSelectedConnection(Connection* conn, IceControllerEvent reason);
  void UpdateState();
  void HandleAllTimedOut();
  void MaybeStopPortAllocatorSessions();

  // ComputeIceTransportState computes the RTCIceTransportState as described in
  // https://w3c.github.io/webrtc-pc/#dom-rtcicetransportstate. ComputeState
  // computes the value we currently export as RTCIceTransportState.
  // TODO(bugs.webrtc.org/9308): Remove ComputeState once it's no longer used.
  IceTransportState ComputeState() const;
  webrtc::IceTransportState ComputeIceTransportState() const;

  bool CreateConnections(const Candidate& remote_candidate,
                         PortInterface* origin_port);
  bool CreateConnection(PortInterface* port,
                        const Candidate& remote_candidate,
                        PortInterface* origin_port);
  bool FindConnection(const Connection* connection) const;

  uint32_t GetRemoteCandidateGeneration(const Candidate& candidate);
  bool IsDuplicateRemoteCandidate(const Candidate& candidate);
  void RememberRemoteCandidate(const Candidate& remote_candidate,
                               PortInterface* origin_port);
  void PingConnection(Connection* conn);
  void AddAllocatorSession(std::unique_ptr<PortAllocatorSession> session);
  void AddConnection(Connection* connection);

  void OnPortReady(PortAllocatorSession* session, PortInterface* port);
  void OnPortsPruned(PortAllocatorSession* session,
                     const std::vector<PortInterface*>& ports);
  void OnCandidatesReady(PortAllocatorSession* session,
                         const std::vector<Candidate>& candidates);
  void OnCandidateError(PortAllocatorSession* session,
                        const IceCandidateErrorEvent& event);
  void OnCandidatesRemoved(PortAllocatorSession* session,
                           const std::vector<Candidate>& candidates);
  void OnCandidatesAllocationDone(PortAllocatorSession* session);
  void OnUnknownAddress(PortInterface* port,
                        const rtc::SocketAddress& addr,
                        ProtocolType proto,
                        IceMessage* stun_msg,
                        const std::string& remote_username,
                        bool port_muxed);
  void OnCandidateFilterChanged(uint32_t prev_filter, uint32_t cur_filter);

  // When a port is destroyed, remove it from both lists |ports_|
  // and |pruned_ports_|.
  void OnPortDestroyed(PortInterface* port);
  // When pruning a port, move it from |ports_| to |pruned_ports_|.
  // Returns true if the port is found and removed from |ports_|.
  bool PrunePort(PortInterface* port);
  void OnRoleConflict(PortInterface* port);

  void OnConnectionStateChange(Connection* connection);
  void OnReadPacket(Connection* connection,
                    const char* data,
                    size_t len,
                    int64_t packet_time_us);
  void OnSentPacket(const rtc::SentPacket& sent_packet);
  void OnReadyToSend(Connection* connection);
  void OnConnectionDestroyed(Connection* connection);

  void OnNominated(Connection* conn);

  void CheckAndPing();

  void LogCandidatePairConfig(Connection* conn,
                              webrtc::IceCandidatePairConfigType type);

  uint32_t GetNominationAttr(Connection* conn) const;
  bool GetUseCandidateAttr(Connection* conn) const;

  // Returns true if the new_connection is selected for transmission.
  bool MaybeSwitchSelectedConnection(Connection* new_connection,
                                     IceControllerEvent reason);
  bool MaybeSwitchSelectedConnection(
      IceControllerEvent reason,
      IceControllerInterface::SwitchResult result);
  void PruneConnections();

  // Returns the latest remote ICE parameters or nullptr if there are no remote
  // ICE parameters yet.
  IceParameters* remote_ice() {
    RTC_DCHECK_RUN_ON(network_thread_);
    return remote_ice_parameters_.empty() ? nullptr
                                          : &remote_ice_parameters_.back();
  }
  // Returns the remote IceParameters and generation that match |ufrag|
  // if found, and returns nullptr otherwise.
  const IceParameters* FindRemoteIceFromUfrag(const std::string& ufrag,
                                              uint32_t* generation);
  // Returns the index of the latest remote ICE parameters, or 0 if no remote
  // ICE parameters have been received.
  uint32_t remote_ice_generation() {
    RTC_DCHECK_RUN_ON(network_thread_);
    return remote_ice_parameters_.empty()
               ? 0
               : static_cast<uint32_t>(remote_ice_parameters_.size() - 1);
  }

  // Indicates if the given local port has been pruned.
  bool IsPortPruned(const Port* port) const;

  // Indicates if the given remote candidate has been pruned.
  bool IsRemoteCandidatePruned(const Candidate& cand) const;

  // Sets the writable state, signaling if necessary.
  void SetWritable(bool writable);
  // Sets the receiving state, signaling if necessary.
  void SetReceiving(bool receiving);
  // Clears the address and the related address fields of a local candidate to
  // avoid IP leakage. This is applicable in several scenarios as commented in
  // |PortAllocator::SanitizeCandidate|.
  Candidate SanitizeLocalCandidate(const Candidate& c) const;
  // Clears the address field of a remote candidate to avoid IP leakage. This is
  // applicable in the following scenarios:
  // 1. mDNS candidates are received.
  // 2. Peer-reflexive remote candidates.
  Candidate SanitizeRemoteCandidate(const Candidate& c) const;

  // Cast a Connection returned from IceController and verify that it exists.
  // (P2P owns all Connections, and only gives const pointers to IceController,
  // see IceControllerInterface).
  Connection* FromIceController(const Connection* conn) {
    // Verify that IceController does not return a connection
    // that we have destroyed.
    RTC_DCHECK(FindConnection(conn));
    return const_cast<Connection*>(conn);
  }

  int64_t ComputeEstimatedDisconnectedTimeMs(int64_t now,
                                             Connection* old_connection);

  std::string transport_name_ RTC_GUARDED_BY(network_thread_);
  int component_ RTC_GUARDED_BY(network_thread_);
  PortAllocator* allocator_ RTC_GUARDED_BY(network_thread_);
  webrtc::AsyncResolverFactory* async_resolver_factory_
      RTC_GUARDED_BY(network_thread_);
  rtc::Thread* network_thread_;
  bool incoming_only_ RTC_GUARDED_BY(network_thread_);
  int error_ RTC_GUARDED_BY(network_thread_);
  std::vector<std::unique_ptr<PortAllocatorSession>> allocator_sessions_
      RTC_GUARDED_BY(network_thread_);
  // |ports_| contains ports that are used to form new connections when
  // new remote candidates are added.
  std::vector<PortInterface*> ports_ RTC_GUARDED_BY(network_thread_);
  // |pruned_ports_| contains ports that have been removed from |ports_| and
  // are not being used to form new connections, but that aren't yet destroyed.
  // They may have existing connections, and they still fire signals such as
  // SignalUnknownAddress.
  std::vector<PortInterface*> pruned_ports_ RTC_GUARDED_BY(network_thread_);

  Connection* selected_connection_ RTC_GUARDED_BY(network_thread_) = nullptr;

  std::vector<RemoteCandidate> remote_candidates_
      RTC_GUARDED_BY(network_thread_);
  bool sort_dirty_ RTC_GUARDED_BY(
      network_thread_);  // indicates whether another sort is needed right now
  bool had_connection_ RTC_GUARDED_BY(network_thread_) =
      false;  // if connections_ has ever been nonempty
  typedef std::map<rtc::Socket::Option, int> OptionMap;
  OptionMap options_ RTC_GUARDED_BY(network_thread_);
  IceParameters ice_parameters_ RTC_GUARDED_BY(network_thread_);
  std::vector<IceParameters> remote_ice_parameters_
      RTC_GUARDED_BY(network_thread_);
  IceMode remote_ice_mode_ RTC_GUARDED_BY(network_thread_);
  IceRole ice_role_ RTC_GUARDED_BY(network_thread_);
  uint64_t tiebreaker_ RTC_GUARDED_BY(network_thread_);
  IceGatheringState gathering_state_ RTC_GUARDED_BY(network_thread_);
  std::unique_ptr<webrtc::BasicRegatheringController> regathering_controller_
      RTC_GUARDED_BY(network_thread_);
  int64_t last_ping_sent_ms_ RTC_GUARDED_BY(network_thread_) = 0;
  int weak_ping_interval_ RTC_GUARDED_BY(network_thread_) = WEAK_PING_INTERVAL;
  // TODO(jonasolsson): Remove state_ and rename standardized_state_ once state_
  // is no longer used to compute the ICE connection state.
  IceTransportState state_ RTC_GUARDED_BY(network_thread_) =
      IceTransportState::STATE_INIT;
  webrtc::IceTransportState standardized_state_
      RTC_GUARDED_BY(network_thread_) = webrtc::IceTransportState::kNew;
  IceConfig config_ RTC_GUARDED_BY(network_thread_);
  int last_sent_packet_id_ RTC_GUARDED_BY(network_thread_) =
      -1;  // -1 indicates no packet was sent before.
  bool started_pinging_ RTC_GUARDED_BY(network_thread_) = false;
  // The value put in the "nomination" attribute for the next nominated
  // connection. A zero-value indicates the connection will not be nominated.
  uint32_t nomination_ RTC_GUARDED_BY(network_thread_) = 0;
  bool receiving_ RTC_GUARDED_BY(network_thread_) = false;
  bool writable_ RTC_GUARDED_BY(network_thread_) = false;
  bool has_been_writable_ RTC_GUARDED_BY(network_thread_) =
      false;  // if writable_ has ever been true

  rtc::AsyncInvoker invoker_ RTC_GUARDED_BY(network_thread_);
  absl::optional<rtc::NetworkRoute> network_route_
      RTC_GUARDED_BY(network_thread_);
  webrtc::IceEventLog ice_event_log_ RTC_GUARDED_BY(network_thread_);

  std::unique_ptr<IceControllerInterface> ice_controller_
      RTC_GUARDED_BY(network_thread_);

  struct CandidateAndResolver final {
    CandidateAndResolver(const Candidate& candidate,
                         rtc::AsyncResolverInterface* resolver);
    ~CandidateAndResolver();
    Candidate candidate_;
    rtc::AsyncResolverInterface* resolver_;
  };
  std::vector<CandidateAndResolver> resolvers_ RTC_GUARDED_BY(network_thread_);
  void FinishAddingRemoteCandidate(const Candidate& new_remote_candidate);
  void OnCandidateResolved(rtc::AsyncResolverInterface* resolver);
  void AddRemoteCandidateWithResolver(Candidate candidate,
                                      rtc::AsyncResolverInterface* resolver);

  // Number of times the selected_connection_ has been modified.
  uint32_t selected_candidate_pair_changes_ = 0;

  // When was last data received on a existing connection,
  // from connection->last_data_received() that uses rtc::TimeMillis().
  int64_t last_data_received_ms_ = 0;

  IceFieldTrials field_trials_;

  RTC_DISALLOW_COPY_AND_ASSIGN(P2PTransportChannel);
};

}  // namespace cricket

#endif  // P2P_BASE_P2P_TRANSPORT_CHANNEL_H_
