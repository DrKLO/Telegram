/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "logging/rtc_event_log/encoder/rtc_event_log_encoder_new_format.h"

#include <type_traits>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "api/field_trials_view.h"
#include "api/network_state_predictor.h"
#include "logging/rtc_event_log/dependency_descriptor_encoder_decoder.h"
#include "logging/rtc_event_log/encoder/blob_encoding.h"
#include "logging/rtc_event_log/encoder/delta_encoding.h"
#include "logging/rtc_event_log/encoder/rtc_event_log_encoder_common.h"
#include "logging/rtc_event_log/events/rtc_event_alr_state.h"
#include "logging/rtc_event_log/events/rtc_event_audio_network_adaptation.h"
#include "logging/rtc_event_log/events/rtc_event_audio_playout.h"
#include "logging/rtc_event_log/events/rtc_event_audio_receive_stream_config.h"
#include "logging/rtc_event_log/events/rtc_event_audio_send_stream_config.h"
#include "logging/rtc_event_log/events/rtc_event_bwe_update_delay_based.h"
#include "logging/rtc_event_log/events/rtc_event_bwe_update_loss_based.h"
#include "logging/rtc_event_log/events/rtc_event_dtls_transport_state.h"
#include "logging/rtc_event_log/events/rtc_event_dtls_writable_state.h"
#include "logging/rtc_event_log/events/rtc_event_frame_decoded.h"
#include "logging/rtc_event_log/events/rtc_event_generic_ack_received.h"
#include "logging/rtc_event_log/events/rtc_event_generic_packet_received.h"
#include "logging/rtc_event_log/events/rtc_event_generic_packet_sent.h"
#include "logging/rtc_event_log/events/rtc_event_ice_candidate_pair.h"
#include "logging/rtc_event_log/events/rtc_event_ice_candidate_pair_config.h"
#include "logging/rtc_event_log/events/rtc_event_neteq_set_minimum_delay.h"
#include "logging/rtc_event_log/events/rtc_event_probe_cluster_created.h"
#include "logging/rtc_event_log/events/rtc_event_probe_result_failure.h"
#include "logging/rtc_event_log/events/rtc_event_probe_result_success.h"
#include "logging/rtc_event_log/events/rtc_event_remote_estimate.h"
#include "logging/rtc_event_log/events/rtc_event_route_change.h"
#include "logging/rtc_event_log/events/rtc_event_rtcp_packet_incoming.h"
#include "logging/rtc_event_log/events/rtc_event_rtcp_packet_outgoing.h"
#include "logging/rtc_event_log/events/rtc_event_rtp_packet_incoming.h"
#include "logging/rtc_event_log/events/rtc_event_rtp_packet_outgoing.h"
#include "logging/rtc_event_log/events/rtc_event_video_receive_stream_config.h"
#include "logging/rtc_event_log/events/rtc_event_video_send_stream_config.h"
#include "logging/rtc_event_log/rtc_stream_config.h"
#include "modules/audio_coding/audio_network_adaptor/include/audio_network_adaptor_config.h"
#include "modules/rtp_rtcp/include/rtp_cvo.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "modules/rtp_rtcp/source/rtcp_packet/app.h"
#include "modules/rtp_rtcp/source/rtcp_packet/bye.h"
#include "modules/rtp_rtcp/source/rtcp_packet/common_header.h"
#include "modules/rtp_rtcp/source/rtcp_packet/extended_reports.h"
#include "modules/rtp_rtcp/source/rtcp_packet/psfb.h"
#include "modules/rtp_rtcp/source/rtcp_packet/receiver_report.h"
#include "modules/rtp_rtcp/source/rtcp_packet/rtpfb.h"
#include "modules/rtp_rtcp/source/rtcp_packet/sdes.h"
#include "modules/rtp_rtcp/source/rtcp_packet/sender_report.h"
#include "modules/rtp_rtcp/source/rtp_dependency_descriptor_extension.h"
#include "modules/rtp_rtcp/source/rtp_header_extensions.h"
#include "modules/rtp_rtcp/source/rtp_packet.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

// *.pb.h files are generated at build-time by the protobuf compiler.
#ifdef WEBRTC_ANDROID_PLATFORM_BUILD
#include "external/webrtc/webrtc/logging/rtc_event_log/rtc_event_log2.pb.h"
#else
#include "logging/rtc_event_log/rtc_event_log2.pb.h"
#endif

using webrtc_event_logging::ToUnsigned;

