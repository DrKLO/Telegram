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
#include "rtc_base/logging.h"
#include "rtc_base/task_utils/repeating_task.h"
#include "rtc_base/task_utils/to_queued_task.h"
#include "rtc_base/time_utils.h"

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
    SchedulePeriodicCompoundPackets(config_.initial_report_delay_ms);
  }
}

RtcpTransceiverImpl::~RtcpTransceiverImpl() = default;

void RtcpTransceiverImpl::AddMediaReceiverRtcpObserver(
    uint32_t remote_ssrc,
    MediaReceiverRtcpObserver* observer) {
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

void RtcpTransceiverImpl::SetReadyToSend(bool ready) {
  if (config_.schedule_periodic_compound_packets) {
    if (ready_to_send_ && !ready)
      periodic_task_handle_.Stop();

    if (!ready_to_send_ && ready)  // Restart periodic sending.
      SchedulePeriodicCompoundPackets(config_.report_period_ms / 2);
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
  report_blocks.insert(report_blocks.end(), received_report_blocks.begin(),
                       received_report_blocks.end());
}

void RtcpTransceiverImpl::HandlePayloadSpecificFeedback(
    const rtcp::CommonHeader& rtcp_packet_header,
    Timestamp now) {
  // Remb is the only payload specific message handled right now.
  if (rtcp_packet_header.fmt() != rtcp::Psfb::kAfbMessageType ||
      config_.network_link_observer == nullptr) {
    return;
  }
  rtcp::Remb remb;
  if (remb.Parse(rtcp_packet_header)) {
    config_.network_link_observer->OnReceiverEstimatedMaxBitrate(
        now, DataRate::BitsPerSec(remb.bitrate_bps()));
  }
}

void RtcpTransceiverImpl::HandleRtpFeedback(
    const rtcp::CommonHeader& rtcp_packet_header,
    Timestamp now) {
  // Transport feedback is the only message handled right now.
  if (rtcp_packet_header.fmt() !=
          rtcp::TransportFeedback::kFeedbackMessageType ||
      config_.network_link_observer == nullptr) {
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
    int64_t rtt_ms = CompactNtpRttToMs(rtt_ntp);
    config_.network_link_observer->OnRttUpdate(now, TimeDelta::Millis(rtt_ms));
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
    rtt_sum += TimeDelta::Millis(CompactNtpRttToMs(rtt_ntp));
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
  SchedulePeriodicCompoundPackets(config_.report_period_ms);
}

void RtcpTransceiverImpl::SchedulePeriodicCompoundPackets(int64_t delay_ms) {
  periodic_task_handle_ = RepeatingTaskHandle::DelayedStart(
      config_.task_queue, TimeDelta::Millis(delay_ms), [this] {
        RTC_DCHECK(config_.schedule_periodic_compound_packets);
        RTC_DCHECK(ready_to_send_);
        SendPeriodicCompoundPacket();
        return TimeDelta::Millis(config_.report_period_ms);
      });
}

void RtcpTransceiverImpl::CreateCompoundPacket(PacketSender* sender) {
  RTC_DCHECK(sender->IsEmpty());
  const uint32_t sender_ssrc = config_.feedback_ssrc;
  Timestamp now = config_.clock->CurrentTime();
  rtcp::ReceiverReport receiver_report;
  receiver_report.SetSenderSsrc(sender_ssrc);
  receiver_report.SetReportBlocks(CreateReportBlocks(now));
  if (config_.rtcp_mode == RtcpMode::kCompound ||
      !receiver_report.report_blocks().empty()) {
    sender->AppendPacket(receiver_report);
  }

  if (!config_.cname.empty() && !sender->IsEmpty()) {
    rtcp::Sdes sdes;
    bool added = sdes.AddCName(config_.feedback_ssrc, config_.cname);
    RTC_DCHECK(added) << "Failed to add cname " << config_.cname
                      << " to rtcp sdes packet.";
    sender->AppendPacket(sdes);
  }
  if (remb_) {
    remb_->SetSenderSsrc(sender_ssrc);
    sender->AppendPacket(*remb_);
  }
  // TODO(bugs.webrtc.org/8239): Do not send rrtr if this packet starts with
  // SenderReport instead of ReceiverReport
  // when RtcpTransceiver supports rtp senders.
  if (config_.non_sender_rtt_measurement) {
    rtcp::ExtendedReports xr;

    rtcp::Rrtr rrtr;
    rrtr.SetNtp(config_.clock->ConvertTimestampToNtpTime(now));
    xr.SetRrtr(rrtr);

    xr.SetSenderSsrc(sender_ssrc);
    sender->AppendPacket(xr);
  }
}

void RtcpTransceiverImpl::SendPeriodicCompoundPacket() {
  auto send_packet = [this](rtc::ArrayView<const uint8_t> packet) {
    config_.outgoing_transport->SendRtcp(packet.data(), packet.size());
  };
  PacketSender sender(send_packet, config_.max_packet_size);
  CreateCompoundPacket(&sender);
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
  if (config_.rtcp_mode == RtcpMode::kCompound)
    CreateCompoundPacket(&sender);

  sender.AppendPacket(rtcp_packet);
  sender.Send();

  // If compound packet was sent, delay (reschedule) the periodic one.
  if (config_.rtcp_mode == RtcpMode::kCompound)
    ReschedulePeriodicCompoundPackets();
}

std::vector<rtcp::ReportBlock> RtcpTransceiverImpl::CreateReportBlocks(
    Timestamp now) {
  if (!config_.receive_statistics)
    return {};
  // TODO(danilchap): Support sending more than
  // `ReceiverReport::kMaxNumberOfReportBlocks` per compound rtcp packet.
  std::vector<rtcp::ReportBlock> report_blocks =
      config_.receive_statistics->RtcpReportBlocks(
          rtcp::ReceiverReport::kMaxNumberOfReportBlocks);
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
    last_delay = SaturatedUsToCompactNtp(
        now.us() - last_sender_report.local_received_time.us());
    report_block.SetLastSr(last_sr);
    report_block.SetDelayLastSr(last_delay);
  }
  return report_blocks;
}

}  // namespace webrtc
