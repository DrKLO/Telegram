/* libFLAC - Free Lossless Audio Codec library
 * Copyright (C) 2004-2009  Josh Coalson
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

#ifdef HAVE_CONFIG_H
#  include <config.h>
#endif

#include "FLAC/assert.h"
#include "share/compat.h"
#include "private/float.h"

#ifdef FLAC__INTEGER_ONLY_LIBRARY

const FLAC__fixedpoint FLAC__FP_ZERO = 0;
const FLAC__fixedpoint FLAC__FP_ONE_HALF = 0x00008000;
const FLAC__fixedpoint FLAC__FP_ONE = 0x00010000;
const FLAC__fixedpoint FLAC__FP_LN2 = 45426;
const FLAC__fixedpoint FLAC__FP_E = 178145;

/* Lookup tables for Knuth's logarithm algorithm */
#define LOG2_LOOKUP_PRECISION 16
static const FLAC__uint32 log2_lookup[][LOG2_LOOKUP_PRECISION] = {
	{
		/*
		 * 0 fraction bits
		 */
		/* undefined */ 0x00000000,
		/* lg(2/1) = */ 0x00000001,
		/* lg(4/3) = */ 0x00000000,
		/* lg(8/7) = */ 0x00000000,
		/* lg(16/15) = */ 0x00000000,
		/* lg(32/31) = */ 0x00000000,
		/* lg(64/63) = */ 0x00000000,
		/* lg(128/127) = */ 0x00000000,
		/* lg(256/255) = */ 0x00000000,
		/* lg(512/511) = */ 0x00000000,
		/* lg(1024/1023) = */ 0x00000000,
		/* lg(2048/2047) = */ 0x00000000,
		/* lg(4096/4095) = */ 0x00000000,
		/* lg(8192/8191) = */ 0x00000000,
		/* lg(16384/16383) = */ 0x00000000,
		/* lg(32768/32767) = */ 0x00000000
	},
	{
		/*
		 * 4 fraction bits
		 */
		/* undefined */ 0x00000000,
		/* lg(2/1) = */ 0x00000010,
		/* lg(4/3) = */ 0x00000007,
		/* lg(8/7) = */ 0x00000003,
		/* lg(16/15) = */ 0x00000001,
		/* lg(32/31) = */ 0x00000001,
		/* lg(64/63) = */ 0x00000000,
		/* lg(128/127) = */ 0x00000000,
		/* lg(256/255) = */ 0x00000000,
		/* lg(512/511) = */ 0x00000000,
		/* lg(1024/1023) = */ 0x00000000,
		/* lg(2048/2047) = */ 0x00000000,
		/* lg(4096/4095) = */ 0x00000000,
		/* lg(8192/8191) = */ 0x00000000,
		/* lg(16384/16383) = */ 0x00000000,
		/* lg(32768/32767) = */ 0x00000000
	},
	{
		/*
		 * 8 fraction bits
		 */
		/* undefined */ 0x00000000,
		/* lg(2/1) = */ 0x00000100,
		/* lg(4/3) = */ 0x0000006a,
		/* lg(8/7) = */ 0x00000031,
		/* lg(16/15) = */ 0x00000018,
		/* lg(32/31) = */ 0x0000000c,
		/* lg(64/63) = */ 0x00000006,
		/* lg(128/127) = */ 0x00000003,
		/* lg(256/255) = */ 0x00000001,
		/* lg(512/511) = */ 0x00000001,
		/* lg(1024/1023) = */ 0x00000000,
		/* lg(2048/2047) = */ 0x00000000,
		/* lg(4096/4095) = */ 0x00000000,
		/* lg(8192/8191) = */ 0x00000000,
		/* lg(16384/16383) = */ 0x00000000,
		/* lg(32768/32767) = */ 0x00000000
	},
	{
		/*
		 * 12 fraction bits
		 */
		/* undefined */ 0x00000000,
		/* lg(2/1) = */ 0x00001000,
		/* lg(4/3) = */ 0x000006a4,
		/* lg(8/7) = */ 0x00000315,
		/* lg(16/15) = */ 0x0000017d,
		/* lg(32/31) = */ 0x000000bc,
		/* lg(64/63) = */ 0x0000005d,
		/* lg(128/127) = */ 0x0000002e,
		/* lg(256/255) = */ 0x00000017,
		/* lg(512/511) = */ 0x0000000c,
		/* lg(1024/1023) = */ 0x00000006,
		/* lg(2048/2047) = */ 0x00000003,
		/* lg(4096/4095) = */ 0x00000001,
		/* lg(8192/8191) = */ 0x00000001,
		/* lg(16384/16383) = */ 0x00000000,
		/* lg(32768/32767) = */ 0x00000000
	},
	{
		/*
		 * 16 fraction bits
		 */
		/* undefined */ 0x00000000,
		/* lg(2/1) = */ 0x00010000,
		/* lg(4/3) = */ 0x00006a40,
		/* lg(8/7) = */ 0x00003151,
		/* lg(16/15) = */ 0x000017d6,
		/* lg(32/31) = */ 0x00000bba,
		/* lg(64/63) = */ 0x000005d1,
		/* lg(128/127) = */ 0x000002e6,
		/* lg(256/255) = */ 0x00000172,
		/* lg(512/511) = */ 0x000000b9,
		/* lg(1024/1023) = */ 0x0000005c,
		/* lg(2048/2047) = */ 0x0000002e,
		/* lg(4096/4095) = */ 0x00000017,
		/* lg(8192/8191) = */ 0x0000000c,
		/* lg(16384/16383) = */ 0x00000006,
		/* lg(32768/32767) = */ 0x00000003
	},
	{
		/*
		 * 20 fraction bits
		 */
		/* undefined */ 0x00000000,
		/* lg(2/1) = */ 0x00100000,
		/* lg(4/3) = */ 0x0006a3fe,
		/* lg(8/7) = */ 0x00031513,
		/* lg(16/15) = */ 0x00017d60,
		/* lg(32/31) = */ 0x0000bb9d,
		/* lg(64/63) = */ 0x00005d10,
		/* lg(128/127) = */ 0x00002e59,
		/* lg(256/255) = */ 0x00001721,
		/* lg(512/511) = */ 0x00000b8e,
		/* lg(1024/1023) = */ 0x000005c6,
		/* lg(2048/2047) = */ 0x000002e3,
		/* lg(4096/4095) = */ 0x00000171,
		/* lg(8192/8191) = */ 0x000000b9,
		/* lg(16384/16383) = */ 0x0000005c,
		/* lg(32768/32767) = */ 0x0000002e
	},
	{
		/*
		 * 24 fraction bits
		 */
		/* undefined */ 0x00000000,
		/* lg(2/1) = */ 0x01000000,
		/* lg(4/3) = */ 0x006a3fe6,
		/* lg(8/7) = */ 0x00315130,
		/* lg(16/15) = */ 0x0017d605,
		/* lg(32/31) = */ 0x000bb9ca,
		/* lg(64/63) = */ 0x0005d0fc,
		/* lg(128/127) = */ 0x0002e58f,
		/* lg(256/255) = */ 0x0001720e,
		/* lg(512/511) = */ 0x0000b8d8,
		/* lg(1024/1023) = */ 0x00005c61,
		/* lg(2048/2047) = */ 0x00002e2d,
		/* lg(4096/4095) = */ 0x00001716,
		/* lg(8192/8191) = */ 0x00000b8b,
		/* lg(16384/16383) = */ 0x000005c5,
		/* lg(32768/32767) = */ 0x000002e3
	},
	{
		/*
		 * 28 fraction bits
		 */
		/* undefined */ 0x00000000,
		/* lg(2/1) = */ 0x10000000,
		/* lg(4/3) = */ 0x06a3fe5c,
		/* lg(8/7) = */ 0x03151301,
		/* lg(16/15) = */ 0x017d6049,
		/* lg(32/31) = */ 0x00bb9ca6,
		/* lg(64/63) = */ 0x005d0fba,
		/* lg(128/127) = */ 0x002e58f7,
		/* lg(256/255) = */ 0x001720da,
		/* lg(512/511) = */ 0x000b8d87,
		/* lg(1024/1023) = */ 0x0005c60b,
		/* lg(2048/2047) = */ 0x0002e2d7,
		/* lg(4096/4095) = */ 0x00017160,
		/* lg(8192/8191) = */ 0x0000b8ad,
		/* lg(16384/16383) = */ 0x00005c56,
		/* lg(32768/32767) = */ 0x00002e2b
	}
};

