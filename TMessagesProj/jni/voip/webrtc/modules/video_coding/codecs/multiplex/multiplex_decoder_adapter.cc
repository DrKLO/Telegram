/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/codecs/multiplex/include/multiplex_decoder_adapter.h"

#include "api/environment/environment.h"
#include "api/video/encoded_image.h"
#include "api/video/i420_buffer.h"
#include "api/video/video_frame_buffer.h"
#include "common_video/include/video_frame_buffer.h"
#include "common_video/libyuv/include/webrtc_libyuv.h"
#include "modules/video_coding/codecs/multiplex/include/augmented_video_frame_buffer.h"
#include "modules/video_coding/codecs/multiplex/multiplex_encoded_image_packer.h"
#include "rtc_base/logging.h"

namespace webrtc {

class MultiplexDecoderAdapter::AdapterDecodedImageCallback
    : public webrtc::DecodedImageCallback {
 public:
  AdapterDecodedImageCallback(webrtc::MultiplexDecoderAdapter* adapter,
                              AlphaCodecStream stream_idx)
      : adapter_(adapter), stream_idx_(stream_idx) {}

  void Decoded(VideoFrame& decoded_image,
               absl::optional<int32_t> decode_time_ms,
               absl::optional<uint8_t> qp) override {
    if (!adapter_)
      return;
    adapter_->Decoded(stream_idx_, &decoded_image, decode_time_ms, qp);
  }
  int32_t Decoded(VideoFrame& decoded_image) override {
    RTC_DCHECK_NOTREACHED();
    return WEBRTC_VIDEO_CODEC_OK;
  }
  int32_t Decoded(VideoFrame& decoded_image, int64_t decode_time_ms) override {
    RTC_DCHECK_NOTREACHED();
    return WEBRTC_VIDEO_CODEC_OK;
  }

 private:
  MultiplexDecoderAdapter* adapter_;
  const AlphaCodecStream stream_idx_;
};

struct MultiplexDecoderAdapter::DecodedImageData {
  explicit DecodedImageData(AlphaCodecStream stream_idx)
      : stream_idx_(stream_idx),
        decoded_image_(
            VideoFrame::Builder()
                .set_video_frame_buffer(
                    I420Buffer::Create(1 /* width */, 1 /* height */))
                .set_timestamp_rtp(0)
                .set_timestamp_us(0)
                .set_rotation(kVideoRotation_0)
                .build()) {
    RTC_DCHECK_EQ(kAXXStream, stream_idx);
  }
  DecodedImageData(AlphaCodecStream stream_idx,
                   const VideoFrame& decoded_image,
                   const absl::optional<int32_t>& decode_time_ms,
                   const absl::optional<uint8_t>& qp)
      : stream_idx_(stream_idx),
        decoded_image_(decoded_image),
        decode_time_ms_(decode_time_ms),
        qp_(qp) {}

  DecodedImageData() = delete;
  DecodedImageData(const DecodedImageData&) = delete;
  DecodedImageData& operator=(const DecodedImageData&) = delete;

  const AlphaCodecStream stream_idx_;
  VideoFrame decoded_image_;
  const absl::optional<int32_t> decode_time_ms_;
  const absl::optional<uint8_t> qp_;
};

struct MultiplexDecoderAdapter::AugmentingData {
  AugmentingData(std::unique_ptr<uint8_t[]> augmenting_data, uint16_t data_size)
      : data_(std::move(augmenting_data)), size_(data_size) {}
  AugmentingData() = delete;
  AugmentingData(const AugmentingData&) = delete;
  AugmentingData& operator=(const AugmentingData&) = delete;