namespace webrtc {

namespace {
rtclog2::DelayBasedBweUpdates::DetectorState ConvertToProtoFormat(
    BandwidthUsage state) {
  switch (state) {
    case BandwidthUsage::kBwNormal:
      return rtclog2::DelayBasedBweUpdates::BWE_NORMAL;
    case BandwidthUsage::kBwUnderusing:
      return rtclog2::DelayBasedBweUpdates::BWE_UNDERUSING;
    case BandwidthUsage::kBwOverusing:
      return rtclog2::DelayBasedBweUpdates::BWE_OVERUSING;
    case BandwidthUsage::kLast:
      RTC_DCHECK_NOTREACHED();
  }
  RTC_DCHECK_NOTREACHED();
  return rtclog2::DelayBasedBweUpdates::BWE_UNKNOWN_STATE;
}

rtclog2::FrameDecodedEvents::Codec ConvertToProtoFormat(VideoCodecType codec) {
  switch (codec) {
    case VideoCodecType::kVideoCodecGeneric:
      return rtclog2::FrameDecodedEvents::CODEC_GENERIC;
    case VideoCodecType::kVideoCodecVP8:
      return rtclog2::FrameDecodedEvents::CODEC_VP8;
    case VideoCodecType::kVideoCodecVP9:
      return rtclog2::FrameDecodedEvents::CODEC_VP9;
    case VideoCodecType::kVideoCodecAV1:
      return rtclog2::FrameDecodedEvents::CODEC_AV1;
    case VideoCodecType::kVideoCodecH264:
      return rtclog2::FrameDecodedEvents::CODEC_H264;
    case VideoCodecType::kVideoCodecMultiplex:
      // This codec type is afaik not used.
      return rtclog2::FrameDecodedEvents::CODEC_UNKNOWN;
    case VideoCodecType::kVideoCodecH265:
      return rtclog2::FrameDecodedEvents::CODEC_H265;
  }
  RTC_DCHECK_NOTREACHED();
  return rtclog2::FrameDecodedEvents::CODEC_UNKNOWN;
}

rtclog2::BweProbeResultFailure::FailureReason ConvertToProtoFormat(
    ProbeFailureReason failure_reason) {
  switch (failure_reason) {
    case ProbeFailureReason::kInvalidSendReceiveInterval:
      return rtclog2::BweProbeResultFailure::INVALID_SEND_RECEIVE_INTERVAL;
    case ProbeFailureReason::kInvalidSendReceiveRatio:
      return rtclog2::BweProbeResultFailure::INVALID_SEND_RECEIVE_RATIO;
    case ProbeFailureReason::kTimeout:
      return rtclog2::BweProbeResultFailure::TIMEOUT;
    case ProbeFailureReason::kLast:
      RTC_DCHECK_NOTREACHED();
  }
  RTC_DCHECK_NOTREACHED();
  return rtclog2::BweProbeResultFailure::UNKNOWN;
}

// Returns true if there are recognized extensions that we should log
// and false if there are no extensions or all extensions are types we don't
// log. The protobuf representation of the header configs is written to
// `proto_config`.
bool ConvertToProtoFormat(const std::vector<RtpExtension>& extensions,
                          rtclog2::RtpHeaderExtensionConfig* proto_config) {
  size_t unknown_extensions = 0;
  for (auto& extension : extensions) {
    if (extension.uri == RtpExtension::kAudioLevelUri) {
      proto_config->set_audio_level_id(extension.id);
    } else if (extension.uri == RtpExtension::kTimestampOffsetUri) {
      proto_config->set_transmission_time_offset_id(extension.id);
    } else if (extension.uri == RtpExtension::kAbsSendTimeUri) {
      proto_config->set_absolute_send_time_id(extension.id);
    } else if (extension.uri == RtpExtension::kTransportSequenceNumberUri) {
      proto_config->set_transport_sequence_number_id(extension.id);
    } else if (extension.uri == RtpExtension::kVideoRotationUri) {
      proto_config->set_video_rotation_id(extension.id);
    } else if (extension.uri == RtpExtension::kDependencyDescriptorUri) {
      proto_config->set_dependency_descriptor_id(extension.id);
    } else {
      ++unknown_extensions;
    }
  }
  return unknown_extensions < extensions.size();
}

rtclog2::DtlsTransportStateEvent::DtlsTransportState ConvertToProtoFormat(
    webrtc::DtlsTransportState state) {
  switch (state) {
    case webrtc::DtlsTransportState::kNew:
      return rtclog2::DtlsTransportStateEvent::DTLS_TRANSPORT_NEW;
    case webrtc::DtlsTransportState::kConnecting:
      return rtclog2::DtlsTransportStateEvent::DTLS_TRANSPORT_CONNECTING;
    case webrtc::DtlsTransportState::kConnected:
      return rtclog2::DtlsTransportStateEvent::DTLS_TRANSPORT_CONNECTED;
    case webrtc::DtlsTransportState::kClosed:
      return rtclog2::DtlsTransportStateEvent::DTLS_TRANSPORT_CLOSED;
    case webrtc::DtlsTransportState::kFailed:
      return rtclog2::DtlsTransportStateEvent::DTLS_TRANSPORT_FAILED;
    case webrtc::DtlsTransportState::kNumValues:
      RTC_DCHECK_NOTREACHED();
  }
  RTC_DCHECK_NOTREACHED();
  return rtclog2::DtlsTransportStateEvent::UNKNOWN_DTLS_TRANSPORT_STATE;
}

rtclog2::IceCandidatePairConfig::IceCandidatePairConfigType
ConvertToProtoFormat(IceCandidatePairConfigType type) {
  switch (type) {
    case IceCandidatePairConfigType::kAdded:
      return rtclog2::IceCandidatePairConfig::ADDED;
    case IceCandidatePairConfigType::kUpdated:
      return rtclog2::IceCandidatePairConfig::UPDATED;
    case IceCandidatePairConfigType::kDestroyed:
      return rtclog2::IceCandidatePairConfig::DESTROYED;
    case IceCandidatePairConfigType::kSelected:
      return rtclog2::IceCandidatePairConfig::SELECTED;
    case IceCandidatePairConfigType::kNumValues:
      RTC_DCHECK_NOTREACHED();
  }
  RTC_DCHECK_NOTREACHED();
  return rtclog2::IceCandidatePairConfig::UNKNOWN_CONFIG_TYPE;
}

rtclog2::IceCandidatePairConfig::IceCandidateType ConvertToProtoFormat(
    IceCandidateType type) {
  switch (type) {
    case IceCandidateType::kHost:
      return rtclog2::IceCandidatePairConfig::LOCAL;
    case IceCandidateType::kSrflx:
      return rtclog2::IceCandidatePairConfig::STUN;
    case IceCandidateType::kPrflx:
      return rtclog2::IceCandidatePairConfig::PRFLX;
    case IceCandidateType::kRelay:
      return rtclog2::IceCandidatePairConfig::RELAY;
  }
  RTC_DCHECK_NOTREACHED();
  return rtclog2::IceCandidatePairConfig::UNKNOWN_CANDIDATE_TYPE;
}

rtclog2::IceCandidatePairConfig::Protocol ConvertToProtoFormat(
    IceCandidatePairProtocol protocol) {
  switch (protocol) {
    case IceCandidatePairProtocol::kUnknown:
      return rtclog2::IceCandidatePairConfig::UNKNOWN_PROTOCOL;
    case IceCandidatePairProtocol::kUdp:
      return rtclog2::IceCandidatePairConfig::UDP;
    case IceCandidatePairProtocol::kTcp:
      return rtclog2::IceCandidatePairConfig::TCP;
    case IceCandidatePairProtocol::kSsltcp:
      return rtclog2::IceCandidatePairConfig::SSLTCP;
    case IceCandidatePairProtocol::kTls:
      return rtclog2::IceCandidatePairConfig::TLS;
    case IceCandidatePairProtocol::kNumValues:
      RTC_DCHECK_NOTREACHED();
  }
  RTC_DCHECK_NOTREACHED();
  return rtclog2::IceCandidatePairConfig::UNKNOWN_PROTOCOL;
}

rtclog2::IceCandidatePairConfig::AddressFamily ConvertToProtoFormat(
    IceCandidatePairAddressFamily address_family) {
  switch (address_family) {
    case IceCandidatePairAddressFamily::kUnknown:
      return rtclog2::IceCandidatePairConfig::UNKNOWN_ADDRESS_FAMILY;
    case IceCandidatePairAddressFamily::kIpv4:
      return rtclog2::IceCandidatePairConfig::IPV4;
    case IceCandidatePairAddressFamily::kIpv6:
      return rtclog2::IceCandidatePairConfig::IPV6;
    case IceCandidatePairAddressFamily::kNumValues:
      RTC_DCHECK_NOTREACHED();
  }
  RTC_DCHECK_NOTREACHED();
  return rtclog2::IceCandidatePairConfig::UNKNOWN_ADDRESS_FAMILY;
}

rtclog2::IceCandidatePairConfig::NetworkType ConvertToProtoFormat(
    IceCandidateNetworkType network_type) {
  switch (network_type) {
    case IceCandidateNetworkType::kUnknown:
      return rtclog2::IceCandidatePairConfig::UNKNOWN_NETWORK_TYPE;
    case IceCandidateNetworkType::kEthernet:
      return rtclog2::IceCandidatePairConfig::ETHERNET;
    case IceCandidateNetworkType::kLoopback:
      return rtclog2::IceCandidatePairConfig::LOOPBACK;
    case IceCandidateNetworkType::kWifi:
      return rtclog2::IceCandidatePairConfig::WIFI;
    case IceCandidateNetworkType::kVpn:
      return rtclog2::IceCandidatePairConfig::VPN;
    case IceCandidateNetworkType::kCellular:
      return rtclog2::IceCandidatePairConfig::CELLULAR;
    case IceCandidateNetworkType::kNumValues:
      RTC_DCHECK_NOTREACHED();
  }
  RTC_DCHECK_NOTREACHED();
  return rtclog2::IceCandidatePairConfig::UNKNOWN_NETWORK_TYPE;
}

rtclog2::IceCandidatePairEvent::IceCandidatePairEventType ConvertToProtoFormat(
    IceCandidatePairEventType type) {
  switch (type) {
    case IceCandidatePairEventType::kCheckSent:
      return rtclog2::IceCandidatePairEvent::CHECK_SENT;
    case IceCandidatePairEventType::kCheckReceived:
      return rtclog2::IceCandidatePairEvent::CHECK_RECEIVED;
    case IceCandidatePairEventType::kCheckResponseSent:
      return rtclog2::IceCandidatePairEvent::CHECK_RESPONSE_SENT;
    case IceCandidatePairEventType::kCheckResponseReceived:
      return rtclog2::IceCandidatePairEvent::CHECK_RESPONSE_RECEIVED;
    case IceCandidatePairEventType::kNumValues:
      RTC_DCHECK_NOTREACHED();
  }
  RTC_DCHECK_NOTREACHED();
  return rtclog2::IceCandidatePairEvent::UNKNOWN_CHECK_TYPE;
}

// Copies all RTCP blocks except APP, SDES and unknown from `packet` to
// `buffer`. `buffer` must have space for at least `packet.size()` bytes.
size_t RemoveNonAllowlistedRtcpBlocks(const rtc::Buffer& packet,
                                      uint8_t* buffer) {
  RTC_DCHECK(buffer != nullptr);
  rtcp::CommonHeader header;
  const uint8_t* block_begin = packet.data();
  const uint8_t* packet_end = packet.data() + packet.size();
  size_t buffer_length = 0;
  while (block_begin < packet_end) {
    if (!header.Parse(block_begin, packet_end - block_begin)) {
      break;  // Incorrect message header.
    }
    const uint8_t* next_block = header.NextPacket();
    RTC_DCHECK_GT(next_block, block_begin);
    RTC_DCHECK_LE(next_block, packet_end);
    size_t block_size = next_block - block_begin;
    switch (header.type()) {
      case rtcp::Bye::kPacketType:
      case rtcp::ExtendedReports::kPacketType:
      case rtcp::Psfb::kPacketType:
      case rtcp::ReceiverReport::kPacketType:
      case rtcp::Rtpfb::kPacketType:
      case rtcp::SenderReport::kPacketType:
        // We log sender reports, receiver reports, bye messages, third-party
        // loss reports, payload-specific feedback and extended reports.
        // TODO(terelius): As an optimization, don't copy anything if all blocks
        // in the packet are allowlisted types.
        memcpy(buffer + buffer_length, block_begin, block_size);
        buffer_length += block_size;
        break;
      case rtcp::App::kPacketType:
      case rtcp::Sdes::kPacketType:
      default:
        // We don't log sender descriptions, application defined messages
        // or message blocks of unknown type.
        break;
    }

    block_begin += block_size;
  }
  return buffer_length;
}

template <typename EventType, typename ProtoType>
void EncodeRtcpPacket(rtc::ArrayView<const EventType*> batch,
                      ProtoType* proto_batch) {
  if (batch.empty()) {
    return;
  }

  // Base event
  const EventType* const base_event = batch[0];
  proto_batch->set_timestamp_ms(base_event->timestamp_ms());
  {
    std::vector<uint8_t> buffer(base_event->packet().size());
    size_t buffer_length =
        RemoveNonAllowlistedRtcpBlocks(base_event->packet(), buffer.data());
    proto_batch->set_raw_packet(buffer.data(), buffer_length);
  }

  if (batch.size() == 1) {
    return;
  }

  // Delta encoding
  proto_batch->set_number_of_deltas(batch.size() - 1);
  std::vector<absl::optional<uint64_t>> values(batch.size() - 1);
  std::string encoded_deltas;

  // timestamp_ms
  for (size_t i = 0; i < values.size(); ++i) {
    const EventType* event = batch[i + 1];
    values[i] = ToUnsigned(event->timestamp_ms());
  }
  encoded_deltas = EncodeDeltas(ToUnsigned(base_event->timestamp_ms()), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_timestamp_ms_deltas(encoded_deltas);
  }

  // raw_packet
  std::vector<std::string> scrubed_packets(batch.size() - 1);
  for (size_t i = 0; i < scrubed_packets.size(); ++i) {
    const EventType* event = batch[i + 1];
    scrubed_packets[i].resize(event->packet().size());
    static_assert(sizeof(std::string::value_type) == sizeof(uint8_t), "");
    const size_t buffer_length = RemoveNonAllowlistedRtcpBlocks(
        event->packet(), reinterpret_cast<uint8_t*>(&scrubed_packets[i][0]));
    if (buffer_length < event->packet().size()) {
      scrubed_packets[i].resize(buffer_length);
    }
  }
  proto_batch->set_raw_packet_blobs(EncodeBlobs(scrubed_packets));
}
}  // namespace

template <typename Batch, typename ProtoType>
void RtcEventLogEncoderNewFormat::EncodeRtpPacket(const Batch& batch,
                                                  ProtoType* proto_batch) {
  using EventType = std::remove_pointer_t<typename Batch::value_type>;
  if (batch.empty()) {
    return;
  }

  // Base event
  const EventType* const base_event = batch[0];
  proto_batch->set_timestamp_ms(base_event->timestamp_ms());
  proto_batch->set_marker(base_event->Marker());
  // TODO(terelius): Is payload type needed?
  proto_batch->set_payload_type(base_event->PayloadType());
  proto_batch->set_sequence_number(base_event->SequenceNumber());
  proto_batch->set_rtp_timestamp(base_event->Timestamp());
  proto_batch->set_ssrc(base_event->Ssrc());
  proto_batch->set_payload_size(base_event->payload_length());
  proto_batch->set_header_size(base_event->header_length());
  proto_batch->set_padding_size(base_event->padding_length());

  // Add header extensions (base event).
  absl::optional<uint64_t> base_transport_sequence_number;
  {
    uint16_t seqnum;
    if (base_event->template GetExtension<TransportSequenceNumber>(&seqnum)) {
      proto_batch->set_transport_sequence_number(seqnum);
      base_transport_sequence_number = seqnum;
    }
  }

  absl::optional<uint64_t> unsigned_base_transmission_time_offset;
  {
    int32_t offset;
    if (base_event->template GetExtension<TransmissionOffset>(&offset)) {
      proto_batch->set_transmission_time_offset(offset);
      unsigned_base_transmission_time_offset = ToUnsigned(offset);
    }
  }

  absl::optional<uint64_t> base_absolute_send_time;
  {
    uint32_t sendtime;
    if (base_event->template GetExtension<AbsoluteSendTime>(&sendtime)) {
      proto_batch->set_absolute_send_time(sendtime);
      base_absolute_send_time = sendtime;
    }
  }

  absl::optional<uint64_t> base_video_rotation;
  {
    VideoRotation video_rotation;
    if (base_event->template GetExtension<VideoOrientation>(&video_rotation)) {
      proto_batch->set_video_rotation(
          ConvertVideoRotationToCVOByte(video_rotation));
      base_video_rotation = ConvertVideoRotationToCVOByte(video_rotation);
    }
  }

  absl::optional<uint64_t> base_audio_level;
  absl::optional<uint64_t> base_voice_activity;
  {
    bool voice_activity;
    uint8_t audio_level;
    if (base_event->template GetExtension<AudioLevel>(&voice_activity,
                                                      &audio_level)) {
      RTC_DCHECK_LE(audio_level, 0x7Fu);
      base_audio_level = audio_level;
      proto_batch->set_audio_level(audio_level);

      base_voice_activity = voice_activity;
      proto_batch->set_voice_activity(voice_activity);
    }
  }

  {
    // TODO(webrtc:14975) Remove this kill switch after DD in RTC event log has
    //                    been rolled out.
    if (encode_dependency_descriptor_) {
      std::vector<rtc::ArrayView<const uint8_t>> raw_dds(batch.size());
      bool has_dd = false;
      for (size_t i = 0; i < batch.size(); ++i) {
        raw_dds[i] =
            batch[i]
                ->template GetRawExtension<RtpDependencyDescriptorExtension>();
        has_dd |= !raw_dds[i].empty();
      }
      if (has_dd) {
        if (auto dd_encoded =
                RtcEventLogDependencyDescriptorEncoderDecoder::Encode(
                    raw_dds)) {
          *proto_batch->mutable_dependency_descriptor() = *dd_encoded;
        }
      }
    }
  }

  if (batch.size() == 1) {
    return;
  }

  // Delta encoding
  proto_batch->set_number_of_deltas(batch.size() - 1);
  std::vector<absl::optional<uint64_t>> values(batch.size() - 1);
  std::string encoded_deltas;

  // timestamp_ms (event)
  for (size_t i = 0; i < values.size(); ++i) {
    const EventType* event = batch[i + 1];
    values[i] = ToUnsigned(event->timestamp_ms());
  }
  encoded_deltas = EncodeDeltas(ToUnsigned(base_event->timestamp_ms()), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_timestamp_ms_deltas(encoded_deltas);
  }

  // marker (RTP base)
  for (size_t i = 0; i < values.size(); ++i) {
    const EventType* event = batch[i + 1];
    values[i] = event->Marker();
  }
  encoded_deltas = EncodeDeltas(base_event->Marker(), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_marker_deltas(encoded_deltas);
  }

  // payload_type (RTP base)
  for (size_t i = 0; i < values.size(); ++i) {
    const EventType* event = batch[i + 1];
    values[i] = event->PayloadType();
  }
  encoded_deltas = EncodeDeltas(base_event->PayloadType(), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_payload_type_deltas(encoded_deltas);
  }

  // sequence_number (RTP base)
  for (size_t i = 0; i < values.size(); ++i) {
    const EventType* event = batch[i + 1];
    values[i] = event->SequenceNumber();
  }
  encoded_deltas = EncodeDeltas(base_event->SequenceNumber(), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_sequence_number_deltas(encoded_deltas);
  }

  // rtp_timestamp (RTP base)
  for (size_t i = 0; i < values.size(); ++i) {
    const EventType* event = batch[i + 1];
    values[i] = event->Timestamp();
  }
  encoded_deltas = EncodeDeltas(base_event->Timestamp(), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_rtp_timestamp_deltas(encoded_deltas);
  }

  // ssrc (RTP base)
  for (size_t i = 0; i < values.size(); ++i) {
    const EventType* event = batch[i + 1];
    values[i] = event->Ssrc();
  }
  encoded_deltas = EncodeDeltas(base_event->Ssrc(), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_ssrc_deltas(encoded_deltas);
  }

  // payload_size (RTP base)
  for (size_t i = 0; i < values.size(); ++i) {
    const EventType* event = batch[i + 1];
    values[i] = event->payload_length();
  }
  encoded_deltas = EncodeDeltas(base_event->payload_length(), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_payload_size_deltas(encoded_deltas);
  }

  // header_size (RTP base)
  for (size_t i = 0; i < values.size(); ++i) {
    const EventType* event = batch[i + 1];
    values[i] = event->header_length();
  }
  encoded_deltas = EncodeDeltas(base_event->header_length(), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_header_size_deltas(encoded_deltas);
  }

  // padding_size (RTP base)
  for (size_t i = 0; i < values.size(); ++i) {
    const EventType* event = batch[i + 1];
    values[i] = event->padding_length();
  }
  encoded_deltas = EncodeDeltas(base_event->padding_length(), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_padding_size_deltas(encoded_deltas);
  }

  // transport_sequence_number (RTP extension)
  for (size_t i = 0; i < values.size(); ++i) {
    const EventType* event = batch[i + 1];
    uint16_t seqnum;
    if (event->template GetExtension<TransportSequenceNumber>(&seqnum)) {
      values[i] = seqnum;
    } else {
      values[i].reset();
    }
  }
  encoded_deltas = EncodeDeltas(base_transport_sequence_number, values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_transport_sequence_number_deltas(encoded_deltas);
  }

  // transmission_time_offset (RTP extension)
  for (size_t i = 0; i < values.size(); ++i) {
    const EventType* event = batch[i + 1];
    int32_t offset;
    if (event->template GetExtension<TransmissionOffset>(&offset)) {
      values[i] = ToUnsigned(offset);
    } else {
      values[i].reset();
    }
  }
  encoded_deltas = EncodeDeltas(unsigned_base_transmission_time_offset, values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_transmission_time_offset_deltas(encoded_deltas);
  }

  // absolute_send_time (RTP extension)
  for (size_t i = 0; i < values.size(); ++i) {
    const EventType* event = batch[i + 1];
    uint32_t sendtime;
    if (event->template GetExtension<AbsoluteSendTime>(&sendtime)) {
      values[i] = sendtime;
    } else {
      values[i].reset();
    }
  }
  encoded_deltas = EncodeDeltas(base_absolute_send_time, values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_absolute_send_time_deltas(encoded_deltas);
  }

  // video_rotation (RTP extension)
  for (size_t i = 0; i < values.size(); ++i) {
    const EventType* event = batch[i + 1];
    VideoRotation video_rotation;
    if (event->template GetExtension<VideoOrientation>(&video_rotation)) {
      values[i] = ConvertVideoRotationToCVOByte(video_rotation);
    } else {
      values[i].reset();
    }
  }
  encoded_deltas = EncodeDeltas(base_video_rotation, values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_video_rotation_deltas(encoded_deltas);
  }

  // audio_level (RTP extension)
  for (size_t i = 0; i < values.size(); ++i) {
    const EventType* event = batch[i + 1];
    bool voice_activity;
    uint8_t audio_level;
    if (event->template GetExtension<AudioLevel>(&voice_activity,
                                                 &audio_level)) {
      RTC_DCHECK_LE(audio_level, 0x7Fu);
      values[i] = audio_level;
    } else {
      values[i].reset();
    }
  }
  encoded_deltas = EncodeDeltas(base_audio_level, values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_audio_level_deltas(encoded_deltas);
  }

  // voice_activity (RTP extension)
  for (size_t i = 0; i < values.size(); ++i) {
    const EventType* event = batch[i + 1];
    bool voice_activity;
    uint8_t audio_level;
    if (event->template GetExtension<AudioLevel>(&voice_activity,
                                                 &audio_level)) {
      RTC_DCHECK_LE(audio_level, 0x7Fu);
      values[i] = voice_activity;
    } else {
      values[i].reset();
    }
  }
  encoded_deltas = EncodeDeltas(base_voice_activity, values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_voice_activity_deltas(encoded_deltas);
  }
}

RtcEventLogEncoderNewFormat::RtcEventLogEncoderNewFormat(
    const FieldTrialsView& field_trials)
    : encode_neteq_set_minimum_delay_kill_switch_(field_trials.IsEnabled(
          "WebRTC-RtcEventLogEncodeNetEqSetMinimumDelayKillSwitch")),
      encode_dependency_descriptor_(!field_trials.IsDisabled(
          "WebRTC-RtcEventLogEncodeDependencyDescriptor")) {}

std::string RtcEventLogEncoderNewFormat::EncodeLogStart(int64_t timestamp_us,
                                                        int64_t utc_time_us) {
  rtclog2::EventStream event_stream;
  rtclog2::BeginLogEvent* proto_batch = event_stream.add_begin_log_events();
  proto_batch->set_timestamp_ms(timestamp_us / 1000);
  proto_batch->set_version(2);
  proto_batch->set_utc_time_ms(utc_time_us / 1000);
  return event_stream.SerializeAsString();
}

std::string RtcEventLogEncoderNewFormat::EncodeLogEnd(int64_t timestamp_us) {
  rtclog2::EventStream event_stream;
  rtclog2::EndLogEvent* proto_batch = event_stream.add_end_log_events();
  proto_batch->set_timestamp_ms(timestamp_us / 1000);
  return event_stream.SerializeAsString();
}

std::string RtcEventLogEncoderNewFormat::EncodeBatch(
    std::deque<std::unique_ptr<RtcEvent>>::const_iterator begin,
    std::deque<std::unique_ptr<RtcEvent>>::const_iterator end) {
  rtclog2::EventStream event_stream;
  std::string encoded_output;

  {
    std::vector<const RtcEventAlrState*> alr_state_events;
    std::vector<const RtcEventAudioNetworkAdaptation*>
        audio_network_adaptation_events;
    std::vector<const RtcEventAudioPlayout*> audio_playout_events;
    std::vector<const RtcEventNetEqSetMinimumDelay*>
        neteq_set_minimum_delay_events;
    std::vector<const RtcEventAudioReceiveStreamConfig*>
        audio_recv_stream_configs;
    std::vector<const RtcEventAudioSendStreamConfig*> audio_send_stream_configs;
    std::vector<const RtcEventBweUpdateDelayBased*> bwe_delay_based_updates;
    std::vector<const RtcEventBweUpdateLossBased*> bwe_loss_based_updates;
    std::vector<const RtcEventDtlsTransportState*> dtls_transport_states;
    std::vector<const RtcEventDtlsWritableState*> dtls_writable_states;
    std::map<uint32_t /* SSRC */, std::vector<const RtcEventFrameDecoded*>>
        frames_decoded;
    std::vector<const RtcEventGenericAckReceived*> generic_acks_received;
    std::vector<const RtcEventGenericPacketReceived*> generic_packets_received;
    std::vector<const RtcEventGenericPacketSent*> generic_packets_sent;
    std::vector<const RtcEventIceCandidatePair*> ice_candidate_events;
    std::vector<const RtcEventIceCandidatePairConfig*> ice_candidate_configs;
    std::vector<const RtcEventProbeClusterCreated*>
        probe_cluster_created_events;
    std::vector<const RtcEventProbeResultFailure*> probe_result_failure_events;
    std::vector<const RtcEventProbeResultSuccess*> probe_result_success_events;
    std::vector<const RtcEventRouteChange*> route_change_events;
    std::vector<const RtcEventRemoteEstimate*> remote_estimate_events;
    std::vector<const RtcEventRtcpPacketIncoming*> incoming_rtcp_packets;
    std::vector<const RtcEventRtcpPacketOutgoing*> outgoing_rtcp_packets;
    std::map<uint32_t /* SSRC */, std::vector<const RtcEventRtpPacketIncoming*>>
        incoming_rtp_packets;
    std::map<uint32_t /* SSRC */, std::vector<const RtcEventRtpPacketOutgoing*>>
        outgoing_rtp_packets;
    std::vector<const RtcEventVideoReceiveStreamConfig*>
        video_recv_stream_configs;
    std::vector<const RtcEventVideoSendStreamConfig*> video_send_stream_configs;

    for (auto it = begin; it != end; ++it) {
      switch ((*it)->GetType()) {
        case RtcEvent::Type::AlrStateEvent: {
          auto* rtc_event =
              static_cast<const RtcEventAlrState* const>(it->get());
          alr_state_events.push_back(rtc_event);
          break;
        }
        case RtcEvent::Type::AudioNetworkAdaptation: {
          auto* rtc_event =
              static_cast<const RtcEventAudioNetworkAdaptation* const>(
                  it->get());
          audio_network_adaptation_events.push_back(rtc_event);
          break;
        }
        case RtcEvent::Type::AudioPlayout: {
          auto* rtc_event =
              static_cast<const RtcEventAudioPlayout* const>(it->get());
          audio_playout_events.push_back(rtc_event);
          break;
        }
        case RtcEvent::Type::AudioReceiveStreamConfig: {
          auto* rtc_event =
              static_cast<const RtcEventAudioReceiveStreamConfig* const>(
                  it->get());
          audio_recv_stream_configs.push_back(rtc_event);
          break;
        }
        case RtcEvent::Type::AudioSendStreamConfig: {
          auto* rtc_event =
              static_cast<const RtcEventAudioSendStreamConfig* const>(
                  it->get());
          audio_send_stream_configs.push_back(rtc_event);
          break;
        }
        case RtcEvent::Type::BweUpdateDelayBased: {
          auto* rtc_event =
              static_cast<const RtcEventBweUpdateDelayBased* const>(it->get());
          bwe_delay_based_updates.push_back(rtc_event);
          break;
        }
        case RtcEvent::Type::BweUpdateLossBased: {
          auto* rtc_event =
              static_cast<const RtcEventBweUpdateLossBased* const>(it->get());
          bwe_loss_based_updates.push_back(rtc_event);
          break;
        }
        case RtcEvent::Type::DtlsTransportState: {
          auto* rtc_event =
              static_cast<const RtcEventDtlsTransportState* const>(it->get());
          dtls_transport_states.push_back(rtc_event);
          break;
        }
        case RtcEvent::Type::DtlsWritableState: {
          auto* rtc_event =
              static_cast<const RtcEventDtlsWritableState* const>(it->get());
          dtls_writable_states.push_back(rtc_event);
          break;
        }
        case RtcEvent::Type::ProbeClusterCreated: {
          auto* rtc_event =
              static_cast<const RtcEventProbeClusterCreated* const>(it->get());
          probe_cluster_created_events.push_back(rtc_event);
          break;
        }
        case RtcEvent::Type::ProbeResultFailure: {
          auto* rtc_event =
              static_cast<const RtcEventProbeResultFailure* const>(it->get());
          probe_result_failure_events.push_back(rtc_event);
          break;
        }
        case RtcEvent::Type::ProbeResultSuccess: {
          auto* rtc_event =
              static_cast<const RtcEventProbeResultSuccess* const>(it->get());
          probe_result_success_events.push_back(rtc_event);
          break;
        }
        case RtcEvent::Type::RouteChangeEvent: {
          auto* rtc_event =
              static_cast<const RtcEventRouteChange* const>(it->get());
          route_change_events.push_back(rtc_event);
          break;
        }
        case RtcEvent::Type::RemoteEstimateEvent: {
          auto* rtc_event =
              static_cast<const RtcEventRemoteEstimate* const>(it->get());
          remote_estimate_events.push_back(rtc_event);
          break;
        }
        case RtcEvent::Type::RtcpPacketIncoming: {
          auto* rtc_event =
              static_cast<const RtcEventRtcpPacketIncoming* const>(it->get());
          incoming_rtcp_packets.push_back(rtc_event);
          break;
        }
        case RtcEvent::Type::RtcpPacketOutgoing: {
          auto* rtc_event =
              static_cast<const RtcEventRtcpPacketOutgoing* const>(it->get());
          outgoing_rtcp_packets.push_back(rtc_event);
          break;
        }
        case RtcEvent::Type::RtpPacketIncoming: {
          auto* rtc_event =
              static_cast<const RtcEventRtpPacketIncoming* const>(it->get());
          auto& v = incoming_rtp_packets[rtc_event->Ssrc()];
          v.emplace_back(rtc_event);
          break;
        }
        case RtcEvent::Type::RtpPacketOutgoing: {
          auto* rtc_event =
              static_cast<const RtcEventRtpPacketOutgoing* const>(it->get());
          auto& v = outgoing_rtp_packets[rtc_event->Ssrc()];
          v.emplace_back(rtc_event);
          break;
        }
        case RtcEvent::Type::VideoReceiveStreamConfig: {
          auto* rtc_event =
              static_cast<const RtcEventVideoReceiveStreamConfig* const>(
                  it->get());
          video_recv_stream_configs.push_back(rtc_event);
          break;
        }
        case RtcEvent::Type::VideoSendStreamConfig: {
          auto* rtc_event =
              static_cast<const RtcEventVideoSendStreamConfig* const>(
                  it->get());
          video_send_stream_configs.push_back(rtc_event);
          break;
        }
        case RtcEvent::Type::IceCandidatePairConfig: {
          auto* rtc_event =
              static_cast<const RtcEventIceCandidatePairConfig* const>(
                  it->get());
          ice_candidate_configs.push_back(rtc_event);
          break;
        }
        case RtcEvent::Type::IceCandidatePairEvent: {
          auto* rtc_event =
              static_cast<const RtcEventIceCandidatePair* const>(it->get());
          ice_candidate_events.push_back(rtc_event);
          break;
        }
        case RtcEvent::Type::GenericPacketReceived: {
          auto* rtc_event =
              static_cast<const RtcEventGenericPacketReceived* const>(
                  it->get());
          generic_packets_received.push_back(rtc_event);
          break;
        }
        case RtcEvent::Type::GenericPacketSent: {
          auto* rtc_event =
              static_cast<const RtcEventGenericPacketSent* const>(it->get());
          generic_packets_sent.push_back(rtc_event);
          break;
        }
        case RtcEvent::Type::GenericAckReceived: {
          auto* rtc_event =
              static_cast<const RtcEventGenericAckReceived* const>(it->get());
          generic_acks_received.push_back(rtc_event);
          break;
        }
        case RtcEvent::Type::FrameDecoded: {
          auto* rtc_event =
              static_cast<const RtcEventFrameDecoded* const>(it->get());
          frames_decoded[rtc_event->ssrc()].emplace_back(rtc_event);
          break;
        }
        case RtcEvent::Type::NetEqSetMinimumDelay: {
          auto* rtc_event =
              static_cast<const RtcEventNetEqSetMinimumDelay* const>(it->get());
          neteq_set_minimum_delay_events.push_back(rtc_event);
          break;
        }
        case RtcEvent::Type::BeginV3Log:
        case RtcEvent::Type::EndV3Log:
          // These special events are written as part of starting
          // and stopping the log, and only as part of version 3 of the format.
          RTC_DCHECK_NOTREACHED();
          break;
        case RtcEvent::Type::FakeEvent:
          // Fake event used for unit test.
          RTC_DCHECK_NOTREACHED();
          break;
      }
    }

    EncodeAlrState(alr_state_events, &event_stream);
    EncodeAudioNetworkAdaptation(audio_network_adaptation_events,
                                 &event_stream);
    EncodeAudioPlayout(audio_playout_events, &event_stream);
    EncodeAudioRecvStreamConfig(audio_recv_stream_configs, &event_stream);
    EncodeAudioSendStreamConfig(audio_send_stream_configs, &event_stream);
    EncodeNetEqSetMinimumDelay(neteq_set_minimum_delay_events, &event_stream);
    EncodeBweUpdateDelayBased(bwe_delay_based_updates, &event_stream);
    EncodeBweUpdateLossBased(bwe_loss_based_updates, &event_stream);
    EncodeDtlsTransportState(dtls_transport_states, &event_stream);
    EncodeDtlsWritableState(dtls_writable_states, &event_stream);
    for (const auto& kv : frames_decoded) {
      EncodeFramesDecoded(kv.second, &event_stream);
    }
    EncodeGenericAcksReceived(generic_acks_received, &event_stream);
    EncodeGenericPacketsReceived(generic_packets_received, &event_stream);
    EncodeGenericPacketsSent(generic_packets_sent, &event_stream);
    EncodeIceCandidatePairConfig(ice_candidate_configs, &event_stream);
    EncodeIceCandidatePairEvent(ice_candidate_events, &event_stream);
    EncodeProbeClusterCreated(probe_cluster_created_events, &event_stream);
    EncodeProbeResultFailure(probe_result_failure_events, &event_stream);
    EncodeProbeResultSuccess(probe_result_success_events, &event_stream);
    EncodeRouteChange(route_change_events, &event_stream);
    EncodeRemoteEstimate(remote_estimate_events, &event_stream);
    EncodeRtcpPacketIncoming(incoming_rtcp_packets, &event_stream);
    EncodeRtcpPacketOutgoing(outgoing_rtcp_packets, &event_stream);
    EncodeRtpPacketIncoming(incoming_rtp_packets, &event_stream);
    EncodeRtpPacketOutgoing(outgoing_rtp_packets, &event_stream);
    EncodeVideoRecvStreamConfig(video_recv_stream_configs, &event_stream);
    EncodeVideoSendStreamConfig(video_send_stream_configs, &event_stream);
  }  // Deallocate the temporary vectors.

  return event_stream.SerializeAsString();
}

void RtcEventLogEncoderNewFormat::EncodeAlrState(
    rtc::ArrayView<const RtcEventAlrState*> batch,
    rtclog2::EventStream* event_stream) {
  for (const RtcEventAlrState* base_event : batch) {
    rtclog2::AlrState* proto_batch = event_stream->add_alr_states();
    proto_batch->set_timestamp_ms(base_event->timestamp_ms());
    proto_batch->set_in_alr(base_event->in_alr());
  }
  // TODO(terelius): Should we delta-compress this event type?
}

void RtcEventLogEncoderNewFormat::EncodeAudioNetworkAdaptation(
    rtc::ArrayView<const RtcEventAudioNetworkAdaptation*> batch,
    rtclog2::EventStream* event_stream) {
  if (batch.empty())
    return;

  // Base event
  const RtcEventAudioNetworkAdaptation* const base_event = batch[0];
  rtclog2::AudioNetworkAdaptations* proto_batch =
      event_stream->add_audio_network_adaptations();
  proto_batch->set_timestamp_ms(base_event->timestamp_ms());
  if (base_event->config().bitrate_bps.has_value())
    proto_batch->set_bitrate_bps(base_event->config().bitrate_bps.value());
  if (base_event->config().frame_length_ms.has_value()) {
    proto_batch->set_frame_length_ms(
        base_event->config().frame_length_ms.value());
  }
  absl::optional<uint64_t> base_uplink_packet_loss_fraction;
  if (base_event->config().uplink_packet_loss_fraction.has_value()) {
    base_uplink_packet_loss_fraction = ConvertPacketLossFractionToProtoFormat(
        base_event->config().uplink_packet_loss_fraction.value());
    proto_batch->set_uplink_packet_loss_fraction(
        base_uplink_packet_loss_fraction.value());
  }
  if (base_event->config().enable_fec.has_value())
    proto_batch->set_enable_fec(base_event->config().enable_fec.value());
  if (base_event->config().enable_dtx.has_value())
    proto_batch->set_enable_dtx(base_event->config().enable_dtx.value());
  // Note that `num_channels_deltas` encodes N as N-1, to keep deltas smaller,
  // but there's no reason to do the same for the base event's value, since
  // no bits will be spared.
  if (base_event->config().num_channels.has_value())
    proto_batch->set_num_channels(base_event->config().num_channels.value());

  if (batch.size() == 1)
    return;

  // Delta encoding
  proto_batch->set_number_of_deltas(batch.size() - 1);
  std::vector<absl::optional<uint64_t>> values(batch.size() - 1);
  std::string encoded_deltas;

  // timestamp_ms
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventAudioNetworkAdaptation* event = batch[i + 1];
    values[i] = ToUnsigned(event->timestamp_ms());
  }
  encoded_deltas = EncodeDeltas(ToUnsigned(base_event->timestamp_ms()), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_timestamp_ms_deltas(encoded_deltas);
  }

