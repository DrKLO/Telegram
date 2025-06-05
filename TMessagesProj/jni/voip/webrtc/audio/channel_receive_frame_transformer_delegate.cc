/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "audio/channel_receive_frame_transformer_delegate.h"

#include <string>
#include <utility>

#include "rtc_base/buffer.h"

namespace webrtc {
namespace {

class TransformableIncomingAudioFrame
    : public TransformableAudioFrameInterface {
 public:
  TransformableIncomingAudioFrame(rtc::ArrayView<const uint8_t> payload,
                                  const RTPHeader& header,
                                  uint32_t ssrc,
                                  const std::string& codec_mime_type)
      : payload_(payload.data(), payload.size()),
        header_(header),
        ssrc_(ssrc),
        codec_mime_type_(codec_mime_type) {}
  ~TransformableIncomingAudioFrame() override = default;
  rtc::ArrayView<const uint8_t> GetData() const override { return payload_; }

  void SetData(rtc::ArrayView<const uint8_t> data) override {
    payload_.SetData(data.data(), data.size());
  }

  void SetRTPTimestamp(uint32_t timestamp) override {
    header_.timestamp = timestamp;
  }

  uint8_t GetPayloadType() const override { return header_.payloadType; }
  uint32_t GetSsrc() const override { return ssrc_; }
  uint32_t GetTimestamp() const override { return header_.timestamp; }
  rtc::ArrayView<const uint32_t> GetContributingSources() const override {
    return rtc::ArrayView<const uint32_t>(header_.arrOfCSRCs, header_.numCSRCs);
  }
  Direction GetDirection() const override { return Direction::kReceiver; }

  std::string GetMimeType() const override { return codec_mime_type_; }
  const absl::optional<uint16_t> SequenceNumber() const override {
    return header_.sequenceNumber;
  }

  absl::optional<uint64_t> AbsoluteCaptureTimestamp() const override {
    // This could be extracted from received header extensions + extrapolation,
    // if required in future, eg for being able to re-send received frames.
    return absl::nullopt;
  }
  const RTPHeader& Header() const { return header_; }

  FrameType Type() const override {
    return header_.extension.voiceActivity ? FrameType::kAudioFrameSpeech
                                           : FrameType::kAudioFrameCN;
  }

 private:
  rtc::Buffer payload_;
  RTPHeader header_;
  uint32_t ssrc_;
  std::string codec_mime_type_;
};
}  // namespace

ChannelReceiveFrameTransformerDelegate::ChannelReceiveFrameTransformerDelegate(
    ReceiveFrameCallback receive_frame_callback,
    rtc::scoped_refptr<FrameTransformerInterface> frame_transformer,
    TaskQueueBase* channel_receive_thread)
    : receive_frame_callback_(receive_frame_callback),
      frame_transformer_(std::move(frame_transformer)),
      channel_receive_thread_(channel_receive_thread) {}

void ChannelReceiveFrameTransformerDelegate::Init() {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  frame_transformer_->RegisterTransformedFrameCallback(
      rtc::scoped_refptr<TransformedFrameCallback>(this));
}

void ChannelReceiveFrameTransformerDelegate::Reset() {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  frame_transformer_->UnregisterTransformedFrameCallback();
  frame_transformer_ = nullptr;
  receive_frame_callback_ = ReceiveFrameCallback();
}

void ChannelReceiveFrameTransformerDelegate::Transform(
    rtc::ArrayView<const uint8_t> packet,
    const RTPHeader& header,
    uint32_t ssrc,
    const std::string& codec_mime_type) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  if (short_circuit_) {
    receive_frame_callback_(packet, header);
  } else {
    frame_transformer_->Transform(
        std::make_unique<TransformableIncomingAudioFrame>(packet, header, ssrc,
                                                          codec_mime_type));
  }
}

void ChannelReceiveFrameTransformerDelegate::OnTransformedFrame(
    std::unique_ptr<TransformableFrameInterface> frame) {
  rtc::scoped_refptr<ChannelReceiveFrameTransformerDelegate> delegate(this);
  channel_receive_thread_->PostTask(
      [delegate = std::move(delegate), frame = std::move(frame)]() mutable {
        delegate->ReceiveFrame(std::move(frame));
      });
}

void ChannelReceiveFrameTransformerDelegate::StartShortCircuiting() {
  rtc::scoped_refptr<ChannelReceiveFrameTransformerDelegate> delegate(this);
  channel_receive_thread_->PostTask([delegate = std::move(delegate)]() mutable {
    RTC_DCHECK_RUN_ON(&delegate->sequence_checker_);
    delegate->short_circuit_ = true;
  });
}

void ChannelReceiveFrameTransformerDelegate::ReceiveFrame(
    std::unique_ptr<TransformableFrameInterface> frame) const {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  if (!receive_frame_callback_)
    return;

  RTPHeader header;
  if (frame->GetDirection() ==
      TransformableFrameInterface::Direction::kSender) {
    auto* transformed_frame =
        static_cast<TransformableAudioFrameInterface*>(frame.get());
    header.payloadType = transformed_frame->GetPayloadType();
    header.timestamp = transformed_frame->GetTimestamp();
    header.ssrc = transformed_frame->GetSsrc();
    if (transformed_frame->AbsoluteCaptureTimestamp().has_value()) {
      header.extension.absolute_capture_time = AbsoluteCaptureTime();
      header.extension.absolute_capture_time->absolute_capture_timestamp =
          transformed_frame->AbsoluteCaptureTimestamp().value();
    }
  } else {
    auto* transformed_frame =
        static_cast<TransformableIncomingAudioFrame*>(frame.get());
    header = transformed_frame->Header();
  }

  // TODO(crbug.com/1464860): Take an explicit struct with the required
  // information rather than the RTPHeader to make it easier to
  // construct the required information when injecting transformed frames not
  // originally from this receiver.
  receive_frame_callback_(frame->GetData(), header);
}

rtc::scoped_refptr<FrameTransformerInterface>
ChannelReceiveFrameTransformerDelegate::FrameTransformer() {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  return frame_transformer_;
}

}  // namespace webrtc
