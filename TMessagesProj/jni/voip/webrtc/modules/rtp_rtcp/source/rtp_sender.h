/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_RTP_RTCP_SOURCE_RTP_SENDER_H_
#define MODULES_RTP_RTCP_SOURCE_RTP_SENDER_H_

#include <map>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/array_view.h"
#include "api/call/transport.h"
#include "api/transport/webrtc_key_value_config.h"
#include "modules/rtp_rtcp/include/flexfec_sender.h"
#include "modules/rtp_rtcp/include/rtp_header_extension_map.h"
#include "modules/rtp_rtcp/include/rtp_packet_sender.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "modules/rtp_rtcp/source/rtp_packet_history.h"
#include "modules/rtp_rtcp/source/rtp_rtcp_config.h"
#include "modules/rtp_rtcp/source/rtp_rtcp_interface.h"
#include "rtc_base/constructor_magic.h"
#include "rtc_base/deprecation.h"
#include "rtc_base/random.h"
#include "rtc_base/rate_statistics.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/thread_annotations.h"

namespace webrtc {

class FrameEncryptorInterface;
class RateLimiter;
class RtcEventLog;
class RtpPacketToSend;

class RTPSender {
 public:
  RTPSender(const RtpRtcpInterface::Configuration& config,
            RtpPacketHistory* packet_history,
            RtpPacketSender* packet_sender);

  ~RTPSender();

  void SetSendingMediaStatus(bool enabled) RTC_LOCKS_EXCLUDED(send_mutex_);
  bool SendingMedia() const RTC_LOCKS_EXCLUDED(send_mutex_);
  bool IsAudioConfigured() const RTC_LOCKS_EXCLUDED(send_mutex_);

  uint32_t TimestampOffset() const RTC_LOCKS_EXCLUDED(send_mutex_);
  void SetTimestampOffset(uint32_t timestamp) RTC_LOCKS_EXCLUDED(send_mutex_);

  void SetRid(const std::string& rid) RTC_LOCKS_EXCLUDED(send_mutex_);

  void SetMid(const std::string& mid) RTC_LOCKS_EXCLUDED(send_mutex_);

  uint16_t SequenceNumber() const RTC_LOCKS_EXCLUDED(send_mutex_);
  void SetSequenceNumber(uint16_t seq) RTC_LOCKS_EXCLUDED(send_mutex_);

  void SetCsrcs(const std::vector<uint32_t>& csrcs)
      RTC_LOCKS_EXCLUDED(send_mutex_);

  void SetMaxRtpPacketSize(size_t max_packet_size)
      RTC_LOCKS_EXCLUDED(send_mutex_);

  void SetExtmapAllowMixed(bool extmap_allow_mixed)
      RTC_LOCKS_EXCLUDED(send_mutex_);

  // RTP header extension
  int32_t RegisterRtpHeaderExtension(RTPExtensionType type, uint8_t id)
      RTC_LOCKS_EXCLUDED(send_mutex_);
  bool RegisterRtpHeaderExtension(absl::string_view uri, int id)
      RTC_LOCKS_EXCLUDED(send_mutex_);
  bool IsRtpHeaderExtensionRegistered(RTPExtensionType type) const
      RTC_LOCKS_EXCLUDED(send_mutex_);
  int32_t DeregisterRtpHeaderExtension(RTPExtensionType type)
      RTC_LOCKS_EXCLUDED(send_mutex_);
  void DeregisterRtpHeaderExtension(absl::string_view uri)
      RTC_LOCKS_EXCLUDED(send_mutex_);

  bool SupportsPadding() const RTC_LOCKS_EXCLUDED(send_mutex_);
  bool SupportsRtxPayloadPadding() const RTC_LOCKS_EXCLUDED(send_mutex_);

  std::vector<std::unique_ptr<RtpPacketToSend>> GeneratePadding(
      size_t target_size_bytes,
      bool media_has_been_sent) RTC_LOCKS_EXCLUDED(send_mutex_);

