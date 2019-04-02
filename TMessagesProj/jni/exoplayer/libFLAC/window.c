/* libFLAC - Free Lossless Audio Codec library
 * Copyright (C) 2006-2009  Josh Coalson
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

#include <math.h>
#include "share/compat.h"
#include "FLAC/assert.h"
#include "FLAC/format.h"
#include "private/window.h"

#ifndef FLAC__INTEGER_ONLY_LIBRARY


void FLAC__window_bartlett(FLAC__real *window, const FLAC__int32 L)
{
	const FLAC__int32 N = L - 1;
	FLAC__int32 n;

	if (L & 1) {
		for (n = 0; n <= N/2; n++)
			window[n] = 2.0f * n / (float)N;
		for (; n <= N; n++)
			window[n] = 2.0f - 2.0f * n / (float)N;
	}
	else {
		for (n = 0; n <= L/2-1; n++)
			window[n] = 2.0f * n / (float)N;
		for (; n <= N; n++)
			window[n] = 2.0f - 2.0f * n / (float)N;
	}
}

void FLAC__window_bartlett_hann(FLAC__real *window, const FLAC__int32 L)
{
	const FLAC__int32 N = L - 1;
	FLAC__int32 n;

	for (n = 0; n < L; n++)
		window[n] = (FLAC__real)(0.62f - 0.48f * fabs((float)n/(float)N-0.5f) - 0.38f * cos(2.0f * M_PI * ((float)n/(float)N)));
}

void FLAC__window_blackman(FLAC__real *window, const FLAC__int32 L)
{
	const FLAC__int32 N = L - 1;
	FLAC__int32 n;

	for (n = 0; n < L; n++)
		window[n] = (FLAC__real)(0.42f - 0.5f * cos(2.0f * M_PI * n / N) + 0.08f * cos(4.0f * M_PI * n / N));
}

/* 4-term -92dB side-lobe */
void FLAC__window_blackman_harris_4term_92db_sidelobe(FLAC__real *window, const FLAC__int32 L)
{
	const FLAC__int32 N = L - 1;
	FLAC__int32 n;

	for (n = 0; n <= N; n++)
		window[n] = (FLAC__real)(0.35875f - 0.48829f * cos(2.0f * M_PI * n / N) + 0.14128f * cos(4.0f * M_PI * n / N) - 0.01168f * cos(6.0f * M_PI * n / N));
}

void FLAC__window_connes(FLAC__real *window, const FLAC__int32 L)
{
	const FLAC__int32 N = L - 1;
	const double N2 = (double)N / 2.;
	FLAC__int32 n;

	for (n = 0; n <= N; n++) {
		double k = ((double)n - N2) / N2;
		k = 1.0f - k * k;
		window[n] = (FLAC__real)(k * k);
	}
}

void FLAC__window_flattop(FLAC__real *window, const FLAC__int32 L)
{
	const FLAC__int32 N = L - 1;
	FLAC__int32 n;

	for (n = 0; n < L; n++)
		window[n] = (FLAC__real)(0.21557895f - 0.41663158f * cos(2.0f * M_PI * n / N) + 0.277263158f * cos(4.0f * M_PI * n / N) - 0.083578947f * cos(6.0f * M_PI * n / N) + 0.006947368f * cos(8.0f * M_PI * n / N));
}

void FLAC__window_gauss(FLAC__real *window, const FLAC__int32 L, const FLAC__real stddev)
{
	const FLAC__int32 N = L - 1;
	const double N2 = (double)N / 2.;
	FLAC__int32 n;

	for (n = 0; n <= N; n++) {
		const double k = ((double)n - N2) / (stddev * N2);
		window[n] = (FLAC__real)exp(-0.5f * k * k);
	}
}

void FLAC__window_hamming(FLAC__real *window, const FLAC__int32 L)
{
	const FLAC__int32 N = L - 1;
	FLAC__int32 n;

	for (n = 0; n < L; n++)
		window[n] = (FLAC__real)(0.54f - 0.46f * cos(2.0f * M_PI * n / N));
}

void FLAC__window_hann(FLAC__real *window, const FLAC__int32 L)
{
	const FLAC__int32 N = L - 1;
	FLAC__int32 n;

	for (n = 0; n < L; n++)
		window[n] = (FLAC__real)(0.5f - 0.5f * cos(2.0f * M_PI * n / N));
}

void FLAC__window_kaiser_bessel(FLAC__real *window, const FLAC__int32 L)
{
	const FLAC__int32 N = L - 1;
	FLAC__int32 n;

	for (n = 0; n < L; n++)
		window[n] = (FLAC__real)(0.402f - 0.498f * cos(2.0f * M_PI * n / N) + 0.098f * cos(4.0f * M_PI * n / N) - 0.001f * cos(6.0f * M_PI * n / N));
}

void FLAC__window_nuttall(FLAC__real *window, const FLAC__int32 L)
{
	const FLAC__int32 N = L - 1;
	FLAC__int32 n;

	for (n = 0; n < L; n++)
		window[n] = (FLAC__real)(0.3635819f - 0.4891775f*cos(2.0f*M_PI*n/N) + 0.1365995f*cos(4.0f*M_PI*n/N) - 0.0106411f*cos(6.0f*M_PI*n/N));
}

