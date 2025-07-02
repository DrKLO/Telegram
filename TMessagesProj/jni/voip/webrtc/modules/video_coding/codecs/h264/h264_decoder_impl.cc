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
#include "ffmpeg/include/libavcodec/avcodec.h"
#include "ffmpeg/include/libavformat/avformat.h"
#include "ffmpeg/include/libavutil/imgutils.h"
}  // extern "C"

#include "api/video/color_space.h"
#include "api/video/i010_buffer.h"
#include "api/video/i420_buffer.h"
#include "common_video/include/video_frame_buffer.h"
#include "modules/video_coding/codecs/h264/h264_color_space.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "system_wrappers/include/metrics.h"

namespace webrtc {

namespace {

constexpr std::array<AVPixelFormat, 9> kPixelFormatsSupported = {
    AV_PIX_FMT_YUV420P,     AV_PIX_FMT_YUV422P,     AV_PIX_FMT_YUV444P,
    AV_PIX_FMT_YUVJ420P,    AV_PIX_FMT_YUVJ422P,    AV_PIX_FMT_YUVJ444P,
    AV_PIX_FMT_YUV420P10LE, AV_PIX_FMT_YUV422P10LE, AV_PIX_FMT_YUV444P10LE};
const size_t kYPlaneIndex = 0;
const size_t kUPlaneIndex = 1;
const size_t kVPlaneIndex = 2;

// Used by histograms. Values of entries should not be changed.
enum H264DecoderImplEvent {
  kH264DecoderEventInit = 0,
  kH264DecoderEventError = 1,
  kH264DecoderEventMax = 16,
};

struct ScopedPtrAVFreePacket {
  void operator()(AVPacket* packet) { av_packet_free(&packet); }
};
typedef std::unique_ptr<AVPacket, ScopedPtrAVFreePacket> ScopedAVPacket;

ScopedAVPacket MakeScopedAVPacket() {
  ScopedAVPacket packet(av_packet_alloc());
  return packet;
}

}  // namespace

int H264DecoderImpl::AVGetBuffer2(AVCodecContext* context,
                                  AVFrame* av_frame,
                                  int flags) {
  // Set in `Configure`.
  H264DecoderImpl* decoder = static_cast<H264DecoderImpl*>(context->opaque);
  // DCHECK values set in `Configure`.
  RTC_DCHECK(decoder);
  // Necessary capability to be allowed to provide our own buffers.
  RTC_DCHECK(context->codec->capabilities | AV_CODEC_CAP_DR1);

  auto pixelFormatSupported = std::find_if(
      kPixelFormatsSupported.begin(), kPixelFormatsSupported.end(),
      [context](AVPixelFormat format) { return context->pix_fmt == format; });

  if (pixelFormatSupported == kPixelFormatsSupported.end()) {
    RTC_LOG(LS_ERROR) << "Unsupported pixel format: " << context->pix_fmt;
    decoder->ReportError();
    return -1;
  }

  // `av_frame->width` and `av_frame->height` are set by FFmpeg. These are the
  // actual image's dimensions and may be different from `context->width` and
  // `context->coded_width` due to reordering.
  int width = av_frame->width;
  int height = av_frame->height;
  // See `lowres`, if used the decoder scales the image by 1/2^(lowres). This
  // has implications on which resolutions are valid, but we don't use it.
  RTC_CHECK_EQ(context->lowres, 0);
  // Adjust the `width` and `height` to values acceptable by the decoder.
  // Without this, FFmpeg may overflow the buffer. If modified, `width` and/or
  // `height` are larger than the actual image and the image has to be cropped
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

  // The video frame is stored in `frame_buffer`. `av_frame` is FFmpeg's version
  // of a video frame and will be set up to reference `frame_buffer`'s data.

  // FFmpeg expects the initial allocation to be zero-initialized according to
  // http://crbug.com/390941. Our pool is set up to zero-initialize new buffers.
  // TODO(https://crbug.com/390941): Delete that feature from the video pool,
  // instead add an explicit call to InitializeData here.
  rtc::scoped_refptr<PlanarYuvBuffer> frame_buffer;
  rtc::scoped_refptr<I444Buffer> i444_buffer;
  rtc::scoped_refptr<I420Buffer> i420_buffer;
  rtc::scoped_refptr<I422Buffer> i422_buffer;
  rtc::scoped_refptr<I010Buffer> i010_buffer;
  rtc::scoped_refptr<I210Buffer> i210_buffer;
  rtc::scoped_refptr<I410Buffer> i410_buffer;
  int bytes_per_pixel = 1;
  switch (context->pix_fmt) {
    case AV_PIX_FMT_YUV420P:
    case AV_PIX_FMT_YUVJ420P:
      i420_buffer =
          decoder->ffmpeg_buffer_pool_.CreateI420Buffer(width, height);
      // Set `av_frame` members as required by FFmpeg.
      av_frame->data[kYPlaneIndex] = i420_buffer->MutableDataY();
      av_frame->linesize[kYPlaneIndex] = i420_buffer->StrideY();
      av_frame->data[kUPlaneIndex] = i420_buffer->MutableDataU();
      av_frame->linesize[kUPlaneIndex] = i420_buffer->StrideU();
      av_frame->data[kVPlaneIndex] = i420_buffer->MutableDataV();
      av_frame->linesize[kVPlaneIndex] = i420_buffer->StrideV();
      RTC_DCHECK_EQ(av_frame->extended_data, av_frame->data);
      frame_buffer = i420_buffer;
      break;
    case AV_PIX_FMT_YUV444P:
    case AV_PIX_FMT_YUVJ444P:
      i444_buffer =
          decoder->ffmpeg_buffer_pool_.CreateI444Buffer(width, height);
      // Set `av_frame` members as required by FFmpeg.
      av_frame->data[kYPlaneIndex] = i444_buffer->MutableDataY();
      av_frame->linesize[kYPlaneIndex] = i444_buffer->StrideY();
      av_frame->data[kUPlaneIndex] = i444_buffer->MutableDataU();
      av_frame->linesize[kUPlaneIndex] = i444_buffer->StrideU();
      av_frame->data[kVPlaneIndex] = i444_buffer->MutableDataV();
      av_frame->linesize[kVPlaneIndex] = i444_buffer->StrideV();
      frame_buffer = i444_buffer;
      break;
    case AV_PIX_FMT_YUV422P:
    case AV_PIX_FMT_YUVJ422P:
      i422_buffer =
          decoder->ffmpeg_buffer_pool_.CreateI422Buffer(width, height);
      // Set `av_frame` members as required by FFmpeg.
      av_frame->data[kYPlaneIndex] = i422_buffer->MutableDataY();
      av_frame->linesize[kYPlaneIndex] = i422_buffer->StrideY();
      av_frame->data[kUPlaneIndex] = i422_buffer->MutableDataU();
      av_frame->linesize[kUPlaneIndex] = i422_buffer->StrideU();
      av_frame->data[kVPlaneIndex] = i422_buffer->MutableDataV();
      av_frame->linesize[kVPlaneIndex] = i422_buffer->StrideV();
      frame_buffer = i422_buffer;
      break;
    case AV_PIX_FMT_YUV420P10LE:
      i010_buffer =
          decoder->ffmpeg_buffer_pool_.CreateI010Buffer(width, height);
      // Set `av_frame` members as required by FFmpeg.
      av_frame->data[kYPlaneIndex] =
          reinterpret_cast<uint8_t*>(i010_buffer->MutableDataY());
      av_frame->linesize[kYPlaneIndex] = i010_buffer->StrideY() * 2;
      av_frame->data[kUPlaneIndex] =
          reinterpret_cast<uint8_t*>(i010_buffer->MutableDataU());
      av_frame->linesize[kUPlaneIndex] = i010_buffer->StrideU() * 2;
      av_frame->data[kVPlaneIndex] =
          reinterpret_cast<uint8_t*>(i010_buffer->MutableDataV());
      av_frame->linesize[kVPlaneIndex] = i010_buffer->StrideV() * 2;
      frame_buffer = i010_buffer;
      bytes_per_pixel = 2;
      break;
    case AV_PIX_FMT_YUV422P10LE:
      i210_buffer =
          decoder->ffmpeg_buffer_pool_.CreateI210Buffer(width, height);
      // Set `av_frame` members as required by FFmpeg.
      av_frame->data[kYPlaneIndex] =
          reinterpret_cast<uint8_t*>(i210_buffer->MutableDataY());
      av_frame->linesize[kYPlaneIndex] = i210_buffer->StrideY() * 2;
      av_frame->data[kUPlaneIndex] =
          reinterpret_cast<uint8_t*>(i210_buffer->MutableDataU());
      av_frame->linesize[kUPlaneIndex] = i210_buffer->StrideU() * 2;
      av_frame->data[kVPlaneIndex] =
          reinterpret_cast<uint8_t*>(i210_buffer->MutableDataV());
      av_frame->linesize[kVPlaneIndex] = i210_buffer->StrideV() * 2;
      frame_buffer = i210_buffer;
      bytes_per_pixel = 2;
      break;
    case AV_PIX_FMT_YUV444P10LE:
      i410_buffer =
          decoder->ffmpeg_buffer_pool_.CreateI410Buffer(width, height);
      // Set `av_frame` members as required by FFmpeg.
      av_frame->data[kYPlaneIndex] =
          reinterpret_cast<uint8_t*>(i410_buffer->MutableDataY());
      av_frame->linesize[kYPlaneIndex] = i410_buffer->StrideY() * 2;
      av_frame->data[kUPlaneIndex] =
          reinterpret_cast<uint8_t*>(i410_buffer->MutableDataU());
      av_frame->linesize[kUPlaneIndex] = i410_buffer->StrideU() * 2;
      av_frame->data[kVPlaneIndex] =
          reinterpret_cast<uint8_t*>(i410_buffer->MutableDataV());
      av_frame->linesize[kVPlaneIndex] = i410_buffer->StrideV() * 2;
      frame_buffer = i410_buffer;
      bytes_per_pixel = 2;
      break;
    default:
      RTC_LOG(LS_ERROR) << "Unsupported buffer type " << context->pix_fmt
                        << ". Check supported supported pixel formats!";
      decoder->ReportError();
      return -1;
  }

  int y_size = width * height * bytes_per_pixel;
  int uv_size = frame_buffer->ChromaWidth() * frame_buffer->ChromaHeight() *
                bytes_per_pixel;
  // DCHECK that we have a continuous buffer as is required.
  RTC_DCHECK_EQ(av_frame->data[kUPlaneIndex],
                av_frame->data[kYPlaneIndex] + y_size);
  RTC_DCHECK_EQ(av_frame->data[kVPlaneIndex],
                av_frame->data[kUPlaneIndex] + uv_size);
  int total_size = y_size + 2 * uv_size;

  av_frame->format = context->pix_fmt;
  av_frame->reordered_opaque = context->reordered_opaque;

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
  // The buffer pool recycles the buffer used by `video_frame` when there are no
  // more references to it. `video_frame` is a thin buffer holder and is not
  // recycled.
  VideoFrame* video_frame = static_cast<VideoFrame*>(opaque);
  delete video_frame;
}

H264DecoderImpl::H264DecoderImpl()
    : ffmpeg_buffer_pool_(true),
      decoded_image_callback_(nullptr),
      has_reported_init_(false),
      has_reported_error_(false) {}

H264DecoderImpl::~H264DecoderImpl() {
  Release();
}

bool H264DecoderImpl::Configure(const Settings& settings) {
  ReportInit();
  if (settings.codec_type() != kVideoCodecH264) {
    ReportError();
    return false;
  }

  // Release necessary in case of re-initializing.
  int32_t ret = Release();
  if (ret != WEBRTC_VIDEO_CODEC_OK) {
    ReportError();
    return false;
  }
  RTC_DCHECK(!av_context_);

  // Initialize AVCodecContext.
  av_context_.reset(avcodec_alloc_context3(nullptr));

  av_context_->codec_type = AVMEDIA_TYPE_VIDEO;
  av_context_->codec_id = AV_CODEC_ID_H264;
  const RenderResolution& resolution = settings.max_render_resolution();
  if (resolution.Valid()) {
    av_context_->coded_width = resolution.Width();
    av_context_->coded_height = resolution.Height();
  }
  av_context_->extradata = nullptr;
  av_context_->extradata_size = 0;

  // If this is ever increased, look at `av_context_->thread_safe_callbacks` and
  // make it possible to disable the thread checker in the frame buffer pool.
  av_context_->thread_count = 1;
  av_context_->thread_type = FF_THREAD_SLICE;

  // Function used by FFmpeg to get buffers to store decoded frames in.
  av_context_->get_buffer2 = AVGetBuffer2;
  // `get_buffer2` is called with the context, there `opaque` can be used to get
  // a pointer `this`.
  av_context_->opaque = this;

  const AVCodec* codec = avcodec_find_decoder(av_context_->codec_id);
  if (!codec) {
    // This is an indication that FFmpeg has not been initialized or it has not
    // been compiled/initialized with the correct set of codecs.
    RTC_LOG(LS_ERROR) << "FFmpeg H.264 decoder not found.";
    Release();
    ReportError();
    return false;
  }
  int res = avcodec_open2(av_context_.get(), codec, nullptr);
  if (res < 0) {
    RTC_LOG(LS_ERROR) << "avcodec_open2 error: " << res;
    Release();
    ReportError();
    return false;
  }

  av_frame_.reset(av_frame_alloc());

  if (absl::optional<int> buffer_pool_size = settings.buffer_pool_size()) {
    if (!ffmpeg_buffer_pool_.Resize(*buffer_pool_size)) {
      return false;
    }
  }
  return true;
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
        << "Configure() has been called, but a callback function "
           "has not been set with RegisterDecodeCompleteCallback()";
    ReportError();
    return WEBRTC_VIDEO_CODEC_UNINITIALIZED;
  }
  if (!input_image.data() || !input_image.size()) {
    ReportError();
    return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
  }

