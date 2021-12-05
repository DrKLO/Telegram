/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef P2P_BASE_ICE_TRANSPORT_INTERNAL_H_
#define P2P_BASE_ICE_TRANSPORT_INTERNAL_H_

#include <stdint.h>

#include <string>
#include <vector>

#include "absl/types/optional.h"
#include "api/candidate.h"
#include "api/rtc_error.h"
#include "api/transport/enums.h"
#include "p2p/base/connection.h"
#include "p2p/base/packet_transport_internal.h"
#include "p2p/base/port.h"
#include "p2p/base/transport_description.h"
#include "rtc_base/network_constants.h"
#include "rtc_base/system/rtc_export.h"
#include "rtc_base/third_party/sigslot/sigslot.h"
#include "rtc_base/time_utils.h"

namespace cricket {

struct IceTransportStats {
  CandidateStatsList candidate_stats_list;
  ConnectionInfos connection_infos;
  // Number of times the selected candidate pair has changed
  // Initially 0 and 1 once the first candidate pair has been selected.
  // The counter is increase also when "unselecting" a connection.
  uint32_t selected_candidate_pair_changes = 0;
};

typedef std::vector<Candidate> Candidates;

enum IceConnectionState {
  kIceConnectionConnecting = 0,
  kIceConnectionFailed,
  kIceConnectionConnected,  // Writable, but still checking one or more
                            // connections
  kIceConnectionCompleted,
};

// TODO(deadbeef): Unify with PeerConnectionInterface::IceConnectionState
// once /talk/ and /webrtc/ are combined, and also switch to ENUM_NAME naming
// style.
enum IceGatheringState {
  kIceGatheringNew = 0,
  kIceGatheringGathering,
  kIceGatheringComplete,
};

enum ContinualGatheringPolicy {
  // All port allocator sessions will stop after a writable connection is found.
  GATHER_ONCE = 0,
  // The most recent port allocator session will keep on running.
  GATHER_CONTINUALLY,
};

// ICE Nomination mode.
enum class NominationMode {
  REGULAR,         // Nominate once per ICE restart (Not implemented yet).
  AGGRESSIVE,      // Nominate every connection except that it will behave as if
                   // REGULAR when the remote is an ICE-LITE endpoint.
  SEMI_AGGRESSIVE  // Our current implementation of the nomination algorithm.
                   // The details are described in P2PTransportChannel.
};

// Utility method that checks if various required Candidate fields are filled in
// and contain valid values. If conditions are not met, an RTCError with the
// appropriated error number and description is returned. If the configuration
// is valid RTCError::OK() is returned.
webrtc::RTCError VerifyCandidate(const Candidate& cand);

// Runs through a list of cricket::Candidate instances and calls VerifyCandidate
// for each one, stopping on the first error encounted and returning that error
// value if so. On success returns RTCError::OK().
webrtc::RTCError VerifyCandidates(const Candidates& candidates);

// Information about ICE configuration.
// TODO(deadbeef): Use absl::optional to represent unset values, instead of
// -1.
struct IceConfig {
  // The ICE connection receiving timeout value in milliseconds.
  absl::optional<int> receiving_timeout;
  // Time interval in milliseconds to ping a backup connection when the ICE
  // channel is strongly connected.
  absl::optional<int> backup_connection_ping_interval;

  ContinualGatheringPolicy continual_gathering_policy = GATHER_ONCE;

  bool gather_continually() const {
    return continual_gathering_policy == GATHER_CONTINUALLY;
  }

  // Whether we should prioritize Relay/Relay candidate when nothing
  // is writable yet.
  bool prioritize_most_likely_candidate_pairs = false;

  // Writable connections are pinged at a slower rate once stablized.
  absl::optional<int> stable_writable_connection_ping_interval;

  // If set to true, this means the ICE transport should presume TURN-to-TURN
  // candidate pairs will succeed, even before a binding response is received.
  bool presume_writable_when_fully_relayed = false;

  // If true, after the ICE transport type (as the candidate filter used by the
  // port allocator) is changed such that new types of ICE candidates are
  // allowed by the new filter, e.g. from CF_RELAY to CF_ALL, candidates that
  // have been gathered by the ICE transport but filtered out and not signaled
  // to the upper layers, will be surfaced.
  bool surface_ice_candidates_on_ice_transport_type_changed = false;

  // Interval to check on all networks and to perform ICE regathering on any
  // active network having no connection on it.
  absl::optional<int> regather_on_failed_networks_interval;

