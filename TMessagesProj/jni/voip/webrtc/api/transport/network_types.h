/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_TRANSPORT_NETWORK_TYPES_H_
#define API_TRANSPORT_NETWORK_TYPES_H_
#include <stdint.h>

#include <vector>

#include "absl/types/optional.h"
#include "api/units/data_rate.h"
#include "api/units/data_size.h"
#include "api/units/time_delta.h"
#include "api/units/timestamp.h"
#include "rtc_base/deprecation.h"

namespace webrtc {

// Configuration

// Represents constraints and rates related to the currently enabled streams.
// This is used as input to the congestion controller via the StreamsConfig
// struct.
struct BitrateAllocationLimits {
  // The total minimum send bitrate required by all sending streams.
  DataRate min_allocatable_rate = DataRate::Zero();
  // The total maximum allocatable bitrate for all currently available streams.
  DataRate max_allocatable_rate = DataRate::Zero();
  // The max bitrate to use for padding. The sum of the per-stream max padding
  // rate.
  DataRate max_padding_rate = DataRate::Zero();
};

// Use StreamsConfig for information about streams that is required for specific
// adjustments to the algorithms in network controllers. Especially useful
// for experiments.
struct StreamsConfig {
  StreamsConfig();
  StreamsConfig(const StreamsConfig&);
  ~StreamsConfig();
  Timestamp at_time = Timestamp::PlusInfinity();
  absl::optional<bool> requests_alr_probing;
  absl::optional<double> pacing_factor;

  // TODO(srte): Use BitrateAllocationLimits here.
  absl::optional<DataRate> min_total_allocated_bitrate;
  absl::optional<DataRate> max_padding_rate;
  absl::optional<DataRate> max_total_allocated_bitrate;
};

struct TargetRateConstraints {
  TargetRateConstraints();
  TargetRateConstraints(const TargetRateConstraints&);
  ~TargetRateConstraints();
  Timestamp at_time = Timestamp::PlusInfinity();
  absl::optional<DataRate> min_data_rate;
  absl::optional<DataRate> max_data_rate;
  // The initial bandwidth estimate to base target rate on. This should be used
  // as the basis for initial OnTargetTransferRate and OnPacerConfig callbacks.
  absl::optional<DataRate> starting_rate;
};

// Send side information

struct NetworkAvailability {
  Timestamp at_time = Timestamp::PlusInfinity();
  bool network_available = false;
};

struct NetworkRouteChange {
  NetworkRouteChange();
  NetworkRouteChange(const NetworkRouteChange&);
  ~NetworkRouteChange();
  Timestamp at_time = Timestamp::PlusInfinity();
  // The TargetRateConstraints are set here so they can be changed synchronously
  // when network route changes.
  TargetRateConstraints constraints;
};

struct PacedPacketInfo {
  PacedPacketInfo();
  PacedPacketInfo(int probe_cluster_id,
                  int probe_cluster_min_probes,
                  int probe_cluster_min_bytes);

  bool operator==(const PacedPacketInfo& rhs) const;

  // TODO(srte): Move probing info to a separate, optional struct.
  static constexpr int kNotAProbe = -1;
  int send_bitrate_bps = -1;
  int probe_cluster_id = kNotAProbe;
  int probe_cluster_min_probes = -1;
  int probe_cluster_min_bytes = -1;
  int probe_cluster_bytes_sent = 0;
};

struct SentPacket {
  Timestamp send_time = Timestamp::PlusInfinity();
  // Size of packet with overhead up to IP layer.
  DataSize size = DataSize::Zero();
  // Size of preceeding packets that are not part of feedback.
  DataSize prior_unacked_data = DataSize::Zero();
  // Probe cluster id and parameters including bitrate, number of packets and
  // number of bytes.
  PacedPacketInfo pacing_info;
  // True if the packet is an audio packet, false for video, padding, RTX etc.
  bool audio = false;
  // Transport independent sequence number, any tracked packet should have a
  // sequence number that is unique over the whole call and increasing by 1 for
  // each packet.
  int64_t sequence_number;
  // Tracked data in flight when the packet was sent, excluding unacked data.
  DataSize data_in_flight = DataSize::Zero();
};

struct ReceivedPacket {
  Timestamp send_time = Timestamp::MinusInfinity();
  Timestamp receive_time = Timestamp::PlusInfinity();
  DataSize size = DataSize::Zero();
};

// Transport level feedback

struct RemoteBitrateReport {
  Timestamp receive_time = Timestamp::PlusInfinity();
  DataRate bandwidth = DataRate::Infinity();
};

struct RoundTripTimeUpdate {
  Timestamp receive_time = Timestamp::PlusInfinity();
  TimeDelta round_trip_time = TimeDelta::PlusInfinity();
  bool smoothed = false;
};

struct TransportLossReport {
  Timestamp receive_time = Timestamp::PlusInfinity();
  Timestamp start_time = Timestamp::PlusInfinity();
  Timestamp end_time = Timestamp::PlusInfinity();
  uint64_t packets_lost_delta = 0;
  uint64_t packets_received_delta = 0;
};

// Packet level feedback

struct PacketResult {
  class ReceiveTimeOrder {
   public:
    bool operator()(const PacketResult& lhs, const PacketResult& rhs);
  };