  // bitrate_bps
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventAudioNetworkAdaptation* event = batch[i + 1];
    if (event->config().bitrate_bps.has_value()) {
      values[i] = ToUnsigned(event->config().bitrate_bps.value());
    } else {
      values[i].reset();
    }
  }
  const absl::optional<uint64_t> unsigned_base_bitrate_bps =
      base_event->config().bitrate_bps.has_value()
          ? ToUnsigned(base_event->config().bitrate_bps.value())
          : absl::optional<uint64_t>();
  encoded_deltas = EncodeDeltas(unsigned_base_bitrate_bps, values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_bitrate_bps_deltas(encoded_deltas);
  }

  // frame_length_ms
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventAudioNetworkAdaptation* event = batch[i + 1];
    if (event->config().frame_length_ms.has_value()) {
      values[i] = ToUnsigned(event->config().frame_length_ms.value());
    } else {
      values[i].reset();
    }
  }
  const absl::optional<uint64_t> unsigned_base_frame_length_ms =
      base_event->config().frame_length_ms.has_value()
          ? ToUnsigned(base_event->config().frame_length_ms.value())
          : absl::optional<uint64_t>();
  encoded_deltas = EncodeDeltas(unsigned_base_frame_length_ms, values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_frame_length_ms_deltas(encoded_deltas);
  }

  // uplink_packet_loss_fraction
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventAudioNetworkAdaptation* event = batch[i + 1];
    if (event->config().uplink_packet_loss_fraction.has_value()) {
      values[i] = ConvertPacketLossFractionToProtoFormat(
          event->config().uplink_packet_loss_fraction.value());
    } else {
      values[i].reset();
    }
  }
  encoded_deltas = EncodeDeltas(base_uplink_packet_loss_fraction, values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_uplink_packet_loss_fraction_deltas(encoded_deltas);
  }

  // enable_fec
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventAudioNetworkAdaptation* event = batch[i + 1];
    values[i] = event->config().enable_fec;
  }
  encoded_deltas = EncodeDeltas(base_event->config().enable_fec, values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_enable_fec_deltas(encoded_deltas);
  }

  // enable_dtx
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventAudioNetworkAdaptation* event = batch[i + 1];
    values[i] = event->config().enable_dtx;
  }
  encoded_deltas = EncodeDeltas(base_event->config().enable_dtx, values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_enable_dtx_deltas(encoded_deltas);
  }

  // num_channels
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventAudioNetworkAdaptation* event = batch[i + 1];
    const absl::optional<size_t> num_channels = event->config().num_channels;
    if (num_channels.has_value()) {
      // Since the number of channels is always greater than 0, we can encode
      // N channels as N-1, thereby making sure that we get smaller deltas.
      // That is, a toggle of 1->2->1 can be encoded as deltas vector (1, 1),
      // rather than as (1, 3) or (1, -1), either of which would require two
      // bits per delta.
      RTC_DCHECK_GT(num_channels.value(), 0u);
      values[i] = num_channels.value() - 1;
    } else {
      values[i].reset();
    }
  }
  // In the base event, N channels encoded as N channels, but for delta
  // compression purposes, also shifted down by 1.
  absl::optional<size_t> shifted_base_num_channels;
  if (base_event->config().num_channels.has_value()) {
    RTC_DCHECK_GT(base_event->config().num_channels.value(), 0u);
    shifted_base_num_channels = base_event->config().num_channels.value() - 1;
  }
  encoded_deltas = EncodeDeltas(shifted_base_num_channels, values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_num_channels_deltas(encoded_deltas);
  }
}

