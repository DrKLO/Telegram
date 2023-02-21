/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "logging/rtc_event_log/rtc_event_log_unittest_helper.h"

#include <string.h>  // memcmp

#include <cmath>
#include <cstdint>
#include <limits>
#include <memory>
#include <numeric>
#include <string>
#include <utility>
#include <vector>

#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/array_view.h"
#include "api/network_state_predictor.h"
#include "api/rtp_headers.h"
#include "api/rtp_parameters.h"
#include "api/units/time_delta.h"
#include "api/units/timestamp.h"
#include "modules/audio_coding/audio_network_adaptor/include/audio_network_adaptor_config.h"
#include "modules/rtp_rtcp/include/rtp_cvo.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "modules/rtp_rtcp/source/rtcp_packet/dlrr.h"
#include "modules/rtp_rtcp/source/rtcp_packet/rrtr.h"
#include "modules/rtp_rtcp/source/rtcp_packet/target_bitrate.h"
#include "modules/rtp_rtcp/source/rtp_header_extensions.h"
#include "modules/rtp_rtcp/source/rtp_packet_received.h"
#include "modules/rtp_rtcp/source/rtp_packet_to_send.h"
#include "rtc_base/buffer.h"
#include "rtc_base/checks.h"
#include "rtc_base/time_utils.h"
#include "system_wrappers/include/ntp_time.h"
#include "test/gtest.h"

