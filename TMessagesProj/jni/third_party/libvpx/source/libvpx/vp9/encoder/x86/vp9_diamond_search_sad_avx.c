/*
 *  Copyright (c) 2015 The WebM project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#if defined(_MSC_VER)
#include <intrin.h>
#endif
#include <emmintrin.h>
#include <smmintrin.h>

#include "vpx_dsp/vpx_dsp_common.h"
#include "vp9/encoder/vp9_encoder.h"
#include "vpx_ports/mem.h"

#ifdef __GNUC__
#define LIKELY(v) __builtin_expect(v, 1)
#define UNLIKELY(v) __builtin_expect(v, 0)
#else
#define LIKELY(v) (v)
#define UNLIKELY(v) (v)
#endif

static INLINE int_mv pack_int_mv(int16_t row, int16_t col) {
  int_mv result;
  result.as_mv.row = row;
  result.as_mv.col = col;
  return result;
}

static INLINE MV_JOINT_TYPE get_mv_joint(const int_mv mv) {
  // This is simplified from the C implementation to utilise that
  //  x->nmvjointsadcost[1] == x->nmvjointsadcost[2]  and
  //  x->nmvjointsadcost[1] == x->nmvjointsadcost[3]
  return mv.as_int == 0 ? 0 : 1;
}

static INLINE int mv_cost(const int_mv mv, const int *joint_cost,
                          int *const comp_cost[2]) {
  return joint_cost[get_mv_joint(mv)] + comp_cost[0][mv.as_mv.row] +
         comp_cost[1][mv.as_mv.col];
}

static int mvsad_err_cost(const MACROBLOCK *x, const int_mv mv, const MV *ref,
                          int sad_per_bit) {
  const int_mv diff =
      pack_int_mv(mv.as_mv.row - ref->row, mv.as_mv.col - ref->col);
  return ROUND_POWER_OF_TWO(
      (unsigned)mv_cost(diff, x->nmvjointsadcost, x->nmvsadcost) * sad_per_bit,
      VP9_PROB_COST_SHIFT);
}

/*****************************************************************************
 * This function utilizes 3 properties of the cost function lookup tables,   *
 * constructed in using 'cal_nmvjointsadcost' and 'cal_nmvsadcosts' in       *
 * vp9_encoder.c.                                                            *
 * For the joint cost:                                                       *
 *   - mvjointsadcost[1] == mvjointsadcost[2] == mvjointsadcost[3]           *
 * For the component costs:                                                  *
 *   - For all i: mvsadcost[0][i] == mvsadcost[1][i]                         *
 *         (Equal costs for both components)                                 *
 *   - For all i: mvsadcost[0][i] == mvsadcost[0][-i]                        *
 *         (Cost function is even)                                           *
 * If these do not hold, then this function cannot be used without           *
 * modification, in which case you can revert to using the C implementation, *
 * which does not rely on these properties.                                  *
 *****************************************************************************/
