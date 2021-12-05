/*
 *  Copyright (c) 2017 The WebM project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef VPX_VPX_DSP_ARM_SUM_NEON_H_
#define VPX_VPX_DSP_ARM_SUM_NEON_H_

#include <arm_neon.h>

#include "./vpx_config.h"
#include "vpx/vpx_integer.h"

static INLINE int32x2_t horizontal_add_int16x8(const int16x8_t a) {
  const int32x4_t b = vpaddlq_s16(a);
  const int64x2_t c = vpaddlq_s32(b);
  return vadd_s32(vreinterpret_s32_s64(vget_low_s64(c)),
                  vreinterpret_s32_s64(vget_high_s64(c)));
}

static INLINE uint32x2_t horizontal_add_uint16x8(const uint16x8_t a) {
  const uint32x4_t b = vpaddlq_u16(a);
  const uint64x2_t c = vpaddlq_u32(b);
  return vadd_u32(vreinterpret_u32_u64(vget_low_u64(c)),
                  vreinterpret_u32_u64(vget_high_u64(c)));
}

static INLINE uint32x2_t horizontal_add_uint32x4(const uint32x4_t a) {
  const uint64x2_t b = vpaddlq_u32(a);
  return vadd_u32(vreinterpret_u32_u64(vget_low_u64(b)),
                  vreinterpret_u32_u64(vget_high_u64(b)));
}
#endif  // VPX_VPX_DSP_ARM_SUM_NEON_H_
