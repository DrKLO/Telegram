/* libFLAC - Free Lossless Audio Codec library
 * Copyright (C) 2013-2016  Xiph.org Foundation
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

/* Safe string handling functions to replace things like strcpy, strncpy,
 * strcat, strncat etc.
 * All of these functions guarantee a correctly NUL terminated string but
 * the string may be truncated if the destination buffer was too short.
 */

#ifndef FLAC__SHARE_SAFE_STR_H
#define FLAC__SHARE_SAFE_STR_H

static inline char *
safe_strncat(char *dest, const char *src, size_t dest_size)
{
	char * ret;

	if (dest_size < 1)
		return dest;

	ret = strncat(dest, src, dest_size - strlen (dest));
	dest [dest_size - 1] = 0;

	return ret;
}

static inline char *
safe_strncpy(char *dest, const char *src, size_t dest_size)
{
	char * ret;

	if (dest_size < 1)
		return dest;

	ret = strncpy(dest, src, dest_size);
	dest [dest_size - 1] = 0;

	return ret;
}

#endif /* FLAC__SHARE_SAFE_STR_H */
