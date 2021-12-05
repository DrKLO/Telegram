/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef VIDEO_ENCODER_RTCP_FEEDBACK_H_
#define VIDEO_ENCODER_RTCP_FEEDBACK_H_

#include <vector>

#include "api/video/video_stream_encoder_interface.h"
#include "call/rtp_video_sender_interface.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "rtc_base/synchronization/mutex.h"
#include "system_wrappers/include/clock.h"

namespace webrtc {

class VideoStreamEncoderInterface;

// This class passes feedback (such as key frame requests or loss notifications)
// from the RtpRtcp module.
class EncoderRtcpFeedback : public RtcpIntraFrameObserver,
                            public RtcpLossNotificationObserver {
 public:
  EncoderRtcpFeedback(Clock* clock,
                      const std::vector<uint32_t>& ssrcs,
                      VideoStreamEncoderInterface* encoder);
  ~EncoderRtcpFeedback() override = default;

  void SetRtpVideoSender(const RtpVideoSenderInterface* rtp_video_sender);

  void OnReceivedIntraFrameRequest(uint32_t ssrc) override;

  // Implements RtcpLossNotificationObserver.
  void OnReceivedLossNotification(uint32_t ssrc,
                                  uint16_t seq_num_of_last_decodable,
                                  uint16_t seq_num_of_last_received,
                                  bool decodability_flag) override;

 private:
  bool HasSsrc(uint32_t ssrc);

  Clock* const clock_;
  const std::vector<uint32_t> ssrcs_;
  const RtpVideoSenderInterface* rtp_video_sender_;
  VideoStreamEncoderInterface* const video_stream_encoder_;

  Mutex mutex_;
  int64_t time_last_intra_request_ms_ RTC_GUARDED_BY(mutex_);

  const int min_keyframe_send_interval_ms_;
};

}  // namespace webrtc

#endif  // VIDEO_ENCODER_RTCP_FEEDBACK_H_
