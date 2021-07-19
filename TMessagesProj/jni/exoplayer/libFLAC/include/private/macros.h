/* libFLAC - Free Lossless Audio Codec library
 * Copyright (C) 2012-2016  Xiph.org Foundation
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

#ifndef FLAC__PRIVATE__MACROS_H
#define FLAC__PRIVATE__MACROS_H

#if defined(__GNUC__) && (__GNUC__ > 4 || ( __GNUC__ == 4 && __GNUC_MINOR__ >= 3))

#define flac_max(a,b) \
	({ __typeof__ (a) _a = (a); \
	__typeof__ (b) _b = (b); \
	_a > _b ? _a : _b; })

#define MIN_PASTE(A,B) A##B
#define MIN_IMPL(A,B,L) ({ \
	__typeof__(A) MIN_PASTE(__a,L) = (A); \
	__typeof__(B) MIN_PASTE(__b,L) = (B); \
	MIN_PASTE(__a,L) < MIN_PASTE(__b,L) ? MIN_PASTE(__a,L) : MIN_PASTE(__b,L); \
	})

#define flac_min(A,B) MIN_IMPL(A,B,__COUNTER__)

/* Whatever other unix that has sys/param.h */
#elif defined(HAVE_SYS_PARAM_H)
#include <sys/param.h>
#define flac_max(a,b) MAX(a,b)
#define flac_min(a,b) MIN(a,b)

/* Windows VS has them in stdlib.h.. XXX:Untested */
#elif defined(_MSC_VER)
#include <stdlib.h>
#define flac_max(a,b) __max(a,b)
#define flac_min(a,b) __min(a,b)
#endif

#ifndef flac_min
#define flac_min(x,y)	((x) <= (y) ? (x) : (y))
#endif

#ifndef flac_max
#define flac_max(x,y)	((x) >= (y) ? (x) : (y))
#endif

#endif
