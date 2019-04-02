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

#ifndef FLAC__PRIVATE__WINDOW_H
#define FLAC__PRIVATE__WINDOW_H

#ifdef HAVE_CONFIG_H
#include <config.h>
#endif

#include "private/float.h"
#include "FLAC/format.h"

#ifndef FLAC__INTEGER_ONLY_LIBRARY

/*
 *	FLAC__window_*()
 *	--------------------------------------------------------------------
 *	Calculates window coefficients according to different apodization
 *	functions.
 *
 *	OUT window[0,L-1]
 *	IN L (number of points in window)
 */
void FLAC__window_bartlett(FLAC__real *window, const FLAC__int32 L);
void FLAC__window_bartlett_hann(FLAC__real *window, const FLAC__int32 L);
void FLAC__window_blackman(FLAC__real *window, const FLAC__int32 L);
void FLAC__window_blackman_harris_4term_92db_sidelobe(FLAC__real *window, const FLAC__int32 L);
void FLAC__window_connes(FLAC__real *window, const FLAC__int32 L);
void FLAC__window_flattop(FLAC__real *window, const FLAC__int32 L);
void FLAC__window_gauss(FLAC__real *window, const FLAC__int32 L, const FLAC__real stddev); /* 0.0 < stddev <= 0.5 */
void FLAC__window_hamming(FLAC__real *window, const FLAC__int32 L);
void FLAC__window_hann(FLAC__real *window, const FLAC__int32 L);
void FLAC__window_kaiser_bessel(FLAC__real *window, const FLAC__int32 L);
void FLAC__window_nuttall(FLAC__real *window, const FLAC__int32 L);
void FLAC__window_rectangle(FLAC__real *window, const FLAC__int32 L);
void FLAC__window_triangle(FLAC__real *window, const FLAC__int32 L);
void FLAC__window_tukey(FLAC__real *window, const FLAC__int32 L, const FLAC__real p);
void FLAC__window_partial_tukey(FLAC__real *window, const FLAC__int32 L, const FLAC__real p, const FLAC__real start, const FLAC__real end);
void FLAC__window_punchout_tukey(FLAC__real *window, const FLAC__int32 L, const FLAC__real p, const FLAC__real start, const FLAC__real end);
void FLAC__window_welch(FLAC__real *window, const FLAC__int32 L);

#endif /* !defined FLAC__INTEGER_ONLY_LIBRARY */

#endif
