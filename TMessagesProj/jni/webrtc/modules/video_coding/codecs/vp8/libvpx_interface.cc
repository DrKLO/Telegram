/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/codecs/vp8/libvpx_interface.h"

#include <memory>

#include "rtc_base/checks.h"

namespace webrtc {
namespace {
class LibvpxVp8Facade : public LibvpxInterface {
 public:
  LibvpxVp8Facade() = default;
  ~LibvpxVp8Facade() override = default;

  vpx_image_t* img_alloc(vpx_image_t* img,
                         vpx_img_fmt_t fmt,
                         unsigned int d_w,
                         unsigned int d_h,
                         unsigned int align) const override {
    return ::vpx_img_alloc(img, fmt, d_w, d_h, align);
  }

  vpx_image_t* img_wrap(vpx_image_t* img,
                        vpx_img_fmt_t fmt,
                        unsigned int d_w,
                        unsigned int d_h,
                        unsigned int stride_align,
                        unsigned char* img_data) const override {
    return ::vpx_img_wrap(img, fmt, d_w, d_h, stride_align, img_data);
  }

  void img_free(vpx_image_t* img) const override { ::vpx_img_free(img); }

  vpx_codec_err_t codec_enc_config_set(
      vpx_codec_ctx_t* ctx,
      const vpx_codec_enc_cfg_t* cfg) const override {
    return ::vpx_codec_enc_config_set(ctx, cfg);
  }

  vpx_codec_err_t codec_enc_config_default(vpx_codec_iface_t* iface,
                                           vpx_codec_enc_cfg_t* cfg,
                                           unsigned int usage) const override {
    return ::vpx_codec_enc_config_default(iface, cfg, usage);
  }

  vpx_codec_err_t codec_enc_init(vpx_codec_ctx_t* ctx,
                                 vpx_codec_iface_t* iface,
                                 const vpx_codec_enc_cfg_t* cfg,
                                 vpx_codec_flags_t flags) const override {
    return ::vpx_codec_enc_init(ctx, iface, cfg, flags);
  }

  vpx_codec_err_t codec_enc_init_multi(vpx_codec_ctx_t* ctx,
                                       vpx_codec_iface_t* iface,
                                       vpx_codec_enc_cfg_t* cfg,
                                       int num_enc,
                                       vpx_codec_flags_t flags,
                                       vpx_rational_t* dsf) const override {
    return ::vpx_codec_enc_init_multi(ctx, iface, cfg, num_enc, flags, dsf);
  }

  vpx_codec_err_t codec_destroy(vpx_codec_ctx_t* ctx) const override {
    return ::vpx_codec_destroy(ctx);
  }

  // For types related to these parameters, see section
  // "VP8 encoder control function parameter type" in vpx/vp8cx.h.

  vpx_codec_err_t codec_control(vpx_codec_ctx_t* ctx,
                                vp8e_enc_control_id ctrl_id,
                                uint32_t param) const override {
    // We need an explicit call for each type since vpx_codec_control is a
    // macro that gets expanded into another call based on the parameter name.
    switch (ctrl_id) {
      case VP8E_SET_ENABLEAUTOALTREF:
        return vpx_codec_control(ctx, VP8E_SET_ENABLEAUTOALTREF, param);
      case VP8E_SET_NOISE_SENSITIVITY:
        return vpx_codec_control(ctx, VP8E_SET_NOISE_SENSITIVITY, param);
      case VP8E_SET_SHARPNESS:
        return vpx_codec_control(ctx, VP8E_SET_SHARPNESS, param);
      case VP8E_SET_STATIC_THRESHOLD:
        return vpx_codec_control(ctx, VP8E_SET_STATIC_THRESHOLD, param);
      case VP8E_SET_ARNR_MAXFRAMES:
        return vpx_codec_control(ctx, VP8E_SET_ARNR_MAXFRAMES, param);
      case VP8E_SET_ARNR_STRENGTH:
        return vpx_codec_control(ctx, VP8E_SET_ARNR_STRENGTH, param);
      case VP8E_SET_ARNR_TYPE:
        RTC_NOTREACHED() << "VP8E_SET_ARNR_TYPE is deprecated.";
        return VPX_CODEC_UNSUP_FEATURE;
      case VP8E_SET_CQ_LEVEL:
        return vpx_codec_control(ctx, VP8E_SET_CQ_LEVEL, param);
      case VP8E_SET_MAX_INTRA_BITRATE_PCT:
        return vpx_codec_control(ctx, VP8E_SET_MAX_INTRA_BITRATE_PCT, param);
      case VP8E_SET_GF_CBR_BOOST_PCT:
        return vpx_codec_control(ctx, VP8E_SET_GF_CBR_BOOST_PCT, param);
      case VP8E_SET_SCREEN_CONTENT_MODE:
        return vpx_codec_control(ctx, VP8E_SET_SCREEN_CONTENT_MODE, param);
      default:
        RTC_NOTREACHED() << "Unsupported libvpx ctrl_id: " << ctrl_id;
    }
    return VPX_CODEC_ERROR;
  }

