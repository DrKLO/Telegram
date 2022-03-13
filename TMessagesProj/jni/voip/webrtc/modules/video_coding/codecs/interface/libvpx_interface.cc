/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/codecs/interface/libvpx_interface.h"

#include <memory>

#include "rtc_base/checks.h"

namespace webrtc {
namespace {
class LibvpxFacade : public LibvpxInterface {
 public:
  LibvpxFacade() = default;
  ~LibvpxFacade() override = default;

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
      case VP8E_SET_CQ_LEVEL:
        return vpx_codec_control(ctx, VP8E_SET_CQ_LEVEL, param);
      case VP8E_SET_MAX_INTRA_BITRATE_PCT:
        return vpx_codec_control(ctx, VP8E_SET_MAX_INTRA_BITRATE_PCT, param);
      case VP9E_SET_MAX_INTER_BITRATE_PCT:
        return vpx_codec_control(ctx, VP9E_SET_MAX_INTER_BITRATE_PCT, param);
      case VP8E_SET_GF_CBR_BOOST_PCT:
        return vpx_codec_control(ctx, VP8E_SET_GF_CBR_BOOST_PCT, param);
      case VP8E_SET_SCREEN_CONTENT_MODE:
        return vpx_codec_control(ctx, VP8E_SET_SCREEN_CONTENT_MODE, param);
      case VP9E_SET_GF_CBR_BOOST_PCT:
        return vpx_codec_control(ctx, VP9E_SET_GF_CBR_BOOST_PCT, param);
      case VP9E_SET_LOSSLESS:
        return vpx_codec_control(ctx, VP9E_SET_LOSSLESS, param);
      case VP9E_SET_FRAME_PARALLEL_DECODING:
        return vpx_codec_control(ctx, VP9E_SET_FRAME_PARALLEL_DECODING, param);
      case VP9E_SET_AQ_MODE:
        return vpx_codec_control(ctx, VP9E_SET_AQ_MODE, param);
      case VP9E_SET_FRAME_PERIODIC_BOOST:
        return vpx_codec_control(ctx, VP9E_SET_FRAME_PERIODIC_BOOST, param);
      case VP9E_SET_NOISE_SENSITIVITY:
        return vpx_codec_control(ctx, VP9E_SET_NOISE_SENSITIVITY, param);
      case VP9E_SET_MIN_GF_INTERVAL:
        return vpx_codec_control(ctx, VP9E_SET_MIN_GF_INTERVAL, param);
      case VP9E_SET_MAX_GF_INTERVAL:
        return vpx_codec_control(ctx, VP9E_SET_MAX_GF_INTERVAL, param);
      case VP9E_SET_TARGET_LEVEL:
        return vpx_codec_control(ctx, VP9E_SET_TARGET_LEVEL, param);
      case VP9E_SET_ROW_MT:
        return vpx_codec_control(ctx, VP9E_SET_ROW_MT, param);
      case VP9E_ENABLE_MOTION_VECTOR_UNIT_TEST:
        return vpx_codec_control(ctx, VP9E_ENABLE_MOTION_VECTOR_UNIT_TEST,
                                 param);
      case VP9E_SET_SVC_INTER_LAYER_PRED:
        return vpx_codec_control(ctx, VP9E_SET_SVC_INTER_LAYER_PRED, param);
      case VP9E_SET_SVC_GF_TEMPORAL_REF:
        return vpx_codec_control(ctx, VP9E_SET_SVC_GF_TEMPORAL_REF, param);
      case VP9E_SET_POSTENCODE_DROP:
        return vpx_codec_control(ctx, VP9E_SET_POSTENCODE_DROP, param);
      default:
        RTC_DCHECK_NOTREACHED() << "Unsupported libvpx ctrl_id: " << ctrl_id;
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
      case VP9E_SET_SVC:
        return vpx_codec_control(ctx, VP9E_SET_SVC, param);
      case VP8E_SET_CPUUSED:
        return vpx_codec_control(ctx, VP8E_SET_CPUUSED, param);
      case VP8E_SET_TOKEN_PARTITIONS:
        return vpx_codec_control(ctx, VP8E_SET_TOKEN_PARTITIONS, param);
      case VP8E_SET_TUNING:
        return vpx_codec_control(ctx, VP8E_SET_TUNING, param);
      case VP9E_SET_TILE_COLUMNS:
        return vpx_codec_control(ctx, VP9E_SET_TILE_COLUMNS, param);
      case VP9E_SET_TILE_ROWS:
        return vpx_codec_control(ctx, VP9E_SET_TILE_ROWS, param);
      case VP9E_SET_TPL:
        return vpx_codec_control(ctx, VP9E_SET_TPL, param);
      case VP9E_SET_ALT_REF_AQ:
        return vpx_codec_control(ctx, VP9E_SET_ALT_REF_AQ, param);
      case VP9E_SET_TUNE_CONTENT:
        return vpx_codec_control(ctx, VP9E_SET_TUNE_CONTENT, param);
      case VP9E_SET_COLOR_SPACE:
        return vpx_codec_control(ctx, VP9E_SET_COLOR_SPACE, param);
      case VP9E_SET_COLOR_RANGE:
        return vpx_codec_control(ctx, VP9E_SET_COLOR_RANGE, param);
      case VP9E_SET_DELTA_Q_UV:
        return vpx_codec_control(ctx, VP9E_SET_DELTA_Q_UV, param);
      case VP9E_SET_DISABLE_OVERSHOOT_MAXQ_CBR:
        return vpx_codec_control(ctx, VP9E_SET_DISABLE_OVERSHOOT_MAXQ_CBR,
                                 param);
      case VP9E_SET_DISABLE_LOOPFILTER:
        return vpx_codec_control(ctx, VP9E_SET_DISABLE_LOOPFILTER, param);

