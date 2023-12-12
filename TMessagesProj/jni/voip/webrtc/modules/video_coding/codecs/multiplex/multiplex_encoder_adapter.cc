/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/codecs/multiplex/include/multiplex_encoder_adapter.h"

#include <cstring>

#include "api/video/encoded_image.h"
#include "api/video_codecs/video_encoder.h"
#include "common_video/include/video_frame_buffer.h"
#include "common_video/libyuv/include/webrtc_libyuv.h"
#include "media/base/video_common.h"
#include "modules/video_coding/codecs/multiplex/include/augmented_video_frame_buffer.h"
#include "rtc_base/logging.h"

namespace webrtc {

// Callback wrapper that helps distinguish returned results from `encoders_`
// instances.
class MultiplexEncoderAdapter::AdapterEncodedImageCallback
    : public webrtc::EncodedImageCallback {
 public:
  AdapterEncodedImageCallback(webrtc::MultiplexEncoderAdapter* adapter,
                              AlphaCodecStream stream_idx)
      : adapter_(adapter), stream_idx_(stream_idx) {}

  EncodedImageCallback::Result OnEncodedImage(
      const EncodedImage& encoded_image,
      const CodecSpecificInfo* codec_specific_info) override {
    if (!adapter_)
      return Result(Result::OK);
    return adapter_->OnEncodedImage(stream_idx_, encoded_image,
                                    codec_specific_info);
  }

 private:
  MultiplexEncoderAdapter* adapter_;
  const AlphaCodecStream stream_idx_;
};

MultiplexEncoderAdapter::MultiplexEncoderAdapter(
    VideoEncoderFactory* factory,
    const SdpVideoFormat& associated_format,
    bool supports_augmented_data)
    : factory_(factory),
      associated_format_(associated_format),
      encoded_complete_callback_(nullptr),
      key_frame_interval_(0),
      supports_augmented_data_(supports_augmented_data) {}

MultiplexEncoderAdapter::~MultiplexEncoderAdapter() {
  Release();
}

void MultiplexEncoderAdapter::SetFecControllerOverride(
    FecControllerOverride* fec_controller_override) {
  // Ignored.
}

int MultiplexEncoderAdapter::InitEncode(
    const VideoCodec* inst,
    const VideoEncoder::Settings& settings) {
  const size_t buffer_size =
      CalcBufferSize(VideoType::kI420, inst->width, inst->height);
  multiplex_dummy_planes_.resize(buffer_size);
  // It is more expensive to encode 0x00, so use 0x80 instead.
  std::fill(multiplex_dummy_planes_.begin(), multiplex_dummy_planes_.end(),
            0x80);

  RTC_DCHECK_EQ(kVideoCodecMultiplex, inst->codecType);
  VideoCodec video_codec = *inst;
  video_codec.codecType = PayloadStringToCodecType(associated_format_.name);

  // Take over the key frame interval at adapter level, because we have to
  // sync the key frames for both sub-encoders.
  switch (video_codec.codecType) {
    case kVideoCodecVP8:
      key_frame_interval_ = video_codec.VP8()->keyFrameInterval;
      video_codec.VP8()->keyFrameInterval = 0;
      break;
    case kVideoCodecVP9:
      key_frame_interval_ = video_codec.VP9()->keyFrameInterval;
      video_codec.VP9()->keyFrameInterval = 0;
      break;
    case kVideoCodecH264:
      key_frame_interval_ = video_codec.H264()->keyFrameInterval;
      video_codec.H264()->keyFrameInterval = 0;
      break;
#ifndef DISABLE_H265
    case kVideoCodecH265:
      key_frame_interval_ = video_codec.H265()->keyFrameInterval;
      video_codec.H265()->keyFrameInterval = 0;
      break;
#endif
    default:
      break;
  }

  encoder_info_ = EncoderInfo();
  encoder_info_.implementation_name = "MultiplexEncoderAdapter (";
  encoder_info_.requested_resolution_alignment = 1;
  encoder_info_.apply_alignment_to_all_simulcast_layers = false;
  // This needs to be false so that we can do the split in Encode().
  encoder_info_.supports_native_handle = false;

  for (size_t i = 0; i < kAlphaCodecStreams; ++i) {
    std::unique_ptr<VideoEncoder> encoder =
        factory_->CreateVideoEncoder(associated_format_);
    const int rv = encoder->InitEncode(&video_codec, settings);
    if (rv) {
      RTC_LOG(LS_ERROR) << "Failed to create multiplex codec index " << i;
      return rv;
    }
    adapter_callbacks_.emplace_back(new AdapterEncodedImageCallback(
        this, static_cast<AlphaCodecStream>(i)));
    encoder->RegisterEncodeCompleteCallback(adapter_callbacks_.back().get());

    const EncoderInfo& encoder_impl_info = encoder->GetEncoderInfo();
    encoder_info_.implementation_name += encoder_impl_info.implementation_name;
    if (i != kAlphaCodecStreams - 1) {
      encoder_info_.implementation_name += ", ";
    }
    // Uses hardware support if any of the encoders uses it.
    // For example, if we are having issues with down-scaling due to
    // pipelining delay in HW encoders we need higher encoder usage
    // thresholds in CPU adaptation.
    if (i == 0) {
      encoder_info_.is_hardware_accelerated =
          encoder_impl_info.is_hardware_accelerated;
    } else {
      encoder_info_.is_hardware_accelerated |=
          encoder_impl_info.is_hardware_accelerated;
    }

    encoder_info_.requested_resolution_alignment = cricket::LeastCommonMultiple(
        encoder_info_.requested_resolution_alignment,
        encoder_impl_info.requested_resolution_alignment);

    if (encoder_impl_info.apply_alignment_to_all_simulcast_layers) {
      encoder_info_.apply_alignment_to_all_simulcast_layers = true;
    }

    encoders_.emplace_back(std::move(encoder));
  }
  encoder_info_.implementation_name += ")";

  return WEBRTC_VIDEO_CODEC_OK;
}

int MultiplexEncoderAdapter::Encode(
    const VideoFrame& input_image,
    const std::vector<VideoFrameType>* frame_types) {
  if (!encoded_complete_callback_) {
    return WEBRTC_VIDEO_CODEC_UNINITIALIZED;
  }

  // The input image is forwarded as-is, unless it is a native buffer and
  // `supports_augmented_data_` is true in which case we need to map it in order
  // to access the underlying AugmentedVideoFrameBuffer.
  VideoFrame forwarded_image = input_image;
  if (supports_augmented_data_ &&
      forwarded_image.video_frame_buffer()->type() ==
          VideoFrameBuffer::Type::kNative) {
    auto info = GetEncoderInfo();
    rtc::scoped_refptr<VideoFrameBuffer> mapped_buffer =
        forwarded_image.video_frame_buffer()->GetMappedFrameBuffer(
            info.preferred_pixel_formats);
    if (!mapped_buffer) {
      // Unable to map the buffer.
      return WEBRTC_VIDEO_CODEC_ERROR;
    }
    forwarded_image.set_video_frame_buffer(std::move(mapped_buffer));
  }

  std::vector<VideoFrameType> adjusted_frame_types;
  if (key_frame_interval_ > 0 && picture_index_ % key_frame_interval_ == 0) {
    adjusted_frame_types.push_back(VideoFrameType::kVideoFrameKey);
  } else {
    adjusted_frame_types.push_back(VideoFrameType::kVideoFrameDelta);
  }
  const bool has_alpha = forwarded_image.video_frame_buffer()->type() ==
                         VideoFrameBuffer::Type::kI420A;
  std::unique_ptr<uint8_t[]> augmenting_data = nullptr;
  uint16_t augmenting_data_length = 0;
  AugmentedVideoFrameBuffer* augmented_video_frame_buffer = nullptr;
  if (supports_augmented_data_) {
    augmented_video_frame_buffer = static_cast<AugmentedVideoFrameBuffer*>(
        forwarded_image.video_frame_buffer().get());
    augmenting_data_length =
        augmented_video_frame_buffer->GetAugmentingDataSize();
    augmenting_data =
        std::unique_ptr<uint8_t[]>(new uint8_t[augmenting_data_length]);
    memcpy(augmenting_data.get(),
           augmented_video_frame_buffer->GetAugmentingData(),
           augmenting_data_length);
    augmenting_data_size_ = augmenting_data_length;
  }

  {
    MutexLock lock(&mutex_);
    stashed_images_.emplace(
        std::piecewise_construct,
        std::forward_as_tuple(forwarded_image.timestamp()),
        std::forward_as_tuple(
            picture_index_, has_alpha ? kAlphaCodecStreams : 1,
            std::move(augmenting_data), augmenting_data_length));
  }

  ++picture_index_;

  // Encode YUV
  int rv =
      encoders_[kYUVStream]->Encode(forwarded_image, &adjusted_frame_types);

  // If we do not receive an alpha frame, we send a single frame for this
  // `picture_index_`. The receiver will receive `frame_count` as 1 which
  // specifies this case.
  if (rv || !has_alpha)
    return rv;

  // Encode AXX
  rtc::scoped_refptr<VideoFrameBuffer> frame_buffer =
      supports_augmented_data_
          ? augmented_video_frame_buffer->GetVideoFrameBuffer()
          : forwarded_image.video_frame_buffer();
  const I420ABufferInterface* yuva_buffer = frame_buffer->GetI420A();
  rtc::scoped_refptr<I420BufferInterface> alpha_buffer =
      WrapI420Buffer(forwarded_image.width(), forwarded_image.height(),
                     yuva_buffer->DataA(), yuva_buffer->StrideA(),
                     multiplex_dummy_planes_.data(), yuva_buffer->StrideU(),
                     multiplex_dummy_planes_.data(), yuva_buffer->StrideV(),
                     // To keep reference alive.
                     [frame_buffer] {});
  VideoFrame alpha_image =
      VideoFrame::Builder()
          .set_video_frame_buffer(alpha_buffer)
          .set_timestamp_rtp(forwarded_image.timestamp())
          .set_timestamp_ms(forwarded_image.render_time_ms())
          .set_rotation(forwarded_image.rotation())
          .set_id(forwarded_image.id())
          .set_packet_infos(forwarded_image.packet_infos())
          .build();
  rv = encoders_[kAXXStream]->Encode(alpha_image, &adjusted_frame_types);
  return rv;
}

int MultiplexEncoderAdapter::RegisterEncodeCompleteCallback(
    EncodedImageCallback* callback) {
  encoded_complete_callback_ = callback;
  return WEBRTC_VIDEO_CODEC_OK;
}

void MultiplexEncoderAdapter::SetRates(
    const RateControlParameters& parameters) {
  VideoBitrateAllocation bitrate_allocation(parameters.bitrate);
  bitrate_allocation.SetBitrate(
      0, 0, parameters.bitrate.GetBitrate(0, 0) - augmenting_data_size_);
  for (auto& encoder : encoders_) {
    // TODO(emircan): `framerate` is used to calculate duration in encoder
    // instances. We report the total frame rate to keep real time for now.
    // Remove this after refactoring duration logic.
    encoder->SetRates(RateControlParameters(
        bitrate_allocation,
        static_cast<uint32_t>(encoders_.size() * parameters.framerate_fps),
        parameters.bandwidth_allocation -
            DataRate::BitsPerSec(augmenting_data_size_)));
  }
}

void MultiplexEncoderAdapter::OnPacketLossRateUpdate(float packet_loss_rate) {
  for (auto& encoder : encoders_) {
    encoder->OnPacketLossRateUpdate(packet_loss_rate);
  }
}

void MultiplexEncoderAdapter::OnRttUpdate(int64_t rtt_ms) {
  for (auto& encoder : encoders_) {
    encoder->OnRttUpdate(rtt_ms);
  }
}

void MultiplexEncoderAdapter::OnLossNotification(
    const LossNotification& loss_notification) {
  for (auto& encoder : encoders_) {
    encoder->OnLossNotification(loss_notification);
  }
}

int MultiplexEncoderAdapter::Release() {
  for (auto& encoder : encoders_) {
    const int rv = encoder->Release();
    if (rv)
      return rv;
  }
  encoders_.clear();
  adapter_callbacks_.clear();
  MutexLock lock(&mutex_);
  stashed_images_.clear();

  return WEBRTC_VIDEO_CODEC_OK;
}

VideoEncoder::EncoderInfo MultiplexEncoderAdapter::GetEncoderInfo() const {
  return encoder_info_;
}

EncodedImageCallback::Result MultiplexEncoderAdapter::OnEncodedImage(
    AlphaCodecStream stream_idx,
    const EncodedImage& encodedImage,
    const CodecSpecificInfo* codecSpecificInfo) {
  // Save the image
  MultiplexImageComponent image_component;
  image_component.component_index = stream_idx;
  image_component.codec_type =
      PayloadStringToCodecType(associated_format_.name);
  image_component.encoded_image = encodedImage;

  MutexLock lock(&mutex_);
  const auto& stashed_image_itr =
      stashed_images_.find(encodedImage.Timestamp());
  const auto& stashed_image_next_itr = std::next(stashed_image_itr, 1);
  RTC_DCHECK(stashed_image_itr != stashed_images_.end());
  MultiplexImage& stashed_image = stashed_image_itr->second;
  const uint8_t frame_count = stashed_image.component_count;

  stashed_image.image_components.push_back(image_component);

  if (stashed_image.image_components.size() == frame_count) {
    // Complete case
    for (auto iter = stashed_images_.begin();
         iter != stashed_images_.end() && iter != stashed_image_next_itr;
         iter++) {
      // No image at all, skip.
      if (iter->second.image_components.size() == 0)
        continue;

      // We have to send out those stashed frames, otherwise the delta frame
      // dependency chain is broken.
      combined_image_ =
          MultiplexEncodedImagePacker::PackAndRelease(iter->second);

      CodecSpecificInfo codec_info = *codecSpecificInfo;
      codec_info.codecType = kVideoCodecMultiplex;
      encoded_complete_callback_->OnEncodedImage(combined_image_, &codec_info);
    }

    stashed_images_.erase(stashed_images_.begin(), stashed_image_next_itr);
  }
  return EncodedImageCallback::Result(EncodedImageCallback::Result::OK);
}

}  // namespace webrtc
