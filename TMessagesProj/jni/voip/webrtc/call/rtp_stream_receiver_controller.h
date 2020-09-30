/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef CALL_RTP_STREAM_RECEIVER_CONTROLLER_H_
#define CALL_RTP_STREAM_RECEIVER_CONTROLLER_H_

#include <memory>

#include "call/rtp_demuxer.h"
#include "call/rtp_stream_receiver_controller_interface.h"
#include "rtc_base/deprecated/recursive_critical_section.h"

namespace webrtc {

class RtpPacketReceived;

// This class represents the RTP receive parsing and demuxing, for a
// single RTP session.
// TODO(nisse): Add RTCP processing, we should aim to terminate RTCP
// and not leave any RTCP processing to individual receive streams.
// TODO(nisse): Extract per-packet processing, including parsing and
// demuxing, into a separate class.
class RtpStreamReceiverController
    : public RtpStreamReceiverControllerInterface {
 public:
  RtpStreamReceiverController();
  ~RtpStreamReceiverController() override;

  // Implements RtpStreamReceiverControllerInterface.
  std::unique_ptr<RtpStreamReceiverInterface> CreateReceiver(
      uint32_t ssrc,
      RtpPacketSinkInterface* sink) override;

  // Thread-safe wrappers for the corresponding RtpDemuxer methods.
  bool AddSink(uint32_t ssrc, RtpPacketSinkInterface* sink) override;
  size_t RemoveSink(const RtpPacketSinkInterface* sink) override;

  // TODO(nisse): Not yet responsible for parsing.
  bool OnRtpPacket(const RtpPacketReceived& packet);

 private:
  class Receiver : public RtpStreamReceiverInterface {
   public:
    Receiver(RtpStreamReceiverController* controller,
             uint32_t ssrc,
             RtpPacketSinkInterface* sink);

    ~Receiver() override;

   private:
    RtpStreamReceiverController* const controller_;
    RtpPacketSinkInterface* const sink_;
  };

  // TODO(nisse): Move to a TaskQueue for synchronization. When used
  // by Call, we expect construction and all methods but OnRtpPacket
  // to be called on the same thread, and OnRtpPacket to be called
  // by a single, but possibly distinct, thread. But applications not
  // using Call may have use threads differently.
  rtc::RecursiveCriticalSection lock_;
  RtpDemuxer demuxer_ RTC_GUARDED_BY(&lock_);
};

}  // namespace webrtc

#endif  // CALL_RTP_STREAM_RECEIVER_CONTROLLER_H_
