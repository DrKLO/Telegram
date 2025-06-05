/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtp_sender.h"

#include <algorithm>
#include <limits>
#include <memory>
#include <string>
#include <utility>

#include "absl/strings/match.h"
#include "absl/strings/string_view.h"
#include "api/array_view.h"
#include "api/rtc_event_log/rtc_event_log.h"
#include "logging/rtc_event_log/events/rtc_event_rtp_packet_outgoing.h"
#include "modules/rtp_rtcp/include/rtp_cvo.h"
#include "modules/rtp_rtcp/source/byte_io.h"
#include "modules/rtp_rtcp/source/rtp_generic_frame_descriptor_extension.h"
#include "modules/rtp_rtcp/source/rtp_header_extensions.h"
#include "modules/rtp_rtcp/source/rtp_packet_to_send.h"
#include "modules/rtp_rtcp/source/time_util.h"
#include "rtc_base/arraysize.h"
#include "rtc_base/checks.h"
#include "rtc_base/experiments/field_trial_parser.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_minmax.h"
#include "rtc_base/rate_limiter.h"
#include "rtc_base/time_utils.h"

namespace webrtc {

namespace {
constexpr size_t kMinAudioPaddingLength = 50;
constexpr size_t kRtpHeaderLength = 12;

// Min size needed to get payload padding from packet history.
constexpr int kMinPayloadPaddingBytes = 50;

// Determines how much larger a payload padding packet may be, compared to the
// requested padding size.
constexpr double kMaxPaddingSizeFactor = 3.0;

template <typename Extension>
constexpr RtpExtensionSize CreateExtensionSize() {
  return {Extension::kId, Extension::kValueSizeBytes};
}

template <typename Extension>
constexpr RtpExtensionSize CreateMaxExtensionSize() {
  return {Extension::kId, Extension::kMaxValueSizeBytes};
}

// Size info for header extensions that might be used in padding or FEC packets.
constexpr RtpExtensionSize kFecOrPaddingExtensionSizes[] = {
    CreateExtensionSize<AbsoluteSendTime>(),
    CreateExtensionSize<TransmissionOffset>(),
    CreateExtensionSize<TransportSequenceNumber>(),
    CreateExtensionSize<PlayoutDelayLimits>(),
    CreateMaxExtensionSize<RtpMid>(),
    CreateExtensionSize<VideoTimingExtension>(),
};

// Size info for header extensions that might be used in video packets.
constexpr RtpExtensionSize kVideoExtensionSizes[] = {
    CreateExtensionSize<AbsoluteSendTime>(),
    CreateExtensionSize<AbsoluteCaptureTimeExtension>(),
    CreateExtensionSize<TransmissionOffset>(),
    CreateExtensionSize<TransportSequenceNumber>(),
    CreateExtensionSize<PlayoutDelayLimits>(),
    CreateExtensionSize<VideoOrientation>(),
    CreateExtensionSize<VideoContentTypeExtension>(),
    CreateExtensionSize<VideoTimingExtension>(),
    CreateMaxExtensionSize<RtpStreamId>(),
    CreateMaxExtensionSize<RepairedRtpStreamId>(),
    CreateMaxExtensionSize<RtpMid>(),
    {RtpGenericFrameDescriptorExtension00::kId,
     RtpGenericFrameDescriptorExtension00::kMaxSizeBytes},
};

// Size info for header extensions that might be used in audio packets.
constexpr RtpExtensionSize kAudioExtensionSizes[] = {
    CreateExtensionSize<AbsoluteSendTime>(),
    CreateExtensionSize<AbsoluteCaptureTimeExtension>(),
    CreateExtensionSize<AudioLevel>(),
    CreateExtensionSize<InbandComfortNoiseExtension>(),
    CreateExtensionSize<TransmissionOffset>(),
    CreateExtensionSize<TransportSequenceNumber>(),
    CreateMaxExtensionSize<RtpMid>(),
};

// Non-volatile extensions can be expected on all packets, if registered.
// Volatile ones, such as VideoContentTypeExtension which is only set on
// key-frames, are removed to simplify overhead calculations at the expense of
// some accuracy.
bool IsNonVolatile(RTPExtensionType type) {
  switch (type) {
    case kRtpExtensionTransmissionTimeOffset:
    case kRtpExtensionAudioLevel:
    case kRtpExtensionCsrcAudioLevel:
    case kRtpExtensionAbsoluteSendTime:
    case kRtpExtensionTransportSequenceNumber:
    case kRtpExtensionTransportSequenceNumber02:
    case kRtpExtensionRtpStreamId:
    case kRtpExtensionRepairedRtpStreamId:
    case kRtpExtensionMid:
    case kRtpExtensionGenericFrameDescriptor:
    case kRtpExtensionDependencyDescriptor:
      return true;
    case kRtpExtensionInbandComfortNoise:
    case kRtpExtensionAbsoluteCaptureTime:
    case kRtpExtensionVideoRotation:
    case kRtpExtensionPlayoutDelay:
    case kRtpExtensionVideoContentType:
    case kRtpExtensionVideoLayersAllocation:
    case kRtpExtensionVideoTiming:
    case kRtpExtensionColorSpace:
    case kRtpExtensionVideoFrameTrackingId:
      return false;
    case kRtpExtensionNone:
    case kRtpExtensionNumberOfExtensions:
      RTC_DCHECK_NOTREACHED();
      return false;
  }
  RTC_CHECK_NOTREACHED();
}

bool HasBweExtension(const RtpHeaderExtensionMap& extensions_map) {
  return extensions_map.IsRegistered(kRtpExtensionTransportSequenceNumber) ||
         extensions_map.IsRegistered(kRtpExtensionTransportSequenceNumber02) ||
         extensions_map.IsRegistered(kRtpExtensionAbsoluteSendTime) ||
         extensions_map.IsRegistered(kRtpExtensionTransmissionTimeOffset);
}

}  // namespace

RTPSender::RTPSender(const RtpRtcpInterface::Configuration& config,
                     RtpPacketHistory* packet_history,
                     RtpPacketSender* packet_sender)
    : clock_(config.clock),
      random_(clock_->TimeInMicroseconds()),
      audio_configured_(config.audio),
      ssrc_(config.local_media_ssrc),
      rtx_ssrc_(config.rtx_send_ssrc),
      flexfec_ssrc_(config.fec_generator ? config.fec_generator->FecSsrc()
                                         : absl::nullopt),
      packet_history_(packet_history),
      paced_sender_(packet_sender),
      sending_media_(true),                   // Default to sending media.
      max_packet_size_(IP_PACKET_SIZE - 28),  // Default is IP-v4/UDP.
      rtp_header_extension_map_(config.extmap_allow_mixed),
      // RTP variables
      rid_(config.rid),
      always_send_mid_and_rid_(config.always_send_mid_and_rid),
      ssrc_has_acked_(false),
      rtx_ssrc_has_acked_(false),
      rtx_(kRtxOff),
      supports_bwe_extension_(false),
      retransmission_rate_limiter_(config.retransmission_rate_limiter) {
  // This random initialization is not intended to be cryptographic strong.
  timestamp_offset_ = random_.Rand<uint32_t>();

  RTC_DCHECK(paced_sender_);
  RTC_DCHECK(packet_history_);
  RTC_DCHECK_LE(rid_.size(), RtpStreamId::kMaxValueSizeBytes);

  UpdateHeaderSizes();
}

RTPSender::~RTPSender() {
  // TODO(tommi): Use a thread checker to ensure the object is created and
  // deleted on the same thread.  At the moment this isn't possible due to
  // voe::ChannelOwner in voice engine.  To reproduce, run:
  // voe_auto_test --automated --gtest_filter=*MixManyChannelsForStressOpus

  // TODO(tommi,holmer): We don't grab locks in the dtor before accessing member
  // variables but we grab them in all other methods. (what's the design?)
  // Start documenting what thread we're on in what method so that it's easier
  // to understand performance attributes and possibly remove locks.
}

rtc::ArrayView<const RtpExtensionSize> RTPSender::FecExtensionSizes() {
  return rtc::MakeArrayView(kFecOrPaddingExtensionSizes,
                            arraysize(kFecOrPaddingExtensionSizes));
}

rtc::ArrayView<const RtpExtensionSize> RTPSender::VideoExtensionSizes() {
  return rtc::MakeArrayView(kVideoExtensionSizes,
                            arraysize(kVideoExtensionSizes));
}

rtc::ArrayView<const RtpExtensionSize> RTPSender::AudioExtensionSizes() {
  return rtc::MakeArrayView(kAudioExtensionSizes,
                            arraysize(kAudioExtensionSizes));
}

void RTPSender::SetExtmapAllowMixed(bool extmap_allow_mixed) {
  MutexLock lock(&send_mutex_);
  rtp_header_extension_map_.SetExtmapAllowMixed(extmap_allow_mixed);
}

bool RTPSender::RegisterRtpHeaderExtension(absl::string_view uri, int id) {
  MutexLock lock(&send_mutex_);
  bool registered = rtp_header_extension_map_.RegisterByUri(id, uri);
  supports_bwe_extension_ = HasBweExtension(rtp_header_extension_map_);
  UpdateHeaderSizes();
  return registered;
}

bool RTPSender::IsRtpHeaderExtensionRegistered(RTPExtensionType type) const {
  MutexLock lock(&send_mutex_);
  return rtp_header_extension_map_.IsRegistered(type);
}

void RTPSender::DeregisterRtpHeaderExtension(absl::string_view uri) {
  MutexLock lock(&send_mutex_);
  rtp_header_extension_map_.Deregister(uri);
  supports_bwe_extension_ = HasBweExtension(rtp_header_extension_map_);
  UpdateHeaderSizes();
}

void RTPSender::SetMaxRtpPacketSize(size_t max_packet_size) {
  RTC_DCHECK_GE(max_packet_size, 100);
  RTC_DCHECK_LE(max_packet_size, IP_PACKET_SIZE);
  MutexLock lock(&send_mutex_);
  max_packet_size_ = max_packet_size;
}

size_t RTPSender::MaxRtpPacketSize() const {
  return max_packet_size_;
}

void RTPSender::SetRtxStatus(int mode) {
  MutexLock lock(&send_mutex_);
  if (mode != kRtxOff &&
      (!rtx_ssrc_.has_value() || rtx_payload_type_map_.empty())) {
    RTC_LOG(LS_ERROR)
        << "Failed to enable RTX without RTX SSRC or payload types.";
    return;
  }
  rtx_ = mode;
}

int RTPSender::RtxStatus() const {
  MutexLock lock(&send_mutex_);
  return rtx_;
}

void RTPSender::SetRtxPayloadType(int payload_type,
                                  int associated_payload_type) {
  MutexLock lock(&send_mutex_);
  RTC_DCHECK_LE(payload_type, 127);
  RTC_DCHECK_LE(associated_payload_type, 127);
  if (payload_type < 0) {
    RTC_LOG(LS_ERROR) << "Invalid RTX payload type: " << payload_type << ".";
    return;
  }

  rtx_payload_type_map_[associated_payload_type] = payload_type;
}

int32_t RTPSender::ReSendPacket(uint16_t packet_id) {
  int32_t packet_size = 0;
  const bool rtx = (RtxStatus() & kRtxRetransmitted) > 0;

  std::unique_ptr<RtpPacketToSend> packet =
      packet_history_->GetPacketAndMarkAsPending(
          packet_id, [&](const RtpPacketToSend& stored_packet) {
            // Check if we're overusing retransmission bitrate.
            // TODO(sprang): Add histograms for nack success or failure
            // reasons.
            packet_size = stored_packet.size();
            std::unique_ptr<RtpPacketToSend> retransmit_packet;
            if (retransmission_rate_limiter_ &&
                !retransmission_rate_limiter_->TryUseRate(packet_size)) {
              return retransmit_packet;
            }
            if (rtx) {
              retransmit_packet = BuildRtxPacket(stored_packet);
            } else {
              retransmit_packet =
                  std::make_unique<RtpPacketToSend>(stored_packet);
            }
            if (retransmit_packet) {
              retransmit_packet->set_retransmitted_sequence_number(
                  stored_packet.SequenceNumber());
            }
            return retransmit_packet;
          });
  if (packet_size == 0) {
    // Packet not found or already queued for retransmission, ignore.
    RTC_DCHECK(!packet);
    return 0;
  }
  if (!packet) {
    // Packet was found, but lambda helper above chose not to create
    // `retransmit_packet` out of it.
    return -1;
  }
  packet->set_packet_type(RtpPacketMediaType::kRetransmission);
  packet->set_fec_protect_packet(false);
  std::vector<std::unique_ptr<RtpPacketToSend>> packets;
  packets.emplace_back(std::move(packet));
  paced_sender_->EnqueuePackets(std::move(packets));

  return packet_size;
}

void RTPSender::OnReceivedAckOnSsrc(int64_t extended_highest_sequence_number) {
  MutexLock lock(&send_mutex_);
  bool update_required = !ssrc_has_acked_;
  ssrc_has_acked_ = true;
  if (update_required) {
    UpdateHeaderSizes();
  }
}

void RTPSender::OnReceivedAckOnRtxSsrc(
    int64_t extended_highest_sequence_number) {
  MutexLock lock(&send_mutex_);
  bool update_required = !rtx_ssrc_has_acked_;
  rtx_ssrc_has_acked_ = true;
  if (update_required) {
    UpdateHeaderSizes();
  }
}

void RTPSender::OnReceivedNack(
    const std::vector<uint16_t>& nack_sequence_numbers,
    int64_t avg_rtt) {
  packet_history_->SetRtt(TimeDelta::Millis(5 + avg_rtt));
  for (uint16_t seq_no : nack_sequence_numbers) {
    const int32_t bytes_sent = ReSendPacket(seq_no);
    if (bytes_sent < 0) {
      // Failed to send one Sequence number. Give up the rest in this nack.
      RTC_LOG(LS_WARNING) << "Failed resending RTP packet " << seq_no
                          << ", Discard rest of packets.";
      break;
    }
  }
}

bool RTPSender::SupportsPadding() const {
  MutexLock lock(&send_mutex_);
  return sending_media_ && supports_bwe_extension_;
}

bool RTPSender::SupportsRtxPayloadPadding() const {
  MutexLock lock(&send_mutex_);
  return sending_media_ && supports_bwe_extension_ &&
         (rtx_ & kRtxRedundantPayloads);
}

std::vector<std::unique_ptr<RtpPacketToSend>> RTPSender::GeneratePadding(
    size_t target_size_bytes,
    bool media_has_been_sent,
    bool can_send_padding_on_media_ssrc) {
  // This method does not actually send packets, it just generates
  // them and puts them in the pacer queue. Since this should incur
  // low overhead, keep the lock for the scope of the method in order
  // to make the code more readable.

  std::vector<std::unique_ptr<RtpPacketToSend>> padding_packets;
  size_t bytes_left = target_size_bytes;
  if (SupportsRtxPayloadPadding()) {
    while (bytes_left >= kMinPayloadPaddingBytes) {
      std::unique_ptr<RtpPacketToSend> packet =
          packet_history_->GetPayloadPaddingPacket(
              [&](const RtpPacketToSend& packet)
                  -> std::unique_ptr<RtpPacketToSend> {
                // Limit overshoot, generate <= `kMaxPaddingSizeFactor` *
                // `target_size_bytes`.
                const size_t max_overshoot_bytes = static_cast<size_t>(
                    ((kMaxPaddingSizeFactor - 1.0) * target_size_bytes) + 0.5);
                if (packet.payload_size() + kRtxHeaderSize >
                    max_overshoot_bytes + bytes_left) {
                  return nullptr;
                }
                return BuildRtxPacket(packet);
              });
      if (!packet) {
        break;
      }

      bytes_left -= std::min(bytes_left, packet->payload_size());
      packet->set_packet_type(RtpPacketMediaType::kPadding);
      padding_packets.push_back(std::move(packet));
    }
  }

  MutexLock lock(&send_mutex_);
  if (!sending_media_) {
    return {};
  }

  size_t padding_bytes_in_packet;
  const size_t max_payload_size =
      max_packet_size_ - max_padding_fec_packet_header_;
  if (audio_configured_) {
    // Allow smaller padding packets for audio.
    padding_bytes_in_packet = rtc::SafeClamp<size_t>(
        bytes_left, kMinAudioPaddingLength,
        rtc::SafeMin(max_payload_size, kMaxPaddingLength));
  } else {
    // Always send full padding packets. This is accounted for by the
    // RtpPacketSender, which will make sure we don't send too much padding even
    // if a single packet is larger than requested.
    // We do this to avoid frequently sending small packets on higher bitrates.
    padding_bytes_in_packet = rtc::SafeMin(max_payload_size, kMaxPaddingLength);
  }

  while (bytes_left > 0) {
    auto padding_packet =
        std::make_unique<RtpPacketToSend>(&rtp_header_extension_map_);
    padding_packet->set_packet_type(RtpPacketMediaType::kPadding);
    padding_packet->SetMarker(false);
    if (rtx_ == kRtxOff) {
      if (!can_send_padding_on_media_ssrc) {
        break;
      }
      padding_packet->SetSsrc(ssrc_);
    } else {
      // Without abs-send-time or transport sequence number a media packet
      // must be sent before padding so that the timestamps used for
      // estimation are correct.
      if (!media_has_been_sent &&
          !(rtp_header_extension_map_.IsRegistered(AbsoluteSendTime::kId) ||
            rtp_header_extension_map_.IsRegistered(
                TransportSequenceNumber::kId))) {
        break;
      }

      RTC_DCHECK(rtx_ssrc_);
      RTC_DCHECK(!rtx_payload_type_map_.empty());
      padding_packet->SetSsrc(*rtx_ssrc_);
      padding_packet->SetPayloadType(rtx_payload_type_map_.begin()->second);
    }

    if (rtp_header_extension_map_.IsRegistered(TransportSequenceNumber::kId)) {
      padding_packet->ReserveExtension<TransportSequenceNumber>();
    }
    if (rtp_header_extension_map_.IsRegistered(TransmissionOffset::kId)) {
      padding_packet->ReserveExtension<TransmissionOffset>();
    }
    if (rtp_header_extension_map_.IsRegistered(AbsoluteSendTime::kId)) {
      padding_packet->ReserveExtension<AbsoluteSendTime>();
    }

    padding_packet->SetPadding(padding_bytes_in_packet);
    bytes_left -= std::min(bytes_left, padding_bytes_in_packet);
    padding_packets.push_back(std::move(padding_packet));
  }

  return padding_packets;
}

void RTPSender::EnqueuePackets(
    std::vector<std::unique_ptr<RtpPacketToSend>> packets) {
  RTC_DCHECK(!packets.empty());
  Timestamp now = clock_->CurrentTime();
  for (auto& packet : packets) {
    RTC_DCHECK(packet);
    RTC_CHECK(packet->packet_type().has_value())
        << "Packet type must be set before sending.";
    if (packet->capture_time() <= Timestamp::Zero()) {
      packet->set_capture_time(now);
    }
  }

  paced_sender_->EnqueuePackets(std::move(packets));
}

size_t RTPSender::FecOrPaddingPacketMaxRtpHeaderLength() const {
  MutexLock lock(&send_mutex_);
  return max_padding_fec_packet_header_;
}

size_t RTPSender::ExpectedPerPacketOverhead() const {
  MutexLock lock(&send_mutex_);
  return max_media_packet_header_;
}

std::unique_ptr<RtpPacketToSend> RTPSender::AllocatePacket(
    rtc::ArrayView<const uint32_t> csrcs) {
  MutexLock lock(&send_mutex_);
  RTC_DCHECK_LE(csrcs.size(), kRtpCsrcSize);
  if (csrcs.size() > max_num_csrcs_) {
    max_num_csrcs_ = csrcs.size();
    UpdateHeaderSizes();
  }
  auto packet = std::make_unique<RtpPacketToSend>(&rtp_header_extension_map_,
                                                  max_packet_size_);
  packet->SetSsrc(ssrc_);
  packet->SetCsrcs(csrcs);

  // Reserve extensions, if registered, RtpSender set in SendToNetwork.
  packet->ReserveExtension<AbsoluteSendTime>();
  packet->ReserveExtension<TransmissionOffset>();
  packet->ReserveExtension<TransportSequenceNumber>();

  // BUNDLE requires that the receiver "bind" the received SSRC to the values
  // in the MID and/or (R)RID header extensions if present. Therefore, the
  // sender can reduce overhead by omitting these header extensions once it
  // knows that the receiver has "bound" the SSRC.
  // This optimization can be configured by setting
  // `always_send_mid_and_rid_` appropriately.
  //
  // The algorithm here is fairly simple: Always attach a MID and/or RID (if
  // configured) to the outgoing packets until an RTCP receiver report comes
  // back for this SSRC. That feedback indicates the receiver must have
  // received a packet with the SSRC and header extension(s), so the sender
  // then stops attaching the MID and RID.
  if (always_send_mid_and_rid_ || !ssrc_has_acked_) {
    // These are no-ops if the corresponding header extension is not registered.
    if (!mid_.empty()) {
      packet->SetExtension<RtpMid>(mid_);
    }
    if (!rid_.empty()) {
      packet->SetExtension<RtpStreamId>(rid_);
    }
  }
  return packet;
}

size_t RTPSender::RtxPacketOverhead() const {
  MutexLock lock(&send_mutex_);
  if (rtx_ == kRtxOff) {
    return 0;
  }
  size_t overhead = 0;

  // Count space for the RTP header extensions that might need to be added to
  // the RTX packet.
  if (!always_send_mid_and_rid_ && (!rtx_ssrc_has_acked_ && ssrc_has_acked_)) {
    // Prefer to reserve extra byte in case two byte header rtp header
    // extensions are used.
    static constexpr int kRtpExtensionHeaderSize = 2;

    // Rtx packets hasn't been acked and would need to have mid and rrsid rtp
    // header extensions, while media packets no longer needs to include mid and
    // rsid extensions.
    if (!mid_.empty()) {
      overhead += (kRtpExtensionHeaderSize + mid_.size());
    }
    if (!rid_.empty()) {
      overhead += (kRtpExtensionHeaderSize + rid_.size());
    }
    // RTP header extensions are rounded up to 4 bytes. Depending on already
    // present extensions adding mid & rrsid may add up to 3 bytes of padding.
    overhead += 3;
  }

  // Add two bytes for the original sequence number in the RTP payload.
  overhead += kRtxHeaderSize;
  return overhead;
}

void RTPSender::SetSendingMediaStatus(bool enabled) {
  MutexLock lock(&send_mutex_);
  sending_media_ = enabled;
}

bool RTPSender::SendingMedia() const {
  MutexLock lock(&send_mutex_);
  return sending_media_;
}

bool RTPSender::IsAudioConfigured() const {
  return audio_configured_;
}

void RTPSender::SetTimestampOffset(uint32_t timestamp) {
  MutexLock lock(&send_mutex_);
  timestamp_offset_ = timestamp;
}

uint32_t RTPSender::TimestampOffset() const {
  MutexLock lock(&send_mutex_);
  return timestamp_offset_;
}

void RTPSender::SetMid(absl::string_view mid) {
  // This is configured via the API.
  MutexLock lock(&send_mutex_);
  RTC_DCHECK_LE(mid.length(), RtpMid::kMaxValueSizeBytes);
  mid_ = std::string(mid);
  UpdateHeaderSizes();
}

static void CopyHeaderAndExtensionsToRtxPacket(const RtpPacketToSend& packet,
                                               RtpPacketToSend* rtx_packet) {
  // Set the relevant fixed packet headers. The following are not set:
  // * Payload type - it is replaced in rtx packets.
  // * Sequence number - RTX has a separate sequence numbering.
  // * SSRC - RTX stream has its own SSRC.
  rtx_packet->SetMarker(packet.Marker());
  rtx_packet->SetTimestamp(packet.Timestamp());

  // Set the variable fields in the packet header:
  // * CSRCs - must be set before header extensions.
  // * Header extensions - replace Rid header with RepairedRid header.
  rtx_packet->SetCsrcs(packet.Csrcs());
  for (int extension_num = kRtpExtensionNone + 1;
       extension_num < kRtpExtensionNumberOfExtensions; ++extension_num) {
    auto extension = static_cast<RTPExtensionType>(extension_num);

    // Stream ID header extensions (MID, RSID) are sent per-SSRC. Since RTX
    // operates on a different SSRC, the presence and values of these header
    // extensions should be determined separately and not blindly copied.
    if (extension == kRtpExtensionMid ||
        extension == kRtpExtensionRtpStreamId) {
      continue;
    }

    // Empty extensions should be supported, so not checking `source.empty()`.
    if (!packet.HasExtension(extension)) {
      continue;
    }

    rtc::ArrayView<const uint8_t> source = packet.FindExtension(extension);

    rtc::ArrayView<uint8_t> destination =
        rtx_packet->AllocateExtension(extension, source.size());

    // Could happen if any:
    // 1. Extension has 0 length.
    // 2. Extension is not registered in destination.
    // 3. Allocating extension in destination failed.
    if (destination.empty() || source.size() != destination.size()) {
      continue;
    }

    std::memcpy(destination.begin(), source.begin(), destination.size());
  }
}

std::unique_ptr<RtpPacketToSend> RTPSender::BuildRtxPacket(
    const RtpPacketToSend& packet) {
  std::unique_ptr<RtpPacketToSend> rtx_packet;

  // Add original RTP header.
  {
    MutexLock lock(&send_mutex_);
    if (!sending_media_)
      return nullptr;

    RTC_DCHECK(rtx_ssrc_);

    // Replace payload type.
    auto kv = rtx_payload_type_map_.find(packet.PayloadType());
    if (kv == rtx_payload_type_map_.end())
      return nullptr;

    rtx_packet = std::make_unique<RtpPacketToSend>(&rtp_header_extension_map_,
                                                   max_packet_size_);

    rtx_packet->SetPayloadType(kv->second);

    // Replace SSRC.
    rtx_packet->SetSsrc(*rtx_ssrc_);

    CopyHeaderAndExtensionsToRtxPacket(packet, rtx_packet.get());

    // RTX packets are sent on an SSRC different from the main media, so the
    // decision to attach MID and/or RRID header extensions is completely
    // separate from that of the main media SSRC.
    //
    // Note that RTX packets must used the RepairedRtpStreamId (RRID) header
    // extension instead of the RtpStreamId (RID) header extension even though
    // the payload is identical.
    if (always_send_mid_and_rid_ || !rtx_ssrc_has_acked_) {
      // These are no-ops if the corresponding header extension is not
      // registered.
      if (!mid_.empty()) {
        rtx_packet->SetExtension<RtpMid>(mid_);
      }
      if (!rid_.empty()) {
        rtx_packet->SetExtension<RepairedRtpStreamId>(rid_);
      }
    }
  }
  RTC_DCHECK(rtx_packet);

  uint8_t* rtx_payload =
      rtx_packet->AllocatePayload(packet.payload_size() + kRtxHeaderSize);
  RTC_CHECK(rtx_payload);

  // Add OSN (original sequence number).
  ByteWriter<uint16_t>::WriteBigEndian(rtx_payload, packet.SequenceNumber());

  // Add original payload data.
  auto payload = packet.payload();
  if (!payload.empty()) {
    memcpy(rtx_payload + kRtxHeaderSize, payload.data(), payload.size());
  }

  // Add original additional data.
  rtx_packet->set_additional_data(packet.additional_data());

  // Copy capture time so e.g. TransmissionOffset is correctly set.
  rtx_packet->set_capture_time(packet.capture_time());

  return rtx_packet;
}

void RTPSender::SetRtpState(const RtpState& rtp_state) {
  MutexLock lock(&send_mutex_);

  timestamp_offset_ = rtp_state.start_timestamp;
  ssrc_has_acked_ = rtp_state.ssrc_has_acked;
  UpdateHeaderSizes();
}

RtpState RTPSender::GetRtpState() const {
  MutexLock lock(&send_mutex_);

  RtpState state;
  state.start_timestamp = timestamp_offset_;
  state.ssrc_has_acked = ssrc_has_acked_;
  return state;
}

void RTPSender::SetRtxRtpState(const RtpState& rtp_state) {
  MutexLock lock(&send_mutex_);
  rtx_ssrc_has_acked_ = rtp_state.ssrc_has_acked;
}

RtpState RTPSender::GetRtxRtpState() const {
  MutexLock lock(&send_mutex_);

  RtpState state;
  state.start_timestamp = timestamp_offset_;
  state.ssrc_has_acked = rtx_ssrc_has_acked_;

  return state;
}

void RTPSender::UpdateHeaderSizes() {
  const size_t rtp_header_length =
      kRtpHeaderLength + sizeof(uint32_t) * max_num_csrcs_;

  max_padding_fec_packet_header_ =
      rtp_header_length + RtpHeaderExtensionSize(kFecOrPaddingExtensionSizes,
                                                 rtp_header_extension_map_);

  // RtpStreamId, Mid and RepairedRtpStreamId are treated specially in that
  // we check if they currently are being sent. RepairedRtpStreamId can be
  // sent instead of RtpStreamID on RTX packets and may share the same space.
  // When the primary SSRC has already been acked but the RTX SSRC has not
  // yet been acked, RepairedRtpStreamId needs to be taken into account
  // separately.
  const bool send_mid_rid_on_rtx =
      rtx_ssrc_.has_value() &&
      (always_send_mid_and_rid_ || !rtx_ssrc_has_acked_);
  const bool send_mid_rid = always_send_mid_and_rid_ || !ssrc_has_acked_;
  std::vector<RtpExtensionSize> non_volatile_extensions;
  for (auto& extension :
       audio_configured_ ? AudioExtensionSizes() : VideoExtensionSizes()) {
    if (IsNonVolatile(extension.type)) {
      switch (extension.type) {
        case RTPExtensionType::kRtpExtensionMid:
          if ((send_mid_rid || send_mid_rid_on_rtx) && !mid_.empty()) {
            non_volatile_extensions.push_back(extension);
          }
          break;
        case RTPExtensionType::kRtpExtensionRtpStreamId:
          if (send_mid_rid && !rid_.empty()) {
            non_volatile_extensions.push_back(extension);
          }
          break;
        case RTPExtensionType::kRtpExtensionRepairedRtpStreamId:
          if (send_mid_rid_on_rtx && !send_mid_rid && !rid_.empty()) {
            non_volatile_extensions.push_back(extension);
          }
          break;
        default:
          non_volatile_extensions.push_back(extension);
      }
    }
  }
  max_media_packet_header_ =
      rtp_header_length + RtpHeaderExtensionSize(non_volatile_extensions,
                                                 rtp_header_extension_map_);
  // Reserve extra bytes if packet might be resent in an rtx packet.
  if (rtx_ssrc_.has_value()) {
    max_media_packet_header_ += kRtxHeaderSize;
  }
}
}  // namespace webrtc
