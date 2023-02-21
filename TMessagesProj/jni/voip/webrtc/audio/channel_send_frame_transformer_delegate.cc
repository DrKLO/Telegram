/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "audio/channel_send_frame_transformer_delegate.h"

#include <utility>

namespace webrtc {
namespace {

class TransformableOutgoingAudioFrame : public TransformableFrameInterface {
 public:
  TransformableOutgoingAudioFrame(AudioFrameType frame_type,
                                  uint8_t payload_type,
                                  uint32_t rtp_timestamp,
                                  uint32_t rtp_start_timestamp,
                                  const uint8_t* payload_data,
                                  size_t payload_size,
                                  int64_t absolute_capture_timestamp_ms,
                                  uint32_t ssrc)
      : frame_type_(frame_type),
        payload_type_(payload_type),
        rtp_timestamp_(rtp_timestamp),
        rtp_start_timestamp_(rtp_start_timestamp),
        payload_(payload_data, payload_size),
        absolute_capture_timestamp_ms_(absolute_capture_timestamp_ms),
        ssrc_(ssrc) {}
  ~TransformableOutgoingAudioFrame() override = default;
  rtc::ArrayView<const uint8_t> GetData() const override { return payload_; }
  void SetData(rtc::ArrayView<const uint8_t> data) override {
    payload_.SetData(data.data(), data.size());
  }
  uint32_t GetTimestamp() const override {
    return rtp_timestamp_ + rtp_start_timestamp_;
  }
  uint32_t GetStartTimestamp() const { return rtp_start_timestamp_; }
  uint32_t GetSsrc() const override { return ssrc_; }

  AudioFrameType GetFrameType() const { return frame_type_; }
  uint8_t GetPayloadType() const override { return payload_type_; }
  int64_t GetAbsoluteCaptureTimestampMs() const {
    return absolute_capture_timestamp_ms_;
  }
  Direction GetDirection() const override { return Direction::kSender; }

 private:
  AudioFrameType frame_type_;
  uint8_t payload_type_;
  uint32_t rtp_timestamp_;
  uint32_t rtp_start_timestamp_;
  rtc::Buffer payload_;
  int64_t absolute_capture_timestamp_ms_;
  uint32_t ssrc_;
};
}  // namespace

ChannelSendFrameTransformerDelegate::ChannelSendFrameTransformerDelegate(
    SendFrameCallback send_frame_callback,
    rtc::scoped_refptr<FrameTransformerInterface> frame_transformer,
    rtc::TaskQueue* encoder_queue)
    : send_frame_callback_(send_frame_callback),
      frame_transformer_(std::move(frame_transformer)),
      encoder_queue_(encoder_queue) {}

void ChannelSendFrameTransformerDelegate::Init() {
  frame_transformer_->RegisterTransformedFrameCallback(
      rtc::scoped_refptr<TransformedFrameCallback>(this));
}

void ChannelSendFrameTransformerDelegate::Reset() {
  frame_transformer_->UnregisterTransformedFrameCallback();
  frame_transformer_ = nullptr;

  MutexLock lock(&send_lock_);
  send_frame_callback_ = SendFrameCallback();
}

void ChannelSendFrameTransformerDelegate::Transform(
    AudioFrameType frame_type,
    uint8_t payload_type,
    uint32_t rtp_timestamp,
    uint32_t rtp_start_timestamp,
    const uint8_t* payload_data,
    size_t payload_size,
    int64_t absolute_capture_timestamp_ms,
    uint32_t ssrc) {
  frame_transformer_->Transform(
      std::make_unique<TransformableOutgoingAudioFrame>(
          frame_type, payload_type, rtp_timestamp, rtp_start_timestamp,
          payload_data, payload_size, absolute_capture_timestamp_ms, ssrc));
}

void ChannelSendFrameTransformerDelegate::OnTransformedFrame(
    std::unique_ptr<TransformableFrameInterface> frame) {
  MutexLock lock(&send_lock_);
  if (!send_frame_callback_)
    return;
  rtc::scoped_refptr<ChannelSendFrameTransformerDelegate> delegate(this);
  encoder_queue_->PostTask(
      [delegate = std::move(delegate), frame = std::move(frame)]() mutable {
        delegate->SendFrame(std::move(frame));
      });
}

void ChannelSendFrameTransformerDelegate::SendFrame(
    std::unique_ptr<TransformableFrameInterface> frame) const {
  MutexLock lock(&send_lock_);
  RTC_DCHECK_RUN_ON(encoder_queue_);
  RTC_CHECK_EQ(frame->GetDirection(),
               TransformableFrameInterface::Direction::kSender);
  if (!send_frame_callback_)
    return;
  auto* transformed_frame =
      static_cast<TransformableOutgoingAudioFrame*>(frame.get());
  send_frame_callback_(transformed_frame->GetFrameType(),
                       transformed_frame->GetPayloadType(),
                       transformed_frame->GetTimestamp() -
                           transformed_frame->GetStartTimestamp(),
                       transformed_frame->GetData(),
                       transformed_frame->GetAbsoluteCaptureTimestampMs());
}

}  // namespace webrtc
