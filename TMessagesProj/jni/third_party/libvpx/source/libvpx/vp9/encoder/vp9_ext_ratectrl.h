/*
 *  Copyright (c) 2020 The WebM project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef VPX_VP9_ENCODER_VP9_EXT_RATECTRL_H_
#define VPX_VP9_ENCODER_VP9_EXT_RATECTRL_H_

#include "vpx/vpx_ext_ratectrl.h"
#include "vp9/encoder/vp9_firstpass.h"

typedef struct EXT_RATECTRL {
  int ready;
  vpx_rc_model_t model;
  vpx_rc_funcs_t funcs;
  vpx_rc_config_t ratectrl_config;
  vpx_rc_firstpass_stats_t rc_firstpass_stats;
} EXT_RATECTRL;

void vp9_extrc_init(EXT_RATECTRL *ext_ratectrl);

void vp9_extrc_create(vpx_rc_funcs_t funcs, vpx_rc_config_t ratectrl_config,
                      EXT_RATECTRL *ext_ratectrl);

void vp9_extrc_delete(EXT_RATECTRL *ext_ratectrl);

void vp9_extrc_send_firstpass_stats(EXT_RATECTRL *ext_ratectrl,
                                    const FIRST_PASS_INFO *first_pass_info);

void vp9_extrc_get_encodeframe_decision(
    EXT_RATECTRL *ext_ratectrl, int show_index, int coding_index,
    FRAME_UPDATE_TYPE update_type,
    RefCntBuffer *ref_frame_bufs[MAX_INTER_REF_FRAMES], int ref_frame_flags,
    vpx_rc_encodeframe_decision_t *encode_frame_decision);

void vp9_extrc_update_encodeframe_result(EXT_RATECTRL *ext_ratectrl,
                                         int64_t bit_count,
                                         const YV12_BUFFER_CONFIG *source_frame,
                                         const YV12_BUFFER_CONFIG *coded_frame,
                                         uint32_t bit_depth,
                                         uint32_t input_bit_depth);

#endif  // VPX_VP9_ENCODER_VP9_EXT_RATECTRL_H_
