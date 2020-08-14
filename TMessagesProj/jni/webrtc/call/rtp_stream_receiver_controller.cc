/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "call/rtp_stream_receiver_controller.h"

#include <memory>

#include "rtc_base/logging.h"

namespace webrtc {

RtpStreamReceiverController::Receiver::Receiver(
    RtpStreamReceiverController* controller,
    uint32_t ssrc,
    RtpPacketSinkInterface* sink)
    : controller_(controller), sink_(sink) {
  const bool sink_added = controller_->AddSink(ssrc, sink_);
  if (!sink_added) {
    RTC_LOG(LS_ERROR)
        << "RtpStreamReceiverController::Receiver::Receiver: Sink "
           "could not be added for SSRC="
        << ssrc << ".";
  }
}

RtpStreamReceiverController::Receiver::~Receiver() {
  // Don't require return value > 0, since for RTX we currently may
  // have multiple Receiver objects with the same sink.
  // TODO(nisse): Consider adding a DCHECK when RtxReceiveStream is wired up.
  controller_->RemoveSink(sink_);
}

RtpStreamReceiverController::RtpStreamReceiverController() {
  // At this level the demuxer is only configured to demux by SSRC, so don't
  // worry about MIDs (MIDs are handled by upper layers).
  demuxer_.set_use_mid(false);
}

RtpStreamReceiverController::~RtpStreamReceiverController() = default;

std::unique_ptr<RtpStreamReceiverInterface>
RtpStreamReceiverController::CreateReceiver(uint32_t ssrc,
                                            RtpPacketSinkInterface* sink) {
  return std::make_unique<Receiver>(this, ssrc, sink);
}

bool RtpStreamReceiverController::OnRtpPacket(const RtpPacketReceived& packet) {
  rtc::CritScope cs(&lock_);
  return demuxer_.OnRtpPacket(packet);
}

bool RtpStreamReceiverController::AddSink(uint32_t ssrc,
                                          RtpPacketSinkInterface* sink) {
  rtc::CritScope cs(&lock_);
  return demuxer_.AddSink(ssrc, sink);
}

size_t RtpStreamReceiverController::RemoveSink(
    const RtpPacketSinkInterface* sink) {
  rtc::CritScope cs(&lock_);
  return demuxer_.RemoveSink(sink);
}

}  // namespace webrtc
