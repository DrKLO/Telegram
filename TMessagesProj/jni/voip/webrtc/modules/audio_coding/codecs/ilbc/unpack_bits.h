/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/******************************************************************

 iLBC Speech Coder ANSI-C Source Code

 WebRtcIlbcfix_UnpackBits.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_UNPACK_BITS_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_UNPACK_BITS_H_

#include "modules/audio_coding/codecs/ilbc/defines.h"

/*----------------------------------------------------------------*
 *  unpacking of bits from bitstream, i.e., vector of bytes
 *---------------------------------------------------------------*/

int16_t
WebRtcIlbcfix_UnpackBits(/* (o) "Empty" frame indicator */
                         const uint16_t*
                             bitstream, /* (i) The packatized bitstream */
                         iLBC_bits*
                             enc_bits, /* (o) Paramerers from bitstream */
                         int16_t mode  /* (i) Codec mode (20 or 30) */
                         );

#endif