  vpx_codec_err_t codec_control(vpx_codec_ctx_t* ctx,
                                vp8e_enc_control_id ctrl_id,
                                int param) const override {
    switch (ctrl_id) {
      case VP8E_SET_FRAME_FLAGS:
        return vpx_codec_control(ctx, VP8E_SET_FRAME_FLAGS, param);
      case VP8E_SET_TEMPORAL_LAYER_ID:
        return vpx_codec_control(ctx, VP8E_SET_TEMPORAL_LAYER_ID, param);
      case VP8E_SET_CPUUSED:
        return vpx_codec_control(ctx, VP8E_SET_CPUUSED, param);
      case VP8E_SET_TOKEN_PARTITIONS:
        return vpx_codec_control(ctx, VP8E_SET_TOKEN_PARTITIONS, param);
      case VP8E_SET_TUNING:
        return vpx_codec_control(ctx, VP8E_SET_TUNING, param);

      default:
        RTC_NOTREACHED() << "Unsupported libvpx ctrl_id: " << ctrl_id;
    }
    return VPX_CODEC_ERROR;
  }

  vpx_codec_err_t codec_control(vpx_codec_ctx_t* ctx,
                                vp8e_enc_control_id ctrl_id,
                                int* param) const override {
    switch (ctrl_id) {
      case VP8E_GET_LAST_QUANTIZER:
        return vpx_codec_control(ctx, VP8E_GET_LAST_QUANTIZER, param);
      case VP8E_GET_LAST_QUANTIZER_64:
        return vpx_codec_control(ctx, VP8E_GET_LAST_QUANTIZER_64, param);
      default:
        RTC_NOTREACHED() << "Unsupported libvpx ctrl_id: " << ctrl_id;
    }
    return VPX_CODEC_ERROR;
  }

  vpx_codec_err_t codec_control(vpx_codec_ctx_t* ctx,
                                vp8e_enc_control_id ctrl_id,
                                vpx_roi_map* param) const override {
    switch (ctrl_id) {
      case VP8E_SET_ROI_MAP:
        return vpx_codec_control(ctx, VP8E_SET_ROI_MAP, param);
      default:
        RTC_NOTREACHED() << "Unsupported libvpx ctrl_id: " << ctrl_id;
    }
    return VPX_CODEC_ERROR;
  }

  vpx_codec_err_t codec_control(vpx_codec_ctx_t* ctx,
                                vp8e_enc_control_id ctrl_id,
                                vpx_active_map* param) const override {
    switch (ctrl_id) {
      case VP8E_SET_ACTIVEMAP:
        return vpx_codec_control(ctx, VP8E_SET_ACTIVEMAP, param);
      default:
        RTC_NOTREACHED() << "Unsupported libvpx ctrl_id: " << ctrl_id;
    }
    return VPX_CODEC_ERROR;
  }

  vpx_codec_err_t codec_control(vpx_codec_ctx_t* ctx,
                                vp8e_enc_control_id ctrl_id,
                                vpx_scaling_mode* param) const override {
    switch (ctrl_id) {
      case VP8E_SET_SCALEMODE:
        return vpx_codec_control(ctx, VP8E_SET_SCALEMODE, param);
      default:
        RTC_NOTREACHED() << "Unsupported libvpx ctrl_id: " << ctrl_id;
    }
    return VPX_CODEC_ERROR;
  }

  vpx_codec_err_t codec_encode(vpx_codec_ctx_t* ctx,
                               const vpx_image_t* img,
                               vpx_codec_pts_t pts,
                               uint64_t duration,
                               vpx_enc_frame_flags_t flags,
                               uint64_t deadline) const override {
    return ::vpx_codec_encode(ctx, img, pts, duration, flags, deadline);
  }

  const vpx_codec_cx_pkt_t* codec_get_cx_data(
      vpx_codec_ctx_t* ctx,
      vpx_codec_iter_t* iter) const override {
    return ::vpx_codec_get_cx_data(ctx, iter);
  }
};

}  // namespace

std::unique_ptr<LibvpxInterface> LibvpxInterface::CreateEncoder() {
  return std::make_unique<LibvpxVp8Facade>();
}

}  // namespace webrtc
