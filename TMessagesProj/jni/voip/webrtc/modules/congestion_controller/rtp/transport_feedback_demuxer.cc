/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/congestion_controller/rtp/transport_feedback_demuxer.h"

#include "absl/algorithm/container.h"
#include "modules/rtp_rtcp/source/rtcp_packet/transport_feedback.h"

namespace webrtc {
namespace {
static const size_t kMaxPacketsInHistory = 5000;
}

TransportFeedbackDemuxer::TransportFeedbackDemuxer() {
  // In case the construction thread is different from where the registration
  // and callbacks occur, detach from the construction thread.
  observer_checker_.Detach();
}

void TransportFeedbackDemuxer::RegisterStreamFeedbackObserver(
    std::vector<uint32_t> ssrcs,
    StreamFeedbackObserver* observer) {
  RTC_DCHECK_RUN_ON(&observer_checker_);
  RTC_DCHECK(observer);
  RTC_DCHECK(absl::c_find_if(observers_, [=](const auto& pair) {
               return pair.second == observer;
             }) == observers_.end());
  observers_.push_back({ssrcs, observer});
}

void TransportFeedbackDemuxer::DeRegisterStreamFeedbackObserver(
    StreamFeedbackObserver* observer) {
  RTC_DCHECK_RUN_ON(&observer_checker_);
  RTC_DCHECK(observer);
  const auto it = absl::c_find_if(
      observers_, [=](const auto& pair) { return pair.second == observer; });
  RTC_DCHECK(it != observers_.end());
  observers_.erase(it);
}

void TransportFeedbackDemuxer::AddPacket(const RtpPacketSendInfo& packet_info) {
  RTC_DCHECK_RUN_ON(&observer_checker_);

  StreamFeedbackObserver::StreamPacketInfo info;
  info.ssrc = packet_info.media_ssrc;
  info.rtp_sequence_number = packet_info.rtp_sequence_number;
  info.received = false;
  info.is_retransmission =
      packet_info.packet_type == RtpPacketMediaType::kRetransmission;
  history_.insert(
      {seq_num_unwrapper_.Unwrap(packet_info.transport_sequence_number), info});

  while (history_.size() > kMaxPacketsInHistory) {
    history_.erase(history_.begin());
  }
}

void TransportFeedbackDemuxer::OnTransportFeedback(
    const rtcp::TransportFeedback& feedback) {
  RTC_DCHECK_RUN_ON(&observer_checker_);

  std::vector<StreamFeedbackObserver::StreamPacketInfo> stream_feedbacks;
  feedback.ForAllPackets(
      [&](uint16_t sequence_number, TimeDelta delta_since_base) {
        RTC_DCHECK_RUN_ON(&observer_checker_);
        auto it = history_.find(seq_num_unwrapper_.PeekUnwrap(sequence_number));
        if (it != history_.end()) {
          auto packet_info = it->second;
          packet_info.received = delta_since_base.IsFinite();
          stream_feedbacks.push_back(std::move(packet_info));
          if (delta_since_base.IsFinite())
            history_.erase(it);
        }
      });

  for (auto& observer : observers_) {
    std::vector<StreamFeedbackObserver::StreamPacketInfo> selected_feedback;
    for (const auto& packet_info : stream_feedbacks) {
      if (absl::c_count(observer.first, packet_info.ssrc) > 0) {
        selected_feedback.push_back(packet_info);
      }
    }
    if (!selected_feedback.empty()) {
      observer.second->OnPacketFeedbackVector(std::move(selected_feedback));
    }
  }
}

}  // namespace webrtc
