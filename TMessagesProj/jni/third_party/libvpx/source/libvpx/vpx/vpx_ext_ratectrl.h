/*
 *  Copyright (c) 2020 The WebM project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef VPX_VPX_VPX_EXT_RATECTRL_H_
#define VPX_VPX_VPX_EXT_RATECTRL_H_

#ifdef __cplusplus
extern "C" {
#endif

#include "./vpx_integer.h"

/*!\brief Abstract rate control model handler
 *
 * The encoder will receive the model handler from create_model() defined in
 * vpx_rc_funcs_t.
 */
typedef void *vpx_rc_model_t;

/*!\brief Encode frame decision made by the external rate control model
 *
 * The encoder will receive the decision from the external rate control model
 * through get_encodeframe_decision() defined in vpx_rc_funcs_t.
 */
typedef struct vpx_rc_encodeframe_decision {
  int q_index; /**< Quantizer step index [0..255]*/
} vpx_rc_encodeframe_decision_t;

/*!\brief Information for the frame to be encoded.
 *
 * The encoder will send the information to external rate control model through
 * get_encodeframe_decision() defined in vpx_rc_funcs_t.
 *
 */
typedef struct vpx_rc_encodeframe_info {
  /*!
   * 0: Key frame
   * 1: Inter frame
   * 2: Alternate reference frame
   * 3: Overlay frame
   * 4: Golden frame
   */
  int frame_type;
  int show_index;                  /**< display index, starts from zero*/
  int coding_index;                /**< coding index, starts from zero*/
  int ref_frame_coding_indexes[3]; /**< three reference frames' coding indices*/
  /*!
   * The validity of the three reference frames.
   * 0: Invalid
   * 1: Valid
   */
  int ref_frame_valid_list[3];
} vpx_rc_encodeframe_info_t;

/*!\brief Frame coding result
 *
 * The encoder will send the result to the external rate control model through
 * update_encodeframe_result() defined in vpx_rc_funcs_t.
 */
typedef struct vpx_rc_encodeframe_result {
  int64_t sse;         /**< sum of squared error of the reconstructed frame */
  int64_t bit_count;   /**< number of bits spent on coding the frame*/
  int64_t pixel_count; /**< number of pixels in YUV planes of the frame*/
} vpx_rc_encodeframe_result_t;

/*!\brief Status returned by rate control callback functions.
 */
typedef enum vpx_rc_status {
  VPX_RC_OK = 0,
  VPX_RC_ERROR = 1,
} vpx_rc_status_t;

/*!\brief First pass frame stats
 * This is a mirror of vp9's FIRSTPASS_STATS except that spatial_layer_id is
 * omitted
 */
typedef struct vpx_rc_frame_stats {
  /*!
   * Frame number in display order, if stats are for a single frame.
   * No real meaning for a collection of frames.
   */
  double frame;
  /*!
   * Weight assigned to this frame (or total weight for the collection of
   * frames) currently based on intra factor and brightness factor. This is used
   * to distribute bits between easier and harder frames.
   */
  double weight;
  /*!
   * Intra prediction error.
   */
  double intra_error;
  /*!
   * Best of intra pred error and inter pred error using last frame as ref.
   */
  double coded_error;
  /*!
   * Best of intra pred error and inter pred error using golden frame as ref.
   */
  double sr_coded_error;
  /*!
   * Estimate the noise energy of the current frame.
   */
  double frame_noise_energy;
  /*!
   * Percentage of blocks with inter pred error < intra pred error.
   */
  double pcnt_inter;
  /*!
   * Percentage of blocks using (inter prediction and) non-zero motion vectors.
   */
  double pcnt_motion;
  /*!
   * Percentage of blocks where golden frame was better than last or intra:
   * inter pred error using golden frame < inter pred error using last frame and
   * inter pred error using golden frame < intra pred error
   */
  double pcnt_second_ref;
  /*!
   * Percentage of blocks where intra and inter prediction errors were very
   * close. Note that this is a 'weighted count', that is, the so blocks may be
   * weighted by how close the two errors were.
   */
  double pcnt_neutral;
  /*!
   * Percentage of blocks that have intra error < inter error and inter error <
   * LOW_I_THRESH LOW_I_THRESH = 24000 using bit_depth 8 LOW_I_THRESH = 24000 <<
   * 4 using bit_depth 10 LOW_I_THRESH = 24000 << 8 using bit_depth 12
   */
  double pcnt_intra_low;
  /*!
   * Percentage of blocks that have intra error < inter error and intra error <
   * LOW_I_THRESH but inter error >= LOW_I_THRESH LOW_I_THRESH = 24000 using
   * bit_depth 8 LOW_I_THRESH = 24000 << 4 using bit_depth 10 LOW_I_THRESH =
   * 24000 << 8 using bit_depth 12
   */
  double pcnt_intra_high;
  /*!
   * Percentage of blocks that have almost no intra error residual
   * (i.e. are in effect completely flat and untextured in the intra
   * domain). In natural videos this is uncommon, but it is much more
   * common in animations, graphics and screen content, so may be used
   * as a signal to detect these types of content.
   */
  double intra_skip_pct;
  /*!
   * Percentage of blocks that have intra error < SMOOTH_INTRA_THRESH
   * SMOOTH_INTRA_THRESH = 4000 using bit_depth 8
   * SMOOTH_INTRA_THRESH = 4000 << 4 using bit_depth 10
   * SMOOTH_INTRA_THRESH = 4000 << 8 using bit_depth 12
   */
  double intra_smooth_pct;
  /*!
   * Image mask rows top and bottom.
   */
  double inactive_zone_rows;
  /*!
   * Image mask columns at left and right edges.
   */
  double inactive_zone_cols;
  /*!
   * Average of row motion vectors.
   */
  double MVr;
  /*!
   * Mean of absolute value of row motion vectors.
   */
  double mvr_abs;
  /*!
   * Mean of column motion vectors.
   */
  double MVc;
  /*!
   * Mean of absolute value of column motion vectors.
   */
  double mvc_abs;
  /*!
   * Variance of row motion vectors.
   */
  double MVrv;
  /*!
   * Variance of column motion vectors.
   */
  double MVcv;
  /*!
   * Value in range [-1,1] indicating fraction of row and column motion vectors
   * that point inwards (negative MV value) or outwards (positive MV value).
   * For example, value of 1 indicates, all row/column MVs are inwards.
   */
  double mv_in_out_count;
  /*!
   * Duration of the frame / collection of frames.
   */
  double duration;
  /*!
   * 1.0 if stats are for a single frame, OR
   * Number of frames in this collection for which the stats are accumulated.
   */
  double count;
} vpx_rc_frame_stats_t;