  ScopedAVPacket packet = MakeScopedAVPacket();
  if (!packet) {
    ReportError();
    return WEBRTC_VIDEO_CODEC_ERROR;
  }
  // packet.data has a non-const type, but isn't modified by
  // avcodec_send_packet.
  packet->data = const_cast<uint8_t*>(input_image.data());
  if (input_image.size() >
      static_cast<size_t>(std::numeric_limits<int>::max())) {
    ReportError();
    return WEBRTC_VIDEO_CODEC_ERROR;
  }
  packet->size = static_cast<int>(input_image.size());
  int64_t frame_timestamp_us = input_image.ntp_time_ms_ * 1000;  // ms -> μs
  av_context_->reordered_opaque = frame_timestamp_us;

  int result = avcodec_send_packet(av_context_.get(), packet.get());

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

  // We don't expect reordering. Decoded frame timestamp should match
  // the input one.
  RTC_DCHECK_EQ(av_frame_->reordered_opaque, frame_timestamp_us);

  // TODO(sakal): Maybe it is possible to get QP directly from FFmpeg.
  h264_bitstream_parser_.ParseBitstream(input_image);
  absl::optional<int> qp = h264_bitstream_parser_.GetLastSliceQp();

  // Obtain the `video_frame` containing the decoded image.
  VideoFrame* input_frame =
      static_cast<VideoFrame*>(av_buffer_get_opaque(av_frame_->buf[0]));
  RTC_DCHECK(input_frame);
  rtc::scoped_refptr<VideoFrameBuffer> frame_buffer =
      input_frame->video_frame_buffer();