  PacketResult();
  PacketResult(const PacketResult&);
  ~PacketResult();

  SentPacket sent_packet;
  Timestamp receive_time = Timestamp::PlusInfinity();
};

struct TransportPacketsFeedback {
  TransportPacketsFeedback();
  TransportPacketsFeedback(const TransportPacketsFeedback& other);
  ~TransportPacketsFeedback();

  Timestamp feedback_time = Timestamp::PlusInfinity();
  Timestamp first_unacked_send_time = Timestamp::PlusInfinity();
  DataSize data_in_flight = DataSize::Zero();
  DataSize prior_in_flight = DataSize::Zero();
  std::vector<PacketResult> packet_feedbacks;

  // Arrival times for messages without send time information.
  std::vector<Timestamp> sendless_arrival_times;

  std::vector<PacketResult> ReceivedWithSendInfo() const;
  std::vector<PacketResult> LostWithSendInfo() const;
  std::vector<PacketResult> PacketsWithFeedback() const;
  std::vector<PacketResult> SortedByReceiveTime() const;
};

// Network estimation

struct NetworkEstimate {
  Timestamp at_time = Timestamp::PlusInfinity();
  // Deprecated, use TargetTransferRate::target_rate instead.
  DataRate bandwidth = DataRate::Infinity();
  TimeDelta round_trip_time = TimeDelta::PlusInfinity();
  TimeDelta bwe_period = TimeDelta::PlusInfinity();

  float loss_rate_ratio = 0;
};

// Network control

struct PacerConfig {
  Timestamp at_time = Timestamp::PlusInfinity();
  // Pacer should send at most data_window data over time_window duration.
  DataSize data_window = DataSize::Infinity();
  TimeDelta time_window = TimeDelta::PlusInfinity();
  // Pacer should send at least pad_window data over time_window duration.
  DataSize pad_window = DataSize::Zero();
  DataRate data_rate() const { return data_window / time_window; }
  DataRate pad_rate() const { return pad_window / time_window; }
};

struct ProbeClusterConfig {
  Timestamp at_time = Timestamp::PlusInfinity();
  DataRate target_data_rate = DataRate::Zero();
  TimeDelta target_duration = TimeDelta::Zero();
  int32_t target_probe_count = 0;
  int32_t id = 0;
};

struct TargetTransferRate {
  Timestamp at_time = Timestamp::PlusInfinity();
  // The estimate on which the target rate is based on.
  NetworkEstimate network_estimate;
  DataRate target_rate = DataRate::Zero();
  DataRate stable_target_rate = DataRate::Zero();
  double cwnd_reduce_ratio = 0;
};

// Contains updates of network controller comand state. Using optionals to
// indicate whether a member has been updated. The array of probe clusters
// should be used to send out probes if not empty.
struct NetworkControlUpdate {
  NetworkControlUpdate();
  NetworkControlUpdate(const NetworkControlUpdate&);
  ~NetworkControlUpdate();
  absl::optional<DataSize> congestion_window;
  absl::optional<PacerConfig> pacer_config;
  std::vector<ProbeClusterConfig> probe_cluster_configs;
  absl::optional<TargetTransferRate> target_rate;
};

// Process control
struct ProcessInterval {
  ProcessInterval();
  ProcessInterval(const ProcessInterval&);
  ~ProcessInterval();
  Timestamp at_time = Timestamp::PlusInfinity();
  absl::optional<DataSize> pacer_queue;
};

// Under development, subject to change without notice.
struct NetworkStateEstimate {
  double confidence = NAN;
  // The time the estimate was received/calculated.
  Timestamp update_time = Timestamp::MinusInfinity();
  Timestamp last_receive_time = Timestamp::MinusInfinity();
  Timestamp last_send_time = Timestamp::MinusInfinity();

  // Total estimated link capacity.
  DataRate link_capacity = DataRate::MinusInfinity();
  // Used as a safe measure of available capacity.
  DataRate link_capacity_lower = DataRate::MinusInfinity();
  // Used as limit for increasing bitrate.
  DataRate link_capacity_upper = DataRate::MinusInfinity();

  TimeDelta pre_link_buffer_delay = TimeDelta::MinusInfinity();
  TimeDelta post_link_buffer_delay = TimeDelta::MinusInfinity();
  TimeDelta propagation_delay = TimeDelta::MinusInfinity();

  // Only for debugging
  TimeDelta time_delta = TimeDelta::MinusInfinity();
  Timestamp last_feed_time = Timestamp::MinusInfinity();
  double cross_delay_rate = NAN;
  double spike_delay_rate = NAN;
  DataRate link_capacity_std_dev = DataRate::MinusInfinity();
  DataRate link_capacity_min = DataRate::MinusInfinity();
  double cross_traffic_ratio = NAN;
};
}  // namespace webrtc

#endif  // API_TRANSPORT_NETWORK_TYPES_H_