      default:
        if (param >= 0) {
          // Might be intended for uint32_t but int literal used, try fallback.
          return codec_control(ctx, ctrl_id, static_cast<uint32_t>(param));
        }
        RTC_DCHECK_NOTREACHED() << "Unsupported libvpx ctrl_id: " << ctrl_id;
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
      case VP9E_SET_RENDER_SIZE:
        return vpx_codec_control(ctx, VP9E_SET_RENDER_SIZE, param);
      case VP9E_GET_LEVEL:
        return vpx_codec_control(ctx, VP9E_GET_LEVEL, param);
      default:
        RTC_DCHECK_NOTREACHED() << "Unsupported libvpx ctrl_id: " << ctrl_id;
    }
    return VPX_CODEC_ERROR;
  }

  vpx_codec_err_t codec_control(vpx_codec_ctx_t* ctx,
                                vp8e_enc_control_id ctrl_id,
                                vpx_roi_map* param) const override {
    switch (ctrl_id) {
      case VP8E_SET_ROI_MAP:
        return vpx_codec_control(ctx, VP8E_SET_ROI_MAP, param);
      case VP9E_SET_ROI_MAP:
        return vpx_codec_control(ctx, VP9E_SET_ROI_MAP, param);
      default:
        RTC_DCHECK_NOTREACHED() << "Unsupported libvpx ctrl_id: " << ctrl_id;
    }
    return VPX_CODEC_ERROR;
  }

  vpx_codec_err_t codec_control(vpx_codec_ctx_t* ctx,
                                vp8e_enc_control_id ctrl_id,
                                vpx_active_map* param) const override {
    switch (ctrl_id) {
      case VP8E_SET_ACTIVEMAP:
        return vpx_codec_control(ctx, VP8E_SET_ACTIVEMAP, param);
      case VP9E_GET_ACTIVEMAP:
        return vpx_codec_control(ctx, VP8E_SET_ACTIVEMAP, param);
      default:
        RTC_DCHECK_NOTREACHED() << "Unsupported libvpx ctrl_id: " << ctrl_id;
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
        RTC_DCHECK_NOTREACHED() << "Unsupported libvpx ctrl_id: " << ctrl_id;
    }
    return VPX_CODEC_ERROR;
  }

  vpx_codec_err_t codec_control(vpx_codec_ctx_t* ctx,
                                vp8e_enc_control_id ctrl_id,
                                vpx_svc_extra_cfg_t* param) const override {
    switch (ctrl_id) {
      case VP9E_SET_SVC_PARAMETERS:
        return vpx_codec_control_(ctx, VP9E_SET_SVC_PARAMETERS, param);
      default:
        RTC_DCHECK_NOTREACHED() << "Unsupported libvpx ctrl_id: " << ctrl_id;
    }
    return VPX_CODEC_ERROR;
  }

