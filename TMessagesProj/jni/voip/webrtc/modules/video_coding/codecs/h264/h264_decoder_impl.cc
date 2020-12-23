/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 *
 */

// Everything declared/defined in this header is only required when WebRTC is
// build with H264 support, please do not move anything out of the
// #ifdef unless needed and tested.
#ifdef WEBRTC_USE_H264

#include "modules/video_coding/codecs/h264/h264_decoder_impl.h"

#include <algorithm>
#include <limits>
#include <memory>

extern "C" {
#include "third_party/ffmpeg/libavcodec/avcodec.h"
#include "third_party/ffmpeg/libavformat/avformat.h"
#include "third_party/ffmpeg/libavutil/imgutils.h"
}  // extern "C"

#include "api/video/color_space.h"
#include "api/video/i010_buffer.h"
#include "api/video/i420_buffer.h"
#include "common_video/include/video_frame_buffer.h"
#include "modules/video_coding/codecs/h264/h264_color_space.h"
#include "rtc_base/checks.h"
#include "rtc_base/keep_ref_until_done.h"
#include "rtc_base/logging.h"
#include "system_wrappers/include/field_trial.h"
#include "system_wrappers/include/metrics.h"
#include "third_party/libyuv/include/libyuv/convert.h"

namespace webrtc {

namespace {

const AVPixelFormat kPixelFormatDefault = AV_PIX_FMT_YUV420P;
const AVPixelFormat kPixelFormatFullRange = AV_PIX_FMT_YUVJ420P;
const size_t kYPlaneIndex = 0;
const size_t kUPlaneIndex = 1;
const size_t kVPlaneIndex = 2;

// Used by histograms. Values of entries should not be changed.
enum H264DecoderImplEvent {
  kH264DecoderEventInit = 0,
  kH264DecoderEventError = 1,
  kH264DecoderEventMax = 16,
};

}  // namespace

int H264DecoderImpl::AVGetBuffer2(AVCodecContext* context,
                                  AVFrame* av_frame,
                                  int flags) {
  // Set in |InitDecode|.
  H264DecoderImpl* decoder = static_cast<H264DecoderImpl*>(context->opaque);
  // DCHECK values set in |InitDecode|.
  RTC_DCHECK(decoder);
  // Necessary capability to be allowed to provide our own buffers.
  RTC_DCHECK(context->codec->capabilities | AV_CODEC_CAP_DR1);

  // Limited or full range YUV420 is expected.
  RTC_CHECK(context->pix_fmt == kPixelFormatDefault ||
            context->pix_fmt == kPixelFormatFullRange);

  // |av_frame->width| and |av_frame->height| are set by FFmpeg. These are the
  // actual image's dimensions and may be different from |context->width| and
  // |context->coded_width| due to reordering.
  int width = av_frame->width;
  int height = av_frame->height;
  // See |lowres|, if used the decoder scales the image by 1/2^(lowres). This
  // has implications on which resolutions are valid, but we don't use it.
  RTC_CHECK_EQ(context->lowres, 0);
  // Adjust the |width| and |height| to values acceptable by the decoder.
  // Without this, FFmpeg may overflow the buffer. If modified, |width| and/or
  // |height| are larger than the actual image and the image has to be cropped
  // (top-left corner) after decoding to avoid visible borders to the right and
  // bottom of the actual image.
  avcodec_align_dimensions(context, &width, &height);

  RTC_CHECK_GE(width, 0);
  RTC_CHECK_GE(height, 0);
  int ret = av_image_check_size(static_cast<unsigned int>(width),
                                static_cast<unsigned int>(height), 0, nullptr);
  if (ret < 0) {
    RTC_LOG(LS_ERROR) << "Invalid picture size " << width << "x" << height;
    decoder->ReportError();
    return ret;
  }

  // The video frame is stored in |frame_buffer|. |av_frame| is FFmpeg's version
  // of a video frame and will be set up to reference |frame_buffer|'s data.

  // FFmpeg expects the initial allocation to be zero-initialized according to
  // http://crbug.com/390941. Our pool is set up to zero-initialize new buffers.
  // TODO(nisse): Delete that feature from the video pool, instead add
  // an explicit call to InitializeData here.
  rtc::scoped_refptr<I420Buffer> frame_buffer =
      decoder->ffmpeg_buffer_pool_.CreateI420Buffer(width, height);

  int y_size = width * height;
  int uv_size = frame_buffer->ChromaWidth() * frame_buffer->ChromaHeight();
  // DCHECK that we have a continuous buffer as is required.
  RTC_DCHECK_EQ(frame_buffer->DataU(), frame_buffer->DataY() + y_size);
  RTC_DCHECK_EQ(frame_buffer->DataV(), frame_buffer->DataU() + uv_size);
  int total_size = y_size + 2 * uv_size;

  av_frame->format = context->pix_fmt;
  av_frame->reordered_opaque = context->reordered_opaque;

  // Set |av_frame| members as required by FFmpeg.
  av_frame->data[kYPlaneIndex] = frame_buffer->MutableDataY();
  av_frame->linesize[kYPlaneIndex] = frame_buffer->StrideY();
  av_frame->data[kUPlaneIndex] = frame_buffer->MutableDataU();
  av_frame->linesize[kUPlaneIndex] = frame_buffer->StrideU();
  av_frame->data[kVPlaneIndex] = frame_buffer->MutableDataV();
  av_frame->linesize[kVPlaneIndex] = frame_buffer->StrideV();
  RTC_DCHECK_EQ(av_frame->extended_data, av_frame->data);

  // Create a VideoFrame object, to keep a reference to the buffer.
  // TODO(nisse): The VideoFrame's timestamp and rotation info is not used.
  // Refactor to do not use a VideoFrame object at all.
  av_frame->buf[0] = av_buffer_create(
      av_frame->data[kYPlaneIndex], total_size, AVFreeBuffer2,
      static_cast<void*>(
          std::make_unique<VideoFrame>(VideoFrame::Builder()
                                           .set_video_frame_buffer(frame_buffer)
                                           .set_rotation(kVideoRotation_0)
                                           .set_timestamp_us(0)
                                           .build())
              .release()),
      0);
  RTC_CHECK(av_frame->buf[0]);
  return 0;
}

void H264DecoderImpl::AVFreeBuffer2(void* opaque, uint8_t* data) {
  // The buffer pool recycles the buffer used by |video_frame| when there are no
  // more references to it. |video_frame| is a thin buffer holder and is not
  // recycled.
  VideoFrame* video_frame = static_cast<VideoFrame*>(opaque);
  delete video_frame;
}

H264DecoderImpl::H264DecoderImpl()
    : ffmpeg_buffer_pool_(true),
      decoded_image_callback_(nullptr),
      has_reported_init_(false),
      has_reported_error_(false),
      preferred_output_format_(field_trial::IsEnabled("WebRTC-NV12Decode")
                                   ? VideoFrameBuffer::Type::kNV12
                                   : VideoFrameBuffer::Type::kI420) {}

H264DecoderImpl::~H264DecoderImpl() {
  Release();
}

int32_t H264DecoderImpl::InitDecode(const VideoCodec* codec_settings,
                                    int32_t number_of_cores) {
  ReportInit();
  if (codec_settings && codec_settings->codecType != kVideoCodecH264) {
    ReportError();
    return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
  }

  // Release necessary in case of re-initializing.
  int32_t ret = Release();
  if (ret != WEBRTC_VIDEO_CODEC_OK) {
    ReportError();
    return ret;
  }
  RTC_DCHECK(!av_context_);

  // Initialize AVCodecContext.
  av_context_.reset(avcodec_alloc_context3(nullptr));

  av_context_->codec_type = AVMEDIA_TYPE_VIDEO;
  av_context_->codec_id = AV_CODEC_ID_H264;
  if (codec_settings) {
    av_context_->coded_width = codec_settings->width;
    av_context_->coded_height = codec_settings->height;
  }
  av_context_->pix_fmt = kPixelFormatDefault;
  av_context_->extradata = nullptr;
  av_context_->extradata_size = 0;

  // If this is ever increased, look at |av_context_->thread_safe_callbacks| and
  // make it possible to disable the thread checker in the frame buffer pool.
  av_context_->thread_count = 1;
  av_context_->thread_type = FF_THREAD_SLICE;

  // Function used by FFmpeg to get buffers to store decoded frames in.
  av_context_->get_buffer2 = AVGetBuffer2;
  // |get_buffer2| is called with the context, there |opaque| can be used to get
  // a pointer |this|.
  av_context_->opaque = this;

  AVCodec* codec = avcodec_find_decoder(av_context_->codec_id);
  if (!codec) {
    // This is an indication that FFmpeg has not been initialized or it has not
    // been compiled/initialized with the correct set of codecs.
    RTC_LOG(LS_ERROR) << "FFmpeg H.264 decoder not found.";
    Release();
    ReportError();
    return WEBRTC_VIDEO_CODEC_ERROR;
  }
  int res = avcodec_open2(av_context_.get(), codec, nullptr);
  if (res < 0) {
    RTC_LOG(LS_ERROR) << "avcodec_open2 error: " << res;
    Release();
    ReportError();
    return WEBRTC_VIDEO_CODEC_ERROR;
  }

  av_frame_.reset(av_frame_alloc());

  if (codec_settings && codec_settings->buffer_pool_size) {
    if (!ffmpeg_buffer_pool_.Resize(*codec_settings->buffer_pool_size) ||
        !output_buffer_pool_.Resize(*codec_settings->buffer_pool_size)) {
      return WEBRTC_VIDEO_CODEC_UNINITIALIZED;
    }
  }
  return WEBRTC_VIDEO_CODEC_OK;
}

int32_t H264DecoderImpl::Release() {
  av_context_.reset();
  av_frame_.reset();
  return WEBRTC_VIDEO_CODEC_OK;
}

int32_t H264DecoderImpl::RegisterDecodeCompleteCallback(
    DecodedImageCallback* callback) {
  decoded_image_callback_ = callback;
  return WEBRTC_VIDEO_CODEC_OK;
}

int32_t H264DecoderImpl::Decode(const EncodedImage& input_image,
                                bool /*missing_frames*/,
                                int64_t /*render_time_ms*/) {
  if (!IsInitialized()) {
    ReportError();
    return WEBRTC_VIDEO_CODEC_UNINITIALIZED;
  }
  if (!decoded_image_callback_) {
    RTC_LOG(LS_WARNING)
        << "InitDecode() has been called, but a callback function "
           "has not been set with RegisterDecodeCompleteCallback()";
    ReportError();
    return WEBRTC_VIDEO_CODEC_UNINITIALIZED;
  }
  if (!input_image.data() || !input_image.size()) {
    ReportError();
    return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
  }

  AVPacket packet;
  av_init_packet(&packet);
  // packet.data has a non-const type, but isn't modified by
  // avcodec_send_packet.
  packet.data = const_cast<uint8_t*>(input_image.data());
  if (input_image.size() >
      static_cast<size_t>(std::numeric_limits<int>::max())) {
    ReportError();
    return WEBRTC_VIDEO_CODEC_ERROR;
  }
  packet.size = static_cast<int>(input_image.size());
  int64_t frame_timestamp_us = input_image.ntp_time_ms_ * 1000;  // ms -> μs
  av_context_->reordered_opaque = frame_timestamp_us;

  int result = avcodec_send_packet(av_context_.get(), &packet);
  if (result < 0) {
    RTC_LOG(LS_ERROR) << "avcodec_send_packet error: " << result;
    ReportError();
    return WEBRTC_VIDEO_CODEC_ERROR;
  }

  result = avcodec_receive_frame(av_context_.get(), av_frame_.get());
  if (result < 0) {
    RTC_LOG(LS_ERROR) << "avcodec_receive_frame error: " << result;
    ReportError();
    return WEBRTC_VIDEO_CODEC_ERROR;
  }

  // We don't expect reordering. Decoded frame tamestamp should match
  // the input one.
  RTC_DCHECK_EQ(av_frame_->reordered_opaque, frame_timestamp_us);

  absl::optional<uint8_t> qp;
  // TODO(sakal): Maybe it is possible to get QP directly from FFmpeg.
  h264_bitstream_parser_.ParseBitstream(input_image.data(), input_image.size());
  int qp_int;
  if (h264_bitstream_parser_.GetLastSliceQp(&qp_int)) {
    qp.emplace(qp_int);
  }

  // Obtain the |video_frame| containing the decoded image.
  VideoFrame* input_frame =
      static_cast<VideoFrame*>(av_buffer_get_opaque(av_frame_->buf[0]));
  RTC_DCHECK(input_frame);
  const webrtc::I420BufferInterface* i420_buffer =
      input_frame->video_frame_buffer()->GetI420();

  // When needed, FFmpeg applies cropping by moving plane pointers and adjusting
  // frame width/height. Ensure that cropped buffers lie within the allocated
  // memory.
  RTC_DCHECK_LE(av_frame_->width, i420_buffer->width());
  RTC_DCHECK_LE(av_frame_->height, i420_buffer->height());
  RTC_DCHECK_GE(av_frame_->data[kYPlaneIndex], i420_buffer->DataY());
  RTC_DCHECK_LE(
      av_frame_->data[kYPlaneIndex] +
          av_frame_->linesize[kYPlaneIndex] * av_frame_->height,
      i420_buffer->DataY() + i420_buffer->StrideY() * i420_buffer->height());
  RTC_DCHECK_GE(av_frame_->data[kUPlaneIndex], i420_buffer->DataU());
  RTC_DCHECK_LE(av_frame_->data[kUPlaneIndex] +
                    av_frame_->linesize[kUPlaneIndex] * av_frame_->height / 2,
                i420_buffer->DataU() +
                    i420_buffer->StrideU() * i420_buffer->height() / 2);
  RTC_DCHECK_GE(av_frame_->data[kVPlaneIndex], i420_buffer->DataV());
  RTC_DCHECK_LE(av_frame_->data[kVPlaneIndex] +
                    av_frame_->linesize[kVPlaneIndex] * av_frame_->height / 2,
                i420_buffer->DataV() +
                    i420_buffer->StrideV() * i420_buffer->height() / 2);

  rtc::scoped_refptr<webrtc::VideoFrameBuffer> cropped_buffer = WrapI420Buffer(
      av_frame_->width, av_frame_->height, av_frame_->data[kYPlaneIndex],
      av_frame_->linesize[kYPlaneIndex], av_frame_->data[kUPlaneIndex],
      av_frame_->linesize[kUPlaneIndex], av_frame_->data[kVPlaneIndex],
      av_frame_->linesize[kVPlaneIndex], rtc::KeepRefUntilDone(i420_buffer));

  if (preferred_output_format_ == VideoFrameBuffer::Type::kNV12) {
    const I420BufferInterface* cropped_i420 = cropped_buffer->GetI420();
    auto nv12_buffer = output_buffer_pool_.CreateNV12Buffer(
        cropped_i420->width(), cropped_i420->height());
    libyuv::I420ToNV12(cropped_i420->DataY(), cropped_i420->StrideY(),
                       cropped_i420->DataU(), cropped_i420->StrideU(),
                       cropped_i420->DataV(), cropped_i420->StrideV(),
                       nv12_buffer->MutableDataY(), nv12_buffer->StrideY(),
                       nv12_buffer->MutableDataUV(), nv12_buffer->StrideUV(),
                       i420_buffer->width(), i420_buffer->height());
    cropped_buffer = nv12_buffer;
  }

  // Pass on color space from input frame if explicitly specified.
  const ColorSpace& color_space =
      input_image.ColorSpace() ? *input_image.ColorSpace()
                               : ExtractH264ColorSpace(av_context_.get());

  VideoFrame decoded_frame = VideoFrame::Builder()
                                 .set_video_frame_buffer(cropped_buffer)
                                 .set_timestamp_rtp(input_image.Timestamp())
                                 .set_color_space(color_space)
                                 .build();

  // Return decoded frame.
  // TODO(nisse): Timestamp and rotation are all zero here. Change decoder
  // interface to pass a VideoFrameBuffer instead of a VideoFrame?
  decoded_image_callback_->Decoded(decoded_frame, absl::nullopt, qp);

  // Stop referencing it, possibly freeing |input_frame|.
  av_frame_unref(av_frame_.get());
  input_frame = nullptr;

  return WEBRTC_VIDEO_CODEC_OK;
}

const char* H264DecoderImpl::ImplementationName() const {
  return "FFmpeg";
}

bool H264DecoderImpl::IsInitialized() const {
  return av_context_ != nullptr;
}

void H264DecoderImpl::ReportInit() {
  if (has_reported_init_)
    return;
  RTC_HISTOGRAM_ENUMERATION("WebRTC.Video.H264DecoderImpl.Event",
                            kH264DecoderEventInit, kH264DecoderEventMax);
  has_reported_init_ = true;
}

void H264DecoderImpl::ReportError() {
  if (has_reported_error_)
    return;
  RTC_HISTOGRAM_ENUMERATION("WebRTC.Video.H264DecoderImpl.Event",
                            kH264DecoderEventError, kH264DecoderEventMax);
  has_reported_error_ = true;
}

}  // namespace webrtc

#endif  // WEBRTC_USE_H264