  std::unique_ptr<uint8_t[]> data_;
  const uint16_t size_;
};

MultiplexDecoderAdapter::MultiplexDecoderAdapter(
    const Environment& env,
    VideoDecoderFactory* factory,
    const SdpVideoFormat& associated_format,
    bool supports_augmenting_data)
    : env_(env),
      factory_(factory),
      associated_format_(associated_format),
      supports_augmenting_data_(supports_augmenting_data) {}

MultiplexDecoderAdapter::~MultiplexDecoderAdapter() {
  Release();
}

bool MultiplexDecoderAdapter::Configure(const Settings& settings) {
  RTC_DCHECK_EQ(settings.codec_type(), kVideoCodecMultiplex);
  Settings associated_settings = settings;
  associated_settings.set_codec_type(
      PayloadStringToCodecType(associated_format_.name));
  for (size_t i = 0; i < kAlphaCodecStreams; ++i) {
    std::unique_ptr<VideoDecoder> decoder =
        factory_->Create(env_, associated_format_);
    if (!decoder->Configure(associated_settings)) {
      return false;
    }
    adapter_callbacks_.emplace_back(
        new MultiplexDecoderAdapter::AdapterDecodedImageCallback(
            this, static_cast<AlphaCodecStream>(i)));
    decoder->RegisterDecodeCompleteCallback(adapter_callbacks_.back().get());
    decoders_.emplace_back(std::move(decoder));
  }
  return true;
}

int32_t MultiplexDecoderAdapter::Decode(const EncodedImage& input_image,
                                        int64_t render_time_ms) {
  MultiplexImage image = MultiplexEncodedImagePacker::Unpack(input_image);

  if (supports_augmenting_data_) {
    RTC_DCHECK(decoded_augmenting_data_.find(input_image.RtpTimestamp()) ==
               decoded_augmenting_data_.end());
    decoded_augmenting_data_.emplace(
        std::piecewise_construct,
        std::forward_as_tuple(input_image.RtpTimestamp()),
        std::forward_as_tuple(std::move(image.augmenting_data),
                              image.augmenting_data_size));
  }

  if (image.component_count == 1) {
    RTC_DCHECK(decoded_data_.find(input_image.RtpTimestamp()) ==
               decoded_data_.end());
    decoded_data_.emplace(std::piecewise_construct,
                          std::forward_as_tuple(input_image.RtpTimestamp()),
                          std::forward_as_tuple(kAXXStream));
  }
  int32_t rv = 0;
  for (size_t i = 0; i < image.image_components.size(); i++) {
    rv = decoders_[image.image_components[i].component_index]->Decode(
        image.image_components[i].encoded_image, render_time_ms);
    if (rv != WEBRTC_VIDEO_CODEC_OK)
      return rv;
  }
  return rv;
}

int32_t MultiplexDecoderAdapter::RegisterDecodeCompleteCallback(
    DecodedImageCallback* callback) {
  decoded_complete_callback_ = callback;
  return WEBRTC_VIDEO_CODEC_OK;
}

int32_t MultiplexDecoderAdapter::Release() {
  for (auto& decoder : decoders_) {
    const int32_t rv = decoder->Release();
    if (rv)
      return rv;
  }
  decoders_.clear();
  adapter_callbacks_.clear();
  return WEBRTC_VIDEO_CODEC_OK;
}

void MultiplexDecoderAdapter::Decoded(AlphaCodecStream stream_idx,
                                      VideoFrame* decoded_image,
                                      absl::optional<int32_t> decode_time_ms,
                                      absl::optional<uint8_t> qp) {
  const auto& other_decoded_data_it =
      decoded_data_.find(decoded_image->timestamp());
  const auto& augmenting_data_it =
      decoded_augmenting_data_.find(decoded_image->timestamp());
  const bool has_augmenting_data =
      augmenting_data_it != decoded_augmenting_data_.end();
  if (other_decoded_data_it != decoded_data_.end()) {
    uint16_t augmenting_data_size =
        has_augmenting_data ? augmenting_data_it->second.size_ : 0;
    std::unique_ptr<uint8_t[]> augmenting_data =
        has_augmenting_data ? std::move(augmenting_data_it->second.data_)
                            : nullptr;
    auto& other_image_data = other_decoded_data_it->second;
    if (stream_idx == kYUVStream) {
      RTC_DCHECK_EQ(kAXXStream, other_image_data.stream_idx_);
      MergeAlphaImages(decoded_image, decode_time_ms, qp,
                       &other_image_data.decoded_image_,
                       other_image_data.decode_time_ms_, other_image_data.qp_,
                       std::move(augmenting_data), augmenting_data_size);
    } else {
      RTC_DCHECK_EQ(kYUVStream, other_image_data.stream_idx_);
      RTC_DCHECK_EQ(kAXXStream, stream_idx);
      MergeAlphaImages(&other_image_data.decoded_image_,
                       other_image_data.decode_time_ms_, other_image_data.qp_,
                       decoded_image, decode_time_ms, qp,
                       std::move(augmenting_data), augmenting_data_size);
    }
    decoded_data_.erase(decoded_data_.begin(), other_decoded_data_it);
    if (has_augmenting_data) {
      decoded_augmenting_data_.erase(decoded_augmenting_data_.begin(),
                                     augmenting_data_it);
    }
    return;
  }
  RTC_DCHECK(decoded_data_.find(decoded_image->timestamp()) ==
             decoded_data_.end());
  decoded_data_.emplace(
      std::piecewise_construct,
      std::forward_as_tuple(decoded_image->timestamp()),
      std::forward_as_tuple(stream_idx, *decoded_image, decode_time_ms, qp));
}

void MultiplexDecoderAdapter::MergeAlphaImages(
    VideoFrame* decoded_image,
    const absl::optional<int32_t>& decode_time_ms,
    const absl::optional<uint8_t>& qp,
    VideoFrame* alpha_decoded_image,
    const absl::optional<int32_t>& alpha_decode_time_ms,
    const absl::optional<uint8_t>& alpha_qp,
    std::unique_ptr<uint8_t[]> augmenting_data,
    uint16_t augmenting_data_length) {
  rtc::scoped_refptr<VideoFrameBuffer> merged_buffer;
  if (!alpha_decoded_image->timestamp()) {
    merged_buffer = decoded_image->video_frame_buffer();
  } else {
    rtc::scoped_refptr<webrtc::I420BufferInterface> yuv_buffer =
        decoded_image->video_frame_buffer()->ToI420();
    rtc::scoped_refptr<webrtc::I420BufferInterface> alpha_buffer =
        alpha_decoded_image->video_frame_buffer()->ToI420();
    RTC_DCHECK_EQ(yuv_buffer->width(), alpha_buffer->width());
    RTC_DCHECK_EQ(yuv_buffer->height(), alpha_buffer->height());
    merged_buffer = WrapI420ABuffer(
        yuv_buffer->width(), yuv_buffer->height(), yuv_buffer->DataY(),
        yuv_buffer->StrideY(), yuv_buffer->DataU(), yuv_buffer->StrideU(),
        yuv_buffer->DataV(), yuv_buffer->StrideV(), alpha_buffer->DataY(),
        alpha_buffer->StrideY(),
        // To keep references alive.
        [yuv_buffer, alpha_buffer] {});
  }
  if (supports_augmenting_data_) {
    merged_buffer = rtc::make_ref_counted<AugmentedVideoFrameBuffer>(
        merged_buffer, std::move(augmenting_data), augmenting_data_length);
  }

  VideoFrame merged_image = VideoFrame::Builder()
                                .set_video_frame_buffer(merged_buffer)
                                .set_timestamp_rtp(decoded_image->timestamp())
                                .set_timestamp_us(0)
                                .set_rotation(decoded_image->rotation())
                                .set_id(decoded_image->id())
                                .set_packet_infos(decoded_image->packet_infos())
                                .build();
  decoded_complete_callback_->Decoded(merged_image, decode_time_ms, qp);
}

}  // namespace webrtc
