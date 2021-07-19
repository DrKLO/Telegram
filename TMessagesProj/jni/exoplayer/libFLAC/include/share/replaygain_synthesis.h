/* replaygain_synthesis - Routines for applying ReplayGain to a signal
 * Copyright (C) 2002-2009  Josh Coalson
 * Copyright (C) 2011-2016  Xiph.Org Foundation
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

#ifndef FLAC__SHARE__REPLAYGAIN_SYNTHESIS_H
#define FLAC__SHARE__REPLAYGAIN_SYNTHESIS_H

#include <stdlib.h> /* for size_t */
#include "FLAC/format.h"

#define FLAC_SHARE__MAX_SUPPORTED_CHANNELS FLAC__MAX_CHANNELS

typedef enum {
	NOISE_SHAPING_NONE = 0,
	NOISE_SHAPING_LOW = 1,
	NOISE_SHAPING_MEDIUM = 2,
	NOISE_SHAPING_HIGH = 3
} NoiseShaping;

typedef struct {
	const float*  FilterCoeff;
	FLAC__uint64  Mask;
	double        Add;
	float         Dither;
	float         ErrorHistory     [FLAC_SHARE__MAX_SUPPORTED_CHANNELS] [16];  /* 16th order Noise shaping */
	float         DitherHistory    [FLAC_SHARE__MAX_SUPPORTED_CHANNELS] [16];
	int           LastRandomNumber [FLAC_SHARE__MAX_SUPPORTED_CHANNELS];
	unsigned      LastHistoryIndex;
	NoiseShaping  ShapingType;
} DitherContext;

void FLAC__replaygain_synthesis__init_dither_context(DitherContext *dither, int bits, int shapingtype);

/* scale = (float) pow(10., (double)replaygain * 0.05); */
size_t FLAC__replaygain_synthesis__apply_gain(FLAC__byte *data_out, FLAC__bool little_endian_data_out, FLAC__bool unsigned_data_out, const FLAC__int32 * const input[], unsigned wide_samples, unsigned channels, const unsigned source_bps, const unsigned target_bps, const double scale, const FLAC__bool hard_limit, FLAC__bool do_dithering, DitherContext *dither_context);

#endif
