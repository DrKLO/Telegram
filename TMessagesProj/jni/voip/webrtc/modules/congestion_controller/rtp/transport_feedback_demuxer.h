/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef MODULES_CONGESTION_CONTROLLER_RTP_TRANSPORT_FEEDBACK_DEMUXER_H_
#define MODULES_CONGESTION_CONTROLLER_RTP_TRANSPORT_FEEDBACK_DEMUXER_H_

#include <map>
#include <utility>
#include <vector>

#include "modules/include/module_common_types_public.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "rtc_base/synchronization/mutex.h"

namespace webrtc {

class TransportFeedbackDemuxer : public StreamFeedbackProvider {
 public:
  // Implements StreamFeedbackProvider interface
  void RegisterStreamFeedbackObserver(
      std::vector<uint32_t> ssrcs,
      StreamFeedbackObserver* observer) override;
  void DeRegisterStreamFeedbackObserver(
      StreamFeedbackObserver* observer) override;
  void AddPacket(const RtpPacketSendInfo& packet_info);
  void OnTransportFeedback(const rtcp::TransportFeedback& feedback);

 private:
  Mutex lock_;
  SequenceNumberUnwrapper seq_num_unwrapper_ RTC_GUARDED_BY(&lock_);
  std::map<int64_t, StreamFeedbackObserver::StreamPacketInfo> history_
      RTC_GUARDED_BY(&lock_);

  // Maps a set of ssrcs to corresponding observer. Vectors are used rather than
  // set/map to ensure that the processing order is consistent independently of
  // the randomized ssrcs.
  Mutex observers_lock_;
  std::vector<std::pair<std::vector<uint32_t>, StreamFeedbackObserver*>>
      observers_ RTC_GUARDED_BY(&observers_lock_);
};
}  // namespace webrtc

#endif  // MODULES_CONGESTION_CONTROLLER_RTP_TRANSPORT_FEEDBACK_DEMUXER_H_