int vp9_diamond_search_sad_avx(const MACROBLOCK *x,
                               const search_site_config *cfg, MV *ref_mv,
                               MV *best_mv, int search_param, int sad_per_bit,
                               int *num00, const vp9_variance_fn_ptr_t *fn_ptr,
                               const MV *center_mv) {
  const int_mv maxmv = pack_int_mv(x->mv_limits.row_max, x->mv_limits.col_max);
  const __m128i v_max_mv_w = _mm_set1_epi32(maxmv.as_int);
  const int_mv minmv = pack_int_mv(x->mv_limits.row_min, x->mv_limits.col_min);
  const __m128i v_min_mv_w = _mm_set1_epi32(minmv.as_int);

  const __m128i v_spb_d = _mm_set1_epi32(sad_per_bit);

  const __m128i v_joint_cost_0_d = _mm_set1_epi32(x->nmvjointsadcost[0]);
  const __m128i v_joint_cost_1_d = _mm_set1_epi32(x->nmvjointsadcost[1]);

  // search_param determines the length of the initial step and hence the number
  // of iterations.
  // 0 = initial step (MAX_FIRST_STEP) pel
  // 1 = (MAX_FIRST_STEP/2) pel,
  // 2 = (MAX_FIRST_STEP/4) pel...
  const MV *ss_mv = &cfg->ss_mv[cfg->searches_per_step * search_param];
  const intptr_t *ss_os = &cfg->ss_os[cfg->searches_per_step * search_param];
  const int tot_steps = cfg->total_steps - search_param;

  const int_mv fcenter_mv =
      pack_int_mv(center_mv->row >> 3, center_mv->col >> 3);
  const __m128i vfcmv = _mm_set1_epi32(fcenter_mv.as_int);

  const int ref_row = clamp(ref_mv->row, minmv.as_mv.row, maxmv.as_mv.row);
  const int ref_col = clamp(ref_mv->col, minmv.as_mv.col, maxmv.as_mv.col);

  int_mv bmv = pack_int_mv(ref_row, ref_col);
  int_mv new_bmv = bmv;
  __m128i v_bmv_w = _mm_set1_epi32(bmv.as_int);

  const int what_stride = x->plane[0].src.stride;
  const int in_what_stride = x->e_mbd.plane[0].pre[0].stride;
  const uint8_t *const what = x->plane[0].src.buf;
  const uint8_t *const in_what =
      x->e_mbd.plane[0].pre[0].buf + ref_row * in_what_stride + ref_col;

  // Work out the start point for the search
  const uint8_t *best_address = in_what;
  const uint8_t *new_best_address = best_address;
#if VPX_ARCH_X86_64
  __m128i v_ba_q = _mm_set1_epi64x((intptr_t)best_address);
#else
  __m128i v_ba_d = _mm_set1_epi32((intptr_t)best_address);
#endif

  unsigned int best_sad;
  int i, j, step;

  // Check the prerequisite cost function properties that are easy to check
  // in an assert. See the function-level documentation for details on all
  // prerequisites.
  assert(x->nmvjointsadcost[1] == x->nmvjointsadcost[2]);
  assert(x->nmvjointsadcost[1] == x->nmvjointsadcost[3]);

  // Check the starting position
  best_sad = fn_ptr->sdf(what, what_stride, in_what, in_what_stride);
  best_sad += mvsad_err_cost(x, bmv, &fcenter_mv.as_mv, sad_per_bit);

  *num00 = 0;

  for (i = 0, step = 0; step < tot_steps; step++) {
    for (j = 0; j < cfg->searches_per_step; j += 4, i += 4) {
      __m128i v_sad_d, v_cost_d, v_outside_d, v_inside_d, v_diff_mv_w;
#if VPX_ARCH_X86_64
      __m128i v_blocka[2];
#else
      __m128i v_blocka[1];
#endif

      // Compute the candidate motion vectors
      const __m128i v_ss_mv_w = _mm_loadu_si128((const __m128i *)&ss_mv[i]);
      const __m128i v_these_mv_w = _mm_add_epi16(v_bmv_w, v_ss_mv_w);
      // Clamp them to the search bounds
      __m128i v_these_mv_clamp_w = v_these_mv_w;
      v_these_mv_clamp_w = _mm_min_epi16(v_these_mv_clamp_w, v_max_mv_w);
      v_these_mv_clamp_w = _mm_max_epi16(v_these_mv_clamp_w, v_min_mv_w);
      // The ones that did not change are inside the search area
      v_inside_d = _mm_cmpeq_epi32(v_these_mv_clamp_w, v_these_mv_w);

      // If none of them are inside, then move on
      if (LIKELY(_mm_test_all_zeros(v_inside_d, v_inside_d))) {
        continue;
      }

      // The inverse mask indicates which of the MVs are outside
      v_outside_d = _mm_xor_si128(v_inside_d, _mm_set1_epi8((int8_t)0xff));
      // Shift right to keep the sign bit clear, we will use this later
      // to set the cost to the maximum value.
      v_outside_d = _mm_srli_epi32(v_outside_d, 1);

      // Compute the difference MV
      v_diff_mv_w = _mm_sub_epi16(v_these_mv_clamp_w, vfcmv);
      // We utilise the fact that the cost function is even, and use the
      // absolute difference. This allows us to use unsigned indexes later
      // and reduces cache pressure somewhat as only a half of the table
      // is ever referenced.
      v_diff_mv_w = _mm_abs_epi16(v_diff_mv_w);

      // Compute the SIMD pointer offsets.
      {
#if VPX_ARCH_X86_64  //  sizeof(intptr_t) == 8
        // Load the offsets
        __m128i v_bo10_q = _mm_loadu_si128((const __m128i *)&ss_os[i + 0]);
        __m128i v_bo32_q = _mm_loadu_si128((const __m128i *)&ss_os[i + 2]);
        // Set the ones falling outside to zero
        v_bo10_q = _mm_and_si128(v_bo10_q, _mm_cvtepi32_epi64(v_inside_d));
        v_bo32_q =
            _mm_and_si128(v_bo32_q, _mm_unpackhi_epi32(v_inside_d, v_inside_d));
        // Compute the candidate addresses
        v_blocka[0] = _mm_add_epi64(v_ba_q, v_bo10_q);
        v_blocka[1] = _mm_add_epi64(v_ba_q, v_bo32_q);
#else  // VPX_ARCH_X86 //  sizeof(intptr_t) == 4
        __m128i v_bo_d = _mm_loadu_si128((const __m128i *)&ss_os[i]);
        v_bo_d = _mm_and_si128(v_bo_d, v_inside_d);
        v_blocka[0] = _mm_add_epi32(v_ba_d, v_bo_d);
#endif
      }

      fn_ptr->sdx4df(what, what_stride, (const uint8_t **)&v_blocka[0],
                     in_what_stride, (uint32_t *)&v_sad_d);

      // Look up the component cost of the residual motion vector
      {
        const int32_t row0 = _mm_extract_epi16(v_diff_mv_w, 0);
        const int32_t col0 = _mm_extract_epi16(v_diff_mv_w, 1);
        const int32_t row1 = _mm_extract_epi16(v_diff_mv_w, 2);
        const int32_t col1 = _mm_extract_epi16(v_diff_mv_w, 3);
        const int32_t row2 = _mm_extract_epi16(v_diff_mv_w, 4);
        const int32_t col2 = _mm_extract_epi16(v_diff_mv_w, 5);
        const int32_t row3 = _mm_extract_epi16(v_diff_mv_w, 6);
        const int32_t col3 = _mm_extract_epi16(v_diff_mv_w, 7);

        // Note: This is a use case for vpgather in AVX2
        const uint32_t cost0 = x->nmvsadcost[0][row0] + x->nmvsadcost[0][col0];
        const uint32_t cost1 = x->nmvsadcost[0][row1] + x->nmvsadcost[0][col1];
        const uint32_t cost2 = x->nmvsadcost[0][row2] + x->nmvsadcost[0][col2];
        const uint32_t cost3 = x->nmvsadcost[0][row3] + x->nmvsadcost[0][col3];

        __m128i v_cost_10_d, v_cost_32_d;
        v_cost_10_d = _mm_cvtsi32_si128(cost0);
        v_cost_10_d = _mm_insert_epi32(v_cost_10_d, cost1, 1);
        v_cost_32_d = _mm_cvtsi32_si128(cost2);
        v_cost_32_d = _mm_insert_epi32(v_cost_32_d, cost3, 1);
        v_cost_d = _mm_unpacklo_epi64(v_cost_10_d, v_cost_32_d);
      }

      // Now add in the joint cost
      {
        const __m128i v_sel_d =
            _mm_cmpeq_epi32(v_diff_mv_w, _mm_setzero_si128());
        const __m128i v_joint_cost_d =
            _mm_blendv_epi8(v_joint_cost_1_d, v_joint_cost_0_d, v_sel_d);
        v_cost_d = _mm_add_epi32(v_cost_d, v_joint_cost_d);
      }

      // Multiply by sad_per_bit
      v_cost_d = _mm_mullo_epi32(v_cost_d, v_spb_d);
      // ROUND_POWER_OF_TWO(v_cost_d, VP9_PROB_COST_SHIFT)
      v_cost_d = _mm_add_epi32(v_cost_d,
                               _mm_set1_epi32(1 << (VP9_PROB_COST_SHIFT - 1)));
      v_cost_d = _mm_srai_epi32(v_cost_d, VP9_PROB_COST_SHIFT);
      // Add the cost to the sad
      v_sad_d = _mm_add_epi32(v_sad_d, v_cost_d);

      // Make the motion vectors outside the search area have max cost
      // by or'ing in the comparison mask, this way the minimum search won't
      // pick them.
      v_sad_d = _mm_or_si128(v_sad_d, v_outside_d);

      // Find the minimum value and index horizontally in v_sad_d
      {
        // Try speculatively on 16 bits, so we can use the minpos intrinsic
        const __m128i v_sad_w = _mm_packus_epi32(v_sad_d, v_sad_d);
        const __m128i v_minp_w = _mm_minpos_epu16(v_sad_w);

        uint32_t local_best_sad = _mm_extract_epi16(v_minp_w, 0);
        uint32_t local_best_idx = _mm_extract_epi16(v_minp_w, 1);

        // If the local best value is not saturated, just use it, otherwise
        // find the horizontal minimum again the hard way on 32 bits.
        // This is executed rarely.
        if (UNLIKELY(local_best_sad == 0xffff)) {
          __m128i v_loval_d, v_hival_d, v_loidx_d, v_hiidx_d, v_sel_d;

          v_loval_d = v_sad_d;
          v_loidx_d = _mm_set_epi32(3, 2, 1, 0);
          v_hival_d = _mm_srli_si128(v_loval_d, 8);
          v_hiidx_d = _mm_srli_si128(v_loidx_d, 8);

          v_sel_d = _mm_cmplt_epi32(v_hival_d, v_loval_d);

          v_loval_d = _mm_blendv_epi8(v_loval_d, v_hival_d, v_sel_d);
          v_loidx_d = _mm_blendv_epi8(v_loidx_d, v_hiidx_d, v_sel_d);
          v_hival_d = _mm_srli_si128(v_loval_d, 4);
          v_hiidx_d = _mm_srli_si128(v_loidx_d, 4);

          v_sel_d = _mm_cmplt_epi32(v_hival_d, v_loval_d);

          v_loval_d = _mm_blendv_epi8(v_loval_d, v_hival_d, v_sel_d);
          v_loidx_d = _mm_blendv_epi8(v_loidx_d, v_hiidx_d, v_sel_d);

          local_best_sad = _mm_extract_epi32(v_loval_d, 0);
          local_best_idx = _mm_extract_epi32(v_loidx_d, 0);
        }

        // Update the global minimum if the local minimum is smaller
        if (LIKELY(local_best_sad < best_sad)) {
          new_bmv = ((const int_mv *)&v_these_mv_w)[local_best_idx];
          new_best_address = ((const uint8_t **)v_blocka)[local_best_idx];

          best_sad = local_best_sad;
        }
      }
    }

    bmv = new_bmv;
    best_address = new_best_address;

    v_bmv_w = _mm_set1_epi32(bmv.as_int);
#if VPX_ARCH_X86_64
    v_ba_q = _mm_set1_epi64x((intptr_t)best_address);
#else
    v_ba_d = _mm_set1_epi32((intptr_t)best_address);
#endif

    if (UNLIKELY(best_address == in_what)) {
      (*num00)++;
    }
  }

  *best_mv = bmv.as_mv;
  return best_sad;
}
