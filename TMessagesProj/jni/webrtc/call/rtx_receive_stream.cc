/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "call/rtx_receive_stream.h"

#include <string.h>

#include <utility>

#include "api/array_view.h"
#include "modules/rtp_rtcp/include/receive_statistics.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "modules/rtp_rtcp/source/rtp_packet_received.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {

RtxReceiveStream::RtxReceiveStream(
    RtpPacketSinkInterface* media_sink,
    std::map<int, int> associated_payload_types,
    uint32_t media_ssrc,
    ReceiveStatistics* rtp_receive_statistics /* = nullptr */)
    : media_sink_(media_sink),
      associated_payload_types_(std::move(associated_payload_types)),
      media_ssrc_(media_ssrc),
      rtp_receive_statistics_(rtp_receive_statistics) {
  if (associated_payload_types_.empty()) {
    RTC_LOG(LS_WARNING)
        << "RtxReceiveStream created with empty payload type mapping.";
  }
}

RtxReceiveStream::~RtxReceiveStream() = default;

void RtxReceiveStream::OnRtpPacket(const RtpPacketReceived& rtx_packet) {
  if (rtp_receive_statistics_) {
    rtp_receive_statistics_->OnRtpPacket(rtx_packet);
  }
  rtc::ArrayView<const uint8_t> payload = rtx_packet.payload();

  if (payload.size() < kRtxHeaderSize) {
    return;
  }

  auto it = associated_payload_types_.find(rtx_packet.PayloadType());
  if (it == associated_payload_types_.end()) {
    RTC_LOG(LS_VERBOSE) << "Unknown payload type "
                        << static_cast<int>(rtx_packet.PayloadType())
                        << " on rtx ssrc " << rtx_packet.Ssrc();
    return;
  }
  RtpPacketReceived media_packet;
  media_packet.CopyHeaderFrom(rtx_packet);

  media_packet.SetSsrc(media_ssrc_);
  media_packet.SetSequenceNumber((payload[0] << 8) + payload[1]);
  media_packet.SetPayloadType(it->second);
  media_packet.set_recovered(true);
  media_packet.set_arrival_time_ms(rtx_packet.arrival_time_ms());

  // Skip the RTX header.
  rtc::ArrayView<const uint8_t> rtx_payload = payload.subview(kRtxHeaderSize);

  uint8_t* media_payload = media_packet.AllocatePayload(rtx_payload.size());
  RTC_DCHECK(media_payload != nullptr);

  memcpy(media_payload, rtx_payload.data(), rtx_payload.size());

  media_sink_->OnRtpPacket(media_packet);
}

}  // namespace webrtc
