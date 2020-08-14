/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef CALL_FLEXFEC_RECEIVE_STREAM_IMPL_H_
#define CALL_FLEXFEC_RECEIVE_STREAM_IMPL_H_

#include <memory>

#include "call/flexfec_receive_stream.h"
#include "call/rtp_packet_sink_interface.h"
#include "modules/rtp_rtcp/source/rtp_rtcp_impl2.h"
#include "system_wrappers/include/clock.h"

namespace webrtc {

class FlexfecReceiver;
class ProcessThread;
class ReceiveStatistics;
class RecoveredPacketReceiver;
class RtcpRttStats;
class RtpPacketReceived;
class RtpRtcp;
class RtpStreamReceiverControllerInterface;
class RtpStreamReceiverInterface;

class FlexfecReceiveStreamImpl : public FlexfecReceiveStream {
 public:
  FlexfecReceiveStreamImpl(
      Clock* clock,
      RtpStreamReceiverControllerInterface* receiver_controller,
      const Config& config,
      RecoveredPacketReceiver* recovered_packet_receiver,
      RtcpRttStats* rtt_stats,
      ProcessThread* process_thread);
  ~FlexfecReceiveStreamImpl() override;

  // RtpPacketSinkInterface.
  void OnRtpPacket(const RtpPacketReceived& packet) override;

  Stats GetStats() const override;
  const Config& GetConfig() const override;

 private:
  // Config.
  const Config config_;

  // Erasure code interfacing.
  const std::unique_ptr<FlexfecReceiver> receiver_;

  // RTCP reporting.
  const std::unique_ptr<ReceiveStatistics> rtp_receive_statistics_;
  const std::unique_ptr<ModuleRtpRtcpImpl2> rtp_rtcp_;
  ProcessThread* process_thread_;

  std::unique_ptr<RtpStreamReceiverInterface> rtp_stream_receiver_;
};

}  // namespace webrtc

#endif  // CALL_FLEXFEC_RECEIVE_STREAM_IMPL_H_