void RtcEventLogEncoderNewFormat::EncodeAudioPlayout(
    rtc::ArrayView<const RtcEventAudioPlayout*> batch,
    rtclog2::EventStream* event_stream) {
  if (batch.empty())
    return;

  // Base event
  const RtcEventAudioPlayout* const base_event = batch[0];
  rtclog2::AudioPlayoutEvents* proto_batch =
      event_stream->add_audio_playout_events();
  proto_batch->set_timestamp_ms(base_event->timestamp_ms());
  proto_batch->set_local_ssrc(base_event->ssrc());

  if (batch.size() == 1)
    return;

  // Delta encoding
  proto_batch->set_number_of_deltas(batch.size() - 1);
  std::vector<absl::optional<uint64_t>> values(batch.size() - 1);
  std::string encoded_deltas;

  // timestamp_ms
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventAudioPlayout* event = batch[i + 1];
    values[i] = ToUnsigned(event->timestamp_ms());
  }
  encoded_deltas = EncodeDeltas(ToUnsigned(base_event->timestamp_ms()), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_timestamp_ms_deltas(encoded_deltas);
  }

  // local_ssrc
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventAudioPlayout* event = batch[i + 1];
    values[i] = event->ssrc();
  }
  encoded_deltas = EncodeDeltas(base_event->ssrc(), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_local_ssrc_deltas(encoded_deltas);
  }
}

