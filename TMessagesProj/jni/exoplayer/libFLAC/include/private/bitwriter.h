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

#ifndef FLAC__PRIVATE__BITWRITER_H
#define FLAC__PRIVATE__BITWRITER_H

#include <stdio.h> /* for FILE */
#include "FLAC/ordinals.h"

/*
 * opaque structure definition
 */
struct FLAC__BitWriter;
typedef struct FLAC__BitWriter FLAC__BitWriter;

/*
 * construction, deletion, initialization, etc functions
 */
FLAC__BitWriter *FLAC__bitwriter_new(void);
void FLAC__bitwriter_delete(FLAC__BitWriter *bw);
FLAC__bool FLAC__bitwriter_init(FLAC__BitWriter *bw);
void FLAC__bitwriter_free(FLAC__BitWriter *bw); /* does not 'free(buffer)' */
void FLAC__bitwriter_clear(FLAC__BitWriter *bw);
void FLAC__bitwriter_dump(const FLAC__BitWriter *bw, FILE *out);

/*
 * CRC functions
 *
 * non-const *bw because they have to cal FLAC__bitwriter_get_buffer()
 */
FLAC__bool FLAC__bitwriter_get_write_crc16(FLAC__BitWriter *bw, FLAC__uint16 *crc);
FLAC__bool FLAC__bitwriter_get_write_crc8(FLAC__BitWriter *bw, FLAC__byte *crc);

/*
 * info functions
 */
FLAC__bool FLAC__bitwriter_is_byte_aligned(const FLAC__BitWriter *bw);
uint32_t FLAC__bitwriter_get_input_bits_unconsumed(const FLAC__BitWriter *bw); /* can be called anytime, returns total # of bits unconsumed */

/*
 * direct buffer access
 *
 * there may be no calls on the bitwriter between get and release.
 * the bitwriter continues to own the returned buffer.
 * before get, bitwriter MUST be byte aligned: check with FLAC__bitwriter_is_byte_aligned()
 */
FLAC__bool FLAC__bitwriter_get_buffer(FLAC__BitWriter *bw, const FLAC__byte **buffer, size_t *bytes);
void FLAC__bitwriter_release_buffer(FLAC__BitWriter *bw);

/*
 * write functions
 */
FLAC__bool FLAC__bitwriter_write_zeroes(FLAC__BitWriter *bw, uint32_t bits);
FLAC__bool FLAC__bitwriter_write_raw_uint32(FLAC__BitWriter *bw, FLAC__uint32 val, uint32_t bits);
FLAC__bool FLAC__bitwriter_write_raw_int32(FLAC__BitWriter *bw, FLAC__int32 val, uint32_t bits);
FLAC__bool FLAC__bitwriter_write_raw_uint64(FLAC__BitWriter *bw, FLAC__uint64 val, uint32_t bits);
FLAC__bool FLAC__bitwriter_write_raw_uint32_little_endian(FLAC__BitWriter *bw, FLAC__uint32 val); /*only for bits=32*/
FLAC__bool FLAC__bitwriter_write_byte_block(FLAC__BitWriter *bw, const FLAC__byte vals[], uint32_t nvals);
FLAC__bool FLAC__bitwriter_write_unary_unsigned(FLAC__BitWriter *bw, uint32_t val);
uint32_t FLAC__bitwriter_rice_bits(FLAC__int32 val, uint32_t parameter);
#if 0 /* UNUSED */
uint32_t FLAC__bitwriter_golomb_bits_signed(int val, uint32_t parameter);
uint32_t FLAC__bitwriter_golomb_bits_unsigned(uint32_t val, uint32_t parameter);
#endif
FLAC__bool FLAC__bitwriter_write_rice_signed(FLAC__BitWriter *bw, FLAC__int32 val, uint32_t parameter);
FLAC__bool FLAC__bitwriter_write_rice_signed_block(FLAC__BitWriter *bw, const FLAC__int32 *vals, uint32_t nvals, uint32_t parameter);
#if 0 /* UNUSED */
FLAC__bool FLAC__bitwriter_write_golomb_signed(FLAC__BitWriter *bw, int val, uint32_t parameter);
FLAC__bool FLAC__bitwriter_write_golomb_unsigned(FLAC__BitWriter *bw, uint32_t val, uint32_t parameter);
#endif
FLAC__bool FLAC__bitwriter_write_utf8_uint32(FLAC__BitWriter *bw, FLAC__uint32 val);
FLAC__bool FLAC__bitwriter_write_utf8_uint64(FLAC__BitWriter *bw, FLAC__uint64 val);
FLAC__bool FLAC__bitwriter_zero_pad_to_byte_boundary(FLAC__BitWriter *bw);

#endif
