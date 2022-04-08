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

 WebRtcIlbcfix_GetCbVec.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/get_cd_vec.h"

#include "modules/audio_coding/codecs/ilbc/constants.h"
#include "modules/audio_coding/codecs/ilbc/create_augmented_vec.h"
#include "modules/audio_coding/codecs/ilbc/defines.h"

/*----------------------------------------------------------------*
 *  Construct codebook vector for given index.
 *---------------------------------------------------------------*/

bool WebRtcIlbcfix_GetCbVec(
    int16_t *cbvec,   /* (o) Constructed codebook vector */
    int16_t *mem,   /* (i) Codebook buffer */
    size_t index,   /* (i) Codebook index */
    size_t lMem,   /* (i) Length of codebook buffer */
    size_t cbveclen   /* (i) Codebook vector length */
                            ){
  size_t k, base_size;
  size_t lag;
  /* Stack based */
  int16_t tempbuff2[SUBL+5];

  /* Determine size of codebook sections */

  base_size=lMem-cbveclen+1;

  if (cbveclen==SUBL) {
    base_size += cbveclen / 2;
  }

  /* No filter -> First codebook section */

  if (index<lMem-cbveclen+1) {

    /* first non-interpolated vectors */

    k=index+cbveclen;
    /* get vector */
    WEBRTC_SPL_MEMCPY_W16(cbvec, mem+lMem-k, cbveclen);

  } else if (index < base_size) {

    /* Calculate lag */

    k = (2 * (index - (lMem - cbveclen + 1))) + cbveclen;

    lag = k / 2;

    WebRtcIlbcfix_CreateAugmentedVec(lag, mem+lMem, cbvec);

  }

  /* Higher codebbok section based on filtering */

  else {

    size_t memIndTest;

    /* first non-interpolated vectors */

    if (index-base_size<lMem-cbveclen+1) {

      /* Set up filter memory, stuff zeros outside memory buffer */

      memIndTest = lMem-(index-base_size+cbveclen);

      WebRtcSpl_MemSetW16(mem-CB_HALFFILTERLEN, 0, CB_HALFFILTERLEN);
      WebRtcSpl_MemSetW16(mem+lMem, 0, CB_HALFFILTERLEN);

      /* do filtering to get the codebook vector */

      WebRtcSpl_FilterMAFastQ12(
          &mem[memIndTest+4], cbvec, (int16_t*)WebRtcIlbcfix_kCbFiltersRev,
          CB_FILTERLEN, cbveclen);
    }

    /* interpolated vectors */

    else {
      if (cbveclen < SUBL) {
        // We're going to fill in cbveclen + 5 elements of tempbuff2 in
        // WebRtcSpl_FilterMAFastQ12, less than the SUBL + 5 elements we'll be
        // using in WebRtcIlbcfix_CreateAugmentedVec. This error is caused by
        // bad values in `index` (which come from the encoded stream). Tell the
        // caller that things went south, and that the decoder state is now
        // corrupt (because it's half-way through an update that we can't
        // complete).
        return false;
      }

      /* Stuff zeros outside memory buffer  */
      memIndTest = lMem-cbveclen-CB_FILTERLEN;
      WebRtcSpl_MemSetW16(mem+lMem, 0, CB_HALFFILTERLEN);

      /* do filtering */
      WebRtcSpl_FilterMAFastQ12(
          &mem[memIndTest+7], tempbuff2, (int16_t*)WebRtcIlbcfix_kCbFiltersRev,
          CB_FILTERLEN, cbveclen+5);

      /* Calculate lag index */
      lag = (cbveclen<<1)-20+index-base_size-lMem-1;

      WebRtcIlbcfix_CreateAugmentedVec(lag, tempbuff2+SUBL+5, cbvec);
    }
  }

  return true;  // Success.
}
