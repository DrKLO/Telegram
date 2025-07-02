/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtcp_receiver.h"

#include <string.h>

#include <algorithm>
#include <limits>
#include <map>
#include <memory>
#include <utility>
#include <vector>

#include "api/units/time_delta.h"
#include "api/units/timestamp.h"
#include "api/video/video_bitrate_allocation.h"
#include "api/video/video_bitrate_allocator.h"
#include "modules/rtp_rtcp/source/rtcp_packet/bye.h"
#include "modules/rtp_rtcp/source/rtcp_packet/common_header.h"
#include "modules/rtp_rtcp/source/rtcp_packet/compound_packet.h"
#include "modules/rtp_rtcp/source/rtcp_packet/extended_reports.h"
#include "modules/rtp_rtcp/source/rtcp_packet/fir.h"
#include "modules/rtp_rtcp/source/rtcp_packet/loss_notification.h"
#include "modules/rtp_rtcp/source/rtcp_packet/nack.h"
#include "modules/rtp_rtcp/source/rtcp_packet/pli.h"
#include "modules/rtp_rtcp/source/rtcp_packet/rapid_resync_request.h"
#include "modules/rtp_rtcp/source/rtcp_packet/receiver_report.h"
#include "modules/rtp_rtcp/source/rtcp_packet/remb.h"
#include "modules/rtp_rtcp/source/rtcp_packet/remote_estimate.h"
#include "modules/rtp_rtcp/source/rtcp_packet/sdes.h"
#include "modules/rtp_rtcp/source/rtcp_packet/sender_report.h"
#include "modules/rtp_rtcp/source/rtcp_packet/tmmbn.h"
#include "modules/rtp_rtcp/source/rtcp_packet/tmmbr.h"
#include "modules/rtp_rtcp/source/rtcp_packet/transport_feedback.h"
#include "modules/rtp_rtcp/source/rtp_rtcp_config.h"
#include "modules/rtp_rtcp/source/rtp_rtcp_impl2.h"
#include "modules/rtp_rtcp/source/time_util.h"
#include "modules/rtp_rtcp/source/tmmbr_help.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/time_utils.h"
#include "rtc_base/trace_event.h"
#include "system_wrappers/include/ntp_time.h"

namespace webrtc {
namespace {

using rtcp::CommonHeader;
using rtcp::ReportBlock;

// The number of RTCP time intervals needed to trigger a timeout.
constexpr int kRrTimeoutIntervals = 3;

constexpr TimeDelta kTmmbrTimeoutInterval = TimeDelta::Seconds(25);
constexpr TimeDelta kMaxWarningLogInterval = TimeDelta::Seconds(10);
constexpr TimeDelta kRtcpMinFrameLength = TimeDelta::Millis(17);

// Maximum number of received RRTRs that will be stored.
constexpr size_t kMaxNumberOfStoredRrtrs = 300;

constexpr TimeDelta kDefaultVideoReportInterval = TimeDelta::Seconds(1);
constexpr TimeDelta kDefaultAudioReportInterval = TimeDelta::Seconds(5);

// Returns true if the `timestamp` has exceeded the |interval *
// kRrTimeoutIntervals| period and was reset (set to PlusInfinity()). Returns
// false if the timer was either already reset or if it has not expired.
bool ResetTimestampIfExpired(const Timestamp now,
                             Timestamp& timestamp,
                             TimeDelta interval) {
  if (timestamp.IsInfinite() ||
      now <= timestamp + interval * kRrTimeoutIntervals) {
    return false;
  }

  timestamp = Timestamp::PlusInfinity();
  return true;
}

}  // namespace

constexpr size_t RTCPReceiver::RegisteredSsrcs::kMediaSsrcIndex;
constexpr size_t RTCPReceiver::RegisteredSsrcs::kMaxSsrcs;

RTCPReceiver::RegisteredSsrcs::RegisteredSsrcs(
    bool disable_sequence_checker,
    const RtpRtcpInterface::Configuration& config)
    : packet_sequence_checker_(disable_sequence_checker) {
  packet_sequence_checker_.Detach();
  ssrcs_.push_back(config.local_media_ssrc);
  if (config.rtx_send_ssrc) {
    ssrcs_.push_back(*config.rtx_send_ssrc);
  }
  if (config.fec_generator) {
    absl::optional<uint32_t> flexfec_ssrc = config.fec_generator->FecSsrc();
    if (flexfec_ssrc) {
      ssrcs_.push_back(*flexfec_ssrc);
    }
  }
  // Ensure that the RegisteredSsrcs can inline the SSRCs.
  RTC_DCHECK_LE(ssrcs_.size(), RTCPReceiver::RegisteredSsrcs::kMaxSsrcs);
}

bool RTCPReceiver::RegisteredSsrcs::contains(uint32_t ssrc) const {
  RTC_DCHECK_RUN_ON(&packet_sequence_checker_);
  return absl::c_linear_search(ssrcs_, ssrc);
}

uint32_t RTCPReceiver::RegisteredSsrcs::media_ssrc() const {
  RTC_DCHECK_RUN_ON(&packet_sequence_checker_);
  return ssrcs_[kMediaSsrcIndex];
}

void RTCPReceiver::RegisteredSsrcs::set_media_ssrc(uint32_t ssrc) {
  RTC_DCHECK_RUN_ON(&packet_sequence_checker_);
  ssrcs_[kMediaSsrcIndex] = ssrc;
}

struct RTCPReceiver::PacketInformation {
  uint32_t packet_type_flags = 0;  // RTCPPacketTypeFlags bit field.

