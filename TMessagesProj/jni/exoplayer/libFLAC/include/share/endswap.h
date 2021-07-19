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

/* It is assumed that this header will be included after "config.h". */

#if HAVE_BSWAP32			/* GCC and Clang */

/* GCC prior to 4.8 didn't provide bswap16 on x86_64 */
#if ! HAVE_BSWAP16
static inline unsigned short __builtin_bswap16(unsigned short a)
{
	return (a<<8)|(a>>8);
}
#endif

#define	ENDSWAP_16(x)		(__builtin_bswap16 (x))
#define	ENDSWAP_32(x)		(__builtin_bswap32 (x))
#define	ENDSWAP_64(x)		(__builtin_bswap64 (x))

#elif defined _MSC_VER		/* Windows */

#include <stdlib.h>

#define	ENDSWAP_16(x)		(_byteswap_ushort (x))
#define	ENDSWAP_32(x)		(_byteswap_ulong (x))
#define	ENDSWAP_64(x)		(_byteswap_uint64 (x))

#elif defined HAVE_BYTESWAP_H		/* Linux */

#include <byteswap.h>

#define	ENDSWAP_16(x)		(bswap_16 (x))
#define	ENDSWAP_32(x)		(bswap_32 (x))
#define	ENDSWAP_64(x)		(bswap_64 (x))

#else

#define	ENDSWAP_16(x)		((((x) >> 8) & 0xFF) | (((x) & 0xFF) << 8))
#define	ENDSWAP_32(x)		((((x) >> 24) & 0xFF) | (((x) >> 8) & 0xFF00) | (((x) & 0xFF00) << 8) | (((x) & 0xFF) << 24))
#define	ENDSWAP_64(x)		((ENDSWAP_32(((x) >> 32) & 0xFFFFFFFF)) | (ENDSWAP_32((x) & 0xFFFFFFFF) << 32))

#endif


/* Host to little-endian byte swapping (for MD5 calculation) */
#if CPU_IS_BIG_ENDIAN

#define H2LE_16(x)		ENDSWAP_16 (x)
#define H2LE_32(x)		ENDSWAP_32 (x)

#else

#define H2LE_16(x)		(x)
#define H2LE_32(x)		(x)

#endif