#if 0
static const FLAC__uint64 log2_lookup_wide[] = {
	{
		/*
		 * 32 fraction bits
		 */
		/* undefined */ 0x00000000,
		/* lg(2/1) = */ FLAC__U64L(0x100000000),
		/* lg(4/3) = */ FLAC__U64L(0x6a3fe5c6),
		/* lg(8/7) = */ FLAC__U64L(0x31513015),
		/* lg(16/15) = */ FLAC__U64L(0x17d60497),
		/* lg(32/31) = */ FLAC__U64L(0x0bb9ca65),
		/* lg(64/63) = */ FLAC__U64L(0x05d0fba2),
		/* lg(128/127) = */ FLAC__U64L(0x02e58f74),
		/* lg(256/255) = */ FLAC__U64L(0x01720d9c),
		/* lg(512/511) = */ FLAC__U64L(0x00b8d875),
		/* lg(1024/1023) = */ FLAC__U64L(0x005c60aa),
		/* lg(2048/2047) = */ FLAC__U64L(0x002e2d72),
		/* lg(4096/4095) = */ FLAC__U64L(0x00171600),
		/* lg(8192/8191) = */ FLAC__U64L(0x000b8ad2),
		/* lg(16384/16383) = */ FLAC__U64L(0x0005c55d),
		/* lg(32768/32767) = */ FLAC__U64L(0x0002e2ac)
	},
	{
		/*
		 * 48 fraction bits
		 */
		/* undefined */ 0x00000000,
		/* lg(2/1) = */ FLAC__U64L(0x1000000000000),
		/* lg(4/3) = */ FLAC__U64L(0x6a3fe5c60429),
		/* lg(8/7) = */ FLAC__U64L(0x315130157f7a),
		/* lg(16/15) = */ FLAC__U64L(0x17d60496cfbb),
		/* lg(32/31) = */ FLAC__U64L(0xbb9ca64ecac),
		/* lg(64/63) = */ FLAC__U64L(0x5d0fba187cd),
		/* lg(128/127) = */ FLAC__U64L(0x2e58f7441ee),
		/* lg(256/255) = */ FLAC__U64L(0x1720d9c06a8),
		/* lg(512/511) = */ FLAC__U64L(0xb8d8752173),
		/* lg(1024/1023) = */ FLAC__U64L(0x5c60aa252e),
		/* lg(2048/2047) = */ FLAC__U64L(0x2e2d71b0d8),
		/* lg(4096/4095) = */ FLAC__U64L(0x1716001719),
		/* lg(8192/8191) = */ FLAC__U64L(0xb8ad1de1b),
		/* lg(16384/16383) = */ FLAC__U64L(0x5c55d640d),
		/* lg(32768/32767) = */ FLAC__U64L(0x2e2abcf52)
	}
};
#endif

FLAC__uint32 FLAC__fixedpoint_log2(FLAC__uint32 x, uint32_t fracbits, uint32_t precision)
{
	const FLAC__uint32 ONE = (1u << fracbits);
	const FLAC__uint32 *table = log2_lookup[fracbits >> 2];

	FLAC__ASSERT(fracbits < 32);
	FLAC__ASSERT((fracbits & 0x3) == 0);

	if(x < ONE)
		return 0;

	if(precision > LOG2_LOOKUP_PRECISION)
		precision = LOG2_LOOKUP_PRECISION;

	/* Knuth's algorithm for computing logarithms, optimized for base-2 with lookup tables */
	{
		FLAC__uint32 y = 0;
		FLAC__uint32 z = x >> 1, k = 1;
		while (x > ONE && k < precision) {
			if (x - z >= ONE) {
				x -= z;
				z = x >> k;
				y += table[k];
			}
			else {
				z >>= 1;
				k++;
			}
		}
		return y;
	}
}

#endif /* defined FLAC__INTEGER_ONLY_LIBRARY */
