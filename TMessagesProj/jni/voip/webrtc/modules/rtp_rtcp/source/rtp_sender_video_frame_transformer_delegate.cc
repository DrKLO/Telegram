/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtp_sender_video_frame_transformer_delegate.h"

#include <string>
#include <utility>
#include <vector>

#include "api/sequence_checker.h"
#include "api/task_queue/task_queue_factory.h"
#include "modules/rtp_rtcp/source/rtp_descriptor_authentication.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace {

// Using a reasonable default of 10ms for the retransmission delay for frames
// not coming from this sender's encoder. This is usually taken from an
// estimate of the RTT of the link,so 10ms should be a reasonable estimate for
// frames being re-transmitted to a peer, probably on the same network.
const TimeDelta kDefaultRetransmissionsTime = TimeDelta::Millis(10);

class TransformableVideoSenderFrame : public TransformableVideoFrameInterface {
 public:
  TransformableVideoSenderFrame(const EncodedImage& encoded_image,
                                const RTPVideoHeader& video_header,
                                int payload_type,
                                absl::optional<VideoCodecType> codec_type,
                                uint32_t rtp_timestamp,
                                TimeDelta expected_retransmission_time,
                                uint32_t ssrc,
                                std::vector<uint32_t> csrcs)
      : encoded_data_(encoded_image.GetEncodedData()),
        pre_transform_payload_size_(encoded_image.size()),
        header_(video_header),
        frame_type_(encoded_image._frameType),
        payload_type_(payload_type),
        codec_type_(codec_type),
        timestamp_(rtp_timestamp),
        capture_time_(encoded_image.CaptureTime()),
        capture_time_identifier_(encoded_image.CaptureTimeIdentifier()),
        expected_retransmission_time_(expected_retransmission_time),
        ssrc_(ssrc),
        csrcs_(csrcs) {
    RTC_DCHECK_GE(payload_type_, 0);
    RTC_DCHECK_LE(payload_type_, 127);
  }

  ~TransformableVideoSenderFrame() override = default;

  // Implements TransformableVideoFrameInterface.
  rtc::ArrayView<const uint8_t> GetData() const override {
    return *encoded_data_;
  }

  void SetData(rtc::ArrayView<const uint8_t> data) override {
    encoded_data_ = EncodedImageBuffer::Create(data.data(), data.size());
  }

  size_t GetPreTransformPayloadSize() const {
    return pre_transform_payload_size_;
  }

  uint32_t GetTimestamp() const override { return timestamp_; }
  void SetRTPTimestamp(uint32_t timestamp) override { timestamp_ = timestamp; }

  uint32_t GetSsrc() const override { return ssrc_; }

  bool IsKeyFrame() const override {
    return frame_type_ == VideoFrameType::kVideoFrameKey;
  }

  VideoFrameMetadata Metadata() const override {
    VideoFrameMetadata metadata = header_.GetAsMetadata();
    metadata.SetSsrc(ssrc_);
    metadata.SetCsrcs(csrcs_);
    return metadata;
  }

  void SetMetadata(const VideoFrameMetadata& metadata) override {
    header_.SetFromMetadata(metadata);
    ssrc_ = metadata.GetSsrc();
    csrcs_ = metadata.GetCsrcs();
  }

  const RTPVideoHeader& GetHeader() const { return header_; }
  uint8_t GetPayloadType() const override { return payload_type_; }
  absl::optional<VideoCodecType> GetCodecType() const { return codec_type_; }
  Timestamp GetCaptureTime() const { return capture_time_; }
  absl::optional<Timestamp> GetCaptureTimeIdentifier() const override {
    return capture_time_identifier_;
  }

  TimeDelta GetExpectedRetransmissionTime() const {
    return expected_retransmission_time_;
  }

  Direction GetDirection() const override { return Direction::kSender; }
  std::string GetMimeType() const override {
    if (!codec_type_.has_value()) {
      return "video/x-unknown";
    }
    std::string mime_type = "video/";
    return mime_type + CodecTypeToPayloadString(*codec_type_);
  }

 private:
  rtc::scoped_refptr<EncodedImageBufferInterface> encoded_data_;
  const size_t pre_transform_payload_size_;
  RTPVideoHeader header_;
  const VideoFrameType frame_type_;
  const uint8_t payload_type_;
  const absl::optional<VideoCodecType> codec_type_ = absl::nullopt;
  uint32_t timestamp_;
  const Timestamp capture_time_;
  const absl::optional<Timestamp> capture_time_identifier_;
  const TimeDelta expected_retransmission_time_;

  uint32_t ssrc_;
  std::vector<uint32_t> csrcs_;
};
}  // namespace

RTPSenderVideoFrameTransformerDelegate::RTPSenderVideoFrameTransformerDelegate(
    RTPVideoFrameSenderInterface* sender,
    rtc::scoped_refptr<FrameTransformerInterface> frame_transformer,
    uint32_t ssrc,
    TaskQueueFactory* task_queue_factory)
    : sender_(sender),
      frame_transformer_(std::move(frame_transformer)),
      ssrc_(ssrc),
      transformation_queue_(task_queue_factory->CreateTaskQueue(
          "video_frame_transformer",
          TaskQueueFactory::Priority::NORMAL)) {}

void RTPSenderVideoFrameTransformerDelegate::Init() {
  frame_transformer_->RegisterTransformedFrameSinkCallback(
      rtc::scoped_refptr<TransformedFrameCallback>(this), ssrc_);
}

