/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_RTP_RTCP_SOURCE_RTCP_RECEIVER_H_
#define MODULES_RTP_RTCP_SOURCE_RTCP_RECEIVER_H_

#include <list>
#include <map>
#include <string>
#include <vector>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "api/sequence_checker.h"
#include "api/units/time_delta.h"
#include "modules/rtp_rtcp/include/report_block_data.h"
#include "modules/rtp_rtcp/include/rtcp_statistics.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "modules/rtp_rtcp/source/rtcp_nack_stats.h"
#include "modules/rtp_rtcp/source/rtcp_packet/dlrr.h"
#include "modules/rtp_rtcp/source/rtcp_packet/tmmb_item.h"
#include "modules/rtp_rtcp/source/rtp_rtcp_interface.h"
#include "rtc_base/containers/flat_map.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/system/no_unique_address.h"
#include "rtc_base/thread_annotations.h"
#include "system_wrappers/include/ntp_time.h"

namespace webrtc {

class ModuleRtpRtcpImpl2;
class VideoBitrateAllocationObserver;

namespace rtcp {
class CommonHeader;
class ReportBlock;
class Rrtr;
class TargetBitrate;
class TmmbItem;
}  // namespace rtcp

class RTCPReceiver final {
 public:
  class ModuleRtpRtcp {
   public:
    virtual void SetTmmbn(std::vector<rtcp::TmmbItem> bounding_set) = 0;
    virtual void OnRequestSendReport() = 0;
    virtual void OnReceivedNack(
        const std::vector<uint16_t>& nack_sequence_numbers) = 0;
    virtual void OnReceivedRtcpReportBlocks(
        const ReportBlockList& report_blocks) = 0;

   protected:
    virtual ~ModuleRtpRtcp() = default;
  };
  // Standardized stats derived from the non-sender RTT.
  class NonSenderRttStats {
   public:
    NonSenderRttStats() = default;
    NonSenderRttStats(const NonSenderRttStats&) = default;
    NonSenderRttStats& operator=(const NonSenderRttStats&) = default;
    ~NonSenderRttStats() = default;
    void Update(TimeDelta non_sender_rtt_seconds) {
      round_trip_time_ = non_sender_rtt_seconds;
      total_round_trip_time_ += non_sender_rtt_seconds;
      round_trip_time_measurements_++;
    }
    void Invalidate() { round_trip_time_.reset(); }
    // https://www.w3.org/TR/webrtc-stats/#dom-rtcremoteoutboundrtpstreamstats-roundtriptime
    absl::optional<TimeDelta> round_trip_time() const {
      return round_trip_time_;
    }
    // https://www.w3.org/TR/webrtc-stats/#dom-rtcremoteoutboundrtpstreamstats-totalroundtriptime
    TimeDelta total_round_trip_time() const { return total_round_trip_time_; }
    // https://www.w3.org/TR/webrtc-stats/#dom-rtcremoteoutboundrtpstreamstats-roundtriptimemeasurements
    int round_trip_time_measurements() const {
      return round_trip_time_measurements_;
    }

   private:
    absl::optional<TimeDelta> round_trip_time_;
    TimeDelta total_round_trip_time_ = TimeDelta::Zero();
    int round_trip_time_measurements_ = 0;
  };

  RTCPReceiver(const RtpRtcpInterface::Configuration& config,
               ModuleRtpRtcp* owner);

  RTCPReceiver(const RtpRtcpInterface::Configuration& config,
               ModuleRtpRtcpImpl2* owner);

  ~RTCPReceiver();

  void IncomingPacket(const uint8_t* packet, size_t packet_size) {
    IncomingPacket(rtc::MakeArrayView(packet, packet_size));
  }
  void IncomingPacket(rtc::ArrayView<const uint8_t> packet);

  int64_t LastReceivedReportBlockMs() const;

  void set_local_media_ssrc(uint32_t ssrc);
  uint32_t local_media_ssrc() const;

  void SetRemoteSSRC(uint32_t ssrc);
  uint32_t RemoteSSRC() const;

  bool receiver_only() const { return receiver_only_; }

  // Get received NTP.
  // The types for the arguments below derive from the specification:
  // - `remote_sender_packet_count`: `RTCSentRtpStreamStats.packetsSent` [1]
  // - `remote_sender_octet_count`: `RTCSentRtpStreamStats.bytesSent` [1]
  // - `remote_sender_reports_count`:
  //   `RTCRemoteOutboundRtpStreamStats.reportsSent` [2]
  // [1] https://www.w3.org/TR/webrtc-stats/#remoteoutboundrtpstats-dict*
  // [2] https://www.w3.org/TR/webrtc-stats/#dom-rtcsentrtpstreamstats
  bool NTP(uint32_t* received_ntp_secs,
           uint32_t* received_ntp_frac,
           uint32_t* rtcp_arrival_time_secs,
           uint32_t* rtcp_arrival_time_frac,
           uint32_t* rtcp_timestamp,
           uint32_t* remote_sender_packet_count,
           uint64_t* remote_sender_octet_count,
           uint64_t* remote_sender_reports_count) const;