/*!\brief Collection of first pass frame stats
 */
typedef struct vpx_rc_firstpass_stats {
  /*!
   * Pointer to first pass frame stats.
   * The pointed array of vpx_rc_frame_stats_t should have length equal to
   * number of show frames in the video.
   */
  vpx_rc_frame_stats_t *frame_stats;
  /*!
   * Number of show frames in the video.
   */
  int num_frames;
} vpx_rc_firstpass_stats_t;

/*!\brief Encode config sent to external rate control model
 */
typedef struct vpx_rc_config {
  int frame_width;      /**< frame width */
  int frame_height;     /**< frame height */
  int show_frame_count; /**< number of visible frames in the video */
  /*!
   * Target bitrate in kilobytes per second
   */
  int target_bitrate_kbps;
  int frame_rate_num; /**< numerator of frame rate */
  int frame_rate_den; /**< denominator of frame rate */
} vpx_rc_config_t;

/*!\brief Create an external rate control model callback prototype
 *
 * This callback is invoked by the encoder to create an external rate control
 * model.
 *
 * \param[in]  priv               Callback's private data
 * \param[in]  ratectrl_config    Pointer to vpx_rc_config_t
 * \param[out] rate_ctrl_model_pt Pointer to vpx_rc_model_t
 */
typedef vpx_rc_status_t (*vpx_rc_create_model_cb_fn_t)(
    void *priv, const vpx_rc_config_t *ratectrl_config,
    vpx_rc_model_t *rate_ctrl_model_pt);

/*!\brief Send first pass stats to the external rate control model callback
 * prototype
 *
 * This callback is invoked by the encoder to send first pass stats to the
 * external rate control model.
 *
 * \param[in]  rate_ctrl_model    rate control model
 * \param[in]  first_pass_stats   first pass stats
 */
typedef vpx_rc_status_t (*vpx_rc_send_firstpass_stats_cb_fn_t)(
    vpx_rc_model_t rate_ctrl_model,
    const vpx_rc_firstpass_stats_t *first_pass_stats);

/*!\brief Receive encode frame decision callback prototype
 *
 * This callback is invoked by the encoder to receive encode frame decision from
 * the external rate control model.
 *
 * \param[in]  rate_ctrl_model    rate control model
 * \param[in]  encode_frame_info  information of the coding frame
 * \param[out] frame_decision     encode decision of the coding frame
 */
typedef vpx_rc_status_t (*vpx_rc_get_encodeframe_decision_cb_fn_t)(
    vpx_rc_model_t rate_ctrl_model,
    const vpx_rc_encodeframe_info_t *encode_frame_info,
    vpx_rc_encodeframe_decision_t *frame_decision);

/*!\brief Update encode frame result callback prototype
 *
 * This callback is invoked by the encoder to update encode frame result to the
 * external rate control model.
 *
 * \param[in]  rate_ctrl_model     rate control model
 * \param[out] encode_frame_result encode result of the coding frame
 */
typedef vpx_rc_status_t (*vpx_rc_update_encodeframe_result_cb_fn_t)(
    vpx_rc_model_t rate_ctrl_model,
    const vpx_rc_encodeframe_result_t *encode_frame_result);

/*!\brief Delete the external rate control model callback prototype
 *
 * This callback is invoked by the encoder to delete the external rate control
 * model.
 *
 * \param[in]  rate_ctrl_model     rate control model
 */
typedef vpx_rc_status_t (*vpx_rc_delete_model_cb_fn_t)(
    vpx_rc_model_t rate_ctrl_model);

/*!\brief Callback function set for external rate control.
 *
 * The user can enable external rate control by registering
 * a set of callback functions with the codec control flag
 * VP9E_SET_EXTERNAL_RATE_CONTROL.
 */
typedef struct vpx_rc_funcs {
  /*!
   * Create an external rate control model.
   */
  vpx_rc_create_model_cb_fn_t create_model;
  /*!
   * Send first pass stats to the external rate control model.
   */
  vpx_rc_send_firstpass_stats_cb_fn_t send_firstpass_stats;
  /*!
   * Get encodeframe decision from the external rate control model.
   */
  vpx_rc_get_encodeframe_decision_cb_fn_t get_encodeframe_decision;
  /*!
   * Update encodeframe result to the external rate control model.
   */
  vpx_rc_update_encodeframe_result_cb_fn_t update_encodeframe_result;
  /*!
   * Delete the external rate control model.
   */
  vpx_rc_delete_model_cb_fn_t delete_model;
  /*!
   * Private data for the external rate control model.
   */
  void *priv;
} vpx_rc_funcs_t;

#ifdef __cplusplus
}  // extern "C"
#endif

#endif  // VPX_VPX_VPX_EXT_RATECTRL_H_
