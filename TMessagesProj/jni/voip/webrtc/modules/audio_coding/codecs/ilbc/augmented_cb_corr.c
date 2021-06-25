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

 WebRtcIlbcfix_AugmentedCbCorr.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/augmented_cb_corr.h"

#include "modules/audio_coding/codecs/ilbc/constants.h"
#include "modules/audio_coding/codecs/ilbc/defines.h"

void WebRtcIlbcfix_AugmentedCbCorr(
    int16_t *target,   /* (i) Target vector */
    int16_t *buffer,   /* (i) Memory buffer */
    int16_t *interpSamples, /* (i) buffer with
                                     interpolated samples */
    int32_t *crossDot,  /* (o) The cross correlation between
                                 the target and the Augmented
                                 vector */
    size_t low,    /* (i) Lag to start from (typically
                             20) */
    size_t high,   /* (i) Lag to end at (typically 39) */
    int scale)   /* (i) Scale factor to use for
                              the crossDot */
{
  size_t lagcount;
  size_t ilow;
  int16_t *targetPtr;
  int32_t *crossDotPtr;
  int16_t *iSPtr=interpSamples;

  /* Calculate the correlation between the target and the
     interpolated codebook. The correlation is calculated in
     3 sections with the interpolated part in the middle */
  crossDotPtr=crossDot;
  for (lagcount=low; lagcount<=high; lagcount++) {

    ilow = lagcount - 4;

    /* Compute dot product for the first (lagcount-4) samples */
    (*crossDotPtr) = WebRtcSpl_DotProductWithScale(target, buffer-lagcount, ilow, scale);

    /* Compute dot product on the interpolated samples */
    (*crossDotPtr) += WebRtcSpl_DotProductWithScale(target+ilow, iSPtr, 4, scale);
    targetPtr = target + lagcount;
    iSPtr += lagcount-ilow;

    /* Compute dot product for the remaining samples */
    (*crossDotPtr) += WebRtcSpl_DotProductWithScale(targetPtr, buffer-lagcount, SUBL-lagcount, scale);
    crossDotPtr++;
  }
}
