/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "common_audio/signal_processing/dot_product_with_scale.h"

#include "rtc_base/numerics/safe_conversions.h"

int32_t WebRtcSpl_DotProductWithScale(const int16_t* vector1,
                                      const int16_t* vector2,
                                      size_t length,
                                      int scaling) {
  int64_t sum = 0;
  size_t i = 0;

  /* Unroll the loop to improve performance. */
  for (i = 0; i + 3 < length; i += 4) {
    sum += (vector1[i + 0] * vector2[i + 0]) >> scaling;
    sum += (vector1[i + 1] * vector2[i + 1]) >> scaling;
    sum += (vector1[i + 2] * vector2[i + 2]) >> scaling;
    sum += (vector1[i + 3] * vector2[i + 3]) >> scaling;
  }
  for (; i < length; i++) {
    sum += (vector1[i] * vector2[i]) >> scaling;
  }

  return rtc::saturated_cast<int32_t>(sum);
}