void RtcEventLogEncoderNewFormat::EncodeNetEqSetMinimumDelay(
    rtc::ArrayView<const RtcEventNetEqSetMinimumDelay*> batch,
    rtclog2::EventStream* event_stream) {
  if (encode_neteq_set_minimum_delay_kill_switch_) {
    return;
  }
  if (batch.empty()) {
    return;
  }

  const RtcEventNetEqSetMinimumDelay* base_event = batch[0];

  rtclog2::NetEqSetMinimumDelay* proto_batch =
      event_stream->add_neteq_set_minimum_delay();
  proto_batch->set_timestamp_ms(base_event->timestamp_ms());
  proto_batch->set_remote_ssrc(base_event->remote_ssrc());
  proto_batch->set_minimum_delay_ms(base_event->minimum_delay_ms());

  if (batch.size() == 1)
    return;

  // Delta encoding
  proto_batch->set_number_of_deltas(batch.size() - 1);
  std::vector<absl::optional<uint64_t>> values(batch.size() - 1);
  std::string encoded_deltas;

  // timestamp_ms
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventNetEqSetMinimumDelay* event = batch[i + 1];
    values[i] = ToUnsigned(event->timestamp_ms());
  }
  encoded_deltas = EncodeDeltas(ToUnsigned(base_event->timestamp_ms()), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_timestamp_ms_deltas(encoded_deltas);
  }

  // remote_ssrc
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventNetEqSetMinimumDelay* event = batch[i + 1];
    values[i] = event->remote_ssrc();
  }
  encoded_deltas = EncodeDeltas(base_event->remote_ssrc(), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_remote_ssrc_deltas(encoded_deltas);
  }

  // minimum_delay_ms
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventNetEqSetMinimumDelay* event = batch[i + 1];
    values[i] = ToUnsigned(event->minimum_delay_ms());
  }
  encoded_deltas =
      EncodeDeltas(ToUnsigned(base_event->minimum_delay_ms()), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_minimum_delay_ms_deltas(encoded_deltas);
  }
}

