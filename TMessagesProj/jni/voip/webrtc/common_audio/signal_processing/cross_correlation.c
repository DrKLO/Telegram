/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "common_audio/signal_processing/include/signal_processing_library.h"

/* C version of WebRtcSpl_CrossCorrelation() for generic platforms. */
void WebRtcSpl_CrossCorrelationC(int32_t* cross_correlation,
                                 const int16_t* seq1,
                                 const int16_t* seq2,
                                 size_t dim_seq,
                                 size_t dim_cross_correlation,
                                 int right_shifts,
                                 int step_seq2) {
  size_t i = 0, j = 0;

  for (i = 0; i < dim_cross_correlation; i++) {
    int32_t corr = 0;
    for (j = 0; j < dim_seq; j++)
      corr += (seq1[j] * seq2[j]) >> right_shifts;
    seq2 += step_seq2;
    *cross_correlation++ = corr;
  }
}
