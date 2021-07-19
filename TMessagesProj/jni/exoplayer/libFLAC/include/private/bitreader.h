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

#ifndef FLAC__PRIVATE__BITREADER_H
#define FLAC__PRIVATE__BITREADER_H

#include <stdio.h> /* for FILE */
#include "FLAC/ordinals.h"
#include "cpu.h"

/*
 * opaque structure definition
 */
struct FLAC__BitReader;
typedef struct FLAC__BitReader FLAC__BitReader;

typedef FLAC__bool (*FLAC__BitReaderReadCallback)(FLAC__byte buffer[], size_t *bytes, void *client_data);

/*
 * construction, deletion, initialization, etc functions
 */
FLAC__BitReader *FLAC__bitreader_new(void);
void FLAC__bitreader_delete(FLAC__BitReader *br);
FLAC__bool FLAC__bitreader_init(FLAC__BitReader *br, FLAC__BitReaderReadCallback rcb, void *cd);
void FLAC__bitreader_free(FLAC__BitReader *br); /* does not 'free(br)' */
FLAC__bool FLAC__bitreader_clear(FLAC__BitReader *br);
void FLAC__bitreader_dump(const FLAC__BitReader *br, FILE *out);

/*
 * CRC functions
 */
void FLAC__bitreader_reset_read_crc16(FLAC__BitReader *br, FLAC__uint16 seed);
FLAC__uint16 FLAC__bitreader_get_read_crc16(FLAC__BitReader *br);

/*
 * info functions
 */
FLAC__bool FLAC__bitreader_is_consumed_byte_aligned(const FLAC__BitReader *br);
uint32_t FLAC__bitreader_bits_left_for_byte_alignment(const FLAC__BitReader *br);
uint32_t FLAC__bitreader_get_input_bits_unconsumed(const FLAC__BitReader *br);

/*
 * read functions
 */

FLAC__bool FLAC__bitreader_read_raw_uint32(FLAC__BitReader *br, FLAC__uint32 *val, uint32_t bits);
FLAC__bool FLAC__bitreader_read_raw_int32(FLAC__BitReader *br, FLAC__int32 *val, uint32_t bits);
FLAC__bool FLAC__bitreader_read_raw_uint64(FLAC__BitReader *br, FLAC__uint64 *val, uint32_t bits);
FLAC__bool FLAC__bitreader_read_uint32_little_endian(FLAC__BitReader *br, FLAC__uint32 *val); /*only for bits=32*/
FLAC__bool FLAC__bitreader_skip_bits_no_crc(FLAC__BitReader *br, uint32_t bits); /* WATCHOUT: does not CRC the skipped data! */ /*@@@@ add to unit tests */
FLAC__bool FLAC__bitreader_skip_byte_block_aligned_no_crc(FLAC__BitReader *br, uint32_t nvals); /* WATCHOUT: does not CRC the read data! */
FLAC__bool FLAC__bitreader_read_byte_block_aligned_no_crc(FLAC__BitReader *br, FLAC__byte *val, uint32_t nvals); /* WATCHOUT: does not CRC the read data! */
FLAC__bool FLAC__bitreader_read_unary_unsigned(FLAC__BitReader *br, uint32_t *val);
FLAC__bool FLAC__bitreader_read_rice_signed(FLAC__BitReader *br, int *val, uint32_t parameter);
FLAC__bool FLAC__bitreader_read_rice_signed_block(FLAC__BitReader *br, int vals[], uint32_t nvals, uint32_t parameter);
#if 0 /* UNUSED */
FLAC__bool FLAC__bitreader_read_golomb_signed(FLAC__BitReader *br, int *val, uint32_t parameter);
FLAC__bool FLAC__bitreader_read_golomb_unsigned(FLAC__BitReader *br, uint32_t *val, uint32_t parameter);
#endif
FLAC__bool FLAC__bitreader_read_utf8_uint32(FLAC__BitReader *br, FLAC__uint32 *val, FLAC__byte *raw, uint32_t *rawlen);
FLAC__bool FLAC__bitreader_read_utf8_uint64(FLAC__BitReader *br, FLAC__uint64 *val, FLAC__byte *raw, uint32_t *rawlen);
#endif
