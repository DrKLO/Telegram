/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "logging/rtc_event_log/rtc_event_log_parser.h"

#include <stdint.h>
#include <string.h>

#include <algorithm>
#include <limits>
#include <map>
#include <utility>

#include "absl/memory/memory.h"
#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/network_state_predictor.h"
#include "api/rtc_event_log/rtc_event_log.h"
#include "api/rtp_headers.h"
#include "api/rtp_parameters.h"
#include "logging/rtc_event_log/encoder/blob_encoding.h"
#include "logging/rtc_event_log/encoder/delta_encoding.h"
#include "logging/rtc_event_log/encoder/rtc_event_log_encoder_common.h"
#include "logging/rtc_event_log/encoder/var_int.h"
#include "logging/rtc_event_log/events/logged_rtp_rtcp.h"
#include "logging/rtc_event_log/rtc_event_processor.h"
#include "modules/audio_coding/audio_network_adaptor/include/audio_network_adaptor.h"
#include "modules/include/module_common_types_public.h"
#include "modules/rtp_rtcp/include/rtp_cvo.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "modules/rtp_rtcp/source/byte_io.h"
#include "modules/rtp_rtcp/source/rtp_header_extensions.h"
#include "modules/rtp_rtcp/source/rtp_packet_received.h"
#include "rtc_base/checks.h"
#include "rtc_base/copy_on_write_buffer.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "rtc_base/numerics/sequence_number_util.h"
#include "rtc_base/protobuf_utils.h"
#include "rtc_base/system/file_wrapper.h"

