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

#ifndef FLAC__PRIVATE__MEMORY_H
#define FLAC__PRIVATE__MEMORY_H

#ifdef HAVE_CONFIG_H
#include <config.h>
#endif

#include <stdlib.h> /* for size_t */

#include "private/float.h"
#include "FLAC/ordinals.h" /* for FLAC__bool */

/* Returns the unaligned address returned by malloc.
 * Use free() on this address to deallocate.
 */
void *FLAC__memory_alloc_aligned(size_t bytes, void **aligned_address);
FLAC__bool FLAC__memory_alloc_aligned_int32_array(size_t elements, FLAC__int32 **unaligned_pointer, FLAC__int32 **aligned_pointer);
FLAC__bool FLAC__memory_alloc_aligned_uint32_array(size_t elements, FLAC__uint32 **unaligned_pointer, FLAC__uint32 **aligned_pointer);
FLAC__bool FLAC__memory_alloc_aligned_uint64_array(size_t elements, FLAC__uint64 **unaligned_pointer, FLAC__uint64 **aligned_pointer);
FLAC__bool FLAC__memory_alloc_aligned_unsigned_array(size_t elements, uint32_t **unaligned_pointer, uint32_t **aligned_pointer);
#ifndef FLAC__INTEGER_ONLY_LIBRARY
FLAC__bool FLAC__memory_alloc_aligned_real_array(size_t elements, FLAC__real **unaligned_pointer, FLAC__real **aligned_pointer);
#endif
void *safe_malloc_mul_2op_p(size_t size1, size_t size2);

#endif
