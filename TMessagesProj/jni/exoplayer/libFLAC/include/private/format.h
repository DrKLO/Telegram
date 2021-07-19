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

#ifndef FLAC__PRIVATE__FORMAT_H
#define FLAC__PRIVATE__FORMAT_H

#include "FLAC/format.h"

uint32_t FLAC__format_get_max_rice_partition_order(uint32_t blocksize, uint32_t predictor_order);
uint32_t FLAC__format_get_max_rice_partition_order_from_blocksize(uint32_t blocksize);
uint32_t FLAC__format_get_max_rice_partition_order_from_blocksize_limited_max_and_predictor_order(uint32_t limit, uint32_t blocksize, uint32_t predictor_order);
void FLAC__format_entropy_coding_method_partitioned_rice_contents_init(FLAC__EntropyCodingMethod_PartitionedRiceContents *object);
void FLAC__format_entropy_coding_method_partitioned_rice_contents_clear(FLAC__EntropyCodingMethod_PartitionedRiceContents *object);
FLAC__bool FLAC__format_entropy_coding_method_partitioned_rice_contents_ensure_size(FLAC__EntropyCodingMethod_PartitionedRiceContents *object, uint32_t max_partition_order);

#endif
