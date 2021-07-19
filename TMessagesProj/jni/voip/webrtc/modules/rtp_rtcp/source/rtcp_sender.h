/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_RTP_RTCP_SOURCE_RTCP_SENDER_H_
#define MODULES_RTP_RTCP_SOURCE_RTCP_SENDER_H_

#include <map>
#include <memory>
#include <set>
#include <string>
#include <vector>

#include "absl/types/optional.h"
#include "api/call/transport.h"
#include "api/video/video_bitrate_allocation.h"
#include "modules/remote_bitrate_estimator/include/remote_bitrate_estimator.h"
#include "modules/rtp_rtcp/include/receive_statistics.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "modules/rtp_rtcp/source/rtcp_nack_stats.h"
#include "modules/rtp_rtcp/source/rtcp_packet.h"
#include "modules/rtp_rtcp/source/rtcp_packet/compound_packet.h"
#include "modules/rtp_rtcp/source/rtcp_packet/dlrr.h"
#include "modules/rtp_rtcp/source/rtcp_packet/loss_notification.h"
#include "modules/rtp_rtcp/source/rtcp_packet/report_block.h"
#include "modules/rtp_rtcp/source/rtcp_packet/tmmb_item.h"
#include "modules/rtp_rtcp/source/rtp_rtcp_interface.h"
#include "rtc_base/random.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/thread_annotations.h"

namespace webrtc {

class RTCPReceiver;
class RtcEventLog;

class RTCPSender final {
 public:
  struct FeedbackState {
    FeedbackState();
    FeedbackState(const FeedbackState&);
    FeedbackState(FeedbackState&&);

    ~FeedbackState();

    uint32_t packets_sent;
    size_t media_bytes_sent;
    uint32_t send_bitrate;

    uint32_t last_rr_ntp_secs;
    uint32_t last_rr_ntp_frac;
    uint32_t remote_sr;

    std::vector<rtcp::ReceiveTimeInfo> last_xr_rtis;

    // Used when generating TMMBR.
    RTCPReceiver* receiver;
  };

  explicit RTCPSender(const RtpRtcpInterface::Configuration& config);

  RTCPSender() = delete;
  RTCPSender(const RTCPSender&) = delete;
  RTCPSender& operator=(const RTCPSender&) = delete;

  virtual ~RTCPSender();

  RtcpMode Status() const RTC_LOCKS_EXCLUDED(mutex_rtcp_sender_);
  void SetRTCPStatus(RtcpMode method) RTC_LOCKS_EXCLUDED(mutex_rtcp_sender_);

  bool Sending() const RTC_LOCKS_EXCLUDED(mutex_rtcp_sender_);
  void SetSendingStatus(const FeedbackState& feedback_state,
                        bool enabled)
      RTC_LOCKS_EXCLUDED(mutex_rtcp_sender_);  // combine the functions

  int32_t SetNackStatus(bool enable) RTC_LOCKS_EXCLUDED(mutex_rtcp_sender_);

  void SetTimestampOffset(uint32_t timestamp_offset)
      RTC_LOCKS_EXCLUDED(mutex_rtcp_sender_);

  // TODO(bugs.webrtc.org/6458): Remove default parameter value when all the
  // depending projects are updated to correctly set payload type.
  void SetLastRtpTime(uint32_t rtp_timestamp,
                      int64_t capture_time_ms,
                      int8_t payload_type = -1)
      RTC_LOCKS_EXCLUDED(mutex_rtcp_sender_);

  void SetRtpClockRate(int8_t payload_type, int rtp_clock_rate_hz)
      RTC_LOCKS_EXCLUDED(mutex_rtcp_sender_);

  uint32_t SSRC() const { return ssrc_; }

  void SetRemoteSSRC(uint32_t ssrc) RTC_LOCKS_EXCLUDED(mutex_rtcp_sender_);

  int32_t SetCNAME(const char* cName) RTC_LOCKS_EXCLUDED(mutex_rtcp_sender_);

  bool TimeToSendRTCPReport(bool sendKeyframeBeforeRTP = false) const
      RTC_LOCKS_EXCLUDED(mutex_rtcp_sender_);

