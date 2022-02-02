/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/codecs/vp8/libvpx_vp8_decoder.h"

#include <stdio.h>
#include <string.h>

#include <algorithm>
#include <memory>
#include <string>

#include "absl/types/optional.h"
#include "api/scoped_refptr.h"
#include "api/video/i420_buffer.h"
#include "api/video/video_frame.h"
#include "api/video/video_frame_buffer.h"
#include "api/video/video_rotation.h"
#include "modules/video_coding/codecs/vp8/include/vp8.h"
#include "modules/video_coding/include/video_error_codes.h"
#include "rtc_base/checks.h"
#include "rtc_base/numerics/exp_filter.h"
#include "rtc_base/time_utils.h"
#include "system_wrappers/include/field_trial.h"
#include "system_wrappers/include/metrics.h"
#include "third_party/libyuv/include/libyuv/convert.h"
#include <libvpx/vp8.h>
#include <libvpx/vp8dx.h>
#include <libvpx/vpx_decoder.h>

namespace webrtc {
namespace {
constexpr int kVp8ErrorPropagationTh = 30;
// vpx_decoder.h documentation indicates decode deadline is time in us, with
// "Set to zero for unlimited.", but actual implementation requires this to be
// a mode with 0 meaning allow delay and 1 not allowing it.
constexpr long kDecodeDeadlineRealtime = 1;  // NOLINT

const char kVp8PostProcArmFieldTrial[] = "WebRTC-VP8-Postproc-Config-Arm";
const char kVp8PostProcFieldTrial[] = "WebRTC-VP8-Postproc-Config";

#if defined(WEBRTC_ARCH_ARM) || defined(WEBRTC_ARCH_ARM64) || \
    defined(WEBRTC_ANDROID)
constexpr bool kIsArm = true;
#else
constexpr bool kIsArm = false;
#endif

absl::optional<LibvpxVp8Decoder::DeblockParams> DefaultDeblockParams() {
  return LibvpxVp8Decoder::DeblockParams(/*max_level=*/8,
                                         /*degrade_qp=*/60,
                                         /*min_qp=*/30);
}

absl::optional<LibvpxVp8Decoder::DeblockParams>
GetPostProcParamsFromFieldTrialGroup() {
  std::string group = webrtc::field_trial::FindFullName(
      kIsArm ? kVp8PostProcArmFieldTrial : kVp8PostProcFieldTrial);
  if (group.empty()) {
    return DefaultDeblockParams();
  }

  LibvpxVp8Decoder::DeblockParams params;
  if (sscanf(group.c_str(), "Enabled-%d,%d,%d", &params.max_level,
             &params.min_qp, &params.degrade_qp) != 3) {
    return DefaultDeblockParams();
  }

  if (params.max_level < 0 || params.max_level > 16) {
    return DefaultDeblockParams();
  }

  if (params.min_qp < 0 || params.degrade_qp <= params.min_qp) {
    return DefaultDeblockParams();
  }

  return params;
}

}  // namespace

std::unique_ptr<VideoDecoder> VP8Decoder::Create() {
  return std::make_unique<LibvpxVp8Decoder>();
}

class LibvpxVp8Decoder::QpSmoother {
 public:
  QpSmoother() : last_sample_ms_(rtc::TimeMillis()), smoother_(kAlpha) {}

  int GetAvg() const {
    float value = smoother_.filtered();
    return (value == rtc::ExpFilter::kValueUndefined) ? 0
                                                      : static_cast<int>(value);
  }

  void Add(float sample) {
    int64_t now_ms = rtc::TimeMillis();
    smoother_.Apply(static_cast<float>(now_ms - last_sample_ms_), sample);
    last_sample_ms_ = now_ms;
  }

  void Reset() { smoother_.Reset(kAlpha); }

