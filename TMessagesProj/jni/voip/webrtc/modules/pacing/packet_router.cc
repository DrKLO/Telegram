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
#include "modules/rtp_rtcp/source/rtcp_packet/transport_feedback.h"
#include "modules/rtp_rtcp/source/rtp_rtcp_interface.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/time_utils.h"
#include "rtc_base/trace_event.h"

namespace webrtc {

PacketRouter::PacketRouter() : PacketRouter(0) {}

PacketRouter::PacketRouter(uint16_t start_transport_seq)
    : last_send_module_(nullptr),
      active_remb_module_(nullptr),
      transport_seq_(start_transport_seq) {}

PacketRouter::~PacketRouter() {
  RTC_DCHECK(send_modules_map_.empty());
  RTC_DCHECK(send_modules_list_.empty());
  RTC_DCHECK(rtcp_feedback_senders_.empty());
  RTC_DCHECK(sender_remb_candidates_.empty());
  RTC_DCHECK(receiver_remb_candidates_.empty());
  RTC_DCHECK(active_remb_module_ == nullptr);
}

void PacketRouter::AddSendRtpModule(RtpRtcpInterface* rtp_module,
                                    bool remb_candidate) {
  MutexLock lock(&modules_mutex_);

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

void PacketRouter::AddSendRtpModuleToMap(RtpRtcpInterface* rtp_module,
                                         uint32_t ssrc) {
  RTC_DCHECK(send_modules_map_.find(ssrc) == send_modules_map_.end());

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
  auto kv = send_modules_map_.find(ssrc);
  RTC_DCHECK(kv != send_modules_map_.end());
  send_modules_list_.remove(kv->second);
  send_modules_map_.erase(kv);
}

void PacketRouter::RemoveSendRtpModule(RtpRtcpInterface* rtp_module) {
  MutexLock lock(&modules_mutex_);
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
  MutexLock lock(&modules_mutex_);
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
  MutexLock lock(&modules_mutex_);
  MaybeRemoveRembModuleCandidate(rtcp_sender, /* media_sender = */ false);
  auto it = std::find(rtcp_feedback_senders_.begin(),
                      rtcp_feedback_senders_.end(), rtcp_sender);
  RTC_DCHECK(it != rtcp_feedback_senders_.end());
  rtcp_feedback_senders_.erase(it);
}

void PacketRouter::SendPacket(std::unique_ptr<RtpPacketToSend> packet,
                              const PacedPacketInfo& cluster_info) {
  TRACE_EVENT2(TRACE_DISABLED_BY_DEFAULT("webrtc"), "PacketRouter::SendPacket",
               "sequence_number", packet->SequenceNumber(), "rtp_timestamp",
               packet->Timestamp());

  MutexLock lock(&modules_mutex_);
  // With the new pacer code path, transport sequence numbers are only set here,
  // on the pacer thread. Therefore we don't need atomics/synchronization.
  if (packet->HasExtension<TransportSequenceNumber>()) {
    packet->SetExtension<TransportSequenceNumber>((++transport_seq_) & 0xFFFF);
  }

  uint32_t ssrc = packet->Ssrc();
  auto kv = send_modules_map_.find(ssrc);
  if (kv == send_modules_map_.end()) {
    RTC_LOG(LS_WARNING)
        << "Failed to send packet, matching RTP module not found "
           "or transport error. SSRC = "
        << packet->Ssrc() << ", sequence number " << packet->SequenceNumber();
    return;
  }

  RtpRtcpInterface* rtp_module = kv->second;
  if (!rtp_module->TrySendPacket(packet.get(), cluster_info)) {
    RTC_LOG(LS_WARNING) << "Failed to send packet, rejected by RTP module.";
    return;
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

std::vector<std::unique_ptr<RtpPacketToSend>> PacketRouter::FetchFec() {
  MutexLock lock(&modules_mutex_);
  std::vector<std::unique_ptr<RtpPacketToSend>> fec_packets =
      std::move(pending_fec_packets_);
  pending_fec_packets_.clear();
  return fec_packets;
}

std::vector<std::unique_ptr<RtpPacketToSend>> PacketRouter::GeneratePadding(
    DataSize size) {
  TRACE_EVENT1(TRACE_DISABLED_BY_DEFAULT("webrtc"),
               "PacketRouter::GeneratePadding", "bytes", size.bytes());

  MutexLock lock(&modules_mutex_);
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

#if RTC_TRACE_EVENTS_ENABLED
  for (auto& packet : padding_packets) {
    TRACE_EVENT2(TRACE_DISABLED_BY_DEFAULT("webrtc"),
                 "PacketRouter::GeneratePadding::Loop", "sequence_number",
                 packet->SequenceNumber(), "rtp_timestamp",
                 packet->Timestamp());
  }
#endif

  return padding_packets;
}

uint16_t PacketRouter::CurrentTransportSequenceNumber() const {
  MutexLock lock(&modules_mutex_);
  return transport_seq_ & 0xFFFF;
}

void PacketRouter::SendRemb(int64_t bitrate_bps, std::vector<uint32_t> ssrcs) {
  MutexLock lock(&modules_mutex_);

  if (!active_remb_module_) {
    return;
  }

  // The Add* and Remove* methods above ensure that REMB is disabled on all
  // other modules, because otherwise, they will send REMB with stale info.
  active_remb_module_->SetRemb(bitrate_bps, std::move(ssrcs));
}

void PacketRouter::SendCombinedRtcpPacket(
    std::vector<std::unique_ptr<rtcp::RtcpPacket>> packets) {
  MutexLock lock(&modules_mutex_);

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
  RTC_CHECK(active_remb_module_);
  active_remb_module_->UnsetRemb();
  active_remb_module_ = nullptr;
}

void PacketRouter::DetermineActiveRembModule() {
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