  // NACK.
  void OnReceivedNack(const std::vector<uint16_t>& nack_sequence_numbers,
                      int64_t avg_rtt) RTC_LOCKS_EXCLUDED(send_mutex_);

  int32_t ReSendPacket(uint16_t packet_id) RTC_LOCKS_EXCLUDED(send_mutex_);

  // ACK.
  void OnReceivedAckOnSsrc(int64_t extended_highest_sequence_number)
      RTC_LOCKS_EXCLUDED(send_mutex_);
  void OnReceivedAckOnRtxSsrc(int64_t extended_highest_sequence_number)
      RTC_LOCKS_EXCLUDED(send_mutex_);

  // RTX.
  void SetRtxStatus(int mode) RTC_LOCKS_EXCLUDED(send_mutex_);
  int RtxStatus() const RTC_LOCKS_EXCLUDED(send_mutex_);
  absl::optional<uint32_t> RtxSsrc() const RTC_LOCKS_EXCLUDED(send_mutex_) {
    return rtx_ssrc_;
  }

  void SetRtxPayloadType(int payload_type, int associated_payload_type)
      RTC_LOCKS_EXCLUDED(send_mutex_);

  // Size info for header extensions used by FEC packets.
  static rtc::ArrayView<const RtpExtensionSize> FecExtensionSizes()
      RTC_LOCKS_EXCLUDED(send_mutex_);

  // Size info for header extensions used by video packets.
  static rtc::ArrayView<const RtpExtensionSize> VideoExtensionSizes()
      RTC_LOCKS_EXCLUDED(send_mutex_);

  // Size info for header extensions used by audio packets.
  static rtc::ArrayView<const RtpExtensionSize> AudioExtensionSizes()
      RTC_LOCKS_EXCLUDED(send_mutex_);

  // Create empty packet, fills ssrc, csrcs and reserve place for header
  // extensions RtpSender updates before sending.
  std::unique_ptr<RtpPacketToSend> AllocatePacket() const
      RTC_LOCKS_EXCLUDED(send_mutex_);
  // Allocate sequence number for provided packet.
  // Save packet's fields to generate padding that doesn't break media stream.
  // Return false if sending was turned off.
  bool AssignSequenceNumber(RtpPacketToSend* packet)
      RTC_LOCKS_EXCLUDED(send_mutex_);
  // Maximum header overhead per fec/padding packet.
  size_t FecOrPaddingPacketMaxRtpHeaderLength() const
      RTC_LOCKS_EXCLUDED(send_mutex_);
  // Expected header overhead per media packet.
  size_t ExpectedPerPacketOverhead() const RTC_LOCKS_EXCLUDED(send_mutex_);
  uint16_t AllocateSequenceNumber(uint16_t packets_to_send)
      RTC_LOCKS_EXCLUDED(send_mutex_);
  // Including RTP headers.
  size_t MaxRtpPacketSize() const RTC_LOCKS_EXCLUDED(send_mutex_);

  uint32_t SSRC() const RTC_LOCKS_EXCLUDED(send_mutex_) { return ssrc_; }

  absl::optional<uint32_t> FlexfecSsrc() const RTC_LOCKS_EXCLUDED(send_mutex_) {
    return flexfec_ssrc_;
  }

  // Sends packet to |transport_| or to the pacer, depending on configuration.
  // TODO(bugs.webrtc.org/XXX): Remove in favor of EnqueuePackets().
  bool SendToNetwork(std::unique_ptr<RtpPacketToSend> packet)
      RTC_LOCKS_EXCLUDED(send_mutex_);

  // Pass a set of packets to RtpPacketSender instance, for paced or immediate
  // sending to the network.
  void EnqueuePackets(std::vector<std::unique_ptr<RtpPacketToSend>> packets)
      RTC_LOCKS_EXCLUDED(send_mutex_);

