/*
 *  Copyright 2022 The LibYuv Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS. All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "libyuv/scale.h" /* For FilterMode */

#include <assert.h>
#include <string.h>

#include "libyuv/convert_argb.h"
#include "libyuv/convert_from_argb.h"
#include "libyuv/row.h"
#include "libyuv/scale_argb.h"
#include "libyuv/scale_rgb.h"

#ifdef __cplusplus
namespace libyuv {
extern "C" {
#endif

// Scale a 24 bit image.
// Converts to ARGB as intermediate step

LIBYUV_API
int RGBScale(const uint8_t* src_rgb,
             int src_stride_rgb,
             int src_width,
             int src_height,
             uint8_t* dst_rgb,
             int dst_stride_rgb,
             int dst_width,
             int dst_height,
             enum FilterMode filtering) {
  int r;
  uint8_t* src_argb =
      (uint8_t*)malloc(src_width * src_height * 4 + dst_width * dst_height * 4);
  uint8_t* dst_argb = src_argb + src_width * src_height * 4;

  if (!src_argb) {
    return 1;
  }

  r = RGB24ToARGB(src_rgb, src_stride_rgb, src_argb, src_width * 4, src_width,
                  src_height);
  if (!r) {
    r = ARGBScale(src_argb, src_width * 4, src_width, src_height, dst_argb,
                  dst_width * 4, dst_width, dst_height, filtering);
    if (!r) {
      r = ARGBToRGB24(dst_argb, dst_width * 4, dst_rgb, dst_stride_rgb,
                      dst_width, dst_height);
    }
  }
  free(src_argb);
  return r;
}

#ifdef __cplusplus
}  // extern "C"
}  // namespace libyuv
#endif
