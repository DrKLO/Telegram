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

#ifndef FLAC__INTEGER_ONLY_LIBRARY
#ifndef FLAC__NO_ASM
#if (defined FLAC__CPU_IA32 || defined FLAC__CPU_X86_64) && FLAC__HAS_X86INTRIN
#include "private/lpc.h"
#ifdef FLAC__SSE2_SUPPORTED

#include "FLAC/assert.h"
#include "FLAC/format.h"

#include <emmintrin.h> /* SSE2 */

#define RESIDUAL32_RESULT(xmmN) residual[i] = data[i] - (_mm_cvtsi128_si32(xmmN) >> lp_quantization);
#define     DATA32_RESULT(xmmN) data[i] = residual[i] + (_mm_cvtsi128_si32(xmmN) >> lp_quantization);

FLAC__SSE_TARGET("sse2")
void FLAC__lpc_compute_residual_from_qlp_coefficients_16_intrin_sse2(const FLAC__int32 *data, uint32_t data_len, const FLAC__int32 qlp_coeff[], uint32_t order, int lp_quantization, FLAC__int32 residual[])
{
	int i;
	FLAC__int32 sum;
	const __m128i cnt = _mm_cvtsi32_si128(lp_quantization);

	FLAC__ASSERT(order > 0);
	FLAC__ASSERT(order <= 32);

	if(order <= 12) {
		if(order > 8) {
			if(order > 10) {
				if(order == 12) {
					__m128i q0, q1, q2, q3, q4, q5, q6, q7, q8, q9, q10, q11;
					q0 = _mm_cvtsi32_si128(0xffff & qlp_coeff[0]); q0 = _mm_shuffle_epi32(q0, _MM_SHUFFLE(0,0,0,0));
					q1 = _mm_cvtsi32_si128(0xffff & qlp_coeff[1]); q1 = _mm_shuffle_epi32(q1, _MM_SHUFFLE(0,0,0,0));
					q2 = _mm_cvtsi32_si128(0xffff & qlp_coeff[2]); q2 = _mm_shuffle_epi32(q2, _MM_SHUFFLE(0,0,0,0));
					q3 = _mm_cvtsi32_si128(0xffff & qlp_coeff[3]); q3 = _mm_shuffle_epi32(q3, _MM_SHUFFLE(0,0,0,0));
					q4 = _mm_cvtsi32_si128(0xffff & qlp_coeff[4]); q4 = _mm_shuffle_epi32(q4, _MM_SHUFFLE(0,0,0,0));
					q5 = _mm_cvtsi32_si128(0xffff & qlp_coeff[5]); q5 = _mm_shuffle_epi32(q5, _MM_SHUFFLE(0,0,0,0));
					q6 = _mm_cvtsi32_si128(0xffff & qlp_coeff[6]); q6 = _mm_shuffle_epi32(q6, _MM_SHUFFLE(0,0,0,0));
					q7 = _mm_cvtsi32_si128(0xffff & qlp_coeff[7]); q7 = _mm_shuffle_epi32(q7, _MM_SHUFFLE(0,0,0,0));
					q8 = _mm_cvtsi32_si128(0xffff & qlp_coeff[8]); q8 = _mm_shuffle_epi32(q8, _MM_SHUFFLE(0,0,0,0));
					q9 = _mm_cvtsi32_si128(0xffff & qlp_coeff[9]); q9 = _mm_shuffle_epi32(q9, _MM_SHUFFLE(0,0,0,0));
					q10 = _mm_cvtsi32_si128(0xffff & qlp_coeff[10]); q10 = _mm_shuffle_epi32(q10, _MM_SHUFFLE(0,0,0,0));
					q11 = _mm_cvtsi32_si128(0xffff & qlp_coeff[11]); q11 = _mm_shuffle_epi32(q11, _MM_SHUFFLE(0,0,0,0));

					for(i = 0; i < (int)data_len-3; i+=4) {
						__m128i summ, mull;
						summ = _mm_madd_epi16(q11, _mm_loadu_si128((const __m128i*)(data+i-12)));
						mull = _mm_madd_epi16(q10, _mm_loadu_si128((const __m128i*)(data+i-11))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q9, _mm_loadu_si128((const __m128i*)(data+i-10))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q8, _mm_loadu_si128((const __m128i*)(data+i-9))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q7, _mm_loadu_si128((const __m128i*)(data+i-8))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q6, _mm_loadu_si128((const __m128i*)(data+i-7))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q5, _mm_loadu_si128((const __m128i*)(data+i-6))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q4, _mm_loadu_si128((const __m128i*)(data+i-5))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q3, _mm_loadu_si128((const __m128i*)(data+i-4))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q2, _mm_loadu_si128((const __m128i*)(data+i-3))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q1, _mm_loadu_si128((const __m128i*)(data+i-2))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q0, _mm_loadu_si128((const __m128i*)(data+i-1))); summ = _mm_add_epi32(summ, mull);
						summ = _mm_sra_epi32(summ, cnt);
						_mm_storeu_si128((__m128i*)(residual+i), _mm_sub_epi32(_mm_loadu_si128((const __m128i*)(data+i)), summ));
					}
				}
				else { /* order == 11 */
					__m128i q0, q1, q2, q3, q4, q5, q6, q7, q8, q9, q10;
					q0 = _mm_cvtsi32_si128(0xffff & qlp_coeff[0]); q0 = _mm_shuffle_epi32(q0, _MM_SHUFFLE(0,0,0,0));
					q1 = _mm_cvtsi32_si128(0xffff & qlp_coeff[1]); q1 = _mm_shuffle_epi32(q1, _MM_SHUFFLE(0,0,0,0));
					q2 = _mm_cvtsi32_si128(0xffff & qlp_coeff[2]); q2 = _mm_shuffle_epi32(q2, _MM_SHUFFLE(0,0,0,0));
					q3 = _mm_cvtsi32_si128(0xffff & qlp_coeff[3]); q3 = _mm_shuffle_epi32(q3, _MM_SHUFFLE(0,0,0,0));
					q4 = _mm_cvtsi32_si128(0xffff & qlp_coeff[4]); q4 = _mm_shuffle_epi32(q4, _MM_SHUFFLE(0,0,0,0));
					q5 = _mm_cvtsi32_si128(0xffff & qlp_coeff[5]); q5 = _mm_shuffle_epi32(q5, _MM_SHUFFLE(0,0,0,0));
					q6 = _mm_cvtsi32_si128(0xffff & qlp_coeff[6]); q6 = _mm_shuffle_epi32(q6, _MM_SHUFFLE(0,0,0,0));
					q7 = _mm_cvtsi32_si128(0xffff & qlp_coeff[7]); q7 = _mm_shuffle_epi32(q7, _MM_SHUFFLE(0,0,0,0));
					q8 = _mm_cvtsi32_si128(0xffff & qlp_coeff[8]); q8 = _mm_shuffle_epi32(q8, _MM_SHUFFLE(0,0,0,0));
					q9 = _mm_cvtsi32_si128(0xffff & qlp_coeff[9]); q9 = _mm_shuffle_epi32(q9, _MM_SHUFFLE(0,0,0,0));
					q10 = _mm_cvtsi32_si128(0xffff & qlp_coeff[10]); q10 = _mm_shuffle_epi32(q10, _MM_SHUFFLE(0,0,0,0));

					for(i = 0; i < (int)data_len-3; i+=4) {
						__m128i summ, mull;
						summ = _mm_madd_epi16(q10, _mm_loadu_si128((const __m128i*)(data+i-11)));
						mull = _mm_madd_epi16(q9, _mm_loadu_si128((const __m128i*)(data+i-10))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q8, _mm_loadu_si128((const __m128i*)(data+i-9))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q7, _mm_loadu_si128((const __m128i*)(data+i-8))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q6, _mm_loadu_si128((const __m128i*)(data+i-7))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q5, _mm_loadu_si128((const __m128i*)(data+i-6))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q4, _mm_loadu_si128((const __m128i*)(data+i-5))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q3, _mm_loadu_si128((const __m128i*)(data+i-4))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q2, _mm_loadu_si128((const __m128i*)(data+i-3))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q1, _mm_loadu_si128((const __m128i*)(data+i-2))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q0, _mm_loadu_si128((const __m128i*)(data+i-1))); summ = _mm_add_epi32(summ, mull);
						summ = _mm_sra_epi32(summ, cnt);
						_mm_storeu_si128((__m128i*)(residual+i), _mm_sub_epi32(_mm_loadu_si128((const __m128i*)(data+i)), summ));
					}
				}
			}
			else {
				if(order == 10) {
					__m128i q0, q1, q2, q3, q4, q5, q6, q7, q8, q9;
					q0 = _mm_cvtsi32_si128(0xffff & qlp_coeff[0]); q0 = _mm_shuffle_epi32(q0, _MM_SHUFFLE(0,0,0,0));
					q1 = _mm_cvtsi32_si128(0xffff & qlp_coeff[1]); q1 = _mm_shuffle_epi32(q1, _MM_SHUFFLE(0,0,0,0));
					q2 = _mm_cvtsi32_si128(0xffff & qlp_coeff[2]); q2 = _mm_shuffle_epi32(q2, _MM_SHUFFLE(0,0,0,0));
					q3 = _mm_cvtsi32_si128(0xffff & qlp_coeff[3]); q3 = _mm_shuffle_epi32(q3, _MM_SHUFFLE(0,0,0,0));
					q4 = _mm_cvtsi32_si128(0xffff & qlp_coeff[4]); q4 = _mm_shuffle_epi32(q4, _MM_SHUFFLE(0,0,0,0));
					q5 = _mm_cvtsi32_si128(0xffff & qlp_coeff[5]); q5 = _mm_shuffle_epi32(q5, _MM_SHUFFLE(0,0,0,0));
					q6 = _mm_cvtsi32_si128(0xffff & qlp_coeff[6]); q6 = _mm_shuffle_epi32(q6, _MM_SHUFFLE(0,0,0,0));
					q7 = _mm_cvtsi32_si128(0xffff & qlp_coeff[7]); q7 = _mm_shuffle_epi32(q7, _MM_SHUFFLE(0,0,0,0));
					q8 = _mm_cvtsi32_si128(0xffff & qlp_coeff[8]); q8 = _mm_shuffle_epi32(q8, _MM_SHUFFLE(0,0,0,0));
					q9 = _mm_cvtsi32_si128(0xffff & qlp_coeff[9]); q9 = _mm_shuffle_epi32(q9, _MM_SHUFFLE(0,0,0,0));

					for(i = 0; i < (int)data_len-3; i+=4) {
						__m128i summ, mull;
						summ = _mm_madd_epi16(q9, _mm_loadu_si128((const __m128i*)(data+i-10)));
						mull = _mm_madd_epi16(q8, _mm_loadu_si128((const __m128i*)(data+i-9))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q7, _mm_loadu_si128((const __m128i*)(data+i-8))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q6, _mm_loadu_si128((const __m128i*)(data+i-7))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q5, _mm_loadu_si128((const __m128i*)(data+i-6))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q4, _mm_loadu_si128((const __m128i*)(data+i-5))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q3, _mm_loadu_si128((const __m128i*)(data+i-4))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q2, _mm_loadu_si128((const __m128i*)(data+i-3))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q1, _mm_loadu_si128((const __m128i*)(data+i-2))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q0, _mm_loadu_si128((const __m128i*)(data+i-1))); summ = _mm_add_epi32(summ, mull);
						summ = _mm_sra_epi32(summ, cnt);
						_mm_storeu_si128((__m128i*)(residual+i), _mm_sub_epi32(_mm_loadu_si128((const __m128i*)(data+i)), summ));
					}
				}
				else { /* order == 9 */
					__m128i q0, q1, q2, q3, q4, q5, q6, q7, q8;
					q0 = _mm_cvtsi32_si128(0xffff & qlp_coeff[0]); q0 = _mm_shuffle_epi32(q0, _MM_SHUFFLE(0,0,0,0));
					q1 = _mm_cvtsi32_si128(0xffff & qlp_coeff[1]); q1 = _mm_shuffle_epi32(q1, _MM_SHUFFLE(0,0,0,0));
					q2 = _mm_cvtsi32_si128(0xffff & qlp_coeff[2]); q2 = _mm_shuffle_epi32(q2, _MM_SHUFFLE(0,0,0,0));
					q3 = _mm_cvtsi32_si128(0xffff & qlp_coeff[3]); q3 = _mm_shuffle_epi32(q3, _MM_SHUFFLE(0,0,0,0));
					q4 = _mm_cvtsi32_si128(0xffff & qlp_coeff[4]); q4 = _mm_shuffle_epi32(q4, _MM_SHUFFLE(0,0,0,0));
					q5 = _mm_cvtsi32_si128(0xffff & qlp_coeff[5]); q5 = _mm_shuffle_epi32(q5, _MM_SHUFFLE(0,0,0,0));
					q6 = _mm_cvtsi32_si128(0xffff & qlp_coeff[6]); q6 = _mm_shuffle_epi32(q6, _MM_SHUFFLE(0,0,0,0));
					q7 = _mm_cvtsi32_si128(0xffff & qlp_coeff[7]); q7 = _mm_shuffle_epi32(q7, _MM_SHUFFLE(0,0,0,0));
					q8 = _mm_cvtsi32_si128(0xffff & qlp_coeff[8]); q8 = _mm_shuffle_epi32(q8, _MM_SHUFFLE(0,0,0,0));

					for(i = 0; i < (int)data_len-3; i+=4) {
						__m128i summ, mull;
						summ = _mm_madd_epi16(q8, _mm_loadu_si128((const __m128i*)(data+i-9)));
						mull = _mm_madd_epi16(q7, _mm_loadu_si128((const __m128i*)(data+i-8))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q6, _mm_loadu_si128((const __m128i*)(data+i-7))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q5, _mm_loadu_si128((const __m128i*)(data+i-6))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q4, _mm_loadu_si128((const __m128i*)(data+i-5))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q3, _mm_loadu_si128((const __m128i*)(data+i-4))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q2, _mm_loadu_si128((const __m128i*)(data+i-3))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q1, _mm_loadu_si128((const __m128i*)(data+i-2))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q0, _mm_loadu_si128((const __m128i*)(data+i-1))); summ = _mm_add_epi32(summ, mull);
						summ = _mm_sra_epi32(summ, cnt);
						_mm_storeu_si128((__m128i*)(residual+i), _mm_sub_epi32(_mm_loadu_si128((const __m128i*)(data+i)), summ));
					}
				}
			}
		}
		else if(order > 4) {
			if(order > 6) {
				if(order == 8) {
					__m128i q0, q1, q2, q3, q4, q5, q6, q7;
					q0 = _mm_cvtsi32_si128(0xffff & qlp_coeff[0]); q0 = _mm_shuffle_epi32(q0, _MM_SHUFFLE(0,0,0,0));
					q1 = _mm_cvtsi32_si128(0xffff & qlp_coeff[1]); q1 = _mm_shuffle_epi32(q1, _MM_SHUFFLE(0,0,0,0));
					q2 = _mm_cvtsi32_si128(0xffff & qlp_coeff[2]); q2 = _mm_shuffle_epi32(q2, _MM_SHUFFLE(0,0,0,0));
					q3 = _mm_cvtsi32_si128(0xffff & qlp_coeff[3]); q3 = _mm_shuffle_epi32(q3, _MM_SHUFFLE(0,0,0,0));
					q4 = _mm_cvtsi32_si128(0xffff & qlp_coeff[4]); q4 = _mm_shuffle_epi32(q4, _MM_SHUFFLE(0,0,0,0));
					q5 = _mm_cvtsi32_si128(0xffff & qlp_coeff[5]); q5 = _mm_shuffle_epi32(q5, _MM_SHUFFLE(0,0,0,0));
					q6 = _mm_cvtsi32_si128(0xffff & qlp_coeff[6]); q6 = _mm_shuffle_epi32(q6, _MM_SHUFFLE(0,0,0,0));
					q7 = _mm_cvtsi32_si128(0xffff & qlp_coeff[7]); q7 = _mm_shuffle_epi32(q7, _MM_SHUFFLE(0,0,0,0));

					for(i = 0; i < (int)data_len-3; i+=4) {
						__m128i summ, mull;
						summ = _mm_madd_epi16(q7, _mm_loadu_si128((const __m128i*)(data+i-8)));
						mull = _mm_madd_epi16(q6, _mm_loadu_si128((const __m128i*)(data+i-7))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q5, _mm_loadu_si128((const __m128i*)(data+i-6))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q4, _mm_loadu_si128((const __m128i*)(data+i-5))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q3, _mm_loadu_si128((const __m128i*)(data+i-4))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q2, _mm_loadu_si128((const __m128i*)(data+i-3))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q1, _mm_loadu_si128((const __m128i*)(data+i-2))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q0, _mm_loadu_si128((const __m128i*)(data+i-1))); summ = _mm_add_epi32(summ, mull);
						summ = _mm_sra_epi32(summ, cnt);
						_mm_storeu_si128((__m128i*)(residual+i), _mm_sub_epi32(_mm_loadu_si128((const __m128i*)(data+i)), summ));
					}
				}
				else { /* order == 7 */
					__m128i q0, q1, q2, q3, q4, q5, q6;
					q0 = _mm_cvtsi32_si128(0xffff & qlp_coeff[0]); q0 = _mm_shuffle_epi32(q0, _MM_SHUFFLE(0,0,0,0));
					q1 = _mm_cvtsi32_si128(0xffff & qlp_coeff[1]); q1 = _mm_shuffle_epi32(q1, _MM_SHUFFLE(0,0,0,0));
					q2 = _mm_cvtsi32_si128(0xffff & qlp_coeff[2]); q2 = _mm_shuffle_epi32(q2, _MM_SHUFFLE(0,0,0,0));
					q3 = _mm_cvtsi32_si128(0xffff & qlp_coeff[3]); q3 = _mm_shuffle_epi32(q3, _MM_SHUFFLE(0,0,0,0));
					q4 = _mm_cvtsi32_si128(0xffff & qlp_coeff[4]); q4 = _mm_shuffle_epi32(q4, _MM_SHUFFLE(0,0,0,0));
					q5 = _mm_cvtsi32_si128(0xffff & qlp_coeff[5]); q5 = _mm_shuffle_epi32(q5, _MM_SHUFFLE(0,0,0,0));
					q6 = _mm_cvtsi32_si128(0xffff & qlp_coeff[6]); q6 = _mm_shuffle_epi32(q6, _MM_SHUFFLE(0,0,0,0));

					for(i = 0; i < (int)data_len-3; i+=4) {
						__m128i summ, mull;
						summ = _mm_madd_epi16(q6, _mm_loadu_si128((const __m128i*)(data+i-7)));
						mull = _mm_madd_epi16(q5, _mm_loadu_si128((const __m128i*)(data+i-6))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q4, _mm_loadu_si128((const __m128i*)(data+i-5))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q3, _mm_loadu_si128((const __m128i*)(data+i-4))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q2, _mm_loadu_si128((const __m128i*)(data+i-3))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q1, _mm_loadu_si128((const __m128i*)(data+i-2))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q0, _mm_loadu_si128((const __m128i*)(data+i-1))); summ = _mm_add_epi32(summ, mull);
						summ = _mm_sra_epi32(summ, cnt);
						_mm_storeu_si128((__m128i*)(residual+i), _mm_sub_epi32(_mm_loadu_si128((const __m128i*)(data+i)), summ));
					}
				}
			}
			else {
				if(order == 6) {
					__m128i q0, q1, q2, q3, q4, q5;
					q0 = _mm_cvtsi32_si128(0xffff & qlp_coeff[0]); q0 = _mm_shuffle_epi32(q0, _MM_SHUFFLE(0,0,0,0));
					q1 = _mm_cvtsi32_si128(0xffff & qlp_coeff[1]); q1 = _mm_shuffle_epi32(q1, _MM_SHUFFLE(0,0,0,0));
					q2 = _mm_cvtsi32_si128(0xffff & qlp_coeff[2]); q2 = _mm_shuffle_epi32(q2, _MM_SHUFFLE(0,0,0,0));
					q3 = _mm_cvtsi32_si128(0xffff & qlp_coeff[3]); q3 = _mm_shuffle_epi32(q3, _MM_SHUFFLE(0,0,0,0));
					q4 = _mm_cvtsi32_si128(0xffff & qlp_coeff[4]); q4 = _mm_shuffle_epi32(q4, _MM_SHUFFLE(0,0,0,0));
					q5 = _mm_cvtsi32_si128(0xffff & qlp_coeff[5]); q5 = _mm_shuffle_epi32(q5, _MM_SHUFFLE(0,0,0,0));

					for(i = 0; i < (int)data_len-3; i+=4) {
						__m128i summ, mull;
						summ = _mm_madd_epi16(q5, _mm_loadu_si128((const __m128i*)(data+i-6)));
						mull = _mm_madd_epi16(q4, _mm_loadu_si128((const __m128i*)(data+i-5))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q3, _mm_loadu_si128((const __m128i*)(data+i-4))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q2, _mm_loadu_si128((const __m128i*)(data+i-3))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q1, _mm_loadu_si128((const __m128i*)(data+i-2))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q0, _mm_loadu_si128((const __m128i*)(data+i-1))); summ = _mm_add_epi32(summ, mull);
						summ = _mm_sra_epi32(summ, cnt);
						_mm_storeu_si128((__m128i*)(residual+i), _mm_sub_epi32(_mm_loadu_si128((const __m128i*)(data+i)), summ));
					}
				}
				else { /* order == 5 */
					__m128i q0, q1, q2, q3, q4;
					q0 = _mm_cvtsi32_si128(0xffff & qlp_coeff[0]); q0 = _mm_shuffle_epi32(q0, _MM_SHUFFLE(0,0,0,0));
					q1 = _mm_cvtsi32_si128(0xffff & qlp_coeff[1]); q1 = _mm_shuffle_epi32(q1, _MM_SHUFFLE(0,0,0,0));
					q2 = _mm_cvtsi32_si128(0xffff & qlp_coeff[2]); q2 = _mm_shuffle_epi32(q2, _MM_SHUFFLE(0,0,0,0));
					q3 = _mm_cvtsi32_si128(0xffff & qlp_coeff[3]); q3 = _mm_shuffle_epi32(q3, _MM_SHUFFLE(0,0,0,0));
					q4 = _mm_cvtsi32_si128(0xffff & qlp_coeff[4]); q4 = _mm_shuffle_epi32(q4, _MM_SHUFFLE(0,0,0,0));

					for(i = 0; i < (int)data_len-3; i+=4) {
						__m128i summ, mull;
						summ = _mm_madd_epi16(q4, _mm_loadu_si128((const __m128i*)(data+i-5)));
						mull = _mm_madd_epi16(q3, _mm_loadu_si128((const __m128i*)(data+i-4))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q2, _mm_loadu_si128((const __m128i*)(data+i-3))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q1, _mm_loadu_si128((const __m128i*)(data+i-2))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q0, _mm_loadu_si128((const __m128i*)(data+i-1))); summ = _mm_add_epi32(summ, mull);
						summ = _mm_sra_epi32(summ, cnt);
						_mm_storeu_si128((__m128i*)(residual+i), _mm_sub_epi32(_mm_loadu_si128((const __m128i*)(data+i)), summ));
					}
				}
			}
		}
		else {
			if(order > 2) {
				if(order == 4) {
					__m128i q0, q1, q2, q3;
					q0 = _mm_cvtsi32_si128(0xffff & qlp_coeff[0]); q0 = _mm_shuffle_epi32(q0, _MM_SHUFFLE(0,0,0,0));
					q1 = _mm_cvtsi32_si128(0xffff & qlp_coeff[1]); q1 = _mm_shuffle_epi32(q1, _MM_SHUFFLE(0,0,0,0));
					q2 = _mm_cvtsi32_si128(0xffff & qlp_coeff[2]); q2 = _mm_shuffle_epi32(q2, _MM_SHUFFLE(0,0,0,0));
					q3 = _mm_cvtsi32_si128(0xffff & qlp_coeff[3]); q3 = _mm_shuffle_epi32(q3, _MM_SHUFFLE(0,0,0,0));

					for(i = 0; i < (int)data_len-3; i+=4) {
						__m128i summ, mull;
						summ = _mm_madd_epi16(q3, _mm_loadu_si128((const __m128i*)(data+i-4)));
						mull = _mm_madd_epi16(q2, _mm_loadu_si128((const __m128i*)(data+i-3))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q1, _mm_loadu_si128((const __m128i*)(data+i-2))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q0, _mm_loadu_si128((const __m128i*)(data+i-1))); summ = _mm_add_epi32(summ, mull);
						summ = _mm_sra_epi32(summ, cnt);
						_mm_storeu_si128((__m128i*)(residual+i), _mm_sub_epi32(_mm_loadu_si128((const __m128i*)(data+i)), summ));
					}
				}
				else { /* order == 3 */
					__m128i q0, q1, q2;
					q0 = _mm_cvtsi32_si128(0xffff & qlp_coeff[0]); q0 = _mm_shuffle_epi32(q0, _MM_SHUFFLE(0,0,0,0));
					q1 = _mm_cvtsi32_si128(0xffff & qlp_coeff[1]); q1 = _mm_shuffle_epi32(q1, _MM_SHUFFLE(0,0,0,0));
					q2 = _mm_cvtsi32_si128(0xffff & qlp_coeff[2]); q2 = _mm_shuffle_epi32(q2, _MM_SHUFFLE(0,0,0,0));

					for(i = 0; i < (int)data_len-3; i+=4) {
						__m128i summ, mull;
						summ = _mm_madd_epi16(q2, _mm_loadu_si128((const __m128i*)(data+i-3)));
						mull = _mm_madd_epi16(q1, _mm_loadu_si128((const __m128i*)(data+i-2))); summ = _mm_add_epi32(summ, mull);
						mull = _mm_madd_epi16(q0, _mm_loadu_si128((const __m128i*)(data+i-1))); summ = _mm_add_epi32(summ, mull);
						summ = _mm_sra_epi32(summ, cnt);
						_mm_storeu_si128((__m128i*)(residual+i), _mm_sub_epi32(_mm_loadu_si128((const __m128i*)(data+i)), summ));
					}
				}
			}
			else {
				if(order == 2) {
					__m128i q0, q1;
					q0 = _mm_cvtsi32_si128(0xffff & qlp_coeff[0]); q0 = _mm_shuffle_epi32(q0, _MM_SHUFFLE(0,0,0,0));
					q1 = _mm_cvtsi32_si128(0xffff & qlp_coeff[1]); q1 = _mm_shuffle_epi32(q1, _MM_SHUFFLE(0,0,0,0));

					for(i = 0; i < (int)data_len-3; i+=4) {
						__m128i summ, mull;
						summ = _mm_madd_epi16(q1, _mm_loadu_si128((const __m128i*)(data+i-2)));
						mull = _mm_madd_epi16(q0, _mm_loadu_si128((const __m128i*)(data+i-1))); summ = _mm_add_epi32(summ, mull);
						summ = _mm_sra_epi32(summ, cnt);
						_mm_storeu_si128((__m128i*)(residual+i), _mm_sub_epi32(_mm_loadu_si128((const __m128i*)(data+i)), summ));
					}
				}
				else { /* order == 1 */
					__m128i q0;
					q0 = _mm_cvtsi32_si128(0xffff & qlp_coeff[0]); q0 = _mm_shuffle_epi32(q0, _MM_SHUFFLE(0,0,0,0));

					for(i = 0; i < (int)data_len-3; i+=4) {
						__m128i summ;
						summ = _mm_madd_epi16(q0, _mm_loadu_si128((const __m128i*)(data+i-1)));
						summ = _mm_sra_epi32(summ, cnt);
						_mm_storeu_si128((__m128i*)(residual+i), _mm_sub_epi32(_mm_loadu_si128((const __m128i*)(data+i)), summ));
					}
				}
			}
		}
		for(; i < (int)data_len; i++) {
			sum = 0;
			switch(order) {
				case 12: sum += qlp_coeff[11] * data[i-12]; /* Falls through. */
				case 11: sum += qlp_coeff[10] * data[i-11]; /* Falls through. */
				case 10: sum += qlp_coeff[ 9] * data[i-10]; /* Falls through. */
				case 9:  sum += qlp_coeff[ 8] * data[i- 9]; /* Falls through. */
				case 8:  sum += qlp_coeff[ 7] * data[i- 8]; /* Falls through. */
				case 7:  sum += qlp_coeff[ 6] * data[i- 7]; /* Falls through. */
				case 6:  sum += qlp_coeff[ 5] * data[i- 6]; /* Falls through. */
				case 5:  sum += qlp_coeff[ 4] * data[i- 5]; /* Falls through. */
				case 4:  sum += qlp_coeff[ 3] * data[i- 4]; /* Falls through. */
				case 3:  sum += qlp_coeff[ 2] * data[i- 3]; /* Falls through. */
				case 2:  sum += qlp_coeff[ 1] * data[i- 2]; /* Falls through. */
				case 1:  sum += qlp_coeff[ 0] * data[i- 1];
			}
			residual[i] = data[i] - (sum >> lp_quantization);
		}
	}
	else { /* order > 12 */
		for(i = 0; i < (int)data_len; i++) {
			sum = 0;
			switch(order) {
				case 32: sum += qlp_coeff[31] * data[i-32]; /* Falls through. */
				case 31: sum += qlp_coeff[30] * data[i-31]; /* Falls through. */
				case 30: sum += qlp_coeff[29] * data[i-30]; /* Falls through. */
				case 29: sum += qlp_coeff[28] * data[i-29]; /* Falls through. */
				case 28: sum += qlp_coeff[27] * data[i-28]; /* Falls through. */
				case 27: sum += qlp_coeff[26] * data[i-27]; /* Falls through. */
				case 26: sum += qlp_coeff[25] * data[i-26]; /* Falls through. */
				case 25: sum += qlp_coeff[24] * data[i-25]; /* Falls through. */
				case 24: sum += qlp_coeff[23] * data[i-24]; /* Falls through. */
				case 23: sum += qlp_coeff[22] * data[i-23]; /* Falls through. */
				case 22: sum += qlp_coeff[21] * data[i-22]; /* Falls through. */
				case 21: sum += qlp_coeff[20] * data[i-21]; /* Falls through. */
				case 20: sum += qlp_coeff[19] * data[i-20]; /* Falls through. */
				case 19: sum += qlp_coeff[18] * data[i-19]; /* Falls through. */
				case 18: sum += qlp_coeff[17] * data[i-18]; /* Falls through. */
				case 17: sum += qlp_coeff[16] * data[i-17]; /* Falls through. */
				case 16: sum += qlp_coeff[15] * data[i-16]; /* Falls through. */
				case 15: sum += qlp_coeff[14] * data[i-15]; /* Falls through. */
				case 14: sum += qlp_coeff[13] * data[i-14]; /* Falls through. */
				case 13: sum += qlp_coeff[12] * data[i-13];
				         sum += qlp_coeff[11] * data[i-12];
				         sum += qlp_coeff[10] * data[i-11];
				         sum += qlp_coeff[ 9] * data[i-10];
				         sum += qlp_coeff[ 8] * data[i- 9];
				         sum += qlp_coeff[ 7] * data[i- 8];
				         sum += qlp_coeff[ 6] * data[i- 7];
				         sum += qlp_coeff[ 5] * data[i- 6];
				         sum += qlp_coeff[ 4] * data[i- 5];
				         sum += qlp_coeff[ 3] * data[i- 4];
				         sum += qlp_coeff[ 2] * data[i- 3];
				         sum += qlp_coeff[ 1] * data[i- 2];
				         sum += qlp_coeff[ 0] * data[i- 1];
			}
			residual[i] = data[i] - (sum >> lp_quantization);
		}
	}
}