  void SetRtpState(const RtpState& rtp_state) RTC_LOCKS_EXCLUDED(send_mutex_);
  RtpState GetRtpState() const RTC_LOCKS_EXCLUDED(send_mutex_);
  void SetRtxRtpState(const RtpState& rtp_state)
      RTC_LOCKS_EXCLUDED(send_mutex_);
  RtpState GetRtxRtpState() const RTC_LOCKS_EXCLUDED(send_mutex_);

  int64_t LastTimestampTimeMs() const RTC_LOCKS_EXCLUDED(send_mutex_);

 private:
  std::unique_ptr<RtpPacketToSend> BuildRtxPacket(
      const RtpPacketToSend& packet);

  bool IsFecPacket(const RtpPacketToSend& packet) const;

  void UpdateHeaderSizes() RTC_EXCLUSIVE_LOCKS_REQUIRED(send_mutex_);

  Clock* const clock_;
  Random random_ RTC_GUARDED_BY(send_mutex_);

  const bool audio_configured_;

  const uint32_t ssrc_;
  const absl::optional<uint32_t> rtx_ssrc_;
  const absl::optional<uint32_t> flexfec_ssrc_;
  // Limits GeneratePadding() outcome to <=
  //  |max_padding_size_factor_| * |target_size_bytes|
  const double max_padding_size_factor_;

  RtpPacketHistory* const packet_history_;
  RtpPacketSender* const paced_sender_;

  mutable Mutex send_mutex_;

  bool sending_media_ RTC_GUARDED_BY(send_mutex_);
  size_t max_packet_size_;

  int8_t last_payload_type_ RTC_GUARDED_BY(send_mutex_);

  RtpHeaderExtensionMap rtp_header_extension_map_ RTC_GUARDED_BY(send_mutex_);
  size_t max_media_packet_header_ RTC_GUARDED_BY(send_mutex_);
  size_t max_padding_fec_packet_header_ RTC_GUARDED_BY(send_mutex_);

  // RTP variables
  uint32_t timestamp_offset_ RTC_GUARDED_BY(send_mutex_);
  bool sequence_number_forced_ RTC_GUARDED_BY(send_mutex_);
  uint16_t sequence_number_ RTC_GUARDED_BY(send_mutex_);
  uint16_t sequence_number_rtx_ RTC_GUARDED_BY(send_mutex_);
  // RID value to send in the RID or RepairedRID header extension.
  std::string rid_ RTC_GUARDED_BY(send_mutex_);
  // MID value to send in the MID header extension.
  std::string mid_ RTC_GUARDED_BY(send_mutex_);
  // Should we send MID/RID even when ACKed? (see below).
  const bool always_send_mid_and_rid_;
  // Track if any ACK has been received on the SSRC and RTX SSRC to indicate
  // when to stop sending the MID and RID header extensions.
  bool ssrc_has_acked_ RTC_GUARDED_BY(send_mutex_);
  bool rtx_ssrc_has_acked_ RTC_GUARDED_BY(send_mutex_);
  uint32_t last_rtp_timestamp_ RTC_GUARDED_BY(send_mutex_);
  int64_t capture_time_ms_ RTC_GUARDED_BY(send_mutex_);
  int64_t last_timestamp_time_ms_ RTC_GUARDED_BY(send_mutex_);
  bool last_packet_marker_bit_ RTC_GUARDED_BY(send_mutex_);
  std::vector<uint32_t> csrcs_ RTC_GUARDED_BY(send_mutex_);
  int rtx_ RTC_GUARDED_BY(send_mutex_);
  // Mapping rtx_payload_type_map_[associated] = rtx.
  std::map<int8_t, int8_t> rtx_payload_type_map_ RTC_GUARDED_BY(send_mutex_);
  bool supports_bwe_extension_ RTC_GUARDED_BY(send_mutex_);

  RateLimiter* const retransmission_rate_limiter_;

  RTC_DISALLOW_IMPLICIT_CONSTRUCTORS(RTPSender);
};

}  // namespace webrtc

#endif  // MODULES_RTP_RTCP_SOURCE_RTP_SENDER_H_
