/*
 *  Copyright 2019 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "p2p/base/basic_ice_controller.h"

namespace {

// The minimum improvement in RTT that justifies a switch.
const int kMinImprovement = 10;

bool IsRelayRelay(const cricket::Connection* conn) {
  return conn->local_candidate().type() == cricket::RELAY_PORT_TYPE &&
         conn->remote_candidate().type() == cricket::RELAY_PORT_TYPE;
}

bool IsUdp(const cricket::Connection* conn) {
  return conn->local_candidate().relay_protocol() == cricket::UDP_PROTOCOL_NAME;
}

// TODO(qingsi) Use an enum to replace the following constants for all
// comparision results.
static constexpr int a_is_better = 1;
static constexpr int b_is_better = -1;
static constexpr int a_and_b_equal = 0;

bool LocalCandidateUsesPreferredNetwork(
    const cricket::Connection* conn,
    absl::optional<rtc::AdapterType> network_preference) {
  rtc::AdapterType network_type = conn->network()->type();
  return network_preference.has_value() && (network_type == network_preference);
}

int CompareCandidatePairsByNetworkPreference(
    const cricket::Connection* a,
    const cricket::Connection* b,
    absl::optional<rtc::AdapterType> network_preference) {
  bool a_uses_preferred_network =
      LocalCandidateUsesPreferredNetwork(a, network_preference);
  bool b_uses_preferred_network =
      LocalCandidateUsesPreferredNetwork(b, network_preference);
  if (a_uses_preferred_network && !b_uses_preferred_network) {
    return a_is_better;
  } else if (!a_uses_preferred_network && b_uses_preferred_network) {
    return b_is_better;
  }
  return a_and_b_equal;
}

}  // namespace

namespace cricket {

BasicIceController::BasicIceController(const IceControllerFactoryArgs& args)
    : ice_transport_state_func_(args.ice_transport_state_func),
      ice_role_func_(args.ice_role_func),
      is_connection_pruned_func_(args.is_connection_pruned_func),
      field_trials_(args.ice_field_trials) {}

BasicIceController::~BasicIceController() {}

void BasicIceController::SetIceConfig(const IceConfig& config) {
  config_ = config;
}

void BasicIceController::SetSelectedConnection(
    const Connection* selected_connection) {
  selected_connection_ = selected_connection;
}

void BasicIceController::AddConnection(const Connection* connection) {
  connections_.push_back(connection);
  unpinged_connections_.insert(connection);
}

void BasicIceController::OnConnectionDestroyed(const Connection* connection) {
  pinged_connections_.erase(connection);
  unpinged_connections_.erase(connection);
  connections_.erase(absl::c_find(connections_, connection));
}

bool BasicIceController::HasPingableConnection() const {
  int64_t now = rtc::TimeMillis();
  return absl::c_any_of(connections_, [this, now](const Connection* c) {
    return IsPingable(c, now);
  });
}

IceControllerInterface::PingResult BasicIceController::SelectConnectionToPing(
    int64_t last_ping_sent_ms) {
  // When the selected connection is not receiving or not writable, or any
  // active connection has not been pinged enough times, use the weak ping
  // interval.
  bool need_more_pings_at_weak_interval =
      absl::c_any_of(connections_, [](const Connection* conn) {
        return conn->active() &&
               conn->num_pings_sent() < MIN_PINGS_AT_WEAK_PING_INTERVAL;
      });
  int ping_interval = (weak() || need_more_pings_at_weak_interval)
                          ? weak_ping_interval()
                          : strong_ping_interval();

  const Connection* conn = nullptr;
  if (rtc::TimeMillis() >= last_ping_sent_ms + ping_interval) {
    conn = FindNextPingableConnection();
  }
  PingResult res(conn, std::min(ping_interval, check_receiving_interval()));
  return res;
}

void BasicIceController::MarkConnectionPinged(const Connection* conn) {
  if (conn && pinged_connections_.insert(conn).second) {
    unpinged_connections_.erase(conn);
  }
}

// Returns the next pingable connection to ping.
const Connection* BasicIceController::FindNextPingableConnection() {
  int64_t now = rtc::TimeMillis();

  // Rule 1: Selected connection takes priority over non-selected ones.
  if (selected_connection_ && selected_connection_->connected() &&
      selected_connection_->writable() &&
      WritableConnectionPastPingInterval(selected_connection_, now)) {
    return selected_connection_;
  }

  // Rule 2: If the channel is weak, we need to find a new writable and
  // receiving connection, probably on a different network. If there are lots of
  // connections, it may take several seconds between two pings for every
  // non-selected connection. This will cause the receiving state of those
  // connections to be false, and thus they won't be selected. This is
  // problematic for network fail-over. We want to make sure at least one
  // connection per network is pinged frequently enough in order for it to be
  // selectable. So we prioritize one connection per network.
  // Rule 2.1: Among such connections, pick the one with the earliest
  // last-ping-sent time.
  if (weak()) {
    std::vector<const Connection*> pingable_selectable_connections;
    absl::c_copy_if(GetBestWritableConnectionPerNetwork(),
                    std::back_inserter(pingable_selectable_connections),
                    [this, now](const Connection* conn) {
                      return WritableConnectionPastPingInterval(conn, now);
                    });
    auto iter = absl::c_min_element(
        pingable_selectable_connections,
        [](const Connection* conn1, const Connection* conn2) {
          return conn1->last_ping_sent() < conn2->last_ping_sent();
        });
    if (iter != pingable_selectable_connections.end()) {
      return *iter;
    }
  }

  // Rule 3: Triggered checks have priority over non-triggered connections.
  // Rule 3.1: Among triggered checks, oldest takes precedence.
  const Connection* oldest_triggered_check =
      FindOldestConnectionNeedingTriggeredCheck(now);
  if (oldest_triggered_check) {
    return oldest_triggered_check;
  }

  // Rule 4: Unpinged connections have priority over pinged ones.
  RTC_CHECK(connections_.size() ==
            pinged_connections_.size() + unpinged_connections_.size());
  // If there are unpinged and pingable connections, only ping those.
  // Otherwise, treat everything as unpinged.
  // TODO(honghaiz): Instead of adding two separate vectors, we can add a state
  // "pinged" to filter out unpinged connections.
  if (absl::c_none_of(unpinged_connections_,
                      [this, now](const Connection* conn) {
                        return this->IsPingable(conn, now);
                      })) {
    unpinged_connections_.insert(pinged_connections_.begin(),
                                 pinged_connections_.end());
    pinged_connections_.clear();
  }

  // Among un-pinged pingable connections, "more pingable" takes precedence.
  std::vector<const Connection*> pingable_connections;
  absl::c_copy_if(
      unpinged_connections_, std::back_inserter(pingable_connections),
      [this, now](const Connection* conn) { return IsPingable(conn, now); });
  auto iter = absl::c_max_element(
      pingable_connections,
      [this](const Connection* conn1, const Connection* conn2) {
        // Some implementations of max_element
        // compare an element with itself.
        if (conn1 == conn2) {
          return false;
        }
        return MorePingable(conn1, conn2) == conn2;
      });
  if (iter != pingable_connections.end()) {
    return *iter;
  }
  return nullptr;
}

// Find "triggered checks".  We ping first those connections that have
// received a ping but have not sent a ping since receiving it
// (last_ping_received > last_ping_sent).  But we shouldn't do
// triggered checks if the connection is already writable.
const Connection* BasicIceController::FindOldestConnectionNeedingTriggeredCheck(
    int64_t now) {
  const Connection* oldest_needing_triggered_check = nullptr;
  for (auto* conn : connections_) {
    if (!IsPingable(conn, now)) {
      continue;
    }
    bool needs_triggered_check =
        (!conn->writable() &&
         conn->last_ping_received() > conn->last_ping_sent());
    if (needs_triggered_check &&
        (!oldest_needing_triggered_check ||
         (conn->last_ping_received() <
          oldest_needing_triggered_check->last_ping_received()))) {
      oldest_needing_triggered_check = conn;
    }
  }

  if (oldest_needing_triggered_check) {
    RTC_LOG(LS_INFO) << "Selecting connection for triggered check: "
                     << oldest_needing_triggered_check->ToString();
  }
  return oldest_needing_triggered_check;
}

bool BasicIceController::WritableConnectionPastPingInterval(
    const Connection* conn,
    int64_t now) const {
  int interval = CalculateActiveWritablePingInterval(conn, now);
  return conn->last_ping_sent() + interval <= now;
}

int BasicIceController::CalculateActiveWritablePingInterval(
    const Connection* conn,
    int64_t now) const {
  // Ping each connection at a higher rate at least
  // MIN_PINGS_AT_WEAK_PING_INTERVAL times.
  if (conn->num_pings_sent() < MIN_PINGS_AT_WEAK_PING_INTERVAL) {
    return weak_ping_interval();
  }

  int stable_interval =
      config_.stable_writable_connection_ping_interval_or_default();
  int weak_or_stablizing_interval = std::min(
      stable_interval, WEAK_OR_STABILIZING_WRITABLE_CONNECTION_PING_INTERVAL);
  // If the channel is weak or the connection is not stable yet, use the
  // weak_or_stablizing_interval.
  return (!weak() && conn->stable(now)) ? stable_interval
                                        : weak_or_stablizing_interval;
}

// Is the connection in a state for us to even consider pinging the other side?
// We consider a connection pingable even if it's not connected because that's
// how a TCP connection is kicked into reconnecting on the active side.
bool BasicIceController::IsPingable(const Connection* conn, int64_t now) const {
  const Candidate& remote = conn->remote_candidate();
  // We should never get this far with an empty remote ufrag.
  RTC_DCHECK(!remote.username().empty());
  if (remote.username().empty() || remote.password().empty()) {
    // If we don't have an ICE ufrag and pwd, there's no way we can ping.
    return false;
  }

  // A failed connection will not be pinged.
  if (conn->state() == IceCandidatePairState::FAILED) {
    return false;
  }

  // An never connected connection cannot be written to at all, so pinging is
  // out of the question. However, if it has become WRITABLE, it is in the
  // reconnecting state so ping is needed.
  if (!conn->connected() && !conn->writable()) {
    return false;
  }

  // If we sent a number of pings wo/ reply, skip sending more
  // until we get one.
  if (conn->TooManyOutstandingPings(field_trials_->max_outstanding_pings)) {
    return false;
  }

  // If the channel is weakly connected, ping all connections.
  if (weak()) {
    return true;
  }

  // Always ping active connections regardless whether the channel is completed
  // or not, but backup connections are pinged at a slower rate.
  if (IsBackupConnection(conn)) {
    return conn->rtt_samples() == 0 ||
           (now >= conn->last_ping_response_received() +
                       config_.backup_connection_ping_interval_or_default());
  }
  // Don't ping inactive non-backup connections.
  if (!conn->active()) {
    return false;
  }

  // Do ping unwritable, active connections.
  if (!conn->writable()) {
    return true;
  }

  // Ping writable, active connections if it's been long enough since the last
  // ping.
  return WritableConnectionPastPingInterval(conn, now);
}

// A connection is considered a backup connection if the channel state
// is completed, the connection is not the selected connection and it is active.
bool BasicIceController::IsBackupConnection(const Connection* conn) const {
  return ice_transport_state_func_() == IceTransportState::STATE_COMPLETED &&
         conn != selected_connection_ && conn->active();
}

const Connection* BasicIceController::MorePingable(const Connection* conn1,
                                                   const Connection* conn2) {
  RTC_DCHECK(conn1 != conn2);
  if (config_.prioritize_most_likely_candidate_pairs) {
    const Connection* most_likely_to_work_conn = MostLikelyToWork(conn1, conn2);
    if (most_likely_to_work_conn) {
      return most_likely_to_work_conn;
    }
  }

  const Connection* least_recently_pinged_conn =
      LeastRecentlyPinged(conn1, conn2);
  if (least_recently_pinged_conn) {
    return least_recently_pinged_conn;
  }

  // During the initial state when nothing has been pinged yet, return the first
  // one in the ordered |connections_|.
  auto connections = connections_;
  return *(std::find_if(connections.begin(), connections.end(),
                        [conn1, conn2](const Connection* conn) {
                          return conn == conn1 || conn == conn2;
                        }));
}

const Connection* BasicIceController::MostLikelyToWork(
    const Connection* conn1,
    const Connection* conn2) {
  bool rr1 = IsRelayRelay(conn1);
  bool rr2 = IsRelayRelay(conn2);
  if (rr1 && !rr2) {
    return conn1;
  } else if (rr2 && !rr1) {
    return conn2;
  } else if (rr1 && rr2) {
    bool udp1 = IsUdp(conn1);
    bool udp2 = IsUdp(conn2);
    if (udp1 && !udp2) {
      return conn1;
    } else if (udp2 && udp1) {
      return conn2;
    }
  }
  return nullptr;
}

const Connection* BasicIceController::LeastRecentlyPinged(
    const Connection* conn1,
    const Connection* conn2) {
  if (conn1->last_ping_sent() < conn2->last_ping_sent()) {
    return conn1;
  }
  if (conn1->last_ping_sent() > conn2->last_ping_sent()) {
    return conn2;
  }
  return nullptr;
}

std::map<const rtc::Network*, const Connection*>
BasicIceController::GetBestConnectionByNetwork() const {
  // |connections_| has been sorted, so the first one in the list on a given
  // network is the best connection on the network, except that the selected
  // connection is always the best connection on the network.
  std::map<const rtc::Network*, const Connection*> best_connection_by_network;
  if (selected_connection_) {
    best_connection_by_network[selected_connection_->network()] =
        selected_connection_;
  }
  // TODO(honghaiz): Need to update this if |connections_| are not sorted.
  for (const Connection* conn : connections_) {
    const rtc::Network* network = conn->network();
    // This only inserts when the network does not exist in the map.
    best_connection_by_network.insert(std::make_pair(network, conn));
  }
  return best_connection_by_network;
}

std::vector<const Connection*>
BasicIceController::GetBestWritableConnectionPerNetwork() const {
  std::vector<const Connection*> connections;
  for (auto kv : GetBestConnectionByNetwork()) {
    const Connection* conn = kv.second;
    if (conn->writable() && conn->connected()) {
      connections.push_back(conn);
    }
  }
  return connections;
}

IceControllerInterface::SwitchResult
BasicIceController::HandleInitialSelectDampening(
    IceControllerEvent reason,
    const Connection* new_connection) {
  if (!field_trials_->initial_select_dampening.has_value() &&
      !field_trials_->initial_select_dampening_ping_received.has_value()) {
    // experiment not enabled => select connection.
    return {new_connection, absl::nullopt};
  }

  int64_t now = rtc::TimeMillis();
  int64_t max_delay = 0;
  if (new_connection->last_ping_received() > 0 &&
      field_trials_->initial_select_dampening_ping_received.has_value()) {
    max_delay = *field_trials_->initial_select_dampening_ping_received;
  } else if (field_trials_->initial_select_dampening.has_value()) {
    max_delay = *field_trials_->initial_select_dampening;
  }

  int64_t start_wait =
      initial_select_timestamp_ms_ == 0 ? now : initial_select_timestamp_ms_;
  int64_t max_wait_until = start_wait + max_delay;

  if (now >= max_wait_until) {
    RTC_LOG(LS_INFO) << "reset initial_select_timestamp_ = "
                     << initial_select_timestamp_ms_
                     << " selection delayed by: " << (now - start_wait) << "ms";
    initial_select_timestamp_ms_ = 0;
    return {new_connection, absl::nullopt};
  }

  // We are not yet ready to select first connection...
  if (initial_select_timestamp_ms_ == 0) {
    // Set timestamp on first time...
    // but run the delayed invokation everytime to
    // avoid possibility that we miss it.
    initial_select_timestamp_ms_ = now;
    RTC_LOG(LS_INFO) << "set initial_select_timestamp_ms_ = "
                     << initial_select_timestamp_ms_;
  }

  int min_delay = max_delay;
  if (field_trials_->initial_select_dampening.has_value()) {
    min_delay = std::min(min_delay, *field_trials_->initial_select_dampening);
  }
  if (field_trials_->initial_select_dampening_ping_received.has_value()) {
    min_delay = std::min(
        min_delay, *field_trials_->initial_select_dampening_ping_received);
  }

  RTC_LOG(LS_INFO) << "delay initial selection up to " << min_delay << "ms";
  reason.type = IceControllerEvent::ICE_CONTROLLER_RECHECK;
  reason.recheck_delay_ms = min_delay;
  return {absl::nullopt, reason};
}

IceControllerInterface::SwitchResult BasicIceController::ShouldSwitchConnection(
    IceControllerEvent reason,
    const Connection* new_connection) {
  if (!ReadyToSend(new_connection) || selected_connection_ == new_connection) {
    return {absl::nullopt, absl::nullopt};
  }

  if (selected_connection_ == nullptr) {
    return HandleInitialSelectDampening(reason, new_connection);
  }

  // Do not switch to a connection that is not receiving if it is not on a
  // preferred network or it has higher cost because it may be just spuriously
  // better.
  int compare_a_b_by_networks = CompareCandidatePairNetworks(
      new_connection, selected_connection_, config_.network_preference);
  if (compare_a_b_by_networks == b_is_better && !new_connection->receiving()) {
    return {absl::nullopt, absl::nullopt};
  }

  bool missed_receiving_unchanged_threshold = false;
  absl::optional<int64_t> receiving_unchanged_threshold(
      rtc::TimeMillis() - config_.receiving_switching_delay_or_default());
  int cmp = CompareConnections(selected_connection_, new_connection,
                               receiving_unchanged_threshold,
                               &missed_receiving_unchanged_threshold);

  absl::optional<IceControllerEvent> recheck_event;
  if (missed_receiving_unchanged_threshold &&
      config_.receiving_switching_delay_or_default()) {
    // If we do not switch to the connection because it missed the receiving
    // threshold, the new connection is in a better receiving state than the
    // currently selected connection. So we need to re-check whether it needs
    // to be switched at a later time.
    recheck_event = reason;
    recheck_event->recheck_delay_ms =
        config_.receiving_switching_delay_or_default();
  }

  if (cmp < 0) {
    return {new_connection, absl::nullopt};
  } else if (cmp > 0) {
    return {absl::nullopt, recheck_event};
  }

  // If everything else is the same, switch only if rtt has improved by
  // a margin.
  if (new_connection->rtt() <= selected_connection_->rtt() - kMinImprovement) {
    return {new_connection, absl::nullopt};
  }

  return {absl::nullopt, recheck_event};
}

IceControllerInterface::SwitchResult
BasicIceController::SortAndSwitchConnection(IceControllerEvent reason) {
  // Find the best alternative connection by sorting.  It is important to note
  // that amongst equal preference, writable connections, this will choose the
  // one whose estimated latency is lowest.  So it is the only one that we
  // need to consider switching to.
  // TODO(honghaiz): Don't sort;  Just use std::max_element in the right places.
  absl::c_stable_sort(
      connections_, [this](const Connection* a, const Connection* b) {
        int cmp = CompareConnections(a, b, absl::nullopt, nullptr);
        if (cmp != 0) {
          return cmp > 0;
        }
        // Otherwise, sort based on latency estimate.
        return a->rtt() < b->rtt();
      });

  RTC_LOG(LS_VERBOSE) << "Sorting " << connections_.size()
                      << " available connections";
  for (size_t i = 0; i < connections_.size(); ++i) {
    RTC_LOG(LS_VERBOSE) << connections_[i]->ToString();
  }

  const Connection* top_connection =
      (!connections_.empty()) ? connections_[0] : nullptr;

  return ShouldSwitchConnection(reason, top_connection);
}

bool BasicIceController::ReadyToSend(const Connection* connection) const {
  // Note that we allow sending on an unreliable connection, because it's
  // possible that it became unreliable simply due to bad chance.
  // So this shouldn't prevent attempting to send media.
  return connection != nullptr &&
         (connection->writable() ||
          connection->write_state() == Connection::STATE_WRITE_UNRELIABLE ||
          PresumedWritable(connection));
}

bool BasicIceController::PresumedWritable(const Connection* conn) const {
  return (conn->write_state() == Connection::STATE_WRITE_INIT &&
          config_.presume_writable_when_fully_relayed &&
          conn->local_candidate().type() == RELAY_PORT_TYPE &&
          (conn->remote_candidate().type() == RELAY_PORT_TYPE ||
           conn->remote_candidate().type() == PRFLX_PORT_TYPE));
}

// Compare two connections based on their writing, receiving, and connected
// states.
int BasicIceController::CompareConnectionStates(
    const Connection* a,
    const Connection* b,
    absl::optional<int64_t> receiving_unchanged_threshold,
    bool* missed_receiving_unchanged_threshold) const {
  // First, prefer a connection that's writable or presumed writable over
  // one that's not writable.
  bool a_writable = a->writable() || PresumedWritable(a);
  bool b_writable = b->writable() || PresumedWritable(b);
  if (a_writable && !b_writable) {
    return a_is_better;
  }
  if (!a_writable && b_writable) {
    return b_is_better;
  }

  // Sort based on write-state. Better states have lower values.
  if (a->write_state() < b->write_state()) {
    return a_is_better;
  }
  if (b->write_state() < a->write_state()) {
    return b_is_better;
  }

  // We prefer a receiving connection to a non-receiving, higher-priority
  // connection when sorting connections and choosing which connection to
  // switch to.
  if (a->receiving() && !b->receiving()) {
    return a_is_better;
  }
  if (!a->receiving() && b->receiving()) {
    if (!receiving_unchanged_threshold ||
        (a->receiving_unchanged_since() <= *receiving_unchanged_threshold &&
         b->receiving_unchanged_since() <= *receiving_unchanged_threshold)) {
      return b_is_better;
    }
    *missed_receiving_unchanged_threshold = true;
  }

  // WARNING: Some complexity here about TCP reconnecting.
  // When a TCP connection fails because of a TCP socket disconnecting, the
  // active side of the connection will attempt to reconnect for 5 seconds while
  // pretending to be writable (the connection is not set to the unwritable
  // state).  On the passive side, the connection also remains writable even
  // though it is disconnected, and a new connection is created when the active
  // side connects.  At that point, there are two TCP connections on the passive
  // side: 1. the old, disconnected one that is pretending to be writable, and
  // 2.  the new, connected one that is maybe not yet writable.  For purposes of
  // pruning, pinging, and selecting the selected connection, we want to treat
  // the new connection as "better" than the old one. We could add a method
  // called something like Connection::ImReallyBadEvenThoughImWritable, but that
  // is equivalent to the existing Connection::connected(), which we already
  // have. So, in code throughout this file, we'll check whether the connection
  // is connected() or not, and if it is not, treat it as "worse" than a
  // connected one, even though it's writable.  In the code below, we're doing
  // so to make sure we treat a new writable connection as better than an old
  // disconnected connection.

  // In the case where we reconnect TCP connections, the original best
  // connection is disconnected without changing to WRITE_TIMEOUT. In this case,
  // the new connection, when it becomes writable, should have higher priority.
  if (a->write_state() == Connection::STATE_WRITABLE &&
      b->write_state() == Connection::STATE_WRITABLE) {
    if (a->connected() && !b->connected()) {
      return a_is_better;
    }
    if (!a->connected() && b->connected()) {
      return b_is_better;
    }
  }

  return 0;
}

// Compares two connections based only on the candidate and network information.
// Returns positive if |a| is better than |b|.
int BasicIceController::CompareConnectionCandidates(const Connection* a,
                                                    const Connection* b) const {
  int compare_a_b_by_networks =
      CompareCandidatePairNetworks(a, b, config_.network_preference);
  if (compare_a_b_by_networks != a_and_b_equal) {
    return compare_a_b_by_networks;
  }

  // Compare connection priority. Lower values get sorted last.
  if (a->priority() > b->priority()) {
    return a_is_better;
  }
  if (a->priority() < b->priority()) {
    return b_is_better;
  }

  // If we're still tied at this point, prefer a younger generation.
  // (Younger generation means a larger generation number).
  int cmp = (a->remote_candidate().generation() + a->generation()) -
            (b->remote_candidate().generation() + b->generation());
  if (cmp != 0) {
    return cmp;
  }

  // A periodic regather (triggered by the regather_all_networks_interval_range)
  // will produce candidates that appear the same but would use a new port. We
  // want to use the new candidates and purge the old candidates as they come
  // in, so use the fact that the old ports get pruned immediately to rank the
  // candidates with an active port/remote candidate higher.
  bool a_pruned = is_connection_pruned_func_(a);
  bool b_pruned = is_connection_pruned_func_(b);
  if (!a_pruned && b_pruned) {
    return a_is_better;
  }
  if (a_pruned && !b_pruned) {
    return b_is_better;
  }

  // Otherwise, must be equal
  return 0;
}

int BasicIceController::CompareConnections(
    const Connection* a,
    const Connection* b,
    absl::optional<int64_t> receiving_unchanged_threshold,
    bool* missed_receiving_unchanged_threshold) const {
  RTC_CHECK(a != nullptr);
  RTC_CHECK(b != nullptr);

  // We prefer to switch to a writable and receiving connection over a
  // non-writable or non-receiving connection, even if the latter has
  // been nominated by the controlling side.
  int state_cmp = CompareConnectionStates(a, b, receiving_unchanged_threshold,
                                          missed_receiving_unchanged_threshold);
  if (state_cmp != 0) {
    return state_cmp;
  }

  if (ice_role_func_() == ICEROLE_CONTROLLED) {
    // Compare the connections based on the nomination states and the last data
    // received time if this is on the controlled side.
    if (a->remote_nomination() > b->remote_nomination()) {
      return a_is_better;
    }
    if (a->remote_nomination() < b->remote_nomination()) {
      return b_is_better;
    }

    if (a->last_data_received() > b->last_data_received()) {
      return a_is_better;
    }
    if (a->last_data_received() < b->last_data_received()) {
      return b_is_better;
    }
  }

  // Compare the network cost and priority.
  return CompareConnectionCandidates(a, b);
}

int BasicIceController::CompareCandidatePairNetworks(
    const Connection* a,
    const Connection* b,
    absl::optional<rtc::AdapterType> network_preference) const {
  int compare_a_b_by_network_preference =
      CompareCandidatePairsByNetworkPreference(a, b,
                                               config_.network_preference);
  // The network preference has a higher precedence than the network cost.
  if (compare_a_b_by_network_preference != a_and_b_equal) {
    return compare_a_b_by_network_preference;
  }

  uint32_t a_cost = a->ComputeNetworkCost();
  uint32_t b_cost = b->ComputeNetworkCost();
  // Prefer lower network cost.
  if (a_cost < b_cost) {
    return a_is_better;
  }
  if (a_cost > b_cost) {
    return b_is_better;
  }
  return a_and_b_equal;
}

std::vector<const Connection*> BasicIceController::PruneConnections() {
  // We can prune any connection for which there is a connected, writable
  // connection on the same network with better or equal priority.  We leave
  // those with better priority just in case they become writable later (at
  // which point, we would prune out the current selected connection).  We leave
  // connections on other networks because they may not be using the same
  // resources and they may represent very distinct paths over which we can
  // switch. If |best_conn_on_network| is not connected, we may be reconnecting
  // a TCP connection and should not prune connections in this network.
  // See the big comment in CompareConnectionStates.
  //
  // An exception is made for connections on an "any address" network, meaning
  // not bound to any specific network interface. We don't want to keep one of
  // these alive as a backup, since it could be using the same network
  // interface as the higher-priority, selected candidate pair.
  std::vector<const Connection*> connections_to_prune;
  auto best_connection_by_network = GetBestConnectionByNetwork();
  for (const Connection* conn : connections_) {
    const Connection* best_conn = selected_connection_;
    if (!rtc::IPIsAny(conn->network()->ip())) {
      // If the connection is bound to a specific network interface (not an
      // "any address" network), compare it against the best connection for
      // that network interface rather than the best connection overall. This
      // ensures that at least one connection per network will be left
      // unpruned.
      best_conn = best_connection_by_network[conn->network()];
    }
    // Do not prune connections if the connection being compared against is
    // weak. Otherwise, it may delete connections prematurely.
    if (best_conn && conn != best_conn && !best_conn->weak() &&
        CompareConnectionCandidates(best_conn, conn) >= 0) {
      connections_to_prune.push_back(conn);
    }
  }
  return connections_to_prune;
}

bool BasicIceController::GetUseCandidateAttr(const Connection* conn,
                                             NominationMode mode,
                                             IceMode remote_ice_mode) const {
  switch (mode) {
    case NominationMode::REGULAR:
      // TODO(honghaiz): Implement regular nomination.
      return false;
    case NominationMode::AGGRESSIVE:
      if (remote_ice_mode == ICEMODE_LITE) {
        return GetUseCandidateAttr(conn, NominationMode::REGULAR,
                                   remote_ice_mode);
      }
      return true;
    case NominationMode::SEMI_AGGRESSIVE: {
      // Nominate if
      // a) Remote is in FULL ICE AND
      //    a.1) |conn| is the selected connection OR
      //    a.2) there is no selected connection OR
      //    a.3) the selected connection is unwritable OR
      //    a.4) |conn| has higher priority than selected_connection.
      // b) Remote is in LITE ICE AND
      //    b.1) |conn| is the selected_connection AND
      //    b.2) |conn| is writable.
      bool selected = conn == selected_connection_;
      if (remote_ice_mode == ICEMODE_LITE) {
        return selected && conn->writable();
      }
      bool better_than_selected =
          !selected_connection_ || !selected_connection_->writable() ||
          CompareConnectionCandidates(selected_connection_, conn) < 0;
      return selected || better_than_selected;
    }
    default:
      RTC_NOTREACHED();
      return false;
  }
}

}  // namespace cricket
