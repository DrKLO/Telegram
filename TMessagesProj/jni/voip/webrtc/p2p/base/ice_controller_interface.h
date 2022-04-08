/*
 *  Copyright 2019 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef P2P_BASE_ICE_CONTROLLER_INTERFACE_H_
#define P2P_BASE_ICE_CONTROLLER_INTERFACE_H_

#include <string>
#include <utility>
#include <vector>

#include "p2p/base/connection.h"
#include "p2p/base/ice_transport_internal.h"

namespace cricket {

struct IceFieldTrials;  // Forward declaration to avoid circular dependency.

struct IceControllerEvent {
  enum Type {
    REMOTE_CANDIDATE_GENERATION_CHANGE,
    NETWORK_PREFERENCE_CHANGE,
    NEW_CONNECTION_FROM_LOCAL_CANDIDATE,
    NEW_CONNECTION_FROM_REMOTE_CANDIDATE,
    NEW_CONNECTION_FROM_UNKNOWN_REMOTE_ADDRESS,
    NOMINATION_ON_CONTROLLED_SIDE,
    DATA_RECEIVED,
    CONNECT_STATE_CHANGE,
    SELECTED_CONNECTION_DESTROYED,
    // The ICE_CONTROLLER_RECHECK enum value lets an IceController request
    // P2PTransportChannel to recheck a switch periodically without an event
    // taking place.
    ICE_CONTROLLER_RECHECK,
  };

  IceControllerEvent(const Type& _type)  // NOLINT: runtime/explicit
      : type(_type) {}
  std::string ToString() const;

  Type type;
  int recheck_delay_ms = 0;
};

// Defines the interface for a module that control
// - which connection to ping
// - which connection to use
// - which connection to prune
// - which connection to forget learned state on
//
// The P2PTransportChannel owns (creates and destroys) Connections,
// but P2PTransportChannel gives const pointers to the the IceController using
// `AddConnection`, i.e the IceController should not call any non-const methods
// on a Connection but signal back in the interface if any mutable function
// shall be called.
//
// Current these are limited to:
// Connection::Ping               - returned in PingResult
// Connection::Prune              - retuned in PruneConnections
// Connection::ForgetLearnedState - return in SwitchResult
//
// The IceController shall keep track of all connections added
// (and not destroyed) and give them back using the connections()-function-
//
// When a Connection gets destroyed
// - signals on Connection::SignalDestroyed
// - P2PTransportChannel calls IceController::OnConnectionDestroyed
class IceControllerInterface {
 public:
  // This represents the result of a switch call.
  struct SwitchResult {
    // Connection that we should (optionally) switch to.
    absl::optional<const Connection*> connection;

    // An optional recheck event for when a Switch() should be attempted again.
    absl::optional<IceControllerEvent> recheck_event;

    // A vector with connection to run ForgetLearnedState on.
    std::vector<const Connection*> connections_to_forget_state_on;
  };

  // This represents the result of a call to SelectConnectionToPing.
  struct PingResult {
    PingResult(const Connection* conn, int _recheck_delay_ms)
        : connection(conn ? absl::optional<const Connection*>(conn)
                          : absl::nullopt),
          recheck_delay_ms(_recheck_delay_ms) {}

    // Connection that we should (optionally) ping.
    const absl::optional<const Connection*> connection;

    // The delay before P2PTransportChannel shall call SelectConnectionToPing()
    // again.
    //
    // Since the IceController determines which connection to ping and
    // only returns one connection at a time, the recheck_delay_ms does not have
    // any obvious implication on bitrate for pings. E.g the recheck_delay_ms
    // will be shorter if there are more connections available.
    const int recheck_delay_ms = 0;
  };

  virtual ~IceControllerInterface() = default;

  // These setters are called when the state of P2PTransportChannel is mutated.
  virtual void SetIceConfig(const IceConfig& config) = 0;
  virtual void SetSelectedConnection(const Connection* selected_connection) = 0;
  virtual void AddConnection(const Connection* connection) = 0;
  virtual void OnConnectionDestroyed(const Connection* connection) = 0;

  // These are all connections that has been added and not destroyed.
  virtual rtc::ArrayView<const Connection*> connections() const = 0;

  // Is there a pingable connection ?
  // This function is used to boot-strap pinging, after this returns true
  // SelectConnectionToPing() will be called periodically.
  virtual bool HasPingableConnection() const = 0;

  // Select a connection to Ping, or nullptr if none.
  virtual PingResult SelectConnectionToPing(int64_t last_ping_sent_ms) = 0;

  // Compute the "STUN_ATTR_USE_CANDIDATE" for `conn`.
  virtual bool GetUseCandidateAttr(const Connection* conn,
                                   NominationMode mode,
                                   IceMode remote_ice_mode) const = 0;

  // These methods is only added to not have to change all unit tests
  // that simulate pinging by marking a connection pinged.
  virtual const Connection* FindNextPingableConnection() = 0;
  virtual void MarkConnectionPinged(const Connection* con) = 0;

  // Check if we should switch to `connection`.
  // This method is called for IceControllerEvent's that can switch directly
  // i.e without resorting.
  virtual SwitchResult ShouldSwitchConnection(IceControllerEvent reason,
                                              const Connection* connection) = 0;

  // Sort connections and check if we should switch.
  virtual SwitchResult SortAndSwitchConnection(IceControllerEvent reason) = 0;

  // Prune connections.
  virtual std::vector<const Connection*> PruneConnections() = 0;
};

}  // namespace cricket

#endif  // P2P_BASE_ICE_CONTROLLER_INTERFACE_H_