FLAC__SSE_TARGET("sse2")
void FLAC__lpc_compute_residual_from_qlp_coefficients_intrin_sse2(const FLAC__int32 *data, uint32_t data_len, const FLAC__int32 qlp_coeff[], uint32_t order, int lp_quantization, FLAC__int32 residual[])
{
	int i;

	FLAC__ASSERT(order > 0);
	FLAC__ASSERT(order <= 32);

	if(order <= 12) {
		if(order > 8) { /* order == 9, 10, 11, 12 */
			if(order > 10) { /* order == 11, 12 */
				if(order == 12) {
					__m128i xmm0, xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7;
					xmm0 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+0));  // 0  0  q[1]  q[0]
					xmm1 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+2));  // 0  0  q[3]  q[2]
					xmm2 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+4));  // 0  0  q[5]  q[4]
					xmm3 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+6));  // 0  0  q[7]  q[6]
					xmm4 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+8));  // 0  0  q[9]  q[8]
					xmm5 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+10)); // 0  0  q[11] q[10]

					xmm0 = _mm_shuffle_epi32(xmm0, _MM_SHUFFLE(3,1,2,0)); // 0  q[1]  0  q[0]
					xmm1 = _mm_shuffle_epi32(xmm1, _MM_SHUFFLE(3,1,2,0)); // 0  q[3]  0  q[2]
					xmm2 = _mm_shuffle_epi32(xmm2, _MM_SHUFFLE(3,1,2,0)); // 0  q[5]  0  q[4]
					xmm3 = _mm_shuffle_epi32(xmm3, _MM_SHUFFLE(3,1,2,0)); // 0  q[7]  0  q[6]
					xmm4 = _mm_shuffle_epi32(xmm4, _MM_SHUFFLE(3,1,2,0)); // 0  q[9]  0  q[8]
					xmm5 = _mm_shuffle_epi32(xmm5, _MM_SHUFFLE(3,1,2,0)); // 0  q[11] 0  q[10]

					for(i = 0; i < (int)data_len; i++) {
						//sum = 0;
						//sum += qlp_coeff[11] * data[i-12];
						//sum += qlp_coeff[10] * data[i-11];
						xmm7 = _mm_loadl_epi64((const __m128i*)(data+i-12));  // 0   0        d[i-11]  d[i-12]
						xmm7 = _mm_shuffle_epi32(xmm7, _MM_SHUFFLE(2,0,3,1)); // 0  d[i-12]   0        d[i-11]
						xmm7 = _mm_mul_epu32(xmm7, xmm5); /* we use _unsigned_ multiplication and discard high dword of the result values */

						//sum += qlp_coeff[9] * data[i-10];
						//sum += qlp_coeff[8] * data[i-9];
						xmm6 = _mm_loadl_epi64((const __m128i*)(data+i-10));
						xmm6 = _mm_shuffle_epi32(xmm6, _MM_SHUFFLE(2,0,3,1));
						xmm6 = _mm_mul_epu32(xmm6, xmm4);
						xmm7 = _mm_add_epi32(xmm7, xmm6);

						//sum += qlp_coeff[7] * data[i-8];
						//sum += qlp_coeff[6] * data[i-7];
						xmm6 = _mm_loadl_epi64((const __m128i*)(data+i-8));
						xmm6 = _mm_shuffle_epi32(xmm6, _MM_SHUFFLE(2,0,3,1));
						xmm6 = _mm_mul_epu32(xmm6, xmm3);
						xmm7 = _mm_add_epi32(xmm7, xmm6);

						//sum += qlp_coeff[5] * data[i-6];
						//sum += qlp_coeff[4] * data[i-5];
						xmm6 = _mm_loadl_epi64((const __m128i*)(data+i-6));
						xmm6 = _mm_shuffle_epi32(xmm6, _MM_SHUFFLE(2,0,3,1));
						xmm6 = _mm_mul_epu32(xmm6, xmm2);
						xmm7 = _mm_add_epi32(xmm7, xmm6);

						//sum += qlp_coeff[3] * data[i-4];
						//sum += qlp_coeff[2] * data[i-3];
						xmm6 = _mm_loadl_epi64((const __m128i*)(data+i-4));
						xmm6 = _mm_shuffle_epi32(xmm6, _MM_SHUFFLE(2,0,3,1));
						xmm6 = _mm_mul_epu32(xmm6, xmm1);
						xmm7 = _mm_add_epi32(xmm7, xmm6);

						//sum += qlp_coeff[1] * data[i-2];
						//sum += qlp_coeff[0] * data[i-1];
						xmm6 = _mm_loadl_epi64((const __m128i*)(data+i-2));
						xmm6 = _mm_shuffle_epi32(xmm6, _MM_SHUFFLE(2,0,3,1));
						xmm6 = _mm_mul_epu32(xmm6, xmm0);
						xmm7 = _mm_add_epi32(xmm7, xmm6);

						xmm7 = _mm_add_epi32(xmm7, _mm_srli_si128(xmm7, 8));
						RESIDUAL32_RESULT(xmm7);
					}
				}
				else { /* order == 11 */
					__m128i xmm0, xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7;
					xmm0 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+0));
					xmm1 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+2));
					xmm2 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+4));
					xmm3 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+6));
					xmm4 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+8));
					xmm5 = _mm_cvtsi32_si128(qlp_coeff[10]);

					xmm0 = _mm_shuffle_epi32(xmm0, _MM_SHUFFLE(3,1,2,0));
					xmm1 = _mm_shuffle_epi32(xmm1, _MM_SHUFFLE(3,1,2,0));
					xmm2 = _mm_shuffle_epi32(xmm2, _MM_SHUFFLE(3,1,2,0));
					xmm3 = _mm_shuffle_epi32(xmm3, _MM_SHUFFLE(3,1,2,0));
					xmm4 = _mm_shuffle_epi32(xmm4, _MM_SHUFFLE(3,1,2,0));

					for(i = 0; i < (int)data_len; i++) {
						//sum = 0;
						//sum  = qlp_coeff[10] * data[i-11];
						xmm7 = _mm_cvtsi32_si128(data[i-11]);
						xmm7 = _mm_mul_epu32(xmm7, xmm5);

						//sum += qlp_coeff[9] * data[i-10];
						//sum += qlp_coeff[8] * data[i-9];
						xmm6 = _mm_loadl_epi64((const __m128i*)(data+i-10));
						xmm6 = _mm_shuffle_epi32(xmm6, _MM_SHUFFLE(2,0,3,1));
						xmm6 = _mm_mul_epu32(xmm6, xmm4);
						xmm7 = _mm_add_epi32(xmm7, xmm6);

						//sum += qlp_coeff[7] * data[i-8];
						//sum += qlp_coeff[6] * data[i-7];
						xmm6 = _mm_loadl_epi64((const __m128i*)(data+i-8));
						xmm6 = _mm_shuffle_epi32(xmm6, _MM_SHUFFLE(2,0,3,1));
						xmm6 = _mm_mul_epu32(xmm6, xmm3);
						xmm7 = _mm_add_epi32(xmm7, xmm6);

						//sum += qlp_coeff[5] * data[i-6];
						//sum += qlp_coeff[4] * data[i-5];
						xmm6 = _mm_loadl_epi64((const __m128i*)(data+i-6));
						xmm6 = _mm_shuffle_epi32(xmm6, _MM_SHUFFLE(2,0,3,1));
						xmm6 = _mm_mul_epu32(xmm6, xmm2);
						xmm7 = _mm_add_epi32(xmm7, xmm6);

						//sum += qlp_coeff[3] * data[i-4];
						//sum += qlp_coeff[2] * data[i-3];
						xmm6 = _mm_loadl_epi64((const __m128i*)(data+i-4));
						xmm6 = _mm_shuffle_epi32(xmm6, _MM_SHUFFLE(2,0,3,1));
						xmm6 = _mm_mul_epu32(xmm6, xmm1);
						xmm7 = _mm_add_epi32(xmm7, xmm6);

						//sum += qlp_coeff[1] * data[i-2];
						//sum += qlp_coeff[0] * data[i-1];
						xmm6 = _mm_loadl_epi64((const __m128i*)(data+i-2));
						xmm6 = _mm_shuffle_epi32(xmm6, _MM_SHUFFLE(2,0,3,1));
						xmm6 = _mm_mul_epu32(xmm6, xmm0);
						xmm7 = _mm_add_epi32(xmm7, xmm6);

						xmm7 = _mm_add_epi32(xmm7, _mm_srli_si128(xmm7, 8));
						RESIDUAL32_RESULT(xmm7);
					}
				}
			}
			else { /* order == 9, 10 */
				if(order == 10) {
					__m128i xmm0, xmm1, xmm2, xmm3, xmm4, xmm6, xmm7;
					xmm0 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+0));
					xmm1 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+2));
					xmm2 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+4));
					xmm3 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+6));
					xmm4 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+8));

					xmm0 = _mm_shuffle_epi32(xmm0, _MM_SHUFFLE(3,1,2,0));
					xmm1 = _mm_shuffle_epi32(xmm1, _MM_SHUFFLE(3,1,2,0));
					xmm2 = _mm_shuffle_epi32(xmm2, _MM_SHUFFLE(3,1,2,0));
					xmm3 = _mm_shuffle_epi32(xmm3, _MM_SHUFFLE(3,1,2,0));
					xmm4 = _mm_shuffle_epi32(xmm4, _MM_SHUFFLE(3,1,2,0));

					for(i = 0; i < (int)data_len; i++) {
						//sum = 0;
						//sum += qlp_coeff[9] * data[i-10];
						//sum += qlp_coeff[8] * data[i-9];
						xmm7 = _mm_loadl_epi64((const __m128i*)(data+i-10));
						xmm7 = _mm_shuffle_epi32(xmm7, _MM_SHUFFLE(2,0,3,1));
						xmm7 = _mm_mul_epu32(xmm7, xmm4);

						//sum += qlp_coeff[7] * data[i-8];
						//sum += qlp_coeff[6] * data[i-7];
						xmm6 = _mm_loadl_epi64((const __m128i*)(data+i-8));
						xmm6 = _mm_shuffle_epi32(xmm6, _MM_SHUFFLE(2,0,3,1));
						xmm6 = _mm_mul_epu32(xmm6, xmm3);
						xmm7 = _mm_add_epi32(xmm7, xmm6);

						//sum += qlp_coeff[5] * data[i-6];
						//sum += qlp_coeff[4] * data[i-5];
						xmm6 = _mm_loadl_epi64((const __m128i*)(data+i-6));
						xmm6 = _mm_shuffle_epi32(xmm6, _MM_SHUFFLE(2,0,3,1));
						xmm6 = _mm_mul_epu32(xmm6, xmm2);
						xmm7 = _mm_add_epi32(xmm7, xmm6);

						//sum += qlp_coeff[3] * data[i-4];
						//sum += qlp_coeff[2] * data[i-3];
						xmm6 = _mm_loadl_epi64((const __m128i*)(data+i-4));
						xmm6 = _mm_shuffle_epi32(xmm6, _MM_SHUFFLE(2,0,3,1));
						xmm6 = _mm_mul_epu32(xmm6, xmm1);
						xmm7 = _mm_add_epi32(xmm7, xmm6);

						//sum += qlp_coeff[1] * data[i-2];
						//sum += qlp_coeff[0] * data[i-1];
						xmm6 = _mm_loadl_epi64((const __m128i*)(data+i-2));
						xmm6 = _mm_shuffle_epi32(xmm6, _MM_SHUFFLE(2,0,3,1));
						xmm6 = _mm_mul_epu32(xmm6, xmm0);
						xmm7 = _mm_add_epi32(xmm7, xmm6);

						xmm7 = _mm_add_epi32(xmm7, _mm_srli_si128(xmm7, 8));
						RESIDUAL32_RESULT(xmm7);
					}
				}
				else { /* order == 9 */
					__m128i xmm0, xmm1, xmm2, xmm3, xmm4, xmm6, xmm7;
					xmm0 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+0));
					xmm1 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+2));
					xmm2 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+4));
					xmm3 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+6));
					xmm4 = _mm_cvtsi32_si128(qlp_coeff[8]);

					xmm0 = _mm_shuffle_epi32(xmm0, _MM_SHUFFLE(3,1,2,0));
					xmm1 = _mm_shuffle_epi32(xmm1, _MM_SHUFFLE(3,1,2,0));
					xmm2 = _mm_shuffle_epi32(xmm2, _MM_SHUFFLE(3,1,2,0));
					xmm3 = _mm_shuffle_epi32(xmm3, _MM_SHUFFLE(3,1,2,0));

					for(i = 0; i < (int)data_len; i++) {
						//sum = 0;
						//sum  = qlp_coeff[8] * data[i-9];
						xmm7 = _mm_cvtsi32_si128(data[i-9]);
						xmm7 = _mm_mul_epu32(xmm7, xmm4);

						//sum += qlp_coeff[7] * data[i-8];
						//sum += qlp_coeff[6] * data[i-7];
						xmm6 = _mm_loadl_epi64((const __m128i*)(data+i-8));
						xmm6 = _mm_shuffle_epi32(xmm6, _MM_SHUFFLE(2,0,3,1));
						xmm6 = _mm_mul_epu32(xmm6, xmm3);
						xmm7 = _mm_add_epi32(xmm7, xmm6);

						//sum += qlp_coeff[5] * data[i-6];
						//sum += qlp_coeff[4] * data[i-5];
						xmm6 = _mm_loadl_epi64((const __m128i*)(data+i-6));
						xmm6 = _mm_shuffle_epi32(xmm6, _MM_SHUFFLE(2,0,3,1));
						xmm6 = _mm_mul_epu32(xmm6, xmm2);
						xmm7 = _mm_add_epi32(xmm7, xmm6);

						//sum += qlp_coeff[3] * data[i-4];
						//sum += qlp_coeff[2] * data[i-3];
						xmm6 = _mm_loadl_epi64((const __m128i*)(data+i-4));
						xmm6 = _mm_shuffle_epi32(xmm6, _MM_SHUFFLE(2,0,3,1));
						xmm6 = _mm_mul_epu32(xmm6, xmm1);
						xmm7 = _mm_add_epi32(xmm7, xmm6);

						//sum += qlp_coeff[1] * data[i-2];
						//sum += qlp_coeff[0] * data[i-1];
						xmm6 = _mm_loadl_epi64((const __m128i*)(data+i-2));
						xmm6 = _mm_shuffle_epi32(xmm6, _MM_SHUFFLE(2,0,3,1));
						xmm6 = _mm_mul_epu32(xmm6, xmm0);
						xmm7 = _mm_add_epi32(xmm7, xmm6);

						xmm7 = _mm_add_epi32(xmm7, _mm_srli_si128(xmm7, 8));
						RESIDUAL32_RESULT(xmm7);
					}
				}
			}
		}
		else if(order > 4) { /* order == 5, 6, 7, 8 */
			if(order > 6) { /* order == 7, 8 */
				if(order == 8) {
					__m128i xmm0, xmm1, xmm2, xmm3, xmm6, xmm7;
					xmm0 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+0));
					xmm1 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+2));
					xmm2 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+4));
					xmm3 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+6));

					xmm0 = _mm_shuffle_epi32(xmm0, _MM_SHUFFLE(3,1,2,0));
					xmm1 = _mm_shuffle_epi32(xmm1, _MM_SHUFFLE(3,1,2,0));
					xmm2 = _mm_shuffle_epi32(xmm2, _MM_SHUFFLE(3,1,2,0));
					xmm3 = _mm_shuffle_epi32(xmm3, _MM_SHUFFLE(3,1,2,0));

					for(i = 0; i < (int)data_len; i++) {
						//sum = 0;
						//sum += qlp_coeff[7] * data[i-8];
						//sum += qlp_coeff[6] * data[i-7];
						xmm7 = _mm_loadl_epi64((const __m128i*)(data+i-8));
						xmm7 = _mm_shuffle_epi32(xmm7, _MM_SHUFFLE(2,0,3,1));
						xmm7 = _mm_mul_epu32(xmm7, xmm3);

						//sum += qlp_coeff[5] * data[i-6];
						//sum += qlp_coeff[4] * data[i-5];
						xmm6 = _mm_loadl_epi64((const __m128i*)(data+i-6));
						xmm6 = _mm_shuffle_epi32(xmm6, _MM_SHUFFLE(2,0,3,1));
						xmm6 = _mm_mul_epu32(xmm6, xmm2);
						xmm7 = _mm_add_epi32(xmm7, xmm6);

						//sum += qlp_coeff[3] * data[i-4];
						//sum += qlp_coeff[2] * data[i-3];
						xmm6 = _mm_loadl_epi64((const __m128i*)(data+i-4));
						xmm6 = _mm_shuffle_epi32(xmm6, _MM_SHUFFLE(2,0,3,1));
						xmm6 = _mm_mul_epu32(xmm6, xmm1);
						xmm7 = _mm_add_epi32(xmm7, xmm6);

						//sum += qlp_coeff[1] * data[i-2];
						//sum += qlp_coeff[0] * data[i-1];
						xmm6 = _mm_loadl_epi64((const __m128i*)(data+i-2));
						xmm6 = _mm_shuffle_epi32(xmm6, _MM_SHUFFLE(2,0,3,1));
						xmm6 = _mm_mul_epu32(xmm6, xmm0);
						xmm7 = _mm_add_epi32(xmm7, xmm6);

						xmm7 = _mm_add_epi32(xmm7, _mm_srli_si128(xmm7, 8));
						RESIDUAL32_RESULT(xmm7);
					}
				}
				else { /* order == 7 */
					__m128i xmm0, xmm1, xmm2, xmm3, xmm6, xmm7;
					xmm0 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+0));
					xmm1 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+2));
					xmm2 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+4));
					xmm3 = _mm_cvtsi32_si128(qlp_coeff[6]);

					xmm0 = _mm_shuffle_epi32(xmm0, _MM_SHUFFLE(3,1,2,0));
					xmm1 = _mm_shuffle_epi32(xmm1, _MM_SHUFFLE(3,1,2,0));
					xmm2 = _mm_shuffle_epi32(xmm2, _MM_SHUFFLE(3,1,2,0));

					for(i = 0; i < (int)data_len; i++) {
						//sum = 0;
						//sum  = qlp_coeff[6] * data[i-7];
						xmm7 = _mm_cvtsi32_si128(data[i-7]);
						xmm7 = _mm_mul_epu32(xmm7, xmm3);

						//sum += qlp_coeff[5] * data[i-6];
						//sum += qlp_coeff[4] * data[i-5];
						xmm6 = _mm_loadl_epi64((const __m128i*)(data+i-6));
						xmm6 = _mm_shuffle_epi32(xmm6, _MM_SHUFFLE(2,0,3,1));
						xmm6 = _mm_mul_epu32(xmm6, xmm2);
						xmm7 = _mm_add_epi32(xmm7, xmm6);

						//sum += qlp_coeff[3] * data[i-4];
						//sum += qlp_coeff[2] * data[i-3];
						xmm6 = _mm_loadl_epi64((const __m128i*)(data+i-4));
						xmm6 = _mm_shuffle_epi32(xmm6, _MM_SHUFFLE(2,0,3,1));
						xmm6 = _mm_mul_epu32(xmm6, xmm1);
						xmm7 = _mm_add_epi32(xmm7, xmm6);

						//sum += qlp_coeff[1] * data[i-2];
						//sum += qlp_coeff[0] * data[i-1];
						xmm6 = _mm_loadl_epi64((const __m128i*)(data+i-2));
						xmm6 = _mm_shuffle_epi32(xmm6, _MM_SHUFFLE(2,0,3,1));
						xmm6 = _mm_mul_epu32(xmm6, xmm0);
						xmm7 = _mm_add_epi32(xmm7, xmm6);

						xmm7 = _mm_add_epi32(xmm7, _mm_srli_si128(xmm7, 8));
						RESIDUAL32_RESULT(xmm7);
					}
				}
			}
			else { /* order == 5, 6 */
				if(order == 6) {
					__m128i xmm0, xmm1, xmm2, xmm6, xmm7;
					xmm0 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+0));
					xmm1 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+2));
					xmm2 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+4));

					xmm0 = _mm_shuffle_epi32(xmm0, _MM_SHUFFLE(3,1,2,0));
					xmm1 = _mm_shuffle_epi32(xmm1, _MM_SHUFFLE(3,1,2,0));
					xmm2 = _mm_shuffle_epi32(xmm2, _MM_SHUFFLE(3,1,2,0));

					for(i = 0; i < (int)data_len; i++) {
						//sum = 0;
						//sum += qlp_coeff[5] * data[i-6];
						//sum += qlp_coeff[4] * data[i-5];
						xmm7 = _mm_loadl_epi64((const __m128i*)(data+i-6));
						xmm7 = _mm_shuffle_epi32(xmm7, _MM_SHUFFLE(2,0,3,1));
						xmm7 = _mm_mul_epu32(xmm7, xmm2);

						//sum += qlp_coeff[3] * data[i-4];
						//sum += qlp_coeff[2] * data[i-3];
						xmm6 = _mm_loadl_epi64((const __m128i*)(data+i-4));
						xmm6 = _mm_shuffle_epi32(xmm6, _MM_SHUFFLE(2,0,3,1));
						xmm6 = _mm_mul_epu32(xmm6, xmm1);
						xmm7 = _mm_add_epi32(xmm7, xmm6);

						//sum += qlp_coeff[1] * data[i-2];
						//sum += qlp_coeff[0] * data[i-1];
						xmm6 = _mm_loadl_epi64((const __m128i*)(data+i-2));
						xmm6 = _mm_shuffle_epi32(xmm6, _MM_SHUFFLE(2,0,3,1));
						xmm6 = _mm_mul_epu32(xmm6, xmm0);
						xmm7 = _mm_add_epi32(xmm7, xmm6);

						xmm7 = _mm_add_epi32(xmm7, _mm_srli_si128(xmm7, 8));
						RESIDUAL32_RESULT(xmm7);
					}
				}
				else { /* order == 5 */
					__m128i xmm0, xmm1, xmm2, xmm6, xmm7;
					xmm0 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+0));
					xmm1 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+2));
					xmm2 = _mm_cvtsi32_si128(qlp_coeff[4]);

					xmm0 = _mm_shuffle_epi32(xmm0, _MM_SHUFFLE(3,1,2,0));
					xmm1 = _mm_shuffle_epi32(xmm1, _MM_SHUFFLE(3,1,2,0));

					for(i = 0; i < (int)data_len; i++) {
						//sum = 0;
						//sum  = qlp_coeff[4] * data[i-5];
						xmm7 = _mm_cvtsi32_si128(data[i-5]);
						xmm7 = _mm_mul_epu32(xmm7, xmm2);

						//sum += qlp_coeff[3] * data[i-4];
						//sum += qlp_coeff[2] * data[i-3];
						xmm6 = _mm_loadl_epi64((const __m128i*)(data+i-4));
						xmm6 = _mm_shuffle_epi32(xmm6, _MM_SHUFFLE(2,0,3,1));
						xmm6 = _mm_mul_epu32(xmm6, xmm1);
						xmm7 = _mm_add_epi32(xmm7, xmm6);

						//sum += qlp_coeff[1] * data[i-2];
						//sum += qlp_coeff[0] * data[i-1];
						xmm6 = _mm_loadl_epi64((const __m128i*)(data+i-2));
						xmm6 = _mm_shuffle_epi32(xmm6, _MM_SHUFFLE(2,0,3,1));
						xmm6 = _mm_mul_epu32(xmm6, xmm0);
						xmm7 = _mm_add_epi32(xmm7, xmm6);

						xmm7 = _mm_add_epi32(xmm7, _mm_srli_si128(xmm7, 8));
						RESIDUAL32_RESULT(xmm7);
					}
				}
			}
		}
		else { /* order == 1, 2, 3, 4 */
			if(order > 2) { /* order == 3, 4 */
				if(order == 4) {
					__m128i xmm0, xmm1, xmm6, xmm7;
					xmm0 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+0));
					xmm1 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+2));

					xmm0 = _mm_shuffle_epi32(xmm0, _MM_SHUFFLE(3,1,2,0));
					xmm1 = _mm_shuffle_epi32(xmm1, _MM_SHUFFLE(3,1,2,0));

					for(i = 0; i < (int)data_len; i++) {
						//sum = 0;
						//sum += qlp_coeff[3] * data[i-4];
						//sum += qlp_coeff[2] * data[i-3];
						xmm7 = _mm_loadl_epi64((const __m128i*)(data+i-4));
						xmm7 = _mm_shuffle_epi32(xmm7, _MM_SHUFFLE(2,0,3,1));
						xmm7 = _mm_mul_epu32(xmm7, xmm1);

						//sum += qlp_coeff[1] * data[i-2];
						//sum += qlp_coeff[0] * data[i-1];
						xmm6 = _mm_loadl_epi64((const __m128i*)(data+i-2));
						xmm6 = _mm_shuffle_epi32(xmm6, _MM_SHUFFLE(2,0,3,1));
						xmm6 = _mm_mul_epu32(xmm6, xmm0);
						xmm7 = _mm_add_epi32(xmm7, xmm6);

						xmm7 = _mm_add_epi32(xmm7, _mm_srli_si128(xmm7, 8));
						RESIDUAL32_RESULT(xmm7);
					}
				}
				else { /* order == 3 */
					__m128i xmm0, xmm1, xmm6, xmm7;
					xmm0 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+0));
					xmm1 = _mm_cvtsi32_si128(qlp_coeff[2]);

					xmm0 = _mm_shuffle_epi32(xmm0, _MM_SHUFFLE(3,1,2,0));

					for(i = 0; i < (int)data_len; i++) {
						//sum = 0;
						//sum  = qlp_coeff[2] * data[i-3];
						xmm7 = _mm_cvtsi32_si128(data[i-3]);
						xmm7 = _mm_mul_epu32(xmm7, xmm1);

						//sum += qlp_coeff[1] * data[i-2];
						//sum += qlp_coeff[0] * data[i-1];
						xmm6 = _mm_loadl_epi64((const __m128i*)(data+i-2));
						xmm6 = _mm_shuffle_epi32(xmm6, _MM_SHUFFLE(2,0,3,1));
						xmm6 = _mm_mul_epu32(xmm6, xmm0);
						xmm7 = _mm_add_epi32(xmm7, xmm6);

						xmm7 = _mm_add_epi32(xmm7, _mm_srli_si128(xmm7, 8));
						RESIDUAL32_RESULT(xmm7);
					}
				}
			}
			else { /* order == 1, 2 */
				if(order == 2) {
					__m128i xmm0, xmm7;
					xmm0 = _mm_loadl_epi64((const __m128i*)(qlp_coeff+0));
					xmm0 = _mm_shuffle_epi32(xmm0, _MM_SHUFFLE(3,1,2,0));

					for(i = 0; i < (int)data_len; i++) {
						//sum = 0;
						//sum += qlp_coeff[1] * data[i-2];
						//sum += qlp_coeff[0] * data[i-1];
						xmm7 = _mm_loadl_epi64((const __m128i*)(data+i-2));
						xmm7 = _mm_shuffle_epi32(xmm7, _MM_SHUFFLE(2,0,3,1));
						xmm7 = _mm_mul_epu32(xmm7, xmm0);

						xmm7 = _mm_add_epi32(xmm7, _mm_srli_si128(xmm7, 8));
						RESIDUAL32_RESULT(xmm7);
					}
				}
				else { /* order == 1 */
					for(i = 0; i < (int)data_len; i++)
						residual[i] = data[i] - ((qlp_coeff[0] * data[i-1]) >> lp_quantization);
				}
			}
		}
	}
	else { /* order > 12 */
		FLAC__int32 sum;
		for(i = 0; i < (int)data_len; i++) {
			sum = 0;
			switch(order) {
				case 32: sum += qlp_coeff[31] * data[i-32]; /* Falls through. */
				case 31: sum += qlp_coeff[30] * data[i-31]; /* Falls through. */
				case 30: sum += qlp_coeff[29] * data[i-30]; /* Falls through. */
				case 29: sum += qlp_coeff[28] * data[i-29]; /* Falls through. */
				case 28: sum += qlp_coeff[27] * data[i-28]; /* Falls through. */
				case 27: sum += qlp_coeff[26] * data[i-27]; /* Falls through. */
				case 26: sum += qlp_coeff[25] * data[i-26]; /* Falls through. */
				case 25: sum += qlp_coeff[24] * data[i-25]; /* Falls through. */
				case 24: sum += qlp_coeff[23] * data[i-24]; /* Falls through. */
				case 23: sum += qlp_coeff[22] * data[i-23]; /* Falls through. */
				case 22: sum += qlp_coeff[21] * data[i-22]; /* Falls through. */
				case 21: sum += qlp_coeff[20] * data[i-21]; /* Falls through. */
				case 20: sum += qlp_coeff[19] * data[i-20]; /* Falls through. */
				case 19: sum += qlp_coeff[18] * data[i-19]; /* Falls through. */
				case 18: sum += qlp_coeff[17] * data[i-18]; /* Falls through. */
				case 17: sum += qlp_coeff[16] * data[i-17]; /* Falls through. */
				case 16: sum += qlp_coeff[15] * data[i-16]; /* Falls through. */
				case 15: sum += qlp_coeff[14] * data[i-15]; /* Falls through. */
				case 14: sum += qlp_coeff[13] * data[i-14]; /* Falls through. */
				case 13: sum += qlp_coeff[12] * data[i-13];
				         sum += qlp_coeff[11] * data[i-12];
				         sum += qlp_coeff[10] * data[i-11];
				         sum += qlp_coeff[ 9] * data[i-10];
				         sum += qlp_coeff[ 8] * data[i- 9];
				         sum += qlp_coeff[ 7] * data[i- 8];
				         sum += qlp_coeff[ 6] * data[i- 7];
				         sum += qlp_coeff[ 5] * data[i- 6];
				         sum += qlp_coeff[ 4] * data[i- 5];
				         sum += qlp_coeff[ 3] * data[i- 4];
				         sum += qlp_coeff[ 2] * data[i- 3];
				         sum += qlp_coeff[ 1] * data[i- 2];
				         sum += qlp_coeff[ 0] * data[i- 1];
			}
			residual[i] = data[i] - (sum >> lp_quantization);
		}
	}
}

#endif /* FLAC__SSE2_SUPPORTED */
#endif /* (FLAC__CPU_IA32 || FLAC__CPU_X86_64) && FLAC__HAS_X86INTRIN */
#endif /* FLAC__NO_ASM */
#endif /* FLAC__INTEGER_ONLY_LIBRARY */
