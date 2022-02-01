/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 *
 */

#ifdef RTC_ENABLE_VP9

#include "modules/video_coding/codecs/vp9/libvpx_vp9_decoder.h"

#include <algorithm>

#include "absl/strings/match.h"
#include "api/transport/field_trial_based_config.h"
#include "api/video/color_space.h"
#include "api/video/i010_buffer.h"
#include "common_video/include/video_frame_buffer.h"
#include "modules/video_coding/utility/vp9_uncompressed_header_parser.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "third_party/libyuv/include/libyuv/convert.h"
#include <libvpx/vp8dx.h>
#include <libvpx/vpx_decoder.h>

namespace webrtc {
namespace {

// Helper class for extracting VP9 colorspace.
ColorSpace ExtractVP9ColorSpace(vpx_color_space_t space_t,
                                vpx_color_range_t range_t,
                                unsigned int bit_depth) {
  ColorSpace::PrimaryID primaries = ColorSpace::PrimaryID::kUnspecified;
  ColorSpace::TransferID transfer = ColorSpace::TransferID::kUnspecified;
  ColorSpace::MatrixID matrix = ColorSpace::MatrixID::kUnspecified;
  switch (space_t) {
    case VPX_CS_BT_601:
    case VPX_CS_SMPTE_170:
      primaries = ColorSpace::PrimaryID::kSMPTE170M;
      transfer = ColorSpace::TransferID::kSMPTE170M;
      matrix = ColorSpace::MatrixID::kSMPTE170M;
      break;
    case VPX_CS_SMPTE_240:
      primaries = ColorSpace::PrimaryID::kSMPTE240M;
      transfer = ColorSpace::TransferID::kSMPTE240M;
      matrix = ColorSpace::MatrixID::kSMPTE240M;
      break;
    case VPX_CS_BT_709:
      primaries = ColorSpace::PrimaryID::kBT709;
      transfer = ColorSpace::TransferID::kBT709;
      matrix = ColorSpace::MatrixID::kBT709;
      break;
    case VPX_CS_BT_2020:
      primaries = ColorSpace::PrimaryID::kBT2020;
      switch (bit_depth) {
        case 8:
          transfer = ColorSpace::TransferID::kBT709;
          break;
        case 10:
          transfer = ColorSpace::TransferID::kBT2020_10;
          break;
        default:
          RTC_NOTREACHED();
          break;
      }
      matrix = ColorSpace::MatrixID::kBT2020_NCL;
      break;
    case VPX_CS_SRGB:
      primaries = ColorSpace::PrimaryID::kBT709;
      transfer = ColorSpace::TransferID::kIEC61966_2_1;
      matrix = ColorSpace::MatrixID::kBT709;
      break;
    default:
      break;
  }

  ColorSpace::RangeID range = ColorSpace::RangeID::kInvalid;
  switch (range_t) {
    case VPX_CR_STUDIO_RANGE:
      range = ColorSpace::RangeID::kLimited;
      break;
    case VPX_CR_FULL_RANGE:
      range = ColorSpace::RangeID::kFull;
      break;
    default:
      break;
  }
  return ColorSpace(primaries, transfer, matrix, range);
}

}  // namespace

LibvpxVp9Decoder::LibvpxVp9Decoder()
    : LibvpxVp9Decoder(FieldTrialBasedConfig()) {}
LibvpxVp9Decoder::LibvpxVp9Decoder(const WebRtcKeyValueConfig& trials)
    : decode_complete_callback_(nullptr),
      inited_(false),
      decoder_(nullptr),
      key_frame_required_(true),
      preferred_output_format_(
          absl::StartsWith(trials.Lookup("WebRTC-NV12Decode"), "Enabled")
              ? VideoFrameBuffer::Type::kNV12
              : VideoFrameBuffer::Type::kI420) {}

LibvpxVp9Decoder::~LibvpxVp9Decoder() {
  inited_ = true;  // in order to do the actual release
  Release();
  int num_buffers_in_use = libvpx_buffer_pool_.GetNumBuffersInUse();
  if (num_buffers_in_use > 0) {
    // The frame buffers are reference counted and frames are exposed after
    // decoding. There may be valid usage cases where previous frames are still
    // referenced after ~LibvpxVp9Decoder that is not a leak.
    RTC_LOG(LS_INFO) << num_buffers_in_use
                     << " Vp9FrameBuffers are still "
                        "referenced during ~LibvpxVp9Decoder.";
  }
}

int LibvpxVp9Decoder::InitDecode(const VideoCodec* inst, int number_of_cores) {
  int ret_val = Release();
  if (ret_val < 0) {
    return ret_val;
  }

  if (decoder_ == nullptr) {
    decoder_ = new vpx_codec_ctx_t;
  }
  vpx_codec_dec_cfg_t cfg;
  memset(&cfg, 0, sizeof(cfg));

#ifdef FUZZING_BUILD_MODE_UNSAFE_FOR_PRODUCTION
  // We focus on webrtc fuzzing here, not libvpx itself. Use single thread for
  // fuzzing, because:
  //  - libvpx's VP9 single thread decoder is more fuzzer friendly. It detects
  //    errors earlier than the multi-threads version.
  //  - Make peak CPU usage under control (not depending on input)
  cfg.threads = 1;
#else
  if (!inst) {
    // No config provided - don't know resolution to decode yet.
    // Set thread count to one in the meantime.
    cfg.threads = 1;
  } else {
    // We want to use multithreading when decoding high resolution videos. But
    // not too many in order to avoid overhead when many stream are decoded
    // concurrently.
    // Set 2 thread as target for 1280x720 pixel count, and then scale up
    // linearly from there - but cap at physical core count.
    // For common resolutions this results in:
    // 1 for 360p
    // 2 for 720p
    // 4 for 1080p
    // 8 for 1440p
    // 18 for 4K
    int num_threads =
        std::max(1, 2 * (inst->width * inst->height) / (1280 * 720));
    cfg.threads = std::min(number_of_cores, num_threads);
    current_codec_ = *inst;
  }
#endif

  num_cores_ = number_of_cores;

  vpx_codec_flags_t flags = 0;
  if (vpx_codec_dec_init(decoder_, vpx_codec_vp9_dx(), &cfg, flags)) {
    return WEBRTC_VIDEO_CODEC_MEMORY;
  }

  if (!libvpx_buffer_pool_.InitializeVpxUsePool(decoder_)) {
    return WEBRTC_VIDEO_CODEC_MEMORY;
  }

  inited_ = true;
  // Always start with a complete key frame.
  key_frame_required_ = true;
  if (inst && inst->buffer_pool_size) {
    if (!libvpx_buffer_pool_.Resize(*inst->buffer_pool_size) ||
        !output_buffer_pool_.Resize(*inst->buffer_pool_size)) {
      return WEBRTC_VIDEO_CODEC_UNINITIALIZED;
    }
  }

  vpx_codec_err_t status =
      vpx_codec_control(decoder_, VP9D_SET_LOOP_FILTER_OPT, 1);
  if (status != VPX_CODEC_OK) {
    RTC_LOG(LS_ERROR) << "Failed to enable VP9D_SET_LOOP_FILTER_OPT. "
                      << vpx_codec_error(decoder_);
    return WEBRTC_VIDEO_CODEC_UNINITIALIZED;
  }

  return WEBRTC_VIDEO_CODEC_OK;
}

int LibvpxVp9Decoder::Decode(const EncodedImage& input_image,
                             bool missing_frames,
                             int64_t /*render_time_ms*/) {
  if (!inited_) {
    return WEBRTC_VIDEO_CODEC_UNINITIALIZED;
  }
  if (decode_complete_callback_ == nullptr) {
    return WEBRTC_VIDEO_CODEC_UNINITIALIZED;
  }

  if (input_image._frameType == VideoFrameType::kVideoFrameKey) {
    absl::optional<vp9::FrameInfo> frame_info =
        vp9::ParseIntraFrameInfo(input_image.data(), input_image.size());
    if (frame_info) {
      if (frame_info->frame_width != current_codec_.width ||
          frame_info->frame_height != current_codec_.height) {
        // Resolution has changed, tear down and re-init a new decoder in
        // order to get correct sizing.
        Release();
        current_codec_.width = frame_info->frame_width;
        current_codec_.height = frame_info->frame_height;
        int reinit_status = InitDecode(&current_codec_, num_cores_);
        if (reinit_status != WEBRTC_VIDEO_CODEC_OK) {
          RTC_LOG(LS_WARNING) << "Failed to re-init decoder.";
          return reinit_status;
        }
      }
    } else {
      RTC_LOG(LS_WARNING) << "Failed to parse VP9 header from key-frame.";
    }
  }

  // Always start with a complete key frame.
  if (key_frame_required_) {
    if (input_image._frameType != VideoFrameType::kVideoFrameKey)
      return WEBRTC_VIDEO_CODEC_ERROR;
    key_frame_required_ = false;
  }
  vpx_codec_iter_t iter = nullptr;
  vpx_image_t* img;
  const uint8_t* buffer = input_image.data();
  if (input_image.size() == 0) {
    buffer = nullptr;  // Triggers full frame concealment.
  }
  // During decode libvpx may get and release buffers from
  // |libvpx_buffer_pool_|. In practice libvpx keeps a few (~3-4) buffers alive
  // at a time.
  if (vpx_codec_decode(decoder_, buffer,
                       static_cast<unsigned int>(input_image.size()), 0,
                       VPX_DL_REALTIME)) {
    return WEBRTC_VIDEO_CODEC_ERROR;
  }
  // |img->fb_priv| contains the image data, a reference counted Vp9FrameBuffer.
  // It may be released by libvpx during future vpx_codec_decode or
  // vpx_codec_destroy calls.
  img = vpx_codec_get_frame(decoder_, &iter);
  int qp;
  vpx_codec_err_t vpx_ret =
      vpx_codec_control(decoder_, VPXD_GET_LAST_QUANTIZER, &qp);
  RTC_DCHECK_EQ(vpx_ret, VPX_CODEC_OK);
  int ret =
      ReturnFrame(img, input_image.Timestamp(), qp, input_image.ColorSpace());
  if (ret != 0) {
    return ret;
  }
  return WEBRTC_VIDEO_CODEC_OK;
}

int LibvpxVp9Decoder::ReturnFrame(
    const vpx_image_t* img,
    uint32_t timestamp,
    int qp,
    const webrtc::ColorSpace* explicit_color_space) {
  if (img == nullptr) {
    // Decoder OK and nullptr image => No show frame.
    return WEBRTC_VIDEO_CODEC_NO_OUTPUT;
  }

  // This buffer contains all of |img|'s image data, a reference counted
  // Vp9FrameBuffer. (libvpx is done with the buffers after a few
  // vpx_codec_decode calls or vpx_codec_destroy).
  rtc::scoped_refptr<Vp9FrameBufferPool::Vp9FrameBuffer> img_buffer =
      static_cast<Vp9FrameBufferPool::Vp9FrameBuffer*>(img->fb_priv);

  // The buffer can be used directly by the VideoFrame (without copy) by
  // using a Wrapped*Buffer.
  rtc::scoped_refptr<VideoFrameBuffer> img_wrapped_buffer;
  switch (img->bit_depth) {
    case 8:
      if (img->fmt == VPX_IMG_FMT_I420) {
        if (preferred_output_format_ == VideoFrameBuffer::Type::kNV12) {
          rtc::scoped_refptr<NV12Buffer> nv12_buffer =
              output_buffer_pool_.CreateNV12Buffer(img->d_w, img->d_h);
          if (!nv12_buffer.get()) {
            // Buffer pool is full.
            return WEBRTC_VIDEO_CODEC_NO_OUTPUT;
          }
          img_wrapped_buffer = nv12_buffer;
          libyuv::I420ToNV12(img->planes[VPX_PLANE_Y], img->stride[VPX_PLANE_Y],
                             img->planes[VPX_PLANE_U], img->stride[VPX_PLANE_U],
                             img->planes[VPX_PLANE_V], img->stride[VPX_PLANE_V],
                             nv12_buffer->MutableDataY(),
                             nv12_buffer->StrideY(),
                             nv12_buffer->MutableDataUV(),
                             nv12_buffer->StrideUV(), img->d_w, img->d_h);
          // No holding onto img_buffer as it's no longer needed and can be
          // reused.
        } else {
          img_wrapped_buffer = WrapI420Buffer(
              img->d_w, img->d_h, img->planes[VPX_PLANE_Y],
              img->stride[VPX_PLANE_Y], img->planes[VPX_PLANE_U],
              img->stride[VPX_PLANE_U], img->planes[VPX_PLANE_V],
              img->stride[VPX_PLANE_V],
              // WrappedI420Buffer's mechanism for allowing the release of its
              // frame buffer is through a callback function. This is where we
              // should release |img_buffer|.
              [img_buffer] {});
        }
      } else if (img->fmt == VPX_IMG_FMT_I444) {
        img_wrapped_buffer = WrapI444Buffer(
            img->d_w, img->d_h, img->planes[VPX_PLANE_Y],
            img->stride[VPX_PLANE_Y], img->planes[VPX_PLANE_U],
            img->stride[VPX_PLANE_U], img->planes[VPX_PLANE_V],
            img->stride[VPX_PLANE_V],
            // WrappedI444Buffer's mechanism for allowing the release of its
            // frame buffer is through a callback function. This is where we
            // should release |img_buffer|.
            [img_buffer] {});
      } else {
        RTC_LOG(LS_ERROR)
            << "Unsupported pixel format produced by the decoder: "
            << static_cast<int>(img->fmt);
        return WEBRTC_VIDEO_CODEC_NO_OUTPUT;
      }
      break;
    case 10:
      img_wrapped_buffer = WrapI010Buffer(
          img->d_w, img->d_h,
          reinterpret_cast<const uint16_t*>(img->planes[VPX_PLANE_Y]),
          img->stride[VPX_PLANE_Y] / 2,
          reinterpret_cast<const uint16_t*>(img->planes[VPX_PLANE_U]),
          img->stride[VPX_PLANE_U] / 2,
          reinterpret_cast<const uint16_t*>(img->planes[VPX_PLANE_V]),
          img->stride[VPX_PLANE_V] / 2, [img_buffer] {});
      break;
    default:
      RTC_LOG(LS_ERROR) << "Unsupported bit depth produced by the decoder: "
                        << img->bit_depth;
      return WEBRTC_VIDEO_CODEC_NO_OUTPUT;
  }

  auto builder = VideoFrame::Builder()
                     .set_video_frame_buffer(img_wrapped_buffer)
                     .set_timestamp_rtp(timestamp);
  if (explicit_color_space) {
    builder.set_color_space(*explicit_color_space);
  } else {
    builder.set_color_space(
        ExtractVP9ColorSpace(img->cs, img->range, img->bit_depth));
  }
  VideoFrame decoded_image = builder.build();

  decode_complete_callback_->Decoded(decoded_image, absl::nullopt, qp);
  return WEBRTC_VIDEO_CODEC_OK;
}

int LibvpxVp9Decoder::RegisterDecodeCompleteCallback(
    DecodedImageCallback* callback) {
  decode_complete_callback_ = callback;
  return WEBRTC_VIDEO_CODEC_OK;
}

int LibvpxVp9Decoder::Release() {
  int ret_val = WEBRTC_VIDEO_CODEC_OK;

  if (decoder_ != nullptr) {
    if (inited_) {
      // When a codec is destroyed libvpx will release any buffers of
      // |libvpx_buffer_pool_| it is currently using.
      if (vpx_codec_destroy(decoder_)) {
        ret_val = WEBRTC_VIDEO_CODEC_MEMORY;
      }
    }
    delete decoder_;
    decoder_ = nullptr;
  }
  // Releases buffers from the pool. Any buffers not in use are deleted. Buffers
  // still referenced externally are deleted once fully released, not returning
  // to the pool.
  libvpx_buffer_pool_.ClearPool();
  output_buffer_pool_.Release();
  inited_ = false;
  return ret_val;
}

VideoDecoder::DecoderInfo LibvpxVp9Decoder::GetDecoderInfo() const {
  DecoderInfo info;
  info.implementation_name = "libvpx";
  info.is_hardware_accelerated = false;
  return info;
}

const char* LibvpxVp9Decoder::ImplementationName() const {
  return "libvpx";
}

}  // namespace webrtc

#endif  // RTC_ENABLE_VP9
