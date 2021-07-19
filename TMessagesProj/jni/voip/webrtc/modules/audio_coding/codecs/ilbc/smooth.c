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

 WebRtcIlbcfix_Smooth.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/smooth.h"

#include "modules/audio_coding/codecs/ilbc/constants.h"
#include "modules/audio_coding/codecs/ilbc/defines.h"
#include "modules/audio_coding/codecs/ilbc/smooth_out_data.h"

/*----------------------------------------------------------------*
 * find the smoothed output data
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_Smooth(
    int16_t *odata,   /* (o) smoothed output */
    int16_t *current,  /* (i) the un enhanced residual for
                                this block */
    int16_t *surround  /* (i) The approximation from the
                                surrounding sequences */
                          ) {
  int16_t scale, scale1, scale2;
  int16_t A, B, C, denomW16;
  int32_t B_W32, denom, num;
  int32_t errs;
  int32_t w00,w10,w11, endiff, crit;
  int32_t w00prim, w10prim, w11_div_w00;
  int16_t w11prim;
  int16_t bitsw00, bitsw10, bitsw11;
  int32_t w11w00, w10w10, w00w00;
  uint32_t max1, max2, max12;

  /* compute some inner products (ensure no overflow by first calculating proper scale factor) */

  w00 = w10 = w11 = 0;

  // Calculate a right shift that will let us sum ENH_BLOCKL pairwise products
  // of values from the two sequences without overflowing an int32_t. (The +1
  // in max1 and max2 are because WebRtcSpl_MaxAbsValueW16 will return 2**15 -
  // 1 if the input array contains -2**15.)
  max1 = WebRtcSpl_MaxAbsValueW16(current, ENH_BLOCKL) + 1;
  max2 = WebRtcSpl_MaxAbsValueW16(surround, ENH_BLOCKL) + 1;
  max12 = WEBRTC_SPL_MAX(max1, max2);
  scale = (64 - 31) -
          WebRtcSpl_CountLeadingZeros64((max12 * max12) * (uint64_t)ENH_BLOCKL);
  scale=WEBRTC_SPL_MAX(0, scale);

  w00=WebRtcSpl_DotProductWithScale(current,current,ENH_BLOCKL,scale);
  w11=WebRtcSpl_DotProductWithScale(surround,surround,ENH_BLOCKL,scale);
  w10=WebRtcSpl_DotProductWithScale(surround,current,ENH_BLOCKL,scale);

  if (w00<0) w00 = WEBRTC_SPL_WORD32_MAX;
  if (w11<0) w11 = WEBRTC_SPL_WORD32_MAX;

  /* Rescale w00 and w11 to w00prim and w11prim, so that w00prim/w11prim
     is in Q16 */

  bitsw00 = WebRtcSpl_GetSizeInBits(w00);
  bitsw11 = WebRtcSpl_GetSizeInBits(w11);
  bitsw10 = WebRtcSpl_GetSizeInBits(WEBRTC_SPL_ABS_W32(w10));
  scale1 = 31 - bitsw00;
  scale2 = 15 - bitsw11;

  if (scale2>(scale1-16)) {
    scale2 = scale1 - 16;
  } else {
    scale1 = scale2 + 16;
  }

  w00prim = w00 << scale1;
  w11prim = (int16_t) WEBRTC_SPL_SHIFT_W32(w11, scale2);

  /* Perform C = sqrt(w11/w00) (C is in Q11 since (16+6)/2=11) */
  if (w11prim>64) {
    endiff = WebRtcSpl_DivW32W16(w00prim, w11prim) << 6;
    C = (int16_t)WebRtcSpl_SqrtFloor(endiff); /* C is in Q11 */
  } else {
    C = 1;
  }

  /* first try enhancement without power-constraint */

  errs = WebRtcIlbcfix_Smooth_odata(odata, current, surround, C);



  /* if constraint violated by first try, add constraint */

  if ( (6-scale+scale1) > 31) {
    crit=0;
  } else {
    /* crit = 0.05 * w00 (Result in Q-6) */
    crit = WEBRTC_SPL_SHIFT_W32(
        WEBRTC_SPL_MUL(ENH_A0, w00prim >> 14),
        -(6-scale+scale1));
  }

  if (errs > crit) {

    if( w00 < 1) {
      w00=1;
    }

    /* Calculate w11*w00, w10*w10 and w00*w00 in the same Q domain */

    scale1 = bitsw00-15;
    scale2 = bitsw11-15;

    if (scale2>scale1) {
      scale = scale2;
    } else {
      scale = scale1;
    }

    w11w00 = (int16_t)WEBRTC_SPL_SHIFT_W32(w11, -scale) *
        (int16_t)WEBRTC_SPL_SHIFT_W32(w00, -scale);

    w10w10 = (int16_t)WEBRTC_SPL_SHIFT_W32(w10, -scale) *
        (int16_t)WEBRTC_SPL_SHIFT_W32(w10, -scale);

    w00w00 = (int16_t)WEBRTC_SPL_SHIFT_W32(w00, -scale) *
        (int16_t)WEBRTC_SPL_SHIFT_W32(w00, -scale);

    /* Calculate (w11*w00-w10*w10)/(w00*w00) in Q16 */
    if (w00w00>65536) {
      endiff = (w11w00-w10w10);
      endiff = WEBRTC_SPL_MAX(0, endiff);
      /* denom is in Q16 */
      denom = WebRtcSpl_DivW32W16(endiff, (int16_t)(w00w00 >> 16));
    } else {
      denom = 65536;
    }

    if( denom > 7){ /* eliminates numerical problems
                       for if smooth */

      scale=WebRtcSpl_GetSizeInBits(denom)-15;

      if (scale>0) {
        /* denomW16 is in Q(16+scale) */
        denomW16 = (int16_t)(denom >> scale);

        /* num in Q(34-scale) */
        num = ENH_A0_MINUS_A0A0DIV4 >> scale;
      } else {
        /* denomW16 is in Q16 */
        denomW16=(int16_t)denom;

        /* num in Q34 */
        num=ENH_A0_MINUS_A0A0DIV4;
      }

      /* A sqrt( (ENH_A0-(ENH_A0^2)/4)*(w00*w00)/(w11*w00 + w10*w10) ) in Q9 */
      A = (int16_t)WebRtcSpl_SqrtFloor(WebRtcSpl_DivW32W16(num, denomW16));

      /* B_W32 is in Q30 ( B = 1 - ENH_A0/2 - A * w10/w00 ) */
      scale1 = 31-bitsw10;
      scale2 = 21-scale1;
      w10prim = w10 == 0 ? 0 : w10 * (1 << scale1);
      w00prim = WEBRTC_SPL_SHIFT_W32(w00, -scale2);
      scale = bitsw00-scale2-15;

      if (scale>0) {
        w10prim >>= scale;
        w00prim >>= scale;
      }

      if ((w00prim>0)&&(w10prim>0)) {
        w11_div_w00=WebRtcSpl_DivW32W16(w10prim, (int16_t)w00prim);

        if (WebRtcSpl_GetSizeInBits(w11_div_w00)+WebRtcSpl_GetSizeInBits(A)>31) {
          B_W32 = 0;
        } else {
          B_W32 = (int32_t)1073741824 - (int32_t)ENH_A0DIV2 -
              WEBRTC_SPL_MUL(A, w11_div_w00);
        }
        B = (int16_t)(B_W32 >> 16);  /* B in Q14. */
      } else {
        /* No smoothing */
        A = 0;
        B = 16384; /* 1 in Q14 */
      }
    }
    else{ /* essentially no difference between cycles;
             smoothing not needed */

      A = 0;
      B = 16384; /* 1 in Q14 */
    }

    /* create smoothed sequence */

    WebRtcSpl_ScaleAndAddVectors(surround, A, 9,
                                current, B, 14,
                                odata, ENH_BLOCKL);
  }
  return;
}