  // The time period in which we will not switch the selected connection
  // when a new connection becomes receiving but the selected connection is not
  // in case that the selected connection may become receiving soon.
  absl::optional<int> receiving_switching_delay;

  // TODO(honghaiz): Change the default to regular nomination.
  // Default nomination mode if the remote does not support renomination.
  NominationMode default_nomination_mode = NominationMode::SEMI_AGGRESSIVE;

  // The interval in milliseconds at which ICE checks (STUN pings) will be sent
  // for a candidate pair when it is both writable and receiving (strong
  // connectivity). This parameter overrides the default value given by
  // |STRONG_PING_INTERVAL| in p2ptransport.h if set.
  absl::optional<int> ice_check_interval_strong_connectivity;
  // The interval in milliseconds at which ICE checks (STUN pings) will be sent
  // for a candidate pair when it is either not writable or not receiving (weak
  // connectivity). This parameter overrides the default value given by
  // |WEAK_PING_INTERVAL| in p2ptransport.h if set.
  absl::optional<int> ice_check_interval_weak_connectivity;
  // ICE checks (STUN pings) will not be sent at higher rate (lower interval)
  // than this, no matter what other settings there are.
  // Measure in milliseconds.
  //
  // Note that this parameter overrides both the above check intervals for
  // candidate pairs with strong or weak connectivity, if either of the above
  // interval is shorter than the min interval.
  absl::optional<int> ice_check_min_interval;
  // The min time period for which a candidate pair must wait for response to
  // connectivity checks before it becomes unwritable. This parameter
  // overrides the default value given by |CONNECTION_WRITE_CONNECT_TIMEOUT|
  // in port.h if set, when determining the writability of a candidate pair.
  absl::optional<int> ice_unwritable_timeout;

  // The min number of connectivity checks that a candidate pair must sent
  // without receiving response before it becomes unwritable. This parameter
  // overrides the default value given by |CONNECTION_WRITE_CONNECT_FAILURES| in
  // port.h if set, when determining the writability of a candidate pair.
  absl::optional<int> ice_unwritable_min_checks;

  // The min time period for which a candidate pair must wait for response to
  // connectivity checks it becomes inactive. This parameter overrides the
  // default value given by |CONNECTION_WRITE_TIMEOUT| in port.h if set, when
  // determining the writability of a candidate pair.
  absl::optional<int> ice_inactive_timeout;

  // The interval in milliseconds at which STUN candidates will resend STUN
  // binding requests to keep NAT bindings open.
  absl::optional<int> stun_keepalive_interval;

  absl::optional<rtc::AdapterType> network_preference;

  IceConfig();
  IceConfig(int receiving_timeout_ms,
            int backup_connection_ping_interval,
            ContinualGatheringPolicy gathering_policy,
            bool prioritize_most_likely_candidate_pairs,
            int stable_writable_connection_ping_interval_ms,
            bool presume_writable_when_fully_relayed,
            int regather_on_failed_networks_interval_ms,
            int receiving_switching_delay_ms);
  ~IceConfig();

  // Helper getters for parameters with implementation-specific default value.
  // By convention, parameters with default value are represented by
  // absl::optional and setting a parameter to null restores its default value.
  int receiving_timeout_or_default() const;
  int backup_connection_ping_interval_or_default() const;
  int stable_writable_connection_ping_interval_or_default() const;
  int regather_on_failed_networks_interval_or_default() const;
  int receiving_switching_delay_or_default() const;
  int ice_check_interval_strong_connectivity_or_default() const;
  int ice_check_interval_weak_connectivity_or_default() const;
  int ice_check_min_interval_or_default() const;
  int ice_unwritable_timeout_or_default() const;
  int ice_unwritable_min_checks_or_default() const;
  int ice_inactive_timeout_or_default() const;
  int stun_keepalive_interval_or_default() const;
};

// TODO(zhihuang): Replace this with
// PeerConnectionInterface::IceConnectionState.
enum class IceTransportState {
  STATE_INIT,
  STATE_CONNECTING,  // Will enter this state once a connection is created
  STATE_COMPLETED,
  STATE_FAILED
};

// TODO(zhihuang): Remove this once it's no longer used in
// remoting/protocol/libjingle_transport_factory.cc
enum IceProtocolType {
  ICEPROTO_RFC5245  // Standard RFC 5245 version of ICE.
};

// IceTransportInternal is an internal abstract class that does ICE.
// Once the public interface is supported,
// (https://www.w3.org/TR/webrtc/#rtcicetransport)
// the IceTransportInterface will be split from this class.
class RTC_EXPORT IceTransportInternal : public rtc::PacketTransportInternal {
 public:
  IceTransportInternal();
  ~IceTransportInternal() override;

