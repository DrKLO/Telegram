/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/deprecated/packet.h"

#include "api/rtp_headers.h"

namespace webrtc {

VCMPacket::VCMPacket()
    : payloadType(0),
      timestamp(0),
      ntp_time_ms_(0),
      seqNum(0),
      dataPtr(NULL),
      sizeBytes(0),
      markerBit(false),
      timesNacked(-1),
      completeNALU(kNaluUnset),
      insertStartCode(false),
      video_header() {
}

VCMPacket::VCMPacket(const uint8_t* ptr,
                     size_t size,
                     const RTPHeader& rtp_header,
                     const RTPVideoHeader& videoHeader,
                     int64_t ntp_time_ms,
                     Timestamp receive_time)
    : payloadType(rtp_header.payloadType),
      timestamp(rtp_header.timestamp),
      ntp_time_ms_(ntp_time_ms),
      seqNum(rtp_header.sequenceNumber),
      dataPtr(ptr),
      sizeBytes(size),
      markerBit(rtp_header.markerBit),
      timesNacked(-1),
      completeNALU(kNaluIncomplete),
      insertStartCode(videoHeader.codec == kVideoCodecH264 &&
                      videoHeader.is_first_packet_in_frame),
      video_header(videoHeader),
      packet_info(rtp_header, receive_time) {
  if (is_first_packet_in_frame() && markerBit) {
    completeNALU = kNaluComplete;
  } else if (is_first_packet_in_frame()) {
    completeNALU = kNaluStart;
  } else if (markerBit) {
    completeNALU = kNaluEnd;
  } else {
    completeNALU = kNaluIncomplete;
  }

  // Playout decisions are made entirely based on first packet in a frame.
  if (!is_first_packet_in_frame()) {
    video_header.playout_delay = absl::nullopt;
  }
}

VCMPacket::~VCMPacket() = default;

}  // namespace webrtc
