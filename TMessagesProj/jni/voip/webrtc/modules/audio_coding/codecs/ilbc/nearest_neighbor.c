/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/******************************************************************

 iLBC Speech Coder ANSI-C Source Code

 WebRtcIlbcfix_NearestNeighbor.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/defines.h"

void WebRtcIlbcfix_NearestNeighbor(size_t* index,
                                   const size_t* array,
                                   size_t value,
                                   size_t arlength) {
  size_t i;
  size_t min_diff = (size_t)-1;
  for (i = 0; i < arlength; i++) {
    const size_t diff =
        (array[i] < value) ? (value - array[i]) : (array[i] - value);
    if (diff < min_diff) {
      *index = i;
      min_diff = diff;
    }
  }
}
