/*
 *  Copyright (c) 2012 The WebM project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "./vpx_config.h"
#include "./vpx_dsp_rtcd.h"

#if !CONFIG_VP9_HIGHBITDEPTH
#define FDCT32x32_2D_AVX2 vpx_fdct32x32_rd_avx2
#define FDCT32x32_HIGH_PRECISION 0
#include "vpx_dsp/x86/fwd_dct32x32_impl_avx2.h"
#undef FDCT32x32_2D_AVX2
#undef FDCT32x32_HIGH_PRECISION

#define FDCT32x32_2D_AVX2 vpx_fdct32x32_avx2
#define FDCT32x32_HIGH_PRECISION 1
#include "vpx_dsp/x86/fwd_dct32x32_impl_avx2.h"  // NOLINT
#undef FDCT32x32_2D_AVX2
#undef FDCT32x32_HIGH_PRECISION
#endif  // !CONFIG_VP9_HIGHBITDEPTH