void RtcEventLogEncoderNewFormat::EncodeAudioRecvStreamConfig(
    rtc::ArrayView<const RtcEventAudioReceiveStreamConfig*> batch,
    rtclog2::EventStream* event_stream) {
  for (const RtcEventAudioReceiveStreamConfig* base_event : batch) {
    rtclog2::AudioRecvStreamConfig* proto_batch =
        event_stream->add_audio_recv_stream_configs();
    proto_batch->set_timestamp_ms(base_event->timestamp_ms());
    proto_batch->set_remote_ssrc(base_event->config().remote_ssrc);
    proto_batch->set_local_ssrc(base_event->config().local_ssrc);

    rtclog2::RtpHeaderExtensionConfig* proto_config =
        proto_batch->mutable_header_extensions();
    bool has_recognized_extensions =
        ConvertToProtoFormat(base_event->config().rtp_extensions, proto_config);
    if (!has_recognized_extensions)
      proto_batch->clear_header_extensions();
  }
}

void RtcEventLogEncoderNewFormat::EncodeAudioSendStreamConfig(
    rtc::ArrayView<const RtcEventAudioSendStreamConfig*> batch,
    rtclog2::EventStream* event_stream) {
  for (const RtcEventAudioSendStreamConfig* base_event : batch) {
    rtclog2::AudioSendStreamConfig* proto_batch =
        event_stream->add_audio_send_stream_configs();
    proto_batch->set_timestamp_ms(base_event->timestamp_ms());
    proto_batch->set_ssrc(base_event->config().local_ssrc);

    rtclog2::RtpHeaderExtensionConfig* proto_config =
        proto_batch->mutable_header_extensions();
    bool has_recognized_extensions =
        ConvertToProtoFormat(base_event->config().rtp_extensions, proto_config);
    if (!has_recognized_extensions)
      proto_batch->clear_header_extensions();
  }
}

void RtcEventLogEncoderNewFormat::EncodeBweUpdateDelayBased(
    rtc::ArrayView<const RtcEventBweUpdateDelayBased*> batch,
    rtclog2::EventStream* event_stream) {
  if (batch.empty())
    return;

  // Base event
  const RtcEventBweUpdateDelayBased* const base_event = batch[0];
  rtclog2::DelayBasedBweUpdates* proto_batch =
      event_stream->add_delay_based_bwe_updates();
  proto_batch->set_timestamp_ms(base_event->timestamp_ms());
  proto_batch->set_bitrate_bps(base_event->bitrate_bps());
  proto_batch->set_detector_state(
      ConvertToProtoFormat(base_event->detector_state()));

  if (batch.size() == 1)
    return;

  // Delta encoding
  proto_batch->set_number_of_deltas(batch.size() - 1);
  std::vector<absl::optional<uint64_t>> values(batch.size() - 1);
  std::string encoded_deltas;

  // timestamp_ms
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventBweUpdateDelayBased* event = batch[i + 1];
    values[i] = ToUnsigned(event->timestamp_ms());
  }
  encoded_deltas = EncodeDeltas(ToUnsigned(base_event->timestamp_ms()), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_timestamp_ms_deltas(encoded_deltas);
  }

  // bitrate_bps
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventBweUpdateDelayBased* event = batch[i + 1];
    values[i] = event->bitrate_bps();
  }
  encoded_deltas = EncodeDeltas(base_event->bitrate_bps(), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_bitrate_bps_deltas(encoded_deltas);
  }

  // detector_state
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventBweUpdateDelayBased* event = batch[i + 1];
    values[i] =
        static_cast<uint64_t>(ConvertToProtoFormat(event->detector_state()));
  }
  encoded_deltas = EncodeDeltas(
      static_cast<uint64_t>(ConvertToProtoFormat(base_event->detector_state())),
      values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_detector_state_deltas(encoded_deltas);
  }
}

void RtcEventLogEncoderNewFormat::EncodeBweUpdateLossBased(
    rtc::ArrayView<const RtcEventBweUpdateLossBased*> batch,
    rtclog2::EventStream* event_stream) {
  if (batch.empty())
    return;

  // Base event
  const RtcEventBweUpdateLossBased* const base_event = batch[0];
  rtclog2::LossBasedBweUpdates* proto_batch =
      event_stream->add_loss_based_bwe_updates();
  proto_batch->set_timestamp_ms(base_event->timestamp_ms());
  proto_batch->set_bitrate_bps(base_event->bitrate_bps());
  proto_batch->set_fraction_loss(base_event->fraction_loss());
  proto_batch->set_total_packets(base_event->total_packets());

  if (batch.size() == 1)
    return;

  // Delta encoding
  proto_batch->set_number_of_deltas(batch.size() - 1);
  std::vector<absl::optional<uint64_t>> values(batch.size() - 1);
  std::string encoded_deltas;

  // timestamp_ms
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventBweUpdateLossBased* event = batch[i + 1];
    values[i] = ToUnsigned(event->timestamp_ms());
  }
  encoded_deltas = EncodeDeltas(ToUnsigned(base_event->timestamp_ms()), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_timestamp_ms_deltas(encoded_deltas);
  }

  // bitrate_bps
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventBweUpdateLossBased* event = batch[i + 1];
    values[i] = event->bitrate_bps();
  }
  encoded_deltas = EncodeDeltas(base_event->bitrate_bps(), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_bitrate_bps_deltas(encoded_deltas);
  }

  // fraction_loss
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventBweUpdateLossBased* event = batch[i + 1];
    values[i] = event->fraction_loss();
  }
  encoded_deltas = EncodeDeltas(base_event->fraction_loss(), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_fraction_loss_deltas(encoded_deltas);
  }

  // total_packets
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventBweUpdateLossBased* event = batch[i + 1];
    values[i] = event->total_packets();
  }
  encoded_deltas = EncodeDeltas(base_event->total_packets(), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_total_packets_deltas(encoded_deltas);
  }
}

void RtcEventLogEncoderNewFormat::EncodeDtlsTransportState(
    rtc::ArrayView<const RtcEventDtlsTransportState*> batch,
    rtclog2::EventStream* event_stream) {
  for (const RtcEventDtlsTransportState* base_event : batch) {
    rtclog2::DtlsTransportStateEvent* proto_batch =
        event_stream->add_dtls_transport_state_events();
    proto_batch->set_timestamp_ms(base_event->timestamp_ms());
    proto_batch->set_dtls_transport_state(
        ConvertToProtoFormat(base_event->dtls_transport_state()));
  }
}

void RtcEventLogEncoderNewFormat::EncodeDtlsWritableState(
    rtc::ArrayView<const RtcEventDtlsWritableState*> batch,
    rtclog2::EventStream* event_stream) {
  for (const RtcEventDtlsWritableState* base_event : batch) {
    rtclog2::DtlsWritableState* proto_batch =
        event_stream->add_dtls_writable_states();
    proto_batch->set_timestamp_ms(base_event->timestamp_ms());
    proto_batch->set_writable(base_event->writable());
  }
}

void RtcEventLogEncoderNewFormat::EncodeProbeClusterCreated(
    rtc::ArrayView<const RtcEventProbeClusterCreated*> batch,
    rtclog2::EventStream* event_stream) {
  for (const RtcEventProbeClusterCreated* base_event : batch) {
    rtclog2::BweProbeCluster* proto_batch = event_stream->add_probe_clusters();
    proto_batch->set_timestamp_ms(base_event->timestamp_ms());
    proto_batch->set_id(base_event->id());
    proto_batch->set_bitrate_bps(base_event->bitrate_bps());
    proto_batch->set_min_packets(base_event->min_probes());
    proto_batch->set_min_bytes(base_event->min_bytes());
  }
}