  int32_t SendRTCP(const FeedbackState& feedback_state,
                   RTCPPacketType packetType,
                   int32_t nackSize = 0,
                   const uint16_t* nackList = 0)
      RTC_LOCKS_EXCLUDED(mutex_rtcp_sender_);

  int32_t SendLossNotification(const FeedbackState& feedback_state,
                               uint16_t last_decoded_seq_num,
                               uint16_t last_received_seq_num,
                               bool decodability_flag,
                               bool buffering_allowed)
      RTC_LOCKS_EXCLUDED(mutex_rtcp_sender_);

  void SetRemb(int64_t bitrate_bps, std::vector<uint32_t> ssrcs)
      RTC_LOCKS_EXCLUDED(mutex_rtcp_sender_);

  void UnsetRemb() RTC_LOCKS_EXCLUDED(mutex_rtcp_sender_);

  bool TMMBR() const RTC_LOCKS_EXCLUDED(mutex_rtcp_sender_);

  void SetMaxRtpPacketSize(size_t max_packet_size)
      RTC_LOCKS_EXCLUDED(mutex_rtcp_sender_);

  void SetTmmbn(std::vector<rtcp::TmmbItem> bounding_set)
      RTC_LOCKS_EXCLUDED(mutex_rtcp_sender_);

  void SetCsrcs(const std::vector<uint32_t>& csrcs)
      RTC_LOCKS_EXCLUDED(mutex_rtcp_sender_);

  void SetTargetBitrate(unsigned int target_bitrate)
      RTC_LOCKS_EXCLUDED(mutex_rtcp_sender_);
  void SetVideoBitrateAllocation(const VideoBitrateAllocation& bitrate)
      RTC_LOCKS_EXCLUDED(mutex_rtcp_sender_);
  void SendCombinedRtcpPacket(
      std::vector<std::unique_ptr<rtcp::RtcpPacket>> rtcp_packets)
      RTC_LOCKS_EXCLUDED(mutex_rtcp_sender_);

 private:
  class RtcpContext;
  class PacketSender;

  absl::optional<int32_t> ComputeCompoundRTCPPacket(
      const FeedbackState& feedback_state,
      RTCPPacketType packet_type,
      int32_t nack_size,
      const uint16_t* nack_list,
      PacketSender& sender) RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_rtcp_sender_);