  std::vector<rtcp::ReceiveTimeInfo> ConsumeReceivedXrReferenceTimeInfo();

  // Get rtt.
  int32_t RTT(uint32_t remote_ssrc,
              int64_t* last_rtt_ms,
              int64_t* avg_rtt_ms,
              int64_t* min_rtt_ms,
              int64_t* max_rtt_ms) const;

  // Returns non-sender RTT metrics for the remote SSRC.
  NonSenderRttStats GetNonSenderRTT() const;

  void SetNonSenderRttMeasurement(bool enabled);
  bool GetAndResetXrRrRtt(int64_t* rtt_ms);

  // Called once per second on the worker thread to do rtt calculations.
  // Returns an optional rtt value if one is available.
  absl::optional<TimeDelta> OnPeriodicRttUpdate(Timestamp newer_than,
                                                bool sending);

  // A snapshot of Report Blocks with additional data of interest to statistics.
  // Within this list, the source SSRC is unique and ReportBlockData represents
  // the latest Report Block that was received for that SSRC.
  std::vector<ReportBlockData> GetLatestReportBlockData() const;

  // Returns true if we haven't received an RTCP RR for several RTCP
  // intervals, but only triggers true once.
  bool RtcpRrTimeout();

  // Returns true if we haven't received an RTCP RR telling the receive side
  // has not received RTP packets for too long, i.e. extended highest sequence
  // number hasn't increased for several RTCP intervals. The function only
  // returns true once until a new RR is received.
  bool RtcpRrSequenceNumberTimeout();

  std::vector<rtcp::TmmbItem> TmmbrReceived();
  // Return true if new bandwidth should be set.
  bool UpdateTmmbrTimers();
  std::vector<rtcp::TmmbItem> BoundingSet(bool* tmmbr_owner);
  // Set new bandwidth and notify remote clients about it.
  void NotifyTmmbrUpdated();

 private:
#if RTC_DCHECK_IS_ON
  class CustomSequenceChecker : public SequenceChecker {
   public:
    explicit CustomSequenceChecker(bool disable_checks)
        : disable_checks_(disable_checks) {}
    bool IsCurrent() const {
      if (disable_checks_)
        return true;
      return SequenceChecker::IsCurrent();
    }

   private:
    const bool disable_checks_;
  };
#else
  class CustomSequenceChecker : public SequenceChecker {
   public:
    explicit CustomSequenceChecker(bool) {}
  };
#endif

  // A lightweight inlined set of local SSRCs.
  class RegisteredSsrcs {
   public:
    static constexpr size_t kMediaSsrcIndex = 0;
    static constexpr size_t kMaxSsrcs = 3;
    // Initializes the set of registered local SSRCS by extracting them from the
    // provided `config`. The `disable_sequence_checker` flag is a workaround
    // to be able to use a sequence checker without breaking downstream
    // code that currently doesn't follow the same threading rules as webrtc.
    RegisteredSsrcs(bool disable_sequence_checker,
                    const RtpRtcpInterface::Configuration& config);

    // Indicates if `ssrc` is in the set of registered local SSRCs.
    bool contains(uint32_t ssrc) const;
    uint32_t media_ssrc() const;
    void set_media_ssrc(uint32_t ssrc);

   private:
    RTC_NO_UNIQUE_ADDRESS CustomSequenceChecker packet_sequence_checker_;
    absl::InlinedVector<uint32_t, kMaxSsrcs> ssrcs_
        RTC_GUARDED_BY(packet_sequence_checker_);
  };

  struct PacketInformation;

  // Structure for handing TMMBR and TMMBN rtcp messages (RFC5104,
  // section 3.5.4).
  struct TmmbrInformation {
    struct TimedTmmbrItem {
      rtcp::TmmbItem tmmbr_item;
      int64_t last_updated_ms;
    };

    int64_t last_time_received_ms = 0;

    bool ready_for_delete = false;

    std::vector<rtcp::TmmbItem> tmmbn;
    std::map<uint32_t, TimedTmmbrItem> tmmbr;
  };

  // Structure for storing received RRTR RTCP messages (RFC3611, section 4.4).
  struct RrtrInformation {
    RrtrInformation(uint32_t ssrc,
                    uint32_t received_remote_mid_ntp_time,
                    uint32_t local_receive_mid_ntp_time)
        : ssrc(ssrc),
          received_remote_mid_ntp_time(received_remote_mid_ntp_time),
          local_receive_mid_ntp_time(local_receive_mid_ntp_time) {}

