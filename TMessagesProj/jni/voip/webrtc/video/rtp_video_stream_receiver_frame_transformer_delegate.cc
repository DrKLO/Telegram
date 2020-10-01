/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "video/rtp_video_stream_receiver_frame_transformer_delegate.h"

#include <utility>
#include <vector>

#include "absl/memory/memory.h"
#include "modules/rtp_rtcp/source/rtp_descriptor_authentication.h"
#include "rtc_base/task_utils/to_queued_task.h"
#include "rtc_base/thread.h"

namespace webrtc {

namespace {
class TransformableVideoReceiverFrame
    : public TransformableVideoFrameInterface {
 public:
  TransformableVideoReceiverFrame(
      std::unique_ptr<video_coding::RtpFrameObject> frame,
      uint32_t ssrc)
      : frame_(std::move(frame)),
        metadata_(frame_->GetRtpVideoHeader()),
        ssrc_(ssrc) {}
  ~TransformableVideoReceiverFrame() override = default;

  // Implements TransformableVideoFrameInterface.
  rtc::ArrayView<const uint8_t> GetData() const override {
    return *frame_->GetEncodedData();
  }

  void SetData(rtc::ArrayView<const uint8_t> data) override {
    frame_->SetEncodedData(
        EncodedImageBuffer::Create(data.data(), data.size()));
  }

  uint32_t GetTimestamp() const override { return frame_->Timestamp(); }
  uint32_t GetSsrc() const override { return ssrc_; }

  bool IsKeyFrame() const override {
    return frame_->FrameType() == VideoFrameType::kVideoFrameKey;
  }

  std::vector<uint8_t> GetAdditionalData() const override {
    return RtpDescriptorAuthentication(frame_->GetRtpVideoHeader());
  }

  const VideoFrameMetadata& GetMetadata() const override { return metadata_; }

  std::unique_ptr<video_coding::RtpFrameObject> ExtractFrame() && {
    return std::move(frame_);
  }

 private:
  std::unique_ptr<video_coding::RtpFrameObject> frame_;
  const VideoFrameMetadata metadata_;
  const uint32_t ssrc_;
};
}  // namespace

RtpVideoStreamReceiverFrameTransformerDelegate::
    RtpVideoStreamReceiverFrameTransformerDelegate(
        RtpVideoFrameReceiver* receiver,
        rtc::scoped_refptr<FrameTransformerInterface> frame_transformer,
        rtc::Thread* network_thread,
        uint32_t ssrc)
    : receiver_(receiver),
      frame_transformer_(std::move(frame_transformer)),
      network_thread_(network_thread),
      ssrc_(ssrc) {}

void RtpVideoStreamReceiverFrameTransformerDelegate::Init() {
  RTC_DCHECK_RUN_ON(&network_sequence_checker_);
  frame_transformer_->RegisterTransformedFrameSinkCallback(
      rtc::scoped_refptr<TransformedFrameCallback>(this), ssrc_);
}

void RtpVideoStreamReceiverFrameTransformerDelegate::Reset() {
  RTC_DCHECK_RUN_ON(&network_sequence_checker_);
  frame_transformer_->UnregisterTransformedFrameSinkCallback(ssrc_);
  frame_transformer_ = nullptr;
  receiver_ = nullptr;
}

void RtpVideoStreamReceiverFrameTransformerDelegate::TransformFrame(
    std::unique_ptr<video_coding::RtpFrameObject> frame) {
  RTC_DCHECK_RUN_ON(&network_sequence_checker_);
  frame_transformer_->Transform(
      std::make_unique<TransformableVideoReceiverFrame>(std::move(frame),
                                                        ssrc_));
}

void RtpVideoStreamReceiverFrameTransformerDelegate::OnTransformedFrame(
    std::unique_ptr<TransformableFrameInterface> frame) {
  rtc::scoped_refptr<RtpVideoStreamReceiverFrameTransformerDelegate> delegate =
      this;
  network_thread_->PostTask(ToQueuedTask(
      [delegate = std::move(delegate), frame = std::move(frame)]() mutable {
        delegate->ManageFrame(std::move(frame));
      }));
}

void RtpVideoStreamReceiverFrameTransformerDelegate::ManageFrame(
    std::unique_ptr<TransformableFrameInterface> frame) {
  RTC_DCHECK_RUN_ON(&network_sequence_checker_);
  if (!receiver_)
    return;
  auto transformed_frame = absl::WrapUnique(
      static_cast<TransformableVideoReceiverFrame*>(frame.release()));
  receiver_->ManageFrame(std::move(*transformed_frame).ExtractFrame());
}

}  // namespace webrtc
