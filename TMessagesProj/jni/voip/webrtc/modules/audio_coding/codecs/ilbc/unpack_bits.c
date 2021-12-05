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

 WebRtcIlbcfix_UnpackBits.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/unpack_bits.h"

#include "modules/audio_coding/codecs/ilbc/defines.h"

/*----------------------------------------------------------------*
 *  unpacking of bits from bitstream, i.e., vector of bytes
 *---------------------------------------------------------------*/

int16_t WebRtcIlbcfix_UnpackBits( /* (o) "Empty" frame indicator */
    const uint16_t *bitstream,    /* (i) The packatized bitstream */
    iLBC_bits *enc_bits,  /* (o) Paramerers from bitstream */
    int16_t mode     /* (i) Codec mode (20 or 30) */
                                        ) {
  const uint16_t *bitstreamPtr;
  int i, k;
  int16_t *tmpPtr;

  bitstreamPtr=bitstream;

  /* First int16_t */
  enc_bits->lsf[0]  =  (*bitstreamPtr)>>10;       /* Bit 0..5  */
  enc_bits->lsf[1]  = ((*bitstreamPtr)>>3)&0x7F;      /* Bit 6..12 */
  enc_bits->lsf[2]  = ((*bitstreamPtr)&0x7)<<4;      /* Bit 13..15 */
  bitstreamPtr++;
  /* Second int16_t */
  enc_bits->lsf[2] |= ((*bitstreamPtr)>>12)&0xF;      /* Bit 0..3  */

  if (mode==20) {
    enc_bits->startIdx             = ((*bitstreamPtr)>>10)&0x3;  /* Bit 4..5  */
    enc_bits->state_first          = ((*bitstreamPtr)>>9)&0x1;  /* Bit 6  */
    enc_bits->idxForMax            = ((*bitstreamPtr)>>3)&0x3F;  /* Bit 7..12 */
    enc_bits->cb_index[0]          = ((*bitstreamPtr)&0x7)<<4;  /* Bit 13..15 */
    bitstreamPtr++;
    /* Third int16_t */
    enc_bits->cb_index[0]         |= ((*bitstreamPtr)>>12)&0xE;  /* Bit 0..2  */
    enc_bits->gain_index[0]        = ((*bitstreamPtr)>>8)&0x18;  /* Bit 3..4  */
    enc_bits->gain_index[1]        = ((*bitstreamPtr)>>7)&0x8;  /* Bit 5  */
    enc_bits->cb_index[3]          = ((*bitstreamPtr)>>2)&0xFE;  /* Bit 6..12 */
    enc_bits->gain_index[3]        = ((*bitstreamPtr)<<2)&0x10;  /* Bit 13  */
    enc_bits->gain_index[4]        = ((*bitstreamPtr)<<2)&0x8;  /* Bit 14  */
    enc_bits->gain_index[6]        = ((*bitstreamPtr)<<4)&0x10;  /* Bit 15  */
  } else { /* mode==30 */
    enc_bits->lsf[3]               = ((*bitstreamPtr)>>6)&0x3F;  /* Bit 4..9  */
    enc_bits->lsf[4]               = ((*bitstreamPtr)<<1)&0x7E;  /* Bit 10..15 */
    bitstreamPtr++;
    /* Third int16_t */
    enc_bits->lsf[4]              |= ((*bitstreamPtr)>>15)&0x1;  /* Bit 0  */
    enc_bits->lsf[5]               = ((*bitstreamPtr)>>8)&0x7F;  /* Bit 1..7  */
    enc_bits->startIdx             = ((*bitstreamPtr)>>5)&0x7;  /* Bit 8..10 */
    enc_bits->state_first          = ((*bitstreamPtr)>>4)&0x1;  /* Bit 11  */
    enc_bits->idxForMax            = ((*bitstreamPtr)<<2)&0x3C;  /* Bit 12..15 */
    bitstreamPtr++;
    /* 4:th int16_t */
    enc_bits->idxForMax           |= ((*bitstreamPtr)>>14)&0x3;  /* Bit 0..1  */
    enc_bits->cb_index[0]        = ((*bitstreamPtr)>>7)&0x78;  /* Bit 2..5  */
    enc_bits->gain_index[0]        = ((*bitstreamPtr)>>5)&0x10;  /* Bit 6  */
    enc_bits->gain_index[1]        = ((*bitstreamPtr)>>5)&0x8;  /* Bit 7  */
    enc_bits->cb_index[3]          = ((*bitstreamPtr))&0xFC;  /* Bit 8..13 */
    enc_bits->gain_index[3]        = ((*bitstreamPtr)<<3)&0x10;  /* Bit 14  */
    enc_bits->gain_index[4]        = ((*bitstreamPtr)<<3)&0x8;  /* Bit 15  */
  }
  /* Class 2 bits of ULP */
  /* 4:th to 6:th int16_t for 20 ms case
     5:th to 7:th int16_t for 30 ms case */
  bitstreamPtr++;
  tmpPtr=enc_bits->idxVec;
  for (k=0; k<3; k++) {
    for (i=15; i>=0; i--) {
      (*tmpPtr)                  = (((*bitstreamPtr)>>i)<<2)&0x4;
      /* Bit 15-i  */
      tmpPtr++;
    }
    bitstreamPtr++;
  }

  if (mode==20) {
    /* 7:th int16_t */
    for (i=15; i>6; i--) {
      (*tmpPtr)                  = (((*bitstreamPtr)>>i)<<2)&0x4;
      /* Bit 15-i  */
      tmpPtr++;
    }
    enc_bits->gain_index[1]       |= ((*bitstreamPtr)>>4)&0x4; /* Bit 9  */
    enc_bits->gain_index[3]       |= ((*bitstreamPtr)>>2)&0xC; /* Bit 10..11 */
    enc_bits->gain_index[4]       |= ((*bitstreamPtr)>>1)&0x4; /* Bit 12  */
    enc_bits->gain_index[6]       |= ((*bitstreamPtr)<<1)&0x8; /* Bit 13  */
    enc_bits->gain_index[7]        = ((*bitstreamPtr)<<2)&0xC; /* Bit 14..15 */

  } else { /* mode==30 */
    /* 8:th int16_t */
    for (i=15; i>5; i--) {
      (*tmpPtr)                  = (((*bitstreamPtr)>>i)<<2)&0x4;
      /* Bit 15-i  */
      tmpPtr++;
    }
    enc_bits->cb_index[0]         |= ((*bitstreamPtr)>>3)&0x6; /* Bit 10..11 */
    enc_bits->gain_index[0]       |= ((*bitstreamPtr))&0x8;  /* Bit 12  */
    enc_bits->gain_index[1]       |= ((*bitstreamPtr))&0x4;  /* Bit 13  */
    enc_bits->cb_index[3]         |= ((*bitstreamPtr))&0x2;  /* Bit 14  */
    enc_bits->cb_index[6]          = ((*bitstreamPtr)<<7)&0x80; /* Bit 15  */
    bitstreamPtr++;
    /* 9:th int16_t */
    enc_bits->cb_index[6]         |= ((*bitstreamPtr)>>9)&0x7E; /* Bit 0..5  */
    enc_bits->cb_index[9]          = ((*bitstreamPtr)>>2)&0xFE; /* Bit 6..12 */
    enc_bits->cb_index[12]         = ((*bitstreamPtr)<<5)&0xE0; /* Bit 13..15 */
    bitstreamPtr++;
    /* 10:th int16_t */
    enc_bits->cb_index[12]         |= ((*bitstreamPtr)>>11)&0x1E;/* Bit 0..3 */
    enc_bits->gain_index[3]       |= ((*bitstreamPtr)>>8)&0xC; /* Bit 4..5  */
    enc_bits->gain_index[4]       |= ((*bitstreamPtr)>>7)&0x6; /* Bit 6..7  */
    enc_bits->gain_index[6]        = ((*bitstreamPtr)>>3)&0x18; /* Bit 8..9  */
    enc_bits->gain_index[7]        = ((*bitstreamPtr)>>2)&0xC; /* Bit 10..11 */
    enc_bits->gain_index[9]        = ((*bitstreamPtr)<<1)&0x10; /* Bit 12  */
    enc_bits->gain_index[10]       = ((*bitstreamPtr)<<1)&0x8; /* Bit 13  */
    enc_bits->gain_index[12]       = ((*bitstreamPtr)<<3)&0x10; /* Bit 14  */
    enc_bits->gain_index[13]       = ((*bitstreamPtr)<<3)&0x8; /* Bit 15  */
  }
  bitstreamPtr++;
  /* Class 3 bits of ULP */
  /*  8:th to 14:th int16_t for 20 ms case
      11:th to 17:th int16_t for 30 ms case */
  tmpPtr=enc_bits->idxVec;
  for (k=0; k<7; k++) {
    for (i=14; i>=0; i-=2) {
      (*tmpPtr)                 |= ((*bitstreamPtr)>>i)&0x3; /* Bit 15-i..14-i*/
      tmpPtr++;
    }
    bitstreamPtr++;
  }

  if (mode==20) {
    /* 15:th int16_t */
    enc_bits->idxVec[56]          |= ((*bitstreamPtr)>>14)&0x3; /* Bit 0..1  */
    enc_bits->cb_index[0]         |= ((*bitstreamPtr)>>13)&0x1; /* Bit 2  */
    enc_bits->cb_index[1]          = ((*bitstreamPtr)>>6)&0x7F; /* Bit 3..9  */
    enc_bits->cb_index[2]          = ((*bitstreamPtr)<<1)&0x7E; /* Bit 10..15 */
    bitstreamPtr++;
    /* 16:th int16_t */
    enc_bits->cb_index[2]         |= ((*bitstreamPtr)>>15)&0x1; /* Bit 0  */
    enc_bits->gain_index[0]       |= ((*bitstreamPtr)>>12)&0x7; /* Bit 1..3  */
    enc_bits->gain_index[1]       |= ((*bitstreamPtr)>>10)&0x3; /* Bit 4..5  */
    enc_bits->gain_index[2]        = ((*bitstreamPtr)>>7)&0x7; /* Bit 6..8  */
    enc_bits->cb_index[3]         |= ((*bitstreamPtr)>>6)&0x1; /* Bit 9  */
    enc_bits->cb_index[4]          = ((*bitstreamPtr)<<1)&0x7E; /* Bit 10..15 */
    bitstreamPtr++;
    /* 17:th int16_t */
    enc_bits->cb_index[4]         |= ((*bitstreamPtr)>>15)&0x1; /* Bit 0  */
    enc_bits->cb_index[5]          = ((*bitstreamPtr)>>8)&0x7F; /* Bit 1..7  */
    enc_bits->cb_index[6]          = ((*bitstreamPtr))&0xFF; /* Bit 8..15 */
    bitstreamPtr++;
    /* 18:th int16_t */
    enc_bits->cb_index[7]          = (*bitstreamPtr)>>8;  /* Bit 0..7  */
    enc_bits->cb_index[8]          = (*bitstreamPtr)&0xFF;  /* Bit 8..15 */
    bitstreamPtr++;
    /* 19:th int16_t */
    enc_bits->gain_index[3]       |= ((*bitstreamPtr)>>14)&0x3; /* Bit 0..1  */
    enc_bits->gain_index[4]       |= ((*bitstreamPtr)>>12)&0x3; /* Bit 2..3  */
    enc_bits->gain_index[5]        = ((*bitstreamPtr)>>9)&0x7; /* Bit 4..6  */
    enc_bits->gain_index[6]       |= ((*bitstreamPtr)>>6)&0x7; /* Bit 7..9  */
    enc_bits->gain_index[7]       |= ((*bitstreamPtr)>>4)&0x3; /* Bit 10..11 */
    enc_bits->gain_index[8]        = ((*bitstreamPtr)>>1)&0x7; /* Bit 12..14 */
  } else { /* mode==30 */
    /* 18:th int16_t */
    enc_bits->idxVec[56]          |= ((*bitstreamPtr)>>14)&0x3; /* Bit 0..1  */
    enc_bits->idxVec[57]          |= ((*bitstreamPtr)>>12)&0x3; /* Bit 2..3  */
    enc_bits->cb_index[0]         |= ((*bitstreamPtr)>>11)&1; /* Bit 4  */
    enc_bits->cb_index[1]          = ((*bitstreamPtr)>>4)&0x7F; /* Bit 5..11 */
    enc_bits->cb_index[2]          = ((*bitstreamPtr)<<3)&0x78; /* Bit 12..15 */
    bitstreamPtr++;
    /* 19:th int16_t */
    enc_bits->cb_index[2]         |= ((*bitstreamPtr)>>13)&0x7; /* Bit 0..2  */
    enc_bits->gain_index[0]       |= ((*bitstreamPtr)>>10)&0x7; /* Bit 3..5  */
    enc_bits->gain_index[1]       |= ((*bitstreamPtr)>>8)&0x3; /* Bit 6..7  */
    enc_bits->gain_index[2]        = ((*bitstreamPtr)>>5)&0x7; /* Bit 8..10 */
    enc_bits->cb_index[3]         |= ((*bitstreamPtr)>>4)&0x1; /* Bit 11  */
    enc_bits->cb_index[4]          = ((*bitstreamPtr)<<3)&0x78; /* Bit 12..15 */
    bitstreamPtr++;
    /* 20:th int16_t */
    enc_bits->cb_index[4]         |= ((*bitstreamPtr)>>13)&0x7; /* Bit 0..2  */
    enc_bits->cb_index[5]          = ((*bitstreamPtr)>>6)&0x7F; /* Bit 3..9  */
    enc_bits->cb_index[6]         |= ((*bitstreamPtr)>>5)&0x1; /* Bit 10  */
    enc_bits->cb_index[7]          = ((*bitstreamPtr)<<3)&0xF8; /* Bit 11..15 */
    bitstreamPtr++;
    /* 21:st int16_t */
    enc_bits->cb_index[7]         |= ((*bitstreamPtr)>>13)&0x7; /* Bit 0..2  */
    enc_bits->cb_index[8]          = ((*bitstreamPtr)>>5)&0xFF; /* Bit 3..10 */
    enc_bits->cb_index[9]         |= ((*bitstreamPtr)>>4)&0x1; /* Bit 11  */
    enc_bits->cb_index[10]         = ((*bitstreamPtr)<<4)&0xF0; /* Bit 12..15 */
    bitstreamPtr++;
    /* 22:nd int16_t */
    enc_bits->cb_index[10]        |= ((*bitstreamPtr)>>12)&0xF; /* Bit 0..3  */
    enc_bits->cb_index[11]         = ((*bitstreamPtr)>>4)&0xFF; /* Bit 4..11 */
    enc_bits->cb_index[12]        |= ((*bitstreamPtr)>>3)&0x1; /* Bit 12  */
    enc_bits->cb_index[13]         = ((*bitstreamPtr)<<5)&0xE0; /* Bit 13..15 */
    bitstreamPtr++;
    /* 23:rd int16_t */
    enc_bits->cb_index[13]        |= ((*bitstreamPtr)>>11)&0x1F;/* Bit 0..4  */
    enc_bits->cb_index[14]         = ((*bitstreamPtr)>>3)&0xFF; /* Bit 5..12 */
    enc_bits->gain_index[3]       |= ((*bitstreamPtr)>>1)&0x3; /* Bit 13..14 */
    enc_bits->gain_index[4]       |= ((*bitstreamPtr)&0x1);  /* Bit 15  */
    bitstreamPtr++;
    /* 24:rd int16_t */
    enc_bits->gain_index[5]        = ((*bitstreamPtr)>>13)&0x7; /* Bit 0..2  */
    enc_bits->gain_index[6]       |= ((*bitstreamPtr)>>10)&0x7; /* Bit 3..5  */
    enc_bits->gain_index[7]       |= ((*bitstreamPtr)>>8)&0x3; /* Bit 6..7  */
    enc_bits->gain_index[8]        = ((*bitstreamPtr)>>5)&0x7; /* Bit 8..10 */
    enc_bits->gain_index[9]       |= ((*bitstreamPtr)>>1)&0xF; /* Bit 11..14 */
    enc_bits->gain_index[10]      |= ((*bitstreamPtr)<<2)&0x4; /* Bit 15  */
    bitstreamPtr++;
    /* 25:rd int16_t */
    enc_bits->gain_index[10]      |= ((*bitstreamPtr)>>14)&0x3; /* Bit 0..1  */
    enc_bits->gain_index[11]       = ((*bitstreamPtr)>>11)&0x7; /* Bit 2..4  */
    enc_bits->gain_index[12]      |= ((*bitstreamPtr)>>7)&0xF; /* Bit 5..8  */
    enc_bits->gain_index[13]      |= ((*bitstreamPtr)>>4)&0x7; /* Bit 9..11 */
    enc_bits->gain_index[14]       = ((*bitstreamPtr)>>1)&0x7; /* Bit 12..14 */
  }
  /* Last bit should be zero, otherwise it's an "empty" frame */
  if (((*bitstreamPtr)&0x1) == 1) {
    return(1);
  } else {
    return(0);
  }
}
