/* libFLAC - Free Lossless Audio Codec
 * Copyright (C) 2004-2009  Josh Coalson
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

#ifndef FLAC__PRIVATE__OGG_MAPPING_H
#define FLAC__PRIVATE__OGG_MAPPING_H

#include "FLAC/ordinals.h"

/** The length of the packet type field in bytes. */
#define FLAC__OGG_MAPPING_PACKET_TYPE_LENGTH (1u)

extern const uint32_t FLAC__OGG_MAPPING_PACKET_TYPE_LEN; /* = 8 bits */

extern const FLAC__byte FLAC__OGG_MAPPING_FIRST_HEADER_PACKET_TYPE; /* = 0x7f */

/** The length of the 'FLAC' magic in bytes. */
#define FLAC__OGG_MAPPING_MAGIC_LENGTH (4u)

extern const FLAC__byte * const FLAC__OGG_MAPPING_MAGIC; /* = "FLAC" */

extern const uint32_t FLAC__OGG_MAPPING_VERSION_MAJOR_LEN; /* = 8 bits */
extern const uint32_t FLAC__OGG_MAPPING_VERSION_MINOR_LEN; /* = 8 bits */

/** The length of the Ogg FLAC mapping major version number in bytes. */
#define FLAC__OGG_MAPPING_VERSION_MAJOR_LENGTH (1u)

/** The length of the Ogg FLAC mapping minor version number in bytes. */
#define FLAC__OGG_MAPPING_VERSION_MINOR_LENGTH (1u)

extern const uint32_t FLAC__OGG_MAPPING_NUM_HEADERS_LEN; /* = 16 bits */

/** The length of the #-of-header-packets number bytes. */
#define FLAC__OGG_MAPPING_NUM_HEADERS_LENGTH (2u)

#endif