// These macros were added to convert existing code using RTC_CHECKs
// to returning a Status object instead. Macros are necessary (over
// e.g. helper functions) since we want to return from the current
// function.
#define RTC_PARSE_CHECK_OR_RETURN(X)                                        \
  do {                                                                      \
    if (!(X))                                                               \
      return ParsedRtcEventLog::ParseStatus::Error(#X, __FILE__, __LINE__); \
  } while (0)

#define RTC_PARSE_CHECK_OR_RETURN_MESSAGE(X, M)                              \
  do {                                                                       \
    if (!(X))                                                                \
      return ParsedRtcEventLog::ParseStatus::Error((M), __FILE__, __LINE__); \
  } while (0)

#define RTC_PARSE_CHECK_OR_RETURN_OP(OP, X, Y)                          \
  do {                                                                  \
    if (!((X)OP(Y)))                                                    \
      return ParsedRtcEventLog::ParseStatus::Error(#X #OP #Y, __FILE__, \
                                                   __LINE__);           \
  } while (0)

#define RTC_PARSE_CHECK_OR_RETURN_EQ(X, Y) \
  RTC_PARSE_CHECK_OR_RETURN_OP(==, X, Y)

#define RTC_PARSE_CHECK_OR_RETURN_NE(X, Y) \
  RTC_PARSE_CHECK_OR_RETURN_OP(!=, X, Y)

#define RTC_PARSE_CHECK_OR_RETURN_LT(X, Y) RTC_PARSE_CHECK_OR_RETURN_OP(<, X, Y)

#define RTC_PARSE_CHECK_OR_RETURN_LE(X, Y) \
  RTC_PARSE_CHECK_OR_RETURN_OP(<=, X, Y)

#define RTC_PARSE_CHECK_OR_RETURN_GT(X, Y) RTC_PARSE_CHECK_OR_RETURN_OP(>, X, Y)

#define RTC_PARSE_CHECK_OR_RETURN_GE(X, Y) \
  RTC_PARSE_CHECK_OR_RETURN_OP(>=, X, Y)

#define RTC_PARSE_WARN_AND_RETURN_SUCCESS_IF(X, M)      \
  do {                                                  \
    if (X) {                                            \
      RTC_LOG(LS_WARNING) << (M);                       \
      return ParsedRtcEventLog::ParseStatus::Success(); \
    }                                                   \
  } while (0)

#define RTC_RETURN_IF_ERROR(X)                                 \
  do {                                                         \
    const ParsedRtcEventLog::ParseStatus _rtc_parse_status(X); \
    if (!_rtc_parse_status.ok()) {                             \
      return _rtc_parse_status;                                \
    }                                                          \
  } while (0)

using webrtc_event_logging::ToSigned;
using webrtc_event_logging::ToUnsigned;

namespace webrtc {

namespace {
constexpr int64_t kMaxLogSize = 250000000;

constexpr size_t kIpv4Overhead = 20;
constexpr size_t kIpv6Overhead = 40;
constexpr size_t kUdpOverhead = 8;
constexpr size_t kSrtpOverhead = 10;
constexpr size_t kStunOverhead = 4;
constexpr uint16_t kDefaultOverhead =
    kUdpOverhead + kSrtpOverhead + kIpv4Overhead;

constexpr char kIncompleteLogError[] =
    "Could not parse the entire log. Only the beginning will be used.";

struct MediaStreamInfo {
  MediaStreamInfo() = default;
  MediaStreamInfo(LoggedMediaType media_type, bool rtx)
      : media_type(media_type), rtx(rtx) {}
  LoggedMediaType media_type = LoggedMediaType::kUnknown;
  bool rtx = false;
  SeqNumUnwrapper<uint32_t> unwrap_capture_ticks;
};

template <typename Iterable>
void AddRecvStreamInfos(std::map<uint32_t, MediaStreamInfo>* streams,
                        const Iterable configs,
                        LoggedMediaType media_type) {
  for (auto& conf : configs) {
    streams->insert({conf.config.remote_ssrc, {media_type, false}});
    if (conf.config.rtx_ssrc != 0)
      streams->insert({conf.config.rtx_ssrc, {media_type, true}});
  }
}
template <typename Iterable>
void AddSendStreamInfos(std::map<uint32_t, MediaStreamInfo>* streams,
                        const Iterable configs,
                        LoggedMediaType media_type) {
  for (auto& conf : configs) {
    streams->insert({conf.config.local_ssrc, {media_type, false}});
    if (conf.config.rtx_ssrc != 0)
      streams->insert({conf.config.rtx_ssrc, {media_type, true}});
  }
}
struct OverheadChangeEvent {
  Timestamp timestamp;
  uint16_t overhead;
};
std::vector<OverheadChangeEvent> GetOverheadChangingEvents(
    const std::vector<InferredRouteChangeEvent>& route_changes,
    PacketDirection direction) {
  std::vector<OverheadChangeEvent> overheads;
  for (auto& event : route_changes) {
    uint16_t new_overhead = direction == PacketDirection::kIncomingPacket
                                ? event.return_overhead
                                : event.send_overhead;
    if (overheads.empty() || new_overhead != overheads.back().overhead) {
      overheads.push_back({event.log_time, new_overhead});
    }
  }
  return overheads;
}

bool IdenticalRtcpContents(const std::vector<uint8_t>& last_rtcp,
                           absl::string_view new_rtcp) {
  if (last_rtcp.size() != new_rtcp.size())
    return false;
  return memcmp(last_rtcp.data(), new_rtcp.data(), new_rtcp.size()) == 0;
}

// Conversion functions for legacy wire format.
RtcpMode GetRuntimeRtcpMode(rtclog::VideoReceiveConfig::RtcpMode rtcp_mode) {
  switch (rtcp_mode) {
    case rtclog::VideoReceiveConfig::RTCP_COMPOUND:
      return RtcpMode::kCompound;
    case rtclog::VideoReceiveConfig::RTCP_REDUCEDSIZE:
      return RtcpMode::kReducedSize;
  }
  RTC_DCHECK_NOTREACHED();
  return RtcpMode::kOff;
}

BandwidthUsage GetRuntimeDetectorState(
    rtclog::DelayBasedBweUpdate::DetectorState detector_state) {
  switch (detector_state) {
    case rtclog::DelayBasedBweUpdate::BWE_NORMAL:
      return BandwidthUsage::kBwNormal;
    case rtclog::DelayBasedBweUpdate::BWE_UNDERUSING:
      return BandwidthUsage::kBwUnderusing;
    case rtclog::DelayBasedBweUpdate::BWE_OVERUSING:
      return BandwidthUsage::kBwOverusing;
  }
  RTC_DCHECK_NOTREACHED();
  return BandwidthUsage::kBwNormal;
}

IceCandidatePairConfigType GetRuntimeIceCandidatePairConfigType(
    rtclog::IceCandidatePairConfig::IceCandidatePairConfigType type) {
  switch (type) {
    case rtclog::IceCandidatePairConfig::ADDED:
      return IceCandidatePairConfigType::kAdded;
    case rtclog::IceCandidatePairConfig::UPDATED:
      return IceCandidatePairConfigType::kUpdated;
    case rtclog::IceCandidatePairConfig::DESTROYED:
      return IceCandidatePairConfigType::kDestroyed;
    case rtclog::IceCandidatePairConfig::SELECTED:
      return IceCandidatePairConfigType::kSelected;
  }
  RTC_DCHECK_NOTREACHED();
  return IceCandidatePairConfigType::kAdded;
}

IceCandidateType GetRuntimeIceCandidateType(
    rtclog::IceCandidatePairConfig::IceCandidateType type) {
  switch (type) {
    case rtclog::IceCandidatePairConfig::LOCAL:
      return IceCandidateType::kLocal;
    case rtclog::IceCandidatePairConfig::STUN:
      return IceCandidateType::kStun;
    case rtclog::IceCandidatePairConfig::PRFLX:
      return IceCandidateType::kPrflx;
    case rtclog::IceCandidatePairConfig::RELAY:
      return IceCandidateType::kRelay;
    case rtclog::IceCandidatePairConfig::UNKNOWN_CANDIDATE_TYPE:
      return IceCandidateType::kUnknown;
  }
  RTC_DCHECK_NOTREACHED();
  return IceCandidateType::kUnknown;
}

IceCandidatePairProtocol GetRuntimeIceCandidatePairProtocol(
    rtclog::IceCandidatePairConfig::Protocol protocol) {
  switch (protocol) {
    case rtclog::IceCandidatePairConfig::UDP:
      return IceCandidatePairProtocol::kUdp;
    case rtclog::IceCandidatePairConfig::TCP:
      return IceCandidatePairProtocol::kTcp;
    case rtclog::IceCandidatePairConfig::SSLTCP:
      return IceCandidatePairProtocol::kSsltcp;
    case rtclog::IceCandidatePairConfig::TLS:
      return IceCandidatePairProtocol::kTls;
    case rtclog::IceCandidatePairConfig::UNKNOWN_PROTOCOL:
      return IceCandidatePairProtocol::kUnknown;
  }
  RTC_DCHECK_NOTREACHED();
  return IceCandidatePairProtocol::kUnknown;
}

IceCandidatePairAddressFamily GetRuntimeIceCandidatePairAddressFamily(
    rtclog::IceCandidatePairConfig::AddressFamily address_family) {
  switch (address_family) {
    case rtclog::IceCandidatePairConfig::IPV4:
      return IceCandidatePairAddressFamily::kIpv4;
    case rtclog::IceCandidatePairConfig::IPV6:
      return IceCandidatePairAddressFamily::kIpv6;
    case rtclog::IceCandidatePairConfig::UNKNOWN_ADDRESS_FAMILY:
      return IceCandidatePairAddressFamily::kUnknown;
  }
  RTC_DCHECK_NOTREACHED();
  return IceCandidatePairAddressFamily::kUnknown;
}

IceCandidateNetworkType GetRuntimeIceCandidateNetworkType(
    rtclog::IceCandidatePairConfig::NetworkType network_type) {
  switch (network_type) {
    case rtclog::IceCandidatePairConfig::ETHERNET:
      return IceCandidateNetworkType::kEthernet;
    case rtclog::IceCandidatePairConfig::LOOPBACK:
      return IceCandidateNetworkType::kLoopback;
    case rtclog::IceCandidatePairConfig::WIFI:
      return IceCandidateNetworkType::kWifi;
    case rtclog::IceCandidatePairConfig::VPN:
      return IceCandidateNetworkType::kVpn;
    case rtclog::IceCandidatePairConfig::CELLULAR:
      return IceCandidateNetworkType::kCellular;
    case rtclog::IceCandidatePairConfig::UNKNOWN_NETWORK_TYPE:
      return IceCandidateNetworkType::kUnknown;
  }
  RTC_DCHECK_NOTREACHED();
  return IceCandidateNetworkType::kUnknown;
}

IceCandidatePairEventType GetRuntimeIceCandidatePairEventType(
    rtclog::IceCandidatePairEvent::IceCandidatePairEventType type) {
  switch (type) {
    case rtclog::IceCandidatePairEvent::CHECK_SENT:
      return IceCandidatePairEventType::kCheckSent;
    case rtclog::IceCandidatePairEvent::CHECK_RECEIVED:
      return IceCandidatePairEventType::kCheckReceived;
    case rtclog::IceCandidatePairEvent::CHECK_RESPONSE_SENT:
      return IceCandidatePairEventType::kCheckResponseSent;
    case rtclog::IceCandidatePairEvent::CHECK_RESPONSE_RECEIVED:
      return IceCandidatePairEventType::kCheckResponseReceived;
  }
  RTC_DCHECK_NOTREACHED();
  return IceCandidatePairEventType::kCheckSent;
}

VideoCodecType GetRuntimeCodecType(rtclog2::FrameDecodedEvents::Codec codec) {
  switch (codec) {
    case rtclog2::FrameDecodedEvents::CODEC_GENERIC:
      return VideoCodecType::kVideoCodecGeneric;
    case rtclog2::FrameDecodedEvents::CODEC_VP8:
      return VideoCodecType::kVideoCodecVP8;
    case rtclog2::FrameDecodedEvents::CODEC_VP9:
      return VideoCodecType::kVideoCodecVP9;
    case rtclog2::FrameDecodedEvents::CODEC_AV1:
      return VideoCodecType::kVideoCodecAV1;
    case rtclog2::FrameDecodedEvents::CODEC_H264:
      return VideoCodecType::kVideoCodecH264;
    case rtclog2::FrameDecodedEvents::CODEC_H265:
      return VideoCodecType::kVideoCodecH265;
    case rtclog2::FrameDecodedEvents::CODEC_UNKNOWN:
      RTC_LOG(LS_ERROR) << "Unknown codec type. Assuming "
                           "VideoCodecType::kVideoCodecMultiplex";
      return VideoCodecType::kVideoCodecMultiplex;
  }
  RTC_DCHECK_NOTREACHED();
  return VideoCodecType::kVideoCodecMultiplex;
}

ParsedRtcEventLog::ParseStatus GetHeaderExtensions(
    std::vector<RtpExtension>* header_extensions,
    const RepeatedPtrField<rtclog::RtpHeaderExtension>&
        proto_header_extensions) {
  header_extensions->clear();
  for (auto& p : proto_header_extensions) {
    RTC_PARSE_CHECK_OR_RETURN(p.has_name());
    RTC_PARSE_CHECK_OR_RETURN(p.has_id());
    const std::string& name = p.name();
    int id = p.id();
    header_extensions->push_back(RtpExtension(name, id));
  }
  return ParsedRtcEventLog::ParseStatus::Success();
}

template <typename ProtoType, typename LoggedType>
ParsedRtcEventLog::ParseStatus StoreRtpPackets(
    const ProtoType& proto,
    std::map<uint32_t, std::vector<LoggedType>>* rtp_packets_map) {
  RTC_PARSE_CHECK_OR_RETURN(proto.has_timestamp_ms());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_marker());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_payload_type());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_sequence_number());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_rtp_timestamp());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_ssrc());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_payload_size());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_header_size());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_padding_size());

  // Base event
  {
    RTPHeader header;
    header.markerBit = rtc::checked_cast<bool>(proto.marker());
    header.payloadType = rtc::checked_cast<uint8_t>(proto.payload_type());
    header.sequenceNumber =
        rtc::checked_cast<uint16_t>(proto.sequence_number());
    header.timestamp = rtc::checked_cast<uint32_t>(proto.rtp_timestamp());
    header.ssrc = rtc::checked_cast<uint32_t>(proto.ssrc());
    header.numCSRCs = 0;  // TODO(terelius): Implement CSRC.
    header.paddingLength = rtc::checked_cast<size_t>(proto.padding_size());
    header.headerLength = rtc::checked_cast<size_t>(proto.header_size());
    // TODO(terelius): Should we implement payload_type_frequency?
    if (proto.has_transport_sequence_number()) {
      header.extension.hasTransportSequenceNumber = true;
      header.extension.transportSequenceNumber =
          rtc::checked_cast<uint16_t>(proto.transport_sequence_number());
    }
    if (proto.has_transmission_time_offset()) {
      header.extension.hasTransmissionTimeOffset = true;
      header.extension.transmissionTimeOffset =
          rtc::checked_cast<int32_t>(proto.transmission_time_offset());
    }
    if (proto.has_absolute_send_time()) {
      header.extension.hasAbsoluteSendTime = true;
      header.extension.absoluteSendTime =
          rtc::checked_cast<uint32_t>(proto.absolute_send_time());
    }
    if (proto.has_video_rotation()) {
      header.extension.hasVideoRotation = true;
      header.extension.videoRotation = ConvertCVOByteToVideoRotation(
          rtc::checked_cast<uint8_t>(proto.video_rotation()));
    }
    if (proto.has_audio_level()) {
      RTC_PARSE_CHECK_OR_RETURN(proto.has_voice_activity());
      header.extension.hasAudioLevel = true;
      header.extension.voiceActivity =
          rtc::checked_cast<bool>(proto.voice_activity());
      const uint8_t audio_level =
          rtc::checked_cast<uint8_t>(proto.audio_level());
      RTC_PARSE_CHECK_OR_RETURN_LE(audio_level, 0x7Fu);
      header.extension.audioLevel = audio_level;
    } else {
      RTC_PARSE_CHECK_OR_RETURN(!proto.has_voice_activity());
    }
    (*rtp_packets_map)[header.ssrc].emplace_back(
        Timestamp::Millis(proto.timestamp_ms()), header, proto.header_size(),
        proto.payload_size() + header.headerLength + header.paddingLength);
  }

  const size_t number_of_deltas =
      proto.has_number_of_deltas() ? proto.number_of_deltas() : 0u;
  if (number_of_deltas == 0) {
    return ParsedRtcEventLog::ParseStatus::Success();
  }

  // timestamp_ms (event)
  std::vector<absl::optional<uint64_t>> timestamp_ms_values =
      DecodeDeltas(proto.timestamp_ms_deltas(),
                   ToUnsigned(proto.timestamp_ms()), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(timestamp_ms_values.size(), number_of_deltas);

  // marker (RTP base)
  std::vector<absl::optional<uint64_t>> marker_values =
      DecodeDeltas(proto.marker_deltas(), proto.marker(), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(marker_values.size(), number_of_deltas);

  // payload_type (RTP base)
  std::vector<absl::optional<uint64_t>> payload_type_values = DecodeDeltas(
      proto.payload_type_deltas(), proto.payload_type(), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(payload_type_values.size(), number_of_deltas);

  // sequence_number (RTP base)
  std::vector<absl::optional<uint64_t>> sequence_number_values =
      DecodeDeltas(proto.sequence_number_deltas(), proto.sequence_number(),
                   number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(sequence_number_values.size(), number_of_deltas);

  // rtp_timestamp (RTP base)
  std::vector<absl::optional<uint64_t>> rtp_timestamp_values = DecodeDeltas(
      proto.rtp_timestamp_deltas(), proto.rtp_timestamp(), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(rtp_timestamp_values.size(), number_of_deltas);

  // ssrc (RTP base)
  std::vector<absl::optional<uint64_t>> ssrc_values =
      DecodeDeltas(proto.ssrc_deltas(), proto.ssrc(), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(ssrc_values.size(), number_of_deltas);

  // payload_size (RTP base)
  std::vector<absl::optional<uint64_t>> payload_size_values = DecodeDeltas(
      proto.payload_size_deltas(), proto.payload_size(), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(payload_size_values.size(), number_of_deltas);

  // header_size (RTP base)
  std::vector<absl::optional<uint64_t>> header_size_values = DecodeDeltas(
      proto.header_size_deltas(), proto.header_size(), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(header_size_values.size(), number_of_deltas);

  // padding_size (RTP base)
  std::vector<absl::optional<uint64_t>> padding_size_values = DecodeDeltas(
      proto.padding_size_deltas(), proto.padding_size(), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(padding_size_values.size(), number_of_deltas);

  // transport_sequence_number (RTP extension)
  std::vector<absl::optional<uint64_t>> transport_sequence_number_values;
  {
    const absl::optional<uint64_t> base_transport_sequence_number =
        proto.has_transport_sequence_number()
            ? proto.transport_sequence_number()
            : absl::optional<uint64_t>();
    transport_sequence_number_values =
        DecodeDeltas(proto.transport_sequence_number_deltas(),
                     base_transport_sequence_number, number_of_deltas);
    RTC_PARSE_CHECK_OR_RETURN_EQ(transport_sequence_number_values.size(),
                                 number_of_deltas);
  }

  // transmission_time_offset (RTP extension)
  std::vector<absl::optional<uint64_t>> transmission_time_offset_values;
  {
    const absl::optional<uint64_t> unsigned_base_transmission_time_offset =
        proto.has_transmission_time_offset()
            ? ToUnsigned(proto.transmission_time_offset())
            : absl::optional<uint64_t>();
    transmission_time_offset_values =
        DecodeDeltas(proto.transmission_time_offset_deltas(),
                     unsigned_base_transmission_time_offset, number_of_deltas);
    RTC_PARSE_CHECK_OR_RETURN_EQ(transmission_time_offset_values.size(),
                                 number_of_deltas);
  }

  // absolute_send_time (RTP extension)
  std::vector<absl::optional<uint64_t>> absolute_send_time_values;
  {
    const absl::optional<uint64_t> base_absolute_send_time =
        proto.has_absolute_send_time() ? proto.absolute_send_time()
                                       : absl::optional<uint64_t>();
    absolute_send_time_values =
        DecodeDeltas(proto.absolute_send_time_deltas(), base_absolute_send_time,
                     number_of_deltas);
    RTC_PARSE_CHECK_OR_RETURN_EQ(absolute_send_time_values.size(),
                                 number_of_deltas);
  }

  // video_rotation (RTP extension)
  std::vector<absl::optional<uint64_t>> video_rotation_values;
  {
    const absl::optional<uint64_t> base_video_rotation =
        proto.has_video_rotation() ? proto.video_rotation()
                                   : absl::optional<uint64_t>();
    video_rotation_values = DecodeDeltas(proto.video_rotation_deltas(),
                                         base_video_rotation, number_of_deltas);
    RTC_PARSE_CHECK_OR_RETURN_EQ(video_rotation_values.size(),
                                 number_of_deltas);
  }

  // audio_level (RTP extension)
  std::vector<absl::optional<uint64_t>> audio_level_values;
  {
    const absl::optional<uint64_t> base_audio_level =
        proto.has_audio_level() ? proto.audio_level()
                                : absl::optional<uint64_t>();
    audio_level_values = DecodeDeltas(proto.audio_level_deltas(),
                                      base_audio_level, number_of_deltas);
    RTC_PARSE_CHECK_OR_RETURN_EQ(audio_level_values.size(), number_of_deltas);
  }

  // voice_activity (RTP extension)
  std::vector<absl::optional<uint64_t>> voice_activity_values;
  {
    const absl::optional<uint64_t> base_voice_activity =
        proto.has_voice_activity() ? proto.voice_activity()
                                   : absl::optional<uint64_t>();
    voice_activity_values = DecodeDeltas(proto.voice_activity_deltas(),
                                         base_voice_activity, number_of_deltas);
    RTC_PARSE_CHECK_OR_RETURN_EQ(voice_activity_values.size(),
                                 number_of_deltas);
  }

  // Populate events from decoded deltas
  for (size_t i = 0; i < number_of_deltas; ++i) {
    RTC_PARSE_CHECK_OR_RETURN(timestamp_ms_values[i].has_value());
    RTC_PARSE_CHECK_OR_RETURN(marker_values[i].has_value());
    RTC_PARSE_CHECK_OR_RETURN(payload_type_values[i].has_value());
    RTC_PARSE_CHECK_OR_RETURN(sequence_number_values[i].has_value());
    RTC_PARSE_CHECK_OR_RETURN(rtp_timestamp_values[i].has_value());
    RTC_PARSE_CHECK_OR_RETURN(ssrc_values[i].has_value());
    RTC_PARSE_CHECK_OR_RETURN(payload_size_values[i].has_value());
    RTC_PARSE_CHECK_OR_RETURN(header_size_values[i].has_value());
    RTC_PARSE_CHECK_OR_RETURN(padding_size_values[i].has_value());

    int64_t timestamp_ms;
    RTC_PARSE_CHECK_OR_RETURN(
        ToSigned(timestamp_ms_values[i].value(), &timestamp_ms));

    RTPHeader header;
    header.markerBit = rtc::checked_cast<bool>(*marker_values[i]);
    header.payloadType = rtc::checked_cast<uint8_t>(*payload_type_values[i]);
    header.sequenceNumber =
        rtc::checked_cast<uint16_t>(*sequence_number_values[i]);
    header.timestamp = rtc::checked_cast<uint32_t>(*rtp_timestamp_values[i]);
    header.ssrc = rtc::checked_cast<uint32_t>(*ssrc_values[i]);
    header.numCSRCs = 0;  // TODO(terelius): Implement CSRC.
    header.paddingLength = rtc::checked_cast<size_t>(*padding_size_values[i]);
    header.headerLength = rtc::checked_cast<size_t>(*header_size_values[i]);
    // TODO(terelius): Should we implement payload_type_frequency?
    if (transport_sequence_number_values.size() > i &&
        transport_sequence_number_values[i].has_value()) {
      header.extension.hasTransportSequenceNumber = true;
      header.extension.transportSequenceNumber = rtc::checked_cast<uint16_t>(
          transport_sequence_number_values[i].value());
    }
    if (transmission_time_offset_values.size() > i &&
        transmission_time_offset_values[i].has_value()) {
      header.extension.hasTransmissionTimeOffset = true;
      int32_t transmission_time_offset;
      RTC_PARSE_CHECK_OR_RETURN(
          ToSigned(transmission_time_offset_values[i].value(),
                   &transmission_time_offset));
      header.extension.transmissionTimeOffset = transmission_time_offset;
    }
    if (absolute_send_time_values.size() > i &&
        absolute_send_time_values[i].has_value()) {
      header.extension.hasAbsoluteSendTime = true;
      header.extension.absoluteSendTime =
          rtc::checked_cast<uint32_t>(absolute_send_time_values[i].value());
    }
    if (video_rotation_values.size() > i &&
        video_rotation_values[i].has_value()) {
      header.extension.hasVideoRotation = true;
      header.extension.videoRotation = ConvertCVOByteToVideoRotation(
          rtc::checked_cast<uint8_t>(video_rotation_values[i].value()));
    }
    if (audio_level_values.size() > i && audio_level_values[i].has_value()) {
      RTC_PARSE_CHECK_OR_RETURN(voice_activity_values.size() > i &&
                                voice_activity_values[i].has_value());
      header.extension.hasAudioLevel = true;
      header.extension.voiceActivity =
          rtc::checked_cast<bool>(voice_activity_values[i].value());
      const uint8_t audio_level =
          rtc::checked_cast<uint8_t>(audio_level_values[i].value());
      RTC_PARSE_CHECK_OR_RETURN_LE(audio_level, 0x7Fu);
      header.extension.audioLevel = audio_level;
    } else {
      RTC_PARSE_CHECK_OR_RETURN(voice_activity_values.size() <= i ||
                                !voice_activity_values[i].has_value());
    }
    (*rtp_packets_map)[header.ssrc].emplace_back(
        Timestamp::Millis(timestamp_ms), header, header.headerLength,
        payload_size_values[i].value() + header.headerLength +
            header.paddingLength);
  }
  return ParsedRtcEventLog::ParseStatus::Success();
}

template <typename ProtoType, typename LoggedType>
ParsedRtcEventLog::ParseStatus StoreRtcpPackets(
    const ProtoType& proto,
    std::vector<LoggedType>* rtcp_packets,
    bool remove_duplicates) {
  RTC_PARSE_CHECK_OR_RETURN(proto.has_timestamp_ms());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_raw_packet());

  // TODO(terelius): Incoming RTCP may be delivered once for audio and once
  // for video. As a work around, we remove the duplicated packets since they
  // cause problems when analyzing the log or feeding it into the transport
  // feedback adapter.
  if (!remove_duplicates || rtcp_packets->empty() ||
      !IdenticalRtcpContents(rtcp_packets->back().rtcp.raw_data,
                             proto.raw_packet())) {
    // Base event
    rtcp_packets->emplace_back(Timestamp::Millis(proto.timestamp_ms()),
                               proto.raw_packet());
  }

  const size_t number_of_deltas =
      proto.has_number_of_deltas() ? proto.number_of_deltas() : 0u;
  if (number_of_deltas == 0) {
    return ParsedRtcEventLog::ParseStatus::Success();
  }

  // timestamp_ms
  std::vector<absl::optional<uint64_t>> timestamp_ms_values =
      DecodeDeltas(proto.timestamp_ms_deltas(),
                   ToUnsigned(proto.timestamp_ms()), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(timestamp_ms_values.size(), number_of_deltas);

  // raw_packet
  RTC_PARSE_CHECK_OR_RETURN(proto.has_raw_packet_blobs());
  std::vector<absl::string_view> raw_packet_values =
      DecodeBlobs(proto.raw_packet_blobs(), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(raw_packet_values.size(), number_of_deltas);

  // Populate events from decoded deltas
  for (size_t i = 0; i < number_of_deltas; ++i) {
    RTC_PARSE_CHECK_OR_RETURN(timestamp_ms_values[i].has_value());
    int64_t timestamp_ms;
    RTC_PARSE_CHECK_OR_RETURN(
        ToSigned(timestamp_ms_values[i].value(), &timestamp_ms));

    // TODO(terelius): Incoming RTCP may be delivered once for audio and once
    // for video. As a work around, we remove the duplicated packets since they
    // cause problems when analyzing the log or feeding it into the transport
    // feedback adapter.
    if (remove_duplicates && !rtcp_packets->empty() &&
        IdenticalRtcpContents(rtcp_packets->back().rtcp.raw_data,
                              raw_packet_values[i])) {
      continue;
    }
    std::string data(raw_packet_values[i]);
    rtcp_packets->emplace_back(Timestamp::Millis(timestamp_ms), data);
  }
  return ParsedRtcEventLog::ParseStatus::Success();
}

ParsedRtcEventLog::ParseStatus StoreRtcpBlocks(
    int64_t timestamp_us,
    const uint8_t* packet_begin,
    const uint8_t* packet_end,
    std::vector<LoggedRtcpPacketSenderReport>* sr_list,
    std::vector<LoggedRtcpPacketReceiverReport>* rr_list,
    std::vector<LoggedRtcpPacketExtendedReports>* xr_list,
    std::vector<LoggedRtcpPacketRemb>* remb_list,
    std::vector<LoggedRtcpPacketNack>* nack_list,
    std::vector<LoggedRtcpPacketFir>* fir_list,
    std::vector<LoggedRtcpPacketPli>* pli_list,
    std::vector<LoggedRtcpPacketBye>* bye_list,
    std::vector<LoggedRtcpPacketTransportFeedback>* transport_feedback_list,
    std::vector<LoggedRtcpPacketLossNotification>* loss_notification_list) {
  Timestamp timestamp = Timestamp::Micros(timestamp_us);
  rtcp::CommonHeader header;
  for (const uint8_t* block = packet_begin; block < packet_end;
       block = header.NextPacket()) {
    RTC_PARSE_CHECK_OR_RETURN(header.Parse(block, packet_end - block));
    if (header.type() == rtcp::TransportFeedback::kPacketType &&
        header.fmt() == rtcp::TransportFeedback::kFeedbackMessageType) {
      LoggedRtcpPacketTransportFeedback parsed_block;
      parsed_block.timestamp = timestamp;
      RTC_PARSE_CHECK_OR_RETURN(parsed_block.transport_feedback.Parse(header));
      transport_feedback_list->push_back(std::move(parsed_block));
    } else if (header.type() == rtcp::SenderReport::kPacketType) {
      LoggedRtcpPacketSenderReport parsed_block;
      parsed_block.timestamp = timestamp;
      RTC_PARSE_CHECK_OR_RETURN(parsed_block.sr.Parse(header));
      sr_list->push_back(std::move(parsed_block));
    } else if (header.type() == rtcp::ReceiverReport::kPacketType) {
      LoggedRtcpPacketReceiverReport parsed_block;
      parsed_block.timestamp = timestamp;
      RTC_PARSE_CHECK_OR_RETURN(parsed_block.rr.Parse(header));
      rr_list->push_back(std::move(parsed_block));
    } else if (header.type() == rtcp::ExtendedReports::kPacketType) {
      LoggedRtcpPacketExtendedReports parsed_block;
      parsed_block.timestamp = timestamp;
      RTC_PARSE_CHECK_OR_RETURN(parsed_block.xr.Parse(header));
      xr_list->push_back(std::move(parsed_block));
    } else if (header.type() == rtcp::Fir::kPacketType &&
               header.fmt() == rtcp::Fir::kFeedbackMessageType) {
      LoggedRtcpPacketFir parsed_block;
      parsed_block.timestamp = timestamp;
      RTC_PARSE_CHECK_OR_RETURN(parsed_block.fir.Parse(header));
      fir_list->push_back(std::move(parsed_block));
    } else if (header.type() == rtcp::Pli::kPacketType &&
               header.fmt() == rtcp::Pli::kFeedbackMessageType) {
      LoggedRtcpPacketPli parsed_block;
      parsed_block.timestamp = timestamp;
      RTC_PARSE_CHECK_OR_RETURN(parsed_block.pli.Parse(header));
      pli_list->push_back(std::move(parsed_block));
    } else if (header.type() == rtcp::Bye::kPacketType) {
      LoggedRtcpPacketBye parsed_block;
      parsed_block.timestamp = timestamp;
      RTC_PARSE_CHECK_OR_RETURN(parsed_block.bye.Parse(header));
      bye_list->push_back(std::move(parsed_block));
    } else if (header.type() == rtcp::Psfb::kPacketType &&
               header.fmt() == rtcp::Psfb::kAfbMessageType) {
      bool type_found = false;
      if (!type_found) {
        LoggedRtcpPacketRemb parsed_block;
        parsed_block.timestamp = timestamp;
        if (parsed_block.remb.Parse(header)) {
          remb_list->push_back(std::move(parsed_block));
          type_found = true;
        }
      }
      if (!type_found) {
        LoggedRtcpPacketLossNotification parsed_block;
        parsed_block.timestamp = timestamp;
        if (parsed_block.loss_notification.Parse(header)) {
          loss_notification_list->push_back(std::move(parsed_block));
          type_found = true;
        }
      }
      // We ignore other application-layer feedback types.
    } else if (header.type() == rtcp::Nack::kPacketType &&
               header.fmt() == rtcp::Nack::kFeedbackMessageType) {
      LoggedRtcpPacketNack parsed_block;
      parsed_block.timestamp = timestamp;
      RTC_PARSE_CHECK_OR_RETURN(parsed_block.nack.Parse(header));
      nack_list->push_back(std::move(parsed_block));
    }
  }
  return ParsedRtcEventLog::ParseStatus::Success();
}

}  // namespace

// Conversion functions for version 2 of the wire format.
BandwidthUsage GetRuntimeDetectorState(
    rtclog2::DelayBasedBweUpdates::DetectorState detector_state) {
  switch (detector_state) {
    case rtclog2::DelayBasedBweUpdates::BWE_NORMAL:
      return BandwidthUsage::kBwNormal;
    case rtclog2::DelayBasedBweUpdates::BWE_UNDERUSING:
      return BandwidthUsage::kBwUnderusing;
    case rtclog2::DelayBasedBweUpdates::BWE_OVERUSING:
      return BandwidthUsage::kBwOverusing;
    case rtclog2::DelayBasedBweUpdates::BWE_UNKNOWN_STATE:
      break;
  }
  RTC_DCHECK_NOTREACHED();
  return BandwidthUsage::kBwNormal;
}

ProbeFailureReason GetRuntimeProbeFailureReason(
    rtclog2::BweProbeResultFailure::FailureReason failure) {
  switch (failure) {
    case rtclog2::BweProbeResultFailure::INVALID_SEND_RECEIVE_INTERVAL:
      return ProbeFailureReason::kInvalidSendReceiveInterval;
    case rtclog2::BweProbeResultFailure::INVALID_SEND_RECEIVE_RATIO:
      return ProbeFailureReason::kInvalidSendReceiveRatio;
    case rtclog2::BweProbeResultFailure::TIMEOUT:
      return ProbeFailureReason::kTimeout;
    case rtclog2::BweProbeResultFailure::UNKNOWN:
      break;
  }
  RTC_DCHECK_NOTREACHED();
  return ProbeFailureReason::kTimeout;
}

DtlsTransportState GetRuntimeDtlsTransportState(
    rtclog2::DtlsTransportStateEvent::DtlsTransportState state) {
  switch (state) {
    case rtclog2::DtlsTransportStateEvent::DTLS_TRANSPORT_NEW:
      return DtlsTransportState::kNew;
    case rtclog2::DtlsTransportStateEvent::DTLS_TRANSPORT_CONNECTING:
      return DtlsTransportState::kConnecting;
    case rtclog2::DtlsTransportStateEvent::DTLS_TRANSPORT_CONNECTED:
      return DtlsTransportState::kConnected;
    case rtclog2::DtlsTransportStateEvent::DTLS_TRANSPORT_CLOSED:
      return DtlsTransportState::kClosed;
    case rtclog2::DtlsTransportStateEvent::DTLS_TRANSPORT_FAILED:
      return DtlsTransportState::kFailed;
    case rtclog2::DtlsTransportStateEvent::UNKNOWN_DTLS_TRANSPORT_STATE:
      RTC_DCHECK_NOTREACHED();
      return DtlsTransportState::kNumValues;
  }
  RTC_DCHECK_NOTREACHED();
  return DtlsTransportState::kNumValues;
}

IceCandidatePairConfigType GetRuntimeIceCandidatePairConfigType(
    rtclog2::IceCandidatePairConfig::IceCandidatePairConfigType type) {
  switch (type) {
    case rtclog2::IceCandidatePairConfig::ADDED:
      return IceCandidatePairConfigType::kAdded;
    case rtclog2::IceCandidatePairConfig::UPDATED:
      return IceCandidatePairConfigType::kUpdated;
    case rtclog2::IceCandidatePairConfig::DESTROYED:
      return IceCandidatePairConfigType::kDestroyed;
    case rtclog2::IceCandidatePairConfig::SELECTED:
      return IceCandidatePairConfigType::kSelected;
    case rtclog2::IceCandidatePairConfig::UNKNOWN_CONFIG_TYPE:
      break;
  }
  RTC_DCHECK_NOTREACHED();
  return IceCandidatePairConfigType::kAdded;
}

IceCandidateType GetRuntimeIceCandidateType(
    rtclog2::IceCandidatePairConfig::IceCandidateType type) {
  switch (type) {
    case rtclog2::IceCandidatePairConfig::LOCAL:
      return IceCandidateType::kLocal;
    case rtclog2::IceCandidatePairConfig::STUN:
      return IceCandidateType::kStun;
    case rtclog2::IceCandidatePairConfig::PRFLX:
      return IceCandidateType::kPrflx;
    case rtclog2::IceCandidatePairConfig::RELAY:
      return IceCandidateType::kRelay;
    case rtclog2::IceCandidatePairConfig::UNKNOWN_CANDIDATE_TYPE:
      return IceCandidateType::kUnknown;
  }
  RTC_DCHECK_NOTREACHED();
  return IceCandidateType::kUnknown;
}

IceCandidatePairProtocol GetRuntimeIceCandidatePairProtocol(
    rtclog2::IceCandidatePairConfig::Protocol protocol) {
  switch (protocol) {
    case rtclog2::IceCandidatePairConfig::UDP:
      return IceCandidatePairProtocol::kUdp;
    case rtclog2::IceCandidatePairConfig::TCP:
      return IceCandidatePairProtocol::kTcp;
    case rtclog2::IceCandidatePairConfig::SSLTCP:
      return IceCandidatePairProtocol::kSsltcp;
    case rtclog2::IceCandidatePairConfig::TLS:
      return IceCandidatePairProtocol::kTls;
    case rtclog2::IceCandidatePairConfig::UNKNOWN_PROTOCOL:
      return IceCandidatePairProtocol::kUnknown;
  }
  RTC_DCHECK_NOTREACHED();
  return IceCandidatePairProtocol::kUnknown;
}

IceCandidatePairAddressFamily GetRuntimeIceCandidatePairAddressFamily(
    rtclog2::IceCandidatePairConfig::AddressFamily address_family) {
  switch (address_family) {
    case rtclog2::IceCandidatePairConfig::IPV4:
      return IceCandidatePairAddressFamily::kIpv4;
    case rtclog2::IceCandidatePairConfig::IPV6:
      return IceCandidatePairAddressFamily::kIpv6;
    case rtclog2::IceCandidatePairConfig::UNKNOWN_ADDRESS_FAMILY:
      return IceCandidatePairAddressFamily::kUnknown;
  }
  RTC_DCHECK_NOTREACHED();
  return IceCandidatePairAddressFamily::kUnknown;
}

IceCandidateNetworkType GetRuntimeIceCandidateNetworkType(
    rtclog2::IceCandidatePairConfig::NetworkType network_type) {
  switch (network_type) {
    case rtclog2::IceCandidatePairConfig::ETHERNET:
      return IceCandidateNetworkType::kEthernet;
    case rtclog2::IceCandidatePairConfig::LOOPBACK:
      return IceCandidateNetworkType::kLoopback;
    case rtclog2::IceCandidatePairConfig::WIFI:
      return IceCandidateNetworkType::kWifi;
    case rtclog2::IceCandidatePairConfig::VPN:
      return IceCandidateNetworkType::kVpn;
    case rtclog2::IceCandidatePairConfig::CELLULAR:
      return IceCandidateNetworkType::kCellular;
    case rtclog2::IceCandidatePairConfig::UNKNOWN_NETWORK_TYPE:
      return IceCandidateNetworkType::kUnknown;
  }
  RTC_DCHECK_NOTREACHED();
  return IceCandidateNetworkType::kUnknown;
}

IceCandidatePairEventType GetRuntimeIceCandidatePairEventType(
    rtclog2::IceCandidatePairEvent::IceCandidatePairEventType type) {
  switch (type) {
    case rtclog2::IceCandidatePairEvent::CHECK_SENT:
      return IceCandidatePairEventType::kCheckSent;
    case rtclog2::IceCandidatePairEvent::CHECK_RECEIVED:
      return IceCandidatePairEventType::kCheckReceived;
    case rtclog2::IceCandidatePairEvent::CHECK_RESPONSE_SENT:
      return IceCandidatePairEventType::kCheckResponseSent;
    case rtclog2::IceCandidatePairEvent::CHECK_RESPONSE_RECEIVED:
      return IceCandidatePairEventType::kCheckResponseReceived;
    case rtclog2::IceCandidatePairEvent::UNKNOWN_CHECK_TYPE:
      break;
  }
  RTC_DCHECK_NOTREACHED();
  return IceCandidatePairEventType::kCheckSent;
}

std::vector<RtpExtension> GetRuntimeRtpHeaderExtensionConfig(
    const rtclog2::RtpHeaderExtensionConfig& proto_header_extensions) {
  std::vector<RtpExtension> rtp_extensions;
  if (proto_header_extensions.has_transmission_time_offset_id()) {
    rtp_extensions.emplace_back(
        RtpExtension::kTimestampOffsetUri,
        proto_header_extensions.transmission_time_offset_id());
  }
  if (proto_header_extensions.has_absolute_send_time_id()) {
    rtp_extensions.emplace_back(
        RtpExtension::kAbsSendTimeUri,
        proto_header_extensions.absolute_send_time_id());
  }
  if (proto_header_extensions.has_transport_sequence_number_id()) {
    rtp_extensions.emplace_back(
        RtpExtension::kTransportSequenceNumberUri,
        proto_header_extensions.transport_sequence_number_id());
  }
  if (proto_header_extensions.has_audio_level_id()) {
    rtp_extensions.emplace_back(RtpExtension::kAudioLevelUri,
                                proto_header_extensions.audio_level_id());
  }
  if (proto_header_extensions.has_video_rotation_id()) {
    rtp_extensions.emplace_back(RtpExtension::kVideoRotationUri,
                                proto_header_extensions.video_rotation_id());
  }
  return rtp_extensions;
}
// End of conversion functions.

LoggedPacketInfo::LoggedPacketInfo(const LoggedRtpPacket& rtp,
                                   LoggedMediaType media_type,
                                   bool rtx,
                                   Timestamp capture_time)
    : ssrc(rtp.header.ssrc),
      stream_seq_no(rtp.header.sequenceNumber),
      size(static_cast<uint16_t>(rtp.total_length)),
      payload_size(static_cast<uint16_t>(rtp.total_length -
                                         rtp.header.paddingLength -
                                         rtp.header.headerLength)),
      padding_size(static_cast<uint16_t>(rtp.header.paddingLength)),
      payload_type(rtp.header.payloadType),
      media_type(media_type),
      rtx(rtx),
      marker_bit(rtp.header.markerBit),
      has_transport_seq_no(rtp.header.extension.hasTransportSequenceNumber),
      transport_seq_no(static_cast<uint16_t>(
          has_transport_seq_no ? rtp.header.extension.transportSequenceNumber
                               : 0)),
      capture_time(capture_time),
      log_packet_time(Timestamp::Micros(rtp.log_time_us())),
      reported_send_time(rtp.header.extension.hasAbsoluteSendTime
                             ? rtp.header.extension.GetAbsoluteSendTimestamp()
                             : Timestamp::MinusInfinity()) {}

LoggedPacketInfo::LoggedPacketInfo(const LoggedPacketInfo&) = default;

LoggedPacketInfo::~LoggedPacketInfo() {}

ParsedRtcEventLog::~ParsedRtcEventLog() = default;

ParsedRtcEventLog::LoggedRtpStreamIncoming::LoggedRtpStreamIncoming() = default;
ParsedRtcEventLog::LoggedRtpStreamIncoming::LoggedRtpStreamIncoming(
    const LoggedRtpStreamIncoming& rhs) = default;
ParsedRtcEventLog::LoggedRtpStreamIncoming::~LoggedRtpStreamIncoming() =
    default;

ParsedRtcEventLog::LoggedRtpStreamOutgoing::LoggedRtpStreamOutgoing() = default;
ParsedRtcEventLog::LoggedRtpStreamOutgoing::LoggedRtpStreamOutgoing(
    const LoggedRtpStreamOutgoing& rhs) = default;
ParsedRtcEventLog::LoggedRtpStreamOutgoing::~LoggedRtpStreamOutgoing() =
    default;

ParsedRtcEventLog::LoggedRtpStreamView::LoggedRtpStreamView(
    uint32_t ssrc,
    const std::vector<LoggedRtpPacketIncoming>& packets)
    : ssrc(ssrc), packet_view() {
  for (const LoggedRtpPacketIncoming& packet : packets) {
    packet_view.push_back(&(packet.rtp));
  }
}

ParsedRtcEventLog::LoggedRtpStreamView::LoggedRtpStreamView(
    uint32_t ssrc,
    const std::vector<LoggedRtpPacketOutgoing>& packets)
    : ssrc(ssrc), packet_view() {
  for (const LoggedRtpPacketOutgoing& packet : packets) {
    packet_view.push_back(&(packet.rtp));
  }
}

ParsedRtcEventLog::LoggedRtpStreamView::LoggedRtpStreamView(
    const LoggedRtpStreamView&) = default;

// Return default values for header extensions, to use on streams without stored
// mapping data. Currently this only applies to audio streams, since the mapping
// is not stored in the event log.
// TODO(ivoc): Remove this once this mapping is stored in the event log for
//             audio streams. Tracking bug: webrtc:6399
webrtc::RtpHeaderExtensionMap
ParsedRtcEventLog::GetDefaultHeaderExtensionMap() {
  // Values from before the default RTP header extension IDs were removed.
  constexpr int kAudioLevelDefaultId = 1;
  constexpr int kTimestampOffsetDefaultId = 2;
  constexpr int kAbsSendTimeDefaultId = 3;
  constexpr int kVideoRotationDefaultId = 4;
  constexpr int kTransportSequenceNumberDefaultId = 5;
  constexpr int kPlayoutDelayDefaultId = 6;
  constexpr int kVideoContentTypeDefaultId = 7;
  constexpr int kVideoTimingDefaultId = 8;

  webrtc::RtpHeaderExtensionMap default_map;
  default_map.Register<AudioLevel>(kAudioLevelDefaultId);
  default_map.Register<TransmissionOffset>(kTimestampOffsetDefaultId);
  default_map.Register<AbsoluteSendTime>(kAbsSendTimeDefaultId);
  default_map.Register<VideoOrientation>(kVideoRotationDefaultId);
  default_map.Register<TransportSequenceNumber>(
      kTransportSequenceNumberDefaultId);
  default_map.Register<PlayoutDelayLimits>(kPlayoutDelayDefaultId);
  default_map.Register<VideoContentTypeExtension>(kVideoContentTypeDefaultId);
  default_map.Register<VideoTimingExtension>(kVideoTimingDefaultId);
  return default_map;
}

ParsedRtcEventLog::ParsedRtcEventLog(
    UnconfiguredHeaderExtensions parse_unconfigured_header_extensions,
    bool allow_incomplete_logs)
    : parse_unconfigured_header_extensions_(
          parse_unconfigured_header_extensions),
      allow_incomplete_logs_(allow_incomplete_logs) {
  Clear();
}

void ParsedRtcEventLog::Clear() {
  default_extension_map_ = GetDefaultHeaderExtensionMap();

  incoming_rtx_ssrcs_.clear();
  incoming_video_ssrcs_.clear();
  incoming_audio_ssrcs_.clear();
  outgoing_rtx_ssrcs_.clear();
  outgoing_video_ssrcs_.clear();
  outgoing_audio_ssrcs_.clear();

  incoming_rtp_packets_map_.clear();
  outgoing_rtp_packets_map_.clear();
  incoming_rtp_packets_by_ssrc_.clear();
  outgoing_rtp_packets_by_ssrc_.clear();
  incoming_rtp_packet_views_by_ssrc_.clear();
  outgoing_rtp_packet_views_by_ssrc_.clear();

  incoming_rtcp_packets_.clear();
  outgoing_rtcp_packets_.clear();

  incoming_rr_.clear();
  outgoing_rr_.clear();
  incoming_sr_.clear();
  outgoing_sr_.clear();
  incoming_nack_.clear();
  outgoing_nack_.clear();
  incoming_remb_.clear();
  outgoing_remb_.clear();
  incoming_transport_feedback_.clear();
  outgoing_transport_feedback_.clear();
  incoming_loss_notification_.clear();
  outgoing_loss_notification_.clear();

  start_log_events_.clear();
  stop_log_events_.clear();
  audio_playout_events_.clear();
  audio_network_adaptation_events_.clear();
  bwe_probe_cluster_created_events_.clear();
  bwe_probe_failure_events_.clear();
  bwe_probe_success_events_.clear();
  bwe_delay_updates_.clear();
  bwe_loss_updates_.clear();
  dtls_transport_states_.clear();
  dtls_writable_states_.clear();
  decoded_frames_.clear();
  alr_state_events_.clear();
  ice_candidate_pair_configs_.clear();
  ice_candidate_pair_events_.clear();
  audio_recv_configs_.clear();
  audio_send_configs_.clear();
  video_recv_configs_.clear();
  video_send_configs_.clear();

  last_incoming_rtcp_packet_.clear();

  first_timestamp_ = Timestamp::PlusInfinity();
  last_timestamp_ = Timestamp::MinusInfinity();
  first_log_segment_ = LogSegment(0, std::numeric_limits<int64_t>::max());

  incoming_rtp_extensions_maps_.clear();
  outgoing_rtp_extensions_maps_.clear();
}

ParsedRtcEventLog::ParseStatus ParsedRtcEventLog::ParseFile(
    absl::string_view filename) {
  FileWrapper file = FileWrapper::OpenReadOnly(filename);
  if (!file.is_open()) {
    RTC_LOG(LS_WARNING) << "Could not open file " << filename
                        << " for reading.";
    RTC_PARSE_CHECK_OR_RETURN(file.is_open());
  }

  // Compute file size.
  long signed_filesize = file.FileSize();  // NOLINT(runtime/int)
  RTC_PARSE_CHECK_OR_RETURN_GE(signed_filesize, 0);
  RTC_PARSE_CHECK_OR_RETURN_LE(signed_filesize, kMaxLogSize);
  size_t filesize = rtc::checked_cast<size_t>(signed_filesize);

  // Read file into memory.
  std::string buffer(filesize, '\0');
  size_t bytes_read = file.Read(&buffer[0], buffer.size());
  if (bytes_read != filesize) {
    RTC_LOG(LS_WARNING) << "Failed to read file " << filename;
    RTC_PARSE_CHECK_OR_RETURN_EQ(bytes_read, filesize);
  }

  return ParseStream(buffer);
}

ParsedRtcEventLog::ParseStatus ParsedRtcEventLog::ParseString(
    absl::string_view s) {
  return ParseStream(s);
}

ParsedRtcEventLog::ParseStatus ParsedRtcEventLog::ParseStream(
    absl::string_view s) {
  Clear();
  ParseStatus status = ParseStreamInternal(s);

  // Cache the configured SSRCs.
  for (const auto& video_recv_config : video_recv_configs()) {
    incoming_video_ssrcs_.insert(video_recv_config.config.remote_ssrc);
    incoming_video_ssrcs_.insert(video_recv_config.config.rtx_ssrc);
    incoming_rtx_ssrcs_.insert(video_recv_config.config.rtx_ssrc);
  }
  for (const auto& video_send_config : video_send_configs()) {
    outgoing_video_ssrcs_.insert(video_send_config.config.local_ssrc);
    outgoing_video_ssrcs_.insert(video_send_config.config.rtx_ssrc);
    outgoing_rtx_ssrcs_.insert(video_send_config.config.rtx_ssrc);
  }
  for (const auto& audio_recv_config : audio_recv_configs()) {
    incoming_audio_ssrcs_.insert(audio_recv_config.config.remote_ssrc);
  }
  for (const auto& audio_send_config : audio_send_configs()) {
    outgoing_audio_ssrcs_.insert(audio_send_config.config.local_ssrc);
  }

  // ParseStreamInternal stores the RTP packets in a map indexed by SSRC.
  // Since we dont need rapid lookup based on SSRC after parsing, we move the
  // packets_streams from map to vector.
  incoming_rtp_packets_by_ssrc_.reserve(incoming_rtp_packets_map_.size());
  for (auto& kv : incoming_rtp_packets_map_) {
    incoming_rtp_packets_by_ssrc_.emplace_back(LoggedRtpStreamIncoming());
    incoming_rtp_packets_by_ssrc_.back().ssrc = kv.first;
    incoming_rtp_packets_by_ssrc_.back().incoming_packets =
        std::move(kv.second);
  }
  incoming_rtp_packets_map_.clear();
  outgoing_rtp_packets_by_ssrc_.reserve(outgoing_rtp_packets_map_.size());
  for (auto& kv : outgoing_rtp_packets_map_) {
    outgoing_rtp_packets_by_ssrc_.emplace_back(LoggedRtpStreamOutgoing());
    outgoing_rtp_packets_by_ssrc_.back().ssrc = kv.first;
    outgoing_rtp_packets_by_ssrc_.back().outgoing_packets =
        std::move(kv.second);
  }
  outgoing_rtp_packets_map_.clear();

  // Build PacketViews for easier iteration over RTP packets.
  for (const auto& stream : incoming_rtp_packets_by_ssrc_) {
    incoming_rtp_packet_views_by_ssrc_.emplace_back(
        LoggedRtpStreamView(stream.ssrc, stream.incoming_packets));
  }
  for (const auto& stream : outgoing_rtp_packets_by_ssrc_) {
    outgoing_rtp_packet_views_by_ssrc_.emplace_back(
        LoggedRtpStreamView(stream.ssrc, stream.outgoing_packets));
  }

  // Set up convenience wrappers around the most commonly used RTCP types.
  for (const auto& incoming : incoming_rtcp_packets_) {
    const int64_t timestamp_us = incoming.rtcp.timestamp.us();
    const uint8_t* packet_begin = incoming.rtcp.raw_data.data();
    const uint8_t* packet_end = packet_begin + incoming.rtcp.raw_data.size();
    auto store_rtcp_status = StoreRtcpBlocks(
        timestamp_us, packet_begin, packet_end, &incoming_sr_, &incoming_rr_,
        &incoming_xr_, &incoming_remb_, &incoming_nack_, &incoming_fir_,
        &incoming_pli_, &incoming_bye_, &incoming_transport_feedback_,
        &incoming_loss_notification_);
    RTC_RETURN_IF_ERROR(store_rtcp_status);
  }

  for (const auto& outgoing : outgoing_rtcp_packets_) {
    const int64_t timestamp_us = outgoing.rtcp.timestamp.us();
    const uint8_t* packet_begin = outgoing.rtcp.raw_data.data();
    const uint8_t* packet_end = packet_begin + outgoing.rtcp.raw_data.size();
    auto store_rtcp_status = StoreRtcpBlocks(
        timestamp_us, packet_begin, packet_end, &outgoing_sr_, &outgoing_rr_,
        &outgoing_xr_, &outgoing_remb_, &outgoing_nack_, &outgoing_fir_,
        &outgoing_pli_, &outgoing_bye_, &outgoing_transport_feedback_,
        &outgoing_loss_notification_);
    RTC_RETURN_IF_ERROR(store_rtcp_status);
  }

  // Store first and last timestamp events that might happen before the call is
  // connected or after the call is disconnected. Typical examples are
  // stream configurations and starting/stopping the log.
  // TODO(terelius): Figure out if we actually need to find the first and last
  // timestamp in the parser. It seems like this could be done by the caller.
  first_timestamp_ = Timestamp::PlusInfinity();
  last_timestamp_ = Timestamp::MinusInfinity();
  StoreFirstAndLastTimestamp(alr_state_events());
  StoreFirstAndLastTimestamp(route_change_events());
  for (const auto& audio_stream : audio_playout_events()) {
    // Audio playout events are grouped by SSRC.
    StoreFirstAndLastTimestamp(audio_stream.second);
  }
  StoreFirstAndLastTimestamp(audio_network_adaptation_events());
  StoreFirstAndLastTimestamp(bwe_probe_cluster_created_events());
  StoreFirstAndLastTimestamp(bwe_probe_failure_events());
  StoreFirstAndLastTimestamp(bwe_probe_success_events());
  StoreFirstAndLastTimestamp(bwe_delay_updates());
  StoreFirstAndLastTimestamp(bwe_loss_updates());
  for (const auto& frame_stream : decoded_frames()) {
    StoreFirstAndLastTimestamp(frame_stream.second);
  }
  StoreFirstAndLastTimestamp(dtls_transport_states());
  StoreFirstAndLastTimestamp(dtls_writable_states());
  StoreFirstAndLastTimestamp(ice_candidate_pair_configs());
  StoreFirstAndLastTimestamp(ice_candidate_pair_events());
  for (const auto& rtp_stream : incoming_rtp_packets_by_ssrc()) {
    StoreFirstAndLastTimestamp(rtp_stream.incoming_packets);
  }
  for (const auto& rtp_stream : outgoing_rtp_packets_by_ssrc()) {
    StoreFirstAndLastTimestamp(rtp_stream.outgoing_packets);
  }
  StoreFirstAndLastTimestamp(incoming_rtcp_packets());
  StoreFirstAndLastTimestamp(outgoing_rtcp_packets());
  StoreFirstAndLastTimestamp(generic_packets_sent_);
  StoreFirstAndLastTimestamp(generic_packets_received_);
  StoreFirstAndLastTimestamp(generic_acks_received_);
  StoreFirstAndLastTimestamp(remote_estimate_events_);

  // Stop events could be missing due to file size limits. If so, use the
  // last event, or the next start timestamp if available.
  // TODO(terelius): This could be improved. Instead of using the next start
  // event, we could use the timestamp of the the last previous regular event.
  auto start_iter = start_log_events().begin();
  auto stop_iter = stop_log_events().begin();
  int64_t start_us =
      first_timestamp().us_or(std::numeric_limits<int64_t>::max());
  int64_t next_start_us = std::numeric_limits<int64_t>::max();
  int64_t stop_us = std::numeric_limits<int64_t>::max();
  if (start_iter != start_log_events().end()) {
    start_us = std::min(start_us, start_iter->log_time_us());
    ++start_iter;
    if (start_iter != start_log_events().end())
      next_start_us = start_iter->log_time_us();
  }
  if (stop_iter != stop_log_events().end()) {
    stop_us = stop_iter->log_time_us();
  }
  stop_us = std::min(stop_us, next_start_us);
  if (stop_us == std::numeric_limits<int64_t>::max() &&
      !last_timestamp().IsMinusInfinity()) {
    stop_us = last_timestamp().us();
  }
  RTC_PARSE_CHECK_OR_RETURN_LE(start_us, stop_us);
  first_log_segment_ = LogSegment(start_us, stop_us);

  if (first_timestamp_ > last_timestamp_) {
    first_timestamp_ = last_timestamp_ = Timestamp::Zero();
  }

  return status;
}

ParsedRtcEventLog::ParseStatus ParsedRtcEventLog::ParseStreamInternal(
    absl::string_view s) {
  constexpr uint64_t kMaxEventSize = 10000000;  // Sanity check.
  // Protobuf defines the message tag as
  // (field_number << 3) | wire_type. In the legacy encoding, the field number
  // is supposed to be 1 and the wire type for a length-delimited field is 2.
  // In the new encoding we still expect the wire type to be 2, but the field
  // number will be greater than 1.
  constexpr uint64_t kExpectedV1Tag = (1 << 3) | 2;
  bool success = false;

  // "Peek" at the first varint.
  absl::string_view event_start = s;
  uint64_t tag = 0;
  std::tie(success, std::ignore) = DecodeVarInt(s, &tag);
  if (!success) {
    RTC_LOG(LS_WARNING) << "Failed to read varint from beginning of event log.";
    RTC_PARSE_WARN_AND_RETURN_SUCCESS_IF(allow_incomplete_logs_,
                                         kIncompleteLogError);
    return ParseStatus::Error("Failed to read field tag varint", __FILE__,
                              __LINE__);
  }
  s = event_start;

  if (tag >> 1 == static_cast<uint64_t>(RtcEvent::Type::BeginV3Log)) {
    return ParseStreamInternalV3(s);
  }

  while (!s.empty()) {
    // If not, "reset" event_start and read the field tag for the next event.
    event_start = s;
    std::tie(success, s) = DecodeVarInt(s, &tag);
    if (!success) {
      RTC_LOG(LS_WARNING)
          << "Failed to read field tag from beginning of protobuf event.";
      RTC_PARSE_WARN_AND_RETURN_SUCCESS_IF(allow_incomplete_logs_,
                                           kIncompleteLogError);
      return ParseStatus::Error("Failed to read field tag varint", __FILE__,
                                __LINE__);
    }

    constexpr uint64_t kWireTypeMask = 0x07;
    const uint64_t wire_type = tag & kWireTypeMask;
    if (wire_type != 2) {
      RTC_LOG(LS_WARNING) << "Expected field tag with wire type 2 (length "
                             "delimited message). Found wire type "
                          << wire_type;
      RTC_PARSE_WARN_AND_RETURN_SUCCESS_IF(allow_incomplete_logs_,
                                           kIncompleteLogError);
      RTC_PARSE_CHECK_OR_RETURN_EQ(wire_type, 2);
    }

    // Read the length field.
    uint64_t message_length = 0;
    std::tie(success, s) = DecodeVarInt(s, &message_length);
    if (!success) {
      RTC_LOG(LS_WARNING) << "Missing message length after protobuf field tag.";
      RTC_PARSE_WARN_AND_RETURN_SUCCESS_IF(allow_incomplete_logs_,
                                           kIncompleteLogError);
      return ParseStatus::Error("Failed to read message length varint",
                                __FILE__, __LINE__);
    }

    if (message_length > s.size()) {
      RTC_LOG(LS_WARNING) << "Protobuf message length is larger than the "
                             "remaining bytes in the proto.";
      RTC_PARSE_WARN_AND_RETURN_SUCCESS_IF(allow_incomplete_logs_,
                                           kIncompleteLogError);
      return ParseStatus::Error(
          "Incomplete message: the length of the next message is larger than "
          "the remaining bytes in the proto",
          __FILE__, __LINE__);
    }

    RTC_PARSE_CHECK_OR_RETURN_LE(message_length, kMaxEventSize);
    // Skip forward to the start of the next event.
    s = s.substr(message_length);
    size_t total_event_size = event_start.size() - s.size();
    RTC_CHECK_LE(total_event_size, event_start.size());

    if (tag == kExpectedV1Tag) {
      // Parse the protobuf event from the buffer.
      rtclog::EventStream event_stream;
      if (!event_stream.ParseFromArray(event_start.data(), total_event_size)) {
        RTC_LOG(LS_WARNING)
            << "Failed to parse legacy-format protobuf message.";
        RTC_PARSE_WARN_AND_RETURN_SUCCESS_IF(allow_incomplete_logs_,
                                             kIncompleteLogError);
        RTC_PARSE_CHECK_OR_RETURN(false);
      }

      RTC_PARSE_CHECK_OR_RETURN_EQ(event_stream.stream_size(), 1);
      auto status = StoreParsedLegacyEvent(event_stream.stream(0));
      RTC_RETURN_IF_ERROR(status);
    } else {
      // Parse the protobuf event from the buffer.
      rtclog2::EventStream event_stream;
      if (!event_stream.ParseFromArray(event_start.data(), total_event_size)) {
        RTC_LOG(LS_WARNING) << "Failed to parse new-format protobuf message.";
        RTC_PARSE_WARN_AND_RETURN_SUCCESS_IF(allow_incomplete_logs_,
                                             kIncompleteLogError);
        RTC_PARSE_CHECK_OR_RETURN(false);
      }
      auto status = StoreParsedNewFormatEvent(event_stream);
      RTC_RETURN_IF_ERROR(status);
    }
  }
  return ParseStatus::Success();
}

ParsedRtcEventLog::ParseStatus ParsedRtcEventLog::ParseStreamInternalV3(
    absl::string_view s) {
  constexpr uint64_t kMaxEventSize = 10000000;  // Sanity check.
  bool expect_begin_log_event = true;
  bool success = false;

  while (!s.empty()) {
    // Read event type.
    uint64_t event_tag = 0;
    std::tie(success, s) = DecodeVarInt(s, &event_tag);
    RTC_PARSE_CHECK_OR_RETURN_MESSAGE(success, "Failed to read event type.");
    bool batched = event_tag & 1;
    uint64_t event_type = event_tag >> 1;

    // Read event size
    uint64_t event_size_bytes = 0;
    std::tie(success, s) = DecodeVarInt(s, &event_size_bytes);
    RTC_PARSE_CHECK_OR_RETURN_MESSAGE(success, "Failed to read event size.");
    if (event_size_bytes > kMaxEventSize || event_size_bytes > s.size()) {
      RTC_LOG(LS_WARNING) << "Event size is too large.";
      RTC_PARSE_CHECK_OR_RETURN_LE(event_size_bytes, kMaxEventSize);
      RTC_PARSE_CHECK_OR_RETURN_LE(event_size_bytes, s.size());
    }

    // Read remaining event fields into a buffer.
    absl::string_view event_fields = s.substr(0, event_size_bytes);
    s = s.substr(event_size_bytes);

    if (expect_begin_log_event) {
      RTC_PARSE_CHECK_OR_RETURN_EQ(
          event_type, static_cast<uint32_t>(RtcEvent::Type::BeginV3Log));
      expect_begin_log_event = false;
    }

    switch (event_type) {
      case static_cast<uint32_t>(RtcEvent::Type::BeginV3Log):
        RtcEventBeginLog::Parse(event_fields, batched, start_log_events_);
        break;
      case static_cast<uint32_t>(RtcEvent::Type::EndV3Log):
        RtcEventEndLog::Parse(event_fields, batched, stop_log_events_);
        expect_begin_log_event = true;
        break;
      case static_cast<uint32_t>(RtcEvent::Type::AlrStateEvent):
        RtcEventAlrState::Parse(event_fields, batched, alr_state_events_);
        break;
      case static_cast<uint32_t>(RtcEvent::Type::AudioPlayout):
        RtcEventAudioPlayout::Parse(event_fields, batched,
                                    audio_playout_events_);
        break;
      case static_cast<uint32_t>(RtcEvent::Type::BweUpdateDelayBased):
        RtcEventBweUpdateDelayBased::Parse(event_fields, batched,
                                           bwe_delay_updates_);
        break;
      case static_cast<uint32_t>(RtcEvent::Type::AudioNetworkAdaptation):
        RtcEventAudioNetworkAdaptation::Parse(event_fields, batched,
                                              audio_network_adaptation_events_);
        break;
      case static_cast<uint32_t>(RtcEvent::Type::AudioReceiveStreamConfig):
        RtcEventAudioReceiveStreamConfig::Parse(event_fields, batched,
                                                audio_recv_configs_);
        break;
      case static_cast<uint32_t>(RtcEvent::Type::AudioSendStreamConfig):
        RtcEventAudioSendStreamConfig::Parse(event_fields, batched,
                                             audio_send_configs_);
        break;
      case static_cast<uint32_t>(RtcEvent::Type::BweUpdateLossBased):
        RtcEventBweUpdateLossBased::Parse(event_fields, batched,
                                          bwe_loss_updates_);
        break;
      case static_cast<uint32_t>(RtcEvent::Type::DtlsTransportState):
        RtcEventDtlsTransportState::Parse(event_fields, batched,
                                          dtls_transport_states_);
        break;
      case static_cast<uint32_t>(RtcEvent::Type::DtlsWritableState):
        RtcEventDtlsWritableState::Parse(event_fields, batched,
                                         dtls_writable_states_);
        break;
      case static_cast<uint32_t>(RtcEvent::Type::FrameDecoded):
        RtcEventFrameDecoded::Parse(event_fields, batched, decoded_frames_);
        break;
      case static_cast<uint32_t>(RtcEvent::Type::GenericAckReceived):
        RtcEventGenericAckReceived::Parse(event_fields, batched,
                                          generic_acks_received_);
        break;
      case static_cast<uint32_t>(RtcEvent::Type::GenericPacketReceived):
        RtcEventGenericPacketReceived::Parse(event_fields, batched,
                                             generic_packets_received_);
        break;
      case static_cast<uint32_t>(RtcEvent::Type::GenericPacketSent):
        RtcEventGenericPacketSent::Parse(event_fields, batched,
                                         generic_packets_sent_);
        break;
      case static_cast<uint32_t>(RtcEvent::Type::IceCandidatePairConfig):
        RtcEventIceCandidatePairConfig::Parse(event_fields, batched,
                                              ice_candidate_pair_configs_);
        break;
      case static_cast<uint32_t>(RtcEvent::Type::IceCandidatePairEvent):
        RtcEventIceCandidatePair::Parse(event_fields, batched,
                                        ice_candidate_pair_events_);
        break;
      case static_cast<uint32_t>(RtcEvent::Type::ProbeClusterCreated):
        RtcEventProbeClusterCreated::Parse(event_fields, batched,
                                           bwe_probe_cluster_created_events_);
        break;
      case static_cast<uint32_t>(RtcEvent::Type::ProbeResultFailure):
        RtcEventProbeResultFailure::Parse(event_fields, batched,
                                          bwe_probe_failure_events_);
        break;
      case static_cast<uint32_t>(RtcEvent::Type::ProbeResultSuccess):
        RtcEventProbeResultSuccess::Parse(event_fields, batched,
                                          bwe_probe_success_events_);
        break;
      case static_cast<uint32_t>(RtcEvent::Type::RemoteEstimateEvent):
        RtcEventRemoteEstimate::Parse(event_fields, batched,
                                      remote_estimate_events_);
        break;
      case static_cast<uint32_t>(RtcEvent::Type::RouteChangeEvent):
        RtcEventRouteChange::Parse(event_fields, batched, route_change_events_);
        break;
      case static_cast<uint32_t>(RtcEvent::Type::RtcpPacketIncoming):
        RtcEventRtcpPacketIncoming::Parse(event_fields, batched,
                                          incoming_rtcp_packets_);
        break;
      case static_cast<uint32_t>(RtcEvent::Type::RtcpPacketOutgoing):
        RtcEventRtcpPacketOutgoing::Parse(event_fields, batched,
                                          outgoing_rtcp_packets_);
        break;
      case static_cast<uint32_t>(RtcEvent::Type::RtpPacketIncoming):
        RtcEventRtpPacketIncoming::Parse(event_fields, batched,
                                         incoming_rtp_packets_map_);
        break;
      case static_cast<uint32_t>(RtcEvent::Type::RtpPacketOutgoing):
        RtcEventRtpPacketOutgoing::Parse(event_fields, batched,
                                         outgoing_rtp_packets_map_);
        break;
      case static_cast<uint32_t>(RtcEvent::Type::VideoReceiveStreamConfig):
        RtcEventVideoReceiveStreamConfig::Parse(event_fields, batched,
                                                video_recv_configs_);
        break;
      case static_cast<uint32_t>(RtcEvent::Type::VideoSendStreamConfig):
        RtcEventVideoSendStreamConfig::Parse(event_fields, batched,
                                             video_send_configs_);
        break;
    }
  }

  return ParseStatus::Success();
}

template <typename T>
void ParsedRtcEventLog::StoreFirstAndLastTimestamp(const std::vector<T>& v) {
  if (v.empty())
    return;
  first_timestamp_ = std::min(first_timestamp_, v.front().log_time());
  last_timestamp_ = std::max(last_timestamp_, v.back().log_time());
}

ParsedRtcEventLog::ParseStatus ParsedRtcEventLog::StoreParsedLegacyEvent(
    const rtclog::Event& event) {
  RTC_PARSE_CHECK_OR_RETURN(event.has_type());
  switch (event.type()) {
    case rtclog::Event::VIDEO_RECEIVER_CONFIG_EVENT: {
      auto config = GetVideoReceiveConfig(event);
      if (!config.ok())
        return config.status();

      RTC_PARSE_CHECK_OR_RETURN(event.has_timestamp_us());
      int64_t timestamp_us = event.timestamp_us();
      video_recv_configs_.emplace_back(Timestamp::Micros(timestamp_us),
                                       config.value());
      incoming_rtp_extensions_maps_[config.value().remote_ssrc] =
          RtpHeaderExtensionMap(config.value().rtp_extensions);
      incoming_rtp_extensions_maps_[config.value().rtx_ssrc] =
          RtpHeaderExtensionMap(config.value().rtp_extensions);
      break;
    }
    case rtclog::Event::VIDEO_SENDER_CONFIG_EVENT: {
      auto config = GetVideoSendConfig(event);
      if (!config.ok())
        return config.status();

      RTC_PARSE_CHECK_OR_RETURN(event.has_timestamp_us());
      int64_t timestamp_us = event.timestamp_us();
      video_send_configs_.emplace_back(Timestamp::Micros(timestamp_us),
                                       config.value());
      outgoing_rtp_extensions_maps_[config.value().local_ssrc] =
          RtpHeaderExtensionMap(config.value().rtp_extensions);
      outgoing_rtp_extensions_maps_[config.value().rtx_ssrc] =
          RtpHeaderExtensionMap(config.value().rtp_extensions);
      break;
    }
    case rtclog::Event::AUDIO_RECEIVER_CONFIG_EVENT: {
      auto config = GetAudioReceiveConfig(event);
      if (!config.ok())
        return config.status();

      RTC_PARSE_CHECK_OR_RETURN(event.has_timestamp_us());
      int64_t timestamp_us = event.timestamp_us();
      audio_recv_configs_.emplace_back(Timestamp::Micros(timestamp_us),
                                       config.value());
      incoming_rtp_extensions_maps_[config.value().remote_ssrc] =
          RtpHeaderExtensionMap(config.value().rtp_extensions);
      break;
    }
    case rtclog::Event::AUDIO_SENDER_CONFIG_EVENT: {
      auto config = GetAudioSendConfig(event);
      if (!config.ok())
        return config.status();
      RTC_PARSE_CHECK_OR_RETURN(event.has_timestamp_us());
      int64_t timestamp_us = event.timestamp_us();
      audio_send_configs_.emplace_back(Timestamp::Micros(timestamp_us),
                                       config.value());
      outgoing_rtp_extensions_maps_[config.value().local_ssrc] =
          RtpHeaderExtensionMap(config.value().rtp_extensions);
      break;
    }
    case rtclog::Event::RTP_EVENT: {
      RTC_PARSE_CHECK_OR_RETURN(event.has_rtp_packet());
      const rtclog::RtpPacket& rtp_packet = event.rtp_packet();
      RTC_PARSE_CHECK_OR_RETURN(rtp_packet.has_header());
      RTC_PARSE_CHECK_OR_RETURN(rtp_packet.has_incoming());
      RTC_PARSE_CHECK_OR_RETURN(rtp_packet.has_packet_length());
      size_t total_length = rtp_packet.packet_length();

      // Use RtpPacketReceived instead of more generic RtpPacket because former
      // has a buildin convertion to RTPHeader.
      RtpPacketReceived rtp_header;
      RTC_PARSE_CHECK_OR_RETURN(
          rtp_header.Parse(rtc::CopyOnWriteBuffer(rtp_packet.header())));

      if (const RtpHeaderExtensionMap* extension_map = GetRtpHeaderExtensionMap(
              rtp_packet.incoming(), rtp_header.Ssrc())) {
        rtp_header.IdentifyExtensions(*extension_map);
      }

      RTPHeader parsed_header;
      rtp_header.GetHeader(&parsed_header);

      // Since we give the parser only a header, there is no way for it to know
      // the padding length. The best solution would be to log the padding
      // length in RTC event log. In absence of it, we assume the RTP packet to
      // contain only padding, if the padding bit is set.
      // TODO(webrtc:9730): Use a generic way to obtain padding length.
      if (rtp_header.has_padding())
        parsed_header.paddingLength = total_length - rtp_header.size();

      RTC_PARSE_CHECK_OR_RETURN(event.has_timestamp_us());
      int64_t timestamp_us = event.timestamp_us();
      if (rtp_packet.incoming()) {
        incoming_rtp_packets_map_[parsed_header.ssrc].push_back(
            LoggedRtpPacketIncoming(Timestamp::Micros(timestamp_us),
                                    parsed_header, rtp_header.size(),
                                    total_length));
      } else {
        outgoing_rtp_packets_map_[parsed_header.ssrc].push_back(
            LoggedRtpPacketOutgoing(Timestamp::Micros(timestamp_us),
                                    parsed_header, rtp_header.size(),
                                    total_length));
      }
      break;
    }
    case rtclog::Event::RTCP_EVENT: {
      PacketDirection direction;
      std::vector<uint8_t> packet;
      auto status = GetRtcpPacket(event, &direction, &packet);
      RTC_RETURN_IF_ERROR(status);
      RTC_PARSE_CHECK_OR_RETURN(event.has_timestamp_us());
      int64_t timestamp_us = event.timestamp_us();
      if (direction == kIncomingPacket) {
        // Currently incoming RTCP packets are logged twice, both for audio and
        // video. Only act on one of them. Compare against the previous parsed
        // incoming RTCP packet.
        if (packet == last_incoming_rtcp_packet_)
          break;
        incoming_rtcp_packets_.push_back(
            LoggedRtcpPacketIncoming(Timestamp::Micros(timestamp_us), packet));
        last_incoming_rtcp_packet_ = packet;
      } else {
        outgoing_rtcp_packets_.push_back(
            LoggedRtcpPacketOutgoing(Timestamp::Micros(timestamp_us), packet));
      }
      break;
    }
    case rtclog::Event::LOG_START: {
      RTC_PARSE_CHECK_OR_RETURN(event.has_timestamp_us());
      int64_t timestamp_us = event.timestamp_us();
      start_log_events_.push_back(
          LoggedStartEvent(Timestamp::Micros(timestamp_us)));
      break;
    }
    case rtclog::Event::LOG_END: {
      RTC_PARSE_CHECK_OR_RETURN(event.has_timestamp_us());
      int64_t timestamp_us = event.timestamp_us();
      stop_log_events_.push_back(
          LoggedStopEvent(Timestamp::Micros(timestamp_us)));
      break;
    }
    case rtclog::Event::AUDIO_PLAYOUT_EVENT: {
      auto status_or_value = GetAudioPlayout(event);
      RTC_RETURN_IF_ERROR(status_or_value.status());
      LoggedAudioPlayoutEvent playout_event = status_or_value.value();
      audio_playout_events_[playout_event.ssrc].push_back(playout_event);
      break;
    }
    case rtclog::Event::LOSS_BASED_BWE_UPDATE: {
      auto status_or_value = GetLossBasedBweUpdate(event);
      RTC_RETURN_IF_ERROR(status_or_value.status());
      bwe_loss_updates_.push_back(status_or_value.value());
      break;
    }
    case rtclog::Event::DELAY_BASED_BWE_UPDATE: {
      auto status_or_value = GetDelayBasedBweUpdate(event);
      RTC_RETURN_IF_ERROR(status_or_value.status());
      bwe_delay_updates_.push_back(status_or_value.value());
      break;
    }
    case rtclog::Event::AUDIO_NETWORK_ADAPTATION_EVENT: {
      auto status_or_value = GetAudioNetworkAdaptation(event);
      RTC_RETURN_IF_ERROR(status_or_value.status());
      LoggedAudioNetworkAdaptationEvent ana_event = status_or_value.value();
      audio_network_adaptation_events_.push_back(ana_event);
      break;
    }
    case rtclog::Event::BWE_PROBE_CLUSTER_CREATED_EVENT: {
      auto status_or_value = GetBweProbeClusterCreated(event);
      RTC_RETURN_IF_ERROR(status_or_value.status());
      bwe_probe_cluster_created_events_.push_back(status_or_value.value());
      break;
    }
    case rtclog::Event::BWE_PROBE_RESULT_EVENT: {
      // Probe successes and failures are currently stored in the same proto
      // message, we are moving towards separate messages. Probe results
      // therefore need special treatment in the parser.
      RTC_PARSE_CHECK_OR_RETURN(event.has_probe_result());
      RTC_PARSE_CHECK_OR_RETURN(event.probe_result().has_result());
      if (event.probe_result().result() == rtclog::BweProbeResult::SUCCESS) {
        auto status_or_value = GetBweProbeSuccess(event);
        RTC_RETURN_IF_ERROR(status_or_value.status());
        bwe_probe_success_events_.push_back(status_or_value.value());
      } else {
        auto status_or_value = GetBweProbeFailure(event);
        RTC_RETURN_IF_ERROR(status_or_value.status());
        bwe_probe_failure_events_.push_back(status_or_value.value());
      }
      break;
    }
    case rtclog::Event::ALR_STATE_EVENT: {
      auto status_or_value = GetAlrState(event);
      RTC_RETURN_IF_ERROR(status_or_value.status());
      alr_state_events_.push_back(status_or_value.value());
      break;
    }
    case rtclog::Event::ICE_CANDIDATE_PAIR_CONFIG: {
      auto status_or_value = GetIceCandidatePairConfig(event);
      RTC_RETURN_IF_ERROR(status_or_value.status());
      ice_candidate_pair_configs_.push_back(status_or_value.value());
      break;
    }
    case rtclog::Event::ICE_CANDIDATE_PAIR_EVENT: {
      auto status_or_value = GetIceCandidatePairEvent(event);
      RTC_RETURN_IF_ERROR(status_or_value.status());
      ice_candidate_pair_events_.push_back(status_or_value.value());
      break;
    }
    case rtclog::Event::REMOTE_ESTIMATE: {
      auto status_or_value = GetRemoteEstimateEvent(event);
      RTC_RETURN_IF_ERROR(status_or_value.status());
      remote_estimate_events_.push_back(status_or_value.value());
      break;
    }
    case rtclog::Event::UNKNOWN_EVENT: {
      break;
    }
  }
  return ParseStatus::Success();
}

const RtpHeaderExtensionMap* ParsedRtcEventLog::GetRtpHeaderExtensionMap(
    bool incoming,
    uint32_t ssrc) {
  auto& extensions_maps =
      incoming ? incoming_rtp_extensions_maps_ : outgoing_rtp_extensions_maps_;
  auto it = extensions_maps.find(ssrc);
  if (it != extensions_maps.end()) {
    return &(it->second);
  }
  if (parse_unconfigured_header_extensions_ ==
      UnconfiguredHeaderExtensions::kAttemptWebrtcDefaultConfig) {
    RTC_DLOG(LS_WARNING) << "Using default header extension map for SSRC "
                         << ssrc;
    extensions_maps.insert(std::make_pair(ssrc, default_extension_map_));
    return &default_extension_map_;
  }
  RTC_DLOG(LS_WARNING) << "Not parsing header extensions for SSRC " << ssrc
                       << ". No header extension map found.";
  return nullptr;
}

ParsedRtcEventLog::ParseStatus ParsedRtcEventLog::GetRtcpPacket(
    const rtclog::Event& event,
    PacketDirection* incoming,
    std::vector<uint8_t>* packet) const {
  RTC_PARSE_CHECK_OR_RETURN(event.has_type());
  RTC_PARSE_CHECK_OR_RETURN_EQ(event.type(), rtclog::Event::RTCP_EVENT);
  RTC_PARSE_CHECK_OR_RETURN(event.has_rtcp_packet());
  const rtclog::RtcpPacket& rtcp_packet = event.rtcp_packet();
  // Get direction of packet.
  RTC_PARSE_CHECK_OR_RETURN(rtcp_packet.has_incoming());
  if (incoming != nullptr) {
    *incoming = rtcp_packet.incoming() ? kIncomingPacket : kOutgoingPacket;
  }
  // Get packet contents.
  RTC_PARSE_CHECK_OR_RETURN(rtcp_packet.has_packet_data());
  if (packet != nullptr) {
    packet->resize(rtcp_packet.packet_data().size());
    memcpy(packet->data(), rtcp_packet.packet_data().data(),
           rtcp_packet.packet_data().size());
  }
  return ParseStatus::Success();
}

ParsedRtcEventLog::ParseStatusOr<rtclog::StreamConfig>
ParsedRtcEventLog::GetVideoReceiveConfig(const rtclog::Event& event) const {
  rtclog::StreamConfig config;
  RTC_PARSE_CHECK_OR_RETURN(event.has_type());
  RTC_PARSE_CHECK_OR_RETURN_EQ(event.type(),
                               rtclog::Event::VIDEO_RECEIVER_CONFIG_EVENT);
  RTC_PARSE_CHECK_OR_RETURN(event.has_video_receiver_config());
  const rtclog::VideoReceiveConfig& receiver_config =
      event.video_receiver_config();
  // Get SSRCs.
  RTC_PARSE_CHECK_OR_RETURN(receiver_config.has_remote_ssrc());
  config.remote_ssrc = receiver_config.remote_ssrc();
  RTC_PARSE_CHECK_OR_RETURN(receiver_config.has_local_ssrc());
  config.local_ssrc = receiver_config.local_ssrc();
  config.rtx_ssrc = 0;
  // Get RTCP settings.
  RTC_PARSE_CHECK_OR_RETURN(receiver_config.has_rtcp_mode());
  config.rtcp_mode = GetRuntimeRtcpMode(receiver_config.rtcp_mode());
  RTC_PARSE_CHECK_OR_RETURN(receiver_config.has_remb());
  config.remb = receiver_config.remb();

  // Get RTX map.
  std::map<uint32_t, const rtclog::RtxConfig> rtx_map;
  for (int i = 0; i < receiver_config.rtx_map_size(); i++) {
    const rtclog::RtxMap& map = receiver_config.rtx_map(i);
    RTC_PARSE_CHECK_OR_RETURN(map.has_payload_type());
    RTC_PARSE_CHECK_OR_RETURN(map.has_config());
    RTC_PARSE_CHECK_OR_RETURN(map.config().has_rtx_ssrc());
    RTC_PARSE_CHECK_OR_RETURN(map.config().has_rtx_payload_type());
    rtx_map.insert(std::make_pair(map.payload_type(), map.config()));
  }

  // Get header extensions.
  auto status = GetHeaderExtensions(&config.rtp_extensions,
                                    receiver_config.header_extensions());
  RTC_RETURN_IF_ERROR(status);

  // Get decoders.
  config.codecs.clear();
  for (int i = 0; i < receiver_config.decoders_size(); i++) {
    RTC_PARSE_CHECK_OR_RETURN(receiver_config.decoders(i).has_name());
    RTC_PARSE_CHECK_OR_RETURN(receiver_config.decoders(i).has_payload_type());
    int rtx_payload_type = 0;
    auto rtx_it = rtx_map.find(receiver_config.decoders(i).payload_type());
    if (rtx_it != rtx_map.end()) {
      rtx_payload_type = rtx_it->second.rtx_payload_type();
      if (config.rtx_ssrc != 0 &&
          config.rtx_ssrc != rtx_it->second.rtx_ssrc()) {
        RTC_LOG(LS_WARNING)
            << "RtcEventLog protobuf contained different SSRCs for "
               "different received RTX payload types. Will only use "
               "rtx_ssrc = "
            << config.rtx_ssrc << ".";
      } else {
        config.rtx_ssrc = rtx_it->second.rtx_ssrc();
      }
    }
    config.codecs.emplace_back(receiver_config.decoders(i).name(),
                               receiver_config.decoders(i).payload_type(),
                               rtx_payload_type);
  }
  return config;
}

ParsedRtcEventLog::ParseStatusOr<rtclog::StreamConfig>
ParsedRtcEventLog::GetVideoSendConfig(const rtclog::Event& event) const {
  rtclog::StreamConfig config;
  RTC_PARSE_CHECK_OR_RETURN(event.has_type());
  RTC_PARSE_CHECK_OR_RETURN_EQ(event.type(),
                               rtclog::Event::VIDEO_SENDER_CONFIG_EVENT);
  RTC_PARSE_CHECK_OR_RETURN(event.has_video_sender_config());
  const rtclog::VideoSendConfig& sender_config = event.video_sender_config();

  // Get SSRCs.
  // VideoSendStreamConfig no longer stores multiple SSRCs. If you are
  // analyzing a very old log, try building the parser from the same
  // WebRTC version.
  RTC_PARSE_CHECK_OR_RETURN_EQ(sender_config.ssrcs_size(), 1);
  config.local_ssrc = sender_config.ssrcs(0);
  RTC_PARSE_CHECK_OR_RETURN_LE(sender_config.rtx_ssrcs_size(), 1);
  if (sender_config.rtx_ssrcs_size() == 1) {
    config.rtx_ssrc = sender_config.rtx_ssrcs(0);
  }

  // Get header extensions.
  auto status = GetHeaderExtensions(&config.rtp_extensions,
                                    sender_config.header_extensions());
  RTC_RETURN_IF_ERROR(status);

  // Get the codec.
  RTC_PARSE_CHECK_OR_RETURN(sender_config.has_encoder());
  RTC_PARSE_CHECK_OR_RETURN(sender_config.encoder().has_name());
  RTC_PARSE_CHECK_OR_RETURN(sender_config.encoder().has_payload_type());
  config.codecs.emplace_back(
      sender_config.encoder().name(), sender_config.encoder().payload_type(),
      sender_config.has_rtx_payload_type() ? sender_config.rtx_payload_type()
                                           : 0);
  return config;
}

ParsedRtcEventLog::ParseStatusOr<rtclog::StreamConfig>
ParsedRtcEventLog::GetAudioReceiveConfig(const rtclog::Event& event) const {
  rtclog::StreamConfig config;
  RTC_PARSE_CHECK_OR_RETURN(event.has_type());
  RTC_PARSE_CHECK_OR_RETURN_EQ(event.type(),
                               rtclog::Event::AUDIO_RECEIVER_CONFIG_EVENT);
  RTC_PARSE_CHECK_OR_RETURN(event.has_audio_receiver_config());
  const rtclog::AudioReceiveConfig& receiver_config =
      event.audio_receiver_config();
  // Get SSRCs.
  RTC_PARSE_CHECK_OR_RETURN(receiver_config.has_remote_ssrc());
  config.remote_ssrc = receiver_config.remote_ssrc();
  RTC_PARSE_CHECK_OR_RETURN(receiver_config.has_local_ssrc());
  config.local_ssrc = receiver_config.local_ssrc();
  // Get header extensions.
  auto status = GetHeaderExtensions(&config.rtp_extensions,
                                    receiver_config.header_extensions());
  RTC_RETURN_IF_ERROR(status);

  return config;
}

ParsedRtcEventLog::ParseStatusOr<rtclog::StreamConfig>
ParsedRtcEventLog::GetAudioSendConfig(const rtclog::Event& event) const {
  rtclog::StreamConfig config;
  RTC_PARSE_CHECK_OR_RETURN(event.has_type());
  RTC_PARSE_CHECK_OR_RETURN_EQ(event.type(),
                               rtclog::Event::AUDIO_SENDER_CONFIG_EVENT);
  RTC_PARSE_CHECK_OR_RETURN(event.has_audio_sender_config());
  const rtclog::AudioSendConfig& sender_config = event.audio_sender_config();
  // Get SSRCs.
  RTC_PARSE_CHECK_OR_RETURN(sender_config.has_ssrc());
  config.local_ssrc = sender_config.ssrc();
  // Get header extensions.
  auto status = GetHeaderExtensions(&config.rtp_extensions,
                                    sender_config.header_extensions());
  RTC_RETURN_IF_ERROR(status);

  return config;
}

ParsedRtcEventLog::ParseStatusOr<LoggedAudioPlayoutEvent>
ParsedRtcEventLog::GetAudioPlayout(const rtclog::Event& event) const {
  RTC_PARSE_CHECK_OR_RETURN(event.has_type());
  RTC_PARSE_CHECK_OR_RETURN_EQ(event.type(),
                               rtclog::Event::AUDIO_PLAYOUT_EVENT);
  RTC_PARSE_CHECK_OR_RETURN(event.has_audio_playout_event());
  const rtclog::AudioPlayoutEvent& playout_event = event.audio_playout_event();
  LoggedAudioPlayoutEvent res;
  RTC_PARSE_CHECK_OR_RETURN(event.has_timestamp_us());
  res.timestamp = Timestamp::Micros(event.timestamp_us());
  RTC_PARSE_CHECK_OR_RETURN(playout_event.has_local_ssrc());
  res.ssrc = playout_event.local_ssrc();
  return res;
}

ParsedRtcEventLog::ParseStatusOr<LoggedBweLossBasedUpdate>
ParsedRtcEventLog::GetLossBasedBweUpdate(const rtclog::Event& event) const {
  RTC_PARSE_CHECK_OR_RETURN(event.has_type());
  RTC_PARSE_CHECK_OR_RETURN_EQ(event.type(),
                               rtclog::Event::LOSS_BASED_BWE_UPDATE);
  RTC_PARSE_CHECK_OR_RETURN(event.has_loss_based_bwe_update());
  const rtclog::LossBasedBweUpdate& loss_event = event.loss_based_bwe_update();

  LoggedBweLossBasedUpdate bwe_update;
  RTC_CHECK(event.has_timestamp_us());
  bwe_update.timestamp = Timestamp::Micros(event.timestamp_us());
  RTC_PARSE_CHECK_OR_RETURN(loss_event.has_bitrate_bps());
  bwe_update.bitrate_bps = loss_event.bitrate_bps();
  RTC_PARSE_CHECK_OR_RETURN(loss_event.has_fraction_loss());
  bwe_update.fraction_lost = loss_event.fraction_loss();
  RTC_PARSE_CHECK_OR_RETURN(loss_event.has_total_packets());
  bwe_update.expected_packets = loss_event.total_packets();
  return bwe_update;
}

ParsedRtcEventLog::ParseStatusOr<LoggedBweDelayBasedUpdate>
ParsedRtcEventLog::GetDelayBasedBweUpdate(const rtclog::Event& event) const {
  RTC_PARSE_CHECK_OR_RETURN(event.has_type());
  RTC_PARSE_CHECK_OR_RETURN_EQ(event.type(),
                               rtclog::Event::DELAY_BASED_BWE_UPDATE);
  RTC_PARSE_CHECK_OR_RETURN(event.has_delay_based_bwe_update());
  const rtclog::DelayBasedBweUpdate& delay_event =
      event.delay_based_bwe_update();

  LoggedBweDelayBasedUpdate res;
  RTC_PARSE_CHECK_OR_RETURN(event.has_timestamp_us());
  res.timestamp = Timestamp::Micros(event.timestamp_us());
  RTC_PARSE_CHECK_OR_RETURN(delay_event.has_bitrate_bps());
  res.bitrate_bps = delay_event.bitrate_bps();
  RTC_PARSE_CHECK_OR_RETURN(delay_event.has_detector_state());
  res.detector_state = GetRuntimeDetectorState(delay_event.detector_state());
  return res;
}

ParsedRtcEventLog::ParseStatusOr<LoggedAudioNetworkAdaptationEvent>
ParsedRtcEventLog::GetAudioNetworkAdaptation(const rtclog::Event& event) const {
  RTC_PARSE_CHECK_OR_RETURN(event.has_type());
  RTC_PARSE_CHECK_OR_RETURN_EQ(event.type(),
                               rtclog::Event::AUDIO_NETWORK_ADAPTATION_EVENT);
  RTC_PARSE_CHECK_OR_RETURN(event.has_audio_network_adaptation());
  const rtclog::AudioNetworkAdaptation& ana_event =
      event.audio_network_adaptation();

  LoggedAudioNetworkAdaptationEvent res;
  RTC_PARSE_CHECK_OR_RETURN(event.has_timestamp_us());
  res.timestamp = Timestamp::Micros(event.timestamp_us());
  if (ana_event.has_bitrate_bps())
    res.config.bitrate_bps = ana_event.bitrate_bps();
  if (ana_event.has_enable_fec())
    res.config.enable_fec = ana_event.enable_fec();
  if (ana_event.has_enable_dtx())
    res.config.enable_dtx = ana_event.enable_dtx();
  if (ana_event.has_frame_length_ms())
    res.config.frame_length_ms = ana_event.frame_length_ms();
  if (ana_event.has_num_channels())
    res.config.num_channels = ana_event.num_channels();
  if (ana_event.has_uplink_packet_loss_fraction())
    res.config.uplink_packet_loss_fraction =
        ana_event.uplink_packet_loss_fraction();
  return res;
}

ParsedRtcEventLog::ParseStatusOr<LoggedBweProbeClusterCreatedEvent>
ParsedRtcEventLog::GetBweProbeClusterCreated(const rtclog::Event& event) const {
  RTC_PARSE_CHECK_OR_RETURN(event.has_type());
  RTC_PARSE_CHECK_OR_RETURN_EQ(event.type(),
                               rtclog::Event::BWE_PROBE_CLUSTER_CREATED_EVENT);
  RTC_PARSE_CHECK_OR_RETURN(event.has_probe_cluster());
  const rtclog::BweProbeCluster& pcc_event = event.probe_cluster();
  LoggedBweProbeClusterCreatedEvent res;
  RTC_PARSE_CHECK_OR_RETURN(event.has_timestamp_us());
  res.timestamp = Timestamp::Micros(event.timestamp_us());
  RTC_PARSE_CHECK_OR_RETURN(pcc_event.has_id());
  res.id = pcc_event.id();
  RTC_PARSE_CHECK_OR_RETURN(pcc_event.has_bitrate_bps());
  res.bitrate_bps = pcc_event.bitrate_bps();
  RTC_PARSE_CHECK_OR_RETURN(pcc_event.has_min_packets());
  res.min_packets = pcc_event.min_packets();
  RTC_PARSE_CHECK_OR_RETURN(pcc_event.has_min_bytes());
  res.min_bytes = pcc_event.min_bytes();
  return res;
}

ParsedRtcEventLog::ParseStatusOr<LoggedBweProbeFailureEvent>
ParsedRtcEventLog::GetBweProbeFailure(const rtclog::Event& event) const {
  RTC_PARSE_CHECK_OR_RETURN(event.has_type());
  RTC_PARSE_CHECK_OR_RETURN_EQ(event.type(),
                               rtclog::Event::BWE_PROBE_RESULT_EVENT);
  RTC_PARSE_CHECK_OR_RETURN(event.has_probe_result());
  const rtclog::BweProbeResult& pr_event = event.probe_result();
  RTC_PARSE_CHECK_OR_RETURN(pr_event.has_result());
  RTC_PARSE_CHECK_OR_RETURN_NE(pr_event.result(),
                               rtclog::BweProbeResult::SUCCESS);

  LoggedBweProbeFailureEvent res;
  RTC_PARSE_CHECK_OR_RETURN(event.has_timestamp_us());
  res.timestamp = Timestamp::Micros(event.timestamp_us());
  RTC_PARSE_CHECK_OR_RETURN(pr_event.has_id());
  res.id = pr_event.id();
  RTC_PARSE_CHECK_OR_RETURN(pr_event.has_result());
  if (pr_event.result() ==
      rtclog::BweProbeResult::INVALID_SEND_RECEIVE_INTERVAL) {
    res.failure_reason = ProbeFailureReason::kInvalidSendReceiveInterval;
  } else if (pr_event.result() ==
             rtclog::BweProbeResult::INVALID_SEND_RECEIVE_RATIO) {
    res.failure_reason = ProbeFailureReason::kInvalidSendReceiveRatio;
  } else if (pr_event.result() == rtclog::BweProbeResult::TIMEOUT) {
    res.failure_reason = ProbeFailureReason::kTimeout;
  } else {
    RTC_DCHECK_NOTREACHED();
  }
  RTC_PARSE_CHECK_OR_RETURN(!pr_event.has_bitrate_bps());

  return res;
}

ParsedRtcEventLog::ParseStatusOr<LoggedBweProbeSuccessEvent>
ParsedRtcEventLog::GetBweProbeSuccess(const rtclog::Event& event) const {
  RTC_PARSE_CHECK_OR_RETURN(event.has_type());
  RTC_PARSE_CHECK_OR_RETURN_EQ(event.type(),
                               rtclog::Event::BWE_PROBE_RESULT_EVENT);
  RTC_PARSE_CHECK_OR_RETURN(event.has_probe_result());
  const rtclog::BweProbeResult& pr_event = event.probe_result();
  RTC_PARSE_CHECK_OR_RETURN(pr_event.has_result());
  RTC_PARSE_CHECK_OR_RETURN_EQ(pr_event.result(),
                               rtclog::BweProbeResult::SUCCESS);

  LoggedBweProbeSuccessEvent res;
  RTC_PARSE_CHECK_OR_RETURN(event.has_timestamp_us());
  res.timestamp = Timestamp::Micros(event.timestamp_us());
  RTC_PARSE_CHECK_OR_RETURN(pr_event.has_id());
  res.id = pr_event.id();
  RTC_PARSE_CHECK_OR_RETURN(pr_event.has_bitrate_bps());
  res.bitrate_bps = pr_event.bitrate_bps();

  return res;
}

ParsedRtcEventLog::ParseStatusOr<LoggedAlrStateEvent>
ParsedRtcEventLog::GetAlrState(const rtclog::Event& event) const {
  RTC_PARSE_CHECK_OR_RETURN(event.has_type());
  RTC_PARSE_CHECK_OR_RETURN_EQ(event.type(), rtclog::Event::ALR_STATE_EVENT);
  RTC_PARSE_CHECK_OR_RETURN(event.has_alr_state());
  const rtclog::AlrState& alr_event = event.alr_state();
  LoggedAlrStateEvent res;
  RTC_PARSE_CHECK_OR_RETURN(event.has_timestamp_us());
  res.timestamp = Timestamp::Micros(event.timestamp_us());
  RTC_PARSE_CHECK_OR_RETURN(alr_event.has_in_alr());
  res.in_alr = alr_event.in_alr();

  return res;
}

ParsedRtcEventLog::ParseStatusOr<LoggedIceCandidatePairConfig>
ParsedRtcEventLog::GetIceCandidatePairConfig(
    const rtclog::Event& rtc_event) const {
  RTC_PARSE_CHECK_OR_RETURN(rtc_event.has_type());
  RTC_PARSE_CHECK_OR_RETURN_EQ(rtc_event.type(),
                               rtclog::Event::ICE_CANDIDATE_PAIR_CONFIG);
  LoggedIceCandidatePairConfig res;
  const rtclog::IceCandidatePairConfig& config =
      rtc_event.ice_candidate_pair_config();
  RTC_PARSE_CHECK_OR_RETURN(rtc_event.has_timestamp_us());
  res.timestamp = Timestamp::Micros(rtc_event.timestamp_us());
  RTC_PARSE_CHECK_OR_RETURN(config.has_config_type());
  res.type = GetRuntimeIceCandidatePairConfigType(config.config_type());
  RTC_PARSE_CHECK_OR_RETURN(config.has_candidate_pair_id());
  res.candidate_pair_id = config.candidate_pair_id();
  RTC_PARSE_CHECK_OR_RETURN(config.has_local_candidate_type());
  res.local_candidate_type =
      GetRuntimeIceCandidateType(config.local_candidate_type());
  RTC_PARSE_CHECK_OR_RETURN(config.has_local_relay_protocol());
  res.local_relay_protocol =
      GetRuntimeIceCandidatePairProtocol(config.local_relay_protocol());
  RTC_PARSE_CHECK_OR_RETURN(config.has_local_network_type());
  res.local_network_type =
      GetRuntimeIceCandidateNetworkType(config.local_network_type());
  RTC_PARSE_CHECK_OR_RETURN(config.has_local_address_family());
  res.local_address_family =
      GetRuntimeIceCandidatePairAddressFamily(config.local_address_family());
  RTC_PARSE_CHECK_OR_RETURN(config.has_remote_candidate_type());
  res.remote_candidate_type =
      GetRuntimeIceCandidateType(config.remote_candidate_type());
  RTC_PARSE_CHECK_OR_RETURN(config.has_remote_address_family());
  res.remote_address_family =
      GetRuntimeIceCandidatePairAddressFamily(config.remote_address_family());
  RTC_PARSE_CHECK_OR_RETURN(config.has_candidate_pair_protocol());
  res.candidate_pair_protocol =
      GetRuntimeIceCandidatePairProtocol(config.candidate_pair_protocol());
  return res;
}

ParsedRtcEventLog::ParseStatusOr<LoggedIceCandidatePairEvent>
ParsedRtcEventLog::GetIceCandidatePairEvent(
    const rtclog::Event& rtc_event) const {
  RTC_PARSE_CHECK_OR_RETURN(rtc_event.has_type());
  RTC_PARSE_CHECK_OR_RETURN_EQ(rtc_event.type(),
                               rtclog::Event::ICE_CANDIDATE_PAIR_EVENT);
  LoggedIceCandidatePairEvent res;
  const rtclog::IceCandidatePairEvent& event =
      rtc_event.ice_candidate_pair_event();
  RTC_PARSE_CHECK_OR_RETURN(rtc_event.has_timestamp_us());
  res.timestamp = Timestamp::Micros(rtc_event.timestamp_us());
  RTC_PARSE_CHECK_OR_RETURN(event.has_event_type());
  res.type = GetRuntimeIceCandidatePairEventType(event.event_type());
  RTC_PARSE_CHECK_OR_RETURN(event.has_candidate_pair_id());
  res.candidate_pair_id = event.candidate_pair_id();
  // transaction_id is not supported by rtclog::Event
  res.transaction_id = 0;
  return res;
}

ParsedRtcEventLog::ParseStatusOr<LoggedRemoteEstimateEvent>
ParsedRtcEventLog::GetRemoteEstimateEvent(const rtclog::Event& event) const {
  RTC_PARSE_CHECK_OR_RETURN(event.has_type());
  RTC_PARSE_CHECK_OR_RETURN_EQ(event.type(), rtclog::Event::REMOTE_ESTIMATE);
  LoggedRemoteEstimateEvent res;
  const rtclog::RemoteEstimate& remote_estimate_event = event.remote_estimate();
  RTC_PARSE_CHECK_OR_RETURN(event.has_timestamp_us());
  res.timestamp = Timestamp::Micros(event.timestamp_us());
  if (remote_estimate_event.has_link_capacity_lower_kbps())
    res.link_capacity_lower = DataRate::KilobitsPerSec(
        remote_estimate_event.link_capacity_lower_kbps());
  if (remote_estimate_event.has_link_capacity_upper_kbps())
    res.link_capacity_upper = DataRate::KilobitsPerSec(
        remote_estimate_event.link_capacity_upper_kbps());
  return res;
}

// Returns the MediaType for registered SSRCs. Search from the end to use last
// registered types first.
ParsedRtcEventLog::MediaType ParsedRtcEventLog::GetMediaType(
    uint32_t ssrc,
    PacketDirection direction) const {
  if (direction == kIncomingPacket) {
    if (std::find(incoming_video_ssrcs_.begin(), incoming_video_ssrcs_.end(),
                  ssrc) != incoming_video_ssrcs_.end()) {
      return MediaType::VIDEO;
    }
    if (std::find(incoming_audio_ssrcs_.begin(), incoming_audio_ssrcs_.end(),
                  ssrc) != incoming_audio_ssrcs_.end()) {
      return MediaType::AUDIO;
    }
  } else {
    if (std::find(outgoing_video_ssrcs_.begin(), outgoing_video_ssrcs_.end(),
                  ssrc) != outgoing_video_ssrcs_.end()) {
      return MediaType::VIDEO;
    }
    if (std::find(outgoing_audio_ssrcs_.begin(), outgoing_audio_ssrcs_.end(),
                  ssrc) != outgoing_audio_ssrcs_.end()) {
      return MediaType::AUDIO;
    }
  }
  return MediaType::ANY;
}

std::vector<InferredRouteChangeEvent> ParsedRtcEventLog::GetRouteChanges()
    const {
  std::vector<InferredRouteChangeEvent> route_changes;
  for (auto& candidate : ice_candidate_pair_configs()) {
    if (candidate.type == IceCandidatePairConfigType::kSelected) {
      InferredRouteChangeEvent route;
      route.route_id = candidate.candidate_pair_id;
      route.log_time = Timestamp::Millis(candidate.log_time_ms());

      route.send_overhead = kUdpOverhead + kSrtpOverhead + kIpv4Overhead;
      if (candidate.remote_address_family ==
          IceCandidatePairAddressFamily::kIpv6)
        route.send_overhead += kIpv6Overhead - kIpv4Overhead;
      if (candidate.remote_candidate_type != IceCandidateType::kLocal)
        route.send_overhead += kStunOverhead;
      route.return_overhead = kUdpOverhead + kSrtpOverhead + kIpv4Overhead;
      if (candidate.remote_address_family ==
          IceCandidatePairAddressFamily::kIpv6)
        route.return_overhead += kIpv6Overhead - kIpv4Overhead;
      if (candidate.remote_candidate_type != IceCandidateType::kLocal)
        route.return_overhead += kStunOverhead;
      route_changes.push_back(route);
    }
  }
  return route_changes;
}

std::vector<LoggedPacketInfo> ParsedRtcEventLog::GetPacketInfos(
    PacketDirection direction) const {
  std::map<uint32_t, MediaStreamInfo> streams;
  if (direction == PacketDirection::kIncomingPacket) {
    AddRecvStreamInfos(&streams, audio_recv_configs(), LoggedMediaType::kAudio);
    AddRecvStreamInfos(&streams, video_recv_configs(), LoggedMediaType::kVideo);
  } else if (direction == PacketDirection::kOutgoingPacket) {
    AddSendStreamInfos(&streams, audio_send_configs(), LoggedMediaType::kAudio);
    AddSendStreamInfos(&streams, video_send_configs(), LoggedMediaType::kVideo);
  }

  std::vector<OverheadChangeEvent> overheads =
      GetOverheadChangingEvents(GetRouteChanges(), direction);
  auto overhead_iter = overheads.begin();
  std::vector<LoggedPacketInfo> packets;
  std::map<int64_t, size_t> indices;
  uint16_t current_overhead = kDefaultOverhead;
  Timestamp last_log_time = Timestamp::Zero();
  SequenceNumberUnwrapper seq_num_unwrapper;

  auto advance_time = [&](Timestamp new_log_time) {
    if (overhead_iter != overheads.end() &&
        new_log_time >= overhead_iter->timestamp) {
      current_overhead = overhead_iter->overhead;
      ++overhead_iter;
    }
    // If we have a large time delta, it can be caused by a gap in logging,
    // therefore we don't want to match up sequence numbers as we might have had
    // a wraparound.
    if (new_log_time - last_log_time > TimeDelta::Seconds(30)) {
      seq_num_unwrapper = SequenceNumberUnwrapper();
      indices.clear();
    }
    RTC_DCHECK_GE(new_log_time, last_log_time);
    last_log_time = new_log_time;
  };

  auto rtp_handler = [&](const LoggedRtpPacket& rtp) {
    advance_time(rtp.log_time());
    MediaStreamInfo* stream = &streams[rtp.header.ssrc];
    Timestamp capture_time = Timestamp::MinusInfinity();
    if (!stream->rtx) {
      // RTX copy the timestamp of the retransmitted packets. This means that
      // RTX streams don't have a unique clock offset and frequency, so
      // the RTP timstamps can't be unwrapped.

      // Add an offset to avoid `capture_ticks` to become negative in the case
      // of reordering.
      constexpr int64_t kStartingCaptureTimeTicks = 90 * 48 * 10000;
      int64_t capture_ticks =
          kStartingCaptureTimeTicks +
          stream->unwrap_capture_ticks.Unwrap(rtp.header.timestamp);
      // TODO(srte): Use logged sample rate when it is added to the format.
      capture_time = Timestamp::Seconds(
          capture_ticks /
          (stream->media_type == LoggedMediaType::kAudio ? 48000.0 : 90000.0));
    }
    LoggedPacketInfo logged(rtp, stream->media_type, stream->rtx, capture_time);
    logged.overhead = current_overhead;
    if (logged.has_transport_seq_no) {
      logged.log_feedback_time = Timestamp::PlusInfinity();
      int64_t unwrapped_seq_num =
          seq_num_unwrapper.Unwrap(logged.transport_seq_no);
      if (indices.find(unwrapped_seq_num) != indices.end()) {
        auto prev = packets[indices[unwrapped_seq_num]];
        RTC_LOG(LS_WARNING)
            << "Repeated sent packet sequence number: " << unwrapped_seq_num
            << " Packet time:" << prev.log_packet_time.seconds() << "s vs "
            << logged.log_packet_time.seconds()
            << "s at:" << rtp.log_time_ms() / 1000;
      }
      indices[unwrapped_seq_num] = packets.size();
    }
    packets.push_back(logged);
  };

  Timestamp feedback_base_time = Timestamp::MinusInfinity();
  Timestamp last_feedback_base_time = Timestamp::MinusInfinity();

  auto feedback_handler =
      [&](const LoggedRtcpPacketTransportFeedback& logged_rtcp) {
        auto log_feedback_time = logged_rtcp.log_time();
        advance_time(log_feedback_time);
        const auto& feedback = logged_rtcp.transport_feedback;
        // Add timestamp deltas to a local time base selected on first packet
        // arrival. This won't be the true time base, but makes it easier to
        // manually inspect time stamps.
        if (!last_feedback_base_time.IsFinite()) {
          feedback_base_time = log_feedback_time;
        } else {
          feedback_base_time += feedback.GetBaseDelta(last_feedback_base_time);
        }
        last_feedback_base_time = feedback.BaseTime();

        std::vector<LoggedPacketInfo*> packet_feedbacks;
        packet_feedbacks.reserve(feedback.GetAllPackets().size());
        Timestamp receive_timestamp = feedback_base_time;
        std::vector<int64_t> unknown_seq_nums;
        for (const auto& packet : feedback.GetAllPackets()) {
          int64_t unwrapped_seq_num =
              seq_num_unwrapper.Unwrap(packet.sequence_number());
          auto it = indices.find(unwrapped_seq_num);
          if (it == indices.end()) {
            unknown_seq_nums.push_back(unwrapped_seq_num);
            continue;
          }
          LoggedPacketInfo* sent = &packets[it->second];
          if (log_feedback_time - sent->log_packet_time >
              TimeDelta::Seconds(60)) {
            RTC_LOG(LS_WARNING)
                << "Received very late feedback, possibly due to wraparound.";
            continue;
          }
          if (packet.received()) {
            receive_timestamp += packet.delta();
            if (sent->reported_recv_time.IsInfinite()) {
              sent->reported_recv_time = receive_timestamp;
              sent->log_feedback_time = log_feedback_time;
            }
          } else {
            if (sent->reported_recv_time.IsInfinite() &&
                sent->log_feedback_time.IsInfinite()) {
              sent->reported_recv_time = Timestamp::PlusInfinity();
              sent->log_feedback_time = log_feedback_time;
            }
          }
          packet_feedbacks.push_back(sent);
        }
        if (!unknown_seq_nums.empty()) {
          RTC_LOG(LS_WARNING)
              << "Received feedback for unknown packets: "
              << unknown_seq_nums.front() << " - " << unknown_seq_nums.back();
        }
        if (packet_feedbacks.empty())
          return;
        LoggedPacketInfo* last = packet_feedbacks.back();
        last->last_in_feedback = true;
        for (LoggedPacketInfo* fb : packet_feedbacks) {
          if (direction == PacketDirection::kOutgoingPacket) {
            if (last->reported_recv_time.IsFinite() &&
                fb->reported_recv_time.IsFinite()) {
              fb->feedback_hold_duration =
                  last->reported_recv_time - fb->reported_recv_time;
            }
          } else {
            fb->feedback_hold_duration =
                log_feedback_time - fb->log_packet_time;
          }
        }
      };

  RtcEventProcessor process;
  for (const auto& rtp_packets : rtp_packets_by_ssrc(direction)) {
    process.AddEvents(rtp_packets.packet_view, rtp_handler);
  }
  if (direction == PacketDirection::kOutgoingPacket) {
    process.AddEvents(incoming_transport_feedback_, feedback_handler);
  } else {
    process.AddEvents(outgoing_transport_feedback_, feedback_handler);
  }
  process.ProcessEventsInOrder();
  return packets;
}

std::vector<LoggedIceCandidatePairConfig> ParsedRtcEventLog::GetIceCandidates()
    const {
  std::vector<LoggedIceCandidatePairConfig> candidates;
  std::set<uint32_t> added;
  for (auto& candidate : ice_candidate_pair_configs()) {
    if (added.find(candidate.candidate_pair_id) == added.end()) {
      candidates.push_back(candidate);
      added.insert(candidate.candidate_pair_id);
    }
  }
  return candidates;
}

std::vector<LoggedIceEvent> ParsedRtcEventLog::GetIceEvents() const {
  using CheckType = IceCandidatePairEventType;
  using ConfigType = IceCandidatePairConfigType;
  using Combined = LoggedIceEventType;
  std::map<CheckType, Combined> check_map(
      {{CheckType::kCheckSent, Combined::kCheckSent},
       {CheckType::kCheckReceived, Combined::kCheckReceived},
       {CheckType::kCheckResponseSent, Combined::kCheckResponseSent},
       {CheckType::kCheckResponseReceived, Combined::kCheckResponseReceived}});
  std::map<ConfigType, Combined> config_map(
      {{ConfigType::kAdded, Combined::kAdded},
       {ConfigType::kUpdated, Combined::kUpdated},
       {ConfigType::kDestroyed, Combined::kDestroyed},
       {ConfigType::kSelected, Combined::kSelected}});
  std::vector<LoggedIceEvent> log_events;
  auto handle_check = [&](const LoggedIceCandidatePairEvent& check) {
    log_events.push_back(LoggedIceEvent{check.candidate_pair_id,
                                        Timestamp::Millis(check.log_time_ms()),
                                        check_map[check.type]});
  };
  auto handle_config = [&](const LoggedIceCandidatePairConfig& conf) {
    log_events.push_back(LoggedIceEvent{conf.candidate_pair_id,
                                        Timestamp::Millis(conf.log_time_ms()),
                                        config_map[conf.type]});
  };
  RtcEventProcessor process;
  process.AddEvents(ice_candidate_pair_events(), handle_check);
  process.AddEvents(ice_candidate_pair_configs(), handle_config);
  process.ProcessEventsInOrder();
  return log_events;
}

const std::vector<MatchedSendArrivalTimes> GetNetworkTrace(
    const ParsedRtcEventLog& parsed_log) {
  std::vector<MatchedSendArrivalTimes> rtp_rtcp_matched;
  for (auto& packet :
       parsed_log.GetPacketInfos(PacketDirection::kOutgoingPacket)) {
    if (packet.log_feedback_time.IsFinite()) {
      rtp_rtcp_matched.emplace_back(packet.log_feedback_time.ms(),
                                    packet.log_packet_time.ms(),
                                    packet.reported_recv_time.ms_or(
                                        MatchedSendArrivalTimes::kNotReceived),
                                    packet.size);
    }
  }
  return rtp_rtcp_matched;
}

// Helper functions for new format start here
ParsedRtcEventLog::ParseStatus ParsedRtcEventLog::StoreParsedNewFormatEvent(
    const rtclog2::EventStream& stream) {
  RTC_DCHECK_EQ(stream.stream_size(), 0);  // No legacy format event.

  RTC_DCHECK_EQ(
      stream.incoming_rtp_packets_size() + stream.outgoing_rtp_packets_size() +
          stream.incoming_rtcp_packets_size() +
          stream.outgoing_rtcp_packets_size() +
          stream.audio_playout_events_size() + stream.begin_log_events_size() +
          stream.end_log_events_size() + stream.loss_based_bwe_updates_size() +
          stream.delay_based_bwe_updates_size() +
          stream.dtls_transport_state_events_size() +
          stream.dtls_writable_states_size() +
          stream.audio_network_adaptations_size() +
          stream.probe_clusters_size() + stream.probe_success_size() +
          stream.probe_failure_size() + stream.alr_states_size() +
          stream.route_changes_size() + stream.remote_estimates_size() +
          stream.ice_candidate_configs_size() +
          stream.ice_candidate_events_size() +
          stream.audio_recv_stream_configs_size() +
          stream.audio_send_stream_configs_size() +
          stream.video_recv_stream_configs_size() +
          stream.video_send_stream_configs_size() +
          stream.generic_packets_sent_size() +
          stream.generic_packets_received_size() +
          stream.generic_acks_received_size() +
          stream.frame_decoded_events_size(),
      1u);

  if (stream.incoming_rtp_packets_size() == 1) {
    return StoreIncomingRtpPackets(stream.incoming_rtp_packets(0));
  } else if (stream.outgoing_rtp_packets_size() == 1) {
    return StoreOutgoingRtpPackets(stream.outgoing_rtp_packets(0));
  } else if (stream.incoming_rtcp_packets_size() == 1) {
    return StoreIncomingRtcpPackets(stream.incoming_rtcp_packets(0));
  } else if (stream.outgoing_rtcp_packets_size() == 1) {
    return StoreOutgoingRtcpPackets(stream.outgoing_rtcp_packets(0));
  } else if (stream.audio_playout_events_size() == 1) {
    return StoreAudioPlayoutEvent(stream.audio_playout_events(0));
  } else if (stream.begin_log_events_size() == 1) {
    return StoreStartEvent(stream.begin_log_events(0));
  } else if (stream.end_log_events_size() == 1) {
    return StoreStopEvent(stream.end_log_events(0));
  } else if (stream.loss_based_bwe_updates_size() == 1) {
    return StoreBweLossBasedUpdate(stream.loss_based_bwe_updates(0));
  } else if (stream.delay_based_bwe_updates_size() == 1) {
    return StoreBweDelayBasedUpdate(stream.delay_based_bwe_updates(0));
  } else if (stream.dtls_transport_state_events_size() == 1) {
    return StoreDtlsTransportState(stream.dtls_transport_state_events(0));
  } else if (stream.dtls_writable_states_size() == 1) {
    return StoreDtlsWritableState(stream.dtls_writable_states(0));
  } else if (stream.audio_network_adaptations_size() == 1) {
    return StoreAudioNetworkAdaptationEvent(
        stream.audio_network_adaptations(0));
  } else if (stream.probe_clusters_size() == 1) {
    return StoreBweProbeClusterCreated(stream.probe_clusters(0));
  } else if (stream.probe_success_size() == 1) {
    return StoreBweProbeSuccessEvent(stream.probe_success(0));
  } else if (stream.probe_failure_size() == 1) {
    return StoreBweProbeFailureEvent(stream.probe_failure(0));
  } else if (stream.alr_states_size() == 1) {
    return StoreAlrStateEvent(stream.alr_states(0));
  } else if (stream.route_changes_size() == 1) {
    return StoreRouteChangeEvent(stream.route_changes(0));
  } else if (stream.remote_estimates_size() == 1) {
    return StoreRemoteEstimateEvent(stream.remote_estimates(0));
  } else if (stream.ice_candidate_configs_size() == 1) {
    return StoreIceCandidatePairConfig(stream.ice_candidate_configs(0));
  } else if (stream.ice_candidate_events_size() == 1) {
    return StoreIceCandidateEvent(stream.ice_candidate_events(0));
  } else if (stream.audio_recv_stream_configs_size() == 1) {
    return StoreAudioRecvConfig(stream.audio_recv_stream_configs(0));
  } else if (stream.audio_send_stream_configs_size() == 1) {
    return StoreAudioSendConfig(stream.audio_send_stream_configs(0));
  } else if (stream.video_recv_stream_configs_size() == 1) {
    return StoreVideoRecvConfig(stream.video_recv_stream_configs(0));
  } else if (stream.video_send_stream_configs_size() == 1) {
    return StoreVideoSendConfig(stream.video_send_stream_configs(0));
  } else if (stream.generic_packets_received_size() == 1) {
    return StoreGenericPacketReceivedEvent(stream.generic_packets_received(0));
  } else if (stream.generic_packets_sent_size() == 1) {
    return StoreGenericPacketSentEvent(stream.generic_packets_sent(0));
  } else if (stream.generic_acks_received_size() == 1) {
    return StoreGenericAckReceivedEvent(stream.generic_acks_received(0));
  } else if (stream.frame_decoded_events_size() == 1) {
    return StoreFrameDecodedEvents(stream.frame_decoded_events(0));
  } else {
    RTC_DCHECK_NOTREACHED();
    return ParseStatus::Success();
  }
}

ParsedRtcEventLog::ParseStatus ParsedRtcEventLog::StoreAlrStateEvent(
    const rtclog2::AlrState& proto) {
  RTC_PARSE_CHECK_OR_RETURN(proto.has_timestamp_ms());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_in_alr());
  LoggedAlrStateEvent alr_event;
  alr_event.timestamp = Timestamp::Millis(proto.timestamp_ms());
  alr_event.in_alr = proto.in_alr();

  alr_state_events_.push_back(alr_event);
  // TODO(terelius): Should we delta encode this event type?
  return ParseStatus::Success();
}

ParsedRtcEventLog::ParseStatus ParsedRtcEventLog::StoreRouteChangeEvent(
    const rtclog2::RouteChange& proto) {
  RTC_PARSE_CHECK_OR_RETURN(proto.has_timestamp_ms());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_connected());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_overhead());
  LoggedRouteChangeEvent route_event;
  route_event.timestamp = Timestamp::Millis(proto.timestamp_ms());
  route_event.connected = proto.connected();
  route_event.overhead = proto.overhead();

  route_change_events_.push_back(route_event);
  // TODO(terelius): Should we delta encode this event type?
  return ParseStatus::Success();
}

ParsedRtcEventLog::ParseStatus ParsedRtcEventLog::StoreRemoteEstimateEvent(
    const rtclog2::RemoteEstimates& proto) {
  RTC_PARSE_CHECK_OR_RETURN(proto.has_timestamp_ms());
  // Base event
  LoggedRemoteEstimateEvent base_event;
  base_event.timestamp = Timestamp::Millis(proto.timestamp_ms());

  absl::optional<uint64_t> base_link_capacity_lower_kbps;
  if (proto.has_link_capacity_lower_kbps()) {
    base_link_capacity_lower_kbps = proto.link_capacity_lower_kbps();
    base_event.link_capacity_lower =
        DataRate::KilobitsPerSec(proto.link_capacity_lower_kbps());
  }

  absl::optional<uint64_t> base_link_capacity_upper_kbps;
  if (proto.has_link_capacity_upper_kbps()) {
    base_link_capacity_upper_kbps = proto.link_capacity_upper_kbps();
    base_event.link_capacity_upper =
        DataRate::KilobitsPerSec(proto.link_capacity_upper_kbps());
  }

  remote_estimate_events_.push_back(base_event);

  const size_t number_of_deltas =
      proto.has_number_of_deltas() ? proto.number_of_deltas() : 0u;
  if (number_of_deltas == 0) {
    return ParseStatus::Success();
  }

  // timestamp_ms
  auto timestamp_ms_values =
      DecodeDeltas(proto.timestamp_ms_deltas(),
                   ToUnsigned(proto.timestamp_ms()), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(timestamp_ms_values.size(), number_of_deltas);

  // link_capacity_lower_kbps
  auto link_capacity_lower_kbps_values =
      DecodeDeltas(proto.link_capacity_lower_kbps_deltas(),
                   base_link_capacity_lower_kbps, number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(link_capacity_lower_kbps_values.size(),
                               number_of_deltas);

  // link_capacity_upper_kbps
  auto link_capacity_upper_kbps_values =
      DecodeDeltas(proto.link_capacity_upper_kbps_deltas(),
                   base_link_capacity_upper_kbps, number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(link_capacity_upper_kbps_values.size(),
                               number_of_deltas);

  // Populate events from decoded deltas
  for (size_t i = 0; i < number_of_deltas; ++i) {
    LoggedRemoteEstimateEvent event;
    RTC_PARSE_CHECK_OR_RETURN(timestamp_ms_values[i].has_value());
    event.timestamp = Timestamp::Millis(*timestamp_ms_values[i]);
    if (link_capacity_lower_kbps_values[i])
      event.link_capacity_lower =
          DataRate::KilobitsPerSec(*link_capacity_lower_kbps_values[i]);
    if (link_capacity_upper_kbps_values[i])
      event.link_capacity_upper =
          DataRate::KilobitsPerSec(*link_capacity_upper_kbps_values[i]);
    remote_estimate_events_.push_back(event);
  }
  return ParseStatus::Success();
}

ParsedRtcEventLog::ParseStatus ParsedRtcEventLog::StoreAudioPlayoutEvent(
    const rtclog2::AudioPlayoutEvents& proto) {
  RTC_PARSE_CHECK_OR_RETURN(proto.has_timestamp_ms());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_local_ssrc());

  // Base event
  audio_playout_events_[proto.local_ssrc()].emplace_back(
      Timestamp::Millis(proto.timestamp_ms()), proto.local_ssrc());

  const size_t number_of_deltas =
      proto.has_number_of_deltas() ? proto.number_of_deltas() : 0u;
  if (number_of_deltas == 0) {
    return ParseStatus::Success();
  }

  // timestamp_ms
  std::vector<absl::optional<uint64_t>> timestamp_ms_values =
      DecodeDeltas(proto.timestamp_ms_deltas(),
                   ToUnsigned(proto.timestamp_ms()), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(timestamp_ms_values.size(), number_of_deltas);

  // local_ssrc
  std::vector<absl::optional<uint64_t>> local_ssrc_values = DecodeDeltas(
      proto.local_ssrc_deltas(), proto.local_ssrc(), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(local_ssrc_values.size(), number_of_deltas);

  // Populate events from decoded deltas
  for (size_t i = 0; i < number_of_deltas; ++i) {
    RTC_PARSE_CHECK_OR_RETURN(timestamp_ms_values[i].has_value());
    RTC_PARSE_CHECK_OR_RETURN(local_ssrc_values[i].has_value());
    RTC_PARSE_CHECK_OR_RETURN_LE(local_ssrc_values[i].value(),
                                 std::numeric_limits<uint32_t>::max());

    int64_t timestamp_ms;
    RTC_PARSE_CHECK_OR_RETURN(
        ToSigned(timestamp_ms_values[i].value(), &timestamp_ms));

    const uint32_t local_ssrc =
        static_cast<uint32_t>(local_ssrc_values[i].value());
    audio_playout_events_[local_ssrc].emplace_back(
        Timestamp::Millis(timestamp_ms), local_ssrc);
  }
  return ParseStatus::Success();
}

ParsedRtcEventLog::ParseStatus ParsedRtcEventLog::StoreIncomingRtpPackets(
    const rtclog2::IncomingRtpPackets& proto) {
  return StoreRtpPackets(proto, &incoming_rtp_packets_map_);
}

ParsedRtcEventLog::ParseStatus ParsedRtcEventLog::StoreOutgoingRtpPackets(
    const rtclog2::OutgoingRtpPackets& proto) {
  return StoreRtpPackets(proto, &outgoing_rtp_packets_map_);
}

ParsedRtcEventLog::ParseStatus ParsedRtcEventLog::StoreIncomingRtcpPackets(
    const rtclog2::IncomingRtcpPackets& proto) {
  return StoreRtcpPackets(proto, &incoming_rtcp_packets_,
                          /*remove_duplicates=*/true);
}

ParsedRtcEventLog::ParseStatus ParsedRtcEventLog::StoreOutgoingRtcpPackets(
    const rtclog2::OutgoingRtcpPackets& proto) {
  return StoreRtcpPackets(proto, &outgoing_rtcp_packets_,
                          /*remove_duplicates=*/false);
}

ParsedRtcEventLog::ParseStatus ParsedRtcEventLog::StoreStartEvent(
    const rtclog2::BeginLogEvent& proto) {
  RTC_PARSE_CHECK_OR_RETURN(proto.has_timestamp_ms());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_version());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_utc_time_ms());
  RTC_PARSE_CHECK_OR_RETURN_EQ(proto.version(), 2);
  LoggedStartEvent start_event(Timestamp::Millis(proto.timestamp_ms()),
                               Timestamp::Millis(proto.utc_time_ms()));

  start_log_events_.push_back(start_event);
  return ParseStatus::Success();
}

ParsedRtcEventLog::ParseStatus ParsedRtcEventLog::StoreStopEvent(
    const rtclog2::EndLogEvent& proto) {
  RTC_PARSE_CHECK_OR_RETURN(proto.has_timestamp_ms());
  LoggedStopEvent stop_event(Timestamp::Millis(proto.timestamp_ms()));

  stop_log_events_.push_back(stop_event);
  return ParseStatus::Success();
}

ParsedRtcEventLog::ParseStatus ParsedRtcEventLog::StoreBweLossBasedUpdate(
    const rtclog2::LossBasedBweUpdates& proto) {
  RTC_PARSE_CHECK_OR_RETURN(proto.has_timestamp_ms());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_bitrate_bps());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_fraction_loss());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_total_packets());

  // Base event
  bwe_loss_updates_.emplace_back(Timestamp::Millis(proto.timestamp_ms()),
                                 proto.bitrate_bps(), proto.fraction_loss(),
                                 proto.total_packets());

  const size_t number_of_deltas =
      proto.has_number_of_deltas() ? proto.number_of_deltas() : 0u;
  if (number_of_deltas == 0) {
    return ParseStatus::Success();
  }

  // timestamp_ms
  std::vector<absl::optional<uint64_t>> timestamp_ms_values =
      DecodeDeltas(proto.timestamp_ms_deltas(),
                   ToUnsigned(proto.timestamp_ms()), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(timestamp_ms_values.size(), number_of_deltas);

  // bitrate_bps
  std::vector<absl::optional<uint64_t>> bitrate_bps_values = DecodeDeltas(
      proto.bitrate_bps_deltas(), proto.bitrate_bps(), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(bitrate_bps_values.size(), number_of_deltas);

  // fraction_loss
  std::vector<absl::optional<uint64_t>> fraction_loss_values = DecodeDeltas(
      proto.fraction_loss_deltas(), proto.fraction_loss(), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(fraction_loss_values.size(), number_of_deltas);

  // total_packets
  std::vector<absl::optional<uint64_t>> total_packets_values = DecodeDeltas(
      proto.total_packets_deltas(), proto.total_packets(), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(total_packets_values.size(), number_of_deltas);

  // Populate events from decoded deltas
  for (size_t i = 0; i < number_of_deltas; ++i) {
    RTC_PARSE_CHECK_OR_RETURN(timestamp_ms_values[i].has_value());
    int64_t timestamp_ms;
    RTC_PARSE_CHECK_OR_RETURN(
        ToSigned(timestamp_ms_values[i].value(), &timestamp_ms));

    RTC_PARSE_CHECK_OR_RETURN(bitrate_bps_values[i].has_value());
    RTC_PARSE_CHECK_OR_RETURN_LE(bitrate_bps_values[i].value(),
                                 std::numeric_limits<uint32_t>::max());
    const uint32_t bitrate_bps =
        static_cast<uint32_t>(bitrate_bps_values[i].value());

    RTC_PARSE_CHECK_OR_RETURN(fraction_loss_values[i].has_value());
    RTC_PARSE_CHECK_OR_RETURN_LE(fraction_loss_values[i].value(),
                                 std::numeric_limits<uint32_t>::max());
    const uint32_t fraction_loss =
        static_cast<uint32_t>(fraction_loss_values[i].value());

    RTC_PARSE_CHECK_OR_RETURN(total_packets_values[i].has_value());
    RTC_PARSE_CHECK_OR_RETURN_LE(total_packets_values[i].value(),
                                 std::numeric_limits<uint32_t>::max());
    const uint32_t total_packets =
        static_cast<uint32_t>(total_packets_values[i].value());

    bwe_loss_updates_.emplace_back(Timestamp::Millis(timestamp_ms), bitrate_bps,
                                   fraction_loss, total_packets);
  }
  return ParseStatus::Success();
}

ParsedRtcEventLog::ParseStatus ParsedRtcEventLog::StoreBweDelayBasedUpdate(
    const rtclog2::DelayBasedBweUpdates& proto) {
  RTC_PARSE_CHECK_OR_RETURN(proto.has_timestamp_ms());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_bitrate_bps());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_detector_state());

  // Base event
  const BandwidthUsage base_detector_state =
      GetRuntimeDetectorState(proto.detector_state());
  bwe_delay_updates_.emplace_back(Timestamp::Millis(proto.timestamp_ms()),
                                  proto.bitrate_bps(), base_detector_state);

  const size_t number_of_deltas =
      proto.has_number_of_deltas() ? proto.number_of_deltas() : 0u;
  if (number_of_deltas == 0) {
    return ParseStatus::Success();
  }

  // timestamp_ms
  std::vector<absl::optional<uint64_t>> timestamp_ms_values =
      DecodeDeltas(proto.timestamp_ms_deltas(),
                   ToUnsigned(proto.timestamp_ms()), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(timestamp_ms_values.size(), number_of_deltas);

  // bitrate_bps
  std::vector<absl::optional<uint64_t>> bitrate_bps_values = DecodeDeltas(
      proto.bitrate_bps_deltas(), proto.bitrate_bps(), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(bitrate_bps_values.size(), number_of_deltas);

  // detector_state
  std::vector<absl::optional<uint64_t>> detector_state_values = DecodeDeltas(
      proto.detector_state_deltas(),
      static_cast<uint64_t>(proto.detector_state()), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(detector_state_values.size(), number_of_deltas);

  // Populate events from decoded deltas
  for (size_t i = 0; i < number_of_deltas; ++i) {
    RTC_PARSE_CHECK_OR_RETURN(timestamp_ms_values[i].has_value());
    int64_t timestamp_ms;
    RTC_PARSE_CHECK_OR_RETURN(
        ToSigned(timestamp_ms_values[i].value(), &timestamp_ms));

    RTC_PARSE_CHECK_OR_RETURN(bitrate_bps_values[i].has_value());
    RTC_PARSE_CHECK_OR_RETURN_LE(bitrate_bps_values[i].value(),
                                 std::numeric_limits<uint32_t>::max());
    const uint32_t bitrate_bps =
        static_cast<uint32_t>(bitrate_bps_values[i].value());

    RTC_PARSE_CHECK_OR_RETURN(detector_state_values[i].has_value());
    const auto detector_state =
        static_cast<rtclog2::DelayBasedBweUpdates::DetectorState>(
            detector_state_values[i].value());

    bwe_delay_updates_.emplace_back(Timestamp::Millis(timestamp_ms),
                                    bitrate_bps,
                                    GetRuntimeDetectorState(detector_state));
  }
  return ParseStatus::Success();
}

ParsedRtcEventLog::ParseStatus ParsedRtcEventLog::StoreBweProbeClusterCreated(
    const rtclog2::BweProbeCluster& proto) {
  LoggedBweProbeClusterCreatedEvent probe_cluster;
  RTC_PARSE_CHECK_OR_RETURN(proto.has_timestamp_ms());
  probe_cluster.timestamp = Timestamp::Millis(proto.timestamp_ms());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_id());
  probe_cluster.id = proto.id();
  RTC_PARSE_CHECK_OR_RETURN(proto.has_bitrate_bps());
  probe_cluster.bitrate_bps = proto.bitrate_bps();
  RTC_PARSE_CHECK_OR_RETURN(proto.has_min_packets());
  probe_cluster.min_packets = proto.min_packets();
  RTC_PARSE_CHECK_OR_RETURN(proto.has_min_bytes());
  probe_cluster.min_bytes = proto.min_bytes();

  bwe_probe_cluster_created_events_.push_back(probe_cluster);

  // TODO(terelius): Should we delta encode this event type?
  return ParseStatus::Success();
}

ParsedRtcEventLog::ParseStatus ParsedRtcEventLog::StoreBweProbeSuccessEvent(
    const rtclog2::BweProbeResultSuccess& proto) {
  LoggedBweProbeSuccessEvent probe_result;
  RTC_PARSE_CHECK_OR_RETURN(proto.has_timestamp_ms());
  probe_result.timestamp = Timestamp::Millis(proto.timestamp_ms());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_id());
  probe_result.id = proto.id();
  RTC_PARSE_CHECK_OR_RETURN(proto.has_bitrate_bps());
  probe_result.bitrate_bps = proto.bitrate_bps();

  bwe_probe_success_events_.push_back(probe_result);

  // TODO(terelius): Should we delta encode this event type?
  return ParseStatus::Success();
}

ParsedRtcEventLog::ParseStatus ParsedRtcEventLog::StoreBweProbeFailureEvent(
    const rtclog2::BweProbeResultFailure& proto) {
  LoggedBweProbeFailureEvent probe_result;
  RTC_PARSE_CHECK_OR_RETURN(proto.has_timestamp_ms());
  probe_result.timestamp = Timestamp::Millis(proto.timestamp_ms());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_id());
  probe_result.id = proto.id();
  RTC_PARSE_CHECK_OR_RETURN(proto.has_failure());
  probe_result.failure_reason = GetRuntimeProbeFailureReason(proto.failure());

  bwe_probe_failure_events_.push_back(probe_result);

  // TODO(terelius): Should we delta encode this event type?
  return ParseStatus::Success();
}

ParsedRtcEventLog::ParseStatus ParsedRtcEventLog::StoreFrameDecodedEvents(
    const rtclog2::FrameDecodedEvents& proto) {
  RTC_PARSE_CHECK_OR_RETURN(proto.has_timestamp_ms());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_ssrc());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_render_time_ms());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_width());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_height());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_codec());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_qp());

  LoggedFrameDecoded base_frame;
  base_frame.timestamp = Timestamp::Millis(proto.timestamp_ms());
  base_frame.ssrc = proto.ssrc();
  base_frame.render_time_ms = proto.render_time_ms();
  base_frame.width = proto.width();
  base_frame.height = proto.height();
  base_frame.codec = GetRuntimeCodecType(proto.codec());
  RTC_PARSE_CHECK_OR_RETURN_GE(proto.qp(), 0);
  RTC_PARSE_CHECK_OR_RETURN_LE(proto.qp(), 255);
  base_frame.qp = static_cast<uint8_t>(proto.qp());

  decoded_frames_[base_frame.ssrc].push_back(base_frame);

  const size_t number_of_deltas =
      proto.has_number_of_deltas() ? proto.number_of_deltas() : 0u;
  if (number_of_deltas == 0) {
    return ParseStatus::Success();
  }

  // timestamp_ms
  std::vector<absl::optional<uint64_t>> timestamp_ms_values =
      DecodeDeltas(proto.timestamp_ms_deltas(),
                   ToUnsigned(proto.timestamp_ms()), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(timestamp_ms_values.size(), number_of_deltas);

  // SSRC
  std::vector<absl::optional<uint64_t>> ssrc_values =
      DecodeDeltas(proto.ssrc_deltas(), proto.ssrc(), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(ssrc_values.size(), number_of_deltas);

  // render_time_ms
  std::vector<absl::optional<uint64_t>> render_time_ms_values =
      DecodeDeltas(proto.render_time_ms_deltas(),
                   ToUnsigned(proto.render_time_ms()), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(render_time_ms_values.size(), number_of_deltas);

  // width
  std::vector<absl::optional<uint64_t>> width_values = DecodeDeltas(
      proto.width_deltas(), ToUnsigned(proto.width()), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(width_values.size(), number_of_deltas);

  // height
  std::vector<absl::optional<uint64_t>> height_values = DecodeDeltas(
      proto.height_deltas(), ToUnsigned(proto.height()), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(height_values.size(), number_of_deltas);

  // codec
  std::vector<absl::optional<uint64_t>> codec_values =
      DecodeDeltas(proto.codec_deltas(), static_cast<uint64_t>(proto.codec()),
                   number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(codec_values.size(), number_of_deltas);

  // qp
  std::vector<absl::optional<uint64_t>> qp_values =
      DecodeDeltas(proto.qp_deltas(), proto.qp(), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(qp_values.size(), number_of_deltas);

  // Populate events from decoded deltas
  for (size_t i = 0; i < number_of_deltas; ++i) {
    LoggedFrameDecoded frame;
    int64_t timestamp_ms;
    RTC_PARSE_CHECK_OR_RETURN(timestamp_ms_values[i].has_value());
    RTC_PARSE_CHECK_OR_RETURN(
        ToSigned(timestamp_ms_values[i].value(), &timestamp_ms));
    frame.timestamp = Timestamp::Millis(timestamp_ms);

    RTC_PARSE_CHECK_OR_RETURN(ssrc_values[i].has_value());
    RTC_PARSE_CHECK_OR_RETURN_LE(ssrc_values[i].value(),
                                 std::numeric_limits<uint32_t>::max());
    frame.ssrc = static_cast<uint32_t>(ssrc_values[i].value());

    RTC_PARSE_CHECK_OR_RETURN(render_time_ms_values[i].has_value());
    RTC_PARSE_CHECK_OR_RETURN(
        ToSigned(render_time_ms_values[i].value(), &frame.render_time_ms));

    RTC_PARSE_CHECK_OR_RETURN(width_values[i].has_value());
    RTC_PARSE_CHECK_OR_RETURN(ToSigned(width_values[i].value(), &frame.width));

    RTC_PARSE_CHECK_OR_RETURN(height_values[i].has_value());
    RTC_PARSE_CHECK_OR_RETURN(
        ToSigned(height_values[i].value(), &frame.height));

    RTC_PARSE_CHECK_OR_RETURN(codec_values[i].has_value());
    frame.codec =
        GetRuntimeCodecType(static_cast<rtclog2::FrameDecodedEvents::Codec>(
            codec_values[i].value()));

    RTC_PARSE_CHECK_OR_RETURN(qp_values[i].has_value());
    RTC_PARSE_CHECK_OR_RETURN_LE(qp_values[i].value(),
                                 std::numeric_limits<uint8_t>::max());
    frame.qp = static_cast<uint8_t>(qp_values[i].value());

    decoded_frames_[frame.ssrc].push_back(frame);
  }
  return ParseStatus::Success();
}

ParsedRtcEventLog::ParseStatus ParsedRtcEventLog::StoreGenericAckReceivedEvent(
    const rtclog2::GenericAckReceived& proto) {
  RTC_PARSE_CHECK_OR_RETURN(proto.has_timestamp_ms());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_packet_number());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_acked_packet_number());
  // receive_acked_packet_time_ms is optional.

  absl::optional<int64_t> base_receive_acked_packet_time_ms;
  if (proto.has_receive_acked_packet_time_ms()) {
    base_receive_acked_packet_time_ms = proto.receive_acked_packet_time_ms();
  }
  generic_acks_received_.push_back(
      {Timestamp::Millis(proto.timestamp_ms()), proto.packet_number(),
       proto.acked_packet_number(), base_receive_acked_packet_time_ms});

  const size_t number_of_deltas =
      proto.has_number_of_deltas() ? proto.number_of_deltas() : 0u;
  if (number_of_deltas == 0) {
    return ParseStatus::Success();
  }

  // timestamp_ms
  std::vector<absl::optional<uint64_t>> timestamp_ms_values =
      DecodeDeltas(proto.timestamp_ms_deltas(),
                   ToUnsigned(proto.timestamp_ms()), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(timestamp_ms_values.size(), number_of_deltas);

  // packet_number
  std::vector<absl::optional<uint64_t>> packet_number_values =
      DecodeDeltas(proto.packet_number_deltas(),
                   ToUnsigned(proto.packet_number()), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(packet_number_values.size(), number_of_deltas);

  // acked_packet_number
  std::vector<absl::optional<uint64_t>> acked_packet_number_values =
      DecodeDeltas(proto.acked_packet_number_deltas(),
                   ToUnsigned(proto.acked_packet_number()), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(acked_packet_number_values.size(),
                               number_of_deltas);

  // optional receive_acked_packet_time_ms
  const absl::optional<uint64_t> unsigned_receive_acked_packet_time_ms_base =
      proto.has_receive_acked_packet_time_ms()
          ? absl::optional<uint64_t>(
                ToUnsigned(proto.receive_acked_packet_time_ms()))
          : absl::optional<uint64_t>();
  std::vector<absl::optional<uint64_t>> receive_acked_packet_time_ms_values =
      DecodeDeltas(proto.receive_acked_packet_time_ms_deltas(),
                   unsigned_receive_acked_packet_time_ms_base,
                   number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(receive_acked_packet_time_ms_values.size(),
                               number_of_deltas);

  for (size_t i = 0; i < number_of_deltas; i++) {
    int64_t timestamp_ms;
    RTC_PARSE_CHECK_OR_RETURN(
        ToSigned(timestamp_ms_values[i].value(), &timestamp_ms));
    int64_t packet_number;
    RTC_PARSE_CHECK_OR_RETURN(
        ToSigned(packet_number_values[i].value(), &packet_number));
    int64_t acked_packet_number;
    RTC_PARSE_CHECK_OR_RETURN(
        ToSigned(acked_packet_number_values[i].value(), &acked_packet_number));
    absl::optional<int64_t> receive_acked_packet_time_ms;

    if (receive_acked_packet_time_ms_values[i].has_value()) {
      int64_t value;
      RTC_PARSE_CHECK_OR_RETURN(
          ToSigned(receive_acked_packet_time_ms_values[i].value(), &value));
      receive_acked_packet_time_ms = value;
    }
    generic_acks_received_.push_back({Timestamp::Millis(timestamp_ms),
                                      packet_number, acked_packet_number,
                                      receive_acked_packet_time_ms});
  }
  return ParseStatus::Success();
}

ParsedRtcEventLog::ParseStatus ParsedRtcEventLog::StoreGenericPacketSentEvent(
    const rtclog2::GenericPacketSent& proto) {
  RTC_PARSE_CHECK_OR_RETURN(proto.has_timestamp_ms());

  // Base event
  RTC_PARSE_CHECK_OR_RETURN(proto.has_packet_number());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_overhead_length());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_payload_length());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_padding_length());

  generic_packets_sent_.push_back(
      {Timestamp::Millis(proto.timestamp_ms()), proto.packet_number(),
       static_cast<size_t>(proto.overhead_length()),
       static_cast<size_t>(proto.payload_length()),
       static_cast<size_t>(proto.padding_length())});

  const size_t number_of_deltas =
      proto.has_number_of_deltas() ? proto.number_of_deltas() : 0u;
  if (number_of_deltas == 0) {
    return ParseStatus::Success();
  }

  // timestamp_ms
  std::vector<absl::optional<uint64_t>> timestamp_ms_values =
      DecodeDeltas(proto.timestamp_ms_deltas(),
                   ToUnsigned(proto.timestamp_ms()), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(timestamp_ms_values.size(), number_of_deltas);

  // packet_number
  std::vector<absl::optional<uint64_t>> packet_number_values =
      DecodeDeltas(proto.packet_number_deltas(),
                   ToUnsigned(proto.packet_number()), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(packet_number_values.size(), number_of_deltas);

  std::vector<absl::optional<uint64_t>> overhead_length_values =
      DecodeDeltas(proto.overhead_length_deltas(), proto.overhead_length(),
                   number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(overhead_length_values.size(), number_of_deltas);

  std::vector<absl::optional<uint64_t>> payload_length_values = DecodeDeltas(
      proto.payload_length_deltas(), proto.payload_length(), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(payload_length_values.size(), number_of_deltas);

  std::vector<absl::optional<uint64_t>> padding_length_values = DecodeDeltas(
      proto.padding_length_deltas(), proto.padding_length(), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(padding_length_values.size(), number_of_deltas);

  for (size_t i = 0; i < number_of_deltas; i++) {
    int64_t timestamp_ms;
    RTC_PARSE_CHECK_OR_RETURN(
        ToSigned(timestamp_ms_values[i].value(), &timestamp_ms));
    int64_t packet_number;
    RTC_PARSE_CHECK_OR_RETURN(
        ToSigned(packet_number_values[i].value(), &packet_number));
    RTC_PARSE_CHECK_OR_RETURN(overhead_length_values[i].has_value());
    RTC_PARSE_CHECK_OR_RETURN(payload_length_values[i].has_value());
    RTC_PARSE_CHECK_OR_RETURN(padding_length_values[i].has_value());
    generic_packets_sent_.push_back(
        {Timestamp::Millis(timestamp_ms), packet_number,
         static_cast<size_t>(overhead_length_values[i].value()),
         static_cast<size_t>(payload_length_values[i].value()),
         static_cast<size_t>(padding_length_values[i].value())});
  }
  return ParseStatus::Success();
}

ParsedRtcEventLog::ParseStatus
ParsedRtcEventLog::StoreGenericPacketReceivedEvent(
    const rtclog2::GenericPacketReceived& proto) {
  RTC_PARSE_CHECK_OR_RETURN(proto.has_timestamp_ms());

  // Base event
  RTC_PARSE_CHECK_OR_RETURN(proto.has_packet_number());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_packet_length());

  generic_packets_received_.push_back({Timestamp::Millis(proto.timestamp_ms()),
                                       proto.packet_number(),
                                       proto.packet_length()});

  const size_t number_of_deltas =
      proto.has_number_of_deltas() ? proto.number_of_deltas() : 0u;
  if (number_of_deltas == 0) {
    return ParseStatus::Success();
  }

  // timestamp_ms
  std::vector<absl::optional<uint64_t>> timestamp_ms_values =
      DecodeDeltas(proto.timestamp_ms_deltas(),
                   ToUnsigned(proto.timestamp_ms()), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(timestamp_ms_values.size(), number_of_deltas);

  // packet_number
  std::vector<absl::optional<uint64_t>> packet_number_values =
      DecodeDeltas(proto.packet_number_deltas(),
                   ToUnsigned(proto.packet_number()), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(packet_number_values.size(), number_of_deltas);

  std::vector<absl::optional<uint64_t>> packet_length_values = DecodeDeltas(
      proto.packet_length_deltas(), proto.packet_length(), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(packet_length_values.size(), number_of_deltas);

  for (size_t i = 0; i < number_of_deltas; i++) {
    int64_t timestamp_ms;
    RTC_PARSE_CHECK_OR_RETURN(
        ToSigned(timestamp_ms_values[i].value(), &timestamp_ms));
    int64_t packet_number;
    RTC_PARSE_CHECK_OR_RETURN(
        ToSigned(packet_number_values[i].value(), &packet_number));
    RTC_PARSE_CHECK_OR_RETURN_LE(packet_length_values[i].value(),
                                 std::numeric_limits<int32_t>::max());
    int32_t packet_length =
        static_cast<int32_t>(packet_length_values[i].value());
    generic_packets_received_.push_back(
        {Timestamp::Millis(timestamp_ms), packet_number, packet_length});
  }
  return ParseStatus::Success();
}

ParsedRtcEventLog::ParseStatus
ParsedRtcEventLog::StoreAudioNetworkAdaptationEvent(
    const rtclog2::AudioNetworkAdaptations& proto) {
  RTC_PARSE_CHECK_OR_RETURN(proto.has_timestamp_ms());

  // Base event
  {
    AudioEncoderRuntimeConfig runtime_config;
    if (proto.has_bitrate_bps()) {
      runtime_config.bitrate_bps = proto.bitrate_bps();
    }
    if (proto.has_frame_length_ms()) {
      runtime_config.frame_length_ms = proto.frame_length_ms();
    }
    if (proto.has_uplink_packet_loss_fraction()) {
      float uplink_packet_loss_fraction;
      RTC_PARSE_CHECK_OR_RETURN(ParsePacketLossFractionFromProtoFormat(
          proto.uplink_packet_loss_fraction(), &uplink_packet_loss_fraction));
      runtime_config.uplink_packet_loss_fraction = uplink_packet_loss_fraction;
    }
    if (proto.has_enable_fec()) {
      runtime_config.enable_fec = proto.enable_fec();
    }
    if (proto.has_enable_dtx()) {
      runtime_config.enable_dtx = proto.enable_dtx();
    }
    if (proto.has_num_channels()) {
      // Note: Encoding N as N-1 only done for `num_channels_deltas`.
      runtime_config.num_channels = proto.num_channels();
    }
    audio_network_adaptation_events_.emplace_back(
        Timestamp::Millis(proto.timestamp_ms()), runtime_config);
  }

  const size_t number_of_deltas =
      proto.has_number_of_deltas() ? proto.number_of_deltas() : 0u;
  if (number_of_deltas == 0) {
    return ParseStatus::Success();
  }

  // timestamp_ms
  std::vector<absl::optional<uint64_t>> timestamp_ms_values =
      DecodeDeltas(proto.timestamp_ms_deltas(),
                   ToUnsigned(proto.timestamp_ms()), number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(timestamp_ms_values.size(), number_of_deltas);

  // bitrate_bps
  const absl::optional<uint64_t> unsigned_base_bitrate_bps =
      proto.has_bitrate_bps()
          ? absl::optional<uint64_t>(ToUnsigned(proto.bitrate_bps()))
          : absl::optional<uint64_t>();
  std::vector<absl::optional<uint64_t>> bitrate_bps_values = DecodeDeltas(
      proto.bitrate_bps_deltas(), unsigned_base_bitrate_bps, number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(bitrate_bps_values.size(), number_of_deltas);

  // frame_length_ms
  const absl::optional<uint64_t> unsigned_base_frame_length_ms =
      proto.has_frame_length_ms()
          ? absl::optional<uint64_t>(ToUnsigned(proto.frame_length_ms()))
          : absl::optional<uint64_t>();
  std::vector<absl::optional<uint64_t>> frame_length_ms_values =
      DecodeDeltas(proto.frame_length_ms_deltas(),
                   unsigned_base_frame_length_ms, number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(frame_length_ms_values.size(), number_of_deltas);

  // uplink_packet_loss_fraction
  const absl::optional<uint64_t> uplink_packet_loss_fraction =
      proto.has_uplink_packet_loss_fraction()
          ? absl::optional<uint64_t>(proto.uplink_packet_loss_fraction())
          : absl::optional<uint64_t>();
  std::vector<absl::optional<uint64_t>> uplink_packet_loss_fraction_values =
      DecodeDeltas(proto.uplink_packet_loss_fraction_deltas(),
                   uplink_packet_loss_fraction, number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(uplink_packet_loss_fraction_values.size(),
                               number_of_deltas);

  // enable_fec
  const absl::optional<uint64_t> enable_fec =
      proto.has_enable_fec() ? absl::optional<uint64_t>(proto.enable_fec())
                             : absl::optional<uint64_t>();
  std::vector<absl::optional<uint64_t>> enable_fec_values =
      DecodeDeltas(proto.enable_fec_deltas(), enable_fec, number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(enable_fec_values.size(), number_of_deltas);

  // enable_dtx
  const absl::optional<uint64_t> enable_dtx =
      proto.has_enable_dtx() ? absl::optional<uint64_t>(proto.enable_dtx())
                             : absl::optional<uint64_t>();
  std::vector<absl::optional<uint64_t>> enable_dtx_values =
      DecodeDeltas(proto.enable_dtx_deltas(), enable_dtx, number_of_deltas);
  RTC_PARSE_CHECK_OR_RETURN_EQ(enable_dtx_values.size(), number_of_deltas);

  // num_channels
  // Note: For delta encoding, all num_channel values, including the base,
  // were shifted down by one, but in the base event, they were not.
  // We likewise shift the base event down by one, to get the same base as
  // encoding had, but then shift all of the values (except the base) back up
  // to their original value.
  absl::optional<uint64_t> shifted_base_num_channels;
  if (proto.has_num_channels()) {
    shifted_base_num_channels =
        absl::optional<uint64_t>(proto.num_channels() - 1);
  }
  std::vector<absl::optional<uint64_t>> num_channels_values = DecodeDeltas(
      proto.num_channels_deltas(), shifted_base_num_channels, number_of_deltas);
  for (size_t i = 0; i < num_channels_values.size(); ++i) {
    if (num_channels_values[i].has_value()) {
      num_channels_values[i] = num_channels_values[i].value() + 1;
    }
  }
  RTC_PARSE_CHECK_OR_RETURN_EQ(num_channels_values.size(), number_of_deltas);

  // Populate events from decoded deltas
  for (size_t i = 0; i < number_of_deltas; ++i) {
    RTC_PARSE_CHECK_OR_RETURN(timestamp_ms_values[i].has_value());
    int64_t timestamp_ms;
    RTC_PARSE_CHECK_OR_RETURN(
        ToSigned(timestamp_ms_values[i].value(), &timestamp_ms));

    AudioEncoderRuntimeConfig runtime_config;
    if (bitrate_bps_values[i].has_value()) {
      int signed_bitrate_bps;
      RTC_PARSE_CHECK_OR_RETURN(
          ToSigned(bitrate_bps_values[i].value(), &signed_bitrate_bps));
      runtime_config.bitrate_bps = signed_bitrate_bps;
    }
    if (frame_length_ms_values[i].has_value()) {
      int signed_frame_length_ms;
      RTC_PARSE_CHECK_OR_RETURN(
          ToSigned(frame_length_ms_values[i].value(), &signed_frame_length_ms));
      runtime_config.frame_length_ms = signed_frame_length_ms;
    }
    if (uplink_packet_loss_fraction_values[i].has_value()) {
      float uplink_packet_loss_fraction2;
      RTC_PARSE_CHECK_OR_RETURN(ParsePacketLossFractionFromProtoFormat(
          rtc::checked_cast<uint32_t>(
              uplink_packet_loss_fraction_values[i].value()),
          &uplink_packet_loss_fraction2));
      runtime_config.uplink_packet_loss_fraction = uplink_packet_loss_fraction2;
    }
    if (enable_fec_values[i].has_value()) {
      runtime_config.enable_fec =
          rtc::checked_cast<bool>(enable_fec_values[i].value());
    }
    if (enable_dtx_values[i].has_value()) {
      runtime_config.enable_dtx =
          rtc::checked_cast<bool>(enable_dtx_values[i].value());
    }
    if (num_channels_values[i].has_value()) {
      runtime_config.num_channels =
          rtc::checked_cast<size_t>(num_channels_values[i].value());
    }
    audio_network_adaptation_events_.emplace_back(
        Timestamp::Millis(timestamp_ms), runtime_config);
  }
  return ParseStatus::Success();
}

ParsedRtcEventLog::ParseStatus ParsedRtcEventLog::StoreDtlsTransportState(
    const rtclog2::DtlsTransportStateEvent& proto) {
  LoggedDtlsTransportState dtls_state;
  RTC_PARSE_CHECK_OR_RETURN(proto.has_timestamp_ms());
  dtls_state.timestamp = Timestamp::Millis(proto.timestamp_ms());

  RTC_PARSE_CHECK_OR_RETURN(proto.has_dtls_transport_state());
  dtls_state.dtls_transport_state =
      GetRuntimeDtlsTransportState(proto.dtls_transport_state());

  dtls_transport_states_.push_back(dtls_state);
  return ParseStatus::Success();
}

ParsedRtcEventLog::ParseStatus ParsedRtcEventLog::StoreDtlsWritableState(
    const rtclog2::DtlsWritableState& proto) {
  LoggedDtlsWritableState dtls_writable_state;
  RTC_PARSE_CHECK_OR_RETURN(proto.has_timestamp_ms());
  dtls_writable_state.timestamp = Timestamp::Millis(proto.timestamp_ms());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_writable());
  dtls_writable_state.writable = proto.writable();

  dtls_writable_states_.push_back(dtls_writable_state);
  return ParseStatus::Success();
}

ParsedRtcEventLog::ParseStatus ParsedRtcEventLog::StoreIceCandidatePairConfig(
    const rtclog2::IceCandidatePairConfig& proto) {
  LoggedIceCandidatePairConfig ice_config;
  RTC_PARSE_CHECK_OR_RETURN(proto.has_timestamp_ms());
  ice_config.timestamp = Timestamp::Millis(proto.timestamp_ms());

  RTC_PARSE_CHECK_OR_RETURN(proto.has_config_type());
  ice_config.type = GetRuntimeIceCandidatePairConfigType(proto.config_type());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_candidate_pair_id());
  ice_config.candidate_pair_id = proto.candidate_pair_id();
  RTC_PARSE_CHECK_OR_RETURN(proto.has_local_candidate_type());
  ice_config.local_candidate_type =
      GetRuntimeIceCandidateType(proto.local_candidate_type());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_local_relay_protocol());
  ice_config.local_relay_protocol =
      GetRuntimeIceCandidatePairProtocol(proto.local_relay_protocol());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_local_network_type());
  ice_config.local_network_type =
      GetRuntimeIceCandidateNetworkType(proto.local_network_type());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_local_address_family());
  ice_config.local_address_family =
      GetRuntimeIceCandidatePairAddressFamily(proto.local_address_family());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_remote_candidate_type());
  ice_config.remote_candidate_type =
      GetRuntimeIceCandidateType(proto.remote_candidate_type());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_remote_address_family());
  ice_config.remote_address_family =
      GetRuntimeIceCandidatePairAddressFamily(proto.remote_address_family());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_candidate_pair_protocol());
  ice_config.candidate_pair_protocol =
      GetRuntimeIceCandidatePairProtocol(proto.candidate_pair_protocol());

  ice_candidate_pair_configs_.push_back(ice_config);

  // TODO(terelius): Should we delta encode this event type?
  return ParseStatus::Success();
}

ParsedRtcEventLog::ParseStatus ParsedRtcEventLog::StoreIceCandidateEvent(
    const rtclog2::IceCandidatePairEvent& proto) {
  LoggedIceCandidatePairEvent ice_event;
  RTC_PARSE_CHECK_OR_RETURN(proto.has_timestamp_ms());
  ice_event.timestamp = Timestamp::Millis(proto.timestamp_ms());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_event_type());
  ice_event.type = GetRuntimeIceCandidatePairEventType(proto.event_type());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_candidate_pair_id());
  ice_event.candidate_pair_id = proto.candidate_pair_id();
  // TODO(zstein): Make the transaction_id field required once all old versions
  // of the log (which don't have the field) are obsolete.
  ice_event.transaction_id =
      proto.has_transaction_id() ? proto.transaction_id() : 0;

  ice_candidate_pair_events_.push_back(ice_event);

  // TODO(terelius): Should we delta encode this event type?
  return ParseStatus::Success();
}

ParsedRtcEventLog::ParseStatus ParsedRtcEventLog::StoreVideoRecvConfig(
    const rtclog2::VideoRecvStreamConfig& proto) {
  LoggedVideoRecvConfig stream;
  RTC_PARSE_CHECK_OR_RETURN(proto.has_timestamp_ms());
  stream.timestamp = Timestamp::Millis(proto.timestamp_ms());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_remote_ssrc());
  stream.config.remote_ssrc = proto.remote_ssrc();
  RTC_PARSE_CHECK_OR_RETURN(proto.has_local_ssrc());
  stream.config.local_ssrc = proto.local_ssrc();
  if (proto.has_rtx_ssrc()) {
    stream.config.rtx_ssrc = proto.rtx_ssrc();
  }
  if (proto.has_header_extensions()) {
    stream.config.rtp_extensions =
        GetRuntimeRtpHeaderExtensionConfig(proto.header_extensions());
  }
  video_recv_configs_.push_back(stream);
  return ParseStatus::Success();
}

ParsedRtcEventLog::ParseStatus ParsedRtcEventLog::StoreVideoSendConfig(
    const rtclog2::VideoSendStreamConfig& proto) {
  LoggedVideoSendConfig stream;
  RTC_PARSE_CHECK_OR_RETURN(proto.has_timestamp_ms());
  stream.timestamp = Timestamp::Millis(proto.timestamp_ms());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_ssrc());
  stream.config.local_ssrc = proto.ssrc();
  if (proto.has_rtx_ssrc()) {
    stream.config.rtx_ssrc = proto.rtx_ssrc();
  }
  if (proto.has_header_extensions()) {
    stream.config.rtp_extensions =
        GetRuntimeRtpHeaderExtensionConfig(proto.header_extensions());
  }
  video_send_configs_.push_back(stream);
  return ParseStatus::Success();
}

ParsedRtcEventLog::ParseStatus ParsedRtcEventLog::StoreAudioRecvConfig(
    const rtclog2::AudioRecvStreamConfig& proto) {
  LoggedAudioRecvConfig stream;
  RTC_PARSE_CHECK_OR_RETURN(proto.has_timestamp_ms());
  stream.timestamp = Timestamp::Millis(proto.timestamp_ms());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_remote_ssrc());
  stream.config.remote_ssrc = proto.remote_ssrc();
  RTC_PARSE_CHECK_OR_RETURN(proto.has_local_ssrc());
  stream.config.local_ssrc = proto.local_ssrc();
  if (proto.has_header_extensions()) {
    stream.config.rtp_extensions =
        GetRuntimeRtpHeaderExtensionConfig(proto.header_extensions());
  }
  audio_recv_configs_.push_back(stream);
  return ParseStatus::Success();
}

ParsedRtcEventLog::ParseStatus ParsedRtcEventLog::StoreAudioSendConfig(
    const rtclog2::AudioSendStreamConfig& proto) {
  LoggedAudioSendConfig stream;
  RTC_PARSE_CHECK_OR_RETURN(proto.has_timestamp_ms());
  stream.timestamp = Timestamp::Millis(proto.timestamp_ms());
  RTC_PARSE_CHECK_OR_RETURN(proto.has_ssrc());
  stream.config.local_ssrc = proto.ssrc();
  if (proto.has_header_extensions()) {
    stream.config.rtp_extensions =
        GetRuntimeRtpHeaderExtensionConfig(proto.header_extensions());
  }
  audio_send_configs_.push_back(stream);
  return ParseStatus::Success();
}

}  // namespace webrtc