  // Instantiate Planar YUV buffer according to video frame buffer type
  const webrtc::PlanarYuvBuffer* planar_yuv_buffer = nullptr;
  const webrtc::PlanarYuv8Buffer* planar_yuv8_buffer = nullptr;
  const webrtc::PlanarYuv16BBuffer* planar_yuv16_buffer = nullptr;
  VideoFrameBuffer::Type video_frame_buffer_type = frame_buffer->type();
  switch (video_frame_buffer_type) {
    case VideoFrameBuffer::Type::kI420:
      planar_yuv_buffer = frame_buffer->GetI420();
      planar_yuv8_buffer =
          reinterpret_cast<const webrtc::PlanarYuv8Buffer*>(planar_yuv_buffer);
      break;
    case VideoFrameBuffer::Type::kI444:
      planar_yuv_buffer = frame_buffer->GetI444();
      planar_yuv8_buffer =
          reinterpret_cast<const webrtc::PlanarYuv8Buffer*>(planar_yuv_buffer);
      break;
    case VideoFrameBuffer::Type::kI422:
      planar_yuv_buffer = frame_buffer->GetI422();
      planar_yuv8_buffer =
          reinterpret_cast<const webrtc::PlanarYuv8Buffer*>(planar_yuv_buffer);
      break;
    case VideoFrameBuffer::Type::kI010:
      planar_yuv_buffer = frame_buffer->GetI010();
      planar_yuv16_buffer = reinterpret_cast<const webrtc::PlanarYuv16BBuffer*>(
          planar_yuv_buffer);
      break;
    case VideoFrameBuffer::Type::kI210:
      planar_yuv_buffer = frame_buffer->GetI210();
      planar_yuv16_buffer = reinterpret_cast<const webrtc::PlanarYuv16BBuffer*>(
          planar_yuv_buffer);
      break;
    case VideoFrameBuffer::Type::kI410:
      planar_yuv_buffer = frame_buffer->GetI410();
      planar_yuv16_buffer = reinterpret_cast<const webrtc::PlanarYuv16BBuffer*>(
          planar_yuv_buffer);
      break;
    default:
      // If this code is changed to allow other video frame buffer type,
      // make sure that the code below which wraps I420/I422/I444 buffer and
      // code which converts to NV12 is changed
      // to work with new video frame buffer type

      RTC_LOG(LS_ERROR) << "frame_buffer type: "
                        << static_cast<int32_t>(video_frame_buffer_type)
                        << " is not supported!";
      ReportError();
      return WEBRTC_VIDEO_CODEC_ERROR;
  }