  uint32_t remote_ssrc = 0;
  std::vector<uint16_t> nack_sequence_numbers;
  std::vector<ReportBlockData> report_block_datas;
  absl::optional<TimeDelta> rtt;
  uint32_t receiver_estimated_max_bitrate_bps = 0;
  std::unique_ptr<rtcp::TransportFeedback> transport_feedback;
  absl::optional<VideoBitrateAllocation> target_bitrate_allocation;
  absl::optional<NetworkStateEstimate> network_state_estimate;
  std::unique_ptr<rtcp::LossNotification> loss_notification;
};

RTCPReceiver::RTCPReceiver(const RtpRtcpInterface::Configuration& config,
                           ModuleRtpRtcpImpl2* owner)
    : clock_(config.clock),
      receiver_only_(config.receiver_only),
      rtp_rtcp_(owner),
      registered_ssrcs_(false, config),
      network_link_rtcp_observer_(config.network_link_rtcp_observer),
      rtcp_intra_frame_observer_(config.intra_frame_callback),
      rtcp_loss_notification_observer_(config.rtcp_loss_notification_observer),
      network_state_estimate_observer_(config.network_state_estimate_observer),
      bitrate_allocation_observer_(config.bitrate_allocation_observer),
      report_interval_(config.rtcp_report_interval_ms > 0
                           ? TimeDelta::Millis(config.rtcp_report_interval_ms)
                           : (config.audio ? kDefaultAudioReportInterval
                                           : kDefaultVideoReportInterval)),
      // TODO(bugs.webrtc.org/10774): Remove fallback.
      remote_ssrc_(0),
      xr_rrtr_status_(config.non_sender_rtt_measurement),
      oldest_tmmbr_info_(Timestamp::Zero()),
      cname_callback_(config.rtcp_cname_callback),
      report_block_data_observer_(config.report_block_data_observer),
      packet_type_counter_observer_(config.rtcp_packet_type_counter_observer),
      num_skipped_packets_(0),
      last_skipped_packets_warning_(clock_->CurrentTime()) {
  RTC_DCHECK(owner);
}

RTCPReceiver::RTCPReceiver(const RtpRtcpInterface::Configuration& config,
                           ModuleRtpRtcp* owner)
    : clock_(config.clock),
      receiver_only_(config.receiver_only),
      rtp_rtcp_(owner),
      registered_ssrcs_(true, config),
      network_link_rtcp_observer_(config.network_link_rtcp_observer),
      rtcp_intra_frame_observer_(config.intra_frame_callback),
      rtcp_loss_notification_observer_(config.rtcp_loss_notification_observer),
      network_state_estimate_observer_(config.network_state_estimate_observer),
      bitrate_allocation_observer_(config.bitrate_allocation_observer),
      report_interval_(config.rtcp_report_interval_ms > 0
                           ? TimeDelta::Millis(config.rtcp_report_interval_ms)
                           : (config.audio ? kDefaultAudioReportInterval
                                           : kDefaultVideoReportInterval)),
      // TODO(bugs.webrtc.org/10774): Remove fallback.
      remote_ssrc_(0),
      xr_rrtr_status_(config.non_sender_rtt_measurement),
      oldest_tmmbr_info_(Timestamp::Zero()),
      cname_callback_(config.rtcp_cname_callback),
      report_block_data_observer_(config.report_block_data_observer),
      packet_type_counter_observer_(config.rtcp_packet_type_counter_observer),
      num_skipped_packets_(0),
      last_skipped_packets_warning_(clock_->CurrentTime()) {
  RTC_DCHECK(owner);
  // Dear reader - if you're here because of this log statement and are
  // wondering what this is about, chances are that you are using an instance
  // of RTCPReceiver without using the webrtc APIs. This creates a bit of a
  // problem for WebRTC because this class is a part of an internal
  // implementation that is constantly changing and being improved.
  // The intention of this log statement is to give a heads up that changes
  // are coming and encourage you to use the public APIs or be prepared that
  // things might break down the line as more changes land. A thing you could
  // try out for now is to replace the `CustomSequenceChecker` in the header
  // with a regular `SequenceChecker` and see if that triggers an
  // error in your code. If it does, chances are you have your own threading
  // model that is not the same as WebRTC internally has.
  RTC_LOG(LS_INFO) << "************** !!!DEPRECATION WARNING!! **************";
}

RTCPReceiver::~RTCPReceiver() {}

void RTCPReceiver::IncomingPacket(rtc::ArrayView<const uint8_t> packet) {
  if (packet.empty()) {
    RTC_LOG(LS_WARNING) << "Incoming empty RTCP packet";
    return;
  }

  PacketInformation packet_information;
  if (!ParseCompoundPacket(packet, &packet_information))
    return;
  TriggerCallbacksFromRtcpPacket(packet_information);
}

// This method is only used by test and legacy code, so we should be able to
// remove it soon.
int64_t RTCPReceiver::LastReceivedReportBlockMs() const {
  MutexLock lock(&rtcp_receiver_lock_);
  return last_received_rb_.IsFinite() ? last_received_rb_.ms() : 0;
}

void RTCPReceiver::SetRemoteSSRC(uint32_t ssrc) {
  MutexLock lock(&rtcp_receiver_lock_);
  // New SSRC reset old reports.
  remote_sender_.last_arrival_timestamp.Reset();
  remote_ssrc_ = ssrc;
}

void RTCPReceiver::set_local_media_ssrc(uint32_t ssrc) {
  registered_ssrcs_.set_media_ssrc(ssrc);
}

uint32_t RTCPReceiver::local_media_ssrc() const {
  return registered_ssrcs_.media_ssrc();
}

uint32_t RTCPReceiver::RemoteSSRC() const {
  MutexLock lock(&rtcp_receiver_lock_);
  return remote_ssrc_;
}

void RTCPReceiver::RttStats::AddRtt(TimeDelta rtt) {
  last_rtt_ = rtt;
  sum_rtt_ += rtt;
  ++num_rtts_;
}

absl::optional<TimeDelta> RTCPReceiver::AverageRtt() const {
  MutexLock lock(&rtcp_receiver_lock_);
  auto it = rtts_.find(remote_ssrc_);
  if (it == rtts_.end()) {
    return absl::nullopt;
  }
  return it->second.average_rtt();
}

absl::optional<TimeDelta> RTCPReceiver::LastRtt() const {
  MutexLock lock(&rtcp_receiver_lock_);
  auto it = rtts_.find(remote_ssrc_);
  if (it == rtts_.end()) {
    return absl::nullopt;
  }
  return it->second.last_rtt();
}

RTCPReceiver::NonSenderRttStats RTCPReceiver::GetNonSenderRTT() const {
  MutexLock lock(&rtcp_receiver_lock_);
  auto it = non_sender_rtts_.find(remote_ssrc_);
  if (it == non_sender_rtts_.end()) {
    return {};
  }
  return it->second;
}

void RTCPReceiver::SetNonSenderRttMeasurement(bool enabled) {
  MutexLock lock(&rtcp_receiver_lock_);
  xr_rrtr_status_ = enabled;
}

absl::optional<TimeDelta> RTCPReceiver::GetAndResetXrRrRtt() {
  MutexLock lock(&rtcp_receiver_lock_);
  absl::optional<TimeDelta> rtt = xr_rr_rtt_;
  xr_rr_rtt_ = absl::nullopt;
  return rtt;
}

// Called regularly (1/sec) on the worker thread to do rtt  calculations.
absl::optional<TimeDelta> RTCPReceiver::OnPeriodicRttUpdate(
    Timestamp newer_than,
    bool sending) {
  // Running on the worker thread (same as construction thread).
  absl::optional<TimeDelta> rtt;

  if (sending) {
    // Check if we've received a report block within the last kRttUpdateInterval
    // amount of time.
    MutexLock lock(&rtcp_receiver_lock_);
    if (last_received_rb_.IsInfinite() || last_received_rb_ > newer_than) {
      TimeDelta max_rtt = TimeDelta::MinusInfinity();
      for (const auto& rtt_stats : rtts_) {
        if (rtt_stats.second.last_rtt() > max_rtt) {
          max_rtt = rtt_stats.second.last_rtt();
        }
      }
      if (max_rtt.IsFinite()) {
        rtt = max_rtt;
      }
    }

    // Check for expired timers and if so, log and reset.
    Timestamp now = clock_->CurrentTime();
    if (RtcpRrTimeoutLocked(now)) {
      RTC_LOG_F(LS_WARNING) << "Timeout: No RTCP RR received.";
    } else if (RtcpRrSequenceNumberTimeoutLocked(now)) {
      RTC_LOG_F(LS_WARNING) << "Timeout: No increase in RTCP RR extended "
                               "highest sequence number.";
    }
  } else {
    // Report rtt from receiver.
    rtt = GetAndResetXrRrRtt();
  }

  return rtt;
}

absl::optional<RtpRtcpInterface::SenderReportStats>
RTCPReceiver::GetSenderReportStats() const {
  MutexLock lock(&rtcp_receiver_lock_);
  if (!remote_sender_.last_arrival_timestamp.Valid()) {
    return absl::nullopt;
  }

  return remote_sender_;
}

std::vector<rtcp::ReceiveTimeInfo>
RTCPReceiver::ConsumeReceivedXrReferenceTimeInfo() {
  MutexLock lock(&rtcp_receiver_lock_);

  const size_t last_xr_rtis_size = std::min(
      received_rrtrs_.size(), rtcp::ExtendedReports::kMaxNumberOfDlrrItems);
  std::vector<rtcp::ReceiveTimeInfo> last_xr_rtis;
  last_xr_rtis.reserve(last_xr_rtis_size);

  const uint32_t now_ntp = CompactNtp(clock_->CurrentNtpTime());

  for (size_t i = 0; i < last_xr_rtis_size; ++i) {
    RrtrInformation& rrtr = received_rrtrs_.front();
    last_xr_rtis.emplace_back(rrtr.ssrc, rrtr.received_remote_mid_ntp_time,
                              now_ntp - rrtr.local_receive_mid_ntp_time);
    received_rrtrs_ssrc_it_.erase(rrtr.ssrc);
    received_rrtrs_.pop_front();
  }

  return last_xr_rtis;
}

std::vector<ReportBlockData> RTCPReceiver::GetLatestReportBlockData() const {
  std::vector<ReportBlockData> result;
  MutexLock lock(&rtcp_receiver_lock_);
  for (const auto& report : received_report_blocks_) {
    result.push_back(report.second);
  }
  return result;
}

bool RTCPReceiver::ParseCompoundPacket(rtc::ArrayView<const uint8_t> packet,
                                       PacketInformation* packet_information) {
  MutexLock lock(&rtcp_receiver_lock_);

  CommonHeader rtcp_block;
  // If a sender report is received but no DLRR, we need to reset the
  // roundTripTime stat according to the standard, see
  // https://www.w3.org/TR/webrtc-stats/#dom-rtcremoteoutboundrtpstreamstats-roundtriptime
  struct RtcpReceivedBlock {
    bool sender_report = false;
    bool dlrr = false;
  };
  // For each remote SSRC we store if we've received a sender report or a DLRR
  // block.
  flat_map<uint32_t, RtcpReceivedBlock> received_blocks;
  bool valid = true;
  for (const uint8_t* next_block = packet.begin();
       valid && next_block != packet.end();
       next_block = rtcp_block.NextPacket()) {
    ptrdiff_t remaining_blocks_size = packet.end() - next_block;
    RTC_DCHECK_GT(remaining_blocks_size, 0);
    if (!rtcp_block.Parse(next_block, remaining_blocks_size)) {
      valid = false;
      break;
    }

    switch (rtcp_block.type()) {
      case rtcp::SenderReport::kPacketType:
        valid = HandleSenderReport(rtcp_block, packet_information);
        received_blocks[packet_information->remote_ssrc].sender_report = true;
        break;
      case rtcp::ReceiverReport::kPacketType:
        valid = HandleReceiverReport(rtcp_block, packet_information);
        break;
      case rtcp::Sdes::kPacketType:
        valid = HandleSdes(rtcp_block, packet_information);
        break;
      case rtcp::ExtendedReports::kPacketType: {
        bool contains_dlrr = false;
        uint32_t ssrc = 0;
        valid = HandleXr(rtcp_block, packet_information, contains_dlrr, ssrc);
        if (contains_dlrr) {
          received_blocks[ssrc].dlrr = true;
        }
        break;
      }
      case rtcp::Bye::kPacketType:
        valid = HandleBye(rtcp_block);
        break;
      case rtcp::App::kPacketType:
        valid = HandleApp(rtcp_block, packet_information);
        break;
      case rtcp::Rtpfb::kPacketType:
        switch (rtcp_block.fmt()) {
          case rtcp::Nack::kFeedbackMessageType:
            valid = HandleNack(rtcp_block, packet_information);
            break;
          case rtcp::Tmmbr::kFeedbackMessageType:
            valid = HandleTmmbr(rtcp_block, packet_information);
            break;
          case rtcp::Tmmbn::kFeedbackMessageType:
            valid = HandleTmmbn(rtcp_block, packet_information);
            break;
          case rtcp::RapidResyncRequest::kFeedbackMessageType:
            valid = HandleSrReq(rtcp_block, packet_information);
            break;
          case rtcp::TransportFeedback::kFeedbackMessageType:
            HandleTransportFeedback(rtcp_block, packet_information);
            break;
          default:
            ++num_skipped_packets_;
            break;
        }
        break;
      case rtcp::Psfb::kPacketType:
        switch (rtcp_block.fmt()) {
          case rtcp::Pli::kFeedbackMessageType:
            valid = HandlePli(rtcp_block, packet_information);
            break;
          case rtcp::Fir::kFeedbackMessageType:
            valid = HandleFir(rtcp_block, packet_information);
            break;
          case rtcp::Psfb::kAfbMessageType:
            HandlePsfbApp(rtcp_block, packet_information);
            break;
          default:
            ++num_skipped_packets_;
            break;
        }
        break;
      default:
        ++num_skipped_packets_;
        break;
    }
  }

  if (num_skipped_packets_ > 0) {
    const Timestamp now = clock_->CurrentTime();
    if (now - last_skipped_packets_warning_ >= kMaxWarningLogInterval) {
      last_skipped_packets_warning_ = now;
      RTC_LOG(LS_WARNING)
          << num_skipped_packets_
          << " RTCP blocks were skipped due to being malformed or of "
             "unrecognized/unsupported type, during the past "
          << kMaxWarningLogInterval << " period.";
    }
  }

  if (!valid) {
    ++num_skipped_packets_;
    return false;
  }

  for (const auto& rb : received_blocks) {
    if (rb.second.sender_report && !rb.second.dlrr) {
      auto rtt_stats = non_sender_rtts_.find(rb.first);
      if (rtt_stats != non_sender_rtts_.end()) {
        rtt_stats->second.Invalidate();
      }
    }
  }

  if (packet_type_counter_observer_) {
    packet_type_counter_observer_->RtcpPacketTypesCounterUpdated(
        local_media_ssrc(), packet_type_counter_);
  }

  return true;
}

bool RTCPReceiver::HandleSenderReport(const CommonHeader& rtcp_block,
                                      PacketInformation* packet_information) {
  rtcp::SenderReport sender_report;
  if (!sender_report.Parse(rtcp_block)) {
    return false;
  }

  const uint32_t remote_ssrc = sender_report.sender_ssrc();

  packet_information->remote_ssrc = remote_ssrc;

  UpdateTmmbrRemoteIsAlive(remote_ssrc);

  // Have I received RTP packets from this party?
  if (remote_ssrc_ == remote_ssrc) {
    // Only signal that we have received a SR when we accept one.
    packet_information->packet_type_flags |= kRtcpSr;

    remote_sender_.last_remote_timestamp = sender_report.ntp();
    remote_sender_.last_remote_rtp_timestamp = sender_report.rtp_timestamp();
    remote_sender_.last_arrival_timestamp = clock_->CurrentNtpTime();
    remote_sender_.packets_sent = sender_report.sender_packet_count();
    remote_sender_.bytes_sent = sender_report.sender_octet_count();
    remote_sender_.reports_count++;
  } else {
    // We will only store the send report from one source, but
    // we will store all the receive blocks.
    packet_information->packet_type_flags |= kRtcpRr;
  }

  for (const rtcp::ReportBlock& report_block : sender_report.report_blocks()) {
    HandleReportBlock(report_block, packet_information, remote_ssrc);
  }

  return true;
}

bool RTCPReceiver::HandleReceiverReport(const CommonHeader& rtcp_block,
                                        PacketInformation* packet_information) {
  rtcp::ReceiverReport receiver_report;
  if (!receiver_report.Parse(rtcp_block)) {
    return false;
  }

  const uint32_t remote_ssrc = receiver_report.sender_ssrc();

  packet_information->remote_ssrc = remote_ssrc;

  UpdateTmmbrRemoteIsAlive(remote_ssrc);

  packet_information->packet_type_flags |= kRtcpRr;

  for (const ReportBlock& report_block : receiver_report.report_blocks()) {
    HandleReportBlock(report_block, packet_information, remote_ssrc);
  }

  return true;
}

void RTCPReceiver::HandleReportBlock(const ReportBlock& report_block,
                                     PacketInformation* packet_information,
                                     uint32_t remote_ssrc) {
  // This will be called once per report block in the RTCP packet.
  // We filter out all report blocks that are not for us.
  // Each packet has max 31 RR blocks.
  //
  // We can calc RTT if we send a send report and get a report block back.

  // `report_block.source_ssrc()` is the SSRC identifier of the source to
  // which the information in this reception report block pertains.

  // Filter out all report blocks that are not for us.
  if (!registered_ssrcs_.contains(report_block.source_ssrc()))
    return;

  Timestamp now = clock_->CurrentTime();
  last_received_rb_ = now;

  ReportBlockData* report_block_data =
      &received_report_blocks_[report_block.source_ssrc()];
  if (report_block.extended_high_seq_num() >
      report_block_data->extended_highest_sequence_number()) {
    // We have successfully delivered new RTP packets to the remote side after
    // the last RR was sent from the remote side.
    last_increased_sequence_number_ = last_received_rb_;
  }
  NtpTime now_ntp = clock_->ConvertTimestampToNtpTime(now);
  // Number of seconds since 1900 January 1 00:00 GMT (see
  // https://tools.ietf.org/html/rfc868).
  report_block_data->SetReportBlock(
      remote_ssrc, report_block,
      Timestamp::Millis(now_ntp.ToMs() - rtc::kNtpJan1970Millisecs));

  uint32_t send_time_ntp = report_block.last_sr();
  // RFC3550, section 6.4.1, LSR field discription states:
  // If no SR has been received yet, the field is set to zero.
  // Receiver rtp_rtcp module is not expected to calculate rtt using
  // Sender Reports even if it accidentally can.
  if (send_time_ntp != 0) {
    uint32_t delay_ntp = report_block.delay_since_last_sr();
    // Local NTP time.
    uint32_t receive_time_ntp = CompactNtp(now_ntp);

    // RTT in 1/(2^16) seconds.
    uint32_t rtt_ntp = receive_time_ntp - delay_ntp - send_time_ntp;
    // Convert to 1/1000 seconds (milliseconds).
    TimeDelta rtt = CompactNtpRttToTimeDelta(rtt_ntp);
    report_block_data->AddRoundTripTimeSample(rtt);
    if (report_block.source_ssrc() == local_media_ssrc()) {
      rtts_[remote_ssrc].AddRtt(rtt);
    }

    packet_information->rtt = rtt;
  }

  packet_information->report_block_datas.push_back(*report_block_data);
}

RTCPReceiver::TmmbrInformation* RTCPReceiver::FindOrCreateTmmbrInfo(
    uint32_t remote_ssrc) {
  // Create or find receive information.
  TmmbrInformation* tmmbr_info = &tmmbr_infos_[remote_ssrc];
  // Update that this remote is alive.
  tmmbr_info->last_time_received = clock_->CurrentTime();
  return tmmbr_info;
}

void RTCPReceiver::UpdateTmmbrRemoteIsAlive(uint32_t remote_ssrc) {
  auto tmmbr_it = tmmbr_infos_.find(remote_ssrc);
  if (tmmbr_it != tmmbr_infos_.end())
    tmmbr_it->second.last_time_received = clock_->CurrentTime();
}

RTCPReceiver::TmmbrInformation* RTCPReceiver::GetTmmbrInformation(
    uint32_t remote_ssrc) {
  auto it = tmmbr_infos_.find(remote_ssrc);
  if (it == tmmbr_infos_.end())
    return nullptr;
  return &it->second;
}

// These two methods (RtcpRrTimeout and RtcpRrSequenceNumberTimeout) only exist
// for tests and legacy code (rtp_rtcp_impl.cc). We should be able to to delete
// the methods and require that access to the locked variables only happens on
// the worker thread and thus no locking is needed.
bool RTCPReceiver::RtcpRrTimeout() {
  MutexLock lock(&rtcp_receiver_lock_);
  return RtcpRrTimeoutLocked(clock_->CurrentTime());
}

bool RTCPReceiver::RtcpRrSequenceNumberTimeout() {
  MutexLock lock(&rtcp_receiver_lock_);
  return RtcpRrSequenceNumberTimeoutLocked(clock_->CurrentTime());
}

bool RTCPReceiver::UpdateTmmbrTimers() {
  MutexLock lock(&rtcp_receiver_lock_);

  Timestamp timeout = clock_->CurrentTime() - kTmmbrTimeoutInterval;

  if (oldest_tmmbr_info_ >= timeout)
    return false;

  bool update_bounding_set = false;
  oldest_tmmbr_info_ = Timestamp::MinusInfinity();
  for (auto tmmbr_it = tmmbr_infos_.begin(); tmmbr_it != tmmbr_infos_.end();) {
    TmmbrInformation* tmmbr_info = &tmmbr_it->second;
    if (tmmbr_info->last_time_received > Timestamp::Zero()) {
      if (tmmbr_info->last_time_received < timeout) {
        // No rtcp packet for the last 5 regular intervals, reset limitations.
        tmmbr_info->tmmbr.clear();
        // Prevent that we call this over and over again.
        tmmbr_info->last_time_received = Timestamp::Zero();
        // Send new TMMBN to all channels using the default codec.
        update_bounding_set = true;
      } else if (oldest_tmmbr_info_ == Timestamp::MinusInfinity() ||
                 tmmbr_info->last_time_received < oldest_tmmbr_info_) {
        oldest_tmmbr_info_ = tmmbr_info->last_time_received;
      }
      ++tmmbr_it;
    } else if (tmmbr_info->ready_for_delete) {
      // When we dont have a `last_time_received` and the object is marked
      // ready_for_delete it's removed from the map.
      tmmbr_it = tmmbr_infos_.erase(tmmbr_it);
    } else {
      ++tmmbr_it;
    }
  }
  return update_bounding_set;
}

std::vector<rtcp::TmmbItem> RTCPReceiver::BoundingSet(bool* tmmbr_owner) {
  MutexLock lock(&rtcp_receiver_lock_);
  TmmbrInformation* tmmbr_info = GetTmmbrInformation(remote_ssrc_);
  if (!tmmbr_info)
    return std::vector<rtcp::TmmbItem>();

  *tmmbr_owner = TMMBRHelp::IsOwner(tmmbr_info->tmmbn, local_media_ssrc());
  return tmmbr_info->tmmbn;
}

bool RTCPReceiver::HandleSdes(const CommonHeader& rtcp_block,
                              PacketInformation* packet_information) {
  rtcp::Sdes sdes;
  if (!sdes.Parse(rtcp_block)) {
    return false;
  }

  for (const rtcp::Sdes::Chunk& chunk : sdes.chunks()) {
    if (cname_callback_)
      cname_callback_->OnCname(chunk.ssrc, chunk.cname);
  }
  packet_information->packet_type_flags |= kRtcpSdes;

  return true;
}

bool RTCPReceiver::HandleNack(const CommonHeader& rtcp_block,
                              PacketInformation* packet_information) {
  rtcp::Nack nack;
  if (!nack.Parse(rtcp_block)) {
    return false;
  }

  if (receiver_only_ || local_media_ssrc() != nack.media_ssrc())  // Not to us.
    return true;

  packet_information->nack_sequence_numbers.insert(
      packet_information->nack_sequence_numbers.end(),
      nack.packet_ids().begin(), nack.packet_ids().end());
  for (uint16_t packet_id : nack.packet_ids())
    nack_stats_.ReportRequest(packet_id);

  if (!nack.packet_ids().empty()) {
    packet_information->packet_type_flags |= kRtcpNack;
    ++packet_type_counter_.nack_packets;
    packet_type_counter_.nack_requests = nack_stats_.requests();
    packet_type_counter_.unique_nack_requests = nack_stats_.unique_requests();
  }

  return true;
}

bool RTCPReceiver::HandleApp(const rtcp::CommonHeader& rtcp_block,
                             PacketInformation* packet_information) {
  rtcp::App app;
  if (!app.Parse(rtcp_block)) {
    return false;
  }
  if (app.name() == rtcp::RemoteEstimate::kName &&
      app.sub_type() == rtcp::RemoteEstimate::kSubType) {
    rtcp::RemoteEstimate estimate(std::move(app));
    if (estimate.ParseData()) {
      packet_information->network_state_estimate = estimate.estimate();
    }
    // RemoteEstimate is not a standard RTCP message. Failing to parse it
    // doesn't indicates RTCP packet is invalid. It may indicate sender happens
    // to use the same id for a different message. Thus don't return false.
  }

  return true;
}

bool RTCPReceiver::HandleBye(const CommonHeader& rtcp_block) {
  rtcp::Bye bye;
  if (!bye.Parse(rtcp_block)) {
    return false;
  }

  // Clear our lists.
  rtts_.erase(bye.sender_ssrc());
  EraseIf(received_report_blocks_, [&](const auto& elem) {
    return elem.second.sender_ssrc() == bye.sender_ssrc();
  });

  TmmbrInformation* tmmbr_info = GetTmmbrInformation(bye.sender_ssrc());
  if (tmmbr_info)
    tmmbr_info->ready_for_delete = true;

  last_fir_.erase(bye.sender_ssrc());
  auto it = received_rrtrs_ssrc_it_.find(bye.sender_ssrc());
  if (it != received_rrtrs_ssrc_it_.end()) {
    received_rrtrs_.erase(it->second);
    received_rrtrs_ssrc_it_.erase(it);
  }
  xr_rr_rtt_ = absl::nullopt;
  return true;
}

bool RTCPReceiver::HandleXr(const CommonHeader& rtcp_block,
                            PacketInformation* packet_information,
                            bool& contains_dlrr,
                            uint32_t& ssrc) {
  rtcp::ExtendedReports xr;
  if (!xr.Parse(rtcp_block)) {
    return false;
  }
  ssrc = xr.sender_ssrc();
  contains_dlrr = !xr.dlrr().sub_blocks().empty();

  if (xr.rrtr())
    HandleXrReceiveReferenceTime(xr.sender_ssrc(), *xr.rrtr());

  for (const rtcp::ReceiveTimeInfo& time_info : xr.dlrr().sub_blocks())
    HandleXrDlrrReportBlock(xr.sender_ssrc(), time_info);

  if (xr.target_bitrate()) {
    HandleXrTargetBitrate(xr.sender_ssrc(), *xr.target_bitrate(),
                          packet_information);
  }
  return true;
}

void RTCPReceiver::HandleXrReceiveReferenceTime(uint32_t sender_ssrc,
                                                const rtcp::Rrtr& rrtr) {
  uint32_t received_remote_mid_ntp_time = CompactNtp(rrtr.ntp());
  uint32_t local_receive_mid_ntp_time = CompactNtp(clock_->CurrentNtpTime());

  auto it = received_rrtrs_ssrc_it_.find(sender_ssrc);
  if (it != received_rrtrs_ssrc_it_.end()) {
    it->second->received_remote_mid_ntp_time = received_remote_mid_ntp_time;
    it->second->local_receive_mid_ntp_time = local_receive_mid_ntp_time;
  } else {
    if (received_rrtrs_.size() < kMaxNumberOfStoredRrtrs) {
      received_rrtrs_.emplace_back(sender_ssrc, received_remote_mid_ntp_time,
                                   local_receive_mid_ntp_time);
      received_rrtrs_ssrc_it_[sender_ssrc] = std::prev(received_rrtrs_.end());
    } else {
      RTC_LOG(LS_WARNING) << "Discarding received RRTR for ssrc " << sender_ssrc
                          << ", reached maximum number of stored RRTRs.";
    }
  }
}

void RTCPReceiver::HandleXrDlrrReportBlock(uint32_t sender_ssrc,
                                           const rtcp::ReceiveTimeInfo& rti) {
  if (!registered_ssrcs_.contains(rti.ssrc))  // Not to us.
    return;

  // Caller should explicitly enable rtt calculation using extended reports.
  if (!xr_rrtr_status_)
    return;

  // The send_time and delay_rr fields are in units of 1/2^16 sec.
  uint32_t send_time_ntp = rti.last_rr;
  // RFC3611, section 4.5, LRR field discription states:
  // If no such block has been received, the field is set to zero.
  if (send_time_ntp == 0) {
    auto rtt_stats = non_sender_rtts_.find(sender_ssrc);
    if (rtt_stats != non_sender_rtts_.end()) {
      rtt_stats->second.Invalidate();
    }
    return;
  }

  uint32_t delay_ntp = rti.delay_since_last_rr;
  uint32_t now_ntp = CompactNtp(clock_->CurrentNtpTime());

  uint32_t rtt_ntp = now_ntp - delay_ntp - send_time_ntp;
  TimeDelta rtt = CompactNtpRttToTimeDelta(rtt_ntp);
  xr_rr_rtt_ = rtt;

  non_sender_rtts_[sender_ssrc].Update(rtt);
}

void RTCPReceiver::HandleXrTargetBitrate(
    uint32_t ssrc,
    const rtcp::TargetBitrate& target_bitrate,
    PacketInformation* packet_information) {
  if (ssrc != remote_ssrc_) {
    return;  // Not for us.
  }

  VideoBitrateAllocation bitrate_allocation;
  for (const auto& item : target_bitrate.GetTargetBitrates()) {
    if (item.spatial_layer >= kMaxSpatialLayers ||
        item.temporal_layer >= kMaxTemporalStreams) {
      RTC_LOG(LS_WARNING)
          << "Invalid layer in XR target bitrate pack: spatial index "
          << item.spatial_layer << ", temporal index " << item.temporal_layer
          << ", dropping.";
    } else {
      bitrate_allocation.SetBitrate(item.spatial_layer, item.temporal_layer,
                                    item.target_bitrate_kbps * 1000);
    }
  }
  packet_information->target_bitrate_allocation.emplace(bitrate_allocation);
}

bool RTCPReceiver::HandlePli(const CommonHeader& rtcp_block,
                             PacketInformation* packet_information) {
  rtcp::Pli pli;
  if (!pli.Parse(rtcp_block)) {
    return false;
  }

  if (local_media_ssrc() == pli.media_ssrc()) {
    ++packet_type_counter_.pli_packets;
    // Received a signal that we need to send a new key frame.
    packet_information->packet_type_flags |= kRtcpPli;
  }
  return true;
}

bool RTCPReceiver::HandleTmmbr(const CommonHeader& rtcp_block,
                               PacketInformation* packet_information) {
  rtcp::Tmmbr tmmbr;
  if (!tmmbr.Parse(rtcp_block)) {
    return false;
  }

  uint32_t sender_ssrc = tmmbr.sender_ssrc();
  if (tmmbr.media_ssrc()) {
    // media_ssrc() SHOULD be 0 if same as SenderSSRC.
    // In relay mode this is a valid number.
    sender_ssrc = tmmbr.media_ssrc();
  }

  for (const rtcp::TmmbItem& request : tmmbr.requests()) {
    if (local_media_ssrc() != request.ssrc() || request.bitrate_bps() == 0)
      continue;

    TmmbrInformation* tmmbr_info = FindOrCreateTmmbrInfo(tmmbr.sender_ssrc());
    auto* entry = &tmmbr_info->tmmbr[sender_ssrc];
    entry->tmmbr_item = rtcp::TmmbItem(sender_ssrc, request.bitrate_bps(),
                                       request.packet_overhead());
    // FindOrCreateTmmbrInfo always sets `last_time_received` to
    // `clock_->CurrentTime()`.
    entry->last_updated = tmmbr_info->last_time_received;

    packet_information->packet_type_flags |= kRtcpTmmbr;
    break;
  }
  return true;
}

bool RTCPReceiver::HandleTmmbn(const CommonHeader& rtcp_block,
                               PacketInformation* packet_information) {
  rtcp::Tmmbn tmmbn;
  if (!tmmbn.Parse(rtcp_block)) {
    return false;
  }

  TmmbrInformation* tmmbr_info = FindOrCreateTmmbrInfo(tmmbn.sender_ssrc());

  packet_information->packet_type_flags |= kRtcpTmmbn;

  tmmbr_info->tmmbn = tmmbn.items();
  return true;
}

bool RTCPReceiver::HandleSrReq(const CommonHeader& rtcp_block,
                               PacketInformation* packet_information) {
  rtcp::RapidResyncRequest sr_req;
  if (!sr_req.Parse(rtcp_block)) {
    return false;
  }

  packet_information->packet_type_flags |= kRtcpSrReq;
  return true;
}

void RTCPReceiver::HandlePsfbApp(const CommonHeader& rtcp_block,
                                 PacketInformation* packet_information) {
  {
    rtcp::Remb remb;
    if (remb.Parse(rtcp_block)) {
      packet_information->packet_type_flags |= kRtcpRemb;
      packet_information->receiver_estimated_max_bitrate_bps =
          remb.bitrate_bps();
      return;
    }
  }

  {
    auto loss_notification = std::make_unique<rtcp::LossNotification>();
    if (loss_notification->Parse(rtcp_block)) {
      packet_information->packet_type_flags |= kRtcpLossNotification;
      packet_information->loss_notification = std::move(loss_notification);
      return;
    }
  }

  RTC_LOG(LS_WARNING) << "Unknown PSFB-APP packet.";
  ++num_skipped_packets_;
  // Application layer feedback message doesn't have a standard format.
  // Failing to parse one of known messages doesn't indicate an invalid RTCP.
}

bool RTCPReceiver::HandleFir(const CommonHeader& rtcp_block,
                             PacketInformation* packet_information) {
  rtcp::Fir fir;
  if (!fir.Parse(rtcp_block)) {
    return false;
  }

  if (fir.requests().empty())
    return true;

  const Timestamp now = clock_->CurrentTime();
  for (const rtcp::Fir::Request& fir_request : fir.requests()) {
    // Is it our sender that is requested to generate a new keyframe.
    if (local_media_ssrc() != fir_request.ssrc)
      continue;

    ++packet_type_counter_.fir_packets;

    auto [it, inserted] =
        last_fir_.try_emplace(fir.sender_ssrc(), now, fir_request.seq_nr);
    if (!inserted) {  // There was already an entry.
      LastFirStatus* last_fir = &it->second;

      // Check if we have reported this FIRSequenceNumber before.
      if (fir_request.seq_nr == last_fir->sequence_number)
        continue;

      // Sanity: don't go crazy with the callbacks.
      if (now - last_fir->request < kRtcpMinFrameLength)
        continue;

      last_fir->request = now;
      last_fir->sequence_number = fir_request.seq_nr;
    }
    // Received signal that we need to send a new key frame.
    packet_information->packet_type_flags |= kRtcpFir;
  }
  return true;
}

void RTCPReceiver::HandleTransportFeedback(
    const CommonHeader& rtcp_block,
    PacketInformation* packet_information) {
  std::unique_ptr<rtcp::TransportFeedback> transport_feedback(
      new rtcp::TransportFeedback());
  if (!transport_feedback->Parse(rtcp_block)) {
    ++num_skipped_packets_;
    // Application layer feedback message doesn't have a standard format.
    // Failing to parse it as transport feedback messages doesn't indicate an
    // invalid RTCP.
    return;
  }
  uint32_t media_source_ssrc = transport_feedback->media_ssrc();
  if (media_source_ssrc == local_media_ssrc() ||
      registered_ssrcs_.contains(media_source_ssrc)) {
    packet_information->packet_type_flags |= kRtcpTransportFeedback;
    packet_information->transport_feedback = std::move(transport_feedback);
  }
}

void RTCPReceiver::NotifyTmmbrUpdated() {
  // Find bounding set.
  std::vector<rtcp::TmmbItem> bounding =
      TMMBRHelp::FindBoundingSet(TmmbrReceived());

  if (!bounding.empty() && network_link_rtcp_observer_) {
    // We have a new bandwidth estimate on this channel.
    uint64_t bitrate_bps = TMMBRHelp::CalcMinBitrateBps(bounding);
    if (bitrate_bps < std::numeric_limits<int64_t>::max()) {
      network_link_rtcp_observer_->OnReceiverEstimatedMaxBitrate(
          clock_->CurrentTime(), DataRate::BitsPerSec(bitrate_bps));
    }
  }

  // Send tmmbn to inform remote clients about the new bandwidth.
  rtp_rtcp_->SetTmmbn(std::move(bounding));
}

// Holding no Critical section.
void RTCPReceiver::TriggerCallbacksFromRtcpPacket(
    const PacketInformation& packet_information) {
  // Process TMMBR and REMB first to avoid multiple callbacks
  // to OnNetworkChanged.
  if (packet_information.packet_type_flags & kRtcpTmmbr) {
    // Might trigger a OnReceivedBandwidthEstimateUpdate.
    NotifyTmmbrUpdated();
  }

  if (!receiver_only_ && (packet_information.packet_type_flags & kRtcpSrReq)) {
    rtp_rtcp_->OnRequestSendReport();
  }
  if (!receiver_only_ && (packet_information.packet_type_flags & kRtcpNack)) {
    if (!packet_information.nack_sequence_numbers.empty()) {
      RTC_LOG(LS_VERBOSE) << "Incoming NACK length: "
                          << packet_information.nack_sequence_numbers.size();
      rtp_rtcp_->OnReceivedNack(packet_information.nack_sequence_numbers);
    }
  }

  // We need feedback that we have received a report block(s) so that we
  // can generate a new packet in a conference relay scenario, one received
  // report can generate several RTCP packets, based on number relayed/mixed
  // a send report block should go out to all receivers.
  if (rtcp_intra_frame_observer_) {
    RTC_DCHECK(!receiver_only_);
    if ((packet_information.packet_type_flags & kRtcpPli) ||
        (packet_information.packet_type_flags & kRtcpFir)) {
      if (packet_information.packet_type_flags & kRtcpPli) {
        RTC_LOG(LS_VERBOSE)
            << "Incoming PLI from SSRC " << packet_information.remote_ssrc;
      } else {
        RTC_LOG(LS_VERBOSE)
            << "Incoming FIR from SSRC " << packet_information.remote_ssrc;
      }
      rtcp_intra_frame_observer_->OnReceivedIntraFrameRequest(
          local_media_ssrc());
    }
  }
  if (rtcp_loss_notification_observer_ &&
      (packet_information.packet_type_flags & kRtcpLossNotification)) {
    rtcp::LossNotification* loss_notification =
        packet_information.loss_notification.get();
    RTC_DCHECK(loss_notification);
    if (loss_notification->media_ssrc() == local_media_ssrc()) {
      rtcp_loss_notification_observer_->OnReceivedLossNotification(
          loss_notification->media_ssrc(), loss_notification->last_decoded(),
          loss_notification->last_received(),
          loss_notification->decodability_flag());
    }
  }

  if (network_link_rtcp_observer_) {
    Timestamp now = clock_->CurrentTime();
    if (packet_information.packet_type_flags & kRtcpRemb) {
      network_link_rtcp_observer_->OnReceiverEstimatedMaxBitrate(
          now, DataRate::BitsPerSec(
                   packet_information.receiver_estimated_max_bitrate_bps));
    }
    if (!packet_information.report_block_datas.empty()) {
      network_link_rtcp_observer_->OnReport(
          now, packet_information.report_block_datas);
    }
    if (packet_information.rtt.has_value()) {
      network_link_rtcp_observer_->OnRttUpdate(now, *packet_information.rtt);
    }
    if (packet_information.transport_feedback != nullptr) {
      network_link_rtcp_observer_->OnTransportFeedback(
          now, *packet_information.transport_feedback);
    }
  }

  if ((packet_information.packet_type_flags & kRtcpSr) ||
      (packet_information.packet_type_flags & kRtcpRr)) {
    rtp_rtcp_->OnReceivedRtcpReportBlocks(
        packet_information.report_block_datas);
  }

  if (network_state_estimate_observer_ &&
      packet_information.network_state_estimate) {
    network_state_estimate_observer_->OnRemoteNetworkEstimate(
        *packet_information.network_state_estimate);
  }

  if (bitrate_allocation_observer_ &&
      packet_information.target_bitrate_allocation) {
    bitrate_allocation_observer_->OnBitrateAllocationUpdated(
        *packet_information.target_bitrate_allocation);
  }

  if (!receiver_only_) {
    if (report_block_data_observer_) {
      for (const auto& report_block_data :
           packet_information.report_block_datas) {
        report_block_data_observer_->OnReportBlockDataUpdated(
            report_block_data);
      }
    }
  }
}

std::vector<rtcp::TmmbItem> RTCPReceiver::TmmbrReceived() {
  MutexLock lock(&rtcp_receiver_lock_);
  std::vector<rtcp::TmmbItem> candidates;

  Timestamp now = clock_->CurrentTime();

  for (auto& kv : tmmbr_infos_) {
    for (auto it = kv.second.tmmbr.begin(); it != kv.second.tmmbr.end();) {
      if (now - it->second.last_updated > kTmmbrTimeoutInterval) {
        // Erase timeout entries.
        it = kv.second.tmmbr.erase(it);
      } else {
        candidates.push_back(it->second.tmmbr_item);
        ++it;
      }
    }
  }
  return candidates;
}

bool RTCPReceiver::RtcpRrTimeoutLocked(Timestamp now) {
  return ResetTimestampIfExpired(now, last_received_rb_, report_interval_);
}

bool RTCPReceiver::RtcpRrSequenceNumberTimeoutLocked(Timestamp now) {
  return ResetTimestampIfExpired(now, last_increased_sequence_number_,
                                 report_interval_);
}

}  // namespace webrtc
