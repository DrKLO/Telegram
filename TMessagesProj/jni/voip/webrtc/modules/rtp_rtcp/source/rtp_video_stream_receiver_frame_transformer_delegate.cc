/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtp_video_stream_receiver_frame_transformer_delegate.h"

#include <string>
#include <utility>
#include <vector>

#include "absl/memory/memory.h"
#include "modules/rtp_rtcp/source/rtp_descriptor_authentication.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/thread.h"

namespace webrtc {

namespace {
class TransformableVideoReceiverFrame
    : public TransformableVideoFrameInterface {
 public:
  TransformableVideoReceiverFrame(std::unique_ptr<RtpFrameObject> frame,
                                  uint32_t ssrc,
                                  RtpVideoFrameReceiver* receiver)
      : frame_(std::move(frame)),
        metadata_(frame_->GetRtpVideoHeader().GetAsMetadata()),
        receiver_(receiver) {
    metadata_.SetSsrc(ssrc);
    metadata_.SetCsrcs(frame_->Csrcs());
  }
  ~TransformableVideoReceiverFrame() override = default;

  // Implements TransformableVideoFrameInterface.
  rtc::ArrayView<const uint8_t> GetData() const override {
    return *frame_->GetEncodedData();
  }

  void SetData(rtc::ArrayView<const uint8_t> data) override {
    frame_->SetEncodedData(
        EncodedImageBuffer::Create(data.data(), data.size()));
  }

  uint8_t GetPayloadType() const override { return frame_->PayloadType(); }
  uint32_t GetSsrc() const override { return Metadata().GetSsrc(); }
  uint32_t GetTimestamp() const override { return frame_->RtpTimestamp(); }
  void SetRTPTimestamp(uint32_t timestamp) override {
    frame_->SetRtpTimestamp(timestamp);
  }

  bool IsKeyFrame() const override {
    return frame_->FrameType() == VideoFrameType::kVideoFrameKey;
  }

  VideoFrameMetadata Metadata() const override { return metadata_; }

  void SetMetadata(const VideoFrameMetadata& metadata) override {
    // Create |new_metadata| from existing metadata and change only frameId and
    // dependencies.
    VideoFrameMetadata new_metadata = Metadata();
    new_metadata.SetFrameId(metadata.GetFrameId());
    new_metadata.SetFrameDependencies(metadata.GetFrameDependencies());
    RTC_DCHECK(new_metadata == metadata)
        << "TransformableVideoReceiverFrame::SetMetadata can be only used to "
           "change frameID and dependencies";
    frame_->SetHeaderFromMetadata(new_metadata);
  }

  std::unique_ptr<RtpFrameObject> ExtractFrame() && {
    return std::move(frame_);
  }

  Direction GetDirection() const override { return Direction::kReceiver; }
  std::string GetMimeType() const override {
    std::string mime_type = "video/";
    return mime_type + CodecTypeToPayloadString(frame_->codec_type());
  }

  const RtpVideoFrameReceiver* Receiver() { return receiver_; }

 private:
  std::unique_ptr<RtpFrameObject> frame_;
  VideoFrameMetadata metadata_;
  RtpVideoFrameReceiver* receiver_;
};
}  // namespace

RtpVideoStreamReceiverFrameTransformerDelegate::
    RtpVideoStreamReceiverFrameTransformerDelegate(
        RtpVideoFrameReceiver* receiver,
        Clock* clock,
        rtc::scoped_refptr<FrameTransformerInterface> frame_transformer,
        rtc::Thread* network_thread,
        uint32_t ssrc)
    : receiver_(receiver),
      frame_transformer_(std::move(frame_transformer)),
      network_thread_(network_thread),
      ssrc_(ssrc),
      clock_(clock) {}

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
    std::unique_ptr<RtpFrameObject> frame) {
  RTC_DCHECK_RUN_ON(&network_sequence_checker_);
  if (short_circuit_) {
    // Just pass the frame straight back.
    receiver_->ManageFrame(std::move(frame));
  } else {
    frame_transformer_->Transform(
        std::make_unique<TransformableVideoReceiverFrame>(std::move(frame),
                                                          ssrc_, receiver_));
  }
}