  // When needed, FFmpeg applies cropping by moving plane pointers and adjusting
  // frame width/height. Ensure that cropped buffers lie within the allocated
  // memory.
  RTC_DCHECK_LE(av_frame_->width, planar_yuv_buffer->width());
  RTC_DCHECK_LE(av_frame_->height, planar_yuv_buffer->height());
  switch (video_frame_buffer_type) {
    case VideoFrameBuffer::Type::kI420:
    case VideoFrameBuffer::Type::kI444:
    case VideoFrameBuffer::Type::kI422: {
      RTC_DCHECK_GE(av_frame_->data[kYPlaneIndex], planar_yuv8_buffer->DataY());
      RTC_DCHECK_LE(
          av_frame_->data[kYPlaneIndex] +
              av_frame_->linesize[kYPlaneIndex] * av_frame_->height,
          planar_yuv8_buffer->DataY() +
              planar_yuv8_buffer->StrideY() * planar_yuv8_buffer->height());
      RTC_DCHECK_GE(av_frame_->data[kUPlaneIndex], planar_yuv8_buffer->DataU());
      RTC_DCHECK_LE(
          av_frame_->data[kUPlaneIndex] +
              av_frame_->linesize[kUPlaneIndex] *
                  planar_yuv8_buffer->ChromaHeight(),
          planar_yuv8_buffer->DataU() + planar_yuv8_buffer->StrideU() *
                                            planar_yuv8_buffer->ChromaHeight());
      RTC_DCHECK_GE(av_frame_->data[kVPlaneIndex], planar_yuv8_buffer->DataV());
      RTC_DCHECK_LE(
          av_frame_->data[kVPlaneIndex] +
              av_frame_->linesize[kVPlaneIndex] *
                  planar_yuv8_buffer->ChromaHeight(),
          planar_yuv8_buffer->DataV() + planar_yuv8_buffer->StrideV() *
                                            planar_yuv8_buffer->ChromaHeight());
      break;
    }
    case VideoFrameBuffer::Type::kI010:
    case VideoFrameBuffer::Type::kI210:
    case VideoFrameBuffer::Type::kI410: {
      RTC_DCHECK_GE(
          av_frame_->data[kYPlaneIndex],
          reinterpret_cast<const uint8_t*>(planar_yuv16_buffer->DataY()));
      RTC_DCHECK_LE(
          av_frame_->data[kYPlaneIndex] +
              av_frame_->linesize[kYPlaneIndex] * av_frame_->height,
          reinterpret_cast<const uint8_t*>(planar_yuv16_buffer->DataY()) +
              planar_yuv16_buffer->StrideY() * 2 *
                  planar_yuv16_buffer->height());
      RTC_DCHECK_GE(
          av_frame_->data[kUPlaneIndex],
          reinterpret_cast<const uint8_t*>(planar_yuv16_buffer->DataU()));
      RTC_DCHECK_LE(
          av_frame_->data[kUPlaneIndex] +
              av_frame_->linesize[kUPlaneIndex] *
                  planar_yuv16_buffer->ChromaHeight(),
          reinterpret_cast<const uint8_t*>(planar_yuv16_buffer->DataU()) +
              planar_yuv16_buffer->StrideU() * 2 *
                  planar_yuv16_buffer->ChromaHeight());
      RTC_DCHECK_GE(
          av_frame_->data[kVPlaneIndex],
          reinterpret_cast<const uint8_t*>(planar_yuv16_buffer->DataV()));
      RTC_DCHECK_LE(
          av_frame_->data[kVPlaneIndex] +
              av_frame_->linesize[kVPlaneIndex] *
                  planar_yuv16_buffer->ChromaHeight(),
          reinterpret_cast<const uint8_t*>(planar_yuv16_buffer->DataV()) +
              planar_yuv16_buffer->StrideV() * 2 *
                  planar_yuv16_buffer->ChromaHeight());
      break;
    }
    default:
      RTC_LOG(LS_ERROR) << "frame_buffer type: "
                        << static_cast<int32_t>(video_frame_buffer_type)
                        << " is not supported!";
      ReportError();
      return WEBRTC_VIDEO_CODEC_ERROR;
  }