void FLAC__window_rectangle(FLAC__real *window, const FLAC__int32 L)
{
	FLAC__int32 n;

	for (n = 0; n < L; n++)
		window[n] = 1.0f;
}

void FLAC__window_triangle(FLAC__real *window, const FLAC__int32 L)
{
	FLAC__int32 n;

	if (L & 1) {
		for (n = 1; n <= (L+1)/2; n++)
			window[n-1] = 2.0f * n / ((float)L + 1.0f);
		for (; n <= L; n++)
			window[n-1] = (float)(2 * (L - n + 1)) / ((float)L + 1.0f);
	}
	else {
		for (n = 1; n <= L/2; n++)
			window[n-1] = 2.0f * n / ((float)L + 1.0f);
		for (; n <= L; n++)
			window[n-1] = (float)(2 * (L - n + 1)) / ((float)L + 1.0f);
	}
}

void FLAC__window_tukey(FLAC__real *window, const FLAC__int32 L, const FLAC__real p)
{
	if (p <= 0.0)
		FLAC__window_rectangle(window, L);
	else if (p >= 1.0)
		FLAC__window_hann(window, L);
	else {
		const FLAC__int32 Np = (FLAC__int32)(p / 2.0f * L) - 1;
		FLAC__int32 n;
		/* start with rectangle... */
		FLAC__window_rectangle(window, L);
		/* ...replace ends with hann */
		if (Np > 0) {
			for (n = 0; n <= Np; n++) {
				window[n] = (FLAC__real)(0.5f - 0.5f * cos(M_PI * n / Np));
				window[L-Np-1+n] = (FLAC__real)(0.5f - 0.5f * cos(M_PI * (n+Np) / Np));
			}
		}
	}
}

void FLAC__window_partial_tukey(FLAC__real *window, const FLAC__int32 L, const FLAC__real p, const FLAC__real start, const FLAC__real end)
{
	const FLAC__int32 start_n = (FLAC__int32)(start * L);
	const FLAC__int32 end_n = (FLAC__int32)(end * L);
	const FLAC__int32 N = end_n - start_n;
	FLAC__int32 Np, n, i;

	if (p <= 0.0f)
		FLAC__window_partial_tukey(window, L, 0.05f, start, end);
	else if (p >= 1.0f)
		FLAC__window_partial_tukey(window, L, 0.95f, start, end);
	else {

		Np = (FLAC__int32)(p / 2.0f * N);

		for (n = 0; n < start_n && n < L; n++)
			window[n] = 0.0f;
		for (i = 1; n < (start_n+Np) && n < L; n++, i++)
			window[n] = (FLAC__real)(0.5f - 0.5f * cos(M_PI * i / Np));
		for (; n < (end_n-Np) && n < L; n++)
			window[n] = 1.0f;
		for (i = Np; n < end_n && n < L; n++, i--)
			window[n] = (FLAC__real)(0.5f - 0.5f * cos(M_PI * i / Np));
		for (; n < L; n++)
			window[n] = 0.0f;
	}
}

void FLAC__window_punchout_tukey(FLAC__real *window, const FLAC__int32 L, const FLAC__real p, const FLAC__real start, const FLAC__real end)
{
	const FLAC__int32 start_n = (FLAC__int32)(start * L);
	const FLAC__int32 end_n = (FLAC__int32)(end * L);
	FLAC__int32 Ns, Ne, n, i;

	if (p <= 0.0f)
		FLAC__window_punchout_tukey(window, L, 0.05f, start, end);
	else if (p >= 1.0f)
		FLAC__window_punchout_tukey(window, L, 0.95f, start, end);
	else {

		Ns = (FLAC__int32)(p / 2.0f * start_n);
		Ne = (FLAC__int32)(p / 2.0f * (L - end_n));

		for (n = 0, i = 1; n < Ns && n < L; n++, i++)
			window[n] = (FLAC__real)(0.5f - 0.5f * cos(M_PI * i / Ns));
		for (; n < start_n-Ns && n < L; n++)
			window[n] = 1.0f;
		for (i = Ns; n < start_n && n < L; n++, i--)
			window[n] = (FLAC__real)(0.5f - 0.5f * cos(M_PI * i / Ns));
		for (; n < end_n && n < L; n++)
			window[n] = 0.0f;
		for (i = 1; n < end_n+Ne && n < L; n++, i++)
			window[n] = (FLAC__real)(0.5f - 0.5f * cos(M_PI * i / Ne));
		for (; n < L - (Ne) && n < L; n++)
			window[n] = 1.0f;
		for (i = Ne; n < L; n++, i--)
			window[n] = (FLAC__real)(0.5f - 0.5f * cos(M_PI * i / Ne));
	}
}

void FLAC__window_welch(FLAC__real *window, const FLAC__int32 L)
{
	const FLAC__int32 N = L - 1;
	const double N2 = (double)N / 2.;
	FLAC__int32 n;

	for (n = 0; n <= N; n++) {
		const double k = ((double)n - N2) / N2;
		window[n] = (FLAC__real)(1.0f - k * k);
	}
}

#endif /* !defined FLAC__INTEGER_ONLY_LIBRARY */
