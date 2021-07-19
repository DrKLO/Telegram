/* grabbag - Convenience lib for various routines common to several tools
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

/*
 * This wraps the replaygain_analysis lib, which is LGPL.  This wrapper
 * allows analysis of different input resolutions by automatically
 * scaling the input signal
 */

/* This .h cannot be included by itself; #include "share/grabbag.h" instead. */

#ifndef GRABBAG__REPLAYGAIN_H
#define GRABBAG__REPLAYGAIN_H

#include "FLAC/metadata.h"

#ifdef __cplusplus
extern "C" {
#endif

extern const unsigned GRABBAG__REPLAYGAIN_MAX_TAG_SPACE_REQUIRED;

extern const FLAC__byte * const GRABBAG__REPLAYGAIN_TAG_REFERENCE_LOUDNESS; /* = "REPLAYGAIN_REFERENCE_LOUDNESS" */
extern const FLAC__byte * const GRABBAG__REPLAYGAIN_TAG_TITLE_GAIN; /* = "REPLAYGAIN_TRACK_GAIN" */
extern const FLAC__byte * const GRABBAG__REPLAYGAIN_TAG_TITLE_PEAK; /* = "REPLAYGAIN_TRACK_PEAK" */
extern const FLAC__byte * const GRABBAG__REPLAYGAIN_TAG_ALBUM_GAIN; /* = "REPLAYGAIN_ALBUM_GAIN" */
extern const FLAC__byte * const GRABBAG__REPLAYGAIN_TAG_ALBUM_PEAK; /* = "REPLAYGAIN_ALBUM_PEAK" */

FLAC__bool grabbag__replaygain_is_valid_sample_frequency(unsigned sample_frequency);

FLAC__bool grabbag__replaygain_init(unsigned sample_frequency);

/* 'bps' must be valid for FLAC, i.e. >=4 and <= 32 */
FLAC__bool grabbag__replaygain_analyze(const FLAC__int32 * const input[], FLAC__bool is_stereo, unsigned bps, unsigned samples);

void grabbag__replaygain_get_album(float *gain, float *peak);
void grabbag__replaygain_get_title(float *gain, float *peak);

/* These three functions return an error string on error, or NULL if successful */
const char *grabbag__replaygain_analyze_file(const char *filename, float *title_gain, float *title_peak);
const char *grabbag__replaygain_store_to_vorbiscomment(FLAC__StreamMetadata *block, float album_gain, float album_peak, float title_gain, float title_peak);
const char *grabbag__replaygain_store_to_vorbiscomment_reference(FLAC__StreamMetadata *block);
const char *grabbag__replaygain_store_to_vorbiscomment_album(FLAC__StreamMetadata *block, float album_gain, float album_peak);
const char *grabbag__replaygain_store_to_vorbiscomment_title(FLAC__StreamMetadata *block, float title_gain, float title_peak);
const char *grabbag__replaygain_store_to_file(const char *filename, float album_gain, float album_peak, float title_gain, float title_peak, FLAC__bool preserve_modtime);
const char *grabbag__replaygain_store_to_file_reference(const char *filename, FLAC__bool preserve_modtime);
const char *grabbag__replaygain_store_to_file_album(const char *filename, float album_gain, float album_peak, FLAC__bool preserve_modtime);
const char *grabbag__replaygain_store_to_file_title(const char *filename, float title_gain, float title_peak, FLAC__bool preserve_modtime);

FLAC__bool grabbag__replaygain_load_from_vorbiscomment(const FLAC__StreamMetadata *block, FLAC__bool album_mode, FLAC__bool strict, double *reference, double *gain, double *peak);
double grabbag__replaygain_compute_scale_factor(double peak, double gain, double preamp, FLAC__bool prevent_clipping);

#ifdef __cplusplus
}
#endif

#endif
