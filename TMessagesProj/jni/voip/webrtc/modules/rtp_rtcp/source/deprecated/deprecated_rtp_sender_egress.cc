/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/deprecated/deprecated_rtp_sender_egress.h"

#include <limits>
#include <memory>
#include <utility>

#include "absl/strings/match.h"
#include "api/units/timestamp.h"
#include "logging/rtc_event_log/events/rtc_event_rtp_packet_outgoing.h"
#include "modules/remote_bitrate_estimator/test/bwe_test_logging.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace {
constexpr uint32_t kTimestampTicksPerMs = 90;
constexpr TimeDelta kBitrateStatisticsWindow = TimeDelta::Seconds(1);
constexpr size_t kRtpSequenceNumberMapMaxEntries = 1 << 13;

}  // namespace

DEPRECATED_RtpSenderEgress::NonPacedPacketSender::NonPacedPacketSender(
    DEPRECATED_RtpSenderEgress* sender,
    PacketSequencer* sequence_number_assigner)
    : transport_sequence_number_(0),
      sender_(sender),
      sequence_number_assigner_(sequence_number_assigner) {
  RTC_DCHECK(sequence_number_assigner_);
}
DEPRECATED_RtpSenderEgress::NonPacedPacketSender::~NonPacedPacketSender() =
    default;

void DEPRECATED_RtpSenderEgress::NonPacedPacketSender::EnqueuePackets(
    std::vector<std::unique_ptr<RtpPacketToSend>> packets) {
  for (auto& packet : packets) {
    // Assign sequence numbers, but not for flexfec which is already running on
    // an internally maintained sequence number series.
    if (packet->Ssrc() != sender_->FlexFecSsrc()) {
      sequence_number_assigner_->Sequence(*packet);
    }
    if (!packet->SetExtension<TransportSequenceNumber>(
            ++transport_sequence_number_)) {
      --transport_sequence_number_;
    }
    packet->ReserveExtension<TransmissionOffset>();
    packet->ReserveExtension<AbsoluteSendTime>();
    sender_->SendPacket(packet.get(), PacedPacketInfo());
  }
}

DEPRECATED_RtpSenderEgress::DEPRECATED_RtpSenderEgress(
    const RtpRtcpInterface::Configuration& config,
    RtpPacketHistory* packet_history)
    : ssrc_(config.local_media_ssrc),
      rtx_ssrc_(config.rtx_send_ssrc),
      flexfec_ssrc_(config.fec_generator ? config.fec_generator->FecSsrc()
                                         : absl::nullopt),
      populate_network2_timestamp_(config.populate_network2_timestamp),
      clock_(config.clock),
      packet_history_(packet_history),
      transport_(config.outgoing_transport),
      event_log_(config.event_log),
      is_audio_(config.audio),
      need_rtp_packet_infos_(config.need_rtp_packet_infos),
      transport_feedback_observer_(config.transport_feedback_callback),
      send_packet_observer_(config.send_packet_observer),
      rtp_stats_callback_(config.rtp_stats_callback),
      bitrate_callback_(config.send_bitrate_observer),
      media_has_been_sent_(false),
      force_part_of_allocation_(false),
      timestamp_offset_(0),
      send_rates_(kNumMediaTypes, BitrateTracker(kBitrateStatisticsWindow)),
      rtp_sequence_number_map_(need_rtp_packet_infos_
                                   ? std::make_unique<RtpSequenceNumberMap>(
                                         kRtpSequenceNumberMapMaxEntries)
                                   : nullptr) {}

