/* libFLAC - Free Lossless Audio Codec
 * Copyright (C) 2002-2009  Josh Coalson
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

#ifndef FLAC__PRIVATE__OGG_ENCODER_ASPECT_H
#define FLAC__PRIVATE__OGG_ENCODER_ASPECT_H

#include <ogg/ogg.h>

#include "FLAC/ordinals.h"
#include "FLAC/stream_encoder.h" /* for FLAC__StreamEncoderWriteStatus */

typedef struct FLAC__OggEncoderAspect {
	/* these are storage for values that can be set through the API */
	long serial_number;
	uint32_t num_metadata;

	/* these are for internal state related to Ogg encoding */
	ogg_stream_state stream_state;
	ogg_page page;
	FLAC__bool seen_magic; /* true if we've seen the fLaC magic in the write callback yet */
	FLAC__bool is_first_packet;
	FLAC__uint64 samples_written;
} FLAC__OggEncoderAspect;

void FLAC__ogg_encoder_aspect_set_serial_number(FLAC__OggEncoderAspect *aspect, long value);
FLAC__bool FLAC__ogg_encoder_aspect_set_num_metadata(FLAC__OggEncoderAspect *aspect, uint32_t value);
void FLAC__ogg_encoder_aspect_set_defaults(FLAC__OggEncoderAspect *aspect);
FLAC__bool FLAC__ogg_encoder_aspect_init(FLAC__OggEncoderAspect *aspect);
void FLAC__ogg_encoder_aspect_finish(FLAC__OggEncoderAspect *aspect);

typedef FLAC__StreamEncoderWriteStatus (*FLAC__OggEncoderAspectWriteCallbackProxy)(const void *encoder, const FLAC__byte buffer[], size_t bytes, uint32_t samples, uint32_t current_frame, void *client_data);

FLAC__StreamEncoderWriteStatus FLAC__ogg_encoder_aspect_write_callback_wrapper(FLAC__OggEncoderAspect *aspect, const FLAC__byte buffer[], size_t bytes, uint32_t samples, uint32_t current_frame, FLAC__bool is_last_block, FLAC__OggEncoderAspectWriteCallbackProxy write_callback, void *encoder, void *client_data);
#endif
