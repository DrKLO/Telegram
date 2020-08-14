/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "video/encoder_rtcp_feedback.h"

#include "absl/types/optional.h"
#include "api/video_codecs/video_encoder.h"
#include "rtc_base/checks.h"
#include "rtc_base/experiments/keyframe_interval_settings.h"

namespace webrtc {

namespace {
constexpr int kMinKeyframeSendIntervalMs = 300;
}  // namespace

EncoderRtcpFeedback::EncoderRtcpFeedback(Clock* clock,
                                         const std::vector<uint32_t>& ssrcs,
                                         VideoStreamEncoderInterface* encoder)
    : clock_(clock),
      ssrcs_(ssrcs),
      rtp_video_sender_(nullptr),
      video_stream_encoder_(encoder),
      time_last_intra_request_ms_(-1),
      min_keyframe_send_interval_ms_(
          KeyframeIntervalSettings::ParseFromFieldTrials()
              .MinKeyframeSendIntervalMs()
              .value_or(kMinKeyframeSendIntervalMs)) {
  RTC_DCHECK(!ssrcs.empty());
}

void EncoderRtcpFeedback::SetRtpVideoSender(
    const RtpVideoSenderInterface* rtp_video_sender) {
  RTC_DCHECK(rtp_video_sender);
  RTC_DCHECK(!rtp_video_sender_);
  rtp_video_sender_ = rtp_video_sender;
}

bool EncoderRtcpFeedback::HasSsrc(uint32_t ssrc) {
  for (uint32_t registered_ssrc : ssrcs_) {
    if (registered_ssrc == ssrc) {
      return true;
    }
  }
  return false;
}

void EncoderRtcpFeedback::OnReceivedIntraFrameRequest(uint32_t ssrc) {
  RTC_DCHECK(HasSsrc(ssrc));
  {
    int64_t now_ms = clock_->TimeInMilliseconds();
    MutexLock lock(&mutex_);
    if (time_last_intra_request_ms_ + min_keyframe_send_interval_ms_ > now_ms) {
      return;
    }
    time_last_intra_request_ms_ = now_ms;
  }

  // Always produce key frame for all streams.
  video_stream_encoder_->SendKeyFrame();
}

void EncoderRtcpFeedback::OnReceivedLossNotification(
    uint32_t ssrc,
    uint16_t seq_num_of_last_decodable,
    uint16_t seq_num_of_last_received,
    bool decodability_flag) {
  RTC_DCHECK(rtp_video_sender_) << "Object initialization incomplete.";

  const std::vector<uint16_t> seq_nums = {seq_num_of_last_decodable,
                                          seq_num_of_last_received};
  const std::vector<RtpSequenceNumberMap::Info> infos =
      rtp_video_sender_->GetSentRtpPacketInfos(ssrc, seq_nums);
  if (infos.empty()) {
    return;
  }
  RTC_DCHECK_EQ(infos.size(), 2u);

  const RtpSequenceNumberMap::Info& last_decodable = infos[0];
  const RtpSequenceNumberMap::Info& last_received = infos[1];

  VideoEncoder::LossNotification loss_notification;
  loss_notification.timestamp_of_last_decodable = last_decodable.timestamp;
  loss_notification.timestamp_of_last_received = last_received.timestamp;

  // Deduce decodability of the last received frame and of its dependencies.
  if (last_received.is_first && last_received.is_last) {
    // The frame consists of a single packet, and that packet has evidently
    // been received in full; the frame is therefore assemblable.
    // In this case, the decodability of the dependencies is communicated by
    // the decodability flag, and the frame itself is decodable if and only
    // if they are decodable.
    loss_notification.dependencies_of_last_received_decodable =
        decodability_flag;
    loss_notification.last_received_decodable = decodability_flag;
  } else if (last_received.is_first && !last_received.is_last) {
    // In this case, the decodability flag communicates the decodability of
    // the dependencies. If any is undecodable, we also know that the frame
    // itself will not be decodable; if all are decodable, the frame's own
    // decodability will remain unknown, as not all of its packets have
    // been received.
    loss_notification.dependencies_of_last_received_decodable =
        decodability_flag;
    loss_notification.last_received_decodable =
        !decodability_flag ? absl::make_optional(false) : absl::nullopt;
  } else if (!last_received.is_first && last_received.is_last) {
    if (decodability_flag) {
      // The frame has been received in full, and found to be decodable.
      // (Messages of this type are not sent by WebRTC at the moment, but are
      // theoretically possible, for example for serving as acks.)
      loss_notification.dependencies_of_last_received_decodable = true;
      loss_notification.last_received_decodable = true;
    } else {
      // It is impossible to tell whether some dependencies were undecodable,
      // or whether the frame was unassemblable, but in either case, the frame
      // itself was undecodable.
      loss_notification.dependencies_of_last_received_decodable = absl::nullopt;
      loss_notification.last_received_decodable = false;
    }
  } else {  // !last_received.is_first && !last_received.is_last
    if (decodability_flag) {
      // The frame has not yet been received in full, but no gaps have
      // been encountered so far, and the dependencies were all decodable.
      // (Messages of this type are not sent by WebRTC at the moment, but are
      // theoretically possible, for example for serving as acks.)
      loss_notification.dependencies_of_last_received_decodable = true;
      loss_notification.last_received_decodable = absl::nullopt;
    } else {
      // It is impossible to tell whether some dependencies were undecodable,
      // or whether the frame was unassemblable, but in either case, the frame
      // itself was undecodable.
      loss_notification.dependencies_of_last_received_decodable = absl::nullopt;
      loss_notification.last_received_decodable = false;
    }
  }

  video_stream_encoder_->OnLossNotification(loss_notification);
}

}  // namespace webrtc
