/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/pacing/packet_router.h"

#include <algorithm>
#include <cstdint>
#include <limits>
#include <memory>
#include <utility>

#include "absl/types/optional.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "modules/rtp_rtcp/source/rtcp_packet.h"
#include "modules/rtp_rtcp/source/rtp_rtcp_interface.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/system/unused.h"
#include "rtc_base/trace_event.h"

namespace webrtc {

PacketRouter::PacketRouter() : PacketRouter(0) {}

PacketRouter::PacketRouter(uint16_t start_transport_seq)
    : last_send_module_(nullptr),
      active_remb_module_(nullptr),
      transport_seq_(start_transport_seq) {}

PacketRouter::~PacketRouter() {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  RTC_DCHECK(send_modules_map_.empty());
  RTC_DCHECK(send_modules_list_.empty());
  RTC_DCHECK(rtcp_feedback_senders_.empty());
  RTC_DCHECK(sender_remb_candidates_.empty());
  RTC_DCHECK(receiver_remb_candidates_.empty());
  RTC_DCHECK(active_remb_module_ == nullptr);
}

void PacketRouter::AddSendRtpModule(RtpRtcpInterface* rtp_module,
                                    bool remb_candidate) {
  RTC_DCHECK_RUN_ON(&thread_checker_);

  AddSendRtpModuleToMap(rtp_module, rtp_module->SSRC());
  if (absl::optional<uint32_t> rtx_ssrc = rtp_module->RtxSsrc()) {
    AddSendRtpModuleToMap(rtp_module, *rtx_ssrc);
  }
  if (absl::optional<uint32_t> flexfec_ssrc = rtp_module->FlexfecSsrc()) {
    AddSendRtpModuleToMap(rtp_module, *flexfec_ssrc);
  }

  if (rtp_module->SupportsRtxPayloadPadding()) {
    last_send_module_ = rtp_module;
  }

  if (remb_candidate) {
    AddRembModuleCandidate(rtp_module, /* media_sender = */ true);
  }
}

bool PacketRouter::SupportsRtxPayloadPadding() const {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  for (RtpRtcpInterface* rtp_module : send_modules_list_) {
    if (rtp_module->SupportsRtxPayloadPadding()) {
      return true;
    }
  }
  return false;
}

void PacketRouter::AddSendRtpModuleToMap(RtpRtcpInterface* rtp_module,
                                         uint32_t ssrc) {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  RTC_CHECK(send_modules_map_.find(ssrc) == send_modules_map_.end());

  // Signal to module that the pacer thread is attached and can send packets.
  rtp_module->OnPacketSendingThreadSwitched();

  // Always keep the audio modules at the back of the list, so that when we
  // iterate over the modules in order to find one that can send padding we
  // will prioritize video. This is important to make sure they are counted
  // into the bandwidth estimate properly.
  if (rtp_module->IsAudioConfigured()) {
    send_modules_list_.push_back(rtp_module);
  } else {
    send_modules_list_.push_front(rtp_module);
  }
  send_modules_map_[ssrc] = rtp_module;
}

void PacketRouter::RemoveSendRtpModuleFromMap(uint32_t ssrc) {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  auto it = send_modules_map_.find(ssrc);
  if (it == send_modules_map_.end()) {
    RTC_LOG(LS_ERROR) << "No send module found for ssrc " << ssrc;
    return;
  }
  send_modules_list_.remove(it->second);
  RTC_CHECK(modules_used_in_current_batch_.empty());
  send_modules_map_.erase(it);
}

void PacketRouter::RemoveSendRtpModule(RtpRtcpInterface* rtp_module) {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  MaybeRemoveRembModuleCandidate(rtp_module, /* media_sender = */ true);

  RemoveSendRtpModuleFromMap(rtp_module->SSRC());
  if (absl::optional<uint32_t> rtx_ssrc = rtp_module->RtxSsrc()) {
    RemoveSendRtpModuleFromMap(*rtx_ssrc);
  }
  if (absl::optional<uint32_t> flexfec_ssrc = rtp_module->FlexfecSsrc()) {
    RemoveSendRtpModuleFromMap(*flexfec_ssrc);
  }

  if (last_send_module_ == rtp_module) {
    last_send_module_ = nullptr;
  }
  rtp_module->OnPacketSendingThreadSwitched();
}

void PacketRouter::AddReceiveRtpModule(RtcpFeedbackSenderInterface* rtcp_sender,
                                       bool remb_candidate) {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  RTC_DCHECK(std::find(rtcp_feedback_senders_.begin(),
                       rtcp_feedback_senders_.end(),
                       rtcp_sender) == rtcp_feedback_senders_.end());

  rtcp_feedback_senders_.push_back(rtcp_sender);

  if (remb_candidate) {
    AddRembModuleCandidate(rtcp_sender, /* media_sender = */ false);
  }
}

void PacketRouter::RemoveReceiveRtpModule(
    RtcpFeedbackSenderInterface* rtcp_sender) {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  MaybeRemoveRembModuleCandidate(rtcp_sender, /* media_sender = */ false);
  auto it = std::find(rtcp_feedback_senders_.begin(),
                      rtcp_feedback_senders_.end(), rtcp_sender);
  RTC_DCHECK(it != rtcp_feedback_senders_.end());
  rtcp_feedback_senders_.erase(it);
}

void PacketRouter::SendPacket(std::unique_ptr<RtpPacketToSend> packet,
                              const PacedPacketInfo& cluster_info) {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  TRACE_EVENT2(TRACE_DISABLED_BY_DEFAULT("webrtc"), "PacketRouter::SendPacket",
               "sequence_number", packet->SequenceNumber(), "rtp_timestamp",
               packet->Timestamp());

  // With the new pacer code path, transport sequence numbers are only set here,
  // on the pacer thread. Therefore we don't need atomics/synchronization.
  bool assign_transport_sequence_number =
      packet->HasExtension<TransportSequenceNumber>();
  if (assign_transport_sequence_number) {
    packet->SetExtension<TransportSequenceNumber>((transport_seq_ + 1) &
                                                  0xFFFF);
  }

  uint32_t ssrc = packet->Ssrc();
  auto it = send_modules_map_.find(ssrc);
  if (it == send_modules_map_.end()) {
    RTC_LOG(LS_WARNING)
        << "Failed to send packet, matching RTP module not found "
           "or transport error. SSRC = "
        << packet->Ssrc() << ", sequence number " << packet->SequenceNumber();
    return;
  }

  RtpRtcpInterface* rtp_module = it->second;
  if (!rtp_module->TrySendPacket(std::move(packet), cluster_info)) {
    RTC_LOG(LS_WARNING) << "Failed to send packet, rejected by RTP module.";
    return;
  }
  modules_used_in_current_batch_.insert(rtp_module);

  // Sending succeeded.

  if (assign_transport_sequence_number) {
    ++transport_seq_;
  }

  if (rtp_module->SupportsRtxPayloadPadding()) {
    // This is now the last module to send media, and has the desired
    // properties needed for payload based padding. Cache it for later use.
    last_send_module_ = rtp_module;
  }

  for (auto& packet : rtp_module->FetchFecPackets()) {
    pending_fec_packets_.push_back(std::move(packet));
  }
}

void PacketRouter::OnBatchComplete() {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  TRACE_EVENT0(TRACE_DISABLED_BY_DEFAULT("webrtc"),
               "PacketRouter::OnBatchComplete");
  for (auto& module : modules_used_in_current_batch_) {
    module->OnBatchComplete();
  }
  modules_used_in_current_batch_.clear();
}

std::vector<std::unique_ptr<RtpPacketToSend>> PacketRouter::FetchFec() {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  std::vector<std::unique_ptr<RtpPacketToSend>> fec_packets =
      std::move(pending_fec_packets_);
  pending_fec_packets_.clear();
  return fec_packets;
}

std::vector<std::unique_ptr<RtpPacketToSend>> PacketRouter::GeneratePadding(
    DataSize size) {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  TRACE_EVENT1(TRACE_DISABLED_BY_DEFAULT("webrtc"),
               "PacketRouter::GeneratePadding", "bytes", size.bytes());

  // First try on the last rtp module to have sent media. This increases the
  // the chance that any payload based padding will be useful as it will be
  // somewhat distributed over modules according the packet rate, even if it
  // will be more skewed towards the highest bitrate stream. At the very least
  // this prevents sending payload padding on a disabled stream where it's
  // guaranteed not to be useful.
  std::vector<std::unique_ptr<RtpPacketToSend>> padding_packets;
  if (last_send_module_ != nullptr &&
      last_send_module_->SupportsRtxPayloadPadding()) {
    padding_packets = last_send_module_->GeneratePadding(size.bytes());
  }

  if (padding_packets.empty()) {
    // Iterate over all modules send module. Video modules will be at the front
    // and so will be prioritized. This is important since audio packets may not
    // be taken into account by the bandwidth estimator, e.g. in FF.
    for (RtpRtcpInterface* rtp_module : send_modules_list_) {
      if (rtp_module->SupportsPadding()) {
        padding_packets = rtp_module->GeneratePadding(size.bytes());
        if (!padding_packets.empty()) {
          last_send_module_ = rtp_module;
          break;
        }
      }
    }
  }

  for (auto& packet : padding_packets) {
    RTC_UNUSED(packet);
    TRACE_EVENT2(TRACE_DISABLED_BY_DEFAULT("webrtc"),
                 "PacketRouter::GeneratePadding::Loop", "sequence_number",
                 packet->SequenceNumber(), "rtp_timestamp",
                 packet->Timestamp());
  }

  return padding_packets;
}

void PacketRouter::OnAbortedRetransmissions(
    uint32_t ssrc,
    rtc::ArrayView<const uint16_t> sequence_numbers) {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  auto it = send_modules_map_.find(ssrc);
  if (it != send_modules_map_.end()) {
    it->second->OnAbortedRetransmissions(sequence_numbers);
  }
}

absl::optional<uint32_t> PacketRouter::GetRtxSsrcForMedia(uint32_t ssrc) const {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  auto it = send_modules_map_.find(ssrc);
  if (it != send_modules_map_.end() && it->second->SSRC() == ssrc) {
    // A module is registered with the given SSRC, and that SSRC is the main
    // media SSRC for that RTP module.
    return it->second->RtxSsrc();
  }
  return absl::nullopt;
}

uint16_t PacketRouter::CurrentTransportSequenceNumber() const {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  return transport_seq_ & 0xFFFF;
}

void PacketRouter::SendRemb(int64_t bitrate_bps, std::vector<uint32_t> ssrcs) {
  RTC_DCHECK_RUN_ON(&thread_checker_);

  if (!active_remb_module_) {
    return;
  }

  // The Add* and Remove* methods above ensure that REMB is disabled on all
  // other modules, because otherwise, they will send REMB with stale info.
  active_remb_module_->SetRemb(bitrate_bps, std::move(ssrcs));
}

void PacketRouter::SendCombinedRtcpPacket(
    std::vector<std::unique_ptr<rtcp::RtcpPacket>> packets) {
  RTC_DCHECK_RUN_ON(&thread_checker_);

  // Prefer send modules.
  for (RtpRtcpInterface* rtp_module : send_modules_list_) {
    if (rtp_module->RTCP() == RtcpMode::kOff) {
      continue;
    }
    rtp_module->SendCombinedRtcpPacket(std::move(packets));
    return;
  }

  if (rtcp_feedback_senders_.empty()) {
    return;
  }
  auto* rtcp_sender = rtcp_feedback_senders_[0];
  rtcp_sender->SendCombinedRtcpPacket(std::move(packets));
}

void PacketRouter::AddRembModuleCandidate(
    RtcpFeedbackSenderInterface* candidate_module,
    bool media_sender) {
  RTC_DCHECK(candidate_module);
  std::vector<RtcpFeedbackSenderInterface*>& candidates =
      media_sender ? sender_remb_candidates_ : receiver_remb_candidates_;
  RTC_DCHECK(std::find(candidates.cbegin(), candidates.cend(),
                       candidate_module) == candidates.cend());
  candidates.push_back(candidate_module);
  DetermineActiveRembModule();
}

void PacketRouter::MaybeRemoveRembModuleCandidate(
    RtcpFeedbackSenderInterface* candidate_module,
    bool media_sender) {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  RTC_DCHECK(candidate_module);
  std::vector<RtcpFeedbackSenderInterface*>& candidates =
      media_sender ? sender_remb_candidates_ : receiver_remb_candidates_;
  auto it = std::find(candidates.begin(), candidates.end(), candidate_module);

  if (it == candidates.end()) {
    return;  // Function called due to removal of non-REMB-candidate module.
  }

  if (*it == active_remb_module_) {
    UnsetActiveRembModule();
  }
  candidates.erase(it);
  DetermineActiveRembModule();
}

void PacketRouter::UnsetActiveRembModule() {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  RTC_CHECK(active_remb_module_);
  active_remb_module_->UnsetRemb();
  active_remb_module_ = nullptr;
}

void PacketRouter::DetermineActiveRembModule() {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  // Sender modules take precedence over receiver modules, because SRs (sender
  // reports) are sent more frequently than RR (receiver reports).
  // When adding the first sender module, we should change the active REMB
  // module to be that. Otherwise, we remain with the current active module.

  RtcpFeedbackSenderInterface* new_active_remb_module;

  if (!sender_remb_candidates_.empty()) {
    new_active_remb_module = sender_remb_candidates_.front();
  } else if (!receiver_remb_candidates_.empty()) {
    new_active_remb_module = receiver_remb_candidates_.front();
  } else {
    new_active_remb_module = nullptr;
  }

  if (new_active_remb_module != active_remb_module_ && active_remb_module_) {
    UnsetActiveRembModule();
  }

  active_remb_module_ = new_active_remb_module;
}

}  // namespace webrtc
