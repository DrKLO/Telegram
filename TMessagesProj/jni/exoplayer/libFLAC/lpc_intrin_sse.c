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
#ifdef FLAC__SSE_SUPPORTED
#include "FLAC/assert.h"
#include "FLAC/format.h"

#include <xmmintrin.h> /* SSE */

/*   new routines: more unaligned loads, less shuffle
 *   old routines: less unaligned loads, more shuffle
 *   these *_old routines are equivalent to the ASM routines in ia32/lpc_asm.nasm
 */

/* new routines: faster on current Intel (starting from Core i aka Nehalem) and all AMD CPUs */

FLAC__SSE_TARGET("sse")
void FLAC__lpc_compute_autocorrelation_intrin_sse_lag_4_new(const FLAC__real data[], uint32_t data_len, uint32_t lag, FLAC__real autoc[])
{
	int i;
	int limit = data_len - 4;
	__m128 sum0;

	(void) lag;
	FLAC__ASSERT(lag <= 4);
	FLAC__ASSERT(lag <= data_len);

	sum0 = _mm_setzero_ps();

	for(i = 0; i <= limit; i++) {
		__m128 d, d0;
		d0 = _mm_loadu_ps(data+i);
		d = _mm_shuffle_ps(d0, d0, 0);
		sum0 = _mm_add_ps(sum0, _mm_mul_ps(d0, d));
	}

	{
		__m128 d0 = _mm_setzero_ps();
		limit++; if(limit < 0) limit = 0;

		for(i = data_len-1; i >= limit; i--) {
			__m128 d;
			d = _mm_load_ss(data+i); d = _mm_shuffle_ps(d, d, 0);
			d0 = _mm_shuffle_ps(d0, d0, _MM_SHUFFLE(2,1,0,3));
			d0 = _mm_move_ss(d0, d);
			sum0 = _mm_add_ps(sum0, _mm_mul_ps(d, d0));
		}
	}

	_mm_storeu_ps(autoc,   sum0);
}

FLAC__SSE_TARGET("sse")
void FLAC__lpc_compute_autocorrelation_intrin_sse_lag_8_new(const FLAC__real data[], uint32_t data_len, uint32_t lag, FLAC__real autoc[])
{
	int i;
	int limit = data_len - 8;
	__m128 sum0, sum1;

	(void) lag;
	FLAC__ASSERT(lag <= 8);
	FLAC__ASSERT(lag <= data_len);

	sum0 = _mm_setzero_ps();
	sum1 = _mm_setzero_ps();

	for(i = 0; i <= limit; i++) {
		__m128 d, d0, d1;
		d0 = _mm_loadu_ps(data+i);
		d1 = _mm_loadu_ps(data+i+4);
		d = _mm_shuffle_ps(d0, d0, 0);
		sum0 = _mm_add_ps(sum0, _mm_mul_ps(d0, d));
		sum1 = _mm_add_ps(sum1, _mm_mul_ps(d1, d));
	}

	{
		__m128 d0 = _mm_setzero_ps();
		__m128 d1 = _mm_setzero_ps();
		limit++; if(limit < 0) limit = 0;

		for(i = data_len-1; i >= limit; i--) {
			__m128 d;
			d = _mm_load_ss(data+i); d = _mm_shuffle_ps(d, d, 0);
			d1 = _mm_shuffle_ps(d1, d1, _MM_SHUFFLE(2,1,0,3));
			d0 = _mm_shuffle_ps(d0, d0, _MM_SHUFFLE(2,1,0,3));
			d1 = _mm_move_ss(d1, d0);
			d0 = _mm_move_ss(d0, d);
			sum1 = _mm_add_ps(sum1, _mm_mul_ps(d, d1));
			sum0 = _mm_add_ps(sum0, _mm_mul_ps(d, d0));
		}
	}

	_mm_storeu_ps(autoc,   sum0);
	_mm_storeu_ps(autoc+4, sum1);
}

