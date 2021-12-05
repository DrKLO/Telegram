/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_RTP_RTCP_SOURCE_RTP_SENDER_VIDEO_FRAME_TRANSFORMER_DELEGATE_H_
#define MODULES_RTP_RTCP_SOURCE_RTP_SENDER_VIDEO_FRAME_TRANSFORMER_DELEGATE_H_

#include <memory>

#include "api/frame_transformer_interface.h"
#include "api/scoped_refptr.h"
#include "api/task_queue/task_queue_base.h"
#include "api/video/video_layers_allocation.h"
#include "rtc_base/synchronization/mutex.h"

namespace webrtc {

class RTPSenderVideo;

// Delegates calls to FrameTransformerInterface to transform frames, and to
// RTPSenderVideo to send the transformed frames. Ensures thread-safe access to
// the sender.
class RTPSenderVideoFrameTransformerDelegate : public TransformedFrameCallback {
 public:
  RTPSenderVideoFrameTransformerDelegate(
      RTPSenderVideo* sender,
      rtc::scoped_refptr<FrameTransformerInterface> frame_transformer,
      uint32_t ssrc,
      TaskQueueBase* send_transport_queue);

  void Init();

  // Delegates the call to FrameTransformerInterface::TransformFrame.
  bool TransformFrame(int payload_type,
                      absl::optional<VideoCodecType> codec_type,
                      uint32_t rtp_timestamp,
                      const EncodedImage& encoded_image,
                      RTPVideoHeader video_header,
                      absl::optional<int64_t> expected_retransmission_time_ms);

  // Implements TransformedFrameCallback. Can be called on any thread. Posts
  // the transformed frame to be sent on the |encoder_queue_|.
  void OnTransformedFrame(
      std::unique_ptr<TransformableFrameInterface> frame) override;

  // Delegates the call to RTPSendVideo::SendVideo on the |encoder_queue_|.
  void SendVideo(std::unique_ptr<TransformableFrameInterface> frame) const;

  // Delegates the call to RTPSendVideo::SetVideoStructureAfterTransformation
  // under |sender_lock_|.
  void SetVideoStructureUnderLock(
      const FrameDependencyStructure* video_structure);

  // Delegates the call to
  // RTPSendVideo::SetVideoLayersAllocationAfterTransformation under
  // |sender_lock_|.
  void SetVideoLayersAllocationUnderLock(VideoLayersAllocation allocation);

  // Unregisters and releases the |frame_transformer_| reference, and resets
  // |sender_| under lock. Called from RTPSenderVideo destructor to prevent the
  // |sender_| to dangle.
  void Reset();

 protected:
  ~RTPSenderVideoFrameTransformerDelegate() override = default;

 private:
  mutable Mutex sender_lock_;
  RTPSenderVideo* sender_ RTC_GUARDED_BY(sender_lock_);
  rtc::scoped_refptr<FrameTransformerInterface> frame_transformer_;
  const uint32_t ssrc_;
  TaskQueueBase* encoder_queue_ = nullptr;
  TaskQueueBase* send_transport_queue_;
};

}  // namespace webrtc

#endif  // MODULES_RTP_RTCP_SOURCE_RTP_SENDER_VIDEO_FRAME_TRANSFORMER_DELEGATE_H_
