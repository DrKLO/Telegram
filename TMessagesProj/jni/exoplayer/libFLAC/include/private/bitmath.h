/* libFLAC - Free Lossless Audio Codec library
 * Copyright (C) 2001-2009  Josh Coalson
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

#ifndef FLAC__PRIVATE__BITMATH_H
#define FLAC__PRIVATE__BITMATH_H

#include "FLAC/ordinals.h"
#include "FLAC/assert.h"

#include "share/compat.h"

#if defined(_MSC_VER)
#include <intrin.h> /* for _BitScanReverse* */
#endif

/* Will never be emitted for MSVC, GCC, Intel compilers */
static inline uint32_t FLAC__clz_soft_uint32(FLAC__uint32 word)
{
	static const uint8_t byte_to_unary_table[] = {
	8, 7, 6, 6, 5, 5, 5, 5, 4, 4, 4, 4, 4, 4, 4, 4,
	3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3,
	2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
	2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
	1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
	1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
	1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
	1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
	0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
	0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
	0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
	0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
	0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
	0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
	0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
	0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
	};

	return word > 0xffffff ? byte_to_unary_table[word >> 24] :
		word > 0xffff ? byte_to_unary_table[word >> 16] + 8 :
		word > 0xff ? byte_to_unary_table[word >> 8] + 16 :
		byte_to_unary_table[word] + 24;
}

static inline uint32_t FLAC__clz_uint32(FLAC__uint32 v)
{
/* Never used with input 0 */
	FLAC__ASSERT(v > 0);
#if defined(__INTEL_COMPILER)
	return _bit_scan_reverse(v) ^ 31U;
#elif defined(__GNUC__) && (__GNUC__ >= 4 || (__GNUC__ == 3 && __GNUC_MINOR__ >= 4))
/* This will translate either to (bsr ^ 31U), clz , ctlz, cntlz, lzcnt depending on
 * -march= setting or to a software routine in exotic machines. */
	return __builtin_clz(v);
#elif defined(_MSC_VER)
	{
		uint32_t idx;
		_BitScanReverse(&idx, v);
		return idx ^ 31U;
	}
#else
	return FLAC__clz_soft_uint32(v);
#endif
}

/* Used when 64-bit bsr/clz is unavailable; can use 32-bit bsr/clz when possible */
static inline uint32_t FLAC__clz_soft_uint64(FLAC__uint64 word)
{
	return (FLAC__uint32)(word>>32) ? FLAC__clz_uint32((FLAC__uint32)(word>>32)) :
		FLAC__clz_uint32((FLAC__uint32)word) + 32;
}

static inline uint32_t FLAC__clz_uint64(FLAC__uint64 v)
{
	/* Never used with input 0 */
	FLAC__ASSERT(v > 0);
#if defined(__GNUC__) && (__GNUC__ >= 4 || (__GNUC__ == 3 && __GNUC_MINOR__ >= 4))
	return __builtin_clzll(v);
#elif (defined(__INTEL_COMPILER) || defined(_MSC_VER)) && (defined(_M_IA64) || defined(_M_X64))
	{
		uint32_t idx;
		_BitScanReverse64(&idx, v);
		return idx ^ 63U;
	}
#else
	return FLAC__clz_soft_uint64(v);
#endif
}

/* These two functions work with input 0 */
static inline uint32_t FLAC__clz2_uint32(FLAC__uint32 v)
{
	if (!v)
		return 32;
	return FLAC__clz_uint32(v);
}

static inline uint32_t FLAC__clz2_uint64(FLAC__uint64 v)
{
	if (!v)
		return 64;
	return FLAC__clz_uint64(v);
}

/* An example of what FLAC__bitmath_ilog2() computes:
 *
 * ilog2( 0) = assertion failure
 * ilog2( 1) = 0
 * ilog2( 2) = 1
 * ilog2( 3) = 1
 * ilog2( 4) = 2
 * ilog2( 5) = 2
 * ilog2( 6) = 2
 * ilog2( 7) = 2
 * ilog2( 8) = 3
 * ilog2( 9) = 3
 * ilog2(10) = 3
 * ilog2(11) = 3
 * ilog2(12) = 3
 * ilog2(13) = 3
 * ilog2(14) = 3
 * ilog2(15) = 3
 * ilog2(16) = 4
 * ilog2(17) = 4
 * ilog2(18) = 4
 */

static inline uint32_t FLAC__bitmath_ilog2(FLAC__uint32 v)
{
	FLAC__ASSERT(v > 0);
#if defined(__INTEL_COMPILER)
	return _bit_scan_reverse(v);
#elif defined(_MSC_VER)
	{
		uint32_t idx;
		_BitScanReverse(&idx, v);
		return idx;
	}
#else
	return FLAC__clz_uint32(v) ^ 31U;
#endif
}

static inline uint32_t FLAC__bitmath_ilog2_wide(FLAC__uint64 v)
{
	FLAC__ASSERT(v > 0);
#if defined(__GNUC__) && (__GNUC__ >= 4 || (__GNUC__ == 3 && __GNUC_MINOR__ >= 4))
	return __builtin_clzll(v) ^ 63U;
/* Sorry, only supported in x64/Itanium.. and both have fast FPU which makes integer-only encoder pointless */
#elif (defined(__INTEL_COMPILER) || defined(_MSC_VER)) && (defined(_M_IA64) || defined(_M_X64))
	{
		uint32_t idx;
		_BitScanReverse64(&idx, v);
		return idx;
	}
#else
/*  Brain-damaged compilers will use the fastest possible way that is,
	de Bruijn sequences (http://supertech.csail.mit.edu/papers/debruijn.pdf)
	(C) Timothy B. Terriberry (tterribe@xiph.org) 2001-2009 CC0 (Public domain).
*/
	{
		static const uint8_t DEBRUIJN_IDX64[64]={
			0, 1, 2, 7, 3,13, 8,19, 4,25,14,28, 9,34,20,40,
			5,17,26,38,15,46,29,48,10,31,35,54,21,50,41,57,
			63, 6,12,18,24,27,33,39,16,37,45,47,30,53,49,56,
			62,11,23,32,36,44,52,55,61,22,43,51,60,42,59,58
		};
		v|= v>>1;
		v|= v>>2;
		v|= v>>4;
		v|= v>>8;
		v|= v>>16;
		v|= v>>32;
		v= (v>>1)+1;
		return DEBRUIJN_IDX64[v*FLAC__U64L(0x218A392CD3D5DBF)>>58&0x3F];
	}
#endif
}

uint32_t FLAC__bitmath_silog2(FLAC__int64 v);

#endif