FLAC__SSE_TARGET("sse")
void FLAC__lpc_compute_autocorrelation_intrin_sse_lag_12_new(const FLAC__real data[], uint32_t data_len, uint32_t lag, FLAC__real autoc[])
{
	int i;
	int limit = data_len - 12;
	__m128 sum0, sum1, sum2;

	(void) lag;
	FLAC__ASSERT(lag <= 12);
	FLAC__ASSERT(lag <= data_len);

	sum0 = _mm_setzero_ps();
	sum1 = _mm_setzero_ps();
	sum2 = _mm_setzero_ps();

	for(i = 0; i <= limit; i++) {
		__m128 d, d0, d1, d2;
		d0 = _mm_loadu_ps(data+i);
		d1 = _mm_loadu_ps(data+i+4);
		d2 = _mm_loadu_ps(data+i+8);
		d = _mm_shuffle_ps(d0, d0, 0);
		sum0 = _mm_add_ps(sum0, _mm_mul_ps(d0, d));
		sum1 = _mm_add_ps(sum1, _mm_mul_ps(d1, d));
		sum2 = _mm_add_ps(sum2, _mm_mul_ps(d2, d));
	}

	{
		__m128 d0 = _mm_setzero_ps();
		__m128 d1 = _mm_setzero_ps();
		__m128 d2 = _mm_setzero_ps();
		limit++; if(limit < 0) limit = 0;

		for(i = data_len-1; i >= limit; i--) {
			__m128 d;
			d = _mm_load_ss(data+i); d = _mm_shuffle_ps(d, d, 0);
			d2 = _mm_shuffle_ps(d2, d2, _MM_SHUFFLE(2,1,0,3));
			d1 = _mm_shuffle_ps(d1, d1, _MM_SHUFFLE(2,1,0,3));
			d0 = _mm_shuffle_ps(d0, d0, _MM_SHUFFLE(2,1,0,3));
			d2 = _mm_move_ss(d2, d1);
			d1 = _mm_move_ss(d1, d0);
			d0 = _mm_move_ss(d0, d);
			sum2 = _mm_add_ps(sum2, _mm_mul_ps(d, d2));
			sum1 = _mm_add_ps(sum1, _mm_mul_ps(d, d1));
			sum0 = _mm_add_ps(sum0, _mm_mul_ps(d, d0));
		}
	}

	_mm_storeu_ps(autoc,   sum0);
	_mm_storeu_ps(autoc+4, sum1);
	_mm_storeu_ps(autoc+8, sum2);
}

FLAC__SSE_TARGET("sse")
void FLAC__lpc_compute_autocorrelation_intrin_sse_lag_16_new(const FLAC__real data[], uint32_t data_len, uint32_t lag, FLAC__real autoc[])
{
	int i;
	int limit = data_len - 16;
	__m128 sum0, sum1, sum2, sum3;

	(void) lag;
	FLAC__ASSERT(lag <= 16);
	FLAC__ASSERT(lag <= data_len);

	sum0 = _mm_setzero_ps();
	sum1 = _mm_setzero_ps();
	sum2 = _mm_setzero_ps();
	sum3 = _mm_setzero_ps();

	for(i = 0; i <= limit; i++) {
		__m128 d, d0, d1, d2, d3;
		d0 = _mm_loadu_ps(data+i);
		d1 = _mm_loadu_ps(data+i+4);
		d2 = _mm_loadu_ps(data+i+8);
		d3 = _mm_loadu_ps(data+i+12);
		d = _mm_shuffle_ps(d0, d0, 0);
		sum0 = _mm_add_ps(sum0, _mm_mul_ps(d0, d));
		sum1 = _mm_add_ps(sum1, _mm_mul_ps(d1, d));
		sum2 = _mm_add_ps(sum2, _mm_mul_ps(d2, d));
		sum3 = _mm_add_ps(sum3, _mm_mul_ps(d3, d));
	}

	{
		__m128 d0 = _mm_setzero_ps();
		__m128 d1 = _mm_setzero_ps();
		__m128 d2 = _mm_setzero_ps();
		__m128 d3 = _mm_setzero_ps();
		limit++; if(limit < 0) limit = 0;

		for(i = data_len-1; i >= limit; i--) {
			__m128 d;
			d = _mm_load_ss(data+i); d = _mm_shuffle_ps(d, d, 0);
			d3 = _mm_shuffle_ps(d3, d3, _MM_SHUFFLE(2,1,0,3));
			d2 = _mm_shuffle_ps(d2, d2, _MM_SHUFFLE(2,1,0,3));
			d1 = _mm_shuffle_ps(d1, d1, _MM_SHUFFLE(2,1,0,3));
			d0 = _mm_shuffle_ps(d0, d0, _MM_SHUFFLE(2,1,0,3));
			d3 = _mm_move_ss(d3, d2);
			d2 = _mm_move_ss(d2, d1);
			d1 = _mm_move_ss(d1, d0);
			d0 = _mm_move_ss(d0, d);
			sum3 = _mm_add_ps(sum3, _mm_mul_ps(d, d3));
			sum2 = _mm_add_ps(sum2, _mm_mul_ps(d, d2));
			sum1 = _mm_add_ps(sum1, _mm_mul_ps(d, d1));
			sum0 = _mm_add_ps(sum0, _mm_mul_ps(d, d0));
		}
	}

	_mm_storeu_ps(autoc,   sum0);
	_mm_storeu_ps(autoc+4, sum1);
	_mm_storeu_ps(autoc+8, sum2);
	_mm_storeu_ps(autoc+12,sum3);
}

