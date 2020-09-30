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

 WebRtcIlbcfix_Enhancer.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/defines.h"
#include "modules/audio_coding/codecs/ilbc/constants.h"
#include "modules/audio_coding/codecs/ilbc/get_sync_seq.h"
#include "modules/audio_coding/codecs/ilbc/smooth.h"

/*----------------------------------------------------------------*
 * perform enhancement on idata+centerStartPos through
 * idata+centerStartPos+ENH_BLOCKL-1
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_Enhancer(
    int16_t *odata,   /* (o) smoothed block, dimension blockl */
    int16_t *idata,   /* (i) data buffer used for enhancing */
    size_t idatal,   /* (i) dimension idata */
    size_t centerStartPos, /* (i) first sample current block within idata */
    size_t *period,   /* (i) pitch period array (pitch bward-in time) */
    const size_t *plocs,   /* (i) locations where period array values valid */
    size_t periodl   /* (i) dimension of period and plocs */
                            ){
  /* Stack based */
  int16_t surround[ENH_BLOCKL];

  WebRtcSpl_MemSetW16(surround, 0, ENH_BLOCKL);

  /* get said second sequence of segments */

  WebRtcIlbcfix_GetSyncSeq(idata, idatal, centerStartPos, period, plocs,
                           periodl, ENH_HL, surround);

  /* compute the smoothed output from said second sequence */

  WebRtcIlbcfix_Smooth(odata, idata + centerStartPos, surround);
}