  // Determine which RTCP messages should be sent and setup flags.
  void PrepareReport(const FeedbackState& feedback_state)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_rtcp_sender_);

  std::vector<rtcp::ReportBlock> CreateReportBlocks(
      const FeedbackState& feedback_state)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_rtcp_sender_);

  void BuildSR(const RtcpContext& context, PacketSender& sender)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_rtcp_sender_);
  void BuildRR(const RtcpContext& context, PacketSender& sender)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_rtcp_sender_);
  void BuildSDES(const RtcpContext& context, PacketSender& sender)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_rtcp_sender_);
  void BuildPLI(const RtcpContext& context, PacketSender& sender)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_rtcp_sender_);
  void BuildREMB(const RtcpContext& context, PacketSender& sender)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_rtcp_sender_);
  void BuildTMMBR(const RtcpContext& context, PacketSender& sender)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_rtcp_sender_);
  void BuildTMMBN(const RtcpContext& context, PacketSender& sender)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_rtcp_sender_);
  void BuildAPP(const RtcpContext& context, PacketSender& sender)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_rtcp_sender_);
  void BuildLossNotification(const RtcpContext& context, PacketSender& sender)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_rtcp_sender_);
  void BuildExtendedReports(const RtcpContext& context, PacketSender& sender)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_rtcp_sender_);
  void BuildBYE(const RtcpContext& context, PacketSender& sender)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_rtcp_sender_);
  void BuildFIR(const RtcpContext& context, PacketSender& sender)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_rtcp_sender_);
  void BuildNACK(const RtcpContext& context, PacketSender& sender)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_rtcp_sender_);

  const bool audio_;
  const uint32_t ssrc_;
  Clock* const clock_;
  Random random_ RTC_GUARDED_BY(mutex_rtcp_sender_);
  RtcpMode method_ RTC_GUARDED_BY(mutex_rtcp_sender_);

  RtcEventLog* const event_log_;
  Transport* const transport_;

  const int report_interval_ms_;

  mutable Mutex mutex_rtcp_sender_;
  bool sending_ RTC_GUARDED_BY(mutex_rtcp_sender_);

  int64_t next_time_to_send_rtcp_ RTC_GUARDED_BY(mutex_rtcp_sender_);

  uint32_t timestamp_offset_ RTC_GUARDED_BY(mutex_rtcp_sender_);
  uint32_t last_rtp_timestamp_ RTC_GUARDED_BY(mutex_rtcp_sender_);
  int64_t last_frame_capture_time_ms_ RTC_GUARDED_BY(mutex_rtcp_sender_);
  // SSRC that we receive on our RTP channel
  uint32_t remote_ssrc_ RTC_GUARDED_BY(mutex_rtcp_sender_);
  std::string cname_ RTC_GUARDED_BY(mutex_rtcp_sender_);

  ReceiveStatisticsProvider* receive_statistics_
      RTC_GUARDED_BY(mutex_rtcp_sender_);

  // send CSRCs
  std::vector<uint32_t> csrcs_ RTC_GUARDED_BY(mutex_rtcp_sender_);

  // Full intra request
  uint8_t sequence_number_fir_ RTC_GUARDED_BY(mutex_rtcp_sender_);

  rtcp::LossNotification loss_notification_ RTC_GUARDED_BY(mutex_rtcp_sender_);

  // REMB
  int64_t remb_bitrate_ RTC_GUARDED_BY(mutex_rtcp_sender_);
  std::vector<uint32_t> remb_ssrcs_ RTC_GUARDED_BY(mutex_rtcp_sender_);

  std::vector<rtcp::TmmbItem> tmmbn_to_send_ RTC_GUARDED_BY(mutex_rtcp_sender_);
  uint32_t tmmbr_send_bps_ RTC_GUARDED_BY(mutex_rtcp_sender_);
  uint32_t packet_oh_send_ RTC_GUARDED_BY(mutex_rtcp_sender_);
  size_t max_packet_size_ RTC_GUARDED_BY(mutex_rtcp_sender_);

  // True if sending of XR Receiver reference time report is enabled.
  const bool xr_send_receiver_reference_time_enabled_;

  RtcpPacketTypeCounterObserver* const packet_type_counter_observer_;
  RtcpPacketTypeCounter packet_type_counter_ RTC_GUARDED_BY(mutex_rtcp_sender_);

  RtcpNackStats nack_stats_ RTC_GUARDED_BY(mutex_rtcp_sender_);

  VideoBitrateAllocation video_bitrate_allocation_
      RTC_GUARDED_BY(mutex_rtcp_sender_);
  bool send_video_bitrate_allocation_ RTC_GUARDED_BY(mutex_rtcp_sender_);

  std::map<int8_t, int> rtp_clock_rates_khz_ RTC_GUARDED_BY(mutex_rtcp_sender_);
  int8_t last_payload_type_ RTC_GUARDED_BY(mutex_rtcp_sender_);

  absl::optional<VideoBitrateAllocation> CheckAndUpdateLayerStructure(
      const VideoBitrateAllocation& bitrate) const
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_rtcp_sender_);

  void SetFlag(uint32_t type, bool is_volatile)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_rtcp_sender_);
  bool IsFlagPresent(uint32_t type) const
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_rtcp_sender_);
  bool ConsumeFlag(uint32_t type, bool forced = false)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_rtcp_sender_);
  bool AllVolatileFlagsConsumed() const
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_rtcp_sender_);
  struct ReportFlag {
    ReportFlag(uint32_t type, bool is_volatile)
        : type(type), is_volatile(is_volatile) {}
    bool operator<(const ReportFlag& flag) const { return type < flag.type; }
    bool operator==(const ReportFlag& flag) const { return type == flag.type; }
    const uint32_t type;
    const bool is_volatile;
  };

  std::set<ReportFlag> report_flags_ RTC_GUARDED_BY(mutex_rtcp_sender_);

  typedef void (RTCPSender::*BuilderFunc)(const RtcpContext&, PacketSender&);
  // Map from RTCPPacketType to builder.
  std::map<uint32_t, BuilderFunc> builders_;
};
}  // namespace webrtc

#endif  // MODULES_RTP_RTCP_SOURCE_RTCP_SENDER_H_
