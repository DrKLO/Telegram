/*
 *  Copyright (c) 2020 The WebM project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "vp9/ratectrl_rtc.h"

#include <new>

#include "vp9/encoder/vp9_encoder.h"
#include "vp9/encoder/vp9_picklpf.h"
#include "vpx/vp8cx.h"
#include "vpx/vpx_codec.h"

namespace libvpx {

std::unique_ptr<VP9RateControlRTC> VP9RateControlRTC::Create(
    const VP9RateControlRtcConfig &cfg) {
  std::unique_ptr<VP9RateControlRTC> rc_api(new (std::nothrow)
                                                VP9RateControlRTC());
  if (!rc_api) return nullptr;
  rc_api->cpi_ = static_cast<VP9_COMP *>(vpx_memalign(32, sizeof(*cpi_)));
  if (rc_api->cpi_ == nullptr) {
    return nullptr;
  }
  rc_api->InitRateControl(cfg);
  return rc_api;
}

void VP9RateControlRTC::InitRateControl(const VP9RateControlRtcConfig &rc_cfg) {
  VP9_COMMON *cm = &cpi_->common;
  VP9EncoderConfig *oxcf = &cpi_->oxcf;
  RATE_CONTROL *const rc = &cpi_->rc;
  cm->profile = PROFILE_0;
  cm->bit_depth = VPX_BITS_8;
  cm->show_frame = 1;
  oxcf->rc_mode = VPX_CBR;
  oxcf->pass = 0;
  oxcf->aq_mode = NO_AQ;
  oxcf->content = VP9E_CONTENT_DEFAULT;
  oxcf->drop_frames_water_mark = 0;

  UpdateRateControl(rc_cfg);

  cpi_->use_svc = (cpi_->svc.number_spatial_layers > 1 ||
                   cpi_->svc.number_temporal_layers > 1)
                      ? 1
                      : 0;

  rc->rc_1_frame = 0;
  rc->rc_2_frame = 0;
  vp9_rc_init_minq_luts();
  vp9_rc_init(oxcf, 0, rc);
  cpi_->sf.use_nonrd_pick_mode = 1;
  cm->current_video_frame = 0;
}

void VP9RateControlRTC::UpdateRateControl(
    const VP9RateControlRtcConfig &rc_cfg) {
  VP9_COMMON *cm = &cpi_->common;
  VP9EncoderConfig *oxcf = &cpi_->oxcf;
  RATE_CONTROL *const rc = &cpi_->rc;

  cm->width = rc_cfg.width;
  cm->height = rc_cfg.height;
  oxcf->width = rc_cfg.width;
  oxcf->height = rc_cfg.height;
  oxcf->worst_allowed_q = vp9_quantizer_to_qindex(rc_cfg.max_quantizer);
  oxcf->best_allowed_q = vp9_quantizer_to_qindex(rc_cfg.min_quantizer);
  rc->worst_quality = oxcf->worst_allowed_q;
  rc->best_quality = oxcf->best_allowed_q;
  oxcf->target_bandwidth = 1000 * rc_cfg.target_bandwidth;
  oxcf->starting_buffer_level_ms = rc_cfg.buf_initial_sz;
  oxcf->optimal_buffer_level_ms = rc_cfg.buf_optimal_sz;
  oxcf->maximum_buffer_size_ms = rc_cfg.buf_sz;
  oxcf->under_shoot_pct = rc_cfg.undershoot_pct;
  oxcf->over_shoot_pct = rc_cfg.overshoot_pct;
  oxcf->ss_number_layers = rc_cfg.ss_number_layers;
  oxcf->ts_number_layers = rc_cfg.ts_number_layers;
  oxcf->temporal_layering_mode = (VP9E_TEMPORAL_LAYERING_MODE)(
      (rc_cfg.ts_number_layers > 1) ? rc_cfg.ts_number_layers : 0);

  cpi_->oxcf.rc_max_intra_bitrate_pct = rc_cfg.max_intra_bitrate_pct;
  cpi_->framerate = rc_cfg.framerate;
  cpi_->svc.number_spatial_layers = rc_cfg.ss_number_layers;
  cpi_->svc.number_temporal_layers = rc_cfg.ts_number_layers;

  for (int sl = 0; sl < cpi_->svc.number_spatial_layers; ++sl) {
    for (int tl = 0; tl < cpi_->svc.number_temporal_layers; ++tl) {
      const int layer =
          LAYER_IDS_TO_IDX(sl, tl, cpi_->svc.number_temporal_layers);
      LAYER_CONTEXT *lc = &cpi_->svc.layer_context[layer];
      RATE_CONTROL *const lrc = &lc->rc;
      oxcf->layer_target_bitrate[layer] =
          1000 * rc_cfg.layer_target_bitrate[layer];
      lrc->worst_quality =
          vp9_quantizer_to_qindex(rc_cfg.max_quantizers[layer]);
      lrc->best_quality = vp9_quantizer_to_qindex(rc_cfg.min_quantizers[layer]);
      lc->scaling_factor_num = rc_cfg.scaling_factor_num[sl];
      lc->scaling_factor_den = rc_cfg.scaling_factor_den[sl];
      oxcf->ts_rate_decimator[tl] = rc_cfg.ts_rate_decimator[tl];
    }
  }
  vp9_set_rc_buffer_sizes(cpi_);
  vp9_new_framerate(cpi_, cpi_->framerate);
  if (cpi_->svc.number_temporal_layers > 1 ||
      cpi_->svc.number_spatial_layers > 1) {
    if (cm->current_video_frame == 0) vp9_init_layer_context(cpi_);
    vp9_update_layer_context_change_config(cpi_,
                                           (int)cpi_->oxcf.target_bandwidth);
  }
  vp9_check_reset_rc_flag(cpi_);
}

void VP9RateControlRTC::ComputeQP(const VP9FrameParamsQpRTC &frame_params) {
  VP9_COMMON *const cm = &cpi_->common;
  int width, height;
  cpi_->svc.spatial_layer_id = frame_params.spatial_layer_id;
  cpi_->svc.temporal_layer_id = frame_params.temporal_layer_id;
  if (cpi_->svc.number_spatial_layers > 1) {
    const int layer = LAYER_IDS_TO_IDX(cpi_->svc.spatial_layer_id,
                                       cpi_->svc.temporal_layer_id,
                                       cpi_->svc.number_temporal_layers);
    LAYER_CONTEXT *lc = &cpi_->svc.layer_context[layer];
    get_layer_resolution(cpi_->oxcf.width, cpi_->oxcf.height,
                         lc->scaling_factor_num, lc->scaling_factor_den, &width,
                         &height);
    cm->width = width;
    cm->height = height;
  }
  vp9_set_mb_mi(cm, cm->width, cm->height);
  cm->frame_type = frame_params.frame_type;
  cpi_->refresh_golden_frame = (cm->frame_type == KEY_FRAME) ? 1 : 0;
  cpi_->sf.use_nonrd_pick_mode = 1;
  if (cpi_->svc.number_spatial_layers == 1 &&
      cpi_->svc.number_temporal_layers == 1) {
    int target;
    if (frame_is_intra_only(cm))
      target = vp9_calc_iframe_target_size_one_pass_cbr(cpi_);
    else
      target = vp9_calc_pframe_target_size_one_pass_cbr(cpi_);
    vp9_rc_set_frame_target(cpi_, target);
    vp9_update_buffer_level_preencode(cpi_);
  } else {
    vp9_update_temporal_layer_framerate(cpi_);
    vp9_restore_layer_context(cpi_);
    vp9_rc_get_svc_params(cpi_);
  }
  int bottom_index, top_index;
  cpi_->common.base_qindex =
      vp9_rc_pick_q_and_bounds(cpi_, &bottom_index, &top_index);
}

int VP9RateControlRTC::GetQP() const { return cpi_->common.base_qindex; }

int VP9RateControlRTC::GetLoopfilterLevel() const {
  struct loopfilter *const lf = &cpi_->common.lf;
  vp9_pick_filter_level(nullptr, cpi_, LPF_PICK_FROM_Q);
  return lf->filter_level;
}

void VP9RateControlRTC::PostEncodeUpdate(uint64_t encoded_frame_size) {
  vp9_rc_postencode_update(cpi_, encoded_frame_size);
  if (cpi_->svc.number_spatial_layers > 1 ||
      cpi_->svc.number_temporal_layers > 1)
    vp9_save_layer_context(cpi_);
  cpi_->common.current_video_frame++;
}

}  // namespace libvpx
