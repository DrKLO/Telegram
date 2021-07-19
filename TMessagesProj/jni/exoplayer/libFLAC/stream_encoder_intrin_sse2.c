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

#ifdef HAVE_CONFIG_H
#  include <config.h>
#endif

#include "private/cpu.h"

#ifndef FLAC__NO_ASM
#if (defined FLAC__CPU_IA32 || defined FLAC__CPU_X86_64) && FLAC__HAS_X86INTRIN
#include "private/stream_encoder.h"
#include "private/bitmath.h"
#ifdef FLAC__SSE2_SUPPORTED

#include <stdlib.h>    /* for abs() */
#include <emmintrin.h> /* SSE2 */
#include "FLAC/assert.h"
#include "share/compat.h"

FLAC__SSE_TARGET("sse2")
static inline __m128i local_abs_epi32(__m128i val)
{
	__m128i mask = _mm_srai_epi32(val, 31);
	val = _mm_xor_si128(val, mask);
	val = _mm_sub_epi32(val, mask);
	return val;
}


FLAC__SSE_TARGET("sse2")
void FLAC__precompute_partition_info_sums_intrin_sse2(const FLAC__int32 residual[], FLAC__uint64 abs_residual_partition_sums[],
		uint32_t residual_samples, uint32_t predictor_order, uint32_t min_partition_order, uint32_t max_partition_order, uint32_t bps)
{
	const uint32_t default_partition_samples = (residual_samples + predictor_order) >> max_partition_order;
	uint32_t partitions = 1u << max_partition_order;

	FLAC__ASSERT(default_partition_samples > predictor_order);

	/* first do max_partition_order */
	{
		const uint32_t threshold = 32 - FLAC__bitmath_ilog2(default_partition_samples);
		uint32_t partition, residual_sample, end = (uint32_t)(-(int32_t)predictor_order);

		if(bps + FLAC__MAX_EXTRA_RESIDUAL_BPS < threshold) {
			for(partition = residual_sample = 0; partition < partitions; partition++) {
				__m128i mm_sum = _mm_setzero_si128();
				uint32_t e1, e3;
				end += default_partition_samples;

				e1 = (residual_sample + 3) & ~3; e3 = end & ~3;
				if(e1 > end)
					e1 = end; /* try flac -l 1 -b 16 and you'll be here */

				/* assumption: residual[] is properly aligned so (residual + e1) is properly aligned too and _mm_loadu_si128() is fast */
				for( ; residual_sample < e1; residual_sample++) {
					__m128i mm_res = local_abs_epi32(_mm_cvtsi32_si128(residual[residual_sample]));
					mm_sum = _mm_add_epi32(mm_sum, mm_res);
				}

				for( ; residual_sample < e3; residual_sample+=4) {
					__m128i mm_res = local_abs_epi32(_mm_loadu_si128((const __m128i*)(residual+residual_sample)));
					mm_sum = _mm_add_epi32(mm_sum, mm_res);
				}

				for( ; residual_sample < end; residual_sample++) {
					__m128i mm_res = local_abs_epi32(_mm_cvtsi32_si128(residual[residual_sample]));
					mm_sum = _mm_add_epi32(mm_sum, mm_res);
				}

				mm_sum = _mm_add_epi32(mm_sum, _mm_srli_si128(mm_sum, 8));
				mm_sum = _mm_add_epi32(mm_sum, _mm_srli_si128(mm_sum, 4));
				abs_residual_partition_sums[partition] = (FLAC__uint32)_mm_cvtsi128_si32(mm_sum);
/* workaround for a bug in MSVC2015U2 - see https://connect.microsoft.com/VisualStudio/feedback/details/2659191/incorrect-code-generation-for-x86-64 */
#if (defined _MSC_VER) && (_MSC_FULL_VER == 190023918) && (defined FLAC__CPU_X86_64)
				abs_residual_partition_sums[partition] &= 0xFFFFFFFF;
#endif
			}
		}
		else { /* have to pessimistically use 64 bits for accumulator */
			for(partition = residual_sample = 0; partition < partitions; partition++) {
				__m128i mm_sum = _mm_setzero_si128();
				uint32_t e1, e3;
				end += default_partition_samples;

				e1 = (residual_sample + 1) & ~1; e3 = end & ~1;
				FLAC__ASSERT(e1 <= end);

				for( ; residual_sample < e1; residual_sample++) {
					__m128i mm_res = local_abs_epi32(_mm_cvtsi32_si128(residual[residual_sample])); /*  0   0   0  |r0|  ==   00   |r0_64| */
					mm_sum = _mm_add_epi64(mm_sum, mm_res);
				}

				for( ; residual_sample < e3; residual_sample+=2) {
					__m128i mm_res = local_abs_epi32(_mm_loadl_epi64((const __m128i*)(residual+residual_sample))); /*  0   0  |r1|   |r0| */
					mm_res = _mm_shuffle_epi32(mm_res, _MM_SHUFFLE(3,1,2,0)); /* 0  |r1|  0  |r0|  ==  |r1_64|  |r0_64|  */
					mm_sum = _mm_add_epi64(mm_sum, mm_res);
				}

				for( ; residual_sample < end; residual_sample++) {
					__m128i mm_res = local_abs_epi32(_mm_cvtsi32_si128(residual[residual_sample]));
					mm_sum = _mm_add_epi64(mm_sum, mm_res);
				}

				mm_sum = _mm_add_epi64(mm_sum, _mm_srli_si128(mm_sum, 8));
				_mm_storel_epi64((__m128i*)(abs_residual_partition_sums+partition), mm_sum);
			}
		}
	}

	/* now merge partitions for lower orders */
	{
		uint32_t from_partition = 0, to_partition = partitions;
		int partition_order;
		for(partition_order = (int)max_partition_order - 1; partition_order >= (int)min_partition_order; partition_order--) {
			uint32_t i;
			partitions >>= 1;
			for(i = 0; i < partitions; i++) {
				abs_residual_partition_sums[to_partition++] =
					abs_residual_partition_sums[from_partition  ] +
					abs_residual_partition_sums[from_partition+1];
				from_partition += 2;
			}
		}
	}
}

#endif /* FLAC__SSE2_SUPPORTED */
#endif /* (FLAC__CPU_IA32 || FLAC__CPU_X86_64) && FLAC__HAS_X86INTRIN */
#endif /* FLAC__NO_ASM */
