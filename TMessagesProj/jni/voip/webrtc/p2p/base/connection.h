/*
 *  Copyright 2019 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef P2P_BASE_CONNECTION_H_
#define P2P_BASE_CONNECTION_H_

#include <memory>
#include <string>
#include <vector>

#include "absl/types/optional.h"
#include "api/candidate.h"
#include "api/transport/stun.h"
#include "logging/rtc_event_log/ice_logger.h"
#include "p2p/base/candidate_pair_interface.h"
#include "p2p/base/connection_info.h"
#include "p2p/base/p2p_transport_channel_ice_field_trials.h"
#include "p2p/base/stun_request.h"
#include "p2p/base/transport_description.h"
#include "rtc_base/async_packet_socket.h"
#include "rtc_base/message_handler.h"
#include "rtc_base/network.h"
#include "rtc_base/numerics/event_based_exponential_moving_average.h"
#include "rtc_base/rate_tracker.h"

namespace cricket {

// Version number for GOOG_PING, this is added to have the option of
// adding other flavors in the future.
constexpr int kGoogPingVersion = 1;

// Connection and Port has circular dependencies.
// So we use forward declaration rather than include.
class Port;

// Forward declaration so that a ConnectionRequest can contain a Connection.
class Connection;

struct CandidatePair final : public CandidatePairInterface {
  ~CandidatePair() override = default;

  const Candidate& local_candidate() const override { return local; }
  const Candidate& remote_candidate() const override { return remote; }

  Candidate local;
  Candidate remote;
};

// A ConnectionRequest is a simple STUN ping used to determine writability.
class ConnectionRequest : public StunRequest {
 public:
  explicit ConnectionRequest(Connection* connection);
  void Prepare(StunMessage* request) override;
  void OnResponse(StunMessage* response) override;
  void OnErrorResponse(StunMessage* response) override;
  void OnTimeout() override;
  void OnSent() override;
  int resend_delay() override;

 private:
  Connection* const connection_;
};

// Represents a communication link between a port on the local client and a
// port on the remote client.
class Connection : public CandidatePairInterface,
                   public rtc::MessageHandlerAutoCleanup,
                   public sigslot::has_slots<> {
 public:
  struct SentPing {
    SentPing(const std::string id, int64_t sent_time, uint32_t nomination)
        : id(id), sent_time(sent_time), nomination(nomination) {}

    std::string id;
    int64_t sent_time;
    uint32_t nomination;
  };

  ~Connection() override;

  // A unique ID assigned when the connection is created.
  uint32_t id() const { return id_; }

  // Implementation of virtual methods in CandidatePairInterface.
  // Returns the description of the local port
  const Candidate& local_candidate() const override;
  // Returns the description of the remote port to which we communicate.
  const Candidate& remote_candidate() const override;

  // Return local network for this connection.
  virtual const rtc::Network* network() const;
  // Return generation for this connection.
  virtual int generation() const;

  // Returns the pair priority.
  virtual uint64_t priority() const;

  enum WriteState {
    STATE_WRITABLE = 0,          // we have received ping responses recently
    STATE_WRITE_UNRELIABLE = 1,  // we have had a few ping failures
    STATE_WRITE_INIT = 2,        // we have yet to receive a ping response
    STATE_WRITE_TIMEOUT = 3,     // we have had a large number of ping failures
  };

  WriteState write_state() const { return write_state_; }
  bool writable() const { return write_state_ == STATE_WRITABLE; }
  bool receiving() const { return receiving_; }

  // Determines whether the connection has finished connecting.  This can only
  // be false for TCP connections.
  bool connected() const { return connected_; }
  bool weak() const { return !(writable() && receiving() && connected()); }
  bool active() const { return write_state_ != STATE_WRITE_TIMEOUT; }

  // A connection is dead if it can be safely deleted.
  bool dead(int64_t now) const;

  // Estimate of the round-trip time over this connection.
  int rtt() const { return rtt_; }

  int unwritable_timeout() const;
  void set_unwritable_timeout(const absl::optional<int>& value_ms) {
    unwritable_timeout_ = value_ms;
  }
  int unwritable_min_checks() const;
  void set_unwritable_min_checks(const absl::optional<int>& value) {
    unwritable_min_checks_ = value;
  }
  int inactive_timeout() const;
  void set_inactive_timeout(const absl::optional<int>& value) {
    inactive_timeout_ = value;
  }

  // Gets the `ConnectionInfo` stats, where `best_connection` has not been
  // populated (default value false).
  ConnectionInfo stats();

  sigslot::signal1<Connection*> SignalStateChange;

  // Sent when the connection has decided that it is no longer of value.  It
  // will delete itself immediately after this call.
  sigslot::signal1<Connection*> SignalDestroyed;

  // The connection can send and receive packets asynchronously.  This matches
  // the interface of AsyncPacketSocket, which may use UDP or TCP under the
  // covers.
  virtual int Send(const void* data,
                   size_t size,
                   const rtc::PacketOptions& options) = 0;

  // Error if Send() returns < 0
  virtual int GetError() = 0;

  sigslot::signal4<Connection*, const char*, size_t, int64_t> SignalReadPacket;

  sigslot::signal1<Connection*> SignalReadyToSend;

  // Called when a packet is received on this connection.
  void OnReadPacket(const char* data, size_t size, int64_t packet_time_us);

  // Called when the socket is currently able to send.
  void OnReadyToSend();

  // Called when a connection is determined to be no longer useful to us.  We
  // still keep it around in case the other side wants to use it.  But we can
  // safely stop pinging on it and we can allow it to time out if the other
  // side stops using it as well.
  bool pruned() const { return pruned_; }
  void Prune();

  bool use_candidate_attr() const { return use_candidate_attr_; }
  void set_use_candidate_attr(bool enable);

  void set_nomination(uint32_t value) { nomination_ = value; }

  uint32_t remote_nomination() const { return remote_nomination_; }
  // One or several pairs may be nominated based on if Regular or Aggressive
  // Nomination is used. https://tools.ietf.org/html/rfc5245#section-8
  // `nominated` is defined both for the controlling or controlled agent based
  // on if a nomination has been pinged or acknowledged. The controlled agent
  // gets its `remote_nomination_` set when pinged by the controlling agent with
  // a nomination value. The controlling agent gets its `acked_nomination_` set
  // when receiving a response to a nominating ping.
  bool nominated() const { return acked_nomination_ || remote_nomination_; }
  void set_remote_ice_mode(IceMode mode) { remote_ice_mode_ = mode; }

  int receiving_timeout() const;
  void set_receiving_timeout(absl::optional<int> receiving_timeout_ms) {
    receiving_timeout_ = receiving_timeout_ms;
  }

  // Makes the connection go away.
  void Destroy();

  // Makes the connection go away, in a failed state.
  void FailAndDestroy();

  // Prunes the connection and sets its state to STATE_FAILED,
  // It will not be used or send pings although it can still receive packets.
  void FailAndPrune();

  // Checks that the state of this connection is up-to-date.  The argument is
  // the current time, which is compared against various timeouts.
  void UpdateState(int64_t now);

  // Called when this connection should try checking writability again.
  int64_t last_ping_sent() const { return last_ping_sent_; }
  void Ping(int64_t now);
  void ReceivedPingResponse(
      int rtt,
      const std::string& request_id,
      const absl::optional<uint32_t>& nomination = absl::nullopt);
  int64_t last_ping_response_received() const {
    return last_ping_response_received_;
  }
  const absl::optional<std::string>& last_ping_id_received() const {
    return last_ping_id_received_;
  }
  // Used to check if any STUN ping response has been received.
  int rtt_samples() const { return rtt_samples_; }

  // Called whenever a valid ping is received on this connection.  This is
  // public because the connection intercepts the first ping for us.
  int64_t last_ping_received() const { return last_ping_received_; }
  void ReceivedPing(
      const absl::optional<std::string>& request_id = absl::nullopt);
  // Handles the binding request; sends a response if this is a valid request.
  void HandleStunBindingOrGoogPingRequest(IceMessage* msg);
  // Handles the piggyback acknowledgement of the lastest connectivity check
  // that the remote peer has received, if it is indicated in the incoming
  // connectivity check from the peer.
  void HandlePiggybackCheckAcknowledgementIfAny(StunMessage* msg);
  // Timestamp when data was last sent (or attempted to be sent).
  int64_t last_send_data() const { return last_send_data_; }
  int64_t last_data_received() const { return last_data_received_; }

  // Debugging description of this connection
  std::string ToDebugId() const;
  std::string ToString() const;
  std::string ToSensitiveString() const;
  // Structured description of this candidate pair.
  const webrtc::IceCandidatePairDescription& ToLogDescription();
  void set_ice_event_log(webrtc::IceEventLog* ice_event_log) {
    ice_event_log_ = ice_event_log;
  }
  // Prints pings_since_last_response_ into a string.
  void PrintPingsSinceLastResponse(std::string* pings, size_t max);

  bool reported() const { return reported_; }
  void set_reported(bool reported) { reported_ = reported; }
  // The following two methods are only used for logging in ToString above, and
  // this flag is set true by P2PTransportChannel for its selected candidate
  // pair.
  bool selected() const { return selected_; }
  void set_selected(bool selected) { selected_ = selected; }

  // This signal will be fired if this connection is nominated by the
  // controlling side.
  sigslot::signal1<Connection*> SignalNominated;

  // Invoked when Connection receives STUN error response with 487 code.
  void HandleRoleConflictFromPeer();

  IceCandidatePairState state() const { return state_; }

  int num_pings_sent() const { return num_pings_sent_; }

  IceMode remote_ice_mode() const { return remote_ice_mode_; }

  uint32_t ComputeNetworkCost() const;

  // Update the ICE password and/or generation of the remote candidate if the
  // ufrag in `params` matches the candidate's ufrag, and the
  // candidate's password and/or ufrag has not been set.
  void MaybeSetRemoteIceParametersAndGeneration(const IceParameters& params,
                                                int generation);

  // If `remote_candidate_` is peer reflexive and is equivalent to
  // `new_candidate` except the type, update `remote_candidate_` to
  // `new_candidate`.
  void MaybeUpdatePeerReflexiveCandidate(const Candidate& new_candidate);

  // Returns the last received time of any data, stun request, or stun
  // response in milliseconds
  int64_t last_received() const;
  // Returns the last time when the connection changed its receiving state.
  int64_t receiving_unchanged_since() const {
    return receiving_unchanged_since_;
  }

  bool stable(int64_t now) const;

  // Check if we sent `val` pings without receving a response.
  bool TooManyOutstandingPings(const absl::optional<int>& val) const;

  void SetIceFieldTrials(const IceFieldTrials* field_trials);
  const rtc::EventBasedExponentialMovingAverage& GetRttEstimate() const {
    return rtt_estimate_;
  }

  // Reset the connection to a state of a newly connected.
  // - STATE_WRITE_INIT
  // - receving = false
  // - throw away all pending request
  // - reset RttEstimate
  //
  // Keep the following unchanged:
  // - connected
  // - remote_candidate
  // - statistics
  //
  // Does not trigger SignalStateChange
  void ForgetLearnedState();

  void SendStunBindingResponse(const StunMessage* request);
  void SendGoogPingResponse(const StunMessage* request);
  void SendResponseMessage(const StunMessage& response);

  // An accessor for unit tests.
  Port* PortForTest() { return port_; }
  const Port* PortForTest() const { return port_; }

  // Public for unit tests.
  uint32_t acked_nomination() const { return acked_nomination_; }

  // Public for unit tests.
  void set_remote_nomination(uint32_t remote_nomination) {
    remote_nomination_ = remote_nomination;
  }

 protected:
  enum { MSG_DELETE = 0, MSG_FIRST_AVAILABLE };

  // Constructs a new connection to the given remote port.
  Connection(Port* port, size_t index, const Candidate& candidate);

  // Called back when StunRequestManager has a stun packet to send
  void OnSendStunPacket(const void* data, size_t size, StunRequest* req);

  // Callbacks from ConnectionRequest
  virtual void OnConnectionRequestResponse(ConnectionRequest* req,
                                           StunMessage* response);
  void OnConnectionRequestErrorResponse(ConnectionRequest* req,
                                        StunMessage* response);
  void OnConnectionRequestTimeout(ConnectionRequest* req);
  void OnConnectionRequestSent(ConnectionRequest* req);

  bool rtt_converged() const;

  // If the response is not received within 2 * RTT, the response is assumed to
  // be missing.
  bool missing_responses(int64_t now) const;

  // Changes the state and signals if necessary.
  void set_write_state(WriteState value);
  void UpdateReceiving(int64_t now);
  void set_state(IceCandidatePairState state);
  void set_connected(bool value);

  uint32_t nomination() const { return nomination_; }

  void OnMessage(rtc::Message* pmsg) override;

  // The local port where this connection sends and receives packets.
  Port* port() { return port_; }
  const Port* port() const { return port_; }

  uint32_t id_;
  Port* port_;
  size_t local_candidate_index_;
  Candidate remote_candidate_;

  ConnectionInfo stats_;
  rtc::RateTracker recv_rate_tracker_;
  rtc::RateTracker send_rate_tracker_;
  int64_t last_send_data_ = 0;

 private:
  // Update the local candidate based on the mapped address attribute.
  // If the local candidate changed, fires SignalStateChange.
  void MaybeUpdateLocalCandidate(ConnectionRequest* request,
                                 StunMessage* response);

  void LogCandidatePairConfig(webrtc::IceCandidatePairConfigType type);
  void LogCandidatePairEvent(webrtc::IceCandidatePairEventType type,
                             uint32_t transaction_id);

  // Check if this IceMessage is identical
  // to last message ack:ed STUN_BINDING_REQUEST.
  bool ShouldSendGoogPing(const StunMessage* message);

  WriteState write_state_;
  bool receiving_;
  bool connected_;
  bool pruned_;
  bool selected_ = false;
  // By default `use_candidate_attr_` flag will be true,
  // as we will be using aggressive nomination.
  // But when peer is ice-lite, this flag "must" be initialized to false and
  // turn on when connection becomes "best connection".
  bool use_candidate_attr_;
  // Used by the controlling side to indicate that this connection will be
  // selected for transmission if the peer supports ICE-renomination when this
  // value is positive. A larger-value indicates that a connection is nominated
  // later and should be selected by the controlled side with higher precedence.
  // A zero-value indicates not nominating this connection.
  uint32_t nomination_ = 0;
  // The last nomination that has been acknowledged.
  uint32_t acked_nomination_ = 0;
  // Used by the controlled side to remember the nomination value received from
  // the controlling side. When the peer does not support ICE re-nomination, its
  // value will be 1 if the connection has been nominated.
  uint32_t remote_nomination_ = 0;

  IceMode remote_ice_mode_;
  StunRequestManager requests_;
  int rtt_;
  int rtt_samples_ = 0;
  // https://w3c.github.io/webrtc-stats/#dom-rtcicecandidatepairstats-totalroundtriptime
  uint64_t total_round_trip_time_ms_ = 0;
  // https://w3c.github.io/webrtc-stats/#dom-rtcicecandidatepairstats-currentroundtriptime
  absl::optional<uint32_t> current_round_trip_time_ms_;
  int64_t last_ping_sent_;      // last time we sent a ping to the other side
  int64_t last_ping_received_;  // last time we received a ping from the other
                                // side
  int64_t last_data_received_;
  int64_t last_ping_response_received_;
  int64_t receiving_unchanged_since_ = 0;
  std::vector<SentPing> pings_since_last_response_;
  // Transaction ID of the last connectivity check received. Null if having not
  // received a ping yet.
  absl::optional<std::string> last_ping_id_received_;

  absl::optional<int> unwritable_timeout_;
  absl::optional<int> unwritable_min_checks_;
  absl::optional<int> inactive_timeout_;

  bool reported_;
  IceCandidatePairState state_;
  // Time duration to switch from receiving to not receiving.
  absl::optional<int> receiving_timeout_;
  int64_t time_created_ms_;
  int num_pings_sent_ = 0;

  absl::optional<webrtc::IceCandidatePairDescription> log_description_;
  webrtc::IceEventLog* ice_event_log_ = nullptr;

  // GOOG_PING_REQUEST is sent in place of STUN_BINDING_REQUEST
  // if configured via field trial, the remote peer supports it (signaled
  // in STUN_BINDING) and if the last STUN BINDING is identical to the one
  // that is about to be sent.
  absl::optional<bool> remote_support_goog_ping_;
  std::unique_ptr<StunMessage> cached_stun_binding_;

  const IceFieldTrials* field_trials_;
  rtc::EventBasedExponentialMovingAverage rtt_estimate_;

  friend class Port;
  friend class ConnectionRequest;
  friend class P2PTransportChannel;
};

// ProxyConnection defers all the interesting work to the port.
class ProxyConnection : public Connection {
 public:
  ProxyConnection(Port* port, size_t index, const Candidate& remote_candidate);

  int Send(const void* data,
           size_t size,
           const rtc::PacketOptions& options) override;
  int GetError() override;

 private:
  int error_ = 0;
};

}  // namespace cricket

#endif  // P2P_BASE_CONNECTION_H_