void RtcEventLogEncoderNewFormat::EncodeProbeResultFailure(
    rtc::ArrayView<const RtcEventProbeResultFailure*> batch,
    rtclog2::EventStream* event_stream) {
  for (const RtcEventProbeResultFailure* base_event : batch) {
    rtclog2::BweProbeResultFailure* proto_batch =
        event_stream->add_probe_failure();
    proto_batch->set_timestamp_ms(base_event->timestamp_ms());
    proto_batch->set_id(base_event->id());
    proto_batch->set_failure(
        ConvertToProtoFormat(base_event->failure_reason()));
  }
  // TODO(terelius): Should we delta-compress this event type?
}

void RtcEventLogEncoderNewFormat::EncodeProbeResultSuccess(
    rtc::ArrayView<const RtcEventProbeResultSuccess*> batch,
    rtclog2::EventStream* event_stream) {
  for (const RtcEventProbeResultSuccess* base_event : batch) {
    rtclog2::BweProbeResultSuccess* proto_batch =
        event_stream->add_probe_success();
    proto_batch->set_timestamp_ms(base_event->timestamp_ms());
    proto_batch->set_id(base_event->id());
    proto_batch->set_bitrate_bps(base_event->bitrate_bps());
  }
  // TODO(terelius): Should we delta-compress this event type?
}

void RtcEventLogEncoderNewFormat::EncodeRouteChange(
    rtc::ArrayView<const RtcEventRouteChange*> batch,
    rtclog2::EventStream* event_stream) {
  for (const RtcEventRouteChange* base_event : batch) {
    rtclog2::RouteChange* proto_batch = event_stream->add_route_changes();
    proto_batch->set_timestamp_ms(base_event->timestamp_ms());
    proto_batch->set_connected(base_event->connected());
    proto_batch->set_overhead(base_event->overhead());
  }
  // TODO(terelius): Should we delta-compress this event type?
}

void RtcEventLogEncoderNewFormat::EncodeRemoteEstimate(
    rtc::ArrayView<const RtcEventRemoteEstimate*> batch,
    rtclog2::EventStream* event_stream) {
  if (batch.empty())
    return;

  // Base event
  const auto* const base_event = batch[0];
  rtclog2::RemoteEstimates* proto_batch = event_stream->add_remote_estimates();

  proto_batch->set_timestamp_ms(base_event->timestamp_ms());

  absl::optional<uint64_t> base_link_capacity_lower;
  if (base_event->link_capacity_lower_.IsFinite()) {
    base_link_capacity_lower =
        base_event->link_capacity_lower_.kbps<uint32_t>();
    proto_batch->set_link_capacity_lower_kbps(*base_link_capacity_lower);
  }
  absl::optional<uint64_t> base_link_capacity_upper;
  if (base_event->link_capacity_upper_.IsFinite()) {
    base_link_capacity_upper =
        base_event->link_capacity_upper_.kbps<uint32_t>();
    proto_batch->set_link_capacity_upper_kbps(*base_link_capacity_upper);
  }

  if (batch.size() == 1)
    return;

  // Delta encoding
  proto_batch->set_number_of_deltas(batch.size() - 1);
  std::vector<absl::optional<uint64_t>> values(batch.size() - 1);
  std::string encoded_deltas;

  // timestamp_ms
  for (size_t i = 0; i < values.size(); ++i) {
    const auto* event = batch[i + 1];
    values[i] = ToUnsigned(event->timestamp_ms());
  }
  encoded_deltas = EncodeDeltas(ToUnsigned(base_event->timestamp_ms()), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_timestamp_ms_deltas(encoded_deltas);
  }

  // link_capacity_lower_kbps
  for (size_t i = 0; i < values.size(); ++i) {
    const auto* event = batch[i + 1];
    if (event->link_capacity_lower_.IsFinite()) {
      values[i] = event->link_capacity_lower_.kbps<uint32_t>();
    } else {
      values[i].reset();
    }
  }
  encoded_deltas = EncodeDeltas(base_link_capacity_lower, values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_link_capacity_lower_kbps_deltas(encoded_deltas);
  }

  // link_capacity_upper_kbps
  for (size_t i = 0; i < values.size(); ++i) {
    const auto* event = batch[i + 1];
    if (event->link_capacity_upper_.IsFinite()) {
      values[i] = event->link_capacity_upper_.kbps<uint32_t>();
    } else {
      values[i].reset();
    }
  }
  encoded_deltas = EncodeDeltas(base_link_capacity_upper, values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_link_capacity_upper_kbps_deltas(encoded_deltas);
  }
}

void RtcEventLogEncoderNewFormat::EncodeRtcpPacketIncoming(
    rtc::ArrayView<const RtcEventRtcpPacketIncoming*> batch,
    rtclog2::EventStream* event_stream) {
  if (batch.empty()) {
    return;
  }
  EncodeRtcpPacket(batch, event_stream->add_incoming_rtcp_packets());
}

void RtcEventLogEncoderNewFormat::EncodeRtcpPacketOutgoing(
    rtc::ArrayView<const RtcEventRtcpPacketOutgoing*> batch,
    rtclog2::EventStream* event_stream) {
  if (batch.empty()) {
    return;
  }
  EncodeRtcpPacket(batch, event_stream->add_outgoing_rtcp_packets());
}

void RtcEventLogEncoderNewFormat::EncodeRtpPacketIncoming(
    const std::map<uint32_t, std::vector<const RtcEventRtpPacketIncoming*>>&
        batch,
    rtclog2::EventStream* event_stream) {
  for (const auto& it : batch) {
    RTC_DCHECK(!it.second.empty());
    EncodeRtpPacket(it.second, event_stream->add_incoming_rtp_packets());
  }
}

void RtcEventLogEncoderNewFormat::EncodeFramesDecoded(
    rtc::ArrayView<const RtcEventFrameDecoded* const> batch,
    rtclog2::EventStream* event_stream) {
  if (batch.empty()) {
    return;
  }
  const RtcEventFrameDecoded* const base_event = batch[0];
  rtclog2::FrameDecodedEvents* proto_batch =
      event_stream->add_frame_decoded_events();
  proto_batch->set_timestamp_ms(base_event->timestamp_ms());
  proto_batch->set_ssrc(base_event->ssrc());
  proto_batch->set_render_time_ms(base_event->render_time_ms());
  proto_batch->set_width(base_event->width());
  proto_batch->set_height(base_event->height());
  proto_batch->set_codec(ConvertToProtoFormat(base_event->codec()));
  proto_batch->set_qp(base_event->qp());

  if (batch.size() == 1) {
    return;
  }

  // Delta encoding
  proto_batch->set_number_of_deltas(batch.size() - 1);
  std::vector<absl::optional<uint64_t>> values(batch.size() - 1);
  std::string encoded_deltas;

  // timestamp_ms
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventFrameDecoded* event = batch[i + 1];
    values[i] = ToUnsigned(event->timestamp_ms());
  }
  encoded_deltas = EncodeDeltas(ToUnsigned(base_event->timestamp_ms()), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_timestamp_ms_deltas(encoded_deltas);
  }

  // SSRC
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventFrameDecoded* event = batch[i + 1];
    values[i] = event->ssrc();
  }
  encoded_deltas = EncodeDeltas(base_event->ssrc(), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_ssrc_deltas(encoded_deltas);
  }

  // render_time_ms
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventFrameDecoded* event = batch[i + 1];
    values[i] = ToUnsigned(event->render_time_ms());
  }
  encoded_deltas =
      EncodeDeltas(ToUnsigned(base_event->render_time_ms()), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_render_time_ms_deltas(encoded_deltas);
  }

  // width
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventFrameDecoded* event = batch[i + 1];
    values[i] = ToUnsigned(event->width());
  }
  encoded_deltas = EncodeDeltas(ToUnsigned(base_event->width()), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_width_deltas(encoded_deltas);
  }

  // height
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventFrameDecoded* event = batch[i + 1];
    values[i] = ToUnsigned(event->height());
  }
  encoded_deltas = EncodeDeltas(ToUnsigned(base_event->height()), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_height_deltas(encoded_deltas);
  }

  // codec
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventFrameDecoded* event = batch[i + 1];
    values[i] = static_cast<uint64_t>(ConvertToProtoFormat(event->codec()));
  }
  encoded_deltas = EncodeDeltas(
      static_cast<uint64_t>(ConvertToProtoFormat(base_event->codec())), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_codec_deltas(encoded_deltas);
  }

  // qp
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventFrameDecoded* event = batch[i + 1];
    values[i] = event->qp();
  }
  encoded_deltas = EncodeDeltas(base_event->qp(), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_qp_deltas(encoded_deltas);
  }
}

void RtcEventLogEncoderNewFormat::EncodeGenericPacketsSent(
    rtc::ArrayView<const RtcEventGenericPacketSent*> batch,
    rtclog2::EventStream* event_stream) {
  if (batch.empty()) {
    return;
  }
  const RtcEventGenericPacketSent* const base_event = batch[0];
  rtclog2::GenericPacketSent* proto_batch =
      event_stream->add_generic_packets_sent();
  proto_batch->set_timestamp_ms(base_event->timestamp_ms());
  proto_batch->set_packet_number(base_event->packet_number());
  proto_batch->set_overhead_length(base_event->overhead_length());
  proto_batch->set_payload_length(base_event->payload_length());
  proto_batch->set_padding_length(base_event->padding_length());

  // Delta encoding
  proto_batch->set_number_of_deltas(batch.size() - 1);
  std::vector<absl::optional<uint64_t>> values(batch.size() - 1);
  std::string encoded_deltas;

  if (batch.size() == 1) {
    return;
  }

  // timestamp_ms
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventGenericPacketSent* event = batch[i + 1];
    values[i] = ToUnsigned(event->timestamp_ms());
  }
  encoded_deltas = EncodeDeltas(ToUnsigned(base_event->timestamp_ms()), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_timestamp_ms_deltas(encoded_deltas);
  }

  // packet_number
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventGenericPacketSent* event = batch[i + 1];
    values[i] = ToUnsigned(event->packet_number());
  }
  encoded_deltas =
      EncodeDeltas(ToUnsigned(base_event->packet_number()), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_packet_number_deltas(encoded_deltas);
  }

  // overhead_length
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventGenericPacketSent* event = batch[i + 1];
    values[i] = event->overhead_length();
  }
  encoded_deltas = EncodeDeltas(base_event->overhead_length(), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_overhead_length_deltas(encoded_deltas);
  }

  // payload_length
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventGenericPacketSent* event = batch[i + 1];
    values[i] = event->payload_length();
  }
  encoded_deltas = EncodeDeltas(base_event->payload_length(), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_payload_length_deltas(encoded_deltas);
  }

  // padding_length
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventGenericPacketSent* event = batch[i + 1];
    values[i] = event->padding_length();
  }
  encoded_deltas = EncodeDeltas(base_event->padding_length(), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_padding_length_deltas(encoded_deltas);
  }
}

