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

#ifndef FLAC__PRIVATE__STREAM_ENCODER_FRAMING_H
#define FLAC__PRIVATE__STREAM_ENCODER_FRAMING_H

#include "FLAC/format.h"
#include "bitwriter.h"

FLAC__bool FLAC__add_metadata_block(const FLAC__StreamMetadata *metadata, FLAC__BitWriter *bw);
FLAC__bool FLAC__frame_add_header(const FLAC__FrameHeader *header, FLAC__BitWriter *bw);
FLAC__bool FLAC__subframe_add_constant(const FLAC__Subframe_Constant *subframe, uint32_t subframe_bps, uint32_t wasted_bits, FLAC__BitWriter *bw);
FLAC__bool FLAC__subframe_add_fixed(const FLAC__Subframe_Fixed *subframe, uint32_t residual_samples, uint32_t subframe_bps, uint32_t wasted_bits, FLAC__BitWriter *bw);
FLAC__bool FLAC__subframe_add_lpc(const FLAC__Subframe_LPC *subframe, uint32_t residual_samples, uint32_t subframe_bps, uint32_t wasted_bits, FLAC__BitWriter *bw);
FLAC__bool FLAC__subframe_add_verbatim(const FLAC__Subframe_Verbatim *subframe, uint32_t samples, uint32_t subframe_bps, uint32_t wasted_bits, FLAC__BitWriter *bw);

#endif