    uint32_t ssrc;
    // Received NTP timestamp in compact representation.
    uint32_t received_remote_mid_ntp_time;
    // NTP time when the report was received in compact representation.
    uint32_t local_receive_mid_ntp_time;
  };

  struct LastFirStatus {
    LastFirStatus(int64_t now_ms, uint8_t sequence_number)
        : request_ms(now_ms), sequence_number(sequence_number) {}
    int64_t request_ms;
    uint8_t sequence_number;
  };

  class RttStats {
   public:
    RttStats() = default;
    RttStats(const RttStats&) = default;
    RttStats& operator=(const RttStats&) = default;

    void AddRtt(TimeDelta rtt);

    TimeDelta last_rtt() const { return last_rtt_; }
    TimeDelta min_rtt() const { return min_rtt_; }
    TimeDelta max_rtt() const { return max_rtt_; }
    TimeDelta average_rtt() const { return sum_rtt_ / num_rtts_; }

   private:
    TimeDelta last_rtt_ = TimeDelta::Zero();
    TimeDelta min_rtt_ = TimeDelta::PlusInfinity();
    TimeDelta max_rtt_ = TimeDelta::MinusInfinity();
    TimeDelta sum_rtt_ = TimeDelta::Zero();
    size_t num_rtts_ = 0;
  };

  bool ParseCompoundPacket(rtc::ArrayView<const uint8_t> packet,
                           PacketInformation* packet_information);

  void TriggerCallbacksFromRtcpPacket(
      const PacketInformation& packet_information);

  TmmbrInformation* FindOrCreateTmmbrInfo(uint32_t remote_ssrc)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(rtcp_receiver_lock_);
  // Update TmmbrInformation (if present) is alive.
  void UpdateTmmbrRemoteIsAlive(uint32_t remote_ssrc)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(rtcp_receiver_lock_);
  TmmbrInformation* GetTmmbrInformation(uint32_t remote_ssrc)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(rtcp_receiver_lock_);

  void HandleSenderReport(const rtcp::CommonHeader& rtcp_block,
                          PacketInformation* packet_information)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(rtcp_receiver_lock_);

  void HandleReceiverReport(const rtcp::CommonHeader& rtcp_block,
                            PacketInformation* packet_information)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(rtcp_receiver_lock_);

  void HandleReportBlock(const rtcp::ReportBlock& report_block,
                         PacketInformation* packet_information,
                         uint32_t remote_ssrc)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(rtcp_receiver_lock_);

  void HandleSdes(const rtcp::CommonHeader& rtcp_block,
                  PacketInformation* packet_information)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(rtcp_receiver_lock_);

  void HandleXr(const rtcp::CommonHeader& rtcp_block,
                PacketInformation* packet_information,
                bool& contains_dlrr,
                uint32_t& ssrc)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(rtcp_receiver_lock_);

  void HandleXrReceiveReferenceTime(uint32_t sender_ssrc,
                                    const rtcp::Rrtr& rrtr)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(rtcp_receiver_lock_);

  void HandleXrDlrrReportBlock(uint32_t ssrc, const rtcp::ReceiveTimeInfo& rti)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(rtcp_receiver_lock_);

  void HandleXrTargetBitrate(uint32_t ssrc,
                             const rtcp::TargetBitrate& target_bitrate,
                             PacketInformation* packet_information)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(rtcp_receiver_lock_);

  void HandleNack(const rtcp::CommonHeader& rtcp_block,
                  PacketInformation* packet_information)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(rtcp_receiver_lock_);

  void HandleApp(const rtcp::CommonHeader& rtcp_block,
                 PacketInformation* packet_information)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(rtcp_receiver_lock_);

  void HandleBye(const rtcp::CommonHeader& rtcp_block)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(rtcp_receiver_lock_);

  void HandlePli(const rtcp::CommonHeader& rtcp_block,
                 PacketInformation* packet_information)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(rtcp_receiver_lock_);

  void HandlePsfbApp(const rtcp::CommonHeader& rtcp_block,
                     PacketInformation* packet_information)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(rtcp_receiver_lock_);

  void HandleTmmbr(const rtcp::CommonHeader& rtcp_block,
                   PacketInformation* packet_information)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(rtcp_receiver_lock_);

  void HandleTmmbn(const rtcp::CommonHeader& rtcp_block,
                   PacketInformation* packet_information)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(rtcp_receiver_lock_);

  void HandleSrReq(const rtcp::CommonHeader& rtcp_block,
                   PacketInformation* packet_information)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(rtcp_receiver_lock_);

