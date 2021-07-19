/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_RTP_RTCP_SOURCE_RTCP_TRANSCEIVER_CONFIG_H_
#define MODULES_RTP_RTCP_SOURCE_RTCP_TRANSCEIVER_CONFIG_H_

#include <string>

#include "api/rtp_headers.h"
#include "api/task_queue/task_queue_base.h"
#include "api/video/video_bitrate_allocation.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "system_wrappers/include/clock.h"
#include "system_wrappers/include/ntp_time.h"

namespace webrtc {
class ReceiveStatisticsProvider;
class Transport;

// Interface to watch incoming rtcp packets by media (rtp) receiver.
class MediaReceiverRtcpObserver {
 public:
  virtual ~MediaReceiverRtcpObserver() = default;

  // All message handlers have default empty implementation. This way users only
  // need to implement the ones they are interested in.
  virtual void OnSenderReport(uint32_t sender_ssrc,
                              NtpTime ntp_time,
                              uint32_t rtp_time) {}
  virtual void OnBye(uint32_t sender_ssrc) {}
  virtual void OnBitrateAllocation(uint32_t sender_ssrc,
                                   const VideoBitrateAllocation& allocation) {}
};

struct RtcpTransceiverConfig {
  RtcpTransceiverConfig();
  RtcpTransceiverConfig(const RtcpTransceiverConfig&);
  RtcpTransceiverConfig& operator=(const RtcpTransceiverConfig&);
  ~RtcpTransceiverConfig();

  // Logs the error and returns false if configuration miss key objects or
  // is inconsistant. May log warnings.
  bool Validate() const;

  // Used to prepend all log messages. Can be empty.
  std::string debug_id;

  // Ssrc to use as default sender ssrc, e.g. for transport-wide feedbacks.
  uint32_t feedback_ssrc = 1;

  // Canonical End-Point Identifier of the local particiapnt.
  // Defined in rfc3550 section 6 note 2 and section 6.5.1.
  std::string cname;

  // Maximum packet size outgoing transport accepts.
  size_t max_packet_size = 1200;

  // The clock to use when querying for the NTP time. Should be set.
  Clock* clock = nullptr;

  // Transport to send rtcp packets to. Should be set.
  Transport* outgoing_transport = nullptr;

  // Queue for scheduling delayed tasks, e.g. sending periodic compound packets.
  TaskQueueBase* task_queue = nullptr;

  // Rtcp report block generator for outgoing receiver reports.
  ReceiveStatisticsProvider* receive_statistics = nullptr;

  // Callback to pass result of rtt calculation. Should outlive RtcpTransceiver.
  // Callbacks will be invoked on the task_queue.
  RtcpRttStats* rtt_observer = nullptr;

  // Configures if sending should
  //  enforce compound packets: https://tools.ietf.org/html/rfc4585#section-3.1
  //  or allow reduced size packets: https://tools.ietf.org/html/rfc5506
  // Receiving accepts both compound and reduced-size packets.
  RtcpMode rtcp_mode = RtcpMode::kCompound;
  //
  // Tuning parameters.
  //
  // Initial state if |outgoing_transport| ready to accept packets.
  bool initial_ready_to_send = true;
  // Delay before 1st periodic compound packet.
  int initial_report_delay_ms = 500;

  // Period between periodic compound packets.
  int report_period_ms = 1000;

  //
  // Flags for features and experiments.
  //
  bool schedule_periodic_compound_packets = true;
  // Estimate RTT as non-sender as described in
  // https://tools.ietf.org/html/rfc3611#section-4.4 and #section-4.5
  bool non_sender_rtt_measurement = false;

  // Allows a REMB message to be sent immediately when SetRemb is called without
  // having to wait for the next compount message to be sent.
  bool send_remb_on_change = false;
};

}  // namespace webrtc

#endif  // MODULES_RTP_RTCP_SOURCE_RTCP_TRANSCEIVER_CONFIG_H_
