/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_RTP_RTCP_MOCKS_MOCK_RTP_RTCP_H_
#define MODULES_RTP_RTCP_MOCKS_MOCK_RTP_RTCP_H_

#include <memory>
#include <set>
#include <string>
#include <utility>
#include <vector>

#include "absl/types/optional.h"
#include "api/video/video_bitrate_allocation.h"
#include "modules/include/module.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "modules/rtp_rtcp/source/rtp_packet_to_send.h"
#include "modules/rtp_rtcp/source/rtp_rtcp_interface.h"
#include "test/gmock.h"

namespace webrtc {

class MockRtpRtcpInterface : public RtpRtcpInterface {
 public:
  MOCK_METHOD(void,
              IncomingRtcpPacket,
              (const uint8_t* incoming_packet, size_t packet_length),
              (override));
  MOCK_METHOD(void, SetRemoteSSRC, (uint32_t ssrc), (override));
  MOCK_METHOD(void, SetMaxRtpPacketSize, (size_t size), (override));
  MOCK_METHOD(size_t, MaxRtpPacketSize, (), (const, override));
  MOCK_METHOD(void,
              RegisterSendPayloadFrequency,
              (int payload_type, int frequency),
              (override));
  MOCK_METHOD(int32_t,
              DeRegisterSendPayload,
              (int8_t payload_type),
              (override));
  MOCK_METHOD(void, SetExtmapAllowMixed, (bool extmap_allow_mixed), (override));
  MOCK_METHOD(void,
              RegisterRtpHeaderExtension,
              (absl::string_view uri, int id),
              (override));
  MOCK_METHOD(int32_t,
              DeregisterSendRtpHeaderExtension,
              (RTPExtensionType type),
              (override));
  MOCK_METHOD(void,
              DeregisterSendRtpHeaderExtension,
              (absl::string_view uri),
              (override));
  MOCK_METHOD(bool, SupportsPadding, (), (const, override));
  MOCK_METHOD(bool, SupportsRtxPayloadPadding, (), (const, override));
  MOCK_METHOD(uint32_t, StartTimestamp, (), (const, override));
  MOCK_METHOD(void, SetStartTimestamp, (uint32_t timestamp), (override));
  MOCK_METHOD(uint16_t, SequenceNumber, (), (const, override));
  MOCK_METHOD(void, SetSequenceNumber, (uint16_t seq), (override));
  MOCK_METHOD(void, SetRtpState, (const RtpState& rtp_state), (override));
  MOCK_METHOD(void, SetRtxState, (const RtpState& rtp_state), (override));
  MOCK_METHOD(RtpState, GetRtpState, (), (const, override));
  MOCK_METHOD(RtpState, GetRtxState, (), (const, override));
  MOCK_METHOD(uint32_t, SSRC, (), (const, override));
  MOCK_METHOD(void, SetRid, (const std::string& rid), (override));
  MOCK_METHOD(void, SetMid, (const std::string& mid), (override));
  MOCK_METHOD(void, SetCsrcs, (const std::vector<uint32_t>& csrcs), (override));
  MOCK_METHOD(void, SetRtxSendStatus, (int modes), (override));
  MOCK_METHOD(int, RtxSendStatus, (), (const, override));
  MOCK_METHOD(absl::optional<uint32_t>, RtxSsrc, (), (const, override));
  MOCK_METHOD(void, SetRtxSendPayloadType, (int, int), (override));
  MOCK_METHOD(absl::optional<uint32_t>, FlexfecSsrc, (), (const, override));
  MOCK_METHOD(int32_t, SetSendingStatus, (bool sending), (override));
  MOCK_METHOD(bool, Sending, (), (const, override));
  MOCK_METHOD(void, SetSendingMediaStatus, (bool sending), (override));
  MOCK_METHOD(bool, SendingMedia, (), (const, override));
  MOCK_METHOD(bool, IsAudioConfigured, (), (const, override));
  MOCK_METHOD(void, SetAsPartOfAllocation, (bool), (override));
  MOCK_METHOD(void,
              BitrateSent,
              (uint32_t * total_rate,
               uint32_t* video_rate,
               uint32_t* fec_rate,
               uint32_t* nack_rate),
              (const, override));
  MOCK_METHOD(RtpSendRates, GetSendRates, (), (const, override));
  MOCK_METHOD(bool,
              OnSendingRtpFrame,
              (uint32_t, int64_t, int, bool),
              (override));
  MOCK_METHOD(bool,
              TrySendPacket,
              (RtpPacketToSend * packet, const PacedPacketInfo& pacing_info),
              (override));
  MOCK_METHOD(void,
              SetFecProtectionParams,
              (const FecProtectionParams& delta_params,
               const FecProtectionParams& key_params),
              (override));
  MOCK_METHOD(std::vector<std::unique_ptr<RtpPacketToSend>>,
              FetchFecPackets,
              (),
              (override));
  MOCK_METHOD(void,
              OnPacketsAcknowledged,
              (rtc::ArrayView<const uint16_t>),
              (override));
  MOCK_METHOD(std::vector<std::unique_ptr<RtpPacketToSend>>,
              GeneratePadding,
              (size_t target_size_bytes),
              (override));
  MOCK_METHOD(std::vector<RtpSequenceNumberMap::Info>,
              GetSentRtpPacketInfos,
              (rtc::ArrayView<const uint16_t> sequence_numbers),
              (const, override));
  MOCK_METHOD(size_t, ExpectedPerPacketOverhead, (), (const, override));
  MOCK_METHOD(RtcpMode, RTCP, (), (const, override));
  MOCK_METHOD(void, SetRTCPStatus, (RtcpMode method), (override));
  MOCK_METHOD(int32_t,
              SetCNAME,
              (const char cname[RTCP_CNAME_SIZE]),
              (override));
  MOCK_METHOD(int32_t,
              RemoteNTP,
              (uint32_t * received_ntp_secs,
               uint32_t* received_ntp_frac,
               uint32_t* rtcp_arrival_time_secs,
               uint32_t* rtcp_arrival_time_frac,
               uint32_t* rtcp_timestamp),
              (const, override));
  MOCK_METHOD(int32_t,
              RTT,
              (uint32_t remote_ssrc,
               int64_t* rtt,
               int64_t* avg_rtt,
               int64_t* min_rtt,
               int64_t* max_rtt),
              (const, override));
  MOCK_METHOD(int64_t, ExpectedRetransmissionTimeMs, (), (const, override));
  MOCK_METHOD(int32_t, SendRTCP, (RTCPPacketType packet_type), (override));
  MOCK_METHOD(void,
              GetSendStreamDataCounters,
              (StreamDataCounters*, StreamDataCounters*),
              (const, override));
  MOCK_METHOD(int32_t,
              RemoteRTCPStat,
              (std::vector<RTCPReportBlock> * receive_blocks),
              (const, override));
  MOCK_METHOD(std::vector<ReportBlockData>,
              GetLatestReportBlockData,
              (),
              (const, override));
  MOCK_METHOD(void, SetRtcpXrRrtrStatus, (bool enable), (override));
  MOCK_METHOD(bool, RtcpXrRrtrStatus, (), (const, override));
  MOCK_METHOD(void,
              SetRemb,
              (int64_t bitrate, std::vector<uint32_t> ssrcs),
              (override));
  MOCK_METHOD(void, UnsetRemb, (), (override));
  MOCK_METHOD(int32_t,
              SendNACK,
              (const uint16_t* nack_list, uint16_t size),
              (override));
  MOCK_METHOD(void,
              SendNack,
              (const std::vector<uint16_t>& sequence_numbers),
              (override));
  MOCK_METHOD(void,
              SetStorePacketsStatus,
              (bool enable, uint16_t number_to_store),
              (override));
  MOCK_METHOD(bool, StorePackets, (), (const, override));
  MOCK_METHOD(void,
              SendCombinedRtcpPacket,
              (std::vector<std::unique_ptr<rtcp::RtcpPacket>> rtcp_packets),
              (override));
  MOCK_METHOD(int32_t,
              SendLossNotification,
              (uint16_t last_decoded_seq_num,
               uint16_t last_received_seq_num,
               bool decodability_flag,
               bool buffering_allowed),
              (override));
  MOCK_METHOD(void,
              SetVideoBitrateAllocation,
              (const VideoBitrateAllocation&),
              (override));
  MOCK_METHOD(RTPSender*, RtpSender, (), (override));
  MOCK_METHOD(const RTPSender*, RtpSender, (), (const, override));
};

}  // namespace webrtc

#endif  // MODULES_RTP_RTCP_MOCKS_MOCK_RTP_RTCP_H_
