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
  // This may fail, if corresponding AddSink in the constructor failed.
  controller_->RemoveSink(sink_);
}

RtpStreamReceiverController::RtpStreamReceiverController() {}

RtpStreamReceiverController::~RtpStreamReceiverController() = default;

std::unique_ptr<RtpStreamReceiverInterface>
RtpStreamReceiverController::CreateReceiver(uint32_t ssrc,
                                            RtpPacketSinkInterface* sink) {
  return std::make_unique<Receiver>(this, ssrc, sink);
}

bool RtpStreamReceiverController::OnRtpPacket(const RtpPacketReceived& packet) {
  RTC_DCHECK_RUN_ON(&demuxer_sequence_);
  return demuxer_.OnRtpPacket(packet);
}

void RtpStreamReceiverController::OnRecoveredPacket(
    const RtpPacketReceived& packet) {
  RTC_DCHECK_RUN_ON(&demuxer_sequence_);
  demuxer_.OnRtpPacket(packet);
}

bool RtpStreamReceiverController::AddSink(uint32_t ssrc,
                                          RtpPacketSinkInterface* sink) {
  RTC_DCHECK_RUN_ON(&demuxer_sequence_);
  return demuxer_.AddSink(ssrc, sink);
}

bool RtpStreamReceiverController::RemoveSink(
    const RtpPacketSinkInterface* sink) {
  RTC_DCHECK_RUN_ON(&demuxer_sequence_);
  return demuxer_.RemoveSink(sink);
}

}  // namespace webrtc