void DEPRECATED_RtpSenderEgress::SendPacket(
    RtpPacketToSend* packet,
    const PacedPacketInfo& pacing_info) {
  RTC_DCHECK(packet);

  const uint32_t packet_ssrc = packet->Ssrc();
  RTC_DCHECK(packet->packet_type().has_value());
  RTC_DCHECK(HasCorrectSsrc(*packet));
  Timestamp now = clock_->CurrentTime();
  int64_t now_ms = now.ms();

  if (is_audio_) {
#if BWE_TEST_LOGGING_COMPILE_TIME_ENABLE
    BWE_TEST_LOGGING_PLOT_WITH_SSRC(1, "AudioTotBitrate_kbps", now_ms,
                                    GetSendRates().Sum().kbps(), packet_ssrc);
    BWE_TEST_LOGGING_PLOT_WITH_SSRC(
        1, "AudioNackBitrate_kbps", now_ms,
        GetSendRates()[RtpPacketMediaType::kRetransmission].kbps(),
        packet_ssrc);
#endif
  } else {
#if BWE_TEST_LOGGING_COMPILE_TIME_ENABLE
    BWE_TEST_LOGGING_PLOT_WITH_SSRC(1, "VideoTotBitrate_kbps", now_ms,
                                    GetSendRates().Sum().kbps(), packet_ssrc);
    BWE_TEST_LOGGING_PLOT_WITH_SSRC(
        1, "VideoNackBitrate_kbps", now_ms,
        GetSendRates()[RtpPacketMediaType::kRetransmission].kbps(),
        packet_ssrc);
#endif
  }

  PacketOptions options;
  {
    MutexLock lock(&lock_);
    options.included_in_allocation = force_part_of_allocation_;

    if (need_rtp_packet_infos_ &&
        packet->packet_type() == RtpPacketToSend::Type::kVideo) {
      RTC_DCHECK(rtp_sequence_number_map_);
      // Last packet of a frame, add it to sequence number info map.
      const uint32_t timestamp = packet->Timestamp() - timestamp_offset_;
      bool is_first_packet_of_frame = packet->is_first_packet_of_frame();
      bool is_last_packet_of_frame = packet->Marker();

      rtp_sequence_number_map_->InsertPacket(
          packet->SequenceNumber(),
          RtpSequenceNumberMap::Info(timestamp, is_first_packet_of_frame,
                                     is_last_packet_of_frame));
    }
  }

  // Bug webrtc:7859. While FEC is invoked from rtp_sender_video, and not after
  // the pacer, these modifications of the header below are happening after the
  // FEC protection packets are calculated. This will corrupt recovered packets
  // at the same place. It's not an issue for extensions, which are present in
  // all the packets (their content just may be incorrect on recovered packets).
  // In case of VideoTimingExtension, since it's present not in every packet,
  // data after rtp header may be corrupted if these packets are protected by
  // the FEC.
  int64_t diff_ms = now_ms - packet->capture_time().ms();
  if (packet->HasExtension<TransmissionOffset>()) {
    packet->SetExtension<TransmissionOffset>(kTimestampTicksPerMs * diff_ms);
  }
  if (packet->HasExtension<AbsoluteSendTime>()) {
    packet->SetExtension<AbsoluteSendTime>(AbsoluteSendTime::To24Bits(now));
  }

  if (packet->HasExtension<VideoTimingExtension>()) {
    if (populate_network2_timestamp_) {
      packet->set_network2_time(now);
    } else {
      packet->set_pacer_exit_time(now);
    }
  }

  const bool is_media = packet->packet_type() == RtpPacketMediaType::kAudio ||
                        packet->packet_type() == RtpPacketMediaType::kVideo;

  // Downstream code actually uses this flag to distinguish between media and
  // everything else.
  options.is_retransmit = !is_media;
  if (auto packet_id = packet->GetExtension<TransportSequenceNumber>()) {
    options.packet_id = *packet_id;
    options.included_in_feedback = true;
    options.included_in_allocation = true;
    AddPacketToTransportFeedback(*packet_id, *packet, pacing_info);
  }

  options.additional_data = packet->additional_data();

  if (packet->packet_type() != RtpPacketMediaType::kPadding &&
      packet->packet_type() != RtpPacketMediaType::kRetransmission) {
    UpdateOnSendPacket(options.packet_id, packet->capture_time().ms(),
                       packet_ssrc);
  }

  const bool send_success = SendPacketToNetwork(*packet, options, pacing_info);

  // Put packet in retransmission history or update pending status even if
  // actual sending fails.
  if (is_media && packet->allow_retransmission()) {
    packet_history_->PutRtpPacket(std::make_unique<RtpPacketToSend>(*packet),
                                  now);
  } else if (packet->retransmitted_sequence_number()) {
    packet_history_->MarkPacketAsSent(*packet->retransmitted_sequence_number());
  }

  if (send_success) {
    MutexLock lock(&lock_);
    UpdateRtpStats(*packet);
    media_has_been_sent_ = true;
  }
}

void DEPRECATED_RtpSenderEgress::ProcessBitrateAndNotifyObservers() {
  if (!bitrate_callback_)
    return;

  MutexLock lock(&lock_);
  RtpSendRates send_rates = GetSendRatesLocked();
  bitrate_callback_->Notify(
      send_rates.Sum().bps(),
      send_rates[RtpPacketMediaType::kRetransmission].bps(), ssrc_);
}

RtpSendRates DEPRECATED_RtpSenderEgress::GetSendRates() const {
  MutexLock lock(&lock_);
  return GetSendRatesLocked();
}

RtpSendRates DEPRECATED_RtpSenderEgress::GetSendRatesLocked() const {
  const Timestamp now = clock_->CurrentTime();
  RtpSendRates current_rates;
  for (size_t i = 0; i < kNumMediaTypes; ++i) {
    RtpPacketMediaType type = static_cast<RtpPacketMediaType>(i);
    current_rates[type] = send_rates_[i].Rate(now).value_or(DataRate::Zero());
  }
  return current_rates;
}

void DEPRECATED_RtpSenderEgress::GetDataCounters(
    StreamDataCounters* rtp_stats,
    StreamDataCounters* rtx_stats) const {
  MutexLock lock(&lock_);
  *rtp_stats = rtp_stats_;
  *rtx_stats = rtx_rtp_stats_;
}

void DEPRECATED_RtpSenderEgress::ForceIncludeSendPacketsInAllocation(
    bool part_of_allocation) {
  MutexLock lock(&lock_);
  force_part_of_allocation_ = part_of_allocation;
}

bool DEPRECATED_RtpSenderEgress::MediaHasBeenSent() const {
  MutexLock lock(&lock_);
  return media_has_been_sent_;
}

