/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtcp_transceiver_impl.h"

#include <algorithm>
#include <utility>

#include "absl/algorithm/container.h"
#include "absl/memory/memory.h"
#include "api/call/transport.h"
#include "api/video/video_bitrate_allocation.h"
#include "modules/rtp_rtcp/include/receive_statistics.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "modules/rtp_rtcp/source/rtcp_packet.h"
#include "modules/rtp_rtcp/source/rtcp_packet/bye.h"
#include "modules/rtp_rtcp/source/rtcp_packet/common_header.h"
#include "modules/rtp_rtcp/source/rtcp_packet/extended_reports.h"
#include "modules/rtp_rtcp/source/rtcp_packet/fir.h"
#include "modules/rtp_rtcp/source/rtcp_packet/nack.h"
#include "modules/rtp_rtcp/source/rtcp_packet/pli.h"
#include "modules/rtp_rtcp/source/rtcp_packet/receiver_report.h"
#include "modules/rtp_rtcp/source/rtcp_packet/report_block.h"
#include "modules/rtp_rtcp/source/rtcp_packet/sdes.h"
#include "modules/rtp_rtcp/source/rtcp_packet/sender_report.h"
#include "modules/rtp_rtcp/source/time_util.h"
#include "rtc_base/checks.h"
#include "rtc_base/containers/flat_map.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/divide_round.h"
#include "rtc_base/task_utils/repeating_task.h"
#include "system_wrappers/include/clock.h"

namespace webrtc {
namespace {

struct SenderReportTimes {
  Timestamp local_received_time;
  NtpTime remote_sent_time;
};

}  // namespace

struct RtcpTransceiverImpl::RemoteSenderState {
  uint8_t fir_sequence_number = 0;
  absl::optional<SenderReportTimes> last_received_sender_report;
  std::vector<MediaReceiverRtcpObserver*> observers;
};

struct RtcpTransceiverImpl::LocalSenderState {
  uint32_t ssrc;
  size_t last_num_sent_bytes = 0;
  // Sequence number of the last FIR message per sender SSRC.
  flat_map<uint32_t, uint8_t> last_fir;
  RtpStreamRtcpHandler* handler = nullptr;
};

// Helper to put several RTCP packets into lower layer datagram composing
// Compound or Reduced-Size RTCP packet, as defined by RFC 5506 section 2.
// TODO(danilchap): When in compound mode and packets are so many that several
// compound RTCP packets need to be generated, ensure each packet is compound.
class RtcpTransceiverImpl::PacketSender {
 public:
  PacketSender(rtcp::RtcpPacket::PacketReadyCallback callback,
               size_t max_packet_size)
      : callback_(callback), max_packet_size_(max_packet_size) {
    RTC_CHECK_LE(max_packet_size, IP_PACKET_SIZE);
  }
  ~PacketSender() { RTC_DCHECK_EQ(index_, 0) << "Unsent rtcp packet."; }

  // Appends a packet to pending compound packet.
  // Sends rtcp compound packet if buffer was already full and resets buffer.
  void AppendPacket(const rtcp::RtcpPacket& packet) {
    packet.Create(buffer_, &index_, max_packet_size_, callback_);
  }

  // Sends pending rtcp compound packet.
  void Send() {
    if (index_ > 0) {
      callback_(rtc::ArrayView<const uint8_t>(buffer_, index_));
      index_ = 0;
    }
  }

  bool IsEmpty() const { return index_ == 0; }