  // TODO(bugs.webrtc.org/9308): Remove GetState once all uses have been
  // migrated to GetIceTransportState.
  virtual IceTransportState GetState() const = 0;
  virtual webrtc::IceTransportState GetIceTransportState() const = 0;

  virtual int component() const = 0;

  virtual IceRole GetIceRole() const = 0;

  virtual void SetIceRole(IceRole role) = 0;

  virtual void SetIceTiebreaker(uint64_t tiebreaker) = 0;

  // TODO(zhihuang): Remove this once it's no longer called in
  // remoting/protocol/libjingle_transport_factory.cc
  virtual void SetIceProtocolType(IceProtocolType type) {}

  virtual void SetIceCredentials(const std::string& ice_ufrag,
                                 const std::string& ice_pwd);

  virtual void SetRemoteIceCredentials(const std::string& ice_ufrag,
                                       const std::string& ice_pwd);

  // The ufrag and pwd in |ice_params| must be set
  // before candidate gathering can start.
  virtual void SetIceParameters(const IceParameters& ice_params) = 0;

  virtual void SetRemoteIceParameters(const IceParameters& ice_params) = 0;

  virtual void SetRemoteIceMode(IceMode mode) = 0;

  virtual void SetIceConfig(const IceConfig& config) = 0;

  // Start gathering candidates if not already started, or if an ICE restart
  // occurred.
  virtual void MaybeStartGathering() = 0;

  virtual void AddRemoteCandidate(const Candidate& candidate) = 0;

  virtual void RemoveRemoteCandidate(const Candidate& candidate) = 0;

  virtual void RemoveAllRemoteCandidates() = 0;

  virtual IceGatheringState gathering_state() const = 0;

  // Returns the current stats for this connection.
  virtual bool GetStats(IceTransportStats* ice_transport_stats) = 0;

  // Returns RTT estimate over the currently active connection, or an empty
  // absl::optional if there is none.
  virtual absl::optional<int> GetRttEstimate() = 0;

  // TODO(qingsi): Remove this method once Chrome does not depend on it anymore.
  virtual const Connection* selected_connection() const = 0;

  // Returns the selected candidate pair, or an empty absl::optional if there is
  // none.
  virtual absl::optional<const CandidatePair> GetSelectedCandidatePair()
      const = 0;

  sigslot::signal1<IceTransportInternal*> SignalGatheringState;

  // Handles sending and receiving of candidates.
  sigslot::signal2<IceTransportInternal*, const Candidate&>
      SignalCandidateGathered;

  sigslot::signal2<IceTransportInternal*, const IceCandidateErrorEvent&>
      SignalCandidateError;

  sigslot::signal2<IceTransportInternal*, const Candidates&>
      SignalCandidatesRemoved;

  // Deprecated by PacketTransportInternal::SignalNetworkRouteChanged.
  // This signal occurs when there is a change in the way that packets are
  // being routed, i.e. to a different remote location. The candidate
  // indicates where and how we are currently sending media.
  // TODO(zhihuang): Update the Chrome remoting to use the new
  // SignalNetworkRouteChanged.
  sigslot::signal2<IceTransportInternal*, const Candidate&> SignalRouteChange;

  sigslot::signal1<const cricket::CandidatePairChangeEvent&>
      SignalCandidatePairChanged;

  // Invoked when there is conflict in the ICE role between local and remote
  // agents.
  sigslot::signal1<IceTransportInternal*> SignalRoleConflict;

  // Emitted whenever the transport state changed.
  // TODO(bugs.webrtc.org/9308): Remove once all uses have migrated to the new
  // IceTransportState.
  sigslot::signal1<IceTransportInternal*> SignalStateChanged;

  // Emitted whenever the new standards-compliant transport state changed.
  sigslot::signal1<IceTransportInternal*> SignalIceTransportStateChanged;

  // Invoked when the transport is being destroyed.
  sigslot::signal1<IceTransportInternal*> SignalDestroyed;
};

}  // namespace cricket

#endif  // P2P_BASE_ICE_TRANSPORT_INTERNAL_H_