/* old routines: faster on older Intel CPUs (up to Core 2) */

FLAC__SSE_TARGET("sse")
void FLAC__lpc_compute_autocorrelation_intrin_sse_lag_4_old(const FLAC__real data[], uint32_t data_len, uint32_t lag, FLAC__real autoc[])
{
	__m128 xmm0, xmm2, xmm5;

	(void) lag;
	FLAC__ASSERT(lag > 0);
	FLAC__ASSERT(lag <= 4);
	FLAC__ASSERT(lag <= data_len);
	FLAC__ASSERT(data_len > 0);

	xmm5 = _mm_setzero_ps();

	xmm0 = _mm_load_ss(data++);
	xmm2 = xmm0;
	xmm0 = _mm_shuffle_ps(xmm0, xmm0, 0);

	xmm0 = _mm_mul_ps(xmm0, xmm2);
	xmm5 = _mm_add_ps(xmm5, xmm0);

	data_len--;

	while(data_len)
	{
		xmm0 = _mm_load1_ps(data++);

		xmm2 = _mm_shuffle_ps(xmm2, xmm2, _MM_SHUFFLE(2,1,0,3));
		xmm2 = _mm_move_ss(xmm2, xmm0);
		xmm0 = _mm_mul_ps(xmm0, xmm2);
		xmm5 = _mm_add_ps(xmm5, xmm0);

		data_len--;
	}

	_mm_storeu_ps(autoc, xmm5);
}

FLAC__SSE_TARGET("sse")
void FLAC__lpc_compute_autocorrelation_intrin_sse_lag_8_old(const FLAC__real data[], uint32_t data_len, uint32_t lag, FLAC__real autoc[])
{
	__m128 xmm0, xmm1, xmm2, xmm3, xmm5, xmm6;

	(void) lag;
	FLAC__ASSERT(lag > 0);
	FLAC__ASSERT(lag <= 8);
	FLAC__ASSERT(lag <= data_len);
	FLAC__ASSERT(data_len > 0);

	xmm5 = _mm_setzero_ps();
	xmm6 = _mm_setzero_ps();

	xmm0 = _mm_load_ss(data++);
	xmm2 = xmm0;
	xmm0 = _mm_shuffle_ps(xmm0, xmm0, 0);
	xmm3 = _mm_setzero_ps();

	xmm0 = _mm_mul_ps(xmm0, xmm2);
	xmm5 = _mm_add_ps(xmm5, xmm0);

	data_len--;

	while(data_len)
	{
		xmm0 = _mm_load1_ps(data++);

		xmm2 = _mm_shuffle_ps(xmm2, xmm2, _MM_SHUFFLE(2,1,0,3));
		xmm3 = _mm_shuffle_ps(xmm3, xmm3, _MM_SHUFFLE(2,1,0,3));
		xmm3 = _mm_move_ss(xmm3, xmm2);
		xmm2 = _mm_move_ss(xmm2, xmm0);

		xmm1 = xmm0;
		xmm1 = _mm_mul_ps(xmm1, xmm3);
		xmm0 = _mm_mul_ps(xmm0, xmm2);
		xmm6 = _mm_add_ps(xmm6, xmm1);
		xmm5 = _mm_add_ps(xmm5, xmm0);

		data_len--;
	}

	_mm_storeu_ps(autoc,   xmm5);
	_mm_storeu_ps(autoc+4, xmm6);
}

FLAC__SSE_TARGET("sse")
void FLAC__lpc_compute_autocorrelation_intrin_sse_lag_12_old(const FLAC__real data[], uint32_t data_len, uint32_t lag, FLAC__real autoc[])
{
	__m128 xmm0, xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7;

	(void) lag;
	FLAC__ASSERT(lag > 0);
	FLAC__ASSERT(lag <= 12);
	FLAC__ASSERT(lag <= data_len);
	FLAC__ASSERT(data_len > 0);

	xmm5 = _mm_setzero_ps();
	xmm6 = _mm_setzero_ps();
	xmm7 = _mm_setzero_ps();

	xmm0 = _mm_load_ss(data++);
	xmm2 = xmm0;
	xmm0 = _mm_shuffle_ps(xmm0, xmm0, 0);
	xmm3 = _mm_setzero_ps();
	xmm4 = _mm_setzero_ps();

	xmm0 = _mm_mul_ps(xmm0, xmm2);
	xmm5 = _mm_add_ps(xmm5, xmm0);

	data_len--;

	while(data_len)
	{
		xmm0 = _mm_load1_ps(data++);

		xmm2 = _mm_shuffle_ps(xmm2, xmm2, _MM_SHUFFLE(2,1,0,3));
		xmm3 = _mm_shuffle_ps(xmm3, xmm3, _MM_SHUFFLE(2,1,0,3));
		xmm4 = _mm_shuffle_ps(xmm4, xmm4, _MM_SHUFFLE(2,1,0,3));
		xmm4 = _mm_move_ss(xmm4, xmm3);
		xmm3 = _mm_move_ss(xmm3, xmm2);
		xmm2 = _mm_move_ss(xmm2, xmm0);

		xmm1 = xmm0;
		xmm1 = _mm_mul_ps(xmm1, xmm2);
		xmm5 = _mm_add_ps(xmm5, xmm1);
		xmm1 = xmm0;
		xmm1 = _mm_mul_ps(xmm1, xmm3);
		xmm6 = _mm_add_ps(xmm6, xmm1);
		xmm0 = _mm_mul_ps(xmm0, xmm4);
		xmm7 = _mm_add_ps(xmm7, xmm0);

		data_len--;
	}

	_mm_storeu_ps(autoc,   xmm5);
	_mm_storeu_ps(autoc+4, xmm6);
	_mm_storeu_ps(autoc+8, xmm7);
}

