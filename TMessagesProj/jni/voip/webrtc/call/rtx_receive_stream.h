/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef CALL_RTX_RECEIVE_STREAM_H_
#define CALL_RTX_RECEIVE_STREAM_H_

#include <cstdint>
#include <map>

#include "call/rtp_packet_sink_interface.h"

namespace webrtc {

class ReceiveStatistics;

// This class is responsible for RTX decapsulation. The resulting media packets
// are passed on to a sink representing the associated media stream.
class RtxReceiveStream : public RtpPacketSinkInterface {
 public:
  RtxReceiveStream(RtpPacketSinkInterface* media_sink,
                   std::map<int, int> associated_payload_types,
                   uint32_t media_ssrc,
                   // TODO(nisse): Delete this argument, and
                   // corresponding member variable, by moving the
                   // responsibility for rtcp feedback to
                   // RtpStreamReceiverController.
                   ReceiveStatistics* rtp_receive_statistics = nullptr);
  ~RtxReceiveStream() override;
  // RtpPacketSinkInterface.
  void OnRtpPacket(const RtpPacketReceived& packet) override;

 private:
  RtpPacketSinkInterface* const media_sink_;
  // Map from rtx payload type -> media payload type.
  const std::map<int, int> associated_payload_types_;
  // TODO(nisse): Ultimately, the media receive stream shouldn't care about the
  // ssrc, and we should delete this.
  const uint32_t media_ssrc_;
  ReceiveStatistics* const rtp_receive_statistics_;
};

}  // namespace webrtc

#endif  // CALL_RTX_RECEIVE_STREAM_H_