 private:
  const float kAlpha = 0.95f;
  int64_t last_sample_ms_;
  rtc::ExpFilter smoother_;
};

LibvpxVp8Decoder::LibvpxVp8Decoder()
    : use_postproc_(
          kIsArm ? webrtc::field_trial::IsEnabled(kVp8PostProcArmFieldTrial)
                 : true),
      buffer_pool_(false, 300 /* max_number_of_buffers*/),
      decode_complete_callback_(NULL),
      inited_(false),
      decoder_(NULL),
      propagation_cnt_(-1),
      last_frame_width_(0),
      last_frame_height_(0),
      key_frame_required_(true),
      deblock_params_(use_postproc_ ? GetPostProcParamsFromFieldTrialGroup()
                                    : absl::nullopt),
      qp_smoother_(use_postproc_ ? new QpSmoother() : nullptr),
      preferred_output_format_(field_trial::IsEnabled("WebRTC-NV12Decode")
                                   ? VideoFrameBuffer::Type::kNV12
                                   : VideoFrameBuffer::Type::kI420) {}

LibvpxVp8Decoder::~LibvpxVp8Decoder() {
  inited_ = true;  // in order to do the actual release
  Release();
}

int LibvpxVp8Decoder::InitDecode(const VideoCodec* inst, int number_of_cores) {
  int ret_val = Release();
  if (ret_val < 0) {
    return ret_val;
  }
  if (decoder_ == NULL) {
    decoder_ = new vpx_codec_ctx_t;
    memset(decoder_, 0, sizeof(*decoder_));
  }
  vpx_codec_dec_cfg_t cfg;
  // Setting number of threads to a constant value (1)
  cfg.threads = 1;
  cfg.h = cfg.w = 0;  // set after decode

  vpx_codec_flags_t flags = use_postproc_ ? VPX_CODEC_USE_POSTPROC : 0;

  if (vpx_codec_dec_init(decoder_, vpx_codec_vp8_dx(), &cfg, flags)) {
    delete decoder_;
    decoder_ = nullptr;
    return WEBRTC_VIDEO_CODEC_MEMORY;
  }

  propagation_cnt_ = -1;
  inited_ = true;

  // Always start with a complete key frame.
  key_frame_required_ = true;
  if (inst && inst->buffer_pool_size) {
    if (!buffer_pool_.Resize(*inst->buffer_pool_size)) {
      return WEBRTC_VIDEO_CODEC_UNINITIALIZED;
    }
  }
  return WEBRTC_VIDEO_CODEC_OK;
}

int LibvpxVp8Decoder::Decode(const EncodedImage& input_image,
                             bool missing_frames,
                             int64_t /*render_time_ms*/) {
  if (!inited_) {
    return WEBRTC_VIDEO_CODEC_UNINITIALIZED;
  }
  if (decode_complete_callback_ == NULL) {
    return WEBRTC_VIDEO_CODEC_UNINITIALIZED;
  }
  if (input_image.data() == NULL && input_image.size() > 0) {
    // Reset to avoid requesting key frames too often.
    if (propagation_cnt_ > 0)
      propagation_cnt_ = 0;
    return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
  }

// Post process configurations.
  if (use_postproc_) {
    vp8_postproc_cfg_t ppcfg;
    // MFQE enabled to reduce key frame popping.
    ppcfg.post_proc_flag = VP8_MFQE;

    if (kIsArm) {
      RTC_DCHECK(deblock_params_.has_value());
    }
    if (deblock_params_.has_value()) {
      // For low resolutions, use stronger deblocking filter.
      int last_width_x_height = last_frame_width_ * last_frame_height_;
      if (last_width_x_height > 0 && last_width_x_height <= 320 * 240) {
        // Enable the deblock and demacroblocker based on qp thresholds.
        RTC_DCHECK(qp_smoother_);
        int qp = qp_smoother_->GetAvg();
        if (qp > deblock_params_->min_qp) {
          int level = deblock_params_->max_level;
          if (qp < deblock_params_->degrade_qp) {
            // Use lower level.
            level = deblock_params_->max_level *
                    (qp - deblock_params_->min_qp) /
                    (deblock_params_->degrade_qp - deblock_params_->min_qp);
          }
          // Deblocking level only affects VP8_DEMACROBLOCK.
          ppcfg.deblocking_level = std::max(level, 1);
          ppcfg.post_proc_flag |= VP8_DEBLOCK | VP8_DEMACROBLOCK;
        }
      }
    } else {
      // Non-arm with no explicit deblock params set.
      ppcfg.post_proc_flag |= VP8_DEBLOCK;
      // For VGA resolutions and lower, enable the demacroblocker postproc.
      if (last_frame_width_ * last_frame_height_ <= 640 * 360) {
        ppcfg.post_proc_flag |= VP8_DEMACROBLOCK;
      }
      // Strength of deblocking filter. Valid range:[0,16]
      ppcfg.deblocking_level = 3;
    }

    vpx_codec_control(decoder_, VP8_SET_POSTPROC, &ppcfg);
  }

  // Always start with a complete key frame.
  if (key_frame_required_) {
    if (input_image._frameType != VideoFrameType::kVideoFrameKey)
      return WEBRTC_VIDEO_CODEC_ERROR;
    key_frame_required_ = false;
  }
  // Restrict error propagation using key frame requests.
  // Reset on a key frame refresh.
  if (input_image._frameType == VideoFrameType::kVideoFrameKey) {
    propagation_cnt_ = -1;
    // Start count on first loss.
  } else if (missing_frames && propagation_cnt_ == -1) {
    propagation_cnt_ = 0;
  }
  if (propagation_cnt_ >= 0) {
    propagation_cnt_++;
  }

  vpx_codec_iter_t iter = NULL;
  vpx_image_t* img;
  int ret;

  // Check for missing frames.
  if (missing_frames) {
    // Call decoder with zero data length to signal missing frames.
    if (vpx_codec_decode(decoder_, NULL, 0, 0, kDecodeDeadlineRealtime)) {
      // Reset to avoid requesting key frames too often.
      if (propagation_cnt_ > 0)
        propagation_cnt_ = 0;
      return WEBRTC_VIDEO_CODEC_ERROR;
    }
    img = vpx_codec_get_frame(decoder_, &iter);
    iter = NULL;
  }

  const uint8_t* buffer = input_image.data();
  if (input_image.size() == 0) {
    buffer = NULL;  // Triggers full frame concealment.
  }
  if (vpx_codec_decode(decoder_, buffer, input_image.size(), 0,
                       kDecodeDeadlineRealtime)) {
    // Reset to avoid requesting key frames too often.
    if (propagation_cnt_ > 0) {
      propagation_cnt_ = 0;
    }
    return WEBRTC_VIDEO_CODEC_ERROR;
  }

  img = vpx_codec_get_frame(decoder_, &iter);
  int qp;
  vpx_codec_err_t vpx_ret =
      vpx_codec_control(decoder_, VPXD_GET_LAST_QUANTIZER, &qp);
  RTC_DCHECK_EQ(vpx_ret, VPX_CODEC_OK);
  ret = ReturnFrame(img, input_image.Timestamp(), qp, input_image.ColorSpace());
  if (ret != 0) {
    // Reset to avoid requesting key frames too often.
    if (ret < 0 && propagation_cnt_ > 0)
      propagation_cnt_ = 0;
    return ret;
  }
  // Check Vs. threshold
  if (propagation_cnt_ > kVp8ErrorPropagationTh) {
    // Reset to avoid requesting key frames too often.
    propagation_cnt_ = 0;
    return WEBRTC_VIDEO_CODEC_ERROR;
  }
  return WEBRTC_VIDEO_CODEC_OK;
}

int LibvpxVp8Decoder::ReturnFrame(
    const vpx_image_t* img,
    uint32_t timestamp,
    int qp,
    const webrtc::ColorSpace* explicit_color_space) {
  if (img == NULL) {
    // Decoder OK and NULL image => No show frame
    return WEBRTC_VIDEO_CODEC_NO_OUTPUT;
  }
  if (qp_smoother_) {
    if (last_frame_width_ != static_cast<int>(img->d_w) ||
        last_frame_height_ != static_cast<int>(img->d_h)) {
      qp_smoother_->Reset();
    }
    qp_smoother_->Add(qp);
  }
  last_frame_width_ = img->d_w;
  last_frame_height_ = img->d_h;
  // Allocate memory for decoded image.
  rtc::scoped_refptr<VideoFrameBuffer> buffer;

  if (preferred_output_format_ == VideoFrameBuffer::Type::kNV12) {
    // Convert instead of making a copy.
    // Note: libvpx doesn't support creating NV12 image directly.
    // Due to the bitstream structure such a change would just hide the
    // conversion operation inside the decode call.
    rtc::scoped_refptr<NV12Buffer> nv12_buffer =
        buffer_pool_.CreateNV12Buffer(img->d_w, img->d_h);
    buffer = nv12_buffer;
    if (nv12_buffer.get()) {
      libyuv::I420ToNV12(img->planes[VPX_PLANE_Y], img->stride[VPX_PLANE_Y],
                         img->planes[VPX_PLANE_U], img->stride[VPX_PLANE_U],
                         img->planes[VPX_PLANE_V], img->stride[VPX_PLANE_V],
                         nv12_buffer->MutableDataY(), nv12_buffer->StrideY(),
                         nv12_buffer->MutableDataUV(), nv12_buffer->StrideUV(),
                         img->d_w, img->d_h);
    }
  } else {
    rtc::scoped_refptr<I420Buffer> i420_buffer =
        buffer_pool_.CreateI420Buffer(img->d_w, img->d_h);
    buffer = i420_buffer;
    if (i420_buffer.get()) {
      libyuv::I420Copy(img->planes[VPX_PLANE_Y], img->stride[VPX_PLANE_Y],
                       img->planes[VPX_PLANE_U], img->stride[VPX_PLANE_U],
                       img->planes[VPX_PLANE_V], img->stride[VPX_PLANE_V],
                       i420_buffer->MutableDataY(), i420_buffer->StrideY(),
                       i420_buffer->MutableDataU(), i420_buffer->StrideU(),
                       i420_buffer->MutableDataV(), i420_buffer->StrideV(),
                       img->d_w, img->d_h);
    }
  }

  if (!buffer.get()) {
    // Pool has too many pending frames.
    RTC_HISTOGRAM_BOOLEAN("WebRTC.Video.LibvpxVp8Decoder.TooManyPendingFrames",
                          1);
    return WEBRTC_VIDEO_CODEC_NO_OUTPUT;
  }

  VideoFrame decoded_image = VideoFrame::Builder()
                                 .set_video_frame_buffer(buffer)
                                 .set_timestamp_rtp(timestamp)
                                 .set_color_space(explicit_color_space)
                                 .build();
  decode_complete_callback_->Decoded(decoded_image, absl::nullopt, qp);

  return WEBRTC_VIDEO_CODEC_OK;
}

int LibvpxVp8Decoder::RegisterDecodeCompleteCallback(
    DecodedImageCallback* callback) {
  decode_complete_callback_ = callback;
  return WEBRTC_VIDEO_CODEC_OK;
}

int LibvpxVp8Decoder::Release() {
  int ret_val = WEBRTC_VIDEO_CODEC_OK;

  if (decoder_ != NULL) {
    if (inited_) {
      if (vpx_codec_destroy(decoder_)) {
        ret_val = WEBRTC_VIDEO_CODEC_MEMORY;
      }
    }
    delete decoder_;
    decoder_ = NULL;
  }
  buffer_pool_.Release();
  inited_ = false;
  return ret_val;
}

VideoDecoder::DecoderInfo LibvpxVp8Decoder::GetDecoderInfo() const {
  DecoderInfo info;
  info.implementation_name = "libvpx";
  info.is_hardware_accelerated = false;
  return info;
}

const char* LibvpxVp8Decoder::ImplementationName() const {
  return "libvpx";
}
}  // namespace webrtc
