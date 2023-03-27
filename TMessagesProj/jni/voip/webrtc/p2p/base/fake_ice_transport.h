/*
 *  Copyright 2017 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef P2P_BASE_FAKE_ICE_TRANSPORT_H_
#define P2P_BASE_FAKE_ICE_TRANSPORT_H_

#include <map>
#include <memory>
#include <string>
#include <utility>

#include "absl/algorithm/container.h"
#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/ice_transport_interface.h"
#include "api/task_queue/pending_task_safety_flag.h"
#include "api/units/time_delta.h"
#include "p2p/base/ice_transport_internal.h"
#include "rtc_base/copy_on_write_buffer.h"
#include "rtc_base/task_queue_for_test.h"

namespace cricket {
using ::webrtc::SafeTask;
using ::webrtc::TimeDelta;

// All methods must be called on the network thread (which is either the thread
// calling the constructor, or the separate thread explicitly passed to the
// constructor).
class FakeIceTransport : public IceTransportInternal {
 public:
  explicit FakeIceTransport(absl::string_view name,
                            int component,
                            rtc::Thread* network_thread = nullptr)
      : name_(name),
        component_(component),
        network_thread_(network_thread ? network_thread
                                       : rtc::Thread::Current()) {
    RTC_DCHECK(network_thread_);
  }
  // Must be called either on the network thread, or after the network thread
  // has been shut down.
  ~FakeIceTransport() override {
    if (dest_ && dest_->dest_ == this) {
      dest_->dest_ = nullptr;
    }
  }

  // If async, will send packets by "Post"-ing to message queue instead of
  // synchronously "Send"-ing.
  void SetAsync(bool async) {
    RTC_DCHECK_RUN_ON(network_thread_);
    async_ = async;
  }
  void SetAsyncDelay(int delay_ms) {
    RTC_DCHECK_RUN_ON(network_thread_);
    async_delay_ms_ = delay_ms;
  }

  // SetWritable, SetReceiving and SetDestination are the main methods that can
  // be used for testing, to simulate connectivity or lack thereof.
  void SetWritable(bool writable) {
    RTC_DCHECK_RUN_ON(network_thread_);
    set_writable(writable);
  }
  void SetReceiving(bool receiving) {
    RTC_DCHECK_RUN_ON(network_thread_);
    set_receiving(receiving);
  }

  // Simulates the two transports connecting to each other.
  // If `asymmetric` is true this method only affects this FakeIceTransport.
  // If false, it affects `dest` as well.
  void SetDestination(FakeIceTransport* dest, bool asymmetric = false) {
    RTC_DCHECK_RUN_ON(network_thread_);
    if (dest == dest_) {
      return;
    }
    RTC_DCHECK(!dest || !dest_)
        << "Changing fake destination from one to another is not supported.";
    if (dest) {
      // This simulates the delivery of candidates.
      dest_ = dest;
      set_writable(true);
      if (!asymmetric) {
        dest->SetDestination(this, true);
      }
    } else {
      // Simulates loss of connectivity, by asymmetrically forgetting dest_.
      dest_ = nullptr;
      set_writable(false);
    }
  }

  void SetTransportState(webrtc::IceTransportState state,
                         IceTransportState legacy_state) {
    RTC_DCHECK_RUN_ON(network_thread_);
    transport_state_ = state;
    legacy_transport_state_ = legacy_state;
    SignalIceTransportStateChanged(this);
  }

  void SetConnectionCount(size_t connection_count) {
    RTC_DCHECK_RUN_ON(network_thread_);
    size_t old_connection_count = connection_count_;
    connection_count_ = connection_count;
    if (connection_count) {
      had_connection_ = true;
    }
    // In this fake transport channel, `connection_count_` determines the
    // transport state.
    if (connection_count_ < old_connection_count) {
      SignalStateChanged(this);
    }
  }

  void SetCandidatesGatheringComplete() {
    RTC_DCHECK_RUN_ON(network_thread_);
    if (gathering_state_ != kIceGatheringComplete) {
      gathering_state_ = kIceGatheringComplete;
      SignalGatheringState(this);
    }
  }

  // Convenience functions for accessing ICE config and other things.
  int receiving_timeout() const {
    RTC_DCHECK_RUN_ON(network_thread_);
    return ice_config_.receiving_timeout_or_default();
  }
  bool gather_continually() const {
    RTC_DCHECK_RUN_ON(network_thread_);
    return ice_config_.gather_continually();
  }
  const Candidates& remote_candidates() const {
    RTC_DCHECK_RUN_ON(network_thread_);
    return remote_candidates_;
  }

  // Fake IceTransportInternal implementation.
  const std::string& transport_name() const override { return name_; }
  int component() const override { return component_; }
  uint64_t IceTiebreaker() const {
    RTC_DCHECK_RUN_ON(network_thread_);
    return tiebreaker_;
  }
  IceMode remote_ice_mode() const {
    RTC_DCHECK_RUN_ON(network_thread_);
    return remote_ice_mode_;
  }
  const std::string& ice_ufrag() const { return ice_parameters_.ufrag; }
  const std::string& ice_pwd() const { return ice_parameters_.pwd; }
  const std::string& remote_ice_ufrag() const {
    return remote_ice_parameters_.ufrag;
  }
  const std::string& remote_ice_pwd() const {
    return remote_ice_parameters_.pwd;
  }
  const IceParameters& ice_parameters() const { return ice_parameters_; }
  const IceParameters& remote_ice_parameters() const {
    return remote_ice_parameters_;
  }

  IceTransportState GetState() const override {
    RTC_DCHECK_RUN_ON(network_thread_);
    if (legacy_transport_state_) {
      return *legacy_transport_state_;
    }

    if (connection_count_ == 0) {
      return had_connection_ ? IceTransportState::STATE_FAILED
                             : IceTransportState::STATE_INIT;
    }

    if (connection_count_ == 1) {
      return IceTransportState::STATE_COMPLETED;
    }

    return IceTransportState::STATE_CONNECTING;
  }

  webrtc::IceTransportState GetIceTransportState() const override {
    RTC_DCHECK_RUN_ON(network_thread_);
    if (transport_state_) {
      return *transport_state_;
    }

    if (connection_count_ == 0) {
      return had_connection_ ? webrtc::IceTransportState::kFailed
                             : webrtc::IceTransportState::kNew;
    }

    if (connection_count_ == 1) {
      return webrtc::IceTransportState::kCompleted;
    }

    return webrtc::IceTransportState::kConnected;
  }

  void SetIceRole(IceRole role) override {
    RTC_DCHECK_RUN_ON(network_thread_);
    role_ = role;
  }
  IceRole GetIceRole() const override {
    RTC_DCHECK_RUN_ON(network_thread_);
    return role_;
  }
  void SetIceTiebreaker(uint64_t tiebreaker) override {
    RTC_DCHECK_RUN_ON(network_thread_);
    tiebreaker_ = tiebreaker;
  }
  void SetIceParameters(const IceParameters& ice_params) override {
    RTC_DCHECK_RUN_ON(network_thread_);
    ice_parameters_ = ice_params;
  }
  void SetRemoteIceParameters(const IceParameters& params) override {
    RTC_DCHECK_RUN_ON(network_thread_);
    remote_ice_parameters_ = params;
  }

  void SetRemoteIceMode(IceMode mode) override {
    RTC_DCHECK_RUN_ON(network_thread_);
    remote_ice_mode_ = mode;
  }

  void MaybeStartGathering() override {
    RTC_DCHECK_RUN_ON(network_thread_);
    if (gathering_state_ == kIceGatheringNew) {
      gathering_state_ = kIceGatheringGathering;
      SignalGatheringState(this);
    }
  }

  IceGatheringState gathering_state() const override {
    RTC_DCHECK_RUN_ON(network_thread_);
    return gathering_state_;
  }

  void SetIceConfig(const IceConfig& config) override {
    RTC_DCHECK_RUN_ON(network_thread_);
    ice_config_ = config;
  }

  void AddRemoteCandidate(const Candidate& candidate) override {
    RTC_DCHECK_RUN_ON(network_thread_);
    remote_candidates_.push_back(candidate);
  }
  void RemoveRemoteCandidate(const Candidate& candidate) override {
    RTC_DCHECK_RUN_ON(network_thread_);
    auto it = absl::c_find(remote_candidates_, candidate);
    if (it == remote_candidates_.end()) {
      RTC_LOG(LS_INFO) << "Trying to remove a candidate which doesn't exist.";
      return;
    }

    remote_candidates_.erase(it);
  }

  void RemoveAllRemoteCandidates() override {
    RTC_DCHECK_RUN_ON(network_thread_);
    remote_candidates_.clear();
  }

  bool GetStats(IceTransportStats* ice_transport_stats) override {
    CandidateStats candidate_stats;
    ConnectionInfo candidate_pair_stats;
    ice_transport_stats->candidate_stats_list.clear();
    ice_transport_stats->candidate_stats_list.push_back(candidate_stats);
    ice_transport_stats->connection_infos.clear();
    ice_transport_stats->connection_infos.push_back(candidate_pair_stats);
    return true;
  }

  absl::optional<int> GetRttEstimate() override { return absl::nullopt; }

  const Connection* selected_connection() const override { return nullptr; }
  absl::optional<const CandidatePair> GetSelectedCandidatePair()
      const override {
    return absl::nullopt;
  }

  // Fake PacketTransportInternal implementation.
  bool writable() const override {
    RTC_DCHECK_RUN_ON(network_thread_);
    return writable_;
  }
  bool receiving() const override {
    RTC_DCHECK_RUN_ON(network_thread_);
    return receiving_;
  }
  // If combine is enabled, every two consecutive packets to be sent with
  // "SendPacket" will be combined into one outgoing packet.
  void combine_outgoing_packets(bool combine) {
    RTC_DCHECK_RUN_ON(network_thread_);
    combine_outgoing_packets_ = combine;
  }
  int SendPacket(const char* data,
                 size_t len,
                 const rtc::PacketOptions& options,
                 int flags) override {
    RTC_DCHECK_RUN_ON(network_thread_);
    if (!dest_) {
      return -1;
    }

    send_packet_.AppendData(data, len);
    if (!combine_outgoing_packets_ || send_packet_.size() > len) {
      rtc::CopyOnWriteBuffer packet(std::move(send_packet_));
      if (async_) {
        network_thread_->PostDelayedTask(
            SafeTask(task_safety_.flag(),
                     [this, packet] {
                       RTC_DCHECK_RUN_ON(network_thread_);
                       FakeIceTransport::SendPacketInternal(packet);
                     }),
            TimeDelta::Millis(async_delay_ms_));
      } else {
        SendPacketInternal(packet);
      }
    }
    rtc::SentPacket sent_packet(options.packet_id, rtc::TimeMillis());
    SignalSentPacket(this, sent_packet);
    return static_cast<int>(len);
  }

  int SetOption(rtc::Socket::Option opt, int value) override {
    RTC_DCHECK_RUN_ON(network_thread_);
    socket_options_[opt] = value;
    return true;
  }
  bool GetOption(rtc::Socket::Option opt, int* value) override {
    RTC_DCHECK_RUN_ON(network_thread_);
    auto it = socket_options_.find(opt);
    if (it != socket_options_.end()) {
      *value = it->second;
      return true;
    } else {
      return false;
    }
  }

  int GetError() override { return 0; }

  rtc::CopyOnWriteBuffer last_sent_packet() {
    RTC_DCHECK_RUN_ON(network_thread_);
    return last_sent_packet_;
  }

  absl::optional<rtc::NetworkRoute> network_route() const override {
    RTC_DCHECK_RUN_ON(network_thread_);
    return network_route_;
  }
  void SetNetworkRoute(absl::optional<rtc::NetworkRoute> network_route) {
    RTC_DCHECK_RUN_ON(network_thread_);
    network_route_ = network_route;
    SendTask(network_thread_, [this] {
      RTC_DCHECK_RUN_ON(network_thread_);
      SignalNetworkRouteChanged(network_route_);
    });
  }

 private:
  void set_writable(bool writable)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(network_thread_) {
    if (writable_ == writable) {
      return;
    }
    RTC_LOG(LS_INFO) << "Change writable_ to " << writable;
    writable_ = writable;
    if (writable_) {
      SignalReadyToSend(this);
    }
    SignalWritableState(this);
  }

  void set_receiving(bool receiving)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(network_thread_) {
    if (receiving_ == receiving) {
      return;
    }
    receiving_ = receiving;
    SignalReceivingState(this);
  }

  void SendPacketInternal(const rtc::CopyOnWriteBuffer& packet)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(network_thread_) {
    if (dest_) {
      last_sent_packet_ = packet;
      dest_->SignalReadPacket(dest_, packet.data<char>(), packet.size(),
                              rtc::TimeMicros(), 0);
    }
  }

  const std::string name_;
  const int component_;
  FakeIceTransport* dest_ RTC_GUARDED_BY(network_thread_) = nullptr;
  bool async_ RTC_GUARDED_BY(network_thread_) = false;
  int async_delay_ms_ RTC_GUARDED_BY(network_thread_) = 0;
  Candidates remote_candidates_ RTC_GUARDED_BY(network_thread_);
  IceConfig ice_config_ RTC_GUARDED_BY(network_thread_);
  IceRole role_ RTC_GUARDED_BY(network_thread_) = ICEROLE_UNKNOWN;
  uint64_t tiebreaker_ RTC_GUARDED_BY(network_thread_) = 0;
  IceParameters ice_parameters_ RTC_GUARDED_BY(network_thread_);
  IceParameters remote_ice_parameters_ RTC_GUARDED_BY(network_thread_);
  IceMode remote_ice_mode_ RTC_GUARDED_BY(network_thread_) = ICEMODE_FULL;
  size_t connection_count_ RTC_GUARDED_BY(network_thread_) = 0;
  absl::optional<webrtc::IceTransportState> transport_state_
      RTC_GUARDED_BY(network_thread_);
  absl::optional<IceTransportState> legacy_transport_state_
      RTC_GUARDED_BY(network_thread_);
  IceGatheringState gathering_state_ RTC_GUARDED_BY(network_thread_) =
      kIceGatheringNew;
  bool had_connection_ RTC_GUARDED_BY(network_thread_) = false;
  bool writable_ RTC_GUARDED_BY(network_thread_) = false;
  bool receiving_ RTC_GUARDED_BY(network_thread_) = false;
  bool combine_outgoing_packets_ RTC_GUARDED_BY(network_thread_) = false;
  rtc::CopyOnWriteBuffer send_packet_ RTC_GUARDED_BY(network_thread_);
  absl::optional<rtc::NetworkRoute> network_route_
      RTC_GUARDED_BY(network_thread_);
  std::map<rtc::Socket::Option, int> socket_options_
      RTC_GUARDED_BY(network_thread_);
  rtc::CopyOnWriteBuffer last_sent_packet_ RTC_GUARDED_BY(network_thread_);
  rtc::Thread* const network_thread_;
  webrtc::ScopedTaskSafetyDetached task_safety_;
};

class FakeIceTransportWrapper : public webrtc::IceTransportInterface {
 public:
  explicit FakeIceTransportWrapper(
      std::unique_ptr<cricket::FakeIceTransport> internal)
      : internal_(std::move(internal)) {}

  cricket::IceTransportInternal* internal() override { return internal_.get(); }

 private:
  std::unique_ptr<cricket::FakeIceTransport> internal_;
};

}  // namespace cricket

#endif  // P2P_BASE_FAKE_ICE_TRANSPORT_H_