void DEPRECATED_RtpSenderEgress::SetMediaHasBeenSent(bool media_sent) {
  MutexLock lock(&lock_);
  media_has_been_sent_ = media_sent;
}

void DEPRECATED_RtpSenderEgress::SetTimestampOffset(uint32_t timestamp) {
  MutexLock lock(&lock_);
  timestamp_offset_ = timestamp;
}

std::vector<RtpSequenceNumberMap::Info>
DEPRECATED_RtpSenderEgress::GetSentRtpPacketInfos(
    rtc::ArrayView<const uint16_t> sequence_numbers) const {
  RTC_DCHECK(!sequence_numbers.empty());
  if (!need_rtp_packet_infos_) {
    return std::vector<RtpSequenceNumberMap::Info>();
  }

  std::vector<RtpSequenceNumberMap::Info> results;
  results.reserve(sequence_numbers.size());

  MutexLock lock(&lock_);
  for (uint16_t sequence_number : sequence_numbers) {
    const auto& info = rtp_sequence_number_map_->Get(sequence_number);
    if (!info) {
      // The empty vector will be returned. We can delay the clearing
      // of the vector until after we exit the critical section.
      return std::vector<RtpSequenceNumberMap::Info>();
    }
    results.push_back(*info);
  }

  return results;
}

bool DEPRECATED_RtpSenderEgress::HasCorrectSsrc(
    const RtpPacketToSend& packet) const {
  switch (*packet.packet_type()) {
    case RtpPacketMediaType::kAudio:
    case RtpPacketMediaType::kVideo:
      return packet.Ssrc() == ssrc_;
    case RtpPacketMediaType::kRetransmission:
    case RtpPacketMediaType::kPadding:
      // Both padding and retransmission must be on either the media or the
      // RTX stream.
      return packet.Ssrc() == rtx_ssrc_ || packet.Ssrc() == ssrc_;
    case RtpPacketMediaType::kForwardErrorCorrection:
      // FlexFEC is on separate SSRC, ULPFEC uses media SSRC.
      return packet.Ssrc() == ssrc_ || packet.Ssrc() == flexfec_ssrc_;
  }
  return false;
}

void DEPRECATED_RtpSenderEgress::AddPacketToTransportFeedback(
    uint16_t packet_id,
    const RtpPacketToSend& packet,
    const PacedPacketInfo& pacing_info) {
  if (transport_feedback_observer_) {
    RtpPacketSendInfo packet_info;
    packet_info.media_ssrc = ssrc_;
    packet_info.transport_sequence_number = packet_id;
    packet_info.rtp_sequence_number = packet.SequenceNumber();
    packet_info.length = packet.size();
    packet_info.pacing_info = pacing_info;
    packet_info.packet_type = packet.packet_type();
    transport_feedback_observer_->OnAddPacket(packet_info);
  }
}

void DEPRECATED_RtpSenderEgress::UpdateOnSendPacket(int packet_id,
                                                    int64_t capture_time_ms,
                                                    uint32_t ssrc) {
  if (!send_packet_observer_ || capture_time_ms <= 0 || packet_id == -1) {
    return;
  }

  send_packet_observer_->OnSendPacket(packet_id,
                                      Timestamp::Millis(capture_time_ms), ssrc);
}

bool DEPRECATED_RtpSenderEgress::SendPacketToNetwork(
    const RtpPacketToSend& packet,
    const PacketOptions& options,
    const PacedPacketInfo& pacing_info) {
  int bytes_sent = -1;
  if (transport_) {
    bytes_sent = transport_->SendRtp(packet, options)
                     ? static_cast<int>(packet.size())
                     : -1;
    if (event_log_ && bytes_sent > 0) {
      event_log_->Log(std::make_unique<RtcEventRtpPacketOutgoing>(
          packet, pacing_info.probe_cluster_id));
    }
  }

  if (bytes_sent <= 0) {
    RTC_LOG(LS_WARNING) << "Transport failed to send packet.";
    return false;
  }
  return true;
}

void DEPRECATED_RtpSenderEgress::UpdateRtpStats(const RtpPacketToSend& packet) {
  Timestamp now = clock_->CurrentTime();

  StreamDataCounters* counters =
      packet.Ssrc() == rtx_ssrc_ ? &rtx_rtp_stats_ : &rtp_stats_;

  counters->MaybeSetFirstPacketTime(now);

  if (packet.packet_type() == RtpPacketMediaType::kForwardErrorCorrection) {
    counters->fec.AddPacket(packet);
  }

  if (packet.packet_type() == RtpPacketMediaType::kRetransmission) {
    counters->retransmitted.AddPacket(packet);
  }
  counters->transmitted.AddPacket(packet);

  RTC_DCHECK(packet.packet_type().has_value());
  send_rates_[static_cast<size_t>(*packet.packet_type())].Update(packet.size(),
                                                                 now);

  if (rtp_stats_callback_) {
    rtp_stats_callback_->DataCountersUpdated(*counters, packet.Ssrc());
  }
}

}  // namespace webrtc
