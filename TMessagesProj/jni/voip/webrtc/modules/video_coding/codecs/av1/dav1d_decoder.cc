/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/codecs/av1/dav1d_decoder.h"

#include <algorithm>

#include "api/scoped_refptr.h"
#include "api/video/encoded_image.h"
#include "api/video/i420_buffer.h"
#include "common_video/include/video_frame_buffer_pool.h"
#include "modules/video_coding/include/video_error_codes.h"
#include "rtc_base/logging.h"
#include "third_party/dav1d/libdav1d/include/dav1d/dav1d.h"
#include "third_party/libyuv/include/libyuv/convert.h"

namespace webrtc {
namespace {

class Dav1dDecoder : public VideoDecoder {
 public:
  Dav1dDecoder();
  Dav1dDecoder(const Dav1dDecoder&) = delete;
  Dav1dDecoder& operator=(const Dav1dDecoder&) = delete;

  ~Dav1dDecoder() override;

  bool Configure(const Settings& settings) override;
  int32_t Decode(const EncodedImage& encoded_image,
                 bool missing_frames,
                 int64_t render_time_ms) override;
  int32_t RegisterDecodeCompleteCallback(
      DecodedImageCallback* callback) override;
  int32_t Release() override;
  DecoderInfo GetDecoderInfo() const override;
  const char* ImplementationName() const override;

 private:
  VideoFrameBufferPool buffer_pool_;
  Dav1dContext* context_ = nullptr;
  DecodedImageCallback* decode_complete_callback_ = nullptr;
};

class ScopedDav1dData {
 public:
  ~ScopedDav1dData() { dav1d_data_unref(&data_); }

  Dav1dData& Data() { return data_; }

 private:
  Dav1dData data_ = {};
};

class ScopedDav1dPicture {
 public:
  ~ScopedDav1dPicture() { dav1d_picture_unref(&picture_); }

  Dav1dPicture& Picture() { return picture_; }

 private:
  Dav1dPicture picture_ = {};
};

constexpr char kDav1dName[] = "dav1d";

// Calling `dav1d_data_wrap` requires a `free_callback` to be registered.
void NullFreeCallback(const uint8_t* buffer, void* opaque) {}

Dav1dDecoder::Dav1dDecoder()
    : buffer_pool_(/*zero_initialize=*/false, /*max_number_of_buffers=*/150) {}

Dav1dDecoder::~Dav1dDecoder() {
  Release();
}

bool Dav1dDecoder::Configure(const Settings& settings) {
  Dav1dSettings s;
  dav1d_default_settings(&s);

  s.n_threads = std::max(2, settings.number_of_cores());
  s.max_frame_delay = 1;   // For low latency decoding.
  s.all_layers = 0;        // Don't output a frame for every spatial layer.
  s.operating_point = 31;  // Decode all operating points.

  return dav1d_open(&context_, &s) == 0;
}

int32_t Dav1dDecoder::RegisterDecodeCompleteCallback(
    DecodedImageCallback* decode_complete_callback) {
  decode_complete_callback_ = decode_complete_callback;
  return WEBRTC_VIDEO_CODEC_OK;
}

int32_t Dav1dDecoder::Release() {
  dav1d_close(&context_);
  if (context_ != nullptr) {
    return WEBRTC_VIDEO_CODEC_MEMORY;
  }
  buffer_pool_.Release();
  return WEBRTC_VIDEO_CODEC_OK;
}

VideoDecoder::DecoderInfo Dav1dDecoder::GetDecoderInfo() const {
  DecoderInfo info;
  info.implementation_name = kDav1dName;
  info.is_hardware_accelerated = false;
  return info;
}

const char* Dav1dDecoder::ImplementationName() const {
  return kDav1dName;
}

int32_t Dav1dDecoder::Decode(const EncodedImage& encoded_image,
                             bool /*missing_frames*/,
                             int64_t /*render_time_ms*/) {
  if (!context_ || decode_complete_callback_ == nullptr) {
    return WEBRTC_VIDEO_CODEC_UNINITIALIZED;
  }

  ScopedDav1dData scoped_dav1d_data;
  Dav1dData& dav1d_data = scoped_dav1d_data.Data();
  dav1d_data_wrap(&dav1d_data, encoded_image.data(), encoded_image.size(),
                  /*free_callback=*/&NullFreeCallback,
                  /*user_data=*/nullptr);

  if (int decode_res = dav1d_send_data(context_, &dav1d_data)) {
    RTC_LOG(LS_WARNING)
        << "Dav1dDecoder::Decode decoding failed with error code "
        << decode_res;
    return WEBRTC_VIDEO_CODEC_ERROR;
  }

  ScopedDav1dPicture scoped_dav1d_picture;
  Dav1dPicture& dav1d_picture = scoped_dav1d_picture.Picture();
  if (int get_picture_res = dav1d_get_picture(context_, &dav1d_picture)) {
    RTC_LOG(LS_WARNING)
        << "Dav1dDecoder::Decode getting picture failed with error code "
        << get_picture_res;
    return WEBRTC_VIDEO_CODEC_ERROR;
  }

  // Only accept I420 pixel format and 8 bit depth.
  if (dav1d_picture.p.layout != DAV1D_PIXEL_LAYOUT_I420 ||
      dav1d_picture.p.bpc != 8) {
    return WEBRTC_VIDEO_CODEC_ERROR;
  }

  rtc::scoped_refptr<I420Buffer> buffer =
      buffer_pool_.CreateI420Buffer(dav1d_picture.p.w, dav1d_picture.p.h);
  if (!buffer.get()) {
    RTC_LOG(LS_WARNING)
        << "Dav1dDecoder::Decode failed to get frame from the buffer pool.";
    return WEBRTC_VIDEO_CODEC_ERROR;
  }

  uint8_t* y_data = static_cast<uint8_t*>(dav1d_picture.data[0]);
  uint8_t* u_data = static_cast<uint8_t*>(dav1d_picture.data[1]);
  uint8_t* v_data = static_cast<uint8_t*>(dav1d_picture.data[2]);
  int y_stride = dav1d_picture.stride[0];
  int uv_stride = dav1d_picture.stride[1];
  libyuv::I420Copy(y_data, y_stride,                           //
                   u_data, uv_stride,                          //
                   v_data, uv_stride,                          //
                   buffer->MutableDataY(), buffer->StrideY(),  //
                   buffer->MutableDataU(), buffer->StrideU(),  //
                   buffer->MutableDataV(), buffer->StrideV(),  //
                   dav1d_picture.p.w,                          //
                   dav1d_picture.p.h);                         //

  VideoFrame decoded_frame = VideoFrame::Builder()
                                 .set_video_frame_buffer(buffer)
                                 .set_timestamp_rtp(encoded_image.Timestamp())
                                 .set_ntp_time_ms(encoded_image.ntp_time_ms_)
                                 .set_color_space(encoded_image.ColorSpace())
                                 .build();

  decode_complete_callback_->Decoded(decoded_frame, absl::nullopt,
                                     absl::nullopt);

  return WEBRTC_VIDEO_CODEC_OK;
}

}  // namespace

std::unique_ptr<VideoDecoder> CreateDav1dDecoder() {
  return std::make_unique<Dav1dDecoder>();
}

}  // namespace webrtc
