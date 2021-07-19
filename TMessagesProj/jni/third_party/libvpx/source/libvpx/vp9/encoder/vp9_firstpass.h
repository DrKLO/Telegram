/*
 *  Copyright (c) 2010 The WebM project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef VPX_VP9_ENCODER_VP9_FIRSTPASS_H_
#define VPX_VP9_ENCODER_VP9_FIRSTPASS_H_

#include <assert.h>

#include "vp9/common/vp9_onyxc_int.h"
#include "vp9/encoder/vp9_lookahead.h"
#include "vp9/encoder/vp9_ratectrl.h"

#ifdef __cplusplus
extern "C" {
#endif

#if CONFIG_FP_MB_STATS

#define FPMB_DCINTRA_MASK 0x01

#define FPMB_MOTION_ZERO_MASK 0x02
#define FPMB_MOTION_LEFT_MASK 0x04
#define FPMB_MOTION_RIGHT_MASK 0x08
#define FPMB_MOTION_UP_MASK 0x10
#define FPMB_MOTION_DOWN_MASK 0x20

#define FPMB_ERROR_SMALL_MASK 0x40
#define FPMB_ERROR_LARGE_MASK 0x80
#define FPMB_ERROR_SMALL_TH 2000
#define FPMB_ERROR_LARGE_TH 48000

typedef struct {
  uint8_t *mb_stats_start;
  uint8_t *mb_stats_end;
} FIRSTPASS_MB_STATS;
#endif

#define INVALID_ROW (-1)

#define MAX_ARF_LAYERS 6
#define SECTION_NOISE_DEF 250.0

typedef struct {
  double frame_mb_intra_factor;
  double frame_mb_brightness_factor;
  double frame_mb_neutral_count;
} FP_MB_FLOAT_STATS;

typedef struct {
  double intra_factor;
  double brightness_factor;
  int64_t coded_error;
  int64_t sr_coded_error;
  int64_t frame_noise_energy;
  int64_t intra_error;
  int intercount;
  int second_ref_count;
  double neutral_count;
  double intra_count_low;   // Coded intra but low variance
  double intra_count_high;  // Coded intra high variance
  int intra_skip_count;
  int image_data_start_row;
  int mvcount;
  int sum_mvr;
  int sum_mvr_abs;
  int sum_mvc;
  int sum_mvc_abs;
  int64_t sum_mvrs;
  int64_t sum_mvcs;
  int sum_in_vectors;
  int intra_smooth_count;
} FIRSTPASS_DATA;

typedef struct {
  double frame;
  double weight;
  double intra_error;
  double coded_error;
  double sr_coded_error;
  double frame_noise_energy;
  double pcnt_inter;
  double pcnt_motion;
  double pcnt_second_ref;
  double pcnt_neutral;
  double pcnt_intra_low;   // Coded intra but low variance
  double pcnt_intra_high;  // Coded intra high variance
  double intra_skip_pct;
  double intra_smooth_pct;    // % of blocks that are smooth
  double inactive_zone_rows;  // Image mask rows top and bottom.
  double inactive_zone_cols;  // Image mask columns at left and right edges.
  double MVr;
  double mvr_abs;
  double MVc;
  double mvc_abs;
  double MVrv;
  double MVcv;
  double mv_in_out_count;
  double duration;
  double count;
  int64_t spatial_layer_id;
} FIRSTPASS_STATS;

typedef enum {
  KF_UPDATE = 0,
  LF_UPDATE = 1,
  GF_UPDATE = 2,
  ARF_UPDATE = 3,
  OVERLAY_UPDATE = 4,
  MID_OVERLAY_UPDATE = 5,
  USE_BUF_FRAME = 6,  // Use show existing frame, no ref buffer update
  FRAME_UPDATE_TYPES = 7
} FRAME_UPDATE_TYPE;

#define FC_ANIMATION_THRESH 0.15
typedef enum {
  FC_NORMAL = 0,
  FC_GRAPHICS_ANIMATION = 1,
  FRAME_CONTENT_TYPES = 2
} FRAME_CONTENT_TYPE;

typedef struct {
  unsigned char index;
  RATE_FACTOR_LEVEL rf_level[MAX_STATIC_GF_GROUP_LENGTH + 2];
  FRAME_UPDATE_TYPE update_type[MAX_STATIC_GF_GROUP_LENGTH + 2];
  unsigned char arf_src_offset[MAX_STATIC_GF_GROUP_LENGTH + 2];
  unsigned char layer_depth[MAX_STATIC_GF_GROUP_LENGTH + 2];
  unsigned char frame_gop_index[MAX_STATIC_GF_GROUP_LENGTH + 2];
  int bit_allocation[MAX_STATIC_GF_GROUP_LENGTH + 2];
  int gfu_boost[MAX_STATIC_GF_GROUP_LENGTH + 2];

  int frame_start;
  int frame_end;
  // TODO(jingning): The array size of arf_stack could be reduced.
  int arf_index_stack[MAX_LAG_BUFFERS * 2];
  int top_arf_idx;
  int stack_size;
  int gf_group_size;
  int max_layer_depth;
  int allowed_max_layer_depth;
  int group_noise_energy;
} GF_GROUP;

typedef struct {
  const FIRSTPASS_STATS *stats;
  int num_frames;
} FIRST_PASS_INFO;

static INLINE void fps_init_first_pass_info(FIRST_PASS_INFO *first_pass_info,
                                            const FIRSTPASS_STATS *stats,
                                            int num_frames) {
  first_pass_info->stats = stats;
  first_pass_info->num_frames = num_frames;
}

static INLINE int fps_get_num_frames(const FIRST_PASS_INFO *first_pass_info) {
  return first_pass_info->num_frames;
}

static INLINE const FIRSTPASS_STATS *fps_get_frame_stats(
    const FIRST_PASS_INFO *first_pass_info, int show_idx) {
  if (show_idx < 0 || show_idx >= first_pass_info->num_frames) {
    return NULL;
  }
  return &first_pass_info->stats[show_idx];
}

typedef struct {
  unsigned int section_intra_rating;
  unsigned int key_frame_section_intra_rating;
  FIRSTPASS_STATS total_stats;
  FIRSTPASS_STATS this_frame_stats;
  const FIRSTPASS_STATS *stats_in;
  const FIRSTPASS_STATS *stats_in_start;
  const FIRSTPASS_STATS *stats_in_end;
  FIRST_PASS_INFO first_pass_info;
  FIRSTPASS_STATS total_left_stats;
  int first_pass_done;
  int64_t bits_left;
  double mean_mod_score;
  double normalized_score_left;
  double mb_av_energy;
  double mb_smooth_pct;

#if CONFIG_FP_MB_STATS
  uint8_t *frame_mb_stats_buf;
  uint8_t *this_frame_mb_stats;
  FIRSTPASS_MB_STATS firstpass_mb_stats;
#endif

  FP_MB_FLOAT_STATS *fp_mb_float_stats;

  // An indication of the content type of the current frame
  FRAME_CONTENT_TYPE fr_content_type;

  // Projected total bits available for a key frame group of frames
  int64_t kf_group_bits;

  // Error score of frames still to be coded in kf group
  double kf_group_error_left;

  double bpm_factor;
  int rolling_arf_group_target_bits;
  int rolling_arf_group_actual_bits;

  int sr_update_lag;
  int kf_zeromotion_pct;
  int last_kfgroup_zeromotion_pct;
  int active_worst_quality;
  int baseline_active_worst_quality;
  int extend_minq;
  int extend_maxq;
  int extend_minq_fast;
  int arnr_strength_adjustment;
  int last_qindex_of_arf_layer[MAX_ARF_LAYERS];

  GF_GROUP gf_group;
} TWO_PASS;

struct VP9_COMP;
struct ThreadData;
struct TileDataEnc;

void vp9_init_first_pass(struct VP9_COMP *cpi);
void vp9_first_pass(struct VP9_COMP *cpi, const struct lookahead_entry *source);
void vp9_end_first_pass(struct VP9_COMP *cpi);

void vp9_first_pass_encode_tile_mb_row(struct VP9_COMP *cpi,
                                       struct ThreadData *td,
                                       FIRSTPASS_DATA *fp_acc_data,
                                       struct TileDataEnc *tile_data,
                                       MV *best_ref_mv, int mb_row);

void vp9_init_second_pass(struct VP9_COMP *cpi);
void vp9_rc_get_second_pass_params(struct VP9_COMP *cpi);

// Post encode update of the rate control parameters for 2-pass
void vp9_twopass_postencode_update(struct VP9_COMP *cpi);

void calculate_coded_size(struct VP9_COMP *cpi, int *scaled_frame_width,
                          int *scaled_frame_height);

struct VP9EncoderConfig;
int vp9_get_frames_to_next_key(const struct VP9EncoderConfig *oxcf,
                               const FRAME_INFO *frame_info,
                               const FIRST_PASS_INFO *first_pass_info,
                               int kf_show_idx, int min_gf_interval);
#if CONFIG_RATE_CTRL
/* Call this function to get info about the next group of pictures.
 * This function should be called after vp9_create_compressor() when encoding
 * starts or after vp9_get_compressed_data() when the encoding process of
 * the last group of pictures is just finished.
 */