FLAC__SSE_TARGET("sse")
void FLAC__lpc_compute_autocorrelation_intrin_sse_lag_16_old(const FLAC__real data[], uint32_t data_len, uint32_t lag, FLAC__real autoc[])
{
	__m128 xmm0, xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7, xmm8, xmm9;

	(void) lag;
	FLAC__ASSERT(lag > 0);
	FLAC__ASSERT(lag <= 16);
	FLAC__ASSERT(lag <= data_len);
	FLAC__ASSERT(data_len > 0);

	xmm6 = _mm_setzero_ps();
	xmm7 = _mm_setzero_ps();
	xmm8 = _mm_setzero_ps();
	xmm9 = _mm_setzero_ps();

	xmm0 = _mm_load_ss(data++);
	xmm2 = xmm0;
	xmm0 = _mm_shuffle_ps(xmm0, xmm0, 0);
	xmm3 = _mm_setzero_ps();
	xmm4 = _mm_setzero_ps();
	xmm5 = _mm_setzero_ps();

	xmm0 = _mm_mul_ps(xmm0, xmm2);
	xmm6 = _mm_add_ps(xmm6, xmm0);

	data_len--;

	while(data_len)
	{
		xmm0 = _mm_load1_ps(data++);

		/* shift xmm5:xmm4:xmm3:xmm2 left by one float */
		xmm5 = _mm_shuffle_ps(xmm5, xmm5, _MM_SHUFFLE(2,1,0,3));
		xmm4 = _mm_shuffle_ps(xmm4, xmm4, _MM_SHUFFLE(2,1,0,3));
		xmm3 = _mm_shuffle_ps(xmm3, xmm3, _MM_SHUFFLE(2,1,0,3));
		xmm2 = _mm_shuffle_ps(xmm2, xmm2, _MM_SHUFFLE(2,1,0,3));
		xmm5 = _mm_move_ss(xmm5, xmm4);
		xmm4 = _mm_move_ss(xmm4, xmm3);
		xmm3 = _mm_move_ss(xmm3, xmm2);
		xmm2 = _mm_move_ss(xmm2, xmm0);

		/* xmm9|xmm8|xmm7|xmm6 += xmm0|xmm0|xmm0|xmm0 * xmm5|xmm4|xmm3|xmm2 */
		xmm1 = xmm0;
		xmm1 = _mm_mul_ps(xmm1, xmm5);
		xmm9 = _mm_add_ps(xmm9, xmm1);
		xmm1 = xmm0;
		xmm1 = _mm_mul_ps(xmm1, xmm4);
		xmm8 = _mm_add_ps(xmm8, xmm1);
		xmm1 = xmm0;
		xmm1 = _mm_mul_ps(xmm1, xmm3);
		xmm7 = _mm_add_ps(xmm7, xmm1);
		xmm0 = _mm_mul_ps(xmm0, xmm2);
		xmm6 = _mm_add_ps(xmm6, xmm0);

		data_len--;
	}

	_mm_storeu_ps(autoc,   xmm6);
	_mm_storeu_ps(autoc+4, xmm7);
	_mm_storeu_ps(autoc+8, xmm8);
	_mm_storeu_ps(autoc+12,xmm9);
}

#endif /* FLAC__SSE_SUPPORTED */
#endif /* (FLAC__CPU_IA32 || FLAC__CPU_X86_64) && FLAC__HAS_X86INTRIN */
#endif /* FLAC__NO_ASM */
#endif /* FLAC__INTEGER_ONLY_LIBRARY */
