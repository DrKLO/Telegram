/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_RTP_RTCP_SOURCE_RTP_SENDER_EGRESS_H_
#define MODULES_RTP_RTCP_SOURCE_RTP_SENDER_EGRESS_H_

#include <map>
#include <memory>
#include <utility>
#include <vector>

#include "absl/types/optional.h"
#include "api/call/transport.h"
#include "api/rtc_event_log/rtc_event_log.h"
#include "api/sequence_checker.h"
#include "api/task_queue/pending_task_safety_flag.h"
#include "api/task_queue/task_queue_base.h"
#include "api/units/data_rate.h"
#include "api/units/time_delta.h"
#include "api/units/timestamp.h"
#include "modules/remote_bitrate_estimator/test/bwe_test_logging.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "modules/rtp_rtcp/source/packet_sequencer.h"
#include "modules/rtp_rtcp/source/rtp_packet_history.h"
#include "modules/rtp_rtcp/source/rtp_packet_to_send.h"
#include "modules/rtp_rtcp/source/rtp_rtcp_interface.h"
#include "modules/rtp_rtcp/source/rtp_sequence_number_map.h"
#include "rtc_base/bitrate_tracker.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/system/no_unique_address.h"
#include "rtc_base/task_utils/repeating_task.h"
#include "rtc_base/thread_annotations.h"

namespace webrtc {

class RtpSenderEgress {
 public:
  // Helper class that redirects packets directly to the send part of this class
  // without passing through an actual paced sender.
  class NonPacedPacketSender : public RtpPacketSender {
   public:
    NonPacedPacketSender(TaskQueueBase& worker_queue,
                         RtpSenderEgress* sender,
                         PacketSequencer* sequencer);
    virtual ~NonPacedPacketSender();

    void EnqueuePackets(
        std::vector<std::unique_ptr<RtpPacketToSend>> packets) override;
    // Since we don't pace packets, there's no pending packets to remove.
    void RemovePacketsForSsrc(uint32_t ssrc) override {}

   private:
    void PrepareForSend(RtpPacketToSend* packet);
    TaskQueueBase& worker_queue_;
    uint16_t transport_sequence_number_;
    RtpSenderEgress* const sender_;
    PacketSequencer* sequencer_;
    ScopedTaskSafety task_safety_;
  };

  RtpSenderEgress(const RtpRtcpInterface::Configuration& config,
                  RtpPacketHistory* packet_history);
  ~RtpSenderEgress();

  void SendPacket(std::unique_ptr<RtpPacketToSend> packet,
                  const PacedPacketInfo& pacing_info);
  void OnBatchComplete();
  uint32_t Ssrc() const { return ssrc_; }
  absl::optional<uint32_t> RtxSsrc() const { return rtx_ssrc_; }
  absl::optional<uint32_t> FlexFecSsrc() const { return flexfec_ssrc_; }

  RtpSendRates GetSendRates(Timestamp now) const;
  void GetDataCounters(StreamDataCounters* rtp_stats,
                       StreamDataCounters* rtx_stats) const;

  void ForceIncludeSendPacketsInAllocation(bool part_of_allocation);

  bool MediaHasBeenSent() const;
  void SetMediaHasBeenSent(bool media_sent);
  void SetTimestampOffset(uint32_t timestamp);

  // For each sequence number in `sequence_number`, recall the last RTP packet
  // which bore it - its timestamp and whether it was the first and/or last
  // packet in that frame. If all of the given sequence numbers could be
  // recalled, return a vector with all of them (in corresponding order).
  // If any could not be recalled, return an empty vector.
  std::vector<RtpSequenceNumberMap::Info> GetSentRtpPacketInfos(
      rtc::ArrayView<const uint16_t> sequence_numbers) const;

  void SetFecProtectionParameters(const FecProtectionParams& delta_params,
                                  const FecProtectionParams& key_params);
  std::vector<std::unique_ptr<RtpPacketToSend>> FetchFecPackets();

