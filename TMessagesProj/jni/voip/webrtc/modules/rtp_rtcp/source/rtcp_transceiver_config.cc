/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtcp_transceiver_config.h"

#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "rtc_base/logging.h"

namespace webrtc {

RtcpTransceiverConfig::RtcpTransceiverConfig() = default;
RtcpTransceiverConfig::RtcpTransceiverConfig(const RtcpTransceiverConfig&) =
    default;
RtcpTransceiverConfig& RtcpTransceiverConfig::operator=(
    const RtcpTransceiverConfig&) = default;
RtcpTransceiverConfig::~RtcpTransceiverConfig() = default;

bool RtcpTransceiverConfig::Validate() const {
  if (feedback_ssrc == 0)
    RTC_LOG(LS_WARNING)
        << debug_id
        << "Ssrc 0 may be treated by some implementation as invalid.";
  if (cname.empty())
    RTC_LOG(LS_WARNING) << debug_id << "missing cname for ssrc "
                        << feedback_ssrc;
  if (cname.size() > 255) {
    RTC_LOG(LS_ERROR) << debug_id << "cname can be maximum 255 characters.";
    return false;
  }
  if (max_packet_size < 100) {
    RTC_LOG(LS_ERROR) << debug_id << "max packet size " << max_packet_size
                      << " is too small.";
    return false;
  }
  if (max_packet_size > IP_PACKET_SIZE) {
    RTC_LOG(LS_ERROR) << debug_id << "max packet size " << max_packet_size
                      << " more than " << IP_PACKET_SIZE << " is unsupported.";
    return false;
  }
  if (!outgoing_transport) {
    RTC_LOG(LS_ERROR) << debug_id << "outgoing transport must be set";
    return false;
  }
  if (initial_report_delay < TimeDelta::Zero()) {
    RTC_LOG(LS_ERROR) << debug_id << "delay " << initial_report_delay.ms()
                      << "ms before first report shouldn't be negative.";
    return false;
  }
  if (report_period <= TimeDelta::Zero()) {
    RTC_LOG(LS_ERROR) << debug_id << "period " << report_period.ms()
                      << "ms between reports should be positive.";
    return false;
  }
  if (schedule_periodic_compound_packets && task_queue == nullptr) {
    RTC_LOG(LS_ERROR) << debug_id
                      << "missing task queue for periodic compound packets";
    return false;
  }
  if (rtcp_mode != RtcpMode::kCompound && rtcp_mode != RtcpMode::kReducedSize) {
    RTC_LOG(LS_ERROR) << debug_id << "unsupported rtcp mode";
    return false;
  }
  if (non_sender_rtt_measurement && !network_link_observer)
    RTC_LOG(LS_WARNING) << debug_id
                        << "Enabled special feature to calculate rtt, but no "
                           "rtt observer is provided.";
  // TODO(danilchap): Remove or update the warning when RtcpTransceiver supports
  // send-only sessions.
  if (receive_statistics == nullptr)
    RTC_LOG(LS_WARNING)
        << debug_id
        << "receive statistic should be set to generate rtcp report blocks.";
  return true;
}

}  // namespace webrtc
