/*
 *  Copyright (c) 2010 The WebM project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef VPX_VP9_ENCODER_VP9_TEMPORAL_FILTER_H_
#define VPX_VP9_ENCODER_VP9_TEMPORAL_FILTER_H_

#ifdef __cplusplus
extern "C" {
#endif

#define ARNR_FILT_QINDEX 128
static const MV kZeroMv = { 0, 0 };

// Block size used in temporal filtering
#define TF_BLOCK BLOCK_32X32
#define BH 32
#define BH_LOG2 5
#define BW 32
#define BW_LOG2 5
#define BLK_PELS ((BH) * (BW))  // Pixels in the block
#define TF_SHIFT 2
#define TF_ROUND 3
#define THR_SHIFT 2
#define TF_SUB_BLOCK BLOCK_16X16
#define SUB_BH 16
#define SUB_BW 16

void vp9_temporal_filter_init(void);
void vp9_temporal_filter(VP9_COMP *cpi, int distance);

void vp9_temporal_filter_iterate_row_c(VP9_COMP *cpi, ThreadData *td,
                                       int mb_row, int mb_col_start,
                                       int mb_col_end);

#ifdef __cplusplus
}  // extern "C"
#endif

#endif  // VPX_VP9_ENCODER_VP9_TEMPORAL_FILTER_H_
