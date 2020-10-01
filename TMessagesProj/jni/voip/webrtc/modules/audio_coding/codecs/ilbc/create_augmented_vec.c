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

 WebRtcIlbcfix_CreateAugmentedVec.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/defines.h"
#include "modules/audio_coding/codecs/ilbc/constants.h"
#include "rtc_base/sanitizer.h"
#include "common_audio/signal_processing/include/signal_processing_library.h"

/*----------------------------------------------------------------*
 *  Recreate a specific codebook vector from the augmented part.
 *
 *----------------------------------------------------------------*/

void WebRtcIlbcfix_CreateAugmentedVec(
    size_t index,          /* (i) Index for the augmented vector to be
                              created */
    const int16_t* buffer, /* (i) Pointer to the end of the codebook memory
                              that is used for creation of the augmented
                              codebook */
    int16_t* cbVec) {      /* (o) The constructed codebook vector */
  size_t ilow;
  const int16_t *ppo, *ppi;
  int16_t cbVecTmp[4];
  /* Interpolation starts 4 elements before cbVec+index, but must not start
     outside |cbVec|; clamping interp_len to stay within |cbVec|.
   */
  size_t interp_len = WEBRTC_SPL_MIN(index, 4);

  rtc_MsanCheckInitialized(buffer - index - interp_len, sizeof(buffer[0]),
                           index + interp_len);

  ilow = index - interp_len;

  /* copy the first noninterpolated part */
  ppo = buffer-index;
  WEBRTC_SPL_MEMCPY_W16(cbVec, ppo, index);

  /* interpolation */
  ppo = buffer - interp_len;
  ppi = buffer - index - interp_len;

  /* perform cbVec[ilow+k] = ((ppi[k]*alphaTbl[k])>>15) +
                             ((ppo[k]*alphaTbl[interp_len-1-k])>>15);
     for k = 0..interp_len-1
  */
  WebRtcSpl_ElementwiseVectorMult(&cbVec[ilow], ppi, WebRtcIlbcfix_kAlpha,
                                  interp_len, 15);
  WebRtcSpl_ReverseOrderMultArrayElements(
      cbVecTmp, ppo, &WebRtcIlbcfix_kAlpha[interp_len - 1], interp_len, 15);
  WebRtcSpl_AddVectorsAndShift(&cbVec[ilow], &cbVec[ilow], cbVecTmp, interp_len,
                               0);

  /* copy the second noninterpolated part */
  ppo = buffer - index;
  /* |tempbuff2| is declared in WebRtcIlbcfix_GetCbVec and is SUBL+5 elements
     long. |buffer| points one element past the end of that vector, i.e., at
     tempbuff2+SUBL+5. Since ppo=buffer-index, we cannot read any more than
     |index| elements from |ppo|.

     |cbVec| is declared to be SUBL elements long in WebRtcIlbcfix_CbConstruct.
     Therefore, we can only write SUBL-index elements to cbVec+index.

     These two conditions limit the number of elements to copy.
   */
  WEBRTC_SPL_MEMCPY_W16(cbVec+index, ppo, WEBRTC_SPL_MIN(SUBL-index, index));
}