void RtcEventLogEncoderNewFormat::EncodeGenericPacketsReceived(
    rtc::ArrayView<const RtcEventGenericPacketReceived*> batch,
    rtclog2::EventStream* event_stream) {
  if (batch.empty()) {
    return;
  }
  const RtcEventGenericPacketReceived* const base_event = batch[0];
  rtclog2::GenericPacketReceived* proto_batch =
      event_stream->add_generic_packets_received();
  proto_batch->set_timestamp_ms(base_event->timestamp_ms());
  proto_batch->set_packet_number(base_event->packet_number());
  proto_batch->set_packet_length(base_event->packet_length());

  // Delta encoding
  proto_batch->set_number_of_deltas(batch.size() - 1);
  std::vector<absl::optional<uint64_t>> values(batch.size() - 1);
  std::string encoded_deltas;

  if (batch.size() == 1) {
    return;
  }

  // timestamp_ms
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventGenericPacketReceived* event = batch[i + 1];
    values[i] = ToUnsigned(event->timestamp_ms());
  }
  encoded_deltas = EncodeDeltas(ToUnsigned(base_event->timestamp_ms()), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_timestamp_ms_deltas(encoded_deltas);
  }

  // packet_number
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventGenericPacketReceived* event = batch[i + 1];
    values[i] = ToUnsigned(event->packet_number());
  }
  encoded_deltas =
      EncodeDeltas(ToUnsigned(base_event->packet_number()), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_packet_number_deltas(encoded_deltas);
  }

  // packet_length
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventGenericPacketReceived* event = batch[i + 1];
    values[i] = event->packet_length();
  }
  encoded_deltas = EncodeDeltas(base_event->packet_length(), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_packet_length_deltas(encoded_deltas);
  }
}

void RtcEventLogEncoderNewFormat::EncodeGenericAcksReceived(
    rtc::ArrayView<const RtcEventGenericAckReceived*> batch,
    rtclog2::EventStream* event_stream) {
  if (batch.empty()) {
    return;
  }
  const RtcEventGenericAckReceived* const base_event = batch[0];
  rtclog2::GenericAckReceived* proto_batch =
      event_stream->add_generic_acks_received();
  proto_batch->set_timestamp_ms(base_event->timestamp_ms());
  proto_batch->set_packet_number(base_event->packet_number());
  proto_batch->set_acked_packet_number(base_event->acked_packet_number());
  absl::optional<uint64_t> base_receive_timestamp;
  if (base_event->receive_acked_packet_time_ms()) {
    int64_t receive_acked_packet_time_ms =
        base_event->receive_acked_packet_time_ms().value();
    base_receive_timestamp = ToUnsigned(receive_acked_packet_time_ms);
    proto_batch->set_receive_acked_packet_time_ms(receive_acked_packet_time_ms);
  }

  // Delta encoding
  proto_batch->set_number_of_deltas(batch.size() - 1);
  std::vector<absl::optional<uint64_t>> values(batch.size() - 1);
  std::string encoded_deltas;

  if (batch.size() == 1) {
    return;
  }

  // timestamp_ms
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventGenericAckReceived* event = batch[i + 1];
    values[i] = ToUnsigned(event->timestamp_ms());
  }
  encoded_deltas = EncodeDeltas(ToUnsigned(base_event->timestamp_ms()), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_timestamp_ms_deltas(encoded_deltas);
  }

  // packet_number
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventGenericAckReceived* event = batch[i + 1];
    values[i] = ToUnsigned(event->packet_number());
  }
  encoded_deltas =
      EncodeDeltas(ToUnsigned(base_event->packet_number()), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_packet_number_deltas(encoded_deltas);
  }

  // acked packet number
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventGenericAckReceived* event = batch[i + 1];
    values[i] = ToUnsigned(event->acked_packet_number());
  }
  encoded_deltas =
      EncodeDeltas(ToUnsigned(base_event->acked_packet_number()), values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_acked_packet_number_deltas(encoded_deltas);
  }

  // receive timestamp
  for (size_t i = 0; i < values.size(); ++i) {
    const RtcEventGenericAckReceived* event = batch[i + 1];
    if (event->receive_acked_packet_time_ms()) {
      values[i] = ToUnsigned(event->receive_acked_packet_time_ms().value());
    } else {
      values[i] = absl::nullopt;
    }
  }
  encoded_deltas = EncodeDeltas(base_receive_timestamp, values);
  if (!encoded_deltas.empty()) {
    proto_batch->set_receive_acked_packet_time_ms_deltas(encoded_deltas);
  }
}

void RtcEventLogEncoderNewFormat::EncodeRtpPacketOutgoing(
    const std::map<uint32_t, std::vector<const RtcEventRtpPacketOutgoing*>>&
        batch,
    rtclog2::EventStream* event_stream) {
  for (const auto& it : batch) {
    RTC_DCHECK(!it.second.empty());
    EncodeRtpPacket(it.second, event_stream->add_outgoing_rtp_packets());
  }
}

void RtcEventLogEncoderNewFormat::EncodeVideoRecvStreamConfig(
    rtc::ArrayView<const RtcEventVideoReceiveStreamConfig*> batch,
    rtclog2::EventStream* event_stream) {
  for (const RtcEventVideoReceiveStreamConfig* base_event : batch) {
    rtclog2::VideoRecvStreamConfig* proto_batch =
        event_stream->add_video_recv_stream_configs();
    proto_batch->set_timestamp_ms(base_event->timestamp_ms());
    proto_batch->set_remote_ssrc(base_event->config().remote_ssrc);
    proto_batch->set_local_ssrc(base_event->config().local_ssrc);
    proto_batch->set_rtx_ssrc(base_event->config().rtx_ssrc);

    rtclog2::RtpHeaderExtensionConfig* proto_config =
        proto_batch->mutable_header_extensions();
    bool has_recognized_extensions =
        ConvertToProtoFormat(base_event->config().rtp_extensions, proto_config);
    if (!has_recognized_extensions)
      proto_batch->clear_header_extensions();
  }
}

void RtcEventLogEncoderNewFormat::EncodeVideoSendStreamConfig(
    rtc::ArrayView<const RtcEventVideoSendStreamConfig*> batch,
    rtclog2::EventStream* event_stream) {
  for (const RtcEventVideoSendStreamConfig* base_event : batch) {
    rtclog2::VideoSendStreamConfig* proto_batch =
        event_stream->add_video_send_stream_configs();
    proto_batch->set_timestamp_ms(base_event->timestamp_ms());
    proto_batch->set_ssrc(base_event->config().local_ssrc);
    proto_batch->set_rtx_ssrc(base_event->config().rtx_ssrc);

    rtclog2::RtpHeaderExtensionConfig* proto_config =
        proto_batch->mutable_header_extensions();
    bool has_recognized_extensions =
        ConvertToProtoFormat(base_event->config().rtp_extensions, proto_config);
    if (!has_recognized_extensions)
      proto_batch->clear_header_extensions();
  }
}

void RtcEventLogEncoderNewFormat::EncodeIceCandidatePairConfig(
    rtc::ArrayView<const RtcEventIceCandidatePairConfig*> batch,
    rtclog2::EventStream* event_stream) {
  for (const RtcEventIceCandidatePairConfig* base_event : batch) {
    rtclog2::IceCandidatePairConfig* proto_batch =
        event_stream->add_ice_candidate_configs();

    proto_batch->set_timestamp_ms(base_event->timestamp_ms());
    proto_batch->set_config_type(ConvertToProtoFormat(base_event->type()));
    proto_batch->set_candidate_pair_id(base_event->candidate_pair_id());
    const auto& desc = base_event->candidate_pair_desc();
    proto_batch->set_local_candidate_type(
        ConvertToProtoFormat(desc.local_candidate_type));
    proto_batch->set_local_relay_protocol(
        ConvertToProtoFormat(desc.local_relay_protocol));
    proto_batch->set_local_network_type(
        ConvertToProtoFormat(desc.local_network_type));
    proto_batch->set_local_address_family(
        ConvertToProtoFormat(desc.local_address_family));
    proto_batch->set_remote_candidate_type(
        ConvertToProtoFormat(desc.remote_candidate_type));
    proto_batch->set_remote_address_family(
        ConvertToProtoFormat(desc.remote_address_family));
    proto_batch->set_candidate_pair_protocol(
        ConvertToProtoFormat(desc.candidate_pair_protocol));
  }
  // TODO(terelius): Should we delta-compress this event type?
}

void RtcEventLogEncoderNewFormat::EncodeIceCandidatePairEvent(
    rtc::ArrayView<const RtcEventIceCandidatePair*> batch,
    rtclog2::EventStream* event_stream) {
  for (const RtcEventIceCandidatePair* base_event : batch) {
    rtclog2::IceCandidatePairEvent* proto_batch =
        event_stream->add_ice_candidate_events();

    proto_batch->set_timestamp_ms(base_event->timestamp_ms());

    proto_batch->set_event_type(ConvertToProtoFormat(base_event->type()));
    proto_batch->set_candidate_pair_id(base_event->candidate_pair_id());
    proto_batch->set_transaction_id(base_event->transaction_id());
  }
  // TODO(terelius): Should we delta-compress this event type?
}

}  // namespace webrtc