  // Clears pending status for these sequence numbers in the packet history.
  void OnAbortedRetransmissions(
      rtc::ArrayView<const uint16_t> sequence_numbers);

 private:
  struct Packet {
    std::unique_ptr<RtpPacketToSend> rtp_packet;
    PacedPacketInfo info;
    Timestamp now;
  };
  void CompleteSendPacket(const Packet& compound_packet, bool last_in_batch);
  bool HasCorrectSsrc(const RtpPacketToSend& packet) const;
  void AddPacketToTransportFeedback(uint16_t packet_id,
                                    const RtpPacketToSend& packet,
                                    const PacedPacketInfo& pacing_info);

  // Sends packet on to `transport_`, leaving the RTP module.
  bool SendPacketToNetwork(const RtpPacketToSend& packet,
                           const PacketOptions& options,
                           const PacedPacketInfo& pacing_info);
  void UpdateRtpStats(Timestamp now,
                      uint32_t packet_ssrc,
                      RtpPacketMediaType packet_type,
                      RtpPacketCounter counter,
                      size_t packet_size);
#if BWE_TEST_LOGGING_COMPILE_TIME_ENABLE
  void BweTestLoggingPlot(Timestamp now, uint32_t packet_ssrc);
#endif

  // Called on a timer, once a second, on the worker_queue_.
  void PeriodicUpdate();

  const bool enable_send_packet_batching_;
  TaskQueueBase* const worker_queue_;
  const uint32_t ssrc_;
  const absl::optional<uint32_t> rtx_ssrc_;
  const absl::optional<uint32_t> flexfec_ssrc_;
  const bool populate_network2_timestamp_;
  Clock* const clock_;
  RtpPacketHistory* const packet_history_ RTC_GUARDED_BY(worker_queue_);
  Transport* const transport_;
  RtcEventLog* const event_log_;
  const bool is_audio_;
  const bool need_rtp_packet_infos_;
  VideoFecGenerator* const fec_generator_ RTC_GUARDED_BY(worker_queue_);
  absl::optional<uint16_t> last_sent_seq_ RTC_GUARDED_BY(worker_queue_);
  absl::optional<uint16_t> last_sent_rtx_seq_ RTC_GUARDED_BY(worker_queue_);

  TransportFeedbackObserver* const transport_feedback_observer_;
  SendPacketObserver* const send_packet_observer_;
  StreamDataCountersCallback* const rtp_stats_callback_;
  BitrateStatisticsObserver* const bitrate_callback_;

  bool media_has_been_sent_ RTC_GUARDED_BY(worker_queue_);
  bool force_part_of_allocation_ RTC_GUARDED_BY(worker_queue_);
  uint32_t timestamp_offset_ RTC_GUARDED_BY(worker_queue_);

  StreamDataCounters rtp_stats_ RTC_GUARDED_BY(worker_queue_);
  StreamDataCounters rtx_rtp_stats_ RTC_GUARDED_BY(worker_queue_);
  // One element per value in RtpPacketMediaType, with index matching value.
  std::vector<BitrateTracker> send_rates_ RTC_GUARDED_BY(worker_queue_);
  absl::optional<std::pair<FecProtectionParams, FecProtectionParams>>
      pending_fec_params_ RTC_GUARDED_BY(worker_queue_);

  // Maps sent packets' sequence numbers to a tuple consisting of:
  // 1. The timestamp, without the randomizing offset mandated by the RFC.
  // 2. Whether the packet was the first in its frame.
  // 3. Whether the packet was the last in its frame.
  const std::unique_ptr<RtpSequenceNumberMap> rtp_sequence_number_map_
      RTC_GUARDED_BY(worker_queue_);
  RepeatingTaskHandle update_task_ RTC_GUARDED_BY(worker_queue_);
  std::vector<Packet> packets_to_send_ RTC_GUARDED_BY(worker_queue_);
  ScopedTaskSafety task_safety_;
};

}  // namespace webrtc

#endif  // MODULES_RTP_RTCP_SOURCE_RTP_SENDER_EGRESS_H_
