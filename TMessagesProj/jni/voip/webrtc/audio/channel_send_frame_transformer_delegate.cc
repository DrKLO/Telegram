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
#include <vector>

namespace webrtc {
namespace {

using IfaceFrameType = TransformableAudioFrameInterface::FrameType;

IfaceFrameType InternalFrameTypeToInterfaceFrameType(
    const AudioFrameType frame_type) {
  switch (frame_type) {
    case AudioFrameType::kEmptyFrame:
      return IfaceFrameType::kEmptyFrame;
    case AudioFrameType::kAudioFrameSpeech:
      return IfaceFrameType::kAudioFrameSpeech;
    case AudioFrameType::kAudioFrameCN:
      return IfaceFrameType::kAudioFrameCN;
  }
  RTC_DCHECK_NOTREACHED();
  return IfaceFrameType::kEmptyFrame;
}

AudioFrameType InterfaceFrameTypeToInternalFrameType(
    const IfaceFrameType frame_type) {
  switch (frame_type) {
    case IfaceFrameType::kEmptyFrame:
      return AudioFrameType::kEmptyFrame;
    case IfaceFrameType::kAudioFrameSpeech:
      return AudioFrameType::kAudioFrameSpeech;
    case IfaceFrameType::kAudioFrameCN:
      return AudioFrameType::kAudioFrameCN;
  }
  RTC_DCHECK_NOTREACHED();
  return AudioFrameType::kEmptyFrame;
}

class TransformableOutgoingAudioFrame
    : public TransformableAudioFrameInterface {
 public:
  TransformableOutgoingAudioFrame(
      AudioFrameType frame_type,
      uint8_t payload_type,
      uint32_t rtp_timestamp_with_offset,
      const uint8_t* payload_data,
      size_t payload_size,
      absl::optional<uint64_t> absolute_capture_timestamp_ms,
      uint32_t ssrc,
      std::vector<uint32_t> csrcs,
      const std::string& codec_mime_type,
      absl::optional<uint16_t> sequence_number)
      : frame_type_(frame_type),
        payload_type_(payload_type),
        rtp_timestamp_with_offset_(rtp_timestamp_with_offset),
        payload_(payload_data, payload_size),
        absolute_capture_timestamp_ms_(absolute_capture_timestamp_ms),
        ssrc_(ssrc),
        csrcs_(std::move(csrcs)),
        codec_mime_type_(codec_mime_type),
        sequence_number_(sequence_number) {}
  ~TransformableOutgoingAudioFrame() override = default;
  rtc::ArrayView<const uint8_t> GetData() const override { return payload_; }
  void SetData(rtc::ArrayView<const uint8_t> data) override {
    payload_.SetData(data.data(), data.size());
  }
  uint32_t GetTimestamp() const override { return rtp_timestamp_with_offset_; }
  uint32_t GetSsrc() const override { return ssrc_; }

  IfaceFrameType Type() const override {
    return InternalFrameTypeToInterfaceFrameType(frame_type_);
  }

  uint8_t GetPayloadType() const override { return payload_type_; }
  Direction GetDirection() const override { return Direction::kSender; }
  std::string GetMimeType() const override { return codec_mime_type_; }

  rtc::ArrayView<const uint32_t> GetContributingSources() const override {
    return csrcs_;
  }

  const absl::optional<uint16_t> SequenceNumber() const override {
    return sequence_number_;
  }

  void SetRTPTimestamp(uint32_t rtp_timestamp_with_offset) override {
    rtp_timestamp_with_offset_ = rtp_timestamp_with_offset;
  }

  absl::optional<uint64_t> AbsoluteCaptureTimestamp() const override {
    return absolute_capture_timestamp_ms_;
  }

 private:
  AudioFrameType frame_type_;
  uint8_t payload_type_;
  uint32_t rtp_timestamp_with_offset_;
  rtc::Buffer payload_;
  absl::optional<uint64_t> absolute_capture_timestamp_ms_;
  uint32_t ssrc_;
  std::vector<uint32_t> csrcs_;
  std::string codec_mime_type_;
  absl::optional<uint16_t> sequence_number_;
};
}  // namespace

ChannelSendFrameTransformerDelegate::ChannelSendFrameTransformerDelegate(
    SendFrameCallback send_frame_callback,
    rtc::scoped_refptr<FrameTransformerInterface> frame_transformer,
    TaskQueueBase* encoder_queue)
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
    const uint8_t* payload_data,
    size_t payload_size,
    int64_t absolute_capture_timestamp_ms,
    uint32_t ssrc,
    const std::string& codec_mimetype) {
  {
    MutexLock lock(&send_lock_);
    if (short_circuit_) {
      send_frame_callback_(
          frame_type, payload_type, rtp_timestamp,
          rtc::ArrayView<const uint8_t>(payload_data, payload_size),
          absolute_capture_timestamp_ms, /*csrcs=*/{});
      return;
    }
  }
  frame_transformer_->Transform(
      std::make_unique<TransformableOutgoingAudioFrame>(
          frame_type, payload_type, rtp_timestamp, payload_data, payload_size,
          absolute_capture_timestamp_ms, ssrc,
          /*csrcs=*/std::vector<uint32_t>(), codec_mimetype,
          /*sequence_number=*/absl::nullopt));
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

void ChannelSendFrameTransformerDelegate::StartShortCircuiting() {
  MutexLock lock(&send_lock_);
  short_circuit_ = true;
}

void ChannelSendFrameTransformerDelegate::SendFrame(
    std::unique_ptr<TransformableFrameInterface> frame) const {
  MutexLock lock(&send_lock_);
  RTC_DCHECK_RUN_ON(encoder_queue_);
  if (!send_frame_callback_)
    return;
  auto* transformed_frame =
      static_cast<TransformableAudioFrameInterface*>(frame.get());
  send_frame_callback_(
      InterfaceFrameTypeToInternalFrameType(transformed_frame->Type()),
      transformed_frame->GetPayloadType(), transformed_frame->GetTimestamp(),
      transformed_frame->GetData(),
      transformed_frame->AbsoluteCaptureTimestamp()
          ? *transformed_frame->AbsoluteCaptureTimestamp()
          : 0,
      transformed_frame->GetContributingSources());
}

std::unique_ptr<TransformableAudioFrameInterface> CloneSenderAudioFrame(
    TransformableAudioFrameInterface* original) {
  std::vector<uint32_t> csrcs;
  csrcs.assign(original->GetContributingSources().begin(),
               original->GetContributingSources().end());
  return std::make_unique<TransformableOutgoingAudioFrame>(
      InterfaceFrameTypeToInternalFrameType(original->Type()),
      original->GetPayloadType(), original->GetTimestamp(),
      original->GetData().data(), original->GetData().size(),
      original->AbsoluteCaptureTimestamp(), original->GetSsrc(),
      std::move(csrcs), original->GetMimeType(), original->SequenceNumber());
}

}  // namespace webrtc