void vp9_get_next_group_of_picture(const struct VP9_COMP *cpi,
                                   int *first_is_key_frame, int *use_alt_ref,
                                   int *coding_frame_count, int *first_show_idx,
                                   int *last_gop_use_alt_ref);

/*!\brief Call this function before coding a new group of pictures to get
 * information about it.
 * \param[in] oxcf                 Encoder config
 * \param[in] frame_info           Frame info
 * \param[in] first_pass_info      First pass stats
 * \param[in] rc                   Rate control state
 * \param[in] show_idx             Show index of the first frame in the group
 * \param[in] multi_layer_arf      Is multi-layer alternate reference used
 * \param[in] allow_alt_ref        Is alternate reference allowed
 * \param[in] first_is_key_frame   Is the first frame in the group a key frame
 * \param[in] last_gop_use_alt_ref Does the last group use alternate reference
 *
 * \param[out] use_alt_ref         Does this group use alternate reference
 *
 * \return Returns coding frame count
 */
int vp9_get_gop_coding_frame_count(const struct VP9EncoderConfig *oxcf,
                                   const FRAME_INFO *frame_info,
                                   const FIRST_PASS_INFO *first_pass_info,
                                   const RATE_CONTROL *rc, int show_idx,
                                   int multi_layer_arf, int allow_alt_ref,
                                   int first_is_key_frame,
                                   int last_gop_use_alt_ref, int *use_alt_ref);

int vp9_get_coding_frame_num(const struct VP9EncoderConfig *oxcf,
                             const FRAME_INFO *frame_info,
                             const FIRST_PASS_INFO *first_pass_info,
                             int multi_layer_arf, int allow_alt_ref);

/*!\brief Compute a key frame binary map indicates whether key frames appear
 * in the corresponding positions. The passed in key_frame_map must point to an
 * integer array with length equal to first_pass_info->num_frames, which is the
 * number of show frames in the video.
 */
void vp9_get_key_frame_map(const struct VP9EncoderConfig *oxcf,
                           const FRAME_INFO *frame_info,
                           const FIRST_PASS_INFO *first_pass_info,
                           int *key_frame_map);
#endif  // CONFIG_RATE_CTRL

FIRSTPASS_STATS vp9_get_frame_stats(const TWO_PASS *twopass);
FIRSTPASS_STATS vp9_get_total_stats(const TWO_PASS *twopass);

#ifdef __cplusplus
}  // extern "C"
#endif

#endif  // VPX_VP9_ENCODER_VP9_FIRSTPASS_H_
