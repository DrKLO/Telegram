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

 WebRtcIlbcfix_PackBits.c

******************************************************************/

#include "modules/audio_coding/codecs/ilbc/defines.h"

/*----------------------------------------------------------------*
 *  unpacking of bits from bitstream, i.e., vector of bytes
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_PackBits(
    uint16_t *bitstream,   /* (o) The packetized bitstream */
    iLBC_bits *enc_bits,  /* (i) Encoded bits */
    int16_t mode     /* (i) Codec mode (20 or 30) */
                             ){
  uint16_t *bitstreamPtr;
  int i, k;
  int16_t *tmpPtr;

  bitstreamPtr=bitstream;

  /* Class 1 bits of ULP */
  /* First int16_t */
  (*bitstreamPtr)  = ((uint16_t)enc_bits->lsf[0])<<10;   /* Bit 0..5  */
  (*bitstreamPtr) |= (enc_bits->lsf[1])<<3;     /* Bit 6..12 */
  (*bitstreamPtr) |= (enc_bits->lsf[2]&0x70)>>4;    /* Bit 13..15 */
  bitstreamPtr++;
  /* Second int16_t */
  (*bitstreamPtr)  = ((uint16_t)enc_bits->lsf[2]&0xF)<<12;  /* Bit 0..3  */

  if (mode==20) {
    (*bitstreamPtr) |= (enc_bits->startIdx)<<10;    /* Bit 4..5  */
    (*bitstreamPtr) |= (enc_bits->state_first)<<9;    /* Bit 6  */
    (*bitstreamPtr) |= (enc_bits->idxForMax)<<3;    /* Bit 7..12 */
    (*bitstreamPtr) |= ((enc_bits->cb_index[0])&0x70)>>4;  /* Bit 13..15 */
    bitstreamPtr++;
    /* Third int16_t */
    (*bitstreamPtr) = ((enc_bits->cb_index[0])&0xE)<<12;  /* Bit 0..2  */
    (*bitstreamPtr) |= ((enc_bits->gain_index[0])&0x18)<<8;  /* Bit 3..4  */
    (*bitstreamPtr) |= ((enc_bits->gain_index[1])&0x8)<<7;  /* Bit 5  */
    (*bitstreamPtr) |= ((enc_bits->cb_index[3])&0xFE)<<2;  /* Bit 6..12 */
    (*bitstreamPtr) |= ((enc_bits->gain_index[3])&0x10)>>2;  /* Bit 13  */
    (*bitstreamPtr) |= ((enc_bits->gain_index[4])&0x8)>>2;  /* Bit 14  */
    (*bitstreamPtr) |= ((enc_bits->gain_index[6])&0x10)>>4;  /* Bit 15  */
  } else { /* mode==30 */
    (*bitstreamPtr) |= (enc_bits->lsf[3])<<6;     /* Bit 4..9  */
    (*bitstreamPtr) |= (enc_bits->lsf[4]&0x7E)>>1;    /* Bit 10..15 */
    bitstreamPtr++;
    /* Third int16_t */
    (*bitstreamPtr)  = ((uint16_t)enc_bits->lsf[4]&0x1)<<15;  /* Bit 0  */
    (*bitstreamPtr) |= (enc_bits->lsf[5])<<8;     /* Bit 1..7  */
    (*bitstreamPtr) |= (enc_bits->startIdx)<<5;     /* Bit 8..10 */
    (*bitstreamPtr) |= (enc_bits->state_first)<<4;    /* Bit 11  */
    (*bitstreamPtr) |= ((enc_bits->idxForMax)&0x3C)>>2;   /* Bit 12..15 */
    bitstreamPtr++;
    /* 4:th int16_t */
    (*bitstreamPtr)  = ((uint16_t)enc_bits->idxForMax&0x3)<<14; /* Bit 0..1  */
    (*bitstreamPtr) |= (enc_bits->cb_index[0]&0x78)<<7;   /* Bit 2..5  */
    (*bitstreamPtr) |= (enc_bits->gain_index[0]&0x10)<<5;  /* Bit 6  */
    (*bitstreamPtr) |= (enc_bits->gain_index[1]&0x8)<<5;  /* Bit 7  */
    (*bitstreamPtr) |= (enc_bits->cb_index[3]&0xFC);   /* Bit 8..13 */
    (*bitstreamPtr) |= (enc_bits->gain_index[3]&0x10)>>3;  /* Bit 14  */
    (*bitstreamPtr) |= (enc_bits->gain_index[4]&0x8)>>3;  /* Bit 15  */
  }
  /* Class 2 bits of ULP */
  /* 4:th to 6:th int16_t for 20 ms case
     5:th to 7:th int16_t for 30 ms case */
  bitstreamPtr++;
  tmpPtr=enc_bits->idxVec;
  for (k=0; k<3; k++) {
    (*bitstreamPtr) = 0;
    for (i=15; i>=0; i--) {
      (*bitstreamPtr) |= ((uint16_t)((*tmpPtr)&0x4)>>2)<<i;
      /* Bit 15-i  */
      tmpPtr++;
    }
    bitstreamPtr++;
  }

  if (mode==20) {
    /* 7:th int16_t */
    (*bitstreamPtr) = 0;
    for (i=15; i>6; i--) {
      (*bitstreamPtr) |= ((uint16_t)((*tmpPtr)&0x4)>>2)<<i;
      /* Bit 15-i  */
      tmpPtr++;
    }
    (*bitstreamPtr) |= (enc_bits->gain_index[1]&0x4)<<4;  /* Bit 9  */
    (*bitstreamPtr) |= (enc_bits->gain_index[3]&0xC)<<2;  /* Bit 10..11 */
    (*bitstreamPtr) |= (enc_bits->gain_index[4]&0x4)<<1;  /* Bit 12  */
    (*bitstreamPtr) |= (enc_bits->gain_index[6]&0x8)>>1;  /* Bit 13  */
    (*bitstreamPtr) |= (enc_bits->gain_index[7]&0xC)>>2;  /* Bit 14..15 */

  } else { /* mode==30 */
    /* 8:th int16_t */
    (*bitstreamPtr) = 0;
    for (i=15; i>5; i--) {
      (*bitstreamPtr) |= ((uint16_t)((*tmpPtr)&0x4)>>2)<<i;
      /* Bit 15-i  */
      tmpPtr++;
    }
    (*bitstreamPtr) |= (enc_bits->cb_index[0]&0x6)<<3;   /* Bit 10..11 */
    (*bitstreamPtr) |= (enc_bits->gain_index[0]&0x8);   /* Bit 12  */
    (*bitstreamPtr) |= (enc_bits->gain_index[1]&0x4);   /* Bit 13  */
    (*bitstreamPtr) |= (enc_bits->cb_index[3]&0x2);    /* Bit 14  */
    (*bitstreamPtr) |= (enc_bits->cb_index[6]&0x80)>>7;   /* Bit 15  */
    bitstreamPtr++;
    /* 9:th int16_t */
    (*bitstreamPtr)  = ((uint16_t)enc_bits->cb_index[6]&0x7E)<<9;/* Bit 0..5  */
    (*bitstreamPtr) |= (enc_bits->cb_index[9]&0xFE)<<2;   /* Bit 6..12 */
    (*bitstreamPtr) |= (enc_bits->cb_index[12]&0xE0)>>5;  /* Bit 13..15 */
    bitstreamPtr++;
    /* 10:th int16_t */
    (*bitstreamPtr)  = ((uint16_t)enc_bits->cb_index[12]&0x1E)<<11;/* Bit 0..3 */
    (*bitstreamPtr) |= (enc_bits->gain_index[3]&0xC)<<8;  /* Bit 4..5  */
    (*bitstreamPtr) |= (enc_bits->gain_index[4]&0x6)<<7;  /* Bit 6..7  */
    (*bitstreamPtr) |= (enc_bits->gain_index[6]&0x18)<<3;  /* Bit 8..9  */
    (*bitstreamPtr) |= (enc_bits->gain_index[7]&0xC)<<2;  /* Bit 10..11 */
    (*bitstreamPtr) |= (enc_bits->gain_index[9]&0x10)>>1;  /* Bit 12  */
    (*bitstreamPtr) |= (enc_bits->gain_index[10]&0x8)>>1;  /* Bit 13  */
    (*bitstreamPtr) |= (enc_bits->gain_index[12]&0x10)>>3;  /* Bit 14  */
    (*bitstreamPtr) |= (enc_bits->gain_index[13]&0x8)>>3;  /* Bit 15  */
  }
  bitstreamPtr++;
  /* Class 3 bits of ULP */
  /*  8:th to 14:th int16_t for 20 ms case
      11:th to 17:th int16_t for 30 ms case */
  tmpPtr=enc_bits->idxVec;
  for (k=0; k<7; k++) {
    (*bitstreamPtr) = 0;
    for (i=14; i>=0; i-=2) {
      (*bitstreamPtr) |= ((uint16_t)((*tmpPtr)&0x3))<<i; /* Bit 15-i..14-i*/
      tmpPtr++;
    }
    bitstreamPtr++;
  }

  if (mode==20) {
    /* 15:th int16_t */
    (*bitstreamPtr)  = ((uint16_t)((enc_bits->idxVec[56])&0x3))<<14;/* Bit 0..1 */
    (*bitstreamPtr) |= (((enc_bits->cb_index[0])&1))<<13;  /* Bit 2  */
    (*bitstreamPtr) |= ((enc_bits->cb_index[1]))<<6;   /* Bit 3..9  */
    (*bitstreamPtr) |= ((enc_bits->cb_index[2])&0x7E)>>1;  /* Bit 10..15 */
    bitstreamPtr++;
    /* 16:th int16_t */
    (*bitstreamPtr) = ((uint16_t)((enc_bits->cb_index[2])&0x1))<<15;
    /* Bit 0  */
    (*bitstreamPtr) |= ((enc_bits->gain_index[0])&0x7)<<12;  /* Bit 1..3  */
    (*bitstreamPtr) |= ((enc_bits->gain_index[1])&0x3)<<10;  /* Bit 4..5  */
    (*bitstreamPtr) |= ((enc_bits->gain_index[2]))<<7;   /* Bit 6..8  */
    (*bitstreamPtr) |= ((enc_bits->cb_index[3])&0x1)<<6;  /* Bit 9  */
    (*bitstreamPtr) |= ((enc_bits->cb_index[4])&0x7E)>>1;  /* Bit 10..15 */
    bitstreamPtr++;
    /* 17:th int16_t */
    (*bitstreamPtr) = ((uint16_t)((enc_bits->cb_index[4])&0x1))<<15;
    /* Bit 0  */
    (*bitstreamPtr) |= (enc_bits->cb_index[5])<<8;    /* Bit 1..7  */
    (*bitstreamPtr) |= (enc_bits->cb_index[6]);     /* Bit 8..15 */
    bitstreamPtr++;
    /* 18:th int16_t */
    (*bitstreamPtr) = ((uint16_t)(enc_bits->cb_index[7]))<<8; /* Bit 0..7  */
    (*bitstreamPtr) |= (enc_bits->cb_index[8]);     /* Bit 8..15 */
    bitstreamPtr++;
    /* 19:th int16_t */
    (*bitstreamPtr) = ((uint16_t)((enc_bits->gain_index[3])&0x3))<<14;
    /* Bit 0..1  */
    (*bitstreamPtr) |= ((enc_bits->gain_index[4])&0x3)<<12;  /* Bit 2..3  */
    (*bitstreamPtr) |= ((enc_bits->gain_index[5]))<<9;   /* Bit 4..6  */
    (*bitstreamPtr) |= ((enc_bits->gain_index[6])&0x7)<<6;  /* Bit 7..9  */
    (*bitstreamPtr) |= ((enc_bits->gain_index[7])&0x3)<<4;  /* Bit 10..11 */
    (*bitstreamPtr) |= (enc_bits->gain_index[8])<<1;   /* Bit 12..14 */
  } else { /* mode==30 */
    /* 18:th int16_t */
    (*bitstreamPtr)  = ((uint16_t)((enc_bits->idxVec[56])&0x3))<<14;/* Bit 0..1 */
    (*bitstreamPtr) |= (((enc_bits->idxVec[57])&0x3))<<12;  /* Bit 2..3  */
    (*bitstreamPtr) |= (((enc_bits->cb_index[0])&1))<<11;  /* Bit 4  */
    (*bitstreamPtr) |= ((enc_bits->cb_index[1]))<<4;   /* Bit 5..11 */
    (*bitstreamPtr) |= ((enc_bits->cb_index[2])&0x78)>>3;  /* Bit 12..15 */
    bitstreamPtr++;
    /* 19:th int16_t */
    (*bitstreamPtr)  = ((uint16_t)(enc_bits->cb_index[2])&0x7)<<13;
    /* Bit 0..2  */
    (*bitstreamPtr) |= ((enc_bits->gain_index[0])&0x7)<<10;  /* Bit 3..5  */
    (*bitstreamPtr) |= ((enc_bits->gain_index[1])&0x3)<<8;  /* Bit 6..7  */
    (*bitstreamPtr) |= ((enc_bits->gain_index[2])&0x7)<<5;  /* Bit 8..10 */
    (*bitstreamPtr) |= ((enc_bits->cb_index[3])&0x1)<<4;  /* Bit 11  */
    (*bitstreamPtr) |= ((enc_bits->cb_index[4])&0x78)>>3;  /* Bit 12..15 */
    bitstreamPtr++;
    /* 20:th int16_t */
    (*bitstreamPtr)  = ((uint16_t)(enc_bits->cb_index[4])&0x7)<<13;
    /* Bit 0..2  */
    (*bitstreamPtr) |= ((enc_bits->cb_index[5]))<<6;   /* Bit 3..9  */
    (*bitstreamPtr) |= ((enc_bits->cb_index[6])&0x1)<<5;  /* Bit 10  */
    (*bitstreamPtr) |= ((enc_bits->cb_index[7])&0xF8)>>3;  /* Bit 11..15 */
    bitstreamPtr++;
    /* 21:st int16_t */
    (*bitstreamPtr)  = ((uint16_t)(enc_bits->cb_index[7])&0x7)<<13;
    /* Bit 0..2  */
    (*bitstreamPtr) |= ((enc_bits->cb_index[8]))<<5;   /* Bit 3..10 */
    (*bitstreamPtr) |= ((enc_bits->cb_index[9])&0x1)<<4;  /* Bit 11  */
    (*bitstreamPtr) |= ((enc_bits->cb_index[10])&0xF0)>>4;  /* Bit 12..15 */
    bitstreamPtr++;
    /* 22:nd int16_t */
    (*bitstreamPtr)  = ((uint16_t)(enc_bits->cb_index[10])&0xF)<<12;
    /* Bit 0..3  */
    (*bitstreamPtr) |= ((enc_bits->cb_index[11]))<<4;   /* Bit 4..11 */
    (*bitstreamPtr) |= ((enc_bits->cb_index[12])&0x1)<<3;  /* Bit 12  */
    (*bitstreamPtr) |= ((enc_bits->cb_index[13])&0xE0)>>5;  /* Bit 13..15 */
    bitstreamPtr++;
    /* 23:rd int16_t */
    (*bitstreamPtr)  = ((uint16_t)(enc_bits->cb_index[13])&0x1F)<<11;
    /* Bit 0..4  */
    (*bitstreamPtr) |= ((enc_bits->cb_index[14]))<<3;   /* Bit 5..12 */
    (*bitstreamPtr) |= ((enc_bits->gain_index[3])&0x3)<<1;  /* Bit 13..14 */
    (*bitstreamPtr) |= ((enc_bits->gain_index[4])&0x1);   /* Bit 15  */
    bitstreamPtr++;
    /* 24:rd int16_t */
    (*bitstreamPtr)  = ((uint16_t)(enc_bits->gain_index[5]))<<13;
    /* Bit 0..2  */
    (*bitstreamPtr) |= ((enc_bits->gain_index[6])&0x7)<<10;  /* Bit 3..5  */
    (*bitstreamPtr) |= ((enc_bits->gain_index[7])&0x3)<<8;  /* Bit 6..7  */
    (*bitstreamPtr) |= ((enc_bits->gain_index[8]))<<5;   /* Bit 8..10 */
    (*bitstreamPtr) |= ((enc_bits->gain_index[9])&0xF)<<1;  /* Bit 11..14 */
    (*bitstreamPtr) |= ((enc_bits->gain_index[10])&0x4)>>2;  /* Bit 15  */
    bitstreamPtr++;
    /* 25:rd int16_t */
    (*bitstreamPtr)  = ((uint16_t)(enc_bits->gain_index[10])&0x3)<<14;
    /* Bit 0..1  */
    (*bitstreamPtr) |= ((enc_bits->gain_index[11]))<<11;  /* Bit 2..4  */
    (*bitstreamPtr) |= ((enc_bits->gain_index[12])&0xF)<<7;  /* Bit 5..8  */
    (*bitstreamPtr) |= ((enc_bits->gain_index[13])&0x7)<<4;  /* Bit 9..11 */
    (*bitstreamPtr) |= ((enc_bits->gain_index[14]))<<1;   /* Bit 12..14 */
  }
  /* Last bit is automatically zero */

  return;
}