 private:
  const rtcp::RtcpPacket::PacketReadyCallback callback_;
  const size_t max_packet_size_;
  size_t index_ = 0;
  uint8_t buffer_[IP_PACKET_SIZE];
};

RtcpTransceiverImpl::RtcpTransceiverImpl(const RtcpTransceiverConfig& config)
    : config_(config), ready_to_send_(config.initial_ready_to_send) {
  RTC_CHECK(config_.Validate());
  if (ready_to_send_ && config_.schedule_periodic_compound_packets) {
    SchedulePeriodicCompoundPackets(config_.initial_report_delay);
  }
}

RtcpTransceiverImpl::~RtcpTransceiverImpl() = default;

void RtcpTransceiverImpl::AddMediaReceiverRtcpObserver(
    uint32_t remote_ssrc,
    MediaReceiverRtcpObserver* observer) {
  if (config_.receive_statistics == nullptr && remote_senders_.empty()) {
    RTC_LOG(LS_WARNING) << config_.debug_id
                        << "receive statistic is not set. RTCP report blocks "
                           "will not be generated.";
  }
  auto& stored = remote_senders_[remote_ssrc].observers;
  RTC_DCHECK(!absl::c_linear_search(stored, observer));
  stored.push_back(observer);
}

void RtcpTransceiverImpl::RemoveMediaReceiverRtcpObserver(
    uint32_t remote_ssrc,
    MediaReceiverRtcpObserver* observer) {
  auto remote_sender_it = remote_senders_.find(remote_ssrc);
  if (remote_sender_it == remote_senders_.end())
    return;
  auto& stored = remote_sender_it->second.observers;
  auto it = absl::c_find(stored, observer);
  if (it == stored.end())
    return;
  stored.erase(it);
}

bool RtcpTransceiverImpl::AddMediaSender(uint32_t local_ssrc,
                                         RtpStreamRtcpHandler* handler) {
  RTC_DCHECK(handler != nullptr);
  LocalSenderState state;
  state.ssrc = local_ssrc;
  state.handler = handler;
  local_senders_.push_back(state);
  auto it = std::prev(local_senders_.end());
  auto [unused, inserted] = local_senders_by_ssrc_.emplace(local_ssrc, it);
  if (!inserted) {
    local_senders_.pop_back();
    return false;
  }
  return true;
}

bool RtcpTransceiverImpl::RemoveMediaSender(uint32_t local_ssrc) {
  auto index_it = local_senders_by_ssrc_.find(local_ssrc);
  if (index_it == local_senders_by_ssrc_.end()) {
    return false;
  }
  local_senders_.erase(index_it->second);
  local_senders_by_ssrc_.erase(index_it);
  return true;
}

void RtcpTransceiverImpl::SetReadyToSend(bool ready) {
  if (config_.schedule_periodic_compound_packets) {
    if (ready_to_send_ && !ready)
      periodic_task_handle_.Stop();

    if (!ready_to_send_ && ready)  // Restart periodic sending.
      SchedulePeriodicCompoundPackets(config_.report_period / 2);
  }
  ready_to_send_ = ready;
}

void RtcpTransceiverImpl::ReceivePacket(rtc::ArrayView<const uint8_t> packet,
                                        Timestamp now) {
  // Report blocks may be spread across multiple sender and receiver reports.
  std::vector<rtcp::ReportBlock> report_blocks;

  while (!packet.empty()) {
    rtcp::CommonHeader rtcp_block;
    if (!rtcp_block.Parse(packet.data(), packet.size()))
      break;

    HandleReceivedPacket(rtcp_block, now, report_blocks);

    packet = packet.subview(rtcp_block.packet_size());
  }

  if (!report_blocks.empty()) {
    ProcessReportBlocks(now, report_blocks);
  }
}

void RtcpTransceiverImpl::SendCompoundPacket() {
  if (!ready_to_send_)
    return;
  SendPeriodicCompoundPacket();
  ReschedulePeriodicCompoundPackets();
}

void RtcpTransceiverImpl::SetRemb(int64_t bitrate_bps,
                                  std::vector<uint32_t> ssrcs) {
  RTC_DCHECK_GE(bitrate_bps, 0);

  bool send_now = config_.send_remb_on_change &&
                  (!remb_.has_value() || bitrate_bps != remb_->bitrate_bps());
  remb_.emplace();
  remb_->SetSsrcs(std::move(ssrcs));
  remb_->SetBitrateBps(bitrate_bps);
  remb_->SetSenderSsrc(config_.feedback_ssrc);
  // TODO(bugs.webrtc.org/8239): Move logic from PacketRouter for sending remb
  // immideately on large bitrate change when there is one RtcpTransceiver per
  // rtp transport.
  if (send_now) {
    absl::optional<rtcp::Remb> remb;
    remb.swap(remb_);
    SendImmediateFeedback(*remb);
    remb.swap(remb_);
  }
}

void RtcpTransceiverImpl::UnsetRemb() {
  remb_.reset();
}

void RtcpTransceiverImpl::SendRawPacket(rtc::ArrayView<const uint8_t> packet) {
  if (!ready_to_send_)
    return;
  // Unlike other senders, this functions just tries to send packet away and
  // disregard rtcp_mode, max_packet_size or anything else.
  // TODO(bugs.webrtc.org/8239): respect config_ by creating the
  // TransportFeedback inside this class when there is one per rtp transport.
  config_.outgoing_transport->SendRtcp(packet.data(), packet.size());
}

void RtcpTransceiverImpl::SendNack(uint32_t ssrc,
                                   std::vector<uint16_t> sequence_numbers) {
  RTC_DCHECK(!sequence_numbers.empty());
  if (!ready_to_send_)
    return;
  rtcp::Nack nack;
  nack.SetSenderSsrc(config_.feedback_ssrc);
  nack.SetMediaSsrc(ssrc);
  nack.SetPacketIds(std::move(sequence_numbers));
  SendImmediateFeedback(nack);
}

void RtcpTransceiverImpl::SendPictureLossIndication(uint32_t ssrc) {
  if (!ready_to_send_)
    return;
  rtcp::Pli pli;
  pli.SetSenderSsrc(config_.feedback_ssrc);
  pli.SetMediaSsrc(ssrc);
  SendImmediateFeedback(pli);
}

void RtcpTransceiverImpl::SendFullIntraRequest(
    rtc::ArrayView<const uint32_t> ssrcs,
    bool new_request) {
  RTC_DCHECK(!ssrcs.empty());
  if (!ready_to_send_)
    return;
  rtcp::Fir fir;
  fir.SetSenderSsrc(config_.feedback_ssrc);
  for (uint32_t media_ssrc : ssrcs) {
    uint8_t& command_seq_num = remote_senders_[media_ssrc].fir_sequence_number;
    if (new_request)
      command_seq_num += 1;
    fir.AddRequestTo(media_ssrc, command_seq_num);
  }
  SendImmediateFeedback(fir);
}

void RtcpTransceiverImpl::HandleReceivedPacket(
    const rtcp::CommonHeader& rtcp_packet_header,
    Timestamp now,
    std::vector<rtcp::ReportBlock>& report_blocks) {
  switch (rtcp_packet_header.type()) {
    case rtcp::Bye::kPacketType:
      HandleBye(rtcp_packet_header);
      break;
    case rtcp::SenderReport::kPacketType:
      HandleSenderReport(rtcp_packet_header, now, report_blocks);
      break;
    case rtcp::ReceiverReport::kPacketType:
      HandleReceiverReport(rtcp_packet_header, report_blocks);
      break;
    case rtcp::ExtendedReports::kPacketType:
      HandleExtendedReports(rtcp_packet_header, now);
      break;
    case rtcp::Psfb::kPacketType:
      HandlePayloadSpecificFeedback(rtcp_packet_header, now);
      break;
    case rtcp::Rtpfb::kPacketType:
      HandleRtpFeedback(rtcp_packet_header, now);
      break;
  }
}

void RtcpTransceiverImpl::HandleBye(
    const rtcp::CommonHeader& rtcp_packet_header) {
  rtcp::Bye bye;
  if (!bye.Parse(rtcp_packet_header))
    return;
  auto remote_sender_it = remote_senders_.find(bye.sender_ssrc());
  if (remote_sender_it == remote_senders_.end())
    return;
  for (MediaReceiverRtcpObserver* observer : remote_sender_it->second.observers)
    observer->OnBye(bye.sender_ssrc());
}

void RtcpTransceiverImpl::HandleSenderReport(
    const rtcp::CommonHeader& rtcp_packet_header,
    Timestamp now,
    std::vector<rtcp::ReportBlock>& report_blocks) {
  rtcp::SenderReport sender_report;
  if (!sender_report.Parse(rtcp_packet_header))
    return;
  RemoteSenderState& remote_sender =
      remote_senders_[sender_report.sender_ssrc()];
  remote_sender.last_received_sender_report = {{now, sender_report.ntp()}};
  const auto& received_report_blocks = sender_report.report_blocks();
  CallbackOnReportBlocks(sender_report.sender_ssrc(), received_report_blocks);
  report_blocks.insert(report_blocks.end(), received_report_blocks.begin(),
                       received_report_blocks.end());

  for (MediaReceiverRtcpObserver* observer : remote_sender.observers)
    observer->OnSenderReport(sender_report.sender_ssrc(), sender_report.ntp(),
                             sender_report.rtp_timestamp());
}

void RtcpTransceiverImpl::HandleReceiverReport(
    const rtcp::CommonHeader& rtcp_packet_header,
    std::vector<rtcp::ReportBlock>& report_blocks) {
  rtcp::ReceiverReport receiver_report;
  if (!receiver_report.Parse(rtcp_packet_header)) {
    return;
  }
  const auto& received_report_blocks = receiver_report.report_blocks();
  CallbackOnReportBlocks(receiver_report.sender_ssrc(), received_report_blocks);
  report_blocks.insert(report_blocks.end(), received_report_blocks.begin(),
                       received_report_blocks.end());
}

void RtcpTransceiverImpl::CallbackOnReportBlocks(
    uint32_t sender_ssrc,
    rtc::ArrayView<const rtcp::ReportBlock> report_blocks) {
  if (local_senders_.empty()) {
    return;
  }
  for (const rtcp::ReportBlock& block : report_blocks) {
    auto sender_it = local_senders_by_ssrc_.find(block.source_ssrc());
    if (sender_it != local_senders_by_ssrc_.end()) {
      sender_it->second->handler->OnReportBlock(sender_ssrc, block);
    }
  }
}

void RtcpTransceiverImpl::HandlePayloadSpecificFeedback(
    const rtcp::CommonHeader& rtcp_packet_header,
    Timestamp now) {
  switch (rtcp_packet_header.fmt()) {
    case rtcp::Fir::kFeedbackMessageType:
      HandleFir(rtcp_packet_header);
      break;
    case rtcp::Pli::kFeedbackMessageType:
      HandlePli(rtcp_packet_header);
      break;
    case rtcp::Psfb::kAfbMessageType:
      HandleRemb(rtcp_packet_header, now);
      break;
  }
}

void RtcpTransceiverImpl::HandleFir(
    const rtcp::CommonHeader& rtcp_packet_header) {
  rtcp::Fir fir;
  if (local_senders_.empty() || !fir.Parse(rtcp_packet_header)) {
    return;
  }
  for (const rtcp::Fir::Request& r : fir.requests()) {
    auto it = local_senders_by_ssrc_.find(r.ssrc);
    if (it == local_senders_by_ssrc_.end()) {
      continue;
    }
    auto [fir_it, is_new] =
        it->second->last_fir.emplace(fir.sender_ssrc(), r.seq_nr);
    if (is_new || fir_it->second != r.seq_nr) {
      it->second->handler->OnFir(fir.sender_ssrc());
      fir_it->second = r.seq_nr;
    }
  }
}

void RtcpTransceiverImpl::HandlePli(
    const rtcp::CommonHeader& rtcp_packet_header) {
  rtcp::Pli pli;
  if (local_senders_.empty() || !pli.Parse(rtcp_packet_header)) {
    return;
  }
  auto it = local_senders_by_ssrc_.find(pli.media_ssrc());
  if (it != local_senders_by_ssrc_.end()) {
    it->second->handler->OnPli(pli.sender_ssrc());
  }
}

void RtcpTransceiverImpl::HandleRemb(
    const rtcp::CommonHeader& rtcp_packet_header,
    Timestamp now) {
  rtcp::Remb remb;
  if (config_.network_link_observer == nullptr ||
      !remb.Parse(rtcp_packet_header)) {
    return;
  }
  config_.network_link_observer->OnReceiverEstimatedMaxBitrate(
      now, DataRate::BitsPerSec(remb.bitrate_bps()));
}

void RtcpTransceiverImpl::HandleRtpFeedback(
    const rtcp::CommonHeader& rtcp_packet_header,
    Timestamp now) {
  switch (rtcp_packet_header.fmt()) {
    case rtcp::Nack::kFeedbackMessageType:
      HandleNack(rtcp_packet_header);
      break;
    case rtcp::TransportFeedback::kFeedbackMessageType:
      HandleTransportFeedback(rtcp_packet_header, now);
      break;
  }
}

void RtcpTransceiverImpl::HandleNack(
    const rtcp::CommonHeader& rtcp_packet_header) {
  rtcp::Nack nack;
  if (local_senders_.empty() || !nack.Parse(rtcp_packet_header)) {
    return;
  }
  auto it = local_senders_by_ssrc_.find(nack.media_ssrc());
  if (it != local_senders_by_ssrc_.end()) {
    it->second->handler->OnNack(nack.sender_ssrc(), nack.packet_ids());
  }
}

void RtcpTransceiverImpl::HandleTransportFeedback(
    const rtcp::CommonHeader& rtcp_packet_header,
    Timestamp now) {
  RTC_DCHECK_EQ(rtcp_packet_header.fmt(),
                rtcp::TransportFeedback::kFeedbackMessageType);
  if (config_.network_link_observer == nullptr) {
    return;
  }
  rtcp::TransportFeedback feedback;
  if (feedback.Parse(rtcp_packet_header)) {
    config_.network_link_observer->OnTransportFeedback(now, feedback);
  }
}

void RtcpTransceiverImpl::HandleExtendedReports(
    const rtcp::CommonHeader& rtcp_packet_header,
    Timestamp now) {
  rtcp::ExtendedReports extended_reports;
  if (!extended_reports.Parse(rtcp_packet_header))
    return;

  if (config_.reply_to_non_sender_rtt_measurement && extended_reports.rrtr()) {
    RrtrTimes& rrtr = received_rrtrs_[extended_reports.sender_ssrc()];
    rrtr.received_remote_mid_ntp_time =
        CompactNtp(extended_reports.rrtr()->ntp());
    rrtr.local_receive_mid_ntp_time =
        CompactNtp(config_.clock->ConvertTimestampToNtpTime(now));
  }

  if (extended_reports.dlrr())
    HandleDlrr(extended_reports.dlrr(), now);

  if (extended_reports.target_bitrate())
    HandleTargetBitrate(*extended_reports.target_bitrate(),
                        extended_reports.sender_ssrc());
}

void RtcpTransceiverImpl::HandleDlrr(const rtcp::Dlrr& dlrr, Timestamp now) {
  if (!config_.non_sender_rtt_measurement ||
      config_.network_link_observer == nullptr) {
    return;
  }

  // Delay and last_rr are transferred using 32bit compact ntp resolution.
  // Convert packet arrival time to same format through 64bit ntp format.
  uint32_t receive_time_ntp =
      CompactNtp(config_.clock->ConvertTimestampToNtpTime(now));
  for (const rtcp::ReceiveTimeInfo& rti : dlrr.sub_blocks()) {
    if (rti.ssrc != config_.feedback_ssrc)
      continue;
    uint32_t rtt_ntp = receive_time_ntp - rti.delay_since_last_rr - rti.last_rr;
    TimeDelta rtt = CompactNtpRttToTimeDelta(rtt_ntp);
    config_.network_link_observer->OnRttUpdate(now, rtt);
  }
}

void RtcpTransceiverImpl::ProcessReportBlocks(
    Timestamp now,
    rtc::ArrayView<const rtcp::ReportBlock> report_blocks) {
  RTC_DCHECK(!report_blocks.empty());
  if (config_.network_link_observer == nullptr) {
    return;
  }
  // Round trip time calculated from different report blocks suppose to be about
  // the same, as those blocks should be generated by the same remote sender.
  // To avoid too many callbacks, this code accumulate multiple rtts into one.
  TimeDelta rtt_sum = TimeDelta::Zero();
  size_t num_rtts = 0;
  uint32_t receive_time_ntp =
      CompactNtp(config_.clock->ConvertTimestampToNtpTime(now));
  for (const rtcp::ReportBlock& report_block : report_blocks) {
    if (report_block.last_sr() == 0) {
      continue;
    }

    uint32_t rtt_ntp = receive_time_ntp - report_block.delay_since_last_sr() -
                       report_block.last_sr();
    rtt_sum += CompactNtpRttToTimeDelta(rtt_ntp);
    ++num_rtts;
  }
  // For backward compatibility, do not report rtt based on report blocks to the
  // `config_.rtt_observer`
  if (num_rtts > 0) {
    config_.network_link_observer->OnRttUpdate(now, rtt_sum / num_rtts);
  }
  config_.network_link_observer->OnReportBlocks(now, report_blocks);
}

void RtcpTransceiverImpl::HandleTargetBitrate(
    const rtcp::TargetBitrate& target_bitrate,
    uint32_t remote_ssrc) {
  auto remote_sender_it = remote_senders_.find(remote_ssrc);
  if (remote_sender_it == remote_senders_.end() ||
      remote_sender_it->second.observers.empty())
    return;

  // Convert rtcp::TargetBitrate to VideoBitrateAllocation.
  VideoBitrateAllocation bitrate_allocation;
  for (const rtcp::TargetBitrate::BitrateItem& item :
       target_bitrate.GetTargetBitrates()) {
    if (item.spatial_layer >= kMaxSpatialLayers ||
        item.temporal_layer >= kMaxTemporalStreams) {
      RTC_DLOG(LS_WARNING)
          << config_.debug_id
          << "Invalid incoming TargetBitrate with spatial layer "
          << item.spatial_layer << ", temporal layer " << item.temporal_layer;
      continue;
    }
    bitrate_allocation.SetBitrate(item.spatial_layer, item.temporal_layer,
                                  item.target_bitrate_kbps * 1000);
  }

  for (MediaReceiverRtcpObserver* observer : remote_sender_it->second.observers)
    observer->OnBitrateAllocation(remote_ssrc, bitrate_allocation);
}

void RtcpTransceiverImpl::ReschedulePeriodicCompoundPackets() {
  if (!config_.schedule_periodic_compound_packets)
    return;
  periodic_task_handle_.Stop();
  RTC_DCHECK(ready_to_send_);
  SchedulePeriodicCompoundPackets(config_.report_period);
}

void RtcpTransceiverImpl::SchedulePeriodicCompoundPackets(TimeDelta delay) {
  periodic_task_handle_ = RepeatingTaskHandle::DelayedStart(
      config_.task_queue, delay,
      [this] {
        RTC_DCHECK(config_.schedule_periodic_compound_packets);
        RTC_DCHECK(ready_to_send_);
        SendPeriodicCompoundPacket();
        return config_.report_period;
      },
      TaskQueueBase::DelayPrecision::kLow, config_.clock);
}

std::vector<uint32_t> RtcpTransceiverImpl::FillReports(
    Timestamp now,
    ReservedBytes reserved,
    PacketSender& rtcp_sender) {
  // Sender/receiver reports should be first in the RTCP packet.
  RTC_DCHECK(rtcp_sender.IsEmpty());

  size_t available_bytes = config_.max_packet_size;
  if (reserved.per_packet > available_bytes) {
    // Because reserved.per_packet is unsigned, substracting would underflow and
    // will not produce desired result.
    available_bytes = 0;
  } else {
    available_bytes -= reserved.per_packet;
  }

  const size_t sender_report_size_bytes = 28 + reserved.per_sender;
  const size_t full_sender_report_size_bytes =
      sender_report_size_bytes +
      rtcp::SenderReport::kMaxNumberOfReportBlocks * rtcp::ReportBlock::kLength;
  size_t max_full_sender_reports =
      available_bytes / full_sender_report_size_bytes;
  size_t max_report_blocks =
      max_full_sender_reports * rtcp::SenderReport::kMaxNumberOfReportBlocks;
  size_t available_bytes_for_last_sender_report =
      available_bytes - max_full_sender_reports * full_sender_report_size_bytes;
  if (available_bytes_for_last_sender_report >= sender_report_size_bytes) {
    max_report_blocks +=
        (available_bytes_for_last_sender_report - sender_report_size_bytes) /
        rtcp::ReportBlock::kLength;
  }

  std::vector<rtcp::ReportBlock> report_blocks =
      CreateReportBlocks(now, max_report_blocks);
  // Previous calculation of max number of sender report made space for max
  // number of report blocks per sender report, but if number of report blocks
  // is low, more sender reports may fit in.
  size_t max_sender_reports =
      (available_bytes - report_blocks.size() * rtcp::ReportBlock::kLength) /
      sender_report_size_bytes;

  auto last_handled_sender_it = local_senders_.end();
  auto report_block_it = report_blocks.begin();
  std::vector<uint32_t> sender_ssrcs;
  for (auto it = local_senders_.begin();
       it != local_senders_.end() && sender_ssrcs.size() < max_sender_reports;
       ++it) {
    LocalSenderState& rtp_sender = *it;
    RtpStreamRtcpHandler::RtpStats stats = rtp_sender.handler->SentStats();

    if (stats.num_sent_bytes() < rtp_sender.last_num_sent_bytes) {
      RTC_LOG(LS_ERROR) << "Inconsistent SR for SSRC " << rtp_sender.ssrc
                        << ". Number of total sent bytes decreased.";
      rtp_sender.last_num_sent_bytes = 0;
    }
    if (stats.num_sent_bytes() == rtp_sender.last_num_sent_bytes) {
      // Skip because no RTP packet was send for this SSRC since last report.
      continue;
    }
    rtp_sender.last_num_sent_bytes = stats.num_sent_bytes();

    last_handled_sender_it = it;
    rtcp::SenderReport sender_report;
    sender_report.SetSenderSsrc(rtp_sender.ssrc);
    sender_report.SetPacketCount(stats.num_sent_packets());
    sender_report.SetOctetCount(stats.num_sent_bytes());
    sender_report.SetNtp(config_.clock->ConvertTimestampToNtpTime(now));
    RTC_DCHECK_GE(now, stats.last_capture_time());
    sender_report.SetRtpTimestamp(
        stats.last_rtp_timestamp() +
        ((now - stats.last_capture_time()) * stats.last_clock_rate())
            .seconds());
    if (report_block_it != report_blocks.end()) {
      size_t num_blocks =
          std::min<size_t>(rtcp::SenderReport::kMaxNumberOfReportBlocks,
                           report_blocks.end() - report_block_it);
      std::vector<rtcp::ReportBlock> sub_blocks(report_block_it,
                                                report_block_it + num_blocks);
      sender_report.SetReportBlocks(std::move(sub_blocks));
      report_block_it += num_blocks;
    }
    rtcp_sender.AppendPacket(sender_report);
    sender_ssrcs.push_back(rtp_sender.ssrc);
  }
  if (last_handled_sender_it != local_senders_.end()) {
    // Rotate `local_senders_` so that the 1st unhandled sender become first in
    // the list, and thus will be first to generate rtcp sender report for on
    // the next call to `FillReports`.
    local_senders_.splice(local_senders_.end(), local_senders_,
                          local_senders_.begin(),
                          std::next(last_handled_sender_it));
  }

  // Calculcate number of receiver reports to attach remaining report blocks to.
  size_t num_receiver_reports =
      DivideRoundUp(report_blocks.end() - report_block_it,
                    rtcp::ReceiverReport::kMaxNumberOfReportBlocks);

  // In compound mode each RTCP packet has to start with a sender or receiver
  // report.
  if (config_.rtcp_mode == RtcpMode::kCompound && sender_ssrcs.empty() &&
      num_receiver_reports == 0) {
    num_receiver_reports = 1;
  }

  uint32_t sender_ssrc =
      sender_ssrcs.empty() ? config_.feedback_ssrc : sender_ssrcs.front();
  for (size_t i = 0; i < num_receiver_reports; ++i) {
    rtcp::ReceiverReport receiver_report;
    receiver_report.SetSenderSsrc(sender_ssrc);
    size_t num_blocks =
        std::min<size_t>(rtcp::ReceiverReport::kMaxNumberOfReportBlocks,
                         report_blocks.end() - report_block_it);
    std::vector<rtcp::ReportBlock> sub_blocks(report_block_it,
                                              report_block_it + num_blocks);
    receiver_report.SetReportBlocks(std::move(sub_blocks));
    report_block_it += num_blocks;
    rtcp_sender.AppendPacket(receiver_report);
  }
  // All report blocks should be attached at this point.
  RTC_DCHECK_EQ(report_blocks.end() - report_block_it, 0);
  return sender_ssrcs;
}

void RtcpTransceiverImpl::CreateCompoundPacket(Timestamp now,
                                               size_t reserved_bytes,
                                               PacketSender& sender) {
  RTC_DCHECK(sender.IsEmpty());
  ReservedBytes reserved = {.per_packet = reserved_bytes};
  absl::optional<rtcp::Sdes> sdes;
  if (!config_.cname.empty()) {
    sdes.emplace();
    bool added = sdes->AddCName(config_.feedback_ssrc, config_.cname);
    RTC_DCHECK(added) << "Failed to add CNAME " << config_.cname
                      << " to RTCP SDES packet.";
    reserved.per_packet += sdes->BlockLength();
  }
  if (remb_.has_value()) {
    reserved.per_packet += remb_->BlockLength();
  }
  absl::optional<rtcp::ExtendedReports> xr_with_dlrr;
  if (!received_rrtrs_.empty()) {
    RTC_DCHECK(config_.reply_to_non_sender_rtt_measurement);
    xr_with_dlrr.emplace();
    uint32_t now_ntp =
        CompactNtp(config_.clock->ConvertTimestampToNtpTime(now));
    for (const auto& [ssrc, rrtr_info] : received_rrtrs_) {
      rtcp::ReceiveTimeInfo reply;
      reply.ssrc = ssrc;
      reply.last_rr = rrtr_info.received_remote_mid_ntp_time;
      reply.delay_since_last_rr =
          now_ntp - rrtr_info.local_receive_mid_ntp_time;
      xr_with_dlrr->AddDlrrItem(reply);
    }
    if (config_.reply_to_non_sender_rtt_mesaurments_on_all_ssrcs) {
      reserved.per_sender += xr_with_dlrr->BlockLength();
    } else {
      reserved.per_packet += xr_with_dlrr->BlockLength();
    }
  }
  if (config_.non_sender_rtt_measurement) {
    // It looks like bytes for ExtendedReport header are reserved twice, but in
    // practice the same RtcpTransceiver won't both produce RRTR (i.e. it is a
    // receiver-only) and reply to RRTR (i.e. remote participant is a receiver
    // only). If that happen, then `reserved_bytes` would be slightly larger
    // than it should, which is not an issue.

    // 4 bytes for common RTCP header + 4 bytes for the ExtenedReports header.
    reserved.per_packet += (4 + 4 + rtcp::Rrtr::kLength);
  }

  std::vector<uint32_t> sender_ssrcs = FillReports(now, reserved, sender);
  bool has_sender_report = !sender_ssrcs.empty();
  uint32_t sender_ssrc =
      has_sender_report ? sender_ssrcs.front() : config_.feedback_ssrc;

  if (sdes.has_value() && !sender.IsEmpty()) {
    sender.AppendPacket(*sdes);
  }
  if (remb_.has_value()) {
    remb_->SetSenderSsrc(sender_ssrc);
    sender.AppendPacket(*remb_);
  }
  if (!has_sender_report && config_.non_sender_rtt_measurement) {
    rtcp::ExtendedReports xr_with_rrtr;
    xr_with_rrtr.SetSenderSsrc(config_.feedback_ssrc);
    rtcp::Rrtr rrtr;
    rrtr.SetNtp(config_.clock->ConvertTimestampToNtpTime(now));
    xr_with_rrtr.SetRrtr(rrtr);
    sender.AppendPacket(xr_with_rrtr);
  }
  if (xr_with_dlrr.has_value()) {
    rtc::ArrayView<const uint32_t> ssrcs(&sender_ssrc, 1);
    if (config_.reply_to_non_sender_rtt_mesaurments_on_all_ssrcs &&
        !sender_ssrcs.empty()) {
      ssrcs = sender_ssrcs;
    }
    RTC_DCHECK(!ssrcs.empty());
    for (uint32_t ssrc : ssrcs) {
      xr_with_dlrr->SetSenderSsrc(ssrc);
      sender.AppendPacket(*xr_with_dlrr);
    }
  }
}

void RtcpTransceiverImpl::SendPeriodicCompoundPacket() {
  auto send_packet = [this](rtc::ArrayView<const uint8_t> packet) {
    config_.outgoing_transport->SendRtcp(packet.data(), packet.size());
  };
  Timestamp now = config_.clock->CurrentTime();
  PacketSender sender(send_packet, config_.max_packet_size);
  CreateCompoundPacket(now, /*reserved_bytes=*/0, sender);
  sender.Send();
}

void RtcpTransceiverImpl::SendCombinedRtcpPacket(
    std::vector<std::unique_ptr<rtcp::RtcpPacket>> rtcp_packets) {
  auto send_packet = [this](rtc::ArrayView<const uint8_t> packet) {
    config_.outgoing_transport->SendRtcp(packet.data(), packet.size());
  };
  PacketSender sender(send_packet, config_.max_packet_size);

  for (auto& rtcp_packet : rtcp_packets) {
    rtcp_packet->SetSenderSsrc(config_.feedback_ssrc);
    sender.AppendPacket(*rtcp_packet);
  }
  sender.Send();
}

void RtcpTransceiverImpl::SendImmediateFeedback(
    const rtcp::RtcpPacket& rtcp_packet) {
  auto send_packet = [this](rtc::ArrayView<const uint8_t> packet) {
    config_.outgoing_transport->SendRtcp(packet.data(), packet.size());
  };
  PacketSender sender(send_packet, config_.max_packet_size);
  // Compound mode requires every sent rtcp packet to be compound, i.e. start
  // with a sender or receiver report.
  if (config_.rtcp_mode == RtcpMode::kCompound) {
    Timestamp now = config_.clock->CurrentTime();
    CreateCompoundPacket(now, /*reserved_bytes=*/rtcp_packet.BlockLength(),
                         sender);
  }

  sender.AppendPacket(rtcp_packet);
  sender.Send();

  // If compound packet was sent, delay (reschedule) the periodic one.
  if (config_.rtcp_mode == RtcpMode::kCompound)
    ReschedulePeriodicCompoundPackets();
}

std::vector<rtcp::ReportBlock> RtcpTransceiverImpl::CreateReportBlocks(
    Timestamp now,
    size_t num_max_blocks) {
  if (!config_.receive_statistics)
    return {};
  std::vector<rtcp::ReportBlock> report_blocks =
      config_.receive_statistics->RtcpReportBlocks(num_max_blocks);
  uint32_t last_sr = 0;
  uint32_t last_delay = 0;
  for (rtcp::ReportBlock& report_block : report_blocks) {
    auto it = remote_senders_.find(report_block.source_ssrc());
    if (it == remote_senders_.end() ||
        !it->second.last_received_sender_report) {
      continue;
    }
    const SenderReportTimes& last_sender_report =
        *it->second.last_received_sender_report;
    last_sr = CompactNtp(last_sender_report.remote_sent_time);
    last_delay =
        SaturatedToCompactNtp(now - last_sender_report.local_received_time);
    report_block.SetLastSr(last_sr);
    report_block.SetDelayLastSr(last_delay);
  }
  return report_blocks;
}

}  // namespace webrtc