  rtc::scoped_refptr<webrtc::VideoFrameBuffer> cropped_buffer;
  switch (video_frame_buffer_type) {
    case VideoFrameBuffer::Type::kI420:
      cropped_buffer = WrapI420Buffer(
          av_frame_->width, av_frame_->height, av_frame_->data[kYPlaneIndex],
          av_frame_->linesize[kYPlaneIndex], av_frame_->data[kUPlaneIndex],
          av_frame_->linesize[kUPlaneIndex], av_frame_->data[kVPlaneIndex],
          av_frame_->linesize[kVPlaneIndex],
          // To keep reference alive.
          [frame_buffer] {});
      break;
    case VideoFrameBuffer::Type::kI444:
      cropped_buffer = WrapI444Buffer(
          av_frame_->width, av_frame_->height, av_frame_->data[kYPlaneIndex],
          av_frame_->linesize[kYPlaneIndex], av_frame_->data[kUPlaneIndex],
          av_frame_->linesize[kUPlaneIndex], av_frame_->data[kVPlaneIndex],
          av_frame_->linesize[kVPlaneIndex],
          // To keep reference alive.
          [frame_buffer] {});
      break;
    case VideoFrameBuffer::Type::kI422:
      cropped_buffer = WrapI422Buffer(
          av_frame_->width, av_frame_->height, av_frame_->data[kYPlaneIndex],
          av_frame_->linesize[kYPlaneIndex], av_frame_->data[kUPlaneIndex],
          av_frame_->linesize[kUPlaneIndex], av_frame_->data[kVPlaneIndex],
          av_frame_->linesize[kVPlaneIndex],
          // To keep reference alive.
          [frame_buffer] {});
      break;
    case VideoFrameBuffer::Type::kI010:
      cropped_buffer = WrapI010Buffer(
          av_frame_->width, av_frame_->height,
          reinterpret_cast<const uint16_t*>(av_frame_->data[kYPlaneIndex]),
          av_frame_->linesize[kYPlaneIndex] / 2,
          reinterpret_cast<const uint16_t*>(av_frame_->data[kUPlaneIndex]),
          av_frame_->linesize[kUPlaneIndex] / 2,
          reinterpret_cast<const uint16_t*>(av_frame_->data[kVPlaneIndex]),
          av_frame_->linesize[kVPlaneIndex] / 2,
          // To keep reference alive.
          [frame_buffer] {});
      break;
    case VideoFrameBuffer::Type::kI210:
      cropped_buffer = WrapI210Buffer(
          av_frame_->width, av_frame_->height,
          reinterpret_cast<const uint16_t*>(av_frame_->data[kYPlaneIndex]),
          av_frame_->linesize[kYPlaneIndex] / 2,
          reinterpret_cast<const uint16_t*>(av_frame_->data[kUPlaneIndex]),
          av_frame_->linesize[kUPlaneIndex] / 2,
          reinterpret_cast<const uint16_t*>(av_frame_->data[kVPlaneIndex]),
          av_frame_->linesize[kVPlaneIndex] / 2,
          // To keep reference alive.
          [frame_buffer] {});
      break;
    case VideoFrameBuffer::Type::kI410:
      cropped_buffer = WrapI410Buffer(
          av_frame_->width, av_frame_->height,
          reinterpret_cast<const uint16_t*>(av_frame_->data[kYPlaneIndex]),
          av_frame_->linesize[kYPlaneIndex] / 2,
          reinterpret_cast<const uint16_t*>(av_frame_->data[kUPlaneIndex]),
          av_frame_->linesize[kUPlaneIndex] / 2,
          reinterpret_cast<const uint16_t*>(av_frame_->data[kVPlaneIndex]),
          av_frame_->linesize[kVPlaneIndex] / 2,
          // To keep reference alive.
          [frame_buffer] {});
      break;
    default:
      RTC_LOG(LS_ERROR) << "frame_buffer type: "
                        << static_cast<int32_t>(video_frame_buffer_type)
                        << " is not supported!";
      ReportError();
      return WEBRTC_VIDEO_CODEC_ERROR;
  }

  // Pass on color space from input frame if explicitly specified.
  const ColorSpace& color_space =
      input_image.ColorSpace() ? *input_image.ColorSpace()
                               : ExtractH264ColorSpace(av_context_.get());

  VideoFrame decoded_frame = VideoFrame::Builder()
                                 .set_video_frame_buffer(cropped_buffer)
                                 .set_timestamp_rtp(input_image.RtpTimestamp())
                                 .set_color_space(color_space)
                                 .build();

  // Return decoded frame.
  // TODO(nisse): Timestamp and rotation are all zero here. Change decoder
  // interface to pass a VideoFrameBuffer instead of a VideoFrame?
  decoded_image_callback_->Decoded(decoded_frame, absl::nullopt, qp);

  // Stop referencing it, possibly freeing `input_frame`.
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
