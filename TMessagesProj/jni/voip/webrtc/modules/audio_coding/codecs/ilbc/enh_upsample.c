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

 WebRtcIlbcfix_EnhUpsample.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/enh_upsample.h"

#include "modules/audio_coding/codecs/ilbc/constants.h"
#include "modules/audio_coding/codecs/ilbc/defines.h"

/*----------------------------------------------------------------*
 * upsample finite array assuming zeros outside bounds
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_EnhUpsample(
    int32_t *useq1, /* (o) upsampled output sequence */
    int16_t *seq1 /* (i) unupsampled sequence */
                                ){
  int j;
  int32_t *pu1, *pu11;
  int16_t *ps, *w16tmp;
  const int16_t *pp;

  /* filtering: filter overhangs left side of sequence */
  pu1=useq1;
  for (j=0;j<ENH_UPS0; j++) {
    pu11=pu1;
    /* i = 2 */
    pp=WebRtcIlbcfix_kEnhPolyPhaser[j]+1;
    ps=seq1+2;
    *pu11 = (*ps--) * *pp++;
    *pu11 += (*ps--) * *pp++;
    *pu11 += (*ps--) * *pp++;
    pu11+=ENH_UPS0;
    /* i = 3 */
    pp=WebRtcIlbcfix_kEnhPolyPhaser[j]+1;
    ps=seq1+3;
    *pu11 = (*ps--) * *pp++;
    *pu11 += (*ps--) * *pp++;
    *pu11 += (*ps--) * *pp++;
    *pu11 += (*ps--) * *pp++;
    pu11+=ENH_UPS0;
    /* i = 4 */
    pp=WebRtcIlbcfix_kEnhPolyPhaser[j]+1;
    ps=seq1+4;
    *pu11 = (*ps--) * *pp++;
    *pu11 += (*ps--) * *pp++;
    *pu11 += (*ps--) * *pp++;
    *pu11 += (*ps--) * *pp++;
    *pu11 += (*ps--) * *pp++;
    pu1++;
  }

  /* filtering: simple convolution=inner products
     (not needed since the sequence is so short)
  */

  /* filtering: filter overhangs right side of sequence */

  /* Code with loops, which is equivivalent to the expanded version below

     filterlength = 5;
     hf1 = 2;
     for(j=0;j<ENH_UPS0; j++){
     pu = useq1 + (filterlength-hfl)*ENH_UPS0 + j;
     for(i=1; i<=hfl; i++){
     *pu=0;
     pp = polyp[j]+i;
     ps = seq1+dim1-1;
     for(k=0;k<filterlength-i;k++) {
     *pu += (*ps--) * *pp++;
     }
     pu+=ENH_UPS0;
     }
     }
  */
  pu1 = useq1 + 12;
  w16tmp = seq1+4;
  for (j=0;j<ENH_UPS0; j++) {
    pu11 = pu1;
    /* i = 1 */
    pp = WebRtcIlbcfix_kEnhPolyPhaser[j]+2;
    ps = w16tmp;
    *pu11 = (*ps--) * *pp++;
    *pu11 += (*ps--) * *pp++;
    *pu11 += (*ps--) * *pp++;
    *pu11 += (*ps--) * *pp++;
    pu11+=ENH_UPS0;
    /* i = 2 */
    pp = WebRtcIlbcfix_kEnhPolyPhaser[j]+3;
    ps = w16tmp;
    *pu11 = (*ps--) * *pp++;
    *pu11 += (*ps--) * *pp++;
    *pu11 += (*ps--) * *pp++;
    pu11+=ENH_UPS0;

    pu1++;
  }
}
