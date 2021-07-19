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

 WebRtcIlbcfix_PackBits.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_PACK_BITS_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_PACK_BITS_H_

#include "modules/audio_coding/codecs/ilbc/defines.h"

/*----------------------------------------------------------------*
 *  unpacking of bits from bitstream, i.e., vector of bytes
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_PackBits(
    uint16_t* bitstream, /* (o) The packetized bitstream */
    iLBC_bits* enc_bits, /* (i) Encoded bits */
    int16_t mode         /* (i) Codec mode (20 or 30) */
    );

#endif