void RtpVideoStreamReceiverFrameTransformerDelegate::OnTransformedFrame(
    std::unique_ptr<TransformableFrameInterface> frame) {
  rtc::scoped_refptr<RtpVideoStreamReceiverFrameTransformerDelegate> delegate(
      this);
  network_thread_->PostTask(
      [delegate = std::move(delegate), frame = std::move(frame)]() mutable {
        delegate->ManageFrame(std::move(frame));
      });
}

void RtpVideoStreamReceiverFrameTransformerDelegate::StartShortCircuiting() {
  rtc::scoped_refptr<RtpVideoStreamReceiverFrameTransformerDelegate> delegate(
      this);
  network_thread_->PostTask([delegate = std::move(delegate)]() mutable {
    delegate->StartShortCircuitingOnNetworkSequence();
  });
}

void RtpVideoStreamReceiverFrameTransformerDelegate::
    StartShortCircuitingOnNetworkSequence() {
  RTC_DCHECK_RUN_ON(&network_sequence_checker_);
  short_circuit_ = true;
}

void RtpVideoStreamReceiverFrameTransformerDelegate::ManageFrame(
    std::unique_ptr<TransformableFrameInterface> frame) {
  RTC_DCHECK_RUN_ON(&network_sequence_checker_);
  if (!receiver_)
    return;
  if (frame->GetDirection() ==
      TransformableFrameInterface::Direction::kReceiver) {
    auto transformed_frame = absl::WrapUnique(
        static_cast<TransformableVideoReceiverFrame*>(frame.release()));
    auto frame_receiver = transformed_frame->Receiver();
    std::unique_ptr<RtpFrameObject> frame_object =
        std::move(*transformed_frame).ExtractFrame();
    if (frame_receiver != receiver_) {
      // This frame was received by a different RtpReceiver instance, so has
      // first and last sequence numbers which will be meaningless to our
      // receiver_. Work around this by using the frame id as a surrogate value,
      // same as when given a Sender frame below.

      // TODO(https://crbug.com/1250638): Change what happens after the encoded
      // insertable stream insertion to not require RTP data.
      frame_object->SetFirstSeqNum(frame_object->Id());
      frame_object->SetLastSeqNum(frame_object->Id());
    }
    receiver_->ManageFrame(std::move(frame_object));
  } else {
    RTC_CHECK_EQ(frame->GetDirection(),
                 TransformableFrameInterface::Direction::kSender);
    // This frame is actually an frame encoded locally, to be sent, but has been
    // fed back into this receiver's insertable stream writer.
    // Create a reasonable RtpFrameObject as if this frame had been received
    // over RTP, reusing the frameId as an analog for the RTP sequence number,
    // and handle it as if it had been received.
    // TODO(https://crbug.com/1250638): Rewrite the receiver's codepaths after
    // this transform to be transport-agnostic and not need a faked rtp
    // sequence number.

    auto transformed_frame = absl::WrapUnique(
        static_cast<TransformableVideoFrameInterface*>(frame.release()));
    VideoFrameMetadata metadata = transformed_frame->Metadata();
    RTPVideoHeader video_header = RTPVideoHeader::FromMetadata(metadata);
    VideoSendTiming timing;
    rtc::ArrayView<const uint8_t> data = transformed_frame->GetData();
    int64_t receive_time = clock_->CurrentTime().ms();
    receiver_->ManageFrame(std::make_unique<RtpFrameObject>(
        /*first_seq_num=*/metadata.GetFrameId().value_or(0),
        /*last_seq_num=*/metadata.GetFrameId().value_or(0),
        /*markerBit=*/video_header.is_last_frame_in_picture,
        /*times_nacked=*/0,
        /*first_packet_received_time=*/receive_time,
        /*last_packet_received_time=*/receive_time,
        /*rtp_timestamp=*/transformed_frame->GetTimestamp(),
        /*ntp_time_ms=*/0, timing, transformed_frame->GetPayloadType(),
        metadata.GetCodec(), metadata.GetRotation(), metadata.GetContentType(),
        video_header, video_header.color_space, RtpPacketInfos(),
        EncodedImageBuffer::Create(data.data(), data.size())));
  }
}

}  // namespace webrtc
