/*
 *  Copyright (c) 2017 The WebM project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef VPX_VPX_DSP_ARM_MEM_NEON_H_
#define VPX_VPX_DSP_ARM_MEM_NEON_H_

#include <arm_neon.h>
#include <assert.h>
#include <string.h>

#include "./vpx_config.h"
#include "vpx/vpx_integer.h"
#include "vpx_dsp/vpx_dsp_common.h"

static INLINE int16x4_t create_s16x4_neon(const int16_t c0, const int16_t c1,
                                          const int16_t c2, const int16_t c3) {
  return vcreate_s16((uint16_t)c0 | ((uint32_t)c1 << 16) |
                     ((int64_t)(uint16_t)c2 << 32) | ((int64_t)c3 << 48));
}

static INLINE int32x2_t create_s32x2_neon(const int32_t c0, const int32_t c1) {
  return vcreate_s32((uint32_t)c0 | ((int64_t)(uint32_t)c1 << 32));
}

static INLINE int32x4_t create_s32x4_neon(const int32_t c0, const int32_t c1,
                                          const int32_t c2, const int32_t c3) {
  return vcombine_s32(create_s32x2_neon(c0, c1), create_s32x2_neon(c2, c3));
}

// Helper functions used to load tran_low_t into int16, narrowing if necessary.
static INLINE int16x8x2_t load_tran_low_to_s16x2q(const tran_low_t *buf) {
#if CONFIG_VP9_HIGHBITDEPTH
  const int32x4x2_t v0 = vld2q_s32(buf);
  const int32x4x2_t v1 = vld2q_s32(buf + 8);
  const int16x4_t s0 = vmovn_s32(v0.val[0]);
  const int16x4_t s1 = vmovn_s32(v0.val[1]);
  const int16x4_t s2 = vmovn_s32(v1.val[0]);
  const int16x4_t s3 = vmovn_s32(v1.val[1]);
  int16x8x2_t res;
  res.val[0] = vcombine_s16(s0, s2);
  res.val[1] = vcombine_s16(s1, s3);
  return res;
#else
  return vld2q_s16(buf);
#endif
}

static INLINE int16x8_t load_tran_low_to_s16q(const tran_low_t *buf) {
#if CONFIG_VP9_HIGHBITDEPTH
  const int32x4_t v0 = vld1q_s32(buf);
  const int32x4_t v1 = vld1q_s32(buf + 4);
  const int16x4_t s0 = vmovn_s32(v0);
  const int16x4_t s1 = vmovn_s32(v1);
  return vcombine_s16(s0, s1);
#else
  return vld1q_s16(buf);
#endif
}

static INLINE int16x4_t load_tran_low_to_s16d(const tran_low_t *buf) {
#if CONFIG_VP9_HIGHBITDEPTH
  const int32x4_t v0 = vld1q_s32(buf);
  return vmovn_s32(v0);
#else
  return vld1_s16(buf);
#endif
}

static INLINE void store_s16q_to_tran_low(tran_low_t *buf, const int16x8_t a) {
#if CONFIG_VP9_HIGHBITDEPTH
  const int32x4_t v0 = vmovl_s16(vget_low_s16(a));
  const int32x4_t v1 = vmovl_s16(vget_high_s16(a));
  vst1q_s32(buf, v0);
  vst1q_s32(buf + 4, v1);
#else
  vst1q_s16(buf, a);
#endif
}

// Propagate type information to the compiler. Without this the compiler may
// assume the required alignment of uint32_t (4 bytes) and add alignment hints
// to the memory access.
//
// This is used for functions operating on uint8_t which wish to load or store 4
// values at a time but which may not be on 4 byte boundaries.
static INLINE void uint32_to_mem(uint8_t *buf, uint32_t a) {
  memcpy(buf, &a, 4);
}

// Load 2 sets of 4 bytes when alignment is not guaranteed.
static INLINE uint8x8_t load_unaligned_u8(const uint8_t *buf, int stride) {
  uint32_t a;
  uint32x2_t a_u32 = vdup_n_u32(0);
  if (stride == 4) return vld1_u8(buf);
  memcpy(&a, buf, 4);
  buf += stride;
  a_u32 = vset_lane_u32(a, a_u32, 0);
  memcpy(&a, buf, 4);
  a_u32 = vset_lane_u32(a, a_u32, 1);
  return vreinterpret_u8_u32(a_u32);
}

// Store 2 sets of 4 bytes when alignment is not guaranteed.
static INLINE void store_unaligned_u8(uint8_t *buf, int stride,
                                      const uint8x8_t a) {
  const uint32x2_t a_u32 = vreinterpret_u32_u8(a);
  if (stride == 4) {
    vst1_u8(buf, a);
    return;
  }
  uint32_to_mem(buf, vget_lane_u32(a_u32, 0));
  buf += stride;
  uint32_to_mem(buf, vget_lane_u32(a_u32, 1));
}

// Load 4 sets of 4 bytes when alignment is not guaranteed.
static INLINE uint8x16_t load_unaligned_u8q(const uint8_t *buf, int stride) {
  uint32_t a;
  uint32x4_t a_u32 = vdupq_n_u32(0);
  if (stride == 4) return vld1q_u8(buf);
  memcpy(&a, buf, 4);
  buf += stride;
  a_u32 = vsetq_lane_u32(a, a_u32, 0);
  memcpy(&a, buf, 4);
  buf += stride;
  a_u32 = vsetq_lane_u32(a, a_u32, 1);
  memcpy(&a, buf, 4);
  buf += stride;
  a_u32 = vsetq_lane_u32(a, a_u32, 2);
  memcpy(&a, buf, 4);
  buf += stride;
  a_u32 = vsetq_lane_u32(a, a_u32, 3);
  return vreinterpretq_u8_u32(a_u32);
}

// Store 4 sets of 4 bytes when alignment is not guaranteed.
static INLINE void store_unaligned_u8q(uint8_t *buf, int stride,
                                       const uint8x16_t a) {
  const uint32x4_t a_u32 = vreinterpretq_u32_u8(a);
  if (stride == 4) {
    vst1q_u8(buf, a);
    return;
  }
  uint32_to_mem(buf, vgetq_lane_u32(a_u32, 0));
  buf += stride;
  uint32_to_mem(buf, vgetq_lane_u32(a_u32, 1));
  buf += stride;
  uint32_to_mem(buf, vgetq_lane_u32(a_u32, 2));
  buf += stride;
  uint32_to_mem(buf, vgetq_lane_u32(a_u32, 3));
}

// Load 2 sets of 4 bytes when alignment is guaranteed.
static INLINE uint8x8_t load_u8(const uint8_t *buf, int stride) {
  uint32x2_t a = vdup_n_u32(0);

  assert(!((intptr_t)buf % sizeof(uint32_t)));
  assert(!(stride % sizeof(uint32_t)));

  a = vld1_lane_u32((const uint32_t *)buf, a, 0);
  buf += stride;
  a = vld1_lane_u32((const uint32_t *)buf, a, 1);
  return vreinterpret_u8_u32(a);
}

// Store 2 sets of 4 bytes when alignment is guaranteed.
static INLINE void store_u8(uint8_t *buf, int stride, const uint8x8_t a) {
  uint32x2_t a_u32 = vreinterpret_u32_u8(a);

  assert(!((intptr_t)buf % sizeof(uint32_t)));
  assert(!(stride % sizeof(uint32_t)));

  vst1_lane_u32((uint32_t *)buf, a_u32, 0);
  buf += stride;
  vst1_lane_u32((uint32_t *)buf, a_u32, 1);
}
#endif  // VPX_VPX_DSP_ARM_MEM_NEON_H_