namespace webrtc {

namespace test {

namespace {

struct ExtensionPair {
  RTPExtensionType type;
  const char* name;
};

constexpr int kMaxCsrcs = 3;

// Maximum serialized size of a header extension, including 1 byte ID.
constexpr int kMaxExtensionSizeBytes = 4;
constexpr int kMaxNumExtensions = 5;

constexpr ExtensionPair kExtensions[kMaxNumExtensions] = {
    {RTPExtensionType::kRtpExtensionTransmissionTimeOffset,
     RtpExtension::kTimestampOffsetUri},
    {RTPExtensionType::kRtpExtensionAbsoluteSendTime,
     RtpExtension::kAbsSendTimeUri},
    {RTPExtensionType::kRtpExtensionTransportSequenceNumber,
     RtpExtension::kTransportSequenceNumberUri},
    {RTPExtensionType::kRtpExtensionAudioLevel, RtpExtension::kAudioLevelUri},
    {RTPExtensionType::kRtpExtensionVideoRotation,
     RtpExtension::kVideoRotationUri}};

template <typename T>
void ShuffleInPlace(Random* prng, rtc::ArrayView<T> array) {
  RTC_DCHECK_LE(array.size(), std::numeric_limits<uint32_t>::max());
  for (uint32_t i = 0; i + 1 < array.size(); i++) {
    uint32_t other = prng->Rand(i, static_cast<uint32_t>(array.size() - 1));
    std::swap(array[i], array[other]);
  }
}

absl::optional<int> GetExtensionId(const std::vector<RtpExtension>& extensions,
                                   absl::string_view uri) {
  for (const auto& extension : extensions) {
    if (extension.uri == uri)
      return extension.id;
  }
  return absl::nullopt;
}

}  // namespace

std::unique_ptr<RtcEventAlrState> EventGenerator::NewAlrState() {
  return std::make_unique<RtcEventAlrState>(prng_.Rand<bool>());
}

std::unique_ptr<RtcEventAudioPlayout> EventGenerator::NewAudioPlayout(
    uint32_t ssrc) {
  return std::make_unique<RtcEventAudioPlayout>(ssrc);
}

std::unique_ptr<RtcEventAudioNetworkAdaptation>
EventGenerator::NewAudioNetworkAdaptation() {
  std::unique_ptr<AudioEncoderRuntimeConfig> config =
      std::make_unique<AudioEncoderRuntimeConfig>();

  config->bitrate_bps = prng_.Rand(0, 3000000);
  config->enable_fec = prng_.Rand<bool>();
  config->enable_dtx = prng_.Rand<bool>();
  config->frame_length_ms = prng_.Rand(10, 120);
  config->num_channels = prng_.Rand(1, 2);
  config->uplink_packet_loss_fraction = prng_.Rand<float>();

  return std::make_unique<RtcEventAudioNetworkAdaptation>(std::move(config));
}

std::unique_ptr<RtcEventBweUpdateDelayBased>
EventGenerator::NewBweUpdateDelayBased() {
  constexpr int32_t kMaxBweBps = 20000000;
  int32_t bitrate_bps = prng_.Rand(0, kMaxBweBps);
  BandwidthUsage state = static_cast<BandwidthUsage>(
      prng_.Rand(static_cast<uint32_t>(BandwidthUsage::kLast) - 1));
  return std::make_unique<RtcEventBweUpdateDelayBased>(bitrate_bps, state);
}

std::unique_ptr<RtcEventBweUpdateLossBased>
EventGenerator::NewBweUpdateLossBased() {
  constexpr int32_t kMaxBweBps = 20000000;
  constexpr int32_t kMaxPackets = 1000;
  int32_t bitrate_bps = prng_.Rand(0, kMaxBweBps);
  uint8_t fraction_lost = prng_.Rand<uint8_t>();
  int32_t total_packets = prng_.Rand(1, kMaxPackets);

  return std::make_unique<RtcEventBweUpdateLossBased>(
      bitrate_bps, fraction_lost, total_packets);
}

std::unique_ptr<RtcEventDtlsTransportState>
EventGenerator::NewDtlsTransportState() {
  DtlsTransportState state = static_cast<DtlsTransportState>(
      prng_.Rand(static_cast<uint32_t>(DtlsTransportState::kNumValues) - 1));

  return std::make_unique<RtcEventDtlsTransportState>(state);
}

std::unique_ptr<RtcEventDtlsWritableState>
EventGenerator::NewDtlsWritableState() {
  bool writable = prng_.Rand<bool>();
  return std::make_unique<RtcEventDtlsWritableState>(writable);
}

std::unique_ptr<RtcEventFrameDecoded> EventGenerator::NewFrameDecodedEvent(
    uint32_t ssrc) {
  constexpr int kMinRenderDelayMs = 1;
  constexpr int kMaxRenderDelayMs = 2000000;
  constexpr int kMaxWidth = 15360;
  constexpr int kMaxHeight = 8640;
  constexpr int kMinWidth = 16;
  constexpr int kMinHeight = 16;
  constexpr int kNumCodecTypes = 5;

  constexpr VideoCodecType kCodecList[kNumCodecTypes] = {
      kVideoCodecGeneric, kVideoCodecVP8, kVideoCodecVP9, kVideoCodecAV1,
      kVideoCodecH264};
  const int64_t render_time_ms =
      rtc::TimeMillis() + prng_.Rand(kMinRenderDelayMs, kMaxRenderDelayMs);
  const int width = prng_.Rand(kMinWidth, kMaxWidth);
  const int height = prng_.Rand(kMinHeight, kMaxHeight);
  const VideoCodecType codec = kCodecList[prng_.Rand(0, kNumCodecTypes - 1)];
  const uint8_t qp = prng_.Rand<uint8_t>();
  return std::make_unique<RtcEventFrameDecoded>(render_time_ms, ssrc, width,
                                                height, codec, qp);
}

std::unique_ptr<RtcEventProbeClusterCreated>
EventGenerator::NewProbeClusterCreated() {
  constexpr int kMaxBweBps = 20000000;
  constexpr int kMaxNumProbes = 10000;
  int id = prng_.Rand(1, kMaxNumProbes);
  int bitrate_bps = prng_.Rand(0, kMaxBweBps);
  int min_probes = prng_.Rand(5, 50);
  int min_bytes = prng_.Rand(500, 50000);

  return std::make_unique<RtcEventProbeClusterCreated>(id, bitrate_bps,
                                                       min_probes, min_bytes);
}

std::unique_ptr<RtcEventProbeResultFailure>
EventGenerator::NewProbeResultFailure() {
  constexpr int kMaxNumProbes = 10000;
  int id = prng_.Rand(1, kMaxNumProbes);
  ProbeFailureReason reason = static_cast<ProbeFailureReason>(
      prng_.Rand(static_cast<uint32_t>(ProbeFailureReason::kLast) - 1));

  return std::make_unique<RtcEventProbeResultFailure>(id, reason);
}

std::unique_ptr<RtcEventProbeResultSuccess>
EventGenerator::NewProbeResultSuccess() {
  constexpr int kMaxBweBps = 20000000;
  constexpr int kMaxNumProbes = 10000;
  int id = prng_.Rand(1, kMaxNumProbes);
  int bitrate_bps = prng_.Rand(0, kMaxBweBps);

  return std::make_unique<RtcEventProbeResultSuccess>(id, bitrate_bps);
}

std::unique_ptr<RtcEventIceCandidatePairConfig>
EventGenerator::NewIceCandidatePairConfig() {
  IceCandidateType local_candidate_type = static_cast<IceCandidateType>(
      prng_.Rand(static_cast<uint32_t>(IceCandidateType::kNumValues) - 1));
  IceCandidateNetworkType local_network_type =
      static_cast<IceCandidateNetworkType>(prng_.Rand(
          static_cast<uint32_t>(IceCandidateNetworkType::kNumValues) - 1));
  IceCandidatePairAddressFamily local_address_family =
      static_cast<IceCandidatePairAddressFamily>(prng_.Rand(
          static_cast<uint32_t>(IceCandidatePairAddressFamily::kNumValues) -
          1));
  IceCandidateType remote_candidate_type = static_cast<IceCandidateType>(
      prng_.Rand(static_cast<uint32_t>(IceCandidateType::kNumValues) - 1));
  IceCandidatePairAddressFamily remote_address_family =
      static_cast<IceCandidatePairAddressFamily>(prng_.Rand(
          static_cast<uint32_t>(IceCandidatePairAddressFamily::kNumValues) -
          1));
  IceCandidatePairProtocol protocol_type =
      static_cast<IceCandidatePairProtocol>(prng_.Rand(
          static_cast<uint32_t>(IceCandidatePairProtocol::kNumValues) - 1));

  IceCandidatePairDescription desc;
  desc.local_candidate_type = local_candidate_type;
  desc.local_relay_protocol = protocol_type;
  desc.local_network_type = local_network_type;
  desc.local_address_family = local_address_family;
  desc.remote_candidate_type = remote_candidate_type;
  desc.remote_address_family = remote_address_family;
  desc.candidate_pair_protocol = protocol_type;

  IceCandidatePairConfigType type =
      static_cast<IceCandidatePairConfigType>(prng_.Rand(
          static_cast<uint32_t>(IceCandidatePairConfigType::kNumValues) - 1));
  uint32_t pair_id = prng_.Rand<uint32_t>();
  return std::make_unique<RtcEventIceCandidatePairConfig>(type, pair_id, desc);
}

std::unique_ptr<RtcEventIceCandidatePair>
EventGenerator::NewIceCandidatePair() {
  IceCandidatePairEventType type =
      static_cast<IceCandidatePairEventType>(prng_.Rand(
          static_cast<uint32_t>(IceCandidatePairEventType::kNumValues) - 1));
  uint32_t pair_id = prng_.Rand<uint32_t>();
  uint32_t transaction_id = prng_.Rand<uint32_t>();

  return std::make_unique<RtcEventIceCandidatePair>(type, pair_id,
                                                    transaction_id);
}

rtcp::ReportBlock EventGenerator::NewReportBlock() {
  rtcp::ReportBlock report_block;
  report_block.SetMediaSsrc(prng_.Rand<uint32_t>());
  report_block.SetFractionLost(prng_.Rand<uint8_t>());
  // cumulative_lost is a 3-byte signed value.
  RTC_DCHECK(report_block.SetCumulativeLost(
      prng_.Rand(-(1 << 23) + 1, (1 << 23) - 1)));
  report_block.SetExtHighestSeqNum(prng_.Rand<uint32_t>());
  report_block.SetJitter(prng_.Rand<uint32_t>());
  report_block.SetLastSr(prng_.Rand<uint32_t>());
  report_block.SetDelayLastSr(prng_.Rand<uint32_t>());
  return report_block;
}

rtcp::SenderReport EventGenerator::NewSenderReport() {
  rtcp::SenderReport sender_report;
  sender_report.SetSenderSsrc(prng_.Rand<uint32_t>());
  sender_report.SetNtp(NtpTime(prng_.Rand<uint32_t>(), prng_.Rand<uint32_t>()));
  sender_report.SetRtpTimestamp(prng_.Rand<uint32_t>());
  sender_report.SetPacketCount(prng_.Rand<uint32_t>());
  sender_report.SetOctetCount(prng_.Rand<uint32_t>());
  sender_report.AddReportBlock(NewReportBlock());
  return sender_report;
}

rtcp::ReceiverReport EventGenerator::NewReceiverReport() {
  rtcp::ReceiverReport receiver_report;
  receiver_report.SetSenderSsrc(prng_.Rand<uint32_t>());
  receiver_report.AddReportBlock(NewReportBlock());
  return receiver_report;
}

rtcp::ExtendedReports EventGenerator::NewExtendedReports() {
  rtcp::ExtendedReports extended_report;
  extended_report.SetSenderSsrc(prng_.Rand<uint32_t>());

  rtcp::Rrtr rrtr;
  rrtr.SetNtp(NtpTime(prng_.Rand<uint32_t>(), prng_.Rand<uint32_t>()));
  extended_report.SetRrtr(rrtr);

  rtcp::ReceiveTimeInfo time_info(
      prng_.Rand<uint32_t>(), prng_.Rand<uint32_t>(), prng_.Rand<uint32_t>());
  extended_report.AddDlrrItem(time_info);

  rtcp::TargetBitrate target_bitrate;
  target_bitrate.AddTargetBitrate(/*spatial layer*/ prng_.Rand(0, 3),
                                  /*temporal layer*/ prng_.Rand(0, 3),
                                  /*bitrate kbps*/ prng_.Rand(0, 50000));
  target_bitrate.AddTargetBitrate(/*spatial layer*/ prng_.Rand(4, 7),
                                  /*temporal layer*/ prng_.Rand(4, 7),
                                  /*bitrate kbps*/ prng_.Rand(0, 50000));
  extended_report.SetTargetBitrate(target_bitrate);
  return extended_report;
}

rtcp::Nack EventGenerator::NewNack() {
  rtcp::Nack nack;
  uint16_t base_seq_no = prng_.Rand<uint16_t>();
  std::vector<uint16_t> nack_list;
  nack_list.push_back(base_seq_no);
  for (uint16_t i = 1u; i < 10u; i++) {
    if (prng_.Rand<bool>())
      nack_list.push_back(base_seq_no + i);
  }
  nack.SetPacketIds(nack_list);
  return nack;
}

rtcp::Fir EventGenerator::NewFir() {
  rtcp::Fir fir;
  fir.SetSenderSsrc(prng_.Rand<uint32_t>());
  fir.AddRequestTo(/*ssrc*/ prng_.Rand<uint32_t>(),
                   /*seq num*/ prng_.Rand<uint8_t>());
  fir.AddRequestTo(/*ssrc*/ prng_.Rand<uint32_t>(),
                   /*seq num*/ prng_.Rand<uint8_t>());
  return fir;
}

rtcp::Pli EventGenerator::NewPli() {
  rtcp::Pli pli;
  pli.SetSenderSsrc(prng_.Rand<uint32_t>());
  pli.SetMediaSsrc(prng_.Rand<uint32_t>());
  return pli;
}

rtcp::Bye EventGenerator::NewBye() {
  rtcp::Bye bye;
  bye.SetSenderSsrc(prng_.Rand<uint32_t>());
  std::vector<uint32_t> csrcs{prng_.Rand<uint32_t>(), prng_.Rand<uint32_t>()};
  bye.SetCsrcs(csrcs);
  if (prng_.Rand(0, 2)) {
    bye.SetReason("foo");
  } else {
    bye.SetReason("bar");
  }
  return bye;
}

rtcp::TransportFeedback EventGenerator::NewTransportFeedback() {
  rtcp::TransportFeedback transport_feedback;
  uint16_t base_seq_no = prng_.Rand<uint16_t>();
  Timestamp base_time = Timestamp::Micros(prng_.Rand<uint32_t>());
  transport_feedback.SetBase(base_seq_no, base_time);
  transport_feedback.AddReceivedPacket(base_seq_no, base_time);
  Timestamp time = base_time;
  for (uint16_t i = 1u; i < 10u; i++) {
    time += TimeDelta::Micros(prng_.Rand(0, 100'000));
    if (prng_.Rand<bool>()) {
      transport_feedback.AddReceivedPacket(base_seq_no + i, time);
    }
  }
  return transport_feedback;
}

rtcp::Remb EventGenerator::NewRemb() {
  rtcp::Remb remb;
  // The remb bitrate is transported as a 16-bit mantissa and an 8-bit exponent.
  uint64_t bitrate_bps = prng_.Rand(0, (1 << 16) - 1) << prng_.Rand(7);
  std::vector<uint32_t> ssrcs{prng_.Rand<uint32_t>(), prng_.Rand<uint32_t>()};
  remb.SetSsrcs(ssrcs);
  remb.SetBitrateBps(bitrate_bps);
  return remb;
}

rtcp::LossNotification EventGenerator::NewLossNotification() {
  rtcp::LossNotification loss_notification;
  const uint16_t last_decoded = prng_.Rand<uint16_t>();
  const uint16_t last_received =
      last_decoded + (prng_.Rand<uint16_t>() & 0x7fff);
  const bool decodability_flag = prng_.Rand<bool>();
  EXPECT_TRUE(
      loss_notification.Set(last_decoded, last_received, decodability_flag));
  return loss_notification;
}

std::unique_ptr<RtcEventRouteChange> EventGenerator::NewRouteChange() {
  return std::make_unique<RtcEventRouteChange>(prng_.Rand<bool>(),
                                               prng_.Rand(0, 128));
}

std::unique_ptr<RtcEventRemoteEstimate> EventGenerator::NewRemoteEstimate() {
  return std::make_unique<RtcEventRemoteEstimate>(
      DataRate::KilobitsPerSec(prng_.Rand(0, 100000)),
      DataRate::KilobitsPerSec(prng_.Rand(0, 100000)));
}

std::unique_ptr<RtcEventRtcpPacketIncoming>
EventGenerator::NewRtcpPacketIncoming() {
  enum class SupportedRtcpTypes {
    kSenderReport = 0,
    kReceiverReport,
    kExtendedReports,
    kFir,
    kPli,
    kNack,
    kRemb,
    kBye,
    kTransportFeedback,
    kNumValues
  };
  SupportedRtcpTypes type = static_cast<SupportedRtcpTypes>(
      prng_.Rand(0, static_cast<int>(SupportedRtcpTypes::kNumValues) - 1));
  switch (type) {
    case SupportedRtcpTypes::kSenderReport: {
      rtcp::SenderReport sender_report = NewSenderReport();
      rtc::Buffer buffer = sender_report.Build();
      return std::make_unique<RtcEventRtcpPacketIncoming>(buffer);
    }
    case SupportedRtcpTypes::kReceiverReport: {
      rtcp::ReceiverReport receiver_report = NewReceiverReport();
      rtc::Buffer buffer = receiver_report.Build();
      return std::make_unique<RtcEventRtcpPacketIncoming>(buffer);
    }
    case SupportedRtcpTypes::kExtendedReports: {
      rtcp::ExtendedReports extended_report = NewExtendedReports();
      rtc::Buffer buffer = extended_report.Build();
      return std::make_unique<RtcEventRtcpPacketIncoming>(buffer);
    }
    case SupportedRtcpTypes::kFir: {
      rtcp::Fir fir = NewFir();
      rtc::Buffer buffer = fir.Build();
      return std::make_unique<RtcEventRtcpPacketIncoming>(buffer);
    }
    case SupportedRtcpTypes::kPli: {
      rtcp::Pli pli = NewPli();
      rtc::Buffer buffer = pli.Build();
      return std::make_unique<RtcEventRtcpPacketIncoming>(buffer);
    }
    case SupportedRtcpTypes::kNack: {
      rtcp::Nack nack = NewNack();
      rtc::Buffer buffer = nack.Build();
      return std::make_unique<RtcEventRtcpPacketIncoming>(buffer);
    }
    case SupportedRtcpTypes::kRemb: {
      rtcp::Remb remb = NewRemb();
      rtc::Buffer buffer = remb.Build();
      return std::make_unique<RtcEventRtcpPacketIncoming>(buffer);
    }
    case SupportedRtcpTypes::kBye: {
      rtcp::Bye bye = NewBye();
      rtc::Buffer buffer = bye.Build();
      return std::make_unique<RtcEventRtcpPacketIncoming>(buffer);
    }
    case SupportedRtcpTypes::kTransportFeedback: {
      rtcp::TransportFeedback transport_feedback = NewTransportFeedback();
      rtc::Buffer buffer = transport_feedback.Build();
      return std::make_unique<RtcEventRtcpPacketIncoming>(buffer);
    }
    default:
      RTC_DCHECK_NOTREACHED();
      rtc::Buffer buffer;
      return std::make_unique<RtcEventRtcpPacketIncoming>(buffer);
  }
}

std::unique_ptr<RtcEventRtcpPacketOutgoing>
EventGenerator::NewRtcpPacketOutgoing() {
  enum class SupportedRtcpTypes {
    kSenderReport = 0,
    kReceiverReport,
    kExtendedReports,
    kFir,
    kPli,
    kNack,
    kRemb,
    kBye,
    kTransportFeedback,
    kNumValues
  };
  SupportedRtcpTypes type = static_cast<SupportedRtcpTypes>(
      prng_.Rand(0, static_cast<int>(SupportedRtcpTypes::kNumValues) - 1));
  switch (type) {
    case SupportedRtcpTypes::kSenderReport: {
      rtcp::SenderReport sender_report = NewSenderReport();
      rtc::Buffer buffer = sender_report.Build();
      return std::make_unique<RtcEventRtcpPacketOutgoing>(buffer);
    }
    case SupportedRtcpTypes::kReceiverReport: {
      rtcp::ReceiverReport receiver_report = NewReceiverReport();
      rtc::Buffer buffer = receiver_report.Build();
      return std::make_unique<RtcEventRtcpPacketOutgoing>(buffer);
    }
    case SupportedRtcpTypes::kExtendedReports: {
      rtcp::ExtendedReports extended_report = NewExtendedReports();
      rtc::Buffer buffer = extended_report.Build();
      return std::make_unique<RtcEventRtcpPacketOutgoing>(buffer);
    }
    case SupportedRtcpTypes::kFir: {
      rtcp::Fir fir = NewFir();
      rtc::Buffer buffer = fir.Build();
      return std::make_unique<RtcEventRtcpPacketOutgoing>(buffer);
    }
    case SupportedRtcpTypes::kPli: {
      rtcp::Pli pli = NewPli();
      rtc::Buffer buffer = pli.Build();
      return std::make_unique<RtcEventRtcpPacketOutgoing>(buffer);
    }
    case SupportedRtcpTypes::kNack: {
      rtcp::Nack nack = NewNack();
      rtc::Buffer buffer = nack.Build();
      return std::make_unique<RtcEventRtcpPacketOutgoing>(buffer);
    }
    case SupportedRtcpTypes::kRemb: {
      rtcp::Remb remb = NewRemb();
      rtc::Buffer buffer = remb.Build();
      return std::make_unique<RtcEventRtcpPacketOutgoing>(buffer);
    }
    case SupportedRtcpTypes::kBye: {
      rtcp::Bye bye = NewBye();
      rtc::Buffer buffer = bye.Build();
      return std::make_unique<RtcEventRtcpPacketOutgoing>(buffer);
    }
    case SupportedRtcpTypes::kTransportFeedback: {
      rtcp::TransportFeedback transport_feedback = NewTransportFeedback();
      rtc::Buffer buffer = transport_feedback.Build();
      return std::make_unique<RtcEventRtcpPacketOutgoing>(buffer);
    }
    default:
      RTC_DCHECK_NOTREACHED();
      rtc::Buffer buffer;
      return std::make_unique<RtcEventRtcpPacketOutgoing>(buffer);
  }
}

std::unique_ptr<RtcEventGenericPacketSent>
EventGenerator::NewGenericPacketSent() {
  return std::make_unique<RtcEventGenericPacketSent>(
      sent_packet_number_++, prng_.Rand(40, 50), prng_.Rand(0, 150),
      prng_.Rand(0, 1000));
}
std::unique_ptr<RtcEventGenericPacketReceived>
EventGenerator::NewGenericPacketReceived() {
  return std::make_unique<RtcEventGenericPacketReceived>(
      received_packet_number_++, prng_.Rand(40, 250));
}
std::unique_ptr<RtcEventGenericAckReceived>
EventGenerator::NewGenericAckReceived() {
  absl::optional<int64_t> receive_timestamp = absl::nullopt;
  if (prng_.Rand(0, 2) > 0) {
    receive_timestamp = prng_.Rand(0, 100000);
  }
  AckedPacket packet = {prng_.Rand(40, 250), receive_timestamp};
  return std::move(RtcEventGenericAckReceived::CreateLogs(
      received_packet_number_++, std::vector<AckedPacket>{packet})[0]);
}

void EventGenerator::RandomizeRtpPacket(
    size_t payload_size,
    size_t padding_size,
    uint32_t ssrc,
    const RtpHeaderExtensionMap& extension_map,
    RtpPacket* rtp_packet,
    bool all_configured_exts) {
  constexpr int kMaxPayloadType = 127;
  rtp_packet->SetPayloadType(prng_.Rand(kMaxPayloadType));
  rtp_packet->SetMarker(prng_.Rand<bool>());
  rtp_packet->SetSequenceNumber(prng_.Rand<uint16_t>());
  rtp_packet->SetSsrc(ssrc);
  rtp_packet->SetTimestamp(prng_.Rand<uint32_t>());

  uint32_t csrcs_count = prng_.Rand(0, kMaxCsrcs);
  std::vector<uint32_t> csrcs;
  for (size_t i = 0; i < csrcs_count; i++) {
    csrcs.push_back(prng_.Rand<uint32_t>());
  }
  rtp_packet->SetCsrcs(csrcs);

  if (extension_map.IsRegistered(TransmissionOffset::kId) &&
      (all_configured_exts || prng_.Rand<bool>())) {
    rtp_packet->SetExtension<TransmissionOffset>(prng_.Rand(0x00ffffff));
  }

  if (extension_map.IsRegistered(AudioLevel::kId) &&
      (all_configured_exts || prng_.Rand<bool>())) {
    rtp_packet->SetExtension<AudioLevel>(prng_.Rand<bool>(), prng_.Rand(127));
  }

  if (extension_map.IsRegistered(AbsoluteSendTime::kId) &&
      (all_configured_exts || prng_.Rand<bool>())) {
    rtp_packet->SetExtension<AbsoluteSendTime>(prng_.Rand(0x00ffffff));
  }

  if (extension_map.IsRegistered(VideoOrientation::kId) &&
      (all_configured_exts || prng_.Rand<bool>())) {
    rtp_packet->SetExtension<VideoOrientation>(prng_.Rand(3));
  }

  if (extension_map.IsRegistered(TransportSequenceNumber::kId) &&
      (all_configured_exts || prng_.Rand<bool>())) {
    rtp_packet->SetExtension<TransportSequenceNumber>(prng_.Rand<uint16_t>());
  }

  RTC_CHECK_LE(rtp_packet->headers_size() + payload_size, IP_PACKET_SIZE);

  uint8_t* payload = rtp_packet->AllocatePayload(payload_size);
  RTC_DCHECK(payload != nullptr);
  for (size_t i = 0; i < payload_size; i++) {
    payload[i] = prng_.Rand<uint8_t>();
  }
  RTC_CHECK(rtp_packet->SetPadding(padding_size));
}

std::unique_ptr<RtcEventRtpPacketIncoming> EventGenerator::NewRtpPacketIncoming(
    uint32_t ssrc,
    const RtpHeaderExtensionMap& extension_map,
    bool all_configured_exts) {
  constexpr size_t kMaxPaddingLength = 224;
  const bool padding = prng_.Rand(0, 9) == 0;  // Let padding be 10% probable.
  const size_t padding_size = !padding ? 0u : prng_.Rand(0u, kMaxPaddingLength);

  // 12 bytes RTP header, 4 bytes for 0xBEDE + alignment, 4 bytes per CSRC.
  constexpr size_t kMaxHeaderSize =
      16 + 4 * kMaxCsrcs + kMaxExtensionSizeBytes * kMaxNumExtensions;

  // In principle, a packet can contain both padding and other payload.
  // Currently, RTC eventlog encoder-parser can only maintain padding length if
  // packet is full padding.
  // TODO(webrtc:9730): Remove the deterministic logic for padding_size > 0.
  size_t payload_size =
      padding_size > 0 ? 0
                       : prng_.Rand(0u, static_cast<uint32_t>(IP_PACKET_SIZE -
                                                              1 - padding_size -
                                                              kMaxHeaderSize));

  RtpPacketReceived rtp_packet(&extension_map);
  RandomizeRtpPacket(payload_size, padding_size, ssrc, extension_map,
                     &rtp_packet, all_configured_exts);

  return std::make_unique<RtcEventRtpPacketIncoming>(rtp_packet);
}

std::unique_ptr<RtcEventRtpPacketOutgoing> EventGenerator::NewRtpPacketOutgoing(
    uint32_t ssrc,
    const RtpHeaderExtensionMap& extension_map,
    bool all_configured_exts) {
  constexpr size_t kMaxPaddingLength = 224;
  const bool padding = prng_.Rand(0, 9) == 0;  // Let padding be 10% probable.
  const size_t padding_size = !padding ? 0u : prng_.Rand(0u, kMaxPaddingLength);

  // 12 bytes RTP header, 4 bytes for 0xBEDE + alignment, 4 bytes per CSRC.
  constexpr size_t kMaxHeaderSize =
      16 + 4 * kMaxCsrcs + kMaxExtensionSizeBytes * kMaxNumExtensions;

  // In principle,a packet can contain both padding and other payload.
  // Currently, RTC eventlog encoder-parser can only maintain padding length if
  // packet is full padding.
  // TODO(webrtc:9730): Remove the deterministic logic for padding_size > 0.
  size_t payload_size =
      padding_size > 0 ? 0
                       : prng_.Rand(0u, static_cast<uint32_t>(IP_PACKET_SIZE -
                                                              1 - padding_size -
                                                              kMaxHeaderSize));

  RtpPacketToSend rtp_packet(&extension_map,
                             kMaxHeaderSize + payload_size + padding_size);
  RandomizeRtpPacket(payload_size, padding_size, ssrc, extension_map,
                     &rtp_packet, all_configured_exts);

  int probe_cluster_id = prng_.Rand(0, 100000);
  return std::make_unique<RtcEventRtpPacketOutgoing>(rtp_packet,
                                                     probe_cluster_id);
}

RtpHeaderExtensionMap EventGenerator::NewRtpHeaderExtensionMap(
    bool configure_all) {
  RtpHeaderExtensionMap extension_map;
  std::vector<int> id(RtpExtension::kOneByteHeaderExtensionMaxId -
                      RtpExtension::kMinId + 1);
  std::iota(id.begin(), id.end(), RtpExtension::kMinId);
  ShuffleInPlace(&prng_, rtc::ArrayView<int>(id));

  if (configure_all || prng_.Rand<bool>()) {
    extension_map.Register<AudioLevel>(id[0]);
  }
  if (configure_all || prng_.Rand<bool>()) {
    extension_map.Register<TransmissionOffset>(id[1]);
  }
  if (configure_all || prng_.Rand<bool>()) {
    extension_map.Register<AbsoluteSendTime>(id[2]);
  }
  if (configure_all || prng_.Rand<bool>()) {
    extension_map.Register<VideoOrientation>(id[3]);
  }
  if (configure_all || prng_.Rand<bool>()) {
    extension_map.Register<TransportSequenceNumber>(id[4]);
  }

  return extension_map;
}

std::unique_ptr<RtcEventAudioReceiveStreamConfig>
EventGenerator::NewAudioReceiveStreamConfig(
    uint32_t ssrc,
    const RtpHeaderExtensionMap& extensions) {
  auto config = std::make_unique<rtclog::StreamConfig>();
  // Add SSRCs for the stream.
  config->remote_ssrc = ssrc;
  config->local_ssrc = prng_.Rand<uint32_t>();
  // Add header extensions.
  for (size_t i = 0; i < kMaxNumExtensions; i++) {
    uint8_t id = extensions.GetId(kExtensions[i].type);
    if (id != RtpHeaderExtensionMap::kInvalidId) {
      config->rtp_extensions.emplace_back(kExtensions[i].name, id);
    }
  }

  return std::make_unique<RtcEventAudioReceiveStreamConfig>(std::move(config));
}

std::unique_ptr<RtcEventAudioSendStreamConfig>
EventGenerator::NewAudioSendStreamConfig(
    uint32_t ssrc,
    const RtpHeaderExtensionMap& extensions) {
  auto config = std::make_unique<rtclog::StreamConfig>();
  // Add SSRC to the stream.
  config->local_ssrc = ssrc;
  // Add header extensions.
  for (size_t i = 0; i < kMaxNumExtensions; i++) {
    uint8_t id = extensions.GetId(kExtensions[i].type);
    if (id != RtpHeaderExtensionMap::kInvalidId) {
      config->rtp_extensions.emplace_back(kExtensions[i].name, id);
    }
  }
  return std::make_unique<RtcEventAudioSendStreamConfig>(std::move(config));
}

std::unique_ptr<RtcEventVideoReceiveStreamConfig>
EventGenerator::NewVideoReceiveStreamConfig(
    uint32_t ssrc,
    const RtpHeaderExtensionMap& extensions) {
  auto config = std::make_unique<rtclog::StreamConfig>();

  // Add SSRCs for the stream.
  config->remote_ssrc = ssrc;
  config->local_ssrc = prng_.Rand<uint32_t>();
  // Add extensions and settings for RTCP.
  config->rtcp_mode =
      prng_.Rand<bool>() ? RtcpMode::kCompound : RtcpMode::kReducedSize;
  config->remb = prng_.Rand<bool>();
  config->rtx_ssrc = prng_.Rand<uint32_t>();
  config->codecs.emplace_back(prng_.Rand<bool>() ? "VP8" : "H264",
                              prng_.Rand(127), prng_.Rand(127));
  // Add header extensions.
  for (size_t i = 0; i < kMaxNumExtensions; i++) {
    uint8_t id = extensions.GetId(kExtensions[i].type);
    if (id != RtpHeaderExtensionMap::kInvalidId) {
      config->rtp_extensions.emplace_back(kExtensions[i].name, id);
    }
  }
  return std::make_unique<RtcEventVideoReceiveStreamConfig>(std::move(config));
}

std::unique_ptr<RtcEventVideoSendStreamConfig>
EventGenerator::NewVideoSendStreamConfig(
    uint32_t ssrc,
    const RtpHeaderExtensionMap& extensions) {
  auto config = std::make_unique<rtclog::StreamConfig>();

  config->codecs.emplace_back(prng_.Rand<bool>() ? "VP8" : "H264",
                              prng_.Rand(127), prng_.Rand(127));
  config->local_ssrc = ssrc;
  config->rtx_ssrc = prng_.Rand<uint32_t>();
  // Add header extensions.
  for (size_t i = 0; i < kMaxNumExtensions; i++) {
    uint8_t id = extensions.GetId(kExtensions[i].type);
    if (id != RtpHeaderExtensionMap::kInvalidId) {
      config->rtp_extensions.emplace_back(kExtensions[i].name, id);
    }
  }
  return std::make_unique<RtcEventVideoSendStreamConfig>(std::move(config));
}

void EventVerifier::VerifyLoggedAlrStateEvent(
    const RtcEventAlrState& original_event,
    const LoggedAlrStateEvent& logged_event) const {
  EXPECT_EQ(original_event.timestamp_ms(), logged_event.log_time_ms());
  EXPECT_EQ(original_event.in_alr(), logged_event.in_alr);
}

void EventVerifier::VerifyLoggedAudioPlayoutEvent(
    const RtcEventAudioPlayout& original_event,
    const LoggedAudioPlayoutEvent& logged_event) const {
  EXPECT_EQ(original_event.timestamp_ms(), logged_event.log_time_ms());
  EXPECT_EQ(original_event.ssrc(), logged_event.ssrc);
}

void EventVerifier::VerifyLoggedAudioNetworkAdaptationEvent(
    const RtcEventAudioNetworkAdaptation& original_event,
    const LoggedAudioNetworkAdaptationEvent& logged_event) const {
  EXPECT_EQ(original_event.timestamp_ms(), logged_event.log_time_ms());

  EXPECT_EQ(original_event.config().bitrate_bps,
            logged_event.config.bitrate_bps);
  EXPECT_EQ(original_event.config().enable_dtx, logged_event.config.enable_dtx);
  EXPECT_EQ(original_event.config().enable_fec, logged_event.config.enable_fec);
  EXPECT_EQ(original_event.config().frame_length_ms,
            logged_event.config.frame_length_ms);
  EXPECT_EQ(original_event.config().num_channels,
            logged_event.config.num_channels);

  // uplink_packet_loss_fraction
  ASSERT_EQ(original_event.config().uplink_packet_loss_fraction.has_value(),
            logged_event.config.uplink_packet_loss_fraction.has_value());
  if (original_event.config().uplink_packet_loss_fraction.has_value()) {
    const float original =
        original_event.config().uplink_packet_loss_fraction.value();
    const float logged =
        logged_event.config.uplink_packet_loss_fraction.value();
    const float uplink_packet_loss_fraction_delta = std::abs(original - logged);
    EXPECT_LE(uplink_packet_loss_fraction_delta, 0.0001f);
  }
}

void EventVerifier::VerifyLoggedBweDelayBasedUpdate(
    const RtcEventBweUpdateDelayBased& original_event,
    const LoggedBweDelayBasedUpdate& logged_event) const {
  EXPECT_EQ(original_event.timestamp_ms(), logged_event.log_time_ms());
  EXPECT_EQ(original_event.bitrate_bps(), logged_event.bitrate_bps);
  EXPECT_EQ(original_event.detector_state(), logged_event.detector_state);
}

void EventVerifier::VerifyLoggedBweLossBasedUpdate(
    const RtcEventBweUpdateLossBased& original_event,
    const LoggedBweLossBasedUpdate& logged_event) const {
  EXPECT_EQ(original_event.timestamp_ms(), logged_event.log_time_ms());
  EXPECT_EQ(original_event.bitrate_bps(), logged_event.bitrate_bps);
  EXPECT_EQ(original_event.fraction_loss(), logged_event.fraction_lost);
  EXPECT_EQ(original_event.total_packets(), logged_event.expected_packets);
}

void EventVerifier::VerifyLoggedBweProbeClusterCreatedEvent(
    const RtcEventProbeClusterCreated& original_event,
    const LoggedBweProbeClusterCreatedEvent& logged_event) const {
  EXPECT_EQ(original_event.timestamp_ms(), logged_event.log_time_ms());
  EXPECT_EQ(original_event.id(), logged_event.id);
  EXPECT_EQ(original_event.bitrate_bps(), logged_event.bitrate_bps);
  EXPECT_EQ(original_event.min_probes(), logged_event.min_packets);
  EXPECT_EQ(original_event.min_bytes(), logged_event.min_bytes);
}

void EventVerifier::VerifyLoggedBweProbeFailureEvent(
    const RtcEventProbeResultFailure& original_event,
    const LoggedBweProbeFailureEvent& logged_event) const {
  EXPECT_EQ(original_event.timestamp_ms(), logged_event.log_time_ms());
  EXPECT_EQ(original_event.id(), logged_event.id);
  EXPECT_EQ(original_event.failure_reason(), logged_event.failure_reason);
}

void EventVerifier::VerifyLoggedBweProbeSuccessEvent(
    const RtcEventProbeResultSuccess& original_event,
    const LoggedBweProbeSuccessEvent& logged_event) const {
  EXPECT_EQ(original_event.timestamp_ms(), logged_event.log_time_ms());
  EXPECT_EQ(original_event.id(), logged_event.id);
  EXPECT_EQ(original_event.bitrate_bps(), logged_event.bitrate_bps);
}

void EventVerifier::VerifyLoggedDtlsTransportState(
    const RtcEventDtlsTransportState& original_event,
    const LoggedDtlsTransportState& logged_event) const {
  EXPECT_EQ(original_event.timestamp_ms(), logged_event.log_time_ms());
  EXPECT_EQ(original_event.dtls_transport_state(),
            logged_event.dtls_transport_state);
}

void EventVerifier::VerifyLoggedDtlsWritableState(
    const RtcEventDtlsWritableState& original_event,
    const LoggedDtlsWritableState& logged_event) const {
  EXPECT_EQ(original_event.timestamp_ms(), logged_event.log_time_ms());
  EXPECT_EQ(original_event.writable(), logged_event.writable);
}

void EventVerifier::VerifyLoggedFrameDecoded(
    const RtcEventFrameDecoded& original_event,
    const LoggedFrameDecoded& logged_event) const {
  EXPECT_EQ(original_event.timestamp_ms(), logged_event.log_time_ms());
  EXPECT_EQ(original_event.ssrc(), logged_event.ssrc);
  EXPECT_EQ(original_event.render_time_ms(), logged_event.render_time_ms);
  EXPECT_EQ(original_event.width(), logged_event.width);
  EXPECT_EQ(original_event.height(), logged_event.height);
  EXPECT_EQ(original_event.codec(), logged_event.codec);
  EXPECT_EQ(original_event.qp(), logged_event.qp);
}

void EventVerifier::VerifyLoggedIceCandidatePairConfig(
    const RtcEventIceCandidatePairConfig& original_event,
    const LoggedIceCandidatePairConfig& logged_event) const {
  EXPECT_EQ(original_event.timestamp_ms(), logged_event.log_time_ms());

  EXPECT_EQ(original_event.type(), logged_event.type);
  EXPECT_EQ(original_event.candidate_pair_id(), logged_event.candidate_pair_id);
  EXPECT_EQ(original_event.candidate_pair_desc().local_candidate_type,
            logged_event.local_candidate_type);
  EXPECT_EQ(original_event.candidate_pair_desc().local_relay_protocol,
            logged_event.local_relay_protocol);
  EXPECT_EQ(original_event.candidate_pair_desc().local_network_type,
            logged_event.local_network_type);
  EXPECT_EQ(original_event.candidate_pair_desc().local_address_family,
            logged_event.local_address_family);
  EXPECT_EQ(original_event.candidate_pair_desc().remote_candidate_type,
            logged_event.remote_candidate_type);
  EXPECT_EQ(original_event.candidate_pair_desc().remote_address_family,
            logged_event.remote_address_family);
  EXPECT_EQ(original_event.candidate_pair_desc().candidate_pair_protocol,
            logged_event.candidate_pair_protocol);
}

void EventVerifier::VerifyLoggedIceCandidatePairEvent(
    const RtcEventIceCandidatePair& original_event,
    const LoggedIceCandidatePairEvent& logged_event) const {
  EXPECT_EQ(original_event.timestamp_ms(), logged_event.log_time_ms());

  EXPECT_EQ(original_event.type(), logged_event.type);
  EXPECT_EQ(original_event.candidate_pair_id(), logged_event.candidate_pair_id);
  if (encoding_type_ == RtcEventLog::EncodingType::NewFormat) {
    EXPECT_EQ(original_event.transaction_id(), logged_event.transaction_id);
  }
}

template <typename Event>
void VerifyLoggedRtpHeader(const Event& original_header,
                           const RTPHeader& logged_header) {
  // Standard RTP header.
  EXPECT_EQ(original_header.Marker(), logged_header.markerBit);
  EXPECT_EQ(original_header.PayloadType(), logged_header.payloadType);
  EXPECT_EQ(original_header.SequenceNumber(), logged_header.sequenceNumber);
  EXPECT_EQ(original_header.Timestamp(), logged_header.timestamp);
  EXPECT_EQ(original_header.Ssrc(), logged_header.ssrc);

  EXPECT_EQ(original_header.header_length(), logged_header.headerLength);

  // TransmissionOffset header extension.
  ASSERT_EQ(original_header.template HasExtension<TransmissionOffset>(),
            logged_header.extension.hasTransmissionTimeOffset);
  if (logged_header.extension.hasTransmissionTimeOffset) {
    int32_t offset;
    ASSERT_TRUE(
        original_header.template GetExtension<TransmissionOffset>(&offset));
    EXPECT_EQ(offset, logged_header.extension.transmissionTimeOffset);
  }

  // AbsoluteSendTime header extension.
  ASSERT_EQ(original_header.template HasExtension<AbsoluteSendTime>(),
            logged_header.extension.hasAbsoluteSendTime);
  if (logged_header.extension.hasAbsoluteSendTime) {
    uint32_t sendtime;
    ASSERT_TRUE(
        original_header.template GetExtension<AbsoluteSendTime>(&sendtime));
    EXPECT_EQ(sendtime, logged_header.extension.absoluteSendTime);
  }

  // TransportSequenceNumber header extension.
  ASSERT_EQ(original_header.template HasExtension<TransportSequenceNumber>(),
            logged_header.extension.hasTransportSequenceNumber);
  if (logged_header.extension.hasTransportSequenceNumber) {
    uint16_t seqnum;
    ASSERT_TRUE(original_header.template GetExtension<TransportSequenceNumber>(
        &seqnum));
    EXPECT_EQ(seqnum, logged_header.extension.transportSequenceNumber);
  }

  // AudioLevel header extension.
  ASSERT_EQ(original_header.template HasExtension<AudioLevel>(),
            logged_header.extension.hasAudioLevel);
  if (logged_header.extension.hasAudioLevel) {
    bool voice_activity;
    uint8_t audio_level;
    ASSERT_TRUE(original_header.template GetExtension<AudioLevel>(
        &voice_activity, &audio_level));
    EXPECT_EQ(voice_activity, logged_header.extension.voiceActivity);
    EXPECT_EQ(audio_level, logged_header.extension.audioLevel);
  }

  // VideoOrientation header extension.
  ASSERT_EQ(original_header.template HasExtension<VideoOrientation>(),
            logged_header.extension.hasVideoRotation);
  if (logged_header.extension.hasVideoRotation) {
    uint8_t rotation;
    ASSERT_TRUE(
        original_header.template GetExtension<VideoOrientation>(&rotation));
    EXPECT_EQ(ConvertCVOByteToVideoRotation(rotation),
              logged_header.extension.videoRotation);
  }
}

void EventVerifier::VerifyLoggedRouteChangeEvent(
    const RtcEventRouteChange& original_event,
    const LoggedRouteChangeEvent& logged_event) const {
  EXPECT_EQ(original_event.timestamp_ms(), logged_event.log_time_ms());
  EXPECT_EQ(original_event.connected(), logged_event.connected);
  EXPECT_EQ(original_event.overhead(), logged_event.overhead);
}

void EventVerifier::VerifyLoggedRemoteEstimateEvent(
    const RtcEventRemoteEstimate& original_event,
    const LoggedRemoteEstimateEvent& logged_event) const {
  EXPECT_EQ(original_event.timestamp_ms(), logged_event.log_time_ms());
  EXPECT_EQ(original_event.link_capacity_lower_,
            logged_event.link_capacity_lower);
  EXPECT_EQ(original_event.link_capacity_upper_,
            logged_event.link_capacity_upper);
}

void EventVerifier::VerifyLoggedRtpPacketIncoming(
    const RtcEventRtpPacketIncoming& original_event,
    const LoggedRtpPacketIncoming& logged_event) const {
  EXPECT_EQ(original_event.timestamp_ms(), logged_event.log_time_ms());

  EXPECT_EQ(original_event.header_length(), logged_event.rtp.header_length);

  EXPECT_EQ(original_event.packet_length(), logged_event.rtp.total_length);

  // Currently, RTC eventlog encoder-parser can only maintain padding length
  // if packet is full padding.
  EXPECT_EQ(original_event.padding_length(),
            logged_event.rtp.header.paddingLength);

  VerifyLoggedRtpHeader(original_event, logged_event.rtp.header);
}

void EventVerifier::VerifyLoggedRtpPacketOutgoing(
    const RtcEventRtpPacketOutgoing& original_event,
    const LoggedRtpPacketOutgoing& logged_event) const {
  EXPECT_EQ(original_event.timestamp_ms(), logged_event.log_time_ms());

  EXPECT_EQ(original_event.header_length(), logged_event.rtp.header_length);

  EXPECT_EQ(original_event.packet_length(), logged_event.rtp.total_length);

  // Currently, RTC eventlog encoder-parser can only maintain padding length
  // if packet is full padding.
  EXPECT_EQ(original_event.padding_length(),
            logged_event.rtp.header.paddingLength);

  // TODO(terelius): Probe cluster ID isn't parsed, used or tested. Unless
  // someone has a strong reason to keep it, it'll be removed.

  VerifyLoggedRtpHeader(original_event, logged_event.rtp.header);
}

void EventVerifier::VerifyLoggedGenericPacketSent(
    const RtcEventGenericPacketSent& original_event,
    const LoggedGenericPacketSent& logged_event) const {
  EXPECT_EQ(original_event.timestamp_ms(), logged_event.log_time_ms());
  EXPECT_EQ(original_event.packet_number(), logged_event.packet_number);
  EXPECT_EQ(original_event.overhead_length(), logged_event.overhead_length);
  EXPECT_EQ(original_event.payload_length(), logged_event.payload_length);
  EXPECT_EQ(original_event.padding_length(), logged_event.padding_length);
}

void EventVerifier::VerifyLoggedGenericPacketReceived(
    const RtcEventGenericPacketReceived& original_event,
    const LoggedGenericPacketReceived& logged_event) const {
  EXPECT_EQ(original_event.timestamp_ms(), logged_event.log_time_ms());
  EXPECT_EQ(original_event.packet_number(), logged_event.packet_number);
  EXPECT_EQ(static_cast<int>(original_event.packet_length()),
            logged_event.packet_length);
}

void EventVerifier::VerifyLoggedGenericAckReceived(
    const RtcEventGenericAckReceived& original_event,
    const LoggedGenericAckReceived& logged_event) const {
  EXPECT_EQ(original_event.timestamp_ms(), logged_event.log_time_ms());
  EXPECT_EQ(original_event.packet_number(), logged_event.packet_number);
  EXPECT_EQ(original_event.acked_packet_number(),
            logged_event.acked_packet_number);
  EXPECT_EQ(original_event.receive_acked_packet_time_ms(),
            logged_event.receive_acked_packet_time_ms);
}

void EventVerifier::VerifyLoggedRtcpPacketIncoming(
    const RtcEventRtcpPacketIncoming& original_event,
    const LoggedRtcpPacketIncoming& logged_event) const {
  EXPECT_EQ(original_event.timestamp_ms(), logged_event.log_time_ms());

  ASSERT_EQ(original_event.packet().size(), logged_event.rtcp.raw_data.size());
  EXPECT_EQ(
      memcmp(original_event.packet().data(), logged_event.rtcp.raw_data.data(),
             original_event.packet().size()),
      0);
}

void EventVerifier::VerifyLoggedRtcpPacketOutgoing(
    const RtcEventRtcpPacketOutgoing& original_event,
    const LoggedRtcpPacketOutgoing& logged_event) const {
  EXPECT_EQ(original_event.timestamp_ms(), logged_event.log_time_ms());

  ASSERT_EQ(original_event.packet().size(), logged_event.rtcp.raw_data.size());
  EXPECT_EQ(
      memcmp(original_event.packet().data(), logged_event.rtcp.raw_data.data(),
             original_event.packet().size()),
      0);
}

void EventVerifier::VerifyReportBlock(
    const rtcp::ReportBlock& original_report_block,
    const rtcp::ReportBlock& logged_report_block) {
  EXPECT_EQ(original_report_block.source_ssrc(),
            logged_report_block.source_ssrc());
  EXPECT_EQ(original_report_block.fraction_lost(),
            logged_report_block.fraction_lost());
  EXPECT_EQ(original_report_block.cumulative_lost_signed(),
            logged_report_block.cumulative_lost_signed());
  EXPECT_EQ(original_report_block.extended_high_seq_num(),
            logged_report_block.extended_high_seq_num());
  EXPECT_EQ(original_report_block.jitter(), logged_report_block.jitter());
  EXPECT_EQ(original_report_block.last_sr(), logged_report_block.last_sr());
  EXPECT_EQ(original_report_block.delay_since_last_sr(),
            logged_report_block.delay_since_last_sr());
}

void EventVerifier::VerifyLoggedSenderReport(
    int64_t log_time_ms,
    const rtcp::SenderReport& original_sr,
    const LoggedRtcpPacketSenderReport& logged_sr) {
  EXPECT_EQ(log_time_ms, logged_sr.log_time_ms());
  EXPECT_EQ(original_sr.sender_ssrc(), logged_sr.sr.sender_ssrc());
  EXPECT_EQ(original_sr.ntp(), logged_sr.sr.ntp());
  EXPECT_EQ(original_sr.rtp_timestamp(), logged_sr.sr.rtp_timestamp());
  EXPECT_EQ(original_sr.sender_packet_count(),
            logged_sr.sr.sender_packet_count());
  EXPECT_EQ(original_sr.sender_octet_count(),
            logged_sr.sr.sender_octet_count());
  ASSERT_EQ(original_sr.report_blocks().size(),
            logged_sr.sr.report_blocks().size());
  for (size_t i = 0; i < original_sr.report_blocks().size(); i++) {
    VerifyReportBlock(original_sr.report_blocks()[i],
                      logged_sr.sr.report_blocks()[i]);
  }
}

void EventVerifier::VerifyLoggedReceiverReport(
    int64_t log_time_ms,
    const rtcp::ReceiverReport& original_rr,
    const LoggedRtcpPacketReceiverReport& logged_rr) {
  EXPECT_EQ(log_time_ms, logged_rr.log_time_ms());
  EXPECT_EQ(original_rr.sender_ssrc(), logged_rr.rr.sender_ssrc());
  ASSERT_EQ(original_rr.report_blocks().size(),
            logged_rr.rr.report_blocks().size());
  for (size_t i = 0; i < original_rr.report_blocks().size(); i++) {
    VerifyReportBlock(original_rr.report_blocks()[i],
                      logged_rr.rr.report_blocks()[i]);
  }
}

void EventVerifier::VerifyLoggedExtendedReports(
    int64_t log_time_ms,
    const rtcp::ExtendedReports& original_xr,
    const LoggedRtcpPacketExtendedReports& logged_xr) {
  EXPECT_EQ(log_time_ms, logged_xr.log_time_ms());
  EXPECT_EQ(original_xr.sender_ssrc(), logged_xr.xr.sender_ssrc());

  EXPECT_EQ(original_xr.rrtr().has_value(), logged_xr.xr.rrtr().has_value());
  if (original_xr.rrtr().has_value() && logged_xr.xr.rrtr().has_value()) {
    EXPECT_EQ(original_xr.rrtr()->ntp(), logged_xr.xr.rrtr()->ntp());
  }

  const auto& original_subblocks = original_xr.dlrr().sub_blocks();
  const auto& logged_subblocks = logged_xr.xr.dlrr().sub_blocks();
  ASSERT_EQ(original_subblocks.size(), logged_subblocks.size());
  for (size_t i = 0; i < original_subblocks.size(); i++) {
    EXPECT_EQ(original_subblocks[i].ssrc, logged_subblocks[i].ssrc);
    EXPECT_EQ(original_subblocks[i].last_rr, logged_subblocks[i].last_rr);
    EXPECT_EQ(original_subblocks[i].delay_since_last_rr,
              logged_subblocks[i].delay_since_last_rr);
  }

  EXPECT_EQ(original_xr.target_bitrate().has_value(),
            logged_xr.xr.target_bitrate().has_value());
  if (original_xr.target_bitrate().has_value() &&
      logged_xr.xr.target_bitrate().has_value()) {
    const auto& original_bitrates =
        original_xr.target_bitrate()->GetTargetBitrates();
    const auto& logged_bitrates =
        logged_xr.xr.target_bitrate()->GetTargetBitrates();
    ASSERT_EQ(original_bitrates.size(), logged_bitrates.size());
    for (size_t i = 0; i < original_bitrates.size(); i++) {
      EXPECT_EQ(original_bitrates[i].spatial_layer,
                logged_bitrates[i].spatial_layer);
      EXPECT_EQ(original_bitrates[i].temporal_layer,
                logged_bitrates[i].temporal_layer);
      EXPECT_EQ(original_bitrates[i].target_bitrate_kbps,
                logged_bitrates[i].target_bitrate_kbps);
    }
  }
}

void EventVerifier::VerifyLoggedFir(int64_t log_time_ms,
                                    const rtcp::Fir& original_fir,
                                    const LoggedRtcpPacketFir& logged_fir) {
  EXPECT_EQ(log_time_ms, logged_fir.log_time_ms());
  EXPECT_EQ(original_fir.sender_ssrc(), logged_fir.fir.sender_ssrc());
  const auto& original_requests = original_fir.requests();
  const auto& logged_requests = logged_fir.fir.requests();
  ASSERT_EQ(original_requests.size(), logged_requests.size());
  for (size_t i = 0; i < original_requests.size(); i++) {
    EXPECT_EQ(original_requests[i].ssrc, logged_requests[i].ssrc);
    EXPECT_EQ(original_requests[i].seq_nr, logged_requests[i].seq_nr);
  }
}

void EventVerifier::VerifyLoggedPli(int64_t log_time_ms,
                                    const rtcp::Pli& original_pli,
                                    const LoggedRtcpPacketPli& logged_pli) {
  EXPECT_EQ(log_time_ms, logged_pli.log_time_ms());
  EXPECT_EQ(original_pli.sender_ssrc(), logged_pli.pli.sender_ssrc());
  EXPECT_EQ(original_pli.media_ssrc(), logged_pli.pli.media_ssrc());
}

void EventVerifier::VerifyLoggedBye(int64_t log_time_ms,
                                    const rtcp::Bye& original_bye,
                                    const LoggedRtcpPacketBye& logged_bye) {
  EXPECT_EQ(log_time_ms, logged_bye.log_time_ms());
  EXPECT_EQ(original_bye.sender_ssrc(), logged_bye.bye.sender_ssrc());
  EXPECT_EQ(original_bye.csrcs(), logged_bye.bye.csrcs());
  EXPECT_EQ(original_bye.reason(), logged_bye.bye.reason());
}

void EventVerifier::VerifyLoggedNack(int64_t log_time_ms,
                                     const rtcp::Nack& original_nack,
                                     const LoggedRtcpPacketNack& logged_nack) {
  EXPECT_EQ(log_time_ms, logged_nack.log_time_ms());
  EXPECT_EQ(original_nack.packet_ids(), logged_nack.nack.packet_ids());
}

void EventVerifier::VerifyLoggedTransportFeedback(
    int64_t log_time_ms,
    const rtcp::TransportFeedback& original_transport_feedback,
    const LoggedRtcpPacketTransportFeedback& logged_transport_feedback) {
  EXPECT_EQ(log_time_ms, logged_transport_feedback.log_time_ms());
  ASSERT_EQ(
      original_transport_feedback.GetReceivedPackets().size(),
      logged_transport_feedback.transport_feedback.GetReceivedPackets().size());
  for (size_t i = 0;
       i < original_transport_feedback.GetReceivedPackets().size(); i++) {
    EXPECT_EQ(
        original_transport_feedback.GetReceivedPackets()[i].sequence_number(),
        logged_transport_feedback.transport_feedback.GetReceivedPackets()[i]
            .sequence_number());
    EXPECT_EQ(
        original_transport_feedback.GetReceivedPackets()[i].delta(),
        logged_transport_feedback.transport_feedback.GetReceivedPackets()[i]
            .delta());
  }
}

void EventVerifier::VerifyLoggedRemb(int64_t log_time_ms,
                                     const rtcp::Remb& original_remb,
                                     const LoggedRtcpPacketRemb& logged_remb) {
  EXPECT_EQ(log_time_ms, logged_remb.log_time_ms());
  EXPECT_EQ(original_remb.ssrcs(), logged_remb.remb.ssrcs());
  EXPECT_EQ(original_remb.bitrate_bps(), logged_remb.remb.bitrate_bps());
}

void EventVerifier::VerifyLoggedLossNotification(
    int64_t log_time_ms,
    const rtcp::LossNotification& original_loss_notification,
    const LoggedRtcpPacketLossNotification& logged_loss_notification) {
  EXPECT_EQ(log_time_ms, logged_loss_notification.log_time_ms());
  EXPECT_EQ(original_loss_notification.last_decoded(),
            logged_loss_notification.loss_notification.last_decoded());
  EXPECT_EQ(original_loss_notification.last_received(),
            logged_loss_notification.loss_notification.last_received());
  EXPECT_EQ(original_loss_notification.decodability_flag(),
            logged_loss_notification.loss_notification.decodability_flag());
}

void EventVerifier::VerifyLoggedStartEvent(
    int64_t start_time_us,
    int64_t utc_start_time_us,
    const LoggedStartEvent& logged_event) const {
  EXPECT_EQ(start_time_us / 1000, logged_event.log_time_ms());
  if (encoding_type_ == RtcEventLog::EncodingType::NewFormat) {
    EXPECT_EQ(utc_start_time_us / 1000, logged_event.utc_start_time.ms());
  }
}

void EventVerifier::VerifyLoggedStopEvent(
    int64_t stop_time_us,
    const LoggedStopEvent& logged_event) const {
  EXPECT_EQ(stop_time_us / 1000, logged_event.log_time_ms());
}

void VerifyLoggedStreamConfig(const rtclog::StreamConfig& original_config,
                              const rtclog::StreamConfig& logged_config) {
  EXPECT_EQ(original_config.local_ssrc, logged_config.local_ssrc);
  EXPECT_EQ(original_config.remote_ssrc, logged_config.remote_ssrc);
  EXPECT_EQ(original_config.rtx_ssrc, logged_config.rtx_ssrc);

  EXPECT_EQ(original_config.rtp_extensions.size(),
            logged_config.rtp_extensions.size());
  size_t recognized_extensions = 0;
  for (size_t i = 0; i < kMaxNumExtensions; i++) {
    auto original_id =
        GetExtensionId(original_config.rtp_extensions, kExtensions[i].name);
    auto logged_id =
        GetExtensionId(logged_config.rtp_extensions, kExtensions[i].name);
    EXPECT_EQ(original_id, logged_id)
        << "IDs for " << kExtensions[i].name << " don't match. Original ID "
        << original_id.value_or(-1) << ". Parsed ID " << logged_id.value_or(-1)
        << ".";
    if (original_id) {
      recognized_extensions++;
    }
  }
  EXPECT_EQ(recognized_extensions, original_config.rtp_extensions.size());
}

void EventVerifier::VerifyLoggedAudioRecvConfig(
    const RtcEventAudioReceiveStreamConfig& original_event,
    const LoggedAudioRecvConfig& logged_event) const {
  EXPECT_EQ(original_event.timestamp_ms(), logged_event.log_time_ms());
  VerifyLoggedStreamConfig(original_event.config(), logged_event.config);
}

void EventVerifier::VerifyLoggedAudioSendConfig(
    const RtcEventAudioSendStreamConfig& original_event,
    const LoggedAudioSendConfig& logged_event) const {
  EXPECT_EQ(original_event.timestamp_ms(), logged_event.log_time_ms());
  VerifyLoggedStreamConfig(original_event.config(), logged_event.config);
}

void EventVerifier::VerifyLoggedVideoRecvConfig(
    const RtcEventVideoReceiveStreamConfig& original_event,
    const LoggedVideoRecvConfig& logged_event) const {
  EXPECT_EQ(original_event.timestamp_ms(), logged_event.log_time_ms());
  VerifyLoggedStreamConfig(original_event.config(), logged_event.config);
}

void EventVerifier::VerifyLoggedVideoSendConfig(
    const RtcEventVideoSendStreamConfig& original_event,
    const LoggedVideoSendConfig& logged_event) const {
  EXPECT_EQ(original_event.timestamp_ms(), logged_event.log_time_ms());
  VerifyLoggedStreamConfig(original_event.config(), logged_event.config);
}

}  // namespace test
}  // namespace webrtc