  void HandleFir(const rtcp::CommonHeader& rtcp_block,
                 PacketInformation* packet_information)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(rtcp_receiver_lock_);

  void HandleTransportFeedback(const rtcp::CommonHeader& rtcp_block,
                               PacketInformation* packet_information)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(rtcp_receiver_lock_);

  bool RtcpRrTimeoutLocked(Timestamp now)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(rtcp_receiver_lock_);

  bool RtcpRrSequenceNumberTimeoutLocked(Timestamp now)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(rtcp_receiver_lock_);

  Clock* const clock_;
  const bool receiver_only_;
  ModuleRtpRtcp* const rtp_rtcp_;
  // The set of registered local SSRCs.
  RegisteredSsrcs registered_ssrcs_;

  RtcpBandwidthObserver* const rtcp_bandwidth_observer_;
  RtcpIntraFrameObserver* const rtcp_intra_frame_observer_;
  RtcpLossNotificationObserver* const rtcp_loss_notification_observer_;
  NetworkStateEstimateObserver* const network_state_estimate_observer_;
  TransportFeedbackObserver* const transport_feedback_observer_;
  VideoBitrateAllocationObserver* const bitrate_allocation_observer_;
  const TimeDelta report_interval_;

  mutable Mutex rtcp_receiver_lock_;
  uint32_t remote_ssrc_ RTC_GUARDED_BY(rtcp_receiver_lock_);

  // Received sender report.
  NtpTime remote_sender_ntp_time_ RTC_GUARDED_BY(rtcp_receiver_lock_);
  uint32_t remote_sender_rtp_time_ RTC_GUARDED_BY(rtcp_receiver_lock_);
  // When did we receive the last send report.
  NtpTime last_received_sr_ntp_ RTC_GUARDED_BY(rtcp_receiver_lock_);
  uint32_t remote_sender_packet_count_ RTC_GUARDED_BY(rtcp_receiver_lock_);
  uint64_t remote_sender_octet_count_ RTC_GUARDED_BY(rtcp_receiver_lock_);
  uint64_t remote_sender_reports_count_ RTC_GUARDED_BY(rtcp_receiver_lock_);

  // Received RRTR information in ascending receive time order.
  std::list<RrtrInformation> received_rrtrs_
      RTC_GUARDED_BY(rtcp_receiver_lock_);
  // Received RRTR information mapped by remote ssrc.
  flat_map<uint32_t, std::list<RrtrInformation>::iterator>
      received_rrtrs_ssrc_it_ RTC_GUARDED_BY(rtcp_receiver_lock_);

  // Estimated rtt, zero when there is no valid estimate.
  bool xr_rrtr_status_ RTC_GUARDED_BY(rtcp_receiver_lock_);
  int64_t xr_rr_rtt_ms_;

  int64_t oldest_tmmbr_info_ms_ RTC_GUARDED_BY(rtcp_receiver_lock_);
  // Mapped by remote ssrc.
  flat_map<uint32_t, TmmbrInformation> tmmbr_infos_
      RTC_GUARDED_BY(rtcp_receiver_lock_);

  // Round-Trip Time per remote sender ssrc.
  flat_map<uint32_t, RttStats> rtts_ RTC_GUARDED_BY(rtcp_receiver_lock_);
  // Non-sender Round-trip time per remote ssrc.
  flat_map<uint32_t, NonSenderRttStats> non_sender_rtts_
      RTC_GUARDED_BY(rtcp_receiver_lock_);

  // Report blocks per local source ssrc.
  flat_map<uint32_t, ReportBlockData> received_report_blocks_
      RTC_GUARDED_BY(rtcp_receiver_lock_);
  flat_map<uint32_t, LastFirStatus> last_fir_
      RTC_GUARDED_BY(rtcp_receiver_lock_);

  // The last time we received an RTCP Report block for this module.
  Timestamp last_received_rb_ RTC_GUARDED_BY(rtcp_receiver_lock_) =
      Timestamp::PlusInfinity();

  // The time we last received an RTCP RR telling we have successfully
  // delivered RTP packet to the remote side.
  Timestamp last_increased_sequence_number_ = Timestamp::PlusInfinity();

  RtcpCnameCallback* const cname_callback_;
  ReportBlockDataObserver* const report_block_data_observer_;

  RtcpPacketTypeCounterObserver* const packet_type_counter_observer_;
  RtcpPacketTypeCounter packet_type_counter_;

  RtcpNackStats nack_stats_;

  size_t num_skipped_packets_;
  int64_t last_skipped_packets_warning_ms_;
};
}  // namespace webrtc
#endif  // MODULES_RTP_RTCP_SOURCE_RTCP_RECEIVER_H_
