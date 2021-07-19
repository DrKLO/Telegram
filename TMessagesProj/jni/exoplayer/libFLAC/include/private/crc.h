/* libFLAC - Free Lossless Audio Codec library
 * Copyright (C) 2000-2009  Josh Coalson
 * Copyright (C) 2011-2016  Xiph.Org Foundation
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * - Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * - Neither the name of the Xiph.org Foundation nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE FOUNDATION OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#ifndef FLAC__PRIVATE__CRC_H
#define FLAC__PRIVATE__CRC_H

#include "FLAC/ordinals.h"

/* 8 bit CRC generator, MSB shifted first
** polynomial = x^8 + x^2 + x^1 + x^0
** init = 0
*/
extern FLAC__byte const FLAC__crc8_table[256];
#define FLAC__CRC8_UPDATE(data, crc) (crc) = FLAC__crc8_table[(crc) ^ (data)];
void FLAC__crc8_update(const FLAC__byte data, FLAC__uint8 *crc);
void FLAC__crc8_update_block(const FLAC__byte *data, uint32_t len, FLAC__uint8 *crc);
FLAC__uint8 FLAC__crc8(const FLAC__byte *data, uint32_t len);

/* 16 bit CRC generator, MSB shifted first
** polynomial = x^16 + x^15 + x^2 + x^0
** init = 0
*/
extern uint32_t const FLAC__crc16_table[256];

#define FLAC__CRC16_UPDATE(data, crc) ((((crc)<<8) & 0xffff) ^ FLAC__crc16_table[((crc)>>8) ^ (data)])
/* this alternate may be faster on some systems/compilers */
#if 0
#define FLAC__CRC16_UPDATE(data, crc) ((((crc)<<8) ^ FLAC__crc16_table[((crc)>>8) ^ (data)]) & 0xffff)
#endif

uint32_t FLAC__crc16(const FLAC__byte *data, uint32_t len);

#endif
