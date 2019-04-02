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

#ifndef FLAC__PROTECTED__STREAM_ENCODER_H
#define FLAC__PROTECTED__STREAM_ENCODER_H

#include "FLAC/stream_encoder.h"
#if FLAC__HAS_OGG
#include "private/ogg_encoder_aspect.h"
#endif

#ifndef FLAC__INTEGER_ONLY_LIBRARY

#include "private/float.h"

#define FLAC__MAX_APODIZATION_FUNCTIONS 32

typedef enum {
	FLAC__APODIZATION_BARTLETT,
	FLAC__APODIZATION_BARTLETT_HANN,
	FLAC__APODIZATION_BLACKMAN,
	FLAC__APODIZATION_BLACKMAN_HARRIS_4TERM_92DB_SIDELOBE,
	FLAC__APODIZATION_CONNES,
	FLAC__APODIZATION_FLATTOP,
	FLAC__APODIZATION_GAUSS,
	FLAC__APODIZATION_HAMMING,
	FLAC__APODIZATION_HANN,
	FLAC__APODIZATION_KAISER_BESSEL,
	FLAC__APODIZATION_NUTTALL,
	FLAC__APODIZATION_RECTANGLE,
	FLAC__APODIZATION_TRIANGLE,
	FLAC__APODIZATION_TUKEY,
	FLAC__APODIZATION_PARTIAL_TUKEY,
	FLAC__APODIZATION_PUNCHOUT_TUKEY,
	FLAC__APODIZATION_WELCH
} FLAC__ApodizationFunction;

typedef struct {
	FLAC__ApodizationFunction type;
	union {
		struct {
			FLAC__real stddev;
		} gauss;
		struct {
			FLAC__real p;
		} tukey;
		struct {
			FLAC__real p;
			FLAC__real start;
			FLAC__real end;
		} multiple_tukey;
	} parameters;
} FLAC__ApodizationSpecification;

#endif // #ifndef FLAC__INTEGER_ONLY_LIBRARY

typedef struct FLAC__StreamEncoderProtected {
	FLAC__StreamEncoderState state;
	FLAC__bool verify;
	FLAC__bool streamable_subset;
	FLAC__bool do_md5;
	FLAC__bool do_mid_side_stereo;
	FLAC__bool loose_mid_side_stereo;
	uint32_t channels;
	uint32_t bits_per_sample;
	uint32_t sample_rate;
	uint32_t blocksize;
#ifndef FLAC__INTEGER_ONLY_LIBRARY
	uint32_t num_apodizations;
	FLAC__ApodizationSpecification apodizations[FLAC__MAX_APODIZATION_FUNCTIONS];
#endif
	uint32_t max_lpc_order;
	uint32_t qlp_coeff_precision;
	FLAC__bool do_qlp_coeff_prec_search;
	FLAC__bool do_exhaustive_model_search;
	FLAC__bool do_escape_coding;
	uint32_t min_residual_partition_order;
	uint32_t max_residual_partition_order;
	uint32_t rice_parameter_search_dist;
	FLAC__uint64 total_samples_estimate;
	FLAC__StreamMetadata **metadata;
	uint32_t num_metadata_blocks;
	FLAC__uint64 streaminfo_offset, seektable_offset, audio_offset;
#if FLAC__HAS_OGG
	FLAC__OggEncoderAspect ogg_encoder_aspect;
#endif
} FLAC__StreamEncoderProtected;

#endif