  vpx_codec_err_t codec_control(vpx_codec_ctx_t* ctx,
                                vp8e_enc_control_id ctrl_id,
                                vpx_svc_frame_drop_t* param) const override {
    switch (ctrl_id) {
      case VP9E_SET_SVC_FRAME_DROP_LAYER:
        return vpx_codec_control_(ctx, VP9E_SET_SVC_FRAME_DROP_LAYER, param);
      default:
        RTC_DCHECK_NOTREACHED() << "Unsupported libvpx ctrl_id: " << ctrl_id;
    }
    return VPX_CODEC_ERROR;
  }

  vpx_codec_err_t codec_control(vpx_codec_ctx_t* ctx,
                                vp8e_enc_control_id ctrl_id,
                                void* param) const override {
    switch (ctrl_id) {
      case VP9E_SET_SVC_PARAMETERS:
        return vpx_codec_control_(ctx, VP9E_SET_SVC_PARAMETERS, param);
      case VP9E_REGISTER_CX_CALLBACK:
        return vpx_codec_control_(ctx, VP9E_REGISTER_CX_CALLBACK, param);
      default:
        RTC_DCHECK_NOTREACHED() << "Unsupported libvpx ctrl_id: " << ctrl_id;
    }
    return VPX_CODEC_ERROR;
  }

  vpx_codec_err_t codec_control(vpx_codec_ctx_t* ctx,
                                vp8e_enc_control_id ctrl_id,
                                vpx_svc_layer_id_t* param) const override {
    switch (ctrl_id) {
      case VP9E_SET_SVC_LAYER_ID:
        return vpx_codec_control_(ctx, VP9E_SET_SVC_LAYER_ID, param);
      case VP9E_GET_SVC_LAYER_ID:
        return vpx_codec_control_(ctx, VP9E_GET_SVC_LAYER_ID, param);
      default:
        RTC_DCHECK_NOTREACHED() << "Unsupported libvpx ctrl_id: " << ctrl_id;
    }
    return VPX_CODEC_ERROR;
  }

  vpx_codec_err_t codec_control(
      vpx_codec_ctx_t* ctx,
      vp8e_enc_control_id ctrl_id,
      vpx_svc_ref_frame_config_t* param) const override {
    switch (ctrl_id) {
      case VP9E_SET_SVC_REF_FRAME_CONFIG:
        return vpx_codec_control_(ctx, VP9E_SET_SVC_REF_FRAME_CONFIG, param);
      case VP9E_GET_SVC_REF_FRAME_CONFIG:
        return vpx_codec_control_(ctx, VP9E_GET_SVC_REF_FRAME_CONFIG, param);
      default:
        RTC_DCHECK_NOTREACHED() << "Unsupported libvpx ctrl_id: " << ctrl_id;
    }
    return VPX_CODEC_ERROR;
  }

  vpx_codec_err_t codec_control(
      vpx_codec_ctx_t* ctx,
      vp8e_enc_control_id ctrl_id,
      vpx_svc_spatial_layer_sync_t* param) const override {
    switch (ctrl_id) {
      case VP9E_SET_SVC_SPATIAL_LAYER_SYNC:
        return vpx_codec_control_(ctx, VP9E_SET_SVC_SPATIAL_LAYER_SYNC, param);
      default:
        RTC_DCHECK_NOTREACHED() << "Unsupported libvpx ctrl_id: " << ctrl_id;
    }
    return VPX_CODEC_ERROR;
  }

  vpx_codec_err_t codec_control(vpx_codec_ctx_t* ctx,
                                vp8e_enc_control_id ctrl_id,
                                vpx_rc_funcs_t* param) const override {
    switch (ctrl_id) {
      case VP9E_SET_EXTERNAL_RATE_CONTROL:
        return vpx_codec_control_(ctx, VP9E_SET_EXTERNAL_RATE_CONTROL, param);
      default:
        RTC_DCHECK_NOTREACHED() << "Unsupported libvpx ctrl_id: " << ctrl_id;
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

  const char* codec_error_detail(vpx_codec_ctx_t* ctx) const override {
    return ::vpx_codec_error_detail(ctx);
  }

  const char* codec_error(vpx_codec_ctx_t* ctx) const override {
    return ::vpx_codec_error(ctx);
  }

  const char* codec_err_to_string(vpx_codec_err_t err) const override {
    return ::vpx_codec_err_to_string(err);
  }
};

}  // namespace

std::unique_ptr<LibvpxInterface> LibvpxInterface::Create() {
  return std::make_unique<LibvpxFacade>();
}

}  // namespace webrtc