bool RTPSenderVideoFrameTransformerDelegate::TransformFrame(
    int payload_type,
    absl::optional<VideoCodecType> codec_type,
    uint32_t rtp_timestamp,
    const EncodedImage& encoded_image,
    RTPVideoHeader video_header,
    TimeDelta expected_retransmission_time) {
  {
    MutexLock lock(&sender_lock_);
    if (short_circuit_) {
      sender_->SendVideo(payload_type, codec_type, rtp_timestamp,
                         encoded_image.CaptureTime(),
                         *encoded_image.GetEncodedData(), encoded_image.size(),
                         video_header, expected_retransmission_time,
                         /*csrcs=*/{});
      return true;
    }
  }
  frame_transformer_->Transform(std::make_unique<TransformableVideoSenderFrame>(
      encoded_image, video_header, payload_type, codec_type, rtp_timestamp,
      expected_retransmission_time, ssrc_,
      /*csrcs=*/std::vector<uint32_t>()));
  return true;
}

void RTPSenderVideoFrameTransformerDelegate::OnTransformedFrame(
    std::unique_ptr<TransformableFrameInterface> frame) {
  MutexLock lock(&sender_lock_);

  if (!sender_) {
    return;
  }
  rtc::scoped_refptr<RTPSenderVideoFrameTransformerDelegate> delegate(this);
  transformation_queue_->PostTask(
      [delegate = std::move(delegate), frame = std::move(frame)]() mutable {
        RTC_DCHECK_RUN_ON(delegate->transformation_queue_.get());
        delegate->SendVideo(std::move(frame));
      });
}

void RTPSenderVideoFrameTransformerDelegate::StartShortCircuiting() {
  MutexLock lock(&sender_lock_);
  short_circuit_ = true;
}

void RTPSenderVideoFrameTransformerDelegate::SendVideo(
    std::unique_ptr<TransformableFrameInterface> transformed_frame) const {
  RTC_DCHECK_RUN_ON(transformation_queue_.get());
  MutexLock lock(&sender_lock_);
  if (!sender_)
    return;
  if (transformed_frame->GetDirection() ==
      TransformableFrameInterface::Direction::kSender) {
    auto* transformed_video_frame =
        static_cast<TransformableVideoSenderFrame*>(transformed_frame.get());
    sender_->SendVideo(transformed_video_frame->GetPayloadType(),
                       transformed_video_frame->GetCodecType(),
                       transformed_video_frame->GetTimestamp(),
                       transformed_video_frame->GetCaptureTime(),
                       transformed_video_frame->GetData(),
                       transformed_video_frame->GetPreTransformPayloadSize(),
                       transformed_video_frame->GetHeader(),
                       transformed_video_frame->GetExpectedRetransmissionTime(),
                       transformed_video_frame->Metadata().GetCsrcs());
  } else {
    auto* transformed_video_frame =
        static_cast<TransformableVideoFrameInterface*>(transformed_frame.get());
    VideoFrameMetadata metadata = transformed_video_frame->Metadata();
    // TODO(bugs.webrtc.org/14708): Use an actual RTT estimate for the
    // retransmission time instead of a const default, in the same way as a
    // locally encoded frame.
    sender_->SendVideo(transformed_video_frame->GetPayloadType(),
                       metadata.GetCodec(),
                       transformed_video_frame->GetTimestamp(),
                       /*capture_time=*/Timestamp::MinusInfinity(),
                       transformed_video_frame->GetData(),
                       transformed_video_frame->GetData().size(),
                       RTPVideoHeader::FromMetadata(metadata),
                       kDefaultRetransmissionsTime, metadata.GetCsrcs());
  }
}

void RTPSenderVideoFrameTransformerDelegate::SetVideoStructureUnderLock(
    const FrameDependencyStructure* video_structure) {
  MutexLock lock(&sender_lock_);
  RTC_CHECK(sender_);
  sender_->SetVideoStructureAfterTransformation(video_structure);
}

void RTPSenderVideoFrameTransformerDelegate::SetVideoLayersAllocationUnderLock(
    VideoLayersAllocation allocation) {
  MutexLock lock(&sender_lock_);
  RTC_CHECK(sender_);
  sender_->SetVideoLayersAllocationAfterTransformation(std::move(allocation));
}

void RTPSenderVideoFrameTransformerDelegate::Reset() {
  frame_transformer_->UnregisterTransformedFrameSinkCallback(ssrc_);
  frame_transformer_ = nullptr;
  {
    MutexLock lock(&sender_lock_);
    sender_ = nullptr;
  }
}

std::unique_ptr<TransformableVideoFrameInterface> CloneSenderVideoFrame(
    TransformableVideoFrameInterface* original) {
  auto encoded_image_buffer = EncodedImageBuffer::Create(
      original->GetData().data(), original->GetData().size());
  EncodedImage encoded_image;
  encoded_image.SetEncodedData(encoded_image_buffer);
  encoded_image._frameType = original->IsKeyFrame()
                                 ? VideoFrameType::kVideoFrameKey
                                 : VideoFrameType::kVideoFrameDelta;
  // TODO(bugs.webrtc.org/14708): Fill in other EncodedImage parameters
  // TODO(bugs.webrtc.org/14708): Use an actual RTT estimate for the
  // retransmission time instead of a const default, in the same way as a
  // locally encoded frame.
  VideoFrameMetadata metadata = original->Metadata();
  RTPVideoHeader new_header = RTPVideoHeader::FromMetadata(metadata);
  return std::make_unique<TransformableVideoSenderFrame>(
      encoded_image, new_header, original->GetPayloadType(), new_header.codec,
      original->GetTimestamp(), kDefaultRetransmissionsTime,
      original->GetSsrc(), metadata.GetCsrcs());
}

}  // namespace webrtc
